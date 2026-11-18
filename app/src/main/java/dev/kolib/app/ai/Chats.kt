package dev.kolib.app.ai

import dev.kolib.app.log.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A question and its answer in a chat, and where it was asked: a book's title, or nothing for the library. */
data class SavedTurn(val question: String, val answer: String, val where: String)

/** A chat that's been kept: what it's called (its first question), when it was last added to, and how long it is. */
data class SavedChat(val id: String, val title: String, val changedAt: Long, val questions: Int)

/** The chats with the model, kept on the device a file each, until they're deleted. */
class ChatStore(private val dir: File) {
    private fun file(id: String) = File(dir, "$id.json")

    /** The chats kept, the latest first. */
    fun list(): List<SavedChat> = (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()).mapNotNull { f ->
        try {
            val o = JSONObject(f.readText())
            SavedChat(f.name.removeSuffix(".json"), o.optString("title"), o.optLong("changedAt"), o.optJSONArray("turns")?.length() ?: 0)
        } catch (e: Exception) {
            null
        }
    }.filter { it.questions > 0 }.sortedByDescending { it.changedAt }

    fun load(id: String): List<SavedTurn>? = try {
        val turns = JSONObject(file(id).readText()).optJSONArray("turns") ?: JSONArray()
        (0 until turns.length()).mapNotNull { i -> turns.optJSONObject(i)?.let { SavedTurn(it.optString("q"), it.optString("a"), it.optString("where")) } }
    } catch (e: Exception) {
        null
    }

    /** Keeps a chat as it now is; one with nothing answered in it isn't kept. */
    fun save(id: String, turns: List<SavedTurn>, now: Long = System.currentTimeMillis()) {
        if (turns.isEmpty()) return
        try {
            dir.mkdirs()
            val o = JSONObject().put("title", title(turns)).put("changedAt", now)
                .put("turns", JSONArray(turns.map { JSONObject().put("q", it.question).put("a", it.answer).put("where", it.where) }))
            val part = File(dir, "$id.json.part")
            part.writeText(o.toString())
            if (!part.renameTo(file(id))) {
                file(id).delete()
                part.renameTo(file(id))
            }
        } catch (e: Exception) {
            AppLog.w("AI", "A chat couldn't be saved", e)
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }

    companion object {
        /** What a chat is called: its first question, cut short. */
        fun title(turns: List<SavedTurn>): String = turns.firstOrNull()?.question.orEmpty().replace(Regex("\\s+"), " ").trim().let { if (it.length > 70) it.take(68).trimEnd() + "…" else it }
    }
}

/** Questions about the library itself, answered with the list of what's in it. */
object LibraryAi {
    /** A book as the model is told of it. */
    data class Shelved(val title: String, val authors: String, val series: String?, val tags: List<String>, val state: String?)

    private const val ANSWER = 420
    private const val AHEAD_ROOM = 300

