package dev.kolib.app.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.TestLanguage
import dev.kolib.app.tr
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The notification's Send clipboard: the clipboard is read once the screen is in front (Android allows no sooner). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xxhdpi")
class SendClipboardTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    @Before
    fun setUp() {
        TestLanguage.apply()
        // Offered once a device is paired: as it would be by then.
        ShareToDeviceActivity.offer(context, true)
    }

    @Test
    fun whatsOnTheClipboardIsTakenToSend() {
        clipboard.setPrimaryClip(ClipData.newPlainText("note", "The lamp in the turret"))
        ActivityScenario.launch<ShareToDeviceActivity>(ShareToDeviceActivity.clipboardIntent(context)).use { scenario ->
            // Nothing's read before it's in front.
            compose.onNodeWithText(tr("What would you like to send?")).assertDoesNotExist()
            scenario.onActivity { it.onWindowFocusChanged(true) }
            compose.waitForIdle()
            compose.onNodeWithText(tr("Text to the other device's clipboard: “{0}”", "The lamp in the turret")).assertExists()
        }
    }

    @Test
    fun anEmptyClipboardSaysSo() {
        clipboard.clearPrimaryClip()
        ActivityScenario.launch<ShareToDeviceActivity>(ShareToDeviceActivity.clipboardIntent(context)).use { scenario ->
            scenario.onActivity { it.onWindowFocusChanged(true) }
            compose.waitForIdle()
            compose.onNodeWithText(tr("The clipboard is empty.")).assertExists()
        }
    }
}
