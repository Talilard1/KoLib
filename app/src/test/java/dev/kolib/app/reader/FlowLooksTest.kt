package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pages and scrolling each keep a look of their own: going over to one brings its look back (the first time, it starts
 * as the other's), and changing one leaves the other. What isn't the look (the page colour, the controls) is shared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowLooksTest {

    @Test
    fun theFirstTimeScrollingStartsAsPagesAre() {
        val pages = ReaderSettings(fontScale = 1.1f, margins = ReaderMargins.NARROW)
        val scrolling = pages.withFlow(ReaderFlow.SCROLL)
        assertEquals(ReaderFlow.SCROLL, scrolling.flow)
        assertEquals(1.1f, scrolling.fontScale)
        assertEquals(ReaderMargins.NARROW, scrolling.margins)
        assertNotNull("pages' look is kept for coming back to", scrolling.otherLook)
        assertEquals("the same flow changes nothing", pages, pages.withFlow(ReaderFlow.PAGES))
    }

    @Test
    fun eachKeepsItsOwnLookAndTheRestIsShared() {
        val pages = ReaderSettings(fontScale = 1.1f, margins = ReaderMargins.NARROW, lineSpacing = 1.4f)
        // Scrolling, set up differently.
        val scrolling = pages.withFlow(ReaderFlow.SCROLL).copy(fontScale = 1.6f, margins = ReaderMargins.WIDE, theme = ReaderTheme.NIGHT)
        // Back to pages: its own look, with the page colour chosen meanwhile.
        val back = scrolling.withFlow(ReaderFlow.PAGES)
        assertEquals(ReaderFlow.PAGES, back.flow)
        assertEquals(1.1f to ReaderMargins.NARROW, back.fontScale to back.margins)
        assertEquals(1.4f, back.lineSpacing)
        assertEquals(ReaderTheme.NIGHT, back.theme)
        // And to scrolling again: as it was left.
        val again = back.copy(lineSpacing = 2f).withFlow(ReaderFlow.SCROLL)
        assertEquals(1.6f to ReaderMargins.WIDE, again.fontScale to again.margins)
        assertEquals("pages' spacing stays pages'", 1.4f, again.lineSpacing)
        assertEquals(2f, again.withFlow(ReaderFlow.PAGES).lineSpacing)
    }

    @Test
    fun aBooksOwnLookKeepsTheOtherFlowsToo() {
        val scrolling = ReaderSettings(fontScale = 1.2f).withFlow(ReaderFlow.SCROLL).copy(fontScale = 1.8f)
        // As a book's own look is kept (a file) and read back over the usual settings.
        val read = ReaderSettings.withLook(ReaderSettings(), scrolling.lookJson())
        assertEquals(ReaderFlow.SCROLL, read.flow)
        assertEquals(1.8f, read.fontScale)
        assertEquals(1.2f, read.withFlow(ReaderFlow.PAGES).fontScale)
        // Set as the default (the usual settings take the book's look), it comes along.
        assertEquals(1.2f, ReaderSettings().withLookOf(read).withFlow(ReaderFlow.PAGES).fontScale)
        // The look kept for the other flow holds no look inside it in turn.
        assertNull(org.json.JSONObject(read.otherLook!!).optString("otherLook").takeIf { it.isNotEmpty() })
    }
}
