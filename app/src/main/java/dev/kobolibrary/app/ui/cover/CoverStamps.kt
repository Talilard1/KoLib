package dev.kobolibrary.app.ui.cover

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Line drawings for the newer genres, stamped in gold on cloth (see CoverArt.stamp): [line] draws the
 * strokes and [solid] fills, both in the foil.
 */
internal object Stamps {

    private fun thin(line: Paint, f: Float = 0.5f) = Paint(line).apply { strokeWidth = line.strokeWidth * f }

    /** A four-pointed twinkle. */
    private fun twinkle(p: Painter, cx: Float, cy: Float, size: Float, solid: Paint) {
        p.c.drawPath(Path().apply {
            moveTo(cx, cy - size); quadTo(cx, cy, cx + size, cy); quadTo(cx, cy, cx, cy + size); quadTo(cx, cy, cx - size, cy); quadTo(cx, cy, cx, cy - size); close()
        }, solid)
    }

    private fun twinkles(p: Painter, n: Int, solid: Paint, top: Float = 0.04f, bottom: Float = 0.5f) {
        repeat(n) { twinkle(p, p.x(0.08f + p.rnd.nextFloat() * 0.84f), p.y(top + p.rnd.nextFloat() * (bottom - top)), p.u * (1.2f + p.rnd.nextFloat() * 1.6f), solid) }
    }

