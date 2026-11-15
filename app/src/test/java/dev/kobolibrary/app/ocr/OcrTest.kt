package dev.kobolibrary.app.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.convert.EpubOut
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.ImportOutcome
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.reader.ReaderStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Text recognition, from Tesseract's hOCR of four made-up pages (tools/ocr/make_fixtures.py): a chapter's
 * first page, a page with a running head that carries a paragraph on, two columns like a dictionary's, and
 * a picture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(context.cacheDir, "ocr-" + UUID.randomUUID())

    private fun hocr(n: Int): String = javaClass.getResource("/ocr/page$n.hocr")!!.readText()
    private fun page(n: Int): OcrPage = Hocr.parse(hocr(n), 300)
    private val pages by lazy { (1..4).map { page(it) } }
    private val texts by lazy { pages.map { OcrBook.PageText.of(it) } }

    @Test
    fun hocrIsReadIntoAreasParagraphsLinesAndWords() {
        val p = page(1)
        assertEquals(1700, p.width)
        assertEquals(2200, p.height)
        assertEquals("CHAPTER I.", p.lines.first().text)
        assertEquals(12, p.lines.size)
        assertTrue(p.lines.first().size > p.lines.last().size * 1.5)
        assertTrue(p.words.all { it.confidence in 0..100 && it.box.width > 0 })
        assertTrue("sure of most words", p.confidence > 85)
        // Quotation marks and other characters written as entities come out as themselves.
        assertTrue(p.words.any { it.text.startsWith("“") })
        // The running head over the dictionary's columns, taken for a heading; and the picture.
        assertEquals(OcrLine.Kind.HEADING, page(3).lines.first().kind)
        assertTrue(page(4).blocks.any { it.picture })
        assertTrue(OcrBook.isPicture(texts[3]))
        assertFalse(OcrBook.isPicture(texts[2]))
        // Entities and nested markup in a word.
        val made = Hocr.parse(
            "<div class='ocr_page' title='bbox 0 0 100 50'><div class='ocr_carea' title='bbox 1 1 99 49'><p class='ocr_par' title='bbox 1 1 99 49'>" +
                "<span class='ocr_line' title='bbox 1 1 99 20; x_size 18'><span class='ocrx_word' title='bbox 1 1 40 20; x_wconf 91'><strong>Tom&amp;Jerry&#39;s</strong></span>" +
                "<span class='ocrx_word' title=\"bbox 45 1 99 20; x_wconf 57\">&#x3b1;&lt;b</span></span></p></div></div>",
            300,
        )
        assertEquals(listOf("Tom&Jerry's", "α<b"), made.words.map { it.text })
        assertEquals(listOf(91, 57), made.words.map { it.confidence })
        assertEquals(18, made.lines.single().size)
    }

    @Test
    fun aPageKeepsAllItsDetailsWhenSavedAndRead() {
        val store = OcrStore(dir)
        store.begin(OcrStore.Settings(listOf("eng", "grc"), 300, 4))
        pages.forEachIndexed { i, p -> store.save(i, p) }
        store.save(4, OcrPage.blank(10, 20, 300))
        for (i in 0..3) assertEquals(pages[i], store.page(i))
        assertTrue(store.page(4)!!.blank)
        assertEquals(4, store.done(4))
        // Read another way (other languages), the pages read before don't count.
        store.begin(OcrStore.Settings(listOf("eng"), 300, 4))
        assertEquals(0, store.done(4))
        store.applied = OcrStore.Applied("SCAN", listOf("eng"), 5)
        assertEquals("SCAN", store.applied!!.was)
        store.applied = null
        assertNull(store.applied)
    }

    @Test
    fun theTextReadsOnAsInTheBook() {
        val items = OcrBook.items(texts)
        val paras = items.filterIsInstance<OcrBook.Item.Para>()
        assertEquals("CHAPTER I.", paras[0].text)
        assertEquals(2, paras[0].heading)
        assertEquals("Down the Rabbit-Hole", paras[1].text)
        assertEquals(3, paras[1].heading)
        assertTrue(paras[2].text.startsWith("Alice was beginning to get very tired"))
        assertEquals(0, paras[2].heading)
        // Carried on over the page, with the word broken at the end of a line mended, and the running head left out.
        val rabbit = paras.single { "considering in her own mind" in it.text }
        assertTrue(rabbit.text, "There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say" in rabbit.text)
        assertTrue(rabbit.text.endsWith("quite natural)."))
        assertTrue(paras.none { "ADVENTURES IN WONDERLAND" in it.text || "ἄγω" == it.text.takeLast(3) })
        assertTrue(paras.any { it.text.startsWith("In another moment down went Alice") })
        // The dictionary's entries, column by column, without the words over them.
        val entries = paras.filter { it.page == 2 }
        assertEquals(entries.joinToString("\n") { it.text }, 6, entries.size)
        assertTrue(entries.first().text.startsWith("ἀγαθός, good, brave"))
        assertTrue(entries.last().text.startsWith("ἀγών"))
        assertTrue(entries.none { it.text.contains("ἀγαθός -") })
        // The picture stays a picture.
        val last = items.last()
        assertTrue(last is OcrBook.Item.Picture && last.page == 3 && last.box == null)
    }

    @Test
    fun runningHeadsFeetAndPageNumbersAreLeftOut() {
        fun line(text: String, top: Int, size: Int = 30) =
            OcrBook.TextLine(text, Box(100, top, 900, top + size), size, OcrLine.Kind.TEXT, text.split(' ').size)
        val body = (0 until 12).map { line("Some ordinary words of the book's text, line $it", 300 + it * 60) }
        fun page(vararg extra: OcrBook.TextLine) = OcrBook.PageText(
            1000, 2000, false, 2000, 90,
            listOf(OcrBook.TextBlock(Box(0, 0, 1000, 2000), (extra.toList() + body).map { OcrBook.TextPara(listOf(it)) }, false)),
        )
        val pages = listOf(
            page(line("THE HISTORIES", 60), line("11", 1900)),
            page(line("12 BOOK ONE", 60), line("A note at the foot, once only.", 1880, 24)),
            page(line("THE HISTORIES", 60), line("xiii", 1900)),
            page(line("THE HISTORIES", 60)),
        )
        val edges = OcrBook.edges(pages)
        assertEquals(setOf("THE HISTORIES", "11"), edges[0].map { it.text }.toSet())
        assertEquals(setOf("12 BOOK ONE"), edges[1].map { it.text }.toSet())
        assertEquals(setOf("THE HISTORIES", "xiii"), edges[2].map { it.text }.toSet())
        val kept = OcrBook.items(pages).filterIsInstance<OcrBook.Item.Para>().map { it.text }
        assertTrue(kept.contains("A note at the foot, once only."))
        assertTrue(kept.none { it == "THE HISTORIES" || it == "11" })
    }

    @Test
    fun chaptersFollowTheBookmarksElseTheBigHeadingsElseEveryTwentyPages() {
        fun para(page: Int, text: String, heading: Int = 0) = OcrBook.Item.Para(page, text, heading)
        val items = (0 until 45).map { para(it, "Text on page $it") }
        // Bookmarks: those at the top, or under a single entry for the whole book.
        val marks = listOf(Triple("The Book", 0, 0), Triple("One", 2, 1), Triple("Two", 30, 1))
        assertEquals(listOf(2 to "One", 30 to "Two"), OcrBook.chapters(items, marks, 45))
        val withHeadings = items.toMutableList<OcrBook.Item>().apply {
            add(5, para(5, "BOOK I", 2))
            add(26, para(25, "BOOK II", 2))
        }
        assertEquals(listOf(5 to "BOOK I", 26 to "BOOK II"), OcrBook.chapters(withHeadings, emptyList(), 45))
        assertEquals(listOf(0 to "Pages 1–20", 20 to "Pages 21–40", 40 to "Pages 41–45"), OcrBook.chapters(items, emptyList(), 45))
        assertEquals(emptyList<Pair<Int, String?>>(), OcrBook.chapters(items.take(10), emptyList(), 10))
    }

    @Test
    fun wordsBrokenOverLinesAreMended() {
        assertEquals("nothing so", OcrBook.join("noth-", "ing so"))
        assertEquals("ἀγαθός", OcrBook.join("ἀγα-", "θός"))
        assertEquals("well- Known", OcrBook.join("well-", "Known"))
        assertEquals("1914- 1918", OcrBook.join("1914-", "1918"))
        assertEquals("a b", OcrBook.join("a", "b"))
    }

    /** The book's pages in reading order: each one's path and text. */
    private fun pagesOf(epub: EpubFile): List<Pair<String, String>> =
        epub.opf.spineRefs().mapNotNull { ref -> epub.opf.item(ref.idref)?.let { val p = epub.resolve(it.href); p to (epub.readText(p) ?: "") } }

    private object White : PagePictures {
        val jpeg: ByteArray by lazy {
            val b = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
            ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        }
        override fun page(index: Int, maxSide: Int) = jpeg
        override fun part(index: Int, box: Box, width: Int, height: Int, maxSide: Int) = jpeg
    }

    @Test
    fun theTextIsMadeIntoABook() {
        val target = File(dir.apply { mkdirs() }, "made.epub")
        OcrBook.write(BookMetadata(title = "Alice", authors = listOf("Lewis Carroll"), language = "en"), texts, emptyList(), White, target)
        EpubFile.open(target).use { epub ->
            val spine = pagesOf(epub)
            assertTrue(spine.first().first.contains("cover"))
            val text = spine.drop(1).joinToString("\n") { it.second }
            assertTrue(text.contains("<h2>CHAPTER I.</h2>"))
            assertTrue(text.contains("<h3>Down the Rabbit-Hole</h3>"))
            assertTrue(text.contains("There was nothing so very remarkable"))
            assertTrue(text.contains("<img src=\"../images/page0004.jpg\""))
            assertEquals("Alice", epub.opf.readMetadata().title)
            assertNotNull(epub.findCover())
        }
    }

    /** Reads pages by looking up their hOCR: page 3 is blank. */
    private class Pages(override val count: Int) : OcrPageSource {
        val drawn = AtomicInteger()
        override fun gray(index: Int, dpi: Int): GrayPage {
            drawn.incrementAndGet()
            return GrayPage(byteArrayOf(index.toByte()), 1, 1, dpi, blank = index == 2)
        }
    }

    private inner class Engine(val sizes: Int? = null, val holdOn: CountDownLatch? = null) : OcrEngine {
        val stopped = CountDownLatch(1)
        override fun read(gray: ByteArray, width: Int, height: Int, dpi: Int): String {
            if (holdOn != null && gray[0].toInt() == 1) {
                // Page 2 takes until it's stopped. (Long waits: a test process short of memory can stall for seconds.)
                holdOn.countDown()
                stopped.await(120, TimeUnit.SECONDS)
                return ""
            }
            reads.incrementAndGet()
            val n = listOf(1, 2, 3, 3, 4)[gray[0].toInt() % 5]
            val h = hocr(n)
            return if (sizes == null) h else h.replace(Regex("x_size [0-9.]+"), "x_size $sizes")
        }
        override fun stop() = stopped.countDown()
        override fun close() = Unit
    }

    private val reads = AtomicInteger()

    @Test
    fun pagesAreReadSeveralAtOnceAndKept() = runBlocking {
        val store = OcrStore(dir)
        val source = Pages(5)
        store.begin(OcrStore.Settings(listOf("eng"), 300, 5))
        val seen = ArrayList<Pair<Int, Int>>()
        OcrRun(source, store, { Engine() }, 3).run(300) { done, total -> synchronized(seen) { seen += done to total } }
        assertEquals(5, store.done(5))
        assertEquals(5 to 5, seen.maxByOrNull { it.first })
        assertTrue(store.page(2)!!.blank)
        assertEquals("CHAPTER I.", store.page(0)!!.lines.first().text)
        assertEquals(4, reads.get())
        // Carrying on reads only what's missing.
        File(dir, "p00004.json").delete()
        OcrRun(source, store, { Engine() }, 2).run(300) { _, _ -> }
        assertEquals(5, reads.get())
        assertEquals(5, store.done(5))
    }

    @Test
    fun stoppingKeepsThePagesDoneAndStopsTheOneUnderWay() = runBlocking {
        val store = OcrStore(dir)
        store.begin(OcrStore.Settings(listOf("eng"), 300, 5))
        val started = CountDownLatch(1)
        val engine = Engine(holdOn = started)
        val run = async(kotlinx.coroutines.Dispatchers.Default) { OcrRun(Pages(5), store, { engine }, 1).run(300) { _, _ -> } }
        assertTrue(started.await(60, TimeUnit.SECONDS))
        run.cancel()
        try {
            withTimeout(60_000) { run.await() }
            fail("expected it to stop")
        } catch (e: CancellationException) {
            // stopped
        }
        assertEquals("the page under way was told to stop", 0L, engine.stopped.count)
        assertTrue(store.has(0))
        assertFalse(store.has(1))
        assertFalse(store.has(2))
    }

    @Test
    fun smallPrintIsDrawnBigger() {
        val source = Pages(5)
        val store = OcrStore(dir)
        assertEquals(300, OcrRun(source, store, { Engine() }, 1).chooseDpi(Engine(sizes = 40)))
        assertEquals(600, OcrRun(source, store, { Engine() }, 1).chooseDpi(Engine(sizes = 20)))
        assertEquals(400, OcrRun(source, store, { Engine() }, 1).chooseDpi(Engine(sizes = 30)))
    }

    @Test
    fun theLibraryTakesTheTextAndCanGoBackToTheScan() = runBlocking {
        val lib = Library(context, File(context.filesDir, "lib-" + UUID.randomUUID())).apply { load() }
        // A scanned book as the library has one: a stand-in EPUB, and the PDF it came as.
        val standIn = File(dir.apply { mkdirs() }, "standin.epub")
        EpubOut(BookMetadata(title = "Alice", authors = listOf("Lewis Carroll"), language = "en")).apply {
            page("Alice", "<p>This book's pages are pictures, so it reads as the PDF it came as.</p>")
            write(standIn)
        }
        val pdf = File(dir, "alice.pdf").apply { writeText("%PDF-1.4 made up") }
        val added = lib.import("alice.pdf", { standIn.inputStream() }, BookSource.FILE, made = true, attach = pdf to "SCAN") as ImportOutcome.Added
        val id = added.book.id
        assertTrue(added.book.isScan)
        assertFalse(lib.canGoBackToScan(id))

        val made = File(dir, "made.epub")
        OcrBook.write(BookMetadata(title = "Alice"), texts, emptyList(), White, made)
        lib.ocrStore(id).begin(OcrStore.Settings(listOf("eng", "grc"), 300, 4))
        val recognised = lib.applyRecognisedText(id, made, listOf("eng", "grc"))
        assertEquals("OCR", recognised.sourceFormat)
        assertTrue(recognised.isRecognised && recognised.isPdf && !recognised.isScan)
        assertEquals(listOf("Lewis Carroll"), recognised.meta.authors) // the book's own details are kept
        assertTrue((recognised.words ?: 0) > 300)
        assertTrue(lib.canGoBackToScan(id))
        assertEquals(true, ReaderStore.pdfPlace(lib.bookDir(id))?.asText)
        assertTrue(lib.sourceFile(id)!!.exists())

        val back = lib.goBackToScan(id)
        assertEquals("SCAN", back.sourceFormat)
        assertTrue(back.isScan)
        assertFalse(lib.canGoBackToScan(id))
        EpubFile.open(lib.epubFile(id)).use { e ->
            val text = pagesOf(e).joinToString { it.second }
            assertTrue(text.contains("This book's pages are pictures"))
        }
        assertEquals(false, ReaderStore.pdfPlace(lib.bookDir(id))?.asText)
        // What was read is kept, should it be wanted again.
        assertNotNull(lib.ocrStore(id).settings)
    }
}
