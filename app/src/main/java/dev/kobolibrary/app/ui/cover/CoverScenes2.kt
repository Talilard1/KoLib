package dev.kobolibrary.app.ui.cover

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The painted scenes for mystery, history and war, romance, horror and everyday life, and the things in
 * them: this app's own shapes, as in CoverScenes.
 */

private fun rgb(hex: Long) = (0xFF000000 or hex).toInt()
private fun mix(a: Int, b: Int, f: Float) = ColorUtils.blendARGB(a, b, f.coerceIn(0f, 1f))
private fun alpha(c: Int, a: Int) = ColorUtils.setAlphaComponent(c, a.coerceIn(0, 255))

// ---- things to draw ----------------------------------------------------------------------------

/** A street lamp from [top] down to [bottom] at [lx], lit with [light], and the pool of light it throws. */
internal fun Painter.streetLamp(lx: Float, top: Float, bottom: Float, light: Int, dark: Int, cone: Boolean = true) {
    if (cone) {
        val pool = Path().apply {
            moveTo(lx - u * 2, top + u * 3)
            lineTo(lx - w * 0.2f, bottom)
            lineTo(lx + w * 0.2f, bottom)
            lineTo(lx + u * 2, top + u * 3)
            close()
        }
        c.drawPath(pool, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, top, 0f, bottom, alpha(light, 100), alpha(light, 8), Shader.TileMode.CLAMP)
        })
    }
    glow(lx, top + u * 2, w * 0.15f, light, 190)
    c.drawRect(lx - u * 0.55f, top + u * 3, lx + u * 0.55f, bottom, fill(dark))
    c.drawPath(Path().apply { moveTo(lx - u * 2.4f, top + u * 3.6f); lineTo(lx - u * 1.6f, top); lineTo(lx + u * 1.6f, top); lineTo(lx + u * 2.4f, top + u * 3.6f); close() }, fill(dark))
    disc(lx, top + u * 2, u * 1.3f, mix(light, Color.WHITE, 0.6f))
}

/** A man in a caped greatcoat and a deerstalker, pipe in his mouth, [tall] high on ([px], [py]). */
internal fun Painter.deerstalker(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
    val head = tall * 0.065f
    val hy = py - tall + head * 2.2f
    path.addCircle(px, hy, head, Path.Direction.CW)
    // The cap with its peaks fore and aft.
    path.moveTo(px - head * 1.1f, hy - head * 0.3f)
    path.quadTo(px, hy - head * 1.9f, px + head * 1.1f, hy - head * 0.3f)
    path.lineTo(px + facing * head * 1.9f, hy - head * 0.1f)
    path.lineTo(px - facing * head * 1.9f, hy - head * 0.1f)
    path.close()
    // The pipe.
    path.moveTo(px + facing * head * 0.8f, hy + head * 0.4f)
    path.lineTo(px + facing * head * 1.9f, hy + head * 0.7f)
    path.lineTo(px + facing * head * 1.9f, hy + head * 0.2f)
    path.lineTo(px + facing * head * 2.4f, hy + head * 0.2f)
    path.lineTo(px + facing * head * 2.3f, hy + head * 1.0f)
    path.lineTo(px + facing * head * 0.8f, hy + head * 0.7f)
    path.close()
    // The shoulder cape over a long coat.
    path.moveTo(px - head * 1.2f, hy + head * 1.1f)
    path.lineTo(px - head * 3.4f, hy + head * 4.6f)
    path.lineTo(px + head * 3.4f, hy + head * 4.6f)
    path.lineTo(px + head * 1.2f, hy + head * 1.1f)
    path.close()
    path.moveTo(px - head * 2.2f, hy + head * 4.4f)
    path.lineTo(px - head * 2.8f, py - tall * 0.2f)
    path.lineTo(px + head * 2.8f, py - tall * 0.2f)
    path.lineTo(px + head * 2.2f, hy + head * 4.4f)
    path.close()
    path.addRect(px - head * 1.5f, py - tall * 0.21f, px - head * 0.3f, py, Path.Direction.CW)
    path.addRect(px + head * 0.3f, py - tall * 0.21f, px + head * 1.5f, py, Path.Direction.CW)
}

/** A soldier in a steel helmet, rifle at the slope, walking towards [facing]. */
internal fun Painter.soldier(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
    val head = tall * 0.07f
    val hy = py - tall + head * 2f
    path.addCircle(px, hy, head, Path.Direction.CW)
    path.addArc(RectF(px - head * 1.5f, hy - head * 1.5f, px + head * 1.5f, hy + head * 0.9f), 180f, 180f)
    path.addRect(px - head * 2.0f, hy - head * 0.4f, px + head * 2.0f, hy - head * 0.15f, Path.Direction.CW)
    path.moveTo(px - head * 1.4f, hy + head * 1.0f)
    path.lineTo(px + head * 1.6f, hy + head * 1.0f)
    path.lineTo(px + head * 1.8f, py - tall * 0.45f)
    path.lineTo(px - head * 1.6f, py - tall * 0.45f)
    path.close()
    // Legs mid-stride.
    path.moveTo(px - head * 1.2f, py - tall * 0.46f)
    path.lineTo(px - facing * head * 2.6f, py)
    path.lineTo(px - facing * head * 1.4f, py)
    path.lineTo(px + head * 0.2f, py - tall * 0.28f)
    path.lineTo(px + facing * head * 1.6f, py)
    path.lineTo(px + facing * head * 2.8f, py)
    path.lineTo(px + head * 1.4f, py - tall * 0.46f)
    path.close()
    // The rifle over the shoulder, bayonet up.
    path.moveTo(px - facing * head * 0.6f, hy + head * 1.6f)
    path.lineTo(px - facing * head * 3.6f, hy - head * 3.6f)
    path.lineTo(px - facing * head * 3.2f, hy - head * 3.8f)
    path.lineTo(px - facing * head * 0.1f, hy + head * 1.3f)
    path.close()
}

/** A bomber seen from below, [size] across its wings, flying towards the top. */
internal fun Painter.bomber(path: Path, cx: Float, cy: Float, size: Float) {
    path.addRoundRect(cx - size * 0.05f, cy - size * 0.4f, cx + size * 0.05f, cy + size * 0.38f, size * 0.05f, size * 0.05f, Path.Direction.CW)
    path.moveTo(cx - size * 0.5f, cy - size * 0.02f)
    path.lineTo(cx - size * 0.46f, cy - size * 0.1f)
    path.lineTo(cx + size * 0.46f, cy - size * 0.1f)
    path.lineTo(cx + size * 0.5f, cy - size * 0.02f)
    path.lineTo(cx + size * 0.06f, cy + size * 0.05f)
    path.lineTo(cx - size * 0.06f, cy + size * 0.05f)
    path.close()
    path.moveTo(cx - size * 0.18f, cy + size * 0.36f)
    path.lineTo(cx + size * 0.18f, cy + size * 0.36f)
    path.lineTo(cx + size * 0.16f, cy + size * 0.3f)
    path.lineTo(cx - size * 0.16f, cy + size * 0.3f)
    path.close()
    for (k in listOf(-0.3f, -0.16f, 0.16f, 0.3f)) path.addRoundRect(cx + k * size - size * 0.025f, cy - size * 0.2f, cx + k * size + size * 0.025f, cy - size * 0.04f, size * 0.02f, size * 0.02f, Path.Direction.CW)
}

/** Barbed wire on posts along [atY], from one side to the other. */
internal fun Painter.barbedWire(atY: Float, colour: Int) {
    val post = fill(colour)
    val wire = stroke(colour, u * 0.25f)
    var px = r.left - u * 3
    var last: Pair<Float, Float>? = null
    while (px < r.right + u * 6) {
        val lean = (rnd.nextFloat() - 0.5f) * 10f
        c.save()
        c.rotate(lean, px, atY)
        c.drawRect(px - u * 0.5f, atY - h * 0.08f, px + u * 0.5f, atY + u, post)
        c.restore()
        last?.let { (lx, ly) ->
            for (k in 0..1) {
                val wy = atY - h * (0.03f + k * 0.035f)
                val path = Path().apply { moveTo(lx, wy) }
                var t = 0f
                while (t < 1f) {
                    t += 0.08f
                    path.lineTo(lx + (px - lx) * t, wy + (if ((t * 12).toInt() % 2 == 0) -u else u) * 0.6f + sin(t * PI.toFloat()) * u * 1.5f)
                }
                c.drawPath(path, wire)
            }
        }
        last = px to atY
        px += w * (0.14f + rnd.nextFloat() * 0.08f)
    }
}

/** A poppy: four red petals round a dark heart. */
internal fun Painter.poppy(cx: Float, cy: Float, size: Float, petal: Int = rgb(0xD62828)) {
    for (k in 0 until 4) {
        val a = PI.toFloat() / 2 * k + 0.4f
        val px = cx + cos(a) * size * 0.45f
        val py = cy + sin(a) * size * 0.32f
        c.drawOval(px - size * 0.55f, py - size * 0.42f, px + size * 0.55f, py + size * 0.42f, fill(mix(petal, if (k % 2 == 0) Color.BLACK else Color.WHITE, 0.12f)))
    }
    disc(cx, cy, size * 0.22f, rgb(0x1A0E0E))
}

/** A grand country house: a pillared front with a pediment and two wings, its windows into [windows]. */
internal fun Painter.manor(path: Path, windows: Path, cx: Float, base: Float, size: Float) {
    val wingTop = base - size * 0.22f
    path.addRect(cx - size * 0.5f, wingTop, cx + size * 0.5f, base, Path.Direction.CW)
    path.addRect(cx - size * 0.52f, wingTop - size * 0.02f, cx + size * 0.52f, wingTop, Path.Direction.CW)
    val mainTop = base - size * 0.3f
    path.addRect(cx - size * 0.2f, mainTop, cx + size * 0.2f, base, Path.Direction.CW)
    path.moveTo(cx - size * 0.22f, mainTop)
    path.lineTo(cx, mainTop - size * 0.09f)
    path.lineTo(cx + size * 0.22f, mainTop)
    path.close()
    // Chimneys.
    for (k in listOf(-0.4f, -0.28f, 0.28f, 0.4f)) path.addRect(cx + k * size - size * 0.012f, wingTop - size * 0.07f, cx + k * size + size * 0.012f, wingTop, Path.Direction.CW)
    // Two rows of tall windows; the portico's columns in front.
    for (row in 0 until 2) {
        val wy = base - size * (0.19f - row * 0.1f)
        var f = -0.46f
        while (f < 0.47f) {
            if (f < -0.17f || f > 0.15f || row == 0) windows.addRect(cx + f * size, wy, cx + (f + 0.025f) * size, wy + size * 0.055f, Path.Direction.CW)
            f += 0.06f
        }
    }
    windows.addRect(cx - size * 0.03f, base - size * 0.08f, cx + size * 0.03f, base, Path.Direction.CW)
}

/** A lady in a high-waisted gown and a gentleman in a tailcoat and tall hat, arm in arm. */
internal fun Painter.periodCouple(path: Path, px: Float, py: Float, tall: Float) {
    val head = tall * 0.07f
    // Her: hair up, a long column of a gown.
    val lx = px - head * 1.8f
    val lhy = py - tall * 0.9f + head * 1.2f
    path.addCircle(lx, lhy, head, Path.Direction.CW)
    path.addCircle(lx - head * 0.4f, lhy - head * 0.9f, head * 0.55f, Path.Direction.CW)
    path.moveTo(lx - head * 1.2f, lhy + head * 1.1f)
    path.lineTo(lx + head * 1.2f, lhy + head * 1.1f)
    path.lineTo(lx + head * 1.4f, lhy + head * 2.4f)
    path.lineTo(lx + head * 2.4f, py)
    path.lineTo(lx - head * 2.4f, py)
    path.lineTo(lx - head * 1.4f, lhy + head * 2.4f)
    path.close()
    // Him: the hat, the coat with its tails, the legs.
    val gx = px + head * 1.8f
    val ghy = py - tall + head * 2.2f
    path.addCircle(gx, ghy, head, Path.Direction.CW)
    path.addRect(gx - head * 0.85f, ghy - head * 2.6f, gx + head * 0.85f, ghy - head * 0.6f, Path.Direction.CW)
    path.addRect(gx - head * 1.4f, ghy - head * 0.85f, gx + head * 1.4f, ghy - head * 0.55f, Path.Direction.CW)
    path.moveTo(gx - head * 1.4f, ghy + head * 1.1f)
    path.lineTo(gx + head * 1.4f, ghy + head * 1.1f)
    path.lineTo(gx + head * 1.5f, py - tall * 0.42f)
    path.lineTo(gx + head * 0.2f, py - tall * 0.3f)
    path.lineTo(gx - head * 1.6f, py - tall * 0.3f)
    path.close()
    path.addRect(gx - head * 1.1f, py - tall * 0.42f, gx - head * 0.2f, py, Path.Direction.CW)
    path.addRect(gx + head * 0.2f, py - tall * 0.42f, gx + head * 1.1f, py, Path.Direction.CW)
    // Arm in arm.
    path.addRect(lx + head * 0.6f, lhy + head * 2.4f, gx - head * 0.6f, lhy + head * 3.1f, Path.Direction.CW)
}

