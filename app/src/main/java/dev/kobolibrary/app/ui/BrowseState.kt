package dev.kobolibrary.app.ui

import dev.kobolibrary.app.tr
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kobolibrary.app.kobo.BookKind
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboTagStore
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.ui.library.LibraryQuery
import dev.kobolibrary.app.ui.library.SearchFacts

enum class SourceFilter(val label: String) { ALL(tr("All")), SIDELOADED(tr("Sideloaded")), STORE(tr("Kobo Store")) }

enum class StatusFilter(val label: String, val status: ReadStatus) {
    UNREAD(tr("Unread"), ReadStatus.UNREAD),
    READING(tr("Reading"), ReadStatus.READING),
    FINISHED(tr("Finished"), ReadStatus.FINISHED),
}

/** Books on the Kobo that do or don't have a copy in the library. */
enum class InLibraryFilter(val label: String) {
    IN(tr("In library")),
    NOT_IN(tr("Not in library")),
}

/** The Kobo's sort orders: the library's, plus reading progress. Each starts in the direction that's usually wanted. */
enum class KoboSort(val label: String, val startsDescending: Boolean, val ascending: String, val descending: String) {
    TITLE(tr("Title"), false, tr("A to Z"), tr("Z to A")),
    AUTHOR(tr("Author"), false, tr("A to Z"), tr("Z to A")),
    SERIES(tr("Series"), false, tr("A to Z"), tr("Z to A")),
    PUBLISHED(tr("Published"), true, tr("oldest first"), tr("newest first")),
    ADDED(tr("Date added"), true, tr("oldest first"), tr("newest first")),
    SIZE(tr("File size"), true, tr("smallest first"), tr("largest first")),
    PAGES(tr("Pages"), true, tr("shortest first"), tr("longest first")),
    RATING(tr("Rating"), true, tr("lowest first"), tr("highest first")),
    LAST_READ(tr("Last read"), true, tr("least recent first"), tr("most recent first")),
    PROGRESS(tr("Progress"), true, tr("least read first"), tr("most read first")),
}

/** Book list settings and scroll positions, kept while switching tabs. */
class BrowseState {
    var query by mutableStateOf("")
    var source by mutableStateOf(SourceFilter.ALL)
    var status by mutableStateOf<StatusFilter?>(null)
    var inLibrary by mutableStateOf<InLibraryFilter?>(null)
    var collection by mutableStateOf<String?>(null)
    var tags by mutableStateOf<Set<String>>(emptySet())
    /** With several tags chosen: books need all of them (true) or any one (false). */
    var tagsMatchAll by mutableStateOf(true)
    var sort by mutableStateOf(KoboSort.TITLE)
    var descending by mutableStateOf(false)
    var grid by mutableStateOf(true)
    var selectedBookId by mutableStateOf<String?>(null)

    /** Which of the Kobo's tabs is showing: books, collections or device. */
    var deviceTab by mutableIntStateOf(0)

    /** The Collections tab: the collection open in it, and the book of it open beside it. */
    var collectionKey by mutableStateOf<String?>(null)
    var collectionBookId by mutableStateOf<String?>(null)
    val collectionScroll = CollectionScroll()
    val gridState = LazyGridState()
    val listState = LazyListState()

    /** Picking several books (to add them to the library). */
    var selecting by mutableStateOf(false)
    val picked = mutableStateMapOf<String, Boolean>()
    private val range = PickRange()

    /** A tap while picking: picks [contentId], or unpicks it. */
    fun togglePicked(contentId: String) = range.toggle(picked, contentId)

    /** A hold: see [PickRange.hold]; [shown] is the books in the order they're shown. */
    fun holdPick(contentId: String, shown: List<String>) {
        if (!selecting) {
            selecting = true
            picked.clear()
            range.reset()
        }
        range.hold(picked, contentId, shown)
    }

    fun stopSelecting() {
        selecting = false
        picked.clear()
        range.reset()
    }

    /** Picks [order], or flips the direction if it's already the order. */
    fun sortBy(order: KoboSort) {
        if (sort == order) {
            descending = !descending
        } else {
            sort = order
            descending = order.startsDescending
        }
    }

    val sortDescription: String get() = "${dev.kobolibrary.app.L10n.midSentence(sort.label)}, ${if (descending) sort.descending else sort.ascending}"

    val narrowed: Boolean
        get() = query.isNotBlank() || source != SourceFilter.ALL || status != null || inLibrary != null || collection != null || tags.isNotEmpty()

    fun clearFilters() {
        query = ""
        source = SourceFilter.ALL
        status = null
        inLibrary = null
        collection = null
        tags = emptySet()
    }
}

/** Runs of spaces, to compare titles and names whatever their spacing. */
private val spaces = Regex("\\s+")

