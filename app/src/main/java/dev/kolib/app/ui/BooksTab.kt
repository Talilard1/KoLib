@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package dev.kolib.app.ui

import dev.kolib.app.ui.components.hasAll
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import dev.kolib.app.ui.tour.tourPlace
import dev.kolib.app.ui.components.UnderCover
import dev.kolib.app.ui.components.DisplayButton
import dev.kolib.app.ui.components.shelfMinCell
import dev.kolib.app.ui.components.shelfCells
import dev.kolib.app.ui.components.chosenPerRow
import dev.kolib.app.ui.components.shortScreen
import dev.kolib.app.ui.components.HidingTop
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import dev.kolib.app.ui.components.BarAction
import dev.kolib.app.ui.components.BarActions
import dev.kolib.app.ui.components.LocalActionMaxWidth
import dev.kolib.app.ui.components.SelectAllButtons
import dev.kolib.app.ui.components.AtFoot
import dev.kolib.app.tr
import dev.kolib.app.ui.components.seriesSearch
import dev.kolib.app.ui.components.DetailText
import dev.kolib.app.ui.components.titleSearch
import dev.kolib.app.ui.components.authorSearch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import dev.kolib.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.kolib.app.ui.components.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import dev.kolib.app.ui.components.SeriesStyle
import dev.kolib.app.ui.components.AlertDialog
import dev.kolib.app.ui.components.OutlinedButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.kobo.BookKind
import dev.kolib.app.kobo.CoverSize
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboCoverLoader
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.kobo.KoboSnapshot
import dev.kolib.app.ui.library.SearchHelpDialog
import dev.kolib.app.ui.library.TagPickerDialog
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.foundation.layout.heightIn
import dev.kolib.app.kobo.ReadStatus
import dev.kolib.app.ui.components.BookCover
import dev.kolib.app.ui.components.EmptyState
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.InfoRow
import dev.kolib.app.ui.components.SectionCard
import dev.kolib.app.ui.theme.LocalBookcase
import dev.kolib.app.ui.theme.LocalKoLibStyle
import dev.kolib.app.ui.theme.ShelfColors
import dev.kolib.app.ui.components.ShelfSpace
import dev.kolib.app.ui.components.shelfUnderCover
import androidx.compose.material3.ButtonDefaults
import dev.kolib.app.ui.components.ShelfGridBox
import dev.kolib.app.ui.components.gridCover
import dev.kolib.app.ui.components.phoneLayout
import dev.kolib.app.ui.components.TabBackHandler

/** Width at which a list and its details sit side by side. */
val TwoPaneWidth = 900.dp

