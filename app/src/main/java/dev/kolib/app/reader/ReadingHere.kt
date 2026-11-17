package dev.kolib.app.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * How far a library book has been read in the app's reader (0 to 1), and when it was last read; or
 * that it's been marked as finished (until it's read again).
 */
data class ReadHere(val progress: Float, val at: Long, val finished: Boolean = false) {
    /** As a whole percentage; a book that's been started never shows as 0%. */
    val percent: Int get() = if (progress <= 0f) 0 else (progress * 100).toInt().coerceIn(1, 100)
    /** Being read: started, and not marked as finished. */
    val started: Boolean get() = !finished && progress > 0f
}

/** Every library book's place in the app's reader, for the library's covers, lists and sorting. */
class ReadingHereIndex {
    private val _books = MutableStateFlow<Map<String, ReadHere>>(emptyMap())
    val books: StateFlow<Map<String, ReadHere>> = _books.asStateFlow()

    /** Reads each book's saved place: one small file per book that's been opened. */
    fun load(ids: Collection<String>, dirOf: (String) -> File) {
        val found = HashMap<String, ReadHere>()
        for (id in ids) ReaderStore.readHereOf(dirOf(id))?.let { found[id] = it }
        _books.update { now -> found + now }
    }

    fun update(id: String, position: ReaderPosition?) {
        _books.update { m -> if (position == null) m - id else m + (id to ReadHere(position.bookProgress, position.at)) }
    }

    /** A book marked as finished. */
    fun set(id: String, here: ReadHere) {
        _books.update { m -> m + (id to here) }
    }

    fun forget(ids: Collection<String>) {
        _books.update { m -> m - ids.toSet() }
    }
}