    private fun crescent(p: Painter, cx: Float, cy: Float, r: Float, solid: Paint) {
        val moon = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            op(Path().apply { addCircle(cx + r * 0.45f, cy - r * 0.15f, r * 0.88f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        p.c.drawPath(moon, solid)
    }

    private fun heart(cx: Float, cy: Float, s: Float): Path = Path().apply {
        moveTo(cx, cy + s * 0.9f)
        cubicTo(cx - s * 1.4f, cy + s * 0.1f, cx - s * 1.0f, cy - s * 0.95f, cx, cy - s * 0.35f)
        cubicTo(cx + s * 1.0f, cy - s * 0.95f, cx + s * 1.4f, cy + s * 0.1f, cx, cy + s * 0.9f)
        close()
    }

    /** An owl perched on a pile of books, a wand across them giving off sparks. */
    fun owlOnBooks(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        // The books.
        val bw = p.w * 0.5f
        var by = p.y(0.92f)
        for (k in 0 until 3) {
            val off = (k - 1) * p.w * 0.03f
            val r = RectF(cx - bw / 2 + off, by - p.h * 0.09f, cx + bw / 2 + off, by)
            p.c.drawRoundRect(r, p.u, p.u, line)
            p.c.drawLine(r.left + p.w * 0.04f, r.top + p.h * 0.03f, r.left + p.w * 0.04f, r.bottom - p.h * 0.03f, thin(line))
            by -= p.h * 0.09f
        }
        // The owl.
        val oy = by
        val body = Path().apply {
            moveTo(cx - p.w * 0.13f, oy)
            cubicTo(cx - p.w * 0.17f, oy - p.h * 0.22f, cx - p.w * 0.12f, oy - p.h * 0.36f, cx - p.w * 0.1f, oy - p.h * 0.42f)
            lineTo(cx - p.w * 0.06f, oy - p.h * 0.37f)
            quadTo(cx, oy - p.h * 0.39f, cx + p.w * 0.06f, oy - p.h * 0.37f)
            lineTo(cx + p.w * 0.1f, oy - p.h * 0.42f)
            cubicTo(cx + p.w * 0.12f, oy - p.h * 0.36f, cx + p.w * 0.17f, oy - p.h * 0.22f, cx + p.w * 0.13f, oy)
            close()
        }
        p.c.drawPath(body, line)
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * p.w * 0.05f
            val ey = oy - p.h * 0.3f
            p.c.drawCircle(ex, ey, p.w * 0.04f, line)
            p.c.drawCircle(ex, ey, p.w * 0.015f, solid)
            p.c.drawPath(Path().apply { moveTo(cx + side * p.w * 0.11f, oy - p.h * 0.2f); quadTo(cx + side * p.w * 0.06f, oy - p.h * 0.1f, cx + side * p.w * 0.08f, oy - p.h * 0.02f) }, thin(line))
        }
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.012f, oy - p.h * 0.26f); lineTo(cx, oy - p.h * 0.23f); lineTo(cx + p.w * 0.012f, oy - p.h * 0.26f); close() }, solid)
        for (k in 0 until 3) p.c.drawArc(RectF(cx - p.w * 0.04f, oy - p.h * (0.17f - k * 0.04f), cx + p.w * 0.04f, oy - p.h * (0.13f - k * 0.04f)), 20f, 140f, false, thin(line))
        // The wand, and its sparks.
        val wx0 = p.x(0.2f)
        val wy0 = p.y(0.98f)
        val wx1 = p.x(0.85f)
        val wy1 = p.y(0.58f)
        p.c.drawLine(wx0, wy0, wx1, wy1, line)
        twinkle(p, wx1 + p.u * 2, wy1 - p.u * 2, p.u * 3f, solid)
        twinkles(p, 7, solid, 0.04f, 0.45f)
    }

    /** A serpent swallowing its tail round a crescent moon and stars. */
    fun ouroboros(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val rad = min(p.w, p.h) * 0.36f
        val body = rad * 0.13f
        // The body as a thick ring with scales across it.
        p.c.drawCircle(cx, cy, rad + body / 2, line)
        p.c.drawCircle(cx, cy, rad - body / 2, line)
        for (k in 0 until 40) {
            val a = 2f * PI.toFloat() * k / 40
            if (a > 4.4f && a < 5.2f) continue
            p.c.drawArc(RectF(cx + cos(a) * rad - body * 0.5f, cy + sin(a) * rad - body * 0.5f, cx + cos(a) * rad + body * 0.5f, cy + sin(a) * rad + body * 0.5f), a * 180f / PI.toFloat() + 90f, 180f, false, thin(line, 0.4f))
        }
        // The head at the top, jaws round the tail.
        val ha = -PI.toFloat() / 2 + 0.25f
        val hx = cx + cos(ha) * rad
        val hy = cy + sin(ha) * rad
        p.c.drawPath(Path().apply {
            moveTo(hx - body * 0.4f, hy + body * 0.8f); quadTo(hx + body * 1.6f, hy + body * 0.6f, hx + body * 2.2f, hy - body * 0.1f)
            quadTo(hx + body * 1.4f, hy - body * 1.2f, hx - body * 0.6f, hy - body * 0.8f); close()
        }, solid)
        // Inside: the moon and stars.
        crescent(p, cx - rad * 0.08f, cy, rad * 0.4f, solid)
        twinkle(p, cx + rad * 0.4f, cy - rad * 0.3f, p.u * 2.2f, solid)
        twinkle(p, cx + rad * 0.35f, cy + rad * 0.35f, p.u * 1.6f, solid)
        twinkle(p, cx - rad * 0.45f, cy + rad * 0.45f, p.u * 1.3f, solid)
    }

    /** A cauldron on its fire, bubbling, steam curling up to a crescent moon. */
    fun cauldron(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.6f)
        val r = p.w * 0.24f
        // A round belly under a lip.
        p.c.drawArc(RectF(cx - r, cy - r * 0.75f, cx + r, cy + r * 1.05f), -20f, 220f, false, line)
        p.c.drawOval(cx - r * 0.95f, cy - r * 0.38f, cx + r * 0.95f, cy - r * 0.08f, line)
        p.c.drawArc(RectF(cx - r * 0.7f, cy - r * 0.2f, cx + r * 0.7f, cy + r * 0.85f), 30f, 120f, false, thin(line, 0.5f))
        for (side in listOf(-1f, 1f)) p.c.drawRoundRect(cx + side * r * 0.55f - r * 0.08f, cy + r * 0.88f, cx + side * r * 0.55f + r * 0.08f, cy + r * 1.12f, r * 0.05f, r * 0.05f, solid)
        // The fire under it.
        for (k in -1..1) {
            val fx = cx + k * r * 0.3f
            p.c.drawPath(Path().apply { moveTo(fx - r * 0.12f, cy + r * 1.4f); quadTo(fx - r * 0.12f, cy + r * 1.2f, fx, cy + r * 1.08f); quadTo(fx + r * 0.12f, cy + r * 1.2f, fx + r * 0.12f, cy + r * 1.4f); close() }, solid)
        }
        p.c.drawLine(cx - r * 0.7f, cy + r * 1.42f, cx + r * 0.7f, cy + r * 1.42f, line)
        // Bubbles and steam.
        repeat(5) { p.c.drawCircle(cx + (p.rnd.nextFloat() - 0.5f) * r * 1.4f, cy - r * (0.25f + p.rnd.nextFloat() * 0.3f), p.u * (0.8f + p.rnd.nextFloat() * 1.2f), thin(line, 0.5f)) }
        for (k in 0 until 3) {
            val sx = cx + (k - 1) * r * 0.4f
            p.c.drawPath(Path().apply { moveTo(sx, cy - r * 0.3f); cubicTo(sx - r * 0.3f, cy - r * 0.7f, sx + r * 0.3f, cy - r * 0.9f, sx, cy - r * 1.3f) }, thin(line, 0.6f))
        }
        crescent(p, p.x(0.72f), p.y(0.14f), p.w * 0.08f, solid)
        twinkles(p, 5, solid, 0.04f, 0.3f)
    }

    /** A sword standing in a stone, light about it. */
    fun swordInStone(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val stoneTop = p.y(0.66f)
        p.c.drawPath(Path().apply {
            moveTo(p.x(0.22f), p.y(0.95f)); lineTo(p.x(0.28f), p.y(0.74f)); lineTo(p.x(0.4f), stoneTop); lineTo(p.x(0.62f), p.y(0.67f))
            lineTo(p.x(0.74f), p.y(0.76f)); lineTo(p.x(0.8f), p.y(0.95f)); close()
        }, line)
        p.c.drawLine(p.x(0.32f), p.y(0.82f), p.x(0.46f), p.y(0.86f), thin(line))
        p.c.drawLine(p.x(0.56f), p.y(0.8f), p.x(0.7f), p.y(0.84f), thin(line))
        // The blade, the guard, the grip and the pommel.
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.025f, stoneTop + p.u); lineTo(cx - p.w * 0.025f, p.y(0.3f)); lineTo(cx + p.w * 0.025f, p.y(0.3f)); lineTo(cx + p.w * 0.025f, stoneTop + p.u) }, line)
        p.c.drawLine(cx, p.y(0.32f), cx, stoneTop, thin(line, 0.4f))
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.14f, p.y(0.29f)); quadTo(cx, p.y(0.31f), cx + p.w * 0.14f, p.y(0.29f)); lineTo(cx + p.w * 0.14f, p.y(0.27f)); quadTo(cx, p.y(0.29f), cx - p.w * 0.14f, p.y(0.27f)); close() }, solid)
        p.c.drawRect(cx - p.w * 0.015f, p.y(0.15f), cx + p.w * 0.015f, p.y(0.27f), line)
        p.c.drawCircle(cx, p.y(0.12f), p.w * 0.03f, solid)
        // Rays of light.
        for (k in 0 until 14) {
            val a = PI.toFloat() * (1.05f + k * 0.065f)
            val r0 = p.w * 0.18f
            val r1 = p.w * (0.26f + (k % 2) * 0.06f)
            p.c.drawLine(cx + cos(a) * r0, p.y(0.3f) + sin(a) * r0, cx + cos(a) * r1, p.y(0.3f) + sin(a) * r1, thin(line, 0.4f))
        }
    }

    /** A storm cloud whose lightning strikes a cut gem, which shines. */
    fun stormAndGem(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        // The cloud.
        val cy = p.y(0.2f)
        val cloud = Path().apply {
            moveTo(cx - p.w * 0.34f, cy + p.h * 0.08f)
            cubicTo(cx - p.w * 0.42f, cy, cx - p.w * 0.3f, cy - p.h * 0.1f, cx - p.w * 0.18f, cy - p.h * 0.06f)
            cubicTo(cx - p.w * 0.14f, cy - p.h * 0.16f, cx + p.w * 0.06f, cy - p.h * 0.18f, cx + p.w * 0.1f, cy - p.h * 0.07f)
            cubicTo(cx + p.w * 0.22f, cy - p.h * 0.14f, cx + p.w * 0.38f, cy - p.h * 0.06f, cx + p.w * 0.34f, cy + p.h * 0.08f)
            close()
        }
        p.c.drawPath(cloud, line)
        for (k in 0 until 3) p.c.drawLine(cx - p.w * (0.24f - k * 0.18f), cy + p.h * 0.11f, cx - p.w * (0.28f - k * 0.18f), cy + p.h * 0.16f, thin(line, 0.5f))
        // The lightning.
        p.c.drawPath(Path().apply {
            moveTo(cx + p.w * 0.02f, cy + p.h * 0.09f); lineTo(cx - p.w * 0.06f, p.y(0.42f)); lineTo(cx + p.w * 0.02f, p.y(0.42f)); lineTo(cx - p.w * 0.04f, p.y(0.56f))
            lineTo(cx + p.w * 0.08f, p.y(0.38f)); lineTo(cx, p.y(0.38f)); lineTo(cx + p.w * 0.07f, cy + p.h * 0.09f); close()
        }, solid)
        // The gem, cut, with its facets.
        val gy = p.y(0.74f)
        val gw = p.w * 0.22f
        val top = gy - p.h * 0.08f
        p.c.drawPath(Path().apply { moveTo(cx - gw, top); lineTo(cx - gw * 0.6f, top - p.h * 0.06f); lineTo(cx + gw * 0.6f, top - p.h * 0.06f); lineTo(cx + gw, top); lineTo(cx, gy + p.h * 0.16f); close() }, line)
        p.c.drawLine(cx - gw, top, cx + gw, top, thin(line))
        for (f in listOf(-0.6f, -0.2f, 0.2f, 0.6f)) {
            p.c.drawLine(cx + gw * f, top - p.h * 0.06f, cx + gw * f * 0.6f, top, thin(line, 0.5f))
            p.c.drawLine(cx + gw * f * 0.6f, top, cx, gy + p.h * 0.16f, thin(line, 0.5f))
        }
        for (k in 0 until 10) {
            val a = 2f * PI.toFloat() * k / 10
            p.c.drawLine(cx + cos(a) * gw * 1.3f, gy + sin(a) * gw * 1.1f, cx + cos(a) * gw * 1.6f, gy + sin(a) * gw * 1.35f, thin(line, 0.4f))
        }
    }

    /** A trident rising from the waves. */
    fun trident(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        p.c.drawLine(cx, p.y(0.25f), cx, p.y(0.86f), line)
        val tines = Path().apply {
            moveTo(cx - p.w * 0.16f, p.y(0.1f)); lineTo(cx - p.w * 0.16f, p.y(0.25f)); quadTo(cx - p.w * 0.16f, p.y(0.31f), cx, p.y(0.31f))
            quadTo(cx + p.w * 0.16f, p.y(0.31f), cx + p.w * 0.16f, p.y(0.25f)); lineTo(cx + p.w * 0.16f, p.y(0.1f))
            moveTo(cx, p.y(0.06f)); lineTo(cx, p.y(0.3f))
        }
        p.c.drawPath(tines, line)
        for (tx in listOf(-0.16f, 0f, 0.16f)) {
            val x0 = cx + p.w * tx
            val top = if (tx == 0f) p.y(0.03f) else p.y(0.07f)
            p.c.drawPath(Path().apply { moveTo(x0, top); lineTo(x0 - p.w * 0.025f, top + p.h * 0.06f); lineTo(x0 + p.w * 0.025f, top + p.h * 0.06f); close() }, solid)
        }
        p.c.drawRect(cx - p.w * 0.03f, p.y(0.34f), cx + p.w * 0.03f, p.y(0.37f), solid)
        // Waves.
        for (row in 0 until 3) {
            val wy = p.y(0.76f + row * 0.08f)
            val path = Path()
            var wx = p.x(0.06f + row * 0.04f)
            path.moveTo(wx, wy)
            while (wx < p.x(0.92f - row * 0.04f)) {
                path.cubicTo(wx + p.w * 0.04f, wy - p.h * 0.06f, wx + p.w * 0.1f, wy - p.h * 0.05f, wx + p.w * 0.1f, wy)
                wx += p.w * 0.1f
            }
            p.c.drawPath(path, if (row == 0) line else thin(line, 0.7f))
        }
    }

    /** A tall tower with a rose briar climbing it, and a crescent moon. */
    fun roseTower(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val tw = p.w * 0.12f
        p.c.drawPath(Path().apply {
            moveTo(cx - tw, p.y(0.95f)); lineTo(cx - tw * 0.8f, p.y(0.28f)); lineTo(cx + tw * 0.8f, p.y(0.28f)); lineTo(cx + tw, p.y(0.95f))
            moveTo(cx - tw * 1.2f, p.y(0.28f)); lineTo(cx, p.y(0.08f)); lineTo(cx + tw * 1.2f, p.y(0.28f)); close()
        }, line)
        p.c.drawRoundRect(cx - tw * 0.25f, p.y(0.34f), cx + tw * 0.25f, p.y(0.44f), tw * 0.25f, tw * 0.25f, solid)
        // The briar winding up it, with roses.
        val vine = Path().apply {
            moveTo(cx - tw * 1.6f, p.y(0.98f))
            cubicTo(cx + tw * 1.8f, p.y(0.85f), cx - tw * 1.8f, p.y(0.7f), cx + tw * 1.4f, p.y(0.58f))
            cubicTo(cx + tw * 2.0f, p.y(0.55f), cx - tw * 1.2f, p.y(0.45f), cx - tw * 1.0f, p.y(0.36f))
        }
        p.c.drawPath(vine, thin(line, 0.7f))
        for ((fx, fy) in listOf(-1.2f to 0.92f, 1.1f to 0.78f, -1.0f to 0.66f, 1.3f to 0.57f, -0.9f to 0.4f)) {
            val rx = cx + tw * fx
            val ry = p.y(fy)
            p.c.drawCircle(rx, ry, p.u * 3f, solid)
            p.c.drawArc(RectF(rx - p.u * 1.8f, ry - p.u * 1.8f, rx + p.u * 1.8f, ry + p.u * 1.8f), 0f, 270f, false, thin(Paint(line).apply { shader = null; color = 0x66000000 }, 0.4f))
        }
        crescent(p, p.x(0.8f), p.y(0.14f), p.w * 0.07f, solid)
        twinkles(p, 4, solid, 0.04f, 0.25f)
    }

    /** A crown wound about with thorns, a rose at its front. */
    fun thornCrown(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.52f)
        val w2 = p.w * 0.34f
        val crown = Path().apply {
            moveTo(cx - w2, cy + p.h * 0.12f)
            lineTo(cx - w2 * 1.05f, cy - p.h * 0.12f)
            lineTo(cx - w2 * 0.6f, cy - p.h * 0.02f)
            lineTo(cx - w2 * 0.3f, cy - p.h * 0.22f)
            lineTo(cx, cy - p.h * 0.05f)
            lineTo(cx + w2 * 0.3f, cy - p.h * 0.22f)
            lineTo(cx + w2 * 0.6f, cy - p.h * 0.02f)
            lineTo(cx + w2 * 1.05f, cy - p.h * 0.12f)
            lineTo(cx + w2, cy + p.h * 0.12f)
            close()
        }
        p.c.drawPath(crown, line)
        for (k in listOf(-1.05f, -0.3f, 0.3f, 1.05f)) p.c.drawCircle(cx + w2 * k, cy - p.h * (if (kotlin.math.abs(k) > 1f) 0.14f else 0.24f), p.u * 1.6f, solid)
        // The thorns round it.
        val vine = Path().apply {
            moveTo(cx - w2 * 1.2f, cy + p.h * 0.06f)
            cubicTo(cx - w2 * 0.5f, cy + p.h * 0.22f, cx + w2 * 0.5f, cy - p.h * 0.04f, cx + w2 * 1.2f, cy + p.h * 0.14f)
            moveTo(cx - w2 * 1.15f, cy + p.h * 0.16f)
            cubicTo(cx - w2 * 0.4f, cy + p.h * 0.0f, cx + w2 * 0.4f, cy + p.h * 0.24f, cx + w2 * 1.2f, cy + p.h * 0.04f)
        }
        p.c.drawPath(vine, thin(line, 0.6f))
        for (k in 0 until 12) {
            val tx = cx - w2 * 1.1f + k * w2 * 0.2f
            val ty = cy + p.h * (0.08f + (k % 2) * 0.05f)
            p.c.drawPath(Path().apply { moveTo(tx, ty); lineTo(tx + p.u * 1.2f, ty - p.u * 2.6f * (if (k % 2 == 0) 1 else -1)); lineTo(tx + p.u * 2f, ty); close() }, solid)
        }
        // The rose.
        val ry = cy + p.h * 0.24f
        for (k in 0 until 3) p.c.drawCircle(cx, ry, p.u * (5f - k * 1.6f), line)
        p.c.drawLine(cx, ry + p.u * 5, cx, p.y(0.98f), line)
        p.c.drawPath(Path().apply { moveTo(cx, p.y(0.88f)); quadTo(cx + p.w * 0.1f, p.y(0.84f), cx + p.w * 0.12f, p.y(0.88f)); quadTo(cx + p.w * 0.06f, p.y(0.9f), cx, p.y(0.88f)) }, solid)
        twinkles(p, 5, solid, 0.04f, 0.22f)
    }

    /** A full moon with a city's towers across its foot. */
    fun moonCity(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.48f)
        val r = min(p.w, p.h) * 0.36f
        p.c.drawCircle(cx, cy, r, line)
        p.c.drawCircle(cx, cy, r * 0.94f, thin(line, 0.4f))
        p.c.save()
        p.c.clipPath(Path().apply { addCircle(cx, cy, r * 0.94f, Path.Direction.CW) })
        var bx = cx - r
        while (bx < cx + r) {
            val bw = r * (0.12f + p.rnd.nextFloat() * 0.14f)
            val bh = r * (0.3f + p.rnd.nextFloat() * 0.5f)
            p.c.drawRect(bx, cy + r - bh, bx + bw, cy + r, solid)
            bx += bw + r * 0.03f
        }
        p.c.restore()
        p.c.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.12f, thin(line, 0.4f))
        p.c.drawCircle(cx + r * 0.25f, cy - r * 0.45f, r * 0.07f, thin(line, 0.4f))
        twinkles(p, 6, solid, 0.02f, 0.2f)
    }

    /** A crane flying over misty peaks, a sun behind. */
    fun craneAndPeaks(p: Painter, line: Paint, solid: Paint) {
        p.c.drawCircle(p.x(0.68f), p.y(0.28f), p.w * 0.13f, line)
        val bird = Path()
        p.crane(bird, p.x(0.42f), p.y(0.3f), p.w * 0.42f, flip = true)
        p.c.drawPath(bird, solid)
        for ((k, wide) in listOf(0.24f, 0.32f, 0.2f).withIndex()) {
            val px = p.x(0.25f + k * 0.25f)
            val peak = Path()
            p.karst(peak, px, p.y(0.95f), p.w * wide, p.h * (0.36f + (k % 2) * 0.12f))
            p.c.drawPath(peak, line)
        }
        for (k in 0 until 3) p.c.drawLine(p.x(0.1f + k * 0.08f), p.y(0.76f + k * 0.05f), p.x(0.5f + k * 0.12f), p.y(0.76f + k * 0.05f), thin(line, 0.4f))
    }

    /** A shrine gate before a great sun, a mountain behind and grass at its feet. */
    fun gateAndSun(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val base = p.y(0.84f)
        val size = p.w * 0.6f
        p.c.drawCircle(cx, p.y(0.4f), p.w * 0.27f, thin(line, 0.7f))
        // The mountain, low behind.
        p.c.drawPath(Path().apply {
            moveTo(p.x(0.08f), p.y(0.66f)); quadTo(p.x(0.34f), p.y(0.6f), p.x(0.45f), p.y(0.47f)); lineTo(p.x(0.55f), p.y(0.47f)); quadTo(p.x(0.66f), p.y(0.6f), p.x(0.92f), p.y(0.66f))
        }, thin(line, 0.6f))
        // The gate: posts, tie-beam, and the two beams of its head.
        for (side in listOf(-1f, 1f)) {
            p.c.drawPath(Path().apply {
                moveTo(cx + side * size * 0.33f, base); lineTo(cx + side * size * 0.29f, base - size * 0.78f); lineTo(cx + side * size * 0.22f, base - size * 0.78f); lineTo(cx + side * size * 0.25f, base); close()
            }, solid)
        }
        p.c.drawRect(cx - size * 0.42f, base - size * 0.6f, cx + size * 0.42f, base - size * 0.54f, solid)
        p.c.drawRect(cx - size * 0.45f, base - size * 0.8f, cx + size * 0.45f, base - size * 0.75f, solid)
        p.c.drawPath(Path().apply {
            moveTo(cx - size * 0.56f, base - size * 0.93f); quadTo(cx, base - size * 0.85f, cx + size * 0.56f, base - size * 0.93f)
            lineTo(cx + size * 0.51f, base - size * 0.82f); quadTo(cx, base - size * 0.8f, cx - size * 0.51f, base - size * 0.82f); close()
        }, solid)
        p.c.drawRect(cx - size * 0.03f, base - size * 0.75f, cx + size * 0.03f, base - size * 0.6f, solid)
        for (k in 0 until 9) {
            val gx = p.x(0.12f + k * 0.095f)
            p.c.drawLine(gx, base + p.u * 2f, gx + p.u * 1.2f, base - p.u * (2f + (k % 3) * 1.4f), thin(line, 0.5f))
        }
        p.c.drawLine(p.x(0.08f), base + p.u * 2f, p.x(0.92f), base + p.u * 2f, thin(line, 0.6f))
    }

    /** A fighter craft at speed, bolts of light ahead of it, a planet behind. */
    fun fighterAndPlanet(p: Painter, line: Paint, solid: Paint) {
        val pr = p.w * 0.2f
        val px = p.x(0.72f)
        val py = p.y(0.3f)
        p.c.drawCircle(px, py, pr, line)
        p.c.drawOval(px - pr * 1.6f, py - pr * 0.3f, px + pr * 1.6f, py + pr * 0.3f, thin(line, 0.6f))
        val cx = p.x(0.42f)
        val cy = p.y(0.66f)
        val s = p.w * 0.38f
        val craft = Path().apply {
            moveTo(cx + s * 0.5f, cy - s * 0.1f); lineTo(cx - s * 0.1f, cy - s * 0.04f); lineTo(cx - s * 0.36f, cy - s * 0.36f); lineTo(cx - s * 0.44f, cy - s * 0.34f)
            lineTo(cx - s * 0.3f, cy - s * 0.0f); lineTo(cx - s * 0.44f, cy + s * 0.3f); lineTo(cx - s * 0.36f, cy + s * 0.32f); lineTo(cx - s * 0.1f, cy + s * 0.04f); close()
        }
        p.c.drawPath(craft, line)
        p.c.drawCircle(cx + s * 0.18f, cy - s * 0.06f, s * 0.04f, solid)
        for (k in 0 until 3) p.c.drawLine(cx - s * (0.5f + k * 0.08f), cy + s * (k - 1) * 0.08f, cx - s * (0.75f + k * 0.08f), cy + s * (k - 1) * 0.08f, thin(line, 0.5f))
        for (k in 0 until 2) p.c.drawLine(cx + s * (0.6f + k * 0.25f), cy - s * (0.12f + k * 0.03f), cx + s * (0.75f + k * 0.25f), cy - s * (0.14f + k * 0.03f), line)
        twinkles(p, 7, solid, 0.04f, 0.95f)
    }

    /** A sleek ship with a ring about it, streaking away from the stars. */
    fun shipAndWarp(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.55f)
        val cy = p.y(0.5f)
        val s = p.w * 0.7f
        val hull = Path().apply {
            moveTo(cx + s * 0.5f, cy); cubicTo(cx + s * 0.4f, cy - s * 0.07f, cx, cy - s * 0.08f, cx - s * 0.4f, cy - s * 0.05f)
            lineTo(cx - s * 0.4f, cy + s * 0.05f); cubicTo(cx, cy + s * 0.08f, cx + s * 0.4f, cy + s * 0.07f, cx + s * 0.5f, cy); close()
        }
        p.c.drawPath(hull, line)
        p.c.drawOval(cx - s * 0.16f, cy - s * 0.2f, cx - s * 0.06f, cy + s * 0.2f, line)
        p.c.drawCircle(cx - s * 0.42f, cy, s * 0.03f, solid)
        for (k in 0 until 12) {
            val yy = cy + (k - 5.5f) * s * 0.06f
            val len = s * (0.12f + (k % 3) * 0.08f)
            p.c.drawLine(cx - s * 0.5f - len * 0.2f, yy, cx - s * 0.5f - len * 1.2f, yy, thin(line, 0.4f))
        }
        twinkles(p, 6, solid, 0.04f, 0.3f)
    }

    /** An eye made of circuit lines. */
    fun circuitEye(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.48f)
        val w2 = p.w * 0.34f
        p.c.drawPath(Path().apply { moveTo(cx - w2, cy); quadTo(cx, cy - p.h * 0.32f, cx + w2, cy); quadTo(cx, cy + p.h * 0.32f, cx - w2, cy); close() }, line)
        p.c.drawCircle(cx, cy, p.w * 0.11f, line)
        p.c.drawCircle(cx, cy, p.w * 0.05f, solid)
        // Traces running out from it, ending in pads.
        repeat(10) { k ->
            val side = if (k % 2 == 0) 1f else -1f
            val sy = cy + (k / 2 - 2) * p.h * 0.07f
            val x0 = cx + side * w2 * 0.9f
            val x1 = cx + side * (w2 + p.w * 0.06f)
            val y1 = sy + (if (k % 3 == 0) p.h * 0.05f else -p.h * 0.03f)
            val x2 = cx + side * (w2 + p.w * 0.14f)
            p.c.drawPath(Path().apply { moveTo(x0, sy); lineTo(x1, sy); lineTo(x1 + side * p.w * 0.03f, y1); lineTo(x2, y1) }, thin(line, 0.5f))
            p.c.drawCircle(x2, y1, p.u * 1.2f, solid)
        }
    }

    /** A seedling growing out of cracked ground. */
    fun seedling(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val gy = p.y(0.72f)
        p.c.drawLine(p.x(0.06f), gy, p.x(0.94f), gy, line)
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.02f, gy); lineTo(cx - p.w * 0.08f, gy + p.h * 0.08f); lineTo(cx - p.w * 0.04f, gy + p.h * 0.12f); lineTo(cx - p.w * 0.12f, gy + p.h * 0.24f) }, thin(line, 0.6f))
        p.c.drawPath(Path().apply { moveTo(cx + p.w * 0.03f, gy); lineTo(cx + p.w * 0.1f, gy + p.h * 0.1f); lineTo(cx + p.w * 0.07f, gy + p.h * 0.16f); lineTo(cx + p.w * 0.2f, gy + p.h * 0.24f) }, thin(line, 0.6f))
        p.c.drawPath(Path().apply { moveTo(cx, gy); quadTo(cx - p.w * 0.03f, gy - p.h * 0.2f, cx, gy - p.h * 0.38f) }, line)
        for (side in listOf(-1f, 1f)) {
            val ly = gy - p.h * (if (side < 0) 0.26f else 0.34f)
            val leaf = Path().apply {
                moveTo(cx, ly); quadTo(cx + side * p.w * 0.06f, ly - p.h * 0.14f, cx + side * p.w * 0.22f, ly - p.h * 0.1f)
                quadTo(cx + side * p.w * 0.12f, ly + p.h * 0.02f, cx, ly); close()
            }
            p.c.drawPath(leaf, solid)
        }
        // A broken skyline far behind.
        var bx = p.x(0.08f)
        while (bx < p.x(0.9f)) {
            val bh = p.h * (0.06f + p.rnd.nextFloat() * 0.14f)
            if (kotlin.math.abs(bx - cx) > p.w * 0.12f) p.c.drawPath(Path().apply { moveTo(bx, gy); lineTo(bx, gy - bh); lineTo(bx + p.w * 0.03f, gy - bh * 0.85f); lineTo(bx + p.w * 0.06f, gy - bh); lineTo(bx + p.w * 0.06f, gy) }, thin(line, 0.4f))
            bx += p.w * 0.08f
        }
    }

    private fun gear(p: Painter, cx: Float, cy: Float, r: Float, teeth: Int, line: Paint) {
        val path = Path()
        for (i in 0 until teeth * 2) {
            val a0 = 2f * PI.toFloat() * i / (teeth * 2)
            val a1 = 2f * PI.toFloat() * (i + 1) / (teeth * 2)
            val rr = if (i % 2 == 0) r else r * 0.84f
            if (i == 0) path.moveTo(cx + cos(a0) * rr, cy + sin(a0) * rr) else path.lineTo(cx + cos(a0) * rr, cy + sin(a0) * rr)
            path.lineTo(cx + cos(a1) * rr, cy + sin(a1) * rr)
        }
        path.close()
        p.c.drawPath(path, line)
        p.c.drawCircle(cx, cy, r * 0.32f, line)
    }

    /** Gears turning together under an airship. */
    fun gearsAndAirship(p: Painter, line: Paint, solid: Paint) {
        val s = p.w * 0.5f
        val ax = p.x(0.5f)
        val ay = p.y(0.22f)
        p.c.drawOval(ax - s * 0.5f, ay - s * 0.15f, ax + s * 0.5f, ay + s * 0.15f, line)
        for (k in -2..2) p.c.drawLine(ax + k * s * 0.15f, ay - s * 0.14f * (1f - kotlin.math.abs(k) * 0.12f), ax + k * s * 0.15f, ay + s * 0.14f * (1f - kotlin.math.abs(k) * 0.12f), thin(line, 0.4f))
        p.c.drawRoundRect(ax - s * 0.14f, ay + s * 0.22f, ax + s * 0.14f, ay + s * 0.3f, p.u, p.u, solid)
        p.c.drawLine(ax - s * 0.12f, ay + s * 0.22f, ax - s * 0.22f, ay + s * 0.12f, thin(line, 0.5f))
        p.c.drawLine(ax + s * 0.12f, ay + s * 0.22f, ax + s * 0.22f, ay + s * 0.12f, thin(line, 0.5f))
        gear(p, p.x(0.38f), p.y(0.7f), p.w * 0.2f, 12, line)
        gear(p, p.x(0.66f), p.y(0.6f), p.w * 0.13f, 9, line)
        gear(p, p.x(0.7f), p.y(0.86f), p.w * 0.09f, 7, line)
    }

    /** A mask, with lines bursting out behind it. */
    fun maskAndBurst(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        for (k in 0 until 24) {
            val a = 2f * PI.toFloat() * k / 24
            val r0 = p.w * 0.3f
            val r1 = p.w * (if (k % 2 == 0) 0.46f else 0.4f)
            p.c.drawLine(cx + cos(a) * r0, cy + sin(a) * r0 * 0.9f, cx + cos(a) * r1, cy + sin(a) * r1 * 0.9f, thin(line, 0.5f))
        }
        val mask = Path().apply {
            moveTo(cx - p.w * 0.3f, cy - p.h * 0.02f)
            cubicTo(cx - p.w * 0.28f, cy - p.h * 0.14f, cx - p.w * 0.08f, cy - p.h * 0.12f, cx, cy - p.h * 0.05f)
            cubicTo(cx + p.w * 0.08f, cy - p.h * 0.12f, cx + p.w * 0.28f, cy - p.h * 0.14f, cx + p.w * 0.3f, cy - p.h * 0.02f)
            cubicTo(cx + p.w * 0.26f, cy + p.h * 0.1f, cx + p.w * 0.08f, cy + p.h * 0.1f, cx, cy + p.h * 0.03f)
            cubicTo(cx - p.w * 0.08f, cy + p.h * 0.1f, cx - p.w * 0.26f, cy + p.h * 0.1f, cx - p.w * 0.3f, cy - p.h * 0.02f)
            close()
        }
        p.c.drawPath(mask, solid)
        p.c.drawOval(cx - p.w * 0.2f, cy - p.h * 0.05f, cx - p.w * 0.08f, cy + p.h * 0.02f, Paint(solid).apply { shader = null; color = 0x99000000.toInt() })
        p.c.drawOval(cx + p.w * 0.08f, cy - p.h * 0.05f, cx + p.w * 0.2f, cy + p.h * 0.02f, Paint(solid).apply { shader = null; color = 0x99000000.toInt() })
    }

    /** A magnifying glass beside a curved pipe, a wisp of smoke over it. */
    fun glassAndPipe(p: Painter, line: Paint, solid: Paint) {
        val gx = p.x(0.4f)
        val gy = p.y(0.38f)
        val r = p.w * 0.2f
        p.c.drawCircle(gx, gy, r, line)
        p.c.drawCircle(gx, gy, r * 0.88f, thin(line, 0.4f))
        p.c.drawArc(RectF(gx - r * 0.6f, gy - r * 0.6f, gx + r * 0.6f, gy + r * 0.6f), 200f, 60f, false, thin(line, 0.6f))
        p.c.drawPath(Path().apply { moveTo(gx + r * 0.7f, gy + r * 0.7f); lineTo(gx + r * 1.9f, gy + r * 1.9f); lineTo(gx + r * 1.75f, gy + r * 2.05f); lineTo(gx + r * 0.55f, gy + r * 0.85f); close() }, solid)
        // The pipe.
        val px = p.x(0.66f)
        val py = p.y(0.66f)
        p.c.drawPath(Path().apply { moveTo(px - p.w * 0.3f, py + p.h * 0.18f); quadTo(px - p.w * 0.05f, py + p.h * 0.22f, px + p.w * 0.04f, py + p.h * 0.1f) }, line)
        p.c.drawPath(Path().apply { moveTo(px, py + p.h * 0.12f); lineTo(px - p.w * 0.01f, py - p.h * 0.02f); lineTo(px + p.w * 0.12f, py - p.h * 0.02f); lineTo(px + p.w * 0.1f, py + p.h * 0.14f); quadTo(px + p.w * 0.05f, py + p.h * 0.17f, px, py + p.h * 0.12f); close() }, solid)
        p.c.drawPath(Path().apply { moveTo(px + p.w * 0.05f, py - p.h * 0.04f); cubicTo(px - p.w * 0.04f, py - p.h * 0.14f, px + p.w * 0.12f, py - p.h * 0.2f, px + p.w * 0.04f, py - p.h * 0.3f) }, thin(line, 0.5f))
    }

    /** A teacup on its saucer, steaming, a magnifying glass leaning on it. */
    fun teacupAndGlass(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.46f)
        val cy = p.y(0.64f)
        val r = p.w * 0.22f
        p.c.drawOval(cx - r * 1.5f, cy + r * 0.55f, cx + r * 1.5f, cy + r * 0.85f, line)
        p.c.drawPath(Path().apply { moveTo(cx - r, cy - r * 0.1f); quadTo(cx - r * 0.95f, cy + r * 0.7f, cx, cy + r * 0.7f); quadTo(cx + r * 0.95f, cy + r * 0.7f, cx + r, cy - r * 0.1f) }, line)
        p.c.drawOval(cx - r, cy - r * 0.22f, cx + r, cy + r * 0.02f, line)
        p.c.drawArc(RectF(cx + r * 0.8f, cy - r * 0.05f, cx + r * 1.35f, cy + r * 0.45f), -80f, 190f, false, line)
        for (k in 0 until 3) {
            val sx = cx + (k - 1) * r * 0.4f
            p.c.drawPath(Path().apply { moveTo(sx, cy - r * 0.35f); cubicTo(sx - r * 0.25f, cy - r * 0.7f, sx + r * 0.25f, cy - r * 0.9f, sx, cy - r * 1.3f) }, thin(line, 0.6f))
        }
        p.c.drawCircle(p.x(0.78f), p.y(0.4f), p.w * 0.1f, line)
        p.c.drawLine(p.x(0.78f) - p.w * 0.07f, p.y(0.4f) + p.w * 0.07f, p.x(0.64f), p.y(0.74f), line)
        twinkles(p, 3, solid, 0.06f, 0.2f)
    }

    /** A key held in the cross of a sight. */
    fun keyInSight(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val r = min(p.w, p.h) * 0.38f
        p.c.drawCircle(cx, cy, r, line)
        p.c.drawCircle(cx, cy, r * 0.7f, thin(line, 0.4f))
        for (k in 0 until 4) {
            val a = PI.toFloat() / 2 * k
            p.c.drawLine(cx + cos(a) * r * 0.8f, cy + sin(a) * r * 0.8f, cx + cos(a) * r * 1.2f, cy + sin(a) * r * 1.2f, line)
        }
        // The key, aslant.
        p.c.save()
        p.c.rotate(-35f, cx, cy)
        p.c.drawCircle(cx - r * 0.42f, cy, r * 0.16f, line)
        p.c.drawCircle(cx - r * 0.42f, cy, r * 0.06f, solid)
        p.c.drawLine(cx - r * 0.26f, cy, cx + r * 0.5f, cy, line)
        p.c.drawRect(cx + r * 0.3f, cy, cx + r * 0.36f, cy + r * 0.14f, solid)
        p.c.drawRect(cx + r * 0.42f, cy, cx + r * 0.48f, cy + r * 0.1f, solid)
        p.c.restore()
    }

    /** A poppy on its stem, wire strung behind. */
    fun poppyAndWire(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.36f)
        val r = p.w * 0.17f
        for (k in 0 until 4) {
            val a = PI.toFloat() / 2 * k + 0.4f
            val px = cx + cos(a) * r * 0.5f
            val py = cy + sin(a) * r * 0.36f
            p.c.drawOval(px - r * 0.6f, py - r * 0.45f, px + r * 0.6f, py + r * 0.45f, line)
        }
        p.c.drawCircle(cx, cy, r * 0.22f, solid)
        for (k in 0 until 8) {
            val a = 2f * PI.toFloat() * k / 8
            p.c.drawLine(cx + cos(a) * r * 0.24f, cy + sin(a) * r * 0.24f, cx + cos(a) * r * 0.36f, cy + sin(a) * r * 0.36f, thin(line, 0.4f))
        }
        p.c.drawPath(Path().apply { moveTo(cx, cy + r * 0.5f); quadTo(cx + p.w * 0.04f, p.y(0.7f), cx - p.w * 0.02f, p.y(0.96f)) }, line)
        p.c.drawPath(Path().apply { moveTo(cx + p.w * 0.01f, p.y(0.7f)); quadTo(cx + p.w * 0.16f, p.y(0.62f), cx + p.w * 0.2f, p.y(0.68f)); quadTo(cx + p.w * 0.1f, p.y(0.72f), cx + p.w * 0.01f, p.y(0.7f)); close() }, solid)
        // The wire: a strand with its barbs.
        val wy = p.y(0.82f)
        val wire = Path().apply { moveTo(p.x(0.04f), wy); cubicTo(p.x(0.3f), wy - p.h * 0.05f, p.x(0.7f), wy + p.h * 0.05f, p.x(0.96f), wy) }
        p.c.drawPath(wire, thin(line, 0.6f))
        for (k in 0 until 7) {
            val bx = p.x(0.1f + k * 0.13f)
            val by = wy + sin(k.toFloat()) * p.h * 0.015f
            p.c.drawLine(bx - p.u * 1.5f, by - p.u * 1.5f, bx + p.u * 1.5f, by + p.u * 1.5f, thin(line, 0.5f))
            p.c.drawLine(bx - p.u * 1.5f, by + p.u * 1.5f, bx + p.u * 1.5f, by - p.u * 1.5f, thin(line, 0.5f))
        }
    }

    /** A shield with two swords crossed behind it. */
    fun shieldAndSwords(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        for (side in listOf(-1f, 1f)) {
            p.c.save()
            p.c.rotate(side * 35f, cx, cy)
            p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.018f, cy + p.h * 0.36f); lineTo(cx - p.w * 0.018f, cy - p.h * 0.34f); lineTo(cx, cy - p.h * 0.4f); lineTo(cx + p.w * 0.018f, cy - p.h * 0.34f); lineTo(cx + p.w * 0.018f, cy + p.h * 0.36f) }, line)
            p.c.drawRect(cx - p.w * 0.08f, cy + p.h * 0.36f, cx + p.w * 0.08f, cy + p.h * 0.38f, solid)
            p.c.drawRect(cx - p.w * 0.012f, cy + p.h * 0.38f, cx + p.w * 0.012f, cy + p.h * 0.46f, line)
            p.c.drawCircle(cx, cy + p.h * 0.48f, p.w * 0.02f, solid)
            p.c.restore()
        }
        val sw = p.w * 0.22f
        val shield = Path().apply {
            moveTo(cx - sw, cy - p.h * 0.2f); lineTo(cx + sw, cy - p.h * 0.2f); lineTo(cx + sw, cy + p.h * 0.02f)
            quadTo(cx + sw * 0.9f, cy + p.h * 0.2f, cx, cy + p.h * 0.3f); quadTo(cx - sw * 0.9f, cy + p.h * 0.2f, cx - sw, cy + p.h * 0.02f); close()
        }
        p.c.drawPath(shield, Paint(solid).apply { shader = null; color = 0x55000000 })
        p.c.drawPath(shield, line)
        p.c.drawLine(cx, cy - p.h * 0.2f, cx, cy + p.h * 0.3f, thin(line, 0.6f))
        p.c.drawLine(cx - sw, cy - p.h * 0.05f, cx + sw, cy - p.h * 0.05f, thin(line, 0.6f))
        p.c.drawCircle(cx, cy - p.h * 0.05f, p.w * 0.04f, solid)
    }

    /** A cameo: a lady's head in profile, hair up, in an oval wreathed with leaves. */
    fun cameo(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val rw = p.w * 0.26f
        val rh = p.h * 0.36f
        p.c.drawOval(cx - rw, cy - rh, cx + rw, cy + rh, line)
        p.c.drawOval(cx - rw * 0.9f, cy - rh * 0.92f, cx + rw * 0.9f, cy + rh * 0.92f, thin(line, 0.4f))
        val head = Path().apply {
            moveTo(cx - rw * 0.3f, cy + rh * 0.7f)
            cubicTo(cx - rw * 0.2f, cy + rh * 0.35f, cx - rw * 0.55f, cy + rh * 0.1f, cx - rw * 0.4f, cy - rh * 0.2f)
            cubicTo(cx - rw * 0.4f, cy - rh * 0.5f, cx - rw * 0.1f, cy - rh * 0.62f, cx + rw * 0.15f, cy - rh * 0.48f)
            cubicTo(cx + rw * 0.35f, cy - rh * 0.4f, cx + rw * 0.38f, cy - rh * 0.25f, cx + rw * 0.36f, cy - rh * 0.12f)
            lineTo(cx + rw * 0.46f, cy - rh * 0.02f)
            lineTo(cx + rw * 0.36f, cy + rh * 0.04f)
            lineTo(cx + rw * 0.38f, cy + rh * 0.1f)
            quadTo(cx + rw * 0.3f, cy + rh * 0.2f, cx + rw * 0.15f, cy + rh * 0.22f)
            cubicTo(cx + rw * 0.1f, cy + rh * 0.4f, cx + rw * 0.2f, cy + rh * 0.55f, cx + rw * 0.4f, cy + rh * 0.7f)
            close()
        }
        p.c.drawPath(head, solid)
        p.c.drawCircle(cx - rw * 0.2f, cy - rh * 0.58f, rw * 0.2f, solid)
        // Leaves about the oval.
        for (k in 0 until 18) {
            val a = 2f * PI.toFloat() * k / 18
            val lx = cx + cos(a) * rw * 1.12f
            val ly = cy + sin(a) * rh * 1.08f
            p.c.save()
            p.c.rotate(a * 180f / PI.toFloat() + 90f, lx, ly)
            p.c.drawOval(lx - p.u * 2.4f, ly - p.u, lx + p.u * 2.4f, ly + p.u, solid)
            p.c.restore()
        }
    }

    /** Pyramids under a sun with its rays, a palm beside them. */
    fun pyramidAndSun(p: Painter, line: Paint, solid: Paint) {
        val gy = p.y(0.86f)
        val sx = p.x(0.66f)
        val sy = p.y(0.3f)
        p.c.drawCircle(sx, sy, p.w * 0.1f, solid)
        for (k in 0 until 16) {
            val a = 2f * PI.toFloat() * k / 16
            p.c.drawLine(sx + cos(a) * p.w * 0.13f, sy + sin(a) * p.w * 0.13f, sx + cos(a) * p.w * (0.19f + (k % 2) * 0.04f), sy + sin(a) * p.w * (0.19f + (k % 2) * 0.04f), thin(line, 0.5f))
        }
        for ((px, half) in listOf(0.38f to 0.24f, 0.72f to 0.15f)) {
            val cx = p.x(px)
            p.c.drawPath(Path().apply { moveTo(cx - p.w * half, gy); lineTo(cx, gy - p.w * half * 0.95f); lineTo(cx + p.w * half, gy); close() }, line)
            p.c.drawLine(cx, gy - p.w * half * 0.95f, cx + p.w * half * 0.2f, gy, thin(line, 0.5f))
        }
        p.c.drawLine(p.x(0.04f), gy, p.x(0.96f), gy, line)
        val palm = Path()
        p.palm(palm, p.x(0.12f), gy, p.h * 0.42f, lean = 0.2f)
        p.c.drawPath(palm, solid)
    }

    /** Two hearts, one through the other. */
    fun twoHearts(p: Painter, line: Paint, solid: Paint) {
        p.c.drawPath(heart(p.x(0.4f), p.y(0.46f), p.w * 0.2f), line)
        p.c.drawPath(heart(p.x(0.6f), p.y(0.54f), p.w * 0.2f), line)
        p.c.drawPath(heart(p.x(0.6f), p.y(0.54f), p.w * 0.07f), solid)
        twinkles(p, 6, solid, 0.04f, 0.95f)
    }

    /** A rose with a dagger through it, drops falling. */
    fun roseAndDagger(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.42f)
        p.c.save()
        p.c.rotate(-40f, cx, cy)
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.02f, cy + p.h * 0.3f); lineTo(cx - p.w * 0.02f, cy - p.h * 0.32f); lineTo(cx, cy - p.h * 0.38f); lineTo(cx + p.w * 0.02f, cy - p.h * 0.32f); lineTo(cx + p.w * 0.02f, cy + p.h * 0.3f) }, line)
        p.c.drawRect(cx - p.w * 0.09f, cy + p.h * 0.3f, cx + p.w * 0.09f, cy + p.h * 0.32f, solid)
        p.c.drawRect(cx - p.w * 0.015f, cy + p.h * 0.32f, cx + p.w * 0.015f, cy + p.h * 0.42f, line)
        p.c.drawCircle(cx, cy + p.h * 0.44f, p.w * 0.022f, solid)
        p.c.restore()
        val spiral = Path()
        for (i in 0..60) {
            val a = i / 60f * 4.2f * PI.toFloat()
            val rr = p.u * (1f + i / 60f * 8f)
            val sx = cx + cos(a) * rr
            val sy = cy + sin(a) * rr * 0.8f
            if (i == 0) spiral.moveTo(sx, sy) else spiral.lineTo(sx, sy)
        }
        p.c.drawPath(spiral, line)
        p.c.drawPath(Path().apply { moveTo(cx - p.u * 9, cy); quadTo(cx - p.u * 12, cy + p.u * 9, cx, cy + p.u * 11); quadTo(cx + p.u * 12, cy + p.u * 9, cx + p.u * 9, cy) }, line)
        p.c.drawPath(Path().apply { moveTo(cx, cy + p.u * 11); quadTo(cx - p.w * 0.04f, p.y(0.8f), cx + p.w * 0.02f, p.y(0.96f)) }, line)
        for (k in 0 until 3) {
            val dx = p.x(0.66f + k * 0.04f)
            val dy = p.y(0.66f + k * 0.1f)
            p.c.drawPath(Path().apply { moveTo(dx, dy - p.u * 2.4f); quadTo(dx + p.u * 1.6f, dy + p.u * 0.6f, dx, dy + p.u * 1.2f); quadTo(dx - p.u * 1.6f, dy + p.u * 0.6f, dx, dy - p.u * 2.4f); close() }, solid)
        }
    }

    /** A soft cloud with a heart, stars about it. */
    fun cloudAndHeart(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.56f)
        val cloud = Path().apply {
            moveTo(cx - p.w * 0.36f, cy + p.h * 0.12f)
            cubicTo(cx - p.w * 0.46f, cy, cx - p.w * 0.34f, cy - p.h * 0.14f, cx - p.w * 0.2f, cy - p.h * 0.08f)
            cubicTo(cx - p.w * 0.16f, cy - p.h * 0.24f, cx + p.w * 0.1f, cy - p.h * 0.26f, cx + p.w * 0.14f, cy - p.h * 0.1f)
            cubicTo(cx + p.w * 0.3f, cy - p.h * 0.18f, cx + p.w * 0.46f, cy - p.h * 0.02f, cx + p.w * 0.36f, cy + p.h * 0.12f)
            close()
        }
        p.c.drawPath(cloud, line)
        p.c.drawPath(heart(cx, cy - p.h * 0.01f, p.w * 0.1f), solid)
        crescent(p, p.x(0.78f), p.y(0.16f), p.w * 0.07f, solid)
        twinkles(p, 7, solid, 0.04f, 0.32f)
        for (k in 0 until 4) p.c.drawLine(cx - p.w * 0.24f + k * p.w * 0.16f, cy + p.h * 0.2f, cx - p.w * 0.26f + k * p.w * 0.16f, cy + p.h * 0.26f, thin(line, 0.4f))
    }

    /** A heart broken in two, rain falling on it. */
    fun brokenHeart(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.58f)
        val s = p.w * 0.26f
        val crack = floatArrayOf(0f, -0.35f, -0.08f, -0.1f, 0.06f, 0.15f, -0.05f, 0.4f, 0f, 0.9f)
        for (side in listOf(-1f, 1f)) {
            p.c.save()
            p.c.translate(side * p.u * 2.4f, 0f)
            p.c.rotate(side * 6f, cx, cy + s * 0.9f)
            val half = Path()
            half.moveTo(cx, cy + s * 0.9f)
            if (side < 0) half.cubicTo(cx - s * 1.4f, cy + s * 0.1f, cx - s * 1.0f, cy - s * 0.95f, cx, cy - s * 0.35f)
            else half.cubicTo(cx + s * 1.4f, cy + s * 0.1f, cx + s * 1.0f, cy - s * 0.95f, cx, cy - s * 0.35f)
            var k = 2
            while (k < crack.size) {
                half.lineTo(cx + crack[k] * s, cy + crack[k + 1] * s)
                k += 2
            }
            p.c.drawPath(half, line)
            p.c.restore()
        }
        repeat(14) {
            val rx = p.x(0.08f + p.rnd.nextFloat() * 0.84f)
            val ry = p.y(0.02f + p.rnd.nextFloat() * 0.3f)
            p.c.drawLine(rx, ry, rx - p.u * 1.2f, ry + p.u * 5f, thin(line, 0.4f))
        }
    }

    /** A bat flying across a full moon. */
    fun batAndMoon(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.46f)
        val r = min(p.w, p.h) * 0.36f
        p.c.drawCircle(cx, cy, r, line)
        p.c.drawCircle(cx, cy, r * 0.93f, thin(line, 0.4f))
        val s = r * 1.1f
        val bat = Path().apply {
            moveTo(cx, cy - s * 0.12f)
            lineTo(cx - s * 0.06f, cy - s * 0.2f)
            lineTo(cx - s * 0.08f, cy - s * 0.1f)
            quadTo(cx - s * 0.3f, cy - s * 0.24f, cx - s * 0.62f, cy - s * 0.12f)
            quadTo(cx - s * 0.5f, cy - s * 0.04f, cx - s * 0.48f, cy + s * 0.06f)
            quadTo(cx - s * 0.38f, cy - s * 0.02f, cx - s * 0.3f, cy + s * 0.06f)
            quadTo(cx - s * 0.2f, cy - s * 0.02f, cx - s * 0.1f, cy + s * 0.1f)
            lineTo(cx, cy + s * 0.04f)
            lineTo(cx + s * 0.1f, cy + s * 0.1f)
            quadTo(cx + s * 0.2f, cy - s * 0.02f, cx + s * 0.3f, cy + s * 0.06f)
            quadTo(cx + s * 0.38f, cy - s * 0.02f, cx + s * 0.48f, cy + s * 0.06f)
            quadTo(cx + s * 0.5f, cy - s * 0.04f, cx + s * 0.62f, cy - s * 0.12f)
            quadTo(cx + s * 0.3f, cy - s * 0.24f, cx + s * 0.08f, cy - s * 0.1f)
            lineTo(cx + s * 0.06f, cy - s * 0.2f)
            close()
        }
        p.c.drawPath(bat, solid)
        twinkles(p, 5, solid, 0.02f, 0.12f)
    }

    /** An eye opening among reaching tentacles. */
    fun eyeAndTentacles(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.4f)
        for (k in 0 until 6) {
            val bx = p.x(0.12f + k * 0.15f)
            val lean = if (k < 3) -0.5f else 0.5f
            val path = Path().apply {
                moveTo(bx, p.y(0.98f))
                cubicTo(bx + lean * p.w * 0.05f, p.y(0.75f), bx - lean * p.w * 0.12f, p.y(0.6f), bx + lean * p.w * 0.08f, p.y(0.5f + (k % 2) * 0.08f))
            }
            p.c.drawPath(path, line)
        }
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.3f, cy); quadTo(cx, cy - p.h * 0.26f, cx + p.w * 0.3f, cy); quadTo(cx, cy + p.h * 0.26f, cx - p.w * 0.3f, cy); close() }, line)
        p.c.drawCircle(cx, cy, p.w * 0.09f, line)
        p.c.drawOval(cx - p.w * 0.018f, cy - p.w * 0.08f, cx + p.w * 0.018f, cy + p.w * 0.08f, solid)
        for (k in 0 until 9) {
            val a = PI.toFloat() * (1.1f + k * 0.1f)
            p.c.drawLine(cx + cos(a) * p.w * 0.34f, cy + sin(a) * p.h * 0.2f, cx + cos(a) * p.w * 0.42f, cy + sin(a) * p.h * 0.28f, thin(line, 0.4f))
        }
    }

    /** A jack-o'-lantern, its face cut out. */
    fun pumpkin(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.58f)
        val r = p.w * 0.3f
        for (k in -2..2) p.c.drawOval(cx + k * r * 0.24f - r * 0.4f, cy - r * 0.7f, cx + k * r * 0.24f + r * 0.4f, cy + r * 0.7f, if (kotlin.math.abs(k) == 2) line else thin(line, 0.5f))
        p.c.drawPath(Path().apply { moveTo(cx - r * 0.05f, cy - r * 0.66f); quadTo(cx - r * 0.02f, cy - r * 0.9f, cx + r * 0.15f, cy - r * 1.0f) }, line)
        for (side in listOf(-1f, 1f)) p.c.drawPath(Path().apply { moveTo(cx + side * r * 0.12f, cy - r * 0.08f); lineTo(cx + side * r * 0.42f, cy - r * 0.08f); lineTo(cx + side * r * 0.27f, cy - r * 0.34f); close() }, solid)
        p.c.drawPath(Path().apply {
            moveTo(cx - r * 0.5f, cy + r * 0.14f); quadTo(cx, cy + r * 0.6f, cx + r * 0.5f, cy + r * 0.14f); lineTo(cx + r * 0.3f, cy + r * 0.26f)
            lineTo(cx + r * 0.16f, cy + r * 0.14f); lineTo(cx, cy + r * 0.28f); lineTo(cx - r * 0.16f, cy + r * 0.14f); lineTo(cx - r * 0.3f, cy + r * 0.26f); close()
        }, solid)
        twinkles(p, 4, solid, 0.02f, 0.14f)
    }

    /** A coffee cup, its steam rising in the shape of a heart. */
    fun coffeeCup(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.46f)
        val cy = p.y(0.62f)
        val r = p.w * 0.2f
        p.c.drawPath(Path().apply { moveTo(cx - r, cy - r * 0.4f); lineTo(cx - r * 0.8f, cy + r * 0.9f); quadTo(cx, cy + r * 1.1f, cx + r * 0.8f, cy + r * 0.9f); lineTo(cx + r, cy - r * 0.4f) }, line)
        p.c.drawOval(cx - r, cy - r * 0.55f, cx + r, cy - r * 0.25f, line)
        p.c.drawArc(RectF(cx + r * 0.75f, cy - r * 0.2f, cx + r * 1.4f, cy + r * 0.5f), -90f, 180f, false, line)
        p.c.drawOval(cx - r * 1.5f, cy + r * 0.95f, cx + r * 1.5f, cy + r * 1.2f, line)
        p.c.drawPath(Path().apply {
            moveTo(cx, cy - r * 0.7f)
            cubicTo(cx - r * 0.9f, cy - r * 1.2f, cx - r * 0.6f, cy - r * 2.0f, cx, cy - r * 1.6f)
            cubicTo(cx + r * 0.6f, cy - r * 2.0f, cx + r * 0.9f, cy - r * 1.2f, cx, cy - r * 0.7f)
        }, thin(line, 0.7f))
        p.c.drawRect(cx - r * 0.5f, cy + r * 0.1f, cx + r * 0.5f, cy + r * 0.2f, solid)
    }

    /** A graduate's cap on a pile of books, its tassel hanging. */
    fun capAndBooks(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        var by = p.y(0.94f)
        for (k in 0 until 3) {
            val off = (k - 1) * p.w * 0.04f
            p.c.drawRoundRect(cx - p.w * 0.3f + off, by - p.h * 0.1f, cx + p.w * 0.3f + off, by, p.u, p.u, line)
            p.c.drawLine(cx - p.w * 0.26f + off, by - p.h * 0.05f, cx + p.w * 0.2f + off, by - p.h * 0.05f, thin(line, 0.4f))
            by -= p.h * 0.1f
        }
        val top = by - p.h * 0.14f
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.36f, top); lineTo(cx, top - p.h * 0.12f); lineTo(cx + p.w * 0.36f, top); lineTo(cx, top + p.h * 0.12f); close() }, solid)
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.2f, top + p.h * 0.06f); lineTo(cx - p.w * 0.2f, by - p.h * 0.02f); quadTo(cx, by + p.h * 0.02f, cx + p.w * 0.2f, by - p.h * 0.02f); lineTo(cx + p.w * 0.2f, top + p.h * 0.06f) }, line)
        p.c.drawPath(Path().apply { moveTo(cx, top); lineTo(cx + p.w * 0.3f, top + p.h * 0.02f); lineTo(cx + p.w * 0.3f, top + p.h * 0.16f) }, thin(line, 0.6f))
        p.c.drawRect(cx + p.w * 0.29f, top + p.h * 0.16f, cx + p.w * 0.31f, top + p.h * 0.22f, solid)
        twinkles(p, 4, solid, 0.02f, 0.18f)
    }

    /** A trophy cup between two branches of laurel. */
    fun trophy(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val top = p.y(0.18f)
        val r = p.w * 0.2f
        p.c.drawPath(Path().apply { moveTo(cx - r, top); lineTo(cx + r, top); quadTo(cx + r, top + r * 1.5f, cx, top + r * 1.6f); quadTo(cx - r, top + r * 1.5f, cx - r, top); close() }, line)
        for (side in listOf(-1f, 1f)) p.c.drawArc(RectF(cx + side * r * 0.75f - r * 0.4f, top + r * 0.1f, cx + side * r * 0.75f + r * 0.4f, top + r * 0.8f), if (side < 0) 90f else -90f, 180f, false, line)
        p.c.drawRect(cx - r * 0.1f, top + r * 1.6f, cx + r * 0.1f, top + r * 2.1f, solid)
        p.c.drawRect(cx - r * 0.55f, top + r * 2.1f, cx + r * 0.55f, top + r * 2.35f, solid)
        p.c.drawRect(cx - r * 0.7f, top + r * 2.35f, cx + r * 0.7f, top + r * 2.75f, line)
        twinkle(p, cx, top + r * 0.6f, p.u * 3f, solid)
        for (side in listOf(-1f, 1f)) {
            val stem = Path().apply { moveTo(cx + side * r * 0.4f, p.y(0.95f)); quadTo(cx + side * r * 2.2f, p.y(0.7f), cx + side * r * 1.6f, p.y(0.25f)) }
            p.c.drawPath(stem, thin(line, 0.6f))
            for (k in 0 until 7) {
                val t = 0.1f + k * 0.13f
                val m = 1 - t
                val lx = m * m * (cx + side * r * 0.4f) + 2 * m * t * (cx + side * r * 2.2f) + t * t * (cx + side * r * 1.6f)
                val ly = m * m * p.y(0.95f) + 2 * m * t * p.y(0.7f) + t * t * p.y(0.25f)
                p.c.save()
                p.c.rotate(side * (30f + k * 4f), lx, ly)
                p.c.drawOval(lx - p.u * 3, ly - p.u * 1.1f, lx + p.u * 3, ly + p.u * 1.1f, solid)
                p.c.restore()
            }
        }
    }

    /** A microphone, notes rising round it. */
    fun microphone(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val top = p.y(0.14f)
        val r = p.w * 0.12f
        p.c.drawRoundRect(cx - r, top, cx + r, top + r * 2.6f, r, r, line)
        for (k in 1 until 5) p.c.drawLine(cx - r * 0.8f, top + r * 0.45f * k, cx + r * 0.8f, top + r * 0.45f * k, thin(line, 0.4f))
        p.c.drawArc(RectF(cx - r * 1.6f, top + r * 0.8f, cx + r * 1.6f, top + r * 3.4f), 0f, 180f, false, line)
        p.c.drawLine(cx, top + r * 3.4f, cx, p.y(0.86f), line)
        p.c.drawRect(cx - r * 1.4f, p.y(0.86f), cx + r * 1.4f, p.y(0.9f), solid)
        for ((nx, ny) in listOf(0.18f to 0.36f, 0.8f to 0.28f, 0.76f to 0.6f, 0.22f to 0.66f)) {
            val x0 = p.x(nx)
            val y0 = p.y(ny)
            p.c.drawOval(x0 - p.u * 2.4f, y0 - p.u * 1.6f, x0 + p.u * 2.4f, y0 + p.u * 1.6f, solid)
            p.c.drawLine(x0 + p.u * 2.2f, y0, x0 + p.u * 2.2f, y0 - p.u * 10, line)
            p.c.drawPath(Path().apply { moveTo(x0 + p.u * 2.2f, y0 - p.u * 10); quadTo(x0 + p.u * 6, y0 - p.u * 8, x0 + p.u * 5, y0 - p.u * 4) }, line)
        }
    }

    /** A snowflake in a wreath of holly. */
    fun snowflakeWreath(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val r = min(p.w, p.h) * 0.22f
        for (k in 0 until 6) {
            val a = PI.toFloat() / 3 * k
            val ex = cx + cos(a) * r
            val ey = cy + sin(a) * r
            p.c.drawLine(cx, cy, ex, ey, line)
            for (f in listOf(0.45f, 0.72f)) {
                val bx = cx + cos(a) * r * f
                val by = cy + sin(a) * r * f
                for (side in listOf(-1f, 1f)) {
                    val b = a + side * 0.7f
                    p.c.drawLine(bx, by, bx + cos(b) * r * 0.22f, by + sin(b) * r * 0.22f, thin(line, 0.6f))
                }
            }
        }
        val wr = min(p.w, p.h) * 0.4f
        for (k in 0 until 16) {
            val a = 2f * PI.toFloat() * k / 16
            val lx = cx + cos(a) * wr
            val ly = cy + sin(a) * wr
            p.c.save()
            p.c.rotate(a * 180f / PI.toFloat() + 60f, lx, ly)
            p.c.drawPath(Path().apply {
                moveTo(lx - p.u * 4, ly); quadTo(lx - p.u * 2, ly - p.u * 2.4f, lx, ly - p.u * 1.2f); quadTo(lx + p.u * 2, ly - p.u * 2.4f, lx + p.u * 4, ly)
                quadTo(lx + p.u * 2, ly + p.u * 2.4f, lx, ly + p.u * 1.2f); quadTo(lx - p.u * 2, ly + p.u * 2.4f, lx - p.u * 4, ly); close()
            }, line)
            p.c.restore()
            if (k % 2 == 0) p.c.drawCircle(cx + cos(a + 0.2f) * wr * 0.9f, cy + sin(a + 0.2f) * wr * 0.9f, p.u * 1.4f, solid)
        }
    }
}
