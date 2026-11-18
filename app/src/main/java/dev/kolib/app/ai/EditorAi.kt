package dev.kolib.app.ai

import dev.kolib.app.epub.EpubText

/**
 * What the model does in the text editor: changes the selected text as it's told (fixes its spelling, tidies
 * it, rewrites it), or carries on writing from where the cursor is. The text goes to it with *italics* and
 * **bold** marked, and comes back the same way, so they survive the trip.
 */
object EditorAi {
    private val blockRe = Regex("(?is)<(p|h[1-6]|blockquote|li|div)\\b[^>]*>(.*?)</\\1\\s*>")
    private val boldRe = Regex("(?is)<(b|strong)\\b[^>]*>(.*?)</\\1\\s*>")
    private val italicRe = Regex("(?is)<(i|em)\\b[^>]*>(.*?)</\\1\\s*>")

    /** The editor's paragraphs ([html]) as text for a model: a blank line between paragraphs, italics and bold in asterisks. */
    fun marked(html: String): String {
        val blocks = blockRe.findAll(html).map { it.groupValues[2] }.toList().ifEmpty { listOf(html) }
        return blocks.joinToString("\n\n") { inner ->
            val m = inner.replace(boldRe) { "**" + it.groupValues[2] + "**" }.replace(italicRe) { "*" + it.groupValues[2] + "*" }.replace(Regex("(?i)<br\\s*/?>"), "\n")
            EpubText.plainText(m).replace(Regex("[ \\t]+"), " ").trim()
        }.trim()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** A model's text as paragraphs for the editor, its asterisks back as bold and italics. */
    fun html(marked: String): String = paragraphs(marked).joinToString("") { p ->
        "<p>" + escape(p)
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])"), "<i>$1</i>")
            .replace("\n", "<br/>") + "</p>"
    }

    /** One paragraph of a model's text as markup for inside a paragraph: bold, italics and line breaks. */
    fun inner(marked: String): String = escape(marked.trim())
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])"), "<i>$1</i>")
        .replace("\n", "<br/>")

    /** The same without the marks, for where only plain text can go. */
    fun plain(marked: String): String = paragraphs(marked).joinToString("\n\n") { it.replace("**", "").replace(Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])"), "$1") }

    private fun paragraphs(text: String): List<String> = cleanAnswer(text).split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

    /** The changes offered at a tap, and what the model is told for each. */
    enum class Quick(val instruction: String) {
        SPELLING("Correct spelling mistakes, typos and punctuation. Change nothing else."),
        TIDY(
            "Fix the formatting only: join lines and words that were broken in the middle of a sentence, remove stray spaces and the hyphens " +
                "left from line breaks, and make the quotation marks consistent. Do not change the wording."
        ),
        CLEARER("Rewrite it so that it reads more clearly and simply. Keep the meaning, the voice and the length."),
    }

    private const val EDITOR =
        "You are an editor working inside an e-book editor. You are given part of a book's text and an instruction. Reply with the text after " +
            "carrying out the instruction, and nothing else: no comments, no explanations, no quotation marks around it. Leave everything the " +
            "instruction doesn't ask you to change exactly as it is. Keep the paragraphs, separated by a blank line. *Asterisks* mark italics and " +
            "**double asterisks** mark bold: keep them around the same words. Write in the language the text is in."

    /** How much of the text goes to the model at a time: little enough that it comes back whole. */
    private const val PART = 450

    /**
     * Changes [text] (as [marked] gives it) the way [instruction] says. A long text goes a part at a time;
     * [onPart] hears which part of how many, [onText] gets the new text as it's written. Returns how it ended
     * and the new text.
     */
    fun change(engine: AiEngine, text: String, instruction: String, onPart: (Int, Int) -> Unit, onText: (String) -> Unit): Pair<AiEnd, String> {
        val room = minOf(PART, (engine.contextTokens - 300) / 3).coerceAtLeast(120)
        val parts = BookText.chunks(text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }, room, engine::countTokens)
            .map { it.replace("\n", "\n\n") }
        val out = StringBuilder()
        for ((i, part) in parts.withIndex()) {
            onPart(i + 1, parts.size)
            if (i > 0) {
                out.append("\n\n")
                onText("\n\n")
            }
            val said = StringBuilder()
            val end = engine.generate(
                listOf(AiMessage.system(EDITOR), AiMessage.user("Instruction: ${instruction.trim()}\n\nText:\n$part")),
                (engine.countTokens(part) * 1.6f).toInt() + 80, 0.2f, {},
            ) { said.append(it); onText(it) }
            if (end != AiEnd.DONE && end != AiEnd.LENGTH) return end to ""
            out.append(cleanAnswer(said.toString()))
        }
        return AiEnd.DONE to out.toString().trim()
    }

    /** What became of some paragraphs of a run: [count] of them, from the one at [from], are now [paragraphs]. */
    class Edit(val run: Int, val from: Int, val count: Int, val paragraphs: List<String>)

    /** Paragraphs in groups small enough to go to the model whole: where each group starts, and what's in it. */
    internal fun groups(paragraphs: List<String>, room: Int, count: (String) -> Int): List<Pair<Int, List<String>>> {
        val out = ArrayList<Pair<Int, List<String>>>()
        var from = 0
        var now = ArrayList<String>()
        var size = 0
        for ((i, p) in paragraphs.withIndex()) {
            val n = count(p) + 2
            if (now.isNotEmpty() && size + n > room) {
                out += from to now
                from = i
                now = ArrayList()
                size = 0
            }
            now += p
            size += n
        }
        if (now.isNotEmpty()) out += from to now
        return out
    }

    /**
     * Changes whole paragraphs the way [instruction] says. [runs] are the paragraphs, those side by side in
     * the page together; they go to the model a group at a time, and what comes back for a group takes that
     * group's place, so a paragraph the model leaves alone, joins or splits only moves its neighbours in the
     * group. A group the model says nothing for is left as it was. [onPart] hears which group of how many,
     * [onText] gets the new text as it's written.
     */
    fun changeRuns(engine: AiEngine, runs: List<List<String>>, instruction: String, onPart: (Int, Int) -> Unit, onText: (String) -> Unit): Pair<AiEnd, List<Edit>> {
        val room = minOf(PART, (engine.contextTokens - 300) / 3).coerceAtLeast(120)
        val parts = runs.flatMapIndexed { r, run -> groups(run, room, engine::countTokens).map { (from, list) -> Triple(r, from, list) } }
        val edits = ArrayList<Edit>()
        for ((i, part) in parts.withIndex()) {
            val (run, from, list) = part
            onPart(i + 1, parts.size)
            if (i > 0) onText("\n\n")
            val text = list.joinToString("\n\n")
            val said = StringBuilder()
            val end = engine.generate(
                listOf(AiMessage.system(EDITOR), AiMessage.user("Instruction: ${instruction.trim()}\n\nText:\n$text")),
                (engine.countTokens(text) * 1.6f).toInt() + 80, 0.2f, {},
            ) { said.append(it); onText(it) }
            if (end != AiEnd.DONE && end != AiEnd.LENGTH) return end to emptyList()
            val back = paragraphs(said.toString())
            if (back.isNotEmpty()) edits += Edit(run, from, list.size, back)
        }
        return AiEnd.DONE to edits
    }

    /** Carries on from [before] (the text up to the cursor); [wish] is what the writer wants next, if they said. */
    fun carryOn(engine: AiEngine, before: String, wish: String, onText: (String) -> Unit): Pair<AiEnd, String> {
        val lead = before.trim().takeLast(((engine.contextTokens - 700) * 3).coerceIn(600, 4000))
        val system = "You are a writing assistant inside an e-book editor. Carry on the text you are given, in the same language, voice, tense and style. " +
            "Write only what comes next: two or three paragraphs separated by a blank line. No comments, no title, and don't repeat what's there." +
            (if (wish.isBlank()) "" else " What the writer wants next: ${wish.trim()}")
        val said = StringBuilder()
        val end = engine.generate(listOf(AiMessage.system(system), AiMessage.user(lead.ifBlank { "(The page is empty: start the story.)" })), 420, 0.8f, {}) { said.append(it); onText(it) }
        return end to cleanAnswer(said.toString())
    }
}
