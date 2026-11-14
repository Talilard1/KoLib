package dev.kobolibrary.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
}