/** A palm tree leaning a little, [tall] high. */
internal fun Painter.palm(path: Path, px: Float, py: Float, tall: Float, lean: Float = 0.15f) {
    val tx = px + lean * tall
    val ty = py - tall
    path.moveTo(px - tall * 0.025f, py)
    path.quadTo(px + lean * tall * 0.2f, py - tall * 0.5f, tx - tall * 0.012f, ty)
    path.lineTo(tx + tall * 0.012f, ty)
    path.quadTo(px + lean * tall * 0.3f, py - tall * 0.5f, px + tall * 0.025f, py)
    path.close()
    for (k in 0 until 7) {
        val a = (-160f + k * 23f + rnd.nextFloat() * 8f) * PI.toFloat() / 180f
        val len = tall * (0.32f + rnd.nextFloat() * 0.1f)
        val ex = tx + cos(a) * len
        val ey = ty + sin(a) * len * 0.6f + len * 0.25f
        path.moveTo(tx, ty)
        path.quadTo(tx + cos(a) * len * 0.5f, ty + sin(a) * len * 0.6f - len * 0.12f, ex, ey)
        path.quadTo(tx + cos(a) * len * 0.5f, ty + sin(a) * len * 0.6f, tx, ty + tall * 0.02f)
        path.close()
    }
}

/** A pumpkin with a lit, carved face. */
internal fun Painter.jackOLantern(cx: Float, cy: Float, size: Float, dark: Int) {
    glow(cx, cy, size * 2.2f, rgb(0xFF9A2A), 140)
    for (k in -2..2) {
        val ox = k * size * 0.22f
        c.drawOval(cx + ox - size * 0.32f, cy - size * 0.42f, cx + ox + size * 0.32f, cy + size * 0.42f, fill(mix(rgb(0xE8701A), rgb(0xB8500E), kotlin.math.abs(k) * 0.3f)))
    }
    c.drawRect(cx - size * 0.05f, cy - size * 0.58f, cx + size * 0.06f, cy - size * 0.38f, fill(rgb(0x3A4A1A)))
    val face = fill(rgb(0xFFE070))
    for (side in listOf(-1f, 1f)) {
        c.drawPath(Path().apply { moveTo(cx + side * size * 0.12f, cy - size * 0.05f); lineTo(cx + side * size * 0.3f, cy - size * 0.05f); lineTo(cx + side * size * 0.21f, cy - size * 0.22f); close() }, face)
    }
    c.drawPath(Path().apply {
        moveTo(cx - size * 0.32f, cy + size * 0.08f)
        quadTo(cx, cy + size * 0.42f, cx + size * 0.32f, cy + size * 0.08f)
        lineTo(cx + size * 0.18f, cy + size * 0.16f)
        lineTo(cx + size * 0.08f, cy + size * 0.08f)
        lineTo(cx - size * 0.02f, cy + size * 0.18f)
        lineTo(cx - size * 0.12f, cy + size * 0.08f)
        lineTo(cx - size * 0.22f, cy + size * 0.17f)
        close()
    }, face)
    c.drawLine(cx - size * 0.5f, cy + size * 0.42f, cx + size * 0.5f, cy + size * 0.42f, stroke(alpha(dark, 120), size * 0.04f))
}

/** A lighthouse on its rock, its lamp lit and its beam sweeping out to [beamDir]. */
internal fun Painter.lighthouse(cx: Float, base: Float, size: Float, dark: Int, light: Int, beamDir: Float = 1f) {
    val beam = Path().apply {
        moveTo(cx, base - size * 0.95f)
        lineTo(cx + beamDir * w * 0.9f, base - size * 1.3f)
        lineTo(cx + beamDir * w * 0.9f, base - size * 0.65f)
        close()
    }
    c.drawPath(beam, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(cx, 0f, cx + beamDir * w * 0.9f, 0f, alpha(light, 110), alpha(light, 0), Shader.TileMode.CLAMP)
    })
    c.drawPath(Path().apply {
        moveTo(cx - size * 0.16f, base); lineTo(cx - size * 0.09f, base - size * 0.85f); lineTo(cx + size * 0.09f, base - size * 0.85f); lineTo(cx + size * 0.16f, base); close()
    }, fill(dark))
    c.drawRect(cx - size * 0.08f, base - size * 1.02f, cx + size * 0.08f, base - size * 0.87f, fill(dark))
    c.drawPath(Path().apply { moveTo(cx - size * 0.11f, base - size * 1.02f); lineTo(cx, base - size * 1.12f); lineTo(cx + size * 0.11f, base - size * 1.02f); close() }, fill(dark))
    glow(cx, base - size * 0.95f, size * 0.4f, light, 230)
    disc(cx, base - size * 0.95f, size * 0.05f, Color.WHITE)
    c.drawRect(cx - size * 0.3f, base - size * 0.02f, cx + size * 0.35f, base + size * 0.2f, fill(dark))
}

/** A curling tentacle rising from [base], with suckers along its inner side. */
internal fun Painter.tentacle(bx: Float, base: Float, tall: Float, lean: Float, colour: Int) {
    val tipX = bx + lean * tall * 0.6f
    val tipY = base - tall
    taper(bx, base + u * 2, bx + lean * tall * 0.1f, base - tall * 0.45f, bx - lean * tall * 0.25f, base - tall * 0.8f, tipX, tipY, tall * 0.18f, fill(colour))
    val sucker = alpha(mix(colour, Color.WHITE, 0.35f), 160)
    for (k in 1 until 7) {
        val t = k / 8f
        val m = 1 - t
        val sx = m * m * m * bx + 3 * m * m * t * (bx + lean * tall * 0.1f) + 3 * m * t * t * (bx - lean * tall * 0.25f) + t * t * t * tipX
        val sy = m * m * m * base + 3 * m * m * t * (base - tall * 0.45f) + 3 * m * t * t * (base - tall * 0.8f) + t * t * t * tipY
        disc(sx + lean * tall * 0.03f, sy, tall * 0.018f * (1 - t * 0.6f), sucker)
    }
}

/** A cottage deep in snow, its windows lit. */
internal fun Painter.snowCottage(cx: Float, base: Float, size: Float, wall: Int, light: Int) {
    val house = Path().apply {
        addRect(cx - size * 0.4f, base - size * 0.42f, cx + size * 0.4f, base, Path.Direction.CW)
        moveTo(cx - size * 0.52f, base - size * 0.4f); lineTo(cx, base - size * 0.82f); lineTo(cx + size * 0.52f, base - size * 0.4f); close()
        addRect(cx + size * 0.18f, base - size * 0.8f, cx + size * 0.3f, base - size * 0.5f, Path.Direction.CW)
    }
    c.drawPath(house, fill(wall))
    // Snow on the roof.
    c.drawPath(Path().apply {
        moveTo(cx - size * 0.56f, base - size * 0.38f); lineTo(cx, base - size * 0.86f); lineTo(cx + size * 0.56f, base - size * 0.38f)
        lineTo(cx + size * 0.5f, base - size * 0.36f); quadTo(cx + size * 0.25f, base - size * 0.52f, cx, base - size * 0.76f)
        quadTo(cx - size * 0.25f, base - size * 0.52f, cx - size * 0.5f, base - size * 0.36f); close()
    }, fill(rgb(0xF4F8FF)))
    c.drawRect(cx + size * 0.17f, base - size * 0.84f, cx + size * 0.31f, base - size * 0.8f, fill(rgb(0xF4F8FF)))
    glow(cx, base - size * 0.2f, size * 0.6f, light, 110)
    c.drawRect(cx - size * 0.28f, base - size * 0.3f, cx - size * 0.1f, base - size * 0.14f, fill(light))
    c.drawRect(cx + size * 0.08f, base - size * 0.3f, cx + size * 0.26f, base - size * 0.14f, fill(light))
}

/** A string of little coloured lights from ([x0], [y0]) to ([x1], [y1]), sagging by [sag]. */
internal fun Painter.stringLights(x0: Float, y0: Float, x1: Float, y1: Float, sag: Float, colours: IntArray, n: Int = 14) {
    val wire = Path().apply { moveTo(x0, y0); quadTo((x0 + x1) / 2, (y0 + y1) / 2 + sag * 2, x1, y1) }
    c.drawPath(wire, stroke(alpha(Color.BLACK, 140), u * 0.2f))
    for (k in 1 until n) {
        val t = k / n.toFloat()
        val m = 1 - t
        val bx = m * m * x0 + 2 * m * t * (x0 + x1) / 2 + t * t * x1
        val by = m * m * y0 + 2 * m * t * ((y0 + y1) / 2 + sag * 2) + t * t * y1
        val colour = colours[k % colours.size]
        glow(bx, by + u, u * 3.4f, colour, 150)
        disc(bx, by + u, u * 0.7f, mix(colour, Color.WHITE, 0.5f))
    }
}

/** A rose in bloom: layers of petals curling in, seen a little from above. */
internal fun Painter.rose(cx: Float, cy: Float, size: Float, petal: Int, dark: Int) {
    for (layer in 0 until 4) {
        val rr = size * (1f - layer * 0.22f)
        val n = 5 + layer
        for (k in 0 until n) {
            val a = 2f * PI.toFloat() * k / n + layer * 0.5f
            val px = cx + cos(a) * rr * 0.42f
            val py = cy + sin(a) * rr * 0.32f
            val oval = RectF(px - rr * 0.5f, py - rr * 0.4f, px + rr * 0.5f, py + rr * 0.4f)
            c.drawOval(oval, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, rr * 1.1f, mix(petal, dark, 0.55f), mix(petal, Color.WHITE, 0.12f * (3 - layer)), Shader.TileMode.CLAMP)
            })
            c.drawArc(oval, 200f + k * 13f, 120f, false, stroke(alpha(dark, 110), size * 0.025f))
        }
    }
    // The tight heart of it.
    c.drawArc(RectF(cx - size * 0.16f, cy - size * 0.14f, cx + size * 0.16f, cy + size * 0.12f), 0f, 300f, false, stroke(dark, size * 0.04f))
    c.drawArc(RectF(cx - size * 0.08f, cy - size * 0.07f, cx + size * 0.1f, cy + size * 0.07f), 120f, 260f, false, stroke(dark, size * 0.035f))
}

/** A thorny stem from ([x0], [y0]) curving to ([x1], [y1]). */
internal fun Painter.thornStem(x0: Float, y0: Float, x1: Float, y1: Float, bend: Float, colour: Int, width: Float) {
    val mx = (x0 + x1) / 2 + bend
    val my = (y0 + y1) / 2
    c.drawPath(Path().apply { moveTo(x0, y0); quadTo(mx, my, x1, y1) }, stroke(colour, width))
    val thorn = fill(colour)
    for (k in 1 until 7) {
        val t = k / 7f
        val m = 1 - t
        val tx = m * m * x0 + 2 * m * t * mx + t * t * x1
        val ty = m * m * y0 + 2 * m * t * my + t * t * y1
        val side = if (k % 2 == 0) 1f else -1f
        c.drawPath(Path().apply { moveTo(tx - width, ty); lineTo(tx + side * width * 3.2f, ty - width * 1.6f); lineTo(tx + width, ty - width * 0.6f); close() }, thorn)
    }
}

/** Someone under an umbrella, [tall] high, with company if [two]. */
internal fun Painter.underUmbrella(path: Path, px: Float, py: Float, tall: Float, two: Boolean) {
    if (two) couple(path, px, py, tall) else cloaked(path, px, py, tall)
    val top = py - tall * 1.12f
    val half = tall * (if (two) 0.36f else 0.26f)
    path.moveTo(px - half, top + tall * 0.12f)
    path.quadTo(px - half * 0.95f, top - tall * 0.04f, px, top - tall * 0.06f)
    path.quadTo(px + half * 0.95f, top - tall * 0.04f, px + half, top + tall * 0.12f)
    path.quadTo(px + half * 0.66f, top + tall * 0.07f, px + half * 0.33f, top + tall * 0.11f)
    path.quadTo(px, top + tall * 0.06f, px - half * 0.33f, top + tall * 0.11f)
    path.quadTo(px - half * 0.66f, top + tall * 0.07f, px - half, top + tall * 0.12f)
    path.close()
    path.addRect(px - tall * 0.006f, top - tall * 0.08f, px + tall * 0.006f, top + tall * 0.42f, Path.Direction.CW)
}

