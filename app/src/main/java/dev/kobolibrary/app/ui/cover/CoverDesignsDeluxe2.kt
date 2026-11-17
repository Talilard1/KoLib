package dev.kobolibrary.app.ui.cover

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 * More of the grandest designs: a scene drawn in glowing lines, peaks in watercolour, an old tome with its
 * metal fittings, tooled leather, a great dial, flowers on a cloud wash, a map of streets.
 * Every one is drawn here in the book's own colours; none copies a particular book.
 */

/** The picture drawn in fine lines that change colour down the cover: glowing on a dark ground, or dark on a pale one. */
internal fun CoverGenerator.engraving(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val w = max(1, s.w.toInt())
    val h = max(1, s.h.toInt())
    val picture = pictureOf(art, s.w, s.h)
    val px = IntArray(w * h)
    picture.getPixels(px, 0, w, 0, 0, w, h)
    var lo = 255
    var hi = 0
    val raw = IntArray(px.size)
    for (i in px.indices) {
        val p = px[i]
        val v = (Color.red(p) * 54 + Color.green(p) * 183 + Color.blue(p) * 19) shr 8
        raw[i] = v
        if (v < lo) lo = v
        if (v > hi) hi = v
    }
    val span = max(1, hi - lo).toFloat()
    val lum = FloatArray(px.size) { (raw[it] - lo) / span }
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val groundHead = if (dark) mix(s.c.ground, a, 0.12f) else s.c.ground
    val groundFoot = if (dark) mix(s.c.ground, Color.BLACK, 0.62f) else mix(s.c.ground, a, 0.16f)
    val lineHead = if (dark) a else mix(s.c.ink, a, 0.2f)
    val lineMiddle = if (dark) hueShift(a, -36f) else mix(s.c.ink, a, 0.55f)
    val lineFoot = if (dark) hueShift(a, -80f, 0.06f) else a
    val pitch = max(3f, 0.92f * u)
    val k = 6.2832f / pitch * 0.7071f
    val soft = 0.9f / pitch + 0.02f
    // How steep a change of light counts as an edge to draw (finer on a bigger cover).
    val reach = if (w > 700) 2 else 1
    val edgeGain = 5.5f
    for (y in 0 until h) {
        val fy = y / h.toFloat()
        val paper = mix(groundHead, groundFoot, fy)
        val line = if (fy < 0.5f) mix(lineHead, lineMiddle, fy * 2f) else mix(lineMiddle, lineFoot, (fy - 0.5f) * 2f)
        for (x in 0 until w) {
            val i = y * w + x
            // On a dark ground the lines are light, so they gather where the picture is light; on a pale one, where it's dark.
            val tone = if (dark) lum[i] else 1f - lum[i]
            // Shading in lines where it's neither light nor dark (crossed where it's most so), and what's brightest filled in.
            val middling = 4f * tone * (1f - tone)
            var ink = if (middling < 0.2f) 0f else ((middling * 0.46f - (0.5f + 0.5f * sin((x + y) * k))) / soft + 0.5f).coerceIn(0f, 1f)
            if (middling > 0.86f) {
                val across = (((middling - 0.86f) / 0.14f * 0.36f - (0.5f + 0.5f * sin((x - y) * k + 1.3f))) / soft + 0.5f).coerceIn(0f, 1f)
                if (across > ink) ink = across
            }
            if (tone > 0.9f) ink = max(ink, ((tone - 0.9f) / 0.06f).coerceIn(0f, 1f))
            if (x in reach until w - reach && y in reach until h - reach) {
                val edge = ((abs(lum[i + reach] - lum[i - reach]) + abs(lum[i + w * reach] - lum[i - w * reach])) * edgeGain - 0.12f) * 3f
                if (edge > ink) ink = min(1f, edge)
            }
            px[i] = mix(paper, line, ink) or SOLID
        }
    }
    c.drawBitmap(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888), 0f, 0f, null)
    // Quieter behind the words.
    val hush = if (dark) groundFoot else s.c.ground
    c.drawRect(0f, 0.3f * s.h, s.w, 0.7f * s.h, Paint().apply {
        shader = LinearGradient(0f, 0.3f * s.h, 0f, 0.7f * s.h, intArrayOf(faint(hush, 0), faint(hush, 190), faint(hush, 190), faint(hush, 0)), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
    })
    c.drawRect(0f, 0.8f * s.h, s.w, s.h, Paint().apply { shader = LinearGradient(0f, 0.8f * s.h, 0f, s.h, faint(hush, 0), faint(hush, 225), Shader.TileMode.CLAMP) })
    c.drawRect(3.6f * u, 3.6f * u, s.w - 3.6f * u, s.h - 3.6f * u, s.stroke(s.deco(faint(s.c.ink, 150)), 0.26f * u))
    titleIn(
        s, t, RectF(8f * u, 0.37f * s.h, 92f * u, 0.63f * s.h), didoneBlack, 12.5f, s.c.ink, showing(a, hush, 3.5), upper = true, tracking = 0.03f, pitch = 1.06f,
        seriesFace = sansBold, seriesSize = 2.8f, seriesTracking = 0.2f, gap = 3.4f, shadow = faint(hush, 255), shadowRadius = 1.6f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 17f * u, 90f * u, s.h - 7f * u), sansBold, 3.9f, s.c.ink, tracking = 0.26f, shadow = faint(hush, 255), shadowRadius = 1.4f)
}

