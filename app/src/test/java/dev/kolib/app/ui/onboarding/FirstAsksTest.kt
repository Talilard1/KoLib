package dev.kolib.app.ui.onboarding

import android.Manifest
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What KoLib needs allowed, asked for together when it's first opened. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FirstAsksTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var done = 0

    /** Android allows these (and not the rest). */
    private fun allow(vararg asks: Ask) {
        val activity = compose.activity
        if (Ask.NOTIFICATIONS in asks) shadowOf(activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        if (Ask.CAMERA in asks) shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)
        shadowOf(activity.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(activity.packageName, Ask.BACKGROUND in asks)
        shadowOf(activity.packageManager).setCanRequestPackageInstalls(Ask.UPDATES in asks)
    }

    private fun show() {
        compose.setContent { FirstAsks(onDone = { done++ }) }
        compose.waitForIdle()
    }

    /** What's allowed already has a tick, the rest an Allow; Not now carries on into KoLib. */
    @Test
    fun itShowsWhatsLeftToAllowAndNotNowCarriesOn() {
        allow(Ask.CAMERA)
        show()
        compose.onNodeWithTag("ask-camera-allowed").assertExists()
        for (key in listOf("notifications", "background", "updates")) compose.onNodeWithTag("ask-$key-allow").assertExists()
        compose.onNodeWithTag("first-asks-later").performClick()
        assertEquals(1, done)
    }

    /** With everything allowed already, it isn't shown: straight on into KoLib. */
    @Test
    fun withEverythingAllowedItIsntShown() {
        allow(*Ask.entries.toTypedArray())
        show()
        compose.onNodeWithTag("first-asks").assertDoesNotExist()
        assertEquals(1, done)
    }

    /** Allow all asks Android for the first that's left (here, notifications), and stays till it's answered. */
    @Test
    fun allowAllAsksForTheFirstThatsLeft() {
        allow(Ask.CAMERA)
        show()
        compose.onNodeWithTag("first-asks-all").performClick()
        compose.waitForIdle()
        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertTrue("notifications were asked for", asked != null && Manifest.permission.POST_NOTIFICATIONS in asked.requestedPermissions)
        assertEquals(0, done)
    }
}
