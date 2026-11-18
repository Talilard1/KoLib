package dev.kolib.app.ui.ocr

import dev.kolib.app.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.kolib.app.ui.components.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.KoLibApp
import dev.kolib.app.convert.BookFormats
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.ocr.OcrLanguages
import dev.kolib.app.ocr.OcrStatus
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.GoldButton
import dev.kolib.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the screens can ask for, to do with making scanned books into text. */
class OcrActions(
    /** Opens the dialog to make [books] into text. */
    val offer: (List<LibraryBook>) -> Unit,
    /** Carries on with a book that was stopped, in the languages it was being read in. */
    val carryOn: (LibraryBook, List<String>) -> Unit,
    val stop: (LibraryBook) -> Unit,
    /** Opens the page comparing the scan with the text. */
    val compare: (LibraryBook) -> Unit,
    /** Asks, then makes the book its scan again. */
    val goBack: (LibraryBook) -> Unit,
)

val LocalOcrActions = staticCompositionLocalOf<OcrActions?> { null }

/** Where [status] is, in words: "Page 120 of 900 · about 40 minutes left". */
fun describe(status: OcrStatus): String = when (status) {
    OcrStatus.Waiting -> tr("Waiting in the queue…")
    is OcrStatus.Downloading -> tr("Downloading the language data: {0} of {1} MB", "%.1f".format(status.done / 1e6), "%.1f".format(status.total / 1e6))
    OcrStatus.Preparing -> tr("Getting ready…")
    is OcrStatus.Reading -> tr("Page {0} of {1}", status.done, status.total) + (status.millisLeft()?.let { Fmt.timeLeft(it) } ?: "")
    OcrStatus.Making -> tr("Building the book…")
}

private fun OcrStatus.fraction(): Float? = when (this) {
    is OcrStatus.Downloading -> if (total > 0) done.toFloat() / total else null
    is OcrStatus.Reading -> if (total > 0) done.toFloat() / total else null
    else -> null
}

/** The names of [codes], as a list: "Ancient Greek, English". */
fun languageNames(codes: List<String>): String = codes.mapNotNull { OcrLanguages.byCode(it)?.name }.joinToString(", ")

