package dev.kolib.app.ui.opening

import dev.kolib.app.tr
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
import dev.kolib.app.R
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

/**
 * The lighter opening, for phones and slower devices: the same, drawn far more cheaply. Its soft light
 * comes from small pictures drawn once, the book's glow is blurred once away from the screen's thread,
 * and the dark behind is drawn small once and stretched.
 */
@Composable
internal fun LightOpening(time: () -> Float, alpha: () -> Float, modifier: Modifier) {
    val art = remember { LightArt() }
    val measurer = rememberTextMeasurer()
    val font = remember { nameFont(OpeningColours.look.lettering) }
    // The book's glow, blurred once, away from the screen's thread: it's needed from about two seconds in.
    var glows by remember { mutableStateOf<MarkGlow?>(null) }
    LaunchedEffect(Unit) { glows = withContext(Dispatchers.Default) { MarkGlow.make() } }
    Canvas(modifier.fillMaxSize()) {
        val t = time()
        val a = alpha()
        val minSide = min(size.width, size.height)
        val markW = (minSide * 0.36f).coerceAtMost(250.dp.toPx())
        val unit = markW / 52f
        val nameSize = markW * 0.40f
        val name = art.name(measurer, font, nameSize.toSp())
        // The mark and the name under it, centred together.
        val markH = 43.3f * unit
        val gap = markW * 0.16f
        val groupH = markH + gap + name.whole.size.height
        val markCenter = Offset(size.width / 2f, (size.height - groupH) / 2f + markH / 2f)
        drawOpening(t, a, art, glows, markCenter, unit, name, gap + markH / 2f, nameSize)
    }
}

/** The mark's lines, in its own 64 x 64 space: the book all the way round, each half from the top of the spine, and the ribbon. */
private const val BOOK = "M32,54.5 C25,49 15,48 6,50 V12.5 C15,10.5 25,11.5 32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5 Z"
private const val LEFT_HALF = "M32,17 C25,11.5 15,10.5 6,12.5 V50 C15,48 25,49 32,54.5"
private const val RIGHT_HALF = "M32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5"
private const val RIBBON = "M44,12 V27 L47.5,24 L51,27 V11.6"

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()


/** The book's glow, and the ribbon's, blurred once into pictures, in the mark's own 64 x 64 space plus a margin. */
private class MarkGlow(val book: Bitmap, val ribbon: Bitmap) {
    companion object {
        /** Pixels to a unit of the mark, and the margin round it for the blur, in units. */
        const val SCALE = 6f
        const val PAD = 12f

        /** Made from paths of its own, so it can be made away from the screen's thread while the screen draws. */
        fun make(): MarkGlow = MarkGlow(blur(path(BOOK).asAndroidPath()), blur(path(RIBBON).asAndroidPath()))

        private fun blur(path: android.graphics.Path): Bitmap {
            val side = ((64f + 2 * PAD) * SCALE).toInt()
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(SCALE, SCALE)
            canvas.translate(PAD, PAD)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                blendMode = android.graphics.BlendMode.PLUS
            }
            // Wide and faint, then close and brighter.
            paint.strokeWidth = 3.6f
            paint.maskFilter = BlurMaskFilter(6.5f, BlurMaskFilter.Blur.NORMAL)
            paint.color = Gold.copy(alpha = 0.42f).toArgb()
            canvas.drawPath(path, paint)
            paint.strokeWidth = 2.8f
            paint.maskFilter = BlurMaskFilter(2.2f, BlurMaskFilter.Blur.NORMAL)
            paint.color = GoldLight.copy(alpha = 0.5f).toArgb()
            canvas.drawPath(path, paint)
            return bitmap
        }
    }
}

