package dev.kobolibrary.app.ui.reader

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
import dev.kobolibrary.app.epub.BookFiles
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReaderSettingsStore
import dev.kobolibrary.app.reader.ReaderStore
import dev.kobolibrary.app.reader.ReadingHereIndex
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var controller by remember(book.id) { mutableStateOf<ReaderController?>(null) }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
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
                    error = "This book has no chapters to read."
                    return@launch
                }
                controller = ReaderController(
                    files = opened.first,
                    structure = opened.second,
                    bookTitle = book.title,
                    language = book.meta.language,
                    store = ReaderStore(library.bookDir(book.id)) { saved -> readingHere?.update(book.id, saved.position) },
                    initialSettings = settings.settings.value,
                    assets = context.assets,
                    bookAuthor = book.authorLine,
                    bookSeriesIndex = book.meta.seriesIndex,
                )
            } catch (e: Exception) {
                AppLog.e("Reader", "The book couldn't be opened", e)
                error = e.message ?: "The book couldn't be opened."
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
        c != null -> ReaderWith(c, settings, onClose, onEdit)
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
                TextButton(onClick = onClose) { Text("Back") }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** Reads a book as it is in the editor, unsaved changes included, from [startPath]. */
@Composable
fun PreviewReader(files: BookFiles, structure: BookStructure, title: String, language: String?, startPath: String?, settings: ReaderSettingsStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val controller = remember(files, structure) {
        ReaderController(files, structure, title, language, store = null, initialSettings = settings.settings.value, assets = context.assets)
            .apply { initialPath = startPath }
    }
    ReaderWith(controller, settings, onClose, onEdit = null)
}

@Composable
private fun ReaderWith(controller: ReaderController, settings: ReaderSettingsStore, onClose: () -> Unit, onEdit: ((String) -> Unit)?) {
    val current by settings.settings.collectAsStateWithLifecycle()
    ReaderScreen(
        controller = controller,
        onSettings = { s ->
            settings.update { s }
            controller.applySettings(s)
        },
        onClose = onClose,
        onEdit = onEdit,
    )
    // Settings changed elsewhere reach this book too.
    androidx.compose.runtime.LaunchedEffect(current) {
        if (controller.settings != current) controller.applySettings(current)
    }
}
