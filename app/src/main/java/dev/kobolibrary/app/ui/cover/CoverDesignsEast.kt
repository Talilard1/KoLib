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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Designs in the manners of China and Japan: a hanging scroll, a stitched book, cranes before the sun over a
 * printed sea, a lone swordsman, koi, ink bamboo, a shrine gate, cranes among clouds, a family crest, and paper
 * lanterns. Each is drawn here in the book's own colours, in a tradition and not after any one picture or book.
 */

/** A curling cloud of the kind woven into silk: its shape goes into [clouds], the curls within it into [curls]. */
internal fun ruyiCloud(clouds: Path, curls: Path, x0: Float, y0: Float, q: Float) {
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

private val sealRed = Color.rgb(0xC2, 0x36, 0x2B)

/** A hanging scroll on a wall: the picture mounted in silk, with a rod above, a roller below, and the title on the mount. */
internal fun CoverGenerator.scroll(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    board(s, art.seed, vignette = 120)
    val left = 14f * u
    val right = 86f * u
    val top = 0.055f * s.h
    val foot = 0.945f * s.h
    val frame = pictureFrame(CoverStyle.SCROLL, s.w, s.h)!!
    val silk = mix(g, a, 0.14f)
    val wood = if (dark) mix(g, Color.BLACK, 0.6f) else mix(s.c.ink, Color.BLACK, 0.15f)
    // The cord it hangs by.
    val cord = s.stroke(mix(a, wood, 0.35f), 0.32f * u)
    c.drawLine(left + 7f * u, top, s.w / 2, 0.016f * s.h, cord)
    c.drawLine(right - 7f * u, top, s.w / 2, 0.016f * s.h, cord)
    c.drawCircle(s.w / 2, 0.016f * s.h, 0.8f * u, s.fill(wood))
    // The mount, figured all over.
    c.drawRect(left, top, right, foot, s.fill(silk).apply { setShadowLayer(2.4f * u, 0.9f * u, 1.3f * u, faint(Color.BLACK, if (dark) 160 else 80)) })
    c.save()
    c.clipRect(left, top, right, foot)
    val figure = s.stroke(faint(if (dark) Color.WHITE else Color.BLACK, 17), 0.2f * u)
    val lozenges = Path()
    var y = top
    var row = 0
    while (y < foot + 3f * u) {
        var x = left - (if (row % 2 == 0) 0f else 2.6f * u)
        while (x < right + 3f * u) {
            lozenges.moveTo(x, y - 1.5f * u); lozenges.lineTo(x + 1.7f * u, y); lozenges.lineTo(x, y + 1.5f * u); lozenges.lineTo(x - 1.7f * u, y); lozenges.close()
            x += 5.2f * u
        }
        y += 2.6f * u
        row++
    }
    c.drawPath(lozenges, figure)
    c.restore()
    // Strips of brocade above and below the picture, then the picture on its paper.
    for ((y0, y1) in listOf(frame.top - 3.2f * u to frame.top - 0.8f * u, frame.bottom + 0.8f * u to frame.bottom + 3.2f * u)) {
        c.drawRect(frame.left - 0.8f * u, y0, frame.right + 0.8f * u, y1, s.fill(a))
        var x = frame.left
        while (x < frame.right) {
            c.drawCircle(x + 1.2f * u, (y0 + y1) / 2, 0.42f * u, s.fill(mix(a, Color.BLACK, 0.3f)))
            x += 2.4f * u
        }
    }
    c.drawRect(frame.left - 0.8f * u, frame.top - 0.8f * u, frame.right + 0.8f * u, frame.bottom + 0.8f * u, s.fill(Color.rgb(0xF3, 0xEB, 0xD8)))
    c.save()
    c.clipRect(frame)
    paint(c, frame, art)
    c.restore()
    c.drawRect(frame, s.stroke(faint(Color.BLACK, 70), 0.2f * u))
    // The rod above, and the roller below with its ends.
    c.drawRect(left - 1f * u, top - 0.9f * u, right + 1f * u, top + 0.5f * u, s.fill(wood))
    val roller = RectF(left - 3.6f * u, foot - 1.5f * u, right + 3.6f * u, foot + 1.7f * u)
    c.drawRoundRect(roller, 0.6f * u, 0.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, roller.top, 0f, roller.bottom, intArrayOf(mix(wood, Color.WHITE, 0.3f), wood, mix(wood, Color.BLACK, 0.4f)), null, Shader.TileMode.CLAMP)
        setShadowLayer(1.4f * u, 0.5f * u, 0.9f * u, faint(Color.BLACK, 110))
    })
    for (x0 in listOf(roller.left, roller.right - 3f * u)) {
        c.drawRoundRect(RectF(x0, roller.top - 0.4f * u, x0 + 3f * u, roller.bottom + 0.4f * u), 0.6f * u, 0.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, roller.top, 0f, roller.bottom, intArrayOf(mix(a, Color.WHITE, 0.4f), a, mix(a, Color.BLACK, 0.4f)), null, Shader.TileMode.CLAMP)
        })
    }
    titleIn(
        s, t, RectF(left + 5f * u, top + 2.6f * u, right - 5f * u, frame.top - 4.6f * u), roman, 7.6f, s.c.ink, a, upper = true, tracking = 0.08f, pitch = 1.16f,
        seriesSize = 2.6f, gap = 2.2f, smallest = 2.6f,
    )
    authorIn(s, t, RectF(left + 5f * u, frame.bottom + 5.5f * u, right - 5f * u, foot - 4.4f * u), romanLight, 4.4f, s.c.ink, tracking = 0.2f)
}

