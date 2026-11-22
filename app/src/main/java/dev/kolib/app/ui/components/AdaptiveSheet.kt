package dev.kolib.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long the sheet takes to come up, go down and fade, as Mihon's. */
private val SheetMotion = tween<Float>(durationMillis = 350)

/**
 * Let go once it's come down this much of its height, the sheet closes; less, it comes back up. A flick down faster
 * than [CLOSE_FLICK] (a second) closes it too, and let go moving up, it always comes back up.
 */
private const val CLOSE_SHARE = 0.4f
private val CLOSE_FLICK = 700.dp

/** At least this wide (its shorter side), a screen has the sheet in its middle, faded in, as Mihon does on big tablets. */
private const val CENTRED_FROM_DP = 720

/**
 * A sheet as Mihon's, at most 460 dp wide: from the foot of the screen, sliding up as it opens; or, on a big tablet, in
 * the middle of the screen, fading in. Either way it's dragged down to close (or a tap beside it, or Back), sliding
 * down out of sight as it goes.
 */
@Composable
fun AdaptiveSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val centred = LocalConfiguration.current.smallestScreenWidthDp >= CENTRED_FROM_DP
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Sheet(centred, onDismiss, modifier, content)
    }
}

@Composable
private fun Sheet(centred: Boolean, onDismiss: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Where it rests (0), or down out of sight (1). At the foot of the screen it starts down out of sight and comes up;
    // in the middle it starts where it rests, and fades in.
    val state = remember { AnchoredDraggableState(initialValue = if (centred) 0 else 1) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableFloatStateOf(if (centred) 0f else 1f) }
    val fade by animateFloatAsState(shown, SheetMotion, label = "sheet")
    // Let go moving up, it goes back up; flicked down, or let go a good way down, it closes; otherwise back up it comes.
    val flickAt = with(density) { CLOSE_FLICK.toPx() }
    val close: () -> Unit = { if (state.settledValue == 0) scope.launch { state.animateTo(1, SheetMotion) } }
    // Let go after it (or what's in it) was dragged: by which way it was going, how fast, and how far it's come down.
    val settle: (Float) -> Unit = { velocity ->
        scope.launch {
            val target = when {
                velocity < 0f -> 0
                velocity > flickAt -> 1
                state.offset > sheetHeight * CLOSE_SHARE -> 1
                else -> 0
            }
            state.animateTo(target, SheetMotion)
        }
    }
    // Held by its tabs or its edge, it's dragged the same way, and let go, it settles by the same rule.
    val dragging = rememberDraggableState { delta -> state.dispatchRawDelta(delta) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = close),
        contentAlignment = if (centred) Alignment.Center else Alignment.BottomCenter,
    ) {
        val screen = constraints.maxHeight.toFloat()
        Surface(
            modifier = Modifier
                .then(if (centred) Modifier.requiredWidthIn(max = 460.dp) else Modifier.widthIn(max = 460.dp))
                .clickable(interactionSource = null, indication = null, onClick = {})
                // Scrolled to the top of what's in it, a drag down takes the sheet down with it.
                .nestedScroll(remember(state) { state.sheetScrolling(settle) })
                .then(modifier)
                // Down by its own height (or, in the middle, by half of the screen and itself), it's out of sight.
                .onSizeChanged { size ->
                    sheetHeight = size.height.toFloat()
                    state.updateAnchors(DraggableAnchors {
                        0 at 0f
                        1 at if (centred) (screen + size.height) / 2f else size.height.toFloat()
                    })
                }
                .offset { IntOffset(0, state.offset.takeIf { it.isFinite() }?.roundToInt() ?: 0) }
                .then(
                    // In the middle, it fades in as it opens (and slides away, as at the foot, as it closes).
                    if (centred) Modifier.graphicsLayer { alpha = fade } else Modifier,
                )
                .draggable(
                    dragging,
                    Orientation.Vertical,
                    startDragImmediately = state.isAnimationRunning,
                    // Caught as it moves, it stops where it is.
                    onDragStarted = { state.anchoredDrag(MutatePriority.UserInput) {} },
                    onDragStopped = { velocity -> settle(velocity) },
                )
                .then(
                    if (centred) Modifier.systemBarsPadding().padding(vertical = 16.dp)
                    else Modifier.navigationBarsPadding().statusBarsPadding(),
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            BackHandler(enabled = state.targetValue == 0, onBack = close)
            content()
        }
    }
    LaunchedEffect(state) {
        // Once its height is known, up it comes (or, in the middle, in it fades); down again, it's gone.
        snapshotFlow { state.anchors.size }.first { it > 0 }
        if (centred) {
            shown = 1f
        } else {
            launch { state.animateTo(0, SheetMotion) }
        }
        snapshotFlow { state.settledValue }.drop(1).filter { it == 1 }.first()
        onDismiss()
    }
}

/**
 * What's in the sheet scrolls first; at its top, a drag down moves the sheet (and a drag up brings it back up before
 * what's in it scrolls). Let go, it settles up or down.
 */
private fun AnchoredDraggableState<Int>.sheetScrolling(settle: (Float) -> Unit) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0 && source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    // Let go once the sheet's been pulled down, it settles (just the once) and what's in it doesn't fling; let go with
    // the sheet up, what's in it flings as it would, and a fling up to its top doesn't close it.
    override suspend fun onPreFling(available: Velocity): Velocity =
        if (offset > anchors.minPosition()) {
            settle(available.y)
            available
        } else Velocity.Zero
}
