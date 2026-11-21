package dev.kolib.app.ui

import dev.kolib.app.ui.components.ShelfMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.kobo.KoboSnapshot
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.tr
import dev.kolib.app.ui.components.ClearFiltersLine
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.SheetHeading
import dev.kolib.app.ui.components.ShelfExtras
import dev.kolib.app.ui.components.SortChoices
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibraryFilter
import dev.kolib.app.ui.library.LibrarySort
import dev.kolib.app.ui.library.ComicShow
import dev.kolib.app.ui.library.ComicSort

// The filters and orders of the library's and the Kobo's shelves, on the tabs of the sheet their one button opens
// (as Mihon's Filter, Sort and Display), and the line over the books that says what's showing.

/** A filter's on in the library (the search aside). */
val LibraryBrowseState.filtered: Boolean get() = filter != LibraryFilter.ALL || collection != null || tags.isNotEmpty()

/** A filter's on in the Kobo's books (the search aside). */
val BrowseState.filtered: Boolean get() = source != SourceFilter.ALL || status != null || inLibrary != null || collection != null || tags.isNotEmpty()

private fun tagWords(tags: Set<String>, all: Boolean): String? = when (tags.size) {
    0 -> null
    1 -> tags.first()
    else -> if (all) tr("{0} tags (all)", tags.size) else tr("{0} tags (any)", tags.size)
}

/** The library's filters that are on, in a few words: "On Kobo · Classics". */
fun LibraryBrowseState.filterWords(): String? =
    listOfNotNull(filter.takeIf { it != LibraryFilter.ALL }?.label, collection, tagWords(tags, tagsMatchAll)).joinToString(" · ").ifEmpty { null }

/** The Kobo's filters that are on, in a few words. */
fun BrowseState.filterWords(): String? =
    listOfNotNull(source.takeIf { it != SourceFilter.ALL }?.label, status?.label, inLibrary?.label, collection, tagWords(tags, tagsMatchAll))
        .joinToString(" · ").ifEmpty { null }

/** The library's one button over its books: its filters, its order, and how the books show. */
@Composable
fun libraryShelfExtras(all: List<LibraryBook>, browse: LibraryBrowseState) = ShelfExtras(
    filter = { LibraryFilterChoices(all, browse) },
    sort = {
        SortChoices(LibrarySort.entries, browse.sort, browse.descending, { it.label }, { o, d -> if (d) o.descending else o.ascending }) { browse.sortBy(it) }
    },
    filtered = browse.filtered,
)