@Composable
fun BooksTab(
    view: KoboView,
    live: Boolean,
    browse: BrowseState,
    extras: KoboExtras,
    onAddToLibrary: (List<KoboBook>) -> Unit,
    onDeleteFromKobo: (List<KoboBook>) -> Unit,
    /** While tags are being read from the books on the Kobo: done and total. */
    readingTags: Pair<Int, Int>? = null,
) {
    val snapshot = view.snapshot
    val visible = remember(
        snapshot, extras, browse.query, browse.source, browse.status, browse.inLibrary, browse.sort, browse.descending,
        browse.collection, browse.tags, browse.tagsMatchAll,
    ) {
        BookSorting.filter(
            snapshot.books, snapshot.collectionsByBook, browse.query, browse.source, browse.status, browse.sort, browse.descending,
            browse.collection, browse.tags, browse.tagsMatchAll, extras, browse.inLibrary,
        )
    }
    val selected = browse.selectedBookId?.let { snapshot.booksById[it] }
    // On a phone a book opens on a page of its own, over the Kobo's bar and tabs (see DeviceScreen).
    if (phoneLayout()) {
        PhoneKoboBrowser(view, live, browse, visible, extras, onAddToLibrary, onDeleteFromKobo, readingTags)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPanes = maxWidth >= TwoPaneWidth
        val detail: @Composable (KoboBook, (() -> Unit)?) -> Unit = { book, onBack ->
            BookDetailPane(
                book,
                snapshot.collectionsByBook[book.contentId].orEmpty(),
                view.covers,
                inLibrary = extras.inLibrary(book),
                onBack = onBack,
                onOpenCollection = { name ->
                    browse.collection = name
                    if (!twoPanes) browse.selectedBookId = null
                },
                onAddToLibrary = if (live) ({ onAddToLibrary(listOf(book)) }) else null,
                onDeleteFromKobo = if (live && book.isSideloaded) ({
                    if (browse.selectedBookId == book.contentId) browse.selectedBookId = null
                    onDeleteFromKobo(listOf(book))
                }) else null,
                tags = extras.tags(book),
                onTag = { tag ->
                    browse.tags = setOf(tag)
                    if (!twoPanes) browse.selectedBookId = null
                },
                onSearch = { search ->
                    browse.query = search
                    browse.collection = null
                    browse.tags = emptySet()
                    if (!twoPanes) browse.selectedBookId = null
                },
                stars = extras.stars(book),
                pages = extras.pages(book),
            )
        }
        if (twoPanes) {
            // The books have the whole width till one's tapped; its details then open beside them, closed with ✕ or Back.
            if (selected != null) TabBackHandler { browse.selectedBookId = null }
            Row(Modifier.fillMaxSize()) {
                BookBrowser(view, live, browse, visible, selected?.contentId, extras, onAddToLibrary, onDeleteFromKobo, Modifier.weight(1f), readingTags)
                if (selected != null) {
                    VerticalDivider()
                    dev.kolib.app.ui.components.SidePane(onClose = { browse.selectedBookId = null }, modifier = Modifier.width(this@BoxWithConstraints.maxWidth * 0.41f).fillMaxHeight()) {
                        detail(selected, null)
                    }
                }
            }
        } else if (selected != null && !browse.selecting) {
            TabBackHandler { browse.selectedBookId = null }
            detail(selected) { browse.selectedBookId = null }
        } else {
            BookBrowser(view, live, browse, visible, null, extras, onAddToLibrary, onDeleteFromKobo, Modifier.fillMaxSize(), readingTags)
        }
    }
}

@Composable
private fun BookBrowser(
    view: KoboView,
    live: Boolean,
    browse: BrowseState,
    books: List<KoboBook>,
    selectedId: String?,
    extras: KoboExtras,
    onAddToLibrary: (List<KoboBook>) -> Unit,
    onDeleteFromKobo: (List<KoboBook>) -> Unit,
    modifier: Modifier,
    readingTags: Pair<Int, Int>?,
) {
    val inLibrary: (KoboBook) -> Boolean = { extras.inLibrary(it) }
    if (browse.selecting) TabBackHandler { browse.stopSelecting() }
    var confirmDelete by remember { mutableStateOf<List<KoboBook>?>(null) }
    Column(modifier.fillMaxHeight()) {
        // On a short screen, the search and filters slide away as the books are scrolled, once the bar and tabs have.
        HidingTop(top = { BrowserControls(browse, books.size, view.snapshot, extras, readingTags) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The books, and while some are being chosen, the bar for them along the foot of the screen, its whole width.
            Box(Modifier.fillMaxSize()) { KoboBookList(view, browse, books, selectedId, extras, inLibrary) }
        }
        if (browse.selecting) AtFoot {
            val chosen = { books.filter { browse.picked.containsKey(it.contentId) } }
            val choosable = books.filter { it.isSideloaded && !inLibrary(it) }.map { it.contentId }
            SelectionBar(
                count = browse.picked.size,
                onSelectAll = { choosable.forEach { browse.picked[it] = true } },
                onDeselectAll = { browse.deselectAll() },
                onInvert = { browse.invertPicks(choosable) },
                allSelected = browse.picked.hasAll(choosable),
                onCancel = { browse.stopSelecting() },
                hint = if (!live) tr("Connect your Kobo to add or delete books") else null,
                actions = koboSelectionActions(
                    live, browse.picked.isNotEmpty(),
                    onAdd = { onAddToLibrary(chosen()); browse.stopSelecting() },
                    onDelete = { confirmDelete = chosen().filter { it.isSideloaded } },
                ),
            )
        }
    }
    confirmDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (doomed.size == 1) tr("Delete “{0}” from the Kobo?", doomed[0].title) else tr("Delete {0} books from the Kobo?", doomed.size)) },
            text = {
                Text(
                    tr("Their files, reading progress and highlights are deleted from the Kobo. Copies in your library aren't affected. Kobo Store books can't be deleted here.") +
                        if (doomed.isEmpty()) tr("\n\nNone of these can be deleted: they're Kobo Store books.") else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFromKobo(doomed)
                        confirmDelete = null
                        browse.stopSelecting()
                    },
                    enabled = doomed.isNotEmpty(),
                ) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Cancel")) } },
        )
    }
}

