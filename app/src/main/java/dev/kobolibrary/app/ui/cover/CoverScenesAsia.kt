package dev.kobolibrary.app.ui.cover

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Things drawn in the manner of the ink paintings of China and the prints of Japan: peaks in mist, crooked pines,
 * cranes, a swordsman, a wandering samurai, a gate, a snow-capped mountain, a red seal. The two scenes made of them
 * (for wuxia, and for samurai and old Japan) are here too. All of it is drawn from nothing; none copies a picture.
 */

private fun rgb(hex: Long) = (0xFF000000 or hex).toInt()

private fun alpha(c: Int, a: Int) = ColorUtils.setAlphaComponent(c, a.coerceIn(0, 255))

private fun blend(a: Int, b: Int, t: Float) = ColorUtils.blendARGB(a, b, t.coerceIn(0f, 1f)) or (0xFF shl 24)

/**
 * A tall peak of the kind in ink paintings: steep, ledged, round at the top, strong at its head and thinning into
 * the mist at its foot. Returns the height of its top.
 */
internal fun Painter.inkPeak(cx: Float, base: Float, wide: Float, tall: Float, ink: Int, paper: Int, strength: Int): Float {
    val steps = 7
    val lefts = FloatArray(steps + 1)
    val rights = FloatArray(steps + 1)
    for (k in 0..steps) {
        val t = k / steps.toFloat()
        val body = if (t < 0.72f) 1f - 0.26f * t else 0.813f * sqrt(max(0f, 1f - ((t - 0.72f) / 0.3f) * ((t - 0.72f) / 0.3f)))
        lefts[k] = wide / 2 * body * (0.9f + rnd.nextFloat() * 0.2f)
        rights[k] = wide / 2 * body * (0.9f + rnd.nextFloat() * 0.2f)
    }
    val lean = (rnd.nextFloat() - 0.5f) * wide * 0.2f
    val path = Path()
    path.moveTo(cx - lefts[0] * 1.15f, base)
    for (k in 1..steps) {
        val t = k / steps.toFloat()
        val y0 = base - tall * (t - 0.5f / steps)
        // A ledge, then the face above it.
        path.lineTo(cx + lean * t - lefts[k - 1] * 0.96f, y0)
        path.lineTo(cx + lean * t - lefts[k], base - tall * t)
    }
    path.quadTo(cx + lean, base - tall * 1.05f, cx + lean + rights[steps], base - tall)
    for (k in steps - 1 downTo 0) {
        val t = k / steps.toFloat()
        path.lineTo(cx + lean * t + rights[k + 1] * 0.98f, base - tall * (t + 0.5f / steps))
        path.lineTo(cx + lean * t + rights[k], base - tall * t)
    }
    path.lineTo(cx + rights[0] * 1.15f, base)
    path.close()
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, base - tall, 0f, base, intArrayOf(alpha(ink, strength), alpha(ink, (strength * 0.82f).toInt()), alpha(ink, 0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })
    // The brush's strokes down its faces, and light on one side.
    c.save()
    c.clipPath(path)
    repeat((wide / u * 0.9f).toInt().coerceIn(6, 60)) {
        val sx = cx + (rnd.nextFloat() - 0.5f) * wide
        val sy = base - tall * (0.25f + rnd.nextFloat() * 0.75f)
        val long = tall * (0.1f + rnd.nextFloat() * 0.3f)
        c.drawLine(sx, sy, sx + (rnd.nextFloat() - 0.5f) * wide * 0.06f, sy + long, stroke(alpha(if (rnd.nextInt(3) == 0) paper else ink, strength / 5 + rnd.nextInt(max(1, strength / 6))), u * (0.2f + rnd.nextFloat() * 0.5f)))
    }
    c.drawRect(cx - wide, base - tall * 1.1f, cx + wide, base, Paint().apply {
        shader = LinearGradient(cx - wide / 2, 0f, cx + wide / 2, 0f, intArrayOf(alpha(paper, strength / 5), alpha(paper, 0), alpha(ink, strength / 5)), null, Shader.TileMode.CLAMP)
    })
    c.restore()
    // Scrub along its head.
    val scrub = fill(alpha(blend(ink, Color.BLACK, 0.2f), min(255, strength + 30)))
    repeat(5) {
        val t = rnd.nextFloat()
        val sx = cx + lean + (t - 0.5f) * wide * 0.66f
        val sy = base - tall * (1.02f - abs(t - 0.5f) * 0.24f)
        c.drawOval(sx - wide * 0.09f, sy - wide * 0.035f, sx + wide * 0.09f, sy + wide * 0.03f, scrub)
    }
    return base - tall
}

