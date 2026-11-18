package dev.kolib.app.ai

/** What KoLib asks a model about a book: a word's meaning where it's used, a chapter's summary, and questions. */
object BookAi {

    /** What the reader is reading, as far as it bears on an answer. */
    class Book(val title: String, val author: String?, val language: String?, val about: String? = null)

    /** The words selected are a word or a short phrase to define, not a passage to put in plain words. */
    fun isPhrase(text: String): Boolean = text.trim().split(Regex("\\s+")).size <= 4

    /** What [word] means in [sentence]; or, for more than a few words, the passage in plain words. */
    fun explain(engine: AiEngine, word: String, sentence: String, book: Book, answerIn: String, onProgress: (Float) -> Unit, onText: (String) -> Unit): AiEnd {
        val w = word.trim().replace(Regex("\\s+"), " ")
        val messages = if (isPhrase(w)) Prompts.lookUp(w, sentence.ifBlank { w }, book.title, book.author, answerIn)
        else Prompts.passage(w.take(1500), book.title, book.author, answerIn)
        return engine.generate(messages, 160, 0.2f, onProgress, onText)
    }

    /** How a summary is getting on: reading part [part] of [parts] of the chapter, or (part 0) writing it up. */
    class Stage(val part: Int, val parts: Int, val fraction: Float)

    /**
     * Summarises a chapter from its [paragraphs]. One long enough not to fit in the model is read in parts, notes
     * taken on each, and the summary written from the notes. [soFar]: the paragraphs are only what's been read.
     * Returns how it ended and the summary.
     */
    fun summarise(
        engine: AiEngine, paragraphs: List<String>, chapter: String, book: Book, soFar: Boolean, answerIn: String,
        onStage: (Stage) -> Unit, onText: (String) -> Unit,
    ): Pair<AiEnd, String> {
        val room = (engine.contextTokens - ANSWER - OVERHEAD).coerceIn(600, MOST_PART)
        val parts = BookText.chunks(paragraphs, room, engine::countTokens)
        if (parts.isEmpty()) return AiEnd.DONE to ""
        var source = parts[0]
        if (parts.size > 1) {
            val notes = StringBuilder()
            for ((i, part) in parts.withIndex()) {
                val said = StringBuilder()
                val end = engine.generate(
                    Prompts.chapterPart(part, chapter, book.title, i + 1, parts.size, answerIn), NOTES, 0.2f,
                    { onStage(Stage(i + 1, parts.size, it)) }, { said.append(it) },
                )
                if (end == AiEnd.CANCELLED || end == AiEnd.FAILED || end == AiEnd.TOO_LONG) return end to ""
                notes.append("Part ").append(i + 1).append(":\n").append(cleanAnswer(said.toString())).append("\n\n")
            }
            source = notes.toString().trim()
        }
        val out = StringBuilder()
        val end = engine.generate(
            Prompts.chapter(source, chapter, book.title, fromNotes = parts.size > 1, soFar = soFar, answerIn = answerIn), ANSWER, 0.3f,
            { if (parts.size == 1) onStage(Stage(1, 1, it)) else onStage(Stage(0, parts.size, it)) },
            { out.append(it); onText(it) },
        )
        return end to cleanAnswer(out.toString())
    }

    /**
     * Answers [question] in a conversation about the book. [chapters]: what's been read, each chapter's place in
     * the book, its name and its paragraphs (the last one only up to where the reader is). [history] is the
     * conversation so far (after its first, system message), and gets this question and its answer added.
     */
    fun answer(
        engine: AiEngine, history: MutableList<AiMessage>, question: String, book: Book,
        chapters: List<Triple<Int, String, List<String>>>, chapter: String, percent: Int, answerIn: String,
        readSpeed: Float?, onProgress: (Float) -> Unit, instructions: String = "", onText: (String) -> Unit,
    ): AiEnd {
        val count = engine::countTokens
        // As much of the book as the model reads in a quarter of a minute, where that's known; a third of what it holds otherwise.
        val most = minOf(MOST_PASSAGES, engine.contextTokens / 3)
        val budget = (readSpeed?.let { (it * READ_SECONDS).toInt() } ?: most).coerceIn(minOf(500, most), most)
        val latest = latest(chapters, LATEST, count)
        val found = BookText.passages(chapters.map { it.first to it.third }, question, budget - count(latest), count)
        val names = chapters.associate { it.first to it.second }
        val passages = buildString {
            var last = -1
            for (p in found) {
                if (p.chapter != last) {
                    names[p.chapter]?.takeIf { it.isNotBlank() }?.let { append("[").append(it).append("]\n") }
                    last = p.chapter
                }
                append(p.text).append('\n')
            }
        }
        // Nothing in the book matches the question's words (it asks what just happened, say): more of the latest pages instead.
        val recent = if (found.isEmpty()) latest(chapters, budget, count) else latest
        val system = Prompts.chatSystem(book.title, book.author, chapter, percent, answerIn, book.about, instructions)
        val asked = Prompts.chatQuestion(question, passages, recent)
        // The oldest turns go when the conversation outgrows the model.
        val room = engine.contextTokens - CHAT_ANSWER - 64
        fun size() = count(system.content) + history.sumOf { count(it.content) + 8 } + count(asked.content) + 16
        while (history.size >= 2 && size() > room) {
            history.removeAt(0)
            history.removeAt(0)
        }
        val said = StringBuilder()
        val end = engine.generate(listOf(system) + history + asked, CHAT_ANSWER, 0.4f, onProgress) { said.append(it); onText(it) }
        if (end == AiEnd.DONE || end == AiEnd.LENGTH) {
            history += asked
            history += AiMessage.assistant(cleanAnswer(said.toString()))
        }
        return end
    }

    /** The last lines read: up to [mostTokens] tokens' worth of the paragraphs just before where the reader is. */
    fun latest(chapters: List<Triple<Int, String, List<String>>>, mostTokens: Int, count: (String) -> Int): String {
        val out = ArrayList<String>()
        var held = 0
        // The pages the reader is on, and the story before them: not what's in front of it (an introduction, the
        // copyright page), which would be told of as if it were the book.
        val story = chapters.filterIndexed { i, c -> i == chapters.lastIndex || !BookText.frontMatter(c.second) }
        loop@ for ((_, _, paragraphs) in story.asReversed()) {
            for (p in paragraphs.asReversed()) {
                val n = count(p) + 1
                if (held + n > mostTokens) break@loop
                out += p
                held += n
            }
        }
        return out.asReversed().joinToString("\n")
    }

    private const val ANSWER = 420
    private const val NOTES = 220
    private const val OVERHEAD = 260
    private const val MOST_PART = 3000
    private const val CHAT_ANSWER = 320
    private const val LATEST = 220
    private const val MOST_PASSAGES = 1800
    private const val READ_SECONDS = 15f
}
