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

    /** Where the reader is in the book, as a question asked there needs it. */
    class Place(
        /** What's been read: each chapter's place in the book, its name and its paragraphs (the last up to the end of the screen). */
        val chapters: List<Triple<Int, String, List<String>>>,
        /** The chapter the reader is in, by name, and how far through the book they are. */
        val chapter: String,
        val percent: Int,
        /** The text on the reader's screen now, when it's known. */
        val screen: String? = null,
        /** When the screen is known: what's been read up to the top of it, to tell what came just before it. */
        val beforeScreen: List<Triple<Int, String, List<String>>>? = null,
        /** Notes on chapters read before this one: their names, and the notes. */
        val notes: List<Pair<String, String>> = emptyList(),
        /** Words the reader picked out on the page to ask about. */
        val selected: String? = null,
    )

    /**
     * What the model is told about the book before any question: the instructions, where the reader is, notes on
     * the chapters before, the pages before the screen and the screen itself. It's the same for every question
     * asked on the same page, so the model keeps it read from one to the next (and can read it ahead).
     */
    fun context(engine: AiEngine, book: Book, place: Place, answerIn: String, instructions: String = ""): AiMessage {
        val count = engine::countTokens
        val ctx = engine.contextTokens
        val screen = place.screen?.takeIf { it.isNotBlank() }?.let { lastOf(it, minOf(SCREEN, ctx / 6), count) }
        val before = latest(place.beforeScreen ?: place.chapters, if (screen != null) BEFORE else LATEST, count)
        val notes = notesText(place.notes, minOf(NOTES_HELD, ctx / 5), count)
        return Prompts.chatSystem(book.title, book.author, place.chapter, place.percent, answerIn, book.about, instructions, notes, before, screen.orEmpty())
    }

    /**
     * The conversation so far, as much of it as fits beside [system] with room left for a question of [question]
     * tokens and its answer: the oldest turns go first.
     */
    fun fitHistory(engine: AiEngine, system: AiMessage, history: MutableList<AiMessage>, question: Int): MutableList<AiMessage> {
        val count = engine::countTokens
        val room = engine.contextTokens - CHAT_ANSWER - 64
        fun size() = count(system.content) + history.sumOf { count(it.content) + 8 } + question + 16
        while (history.size >= 2 && size() > room) {
            history.removeAt(0)
            history.removeAt(0)
        }
        return history
    }

    /** What's read ahead while a question is typed: the book's context and the conversation so far, with room for a question. */
    fun ahead(engine: AiEngine, history: MutableList<AiMessage>, book: Book, place: Place, answerIn: String, instructions: String = ""): List<AiMessage> {
        val system = context(engine, book, place, answerIn, instructions)
        return listOf(system) + fitHistory(engine, system, history, AHEAD_ROOM)
    }

    /**
     * Answers [question] in a conversation about the book, read where the reader is ([place]). [history] is the
     * conversation so far (after its first, system message), and gets this question and its answer added.
     */
    fun answer(
        engine: AiEngine, history: MutableList<AiMessage>, question: String, book: Book, place: Place, answerIn: String,
        readSpeed: Float?, onProgress: (Float) -> Unit, instructions: String = "", onText: (String) -> Unit,
    ): AiEnd {
        val count = engine::countTokens
        val system = context(engine, book, place, answerIn, instructions)
        // As much more of the book as the model reads in a quarter of a minute, where that's known; a third of what it holds otherwise.
        val most = minOf(MOST_PASSAGES, engine.contextTokens / 3)
        val budget = (readSpeed?.let { (it * READ_SECONDS).toInt() } ?: most).coerceIn(minOf(500, most), most)
        val told = system.content
        val found = BookText.passages(place.chapters.map { it.first to it.third }, question, budget, count).filter { !told.contains(it.text.trim()) }
        val names = place.chapters.associate { it.first to it.second }
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
        // Nothing in the book matches the question's words (it asks what just happened, say): more of what came before instead.
        val recent = if (found.isEmpty()) earlier(place.beforeScreen ?: place.chapters, budget, told, count) else ""
        val asked = Prompts.chatQuestion(question, passages.ifBlank { recent }, place.selected.orEmpty())
        fitHistory(engine, system, history, count(asked.content))
        val said = StringBuilder()
        val end = engine.generate(listOf(system) + history + asked, CHAT_ANSWER, 0.4f, onProgress) { said.append(it); onText(it) }
        if (end == AiEnd.DONE || end == AiEnd.LENGTH) {
            history += asked
            history += AiMessage.assistant(cleanAnswer(said.toString()))
        }
        return end
    }

    /** Up to [mostTokens] of what was read before the part already [told], going back from it. */
    private fun earlier(chapters: List<Triple<Int, String, List<String>>>, mostTokens: Int, told: String, count: (String) -> Int): String {
        val paragraphs = story(chapters).flatMap { it.third }
        var end = paragraphs.size
        while (end > 0 && told.contains(paragraphs[end - 1].trim())) end--
        val out = ArrayList<String>()
        var held = 0
        for (i in end - 1 downTo 0) {
            val n = count(paragraphs[i]) + 1
            if (held + n > mostTokens) break
            out += paragraphs[i]
            held += n
        }
        return out.asReversed().joinToString("\n")
    }

    /** The end of [text], at most [mostTokens] of it, starting at a line. */
    private fun lastOf(text: String, mostTokens: Int, count: (String) -> Int): String {
        if (count(text) <= mostTokens) return text.trim()
        val lines = text.trim().lines()
        val out = ArrayList<String>()
        var held = 0
        for (l in lines.asReversed()) {
            val n = count(l) + 1
            if (held + n > mostTokens) break
            out += l
            held += n
        }
        return out.asReversed().joinToString("\n")
    }

    /** Notes on earlier chapters, the latest kept where they don't all fit in [mostTokens]: "[Chapter] note" a line. */
    fun notesText(notes: List<Pair<String, String>>, mostTokens: Int, count: (String) -> Int): String {
        val out = ArrayList<String>()
        var held = 0
        for ((name, note) in notes.asReversed()) {
            val line = (if (name.isBlank()) "" else "[$name] ") + note.replace(Regex("\\s+"), " ").trim()
            val n = count(line) + 1
            if (held + n > mostTokens) break
            out += line
            held += n
        }
        if (out.isEmpty()) return ""
        val left = notes.size - out.size
        return (if (left > 0) "(Notes on the first $left chapters are left out.)\n" else "") + out.asReversed().joinToString("\n")
    }

    /**
     * A note on a chapter the reader has finished, for the model to remember the book by. A chapter too long to
     * read in one go is read in parts, and the note written from what each part says. Returns how it ended, and
     * the note.
     */
    fun note(engine: AiEngine, paragraphs: List<String>, chapter: String, book: Book): Pair<AiEnd, String> {
        val room = (engine.contextTokens - NOTE - OVERHEAD).coerceIn(600, MOST_PART)
        val parts = BookText.chunks(paragraphs, room, engine::countTokens)
        if (parts.isEmpty()) return AiEnd.DONE to ""
        var source = parts[0]
        if (parts.size > 1) {
            val notes = StringBuilder()
            for ((i, part) in parts.withIndex()) {
                val said = StringBuilder()
                val end = engine.generate(Prompts.chapterPart(part, chapter, book.title, i + 1, parts.size, "English"), NOTES, 0.2f, {}) { said.append(it) }
                if (end == AiEnd.CANCELLED || end == AiEnd.FAILED || end == AiEnd.TOO_LONG) return end to ""
                notes.append("Part ").append(i + 1).append(":\n").append(cleanAnswer(said.toString())).append("\n\n")
            }
            source = notes.toString().trim()
        }
        val out = StringBuilder()
        val end = engine.generate(Prompts.chapterNote(source, chapter, book.title, fromNotes = parts.size > 1), NOTE, 0.2f, {}) { out.append(it) }
        return end to cleanAnswer(out.toString())
    }

    /**
     * The pages the reader is on, and the story before them: not what's in front of it (an introduction, the
     * copyright page), which would be told of as if it were the book.
     */
    private fun story(chapters: List<Triple<Int, String, List<String>>>) =
        chapters.filterIndexed { i, c -> i == chapters.lastIndex || !BookText.frontMatter(c.second) }

    /** The last lines read: up to [mostTokens] tokens' worth of the paragraphs just before where the reader is. */
    fun latest(chapters: List<Triple<Int, String, List<String>>>, mostTokens: Int, count: (String) -> Int): String {
        val out = ArrayList<String>()
        var held = 0
        loop@ for ((_, _, paragraphs) in story(chapters).asReversed()) {
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
    private const val NOTE = 170
    private const val NOTES_HELD = 1500
    private const val SCREEN = 1200
    private const val BEFORE = 260
    private const val AHEAD_ROOM = 300
    private const val OVERHEAD = 260
    private const val MOST_PART = 3000
    private const val CHAT_ANSWER = 320
    private const val LATEST = 220
    private const val MOST_PASSAGES = 1800
    private const val READ_SECONDS = 15f
}