/** A pad of pine needles: flat underneath, heaped unevenly above, with the needles standing up along its top. */
private fun Painter.pinePad(px: Float, py: Float, wide: Float, paint: Paint, light: Paint?) {
    val pad = Path()
    val heaps = 7
    for (k in 0 until heaps) {
        val t = (k + 0.5f) / heaps - 0.5f
        val high = wide * (0.1f + 0.08f * (1f - 4f * t * t) + rnd.nextFloat() * 0.035f)
        val across = wide * (0.13f + rnd.nextFloat() * 0.05f)
        pad.op(Path().apply { addOval(px + t * wide - across, py - high, px + t * wide + across, py + high * 0.7f, Path.Direction.CW) }, Path.Op.UNION)
    }
    pad.op(Path().apply { addRect(px - wide, py + wide * 0.035f, px + wide, py + wide, Path.Direction.CW) }, Path.Op.DIFFERENCE)
    c.drawPath(pad, paint)
    val needle = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = wide * 0.012f; strokeCap = Paint.Cap.ROUND }
    val tufts = Path()
    repeat(26) {
        val t = rnd.nextFloat() - 0.5f
        val top = py - wide * (0.09f + 0.08f * (1f - 4f * t * t))
        tufts.moveTo(px + t * wide * 0.95f, top + wide * 0.03f)
        tufts.lineTo(px + t * wide * 0.95f + (rnd.nextFloat() - 0.5f) * wide * 0.07f, top - wide * (0.03f + rnd.nextFloat() * 0.05f))
    }
    c.drawPath(tufts, needle)
    if (light != null) {
        for (k in 0 until 4) {
            val t = (k + 0.5f) / 4f - 0.5f
            c.drawArc(RectF(px + t * wide * 0.8f - wide * 0.12f, py - wide * (0.12f + 0.07f * (1f - 4f * t * t)), px + t * wide * 0.8f + wide * 0.12f, py + wide * 0.02f), 205f, 130f, false, light)
        }
    }
}

/** An old pine, its trunk crooked and its needles in flat pads, leaning out to one side ([lean] 1 to the right, -1 to the left). */
internal fun Painter.crookedPine(bx: Float, by: Float, size: Float, lean: Float, bark: Int, needles: Int, light: Int? = null) {
    fun px(f: Float) = bx + lean * f * size
    fun py(f: Float) = by - f * size
    val wood = fill(bark)
    c.drawPath(tapered(floatArrayOf(px(0f), py(-0.02f), px(0.1f), py(0.26f), px(0.02f), py(0.5f), px(0.2f), py(0.76f), px(0.44f), py(0.9f)), size * 0.075f, size * 0.018f), wood)
    c.drawPath(tapered(floatArrayOf(px(0.08f), py(0.34f), px(0.3f), py(0.36f), px(0.56f), py(0.46f)), size * 0.035f, size * 0.01f), wood)
    c.drawPath(tapered(floatArrayOf(px(0.03f), py(0.52f), px(-0.14f), py(0.6f), px(-0.3f), py(0.68f)), size * 0.03f, size * 0.01f), wood)
    c.drawPath(tapered(floatArrayOf(px(0.16f), py(0.72f), px(0.02f), py(0.84f), px(-0.06f), py(0.94f)), size * 0.024f, size * 0.008f), wood)
    // Roots gripping the rock.
    c.drawPath(tapered(floatArrayOf(px(0f), py(0.04f), px(-0.1f), py(-0.01f), px(-0.2f), py(-0.04f)), size * 0.035f, size * 0.008f), wood)
    c.drawPath(tapered(floatArrayOf(px(0.02f), py(0.04f), px(0.12f), py(-0.01f), px(0.2f), py(-0.05f)), size * 0.03f, size * 0.008f), wood)
    val leaf = fill(needles)
    val lit = light?.let { stroke(it, size * 0.008f).apply { strokeCap = Paint.Cap.ROUND } }
    pinePad(px(0.56f), py(0.5f), size * 0.34f, leaf, lit)
    pinePad(px(0.34f), py(0.42f), size * 0.22f, leaf, lit)
    pinePad(px(-0.3f), py(0.72f), size * 0.26f, leaf, lit)
    pinePad(px(-0.06f), py(0.98f), size * 0.24f, leaf, lit)
    pinePad(px(0.46f), py(0.95f), size * 0.36f, leaf, lit)
    pinePad(px(0.22f), py(0.84f), size * 0.22f, leaf, lit)
}

/**
 * A swordsman of the rivers and lakes, [tall] high, standing on ([px], [py]) and facing [facing] (1 right, -1 left):
 * hair bound up with a ribbon, long robes blowing out behind, a straight sword held low.
 */