/** Mountains in washes of watercolour, each fading into mist, under clouds and stars, with firs and a castle dark at the foot. */
internal fun CoverGenerator.waterPeaks(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val mistC = if (dark) mix(g, s.c.ink, 0.2f) else mix(g, Color.WHITE, 0.55f)
    p.sky(intArrayOf(if (dark) mix(g, Color.BLACK, 0.3f) else mix(g, a, 0.12f), g, mistC), floatArrayOf(0f, 0.45f, 0.78f))
    // Where the paint ran and pooled.
    repeat(7) { p.glow(p.x(rnd.nextFloat()), p.y(0.05f + rnd.nextFloat() * 0.4f), s.w * (0.18f + rnd.nextFloat() * 0.3f), if (dark) a else mix(a, g, 0.2f), 18) }
    p.clouds(6, 0.27f, 0.2f, if (dark) mix(g, s.c.ink, 0.6f) else Color.WHITE, if (dark) 26 else 90, puffy = true)
    // Stars: one great one, lesser ones, and a spatter of the opposite colour.
    val starC = if (dark) s.c.ink else mix(a, s.c.ink, 0.4f)
    val spatter = showing(hueShift(a, 150f, if (dark) 0.1f else 0f), g, 1.6)
    repeat(70) { c.drawCircle(rnd.nextFloat() * s.w, rnd.nextFloat() * 0.42f * s.h, (0.14f + rnd.nextFloat() * 0.34f) * u, s.fill(faint(spatter, 120 + rnd.nextInt(130)))) }
    starDust(s, rnd, RectF(4f * u, 0.3f * s.h, s.w - 4f * u, 0.44f * s.h), 14, 6, starC)
    val bigX = (if (rnd.nextBoolean()) 20f + rnd.nextFloat() * 10f else 70f + rnd.nextFloat() * 10f) * u
    val bigY = 0.36f * s.h
    val ray = s.stroke(starC, 0.22f * u)
    c.drawLine(bigX, bigY - 9f * u, bigX, bigY + 9f * u, ray)
    c.drawLine(bigX - 6f * u, bigY, bigX + 6f * u, bigY, ray)
    CoverMotifs.draw(c, Motif.SPARK, bigX, bigY, 3.6f * u, s.fill(starC))
    // Peak behind peak: the paint strong at each crest and thinning to mist below it.
    for (k in 0 until 5) {
        val top = 0.42f + 0.08f * k
        val wash = if (dark) (if (k < 3) mix(g, s.c.ink, 0.34f - 0.09f * k) else mix(g, Color.BLACK, 0.25f + 0.2f * (k - 3))) else mix(g, a, 0.2f + 0.17f * k)
        p.layer(p.ridge(top + 0.15f, top, rough = 0.56f - 0.04f * k), wash, mistC, top, top + 0.26f)
    }
    // Dark at the foot: firs, and what stands among them.
    val deep = if (dark) mix(g, Color.BLACK, 0.8f) else mix(a, s.c.ink, 0.74f)
    val foot = Path()
    repeat(13) { fir(foot, rnd.nextFloat() * s.w, s.h - 10f * u, (13f + rnd.nextFloat() * 15f) * u, rnd) }
    val castleX = s.w - bigX + (rnd.nextFloat() - 0.5f) * 10f * u
    val storied = art.genre in setOf(
        CoverGenre.FANTASY, CoverGenre.MAGIC_SCHOOL, CoverGenre.DARK_MAGIC, CoverGenre.QUEST, CoverGenre.STORM_EPIC, CoverGenre.FAIRY_TALE, CoverGenre.ROMANTASY,
        CoverGenre.KNIGHTS, CoverGenre.HISTORICAL, CoverGenre.VAMPIRES, CoverGenre.MYTHOLOGY, CoverGenre.WITCHES, CoverGenre.HORROR,
    )
    val lit = Path()
    if (storied) {
        // A crag for it to stand on: up one side in ledges, across under the walls, and down the other.
        val base = s.h - 25f * u
        val size = 20f * u
        fun side(from: Float, to: Float, rising: Boolean) {
            val steps = 5
            val runs = FloatArray(steps) { 0.6f + rnd.nextFloat() }
            val rises = FloatArray(steps) { 0.6f + rnd.nextFloat() }
            val run = (to - from) / runs.sum()
            val rise = (s.h + 2f * u - base) / rises.sum() * (if (rising) -1f else 1f)
            var x = from
            var y = if (rising) s.h + 2f * u else base
            for (k in 0 until steps) {
                // A ledge, then the rock face above (or below) it.
                val flatFirst = rising
                val dx = runs[k] * run
                val dy = rises[k] * rise
                if (flatFirst) foot.lineTo(x + dx * 0.72f, y + dy * 0.14f) else foot.lineTo(x + dx * 0.28f, y + dy * 0.86f)
                x += dx
                y += dy
                foot.lineTo(x, y)
            }
        }
        foot.moveTo(castleX - size * 2.5f, s.h + 2f * u)
        side(castleX - size * 2.5f, castleX - size * 1.02f, rising = true)
        foot.lineTo(castleX + size * 1.07f, base)
        side(castleX + size * 1.07f, castleX + size * 2.6f, rising = false)
        foot.close()
        p.castle(foot, castleX, base + 0.3f * u, size, lit)
        // Firs up its flanks.
        for (k in 0 until 7) {
            val out = 1.15f + rnd.nextFloat() * 1.2f
            val x = castleX + size * out * (if (k % 2 == 0) -1f else 1f)
            fir(foot, x, base + (out - 0.9f) / 1.6f * 19f * u + 3f * u, (6f + rnd.nextFloat() * 6f) * u, rnd)
        }
    }
    // The ground they all stand on, dark enough to read the author's name on.
    foot.moveTo(-2f, s.h - 13.5f * u)
    foot.cubicTo(0.25f * s.w, s.h - 16.5f * u, 0.45f * s.w, s.h - 12.5f * u, 0.62f * s.w, s.h - 14.5f * u)
    foot.cubicTo(0.78f * s.w, s.h - 16.5f * u, 0.9f * s.w, s.h - 13f * u, s.w + 2f, s.h - 15f * u)
    foot.lineTo(s.w + 2f, s.h + 2f)
    foot.lineTo(-2f, s.h + 2f)
    foot.close()
    c.drawPath(foot, s.fill(deep))
    // Lights in its windows.
    c.drawPath(lit, s.fill(if (dark) mix(a, Color.WHITE, 0.35f) else mix(g, Color.WHITE, 0.4f)))
    p.grain(1f)
    titleIn(
        s, t, RectF(9f * u, 9f * u, 91f * u, 0.28f * s.h), romanDecorated, 9.6f, if (dark) a else s.c.ink, if (dark) s.c.ink else a, pitch = 1.14f, place = -1,
        foil = true, gap = 3.2f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 12.5f * u, 90f * u, s.h - 4.5f * u), romanLight, 4f, showing(if (dark) s.c.ink else s.c.ground, deep, 4.5), tracking = 0.22f)
}

