@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package dev.kolib.app.ui.library

import dev.kolib.app.ui.components.UnderCover
import dev.kolib.app.ui.components.DisplayButton
import dev.kolib.app.ui.components.shelfMinCell
import dev.kolib.app.ui.components.shelfCells
import dev.kolib.app.ui.components.chosenPerRow
import dev.kolib.app.ui.components.ShortTopRow
import androidx.compose.material3.FilledIconButton
import androidx.compose.foundation.layout.WindowInsets
import dev.kolib.app.ui.components.TabLabel
import dev.kolib.app.ui.components.shortScreen
import dev.kolib.app.ui.components.HidingTop
import dev.kolib.app.ui.tour.tourPlace
import androidx.compose.foundation.layout.RowScope
import dev.kolib.app.ui.components.BarAction
import dev.kolib.app.ui.components.AtFoot
import dev.kolib.app.ui.components.FittedText
import dev.kolib.app.ui.components.WithFoot
import dev.kolib.app.tr
import dev.kolib.app.ui.components.seriesSearch
import dev.kolib.app.ui.components.DetailText
import dev.kolib.app.ui.components.titleSearch
import dev.kolib.app.ui.components.authorSearch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.WarningAmber
import dev.kolib.app.ui.components.KoLibMark
import dev.kolib.app.ui.components.SeriesStyle
import dev.kolib.app.ui.components.GoldButton
import dev.kolib.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import dev.kolib.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import dev.kolib.app.ui.components.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import dev.kolib.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Eject
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.JobKind
import dev.kolib.app.JobProgress
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.library.LibraryBook
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.TaskAlt
import dev.kolib.app.ui.components.ShelfSpace
import dev.kolib.app.ui.components.shelfUnderCover
import dev.kolib.app.ui.theme.LocalBookcase
import dev.kolib.app.ui.theme.LocalKoLibStyle
import dev.kolib.app.kobo.ReadStatus
import dev.kolib.app.kobo.KoboPaths
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.ui.SelectionBar
import dev.kolib.app.ui.TwoPaneWidth
import dev.kolib.app.ui.components.EmptyState
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.InfoRow
import dev.kolib.app.ui.components.LibraryCover
import dev.kolib.app.ui.components.SectionCard
import dev.kolib.app.ui.descriptionText
import dev.kolib.app.ui.languageName
import dev.kolib.app.ui.theme.ShelfColors
import dev.kolib.app.ui.components.ShelfGridBox
import dev.kolib.app.ui.components.gridCover
import dev.kolib.app.ui.components.phoneLayout
import dev.kolib.app.ui.components.TabBackHandler
import dev.kolib.app.ui.components.TabPages
import dev.kolib.app.ui.KoboNotice
import dev.kolib.app.ui.KoboNoticeBar
import dev.kolib.app.ui.components.rememberTabPager
import dev.kolib.app.ui.components.rememberTabTap
import androidx.compose.runtime.LaunchedEffect