internal fun Painter.swordsman(px: Float, py: Float, tall: Float, facing: Float, paint: Paint) {
    fun x(a: Float) = px + facing * a * tall
    fun y(b: Float) = py - b * tall
    fun lock(x0: Float, y0: Float, x1: Float, y1: Float, wide: Float, bend: Float) {
        val path = Path()
        flame(path, x(x0), y(y0), x(x1), y(y1), wide * tall, bend * facing)
        c.drawPath(path, paint)
    }
    // Ribbon, hair and the robe's flying edges.
    lock(-0.02f, 0.965f, -0.32f, 1f, 0.022f, 0.16f)
    lock(-0.02f, 0.95f, -0.27f, 0.9f, 0.02f, -0.12f)
    lock(-0.03f, 0.9f, -0.2f, 0.6f, 0.07f, -0.14f)
    lock(-0.08f, 0.76f, -0.4f, 0.5f, 0.15f, -0.16f)
    lock(-0.08f, 0.5f, -0.36f, 0.34f, 0.035f, 0.14f)
    c.drawCircle(x(0f), y(0.905f), 0.052f * tall, paint)
    c.drawCircle(x(-0.012f), y(0.972f), 0.024f * tall, paint)
    c.drawPath(Path().apply {
        moveTo(x(-0.035f), y(0.86f))
        lineTo(x(-0.11f), y(0.83f))
        cubicTo(x(-0.13f), y(0.6f), x(-0.17f), y(0.3f), x(-0.31f), y(0.05f))
        lineTo(x(-0.2f), y(0f))
        lineTo(x(0.1f), y(0f))
        cubicTo(x(0.09f), y(0.3f), x(0.07f), y(0.6f), x(0.1f), y(0.82f))
        lineTo(x(0.035f), y(0.86f))
        close()
    }, paint)
    // The arm, and the sword held low before him.
    c.drawPath(capsule(x(0.075f), y(0.79f), 0.034f * tall, x(0.14f), y(0.53f), 0.024f * tall), paint)
    c.drawPath(tapered(floatArrayOf(x(0.1f), y(0.6f), x(0.2f), y(0.4f), x(0.4f), y(0.04f)), 0.02f * tall, 0.004f * tall), paint)
    c.drawPath(tapered(floatArrayOf(x(0.115f), y(0.49f), x(0.175f), y(0.52f)), 0.014f * tall, 0.014f * tall), paint)
}

/**
 * A wandering samurai, [tall] high, standing on ([px], [py]) and facing [facing]: a wide straw hat, a coat blowing
 * out behind, and two swords through his sash.
 */
internal fun Painter.ronin(px: Float, py: Float, tall: Float, facing: Float, paint: Paint) {
    fun x(a: Float) = px + facing * a * tall
    fun y(b: Float) = py - b * tall
    // The coat's skirt blowing back.
    val skirt = Path()
    flame(skirt, x(-0.09f), y(0.6f), x(-0.36f), y(0.26f), 0.14f * tall, 0.14f * facing)
    c.drawPath(skirt, paint)
    // The swords: hilts forward, scabbards out behind.
    c.drawPath(tapered(floatArrayOf(x(0.27f), y(0.63f), x(0.08f), y(0.555f), x(-0.16f), y(0.47f), x(-0.38f), y(0.41f)), 0.024f * tall, 0.013f * tall), paint)
    c.drawPath(tapered(floatArrayOf(x(0.22f), y(0.565f), x(0.04f), y(0.51f), x(-0.2f), y(0.455f)), 0.019f * tall, 0.012f * tall), paint)
    c.drawOval(x(0.135f) - 0.016f * tall, y(0.605f) - 0.03f * tall, x(0.135f) + 0.016f * tall, y(0.605f) + 0.03f * tall, paint)
    // Head and hat.
    c.drawCircle(x(0.008f), y(0.862f), 0.046f * tall, paint)
    c.drawPath(Path().apply {
        moveTo(x(-0.18f), y(0.868f))
        quadTo(x(0f), y(1.03f), x(0.18f), y(0.868f))
        quadTo(x(0f), y(0.9f), x(-0.18f), y(0.868f))
        close()
    }, paint)
    // Kimono and wide trousers.
    c.drawPath(Path().apply {
        moveTo(x(-0.07f), y(0.84f))
        quadTo(x(-0.135f), y(0.83f), x(-0.14f), y(0.76f))
        lineTo(x(-0.1f), y(0.5f))
        lineTo(x(-0.2f), y(0f))
        lineTo(x(-0.035f), y(0f))
        lineTo(x(0f), y(0.14f))
        lineTo(x(0.025f), y(0f))
        lineTo(x(0.175f), y(0f))
        lineTo(x(0.1f), y(0.5f))
        lineTo(x(0.14f), y(0.76f))
        quadTo(x(0.135f), y(0.83f), x(0.07f), y(0.84f))
        close()
    }, paint)
    // Sleeves: one hanging, one with its hand resting on the hilts.
    c.drawPath(Path().apply { moveTo(x(-0.13f), y(0.8f)); lineTo(x(-0.215f), y(0.5f)); lineTo(x(-0.13f), y(0.46f)); lineTo(x(-0.09f), y(0.62f)); close() }, paint)
    c.drawPath(Path().apply { moveTo(x(0.13f), y(0.8f)); lineTo(x(0.2f), y(0.57f)); lineTo(x(0.13f), y(0.5f)); lineTo(x(0.09f), y(0.64f)); close() }, paint)
}