/** Soft round lights out of focus. */
internal fun Painter.bokeh(n: Int, area: RectF, colours: IntArray) {
    repeat(n) {
        val bx = area.left + rnd.nextFloat() * area.width()
        val by = area.top + rnd.nextFloat() * area.height()
        val br = u * (2f + rnd.nextFloat() * 5f)
        disc(bx, by, br, alpha(colours[rnd.nextInt(colours.size)], 40 + rnd.nextInt(60)))
    }
}

/**
 * An owl sitting on a branch, its feet at ([px], [py]) and [tall] high, its ear tufts up, looking out. Its outline goes in
 * [path], and the lines of its face, wings and breast in [details] if given. Gives where its eyes are: the left one's x and
 * y, the right one's x and y, and their radius.
 */
internal fun Painter.perchedOwl(path: Path, px: Float, py: Float, tall: Float, details: Path? = null): FloatArray {
    fun ax(f: Float) = px + f * tall
    fun ay(f: Float) = py - f * tall
    path.moveTo(ax(-0.1f), ay(0.03f))
    path.cubicTo(ax(-0.3f), ay(0.1f), ax(-0.35f), ay(0.42f), ax(-0.29f), ay(0.62f))
    path.quadTo(ax(-0.31f), ay(0.76f), ax(-0.27f), ay(0.86f))
    path.lineTo(ax(-0.3f), ay(1.0f))
    path.quadTo(ax(-0.2f), ay(0.93f), ax(-0.12f), ay(0.9f))
    path.quadTo(ax(0f), ay(0.86f), ax(0.12f), ay(0.9f))
    path.quadTo(ax(0.2f), ay(0.93f), ax(0.3f), ay(1.0f))
    path.lineTo(ax(0.27f), ay(0.86f))
    path.quadTo(ax(0.31f), ay(0.76f), ax(0.29f), ay(0.62f))
    path.cubicTo(ax(0.35f), ay(0.42f), ax(0.3f), ay(0.1f), ax(0.1f), ay(0.03f))
    // The tail, hanging down behind the branch.
    path.lineTo(ax(0.07f), ay(-0.13f))
    path.lineTo(ax(-0.07f), ay(-0.13f))
    path.close()
    // Its feet round the branch.
    for (side in listOf(-1f, 1f)) path.addOval(ax(side * 0.08f) - tall * 0.045f, ay(0.06f), ax(side * 0.08f) + tall * 0.045f, ay(-0.025f), Path.Direction.CW)
    details?.apply {
        // The rim of its face round each eye, and its beak.
        val rim = tall * 0.125f
        addArc(ax(-0.11f) - rim, ay(0.72f) - rim, ax(-0.11f) + rim, ay(0.72f) + rim, 300f, -240f)
        addArc(ax(0.11f) - rim, ay(0.72f) - rim, ax(0.11f) + rim, ay(0.72f) + rim, 240f, 240f)
        moveTo(ax(-0.028f), ay(0.68f)); lineTo(ax(0f), ay(0.58f)); lineTo(ax(0.028f), ay(0.68f))
        // The edges of its folded wings.
        for (side in listOf(-1f, 1f)) {
            moveTo(ax(side * 0.27f), ay(0.57f))
            cubicTo(ax(side * 0.19f), ay(0.42f), ax(side * 0.22f), ay(0.2f), ax(side * 0.12f), ay(0.07f))
        }
        // Little marks down its breast.
        for (row in 0 until 4) {
            val fy = 0.5f - row * 0.1f
            val xs = if (row % 2 == 0) floatArrayOf(-0.1f, 0f, 0.1f) else floatArrayOf(-0.05f, 0.05f)
            for (fx in xs) {
                moveTo(ax(fx - 0.026f), ay(fy))
                quadTo(ax(fx), ay(fy - 0.04f), ax(fx + 0.026f), ay(fy))
            }
        }
    }
    return floatArrayOf(ax(-0.11f), ay(0.72f), ax(0.11f), ay(0.72f), tall * 0.062f)
}

/**
 * A raven in flight seen from below, its wings [span] from tip to tip with the long feathers at their tips spread apart like
 * fingers, the middle of its body at ([cx], [cy]) and its head turned to the right ([facing] 1) or the left (-1). Its
 * outline goes in [path], and the lines of its feathers in [details] if given.
 */
internal fun Painter.raven(path: Path, cx: Float, cy: Float, span: Float, facing: Float = 1f, details: Path? = null) {
    fun bx(f: Float) = cx + f * span
    fun by(f: Float) = cy + f * span
    // The pieces are joined into one, so that where they overlap is never left hollow.
    val bird = Path()
    fun join(piece: Path.() -> Unit) {
        bird.op(Path().apply(piece), Path.Op.UNION)
    }
    // The head, its heavy beak to one side.
    join { addCircle(bx(0f), by(-0.15f), span * 0.045f, Path.Direction.CW) }
    join {
        moveTo(bx(facing * 0.02f), by(-0.175f))
        quadTo(bx(facing * 0.08f), by(-0.172f), bx(facing * 0.118f), by(-0.15f))
        lineTo(bx(facing * 0.03f), by(-0.124f))
        close()
    }
    // The body and its long wedge of a tail.
    join {
        moveTo(bx(-0.042f), by(-0.14f))
        cubicTo(bx(-0.075f), by(-0.05f), bx(-0.06f), by(0.08f), bx(-0.03f), by(0.12f))
        lineTo(bx(-0.08f), by(0.29f))
        lineTo(bx(-0.04f), by(0.305f))
        lineTo(bx(0f), by(0.335f))
        lineTo(bx(0.04f), by(0.305f))
        lineTo(bx(0.08f), by(0.29f))
        lineTo(bx(0.03f), by(0.12f))
        cubicTo(bx(0.06f), by(0.08f), bx(0.075f), by(-0.05f), bx(0.042f), by(-0.14f))
        close()
    }
    details?.apply {
        moveTo(bx(0f), by(0.14f)); lineTo(bx(0f), by(0.31f))
        moveTo(bx(-0.025f), by(0.15f)); lineTo(bx(-0.055f), by(0.285f))
        moveTo(bx(0.025f), by(0.15f)); lineTo(bx(0.055f), by(0.285f))
    }
    for (s in listOf(-1f, 1f)) {
        fun wx(f: Float) = cx + s * f * span
        val sx = 0.345f
        val sy = -0.05f
        val ex = 0.04f
        val ey = 0.07f
        val len = kotlin.math.hypot(ex - sx, ey - sy)
        val nx = (ey - sy) / len
        val ny = -(ex - sx) / len
        val n = 6
        // The arm and the inner wing, its back edge scalloped with feathers.
        join {
            moveTo(wx(0.035f), by(-0.085f))
            cubicTo(wx(0.12f), by(-0.2f), wx(0.26f), by(-0.245f), wx(0.36f), by(-0.205f))
            lineTo(wx(0.378f), by(-0.15f))
            lineTo(wx(0.345f), by(-0.05f))
            for (k in 1..n) {
                val mid = (k - 0.5f) / n
                val end = k.toFloat() / n
                quadTo(wx(sx + (ex - sx) * mid + nx * 0.032f), by(sy + (ey - sy) * mid + ny * 0.032f), wx(sx + (ex - sx) * end), by(sy + (ey - sy) * end))
            }
            lineTo(wx(0.03f), by(-0.02f))
            close()
        }
        details?.apply {
            // The coverts along the wing, and the feathers of its back edge.
            moveTo(wx(0.07f), by(-0.07f))
            cubicTo(wx(0.14f), by(-0.15f), wx(0.25f), by(-0.18f), wx(0.33f), by(-0.15f))
            for (k in 1 until n) {
                val f = k.toFloat() / n
                val jx = sx + (ex - sx) * f
                val jy = sy + (ey - sy) * f
                moveTo(wx(jx), by(jy)); lineTo(wx(jx - nx * 0.07f), by(jy - ny * 0.07f))
            }
        }
        // The long feathers at the tip, spread like fingers.
        val angles = floatArrayOf(-26f, -13f, 0f, 12f, 24f, 36f)
        val lengths = floatArrayOf(0.15f, 0.17f, 0.175f, 0.165f, 0.145f, 0.12f)
        for (i in angles.indices) {
            val f = i / (angles.size - 1f)
            val a = Math.toRadians(angles[i].toDouble())
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val fx = 0.355f - 0.02f * f - dx * 0.03f
            val fy = -0.198f + 0.13f * f - dy * 0.03f
            val l = lengths[i] + 0.03f
            val half = 0.017f
            val tip = 0.008f
            join {
                moveTo(wx(fx - dy * half), by(fy + dx * half))
                lineTo(wx(fx + dx * (l - 0.014f) - dy * tip), by(fy + dy * (l - 0.014f) + dx * tip))
                quadTo(wx(fx + dx * (l + 0.004f)), by(fy + dy * (l + 0.004f)), wx(fx + dx * (l - 0.014f) + dy * tip), by(fy + dy * (l - 0.014f) - dx * tip))
                lineTo(wx(fx + dy * half), by(fy - dx * half))
                close()
            }
            details?.apply {
                moveTo(wx(fx + dx * 0.04f), by(fy + dy * 0.04f)); lineTo(wx(fx + dx * (l - 0.03f)), by(fy + dy * (l - 0.03f)))
            }
        }
    }
    path.op(bird, Path.Op.UNION)
}

/**
 * A carved wand from the end of its handle ([x0], [y0]) to its tip ([x1], [y1]), [thick] across the handle: dark [wood],
 * catching [light] along one side.
 */
