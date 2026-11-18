package dev.kolib.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.kolib.app.tr
import dev.kolib.app.ui.setup.GuideHeader
import dev.kolib.app.ui.setup.SetupBody
import dev.kolib.app.ui.setup.SetupButton
import dev.kolib.app.ui.setup.SetupColours
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the parts of a screen that a tour stops at are, in the window: each part tells its place here as
 * it's laid out (see [tourPlace]), so the tour lights it where it really is, however the screen is arranged.
 */
class TourPlaces {
    internal val rects = mutableStateMapOf<String, Rect>()

    /** The space [names] take up together; null when none of them is on screen. */
    fun rectOf(names: List<String>): Rect? = names.mapNotNull { rects[it] }
        .reduceOrNull { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
}

/** The places of the tour being given on this screen, if one is. */
val LocalTourPlaces = staticCompositionLocalOf<TourPlaces?> { null }

/** Marks this as a place a tour can stop at, called [name]. It does nothing while no tour is being given. */
fun Modifier.tourPlace(name: String): Modifier = composed {
    val places = LocalTourPlaces.current ?: return@composed this
    DisposableEffect(places, name) { onDispose { places.rects.remove(name) } }
    onGloballyPositioned { c -> if (c.isAttached) places.rects[name] = Rect(c.positionInWindow(), c.size.toSize()) }
}

/** One stop of a tour: some places lit together, and what's said about them. */
class TourStop(
    /** The places it lights, as one; the stop is passed over when none of them is on screen. */
    val places: List<String>,
    val title: String,
    val text: String,
    /** Brings its places into view before they're lit (scrolling a row of buttons along to them, say). */
    val reveal: (suspend () -> Unit)? = null,
)

/** Which side of what's lit the tour's card sits on. */
private enum class CardSide { BELOW, ABOVE, RIGHT, LEFT, OVER }

/**
 * A tour of a screen, over all of it: the screen is dimmed but for the part each stop is about, which is
 * ringed, with a card beside it saying what it's for. The screen under it can't be touched meanwhile.
 * [onDone] is told when the last stop is passed, or the tour is skipped.
 */
@Composable
fun Tour(stops: List<TourStop>, places: TourPlaces, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var at by rememberSaveable { mutableIntStateOf(0) }
    val present = stops.filter { places.rectOf(it.places) != null }
    val stop = stops.getOrNull(at)
    val target = stop?.let { places.rectOf(it.places) }
    fun step(by: Int) {
        var i = at + by
        while (i in stops.indices && places.rectOf(stops[i].places) == null) i += by
        when {
            i in stops.indices -> at = i
            by > 0 -> onDone()
        }
    }
    val first = present.firstOrNull() == stop
    BackHandler { if (first) onDone() else step(-1) }
    // A stop whose places aren't on this screen (as it's arranged on this device) is passed over, once the screen
    // has had a moment to lay itself out.
    LaunchedEffect(at, target == null) {
        if (target == null) {
            delay(300)
            if (stop == null) onDone() else if (places.rectOf(stop.places) == null) step(1)
        }
    }
    LaunchedEffect(at) { stop?.reveal?.invoke() }

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.4f
    val c = if (dark) SetupColours.Dark else SetupColours.Light
    var origin by remember { mutableStateOf(Offset.Zero) }
    val appear by animateFloatAsState(if (target != null) 1f else 0f, tween(320), label = "appear")
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .testTag("tour"),
    ) {
        // Nothing under the tour can be touched: touches stop at this layer, which lies under the card and
        // isn't around it. (Around it, it would take every movement of a finger or pen on the card's buttons
        // too, and a tap that moves at all, as a pen's always does, would be dropped by the button.)
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pad = with(density) { 6.dp.toPx() }
        // What's lit, a little larger than the part itself, and never off the screen's edges.
        var last by remember { mutableStateOf<Rect?>(null) }
        if (target != null) last = target
        val lit = (target ?: last)?.translate(-origin)?.let { r ->
            Rect(max(r.left - pad, 2f), max(r.top - pad, 2f), min(r.right + pad, w - 2f), min(r.bottom + pad, h - 2f))
        }
        val hole by animateRectAsState(lit ?: Rect(w / 2, h / 2, w / 2, h / 2), spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), label = "hole")
        val corner = with(density) { 14.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = appear }) {
            drawRect(Color.Black.copy(alpha = if (dark) 0.66f else 0.56f))
            drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(corner), blendMode = BlendMode.Clear)
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = appear }) {
            drawRoundRect(c.button, hole.topLeft, hole.size, CornerRadius(corner), style = Stroke(2.dp.toPx()))
        }

        // The card: under what's lit if there's room, else over it, beside it, or (when it fills the screen) on it.
        val gap = with(density) { 14.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val cardWidth = min(with(density) { 340.dp.toPx() }, w - 2 * margin)
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val where = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
        var side by remember { mutableStateOf(CardSide.OVER) }
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(lit, cardSize, w, h) {
            val r = lit ?: return@LaunchedEffect
            if (cardSize == IntSize.Zero) return@LaunchedEffect
            val cw = cardSize.width.toFloat()
            val ch = cardSize.height.toFloat()
            fun across(x: Float) = x.coerceIn(margin, max(margin, w - margin - cw))
            fun down(y: Float) = y.coerceIn(margin, max(margin, h - margin - ch))
            val (to, s) = when {
                r.bottom + gap + ch <= h - margin -> Offset(across(r.center.x - cw / 2), r.bottom + gap) to CardSide.BELOW
                r.top - gap - ch >= margin -> Offset(across(r.center.x - cw / 2), r.top - gap - ch) to CardSide.ABOVE
                r.right + gap + cw <= w - margin -> Offset(r.right + gap, down(r.center.y - ch / 2)) to CardSide.RIGHT
                r.left - gap - cw >= margin -> Offset(r.left - gap - cw, down(r.center.y - ch / 2)) to CardSide.LEFT
                else -> Offset(across(r.center.x - cw / 2), down(r.center.y - ch / 2)) to CardSide.OVER
            }
            side = s
            val offset = IntOffset(to.x.roundToInt(), to.y.roundToInt())
            if (!placed) {
                where.snapTo(offset)
                placed = true
            } else where.animateTo(offset, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
        }
        val show = appear * if (placed) 1f else 0f
        // A point from the card toward what it's about.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = show }) {
            val r = lit ?: return@Canvas
            val at2 = where.value
            val cw = cardSize.width.toFloat()
            val ch = cardSize.height.toFloat()
            val half = 9.dp.toPx()
            val out = 8.dp.toPx()
            val inset = 30.dp.toPx()
            val x = r.center.x.coerceIn(at2.x + inset, max(at2.x + inset, at2.x + cw - inset))
            val y = r.center.y.coerceIn(at2.y + inset, max(at2.y + inset, at2.y + ch - inset))
            val p = Path()
            when (side) {
                CardSide.BELOW -> { p.moveTo(x - half, at2.y + 1f); p.lineTo(x, at2.y - out); p.lineTo(x + half, at2.y + 1f) }
                CardSide.ABOVE -> { p.moveTo(x - half, at2.y + ch - 1f); p.lineTo(x, at2.y + ch + out); p.lineTo(x + half, at2.y + ch - 1f) }
                CardSide.RIGHT -> { p.moveTo(at2.x + 1f, y - half); p.lineTo(at2.x - out, y); p.lineTo(at2.x + 1f, y + half) }
                CardSide.LEFT -> { p.moveTo(at2.x + cw - 1f, y - half); p.lineTo(at2.x + cw + out, y); p.lineTo(at2.x + cw - 1f, y + half) }
                CardSide.OVER -> return@Canvas
            }
            p.close()
            drawPath(p, c.card)
        }
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .offset { where.value }
                .width(with(density) { cardWidth.toDp() })
                .heightIn(max = with(density) { (h - 2 * margin).toDp() })
                .graphicsLayer { alpha = show }
                .onSizeChanged { cardSize = it }
                .shadow(24.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(shape)
                .background(c.card)
                .border(0.5.dp, c.line, shape)
                // Touches on the card are its own: none fall through it.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .verticalScroll(rememberScrollState())
                .testTag("tour-card"),
        ) {
            val shownAt = present.indexOf(stop).coerceAtLeast(0)
            GuideHeader(present.size.coerceAtLeast(1), shownAt, c, onBack = if (first) null else ({ step(-1) }), onSkip = if (stop == present.lastOrNull()) null else onDone)
            AnimatedContent(
                targetState = stop,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 70)) togetherWith fadeOut(tween(120)))
                        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) })
                },
                label = "stop",
            ) { shown ->
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
                    if (shown != null) {
                        Text(shown.title, color = c.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }.testTag("tour-title"))
                        Text(shown.text, color = c.text.copy(alpha = 0.84f), style = SetupBody, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    SetupButton(if (shown == present.lastOrNull()) tr("Done") else tr("Next"), c, Modifier.testTag("tour-next"), height = 46.dp) { step(1) }
                }
            }
        }
    }
}