/** A gate of the kind that stands before a shrine, [size] across its top beam, standing on [base]. */
internal fun Painter.torii(cx: Float, base: Float, size: Float, red: Int, shade: Int, black: Int) {
    val post = Paint(Paint.ANTI_ALIAS_FLAG)
    for (side in listOf(-1f, 1f)) {
        val foot = cx + side * size * 0.31f
        val head = cx + side * size * 0.275f
        val half = size * 0.036f
        post.shader = LinearGradient(foot - half, 0f, foot + half, 0f, intArrayOf(blend(red, Color.WHITE, 0.12f), red, shade), null, Shader.TileMode.CLAMP)
        c.drawPath(Path().apply { moveTo(foot - half, base); lineTo(head - half * 0.9f, base - size * 0.8f); lineTo(head + half * 0.9f, base - size * 0.8f); lineTo(foot + half, base); close() }, post)
        c.drawRect(foot - half * 1.35f, base - size * 0.055f, foot + half * 1.35f, base, fill(black))
    }
    // The tie-beam through the posts, and the strut above it.
    c.drawRect(cx - size * 0.41f, base - size * 0.6f, cx + size * 0.41f, base - size * 0.545f, fill(red))
    c.drawRect(cx - size * 0.41f, base - size * 0.555f, cx + size * 0.41f, base - size * 0.545f, fill(shade))
    c.drawRect(cx - size * 0.03f, base - size * 0.76f, cx + size * 0.03f, base - size * 0.6f, fill(red))
    // The two beams of its head: the lower straight, the upper curving up at its ends.
    c.drawRect(cx - size * 0.44f, base - size * 0.8f, cx + size * 0.44f, base - size * 0.745f, fill(red))
    c.drawRect(cx - size * 0.44f, base - size * 0.757f, cx + size * 0.44f, base - size * 0.745f, fill(shade))
    c.drawPath(Path().apply {
        moveTo(cx - size * 0.54f, base - size * 0.905f)
        quadTo(cx, base - size * 0.835f, cx + size * 0.54f, base - size * 0.905f)
        lineTo(cx + size * 0.5f, base - size * 0.8f)
        quadTo(cx, base - size * 0.79f, cx - size * 0.5f, base - size * 0.8f)
        close()
    }, fill(black))
}

/** A great lone mountain with snow on its head: [wide] at its foot, [tall] high, fading into the haze below. */
internal fun Painter.snowPeak(cx: Float, base: Float, wide: Float, tall: Float, rock: Int, snow: Int, haze: Int) {
    val shape = Path().apply {
        moveTo(cx - wide / 2, base)
        cubicTo(cx - wide * 0.28f, base - tall * 0.2f, cx - wide * 0.13f, base - tall * 0.78f, cx - wide * 0.05f, base - tall)
        lineTo(cx - wide * 0.02f, base - tall * 0.975f)
        lineTo(cx + wide * 0.015f, base - tall * 0.995f)
        lineTo(cx + wide * 0.05f, base - tall * 0.975f)
        cubicTo(cx + wide * 0.14f, base - tall * 0.78f, cx + wide * 0.3f, base - tall * 0.2f, cx + wide / 2, base)
        close()
    }
    c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, base - tall, 0f, base, intArrayOf(rock, rock, haze), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) })
    c.save()
    c.clipPath(shape)
    // The snow, running down in fingers.
    val cap = Path()
    cap.moveTo(cx - wide * 0.3f, base - tall * 1.05f)
    val fingers = 9
    for (k in 0..fingers) {
        val t = k / fingers.toFloat()
        val fx = cx + (t - 0.5f) * wide * 0.34f
        val deep = 0.2f + 0.1f * sin(t * 3.1416f) + (if (k % 2 == 0) 0.07f else -0.03f) + rnd.nextFloat() * 0.05f
        cap.lineTo(fx, base - tall * (1f - deep))
        cap.lineTo(fx + wide * 0.34f / fingers * 0.5f, base - tall * (1f - deep * 0.55f))
    }
    cap.lineTo(cx + wide * 0.3f, base - tall * 1.05f)
    cap.close()
    c.drawPath(cap, fill(snow))
    // Shade down one side.
    c.drawRect(cx - wide, base - tall * 1.1f, cx + wide, base, Paint().apply { shader = LinearGradient(cx - wide * 0.1f, 0f, cx + wide * 0.3f, 0f, alpha(Color.BLACK, 0), alpha(Color.BLACK, 60), Shader.TileMode.CLAMP) })
    c.restore()
}

/**
 * A crane in flight, [span] from beak to feet, its neck stretched out before it and its legs trailing: white, with
 * black at the edge of its wings and a red crown. [wings] is how far up its wings are (below nothing, down).
 */
