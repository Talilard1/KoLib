package dev.kolib.app.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How the reader starts: on a phone with settings chosen for a small screen, on a tablet as it always
 * has. They're kept from the first time they're read, so they don't change under anyone later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderDefaultsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    @Before
    @After
    fun fresh() {
        prefs.edit().clear().commit()
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun aPhoneStartsWithThePhonesSettings() {
        val s = ReaderSettingsStore(context).settings.value
        assertEquals(ReaderSettings.PHONE, s)
        assertEquals(ReaderFont.LITERATA, s.font)
        assertEquals(1.1f, s.fontScale)
        assertEquals(1.4f, s.lineSpacing)
        assertEquals(ReaderHyphens.ON, s.hyphens)
        assertEquals(PageTurn.SLIDE, s.pageTurn)
        assertEquals(TapWidth.WIDE, s.tapWidth)
        assertEquals(HeadSize.SMALL, s.headSize)
        assertEquals(HeadGap.CLOSE, s.headGap)
        assertEquals(HeadTitlePlace.CENTRED, s.headTitlePlace)
        // The page itself is the same on both: its colour, its margins, its headings at the top.
        assertEquals(ReaderSettings().theme, s.theme)
        assertEquals(ReaderSettings().margins, s.margins)
        assertEquals(RunningHeads.TOP, s.runningHeads)
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land-420dpi")
    fun aPhoneOnItsSideIsStillAPhone() {
        assertEquals(ReaderSettings.PHONE, ReaderSettingsStore(context).settings.value)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun aTabletStartsAsItAlwaysHas() {
        assertEquals(ReaderSettings(), ReaderSettingsStore(context).settings.value)
        assertNotEquals(ReaderSettings.PHONE, ReaderSettings())
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun aTabletUprightIsStillATablet() {
        assertEquals(ReaderSettings(), ReaderSettingsStore(context).settings.value)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun theStartingSettingsAreKeptAndReadBackTheSame() {
        val first = ReaderSettingsStore(context).settings.value
        assertTrue("They're kept as soon as they're read", prefs.all.isNotEmpty())
        assertEquals(first, ReaderSettingsStore(context).settings.value)
        // Changed since, they stay changed.
        ReaderSettingsStore(context).update { it.copy(font = ReaderFont.GARAMOND, fontScale = 1.3f) }
        val again = ReaderSettingsStore(context).settings.value
        assertEquals(ReaderFont.GARAMOND, again.font)
        assertEquals(1.3f, again.fontScale)
        assertEquals(ReaderHyphens.ON, again.hyphens)
    }

    /** The look kept for the other of pages and scrolling is kept too, both ways the device is held. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun theOtherFlowsLookIsKept() {
        ReaderSettingsStore(context).update { it.copy(fontScale = 1.2f).withFlow(ReaderFlow.SCROLL).copy(fontScale = 1.8f) }
        ReaderSettingsStore(context).update(true) { it.copy(fontScale = 0.9f).withFlow(ReaderFlow.PAGES) }
        val store = ReaderSettingsStore(context)
        assertEquals(ReaderFlow.SCROLL to 1.8f, store.usual(false).flow to store.usual(false).fontScale)
        assertEquals(1.2f, store.usual(false).withFlow(ReaderFlow.PAGES).fontScale)
        assertEquals(ReaderFlow.PAGES, store.usual(true).flow)
    }

    /** The brightness swipe's side and where it starts are kept. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun theBrightnessSwipesSideAndStartAreKept() {
        ReaderSettingsStore(context).update { it.copy(levelSide = LevelSide.BOTH, levelStart = LevelStart.BOTTOM) }
        val again = ReaderSettingsStore(context).settings.value
        assertEquals(LevelSide.BOTH to LevelStart.BOTTOM, again.levelSide to again.levelStart)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun edgeSwipesGoingBackAndTheHillAreKept() {
        ReaderSettingsStore(context).update { it.copy(edgeBack = true, pullHill = false) }
        val again = ReaderSettingsStore(context).settings.value
        assertEquals(true to false, again.edgeBack to again.pullHill)
        assertFalse(again.edgePanels)
    }

    /** From before there was one switch for the sides: the panels turned off had them going back. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun edgeSwipesSetBeforeTheOneSwitchStayAsTheyWere() {
        prefs.edit().putString("theme", "SEPIA").putBoolean("edgePanels", false).putBoolean("edgeBack", false).commit()
        assertTrue(ReaderSettingsStore(context).settings.value.edgeBack)
        prefs.edit().clear().putString("theme", "SEPIA").putBoolean("edgePanels", true).commit()
        assertFalse(ReaderSettingsStore(context).settings.value.edgeBack)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun settingsKeptBeforeThePhonesWereChosenStayAsTheyWere() {
        // Someone who'd changed only the page's colour: everything else is as it was for them, not the phone's.
        prefs.edit().putString("theme", "SEPIA").putString("heads", "TOP").commit()
        val s = ReaderSettingsStore(context).settings.value
        assertEquals(ReaderSettings().copy(theme = ReaderTheme.SEPIA), s)
    }
}
