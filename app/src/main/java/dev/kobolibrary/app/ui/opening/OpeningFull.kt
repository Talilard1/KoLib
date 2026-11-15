package dev.kobolibrary.app.ui.opening

import dev.kobolibrary.app.tr
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.kobolibrary.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The opening in full, at [time] seconds in, drawn at [alpha]. */
@Composable
internal fun FullOpening(time: () -> Float, alpha: () -> Float, modifier: Modifier) {
    val art = remember { FullArt() }
    val measurer = rememberTextMeasurer()
    val family = remember { FontFamily(Font(R.font.eb_garamond_kolib)) }
    Canvas(modifier.fillMaxSize()) {
        val t = time()
        val alpha = alpha()
        val minSide = min(size.width, size.height)
        val markW = (minSide * 0.36f).coerceAtMost(250.dp.toPx())
        val unit = markW / 52f
        val nameSize = markW * 0.40f
        val style = TextStyle(fontFamily = family, fontSize = nameSize.toSp(), letterSpacing = 0.02.em)
        val name = art.name(measurer, style)
        // The mark and the name under it, centred together.
        val markH = 43.3f * unit
        val gap = markW * 0.16f
        val groupH = markH + gap + name.whole.size.height
        val markCenter = Offset(size.width / 2f, (size.height - groupH) / 2f + markH / 2f)
        drawOpening(t, alpha, art, markCenter, unit, name, gap + markH / 2f, nameSize)
    }
}


/** Everything worked out once: the mark's lines, and where each thread and speck of light goes. */
private class FullArt {
    /** The book as one line all the way round, so it meets itself cleanly at the spine. */
    val book = path("M32,54.5 C25,49 15,48 6,50 V12.5 C15,10.5 25,11.5 32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5 Z")
    /** Each half of it from the top of the spine, for drawing it on. */
    val leftHalf = path("M32,17 C25,11.5 15,10.5 6,12.5 V50 C15,48 25,49 32,54.5")
    val rightHalf = path("M32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5")
    val ribbon = path("M44,12 V27 L47.5,24 L51,27 V11.6")
    /** The book's outline on screen, as it flies at the camera: the app shows through it. */
    private val holeOutline = android.graphics.Path()
    private val holeMatrix = android.graphics.Matrix()
    val hole: Path = holeOutline.asComposePath()

