package dev.kolib.app.ui.components

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

// The floating windows (looking a word up, summaries and questions, the editor's assistant) are as big as
// what's in them, and follow it as it changes: quickly, and settling without a wobble.
private const val STIFFNESS = 380f

/**
 * What this holds changing size is followed smoothly rather than jumped to. On the part of a window that
 * grows (an answer being written), what's below it rides down with its edge.
 */
fun Modifier.grows(): Modifier = animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = STIFFNESS, visibilityThreshold = IntSize(1, 1)))

/** A part of a floating window that comes and goes: it opens out and closes up, and the window with it. */
@Composable
fun ColumnScope.Part(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible, modifier,
        enter = expandVertically(spring(Spring.DampingRatioNoBouncy, STIFFNESS, IntSize(1, 1)), expandFrom = Alignment.Top) + fadeIn(tween(220, delayMillis = 50)),
        exit = shrinkVertically(spring(Spring.DampingRatioNoBouncy, STIFFNESS, IntSize(1, 1)), shrinkTowards = Alignment.Top) + fadeOut(tween(90)),
    ) { content() }
}

/** The same for a part that shows something that may not be there: while it closes up, it goes on showing what was. */
@Composable
fun <T : Any> ColumnScope.PartOf(value: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    Part(value != null, modifier) {
        @Suppress("UNCHECKED_CAST")
        (last[0] as T?)?.let { content(it) }
    }
}

/** Text that may still be being written: while it is, a cursor blinks at the end of it. */
@Composable
fun StreamingText(text: String, writing: Boolean, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    if (!writing) {
        Text(text, modifier, color = color, style = style)
        return
    }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        1f, 0.15f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cursor",
    )
    val ink = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            append(text)
            appendInlineContent("cursor", " ")
        },
        modifier, color = color, style = style,
        inlineContent = mapOf(
            "cursor" to InlineTextContent(Placeholder(0.55.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                Box(Modifier.fillMaxSize().padding(start = 2.dp).graphicsLayer { alpha = blink }.background(ink, RoundedCornerShape(2.dp)))
            },
        ),
    )
}

/**
 * A window floating over the screen, in the middle of it: as wide as [maxWidth] allows and as tall as what's
 * in it, up to the room there is (what's in it is given at most that, to scroll within). It stays clear of
 * the keyboard, and a tap outside it closes it.
 */
@Composable
fun FloatingWindow(onDismiss: () -> Unit, modifier: Modifier = Modifier, maxWidth: Dp = 560.dp, content: @Composable ColumnScope.() -> Unit) {
    // The window itself fills the screen and stays still: only the card in it changes size, which is what keeps that smooth.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A dialog is a window of its own: a pen's tap on a text box in it opens the keyboard, as in the app's other dialogs.
        PenTapsOpenTheKeyboard {
        val shown = remember { Animatable(0f) }
        LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 420f)) }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }.systemBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, shadowElevation = 12.dp,
                modifier = modifier
                    .widthIn(max = maxWidth).fillMaxWidth()
                    .graphicsLayer {
                        val s = 0.95f + 0.05f * shown.value
                        scaleX = s
                        scaleY = s
                    }
                    // A tap on the window isn't a tap outside it.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(content = content)
            }
        }
        }
    }
}

/**
 * A small card floating by some words on the page, with a pointer to them: under them where there's room,
 * over them otherwise. It's as tall as what's in it, up to the room there is, opens out from the words, and
 * a tap anywhere else (or Back) closes it. [place] is where the words are, in dp from the top left of what
 * this fills, and [lines] the same a line at a time: those are marked while the card is open. [content] is
 * given a way to close the card that lets it fold away first.
 */
@Composable
fun PointerCard(
    place: RectF, lines: List<RectF>, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 460f)) }
    // (Not called "close": a path being drawn has a close() of its own.)
    val fold: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                shown.animateTo(0f, tween(130))
                onDismiss()
            }
        }
    }
    BackHandler(onBack = fold)
    val mark = MaterialTheme.colorScheme.primary
    val card = MaterialTheme.colorScheme.surfaceContainerHigh
    val edge = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { fold() } }) {
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val top = safe.calculateTopPadding() + 8.dp
        val bottom = maxHeight - safe.calculateBottomPadding() - 8.dp
        val side = 16.dp
        val width = min(maxWidth - side * 2, 380.dp)
        val gap = 3.dp
        val tip = 9.dp
        // Room for the shadow, inside what's faded and scaled as the card opens.
        val around = 28.dp
        val roomUnder = bottom - (place.bottom.dp + gap + tip)
        val roomOver = (place.top.dp - gap - tip) - top
        val under = roomUnder >= 240.dp || roomUnder >= roomOver
        val room = min(if (under) roomUnder else roomOver, maxHeight * 0.62f).coerceAtLeast(120.dp)
        val x = (place.centerX().dp - width / 2).coerceIn(side, (maxWidth - side - width).coerceAtLeast(side))
        val tipX = (place.centerX().dp - x).coerceIn(24.dp, (width - 24.dp).coerceAtLeast(24.dp))

        // The words being looked up, marked.
        Canvas(Modifier.fillMaxSize()) {
            val a = 0.2f * shown.value.coerceIn(0f, 1f)
            lines.forEach { l ->
                drawRoundRect(
                    mark.copy(alpha = a), topLeft = Offset((l.left - 2f).dp.toPx(), (l.top - 1f).dp.toPx()),
                    size = Size((l.width() + 4f).dp.toPx(), (l.height() + 2f).dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints(minWidth = (width + around * 2).roundToPx(), maxWidth = (width + around * 2).roundToPx(), maxHeight = (room + tip + around * 2).roundToPx()))
                    // Under the words its top stays put as it grows; over them, its bottom does.
                    val y = if (under) (place.bottom.dp + gap - around).roundToPx() else (place.top.dp - gap + around).roundToPx() - p.height
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place((x - around).roundToPx(), y) }
                }
                .graphicsLayer {
                    val v = shown.value
                    alpha = v.coerceIn(0f, 1f)
                    scaleX = 0.88f + 0.12f * v
                    scaleY = scaleX
                    // It opens out from the tip of its pointer.
                    transformOrigin = TransformOrigin(
                        ((around + tipX).toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        if (under) around.toPx() / size.height.coerceAtLeast(1f) else 1f - around.toPx() / size.height.coerceAtLeast(1f),
                    )
                }
                .drawWithContent {
                    drawContent()
                    val half = 9.dp.toPx()
                    val cx = (around + tipX).toPx()
                    val over = 1.5.dp.toPx()
                    val base = if (under) (around + tip).toPx() else size.height - (around + tip).toPx()
                    val apex = if (under) around.toPx() else size.height - around.toPx()
                    // The pointer covers the card's edge where it joins it, and carries the edge up its own two sides.
                    val inside = if (under) base + over else base - over
                    drawPath(Path().apply { moveTo(cx - half, inside); lineTo(cx - half, base); lineTo(cx, apex); lineTo(cx + half, base); lineTo(cx + half, inside); this.close() }, card)
                    drawLine(edge, Offset(cx - half, base), Offset(cx, apex), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(edge, Offset(cx, apex), Offset(cx + half, base), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                }
                .padding(around)
                .padding(top = if (under) tip else 0.dp, bottom = if (under) 0.dp else tip),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp), color = card, shadowElevation = 10.dp, border = BorderStroke(1.dp, edge),
                modifier = modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column { content(fold) }
            }
        }
    }
}
