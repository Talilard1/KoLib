package dev.kobolibrary.app.ui.cover

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The last of the grandest designs: neon worlds on black, a dagger under the moon, watercolour and gold,
 * gilt oak leaves, rings, a wind-blown moor, an apothecary's bottle, an emblem cut in stone, and a binding
 * covered in ornament. Every one is drawn here in the book's own colours; none copies a particular book.
 */

/** Worlds in neon on black: balls shaded in specks, a screen of dots, rays in dashes, and the title in a ruled box. */
internal fun CoverGenerator.cosmos(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    val rnd = Random(art.seed)
    val black = mix(n.dark, Color.BLACK, 0.87f)
    c.drawRect(whole(s), Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.h, intArrayOf(black, black, mix(n.dark, Color.BLACK, 0.3f)), floatArrayOf(0f, 0.66f, 1f), Shader.TileMode.CLAMP) })
    val p = Painter(c, whole(s), rnd)
    val a = n.accentOn(black)
    val b = showing(hueShift(a, 172f), black, 4.5)
    val third = showing(hueShift(a, -66f), black, 3.5)
    val fourth = showing(hueShift(a, 80f), black, 3.5)
    p.stars(110, 1f, n.light)
    fun lit(colour: Int, wide: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = wide
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(1.5f * u, 0f, 0f, colour)
    }
    // Streaks of light from one side.
    repeat(7) {
        val y = (0.33f + it * 0.062f + rnd.nextFloat() * 0.02f) * s.h
        c.drawLine(-2f * u, y, (14f + rnd.nextFloat() * 44f) * u, y, lit(faint(b, 150 + rnd.nextInt(100)), 0.44f * u))
    }
    // A ball shaded in specks: thickest where the light falls.
    fun ball(cx: Float, cy: Float, r: Float, colour: Int, towards: Float) {
        c.drawCircle(cx, cy, r, s.fill(mix(black, colour, 0.16f)))
        val lx = cos(towards) * 0.75f
        val ly = sin(towards) * 0.75f
        val count = (2600f * (r / (17f * u)).pow(2)).toInt()
        repeat(count) {
            val ang = rnd.nextFloat() * 6.2832f
            val far = sqrt(rnd.nextFloat())
            val dx = cos(ang) * far
            val dy = sin(ang) * far
            val light = dx * lx + dy * ly + sqrt(max(0f, 1f - far * far)) * 0.66f
            if (light > 0f && rnd.nextFloat() < light * light * 1.5f) {
                c.drawCircle(cx + dx * r, cy + dy * r, (0.12f + rnd.nextFloat() * 0.2f) * u, s.fill(faint(mix(colour, Color.WHITE, ((light - 0.62f) * 1.6f).coerceIn(0f, 0.7f)), 215)))
            }
        }
    }
    // A round of square dots, bigger across it.
    fun screen(cx: Float, cy: Float, r: Float, colour: Int) {
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        val step = 1.5f * u
        var y = cy - r
        while (y < cy + r) {
            var x = cx - r
            while (x < cx + r) {
                val size = step * (0.16f + 0.62f * (x - (cx - r)) / (2 * r))
                c.drawRect(x, y, x + size, y + size, s.fill(colour))
                x += step
            }
            y += step
        }
        c.restore()
        c.drawCircle(cx, cy, r, lit(faint(colour, 200), 0.2f * u))
    }
    // Rays in dashes that grow as they go out.
    fun rays(cx: Float, cy: Float, near: Float, far: Float, one: Int, other: Int) {
        for (k in 0 until 20) {
            c.save()
            c.rotate(k * 18f, cx, cy)
            for (j in 0 until 5) {
                val at = near + (far - near) * j / 4f
                val size = (0.45f + 0.3f * j) * u
                c.drawRect(cx + at - size, cy - size * 0.55f, cx + at + size, cy + size * 0.55f, s.fill(if (k % 2 == 0) one else other))
            }
            c.restore()
        }
    }
    val towards = -2.3f
    rays(42f * u, 0.67f * s.h, 13f * u, 27f * u, a, fourth)
    screen(63f * u, 0.46f * s.h, 14f * u, b)
    ball(35f * u, 0.45f * s.h, 14.6f * u, third, towards)
    c.save()
    c.rotate(-20f, 35f * u, 0.45f * s.h)
    c.drawArc(RectF(13f * u, 0.45f * s.h - 5f * u, 57f * u, 0.45f * s.h + 5f * u), 0f, 180f, false, lit(fourth, 0.3f * u))
    c.restore()
    ball(42f * u, 0.67f * s.h, 11f * u, a, towards)
    ball(73f * u, 0.69f * s.h, 6.6f * u, fourth, towards)
    c.drawCircle(21f * u, 0.35f * s.h, 2.4f * u, s.fill(a))
    // A fine frame, broken, with a bead on it.
    val frame = lit(faint(b, 210), 0.24f * u)
    c.drawLine(5f * u, 0.32f * s.h, 5f * u, s.h - 5f * u, frame)
    c.drawLine(5f * u, s.h - 5f * u, 60f * u, s.h - 5f * u, frame)
    c.drawLine(s.w - 5f * u, 0.3f * s.h, s.w - 5f * u, 0.78f * s.h, lit(faint(a, 210), 0.24f * u))
    c.drawCircle(s.w - 5f * u, 0.78f * s.h, 0.9f * u, s.fill(a))
    c.drawCircle(5f * u, 0.32f * s.h, 0.9f * u, s.fill(b))
    // The title, in a ruled box.
    val box = RectF(12f * u, 9f * u, 88f * u, 0.275f * s.h)
    c.drawRect(box, lit(b, 0.3f * u))
    titleIn(
        s, t, RectF(box.left + 5f * u, box.top + 3.4f * u, box.right - 5f * u, box.bottom - 3.4f * u), rounded, 8f, a, b, upper = true, tracking = 0.04f, pitch = 1.16f, smallest = 3.2f,
        seriesFace = sans, seriesSize = 2.5f, seriesTracking = 0.2f, gap = 2.6f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 19f * u, 90f * u, s.h - 9f * u), rounded, 4.2f, b, tracking = 0.14f)
}