    /** Puts [hole] where the book is: its middle at [center], [scale] pixels to a unit of its 64 x 64 space. */
    fun placeHole(center: Offset, scale: Float): Path {
        holeMatrix.setTranslate(-32f, -32.85f)
        holeMatrix.postScale(scale, scale)
        holeMatrix.postTranslate(center.x, center.y)
        book.asAndroidPath().transform(holeMatrix, holeOutline)
        return hole
    }
    /** One gold for the whole mark, light at the top left to deep at the foot. */
    val gold = Brush.linearGradient(
        0f to Color(0xFFFBE6BF), 0.5f to Color(0xFFEBC07E), 1f to GoldDeep,
        start = Offset(14f, 8f), end = Offset(48f, 60f),
    )
    /** For the mark's glow: its line, blurred, added onto what's under it. */
    val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        blendMode = android.graphics.BlendMode.PLUS
    }
    /** Blurs measured in the mark's own 64 x 64 space, so they grow with it. */
    val wideBlur = android.graphics.BlurMaskFilter(6.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    val closeBlur = android.graphics.BlurMaskFilter(2.2f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    private val measure = PathMeasure()
    private val bookPart = Path()
    private val ribbonPart = Path()
    /** The book and ribbon as far as drawn, together, for the light laid over them: one line, so nothing doubles up where they cross. */
    val together = Path()
    val pens = ArrayList<Offset>(3)
    val comet = Path()
    private val left = ArrayList<Offset>(48)
    private val right = ArrayList<Offset>(48)
    val points = ArrayList<Offset>(48)

    /** The mark as far as [pages] and [ribbon] say (0 to 1): the book, the ribbon, and where the drawing has got to. */
    fun drawnSoFar(pages: Float, ribbon: Float): Pair<Path?, Path?> {
        pens.clear()
        val bookShape: Path? = when {
            pages <= 0f -> null
            pages >= 1f -> book
            else -> {
                bookPart.reset()
                part(leftHalf, pages, bookPart)
                part(rightHalf, pages, bookPart)
                bookPart
            }
        }
        val ribbonShape: Path? = when {
            ribbon <= 0f -> null
            ribbon >= 1f -> this.ribbon
            else -> {
                ribbonPart.reset()
                part(this.ribbon, ribbon, ribbonPart)
                ribbonPart
            }
        }
        together.reset()
        bookShape?.let { together.addPath(it) }
        ribbonShape?.let { together.addPath(it) }
        return bookShape to ribbonShape
    }

    private fun part(whole: Path, f: Float, into: Path) {
        measure.setPath(whole, false)
        val length = measure.length
        measure.getSegment(0f, length * f, into, true)
        pens.add(measure.getPosition(length * f))
    }

    /** A comet's tail through [points] (head first), [width] across at the head and tapering to nothing. */
    fun cometThrough(width: Float): Path {
        comet.reset()
        val n = points.size
        if (n < 2) return comet
        left.clear()
        right.clear()
        for (i in 0 until n) {
            val a = points[max(0, i - 1)]
            val b = points[min(n - 1, i + 1)]
            var dx = b.x - a.x
            var dy = b.y - a.y
            val length = hypot(dx, dy).coerceAtLeast(0.001f)
            dx /= length
            dy /= length
            val half = width / 2f * (1f - i / (n - 1f)).pow(0.9f)
            val p = points[i]
            left.add(Offset(p.x - dy * half, p.y + dx * half))
            right.add(Offset(p.x + dy * half, p.y - dx * half))
        }
        comet.moveTo(left[0].x, left[0].y)
        for (i in 1 until n) comet.lineTo(left[i].x, left[i].y)
        for (i in n - 1 downTo 0) comet.lineTo(right[i].x, right[i].y)
        comet.close()
        return comet
    }

    class Speck(val angle: Float, val radius: Float, val size: Float, val phase: Float, val twinkle: Float, val appear: Float, val spin: Float, val depth: Float)
    class Spark(val angle: Float, val speed: Float, val size: Float, val life: Float)

    val threads: List<OpeningThread>
    val specks: List<Speck>
    val sparks: List<Spark>
    /** The pages' lines measured, for where the threads go over into them. */
    val leftTrack = MarkTrack(leftHalf)
    val rightTrack = MarkTrack(rightHalf)
    /** How far the right-hand page's line is drawn when it passes where the ribbon hangs: it leaves a spark there. */
    val ribbonSeed = OpeningThreads.nearestAlong(rightHalf, MarkPoints.RIBBON_TOP)

    private val tailPart = Path()
    /** Where the tail [tail] gave last starts. */
    var tailStart = Offset.Zero
        private set

    /** The last [back] of [half], drawn to [f] of the way along: the bright tail behind its pen. */
    fun tail(half: Path, f: Float, back: Float): Path {
        tailPart.reset()
        measure.setPath(half, false)
        val length = measure.length
        val from = length * max(0f, f - back)
        measure.getSegment(from, length * f, tailPart, true)
        tailStart = measure.getPosition(from)
        return tailPart
    }

    init {
        val r = Random(7)
        threads = OpeningThreads.make(r, leftTrack, rightTrack)
        specks = List(80) {
            Speck(
                angle = r.nextFloat() * 2 * PI.toFloat(),
                radius = 0.1f + r.nextFloat() * 0.65f,
                size = 0.7f + r.nextFloat() * 1.6f,
                phase = r.nextFloat() * 6.28f,
                twinkle = 2.5f + r.nextFloat() * 6f,
                appear = 0.25f + r.nextFloat() * 1.4f,
                spin = 0.1f + r.nextFloat() * 0.3f,
                depth = 0.3f + r.nextFloat() * 0.7f,
            )
        }
        sparks = List(54) {
            Spark(
                angle = r.nextFloat() * 2 * PI.toFloat(),
                speed = 0.3f + r.nextFloat() * 0.65f,
                size = 0.8f + r.nextFloat() * 1.4f,
                life = 0.8f + r.nextFloat() * 0.7f,
            )
        }
    }

    private var nameStyle: TextStyle? = null
    private var nameLayout: NameLayout? = null

    fun name(measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle): NameLayout {
        nameLayout?.let { if (style == nameStyle) return it }
        val text = tr("KoLib")
        val whole = measurer.measure(text, style)
        val letters = text.indices.map { i -> measurer.measure(text[i].toString(), style) to whole.getBoundingBox(i).left }
        return NameLayout(whole, letters).also {
            nameStyle = style
            nameLayout = it
        }
    }

    private fun path(d: String): Path = PathParser().parsePathString(d).toPath()
}

private fun DrawScope.drawOpening(t: Float, alpha: Float, art: FullArt, center: Offset, unit: Float, name: NameLayout, nameDrop: Float, nameSize: Float) {
    val minSide = min(size.width, size.height)
    val halfDiag = hypot(size.width, size.height) / 2f
    val zoomT = clamp01((t - Opening.ZOOM_START) / (Opening.LENGTH - Opening.ZOOM_START))
    val zoom = 1f + 17f * zoomT.pow(3)
    // Easing back from the camera as the book forms, slowing to a stop just before it comes at it.
    val recede = 1.08f - 0.18f * easeInOutSine(clamp01((t - Opening.RECEDE_START) / (Opening.ZOOM_START - Opening.RECEDE_START)))
    // The dark, warming from the middle as the light gathers.
    fun backdrop(dark: Float) {
        if (dark <= 0f) return
        drawRect(OpeningNight, alpha = dark)
        drawRect(
            Brush.radialGradient(listOf(Glow, Glow.copy(alpha = 0f)), center, halfDiag * 0.9f),
            alpha = dark * smooth(0.05f, 0.7f, t),
        )
        val ember = bump(t, 1.2f, Opening.FLASH, Opening.FLASH + 1.1f)
        if (ember > 0f) {
            drawRect(Brush.radialGradient(listOf(Ember, Ember.copy(alpha = 0f)), center, minSide * 0.6f), alpha = dark * ember * 0.5f)
        }
        drawRect(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, halfDiag * 1.05f),
            alpha = dark,
        )
    }
    // As the book comes at the camera the app shows through it, and only through it: the dark stays all
    // round it until the book has grown past the screen's edges, as if flown through.
    val through = smooth(Opening.REVEAL, Opening.REVEAL + 0.15f, t)
    if (through <= 0f) {
        backdrop(alpha)
    } else {
        val hole = art.placeHole(center, recede * unit * zoom)
        clipPath(hole, ClipOp.Difference) { backdrop(alpha * (1f - smooth(Opening.LENGTH - 0.08f, Opening.LENGTH, t))) }
        if (through < 1f) clipPath(hole) { backdrop(alpha * (1f - through)) }
    }

    // Specks nearer the camera move more as it moves.
    drawSpecks(t, alpha, art, center, halfDiag, zoomT, recede)
    withTransform({ scale(recede, recede, center) }) {
        drawThreads(t, alpha, art, center, halfDiag, unit)
        drawFlash(t, alpha, center, minSide, unit)
        drawSparks(t, alpha, art, center, minSide, unit)

        // The mark, flying at the camera at the end: whole until it's past the screen's edges, its line
        // round the edge of what shows through it.
        val markAlpha = alpha * (1f - smooth(Opening.LENGTH - 0.08f, Opening.LENGTH, t))
        if (markAlpha > 0f) {
            val pages = OpeningThreads.pages(t)
            val ribbon = easeOut(clamp01((t - Opening.RIBBON_START) / (Opening.RIBBON_END - Opening.RIBBON_START)))
            withTransform({
                translate(center.x, center.y)
                scale(unit * zoom, unit * zoom, Offset.Zero)
                translate(-32f, -32.85f)
            }) {
                drawMark(t, markAlpha, art, pages, ribbon, 1f - smooth(0f, 0.45f, zoomT))
            }
        }
        drawName(t, alpha, name, center, nameDrop, nameSize, zoom)
    }
}

