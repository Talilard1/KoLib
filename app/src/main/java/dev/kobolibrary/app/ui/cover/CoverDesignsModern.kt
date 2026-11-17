package dev.kobolibrary.app.ui.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Designs after the modern paperback: type as the picture, flat shapes and cut paper, one emblem on a plain
 * ground, gradients, grids of symbols, lines that fool the eye. Every one is drawn here in the book's own
 * colours; none copies a particular book.
 */

// ---- what these share --------------------------------------------------------------------------------------

/** One line of big type: [text] in [face], as big as fills [wide] (up to [tallest] for its capitals); gives how tall it was drawn. */
internal fun bigLine(
    c: Canvas, text: String, face: Typeface, x: Float, top: Float, wide: Float, tallest: Float, colour: Int,
    align: Paint.Align = Paint.Align.CENTER, measureOnly: Boolean = false,
): Float {
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face
        textSize = 100f
        color = colour
        textAlign = align
    }
    val bounds = android.graphics.Rect()
    p.getTextBounds(text, 0, text.length, bounds)
    val across = max(1f, p.measureText(text))
    val up = max(1, -bounds.top).toFloat()
    val size = min(100f * wide / across, 100f * tallest / up)
    p.textSize = size
    val tall = up * size / 100f
    if (!measureOnly) c.drawText(text, x, top + tall, p)
    return tall
}

/** The title broken into short lines for setting big, a few words to each; null if it's too long for that. */
internal fun shortLines(title: String, most: Int = 4, target: Int = 11): List<String>? {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty() || words.size > 9 || words.any { it.length > 16 }) return null
    val lines = ArrayList<String>()
    var line = ""
    for (w in words) {
        if (line.isEmpty()) line = w
        else if (line.length + 1 + w.length <= target) line += " $w"
        else {
            lines += line
            line = w
        }
    }
    lines += line
    return lines.takeIf { it.size <= most }
}

/** A smooth closed shape through [pts] (x, y by turns). */
internal fun smoothLoop(pts: FloatArray): Path {
    val n = pts.size / 2
    val path = Path()
    fun x(i: Int) = pts[(i % n) * 2]
    fun y(i: Int) = pts[(i % n) * 2 + 1]
    path.moveTo((x(0) + x(1)) / 2, (y(0) + y(1)) / 2)
    for (i in 1..n) path.quadTo(x(i), y(i), (x(i) + x(i + 1)) / 2, (y(i) + y(i + 1)) / 2)
    path.close()
    return path
}

/** A shape cut freehand: a round about ([cx], [cy]), swelling and dipping as it goes. */
internal fun blob(cx: Float, cy: Float, r: Float, seed: Int, wobble: Float = 0.26f, squash: Float = 1f): Path {
    val n = 28
    val pts = FloatArray(n * 2)
    for (k in 0 until n) {
        val a = k / n.toFloat() * 6.2832f
        val rr = r * (1f - wobble + 2f * wobble * CoverMotifs.cloudy(3f + cos(a) * 1.1f, 3f + sin(a) * 1.1f, seed, 2))
        pts[k * 2] = cx + cos(a) * rr
        pts[k * 2 + 1] = cy + sin(a) * rr * squash
    }
    return smoothLoop(pts)
}

/** A frond: a bowed stem from ([x0], [y0]) to ([x1], [y1]) with leaves in pairs along it. */
internal fun frond(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, bow: Float, leaves: Int, leafLength: Float, stem: Float) {
    val mx = (x0 + x1) / 2 - (y1 - y0) * bow
    val my = (y0 + y1) / 2 + (x1 - x0) * bow
    for (k in 1..leaves) {
        val f = k / (leaves + 0.6f)
        val g = 1f - f
        val px = g * g * x0 + 2 * g * f * mx + f * f * x1
        val py = g * g * y0 + 2 * g * f * my + f * f * y1
        val dx = 2 * g * (mx - x0) + 2 * f * (x1 - mx)
        val dy = 2 * g * (my - y0) + 2 * f * (y1 - my)
        val along = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val len = leafLength * (1f - f * 0.45f)
        CoverMotifs.leaf(path, px, py, len, along - 52f, 0.36f)
        CoverMotifs.leaf(path, px, py, len, along + 52f, 0.36f)
    }
    // The stem itself, a thin lens along the bow.
    val nx = -(y1 - y0)
    val ny = x1 - x0
    val l = max(1f, hypot(nx, ny))
    val ox = nx / l * stem
    val oy = ny / l * stem
    path.moveTo(x0 - ox, y0 - oy)
    path.quadTo(mx - ox, my - oy, x1, y1)
    path.quadTo(mx + ox, my + oy, x0 + ox, y0 + oy)
    path.close()
}

/** The series (above) and the title, flush left under one another from [top], in plain modern type; gives where they end. */
private fun CoverGenerator.plainHead(
    s: Sheet, t: Words, left: Float, top: Float, wide: Float, room: Float, face: Typeface, largest: Float,
    upper: Boolean = false, pitch: Float = 1.08f, ink: Int = s.c.ink, seriesInk: Int = s.c.accent, seriesFace: Typeface = sansBold, tracking: Float = 0f,
): Float = titleIn(
    s, t, RectF(left, top, left + wide, top + room), face, largest, ink, seriesInk, upper = upper, align = Layout.Alignment.ALIGN_NORMAL, pitch = pitch,
    tracking = tracking, seriesFace = seriesFace, seriesSize = 2.9f, seriesTracking = 0.14f, seriesAbove = true, gap = 3.4f, place = -1, smallest = 4.2f,
).bottom

