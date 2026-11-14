package dev.kobolibrary.app.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kobolibrary.app.ui.library.ExportDialog
import dev.kobolibrary.app.ui.library.SplitBookDialog
import dev.kobolibrary.app.ui.library.MergeDialog
import dev.kobolibrary.app.ui.library.NewBookDialog
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.Connection
import dev.kobolibrary.app.JobKind
import dev.kobolibrary.app.kobo.SendFormat
import dev.kobolibrary.app.LibraryViewModel
import dev.kobolibrary.app.MainViewModel
import dev.kobolibrary.app.kobo.VolumeCandidate
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.cover.CoverEditorScreen
import dev.kobolibrary.app.ui.edit.EditDetailsScreen
import dev.kobolibrary.app.ui.edit.EditSuggestions
import dev.kobolibrary.app.kobo.KoboSender
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Palette
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import dev.kobolibrary.app.ui.theme.ThemeChoice
import dev.kobolibrary.app.ui.theme.LocalKoLibStyle
import dev.kobolibrary.app.ui.theme.appBackdrop
import dev.kobolibrary.app.ui.components.KoLibMark
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import dev.kobolibrary.app.ui.library.DuplicatesDialog
import dev.kobolibrary.app.ui.library.JobDialog
import dev.kobolibrary.app.ui.library.SendDialog
import dev.kobolibrary.app.ui.library.LibraryBrowseState
import dev.kobolibrary.app.ui.library.LibraryScreen
import dev.kobolibrary.app.ui.reader.BookReader
import dev.kobolibrary.app.ui.reader.PreviewReader
import dev.kobolibrary.app.ui.textedit.TextEditorScreen
import dev.kobolibrary.app.ui.components.HideStatusBar
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.CompositionLocalProvider
import dev.kobolibrary.app.ui.components.FrameBars
import dev.kobolibrary.app.ui.components.LocalFrameBars
import dev.kobolibrary.app.ui.components.phoneLayout