/** The Kobo's books as covers or a list: tapped for their details, or chosen while choosing. */
@Composable
private fun KoboBookList(
    view: KoboView,
    browse: BrowseState,
    books: List<KoboBook>,
    selectedId: String?,
    extras: KoboExtras,
    inLibrary: (KoboBook) -> Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        if (books.isEmpty()) {
            if (view.snapshot.books.isEmpty()) {
                EmptyState(Icons.Outlined.AutoStories, tr("No books on this Kobo"), tr("Books you add to the Kobo will appear here."))
            } else {
                EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search, or clear the filters."))
            }
            return@Column
        }
        val onTap: (KoboBook) -> Unit = { book ->
            if (browse.selecting) browse.togglePicked(book.contentId) else browse.selectedBookId = book.contentId
        }
        // Holding a book picks it, and every book from the one picked last to it.
        val onLongPress: (KoboBook) -> Unit = { book -> browse.holdPick(book.contentId, books.map { it.contentId }) }
        val perRow = chosenPerRow()
        val minCell = shelfMinCell()
        if (browse.grid) ShelfGridBox(minCell = minCell, coverPadding = 8.dp, columns = perRow, mode = browse.mode) {
            LazyVerticalGrid(
                columns = shelfCells(perRow, minCell),
                state = browse.gridState,
                // In the Bookcase look the covers stand on shelves that run edge to edge.
                contentPadding = if (LocalBookcase.current) PaddingValues(top = 8.dp, bottom = 24.dp)
                else PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (LocalBookcase.current) 0.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (LocalBookcase.current) Modifier.background((LocalKoLibStyle.current.shelf ?: ShelfColors).back) else Modifier),
            ) {
                // Over the books till it's put away: they're on the Kobo, and the library is for having them here.
                if (!browse.whereNoteSeen && !browse.selecting) item(key = "where", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    dev.kolib.app.ui.components.KoboWhereNote(onDismiss = { browse.whereNoteSeen = true }, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).widthIn(max = 720.dp))
                }
                itemsIndexed(books, key = { _, b -> b.contentId }) { index, book ->
                    BookGridItem(
                        book,
                        view.covers,
                        highlighted = book.contentId == selectedId && !browse.selecting,
                        picked = if (browse.selecting) browse.picked.containsKey(book.contentId) else null,
                        inLibrary = inLibrary(book),
                        onClick = { onTap(book) },
                        onLongClick = { onLongPress(book) },
                        shelfIndex = index,
                        shelfCount = books.size,
                        detail = koboSortDetail(book, browse.sort, extras),
                    )
                }
            }
        } else {
            LazyColumn(state = browse.listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                if (!browse.whereNoteSeen && !browse.selecting) item(key = "where") {
                    dev.kolib.app.ui.components.KoboWhereNote(onDismiss = { browse.whereNoteSeen = true }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).widthIn(max = 720.dp))
                }
                items(books, key = { it.contentId }) { book ->
                    BookListRow(
                        book,
                        view.covers,
                        view.snapshot.collectionsByBook[book.contentId]?.size ?: 0,
                        detail = koboSortDetail(book, browse.sort, extras),
                        highlighted = book.contentId == selectedId && !browse.selecting,
                        picked = if (browse.selecting) browse.picked.containsKey(book.contentId) else null,
                        inLibrary = inLibrary(book),
                        onClick = { onTap(book) },
                        onLongClick = { onLongPress(book) },
                    )
                }
            }
        }
    }
}

/**
 * The bar shown while choosing books, along the foot of the screen: how many are chosen, ways to choose them all or
 * none, and what can be done with them, as buttons with their labels under them, as on a phone held upright. Laid out
 * like that, they fit on one line on a phone held sideways too.
 */
