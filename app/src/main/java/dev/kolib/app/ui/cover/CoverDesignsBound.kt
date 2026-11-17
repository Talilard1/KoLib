package dev.kolib.app.ui.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * More designs for the cover maker, after the ways books have been bound and dressed: patterned and gilt cloth,
 * marbled boards, tooled leather, private-press title pages, playbills, and the foil-stamped cases of
 * special editions. Every one is drawn here, in the book's own colours; none copies a particular book.
 */

internal typealias Sheet = CoverGenerator.Sheet
internal typealias Words = CoverGenerator.Words
internal typealias Art = CoverGenerator.Art

// ---- what the designs share --------------------------------------------------------------------------------

/** [colour] with its hue turned by [degrees], and made [lighter] (or, below nought, darker). */
internal fun hueShift(colour: Int, degrees: Float, lighter: Float = 0f): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(colour, hsl)
    hsl[0] = ((hsl[0] + degrees) % 360f + 360f) % 360f
    hsl[2] = (hsl[2] + lighter).coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}

internal const val SOLID = 0xFF shl 24

internal fun isDark(colour: Int) = ColorUtils.calculateLuminance(colour) < 0.3

/** [colour], made paler or darker until it shows on [on]. */
internal fun showing(colour: Int, on: Int, least: Double = 3.0): Int {
    val under = ColorUtils.setAlphaComponent(on, 255)
    val towards = if (ColorUtils.calculateLuminance(under) < 0.4) Color.WHITE else Color.BLACK
    var out = ColorUtils.setAlphaComponent(colour, 255)
    var k = 0
    while (ColorUtils.calculateContrast(out, under) < least && k < 10) out = ColorUtils.blendARGB(colour, towards, ++k * 0.1f)
    return out
}

/** A number as the Romans wrote it (1 to 3999). */
internal fun romanNumeral(n: Int): String {
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val marks = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var left = n
    val out = StringBuilder()
    for (i in values.indices) while (left >= values[i]) {
        out.append(marks[i])
        left -= values[i]
    }
    return out.toString()
}

/** A sheen for lettering in leaf: its colour a little darker and lighter in bands across the cover. */
internal fun CoverGenerator.sheen(s: Sheet, base: Int): Shader =
    LinearGradient(0f, 0f, s.w * 0.6f, s.h * 0.45f, intArrayOf(mix(base, Color.BLACK, 0.16f), base, mix(base, Color.WHITE, 0.14f), base), null, Shader.TileMode.MIRROR)

/** A panel for words: filled with [fill], and ruled round in [line] (a line and a finer one inside it). */
internal fun CoverGenerator.plate(s: Sheet, r: RectF, fill: Int, line: Int?, round: Float = 0f) {
    val u = s.u
    s.canvas.drawRoundRect(r, round, round, s.fill(fill))
    if (line == null) return
    s.canvas.drawRoundRect(r, round, round, s.stroke(line, 0.32f * u))
    val g = 1.1f * u
    s.canvas.drawRoundRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), max(0f, round - g), max(0f, round - g), s.stroke(faint(line, 150), 0.14f * u))
}

/**
 * The title, with the series under it (or [seriesAbove] it), set in [box]: in the middle of it, or ([place] -1) at
 * its head or (1) its foot. Sizes are in hundredths of the cover's width. Gives the space the words took.
 */
internal fun CoverGenerator.titleIn(
    s: Sheet, t: Words, box: RectF, face: Typeface, largest: Float, ink: Int, seriesInk: Int,
    upper: Boolean = false, smallest: Float = 3.8f, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, pitch: Float = 1.1f, tracking: Float = 0f,
    seriesFace: Typeface = romanLight, seriesUpper: Boolean = true, seriesSize: Float = 3f, seriesTracking: Float = 0.2f, seriesAbove: Boolean = false,
    gap: Float = 3f, place: Int = 0, foil: Boolean = false, shadow: Int? = null, shadowRadius: Float = 0f, shadowDy: Float = 0f,
    outline: Int? = null, outlineWidth: Float = 0f, series: Boolean = true,
): RectF {
    val u = s.u
    val c = s.canvas
    val seriesF = if (series) t.series?.let {
        fit(if (seriesUpper) caps(it) else it, seriesFace, seriesSize * u, seriesSize * 0.72f * u, box.width(), seriesSize * u * 2.7f, align, tracking = seriesTracking, pitch = 1.4f)
    } else null
    val extra = seriesF?.let { it.height + gap * u } ?: 0f
    val title = fit(if (upper) caps(t.title) else t.title, face, largest * u, smallest * u, box.width(), box.height() - extra, align, pitch, tracking)!!
    val block = title.height + extra
    var y = max(box.top, when (place) {
        -1 -> box.top
        1 -> box.bottom - block
        else -> box.top + (box.height() - block) / 2
    })
    val top = y
    if (seriesAbove) seriesF?.let {
        it.draw(c, box.left, y, s.seriesWords(seriesInk))
        y += it.height + gap * u
    }
    val colour = s.words(ink)
    title.draw(c, box.left, y, colour, if (foil) sheen(s, colour) else null, outline, outlineWidth * u, shadow, shadowRadius * u, shadowDy * u)
    y += title.height
    if (!seriesAbove) seriesF?.let {
        y += gap * u
        it.draw(c, box.left, y, s.seriesWords(seriesInk))
        y += it.height
    }
    return RectF(box.left, top, box.right, y)
}

/** The author's name set in [box] (in the middle of it, or at its head or foot as [place] says). Gives the space it took, if there's a name. */
internal fun CoverGenerator.authorIn(
    s: Sheet, t: Words, box: RectF, face: Typeface, largest: Float, ink: Int,
    upper: Boolean = true, tracking: Float = 0.16f, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, smallest: Float = largest * 0.62f,
    place: Int = 0, foil: Boolean = false, shadow: Int? = null, shadowRadius: Float = 0f, pitch: Float = 1.4f,
    outline: Int? = null, outlineWidth: Float = 0f,
): RectF? {
    val u = s.u
    val f = t.author?.let { fit(if (upper) caps(it) else it, face, largest * u, smallest * u, box.width(), box.height(), align, pitch, tracking) } ?: return null
    val y = max(box.top, when (place) {
        -1 -> box.top
        1 -> box.bottom - f.height
        else -> box.top + (box.height() - f.height) / 2
    })
    val colour = s.words(ink)
    f.draw(s.canvas, box.left, y, colour, if (foil) sheen(s, colour) else null, outline, outlineWidth * u, shadow, shadowRadius * u)
    val wide = f.width
    val left = when (align) {
        Layout.Alignment.ALIGN_NORMAL -> box.left
        Layout.Alignment.ALIGN_OPPOSITE -> box.right - wide
        else -> box.centerX() - wide / 2
    }
    return RectF(left, y, left + wide, y + f.height)
}

/**
 * Paints [r] dot by dot: [colour] is asked for each, at its place across and down the cover (both in widths of
 * the cover, so shapes keep their proportions). [coarse] dots are painted as one, for soft things on a big cover.
 */
internal fun CoverGenerator.field(s: Sheet, r: RectF, coarse: Int = 1, colour: (Float, Float) -> Int) {
    val bw = max(1, (r.width() / coarse).toInt())
    val bh = max(1, (r.height() / coarse).toInt())
    val px = IntArray(bw * bh)
    for (j in 0 until bh) {
        val y = (r.top + (j + 0.5f) * coarse) / s.w
        // (Made solid: mixing two solid colours can come out a shade short of it.)
        for (i in 0 until bw) px[j * bw + i] = colour((r.left + (i + 0.5f) * coarse) / s.w, y) or SOLID
    }
    val bmp = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
    s.canvas.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
}

/** [path] again, mirrored left to right and top to bottom about the middle of the cover: one corner's work made four. */
private fun fourfold(s: Sheet, path: Path): Path {
    val all = Path(path)
    for ((sx, sy) in listOf(-1f to 1f, 1f to -1f, -1f to -1f)) {
        val other = Path()
        path.transform(Matrix().apply { setScale(sx, sy, s.w / 2, s.h / 2) }, other)
        all.addPath(other)
    }
    return all
}

/** A paint that lays gold leaf from [base], pressed a little into the cover. */
internal fun CoverGenerator.leafPaint(s: Sheet, base: Int, pressed: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = foil(s, base)
    if (pressed) setShadowLayer(0.3f * s.u, 0.1f * s.u, 0.2f * s.u, faint(Color.BLACK, 130))
}

// ---- cloth, paper and leather ------------------------------------------------------------------------------

/** Cloth stamped all over with a small repeating figure in one foil, the title in a plain panel. */
internal fun CoverGenerator.patterned(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    cloth(s, art.seed)
    val rnd = Random(art.seed)
    val set = CoverMotifs.forGenre(art.genre)
    val first = set[rnd.nextInt(3)]
    val second = (set - first)[rnd.nextInt(set.size - 1)]
    val way = rnd.nextInt(3)
    val gold = s.c.accent
    val ink = leafPaint(s, s.deco(gold))
    val cols = if (way == 2) 7 else 6
    val cell = s.w / cols
    val rows = (s.h / (cell * 0.86f)).toInt() + 2
    for (row in 0 until rows) for (col in -1..cols) {
        val x = col * cell + cell / 2 + if (row % 2 == 0) 0f else cell / 2
        val y = row * cell * 0.86f + cell * 0.3f
        when (way) {
            0 -> CoverMotifs.draw(c, if (row % 2 == 0) first else second, x, y, cell * 0.29f, ink)
            1 -> {
                CoverMotifs.draw(c, first, x, y, cell * 0.3f, ink, if ((col + row) % 2 == 0) -18f else 18f)
                c.drawCircle(x + cell / 2, y, cell * 0.05f, ink)
            }
            else -> {
                CoverMotifs.draw(c, first, x, y, cell * 0.27f, ink, if (row % 2 == 0) 0f else 180f)
                CoverMotifs.draw(c, Motif.SPARK, x + cell / 2, y, cell * 0.09f, ink)
            }
        }
    }
    // The title in a plain panel, as it's stamped on patterned cloth.
    val cx = s.w / 2
    val wide = 58f * u
    val seriesF = t.series?.let { fit(caps(it), romanLight, 2.9f * u, 2.1f * u, wide, 7.6f * u, tracking = 0.2f, pitch = 1.4f) }
    val title = fit(caps(t.title), roman, 8.4f * u, 3.4f * u, wide, 38f * u, pitch = 1.14f, tracking = 0.04f)!!
    val panel = RectF(cx - 35f * u, 20f * u, cx + 35f * u, 20f * u + title.height + (seriesF?.let { it.height + 3.6f * u } ?: 0f) + 12f * u)
    plate(s, panel, s.c.ground, s.deco(gold))
    var y = panel.top + 6f * u
    title.draw(c, cx - wide / 2, y, s.words(gold), sheen(s, s.words(gold)))
    y += title.height + 3.6f * u
    seriesF?.draw(c, cx - wide / 2, y, s.seriesWords(s.c.ink))
    t.author?.let { fit(caps(it), roman, 3.9f * u, 2.5f * u, 52f * u, 9f * u, tracking = 0.16f, pitch = 1.4f) }?.let { f ->
        val r = RectF(cx - 31f * u, s.h - 12f * u - f.height - 6.4f * u, cx + 31f * u, s.h - 12f * u)
        plate(s, r, s.c.ground, s.deco(gold))
        f.draw(c, cx - 26f * u, r.top + 3.2f * u, s.words(gold), sheen(s, s.words(gold)))
    }
}

