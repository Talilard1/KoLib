package dev.kolib.app.ui.cover

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The painted scenes for the fantasy and science-fiction genres, and the things in them. Like the rest
 * of the covers' pictures they're this app's own: flat shapes, light and mist, nothing taken from any
 * book, film or game.
 */

private fun rgb(hex: Long) = (0xFF000000 or hex).toInt()
private fun mix(a: Int, b: Int, f: Float) = ColorUtils.blendARGB(a, b, f.coerceIn(0f, 1f))
private fun alpha(c: Int, a: Int) = ColorUtils.setAlphaComponent(c, a.coerceIn(0, 255))

// ---- standing on the ground ------------------------------------------------------------------

/** The top of [shape] at [atX] (pixels): where something standing on it there stands. Below the picture if it doesn't reach. */
internal fun Painter.groundAt(shape: Path, atX: Float): Float {
    val measure = android.graphics.PathMeasure(shape, false)
    val pos = FloatArray(2)
    var best = r.bottom + h
    do {
        val len = measure.length
        val step = kotlin.math.max(1f, u * 0.25f)
        var d = 0f
        var px = Float.NaN
        var py = Float.NaN
        while (d <= len) {
            measure.getPosTan(d, pos, null)
            if (!px.isNaN() && px != pos[0] && (px - atX) * (pos[0] - atX) <= 0f) {
                val y = py + (pos[1] - py) * (atX - px) / (pos[0] - px)
                if (y < best) best = y
            }
            px = pos[0]
            py = pos[1]
            d += step
        }
    } while (measure.nextContour())
    return best
}

/** The lowest the ground under [shape] comes between [fromX] and [toX] (pixels): a base that won't leave anything hanging in the air. */
internal fun Painter.groundUnder(shape: Path, fromX: Float, toX: Float): Float =
    (0..6).maxOf { groundAt(shape, fromX + (toX - fromX) * it / 6f) }

// ---- things to draw ----------------------------------------------------------------------------

/** A moon with a halo: full, or a crescent ([crescent] from 0, full, to about 0.6, thin). */
internal fun Painter.moon(cx: Float, cy: Float, radius: Float, colour: Int, halo: Int = 130, crescent: Float = 0f) {
    glow(cx, cy, radius * 3.4f, colour, halo)
    if (crescent <= 0f) {
        disc(cx, cy, radius, colour)
        return
    }
    val full = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
    val bite = Path().apply { addCircle(cx + radius * crescent * 1.2f, cy - radius * crescent * 0.4f, radius * 0.95f, Path.Direction.CW) }
    full.op(bite, Path.Op.DIFFERENCE)
    c.drawPath(full, fill(colour))
}

/** A smoothed shape through [pts] (x, y pairs), closed. */
internal fun Painter.blob(pts: FloatArray, paint: Paint) {
    val path = Path()
    val n = pts.size / 2
    for (i in 0 until n) {
        val x0 = pts[2 * i]
        val y0 = pts[2 * i + 1]
        val x1 = pts[(2 * i + 2) % pts.size]
        val y1 = pts[(2 * i + 3) % pts.size]
        val mx = (x0 + x1) / 2
        val my = (y0 + y1) / 2
        if (i == 0) path.moveTo(mx, my) else path.quadTo(x0, y0, mx, my)
    }
    path.quadTo(pts[0], pts[1], (pts[0] + pts[2]) / 2, (pts[1] + pts[3]) / 2)
    path.close()
    c.drawPath(path, paint)
}

/** Little lights hanging in the air (fireflies, lanterns far off, sparks of magic) over [area] of the picture. */
internal fun Painter.fireflies(n: Int, area: RectF, colour: Int, size: Float = 1f) {
    repeat(n) {
        val fx = area.left + rnd.nextFloat() * area.width()
        val fy = area.top + rnd.nextFloat() * area.height()
        val s = u * size * (0.25f + rnd.nextFloat() * 0.5f)
        glow(fx, fy, s * 5f, colour, 120)
        disc(fx, fy, s, alpha(mix(colour, Color.WHITE, 0.5f), 230))
    }
}

/** Rain: thin slanting streaks over the whole picture. */
internal fun Painter.rain(n: Int, colour: Int, slant: Float = 0.18f, strength: Int = 70) {
    val s = stroke(alpha(colour, strength), u * 0.18f)
    repeat(n) {
        val rx = x(rnd.nextFloat() * 1.2f - 0.1f)
        val ry = y(rnd.nextFloat())
        val len = h * (0.02f + rnd.nextFloat() * 0.04f)
        c.drawLine(rx, ry, rx - len * slant, ry + len, s)
    }
}

/** Falling snow. */
internal fun Painter.snow(n: Int, colour: Int = Color.WHITE) {
    repeat(n) {
        val big = rnd.nextFloat()
        disc(x(rnd.nextFloat()), y(rnd.nextFloat()), u * (0.2f + big * big * 0.7f), alpha(colour, (120 + 135 * rnd.nextFloat()).toInt()))
    }
}

/** A wisp of smoke or magic rising from ([sx], [sy]), [tall] high, curling as it goes. */
internal fun Painter.wisp(sx: Float, sy: Float, tall: Float, colour: Int, strength: Int = 120, curl: Float = 1f) {
    var px = sx
    var py = sy
    val steps = 26
    for (i in 0 until steps) {
        val t = i / steps.toFloat()
        px += sin(t * 9f * curl + rnd.nextFloat() * 0.3f) * u * 0.8f * curl
        py -= tall / steps
        glow(px, py, u * (1.2f + t * 5f), colour, (strength * (1f - t)).toInt())
    }
}

/** A figure in robes and a pointed hat, [tall] high on ([px], [py]), wand raised; gives where the wand's tip is. */
internal fun Painter.wizard(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f): Pair<Float, Float> {
    return Figures.wizard(path, px, py, tall, facing)
}

/**
 * A great castle of many towers and spires on a crag, as a school of magic might be: [base] is the top
 * of the crag. Its windows are added to [windows] and listed, for their light on the water.
 */
internal fun Painter.academy(path: Path, windows: Path, cx: Float, base: Float, size: Float, water: Float): List<RectF> {
    val lit = ArrayList<RectF>()
    // The crag, down to the water.
    path.moveTo(cx - size * 0.62f, water + u)
    path.lineTo(cx - size * 0.56f, base + size * 0.08f)
    path.lineTo(cx - size * 0.5f, base + size * 0.04f)
    path.lineTo(cx - size * 0.44f, base)
    path.lineTo(cx + size * 0.46f, base)
    path.lineTo(cx + size * 0.52f, base + size * 0.05f)
    path.lineTo(cx + size * 0.6f, base + size * 0.09f)
    path.lineTo(cx + size * 0.7f, water + u)
    path.close()
    fun window(l: Float, t: Float, r: Float, b: Float) {
        if (rnd.nextFloat() < 0.18f) return
        val rect = RectF(l, t, r, b)
        windows.addRect(rect, Path.Direction.CW)
        lit += rect
    }
    fun tower(dx: Float, wide: Float, tall: Float, roof: Float) {
        val tx = cx + dx * size
        val half = wide * size / 2
        val top = base - tall * size
        path.addRect(tx - half, top, tx + half, base + u, Path.Direction.CW)
        path.moveTo(tx - half * 1.25f, top)
        path.lineTo(tx, top - roof * size)
        path.lineTo(tx + half * 1.25f, top)
        path.close()
        var wy = top + half * 1.2f
        while (wy < base - half * 1.4f) {
            window(tx - half * 0.28f, wy, tx + half * 0.28f, wy + half * 0.7f)
            wy += half * 1.9f
        }
    }
    // The great hall, with its tall windows.
    val hallL = cx - size * 0.3f
    val hallR = cx + size * 0.12f
    val hallTop = base - size * 0.2f
    path.addRect(hallL, hallTop, hallR, base + u, Path.Direction.CW)
    path.moveTo(hallL - size * 0.01f, hallTop)
    path.lineTo((hallL + hallR) / 2, hallTop - size * 0.08f)
    path.lineTo(hallR + size * 0.01f, hallTop)
    path.close()
    var hx = hallL + size * 0.03f
    while (hx < hallR - size * 0.04f) {
        window(hx, hallTop + size * 0.04f, hx + size * 0.018f, hallTop + size * 0.14f)
        hx += size * 0.045f
    }
    // Walls and a bridge between the towers.
    path.addRect(cx - size * 0.44f, base - size * 0.1f, cx + size * 0.46f, base + u, Path.Direction.CW)
    tower(-0.42f, 0.06f, 0.26f, 0.09f)
    tower(-0.34f, 0.05f, 0.36f, 0.11f)
    tower(-0.2f, 0.07f, 0.31f, 0.1f)
    tower(-0.06f, 0.05f, 0.42f, 0.13f)
    tower(0.06f, 0.04f, 0.3f, 0.1f)
    tower(0.19f, 0.09f, 0.52f, 0.16f)
    tower(0.31f, 0.05f, 0.33f, 0.1f)
    tower(0.41f, 0.06f, 0.24f, 0.08f)
    return lit
}

/** A broken tower, its top fallen in, with arched holes for windows. */
internal fun Painter.ruinedTower(path: Path, windows: Path, cx: Float, base: Float, size: Float) {
    val half = size * 0.12f
    val top = base - size
    path.moveTo(cx - half * 1.2f, base)
    path.lineTo(cx - half, top + size * 0.12f)
    path.lineTo(cx - half * 0.7f, top + size * 0.04f)
    path.lineTo(cx - half * 0.4f, top + size * 0.1f)
    path.lineTo(cx - half * 0.1f, top)
    path.lineTo(cx + half * 0.3f, top + size * 0.07f)
    path.lineTo(cx + half * 0.6f, top + size * 0.03f)
    path.lineTo(cx + half, top + size * 0.16f)
    path.lineTo(cx + half * 1.2f, base)
    path.close()
    // A lower wall with a broken edge beside it.
    path.moveTo(cx + half * 1.1f, base)
    path.lineTo(cx + half * 1.1f, base - size * 0.32f)
    path.lineTo(cx + half * 2.2f, base - size * 0.26f)
    path.lineTo(cx + half * 2.6f, base - size * 0.3f)
    path.lineTo(cx + half * 3.4f, base - size * 0.12f)
    path.lineTo(cx + half * 3.6f, base)
    path.close()
    for (k in 0 until 3) {
        val wy = top + size * (0.22f + k * 0.22f)
        val ww = half * 0.32f
        windows.addRoundRect(cx - ww, wy, cx + ww, wy + size * 0.11f, ww, ww, Path.Direction.CW)
    }
    windows.addRoundRect(cx + half * 2f, base - size * 0.22f, cx + half * 2.5f, base - size * 0.1f, half * 0.25f, half * 0.25f, Path.Direction.CW)
}