/** An old tome: worn leather or stained vellum, bound with metal: strips, plates or hinges, a boss in the middle, and rivets. */
internal fun CoverGenerator.tome(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val g = s.c.ground
    val dark = isDark(g)
    c.drawColor(g)
    // Wear, and (on vellum) stains.
    repeat(28) { p.glow(p.x(rnd.nextFloat()), p.y(rnd.nextFloat()), s.w * (0.08f + rnd.nextFloat() * 0.26f), if (rnd.nextInt(3) == 0) Color.WHITE else Color.BLACK, 16 + rnd.nextInt(22)) }
    if (!dark) repeat(5) { p.glow(p.x(rnd.nextFloat()), p.y(rnd.nextFloat()), s.w * (0.07f + rnd.nextFloat() * 0.13f), mix(s.c.ink, Color.BLACK, 0.3f), 120 + rnd.nextInt(90)) }
    repeat(44) {
        val x = rnd.nextFloat() * s.w
        val y = rnd.nextFloat() * s.h
        c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * 16f * u, y + (rnd.nextFloat() - 0.5f) * 16f * u, s.stroke(faint(if (rnd.nextBoolean()) Color.WHITE else Color.BLACK, 16 + rnd.nextInt(14)), max(1f, 0.1f * u)))
    }
    p.grain(1.8f)
    p.vignette(if (dark) 150 else 80)
    // The metal: each piece with its shadow under it and a line of light along its edge.
    val base = s.deco(mix(s.c.accent, if (dark) Color.BLACK else s.c.ink, 0.14f))
    val flat = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = foil(s, base) }
    val under = s.fill(faint(Color.BLACK, 130))
    val cutLine = s.stroke(faint(Color.BLACK, 120), 0.26f * u)
    val cutFill = s.fill(faint(Color.BLACK, 130))
    val shine = s.stroke(faint(Color.WHITE, 70), 0.16f * u)
    fun metal(path: Path) {
        val low = Path()
        path.offset(0.5f * u, 0.8f * u, low)
        c.drawPath(low, under)
        c.drawPath(path, flat)
        c.drawPath(path, shine)
    }
    fun rivet(x: Float, y: Float, r: Float) {
        c.drawCircle(x + r * 0.3f, y + r * 0.4f, r, under)
        c.drawCircle(x, y, r, s.fill(mix(base, Color.BLACK, 0.45f)))
        c.drawCircle(x - r * 0.16f, y - r * 0.16f, r * 0.74f, flat)
        c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.22f, s.fill(faint(Color.WHITE, 150)))
    }
    val corners = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
    val fitting = rnd.nextInt(3)
    when (fitting) {
        0 -> {
            // Bound all round with strips of metal, riveted, and a plate at each corner.
            val inset = 3.4f * u
            val wide = 5f * u
            val strips = Path().apply {
                addRect(inset, inset, s.w - inset, s.h - inset, Path.Direction.CW)
                addRect(inset + wide, inset + wide, s.w - inset - wide, s.h - inset - wide, Path.Direction.CCW)
            }
            metal(strips)
            var x = inset + wide * 2.6f
            while (x < s.w - inset - wide * 2.2f) {
                rivet(x, inset + wide / 2, 0.8f * u)
                rivet(x, s.h - inset - wide / 2, 0.8f * u)
                x += 7.4f * u
            }
            var y = inset + wide * 2.6f
            while (y < s.h - inset - wide * 2.2f) {
                rivet(inset + wide / 2, y, 0.8f * u)
                rivet(s.w - inset - wide / 2, y, 0.8f * u)
                y += 7.4f * u
            }
            for ((fx, fy) in corners) {
                val x0 = if (fx == 0f) inset - 0.8f * u else s.w - inset - 11.2f * u
                val y0 = if (fy == 0f) inset - 0.8f * u else s.h - inset - 11.2f * u
                val plateR = RectF(x0, y0, x0 + 12f * u, y0 + 12f * u)
                metal(Path().apply { addRoundRect(plateR, 1f * u, 1f * u, Path.Direction.CW) })
                val cutOut = Path()
                for (k in 0 until 4) CoverMotifs.leaf(cutOut, plateR.centerX(), plateR.centerY(), 4.8f * u, 45f + k * 90f, 0.5f)
                c.drawPath(cutOut, cutFill)
                rivet(plateR.centerX(), plateR.centerY(), 1.2f * u)
            }
        }
        1 -> {
            // A quarter-round plate at each corner, toothed at its edge and turned with rings.
            for ((fx, fy) in corners) {
                val x0 = fx * s.w
                val y0 = fy * s.h
                val from = if (fx == 0f) (if (fy == 0f) 0f else 270f) else (if (fy == 0f) 90f else 180f)
                metal(Path().apply {
                    moveTo(x0, y0)
                    for (k in 0..36) {
                        val ang = Math.toRadians((from + k * 2.5).toDouble())
                        val r = (if (k % 2 == 0) 19.6f else 18f) * u
                        lineTo(x0 + (cos(ang) * r).toFloat(), y0 + (sin(ang) * r).toFloat())
                    }
                    close()
                })
                for (r in listOf(15.4f, 14.6f, 9f)) c.drawArc(RectF(x0 - r * u, y0 - r * u, x0 + r * u, y0 + r * u), from, 90f, false, cutLine)
                for (k in 0 until 4) {
                    val ang = Math.toRadians((from + 12.0 + k * 22.0))
                    rivet(x0 + (cos(ang) * 12f * u).toFloat(), y0 + (sin(ang) * 12f * u).toFloat(), 0.9f * u)
                }
                val mid = Math.toRadians(from + 45.0)
                rivet(x0 + (cos(mid) * 5f * u).toFloat(), y0 + (sin(mid) * 5f * u).toFloat(), 1.3f * u)
            }
        }
        else -> {
            // A strip down the spine, two long hinges from it, and plates on the corners of the fore-edge.
            metal(Path().apply { addRect(-1f, -1f, 6.4f * u, s.h + 1f, Path.Direction.CW) })
            var y = 5f * u
            while (y < s.h) {
                rivet(3.2f * u, y, 0.8f * u)
                y += 9f * u
            }
            for (at in listOf(0.115f, 0.885f)) {
                val hy = at * s.h
                metal(Path().apply {
                    moveTo(5f * u, hy - 3.4f * u)
                    lineTo(38f * u, hy - 2f * u)
                    lineTo(42f * u, hy - 4.6f * u)
                    lineTo(49f * u, hy)
                    lineTo(42f * u, hy + 4.6f * u)
                    lineTo(38f * u, hy + 2f * u)
                    lineTo(5f * u, hy + 3.4f * u)
                    close()
                })
                for (x in listOf(11f, 20f, 29f)) rivet(x * u, hy, 0.9f * u)
                rivet(43.6f * u, hy, 1.2f * u)
            }
            for (fy in listOf(0f, 1f)) {
                val y0 = fy * s.h
                val sy = if (fy == 0f) 1f else -1f
                metal(Path().apply {
                    moveTo(s.w + 1f, y0 - sy)
                    lineTo(s.w - 21f * u, y0 - sy)
                    quadTo(s.w - 7f * u, y0 + sy * 7f * u, s.w + 1f, y0 + sy * 21f * u)
                    close()
                })
                val cutOut = Path()
                CoverMotifs.leaf(cutOut, s.w - 4f * u, y0 + sy * 4f * u, 7f * u, if (fy == 0f) 135f else 225f, 0.5f)
                c.drawPath(cutOut, cutFill)
                rivet(s.w - 15f * u, y0 + sy * 3f * u, 0.9f * u)
                rivet(s.w - 3f * u, y0 + sy * 15f * u, 0.9f * u)
                rivet(s.w - 3.4f * u, y0 + sy * 3.4f * u, 1.1f * u)
            }
        }
    }
    // The boss in the middle, and studs about it.
    val cx = s.w / 2
    val cy = 0.53f * s.h
    when (rnd.nextInt(3)) {
        0 -> {
            val rose = Path()
            for (k in 0 until 4) CoverMotifs.leaf(rose, cx, cy, 11f * u, k * 90f, 0.52f)
            metal(rose)
            val lesser = Path()
            for (k in 0 until 4) CoverMotifs.leaf(lesser, cx, cy, 7f * u, 45f + k * 90f, 0.5f)
            metal(lesser)
            c.drawCircle(cx, cy, 3.6f * u, cutLine)
            rivet(cx, cy, 2.6f * u)
        }
        1 -> {
            metal(cog(cx, cy, 10.6f * u, 24))
            c.drawCircle(cx, cy, 10.6f * u * 0.84f, flat)
            for (r in listOf(8.2f, 7.5f, 4.2f)) c.drawCircle(cx, cy, r * u, cutLine)
            for (k in 0 until 12) {
                val ang = Math.toRadians(k * 30.0)
                rivet(cx + (cos(ang) * 5.9f * u).toFloat(), cy + (sin(ang) * 5.9f * u).toFloat(), 0.65f * u)
            }
            rivet(cx, cy, 2.2f * u)
        }
        else -> {
            metal(Path().apply { moveTo(cx, cy - 10.6f * u); lineTo(cx + 8.8f * u, cy); lineTo(cx, cy + 10.6f * u); lineTo(cx - 8.8f * u, cy); close() })
            c.drawPath(Path().apply { moveTo(cx, cy - 7.2f * u); lineTo(cx + 5.9f * u, cy); lineTo(cx, cy + 7.2f * u); lineTo(cx - 5.9f * u, cy); close() }, cutLine)
            rivet(cx, cy, 2.4f * u)
        }
    }
    for ((dx, dy) in listOf(0f to -14.4f, 12.6f to 0f, 0f to 14.4f, -12.6f to 0f)) rivet(cx + dx * u, cy + dy * u, 0.9f * u)
    val lettering = if (dark) s.c.accent else s.c.ink
    titleIn(
        s, t, RectF(19f * u, (if (fitting == 2) 0.2f else 0.16f) * s.h, 81f * u, cy - 17f * u), fell, 9f, lettering, lettering, upper = true, tracking = 0.07f, pitch = 1.16f, foil = dark,
        seriesFace = fellItalic, seriesUpper = false, seriesSize = 3.6f, seriesTracking = 0f, gap = 2.8f, smallest = 3.2f,
        shadow = if (dark) faint(Color.BLACK, 170) else null, shadowRadius = 0.5f, shadowDy = 0.2f,
    )
    authorIn(s, t, RectF(19f * u, max(cy + 17f * u, 0.675f * s.h), 81f * u, 0.8f * s.h), fellCaps, 4.4f, lettering, upper = false, tracking = 0.1f, foil = dark, place = -1)
}

