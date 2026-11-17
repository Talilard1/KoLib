package dev.kolib.app.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-420dpi")
    fun settingsKeptBeforeThePhonesWereChosenStayAsTheyWere() {
        // Someone who'd changed only the page's colour: everything else is as it was for them, not the phone's.
        prefs.edit().putString("theme", "SEPIA").putString("heads", "TOP").commit()
        val s = ReaderSettingsStore(context).settings.value
        assertEquals(ReaderSettings().copy(theme = ReaderTheme.SEPIA), s)
    }
}
