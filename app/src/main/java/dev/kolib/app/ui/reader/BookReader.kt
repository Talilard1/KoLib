package dev.kolib.app.ui.reader

import dev.kolib.app.tr
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.epub.BookFiles
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.reader.ReadingHereIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads a library book: opens its file, keeps the place in it, and closes it again afterwards. */
@Composable
fun BookReader(
    book: LibraryBook,
    library: Library,
    settings: ReaderSettingsStore,
    onClose: () -> Unit,
    onEdit: ((String) -> Unit)?,
    /** Told where the book is left, for the library's covers. */
    readingHere: ReadingHereIndex? = null,
    /** For a book added as a PDF: reads the PDF instead. */
    onReadAsPdf: (() -> Unit)? = null,
    /** Opens the app's own settings: its look, language and books folder. */
    onAppSettings: (() -> Unit)? = null,
    /** How the Kobo shares the book out between its chapters, when it has a place in it (see [ReaderController]). */
    koboSpans: List<dev.kolib.app.kobo.KoboChapterSpan> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val landscape = heldSideways()
    var controller by remember(book.id) { mutableStateOf<ReaderController?>(null) }
    val store = remember(book.id) { ReaderStore(library.bookDir(book.id)) { saved -> readingHere?.update(book.id, saved.position) } }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
    val session = rememberReadingSession(book)
    CountReading(session)
    DisposableEffect(book.id) {
        var epub: EpubFile? = null
        val job = scope.launch {
            try {
                val opened = withContext(Dispatchers.IO) {
                    val e = EpubFile.open(library.epubFile(book.id))
                    epub = e
                    val files = EpubFileFiles(e)
                    files to BookStructure(files)
                }
                if (opened.second.chapters.isEmpty()) {
                    error = tr("This book has no chapters to read.")
                    return@launch
                }
                controller = ReaderController(
                    files = opened.first,
                    structure = opened.second,
                    bookTitle = book.title,
                    language = book.meta.language,
                    store = store,
                    // With the book's own text, page and layout settings, if it has them.
                    initialSettings = settings.usual(landscape).let { usual -> store.look(usual, landscape)?.let { usual.withLookOf(it) } ?: usual },
                    assets = context.assets,
                    bookAuthor = book.authorLine,
                    bookSeriesIndex = book.meta.seriesIndex,
                    koboSpans = koboSpans,
                    session = session,
                ).apply {
                    // Plates printed on their side (as light novels have scenes across two pages) found from the words in
                    // them, with the text recognition's data for the book's language or English, if it's on the device.
                    val filesDir = context.filesDir
                    val language = dev.kolib.app.ocr.OcrLanguages.suggested(book.meta.language)
                        .filter { dev.kolib.app.ocr.OcrLanguages.installed(filesDir, it) }
                    if (language.isNotEmpty()) sidewaysCheck = {
                        runCatching { dev.kolib.app.ocr.SidewaysPictures(dev.kolib.app.ocr.OcrLanguages.root(filesDir), language.take(1)) }.getOrNull()
                    }
                }
            } catch (e: Exception) {
                AppLog.e("Reader", "The book couldn't be opened", e)
                error = e.message ?: tr("The book couldn't be opened.")
            }
        }
        onDispose {
            job.cancel()
            controller?.saveNow()
            controller = null
            // After the page has let go of it.
            val toClose = epub
            scope.launch { withContext(Dispatchers.IO) { kotlinx.coroutines.delay(500); runCatching { toClose?.close() } } }
        }
    }
    val c = controller
    when {
        c != null -> ReaderWith(c, settings, onClose, onEdit, onReadAsPdf, onAppSettings, lookStore = store)
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
                TextButton(onClick = onClose) { Text(tr("Back")) }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** Reads a book as it is in the editor, unsaved changes included, from [startPath]. */
@Composable
fun PreviewReader(files: BookFiles, structure: BookStructure, title: String, language: String?, startPath: String?, settings: ReaderSettingsStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val landscape = heldSideways()
    val controller = remember(files, structure) {
        ReaderController(files, structure, title, language, store = null, initialSettings = settings.usual(landscape), assets = context.assets)
            .apply { initialPath = startPath }
    }
    ReaderWith(controller, settings, onClose, onEdit = null)
}

/** The screen is wider than it's tall: the device is held sideways (or its window is that shape). */
@Composable
internal fun heldSideways(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

/**
 * The reader with its settings: the usual ones, and a book's own text, page and layout settings
 * ([lookStore] keeps them), which it has once one of those is changed while it's read. Both are kept for
 * each way the device is held: turned, the reader takes up that way's, and a change is to that way's only.
 */
@Composable
private fun ReaderWith(
    controller: ReaderController,
    settings: ReaderSettingsStore,
    onClose: () -> Unit,
    onEdit: ((String) -> Unit)?,
    onReadAsPdf: (() -> Unit)? = null,
    onAppSettings: (() -> Unit)? = null,
    lookStore: ReaderStore? = null,
) {
    val landscape = heldSideways()
    val upright by settings.settings.collectAsStateWithLifecycle()
    val sideways by settings.landscape.collectAsStateWithLifecycle()
    val current = if (landscape) sideways else upright
    var own by remember(controller, landscape) { mutableStateOf(lookStore?.look(settings.usual(landscape), landscape)) }
    val shown = own?.let { current.withLookOf(it) } ?: current
    ReaderScreen(
        controller = controller,
        onSettings = { s ->
            if (lookStore == null) settings.update(landscape) { s }
            else {
                // The page colour, brightness, controls and the rest are every book's; the look is this book's.
                settings.update(landscape) { usual -> s.withLookOf(usual) }
                if (!s.sameLook(shown)) {
                    own = s
                    lookStore.saveLook(s, landscape)
                }
            }
            controller.applySettings(s)
        },
        onClose = onClose,
        onEdit = onEdit,
        onReadAsPdf = onReadAsPdf,
        onAppSettings = onAppSettings,
        bookLook = lookStore?.let { ls ->
            BookLook(
                own = own != null,
                makeUsual = {
                    own?.let { o -> settings.update(landscape) { usual -> usual.withLookOf(o) } }
                    own = null
                    ls.forgetLook(landscape)
                },
                useUsual = {
                    own = null
                    ls.forgetLook(landscape)
                },
                landscape = landscape,
            )
        },
    )
    // On a screen with a camera in the middle of its top, the first time it's read on held this way: the headings
    // and the time move clear of the camera, where nobody has moved them (the usual ones, and this book's own).
    val cut = controller.cutouts
    androidx.compose.runtime.LaunchedEffect(landscape, cut, controller) {
        if (cut.top == null || cut.width <= 0f) return@LaunchedEffect
        settings.placeAroundCamera(landscape, cut)
        val mine = own
        if (lookStore != null && mine != null && !lookStore.lookPlacedForCamera(landscape)) {
            val placed = if (cut.topMiddle && mine.stockArrangement && mine.pagesAcross(cut.width, cut.height) == 1) mine.aroundCamera() else mine
            own = placed
            lookStore.saveLook(placed, landscape)
        }
    }
    // Settings changed elsewhere (or the book going back to the usual ones, or the device being turned) reach this book too.
    androidx.compose.runtime.LaunchedEffect(shown) {
        if (controller.settings != shown) controller.applySettings(shown)
    }
}
