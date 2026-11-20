package dev.kolib.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The place along the foot of a screen for a bar shown from further in (the bar for what's chosen), so it runs the
 * screen's whole width, under a side panel too, rather than only the width of the list it's for.
 */
class FootSlot {
    internal var bar by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalFootSlot = staticCompositionLocalOf<FootSlot?> { null }

/** [content], with a bar along its foot, the whole width, put there by [AtFoot] from anywhere in it. */
@Composable
fun WithFoot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val slot = remember { FootSlot() }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) { CompositionLocalProvider(LocalFootSlot provides slot) { content() } }
        slot.bar?.invoke()
    }
}

/** Puts [bar] along the foot of the screen (see [WithFoot]); where there's no such place, it's shown where it's called. */
@Composable
fun AtFoot(bar: @Composable () -> Unit) {
    val slot = LocalFootSlot.current
    if (slot == null) {
        bar()
        return
    }
    val latest by rememberUpdatedState(bar)
    DisposableEffect(slot) {
        val shown: @Composable () -> Unit = { latest() }
        slot.bar = shown
        onDispose { if (slot.bar === shown) slot.bar = null }
    }
}