internal fun Painter.wand(x0: Float, y0: Float, x1: Float, y1: Float, thick: Float, wood: Int, light: Int) {
    val len = kotlin.math.hypot(x1 - x0, y1 - y0)
    val dx = (x1 - x0) / len
    val dy = (y1 - y0) / len
    val nx = -dy
    val ny = dx
    fun half(t: Float): Float = when {
        t < 0.3f -> thick * 0.5f * (0.86f + 0.14f * sin((t - 0.04f) / 0.26f * 3f * PI.toFloat()))
        t < 0.335f -> thick * 0.6f
        else -> thick * (0.36f + (0.12f - 0.36f) * (t - 0.335f) / 0.665f)
    }
    fun px(t: Float, side: Float) = x0 + dx * len * t + nx * half(t) * side
    fun py(t: Float, side: Float) = y0 + dy * len * t + ny * half(t) * side
    val ts = (4..100).map { it / 100f }
    val shape = Path()
    ts.forEachIndexed { i, t -> if (i == 0) shape.moveTo(px(t, -1f), py(t, -1f)) else shape.lineTo(px(t, -1f), py(t, -1f)) }
    shape.quadTo(x1 + dx * thick * 0.15f, y1 + dy * thick * 0.15f, px(1f, 1f), py(1f, 1f))
    ts.reversed().forEach { t -> shape.lineTo(px(t, 1f), py(t, 1f)) }
    shape.close()
    val mx = (x0 + x1) / 2
    val my = (y0 + y1) / 2
    val lit = mix(wood, light, 0.45f)
    val deep = mix(wood, Color.BLACK, 0.45f)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(mx - nx * thick * 0.6f, my - ny * thick * 0.6f, mx + nx * thick * 0.6f, my + ny * thick * 0.6f, intArrayOf(lit, wood, deep), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    // The knob at the end of the handle.
    val kx = x0 + dx * len * 0.02f
    val ky = y0 + dy * len * 0.02f
    c.drawCircle(kx, ky, thick * 0.62f, body)
    c.drawPath(shape, body)
    val cut = stroke(alpha(deep, 230), thick * 0.09f)
    // Rings at the ends of the grip, and the collar.
    for (t in floatArrayOf(0.045f, 0.3f, 0.335f)) c.drawLine(px(t, -1f), py(t, -1f), px(t, 1f), py(t, 1f), cut)
    // A spiral cut round the grip.
    val groove = stroke(alpha(deep, 170), thick * 0.07f)
    for (k in 0 until 6) {
        val t = 0.07f + k * 0.035f
        c.drawLine(px(t, -0.95f), py(t, -0.95f), px(t + 0.03f, 0.95f), py(t + 0.03f, 0.95f), groove)
    }
    // The light along the shaft.
    val shine = stroke(alpha(mix(light, Color.WHITE, 0.4f), 170), thick * 0.08f)
    for (k in 34 until 96 step 2) {
        val t0 = k / 100f
        val t1 = (k + 2) / 100f
        c.drawLine(px(t0, -0.55f), py(t0, -0.55f), px(t1, -0.55f), py(t1, -0.55f), shine.apply { color = alpha(mix(light, Color.WHITE, 0.4f), (60 + 150 * (t0 - 0.34f) / 0.62f).toInt()) })
    }
    c.drawCircle(kx - nx * thick * 0.25f, ky - ny * thick * 0.25f, thick * 0.16f, fill(alpha(mix(light, Color.WHITE, 0.4f), 110)))
}

/** A four-pointed star of light, [size] from its middle to a point. */
internal fun Painter.sparkle(cx: Float, cy: Float, size: Float, colour: Int, turn: Float = 0f) {
    val star = Path()
    for (i in 0 until 8) {
        val a = (PI / 4 * i).toFloat() + turn
        val r = if (i % 2 == 0) size else size * 0.16f
        if (i == 0) star.moveTo(cx + cos(a) * r, cy + sin(a) * r) else star.lineTo(cx + cos(a) * r, cy + sin(a) * r)
    }
    star.close()
    c.drawPath(star, fill(colour))
}

/** A dark feather [long] from its quill to its tip, turned [angle] degrees, rimmed with [edge] so it shows on the dark. */
internal fun Painter.feather(cx: Float, cy: Float, long: Float, angle: Float, colour: Int, edge: Int) {
    c.save()
    c.rotate(angle, cx, cy)
    val l = long
    val vane = Path().apply {
        moveTo(cx - l * 0.3f, cy)
        quadTo(cx - l * 0.05f, cy - l * 0.17f, cx + l * 0.5f, cy - l * 0.01f)
        quadTo(cx + l * 0.1f, cy + l * 0.13f, cx - l * 0.3f, cy + l * 0.01f)
        close()
    }
    c.drawPath(vane, fill(colour))
    c.drawPath(vane, stroke(edge, l * 0.025f))
    // A split in the vane, and the quill.
    c.drawLine(cx + l * 0.05f, cy - l * 0.02f, cx - l * 0.02f, cy - l * 0.13f, stroke(edge, l * 0.02f))
    c.drawLine(cx - l * 0.5f, cy + l * 0.012f, cx + l * 0.46f, cy - l * 0.005f, stroke(edge, l * 0.03f))
    c.restore()
}

// ---- the scenes ----------------------------------------------------------------------------------

internal object MoreScenes {

    /** A gaslit street of tall houses in the fog, and a man in a caped coat and a deerstalker with his pipe. */
    fun detective(p: Painter) {
        p.sky(intArrayOf(rgb(0x1A1712), rgb(0x3E3628), rgb(0x8A7A5E)), floatArrayOf(0f, 0.55f, 1f))
        p.glow(p.x(0.5f), p.y(0.6f), p.w * 0.7f, rgb(0xE8C890), 70)
        // A clock tower far off in the fog.
        val far = rgb(0x5E5442)
        p.c.drawRect(p.x(0.18f), p.y(0.25f), p.x(0.26f), p.y(0.7f), p.fill(far))
        p.c.drawPath(Path().apply { moveTo(p.x(0.17f), p.y(0.25f)); lineTo(p.x(0.22f), p.y(0.14f)); lineTo(p.x(0.27f), p.y(0.25f)); close() }, p.fill(far))
        p.disc(p.x(0.22f), p.y(0.3f), p.w * 0.025f, rgb(0xE8D8A8))
        p.skyline(0.72f, 0.36f, far, alpha(rgb(0xF5C870), 90), dense = 1.4f)
        p.mist(0.66f, 0.2f, rgb(0xB0A080), 150)
        // Near houses with chimney pots.
        val dark = rgb(0x14110C)
        var hx = p.r.left - p.u * 2
        while (hx < p.r.right) {
            val hw = p.w * (0.12f + p.rnd.nextFloat() * 0.06f)
            val top = p.y(0.4f + p.rnd.nextFloat() * 0.12f)
            p.c.drawRect(hx, top, hx + hw, p.y(0.9f), p.fill(dark))
            repeat(2) { k -> p.c.drawRect(hx + hw * (0.2f + k * 0.45f), top - p.u * 4, hx + hw * (0.3f + k * 0.45f), top, p.fill(dark)) }
            for (j in 0 until 3) for (i in 0 until 2) {
                if (p.rnd.nextFloat() < 0.5f) p.c.drawRect(hx + hw * (0.18f + i * 0.4f), top + p.h * (0.05f + j * 0.12f), hx + hw * (0.38f + i * 0.4f), top + p.h * (0.12f + j * 0.12f), p.fill(alpha(rgb(0xF5C870), 170)))
            }
            hx += hw + p.u * 0.5f
        }
        p.mist(0.8f, 0.14f, rgb(0xB0A080), 110)
        p.c.drawRect(p.r.left, p.y(0.88f), p.r.right, p.r.bottom, p.fill(rgb(0x0E0C09)))
        // The cobbles catching the light.
        repeat(40) { p.c.drawOval(p.x(p.rnd.nextFloat()) - p.u, p.y(0.9f + p.rnd.nextFloat() * 0.1f) - p.u * 0.4f, p.x(p.rnd.nextFloat()) + p.u, p.y(0.9f + p.rnd.nextFloat() * 0.1f) + p.u * 0.4f, p.fill(alpha(rgb(0xE8C890), 25))) }
        val light = rgb(0xFFD58A)
        p.streetLamp(p.x(0.74f), p.y(0.5f), p.y(0.95f), light, rgb(0x070604))
        val man = Path()
        p.deerstalker(man, p.x(0.5f), p.y(0.95f), p.h * 0.32f)
        p.c.drawPath(man, p.fill(rgb(0x070604)))
        p.wisp(p.x(0.5f) + p.h * 0.32f * 0.065f * 2.3f, p.y(0.95f) - p.h * 0.32f * 0.84f, p.h * 0.08f, rgb(0xD8D0C0), 70, curl = 0.7f)
    }

    /** A village on a sunny day: cottages, a church spire, flowers, and a black cat on the wall. */
    fun cosyMystery(p: Painter) {
        p.sky(intArrayOf(rgb(0x8EC6E6), rgb(0xBFE0EE), rgb(0xF2EAD2)), floatArrayOf(0f, 0.6f, 1f))
        p.clouds(4, 0.2f, 0.15f, Color.WHITE, 220, puffy = true)
        val hills = rgb(0x8CB878)
        p.layer(p.ridge(0.62f, 0.5f, 0.3f), mix(hills, Color.WHITE, 0.25f), hills, 0.5f, 0.66f)
        // The church.
        val stone = rgb(0x8A8070)
        val cx = p.x(0.72f)
        p.c.drawRect(cx - p.w * 0.12f, p.y(0.5f), cx + p.w * 0.06f, p.y(0.64f), p.fill(stone))
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.13f, p.y(0.5f)); lineTo(cx - p.w * 0.03f, p.y(0.44f)); lineTo(cx + p.w * 0.07f, p.y(0.5f)); close() }, p.fill(mix(stone, Color.BLACK, 0.2f)))
        p.c.drawRect(cx + p.w * 0.04f, p.y(0.38f), cx + p.w * 0.1f, p.y(0.64f), p.fill(stone))
        p.c.drawPath(Path().apply { moveTo(cx + p.w * 0.035f, p.y(0.38f)); lineTo(cx + p.w * 0.07f, p.y(0.22f)); lineTo(cx + p.w * 0.105f, p.y(0.38f)); close() }, p.fill(mix(stone, Color.BLACK, 0.25f)))
        val green = rgb(0x6FA060)
        p.layer(p.ridge(0.74f, 0.64f, 0.25f), mix(green, Color.WHITE, 0.15f), green, 0.64f, 0.76f)
        // Cottages along the lane.
        val walls = intArrayOf(rgb(0xF2E2C2), rgb(0xE8D0B0), rgb(0xF4ECDC))
        for (k in 0 until 3) p.cottage(p.x(0.14f + k * 0.24f), p.y(0.82f), p.w * 0.2f, walls[k], rgb(0x6A4A3A), rgb(0x3A5A6A))
        // The wall, the flowers along it, and the cat.
        p.c.drawRect(p.r.left, p.y(0.86f), p.r.right, p.y(0.9f), p.fill(rgb(0xB0A890)))
        p.c.drawRect(p.r.left, p.y(0.9f), p.r.right, p.r.bottom, p.fill(rgb(0x5A8A4A)))
        repeat(30) {
            val fx = p.x(p.rnd.nextFloat())
            val fy = p.y(0.9f + p.rnd.nextFloat() * 0.08f)
            p.disc(fx, fy, p.u * 0.8f, listOf(rgb(0xE85A7A), rgb(0xF2D04A), rgb(0xFFFFFF), rgb(0xB070D0))[p.rnd.nextInt(4)])
        }
        val kitty = Path()
        p.cat(kitty, p.x(0.8f), p.y(0.86f), p.h * 0.08f, flip = true)
        p.c.drawPath(kitty, p.fill(rgb(0x18161A)))
    }

    /** Rooftops at night, searchlights crossing, a man in a hat at the edge, and the cross of a sight laid over it. */
    fun spy(p: Painter) {
        p.sky(intArrayOf(rgb(0x03080C), rgb(0x0B2028), rgb(0x1E4048)), floatArrayOf(0f, 0.6f, 1f))
        for (k in 0 until 3) {
            val bx = p.x(0.2f + k * 0.3f)
            val ang = (p.rnd.nextFloat() - 0.5f) * 0.5f
            p.c.drawPath(Path().apply { moveTo(bx, p.y(0.8f)); lineTo(bx + p.w * (ang - 0.04f), p.r.top); lineTo(bx + p.w * (ang + 0.04f), p.r.top); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, p.y(0.8f), 0f, p.r.top, alpha(rgb(0xD8F0F0), 70), alpha(rgb(0xD8F0F0), 0), Shader.TileMode.CLAMP)
            })
        }
        p.skyline(0.8f, 0.5f, rgb(0x10262E), alpha(rgb(0xE8F2D8), 130), dense = 1.2f)
        p.skyline(0.97f, 0.2f, rgb(0x050C10), alpha(rgb(0xE8F2D8), 160), dense = 0.8f)
        val dark = rgb(0x020506)
        p.c.drawRect(p.x(0.4f), p.y(0.84f), p.r.right + 2, p.r.bottom + 2, p.fill(dark))
        val man = Path()
        p.glow(p.x(0.66f), p.y(0.66f), p.w * 0.22f, rgb(0xBFE8E8), 70)
        p.inHat(man, p.x(0.66f), p.y(0.84f), p.h * 0.3f)
        p.c.drawPath(man, p.fill(dark))
        // The sight.
        val sx = p.x(0.66f)
        val sy = p.y(0.66f)
        val red = alpha(rgb(0xE83A3A), 170)
        p.c.drawCircle(sx, sy, p.w * 0.16f, p.stroke(red, p.u * 0.35f))
        p.c.drawCircle(sx, sy, p.w * 0.02f, p.stroke(red, p.u * 0.3f))
        p.c.drawLine(sx - p.w * 0.24f, sy, sx - p.w * 0.04f, sy, p.stroke(red, p.u * 0.3f))
        p.c.drawLine(sx + p.w * 0.04f, sy, sx + p.w * 0.24f, sy, p.stroke(red, p.u * 0.3f))
        p.c.drawLine(sx, sy - p.w * 0.24f, sx, sy - p.w * 0.04f, p.stroke(red, p.u * 0.3f))
        p.c.drawLine(sx, sy + p.w * 0.04f, sx, sy + p.w * 0.24f, p.stroke(red, p.u * 0.3f))
    }

    /** Soldiers going over a ridge under a burning sky, bombers overhead, wire and poppies in front. */
    fun war(p: Painter) {
        p.sky(intArrayOf(rgb(0x1E0E0A), rgb(0x6A2414), rgb(0xD0642A), rgb(0xF2B060)), floatArrayOf(0f, 0.4f, 0.72f, 0.86f))
        p.glow(p.x(0.5f), p.y(0.72f), p.w * 0.6f, rgb(0xFFC070), 170)
        // Smoke rising from fires far off.
        repeat(4) { p.wisp(p.x(0.1f + p.rnd.nextFloat() * 0.8f), p.y(0.72f), p.h * 0.4f, rgb(0x2A1A16), 90, curl = 0.6f) }
        val dark = rgb(0x120806)
        val planes = Path()
        repeat(5) { k -> p.bomber(planes, p.x(0.2f + k * 0.15f + (p.rnd.nextFloat() - 0.5f) * 0.05f), p.y(0.38f + (k % 2) * 0.05f), p.w * (0.07f + (k % 3) * 0.01f)) }
        p.c.drawPath(planes, p.fill(alpha(dark, 220)))
        val ridge = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom)
            lineTo(p.r.left - 2, p.y(0.76f))
            quadTo(p.x(0.5f), p.y(0.7f), p.r.right + 2, p.y(0.78f))
            lineTo(p.r.right + 2, p.r.bottom)
            close()
        }
        p.c.drawPath(ridge, p.fill(dark))
        val men = Path()
        val facing = if (p.rnd.nextBoolean()) 1f else -1f
        repeat(6) { k ->
            val fx = p.x(0.1f + k * 0.16f + (p.rnd.nextFloat() - 0.5f) * 0.05f)
            p.soldier(men, fx, p.groundAt(ridge, fx) + p.u * 0.8f, p.h * (0.17f + p.rnd.nextFloat() * 0.03f), facing)
        }
        p.c.drawPath(men, p.fill(dark))
        p.barbedWire(p.y(0.86f), rgb(0x0A0403))
        repeat(16) { p.poppy(p.x(p.rnd.nextFloat()), p.y(0.9f + p.rnd.nextFloat() * 0.08f), p.u * (1.4f + p.rnd.nextFloat() * 1.2f)) }
    }

    /** An army with spears and banners on a hill at sunset, a castle burning beyond. */
    fun knights(p: Painter) {
        p.sky(intArrayOf(rgb(0x241828), rgb(0x7A3A3A), rgb(0xE08048), rgb(0xF6C27A)), floatArrayOf(0f, 0.42f, 0.72f, 0.84f))
        val far = rgb(0x5A3440)
        val farHill = p.ridge(0.68f, 0.56f, 0.4f)
        p.layer(farHill, mix(far, rgb(0xF6C27A), 0.25f), far, 0.56f, 0.7f)
        // The castle on fire.
        val castle = Path()
        p.castle(castle, p.x(0.66f), p.y(0.62f), p.w * 0.3f, null, downTo = p.groundUnder(farHill, p.x(0.66f) - p.w * 0.37f, p.x(0.66f) + p.w * 0.39f) + p.u)
        p.glow(p.x(0.66f), p.y(0.5f), p.w * 0.3f, rgb(0xFF8A30), 200)
        p.c.drawPath(castle, p.fill(rgb(0x2A1418)))
        repeat(3) { p.wisp(p.x(0.6f + p.rnd.nextFloat() * 0.12f), p.y(0.48f), p.h * 0.35f, rgb(0x3A2420), 110, curl = 0.7f) }
        val dark = rgb(0x0E0608)
        val hill = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom); lineTo(p.r.left - 2, p.y(0.76f)); quadTo(p.x(0.4f), p.y(0.7f), p.r.right + 2, p.y(0.8f)); lineTo(p.r.right + 2, p.r.bottom); close()
        }
        p.c.drawPath(hill, p.fill(dark))
        // Rows of spears and banners, and the men under them.
        val army = Path()
        for (row in 0 until 2) {
            var ax = p.r.left + p.u * 2 + row * p.u * 2.5f
            while (ax < p.r.right) {
                val ay = p.y(0.79f + row * 0.05f)
                val tall = p.h * (0.1f + row * 0.02f)
                p.cloaked(army, ax, ay + p.h * 0.05f, tall)
                army.addRect(ax - p.u * 0.2f, ay - tall * 1.4f, ax + p.u * 0.2f, ay, Path.Direction.CW)
                army.moveTo(ax - p.u * 0.6f, ay - tall * 1.4f); army.lineTo(ax, ay - tall * 1.55f); army.lineTo(ax + p.u * 0.6f, ay - tall * 1.4f); army.close()
                ax += p.w * (0.05f + p.rnd.nextFloat() * 0.03f)
            }
        }
        p.c.drawPath(army, p.fill(dark))
        repeat(4) {
            val bx = p.x(0.12f + p.rnd.nextFloat() * 0.76f)
            val by = p.y(0.6f)
            p.c.drawRect(bx - p.u * 0.3f, by, bx + p.u * 0.3f, p.y(0.82f), p.fill(dark))
            p.c.drawPath(Path().apply { moveTo(bx, by); lineTo(bx + p.w * 0.07f, by + p.u * 1.5f); lineTo(bx + p.w * 0.05f, by + p.u * 3.5f); lineTo(bx + p.w * 0.07f, by + p.u * 5.5f); lineTo(bx, by + p.u * 5f); close() }, p.fill(rgb(0x8A1A1A)))
        }
    }

    /** A great house at dusk, its windows lit, and a lady and gentleman walking on the lawn. */
    fun regency(p: Painter) {
        p.sky(intArrayOf(rgb(0x3A4A78), rgb(0x9A88B0), rgb(0xF0BCA8), rgb(0xF8DCC0)), floatArrayOf(0f, 0.42f, 0.74f, 0.86f))
        p.clouds(6, 0.32f, 0.2f, rgb(0xF8DCC0), 70)
        val trees = rgb(0x3E4A52)
        repeat(7) { k ->
            val tx = p.x(k / 6f + (p.rnd.nextFloat() - 0.5f) * 0.06f)
            p.c.drawOval(tx - p.w * 0.1f, p.y(0.46f), tx + p.w * 0.1f, p.y(0.66f), p.fill(mix(trees, rgb(0x9A88B0), 0.3f)))
        }
        val house = Path()
        val windows = Path()
        val stone = rgb(0x6A6470)
        p.manor(house, windows, p.x(0.5f), p.y(0.66f), p.w * 0.78f)
        p.c.drawPath(house, p.fill(stone))
        p.glow(p.x(0.5f), p.y(0.58f), p.w * 0.4f, rgb(0xFFD08A), 70)
        p.c.drawPath(windows, p.fill(rgb(0xFFDA96)))
        // The columns of the portico.
        for (k in 0 until 4) {
            val cx = p.x(0.5f) + (k - 1.5f) * p.w * 0.06f
            p.c.drawRect(cx - p.u * 0.8f, p.y(0.66f) - p.w * 0.78f * 0.3f, cx + p.u * 0.8f, p.y(0.66f), p.fill(mix(stone, Color.WHITE, 0.2f)))
        }
        val lawn = rgb(0x4A5A48)
        p.c.drawRect(p.r.left, p.y(0.66f), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(0.66f), 0f, p.r.bottom, mix(lawn, rgb(0xF0BCA8), 0.2f), mix(lawn, Color.BLACK, 0.4f), Shader.TileMode.CLAMP)
        })
        // The path to the door, and the two of them on it.
        p.c.drawPath(Path().apply { moveTo(p.x(0.47f), p.y(0.66f)); lineTo(p.x(0.53f), p.y(0.66f)); lineTo(p.x(0.7f), p.r.bottom); lineTo(p.x(0.3f), p.r.bottom); close() }, p.fill(alpha(rgb(0xE8D6C0), 90)))
        val pair = Path()
        p.periodCouple(pair, p.x(0.5f), p.y(0.95f), p.h * 0.22f)
        p.c.drawPath(pair, p.fill(rgb(0x1A1620)))
    }

    /** Pyramids at sunset over a river with a sailing boat and palms; or the arches of an arena in ruins. */
    fun ancient(p: Painter) {
        if (p.rnd.nextBoolean()) pyramids(p) else arena(p)
    }

    private fun pyramids(p: Painter) {
        p.sky(intArrayOf(rgb(0x2A1E3A), rgb(0x9A4A4A), rgb(0xF0904A), rgb(0xFAD08A)), floatArrayOf(0f, 0.4f, 0.7f, 0.82f))
        val sunX = p.x(0.6f)
        p.glow(sunX, p.y(0.62f), p.w * 0.5f, rgb(0xFFD890), 200)
        p.disc(sunX, p.y(0.62f), p.u * 10f, rgb(0xFFE4A8))
        val sand = rgb(0xB0703A)
        val shade = rgb(0x6A3A20)
        for ((px, size) in listOf(0.3f to 0.42f, 0.62f to 0.3f, 0.82f to 0.2f)) {
            val cx = p.x(px)
            val base = p.y(0.72f)
            val half = p.w * size / 2
            p.c.drawPath(Path().apply { moveTo(cx - half, base); lineTo(cx, base - half * 0.9f); lineTo(cx + half, base); close() }, p.fill(mix(sand, rgb(0xF0904A), 0.2f)))
            p.c.drawPath(Path().apply { moveTo(cx, base - half * 0.9f); lineTo(cx + half, base); lineTo(cx + half * 0.25f, base); close() }, p.fill(shade))
        }
        p.c.drawRect(p.r.left, p.y(0.72f), p.r.right, p.y(0.8f), p.fill(mix(sand, rgb(0x3A2018), 0.3f)))
        p.c.drawRect(p.r.left, p.y(0.8f), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(0.8f), 0f, p.r.bottom, rgb(0xC07850), rgb(0x2A1820), Shader.TileMode.CLAMP)
        })
        p.waves(0.81f, rgb(0xFAD08A), rows = 5)
        val dark = rgb(0x1A0E0A)
        val sx = p.x(0.4f)
        val sy = p.y(0.86f)
        p.c.drawPath(Path().apply { moveTo(sx - p.w * 0.08f, sy); lineTo(sx + p.w * 0.08f, sy); lineTo(sx + p.w * 0.06f, sy + p.u * 2); lineTo(sx - p.w * 0.06f, sy + p.u * 2); close() }, p.fill(dark))
        p.c.drawPath(Path().apply { moveTo(sx - p.w * 0.06f, sy - p.u); lineTo(sx + p.w * 0.04f, sy - p.h * 0.2f); lineTo(sx + p.w * 0.06f, sy - p.u); close() }, p.fill(dark))
        val palms = Path()
        p.palm(palms, p.x(0.08f), p.y(0.98f), p.h * 0.3f, lean = 0.2f)
        p.palm(palms, p.x(0.9f), p.y(0.99f), p.h * 0.26f, lean = -0.15f)
        p.c.drawPath(palms, p.fill(dark))
    }

    private fun arena(p: Painter) {
        p.sky(intArrayOf(rgb(0x2A3A5E), rgb(0xA87A6A), rgb(0xF2B87A)), floatArrayOf(0f, 0.6f, 0.85f))
        p.glow(p.x(0.5f), p.y(0.7f), p.w * 0.6f, rgb(0xFFD8A0), 160)
        val stone = rgb(0x8A6A54)
        val dark = mix(stone, Color.BLACK, 0.55f)
        val l = p.x(0.06f)
        val r = p.x(0.94f)
        val top = p.y(0.36f)
        val base = p.y(0.82f)
        // Three tiers of arches, broken away to one side.
        val wall = Path().apply {
            moveTo(l, base); lineTo(l, top + p.h * 0.1f); lineTo(p.x(0.3f), top); lineTo(p.x(0.55f), top); lineTo(p.x(0.62f), top + p.h * 0.08f)
            lineTo(p.x(0.75f), top + p.h * 0.12f); lineTo(p.x(0.8f), top + p.h * 0.24f); lineTo(r, top + p.h * 0.28f); lineTo(r, base); close()
        }
        p.c.save()
        p.c.clipPath(wall)
        p.c.drawRect(l, top, r, base, p.fill(stone))
        val tiers = 3
        val tierH = (base - top) / tiers
        for (t in 0 until tiers) {
            var ax = l + p.u * 2
            while (ax < r) {
                val aw = (r - l) / 9
                val ay = top + t * tierH
                p.c.drawRoundRect(ax + aw * 0.15f, ay + tierH * 0.2f, ax + aw * 0.85f, ay + tierH * 1.05f, aw * 0.35f, aw * 0.35f, p.fill(mix(dark, rgb(0xF2B87A), 0.15f)))
                ax += aw
            }
            p.c.drawRect(l, top + t * tierH, r, top + t * tierH + p.u * 1.2f, p.fill(mix(stone, Color.WHITE, 0.15f)))
        }
        p.c.restore()
        p.c.drawRect(p.r.left, base, p.r.right, p.r.bottom, p.fill(mix(stone, Color.BLACK, 0.4f)))
        val pines = Path()
        p.cypress(pines, p.x(0.04f), p.y(0.9f), p.h * 0.3f)
        p.cypress(pines, p.x(0.95f), p.y(0.92f), p.h * 0.26f)
        p.c.drawPath(pines, p.fill(rgb(0x1A2018)))
    }

    /** A temple of the old gods half lost in the jungle, vines hanging and mist in the trees. */
    fun jungle(p: Painter) {
        p.sky(intArrayOf(rgb(0x1A3A2A), rgb(0x5A8A5A), rgb(0xC8D8A0)), floatArrayOf(0f, 0.6f, 1f))
        p.glow(p.x(0.5f), p.y(0.4f), p.w * 0.5f, rgb(0xF6F0C0), 160)
        val far = rgb(0x3A6A4A)
        repeat(10) { p.c.drawOval(p.x(p.rnd.nextFloat()) - p.w * 0.12f, p.y(0.35f + p.rnd.nextFloat() * 0.15f), p.x(p.rnd.nextFloat()) + p.w * 0.12f, p.y(0.65f), p.fill(alpha(far, 200))) }
        // The temple: stepped, with a doorway.
        val stone = rgb(0x5A5A48)
        val cx = p.x(0.5f)
        for (k in 0 until 5) {
            val half = p.w * (0.3f - k * 0.05f)
            val y0 = p.y(0.78f - k * 0.07f)
            p.c.drawRect(cx - half, y0 - p.h * 0.07f, cx + half, y0, p.fill(mix(stone, Color.BLACK, k * 0.05f)))
        }
        p.c.drawRect(cx - p.w * 0.05f, p.y(0.36f), cx + p.w * 0.05f, p.y(0.43f), p.fill(rgb(0x2A2A20)))
        p.c.drawRect(cx - p.w * 0.04f, p.y(0.68f), cx + p.w * 0.04f, p.y(0.78f), p.fill(rgb(0x14140E)))
        p.mist(0.6f, 0.12f, rgb(0xE0E8C0), 120)
        // The near jungle: leaves and hanging vines.
        val leaf = rgb(0x0E2214)
        repeat(18) {
            val lx = p.x(if (p.rnd.nextBoolean()) p.rnd.nextFloat() * 0.25f else 0.75f + p.rnd.nextFloat() * 0.25f)
            val ly = p.y(p.rnd.nextFloat())
            p.c.save()
            p.c.rotate(p.rnd.nextFloat() * 360f, lx, ly)
            p.c.drawOval(lx - p.w * 0.1f, ly - p.w * 0.03f, lx + p.w * 0.1f, ly + p.w * 0.03f, p.fill(leaf))
            p.c.restore()
        }
        repeat(6) {
            val vx = p.x(p.rnd.nextFloat())
            p.c.drawPath(Path().apply { moveTo(vx, p.r.top); quadTo(vx + p.u * 4, p.y(0.2f), vx, p.y(0.2f + p.rnd.nextFloat() * 0.3f)) }, p.stroke(leaf, p.u * 0.5f))
        }
        p.c.drawRect(p.r.left, p.y(0.86f), p.r.right, p.r.bottom, p.fill(leaf))
    }

    /** A couple under one umbrella on a bridge in the rain, the city's lights soft behind them. */
    fun modernRomance(p: Painter) {
        p.sky(intArrayOf(rgb(0x2A2450), rgb(0x8A5A8A), rgb(0xF0A0A0), rgb(0xF8D0B0)), floatArrayOf(0f, 0.4f, 0.7f, 0.86f))
        p.skyline(0.7f, 0.4f, rgb(0x5A4070), alpha(rgb(0xFFE0A0), 150), dense = 1.3f)
        p.bokeh(26, RectF(p.r.left, p.y(0.3f), p.r.right, p.y(0.75f)), intArrayOf(rgb(0xFFD08A), rgb(0xFF9AB0), rgb(0xFFFFFF)))
        p.mist(0.7f, 0.1f, rgb(0xF0A0A0), 80)
        // The river and the bridge's rail.
        p.c.drawRect(p.r.left, p.y(0.72f), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(0.72f), 0f, p.r.bottom, rgb(0x6A4A7A), rgb(0x1A1428), Shader.TileMode.CLAMP)
        })
        val dark = rgb(0x120E1A)
        p.c.drawRect(p.r.left, p.y(0.8f), p.r.right, p.y(0.815f), p.fill(dark))
        var bx = p.r.left
        while (bx < p.r.right) {
            p.c.drawRect(bx, p.y(0.815f), bx + p.u * 0.8f, p.y(0.88f), p.fill(dark))
            bx += p.u * 4
        }
        p.c.drawRect(p.r.left, p.y(0.88f), p.r.right, p.r.bottom, p.fill(dark))
        p.stringLights(p.r.left, p.y(0.3f), p.r.right, p.y(0.26f), p.h * 0.06f, intArrayOf(rgb(0xFFE0A0)), n = 18)
        val pair = Path()
        p.underUmbrella(pair, p.x(0.52f), p.y(0.9f), p.h * 0.28f, two = true)
        p.c.drawPath(pair, p.fill(rgb(0x0A0810)))
        p.rain(120, rgb(0xF8E0E8), slant = 0.08f, strength = 50)
    }

    /** A dark red rose on its thorny stem in moonlight and smoke, a petal falling. */
    fun darkRomance(p: Painter) {
        p.sky(intArrayOf(rgb(0x050203), rgb(0x1A0508), rgb(0x2A0A10)), floatArrayOf(0f, 0.6f, 1f))
        p.glow(p.x(0.5f), p.y(0.4f), p.w * 0.7f, rgb(0x6A1020), 120)
        repeat(4) { p.wisp(p.x(0.2f + p.rnd.nextFloat() * 0.6f), p.y(0.95f), p.h * 0.5f, rgb(0x6A5A60), 50, curl = 0.6f) }
        val red = rgb(0xB0101E)
        val stem = rgb(0x4A1A20)
        val cx = p.x(0.5f)
        val cy = p.y(0.42f)
        p.thornStem(cx, cy + p.w * 0.12f, p.x(0.42f), p.y(1.02f), -p.w * 0.12f, stem, p.u * 1.1f)
        p.thornStem(p.x(0.1f), p.y(0.9f), p.x(0.9f), p.y(0.7f), -p.h * 0.1f, stem, p.u * 0.7f)
        // Leaves.
        for (side in listOf(-1f, 1f)) {
            val lx = cx + side * p.w * 0.1f
            val ly = cy + p.w * 0.28f
            p.c.save()
            p.c.rotate(side * 35f, lx, ly)
            p.c.drawOval(lx - p.w * 0.07f, ly - p.w * 0.025f, lx + p.w * 0.07f, ly + p.w * 0.025f, p.fill(rgb(0x3A1418)))
            p.c.restore()
        }
        p.glow(cx, cy, p.w * 0.3f, red, 120)
        p.rose(cx, cy, p.w * 0.2f, red, rgb(0x2A0006))
        // A petal falling.
        p.c.save()
        p.c.rotate(30f, p.x(0.7f), p.y(0.72f))
        p.c.drawOval(p.x(0.7f) - p.u * 2.5f, p.y(0.72f) - p.u * 1.5f, p.x(0.7f) + p.u * 2.5f, p.y(0.72f) + p.u * 1.5f, p.fill(red))
        p.c.restore()
    }

    /** Inside by a window at night: fairy lights, a steaming mug, a cat asleep and a plant on the sill. */
    fun fluff(p: Painter) {
        val wall = rgb(0xF2DCC8)
        p.c.drawRect(p.r, p.fill(wall))
        p.glow(p.x(0.3f), p.y(0.6f), p.w * 0.7f, rgb(0xFFE0B0), 120)
        // The window and the night in it.
        val win = RectF(p.x(0.14f), p.y(0.1f), p.x(0.86f), p.y(0.66f))
        p.c.save()
        p.c.clipRect(win)
        p.c.drawRect(win, Paint().apply { shader = LinearGradient(0f, win.top, 0f, win.bottom, rgb(0x1A2050), rgb(0x5A4A8A), Shader.TileMode.CLAMP) })
        repeat(40) { p.disc(win.left + p.rnd.nextFloat() * win.width(), win.top + p.rnd.nextFloat() * win.height() * 0.8f, p.u * 0.3f, alpha(Color.WHITE, 180)) }
        p.moon(win.left + win.width() * 0.72f, win.top + win.height() * 0.25f, p.u * 5f, rgb(0xFFF4D8), 120, crescent = 0.45f)
        p.skyline(0.66f, 0.18f, rgb(0x2A2450), alpha(rgb(0xFFD890), 200), dense = 1.2f)
        p.c.restore()
        val frame = p.stroke(rgb(0xFFFFFF), p.u * 2f)
        p.c.drawRect(win, frame)
        p.c.drawLine(win.centerX(), win.top, win.centerX(), win.bottom, p.stroke(Color.WHITE, p.u * 1.2f))
        p.c.drawLine(win.left, win.centerY(), win.right, win.centerY(), p.stroke(Color.WHITE, p.u * 1.2f))
        // Curtains.
        for (side in listOf(-1f, 1f)) {
            val cx = if (side < 0) win.left else win.right
            p.c.drawPath(Path().apply {
                moveTo(cx - side * p.w * 0.02f, win.top - p.u * 3); lineTo(cx + side * p.w * 0.14f, win.top - p.u * 3)
                quadTo(cx + side * p.w * 0.04f, win.centerY(), cx + side * p.w * 0.1f, win.bottom + p.u * 4); lineTo(cx - side * p.w * 0.02f, win.bottom + p.u * 4); close()
            }, p.fill(rgb(0xE88A9A)))
        }
        p.stringLights(win.left - p.u * 4, win.top + p.u * 2, win.right + p.u * 4, win.top + p.u * 2, p.h * 0.05f, intArrayOf(rgb(0xFFE0A0), rgb(0xFFB0C0), rgb(0xB0E0FF)), n = 16)
        // The sill, the mug, the cat and the plant.
        p.c.drawRect(p.x(0.08f), win.bottom, p.x(0.92f), win.bottom + p.h * 0.04f, p.fill(rgb(0xFFFFFF)))
        p.c.drawRect(p.r.left, win.bottom + p.h * 0.04f, p.r.right, p.r.bottom, p.fill(mix(wall, rgb(0xC8A890), 0.4f)))
        val mx = p.x(0.24f)
        val my = win.bottom
        p.c.drawRoundRect(mx - p.u * 3.5f, my - p.u * 8, mx + p.u * 3.5f, my, p.u, p.u, p.fill(rgb(0xF6B6A6)))
        p.c.drawArc(RectF(mx + p.u * 2.5f, my - p.u * 6.5f, mx + p.u * 6f, my - p.u * 2.5f), -90f, 180f, false, p.stroke(rgb(0xF6B6A6), p.u * 1.1f))
        p.wisp(mx, my - p.u * 9, p.h * 0.1f, Color.WHITE, 90, curl = 0.9f)
        val kitty = Path()
        p.cat(kitty, p.x(0.52f), my, p.h * 0.12f)
        p.c.drawPath(kitty, p.fill(rgb(0x3A3040)))
        val px = p.x(0.78f)
        p.c.drawPath(Path().apply { moveTo(px - p.u * 3, my - p.u * 6); lineTo(px + p.u * 3, my - p.u * 6); lineTo(px + p.u * 2.4f, my); lineTo(px - p.u * 2.4f, my); close() }, p.fill(rgb(0xC8784A)))
        repeat(6) { k ->
            val a = (-150f + k * 24f) * PI.toFloat() / 180f
            p.c.drawLine(px, my - p.u * 6, px + cos(a) * p.u * 8, my - p.u * 6 + sin(a) * p.u * 8, p.stroke(rgb(0x5A9A5A), p.u * 1.4f))
        }
    }

    /** A wet street at night in heavy rain, one lamp, and someone alone in its light. */
    fun angst(p: Painter) {
        p.sky(intArrayOf(rgb(0x05080E), rgb(0x141E2C), rgb(0x2A3646)), floatArrayOf(0f, 0.6f, 1f))
        p.skyline(0.74f, 0.4f, rgb(0x182230), alpha(rgb(0xB8C8D8), 60), dense = 1.3f)
        p.mist(0.72f, 0.16f, rgb(0x5A6878), 120)
        p.c.drawRect(p.r.left, p.y(0.86f), p.r.right, p.r.bottom, p.fill(rgb(0x070A10)))
        val light = rgb(0xF6E2B0)
        p.streetLamp(p.x(0.36f), p.y(0.42f), p.y(0.95f), light, rgb(0x020306))
        // Its light in the puddles.
        repeat(6) { p.c.drawOval(p.x(0.2f + p.rnd.nextFloat() * 0.35f), p.y(0.9f + p.rnd.nextFloat() * 0.06f), p.x(0.3f + p.rnd.nextFloat() * 0.35f), p.y(0.91f + p.rnd.nextFloat() * 0.06f), p.fill(alpha(light, 40))) }
        val fig = Path()
        p.underUmbrella(fig, p.x(0.42f), p.y(0.93f), p.h * 0.24f, two = false)
        p.c.drawPath(fig, p.fill(rgb(0x020306)))
        p.rain(320, rgb(0xC8D8E8), slant = 0.12f, strength = 70)
    }

    /** A castle on a crag under a blood-red moon, bats about it, and a wolf howling on the rocks below. */
    fun vampires(p: Painter) {
        p.sky(intArrayOf(rgb(0x0A0306), rgb(0x3A0A12), rgb(0x8A1A20), rgb(0xC8483A)), floatArrayOf(0f, 0.45f, 0.75f, 1f))
        val mx = p.x(0.62f)
        p.moon(mx, p.y(0.3f), p.w * 0.2f, rgb(0xE84A3A), 180)
        p.clouds(5, 0.38f, 0.2f, rgb(0x2A0608), 150)
        val dark = rgb(0x060103)
        val rocks = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom); lineTo(p.r.left - 2, p.y(0.84f)); lineTo(p.x(0.55f), p.y(0.86f)); lineTo(p.x(0.7f), p.y(0.8f))
            lineTo(p.x(0.86f), p.y(0.79f)); lineTo(p.r.right + 2, p.y(0.84f)); lineTo(p.r.right + 2, p.r.bottom); close()
        }
        val castle = Path()
        val windows = Path()
        p.castle(castle, p.x(0.42f), p.y(0.76f), p.w * 0.42f, windows, downTo = p.groundUnder(rocks, p.x(0.42f) - p.w * 0.52f, p.x(0.42f) + p.w * 0.55f) + p.u)
        p.c.drawPath(castle, p.fill(dark))
        p.c.drawPath(windows, p.fill(rgb(0xFFB060)))
        p.bats(12, 0.62f, 0.3f, 0.5f, dark)
        p.c.drawPath(rocks, p.fill(dark))
        val wolf = Path()
        p.howlingWolf(wolf, p.x(0.78f), p.groundAt(rocks, p.x(0.78f)) + p.u * 0.4f, p.h * 0.13f, flip = true)
        p.c.drawPath(wolf, p.fill(dark))
        p.bareTree(p.x(0.1f), p.y(0.86f), p.h * 0.2f, dark, twist = 0.55f)
        p.mist(0.88f, 0.12f, rgb(0x8A3A3A), 110)
    }

    /** Vast tentacles rising from a dark sea under a strange green moon, a lighthouse and a little boat. */
    fun cosmicHorror(p: Painter) {
        p.sky(intArrayOf(rgb(0x020806), rgb(0x0A2A24), rgb(0x2A5A48), rgb(0x6A8A6A)), floatArrayOf(0f, 0.45f, 0.75f, 1f))
        p.stars(70, 0.4f, rgb(0xC8F0D8))
        p.moon(p.x(0.32f), p.y(0.22f), p.w * 0.11f, rgb(0xC8F0B0), 150)
        p.c.drawRect(p.r.left, p.y(0.66f), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(0.66f), 0f, p.r.bottom, rgb(0x1A3A34), rgb(0x020605), Shader.TileMode.CLAMP)
        })
        p.waves(0.68f, rgb(0x8ACCA8), rows = 7)
        val flesh = rgb(0x0E1A16)
        p.tentacle(p.x(0.62f), p.y(0.72f), p.h * 0.5f, -0.6f, flesh)
        p.tentacle(p.x(0.8f), p.y(0.74f), p.h * 0.4f, 0.4f, flesh)
        p.tentacle(p.x(0.48f), p.y(0.76f), p.h * 0.26f, 0.7f, flesh)
        p.tentacle(p.x(0.92f), p.y(0.7f), p.h * 0.22f, -0.5f, flesh)
        p.lighthouse(p.x(0.14f), p.y(0.78f), p.h * 0.3f, rgb(0x030605), rgb(0xF6F2C8), beamDir = 1f)
        // The little boat in the beam.
        val bx = p.x(0.4f)
        val by = p.y(0.84f)
        p.c.drawPath(Path().apply { moveTo(bx - p.u * 4, by); lineTo(bx + p.u * 4, by); lineTo(bx + p.u * 3, by + p.u * 1.5f); lineTo(bx - p.u * 3, by + p.u * 1.5f); close() }, p.fill(rgb(0x030605)))
        p.c.drawPath(Path().apply { moveTo(bx - p.u * 0.5f, by); lineTo(bx - p.u * 0.5f, by - p.u * 6); lineTo(bx + p.u * 3, by - p.u * 0.5f); close() }, p.fill(rgb(0x030605)))
        p.mist(0.72f, 0.1f, rgb(0x6A9A80), 110)
    }

    /** Jack-o'-lanterns on a hill under a huge moon, a witch flying across it, a bare tree and bats. */
    fun halloween(p: Painter) {
        p.sky(intArrayOf(rgb(0x14082A), rgb(0x4A1A4A), rgb(0xD0603A), rgb(0xF2A050)), floatArrayOf(0f, 0.45f, 0.78f, 0.9f))
        p.stars(60, 0.35f)
        val mx = p.x(0.6f)
        val my = p.y(0.32f)
        p.moon(mx, my, p.w * 0.2f, rgb(0xFBE6A8), 170)
        val dark = rgb(0x0C0410)
        val rider = Path()
        p.onBroom(rider, mx, my + p.u * 2, p.w * 0.18f)
        p.c.drawPath(rider, p.fill(dark))
        // A house far off on a hill of its own.
        val farColour = mix(dark, rgb(0x4A1A4A), 0.4f)
        val farHill = p.ridge(0.76f, 0.66f, 0.3f)
        p.c.drawPath(farHill, p.fill(farColour))
        val far = Path()
        val farWin = Path()
        p.oldHouse(far, p.x(0.84f), p.groundUnder(farHill, p.x(0.84f) - p.w * 0.07f, p.x(0.84f) + p.w * 0.09f) + p.u * 0.4f, p.w * 0.16f, farWin)
        p.c.drawPath(far, p.fill(farColour))
        p.c.drawPath(farWin, p.fill(rgb(0xFFB040)))
        val hill = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom); lineTo(p.r.left - 2, p.y(0.76f)); quadTo(p.x(0.45f), p.y(0.7f), p.r.right + 2, p.y(0.8f)); lineTo(p.r.right + 2, p.r.bottom); close()
        }
        p.c.drawPath(hill, p.fill(dark))
        p.bareTree(p.x(0.06f), p.y(0.8f), p.h * 0.18f, dark, twist = 0.6f)
        p.bats(8, 0.4f, 0.22f, 0.45f, dark)
        val fence = p.fill(dark)
        var fx = p.x(0.5f)
        while (fx < p.r.right) {
            p.c.drawPath(Path().apply { moveTo(fx, p.y(0.86f)); lineTo(fx + p.u * 0.7f, p.y(0.83f)); lineTo(fx + p.u * 1.4f, p.y(0.86f)); lineTo(fx + p.u * 1.4f, p.y(0.92f)); lineTo(fx, p.y(0.92f)); close() }, fence)
            fx += p.u * 3.2f
        }
        p.jackOLantern(p.x(0.3f), p.y(0.8f), p.w * 0.1f, dark)
        p.jackOLantern(p.x(0.5f), p.y(0.83f), p.w * 0.075f, dark)
        p.jackOLantern(p.x(0.72f), p.y(0.84f), p.w * 0.085f, dark)
    }

    /** A café at evening, its window warm, lights strung over the street and a bicycle by the door. */
    fun cafe(p: Painter) {
        p.sky(intArrayOf(rgb(0x2A3A6A), rgb(0x7A6A9A), rgb(0xE8A890)), floatArrayOf(0f, 0.6f, 1f))
        val brick = rgb(0x8A4A3A)
        p.c.drawRect(p.r.left, p.y(0.18f), p.r.right, p.y(0.88f), p.fill(brick))
        for (row in 0 until 30) {
            val by = p.y(0.18f + row * 0.024f)
            p.c.drawLine(p.r.left, by, p.r.right, by, p.stroke(alpha(Color.BLACK, 30), p.u * 0.2f))
        }
        // The upstairs windows.
        for (k in 0 until 3) p.c.drawRect(p.x(0.12f + k * 0.3f), p.y(0.24f), p.x(0.28f + k * 0.3f), p.y(0.36f), p.fill(if (k == 1) rgb(0xFFD890) else rgb(0x3A3050)))
        // The awning, striped.
        val aw = Path().apply { moveTo(p.x(0.04f), p.y(0.44f)); lineTo(p.x(0.96f), p.y(0.44f)); lineTo(p.x(1.0f), p.y(0.52f)); lineTo(p.x(0.0f), p.y(0.52f)); close() }
        p.c.save()
        p.c.clipPath(aw)
        var sx = p.r.left
        var odd = false
        while (sx < p.r.right) {
            p.c.drawRect(sx, p.y(0.44f), sx + p.w * 0.06f, p.y(0.52f), p.fill(if (odd) rgb(0xF4ECDC) else rgb(0x2E6A5A)))
            sx += p.w * 0.06f
            odd = !odd
        }
        p.c.restore()
        // The big warm window, someone reading inside.
        val win = RectF(p.x(0.08f), p.y(0.54f), p.x(0.62f), p.y(0.84f))
        p.glow(win.centerX(), win.centerY(), p.w * 0.5f, rgb(0xFFC878), 120)
        p.c.drawRect(win, p.fill(rgb(0xFFD99A)))
        val reader = Path()
        p.cloaked(reader, p.x(0.28f), win.bottom, p.h * 0.18f)
        p.c.drawPath(reader, p.fill(alpha(rgb(0x6A3A20), 180)))
        p.c.drawRect(p.x(0.36f), p.y(0.74f), p.x(0.5f), p.y(0.76f), p.fill(alpha(rgb(0x6A3A20), 160)))
        p.c.drawRect(win, p.stroke(rgb(0x1E1A16), p.u * 1.2f))
        p.c.drawRect(p.x(0.68f), p.y(0.56f), p.x(0.88f), p.y(0.88f), p.fill(rgb(0x2E4A42)))
        p.disc(p.x(0.72f), p.y(0.72f), p.u * 0.8f, rgb(0xE8C870))
        p.c.drawRect(p.r.left, p.y(0.88f), p.r.right, p.r.bottom, p.fill(rgb(0x3A3430)))
        p.stringLights(p.r.left, p.y(0.08f), p.r.right, p.y(0.12f), p.h * 0.05f, intArrayOf(rgb(0xFFE0A0)), n = 16)
        // A bicycle, and flowers in pots.
        val bike = p.stroke(rgb(0x1A1614), p.u * 0.7f)
        val bxx = p.x(0.8f)
        val byy = p.y(0.93f)
        val wr = p.u * 4f
        p.c.drawCircle(bxx - wr * 1.4f, byy, wr, bike)
        p.c.drawCircle(bxx + wr * 1.4f, byy, wr, bike)
        p.c.drawPath(Path().apply { moveTo(bxx - wr * 1.4f, byy); lineTo(bxx - wr * 0.2f, byy - wr * 1.4f); lineTo(bxx + wr * 0.8f, byy - wr * 1.4f); lineTo(bxx + wr * 1.4f, byy); moveTo(bxx - wr * 0.2f, byy - wr * 1.4f); lineTo(bxx, byy); lineTo(bxx + wr * 0.8f, byy - wr * 1.4f) }, bike)
        for (k in 0 until 2) {
            val fx = p.x(0.12f + k * 0.4f)
            p.c.drawRect(fx - p.u * 2.5f, p.y(0.86f), fx + p.u * 2.5f, p.y(0.9f), p.fill(rgb(0xB0603A)))
            repeat(5) { p.disc(fx + (p.rnd.nextFloat() - 0.5f) * p.u * 6, p.y(0.84f) - p.rnd.nextFloat() * p.u * 4, p.u, listOf(rgb(0xE85A7A), rgb(0xF2D04A), rgb(0xFFFFFF))[p.rnd.nextInt(3)]) }
        }
    }

    /** An old college with a clock tower at sunset, trees in blossom along the path. */
    fun school(p: Painter) {
        p.sky(intArrayOf(rgb(0x3A4A7A), rgb(0xB08AA0), rgb(0xF4C898)), floatArrayOf(0f, 0.55f, 0.85f))
        p.clouds(5, 0.25f, 0.2f, rgb(0xF8E0C0), 70)
        val stone = rgb(0x6A5A5A)
        val windowLit = rgb(0xFFD890)
        val cx = p.x(0.5f)
        val base = p.y(0.72f)
        // Two long wings with rows of windows, the tower in the middle.
        p.c.drawRect(p.x(0.04f), base - p.h * 0.18f, p.x(0.96f), base, p.fill(stone))
        for (k in 0 until 26) {
            val f = 0.06f + k * 0.035f
            if (kotlin.math.abs(f - 0.5f) < 0.07f) continue
            for (row in 0 until 2) p.c.drawRect(p.x(f), base - p.h * (0.15f - row * 0.07f), p.x(f + 0.015f), base - p.h * (0.11f - row * 0.07f), p.fill(if (p.rnd.nextFloat() < 0.4f) windowLit else mix(stone, Color.BLACK, 0.4f)))
        }
        for (k in 0 until 16) p.c.drawPath(Path().apply { val gx = p.x(0.06f + k * 0.058f); moveTo(gx, base - p.h * 0.18f); lineTo(gx + p.w * 0.014f, base - p.h * 0.21f); lineTo(gx + p.w * 0.028f, base - p.h * 0.18f); close() }, p.fill(stone))
        p.c.drawRect(cx - p.w * 0.08f, base - p.h * 0.42f, cx + p.w * 0.08f, base, p.fill(mix(stone, Color.BLACK, 0.1f)))
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.09f, base - p.h * 0.42f); lineTo(cx, base - p.h * 0.56f); lineTo(cx + p.w * 0.09f, base - p.h * 0.42f); close() }, p.fill(mix(stone, Color.BLACK, 0.25f)))
        p.disc(cx, base - p.h * 0.34f, p.w * 0.04f, rgb(0xF4ECD8))
        p.c.drawLine(cx, base - p.h * 0.34f, cx, base - p.h * 0.37f, p.stroke(stone, p.u * 0.5f))
        p.c.drawLine(cx, base - p.h * 0.34f, cx + p.w * 0.02f, base - p.h * 0.335f, p.stroke(stone, p.u * 0.5f))
        p.c.drawRoundRect(cx - p.w * 0.03f, base - p.h * 0.1f, cx + p.w * 0.03f, base, p.w * 0.03f, p.w * 0.03f, p.fill(rgb(0x2A2020)))
        p.c.drawRect(p.r.left, base, p.r.right, p.r.bottom, p.fill(rgb(0x5A7A4A)))
        p.c.drawPath(Path().apply { moveTo(cx - p.w * 0.03f, base); lineTo(cx + p.w * 0.03f, base); lineTo(cx + p.w * 0.16f, p.r.bottom); lineTo(cx - p.w * 0.16f, p.r.bottom); close() }, p.fill(rgb(0xD8C8A8)))
        // Blossoming trees either side of the path.
        for (side in listOf(-1f, 1f)) for (k in 0 until 2) {
            val tx = cx + side * p.w * (0.2f + k * 0.18f)
            val ty = p.y(0.86f + k * 0.04f)
            p.c.drawRect(tx - p.u * 0.8f, ty - p.h * 0.1f, tx + p.u * 0.8f, ty, p.fill(rgb(0x4A3A30)))
            repeat(14) { p.disc(tx + (p.rnd.nextFloat() - 0.5f) * p.w * 0.16f, ty - p.h * (0.1f + p.rnd.nextFloat() * 0.1f), p.u * (2f + p.rnd.nextFloat() * 2f), alpha(rgb(0xF6B8CC), 200)) }
        }
    }

    /** A stadium at night under its floodlights, the crowd in the stands and the pitch bright below. */
    fun sports(p: Painter) {
        p.sky(intArrayOf(rgb(0x02040C), rgb(0x0A1430), rgb(0x1E2A50)), floatArrayOf(0f, 0.6f, 1f))
        p.stars(60, 0.3f)
        val light = rgb(0xF6F8FF)
        for (side in listOf(-1f, 1f)) {
            val lx = p.x(0.5f + side * 0.38f)
            p.c.drawRect(lx - p.u * 0.8f, p.y(0.12f), lx + p.u * 0.8f, p.y(0.5f), p.fill(rgb(0x1A2030)))
            p.c.drawRect(lx - p.u * 5f, p.y(0.1f), lx + p.u * 5f, p.y(0.15f), p.fill(rgb(0x2A3040)))
            for (k in 0 until 4) p.disc(lx - p.u * 3.6f + k * p.u * 2.4f, p.y(0.125f), p.u * 0.9f, light)
            p.glow(lx, p.y(0.125f), p.w * 0.3f, light, 160)
            p.c.drawPath(Path().apply { moveTo(lx - p.u * 4, p.y(0.14f)); lineTo(p.x(0.5f) - side * p.w * 0.1f, p.y(0.8f)); lineTo(p.x(0.5f) + side * p.w * 0.3f, p.y(0.8f)); lineTo(lx + p.u * 4, p.y(0.14f)); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, p.y(0.14f), 0f, p.y(0.8f), alpha(light, 60), alpha(light, 5), Shader.TileMode.CLAMP)
            })
        }
        // The stands, full.
        val stand = Path().apply { moveTo(p.r.left, p.y(0.62f)); lineTo(p.x(0.2f), p.y(0.42f)); lineTo(p.x(0.8f), p.y(0.42f)); lineTo(p.r.right, p.y(0.62f)); lineTo(p.r.right, p.y(0.66f)); lineTo(p.r.left, p.y(0.66f)); close() }
        p.c.drawPath(stand, p.fill(rgb(0x1E2436)))
        p.c.save()
        p.c.clipPath(stand)
        repeat(500) { p.disc(p.x(p.rnd.nextFloat()), p.y(0.42f + p.rnd.nextFloat() * 0.24f), p.u * 0.5f, listOf(rgb(0xE84A4A), rgb(0xF6F0E0), rgb(0x4A7AE8), rgb(0xF2D04A))[p.rnd.nextInt(4)]) }
        p.c.restore()
        // The pitch, in stripes, with its lines.
        val pitch = Path().apply { moveTo(p.x(-0.2f), p.r.bottom + 2); lineTo(p.x(0.06f), p.y(0.66f)); lineTo(p.x(0.94f), p.y(0.66f)); lineTo(p.x(1.2f), p.r.bottom + 2); close() }
        p.c.drawPath(pitch, p.fill(rgb(0x2E8A3E)))
        p.c.save()
        p.c.clipPath(pitch)
        for (k in 0 until 8) if (k % 2 == 0) p.c.drawRect(p.r.left, p.y(0.66f + k * 0.045f + k * k * 0.002f), p.r.right, p.y(0.66f + (k + 1) * 0.045f + (k + 1) * (k + 1) * 0.002f), p.fill(alpha(Color.WHITE, 18)))
        p.c.restore()
        val line = p.stroke(alpha(Color.WHITE, 220), p.u * 0.5f)
        p.c.drawPath(Path().apply { moveTo(p.x(0.02f), p.y(0.98f)); lineTo(p.x(0.14f), p.y(0.69f)); lineTo(p.x(0.86f), p.y(0.69f)); lineTo(p.x(0.98f), p.y(0.98f)) }, line)
        p.c.drawLine(p.x(0.5f), p.y(0.69f), p.x(0.5f), p.y(0.98f), line)
        p.c.drawOval(p.x(0.36f), p.y(0.78f), p.x(0.64f), p.y(0.88f), line)
        p.disc(p.x(0.56f), p.y(0.9f), p.u * 1.4f, Color.WHITE)
    }

    /** A stage under coloured lights, a singer at the microphone and the crowd's hands up in front. */
    fun music(p: Painter) {
        p.sky(intArrayOf(rgb(0x05020C), rgb(0x12082A), rgb(0x2A0E3A)), floatArrayOf(0f, 0.6f, 1f))
        val beams = intArrayOf(rgb(0xFF3FA0), rgb(0x3FA0FF), rgb(0xA05BFF), rgb(0xFFD040), rgb(0x3FFFC0))
        repeat(7) { k ->
            val bx = p.x(0.08f + k * 0.14f)
            val colour = beams[k % beams.size]
            val tx = p.x(0.5f + (p.rnd.nextFloat() - 0.5f) * 0.6f)
            p.c.drawPath(Path().apply { moveTo(bx - p.u, p.r.top); lineTo(tx - p.w * 0.08f, p.y(0.75f)); lineTo(tx + p.w * 0.08f, p.y(0.75f)); lineTo(bx + p.u, p.r.top); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, p.r.top, 0f, p.y(0.75f), alpha(colour, 120), alpha(colour, 10), Shader.TileMode.CLAMP)
            })
            p.glow(bx, p.r.top + p.u * 2, p.u * 6, colour, 220)
        }
        p.mist(0.6f, 0.3f, rgb(0x8A6AC8), 70)
        // The stage, and the singer at the microphone.
        p.c.drawRect(p.r.left, p.y(0.72f), p.r.right, p.y(0.76f), p.fill(rgb(0x14081E)))
        val dark = rgb(0x030106)
        val singer = Path()
        val sx = p.x(0.5f)
        val sy = p.y(0.72f)
        p.cloaked(singer, sx, sy, p.h * 0.28f)
        singer.addRect(sx + p.h * 0.04f, sy - p.h * 0.2f, sx + p.h * 0.045f, sy, Path.Direction.CW)
        singer.addOval(sx + p.h * 0.03f, sy - p.h * 0.215f, sx + p.h * 0.055f, sy - p.h * 0.195f, Path.Direction.CW)
        p.c.drawPath(singer, p.fill(dark))
        p.glow(sx, sy - p.h * 0.14f, p.w * 0.25f, Color.WHITE, 60)
        // The crowd.
        val crowd = Path()
        var cx0 = p.r.left - p.u * 2
        while (cx0 < p.r.right + p.u * 2) {
            val head = p.u * (2.6f + p.rnd.nextFloat() * 0.8f)
            val cy0 = p.y(0.86f + p.rnd.nextFloat() * 0.03f)
            crowd.addCircle(cx0, cy0, head, Path.Direction.CW)
            crowd.addRoundRect(cx0 - head * 1.7f, cy0 + head * 0.8f, cx0 + head * 1.7f, p.r.bottom + 2, head, head, Path.Direction.CW)
            if (p.rnd.nextFloat() < 0.45f) {
                val side = if (p.rnd.nextBoolean()) 1f else -1f
                crowd.moveTo(cx0 + side * head, cy0 + head)
                crowd.lineTo(cx0 + side * head * 2.2f, cy0 - head * 3.5f)
                crowd.lineTo(cx0 + side * head * 2.9f, cy0 - head * 3.3f)
                crowd.lineTo(cx0 + side * head * 1.8f, cy0 + head * 1.2f)
                crowd.close()
            }
            cx0 += head * 2.6f
        }
        p.c.drawPath(crowd, p.fill(dark))
    }

    /** A snowy village at night, its windows warm, lights in the trees and the snow still falling. */
    fun christmas(p: Painter) {
        p.sky(intArrayOf(rgb(0x050A24), rgb(0x16285A), rgb(0x3A4A80)), floatArrayOf(0f, 0.6f, 1f))
        p.stars(140, 0.5f)
        p.moon(p.x(0.78f), p.y(0.16f), p.u * 5f, rgb(0xF6F2E8), 110)
        val snowWhite = rgb(0xE8F0FF)
        val snowHill = p.ridge(0.66f, 0.54f, 0.3f)
        p.layer(snowHill, mix(snowWhite, rgb(0x3A4A80), 0.5f), mix(snowWhite, rgb(0x3A4A80), 0.3f), 0.54f, 0.68f)
        // The church on the hill.
        val stone = rgb(0x2A3050)
        val chx = p.x(0.3f)
        val foot = p.groundUnder(snowHill, chx - p.w * 0.07f, chx + p.w * 0.08f) + p.u * 0.5f
        p.c.drawRect(chx - p.w * 0.07f, p.y(0.5f), chx + p.w * 0.07f, foot, p.fill(stone))
        p.c.drawRect(chx + p.w * 0.03f, p.y(0.4f), chx + p.w * 0.08f, foot, p.fill(stone))
        p.c.drawPath(Path().apply { moveTo(chx + p.w * 0.025f, p.y(0.4f)); lineTo(chx + p.w * 0.055f, p.y(0.3f)); lineTo(chx + p.w * 0.085f, p.y(0.4f)); close() }, p.fill(stone))
        p.c.drawRoundRect(chx - p.w * 0.04f, p.y(0.53f), chx - p.w * 0.01f, p.y(0.59f), p.u, p.u, p.fill(rgb(0xFFD890)))
        p.c.drawRoundRect(chx + p.w * 0.0f, p.y(0.53f), chx + p.w * 0.025f, p.y(0.59f), p.u, p.u, p.fill(rgb(0xFFD890)))
        p.c.drawRect(p.r.left, p.y(0.8f), p.r.right, p.r.bottom, p.fill(snowWhite))
        p.layer(p.ridge(0.84f, 0.76f, 0.2f), snowWhite, mix(snowWhite, rgb(0x8AA0D0), 0.3f), 0.76f, 1f)
        val wall = rgb(0x3A2A3A)
        p.snowCottage(p.x(0.2f), p.y(0.84f), p.w * 0.22f, wall, rgb(0xFFD27A))
        p.snowCottage(p.x(0.5f), p.y(0.86f), p.w * 0.26f, wall, rgb(0xFFD27A))
        p.snowCottage(p.x(0.8f), p.y(0.83f), p.w * 0.2f, wall, rgb(0xFFD27A))
        // Pines with coloured lights.
        val pines = Path()
        val treeSpots = listOf(0.06f to 0.9f, 0.36f to 0.92f, 0.66f to 0.9f, 0.95f to 0.92f)
        for ((tx, ty) in treeSpots) p.pine(pines, p.x(tx), p.y(ty), p.h * 0.16f)
        p.c.drawPath(pines, p.fill(rgb(0x0E2A22)))
        val bulbs = intArrayOf(rgb(0xFF4A4A), rgb(0xFFD04A), rgb(0x4AC8FF), rgb(0x6AFF8A))
        for ((tx, ty) in treeSpots) repeat(7) {
            val bx = p.x(tx) + (p.rnd.nextFloat() - 0.5f) * p.h * 0.05f
            val by = p.y(ty) - p.rnd.nextFloat() * p.h * 0.12f
            p.glow(bx, by, p.u * 2f, bulbs[it % bulbs.size], 160)
            p.disc(bx, by, p.u * 0.5f, bulbs[it % bulbs.size])
        }
        p.snow(200)
    }
}
