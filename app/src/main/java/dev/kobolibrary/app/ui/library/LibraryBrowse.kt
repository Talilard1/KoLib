package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.tr
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.ReadHere
import dev.kobolibrary.app.ui.BookSorting

enum class LibraryFilter(val label: String) {
    ALL(tr("All")),
    ON_KOBO(tr("On Kobo")),
    NOT_ON_KOBO(tr("Not on Kobo")),
    CHANGED(tr("Changed since sent")),
}

/** Sort orders, each with the direction it starts in and how its two directions read. */
enum class LibrarySort(val label: String, val startsDescending: Boolean, val ascending: String, val descending: String) {
    TITLE(tr("Title"), false, tr("A to Z"), tr("Z to A")),
    AUTHOR(tr("Author"), false, tr("A to Z"), tr("Z to A")),
    SERIES(tr("Series"), false, tr("A to Z"), tr("Z to A")),
    PUBLISHED(tr("Published"), true, tr("oldest first"), tr("newest first")),
    ADDED(tr("Date added"), true, tr("oldest first"), tr("newest first")),
    EDITED(tr("Last edited"), true, tr("oldest first"), tr("newest first")),
    SIZE(tr("File size"), true, tr("smallest first"), tr("largest first")),
    PAGES(tr("Pages"), true, tr("shortest first"), tr("longest first")),
    RATING(tr("Rating"), true, tr("lowest first"), tr("highest first")),
    LAST_READ(tr("Last read on Kobo"), true, tr("least recent first"), tr("most recent first")),
    READ_HERE(tr("Last read on this device"), true, tr("least recent first"), tr("most recent first")),
}

/** Library list settings and scroll positions, kept while moving around the app. */
class LibraryBrowseState {
    var query by mutableStateOf("")
    var filter by mutableStateOf(LibraryFilter.ALL)
    var collection by mutableStateOf<String?>(null)
    var tags by mutableStateOf<Set<String>>(emptySet())
    /** With several tags chosen: books need all of them (true) or any one (false). */
    var tagsMatchAll by mutableStateOf(true)
    var sort by mutableStateOf(LibrarySort.TITLE)
    var descending by mutableStateOf(false)
    var grid by mutableStateOf(true)
    var selectedId by mutableStateOf<String?>(null)
    val gridState = LazyGridState()
    val listState = LazyListState()

    /** Choosing several books (to send them, for instance). */
    var selecting by mutableStateOf(false)
    val picked = mutableStateMapOf<String, Boolean>()

    fun togglePicked(id: String) {
        if (picked.remove(id) == null) picked[id] = true
    }

    fun stopSelecting() {
        selecting = false
        picked.clear()
    }

    /** Picks [order], or flips the direction if it's already the order. */
    fun sortBy(order: LibrarySort) {
        if (sort == order) {
            descending = !descending
        } else {
            sort = order
            descending = order.startsDescending
        }
    }

    val sortDescription: String get() = "${sort.label.lowercase()}, ${if (descending) sort.descending else sort.ascending}"

    val narrowed: Boolean get() = query.isNotBlank() || filter != LibraryFilter.ALL || collection != null || tags.isNotEmpty()

    fun clearFilters() {
        query = ""
        filter = LibraryFilter.ALL
        collection = null
        tags = emptySet()
    }
}

object LibrarySorting {

    private fun seriesNumber(b: LibraryBook) = b.meta.seriesIndex ?: Double.MAX_VALUE
    private val byTitle = compareBy<LibraryBook> { BookSorting.titleKey(it.meta.title) }