/** Peaks drawn in fine pale line with their facets, flat clouds, a sun, and the words on a band with a scalloped edge. */
internal fun CoverGenerator.linePeaks(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val ink = s.c.ink
    c.drawColor(g)
    val cx = s.w / 2
    val bandTop = s.h - max(34f * u, 0.25f * s.h)
    // A great ring, hardly there, and the sun.
    c.drawCircle(cx + (rnd.nextFloat() - 0.5f) * 20f * u, 0.36f * s.h, 37f * u, s.stroke(mix(g, ink, 0.09f), 5.4f * u))
    val sunX = (22f + rnd.nextFloat() * 56f) * u
    c.drawCircle(sunX, 0.21f * s.h, 6.2f * u, s.fill(s.c.accent))
    // Clouds, flat underneath.
    fun cloud(x: Float, y: Float, wide: Float) {
        val path = Path()
        path.addRoundRect(RectF(x - wide / 2, y - wide * 0.1f, x + wide / 2, y), wide * 0.05f, wide * 0.05f, Path.Direction.CW)
        path.addCircle(x - wide * 0.2f, y - wide * 0.1f, wide * 0.15f, Path.Direction.CW)
        path.addCircle(x + wide * 0.04f, y - wide * 0.16f, wide * 0.2f, Path.Direction.CW)
        path.addCircle(x + wide * 0.26f, y - wide * 0.09f, wide * 0.12f, Path.Direction.CW)
        c.drawPath(path, s.fill(ink))
    }
    cloud((16f + rnd.nextFloat() * 20f) * u, 0.13f * s.h, 22f * u)
    cloud((62f + rnd.nextFloat() * 22f) * u, 0.3f * s.h, 16f * u)
    cloud((30f + rnd.nextFloat() * 30f) * u, 0.4f * s.h, 9f * u)
    // The peaks, the highest (farthest) first so the nearer stand in front.
    val pen = s.stroke(s.deco(ink), 0.32f * u).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    val fine = Paint(pen).apply { strokeWidth = 0.18f * u }
    val base = bandTop + 6f * u
    val peaks = List(7) { i -> floatArrayOf((4f + i * 15.4f + rnd.nextFloat() * 8f) * u, (0.22f + rnd.nextFloat() * 0.26f) * s.h, (15f + rnd.nextFloat() * 12f) * u) }.sortedBy { it[1] }
    for (pk in peaks) {
        val ax = pk[0]
        val ay = pk[1]
        val half = pk[2]
        val tall = base - ay
        c.drawPath(Path().apply { moveTo(ax - half, base); lineTo(ax, ay); lineTo(ax + half, base); close() }, s.fill(g))
        c.drawPath(Path().apply { moveTo(ax - half, base); lineTo(ax, ay); lineTo(ax + half, base) }, pen)
        // The line the shadow falls along, and facets running off it.
        val x1 = ax + half * 0.14f
        val y1 = ay + tall * 0.3f
        val x2 = ax - half * 0.1f
        val y2 = ay + tall * 0.56f
        val x3 = ax + half * 0.16f
        c.drawPath(Path().apply { moveTo(ax, ay); lineTo(x1, y1); lineTo(x2, y2); lineTo(x3, base) }, fine)
        c.drawLine(x1, y1, ax + half * 0.42f, ay + tall * 0.42f, fine)
        c.drawLine(x2, y2, ax + half * 0.56f, ay + tall * 0.6f + rnd.nextFloat() * tall * 0.1f, fine)
        if (rnd.nextBoolean()) c.drawLine(x2, y2, ax - half * 0.46f, ay + tall * 0.7f, fine)
    }
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], (if (sunX > cx) 17f else 83f) * u, 0.5f * s.h, 5f * u, s.fill(s.c.accent))
    // The band: its upper edge in scallops, like cloud.
    val band = Path().apply {
        moveTo(-2f, s.h + 2f)
        lineTo(-2f, bandTop + 2f * u)
        var x = -2f
        while (x < s.w) {
            val wide = (9f + rnd.nextFloat() * 12f) * u
            quadTo(x + wide / 2, bandTop - (2f + rnd.nextFloat() * 4.4f) * u, x + wide, bandTop + 2f * u)
            x += wide
        }
        lineTo(s.w + 2f, s.h + 2f)
        close()
    }
    c.drawPath(band, s.fill(ink))
    Painter(c, whole(s), rnd).grain(0.7f)
    val onBand = showing(s.c.accent, ink, 3.0)
    titleIn(
        s, t, RectF(9f * u, bandTop + 8f * u, 91f * u, s.h - 15.5f * u), serifBold, 6.8f, g, onBand, upper = true, tracking = 0.06f, pitch = 1.14f, smallest = 3f,
        seriesFace = sans, seriesSize = 2.4f, seriesTracking = 0.18f, gap = 2.2f,
    )
    authorIn(s, t, RectF(9f * u, s.h - 13.5f * u, 91f * u, s.h - 5.5f * u), serif, 3.7f, onBand, tracking = 0.12f)
}