/** On a scanned book's page: making it into text, how that's going, and comparing the text with the scan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrSection(book: LibraryBook, shownStatus: OcrStatus? = null) {
    if (!book.isScan && !book.isRecognised) return
    val actions = LocalOcrActions.current ?: return
    val app = LocalContext.current.applicationContext as KoLibApp
    val all by app.ocr.status.collectAsStateWithLifecycle()
    val status = shownStatus ?: all[book.id]
    // Pages read before it was stopped (looked up on disk, off the main thread).
    val stopped by produceState<Pair<Int, Int>?>(null, book.id, book.sourceFormat, status == null) {
        value = if (status == null && book.isScan) withContext(Dispatchers.IO) { app.ocr.partly(book.id) } else null
    }
    val applied by produceState<List<String>?>(null, book.id, book.sourceFormat) {
        value = withContext(Dispatchers.IO) { app.library.ocrStore(book.id).applied?.languages }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard(if (book.isRecognised) tr("Text from the scan") else tr("Convert to text"), icon = Icons.Outlined.DocumentScanner) {
        when {
            status != null -> {
                Text(describe(status), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val f = status.fraction()
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("Continues with KoLib closed. You can check the pages done so far."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status is OcrStatus.Reading && status.done > 0) OutlinedButton(onClick = { actions.compare(book) }) {
                        Icon(Icons.Outlined.Compare, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Compare with the pages"))
                    }
                    OutlinedButton(onClick = { actions.stop(book) }) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Stop"))
                    }
                }
            }
            book.isRecognised -> {
                Text(
                    (applied?.let { tr("The text was recognised from the scanned pages ({0}), so it may have mistakes.", languageNames(it)) }
                        ?: tr("The text was recognised from the scanned pages, so it may have mistakes.")) +
                        tr(" The original PDF is still available."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.compare(book) }) {
                        Icon(Icons.Outlined.Compare, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Compare with the pages"))
                    }
                    OutlinedButton(onClick = { actions.goBack(book) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Outlined.Undo, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Revert to scan"))
                    }
                }
            }
            else -> {
                Text(
                    if (book.sourceFormat == BookFormats.PDF_ONLY) tr("This PDF is too large to convert to an EPUB here, so it's read and sent as a PDF. If it's a scan, text recognition can make it a book with resizable text.")
                    else tr("This PDF is a scan, so it's read and sent as a PDF. Text recognition can make it a book with resizable text."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (stopped == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Each language needs a one-time download of a few megabytes."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val s = stopped
                if (s != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr("{0} of {1} pages done.", s.first, s.second), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s != null) {
                        val languages = remember(book.id, s) { app.library.ocrStore(book.id).settings?.languages.orEmpty() }
                        if (languages.isNotEmpty()) GoldButton(onClick = { actions.carryOn(book, languages) }) { Text(tr("Resume")) }
                        OutlinedButton(onClick = { actions.compare(book) }) {
                            Icon(Icons.Outlined.Compare, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Compare with the pages"))
                        }
                    }
                    if (s == null) GoldButton(onClick = { actions.offer(listOf(book)) }) { Text(tr("Convert to text…")) }
                    else OutlinedButton(onClick = { actions.offer(listOf(book)) }) { Text(tr("Other languages…")) }
                }
            }
        }
    }
}

/** Over the library while books are being made into text: which, and how far it's got. Tap to see the book. */
@Composable
fun OcrStrip(onShow: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val all by app.ocr.status.collectAsStateWithLifecycle()
    val (id, status) = all.entries.firstOrNull { it.value != OcrStatus.Waiting }?.toPair() ?: return
    val title = app.library.book(id)?.title ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable { onShow(id) }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                tr("Converting “{0}” to text", title) + " · " + describe(status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val f = status.fraction()
            if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** Asks which languages [books] are in, and says what making them into text means, before it starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrDialog(books: List<LibraryBook>, onStart: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember(books) { mutableStateOf(OcrLanguages.suggested(books.firstOrNull()?.meta?.language).toSet()) }
    val ordered = OcrLanguages.all.map { it.code }.filter { it in chosen }
    val toDownload = remember(ordered) { OcrLanguages.missing(context.filesDir, ordered).sumOf { it.bytes } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DocumentScanner, null) },
        title = { Text(if (books.size == 1) tr("Convert “{0}” to text", books[0].title) else tr("Convert {0} books to text", books.size)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    tr("Text recognition (OCR) runs on this device and makes a book with resizable text. It takes a few seconds per page and continues with KoLib closed."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                // Said first, before anything is picked: it needs the internet the first time.
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (toDownload > 0) tr("A one-time download is needed: {0} of language data from the Tesseract project.", Fmt.bytes(context, toDownload) ?: "")
                        else tr("The language data is already on this device."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (toDownload > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("Expect some mistakes, especially with old or small print, tables, footnotes and other alphabets. You can compare the text with the scan page by page, and revert to the scan."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(tr("Languages in the book"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(tr("Select every language in the book. Each one slows it down a little."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in OcrLanguages.all) {
                        FilterChip(
                            selected = l.code in chosen,
                            onClick = { chosen = if (l.code in chosen) chosen - l.code else chosen + l.code },
                            label = { Text(l.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(ordered) }, enabled = ordered.isNotEmpty()) { Text(tr("Convert to text")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Asks before a recognised book goes back to being its scan. */
@Composable
fun GoBackToScanDialog(book: LibraryBook, onGoBack: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Revert to the scan?")) },
        text = {
            Text(
                tr("“{0}” goes back to being read and sent as a PDF. Converting it again in the same languages will be quick.", book.title)
            )
        },
        confirmButton = { TextButton(onClick = onGoBack) { Text(tr("Revert to scan"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