@Composable
fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancel: () -> Unit,
    hint: String? = null,
    /** What's chosen, where it isn't books. */
    title: String? = null,
    /** Whether any are chosen, where [count] isn't the number. */
    anySelected: Boolean = count > 0,
    moreTag: String? = null,
    actions: List<BarAction>,
    onInvert: (() -> Unit)? = null,
    allSelected: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("selection-bar")) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, tr("Cancel selection")) }
            Column(Modifier.widthIn(max = 220.dp).padding(start = 4.dp, end = 4.dp)) {
                Text(
                    title ?: if (count == 0) tr("Tap books to select them") else if (count == 1) tr("1 book selected") else tr("{0} books selected", count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            SelectAllButtons(onSelectAll, onDeselectAll, anySelected = anySelected, onInvert = onInvert, allSelected = allSelected)
            Spacer(Modifier.width(8.dp))
            // The buttons share what's left, up to a width each, gathered at the end: all of them, where there's room.
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalActionMaxWidth provides 112.dp) { BarActions(actions, narrowest = 60.dp, moreTag = moreTag) }
            }
        }
    }
}

/** What can be done with books chosen on the Kobo: the same in the bar along the foot of a phone or a wider screen. */
internal fun koboSelectionActions(live: Boolean, any: Boolean, onAdd: () -> Unit, onDelete: () -> Unit) = listOf(
    BarAction(Icons.Filled.LibraryAdd, tr("Add to library"), onAdd, enabled = live && any, main = true, tag = "kobo-add-to-library"),
    BarAction(Icons.Outlined.Delete, tr("Delete from Kobo"), onDelete, enabled = live && any, destructive = true, tag = "kobo-delete"),
)

/** A line under each book that shows what it's sorted by, when that isn't obvious. */
internal fun koboSortDetail(book: KoboBook, sort: KoboSort, extras: KoboExtras): String? = when (sort) {
    KoboSort.PUBLISHED -> BookSorting.year(book)?.toString()
    KoboSort.ADDED -> book.addedMillis?.let { Fmt.date(it) }
    KoboSort.SIZE -> book.fileSize?.let { size -> if (size < 1_048_576) tr("{0} kB", (size + 1023) / 1024) else tr("{0} MB", "%.1f".format(size / 1_048_576.0)) }
    KoboSort.PAGES -> extras.pages(book)?.let { if (it == 1) tr("1 page") else tr("{0} pages", "%,d".format(it)) }
    KoboSort.RATING -> extras.stars(book).takeIf { it > 0 }?.let { stars -> "★".repeat(stars.toInt()) + if (stars % 1.0 >= 0.5) "½" else "" }
    KoboSort.LAST_READ -> book.lastReadMillis?.let { Fmt.date(it) }
    KoboSort.PROGRESS -> when (book.readStatus) {
        ReadStatus.FINISHED -> tr("Finished")
        ReadStatus.READING -> tr("{0}% read", book.percentRead)
        ReadStatus.UNREAD -> null
    }
    else -> null
}

