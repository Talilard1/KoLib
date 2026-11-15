package dev.kobolibrary.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kobolibrary.app.convert.EpubOut
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.ImportOutcome
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.ocr.Hocr
import dev.kobolibrary.app.ocr.OcrStatus
import dev.kobolibrary.app.ocr.OcrStore
import dev.kobolibrary.app.ui.library.LibraryDetailPane
import dev.kobolibrary.app.ui.ocr.CompareScreen
import dev.kobolibrary.app.ui.ocr.LocalOcrActions
import dev.kobolibrary.app.ui.ocr.OcrActions
import dev.kobolibrary.app.ui.ocr.OcrDialog
import dev.kobolibrary.app.ui.ocr.OcrSection
import dev.kobolibrary.app.ui.ocr.ScanPages
import dev.kobolibrary.app.ui.theme.AppSurface
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Making a scanned book into text: the offer on its page, the dialog, progress, and comparing the text with the scan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrShots {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app get() = context.applicationContext as KoboLibraryApp

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun inLanguage() = TestLanguage.apply()

    private val noActions = OcrActions({}, { _, _ -> }, {}, {}, {})

    private fun shot(name: String, settle: Int = 10, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface { CompositionLocalProvider(LocalOcrActions provides noActions) { content() } } }
        // Pages are drawn and read off the main thread.
        repeat(settle) {
            Thread.sleep(100)
            compose.mainClock.advanceTimeBy(100)
        }
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    /** A scanned book in the app's library: a stand-in and the PDF it came as. */
    private fun scan(title: String = "A Greek-English Lexicon", format: String = "SCAN"): LibraryBook = runBlocking {
        val lib = app.library.apply { load() }
        lib.books.value.firstOrNull { it.title == title }?.let { return@runBlocking it }
        val standIn = File(context.cacheDir, "standin-${title.hashCode()}.epub")
        EpubOut(BookMetadata(title = title, authors = listOf("H. G. Liddell", "R. Scott"), language = "grc")).apply {
            page(title, "<p>This book's pages are pictures, so it reads as the PDF it came as.</p>")
            write(standIn)
        }
        val pdf = File(context.cacheDir, "scan.pdf").apply { writeText("%PDF-1.4 made up") }
        var book = (lib.import("lexicon.pdf", { standIn.inputStream() }, BookSource.FILE, made = true, attach = pdf to "SCAN") as ImportOutcome.Added).book
        if (format == "OCR") {
            val store = lib.ocrStore(book.id)
            store.begin(OcrStore.Settings(listOf("grc", "eng"), 300, 4))
            for (n in 1..4) store.save(n - 1, Hocr.parse(javaClass.getResource("/ocr/page$n.hocr")!!.readText(), 300))
            val made = File(context.cacheDir, "made.epub")
            dev.kobolibrary.app.ocr.OcrBook.write(book.meta, (0 until 4).map { dev.kobolibrary.app.ocr.OcrBook.PageText.of(store.page(it)!!) }, emptyList(), null, made)
            book = lib.applyRecognisedText(book.id, made, listOf("grc", "eng"))
        }
        book
    }

    private fun page(book: LibraryBook, status: OcrStatus? = null) {
        val covers = LibraryCovers(app.library)
        shot(if (status != null) "ocr_reading" else if (book.isRecognised) "ocr_recognised" else "ocr_offer") {
            Box(Modifier.fillMaxSize()) {
                if (status == null) {
                    LibraryDetailPane(book, covers, null, koboConnected = true, onEdit = {}, onCover = {}, onDelete = {}, onSend = {}, onCollection = {}, onTag = {})
                } else {
                    Box(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) { OcrSection(book, status) }
                }
            }
        }
    }

    @Test
    fun theOfferOnAScannedBooksPage() = page(scan())

    @Test
    fun howFarItsGot() = page(scan(), OcrStatus.Reading(312, 1104, System.currentTimeMillis() - 40 * 60_000L, 0))

    @Test
    fun aBookMadeIntoText() = page(scan("Alice", "OCR"))

    @Test
    fun theDialog() {
        val book = scan()
        shot("ocr_dialog") { OcrDialog(listOf(book), {}, {}) }
    }

    /** The made-up pages the tests read, as the scan. */
    private object Fixtures : ScanPages {
        override val count = 4
        override fun draw(index: Int, maxW: Int, maxH: Int): Bitmap? {
            val b = javaClass.getResourceAsStream("/ocr/page${index + 1}.png")!!.use { BitmapFactory.decodeStream(it) }
            val scale = minOf(maxW.toFloat() / b.width, maxH.toFloat() / b.height)
            return Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
        }
        override fun close() = Unit
    }

    @Test
    fun comparingTheTextWithTheScan() {
        val book = scan("Alice", "OCR")
        shot("ocr_side_by_side", settle = 20) {
            CompareScreen(book, app.library, onClose = {}, onGoBack = {}, open = { Fixtures })
        }
    }
}
