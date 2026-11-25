package dev.kolib.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Setting where, in the contents, a swipe across goes to the next tab (above the line) or closes them (below it). First
 * the contents are swiped closed as they would be with the phone held as usual, and the line goes just above that swipe;
 * then either side of it is shown, to try, and it can be moved by hand, and tried again, till it's right.
 */
@Stable
internal class TabLineSetup(start: Float) {
    /** Where the line is, how far down the contents: 0 to 1 of their height. */
    var line by mutableFloatStateOf(start.coerceIn(ReaderSettings.TAB_LINE_MIN, ReaderSettings.TAB_LINE_MAX))
    /** The swipe to close has been made and the line set from it: what's either side of the line is shown, to try. */
    var measured by mutableStateOf(false)
    /** What the last swipe tried did: [TRIED_TAB] or [TRIED_CLOSE] (0 for nothing yet). */
    var tried by mutableIntStateOf(0)
    /** Goes up with each swipe tried, to say what it did again. */
    var tries by mutableIntStateOf(0)
    /** Where the contents are, for a swipe anywhere over them (or beside them) to be measured against. */
    internal var contents: LayoutCoordinates? = null

    /** A swipe to close made [top] (its highest point) down the contents, [height] tall: the line goes [margin] over it. */
    fun measure(top: Float, height: Float, margin: Float) {
        line = lineOver(top, height, margin)
        measured = true
    }

    fun tried(what: Int) {
        tried = what
        tries++
    }

    companion object {
        const val TRIED_TAB = 1
        const val TRIED_CLOSE = 2

        /** The line for a swipe to close whose highest point is [top] down [height]: [margin] over it, kept in bounds. */
        fun lineOver(top: Float, height: Float, margin: Float): Float =
            if (height <= 0f) ReaderSettings.TAB_LINE
            else ((top - margin) / height).coerceIn(ReaderSettings.TAB_LINE_MIN, ReaderSettings.TAB_LINE_MAX)
    }
}

/**
 * Watches the swipes over the contents and beside them while [setup] runs (without taking them: the tabs and the panel
 * still do what they do): the first swipe to close sets the line, and after that, one to close below the line (or
 * beside the contents) is said to close them. For the whole screen, under the panel as it slides, so how far across a
 * swipe goes is the finger's; how far down, the contents' own.
 */
internal fun Modifier.watchesTabLine(setup: TabLineSetup, closing: Float): Modifier = composed {
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { place[0] = it }.pointerInput(setup, closing) {
        val needed = 32.dp.toPx()
        val margin = 28.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            // Where in the contents a point here is (null till they're laid out).
            fun inContents(at: Offset): Offset? {
                val here = place[0]?.takeIf { it.isAttached } ?: return null
                val c = setup.contents?.takeIf { it.isAttached } ?: return null
                return c.localPositionOf(here, at)
            }
            val start = down.position
            val from = inContents(start)
            var end = start
            var top = from?.y ?: start.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                end = change.position
                inContents(change.position)?.let { top = minOf(top, it.y) }
                if (!change.pressed) break
            }
            val across = (end.x - start.x) * closing
            if (across < needed || abs(end.x - start.x) < abs(end.y - start.y) || from == null) return@awaitEachGesture
            val size = setup.contents?.size ?: return@awaitEachGesture
            if (!setup.measured) {
                setup.measure(top, size.height.toFloat(), margin)
            } else {
                // Beside the contents a swipe closes them wherever it is; over them, below the line.
                val beside = from.x < 0f || from.x > size.width
                if (beside || from.y > size.height * setup.line) setup.tried(TabLineSetup.TRIED_CLOSE)
            }
        }
    }
}

/**
 * Over the contents while [setup] runs. First, what to do: swipe them closed as usual (or keep the line as it is). Then
 * the line, with what a swipe across does either side of it, a handle to drag it up or down, what each swipe tried did,
 * and the way to swipe again or say it's right. Taps don't reach the contents under it; swipes across do.
 */