/** Old leather tooled without gold: knots in the corners, an oval frame, a star in foil at its head and the title small in the middle. */
internal fun CoverGenerator.tooled(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val g = s.c.ground
    c.drawColor(g)
    repeat(46) { p.glow(p.x(rnd.nextFloat()), p.y(rnd.nextFloat()), s.w * (0.07f + rnd.nextFloat() * 0.26f), if (rnd.nextInt(3) == 0) Color.WHITE else Color.BLACK, 18 + rnd.nextInt(26)) }
    repeat(90) { c.drawCircle(rnd.nextFloat() * s.w, rnd.nextFloat() * s.h, (0.2f + rnd.nextFloat() * 0.7f) * u, s.fill(faint(Color.BLACK, 16 + rnd.nextInt(34)))) }
    repeat(30) {
        val x = rnd.nextFloat() * s.w
        val y = rnd.nextFloat() * s.h
        c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * 20f * u, y + (rnd.nextFloat() - 0.5f) * 20f * u, s.stroke(faint(Color.WHITE, 14 + rnd.nextInt(12)), max(1f, 0.1f * u)))
    }
    p.grain(2f)
    p.vignette(165)
    // A line pressed into the leather: dark in the groove, a little light on its far lip.
    val low = mix(g, Color.BLACK, 0.52f)
    val lip = faint(mix(g, Color.WHITE, 0.34f), 150)
    fun press(path: Path, wide: Float) {
        val moved = Path()
        path.offset(0.34f * u, 0.34f * u, moved)
        c.drawPath(moved, s.stroke(lip, wide * 0.7f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
        c.drawPath(path, s.stroke(low, wide).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
    }
    val cx = s.w / 2
    val cy = s.h / 2
    val rx = 34f * u
    val ry = min(0.39f * s.h, s.h / 2 - 13f * u)
    press(Path().apply { addRect(4.4f * u, 4.4f * u, s.w - 4.4f * u, s.h - 4.4f * u, Path.Direction.CW) }, 1f * u)
    press(Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }, 1.1f * u)
    press(Path().apply { addOval(RectF(cx - rx + 2.6f * u, cy - ry + 2.6f * u, cx + rx - 2.6f * u, cy + ry - 2.6f * u), Path.Direction.CW) }, 0.4f * u)
    // In each corner a knot of three, laced through a ring, and a band curving away from it round the oval.
    for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
        val kx = cx + sx * (s.w / 2 - 14.5f * u)
        val ky = cy + sy * (s.h / 2 - 15.5f * u)
        val knot = Path()
        val toMiddle = Math.toDegrees(kotlin.math.atan2((cy - ky).toDouble(), (cx - kx).toDouble())).toFloat()
        for (k in 0 until 3) CoverMotifs.leaf(knot, kx, ky, 8.6f * u, toMiddle + 180f + k * 120f, 0.46f)
        press(knot, 0.9f * u)
        press(Path().apply { addCircle(kx, ky, 4.2f * u, Path.Direction.CW) }, 0.7f * u)
        val start = Math.toDegrees(kotlin.math.atan2((sy * 0.62f).toDouble(), (sx * 0.78f).toDouble())).toFloat()
        press(Path().apply { addArc(RectF(cx - rx - 4f * u, cy - ry - 4f * u, cx + rx + 4f * u, cy + ry + 4f * u), start - 13f, 26f) }, 0.8f * u)
    }
    // Links along the head and the foot.
    for (y in listOf(10.4f * u, s.h - 10.4f * u)) for (k in -1..1) {
        press(Path().apply { addOval(RectF(cx + k * 8.4f * u - 6.4f * u, y - 3f * u, cx + k * 8.4f * u + 6.4f * u, y + 3f * u), Path.Direction.CW) }, 0.7f * u)
    }
    // The star, in foil, and the words.
    val gilt = s.c.accent
    val leaf = leafPaint(s, s.deco(gilt))
    val starY = cy - ry + 14f * u
    val star = Path()
    CoverMotifs.star(star, cx, starY, 6.4f * u, 1.1f * u, 4)
    CoverMotifs.star(star, cx, starY, 3.2f * u, 0.9f * u, 4, -45f)
    c.drawPath(star, leaf)
    titleIn(
        s, t, RectF(cx - 25f * u, starY + 9f * u, cx + 25f * u, 0.64f * s.h), fell, 7.6f, gilt, gilt, upper = true, tracking = 0.1f, pitch = 1.2f, foil = true, smallest = 3f,
        seriesFace = fellItalic, seriesUpper = false, seriesSize = 3.4f, seriesTracking = 0f, gap = 2.6f, shadow = faint(Color.BLACK, 170), shadowRadius = 0.4f, shadowDy = 0.24f,
    )
    authorIn(s, t, RectF(cx - 23f * u, 0.675f * s.h, cx + 23f * u, 0.76f * s.h), fellCaps, 3.9f, gilt, upper = false, tracking = 0.12f, foil = true)
    val deviceY = cy + ry - 11f * u
    if (deviceY > 0.79f * s.h) CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, deviceY, 4.2f * u, leaf)
}

