package dev.kobolibrary.app.ui.cover

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The grandest designs, after collectors' editions of fantasy and science fiction: a scene engraved under a
 * wash of colour, peaks under the stars with the castle picked out in foil, a sword with a serpent about it,
 * a world tree, a wood seen through wood, an eclipse. Every one is drawn here in the book's own colours; none
 * copies a particular book.
 */

// ---- what these share --------------------------------------------------------------------------------------

/** Stars scattered over [r]: specks, and a few with four points. */
internal fun CoverGenerator.starDust(s: Sheet, rnd: Random, r: RectF, specks: Int, sparks: Int, colour: Int, paint: Paint? = null) {
    val u = s.u
    val dot = paint ?: s.fill(colour)
    repeat(specks) {
        val shade = Paint(dot).apply { alpha = 90 + rnd.nextInt(160) }
        s.canvas.drawCircle(r.left + rnd.nextFloat() * r.width(), r.top + rnd.nextFloat() * r.height(), (0.12f + rnd.nextFloat() * 0.3f) * u, shade)
    }
    repeat(sparks) {
        CoverMotifs.draw(s.canvas, Motif.SPARK, r.left + rnd.nextFloat() * r.width(), r.top + rnd.nextFloat() * r.height(), (0.7f + rnd.nextFloat() * 1.3f) * u, dot)
    }
}

/** A fir: a trunk and tier on tier of boughs, [tall] high, standing on ([x], [base]). */
internal fun fir(path: Path, x: Float, base: Float, tall: Float, rnd: Random) {
    val tiers = 7 + rnd.nextInt(4)
    path.addRect(x - tall * 0.018f, base - tall * 0.16f, x + tall * 0.018f, base, Path.Direction.CW)
    for (i in 0 until tiers) {
        val f = i / tiers.toFloat()
        val y = base - tall * (0.1f + 0.86f * f)
        val half = tall * 0.2f * (1f - f).pow(0.85f) * (0.82f + rnd.nextFloat() * 0.3f) + tall * 0.012f
        val droop = tall * 0.03f
        path.moveTo(x - half, y + droop)
        path.lineTo(x - half * 0.3f, y - tall * 0.05f)
        path.lineTo(x, y - tall * 1.7f / tiers)
        path.lineTo(x + half * 0.3f, y - tall * 0.05f)
        path.lineTo(x + half, y + droop)
        path.lineTo(x, y + droop * 0.2f)
        path.close()
    }
}

/** A double rule round the cover, in leaf. */
private fun CoverGenerator.foilFrame(s: Sheet, colour: Int, inset: Float = 4.2f) {
    val u = s.u
    val line = leafPaint(s, colour, pressed = false).apply { style = Paint.Style.STROKE }
    s.canvas.drawRect(inset * u, inset * u, s.w - inset * u, s.h - inset * u, Paint(line).apply { strokeWidth = 0.5f * u })
    s.canvas.drawRect((inset + 1.5f) * u, (inset + 1.5f) * u, s.w - (inset + 1.5f) * u, s.h - (inset + 1.5f) * u, Paint(line).apply { strokeWidth = 0.16f * u })
}

// ---- scenes ------------------------------------------------------------------------------------------------