/** A book stitched in the old way: patterned paper covers, thread through four holes down the spine, and the title on a paper slip. */
internal fun CoverGenerator.stitched(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    c.drawColor(g)
    // The paper's pattern, all over: hemp leaves, waves, or linked rings.
    val tone = s.stroke(mix(g, if (dark) Color.WHITE else Color.BLACK, if (dark) 0.1f else 0.12f), 0.22f * u).apply { strokeJoin = Paint.Join.ROUND }
    when (rnd.nextInt(3)) {
        0 -> {
            val side = 9.6f * u
            val high = side * 0.866f
            val leaves = Path()
            var row = -1
            while (row * high < s.h + high) {
                var x = -side + (if (row % 2 == 0) 0f else side / 2)
                val y = row * high
                while (x < s.w + side) {
                    // One triangle pointing up and one down, each with lines from its middle to its corners.
                    leaves.moveTo(x, y + high); leaves.lineTo(x + side / 2, y); leaves.lineTo(x + side, y + high); leaves.close()
                    for ((px, py) in listOf(x to y + high, x + side / 2 to y, x + side to y + high)) { leaves.moveTo(x + side / 2, y + high * 0.667f); leaves.lineTo(px, py) }
                    for ((px, py) in listOf(x + side / 2 to y, x + side to y + high, x + side * 1.5f to y)) { leaves.moveTo(x + side, y + high * 0.333f); leaves.lineTo(px, py) }
                    x += side
                }
                row++
            }
            c.drawPath(leaves, tone)
        }
        1 -> {
            val r = 6.2f * u
            val over = s.fill(g)
            var row = 0
            var y = -r
            while (y < s.h + r * 2) {
                var x = -r + (if (row % 2 == 0) 0f else r)
                while (x < s.w + r) {
                    c.drawCircle(x, y, r, over)
                    for (k in 0 until 3) c.drawCircle(x, y, r * (1f - k * 0.28f), tone)
                    x += r * 2
                }
                y += r * 0.5f
                row++
            }
        }
        else -> {
            val r = 6.4f * u
            val rings = Path()
            var y = -r
            while (y < s.h + r) {
                var x = -r
                while (x < s.w + r) {
                    rings.addCircle(x, y, r, Path.Direction.CW)
                    rings.addCircle(x + r * 0.707f, y + r * 0.707f, r, Path.Direction.CW)
                    x += r * 1.414f
                }
                y += r * 1.414f
            }
            c.drawPath(rings, tone)
        }
    }
    val p = Painter(c, whole(s), rnd)
    p.grain(1.4f)
    p.vignette(if (dark) 90 else 50)
    // The stitching: down the spine through four holes, and round the edge at each.
    val spine = 6.6f * u
    val holes = listOf(0.07f, 0.355f, 0.645f, 0.93f).map { it * s.h }
    val shadow = s.stroke(faint(Color.BLACK, 80), 0.6f * u).apply { strokeCap = Paint.Cap.ROUND }
    val thread = s.stroke(a, 0.6f * u).apply { strokeCap = Paint.Cap.ROUND }
    for ((dx, paint) in listOf(0.35f * u to shadow, 0f to thread)) {
        c.drawLine(spine + dx, -u, spine + dx, s.h + u, paint)
        for (hy in holes) c.drawLine(-u, hy + dx, spine + dx, hy + dx, paint)
    }
    for (hy in holes) c.drawCircle(spine, hy, 0.5f * u, s.fill(mix(g, Color.BLACK, 0.6f)))
    // Cloth on the spine's corners.
    for (y0 in listOf(0f, s.h - 7f * u)) c.drawRect(0f, y0, 3.4f * u, y0 + 7f * u, s.fill(mix(a, g, 0.2f)))
    // The title slip.
    val paper = if (dark) mix(s.c.ink, Color.rgb(0xF3, 0xE9, 0xD2), 0.5f) else mix(g, Color.WHITE, 0.62f)
    val slip = RectF(17f * u, 0.085f * s.h, 85f * u, 0.37f * s.h)
    c.drawRect(slip, s.fill(paper).apply { setShadowLayer(1.2f * u, 0.4f * u, 0.7f * u, faint(Color.BLACK, if (dark) 130 else 60)) })
    val written = if (dark) mix(g, Color.BLACK, 0.45f) else s.c.ink
    val ruled = showing(if (dark) mix(a, Color.BLACK, 0.25f) else a, paper, 2.6)
    c.drawRect(RectF(slip.left + 1.5f * u, slip.top + 1.5f * u, slip.right - 1.5f * u, slip.bottom - 1.5f * u), s.stroke(ruled, 0.5f * u))
    c.drawRect(RectF(slip.left + 2.5f * u, slip.top + 2.5f * u, slip.right - 2.5f * u, slip.bottom - 2.5f * u), s.stroke(ruled, 0.18f * u))
    titleIn(
        s, t, RectF(slip.left + 6f * u, slip.top + 5f * u, slip.right - 6f * u, slip.bottom - 5f * u), fell, 8.6f, written, showing(ruled, paper, 4.0), pitch = 1.12f,
        seriesFace = fellCaps, seriesUpper = false, seriesSize = 3f, seriesTracking = 0.08f, gap = 2.4f, smallest = 2.8f,
    )
    // An emblem pressed into the paper, in a ring.
    val pressed = mix(g, if (dark) Color.WHITE else Color.BLACK, 0.15f)
    val cx = 53f * u
    val cy = 0.61f * s.h
    c.drawCircle(cx, cy, 17.5f * u, s.stroke(pressed, 0.7f * u))
    c.drawCircle(cx, cy, 15.9f * u, s.stroke(pressed, 0.25f * u))
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy, 10.4f * u, s.fill(pressed))
    authorIn(s, t, RectF(17f * u, s.h - 17f * u, 90f * u, s.h - 7f * u), fellCaps, 4.6f, s.c.ink, upper = false, tracking = 0.12f)
}

/** Cranes flying before a great sun, over a sea drawn as the old prints draw it. */
internal fun CoverGenerator.sunCranes(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    c.drawColor(g)
    val p = Painter(c, whole(s), rnd)
    p.sky(intArrayOf(g, g, mix(g, if (dark) Color.WHITE else a, 0.09f)), floatArrayOf(0f, 0.5f, 0.78f))
    val way = if (rnd.nextBoolean()) 1f else -1f
    val sunX = (50f + way * (2f + rnd.nextFloat() * 6f)) * u
    val sunY = 0.475f * s.h
    val sunR = 29f * u
    if (dark) p.glow(sunX, sunY, sunR * 1.8f, a, 60)
    c.drawCircle(sunX, sunY, sunR, s.fill(a))
    // Thin cloud across the sun's foot.
    for ((dy, from, to) in listOf(Triple(0.3f, -1.5f, 0.5f), Triple(0.5f, -0.7f, 1.6f), Triple(0.68f, -1.25f, 0.2f))) {
        c.drawRoundRect(RectF(sunX + from * sunR, sunY + dy * sunR, sunX + to * sunR, sunY + dy * sunR + 1.5f * u), 0.75f * u, 0.75f * u, s.fill(g))
    }
    // The sea.
    val deep = if (dark) mix(g, Color.WHITE, 0.07f) else mix(g, s.c.ink, 0.5f)
    val shallow = if (dark) mix(g, Color.WHITE, 0.2f) else mix(g, s.c.ink, 0.3f)
    val foam = if (dark) s.c.ink else mix(g, Color.WHITE, 0.7f)
    p.printWaves(0.765f * s.h, s.h, deep, shallow, foam, 9.4f * u)
    // The cranes: one great, two lesser.
    val white = if (dark) mix(s.c.ink, Color.WHITE, 0.5f) else Color.rgb(0xFB, 0xF8, 0xF0)
    val black = if (dark) mix(g, Color.BLACK, 0.78f) else mix(s.c.ink, Color.BLACK, 0.35f)
    p.tancho(sunX + way * 21f * u, sunY - 0.19f * s.h, 30f * u, way < 0f, white, black, sealRed, wings = -0.7f, line = black)
    p.tancho(sunX - way * 24f * u, sunY + 0.14f * s.h, 27f * u, way < 0f, white, black, sealRed, wings = 0.8f, line = black)
    p.tancho(sunX - way * 5f * u, sunY - 0.005f * s.h, 52f * u, way < 0f, white, black, sealRed, wings = 1f, line = black)
    p.grain(0.8f)
    // A band across the foot for the author's name.
    c.drawRect(0f, s.h - 15f * u, s.w, s.h - 5.5f * u, s.fill(g))
    for (y in listOf(s.h - 15f * u, s.h - 5.5f * u)) c.drawRect(0f, y - 0.2f * u, s.w, y + 0.2f * u, s.fill(a))
    titleIn(
        s, t, RectF(9f * u, 8f * u, 91f * u, 0.215f * s.h), roman, 9f, s.c.ink, a, upper = true, tracking = 0.1f, pitch = 1.16f, place = -1,
        seriesSize = 2.7f, gap = 2.6f, smallest = 2.8f,
    )
    authorIn(s, t, RectF(8f * u, s.h - 14.2f * u, 92f * u, s.h - 6.3f * u), romanLight, 4.2f, s.c.ink, tracking = 0.24f)
}

