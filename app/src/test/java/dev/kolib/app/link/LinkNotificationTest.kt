package dev.kolib.app.link

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The link's notification: while it waits, Send and Send clipboard (with a device paired) and Stop; what came of the last one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun actions(n: android.app.Notification) = (0 until NotificationCompat.getActionCount(n)).map { NotificationCompat.getAction(n, it)!!.title.toString() }

    @Test
    fun waitingWithADevicePairedOffersSendSendClipboardAndStop() {
        val n = LinkService.build(context, emptyList(), paired = true, said = null)
        assertEquals(listOf(tr("Send"), tr("Send clipboard"), tr("Stop")), actions(n))
        assertEquals(tr("Ready to receive from your devices"), NotificationCompat.getContentTitle(n).toString())
        assertEquals(tr("Paired devices can send while KoLib is closed."), NotificationCompat.getContentText(n).toString())
        // Send asks what to send, over whatever's on the screen; Send clipboard opens a screen that can read the
        // clipboard (only what's in front can) and sends it.
        val send = shadowOf(NotificationCompat.getAction(n, 0)!!.actionIntent).savedIntent
        assertEquals(ShareToDeviceActivity.ACTION_CHOOSE, send.action)
        assertEquals(ShareToDeviceActivity::class.java.name, send.component?.className)
        assertTrue(shadowOf(NotificationCompat.getAction(n, 0)!!.actionIntent).isActivityIntent)
        val clipboard = shadowOf(NotificationCompat.getAction(n, 1)!!.actionIntent)
        assertTrue(clipboard.isActivityIntent)
        assertEquals(ShareToDeviceActivity.ACTION_CLIPBOARD, clipboard.savedIntent.action)
        assertEquals(ShareToDeviceActivity::class.java.name, clipboard.savedIntent.component?.className)
    }

    @Test
    fun withNoDevicePairedOnlyStop() {
        assertEquals(listOf(tr("Stop")), actions(LinkService.build(context, emptyList(), paired = false, said = null)))
    }

    @Test
    fun whatCameOfItShowsInPlaceOfTheUsualLine() {
        val n = LinkService.build(context, emptyList(), paired = true, said = "Reading positions synced with Tablet.")
        assertEquals("Reading positions synced with Tablet.", NotificationCompat.getContentText(n).toString())
    }

    @Test
    fun whileSendingItShowsHowFarWithNoActions() {
        val t = Transfer(1, "Tablet", "The Histories", outgoing = true, done = 45, total = 100)
        val n = LinkService.build(context, listOf(t), paired = true, said = null)
        assertEquals(tr("Sending to {0}", "Tablet"), NotificationCompat.getContentTitle(n).toString())
        assertTrue(actions(n).isEmpty())
    }
}
