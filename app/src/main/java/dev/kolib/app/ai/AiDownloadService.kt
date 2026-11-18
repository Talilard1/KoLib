package dev.kolib.app.ai

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
import dev.kolib.app.KoLibApp
import dev.kolib.app.MainActivity
import dev.kolib.app.R
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps the app running while a model downloads (they're big: it can take a while), with a notification
 * saying how far it's got. It stops itself when nothing is downloading.
 */
class AiDownloadService : Service() {

    companion object {
        private const val CHANNEL = "ai"
        private const val NOTIFICATION = 4203

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, AiDownloadService::class.java))
            } catch (e: Exception) {
                AppLog.w("AI", "Couldn't keep the app running in the background for the download", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private val hub get() = (application as KoLibApp).ai

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel()
        try {
            val first = notification(hub.downloads.value)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION, first)
        } catch (e: Exception) {
            AppLog.w("AI", "Couldn't show the download's notification", e)
        }
        if (watching == null) {
            watching = scope.launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                hub.downloads.sample(1000).collect { s ->
                    if (s.values.none { it.error == null }) finish()
                    else getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(s))
                }
            }
        }
        if (!hub.downloading) finish()
        return START_NOT_STICKY
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15 lets this run for six hours a day at most: what's downloaded is kept, to carry on later. */
    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("AI model downloads"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
    }

    private fun notification(s: Map<String, AiDownload>): android.app.Notification {
        val entry = s.entries.firstOrNull { it.value.error == null }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_text)
            .setContentTitle(tr("Downloading {0}", AiModels.byId(entry?.key)?.name ?: tr("AI model")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val d = entry?.value
        if (d != null && d.total > 0) {
            b.setContentText(tr("{0} of {1}", Fmt.bytes(this, d.bytes), Fmt.bytes(this, d.total)))
                .setProgress(1000, (d.bytes * 1000 / d.total).toInt(), false)
        } else b.setProgress(0, 0, true)
        return b.build()
    }
}
