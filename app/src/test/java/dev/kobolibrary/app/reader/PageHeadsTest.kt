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
import org.junit.Assert.assertFalse
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
 * The page headings, as a printed book has them: each page numbered on its own (two side by side are two
 * pages), the book's title without what's only there to sort it, and room kept for them over the text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageHeadsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aNumberOnlyThereToSortTheFilesIsLeftOffTheTitle() {
        val cases = mapOf(
            "01 The Eye of the World" to "The Eye of the World",
            "00 Prologue" to "Prologue",
            "06 - Lord of Chaos" to "Lord of Chaos",
            "6 - Lord of Chaos" to "Lord of Chaos",
            "6. Lord of Chaos" to "Lord of Chaos",
            "[06] Lord of Chaos" to "Lord of Chaos",
            "(6) Lord of Chaos" to "Lord of Chaos",
            "03_The Dragon Reborn" to "The Dragon Reborn",
            "01-The Hobbit" to "The Hobbit",
            "01: The Fellowship" to "The Fellowship",
            "Lord of Chaos (The Wheel of Time, Book 6)" to "Lord of Chaos",
            "06 Lord of Chaos (Wheel of Time #6)" to "Lord of Chaos",
            // Numbers that are the title, or part of it, stay.
            "1984" to "1984",
            "2001: A Space Odyssey" to "2001: A Space Odyssey",
            "12 Rules for Life" to "12 Rules for Life",
            "20,000 Leagues Under the Sea" to "20,000 Leagues Under the Sea",
            "11/22/63" to "11/22/63",
            "1Q84" to "1Q84",
            "1-800-Where-R-You" to "1-800-Where-R-You",
            "1 Corinthians" to "1 Corinthians",
            "Selected Poems (1950)" to "Selected Poems (1950)",
            "The Hobbit" to "The Hobbit",
        )
        for ((title, shown) in cases) assertEquals(title, shown, HeadTitles.book(title))
        // A number the same as the book's in its series is there to sort it.
        assertEquals("Lord of Chaos", HeadTitles.book("6 Lord of Chaos", seriesIndex = 6.0))
        assertEquals("12 Rules for Life", HeadTitles.book("12 Rules for Life", seriesIndex = 3.0))
        // All in capitals: all small capitals, so it isn't shouted.
        assertTrue(HeadTitles.smallCapsFeatures("LORD OF CHAOS").contains("c2sc"))
        assertFalse(HeadTitles.smallCapsFeatures("Lord of Chaos").contains("c2sc"))
    }

    @Test
    fun theHeadingsTakeTheirEndOfThePageAndWhatTheyShow() {
        val before = ReaderSettings(runningHeads = RunningHeads.OFF, marginItems = MarginItem.DEFAULTS + mapOf(
            MarginItem.CHAPTER to MarginSpot.TOP_CENTER, MarginItem.PAGE to MarginSpot.BOTTOM_LEFT, MarginItem.CLOCK to MarginSpot.TOP_RIGHT,
        ))
        val on = before.withHeads(RunningHeads.TOP)
        assertEquals(RunningHeads.TOP, on.runningHeads)
        // What the headings show isn't shown twice, and nothing is left at the top for them to sit on.
        MarginItem.IN_HEADINGS.forEach { assertEquals(it.name, MarginSpot.OFF, on.spotOf(it)) }
        assertEquals(MarginSpot.BOTTOM_RIGHT, on.spotOf(MarginItem.CLOCK))
        assertTrue(MarginItem.entries.none { on.shownSpotOf(it).top })
        // At the bottom instead: the time and the rest go to the top.
        val below = on.withHeads(RunningHeads.BOTTOM)
        assertEquals(MarginSpot.TOP_RIGHT, below.spotOf(MarginItem.CLOCK))
        assertEquals(MarginSpot.TOP_CENTER, below.spotOf(MarginItem.BATTERY))
        // Off: the chapter's title and the page number come back where they were.
        val off = on.withHeads(RunningHeads.OFF)
        assertEquals(MarginSpot.TOP_CENTER, off.spotOf(MarginItem.CHAPTER))
        assertEquals(MarginSpot.BOTTOM_LEFT, off.spotOf(MarginItem.PAGE))
        // To start with, they're on, at the top, with nothing else at the top.
        val fresh = ReaderSettings()
        assertEquals(RunningHeads.TOP, fresh.runningHeads)
        assertTrue(MarginItem.entries.none { fresh.spotOf(it).top })
    }

    @Test
    fun roomIsKeptOverTheTextAndBelowACameraCutout() {
        val s = ReaderSettings()
        // Headings at the top; the time and the rest at the bottom, over the progress bar.
        assertEquals(MarginRoom(top = 12 + 18 + 10, bottom = 10 + 18 + 10), s.marginRoom())
        // Under a camera's cutout 32dp deep, the top row goes below it.
        assertEquals(34 + 18 + 10, s.marginRoom(cutoutTop = 32).top)
        val js = JSONObject(s.toJs("en", cutoutTop = 32))
        assertEquals(62, js.getInt("minTop"))
        assertEquals(38, js.getInt("minBottom"))
        // Nothing at the bottom, no room kept there; scrolling keeps its own bands instead.
        val bare = s.copy(marginItems = MarginItem.entries.associateWith { MarginSpot.OFF }, progressLine = ProgressLine.OFF)
        assertEquals(0, bare.marginRoom().bottom)
        assertEquals(MarginRoom(), s.copy(flow = ReaderFlow.SCROLL).marginRoom(cutoutTop = 32))
    }

    @Test
    fun settingsFromBeforeTheHeadingsAreBroughtToThem() {
        val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("marginItems", "BOOK=OFF,CHAPTER=TOP_CENTER,PAGE=BOTTOM_LEFT,BOOK_PAGE=OFF,PAGES_LEFT=TOP_LEFT,TIME_LEFT=OFF,CLOCK=BOTTOM_CENTER,BATTERY=BOTTOM_CENTER,PERCENT=BOTTOM_RIGHT")
            .putString("theme", "NIGHT")
            .commit()
        val s = ReaderSettingsStore(context).settings.value
        assertEquals(RunningHeads.TOP, s.runningHeads)
        assertEquals(ReaderTheme.NIGHT, s.theme)
        assertEquals(MarginSpot.OFF, s.spotOf(MarginItem.CHAPTER))
        assertEquals(MarginSpot.OFF, s.spotOf(MarginItem.PAGE))
        assertEquals(MarginSpot.BOTTOM_LEFT, s.spotOf(MarginItem.PAGES_LEFT))
        assertEquals(MarginSpot.BOTTOM_CENTER, s.spotOf(MarginItem.CLOCK))
        assertEquals("TOP", prefs.getString("heads", null))
        // Chosen since: kept as chosen.
        ReaderSettingsStore(context).update { it.withHeads(RunningHeads.OFF) }
        assertEquals(RunningHeads.OFF, ReaderSettingsStore(context).settings.value.runningHeads)
        prefs.edit().clear().commit()
    }

    // ---- numbering each page, two side by side ----------------------------------------------------

    private class FakeCounter(private val pages: (String) -> Int, var layout: String) : ChapterCounter {
        override suspend fun count(path: String, start: String): ChapterCounter.Counted = ChapterCounter.Counted(1, layout, pages(path))
        override fun close() {}
    }

    private val layout = "1280|800|2|0|38|40/40|0|0|1|null|null|book|book|en"
    private val counts = mapOf("ch1.xhtml" to 7, "ch2.xhtml" to 12, "ch3.xhtml" to 9, "notes.xhtml" to 2)
    private fun pagesOf(path: String) = counts[path.substringAfterLast('/')] ?: 3

    private fun spread(page: Int, screens: Int, columns: Int) = JSONObject()
        .put("page", page).put("pages", screens).put("columns", columns).put("spread", true)
        .put("progress", if (screens > 1) page.toDouble() / (screens - 1) else 0.0)
        .put("anchor", "1/$page:0").put("snippet", "On screen $page").put("layout", layout)
        .put("frame", JSONObject().put("w", 1280).put("h", 800).put("perScreen", 2).put("padX", 38).put("col", 564).put("padTop", 40).put("padBottom", 40))
        .put("picture", false)
        .toString()

    private fun one(page: Int, pages: Int) = JSONObject()
        .put("page", page).put("pages", pages).put("columns", pages).put("spread", false)
        .put("progress", if (pages > 1) page.toDouble() / (pages - 1) else 0.0)
        .put("anchor", "1/$page:0").put("snippet", "On page $page").put("layout", layout.replace("|2|", "|1|"))
        .toString()

    private fun settle(done: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(600) {
            if (done()) return
            looper.idleFor(Duration.ofMillis(100))
            Thread.sleep(2)
        }
        fail("The reader never got there")
    }

    private fun ReaderController.index(name: String) = chapters.indexOfFirst { it.path.endsWith(name) }

    @Test
    fun twoPagesSideBySideAreNumberedEachOnTheirOwn() {
        val book = TestBooks.garden(File(context.cacheDir, "heads-${UUID.randomUUID()}.epub"))
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "01 The Garden", "en", null, ReaderSettings(), context.assets)
            val fake = FakeCounter(::pagesOf, layout)
            c.counter = fake
            settle { c.lengthsCounted }
            val ch1 = c.index("ch1.xhtml")
            c.open(ch1, JSONObject().put("type", "start"))
            // Chapter 1 is 7 pages: 4 screens, the last with text on its left-hand page only.
            c.readied(spread(page = 1, screens = 4, columns = 7))
            settle { c.bookPages != null }
            val before = c.bookPages!!.before(ch1)
            assertEquals(7, c.bookPages!!.counts[ch1])
            assertEquals(before + 3, c.bookPage)
            assertEquals(before + 4, c.bookPageLast)
            assertEquals("Pages ${before + 3}–${before + 4}", c.pagesLabel(c.bookPage!!, c.bookPageLast))
            c.shownPages.let { shown ->
                assertEquals(2, shown.size)
                assertEquals(ShownPage(0, before + 3, PageSide.LEFT, opening = false), shown[0])
                assertEquals(ShownPage(1, before + 4, PageSide.RIGHT, opening = false), shown[1])
            }
            // The last screen: one page, on the left.
            c.readied(spread(page = 3, screens = 4, columns = 7))
            assertEquals(before + 7, c.bookPage)
            assertEquals(before + 7, c.bookPageLast)
            assertEquals(listOf(ShownPage(0, before + 7, PageSide.LEFT, opening = false)), c.shownPages)
            // The first: the chapter's opening page, then the next.
            c.readied(spread(page = 0, screens = 4, columns = 7))
            assertEquals(listOf(true, false), c.shownPages.map { it.opening })
            // The headings' titles: the book's without its sorting number, and the chapter's from the contents.
            assertEquals("The Garden", c.headBookTitle)
            assertEquals(c.chapterTitle, c.headChapterTitle)
            assertTrue(c.headChapterTitle.isNotBlank())

            // The progress bar names a page, and goes to the screen it's on.
            for (f in listOf(0.3f, 0.62f)) {
                val named = c.bookPageAt(f)!!
                c.goToBookProgress(f)
                val target = JSONObject(c.startState).getJSONObject("target")
                if (target.getString("type") != "page") continue
                val landed = c.chapterIndex
                val total = pagesOf(c.chapters[landed].path)
                val screens = (total + 1) / 2
                c.readied(spread(target.getInt("value"), screens, total))
                assertTrue("bar at $f named $named, shows ${c.bookPage}–${c.bookPageLast}", named == c.bookPage || named == c.bookPageLast)
            }

            // One page at a time, it's a left-hand page when its number is even, as in print.
            fake.layout = layout.replace("|2|", "|1|")
            c.open(ch1, JSONObject().put("type", "start"))
            c.readied(one(page = 2, pages = 7))
            assertNull("counted again at the new layout", c.bookPages)
            settle { c.bookPages != null }
            val n = c.bookPage!!
            assertEquals(n, c.bookPageLast)
            assertEquals(listOf(ShownPage(0, n, if (n % 2 == 0) PageSide.LEFT else PageSide.RIGHT, opening = false)), c.shownPages)
            c.release()
        }
    }
}
