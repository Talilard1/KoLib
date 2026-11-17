package dev.kolib.app.ui.cover

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/*
 * The dragon: a long one in the old East Asian manner, with antlers, a mane, whiskers, four clawed legs and a
 * pearl before it, every scale drawn. It is drawn here from nothing, in the book's own colours, and copies no
 * particular picture.
 */

/**
 * The dragon's body laid out along its back, from the tip of its tail to the back of its head: where each part of
 * it is, which way it runs, how thick it is, and where across it the belly shows. The body rolls as it turns, so
 * that the belly is underneath wherever the body runs level, and faces us where it climbs.
 */
private class DragonBody(via: FloatArray, h: Float, u: Float, val n: Int = 620) {
    val x = FloatArray(n + 1)
    val y = FloatArray(n + 1)
    val tx = FloatArray(n + 1)
    val ty = FloatArray(n + 1)
    val half = FloatArray(n + 1)
    val belly = FloatArray(n + 1)
    val along = FloatArray(n + 1)
    val length: Float

    init {
        val count = via.size / 2
        fun vx(i: Int) = via[i.coerceIn(0, count - 1) * 2] * u
        fun vy(i: Int) = via[i.coerceIn(0, count - 1) * 2 + 1] * h
        for (i in 0..n) {
            val at = i / n.toFloat() * (count - 1)
            val seg = min(count - 2, at.toInt())
            val f = at - seg
            fun spline(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (3 * b - a - 3 * c + d) * f * f * f)
            x[i] = spline(vx(seg - 1), vx(seg), vx(seg + 1), vx(seg + 2))
            y[i] = spline(vy(seg - 1), vy(seg), vy(seg + 1), vy(seg + 2))
        }
        for (i in 0..n) {
            val dx = x[min(n, i + 1)] - x[max(0, i - 1)]
            val dy = y[min(n, i + 1)] - y[max(0, i - 1)]
            val len = max(0.0001f, hypot(dx, dy))
            tx[i] = dx / len
            ty[i] = dy / len
            along[i] = if (i == 0) 0f else along[i - 1] + hypot(x[i] - x[i - 1], y[i] - y[i - 1])
            // Thin at the tail's tip, thickest through the trunk, and slimmer again up the neck.
            val f = i / n.toFloat()
            val wide = when {
                f < 0.45f -> 0.9f + 10.7f * sin(1.5708f * f / 0.45f).pow(1.6f)
                f < 0.66f -> 11.6f
                else -> 11.6f - 4.6f * sin(1.5708f * (f - 0.66f) / 0.34f).pow(1.25f)
            }
            half[i] = wide / 2 * u
            belly[i] = 0.76f * tanh(2.4f * tx[i])
        }
        length = along[n]
    }

    /** Where along the body (as a place in the arrays, between two of them) is [a] from the tail's tip. */
    fun index(a: Float): Float {
        if (a <= 0f) return 0f
        if (a >= length) return n.toFloat()
        var lo = 0
        var hi = n
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (along[mid] <= a) lo = mid else hi = mid
        }
        val span = along[hi] - along[lo]
        return lo + if (span > 0f) (a - along[lo]) / span else 0f
    }

    /** The place on the cover of the spot [v] across the body (-1 and 1 are its edges) at [i] along it. */
    fun place(i: Float, v: Float, out: FloatArray) {
        val k = i.toInt().coerceIn(0, n - 1)
        val f = (i - k).coerceIn(0f, 1f)
        val hw = half[k] + (half[k + 1] - half[k]) * f
        val nx = -(ty[k] + (ty[k + 1] - ty[k]) * f)
        val ny = tx[k] + (tx[k + 1] - tx[k]) * f
        out[0] = x[k] + (x[k + 1] - x[k]) * f + nx * v * hw
        out[1] = y[k] + (y[k + 1] - y[k]) * f + ny * v * hw
    }
}

/** A line through [pts] (pairs of x and y), smoothed, as thick as [w0] where it starts and [w1] where it ends. */
internal fun tapered(pts: FloatArray, w0: Float, w1: Float): Path {
    val n = pts.size / 2
    fun px(i: Int) = pts[i.coerceIn(0, n - 1) * 2]
    fun py(i: Int) = pts[i.coerceIn(0, n - 1) * 2 + 1]
    val sub = 10
    val total = (n - 1) * sub
    val xs = FloatArray(total + 1)
    val ys = FloatArray(total + 1)
    for (k in 0..total) {
        val at = k / sub.toFloat()
        val seg = min(n - 2, at.toInt())
        val f = at - seg
        fun sp(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (3 * b - a - 3 * c + d) * f * f * f)
        xs[k] = sp(px(seg - 1), px(seg), px(seg + 1), px(seg + 2))
        ys[k] = sp(py(seg - 1), py(seg), py(seg + 1), py(seg + 2))
    }
    val path = Path()
    val rx = FloatArray(total + 1)
    val ry = FloatArray(total + 1)
    for (k in 0..total) {
        val dx = xs[min(total, k + 1)] - xs[max(0, k - 1)]
        val dy = ys[min(total, k + 1)] - ys[max(0, k - 1)]
        val len = max(0.0001f, hypot(dx, dy))
        val w = (w0 + (w1 - w0) * k / total) / 2
        val lx = xs[k] - dy / len * w
        val ly = ys[k] + dx / len * w
        rx[k] = xs[k] + dy / len * w
        ry[k] = ys[k] - dx / len * w
        if (k == 0) path.moveTo(lx, ly) else path.lineTo(lx, ly)
    }
    for (k in total downTo 0) path.lineTo(rx[k], ry[k])
    path.close()
    return path
}