/** One figure on a hill before a huge sun: a swordsman for wuxia, a samurai in a straw hat for old Japan. */
internal fun CoverGenerator.loneSwordsman(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    c.drawColor(g)
    val p = Painter(c, whole(s), rnd)
    val black = if (dark) mix(g, Color.BLACK, 0.74f) else mix(s.c.ink, Color.BLACK, 0.2f)
    p.sky(intArrayOf(g, g, mix(g, a, 0.12f)), floatArrayOf(0f, 0.45f, 0.8f))
    val way = if (rnd.nextBoolean()) 1f else -1f
    val sunX = (50f - way * 5f) * u
    val sunY = 0.5f * s.h
    val sunR = 33f * u
    if (dark) p.glow(sunX, sunY, sunR * 1.7f, a, 70)
    c.drawCircle(sunX, sunY, sunR, s.fill(a))
    for ((dy, from, to) in listOf(Triple(0.22f, -0.4f, 1.5f), Triple(0.4f, -1.4f, 0.3f), Triple(0.56f, -0.5f, 1.3f))) {
        c.drawRoundRect(RectF(sunX + from * sunR, sunY + dy * sunR, sunX + to * sunR, sunY + dy * sunR + 1.5f * u), 0.75f * u, 0.75f * u, s.fill(g))
    }
    // Far hills across the sun's foot, and haze.
    p.layer(p.ridge(0.84f, 0.685f, rough = 0.45f), mix(g, black, 0.42f), mix(g, black, 0.16f), 0.68f, 0.9f)
    p.mist(0.79f, 0.08f, g, 150)
    // The hill he stands on.
    val topX = (50f + way * 9f) * u
    fun ground(x: Float): Float {
        val d = (x - topX) / (38f * u)
        return (0.855f - 0.085f * exp(-d * d) + 0.012f * sin(x / u * 0.21f)) * s.h
    }
    val hill = Path().apply {
        moveTo(-2f, s.h + 2f)
        var x = -2f
        while (x <= s.w + 2f) {
            lineTo(x, ground(x))
            x += u
        }
        lineTo(s.w + 2f, s.h + 2f)
        close()
    }
    c.drawPath(hill, s.fill(black))
    p.grass(0f, s.w, ::ground, black, 5f * u, lean = 0.4f * way)
    // Tall reeds at one side.
    for (k in 0 until 7) {
        val bx = (if (way > 0f) 4f + k * 3.2f else 96f - k * 3.2f) * u + (rnd.nextFloat() - 0.5f) * u
        val tall = (20f + rnd.nextFloat() * 22f) * u
        val by = ground(bx) + u
        c.drawPath(tapered(floatArrayOf(bx, by, bx + way * tall * 0.06f, by - tall * 0.55f, bx + way * tall * 0.24f, by - tall), 0.7f * u, 0.15f * u), s.fill(black))
        val head = Path()
        flame(head, bx + way * tall * 0.2f, by - tall * 0.92f, bx + way * tall * 0.36f, by - tall * 1.1f, 1.5f * u, 0.2f * way)
        c.drawPath(head, s.fill(black))
    }
    val samurai = when (art.genre) {
        CoverGenre.SAMURAI -> true
        CoverGenre.WUXIA -> false
        else -> rnd.nextBoolean()
    }
    val standX = topX + way * 1.5f * u
    if (samurai) p.ronin(standX, ground(standX) + 0.6f * u, 33f * u, -way, s.fill(black)) else p.swordsman(standX, ground(standX) + 0.6f * u, 33f * u, -way, s.fill(black))
    // Leaves on the wind, and birds far off.
    repeat(16) {
        val leaf = Path()
        val lx = rnd.nextFloat() * s.w
        val ly = (0.24f + rnd.nextFloat() * 0.56f) * s.h
        val turn = rnd.nextFloat() * 6.28f
        flame(leaf, lx, ly, lx + cos(turn) * 2.6f * u, ly + sin(turn) * 2.6f * u, 1f * u, 0.3f)
        c.drawPath(leaf, s.fill(faint(black, 150 + rnd.nextInt(100))))
    }
    p.birds(5, (sunX - way * sunR * 0.6f) / s.w, 0.3f, 0.16f, faint(black, 220))
    p.grain(0.8f)
    titleIn(
        s, t, RectF(9f * u, 8f * u, 91f * u, 0.205f * s.h), roman, 9.2f, s.c.ink, a, upper = true, tracking = 0.1f, pitch = 1.16f, place = -1,
        seriesSize = 2.7f, gap = 2.6f, smallest = 2.8f,
    )
    authorIn(s, t, RectF(9f * u, s.h - 12.5f * u, 91f * u, s.h - 4.5f * u), romanLight, 4.2f, showing(if (dark) s.c.ink else g, black, 4.5), tracking = 0.24f)
}