/** Everything worked out once: the mark's lines, the soft light, and where each thread and speck goes. */
private class LightArt {
    /** The book as one line all the way round, so it meets itself cleanly at the spine. */
    val book = path(BOOK)
    /** Each half of it from the top of the spine, for drawing it on. */
    val leftHalf = path(LEFT_HALF)
    val rightHalf = path(RIGHT_HALF)
    val ribbon = path(RIBBON)
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
        0f to MarkLight, 0.5f to MarkMid, 1f to GoldDeep,
        start = Offset(14f, 8f), end = Offset(48f, 60f),
    )
    private val measure = PathMeasure()
    private val bookPart = Path()
    private val ribbonPart = Path()
    /** The book and ribbon as far as drawn, together, for the light laid over them: one line, so nothing doubles up. */
    val together = Path()
    val pens = ArrayList<Offset>(3)
    val trail = Path()

    /** A soft round spot of light, drawn once and reused for every speck, spark and glow point. */
    val dot: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
        val c = android.graphics.Canvas(bitmap)
        val r = 32f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            r, r, r,
            intArrayOf(Spark.toArgb(), Spark.copy(alpha = 0.75f).toArgb(), GoldLight.copy(alpha = 0.22f).toArgb(), Gold.copy(alpha = 0.05f).toArgb(), Gold.copy(alpha = 0f).toArgb()),
            floatArrayOf(0f, 0.16f, 0.3f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(r, r, r, paint)
    }
    /** A soft haze with no bright middle, for the light behind the name. */
    val haze: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
        val c = android.graphics.Canvas(bitmap)
        val r = 32f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            r, r, r,
            intArrayOf(Gold.copy(alpha = 0.5f).toArgb(), Gold.copy(alpha = 0.32f).toArgb(), Gold.copy(alpha = 0.1f).toArgb(), Gold.copy(alpha = 0f).toArgb()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(r, r, r, paint)
    }
    /** For drawing light: added onto what's under it, smoothly scaled. */
    val light = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        blendMode = android.graphics.BlendMode.PLUS
    }
    /** For thin lines of light: sparks' tails, specks streaking past. */
    val streak = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = android.graphics.BlendMode.PLUS
        strokeCap = android.graphics.Paint.Cap.ROUND
        color = Spark.toArgb()
    }
    val spot = RectF()
    /** For the dark behind it all: smoothly scaled, laid over what's under it. */
    val backdropPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    /** The dark behind it all, drawn small once for the screen's size and stretched: it's all soft. */
    private var backdrop: Bitmap? = null
    private var backdropFor = 0L

    fun backdrop(width: Float, height: Float, center: Offset): Bitmap {
        val key = (width.toLong() shl 32) or height.toLong()
        backdrop?.let { if (key == backdropFor) return it }
        val w = (width / 4f).toInt().coerceAtLeast(8)
        val h = (height / 4f).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bitmap)
        c.drawColor(OpeningNight.toArgb())
        val cx = center.x / 4f
        val cy = center.y / 4f
        val half = hypot(w.toFloat(), h.toFloat()) / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(cx, cy, half * 0.9f, Glow.toArgb(), Glow.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = RadialGradient(cx, cy, half * 1.05f, Color.Transparent.toArgb(), Color.Black.copy(alpha = 0.5f).toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        backdrop = bitmap
        backdropFor = key
        return bitmap
    }

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

    private var nameSize = 0f
    private var nameLayout: NameLayout? = null

    fun name(measurer: TextMeasurer, font: NameFont, size: androidx.compose.ui.unit.TextUnit): NameLayout {
        nameLayout?.let { if (size.value == nameSize) return it }
        val style = TextStyle(fontFamily = font.family, fontWeight = font.weight, fontSize = size * font.scale, letterSpacing = font.spacing.em)
        val text = tr("KoLib")
        val whole = measurer.measure(text, style)
        val letters = text.indices.map { i -> measurer.measure(text[i].toString(), style) to whole.getBoundingBox(i).left }
        return NameLayout(whole, letters).also {
            nameSize = size.value
            nameLayout = it
        }
    }

}

/** A spot of light, [radius] across from its middle at [x], [y], at [alpha]. */
private fun android.graphics.Canvas.light(art: LightArt, x: Float, y: Float, radius: Float, alpha: Float) {
    if (alpha <= 0.004f) return
    art.spot.set(x - radius, y - radius, x + radius, y + radius)
    art.light.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
    drawBitmap(art.dot, null, art.spot, art.light)
}