/** Marbled paper boards with a leather spine and corners, and a label for the title. */
internal fun CoverGenerator.marbled(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val g = s.c.ground
    val a = s.c.accent
    val pale = !isDark(g)
    val vein = if (pale) Color.BLACK else Color.WHITE
    val tones = intArrayOf(g, mix(g, vein, 0.07f), mix(g, a, 0.3f), g, mix(g, Color.BLACK, if (pale) 0.1f else 0.24f), mix(g, a, 0.7f), g, mix(g, vein, 0.12f))
    val seed = art.seed
    c.drawColor(g)
    field(s, whole(s), if (s.w > 700) 2 else 1) { x, y ->
        // The colours swirled into one another, then drawn through with a comb.
        val wx = x + 0.34f * CoverMotifs.cloudy(x * 2.1f, y * 2.1f, seed + 7, 3) + 0.016f * sin(y * 58f)
        val wy = y + 0.34f * CoverMotifs.cloudy(x * 2.1f + 5.2f, y * 2.1f + 1.3f, seed + 13, 3)
        val v = CoverMotifs.cloudy(wx * 5.2f, wy * 5.2f, seed, 3) * 34f
        val band = v.toInt()
        val base = tones[band % tones.size]
        // A fine vein where one colour meets the next.
        if (v - band < 0.13f) mix(base, vein, 0.3f) else base
    }
    val hide = if (pale) mix(a, Color.BLACK, 0.25f) else mix(g, Color.BLACK, 0.55f)
    val gilt = showing(if (pale) s.c.accentInk else a, hide, 2.5)
    val spine = 22f * u
    val leatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, spine, 0f, intArrayOf(mix(hide, Color.BLACK, 0.3f), hide, mix(hide, Color.WHITE, 0.08f), hide), null, Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, spine, s.h, leatherPaint)
    val corner = 23f * u
    for (bottom in listOf(false, true)) {
        val y0 = if (bottom) s.h else 0f
        val y1 = if (bottom) s.h - corner else corner
        val tri = Path().apply { moveTo(s.w, y0); lineTo(s.w - corner, y0); lineTo(s.w, y1); close() }
        c.drawPath(tri, s.fill(hide))
        c.drawLine(s.w - corner, y0, s.w, y1, s.stroke(s.deco(gilt), 0.36f * u))
    }
    c.drawLine(spine, 0f, spine, s.h, s.stroke(s.deco(gilt), 0.36f * u))
    c.drawLine(spine - 1.2f * u, 0f, spine - 1.2f * u, s.h, s.stroke(s.deco(faint(gilt, 150)), 0.14f * u))
    Painter(c, whole(s), Random(seed)).vignette(60)

    // A pasted label, like a book from a circulating library.
    val cx = (spine + s.w) / 2
    val wide = 50f * u
    val seriesF = t.series?.let { fit(it, serifItalic, 3.6f * u, 2.5f * u, wide, 8.4f * u) }
    val title = fit(t.title, serifBold, 8.6f * u, 3.6f * u, wide, 40f * u)!!
    val label = RectF(cx - wide / 2 - 5f * u, 22f * u, cx + wide / 2 + 5f * u, 22f * u + title.height + (seriesF?.let { it.height + 6.6f * u } ?: 0f) + 11f * u)
    c.drawRect(label, s.fill(a).apply { setShadowLayer(1.4f * u, 0f, 0.6f * u, faint(Color.BLACK, 100)) })
    c.drawRect(label.left + 1.4f * u, label.top + 1.4f * u, label.right - 1.4f * u, label.bottom - 1.4f * u, s.stroke(s.deco(faint(s.c.accentInk, 160)), 0.26f * u))
    var y = label.top + 5.5f * u
    title.draw(c, cx - wide / 2, y, s.words(s.c.accentInk))
    y += title.height + 3.3f * u
    seriesF?.let {
        s.rule(cx, y, 12f * u, 0.26f * u, s.deco(faint(s.c.accentInk, 160)))
        it.draw(c, cx - wide / 2, y + 3.3f * u, s.seriesWords(s.c.accentInk))
    }
    t.author?.let { fit(caps(it), sans, 3.3f * u, 2.2f * u, 46f * u, 8f * u, tracking = 0.12f, pitch = 1.45f) }?.let { f ->
        val r = RectF(cx - 27f * u, s.h - 27f * u - f.height - 5.6f * u, cx + 27f * u, s.h - 27f * u)
        c.drawRect(r, s.fill(a).apply { setShadowLayer(1.2f * u, 0f, 0.5f * u, faint(Color.BLACK, 100)) })
        f.draw(c, cx - 23f * u, r.top + 2.8f * u, s.words(s.c.accentInk))
    }
}

/** Full leather, tooled and gilt: ruled frames, corner pieces, a lettering piece let in for the title and a device in a ring. */
internal fun CoverGenerator.library(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    leather(s, art.seed)
    val pale = !isDark(s.c.ground)
    val gold = s.c.accent
    // A lattice pressed blind into the leather.
    c.save()
    c.clipRect(9f * u, 9f * u, s.w - 9f * u, s.h - 9f * u)
    val blind = s.stroke(faint(Color.BLACK, if (pale) 26 else 50), 0.22f * u)
    var d = -s.h
    while (d < s.w + s.h) {
        c.drawLine(d, 0f, d + s.h, s.h, blind)
        c.drawLine(d, s.h, d + s.h, 0f, blind)
        d += 7f * u
    }
    c.restore()
    val line = leafPaint(s, s.deco(gold)).apply { style = Paint.Style.STROKE }
    for ((inset, wide) in listOf(4.2f to 0.5f, 5.7f to 0.16f, 8.6f to 0.3f)) {
        c.drawRect(inset * u, inset * u, s.w - inset * u, s.h - inset * u, Paint(line).apply { strokeWidth = wide * u })
    }
    val leaf = leafPaint(s, s.deco(gold))
    val k = 14.2f * u
    CoverMotifs.draw(c, Motif.FLEUR, k, k, 4.4f * u, leaf, 135f)
    CoverMotifs.draw(c, Motif.FLEUR, s.w - k, k, 4.4f * u, leaf, -135f)
    CoverMotifs.draw(c, Motif.FLEUR, k, s.h - k, 4.4f * u, leaf, 45f)
    CoverMotifs.draw(c, Motif.FLEUR, s.w - k, s.h - k, 4.4f * u, leaf, -45f)

    // The lettering piece: another leather let into the cover.
    val inlay = if (pale) s.c.accent else hueShift(mix(s.c.ground, Color.BLACK, 0.1f), 150f)
    val onInlay = if (pale) s.c.accentInk else showing(gold, inlay, 4.0)
    val cx = s.w / 2
    val wide = 56f * u
    val seriesF = t.series?.let { fit(caps(it), romanLight, 2.8f * u, 2f * u, wide, 7.4f * u, tracking = 0.2f, pitch = 1.4f) }
    val title = fit(caps(t.title), roman, 8f * u, 3.4f * u, wide, 34f * u, pitch = 1.14f, tracking = 0.04f)!!
    val piece = RectF(cx - 33f * u, 19f * u, cx + 33f * u, 19f * u + title.height + (seriesF?.let { it.height + 3.4f * u } ?: 0f) + 10.4f * u)
    c.drawRect(piece, s.fill(inlay))
    c.drawRect(piece, Paint(line).apply { strokeWidth = 0.34f * u })
    c.drawRect(piece.left + 1.2f * u, piece.top + 1.2f * u, piece.right - 1.2f * u, piece.bottom - 1.2f * u, Paint(line).apply { strokeWidth = 0.14f * u })
    var y = piece.top + 5.2f * u
    title.draw(c, cx - wide / 2, y, s.words(onInlay), sheen(s, s.words(onInlay)))
    y += title.height + 3.4f * u
    seriesF?.draw(c, cx - wide / 2, y, s.seriesWords(onInlay))

    val foot = RectF(cx - 29f * u, s.h - 27.5f * u, cx + 29f * u, s.h - 15.5f * u)
    // The device, in a ring between the two.
    val ringY = (piece.bottom + foot.top) / 2
    val ringR = min(15f * u, (foot.top - piece.bottom) / 2 - 3f * u)
    if (ringR > 6f * u) {
        c.drawCircle(cx, ringY, ringR, Paint(line).apply { strokeWidth = 0.36f * u })
        c.drawCircle(cx, ringY, ringR - 1.1f * u, Paint(line).apply { strokeWidth = 0.14f * u })
        CoverArt.stamp(c, RectF(cx - ringR * 0.74f, ringY - ringR * 0.74f, cx + ringR * 0.74f, ringY + ringR * 0.74f), art.genre, art.seed, leaf, 1.7f)
        for (side in listOf(-1f, 1f)) CoverMotifs.draw(c, Motif.SPARK, cx + side * (ringR + 4f * u), ringY, 1.5f * u, leaf)
    }
    if (t.author != null) {
        c.drawRect(foot, s.fill(inlay))
        c.drawRect(foot, Paint(line).apply { strokeWidth = 0.3f * u })
        authorIn(s, t, RectF(foot.left + 3f * u, foot.top + 1.5f * u, foot.right - 3f * u, foot.bottom - 1.5f * u), roman, 3.8f, onInlay, foil = true)
    }
}

