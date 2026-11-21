@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kolib.app.ui

import dev.kolib.app.ui.components.hasAll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.ui.platform.testTag
import dev.kolib.app.ui.tour.tourPlace
import dev.kolib.app.ui.components.DisplayButton
import dev.kolib.app.ui.components.shelfCells
import dev.kolib.app.ui.components.chosenPerRow
import dev.kolib.app.tr
import dev.kolib.app.ui.components.DetailText
import dev.kolib.app.ui.components.titleSearch
import dev.kolib.app.ui.components.authorSearch
import dev.kolib.app.ui.components.seriesSearch
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import dev.kolib.app.ui.components.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.kobo.BookKind
import dev.kolib.app.kobo.CoverSize
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboCoverLoader
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.kobo.ReadStatus
import dev.kolib.app.ui.components.SeriesStyle
import dev.kolib.app.ui.components.AlertDialog
import dev.kolib.app.ui.components.BookCover
import dev.kolib.app.ui.components.EmptyState
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.HideBottomBar
import dev.kolib.app.ui.components.InfoRow
import dev.kolib.app.ui.components.PhoneAction
import dev.kolib.app.ui.components.PhoneActionBar
import dev.kolib.app.ui.components.PhoneSearchField
import dev.kolib.app.ui.components.PhoneSelectionHeader
import dev.kolib.app.ui.components.SectionCard
import dev.kolib.app.ui.components.ShelfGridBox
import dev.kolib.app.ui.library.SearchHelpDialog
import dev.kolib.app.ui.theme.LocalBookcase
import dev.kolib.app.ui.theme.ShelfColors
import dev.kolib.app.ui.components.TabBackHandler

/**
 * The Kobo's books on a phone: a search box, the filters in one line that scrolls sideways, and three
 * covers to a row. While choosing books, the bar along the bottom holds what can be done with them.
 */
