package dev.kolib.app.reader

import dev.kolib.app.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** What's known of the reading of one book in the app's readers. */
data class BookReading(
    /** The book's title when it was last read, kept for books since removed from the library. */
    val title: String = "",
    /** Time spent reading it. */
    val seconds: Long = 0,
    /** Sittings of a minute or more. */
    val sessions: Int = 0,
    /** Pages turned while reading (not while leafing through). */
    val pages: Int = 0,
    /** Words read, where the reader could count them: for the speed it's read at. */
    val words: Long = 0,
    /** The time those words took. */
    val wordSeconds: Long = 0,
    /** Seconds read each day ("2026-11-17"), for the last [ReadingLog.DAYS_KEPT] days it was read on. */
    val days: Map<String, Int> = emptyMap(),
    val firstAt: Long = 0,
    val lastAt: Long = 0,
) {
    val hours: Double get() = seconds / 3600.0

    /** Words a minute, once enough has been read to say. */
    val wordsPerMinute: Float? get() = if (wordSeconds >= 300 && words >= 1500) (words * 60f / wordSeconds).coerceIn(ReadingLog.SLOWEST, ReadingLog.FASTEST) else null

    /** Minutes in a sitting, on average. */
    val minutesPerSession: Int? get() = if (sessions > 0) (seconds / 60.0 / sessions).roundToInt().coerceAtLeast(1) else null

    /** Pages turned a minute. */
    val pagesPerMinute: Float? get() = if (seconds >= 120 && pages > 0) pages * 60f / seconds else null

    operator fun plus(o: BookReading) = BookReading(
        title = title,
        seconds = seconds + o.seconds,
        sessions = sessions + o.sessions,
        pages = pages + o.pages,
        words = words + o.words,
        wordSeconds = wordSeconds + o.wordSeconds,
        days = (days.keys + o.days.keys).associateWith { (days[it] ?: 0) + (o.days[it] ?: 0) },
        firstAt = listOf(firstAt, o.firstAt).filter { it > 0 }.minOrNull() ?: 0,
        lastAt = maxOf(lastAt, o.lastAt),
    )
}

/**
 * The reading done in the app's own readers, book by book: how long, in how many sittings, how many pages and
 * words. Kept in one small file, and kept for a book after it's removed from the library, so the totals stay.
 */
class ReadingLog(private val file: File, private val io: Executor = Executors.newSingleThreadExecutor()) {
    private val _books = MutableStateFlow<Map<String, BookReading>>(emptyMap())
    val books: StateFlow<Map<String, BookReading>> = _books.asStateFlow()