/** A great foil medallion on cloth: petals and rings round a drawing for the genre, with scrolls in the corners. */
internal fun CoverGenerator.medallion(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    cloth(s, art.seed)
    val gold = s.c.accent
    val leaf = leafPaint(s, s.deco(gold))
    val line = Paint(leaf).apply { style = Paint.Style.STROKE; strokeWidth = 0.32f * u; strokeJoin = Paint.Join.ROUND }
    val cx = s.w / 2
    val cy = 0.36f * s.h
    val r = 27f * u
    val petals = Path().also { CoverMotifs.star(it, cx, cy, r, r * 0.8f, 24) }
    c.drawPath(petals, line)
    val inner = Path().also { CoverMotifs.star(it, cx, cy, r * 0.79f, r * 0.64f, 12, -75f) }
    c.drawPath(inner, Paint(leaf).apply { alpha = 70 })
    c.drawPath(inner, Paint(line).apply { strokeWidth = 0.2f * u })
    c.drawCircle(cx, cy, r * 0.6f, s.fill(s.c.ground))
    c.drawCircle(cx, cy, r * 0.6f, Paint(line).apply { strokeWidth = 0.44f * u })
    c.drawCircle(cx, cy, r * 0.555f, Paint(line).apply { strokeWidth = 0.14f * u })
    c.drawCircle(cx, cy, r * 1.06f, Paint(line).apply { strokeWidth = 0.26f * u })
    for (k in 0 until 36) {
        val a = Math.toRadians(k * 10.0)
        c.drawCircle(cx + (cos(a) * r * 1.14f).toFloat(), cy + (sin(a) * r * 1.14f).toFloat(), 0.42f * u, leaf)
    }
    CoverArt.stamp(c, RectF(cx - r * 0.42f, cy - r * 0.42f, cx + r * 0.42f, cy + r * 0.42f), art.genre, art.seed, leaf, 1.5f)
    // Scrolls in the corners.
    val quarter = Path()
    CoverMotifs.scroll(quarter, 6f * u, 6f * u, 15f * u, 8f, clockwise = true)
    CoverMotifs.scroll(quarter, 6f * u, 6f * u, 15f * u, 82f, clockwise = false)
    quarter.moveTo(6f * u, 6f * u)
    quarter.lineTo(6f * u, 6f * u)
    c.drawPath(fourfold(s, quarter), Paint(line).apply { strokeWidth = 0.3f * u; strokeCap = Paint.Cap.ROUND })
    val box = RectF(9f * u, cy + r * 1.14f + 5f * u, 91f * u, s.h - 21f * u)
    titleIn(s, t, box, roman, 9f, gold, s.c.ink, upper = true, tracking = 0.04f, pitch = 1.12f, foil = true, shadow = faint(Color.BLACK, 130), shadowRadius = 0.06f, shadowDy = 0.04f)
    authorIn(s, t, RectF(12f * u, s.h - 18f * u, 88f * u, s.h - 7f * u), roman, 4f, gold, foil = true)
}

/** A frame of gold scrollwork, the same in every corner, round the words. */
internal fun CoverGenerator.filigree(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val gold = s.c.accent
    val rnd = Random(art.seed)
    val leaf = leafPaint(s, s.deco(gold))
    val line = Paint(leaf).apply { style = Paint.Style.STROKE; strokeWidth = 0.34f * u; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val quarter = Path()
    val leaves = Path()
    val o = 6.5f * u
    val halfW = s.w / 2
    val halfH = s.h / 2
    // The corner: two great scrolls, and a spray between them.
    val big = (13f + rnd.nextFloat() * 4f) * u
    CoverMotifs.scroll(quarter, o, o, big, 4f, clockwise = true)
    CoverMotifs.scroll(quarter, o, o, big, 86f, clockwise = false)
    CoverMotifs.leaf(leaves, o, o, 9f * u, 45f, 0.22f)
    CoverMotifs.leaf(leaves, o + 1f * u, o + 1f * u, 6f * u, 22f, 0.26f)
    CoverMotifs.leaf(leaves, o + 1f * u, o + 1f * u, 6f * u, 68f, 0.26f)
    // Along the head and down the side: smaller scrolls turning by turns.
    val small = (7.4f + rnd.nextFloat() * 1.6f) * u
    var x = o + 14f * u
    var turn = true
    while (x < halfW - 3f * u) {
        CoverMotifs.scroll(quarter, x, o, small, if (turn) 60f else -10f, clockwise = turn)
        CoverMotifs.leaf(leaves, x, o, 3.4f * u, if (turn) -40f else 40f, 0.32f)
        x += small * 1.18f
        turn = !turn
    }
    var y = o + 14f * u
    turn = false
    while (y < halfH - 3f * u) {
        CoverMotifs.scroll(quarter, o, y, small, if (turn) 100f else 30f, clockwise = turn)
        CoverMotifs.leaf(leaves, o, y, 3.4f * u, if (turn) 130f else 50f, 0.32f)
        y += small * 1.18f
        turn = !turn
    }
    quarter.moveTo(o, o)
    quarter.lineTo(halfW, o)
    quarter.moveTo(o, o)
    quarter.lineTo(o, halfH)
    c.drawPath(fourfold(s, quarter), line)
    c.drawPath(fourfold(s, leaves), leaf)
    c.drawRect(3.6f * u, 3.6f * u, s.w - 3.6f * u, s.h - 3.6f * u, Paint(line).apply { strokeWidth = 0.18f * u })
    for (side in listOf(-1f, 1f)) CoverMotifs.draw(c, Motif.SPARK, halfW, halfH + side * (halfH - o), 2f * u, leaf)

    val box = RectF(21f * u, 0.2f * s.h, 79f * u, 0.64f * s.h)
    val took = titleIn(s, t, box, didone, 10f, gold, s.c.ink, upper = true, tracking = 0.05f, pitch = 1.16f, foil = true, gap = 3.6f)
    ornament(s, halfW, min(took.bottom + 5f * u, 0.67f * s.h), s.deco(gold))
    authorIn(s, t, RectF(22f * u, 0.7f * s.h, 78f * u, 0.8f * s.h), romanLight, 4f, s.c.ink, tracking = 0.2f)
}

/** Whiplash stems rising to buds and flowers, as the 1890s stamped their cloth, under an arched panel for the words. */
internal fun CoverGenerator.nouveau(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 70)
    val rnd = Random(art.seed)
    val stemInk = s.deco(s.c.accent)
    val bloom = mix(s.c.accent, s.c.ink, 0.35f)
    val cx = s.w / 2
    val foot = s.h - 5f * u
    val line = s.stroke(stemInk, 1f * u).apply { strokeCap = Paint.Cap.ROUND }
    val leaves = Path()
    val sway = 5f + rnd.nextFloat() * 5f
    for (k in -3..3) {
        val side = if (k < 0) -1f else 1f
        val reach = kotlin.math.abs(k)
        val x0 = cx + k * 6.4f * u
        val topY = s.h * (0.63f + 0.055f * reach) - (if (reach % 2 == 1) 5f * u else 0f)
        val x1 = cx + k * 13.2f * u
        val stem = Path().apply {
            moveTo(x0, foot)
            cubicTo(x0 - side * sway * u, foot - (foot - topY) * 0.45f, x1 + side * (sway + 6f) * u, topY + (foot - topY) * 0.5f, x1, topY)
        }
        c.drawPath(stem, Paint(line).apply { strokeWidth = (1.05f - 0.1f * reach) * u })
        if (k == 0) {
            CoverMotifs.draw(c, Motif.FLOWER, x1, topY - 4.8f * u, 6.2f * u, s.fill(bloom))
            c.drawCircle(x1, topY - 4.8f * u, 1.3f * u, s.fill(stemInk))
        } else {
            // A bud, leaning the way its stem does.
            CoverMotifs.draw(c, Motif.DROP, x1 - side * 0.6f * u, topY - 3f * u, (3.9f - 0.3f * reach) * u, s.fill(bloom), side * (-14f - 5f * reach))
            CoverMotifs.leaf(leaves, x1, topY + 0.6f * u, 4.4f * u, -90f - side * 62f, 0.3f)
        }
        // Long leaves low on the stem.
        val lx = x0 + (x1 - x0) * 0.18f - side * 1.6f * u
        val ly = foot - (foot - topY) * 0.3f
        CoverMotifs.leaf(leaves, lx, ly, (11f + 2f * reach) * u, -90f + side * (24f + 9f * reach), 0.16f)
    }
    c.drawPath(leaves, s.fill(stemInk))
    c.drawRect(3.6f * u, 3.6f * u, s.w - 3.6f * u, s.h - 3.6f * u, s.stroke(stemInk, 0.3f * u))

    // The words in a panel with an arched head.
    val l = 13f * u
    val r = s.w - l
    val top = 9f * u
    val bottom = 0.56f * s.h
    val arch = Path().apply {
        moveTo(l, bottom)
        lineTo(l, top + 12f * u)
        quadTo(l, top, cx, top)
        quadTo(r, top, r, top + 12f * u)
        lineTo(r, bottom)
        close()
    }
    c.drawPath(arch, s.fill(s.c.ground))
    c.drawPath(arch, s.stroke(stemInk, 0.4f * u))
    val innerArch = Path().apply {
        val g = 1.4f * u
        moveTo(l + g, bottom - g)
        lineTo(l + g, top + 12f * u)
        quadTo(l + g, top + g, cx, top + g)
        quadTo(r - g, top + g, r - g, top + 12f * u)
        lineTo(r - g, bottom - g)
        close()
    }
    c.drawPath(innerArch, s.stroke(faint(stemInk, 150), 0.16f * u))
    val box = RectF(l + 6f * u, top + 9f * u, r - 6f * u, bottom - 16f * u)
    val took = titleIn(s, t, box, deco, 10.5f, s.c.ink, s.c.accent, upper = true, tracking = 0.08f, pitch = 1.2f, seriesFace = serifItalic, seriesUpper = false, seriesSize = 3.5f, seriesTracking = 0f)
    val sep = min(took.bottom + 4f * u, bottom - 13f * u)
    s.diamond(cx, sep, 0.9f * u, stemInk)
    s.rule(cx - 7f * u, sep, 9f * u, 0.18f * u, stemInk)
    s.rule(cx + 7f * u, sep, 9f * u, 0.18f * u, stemInk)
    // The author's name on a tablet at the foot, over the stems.
    t.author?.let { fit(caps(it), deco, 4f * u, 2.6f * u, 60f * u, 9f * u, tracking = 0.24f, pitch = 1.4f) }?.let { f ->
        val plateR = RectF(cx - f.width / 2 - 5f * u, s.h - 9f * u - f.height - 5f * u, cx + f.width / 2 + 5f * u, s.h - 9f * u)
        c.drawRect(plateR, s.fill(s.c.ground))
        c.drawRect(plateR, s.stroke(stemInk, 0.3f * u))
        f.draw(c, cx - 30f * u, plateR.top + 2.6f * u, s.words(s.c.ink))
    }
}

