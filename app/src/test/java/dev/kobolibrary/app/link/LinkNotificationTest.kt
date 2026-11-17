package dev.kobolibrary.app.link

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The link's notification: while it waits, Send and Sync (with a device paired) and Stop; what came of the last one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun actions(n: android.app.Notification) = (0 until NotificationCompat.getActionCount(n)).map { NotificationCompat.getAction(n, it)!!.title.toString() }

    @Test
    fun waitingWithADevicePairedOffersSendSyncAndStop() {
        val n = LinkService.build(context, emptyList(), paired = true, said = null)
        assertEquals(listOf(tr("Send"), tr("Sync"), tr("Stop")), actions(n))
        assertEquals(tr("Ready to receive from your devices"), NotificationCompat.getContentTitle(n).toString())
        assertEquals(tr("Paired devices can send while KoLib is closed."), NotificationCompat.getContentText(n).toString())
        // Send asks what to send, over whatever's on the screen; Sync goes to the service.
        val send = shadowOf(NotificationCompat.getAction(n, 0)!!.actionIntent).savedIntent
        assertEquals(ShareToDeviceActivity.ACTION_CHOOSE, send.action)
        assertEquals(ShareToDeviceActivity::class.java.name, send.component?.className)
        assertTrue(shadowOf(NotificationCompat.getAction(n, 0)!!.actionIntent).isActivityIntent)
        val sync = shadowOf(NotificationCompat.getAction(n, 1)!!.actionIntent)
        assertTrue(sync.isServiceIntent)
        assertEquals(LinkService::class.java.name, sync.savedIntent.component?.className)
    }

    @Test
    fun withNoDevicePairedOnlyStop() {
        assertEquals(listOf(tr("Stop")), actions(LinkService.build(context, emptyList(), paired = false, said = null)))
    }

    @Test
    fun whatCameOfItShowsInPlaceOfTheUsualLine() {
        val n = LinkService.build(context, emptyList(), paired = true, said = "Reading positions synced with Galaxy Tab S9.")
        assertEquals("Reading positions synced with Galaxy Tab S9.", NotificationCompat.getContentText(n).toString())
    }

    @Test
    fun whileSendingItShowsHowFarWithNoActions() {
        val t = Transfer(1, "Galaxy Tab S9", "The Histories", outgoing = true, done = 45, total = 100)
        val n = LinkService.build(context, listOf(t), paired = true, said = null)
        assertEquals(tr("Sending to {0}", "Galaxy Tab S9"), NotificationCompat.getContentTitle(n).toString())
        assertTrue(actions(n).isEmpty())
    }
}