/** Headstones and crosses leaning in long grass, on the ground ([ground] gives where it is at an x) from [from] to [to] across. */
internal fun Painter.headstones(ground: (Float) -> Float, from: Float, to: Float, n: Int, colour: Int) {
    val p = fill(colour)
    repeat(n) {
        val gx = x(from + rnd.nextFloat() * (to - from))
        val gy = ground(gx) + u * 0.5f
        val s = u * (2.2f + rnd.nextFloat() * 2f)
        c.save()
        c.rotate((rnd.nextFloat() - 0.5f) * 18f, gx, gy)
        if (rnd.nextInt(3) == 0) {
            c.drawRect(gx - s * 0.12f, gy - s * 1.6f, gx + s * 0.12f, gy + u, p)
            c.drawRect(gx - s * 0.5f, gy - s * 1.2f, gx + s * 0.5f, gy - s * 0.95f, p)
        } else {
            c.drawRoundRect(gx - s * 0.45f, gy - s * 1.2f, gx + s * 0.45f, gy + u, s * 0.45f, s * 0.45f, p)
        }
        c.restore()
    }
}

/** A crooked little house with a tall chimney; its lit windows go in [windows]. Gives the chimney's top. */
internal fun Painter.crookedCottage(path: Path, windows: Path, cx: Float, base: Float, size: Float): Pair<Float, Float> {
    val l = cx - size * 0.5f
    val r = cx + size * 0.45f
    val eave = base - size * 0.42f
    path.moveTo(l, base)
    path.lineTo(l + size * 0.04f, eave)
    path.lineTo(cx - size * 0.62f, eave + size * 0.02f)
    path.lineTo(cx + size * 0.05f, base - size * 0.95f)
    path.lineTo(r + size * 0.18f, eave - size * 0.04f)
    path.lineTo(r - size * 0.02f, eave)
    path.lineTo(r, base)
    path.close()
    val chimX = cx + size * 0.24f
    path.addRect(chimX - size * 0.05f, base - size * 0.98f, chimX + size * 0.05f, base - size * 0.55f, Path.Direction.CW)
    windows.addRect(cx - size * 0.32f, base - size * 0.32f, cx - size * 0.14f, base - size * 0.16f, Path.Direction.CW)
    windows.addRect(cx + size * 0.04f, base - size * 0.62f, cx + size * 0.16f, base - size * 0.5f, Path.Direction.CW)
    windows.addRoundRect(cx + size * 0.12f, base - size * 0.3f, cx + size * 0.28f, base, size * 0.08f, size * 0.08f, Path.Direction.CW)
    return chimX to base - size * 0.98f
}

/** Someone on a broomstick, cloak flying, [size] long, flying right (or left if [flip]). */
internal fun Painter.onBroom(path: Path, cx: Float, cy: Float, size: Float, flip: Boolean = false) {
    Figures.onBroom(path, cx, cy, size, if (flip) -1f else 1f)
}

/** A cat sitting, seen from the side, [tall] high on ([px], [py]). */
internal fun Painter.cat(path: Path, px: Float, py: Float, tall: Float, flip: Boolean = false) {
    Figures.cat(path, px, py, tall, if (flip) -1f else 1f)
}

/** A wolf on a rock, its head up, howling. */
internal fun Painter.howlingWolf(path: Path, px: Float, py: Float, tall: Float, flip: Boolean = false) {
    Figures.wolf(path, px, py, tall, if (flip) -1f else 1f)
}

/** Two figures close together, one a little taller, [tall] high, standing on ([px], [py]). */
internal fun Painter.couple(path: Path, px: Float, py: Float, tall: Float, gowned: Boolean = true) {
    Figures.couple(path, px, py, tall, gowned)
}

/** A pagoda of [floors] storeys with upturned eaves. */
internal fun Painter.pagoda(path: Path, cx: Float, base: Float, size: Float, floors: Int = 4) {
    var y0 = base
    var wide = size * 0.36f
    val storey = size * 0.2f
    for (k in 0 until floors) {
        path.addRect(cx - wide * 0.6f, y0 - storey, cx + wide * 0.6f, y0, Path.Direction.CW)
        val eave = y0 - storey
        path.moveTo(cx - wide * 1.25f, eave - storey * 0.05f)
        path.quadTo(cx - wide, eave + storey * 0.12f, cx - wide * 0.7f, eave + storey * 0.06f)
        path.lineTo(cx + wide * 0.7f, eave + storey * 0.06f)
        path.quadTo(cx + wide, eave + storey * 0.12f, cx + wide * 1.25f, eave - storey * 0.05f)
        path.quadTo(cx + wide * 0.6f, eave - storey * 0.18f, cx, eave - storey * 0.32f)
        path.quadTo(cx - wide * 0.6f, eave - storey * 0.18f, cx - wide * 1.25f, eave - storey * 0.05f)
        path.close()
        y0 = eave - storey * 0.2f
        wide *= 0.82f
    }
    path.addRect(cx - size * 0.008f, y0 - size * 0.16f, cx + size * 0.008f, y0 + u, Path.Direction.CW)
}

/** A crane in flight, wings up. */
internal fun Painter.crane(path: Path, cx: Float, cy: Float, size: Float, flip: Boolean = false) {
    Figures.crane(path, cx, cy, size, if (flip) -1f else 1f)
}

/** Tall rounded peaks, as in an ink painting of mountains, rising from [base]. */
internal fun Painter.karst(path: Path, cx: Float, base: Float, wide: Float, tall: Float) {
    path.moveTo(cx - wide / 2, base)
    path.cubicTo(cx - wide * 0.46f, base - tall * 0.6f, cx - wide * 0.34f, base - tall, cx - wide * 0.05f, base - tall)
    path.cubicTo(cx + wide * 0.3f, base - tall, cx + wide * 0.42f, base - tall * 0.55f, cx + wide / 2, base)
    path.close()
}

// ---- spaceships ----------------------------------------------------------------------------------

/** A great warship in space, [size] long, its bow towards [dir] (1 right, -1 left); its engines glow [engine]. */
internal fun Painter.capitalShip(cx: Float, cy: Float, size: Float, dir: Float, hull: Int, engine: Int) {
    fun px(f: Float) = cx + dir * f * size
    fun py(f: Float) = cy + f * size
    val body = Path().apply {
        moveTo(px(0.5f), py(0.0f))
        lineTo(px(0.3f), py(-0.05f))
        lineTo(px(0.05f), py(-0.06f))
        lineTo(px(-0.02f), py(-0.12f))
        lineTo(px(-0.16f), py(-0.12f))
        lineTo(px(-0.2f), py(-0.07f))
        lineTo(px(-0.46f), py(-0.07f))
        lineTo(px(-0.5f), py(-0.04f))
        lineTo(px(-0.5f), py(0.05f))
        lineTo(px(-0.3f), py(0.08f))
        lineTo(px(0.2f), py(0.05f))
        close()
    }
    c.drawPath(body, fill(hull))
    // Light along its upper side, and rows of lit ports.
    c.drawPath(Path().apply {
        moveTo(px(0.5f), py(0.0f)); lineTo(px(0.3f), py(-0.05f)); lineTo(px(-0.46f), py(-0.07f)); lineTo(px(-0.46f), py(-0.05f)); lineTo(px(0.3f), py(-0.03f)); close()
    }, fill(alpha(Color.WHITE, 40)))
    val port = fill(alpha(mix(engine, Color.WHITE, 0.6f), 200))
    var f = -0.42f
    while (f < 0.28f) {
        if (rnd.nextFloat() > 0.35f) c.drawRect(px(f) - u * 0.25f, py(0.0f), px(f) + u * 0.25f, py(0.0f) + u * 0.25f, port)
        f += 0.025f
    }
    for (k in 0 until 3) {
        val ey = py(-0.035f + k * 0.035f)
        glow(px(-0.5f), ey, size * 0.06f, engine, 220)
        disc(px(-0.5f), ey, size * 0.012f, mix(engine, Color.WHITE, 0.6f))
    }
}

/** A small fighter craft, its nose towards [dir]. */
internal fun Painter.fighter(cx: Float, cy: Float, size: Float, dir: Float, hull: Int, engine: Int, angle: Float = 0f) {
    c.save()
    c.rotate(angle, cx, cy)
    fun px(f: Float) = cx + dir * f * size
    fun py(f: Float) = cy + f * size
    val body = Path().apply {
        moveTo(px(0.5f), py(0f))
        lineTo(px(-0.1f), py(-0.12f))
        lineTo(px(-0.4f), py(-0.42f))
        lineTo(px(-0.48f), py(-0.4f))
        lineTo(px(-0.32f), py(-0.06f))
        lineTo(px(-0.42f), py(0f))
        lineTo(px(-0.32f), py(0.06f))
        lineTo(px(-0.48f), py(0.4f))
        lineTo(px(-0.4f), py(0.42f))
        lineTo(px(-0.1f), py(0.12f))
        close()
    }
    c.drawPath(body, fill(hull))
    glow(px(-0.44f), py(0f), size * 0.4f, engine, 200)
    disc(px(-0.42f), py(0f), size * 0.06f, mix(engine, Color.WHITE, 0.7f))
    c.restore()
}

/** A bolt of light from a gun, from ([x0], [y0]) to ([x1], [y1]). */
internal fun Painter.bolt(x0: Float, y0: Float, x1: Float, y1: Float, colour: Int) {
    c.drawLine(x0, y0, x1, y1, stroke(alpha(colour, 120), u * 1.2f))
    c.drawLine(x0, y0, x1, y1, stroke(mix(colour, Color.WHITE, 0.7f), u * 0.4f))
}

