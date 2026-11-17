package dev.kobolibrary.app.link

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.kobolibrary.app.KoboLibraryApp
import dev.kobolibrary.app.MainActivity
import dev.kobolibrary.app.R
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps KoLib running while it sends to or takes in from another device (a few hundred books can take a while), and,
 * when asked to, all the time, so paired devices can reach it with KoLib closed. A notification says what it's doing,
 * and while it waits, offers Send (books, files or the clipboard, chosen next), Sync (reading positions, with the
 * paired devices on the network) and Stop; what came of the last one shows in it for a few seconds. Android shows no
 * more than three actions. It stops itself when there's nothing left to do.
 */
class LinkService : Service() {

    companion object {
        private const val CHANNEL = "link"
        private const val NOTIFICATION = 4202
        private const val ACTION_STOP = "dev.kobolibrary.app.link.STOP"
        private const val ACTION_SYNC = "dev.kobolibrary.app.link.SYNC"
        /** How long what came of something (sent, synced, received) shows in the notification. */
        private const val SAID_FOR = 10_000L

        /**
         * The notification: what's being sent or taken in, or, while it waits, that it's ready, with what came of the
         * last thing done ([said]) for a while, and Send and Sync (with a device [paired]) and Stop.
         */
        internal fun build(context: Context, transfers: List<Transfer>, paired: Boolean, said: String?): android.app.Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify_devices)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(open)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            val t = transfers.firstOrNull()
            if (t != null) {
                val more = if (transfers.size > 1) tr(" · {0} more", transfers.size - 1) else ""
                b.setContentTitle(if (t.outgoing) tr("Sending to {0}", t.device) else tr("Receiving from {0}", t.device))
                    .setContentText((if (t.items > 1) tr("“{0}” · {1} of {2} · {3}%", t.what, t.item, t.items, t.percent) else tr("“{0}” · {1}%", t.what, t.percent)) + more)
                    .setProgress(100, t.percent, false)
            } else {
                val stop = PendingIntent.getService(
                    context, 1, Intent(context, LinkService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.setContentTitle(tr("Ready to receive from your devices"))
                    .setContentText(said ?: tr("Paired devices can send while KoLib is closed."))
                if (paired) {
                    // What to send, and to which device, is chosen next, over whatever's on the screen.
                    val send = PendingIntent.getActivity(
                        context, 2, ShareToDeviceActivity.chooseIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    val sync = PendingIntent.getService(
                        context, 3, Intent(context, LinkService::class.java).setAction(ACTION_SYNC),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    b.addAction(0, tr("Send"), send).addAction(0, tr("Sync"), sync)
                }
                b.addAction(0, tr("Stop"), stop)
            }
            return b.build()
        }

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LinkService::class.java))
            } catch (e: Exception) {
                // Android may refuse while the app is in the background; what's being sent still goes while it runs.
                AppLog.w("Link", "Couldn't keep the app running for the link to the other devices", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var holding = false
    private val link get() = (application as KoboLibraryApp).link
    /** What came of the last send, sync or transfer, shown for a while; null: nothing to say. */
    private var said: String? = null
    private var forgetting: Job? = null
    /** Stopping: its notification is gone and mustn't be put back (it couldn't be swiped away). */
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        finished = false
        if (intent?.action == ACTION_STOP) link.changeSettings { it.copy(stayReachable = false) }
        channel()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIFICATION, notification())
        } catch (e: Exception) {
            AppLog.w("Link", "Couldn't show the link's notification", e)
        }
        if (!holding) {
            holding = true
            link.start()
        }
        if (watching == null) {
            watching = scope.launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                combine(link.transfers, link.settings, link.paired) { t, s, _ -> t to s }.sample(1000).collect { (t, s) ->
                    if (t.isEmpty() && !s.stayReachable) finish()
                    else renotify()
                }
            }
            scope.launch { link.messages.collect { say(it) } }
        }
        if (intent?.action == ACTION_SYNC) syncAll()
        if (link.transfers.value.isEmpty() && !link.settings.value.stayReachable) finish()
        return START_NOT_STICKY
    }

    private fun renotify() {
        if (finished) return
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
    }

    /** Shows [what] in the notification for a few seconds. */
    private fun say(what: String) {
        said = what
        renotify()
        forgetting?.cancel()
        forgetting = scope.launch {
            delay(SAID_FOR)
            said = null
            renotify()
        }
    }

    /** Reading positions brought into step with the paired devices on the network (or, with none found, all of them). */
    private fun syncAll() {
        val paired = link.paired.value
        if (paired.isEmpty()) return
        val near = link.nearby.value.map { it.id }.toSet()
        say(tr("Syncing reading positions…"))
        paired.filter { it.id in near }.ifEmpty { paired }.forEach { link.syncPlaces(it) }
    }

    private fun finish() {
        finished = true
        forgetting?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (holding) {
            holding = false
            link.stop()
        }
        super.onDestroy()
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Your other devices"), NotificationManager.IMPORTANCE_LOW).apply {
                    description = tr("Transfers between your devices")
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(): android.app.Notification = build(this, link.transfers.value, link.paired.value.isNotEmpty(), said)
}