// ---- type as the picture -----------------------------------------------------------------------------------

/** Nothing but the title, every line as wide as the cover, over a band of colour with the author's name. */
internal fun CoverGenerator.typeBlock(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    Painter(c, whole(s), Random(art.seed)).grain(0.6f)
    val left = 7f * u
    val wide = s.w - 14f * u
    val band = 21f * u
    s.rect(0f, s.h - band, s.w, s.h, s.c.accent)
    var y = 8f * u
    t.series?.let { fit(caps(it), sansBold, 3f * u, 2.1f * u, wide, 7.6f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.14f, pitch = 1.4f) }?.let {
        it.draw(c, left, y, s.seriesWords(s.c.accent))
        y += it.height + 3.2f * u
    }
    s.rect(left, y, left + wide, y + 0.9f * u, s.deco(s.c.ink))
    y += 4.4f * u
    val bottom = s.h - band - 6f * u
    val lines = shortLines(caps(t.title), most = 5, target = 10)
    if (lines == null) {
        titleIn(s, t, RectF(left, y, left + wide, bottom), condensed, 21f, s.c.ink, s.c.ink, upper = true, align = Layout.Alignment.ALIGN_NORMAL, pitch = 0.98f, place = -1, series = false, smallest = 5f)
    } else {
        val gap = 2.8f * u
        val tallest = 30f * u
        val heights = lines.map { bigLine(c, it, condensed, 0f, 0f, wide, tallest, 0, measureOnly = true) }
        val total = heights.sum() + gap * (lines.size - 1)
        val scale = min(1f, (bottom - y) / total)
        // The shortest line is set in the second colour.
        val pick = if (lines.size > 1) lines.indices.minByOrNull { lines[it].length }!! else -1
        for (i in lines.indices) {
            val drawn = bigLine(c, lines[i], condensed, left, y, wide * scale, tallest * scale, s.words(if (i == pick) s.c.accent else s.c.ink), Paint.Align.LEFT)
            y += drawn + gap * scale
        }
    }
    authorIn(s, t, RectF(left, s.h - band + 3f * u, left + wide, s.h - 3f * u), sansBold, 4.8f, s.c.accentInk, tracking = 0.1f, align = Layout.Alignment.ALIGN_NORMAL)
}