/** A wood seen from within a wood: frame within frame of trees, darker the nearer, and something far off in the light. */
internal fun CoverGenerator.depths(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val near = mix(n.dark, Color.BLACK, 0.84f)
    val a = n.accentOn(near)
    val cx = s.w / 2 + (rnd.nextFloat() - 0.5f) * 6f * u
    val cy = 0.56f * s.h
    val first = min(16f * u, 0.13f * s.h)
    c.drawColor(a)
    p.glow(cx, cy - first * 0.2f, first * 1.7f, mix(a, Color.WHITE, 0.7f), 255)
    // What's out there, on a rise, under the moon.
    val shape = mix(a, n.dark, 0.72f)
    c.drawCircle(cx + first * 0.42f, cy - first * 0.62f, first * 0.3f, s.fill(mix(a, Color.WHITE, 0.85f)))
    val rise = Path().apply {
        moveTo(cx - first * 2f, cy + first * 1.6f)
        quadTo(cx, cy + first * 0.2f, cx + first * 2f, cy + first * 1.6f)
        close()
    }
    c.drawPath(rise, s.fill(shape))
    val there = Path()
    val lit = Path()
    when (art.genre) {
        CoverGenre.HORROR, CoverGenre.HALLOWEEN, CoverGenre.COSMIC_HORROR, CoverGenre.VAMPIRES, CoverGenre.MYSTERY, CoverGenre.THRILLER -> p.oldHouse(there, cx - first * 0.1f, cy + first * 0.92f, first * 0.95f, lit)
        CoverGenre.QUEST, CoverGenre.WITCHES, CoverGenre.DARK_MAGIC, CoverGenre.ADVENTURE -> p.cloaked(there, cx - first * 0.1f, cy + first * 0.92f, first * 0.9f)
        else -> p.castle(there, cx - first * 0.1f, cy + first * 0.95f, first * 1.15f, lit, cy + first * 1.4f)
    }
    c.drawPath(there, s.fill(shape))
    c.drawPath(lit, s.fill(mix(a, Color.WHITE, 0.7f)))
    p.birds(4, 0.5f - 0.06f, cy / s.h - 0.07f, 0.12f, shape)
    // The frames of trees, the farthest (and palest) first.
    val layers = 5
    for (k in 0 until layers) {
        val f = k / (layers - 1f)
        val tone = mix(mix(a, n.dark, 0.5f), near, f.pow(0.8f))
        val r = first * (1.12f + 0.42f * k)
        val frame = Path().apply {
            addRect(-2f, -2f, s.w + 2f, s.h + 2f, Path.Direction.CW)
            op(blob(cx + (rnd.nextFloat() - 0.5f) * 3f * u, cy, r, art.seed + k * 7, 0.2f, 1.22f), Path.Op.DIFFERENCE)
        }
        c.drawPath(frame, s.fill(tone))
        // Trunks either side, their boughs reaching into the gap, and firs along its foot.
        for (side in listOf(-1f, 1f)) {
            val bx = cx + side * (r + (2f + rnd.nextFloat() * 3f) * u)
            p.bareTree(bx, cy + r * 1.3f, r * (0.9f + rnd.nextFloat() * 0.3f), tone, twist = 0.5f, depth = 6, angle = -90f - side * (6f + rnd.nextFloat() * 10f))
        }
        val firs = Path()
        repeat(4 + k) { fir(firs, cx + (rnd.nextFloat() - 0.5f) * r * 2.6f, cy + r * 1.26f + rnd.nextFloat() * 2f * u, r * (0.34f + rnd.nextFloat() * 0.3f), rnd) }
        c.drawPath(firs, s.fill(tone))
        if (k < layers - 1) p.mist(cy / s.h + 0.02f, 0.5f, mix(a, n.dark, 0.4f), 16)
    }
    p.grain(0.8f)
    titleIn(s, t, RectF(8f * u, 8f * u, 92f * u, 0.25f * s.h), didone, 10.4f, n.light, a, upper = true, tracking = 0.08f, pitch = 1.14f, gap = 3f, shadow = faint(Color.BLACK, 220), shadowRadius = 1.4f)
    authorIn(s, t, RectF(10f * u, s.h - 15f * u, 90f * u, s.h - 5.5f * u), romanLight, 4f, n.light, tracking = 0.22f, shadow = faint(Color.BLACK, 220), shadowRadius = 1.2f)
}

/** Firs in the mist, rank behind rank, paler the farther off, with a pale sun through it and the title in bold type. */
internal fun CoverGenerator.mist(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val dark = isDark(s.c.ground)
    val fog = s.c.ground
    val far = if (dark) mix(s.c.ground, Color.BLACK, 0.16f) else mix(s.c.ground, s.c.ink, 0.16f)
    val near = if (dark) mix(s.c.ground, Color.BLACK, 0.82f) else mix(s.c.ground, s.c.ink, 0.94f)
    c.drawColor(fog)
    p.glow(s.w * 0.5f, s.h * 0.3f, s.w * 0.8f, if (dark) Color.WHITE else Color.WHITE, if (dark) 14 else 60)
    val sunX = (28f + rnd.nextFloat() * 44f) * u
    c.drawCircle(sunX, 0.37f * s.h, 8.4f * u, s.fill(s.c.accent))
    p.birds(5, sunX / s.w + 0.12f, 0.3f, 0.2f, mix(fog, near, 0.5f))
    val ranks = 6
    for (k in 0 until ranks) {
        val f = k / (ranks - 1f)
        val tone = mix(far, near, f.pow(1.25f))
        val base = (0.56f + 0.09f * k) * s.h
        val tall = (15f + 9f * k) * u
        val firs = Path()
        var x = -rnd.nextFloat() * 8f * u
        while (x < s.w + 6f * u) {
            fir(firs, x, base + rnd.nextFloat() * 3f * u, tall * (0.6f + rnd.nextFloat() * 0.5f), rnd)
            x += tall * (0.3f + rnd.nextFloat() * 0.34f)
        }
        firs.addRect(-2f, base, s.w + 2f, s.h + 2f, Path.Direction.CW)
        c.drawPath(firs, s.fill(tone))
        if (k < ranks - 1) p.mist(base / s.h + 0.012f, 0.11f, fog, 150 - k * 14)
    }
    p.grain(0.8f)
    titleIn(
        s, t, RectF(9f * u, 9f * u, 91f * u, 0.29f * s.h), didoneBlack, 12f, s.c.ink, s.c.accent, pitch = 1.06f, place = -1,
        seriesFace = sansBold, seriesSize = 2.8f, seriesTracking = 0.18f, gap = 3.4f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 15f * u, 90f * u, s.h - 5.5f * u), sansBold, 4f, if (dark) s.c.ink else s.c.ground, tracking = 0.2f)
}

