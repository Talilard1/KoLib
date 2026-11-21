package dev.kolib.app.ui.library

import dev.kolib.app.ui.components.invertPicks
import dev.kolib.app.ui.PickRange

import dev.kolib.app.tr
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.ui.BookSorting

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

/** How the Comics tab is sorted. */
enum class ComicSort(val label: String) {
    TITLE(tr("Title")),
    READ(tr("Last read on this device")),
    ADDED(tr("Date added")),
}

/** Which series the Comics tab shows: all of them, or by how far they've been read. */
enum class ComicShow(val label: String) {
    // In the words the Kobo's books are filtered by: a series being read is one started with comics still unread.
    ALL(tr("All")), UNREAD(tr("Unread")), STARTED(tr("Reading")), READ(tr("Finished"))
}

/** What's being chosen in the Comics tab: series, or comics of the series open. */
enum class ComicSelecting { SERIES, ISSUES }

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
    /** How the books show: as covers (with their words under them, on them, or none) or a list. */
    var mode by mutableStateOf(dev.kolib.app.ui.components.ShelfMode.COMFORTABLE)
    /** The books show as covers, not a list. */
    val grid: Boolean get() = mode != dev.kolib.app.ui.components.ShelfMode.LIST
    var selectedId by mutableStateOf<String?>(null)
    /** Which tab is showing: the books (0), the collections (1) or the comics (2). */
    var tab by mutableIntStateOf(0)
    /** The Collections tab: the collection open in it (by its key, see libraryCollections), and the book of it open beside it. */
    var collectionKey by mutableStateOf<String?>(null)
    var collectionBookId by mutableStateOf<String?>(null)
    val collectionScroll = dev.kolib.app.ui.CollectionScroll()
    val gridState = LazyGridState()
    val listState = LazyListState()

    /** The Comics tab: its search and order, the series open in it (by its key, see ComicSeries), and comics being chosen. */
    var comicQuery by mutableStateOf("")
    var comicSort by mutableStateOf(ComicSort.TITLE)
    var comicKey by mutableStateOf<String?>(null)
    /** A comic of the series open, shown with its details in the series' place. */
    var comicBookId by mutableStateOf<String?>(null)
    /** The series chosen in the Comics tab (by key), or the comics chosen in the series open (by id). */
    val comicPicked = mutableStateMapOf<String, Boolean>()
    var comicSelecting by mutableStateOf<ComicSelecting?>(null)
    /** The series' comics newest first. */
    var comicIssuesDescending by mutableStateOf(false)
    val comicGrid = LazyGridState()
    val comicIssues = LazyListState()
    val comicIssueGrid = LazyGridState()

    /** The Comics tab is there; without it, comics are in the Books tab with the rest. */
    var comicsTab by mutableStateOf(true)
    /** On a wide screen, a series opens in place of the shelf (with no side panel), rather than beside it. */
    var seriesInPlace by mutableStateOf(false)
    /** Which series the Comics tab shows, by how far they've been read, and by whether they're on the Kobo. */
    var comicShow by mutableStateOf(ComicShow.ALL)
    var comicOnKobo by mutableStateOf<Boolean?>(null)
    val comicsFiltered: Boolean get() = comicShow != ComicShow.ALL || comicOnKobo != null
    /** How the Comics tab shows its series: as covers (and how), or a list. */
    var comicMode by mutableStateOf(dev.kolib.app.ui.components.ShelfMode.COMFORTABLE)
    val comicList = androidx.compose.foundation.lazy.LazyListState()
    /** How a series open in place of the shelf shows its comics: as covers (and how), or a list. */
    var seriesMode by mutableStateOf(dev.kolib.app.ui.components.ShelfMode.LIST)
    /** A series open in place of the shelf shows its comics as a grid of covers, rather than a list. */
    var seriesGrid: Boolean
        get() = seriesMode != dev.kolib.app.ui.components.ShelfMode.LIST
        set(value) { seriesMode = if (value) dev.kolib.app.ui.components.ShelfMode.COMFORTABLE else dev.kolib.app.ui.components.ShelfMode.LIST }

    fun stopComicSelecting() {
        comicSelecting = null
        comicPicked.clear()
    }

    /**
     * A series is to open in place of the shelf, or beside it. Changed to in its place, what was open beside the shelf
     * (a series, or a comic's details) closes: the shelf shows, not what had been beside it.
     */
    fun openSeriesInPlace(inPlace: Boolean) {
        if (inPlace && !seriesInPlace) {
            comicKey = null
            comicBookId = null
            if (comicSelecting == ComicSelecting.ISSUES) stopComicSelecting()
        }
        seriesInPlace = inPlace
    }

    /** Choosing several books (to send them, for instance). */
    var selecting by mutableStateOf(false)
    val picked = mutableStateMapOf<String, Boolean>()
    private val range = PickRange()

    /** A tap while choosing: chooses [id], or unchooses it. */
    fun togglePicked(id: String) = range.toggle(picked, id)

    /** A hold: see [PickRange.hold]; [shown] is the books in the order they're shown. */
    fun holdPick(id: String, shown: List<String>) {
        if (!selecting) {
            selecting = true
            picked.clear()
            range.reset()
        }
        range.hold(picked, id, shown)
    }

    /** Nothing chosen, still choosing. */
    fun deselectAll() {
        picked.clear()
        range.reset()
    }

    /** What's chosen of [shown] isn't, and what isn't is. */
    fun invertPicks(shown: List<String>) {
        picked.invertPicks(shown)
        range.reset()
    }

    fun stopSelecting() {
        selecting = false
        picked.clear()
        range.reset()
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

    val sortDescription: String get() = "${dev.kolib.app.L10n.midSentence(sort.label)}, ${if (descending) sort.descending else sort.ascending}"

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

/**
 * What the library is showing, as the AI is told it (in English, as the rest of what it's told is): which books,
 * picked out how and in what order, and which of them is open.
 */
fun libraryOnScreen(browse: LibraryBrowseState, visible: List<dev.kolib.app.library.LibraryBook>, open: String?): dev.kolib.app.ui.ai.LibraryOnScreen {
    if (browse.tab == 1) return dev.kolib.app.ui.ai.LibraryOnScreen("The library's Collections tab.", emptyList(), browse.collectionBookId)
    val which = when (browse.filter) {
        LibraryFilter.ALL -> "books"
        LibraryFilter.ON_KOBO -> "books that are on the Kobo"
        LibraryFilter.NOT_ON_KOBO -> "books that aren't on the Kobo"
        LibraryFilter.CHANGED -> "books changed since they were sent to the Kobo"
    }
    val order = when (browse.sort) {
        LibrarySort.TITLE -> "title"
        LibrarySort.AUTHOR -> "author"
        LibrarySort.SERIES -> "series"
        LibrarySort.PUBLISHED -> "date published"
        LibrarySort.ADDED -> "date added"
        LibrarySort.EDITED -> "when last edited"
        LibrarySort.SIZE -> "file size"
        LibrarySort.PAGES -> "length"
        LibrarySort.RATING -> "rating"
        LibrarySort.LAST_READ -> "when last read on the Kobo"
        LibrarySort.READ_HERE -> "when last read in KoLib"
    }
    val describe = buildString {
        append("The library, showing ").append(visible.size).append(' ').append(if (visible.size == 1) which.replaceFirst("books", "book") else which)
        browse.collection?.takeIf { it.isNotBlank() }?.let { append(" in the collection “").append(it).append("”") }
        if (browse.tags.isNotEmpty()) append(" tagged ").append(browse.tags.joinToString(if (browse.tagsMatchAll) " and " else " or ") { "“$it”" })
        browse.query.trim().takeIf { it.isNotEmpty() }?.let { append(" that match the search “").append(it).append("”") }
        append(", by ").append(order).append(if (browse.descending != browse.sort.startsDescending) ", the other way round" else "").append(", in this order.")
    }
    return dev.kolib.app.ui.ai.LibraryOnScreen(describe, visible.map { it.id }, open)
}
