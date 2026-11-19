@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kolib.app.ui.library

import dev.kolib.app.ui.components.seriesSearch
import dev.kolib.app.ui.components.DetailText
import dev.kolib.app.ui.components.titleSearch
import dev.kolib.app.ui.components.authorSearch
import androidx.compose.ui.text.rememberTextMeasurer
import dev.kolib.app.tr
import dev.kolib.app.ui.KoboNotice
import dev.kolib.app.ui.KoboNoticeBar
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
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
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
import dev.kolib.app.ui.components.OutlinedButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import dev.kolib.app.ui.components.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import dev.kolib.app.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.outlined.AutoAwesome
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
import dev.kolib.app.JobProgress
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.ui.MenuEntry
import dev.kolib.app.ui.PhoneChipRow
import dev.kolib.app.ui.PhoneCollectionChip
import dev.kolib.app.ui.PhoneListMenu
import dev.kolib.app.ui.PhoneNarrowedLine
import dev.kolib.app.ui.PhoneSortChip
import dev.kolib.app.ui.PhoneTagsChip
import dev.kolib.app.ui.PhoneToggleChip
import dev.kolib.app.ui.components.KoLibMark
import dev.kolib.app.ui.components.GoldButton
import dev.kolib.app.ui.components.GoldFab
import dev.kolib.app.ui.components.SeriesStyle
import dev.kolib.app.ui.components.ActionTile
import dev.kolib.app.ui.components.EmptyState
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.HideBottomBar
import dev.kolib.app.ui.components.InfoRow
import dev.kolib.app.ui.components.LibraryCover
import dev.kolib.app.ui.components.PhoneAction
import dev.kolib.app.ui.components.PhoneActionBar
import dev.kolib.app.ui.components.PhoneSearchField
import dev.kolib.app.ui.components.PhoneSelectionHeader
import dev.kolib.app.ui.components.SectionCard
import dev.kolib.app.ui.components.ShelfGridBox
import dev.kolib.app.ui.components.TopInsets
import dev.kolib.app.ui.descriptionText
import dev.kolib.app.ui.languageName
import dev.kolib.app.ui.theme.LocalBookcase
import dev.kolib.app.ui.theme.ShelfColors
import kotlin.math.roundToInt
import dev.kolib.app.ui.components.TabPages
import dev.kolib.app.ui.components.rememberTabPager
import dev.kolib.app.ui.components.rememberTabTap
import androidx.compose.runtime.LaunchedEffect

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
    onStats: () -> Unit = {},
    onTheme: (() -> Unit)?,
    onMarkFinished: (List<String>) -> Unit = {},
    onMarkUnread: (List<String>) -> Unit = {},
    onContinueFromKobo: ((String, KoboBook) -> Unit)? = null,
    koboNotice: KoboNotice? = null,
    transfers: List<dev.kolib.app.link.Transfer> = emptyList(),
) {
    // A book from a collection (in the Collections tab) opens on a page of its own too, going back to the collection.
    val fromCollection = if (browse.tab == 1 && browse.collectionKey != null) browse.collectionBookId?.let { id -> books.firstOrNull { it.id == id } } else null
    val open = if (browse.tab == 1) fromCollection else selected?.takeIf { !browse.selecting }
    if (open != null) {
        val inCollection = browse.tab == 1
        val back = { if (inCollection) browse.collectionBookId = null else browse.selectedId = null }
        // A tag or a search from a collection's book shows the books in the Books tab.
        val toBooks = {
            browse.collectionBookId = null
            browse.selectedId = null
            browse.tab = 0
        }
        BackHandler(onBack = back)
        val selected = open
        PhoneBookPage(
            book = selected,
            covers = covers,
            koboConnected = koboConnected,
            onBack = back,
            onRead = { onRead(selected.id) },
            onSend = { onSend(listOf(selected.id)) },
            onEdit = { onEditDetails(selected.id) },
            onCover = { onChangeCover(selected.id) },
            onEditText = { onEditText(selected.id) },
            onSplit = { onSplit(selected.id) },
            onExport = { onExport(listOf(selected.id)) },
            onDuplicate = { onDuplicate(listOf(selected.id)) },
            onDelete = {
                back()
                onDelete(listOf(selected.id))
            },
            onCollection = { name ->
                if (inCollection) {
                    browse.collectionKey = name.trim().lowercase()
                    browse.collectionBookId = null
                } else {
                    browse.collection = name
                    browse.selectedId = null
                }
            },
            onTag = { tag ->
                browse.tags = setOf(tag)
                if (inCollection) toBooks() else browse.selectedId = null
            },
            onSearch = { search ->
                browse.query = search
                browse.collection = null
                browse.tags = emptySet()
                if (inCollection) toBooks() else browse.selectedId = null
            },
            readHere = readingHere[selected.id]?.takeIf { !it.finished }?.progress,
            reading = reading(selected),
            readingLive = koboLive,
            finished = readingHere[selected.id]?.finished == true,
            onMarkFinished = { onMarkFinished(listOf(selected.id)) },
            onMarkUnread = { onMarkUnread(listOf(selected.id)) },
            onContinueFromKobo = onContinueFromKobo?.let { f -> { k: KoboBook -> f(selected.id, k) } },
            koboNotice = koboNotice,
            transfers = transfers,
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
    // Holding a book chooses it, and every book from the one chosen last to it.
    val onHold: (LibraryBook) -> Unit = { b -> browse.holdPick(b.id, visible.map { it.id }) }
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
                    dev.kolib.app.ui.ai.LocalOpenAiChat.current?.takeIf { dev.kolib.app.ui.ai.aiButtonShown() }?.let { open ->
                        IconButton(onClick = open, modifier = Modifier.testTag("library-ai")) { dev.kolib.app.ui.ai.AiButtonIcon(tr("AI chat")) }
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("More options")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            onTheme?.let { theme ->
                                DropdownMenuItem(text = { Text(tr("Settings")) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menu = false; theme() })
                            }
                            DropdownMenuItem(text = { Text(tr("Reading stats")) }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = { menu = false; onStats() })
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
                        // A search is for books: from the Collections tab it shows them in the Books tab.
                        onQuery = { browse.query = it; if (it.isNotEmpty()) browse.tab = 0 },
                        placeholder = if (books.isEmpty()) tr("Search the library") else tr("Search {0}", Fmt.plural(books.size, "book")),
                        onHelp = { showHelp = true },
                        modifier = Modifier.weight(1f),
                    )
                    dev.kolib.app.ui.ai.LocalOpenAiChat.current?.takeIf { dev.kolib.app.ui.ai.aiButtonShown() }?.let { open ->
                        IconButton(onClick = open, modifier = Modifier.size(40.dp).testTag("library-ai")) { dev.kolib.app.ui.ai.AiButtonIcon(tr("AI chat")) }
                    }
                    PhoneListMenu(
                        grid = browse.grid,
                        onSelect = { browse.selecting = true },
                        onGrid = { browse.grid = it },
                        more = listOfNotNull(
                            onTheme?.let { MenuEntry(tr("Settings"), Icons.Outlined.Settings, it) },
                            MenuEntry(tr("Reading stats"), Icons.Outlined.BarChart, onStats),
                            MenuEntry(tr("About KoLib"), Icons.Outlined.Info, onAbout),
                        ),
                    )
                }
            }
            KoboNoticeBar(koboNotice)
            dev.kolib.app.ui.link.TransferStrip(transfers)
            if (job != null && !job.finished) JobStrip(job, onShowJob)
            dev.kolib.app.ui.ocr.OcrStrip(onShow = { id -> browse.selectedId = id })
            if (books.isEmpty()) {
                EmptyLibrary(koboConnected, onAddFiles, onAddFromKobo, onNewBook)
                return@Column
            }
            // Swipe left or right for the other tab, or tap it. Choosing books (from anywhere) shows the Books tab.
            val pager = rememberTabPager(browse.tab, 2) { browse.tab = it }
            LaunchedEffect(browse.selecting) { if (browse.selecting) browse.tab = 0 }
            val goTo = rememberTabTap(pager)
            if (!browse.selecting) {
                PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
                    Tab(selected = pager.currentPage == 0, onClick = { goTo(0) }, text = { Text(tr("Books")) })
                    Tab(selected = pager.currentPage == 1, onClick = { goTo(1) }, text = { Text(tr("Collections")) })
                }
            }
            TabPages(pager, Modifier.fillMaxSize(), swipe = !browse.selecting) { page ->
                if (page == 1) LibraryCollectionsTab(books, covers, browse, koboConnected, reading, here, onSend) { _, _, _ -> }
                else Column(Modifier.fillMaxSize()) { PhoneBooksPage(books, visible, covers, browse, reading, here, onTap, onHold) }
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
                dev.kolib.app.ui.link.LocalLinkActions.current?.let { link ->
                    PhoneAction(Icons.AutoMirrored.Outlined.SendToMobile, tr("To device"), { link.sendBooks(chosen()) }, enabled = any)
                }
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
    /** Moving the app's reader to where the Kobo is in the book. */
    onContinueFromKobo: ((KoboBook) -> Unit)? = null,
    /** Searches for books (a search for the tab: the book's title, author or series), from holding one of those. */
    onSearch: ((String) -> Unit)? = null,
    /** What to say about the Kobo under the bar at the top, as the library does. */
    koboNotice: KoboNotice? = null,
    /** What's being sent to or taken in from the user's other devices (a book is sent from this page). */
    transfers: List<dev.kolib.app.link.Transfer> = emptyList(),
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
        KoboNoticeBar(koboNotice)
        dev.kolib.app.ui.link.TransferStrip(transfers)
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
            // A tap on the title, author or series copies it; holding it, a search for it here or on the internet.
            DetailText(meta.title, MaterialTheme.typography.headlineSmall, tr("Title copied"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                inApp = titleSearch(meta.title), onSearch = onSearch)
            book.authorLine?.let {
                Spacer(Modifier.height(4.dp))
                DetailText(it, MaterialTheme.typography.titleMedium, tr("Author copied"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    inApp = authorSearch(meta.authors), onSearch = onSearch)
            }
            meta.seriesLabel?.let { label ->
                val name = meta.series.orEmpty()
                DetailText(label, SeriesStyle(), tr("Series copied"), Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                    copy = name, inApp = seriesSearch(name), onSearch = onSearch)
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
            reading?.let { k ->
                ReadingLine(
                    k, live = readingLive, here = readHere, wideButton = true,
                    onContinue = onContinueFromKobo?.takeIf { book.canReadFromKoboPlace }?.let { f -> { f(k) } },
                )
            }
            dev.kolib.app.ui.ocr.OcrSection(book)
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
            // A comic's pages are pictures in its comic file: there's no text to edit or split.
            val tiles = listOfNotNull(
                Triple(Icons.Outlined.Edit, tr("Edit details"), onEdit),
                Triple(Icons.Outlined.Image, tr("Change cover"), onCover),
                Triple(Icons.Outlined.EditNote, tr("Edit text"), onEditText).takeUnless { book.isComic },
                Triple(Icons.AutoMirrored.Outlined.CallSplit, tr("Split into books"), onSplit).takeUnless { book.isComic },
                Triple(Icons.Outlined.SaveAlt, tr("Save as file"), onExport),
                Triple(Icons.Outlined.ContentCopy, tr("Duplicate"), onDuplicate),
                dev.kolib.app.ui.link.LocalLinkActions.current?.let { link ->
                    Triple(Icons.AutoMirrored.Outlined.SendToMobile, tr("Send to device"), { link.sendBooks(listOf(book.id)) })
                },
            )
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, label, action) -> ActionTile(icon, label, action, Modifier.weight(1f)) }
                    // A short last row keeps its tiles the size of the others.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
        dev.kolib.app.ui.components.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Remove “{0}”?", meta.title)) },
            text = {
                Text(
                    tr("The book and your edits are deleted from the library.") +
                        if (book.koboPath != null) tr(" The copy on the Kobo isn't affected.") else ""
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

/** The phone's Books tab: the filters, then the books as a grid or a list. */
@Composable
private fun PhoneBooksPage(
    books: List<LibraryBook>,
    visible: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    reading: (LibraryBook) -> KoboBook?,
    here: (LibraryBook) -> ReadHere?,
    onTap: (LibraryBook) -> Unit,
    onHold: (LibraryBook) -> Unit,
) {
    PhoneLibraryFilters(books, browse)
    if (browse.narrowed) PhoneNarrowedLine(visible.size, books.size) { browse.clearFilters() }
    if (visible.isEmpty()) {
        EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search, or clear the filters."))
        return
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
                .then(if (bookcase) Modifier.background((dev.kolib.app.ui.theme.LocalKoLibStyle.current.shelf ?: ShelfColors).back) else Modifier),
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