/** Dunes, each with its lit side and its shadow, under two suns; and someone small on a crest. */
internal fun CoverGenerator.dunes(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val g = s.c.ground
    val a = s.c.accent
    c.drawRect(whole(s), Paint().apply { shader = LinearGradient(0f, 0f, 0f, 0.62f * s.h, g, mix(g, a, if (dark) 0.2f else 0.14f), Shader.TileMode.CLAMP) })
    val p = Painter(c, whole(s), rnd)
    // The suns.
    val sunX = (56f + rnd.nextFloat() * 22f) * u
    val sunC = a
    if (dark) p.glow(sunX, 0.36f * s.h, 30f * u, a, 80)
    c.drawCircle(sunX, 0.36f * s.h, 9.4f * u, s.fill(sunC))
    c.drawCircle(sunX - 19f * u, 0.3f * s.h, 3.6f * u, s.fill(sunC))
    if (dark) starDust(s, rnd, RectF(0f, 0.26f * s.h, s.w, 0.5f * s.h), 60, 0, s.c.ink)
    // Dune behind dune, the light coming from the suns' side: each bright along its crest, with the face turned away in shadow.
    val deep = if (dark) mix(g, Color.BLACK, 0.5f) else mix(a, s.c.ink, 0.62f)
    val count = 4
    var crestX = 0f
    var crestY = 0f
    for (k in 0 until count) {
        val f = k / (count - 1f)
        val lit = mix(mix(a, g, 0.34f - 0.2f * f), deep, 0.12f * f)
        val shade = mix(lit, deep, 0.5f + 0.2f * f)
        val top = (0.5f + 0.09f * k) * s.h
        val peakX = (if (k % 2 == 0) 0.56f + rnd.nextFloat() * 0.24f else 0.3f + rnd.nextFloat() * 0.2f) * s.w
        val swell = (9f + rnd.nextFloat() * 5f + k * 1.5f) * u
        val whole = Path().apply {
            moveTo(-2f, top + swell * 0.9f)
            cubicTo(peakX * 0.4f, top + swell * 0.9f, peakX - 14f * u, top + swell * 0.1f, peakX, top)
            cubicTo(peakX + 16f * u, top + swell * 0.5f, peakX + (s.w - peakX) * 0.6f, top + swell * 1.3f, s.w + 2f, top + swell * 0.7f)
            lineTo(s.w + 2f, s.h + 2f)
            lineTo(-2f, s.h + 2f)
            close()
        }
        c.drawPath(whole, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, top, 0f, top + swell * 3.4f, lit, mix(lit, shade, 0.5f), Shader.TileMode.CLAMP) })
        // The face in shadow: from the crest, a long curve falling away to the edge.
        val away = Path().apply {
            moveTo(peakX, top)
            cubicTo(peakX - 3f * u, top + swell * 0.7f, peakX * 0.62f - 5f * u, top + swell * 1f, peakX * 0.35f, top + swell * 2.1f)
            quadTo(peakX * 0.15f, top + swell * 2.7f, -2f, top + swell * 2.9f)
            lineTo(-2f, top + swell * 0.9f)
            cubicTo(peakX * 0.4f, top + swell * 0.9f, peakX - 14f * u, top + swell * 0.1f, peakX, top)
            close()
        }
        c.drawPath(away, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(peakX, 0f, 0f, 0f, shade, mix(shade, deep, 0.3f), Shader.TileMode.CLAMP) })
        if (k == 1) {
            crestX = peakX
            crestY = top
        }
    }
    // Someone on the second crest.
    val who = Path()
    standing(who, crestX, crestY + 0.6f * u, 5.4f * u)
    c.drawPath(who, s.fill(mix(deep, Color.BLACK, 0.5f)))
    // The near dune, in the dark.
    val near = mix(deep, Color.BLACK, if (dark) 0.2f else 0.1f)
    val nearTop = 0.86f * s.h
    c.drawPath(Path().apply {
        moveTo(-2f, nearTop + 2f * u)
        cubicTo(0.3f * s.w, nearTop - 5f * u, 0.5f * s.w, nearTop + 6f * u, s.w + 2f, nearTop - 1f * u)
        lineTo(s.w + 2f, s.h + 2f)
        lineTo(-2f, s.h + 2f)
        close()
    }, s.fill(near))
    val lastShade = near
    p.grain(0.9f)
    titleIn(
        s, t, RectF(9f * u, 9f * u, 91f * u, 0.25f * s.h), romanLight, 9.6f, s.c.ink, a, upper = true, tracking = 0.16f, pitch = 1.22f, place = -1,
        seriesFace = sans, seriesSize = 2.7f, seriesTracking = 0.24f, gap = 3.4f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 12.5f * u, 90f * u, s.h - 4.5f * u), sans, 3.8f, showing(if (dark) s.c.ink else s.c.ground, lastShade, 4.5), tracking = 0.26f)
}

