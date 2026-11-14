package dev.kobolibrary.app.reader

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.TestBooks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.util.UUID

/**
 * Page numbers through the whole book come from every chapter's pages, counted away from the screen,
 * so a page has the same number however the reader got there: turning to it, jumping with the progress
 * bar, or going to and fro with the button that goes back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookPageNumbersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Counts each chapter as the hidden WebView would, and remembers what it was asked. */
    private class FakeCounter(private val pages: (String) -> Int, private val layout: String) : ChapterCounter {
        val asked = mutableListOf<String>()
        override suspend fun count(path: String, start: String): ChapterCounter.Counted {
            asked += path
            return ChapterCounter.Counted(pages(path), layout)
        }
        override fun close() {}
    }

    private val layout = "1280|800|2|0|48|44|0|0|1|null|null|book|book|en"

    private fun report(page: Int, pages: Int) =
        JSONObject().put("page", page).put("pages", pages).put("progress", if (pages > 1) page.toDouble() / (pages - 1) else 0.0)
            .put("anchor", "1/$page:0").put("snippet", "On page $page").put("layout", layout).toString()

    /** Lets the reader's own work run (on the main thread, and away from it) until [done]. */
    private fun settle(done: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(600) {
            if (done()) return
            looper.idleFor(Duration.ofMillis(100))
            Thread.sleep(2)
        }
        fail("The reader never got there")
    }

    private val counts = mapOf("ch1.xhtml" to 7, "ch2.xhtml" to 12, "ch3.xhtml" to 9, "notes.xhtml" to 2)
    private fun pagesOf(path: String) = counts[path.substringAfterLast('/')] ?: 3

    private fun ReaderController.index(name: String) = chapters.indexOfFirst { it.path.endsWith(name) }

    private fun ReaderController.openAt(name: String, page: Int, pages: Int) {
        open(index(name), JSONObject().put("type", "start"))
        reported(report(page, pages))
    }

    @Test
    fun pageNumbersStayTheSameWhereverTheReaderGoes() {
        val book = TestBooks.garden(File(context.cacheDir, "pn-${UUID.randomUUID()}.epub"))
        val storeDir = File(context.cacheDir, "pn-store-${UUID.randomUUID()}")
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "Garden", "en", ReaderStore(storeDir), ReaderSettings(), context.assets)
            val fake = FakeCounter(::pagesOf, layout)
            c.counter = fake
            settle { c.lengthsCounted }

            c.openAt("ch1.xhtml", 3, 7)
            // Until every chapter is counted there are no page numbers through the book: none, rather than ones that change.
            assertNull(c.bookPage)
            // Counted, and the counts kept (which finishes just after they're there to use).
            settle { c.bookPages != null && !c.countingPages }
            val bp = c.bookPages!!
            val ch1 = c.index("ch1.xhtml")
            val ch2 = c.index("ch2.xhtml")
            val onCh1Page4 = bp.before(ch1) + 4
            assertEquals(onCh1Page4, c.bookPage)

            // Off to another chapter, which comes out a page longer on screen than counted: nothing renumbers.
            c.openAt("ch2.xhtml", 5, 13)
            assertEquals(bp.total, c.bookPages!!.total)
            assertEquals(bp.before(ch2) + 6, c.bookPage)
            // And back: the same page, the same number.
            c.openAt("ch1.xhtml", 3, 7)
            assertEquals(onCh1Page4, c.bookPage)

            // The progress bar names a page while it's dragged, and goes to that very page.
            for (f in listOf(0.2f, 0.47f, 0.81f)) {
                val named = c.bookPageAt(f)!!
                c.goToBookProgress(f)
                val target = JSONObject(c.startState).getJSONObject("target")
                // To the page named; or, on a chapter's opening page, to the start of the chapter it opens.
                val type = target.getString("type")
                assertTrue(type, type == "page" || type == "start")
                val landed = c.chapterIndex
                c.reported(report(if (type == "page") target.getInt("value") else 0, pagesOf(c.chapters[landed].path)))
                assertEquals("bar at $f", named, c.bookPage)
            }

            // To and fro with the button that goes back: each place keeps its number both ways.
            c.openAt("ch1.xhtml", 3, 7)
            c.goToBookProgressFromBar(0.81f)
            assertEquals(onCh1Page4, c.barReturnBookPage)
            val target = JSONObject(c.startState).getJSONObject("target")
            c.reported(report(if (target.getString("type") == "page") target.getInt("value") else 0, pagesOf(c.chapters[c.chapterIndex].path)))
            val far = c.bookPage!!
            c.swapWithBarReturn()
            assertEquals(far, c.barReturnBookPage)
            c.openAt("ch1.xhtml", 3, 7)
            assertEquals(onCh1Page4, c.bookPage)
            c.swapWithBarReturn()
            assertEquals(onCh1Page4, c.barReturnBookPage)

            // Each chapter was counted once.
            assertEquals(fake.asked.distinct(), fake.asked)
            c.release()
        }

        // Opened again at the same size: the counts kept from before, without counting again.
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "Garden", "en", ReaderStore(storeDir), ReaderSettings(), context.assets)
            val fake = FakeCounter({ 1 }, layout)
            c.counter = fake
            settle { c.lengthsCounted }
            c.openAt("ch1.xhtml", 3, 7)
            settle { c.bookPages != null }
            assertTrue(fake.asked.isEmpty())
            assertNotNull(c.bookPage)
            assertEquals(c.bookPages!!.before(c.index("ch1.xhtml")) + 4, c.bookPage)
            c.release()
        }
    }
}