/** A private-press title page: a border of printers' flowers, old-face type in two colours, and a small cut for the genre. */
internal fun CoverGenerator.finePress(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    Painter(c, whole(s), Random(art.seed)).apply {
        grain(0.6f)
        vignette(if (isDark(s.c.ground)) 80 else 26)
    }
    val red = s.c.accent
    val flower = s.fill(s.deco(red))
    // Printers' flowers set round the page.
    val inset = 6.4f * u
    val nx = ((s.w - 2 * inset) / (4.1f * u)).toInt()
    val ny = ((s.h - 2 * inset) / (4.1f * u)).toInt()
    for (i in 0..nx) {
        val x = inset + i * (s.w - 2 * inset) / nx
        for (y in listOf(inset, s.h - inset)) CoverMotifs.draw(c, if (i % 2 == 0) Motif.SPARK else Motif.LEAF, x, y, if (i % 2 == 0) 1.5f * u else 1.3f * u, flower, if (i % 2 == 0) 45f else 90f)
    }
    for (j in 1 until ny) {
        val y = inset + j * (s.h - 2 * inset) / ny
        for (x in listOf(inset, s.w - inset)) CoverMotifs.draw(c, if (j % 2 == 0) Motif.SPARK else Motif.LEAF, x, y, if (j % 2 == 0) 1.5f * u else 1.3f * u, flower, if (j % 2 == 0) 45f else 0f)
    }
    val rule = s.stroke(s.deco(faint(s.c.ink, 190)), 0.22f * u)
    c.drawRect(10.6f * u, 10.6f * u, s.w - 10.6f * u, s.h - 10.6f * u, rule)

    val cx = s.w / 2
    val box = RectF(17f * u, 17f * u, 83f * u, 0.47f * s.h)
    val took = titleIn(
        s, t, box, fell, 11.5f, s.c.ink, red, upper = true, tracking = 0.06f, pitch = 1.16f, place = -1,
        seriesFace = fellItalic, seriesUpper = false, seriesSize = 4.2f, seriesTracking = 0f, gap = 4f,
    )
    // A swelled rule under the title.
    val ry = took.bottom + 5f * u
    val swell = Path().apply {
        moveTo(cx - 17f * u, ry)
        quadTo(cx, ry - 0.9f * u, cx + 17f * u, ry)
        quadTo(cx, ry + 0.9f * u, cx - 17f * u, ry)
        close()
    }
    c.drawPath(swell, s.fill(s.deco(s.c.ink)))
    // The cut.
    val cutTop = ry + 5f * u
    val cutBottom = s.h - 31f * u
    val side = min(40f * u, cutBottom - cutTop)
    if (side > 14f * u) {
        val mid = (cutTop + cutBottom) / 2
        CoverArt.stamp(c, RectF(cx - side / 2, mid - side / 2, cx + side / 2, mid + side / 2), art.genre, art.seed, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.c.ink }, 1.25f)
    }
    s.rule(cx, s.h - 27f * u, 30f * u, 0.22f * u, s.deco(faint(s.c.ink, 190)))
    authorIn(s, t, RectF(17f * u, s.h - 25f * u, 83f * u, s.h - 14f * u), fellCaps, 4.6f, s.c.ink, upper = false, tracking = 0.1f)
}

/** A playbill: every line of the title in another wood type, each as wide as the sheet, with rules and stars between, and a cut under it. */
internal fun CoverGenerator.playbill(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    Painter(c, whole(s), Random(art.seed)).apply {
        grain(1f)
        vignette(if (isDark(s.c.ground)) 110 else 50)
    }
    val ink = s.c.ink
    val line = s.deco(s.c.accent)
    c.drawRect(4f * u, 4f * u, s.w - 4f * u, s.h - 4f * u, s.stroke(line, 1f * u))
    c.drawRect(5.8f * u, 5.8f * u, s.w - 5.8f * u, s.h - 5.8f * u, s.stroke(line, 0.24f * u))
    val cx = s.w / 2
    val wide = 80f * u
    var y = 11f * u
    fun stars(at: Float) {
        s.rule(cx - 25f * u, at, 26f * u, 0.3f * u, line)
        s.rule(cx + 25f * u, at, 26f * u, 0.3f * u, line)
        for (k in -1..1) CoverMotifs.draw(c, Motif.STAR, cx + k * 3.6f * u, at, 1.3f * u, s.fill(line))
    }
    t.series?.let { fit(caps(it), slab, 3.6f * u, 2.4f * u, wide, 9f * u, tracking = 0.08f, pitch = 1.3f) }?.let { f ->
        f.draw(c, cx - wide / 2, y, s.seriesWords(s.c.accent))
        y += f.height + 4f * u
    }
    stars(y)
    y += 4.4f * u
    val bottom = s.h - 31f * u
    val lines = shortLines(caps(t.title))
    if (lines == null) {
        titleIn(s, t, RectF(cx - wide / 2, y, cx + wide / 2, bottom), slab, 9f, ink, ink, upper = true, pitch = 1.2f, series = false)
    } else {
        val faces = listOf(woodType, slab, condensed, roman, slab, woodType)
        val shift = Random(art.seed).nextInt(faces.size)
        val gap = 3.2f * u
        val tallest = 24f * u
        val heights = lines.indices.map { bigLine(c, lines[it], faces[(it + shift) % faces.size], cx, 0f, wide, tallest, ink, measureOnly = true) }
        val total = heights.sum() + gap * (lines.size - 1)
        val room = bottom - y
        val scale = min(1f, room / total)
        // With room to spare, a cut for the genre goes under the title.
        val spare = room - total * scale
        val cut = spare > 27f * u
        var ly = y + (if (cut) 1f * u else spare / 2)
        for (i in lines.indices) {
            val drawn = bigLine(c, lines[i], faces[(i + shift) % faces.size], cx, ly, wide * scale, tallest * scale, s.words(ink))
            ly += drawn + gap * scale
            if (i < lines.size - 1 && i % 2 == 0) s.rule(cx, ly - gap * scale / 2, wide * scale, 0.24f * u, line)
        }
        if (cut) {
            val side = min(36f * u, bottom - ly - 5f * u)
            val mid = (ly + bottom) / 2
            CoverArt.stamp(c, RectF(cx - side / 2, mid - side / 2, cx + side / 2, mid + side / 2), art.genre, art.seed, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }, 1.3f)
        }
    }
    stars(s.h - 27f * u)
    authorIn(s, t, RectF(cx - wide / 2, s.h - 23.5f * u, cx + wide / 2, s.h - 10f * u), woodType, 5.4f, ink, tracking = 0.06f, pitch = 1.25f)
}

/** A card from a fortune-teller's pack: a frame with cut corners, a number at its head, a great emblem among stars, and the title on a tablet. */
internal fun CoverGenerator.tarot(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val gold = s.c.accent
    val rnd = Random(art.seed)
    val line = s.stroke(s.deco(gold), 0.4f * u)
    val leaf = leafPaint(s, s.deco(gold))
    val cx = s.w / 2
    c.drawRoundRect(RectF(4f * u, 4f * u, s.w - 4f * u, s.h - 4f * u), 3f * u, 3f * u, line)
    // The inner frame, its corners cut away in quarter rounds.
    val l = 8f * u
    val r = s.w - l
    val top = 8f * u
    val bottom = s.h - 8f * u
    val q = 5f * u
    val frame = Path().apply {
        moveTo(l + q, top)
        lineTo(r - q, top)
        arcTo(RectF(r - q, top - q, r + q, top + q), 180f, -90f)
        lineTo(r, bottom - q)
        arcTo(RectF(r - q, bottom - q, r + q, bottom + q), 270f, -90f)
        lineTo(l + q, bottom)
        arcTo(RectF(l - q, bottom - q, l + q, bottom + q), 0f, -90f)
        lineTo(l, top + q)
        arcTo(RectF(l - q, top - q, l + q, top + q), 90f, -90f)
        close()
    }
    c.drawPath(frame, Paint(line).apply { strokeWidth = 0.22f * u })
    // The number of the card: the book's place in its series.
    val number = t.seriesIndex?.toInt()?.takeIf { it.toDouble() == t.seriesIndex && it in 1..3999 }?.let(::romanNumeral)
    var headY = 14f * u
    if (number != null) {
        fit(number, roman, 6f * u, 4f * u, 40f * u, 7f * u, tracking = 0.12f)?.let { f ->
            f.draw(c, cx - 20f * u, headY, s.seriesWords(gold))
            headY += f.height
        }
    } else {
        CoverMotifs.draw(c, Motif.SPARK, cx, headY + 2.4f * u, 2.6f * u, leaf)
        headY += 5f * u
    }
    s.rule(cx, headY + 3f * u, 56f * u, 0.2f * u, s.deco(faint(gold, 190)))

    // The emblem, in a ring of rays, with stars about it.
    val tabletTop = 0.69f * s.h
    val ey = (headY + 3f * u + tabletTop) / 2
    val er = min(21f * u, (tabletTop - headY - 12f * u) / 2)
    val rays = Path()
    for (k in 0 until 32) {
        val a = Math.toRadians(k * 11.25)
        val far = if (k % 2 == 0) er * 1.42f else er * 1.22f
        rays.moveTo(cx + (cos(a) * er * 1.06f).toFloat(), ey + (sin(a) * er * 1.06f).toFloat())
        rays.lineTo(cx + (cos(a) * far).toFloat(), ey + (sin(a) * far).toFloat())
    }
    c.drawPath(rays, Paint(line).apply { strokeWidth = 0.28f * u; strokeCap = Paint.Cap.ROUND })
    c.drawCircle(cx, ey, er, Paint(line).apply { strokeWidth = 0.36f * u })
    c.drawCircle(cx, ey, er * 0.93f, Paint(line).apply { strokeWidth = 0.14f * u })
    val emblem = CoverMotifs.forGenre(art.genre)[rnd.nextInt(4)]
    CoverMotifs.draw(c, emblem, cx, ey, er * 0.6f, leaf)
    repeat(14) {
        val a = rnd.nextFloat() * 6.283f
        val d = er * (1.6f + rnd.nextFloat() * 0.5f)
        val sx = cx + cos(a) * d
        val sy = ey + sin(a) * d * 0.9f
        if (sx > l + 4f * u && sx < r - 4f * u && sy > headY + 6f * u && sy < tabletTop - 3f * u) CoverMotifs.draw(c, Motif.SPARK, sx, sy, (0.7f + rnd.nextFloat() * 1.1f) * u, leaf)
    }
    for (side in listOf(-1f, 1f)) CoverMotifs.draw(c, Motif.MOON, cx + side * (er * 1.42f + 4.4f * u), ey, 2.6f * u, leaf, if (side < 0) 180f else 0f)

    // The title on a tablet, and the series under it.
    val tablet = RectF(l + 3f * u, tabletTop, r - 3f * u, s.h - 21f * u)
    c.drawRect(tablet, s.fill(mix(s.c.ground, Color.BLACK, 0.25f)))
    c.drawRect(tablet, Paint(line).apply { strokeWidth = 0.3f * u })
    titleIn(
        s, t, RectF(tablet.left + 3f * u, tablet.top + 2.4f * u, tablet.right - 3f * u, tablet.bottom - 2.4f * u), roman, 7.4f, gold, s.c.ink,
        upper = true, tracking = 0.06f, smallest = 3.2f, seriesSize = 2.6f, gap = 2.2f, foil = true,
    )
    authorIn(s, t, RectF(l + 4f * u, s.h - 19f * u, r - 4f * u, s.h - 10f * u), romanLight, 3.6f, s.c.ink, tracking = 0.2f)
}

