@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui.library

import androidx.compose.ui.text.rememberTextMeasurer
import dev.kobolibrary.app.tr
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.JobProgress
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.reader.ReadHere
import dev.kobolibrary.app.ui.MenuEntry
import dev.kobolibrary.app.ui.PhoneChipRow
import dev.kobolibrary.app.ui.PhoneCollectionChip
import dev.kobolibrary.app.ui.PhoneListMenu
import dev.kobolibrary.app.ui.PhoneNarrowedLine
import dev.kobolibrary.app.ui.PhoneSortChip
import dev.kobolibrary.app.ui.PhoneTagsChip
import dev.kobolibrary.app.ui.PhoneToggleChip
import dev.kobolibrary.app.ui.components.KoLibMark
import dev.kobolibrary.app.ui.components.GoldButton
import dev.kobolibrary.app.ui.components.GoldFab
import dev.kobolibrary.app.ui.components.SeriesStyle
import dev.kobolibrary.app.ui.components.ActionTile
import dev.kobolibrary.app.ui.components.EmptyState
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.components.HideBottomBar
import dev.kobolibrary.app.ui.components.InfoRow
import dev.kobolibrary.app.ui.components.LibraryCover
import dev.kobolibrary.app.ui.components.PhoneAction
import dev.kobolibrary.app.ui.components.PhoneActionBar
import dev.kobolibrary.app.ui.components.PhoneSearchField
import dev.kobolibrary.app.ui.components.PhoneSelectionHeader
import dev.kobolibrary.app.ui.components.SectionCard
import dev.kobolibrary.app.ui.components.ShelfGridBox
import dev.kobolibrary.app.ui.components.TopInsets
import dev.kobolibrary.app.ui.descriptionText
import dev.kobolibrary.app.ui.languageName
import dev.kobolibrary.app.ui.theme.LocalBookcase
import dev.kobolibrary.app.ui.theme.ShelfColors
import kotlin.math.roundToInt

/**
 * The library on a phone: a search box at the top, the filters in one line that scrolls sideways,
 * three covers to a row, and a button in the corner for adding books. A book opens on a page of its
 * own; pressing and holding one starts choosing several, with their actions in a bar along the bottom.
 */