/** A dagger, point down, under a horned moon in a dim wood; the title across its blade. */
internal fun CoverGenerator.dagger(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val dark = isDark(g)
    c.drawColor(g)
    val p = Painter(c, whole(s), rnd)
    // The wood, a shade off the cover: trees, firs, and something dripping from above.
    val paler = mix(g, if (dark) Color.WHITE else Color.BLACK, 0.07f)
    val deeper = mix(g, Color.BLACK, if (dark) 0.34f else 0.1f)
    repeat(26) {
        val x = rnd.nextFloat() * s.w
        val len = (4f + rnd.nextFloat() * 24f) * u
        val wide = (0.5f + rnd.nextFloat() * 1.2f) * u
        c.drawRect(x, 0f, x + wide, len, s.fill(deeper))
        c.drawCircle(x + wide / 2, len, wide / 2, s.fill(deeper))
    }
    repeat(8) { p.bareTree(rnd.nextFloat() * s.w, (0.9f + rnd.nextFloat() * 0.12f) * s.h, (22f + rnd.nextFloat() * 14f) * u, if (it % 2 == 0) paler else deeper, twist = 0.4f, depth = 6) }
    val firs = Path()
    repeat(9) { fir(firs, rnd.nextFloat() * s.w, s.h + 2f * u, (10f + rnd.nextFloat() * 12f) * u, rnd) }
    c.drawPath(firs, s.fill(deeper))
    p.vignette(120)
    val steel = if (dark) mix(s.c.ink, g, 0.1f) else mix(g, s.c.ink, 0.36f)
    val shadeSteel = if (dark) mix(s.c.ink, g, 0.52f) else mix(g, s.c.ink, 0.62f)
    val cx = s.w / 2
    p.glow(cx, 0.5f * s.h, 40f * u, steel, 26)
    // The moon, horns up.
    val moonY = 0.18f * s.h
    val moon = Path().apply { addCircle(cx, moonY, 6f * u, Path.Direction.CW) }
    moon.op(Path().apply { addCircle(cx, moonY - 2.7f * u, 5.5f * u, Path.Direction.CW) }, Path.Op.DIFFERENCE)
    c.drawPath(moon, s.fill(steel))
    // The dagger: pommel, grip, a guard like another moon, and the blade in its two faces.
    val gripTop = 0.26f * s.h
    val guardY = 0.385f * s.h
    val tip = 0.84f * s.h
    c.drawPath(Path().apply { moveTo(cx, gripTop - 3f * u); lineTo(cx + 2f * u, gripTop); lineTo(cx, gripTop + 3f * u); lineTo(cx - 2f * u, gripTop); close() }, s.fill(steel))
    c.drawRect(cx - 1.3f * u, gripTop + 1.6f * u, cx + 1.3f * u, guardY, s.fill(shadeSteel))
    var wy = gripTop + 3.4f * u
    while (wy < guardY - 3f * u) {
        c.drawLine(cx - 1.3f * u, wy, cx + 1.3f * u, wy + 0.9f * u, s.stroke(faint(g, 170), 0.26f * u))
        wy += 1.4f * u
    }
    val guard = Path().apply { addCircle(cx, guardY - 6.4f * u, 12.6f * u, Path.Direction.CW) }
    guard.op(Path().apply { addCircle(cx, guardY - 10.4f * u, 13.4f * u, Path.Direction.CW) }, Path.Op.DIFFERENCE)
    c.drawPath(guard, s.fill(steel))
    val top = guardY + 5f * u
    val half = 5.2f * u
    val belly = top + (tip - top) * 0.46f
    c.drawPath(Path().apply { moveTo(cx, top); lineTo(cx - half, top + 1f * u); lineTo(cx - half * 0.86f, belly); lineTo(cx, tip); close() }, s.fill(steel))
    c.drawPath(Path().apply { moveTo(cx, top); lineTo(cx + half, top + 1f * u); lineTo(cx + half * 0.86f, belly); lineTo(cx, tip); close() }, s.fill(shadeSteel))
    // The words.
    t.series?.let { fit(caps(it), romanLight, 2.8f * u, 2f * u, 80f * u, 6f * u, tracking = 0.22f, pitch = 1.4f) }?.draw(c, cx - 40f * u, 6.4f * u, s.seriesWords(s.c.ink))
    titleIn(
        s, t, RectF(14f * u, guardY + 9f * u, 86f * u, 0.7f * s.h), roman, 8.6f, s.c.accent, s.c.ink, upper = true, tracking = 0.14f, pitch = 1.36f, smallest = 3.4f,
        series = false, outline = g, outlineWidth = 1.5f, foil = true,
    )
    authorIn(s, t, RectF(12f * u, s.h - 17.5f * u, 88f * u, s.h - 7f * u), roman, 4f, s.c.accent, tracking = 0.2f, foil = true, shadow = faint(g, 255), shadowRadius = 1.2f)
    c.drawRect(4.6f * u, 0f, 5.2f * u, s.h, s.fill(s.deco(s.c.accent)))
}