/** A great dial, half off the cover: its numerals and rings in one pale foil, a hand, and small emblems in a column beside the title. */
internal fun CoverGenerator.dial(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val rnd = Random(art.seed)
    val gilt = s.c.accent
    val leaf = leafPaint(s, s.deco(gilt), pressed = false)
    val line = Paint(leaf).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    if (isDark(s.c.ground)) starDust(s, rnd, whole(s), 240, 0, s.c.ink)
    val cx = 7f * u
    val cy = 0.53f * s.h
    val r = min(52f * u, 0.4f * s.h)
    Painter(c, whole(s), rnd).glow(cx, cy, r * 1.3f, gilt, 26)
    for ((at, wide) in listOf(1f to 0.6f, 0.965f to 0.16f, 0.75f to 0.22f, 0.72f to 0.14f, 0.2f to 0.3f)) c.drawCircle(cx, cy, r * at, Paint(line).apply { strokeWidth = wide * u })
    for (k in 0 until 60) {
        val a = Math.toRadians(k * 6.0)
        val long = k % 5 == 0
        c.drawLine(
            cx + (cos(a) * r * (if (long) 0.9f else 0.925f)).toFloat(), cy + (sin(a) * r * (if (long) 0.9f else 0.925f)).toFloat(),
            cx + (cos(a) * r * 0.955f).toFloat(), cy + (sin(a) * r * 0.955f).toFloat(), Paint(line).apply { strokeWidth = (if (long) 0.34f else 0.14f) * u },
        )
    }
    // The hours, each standing out from the middle.
    val figure = Paint(leaf).apply {
        typeface = didone
        textSize = r * 0.115f
        textAlign = Paint.Align.CENTER
    }
    for (hour in 1..12) {
        c.save()
        c.rotate(hour * 30f, cx, cy)
        c.drawText(romanNumeral(hour), cx, cy - r * 0.79f, figure)
        c.restore()
    }
    // The hand, with its scrolled tail.
    val pointing = -68f + rnd.nextInt(5) * 26f
    c.save()
    c.rotate(pointing, cx, cy)
    c.drawPath(Path().apply {
        moveTo(cx, cy - 1.1f * u)
        lineTo(cx + r * 0.34f, cy - 0.8f * u)
        cubicTo(cx + r * 0.4f, cy - 4f * u, cx + r * 0.5f, cy - 3f * u, cx + r * 0.52f, cy - 0.5f * u)
        lineTo(cx + r * 0.7f, cy)
        lineTo(cx + r * 0.52f, cy + 0.5f * u)
        cubicTo(cx + r * 0.5f, cy + 3f * u, cx + r * 0.4f, cy + 4f * u, cx + r * 0.34f, cy + 0.8f * u)
        lineTo(cx, cy + 1.1f * u)
        close()
    }, leaf)
    c.restore()
    c.drawCircle(cx, cy, 2.4f * u, leaf)
    // The title, the emblems under it, and the author's name: down the right.
    val took = titleIn(
        s, t, RectF(36f * u, 9f * u, 92f * u, 0.3f * s.h), didone, 9f, gilt, s.c.ink, pitch = 1.1f, place = -1, align = Layout.Alignment.ALIGN_OPPOSITE, foil = true,
        seriesFace = sans, seriesSize = 2.6f, seriesTracking = 0.18f, gap = 3f, smallest = 3.4f,
    )
    val set = CoverMotifs.forGenre(art.genre).shuffled(rnd)
    val from = max(took.bottom + 9f * u, 0.36f * s.h)
    val until = s.h - 30f * u
    for (k in 0 until 3) {
        val y = from + (until - from) * k / 2f
        c.drawCircle(81f * u, y, 6.2f * u, Paint(line).apply { strokeWidth = 0.18f * u })
        CoverMotifs.draw(c, set[k], 81f * u, y, 3.9f * u, leaf)
    }
    authorIn(s, t, RectF(40f * u, s.h - 21f * u, 92f * u, s.h - 8f * u), didoneItalic, 5f, gilt, upper = false, tracking = 0f, align = Layout.Alignment.ALIGN_OPPOSITE, place = 1, foil = true)
}