/** A chart of the heavens: rings and tick marks, constellations joined star to star, a sun above and a moon below. */
internal fun CoverGenerator.celestial(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    // (A night in the theme's second colour where that's a dark one, otherwise in its dark.)
    val nightC = if (ColorUtils.calculateLuminance(s.c.accent) < 0.12) s.c.accent else n.dark
    val deep = mix(nightC, Color.BLACK, 0.5f)
    val p = Painter(c, whole(s), Random(art.seed))
    p.sky(intArrayOf(mix(deep, Color.BLACK, 0.3f), deep, mix(deep, nightC, 0.6f)))
    p.stars(220, 1f, n.light)
    val gold = n.accentOn(deep)
    val line = s.stroke(s.deco(faint(gold, 220)), 0.26f * u)
    val cx = s.w / 2
    val cy = 0.57f * s.h
    val r = min(38f * u, 0.27f * s.h)
    p.glow(cx, cy, r * 1.3f, gold, 30)
    c.drawCircle(cx, cy, r, Paint(line).apply { strokeWidth = 0.36f * u })
    c.drawCircle(cx, cy, r - 2.4f * u, line)
    c.drawCircle(cx, cy, r * 0.58f, Paint(line).apply { pathEffect = DashPathEffect(floatArrayOf(1.2f * u, 1.2f * u), 0f) })
    for (k in 0 until 72) {
        val a = Math.toRadians(k * 5.0)
        val inner = r - (if (k % 6 == 0) 2.4f else 1.2f) * u
        c.drawLine(cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), line)
    }
    for (k in 0 until 6) {
        val a = Math.toRadians(k * 30.0 + 15.0)
        c.drawLine(cx - (cos(a) * (r - 2.4f * u)).toFloat(), cy - (sin(a) * (r - 2.4f * u)).toFloat(), cx + (cos(a) * (r - 2.4f * u)).toFloat(), cy + (sin(a) * (r - 2.4f * u)).toFloat(), s.stroke(s.deco(faint(gold, 50)), 0.14f * u))
    }
    // Constellations: a few stars each, joined in a line.
    val join = s.stroke(faint(n.light, 150), 0.2f * u)
    repeat(4) {
        val a = p.rnd.nextFloat() * 6.283f
        val d = r * (0.2f + p.rnd.nextFloat() * 0.5f)
        var px = cx + cos(a) * d
        var py = cy + sin(a) * d
        var heading = p.rnd.nextFloat() * 6.283f
        repeat(4 + p.rnd.nextInt(3)) {
            c.drawCircle(px, py, (0.5f + p.rnd.nextFloat() * 0.6f) * u, s.fill(n.light))
            p.glow(px, py, 2.2f * u, n.light, 70)
            heading += (p.rnd.nextFloat() - 0.5f) * 1.9f
            val step = (5f + p.rnd.nextFloat() * 5f) * u
            val nx = px + cos(heading) * step
            val ny = py + sin(heading) * step
            val dx = nx - cx
            val dy = ny - cy
            if (dx * dx + dy * dy < (r - 5f * u) * (r - 5f * u)) {
                c.drawLine(px, py, nx, ny, join)
                px = nx
                py = ny
            } else heading += 3.1f
        }
        c.drawCircle(px, py, 0.7f * u, s.fill(n.light))
    }
    // The sun at the head of the ring and the moon at its foot.
    val orb = s.fill(gold)
    c.drawCircle(cx, cy - r, 5.4f * u, s.fill(deep))
    CoverMotifs.draw(c, Motif.SUN, cx, cy - r, 4.6f * u, orb)
    c.drawCircle(cx, cy + r, 4.6f * u, s.fill(mix(deep, n.dark, 0.5f)))
    CoverMotifs.draw(c, Motif.MOON, cx, cy + r, 3.4f * u, orb, -30f)
    // The four quarters marked on the ring.
    for (k in 0 until 4) {
        val a = Math.toRadians(45.0 + k * 90.0)
        CoverMotifs.draw(c, Motif.SPARK, cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), 2.3f * u, orb)
    }

    val headRoom = cy - r - 7f * u - 9f * u
    titleIn(s, t, RectF(8f * u, 9f * u, 92f * u, 9f * u + headRoom), didone, 10f, n.light, gold, upper = true, tracking = 0.08f, pitch = 1.14f, shadow = faint(gold, 150), shadowRadius = 1.6f)
    authorIn(s, t, RectF(12f * u, min(cy + r + 6.5f * u, s.h - 14f * u), 88f * u, s.h - 5f * u), romanLight, 3.9f, n.light, tracking = 0.22f)
}

/** A window of coloured glass in its leads: a rose of petals above, and lights of diamond panes below. */
internal fun CoverGenerator.stainedGlass(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    val stone = mix(n.dark, Color.BLACK, 0.72f)
    c.drawColor(stone)
    val rnd = Random(art.seed)
    val a = n.accentOn(stone)
    val glass = listOf(a, hueShift(a, 150f), hueShift(a, -70f), hueShift(a, 200f, -0.08f), mix(n.dark, Color.WHITE, 0.3f), hueShift(a, 35f))
    val lead = s.stroke(stone, 0.7f * u).apply { strokeJoin = Paint.Join.ROUND }
    fun pane(path: Path, colour: Int, cxp: Float, cyp: Float, size: Float) {
        val lit = mix(colour, Color.WHITE, 0.14f + rnd.nextFloat() * 0.18f)
        val dim = mix(colour, Color.BLACK, 0.1f + rnd.nextFloat() * 0.26f)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cxp, cyp, max(1f, size), lit, dim, Shader.TileMode.CLAMP) })
        c.drawPath(path, lead)
    }
    val l = 10f * u
    val r = s.w - l
    val cx = s.w / 2
    val headTop = 31f * u
    val foot = s.h - 22f * u
    val span = r - l
    val spring = headTop + span * 0.62f
    // The window's shape: a pointed arch.
    val window = Path().apply {
        moveTo(l, foot)
        lineTo(l, spring)
        quadTo(l, headTop + span * 0.12f, cx, headTop)
        quadTo(r, headTop + span * 0.12f, r, spring)
        lineTo(r, foot)
        close()
    }
    c.save()
    c.clipPath(window)
    c.drawColor(mix(n.dark, Color.BLACK, 0.3f))
    // Lights of diamond panes below the rose.
    val d = 6.4f * u
    var row = 0
    var y = spring - d
    while (y < foot + d) {
        var x = l - d + if (row % 2 == 0) 0f else d
        while (x < r + d) {
            val diamond = Path().apply { moveTo(x, y - d); lineTo(x + d, y); lineTo(x, y + d); lineTo(x - d, y); close() }
            val edge = x < l + d * 1.2f || x > r - d * 1.2f
            pane(diamond, if (edge) glass[1] else if (rnd.nextInt(6) == 0) glass[2] else mix(glass[4], glass[rnd.nextInt(glass.size)], 0.35f), x, y, d)
            x += 2 * d
        }
        y += d
        row++
    }
    // The rose: petals round a middle, and a ring of small lights round them.
    val ry = headTop + span * 0.47f
    val rr = span * 0.36f
    c.drawCircle(cx, ry, rr * 1.2f, s.fill(stone))
    for (k in 0 until 12) {
        val a0 = k * 30f
        val wedge = Path().apply {
            arcTo(RectF(cx - rr * 1.14f, ry - rr * 1.14f, cx + rr * 1.14f, ry + rr * 1.14f), a0 + 2f, 26f, true)
            arcTo(RectF(cx - rr * 0.86f, ry - rr * 0.86f, cx + rr * 0.86f, ry + rr * 0.86f), a0 + 28f, -26f)
            close()
        }
        val mid = Math.toRadians(a0 + 15.0)
        pane(wedge, glass[(k % 2) * 3], cx + (cos(mid) * rr).toFloat(), ry + (sin(mid) * rr).toFloat(), rr * 0.3f)
        val petal = Path()
        CoverMotifs.leaf(petal, cx + (cos(mid) * rr * 0.26f).toFloat(), ry + (sin(mid) * rr * 0.26f).toFloat(), rr * 0.58f, a0 + 15f, 0.42f)
        pane(petal, glass[if (k % 2 == 0) 0 else 5], cx + (cos(mid) * rr * 0.55f).toFloat(), ry + (sin(mid) * rr * 0.55f).toFloat(), rr * 0.3f)
    }
    val eye = Path().apply { addCircle(cx, ry, rr * 0.24f, Path.Direction.CW) }
    pane(eye, glass[2], cx, ry, rr * 0.24f)
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, ry, rr * 0.15f, s.fill(stone))
    c.restore()
    c.drawPath(window, s.stroke(stone, 1.5f * u))
    c.drawPath(window, s.stroke(a, 0.34f * u))
    Painter(c, whole(s), rnd).glow(cx, ry, span * 0.8f, a, 34)

    titleIn(s, t, RectF(8f * u, 6f * u, 92f * u, headTop - 3f * u), blackLetter, 10.5f, n.light, a, pitch = 1.05f, seriesSize = 2.7f, gap = 2.4f, shadow = faint(a, 130), shadowRadius = 1.4f)
    authorIn(s, t, RectF(10f * u, foot + 3f * u, 90f * u, s.h - 5f * u), roman, 4.2f, n.light, tracking = 0.16f)
}