/** A wash of watercolour, a great sign for the book a shade darker in it, a band of waves, and the lettering in gold. */
internal fun CoverGenerator.watercolour(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val dark = isDark(g)
    c.drawColor(g)
    val lifted = mix(g, Color.WHITE, if (dark) 0.2f else 0.42f)
    val pooled = mix(g, Color.BLACK, if (dark) 0.42f else 0.17f)
    val seed = art.seed
    field(s, whole(s), if (s.w > 700) 3 else 2) { x, y ->
        val v = CoverMotifs.cloudy(x * 1.5f, y * 1.5f, seed, 4)
        val fine = CoverMotifs.cloudy(x * 3.6f + 7f, y * 3.6f + 2f, seed + 3, 3)
        var colour = if (v > 0.5f) mix(g, lifted, ((v - 0.5f) * 3.4f).coerceAtMost(1f)) else mix(g, pooled, ((0.5f - v) * 3.4f).coerceAtMost(1f))
        // Where one wash dried against the next there's a fine dark edge.
        val edge = v * 5f - floor(v * 5f)
        if (edge > 0.955f) colour = mix(colour, pooled, 0.3f)
        mix(colour, pooled, ((fine - 0.62f) * 2f).coerceIn(0f, 0.3f))
    }
    Painter(c, whole(s), rnd).apply {
        grain(1.2f)
        vignette(if (dark) 110 else 40)
    }
    val tonal = mix(g, Color.BLACK, if (dark) 0.56f else 0.24f)
    val cx = s.w / 2
    // Salt thrown in the wet paint: small pale blooms.
    repeat(46) { c.drawCircle(rnd.nextFloat() * s.w, rnd.nextFloat() * s.h, (0.4f + rnd.nextFloat() * 1.3f) * u, s.fill(faint(lifted, 50 + rnd.nextInt(70)))) }
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, 0.33f * s.h, 23f * u, s.fill(faint(tonal, 225)), (rnd.nextFloat() - 0.5f) * 14f)
    // The band of waves.
    val bandY = 0.57f * s.h
    c.drawRect(0f, bandY + 8f * u, s.w, s.h, s.fill(faint(tonal, 56)))
    for (k in 0 until 3) {
        val wave = Path()
        var x = -4f * u
        wave.moveTo(x, bandY + k * 3.6f * u)
        var up = k % 2 == 0
        while (x < s.w + 4f * u) {
            wave.quadTo(x + 3.2f * u, bandY + k * 3.6f * u + (if (up) -2.6f else 2.6f) * u, x + 6.4f * u, bandY + k * 3.6f * u)
            x += 6.4f * u
            up = !up
        }
        c.drawPath(wave, s.stroke(faint(tonal, 230), 1.5f * u).apply { strokeJoin = Paint.Join.ROUND })
    }
    val gold = s.c.accent
    titleIn(
        s, t, RectF(12f * u, 0.14f * s.h, 88f * u, 0.52f * s.h), roman, 10f, gold, s.c.ink, upper = true, tracking = 0.1f, pitch = 1.22f, foil = true, gap = 3.4f,
        shadow = faint(Color.BLACK, 110), shadowRadius = 0.3f, shadowDy = 0.2f,
    )
    authorIn(s, t, RectF(12f * u, 0.69f * s.h, 88f * u, 0.82f * s.h), roman, 5.8f, gold, tracking = 0.1f, foil = true, pitch = 1.3f)
    c.drawRect(5f * u, 0f, 5.6f * u, s.h, leafPaint(s, s.deco(gold), pressed = false))
}

/** An oak leaf from ([x], [y]), [len] long: its lobed edge into [edge], its ribs into [ribs]. */
private fun oakLeaf(edge: Path, ribs: Path, x: Float, y: Float, len: Float, deg: Float) {
    val rad = Math.toRadians(deg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val steps = 44
    fun half(f: Float) = len * 0.25f * max(0f, sin(3.1416f * f)).pow(0.55f) * (0.68f + 0.32f * cos(6.2832f * 4f * f))
    edge.moveTo(x, y)
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        edge.lineTo(x + dx * len * f - dy * half(f), y + dy * len * f + dx * half(f))
    }
    for (i in steps - 1 downTo 0) {
        val f = i / steps.toFloat()
        edge.lineTo(x + dx * len * f + dy * half(f), y + dy * len * f - dx * half(f))
    }
    edge.close()
    ribs.moveTo(x - dx * len * 0.14f, y - dy * len * 0.14f)
    ribs.lineTo(x + dx * len * 0.94f, y + dy * len * 0.94f)
    for (k in 1..3) {
        val f = k / 4f
        val fromX = x + dx * len * (f - 0.07f)
        val fromY = y + dy * len * (f - 0.07f)
        for (side in listOf(-1f, 1f)) {
            ribs.moveTo(fromX, fromY)
            ribs.lineTo(x + dx * len * f - side * dy * half(f) * 0.78f, y + dy * len * f + side * dx * half(f) * 0.78f)
        }
    }
}

