@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.DetailText
import dev.kobolibrary.app.ui.components.titleSearch
import dev.kobolibrary.app.ui.components.authorSearch
import dev.kobolibrary.app.ui.components.seriesSearch
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
import androidx.compose.material3.Button
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
import dev.kobolibrary.app.kobo.BookKind
import dev.kobolibrary.app.kobo.CoverSize
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboCoverLoader
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.ui.components.SeriesStyle
import dev.kobolibrary.app.ui.components.AlertDialog
import dev.kobolibrary.app.ui.components.BookCover
import dev.kobolibrary.app.ui.components.EmptyState
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.components.HideBottomBar
import dev.kobolibrary.app.ui.components.InfoRow
import dev.kobolibrary.app.ui.components.PhoneAction
import dev.kobolibrary.app.ui.components.PhoneActionBar
import dev.kobolibrary.app.ui.components.PhoneSearchField
import dev.kobolibrary.app.ui.components.PhoneSelectionHeader
import dev.kobolibrary.app.ui.components.SectionCard
import dev.kobolibrary.app.ui.components.ShelfGridBox
import dev.kobolibrary.app.ui.library.SearchHelpDialog
import dev.kobolibrary.app.ui.theme.LocalBookcase
import dev.kobolibrary.app.ui.theme.ShelfColors
import dev.kobolibrary.app.ui.components.TabBackHandler

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
                PhoneSelectionHeader(
                    count = browse.picked.size,
                    onSelectAll = { books.filter { it.isSideloaded && !inLibrary(it) }.forEach { browse.picked[it.contentId] = true } },
                    onCancel = { browse.stopSelecting() },
                    hint = if (!live) tr("Connect your Kobo to add or delete books") else null,
                )
            } else {
                // Choosing several books and grid or list are in the menu at the top, with the Kobo's own items.
                PhoneSearchField(
                    query = browse.query,
                    onQuery = { browse.query = it },
                    placeholder = tr("Search {0}", Fmt.plural(snapshot.books.size, "book")),
                    onHelp = { showHelp = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                )
            }
            PhoneKoboFilters(browse, view, extras)
            if (browse.narrowed) PhoneNarrowedLine(books.size, snapshot.books.size) { browse.clearFilters() }
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
                else EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search or clear the filters."))
                return@Column
            }
            val underLast = if (browse.selecting) 104.dp else 24.dp
            if (browse.grid) ShelfGridBox(minCell = 100.dp, coverPadding = 6.dp, columns = 3) {
                val bookcase = LocalBookcase.current
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = browse.gridState,
                    contentPadding = if (bookcase) PaddingValues(top = 4.dp, bottom = underLast)
                    else PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = underLast),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (bookcase) Modifier.background(ShelfColors.back) else Modifier),
                ) {
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
            PhoneActionBar(Modifier.align(Alignment.BottomCenter)) {
                PhoneAction(Icons.Filled.LibraryAdd, tr("Add to library"), {
                    onAddToLibrary(chosen())
                    browse.stopSelecting()
                }, enabled = live && any, main = true)
                PhoneAction(Icons.Outlined.Delete, tr("Delete from Kobo"), { confirmDelete = chosen().filter { it.isSideloaded } }, enabled = live && any, destructive = true)
            }
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

/** The Kobo's sort order and filters, in one line that scrolls sideways. */
@Composable
private fun PhoneKoboFilters(browse: BrowseState, view: KoboView, extras: KoboExtras) {
    val snapshot = view.snapshot
    val collections = remember(snapshot) {
        snapshot.collections.filter { c -> c.memberIds.any { id -> snapshot.booksById[id]?.isOnDevice == true } }
            .map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val tagCounts = remember(snapshot, extras) {
        snapshot.books.flatMap { b -> extras.tags(b).distinctBy { it.lowercase() } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).map { it.key to it.value }
    }
    PhoneChipRow {
        PhoneSortChip(
            orders = KoboSort.entries,
            current = browse.sort,
            descending = browse.descending,
            label = { it.label },
            direction = { order, down -> if (down) order.descending else order.ascending },
            onPick = { browse.sortBy(it) },
        )
        PhoneCollectionChip(collections, browse.collection) { browse.collection = it }
        PhoneTagsChip(tagCounts, browse.tags, browse.tagsMatchAll) { chosen, all ->
            browse.tags = chosen
            browse.tagsMatchAll = all
        }
        InLibraryFilter.entries.forEach { f ->
            PhoneToggleChip(f.label, browse.inLibrary == f) { browse.inLibrary = if (browse.inLibrary == f) null else f }
        }
        StatusFilter.entries.forEach { f ->
            PhoneToggleChip(f.label, browse.status == f) { browse.status = if (browse.status == f) null else f }
        }
        SourceFilter.entries.filter { it != SourceFilter.ALL }.forEach { f ->
            PhoneToggleChip(f.label, browse.source == f) { browse.source = if (browse.source == f) SourceFilter.ALL else f }
        }
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
            when {
                inLibrary -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("In your library"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                book.isSideloaded && onAddToLibrary != null -> Button(onClick = onAddToLibrary, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Add to library"))
                }
                book.isSideloaded && !live -> Text(
                    tr("Connect your Kobo to add this book to your library."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
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