internal fun Painter.tancho(cx: Float, cy: Float, span: Float, flip: Boolean, white: Int, black: Int, crown: Int?, wings: Float = 1f, line: Int? = null) {
    val d = if (flip) -1f else 1f
    fun x(a: Float) = cx + d * a * span
    fun y(b: Float) = cy + b * span
    fun wy(b: Float) = cy + b * span * wings
    val body = fill(white)
    val dark = fill(black)
    val edge = line?.let { stroke(it, span * 0.006f).apply { strokeJoin = Paint.Join.ROUND } }
    fun put(path: Path, paint: Paint) {
        c.drawPath(path, paint)
        if (edge != null) c.drawPath(path, edge)
    }
    // The far wing, reaching forward.
    put(Path().apply {
        moveTo(x(0.1f), y(-0.02f))
        cubicTo(x(0.2f), wy(-0.2f), x(0.27f), wy(-0.42f), x(0.22f), wy(-0.6f))
        lineTo(x(0.19f), wy(-0.5f)); lineTo(x(0.135f), wy(-0.53f)); lineTo(x(0.125f), wy(-0.43f)); lineTo(x(0.07f), wy(-0.44f))
        cubicTo(x(0.04f), wy(-0.28f), x(0f), wy(-0.14f), x(-0.06f), y(-0.02f))
        close()
    }, fill(blend(white, black, 0.16f)))
    c.drawPath(Path().apply {
        moveTo(x(-0.06f), y(-0.02f))
        cubicTo(x(0f), wy(-0.14f), x(0.04f), wy(-0.28f), x(0.07f), wy(-0.42f))
        lineTo(x(0.12f), wy(-0.36f))
        cubicTo(x(0.08f), wy(-0.22f), x(0.05f), wy(-0.1f), x(0.01f), y(-0.03f))
        close()
    }, dark)
    // Legs, tail, body and neck.
    c.drawPath(tapered(floatArrayOf(x(-0.1f), y(0.012f), x(-0.3f), y(0.05f), x(-0.5f), y(0.075f)), span * 0.02f, span * 0.007f), dark)
    c.drawPath(Path().apply { moveTo(x(-0.12f), y(-0.035f)); lineTo(x(-0.26f), y(0.015f)); lineTo(x(-0.12f), y(0.04f)); close() }, dark)
    put(Path().apply { addOval(x(0f) - span * 0.16f, y(0f) - span * 0.052f, x(0f) + span * 0.16f, y(0f) + span * 0.052f, Path.Direction.CW) }, body)
    c.drawPath(tapered(floatArrayOf(x(0.11f), y(-0.005f), x(0.26f), y(-0.05f), x(0.38f), y(-0.062f), x(0.46f), y(-0.076f)), span * 0.046f, span * 0.02f), dark)
    put(Path().apply { addCircle(x(0.465f), y(-0.078f), span * 0.021f, Path.Direction.CW) }, body)
    c.drawPath(Path().apply { moveTo(x(0.478f), y(-0.092f)); lineTo(x(0.6f), y(-0.07f)); lineTo(x(0.478f), y(-0.062f)); close() }, fill(blend(black, white, 0.25f)))
    if (crown != null) c.drawCircle(x(0.462f), y(-0.097f), span * 0.012f, fill(crown))
    // The near wing, over all, swept back.
    put(Path().apply {
        moveTo(x(0.08f), y(-0.02f))
        cubicTo(x(0.1f), wy(-0.22f), x(0.02f), wy(-0.46f), x(-0.2f), wy(-0.64f))
        lineTo(x(-0.19f), wy(-0.54f)); lineTo(x(-0.25f), wy(-0.56f)); lineTo(x(-0.22f), wy(-0.47f)); lineTo(x(-0.28f), wy(-0.47f)); lineTo(x(-0.24f), wy(-0.39f)); lineTo(x(-0.295f), wy(-0.375f))
        cubicTo(x(-0.26f), wy(-0.25f), x(-0.21f), wy(-0.12f), x(-0.15f), y(-0.02f))
        close()
    }, body)
    c.drawPath(Path().apply {
        moveTo(x(-0.15f), y(-0.02f))
        cubicTo(x(-0.21f), wy(-0.12f), x(-0.26f), wy(-0.25f), x(-0.295f), wy(-0.375f))
        lineTo(x(-0.215f), wy(-0.34f))
        cubicTo(x(-0.18f), wy(-0.22f), x(-0.13f), wy(-0.1f), x(-0.08f), y(-0.03f))
        close()
    }, dark)
    val feather = stroke(alpha(black, 70), span * 0.005f)
    for (k in 0 until 4) c.drawLine(x(0.04f - k * 0.03f), y(-0.04f), x(-0.02f - k * 0.045f), wy(-0.3f - k * 0.045f), feather)
}

/** A seal pressed in red: a square with a key pattern cut in it (no writing). */
internal fun Painter.seal(cx: Float, cy: Float, size: Float, red: Int, paper: Int) {
    c.drawRoundRect(RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), size * 0.12f, size * 0.12f, fill(red))
    val cut = stroke(paper, size * 0.075f).apply { strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER }
    fun x(f: Float) = cx + (f - 0.5f) * size
    fun y(f: Float) = cy + (f - 0.5f) * size
    c.drawPath(Path().apply {
        // A square spiral in each half, one the other turned about.
        moveTo(x(0.2f), y(0.2f)); lineTo(x(0.44f), y(0.2f)); lineTo(x(0.44f), y(0.8f)); lineTo(x(0.2f), y(0.8f)); lineTo(x(0.2f), y(0.38f)); lineTo(x(0.32f), y(0.38f)); lineTo(x(0.32f), y(0.64f))
        moveTo(x(0.8f), y(0.8f)); lineTo(x(0.56f), y(0.8f)); lineTo(x(0.56f), y(0.2f)); lineTo(x(0.8f), y(0.2f)); lineTo(x(0.8f), y(0.62f)); lineTo(x(0.68f), y(0.62f)); lineTo(x(0.68f), y(0.36f))
    }, cut)
}