private fun DrawScope.drawOpening(
    t: Float, alpha: Float, art: LightArt, glows: MarkGlow?, center: Offset, unit: Float, name: NameLayout, nameDrop: Float, nameSize: Float,
) {
    val minSide = min(size.width, size.height)
    val halfDiag = hypot(size.width, size.height) / 2f
    val zoomT = clamp01((t - Opening.ZOOM_START) / (Opening.LENGTH - Opening.ZOOM_START))
    val zoom = 1f + 17f * zoomT.pow(3)
    // Easing back from the camera as the book forms, slowing to a stop just before it comes at it.
    val recede = 1.08f - 0.18f * easeInOutSine(clamp01((t - Opening.RECEDE_START) / (Opening.ZOOM_START - Opening.RECEDE_START)))
    // The dark, warming from the middle as the light gathers.
    fun backdrop(dark: Float) {
        if (dark <= 0f) return
        val backdrop = art.backdrop(size.width, size.height, center)
        drawIntoCanvas { c ->
            val glowIn = smooth(0.05f, 0.7f, t)
            if (glowIn < 1f) drawRect(OpeningNight, alpha = dark)
            art.spot.set(0f, 0f, size.width, size.height)
            val paint = art.backdropPaint
            paint.alpha = (dark * (if (glowIn < 1f) glowIn else 1f) * 255f).toInt().coerceIn(0, 255)
            c.nativeCanvas.drawBitmap(backdrop, null, art.spot, paint)
        }
        val ember = bump(t, 1.2f, Opening.FLASH, Opening.FLASH + 1.1f)
        if (ember > 0f) {
            val r = minSide * 0.6f
            drawCircle(Brush.radialGradient(listOf(Ember, Ember.copy(alpha = 0f)), center, r), radius = r, center = center, alpha = dark * ember * 0.5f)
        }
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

    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        // Specks nearer the camera move more as it moves.
        drawSpecks(canvas, t, alpha, art, center, halfDiag, zoomT, recede)
        canvas.save()
        canvas.scale(recede, recede, center.x, center.y)
        drawThreads(canvas, t, alpha, art, center, halfDiag, unit)
        canvas.restore()
    }
    withTransform({ scale(recede, recede, center) }) {
        drawFlash(t, alpha, center, minSide, unit)
        drawIntoCanvas { c -> drawSparks(c.nativeCanvas, t, alpha, art, center, minSide, unit) }

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
                drawMark(t, markAlpha, art, glows, pages, ribbon, 1f - smooth(0f, 0.45f, zoomT))
            }
        }
        drawName(t, alpha, art, name, center, nameDrop, nameSize, zoom)
    }
}

/** The name, its letters rising in and drawing together, with a soft light behind; going with the mark when it flies. */
private fun DrawScope.drawName(t: Float, alpha: Float, art: LightArt, name: NameLayout, center: Offset, nameDrop: Float, nameSize: Float, zoom: Float) {
    val nameAlpha = alpha * (1f - smooth(Opening.ZOOM_START, Opening.ZOOM_START + 0.3f, t))
    if (nameAlpha <= 0f || t <= Opening.NAME_START) return
    val w = name.whole.size.width.toFloat()
    val h = name.whole.size.height.toFloat()
    val left = center.x - w / 2f
    val top = center.y + nameDrop
    val brush = Brush.verticalGradient(listOf(Cream, GoldLight), startY = 0f, endY = h)
    // The letters start a little apart and settle together.
    val settle = easeOut(clamp01((t - Opening.NAME_START) / 0.9f))
    val spread = nameSize * 0.12f * (1f - settle)
    val middle = (name.letters.size - 1) / 2f
    withTransform({ scale(zoom, zoom, center) }) {
        // A soft light behind the word.
        drawIntoCanvas { c ->
            val glow = smooth(Opening.NAME_START, Opening.NAME_START + 0.7f, t) * nameAlpha
            val cx = center.x
            val cy = top + h * 0.55f
            art.spot.set(cx - w * 0.8f, cy - h * 0.8f, cx + w * 0.8f, cy + h * 0.8f)
            art.light.alpha = (glow * 0.45f * 255f).toInt().coerceIn(0, 255)
            c.nativeCanvas.drawBitmap(art.haze, null, art.spot, art.light)
        }
        name.letters.forEachIndexed { i, (letter, x) ->
            val a = easeOut(clamp01((t - Opening.NAME_START - i * 0.08f) / 0.5f))
            if (a <= 0f) return@forEachIndexed
            val rise = (1f - a) * 10.dp.toPx()
            withTransform({ translate(left + x + (i - middle) * spread, top + rise) }) {
                drawText(letter, brush = brush, alpha = a * nameAlpha)
            }
        }
    }
}