/** The Kobo's one button over its books. */
@Composable
fun koboShelfExtras(snapshot: KoboSnapshot, extras: KoboExtras, browse: BrowseState) = ShelfExtras(
    filter = { KoboFilterChoices(snapshot, extras, browse) },
    sort = {
        SortChoices(KoboSort.entries, browse.sort, browse.descending, { it.label }, { o, d -> if (d) o.descending else o.ascending }) { browse.sortBy(it) }
    },
    filtered = browse.filtered,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.LibraryFilterChoices(all: List<LibraryBook>, browse: LibraryBrowseState) {
    val collections = remember(all) { all.flatMap { it.collections }.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val tagCounts = remember(all) {
        all.flatMap { b -> b.meta.tags.distinctBy { it.lowercase() } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).map { it.key to it.value }
    }
    SheetHeading(tr("Show"), top = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LibraryFilter.entries.forEach { f ->
            FilterChip(
                selected = browse.filter == f,
                onClick = { browse.filter = f },
                label = { Text(f.label) },
                modifier = Modifier.testTag("filter-${f.name.lowercase()}"),
            )
        }
    }
    if (collections.isNotEmpty() || tagCounts.isNotEmpty()) {
        SheetHeading(tr("Collection and tags"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PhoneCollectionChip(collections, browse.collection) { browse.collection = it }
            PhoneTagsChip(tagCounts, browse.tags, browse.tagsMatchAll) { chosen, matchAll ->
                browse.tags = chosen
                browse.tagsMatchAll = matchAll
            }
        }
    }
    ClearFiltersLine(browse.filtered) {
        browse.filter = LibraryFilter.ALL
        browse.collection = null
        browse.tags = emptySet()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.KoboFilterChoices(snapshot: KoboSnapshot, extras: KoboExtras, browse: BrowseState) {
    val collections = remember(snapshot) {
        snapshot.collections.filter { c -> c.memberIds.any { id -> snapshot.booksById[id]?.isOnDevice == true } }
            .map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val tagCounts = remember(snapshot, extras) {
        snapshot.books.flatMap { b -> extras.tags(b).distinctBy { it.lowercase() } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).map { it.key to it.value }
    }
    SheetHeading(tr("Show"), top = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SourceFilter.entries.forEach { f ->
            FilterChip(selected = browse.source == f, onClick = { browse.source = f }, label = { Text(f.label) })
        }
    }
    SheetHeading(tr("Reading"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusFilter.entries.forEach { f ->
            PhoneToggleChip(f.label, browse.status == f) { browse.status = if (browse.status == f) null else f }
        }
    }
    SheetHeading(tr("Library"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InLibraryFilter.entries.forEach { f ->
            PhoneToggleChip(f.label, browse.inLibrary == f) { browse.inLibrary = if (browse.inLibrary == f) null else f }
        }
    }
    if (collections.isNotEmpty() || tagCounts.isNotEmpty()) {
        SheetHeading(tr("Collection and tags"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PhoneCollectionChip(collections, browse.collection) { browse.collection = it }
            PhoneTagsChip(tagCounts, browse.tags, browse.tagsMatchAll) { chosen, matchAll ->
                browse.tags = chosen
                browse.tagsMatchAll = matchAll
            }
        }
    }
    ClearFiltersLine(browse.filtered) {
        browse.source = SourceFilter.ALL
        browse.status = null
        browse.inLibrary = null
        browse.collection = null
        browse.tags = emptySet()
    }
}

/**
 * Over the books, where the filters' chips were: how many are showing (of how many), what's narrowing them, the
 * order they're in, and while searching or filtering, a way back to them all.
 */
@Composable
fun ShelfLine(shown: Int, total: Int, narrowed: Boolean, filters: String?, order: String, onClear: () -> Unit, modifier: Modifier = Modifier, extra: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOfNotNull(
                if (shown == total) Fmt.plural(total, "book") else tr("{0} of {1}", shown, Fmt.plural(total, "book")),
                filters,
                order,
                extra,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp).weight(1f).testTag("shelf-line"),
        )
        if (narrowed) TextButton(onClick = onClear, modifier = Modifier.testTag("shelf-clear")) { Text(tr("Clear")) }
    }
}

/** The Comics tab's one button: which series show, their order, and how they show (and, wide, where a series opens). */
@Composable
fun comicShelfExtras(browse: LibraryBrowseState, wide: Boolean) = ShelfExtras(
    filter = { ComicFilterChoices(browse) },
    sort = { SortChoices(ComicSort.entries, browse.comicSort, false, { it.label }, { _, _ -> "" }, turns = false) { browse.comicSort = it } },
    filtered = browse.comicsFiltered,
    display = if (wide) ({ SeriesOpenChoice(browse) }) else null,
)

/** The comics' filters that are on, in a few words. */
fun LibraryBrowseState.comicFilterWords(): String? =
    listOfNotNull(comicShow.takeIf { it != ComicShow.ALL }?.label, comicOnKobo?.let { if (it) tr("On Kobo") else tr("Not on Kobo") })
        .joinToString(" · ").ifEmpty { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ComicFilterChoices(browse: LibraryBrowseState) {
    SheetHeading(tr("Show"), top = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ComicShow.entries.forEach { f ->
            FilterChip(selected = browse.comicShow == f, onClick = { browse.comicShow = f }, label = { Text(f.label) }, modifier = Modifier.testTag("comic-filter-${f.name.lowercase()}"))
        }
    }
    SheetHeading(tr("Kobo"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PhoneToggleChip(tr("On Kobo"), browse.comicOnKobo == true) { browse.comicOnKobo = if (browse.comicOnKobo == true) null else true }
        PhoneToggleChip(tr("Not on Kobo"), browse.comicOnKobo == false) { browse.comicOnKobo = if (browse.comicOnKobo == false) null else false }
    }
    ClearFiltersLine(browse.comicsFiltered) {
        browse.comicShow = ComicShow.ALL
        browse.comicOnKobo = null
    }
}

/** On a wide screen, where a series opens when it's tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SeriesOpenChoice(browse: LibraryBrowseState) {
    SheetHeading(tr("When you tap a series"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = !browse.seriesInPlace, onClick = { browse.openSeriesInPlace(false) }, label = { Text(tr("Show it beside your comics")) }, modifier = Modifier.testTag("series-beside"))
        FilterChip(selected = browse.seriesInPlace, onClick = { browse.openSeriesInPlace(true) }, label = { Text(tr("Show it full screen")) }, modifier = Modifier.testTag("series-in-place"))
    }
}


/**
 * A tap on the Library tab while it's showing: what's open over the shelf closes (a book, a series, a collection), or,
 * with nothing open, the shelf goes back to its top.
 */
suspend fun dev.kolib.app.ui.library.LibraryBrowseState.backToTop() {
    when (tab) {
        2 -> when {
            comicBookId != null -> comicBookId = null
            comicKey != null -> {
                comicKey = null
                if (comicSelecting == dev.kolib.app.ui.library.ComicSelecting.ISSUES) stopComicSelecting()
            }
            comicMode == ShelfMode.LIST -> comicList.animateScrollToItem(0)
            else -> comicGrid.animateScrollToItem(0)
        }
        1 -> when {
            collectionBookId != null -> collectionBookId = null
            collectionKey != null -> collectionKey = null
        }
        else -> when {
            selectedId != null -> selectedId = null
            grid -> gridState.animateScrollToItem(0)
            else -> listState.animateScrollToItem(0)
        }
    }
}

/** The same for the Kobo tab: a book or collection open closes, or the books go back to their top. */
suspend fun BrowseState.backToTop() {
    when (deviceTab) {
        0 -> when {
            selectedBookId != null -> selectedBookId = null
            grid -> gridState.animateScrollToItem(0)
            else -> listState.animateScrollToItem(0)
        }
        1 -> when {
            collectionBookId != null -> collectionBookId = null
            collectionKey != null -> collectionKey = null
        }
    }
}