@Composable
private fun BrowserControls(browse: BrowseState, shown: Int, snapshot: KoboSnapshot, extras: KoboExtras, readingTags: Pair<Int, Int>?, compact: Boolean = shortScreen()) {
    val total = snapshot.books.size
    var showHelp by remember { mutableStateOf(false) }
    val search: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(
                value = browse.query,
                onValueChange = { browse.query = it },
                placeholder = { Text(tr("Search titles, authors, tags…"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    Row {
                        if (browse.query.isNotEmpty()) IconButton(onClick = { browse.query = "" }) { Icon(Icons.Filled.Clear, tr("Clear search")) }
                        IconButton(onClick = { showHelp = true }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, tr("How to search")) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = m,
            )
    }
    val buttons: @Composable () -> Unit = {
            if (!browse.selecting) {
                TextButton(onClick = { browse.selecting = true }) {
                    Icon(Icons.Outlined.Checklist, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Select"))
                }
            }
            // Filter, Sort and Display, all from the one button over the shelf, as Mihon's.
            DisplayButton(browse.mode, { browse.mode = it }, Modifier.tourPlace("kobo-display"), extras = koboShelfExtras(snapshot, extras, browse))
    }
    val line: @Composable (Modifier) -> Unit = { m ->
        ShelfLine(
            shown, total, browse.narrowed, browse.filterWords(), tr("sorted by {0}", browse.sortDescription),
            onClear = { browse.clearFilters() }, modifier = m,
            extra = readingTags?.let { (done, all) -> tr("reading tags from book files: {0} of {1}", done, all) },
        )
    }
    if (compact) {
        // A short screen (a phone held sideways): the search and the buttons on one row, the line under them.
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                search(Modifier.weight(1f))
                buttons()
            }
            if (browse.narrowed) line(Modifier)
        }
    } else Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            search(Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            buttons()
        }
        line(Modifier.padding(top = 4.dp))
    }
    if (showHelp) SearchHelpDialog(onPick = { browse.query = it; showHelp = false }, onDismiss = { showHelp = false }, forKobo = true)
}

@Composable
fun BookGridItem(
    book: KoboBook,
    covers: KoboCoverLoader?,
    highlighted: Boolean,
    picked: Boolean? = null,
    inLibrary: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /** Where it sits in the grid, so the last book can carry the Bookcase shelf to the edge. */
    shelfIndex: Int = 0,
    shelfCount: Int = 0,
    /** What the list is sorted by, when that isn't obvious from the title and author. */
    detail: String? = null,
    /** Space around the cover, inside the cell. */
    padding: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Column(
        Modifier
            .shelfUnderCover(coverTop = padding, index = shelfIndex, count = shelfCount)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(padding)
    ) {
        BookCover(book, covers, CoverSize.THUMBNAIL, Modifier.gridCover(), selected = picked, inLibrary = inLibrary)
        ShelfSpace()
        UnderCover {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            book.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            (detail ?: book.seriesLabel)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BookListRow(
    book: KoboBook,
    covers: KoboCoverLoader?,
    collectionCount: Int,
    detail: String?,
    highlighted: Boolean,
    picked: Boolean?,
    inLibrary: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(book.author, book.seriesLabel).joinToString(" · ").ifEmpty { book.format },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (picked != null) Checkbox(checked = picked, onCheckedChange = { onClick() })
                BookCover(book, covers, CoverSize.THUMBNAIL, Modifier.width(44.dp), showProgress = false)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(statusText(book), style = MaterialTheme.typography.labelMedium)
                detail?.takeIf { it != statusText(book) && it != "Finished" }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (inLibrary) {
                    Text(tr("In library"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (collectionCount > 0) {
                    Text(
                        Fmt.plural(collectionCount, "collection"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                picked == true || highlighted -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            },
        ),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

private fun statusText(book: KoboBook): String = when (book.readStatus) {
    ReadStatus.UNREAD -> tr("Unread")
    ReadStatus.READING -> tr("{0}% read", book.percentRead)
    ReadStatus.FINISHED -> tr("Finished")
}

/** "en" -> "English", "fr-CA" -> "French (Canada)"; anything unrecognised is shown as stored. */
fun languageName(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code.replace('_', '-'))
    val here = java.util.Locale.getDefault()
    val name = locale.getDisplayName(here)
    // Shown on its own, with a capital: some languages write their names small ("anglais", "английский").
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name.replaceFirstChar { it.titlecase(here) }
}

/** Book descriptions are HTML; show their bold and italic, nothing riskier. */
fun descriptionText(html: String): AnnotatedString =
    runCatching { AnnotatedString.fromHtml(html.trim()) }.getOrElse { AnnotatedString(html) }

@Composable
fun BookDetailPane(
    book: KoboBook,
    collections: List<String>,
    covers: KoboCoverLoader?,
    inLibrary: Boolean,
    onBack: (() -> Unit)?,
    onOpenCollection: (String) -> Unit,
    onAddToLibrary: (() -> Unit)?,
    onDeleteFromKobo: (() -> Unit)? = null,
    /** What [onBack] goes back to. */
    backLabel: String = tr("All books"),
    tags: List<String> = emptyList(),
    onTag: (String) -> Unit = {},
    /** Stars from the Kobo, or the book's own rating; 0 when it has none. */
    stars: Double = 0.0,
    pages: Int? = null,
    /** Searches for books (a search for the tab: the book's title, author or series), from holding one of those. */
    onSearch: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDeleteFromKobo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(tr("Delete “{0}” from the Kobo?", book.title)) },
            text = {
                Text(
                    tr("The file, your reading progress and highlights are deleted from the Kobo.") +
                        if (inLibrary) tr("The copy in your library isn't affected.") else tr("It isn't in your library. Add it first to keep a copy.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteFromKobo()
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } },
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(backLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BookCover(book, covers, CoverSize.FULL, Modifier.width(if (dev.kolib.app.ui.components.shortScreen()) 120.dp else if (onBack != null) 200.dp else 160.dp), showProgress = false)
            Column(Modifier.weight(1f)) {
                // A tap on the title, author or series copies it; holding it, a search for it here or on the internet.
                DetailText(book.title, MaterialTheme.typography.headlineSmall, tr("Title copied"), inApp = titleSearch(book.title), onSearch = onSearch)
                book.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                book.author?.let { DetailText(it, MaterialTheme.typography.titleMedium, tr("Author copied"), inApp = authorSearch(listOf(it)), onSearch = onSearch) }
                book.seriesLabel?.let { label ->
                    val name = book.series.orEmpty()
                    DetailText(label, SeriesStyle(), tr("Series copied"), color = MaterialTheme.colorScheme.primary, copy = name, inApp = seriesSearch(name), onSearch = onSearch)
                }
                if (stars > 0) {
                    Row(Modifier.padding(top = 6.dp)) {
                        repeat(5) { i ->
                            Icon(
                                if (stars >= i + 1 || stars < i + 0.5) Icons.Filled.Star else Icons.AutoMirrored.Filled.StarHalf,
                                null,
                                tint = if (stars >= i + 0.5) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(onClick = {}, label = { Text(book.format) })
                    SuggestionChip(onClick = {}, label = { Text(if (book.kind == BookKind.SIDELOADED) tr("Sideloaded") else tr("Kobo Store")) })
                    SuggestionChip(onClick = {}, label = { Text(book.readStatus.label) })
                }
                if (book.readStatus == ReadStatus.READING) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { book.percentRead / 100f }, modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth())
                    Text(
                        tr("{0}% read", book.percentRead),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                val readHere = dev.kolib.app.ui.components.LocalReadHere.current
                when {
                    // In the library: read here opens the library's copy, Kobo plugged in or not.
                    inLibrary -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (readHere != null) dev.kolib.app.ui.components.GoldButton(onClick = { readHere(book) }, modifier = Modifier.testTag("kobo-book-read")) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Read here"))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("In library"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // Not plugged in, the book can't be copied from the Kobo: the buttons are there, greyed out, saying why.
                    book.isSideloaded -> Column {
                        // A book on the Kobo isn't on this tablet: reading it here adds it to the library first.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dev.kolib.app.ui.components.GoldButton(onClick = { readHere?.invoke(book) }, enabled = onAddToLibrary != null && readHere != null, modifier = Modifier.testTag("kobo-book-read")) {
                                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Read here"))
                            }
                            FilledTonalButton(onClick = { onAddToLibrary?.invoke() }, enabled = onAddToLibrary != null, modifier = Modifier.testTag("kobo-book-add")) {
                                Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Add to library"))
                            }
                        }
                        if (onAddToLibrary == null) Text(
                            tr("Connect your Kobo to read this book here or add it to your library."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                if (onDeleteFromKobo != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Delete from Kobo"))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(tr("Collections on the Kobo"), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        if (collections.isEmpty()) {
            Text(tr("Not in any collection."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                collections.forEach { name ->
                    AssistChip(
                        onClick = { onOpenCollection(name) },
                        label = { Text(name) },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(tr("Tags"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    SuggestionChip(onClick = { onTag(t) }, label = { Text(t) }, icon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(16.dp)) })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionCard(tr("Details")) {
            InfoRow(tr("Last read"), Fmt.dateTime(book.lastReadMillis))
            InfoRow(tr("Added"), Fmt.date(book.addedMillis))
            InfoRow(tr("Published"), BookSorting.year(book)?.toString())
            InfoRow(tr("Publisher"), book.publisher)
            InfoRow(tr("Language"), book.language?.let { languageName(it) })
            InfoRow(tr("Length"), pages?.let { p -> tr("About {0}", Fmt.plural(p, "page")) })
            InfoRow("ISBN", book.isbn, selectable = true)
            InfoRow(tr("File"), book.relativePath, selectable = true)
            InfoRow(tr("Size"), Fmt.bytes(context, book.fileSize))
        }

        book.description?.let { html ->
            val text = remember(html) { descriptionText(html) }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                SectionCard(tr("Description")) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            tr("To edit the details or cover, add the book to your library."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
    }
}
