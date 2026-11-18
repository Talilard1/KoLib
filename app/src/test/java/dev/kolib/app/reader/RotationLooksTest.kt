package dev.kolib.app.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/** A look is kept for each way the device is held, in the usual settings and for a book: changing one leaves the other. */
@RunWith(RobolectricTestRunner::class)
class RotationLooksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val middle = Cutouts(411f, 891f, top = Cutouts.Span(190f, 221f, 32f))

    @Test
    fun theUsualSettingsKeepALookForEachWay() {
        val store = ReaderSettingsStore(context)
        // To start with, both ways are the same.
        assertEquals(store.usual(false), store.usual(true))
        val started = store.usual(false)
        // Bigger text and scrolling, held sideways: upright is as it was.
        store.update(true) { it.copy(fontScale = 2f, flow = ReaderFlow.SCROLL) }
        assertEquals(2f, store.landscape.value.fontScale)
        assertEquals(ReaderFlow.SCROLL, store.usual(true).flow)
        assertEquals(started, store.usual(false))
        // And the other way about.
        store.update(false) { it.copy(margins = ReaderMargins.WIDE) }
        assertEquals(ReaderMargins.WIDE, store.settings.value.margins)
        assertEquals(started.margins, store.usual(true).margins)
        // What isn't the look (the page colour, the page turn) is the same both ways, whichever it's changed from.
        store.update(true) { it.copy(theme = ReaderTheme.SEPIA) }
        store.update(false) { it.copy(pageTurn = PageTurn.FADE) }
        for (way in listOf(false, true)) {
            assertEquals(ReaderTheme.SEPIA, store.usual(way).theme)
            assertEquals(PageTurn.FADE, store.usual(way).pageTurn)
        }
        // Kept.
        val again = ReaderSettingsStore(context)
        assertEquals(store.usual(false), again.usual(false))
        assertEquals(store.usual(true), again.usual(true))
        assertEquals(2f, again.landscape.value.fontScale)
        // What the reading setup chooses is where both start.
        val chosen = started.copy(font = ReaderFont.LITERATA, fontScale = 1.3f)
        again.setBoth(chosen)
        assertEquals(chosen, again.usual(false))
        assertEquals(chosen, again.usual(true))
    }

    @Test
    fun settingsFromBeforeStartTheSameBothWays() {
        // A reader that's been used, with text made bigger, from before sideways had a look of its own.
        ReaderSettingsStore(context).update { it.copy(fontScale = 1.8f, align = ReaderAlign.LEFT) }
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().remove("lookLandscape").commit()
        val store = ReaderSettingsStore(context)
        assertEquals(1.8f, store.usual(true).fontScale)
        assertEquals(store.usual(false), store.usual(true))
        // From then on they go their own ways.
        store.update(false) { it.copy(fontScale = 1.1f) }
        assertEquals(1.8f, ReaderSettingsStore(context).usual(true).fontScale)
    }

    @Test
    fun onAScreenWithACameraInTheMiddleOfItsTopTheHeadingsStartAtTheFoot() {
        val store = ReaderSettingsStore(context)
        store.setBoth(ReaderSettings.PHONE)
        // No camera seen: nothing's moved, and it's still to do when one is.
        assertFalse(store.placeAroundCamera(false, Cutouts(411f, 891f)))
        assertTrue(store.placeAroundCamera(false, middle))
        val placed = store.usual(false)
        assertEquals(RunningHeads.BOTTOM, placed.runningHeads)
        assertEquals(MarginSpot.TOP_LEFT, placed.spotOf(MarginItem.CLOCK))
        assertEquals(MarginSpot.TOP_RIGHT, placed.spotOf(MarginItem.BATTERY))
        // Held sideways the camera's at the side: that way's look is as it was.
        assertTrue(store.usual(true).stockArrangement)
        // Once only: put back as they were, they stay.
        store.update(false) { it.withHeads(RunningHeads.TOP).withClock(true) }
        assertTrue(store.usual(false).stockArrangement)
        assertFalse(store.placeAroundCamera(false, middle))
        assertTrue(ReaderSettingsStore(context).usual(false).stockArrangement)
    }

    @Test
    fun whatsBeenArrangedAlreadyIsLeftAlone() {
        val store = ReaderSettingsStore(context)
        val mine = ReaderSettings.PHONE.copy(marginItems = MarginItem.DEFAULTS + (MarginItem.PERCENT to MarginSpot.BOTTOM_RIGHT))
        store.setBoth(mine)
        assertFalse(store.placeAroundCamera(false, middle))
        assertEquals(mine, store.usual(false))
        // Two pages side by side (a tablet's notch over the gap between them): left alone too.
        val tablet = ReaderSettingsStore(context)
        tablet.setBoth(ReaderSettings())
        assertFalse(tablet.placeAroundCamera(true, Cutouts(1280f, 800f, top = Cutouts.Span(620f, 660f, 24f))))
        assertTrue(tablet.usual(true).stockArrangement)
    }

    @Test
    fun aBookKeepsALookForEachWay() {
        val dir = Files.createTempDirectory("looks").toFile()
        val store = ReaderStore(dir)
        val usual = ReaderSettings()
        val big = usual.copy(fontScale = 2f, flow = ReaderFlow.SCROLL)
        store.saveLook(big, landscape = true)
        // Sideways it has its own; upright it still follows the usual settings.
        assertEquals(2f, store.look(usual, landscape = true)!!.fontScale)
        assertNull(store.look(usual, landscape = false))
        assertTrue(File(dir, LOOK_LANDSCAPE).exists())
        assertFalse(File(dir, LOOK_PORTRAIT).exists())
        val narrow = usual.copy(margins = ReaderMargins.NARROW)
        store.saveLook(narrow, landscape = false)
        assertEquals(ReaderMargins.NARROW, store.look(usual, false)!!.margins)
        assertEquals(usual.margins, store.look(usual, true)!!.margins)
        // Letting one go leaves the other.
        store.forgetLook(landscape = true)
        assertNull(store.look(usual, true))
        assertEquals(ReaderMargins.NARROW, store.look(usual, false)!!.margins)
        dir.deleteRecursively()
    }

    @Test
    fun aLookFromBeforeBecomesBothWays() {
        val dir = Files.createTempDirectory("looks").toFile()
        val usual = ReaderSettings()
        File(dir, "look.json").writeText(usual.copy(fontScale = 1.8f).lookJson().toString())
        val store = ReaderStore(dir)
        assertEquals(1.8f, store.look(usual, false)!!.fontScale)
        assertEquals(1.8f, store.look(usual, true)!!.fontScale)
        assertFalse(File(dir, "look.json").exists())
        // It hasn't been seen to for a camera yet; once kept by this version, it has.
        assertFalse(store.lookPlacedForCamera(false))
        store.saveLook(store.look(usual, false)!!.aroundCamera(), landscape = false)
        assertTrue(store.lookPlacedForCamera(false))
        assertFalse(store.lookPlacedForCamera(true))
        assertEquals(RunningHeads.BOTTOM, store.look(usual, false)!!.runningHeads)
        assertEquals(RunningHeads.TOP, store.look(usual, true)!!.runningHeads)
        // A book with no look of its own has nothing to see to.
        assertTrue(ReaderStore(Files.createTempDirectory("none").toFile()).lookPlacedForCamera(false))
        // Both go with the book to a linked device.
        assertTrue(dev.kolib.app.link.LinkBooks.sendable(LOOK_PORTRAIT) && dev.kolib.app.link.LinkBooks.sendable(LOOK_LANDSCAPE))
        dir.deleteRecursively()
    }
}