/** Oak leaves and acorns in gilt line, in two corners, and the title beside them. */
internal fun CoverGenerator.oakLeaves(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val rnd = Random(art.seed)
    val gold = s.deco(s.c.accent)
    val line = leafPaint(s, gold, pressed = false).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    val wash = s.fill(faint(gold, 34))
    fun spray(ox: Float, oy: Float, towards: Float, reach: Float) {
        // The bough, and leaves off it to either side.
        val rad = Math.toRadians(towards.toDouble())
        val ex = ox + (cos(rad) * reach).toFloat()
        val ey = oy + (sin(rad) * reach).toFloat()
        val bough = Path().apply { moveTo(ox, oy); quadTo((ox + ex) / 2 + (rnd.nextFloat() - 0.5f) * 8f * u, (oy + ey) / 2, ex, ey) }
        c.drawPath(bough, Paint(line).apply { strokeWidth = 0.5f * u })
        val count = 8
        for (k in 0 until count) {
            val f = 0.16f + 0.84f * k / (count - 1f)
            val x = ox + (ex - ox) * f
            val y = oy + (ey - oy) * f
            val side = if (k % 2 == 0) 1f else -1f
            val edge = Path()
            val ribs = Path()
            oakLeaf(edge, ribs, x, y, (13f + rnd.nextFloat() * 6f) * u * (1.1f - 0.3f * f), towards + side * (34f + rnd.nextFloat() * 34f))
            c.drawPath(edge, s.fill(s.c.ground))
            c.drawPath(edge, wash)
            c.drawPath(edge, Paint(line).apply { strokeWidth = 0.3f * u })
            c.drawPath(ribs, Paint(line).apply { strokeWidth = 0.16f * u })
        }
        // Acorns hanging near where it starts.
        for (k in 0 until 3) {
            val f = 0.3f + k * 0.2f
            val x = ox + (ex - ox) * f + (rnd.nextFloat() - 0.5f) * 5f * u
            val y = oy + (ey - oy) * f + (rnd.nextFloat() - 0.5f) * 5f * u
            val size = 2.3f * u
            c.save()
            c.rotate(towards + 90f + (rnd.nextFloat() - 0.5f) * 60f, x, y)
            c.drawOval(RectF(x - size * 0.7f, y, x + size * 0.7f, y + size * 2.2f), s.fill(s.c.ground))
            c.drawOval(RectF(x - size * 0.7f, y, x + size * 0.7f, y + size * 2.2f), Paint(line).apply { strokeWidth = 0.26f * u })
            c.drawArc(RectF(x - size * 0.86f, y - size * 0.7f, x + size * 0.86f, y + size * 0.9f), 180f, 180f, true, leafPaint(s, gold, pressed = false))
            c.drawLine(x, y - size * 0.7f, x, y - size * 1.5f, Paint(line).apply { strokeWidth = 0.3f * u })
            c.restore()
        }
    }
    spray(s.w + 3f * u, -3f * u, 128f + rnd.nextFloat() * 14f, 50f * u)
    spray(s.w + 3f * u, s.h + 3f * u, 232f - rnd.nextFloat() * 14f, 54f * u)
    c.drawRect(5f * u, 0f, 5.6f * u, s.h, leafPaint(s, gold, pressed = false))
    titleIn(
        s, t, RectF(12f * u, 0.3f * s.h, 68f * u, 0.64f * s.h), roman, 8.6f, s.c.accent, s.c.ink, upper = true, tracking = 0.07f, pitch = 1.24f, align = Layout.Alignment.ALIGN_NORMAL,
        foil = true, gap = 3.2f, seriesSize = 2.6f,
    )
    authorIn(s, t, RectF(12f * u, 0.685f * s.h, 60f * u, 0.8f * s.h), roman, 4.4f, s.c.accent, tracking = 0.14f, align = Layout.Alignment.ALIGN_NORMAL, foil = true, pitch = 1.34f, place = -1)
}

/** Rings within rings in a few bold colours, small rings going round between them, over the title in big letters. */
internal fun CoverGenerator.roundel(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val rnd = Random(art.seed)
    val a = s.c.accent
    val warm = showing(hueShift(a, -30f, -0.04f), s.c.ground, 2.6)
    val pale = mix(a, Color.WHITE, 0.4f)
    val cx = s.w / 2
    val r = min(31f * u, 0.24f * s.h)
    val cy = 9f * u + r
    Painter(c, whole(s), rnd).glow(cx, cy, r * 1.6f, a, 36)
    c.drawCircle(cx, cy, r * 0.955f, s.stroke(a, r * 0.09f))
    c.drawCircle(cx, cy, r * 0.84f, s.stroke(warm, r * 0.025f))
    val small = 12 + rnd.nextInt(3) * 2
    for (k in 0 until small) {
        val ang = Math.toRadians(k * 360.0 / small + 90.0)
        val x = cx + (cos(ang) * r * 0.67f).toFloat()
        val y = cy + (sin(ang) * r * 0.67f).toFloat()
        c.drawCircle(x, y, r * 0.095f, s.stroke(if (k % 2 == 0) pale else a, r * 0.03f))
        c.drawCircle(x, y, r * 0.03f, s.fill(warm))
    }
    c.drawCircle(cx, cy, r * 0.46f, s.stroke(warm, r * 0.1f))
    c.drawCircle(cx, cy, r * 0.36f, s.stroke(pale, r * 0.025f))
    c.drawCircle(cx, cy, r * 0.25f, s.fill(a))
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy, r * 0.15f, s.fill(s.c.accentInk))
    val top = cy + r + 7f * u
    titleIn(
        s, t, RectF(8f * u, top, 92f * u, s.h - 24f * u), didone, 11.5f, warm, s.c.ink, upper = true, tracking = 0.05f, pitch = 1.1f,
        seriesFace = sans, seriesSize = 2.7f, seriesTracking = 0.2f, seriesAbove = true, gap = 3.4f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 19.5f * u, 90f * u, s.h - 8f * u), romanLight, 4f, s.c.ink, tracking = 0.22f)
}

