package dev.kolib.app.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material.icons.outlined.Explore
import dev.kolib.app.ui.tour.Tours
import dev.kolib.app.ui.tour.tourPlace
import dev.kolib.app.UsbKobo
import dev.kolib.app.tr
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kolib.app.ui.library.ExportDialog
import dev.kolib.app.ui.library.SplitBookDialog
import dev.kolib.app.ui.library.MergeDialog
import dev.kolib.app.ui.library.NewBookDialog
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.Connection
import dev.kolib.app.JobKind
import dev.kolib.app.kobo.SendFormat
import dev.kolib.app.LibraryViewModel
import dev.kolib.app.MainViewModel
import dev.kolib.app.kobo.VolumeCandidate
import dev.kolib.app.log.AppLog
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.cover.CoverEditorScreen
import dev.kolib.app.ui.edit.EditDetailsScreen
import dev.kolib.app.ui.edit.EditSuggestions
import dev.kolib.app.kobo.KoboSender
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Language
import dev.kolib.app.ui.components.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import dev.kolib.app.ui.theme.ThemeChoice
import dev.kolib.app.ui.theme.LocalKoLibStyle
import dev.kolib.app.ui.theme.appBackdrop
import dev.kolib.app.ui.components.KoLibMark
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import dev.kolib.app.ui.library.DuplicatesDialog
import dev.kolib.app.ui.library.JobDialog
import dev.kolib.app.ui.library.SendDialog
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibraryScreen
import dev.kolib.app.ui.reader.PreviewReader
import dev.kolib.app.ui.textedit.TextEditorScreen
import dev.kolib.app.ui.components.HideStatusBar
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import dev.kolib.app.ui.components.FrameBars
import dev.kolib.app.ui.components.LocalFrameBars
import dev.kolib.app.ui.components.phoneLayout

enum class Section { LIBRARY, KOBO, DEVICES }