    /** Reads what's been kept. */
    fun load() {
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText()).optJSONObject("books") ?: return
            val found = HashMap<String, BookReading>()
            for (id in o.keys()) {
                val b = o.optJSONObject(id) ?: continue
                val days = b.optJSONObject("days")
                found[id] = BookReading(
                    title = b.optString("title"),
                    seconds = b.optLong("seconds"),
                    sessions = b.optInt("sessions"),
                    pages = b.optInt("pages"),
                    words = b.optLong("words"),
                    wordSeconds = b.optLong("wordSeconds"),
                    days = days?.keys()?.asSequence()?.associateWith { days.optInt(it) }.orEmpty(),
                    firstAt = b.optLong("firstAt"),
                    lastAt = b.optLong("lastAt"),
                )
            }
            _books.update { now -> found + now }
        } catch (e: Exception) {
            AppLog.w("Reader", "The reading stats couldn't be read", e)
        }
    }

    /**
     * Counts some reading of book [id]: [seconds] of it, [pages] turned and [words] read in that time, and whether
     * a new sitting has begun with it ([session]).
     */
    fun record(id: String, title: String, seconds: Int, pages: Int, words: Int, session: Boolean, now: Long = System.currentTimeMillis()) {
        if (seconds <= 0 && pages <= 0 && !session) return
        // (LocalDate.ofInstant is Android 14's; this works from 10, as KoLib does.)
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        _books.update { all ->
            val was = all[id] ?: BookReading(firstAt = now)
            var days = was.days + (day to (was.days[day] ?: 0) + seconds)
            if (days.size > DAYS_KEPT) days = days.toSortedMap().entries.toList().takeLast(DAYS_KEPT).associate { it.key to it.value }
            all + (id to was.copy(
                title = title.ifBlank { was.title },
                seconds = was.seconds + seconds,
                sessions = was.sessions + (if (session) 1 else 0),
                pages = was.pages + pages,
                words = was.words + words,
                wordSeconds = was.wordSeconds + (if (words > 0) seconds else 0),
                days = days,
                firstAt = if (was.firstAt > 0) was.firstAt else now,
                lastAt = now,
            ))
        }
    }

    /** Everything read, in every book, added up. */
    val total: BookReading get() = _books.value.values.fold(BookReading()) { sum, b -> sum + b }

    /** The speed to reckon book [id] at, in words a minute: its own if that's known, else the reader's over every book, else an ordinary one. */
    fun speedFor(id: String?): Float = _books.value[id]?.wordsPerMinute ?: total.wordsPerMinute ?: ORDINARY

    /** Keeps the log, in the background. */
    fun save() {
        val all = _books.value
        io.execute { write(all) }
    }

    /** Keeps the log now. */
    fun saveNow() = write(_books.value)

    private fun write(all: Map<String, BookReading>) {
        try {
            val books = JSONObject()
            for ((id, b) in all) {
                books.put(id, JSONObject().apply {
                    put("title", b.title)
                    put("seconds", b.seconds)
                    put("sessions", b.sessions)
                    put("pages", b.pages)
                    put("words", b.words)
                    put("wordSeconds", b.wordSeconds)
                    put("days", JSONObject().apply { b.days.forEach { (d, s) -> put(d, s) } })
                    put("firstAt", b.firstAt)
                    put("lastAt", b.lastAt)
                })
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".part")
            tmp.writeText(JSONObject().put("books", books).toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("Reader", "The reading stats couldn't be saved", e)
        }
    }

    companion object {
        /** An ordinary reading speed, in words a minute, for when the reader's own isn't known yet. */
        const val ORDINARY = 250f
        const val SLOWEST = 60f
        const val FASTEST = 900f
        const val DAYS_KEPT = 90
    }
}

/**
 * One book open in a reader: counts the time it's read for. The time on a page counts when the page is turned (or
 * the reader is left), up to [LONGEST] a page, so a book left open on the table isn't being read all night; and a
 * page turned within a few seconds was leafed past, not read.
 */
class ReadingSession(
    val log: ReadingLog,
    val bookId: String,
    private val title: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** When the page now showing was come to; 0 while the reader isn't on screen. */
    private var since = 0L
    private var sitting = 0L
    private var counted = false
    private var unsaved = 0

    /** What's been read of this book so far. */
    val reading: BookReading? get() = log.books.value[bookId]

    /** The speed this book is being read at, in words a minute (see [ReadingLog.speedFor]). */
    val wordsPerMinute: Float get() = log.speedFor(bookId)

    /** The reader has come on screen. */
    fun resume() {
        if (since != 0L) return
        since = clock()
        sitting = 0
        counted = false
    }

    /** A page was turned, or the text scrolled on: [pages] of them, with [words] read since the last. */
    fun moved(pages: Int = 1, words: Int = 0) = tally(pages, words)

    /** The reader has left the screen. */
    fun pause() {
        if (since == 0L) return
        tally(0, 0)
        since = 0L
        log.save()
        unsaved = 0
    }

    private fun tally(pages: Int, words: Int) {
        if (since == 0L) return
        val now = clock()
        val spent = ((now - since) / 1000).coerceAtLeast(0)
        // Whole seconds are counted, and the rest carried to the next page.
        since = if (spent > LONGEST) now else since + spent * 1000
        val seconds = spent.coerceAtMost(LONGEST).toInt()
        val read = seconds >= QUICKEST
        sitting += seconds
        val session = !counted && sitting >= 60
        if (session) counted = true
        log.record(bookId, title, seconds, if (read) pages else 0, if (read) words else 0, session, now)
        if (++unsaved >= 24) {
            log.save()
            unsaved = 0
        }
    }

    companion object {
        /** The longest one page is counted for, in seconds. */
        const val LONGEST = 300L
        /** The least time on a page for it to count as read, in seconds. */
        const val QUICKEST = 3
    }
}
