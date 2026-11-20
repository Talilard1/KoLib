package dev.kolib.app.ai

import dev.kolib.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The AI's notes on a book: a short note on each chapter the reader has finished, written while the book is open
 * (see [NotesWriter]) for the chat to remember the whole book so far by, not only the pages near where the reader
 * is. Kept with the book, in [file]; whether they're kept at all is the reader's choice, book by book.
 */
class BookNotes(private val file: File) {
    private val notes = LinkedHashMap<String, String>()
    private val skipped = HashSet<String>()

    private val _on = MutableStateFlow(false)
    /** Whether notes are kept on this book. */
    val on: StateFlow<Boolean> = _on

    private val _written = MutableStateFlow(0)
    /** How many chapters have notes: counts up as they're written. */
    val written: StateFlow<Int> = _written

    init {
        try {
            if (file.isFile) {
                val o = JSONObject(file.readText())
                _on.value = o.optBoolean("on")
                o.optJSONObject("chapters")?.let { c -> c.keys().forEach { k -> notes[k] = c.optString(k) } }
                o.optJSONArray("skipped")?.let { a -> for (i in 0 until a.length()) skipped += a.optString(i) }
            }
        } catch (e: Exception) {
            AppLog.w("AI", "The book's notes couldn't be read", e)
        }
        _written.value = notes.size
    }

    @Synchronized
    fun setOn(on: Boolean) {
        _on.value = on
        save()
    }

    /** The note on the chapter at [path], if there is one. */
    @Synchronized
    fun note(path: String): String? = notes[path]

    /** Whether the chapter at [path] has had its note written, or been passed over (too short, or too long for the model). */
    @Synchronized
    fun done(path: String): Boolean = path in notes || path in skipped

    @Synchronized
    fun put(path: String, note: String) {
        notes[path] = note.trim()
        skipped -= path
        _written.value = notes.size
        save()
    }

    /** The chapter at [path] gets no note: it's too short to need one, or the model can't read it. */
    @Synchronized
    fun skip(path: String) {
        skipped += path
        save()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val o = JSONObject().put("on", _on.value)
                .put("chapters", JSONObject().apply { notes.forEach { (k, v) -> put(k, v) } })
                .put("skipped", JSONArray(skipped.toList()))
            val part = File(file.path + ".part")
            part.writeText(o.toString())
            if (!part.renameTo(file)) {
                file.delete()
                part.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("AI", "The book's notes couldn't be saved", e)
        }
    }
}

/** A chapter the reader has finished, that may want a note: where it is in the book, its file and its name. */
data class NoteChapter(val index: Int, val path: String, val title: String)

/**
 * Writes the notes on a book (see [BookNotes]) while it's open: on each chapter the reader has finished that hasn't
 * one yet, the latest first, one at a time, when the model has had nothing to do for a little while. Anything
 * asked of the model stops it; it carries on later. It waits while [canWrite] says no (the app isn't in front, or
 * it should only write on the charger and the device isn't charging).
 */
class NotesWriter(
    private val notes: BookNotes,
    private val hub: AiHub,
    private val book: BookAi.Book,
    /** The chapters finished so far, in the book's order. On the main thread. */
    private val finished: () -> List<NoteChapter>,
    /** A chapter's paragraphs, by its place in the book. */
    private val paragraphs: (Int) -> List<String>,
    /** Why notes can't be written just now, or null when they can. */
    private val blocked: () -> Wait?,
    private val quiet: Long = QUIET_MS,
    private val every: Long = EVERY_MS,
) {
    /** Why it's waiting. */
    enum class Wait { CHARGER, AWAY, UNLOADED }

    sealed interface Status {
        data object Off : Status
        data object UpToDate : Status
        data class Waiting(val why: Wait?) : Status
        data class Writing(val chapter: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status

    /** How many of the chapters finished so far have notes, and how many could. */
    fun progress(): Pair<Int, Int> {
        val all = finished()
        return all.count { notes.note(it.path) != null } to all.count { notes.note(it.path) != null || !notes.done(it.path) }
    }

    /** Runs while the book is open. */
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            delay(every)
            if (!notes.on.value) {
                _status.value = Status.Off
                continue
            }
            val next = finished().lastOrNull { !notes.done(it.path) }
            if (next == null) {
                _status.value = Status.UpToDate
                continue
            }
            // Not while the model's been let go to save memory and battery, till something's asked of it.
            val why = blocked() ?: Wait.UNLOADED.takeIf { hub.resting }
            if (why != null || !hub.ready()) {
                _status.value = Status.Waiting(why)
                continue
            }
            // A quiet spell first: not while the reader is asking things.
            if (hub.working || hub.quietFor() < quiet) {
                if (_status.value !is Status.Writing) _status.value = Status.Waiting(null)
                continue
            }
            _status.value = Status.Writing(next.title)
            val text = withContext(Dispatchers.Default) { paragraphs(next.index) }
            if (text.sumOf { it.length } < SHORTEST) {
                notes.skip(next.path)
                continue
            }
            val result = try {
                hub.background { e -> BookAi.note(e, text, next.title, book) }
            } catch (e: AiException) {
                _status.value = Status.Waiting(null)
                delay(60_000)
                continue
            }
            when {
                // Something else wanted the model: it's done again later.
                result == null || result.first == AiEnd.CANCELLED -> {}
                (result.first == AiEnd.DONE || result.first == AiEnd.LENGTH) && result.second.isNotBlank() -> notes.put(next.path, result.second)
                // Too long for the model, or it failed at it: not tried again.
                else -> notes.skip(next.path)
            }
        }
    }

    companion object {
        /** How long the model is left alone after being asked something before notes are written. */
        const val QUIET_MS = 20_000L
        const val EVERY_MS = 6_000L
        /** A chapter shorter than this (a title page, an epigraph) needs no note. */
        const val SHORTEST = 400
    }
}