/** Grass along the ground from [from] to [to]: blades leaning in the wind, the tallest [tallest] high. */
internal fun Painter.grass(from: Float, to: Float, ground: (Float) -> Float, colour: Int, tallest: Float, lean: Float = 0.3f) {
    val n = ((to - from) / (u * 0.7f)).toInt().coerceIn(8, 600)
    val blade = stroke(colour, u * 0.3f).apply { strokeCap = Paint.Cap.ROUND }
    val path = Path()
    repeat(n) {
        val bx = from + rnd.nextFloat() * (to - from)
        val by = ground(bx) + u * 0.4f
        val tall = tallest * (0.35f + rnd.nextFloat() * 0.65f)
        val bend = (lean + (rnd.nextFloat() - 0.5f) * 0.5f) * tall
        path.moveTo(bx, by)
        path.quadTo(bx + bend * 0.2f, by - tall * 0.6f, bx + bend, by - tall)
    }
    c.drawPath(path, blade)
}

/** Waves as the old prints draw them: row behind row of rounded swells, each edged with foam and lined within. */
internal fun Painter.printWaves(top: Float, bottom: Float, deep: Int, mid: Int, foam: Int, swell: Float) {
    val rows = max(3, ((bottom - top) / (swell * 0.42f)).toInt())
    for (row in 0..rows) {
        val y0 = top + (bottom - top) * row / rows
        val colour = if (row % 2 == 0) mid else deep
        val shift = if (row % 2 == 0) 0f else swell * 0.9f
        var wx = r.left - swell * 2f + shift + (rnd.nextFloat() - 0.5f) * swell * 0.3f
        val body = Path()
        val crest = Path()
        body.moveTo(r.left - swell * 2f, r.bottom + u)
        body.lineTo(wx, y0)
        crest.moveTo(wx, y0)
        while (wx < r.right + swell * 2f) {
            val wide = swell * (1.6f + rnd.nextFloat() * 0.5f)
            val high = swell * (0.42f + rnd.nextFloat() * 0.18f)
            for (path in listOf(body, crest)) {
                path.cubicTo(wx + wide * 0.3f, y0 - high * 1.1f, wx + wide * 0.75f, y0 - high * 1.3f, wx + wide * 0.86f, y0 - high * 0.5f)
                path.cubicTo(wx + wide * 0.9f, y0 - high * 0.15f, wx + wide * 0.96f, y0, wx + wide, y0)
            }
            // The lines within the swell, following its back.
            for (k in 1..2) {
                val inner = Path().apply {
                    moveTo(wx + wide * 0.1f * k, y0 + swell * 0.08f * k)
                    cubicTo(wx + wide * 0.34f, y0 - high * (1.1f - 0.36f * k), wx + wide * 0.68f, y0 - high * (1.3f - 0.42f * k), wx + wide * (0.84f - 0.05f * k), y0 - high * (0.5f - 0.2f * k))
                }
                crest.addPath(inner)
            }
            wx += wide
        }
        body.lineTo(r.right + swell * 2f, r.bottom + u)
        body.close()
        c.drawPath(body, fill(colour))
        c.drawPath(crest, stroke(foam, u * 0.42f).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
    }
}

/**
 * Peaks standing out of the mist in the manner of an ink painting, a red sun, a pagoda on a far height, cranes, and on
 * a cliff under an old pine a swordsman looking out over it all.
 */
internal fun Painter.wuxiaScene() {
    val paper = rgb(0xEDE2CA)
    sky(intArrayOf(rgb(0xE6D8BC), rgb(0xEFE5CE), rgb(0xDCD7C5)), floatArrayOf(0f, 0.5f, 1f))
    glow(x(rnd.nextFloat()), y(0.05f), w * 0.6f, rgb(0xC7AE86), 46)
    val m = min(w, h * 1.15f)
    val side = if (rnd.nextBoolean()) 1f else -1f
    fun fx(f: Float) = if (side > 0f) x(f) else x(1f - f)
    val far = rgb(0x8AA09D)
    val mid = rgb(0x4A6662)
    val near = rgb(0x22383A)
    val ink = rgb(0x142022)
    // The sun, with a band of cloud across it.
    val sunX = fx(0.3f)
    val sunY = y(0.21f)
    val sunR = m * 0.135f
    glow(sunX, sunY, sunR * 2.4f, rgb(0xE9A27E), 70)
    c.drawCircle(sunX, sunY, sunR, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, sunY - sunR, 0f, sunY + sunR, rgb(0xD2452F), rgb(0xBF3A2A), Shader.TileMode.CLAMP) })
    c.drawRect(r.left, sunY + sunR * 0.28f, r.right, sunY + sunR * 0.62f, Paint().apply {
        shader = LinearGradient(sunX - sunR * 2.6f, 0f, sunX + sunR * 2.6f, 0f, intArrayOf(alpha(paper, 0), alpha(paper, 235), alpha(paper, 235), alpha(paper, 0)), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
    })
    // Peaks, far, nearer and near, with mist lying between.
    val across = max(4, (w / (m * 0.2f)).toInt())
    for (k in 0 until across) {
        val px = x((k + 0.2f + rnd.nextFloat() * 0.6f) / across)
        inkPeak(px, y(0.64f), m * (0.1f + rnd.nextFloat() * 0.07f), h * (0.18f + rnd.nextFloat() * 0.2f), far, paper, 120)
    }
    mist(0.61f, 0.14f, paper, 235)
    val templeAt = fx(0.26f)
    var templeTop = y(0.4f)
    for (k in 0 until across - 1) {
        val px = x((k + 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f) / (across - 1))
        if (abs(px - templeAt) < m * 0.16f) continue
        inkPeak(px, y(0.8f), m * (0.13f + rnd.nextFloat() * 0.08f), h * (0.26f + rnd.nextFloat() * 0.2f), mid, paper, 190)
    }
    templeTop = inkPeak(templeAt, y(0.8f), m * 0.2f, h * 0.38f, mid, paper, 200)
    // A waterfall down the temple's peak, and the temple on its head.
    val fall = templeAt + m * 0.045f * side
    c.drawLine(fall, templeTop + h * 0.07f, fall + m * 0.006f, y(0.76f), stroke(alpha(Color.WHITE, 170), m * 0.008f).apply { strokeCap = Paint.Cap.ROUND })
    c.drawLine(fall - m * 0.01f, templeTop + h * 0.1f, fall - m * 0.007f, y(0.74f), stroke(alpha(Color.WHITE, 100), m * 0.004f))
    glow(fall, y(0.77f), m * 0.06f, Color.WHITE, 110)
    val temple = Path()
    pagoda(temple, templeAt - m * 0.02f * side, templeTop + h * 0.012f, m * 0.2f, floors = 4)
    c.drawPath(temple, fill(blend(mid, ink, 0.55f)))
    mist(0.79f, 0.13f, paper, 240)
    inkPeak(fx(0.08f), y(1.04f), m * 0.26f, h * 0.34f, near, paper, 235)
    inkPeak(fx(0.36f), y(1.06f), m * 0.16f, h * 0.2f, near, paper, 225)
    mist(0.985f, 0.16f, paper, 225)
    // Cranes crossing the sun.
    for (k in 0 until 3) {
        val span = m * (0.15f - k * 0.025f)
        tancho(sunX + side * (sunR * 1.5f + k * m * 0.13f), sunY - sunR * 0.5f + k * m * 0.075f, span, flip = side > 0f, white = blend(ink, mid, 0.2f), black = ink, crown = null, wings = if (k == 1) -0.75f else 1f)
    }
    // The cliff, the pine and the swordsman.
    val lipX = fx(0.56f)
    val lipY = y(0.668f)
    val cliff = Path().apply {
        moveTo(fx(1.05f), y(0.615f))
        cubicTo(fx(0.9f), y(0.612f), fx(0.74f), y(0.648f), lipX, lipY)
        lineTo(fx(0.578f), y(0.69f))
        lineTo(fx(0.63f), y(0.7f))
        lineTo(fx(0.655f), y(0.735f))
        lineTo(fx(0.715f), y(0.76f))
        lineTo(fx(0.735f), y(0.82f))
        lineTo(fx(0.79f), y(0.86f))
        lineTo(fx(0.8f), y(0.94f))
        lineTo(fx(0.86f), y(1.05f))
        lineTo(fx(1.05f), y(1.05f))
        close()
    }
    c.drawPath(cliff, fill(ink))
    c.save()
    c.clipPath(cliff)
    repeat(26) {
        val sx = fx(0.58f + rnd.nextFloat() * 0.42f)
        val sy = y(0.65f + rnd.nextFloat() * 0.3f)
        c.drawLine(sx, sy, sx + (rnd.nextFloat() - 0.5f) * m * 0.02f, sy + h * (0.03f + rnd.nextFloat() * 0.08f), stroke(alpha(mid, 70 + rnd.nextInt(60)), m * 0.004f))
    }
    c.restore()
    crookedPine(fx(0.86f), y(0.626f), min(h * 0.27f, m * 0.4f), -side, blend(ink, rgb(0x3A2A1E), 0.35f), blend(near, ink, 0.45f), alpha(mid, 200))
    swordsman(fx(0.645f), y(0.655f), h * 0.155f, -side, fill(ink))
    seal(fx(0.075f), y(0.9f), m * 0.06f, rgb(0xB8362A), paper)
    grain(0.9f)
}

