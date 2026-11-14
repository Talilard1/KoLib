package dev.kobolibrary.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.convert.PdfDocument
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.ReaderSettings
import dev.kobolibrary.app.ui.reader.PdfPages
import dev.kobolibrary.app.ui.reader.PdfPagesView
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import androidx.compose.ui.graphics.Color as ComposeColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The PDF reader, with made-up pages (Android's own PDF drawing isn't there in tests). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfReaderShots {

    @get:Rule
    val compose = createComposeRule()

    /** Pages like a printed book's: a cover, then text pages with numbers at the foot. */
    private class MadeUpPdf(override val count: Int) : PdfDocument {
        override fun size(index: Int) = 432 to 648

        private fun drawPage(c: Canvas, index: Int, w: Float, h: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (index == 0) {
                c.drawColor(0xFF2B3A55.toInt())
                p.color = 0xFFF3E7C9.toInt()
                p.textAlign = Paint.Align.CENTER
                p.textSize = w / 9f
                c.drawText("THE TIDE", w / 2, h * 0.38f, p)
                c.drawText("TABLES", w / 2, h * 0.48f, p)
                p.textSize = w / 18f
                c.drawText("A. N. AUTHOR", w / 2, h * 0.85f, p)
                return
            }
            c.drawColor(Color.WHITE)
            p.color = 0xFF222222.toInt()
            p.textSize = w / 34f
            val left = w * 0.12f
            var y = h * 0.12f
            if (index % 7 == 1) {
                p.textSize = w / 16f
                c.drawText("Chapter ${index / 7 + 1}", left, y + w / 16f, p)
                y += h * 0.12f
                p.textSize = w / 34f
            }
            val line = p.textSize * 1.5f
            var n = 0
            while (y < h * 0.86f) {
                val words = "The harbour lights came on one by one as the water rose over the stones"
                val cut = if (n % 9 == 8) words.length / 2 else words.length
                c.drawText(words.take(cut), left, y, p)
                y += line
                n++
            }
            p.textAlign = Paint.Align.CENTER
            c.drawText("${index + 1}", w / 2, h * 0.94f, p)
        }

        override fun bitmap(index: Int, w: Int, h: Int): Bitmap =
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { drawPage(Canvas(it), index, w.toFloat(), h.toFloat()) }

        override fun drawInto(index: Int, into: Bitmap, clip: Rect, transform: Matrix) {
            val c = Canvas(into)
            c.clipRect(clip)
            c.concat(transform)
            val (w, h) = size(index)
            drawPage(c, index, w.toFloat(), h.toFloat())
        }

        override fun close() = Unit
    }

    private fun shoot(name: String, two: Boolean) {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "pdf-shot").apply { mkdirs() }
        val book = LibraryBook(
            id = "pdf", meta = BookMetadata(title = "The Tide Tables", authors = listOf("A. N. Author")), collections = emptyList(),
            fileSize = 1, hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = dev.kobolibrary.app.library.BookSource.FILE, originalName = "tide.pdf", koboPath = null, sourceFormat = "PDF",
        )
        // Left at a chapter's start, on the right-hand page.
        dev.kobolibrary.app.reader.ReaderStore.savePdfPlace(dir, dev.kobolibrary.app.reader.PdfPlace(7, 120))
        val pages = PdfPages(MadeUpPdf(120))
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                PdfPagesView(
                    book = book, pages = pages, dir = dir,
                    s = ReaderSettings(spread = if (two) dev.kobolibrary.app.reader.ReaderSpread.TWO else dev.kobolibrary.app.reader.ReaderSpread.ONE),
                    background = ComposeColor(0xFFFBF8F1), controls = true, setControls = {}, onClose = {}, onReadAsText = {}, readingHere = null,
                )
            }
        }
        repeat(3) {
            Thread.sleep(400)
            compose.waitForIdle()
        }
        File("/home/claude/heads/shots").mkdirs()
        captureScreenRoboImage("/home/claude/heads/shots/$name.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletTwoPages() = shoot("pdf_tablet", two = true)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneOnePage() = shoot("pdf_phone", two = false)
}