/**
 * What's known about the Kobo's books beyond its database: the library's copies (matched by path) and
 * the tags read from the books' files, which the Kobo's database doesn't keep.
 */
class KoboExtras(libraryBooks: List<LibraryBook> = emptyList(), private val fileTags: Map<String, KoboTagStore.Entry> = emptyMap()) {
    private val byPath: Map<String, LibraryBook> = libraryBooks.mapNotNull { b -> b.koboPath?.let { it.lowercase() to b } }.toMap()

    val libraryPaths: Set<String> get() = byPath.keys

    fun libraryCopy(b: KoboBook): LibraryBook? = b.relativePath?.lowercase()?.let { byPath[it] }
    fun inLibrary(b: KoboBook): Boolean = libraryCopy(b) != null

    private val byTitle: Map<String, List<LibraryBook>> = libraryBooks.groupBy { sameText(it.meta.title) }

    /**
     * The library copy whose cover a book on the Kobo shows: the copy it was sent from or added to the library as;
     * or else, for a book put on the Kobo some other way, the one book in the library with the same title, first
     * author and number in its series.
     */
    fun coverCopy(b: KoboBook): LibraryBook? = libraryCopy(b) ?: synchronized(coverCopies) {
        // Asked for every cover each time it's drawn: worked out once for each book.
        if (b.contentId in coverCopies) coverCopies[b.contentId] else sameBook(b).also { coverCopies[b.contentId] = it }
    }

    private val coverCopies = HashMap<String, LibraryBook?>()

    private fun sameBook(b: KoboBook): LibraryBook? {
        val author = b.author?.split(BookSorting.authorSplit)?.firstOrNull { it.isNotBlank() }?.let(::sameText)
        val number = b.seriesNumber?.trim()?.replace(',', '.')?.toDoubleOrNull()
        return byTitle[sameText(b.title)].orEmpty().filter { c ->
            c.meta.authors.firstOrNull()?.let(::sameText) == author &&
                (number == null || c.meta.seriesIndex == null || c.meta.seriesIndex == number)
        }.singleOrNull()
    }

    private fun sameText(s: String) = s.trim().replace(spaces, " ").lowercase()

    private fun fromFile(b: KoboBook): KoboTagStore.Entry? = b.relativePath?.lowercase()?.let { fileTags[it] }

    fun tags(b: KoboBook): List<String> = libraryCopy(b)?.meta?.tags ?: fromFile(b)?.tags.orEmpty()

    /** Stars given on the Kobo, or else the book's own rating (out of 10, as Calibre keeps it) halved. */
    fun stars(b: KoboBook): Double =
        b.rating?.toDouble() ?: ((libraryCopy(b)?.meta?.rating ?: fromFile(b)?.rating)?.takeIf { it > 0 }?.let { it / 2.0 }) ?: 0.0

    fun pages(b: KoboBook): Int? = libraryCopy(b)?.pages
        ?: b.words?.let { w -> maxOf(1, (w + LibraryBook.WORDS_PER_PAGE - 1) / LibraryBook.WORDS_PER_PAGE) }

    fun facts(b: KoboBook, collections: List<String>): SearchFacts = KoboFacts(b, collections, this)
}

/** A book on the Kobo as search facts. */
class KoboFacts(val book: KoboBook, override val collections: List<String>, private val extras: KoboExtras) : SearchFacts {
    private val copy = extras.libraryCopy(book)
    override val title get() = book.title
    override val authors: List<String>
        get() = copy?.meta?.authors ?: book.author?.split(BookSorting.authorSplit)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    override val series get() = book.series
    override val tags get() = extras.tags(book)
    override val publisher get() = book.publisher
    override val language get() = book.language
    override val isbn get() = book.isbn
    override val stars get() = extras.stars(book)
    override val pages get() = extras.pages(book)
    override val year get() = BookSorting.year(book)
    override val onKobo get() = true
    override val changedSinceOnKobo get() = copy?.changedSinceOnKobo == true
    override val readStatus get() = book.readStatus
    override val inLibrary get() = copy != null
    override val format get() = book.format
}