    private fun line(b: Shelved): String = buildString {
        append("- ").append(b.title)
        if (b.authors.isNotBlank()) append(" — ").append(b.authors)
        b.series?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
        if (b.tags.isNotEmpty()) append(" [").append(b.tags.take(3).joinToString(", ")).append("]")
        b.state?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    /**
     * [books] as lines of a list no longer than [room] tokens, in the order they're given (the ones on the screen
     * first): all of them when they fit. Returns the list and how many books it left out.
     */
    fun shelf(books: List<Shelved>, room: Int, count: (String) -> Int): Pair<String, Int> {
        val out = StringBuilder()
        var used = 0
        var shown = 0
        for (b in books) {
            val l = line(b)
            val n = count(l) + 1
            if (used + n > room) break
            out.append(l).append('\n')
            used += n
            shown++
        }
        return out.toString() to books.size - shown
    }

    /** Of the books the list left out (all after the first [listed]), those whose title, author, series or tags share a word with [question]. */
    fun matching(books: List<Shelved>, listed: Int, question: String, room: Int, count: (String) -> Int): String {
        val asked = BookText.keywords(question).map { it.lowercase() }.toSet()
        if (asked.isEmpty()) return ""
        val out = StringBuilder()
        var used = 0
        for (b in books.drop(listed)) {
            val words = (b.title + " " + b.authors + " " + b.series.orEmpty() + " " + b.tags.joinToString(" ")).lowercase().split(Regex("[^\\p{L}\\p{N}]+")).toSet()
            if (asked.none { it in words }) continue
            val l = line(b)
            val n = count(l) + 1
            if (used + n > room) break
            out.append(l).append('\n')
            used += n
        }
        return out.toString()
    }

    private fun room(engine: AiEngine) = minOf(1800, engine.contextTokens / 3)

    /**
     * What the model is told before any question asked in the library: the instructions, what's on the [screen],
     * and the [books] (the ones on the screen first). The same for every question while the screen stays as it
     * is, so the model keeps it read from one question to the next. Returns it, and how many books it lists.
     */
    fun context(engine: AiEngine, books: List<Shelved>, screen: String, answerIn: String, instructions: String = ""): Pair<AiMessage, Int> {
        val (list, left) = shelf(books, room(engine), engine::countTokens)
        return Prompts.librarySystem(books.size, answerIn, instructions, screen, list, left) to books.size - left
    }

    /** What's read ahead while a question is typed in the library: its context and the conversation so far. */
    fun ahead(engine: AiEngine, history: MutableList<AiMessage>, books: List<Shelved>, screen: String, answerIn: String, instructions: String = ""): List<AiMessage> {
        val system = context(engine, books, screen, answerIn, instructions).first
        return listOf(system) + BookAi.fitHistory(engine, system, history, AHEAD_ROOM)
    }

    /**
     * Answers [question] about the reader's library, whose [books] are given with the ones on the [screen] first.
     * [history] is the conversation so far, and gets this question and its answer added.
     */
    fun answer(
        engine: AiEngine, history: MutableList<AiMessage>, question: String, books: List<Shelved>, answerIn: String,
        instructions: String = "", screen: String = "", onProgress: (Float) -> Unit = {}, onText: (String) -> Unit,
    ): AiEnd {
        val (system, listed) = context(engine, books, screen, answerIn, instructions)
        val more = matching(books, listed, question, minOf(600, engine.contextTokens / 8), engine::countTokens)
        return converse(engine, history, system, Prompts.libraryQuestion(question, more), question, onProgress, onText)
    }

    /**
     * Answers [question] from what the model knows, without being shown anything: for a question asked in a
     * book ([title] by [author]) with the reading of it turned off.
     */
    fun plain(
        engine: AiEngine, history: MutableList<AiMessage>, question: String, title: String, author: String?, answerIn: String,
        instructions: String = "", onProgress: (Float) -> Unit = {}, onText: (String) -> Unit,
    ): AiEnd = converse(engine, history, Prompts.plainSystem(title, author, answerIn, instructions), AiMessage.user("Question: " + question.trim()), question, onProgress, onText)

    /** What's read ahead for a question in a book with the reading of it turned off. */
    fun plainAhead(engine: AiEngine, history: MutableList<AiMessage>, title: String, author: String?, answerIn: String, instructions: String = ""): List<AiMessage> {
        val system = Prompts.plainSystem(title, author, answerIn, instructions)
        return listOf(system) + BookAi.fitHistory(engine, system, history, AHEAD_ROOM)
    }

    private fun converse(
        engine: AiEngine, history: MutableList<AiMessage>, system: AiMessage, asked: AiMessage, question: String,
        onProgress: (Float) -> Unit, onText: (String) -> Unit,
    ): AiEnd {
        BookAi.fitHistory(engine, system, history, engine.countTokens(asked.content))
        val said = StringBuilder()
        val end = engine.generate(listOf(system) + history + asked, ANSWER, 0.5f, onProgress) { said.append(it); onText(it) }
        if (end == AiEnd.DONE || end == AiEnd.LENGTH) {
            history += AiMessage.user("Question: " + question.trim())
            history += AiMessage.assistant(cleanAnswer(said.toString()))
        }
        return end
    }
}