enum class Section { LIBRARY, KOBO }

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
) {
    val state by vm.state.collectAsStateWithLifecycle()
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
    /** Books waiting in the send dialog. */
    var sendIds by remember { mutableStateOf<List<String>?>(null) }
    /** Making books: the new-book dialog, books to put together, a book to split. */
    var showNewBook by remember { mutableStateOf(false) }
    var mergeIds by remember { mutableStateOf<List<String>?>(null) }
    var splitId by remember { mutableStateOf<String?>(null) }
    /** Books waiting in the save-a-copy dialog, then the format they're saved as while a picker is open. */
    var exportIds by remember { mutableStateOf<List<String>?>(null) }
    var exporting by remember { mutableStateOf<Pair<List<String>, SendFormat>?>(null) }
    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
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
    val browse = remember { BrowseState() }
    val libraryBrowse = remember { LibraryBrowseState() }
    var localMessage by remember { mutableStateOf<String?>(null) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onTreePicked(result.data?.data)
    }
    val chooseDrive: (VolumeCandidate?) -> Unit = { candidate ->
        try {
            AppLog.i("Kobo", "Opening Android's drive picker${candidate?.let { " on '${it.description}'" } ?: ""}.")
            treePicker.launch(vm.openTreeIntent(candidate))
        } catch (e: ActivityNotFoundException) {
            AppLog.e("Kobo", "No folder picker available", e)
            localMessage = "This device has no folder picker. The app needs one to open the Kobo's storage."
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        libraryVm.importUris(uris)
    }
    val addFiles = {
        try {
            filePicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        } catch (e: ActivityNotFoundException) {
            AppLog.e("Library", "No file picker available", e)
            localMessage = "This device has no file picker."
        }
    }
    val addFromKobo = {
        section = Section.KOBO
        browse.deviceTab = 0
        browse.selectedBookId = null
        browse.source = SourceFilter.SIDELOADED
        browse.selecting = true
    }

    // A new job: show its progress (and the library, for books shared from other apps).
    LaunchedEffect(job?.title, job?.total, job?.startedAt) {
        val p = job ?: return@LaunchedEffect
        if (!p.finished) {
            showJob = true
            if (p.kind == JobKind.ADD_FILES) {
                section = Section.LIBRARY
                if (listOf("edit:", "cover:", "text:", "read:").none { overlay?.startsWith(it) == true }) overlay = null
            }
        }
    }
    // Finished while hidden: say how it went instead (unless there are books already there to ask about:
    // that question says how it went).
    LaunchedEffect(job?.finished, showJob) {
        val p = job ?: return@LaunchedEffect
        if (p.finished && !showJob && libraryVm.duplicates.value.isEmpty()) {
            libraryVm.dismissJob()
            val sent = p.kind == JobKind.SEND
            snackbar.showSnackbar(
                listOfNotNull(
                    "${Fmt.plural(p.succeeded, "book")} ${if (sent) "sent" else "added"}.",
                    p.alreadyThere.takeIf { it > 0 && !sent }?.let { if (it == 1) "1 was already in the library." else "$it were already in the library." },
                    p.failures.size.takeIf { it > 0 }?.let { "$it couldn't be ${if (sent) "sent" else "added"}. The log has the details." },
                ).joinToString(" ") + (if (sent && p.succeeded > 0) " Eject the Kobo so it can add them." else "") +
                    (if (sent && p.coversAfterRestart.isNotEmpty()) " Restart it to see the new covers." else "")
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            vm.consumeMessage()
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

    BackHandler(enabled = overlay == "log" || overlay == "backups" || overlay == "about") { overlay = null }
    BackHandler(enabled = overlay == null && section == Section.KOBO) { section = Section.LIBRARY }

    val koboExtras = remember(books, state.koboTags) { KoboExtras(books, state.koboTags) }
    val connection = state.connection
    val koboView = remember(state.connection, state.lastSeen, state.removedIds) { state.koboView }
    val liveSession = (connection as? Connection.Connected)?.takeIf { !it.unplugged }?.session
    var narrow by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .appBackdrop()
    ) {
        val current = overlay
        when {
            current == "log" -> LogScreen(onBack = { overlay = null }, onMessage = { localMessage = it })
            current == "about" -> AboutScreen(onBack = { overlay = null })
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
            current?.startsWith("read:") == true -> {
                val book = books.firstOrNull { it.id == current.removePrefix("read:") }
                if (book == null) {
                    LaunchedEffect(Unit) { overlay = null }
                } else {
                    BookReader(
                        book = book,
                        library = libraryVm.library,
                        settings = libraryVm.readerSettings,
                        onClose = { overlay = null },
                        onEdit = { path -> overlay = "text:${book.id}|$path" },
                        readingHere = libraryVm.readingHere,
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
            else -> AppFrame(
                section = section,
                onSection = { section = it },
                koboConnected = liveSession != null,
                onNarrow = { narrow = it },
                onLook = { showLook = true },
            ) {
                when (section) {
                    Section.LIBRARY -> LibraryScreen(
                        books = books,
                        covers = libraryVm.covers,
                        browse = libraryBrowse,
                        job = job,
                        koboConnected = liveSession != null,
                        onAddFiles = addFiles,
                        onAddFromKobo = addFromKobo,
                        onShowJob = { showJob = true },
                        onEditDetails = { overlay = "edit:$it" },
                        onChangeCover = { overlay = "cover:$it" },
                        onRead = { overlay = "read:$it" },
                        onEditText = { overlay = "text:$it" },
                        readingHere = readingHere,
                        onDelete = { ids ->
                            if (libraryBrowse.selectedId in ids) libraryBrowse.selectedId = null
                            libraryVm.delete(ids)
                        },
                        onSend = { ids -> sendIds = ids },
                        onNewBook = { showNewBook = true },
                        onAbout = { overlay = "about" },
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
                    )
                    Section.KOBO -> when {
                        connection is Connection.Connected && koboView != null -> DeviceScreen(
                            view = koboView,
                            unplugged = connection.unplugged,
                            state = state,
                            browse = browse,
                            onReconnect = vm::reconnect,
                            onWriteCheck = vm::runWriteCheck,
                            onBackupNow = vm::backupNow,
                            onForget = vm::forgetKobo,
                            onOpenBackups = {
                                vm.refreshBackups()
                                overlay = "backups"
                            },
                            onOpenLog = { overlay = "log" },
                            extras = koboExtras,
                            onAddToLibrary = { chosen -> libraryVm.importFromKobo(connection.session, chosen) },
                            onDeleteFromKobo = vm::deleteFromKobo,
                            sending = job?.let { it.kind == JobKind.SEND && !it.finished } == true,
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
                            onOpenBackups = {
                                vm.refreshBackups()
                                overlay = "backups"
                            },
                            onOpenLog = { overlay = "log" },
                            extras = koboExtras,
                            onHowToConnect = { showConnectHelp = true },
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
                        )
                    }
                }
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
                    onSend = { format, covers ->
                        liveSession?.let { libraryVm.sendToKobo(it, ids, format, covers) }
                        sendIds = null
                        libraryBrowse.stopSelecting()
                    },
                    onConnect = {
                        sendIds = null
                        section = Section.KOBO
                    },
                    onDismiss = { sendIds = null },
                    pathOnKobo = { b -> libraryVm.pathOnKobo(b, koboView?.snapshot) },
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
                    onSplit = { cuts, titles, series ->
                        splitId = null
                        libraryVm.splitBook(id, cuts, titles, series) { made -> made.firstOrNull()?.let { libraryBrowse.selectedId = it } }
                    },
                    onDismiss = { splitId = null },
                )
            }
        }
        exportIds?.let { ids ->
            ExportDialog(
                count = ids.size,
                title = books.firstOrNull { it.id == ids.first() }?.title,
                initial = libraryVm.sendFormat,
                onSave = { format ->
                    exportIds = null
                    exporting = ids to format
                    try {
                        if (ids.size == 1) saveOne.launch(libraryVm.exportName(ids.first(), format)) else saveMany.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        exporting = null
                        localMessage = "This device has no way to choose where to save files."
                    }
                },
                onDismiss = { exportIds = null },
            )
        }
        // Putting books together can take a few seconds: say so, over everything. So can checking and
        // saving to the Kobo's database, which must be done before the Kobo is ejected, or what was to be
        // saved (collections of books just sent, say) waits until next time. The Kobo screen has its own notice.
        val elsewhere = section != Section.KOBO || overlay != null
        val busy = making?.let { "$it…" }
            ?: "Saving to the Kobo… Don't eject it yet.".takeIf { state.syncing && elsewhere }
            ?: "Checking the Kobo… Don't eject it yet.".takeIf { state.afterConnectRunning && elsewhere }
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

        if (showEject) {
            EjectDialog(
                onDismiss = { showEject = false },
                busy = job?.let { it.kind == JobKind.SEND && !it.finished } == true,
                savingDatabase = state.syncing,
            )
        }
        if (showLook) LookDialog(
            current = theme, onPick = onTheme, onDismiss = { showLook = false },
            opening = opening, onOpening = onOpening, openingSound = openingSound, onOpeningSound = onOpeningSound,
            openingLight = openingLight, onOpeningLight = onOpeningLight,
        )
        if (showConnectHelp) ConnectHelpDialog(connection, chooseDrive, onDismiss = { showConnectHelp = false })

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
    }
}

/** The Library and Kobo sections: a rail down the side on tablets, a bar along the bottom on phones. */
@Composable
internal fun AppFrame(
    section: Section,
    onSection: (Section) -> Unit,
    koboConnected: Boolean,
    onNarrow: (Boolean) -> Unit,
    onLook: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
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
                NavigationRail(
                    containerColor = Color.Transparent,
                    header = {
                        Spacer(Modifier.height(10.dp))
                        KoLibMark(Modifier.size(40.dp).semantics { contentDescription = "KoLib" }, glow = true)
                        Spacer(Modifier.height(18.dp))
                    },
                ) {
                    val itemColors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    NavigationRailItem(
                        selected = section == Section.LIBRARY,
                        onClick = { onSection(Section.LIBRARY) },
                        icon = libraryIcon,
                        label = { Text("Library") },
                        colors = itemColors,
                    )
                    NavigationRailItem(
                        selected = section == Section.KOBO,
                        onClick = { onSection(Section.KOBO) },
                        icon = koboIcon,
                        label = { Text("Kobo") },
                        colors = itemColors,
                    )
                    if (onLook != null) {
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = onLook,
                            icon = { Icon(Icons.Outlined.Palette, null) },
                            label = { Text("Theme") },
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
            // two sections. A screen can hide the bar to put its own there (see HideBottomBar).
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
                                icon = libraryIcon,
                                label = { Text("Library") },
                                colors = itemColors,
                            )
                            NavigationBarItem(
                                selected = section == Section.KOBO,
                                onClick = { onSection(Section.KOBO) },
                                icon = koboIcon,
                                label = { Text("Kobo") },
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Palette, null) },
        title = { Text("App theme") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    ThemeChoice.entries.forEach { choice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = choice == current, role = Role.RadioButton, onClick = { onPick(choice) })
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == current, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(choice.label, style = MaterialTheme.typography.titleSmall)
                                Text(choice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (onOpening != null) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    SwitchLine("Opening animation", "Plays when KoLib starts. Tap it to skip.", opening, onOpening)
                    SwitchLine(
                        "Opening sound", "Plays with the animation, at your media volume.",
                        opening && openingSound, { onOpeningSound?.invoke(it) }, enabled = opening,
                    )
                    if (onOpeningLight != null) {
                        SwitchLine(
                            "Performance mode", "A lighter animation that runs smoothly on phones. On by default on phones.",
                            opening && openingLight, onOpeningLight, enabled = opening,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
