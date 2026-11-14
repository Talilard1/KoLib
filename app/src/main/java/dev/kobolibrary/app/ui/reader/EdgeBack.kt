package dev.kobolibrary.app.ui.reader

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * KoLib's own back swipe, for the reader: in from either side edge, with an arrow that grows out of the
 * edge where the finger is, as Android's does, but in the app's colours. Let go once it fills in to go back.
 */
@Stable
internal class EdgeBackState {
    /** The edge the swipe came in from: -1 the left, 1 the right, 0 none (nothing to show). */
    var side by mutableIntStateOf(0)
    /** How far the finger has come in from where the swipe was taken, in px. */
    var pull by mutableFloatStateOf(0f)
    /** Where the finger is down the screen, in px. */
    var y by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    /** Far enough in that letting go goes back. */
    var armed by mutableStateOf(false)
    /** Goes up by one each time a swipe ends, to play the arrow going back into the edge. */
    var ended by mutableIntStateOf(0)

    fun begin(side: Int, y: Float) {
        this.side = side
        this.y = y
        pull = 0f
        armed = false
        dragging = true
    }

    fun end() {
        dragging = false
        ended++
    }

    companion object {
        /** How near the edge a swipe has to start. */
        val ZONE = 24.dp
        /** How far in it has to come to go back. */
        val THRESHOLD = 64.dp
        /** The arrow's height (its width when fully out: a circle). */
        val SIZE = 44.dp
        /** How far its inner side ends up from the edge, fully out. */
        val GAP = 14.dp
        /** How far above the finger it shows, as Android's does, so the finger doesn't hide it. */
        val ABOVE = 60.dp
    }
}

/**
 * Watches for swipes in from the side edges over everything inside: one that's taken gets the rest of
 * the touch (the page and anything over it see it cancelled), and [onBack] runs if it's let go far enough in.
 * Taps and up-and-down swipes at the edges are left alone.
 */
@Composable
internal fun Modifier.edgeBackSwipe(state: EdgeBackState, enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    val back by rememberUpdatedState(onBack)
    val view = LocalView.current
    return this.pointerInput(state) {
        val zone = EdgeBackState.ZONE.toPx()
        val threshold = EdgeBackState.THRESHOLD.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val side = when {
                down.position.x <= zone -> -1
                down.position.x >= size.width - zone -> 1
                else -> 0
            }
            if (side == 0) return@awaitEachGesture
            val start = down.position
            var taken = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // A second finger before it's taken: a pinch or the like, not a swipe back.
                    if (!taken && event.changes.size > 1) return@awaitEachGesture
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed) {
                        if (taken) {
                            change.consume()
                            val go = state.armed
                            taken = false
                            state.end()
                            if (go) back()
                        }
                        return@awaitEachGesture
                    }
                    val inward = (change.position.x - start.x) * -side
                    val across = abs(change.position.y - start.y)
                    if (!taken) {
                        when {
                            inward > slop && inward > across * 1.5f -> {
                                taken = true
                                state.begin(side, change.position.y)
                            }
                            across > slop || inward < -slop -> return@awaitEachGesture
                        }
                    }
                    if (taken) {
                        change.consume()
                        state.pull = max(0f, inward - slop)
                        state.y = change.position.y
                        val armed = state.pull >= threshold
                        if (armed != state.armed) {
                            state.armed = armed
                            view.performHapticFeedback(
                                when {
                                    Build.VERSION.SDK_INT >= 34 && armed -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                                    Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
                                    armed -> HapticFeedbackConstants.CONTEXT_CLICK
                                    else -> HapticFeedbackConstants.CLOCK_TICK
                                }
                            )
                        }
                    }
                }
            } finally {
                // Cancelled part way (the reader closing, the system taking the touch): the arrow goes back in.
                if (taken) state.end()
            }
        }
    }
}

