package dev.kolib.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.KoLibApp
import dev.kolib.app.kobo.KoboChapterSpan
import dev.kolib.app.library.ComicSeries
import dev.kolib.app.reader.PdfPlace
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.ui.LookDialog
import dev.kolib.app.ui.ai.AiSetupDialog
import dev.kolib.app.ui.library.LanguageDialog
import dev.kolib.app.ui.library.libraryComics

/**
 * The reader for the book [startId] (a comic's, a PDF's or a book's), as the reader's own screen shows it: reading on
 * into the next comic in a series, a PDF read as its text and back, and the app's own settings over it.
 */
@Composable
fun ReaderHost(
    startId: String,
    koboSpans: List<KoboChapterSpan>,
    /** The book or comic being read, as it changes. */
    onShowing: (String) -> Unit,
    onClose: () -> Unit,
    /** Opens the book in the text editor, at a chapter. */
    onEditText: (bookId: String, path: String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val library = app.library
    var id by rememberSaveable { mutableStateOf(startId) }
    SideEffect { onShowing(id) }
    val books by library.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == id }
    var showLook by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    if (book == null) {
        // Removed while it was open.
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val dir = library.bookDir(book.id)
    // A book added as a PDF reads as the PDF, or as the text made from it, whichever was used last.
    var asText by rememberSaveable(book.id) { mutableStateOf(book.isPdf && !book.isScan && ReaderStore.pdfPlace(dir)?.asText == true) }
    val readAs = { text: Boolean ->
        val place = ReaderStore.pdfPlace(dir) ?: PdfPlace(0, 0)
        ReaderStore.savePdfPlace(dir, place.copy(asText = text))
        asText = text
    }
    if (book.isComic && library.sourceFile(book.id) != null) {
        // A comic: at its end, the next in its series is offered, and read on into.
        val series = remember(books, book.id) { ComicSeries.entryOf(book, libraryComics(books))?.takeIf { it.isSeries } }
        ComicBookReader(
            book = book,
            library = library,
            readerSettings = app.readerSettings,
            onClose = onClose,
            readingHere = app.readingHere,
            onAppSettings = { showLook = true },
            series = series,
            onOpen = { next -> id = next },
        )
    } else if (book.isPdf && !asText) {
        PdfBookReader(
            book = book,
            library = library,
            settings = app.readerSettings,
            onClose = onClose,
            onReadAsText = if (book.isScan) null else ({ readAs(true) }),
            readingHere = app.readingHere,
            onAppSettings = { showLook = true },
        )
    } else {
        BookReader(
            book = book,
            library = library,
            settings = app.readerSettings,
            onClose = onClose,
            onEdit = { path -> onEditText(book.id, path) },
            readingHere = app.readingHere,
            onReadAsPdf = if (book.isPdf) ({ readAs(false) }) else null,
            onAppSettings = { showLook = true },
            // The Kobo's copy, if it has a place in it: how far through is counted as the Kobo counts it.
            koboSpans = if (book.id == startId) koboSpans else emptyList(),
        )
    }
    // The app's own settings, over the book: what can be chosen with a book open.
    if (showLook) {
        val themes = app.themeSettings
        val theme by themes.choice.collectAsStateWithLifecycle()
        val opening by themes.opening.collectAsStateWithLifecycle()
        val openingSound by themes.openingSound.collectAsStateWithLifecycle()
        val openingLight by themes.openingLight.collectAsStateWithLifecycle()
        LookDialog(
            current = theme, onPick = themes::set, onDismiss = { showLook = false },
            opening = opening, onOpening = themes::setOpening, openingSound = openingSound, onOpeningSound = themes::setOpeningSound,
            openingLight = openingLight, onOpeningLight = themes::setOpeningLight,
            onLanguage = {
                showLook = false
                showLanguage = true
            },
            onAi = { showAi = true },
        )
    }
    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
    if (showAi) AiSetupDialog(onDismiss = { showAi = false })
}