/** A world in front of its sun: black, a rim of light with one bright bead on it, a ring about it, and the title across the dark. */
internal fun CoverGenerator.eclipse(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    val rnd = Random(art.seed)
    val black = mix(n.dark, Color.BLACK, 0.88f)
    c.drawRect(whole(s), Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.h, intArrayOf(black, black, mix(n.dark, Color.BLACK, 0.3f)), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP) })
    val p = Painter(c, whole(s), rnd)
    val a = n.accentOn(black)
    p.stars(230, 1f, n.light)
    val cx = s.w / 2
    val cy = 0.46f * s.h
    val r = min(31f * u, 0.24f * s.h)
    // Which way the light is.
    val turn = Math.toRadians((-150.0 + rnd.nextInt(4) * 40.0))
    val dx = cos(turn).toFloat()
    val dy = sin(turn).toFloat()
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.24f * u; color = s.deco(faint(a, 170)) }
    val tilt = -18f + rnd.nextFloat() * 36f
    val orbit = RectF(cx - r * 1.56f, cy - r * 0.3f, cx + r * 1.56f, cy + r * 0.3f)
    c.save()
    c.rotate(tilt, cx, cy)
    c.drawArc(orbit, 180f, 180f, false, ring)
    c.restore()
    p.glow(cx + dx * 2f * u, cy + dy * 2f * u, r * 2f, a, 150)
    c.drawCircle(cx + dx * 2.3f * u, cy + dy * 2.3f * u, r, s.fill(mix(a, Color.WHITE, 0.72f)))
    c.drawCircle(cx + dx * 1.1f * u, cy + dy * 1.1f * u, r * 1.012f, s.fill(a))
    c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx - dx * r * 0.5f, cy - dy * r * 0.5f, r * 1.5f, mix(n.dark, Color.BLACK, 0.7f), mix(n.dark, Color.BLACK, 0.93f), Shader.TileMode.CLAMP)
    })
    // The bead of light on the rim, and its flare.
    val bx = cx + dx * (r + 1.2f * u)
    val by = cy + dy * (r + 1.2f * u)
    p.glow(bx, by, 11f * u, Color.WHITE, 235)
    c.drawRect(bx - 34f * u, by - 0.16f * u, bx + 34f * u, by + 0.16f * u, Paint().apply {
        shader = LinearGradient(bx - 34f * u, 0f, bx + 34f * u, 0f, intArrayOf(0, faint(Color.WHITE, 200), 0), null, Shader.TileMode.CLAMP)
    })
    CoverMotifs.draw(c, Motif.SPARK, bx, by, 5.4f * u, s.fill(Color.WHITE))
    c.save()
    c.rotate(tilt, cx, cy)
    c.drawArc(orbit, 0f, 180f, false, ring)
    c.drawCircle(cx + r * 0.9f, orbit.bottom - r * 0.06f, 1.5f * u, s.fill(n.light))
    c.restore()
    titleIn(
        s, t, RectF(cx - r * 0.8f, cy - r * 0.46f, cx + r * 0.8f, cy + r * 0.5f), spaceLight, 6.4f, n.light, a, upper = true, tracking = 0.16f, pitch = 1.3f, smallest = 2.8f,
        seriesFace = sans, seriesSize = 2.4f, seriesTracking = 0.26f, gap = 3f,
    )
    // A scale, as on an instrument.
    val sy = s.h - 23f * u
    val tick = s.stroke(s.deco(faint(a, 170)), max(1f, 0.16f * u))
    for (k in -20..20) c.drawLine(cx + k * 1.5f * u, sy, cx + k * 1.5f * u, sy - (if (k % 5 == 0) 2.2f else 1f) * u, tick)
    authorIn(s, t, RectF(10f * u, s.h - 19f * u, 90f * u, s.h - 8f * u), spaceLight, 3.7f, n.light, tracking = 0.28f)
}

// ---- emblems in foil ---------------------------------------------------------------------------------------