@Composable
internal fun PhoneLibraryScreen(
    books: List<LibraryBook>,
    visible: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    selected: LibraryBook?,
    job: JobProgress?,
    koboConnected: Boolean,
    reading: (LibraryBook) -> KoboBook?,
    here: (LibraryBook) -> ReadHere?,
    readingHere: Map<String, ReadHere>,
    koboLive: Boolean,
    onAddFiles: () -> Unit,
    onAddFromKobo: () -> Unit,
    onAddFolder: () -> Unit = {},
    onShowJob: () -> Unit,
    onEditDetails: (String) -> Unit,
    onChangeCover: (String) -> Unit,
    onDelete: (List<String>) -> Unit,
    onSend: (List<String>) -> Unit,
    onRead: (String) -> Unit,
    onEditText: (String) -> Unit,
    onNewBook: () -> Unit,
    onMerge: (List<String>) -> Unit,
    onSplit: (String) -> Unit,
    onExport: (List<String>) -> Unit,
    onDuplicate: (List<String>) -> Unit,
    onAbout: () -> Unit,
    onTheme: (() -> Unit)?,
    onMarkFinished: (List<String>) -> Unit = {},
    onMarkUnread: (List<String>) -> Unit = {},
) {
    if (selected != null && !browse.selecting) {
        BackHandler { browse.selectedId = null }
        PhoneBookPage(
            book = selected,
            covers = covers,
            koboConnected = koboConnected,
            onBack = { browse.selectedId = null },
            onRead = { onRead(selected.id) },
            onSend = { onSend(listOf(selected.id)) },
            onEdit = { onEditDetails(selected.id) },
            onCover = { onChangeCover(selected.id) },
            onEditText = { onEditText(selected.id) },
            onSplit = { onSplit(selected.id) },
            onExport = { onExport(listOf(selected.id)) },
            onDuplicate = { onDuplicate(listOf(selected.id)) },
            onDelete = {
                browse.selectedId = null
                onDelete(listOf(selected.id))
            },
            onCollection = { name ->
                browse.collection = name
                browse.selectedId = null
            },
            onTag = { tag ->
                browse.tags = setOf(tag)
                browse.selectedId = null
            },
            readHere = readingHere[selected.id]?.takeIf { !it.finished }?.progress,
            reading = reading(selected),
            readingLive = koboLive,
            finished = readingHere[selected.id]?.finished == true,
            onMarkFinished = { onMarkFinished(listOf(selected.id)) },
            onMarkUnread = { onMarkUnread(listOf(selected.id)) },
        )
        return
    }

    var addSheet by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<List<String>?>(null) }
    if (browse.selecting) {
        BackHandler { browse.stopSelecting() }
        HideBottomBar()
    }
    val chosen = { visible.filter { browse.picked.containsKey(it.id) }.map { it.id } }
    val onTap: (LibraryBook) -> Unit = { b -> if (browse.selecting) browse.togglePicked(b.id) else browse.selectedId = b.id }
    val onHold: (LibraryBook) -> Unit = { b ->
        if (!browse.selecting) browse.selecting = true
        browse.togglePicked(b.id)
    }
    // The Add books button shrinks to its icon once the list is scrolled, to show more of the covers.
    val atTop by remember {
        derivedStateOf { if (browse.grid) browse.gridState.firstVisibleItemIndex == 0 else browse.listState.firstVisibleItemIndex == 0 }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (browse.selecting) {
                PhoneSelectionHeader(
                    count = browse.picked.size,
                    onSelectAll = { visible.forEach { browse.picked[it.id] = true } },
                    onCancel = { browse.stopSelecting() },
                )
            } else if (books.isEmpty()) {
                // Nothing to search yet: just the name, and the menu for the theme and the app's details.
                Row(
                    Modifier
                        .windowInsetsPadding(TopInsets)
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 20.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KoLibMark(Modifier.size(34.dp), glow = true)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Library"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("More options")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            onTheme?.let { theme ->
                                DropdownMenuItem(text = { Text(tr("App theme")) }, leadingIcon = { Icon(Icons.Outlined.Palette, null) }, onClick = { menu = false; theme() })
                            }
                            DropdownMenuItem(text = { Text(tr("About KoLib")) }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; onAbout() })
                        }
                    }
                }
            } else {
                Row(
                    Modifier
                        .windowInsetsPadding(TopInsets)
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KoLibMark(Modifier.size(34.dp).semantics { contentDescription = tr("KoLib") }, glow = true)
                    Spacer(Modifier.width(10.dp))
                    PhoneSearchField(
                        query = browse.query,
                        onQuery = { browse.query = it },
                        placeholder = if (books.isEmpty()) tr("Search the library") else tr("Search {0}", Fmt.plural(books.size, "book")),
                        onHelp = { showHelp = true },
                        modifier = Modifier.weight(1f),
                    )
                    PhoneListMenu(
                        grid = browse.grid,
                        onSelect = { browse.selecting = true },
                        onGrid = { browse.grid = it },
                        more = listOfNotNull(
                            onTheme?.let { MenuEntry(tr("App theme"), Icons.Outlined.Palette, it) },
                            MenuEntry(tr("About KoLib"), Icons.Outlined.Info, onAbout),
                        ),
                    )
                }
            }
            if (job != null && !job.finished) JobStrip(job, onShowJob)
            if (books.isEmpty()) {
                EmptyLibrary(koboConnected, onAddFiles, onAddFromKobo, onNewBook)
                return@Column
            }
            PhoneLibraryFilters(books, browse)
            if (browse.narrowed) PhoneNarrowedLine(visible.size, books.size) { browse.clearFilters() }
            if (visible.isEmpty()) {
                EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search, or clear the filters."))
                return@Column
            }
            // Room under the last row, so the Add books button (or the bar for chosen books) doesn't cover it.
            val underLast = 104.dp
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
                    itemsIndexed(visible, key = { _, b -> b.id }) { index, book ->
                        LibraryGridItem(
                            book, covers, index, visible.size,
                            picked = if (browse.selecting) browse.picked.containsKey(book.id) else null,
                            highlighted = false,
                            sort = browse.sort, reading = reading(book), here = here(book),
                            onTap = { onTap(book) }, onHold = { onHold(book) },
                            padding = 6.dp,
                        )
                    }
                }
            } else {
                LazyColumn(state = browse.listState, contentPadding = PaddingValues(bottom = underLast), modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { book ->
                        LibraryListItem(
                            book, covers,
                            picked = if (browse.selecting) browse.picked.containsKey(book.id) else null,
                            highlighted = false,
                            sort = browse.sort, reading = reading(book), here = here(book),
                            onTap = { onTap(book) }, onHold = { onHold(book) }, onToggle = { browse.togglePicked(book.id) },
                        )
                    }
                }
            }
        }

        if (!browse.selecting && books.isNotEmpty()) {
            GoldFab(
                text = tr("Add books"),
                icon = Icons.Filled.Add,
                expanded = atTop,
                onClick = { addSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
        if (browse.selecting) {
            val any = browse.picked.isNotEmpty()
            PhoneActionBar(Modifier.align(Alignment.BottomCenter)) {
                PhoneAction(Icons.AutoMirrored.Filled.Send, tr("Send"), { onSend(chosen()) }, enabled = any, main = true)
                PhoneAction(Icons.Outlined.SaveAlt, tr("Save"), { onExport(chosen()) }, enabled = any)
                PhoneAction(Icons.Outlined.ContentCopy, tr("Duplicate"), { onDuplicate(chosen()) }, enabled = any)
                PhoneAction(Icons.Outlined.Merge, tr("Combine"), { onMerge(chosen()) }, enabled = browse.picked.size >= 2)
                PhoneAction(Icons.Outlined.Delete, tr("Remove"), { confirmRemove = chosen() }, enabled = any, destructive = true)
            }
        }
    }

    if (addSheet) {
        AddBooksSheet(
            koboConnected = koboConnected,
            onDismiss = { addSheet = false },
            onAddFiles = onAddFiles,
            onAddFromKobo = onAddFromKobo,
            onAddFolder = onAddFolder,
            onNewBook = onNewBook,
        )
    }
    if (showHelp) SearchHelpDialog(onPick = { browse.query = it; showHelp = false }, onDismiss = { showHelp = false })
    confirmRemove?.let { ids ->
        RemoveBooksDialog(ids, onRemove = {
            onDelete(ids)
            confirmRemove = null
            browse.stopSelecting()
        }, onDismiss = { confirmRemove = null })
    }
}

/** The sort order and filters, in one line that scrolls sideways. */
@Composable
private fun PhoneLibraryFilters(all: List<LibraryBook>, browse: LibraryBrowseState) {
    val collections = remember(all) { all.flatMap { it.collections }.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val tagCounts = remember(all) {
        all.flatMap { b -> b.meta.tags.distinctBy { it.lowercase() } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).map { it.key to it.value }
    }
    PhoneChipRow {
        PhoneSortChip(
            orders = LibrarySort.entries,
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
        LibraryFilter.entries.filter { it != LibraryFilter.ALL }.forEach { f ->
            PhoneToggleChip(f.label, browse.filter == f) { browse.filter = if (browse.filter == f) LibraryFilter.ALL else f }
        }
    }
}

/** Ways to add books, in a sheet from the bottom of the screen. */
@Composable
private fun AddBooksSheet(
    koboConnected: Boolean,
    onDismiss: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFromKobo: () -> Unit,
    onAddFolder: () -> Unit,
    onNewBook: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text(tr("Add books"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        SheetItem(Icons.Outlined.FileOpen, tr("Files from this device"), tr("EPUB, PDF, MOBI, Word and more")) { onDismiss(); onAddFiles() }
        SheetItem(Icons.Outlined.FolderOpen, tr("A folder of books"), tr("Every book in it, and in the folders inside it")) { onDismiss(); onAddFolder() }
        SheetItem(Icons.Outlined.Usb, tr("Books from the Kobo"), if (koboConnected) null else tr("Connect your Kobo first")) { onDismiss(); onAddFromKobo() }
        SheetItem(Icons.AutoMirrored.Outlined.NoteAdd, tr("Write a new book")) { onDismiss(); onNewBook() }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, note: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = note?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

/**
 * A book on a phone: its cover large and centred, the title under it, then Read and Send, a grid of
 * the other things that can be done with it, and its collections, tags and details.
 */
@Composable
internal fun PhoneBookPage(
    book: LibraryBook,
    covers: LibraryCovers,
    koboConnected: Boolean,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    onCover: () -> Unit,
    onEditText: () -> Unit,
    onSplit: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCollection: (String) -> Unit,
    onTag: (String) -> Unit,
    readHere: Float?,
    reading: KoboBook?,
    readingLive: Boolean,
    /** Marked as finished in the app's reader (until it's read again). */
    finished: Boolean = false,
    onMarkFinished: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val meta = book.meta
    val scroll = rememberScrollState()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Once the title under the cover has scrolled away, it shows in the bar at the top instead.
    val titleGone = with(LocalDensity.current) { scroll.value > 330.dp.toPx() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { if (titleGone) Text(meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back to the library")) } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("More options")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Remove from library"), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
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
                LibraryCover(
                    book, covers, large = true,
                    modifier = Modifier.width(184.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(meta.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            book.authorLine?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            meta.seriesLabel?.let {
                Text(it, style = SeriesStyle(), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            meta.rating?.takeIf { it > 0 }?.let { r ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(5) { i ->
                        Icon(
                            Icons.Filled.Star, null,
                            tint = if (i < (r + 1) / 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoldButton(
                    onClick = onRead,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            finished -> tr("Read again")
                            readHere != null && readHere > 0.005f -> tr("Read · {0}%", (readHere * 100).roundToInt())
                            else -> tr("Read")
                        },
                        maxLines = 1,
                    )
                }
                FilledTonalButton(
                    onClick = onSend,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            book.changedSinceOnKobo -> tr("Update on Kobo")
                            book.koboPath != null -> tr("Send again")
                            else -> tr("Send to Kobo")
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
            ReadStateButtons(finished, started = readHere != null && readHere > 0.005f, onMarkFinished, onMarkUnread, Modifier.padding(top = 10.dp))
            reading?.let { k -> ReadingLine(k, live = readingLive) }
            if (book.changedSinceOnKobo) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tr("Changed since you sent it. Tap Update on Kobo to send the new version."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (!koboConnected && book.koboPath == null) {
                Text(
                    tr("Connect your Kobo to send this book."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            val tiles = listOf(
                Triple(Icons.Outlined.Edit, tr("Edit details"), onEdit),
                Triple(Icons.Outlined.Image, tr("Change cover"), onCover),
                Triple(Icons.Outlined.EditNote, tr("Edit text"), onEditText),
                Triple(Icons.AutoMirrored.Outlined.CallSplit, tr("Split into books"), onSplit),
                Triple(Icons.Outlined.SaveAlt, tr("Save as file"), onExport),
                Triple(Icons.Outlined.ContentCopy, tr("Duplicate"), onDuplicate),
            )
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, label, action) -> ActionTile(icon, label, action, Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(tr("Kobo collections"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (book.collections.isEmpty()) {
                Text(tr("Not in any collection. To add it to one, tap Edit details."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.collections.forEach { name ->
                        AssistChip(
                            onClick = { onCollection(name) },
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (meta.tags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(tr("Tags"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meta.tags.forEach { t ->
                        SuggestionChip(onClick = { onTag(t) }, label = { Text(t) }, icon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(16.dp)) })
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionCard(tr("Details")) {
                InfoRow(tr("Publisher"), meta.publisher)
                InfoRow(tr("Published"), Fmt.publication(meta.published))
                InfoRow(tr("Language"), meta.language?.let { languageName(it) })
                InfoRow("ISBN", meta.isbn, selectable = true)
                InfoRow(tr("Length"), book.pages?.let { p -> tr("About {0} ({1} words)", Fmt.plural(p, "page"), "%,d".format(book.words)) })
                InfoRow(tr("File on the Kobo"), book.koboPath ?: tr("Not sent yet"))
                InfoRow(tr("Last sent"), Fmt.dateTime(book.sentAt))
                InfoRow(tr("Added"), Fmt.date(book.addedAt))
                InfoRow(tr("Last edited"), Fmt.dateTime(book.modifiedAt).takeIf { book.modifiedAt > book.addedAt + 1000 })
                InfoRow(tr("Added as"), book.addedAs)
                InfoRow(tr("File size"), Fmt.bytes(context, book.fileSize))
            }
            meta.description?.let { html ->
                val text = remember(html) { descriptionText(html) }
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    SectionCard(tr("Description")) { Text(text, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete) {
        dev.kobolibrary.app.ui.components.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Remove “{0}”?", meta.title)) },
            text = {
                Text(
                    tr("This deletes the book and your edits to it from the library.") +
                        if (book.koboPath != null) tr(" The copy on the Kobo won't be deleted.") else ""
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } },
        )
    }
}

/**
 * Marking a book as finished (it shows as finished instead of how far through, until it's read
 * again) or as unread (how far through goes). Only what applies: a book not started can't be unread.
 */
@Composable
internal fun ReadStateButtons(
    finished: Boolean,
    started: Boolean,
    onMarkFinished: (() -> Unit)?,
    onMarkUnread: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val canFinish = !finished && onMarkFinished != null
    val canUnread = (finished || started) && onMarkUnread != null
    if (!canFinish && !canUnread) return
    // Side by side, with their padding trimmed so both names show whole on a phone; on a very narrow
    // screen, one above the other.
    val button: @Composable (ImageVector, String, () -> Unit, Modifier) -> Unit = { icon, text, onClick, m ->
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), modifier = m) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
    // Whether both names fit side by side depends on the language they're in, so it's measured.
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest = with(density) {
            listOf(tr("Mark as finished"), tr("Mark as unread")).maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp()
        }
        // The text, plus the icon, the gap after it, the button's padding and its border.
        val each = widest + 16.dp + 6.dp + 16.dp + 4.dp
        if (canFinish && canUnread && each * 2 + 12.dp > maxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                button(Icons.Outlined.TaskAlt, tr("Mark as finished"), onMarkFinished!!, Modifier.fillMaxWidth())
                button(Icons.Outlined.RestartAlt, tr("Mark as unread"), onMarkUnread!!, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canFinish) button(Icons.Outlined.TaskAlt, tr("Mark as finished"), onMarkFinished!!, Modifier.weight(1f))
                if (canUnread) button(Icons.Outlined.RestartAlt, tr("Mark as unread"), onMarkUnread!!, Modifier.weight(1f))
            }
        }
    }
}