@Composable
fun AppRoot(
    vm: MainViewModel,
    libraryVm: LibraryViewModel,
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    onTheme: (ThemeChoice) -> Unit = {},
    /** The opening animation, and its sound: on or off, set in the App theme menu. */
    opening: Boolean = true,
    onOpening: ((Boolean) -> Unit)? = null,
    openingSound: Boolean = true,
    onOpeningSound: ((Boolean) -> Unit)? = null,
    openingLight: Boolean = false,
    onOpeningLight: ((Boolean) -> Unit)? = null,
    /** The introduction shown the first time KoLib starts has been gone through. */
    introSeen: Boolean = true,
    onIntroSeen: (Boolean) -> Unit = {},
    /** The text editor's tour has been given: it is the first time the editor is opened. */
    editorTourSeen: Boolean = true,
    onEditorTourSeen: () -> Unit = {},
    /** The tours of the screens given already (see Tours): each is given the first time its screen opens. Null gives none. */
    toursSeen: Set<String>? = null,
    onTourSeen: (String) -> Unit = {},
    /** Every tour is to be given again, from Settings. */
    onShowToursAgain: (() -> Unit)? = null,
    /** No tours are given (the sample books are still added): turned off from a tour, or in Settings. */
    toursOff: Boolean = false,
    onToursOff: (Boolean) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val folderScan by libraryVm.folderScan.collectAsStateWithLifecycle()
    val books by libraryVm.books.collectAsStateWithLifecycle()
    val readingHere by libraryVm.readingHere.books.collectAsStateWithLifecycle()
    val koboCollections by libraryVm.koboCollections.collectAsStateWithLifecycle()
    val job by libraryVm.job.collectAsStateWithLifecycle()
    val libraryMessage by libraryVm.message.collectAsStateWithLifecycle()
    val saving by libraryVm.saving.collectAsStateWithLifecycle()
    val making by libraryVm.making.collectAsStateWithLifecycle()
    val duplicates by libraryVm.duplicates.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf(Section.LIBRARY) }
    /**
     * A screen shown over everything: "log", "backups", "about", "edit:<book id>" (details), "cover:<book id>",
     * "read:<book id>" or "text:<book id>[|<chapter>]" (the text editor).
     */
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var showJob by remember { mutableStateOf(false) }
    var showEject by remember { mutableStateOf(false) }
    var showConnectHelp by remember { mutableStateOf(false) }
    var showLook by remember { mutableStateOf(false) }
    // The reading setup, opened from the introduction (which is come back to after it, at [introAt]) or from the settings.
    var readingSetup by rememberSaveable { mutableStateOf(false) }
    var introAt by rememberSaveable { mutableStateOf(0) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    // Where the AI's window is drawn: a book open in the reader says it's the one the AI is about.
    val aiHost = remember { dev.kolib.app.ui.ai.AiHost() }
    val aiPanel = (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.kolib.app.KoLibApp).aiPanel
    /** Books waiting in the send dialog. */
    var sendIds by remember { mutableStateOf<List<String>?>(null) }
    /** The page offering books to the Kobo's web browser, while it's open. */
    var koboWifi by remember { mutableStateOf<dev.kolib.app.kobo.KoboWifiServer?>(null) }
    /** Making books: the new-book dialog, books to put together, a book to split. */
    var showNewBook by remember { mutableStateOf(false) }
    var mergeIds by remember { mutableStateOf<List<String>?>(null) }
    var splitId by remember { mutableStateOf<String?>(null) }
    /** Books waiting in the save-a-copy dialog, then the format they're saved as while a picker is open. */
    var exportIds by remember { mutableStateOf<List<String>?>(null) }
    var exporting by remember { mutableStateOf<Pair<List<String>, SendFormat?>?>(null) }
    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        val e = exporting
        exporting = null
        if (uri != null && e != null) libraryVm.exportBook(e.first.first(), e.second, uri)
    }
    // The file a book was added as: a PDF, a MOBI... Android adds no ending of its own to these.
    val saveOriginal = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val e = exporting
        exporting = null
        if (uri != null && e != null) libraryVm.exportBook(e.first.first(), e.second, uri)
    }
    val saveMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val e = exporting
        exporting = null
        if (uri != null && e != null) libraryVm.exportBooks(e.first, e.second, uri)
    }
    val snackbar = remember { SnackbarHostState() }
    val listContext = androidx.compose.ui.platform.LocalContext.current
    // Each list sorted and shown as it was last time.
    val browse = remember { BrowseState().also { ListLooks.restore(listContext, kobo = it) } }
    val libraryBrowse = remember { LibraryBrowseState().also { ListLooks.restore(listContext, library = it) } }
    val coversPerRow = remember { dev.kolib.app.ui.components.CoversPerRow().also { ListLooks.restore(listContext, covers = it) } }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { ListLooks.Now(libraryBrowse, browse, coversPerRow) }.collect { ListLooks.save(listContext, it) }
    }
    var localMessage by remember { mutableStateOf<String?>(null) }

    // Making scanned books into text: the dialog to start it, a book to take back to its scan, and what the screens can ask for.
    val ocr = libraryVm.ocr
    var ocrOffer by remember { mutableStateOf<List<String>?>(null) }
    var goBackId by remember { mutableStateOf<String?>(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askToNotify = {
        // The notification says how far it's got; it goes on without one if it's not allowed.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) runCatching { notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    val ocrActions = remember {
        dev.kolib.app.ui.ocr.OcrActions(
            offer = { list -> ocrOffer = list.map { it.id } },
            carryOn = { book, languages ->
                askToNotify()
                ocr.start(listOf(book.id), languages)
            },
            stop = { book -> ocr.stop(book.id) },
            compare = { book -> overlay = "compare:${book.id}" },
            goBack = { book -> goBackId = book.id },
        )
    }
    LaunchedEffect(Unit) {
        ocr.news.collect { n ->
            when (n) {
                is dev.kolib.app.ocr.OcrNews.Done -> {
                    val r = snackbar.showSnackbar(
                        tr("“{0}” converted to text.", n.title),
                        actionLabel = tr("Compare"),
                        withDismissAction = true,
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) overlay = "compare:${n.bookId}"
                }
                is dev.kolib.app.ocr.OcrNews.Failed -> snackbar.showSnackbar(tr("Couldn't convert “{0}” to text: {1}.", n.title, n.reason), withDismissAction = true)
            }
        }
    }

    // KoLib on the user's other devices: pairing, what comes in and goes out, and what to say about it.
    val link = remember { (appContext.applicationContext as dev.kolib.app.KoLibApp).link }
    val paired by link.paired.collectAsStateWithLifecycle()
    val linkNearby by link.nearby.collectAsStateWithLifecycle()
    val pairing by link.pairing.collectAsStateWithLifecycle()
    val linkScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { link.messages.collect { snackbar.showSnackbar(it, withDismissAction = true) } }
    /** Books chosen to send, waiting for which device to send them to. */
    var booksToDevice by remember { mutableStateOf<List<String>?>(null) }
    /** The device "Send books" was chosen for in the Devices section, while the books are chosen in the library. */
    var booksFor by remember { mutableStateOf<String?>(null) }
    var filesFor by remember { mutableStateOf<dev.kolib.app.link.PairedDevice?>(null) }
    val sendBooksTo: (dev.kolib.app.link.PairedDevice, List<String>) -> Unit = { device, ids ->
        link.sendBooks(device, ids)
        dev.kolib.app.link.DeviceShortcuts.used(appContext, device)
        libraryBrowse.stopSelecting()
        localMessage = if (ids.size == 1) tr("Sending “{0}” to {1}…", books.firstOrNull { it.id == ids[0] }?.title.orEmpty(), device.name)
        else tr("Sending {0} books to {1}…", ids.size, device.name)
    }
    val linkActions = remember(paired.isNotEmpty()) {
        if (paired.isEmpty()) null else dev.kolib.app.ui.link.LinkActions { ids ->
            val devices = link.paired.value
            val chosen = booksFor?.let { id -> devices.firstOrNull { it.id == id } }
            booksFor = null
            when {
                ids.isEmpty() -> Unit
                chosen != null -> sendBooksTo(chosen, ids)
                devices.size == 1 -> sendBooksTo(devices[0], ids)
                devices.isNotEmpty() -> booksToDevice = ids
            }
        }
    }
    val sendFilesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val device = filesFor
        filesFor = null
        if (device != null && uris.isNotEmpty()) linkScope.launch {
            val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.mapNotNull { runCatching { dev.kolib.app.link.LinkFiles.fromUri(appContext, it) }.getOrNull() }
            }
            if (files.isEmpty()) localMessage = tr("The file couldn't be read.") else {
                link.sendFiles(device, files)
                dev.kolib.app.link.DeviceShortcuts.used(appContext, device)
            }
        }
    }
    // Asked from the link's notification: books to choose for a paired device.
    val booksForDevice by vm.booksForDevice.collectAsStateWithLifecycle()
    LaunchedEffect(booksForDevice) {
        val id = booksForDevice ?: return@LaunchedEffect
        vm.booksForDevice.value = null
        val d = link.paired.value.firstOrNull { it.id == id } ?: return@LaunchedEffect
        booksFor = d.id
        // Out of anything open over the library, but not a book or an editor, which may have changes to keep.
        if (listOf("edit:", "cover:", "text:", "read:").none { overlay?.startsWith(it) == true }) overlay = null
        section = Section.LIBRARY
        libraryBrowse.tab = 0
        libraryBrowse.selectedId = null
        libraryBrowse.selecting = true
        localMessage = tr("Select books to send to {0}.", d.name)
    }
    val devicesActions = remember {
        dev.kolib.app.ui.link.DevicesActions(
            pair = link::pair,
            reach = link::reach,
            qrOffer = link::qrOffer,
            closeQr = link::closeQr,
            pairFromQr = link::pairFromQr,
            sendBooks = { d ->
                booksFor = d.id
                section = Section.LIBRARY
                libraryBrowse.selectedId = null
                libraryBrowse.selecting = true
                localMessage = tr("Select books to send to {0}.", d.name)
            },
            sendFiles = { d ->
                filesFor = d
                try {
                    sendFilesPicker.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    filesFor = null
                    localMessage = tr("This device has no file picker.")
                }
            },
            sendClipboard = { d ->
                val clip = appContext.getSystemService(android.content.ClipboardManager::class.java)
                val text = runCatching { clip?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(appContext)?.toString() }.getOrNull()
                if (text.isNullOrEmpty()) localMessage = tr("The clipboard is empty.") else {
                    link.sendText(d, text)
                    dev.kolib.app.link.DeviceShortcuts.used(appContext, d)
                }
            },
            syncPlaces = { d -> link.syncPlaces(d) },
            forget = { d -> link.forget(d.id) },
            keepPlacesInStep = { on -> link.changeSettings { it.copy(keepPlacesInStep = on) } },
            lookAlways = { on -> link.changeSettings { it.copy(lookAlways = on) } },
            stayReachable = { on ->
                link.changeSettings { it.copy(stayReachable = on) }
                if (on) {
                    askToNotify()
                    dev.kolib.app.link.LinkService.start(appContext)
                }
            },
            lookAgain = { linkScope.launch { link.lookAround() } },
            koboOverWifi = {
                section = Section.LIBRARY
                libraryBrowse.selectedId = null
                libraryBrowse.selecting = true
                localMessage = tr("Choose books, then tap To Kobo and Over Wi-Fi.")
            },
        )
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onTreePicked(result.data?.data)
    }
    val chooseDrive: (VolumeCandidate?) -> Unit = { candidate ->
        try {
            AppLog.i("Kobo", "Opening Android's drive picker${candidate?.let { " on '${it.description}'" } ?: ""}.")
            treePicker.launch(vm.openTreeIntent(candidate))
        } catch (e: ActivityNotFoundException) {
            AppLog.e("Kobo", "No folder picker available", e)
            localMessage = tr("No folder picker on this device. KoLib needs one to open the Kobo.")
        }
    }
    // With the user's own file manager, if they like: Android asks which app the first time.
    val filePicker = rememberLauncherForActivityResult(dev.kolib.app.ui.components.PickFiles()) { uris ->
        if (uris.isNotEmpty()) libraryVm.importUris(uris)
    }
    val addFiles = {
        try {
            filePicker.launch(dev.kolib.app.convert.BookFormats.mimeTypes)
        } catch (e: ActivityNotFoundException) {
            AppLog.e("Library", "No file picker available", e)
            localMessage = tr("This device has no file picker.")
        }
    }
    // A folder of books: looked through, then its books added.
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) libraryVm.scanFolder(uri)
    }
    val addFolder = {
        try {
            folderPicker.launch(libraryVm.booksFolder)
        } catch (e: ActivityNotFoundException) {
            AppLog.e("Library", "No folder picker available", e)
            localMessage = tr("This device has no folder picker.")
        }
    }
    val addFromKobo = {
        section = Section.KOBO
        browse.deviceTab = 0
        browse.selectedBookId = null
        browse.source = SourceFilter.SIDELOADED
        browse.selecting = true
    }

    // A new job: show its progress (and the library, for books shared from other apps). During the
    // introduction it waits until the introduction is done.
    LaunchedEffect(job?.title, job?.total, job?.startedAt, introSeen) {
        val p = job ?: return@LaunchedEffect
        if (!p.finished && introSeen) {
            showJob = true
            if (p.kind == JobKind.ADD_FILES) {
                section = Section.LIBRARY
                if (listOf("edit:", "cover:", "text:", "read:").none { overlay?.startsWith(it) == true }) overlay = null
            }
        }
    }
    // Finished while hidden: say how it went instead (unless there are books already there to ask about:
    // that question says how it went).
    LaunchedEffect(job?.finished, showJob, introSeen) {
        val p = job ?: return@LaunchedEffect
        if (p.finished && !showJob && introSeen && libraryVm.duplicates.value.isEmpty()) {
            libraryVm.dismissJob()
            val sent = p.kind == JobKind.SEND
            snackbar.showSnackbar(
                listOfNotNull(
                    when {
                        sent && p.succeeded == 1 -> tr("1 book sent.")
                        sent -> tr("{0} books sent.", p.succeeded)
                        p.succeeded == 1 -> tr("1 book added.")
                        else -> tr("{0} books added.", p.succeeded)
                    },
                    p.alreadyThere.takeIf { it > 0 && !sent }?.let { if (it == 1) tr("1 was already in the library.") else tr("{0} were already in the library.", it) },
                    p.failures.size.takeIf { it > 0 }?.let { if (sent) tr("{0} couldn't be sent. The log has the details.", it) else tr("{0} couldn't be added. The log has the details.", it) },
                ).joinToString(" ") + (if (sent && p.succeeded > 0) tr(" Eject the Kobo so it can add them.") else "") +
                    (if (sent && p.coversAfterRestart.isNotEmpty()) tr(" Restart it to see the new covers.") else "")
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            vm.consumeMessage()
            // The Kobo screen and the library say it's safe to unplug at the top already.
            if (it == usbKoboText(UsbKobo.UNMOUNTED) && koboNoticeShown(section, overlay)) return@let
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(libraryMessage) {
        libraryMessage?.let {
            libraryVm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(localMessage) {
        localMessage?.let {
            localMessage = null
            snackbar.showSnackbar(it)
        }
    }

    BackHandler(enabled = overlay == "log" || overlay == "backups" || overlay == "about" || overlay == "stats") { overlay = null }
    BackHandler(enabled = overlay == null && section != Section.LIBRARY) { section = Section.LIBRARY }

    val koboExtras = remember(books, state.koboTags) { KoboExtras(books, state.koboTags) }
    // The Kobo's screens show a book's library cover when it's in the library.
    val koboLibraryCovers = remember(koboExtras) { dev.kolib.app.ui.components.KoboLibraryCovers(koboExtras::coverCopy, libraryVm.covers) }
    val connection = state.connection
    val koboView = remember(state.connection, state.lastSeen, state.removedIds) { state.koboView }
    val liveSession = (connection as? Connection.Connected)?.takeIf { !it.unplugged }?.session
    var narrow by remember { mutableStateOf(false) }
    // Once a Kobo that was open here is unplugged, the library says so for a few seconds.
    val gone = koboGone(connection, state.usbKobo)
    var goneAt by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(gone) {
        if (!gone) goneAt = 0L else if (goneAt == 0L) goneAt = android.os.SystemClock.elapsedRealtime()
    }
    val justUnplugged by androidx.compose.runtime.produceState(false, goneAt) {
        val left = goneAt + UNPLUGGED_NOTICE_MS - android.os.SystemClock.elapsedRealtime()
        value = goneAt != 0L && left > 0
        if (value) {
            kotlinx.coroutines.delay(left)
            value = false
        }
    }
    val linkTransfers by link.transfers.collectAsStateWithLifecycle()
    val noticeForLibrary = libraryNotice(connection, state.usbKobo, state.syncing, state.afterConnectRunning, justUnplugged)

    // The first time KoLib starts, once the introduction is done: the sample books, if the library has none.
    LaunchedEffect(introSeen, toursSeen?.contains(Tours.SAMPLES)) {
        if (introSeen && toursSeen != null && Tours.SAMPLES !in toursSeen) libraryVm.addSamples(onlyIfEmpty = true) { onTourSeen(Tours.SAMPLES) }
    }
    // The tour of the section showing, the first time it's opened, with nothing over it. The library's waits for its books.
    val tourPlaces = remember { dev.kolib.app.ui.tour.TourPlaces() }
    val seenTours = toursSeen
    val koboShowing = koboView != null && (connection is Connection.Connected || connection is Connection.Waiting)
    val sectionTour = if (seenTours == null || toursOff || !introSeen || readingSetup || overlay != null || showLook) null else when (section) {
        Section.LIBRARY -> Tours.LIBRARY.takeIf { books.isNotEmpty() && Tours.SAMPLES in seenTours }
        Section.KOBO -> if (koboShowing) Tours.KOBO else Tours.KOBO_CONNECT
        Section.DEVICES -> Tours.DEVICES
    }?.takeIf { it !in seenTours }

    CompositionLocalProvider(dev.kolib.app.ui.ai.LocalAiHost provides aiHost, dev.kolib.app.ui.components.LocalCoversPerRow provides coversPerRow) {
    Box(
        Modifier
            .fillMaxSize()
            .appBackdrop()
    ) {
        // A book is read on a screen of its own, the reader's (see ReaderActivity): it's opened there, with the library's
        // screen left under it as it was.
        val current = overlay?.takeUnless { it.startsWith("read:") }
        val readerContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(overlay) {
            val read = overlay?.takeIf { it.startsWith("read:") }?.removePrefix("read:") ?: return@LaunchedEffect
            overlay = null
            val book = books.firstOrNull { it.id == read } ?: return@LaunchedEffect
            // The Kobo's copy, if it has a place in it: how far through is counted as the Kobo counts it.
            val spans = book.koboPath?.let { koboView?.snapshot?.booksById?.get(dev.kolib.app.kobo.KoboPaths.contentId(it)) }?.chapterSpans.orEmpty()
            dev.kolib.app.ReaderActivity.open(readerContext, book.id, spans)
        }
        // The text editor, chosen in a reader.
        val fromReader by (readerContext.applicationContext as dev.kolib.app.KoLibApp).openInLibrary.collectAsStateWithLifecycle()
        LaunchedEffect(fromReader) {
            fromReader?.let {
                (readerContext.applicationContext as dev.kolib.app.KoLibApp).openInLibrary.value = null
                overlay = it
            }
        }
        when {
            current == "log" -> LogScreen(onBack = { overlay = null }, onMessage = { localMessage = it })
            current == "kobo-wifi" -> koboWifi.let { server ->
                if (server == null) LaunchedEffect(Unit) { overlay = null }
                else KoboWifiScreen(server, onClose = {
                    koboWifi = null
                    overlay = null
                })
            }
            current == "about" -> AboutScreen(
                onBack = { overlay = null },
                onShowIntro = {
                    overlay = null
                    introAt = 0
                    onIntroSeen(false)
                },
            )
            current == "stats" -> {
                val log by libraryVm.readingLog.books.collectAsStateWithLifecycle()
                val koboInfo = koboView?.info
                val koboDays by androidx.compose.runtime.produceState(emptyMap<String, Int>(), koboInfo, koboView?.readAtMillis) {
                    value = koboInfo?.let { info -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.koboReadingDays(info) } } ?: emptyMap()
                }
                ReadingStatsScreen(
                    books = books,
                    readingHere = readingHere,
                    log = log,
                    kobo = koboView?.snapshot,
                    koboLive = koboView?.live == true,
                    onBack = { overlay = null },
                    koboDays = koboDays,
                )
            }
            current == "backups" -> BackupsScreen(
                backups = state.backups,
                onExport = vm::exportBackup,
                onDelete = vm::deleteBackup,
                onBack = { overlay = null },
            )
            current?.startsWith("edit:") == true -> {
                val book = books.firstOrNull { it.id == current.removePrefix("edit:") }
                if (book == null) {
                    LaunchedEffect(Unit) { overlay = null }
                } else {
                    val suggestions = remember(books, koboCollections) {
                        EditSuggestions.from(books, libraryVm.library.allCollections(), koboCollections)
                    }
                    EditDetailsScreen(
                        book = book,
                        covers = libraryVm.covers,
                        suggestions = suggestions,
                        saving = saving,
                        onSave = { meta, collections -> libraryVm.saveDetails(book.id, meta, collections) { ok -> if (ok) overlay = null } },
                        onClose = { overlay = null },
                    )
                }
            }
            current?.startsWith("compare:") == true -> {
                val book = books.firstOrNull { it.id == current.removePrefix("compare:") }
                if (book == null) {
                    LaunchedEffect(Unit) { overlay = null }
                } else {
                    dev.kolib.app.ui.ocr.CompareScreen(
                        book = book,
                        library = libraryVm.library,
                        onClose = { overlay = null },
                        onGoBack = if (book.isRecognised) ({ goBackId = book.id }) else null,
                    )
                }
            }
            current?.startsWith("text:") == true -> {
                val id = current.removePrefix("text:").substringBefore('|')
                val path = current.substringAfter('|', "").ifEmpty { null }
                // (A book just made is in the library before this screen's list catches up.)
                val book = books.firstOrNull { it.id == id } ?: libraryVm.library.book(id)
                val editor = remember(id) { if (book != null) libraryVm.textEditor(id, path) else null }
                if (book == null || editor == null) {
                    LaunchedEffect(Unit) { overlay = null }
                } else {
                    var preview by remember(id) { mutableStateOf<String?>(null) }
                    // The editor has the screen to itself. The preview reader shows the status bar again
                    // when it closes, so it's hidden again then.
                    HideStatusBar(enabled = preview == null)
                    Box(Modifier.fillMaxSize()) {
                        TextEditorScreen(
                            state = editor,
                            onClose = {
                                libraryVm.closeTextEditor()
                                overlay = null
                            },
                            onRead = { from -> preview = from ?: "" },
                            tour = !editorTourSeen && !toursOff,
                            onTourDone = onEditorTourSeen,
                            onToursOff = if (toursSeen != null) ({ onToursOff(true) }) else null,
                        )
                        preview?.let { from ->
                            PreviewReader(
                                files = editor.edit,
                                structure = editor.structure,
                                title = book.title,
                                language = book.meta.language,
                                startPath = from.ifEmpty { null },
                                settings = libraryVm.readerSettings,
                                onClose = { preview = null },
                            )
                        }
                    }
                }
            }
            current?.startsWith("cover:") == true -> {
                val book = books.firstOrNull { it.id == current.removePrefix("cover:") }
                if (book == null) {
                    LaunchedEffect(Unit) { overlay = null }
                } else {
                    CoverEditorScreen(
                        book = book,
                        loadOriginal = { libraryVm.originalCover(book.id) },
                        saving = saving,
                        onSave = { cover -> libraryVm.saveCover(book.id, cover) { ok -> if (ok) overlay = null } },
                        onClose = { overlay = null },
                        loadFirstCover = { libraryVm.savedOriginalCover(book.id) },
                        onRestoreFirstCover = { libraryVm.restoreOriginalCover(book.id) { ok -> if (ok) overlay = null } },
                    )
                }
            }
            else -> CompositionLocalProvider(dev.kolib.app.ui.tour.LocalTourPlaces provides tourPlaces.takeIf { sectionTour != null }) { AppFrame(
                section = section,
                onSection = { section = it },
                koboConnected = liveSession != null,
                onNarrow = { narrow = it },
                onLook = { showLook = true },
                devicesHere = paired.any { d -> linkNearby.any { it.id == d.id } },
            ) {
                CompositionLocalProvider(dev.kolib.app.ui.link.LocalLinkActions provides linkActions, dev.kolib.app.ui.ai.LocalOpenAiChat provides { aiPanel.toggle() }) {
                when (section) {
                    Section.DEVICES -> dev.kolib.app.ui.link.DevicesScreen(link, devicesActions, onEject = if (liveSession != null) ({ showEject = true }) else null)
                    Section.LIBRARY -> CompositionLocalProvider(dev.kolib.app.ui.ocr.LocalOcrActions provides ocrActions) { LibraryScreen(
                        onAddSamples = { libraryVm.addSamples() },
                        books = books,
                        covers = libraryVm.covers,
                        browse = libraryBrowse,
                        job = job,
                        koboConnected = liveSession != null,
                        onAddFiles = addFiles,
                        onAddFromKobo = addFromKobo,
                        onAddFolder = addFolder,
                        onShowJob = { showJob = true },
                        onEditDetails = { overlay = "edit:$it" },
                        onChangeCover = { overlay = "cover:$it" },
                        onRead = {
                            // A book opens at the tablet's own brightness (unless the reader's is to be kept).
                            libraryVm.readerSettings.startReading()
                            overlay = "read:$it"
                        },
                        onEditText = { overlay = "text:$it" },
                        readingHere = readingHere,
                        onDelete = { ids ->
                            if (libraryBrowse.selectedId in ids) libraryBrowse.selectedId = null
                            if (libraryBrowse.collectionBookId in ids) libraryBrowse.collectionBookId = null
                            libraryVm.delete(ids)
                        },
                        onSend = { ids -> sendIds = ids },
                        onNewBook = { showNewBook = true },
                        onAbout = { overlay = "about" },
                        onStats = { overlay = "stats" },
                        onEject = if (liveSession != null) ({ showEject = true }) else null,
                        onMerge = { ids -> mergeIds = ids },
                        onSplit = { id -> splitId = id },
                        onExport = { ids -> exportIds = ids },
                        onDuplicate = { ids ->
                            libraryVm.duplicate(ids) { made ->
                                libraryBrowse.stopSelecting()
                                if (made.size == 1) libraryBrowse.selectedId = made.first()
                            }
                        },
                        onKobo = koboView?.snapshot?.booksById.orEmpty(),
                        koboLive = koboView?.live == true,
                        onTheme = { showLook = true },
                        onMarkFinished = { ids -> libraryVm.markFinished(ids) },
                        onMarkUnread = { ids -> libraryVm.markUnread(ids) },
                        onContinueFromKobo = { id, kobo ->
                            libraryVm.continueFromKobo(id, kobo) {
                                libraryVm.readerSettings.startReading()
                                overlay = "read:$id"
                            }
                        },
                        onSyncReading = { ids -> vm.syncReading(ids) },
                        koboNotice = noticeForLibrary,
                        transfers = linkTransfers,
                        onGroupComics = { ids, name -> libraryVm.groupComics(ids, name) },
                        onTakeOutOfSeries = { ids -> libraryVm.takeComicsOutOfSeries(ids) },
                    ) }
                    Section.KOBO -> CompositionLocalProvider(dev.kolib.app.ui.components.LocalKoboLibraryCovers provides koboLibraryCovers) { when {
                        connection is Connection.Connected && koboView != null -> DeviceScreen(
                            view = koboView,
                            unplugged = connection.unplugged,
                            state = state,
                            browse = browse,
                            onReconnect = vm::reconnect,
                            onWriteCheck = vm::runWriteCheck,
                            onBackupNow = vm::backupNow,
                            onForget = vm::forgetKobo,
                            onShowKobo = vm::showKobo,
                            onForgetSavedKobo = vm::forgetSavedKobo,
                            onOpenBackups = {
                                vm.refreshBackups()
                                overlay = "backups"
                            },
                            onOpenLog = { overlay = "log" },
                            extras = koboExtras,
                            onAddToLibrary = { chosen -> libraryVm.importFromKobo(connection.session, chosen) },
                            onDeleteFromKobo = vm::deleteFromKobo,
                            sending = job?.let { it.kind == JobKind.SEND && !it.finished } == true,
                            onSettings = { showLook = true },
                            onEject = { showEject = true },
                        )
                        connection is Connection.Waiting && koboView != null -> DeviceScreen(
                            view = koboView,
                            unplugged = false,
                            state = state,
                            browse = browse,
                            onReconnect = vm::reconnect,
                            onWriteCheck = vm::runWriteCheck,
                            onBackupNow = vm::backupNow,
                            onForget = vm::forgetKobo,
                            onShowKobo = vm::showKobo,
                            onForgetSavedKobo = vm::forgetSavedKobo,
                            onOpenBackups = {
                                vm.refreshBackups()
                                overlay = "backups"
                            },
                            onOpenLog = { overlay = "log" },
                            extras = koboExtras,
                            onHowToConnect = { showConnectHelp = true },
                            onSettings = { showLook = true },
                        )
                        else -> ConnectScreen(
                            connection = connection,
                            backupCount = state.backups.size,
                            onChooseDrive = chooseDrive,
                            onRetry = vm::retry,
                            onOpenBackups = {
                                vm.refreshBackups()
                                overlay = "backups"
                            },
                            onOpenLog = { overlay = "log" },
                            onSettings = { showLook = true },
                        )
                    } }
                }
                }
            } }
        }
        // The AI's window (or its bubble), over whatever screen is showing: about the book open, or the library.
        val libraryPlace = remember(books, readingHere) { dev.kolib.app.ui.ai.ChatPlace.Library(books, readingHere) { aiHost.library } }
        dev.kolib.app.ui.ai.AiLayer(aiHost.book ?: libraryPlace)
        sectionTour?.let { name ->
            androidx.compose.runtime.key(name) {
                val stops = remember(name) {
                    when (name) {
                        Tours.LIBRARY -> Tours.library()
                        Tours.KOBO -> Tours.kobo()
                        Tours.KOBO_CONNECT -> Tours.koboConnect()
                        else -> Tours.devices()
                    }
                }
                dev.kolib.app.ui.tour.Tour(stops, tourPlaces, onDone = { onTourSeen(name) }, onTurnOff = { onToursOff(true) })
            }
        }
        // Got ready as KoLib opens, if asked to be: the model opened and the library read ahead, in the background.
        val aiApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.kolib.app.KoLibApp
        val aiSettings by aiApp.ai.settings.collectAsStateWithLifecycle()
        val inLibrary = aiHost.book == null
        LaunchedEffect(aiSettings.preload, aiSettings.source, inLibrary, books.isNotEmpty()) {
            if (aiSettings.preload && inLibrary && books.isNotEmpty() && aiApp.ai.ready()) {
                kotlinx.coroutines.delay(2_500)
                aiApp.chat.warm(aiApp.ai, libraryPlace)
            }
        }

        val p = job
        // Files added that the library has already: once adding is done, ask what to do with them.
        val askAboutDuplicates = duplicates.isNotEmpty() && (p == null || p.finished)
        if (askAboutDuplicates) {
            DuplicatesDialog(
                held = duplicates,
                existing = { id -> books.firstOrNull { it.id == id } },
                covers = libraryVm.covers,
                job = p?.takeIf { it.kind == JobKind.ADD_FILES },
                onDone = { choices ->
                    showJob = false
                    libraryVm.resolveDuplicates(choices)
                },
            )
        } else if (showJob && p != null) {
            JobDialog(
                p,
                onHide = { showJob = false },
                onStop = libraryVm::cancelJob,
                onDone = {
                    showJob = false
                    libraryVm.dismissJob()
                },
                onOpenLibrary = if (p.fromKobo && section != Section.LIBRARY) {
                    {
                        showJob = false
                        libraryVm.dismissJob()
                        overlay = null
                        section = Section.LIBRARY
                    }
                } else null,
                onHowToEject = {
                    showJob = false
                    libraryVm.dismissJob()
                    showEject = true
                },
                onSendAsEpub = liveSession?.let { session ->
                    { ids: List<String> ->
                        showJob = false
                        libraryVm.dismissJob()
                        libraryVm.sendToKobo(session, ids, SendFormat.EPUB)
                    }
                },
            )
        }

        sendIds?.let { ids ->
            val chosen = books.filter { it.id in ids }
            if (chosen.isEmpty()) {
                sendIds = null
            } else {
                SendDialog(
                    books = chosen,
                    connected = liveSession != null,
                    freeBytes = liveSession?.info?.freeBytes,
                    initialFormat = libraryVm.sendFormat,
                    initialCovers = libraryVm.coverFitting,
                    initialPdfs = libraryVm.sendPdfs,
                    onSend = { format, covers, pdfs ->
                        liveSession?.let { libraryVm.sendToKobo(it, ids, format, covers, pdfs) }
                        sendIds = null
                        libraryBrowse.stopSelecting()
                    },
                    onConnect = {
                        sendIds = null
                        section = Section.KOBO
                    },
                    onDismiss = { sendIds = null },
                    pathOnKobo = { b -> libraryVm.pathOnKobo(b, koboView?.snapshot) },
                    onMakeText = { scans ->
                        sendIds = null
                        ocrOffer = scans.map { it.id }
                    },
                    onWifi = { format, covers, pdfs ->
                        sendIds = null
                        libraryBrowse.stopSelecting()
                        koboWifi = libraryVm.koboOverWifi(ids, format, covers, pdfs)
                        overlay = "kobo-wifi"
                    },
                    initialSyncReading = libraryVm.syncReadingOnSend,
                    onSyncReading = { on ->
                        libraryVm.syncReadingOnSend = on
                        if (on) vm.queueReadingSync(chosen.filter { !it.isComic && !it.isPdf }.map { it.id })
                    },
                )
            }
        }

        if (showNewBook) {
            NewBookDialog(
                onMake = { title, author ->
                    showNewBook = false
                    libraryVm.createBook(title, author) { id ->
                        libraryBrowse.selectedId = id
                        overlay = "text:$id"
                    }
                },
                onDismiss = { showNewBook = false },
            )
        }
        mergeIds?.let { ids ->
            val chosen = ids.mapNotNull { id -> books.firstOrNull { it.id == id } }
            if (chosen.size < 2) {
                LaunchedEffect(Unit) { mergeIds = null }
            } else {
                MergeDialog(
                    books = chosen,
                    covers = libraryVm.covers,
                    onMerge = { order, title, author, coverFrom ->
                        mergeIds = null
                        libraryVm.mergeBooks(order, title, author, coverFrom) { id ->
                            libraryBrowse.stopSelecting()
                            libraryBrowse.selectedId = id
                        }
                    },
                    onDismiss = { mergeIds = null },
                )
            }
        }
        splitId?.let { id ->
            val book = books.firstOrNull { it.id == id }
            if (book == null) {
                LaunchedEffect(Unit) { splitId = null }
            } else {
                SplitBookDialog(
                    book = book,
                    loadPages = { libraryVm.pagesOf(id) },
                    onSplit = { cuts, titles, series, shared, covers ->
                        splitId = null
                        libraryVm.splitBook(id, cuts, titles, series, shared, covers) { made -> made.firstOrNull()?.let { libraryBrowse.selectedId = it } }
                    },
                    onDismiss = { splitId = null },
                )
            }
        }
        exportIds?.let { ids ->
            val chosen = books.filter { it.id in ids }
            ExportDialog(
                count = ids.size,
                title = books.firstOrNull { it.id == ids.first() }?.title,
                initial = libraryVm.sendFormat,
                addedAsOther = chosen.count { it.sourceFormat != null },
                addedAsLabel = chosen.singleOrNull()?.let { b -> libraryVm.addedAsLabel(b) },
                onSave = { format ->
                    exportIds = null
                    exporting = ids to format
                    try {
                        val name = libraryVm.exportName(ids.first(), format)
                        when {
                            ids.size > 1 -> saveMany.launch(null)
                            name.endsWith(".epub") -> saveOne.launch(name)
                            else -> saveOriginal.launch(name)
                        }
                    } catch (e: ActivityNotFoundException) {
                        exporting = null
                        localMessage = tr("This device has no file picker.")
                    }
                },
                onDismiss = { exportIds = null },
            )
        }
        // Putting books together can take a few seconds: say so, over everything. So can checking and
        // saving to the Kobo's database, which must be done before the Kobo is ejected, or what was to be
        // saved (collections of books just sent, say) waits until next time. The Kobo screen and the
        // library have their own notices for the Kobo.
        val elsewhere = !koboNoticeShown(section, overlay)
        // From plugging the Kobo in to having read it, there's a wait: say what's happening, wherever you are.
        val koboOpening = when {
            !elsewhere -> null
            connection is Connection.Working || state.usbKobo == UsbKobo.OPENING -> tr("Opening the Kobo… Don't unplug it.")
            state.usbKobo == UsbKobo.SLOW -> tr("Waiting for Android to open the Kobo…")
            else -> null
        }
        val busy = making?.let { "$it…" }
            ?: koboOpening
            ?: tr("Updating collections on the Kobo… Don't eject it yet.").takeIf { state.syncing && elsewhere }
            ?: tr("Checking the Kobo… Don't eject it yet.").takeIf { state.afterConnectRunning && elsewhere }
        busy?.let { what ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inverseOnSurface)
                    Spacer(Modifier.width(10.dp))
                    Text(what, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        ocrOffer?.let { ids ->
            val chosen = books.filter { it.id in ids && it.isScan }
            if (chosen.isEmpty()) {
                LaunchedEffect(Unit) { ocrOffer = null }
            } else {
                dev.kolib.app.ui.ocr.OcrDialog(
                    books = chosen,
                    onStart = { languages ->
                        ocrOffer = null
                        askToNotify()
                        ocr.start(chosen.map { it.id }, languages)
                    },
                    onDismiss = { ocrOffer = null },
                )
            }
        }
        goBackId?.let { id ->
            val book = books.firstOrNull { it.id == id }
            if (book == null) {
                LaunchedEffect(Unit) { goBackId = null }
            } else {
                dev.kolib.app.ui.ocr.GoBackToScanDialog(
                    book = book,
                    onGoBack = {
                        goBackId = null
                        if (overlay == "compare:$id") overlay = null
                        libraryVm.goBackToScan(id)
                    },
                    onDismiss = { goBackId = null },
                )
            }
        }

        // Another app (a file manager) may have opened over KoLib as the Kobo connected: how to stop it.
        val takenOverBy by vm.takenOverBy.collectAsStateWithLifecycle()
        if (takenOverBy.isNotEmpty() && introSeen) {
            KoboTakeoverDialog(takenOverBy) { mute -> if (mute) vm.muteTakeover() else vm.takenOverBy.value = emptyList() }
        }
        if (showEject) {
            EjectDialog(
                onDismiss = { showEject = false },
                busy = job?.let { it.kind == JobKind.SEND && !it.finished } == true,
                savingDatabase = state.syncing || state.afterConnectRunning,
                treeUri = liveSession?.treeUri,
            )
        }
        if (showLook) LookDialog(
            current = theme, onPick = onTheme, onDismiss = { showLook = false },
            opening = opening, onOpening = onOpening, openingSound = openingSound, onOpeningSound = onOpeningSound,
            openingLight = openingLight, onOpeningLight = onOpeningLight,
            onLanguage = {
                showLook = false
                showLanguage = true
            },
            booksFolder = androidx.compose.ui.platform.LocalContext.current.let { ctx -> remember(showLook) { libraryVm.booksFolder?.let { dev.kolib.app.library.BookFolders.nameOf(ctx.contentResolver, it) } } },
            onChangeFolder = {
                showLook = false
                addFolder()
            },
            onRescanFolder = {
                showLook = false
                libraryVm.booksFolder?.let { libraryVm.scanFolder(it) }
            },
            // Not over a book that's open: the setup opens its own.
            onReadingSetup = if (overlay == null) ({
                showLook = false
                readingSetup = true
            }) else null,
            onAi = { showAi = true },
            comicsTab = libraryBrowse.comicsTab,
            onComicsTab = { libraryBrowse.comicsTab = it },
            seriesInPlace = libraryBrowse.seriesInPlace,
            onSeriesInPlace = { libraryBrowse.openSeriesInPlace(it) },
            coversPerRow = coversPerRow,
            tour = !toursOff && toursSeen?.let { Tours.SETTINGS !in it } == true,
            onTourDone = { onTourSeen(Tours.SETTINGS) },
            onShowTours = onShowToursAgain?.let { again -> { showLook = false; again() } },
            toursOn = if (toursSeen != null) !toursOff else null,
            onToursOn = { on -> onToursOff(!on) },
        )
        if (showAi) dev.kolib.app.ui.ai.AiSetupDialog(onDismiss = { showAi = false })
        // A newer KoLib, when there is one (or what a look for one found, when it was asked for). It's looked for
        // a moment after the app starts, once a day, unless that's turned off.
        val updater = (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.kolib.app.KoLibApp).updater
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2_500)
            updater.lookIfDue()
        }
        dev.kolib.app.ui.update.UpdateWindow(updater)
        if (showLanguage) dev.kolib.app.ui.library.LanguageDialog(
            onDismiss = { showLanguage = false },
            busy = job?.finished == false || state.syncing,
        )
        // A folder chosen from Add books: what's in it, to add (the introduction shows this itself).
        folderScan?.takeIf { introSeen && it !is dev.kolib.app.library.FolderScan.Adding }?.let { scan ->
            dev.kolib.app.ui.library.FolderScanDialog(
                scan = scan,
                onAdd = {
                    libraryVm.addFoundBooks()
                    libraryVm.forgetFolderScan()
                },
                onChooseAnother = addFolder,
                onDismiss = libraryVm::forgetFolderScan,
            )
        }
        if (showConnectHelp) ConnectHelpDialog(connection, chooseDrive, onDismiss = { showConnectHelp = false })
        booksToDevice?.let { ids ->
            dev.kolib.app.ui.link.ChooseDeviceDialog(
                devices = paired,
                here = linkNearby.map { it.id }.toSet(),
                what = if (ids.size == 1) tr("“{0}”", books.firstOrNull { it.id == ids[0] }?.title.orEmpty()) else tr("{0} books", ids.size),
                onPick = { d ->
                    booksToDevice = null
                    sendBooksTo(d, ids)
                },
                onDismiss = { booksToDevice = null },
            )
        }
        pairing?.let { p -> dev.kolib.app.ui.link.PairingDialog(p, link::answer) }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // On a phone: above the bar along the bottom, and in the library above its Add books button.
                .padding(
                    start = 16.dp, end = 16.dp, top = 16.dp,
                    bottom = when {
                        !narrow || overlay != null -> 16.dp
                        section == Section.LIBRARY -> 168.dp
                        else -> 96.dp
                    },
                ),
        )

        if (readingSetup) {
            // The reading setup has the screen to itself. From the introduction, that's gone back to after it: on to
            // its last page, or to the page that offered the setup if it was backed out of.
            dev.kolib.app.ui.setup.ReadingSetup(libraryVm.readerSettings) { finished ->
                readingSetup = false
                if (!introSeen) introAt = if (finished) dev.kolib.app.ui.onboarding.IntroPages.done else dev.kolib.app.ui.onboarding.IntroPages.reading
            }
        } else if (!introSeen) {
            // The first time KoLib starts: the introduction, over everything.
            dev.kolib.app.ui.onboarding.Onboarding(
                connection = connection,
                onChooseDrive = chooseDrive,
                onRetry = vm::retry,
                scan = folderScan,
                onChooseFolder = addFolder,
                onAddFound = libraryVm::addFoundBooks,
                onDone = {
                    libraryVm.forgetFolderScan()
                    section = Section.LIBRARY
                    introAt = 0
                    onIntroSeen(true)
                },
                onSetUpReading = { readingSetup = true },
                startAt = introAt,
            )
        }
    }
    }
}

/** The Library, Kobo and Devices sections: a rail down the side on tablets, a bar along the bottom on phones. */
@Composable
internal fun AppFrame(
    section: Section,
    onSection: (Section) -> Unit,
    koboConnected: Boolean,
    onNarrow: (Boolean) -> Unit,
    onLook: (() -> Unit)? = null,
    /** A paired device is on the network now. */
    devicesHere: Boolean = false,
    content: @Composable () -> Unit,
) {
    val devicesIcon: @Composable () -> Unit = {
        BadgedBox(badge = { if (devicesHere) Badge(containerColor = MaterialTheme.colorScheme.tertiary) }) {
            Icon(if (section == Section.DEVICES) Icons.Filled.Devices else Icons.Outlined.Devices, null)
        }
    }
    val libraryIcon: @Composable () -> Unit = {
        Icon(if (section == Section.LIBRARY) Icons.Filled.LocalLibrary else Icons.Outlined.LocalLibrary, null)
    }
    val koboIcon: @Composable () -> Unit = {
        BadgedBox(badge = { if (koboConnected) Badge(containerColor = MaterialTheme.colorScheme.tertiary) }) {
            Icon(if (section == Section.KOBO) Icons.Filled.Usb else Icons.Outlined.Usb, null)
        }
    }
    Box(Modifier.fillMaxSize()) {
        val wide = !phoneLayout()
        LaunchedEffect(wide) { onNarrow(!wide) }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                // The rail lies on the screen's backdrop, set off from the page by a fine gold line.
                // On a short screen (a phone held sideways), without KoLib's mark at the top, so Settings fits at the foot.
                val short = dev.kolib.app.ui.components.shortScreen()
                NavigationRail(
                    containerColor = Color.Transparent,
                    header = if (short) null else ({
                        Spacer(Modifier.height(10.dp))
                        KoLibMark(Modifier.size(40.dp).semantics { contentDescription = tr("KoLib") }, glow = true)
                        Spacer(Modifier.height(18.dp))
                    }),
                ) {
                    val itemColors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    NavigationRailItem(
                        selected = section == Section.LIBRARY,
                        onClick = { onSection(Section.LIBRARY) },
                        modifier = Modifier.tourPlace("nav-library"),
                        icon = libraryIcon,
                        label = { Text(tr("Library")) },
                        colors = itemColors,
                    )
                    NavigationRailItem(
                        selected = section == Section.KOBO,
                        onClick = { onSection(Section.KOBO) },
                        modifier = Modifier.tourPlace("nav-kobo"),
                        icon = koboIcon,
                        label = { Text(tr("Kobo")) },
                        colors = itemColors,
                    )
                    NavigationRailItem(
                        selected = section == Section.DEVICES,
                        onClick = { onSection(Section.DEVICES) },
                        modifier = Modifier.tourPlace("nav-devices"),
                        icon = devicesIcon,
                        label = { Text(tr("Devices")) },
                        colors = itemColors,
                    )
                    if (onLook != null) {
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = onLook,
                            modifier = Modifier.tourPlace("nav-settings"),
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = { dev.kolib.app.ui.components.FittedText(tr("Settings"), style = MaterialTheme.typography.labelMedium, smallest = 0.7f) },
                            colors = itemColors,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                VerticalDivider(color = LocalKoLibStyle.current.hairline)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                ) { content() }
            }
        } else {
            // On a phone the theme is in the library's menu, so the bar along the bottom holds only the
            // sections. A screen can hide the bar to put its own there (see HideBottomBar).
            val bars = remember { FrameBars() }
            val showBar = bars.hiders == 0
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
                ) {
                    CompositionLocalProvider(LocalFrameBars provides bars) { content() }
                }
                AnimatedVisibility(
                    visible = showBar,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column {
                        HorizontalDivider(color = LocalKoLibStyle.current.hairline)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            NavigationBarItem(
                                selected = section == Section.LIBRARY,
                                onClick = { onSection(Section.LIBRARY) },
                                modifier = Modifier.tourPlace("nav-library"),
                                icon = libraryIcon,
                                label = { Text(tr("Library")) },
                                colors = itemColors,
                            )
                            NavigationBarItem(
                                selected = section == Section.KOBO,
                                onClick = { onSection(Section.KOBO) },
                                modifier = Modifier.tourPlace("nav-kobo"),
                                icon = koboIcon,
                                label = { Text(tr("Kobo")) },
                                colors = itemColors,
                            )
                            NavigationBarItem(
                                selected = section == Section.DEVICES,
                                onClick = { onSection(Section.DEVICES) },
                                modifier = Modifier.tourPlace("nav-devices"),
                                icon = devicesIcon,
                                label = { Text(tr("Devices")) },
                                colors = itemColors,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Choosing how the app looks, and whether its opening animation plays (with or without its sound). */
@Composable
fun LookDialog(
    current: ThemeChoice,
    onPick: (ThemeChoice) -> Unit,
    onDismiss: () -> Unit,
    opening: Boolean = true,
    onOpening: ((Boolean) -> Unit)? = null,
    openingSound: Boolean = true,
    onOpeningSound: ((Boolean) -> Unit)? = null,
    openingLight: Boolean = false,
    onOpeningLight: ((Boolean) -> Unit)? = null,
    /** Opens the choice of language. */
    onLanguage: (() -> Unit)? = null,
    /** The folder of books chosen to add books from, by name; null if none has been. */
    booksFolder: String? = null,
    /** Chooses another folder of books (and looks through it for books to add). */
    onChangeFolder: (() -> Unit)? = null,
    /** Looks through the folder of books again, for books added to it since. */
    onRescanFolder: (() -> Unit)? = null,
    /** Opens the reading setup: the reader's main choices, on an example book. */
    onReadingSetup: (() -> Unit)? = null,
    /** Opens the AI settings: the model that explains words, summarises and answers questions. */
    onAi: (() -> Unit)? = null,
    /** Comics have a tab of their own in the library (null: not offered here). */
    comicsTab: Boolean? = null,
    onComicsTab: (Boolean) -> Unit = {},
    /** On a wide screen, a series opens in place of the comics' shelf rather than beside it. */
    seriesInPlace: Boolean = false,
    onSeriesInPlace: (Boolean) -> Unit = {},
    /** How many covers a shelf's row holds (null: not offered here). */
    coversPerRow: dev.kolib.app.ui.components.CoversPerRow? = null,
    /** The tour of Settings is given: it's the first time they're opened. */
    tour: Boolean = false,
    onTourDone: () -> Unit = {},
    /** Gives every tour again, each the next time its screen opens (null: not offered here). */
    onShowTours: (() -> Unit)? = null,
    /** Tours are given, the first time each screen opens (null: not offered here). */
    toursOn: Boolean? = null,
    onToursOn: (Boolean) -> Unit = {},
) {
    val scroll = rememberScrollState()
    val places = remember { dev.kolib.app.ui.tour.TourPlaces() }
    var touring by remember { mutableStateOf(tour) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Settings, null) },
        title = { Text(tr("Settings")) },
        text = {
            CompositionLocalProvider(dev.kolib.app.ui.tour.LocalTourPlaces provides places.takeIf { touring }) {
            Column(Modifier.tourPlace("settings-view").verticalScroll(scroll)) {
                if (onLanguage != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val language = remember { dev.kolib.app.AppLanguage.nativeName(dev.kolib.app.AppLanguage.current(context)) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onLanguage)
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .tourPlace("settings-language"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Language, null, Modifier.padding(horizontal = 10.dp))
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Language"), style = MaterialTheme.typography.titleSmall)
                            Text(language, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                if (onChangeFolder != null) {
                    Column(Modifier.tourPlace("settings-folder")) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, null, Modifier.padding(horizontal = 10.dp))
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr("Books folder"), style = MaterialTheme.typography.titleSmall)
                                Text(booksFolder ?: tr("None chosen yet"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onChangeFolder) { Text(if (booksFolder == null) tr("Choose") else tr("Change")) }
                            if (booksFolder != null && onRescanFolder != null) TextButton(onClick = onRescanFolder) { Text(tr("Look for new books")) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                if (onReadingSetup != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onReadingSetup)
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .tourPlace("settings-reading"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.padding(horizontal = 10.dp))
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Reading setup"), style = MaterialTheme.typography.titleSmall)
                            Text(tr("Page colour, font, text size and how the reader works"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                if (onAi != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAi)
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("settings-ai")
                            .tourPlace("settings-ai"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(horizontal = 10.dp))
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("AI and dictionary"), style = MaterialTheme.typography.titleSmall)
                            Text(tr("Meanings of words, chapter summaries and questions about a book"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                FileManagerLine()
                UpdateLines()
                if (coversPerRow != null) {
                    Box(Modifier.tourPlace("settings-covers")) { CoversPerRowLines(coversPerRow) }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                if (comicsTab != null) Column(Modifier.tourPlace("settings-comics")) {
                    SwitchLine(tr("Comics tab"), tr("Comics get a tab of their own, with each series as one entry. When this is off, they're with your books."), comicsTab, onComicsTab)
                    if (comicsTab && androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600) {
                        Text(
                            tr("On a wide screen, a series opens"),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        )
                        for (inPlace in listOf(false, true)) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .selectable(seriesInPlace == inPlace, role = Role.RadioButton) { onSeriesInPlace(inPlace) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = seriesInPlace == inPlace, onClick = null, modifier = Modifier.padding(horizontal = 10.dp))
                                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(if (inPlace) tr("In place of the shelf") else tr("Beside the shelf"), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (inPlace) tr("The series takes the whole screen, with its comics as covers or a list.") else tr("Its comics are listed beside the shelf."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                Box(Modifier.tourPlace("settings-look")) { dev.kolib.app.ui.theme.ThemePicker(current, onPick) }
                if (onOpening != null) Column(Modifier.tourPlace("settings-opening")) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    SwitchLine(tr("Opening animation"), tr("Plays when KoLib starts. Tap it to skip."), opening, onOpening)
                    SwitchLine(
                        tr("Opening sound"), tr("Uses your media volume."),
                        opening && openingSound, { onOpeningSound?.invoke(it) }, enabled = opening,
                    )
                    if (onOpeningLight != null) {
                        SwitchLine(
                            tr("Performance mode"), tr("A lighter animation for slower devices. On by default on phones."),
                            opening && openingLight, onOpeningLight, enabled = opening,
                        )
                    }
                }
                if (onShowTours != null || toursOn != null) Column(Modifier.tourPlace("settings-tours")) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    if (toursOn != null) {
                        Box(Modifier.testTag("settings-tours-on")) {
                            SwitchLine(tr("Tours"), tr("A short tour of each screen, the first time you open it."), toursOn, onToursOn)
                        }
                    }
                    if (onShowTours != null) Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onShowTours)
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("settings-tours"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Explore, null, Modifier.padding(horizontal = 10.dp))
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Show the tours again"), style = MaterialTheme.typography.titleSmall)
                            Text(tr("Each screen's tour shows the next time you open it."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done")) } },
    )
    // The tour, over the window: a window of its own, the whole screen, lighting the settings where they are.
    if (touring) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val stops = remember {
            dev.kolib.app.ui.tour.Tours.settings { name ->
                {
                    val view = places.rectOf(listOf("settings-view"))
                    val item = places.rectOf(listOf(name))
                    if (view != null && item != null) {
                        val margin = with(density) { 12.dp.toPx() }
                        when {
                            item.top < view.top + margin -> scroll.animateScrollBy(item.top - view.top - margin)
                            item.bottom > view.bottom - margin -> scroll.animateScrollBy(minOf(item.bottom - view.bottom + margin, item.top - view.top - margin))
                        }
                    }
                }
            }
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            ),
        ) {
            // No dimming of its own: the tour darkens all but what it lights.
            val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect { window?.setDimAmount(0f) }
            dev.kolib.app.ui.tour.Tour(stops, places, onDone = {
                touring = false
                onTourDone()
            }, onTurnOff = if (toursOn != null) ({ touring = false; onToursOn(false) }) else null)
        }
    }
}

/** A setting that's on or off: its name and a line about it, and a switch. */
@Composable
private fun SwitchLine(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val faded = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = faded))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = faded))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = null, enabled = enabled)
    }
}

/** How many covers a shelf's row holds, upright and sideways: Auto (as many as there's room for) or a number. */
@Composable
private fun CoversPerRowLines(perRow: dev.kolib.app.ui.components.CoversPerRow) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag("settings-covers-per-row")) {
        Text(tr("Covers per row"), style = MaterialTheme.typography.titleSmall)
        Text(tr("Auto fits as many as there's room for."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PerRowSlider(tr("Portrait"), perRow.portrait, Modifier.testTag("covers-portrait")) { perRow.portrait = it }
        PerRowSlider(tr("Landscape"), perRow.landscape, Modifier.testTag("covers-landscape")) { perRow.landscape = it }
    }
}

@Composable
private fun PerRowSlider(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val most = dev.kolib.app.ui.components.CoversPerRow.MOST
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.width(92.dp))
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { onChange(kotlin.math.round(it).toInt().coerceIn(0, most)) },
            valueRange = 0f..most.toFloat(),
            steps = most - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (value == 0) tr("Auto") else "$value",
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.widthIn(min = 48.dp),
        )
    }
}

/** The Kobo screen or the library is showing, with its own notices about the Kobo at the top. */
private fun koboNoticeShown(section: Section, overlay: String?) =
    overlay == null && (section == Section.KOBO || section == Section.LIBRARY)


/** Looking for a newer KoLib: now, and whether to by itself when the app starts. */
@Composable
private fun UpdateLines() {
    val updater = (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.kolib.app.KoLibApp).updater
    var auto by remember { mutableStateOf(updater.auto) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { updater.lookNow() }.padding(vertical = 10.dp, horizontal = 4.dp).testTag("settings-update"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SystemUpdate, null, Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Check for updates"), style = MaterialTheme.typography.titleSmall)
            Text(tr("You have KoLib {0}.", updater.installed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SwitchLine(tr("Check for updates automatically"), tr("Asks GitHub for the newest version when KoLib starts, once a day."), auto, { auto = it; updater.auto = it })
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
}

/** The file manager chosen to add books with, once one has been, with a way to choose another. */
@Composable
private fun FileManagerLine() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(dev.kolib.app.ui.components.FileManagers.chosenName(context)) }
    val shown = name ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Folder, null, Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Files are chosen with"), style = MaterialTheme.typography.titleSmall)
            Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = {
            dev.kolib.app.ui.components.FileManagers.forget(context)
            name = null
        }) { Text(tr("Ask again")) }
    }
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
}
