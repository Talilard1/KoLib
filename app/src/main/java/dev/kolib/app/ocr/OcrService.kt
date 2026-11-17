package dev.kolib.app.ocr

import dev.kolib.app.tr
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
import dev.kolib.app.ui.components.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps KoLib running while scanned books are read (it can take an hour for a long one), with a
 * notification saying how far it's got and a button to stop. It stops itself when there's nothing to do.
 */
class OcrService : Service() {

    companion object {
        private const val CHANNEL = "ocr"
        private const val NOTIFICATION = 4201
        private const val ACTION_STOP = "dev.kolib.app.ocr.STOP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OcrService::class.java))
            } catch (e: Exception) {
                // Android may refuse while the app is in the background; the reading still goes on while it runs.
                AppLog.w("OCR", "Couldn't keep the app running in the background for the text recognition", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private val manager get() = (application as KoLibApp).ocr

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) manager.stopAll()
        channel()
        val first = notification(manager.status.value)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION, first)
        } catch (e: Exception) {
            AppLog.w("OCR", "Couldn't show the text recognition's notification", e)
        }
        if (watching == null) {
            watching = scope.launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                manager.status.sample(1000).collect { s ->
                    if (s.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(s))
                    }
                }
            }
        }
        if (manager.status.value.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15 lets this run for six hours a day at most: what's been read is kept, to carry on later. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLog.w("OCR", "Android stopped the text recognition after its time limit; it can be carried on later.")
        manager.stopAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Text recognition"), NotificationManager.IMPORTANCE_LOW).apply {
                    description = tr("Text recognition progress")
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(s: Map<String, OcrStatus>): android.app.Notification {
        val library = (application as KoLibApp).library
        val entry = s.entries.firstOrNull { it.value !is OcrStatus.Waiting } ?: s.entries.firstOrNull()
        val status = entry?.value
        val title = entry?.key?.let { library.book(it)?.title }
        val waiting = s.count { it.value is OcrStatus.Waiting }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OcrService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_text)
            .setContentTitle(title?.let { tr("Converting “{0}” to text", it) } ?: tr("Text recognition"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, tr("Stop"), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val more = if (waiting > 0 && status !is OcrStatus.Waiting) tr(" · {0} more waiting", waiting) else ""
        when (status) {
            is OcrStatus.Downloading -> b.setContentText(tr("Downloading the language data…") + more)
                .setProgress(status.total.toInt().coerceAtLeast(1), status.done.toInt(), false)
            is OcrStatus.Reading -> {
                val left = status.millisLeft()?.let { Fmt.timeLeft(it) } ?: ""
                b.setContentText(tr("Page {0} of {1}", status.done, status.total) + left + more)
                    .setProgress(status.total, status.done, false)
            }
            is OcrStatus.Making -> b.setContentText(tr("Building the book…") + more).setProgress(0, 0, true)
            else -> b.setContentText(tr("Getting ready…") + more).setProgress(0, 0, true)
        }
        return b.build()
    }
}
