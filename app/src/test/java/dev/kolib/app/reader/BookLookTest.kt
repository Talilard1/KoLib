package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/** A book keeps its own text, page and layout settings; the page colour, brightness and controls are every book's. */
@RunWith(RobolectricTestRunner::class)
class BookLookTest {
    private val usual = ReaderSettings()
    private val starWars = usual.copy(
        printHeads = false, fontScale = 1.6f, font = ReaderFont.LITERATA, margins = ReaderMargins.WIDE, headGap = HeadGap.CLOSE,
        runningHeads = RunningHeads.BOTTOM, flow = ReaderFlow.SCROLL, rectoOpenings = false, dropCaps = false,
    )

    @Test
    fun aBooksLookIsItsTextPagesAndLayout() {
        val night = usual.copy(theme = ReaderTheme.NIGHT, brightness = 0.3f, volumeKeys = true, pageTurn = PageTurn.SLIDE, autoWpm = 200)
        val shown = night.withLookOf(starWars)
        // The book's look...
        assertFalse(shown.printHeads)
        assertEquals(1.6f, shown.fontScale)
        assertEquals(ReaderFont.LITERATA, shown.font)
        assertEquals(RunningHeads.BOTTOM, shown.runningHeads)
        assertEquals(ReaderFlow.SCROLL, shown.flow)
        // ...and every book's colour, brightness, controls and autoscroll.
        assertEquals(ReaderTheme.NIGHT, shown.theme)
        assertEquals(0.3f, shown.brightness)
        assertTrue(shown.volumeKeys)
        assertEquals(PageTurn.SLIDE, shown.pageTurn)
        assertEquals(200, shown.autoWpm)
        assertTrue(shown.sameLook(starWars))
        assertFalse(shown.sameLook(night))
        assertTrue(night.sameLook(usual))
    }

    @Test
    fun aBooksLookIsKeptWithItAndCanBeLetGo() {
        val dir = Files.createTempDirectory("look").toFile()
        val store = ReaderStore(dir)
        assertNull(store.look(usual))
        store.saveLook(starWars)
        assertTrue(File(dir, LOOK_PORTRAIT).exists())
        val back = store.look(usual.copy(theme = ReaderTheme.SEPIA))!!
        assertTrue(back.sameLook(starWars))
        assertEquals(ReaderTheme.SEPIA, back.theme)
        assertEquals(starWars.marginItems, back.marginItems)
        store.forgetLook()
        assertNull(store.look(usual))
        dir.deleteRecursively()
    }

    @Test
    fun aLookFromBeforeSomeSettingsExistedKeepsTheUsualOnesForThem() {
        val o = org.json.JSONObject().put("fontScale", 1.2).put("font", "NOT_A_FONT")
        val s = ReaderSettings.withLook(usual, o)
        assertEquals(1.2f, s.fontScale)
        assertEquals(usual.font, s.font)
        assertEquals(usual.printHeads, s.printHeads)
    }
}
