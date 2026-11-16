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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps KoLib running while it sends to or takes in from another device (a few hundred books can take a while), and,
 * when asked to, all the time, so paired devices can reach it with KoLib closed. A notification says what it's doing.
 * It stops itself when there's nothing left to do.
 */
class LinkService : Service() {

    companion object {
        private const val CHANNEL = "link"
        private const val NOTIFICATION = 4202
        private const val ACTION_STOP = "dev.kobolibrary.app.link.STOP"

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                combine(link.transfers, link.settings) { t, s -> t to s }.sample(1000).collect { (t, s) ->
                    if (t.isEmpty() && !s.stayReachable) finish()
                    else getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
                }
            }
        }
        if (link.transfers.value.isEmpty() && !link.settings.value.stayReachable) finish()
        return START_NOT_STICKY
    }

    private fun finish() {
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
                    description = tr("Sending to and from your other devices")
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(): android.app.Notification {
        val transfers = link.transfers.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_devices)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val t = transfers.firstOrNull()
        if (t != null) {
            val more = if (transfers.size > 1) tr(" · {0} more", transfers.size - 1) else ""
            val percent = if (t.total > 0) (t.done * 100 / t.total).toInt() else 0
            b.setContentTitle(if (t.outgoing) tr("Sending to {0}", t.device) else tr("Receiving from {0}", t.device))
                .setContentText(tr("“{0}” · {1}%", t.what, percent) + more)
                .setProgress(100, percent, t.total <= 0)
        } else {
            val stop = PendingIntent.getService(
                this, 1, Intent(this, LinkService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.setContentTitle(tr("Your other devices can reach KoLib"))
                .setContentText(tr("Paired devices can send to it while it's closed."))
                .addAction(0, tr("Stop"), stop)
        }
        return b.build()
    }
}