/** The name, its letters rising in and drawing together, with a soft glow; going with the mark when it flies. */
private fun DrawScope.drawName(t: Float, alpha: Float, name: NameLayout, center: Offset, nameDrop: Float, nameSize: Float, zoom: Float) {
    val nameAlpha = alpha * (1f - smooth(Opening.ZOOM_START, Opening.ZOOM_START + 0.3f, t))
    if (nameAlpha <= 0f || t <= Opening.NAME_START) return
    val w = name.whole.size.width.toFloat()
    val left = center.x - w / 2f
    val top = center.y + nameDrop
    val brush = Brush.verticalGradient(listOf(Cream, GoldLight), startY = 0f, endY = name.whole.size.height.toFloat())
    // The letters start a little apart and settle together.
    val settle = easeOut(clamp01((t - Opening.NAME_START) / 0.9f))
    val spread = nameSize * 0.12f * (1f - settle)
    val middle = (name.letters.size - 1) / 2f
    withTransform({ scale(zoom, zoom, center) }) {
        name.letters.forEachIndexed { i, (letter, x) ->
            val a = easeOut(clamp01((t - Opening.NAME_START - i * 0.08f) / 0.5f))
            if (a <= 0f) return@forEachIndexed
            val rise = (1f - a) * 10.dp.toPx()
            withTransform({ translate(left + x + (i - middle) * spread, top + rise) }) {
                drawText(
                    letter, brush = brush, alpha = a * nameAlpha,
                    shadow = Shadow(Gold.copy(alpha = 0.45f * a * nameAlpha), Offset.Zero, blurRadius = nameSize * 0.3f),
                )
            }
        }
    }
}