/** A sword, point down, with a serpent wound about it, ferns either side and stars, all in foil. */
internal fun CoverGenerator.blade(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    cloth(s, art.seed)
    val rnd = Random(art.seed)
    val gold = s.c.accent
    val leaf = leafPaint(s, s.deco(gold))
    val pale = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = foil(s, mix(s.deco(gold), Color.WHITE, 0.35f)) }
    val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = foil(s, mix(s.deco(gold), s.c.ground, 0.45f)) }
    foilFrame(s, s.deco(gold))
    val cx = s.w / 2
    val took = titleIn(s, t, RectF(11f * u, 11f * u, 89f * u, 0.27f * s.h), roman, 9.4f, gold, s.c.ink, upper = true, tracking = 0.05f, pitch = 1.12f, place = -1, foil = true, gap = 3f)
    val top = max(took.bottom + 5f * u, 0.24f * s.h)
    val tip = s.h - 23f * u
    val guard = top + 15f * u
    Painter(c, whole(s), rnd).glow(cx, (top + tip) / 2, 44f * u, gold, 30)
    // Ferns either side, from the point.
    val ferns = Path()
    for (side in listOf(-1f, 1f)) {
        frond(ferns, cx + side * 7f * u, tip + 1f * u, cx + side * 34f * u, guard + 12f * u, side * 0.22f, 10, 6.6f * u, 0.3f * u)
        frond(ferns, cx + side * 9f * u, tip + 2f * u, cx + side * 27f * u, tip - 22f * u, side * 0.3f, 5, 4.6f * u, 0.26f * u)
    }
    c.drawPath(ferns, dim)
    // The serpent: its place across the blade and how thick it is, from tail (0) to head (1).
    val coils = 2.5f
    val low = tip - 9f * u
    val high = guard + 7f * u
    val phase = if (rnd.nextBoolean()) 0f else 3.1416f
    fun sx(f: Float) = cx + (5f + 7f * sin(3.1416f * f)) * u * sin(6.2832f * coils * f + phase)
    fun sy(f: Float) = low - (low - high) * f
    fun thick(f: Float) = (0.7f + 3.4f * min(1f, f * 3.2f).pow(0.7f)) * u * (1f - 0.12f * f)
    fun inFront(f: Float) = cos(6.2832f * coils * f + phase) > 0f
    val steps = 260
    fun body(front: Boolean, paint: Paint, grow: Float) {
        val pen = Paint(paint).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        for (i in 0 until steps) {
            val f0 = i / steps.toFloat()
            val f1 = (i + 1) / steps.toFloat()
            if (inFront(f0) != front) continue
            pen.strokeWidth = thick(f0) + grow
            c.drawLine(sx(f0), sy(f0), sx(f1), sy(f1), pen)
        }
    }
    body(false, dim, 0f)
    // The sword.
    val pommel = top + 2.6f * u
    c.drawCircle(cx, pommel, 2.7f * u, leaf)
    c.drawCircle(cx, pommel, 1f * u, s.fill(s.c.ground))
    c.drawRect(cx - 1.25f * u, pommel + 2.2f * u, cx + 1.25f * u, guard, leaf)
    val wrap = s.stroke(faint(s.c.ground, 150), 0.24f * u)
    var gy = pommel + 3.4f * u
    while (gy < guard - 1f * u) {
        c.drawLine(cx - 1.25f * u, gy, cx + 1.25f * u, gy + 1f * u, wrap)
        gy += 1.3f * u
    }
    val cross = Path().apply {
        moveTo(cx - 13.4f * u, guard - 1.4f * u)
        quadTo(cx, guard + 1.6f * u, cx + 13.4f * u, guard - 1.4f * u)
        lineTo(cx + 14.6f * u, guard + 1.6f * u)
        quadTo(cx, guard + 4.4f * u, cx - 14.6f * u, guard + 1.6f * u)
        close()
    }
    c.drawPath(cross, leaf)
    for (side in listOf(-1f, 1f)) c.drawCircle(cx + side * 14.2f * u, guard, 1.7f * u, leaf)
    val bladeTop = guard + 3.4f * u
    val half = 2.7f * u
    val shoulder = tip - 9f * u
    c.drawPath(Path().apply { moveTo(cx, bladeTop); lineTo(cx - half, bladeTop); lineTo(cx - half * 0.72f, shoulder); lineTo(cx, tip); close() }, pale)
    c.drawPath(Path().apply { moveTo(cx, bladeTop); lineTo(cx + half, bladeTop); lineTo(cx + half * 0.72f, shoulder); lineTo(cx, tip); close() }, leaf)
    c.drawLine(cx, bladeTop + 1f * u, cx, bladeTop + (tip - bladeTop) * 0.62f, s.stroke(faint(s.c.ground, 120), 0.3f * u))
    // The coils that pass in front, edged so they stand off the blade.
    body(true, s.fill(mix(s.c.ground, Color.BLACK, 0.35f)), 1f * u)
    body(true, pale, 0f)
    val belly = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = faint(s.c.ground, 90) }
    for (i in 0 until steps step 5) {
        val f = i / steps.toFloat()
        if (!inFront(f) || f < 0.08f) continue
        belly.strokeWidth = 0.22f * u
        val w = thick(f) * 0.42f
        c.drawLine(sx(f) - w, sy(f) + w * 0.5f, sx(f) + w, sy(f) - w * 0.5f, belly)
    }
    // Its head.
    val hx = sx(1f)
    val hy = sy(1f)
    val aim = Math.toDegrees(kotlin.math.atan2((sy(1f) - sy(0.985f)).toDouble(), (sx(1f) - sx(0.985f)).toDouble())).toFloat()
    c.save()
    c.rotate(aim, hx, hy)
    val head = Path().apply {
        moveTo(hx - 1.6f * u, hy - 2f * u)
        cubicTo(hx + 1.6f * u, hy - 3.4f * u, hx + 5.4f * u, hy - 1.7f * u, hx + 6.6f * u, hy)
        cubicTo(hx + 5.4f * u, hy + 1.7f * u, hx + 1.6f * u, hy + 3.4f * u, hx - 1.6f * u, hy + 2f * u)
        close()
    }
    c.drawPath(head, s.fill(mix(s.c.ground, Color.BLACK, 0.35f)).apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 1f * u; strokeJoin = Paint.Join.ROUND })
    c.drawPath(head, pale)
    c.drawCircle(hx + 2.7f * u, hy - 1.2f * u, 0.5f * u, s.fill(s.c.ground))
    c.drawCircle(hx + 2.7f * u, hy + 1.2f * u, 0.5f * u, s.fill(s.c.ground))
    val tongue = Paint(leaf).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f * u; strokeCap = Paint.Cap.ROUND }
    c.drawLine(hx + 6.6f * u, hy, hx + 8.6f * u, hy, tongue)
    c.drawLine(hx + 8.6f * u, hy, hx + 9.8f * u, hy - 1f * u, tongue)
    c.drawLine(hx + 8.6f * u, hy, hx + 9.8f * u, hy + 1f * u, tongue)
    c.restore()
    for (side in listOf(-1f, 1f)) starDust(s, rnd, RectF(cx + side * 22f * u - 14f * u, guard - 6f * u, cx + side * 22f * u + 14f * u, guard + 20f * u), 6, 4, gold, leaf)
    authorIn(s, t, RectF(12f * u, s.h - 18.5f * u, 88f * u, s.h - 8.5f * u), roman, 4f, gold, foil = true)
}