object BookSorting {
    private val leadingArticle = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)
    val authorSplit = Regex("\\s*(,|&|;|\\band\\b)\\s*")

    fun titleKey(title: String): String = title.trim().replace(leadingArticle, "").lowercase()

    fun authorKey(author: String?): String {
        if (author.isNullOrBlank()) return "￿"
        val first = author.split(authorSplit).firstOrNull { it.isNotBlank() } ?: author
        val last = first.trim().split(Regex("\\s+")).last()
        return (last + " " + first).lowercase()
    }

    fun year(b: KoboBook): Int? = b.published?.trim()?.take(4)?.toIntOrNull()?.takeIf { it > 101 }

    private fun seriesNumber(book: KoboBook): Double = book.seriesNumber?.toDoubleOrNull() ?: Double.MAX_VALUE

    private val byTitle = compareBy<KoboBook> { titleKey(it.title) }

    /** Reading progress as one number: finished books are 100, unread ones 0. */
    fun progress(b: KoboBook): Int = when (b.readStatus) {
        ReadStatus.FINISHED -> 100
        ReadStatus.UNREAD -> 0
        ReadStatus.READING -> b.percentRead.coerceIn(1, 99)
    }

    private fun primary(order: KoboSort, extras: KoboExtras): Comparator<KoboBook> = when (order) {
        KoboSort.TITLE -> byTitle
        KoboSort.AUTHOR -> compareBy { authorKey(it.author) }
        KoboSort.SERIES -> compareBy { it.series?.lowercase() ?: "" }
        KoboSort.PUBLISHED -> compareBy { it.published?.take(10) ?: "" }
        KoboSort.ADDED -> compareBy { it.addedMillis ?: 0L }
        KoboSort.SIZE -> compareBy { it.fileSize ?: 0L }
        KoboSort.PAGES -> compareBy { extras.pages(it) ?: 0 }
        KoboSort.RATING -> compareBy { extras.stars(it) }
        KoboSort.LAST_READ -> compareBy { it.lastReadMillis ?: 0L }
        KoboSort.PROGRESS -> compareBy { progress(it) }
    }

    /** Ties are broken the same way whichever direction: series in order, then A to Z. */
    private fun ties(order: KoboSort): Comparator<KoboBook> = when (order) {
        KoboSort.TITLE -> compareBy { it.contentId }
        KoboSort.AUTHOR, KoboSort.SERIES -> compareBy<KoboBook> { it.series?.lowercase() ?: "￿" }
            .thenBy { seriesNumber(it) }
            .then(byTitle)
        else -> byTitle
    }

    /** Books without the sorted value go after the rest either way. */
    private fun missing(order: KoboSort, b: KoboBook, extras: KoboExtras): Boolean = when (order) {
        KoboSort.AUTHOR -> b.author.isNullOrBlank()
        KoboSort.SERIES -> b.series.isNullOrBlank()
        KoboSort.PUBLISHED -> year(b) == null
        KoboSort.ADDED -> b.addedMillis == null
        KoboSort.SIZE -> b.fileSize == null
        KoboSort.PAGES -> extras.pages(b) == null
        KoboSort.RATING -> extras.stars(b) == 0.0
        KoboSort.LAST_READ -> b.lastReadMillis == null
        else -> false
    }

    fun comparator(order: KoboSort, descending: Boolean = order.startsDescending, extras: KoboExtras = KoboExtras()): Comparator<KoboBook> {
        val main = primary(order, extras)
        return compareBy<KoboBook> { missing(order, it, extras) }
            .then(if (descending) main.reversed() else main)
            .then(ties(order))
    }

    fun filter(
        books: List<KoboBook>,
        collectionsByBook: Map<String, List<String>>,
        query: String,
        source: SourceFilter,
        status: StatusFilter?,
        sort: KoboSort,
        descending: Boolean = sort.startsDescending,
        collection: String? = null,
        tags: Set<String> = emptySet(),
        tagsMatchAll: Boolean = true,
        extras: KoboExtras = KoboExtras(),
        inLibrary: InLibraryFilter? = null,
    ): List<KoboBook> {
        val node = LibraryQuery.parse(query)
        val wantedTags = tags.map { it.lowercase() }.toSet()
        return books.asSequence()
            .filter {
                when (source) {
                    SourceFilter.ALL -> true
                    SourceFilter.SIDELOADED -> it.kind == BookKind.SIDELOADED
                    SourceFilter.STORE -> it.kind == BookKind.PURCHASED
                }
            }
            .filter { status == null || it.readStatus == status.status }
            .filter { b ->
                when (inLibrary) {
                    null -> true
                    InLibraryFilter.IN -> extras.inLibrary(b)
                    InLibraryFilter.NOT_IN -> !extras.inLibrary(b)
                }
            }
            .filter { b -> collection == null || collectionsByBook[b.contentId]?.any { it.equals(collection, ignoreCase = true) } == true }
            .filter { b ->
                if (wantedTags.isEmpty()) return@filter true
                val has = extras.tags(b).map { it.lowercase() }.toSet()
                if (tagsMatchAll) has.containsAll(wantedTags) else has.any { it in wantedTags }
            }
            .filter { b ->
                // The file name is searched too, as it often holds what the database lacks.
                node == null || node.matches(extras.facts(b, collectionsByBook[b.contentId].orEmpty())) ||
                    (query.isNotBlank() && !query.contains(':') && b.relativePath?.contains(query.trim(), ignoreCase = true) == true)
            }
            .sortedWith(comparator(sort, descending, extras))
            .toList()
    }
}