/** Specks of light: drawn round in the swirl, gathering in, then drifting out, and streaking past at the end. */
private fun DrawScope.drawSpecks(t: Float, alpha: Float, art: FullArt, center: Offset, halfDiag: Float, zoomT: Float, recede: Float) {
    val swirl = easeInOut(clamp01((t - Opening.SWIRL_START) / (Opening.SWIRL_END - Opening.SWIRL_START + 0.2f)))
    val gather = 0.3f * easeInOut(clamp01((t - 0.8f) / 1.8f)) * (1f - smooth(Opening.FLASH, Opening.FLASH + 0.8f, t))
    val dp = 1.dp.toPx()
    val fade = alpha * (1f - smooth(0.65f, 1f, zoomT))
    if (fade <= 0f) return
    fun at(s: FullArt.Speck, time: Float, zt: Float): Offset {
        val drift = 0.12f * halfDiag * (time - Opening.FLASH).coerceAtLeast(0f) + halfDiag * 2.4f * zt * zt
        val angle = s.angle + s.spin * time + 2.2f * swirl * s.depth
        val r = (s.radius * halfDiag * (1f - gather) + drift * s.depth) * recede.pow(0.5f + s.depth)
        return Offset(center.x + cos(angle) * r, center.y + sin(angle) * r * 0.92f)
    }
    art.specks.forEach { s ->
        if (t < s.appear) return@forEach
        val life = smooth(s.appear, s.appear + 0.5f, t)
        val twinkle = 0.55f + 0.45f * sin(s.phase + t * s.twinkle)
        val a = life * twinkle * fade
        if (a <= 0.01f) return@forEach
        val p = at(s, t, zoomT)
        val core = s.size * dp * (0.7f + 0.6f * s.depth)
        if (zoomT > 0.02f) {
            // Rushing past: a streak from where it was a moment ago.
            val before = at(s, t - 0.035f, clamp01(zoomT - 0.035f / (Opening.LENGTH - Opening.ZOOM_START)))
            drawLine(
                Brush.linearGradient(listOf(Spark.copy(alpha = 0f), Spark.copy(alpha = a)), before, p),
                before, p, strokeWidth = core * 1.4f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
        }
        val r = core * 4.5f
        drawCircle(
            Brush.radialGradient(
                0f to Spark.copy(alpha = 0.95f * a), 0.16f to Spark.copy(alpha = 0.7f * a), 0.3f to GoldLight.copy(alpha = 0.2f * a),
                0.6f to Gold.copy(alpha = 0.05f * a), 1f to Gold.copy(alpha = 0f), center = p, radius = r,
            ),
            radius = r, center = p, blendMode = BlendMode.Plus,
        )
    }
}

/**
 * The threads of light swirling in to circle the book: comets with bright heads and tapering tails. One after
 * another they go over into the two lines drawing the book's pages, and run into them.
 */
private fun DrawScope.drawThreads(t: Float, alpha: Float, art: FullArt, center: Offset, halfDiag: Float, unit: Float) {
    if (t < Opening.SWIRL_START) return
    val dp = 1.dp.toPx()
    val from = halfDiag * 0.85f
    val ring = OpeningThreads.RING * unit
    val pen = { side: Int, time: Float -> MarkPoints.onScreen((if (side == 0) art.leftTrack else art.rightTrack).at(OpeningThreads.pages(time)), center, unit) }
    val steps = 40
    art.threads.forEach { th ->
        val u = th.along(t)
        if (u <= 0f || t >= th.joined + OpeningThreads.AFTER_JOIN) return@forEach
        // In as it sets off; once it's run into its line, its tail fading along the line.
        val a = smooth(0f, 0.12f, u) * (1f - smooth(th.joined, th.joined + OpeningThreads.AFTER_JOIN, t)) * th.bright * alpha
        if (a <= 0.01f) return@forEach
        art.points.clear()
        for (k in 0..steps) {
            val back = t - th.tailTime * k / steps
            if (th.along(back) < 0f) break
            art.points.add(th.at(back, center, from, ring, pen))
        }
        if (art.points.size < 3) return@forEach
        val head = art.points.first()
        val tail = art.points.last()
        val w = th.width * dp
        fun fadeAlong(color: Color, strength: Float) =
            Brush.linearGradient(0f to color.copy(alpha = strength), 1f to color.copy(alpha = 0f), start = head, end = tail)
        drawPath(art.cometThrough(w * 6f), fadeAlong(Gold, 0.08f * a), blendMode = BlendMode.Plus)
        drawPath(art.cometThrough(w * 2.6f), fadeAlong(GoldLight, 0.26f * a), blendMode = BlendMode.Plus)
        drawPath(art.cometThrough(w), Brush.linearGradient(0f to Spark.copy(alpha = a), 0.5f to GoldLight.copy(alpha = 0.6f * a), 1f to GoldLight.copy(alpha = 0f), start = head, end = tail), blendMode = BlendMode.Plus)
        drawCircle(
            Brush.radialGradient(0f to Spark.copy(alpha = a), 0.25f to GoldLight.copy(alpha = 0.5f * a), 1f to Gold.copy(alpha = 0f), center = head, radius = w * 5f),
            radius = w * 5f, center = head, blendMode = BlendMode.Plus,
        )
    }
}

/** The flash as the book forms: a burst of light, a streak across, and a ring going out. */
private fun DrawScope.drawFlash(t: Float, alpha: Float, center: Offset, minSide: Float, unit: Float) {
    val ft = t - Opening.FLASH
    if (ft < -0.04f || ft > 0.8f) return
    val a = alpha * if (ft < 0.06f) clamp01((ft + 0.04f) / 0.1f) else (1f - (ft - 0.06f) / 0.56f).coerceAtLeast(0f).pow(2)
    if (a > 0f) {
        val r = minSide * (0.18f + 0.75f * easeOut(clamp01(ft / 0.5f)))
        drawCircle(
            Brush.radialGradient(0f to Spark.copy(alpha = 0.85f), 0.25f to Gold.copy(alpha = 0.45f), 1f to Gold.copy(alpha = 0f), center = center, radius = r),
            radius = r, center = center, alpha = a, blendMode = BlendMode.Plus,
        )
        // A soft streak of light across, like a lens's.
        val length = size.width * (0.35f + 1.2f * easeOut(clamp01((ft + 0.04f) / 0.3f)))
        listOf(0.012f to 1f, 0.045f to 0.35f).forEach { (thin, k) ->
            withTransform({ scale(1f, thin, center) }) {
                drawCircle(
                    Brush.radialGradient(0f to Spark, 0.3f to Gold.copy(alpha = 0.55f), 1f to Gold.copy(alpha = 0f), center = center, radius = length / 2f),
                    radius = length / 2f, center = center, alpha = a * k, blendMode = BlendMode.Plus,
                )
            }
        }
    }
    // A ring of light going out.
    val rt = ft / 0.75f
    if (rt in 0f..1f) {
        val radius = 30f * unit + (minSide * 0.85f - 30f * unit) * easeOut(rt)
        val fade = (1f - rt).pow(2) * alpha
        val width = (5f - 4f * rt).dp.toPx()
        drawCircle(Gold, radius = radius, center = center, alpha = fade * 0.16f, style = Stroke(width * 4f), blendMode = BlendMode.Plus)
        drawCircle(GoldLight, radius = radius, center = center, alpha = fade * 0.5f, style = Stroke(width), blendMode = BlendMode.Plus)
    }
}

/** Sparks thrown out by the flash, slowing as they go. */
private fun DrawScope.drawSparks(t: Float, alpha: Float, art: FullArt, center: Offset, minSide: Float, unit: Float) {
    val dt = t - Opening.FLASH
    if (dt <= 0f) return
    val dp = 1.dp.toPx()
    art.sparks.forEach { s ->
        if (dt > s.life) return@forEach
        val v0 = s.speed * minSide
        val dist = v0 * (1f - exp(-3.2f * dt)) / 3.2f
        val dir = Offset(cos(s.angle), sin(s.angle))
        val p = center + dir * (24f * unit + dist)
        val tail = dir * min(v0 * exp(-3.2f * dt) * 0.04f, 22.dp.toPx())
        val a = (1f - dt / s.life).pow(1.4f) * smooth(0f, 0.06f, dt) * alpha
        if (a <= 0.01f) return@forEach
        drawLine(
            Brush.linearGradient(listOf(Spark.copy(alpha = 0f), Spark.copy(alpha = a)), p - tail, p),
            p - tail, p, strokeWidth = s.size * dp, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
        )
        drawCircle(Gold, radius = s.size * dp * 3f, center = p, alpha = a * 0.12f, blendMode = BlendMode.Plus)
    }
}

/**
 * The book mark, in its own 64 x 64 space, as far drawn as [pages] and [ribbon] say (0 to 1), with a
 * glow of [glow]. The light over it is laid on the book and ribbon as one line, so nothing doubles up.
 */
private fun DrawScope.drawMark(t: Float, alpha: Float, art: FullArt, pages: Float, ribbon: Float, glow: Float) {
    val (book, rib) = art.drawnSoFar(pages, ribbon)
    if (book == null && rib == null) return
    val together = art.together
    val flare = exp(-((t - Opening.FLASH - 0.04f) / 0.13f).pow(2))
    val breathe = 1f + 0.15f * sin((t - Opening.FLASH) * 2.4f) * smooth(Opening.FLASH + 0.4f, Opening.FLASH + 1f, t)
    fun line(width: Float) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // The glow: a soft blur of the line, wide and faint, then close and brighter.
    if (glow > 0f) {
        val path = together.asAndroidPath()
        drawIntoCanvas { c ->
            art.glowPaint.apply {
                strokeWidth = 3.6f
                maskFilter = art.wideBlur
                color = Gold.copy(alpha = (0.42f * alpha * glow * breathe).coerceAtMost(1f)).toArgb()
            }
            c.nativeCanvas.drawPath(path, art.glowPaint)
            art.glowPaint.apply {
                strokeWidth = 2.8f
                maskFilter = art.closeBlur
                color = GoldLight.copy(alpha = (0.5f * alpha * glow * breathe).coerceAtMost(1f)).toArgb()
            }
            c.nativeCanvas.drawPath(path, art.glowPaint)
        }
    }
    // The gold line itself, all one colour across book and ribbon; faded as one where it crosses itself.
    val layered = alpha < 0.999f
    if (layered) drawContext.canvas.saveLayer(Rect(-20f, -20f, 84f, 84f), Paint().apply { this.alpha = alpha })
    book?.let { drawPath(it, art.gold, style = line(2.6f)) }
    rib?.let { drawPath(it, art.gold, style = line(2.2f)) }
    if (layered) drawContext.canvas.restore()

    if (flare > 0.01f) drawPath(together, Spark, alpha = alpha * flare * 0.85f, style = line(2.6f), blendMode = BlendMode.Plus)
    val shimmer = clamp01((t - Opening.SHIMMER_START) / (Opening.SHIMMER_END - Opening.SHIMMER_START))
    if (shimmer > 0f && shimmer < 1f) {
        val x = -10f + 84f * easeInOut(shimmer)
        val band = Brush.linearGradient(
            0f to Color.Transparent, 0.5f to Spark.copy(alpha = 0.9f), 1f to Color.Transparent,
            start = Offset(x - 9f, 6f), end = Offset(x + 9f, 60f),
        )
        drawPath(together, band, alpha = alpha, style = line(2.6f), blendMode = BlendMode.Plus)
    }
    fun spot(at: Offset, radius: Float, a: Float) {
        if (a <= 0.004f) return
        drawCircle(
            Brush.radialGradient(0f to Spark.copy(alpha = a), 0.3f to GoldLight.copy(alpha = 0.5f * a), 1f to Gold.copy(alpha = 0f), center = at, radius = radius),
            radius = radius, center = at, blendMode = BlendMode.Plus,
        )
    }
    // The bright points drawing the lines. The pages' two are made of the threads, growing brighter as each
    // runs into them, each with a tail along the line just drawn; they meet at the foot of the spine.
    val drawingPages = book != null && pages < 1f
    art.pens.forEachIndexed { i, pen ->
        if (i < 2 && drawingPages) {
            if (t >= OpeningThreads.BEAD_START) return@forEachIndexed
            val fed = OpeningThreads.joinedInto(art.threads, i, t)
            val a = alpha * smooth(0f, 0.03f, pages) * (0.55f + 0.45f * fed)
            val tail = art.tail(if (i == 0) art.leftHalf else art.rightHalf, pages, 0.16f)
            drawPath(
                tail, Brush.linearGradient(0f to Spark.copy(alpha = 0.9f * a), 1f to GoldLight.copy(alpha = 0f), start = pen, end = art.tailStart),
                style = line(3f), blendMode = BlendMode.Plus,
            )
            spot(pen, 7f + 2.5f * fed, a)
        } else {
            // The ribbon's, from the spark left for it.
            spot(pen, 7f, alpha * (1f - smooth(0.9f, 1f, ribbon)))
        }
    }
    // Where the ribbon hangs, a spark left by the right-hand page's line as it passes, until the ribbon's drawn from it.
    if (ribbon <= 0f && pages >= art.ribbonSeed) {
        spot(MarkPoints.RIBBON_TOP, 5f, alpha * smooth(art.ribbonSeed, art.ribbonSeed + 0.03f, pages) * (0.8f + 0.2f * sin(t * 23f)))
    }
    // Met at the foot of the spine, the two go on as one, up the middle of the book, and set off the flash.
    OpeningThreads.rising(t)?.let { (p, s) ->
        val a = alpha * (1f - smooth(Opening.FLASH - 0.01f, Opening.FLASH + 0.05f, t))
        val back = Offset(p.x, min(MarkPoints.SPINE_FOOT.y, p.y + 10f))
        if (back.y - p.y > 0.5f) {
            drawLine(
                Brush.linearGradient(0f to Spark.copy(alpha = a), 1f to GoldLight.copy(alpha = 0f), start = p, end = back),
                p, back, strokeWidth = 3f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
        }
        spot(p, 9f + 3f * s, a)
    }
}

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val v = clamp01((x - a) / (b - a))
    return v * v * (3f - 2f * v)
}

/** 0 before [a], up to 1 at [peak], back to 0 at [b]. */
private fun bump(x: Float, a: Float, peak: Float, b: Float): Float = when {
    x <= a || x >= b -> 0f
    x < peak -> smooth(a, peak, x)
    else -> 1f - smooth(peak, b, x)
}

private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOutSine(x: Float) = (1f - cos(PI.toFloat() * x)) / 2f