/** A lick of flame, or a lock of mane: from a base [wide] across at ([bx], [by]) to a point at ([tx], [ty]), curling to one side by [bend]. */
internal fun flame(path: Path, bx: Float, by: Float, tx: Float, ty: Float, wide: Float, bend: Float) {
    val dx = tx - bx
    val dy = ty - by
    val len = max(0.001f, hypot(dx, dy))
    val qx = -dy / len
    val qy = dx / len
    val off = bend * len
    path.moveTo(bx + qx * wide / 2, by + qy * wide / 2)
    path.cubicTo(
        bx + dx * 0.3f + qx * (wide * 0.8f + off * 0.7f), by + dy * 0.3f + qy * (wide * 0.8f + off * 0.7f),
        bx + dx * 0.72f + qx * (wide * 0.2f + off * 1.15f), by + dy * 0.72f + qy * (wide * 0.2f + off * 1.15f),
        tx, ty,
    )
    path.cubicTo(
        bx + dx * 0.7f + qx * (-wide * 0.12f + off * 0.9f), by + dy * 0.7f + qy * (-wide * 0.12f + off * 0.9f),
        bx + dx * 0.3f + qx * (-wide * 0.6f + off * 0.3f), by + dy * 0.3f + qy * (-wide * 0.6f + off * 0.3f),
        bx - qx * wide / 2, by - qy * wide / 2,
    )
    path.close()
}

/** A limb's length, thicker at one end than the other, with round ends. */
internal fun capsule(x0: Float, y0: Float, r0: Float, x1: Float, y1: Float, r1: Float): Path {
    val d = max(0.001f, hypot(x1 - x0, y1 - y0))
    val qx = -(y1 - y0) / d
    val qy = (x1 - x0) / d
    val out = Path().apply { addCircle(x0, y0, r0, Path.Direction.CW) }
    out.op(Path().apply { addCircle(x1, y1, r1, Path.Direction.CW) }, Path.Op.UNION)
    out.op(
        Path().apply {
            moveTo(x0 + qx * r0, y0 + qy * r0)
            lineTo(x1 + qx * r1, y1 + qy * r1)
            lineTo(x1 - qx * r1, y1 - qy * r1)
            lineTo(x0 - qx * r0, y0 - qy * r0)
            close()
        },
        Path.Op.UNION,
    )
    return out
}