/** Bold flowers scattered round the edge of a cover washed like cloud, and the title among them. */
internal fun CoverGenerator.blooms(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    c.drawColor(g)
    val second = mix(g, hueShift(a, 165f, if (dark) 0f else 0.2f), 0.34f)
    val third = mix(g, if (dark) Color.BLACK else Color.WHITE, 0.4f)
    val seed = art.seed
    field(s, whole(s), if (s.w > 700) 3 else 2) { x, y ->
        val v = CoverMotifs.cloudy(x * 1.7f, y * 1.7f, seed, 4)
        val w2 = CoverMotifs.cloudy(x * 1.3f + 9f, y * 1.3f + 4f, seed + 5, 3)
        mix(mix(g, second, ((v - 0.35f) * 2.2f).coerceIn(0f, 1f)), third, ((w2 - 0.5f) * 2.4f).coerceIn(0f, 1f) * 0.8f)
    }
    val heart = if (dark) mix(g, Color.BLACK, 0.7f) else mix(s.c.ink, Color.BLACK, 0.2f)
    val shade = mix(a, Color.BLACK, 0.22f)
    val light = mix(a, Color.WHITE, 0.22f)
    fun flower(x: Float, y: Float, size: Float, turn: Float) {
        val petals = 5
        for (k in 0 until petals) {
            val ang = turn + k * 360f / petals + (rnd.nextFloat() - 0.5f) * 14f
            c.save()
            c.translate(x, y)
            c.rotate(ang)
            val long = size * (0.9f + rnd.nextFloat() * 0.2f)
            val petal = Path().apply {
                moveTo(0f, 0f)
                cubicTo(long * 0.3f, -long * 0.62f, long * 0.95f, -long * 0.6f, long, -long * 0.12f)
                quadTo(long * 0.9f, 0f, long * 1.02f, long * 0.14f)
                cubicTo(long * 0.95f, long * 0.6f, long * 0.3f, long * 0.62f, 0f, 0f)
                close()
            }
            c.drawPath(petal, s.fill(if (k % 2 == 0) a else light))
            c.drawPath(Path().apply { moveTo(0f, 0f); quadTo(long * 0.5f, -long * 0.1f, long * 0.8f, -long * 0.3f); quadTo(long * 0.5f, long * 0.04f, 0f, 0f) }, s.fill(shade))
            c.restore()
        }
        c.drawCircle(x, y, size * 0.2f, s.fill(heart))
        for (k in 0 until 9) {
            val ang = Math.toRadians(k * 40.0 + turn)
            val x1 = x + (cos(ang) * size * 0.34f).toFloat()
            val y1 = y + (sin(ang) * size * 0.34f).toFloat()
            c.drawLine(x, y, x1, y1, s.stroke(heart, size * 0.03f))
            c.drawCircle(x1, y1, size * 0.045f, s.fill(heart))
        }
    }
    // Round the edge, clear of the words in the middle and at the foot.
    val keep = RectF(17f * u, 0.27f * s.h, 83f * u, 0.7f * s.h)
    val keepFoot = RectF(18f * u, s.h - 21f * u, 82f * u, s.h - 4f * u)
    val round = 2 * (s.w + s.h)
    val slots = 15
    for (k in 0 until slots) {
        if (rnd.nextInt(7) == 0) continue
        val size = (7f + rnd.nextFloat() * 6f) * u
        // A place on the way round the edge, and a little in from it or out past it.
        var d = (k + rnd.nextFloat() * 0.7f) / slots * round
        val inward = (-3f + rnd.nextFloat() * 12f) * u
        val x: Float
        val y: Float
        if (d < s.w) { x = d; y = inward } else {
            d -= s.w
            if (d < s.h) { x = s.w - inward; y = d } else {
                d -= s.h
                if (d < s.w) { x = s.w - d; y = s.h - inward } else { x = inward; y = s.h - (d - s.w) }
            }
        }
        if (RectF(keep.left - size, keep.top - size, keep.right + size, keep.bottom + size).contains(x, y)) continue
        if (RectF(keepFoot.left - size, keepFoot.top - size, keepFoot.right + size, keepFoot.bottom + size).contains(x, y)) continue
        flower(x, y, size, rnd.nextFloat() * 360f)
    }
    // A few petals let fall.
    repeat(5) {
        val x = keep.left + rnd.nextFloat() * keep.width()
        val y = if (rnd.nextBoolean()) keep.top - (2f + rnd.nextFloat() * 8f) * u else keep.bottom + (2f + rnd.nextFloat() * 6f) * u
        val fallen = Path()
        CoverMotifs.leaf(fallen, x, y, (2.6f + rnd.nextFloat() * 2f) * u, rnd.nextFloat() * 360f, 0.5f)
        c.drawPath(fallen, s.fill(a))
    }
    Painter(c, whole(s), rnd).grain(0.8f)
    titleIn(
        s, t, RectF(20f * u, 0.3f * s.h, 80f * u, 0.67f * s.h), roman, 9f, s.c.ink, a, upper = true, tracking = 0.05f, pitch = 1.14f, foil = true,
        seriesFace = sans, seriesSize = 2.6f, seriesTracking = 0.18f, gap = 3f, shadow = faint(g, 255), shadowRadius = 1.2f,
    )
    authorIn(s, t, RectF(20f * u, s.h - 18.5f * u, 80f * u, s.h - 8f * u), roman, 3.9f, s.c.ink, shadow = faint(g, 255), shadowRadius = 1f)
}