@Composable
internal fun TabLineOverlay(setup: TabLineSetup, pager: PagerState, onKeep: () -> Unit, onDone: (Float) -> Unit) {
    // A tab changed by a swipe tried is said to have changed.
    LaunchedEffect(pager, setup) {
        snapshotFlow { pager.settledPage }.drop(1).collect { if (setup.measured) setup.tried(TabLineSetup.TRIED_TAB) }
    }
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("tabline"),
    ) {
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        if (!setup.measured) {
            Surface(
                color = scheme.surfaceContainerHighest,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp).widthIn(max = 360.dp).testTag("tabline-measure"),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.SwipeLeft, null, Modifier.size(34.dp), tint = scheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Swipe the contents closed"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("Hold your phone as you usually do, and swipe across with your thumb, without stretching. Above where you swipe, a swipe across will change tab."),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onKeep, modifier = Modifier.testTag("tabline-keep")) { Text(tr("Keep it as it is")) }
                }
            }
            return@BoxWithConstraints
        }
        val lineY = setup.line * h
        // Above the line in the accent colour (the tabs'), below it plain: either side shaded, with the line across.
        val above = scheme.primary
        val below = scheme.onSurfaceVariant
        Canvas(Modifier.fillMaxSize()) {
            drawRect(above.copy(alpha = 0.14f), size = Size(size.width, lineY))
            drawRect(scheme.onSurface.copy(alpha = 0.06f), topLeft = Offset(0f, lineY), size = Size(size.width, size.height - lineY))
            drawRect(above, topLeft = Offset(0f, lineY - 1.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
        }
        val tried = setup.tried
        var saying by remember { mutableStateOf(false) }
        LaunchedEffect(setup.tries) {
            if (setup.tries == 0) return@LaunchedEffect
            saying = true
            delay(1400)
            saying = false
        }
        // What a swipe does either side, beside the line.
        ZoneLabel(
            if (saying && tried == TabLineSetup.TRIED_TAB) tr("Changed tab") else tr("Swipe across here to change tab"),
            above, saying && tried == TabLineSetup.TRIED_TAB,
            Modifier.offset { IntOffset(0, (lineY - with(density) { 40.dp.toPx() }).roundToInt()) }.padding(start = 16.dp).testTag("tabline-above"),
        )
        ZoneLabel(
            if (saying && tried == TabLineSetup.TRIED_CLOSE) tr("That closes them") else tr("Swipe across here to close"),
            below, saying && tried == TabLineSetup.TRIED_CLOSE,
            Modifier.offset { IntOffset(0, (lineY + with(density) { 12.dp.toPx() }).roundToInt()) }.padding(start = 16.dp).testTag("tabline-below"),
        )
        // The handle on the line, to drag it up or down.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (lineY - with(density) { 24.dp.toPx() }).roundToInt()) }
                .padding(end = 12.dp)
                .size(48.dp)
                .pointerInput(setup) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        setup.line = ((setup.line * h + dy) / h).coerceIn(ReaderSettings.TAB_LINE_MIN, ReaderSettings.TAB_LINE_MAX)
                    }
                }
                .testTag("tabline-handle"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 44.dp, height = 28.dp).background(above, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.UnfoldMore, tr("Move the line"), Modifier.size(20.dp), tint = scheme.onPrimary)
            }
        }
        // What to do now, and the way on: at the foot, under the line; or, the line's too low for that, over it (under the
        // tabs, not over them, so a tab changed is seen to).
        var barHeight by remember { mutableIntStateOf(0) }
        val gap = with(density) { 12.dp.toPx() }
        val fitsUnder = h - lineY >= barHeight + with(density) { 64.dp.toPx() }
        val barTop = if (fitsUnder) h - barHeight - gap else (lineY - with(density) { 56.dp.toPx() } - barHeight).coerceAtLeast(with(density) { 140.dp.toPx() })
        Surface(
            color = scheme.surfaceContainerHighest.copy(alpha = 0.97f),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset(0, barTop.roundToInt()) }
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .onSizeChanged { barHeight = it.height }
                .testTag("tabline-bar"),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)) {
                Text(
                    tr("Try swiping across above and below the line. Drag the line to move it."),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { setup.measured = false }, modifier = Modifier.testTag("tabline-again")) { Text(tr("Swipe again")) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onDone(setup.line) }, modifier = Modifier.testTag("tabline-done")) { Text(tr("Looks good")) }
                }
            }
        }
    }
}

/** What a swipe across does on one side of the line, in that side's colour; [lit] while saying what a swipe just did. */
@Composable
private fun ZoneLabel(text: String, colour: androidx.compose.ui.graphics.Color, lit: Boolean, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (lit) MaterialTheme.colorScheme.surface else colour,
        modifier = modifier
            .background(if (lit) colour else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