/** The arrow for [state], drawn over everything; it plays its way back into the edge when a swipe ends. */
@Composable
internal fun EdgeBackIndicator(state: EdgeBackState) {
    val retract = remember { Animatable(0f) }
    LaunchedEffect(state.ended) {
        if (state.ended == 0) return@LaunchedEffect
        retract.snapTo(0f)
        retract.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        if (!state.dragging) state.side = 0
    }
    LaunchedEffect(state.dragging) { if (state.dragging) retract.snapTo(0f) }
    val fill by animateFloatAsState(if (state.armed && state.dragging) 1f else 0f, tween(140), label = "armed")
    if (state.side == 0) return
    EdgeBackArrow(state.side, state.pull, state.y, if (state.dragging) 0f else retract.value, fill)
}

/**
 * The arrow itself, at [side] and [y]: [pull] px in from the edge, [retract] 0 to 1 on its way back into
 * the edge, [armed] 0 to 1 from the app's surface colour to its accent colour.
 */
@Composable
internal fun EdgeBackArrow(side: Int, pull: Float, y: Float, retract: Float, armed: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val idleFill = scheme.surfaceContainerHighest
    val idleArrow = scheme.primary
    val armedFill = scheme.primary
    val armedArrow = scheme.onPrimary
    val rim = scheme.outline
    Canvas(modifier.fillMaxSize()) {
        val h = EdgeBackState.SIZE.toPx()
        val threshold = EdgeBackState.THRESHOLD.toPx()
        // How far out it is (1 at the point where letting go goes back), falling back as it retracts.
        val p = (pull / threshold) * (1f - retract)
        // A narrow capsule in the edge, swelling into a circle, then moving in off the edge.
        val minW = 12.dp.toPx()
        var w = lerpF(minW, h, smooth(0f, 0.55f, p))
        val out = smooth(0.3f, 1f, p)
        val gap = lerpF(-w * 0.35f, EdgeBackState.GAP.toPx(), out) + min(max(p - 1f, 0f), 1f) * 6.dp.toPx()
        // Going back in, it thins to a line in the edge before it's gone.
        if (retract > 0f) w = lerpF(w, 3.dp.toPx(), smooth(0.35f, 1f, retract))
        val alpha = 1f - smooth(0.65f, 1f, retract)
        if (alpha <= 0f) return@Canvas
        val cy = (y - EdgeBackState.ABOVE.toPx()).coerceIn(h, size.height - h)
        val fromEdge = w / 2f + gap
        val cx = if (side < 0) fromEdge else size.width - fromEdge
        val radius = min(w, h) / 2f

        val fillColor = lerp(idleFill, armedFill, armed).copy(alpha = (0.94f * alpha))
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor.toArgb()
                setShadowLayer(6.dp.toPx(), 0f, 1.5.dp.toPx(), Color.Black.copy(alpha = 0.28f * alpha).toArgb())
            }
            c.nativeCanvas.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, radius, radius, paint)
        }
        // A fine rim, so it shows on a page of any colour.
        drawRoundRectOutline(cx, cy, w, h, radius, rim.copy(alpha = 0.45f * alpha * (1f - armed)))

        // The back arrow, "<", fading in as the circle forms.
        val arrowAlpha = smooth(0.25f, 0.7f, p) * alpha
        if (arrowAlpha > 0f) {
            val ah = 7.dp.toPx()
            val aw = 4.dp.toPx() * smooth(0.25f, 0.8f, p).coerceAtLeast(0.4f)
            val path = Path().apply {
                moveTo(cx + aw / 2f, cy - ah)
                lineTo(cx - aw / 2f, cy)
                lineTo(cx + aw / 2f, cy + ah)
            }
            drawPath(
                path,
                lerp(idleArrow, armedArrow, armed).copy(alpha = arrowAlpha),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun DrawScope.drawRoundRectOutline(cx: Float, cy: Float, w: Float, h: Float, radius: Float, color: Color) {
    if (color.alpha <= 0f) return
    drawRoundRect(
        color,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 0 below [a], 1 above [b], and an S-curve between. */
private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