/** A coat of arms: a shield parted in two tinctures with a charge for the genre, laurel either side of it, a crown above and the title on a scroll. */
internal fun CoverGenerator.heraldry(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed)
    val rnd = Random(art.seed)
    val metal = s.c.accent
    val deeper = if (isDark(s.c.ground)) mix(s.c.ground, Color.BLACK, 0.4f) else mix(s.c.ground, s.c.ink, 0.75f)
    val tincture = showing(hueShift(mix(s.c.ground, metal, 0.2f), 140f + rnd.nextInt(3) * 40f), metal, 2.2)
    val cx = s.w / 2
    val top = 0.2f * s.h
    val half = 23f * u
    val tall = 0.42f * s.h
    val shield = Path().apply {
        moveTo(cx - half, top)
        lineTo(cx + half, top)
        lineTo(cx + half, top + tall * 0.5f)
        cubicTo(cx + half, top + tall * 0.78f, cx + half * 0.45f, top + tall * 0.93f, cx, top + tall)
        cubicTo(cx - half * 0.45f, top + tall * 0.93f, cx - half, top + tall * 0.78f, cx - half, top + tall * 0.5f)
        close()
    }
    // Branches of laurel either side of the shield.
    val branches = Path()
    CoverMotifs.wreath(branches, cx, top + tall * 0.5f, half * 1.5f, 12, 100f, half * 0.42f)
    c.drawPath(branches, s.fill(s.deco(mix(metal, s.c.ground, 0.3f))))
    c.drawPath(shield, s.fill(metal).apply { setShadowLayer(1.6f * u, 0f, 0.8f * u, faint(Color.BLACK, 120)) })
    val charge = CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)]
    c.save()
    c.clipPath(shield)
    val midY = top + tall * 0.46f
    when (rnd.nextInt(4)) {
        0 -> {
            // Parted down the middle.
            c.drawRect(cx, top, cx + half, top + tall, s.fill(tincture))
            CoverMotifs.draw(c, charge, cx - half * 0.5f, midY, half * 0.34f, s.fill(tincture))
            CoverMotifs.draw(c, charge, cx + half * 0.5f, midY, half * 0.34f, s.fill(metal))
        }
        1 -> {
            // Quartered.
            c.drawRect(cx - half, top, cx, midY, s.fill(tincture))
            c.drawRect(cx, midY, cx + half, top + tall, s.fill(tincture))
            CoverMotifs.draw(c, charge, cx - half * 0.5f, top + tall * 0.23f, half * 0.3f, s.fill(metal))
            CoverMotifs.draw(c, charge, cx + half * 0.44f, top + tall * 0.64f, half * 0.26f, s.fill(metal))
        }
        2 -> {
            // A chevron.
            c.drawPath(Path().apply {
                moveTo(cx - half, top + tall * 0.62f); lineTo(cx, top + tall * 0.3f); lineTo(cx + half, top + tall * 0.62f)
                lineTo(cx + half, top + tall * 0.8f); lineTo(cx, top + tall * 0.48f); lineTo(cx - half, top + tall * 0.8f); close()
            }, s.fill(tincture))
            CoverMotifs.draw(c, charge, cx, top + tall * 0.17f, half * 0.24f, s.fill(tincture))
            CoverMotifs.draw(c, charge, cx, top + tall * 0.72f, half * 0.2f, s.fill(tincture))
        }
        else -> {
            // A broad band across the head.
            c.drawRect(cx - half, top, cx + half, top + tall * 0.3f, s.fill(tincture))
            CoverMotifs.draw(c, charge, cx, top + tall * 0.6f, half * 0.42f, s.fill(tincture))
            for (k in -1..1) CoverMotifs.draw(c, Motif.STAR, cx + k * half * 0.56f, top + tall * 0.15f, half * 0.14f, s.fill(metal))
        }
    }
    c.restore()
    c.drawPath(shield, s.stroke(s.deco(deeper), 0.7f * u).apply { strokeJoin = Paint.Join.ROUND })
    CoverMotifs.draw(c, Motif.CROWN, cx, top - 6.4f * u, 6.4f * u, s.fill(s.deco(metal)))

    // The title on a scroll below.
    val wide = 66f * u
    val title = fit(caps(t.title), roman, 7.6f * u, 3.2f * u, wide, s.h - 21f * u - (top + tall + 9f * u), pitch = 1.12f, tracking = 0.04f)!!
    val ribbonTop = top + tall + 5f * u
    val ribbon = RectF(cx - wide / 2 - 5f * u, ribbonTop, cx + wide / 2 + 5f * u, ribbonTop + title.height + 6.6f * u)
    for (side in listOf(-1f, 1f)) {
        val x0 = if (side < 0) ribbon.left else ribbon.right
        c.drawPath(Path().apply {
            moveTo(x0, ribbon.top + 2.4f * u)
            lineTo(x0 + side * 7f * u, ribbon.top + 2.4f * u)
            lineTo(x0 + side * 4.4f * u, ribbon.centerY() + 2.4f * u)
            lineTo(x0 + side * 7f * u, ribbon.bottom + 2.4f * u)
            lineTo(x0 - side * 3f * u, ribbon.bottom + 2.4f * u)
            close()
        }, s.fill(mix(metal, Color.BLACK, 0.35f)))
    }
    c.drawRect(ribbon, s.fill(metal).apply { setShadowLayer(1.2f * u, 0f, 0.5f * u, faint(Color.BLACK, 110)) })
    title.draw(c, cx - wide / 2, ribbon.top + 3.3f * u, s.words(s.c.accentInk))
    t.series?.let { fit(caps(it), romanLight, 3f * u, 2.2f * u, 80f * u, 8f * u, tracking = 0.2f, pitch = 1.4f) }?.draw(c, cx - 40f * u, 7.5f * u, s.seriesWords(s.c.ink))
    authorIn(s, t, RectF(12f * u, s.h - 17f * u, 88f * u, s.h - 6f * u), roman, 4f, s.c.ink)
}