/** A stag's head cut in facets, a ring behind its antlers and the moon between them. */
internal fun CoverGenerator.stag(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 130)
    val rnd = Random(art.seed)
    val gold = s.c.accent
    val leaf = leafPaint(s, s.deco(gold), pressed = false)
    val cx = s.w / 2
    val took = titleIn(s, t, RectF(9f * u, 9f * u, 91f * u, 0.25f * s.h), didone, 10.4f, s.c.ink, gold, upper = true, tracking = 0.08f, pitch = 1.14f, place = -1, gap = 3.2f)
    val footTop = s.h - 21f * u
    // The head is 1.6 of its measure tall, and the antlers 1.6 more above it.
    val unit = min(21f * u, (footTop - took.bottom - 6f * u) / 3.5f)
    val cy = took.bottom + 5f * u + unit * 2.2f
    fun px(x: Float) = cx + x * unit
    fun py(y: Float) = cy + y * unit
    Painter(c, whole(s), rnd).glow(cx, py(-0.6f), unit * 2.6f, gold, 44)
    c.drawCircle(cx, py(-0.75f), unit * 1.5f, Paint(leaf).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f * u })
    c.drawCircle(cx, py(-0.75f), unit * 1.58f, Paint(leaf).apply { style = Paint.Style.STROKE; strokeWidth = 0.12f * u })
    starDust(s, rnd, RectF(px(-1.45f), py(-2.2f), px(1.45f), py(-0.4f)), 16, 7, gold, leaf)
    CoverMotifs.draw(c, Motif.MOON, cx, py(-1.5f), unit * 0.24f, leaf, -90f)
    // Antlers: a beam and its tines, each side, curving as they grow.
    for (side in listOf(-1f, 1f)) {
        fun grown(from: Float, to: Float, vararg p: Float) {
            val pts = FloatArray(p.size) { if (it % 2 == 0) px(side * p[it]) else py(-p[it]) }
            c.drawPath(tapered(pts, from * unit, to * unit), leaf)
        }
        grown(0.11f, 0.03f, 0.18f, 0.5f, 0.34f, 0.86f, 0.58f, 1.2f, 0.7f, 1.56f, 0.6f, 1.94f, 0.42f, 2.14f)
        grown(0.065f, 0.018f, 0.28f, 0.74f, 0.2f, 0.98f, 0.24f, 1.2f)
        grown(0.07f, 0.018f, 0.48f, 1.06f, 0.78f, 1.2f, 0.97f, 1.44f)
        grown(0.065f, 0.018f, 0.66f, 1.4f, 0.94f, 1.6f, 1.07f, 1.88f)
        grown(0.055f, 0.016f, 0.68f, 1.72f, 0.86f, 1.96f, 0.9f, 2.18f)
    }
    // The stag itself, facing us: each part a shade of the one colour, lit from one side.
    val pale = mix(gold, Color.WHITE, 0.4f)
    val lit = mix(gold, Color.WHITE, 0.12f)
    val shade = mix(gold, s.c.ground, 0.34f)
    val deep = mix(gold, s.c.ground, 0.6f)
    val darkest = mix(s.c.ground, Color.BLACK, 0.5f)
    fun part(paint: Paint, build: Sil.() -> Unit) {
        val path = Path()
        Sil(path, cx, cy, unit).build()
        c.drawPath(path, paint)
    }
    val edge = s.stroke(faint(darkest, 150), 0.16f * u).apply { strokeJoin = Paint.Join.ROUND }
    // The neck, its fur ragged at the foot, with a paler ruff down the middle.
    val neck: Sil.() -> Unit = {
        round(
            -0.36f, 0.1f, -0.44f, -0.46f, -0.7f, -1f, -0.7f, -1f, -0.52f, -0.93f, -0.52f, -0.93f, -0.38f, -1.13f, -0.38f, -1.13f, -0.2f, -1f, -0.2f, -1f,
            0f, -1.2f, 0f, -1.2f, 0.2f, -1f, 0.2f, -1f, 0.38f, -1.13f, 0.38f, -1.13f, 0.52f, -0.93f, 0.52f, -0.93f, 0.7f, -1f, 0.7f, -1f, 0.44f, -0.46f, 0.36f, 0.1f,
        )
    }
    part(Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(px(-0.7f), 0f, px(0.7f), 0f, intArrayOf(gold, shade, deep), null, Shader.TileMode.CLAMP) }, neck)
    part(s.fill(faint(lit, 150))) { round(-0.2f, -0.46f, 0f, -0.4f, 0.2f, -0.46f, 0.23f, -0.88f, 0f, -1.16f, 0f, -1.16f, -0.23f, -0.88f) }
    part(edge, neck)
    // Its ears, and the darker hollow of each.
    for (side in listOf(-1f, 1f)) {
        val ear: Sil.() -> Unit = { round(side * 0.3f, 0.47f, side * 0.5f, 0.63f, side * 0.86f, 0.7f, side * 0.86f, 0.7f, side * 0.68f, 0.42f, side * 0.42f, 0.3f) }
        part(s.fill(if (side < 0) gold else shade), ear)
        part(s.fill(deep)) { round(side * 0.4f, 0.46f, side * 0.56f, 0.57f, side * 0.76f, 0.63f, side * 0.76f, 0.63f, side * 0.62f, 0.46f, side * 0.45f, 0.38f) }
        part(edge, ear)
    }
    // The head: broad between the ears, long, and narrowing to the muzzle.
    val head: Sil.() -> Unit = {
        round(
            0f, 0.6f, 0.2f, 0.57f, 0.35f, 0.42f, 0.37f, 0.2f, 0.31f, -0.04f, 0.225f, -0.32f, 0.2f, -0.5f, 0.13f, -0.66f, 0f, -0.71f,
            -0.13f, -0.66f, -0.2f, -0.5f, -0.225f, -0.32f, -0.31f, -0.04f, -0.37f, 0.2f, -0.35f, 0.42f, -0.2f, 0.57f,
        )
    }
    part(Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(px(-0.36f), 0f, px(0.36f), 0f, intArrayOf(lit, gold, shade), null, Shader.TileMode.CLAMP) }, head)
    // The paler blaze down its face, and the hollows of its cheeks.
    part(s.fill(faint(pale, 120))) { round(-0.1f, 0.52f, 0.1f, 0.52f, 0.085f, 0.05f, 0.11f, -0.38f, 0f, -0.45f, -0.11f, -0.38f, -0.085f, 0.05f) }
    for (side in listOf(-1f, 1f)) {
        part(s.fill(faint(deep, 120))) { round(side * 0.215f, 0.08f, side * 0.345f, 0.12f, side * 0.285f, -0.08f, side * 0.21f, -0.34f, side * 0.16f, -0.16f) }
        // The eye, its lid and the mark that runs down from it.
        part(s.fill(darkest)) { round(side * 0.155f, 0.19f, side * 0.155f, 0.19f, side * 0.25f, 0.29f, side * 0.345f, 0.215f, side * 0.345f, 0.215f, side * 0.255f, 0.13f) }
        c.drawCircle(px(side * 0.25f - 0.025f), py(-0.235f), unit * 0.024f, s.fill(Color.WHITE))
        c.drawPath(Path().apply { moveTo(px(side * 0.14f), py(-0.24f)); quadTo(px(side * 0.25f), py(-0.36f), px(side * 0.365f), py(-0.26f)) }, s.stroke(faint(pale, 190), 0.2f * u).apply { strokeCap = Paint.Cap.ROUND })
        c.drawPath(Path().apply { moveTo(px(side * 0.155f), py(-0.18f)); quadTo(px(side * 0.12f), py(-0.09f), px(side * 0.13f), py(0.02f)) }, s.stroke(faint(darkest, 130), 0.18f * u).apply { strokeCap = Paint.Cap.ROUND })
        // A nostril.
        c.drawPath(Path().apply { moveTo(px(side * 0.03f), py(0.57f)); quadTo(px(side * 0.075f), py(0.52f), px(side * 0.09f), py(0.58f)) }, s.stroke(faint(shade, 200), 0.2f * u).apply { strokeCap = Paint.Cap.ROUND })
    }
    part(edge, head)
    // Its nose, and the line of its mouth.
    part(s.fill(darkest)) { round(-0.12f, -0.48f, 0f, -0.445f, 0.12f, -0.48f, 0.105f, -0.6f, 0f, -0.67f, -0.105f, -0.6f) }
    // (The nostrils are drawn over the nose, so they show.)
    for (side in listOf(-1f, 1f)) c.drawPath(Path().apply { moveTo(px(side * 0.03f), py(0.57f)); quadTo(px(side * 0.075f), py(0.52f), px(side * 0.09f), py(0.58f)) }, s.stroke(faint(shade, 220), 0.2f * u).apply { strokeCap = Paint.Cap.ROUND })
    // Ferns crossed under it.
    val ferns = Path()
    for (side in listOf(-1f, 1f)) frond(ferns, px(-side * 0.25f), py(1.26f), px(side * 1.3f), py(0.66f), side * 0.18f, 8, unit * 0.3f, 0.26f * u)
    c.drawPath(ferns, leaf)
    authorIn(s, t, RectF(12f * u, footTop + 2f * u, 88f * u, s.h - 7f * u), romanLight, 4f, s.c.ink, tracking = 0.22f)
}