    /** What each order sorts by, in its "ascending" direction. */
    private fun primary(sort: LibrarySort, reading: (LibraryBook) -> KoboBook?, here: (LibraryBook) -> ReadHere?): Comparator<LibraryBook> = when (sort) {
        LibrarySort.LAST_READ -> compareBy { reading(it)?.lastReadMillis ?: 0L }
        LibrarySort.READ_HERE -> compareBy { here(it)?.at ?: 0L }
        LibrarySort.TITLE -> byTitle
        LibrarySort.AUTHOR -> compareBy { it.meta.authorSort.ifEmpty { "\uFFFF" }.lowercase() }
        LibrarySort.SERIES -> compareBy { it.meta.series?.lowercase() ?: "" }
        LibrarySort.PUBLISHED -> compareBy { it.meta.published ?: "" }
        LibrarySort.ADDED -> compareBy { it.addedAt }
        LibrarySort.EDITED -> compareBy { it.modifiedAt }
        LibrarySort.SIZE -> compareBy { it.fileSize }
        LibrarySort.PAGES -> compareBy { it.pages ?: 0 }
        LibrarySort.RATING -> compareBy { it.meta.rating ?: 0 }
    }

    /** How ties are broken, the same way whichever direction: series in order, then A to Z. */
    private fun ties(sort: LibrarySort): Comparator<LibraryBook> = when (sort) {
        LibrarySort.TITLE -> compareBy { it.id }
        LibrarySort.AUTHOR, LibrarySort.SERIES -> compareBy<LibraryBook> { it.meta.series?.lowercase() ?: "\uFFFF" }
            .thenBy { seriesNumber(it) }
            .then(byTitle)
        else -> byTitle
    }

    /** Books without the sorted value (no series, no date, no rating) go after the rest either way. */
    private fun missing(sort: LibrarySort, b: LibraryBook, reading: (LibraryBook) -> KoboBook?, here: (LibraryBook) -> ReadHere?): Boolean = when (sort) {
        LibrarySort.LAST_READ -> reading(b)?.lastReadMillis == null
        LibrarySort.READ_HERE -> here(b) == null
        LibrarySort.SERIES -> b.meta.series.isNullOrBlank()
        LibrarySort.PUBLISHED -> b.meta.published.isNullOrBlank()
        LibrarySort.PAGES -> b.pages == null
        LibrarySort.RATING -> (b.meta.rating ?: 0) == 0
        LibrarySort.AUTHOR -> b.meta.authors.isEmpty()
        else -> false
    }

    fun comparator(
        sort: LibrarySort,
        descending: Boolean,
        reading: (LibraryBook) -> KoboBook? = { null },
        here: (LibraryBook) -> ReadHere? = { null },
    ): Comparator<LibraryBook> {
        val main = primary(sort, reading, here)
        return compareBy<LibraryBook> { missing(sort, it, reading, here) }
            .then(if (descending) main.reversed() else main)
            .then(ties(sort))
    }

    fun filter(
        books: List<LibraryBook>,
        query: String,
        filter: LibraryFilter,
        collection: String?,
        sort: LibrarySort,
        descending: Boolean = sort.startsDescending,
        tags: Set<String> = emptySet(),
        tagsMatchAll: Boolean = true,
        reading: (LibraryBook) -> KoboBook? = { null },
        here: (LibraryBook) -> ReadHere? = { null },
    ): List<LibraryBook> {
        val node = LibraryQuery.parse(query)
        val wantedTags = tags.map { it.lowercase() }.toSet()
        return books.asSequence()
            .filter {
                when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.ON_KOBO -> it.koboPath != null
                    LibraryFilter.NOT_ON_KOBO -> it.koboPath == null
                    LibraryFilter.CHANGED -> it.changedSinceOnKobo
                }
            }
            .filter { b -> collection == null || b.collections.any { it.equals(collection, ignoreCase = true) } }
            .filter { b ->
                if (wantedTags.isEmpty()) return@filter true
                val has = b.meta.tags.map { it.lowercase() }.toSet()
                if (tagsMatchAll) has.containsAll(wantedTags) else has.any { it in wantedTags }
            }
            .filter { b -> node == null || node.matches(b, reading) }
            .sortedWith(comparator(sort, descending, reading, here))
            .toList()
    }
}
