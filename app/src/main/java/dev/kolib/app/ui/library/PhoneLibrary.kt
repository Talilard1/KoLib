@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kolib.app.ui.library

import dev.kolib.app.ui.components.hasAll
import dev.kolib.app.ui.components.HidingTop
import dev.kolib.app.ui.filterWords
import dev.kolib.app.ui.components.DisplayButton
import dev.kolib.app.ui.components.shelfCells
import dev.kolib.app.ui.components.chosenPerRow
import dev.kolib.app.ui.tour.tourPlace
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Eject
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.RestartAlt
import dev.kolib.app.ui.components.OutlinedButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
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
    onNewComic: () -> Unit = {},
    onMerge: (List<String>) -> Unit,
    onSplit: (String) -> Unit,
    onExport: (List<String>) -> Unit,
    onDuplicate: (List<String>) -> Unit,
    onAbout: () -> Unit,
    onStats: () -> Unit = {},
    onEject: (() -> Unit)? = null,
    onAddSamples: (() -> Unit)? = null,
    onTheme: (() -> Unit)?,
    onMarkFinished: (List<String>) -> Unit = {},
    onMarkUnread: (List<String>) -> Unit = {},
    onContinueFromKobo: ((String, KoboBook) -> Unit)? = null,
    onSyncReading: ((List<String>) -> Unit)? = null,
    /** Asks to sync the reading of every book in both the library and on the Kobo. */
    onSyncAll: (() -> Unit)? = null,
    koboNotice: KoboNotice? = null,
    transfers: List<dev.kolib.app.link.Transfer> = emptyList(),
    /** The library's comics (in the Comics tab, not with the books), and what can be done with them. */
    comics: List<LibraryBook> = emptyList(),
    comicActions: ComicActions = ComicActions(),
) {
    val notComics = remember(books, comics) { if (comics.isEmpty()) books else books.filterNot { it.isComic } }
    val comicEntries = remember(comics) { dev.kolib.app.library.ComicSeries.entries(comics) }
    val openSeries = if (browse.tab == 2) browse.comicKey?.let { k -> comicEntries.firstOrNull { it.key == k } } else null
    // A book from a collection (in the Collections tab) opens on a page of its own too, going back to the collection; a
    // comic's details from its series, going back to the series.
    val fromCollection = if (browse.tab == 1 && browse.collectionKey != null) browse.collectionBookId?.let { id -> books.firstOrNull { it.id == id } } else null
    val fromSeries = openSeries?.let { e -> browse.comicBookId?.let { id -> e.issues.firstOrNull { it.id == id } } }
    val open = when (browse.tab) {
        1 -> fromCollection
        2 -> fromSeries
        else -> selected?.takeIf { !browse.selecting }
    }
    if (open == null && openSeries != null) {
        // A series opens on a page of its own.
        BackHandler { if (browse.comicSelecting != null) browse.stopComicSelecting() else browse.comicKey = null }
        ComicSeriesPage(
            openSeries, covers, browse, koboConnected, here, comicActions, onBack = { browse.comicKey = null; browse.stopComicSelecting() }, others = comicEntries,
            // A comic's details: held, then Info in the bar for it, rather than a button on each.
            infoAtTop = true,
            // Its comics as covers or a list, as on a wide screen when a series has the screen to itself.
            mode = browse.seriesMode, onMode = { browse.seriesMode = it },
        )
        return
    }
    if (open != null) {
        val inCollection = browse.tab == 1
        val inSeries = browse.tab == 2
        val back = { if (inSeries) browse.comicBookId = null else if (inCollection) browse.collectionBookId = null else browse.selectedId = null }
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
            onSyncReading = onSyncReading?.let { f -> { f(listOf(selected.id)) } },
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
    // Syncing reading with the Kobo: every book in both, or ones picked.
    val onBoth = remember(books) { books.filter { it.koboPath != null && it.canReadFromKoboPlace } }
    val onTap: (LibraryBook) -> Unit = { b -> if (browse.selecting) browse.togglePicked(b.id) else browse.selectedId = b.id }
    // Holding a book chooses it, and every book from the one chosen last to it.
    val onHold: (LibraryBook) -> Unit = { b -> browse.holdPick(b.id, visible.map { it.id }) }
    // The Add books button shrinks to its icon once the list is scrolled, to show more of the covers.
    val atTop by remember {
        derivedStateOf {
            if (browse.grid) browse.gridState.firstVisibleItemIndex == 0 && browse.gridState.firstVisibleItemScrollOffset < 24
            else browse.listState.firstVisibleItemIndex == 0 && browse.listState.firstVisibleItemScrollOffset < 24
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The bar at the top (the search, or the bar for chosen books), and any notices under it.
            val header: @Composable () -> Unit = {
                if (browse.selecting) {
                    val shownIds = visible.map { it.id }
                    PhoneSelectionHeader(
                        count = browse.picked.size,
                        onSelectAll = { visible.forEach { browse.picked[it.id] = true } },
                        onDeselectAll = { browse.deselectAll() },
                        onInvert = { browse.invertPicks(shownIds) },
                        allSelected = browse.picked.hasAll(shownIds),
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
                        onEject?.let { eject ->
                            IconButton(onClick = eject, modifier = Modifier.testTag("library-eject")) { Icon(Icons.Outlined.Eject, tr("Eject the Kobo")) }
                        }
                        dev.kolib.app.ui.ai.LocalOpenAiChat.current?.takeIf { dev.kolib.app.ui.ai.aiButtonShown() }?.let { open ->
                            IconButton(onClick = open, modifier = Modifier.testTag("library-ai")) { dev.kolib.app.ui.ai.AiButtonIcon(tr("AI chat")) }
                        }

                    }
                } else {
                    // Where you are, as the Kobo's screen says it: the library, and how many books it has on this phone;
                    // then the search, and the button for filtering, sorting and how the books show.
                    Column(Modifier.windowInsetsPadding(TopInsets)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).testTag("library-where")) {
                                Text(tr("Library"), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    tr("{0} on {1}", Fmt.plural(books.size, "book"), dev.kolib.app.ui.components.thisDevice()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            onEject?.let { eject ->
                                IconButton(onClick = eject, modifier = Modifier.size(40.dp).testTag("library-eject")) { Icon(Icons.Outlined.Eject, tr("Eject the Kobo")) }
                            }
                            dev.kolib.app.ui.ai.LocalOpenAiChat.current?.takeIf { dev.kolib.app.ui.ai.aiButtonShown() }?.let { open ->
                                IconButton(onClick = open, modifier = Modifier.size(40.dp).testTag("library-ai")) { dev.kolib.app.ui.ai.AiButtonIcon(tr("AI chat")) }
                            }
                            Box(Modifier.tourPlace("library-menu")) { PhoneListMenu(
                                onSelect = { browse.selecting = true },
                                more = listOfNotNull(
                                    onSyncAll?.takeIf { onBoth.isNotEmpty() }?.let { MenuEntry(tr("Sync reading with the Kobo"), Icons.Outlined.Sync, it) },
                                ),
                            ) }
                        }
                        Row(
                            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val onComics = browse.tab == 2
                            PhoneSearchField(
                                query = if (onComics) browse.comicQuery else browse.query,
                                // A search is for books, or in the Comics tab for comics: from the Collections tab it shows books in the Books tab.
                                onQuery = { if (onComics) browse.comicQuery = it else { browse.query = it; if (it.isNotEmpty()) browse.tab = 0 } },
                                placeholder = when {
                                    onComics -> tr("Search comics")
                                    else -> tr("Search library")
                                },
                                onHelp = { showHelp = true },
                                modifier = Modifier.weight(1f).tourPlace("library-search"),
                            )
                            // Filter, Sort and Display, all from the one button, as Mihon's (with the Books tab showing,
                            // its filters and order; with the others, how they show).
                            DisplayButton(
                                if (browse.tab == 2) browse.comicMode else browse.mode,
                                { if (browse.tab == 2) browse.comicMode = it else browse.mode = it },
                                Modifier.size(40.dp).tourPlace("library-display"),
                                extras = when (browse.tab) {
                                    0 -> dev.kolib.app.ui.libraryShelfExtras(notComics, browse)
                                    2 -> dev.kolib.app.ui.comicShelfExtras(browse, wide = false)
                                    else -> null
                                },
                            )
                        }
                    }
                }
                KoboNoticeBar(koboNotice)
                dev.kolib.app.ui.link.TransferStrip(transfers)
                if (job != null && !job.finished) JobStrip(job, onShowJob)
                dev.kolib.app.ui.ocr.OcrStrip(onShow = { id -> browse.selectedId = id })
            }
            if (books.isEmpty()) {
                header()
                EmptyLibrary(koboConnected, onAddFiles, onAddFromKobo, onNewBook, onAddSamples)
                return@Column
            }
            // Swipe left or right for the other tab, or tap it. Choosing books (from anywhere) shows the Books tab.
            // The Comics tab is there with no comics too, unless it's been hidden.
            // (Hidden while it's showing, it's kept till the library has moved on to the Books tab.)
            val tabCount = if (browse.comicsTab || browse.tab == 2) 3 else 2
            LaunchedEffect(browse.comicsTab) { if (!browse.comicsTab && browse.tab == 2) browse.tab = 0 }
            val pager = rememberTabPager(browse.tab, tabCount) { t ->
                if (t != 2) browse.stopComicSelecting()
                browse.tab = t
            }
            LaunchedEffect(browse.selecting) { if (browse.selecting) browse.tab = 0 }
            val goTo = rememberTabTap(pager)
            // The search slides away as the books are scrolled down and comes back on a scroll up (as Apple's bars
            // shrink), so the books have the screen; the tabs stay, so it's always clear where you are.
            HidingTop(
                top = { Column { header() } },
                hides = !browse.selecting && browse.comicSelecting == null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (!browse.selecting && browse.comicSelecting == null) {
                        PrimaryTabRow(selectedTabIndex = pager.currentPage.coerceAtMost(tabCount - 1), containerColor = Color.Transparent, modifier = Modifier.tourPlace("library-tabs")) {
                            Tab(selected = pager.currentPage == 0, onClick = { goTo(0) }, text = { Text(tr("Books")) })
                            Tab(selected = pager.currentPage == 1, onClick = { goTo(1) }, text = { Text(tr("Collections")) })
                            if (tabCount == 3) Tab(selected = pager.currentPage == 2, onClick = { goTo(2) }, text = { Text(tr("Comics")) }, modifier = Modifier.testTag("tab-comics"))
                        }
                    }
                    TabPages(pager, Modifier.fillMaxSize().tourPlace("library-shelf"), swipe = !browse.selecting && browse.comicSelecting == null) { page ->
                        when (page) {
                            2 -> LibraryComicsTab(comics, covers, browse, koboConnected, here, comicActions) { _, _, _ -> }
                            1 -> LibraryCollectionsTab(books, covers, browse, koboConnected, reading, here, onSend) { _, _, _ -> }
                            else -> Column(Modifier.fillMaxSize()) { PhoneBooksPage(notComics, visible, covers, browse, reading, here, onTap, onHold, onContinue = { onRead(it.id) }) }
                        }
                    }
                }
            }
        }

        if (!browse.selecting && browse.comicSelecting == null && books.isNotEmpty()) {
            GoldFab(
                text = tr("Add books"),
                icon = Icons.Filled.Add,
                expanded = atTop,
                onClick = { addSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .tourPlace("library-add"),
            )
        }
        if (browse.selecting) {
            PhoneActionBar(
                librarySelectionActions(
                    count = browse.picked.size, chosen = chosen, stop = { browse.stopSelecting() },
                    onSend = onSend, onMarkFinished = onMarkFinished, onMarkUnread = onMarkUnread, onSyncReading = onSyncReading,
                    onExport = onExport, onDuplicate = onDuplicate, onMerge = onMerge, onRemove = { confirmRemove = it },
                ),
                Modifier.align(Alignment.BottomCenter),
                moreTag = "selection-more",
            )
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
            onNewComic = onNewComic,
            onAddSamples = onAddSamples,
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

/** Ways to add books, in a sheet from the bottom of the screen. */
@Composable
private fun AddBooksSheet(
    koboConnected: Boolean,
    onDismiss: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFromKobo: () -> Unit,
    onAddFolder: () -> Unit,
    onNewBook: () -> Unit,
    onNewComic: () -> Unit = {},
    onAddSamples: (() -> Unit)? = null,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text(tr("Add books"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        SheetItem(Icons.Outlined.FileOpen, tr("Files from this device"), tr("EPUB, PDF, MOBI, Word and more")) { onDismiss(); onAddFiles() }
        SheetItem(Icons.Outlined.FolderOpen, tr("A folder of books"), tr("Every book in it, and in the folders inside it")) { onDismiss(); onAddFolder() }
        SheetItem(Icons.Outlined.Usb, tr("Books from the Kobo"), if (koboConnected) null else tr("Connect your Kobo first")) { onDismiss(); onAddFromKobo() }
        SheetItem(Icons.AutoMirrored.Outlined.NoteAdd, tr("Write a new book")) { onDismiss(); onNewBook() }
        SheetItem(Icons.Outlined.AddPhotoAlternate, tr("Make a comic from pictures")) { onDismiss(); onNewComic() }
        onAddSamples?.let { add -> SheetItem(Icons.Outlined.AutoStories, tr("Sample books")) { onDismiss(); add() } }
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
    /** Syncing the book's reading with the Kobo. */
    onSyncReading: (() -> Unit)? = null,
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
                Box(Modifier.tourPlace("book-more")) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("book-more")) { Icon(Icons.Filled.MoreVert, tr("More options")) }
                    // What's done now and then with a book, kept off the page so Read and Send stand out (as Apple's
                    // More menu): marking it read or not, copies of it, splitting it, and removing it.
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val started = readHere != null && readHere > 0.005f
                        if (!finished && onMarkFinished != null) DropdownMenuItem(
                            text = { Text(tr("Mark as finished")) },
                            leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) },
                            onClick = { menu = false; onMarkFinished() },
                        )
                        if ((finished || started) && onMarkUnread != null) DropdownMenuItem(
                            text = { Text(tr("Mark as unread")) },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                            onClick = { menu = false; onMarkUnread() },
                        )
                        dev.kolib.app.ui.link.LocalLinkActions.current?.let { link ->
                            DropdownMenuItem(
                                text = { Text(tr("Send to device")) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.SendToMobile, null) },
                                onClick = { menu = false; link.sendBooks(listOf(book.id)) },
                            )
                        }
                        DropdownMenuItem(text = { Text(tr("Save as file")) }, leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text(tr("Duplicate")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                        // A comic is split in the comic editor, at the pages its parts start on.
                        DropdownMenuItem(
                            text = { Text(if (book.isComic) tr("Split into comics") else tr("Split into books")) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) },
                            onClick = { menu = false; onSplit() },
                            modifier = Modifier.testTag("book-split"),
                        )
                        HorizontalDivider()
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
            reading?.let { k ->
                ReadingLine(
                    k, live = readingLive, here = readHere, wideButton = true,
                    onContinue = onContinueFromKobo?.takeIf { book.canReadFromKoboPlace }?.let { f -> { f(k) } },
                    onSync = onSyncReading?.takeIf { book.canReadFromKoboPlace },
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
            // The changes made most often; the rest are in the menu at the top. A comic's pages are pictures: they're
            // edited in the comic editor, not as text.
            val tiles = listOf(
                Triple(Icons.Outlined.Edit, tr("Edit details"), onEdit),
                Triple(Icons.Outlined.Image, tr("Change cover"), onCover),
                if (book.isComic) Triple(Icons.Outlined.PhotoLibrary, tr("Edit pages"), onEditText) else Triple(Icons.Outlined.EditNote, tr("Edit text"), onEditText),
            )
            Column(Modifier.tourPlace("book-edit")) {
                tiles.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (icon, label, action) ->
                            val tag = if (action === onEditText) (if (book.isComic) "book-edit-pages" else "book-edit-text") else null
                            ActionTile(icon, label, action, Modifier.weight(1f).then(if (tag != null) Modifier.testTag(tag) else Modifier))
                        }
                        // A short last row keeps its tiles the size of the others.
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
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
    /** Opens a book being read where it was left (from the row of them at the top). */
    onContinue: (LibraryBook) -> Unit = {},
) {
    // The books being read, along the top of the shelf while it isn't narrowed (and while books are chosen, so they can
    // be chosen there too).
    val continuing = if (!browse.narrowed && dev.kolib.app.ui.components.LocalCoversPerRow.current.showContinue) beingRead(books, here) else emptyList()
    val continueRow: @Composable () -> Unit = {
        // Held, a book's chosen, as on the shelf; while choosing, a tap chooses one (or lets it go).
        ContinueReadingRow(
            continuing, covers, here,
            onTap = { b -> if (browse.selecting) browse.togglePicked(b.id) else onContinue(b) },
            onHold = { b -> browse.holdPick(b.id, continuing.map { it.id }) },
            picked = if (browse.selecting) ({ b -> browse.picked.containsKey(b.id) }) else null,
            sidePadding = 8.dp,
        )
    }
    // Where the filters' chips were: what's showing and in what order (they're chosen with the button at the top).
    dev.kolib.app.ui.ShelfLine(
        visible.size, books.size, browse.narrowed, browse.filterWords(), tr("sorted by {0}", browse.sortDescription),
        onClear = { browse.clearFilters() }, modifier = Modifier.padding(start = 16.dp, end = 8.dp),
    )
    if (visible.isEmpty()) {
        EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search, or clear the filters."))
        return
    }
    ContinueRowKeepsTop(continuing.isNotEmpty(), browse.gridState, browse.listState)
    // Room under the last row, so the Add books button (or the bar for chosen books) doesn't cover it.
    val underLast = 104.dp
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
            if (continuing.isNotEmpty()) item(key = "continue", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { continueRow() }
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
            if (continuing.isNotEmpty()) item(key = "continue") { continueRow() }
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
