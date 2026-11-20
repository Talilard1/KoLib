package dev.kolib.app.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.TestBooks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.UUID

/** Scrolling through chapters, and autoscroll: what the page is told, and what's kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderScrollingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun scrollingIsToldToThePageWithBandsForTheMarginItems() {
        val s = ReaderSettings(flow = ReaderFlow.SCROLL, pageTurn = PageTurn.CURL)
        val js = JSONObject(s.toJs("en"))
        assertEquals("scroll", js.getString("flow"))
        // No turns drawn over the page while scrolling.
        assertEquals("slide", js.getString("turn"))
        // The usual margins have items at the top and the bottom: a band at each.
        assertEquals(ReaderSettings.BAND, js.getInt("bandTop"))
        assertEquals(ReaderSettings.BAND, js.getInt("bandBottom"))
        // Nothing at the top: no band there, so the text goes up to the edge.
        val noTop = s.copy(runningHeads = RunningHeads.OFF, marginItems = MarginItem.DEFAULTS + (MarginItem.CHAPTER to MarginSpot.OFF))
        assertEquals(0, JSONObject(noTop.toJs("en")).getInt("bandTop"))
        // Pages: no bands, and the turn as chosen.
        val pages = JSONObject(s.copy(flow = ReaderFlow.PAGES).toJs("en"))
        assertEquals("pages", pages.getString("flow"))
        assertEquals(0, pages.getInt("bandTop"))
        assertEquals("curl", pages.getString("turn"))
        // Moving a margin item changes a scrolled page (its bands), not a page of a book being turned.
        assertNotEquals(s.pageOnly(), noTop.pageOnly())
        assertEquals(s.copy(flow = ReaderFlow.PAGES).pageOnly(), noTop.copy(flow = ReaderFlow.PAGES).pageOnly())
        // Autoscroll's speed and timings aren't part of the page.
        assertEquals(s.pageOnly(), s.copy(autoWpm = 400, autoEnd = AutoEnd.STOP, autoResume = AutoResume.LATER).pageOnly())
    }

    @Test
    fun withOnlyTheProgressBarAtTheFootTheTextFadesOutAboveIt() {
        val bare = ReaderSettings(
            flow = ReaderFlow.SCROLL, runningHeads = RunningHeads.OFF, marginItems = MarginItem.entries.associateWith { MarginSpot.OFF },
        )
        // Nothing round the page but the progress bar: a band at the foot all the same, so the text doesn't run under the bar.
        assertEquals(ProgressLine.BOOK, bare.progressLine)
        assertEquals(ReaderSettings.BAR_BAND, JSONObject(bare.toJs("en")).getInt("bandBottom"))
        assertEquals(0, JSONObject(bare.toJs("en")).getInt("bandTop"))
        // No bar either: the text has the whole screen.
        assertEquals(0, JSONObject(bare.copy(progressLine = ProgressLine.OFF).toJs("en")).getInt("bandBottom"))
        // Something at the foot as well: its own, deeper band.
        val withPercent = bare.copy(marginItems = bare.marginItems + (MarginItem.entries.first() to MarginSpot.BOTTOM_RIGHT))
        assertEquals(ReaderSettings.BAND, JSONObject(withPercent.toJs("en")).getInt("bandBottom"))
        // Pages have no bands.
        assertEquals(0, JSONObject(bare.copy(flow = ReaderFlow.PAGES).toJs("en")).getInt("bandBottom"))
    }

    @Test
    fun scrollingAndAutoscrollSettingsAreKept() {
        ReaderSettingsStore(context).update {
            it.copy(flow = ReaderFlow.SCROLL, autoWpm = 310, autoEnd = AutoEnd.GO_ON, autoResume = AutoResume.STOP)
        }
        val again = ReaderSettingsStore(context).settings.value
        assertEquals(ReaderFlow.SCROLL, again.flow)
        assertEquals(310, again.autoWpm)
        assertEquals(AutoEnd.GO_ON, again.autoEnd)
        assertEquals(AutoResume.STOP, again.autoResume)
        // The progress line along the foot of the page: the whole book to start with, and kept as chosen.
        assertEquals(ProgressLine.BOOK, ReaderSettings().progressLine)
        ReaderSettingsStore(context).update { it.copy(progressLine = ProgressLine.CHAPTER) }
        assertEquals(ProgressLine.CHAPTER, ReaderSettingsStore(context).settings.value.progressLine)
        // It's drawn by the app over the page, so the page isn't laid out again for it.
        assertEquals(again.pageOnly(), again.copy(progressLine = ProgressLine.OFF).pageOnly())
        // What the page is told for each choice.
        assertEquals(listOf(0, 3, 10, -1), AutoEnd.entries.map { it.seconds })
        assertEquals(listOf(0, 2, 5, -1), AutoResume.entries.map { it.seconds })
    }

    @Test
    fun theBarsCanBeDimmedAndThePageIsToldTheirColour() {
        // Full strength: the text's colour.
        val full = ReaderSettings(theme = ReaderTheme.BLACK, barShade = BarShade.TEXT)
        assertEquals(BarShade.DIM, ReaderSettings().barShade) // dimmed to start with
        assertEquals(0xFFC4C0B8, full.barColor)
        assertEquals("#c4c0b8", JSONObject(full.toJs("en")).getString("barColor"))
        // Dimmed: a solid colour about a third of the way from the page's colour to the text's.
        val dim = full.copy(barShade = BarShade.DIM)
        assertEquals(0xFF454340, dim.barColor)
        assertEquals("#454340", JSONObject(dim.toJs("en")).getString("barColor"))
        // On a light page it's a light grey, not a dark one.
        val paper = ReaderSettings(theme = ReaderTheme.PAPER, barShade = BarShade.DIM)
        assertTrue((paper.barColor shr 16 and 0xFF) > 0xA0)
        // The page draws autoscroll's bar in it, so it's told when the shade changes.
        assertNotEquals(full.pageOnly(), dim.pageOnly())
        // And it's kept.
        ReaderSettingsStore(context).update { it.copy(barShade = BarShade.DIM) }
        assertEquals(BarShade.DIM, ReaderSettingsStore(context).settings.value.barShade)
    }

    @Test
    fun autoscrollCarriesOnIntoTheNextChapterAndStopsAtTheEndOfTheBook() {
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            val settings = ReaderSettings(flow = ReaderFlow.SCROLL, pageTurn = PageTurn.CURL, autoWpm = 270, autoEnd = AutoEnd.WAIT_LONG)
            val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, settings, context.assets)
            // Scrolling: no page turns drawn by the app.
            assertEquals(PageTurn.SLIDE, c.turnPages.pageTurn)
            c.start()
            ShadowLooper.idleMainLooper()
            val first = JSONObject(c.startState)
            assertFalse("not running yet", first.has("auto"))
            assertFalse("nothing before the first chapter", first.getJSONObject("chapters").getBoolean("hasPrev"))
            assertTrue(first.getJSONObject("chapters").getBoolean("hasNext"))

            c.startAutoscroll()
            assertTrue(c.autoscroll)
            c.nextChapter()
            ShadowLooper.idleMainLooper()
            val next = JSONObject(c.startState)
            val auto = next.getJSONObject("auto")
            assertTrue("the next chapter carries on", auto.getBoolean("on"))
            assertEquals(270, auto.getInt("wpm"))
            assertEquals(10, auto.getInt("endPause"))
            assertTrue("after a moment at its start", auto.getInt("startDelay") > 0)
            assertTrue(next.getJSONObject("chapters").getBoolean("hasPrev"))

            // A new speed while it runs is passed on to chapters still to come.
            c.applySettings(c.settings.copy(autoWpm = 350))
            assertEquals(350, JSONObject(c.startState).getJSONObject("auto").getInt("wpm"))

            // On to the end of the book: there it stops.
            repeat(20) { if (!c.atEnd) c.nextChapter() }
            assertTrue(c.atEnd)
            assertFalse(c.autoscroll)
            assertFalse(JSONObject(c.startState).has("auto"))

            // Paused by hand: chapters opened after don't start it again.
            c.dismissEnd()
            c.startAutoscroll()
            c.stopAutoscroll()
            c.previousChapter()
            ShadowLooper.idleMainLooper()
            assertFalse(JSONObject(c.startState).has("auto"))
            c.release()
        }
    }
}