/** Specks of light: drawn round in the swirl, gathering in, then drifting out, and streaking past at the end. */
private fun DrawScope.drawSpecks(canvas: android.graphics.Canvas, t: Float, alpha: Float, art: LightArt, center: Offset, halfDiag: Float, zoomT: Float, recede: Float) {
    val swirl = easeInOut(clamp01((t - Opening.SWIRL_START) / (Opening.SWIRL_END - Opening.SWIRL_START + 0.2f)))
    val gather = 0.3f * easeInOut(clamp01((t - 0.8f) / 1.8f)) * (1f - smooth(Opening.FLASH, Opening.FLASH + 0.8f, t))
    val dp = 1.dp.toPx()
    val fade = alpha * (1f - smooth(0.65f, 1f, zoomT))
    if (fade <= 0f) return
    val zoomLength = Opening.LENGTH - Opening.ZOOM_START
    art.specks.forEach { s ->
        if (t < s.appear) return@forEach
        val life = smooth(s.appear, s.appear + 0.5f, t)
        val twinkle = 0.55f + 0.45f * sin(s.phase + t * s.twinkle)
        val a = life * twinkle * fade
        if (a <= 0.01f) return@forEach
        val angle = s.angle + s.spin * t + 2.2f * swirl * s.depth
        val back = recede.pow(0.5f + s.depth)
        fun radius(time: Float, zt: Float) =
            (s.radius * halfDiag * (1f - gather) + (0.12f * halfDiag * (time - Opening.FLASH).coerceAtLeast(0f) + halfDiag * 2.4f * zt * zt) * s.depth) * back
        val r = radius(t, zoomT)
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r * 0.92f
        val core = s.size * dp * (0.7f + 0.6f * s.depth)
        if (zoomT > 0.02f) {
            // Rushing past: a streak from where it was a moment ago.
            val r0 = radius(t - 0.035f, clamp01(zoomT - 0.035f / zoomLength))
            art.streak.strokeWidth = core * 1.3f
            art.streak.alpha = (a * 0.8f * 255f).toInt().coerceIn(0, 255)
            canvas.drawLine(center.x + cos(angle) * r0, center.y + sin(angle) * r0 * 0.92f, x, y, art.streak)
        }
        canvas.light(art, x, y, core * 4.5f, a)
    }
}

/**
 * The threads of light swirling in to circle the book: comets with bright heads and fading tails. One after
 * another they go over into the two lines drawing the book's pages, and run into them.
 */
