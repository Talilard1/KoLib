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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app running while the AI answers something asked of it (a question, a chapter's summary), so that it
 * carries on when the reader switches to another app. Android only shows its notification if it takes more than a
 * few seconds. It stops itself when nothing is being answered; an answer that's ready while KoLib is out of sight
 * gets a notification of its own ([answered]), which opens the AI's window.
 */
class AiAnswerService : Service() {

    companion object {
        private const val CHANNEL = "ai-answering"
        private const val READY = "ai-ready"
        private const val NOTIFICATION = 4204
        private const val ANSWER = 4205
        /** Opens the app with the AI's window open (from the notification that an answer is ready). */
        const val ACTION_OPEN = "dev.kolib.app.OPEN_AI"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, AiAnswerService::class.java))
            } catch (e: Exception) {
                AppLog.w("AI", "Couldn't keep the app running in the background for the answer", e)
            }
        }

        /** Says, in a notification, that the answer to [what] is ready ([answer]); tapped, it opens the AI's window. */
        fun answered(context: Context, what: String, answer: String) {
            try {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                if (nm.getNotificationChannel(READY) == null) {
                    nm.createNotificationChannel(NotificationChannel(READY, tr("AI answers"), NotificationManager.IMPORTANCE_DEFAULT))
                }
                val open = PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java).setAction(ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val text = cleanAnswer(answer).replace(Regex("\\s+"), " ").trim()
                nm.notify(
                    ANSWER,
                    NotificationCompat.Builder(context, READY)
                        .setSmallIcon(R.drawable.ic_notify_text)
                        .setContentTitle(tr("Your answer is ready"))
                        .setContentText(what)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(what + "\n" + text.take(400)))
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build(),
                )
            } catch (e: Exception) {
                AppLog.w("AI", "Couldn't say the answer is ready", e)
            }
        }

        /** The notification that an answer is ready goes, once the AI's window is open. */
        fun seen(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ANSWER) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private val app get() = application as KoLibApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("AI at work"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        try {
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).setAction(ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // No hurry to show it: most answers are done before Android would.
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify_text)
                .setContentTitle(tr("Answering…"))
                .setOngoing(true)
                .setSilent(true)
                .setProgress(0, 0, true)
                .setContentIntent(open)
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION, n)
        } catch (e: Exception) {
            AppLog.w("AI", "Couldn't keep the app running for the answer", e)
        }
        if (watching == null) {
            watching = scope.launch {
                app.aiWorking.collect { n -> if (n <= 0) finish() }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