@Composable
internal fun PhoneKoboBrowser(
    view: KoboView,
    live: Boolean,
    browse: BrowseState,
    books: List<KoboBook>,
    extras: KoboExtras,
    onAddToLibrary: (List<KoboBook>) -> Unit,
    onDeleteFromKobo: (List<KoboBook>) -> Unit,
    readingTags: Pair<Int, Int>?,
) {
    val snapshot = view.snapshot
    val inLibrary: (KoboBook) -> Boolean = { extras.inLibrary(it) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<KoboBook>?>(null) }
    if (browse.selecting) {
        TabBackHandler { browse.stopSelecting() }
        HideBottomBar()
    }
    val chosen = { books.filter { browse.picked.containsKey(it.contentId) } }
    val onTap: (KoboBook) -> Unit = { book -> if (browse.selecting) browse.togglePicked(book.contentId) else browse.selectedBookId = book.contentId }
    // Holding a book picks it, and every book from the one picked last to it.
    val onHold: (KoboBook) -> Unit = { book -> browse.holdPick(book.contentId, books.map { it.contentId }) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (browse.selecting) {
                // The Kobo's own bar and tabs are hidden meanwhile, so this goes at the top of the screen.
                val choosable = books.filter { it.isSideloaded && !inLibrary(it) }.map { it.contentId }
                PhoneSelectionHeader(
                    count = browse.picked.size,
                    onSelectAll = { choosable.forEach { browse.picked[it] = true } },
                    onDeselectAll = { browse.deselectAll() },
                    onInvert = { browse.invertPicks(choosable) },
                    allSelected = browse.picked.hasAll(choosable),
                    onCancel = { browse.stopSelecting() },
                    hint = if (!live) tr("Connect your Kobo to add or delete books") else null,
                )
            } else {
                // Choosing several books is in the menu at the top, with the Kobo's own items; how the books show, beside the search.
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhoneSearchField(
                        query = browse.query,
                        onQuery = { browse.query = it },
                        placeholder = tr("Search {0}", Fmt.plural(snapshot.books.size, "book")),
                        onHelp = { showHelp = true },
                        modifier = Modifier.weight(1f),
                    )
                    // Filter, Sort and Display, all from the one button, as Mihon's.
                    DisplayButton(browse.mode, { browse.mode = it }, Modifier.tourPlace("kobo-display"), extras = koboShelfExtras(snapshot, extras, browse))
                }
            }
            // Where the filters' chips were: what's showing and in what order (they're chosen with the button at the top).
            ShelfLine(
                books.size, snapshot.books.size, browse.narrowed, browse.filterWords(), tr("sorted by {0}", browse.sortDescription),
                onClear = { browse.clearFilters() }, modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            )
            readingTags?.let { (done, all) ->
                Text(
                    tr("Reading tags from the book files: {0} of {1}", done, all),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (books.isEmpty()) {
                if (snapshot.books.isEmpty()) EmptyState(Icons.Outlined.AutoStories, tr("No books on this Kobo"), tr("Books you add to the Kobo will appear here."))
                else EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search, or clear the filters."))
                return@Column
            }
            val underLast = if (browse.selecting) 104.dp else 24.dp
            val perRow = chosenPerRow() ?: 3
            if (browse.grid) ShelfGridBox(minCell = 100.dp, coverPadding = 6.dp, columns = perRow, mode = browse.mode) {
                val bookcase = LocalBookcase.current
                LazyVerticalGrid(
                    columns = GridCells.Fixed(perRow),
                    state = browse.gridState,
                    contentPadding = if (bookcase) PaddingValues(top = 4.dp, bottom = underLast)
                    else PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = underLast),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (bookcase) Modifier.background((dev.kolib.app.ui.theme.LocalKoLibStyle.current.shelf ?: ShelfColors).back) else Modifier),
                ) {
                    // Over the books till it's put away: they're on the Kobo, and the library is for having them here.
                    if (!browse.whereNoteSeen && !browse.selecting) item(key = "where", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        dev.kolib.app.ui.components.KoboWhereNote(onDismiss = { browse.whereNoteSeen = true }, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
                    }
                    itemsIndexed(books, key = { _, b -> b.contentId }) { index, book ->
                        BookGridItem(
                            book,
                            view.covers,
                            highlighted = false,
                            picked = if (browse.selecting) browse.picked.containsKey(book.contentId) else null,
                            inLibrary = inLibrary(book),
                            onClick = { onTap(book) },
                            onLongClick = { onHold(book) },
                            shelfIndex = index,
                            shelfCount = books.size,
                            detail = koboSortDetail(book, browse.sort, extras),
                            padding = 6.dp,
                        )
                    }
                }
            } else {
                LazyColumn(state = browse.listState, contentPadding = PaddingValues(bottom = underLast), modifier = Modifier.fillMaxSize()) {
                    if (!browse.whereNoteSeen && !browse.selecting) item(key = "where") {
                        dev.kolib.app.ui.components.KoboWhereNote(onDismiss = { browse.whereNoteSeen = true }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                    }
                    items(books, key = { it.contentId }) { book ->
                        BookListRow(
                            book,
                            view.covers,
                            snapshot.collectionsByBook[book.contentId]?.size ?: 0,
                            detail = koboSortDetail(book, browse.sort, extras),
                            highlighted = false,
                            picked = if (browse.selecting) browse.picked.containsKey(book.contentId) else null,
                            inLibrary = inLibrary(book),
                            onClick = { onTap(book) },
                            onLongClick = { onHold(book) },
                        )
                    }
                }
            }
        }
        if (browse.selecting) {
            val any = browse.picked.isNotEmpty()
            PhoneActionBar(
                koboSelectionActions(
                    live, any,
                    onAdd = { onAddToLibrary(chosen()); browse.stopSelecting() },
                    onDelete = { confirmDelete = chosen().filter { it.isSideloaded } },
                ),
                Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    if (showHelp) SearchHelpDialog(onPick = { browse.query = it; showHelp = false }, onDismiss = { showHelp = false }, forKobo = true)
    confirmDelete?.let { doomed ->
        DeleteFromKoboDialog(doomed, onDelete = {
            onDeleteFromKobo(doomed)
            confirmDelete = null
            browse.stopSelecting()
        }, onDismiss = { confirmDelete = null })
    }
}

/** Asks before books' files are deleted from the Kobo. */
@Composable
internal fun DeleteFromKoboDialog(doomed: List<KoboBook>, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (doomed.size == 1) tr("Delete “{0}” from the Kobo?", doomed[0].title) else tr("Delete {0} books from the Kobo?", doomed.size)) },
        text = {
            Text(
                tr("Their files, reading progress and highlights are deleted from the Kobo. Copies in your library aren't affected. Kobo Store books can't be deleted here.") +
                    if (doomed.isEmpty()) tr("\n\nNone of these can be deleted: they're Kobo Store books.") else ""
            )
        },
        confirmButton = { TextButton(onClick = onDelete, enabled = doomed.isNotEmpty()) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/**
 * A book on the Kobo, on a phone: its cover large and centred, the title and how far it's been read,
 * then adding it to the library, its collections, tags and details.
 */
@Composable
internal fun PhoneKoboBookPage(
    book: KoboBook,
    collections: List<String>,
    covers: KoboCoverLoader?,
    inLibrary: Boolean,
    live: Boolean,
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onAddToLibrary: (() -> Unit)?,
    onDeleteFromKobo: (() -> Unit)?,
    tags: List<String>,
    onTag: (String) -> Unit,
    stars: Double,
    pages: Int?,
    /** Searches for books (a search for the tab: the book's title, author or series), from holding one of those. */
    onSearch: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val titleGone = with(LocalDensity.current) { scroll.value > 330.dp.toPx() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { if (titleGone) Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back to the Kobo's books")) } },
            actions = {
                if (onDeleteFromKobo != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("More options")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Delete from Kobo"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BookCover(book, covers, CoverSize.FULL, Modifier.width(184.dp), showProgress = false)
            }
            Spacer(Modifier.height(20.dp))
            // A tap on the title, author or series copies it; holding it, a search for it here or on the internet.
            DetailText(book.title, MaterialTheme.typography.headlineSmall, tr("Title copied"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                inApp = titleSearch(book.title), onSearch = onSearch)
            book.subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            book.author?.let {
                Spacer(Modifier.height(4.dp))
                DetailText(it, MaterialTheme.typography.titleMedium, tr("Author copied"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    inApp = authorSearch(listOf(it)), onSearch = onSearch)
            }
            book.seriesLabel?.let { label ->
                val name = book.series.orEmpty()
                DetailText(label, SeriesStyle(), tr("Series copied"), Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                    copy = name, inApp = seriesSearch(name), onSearch = onSearch)
            }
            if (stars > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
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
            Text(
                listOf(book.format, if (book.kind == BookKind.SIDELOADED) tr("Sideloaded") else tr("Kobo Store"), book.readStatus.label).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (book.readStatus == ReadStatus.READING) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { book.percentRead / 100f }, modifier = Modifier.fillMaxWidth())
                Text(
                    tr("{0}% read", book.percentRead),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            val readHere = dev.kolib.app.ui.components.LocalReadHere.current
            when {
                // In the library: read here opens the library's copy, Kobo plugged in or not.
                inLibrary -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    if (readHere != null) dev.kolib.app.ui.components.GoldButton(
                        onClick = { readHere(book) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("kobo-book-read"),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Read here"))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("In library"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                // Not plugged in, the book can't be copied from the Kobo: the buttons are there, greyed out, saying why.
                book.isSideloaded && (onAddToLibrary != null || !live) -> Column(Modifier.fillMaxWidth()) {
                    // A book on the Kobo isn't on this phone: reading it here adds it to the library first.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        dev.kolib.app.ui.components.GoldButton(
                            onClick = { readHere?.invoke(book) },
                            enabled = onAddToLibrary != null && readHere != null,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("kobo-book-read"),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Read here"))
                        }
                        FilledTonalButton(
                            onClick = { onAddToLibrary?.invoke() },
                            enabled = onAddToLibrary != null,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("kobo-book-add"),
                        ) {
                            Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Add to library"))
                        }
                    }
                    if (onAddToLibrary == null) Text(
                        tr("Connect your Kobo to read this book here or add it to your library."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
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
                    SectionCard(tr("Description")) { Text(text, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                tr("To edit the details or cover, add the book to your library."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
            )
        }
    }
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
}
