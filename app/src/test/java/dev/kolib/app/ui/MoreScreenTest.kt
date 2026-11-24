package dev.kolib.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.update.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MoreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** Check for updates is just above About, and asks; at the foot, the newest release on GitHub. */
    @Test
    fun checkForUpdatesIsAboveAboutAndTheNewestReleaseIsAtTheFoot() {
        var asked = 0
        var storage = 0
        compose.setContent {
            MoreScreen(
                version = "Version 3.4", onSettings = {}, onStats = {}, onTours = null, onLog = {}, onBackups = {}, onAbout = {},
                onUpdates = { asked++ }, onStorage = { storage++ },
            )
        }
        compose.onNodeWithTag("more-storage").performClick()
        assertEquals("Storage opens", 1, storage)
        val updates = compose.onNodeWithTag("more-updates").fetchSemanticsNode().boundsInRoot
        val about = compose.onNodeWithTag("more-about").fetchSemanticsNode().boundsInRoot
        assertTrue("Check for updates is just above About", updates.bottom <= about.top + 1f && about.top - updates.bottom < 2f)
        compose.onNodeWithTag("more-updates").performClick()
        assertEquals(1, asked)
        compose.onNodeWithTag("more-releases").performScrollTo().performClick()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Updates.PAGE, opened?.dataString)
    }
}
