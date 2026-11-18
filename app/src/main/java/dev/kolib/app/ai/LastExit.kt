package dev.kolib.app.ai

import android.app.ActivityManager
import android.app.ApplicationExitInfo

/** Why KoLib was closed last time while it held an AI model in memory, where that's worth telling the user. */
enum class AiClosed { MEMORY, CRASH }

/**
 * Whether KoLib's being closed last time is worth telling of in the AI window, and why: closed while it [held] a model
 * in memory and was in sight or working for the user ([importance] no further back than "perceptible"), by Android
 * running short of memory, or by the model's code failing. ([reason], [status] and [importance]: as Android gives them.)
 */
fun closedFor(held: Boolean, reason: Int, status: Int, importance: Int): AiClosed? {
    if (!held || importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) return null
    return when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> AiClosed.MEMORY
        // Killed outright (signal 9): how some devices report closing an app for memory.
        ApplicationExitInfo.REASON_SIGNALED -> if (status == 9) AiClosed.MEMORY else null
        ApplicationExitInfo.REASON_CRASH_NATIVE -> AiClosed.CRASH
        else -> null
    }
}

/** A reason Android gives for an app's being closed, in words for the log. */
fun exitReason(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
    ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "closed by Android for memory"
    ApplicationExitInfo.REASON_CRASH -> "crashed"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "crashed in native code"
    ApplicationExitInfo.REASON_ANR -> "not responding"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "used too much of the device"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by the user"
    ApplicationExitInfo.REASON_USER_STOPPED -> "stopped by the user"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "something it depends on stopped"
    ApplicationExitInfo.REASON_OTHER -> "closed by Android"
    else -> "reason $reason"
}