/** A dragon, long and scaled, walking across a cover of two colours with its head turned back to the pearl it guards. */
internal fun CoverGenerator.wyrm(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val dark = isDark(g)
    val clothC = s.c.ink
    c.drawColor(g)
    val flip = rnd.nextBoolean()
    c.save()
    if (flip) c.scale(-1f, 1f, s.w / 2, 0f)
    // The second colour sweeps up from the foot, woven like cloth.
    val sweep = Path().apply {
        moveTo(s.w + 2f, 0.4f * s.h)
        cubicTo(0.72f * s.w, 0.5f * s.h, 0.46f * s.w, 0.82f * s.h, -2f, 0.9f * s.h)
        lineTo(-2f, s.h + 2f)
        lineTo(s.w + 2f, s.h + 2f)
        close()
    }
    c.drawPath(sweep, s.fill(clothC))
    c.save()
    c.clipPath(sweep)
    val weave = s.fill(faint(g, 26))
    var wy = 0.3f * s.h
    var row = 0
    while (wy < s.h) {
        var wx = if (row % 2 == 0) 0f else 0.55f * u
        while (wx < s.w) {
            c.drawRect(wx, wy, wx + 0.6f * u, wy + 0.5f * u, weave)
            wx += 1.1f * u
        }
        wy += 1f * u
        row++
    }
    c.restore()
    val rest = Path().apply { addRect(-2f, -2f, s.w + 2f, s.h + 2f, Path.Direction.CW) }
    rest.op(sweep, Path.Op.DIFFERENCE)
    // Clouds behind it, in each field a shade of that field.
    val clouds = Path()
    val curls = Path()
    for ((cx, cy, size) in listOf(Triple(85f, 0.285f, 1f), Triple(35f, 0.425f, 0.95f), Triple(46f, 0.7f, 1.05f), Triple(89f, 0.86f, 0.85f), Triple(11f, 0.262f, 0.7f))) {
        val q = u * size
        val x0 = cx * u
        val y0 = cy * s.h
        val puffs = floatArrayOf(0f, 0f, 3.3f, 4.3f, -0.9f, 2.6f, -4.1f, 0.5f, 2.5f, 7.8f, 0.3f, 1.8f)
        val one = Path()
        for (k in 0 until 4) one.op(Path().apply { addCircle(x0 + puffs[k * 3] * q, y0 + puffs[k * 3 + 1] * q, puffs[k * 3 + 2] * q, Path.Direction.CW) }, Path.Op.UNION)
        one.op(Path().apply { addRoundRect(RectF(x0 - 6.4f * q, y0 + 0.2f * q, x0 + 9.6f * q, y0 + 2.9f * q), 1.3f * q, 1.3f * q, Path.Direction.CW) }, Path.Op.UNION)
        one.op(tapered(floatArrayOf(x0 + 8f * q, y0 + 1.7f * q, x0 + 12.5f * q, y0 + 2.5f * q, x0 + 16.5f * q, y0 + 1.5f * q, x0 + 20f * q, y0 + 2.1f * q), 2.2f * q, 0.15f * q), Path.Op.UNION)
        one.op(tapered(floatArrayOf(x0 - 5.6f * q, y0 + 1.7f * q, x0 - 9f * q, y0 + 2.4f * q, x0 - 12f * q, y0 + 1.6f * q), 1.8f * q, 0.15f * q), Path.Op.UNION)
        clouds.addPath(one)
        for (k in 0 until 3) {
            val px = x0 + puffs[k * 3] * q
            val py = y0 + puffs[k * 3 + 1] * q
            val r = puffs[k * 3 + 2] * q
            curls.addArc(RectF(px - r * 0.62f, py - r * 0.62f, px + r * 0.62f, py + r * 0.62f), 150f, 270f)
            curls.addArc(RectF(px - r * 0.26f, py - r * 0.36f, px + r * 0.36f, py + r * 0.26f), 330f, 240f)
        }
    }
    for ((field, base) in listOf(rest to g, sweep to clothC)) {
        val towards = if (isDark(base)) Color.WHITE else Color.BLACK
        c.save()
        c.clipPath(field)
        c.drawPath(clouds, s.fill(mix(base, towards, 0.07f)))
        c.drawPath(curls, s.stroke(mix(base, towards, 0.16f), 0.3f * u).apply { strokeCap = Paint.Cap.ROUND })
        c.restore()
    }

    // Its colours: the scales in the book's second colour, with a paler metal for the belly, horns and claws, and a warmer or cooler one for the mane.
    val body = s.c.accent
    val edge = if (dark) mix(g, Color.BLACK, 0.74f) else mix(s.c.ink, Color.BLACK, 0.35f)
    val light = mix(body, Color.WHITE, 0.5f)
    val deep = mix(body, edge, 0.66f)
    val gold = showing(mix(body, Color.rgb(0xFF, 0xEE, 0xC6), 0.68f), edge, 4.5)
    val goldDeep = mix(gold, edge, 0.5f)
    val hsv = FloatArray(3)
    Color.colorToHSV(body, hsv)
    val mane = showing(hueShift(body, if (hsv[0] in 22f..85f) -30f else 34f, 0.04f), edge, 3.2)
    val maneLight = mix(mane, Color.WHITE, 0.45f)
    val ivory = mix(Color.WHITE, gold, 0.22f)
    val maw = mix(edge, Color.rgb(0x8E, 0x1C, 0x1C), 0.5f)

    // The dragon's back, from the tip of its tail to the back of its head: through these places, smoothly.
    val shape = DragonBody(
        floatArrayOf(
            7.5f, 0.8f, 9.5f, 0.842f, 21f, 0.86f, 39f, 0.855f, 60f, 0.832f, 78f, 0.776f, 87f, 0.672f, 75.5f, 0.59f,
            52f, 0.566f, 30f, 0.548f, 15f, 0.46f, 16f, 0.362f, 29f, 0.297f, 43f, 0.31f, 54.5f, 0.335f,
        ),
        s.h, u,
    )
    val n = shape.n
    val pt = FloatArray(2)
    val pa = FloatArray(2)
    val pb = FloatArray(2)
    val pc = FloatArray(2)
    val pd = FloatArray(2)
    val pe = FloatArray(2)

    /** The colour of a part [v] across the body at [i]: lit from above and round. */
    fun tone(base: Int, lit: Int, shade: Int, i: Int, v: Float): Int {
        val up = -v * shape.tx[i]
        val round = sqrt(max(0f, 1f - v * v))
        val sh = 0.5f * up + round - 0.72f
        return if (sh >= 0f) mix(base, lit, min(0.6f, sh * 1.15f)) else mix(base, shade, min(0.92f, -sh * 1.3f))
    }

    val line = s.stroke(edge, 0.42f * u).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    val fine = s.stroke(edge, 0.24f * u).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    /** A leg, from the place [at] along the body and [v] of the way to its belly's edge. */
    fun leg(at: Float, v: Float, fore: Boolean, far: Boolean, lean: Float) {
        val i = (at * n).toInt().coerceIn(0, n)
        val under = if (shape.belly[i] < 0f) -1f else 1f
        shape.place(i.toFloat(), v * under, pt)
        c.save()
        c.translate(pt[0], pt[1])
        c.rotate(Math.toDegrees(atan2(shape.ty[i].toDouble(), shape.tx[i].toDouble())).toFloat())
        if (under < 0f) c.scale(1f, -1f)
        c.rotate(lean)
        val k = u * (if (far) 1.08f else 1.2f)
        c.scale(k, k)
        val skin = if (far) mix(body, edge, 0.34f) else body
        val horn = if (far) mix(ivory, edge, 0.4f) else ivory
        val tuft = if (far) mix(mane, edge, 0.4f) else mane
        val l1 = s.stroke(edge, 0.36f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        val l2 = s.stroke(edge, 0.2f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        val j1x = if (fore) -3.2f else 3.4f
        val j1y = if (fore) 5f else 4.6f
        val j2x = if (fore) 2.6f else -1.8f
        val j2y = if (fore) 8.8f else 8.6f
        val pawX = if (fore) 4f else -0.4f
        val pawY = if (fore) 10f else 10.1f
        // Hair at the elbow or the hock, streaming back.
        for ((dy, bend) in listOf(-0.7f to 0.16f, 0.5f to -0.12f)) {
            val hair = Path()
            if (fore) flame(hair, j1x - 0.4f, j1y + dy, j1x - 5f, j1y + dy * 2.6f, 1.7f, bend) else flame(hair, j2x - 0.2f, j2y + dy - 0.2f, j2x - 4.6f, j2y + dy * 2.4f - 0.8f, 1.6f, bend)
            c.drawPath(hair, s.fill(tuft))
            c.drawPath(hair, l2)
        }
        // Four toes, each with its talon.
        for ((angle, back) in listOf(-14f to false, 20f to false, 54f to false, 152f to true)) {
            val dx = cos(Math.toRadians(angle.toDouble())).toFloat()
            val dy = sin(Math.toRadians(angle.toDouble())).toFloat()
            c.save()
            c.translate(pawX + dx * 3.3f, pawY + dy * 3.3f)
            c.rotate(angle)
            if (back) c.scale(1f, -1f)
            val talon = Path().apply { moveTo(-0.5f, -0.74f); quadTo(2f, -1.05f, 3.4f, 1.4f); quadTo(1.5f, 0.5f, -0.5f, 0.74f); close() }
            c.drawPath(talon, s.fill(horn))
            c.drawPath(talon, l2)
            c.restore()
            val toe = capsule(pawX + dx * 0.8f, pawY + dy * 0.8f, 1.02f, pawX + dx * 3.2f, pawY + dy * 3.2f, 0.74f)
            c.drawPath(toe, s.fill(mix(skin, edge, 0.14f)))
            c.drawPath(toe, l2)
            // A knuckle.
            c.drawArc(RectF(pawX + dx * 2f - 0.7f, pawY + dy * 2f - 0.7f, pawX + dx * 2f + 0.7f, pawY + dy * 2f + 0.7f), angle - 70f, 140f, false, l2)
        }
        val limb = capsule(0f, 0f, if (fore) 3.1f else 3.7f, j1x, j1y, if (fore) 2f else 2.3f)
        limb.op(capsule(j1x, j1y, if (fore) 1.85f else 2.05f, j2x, j2y, 1.3f), Path.Op.UNION)
        limb.op(Path().apply { addCircle(pawX, pawY, 1.75f, Path.Direction.CW) }, Path.Op.UNION)
        c.drawPath(limb, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, 11f, skin, mix(skin, edge, 0.26f), Shader.TileMode.CLAMP) })
        // Scales all down it, pointing the way the limb runs.
        c.save()
        c.clipPath(limb)
        val arc = s.stroke(mix(skin, edge, 0.58f), 0.17f)
        val glint = s.stroke(faint(gold, if (far) 60 else 170), 0.2f)
        val upper = Math.toDegrees(atan2(j1y.toDouble(), j1x.toDouble())).toFloat()
        val lower = Math.toDegrees(atan2((j2y - j1y).toDouble(), (j2x - j1x).toDouble())).toFloat()
        var sy = -4f
        var lattice = 0
        while (sy < 12.5f) {
            var sx = -7f + (if (lattice % 2 == 0) 0f else 0.72f)
            while (sx < 7.5f) {
                val towards = if (sy < j1y - 0.4f) upper else lower
                c.drawArc(RectF(sx - 0.86f, sy - 0.86f, sx + 0.86f, sy + 0.86f), towards - 78f, 156f, false, arc)
                c.drawArc(RectF(sx - 0.6f, sy - 0.6f, sx + 0.6f, sy + 0.6f), towards - 62f, 124f, false, glint)
                sx += 1.44f
            }
            sy += 1.02f
            lattice++
        }
        c.restore()
        // (No line round the top of the thigh, where it runs into the flank.)
        c.save()
        if (!far) c.clipRect(-40f, 1.6f, 40f, 60f)
        c.drawPath(limb, l1)
        c.restore()
        c.restore()
    }

    // The legs on its far side first, so the body lies over where they join it.
    leg(0.512f, 0.9f, fore = false, far = true, lean = 16f)
    leg(0.668f, 0.9f, fore = true, far = true, lean = -9f)

    // The tuft at the tip of its tail.
    val backX = -shape.tx[0]
    val backY = -shape.ty[0]
    val tuftAt = atan2(backY, backX)
    for ((turn, long, bend) in listOf(Triple(-0.95f, 6.2f, -0.12f), Triple(0.8f, 6f, -0.26f), Triple(-0.48f, 8.8f, -0.14f), Triple(0.38f, 8.6f, -0.24f), Triple(-0.04f, 11.4f, -0.2f))) {
        val towards = tuftAt + turn
        val lick = Path()
        flame(lick, shape.x[0] - backX * 1.4f * u, shape.y[0] - backY * 1.4f * u, shape.x[0] + cos(towards) * long * u, shape.y[0] + sin(towards) * long * u, 3f * u, bend)
        c.drawPath(lick, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(shape.x[0], shape.y[0], shape.x[0] + cos(towards) * long * u, shape.y[0] + sin(towards) * long * u, intArrayOf(mix(mane, edge, 0.25f), mane, maneLight), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP) })
        c.drawPath(lick, fine)
    }

    // The fins down its back: tall where the mane runs down its neck, small along its tail, and out of sight where it shows us its belly.
    var a = 5f * u
    var k = 0
    while (a < shape.length - 3f * u) {
        val i = shape.index(a).toInt().coerceIn(0, n)
        val hw = shape.half[i]
        val pitch = max(2f * u, 0.95f * hw)
        val seen = ((abs(shape.belly[i]) - 0.32f) / 0.36f).coerceIn(0f, 1f)
        if (seen > 0.05f) {
            val side = if (shape.belly[i] > 0f) -1f else 1f
            val f = a / shape.length
            val grow = when {
                f > 0.78f -> 1f + 0.75f * ((f - 0.78f) / 0.22f)
                f < 0.3f -> 0.5f + 0.5f * (f / 0.3f)
                else -> 1f
            }
            val tall = (0.66f * hw + 1.3f * u) * seen * grow * (if (k % 2 == 0) 1f else 0.62f)
            val ox = -shape.ty[i] * side
            val oy = shape.tx[i] * side
            val tx = shape.tx[i]
            val ty = shape.ty[i]
            shape.place(shape.index(a - pitch * 0.56f), side * 0.86f, pa)
            shape.place(shape.index(a + pitch * 0.56f), side * 0.86f, pb)
            shape.place(i.toFloat(), side, pt)
            val tipX = pt[0] + ox * tall - tx * tall * 1.05f
            val tipY = pt[1] + oy * tall - ty * tall * 1.05f
            val fin = Path().apply {
                moveTo(pb[0], pb[1])
                cubicTo(pb[0] + ox * tall * 1.05f + tx * tall * 0.1f, pb[1] + oy * tall * 1.05f + ty * tall * 0.1f, tipX + tx * tall * 0.6f + ox * tall * 0.14f, tipY + ty * tall * 0.6f + oy * tall * 0.14f, tipX, tipY)
                cubicTo(tipX + tx * tall * 0.5f - ox * tall * 0.5f, tipY + ty * tall * 0.5f - oy * tall * 0.5f, pa[0] + ox * tall * 0.4f, pa[1] + oy * tall * 0.4f, pa[0], pa[1])
                close()
            }
            c.drawPath(fin, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(pt[0], pt[1], tipX, tipY, intArrayOf(mix(mane, edge, 0.3f), mane, maneLight), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) })
            c.drawLine(pt[0] + ox * tall * 0.15f, pt[1] + oy * tall * 0.15f, pt[0] + ox * tall * 0.7f - tx * tall * 0.6f, pt[1] + oy * tall * 0.7f - ty * tall * 0.6f, s.stroke(faint(maneLight, 150), 0.2f * u).apply { strokeCap = Paint.Cap.ROUND })
            c.drawPath(fin, fine)
        }
        a += pitch
        k++
    }

    // The body: its whole shape first, with the shadow it casts.
    val outline = Path()
    for (i in 0..n) {
        shape.place(i.toFloat(), -1f, pt)
        if (i == 0) outline.moveTo(pt[0], pt[1]) else outline.lineTo(pt[0], pt[1])
    }
    for (i in n downTo 0) {
        shape.place(i.toFloat(), 1f, pt)
        outline.lineTo(pt[0], pt[1])
    }
    outline.close()
    c.drawPath(outline, s.fill(deep).apply { setShadowLayer(1.5f * u, 0.5f * u, 1.1f * u, faint(Color.BLACK, if (dark) 130 else 80)) })
    c.save()
    c.clipPath(outline)
    // Scale over scale, from the tail forward, each one lying over the roots of those behind it.
    val rows = 6
    val tall = 2f / rows * 0.64f
    val bellyHalf = 0.44f
    val scaleFill = s.fill(body)
    val scaleEdge = s.stroke(edge, 0.17f * u).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val scaleRim = s.stroke(gold, 0.24f * u).apply { strokeCap = Paint.Cap.ROUND }
    val vary = Random(art.seed + 11)
    val one = Path()
    a = 0f
    var column = 0
    while (a < shape.length + 2f * u) {
        val i0 = shape.index(a)
        val i = i0.toInt().coerceIn(0, n)
        val hw = shape.half[i]
        val pitch = max(0.8f * u, 0.4f * hw)
        if (hw > 1.1f * u) {
            val long = pitch * 1.95f
            val iBend = shape.index(a - long * 0.8f)
            val iTip = shape.index(a - long)
            val iRim0 = shape.index(a - long * 0.3f)
            val iRim1 = shape.index(a - long * 0.74f)
            val iRim2 = shape.index(a - long * 0.82f)
            var r = 0
            while (true) {
                val v = -1f + (r + (if (column % 2 == 0) 0f else 0.5f)) * (2f / rows)
                if (v > 1.06f) break
                r++
                if (abs(v - shape.belly[i]) < bellyHalf - 0.1f) continue
                shape.place(i0, v - tall, pa)
                shape.place(iBend, v - tall * 1.1f, pb)
                shape.place(iTip, v, pc)
                shape.place(iBend, v + tall * 1.1f, pd)
                shape.place(i0, v + tall, pe)
                one.rewind()
                one.moveTo(pa[0], pa[1])
                one.quadTo(pb[0], pb[1], pc[0], pc[1])
                one.quadTo(pd[0], pd[1], pe[0], pe[1])
                val shift = vary.nextFloat() - 0.5f
                val colour = tone(body, light, deep, i, v.coerceIn(-1f, 1f)).let { if (shift > 0f) mix(it, light, shift * 0.16f) else mix(it, deep, -shift * 0.16f) }
                scaleFill.color = colour
                c.drawPath(one, scaleFill)
                scaleEdge.color = mix(colour, edge, 0.62f)
                c.drawPath(one, scaleEdge)
                // A glint of the paler metal just inside its edge.
                shape.place(iRim0, v - tall * 0.62f, pa)
                shape.place(iRim1, v - tall * 0.74f, pb)
                shape.place(iRim2, v, pc)
                shape.place(iRim1, v + tall * 0.74f, pd)
                shape.place(iRim0, v + tall * 0.62f, pe)
                one.rewind()
                one.moveTo(pa[0], pa[1])
                one.quadTo(pb[0], pb[1], pc[0], pc[1])
                one.quadTo(pd[0], pd[1], pe[0], pe[1])
                val up = -v * shape.tx[i]
                scaleRim.color = faint(gold, (150 + 90 * up).toInt().coerceIn(80, 235))
                c.drawPath(one, scaleRim)
            }
        }
        a += pitch
        column++
    }
    // The plates of its belly, each lapping the one behind.
    val plateFill = s.fill(gold)
    val plateEdge = s.stroke(goldDeep, 0.2f * u).apply { strokeCap = Paint.Cap.ROUND }
    a = 0f
    while (a < shape.length) {
        val iA = shape.index(a)
        val i = iA.toInt().coerceIn(0, n)
        val hw = shape.half[i]
        val pitch = max(0.9f * u, 0.52f * hw)
        val iB = shape.index(a + pitch * 1.05f)
        val j = iB.toInt().coerceIn(0, n)
        val a1 = max(-1.06f, shape.belly[i] - bellyHalf)
        val a2 = min(1.06f, shape.belly[i] + bellyHalf)
        val b1 = max(-1.06f, shape.belly[j] - bellyHalf)
        val b2 = min(1.06f, shape.belly[j] + bellyHalf)
        shape.place(iA, a1, pa)
        shape.place(shape.index(a - pitch * 0.42f), (a1 + a2) / 2, pb)
        shape.place(iA, a2, pc)
        shape.place(iB, b2, pd)
        shape.place(iB, b1, pe)
        shape.place(shape.index(a + pitch * 0.63f), (b1 + b2) / 2, pt)
        one.rewind()
        one.moveTo(pa[0], pa[1])
        one.quadTo(pb[0], pb[1], pc[0], pc[1])
        one.lineTo(pd[0], pd[1])
        one.quadTo(pt[0], pt[1], pe[0], pe[1])
        one.close()
        plateFill.color = tone(gold, Color.WHITE, goldDeep, i, ((a1 + a2) / 2).coerceIn(-1f, 1f))
        c.drawPath(one, plateFill)
        one.rewind()
        one.moveTo(pa[0], pa[1])
        one.quadTo(pb[0], pb[1], pc[0], pc[1])
        c.drawPath(one, plateEdge)
        a += pitch
    }
    // Where the belly meets the scales.
    val seam = Path()
    for (sideOf in listOf(-1f, 1f)) {
        var open = false
        for (i in 0..n step 2) {
            val v = shape.belly[i] + sideOf * bellyHalf
            if (abs(v) >= 0.98f) {
                open = false
                continue
            }
            shape.place(i.toFloat(), v, pt)
            if (open) seam.lineTo(pt[0], pt[1]) else seam.moveTo(pt[0], pt[1])
            open = true
        }
    }
    c.drawPath(seam, s.stroke(mix(goldDeep, edge, 0.4f), 0.26f * u).apply { strokeJoin = Paint.Join.ROUND })
    c.restore()
    c.drawPath(outline, line)

    // The legs on its near side.
    leg(0.54f, 0.3f, fore = false, far = false, lean = -4f)
    leg(0.646f, 0.3f, fore = true, far = false, lean = 6f)

    // Its head, turned to the pearl.
    c.save()
    c.translate(shape.x[n], shape.y[n])
    c.rotate(Math.toDegrees(atan2(shape.ty[n].toDouble(), shape.tx[n].toDouble())).toFloat())
    if (shape.belly[n] < 0f) c.scale(1f, -1f)
    c.scale(1.28f * u, 1.28f * u)
    val h1 = s.stroke(edge, 0.38f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    val h2 = s.stroke(edge, 0.2f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    fun antler(dx: Float, dy: Float, colour: Int) {
        val beam = tapered(floatArrayOf(1.6f + dx, -2.6f + dy, 0.6f + dx, -4.4f + dy, -2.4f + dx, -7.2f + dy, -6.2f + dx, -8.5f + dy, -10.6f + dx, -9.6f + dy), 1.9f, 0.18f)
        beam.op(tapered(floatArrayOf(-2.6f + dx, -7.2f + dy, -2.9f + dx, -9.2f + dy, -1.9f + dx, -11f + dy), 1.15f, 0.12f), Path.Op.UNION)
        beam.op(tapered(floatArrayOf(-6.2f + dx, -8.5f + dy, -7.6f + dx, -9.9f + dy, -8.2f + dx, -11.2f + dy), 0.95f, 0.1f), Path.Op.UNION)
        c.drawPath(beam, s.fill(colour))
        c.drawPath(beam, h2)
    }
    antler(1.8f, 0.5f, mix(gold, edge, 0.42f))
    // Its mane, streaming back along its neck.
    for ((lock, bend) in listOf(
        floatArrayOf(0.4f, 4.7f, -5.6f, 9.8f, 2.3f) to -0.12f,
        floatArrayOf(-1.7f, 2.7f, -9.8f, 6.6f, 2.5f) to 0.1f,
        floatArrayOf(-2.2f, 0.3f, -11.4f, 2.5f, 2.5f) to -0.08f,
        floatArrayOf(-2.2f, -2.2f, -13.2f, -2.4f, 2.6f) to 0.08f,
        floatArrayOf(-1.7f, -4.1f, -11.6f, -7.2f, 2.6f) to -0.1f,
    )) {
        val lockPath = Path()
        flame(lockPath, lock[0], lock[1], lock[2], lock[3], lock[4], bend)
        c.drawPath(lockPath, s.fill(mane))
        c.drawLine(lock[0] + (lock[2] - lock[0]) * 0.12f, lock[1] + (lock[3] - lock[1]) * 0.12f, lock[0] + (lock[2] - lock[0]) * 0.62f, lock[1] + (lock[3] - lock[1]) * 0.62f, s.stroke(faint(maneLight, 190), 0.22f).apply { strokeCap = Paint.Cap.ROUND })
        c.drawPath(lockPath, h2)
    }
    // Its beard, under the jaw.
    val beard = Path()
    flame(beard, 11.2f, 6.9f, 9.4f, 10.6f, 1.5f, 0.12f)
    flame(beard, 8.8f, 6.8f, 6.4f, 11f, 1.6f, 0.12f)
    flame(beard, 6.3f, 6.5f, 3.4f, 10.4f, 1.6f, 0.12f)
    flame(beard, 3.9f, 6f, 0.9f, 9.2f, 1.5f, 0.12f)
    c.drawPath(beard, s.fill(mane))
    c.drawPath(beard, h2)
    // The mouth, open: dark within, with its tongue.
    c.drawPath(Path().apply { moveTo(4.2f, 1.1f); lineTo(15.6f, -0.5f); lineTo(14.7f, 5.9f); lineTo(12.4f, 5f); lineTo(4.4f, 1.9f); close() }, s.fill(maw))
    val tongue = Path().apply {
        moveTo(6.2f, 2.5f)
        cubicTo(9.4f, 3.5f, 12.2f, 3.7f, 14f, 2.7f)
        cubicTo(14.9f, 2.2f, 15.7f, 2.4f, 15.9f, 3.1f)
        cubicTo(15.1f, 3f, 14.6f, 3.3f, 13.8f, 3.7f)
        cubicTo(11.6f, 4.6f, 8.6f, 4.3f, 6.2f, 3.5f)
        close()
    }
    c.drawPath(tongue, s.fill(mix(Color.rgb(0xD8, 0x4A, 0x3C), body, 0.18f)))
    c.drawPath(tongue, h2)
    val jaw = Path().apply {
        moveTo(-1.6f, 3.4f)
        cubicTo(0.8f, 3.2f, 3f, 2.6f, 4.6f, 1.6f)
        cubicTo(6.6f, 2.7f, 9.6f, 4.2f, 12.4f, 5f)
        cubicTo(13.4f, 5f, 14.3f, 5.2f, 14.7f, 5.9f)
        cubicTo(14.7f, 6.9f, 13.4f, 7.4f, 12f, 7.2f)
        cubicTo(8.6f, 7f, 4.6f, 6.6f, 1.4f, 5.6f)
        cubicTo(0f, 5.2f, -1f, 4.6f, -1.6f, 3.4f)
        close()
    }
    c.drawPath(jaw, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 2f, 0f, 7.4f, mix(body, edge, 0.12f), mix(body, edge, 0.5f), Shader.TileMode.CLAMP) })
    c.drawPath(Path().apply { moveTo(5.4f, 2.5f); cubicTo(7.6f, 3.6f, 10f, 4.7f, 12.4f, 5.4f); cubicTo(13.2f, 5.5f, 13.9f, 5.7f, 14.3f, 6.1f) }, s.stroke(faint(gold, 200), 0.3f).apply { strokeCap = Paint.Cap.ROUND })
    c.drawPath(jaw, h1)
    antler(0f, 0f, gold)
    val skull = Path().apply {
        moveTo(-2.4f, -3.9f)
        cubicTo(-1f, -5.6f, 2f, -6f, 3.9f, -5.2f)
        cubicTo(5f, -5.6f, 6.4f, -5.2f, 7f, -4.2f)
        cubicTo(8.4f, -3.5f, 10.2f, -3.5f, 11.8f, -3.7f)
        cubicTo(12.9f, -4.9f, 15f, -4.9f, 15.9f, -3.5f)
        cubicTo(16.5f, -2.6f, 16.5f, -1.3f, 15.7f, -0.5f)
        cubicTo(14.6f, 0.2f, 13.2f, -0.2f, 12.2f, 0.4f)
        cubicTo(10.8f, 1.2f, 9f, 0.3f, 7.6f, 0.9f)
        cubicTo(6.6f, 1.3f, 5.6f, 1.5f, 4.8f, 1.3f)
        lineTo(3.4f, 2.6f)
        cubicTo(1.6f, 3.6f, -0.6f, 3.9f, -2.4f, 3.7f)
        cubicTo(-3.6f, 1.6f, -3.6f, -1.8f, -2.4f, -3.9f)
        close()
    }
    c.drawPath(skull, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, -6f, 0f, 3.6f, intArrayOf(mix(body, light, 0.45f), body, mix(body, edge, 0.3f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) })
    // Scales on its cheek, and the lines of its snout and lip.
    c.save()
    c.clipPath(skull)
    val cheek = s.stroke(mix(body, edge, 0.6f), 0.18f)
    val cheekGlint = s.stroke(faint(gold, 150), 0.18f)
    for ((sx, sy) in listOf(-1.2f to -2.6f, -1.2f to -0.6f, -1.2f to 1.4f, 0.4f to -3.6f, 0.4f to -1.6f, 0.4f to 0.4f, 0.4f to 2.4f, 2f to -0.6f, 2f to 1.4f, 3.4f to 0.4f)) {
        c.drawArc(RectF(sx - 1.15f, sy - 1.15f, sx + 1.15f, sy + 1.15f), 105f, 150f, false, cheek)
        c.drawArc(RectF(sx - 0.85f, sy - 0.85f, sx + 0.85f, sy + 0.85f), 120f, 120f, false, cheekGlint)
    }
    c.restore()
    c.drawPath(Path().apply { moveTo(8.6f, -2.3f); quadTo(11.2f, -2.9f, 13.4f, -2f) }, h2)
    c.drawPath(Path().apply { moveTo(7.6f, -1.5f); cubicTo(5.8f, -0.8f, 4.3f, -0.5f, 3.3f, 0.5f); cubicTo(2.7f, 1.3f, 2.9f, 2.2f, 3.4f, 2.6f) }, h2)
    c.drawPath(Path().apply { moveTo(12.7f, -3.1f); quadTo(13.3f, -2.3f, 12.9f, -1.5f); moveTo(11.6f, -3f); quadTo(12.1f, -2.3f, 11.8f, -1.6f) }, h2)
    c.drawPath(Path().apply { moveTo(5.2f, 0.6f); cubicTo(7.4f, -0.3f, 10.6f, 0.3f, 12.4f, -0.6f); cubicTo(13.6f, -1.1f, 14.8f, -1.2f, 15.6f, -1f) }, s.stroke(faint(gold, 200), 0.3f).apply { strokeCap = Paint.Cap.ROUND })
    c.drawPath(skull, h1)
    // Teeth: a long fang above and below, and lesser ones behind.
    val teeth = Path()
    for ((x, y, long) in listOf(Triple(13.7f, -0.2f, 2.3f), Triple(11.7f, 0.6f, 1.1f), Triple(10.1f, 0.6f, 0.95f), Triple(8.5f, 0.5f, 0.85f), Triple(7f, 1f, 0.7f))) {
        teeth.moveTo(x - 0.55f, y)
        teeth.quadTo(x - 0.25f, y + long * 0.6f, x - 0.1f, y + long)
        teeth.quadTo(x + 0.35f, y + long * 0.45f, x + 0.55f, y - 0.1f)
        teeth.close()
    }
    for ((x, y, long) in listOf(Triple(12.3f, 5.1f, 2f), Triple(10.5f, 4.6f, 1f), Triple(8.9f, 4f, 0.9f), Triple(7.3f, 3.3f, 0.8f))) {
        teeth.moveTo(x - 0.5f, y - 0.15f)
        teeth.quadTo(x - 0.2f, y - long * 0.6f, x + 0.1f, y - long)
        teeth.quadTo(x + 0.4f, y - long * 0.4f, x + 0.55f, y + 0.15f)
        teeth.close()
    }
    c.drawPath(teeth, s.fill(ivory))
    c.drawPath(teeth, s.stroke(edge, 0.14f).apply { strokeJoin = Paint.Join.ROUND })
    // A ruff of mane over the back of its head, where the neck begins.
    val ruff = Path()
    flame(ruff, -1.4f, 2.4f, -6.6f, 4.6f, 2.5f, 0.12f)
    flame(ruff, -2f, -0.1f, -8f, 0.3f, 2.6f, -0.1f)
    flame(ruff, -1.6f, -2.6f, -7.2f, -4.2f, 2.5f, 0.12f)
    c.drawPath(ruff, s.fill(mane))
    c.drawPath(ruff, s.stroke(faint(maneLight, 170), 0.2f).apply { strokeCap = Paint.Cap.ROUND; pathEffect = null })
    c.drawPath(ruff, h2)
    // Its ear.
    val ear = Path()
    flame(ear, 0.1f, -4f, -4.4f, -6.2f, 1.9f, 0.12f)
    c.drawPath(ear, s.fill(mix(body, edge, 0.3f)))
    c.drawPath(ear, h2)
    // The eye, narrow under a heavy brow.
    val eye = Path().apply { moveTo(3.9f, -2.5f); quadTo(5.6f, -4.5f, 7.6f, -2.9f); quadTo(5.8f, -1.7f, 3.9f, -2.5f); close() }
    c.drawPath(eye, s.fill(ivory))
    c.save()
    c.clipPath(eye)
    c.drawCircle(5.8f, -2.9f, 0.95f, s.fill(mix(mane, Color.rgb(0xFF, 0xC2, 0x30), 0.6f)))
    c.drawOval(RectF(5.52f, -3.75f, 6.08f, -2.05f), s.fill(edge))
    c.drawCircle(5.4f, -3.3f, 0.2f, s.fill(Color.WHITE))
    c.restore()
    c.drawPath(eye, h2)
    val brow = Path().apply {
        moveTo(8.2f, -3.7f)
        cubicTo(6.8f, -5.5f, 4.6f, -5.8f, 2.8f, -5.1f)
        cubicTo(1.6f, -5.9f, 0.2f, -6.6f, -1.8f, -6.5f)
        cubicTo(-0.2f, -5.7f, 0.6f, -4.9f, 1.4f, -4.2f)
        cubicTo(3.4f, -4.5f, 5.8f, -4.5f, 8.2f, -3.7f)
        close()
    }
    c.drawPath(brow, s.fill(mane))
    c.drawPath(brow, h2)
    // The nostril.
    c.drawPath(Path().apply { moveTo(14f, -3.1f); cubicTo(14.9f, -4f, 15.9f, -3.1f, 15.2f, -2.3f); cubicTo(15.1f, -2.8f, 14.6f, -3f, 14f, -3.1f); close() }, s.fill(edge))
    // The pearl it guards, with fire about it.
    val pearlX = 22.6f
    val pearlY = 3.4f
    val pearlR = 3f
    c.drawCircle(pearlX, pearlY, pearlR * 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(pearlX, pearlY, pearlR * 3.2f, intArrayOf(faint(gold, 120), faint(gold, 40), faint(gold, 0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) })
    val fire = Path()
    for ((angle, long, bend) in listOf(Triple(-18f, 4.2f, -0.22f), Triple(-62f, 5f, -0.22f), Triple(-108f, 3.8f, -0.22f), Triple(30f, 3.6f, -0.22f))) {
        val dx = cos(Math.toRadians(angle.toDouble())).toFloat()
        val dy = sin(Math.toRadians(angle.toDouble())).toFloat()
        flame(fire, pearlX + dx * pearlR * 0.5f, pearlY + dy * pearlR * 0.5f, pearlX + dx * (pearlR + long), pearlY + dy * (pearlR + long), 2.6f, bend)
    }
    c.drawPath(fire, s.fill(mane))
    c.drawPath(fire, h2)
    c.drawCircle(pearlX, pearlY, pearlR, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(pearlX - 1f, pearlY - 1.2f, pearlR * 1.5f, intArrayOf(Color.WHITE, gold, mix(gold, mane, 0.55f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) })
    c.drawOval(RectF(pearlX - 1.9f, pearlY - 2.1f, pearlX - 0.5f, pearlY - 1.1f), s.fill(faint(Color.WHITE, 210)))
    c.drawCircle(pearlX, pearlY, pearlR, h1)
    // Its whiskers, one under the pearl and one over it.
    val under = tapered(floatArrayOf(15.4f, -0.9f, 17.6f, 2.4f, 19.3f, 6.6f, 22.7f, 8.6f, 26.4f, 7.7f, 27.6f, 5.4f), 0.66f, 0.1f)
    val over = tapered(floatArrayOf(15f, -2.5f, 17f, -5.2f, 20.6f, -6.4f, 24.3f, -5.5f, 26.5f, -3.2f, 26.2f, -1.5f), 0.6f, 0.08f)
    for (whisker in listOf(over, under)) {
        c.drawPath(whisker, s.fill(gold))
        c.drawPath(whisker, s.stroke(edge, 0.14f).apply { strokeJoin = Paint.Join.ROUND })
    }
    c.restore()
    c.restore()
    Painter(c, whole(s), rnd).grain(0.8f)
    val start = if (flip) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
    val head = if (flip) RectF(30f * u, 8f * u, 92f * u, 0.2f * s.h) else RectF(8f * u, 8f * u, 70f * u, 0.2f * s.h)
    titleIn(
        s, t, head, roman, 8.4f, clothC, body, upper = true, tracking = 0.06f, pitch = 1.14f, place = -1, align = start, smallest = 2.5f,
        seriesFace = sans, seriesSize = 2.6f, seriesTracking = 0.18f, gap = 2.6f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 12.5f * u, 90f * u, s.h - 4.5f * u), serifBold, 4.6f, showing(body, clothC, 3.0), upper = false, tracking = 0f, align = if (flip) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE)
}