/** A map drawn by hand: an island with its mountains and woods, a dotted way to a cross, a compass, and the title in a cartouche. */
internal fun CoverGenerator.oldMap(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    repeat(14) { p.glow(p.x(rnd.nextFloat()), p.y(rnd.nextFloat()), s.w * (0.12f + rnd.nextFloat() * 0.22f), if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 12 + rnd.nextInt(12)) }
    p.grain(0.9f)
    val ink = s.c.ink
    val pen = s.stroke(faint(ink, 220), 0.3f * u).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    val cx = s.w * 0.5f
    val cy = s.h * 0.6f
    // The coast: a ragged round, drawn three times, the outer two fainter (shallows).
    val seed = art.seed
    fun coast(grow: Float) = Path().apply {
        for (k in 0..120) {
            val a = k / 120f * 6.2832f
            val rough = CoverMotifs.cloudy(2f + cos(a) * 1.5f, 2f + sin(a) * 1.5f, seed, 4)
            val rr = (20f + rough * 26f + grow) * u
            val x = cx + cos(a) * rr * 1.05f
            val y = cy + sin(a) * rr * 0.86f
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    c.drawPath(coast(3.4f), s.stroke(faint(ink, 50), 0.2f * u))
    c.drawPath(coast(1.8f), s.stroke(faint(ink, 90), 0.2f * u))
    val land = coast(0f)
    c.drawPath(land, s.fill(mix(s.c.ground, ink, 0.07f)))
    c.drawPath(land, pen)
    c.save()
    c.clipPath(land)
    // Mountains in a range, and woods.
    val mx = cx + (rnd.nextFloat() - 0.5f) * 14f * u
    val my = cy - 6f * u
    for (k in 0 until 9) {
        val x = mx + (k - 4) * 4.2f * u + (rnd.nextFloat() - 0.5f) * 2f * u
        val y = my + (rnd.nextFloat() - 0.5f) * 7f * u
        val hgt = (4f + rnd.nextFloat() * 3f) * u
        c.drawPath(Path().apply { moveTo(x - hgt * 0.8f, y); lineTo(x, y - hgt); lineTo(x + hgt * 0.8f, y) }, pen)
        c.drawLine(x, y - hgt, x + hgt * 0.25f, y - hgt * 0.2f, s.stroke(faint(ink, 150), 0.2f * u))
    }
    for (k in 0 until 16) {
        val x = cx - 18f * u + rnd.nextFloat() * 14f * u
        val y = cy + 9f * u + rnd.nextFloat() * 9f * u
        CoverMotifs.draw(c, Motif.TREE, x, y, 1.6f * u, s.fill(faint(ink, 200)))
    }
    c.restore()
    // The way: dots from the shore to a cross.
    val goalX = cx + 12f * u
    val goalY = cy + 10f * u
    val way = Path().apply {
        moveTo(cx - 30f * u, cy + 26f * u)
        cubicTo(cx - 12f * u, cy + 18f * u, cx - 14f * u, cy - 2f * u, cx + 2f * u, cy + 2f * u)
        quadTo(cx + 9f * u, cy + 4f * u, goalX, goalY)
    }
    val route = s.deco(s.c.accent)
    c.drawPath(way, s.stroke(route, 0.5f * u).apply { pathEffect = DashPathEffect(floatArrayOf(1.4f * u, 1.5f * u), 0f); strokeCap = Paint.Cap.ROUND })
    val cross = s.stroke(route, 1f * u).apply { strokeCap = Paint.Cap.ROUND }
    c.drawLine(goalX - 1.8f * u, goalY - 1.8f * u, goalX + 1.8f * u, goalY + 1.8f * u, cross)
    c.drawLine(goalX - 1.8f * u, goalY + 1.8f * u, goalX + 1.8f * u, goalY - 1.8f * u, cross)
    // Waves on the sea, and the compass.
    for (k in 0 until 10) {
        val x = 12f * u + rnd.nextFloat() * 76f * u
        val y = s.h * 0.36f + rnd.nextFloat() * s.h * 0.5f
        if (!RectF(cx - 40f * u, cy - 30f * u, cx + 40f * u, cy + 30f * u).contains(x, y)) {
            c.drawPath(Path().apply { moveTo(x, y); quadTo(x + 1.2f * u, y - 1.2f * u, x + 2.4f * u, y); quadTo(x + 3.6f * u, y - 1.2f * u, x + 4.8f * u, y) }, s.stroke(faint(ink, 110), 0.2f * u))
        }
    }
    CoverMotifs.draw(c, Motif.COMPASS, s.w - 17f * u, s.h * 0.4f, 7.4f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.c.accent })
    // The neat line round the sheet: a rule, and a band of light and dark for the scale.
    c.drawRect(4f * u, 4f * u, s.w - 4f * u, s.h - 4f * u, s.stroke(s.deco(ink), 0.3f * u))
    c.drawRect(5.6f * u, 5.6f * u, s.w - 5.6f * u, s.h - 5.6f * u, s.stroke(s.deco(ink), 0.2f * u))
    var k = 0
    var x = 4f * u
    while (x < s.w - 4f * u) {
        if (k % 2 == 0) {
            val x1 = min(x + 4f * u, s.w - 4f * u)
            s.rect(x, 4f * u, x1, 5.6f * u, s.deco(s.c.accent))
            s.rect(x, s.h - 5.6f * u, x1, s.h - 4f * u, s.deco(s.c.accent))
        }
        x += 4f * u
        k++
    }
    // The cartouche.
    val wide = 64f * u
    val seriesF = t.series?.let { fit(it, fellItalic, 3.8f * u, 2.6f * u, wide, 9f * u) }
    val title = fit(caps(t.title), fell, 8.6f * u, 3.4f * u, wide, 0.24f * s.h - (seriesF?.let { it.height + 3f * u } ?: 0f), pitch = 1.14f, tracking = 0.05f)!!
    val scx = s.w / 2
    val card = RectF(scx - wide / 2 - 5f * u, 10f * u, scx + wide / 2 + 5f * u, 10f * u + title.height + (seriesF?.let { it.height + 3f * u } ?: 0f) + 9.6f * u)
    plate(s, card, s.c.ground, s.deco(ink), 1.6f * u)
    var y = card.top + 4.8f * u
    title.draw(c, scx - wide / 2, y, s.words(ink))
    y += title.height + 3f * u
    seriesF?.draw(c, scx - wide / 2, y, s.seriesWords(s.c.accent))
    t.author?.let { fit(caps(it), fellCaps, 4f * u, 2.6f * u, 54f * u, 9f * u, tracking = 0.12f, pitch = 1.35f) }?.let { f ->
        val r = RectF(scx - f.width / 2 - 5f * u, s.h - 11f * u - f.height - 5f * u, scx + f.width / 2 + 5f * u, s.h - 11f * u)
        plate(s, r, s.c.ground, s.deco(ink), 1.2f * u)
        f.draw(c, scx - 27f * u, r.top + 2.5f * u, s.words(ink))
    }
}

/** A plait of two strands along [from] to [to], crossing over and under by turns. */
private fun CoverGenerator.plait(s: Sheet, x0: Float, y0: Float, x1: Float, y1: Float, swing: Float, colour: Int, edge: Int) {
    val c = s.canvas
    val u = s.u
    val length = kotlin.math.hypot(x1 - x0, y1 - y0)
    val waves = max(2, (length / (swing * 3.4f)).toInt())
    val dx = (x1 - x0) / length
    val dy = (y1 - y0) / length
    fun strand(sign: Float, from: Float, to: Float) = Path().apply {
        val steps = max(8, ((to - from) * 90).toInt())
        for (i in 0..steps) {
            val f = from + (to - from) * i / steps
            val along = f * length
            val off = sign * swing * sin(f * waves * Math.PI.toFloat())
            val x = x0 + dx * along - dy * off
            val y = y0 + dy * along + dx * off
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    val body = s.stroke(colour, swing * 0.62f)
    val rim = s.stroke(edge, swing * 0.62f + 0.5f * u)
    for (sign in listOf(1f, -1f)) {
        c.drawPath(strand(sign, 0f, 1f), rim)
        c.drawPath(strand(sign, 0f, 1f), body)
    }
    // Where they cross, the first strand comes back over every other time.
    for (k in 0 until waves step 2) {
        val at = (k + 0f) / waves
        val piece = strand(1f, max(0f, at - 0.22f / waves), min(1f, at + 0.22f / waves))
        c.drawPath(piece, rim)
        c.drawPath(piece, body)
    }
}

/** A border of plaited knotwork, with a knot of three at the head. */
internal fun CoverGenerator.knotwork(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 100)
    val gold = s.deco(s.c.accent)
    val edge = mix(s.c.ground, Color.BLACK, 0.55f)
    val o = 8f * u
    val swing = 2.3f * u
    plait(s, o, o, s.w - o, o, swing, gold, edge)
    plait(s, o, s.h - o, s.w - o, s.h - o, swing, gold, edge)
    plait(s, o, o, o, s.h - o, swing, gold, edge)
    plait(s, s.w - o, o, s.w - o, s.h - o, swing, gold, edge)
    for (x in listOf(o, s.w - o)) for (y in listOf(o, s.h - o)) {
        c.drawRect(x - 3.8f * u, y - 3.8f * u, x + 3.8f * u, y + 3.8f * u, s.fill(edge))
        c.drawRect(x - 3.2f * u, y - 3.2f * u, x + 3.2f * u, y + 3.2f * u, s.fill(gold))
        s.diamond(x, y, 2.3f * u, edge)
        s.diamond(x, y, 1.1f * u, gold)
    }
    // A knot of three: three leaves of two arcs each, laced through a ring.
    val cx = s.w / 2
    val ky = 26f * u
    val kr = 7.4f * u
    val knot = Path()
    for (k in 0 until 3) CoverMotifs.leaf(knot, cx, ky, kr * 1.5f, -90f + k * 120f, 0.44f)
    c.drawPath(knot, s.stroke(edge, 1.5f * u).apply { strokeJoin = Paint.Join.ROUND })
    c.drawPath(knot, s.stroke(gold, 0.9f * u).apply { strokeJoin = Paint.Join.ROUND })
    c.drawCircle(cx, ky + 0.4f * u, kr * 0.74f, s.stroke(edge, 1.5f * u))
    c.drawCircle(cx, ky + 0.4f * u, kr * 0.74f, s.stroke(gold, 0.9f * u))

    val box = RectF(18f * u, 39f * u, 82f * u, 0.66f * s.h)
    val took = titleIn(s, t, box, romanDecorated, 9.4f, s.c.accent, s.c.ink, pitch = 1.12f, foil = true, shadow = faint(Color.BLACK, 140), shadowRadius = 0.08f, shadowDy = 0.05f)
    ornament(s, cx, min(took.bottom + 5f * u, 0.69f * s.h), gold)
    authorIn(s, t, RectF(18f * u, 0.72f * s.h, 82f * u, 0.82f * s.h), roman, 4.1f, s.c.ink)
}

/** Bands of the Greek key at head and foot, and a painted roundel between, as on a vase. */
internal fun CoverGenerator.meander(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    Painter(c, whole(s), rnd).apply {
        grain(0.8f)
        vignette(if (isDark(s.c.ground)) 90 else 40)
    }
    val glaze = if (isDark(s.c.ground)) mix(s.c.ground, Color.BLACK, 0.5f) else s.c.ink
    val key = s.deco(showing(s.c.accent, glaze, 3.0))
    val band = 12.5f * u
    for (top in listOf(0f, s.h - band)) {
        s.rect(0f, top, s.w, top + band, glaze)
        val y0 = top + 2.6f * u
        val y1 = top + band - 2.6f * u
        val hk = y1 - y0
        val n = 10
        val pch = s.w / n
        val pen = s.stroke(key, 0.8f * u).apply { strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.SQUARE }
        val path = Path()
        for (i in 0 until n) {
            val x = i * pch + pch * 0.1f
            path.moveTo(x, y1)
            path.lineTo(x, y0)
            path.lineTo(x + pch * 0.72f, y0)
            path.lineTo(x + pch * 0.72f, y0 + hk * 0.7f)
            path.lineTo(x + pch * 0.3f, y0 + hk * 0.7f)
            path.lineTo(x + pch * 0.3f, y0 + hk * 0.36f)
            path.lineTo(x + pch * 0.5f, y0 + hk * 0.36f)
        }
        c.drawPath(path, pen)
        c.drawLine(0f, top + 1.1f * u, s.w, top + 1.1f * u, s.stroke(key, 0.3f * u))
        c.drawLine(0f, top + band - 1.1f * u, s.w, top + band - 1.1f * u, s.stroke(key, 0.3f * u))
    }
    val cx = s.w / 2
    val took = titleIn(s, t, RectF(10f * u, band + 7f * u, 90f * u, 0.4f * s.h), roman, 9.6f, s.c.ink, s.c.accent, upper = true, tracking = 0.06f, pitch = 1.14f, place = -1)
    // The roundel: a figure for the genre, dark on the clay.
    val footTop = s.h - band - 17f * u
    val ry = (took.bottom + footTop) / 2
    val rr = min(19f * u, (footTop - took.bottom) / 2 - 3f * u)
    if (rr > 7f * u) {
        c.drawCircle(cx, ry, rr, s.fill(glaze))
        c.drawCircle(cx, ry, rr * 0.88f, s.fill(s.c.accent))
        c.drawCircle(cx, ry, rr * 0.88f, s.stroke(key, 0.26f * u))
        val set = CoverMotifs.forGenre(art.genre)
        CoverMotifs.draw(c, set[rnd.nextInt(3)], cx, ry, rr * 0.52f, s.fill(showing(glaze, s.c.accent, 3.0)))
        val wreath = Path()
        CoverMotifs.wreath(wreath, cx, ry, rr * 1.22f, 14, 50f, rr * 0.2f)
        c.drawPath(wreath, s.fill(s.deco(faint(s.c.ink, 200))))
    }
    authorIn(s, t, RectF(10f * u, footTop, 90f * u, s.h - band - 5f * u), roman, 4.2f, s.c.ink)
}

/** Tilework: eight-pointed stars and the crosses between them, with the title on a plain tile among them. */
internal fun CoverGenerator.tilework(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val cols = 5 + rnd.nextInt(2)
    val cell = s.w / cols
    val rows = (s.h / cell).toInt() + 2
    val a = s.c.accent
    val second = mix(a, s.c.ground, 0.45f)
    val grout = s.stroke(mix(s.c.ground, s.c.ink, 0.35f), 0.26f * u).apply { strokeJoin = Paint.Join.MITER }
    val offY = (s.h - rows * cell) / 2
    for (row in 0 until rows) for (col in 0 until cols) {
        val x = col * cell + cell / 2
        val y = offY + row * cell + cell / 2
        val star = Path().also { CoverMotifs.star(it, x, y, cell * 0.5f, cell * 0.5f * 0.7654f, 8, 0f) }
        c.drawPath(star, s.fill(if ((row + col) % 2 == 0) a else second))
        c.drawPath(star, grout)
        val inner = Path().also { CoverMotifs.star(it, x, y, cell * 0.24f, cell * 0.24f * 0.5f, 8, 22.5f) }
        c.drawPath(inner, s.fill(s.c.ground))
        c.drawCircle(x, y, cell * 0.05f, s.fill(if ((row + col) % 2 == 0) second else a))
        // A small square turned on its point in the cross between four stars.
        s.diamond(x + cell / 2, y + cell / 2, cell * 0.09f, mix(s.c.ground, a, 0.5f))
    }
    Painter(c, whole(s), rnd).apply {
        grain(0.7f)
        vignette(50)
    }
    val cx = s.w / 2
    val wide = 56f * u
    val seriesF = t.series?.let { fit(caps(it), sans, 2.7f * u, 2f * u, wide, 7f * u, tracking = 0.16f, pitch = 1.4f) }
    val title = fit(t.title, didoneBlack, 9f * u, 3.6f * u, wide, 38f * u, pitch = 1.1f)!!
    val tile = RectF(cx - 34f * u, 21f * u, cx + 34f * u, 21f * u + title.height + (seriesF?.let { it.height + 3.4f * u } ?: 0f) + 12f * u)
    c.drawRect(tile, s.fill(s.c.ground).apply { setShadowLayer(1.2f * u, 0f, 0.5f * u, faint(Color.BLACK, 90)) })
    c.drawRect(tile.left + 1.6f * u, tile.top + 1.6f * u, tile.right - 1.6f * u, tile.bottom - 1.6f * u, s.stroke(a, 0.4f * u))
    var y = tile.top + 6f * u
    title.draw(c, cx - wide / 2, y, s.words(s.c.ink))
    y += title.height + 3.4f * u
    seriesF?.draw(c, cx - wide / 2, y, s.seriesWords(a))
    t.author?.let { fit(caps(it), sans, 3.5f * u, 2.3f * u, 50f * u, 8.6f * u, tracking = 0.14f, pitch = 1.4f) }?.let { f ->
        val r = RectF(cx - 30f * u, s.h - 13f * u - f.height - 6f * u, cx + 30f * u, s.h - 13f * u)
        c.drawRect(r, s.fill(s.c.ground).apply { setShadowLayer(1.2f * u, 0f, 0.5f * u, faint(Color.BLACK, 90)) })
        f.draw(c, cx - 25f * u, r.top + 3f * u, s.words(s.c.ink))
    }
}

/** A dragon's hide: scales lapped one over the next, each rimmed with light, and plates for the words. */
internal fun CoverGenerator.scales(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val gold = s.c.accent
    val r = 6.2f * u
    val stepY = r * 0.78f
    val rows = (s.h / stepY).toInt() + 3
    val pale = !isDark(s.c.ground)
    // From the foot up, so each row laps over the one below it.
    for (row in rows downTo -1) {
        val y = row * stepY
        var x = if (row % 2 == 0) 0f else r
        while (x < s.w + r) {
            val shade = rnd.nextFloat()
            val body = mix(mix(s.c.ground, if (pale) Color.WHITE else Color.BLACK, 0.2f * shade), gold, 0.03f + 0.07f * rnd.nextFloat())
            val scale = Path().apply {
                moveTo(x - r, y)
                cubicTo(x - r, y + r * 1.25f, x + r, y + r * 1.25f, x + r, y)
                close()
            }
            c.drawPath(scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, y, 0f, y + r, mix(body, Color.BLACK, if (pale) 0.1f else 0.3f), body, Shader.TileMode.CLAMP)
            })
            c.drawPath(scale, s.stroke(faint(gold, 90 + (shade * 110).toInt()), 0.22f * u))
            x += 2 * r
        }
    }
    Painter(c, whole(s), rnd).apply {
        glow(s.w * 0.35f, s.h * 0.25f, s.w * 0.9f, gold, 26)
        vignette(130)
    }
    val cx = s.w / 2
    val wide = 60f * u
    val seriesF = t.series?.let { fit(caps(it), romanLight, 2.8f * u, 2f * u, wide, 7.4f * u, tracking = 0.2f, pitch = 1.4f) }
    val title = fit(caps(t.title), romanDecorated, 8.6f * u, 3.4f * u, wide, 40f * u, pitch = 1.12f)!!
    val shieldR = RectF(cx - 36f * u, 22f * u, cx + 36f * u, 22f * u + title.height + (seriesF?.let { it.height + 3.6f * u } ?: 0f) + 13f * u)
    c.drawRoundRect(shieldR, 3f * u, 3f * u, s.fill(s.c.ground).apply { setShadowLayer(2f * u, 0f, 0.8f * u, faint(Color.BLACK, 150)) })
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = foil(s, gold); style = Paint.Style.STROKE; strokeWidth = 0.4f * u }
    c.drawRoundRect(RectF(shieldR.left + 1.4f * u, shieldR.top + 1.4f * u, shieldR.right - 1.4f * u, shieldR.bottom - 1.4f * u), 2f * u, 2f * u, line)
    var y = shieldR.top + 6.5f * u
    title.draw(c, cx - wide / 2, y, s.words(gold), sheen(s, s.words(gold)))
    y += title.height + 3.6f * u
    seriesF?.draw(c, cx - wide / 2, y, s.seriesWords(s.c.ink))
    t.author?.let { fit(caps(it), roman, 3.8f * u, 2.5f * u, 52f * u, 9f * u, tracking = 0.16f, pitch = 1.4f) }?.let { f ->
        val pr = RectF(cx - 31f * u, s.h - 13f * u - f.height - 6.4f * u, cx + 31f * u, s.h - 13f * u)
        c.drawRoundRect(pr, 2f * u, 2f * u, s.fill(s.c.ground).apply { setShadowLayer(1.6f * u, 0f, 0.6f * u, faint(Color.BLACK, 150)) })
        c.drawRoundRect(RectF(pr.left + 1f * u, pr.top + 1f * u, pr.right - 1f * u, pr.bottom - 1f * u), 1.4f * u, 1.4f * u, Paint(line).apply { strokeWidth = 0.26f * u })
        f.draw(c, cx - 26f * u, pr.top + 3.2f * u, s.words(gold), sheen(s, s.words(gold)))
    }
}