/** Two koi circling in a pond, seen from above, with lily pads and rings on the water. */
internal fun CoverGenerator.koi(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    c.drawColor(g)
    p.glow(s.w / 2, 0.52f * s.h, s.w * 0.75f, if (dark) Color.WHITE else a, if (dark) 20 else 16)
    val line = if (dark) mix(g, Color.BLACK, 0.7f) else mix(s.c.ink, Color.BLACK, 0.25f)
    val water = mix(g, if (dark) Color.WHITE else Color.BLACK, 0.13f)
    val cx = s.w / 2
    val cy = 0.525f * s.h
    // Rings on the water.
    val ring = s.stroke(water, 0.26f * u)
    for ((rx, ry, n) in listOf(Triple(cx, cy, 5), Triple(0.2f * s.w, 0.3f * s.h, 3), Triple(0.84f * s.w, 0.74f * s.h, 3))) {
        for (k in 1..n) c.drawCircle(rx, ry, (if (n == 5) 26f + k * 4.6f else 8f + k * 4f) * u, ring)
    }
    // Lily pads, one in flower.
    val pad = mix(g, if (dark) Color.WHITE else s.c.ink, 0.22f)
    val vein = s.stroke(mix(pad, g, 0.5f), 0.22f * u)
    val pads = listOf(Triple(0.17f * s.w, 0.3f * s.h, 9.5f * u), Triple(0.85f * s.w, 0.745f * s.h, 11.5f * u), Triple(0.12f * s.w, 0.8f * s.h, 6f * u), Triple(0.9f * s.w, 0.27f * s.h, 5f * u))
    for ((px, py, pr) in pads) {
        val cut = rnd.nextFloat() * 360f
        c.drawPath(Path().apply { moveTo(px, py); arcTo(RectF(px - pr, py - pr, px + pr, py + pr), cut, 336f); close() }, s.fill(pad).apply { setShadowLayer(1.2f * u, 0.5f * u, 0.8f * u, faint(Color.BLACK, if (dark) 110 else 50)) })
        for (k in 0 until 9) {
            val ang = Math.toRadians((cut + 24f + k * 36f).toDouble())
            c.drawLine(px, py, px + (cos(ang) * pr * 0.86f).toFloat(), py + (sin(ang) * pr * 0.86f).toFloat(), vein)
        }
    }
    val (bx, by, br) = pads[1]
    val petal = if (dark) mix(s.c.ink, Color.WHITE, 0.4f) else mix(Color.WHITE, a, 0.12f)
    for (ringOf in 0..1) for (k in 0 until 8) {
        val ang = Math.toRadians((k * 45f + ringOf * 22.5f).toDouble())
        val long = br * (if (ringOf == 0) 0.62f else 0.4f)
        val bloom = Path()
        flame(bloom, bx + (cos(ang) * br * 0.06f).toFloat(), by + (sin(ang) * br * 0.06f).toFloat(), bx + (cos(ang) * long).toFloat(), by + (sin(ang) * long).toFloat(), br * 0.26f, 0f)
        c.drawPath(bloom, s.fill(if (ringOf == 0) petal else mix(petal, a, 0.3f)))
        c.drawPath(bloom, s.stroke(faint(line, 150), 0.16f * u))
    }
    c.drawCircle(bx, by, br * 0.1f, s.fill(a))

    /** One fish, [long] from nose to tail, its middle at ([fx], [fy]), heading [turn] degrees and curving round to its right. */
    fun fish(fx: Float, fy: Float, long: Float, turn: Float, skin: Int, patch: Int, seed: Int) {
        val fr = Random(seed)
        val arc = long / 1.25f
        val at = FloatArray(2)
        fun bent(x: Float, y: Float) {
            val phi = x * long / arc
            at[0] = (arc - y * long) * sin(phi)
            at[1] = arc - (arc - y * long) * cos(phi)
        }
        fun Path.to(x: Float, y: Float) { bent(x, y); moveTo(at[0], at[1]) }
        fun Path.on(x: Float, y: Float) { bent(x, y); lineTo(at[0], at[1]) }
        fun Path.curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            bent(x1, y1); val ax = at[0]; val ay = at[1]
            bent(x2, y2); val bx2 = at[0]; val by2 = at[1]
            bent(x3, y3)
            cubicTo(ax, ay, bx2, by2, at[0], at[1])
        }
        c.save()
        c.translate(fx, fy)
        c.rotate(turn)
        c.translate(0f, -arc * 0.09f)
        val thin = s.stroke(line, 0.2f * u).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        val veil = mix(skin, g, 0.35f)
        // Fins first: the tail, and a pair at the shoulders and at the belly.
        val tail = Path().apply {
            to(-0.28f, -0.04f)
            curve(-0.36f, -0.1f, -0.46f, -0.22f, -0.61f, -0.25f)
            curve(-0.53f, -0.15f, -0.47f, -0.06f, -0.44f, 0f)
            curve(-0.47f, 0.07f, -0.52f, 0.17f, -0.63f, 0.27f)
            curve(-0.46f, 0.22f, -0.36f, 0.1f, -0.28f, 0.04f)
            close()
        }
        val fins = Path()
        for (side in listOf(-1f, 1f)) {
            fins.to(0.27f, 0.12f * side)
            fins.curve(0.21f, 0.24f * side, 0.09f, 0.33f * side, -0.03f, 0.33f * side)
            fins.curve(0.04f, 0.25f * side, 0.1f, 0.18f * side, 0.14f, 0.13f * side)
            fins.close()
            fins.to(-0.04f, 0.14f * side)
            fins.curve(-0.08f, 0.2f * side, -0.14f, 0.24f * side, -0.2f, 0.23f * side)
            fins.curve(-0.16f, 0.18f * side, -0.14f, 0.14f * side, -0.12f, 0.11f * side)
            fins.close()
        }
        for (fin in listOf(tail, fins)) {
            c.drawPath(fin, s.fill(veil))
            c.drawPath(fin, thin)
        }
        val rays = Path()
        for (k in -3..3) { rays.to(-0.31f, k * 0.008f); rays.on(-0.5f - 0.04f * (3 - abs(k)) * 0.3f, k * 0.07f) }
        for (side in listOf(-1f, 1f)) for (k in 0 until 3) { rays.to(0.22f, 0.13f * side); rays.on(0.06f + k * 0.05f, (0.3f - k * 0.045f) * side) }
        c.drawPath(rays, s.stroke(faint(line, 90), 0.14f * u))
        // The body, with the shadow it casts on the bottom of the pond.
        val body = Path().apply {
            to(0.5f, 0f)
            curve(0.45f, -0.09f, 0.31f, -0.148f, 0.12f, -0.152f)
            curve(-0.06f, -0.156f, -0.2f, -0.1f, -0.3f, -0.04f)
            on(-0.3f, 0.04f)
            curve(-0.2f, 0.1f, -0.06f, 0.156f, 0.12f, 0.152f)
            curve(0.31f, 0.148f, 0.45f, 0.09f, 0.5f, 0f)
            close()
        }
        c.drawPath(body, s.fill(skin).apply { setShadowLayer(1.6f * u, 1f * u, 1.5f * u, faint(Color.BLACK, if (dark) 120 else 60)) })
        c.save()
        c.clipPath(body)
        // Its markings.
        val marks = Path()
        for ((mx, wide) in listOf(0.42f to 0.1f, 0.12f + fr.nextFloat() * 0.1f to 0.13f, -0.14f - fr.nextFloat() * 0.08f to 0.09f)) {
            val my = (fr.nextFloat() - 0.5f) * 0.1f
            for (k in 0..30) {
                val ang = k / 30f * 6.2832f
                val rr = wide * (0.85f + 0.2f * sin(ang * 2f + mx * 40f) + 0.1f * sin(ang * 3f + mx * 17f))
                if (k == 0) marks.to(mx + cos(ang) * rr, my + sin(ang) * rr * 0.95f) else marks.on(mx + cos(ang) * rr, my + sin(ang) * rr * 0.95f)
            }
            marks.close()
        }
        c.drawPath(marks, s.fill(patch))
        // Scales down its back.
        val scales = Path()
        var sx = 0.3f
        var column = 0
        while (sx > -0.28f) {
            var sy = -0.14f + (if (column % 2 == 0) 0f else 0.02f)
            while (sy < 0.15f) {
                scales.to(sx, sy - 0.02f)
                bent(sx - 0.032f, sy); val mx2 = at[0]; val my2 = at[1]
                bent(sx, sy + 0.02f)
                scales.quadTo(mx2, my2, at[0], at[1])
                sy += 0.04f
            }
            sx -= 0.042f
            column++
        }
        c.drawPath(scales, s.stroke(faint(line, 70), 0.16f * u))
        // Light along its back.
        val back = Path().apply { to(0.36f, 0f); curve(0.2f, -0.03f, -0.1f, -0.03f, -0.26f, 0f); curve(-0.1f, 0.03f, 0.2f, 0.03f, 0.36f, 0f); close() }
        c.drawPath(back, s.fill(faint(Color.WHITE, 46)))
        c.restore()
        c.drawPath(body, thin)
        // Gills, eyes and barbels.
        val face = Path()
        for (side in listOf(-1f, 1f)) {
            face.to(0.31f, 0.146f * side); face.curve(0.34f, 0.09f * side, 0.345f, 0.04f * side, 0.335f, 0.012f * side)
            face.to(0.49f, 0.02f * side); face.curve(0.52f, 0.04f * side, 0.535f, 0.07f * side, 0.525f, 0.095f * side)
        }
        c.drawPath(face, thin)
        for (side in listOf(-1f, 1f)) {
            bent(0.41f, 0.078f * side)
            c.drawCircle(at[0], at[1], 0.017f * long, s.fill(line))
            c.drawCircle(at[0] - 0.004f * long, at[1] - 0.004f * long, 0.005f * long, s.fill(Color.WHITE))
        }
        c.restore()
    }
    val pale = if (dark) mix(s.c.ink, Color.WHITE, 0.45f) else Color.rgb(0xFB, 0xF7, 0xEE)
    val first = rnd.nextFloat() * 360f
    val out = 12.5f * u
    for (k in 0..1) {
        val ang = first + k * 180f
        val rad = Math.toRadians(ang.toDouble())
        fish(cx + (cos(rad) * out).toFloat(), cy + (sin(rad) * out).toFloat(), 46f * u, ang + 90f, if (k == 0) pale else a, if (k == 0) a else mix(line, a, 0.12f), art.seed + k * 17)
    }
    p.grain(0.7f)
    titleIn(
        s, t, RectF(9f * u, 8f * u, 91f * u, 0.2f * s.h), roman, 9f, s.c.ink, a, upper = true, tracking = 0.1f, pitch = 1.16f, place = -1,
        seriesSize = 2.7f, gap = 2.6f, smallest = 2.8f,
    )
    authorIn(s, t, RectF(9f * u, s.h - 13.5f * u, 91f * u, s.h - 5.5f * u), romanLight, 4.2f, s.c.ink, tracking = 0.24f)
}