private fun DrawScope.drawThreads(canvas: android.graphics.Canvas, t: Float, alpha: Float, art: LightArt, center: Offset, halfDiag: Float, unit: Float) {
    if (t < Opening.SWIRL_START) return
    val dp = 1.dp.toPx()
    val from = halfDiag * 0.85f
    val ring = OpeningThreads.RING * unit
    val pen = { side: Int, time: Float -> MarkPoints.onScreen((if (side == 0) art.leftTrack else art.rightTrack).at(OpeningThreads.pages(time)), center, unit) }
    val steps = 28
    art.threads.forEach { th ->
        val u = th.along(t)
        if (u <= 0f || t >= th.joined + OpeningThreads.AFTER_JOIN) return@forEach
        // In as it sets off; once it's run into its line, its tail fading along the line.
        val a = smooth(0f, 0.12f, u) * (1f - smooth(th.joined, th.joined + OpeningThreads.AFTER_JOIN, t)) * th.bright * alpha
        if (a <= 0.01f) return@forEach
        val w = th.width * dp
        // The tail as one line, fading from the head; soft light along it.
        art.trail.reset()
        var headX = 0f
        var headY = 0f
        var tailX = 0f
        var tailY = 0f
        for (k in 0..steps) {
            val back = t - th.tailTime * k / steps
            if (th.along(back) < 0f) break
            val p = th.at(back, center, from, ring, pen)
            if (k == 0) {
                art.trail.moveTo(p.x, p.y)
                headX = p.x
                headY = p.y
            } else {
                art.trail.lineTo(p.x, p.y)
            }
            tailX = p.x
            tailY = p.y
            if (k % 4 == 2) {
                val f = 1f - k.toFloat() / steps
                canvas.light(art, p.x, p.y, w * (2.4f + 2.6f * f), a * 0.4f * f * f)
            }
        }
        drawPath(
            art.trail,
            Brush.linearGradient(0f to Spark.copy(alpha = a), 0.45f to GoldLight.copy(alpha = 0.55f * a), 1f to GoldLight.copy(alpha = 0f), start = Offset(headX, headY), end = Offset(tailX, tailY)),
            style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Plus,
        )
        canvas.light(art, headX, headY, w * 5f, a)
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
        val streak = Brush.radialGradient(0f to Spark, 0.3f to Gold.copy(alpha = 0.55f), 1f to Gold.copy(alpha = 0f), center = center, radius = length / 2f)
        withTransform({ scale(1f, 0.012f, center) }) { drawCircle(streak, radius = length / 2f, center = center, alpha = a, blendMode = BlendMode.Plus) }
        withTransform({ scale(1f, 0.045f, center) }) { drawCircle(streak, radius = length / 2f, center = center, alpha = a * 0.35f, blendMode = BlendMode.Plus) }
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
private fun DrawScope.drawSparks(canvas: android.graphics.Canvas, t: Float, alpha: Float, art: LightArt, center: Offset, minSide: Float, unit: Float) {
    val dt = t - Opening.FLASH
    if (dt <= 0f) return
    val dp = 1.dp.toPx()
    val maxTail = 22.dp.toPx()
    art.sparks.forEach { s ->
        if (dt > s.life) return@forEach
        val a = (1f - dt / s.life).pow(1.4f) * smooth(0f, 0.06f, dt) * alpha
        if (a <= 0.01f) return@forEach
        val v0 = s.speed * minSide
        val dist = 24f * unit + v0 * (1f - exp(-3.2f * dt)) / 3.2f
        val dx = cos(s.angle)
        val dy = sin(s.angle)
        val x = center.x + dx * dist
        val y = center.y + dy * dist
        val tail = min(v0 * exp(-3.2f * dt) * 0.04f, maxTail)
        art.streak.strokeWidth = s.size * dp
        art.streak.alpha = (a * 255f).toInt().coerceIn(0, 255)
        canvas.drawLine(x - dx * tail, y - dy * tail, x, y, art.streak)
        canvas.light(art, x, y, s.size * dp * 4f, a * 0.6f)
    }
}

/**
 * The book mark, in its own 64 x 64 space, as far drawn as [pages] and [ribbon] say (0 to 1), with a
 * glow of [glow]. The light over it is laid on the book and ribbon as one line, so nothing doubles up.
 */
private fun DrawScope.drawMark(t: Float, alpha: Float, art: LightArt, glows: MarkGlow?, pages: Float, ribbon: Float, glow: Float) {
    val (book, rib) = art.drawnSoFar(pages, ribbon)
    if (book == null && rib == null) return
    val together = art.together
    val flare = exp(-((t - Opening.FLASH - 0.04f) / 0.13f).pow(2))
    val breathe = 1f + 0.15f * sin((t - Opening.FLASH) * 2.4f) * smooth(Opening.FLASH + 0.4f, Opening.FLASH + 1f, t)
    fun line(width: Float) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // The glow: the blurred pictures once each part is drawn; while it's being drawn, a faint soft line.
    val bookGlow = smooth(0.8f, 1f, pages)
    val ribbonGlow = smooth(0.8f, 1f, ribbon)
    if (glow > 0f && glows != null && (bookGlow > 0f || ribbonGlow > 0f)) {
        drawIntoCanvas { c ->
            art.spot.set(-MarkGlow.PAD, -MarkGlow.PAD, 64f + MarkGlow.PAD, 64f + MarkGlow.PAD)
            art.light.alpha = (alpha * glow * breathe * bookGlow * 255f).toInt().coerceIn(0, 255)
            if (bookGlow > 0f) c.nativeCanvas.drawBitmap(glows.book, null, art.spot, art.light)
            art.light.alpha = (alpha * glow * breathe * ribbonGlow * 255f).toInt().coerceIn(0, 255)
            if (ribbonGlow > 0f) c.nativeCanvas.drawBitmap(glows.ribbon, null, art.spot, art.light)
        }
    }
    val drawing = 1f - min(bookGlow, if (ribbon > 0f) ribbonGlow else 1f)
    if (drawing > 0f) drawPath(together, Gold, alpha = alpha * 0.12f * drawing, style = line(2.6f * 3f), blendMode = BlendMode.Plus)

    // The gold line itself: book and ribbon, or as one line while it's fading, so it doesn't double up.
    if (alpha < 0.999f) {
        drawPath(together, art.gold, alpha = alpha, style = line(2.5f))
    } else {
        book?.let { drawPath(it, art.gold, style = line(2.6f)) }
        rib?.let { drawPath(it, art.gold, style = line(2.2f)) }
    }

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
            drawIntoCanvas { c -> c.nativeCanvas.light(art, pen.x, pen.y, 8f + 2.5f * fed, a) }
        } else {
            // The ribbon's, from the spark left for it.
            drawIntoCanvas { c -> c.nativeCanvas.light(art, pen.x, pen.y, 8f, alpha * (1f - smooth(0.9f, 1f, ribbon))) }
        }
    }
    // Where the ribbon hangs, a spark left by the right-hand page's line as it passes, until the ribbon's drawn from it.
    if (ribbon <= 0f && pages >= art.ribbonSeed) {
        val p = MarkPoints.RIBBON_TOP
        drawIntoCanvas { c -> c.nativeCanvas.light(art, p.x, p.y, 6f, alpha * smooth(art.ribbonSeed, art.ribbonSeed + 0.03f, pages) * (0.8f + 0.2f * sin(t * 23f))) }
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
        drawIntoCanvas { c -> c.nativeCanvas.light(art, p.x, p.y, 10f + 3f * s, a) }
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