/** One emblem for the book in foil (a crown, a blade, a serpent: what suits the genre) with a wreath, stars or rays about it. */
internal fun CoverGenerator.sigil(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 130)
    val rnd = Random(art.seed)
    val gold = s.c.accent
    val leaf = leafPaint(s, s.deco(gold))
    val line = Paint(leaf).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f * u; strokeCap = Paint.Cap.ROUND }
    val cx = s.w / 2
    val took = titleIn(s, t, RectF(9f * u, 9f * u, 91f * u, 0.3f * s.h), didone, 10.5f, s.c.ink, gold, upper = true, tracking = 0.08f, pitch = 1.14f, place = -1, gap = 3.4f)
    val footTop = s.h - 20f * u
    val cy = (took.bottom + footTop) / 2
    val r = min(21f * u, (footTop - took.bottom) / 2 - 9f * u)
    if (r > 6f * u) {
        Painter(c, whole(s), rnd).glow(cx, cy, r * 2.2f, gold, 34)
        val emblem = CoverMotifs.forGenre(art.genre)[rnd.nextInt(4)]
        when (rnd.nextInt(4)) {
            0 -> {
                val wreath = Path()
                CoverMotifs.wreath(wreath, cx, cy, r * 1.36f, 16, 44f, r * 0.34f)
                c.drawPath(wreath, leaf)
            }
            1 -> for (k in 0 until 16) {
                val a = Math.toRadians(k * 22.5)
                CoverMotifs.draw(c, Motif.SPARK, cx + (cos(a) * r * 1.42f).toFloat(), cy + (sin(a) * r * 1.42f).toFloat(), (if (k % 2 == 0) 1.9f else 1.1f) * u, leaf)
            }
            2 -> for (k in 0 until 40) {
                val a = Math.toRadians(k * 9.0)
                val near = r * 1.18f
                val far = r * (if (k % 2 == 0) 1.62f else 1.4f)
                c.drawLine(cx + (cos(a) * near).toFloat(), cy + (sin(a) * near).toFloat(), cx + (cos(a) * far).toFloat(), cy + (sin(a) * far).toFloat(), line)
            }
            else -> {
                c.drawCircle(cx, cy, r * 1.3f, Paint(line).apply { strokeWidth = 0.4f * u })
                c.drawCircle(cx, cy, r * 1.22f, Paint(line).apply { strokeWidth = 0.14f * u })
                for (k in 0 until 4) {
                    val a = Math.toRadians(k * 90.0 - 90.0)
                    CoverMotifs.draw(c, Motif.SPARK, cx + (cos(a) * r * 1.3f).toFloat(), cy + (sin(a) * r * 1.3f).toFloat(), 2.2f * u, leaf, 0f)
                }
            }
        }
        CoverMotifs.draw(c, emblem, cx, cy, r, leaf)
    }
    s.rule(cx, footTop, 14f * u, 0.22f * u, s.deco(faint(gold, 200)))
    authorIn(s, t, RectF(12f * u, footTop + 2f * u, 88f * u, s.h - 7f * u), romanLight, 4.1f, s.c.ink, tracking = 0.22f)
}