/** Bamboo in ink down one side, a round moon behind its leaves, and a red seal. */
internal fun CoverGenerator.inkBamboo(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    c.drawColor(g)
    repeat(7) { p.glow(rnd.nextFloat() * s.w, rnd.nextFloat() * s.h, s.w * (0.2f + rnd.nextFloat() * 0.3f), if (dark) Color.WHITE else mix(s.c.ink, a, 0.3f), 12) }
    val brush = s.c.ink
    // The moon.
    val moonX = (68f + rnd.nextFloat() * 8f) * u
    val moonY = 0.25f * s.h
    if (dark) p.glow(moonX, moonY, 30f * u, a, 50)
    c.drawCircle(moonX, moonY, 13.5f * u, s.fill(a))

    /** A spray of leaves from one point, hanging the way the wind has them. */
    fun leaves(x: Float, y: Float, n: Int, long: Float, strength: Int) {
        for (k in 0 until n) {
            val ang = Math.toRadians((18f + rnd.nextFloat() * 70f + (if (rnd.nextInt(5) == 0) -70f else 0f)).toDouble())
            val reach = long * (0.7f + rnd.nextFloat() * 0.5f)
            val leaf = Path()
            flame(leaf, x, y, x + (cos(ang) * reach).toFloat(), y + (sin(ang) * reach).toFloat(), reach * 0.19f, (rnd.nextFloat() - 0.5f) * 0.24f)
            c.drawPath(leaf, s.fill(faint(brush, (strength * (0.7f + rnd.nextFloat() * 0.3f)).toInt().coerceIn(20, 255))))
        }
    }
    // Three canes, the nearest darkest.
    for (k in 2 downTo 0) {
        var x = (10f + k * 8.5f + rnd.nextFloat() * 3f) * u
        val wide = (4f - k * 0.9f) * u
        val strength = 240 - k * 62
        val lean = (rnd.nextFloat() - 0.3f) * 0.05f
        var y = s.h + 3f * u
        while (y > -6f * u) {
            val long = (16f + rnd.nextFloat() * 8f) * u
            val x1 = x + lean * long
            val joint = Path().apply {
                moveTo(x - wide / 2, y)
                cubicTo(x - wide * 0.42f, y - long * 0.3f, x1 - wide * 0.42f, y - long * 0.7f, x1 - wide / 2, y - long)
                lineTo(x1 + wide / 2, y - long)
                cubicTo(x1 + wide * 0.42f, y - long * 0.7f, x + wide * 0.42f, y - long * 0.3f, x + wide / 2, y)
                close()
            }
            c.drawPath(joint, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(x - wide / 2, 0f, x + wide / 2, 0f, intArrayOf(faint(brush, strength), faint(brush, strength * 5 / 10), faint(brush, strength * 8 / 10)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
            // The joint, and now and then a twig from it with its leaves.
            c.drawLine(x1 - wide * 0.62f, y - long - 0.35f * u, x1 + wide * 0.62f, y - long - 0.35f * u, s.stroke(faint(brush, min(255, strength + 15)), 0.5f * u).apply { strokeCap = Paint.Cap.ROUND })
            if (rnd.nextInt(5) < 3 && y - long > 0.06f * s.h) {
                val reach = (13f + rnd.nextFloat() * 12f) * u
                val up = Math.toRadians((-18f - rnd.nextFloat() * 34f).toDouble())
                val tx = x1 + (cos(up) * reach).toFloat()
                val ty = y - long + (sin(up) * reach).toFloat()
                c.drawPath(tapered(floatArrayOf(x1, y - long, (x1 + tx) / 2, (y - long + ty) / 2 + 0.6f * u, tx, ty), 0.6f * u, 0.14f * u), s.fill(faint(brush, strength)))
                leaves(tx, ty, 3 + rnd.nextInt(3), (10f + rnd.nextFloat() * 5f) * u, strength)
                leaves((x1 + tx) / 2, (y - long + ty) / 2, 2 + rnd.nextInt(2), (8f + rnd.nextFloat() * 4f) * u, strength - 30)
            }
            x = x1
            y -= long + 0.7f * u
        }
    }
    // A branch from above, across the moon.
    val fromX = moonX + 18f * u
    c.drawPath(tapered(floatArrayOf(fromX, -2f * u, moonX + 6f * u, 0.08f * s.h, moonX - 12f * u, 0.15f * s.h, moonX - 26f * u, 0.17f * s.h), 0.8f * u, 0.14f * u), s.fill(faint(brush, 230)))
    leaves(moonX + 6f * u, 0.08f * s.h, 4, 12f * u, 235)
    leaves(moonX - 12f * u, 0.15f * s.h, 5, 13f * u, 235)
    leaves(moonX - 26f * u, 0.17f * s.h, 4, 11f * u, 220)
    p.seal(88.5f * u, s.h - 22f * u, 6f * u, if (dark) a else sealRed, if (dark) s.c.accentInk else g)
    p.grain(1f)
    titleIn(
        s, t, RectF(38f * u, 0.43f * s.h, 92f * u, 0.72f * s.h), garamond, 10f, s.c.ink, a, pitch = 1.1f, align = Layout.Alignment.ALIGN_OPPOSITE,
        seriesSize = 2.8f, gap = 2.8f, smallest = 3f,
    )
    authorIn(s, t, RectF(38f * u, s.h - 15.5f * u, 83f * u, s.h - 7f * u), romanLight, 4.2f, s.c.ink, tracking = 0.2f, align = Layout.Alignment.ALIGN_OPPOSITE)
}

/** A shrine gate standing in still water at nightfall, the moon behind it, and lanterns set afloat. */
internal fun CoverGenerator.shrineGate(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    c.drawColor(g)
    val p = Painter(c, whole(s), rnd)
    val black = if (dark) mix(g, Color.BLACK, 0.74f) else mix(s.c.ink, Color.BLACK, 0.2f)
    val shore = 0.655f * s.h
    p.sky(intArrayOf(mix(g, Color.BLACK, if (dark) 0.2f else 0f), g, mix(g, a, 0.16f)), floatArrayOf(0f, 0.36f, 0.655f))
    if (dark) p.stars(70, 0.4f, s.c.ink)
    // The moon.
    val moon = if (dark) mix(s.c.ink, a, 0.2f) else mix(g, a, 0.55f)
    val moonX = (50f + (rnd.nextFloat() - 0.5f) * 10f) * u
    val moonY = 0.385f * s.h
    val moonR = 21f * u
    p.glow(moonX, moonY, moonR * 2.2f, moon, if (dark) 70 else 40)
    c.drawCircle(moonX, moonY, moonR, s.fill(moon))
    // Hills along the far shore.
    p.layer(p.ridge(0.66f, 0.585f, rough = 0.4f), mix(g, black, 0.3f), mix(g, black, 0.12f), 0.58f, 0.67f)
    p.layer(p.ridge(0.665f, 0.62f, rough = 0.5f), mix(g, black, 0.55f), mix(g, black, 0.4f), 0.62f, 0.67f)
    // The water, darker, with the moon lying on it.
    val water = mix(g, Color.BLACK, if (dark) 0.34f else 0.1f)
    c.drawRect(0f, shore, s.w, s.h, Paint().apply { shader = LinearGradient(0f, shore, 0f, s.h, mix(g, water, 0.7f), water, Shader.TileMode.CLAMP) })
    var wy = shore + 1.5f * u
    while (wy < s.h) {
        val f = (wy - shore) / (s.h - shore)
        val wide = moonR * (0.5f + 1.4f * f) * (0.5f + rnd.nextFloat() * 0.5f)
        c.drawLine(moonX - wide + (rnd.nextFloat() - 0.5f) * 6f * u, wy, moonX + wide, wy, s.stroke(faint(moon, (120 * (1f - f * 0.7f)).toInt()), 0.4f * u).apply { strokeCap = Paint.Cap.ROUND })
        wy += (1.3f + f * 2f) * u
    }
    // The gate, and what the water shows of it.
    val gateX = s.w / 2
    val gateY = shore + 5.5f * u
    val size = 56f * u
    c.save()
    c.scale(1f, -0.56f, 0f, gateY)
    p.torii(gateX, gateY, size, mix(a, water, 0.66f), mix(a, water, 0.8f), mix(black, water, 0.6f))
    c.restore()
    var ry = gateY + 1.2f * u
    while (ry < gateY + size * 0.54f) {
        c.drawLine(gateX - size * 0.6f, ry, gateX + size * 0.6f, ry, s.stroke(faint(water, 190), 0.36f * u))
        ry += 1.5f * u
    }
    // (What the water shows fades out as it nears us.)
    c.drawRect(0f, gateY + size * 0.16f, s.w, s.h, Paint().apply { shader = LinearGradient(0f, gateY + size * 0.16f, 0f, gateY + size * 0.5f, faint(water, 0), water, Shader.TileMode.CLAMP) })
    p.torii(gateX, gateY, size, a, mix(a, black, 0.5f), black)
    p.mist(0.66f, 0.05f, mix(g, moon, 0.5f), 90)
    // Lanterns on the water.
    repeat(6) {
        val lx = (8f + rnd.nextFloat() * 84f) * u
        val ly = shore + (8f + rnd.nextFloat() * 24f) * u
        if (abs(lx - gateX) < size * 0.4f && ly < gateY + 6f * u) return@repeat
        val big = (1.1f + (ly - shore) / (40f * u)) * u
        p.glow(lx, ly, big * 6f, a, 110)
        c.drawRoundRect(RectF(lx - big, ly - big * 1.3f, lx + big, ly + big * 0.6f), big * 0.3f, big * 0.3f, s.fill(mix(a, Color.WHITE, 0.5f)))
        c.drawLine(lx - big * 1.6f, ly + big * 0.9f, lx + big * 1.6f, ly + big * 0.9f, s.stroke(faint(a, 150), 0.3f * u).apply { strokeCap = Paint.Cap.ROUND })
        c.drawLine(lx - big, ly + big * 2f, lx + big, ly + big * 2f, s.stroke(faint(a, 90), 0.3f * u).apply { strokeCap = Paint.Cap.ROUND })
    }
    p.grain(0.8f)
    titleIn(
        s, t, RectF(9f * u, 8f * u, 91f * u, 0.205f * s.h), roman, 9.2f, s.c.ink, a, upper = true, tracking = 0.1f, pitch = 1.16f, place = -1,
        seriesSize = 2.7f, gap = 2.6f, smallest = 2.8f,
    )
    authorIn(s, t, RectF(9f * u, s.h - 12.5f * u, 91f * u, s.h - 4.5f * u), romanLight, 4.2f, showing(s.c.ink, water, 4.5), tracking = 0.24f)
}

/** Silk woven with curling clouds, cranes flying among them, and the title on a plate in the middle. */
internal fun CoverGenerator.cloudCranes(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    board(s, art.seed, vignette = 100)
    val plate = RectF(19f * u, 0.36f * s.h, 81f * u, 0.64f * s.h)
    val name = RectF(24f * u, s.h - 17.5f * u, 76f * u, s.h - 7f * u)
    // The clouds, row on row.
    val clouds = Path()
    val curls = Path()
    var rowOf = 0
    var y = 0.06f * s.h
    while (y < s.h) {
        var x = (if (rowOf % 2 == 0) 14f else 34f) * u + (rnd.nextFloat() - 0.5f) * 5f * u
        while (x < s.w + 8f * u) {
            ruyiCloud(clouds, curls, x, y + (rnd.nextFloat() - 0.5f) * 2.4f * u, (0.95f + rnd.nextFloat() * 0.3f) * u)
            x += 40f * u
        }
        y += 0.118f * s.h
        rowOf++
    }
    c.drawPath(clouds, s.fill(mix(g, a, 0.13f)))
    c.drawPath(clouds, s.stroke(faint(a, 190), 0.26f * u).apply { strokeJoin = Paint.Join.ROUND })
    c.drawPath(curls, s.stroke(faint(a, 190), 0.26f * u).apply { strokeCap = Paint.Cap.ROUND })
    // Cranes among them.
    val white = if (dark) mix(s.c.ink, Color.WHITE, 0.5f) else Color.rgb(0xFB, 0xF8, 0xF0)
    val black = if (dark) mix(g, Color.BLACK, 0.78f) else mix(s.c.ink, Color.BLACK, 0.35f)
    val way = rnd.nextBoolean()
    p.tancho((if (way) 30f else 70f) * u, 0.22f * s.h, 34f * u, !way, white, black, sealRed, wings = 1f, line = black)
    p.tancho((if (way) 74f else 26f) * u, 0.3f * s.h, 24f * u, way, white, black, sealRed, wings = -0.7f, line = black)
    p.tancho((if (way) 66f else 34f) * u, 0.775f * s.h, 32f * u, way, white, black, sealRed, wings = 0.9f, line = black)
    p.tancho((if (way) 20f else 80f) * u, 0.72f * s.h, 20f * u, !way, white, black, sealRed, wings = -0.6f, line = black)
    // The plates for the words.
    val deep = mix(g, Color.BLACK, if (dark) 0.34f else 0.06f)
    for ((r, round) in listOf(plate to 3f, name to 2f)) {
        c.drawRoundRect(r, round * u, round * u, s.fill(deep).apply { setShadowLayer(1.6f * u, 0f, 0.8f * u, faint(Color.BLACK, if (dark) 140 else 60)) })
        c.drawRoundRect(RectF(r.left + 1.1f * u, r.top + 1.1f * u, r.right - 1.1f * u, r.bottom - 1.1f * u), (round - 0.8f) * u, (round - 0.8f) * u, s.stroke(a, 0.42f * u))
        c.drawRoundRect(RectF(r.left + 1.9f * u, r.top + 1.9f * u, r.right - 1.9f * u, r.bottom - 1.9f * u), (round - 1.4f) * u, (round - 1.4f) * u, s.stroke(a, 0.16f * u))
    }
    p.grain(0.8f)
    titleIn(
        s, t, RectF(plate.left + 5.5f * u, plate.top + 4.5f * u, plate.right - 5.5f * u, plate.bottom - 4.5f * u), roman, 8.6f, showing(s.c.ink, deep, 4.5), a, upper = true, tracking = 0.08f, pitch = 1.16f,
        seriesSize = 2.6f, gap = 2.4f, smallest = 2.6f, foil = dark,
    )
    authorIn(s, t, RectF(name.left + 4f * u, name.top + 2.4f * u, name.right - 4f * u, name.bottom - 2.4f * u), romanLight, 4f, showing(s.c.ink, deep, 4.5), tracking = 0.2f)
}

/** A family crest, large and plain on cloth, with the title under it between two rules. */
internal fun CoverGenerator.crest(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val rnd = Random(art.seed)
    cloth(s, art.seed)
    val mark = s.c.ink
    val cx = s.w / 2
    val cy = 0.36f * s.h
    val r = 23f * u
    val solid = s.fill(mark)
    val cut = s.fill(g)
    val eastern = art.genre == CoverGenre.SAMURAI || art.genre == CoverGenre.WUXIA
    when (if (eastern) rnd.nextInt(5) else rnd.nextInt(6)) {
        0 -> {
            // Three commas chasing one another round: each a round head, its tail lying along the rim as far as the next.
            val head = r * 0.464f
            val out = r - head
            val turn = rnd.nextFloat() * 120f
            fun at(k: Int, far: Float): Pair<Float, Float> {
                val ang = Math.toRadians((turn + k * 120f).toDouble())
                return cx + (cos(ang) * far).toFloat() to cy + (sin(ang) * far).toFloat()
            }
            for (k in 0 until 3) {
                val (hx, hy) = at(k, out)
                val (nx, ny) = at(k + 1, out)
                val tail = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
                tail.op(Path().apply { addCircle(cx, cy, out, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                tail.op(Path().apply {
                    moveTo(cx, cy)
                    for (step in 0..6) {
                        val ang = Math.toRadians((turn + k * 120f + step * 20f).toDouble())
                        lineTo(cx + (cos(ang) * r * 2f).toFloat(), cy + (sin(ang) * r * 2f).toFloat())
                    }
                    close()
                }, Path.Op.INTERSECT)
                tail.op(Path().apply { addCircle(nx, ny, head + r * 0.055f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                tail.op(Path().apply { addCircle(hx, hy, head, Path.Direction.CW) }, Path.Op.UNION)
                c.drawPath(tail, solid)
            }
        }
        1 -> {
            // A plum blossom: five round petals about a sixth.
            for (k in 0 until 5) {
                val ang = Math.toRadians((k * 72f - 90f).toDouble())
                c.drawCircle(cx + (cos(ang) * r * 0.6f).toFloat(), cy + (sin(ang) * r * 0.6f).toFloat(), r * 0.37f, solid)
            }
            c.drawCircle(cx, cy, r * 0.3f, cut)
            c.drawCircle(cx, cy, r * 0.21f, solid)
            for (k in 0 until 5) {
                val ang = Math.toRadians((k * 72f - 54f).toDouble())
                c.drawLine(cx + (cos(ang) * r * 0.28f).toFloat(), cy + (sin(ang) * r * 0.28f).toFloat(), cx + (cos(ang) * r * 0.5f).toFloat(), cy + (sin(ang) * r * 0.5f).toFloat(), s.stroke(g, r * 0.035f))
            }
            c.drawCircle(cx, cy, r, s.stroke(mark, r * 0.1f))
        }
        2 -> {
            // Bamboo leaves fanned in a ring.
            c.drawCircle(cx, cy, r * 0.95f, s.stroke(mark, r * 0.11f))
            for (k in -2..2) {
                val ang = Math.toRadians((-90f + k * 34f).toDouble())
                val leaf = Path()
                flame(leaf, cx, cy + r * 0.56f, cx + (cos(ang) * r * 1.26f).toFloat(), cy + r * 0.56f + (sin(ang) * r * 1.26f).toFloat(), r * 0.34f, 0f)
                c.drawPath(leaf, solid)
                c.drawLine(cx, cy + r * 0.56f, cx + (cos(ang) * r * 1.1f).toFloat(), cy + r * 0.56f + (sin(ang) * r * 1.1f).toFloat(), s.stroke(g, r * 0.03f))
            }
            c.drawCircle(cx, cy + r * 0.56f, r * 0.1f, solid)
        }
        3 -> {
            // Waves in a round.
            val round = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            c.drawPath(round, solid)
            c.save()
            c.clipPath(round)
            val swell = r * 0.5f
            var rowOf = 0
            var wy = cy - r - swell * 0.2f
            while (wy < cy + r + swell) {
                var wx = cx - r - swell + (if (rowOf % 2 == 0) 0f else swell)
                while (wx < cx + r + swell) {
                    c.drawCircle(wx, wy, swell, solid)
                    for (k in 0 until 3) c.drawCircle(wx, wy, swell * (0.92f - k * 0.27f), s.stroke(g, r * 0.045f))
                    wx += swell * 2
                }
                wy += swell * 0.5f
                rowOf++
            }
            c.restore()
            c.drawCircle(cx, cy, r, s.stroke(mark, r * 0.1f))
        }
        4 -> {
            // A flower in a tortoise-shell: six sides within six sides.
            fun six(out: Float) = Path().apply {
                for (k in 0 until 6) {
                    val ang = Math.toRadians((k * 60f - 90f).toDouble())
                    val px = cx + (cos(ang) * out).toFloat()
                    val py = cy + (sin(ang) * out).toFloat()
                    if (k == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            c.drawPath(six(r * 1.04f), solid)
            c.drawPath(six(r * 0.86f), cut)
            c.drawPath(six(r * 0.74f), solid)
            c.drawPath(six(r * 0.6f), cut)
            for (k in 0 until 4) {
                val ang = Math.toRadians((k * 90f).toDouble())
                val petal = Path()
                flame(petal, cx, cy, cx + (cos(ang) * r * 0.5f).toFloat(), cy + (sin(ang) * r * 0.5f).toFloat(), r * 0.34f, 0f)
                c.drawPath(petal, solid)
            }
            c.drawCircle(cx, cy, r * 0.09f, cut)
        }
        else -> {
            // The book's own emblem in a ring.
            c.drawCircle(cx, cy, r * 0.94f, s.stroke(mark, r * 0.12f))
            CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy, r * 0.6f, solid)
        }
    }
    c.drawCircle(cx, cy, r * 1.22f, s.stroke(a, 0.45f * u))
    // The title between two rules.
    val box = RectF(12f * u, 0.615f * s.h, 88f * u, 0.845f * s.h)
    for (y in listOf(box.top, box.bottom)) c.drawRect(box.left, y - 0.25f * u, box.right, y + 0.25f * u, s.fill(a))
    Painter(c, whole(s), rnd).grain(0.6f)
    titleIn(
        s, t, RectF(box.left, box.top + 3.4f * u, box.right, box.bottom - 3.4f * u), roman, 8.6f, s.c.ink, a, upper = true, tracking = 0.12f, pitch = 1.16f,
        seriesSize = 2.6f, gap = 2.4f, smallest = 2.6f,
    )
    authorIn(s, t, RectF(12f * u, s.h - 13.5f * u, 88f * u, s.h - 5.5f * u), romanLight, 4.2f, s.c.ink, tracking = 0.26f)
}

/** Paper lanterns glowing on their strings at night, over the roofs of a town. */
internal fun CoverGenerator.paperLanterns(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val rnd = Random(art.seed)
    c.drawColor(g)
    val p = Painter(c, whole(s), rnd)
    val black = if (dark) mix(g, Color.BLACK, 0.74f) else mix(s.c.ink, Color.BLACK, 0.2f)
    p.sky(intArrayOf(mix(g, Color.BLACK, if (dark) 0.25f else 0f), g, mix(g, a, 0.1f)), floatArrayOf(0f, 0.5f, 1f))
    if (dark) p.stars(60, 0.9f, s.c.ink)
    // Lights far off, out of focus.
    repeat(22) {
        val bx = rnd.nextFloat() * s.w
        val by = (0.3f + rnd.nextFloat() * 0.55f) * s.h
        c.drawCircle(bx, by, (1.2f + rnd.nextFloat() * 2.6f) * u, s.fill(faint(a, 14 + rnd.nextInt(22))))
    }
    // The lanterns, each on its string, the nearest largest.
    val lit = mix(a, Color.WHITE, 0.55f)
    val shade = mix(a, Color.BLACK, 0.34f)
    val hung = listOf(
        floatArrayOf(14f, 0.235f, 9f), floatArrayOf(34f, 0.39f, 11.5f), floatArrayOf(55f, 0.17f, 8f), floatArrayOf(73f, 0.35f, 10.5f), floatArrayOf(90f, 0.2f, 7.5f),
    )
    for (lantern in hung) {
        val lx = (lantern[0] + (rnd.nextFloat() - 0.5f) * 3f) * u
        val ly = lantern[1] * s.h
        val r = lantern[2] * u
        val tall = r * 0.9f
        c.drawLine(lx, -u, lx, ly - tall, s.stroke(black, 0.3f * u))
        p.glow(lx, ly, r * 2.3f, a, 95)
        c.drawOval(RectF(lx - r, ly - tall, lx + r, ly + tall), Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(lx, ly + tall * 0.15f, r * 1.05f, intArrayOf(lit, a, shade), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) })
        // Its ribs, its caps, and the tassel under it.
        for (k in 1..3) c.drawOval(RectF(lx - r * k / 4f, ly - tall, lx + r * k / 4f, ly + tall), s.stroke(faint(shade, 120), 0.2f * u))
        c.drawLine(lx, ly - tall, lx, ly + tall, s.stroke(faint(shade, 120), 0.2f * u))
        for (cap in listOf(ly - tall - 0.9f * u, ly + tall - 0.3f * u)) c.drawRoundRect(RectF(lx - r * 0.36f, cap, lx + r * 0.36f, cap + 1.2f * u), 0.3f * u, 0.3f * u, s.fill(black))
        c.drawLine(lx, ly + tall + 0.9f * u, lx, ly + tall + r * 0.5f, s.stroke(a, 0.3f * u))
        val tassel = Path()
        flame(tassel, lx, ly + tall + r * 0.45f, lx + (rnd.nextFloat() - 0.5f) * r * 0.2f, ly + tall + r * 1.15f, r * 0.2f, 0f)
        c.drawPath(tassel, s.fill(a))
    }
    // Roofs below, their eaves turned up.
    val roofs = Path()
    var x = -8f * u
    while (x < s.w + 8f * u) {
        val wide = (20f + rnd.nextFloat() * 14f) * u
        val top = (0.865f + rnd.nextFloat() * 0.045f) * s.h
        roofs.moveTo(x - 3f * u, top + 5f * u)
        roofs.quadTo(x + wide * 0.12f, top + 4.4f * u, x + wide * 0.3f, top)
        roofs.lineTo(x + wide * 0.7f, top)
        roofs.quadTo(x + wide * 0.88f, top + 4.4f * u, x + wide + 3f * u, top + 5f * u)
        roofs.lineTo(x + wide + 3f * u, s.h + 2f)
        roofs.lineTo(x - 3f * u, s.h + 2f)
        roofs.close()
        roofs.addRect(x + wide * 0.3f, top - 0.8f * u, x + wide * 0.7f, top + 0.2f * u, Path.Direction.CW)
        x += wide * 0.8f
    }
    c.drawPath(roofs, s.fill(black))
    p.grain(0.8f)
    titleIn(
        s, t, RectF(10f * u, 0.52f * s.h, 90f * u, 0.82f * s.h), roman, 9.4f, s.c.ink, a, upper = true, tracking = 0.1f, pitch = 1.16f,
        seriesSize = 2.7f, gap = 2.6f, smallest = 2.8f, shadow = faint(Color.BLACK, if (dark) 150 else 0), shadowRadius = 1f,
    )
    authorIn(s, t, RectF(9f * u, s.h - 11.5f * u, 91f * u, s.h - 4f * u), romanLight, 4.2f, showing(if (dark) s.c.ink else g, black, 4.5), tracking = 0.24f)
}