/** The tree that holds the world: its boughs above and its roots below, the same either side, in a ring, with sun, moon and stars at the corners. */
internal fun CoverGenerator.worldTree(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    cloth(s, art.seed)
    val rnd = Random(art.seed)
    val gold = s.c.accent
    val leaf = leafPaint(s, s.deco(gold))
    val line = Paint(leaf).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    foilFrame(s, s.deco(gold))
    val cx = s.w / 2
    // In the corners: sun, moon, star and the book's own sign, each in its round.
    val corners = listOf(Motif.SUN, Motif.MOON, Motif.STAR, CoverMotifs.forGenre(art.genre)[0])
    for ((i, m) in corners.withIndex()) {
        val x = if (i % 2 == 0) 11.4f * u else s.w - 11.4f * u
        val y = if (i < 2) 11.4f * u else s.h - 11.4f * u
        c.drawCircle(x, y, 4.6f * u, s.fill(s.c.ground))
        c.drawCircle(x, y, 4.6f * u, Paint(line).apply { strokeWidth = 0.34f * u })
        CoverMotifs.draw(c, m, x, y, 2.7f * u, leaf, if (m == Motif.MOON) -30f else 0f)
    }
    val took = titleIn(s, t, RectF(19f * u, 10f * u, 81f * u, 0.25f * s.h), roman, 8.8f, gold, s.c.ink, upper = true, tracking = 0.05f, pitch = 1.12f, place = -1, foil = true, gap = 2.8f)
    val footTop = s.h - 21f * u
    val r = min(32f * u, (footTop - took.bottom - 7f * u) / 2)
    val cy = (took.bottom + footTop) / 2 + 1f * u
    Painter(c, whole(s), rnd).glow(cx, cy, r * 1.5f, gold, 30)
    c.drawCircle(cx, cy, r, Paint(line).apply { strokeWidth = 0.56f * u })
    c.drawCircle(cx, cy, r + 1.5f * u, Paint(line).apply { strokeWidth = 0.14f * u })
    for (k in 0 until 48) {
        val a = Math.toRadians(k * 7.5)
        if (k % 4 == 0) CoverMotifs.draw(c, Motif.SPARK, cx + (cos(a) * (r + 3.6f * u)).toFloat(), cy + (sin(a) * (r + 3.6f * u)).toFloat(), 0.9f * u, leaf)
        else c.drawCircle(cx + (cos(a) * (r + 3.6f * u)).toFloat(), cy + (sin(a) * (r + 3.6f * u)).toFloat(), 0.22f * u, leaf)
    }
    val ground = cy + r * 0.13f
    // One side is grown, and the other is its mirror.
    val tree = Path()
    val leaves = Path()
    fun grow(x0: Float, y0: Float, len: Float, ang: Float, depth: Int, wide: Float, up: Boolean) {
        val rad = Math.toRadians(ang.toDouble())
        val x1 = x0 + (cos(rad) * len).toFloat()
        val y1 = y0 + (sin(rad) * len).toFloat()
        val nx = -(y1 - y0) / len * wide / 2
        val ny = (x1 - x0) / len * wide / 2
        val bend = (rnd.nextFloat() - 0.5f) * len * 0.3f
        val mx = (x0 + x1) / 2 - (y1 - y0) / len * bend
        val my = (y0 + y1) / 2 + (x1 - x0) / len * bend
        tree.moveTo(x0 - nx, y0 - ny)
        tree.quadTo(mx - nx * 0.8f, my - ny * 0.8f, x1 - nx * 0.6f, y1 - ny * 0.6f)
        tree.lineTo(x1 + nx * 0.6f, y1 + ny * 0.6f)
        tree.quadTo(mx + nx * 0.8f, my + ny * 0.8f, x0 + nx, y0 + ny)
        tree.close()
        if (depth == 0) {
            if (up) for (k in -1..1) CoverMotifs.leaf(leaves, x1, y1, r * 0.085f, ang + k * 46f, 0.42f)
            return
        }
        val spread = 22f + rnd.nextFloat() * 14f
        grow(x1, y1, len * (0.7f + rnd.nextFloat() * 0.12f), ang - spread, depth - 1, wide * 0.66f, up)
        grow(x1, y1, len * (0.7f + rnd.nextFloat() * 0.12f), ang + spread, depth - 1, wide * 0.66f, up)
    }
    val crown = cy - r * 0.2f
    for ((ang, len) in listOf(-74f to 0.36f, -46f to 0.38f, -16f to 0.34f)) grow(cx + 0.3f * u, crown, r * len, ang, 4, r * 0.075f, true)
    for ((ang, len) in listOf(70f to 0.3f, 40f to 0.34f, 12f to 0.36f)) grow(cx + 0.3f * u, ground, r * len, ang, 3, r * 0.06f, false)
    c.save()
    c.clipPath(Path().apply { addCircle(cx, cy, r - 1f * u, Path.Direction.CW) })
    for (side in listOf(1f, -1f)) {
        c.save()
        c.scale(side, 1f, cx, cy)
        c.drawPath(tree, leaf)
        c.drawPath(leaves, leaf)
        c.restore()
    }
    // The trunk, wider at the ground, and the ground itself.
    c.drawPath(Path().apply {
        moveTo(cx - r * 0.06f, crown)
        quadTo(cx - r * 0.05f, ground - r * 0.1f, cx - r * 0.13f, ground + r * 0.03f)
        lineTo(cx + r * 0.13f, ground + r * 0.03f)
        quadTo(cx + r * 0.05f, ground - r * 0.1f, cx + r * 0.06f, crown)
        close()
    }, leaf)
    for (side in listOf(-1f, 1f)) c.drawLine(cx + side * r * 0.22f, ground, cx + side * r, ground, Paint(line).apply { strokeWidth = 0.2f * u })
    starDust(s, rnd, RectF(cx - r, cy - r, cx + r, crown - r * 0.1f), 10, 0, gold, leaf)
    c.restore()
    authorIn(s, t, RectF(19f * u, footTop + 2f * u, 81f * u, s.h - 8f * u), roman, 3.9f, gold, foil = true)
}