/** A moor in the wind, drawn all in streaming lines: a tree bent by it, grass, and the title in a lozenge. */
internal fun CoverGenerator.windswept(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    c.drawColor(g)
    val a = s.c.accent
    val airA = mix(g, s.c.ink, 0.4f)
    val airB = showing(mix(g, hueShift(a, 165f), 0.62f), g, 1.7)
    val flecks = showing(hueShift(a, 48f, 0.12f), g, 3.0)
    val seed = art.seed
    val blowing = -0.36f + rnd.nextFloat() * 0.3f
    // The wind: each line follows it a little way.
    repeat(190) {
        var x = rnd.nextFloat() * s.w * 1.2f - s.w * 0.1f
        var y = rnd.nextFloat() * s.h
        val path = Path().apply { moveTo(x, y) }
        repeat(34) {
            val ang = blowing + (CoverMotifs.cloudy(x / s.w * 1.6f, y / s.w * 1.6f, seed, 3) - 0.5f) * 3.4f
            x += cos(ang) * 1.5f * u
            y += sin(ang) * 1.5f * u
            path.lineTo(x, y)
        }
        c.drawPath(path, s.stroke(faint(if (it % 3 == 0) airB else airA, 120 + rnd.nextInt(120)), (0.16f + rnd.nextFloat() * 0.2f) * u).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
    }
    // The tree, everything on it leaning the way the wind goes.
    val towards = Math.toDegrees(blowing.toDouble()).toFloat()
    fun bough(x0: Float, y0: Float, len: Float, ang: Float, depth: Int, wide: Float) {
        val rad = Math.toRadians(ang.toDouble())
        val x1 = x0 + (cos(rad) * len).toFloat()
        val y1 = y0 + (sin(rad) * len).toFloat()
        val bend = len * 0.24f
        c.drawPath(Path().apply { moveTo(x0, y0); quadTo((x0 + x1) / 2 + bend * 0.3f, (y0 + y1) / 2 - bend, x1, y1) }, s.stroke(a, wide).apply { strokeCap = Paint.Cap.ROUND })
        if (depth == 0) {
            repeat(3) {
                val twig = towards + (rnd.nextFloat() - 0.5f) * 30f
                val tr = Math.toRadians(twig.toDouble())
                val tl = (3f + rnd.nextFloat() * 4f) * u
                c.drawLine(x1, y1, x1 + (cos(tr) * tl).toFloat(), y1 + (sin(tr) * tl).toFloat(), s.stroke(a, 0.24f * u).apply { strokeCap = Paint.Cap.ROUND })
            }
            return
        }
        for (k in 0 until 2) {
            // Each bough is pulled round towards the wind.
            val want = ang + (if (k == 0) -26f else 20f) + (rnd.nextFloat() - 0.5f) * 14f
            bough(x1, y1, len * (0.68f + rnd.nextFloat() * 0.12f), want + (towards - want) * 0.3f, depth - 1, wide * 0.66f)
        }
    }
    val rootX = (22f + rnd.nextFloat() * 14f) * u
    bough(rootX, 0.93f * s.h, 0.2f * s.h, -80f, 6, 2.4f * u)
    // Grass, bent the same way, and bright flecks carried on the wind.
    repeat(70) {
        val x = rnd.nextFloat() * s.w
        val tall = (3f + rnd.nextFloat() * 7f) * u
        c.drawPath(Path().apply { moveTo(x, s.h + 1f * u); quadTo(x + 1f * u, s.h - tall * 0.7f, x + tall * 0.9f, s.h - tall) }, s.stroke(if (it % 4 == 0) a else airB, 0.3f * u).apply { strokeCap = Paint.Cap.ROUND })
    }
    repeat(40) {
        val x = rnd.nextFloat() * s.w
        val y = rnd.nextFloat() * s.h
        c.drawLine(x, y, x + cos(blowing) * 1.6f * u, y + sin(blowing) * 1.6f * u, s.stroke(flecks, 0.4f * u).apply { strokeCap = Paint.Cap.ROUND })
    }
    Painter(c, whole(s), rnd).vignette(110)
    // The lozenge, and the words in it.
    val cx = s.w / 2 + 4f * u
    val cy = 0.41f * s.h
    val halfW = 27f * u
    val halfH = min(0.25f * s.h, 34f * u)
    val lozenge = Path().apply { moveTo(cx, cy - halfH); lineTo(cx + halfW, cy); lineTo(cx, cy + halfH); lineTo(cx - halfW, cy); close() }
    c.drawPath(lozenge, s.fill(g))
    val rim = leafPaint(s, s.deco(flecks), pressed = false).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    c.drawPath(lozenge, Paint(rim).apply { strokeWidth = 0.5f * u })
    val inner = Path().apply { moveTo(cx, cy - halfH + 2f * u); lineTo(cx + halfW - 1.7f * u, cy); lineTo(cx, cy + halfH - 2f * u); lineTo(cx - halfW + 1.7f * u, cy); close() }
    c.drawPath(inner, Paint(rim).apply { strokeWidth = 0.14f * u })
    titleIn(
        s, t, RectF(cx - halfW * 0.56f, cy - halfH * 0.4f, cx + halfW * 0.56f, cy + halfH * 0.4f), roman, 5.6f, flecks, s.c.ink, upper = true, tracking = 0.08f, pitch = 1.24f, smallest = 2.4f,
        seriesFace = sans, seriesSize = 2f, seriesTracking = 0.16f, gap = 2f, foil = true,
    )
    // The author, on a plate at the foot.
    t.author?.let { fit(caps(it), sansBold, 3.6f * u, 2.3f * u, 60f * u, 8f * u, tracking = 0.2f, pitch = 1.4f) }?.let { f ->
        val px = s.w / 2
        val plateR = RectF(px - f.width / 2 - 6f * u, s.h - 11f * u - f.height - 5.6f * u, px + f.width / 2 + 6f * u, s.h - 11f * u)
        c.drawRect(plateR, s.fill(g))
        c.drawRect(plateR, Paint(rim).apply { strokeWidth = 0.3f * u })
        f.draw(c, px - 30f * u, plateR.top + 2.8f * u, s.words(s.c.ink))
    }
}

/** An apothecary's bottle with the title on its label and a flowering sprig in its neck, between bands of chain. */
internal fun CoverGenerator.apothecary(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val dark = isDark(g)
    c.drawColor(g)
    val p = Painter(c, whole(s), rnd)
    // A grained ground, like leather.
    repeat(70) {
        val x = rnd.nextFloat() * s.w
        val y = rnd.nextFloat() * s.h
        c.drawPath(Path().apply { moveTo(x, y); quadTo(x + (rnd.nextFloat() - 0.5f) * 8f * u, y + (rnd.nextFloat() - 0.5f) * 8f * u, x + (rnd.nextFloat() - 0.5f) * 14f * u, y + (rnd.nextFloat() - 0.5f) * 14f * u) }, s.stroke(faint(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 14 + rnd.nextInt(12)), max(1f, 0.12f * u)))
    }
    p.grain(1.5f)
    p.vignette(if (dark) 120 else 50)
    val (sheetC, written) = paperAnd(s)
    val cx = s.w / 2
    // Chain along the head and the foot.
    val pale = if (dark) sheetC else s.c.ink
    val chain = s.stroke(s.deco(pale), 0.34f * u)
    for (y in listOf(8.4f * u, s.h - 8.4f * u)) {
        s.rect(0f, y - 3f * u, s.w, y - 2.6f * u, s.deco(pale))
        s.rect(0f, y + 2.6f * u, s.w, y + 3f * u, s.deco(pale))
        for (flip in listOf(1f, -1f)) {
            val wave = Path()
            var x = -3f * u
            wave.moveTo(x, y)
            var up = true
            while (x < s.w + 3f * u) {
                wave.quadTo(x + 1.6f * u, y + flip * (if (up) -2.2f else 2.2f) * u, x + 3.2f * u, y)
                x += 3.2f * u
                up = !up
            }
            c.drawPath(wave, chain)
        }
    }
    // The bottle.
    val a = s.c.accent
    val glass = if (androidx.core.graphics.ColorUtils.calculateLuminance(a) > 0.35) mix(a, Color.BLACK, 0.5f) else a
    val foot = 0.775f * s.h
    val shoulder = 0.47f * s.h
    val neckTop = shoulder - 11f * u
    val halfBody = 17f * u
    val bottle = Path().apply {
        moveTo(cx - 4f * u, neckTop)
        lineTo(cx - 4f * u, shoulder - 3f * u)
        cubicTo(cx - 4f * u, shoulder + 2f * u, cx - halfBody, shoulder, cx - halfBody, shoulder + 8f * u)
        lineTo(cx - halfBody, foot - 3f * u)
        quadTo(cx - halfBody, foot, cx - halfBody + 3f * u, foot)
        lineTo(cx + halfBody - 3f * u, foot)
        quadTo(cx + halfBody, foot, cx + halfBody, foot - 3f * u)
        lineTo(cx + halfBody, shoulder + 8f * u)
        cubicTo(cx + halfBody, shoulder, cx + 4f * u, shoulder + 2f * u, cx + 4f * u, shoulder - 3f * u)
        lineTo(cx + 4f * u, neckTop)
        close()
    }
    c.drawPath(bottle, s.fill(glass).apply { setShadowLayer(1.6f * u, 0.5f * u, 0.9f * u, faint(Color.BLACK, 120)) })
    c.drawRoundRect(RectF(cx - 5.4f * u, neckTop - 2.4f * u, cx + 5.4f * u, neckTop + 0.6f * u), 1f * u, 1f * u, s.fill(glass))
    c.save()
    c.clipPath(bottle)
    c.drawRect(cx - halfBody + 2.4f * u, shoulder, cx - halfBody + 5f * u, foot, s.fill(faint(Color.WHITE, 60)))
    c.drawRect(cx + halfBody - 4f * u, shoulder, cx + halfBody, foot, s.fill(faint(Color.BLACK, 50)))
    c.restore()
    // What grows out of it.
    val leafC = mix(pale, g, 0.3f)
    val petal = a
    val sprig = Path()
    val tips = ArrayList<FloatArray>()
    for (k in -1..1) {
        val tx = cx + k * (15f + rnd.nextFloat() * 6f) * u
        val ty = 15f * u + abs(k) * (7f + rnd.nextFloat() * 5f) * u
        frond(sprig, cx + k * 1.6f * u, neckTop - 1f * u, tx, ty + 6f * u, k * 0.12f, 5, 6f * u, 0.3f * u)
        tips += floatArrayOf(tx, ty + 4f * u)
    }
    c.drawPath(sprig, s.fill(leafC))
    for ((i, tip) in tips.withIndex()) {
        CoverMotifs.draw(c, Motif.FLOWER, tip[0], tip[1], (if (i == 1) 6f else 4.6f) * u, s.fill(petal), rnd.nextFloat() * 50f)
        c.drawCircle(tip[0], tip[1], 1.1f * u, s.fill(sheetC))
    }
    repeat(5) { c.drawCircle(cx + (rnd.nextFloat() - 0.5f) * 30f * u, (20f + rnd.nextFloat() * 20f) * u, (0.6f + rnd.nextFloat() * 0.8f) * u, s.fill(petal)) }
    // Its label, with the title on it.
    val label = RectF(cx - 13.6f * u, shoulder + 11f * u, cx + 13.6f * u, foot - 5f * u)
    c.drawRect(label, s.fill(sheetC))
    c.drawRect(label.left + 1f * u, label.top + 1f * u, label.right - 1f * u, label.bottom - 1f * u, s.stroke(faint(written, 150), 0.2f * u))
    titleIn(
        s, t, RectF(label.left + 2.4f * u, label.top + 2.4f * u, label.right - 2.4f * u, label.bottom - 2.4f * u), fell, 5.6f, written, showing(a, sheetC, 3.0), pitch = 1.1f, smallest = 2.2f,
        seriesFace = fellItalic, seriesUpper = false, seriesSize = 2.2f, seriesTracking = 0f, gap = 1.6f,
    )
    authorIn(s, t, RectF(10f * u, foot + 5f * u, 90f * u, s.h - 14f * u), fellCaps, 4.4f, pale, upper = false, tracking = 0.12f)
}

/** One emblem, as if cut in stone, on a plain cover; the author's name above it and the title below in colour. */
internal fun CoverGenerator.carved(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val dark = isDark(g)
    c.drawColor(g)
    Painter(c, whole(s), rnd).grain(0.5f)
    authorIn(s, t, RectF(10f * u, 8f * u, 90f * u, 18f * u), roman, 4.2f, s.c.ink, tracking = 0.22f, place = -1)
    val cx = s.w / 2
    val cy = 0.42f * s.h
    val r = min(31f * u, 0.21f * s.h)
    val stoneHigh = if (dark) mix(s.c.ink, g, 0.06f) else mix(s.c.ink, g, 0.44f)
    val stoneLow = if (dark) mix(s.c.ink, g, 0.56f) else mix(s.c.ink, Color.BLACK, 0.3f)
    val shape = CoverMotifs.outline(CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy, r, (rnd.nextFloat() - 0.5f) * 8f)
    c.drawPath(shape, s.fill(stoneLow).apply { setShadowLayer(2.4f * u, 0.8f * u, 1.4f * u, faint(Color.BLACK, if (dark) 170 else 90)) })
    c.save()
    c.clipPath(shape)
    val seed = art.seed
    field(s, RectF(cx - r * 1.1f, cy - r * 1.1f, cx + r * 1.1f, cy + r * 1.1f), if (s.w > 700) 2 else 1) { x, y ->
        // Rough as it came from the chisel, and lighter on the side the light is.
        val v = CoverMotifs.cloudy(x * 9f, y * 9f, seed, 4)
        val chip = 1f - abs(CoverMotifs.cloudy(x * 5f + 3f, y * 5f + 8f, seed + 2, 3) - 0.5f) * 2f
        val lit = ((cx / s.w - x) + (cy / s.w - y)) / (r / s.w) * 0.22f
        mix(mix(stoneLow, stoneHigh, (v + lit).coerceIn(0f, 1f)), stoneLow, chip.pow(6) * 0.7f)
    }
    // Its edges: light where they face the light, dark where they turn from it.
    val lifted = Path()
    shape.offset(-0.6f * u, -0.6f * u, lifted)
    c.drawPath(lifted, s.stroke(faint(Color.WHITE, if (dark) 150 else 110), 0.7f * u))
    val sunk = Path()
    shape.offset(0.6f * u, 0.6f * u, sunk)
    c.drawPath(sunk, s.stroke(faint(Color.BLACK, 120), 0.8f * u))
    c.restore()
    titleIn(
        s, t, RectF(8f * u, cy + r + 9f * u, 92f * u, s.h - 12f * u), roman, 12f, s.c.accent, s.c.ink, upper = true, tracking = 0.04f, pitch = 1.1f,
        seriesFace = romanLight, seriesSize = 3f, seriesTracking = 0.2f, gap = 3.6f,
    )
}

/** A binding covered in ornament in one bold colour: bands at head and foot, a sprinkle of stars, an emblem, the title in a ruled box and the book's number in another. */
internal fun CoverGenerator.ornate(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    c.drawColor(g)
    Painter(c, whole(s), rnd).apply {
        grain(1.2f)
        vignette(if (isDark(g)) 100 else 36)
    }
    val o = s.deco(s.c.accent)
    val cx = s.w / 2
    val pen = s.stroke(o, 0.4f * u).apply { strokeJoin = Paint.Join.MITER }
    // Bands at the head and the foot: rules with a zigzag between and a row of beads.
    for (top in listOf(5f * u, s.h - 13f * u)) {
        s.rule(cx, top, 90f * u, 0.8f * u, o)
        s.rule(cx, top + 8f * u, 90f * u, 0.8f * u, o)
        val zig = Path()
        var x = 5f * u
        zig.moveTo(x, top + 6.4f * u)
        var up = true
        while (x < s.w - 5f * u) {
            x += 3f * u
            zig.lineTo(min(x, s.w - 5f * u), top + (if (up) 1.6f else 6.4f) * u)
            up = !up
        }
        c.drawPath(zig, pen)
        var bx = 6.5f * u
        while (bx < s.w - 5f * u) {
            c.drawCircle(bx, top + 4f * u, 0.5f * u, s.fill(o))
            bx += 6f * u
        }
    }
    val frame = RectF(6f * u, 17f * u, s.w - 6f * u, s.h - 17f * u)
    c.drawRect(frame, pen)
    c.drawRect(frame.left + 1.3f * u, frame.top + 1.3f * u, frame.right - 1.3f * u, frame.bottom - 1.3f * u, Paint(pen).apply { strokeWidth = 0.16f * u })
    // The panels: an emblem, the title, the number, the author.
    val emblemY = frame.top + 14f * u
    val wide = 52f * u
    val title = fit(t.title, romanDecorated, 7.4f * u, 3.2f * u, wide, 0.24f * s.h, pitch = 1.14f)!!
    val seriesF = (t.seriesName ?: t.series)?.let { fit(caps(it), sans, 2.5f * u, 1.9f * u, wide, 6.4f * u, tracking = 0.16f, pitch = 1.4f) }
    val panelTop = emblemY + 13f * u
    val panel = RectF(cx - wide / 2 - 5f * u, panelTop, cx + wide / 2 + 5f * u, panelTop + title.height + (seriesF?.let { it.height + 3f * u } ?: 0f) + 11f * u)
    val number = t.seriesIndex?.toInt()?.takeIf { it.toDouble() == t.seriesIndex && it in 0..999 }?.toString()
    val numberBox = RectF(cx - 7f * u, 0.71f * s.h, cx + 7f * u, 0.71f * s.h + 11f * u)
    val authorBox = RectF(13f * u, s.h - 31f * u, 87f * u, s.h - 20f * u)
    // Stars sprinkled over the rest, in a lattice.
    val star = s.stroke(o, 0.3f * u).apply { strokeCap = Paint.Cap.ROUND }
    val keep = listOf(
        RectF(cx - 12f * u, emblemY - 11f * u, cx + 12f * u, emblemY + 11f * u), RectF(panel.left - 3f * u, panel.top - 3f * u, panel.right + 3f * u, panel.bottom + 3f * u),
        RectF(numberBox.left - 4f * u, numberBox.top - 3f * u, numberBox.right + 4f * u, numberBox.bottom + 3f * u), RectF(authorBox.left - 2f * u, authorBox.top - 2f * u, authorBox.right + 2f * u, authorBox.bottom + 2f * u),
    )
    var row = 0
    var y = frame.top + 6f * u
    while (y < frame.bottom - 4f * u) {
        var x = frame.left + 6f * u + (if (row % 2 == 0) 0f else 5.5f * u)
        while (x < frame.right - 4f * u) {
            if (keep.none { it.contains(x, y) }) for (k in 0 until 3) {
                val ang = Math.toRadians(k * 60.0 + 30.0)
                c.drawLine(x - (cos(ang) * 1.3f * u).toFloat(), y - (sin(ang) * 1.3f * u).toFloat(), x + (cos(ang) * 1.3f * u).toFloat(), y + (sin(ang) * 1.3f * u).toFloat(), star)
            }
            x += 11f * u
        }
        y += 6.4f * u
        row++
    }
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, emblemY, 8.6f * u, s.fill(o))
    c.drawRect(panel, s.fill(g))
    c.drawRect(panel, pen)
    c.drawRect(panel.left + 1.2f * u, panel.top + 1.2f * u, panel.right - 1.2f * u, panel.bottom - 1.2f * u, Paint(pen).apply { strokeWidth = 0.16f * u })
    var ty = panel.top + 5.5f * u
    title.draw(c, cx - wide / 2, ty, s.words(s.c.accent))
    ty += title.height + 3f * u
    seriesF?.draw(c, cx - wide / 2, ty, s.seriesWords(s.c.ink))
    c.drawRect(numberBox, s.fill(g))
    c.drawRect(numberBox, pen)
    if (number != null) fit(number, romanDecorated, 6f * u, 3f * u, numberBox.width() - 2f * u, 8f * u)?.let { it.draw(c, numberBox.left + 1f * u, numberBox.centerY() - it.height / 2, s.seriesWords(s.c.accent)) }
    else CoverMotifs.draw(c, Motif.STAR, numberBox.centerX(), numberBox.centerY(), 3f * u, s.fill(o))
    c.drawRect(authorBox, s.fill(g))
    authorIn(s, t, authorBox, roman, 4.2f, s.c.accent, tracking = 0.16f)
}