/** A burst of fire and light: an explosion. */
internal fun Painter.burst(cx: Float, cy: Float, radius: Float, colour: Int) {
    glow(cx, cy, radius * 2.2f, colour, 200)
    val rays = Path()
    val n = 12
    for (i in 0 until n * 2) {
        val a = PI.toFloat() * i / n + rnd.nextFloat() * 0.2f
        val rr = if (i % 2 == 0) radius * (0.8f + rnd.nextFloat() * 0.5f) else radius * 0.35f
        val bx = cx + cos(a) * rr
        val by = cy + sin(a) * rr
        if (i == 0) rays.moveTo(bx, by) else rays.lineTo(bx, by)
    }
    rays.close()
    c.drawPath(rays, fill(alpha(mix(colour, Color.WHITE, 0.4f), 230)))
    disc(cx, cy, radius * 0.3f, Color.WHITE)
}

/** A sleek ship of exploration: a long hull, a ring round its middle, a bright engine; nose towards [dir]. */
internal fun Painter.sleekShip(cx: Float, cy: Float, size: Float, dir: Float, hull: Int, engine: Int, tilt: Float = -8f) {
    c.save()
    c.rotate(tilt * dir, cx, cy)
    fun px(f: Float) = cx + dir * f * size
    fun py(f: Float) = cy + f * size
    val lightHull = mix(hull, Color.WHITE, 0.55f)
    val body = Path().apply {
        moveTo(px(0.5f), py(0f))
        cubicTo(px(0.4f), py(-0.07f), px(0.0f), py(-0.08f), px(-0.4f), py(-0.05f))
        lineTo(px(-0.46f), py(-0.03f))
        lineTo(px(-0.46f), py(0.03f))
        lineTo(px(-0.4f), py(0.05f))
        cubicTo(px(0.0f), py(0.08f), px(0.4f), py(0.07f), px(0.5f), py(0f))
        close()
    }
    c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, py(-0.08f), 0f, py(0.08f), lightHull, hull, Shader.TileMode.CLAMP)
    })
    // The ring, its far half behind the hull drawn faint.
    val rx = px(-0.12f)
    val ring = RectF(rx - size * 0.05f, py(-0.2f), rx + size * 0.05f, py(0.2f))
    c.drawOval(ring, stroke(lightHull, size * 0.022f))
    c.drawArc(ring, -90f, 180f * dir, false, stroke(hull, size * 0.03f))
    // Fins and the engine.
    c.drawPath(Path().apply { moveTo(px(-0.3f), py(-0.04f)); lineTo(px(-0.44f), py(-0.14f)); lineTo(px(-0.4f), py(-0.03f)); close() }, fill(hull))
    c.drawPath(Path().apply { moveTo(px(-0.3f), py(0.04f)); lineTo(px(-0.44f), py(0.14f)); lineTo(px(-0.4f), py(0.03f)); close() }, fill(hull))
    glow(px(-0.48f), py(0f), size * 0.18f, engine, 230)
    disc(px(-0.47f), py(0f), size * 0.025f, Color.WHITE)
    // Lit windows along its side.
    var f = -0.3f
    while (f < 0.36f) {
        disc(px(f), py(0.0f), size * 0.005f, alpha(mix(engine, Color.WHITE, 0.7f), 220))
        f += 0.045f
    }
    c.restore()
}

/** Stars streaking past at speed, away from ([vx], [vy]). */
internal fun Painter.warpStreaks(vx: Float, vy: Float, n: Int, colour: Int) {
    repeat(n) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val d0 = (0.08f + rnd.nextFloat() * 0.6f) * w
        val len = d0 * (0.15f + rnd.nextFloat() * 0.35f)
        val x0 = vx + cos(a) * d0
        val y0 = vy + sin(a) * d0
        c.drawLine(x0, y0, x0 + cos(a) * len, y0 + sin(a) * len, stroke(alpha(colour, (60 + 140 * rnd.nextFloat()).toInt()), u * (0.12f + rnd.nextFloat() * 0.3f)))
    }
}

/** A nebula: clouds of coloured light. */
internal fun Painter.nebula(colours: IntArray, n: Int = 7, top: Float = 0f, bottom: Float = 1f) {
    repeat(n) { k ->
        glow(x(rnd.nextFloat()), y(top + rnd.nextFloat() * (bottom - top)), w * (0.2f + rnd.nextFloat() * 0.35f), colours[k % colours.size], 60 + rnd.nextInt(50))
    }
}

/** An airship: a ribbed envelope, a gondola under it and a propeller behind. */
internal fun Painter.airship(cx: Float, cy: Float, size: Float, body: Int, dark: Int, flip: Boolean = false) {
    val d = if (flip) -1f else 1f
    val env = RectF(cx - size * 0.5f, cy - size * 0.15f, cx + size * 0.5f, cy + size * 0.15f)
    c.drawOval(env, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, env.top, 0f, env.bottom, mix(body, Color.WHITE, 0.25f), mix(body, dark, 0.5f), Shader.TileMode.CLAMP)
    })
    val rib = stroke(alpha(dark, 120), u * 0.3f)
    for (k in -3..3) {
        val rx = cx + k * size * 0.12f
        val half = size * 0.15f * kotlin.math.sqrt((1f - (k * 0.24f) * (k * 0.24f)).coerceAtLeast(0.05f))
        c.drawLine(rx, cy - half, rx, cy + half, rib)
    }
    c.drawLine(env.left + size * 0.04f, cy, env.right - size * 0.04f, cy, rib)
    // Tail fins.
    val tx = cx - d * size * 0.46f
    c.drawPath(Path().apply { moveTo(tx, cy - size * 0.04f); lineTo(tx - d * size * 0.12f, cy - size * 0.2f); lineTo(tx - d * size * 0.12f, cy - size * 0.02f); close() }, fill(dark))
    c.drawPath(Path().apply { moveTo(tx, cy + size * 0.04f); lineTo(tx - d * size * 0.12f, cy + size * 0.2f); lineTo(tx - d * size * 0.12f, cy + size * 0.02f); close() }, fill(dark))
    // The gondola on its ropes.
    val g = RectF(cx - size * 0.16f, cy + size * 0.22f, cx + size * 0.16f, cy + size * 0.29f)
    c.drawRoundRect(g, size * 0.02f, size * 0.02f, fill(dark))
    val rope = stroke(alpha(dark, 200), u * 0.2f)
    c.drawLine(g.left + size * 0.02f, g.top, cx - size * 0.24f, cy + size * 0.1f, rope)
    c.drawLine(g.right - size * 0.02f, g.top, cx + size * 0.24f, cy + size * 0.1f, rope)
    for (k in 0 until 4) c.drawRect(g.left + size * (0.03f + k * 0.07f), g.top + size * 0.015f, g.left + size * (0.06f + k * 0.07f), g.top + size * 0.04f, fill(rgb(0xFFD58A)))
    // The propeller.
    val ppx = g.left - d * size * 0.01f + (if (flip) g.width() + size * 0.02f else 0f)
    c.drawLine(ppx, g.centerY() - size * 0.07f, ppx, g.centerY() + size * 0.07f, stroke(dark, u * 0.6f))
}

// ---- the scenes ----------------------------------------------------------------------------------

internal object Scenes {