/** A map of a city's streets, hardly lighter than the cover, and the title tall and widely spaced over it. */
internal fun CoverGenerator.streets(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    c.drawColor(g)
    val lane = mix(g, s.c.ink, 0.13f)
    val road = mix(g, s.c.ink, 0.22f)
    // Blocks cut into smaller blocks, each cut a street; the first cuts are the widest.
    fun cut(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, depth: Int) {
        val across = kotlin.math.hypot(x1 - x0, y1 - y0)
        val down = kotlin.math.hypot(x3 - x0, y3 - y0)
        if (depth <= 0 || across * down < 30f * u * u || (depth < 3 && rnd.nextInt(7) == 0)) return
        val a = 0.34f + rnd.nextFloat() * 0.32f
        val b = (a + (rnd.nextFloat() - 0.5f) * 0.16f).coerceIn(0.2f, 0.8f)
        val pen = s.stroke(if (depth >= 5) road else lane, (if (depth >= 5) 0.44f else 0.2f) * u)
        if (across > down) {
            val ax = x0 + (x1 - x0) * a
            val ay = y0 + (y1 - y0) * a
            val bx = x3 + (x2 - x3) * b
            val by = y3 + (y2 - y3) * b
            c.drawLine(ax, ay, bx, by, pen)
            cut(x0, y0, ax, ay, bx, by, x3, y3, depth - 1)
            cut(ax, ay, x1, y1, x2, y2, bx, by, depth - 1)
        } else {
            val ax = x0 + (x3 - x0) * a
            val ay = y0 + (y3 - y0) * a
            val bx = x1 + (x2 - x1) * b
            val by = y1 + (y2 - y1) * b
            c.drawLine(ax, ay, bx, by, pen)
            cut(x0, y0, x1, y1, bx, by, ax, ay, depth - 1)
            cut(ax, ay, bx, by, x2, y2, x3, y3, depth - 1)
        }
    }
    c.save()
    c.rotate(-12f + rnd.nextFloat() * 24f, s.w / 2, s.h / 2)
    cut(-30f * u, -30f * u, s.w + 30f * u, -30f * u, s.w + 30f * u, s.h + 30f * u, -30f * u, s.h + 30f * u, 8)
    c.restore()
    // A ring road, avenues out from the middle of it, and the river.
    val mx = (36f + rnd.nextFloat() * 28f) * u
    val my = (0.36f + rnd.nextFloat() * 0.28f) * s.h
    c.drawPath(blob(mx, my, 27f * u, art.seed, 0.12f), s.stroke(road, 0.44f * u))
    for (k in 0 until 7) {
        val ang = Math.toRadians(k * 51.0 + rnd.nextFloat() * 20.0)
        c.drawLine(mx, my, mx + (cos(ang) * s.h).toFloat(), my + (sin(ang) * s.h).toFloat(), s.stroke(road, 0.4f * u))
    }
    val river = Path().apply {
        moveTo(-4f * u, (0.5f + rnd.nextFloat() * 0.3f) * s.h)
        cubicTo(0.3f * s.w, (0.3f + rnd.nextFloat() * 0.3f) * s.h, 0.6f * s.w, (0.6f + rnd.nextFloat() * 0.3f) * s.h, s.w + 4f * u, (0.3f + rnd.nextFloat() * 0.3f) * s.h)
    }
    c.drawPath(river, s.stroke(road, 4.6f * u))
    c.drawPath(river, s.stroke(g, 3.8f * u))
    Painter(c, whole(s), rnd).apply {
        grain(0.8f)
        vignette(90)
    }
    c.drawRect(5f * u, 0f, 5.6f * u, s.h, s.fill(s.deco(s.c.accent)))
    titleIn(
        s, t, RectF(24f * u, 0.2f * s.h, 84f * u, 0.64f * s.h), condensed, 11f, s.c.ink, s.c.accent, upper = true, tracking = 0.3f, pitch = 1.5f, smallest = 4.6f,
        seriesFace = sans, seriesSize = 2.5f, seriesTracking = 0.3f, gap = 4.4f, shadow = faint(g, 255), shadowRadius = 1.6f,
    )
    authorIn(s, t, RectF(20f * u, 0.69f * s.h, 88f * u, 0.79f * s.h), condensed, 5.4f, s.c.accent, tracking = 0.34f, shadow = faint(g, 255), shadowRadius = 1.4f)
}