@Composable
fun LibraryScreen(
    books: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    job: JobProgress?,
    koboConnected: Boolean,
    onAddFiles: () -> Unit,
    onAddFromKobo: () -> Unit,
    /** Adds the books in a folder (and the folders in it). */
    onAddFolder: () -> Unit = {},
    onShowJob: () -> Unit,
    onEditDetails: (String) -> Unit,
    onChangeCover: (String) -> Unit,
    onDelete: (List<String>) -> Unit,
    onSend: (List<String>) -> Unit,
    onRead: (String) -> Unit = {},
    onEditText: (String) -> Unit = {},
    /** How far each book has been read in the app's reader, by book id. */
    readingHere: Map<String, ReadHere> = emptyMap(),
    /** The Kobo's books by ContentID (live or as last seen), for reading progress. */
    onKobo: Map<String, KoboBook> = emptyMap(),
    /** Whether [onKobo] is the connected Kobo rather than the last-seen copy. */
    koboLive: Boolean = false,
    /** Making books: a new one to write, one from several picked, several from one. */
    onNewBook: () -> Unit = {},
    onMerge: (List<String>) -> Unit = {},
    onSplit: (String) -> Unit = {},
    /** Saving copies of books as files, somewhere chosen in Android's pickers. */
    onExport: (List<String>) -> Unit = {},
    /** Copies of books in the library, called "Title (copy)". */
    onDuplicate: (List<String>) -> Unit = {},
    /** The app's version and the licences of what's in it. */
    onAbout: () -> Unit = {},
    /** Opens the reading stats. */
    onStats: () -> Unit = {},
    /** Shows how to eject the Kobo; null when no Kobo is plugged in. */
    onEject: (() -> Unit)? = null,
    /** Adds the sample books that come with KoLib, offered when the library is empty. */
    onAddSamples: (() -> Unit)? = null,
    /** Choosing how the app looks (on phones it's in the library's menu; tablets have it beside the sections). */
    onTheme: (() -> Unit)? = null,
    /** Marking books as finished, or as unread, in the app's reader. */
    onMarkFinished: (List<String>) -> Unit = {},
    onMarkUnread: (List<String>) -> Unit = {},
    /** Moving the app's reader to where the Kobo is in a book, and opening it there. */
    onContinueFromKobo: ((String, KoboBook) -> Unit)? = null,
    /** Syncing books' reading with the Kobo: where they're up to, and their bookmarks, highlights and notes. */
    onSyncReading: ((List<String>) -> Unit)? = null,
    /** What to say at the top about the Kobo: opening, being saved to, safe to unplug, unplugged. */
    koboNotice: KoboNotice? = null,
    /** What's being sent to or taken in from the user's other devices, for its progress bars. */
    transfers: List<dev.kolib.app.link.Transfer> = emptyList(),
    /** Putting comics in one series, of the name given; and taking them out of theirs. */
    onGroupComics: (List<String>, String) -> Unit = { _, _ -> },
    onTakeOutOfSeries: (List<String>) -> Unit = {},
) {
    // Comics have a tab of their own, a series to an entry; the Books tab has the rest.
    // (With the Comics tab hidden, comics are books like the rest.)
    val comics = remember(books, browse.comicsTab) { if (browse.comicsTab) libraryComics(books) else emptyList() }
    val notComics = remember(books, comics) { if (comics.isEmpty()) books else books.filterNot { it.isComic } }
    val comicActions = remember(onRead, onSend, onDelete, onMarkFinished, onMarkUnread, onGroupComics, onTakeOutOfSeries) {
        ComicActions(onRead, onSend, onDelete, onMarkFinished, onMarkUnread, onGroupComics, onTakeOutOfSeries)
    }
    val reading: (LibraryBook) -> KoboBook? = remember(onKobo) {
        { b -> b.koboPath?.let { onKobo[KoboPaths.contentId(it)] } }
    }
    val here: (LibraryBook) -> ReadHere? = remember(readingHere) { { b -> readingHere[b.id] } }
    val visible = remember(notComics, onKobo, readingHere, browse.query, browse.filter, browse.collection, browse.sort, browse.descending, browse.tags, browse.tagsMatchAll) {
        LibrarySorting.filter(notComics, browse.query, browse.filter, browse.collection, browse.sort, browse.descending, browse.tags, browse.tagsMatchAll, reading, here)
    }
    val selected = browse.selectedId?.let { id -> books.firstOrNull { it.id == id } }
    // Every way to sync reading with the Kobo asks first, in the one window where the ways it goes are chosen.
    var syncAsk by remember { mutableStateOf<List<String>?>(null) }
    var syncAll by remember { mutableStateOf(false) }
    val realSync = onSyncReading
    val askSync: ((List<String>) -> Unit)? = realSync?.let { { ids: List<String> -> syncAll = false; syncAsk = ids } }
    val askSyncAll: (() -> Unit)? = realSync?.let {
        {
            syncAll = true
            syncAsk = books.filter { b -> b.koboPath != null && b.canReadFromKoboPlace }.map { b -> b.id }
        }
    }
    syncAsk?.let { ids ->
        if (realSync != null) SyncReadingDialog(
            count = ids.size,
            all = syncAll,
            onSync = { syncAsk = null; realSync(ids) },
            onChoose = if (syncAll) ({ syncAsk = null; browse.selectedId = null; browse.selecting = true }) else null,
            onDismiss = { syncAsk = null },
        )
    }
    // What's showing, for the AI to know when it's asked about "these books".
    dev.kolib.app.ui.ai.LocalAiHost.current?.let { host ->
        SideEffect { host.library = libraryOnScreen(browse, visible, selected?.id) }
        DisposableEffect(host) { onDispose { host.library = null } }
    }
    if (phoneLayout()) {
        PhoneLibraryScreen(
            books = books, visible = visible, covers = covers, browse = browse, selected = selected, job = job,
            koboConnected = koboConnected, reading = reading, here = here, readingHere = readingHere, koboLive = koboLive,
            onAddFiles = onAddFiles, onAddFromKobo = onAddFromKobo, onAddFolder = onAddFolder, onShowJob = onShowJob, onEditDetails = onEditDetails,
            onChangeCover = onChangeCover, onDelete = onDelete, onSend = onSend, onRead = onRead, onEditText = onEditText,
            onNewBook = onNewBook, onMerge = onMerge, onSplit = onSplit, onExport = onExport, onDuplicate = onDuplicate,
            onAbout = onAbout, onStats = onStats, onEject = onEject, onAddSamples = onAddSamples, onTheme = onTheme, onMarkFinished = onMarkFinished, onMarkUnread = onMarkUnread,
            onContinueFromKobo = onContinueFromKobo, onSyncReading = askSync, onSyncAll = askSyncAll, koboNotice = koboNotice, transfers = transfers,
            comics = comics, comicActions = comicActions,
        )
        return
    }
    var addMenu by remember { mutableStateOf(false) }
    // On a short screen (a phone held sideways), the bar, the tabs and the search slide away as the books are
    // scrolled, and come back as they're scrolled back.
    val short = shortScreen()
    val actions: @Composable RowScope.() -> Unit = {
        onEject?.let { eject ->
            TextButton(onClick = eject, modifier = Modifier.testTag("library-eject")) {
                Icon(Icons.Outlined.Eject, null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Eject"))
            }
        }
        dev.kolib.app.ui.ai.LocalOpenAiChat.current?.takeIf { dev.kolib.app.ui.ai.aiButtonShown() }?.let { open ->
            IconButton(onClick = open, modifier = Modifier.testTag("library-ai")) { dev.kolib.app.ui.ai.AiButtonIcon(tr("AI chat")) }
        }
        val onBoth = books.filter { it.koboPath != null && it.canReadFromKoboPlace }
        if (askSyncAll != null && onBoth.isNotEmpty()) {
            IconButton(onClick = askSyncAll, modifier = Modifier.testTag("library-sync-reading").tourPlace("library-sync")) { Icon(Icons.Outlined.Sync, tr("Sync reading with the Kobo")) }
        }
        IconButton(onClick = onStats, modifier = Modifier.tourPlace("library-stats")) { Icon(Icons.Outlined.BarChart, tr("Reading stats")) }
        IconButton(onClick = onAbout) { Icon(Icons.Outlined.Info, tr("About and licences")) }
        Box {
            // On a short screen, where the tabs share the row, just its sign.
            if (short) FilledIconButton(onClick = { addMenu = true }, modifier = Modifier.tourPlace("library-add")) {
                Icon(Icons.Filled.Add, tr("Add books"))
            } else GoldButton(onClick = { addMenu = true }, modifier = Modifier.tourPlace("library-add")) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Add books"))
            }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(
                    text = { Text(tr("Files from this device")) },
                    leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                    onClick = { addMenu = false; onAddFiles() },
                )
                DropdownMenuItem(
                    text = { Text(tr("A folder of books")) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                    onClick = { addMenu = false; onAddFolder() },
                )
                DropdownMenuItem(
                    text = { Text(if (koboConnected) tr("Books from the Kobo") else tr("Books from the Kobo (connect it first)")) },
                    leadingIcon = { Icon(Icons.Outlined.Usb, null) },
                    onClick = { addMenu = false; onAddFromKobo() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(tr("Write a new book")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null) },
                    onClick = { addMenu = false; onNewBook() },
                )
                onAddSamples?.let { add ->
                    DropdownMenuItem(
                        text = { Text(tr("Sample books")) },
                        leadingIcon = { Icon(Icons.Outlined.AutoStories, null) },
                        onClick = { addMenu = false; add() },
                        modifier = Modifier.testTag("library-add-samples-menu"),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
    }
    val topBar: @Composable (WindowInsets) -> Unit = { insets ->
        TopAppBar(
            title = {
                Column {
                    Text(tr("Library"))
                    Text(
                        if (books.size == 1) tr("1 book on this device") else tr("{0} books on this device", books.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = insets,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { if (!short) topBar(TopAppBarDefaults.windowInsets) },
    ) { padding ->
            val notices: @Composable () -> Unit = {
                KoboNoticeBar(koboNotice)
                dev.kolib.app.ui.link.TransferStrip(transfers)
                if (job != null && !job.finished) JobStrip(job, onShowJob)
                dev.kolib.app.ui.ocr.OcrStrip(onShow = { id -> browse.selectedId = id })
            }
            if (books.isEmpty()) {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (short) ShortTopRow(tr("Library"), tabs = {}, actions = actions)
                    notices()
                    EmptyLibrary(koboConnected, onAddFiles, onAddFromKobo, onNewBook, onAddSamples)
                }
                return@Scaffold
            }
            val collectionCount = remember(books) { libraryCollectionCount(books) }
            val seriesCount = remember(comics) { dev.kolib.app.library.ComicSeries.entries(comics).size }
            // Swipe left or right for the other tab, or tap it. Books being chosen are let go of on the other tabs,
            // and choosing books (from anywhere) shows the Books tab. The Comics tab is there with no comics too,
            // unless it's been hidden.
            // (Hidden while it's showing, it's kept till the library has moved on to the Books tab.)
            val tabCount = if (browse.comicsTab || browse.tab == 2) 3 else 2
            LaunchedEffect(browse.comicsTab) { if (!browse.comicsTab && browse.tab == 2) browse.tab = 0 }
            val pager = rememberTabPager(browse.tab.coerceAtMost(tabCount - 1), tabCount) { t ->
                if (t != 0) browse.stopSelecting()
                if (t != 2) browse.stopComicSelecting()
                browse.tab = t
            }
            LaunchedEffect(browse.selecting) { if (browse.selecting) browse.tab = 0 }
            val goTo = rememberTabTap(pager)
            HidingTop(
                hides = short,
                top = {
                    val tabs: @Composable (Modifier) -> Unit = { m ->
                        PrimaryTabRow(selectedTabIndex = pager.currentPage.coerceAtMost(tabCount - 1), containerColor = Color.Transparent, modifier = m.tourPlace("library-tabs")) {
                            Tab(selected = pager.currentPage == 0, onClick = { goTo(0) }, text = { TabLabel(tr("Books ({0})", notComics.size)) })
                            Tab(selected = pager.currentPage == 1, onClick = { goTo(1) }, text = { TabLabel(tr("Collections ({0})", collectionCount)) })
                            if (tabCount == 3) Tab(selected = pager.currentPage == 2, onClick = { goTo(2) }, text = { TabLabel(tr("Comics ({0})", seriesCount)) }, modifier = Modifier.testTag("tab-comics"))
                        }
                    }
                    Column {
                        if (short) ShortTopRow(tr("Library"), tabs = { tabs(Modifier) }, actions = actions)
                        notices()
                        if (!short) tabs(Modifier)
                    }
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
            // The bar for what's chosen goes along the foot, the screen's whole width, under a side panel too.
            WithFoot(Modifier.fillMaxSize()) { BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoPanes = maxWidth >= TwoPaneWidth
                // From the Collections tab, a tag or a search shows the books in the Books tab (the book still open there on a wide screen).
                val toBooks = { book: LibraryBook ->
                    browse.tab = 0
                    browse.selectedId = if (twoPanes) book.id else null
                }
                val detail: @Composable (LibraryBook, (() -> Unit)?, String?) -> Unit = { book, onBack, inCollection ->
                    LibraryDetailPane(
                        book, covers, onBack,
                        backLabel = inCollection ?: tr("Library"),
                        koboConnected = koboConnected,
                        onEdit = { onEditDetails(book.id) },
                        onCover = { onChangeCover(book.id) },
                        onRead = { onRead(book.id) },
                        onEditText = { onEditText(book.id) },
                        readHere = readingHere[book.id]?.takeIf { !it.finished }?.progress,
                        finished = readingHere[book.id]?.finished == true,
                        onMarkFinished = { onMarkFinished(listOf(book.id)) },
                        onMarkUnread = { onMarkUnread(listOf(book.id)) },
                        onDelete = { onDelete(listOf(book.id)) },
                        onSend = { onSend(listOf(book.id)) },
                        onSplit = { onSplit(book.id) },
                        onExport = { onExport(listOf(book.id)) },
                        onDuplicate = { onDuplicate(listOf(book.id)) },
                        reading = reading(book),
                        readingLive = koboLive,
                        onContinueFromKobo = onContinueFromKobo?.let { f -> { k: KoboBook -> f(book.id, k) } },
                        onSyncReading = askSync?.let { f -> { f(listOf(book.id)) } },
                        onCollection = { name ->
                            if (inCollection != null) {
                                // Another of its collections opens, the book still beside it.
                                browse.collectionKey = name.trim().lowercase()
                            } else {
                                browse.collection = name
                                if (!twoPanes) browse.selectedId = null
                            }
                        },
                        onTag = { tag ->
                            browse.tags = setOf(tag)
                            if (inCollection != null) toBooks(book) else if (!twoPanes) browse.selectedId = null
                        },
                        onSearch = { search ->
                            browse.query = search
                            browse.collection = null
                            browse.tags = emptySet()
                            if (inCollection != null) toBooks(book) else if (!twoPanes) browse.selectedId = null
                        },
                    )
                }
                TabPages(pager, Modifier.fillMaxSize(), swipe = !browse.selecting && browse.comicSelecting == null) { page ->
                    if (page == 2) {
                        LibraryComicsTab(comics, covers, browse, koboConnected, here, comicActions) { book, series, onBack -> detail(book, onBack, series) }
                    } else if (page == 1) {
                        LibraryCollectionsTab(books, covers, browse, koboConnected, reading, here, onSend) { book, collection, onBack -> detail(book, onBack, collection) }
                    } else if (twoPanes) {
                        Row(Modifier.fillMaxSize()) {
                            LibraryBrowser(notComics, visible, covers, browse, selected?.id, onSend, onDelete, Modifier.weight(1.45f), reading, here, onMerge, onExport, onDuplicate, askSync, onMarkFinished, onMarkUnread)
                            VerticalDivider()
                            // As the Kobo's and the comics' side panels: the book shown, while books are chosen too (the
                            // bar along the foot says how many are).
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (selected != null) detail(selected, null, null)
                                else EmptyState(Icons.Outlined.AutoStories, tr("Tap a book"), tr("Its details appear here. Hold a book to select several, then hold another to select everything between."))
                            }
                        }
                    } else if (selected != null && !browse.selecting) {
                        TabBackHandler { browse.selectedId = null }
                        detail(selected, { browse.selectedId = null }, null)
                    } else {
                        LibraryBrowser(notComics, visible, covers, browse, null, onSend, onDelete, Modifier.fillMaxSize(), reading, here, onMerge, onExport, onDuplicate, askSync, onMarkFinished, onMarkUnread)
                    }
                }
            } }
            }
    }
}

/** Tighter than usual, so all the buttons for chosen books fit on one line beside them. */
private val SelectionButtonPadding = PaddingValues(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)

@Composable
internal fun JobStrip(p: JobProgress, onShow: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onShow)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                tr("{0}: {1} of {2}", p.title, p.done, p.total) + (p.current?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun EmptyLibrary(koboConnected: Boolean, onAddFiles: () -> Unit, onAddFromKobo: () -> Unit, onNewBook: () -> Unit, onAddSamples: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp)) {
            KoLibMark(Modifier.size(84.dp), glow = true)
            Spacer(Modifier.height(16.dp))
            Text(tr("Your library is empty"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Add books from this device or your Kobo: EPUB, PDF, MOBI, Word and more."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            val fromKobo: @Composable () -> Unit = {
                GoldButton(onClick = onAddFromKobo, modifier = if (phoneLayout()) Modifier.fillMaxWidth().height(48.dp) else Modifier) {
                    Icon(Icons.Outlined.Usb, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (koboConnected) tr("Add from Kobo") else tr("Connect Kobo"))
                }
            }
            val fromFiles: @Composable () -> Unit = {
                OutlinedButton(onClick = onAddFiles, modifier = if (phoneLayout()) Modifier.fillMaxWidth().height(48.dp) else Modifier) {
                    Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Add files"))
                }
            }
            // On a phone the buttons go one above the other, the width of the screen.
            if (phoneLayout()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fromKobo()
                    fromFiles()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fromKobo()
                    fromFiles()
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNewBook) {
                Icon(Icons.AutoMirrored.Outlined.NoteAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Write a new book"))
            }
            if (onAddSamples != null) {
                TextButton(onClick = onAddSamples, modifier = Modifier.testTag("library-add-samples")) {
                    Icon(Icons.Outlined.AutoStories, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Add the sample books"))
                }
                Text(
                    tr("Three classics and a short comic, free to read, to try KoLib with."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LibraryBrowser(
    all: List<LibraryBook>,
    books: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    selectedId: String?,
    onSend: (List<String>) -> Unit,
    onDelete: (List<String>) -> Unit,
    modifier: Modifier,
    reading: (LibraryBook) -> KoboBook? = { null },
    here: (LibraryBook) -> ReadHere? = { null },
    onMerge: (List<String>) -> Unit = {},
    onExport: (List<String>) -> Unit = {},
    onDuplicate: (List<String>) -> Unit = {},
    onSyncReading: ((List<String>) -> Unit)? = null,
    onMarkFinished: (List<String>) -> Unit = {},
    onMarkUnread: (List<String>) -> Unit = {},
) {
    if (browse.selecting) TabBackHandler { browse.stopSelecting() }
    var confirmRemove by remember { mutableStateOf<List<String>?>(null) }
    val onTap: (LibraryBook) -> Unit = { b -> if (browse.selecting) browse.togglePicked(b.id) else browse.selectedId = b.id }
    // Holding a book chooses it, and every book from the one chosen last to it.
    val onHold: (LibraryBook) -> Unit = { b -> browse.holdPick(b.id, books.map { it.id }) }
    Column(modifier.fillMaxHeight()) {
        // On a short screen, the search and filters slide away as the books are scrolled, once the bar and tabs have.
        HidingTop(top = { Box(Modifier.tourPlace("library-search")) { LibraryControls(all, browse, books.size) } }, modifier = Modifier.weight(1f).fillMaxWidth()) {
        // The books, and while some are being chosen, the bar for them along the foot of the screen, its whole width.
        Column(Modifier.fillMaxSize().tourPlace("library-shelf")) {
            if (books.isEmpty()) {
                EmptyState(Icons.Outlined.SearchOff, tr("No matching books"), tr("Try a different search, or clear the filters."))
                return@Column
            }
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
                    itemsIndexed(books, key = { _, b -> b.id }) { index, book ->
                        LibraryGridItem(
                            book, covers, index, books.size,
                            picked = if (browse.selecting) browse.picked.containsKey(book.id) else null,
                            highlighted = book.id == selectedId,
                            sort = browse.sort, reading = reading(book), here = here(book),
                            onTap = { onTap(book) }, onHold = { onHold(book) },
                        )
                    }
                }
            } else {
                LazyColumn(state = browse.listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                    items(books, key = { it.id }) { book ->
                        LibraryListItem(
                            book, covers,
                            picked = if (browse.selecting) browse.picked.containsKey(book.id) else null,
                            highlighted = book.id == selectedId,
                            sort = browse.sort, reading = reading(book), here = here(book),
                            onTap = { onTap(book) }, onHold = { onHold(book) }, onToggle = { browse.togglePicked(book.id) },
                        )
                    }
                }
            }
        }
        }
        if (browse.selecting) AtFoot {
            val chosen = { books.filter { browse.picked.containsKey(it.id) }.map { it.id } }
            SelectionBar(
                count = browse.picked.size,
                onSelectAll = { books.forEach { browse.picked[it.id] = true } },
                onDeselectAll = { browse.deselectAll() },
                onCancel = { browse.stopSelecting() },
                moreTag = "selection-more",
                actions = librarySelectionActions(
                    count = browse.picked.size, chosen = chosen, stop = { browse.stopSelecting() },
                    onSend = onSend, onMarkFinished = onMarkFinished, onMarkUnread = onMarkUnread, onSyncReading = onSyncReading,
                    onExport = onExport, onDuplicate = onDuplicate, onMerge = onMerge, onRemove = { confirmRemove = it },
                ),
            )
        }
    }
    confirmRemove?.let { ids ->
        RemoveBooksDialog(ids, onRemove = { onDelete(ids); confirmRemove = null; browse.stopSelecting() }, onDismiss = { confirmRemove = null })
    }
}

/**
 * What can be done with books chosen in the library, the same in the bar along the foot of a phone or a wider screen:
 * where there isn't room for them all, syncing, sending to a device, saving, duplicating and combining go under More.
 */
@Composable
internal fun librarySelectionActions(
    count: Int,
    chosen: () -> List<String>,
    stop: () -> Unit,
    onSend: (List<String>) -> Unit,
    onMarkFinished: (List<String>) -> Unit,
    onMarkUnread: (List<String>) -> Unit,
    onSyncReading: ((List<String>) -> Unit)?,
    onExport: (List<String>) -> Unit,
    onDuplicate: (List<String>) -> Unit,
    onMerge: (List<String>) -> Unit,
    onRemove: (List<String>) -> Unit,
): List<BarAction> {
    val any = count > 0
    val link = dev.kolib.app.ui.link.LocalLinkActions.current
    return listOfNotNull(
        BarAction(Icons.AutoMirrored.Filled.Send, tr("To Kobo"), { onSend(chosen()) }, enabled = any, main = true, tag = "selection-send", longLabel = tr("Send to Kobo")),
        BarAction(Icons.Outlined.DoneAll, dev.kolib.app.trc("mark", "Read"), { onMarkFinished(chosen()); stop() }, enabled = any, tag = "selection-mark-read", longLabel = tr("Mark as read")),
        BarAction(Icons.Outlined.RemoveDone, dev.kolib.app.trc("mark", "Unread"), { onMarkUnread(chosen()); stop() }, enabled = any, tag = "selection-mark-unread", longLabel = tr("Mark as unread")),
        onSyncReading?.let { sync ->
            BarAction(Icons.Outlined.Sync, tr("Sync"), { sync(chosen()); stop() }, enabled = any, tag = "selection-sync-reading", canWait = true, longLabel = tr("Sync with Kobo"))
        },
        link?.let { BarAction(Icons.AutoMirrored.Outlined.SendToMobile, tr("To device"), { it.sendBooks(chosen()) }, enabled = any, tag = "selection-to-device", canWait = true, longLabel = tr("Send to device")) },
        BarAction(Icons.Outlined.SaveAlt, tr("Save"), { onExport(chosen()) }, enabled = any, tag = "selection-save", canWait = true, longLabel = tr("Save as files")),
        BarAction(Icons.Outlined.ContentCopy, tr("Duplicate"), { onDuplicate(chosen()) }, enabled = any, tag = "selection-duplicate", canWait = true),
        BarAction(Icons.Outlined.Merge, tr("Combine"), { onMerge(chosen()) }, enabled = count >= 2, tag = "selection-combine", canWait = true),
        BarAction(Icons.Outlined.Delete, tr("Remove"), { onRemove(chosen()) }, enabled = any, destructive = true, tag = "selection-remove"),
    )
}

/** A book in the library's grid: its cover, then its title and author, and what it's sorted by when that isn't obvious. */
@Composable
internal fun LibraryGridItem(
    book: LibraryBook,
    covers: LibraryCovers,
    index: Int,
    count: Int,
    picked: Boolean?,
    highlighted: Boolean,
    sort: LibrarySort,
    reading: KoboBook?,
    here: ReadHere?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    padding: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Column(
        Modifier
            .shelfUnderCover(coverTop = padding, index = index, count = count)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (highlighted && picked == null) {
                    Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(padding)
    ) {
        LibraryCover(book, covers, large = false, modifier = Modifier.gridCover(), onKobo = book.koboPath != null, picked = picked, reading = reading, readHere = here)
        ShelfSpace()
        UnderCover {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            book.authorLine?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            sortDetail(book, sort, reading, here)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A book in the library's list: a small cover, title, author and series, and where it's been read. */
@Composable
internal fun LibraryListItem(
    book: LibraryBook,
    covers: LibraryCovers,
    picked: Boolean?,
    highlighted: Boolean,
    sort: LibrarySort,
    reading: KoboBook?,
    here: ReadHere?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onToggle: () -> Unit,
) {
                    ListItem(
                        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(listOfNotNull(book.authorLine, book.meta.seriesLabel).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (picked != null) Checkbox(checked = picked, onCheckedChange = { onToggle() })
                                LibraryCover(book, covers, large = false, modifier = Modifier.width(44.dp))
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                // The series is already on the line under the title.
                                sortDetail(book, sort, reading, here)?.takeIf { it != book.meta.seriesLabel }?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                                here?.takeIf { it.started }?.let { h ->
                                    Text(tr("{0}% read on this device", h.percent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (here?.finished == true) {
                                    Text(tr("Finished on this device"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                reading?.takeIf { it.readStatus == ReadStatus.READING }?.let { k ->
                                    Text(tr("{0}% read on Kobo", k.percentRead), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                when {
                                    book.changedSinceOnKobo -> Text(tr("Changed since sent"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    book.koboPath != null -> Text(tr("On Kobo"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (picked == true || (highlighted && picked == null)) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ),
                        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onHold),
                    )
}

/** Asks before books are removed from the library. */
@Composable
internal fun RemoveBooksDialog(ids: List<String>, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ids.size == 1) tr("Remove this book from the library?") else tr("Remove {0} books from the library?", ids.size)) },
        text = { Text(if (ids.size == 1) tr("The book and your edits are deleted from the library. A copy on the Kobo isn't affected.") else tr("The books and your edits are deleted from the library. Copies on the Kobo aren't affected.")) },
        confirmButton = { TextButton(onClick = onRemove) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** A line under each book that shows what it's sorted by, when that isn't obvious. */
internal fun sortDetail(book: LibraryBook, sort: LibrarySort, reading: KoboBook? = null, here: ReadHere? = null): String? = when (sort) {
    LibrarySort.LAST_READ -> reading?.lastReadMillis?.let { Fmt.date(it) }
    LibrarySort.READ_HERE -> here?.at?.let { Fmt.date(it) }
    LibrarySort.PUBLISHED -> book.meta.published?.take(4)
    LibrarySort.ADDED -> Fmt.date(book.addedAt)
    LibrarySort.EDITED -> Fmt.date(book.modifiedAt)
    LibrarySort.SIZE -> if (book.fileSize < 1_048_576) tr("{0} kB", (book.fileSize + 1023) / 1024) else tr("{0} MB", "%.1f".format(book.fileSize / 1_048_576.0))
    LibrarySort.PAGES -> book.pages?.let { if (it == 1) tr("1 page") else tr("{0} pages", "%,d".format(it)) }
    LibrarySort.RATING -> book.meta.rating?.takeIf { it > 0 }?.let { r -> "★".repeat(r / 2) + if (r % 2 == 1) "½" else "" }
    else -> book.meta.seriesLabel
}

@Composable
private fun LibraryControls(all: List<LibraryBook>, browse: LibraryBrowseState, shown: Int, compact: Boolean = shortScreen()) {
    val collections = remember(all) { all.flatMap { it.collections }.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val tagCounts = remember(all) {
        all.flatMap { b -> b.meta.tags.distinctBy { it.lowercase() } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).map { it.key to it.value }
    }
    var showHelp by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
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
                // Labelled, so it's clear how to send or remove several books at once.
                TextButton(onClick = { browse.selecting = true }) {
                    Icon(Icons.Outlined.Checklist, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Select"))
                }
            }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, tr("Sort")) }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    LibrarySort.entries.forEach { order ->
                        val current = order == browse.sort
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(order.label)
                                    if (current) {
                                        Text(
                                            if (browse.descending) order.descending else order.ascending,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            leadingIcon = { if (current) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                            trailingIcon = {
                                if (current) Icon(if (browse.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, tr("Reverse order"))
                            },
                            onClick = { browse.sortBy(order); sortOpen = false },
                        )
                    }
                }
            }
            // How the books show (covers, and how, or a list) and how many covers to a row, chosen over the shelf.
            DisplayButton(browse.mode, { browse.mode = it }, Modifier.tourPlace("library-display"))
    }
    val chips: @Composable () -> Unit = {
            LibraryFilter.entries.forEach { f ->
                FilterChip(selected = browse.filter == f, onClick = { browse.filter = f }, label = { Text(f.label) })
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            if (collections.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = browse.collection != null,
                        onClick = { open = true },
                        label = { Text(browse.collection ?: tr("Collection")) },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                        DropdownMenuItem(text = { Text(tr("All collections")) }, onClick = { browse.collection = null; open = false })
                        HorizontalDivider()
                        collections.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                leadingIcon = { if (c == browse.collection) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                                onClick = { browse.collection = c; open = false },
                            )
                        }
                    }
                }
            }
            if (tagCounts.isNotEmpty()) {
                FilterChip(
                    selected = browse.tags.isNotEmpty(),
                    onClick = { showTags = true },
                    label = {
                        Text(
                            when (browse.tags.size) {
                                0 -> tr("Tags")
                                1 -> browse.tags.first()
                                else -> if (browse.tagsMatchAll) tr("{0} tags (all)", browse.tags.size) else tr("{0} tags (any)", browse.tags.size)
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
            }
    }
    if (compact) {
        // A short screen (a phone held sideways): the search, the filters (scrolled along) and the buttons on one row.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            search(Modifier.widthIn(min = 180.dp, max = 300.dp).weight(1f, fill = false))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips()
                if (browse.narrowed) TextButton(onClick = { browse.clearFilters() }) { Text(tr("Clear search and filters")) }
            }
            buttons()
        }
    } else Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            search(Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            buttons()
        }
        // Wraps onto a second line rather than hiding filters off the edge.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                (if (shown == all.size) Fmt.plural(all.size, "book") else tr("{0} of {1}", shown, Fmt.plural(all.size, "book"))) + tr(" · sorted by {0}", browse.sortDescription),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
            )
            if (browse.narrowed) TextButton(onClick = { browse.clearFilters() }) { Text(tr("Clear search and filters")) }
        }
    }
    if (showHelp) SearchHelpDialog(onPick = { browse.query = it; showHelp = false }, onDismiss = { showHelp = false })
    if (showTags) {
        TagPickerDialog(
            tags = tagCounts,
            chosen = browse.tags,
            matchAll = browse.tagsMatchAll,
            onApply = { chosen, all ->
                browse.tags = chosen
                browse.tagsMatchAll = all
                showTags = false
            },
            onDismiss = { showTags = false },
        )
    }
}

private val searchExamples = listOf(
    "tag:history" to tr("Books with a tag that contains “history”"),
    "tag:=history" to tr("Books with the exact tag “History”"),
    "author:\"conan doyle\"" to tr("Use quotes to search for a phrase"),
    "tag:history and not tag:greece" to tr("Tagged history, but not Greece"),
    "series:holmes or collection:gothic" to tr("Matches either one"),
    "-kobo:yes" to tr("A minus sign excludes: books that aren't on the Kobo"),
    "(tag:history or tag:philosophy) pages:>300" to tr("Brackets group terms; a space means “and”"),
    "rating:>=4" to tr("Rated four stars or more"),
    "year:<1900" to tr("Published before 1900"),
    "kobo:changed" to tr("Changed since it was sent to the Kobo"),
    "read:reading" to tr("Started but not finished on the Kobo"),
)

private val koboSearchExamples = listOf(
    "tag:history" to tr("Books with a tag that contains “history”"),
    "author:\"conan doyle\"" to tr("Use quotes to search for a phrase"),
    "library:no" to tr("Books that aren't in your library yet"),
    "read:reading" to tr("Started but not finished"),
    "read:finished rating:>=4" to tr("Finished and rated four stars or more"),
    "collection:\"to read\" -tag:horror" to tr("A minus sign excludes: no horror"),
    "series:holmes or series:poirot" to tr("Matches either one"),
    "format:pdf" to tr("Only PDFs (or epub, kepub, cbz and so on)"),
    "year:<1900" to tr("Published before 1900"),
)

@Composable
internal fun SearchHelpDialog(onPick: (String) -> Unit, onDismiss: () -> Unit, forKobo: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forKobo) tr("How to search the Kobo") else tr("How to search the library")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (forKobo) {
                        tr("Searches titles, authors, series, tags, collections and publishers.\n\nTo search one field, put its name and a colon first, like author:tolkien. Fields: title, author, tag, series, collection, publisher, language, isbn, pages, rating, year, read, library, format.\n\nTap an example to try it. Tags come from the book in your library, or from its file on the Kobo.")
                    } else {
                        tr("Searches titles, authors, series, tags, collections and publishers.\n\nTo search one field, put its name and a colon first, like author:tolkien. Fields: title, author, tag, series, collection, publisher, language, isbn, pages, rating, year, kobo, read.\n\nTap an example to try it.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                (if (forKobo) koboSearchExamples else searchExamples).forEach { (q, what) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(q) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Text(q, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
internal fun TagPickerDialog(
    tags: List<Pair<String, Int>>,
    chosen: Set<String>,
    matchAll: Boolean,
    onApply: (Set<String>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(chosen) }
    var all by remember { mutableStateOf(matchAll) }
    var filter by remember { mutableStateOf("") }
    val shown = remember(tags, filter) { tags.filter { it.first.contains(filter.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Filter by tags")) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = all, onClick = { all = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { FittedText(tr("Match all"), style = MaterialTheme.typography.labelLarge, smallest = 0.7f) }
                    SegmentedButton(selected = !all, onClick = { all = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { FittedText(tr("Match any"), style = MaterialTheme.typography.labelLarge, smallest = 0.7f) }
                }
                Spacer(Modifier.height(8.dp))
                if (tags.size > 12) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text(tr("Search tags")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first }) { (tag, count) ->
                        val on = picked.any { it.equals(tag, ignoreCase = true) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (on) picked.filterNot { it.equals(tag, true) }.toSet() else picked + tag }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tag, modifier = Modifier.weight(1f))
                            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(picked, all) }) { Text(tr("Show")) } },
        dismissButton = {
            Row {
                if (picked.isNotEmpty()) TextButton(onClick = { picked = emptySet() }) { Text(tr("Clear")) }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
            }
        },
    )
}

@Composable
fun LibraryDetailPane(
    book: LibraryBook,
    covers: LibraryCovers,
    onBack: (() -> Unit)?,
    koboConnected: Boolean,
    onEdit: () -> Unit,
    onCover: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onCollection: (String) -> Unit,
    onTag: (String) -> Unit,
    /** The book as the Kobo has it (for its reading progress), if it's there. */
    reading: KoboBook? = null,
    readingLive: Boolean = false,
    onRead: () -> Unit = {},
    onEditText: () -> Unit = {},
    /** How far it's been read here, in the app's reader. */
    readHere: Float? = null,
    onSplit: () -> Unit = {},
    onExport: () -> Unit = {},
    onDuplicate: () -> Unit = {},
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
    /** What [onBack] goes back to. */
    backLabel: String = tr("Library"),
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val meta = book.meta
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
            LibraryCover(book, covers, large = true, modifier = Modifier.width(if (dev.kolib.app.ui.components.shortScreen()) 120.dp else if (onBack != null) 200.dp else 160.dp))
            Column(Modifier.weight(1f)) {
                // A tap on the title, author or series copies it; holding it, a search for it here or on the internet.
                DetailText(meta.title, MaterialTheme.typography.headlineSmall, tr("Title copied"), inApp = titleSearch(meta.title), onSearch = onSearch)
                Spacer(Modifier.height(6.dp))
                book.authorLine?.let { DetailText(it, MaterialTheme.typography.titleMedium, tr("Author copied"), inApp = authorSearch(meta.authors), onSearch = onSearch) }
                meta.seriesLabel?.let { label ->
                    val name = meta.series.orEmpty()
                    DetailText(label, SeriesStyle(), tr("Series copied"), color = MaterialTheme.colorScheme.primary, copy = name, inApp = seriesSearch(name), onSearch = onSearch)
                }
                meta.rating?.takeIf { it > 0 }?.let { r ->
                    Row(Modifier.padding(top = 6.dp)) {
                        repeat(5) { i ->
                            Icon(
                                Icons.Filled.Star, null,
                                tint = if (i < (r + 1) / 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        // What can be done with the book, under the cover so the buttons can sit side by side.
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton(onClick = onRead) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        finished -> tr("Read again")
                        readHere != null && readHere > 0.005f -> tr("Read · {0}%", (readHere * 100).roundToInt())
                        else -> tr("Read")
                    }
                )
            }
            if (!finished && onMarkFinished != null) {
                OutlinedButton(onClick = onMarkFinished) {
                    Icon(Icons.Outlined.TaskAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Mark as finished"))
                }
            }
            if ((finished || (readHere ?: 0f) > 0.005f) && onMarkUnread != null) {
                OutlinedButton(onClick = onMarkUnread) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Mark as unread"))
                }
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Edit details"))
            }
            // A comic's pages are pictures in its comic file: there's no text to edit.
            if (!book.isComic) OutlinedButton(onClick = onEditText) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Edit text"))
            }
            OutlinedButton(onClick = onCover) {
                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Change cover"))
            }
            if (!book.isComic) OutlinedButton(onClick = onSplit) {
                Icon(Icons.AutoMirrored.Outlined.CallSplit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Split into books"))
            }
            OutlinedButton(onClick = onExport) {
                Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Save as file"))
            }
            OutlinedButton(onClick = onDuplicate) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Duplicate"))
            }
            FilledTonalButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        book.changedSinceOnKobo -> tr("Update on Kobo")
                        book.koboPath != null -> tr("Send to Kobo again")
                        else -> tr("Send to Kobo")
                    }
                )
            }
            dev.kolib.app.ui.link.LocalLinkActions.current?.let { link ->
                OutlinedButton(onClick = { link.sendBooks(listOf(book.id)) }) {
                    Icon(Icons.AutoMirrored.Outlined.SendToMobile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Send to device"))
                }
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Remove"))
            }
        }
        reading?.let { k ->
            ReadingLine(
                k, live = readingLive, here = readHere,
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

        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) {
        AlertDialog(
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

/** How far the book has been read on the Kobo. */
@Composable
internal fun ReadingLine(
    k: KoboBook,
    live: Boolean,
    /** How far through the book the app's reader is (0 to 1), if it's been started. */
    here: Float? = null,
    /** Moves the app's reader to where the Kobo is, and opens it there. */
    onContinue: (() -> Unit)? = null,
    /** Syncs the book's reading with the Kobo, both ways. */
    onSync: (() -> Unit)? = null,
    /** Its button across the page, as the phone's other buttons are. */
    wideButton: Boolean = false,
) {
    val text = when (k.readStatus) {
        ReadStatus.UNREAD -> tr("Not started on the Kobo")
        ReadStatus.READING -> tr("{0}% read on the Kobo", k.percentRead)
        ReadStatus.FINISHED -> tr("Finished on the Kobo")
    } + (k.lastReadMillis?.let { tr(" · last read {0}", Fmt.dateTime(it)) } ?: "") + if (live) "" else tr(" (when the Kobo was last connected)")
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (k.readStatus == ReadStatus.FINISHED) Icons.Outlined.TaskAlt else Icons.Outlined.AutoStories,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        if (k.readStatus == ReadStatus.READING) {
            LinearProgressIndicator(
                progress = { k.percentRead / 100f },
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp),
                drawStopIndicator = {},
            )
        }
        if (onSync != null) {
            OutlinedButton(
                onClick = onSync,
                modifier = Modifier.padding(top = 8.dp).testTag("sync-reading").then(if (wideButton) Modifier.fillMaxWidth() else Modifier),
            ) {
                Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Sync with the Kobo"))
            }
            Text(
                tr("Your place, bookmarks, highlights and notes. You choose which way each goes."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (onContinue != null && k.readStatus == ReadStatus.READING) {
            // Going back from further on in the app's reader is checked first: that place would be lost.
            var ask by remember { mutableStateOf(false) }
            val ahead = here != null && here * 100f > k.percentRead + 1
            OutlinedButton(
                onClick = { if (ahead) ask = true else onContinue() },
                modifier = Modifier.padding(top = 8.dp).then(if (wideButton) Modifier.fillMaxWidth() else Modifier),
            ) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Continue from the Kobo"))
            }
            if (ask) AlertDialog(
                onDismissRequest = { ask = false },
                title = { Text(tr("Go back to the Kobo's place?")) },
                text = { Text(tr("You're further on here ({0}%) than on the Kobo ({1}%).", ((here ?: 0f) * 100).roundToInt(), k.percentRead)) },
                confirmButton = { TextButton(onClick = { ask = false; onContinue() }) { Text(tr("Go back")) } },
                dismissButton = { TextButton(onClick = { ask = false }) { Text(tr("Cancel")) } },
            )
        }
    }
}

/** Syncing reading with the Kobo: every book that's in both, or ones picked from the library. */
/**
 * Syncing reading with the Kobo: which way where you're up to goes, and which way bookmarks, highlights and notes go
 * (each can be off), kept for next time. [all]: every book in both (with Choose books to pick some); otherwise [count]
 * books chosen.
 */
@Composable
internal fun SyncReadingDialog(count: Int, all: Boolean, onSync: () -> Unit, onChoose: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var choices by remember { mutableStateOf(dev.kolib.app.kobo.SyncChoices.load(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Sync, null) },
        title = { Text(tr("Sync reading with the Kobo")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    when {
                        all && count == 1 -> tr("1 book is in your library and on the Kobo.")
                        all -> tr("{0} books are in your library and on the Kobo.", count)
                        count == 1 -> tr("1 book selected")
                        else -> tr("{0} books selected", count)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(tr("Where you're up to"), style = MaterialTheme.typography.titleSmall)
                RadioChoices(dev.kolib.app.kobo.PlaceWay.entries, choices.place, { it.label() }) { choices = choices.copy(place = it) }
                Text(choices.place.explanation(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(tr("Bookmarks, highlights and notes"), style = MaterialTheme.typography.titleSmall)
                RadioChoices(dev.kolib.app.kobo.MarksWay.entries, choices.marks, { it.label() }) { choices = choices.copy(marks = it) }
                Text(choices.marks.explanation(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("Reading stats stay apart. KoLib's and the Kobo's each have their own tab in Reading stats."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { dev.kolib.app.kobo.SyncChoices.save(context, choices); onSync() },
                enabled = !choices.nothing,
                modifier = Modifier.testTag("sync-reading-all"),
            ) { Text(if (all) tr("Sync all") else tr("Sync")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
                onChoose?.let { choose ->
                    TextButton(onClick = { dev.kolib.app.kobo.SyncChoices.save(context, choices); choose() }) { Text(tr("Choose books")) }
                }
            }
        },
    )
}

/** Books the app's reader opens as text, where the Kobo's place can be found: not comics, nor PDFs read as PDFs. */
internal val LibraryBook.canReadFromKoboPlace: Boolean get() = !isComic && !isPdf

/** Whether a job's kind is about the library (shown on the library screen) rather than the Kobo. */
internal val JobProgress.isLibraryJob: Boolean get() = kind != JobKind.SEND