/** The title again and again down the cover, fainter each time. */
internal fun CoverGenerator.echo(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    Painter(c, whole(s), Random(art.seed)).grain(0.6f)
    val left = 8f * u
    val wide = s.w - 16f * u
    var y = 9f * u
    t.series?.let { fit(caps(it), sansBold, 2.9f * u, 2.1f * u, wide, 7.4f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.14f, pitch = 1.4f) }?.let {
        it.draw(c, left, y, s.seriesWords(s.c.accent))
        y += it.height + 4f * u
    }
    val title = fit(caps(t.title), condensed, 17f * u, 5.6f * u, wide, 0.19f * s.h, Layout.Alignment.ALIGN_NORMAL, pitch = 0.98f)!!
    title.draw(c, left, y, s.words(s.c.ink))
    val step = title.height + 3f * u
    val fades = intArrayOf(255, 170, 110, 66, 34)
    var k = 0
    var ey = y + step
    while (k < fades.size && ey + title.height < s.h - 26f * u) {
        title.draw(c, left, ey, faint(s.words(s.c.accent), fades[k]))
        ey += step
        k++
    }
    authorIn(s, t, RectF(left, s.h - 21f * u, left + wide, s.h - 8f * u), sansBold, 4.4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** Type and nothing else: rules at head and foot, the title in an old-style face, and a small device. */
internal fun CoverGenerator.typographic(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    Painter(c, whole(s), Random(art.seed)).grain(0.5f)
    val cx = s.w / 2
    val rule = s.deco(s.c.ink)
    for (y in listOf(8f * u, s.h - 8f * u)) {
        s.rule(cx, y, 84f * u, 1.1f * u, s.deco(s.c.accent))
        s.rule(cx, y + (if (y < s.h / 2) 1.9f else -1.9f) * u, 84f * u, 0.18f * u, rule)
    }
    val took = titleIn(
        s, t, RectF(11f * u, 17f * u, 89f * u, 0.6f * s.h), garamond, 13.5f, s.c.ink, s.c.accent, pitch = 1.1f, place = 0,
        seriesFace = sans, seriesSize = 2.8f, seriesTracking = 0.22f, seriesAbove = true, gap = 5f,
    )
    val oy = min(took.bottom + 6f * u, 0.64f * s.h)
    for (k in -1..1) s.diamond(cx + k * 3.4f * u, oy, 0.8f * u, s.deco(s.c.accent))
    authorIn(s, t, RectF(12f * u, 0.69f * s.h, 88f * u, 0.8f * s.h), garamondItalic, 6.4f, s.c.ink, upper = false, tracking = 0f)
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[0], cx, s.h - 17f * u, 4.2f * u, s.fill(s.deco(s.c.accent)))
}

// ---- shapes and cut paper ----------------------------------------------------------------------------------

/** Squares each holding one plain shape (a quarter round, a half, a triangle) in a few flat colours, over plain type. */
internal fun CoverGenerator.shapes(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val palette = listOf(a, hueShift(a, 155f, if (dark) 0f else 0.08f), mix(s.c.ground, s.c.ink, 0.8f), hueShift(a, -38f, if (dark) -0.1f else 0.12f))
    val tint = mix(s.c.ground, s.c.ink, 0.12f)
    val cols = 4
    val rows = 3
    val cell = s.w / cols
    val tall = min(cell, 0.58f * s.h / rows)
    val forced = rnd.nextInt(cols * rows)
    var last = -1
    for (row in 0 until rows) for (col in 0 until cols) {
        val l = col * cell
        val top = row * tall
        val r = RectF(l, top, l + cell, top + tall)
        var colour = rnd.nextInt(palette.size)
        if (colour == last) colour = (colour + 1) % palette.size
        if (row * cols + col == forced) colour = 0
        last = colour
        val paint = s.fill(palette[colour])
        if (rnd.nextInt(4) == 0) c.drawRect(r, s.fill(tint))
        c.save()
        c.clipRect(r)
        val turn = rnd.nextInt(4)
        val kx = if (turn % 2 == 0) r.left else r.right
        val ky = if (turn < 2) r.top else r.bottom
        when (rnd.nextInt(7)) {
            // A quarter round out of one corner.
            0, 1 -> c.drawOval(RectF(kx - cell, ky - tall, kx + cell, ky + tall), paint)
            // A half round on one side.
            2 -> if (turn % 2 == 0) c.drawOval(RectF(r.left, ky - tall / 2, r.right, ky + tall / 2), paint) else c.drawOval(RectF(kx - cell / 2, r.top, kx + cell / 2, r.bottom), paint)
            3 -> c.drawOval(RectF(r.left + cell * 0.12f, r.top + tall * 0.12f, r.right - cell * 0.12f, r.bottom - tall * 0.12f), paint)
            4 -> c.drawPath(Path().apply {
                moveTo(kx, ky)
                lineTo(if (turn % 2 == 0) r.right else r.left, ky)
                lineTo(kx, if (turn < 2) r.bottom else r.top)
                close()
            }, paint)
            // Quarter rings.
            5 -> for (k in 3 downTo 1) c.drawOval(RectF(kx - cell * k / 3, ky - tall * k / 3, kx + cell * k / 3, ky + tall * k / 3), if (k % 2 == 1) paint else s.fill(if (dark) mix(s.c.ground, Color.BLACK, 0.2f) else s.c.ground))
            else -> {
                c.drawRect(r, paint)
                c.drawOval(RectF(r.centerX() - cell * 0.2f, r.centerY() - tall * 0.2f, r.centerX() + cell * 0.2f, r.centerY() + tall * 0.2f), s.fill(s.c.ground))
            }
        }
        c.restore()
    }
    val top = rows * tall
    Painter(c, whole(s), rnd).grain(0.7f)
    plainHead(s, t, 8f * u, top + 7f * u, 84f * u, s.h - 24f * u - (top + 7f * u), sansBold, 9.6f)
    authorIn(s, t, RectF(8f * u, s.h - 19f * u, 92f * u, s.h - 8f * u), sans, 4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** Shapes cut from coloured paper and laid one on another: rounds, and fronds of leaves, about the title. */
internal fun CoverGenerator.cutPaper(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val second = hueShift(a, 150f, if (dark) 0.04f else 0.06f)
    val third = hueShift(a, -45f, if (dark) -0.08f else 0.14f)
    val deep = mix(s.c.ground, s.c.ink, 0.72f)
    fun paper(colour: Int) = s.fill(colour).apply { setShadowLayer(1.3f * u, 0.5f * u, 0.8f * u, faint(Color.BLACK, if (dark) 120 else 60)) }
    fun j(by: Float) = (rnd.nextFloat() - 0.5f) * by * u
    c.drawPath(blob(82f * u + j(8f), 13f * u + j(6f), 29f * u, art.seed), paper(a))
    c.drawPath(blob(15f * u + j(8f), 7f * u + j(4f), 20f * u, art.seed + 5), paper(second))
    c.drawPath(Path().also { frond(it, 37f * u + j(6f), -3f * u, 30f * u + j(8f), 0.27f * s.h, 0.16f, 6, 8.4f * u, 0.5f * u) }, paper(deep))
    c.drawPath(blob(62f * u + j(6f), 0.2f * s.h, 6.4f * u, art.seed + 9, 0.2f), paper(third))
    // At the foot, to the left, clear of the author's name.
    c.drawPath(blob(7f * u + j(4f), s.h - 2f * u, 25f * u, art.seed + 11), paper(third))
    c.drawPath(Path().also { frond(it, 24f * u + j(4f), s.h + 3f * u, 33f * u + j(5f), s.h - 34f * u, -0.14f, 6, 8f * u, 0.5f * u) }, paper(a))
    c.drawPath(blob(4f * u, s.h - 31f * u + j(4f), 5.4f * u, art.seed + 13, 0.2f), paper(second))
    Painter(c, whole(s), rnd).grain(0.8f)
    titleIn(
        s, t, RectF(10f * u, 0.33f * s.h, 90f * u, 0.7f * s.h), didoneBlack, 12.5f, s.c.ink, a, align = Layout.Alignment.ALIGN_NORMAL, pitch = 1.06f,
        seriesFace = sansBold, seriesSize = 2.9f, seriesTracking = 0.14f, seriesAbove = true, gap = 3.6f,
    )
    authorIn(s, t, RectF(44f * u, s.h - 21f * u, 92f * u, s.h - 8f * u), sansBold, 4.2f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_OPPOSITE, place = 1)
}

/** One emblem for the book, large and flat, on a plain ground. */
internal fun CoverGenerator.motif(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val emblem = CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)]
    val cx = s.w / 2
    val took = titleIn(
        s, t, RectF(10f * u, 10f * u, 90f * u, 0.34f * s.h), serifBold, 10.6f, s.c.ink, s.c.accent, pitch = 1.1f, place = -1,
        seriesFace = sans, seriesSize = 2.8f, seriesTracking = 0.2f, gap = 3.6f,
    )
    val footTop = s.h - 22f * u
    val cy = (took.bottom + footTop) / 2
    val r = min(24f * u, (footTop - took.bottom) / 2 - 8f * u)
    if (r > 7f * u) {
        c.drawCircle(cx, cy, r * 1.34f, s.fill(mix(s.c.ground, s.c.ink, 0.07f)))
        CoverMotifs.draw(c, emblem, cx, cy, r, s.fill(s.c.accent), (rnd.nextFloat() - 0.5f) * 10f)
    }
    Painter(c, whole(s), rnd).grain(0.6f)
    authorIn(s, t, RectF(12f * u, footTop, 88f * u, s.h - 8f * u), sans, 3.9f, s.c.ink, tracking = 0.22f)
}

/** A ragged band of [x0]..[x1] between [top] and [bottom], torn along both edges. */
private fun tornBand(x0: Float, x1: Float, top: Float, bottom: Float, rough: Float, seed: Int, rnd: Random): Path {
    val path = Path()
    val n = 46
    for (i in 0..n) {
        val x = x0 + (x1 - x0) * i / n
        val y = top + (CoverMotifs.cloudy(i * 0.31f, 1.7f, seed, 2) - 0.5f) * rough * 2f + (rnd.nextFloat() - 0.5f) * rough * 0.5f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in n downTo 0) {
        val x = x0 + (x1 - x0) * i / n
        path.lineTo(x, bottom + (CoverMotifs.cloudy(i * 0.31f, 9.3f, seed, 2) - 0.5f) * rough * 2f + (rnd.nextFloat() - 0.5f) * rough * 0.5f)
    }
    path.close()
    return path
}

/** A painted ground with a strip torn from another sheet across it, the title written on that by hand. */
internal fun CoverGenerator.torn(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    // Brushed across with the second colour, and a round painted in it.
    val wash = s.stroke(faint(s.c.accent, 40), 6.4f * u).apply { strokeCap = Paint.Cap.ROUND }
    repeat(16) {
        val y = (0.05f + rnd.nextFloat() * 0.3f) * s.h
        val x0 = (-8f + rnd.nextFloat() * 30f) * u
        val x1 = (70f + rnd.nextFloat() * 40f) * u
        c.drawPath(Path().apply { moveTo(x0, y); quadTo((x0 + x1) / 2, y + (rnd.nextFloat() - 0.5f) * 7f * u, x1, y + (rnd.nextFloat() - 0.5f) * 4f * u) }, wash)
    }
    c.drawPath(blob((34f + rnd.nextFloat() * 32f) * u, 0.25f * s.h, (19f + rnd.nextFloat() * 4f) * u, art.seed, 0.07f), s.fill(s.c.accent))
    Painter(c, whole(s), rnd).grain(1f)

    val sheetC = if (dark) s.c.ink else mix(s.c.ground, Color.WHITE, 0.72f)
    val written = if (dark) s.c.ground else s.c.ink
    val fibre = mix(sheetC, Color.WHITE, 0.75f)
    fun scrap(path: Path) {
        c.drawPath(path, s.fill(fibre).apply { setShadowLayer(1.6f * u, 0.4f * u, 0.9f * u, faint(Color.BLACK, if (dark) 150 else 70)) })
        c.drawPath(path, s.stroke(fibre, 1f * u).apply { strokeJoin = Paint.Join.ROUND })
        c.drawPath(path, s.fill(sheetC))
    }
    val wide = 78f * u
    val seriesF = t.series?.let { fit(it, typewriter, 3.1f * u, 2.2f * u, wide, 7.6f * u, pitch = 1.3f) }
    val title = fit(t.title, handBold, 17f * u, 5.6f * u, wide, 0.3f * s.h - (seriesF?.let { it.height + 3f * u } ?: 0f), pitch = 0.92f)!!
    val block = title.height + (seriesF?.let { it.height + 3.4f * u } ?: 0f)
    val mid = 0.52f * s.h
    val top = mid - block / 2 - 6f * u
    val bottom = mid + block / 2 + 6.5f * u
    c.save()
    c.rotate(-3f + rnd.nextFloat() * 1.4f, s.w / 2, mid)
    scrap(tornBand(-12f * u, s.w + 12f * u, top, bottom, 1.1f * u, art.seed, rnd))
    var y = top + 6f * u
    title.draw(c, s.w / 2 - wide / 2, y, s.words(written))
    y += title.height + 3.4f * u
    seriesF?.draw(c, s.w / 2 - wide / 2, y, s.seriesWords(showing(s.c.accent, sheetC, 3.0)))
    c.restore()
    // The author's name typed on a scrap of its own.
    t.author?.let { fit(caps(it), typewriterBold, 3.9f * u, 2.5f * u, 52f * u, 9f * u, tracking = 0.06f, pitch = 1.3f) }?.let { f ->
        val cx = 62f * u
        val cy = s.h - 16f * u
        c.save()
        c.rotate(2.2f, cx, cy)
        scrap(tornBand(cx - f.width / 2 - 5f * u, cx + f.width / 2 + 5f * u, cy - f.height / 2 - 3.2f * u, cy + f.height / 2 + 3.4f * u, 0.6f * u, art.seed + 3, rnd))
        f.draw(c, cx - 26f * u, cy - f.height / 2, s.words(written))
        c.restore()
    }
}

/** A soft round of colours melting into one another, between the title and the author's name. */
internal fun CoverGenerator.aura(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val rnd = Random(art.seed)
    val g = s.c.ground
    val a = s.c.accent
    val dark = isDark(g)
    val tones = intArrayOf(hueShift(a, 48f, if (dark) -0.04f else 0.1f), hueShift(a, -60f, if (dark) 0f else 0.12f), mix(a, if (dark) Color.WHITE else g, 0.35f), a)
    c.drawColor(g)
    val ratio = s.h / s.w
    val midY = 0.56f * ratio
    val bx = FloatArray(4) { 0.5f + (rnd.nextFloat() - 0.5f) * 0.3f }
    val by = FloatArray(4) { midY + (rnd.nextFloat() - 0.5f) * 0.26f }
    val spread = FloatArray(4) { 0.13f + rnd.nextFloat() * 0.07f }
    bx[3] = 0.5f + (rnd.nextFloat() - 0.5f) * 0.1f
    by[3] = midY + (rnd.nextFloat() - 0.5f) * 0.08f
    spread[3] = 0.15f
    field(s, whole(s), if (s.w > 700) 3 else 2) { x, y ->
        var colour = g
        for (k in 0 until 4) {
            val dx = x - bx[k]
            val dy = y - by[k]
            val weight = exp(-(dx * dx + dy * dy) / (2f * spread[k] * spread[k]))
            if (weight > 0.004f) colour = mix(colour, tones[k], min(1f, weight * 1.25f))
        }
        colour
    }
    Painter(c, whole(s), rnd).grain(1.5f)
    titleIn(
        s, t, RectF(10f * u, 9f * u, 90f * u, 0.28f * s.h), didoneItalic, 11f, s.c.ink, s.c.accent, pitch = 1.08f, place = -1,
        seriesFace = sans, seriesSize = 2.8f, seriesTracking = 0.2f, gap = 3.4f,
    )
    authorIn(s, t, RectF(12f * u, s.h - 17f * u, 88f * u, s.h - 7f * u), sans, 3.9f, s.c.ink, tracking = 0.22f)
}

/** The genre's picture printed in two inks, over a block of the ground with the words. */
internal fun CoverGenerator.duotone(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val frame = pictureFrame(CoverStyle.DUOTONE, s.w, s.h)!!
    val bw = max(1, frame.width().toInt())
    val bh = max(1, frame.height().toInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    paint(Canvas(bmp), RectF(0f, 0f, bw.toFloat(), bh.toFloat()), art)
    val px = IntArray(bw * bh)
    bmp.getPixels(px, 0, bw, 0, 0, bw, bh)
    var lo = 255
    var hi = 0
    val lum = IntArray(px.size) { i ->
        val p = px[i]
        val v = (Color.red(p) * 54 + Color.green(p) * 183 + Color.blue(p) * 19) shr 8
        if (v < lo) lo = v
        if (v > hi) hi = v
        v
    }
    val dark = isDark(s.c.ground)
    val shadow = if (dark) mix(s.c.ground, Color.BLACK, 0.45f) else s.c.ink
    val light = if (dark) mix(s.c.accent, Color.WHITE, 0.6f) else s.c.ground
    val lut = IntArray(256) { i ->
        val f = i / 255f
        if (f < 0.55f) mix(shadow, s.c.accent, f / 0.55f) else mix(s.c.accent, light, (f - 0.55f) / 0.45f * 0.85f)
    }
    val span = max(1, hi - lo)
    for (i in px.indices) px[i] = lut[((lum[i] - lo) * 255 / span).coerceIn(0, 255)] or SOLID
    bmp.setPixels(px, 0, bw, 0, 0, bw, bh)
    c.drawBitmap(bmp, null, frame, Paint(Paint.FILTER_BITMAP_FLAG))
    s.rect(0f, frame.bottom, s.w, frame.bottom + 1.6f * u, s.c.accent)
    plainHead(s, t, 8f * u, frame.bottom + 7f * u, 84f * u, s.h - 21f * u - (frame.bottom + 7f * u), sansBold, 9f)
    authorIn(s, t, RectF(8f * u, s.h - 17f * u, 92f * u, s.h - 7f * u), sans, 3.9f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** A board of squares, each with one small picture for the genre, over plain type. */
internal fun CoverGenerator.pictograms(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val cols = 4
    val rows = 3
    val cell = min(22f * u, 0.5f * s.h / rows)
    val left = (s.w - cols * cell) / 2
    val top = 7f * u
    val set = CoverMotifs.forGenre(art.genre)
    val order = (set + set).shuffled(rnd)
    val shift = rnd.nextInt(3)
    val tint = mix(s.c.ground, s.c.ink, 0.12f)
    for (row in 0 until rows) for (col in 0 until cols) {
        val r = RectF(left + col * cell, top + row * cell, left + (col + 1) * cell, top + (row + 1) * cell)
        val (under, over) = when ((row + col + shift) % 3) {
            0 -> s.c.accent to s.c.accentInk
            1 -> tint to s.c.accent
            else -> s.c.ink to s.c.ground
        }
        c.drawRect(r, s.fill(under))
        CoverMotifs.draw(c, order[row * cols + col], r.centerX(), r.centerY(), cell * 0.3f, s.fill(over))
    }
    val under = top + rows * cell
    Painter(c, whole(s), rnd).grain(0.6f)
    plainHead(s, t, left, under + 6.4f * u, cols * cell, s.h - 23f * u - (under + 6.4f * u), sansBold, 9.4f)
    authorIn(s, t, RectF(left, s.h - 19f * u, left + cols * cell, s.h - 8f * u), sans, 4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

// ---- lines that fool the eye --------------------------------------------------------------------------------

/** Stripes that swell, ripple or ring as if the page were not flat. */
internal fun CoverGenerator.opArt(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val g = s.c.ground
    val a = s.c.accent
    val bottom = (0.6f * s.h).roundToInt().toFloat()
    val ratio = bottom / s.w
    val kind = rnd.nextInt(3)
    val cxn = 0.5f + (rnd.nextFloat() - 0.5f) * 0.3f
    val cyn = ratio * (0.5f + (rnd.nextFloat() - 0.5f) * 0.2f)
    val count = 15f + rnd.nextInt(5)
    val phase = rnd.nextFloat() * 6.28f
    // How quickly the stripes' wave changes from one dot to the next: their edges are softened over that.
    val soft = Math.PI.toFloat() * count / s.w * 1.3f
    field(s, RectF(0f, 0f, s.w, bottom)) { x, y ->
        val v = when (kind) {
            0 -> {
                // Upright stripes over a swelling.
                val dx = x - cxn
                val dy = y - cyn
                val d = sqrt(dx * dx + dy * dy) / 0.34f
                val xx = if (d < 1f) cxn + dx * (0.42f + 0.58f * d * d) else x
                sin(xx * Math.PI.toFloat() * count)
            }
            1 -> {
                // Rings about a point, rippled.
                val dx = x - cxn
                val dy = y - cyn
                val d = sqrt(dx * dx + dy * dy)
                val turn = kotlin.math.atan2(dy, dx)
                sin((d + 0.012f * sin(turn * 7f + phase)) * Math.PI.toFloat() * count * 1.15f)
            }
            else -> {
                // Level stripes, folded.
                val fold = 0.028f * sin(y / ratio * 3.1416f)
                sin((y + fold * sin(x * 6.2832f * 1.5f + phase)) * Math.PI.toFloat() * count)
            }
        }
        mix(g, a, (v / soft * 0.5f + 0.5f).coerceIn(0f, 1f))
    }
    plainHead(s, t, 8f * u, bottom + 7f * u, 84f * u, s.h - 23f * u - (bottom + 7f * u), sansBold, 9.2f)
    authorIn(s, t, RectF(8f * u, s.h - 19f * u, 92f * u, s.h - 8f * u), sans, 4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** Stripes in a run of warm colours that come in from the side, round a bend and away down the cover. */
internal fun CoverGenerator.seventies(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val a = s.c.accent
    val dark = isDark(s.c.ground)
    val run = listOf(
        mix(hueShift(a, -28f), Color.BLACK, if (dark) 0.3f else 0.12f), hueShift(a, -14f, if (dark) -0.08f else 0f), a,
        hueShift(a, 16f, if (dark) 0.06f else 0.1f), mix(hueShift(a, 30f), if (dark) Color.WHITE else s.c.ground, 0.38f),
    )
    val wideEach = 4.7f * u
    val flip = rnd.nextBoolean()
    val y0 = 0.47f * s.h
    val bendX = 50f * u
    val inner = 8f * u
    val middleY = y0 + run.size * wideEach + inner
    c.save()
    if (flip) c.scale(-1f, 1f, s.w / 2, 0f)
    for (i in run.indices) {
        val y = y0 + (i + 0.5f) * wideEach
        val r = middleY - y
        val path = Path().apply {
            moveTo(-3f * u, y)
            lineTo(bendX, y)
            arcTo(RectF(bendX - r, middleY - r, bendX + r, middleY + r), -90f, 90f)
            lineTo(bendX + r, s.h + 3f * u)
        }
        c.drawPath(path, s.stroke(run[i], wideEach + 0.6f))
    }
    c.restore()
    c.drawCircle(if (flip) 17f * u else 83f * u, 0.36f * s.h, 6f * u, s.fill(run[3]))
    Painter(c, whole(s), rnd).grain(0.9f)
    titleIn(
        s, t, RectF(9f * u, 10f * u, 91f * u, 0.41f * s.h), rounded, 12.5f, s.c.ink, a, pitch = 1.04f, place = -1,
        align = if (flip) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL, seriesFace = sansBold, seriesSize = 2.9f, seriesTracking = 0.14f, gap = 3.4f,
    )
    val box = if (flip) RectF(46f * u, s.h - 23f * u, 91f * u, s.h - 9f * u) else RectF(9f * u, s.h - 23f * u, 54f * u, s.h - 9f * u)
    authorIn(s, t, box, sansBold, 4.3f, s.c.ink, tracking = 0.1f, align = if (flip) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** A draughtsman's sheet: squared paper, an emblem drawn with its centre lines and measures, and the words in the title block. */
internal fun CoverGenerator.schematic(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val ink = s.c.ink
    val fine = s.stroke(s.deco(faint(ink, 26)), max(1f, 0.1f * u))
    val firm = s.stroke(s.deco(faint(ink, 60)), max(1f, 0.14f * u))
    var k = 0
    var x = 0f
    while (x < s.w) {
        c.drawLine(x, 0f, x, s.h, if (k % 5 == 0) firm else fine)
        x += 4f * u
        k++
    }
    k = 0
    var y = 0f
    while (y < s.h) {
        c.drawLine(0f, y, s.w, y, if (k % 5 == 0) firm else fine)
        y += 4f * u
        k++
    }
    val pen = s.stroke(s.deco(faint(ink, 215)), 0.26f * u)
    val dashed = Paint(pen).apply { pathEffect = DashPathEffect(floatArrayOf(1.6f * u, 1f * u), 0f) }
    val chain = Paint(pen).apply { pathEffect = DashPathEffect(floatArrayOf(3.4f * u, 0.9f * u, 0.6f * u, 0.9f * u), 0f) }
    val blockTop = 0.66f * s.h
    val cx = 50f * u
    val cy = 8f * u + (blockTop - 16f * u) / 2
    val r = min(25f * u, (blockTop - 26f * u) / 2)
    // Centre lines, the circles it's drawn in, and the emblem.
    c.drawLine(cx - r * 1.42f, cy, cx + r * 1.42f, cy, chain)
    c.drawLine(cx, cy - r * 1.42f, cx, cy + r * 1.3f, chain)
    c.drawCircle(cx, cy, r, pen)
    c.drawCircle(cx, cy, r * 0.72f, dashed)
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy, r * 0.56f, s.fill(s.c.accent))
    for (deg in listOf(45f, 135f, 225f, 315f)) {
        val a = Math.toRadians(deg.toDouble())
        c.drawCircle(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), 0.9f * u, s.fill(s.deco(ink)))
    }
    // A measure across, under it.
    val my = cy + r + 5.5f * u
    c.drawLine(cx - r, cy + r * 0.3f, cx - r, my + 1.6f * u, pen)
    c.drawLine(cx + r, cy + r * 0.3f, cx + r, my + 1.6f * u, pen)
    c.drawLine(cx - r, my, cx + r, my, pen)
    for (side in listOf(-1f, 1f)) {
        val tip = cx + side * r
        c.drawPath(Path().apply { moveTo(tip, my); lineTo(tip - side * 2.6f * u, my - 0.8f * u); lineTo(tip - side * 2.6f * u, my + 0.8f * u); close() }, s.fill(s.deco(ink)))
    }
    // A leader to a note.
    val la = Math.toRadians(-38.0)
    val lx = cx + (cos(la) * r * 0.72f).toFloat()
    val ly = cy + (sin(la) * r * 0.72f).toFloat()
    c.drawLine(lx, ly, lx + 13f * u, ly - 10f * u, pen)
    c.drawLine(lx + 13f * u, ly - 10f * u, lx + 22f * u, ly - 10f * u, pen)
    c.drawCircle(lx, ly, 0.7f * u, s.fill(s.deco(ink)))
    c.drawCircle(lx + 25f * u, ly - 10f * u, 2.6f * u, pen)
    CoverMotifs.draw(c, Motif.SPARK, lx + 25f * u, ly - 10f * u, 1.3f * u, s.fill(s.c.accent))

    // The title block.
    val l = 7f * u
    val rr = s.w - 7f * u
    val bottom = s.h - 7f * u
    val split = bottom - 13f * u
    c.drawRect(l, blockTop, rr, bottom, s.fill(s.c.ground))
    c.drawRect(l, blockTop, rr, bottom, s.stroke(s.deco(ink), 0.44f * u))
    c.drawLine(l, split, rr, split, pen)
    c.drawLine(cx, split, cx, bottom, pen)
    titleIn(s, t, RectF(l + 4f * u, blockTop + 3.4f * u, rr - 4f * u, split - 3.4f * u), monoBold, 7.2f, ink, ink, upper = true, pitch = 1.16f, smallest = 3f, series = false)
    val seriesF = t.series?.let { fit(caps(it), mono, 2.7f * u, 1.9f * u, cx - l - 6f * u, 9f * u, pitch = 1.3f) }
    if (seriesF != null) seriesF.draw(c, l + 3f * u, split + (bottom - split - seriesF.height) / 2, s.seriesWords(s.c.accent))
    else {
        // A scale, where the series would be.
        val sy = (split + bottom) / 2
        for (i in 0 until 6) s.rect(l + 7f * u + i * 5f * u, sy - 0.7f * u, l + 12f * u + i * 5f * u, sy + 0.7f * u, if (i % 2 == 0) s.deco(ink) else s.deco(faint(ink, 60)))
    }
    authorIn(s, t, RectF(cx + 3f * u, split + 1.6f * u, rr - 3f * u, bottom - 1.6f * u), monoBold, 3.3f, ink, tracking = 0.04f, pitch = 1.25f)
}

/** A maze, with the one way through it marked. */
internal fun CoverGenerator.maze(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val cols = 15
    val cell = 84f * u / cols
    val rows = max(6, (0.54f * s.h / cell).toInt())
    val left = 8f * u
    val top = 8f * u
    // Walls to the right of and below each cell, knocked through as the maze is dug.
    val wallRight = BooleanArray(cols * rows) { true }
    val wallDown = BooleanArray(cols * rows) { true }
    val seen = BooleanArray(cols * rows)
    val from = IntArray(cols * rows) { -1 }
    val stack = ArrayDeque<Int>()
    stack.addLast(0)
    seen[0] = true
    while (stack.isNotEmpty()) {
        val at = stack.last()
        val ax = at % cols
        val ay = at / cols
        val ways = ArrayList<Int>(4)
        if (ax > 0 && !seen[at - 1]) ways += at - 1
        if (ax < cols - 1 && !seen[at + 1]) ways += at + 1
        if (ay > 0 && !seen[at - cols]) ways += at - cols
        if (ay < rows - 1 && !seen[at + cols]) ways += at + cols
        if (ways.isEmpty()) {
            stack.removeLast()
            continue
        }
        val next = ways[rnd.nextInt(ways.size)]
        when (next) {
            at - 1 -> wallRight[next] = false
            at + 1 -> wallRight[at] = false
            at - cols -> wallDown[next] = false
            else -> wallDown[at] = false
        }
        seen[next] = true
        from[next] = at
        stack.addLast(next)
    }
    // The way through, from the last cell back to the first.
    val way = Path()
    var at = cols * rows - 1
    fun mx(i: Int) = left + (i % cols + 0.5f) * cell
    fun my(i: Int) = top + (i / cols + 0.5f) * cell
    way.moveTo(mx(at), top + rows * cell + 3f * u)
    while (at >= 0) {
        way.lineTo(mx(at), my(at))
        at = from[at]
    }
    way.lineTo(mx(0), top - 3f * u)
    c.drawPath(way, s.stroke(s.c.accent, cell * 0.36f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
    val walls = Path()
    for (i in 0 until cols * rows) {
        val x0 = left + (i % cols) * cell
        val y0 = top + (i / cols) * cell
        if (wallRight[i] && i % cols < cols - 1) {
            walls.moveTo(x0 + cell, y0)
            walls.lineTo(x0 + cell, y0 + cell)
        }
        if (wallDown[i] && i / cols < rows - 1) {
            walls.moveTo(x0, y0 + cell)
            walls.lineTo(x0 + cell, y0 + cell)
        }
    }
    // The outer wall, open at the way in and the way out.
    val right = left + cols * cell
    val bottom = top + rows * cell
    walls.moveTo(left + cell, top)
    walls.lineTo(right, top)
    walls.lineTo(right, bottom)
    walls.moveTo(right - cell, bottom)
    walls.lineTo(left, bottom)
    walls.lineTo(left, top)
    c.drawPath(walls, s.stroke(s.deco(s.c.ink), cell * 0.2f).apply { strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER })
    Painter(c, whole(s), rnd).grain(0.6f)
    plainHead(s, t, left, bottom + 8f * u, 84f * u, s.h - 23f * u - (bottom + 8f * u), sansBold, 9.2f, upper = true, tracking = 0.02f)
    authorIn(s, t, RectF(left, s.h - 19f * u, 92f * u, s.h - 8f * u), sans, 4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** The lines of a map of high ground, every fourth one heavier, and the words on a plate over them. */
internal fun CoverGenerator.contours(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val w = max(1, s.w.toInt())
    val h = max(1, s.h.toInt())
    val step = if (w > 700) 4 else 3
    val gw = w / step + 2
    val gh = h / step + 2
    val levels = 13f
    val heights = FloatArray(gw * gh) { i ->
        CoverMotifs.cloudy((i % gw) * step / s.w * 2.1f + 3f, (i / gw) * step / s.w * 2.1f + 7f, art.seed, 3) * levels
    }
    val g = s.c.ground
    val lineC = s.deco(faint(s.c.ink, 150))
    val lineOpaque = faint(lineC, 255)
    val lineStrength = Color.alpha(lineC) / 255f
    val heavy = s.c.accent
    val half = max(0.5f, 0.15f * u)
    val halfHeavy = max(0.9f, 0.34f * u)
    val px = IntArray(w * h)
    for (y in 0 until h) {
        val gy = y / step
        val fy = (y % step) / step.toFloat()
        for (x in 0 until w) {
            val gx = x / step
            val fx = (x % step) / step.toFloat()
            val i = gy * gw + gx
            val f00 = heights[i]
            val f10 = heights[i + 1]
            val f01 = heights[i + gw]
            val f11 = heights[i + gw + 1]
            val f = (f00 * (1 - fx) + f10 * fx) * (1 - fy) + (f01 * (1 - fx) + f11 * fx) * fy
            val dx = ((f10 - f00) * (1 - fy) + (f11 - f01) * fy) / step
            val dy = ((f01 - f00) * (1 - fx) + (f11 - f10) * fx) / step
            val level = f.roundToInt()
            val away = abs(f - level) / max(1e-4f, sqrt(dx * dx + dy * dy))
            val index = level % 4 == 0
            val cover = ((if (index) halfHeavy else half) + 0.5f - away).coerceIn(0f, 1f)
            px[y * w + x] = (if (cover <= 0f) g else if (index) mix(g, heavy, cover) else mix(g, lineOpaque, cover * lineStrength)) or SOLID
        }
    }
    c.drawBitmap(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888), 0f, 0f, null)
    // The plate.
    val plateR = RectF(7f * u, 0.58f * s.h, s.w - 7f * u, s.h - 7f * u)
    c.drawRect(plateR, s.fill(g))
    c.drawRect(plateR, s.stroke(s.deco(s.c.ink), 0.34f * u))
    plainHead(s, t, plateR.left + 5f * u, plateR.top + 5.5f * u, plateR.width() - 10f * u, plateR.height() - 22f * u, sansBold, 8.6f)
    authorIn(
        s, t, RectF(plateR.left + 5f * u, plateR.bottom - 14f * u, plateR.right - 5f * u, plateR.bottom - 4.4f * u), mono, 3.4f, s.c.ink,
        tracking = 0.08f, align = Layout.Alignment.ALIGN_NORMAL, place = 1,
    )
}
