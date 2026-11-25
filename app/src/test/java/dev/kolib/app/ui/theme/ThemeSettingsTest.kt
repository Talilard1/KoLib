package dev.kolib.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lighter opening is chosen for phones the first time the app starts, and after that it's as set. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port")
    fun onAPhoneItStartsLight() {
        assertTrue(ThemeSettings(context).openingLight.value)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun onATabletItStartsInFull() {
        assertFalse(ThemeSettings(context).openingLight.value)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port")
    fun afterThatItStaysAsSet() {
        ThemeSettings(context).setOpeningLight(false)
        assertFalse(ThemeSettings(context).openingLight.value)
    }

    /**
     * The opening plays, and What's new is due, once for each time KoLib's installed or updated: putting the same version
     * on again counts, as it's told by when that was, not by version.
     */
    @Test
    fun theOpeningAndWhatsNewComeOnceForEachUpdateEvenOfTheSameVersion() {
        val themes = ThemeSettings(context)
        assertTrue(themes.openingDue(1_000L))
        themes.openingPlayed(1_000L)
        assertFalse(ThemeSettings(context).openingDue(1_000L))
        // Updated again (the same version, put on again): it plays again, once.
        assertTrue(ThemeSettings(context).openingDue(2_000L))
        assertEquals(-1L, themes.newsSeen.value)
        themes.setNewsSeen(1_000L)
        assertEquals(1_000L, ThemeSettings(context).newsSeen.value)
        assertTrue("due after the next update", ThemeSettings(context).newsSeen.value != 2_000L)
        // What a version before 3.5 kept (by version, under other names) is left alone: after updating, both come.
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().putInt("newsSeen", 110).putInt("openingPlayedFor", 110).commit()
        val after = ThemeSettings(context)
        assertEquals(-1L, after.newsSeen.value)
        assertTrue(after.openingDue(3_000L))
    }
}
