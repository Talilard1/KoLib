package dev.kolib.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
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

/** At least this wide (its shorter side), a screen has the sheet in its middle, faded in, as Mihon does on big tablets. */
private const val CENTRED_FROM_DP = 720

/**
 * A sheet as Mihon's: from the foot of the screen, at most 460 dp wide, sliding up as it opens and down (dragged, or a
 * tap beside it, or Back) as it closes; or, on a big tablet, in the middle of the screen, fading in.
 */
@Composable
fun AdaptiveSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val centred = LocalConfiguration.current.smallestScreenWidthDp >= CENTRED_FROM_DP
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        if (centred) CentredSheet(onDismiss, modifier, content) else FootSheet(onDismiss, modifier, content)
    }
}

@Composable
private fun CentredSheet(onDismiss: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableFloatStateOf(0f) }
    val alpha by animateFloatAsState(shown, SheetMotion, label = "sheet")
    val close = {
        shown = 0f
        onDismiss()
    }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = close).alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .requiredWidthIn(max = 460.dp)
                .clickable(interactionSource = null, indication = null, onClick = {})
                .systemBarsPadding()
                .padding(vertical = 16.dp)
                .then(modifier),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            BackHandler(enabled = alpha > 0f, onBack = close)
            content()
        }
        LaunchedEffect(Unit) { shown = 1f }
    }
}

@Composable
private fun FootSheet(onDismiss: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Up (0) or down out of sight (1): it starts down, and comes up.
    val state = remember { AnchoredDraggableState(initialValue = 1) }
    val fling = AnchoredDraggableDefaults.flingBehavior(state, positionalThreshold = { with(density) { 56.dp.toPx() } }, animationSpec = SheetMotion)
    val close: () -> Unit = { if (state.settledValue == 0) scope.launch { state.animateTo(1, SheetMotion) } }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = close)
            .onSizeChanged { size ->
                state.updateAnchors(DraggableAnchors {
                    0 at 0f
                    1 at size.height.toFloat()
                })
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .clickable(interactionSource = null, indication = null, onClick = {})
                // Scrolled to the top of what's in it, a drag down takes the sheet down with it.
                .nestedScroll(remember(state) { state.sheetScrolling { scope.launch { state.animateTo(state.targetValue, SheetMotion) } } })
                .then(modifier)
                .offset { IntOffset(0, state.offset.takeIf { it.isFinite() }?.roundToInt() ?: 0) }
                .anchoredDraggable(state, Orientation.Vertical, flingBehavior = fling)
                .navigationBarsPadding()
                .statusBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            BackHandler(enabled = state.targetValue == 0, onBack = close)
            content()
        }
    }
    LaunchedEffect(state) {
        // Once its height is known, up it comes; down again, it's gone.
        snapshotFlow { state.anchors.size }.first { it > 0 }
        launch { state.animateTo(0, SheetMotion) }
        snapshotFlow { state.settledValue }.drop(1).filter { it == 1 }.first()
        onDismiss()
    }
}

/**
 * What's in the sheet scrolls first; at its top, a drag down moves the sheet (and a drag up brings it back up before
 * what's in it scrolls). Let go, it settles up or down.
 */
private fun AnchoredDraggableState<Int>.sheetScrolling(settle: () -> Unit) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0 && source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y < 0 && offset > anchors.minPosition()) {
            settle()
            available
        } else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle()
        return if (targetValue != settledValue) available else Velocity.Zero
    }
}
