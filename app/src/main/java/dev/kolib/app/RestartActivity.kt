package dev.kolib.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Starts KoLib again, in a process of its own: KoLib's process is ended and its screen opened anew. For
 * a change of language: some words are made only once, when KoLib starts.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(PID, -1)
        // A moment for KoLib to finish saving, as it does when it goes into the background.
        Handler(Looper.getMainLooper()).postDelayed({
            if (pid > 0) Process.killProcess(pid)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(ACTION_RESTARTED)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            Runtime.getRuntime().exit(0)
        }, 400)
    }

    companion object {
        private const val PID = "pid"

        /** What MainActivity is started with after starting again: no opening, nothing to add. */
        const val ACTION_RESTARTED = "dev.kolib.app.RESTARTED"

        fun restart(activity: Activity) {
            activity.startActivity(
                Intent(activity, RestartActivity::class.java)
                    .putExtra(PID, Process.myPid())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            activity.overridePendingTransition(0, 0)
        }
    }
}