    /** A castle of many towers above a lake at night, its windows lit, boats with lanterns crossing and owls about. */
    fun magicSchool(p: Painter) {
        val sky = intArrayOf(rgb(0x070B24), rgb(0x1A2456), rgb(0x433A78), rgb(0x8E6E9E))
        p.sky(sky, floatArrayOf(0f, 0.4f, 0.6f, 0.7f))
        p.stars(240, 0.62f)
        val left = p.rnd.nextBoolean()
        p.moon(p.x(if (left) 0.2f else 0.8f), p.y(0.15f), p.u * 6.5f, rgb(0xF4EEDC), 110, crescent = 0.45f)
        p.clouds(4, 0.34f, 0.18f, rgb(0x6B5E95), 40)
        val far = rgb(0x2A2A5C)
        p.layer(p.ridge(0.64f, 0.46f, 0.5f), mix(far, sky[3], 0.35f), far, 0.46f, 0.66f)
        val water = 0.74f
        // The lake, dark, catching the sky.
        p.c.drawRect(p.r.left, p.y(water), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(water), 0f, p.r.bottom, rgb(0x1B2150), rgb(0x05070F), Shader.TileMode.CLAMP)
        })
        val castle = Path()
        val windows = Path()
        val cx = p.x(if (left) 0.6f else 0.42f)
        val lit = p.academy(castle, windows, cx, p.y(0.62f), p.w * 0.62f, p.y(water))
        val dark = rgb(0x090B1C)
        p.glow(cx, p.y(0.5f), p.w * 0.45f, rgb(0xFFC56E), 50)
        p.c.drawPath(castle, p.fill(dark))
        p.c.drawPath(windows, p.fill(rgb(0xFFD27A)))
        // The windows' light on the water.
        val shine = p.fill(alpha(rgb(0xFFCF78), 70))
        for (wnd in lit) {
            val dy = p.y(water) - wnd.bottom
            val top = p.y(water) + dy * 0.85f
            if (top < p.r.bottom) p.c.drawRect(wnd.left, top, wnd.right, (top + wnd.height() * 1.6f).coerceAtMost(p.r.bottom), shine)
        }
        // Little boats with lanterns, coming across.
        repeat(4) { k ->
            val bx = p.x(0.12f + k * 0.2f + p.rnd.nextFloat() * 0.08f)
            val by = p.y(0.86f + p.rnd.nextFloat() * 0.08f)
            val s = p.u * (2.4f + k * 0.4f)
            p.c.drawPath(Path().apply { moveTo(bx - s, by); lineTo(bx + s, by); lineTo(bx + s * 0.7f, by + s * 0.35f); lineTo(bx - s * 0.7f, by + s * 0.35f); close() }, p.fill(dark))
            p.c.drawLine(bx + s * 0.6f, by, bx + s * 0.6f, by - s * 0.9f, p.stroke(dark, p.u * 0.25f))
            p.glow(bx + s * 0.6f, by - s * 0.9f, s * 2.2f, rgb(0xFFC56E), 170)
            p.disc(bx + s * 0.6f, by - s * 0.9f, s * 0.18f, rgb(0xFFF0C8))
        }
        // The near shores with pines on them, and owls in flight.
        val shoreColour = rgb(0x05060E)
        val shores = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom + 2); lineTo(p.r.left - 2, p.y(0.8f)); quadTo(p.x(0.12f), p.y(0.79f), p.x(0.24f), p.y(0.9f)); lineTo(p.x(0.26f), p.r.bottom + 2); close()
            moveTo(p.r.right + 2, p.r.bottom + 2); lineTo(p.r.right + 2, p.y(0.81f)); quadTo(p.x(0.88f), p.y(0.8f), p.x(0.78f), p.y(0.92f)); lineTo(p.x(0.76f), p.r.bottom + 2); close()
        }
        p.c.drawPath(shores, p.fill(shoreColour))
        val pines = Path()
        for (k in 0 until 6) {
            val f = if (k < 3) 0.02f + p.rnd.nextFloat() * 0.14f else 0.84f + p.rnd.nextFloat() * 0.14f
            p.pine(pines, p.x(f), p.groundAt(shores, p.x(f)) + p.u, p.h * (0.12f + p.rnd.nextFloat() * 0.08f))
        }
        p.c.drawPath(pines, p.fill(shoreColour))
        p.birds(4, if (left) 0.35f else 0.62f, 0.3f, 0.22f, alpha(rgb(0x0B0E22), 230))
        p.mist(0.74f, 0.06f, rgb(0x8E7FB0), 70)
    }

    /** A broken tower over a churchyard under a sickly moon, ravens, and someone casting a spell of green fire. */
    fun darkMagic(p: Painter) {
        val sky = intArrayOf(rgb(0x050806), rgb(0x112018), rgb(0x2A4433), rgb(0x60806A))
        p.sky(sky, floatArrayOf(0f, 0.45f, 0.75f, 1f))
        p.stars(80, 0.5f, rgb(0xCFE6D2))
        val moonX = p.x(0.6f + (p.rnd.nextFloat() - 0.5f) * 0.3f)
        p.moon(moonX, p.y(0.24f), p.w * 0.12f, rgb(0xD9E8CF), 150)
        p.clouds(6, 0.28f, 0.22f, rgb(0x0E1A13), 150)
        val green = rgb(0x5DFF9A)
        val dark = rgb(0x040705)
        val hill = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom)
            lineTo(p.r.left - 2, p.y(0.78f))
            quadTo(p.x(0.5f), p.y(0.66f), p.r.right + 2, p.y(0.8f))
            lineTo(p.r.right + 2, p.r.bottom)
            close()
        }
        p.c.drawPath(hill, p.fill(dark))
        val tower = Path()
        val windows = Path()
        val tx = p.x(0.68f)
        p.ruinedTower(tower, windows, tx, p.groundUnder(hill, tx - p.h * 0.06f, tx + p.h * 0.16f) + p.u * 0.6f, p.h * 0.42f)
        p.glow(tx, p.y(0.5f), p.w * 0.18f, green, 70)
        p.c.drawPath(tower, p.fill(dark))
        p.c.drawPath(windows, p.fill(alpha(green, 220)))
        p.headstones({ x -> p.groundAt(hill, x) }, 0.36f, 0.95f, 9, dark)
        p.bareTree(p.x(0.12f), p.y(0.84f), p.h * 0.2f, dark, twist = 0.55f)
        // Ravens.
        p.birds(7, 0.4f, 0.22f, 0.4f, alpha(dark, 240))
        // The one casting, and the spell rising.
        val mage = Path()
        val (wx, wy) = p.wizard(mage, p.x(0.3f), p.groundAt(hill, p.x(0.3f)) + p.u * 0.6f, p.h * 0.27f)
        p.glow(p.x(0.3f), p.y(0.62f), p.w * 0.16f, green, 60)
        p.c.drawPath(mage, p.fill(rgb(0x020302)))
        p.wisp(wx, wy, p.h * 0.3f, green, 160, curl = 1.4f)
        p.glow(wx, wy, p.u * 9f, green, 230)
        p.disc(wx, wy, p.u * 1.1f, mix(green, Color.WHITE, 0.6f))
        p.mist(0.86f, 0.14f, rgb(0x5E8A6A), 110)
    }

    /** A crooked cottage under a great moon at dusk, someone on a broomstick across it, and a cat on the fence. */
    fun witches(p: Painter) {
        val sky = intArrayOf(rgb(0x1C1030), rgb(0x4A2252), rgb(0xB0505A), rgb(0xF0A060))
        p.sky(sky, floatArrayOf(0f, 0.4f, 0.75f, 1f))
        p.stars(90, 0.45f)
        val mx = p.x(0.62f + (p.rnd.nextFloat() - 0.5f) * 0.12f)
        val my = p.y(0.3f)
        val mr = p.w * 0.17f
        p.moon(mx, my, mr, rgb(0xFBE3B4), 150)
        val dark = rgb(0x120914)
        val rider = Path()
        p.onBroom(rider, mx - mr * 0.1f, my + mr * 0.1f, mr * 1.1f, flip = p.rnd.nextBoolean())
        p.c.drawPath(rider, p.fill(dark))
        val ground = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom)
            lineTo(p.r.left - 2, p.y(0.8f))
            quadTo(p.x(0.4f), p.y(0.74f), p.r.right + 2, p.y(0.84f))
            lineTo(p.r.right + 2, p.r.bottom)
            close()
        }
        p.c.drawPath(ground, p.fill(dark))
        val house = Path()
        val windows = Path()
        val (chimX, chimY) = p.crookedCottage(house, windows, p.x(0.26f), p.groundUnder(ground, p.x(0.26f) - p.w * 0.18f, p.x(0.26f) + p.w * 0.16f) + p.u * 0.6f, p.w * 0.36f)
        p.wisp(chimX, chimY, p.h * 0.2f, rgb(0xC8B0C8), 50, curl = 1.2f)
        p.c.drawPath(house, p.fill(dark))
        p.glow(p.x(0.2f), p.y(0.7f), p.w * 0.12f, rgb(0xFFB54A), 120)
        p.c.drawPath(windows, p.fill(rgb(0xFFC060)))
        // A cauldron by the door, steaming green.
        val cx = p.x(0.5f)
        val cy = p.y(0.86f)
        val s = p.u * 3.2f
        p.wisp(cx, cy - s, p.h * 0.1f, rgb(0x8DFF8A), 120)
        p.c.drawArc(RectF(cx - s * 1.3f, cy - s * 1.2f, cx + s * 1.3f, cy + s * 1.0f), 0f, 180f, true, p.fill(dark))
        p.c.drawRect(cx - s * 1.4f, cy - s * 0.2f, cx + s * 1.4f, cy + s * 0.05f, p.fill(dark))
        // The fence, and the cat on it.
        val fence = p.fill(dark)
        var fx = p.x(0.6f)
        while (fx < p.r.right) {
            p.c.drawRect(fx, p.y(0.8f), fx + p.u * 1.2f, p.y(0.9f), fence)
            fx += p.u * 3.4f
        }
        p.c.drawRect(p.x(0.6f), p.y(0.82f), p.r.right, p.y(0.825f), fence)
        val kitty = Path()
        p.cat(kitty, p.x(0.82f), p.y(0.82f), p.h * 0.07f)
        p.c.drawPath(kitty, fence)
        p.bareTree(p.x(0.92f), p.y(0.88f), p.h * 0.18f, dark, twist = 0.5f)
        p.bats(4, 0.3f, 0.2f, 0.3f, dark)
    }

    /** Travellers on a winding road through the mountains, towards a far tower catching the morning. */
    fun quest(p: Painter) {
        val sky = intArrayOf(rgb(0x2E3D62), rgb(0x8D7A8E), rgb(0xE5A86A), rgb(0xF6DCA2))
        p.sky(sky, floatArrayOf(0f, 0.4f, 0.66f, 0.8f))
        val sunX = p.x(0.5f + (p.rnd.nextFloat() - 0.5f) * 0.3f)
        p.glow(sunX, p.y(0.58f), p.w * 0.6f, rgb(0xFFE2A6), 190)
        p.clouds(6, 0.3f, 0.2f, rgb(0xF6DCA2), 50)
        val haze = sky[3]
        val far = mix(sky[1], sky[0], 0.25f)
        val peaks = p.heights(rough = 0.58f)
        p.layer(p.ridge(0.6f, 0.32f, peaks = peaks), mix(far, haze, 0.15f), far, 0.32f, 0.62f)
        // The tower on the highest peak.
        val top = peaks.indices.maxByOrNull { peaks[it] }!!
        val tx = p.x(top / (peaks.size - 1f))
        val ty = p.y(0.6f + (0.32f - 0.6f) * peaks[top])
        p.c.drawRect(tx - p.u * 0.9f, ty - p.h * 0.1f, tx + p.u * 0.9f, ty + p.u, p.fill(mix(far, Color.BLACK, 0.3f)))
        p.c.drawPath(Path().apply { moveTo(tx - p.u * 1.4f, ty - p.h * 0.1f); lineTo(tx, ty - p.h * 0.14f); lineTo(tx + p.u * 1.4f, ty - p.h * 0.1f); close() }, p.fill(mix(far, Color.BLACK, 0.3f)))
        p.glow(tx, ty - p.h * 0.09f, p.u * 9f, rgb(0xFFE2A6), 240)
        p.disc(tx, ty - p.h * 0.09f, p.u * 0.9f, Color.WHITE)
        p.mist(0.6f, 0.1f, haze, 120)
        val mid = mix(sky[0], sky[1], 0.4f)
        p.layer(p.ridge(0.78f, 0.56f, 0.5f), mix(mid, haze, 0.25f), mid, 0.56f, 0.82f)
        p.mist(0.76f, 0.06f, haze, 80)
        val near = mix(sky[0], Color.BLACK, 0.4f)
        val nearHill = p.ridge(0.86f, 0.79f, 0.3f)
        p.layer(nearHill, near, mix(near, Color.BLACK, 0.3f), 0.79f, 1f)
        // The road, winding up into the hills (on the hill, not over the sky).
        p.c.save()
        p.c.clipPath(nearHill)
        val road = Path().apply {
            moveTo(p.x(0.3f), p.r.bottom + 2)
            cubicTo(p.x(0.55f), p.y(0.95f), p.x(0.62f), p.y(0.9f), p.x(0.48f), p.y(0.86f))
            cubicTo(p.x(0.38f), p.y(0.83f), p.x(0.46f), p.y(0.8f), p.x(0.56f), p.y(0.79f))
            lineTo(p.x(0.57f), p.y(0.795f))
            cubicTo(p.x(0.5f), p.y(0.81f), p.x(0.46f), p.y(0.835f), p.x(0.56f), p.y(0.865f))
            cubicTo(p.x(0.72f), p.y(0.91f), p.x(0.7f), p.y(0.96f), p.x(0.52f), p.r.bottom + 2)
            close()
        }
        p.c.drawPath(road, p.fill(alpha(rgb(0xE8C99A), 150)))
        p.c.restore()
        // The travellers.
        val folk = Path()
        val steps = listOf(0.47f to 0.86f, 0.5f to 0.872f, 0.54f to 0.886f, 0.585f to 0.902f, 0.62f to 0.925f)
        steps.forEachIndexed { k, (fx, fy) -> p.cloaked(folk, p.x(fx), maxOf(p.y(fy), p.groundAt(nearHill, p.x(fx)) + p.u * 0.5f), p.h * (0.07f + k * 0.01f)) }
        p.c.drawPath(folk, p.fill(mix(near, Color.BLACK, 0.5f)))
        val pines = Path()
        for (k in 0 until 6) {
            val px = p.x(if (k < 3) p.rnd.nextFloat() * 0.22f else 0.8f + p.rnd.nextFloat() * 0.2f)
            p.pine(pines, px, p.groundAt(nearHill, px) + p.u * (1f + p.rnd.nextFloat() * 3f), p.h * (0.12f + p.rnd.nextFloat() * 0.1f))
        }
        p.c.drawPath(pines, p.fill(near))
        p.birds(5, 0.5f, 0.24f, 0.3f, alpha(near, 200))
    }

    /** A towering wall of storm bearing down on a broken plain, and one figure with a great sword facing it. */
    fun stormEpic(p: Painter) {
        p.sky(intArrayOf(rgb(0x0B1220), rgb(0x24344A), rgb(0x5D6C7E), rgb(0xB9A890)), floatArrayOf(0f, 0.45f, 0.72f, 0.82f))
        val fromLeft = p.rnd.nextBoolean()
        // The stormwall: heaped cloud from one side, lit at its edges.
        val wallDark = rgb(0x111A28)
        repeat(60) { k ->
            val t = k / 59f
            val bx = p.x(if (fromLeft) -0.1f + t * 0.8f else 1.1f - t * 0.8f) + (p.rnd.nextFloat() - 0.5f) * p.w * 0.1f
            val by = p.y(0.1f + (1 - t) * 0.22f + p.rnd.nextFloat() * 0.3f)
            val br = p.w * (0.07f + (1 - t) * 0.08f + p.rnd.nextFloat() * 0.05f)
            val lit = mix(wallDark, rgb(0x8A9CB4), 0.25f + p.rnd.nextFloat() * 0.3f)
            p.c.drawCircle(bx, by, br, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(bx - br * 0.3f, by - br * 0.5f, br * 1.3f, lit, wallDark, Shader.TileMode.CLAMP)
            })
        }
        // Lightning from the wall.
        repeat(3) {
            var lx = p.x(if (fromLeft) 0.15f + p.rnd.nextFloat() * 0.4f else 0.45f + p.rnd.nextFloat() * 0.4f)
            var ly = p.y(0.3f + p.rnd.nextFloat() * 0.1f)
            val bolt = Path().apply { moveTo(lx, ly) }
            while (ly < p.y(0.68f)) {
                lx += (p.rnd.nextFloat() - 0.5f) * p.u * 7f
                ly += p.u * (2f + p.rnd.nextFloat() * 3f)
                bolt.lineTo(lx, ly)
            }
            p.c.drawPath(bolt, p.stroke(alpha(rgb(0xBFE3FF), 120), p.u * 1.4f))
            p.c.drawPath(bolt, p.stroke(Color.WHITE, p.u * 0.45f))
            p.glow(lx, ly, p.u * 8f, rgb(0xBFE3FF), 120)
        }
        // Rain driven before it.
        p.rain(160, rgb(0xC6D4E6), slant = if (fromLeft) -0.3f else 0.3f, strength = 60)
        // The broken plain: flat-topped rocks with chasms between.
        val rock = rgb(0x3A3530)
        val far = mix(rock, rgb(0x8E8778), 0.4f)
        var px0 = p.r.left - p.u * 2
        while (px0 < p.r.right) {
            val wide = p.w * (0.12f + p.rnd.nextFloat() * 0.2f)
            val top = p.y(0.7f + p.rnd.nextFloat() * 0.04f)
            p.c.drawPath(Path().apply {
                moveTo(px0, p.y(0.8f)); lineTo(px0 + p.u, top); lineTo(px0 + wide - p.u, top + p.u * 0.5f); lineTo(px0 + wide, p.y(0.8f)); close()
            }, p.fill(far))
            px0 += wide + p.w * 0.015f
        }
        val near = rgb(0x1C1916)
        val plateau = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom)
            lineTo(p.r.left - 2, p.y(0.84f))
            lineTo(p.x(0.4f), p.y(0.83f))
            lineTo(p.x(0.43f), p.y(0.9f))
            lineTo(p.x(0.47f), p.y(0.86f))
            lineTo(p.r.right + 2, p.y(0.85f))
            lineTo(p.r.right + 2, p.r.bottom)
            close()
        }
        p.c.drawPath(plateau, p.fill(near))
        // The figure with the great sword.
        val fx = p.x(if (fromLeft) 0.72f else 0.28f)
        val fy = p.groundAt(plateau, fx) + p.u * 0.4f
        val fig = Path()
        val d = if (fromLeft) -1f else 1f
        Figures.cloaked(fig, fx, fy, p.h * 0.17f, facing = d, staff = false)
        p.c.drawPath(fig, p.fill(rgb(0x0B0A09)))
        p.c.save()
        p.c.rotate(-d * 28f, fx, fy - p.h * 0.1f)
        p.c.drawRect(fx - p.u * 0.7f, fy - p.h * 0.32f, fx + p.u * 0.7f, fy - p.h * 0.07f, p.fill(rgb(0x0B0A09)))
        p.c.drawRect(fx - p.u * 2.2f, fy - p.h * 0.075f, fx + p.u * 2.2f, fy - p.h * 0.065f, p.fill(rgb(0x0B0A09)))
        p.c.restore()
        // Glowing gems on the stone.
        repeat(7) {
            val gx = p.x(0.08f + p.rnd.nextFloat() * 0.84f)
            val gy = p.y(0.88f + p.rnd.nextFloat() * 0.1f)
            val gc = listOf(rgb(0x8FD8FF), rgb(0xFFE08A), rgb(0xFF9AD0), rgb(0x9AFFC8))[p.rnd.nextInt(4)]
            p.glow(gx, gy, p.u * 4f, gc, 200)
            p.disc(gx, gy, p.u * 0.6f, mix(gc, Color.WHITE, 0.6f))
        }
    }

    /** A temple on a cliff above the sea, struck by lightning; or a longship under the northern lights. */
    fun mythology(p: Painter) {
        if (p.rnd.nextBoolean()) greekTemple(p) else norseFjord(p)
    }

    private fun greekTemple(p: Painter) {
        p.sky(intArrayOf(rgb(0x101B3A), rgb(0x34407A), rgb(0xC77B5E), rgb(0xF2C27E)), floatArrayOf(0f, 0.45f, 0.75f, 0.85f))
        p.clouds(7, 0.25f, 0.25f, rgb(0x1A2348), 140)
        // Lightning on the temple.
        val tx = p.x(0.62f)
        var lx = p.x(0.5f)
        var ly = p.r.top
        val bolt = Path().apply { moveTo(lx, ly) }
        while (ly < p.y(0.4f)) {
            lx += (tx - lx) * 0.25f + (p.rnd.nextFloat() - 0.5f) * p.u * 6f
            ly += p.u * 3.5f
            bolt.lineTo(lx, ly)
        }
        p.c.drawPath(bolt, p.stroke(alpha(rgb(0xFFF2C0), 140), p.u * 1.6f))
        p.c.drawPath(bolt, p.stroke(Color.WHITE, p.u * 0.5f))
        p.glow(lx, ly, p.w * 0.2f, rgb(0xFFE7A6), 140)
        // The sea and the cliff.
        p.c.drawRect(p.r.left, p.y(0.72f), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(0.72f), 0f, p.r.bottom, rgb(0x6E5A6E), rgb(0x141A33), Shader.TileMode.CLAMP)
        })
        p.waves(0.74f, rgb(0xF2C27E), rows = 6)
        val dark = rgb(0x0E1020)
        val cliff = Path().apply {
            moveTo(p.x(0.3f), p.r.bottom + 2)
            lineTo(p.x(0.36f), p.y(0.6f))
            lineTo(p.x(0.42f), p.y(0.47f))
            lineTo(p.x(0.86f), p.y(0.46f))
            lineTo(p.x(0.92f), p.y(0.58f))
            lineTo(p.r.right + 2, p.y(0.7f))
            lineTo(p.r.right + 2, p.r.bottom + 2)
            close()
        }
        p.c.drawPath(cliff, p.fill(dark))
        // The temple: steps, columns, roof.
        val l = p.x(0.46f)
        val r = p.x(0.82f)
        val base = p.y(0.465f)
        val tall = p.h * 0.12f
        val t = p.fill(dark)
        p.c.drawRect(l - p.u * 2, base - p.u * 1.5f, r + p.u * 2, base + p.u, t)
        val n = 6
        for (k in 0 until n) {
            val cx = l + (r - l) * (k + 0.5f) / n
            p.c.drawRect(cx - p.u * 1.1f, base - tall, cx + p.u * 1.1f, base - p.u * 1.5f, t)
        }
        p.c.drawRect(l - p.u * 2, base - tall - p.u * 2, r + p.u * 2, base - tall, t)
        p.c.drawPath(Path().apply { moveTo(l - p.u * 3, base - tall - p.u * 2); lineTo((l + r) / 2, base - tall - p.h * 0.06f); lineTo(r + p.u * 3, base - tall - p.u * 2); close() }, t)
        val tree = Path()
        p.cypress(tree, p.x(0.4f), p.y(0.48f), p.h * 0.12f)
        p.c.drawPath(tree, t)
    }

    private fun norseFjord(p: Painter) {
        p.sky(intArrayOf(rgb(0x040A18), rgb(0x0C2036), rgb(0x1E4250)), floatArrayOf(0f, 0.55f, 1f))
        p.stars(180, 0.6f)
        // The northern lights: hanging curtains of green and violet.
        repeat(5) { k ->
            val ax = p.x(p.rnd.nextFloat())
            val top = p.y(0.05f + p.rnd.nextFloat() * 0.1f)
            val wide = p.w * (0.25f + p.rnd.nextFloat() * 0.3f)
            val colour = if (k % 3 == 2) rgb(0xB57BFF) else rgb(0x5DFFB0)
            val band = Path().apply {
                moveTo(ax - wide / 2, top + p.h * 0.05f)
                cubicTo(ax - wide / 4, top - p.h * 0.03f, ax + wide / 4, top + p.h * 0.1f, ax + wide / 2, top)
                lineTo(ax + wide / 2, top + p.h * 0.3f)
                cubicTo(ax + wide / 4, top + p.h * 0.38f, ax - wide / 4, top + p.h * 0.26f, ax - wide / 2, top + p.h * 0.34f)
                close()
            }
            p.c.drawPath(band, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, top, 0f, top + p.h * 0.36f, alpha(colour, 120), alpha(colour, 0), Shader.TileMode.CLAMP)
            })
        }
        val dark = rgb(0x050A12)
        val mountains = mix(rgb(0x0E1E2E), rgb(0x2C4A5A), 0.4f)
        p.layer(p.ridge(0.7f, 0.42f, 0.6f), mountains, mix(mountains, dark, 0.5f), 0.42f, 0.72f)
        p.c.drawRect(p.r.left, p.y(0.72f), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(0.72f), 0f, p.r.bottom, rgb(0x15343E), rgb(0x03070C), Shader.TileMode.CLAMP)
        })
        p.waves(0.75f, rgb(0x5DFFB0), rows = 6)
        // The longship: a curving hull with a dragon's neck at the prow, a square sail, shields along the side.
        val sx = p.x(0.5f)
        val sy = p.y(0.84f)
        val s = p.w * 0.36f
        val hull = Path().apply {
            moveTo(sx - s * 0.5f, sy - s * 0.12f)
            quadTo(sx - s * 0.46f, sy + s * 0.04f, sx - s * 0.3f, sy + s * 0.05f)
            lineTo(sx + s * 0.3f, sy + s * 0.05f)
            quadTo(sx + s * 0.46f, sy + s * 0.04f, sx + s * 0.5f, sy - s * 0.12f)
            quadTo(sx + s * 0.56f, sy - s * 0.24f, sx + s * 0.48f, sy - s * 0.26f)
            quadTo(sx + s * 0.52f, sy - s * 0.18f, sx + s * 0.44f, sy - s * 0.04f)
            lineTo(sx - s * 0.44f, sy - s * 0.04f)
            quadTo(sx - s * 0.52f, sy - s * 0.16f, sx - s * 0.46f, sy - s * 0.24f)
            quadTo(sx - s * 0.56f, sy - s * 0.2f, sx - s * 0.5f, sy - s * 0.12f)
            close()
        }
        p.c.drawPath(hull, p.fill(dark))
        p.c.drawRect(sx - s * 0.008f, sy - s * 0.62f, sx + s * 0.008f, sy, p.fill(dark))
        p.c.drawPath(Path().apply {
            moveTo(sx - s * 0.26f, sy - s * 0.56f); lineTo(sx + s * 0.26f, sy - s * 0.56f); quadTo(sx + s * 0.3f, sy - s * 0.3f, sx + s * 0.26f, sy - s * 0.14f)
            lineTo(sx - s * 0.26f, sy - s * 0.14f); quadTo(sx - s * 0.3f, sy - s * 0.3f, sx - s * 0.26f, sy - s * 0.56f); close()
        }, p.fill(rgb(0x2A1A1A)))
        for (k in 0 until 6) p.c.drawRect(sx - s * 0.26f, sy - s * (0.5f - k * 0.07f), sx + s * 0.26f, sy - s * (0.47f - k * 0.07f), p.fill(alpha(rgb(0xB03030), 140)))
        for (k in 0 until 7) p.disc(sx - s * 0.33f + k * s * 0.11f, sy - s * 0.06f, s * 0.035f, rgb(0x2C2420))
    }

    /** A tall tower rising from a forest of briars and roses at twilight, one window lit, fireflies below. */
    fun fairyTale(p: Painter) {
        p.sky(intArrayOf(rgb(0x231D4A), rgb(0x6B4A86), rgb(0xD98AA6), rgb(0xF6C9B8)), floatArrayOf(0f, 0.4f, 0.72f, 0.86f))
        p.stars(110, 0.5f)
        p.moon(p.x(0.24f), p.y(0.18f), p.u * 5f, rgb(0xFFF4DA), 110, crescent = 0.5f)
        p.clouds(5, 0.42f, 0.16f, rgb(0xF6C9B8), 60)
        val far = rgb(0x3E2F62)
        p.layer(p.ridge(0.74f, 0.58f, 0.35f), mix(far, rgb(0xD98AA6), 0.3f), far, 0.58f, 0.76f)
        val dark = rgb(0x150E22)
        // The tower.
        val tx = p.x(0.6f)
        val wide = p.w * 0.075f
        val top = p.y(0.2f)
        val tower = Path().apply {
            addRect(tx - wide / 2, top, tx + wide / 2, p.y(0.9f), Path.Direction.CW)
            moveTo(tx - wide * 0.85f, top); lineTo(tx, top - p.h * 0.12f); lineTo(tx + wide * 0.85f, top); close()
            addRect(tx - wide * 0.65f, top - p.u, tx + wide * 0.65f, top + p.u * 1.5f, Path.Direction.CW)
        }
        p.c.drawPath(tower, p.fill(dark))
        p.glow(tx, top + p.h * 0.05f, p.w * 0.12f, rgb(0xFFD08A), 160)
        p.c.drawRoundRect(tx - wide * 0.18f, top + p.h * 0.03f, tx + wide * 0.18f, top + p.h * 0.08f, wide * 0.18f, wide * 0.18f, p.fill(rgb(0xFFDB98)))
        // The briar wood, with roses.
        val trees = Path()
        for (k in 0 until 9) {
            val bx = p.x(k / 8f + (p.rnd.nextFloat() - 0.5f) * 0.06f)
            val tall = p.h * (0.18f + p.rnd.nextFloat() * 0.12f)
            trees.addOval(bx - p.w * 0.1f, p.y(0.84f) - tall, bx + p.w * 0.1f, p.y(0.84f) + tall * 0.3f, Path.Direction.CW)
        }
        trees.addRect(p.r.left, p.y(0.84f), p.r.right, p.r.bottom, Path.Direction.CW)
        p.c.drawPath(trees, p.fill(dark))
        val vine = p.stroke(rgb(0x2A1830), p.u * 0.5f)
        repeat(8) {
            val vx = tx + (p.rnd.nextFloat() - 0.5f) * wide
            val path = Path().apply { moveTo(vx, p.y(0.86f)); quadTo(vx + (p.rnd.nextFloat() - 0.5f) * wide * 2, p.y(0.7f), vx + (p.rnd.nextFloat() - 0.5f) * wide, p.y(0.55f + p.rnd.nextFloat() * 0.1f)) }
            p.c.drawPath(path, vine)
        }
        repeat(26) {
            val rx = p.x(p.rnd.nextFloat())
            val ry = p.y(0.66f + p.rnd.nextFloat() * 0.3f)
            p.blossom(rx, ry, p.u * (0.8f + p.rnd.nextFloat()), rgb(0xE0577E), rgb(0x8A1E44), p.rnd.nextFloat() * 70f)
        }
        p.fireflies(18, RectF(p.r.left, p.y(0.6f), p.r.right, p.r.bottom), rgb(0xFFE59A))
    }

    /** A moonlit glade among tall trees, two figures close, one with gossamer wings, and floating lights. */
    fun romantasy(p: Painter) {
        val sky = intArrayOf(rgb(0x081428), rgb(0x173A55), rgb(0x3E6E80), rgb(0x9CC6C2))
        p.sky(sky, floatArrayOf(0f, 0.45f, 0.72f, 1f))
        p.stars(120, 0.5f)
        val mx = p.x(0.5f)
        p.moon(mx, p.y(0.3f), p.w * 0.15f, rgb(0xF2F6E8), 170)
        val dark = rgb(0x03080F)
        // Trees arching over the glade from both sides.
        for (side in listOf(-1f, 1f)) {
            val trunk = Path().apply {
                val bx = p.x(0.5f + side * 0.42f)
                moveTo(bx - p.u * 4, p.r.bottom)
                quadTo(bx, p.y(0.5f), bx - side * p.w * 0.2f, p.r.top - 2)
                lineTo(bx - side * p.w * 0.1f, p.r.top - 2)
                quadTo(bx + side * p.u * 6, p.y(0.5f), bx + p.u * 6 * side + p.u * 4, p.r.bottom)
                close()
            }
            p.c.drawPath(trunk, p.fill(dark))
            repeat(14) {
                val lx = p.x(0.5f + side * (0.3f + p.rnd.nextFloat() * 0.3f))
                val ly = p.y(p.rnd.nextFloat() * 0.45f)
                p.c.drawOval(lx - p.w * 0.12f, ly - p.w * 0.06f, lx + p.w * 0.12f, ly + p.w * 0.06f, p.fill(dark))
            }
        }
        val ground = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom)
            lineTo(p.r.left - 2, p.y(0.86f))
            quadTo(p.x(0.5f), p.y(0.8f), p.r.right + 2, p.y(0.86f))
            lineTo(p.r.right + 2, p.r.bottom)
            close()
        }
        p.c.drawPath(ground, p.fill(dark))
        // The two of them, and the wings of the fae one.
        val cx = p.x(0.5f)
        val cy = p.y(0.83f)
        val tall = p.h * 0.26f
        val wing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(rgb(0xBFF4FF), 70) }
        val edge = p.stroke(alpha(rgb(0xE6FCFF), 170), p.u * 0.3f)
        for (side in listOf(-1f, 1f)) {
            val wx = cx + tall * 0.075f
            val wy = cy - tall * 0.72f
            val upper = Path().apply { moveTo(wx, wy); cubicTo(wx + side * tall * 0.2f, wy - tall * 0.35f, wx + side * tall * 0.42f, wy - tall * 0.3f, wx + side * tall * 0.36f, wy - tall * 0.08f); close() }
            val lower = Path().apply { moveTo(wx, wy + tall * 0.04f); cubicTo(wx + side * tall * 0.3f, wy + tall * 0.02f, wx + side * tall * 0.3f, wy + tall * 0.22f, wx + side * tall * 0.12f, wy + tall * 0.24f); close() }
            for (path in listOf(upper, lower)) {
                p.c.drawPath(path, wing)
                p.c.drawPath(path, edge)
            }
        }
        val pair = Path()
        p.couple(pair, cx, cy, tall)
        p.c.drawPath(pair, p.fill(rgb(0x010306)))
        p.fireflies(30, RectF(p.r.left, p.y(0.35f), p.r.right, p.y(0.95f)), rgb(0xFFE6A0), size = 1.1f)
        p.mist(0.86f, 0.08f, sky[3], 90)
    }

    /** A city at night under a huge moon, someone on a rooftop with magic in their hand, a wolf on another. */
    fun urbanFantasy(p: Painter) {
        p.sky(intArrayOf(rgb(0x070618), rgb(0x1C1640), rgb(0x47306A), rgb(0x7E4A78)), floatArrayOf(0f, 0.45f, 0.75f, 1f))
        p.stars(90, 0.5f)
        val mx = p.x(0.4f + p.rnd.nextFloat() * 0.2f)
        p.moon(mx, p.y(0.34f), p.w * 0.22f, rgb(0xF6E9D6), 160)
        p.skyline(0.78f, 0.38f, rgb(0x231A3C), alpha(rgb(0xFFCF7A), 120), dense = 1.3f)
        p.mist(0.74f, 0.12f, rgb(0x7E4A78), 90)
        p.skyline(0.95f, 0.22f, rgb(0x0B0816), alpha(rgb(0xFFCF7A), 190), dense = 0.9f, spires = true)
        val dark = rgb(0x040309)
        val roof = Path().apply { addRect(p.r.left - 2, p.y(0.86f), p.x(0.42f), p.r.bottom + 2, Path.Direction.CW) }
        p.c.drawPath(roof, p.fill(dark))
        p.c.drawRect(p.x(0.65f), p.y(0.8f), p.r.right + 2, p.r.bottom + 2, p.fill(dark))
        // The one with magic.
        val fig = Path()
        val fx = p.x(0.22f)
        p.cloaked(fig, fx, p.y(0.86f), p.h * 0.2f)
        p.c.drawPath(fig, p.fill(dark))
        val hx = fx + p.h * 0.05f
        val hy = p.y(0.86f) - p.h * 0.12f
        val magic = rgb(0x7FC8FF)
        p.wisp(hx, hy, p.h * 0.12f, magic, 160, curl = 1.6f)
        p.glow(hx, hy, p.u * 10f, magic, 220)
        p.disc(hx, hy, p.u * 1.2f, Color.WHITE)
        val wolf = Path()
        p.howlingWolf(wolf, p.x(0.8f), p.y(0.8f), p.h * 0.1f, flip = true)
        p.c.drawPath(wolf, p.fill(dark))
    }

    /** Tall misty peaks in the manner of an ink painting, a pagoda, cranes, a red sun and a swordsman on a ledge. */
    fun wuxia(p: Painter) {
        p.sky(intArrayOf(rgb(0xE7DFC8), rgb(0xD8D2C0), rgb(0xBFC6C2)), floatArrayOf(0f, 0.5f, 1f))
        p.disc(p.x(0.72f), p.y(0.22f), p.w * 0.09f, rgb(0xC8442E))
        p.glow(p.x(0.72f), p.y(0.22f), p.w * 0.2f, rgb(0xE8826A), 80)
        val ink = rgb(0x1E2526)
        val layers = listOf(0.55f to 0.25f, 0.7f to 0.45f, 0.84f to 0.7f)
        for ((k, pair) in layers.withIndex()) {
            val (base, shade) = pair
            val path = Path()
            repeat(4 + k) {
                val cx = p.x(p.rnd.nextFloat() * 1.1f - 0.05f)
                p.karst(path, cx, p.y(base + 0.05f), p.w * (0.14f + p.rnd.nextFloat() * 0.12f), p.h * (0.2f + p.rnd.nextFloat() * 0.18f + k * 0.04f))
            }
            p.c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, p.y(base - 0.35f), 0f, p.y(base + 0.05f), alpha(ink, (255 * shade).toInt()), alpha(ink, (255 * shade * 0.2f).toInt()), Shader.TileMode.CLAMP)
            })
            p.mist(base + 0.04f, 0.1f, rgb(0xE7DFC8), 200)
        }
        // The pagoda on a near peak.
        val peak = Path()
        p.karst(peak, p.x(0.28f), p.y(1.02f), p.w * 0.34f, p.h * 0.36f)
        p.c.drawPath(peak, p.fill(alpha(ink, 235)))
        val pag = Path()
        p.pagoda(pag, p.x(0.27f), p.y(0.67f), p.w * 0.16f, floors = 4)
        p.c.drawPath(pag, p.fill(ink))
        // The swordsman on a ledge, robes blowing.
        val ledge = Path().apply {
            moveTo(p.r.right + 2, p.y(0.86f)); lineTo(p.x(0.66f), p.y(0.88f)); lineTo(p.x(0.7f), p.y(0.93f)); lineTo(p.r.right + 2, p.y(0.95f)); close()
            addRect(p.x(0.7f), p.y(0.93f), p.r.right + 2, p.r.bottom + 2, Path.Direction.CW)
        }
        p.c.drawPath(ledge, p.fill(ink))
        val fig = Path()
        p.cloaked(fig, p.x(0.78f), p.y(0.875f), p.h * 0.13f)
        p.c.drawPath(fig, p.fill(ink))
        p.c.drawLine(p.x(0.74f), p.y(0.81f), p.x(0.7f), p.y(0.76f), p.stroke(ink, p.u * 0.5f))
        val cranes = Path()
        repeat(3) { k -> p.crane(cranes, p.x(0.42f + k * 0.1f), p.y(0.3f + k * 0.04f), p.w * (0.07f - k * 0.012f), flip = true) }
        p.c.drawPath(cranes, p.fill(ink))
    }

    /** A space battle: a great warship by a planet, fighters streaking past, bolts of light and bursts of fire. */
    fun spaceOpera(p: Painter) {
        p.sky(intArrayOf(rgb(0x02030A), rgb(0x0A0F24), rgb(0x1B1036)), floatArrayOf(0f, 0.6f, 1f))
        p.nebula(intArrayOf(rgb(0x5B2A9A), rgb(0x1E5AA8), rgb(0xA8324A)), n = 6, bottom = 0.7f)
        p.stars(320, 1f)
        val left = p.rnd.nextBoolean()
        p.planet(p.x(if (left) 0.25f else 0.75f), p.y(0.95f), p.w * 0.45f, rgb(0xD8A060), rgb(0x2A1A10), null, lightAngle = -70f)
        val dir = if (left) 1f else -1f
        val hull = rgb(0x8A93A6)
        p.capitalShip(p.x(0.5f), p.y(0.38f), p.w * 0.85f, -dir, mix(hull, Color.BLACK, 0.45f), rgb(0x6FB6FF))
        val shipHull = rgb(0x3C4250)
        repeat(4) { k ->
            val fx = p.x(0.15f + p.rnd.nextFloat() * 0.7f)
            val fy = p.y(0.5f + p.rnd.nextFloat() * 0.3f)
            val fs = p.w * (0.05f + k * 0.012f)
            p.fighter(fx, fy, fs, dir, shipHull, rgb(0xFF8A5A), angle = (p.rnd.nextFloat() - 0.5f) * 30f)
            p.bolt(fx + dir * fs * 0.6f, fy, fx + dir * fs * 2.6f, fy - fs * 0.3f, rgb(0xFF4A4A))
        }
        repeat(5) {
            val bx = p.x(p.rnd.nextFloat())
            val by = p.y(0.2f + p.rnd.nextFloat() * 0.5f)
            p.bolt(bx, by, bx + p.w * 0.1f * (if (p.rnd.nextBoolean()) 1 else -1), by + p.u * 3, if (p.rnd.nextBoolean()) rgb(0x52FF7A) else rgb(0xFF4A4A))
        }
        p.burst(p.x(0.3f + p.rnd.nextFloat() * 0.4f), p.y(0.6f), p.u * 5f, rgb(0xFFA040))
        p.burst(p.x(0.15f + p.rnd.nextFloat() * 0.7f), p.y(0.25f), p.u * 3f, rgb(0xFFC060))
    }

    /** A sleek ship going to warp past a planet, the stars streaking. */
    fun starship(p: Painter) {
        p.sky(intArrayOf(rgb(0x01030A), rgb(0x06112A), rgb(0x0F1E44)), floatArrayOf(0f, 0.6f, 1f))
        p.nebula(intArrayOf(rgb(0x1E4AA8), rgb(0x2A8A9A)), n = 4)
        p.stars(260, 1f)
        val right = p.rnd.nextBoolean()
        val dir = if (right) 1f else -1f
        p.warpStreaks(p.x(if (right) 0.85f else 0.15f), p.y(0.42f), 120, rgb(0xCFE6FF))
        val px = p.x(if (right) 0.2f else 0.8f)
        p.planet(px, p.y(1.05f), p.w * 0.6f, rgb(0x6FA8E8), rgb(0x0A1A3A), null, lightAngle = -60f)
        p.glow(px, p.y(1.05f), p.w * 0.66f, rgb(0x8FD0FF), 70)
        p.sleekShip(p.x(0.5f), p.y(0.44f), p.w * 0.62f, dir, rgb(0x6A7488), rgb(0x7FD8FF))
    }

    /** A rainy city at night, all neon signs and lit windows, and a hooded figure in the wet street. */
    fun cyberpunk(p: Painter) {
        p.sky(intArrayOf(rgb(0x07030F), rgb(0x220A3A), rgb(0x4A1060)), floatArrayOf(0f, 0.6f, 1f))
        p.glow(p.x(0.5f), p.y(0.55f), p.w * 0.7f, rgb(0xFF3FA0), 60)
        val neon = intArrayOf(rgb(0xFF3FA0), rgb(0x2DE2FF), rgb(0xFFE14D), rgb(0xA05BFF))
        p.skyline(0.7f, 0.55f, rgb(0x1A0B2A), alpha(rgb(0xFF8AD0), 120), dense = 1.4f)
        p.mist(0.68f, 0.14f, rgb(0x7A2A8A), 100)
        p.skyline(0.86f, 0.75f, rgb(0x0A0512), alpha(rgb(0x7FE8FF), 170), dense = 0.8f)
        // Neon signs on the near towers.
        repeat(9) {
            val sx = p.x(p.rnd.nextFloat())
            val sy = p.y(0.25f + p.rnd.nextFloat() * 0.5f)
            val colour = neon[p.rnd.nextInt(neon.size)]
            val vertical = p.rnd.nextBoolean()
            val sw = if (vertical) p.u * 2.2f else p.u * (6f + p.rnd.nextFloat() * 6f)
            val sh = if (vertical) p.u * (8f + p.rnd.nextFloat() * 8f) else p.u * 2.2f
            p.glow(sx, sy, maxOf(sw, sh) * 1.2f, colour, 120)
            p.c.drawRoundRect(sx - sw / 2, sy - sh / 2, sx + sw / 2, sy + sh / 2, p.u * 0.6f, p.u * 0.6f, p.stroke(mix(colour, Color.WHITE, 0.4f), p.u * 0.5f))
        }
        // The wet street, its reflections, and the figure.
        p.c.drawRect(p.r.left, p.y(0.88f), p.r.right, p.r.bottom, p.fill(rgb(0x05030A)))
        repeat(14) {
            val rx = p.x(p.rnd.nextFloat())
            p.c.drawRect(rx, p.y(0.885f), rx + p.u * 0.8f, p.y(0.95f + p.rnd.nextFloat() * 0.05f), p.fill(alpha(neon[p.rnd.nextInt(neon.size)], 80)))
        }
        val fig = Path()
        Figures.inCoat(fig, p.x(0.5f), p.y(0.95f), p.h * 0.24f, hat = false)
        p.c.drawPath(fig, p.fill(rgb(0x020105)))
        p.rain(220, rgb(0xC9B6FF), slant = 0.1f, strength = 55)
    }

    /** A ruined city under a hazy sun, vines over the broken towers, and one traveller with a pack. */
    fun dystopia(p: Painter) {
        p.sky(intArrayOf(rgb(0x3A2A20), rgb(0x9A6A40), rgb(0xE0A86A), rgb(0xEAC79A)), floatArrayOf(0f, 0.4f, 0.7f, 1f))
        val sunX = p.x(0.35f + p.rnd.nextFloat() * 0.3f)
        p.glow(sunX, p.y(0.4f), p.w * 0.5f, rgb(0xFFE2B0), 160)
        p.disc(sunX, p.y(0.4f), p.u * 7f, alpha(rgb(0xFFF0D0), 220))
        val far = rgb(0x8A6A50)
        val near = rgb(0x2A1E18)
        // Broken towers: jagged tops, some leaning.
        for ((base, colour, tallest) in listOf(Triple(0.74f, far, 0.45f), Triple(0.9f, near, 0.62f))) {
            var bx = p.r.left - p.u * 2
            while (bx < p.r.right) {
                val bw = p.w * (0.07f + p.rnd.nextFloat() * 0.1f)
                val bh = p.h * tallest * (0.3f + p.rnd.nextFloat() * 0.7f)
                val top = p.y(base) - bh
                p.c.save()
                p.c.rotate((p.rnd.nextFloat() - 0.5f) * 8f, bx + bw / 2, p.y(base))
                val tower = Path().apply {
                    moveTo(bx, p.r.bottom + p.h * 0.1f)
                    lineTo(bx, top + bh * 0.1f)
                    lineTo(bx + bw * 0.3f, top + bh * 0.02f)
                    lineTo(bx + bw * 0.45f, top + bh * 0.14f)
                    lineTo(bx + bw * 0.7f, top)
                    lineTo(bx + bw, top + bh * 0.2f)
                    lineTo(bx + bw, p.r.bottom + p.h * 0.1f)
                    close()
                }
                p.c.drawPath(tower, p.fill(colour))
                // Empty windows and green growing over.
                for (k in 0 until 6) p.c.drawRect(bx + bw * 0.2f, top + bh * (0.25f + k * 0.12f), bx + bw * 0.35f, top + bh * (0.3f + k * 0.12f), p.fill(alpha(rgb(0xF0C890), if (colour == far) 60 else 40)))
                val green = alpha(rgb(0x4A6A2A), if (colour == far) 110 else 210)
                repeat(3) {
                    val vx = bx + p.rnd.nextFloat() * bw
                    val vy = top + bh * (0.05f + p.rnd.nextFloat() * 0.3f)
                    val len = bh * (0.15f + p.rnd.nextFloat() * 0.35f)
                    p.c.drawLine(vx, vy, vx + p.u * 0.4f, vy + len, p.stroke(green, p.u * 0.3f))
                    repeat(9) { p.disc(vx + (p.rnd.nextFloat() - 0.5f) * p.u * 2.2f, vy + p.rnd.nextFloat() * len, p.u * (0.4f + p.rnd.nextFloat() * 0.5f), green) }
                }
                p.c.restore()
                bx += bw + p.u * (1f + p.rnd.nextFloat() * 3f)
            }
            p.mist(base - 0.02f, 0.16f, rgb(0xE0B080), 110)
        }
        p.c.drawRect(p.r.left, p.y(0.9f), p.r.right, p.r.bottom, p.fill(rgb(0x1A120E)))
        val fig = Path()
        p.cloaked(fig, p.x(0.62f), p.y(0.95f), p.h * 0.16f)
        p.c.drawPath(fig, p.fill(rgb(0x0A0705)))
        p.c.drawRoundRect(p.x(0.62f) - p.h * 0.03f, p.y(0.95f) - p.h * 0.13f, p.x(0.62f) - p.h * 0.005f, p.y(0.95f) - p.h * 0.08f, p.u, p.u, p.fill(rgb(0x0A0705)))
    }

    /** A city of chimneys and a great clock tower under a golden sky, an airship sailing over. */
    fun steampunk(p: Painter) {
        p.sky(intArrayOf(rgb(0x3B2A1E), rgb(0x8A5A32), rgb(0xD9A15A), rgb(0xF2D49A)), floatArrayOf(0f, 0.4f, 0.72f, 1f))
        p.glow(p.x(0.3f), p.y(0.62f), p.w * 0.6f, rgb(0xFFE0A0), 150)
        p.clouds(6, 0.4f, 0.25f, rgb(0xF2D49A), 50)
        val dark = rgb(0x1E140E)
        p.skyline(0.82f, 0.38f, rgb(0x5A3E2A), alpha(rgb(0xFFD08A), 110), dense = 1.3f)
        // Smokestacks with smoke.
        repeat(4) {
            val sx = p.x(0.1f + p.rnd.nextFloat() * 0.8f)
            p.c.drawRect(sx - p.u, p.y(0.55f), sx + p.u, p.y(0.85f), p.fill(dark))
            p.wisp(sx, p.y(0.55f), p.h * 0.25f, rgb(0x6A5A50), 80, curl = 0.8f)
        }
        // The clock tower.
        val tx = p.x(0.7f)
        val tw = p.w * 0.1f
        val tower = Path().apply {
            addRect(tx - tw / 2, p.y(0.36f), tx + tw / 2, p.y(0.92f), Path.Direction.CW)
            moveTo(tx - tw * 0.6f, p.y(0.36f)); lineTo(tx, p.y(0.22f)); lineTo(tx + tw * 0.6f, p.y(0.36f)); close()
            addRect(tx - tw * 0.04f, p.y(0.17f), tx + tw * 0.04f, p.y(0.23f), Path.Direction.CW)
        }
        p.c.drawPath(tower, p.fill(dark))
        p.disc(tx, p.y(0.42f), tw * 0.36f, rgb(0xF5DFA8))
        p.c.drawCircle(tx, p.y(0.42f), tw * 0.36f, p.stroke(dark, p.u * 0.5f))
        p.c.drawLine(tx, p.y(0.42f), tx, p.y(0.42f) - tw * 0.26f, p.stroke(dark, p.u * 0.5f))
        p.c.drawLine(tx, p.y(0.42f), tx + tw * 0.18f, p.y(0.42f) + tw * 0.06f, p.stroke(dark, p.u * 0.5f))
        p.skyline(0.95f, 0.3f, dark, alpha(rgb(0xFFD08A), 200), dense = 0.9f)
        p.airship(p.x(0.38f), p.y(0.24f), p.w * 0.5f, rgb(0xB07A4A), rgb(0x3A2418), flip = p.rnd.nextBoolean())
        p.birds(4, 0.7f, 0.15f, 0.2f, alpha(dark, 200))
    }

    /** A city at night, a searchlight in the clouds and someone in a cape on the edge of a rooftop. */
    fun superhero(p: Painter) {
        p.sky(intArrayOf(rgb(0x040814), rgb(0x0E1E3A), rgb(0x2A3E66)), floatArrayOf(0f, 0.55f, 1f))
        p.clouds(7, 0.2f, 0.25f, rgb(0x2A3E66), 120)
        // The searchlight.
        val bx = p.x(0.3f)
        val beam = Path().apply { moveTo(bx, p.y(0.8f)); lineTo(p.x(0.5f), p.r.top); lineTo(p.x(0.68f), p.r.top); close() }
        p.c.drawPath(beam, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, p.y(0.8f), 0f, p.r.top, alpha(rgb(0xFFF2C0), 90), alpha(rgb(0xFFF2C0), 10), Shader.TileMode.CLAMP)
        })
        p.glow(p.x(0.59f), p.y(0.12f), p.w * 0.2f, rgb(0xFFF2C0), 120)
        p.skyline(0.82f, 0.5f, rgb(0x14203A), alpha(rgb(0xFFD88A), 140), dense = 1.3f)
        p.skyline(0.98f, 0.24f, rgb(0x070C18), alpha(rgb(0xFFD88A), 200), dense = 0.9f, spires = true)
        // The rooftop and the caped figure.
        val dark = rgb(0x020408)
        p.c.drawRect(p.x(0.5f), p.y(0.8f), p.r.right + 2, p.r.bottom + 2, p.fill(dark))
        val fx = p.x(0.72f)
        val fy = p.y(0.8f)
        val tall = p.h * 0.3f
        p.glow(fx, fy - tall * 0.6f, p.w * 0.2f, rgb(0xFFF2C0), 60)
        val hero = Path()
        Figures.hero(hero, fx, fy, tall, capeTo = -1f)
        p.c.drawPath(hero, p.fill(dark))
    }
}