/**
 * Old Japan at the day's end: a great snow-capped mountain with the sun behind its shoulder, mist over the hills, a
 * shrine gate, blossom, and a samurai on the road in a wide straw hat.
 */
internal fun Painter.samuraiScene() {
    sky(intArrayOf(rgb(0x232750), rgb(0x6A3F6B), rgb(0xD9694F), rgb(0xF4C083), rgb(0xF7DDB0)), floatArrayOf(0f, 0.3f, 0.52f, 0.68f, 0.8f))
    val m = min(w, h * 1.15f)
    val side = if (rnd.nextBoolean()) 1f else -1f
    fun fx(f: Float) = if (side > 0f) x(f) else x(1f - f)
    val night = rgb(0x141731)
    val hills = rgb(0x2B2F5C)
    val haze = rgb(0xF2C39A)
    // The sun, low and red, and the mountain before it.
    val sunX = fx(0.66f)
    val sunY = y(0.4f)
    val sunR = m * 0.17f
    glow(sunX, sunY, sunR * 2.6f, rgb(0xFFB070), 120)
    c.drawCircle(sunX, sunY, sunR, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, sunY - sunR, 0f, sunY + sunR, rgb(0xF0553A), rgb(0xD23A2E), Shader.TileMode.CLAMP) })
    stars(40, 0.2f, rgb(0xF6E6D0))
    snowPeak(fx(0.36f), y(0.69f), max(w * 0.95f, m * 1.1f), h * 0.42f, rgb(0x343B70), rgb(0xF6ECE4), rgb(0xC98F8A))
    // Thin cloud across the mountain's shoulders.
    for ((at, wide) in listOf(0.46f to 0.5f, 0.535f to 0.36f)) {
        val cx = fx(0.2f + rnd.nextFloat() * 0.4f)
        c.drawRoundRect(RectF(cx - w * wide / 2, y(at) - h * 0.008f, cx + w * wide / 2, y(at) + h * 0.008f), h * 0.008f, h * 0.008f, fill(alpha(rgb(0xF8E4CC), 200)))
        c.drawRoundRect(RectF(cx - w * wide * 0.3f, y(at) + h * 0.012f, cx + w * wide * 0.44f, y(at) + h * 0.024f), h * 0.006f, h * 0.006f, fill(alpha(rgb(0xF8E4CC), 150)))
    }
    mist(0.685f, 0.1f, haze, 210)
    // Hills, a pagoda among them, and mist again.
    layer(ridge(0.74f, 0.66f, rough = 0.3f), hills, blend(hills, haze, 0.5f), 0.66f, 0.84f)
    val tower = Path()
    pagoda(tower, fx(0.88f), y(0.715f), m * 0.2f, floors = 5)
    c.drawPath(tower, fill(blend(hills, night, 0.3f)))
    mist(0.79f, 0.09f, haze, 150)
    // The near ground, its grass, the gate and the traveller.
    val heights = FloatArray(33) { 0.845f + 0.03f * sin(it * 0.45f + rnd.nextFloat() * 0.3f) - 0.04f * (it / 32f) * (1f - it / 32f) * 4f * 0.3f }
    fun ground(px: Float): Float {
        val t = (((if (side > 0f) px - r.left else r.right - px)) / w * 32f).coerceIn(0f, 31.999f)
        val k = t.toInt()
        return y(heights[k] + (heights[k + 1] - heights[k]) * (t - k))
    }
    val earth = Path().apply {
        moveTo(r.left - 2f, r.bottom + 2f)
        for (k in 0..64) {
            val px = r.left + w * k / 64f
            lineTo(px, ground(px))
        }
        lineTo(r.right + 2f, r.bottom + 2f)
        close()
    }
    val gateX = fx(0.25f)
    torii(gateX, ground(gateX) + u, m * 0.36f, rgb(0xC9382C), rgb(0x8A211D), night)
    c.drawPath(earth, fill(night))
    grass(r.left, r.right, ::ground, night, h * 0.045f, lean = 0.4f * side)
    val manX = fx(0.62f)
    ronin(manX, ground(manX) + u * 0.6f, h * 0.2f, -side, fill(night))
    // Blossom from the corner, and petals on the wind.
    val bark = rgb(0x1E1622)
    val petal = rgb(0xF6C9D2)
    blossomBranch(fx(1.04f), y(0.1f), fx(0.5f), y(0.16f), bark, petal, rgb(0xE07A92), m / min(w, h) * 1.1f)
    blossomBranch(fx(1.04f), y(0.2f), fx(0.72f), y(0.3f), bark, petal, rgb(0xE07A92), m / min(w, h) * 0.8f)
    repeat(22) {
        val px = x(rnd.nextFloat())
        val py = y(0.1f + rnd.nextFloat() * 0.7f)
        c.save()
        c.rotate(rnd.nextFloat() * 180f, px, py)
        c.drawOval(px - m * 0.011f, py - m * 0.005f, px + m * 0.011f, py + m * 0.005f, fill(alpha(petal, 150 + rnd.nextInt(100))))
        c.restore()
    }
    birds(5, fx(0.3f), y(0.24f), 0.1f, alpha(night, 200))
    grain(0.8f)
}
