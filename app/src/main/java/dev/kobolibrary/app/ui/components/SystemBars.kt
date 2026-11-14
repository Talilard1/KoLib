package dev.kobolibrary.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides Android's status bar (the clock, battery and notification icons) while this is shown and
 * [enabled], as the reader does: a swipe down from the top shows it for a moment. It comes back
 * when this goes.
 */
@Composable
fun HideStatusBar(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.activity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { bars.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
