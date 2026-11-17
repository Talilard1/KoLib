package dev.kolib.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.ui.theme.ThemeChoice
import dev.kolib.app.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The look KoLib starts with. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeDefaultTest {
    @Test
    fun aFreshInstallFollowsTheSystem() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = ThemeSettings(context)
        assertEquals(ThemeChoice.SYSTEM, settings.choice.value)
        // And a choice made is kept.
        settings.set(ThemeChoice.BOOKCASE)
        assertEquals(ThemeChoice.BOOKCASE, ThemeSettings(context).choice.value)
    }
}
