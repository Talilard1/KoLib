package dev.kobolibrary.app.ui.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Designs that belong to a kind of story: a case file, a lamp-lit street, a letter, a record sleeve, a
 * chalkboard, a front page. Every one is drawn here in the book's own colours; none copies a particular book.
 */

// ---- what these share --------------------------------------------------------------------------------------

/** Paper that shows on the cover's ground, and the ink to write on it with. */
internal fun CoverGenerator.paperAnd(s: Sheet): Pair<Int, Int> = if (isDark(s.c.ground)) s.c.ink to s.c.ground else mix(s.c.ground, Color.WHITE, 0.72f) to s.c.ink

/** [bmp] printed in inks: its darks in [shadow], its middles in [mid], its lights in [light]. */
internal fun CoverGenerator.inInks(bmp: Bitmap, shadow: Int, mid: Int, light: Int, midAt: Float = 0.55f) {
    val bw = bmp.width
    val bh = bmp.height
    val px = IntArray(bw * bh)
    bmp.getPixels(px, 0, bw, 0, 0, bw, bh)
    var lo = 255
    var hi = 0
    val lum = IntArray(px.size)
    for (i in px.indices) {
        val p = px[i]
        val v = (Color.red(p) * 54 + Color.green(p) * 183 + Color.blue(p) * 19) shr 8
        lum[i] = v
        if (v < lo) lo = v
        if (v > hi) hi = v
    }
    val lut = IntArray(256) { i ->
        val f = i / 255f
        if (f < midAt) mix(shadow, mid, f / midAt) else mix(mid, light, (f - midAt) / (1f - midAt))
    }
    val span = max(1, hi - lo)
    for (i in px.indices) px[i] = lut[((lum[i] - lo) * 255 / span).coerceIn(0, 255)] or SOLID
    bmp.setPixels(px, 0, bw, 0, 0, bw, bh)
}

/** The picture for [art], [w] × [h], on a sheet of its own. */
internal fun CoverGenerator.pictureOf(art: Art, w: Float, h: Float): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, w.toInt()), max(1, h.toInt()), Bitmap.Config.ARGB_8888)
    paint(Canvas(bmp), RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), art)
    return bmp
}

/** A stamp's paper: [r] with a row of bites out of every edge, [bite] across and [pitch] apart. */
private fun perforated(r: RectF, bite: Float, pitch: Float): Path {
    val sheet = Path().apply { addRect(r, Path.Direction.CW) }
    val holes = Path()
    val nx = max(2, (r.width() / pitch).toInt())
    val ny = max(2, (r.height() / pitch).toInt())
    for (i in 0..nx) {
        val x = r.left + r.width() * i / nx
        holes.addCircle(x, r.top, bite, Path.Direction.CW)
        holes.addCircle(x, r.bottom, bite, Path.Direction.CW)
    }
    for (j in 1 until ny) {
        val y = r.top + r.height() * j / ny
        holes.addCircle(r.left, y, bite, Path.Direction.CW)
        holes.addCircle(r.right, y, bite, Path.Direction.CW)
    }
    sheet.op(holes, Path.Op.DIFFERENCE)
    return sheet
}

/** A cog-wheel about ([cx], [cy]): [r] to the tips of its [teeth], with a hole through the middle and round ones about it. */
internal fun cog(cx: Float, cy: Float, r: Float, teeth: Int, turn: Float = 0f): Path {
    val path = Path()
    val root = r * 0.84f
    val n = teeth * 4
    for (k in 0 until n) {
        val a = Math.toRadians((turn + k * 360.0 / n))
        val rr = if (k % 4 == 1 || k % 4 == 2) r else root
        val x = cx + (cos(a) * rr).toFloat()
        val y = cy + (sin(a) * rr).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    val holes = Path()
    holes.addCircle(cx, cy, r * 0.16f, Path.Direction.CW)
    for (k in 0 until 5) {
        val a = Math.toRadians(turn + k * 72.0)
        holes.addCircle(cx + (cos(a) * r * 0.5f).toFloat(), cy + (sin(a) * r * 0.5f).toFloat(), r * 0.17f, Path.Direction.CW)
    }
    path.op(holes, Path.Op.DIFFERENCE)
    return path
}

/** Someone standing, seen against the light: [tall] high, on ([px], [py]). */
internal fun standing(path: Path, px: Float, py: Float, tall: Float) {
    val head = tall * 0.075f
    path.addCircle(px, py - tall + head, head, Path.Direction.CW)
    path.moveTo(px - head * 0.7f, py - tall + head * 1.9f)
    path.lineTo(px - head * 2.2f, py - tall + head * 3f)
    path.lineTo(px - head * 2.5f, py - tall * 0.42f)
    path.lineTo(px - head * 1.6f, py - tall * 0.42f)
    path.lineTo(px - head * 1.5f, py)
    path.lineTo(px - head * 0.2f, py)
    path.lineTo(px, py - tall * 0.4f)
    path.lineTo(px + head * 0.2f, py)
    path.lineTo(px + head * 1.5f, py)
    path.lineTo(px + head * 1.6f, py - tall * 0.42f)
    path.lineTo(px + head * 2.5f, py - tall * 0.42f)
    path.lineTo(px + head * 2.2f, py - tall + head * 3f)
    path.lineTo(px + head * 0.7f, py - tall + head * 1.9f)
    path.close()
}

/** The title flush left under the series, in plain modern type, from [top]; gives where they end. */
private fun CoverGenerator.leftHead(s: Sheet, t: Words, left: Float, top: Float, wide: Float, room: Float, largest: Float, upper: Boolean = false): Float = titleIn(
    s, t, RectF(left, top, left + wide, top + room), sansBold, largest, s.c.ink, s.c.accent, upper = upper, align = Layout.Alignment.ALIGN_NORMAL, pitch = 1.08f,
    seriesFace = sansBold, seriesSize = 2.9f, seriesTracking = 0.14f, seriesAbove = true, gap = 3.4f, place = -1, smallest = 4.2f,
).bottom

// ---- crime and suspense ------------------------------------------------------------------------------------

/** A folder from a detective's cabinet: a photograph under a paper clip, a rubber stamp, and the title typed on its label. */
internal fun CoverGenerator.caseFile(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 70)
    val (sheetC, written) = paperAnd(s)
    val dark = isDark(s.c.ground)
    // The back of the folder showing above the front, with its tab.
    val behind = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 6.5f * u)
        lineTo(54f * u, 6.5f * u)
        lineTo(58f * u, 2.2f * u)
        lineTo(93f * u, 2.2f * u)
        lineTo(97f * u, 6.5f * u)
        lineTo(s.w, 6.5f * u)
        lineTo(s.w, 0f)
        close()
    }
    c.drawPath(behind, s.fill(mix(s.c.ground, Color.BLACK, if (dark) 0.5f else 0.25f)))
    // The photograph, under a paper clip.
    val frame = pictureFrame(CoverStyle.CASE_FILE, s.w, s.h)!!
    c.save()
    c.rotate(3.2f, frame.centerX(), frame.centerY())
    c.drawRect(frame.left - 2f * u, frame.top - 2f * u, frame.right + 2f * u, frame.bottom + 2f * u, s.fill(mix(sheetC, Color.WHITE, 0.5f)).apply { setShadowLayer(1.6f * u, 0.4f * u, 0.9f * u, faint(Color.BLACK, 120)) })
    paint(c, frame, art)
    val wire = s.stroke(mix(s.c.ink, s.c.ground, 0.35f), 0.55f * u)
    val clipX = frame.left + 7f * u
    c.drawRoundRect(RectF(clipX - 2f * u, frame.top - 6f * u, clipX + 2f * u, frame.top + 7f * u), 2f * u, 2f * u, wire)
    c.drawRoundRect(RectF(clipX - 1f * u, frame.top - 4.4f * u, clipX + 1f * u, frame.top + 4.6f * u), 1f * u, 1f * u, wire)
    c.restore()
    // The series, stamped.
    t.series?.let { fit(caps(it), typewriterBold, 3.6f * u, 2.3f * u, 34f * u, 13f * u, pitch = 1.25f, tracking = 0.04f) }?.let { f ->
        val cx = 27f * u
        val cy = 0.24f * s.h
        val ink = s.seriesWords(showing(s.c.accent, s.c.ground, 3.0))
        c.save()
        c.rotate(-9f, cx, cy)
        val box = RectF(cx - f.width / 2 - 3.4f * u, cy - f.height / 2 - 3f * u, cx + f.width / 2 + 3.4f * u, cy + f.height / 2 + 3.2f * u)
        c.drawRoundRect(box, 1.4f * u, 1.4f * u, s.stroke(faint(ink, 225), 0.8f * u))
        c.drawRoundRect(RectF(box.left + 1.3f * u, box.top + 1.3f * u, box.right - 1.3f * u, box.bottom - 1.3f * u), 0.8f * u, 0.8f * u, s.stroke(faint(ink, 200), 0.24f * u))
        f.draw(c, cx - 17f * u, cy - f.height / 2, ink)
        c.restore()
    }
    // The label.
    val wide = 74f * u
    val title = fit(t.title, typewriterBold, 8.4f * u, 3.4f * u, wide, 0.2f * s.h, Layout.Alignment.ALIGN_NORMAL, pitch = 1.14f)!!
    val top = 0.52f * s.h
    val label = RectF(8f * u, top, 92f * u, top + title.height + 13.5f * u)
    c.drawRect(label, s.fill(sheetC).apply { setShadowLayer(1.2f * u, 0.3f * u, 0.6f * u, faint(Color.BLACK, 100)) })
    c.drawRect(label.left, label.top, label.right, label.top + 3.2f * u, s.fill(s.c.accent))
    title.draw(c, label.left + 5f * u, label.top + 8f * u, s.words(written))
    s.rule(s.w / 2, s.h - 22f * u, 84f * u, 0.24f * u, s.deco(faint(s.c.ink, 170)))
    authorIn(s, t, RectF(8f * u, s.h - 19f * u, 92f * u, s.h - 8f * u), typewriter, 4.2f, s.c.ink, tracking = 0.1f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** A street at night: one lamp, its light falling in a cone through the mist, and someone waiting under it. */
internal fun CoverGenerator.lamplight(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    nightSky(s, n)
    val p = Painter(c, whole(s), Random(art.seed))
    val sky = mix(n.dark, Color.BLACK, 0.5f)
    val a = n.accentOn(sky)
    val black = mix(n.dark, Color.BLACK, 0.82f)
    p.skyline(0.8f, 0.26f, mix(n.dark, Color.BLACK, 0.6f), faint(a, 110), dense = 1.3f)
    val street = 0.8f * s.h
    c.drawRect(0f, street, s.w, s.h, s.fill(mix(n.dark, Color.BLACK, 0.74f)))
    val flip = p.rnd.nextBoolean()
    c.save()
    if (flip) c.scale(-1f, 1f, s.w / 2, 0f)
    val postX = 27f * u
    val lampX = postX + 11f * u
    val lampY = 0.39f * s.h
    // The light, widening to the street.
    val cone = Path().apply {
        moveTo(lampX - 1.4f * u, lampY)
        lineTo(lampX + 1.4f * u, lampY)
        lineTo(lampX + 30f * u, street)
        lineTo(lampX - 24f * u, street)
        close()
    }
    c.drawPath(cone, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, lampY, 0f, street, faint(a, 130), faint(a, 22), Shader.TileMode.CLAMP) })
    c.drawOval(RectF(lampX - 28f * u, street - 2.4f * u, lampX + 34f * u, street + 5f * u), s.fill(faint(a, 70)))
    // The lamp: a post, an arm and a lantern.
    c.drawRect(postX - 0.7f * u, lampY - 5f * u, postX + 0.7f * u, street, s.fill(black))
    c.drawRect(postX - 1.6f * u, street - 6f * u, postX + 1.6f * u, street, s.fill(black))
    c.drawPath(Path().apply { moveTo(postX, lampY - 4.4f * u); quadTo(postX + 6f * u, lampY - 9f * u, lampX, lampY - 3f * u) }, s.stroke(black, 0.8f * u))
    c.drawPath(Path().apply { moveTo(lampX - 2.4f * u, lampY - 3f * u); lineTo(lampX + 2.4f * u, lampY - 3f * u); lineTo(lampX + 1.5f * u, lampY); lineTo(lampX - 1.5f * u, lampY); close() }, s.fill(black))
    p.glow(lampX, lampY + 0.6f * u, 17f * u, a, 235)
    c.drawCircle(lampX, lampY + 0.4f * u, 1.5f * u, s.fill(a))
    // Someone under it.
    val who = Path()
    p.inHat(who, lampX + 9f * u, street + 0.6f * u, 25f * u)
    c.drawPath(who, s.fill(black))
    c.restore()
    p.mist(0.76f, 0.22f, n.light, 46)
    // Rain, slanting.
    val rain = s.stroke(faint(n.light, 36), max(1f, 0.12f * u))
    repeat(70) {
        val x = p.rnd.nextFloat() * s.w
        val y = (0.32f + p.rnd.nextFloat() * 0.56f) * s.h
        c.drawLine(x, y, x - 1.1f * u, y + 4.2f * u, rain)
    }
    titleIn(s, t, RectF(9f * u, 9f * u, 91f * u, 0.31f * s.h), didone, 10.5f, n.light, a, upper = true, tracking = 0.07f, pitch = 1.14f, place = -1, shadow = faint(Color.BLACK, 200), shadowRadius = 1.2f)
    authorIn(s, t, RectF(10f * u, s.h - 15.5f * u, 90f * u, s.h - 5.5f * u), romanLight, 3.9f, n.light, tracking = 0.22f)
}

/** A dark room, a door open on the light, and someone standing in it, their shadow across the floor. */
internal fun CoverGenerator.doorway(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    val deep = mix(n.dark, Color.BLACK, 0.74f)
    c.drawRect(whole(s), Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.h, intArrayOf(deep, deep, mix(n.dark, Color.BLACK, 0.3f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) })
    val p = Painter(c, whole(s), Random(art.seed))
    val a = n.accentOn(deep)
    val cx = (50f + (p.rnd.nextFloat() - 0.5f) * 14f) * u
    val half = 11.5f * u
    val top = 0.35f * s.h
    val floor = 0.73f * s.h
    p.glow(cx, (top + floor) / 2, 46f * u, a, 60)
    c.drawRect(cx - half, top, cx + half, floor, Paint().apply { shader = LinearGradient(0f, top, 0f, floor, mix(a, Color.WHITE, 0.4f), a, Shader.TileMode.CLAMP) })
    // The light across the floor.
    val reach = 0.89f * s.h
    val spill = Path().apply {
        moveTo(cx - half, floor)
        lineTo(cx + half, floor)
        lineTo(cx + half + 24f * u, reach)
        lineTo(cx - half - 24f * u, reach)
        close()
    }
    c.drawPath(spill, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, floor, 0f, reach, faint(a, 150), faint(a, 0), Shader.TileMode.CLAMP) })
    val who = Path()
    val tall = (floor - top) * 0.74f
    standing(who, cx, floor, tall)
    c.drawPath(who, s.fill(deep))
    // Their shadow, long towards us.
    val shadow = Path().apply {
        moveTo(cx - tall * 0.11f, floor)
        lineTo(cx + tall * 0.11f, floor)
        lineTo(cx + tall * 0.3f, reach - 2f * u)
        lineTo(cx - tall * 0.3f, reach - 2f * u)
        close()
    }
    c.drawPath(shadow, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, floor, 0f, reach, faint(deep, 235), faint(deep, 0), Shader.TileMode.CLAMP) })
    p.grain(0.8f)
    titleIn(
        s, t, RectF(8f * u, 9f * u, 92f * u, 0.3f * s.h), condensed, 19f, n.light, a, upper = true, tracking = 0.04f, pitch = 1.0f, place = -1, smallest = 5f,
        seriesFace = sans, seriesSize = 2.8f, gap = 3.6f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 14.5f * u, 90f * u, s.h - 5.5f * u), sans, 3.8f, n.light, tracking = 0.24f)
}

/** Glass broken from one blow: cracks running out from it and round it, a few of the pieces in colour. */
internal fun CoverGenerator.shattered(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val ix = (58f + rnd.nextFloat() * 24f) * u
    val iy = (0.2f + rnd.nextFloat() * 0.12f) * s.h
    val rays = 13 + rnd.nextInt(4)
    val rings = floatArrayOf(0f, 8f, 20f, 37f, 62f, 104f, 190f)
    val angles = FloatArray(rays) { (it + (rnd.nextFloat() - 0.5f) * 0.6f) * 6.2832f / rays }
    // Where each crack crosses each ring.
    val px = Array(rays) { FloatArray(rings.size) }
    val py = Array(rays) { FloatArray(rings.size) }
    for (k in 0 until rays) for (j in rings.indices) {
        val rr = rings[j] * u * (0.82f + rnd.nextFloat() * 0.36f)
        val a = angles[k] + (rnd.nextFloat() - 0.5f) * 0.1f
        px[k][j] = ix + cos(a) * rr
        py[k][j] = iy + sin(a) * rr
    }
    // A few of the pieces that are in sight, clear of the words, are in colour.
    val inSight = ArrayList<Int>()
    for (k in 0 until rays) for (j in 2..4) {
        val k2 = (k + 1) % rays
        val mx = (px[k][j] + px[k][j + 1] + px[k2][j] + px[k2][j + 1]) / 4
        val my = (py[k][j] + py[k][j + 1] + py[k2][j] + py[k2][j + 1]) / 4
        if (mx > 6f * u && mx < s.w - 6f * u && my > 5f * u && my < 0.44f * s.h) inSight += k * 10 + j
    }
    val coloured = inSight.shuffled(rnd).take(3).toSet()
    val crack = s.stroke(s.deco(faint(s.c.ink, 150)), 0.22f * u).apply { strokeJoin = Paint.Join.ROUND }
    val glint = s.stroke(faint(Color.WHITE, if (dark) 50 else 150), 0.16f * u)
    for (k in 0 until rays) for (j in 0 until rings.size - 1) {
        val k2 = (k + 1) % rays
        val piece = Path().apply {
            moveTo(px[k][j], py[k][j])
            lineTo(px[k][j + 1], py[k][j + 1])
            lineTo(px[k2][j + 1], py[k2][j + 1])
            lineTo(px[k2][j], py[k2][j])
            close()
        }
        val tone = if (k * 10 + j in coloured) s.c.accent else mix(s.c.ground, if (rnd.nextBoolean()) Color.WHITE else Color.BLACK, rnd.nextFloat() * (if (dark) 0.1f else 0.06f))
        c.drawPath(piece, s.fill(tone))
        c.drawPath(piece, crack)
        c.drawLine(px[k][j] + 0.3f * u, py[k][j] + 0.3f * u, px[k][j + 1] + 0.3f * u, py[k][j + 1] + 0.3f * u, glint)
    }
    c.drawCircle(ix, iy, 1.2f * u, s.fill(s.c.ink))
    val took = titleIn(
        s, t, RectF(8f * u, 0.47f * s.h, 92f * u, s.h - 24f * u), sansBold, 10.5f, s.c.ink, s.c.accent, upper = true, align = Layout.Alignment.ALIGN_NORMAL, pitch = 1.06f,
        seriesFace = sansBold, seriesSize = 2.9f, seriesTracking = 0.14f, seriesAbove = true, gap = 3.4f, place = 1, outline = s.c.ground, outlineWidth = 1.5f,
    )
    s.rect(8f * u, took.top - 4.6f * u, 26f * u, took.top - 3.4f * u, s.c.accent)
    authorIn(s, t, RectF(8f * u, s.h - 19f * u, 92f * u, s.h - 8f * u), sans, 4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1, outline = s.c.ground, outlineWidth = 1.2f)
}

/** Everything printed twice, a little apart, as if seen double. */
internal fun CoverGenerator.doubleVision(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    Painter(c, whole(s), rnd).grain(0.7f)
    val ghost = s.deco(s.c.accent)
    val dx = (1.1f + rnd.nextFloat() * 0.7f) * u * (if (rnd.nextBoolean()) 1 else -1)
    val dy = 0.8f * u
    val left = 8f * u
    val wide = s.w - 16f * u
    var y = 9f * u
    t.series?.let { fit(caps(it), sansBold, 2.9f * u, 2.1f * u, wide, 7.4f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.14f, pitch = 1.4f) }?.let {
        it.draw(c, left, y, s.seriesWords(s.c.accent))
        y += it.height + 4.4f * u
    }
    val title = fit(caps(t.title), condensed, 24f * u, 6f * u, wide, 0.36f * s.h, Layout.Alignment.ALIGN_NORMAL, pitch = 0.98f)!!
    title.draw(c, left - dx, y - dy, ghost)
    title.draw(c, left + dx * 0.3f, y, s.words(s.c.ink))
    // Two rings, one over the other.
    val cy = (y + title.height + s.h - 24f * u) / 2
    val r = min(19f * u, (s.h - 24f * u - (y + title.height)) / 2 - 7f * u)
    if (r > 5f * u) {
        val cx = s.w / 2
        c.drawCircle(cx - r * 0.42f, cy, r, s.stroke(ghost, r * 0.2f))
        c.drawCircle(cx + r * 0.42f, cy, r, s.stroke(faint(s.deco(s.c.ink), 235), r * 0.2f))
    }
    t.author?.let { fit(caps(it), sansBold, 4.4f * u, 2.8f * u, wide, 11f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.12f, pitch = 1.3f) }?.let { f ->
        f.draw(c, left, s.h - 8f * u - f.height, s.words(s.c.ink))
    }
}

/** A paperback to read with the lights on: black, a picture in an oval, and the title glowing over it. */
internal fun CoverGenerator.chiller(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    val deep = mix(n.dark, Color.BLACK, 0.78f)
    c.drawRect(whole(s), Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.h, intArrayOf(deep, deep, mix(n.dark, Color.BLACK, 0.3f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) })
    val p = Painter(c, whole(s), Random(art.seed))
    val a = n.accentOn(deep)
    val frame = pictureFrame(CoverStyle.CHILLER, s.w, s.h)!!
    p.glow(frame.centerX(), frame.centerY(), frame.height() * 0.8f, a, 70)
    val oval = Path().apply { addOval(frame, Path.Direction.CW) }
    c.save()
    c.clipPath(oval)
    paint(c, frame, art)
    c.drawOval(frame, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(frame.centerX(), frame.centerY(), frame.height() / 2, intArrayOf(0, 0, faint(Color.BLACK, 190)), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    })
    c.restore()
    c.drawOval(frame, s.stroke(s.deco(a), 0.8f * u))
    c.drawOval(RectF(frame.left - 1.7f * u, frame.top - 1.7f * u, frame.right + 1.7f * u, frame.bottom + 1.7f * u), s.stroke(s.deco(faint(a, 150)), 0.2f * u))
    p.grain(0.8f)
    titleIn(
        s, t, RectF(7f * u, 8f * u, 93f * u, frame.top - 4.4f * u), didoneBlack, 13f, a, n.light, upper = true, tracking = 0.02f, pitch = 1.06f,
        seriesSize = 2.7f, gap = 2.8f, shadow = faint(a, 170), shadowRadius = 2f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 15f * u, 90f * u, s.h - 5.5f * u), roman, 4.4f, n.light, tracking = 0.2f)
}

/** A print cut in wood: the sun and its rays, hills cut with lines, pines and birds, in black and one colour. */
internal fun CoverGenerator.woodcut(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val ink = s.c.ink
    val block = RectF(9f * u, 0.34f * s.h, 91f * u, s.h - 27f * u)
    val p = Painter(c, block, rnd)
    c.save()
    c.clipRect(block)
    val sx = block.left + block.width() * (0.3f + rnd.nextFloat() * 0.4f)
    val sy = block.top + block.height() * 0.36f
    // Rays, thick and thin by turns, and the sun on them.
    for (k in 0 until 24) {
        val a0 = Math.toRadians(k * 15.0 - 2.2)
        val a1 = Math.toRadians(k * 15.0 + 2.2)
        val far = block.width() * 1.2f
        if (k % 2 == 0) c.drawPath(Path().apply {
            moveTo(sx, sy)
            lineTo(sx + (cos(a0) * far).toFloat(), sy + (sin(a0) * far).toFloat())
            lineTo(sx + (cos(a1) * far).toFloat(), sy + (sin(a1) * far).toFloat())
            close()
        }, s.fill(faint(s.c.accent, 120)))
    }
    c.drawCircle(sx, sy, 10.4f * u, s.fill(s.c.ground))
    c.drawCircle(sx, sy, 8.6f * u, s.fill(s.c.accent))
    p.birds(5, 0.7f - (sx - block.left) / block.width() * 0.4f, 0.2f, 0.3f, ink)
    // Hills, the nearer ones black with lines cut out of them, the farther cut the other way.
    for (k in 0 until 3) {
        val topAt = 0.46f + 0.17f * k
        val hill = p.ridge(topAt + 0.16f, topAt, rough = 0.36f)
        val solid = k != 1
        c.drawPath(hill, s.fill(if (solid) ink else s.c.ground))
        c.drawPath(hill, s.stroke(ink, 0.6f * u))
        c.save()
        c.clipPath(hill)
        val cut = s.stroke(if (solid) s.c.ground else ink, 0.34f * u)
        var d = -block.height()
        val lean = if (k % 2 == 0) 0.55f else -0.55f
        while (d < block.width() + block.height()) {
            c.drawLine(block.left + d, block.top, block.left + d + block.height() * lean, block.bottom, cut)
            d += (2.1f + k * 0.5f) * u
        }
        c.restore()
    }
    val pines = Path()
    repeat(7) { p.pine(pines, block.left + (0.04f + rnd.nextFloat() * 0.92f) * block.width(), block.bottom + 1f * u, (9f + rnd.nextFloat() * 9f) * u) }
    c.drawPath(pines, s.fill(ink))
    c.drawPath(pines, s.stroke(s.c.ground, 0.22f * u))
    c.restore()
    c.drawRect(block, s.stroke(s.deco(ink), 0.9f * u))
    c.drawRect(4.4f * u, 4.4f * u, s.w - 4.4f * u, s.h - 4.4f * u, s.stroke(s.deco(ink), 1.3f * u))
    c.drawRect(6.3f * u, 6.3f * u, s.w - 6.3f * u, s.h - 6.3f * u, s.stroke(s.deco(ink), 0.22f * u))
    Painter(c, whole(s), rnd).grain(0.8f)
    titleIn(
        s, t, RectF(11f * u, 11f * u, 89f * u, block.top - 4f * u), fell, 10.6f, ink, s.c.accent, upper = true, tracking = 0.06f, pitch = 1.14f,
        seriesFace = fellItalic, seriesUpper = false, seriesSize = 3.8f, seriesTracking = 0f, gap = 3f,
    )
    authorIn(s, t, RectF(11f * u, s.h - 23.5f * u, 89f * u, s.h - 9.5f * u), fellCaps, 4.6f, ink, upper = false, tracking = 0.1f)
}

// ---- love, letters and gardens -----------------------------------------------------------------------------

/** A bright cover scattered with small pictures from the story, the title in a brush hand. */
internal fun CoverGenerator.romcom(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val colours = listOf(a, hueShift(a, 150f, if (dark) 0.04f else 0.08f), mix(s.c.ground, s.c.ink, 0.3f), hueShift(a, -42f, if (dark) -0.06f else 0.12f))
    val set = CoverMotifs.forGenre(art.genre)
    val keepTitle = RectF(9f * u, 0.27f * s.h, 91f * u, 0.69f * s.h)
    val keepAuthor = RectF(12f * u, s.h - 22f * u, 88f * u, s.h - 5f * u)
    val cols = 5
    val rows = 7
    var k = rnd.nextInt(set.size)
    for (row in 0 until rows) for (col in 0 until cols) {
        val x = (col + 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f) * s.w / cols
        val y = (row + 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f) * s.h / rows
        val size = (4.4f + rnd.nextFloat() * 2.4f) * u
        val turn = (rnd.nextFloat() - 0.5f) * 50f
        k++
        if (RectF(keepTitle.left - size, keepTitle.top - size, keepTitle.right + size, keepTitle.bottom + size).contains(x, y)) continue
        if (RectF(keepAuthor.left - size, keepAuthor.top - size, keepAuthor.right + size, keepAuthor.bottom + size).contains(x, y)) continue
        CoverMotifs.draw(c, set[k % set.size], x, y, size, s.fill(colours[(k + row) % colours.size]), turn)
    }
    titleIn(
        s, t, RectF(12f * u, 0.29f * s.h, 88f * u, 0.67f * s.h), brush, 12.5f, s.c.ink, a, pitch = 1.2f,
        seriesFace = sansBold, seriesSize = 2.9f, seriesTracking = 0.16f, gap = 4f,
    )
    authorIn(s, t, RectF(14f * u, s.h - 19f * u, 86f * u, s.h - 8f * u), sansBold, 4.2f, s.c.ink, tracking = 0.14f)
}

/** A wreath of leaves and flowers, the title within it. */
internal fun CoverGenerator.wreath(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 60)
    val rnd = Random(art.seed)
    val cx = s.w / 2
    val r = min(35f * u, 0.27f * s.h)
    val cy = 10f * u + r * 1.16f
    val leafC = s.deco(mix(s.c.accent, s.c.ink, 0.5f))
    val outer = Path()
    CoverMotifs.wreath(outer, cx, cy, r, 22, 30f, r * 0.27f)
    c.drawPath(outer, s.fill(leafC))
    val innerRing = Path()
    CoverMotifs.wreath(innerRing, cx, cy, r * 0.93f, 15, 70f, r * 0.17f)
    c.drawPath(innerRing, s.fill(faint(leafC, 150)))
    // Flowers low on it, and berries along it.
    for (k in -2..2) {
        val a = Math.toRadians(90.0 + k * 21.0)
        val size = (if (k == 0) 4.6f else 3.4f - abs(k) * 0.3f) * u
        val fx = cx + (cos(a) * r).toFloat()
        val fy = cy + (sin(a) * r).toFloat()
        CoverMotifs.draw(c, Motif.FLOWER, fx, fy, size, s.fill(s.c.accent), k * 24f)
        c.drawCircle(fx, fy, size * 0.2f, s.fill(s.c.ground))
    }
    repeat(16) {
        val a = Math.toRadians((200.0 + rnd.nextFloat() * 140.0).let { d -> if (rnd.nextBoolean()) d else 540.0 - d })
        val d = r * (0.9f + rnd.nextFloat() * 0.22f)
        c.drawCircle(cx + (cos(a) * d).toFloat(), cy + (sin(a) * d).toFloat(), (0.55f + rnd.nextFloat() * 0.5f) * u, s.fill(s.c.accent))
    }
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[0], cx, cy - r, 3.6f * u, s.fill(s.c.accent))
    titleIn(
        s, t, RectF(cx - r * 0.7f, cy - r * 0.56f, cx + r * 0.7f, cy + r * 0.6f), didoneItalic, 9f, s.c.ink, s.c.accent, pitch = 1.1f, smallest = 3.2f,
        seriesFace = sans, seriesSize = 2.4f, seriesTracking = 0.18f, gap = 2.6f,
    )
    val footTop = max(cy + r * 1.2f, s.h - 21f * u)
    authorIn(s, t, RectF(12f * u, footTop, 88f * u, s.h - 8f * u), romanLight, 4.2f, s.c.ink, tracking = 0.22f)
}

/** A letter come by post: a striped edge, a stamp and its postmark, and the title written for the address. */
internal fun CoverGenerator.envelope(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    Painter(c, whole(s), rnd).grain(0.8f)
    val sheetC = paperAnd(s).first
    // The edge: slanted bars in two colours all round.
    val second = mix(s.c.ground, s.c.ink, 0.72f)
    val thick = 3.4f * u
    val pitch = 6.2f * u
    c.save()
    val rim = Path().apply {
        addRect(0f, 0f, s.w, s.h, Path.Direction.CW)
        addRect(thick, thick, s.w - thick, s.h - thick, Path.Direction.CCW)
    }
    c.clipPath(rim)
    var d = -s.h
    var k = 0
    while (d < s.w + s.h) {
        if (k % 2 == 0) c.drawPath(Path().apply {
            moveTo(d, 0f)
            lineTo(d + pitch, 0f)
            lineTo(d + pitch - s.h, s.h)
            lineTo(d - s.h, s.h)
            close()
        }, s.fill(if (k % 4 == 0) s.c.accent else second))
        d += pitch
        k++
    }
    c.restore()
    // The stamp and its postmark.
    val stamp = RectF(s.w - 31f * u, 10f * u, s.w - 11f * u, 34f * u)
    c.drawPath(perforated(stamp, 0.9f * u, 2.9f * u), s.fill(mix(sheetC, Color.WHITE, 0.4f)).apply { setShadowLayer(0.8f * u, 0.2f * u, 0.4f * u, faint(Color.BLACK, 90)) })
    c.drawRect(stamp.left + 2f * u, stamp.top + 2f * u, stamp.right - 2f * u, stamp.bottom - 2f * u, s.fill(s.c.accent))
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], stamp.centerX(), stamp.centerY(), 5.4f * u, s.fill(s.c.accentInk))
    val mark = s.stroke(s.deco(faint(s.c.ink, 150)), 0.3f * u)
    val mx = stamp.left - 6f * u
    val my = stamp.centerY() + 1f * u
    c.drawCircle(mx, my, 7.6f * u, mark)
    c.drawCircle(mx, my, 5.6f * u, Paint(mark).apply { strokeWidth = 0.18f * u })
    for (i in -2..2) {
        val wave = Path()
        val y0 = my + i * 2.3f * u
        wave.moveTo(mx + 8.4f * u, y0)
        var x = mx + 8.4f * u
        var up = true
        while (x < stamp.right - 1f * u) {
            wave.quadTo(x + 1.8f * u, y0 + (if (up) -1.2f else 1.2f) * u, x + 3.6f * u, y0)
            x += 3.6f * u
            up = !up
        }
        c.drawPath(wave, mark)
    }
    titleIn(
        s, t, RectF(12f * u, 0.36f * s.h, 88f * u, 0.7f * s.h), script, 17f, s.c.ink, s.c.accent, pitch = 1.05f, smallest = 6f,
        seriesFace = typewriter, seriesSize = 3f, seriesTracking = 0.06f, gap = 8f,
    )
    authorIn(s, t, RectF(12f * u, s.h - 21f * u, 88f * u, s.h - 9f * u), typewriter, 4f, s.c.ink, tracking = 0.1f)
}

/** A study of a plant, as in an old flora: stem, leaves and flowers set out on the page. */
internal fun CoverGenerator.botanical(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    Painter(c, whole(s), rnd).apply {
        grain(0.6f)
        vignette(if (isDark(s.c.ground)) 70 else 24)
    }
    val rule = s.stroke(s.deco(faint(s.c.ink, 170)), 0.24f * u)
    c.drawRect(5f * u, 5f * u, s.w - 5f * u, s.h - 5f * u, rule)
    val took = titleIn(
        s, t, RectF(12f * u, 12f * u, 88f * u, 0.3f * s.h), garamond, 9.6f, s.c.ink, s.c.accent, upper = true, tracking = 0.1f, pitch = 1.16f, place = -1,
        seriesFace = garamondItalic, seriesUpper = false, seriesSize = 4f, seriesTracking = 0f, gap = 3.4f,
    )
    val cx = s.w / 2
    val foot = s.h - 26f * u
    val head = max(took.bottom + 9f * u, 0.3f * s.h)
    val leafC = mix(s.c.ink, s.c.ground, 0.28f)
    val green = s.fill(leafC)
    if (foot - head > 20f * u) {
        val lean = (rnd.nextFloat() - 0.5f) * 10f * u
        val plant = Path()
        frond(plant, cx, foot, cx + lean, head + 8f * u, 0.06f, 7, 9.6f * u, 0.36f * u)
        // Side stems, each to a flower or a bud.
        val tips = ArrayList<FloatArray>()
        for (side in listOf(-1f, 1f)) for (k in 0 until 2) {
            val fromY = foot - (foot - head) * (0.12f + 0.26f * k)
            val tx = cx + side * (17f + rnd.nextFloat() * 8f + k * 5f) * u
            val ty = fromY - (foot - head) * (0.3f + rnd.nextFloat() * 0.12f)
            frond(plant, cx, fromY, tx, ty, side * 0.2f, 4, 6.4f * u, 0.3f * u)
            tips += floatArrayOf(tx, ty, side)
        }
        c.drawPath(plant, green)
        for ((i, tip) in tips.withIndex()) {
            if (i % 2 == 0) {
                CoverMotifs.draw(c, Motif.FLOWER, tip[0], tip[1] - 1.6f * u, 4.2f * u, s.fill(s.c.accent), rnd.nextFloat() * 40f)
                c.drawCircle(tip[0], tip[1] - 1.6f * u, 0.9f * u, s.fill(s.c.ground))
            } else CoverMotifs.draw(c, Motif.DROP, tip[0], tip[1] - 1.4f * u, 2.6f * u, s.fill(s.c.accent), tip[2] * 12f)
        }
        CoverMotifs.draw(c, Motif.FLOWER, cx + lean, head + 5.4f * u, 5.6f * u, s.fill(s.c.accent))
        c.drawCircle(cx + lean, head + 5.4f * u, 1.2f * u, s.fill(s.c.ground))
        s.rule(cx, foot + 1.6f * u, 26f * u, 0.24f * u, s.deco(faint(s.c.ink, 170)))
    }
    authorIn(s, t, RectF(12f * u, s.h - 21.5f * u, 88f * u, s.h - 10f * u), garamond, 4.6f, s.c.ink, tracking = 0.16f)
}

// ---- landscapes in paper, ink and wool ---------------------------------------------------------------------

/** Hills cut from sheets of paper and laid one over the next, each casting its shadow, under a paper sun. */
internal fun CoverGenerator.paperLayers(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val dark = isDark(s.c.ground)
    val first = mix(s.c.ground, s.c.accent, 0.5f)
    val deepest = if (dark) mix(s.c.ground, Color.BLACK, 0.62f) else mix(s.c.ground, s.c.ink, 0.9f)
    fun cut(colour: Int) = s.fill(colour).apply { setShadowLayer(1.8f * u, 0f, -0.7f * u, faint(Color.BLACK, if (dark) 150 else 70)) }
    c.drawCircle((26f + rnd.nextFloat() * 48f) * u, 0.37f * s.h, 10.5f * u, s.fill(s.c.accent).apply { setShadowLayer(1.6f * u, 0.3f * u, 0.7f * u, faint(Color.BLACK, if (dark) 150 else 70)) })
    val n = 6
    for (k in 0 until n) {
        val top = 0.43f + 0.07f * k
        c.drawPath(p.ridge(top + 0.09f, top, rough = 0.44f), cut(mix(first, deepest, k / (n - 1f))))
    }
    p.grain(0.8f)
    titleIn(
        s, t, RectF(9f * u, 9f * u, 91f * u, 0.27f * s.h), didoneBlack, 11.5f, s.c.ink, s.c.accent, pitch = 1.06f, place = -1,
        seriesFace = sansBold, seriesSize = 2.8f, seriesTracking = 0.16f, gap = 3.4f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 15.5f * u, 90f * u, s.h - 5.5f * u), sansBold, 4f, if (dark) s.c.ink else s.c.ground, tracking = 0.18f)
}

/** The sea in the old pattern of arcs within arcs, and the sun going down into it. */
internal fun CoverGenerator.waves(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val top = 0.5f * s.h
    c.drawCircle((30f + rnd.nextFloat() * 40f) * u, top + 1f * u, 15f * u, s.fill(s.c.accent))
    val line = s.stroke(s.deco(mix(s.c.ground, s.c.ink, 0.7f)), 0.32f * u)
    val band = s.stroke(s.c.accent, 0f)
    val r = 100f * u / 11f
    c.save()
    c.clipRect(0f, top, s.w, s.h)
    var row = 0
    var cy = top + r
    while (cy - r < s.h) {
        var cx = if (row % 2 == 0) 0f else r
        while (cx - r < s.w) {
            c.drawCircle(cx, cy, r, s.fill(s.c.ground))
            c.drawCircle(cx, cy, r * 0.69f, Paint(band).apply { strokeWidth = r * 0.12f })
            c.drawCircle(cx, cy, r, line)
            c.drawCircle(cx, cy, r * 0.86f, line)
            c.drawCircle(cx, cy, r * 0.53f, line)
            cx += 2 * r
        }
        cy += r * 0.5f
        row++
    }
    c.restore()
    Painter(c, whole(s), rnd).grain(0.7f)
    titleIn(s, t, RectF(9f * u, 10f * u, 91f * u, 0.34f * s.h), didone, 10.4f, s.c.ink, s.c.accent, upper = true, tracking = 0.07f, pitch = 1.14f, place = -1, gap = 3.4f)
    t.author?.let { fit(caps(it), romanLight, 4f * u, 2.6f * u, 60f * u, 9f * u, tracking = 0.2f, pitch = 1.4f) }?.let { f ->
        val plateR = RectF(s.w / 2 - f.width / 2 - 6f * u, s.h - 11f * u - f.height - 6f * u, s.w / 2 + f.width / 2 + 6f * u, s.h - 11f * u)
        c.drawRect(plateR, s.fill(s.c.ground))
        c.drawRect(plateR, s.stroke(s.deco(mix(s.c.ground, s.c.ink, 0.7f)), 0.3f * u))
        f.draw(c, s.w / 2 - 30f * u, plateR.top + 3f * u, s.words(s.c.ink))
    }
}

/** Mountains in washes of ink, fading into mist, with a tree, a red sun and a seal. */
internal fun CoverGenerator.inkWash(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    val ink = s.c.ink
    val flip = rnd.nextBoolean()
    c.drawCircle((if (flip) 24f + rnd.nextFloat() * 12f else 64f + rnd.nextFloat() * 12f) * u, 0.31f * s.h, 7.4f * u, s.fill(s.c.accent))
    for (k in 0 until 4) {
        val top = 0.37f + 0.11f * k
        val hill = p.ridge(top + 0.13f, top, rough = 0.56f - 0.05f * k)
        p.layer(hill, faint(ink, 50 + 46 * k), faint(ink, 0), top - 0.02f, top + 0.24f)
    }
    p.mist(0.84f, 0.2f, s.c.ground, 220)
    // A tree leaning in from the side.
    val tx = (if (flip) 80f else 20f) * u
    p.bareTree(tx, 0.83f * s.h, 15f * u, faint(ink, 235), twist = 0.5f, depth = 7, angle = if (flip) -104f else -76f)
    c.drawOval(RectF(tx - 13f * u, 0.82f * s.h, tx + 13f * u, 0.86f * s.h), s.fill(faint(ink, 200)))
    p.birds(4, if (flip) 0.66f else 0.34f, 0.355f, 0.12f, faint(ink, 200))
    p.grain(0.9f)
    val align = if (flip) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
    titleIn(
        s, t, RectF(9f * u, 9f * u, 91f * u, 0.27f * s.h), romanLight, 9f, ink, s.c.accent, upper = true, tracking = 0.14f, pitch = 1.2f, place = -1, align = align,
        seriesFace = serifItalic, seriesUpper = false, seriesSize = 3.6f, seriesTracking = 0f, gap = 3.2f,
    )
    // The seal, and the name beside it.
    val sealX = if (flip) 9f * u else s.w - 18f * u
    val seal = RectF(sealX, s.h - 18f * u, sealX + 9f * u, s.h - 9f * u)
    c.drawRoundRect(seal, 1f * u, 1f * u, s.fill(s.c.accent))
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], seal.centerX(), seal.centerY(), 3f * u, s.fill(s.c.accentInk))
    val box = if (flip) RectF(22f * u, s.h - 19f * u, 91f * u, s.h - 8f * u) else RectF(9f * u, s.h - 19f * u, s.w - 22f * u, s.h - 8f * u)
    authorIn(s, t, box, romanLight, 3.9f, ink, tracking = 0.2f, align = if (flip) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
}

/** Woven plaid, with the title on a label sewn to it. */
internal fun CoverGenerator.tartan(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val a = s.c.accent
    val deep = mix(s.c.ground, Color.BLACK, 0.5f)
    val period = (30f + rnd.nextInt(3) * 4f) * u
    val broad = (6f + rnd.nextFloat() * 2.4f) * u
    // The sett: one broad band, and fine lines either side of it and between.
    val bands = listOf(
        Triple(0f, broad, faint(a, 150)), Triple(broad + 1.6f * u, 0.9f * u, faint(deep, 150)), Triple(period * 0.56f, 0.7f * u, faint(s.c.ink, 150)),
        Triple(period * 0.56f + 2f * u, 0.7f * u, faint(s.c.ink, 150)), Triple(period * 0.8f, 1.6f * u, faint(deep, 130)),
    )
    for (upright in listOf(true, false)) {
        var at = -period * rnd.nextFloat()
        val end = if (upright) s.w else s.h
        while (at < end) {
            for ((from, wide, colour) in bands) {
                if (upright) c.drawRect(at + from, 0f, at + from + wide, s.h, s.fill(colour)) else c.drawRect(0f, at + from, s.w, at + from + wide, s.fill(colour))
            }
            at += period
        }
    }
    // The twill: fine slanting threads.
    val thread = s.stroke(faint(Color.BLACK, 16), max(1f, 0.14f * u))
    var d = -s.h
    while (d < s.w) {
        c.drawLine(d, 0f, d + s.h, s.h, thread)
        d += 0.9f * u
    }
    Painter(c, whole(s), rnd).vignette(60)
    val cx = s.w / 2
    val wide = 56f * u
    val seriesF = t.series?.let { fit(caps(it), sans, 2.7f * u, 2f * u, wide, 7f * u, tracking = 0.16f, pitch = 1.4f) }
    val title = fit(t.title, serifBold, 8.8f * u, 3.6f * u, wide, 38f * u, pitch = 1.1f)!!
    val label = RectF(cx - 34f * u, 21f * u, cx + 34f * u, 21f * u + title.height + (seriesF?.let { it.height + 3.4f * u } ?: 0f) + 12f * u)
    c.drawRect(label, s.fill(s.c.ground).apply { setShadowLayer(1.2f * u, 0f, 0.5f * u, faint(Color.BLACK, 110)) })
    // Stitched round.
    c.drawRect(label.left + 1.6f * u, label.top + 1.6f * u, label.right - 1.6f * u, label.bottom - 1.6f * u, s.stroke(s.deco(faint(s.c.ink, 170)), 0.3f * u).apply {
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(1.6f * u, 1f * u), 0f)
    })
    var y = label.top + 6f * u
    title.draw(c, cx - wide / 2, y, s.words(s.c.ink))
    y += title.height + 3.4f * u
    seriesF?.draw(c, cx - wide / 2, y, s.seriesWords(a))
    t.author?.let { fit(caps(it), sans, 3.5f * u, 2.3f * u, 50f * u, 8.6f * u, tracking = 0.14f, pitch = 1.4f) }?.let { f ->
        val r = RectF(cx - 30f * u, s.h - 13f * u - f.height - 6f * u, cx + 30f * u, s.h - 13f * u)
        c.drawRect(r, s.fill(s.c.ground).apply { setShadowLayer(1.2f * u, 0f, 0.5f * u, faint(Color.BLACK, 110)) })
        f.draw(c, cx - 25f * u, r.top + 3f * u, s.words(s.c.ink))
    }
}

// ---- pictures in a frame of their own ----------------------------------------------------------------------

/** A postage stamp, its edge perforated, with the picture on it and a postmark across the corner. */
internal fun CoverGenerator.stamp(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 60)
    val (sheetC, written) = paperAnd(s)
    val frame = pictureFrame(CoverStyle.STAMP, s.w, s.h)!!
    val sheet = RectF(frame.left - 6f * u, frame.top - 6f * u, frame.right + 6f * u, frame.bottom + 6f * u)
    c.drawPath(perforated(sheet, 1.25f * u, 3.7f * u), s.fill(mix(sheetC, Color.WHITE, 0.3f)).apply { setShadowLayer(1.6f * u, 0.4f * u, 0.9f * u, faint(Color.BLACK, 120)) })
    c.drawRect(frame.left - 2f * u, frame.top - 2f * u, frame.right + 2f * u, frame.bottom + 2f * u, s.fill(s.c.accent))
    paint(c, frame, art)
    c.drawRect(frame, s.stroke(faint(written, 150), 0.24f * u))
    // What it's worth: the book's number in its series.
    val number = t.seriesIndex?.toInt()?.takeIf { it.toDouble() == t.seriesIndex && it in 0..999 }?.toString()
    if (number != null) {
        val tab = RectF(frame.left, frame.top, frame.left + 11f * u + (number.length - 1) * 3.4f * u, frame.top + 10f * u)
        c.drawRect(tab, s.fill(mix(sheetC, Color.WHITE, 0.3f)))
        fit(number, didoneBlack, 7f * u, 4f * u, tab.width(), 8f * u)?.draw(c, tab.left, tab.top + 2.2f * u, s.seriesWords(showing(s.c.accent, sheetC, 3.0)))
    }
    // The postmark, across the corner and off the stamp.
    val mark = s.stroke(s.deco(faint(s.c.ink, 170)), 0.34f * u)
    val mx = sheet.right - 3f * u
    val my = sheet.bottom - 4f * u
    c.drawCircle(mx, my, 9f * u, mark)
    c.drawCircle(mx, my, 6.8f * u, Paint(mark).apply { strokeWidth = 0.2f * u })
    CoverMotifs.draw(c, Motif.STAR, mx, my, 2.4f * u, s.fill(s.deco(faint(s.c.ink, 170))))
    for (i in -2..2) {
        val wave = Path()
        val y0 = my + i * 2.4f * u
        var x = mx - 10f * u
        wave.moveTo(x, y0)
        var up = true
        while (x > sheet.left + 14f * u) {
            wave.quadTo(x - 2f * u, y0 + (if (up) -1.3f else 1.3f) * u, x - 4f * u, y0)
            x -= 4f * u
            up = !up
        }
        c.drawPath(wave, mark)
    }
    titleIn(
        s, t, RectF(9f * u, sheet.bottom + 6f * u, 91f * u, s.h - 21f * u), didone, 8.6f, s.c.ink, s.c.accent, upper = true, tracking = 0.06f, pitch = 1.14f,
        seriesSize = 2.7f, gap = 2.8f,
    )
    authorIn(s, t, RectF(12f * u, s.h - 17.5f * u, 88f * u, s.h - 7f * u), romanLight, 3.9f, s.c.ink, tracking = 0.22f)
}

/** The picture wide across the cover like a still from a film, the title spaced out under it. */
internal fun CoverGenerator.widescreen(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 110)
    val frame = pictureFrame(CoverStyle.WIDESCREEN, s.w, s.h)!!
    paint(c, frame, art)
    val line = s.deco(s.c.accent)
    s.rect(0f, frame.top - 1.5f * u, s.w, frame.top - 1f * u, line)
    s.rect(0f, frame.bottom + 1f * u, s.w, frame.bottom + 1.5f * u, line)
    val cx = s.w / 2
    val seriesF = t.series?.let { fit(caps(it), sans, 3f * u, 2.1f * u, 80f * u, frame.top - 12f * u, tracking = 0.3f, pitch = 1.5f) }
    if (seriesF != null) seriesF.draw(c, cx - 40f * u, (frame.top - 1.5f * u - seriesF.height) / 2 + 1f * u, s.seriesWords(s.c.accent))
    else for (k in -1..1) CoverMotifs.draw(c, Motif.STAR, cx + k * 5f * u, frame.top / 2, 1.5f * u, s.fill(line))
    titleIn(s, t, RectF(8f * u, frame.bottom + 7f * u, 92f * u, s.h - 23f * u), condensed, 15f, s.c.ink, s.c.ink, upper = true, tracking = 0.14f, pitch = 1.08f, series = false, smallest = 4.4f)
    s.rule(cx, s.h - 20f * u, 12f * u, 0.3f * u, line)
    authorIn(s, t, RectF(10f * u, s.h - 17.5f * u, 90f * u, s.h - 7f * u), sans, 3.7f, s.c.ink, tracking = 0.34f)
}

/** The picture seen through a keyhole. */
internal fun CoverGenerator.keyhole(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val frame = pictureFrame(CoverStyle.KEYHOLE, s.w, s.h)!!
    val cx = frame.centerX()
    val r = frame.width() / 2
    val cy = frame.top + r
    val hole = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
    val skirt = Path().apply {
        moveTo(cx - r * 0.4f, cy + r * 0.6f)
        lineTo(cx - r * 0.82f, frame.bottom)
        lineTo(cx + r * 0.82f, frame.bottom)
        lineTo(cx + r * 0.4f, cy + r * 0.6f)
        close()
    }
    hole.op(skirt, Path.Op.UNION)
    Painter(c, whole(s), Random(art.seed)).glow(cx, frame.centerY(), frame.height() * 0.8f, s.c.accent, 40)
    c.save()
    c.clipPath(hole)
    paint(c, frame, art)
    c.drawPath(hole, s.stroke(faint(Color.BLACK, 130), 3.4f * u))
    c.restore()
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = foil(s, s.deco(s.c.accent)); style = Paint.Style.STROKE; strokeWidth = 0.8f * u; strokeJoin = Paint.Join.ROUND }
    c.drawPath(hole, rim)
    // The plate about it, and its screws.
    val plateR = RectF(frame.left - 7f * u, frame.top - 7f * u, frame.right + 7f * u, frame.bottom + 7f * u)
    c.drawRoundRect(plateR, 9f * u, 9f * u, Paint(rim).apply { strokeWidth = 0.3f * u })
    for (y in listOf(plateR.top + 3.6f * u, plateR.bottom - 3.6f * u)) c.drawCircle(cx, y, 1f * u, Paint(rim).apply { style = Paint.Style.FILL })
    titleIn(s, t, RectF(9f * u, 9f * u, 91f * u, plateR.top - 4f * u), didone, 10f, s.c.ink, s.c.accent, upper = true, tracking = 0.07f, pitch = 1.14f, seriesSize = 2.7f, gap = 2.8f)
    authorIn(s, t, RectF(12f * u, max(plateR.bottom + 3f * u, s.h - 18f * u), 88f * u, s.h - 6f * u), romanLight, 3.9f, s.c.ink, tracking = 0.22f)
}

/** The front page of a newspaper: the series for its name, the title for the headline, a picture, and columns of print. */
internal fun CoverGenerator.newsprint(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val ink = s.c.ink
    val l = 7f * u
    val r = s.w - 7f * u
    val cx = s.w / 2
    val rule = s.deco(ink)
    s.rule(cx, 7f * u, r - l, 0.7f * u, rule)
    // The paper's name.
    val name = t.series?.let { fit(it, blackLetter, 8.4f * u, 3.6f * u, r - l, 10f * u, pitch = 1.0f) }
    if (name != null) name.draw(c, l, 10f * u + (10f * u - name.height) / 2, s.seriesWords(ink))
    else for (k in -2..2) CoverMotifs.draw(c, if (k == 0) CoverMotifs.forGenre(art.genre)[0] else Motif.STAR, cx + k * 7f * u, 15f * u, (if (k == 0) 3.6f else 1.5f) * u, s.fill(rule))
    s.rect(l, 22.2f * u, r, 25f * u, s.c.accent)
    for (k in -9..9) if (k % 3 != 0) CoverMotifs.draw(c, Motif.SPARK, cx + k * 4.4f * u, 23.6f * u, 0.7f * u, s.fill(s.c.accentInk))
    for (k in -3..3) CoverMotifs.draw(c, Motif.STAR, cx + k * 13.2f * u, 23.6f * u, 1f * u, s.fill(s.c.accentInk))
    val frame = pictureFrame(CoverStyle.NEWSPRINT, s.w, s.h)!!
    titleIn(s, t, RectF(l, 27.5f * u, r, frame.top - 4f * u), didoneBlack, 12f, ink, ink, upper = true, pitch = 1.04f, series = false, smallest = 4f)
    // The picture, printed grey in dots.
    val picture = pictureOf(art, frame.width(), frame.height())
    inInks(picture, ink, mix(ink, s.c.ground, 0.55f), s.c.ground)
    c.drawBitmap(picture, null, frame, Paint(Paint.FILTER_BITMAP_FLAG))
    c.save()
    c.clipRect(frame)
    halftone(c, frame, u)
    c.restore()
    c.drawRect(frame, s.stroke(rule, 0.24f * u))
    // Columns of print: lines, with a short one where a paragraph ends.
    val print = s.fill(faint(ink, 90))
    fun column(left: Float, right: Float, top: Float, bottom: Float) {
        var y = top
        while (y < bottom) {
            val short = rnd.nextInt(7) == 0
            c.drawRect(left, y, if (short) left + (right - left) * (0.3f + rnd.nextFloat() * 0.4f) else right, y + 0.5f * u, print)
            y += if (short) 3.1f * u else 1.7f * u
        }
    }
    val foot = s.h - 22.5f * u
    column(frame.right + 3f * u, r, frame.top, frame.bottom)
    val gutter = 3f * u
    val wideEach = (r - l - 2 * gutter) / 3
    for (k in 0 until 3) column(l + k * (wideEach + gutter), l + k * (wideEach + gutter) + wideEach, frame.bottom + 4f * u, foot - 2f * u)
    Painter(c, whole(s), rnd).apply {
        grain(1.2f)
        vignette(if (isDark(s.c.ground)) 90 else 36)
    }
    s.rule(cx, foot, r - l, 0.24f * u, rule)
    s.rule(cx, s.h - 7f * u, r - l, 0.7f * u, rule)
    authorIn(s, t, RectF(l, foot + 2f * u, r, s.h - 9.5f * u), sansBold, 4.2f, ink, tracking = 0.16f)
}

// ---- music, sport and school -------------------------------------------------------------------------------

/** A record: the black disc with its grooves and a label, over plain type. */
internal fun CoverGenerator.record(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val r = min(38f * u, 0.29f * s.h)
    val cx = s.w / 2 + (rnd.nextFloat() - 0.5f) * 10f * u
    val cy = 8f * u + r
    val vinyl = mix(if (dark) s.c.ground else s.c.ink, Color.BLACK, 0.6f)
    c.drawCircle(cx, cy, r, s.fill(vinyl).apply { setShadowLayer(2f * u, 0.5f * u, 1f * u, faint(Color.BLACK, 130)) })
    if (dark) c.drawCircle(cx, cy, r, s.stroke(faint(s.c.ink, 70), 0.2f * u))
    val groove = s.stroke(faint(Color.WHITE, 22), max(1f, 0.1f * u))
    var gr = r * 0.44f
    while (gr < r * 0.97f) {
        c.drawCircle(cx, cy, gr, groove)
        gr += (0.7f + rnd.nextFloat() * 0.9f) * u
    }
    // Where the light catches it.
    val oval = RectF(cx - r * 0.96f, cy - r * 0.96f, cx + r * 0.96f, cy + r * 0.96f)
    val turn = rnd.nextFloat() * 60f
    c.drawArc(oval, -70f + turn, 26f, true, s.fill(faint(Color.WHITE, 26)))
    c.drawArc(oval, 110f + turn, 26f, true, s.fill(faint(Color.WHITE, 26)))
    c.drawCircle(cx, cy, r * 0.37f, s.fill(s.c.accent))
    c.drawCircle(cx, cy, r * 0.33f, s.stroke(faint(s.c.accentInk, 120), 0.18f * u))
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy - r * 0.19f, r * 0.1f, s.fill(s.c.accentInk))
    c.drawCircle(cx, cy, 0.9f * u, s.fill(s.c.ground))
    Painter(c, whole(s), rnd).grain(0.6f)
    val top = cy + r + 7f * u
    leftHead(s, t, 8f * u, top, 84f * u, s.h - 23f * u - top, 9.6f)
    authorIn(s, t, RectF(8f * u, s.h - 19f * u, 92f * u, s.h - 8f * u), sans, 4f, s.c.ink, tracking = 0.12f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

/** A team's colours: the book's number as on a jersey, stripes either side, and block lettering. */
internal fun CoverGenerator.varsity(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    Painter(c, whole(s), rnd).grain(0.9f)
    val cx = s.w / 2
    s.rect(0f, 7f * u, s.w, 10.4f * u, s.c.accent)
    s.rect(0f, 11.6f * u, s.w, 12.3f * u, s.c.accent)
    val took = titleIn(
        s, t, RectF(8f * u, 18f * u, 92f * u, 0.37f * s.h), college, 10f, s.c.ink, s.c.accent, upper = true, pitch = 1.12f, place = -1,
        seriesFace = sansBold, seriesSize = 2.8f, seriesTracking = 0.16f, gap = 3.4f,
    )
    // The number: the book's place in its series, or the title's first letter.
    val number = t.seriesIndex?.toInt()?.takeIf { it.toDouble() == t.seriesIndex && it in 0..99 }?.toString()
        ?: caps(t.title).firstOrNull { it.isLetterOrDigit() }?.toString() ?: "1"
    val footTop = s.h - 24f * u
    val room = footTop - took.bottom - 10f * u
    if (room > 12f * u) {
        val tall = min(40f * u, room)
        val top = took.bottom + 5f * u + (room - tall) / 2
        val figure = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = college
            textAlign = Paint.Align.CENTER
            textSize = 100f
        }
        val bounds = Rect()
        figure.getTextBounds(number, 0, number.length, bounds)
        figure.textSize = 100f * tall / max(1, -bounds.top)
        val wide = figure.measureText(number)
        val base = top + tall
        // Stripes either side of it.
        for (k in -1..1) {
            val y = top + tall / 2 + k * 4.4f * u
            s.rect(0f, y - 1.3f * u, cx - wide / 2 - 5f * u, y + 1.3f * u, s.c.accent)
            s.rect(cx + wide / 2 + 5f * u, y - 1.3f * u, s.w, y + 1.3f * u, s.c.accent)
        }
        c.drawText(number, cx + 1.1f * u, base + 1.1f * u, Paint(figure).apply { color = mix(s.c.ground, Color.BLACK, 0.5f) })
        c.drawText(number, cx, base, Paint(figure).apply { color = s.c.ink; style = Paint.Style.STROKE; strokeWidth = 2.4f * u; strokeJoin = Paint.Join.ROUND })
        c.drawText(number, cx, base, Paint(figure).apply { color = s.seriesWords(s.c.accent) })
    }
    s.rect(0f, s.h - 21f * u, s.w, s.h - 7f * u, s.c.accent)
    authorIn(s, t, RectF(8f * u, s.h - 19f * u, 92f * u, s.h - 9f * u), college, 4.6f, s.c.accentInk, tracking = 0.08f)
}

/** A comic: rays and print dots, the title bursting out of a star, the book's number in the corner box. */
internal fun CoverGenerator.comicBook(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val (sheetC, written) = paperAnd(s)
    val cx = s.w / 2
    val by = 0.4f * s.h
    // Rays from behind the title.
    val ray = s.fill(mix(s.c.ground, if (dark) Color.WHITE else Color.BLACK, 0.07f))
    for (k in 0 until 18) {
        val a0 = Math.toRadians(k * 20.0)
        val a1 = Math.toRadians(k * 20.0 + 10.0)
        val far = s.h * 1.2f
        c.drawPath(Path().apply {
            moveTo(cx, by)
            lineTo(cx + (cos(a0) * far).toFloat(), by + (sin(a0) * far).toFloat())
            lineTo(cx + (cos(a1) * far).toFloat(), by + (sin(a1) * far).toFloat())
            close()
        }, ray)
    }
    c.save()
    c.clipRect(0f, 0.62f * s.h, s.w, s.h)
    halftone(c, whole(s), u)
    c.restore()
    // The burst, and the title on it.
    val outline = if (dark) mix(s.c.ground, Color.BLACK, 0.6f) else s.c.ink
    val burst = Path()
    val points = 16
    for (k in 0 until points * 2) {
        val a = Math.toRadians(k * 180.0 / points - 90.0)
        val far = if (k % 2 == 0) 1f + rnd.nextFloat() * 0.1f else 0.74f + rnd.nextFloat() * 0.06f
        val x = cx + (cos(a) * 47f * u * far).toFloat()
        val y = by + (sin(a) * 0.25f * s.h * far).toFloat()
        if (k == 0) burst.moveTo(x, y) else burst.lineTo(x, y)
    }
    burst.close()
    c.drawPath(burst, s.fill(outline).apply { setShadowLayer(0f, 1.4f * u, 1.6f * u, outline) })
    c.drawPath(burst, s.fill(s.c.accent))
    c.drawPath(burst, s.stroke(outline, 0.8f * u).apply { strokeJoin = Paint.Join.MITER })
    c.save()
    c.rotate(-5f, cx, by)
    titleIn(s, t, RectF(cx - 30f * u, by - 0.125f * s.h, cx + 30f * u, by + 0.125f * s.h), comic, 15f, s.c.accentInk, s.c.accentInk, upper = true, pitch = 1.04f, series = false, smallest = 3.6f)
    c.restore()
    // The corner box, with the book's number, and the series across the head.
    val number = t.seriesIndex?.toInt()?.takeIf { it.toDouble() == t.seriesIndex && it in 0..999 }?.toString()
    val corner = RectF(6f * u, 6f * u, 22f * u, 25f * u)
    c.drawRect(corner, s.fill(sheetC))
    c.drawRect(corner, s.stroke(outline, 0.6f * u))
    val emblem = CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)]
    if (number != null) {
        fit(number, comic, 9f * u, 5f * u, 14f * u, 10f * u)?.draw(c, corner.left + 1f * u, corner.top + 2.4f * u, s.seriesWords(showing(s.c.accent, sheetC, 3.0)))
        CoverMotifs.draw(c, emblem, corner.centerX(), corner.bottom - 4.6f * u, 2.8f * u, s.fill(written))
    } else CoverMotifs.draw(c, emblem, corner.centerX(), corner.centerY(), 5.4f * u, s.fill(written))
    (t.seriesName ?: t.series)?.let { fit(caps(it), comic, 5.4f * u, 3f * u, 66f * u, 12f * u, Layout.Alignment.ALIGN_NORMAL, pitch = 1.1f, tracking = 0.04f) }?.let { f ->
        f.draw(c, 26f * u, 8f * u, s.seriesWords(s.c.ink))
    }
    // The emblem, big, under the burst.
    CoverMotifs.draw(c, emblem, cx, 0.74f * s.h, 9f * u, s.fill(outline))
    CoverMotifs.draw(c, emblem, cx - 0.8f * u, 0.74f * s.h - 0.8f * u, 9f * u, s.fill(s.c.accent))
    // The author, in a caption box.
    t.author?.let { fit(caps(it), comic, 5.2f * u, 3f * u, 70f * u, 9f * u, tracking = 0.05f, pitch = 1.2f) }?.let { f ->
        val box = RectF(cx - f.width / 2 - 5f * u, s.h - 9f * u - f.height - 6f * u, cx + f.width / 2 + 5f * u, s.h - 9f * u)
        c.drawRect(RectF(box.left + 1.1f * u, box.top + 1.1f * u, box.right + 1.1f * u, box.bottom + 1.1f * u), s.fill(outline))
        c.drawRect(box, s.fill(sheetC))
        c.drawRect(box, s.stroke(outline, 0.6f * u))
        f.draw(c, cx - 35f * u, box.top + 3f * u, s.words(written))
    }
}

/** A blackboard in its wooden frame, the title chalked on it among drawings. */
internal fun CoverGenerator.chalkboard(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    val n = night(s)
    // (A board in the theme's second colour where that's a dark one, otherwise in its dark.)
    val slate = if (androidx.core.graphics.ColorUtils.calculateLuminance(s.c.accent) < 0.12) mix(s.c.accent, Color.BLACK, 0.22f) else mix(n.dark, Color.BLACK, 0.3f)
    c.drawColor(slate)
    val rnd = Random(art.seed)
    val p = Painter(c, whole(s), rnd)
    // Where it's been wiped.
    repeat(9) { p.glow(p.x(rnd.nextFloat()), p.y(rnd.nextFloat()), s.w * (0.2f + rnd.nextFloat() * 0.3f), Color.WHITE, 12 + rnd.nextInt(10)) }
    val chalk = n.light
    val a = n.accentOn(slate)
    val inside = RectF(6f * u, 6f * u, s.w - 6f * u, s.h - 6f * u)
    // Drawings, chalked in and rubbed.
    val set = CoverMotifs.forGenre(art.genre).shuffled(rnd)
    val spots = listOf(floatArrayOf(18f, 0.14f), floatArrayOf(82f, 0.15f), floatArrayOf(15f, 0.7f), floatArrayOf(85f, 0.68f))
    for ((i, spot) in spots.withIndex()) {
        val x = spot[0] * u + (rnd.nextFloat() - 0.5f) * 4f * u
        val y = spot[1] * s.h + (rnd.nextFloat() - 0.5f) * 4f * u
        val size = (5.6f + rnd.nextFloat() * 1.6f) * u
        CoverMotifs.draw(c, set[i % set.size], x, y, size, s.fill(faint(if (i % 2 == 0) a else chalk, 215)), (rnd.nextFloat() - 0.5f) * 30f)
        c.save()
        c.clipRect(x - size * 1.3f, y - size * 1.3f, x + size * 1.3f, y + size * 1.3f)
        val rub = s.stroke(faint(slate, 190), max(1f, 0.22f * u))
        var d = -size * 2.6f
        while (d < size * 2.6f) {
            c.drawLine(x + d - size, y + size * 1.3f, x + d + size, y - size * 1.3f, rub)
            d += 0.9f * u
        }
        c.restore()
    }
    repeat(12) { CoverMotifs.draw(c, Motif.SPARK, inside.left + rnd.nextFloat() * inside.width(), inside.top + rnd.nextFloat() * inside.height() * 0.9f, (0.6f + rnd.nextFloat() * 0.9f) * u, s.fill(faint(chalk, 150))) }
    val took = titleIn(
        s, t, RectF(15f * u, 0.22f * s.h, 85f * u, 0.62f * s.h), handBold, 17f, chalk, a, pitch = 0.94f, smallest = 5.6f,
        seriesFace = hand, seriesUpper = false, seriesSize = 5f, seriesTracking = 0f, seriesAbove = true, gap = 3.6f,
    )
    // Underlined twice, by hand.
    val under = s.stroke(s.deco(faint(a, 230)), 0.5f * u).apply { strokeCap = Paint.Cap.ROUND }
    val uy = min(took.bottom + 4f * u, 0.65f * s.h)
    for (k in 0..1) c.drawPath(Path().apply {
        moveTo(26f * u, uy + k * 1.6f * u)
        quadTo(50f * u, uy + k * 1.6f * u + (rnd.nextFloat() - 0.5f) * 2f * u, (74f - k * 5f) * u, uy + k * 1.6f * u - 0.4f * u)
    }, under)
    p.grain(1.4f)
    // The frame, and the ledge with a stick of chalk on it.
    val wood = mix(a, Color.BLACK, 0.12f)
    val edge = s.stroke(wood, 5f * u)
    c.drawRect(2.5f * u, 2.5f * u, s.w - 2.5f * u, s.h - 2.5f * u, edge)
    c.drawRect(5f * u, 5f * u, s.w - 5f * u, s.h - 5f * u, s.stroke(faint(Color.BLACK, 110), 0.5f * u))
    c.drawRect(0f, s.h - 6.4f * u, s.w, s.h, s.fill(mix(a, Color.BLACK, 0.3f)))
    c.drawRect(0f, s.h - 6.4f * u, s.w, s.h - 5.6f * u, s.fill(wood))
    c.drawRoundRect(RectF(64f * u, s.h - 8f * u, 74f * u, s.h - 6.4f * u), 0.5f * u, 0.5f * u, s.fill(chalk))
    authorIn(s, t, RectF(14f * u, s.h - 24f * u, 86f * u, s.h - 11f * u), hand, 6.4f, chalk, upper = false, tracking = 0f, pitch = 1.1f)
}

/** A school notebook: a wire binding down the side, a label with the title written on it, drawings in the margin and a strip of tape. */
internal fun CoverGenerator.notebook(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 60)
    val rnd = Random(art.seed)
    val (sheetC, written) = paperAnd(s)
    val dark = isDark(s.c.ground)
    // The wire: a hole and a loop for each turn.
    val wire = s.stroke(mix(s.c.ink, s.c.ground, 0.4f), 0.7f * u).apply { strokeCap = Paint.Cap.ROUND }
    var y = 6f * u
    while (y < s.h - 4f * u) {
        c.drawCircle(8.4f * u, y, 1.1f * u, s.fill(mix(s.c.ground, Color.BLACK, if (dark) 0.6f else 0.45f)))
        c.drawArc(RectF(-3f * u, y - 2.2f * u, 8.6f * u, y + 0.6f * u), 180f, 190f, false, wire)
        y += 5.4f * u
    }
    // Drawings in the corners.
    val set = CoverMotifs.forGenre(art.genre).shuffled(rnd)
    val spots = listOf(floatArrayOf(80f, 0.1f), floatArrayOf(27f, 0.64f), floatArrayOf(84f, 0.67f), floatArrayOf(55f, 0.72f))
    for ((i, spot) in spots.withIndex()) {
        CoverMotifs.draw(
            c, set[i % set.size], spot[0] * u + (rnd.nextFloat() - 0.5f) * 5f * u, spot[1] * s.h + (rnd.nextFloat() - 0.5f) * 4f * u, (4.6f + rnd.nextFloat() * 2f) * u,
            s.fill(if (i % 2 == 0) s.c.accent else faint(s.c.ink, 150)), (rnd.nextFloat() - 0.5f) * 40f,
        )
    }
    // The label: ruled, with a line of colour at its head.
    val wide = 60f * u
    val title = fit(t.title, handBold, 14f * u, 5f * u, wide, 0.3f * s.h, pitch = 0.96f)!!
    val seriesF = t.series?.let { fit(it, typewriter, 3f * u, 2.1f * u, wide, 7.4f * u, pitch = 1.3f) }
    val top = 0.2f * s.h
    val label = RectF(19f * u, top, 91f * u, top + title.height + (seriesF?.let { it.height + 3.6f * u } ?: 0f) + 15f * u)
    c.drawRoundRect(label, 2.4f * u, 2.4f * u, s.fill(sheetC).apply { setShadowLayer(1.2f * u, 0.3f * u, 0.6f * u, faint(Color.BLACK, 100)) })
    c.drawLine(label.left + 3f * u, label.top + 5f * u, label.right - 3f * u, label.top + 5f * u, s.stroke(s.deco(showing(s.c.accent, sheetC, 2.5)), 0.5f * u))
    var ly = label.top + 10f * u
    val ruled = s.stroke(s.deco(faint(written, 46)), max(1f, 0.16f * u))
    while (ly < label.bottom - 2f * u) {
        c.drawLine(label.left + 3f * u, ly, label.right - 3f * u, ly, ruled)
        ly += 4.6f * u
    }
    var ty = label.top + 9f * u
    title.draw(c, label.centerX() - wide / 2, ty, s.words(written))
    ty += title.height + 3.6f * u
    seriesF?.draw(c, label.centerX() - wide / 2, ty, s.seriesWords(showing(s.c.accent, sheetC, 3.0)))
    // The author, on a strip of tape.
    t.author?.let { fit(it, handBold, 6.4f * u, 3.6f * u, 58f * u, 9f * u, pitch = 1.0f) }?.let { f ->
        val cx = 56f * u
        val cy = s.h - 15.5f * u
        c.save()
        c.rotate(-2.4f, cx, cy)
        val tape = RectF(cx - f.width / 2 - 6f * u, cy - f.height / 2 - 3.4f * u, cx + f.width / 2 + 6f * u, cy + f.height / 2 + 3.6f * u)
        // Its ends cut with pinking shears.
        val strip = Path().apply {
            moveTo(tape.left, tape.top)
            lineTo(tape.right, tape.top)
            val teeth = 5
            for (k in 0 until teeth) {
                lineTo(tape.right - 1.2f * u, tape.top + tape.height() * (k + 0.5f) / teeth)
                lineTo(tape.right, tape.top + tape.height() * (k + 1f) / teeth)
            }
            lineTo(tape.left, tape.bottom)
            for (k in teeth - 1 downTo 0) {
                lineTo(tape.left + 1.2f * u, tape.top + tape.height() * (k + 0.5f) / teeth)
                lineTo(tape.left, tape.top + tape.height() * k / teeth)
            }
            close()
        }
        c.drawPath(strip, s.fill(faint(s.c.accent, 238)).apply { setShadowLayer(0.8f * u, 0.2f * u, 0.4f * u, faint(Color.BLACK, 90)) })
        f.draw(c, cx - 29f * u, cy - f.height / 2, s.words(s.c.accentInk))
        c.restore()
    }
}

// ---- machines ----------------------------------------------------------------------------------------------

/** A circuit board: a chip in the middle and its tracks running out to their pads. */
internal fun CoverGenerator.circuit(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val track = mix(s.c.ground, a, 0.6f)
    val cx = s.w / 2
    val cy = 0.57f * s.h
    val half = 11f * u
    val upTo = 0.35f * s.h
    val downTo = s.h - 25f * u
    val line = s.stroke(track, 0.5f * u).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    val pins = 7
    Painter(c, whole(s), rnd).glow(cx, cy, 42f * u, a, if (dark) 46 else 22)
    for (side in 0 until 4) for (k in 0 until pins) {
        val off = (k - (pins - 1) / 2f) * (2 * half * 0.86f / (pins - 1))
        // Out from the pin, away at a slant, then straight on to a pad.
        val dx = if (side == 1) 1f else if (side == 3) -1f else 0f
        val dy = if (side == 0) -1f else if (side == 2) 1f else 0f
        val x0 = cx + dx * half + (if (dx == 0f) off else 0f)
        val y0 = cy + dy * half + (if (dy == 0f) off else 0f)
        val out = (2.4f + abs(k - (pins - 1) / 2f) * 1.9f) * u
        val slant = (4f + rnd.nextFloat() * 9f) * u * (if (k < pins / 2) -1f else if (k > pins / 2) 1f else if (rnd.nextBoolean()) 1f else -1f)
        val x1 = x0 + dx * out
        val y1 = y0 + dy * out
        val x2 = x1 + dx * abs(slant) + (if (dx == 0f) slant else 0f)
        val y2 = y1 + dy * abs(slant) + (if (dy == 0f) slant else 0f)
        val reach = (6f + rnd.nextFloat() * 30f) * u
        var x3 = x2 + dx * reach
        var y3 = y2 + dy * reach
        if (y3 < upTo) y3 = upTo
        if (y3 > downTo) y3 = downTo
        if (x3 < 5f * u) x3 = 5f * u
        if (x3 > s.w - 5f * u) x3 = s.w - 5f * u
        if (y2 < upTo || y2 > downTo) continue
        c.drawPath(Path().apply { moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3) }, line)
        val lit = (k + side) % 3 == 0
        c.drawCircle(x3, y3, 1.3f * u, s.fill(if (lit) a else track))
        c.drawCircle(x3, y3, 0.5f * u, s.fill(s.c.ground))
        // The pin itself.
        c.drawCircle(x0 + dx * 0.8f * u, y0 + dy * 0.8f * u, 0.8f * u, s.fill(track))
    }
    val chip = RectF(cx - half, cy - half, cx + half, cy + half)
    c.drawRoundRect(chip, 1.6f * u, 1.6f * u, s.fill(if (dark) mix(s.c.ground, Color.BLACK, 0.55f) else s.c.ink))
    c.drawRoundRect(chip, 1.6f * u, 1.6f * u, s.stroke(a, 0.4f * u))
    CoverMotifs.draw(c, CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)], cx, cy, half * 0.56f, s.fill(if (dark) a else showing(a, s.c.ink, 3.0)))
    c.drawCircle(chip.left + 2.2f * u, chip.top + 2.2f * u, 0.6f * u, s.fill(if (dark) a else showing(a, s.c.ink, 3.0)))
    titleIn(
        s, t, RectF(8f * u, 9f * u, 92f * u, 0.3f * s.h), space, 9.4f, s.c.ink, a, upper = true, tracking = 0.04f, pitch = 1.2f, place = -1,
        seriesFace = spaceLight, seriesSize = 2.7f, gap = 3.6f, shadow = if (dark) faint(a, 150) else null, shadowRadius = 1.4f,
    )
    authorIn(s, t, RectF(10f * u, s.h - 19f * u, 90f * u, s.h - 8f * u), spaceLight, 3.6f, s.c.ink, tracking = 0.2f)
}

/** A screen gone wrong: the title split into its colours and slipped sideways in strips, over scan lines. */
internal fun CoverGenerator.glitch(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val other = showing(hueShift(a, 170f), s.c.ground, 2.5)
    val scan = s.fill(faint(if (dark) Color.WHITE else Color.BLACK, if (dark) 9 else 12))
    var sy = 0f
    while (sy < s.h) {
        c.drawRect(0f, sy, s.w, sy + max(1f, 0.3f * u), scan)
        sy += max(2f, 1.1f * u)
    }
    val left = 8f * u
    val wide = s.w - 16f * u
    var y = 9f * u
    t.series?.let { fit(caps(it), mono, 2.8f * u, 2f * u, wide, 7.4f * u, Layout.Alignment.ALIGN_NORMAL, pitch = 1.4f) }?.let {
        it.draw(c, left, y, s.seriesWords(a))
        y += it.height + 4.4f * u
    }
    // The title on a sheet of its own, in three printings, then laid down strip by strip.
    val title = fit(caps(t.title), condensed, 25f * u, 6f * u, wide, 0.4f * s.h, Layout.Alignment.ALIGN_NORMAL, pitch = 0.98f)!!
    val pad = (3f * u).toInt()
    val layer = Bitmap.createBitmap(max(1, s.w.toInt()), max(1, (title.height + pad * 2).toInt()), Bitmap.Config.ARGB_8888)
    val lc = Canvas(layer)
    title.draw(lc, left - 1.3f * u, pad.toFloat(), a)
    title.draw(lc, left + 1.3f * u, pad + 0.4f * u, other)
    title.draw(lc, left, pad.toFloat(), s.words(s.c.ink))
    var from = 0
    while (from < layer.height) {
        val tall = min(layer.height - from, ((2.4f + rnd.nextFloat() * 7f) * u).toInt().coerceAtLeast(2))
        val slip = if (rnd.nextInt(5) == 0) (rnd.nextFloat() - 0.5f) * 7f * u else 0f
        c.drawBitmap(layer, Rect(0, from, layer.width, from + tall), RectF(slip, y - pad + from, slip + layer.width, y - pad + from + tall), null)
        from += tall
    }
    y += title.height
    // Bars of colour where the picture broke up, and rows of data.
    val foot = s.h - 24f * u
    repeat(9) {
        val by = y + 5f * u + rnd.nextFloat() * max(1f, foot - y - 8f * u)
        val bx = rnd.nextFloat() * 80f * u
        c.drawRect(bx, by, bx + (6f + rnd.nextFloat() * 36f) * u, by + (0.5f + rnd.nextFloat() * 1.7f) * u, s.fill(faint(if (rnd.nextBoolean()) a else other, 150 + rnd.nextInt(100))))
    }
    val data = s.fill(faint(s.c.ink, 70))
    var dy = y + 7f * u
    while (dy < foot - 2f * u) {
        var dx = left
        while (dx < left + wide) {
            val run = (0.8f + rnd.nextFloat() * 4f) * u
            if (rnd.nextInt(3) != 0) c.drawRect(dx, dy, min(dx + run, left + wide), dy + 0.7f * u, data)
            dx += run + 0.8f * u
        }
        dy += 3.1f * u
    }
    t.author?.let { fit(caps(it), monoBold, 4f * u, 2.6f * u, wide, 11f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.06f, pitch = 1.3f) }?.let { f ->
        f.draw(c, left, s.h - 8f * u - f.height, s.words(s.c.ink))
    }
}

/** Clockwork: a dial with its hands, and cog-wheels turning behind it. */
internal fun CoverGenerator.clockwork(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 120)
    val rnd = Random(art.seed)
    val gold = s.deco(s.c.accent)
    val brass = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = foil(s, gold) }
    val line = Paint(brass).apply { style = Paint.Style.STROKE; strokeWidth = 0.34f * u; strokeCap = Paint.Cap.ROUND }
    val cx = s.w / 2
    val cy = 0.56f * s.h
    val r = min(24f * u, 0.19f * s.h)
    // The wheels behind, each a little fainter.
    val wheels = listOf(floatArrayOf(cx - r * 1.36f, cy - r * 0.6f, r * 0.86f, 14f), floatArrayOf(cx + r * 1.5f, cy + r * 0.34f, r * 0.94f, 16f), floatArrayOf(cx - r * 1.1f, cy + r * 1.02f, r * 0.56f, 10f), floatArrayOf(cx + r * 1.02f, cy - r * 1.06f, r * 0.5f, 9f))
    for ((i, wh) in wheels.withIndex()) {
        val wheel = cog(wh[0], wh[1], wh[2], wh[3].toInt(), rnd.nextFloat() * 30f)
        c.drawPath(wheel, Paint(brass).apply { alpha = 120 - i * 14 })
        c.drawPath(wheel, Paint(line).apply { strokeWidth = 0.2f * u; alpha = 200 })
    }
    // The dial.
    c.drawCircle(cx, cy, r, s.fill(s.c.ground).apply { setShadowLayer(1.6f * u, 0f, 0.6f * u, faint(Color.BLACK, 140)) })
    c.drawCircle(cx, cy, r, Paint(line).apply { strokeWidth = 0.9f * u })
    c.drawCircle(cx, cy, r * 0.9f, Paint(line).apply { strokeWidth = 0.16f * u })
    for (k in 0 until 60) {
        val a = Math.toRadians(k * 6.0)
        val hour = k % 5 == 0
        val near = r * (if (hour) 0.74f else 0.84f)
        c.drawLine(cx + (cos(a) * near).toFloat(), cy + (sin(a) * near).toFloat(), cx + (cos(a) * r * 0.88f).toFloat(), cy + (sin(a) * r * 0.88f).toFloat(), Paint(line).apply { strokeWidth = (if (hour) 0.6f else 0.16f) * u })
    }
    val hourAt = Math.toRadians(rnd.nextInt(12) * 30.0 - 90.0 + 8.0)
    val minuteAt = Math.toRadians(rnd.nextInt(12) * 30.0 - 90.0)
    c.drawLine(cx, cy, cx + (cos(hourAt) * r * 0.46f).toFloat(), cy + (sin(hourAt) * r * 0.46f).toFloat(), Paint(line).apply { strokeWidth = 1f * u })
    c.drawLine(cx, cy, cx + (cos(minuteAt) * r * 0.7f).toFloat(), cy + (sin(minuteAt) * r * 0.7f).toFloat(), Paint(line).apply { strokeWidth = 0.6f * u })
    c.drawCircle(cx, cy, 1.5f * u, brass)
    titleIn(
        s, t, RectF(9f * u, 9f * u, 91f * u, cy - r * 1.62f - 3f * u), didone, 10f, s.c.accent, s.c.ink, upper = true, tracking = 0.07f, pitch = 1.14f, place = -1,
        foil = true, gap = 3.2f,
    )
    authorIn(s, t, RectF(12f * u, max(cy + r * 1.62f + 2f * u, s.h - 19f * u), 88f * u, s.h - 7f * u), romanLight, 4f, s.c.ink, tracking = 0.22f)
}

/** The side of a packing case: boards, warning stripes, and the words sprayed through a stencil. */
internal fun CoverGenerator.stencilCrate(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    // Boards, their grain, and nails.
    val gap = s.stroke(faint(Color.BLACK, if (dark) 110 else 60), 0.4f * u)
    val grainLine = s.stroke(faint(Color.BLACK, if (dark) 30 else 16), max(1f, 0.14f * u))
    val boards = 5
    for (k in 0..boards) {
        val y = s.h * k / boards
        if (k in 1 until boards) c.drawLine(0f, y, s.w, y, gap)
        if (k < boards) {
            repeat(9) {
                val gy = y + rnd.nextFloat() * s.h / boards
                val gx = rnd.nextFloat() * s.w
                c.drawPath(Path().apply { moveTo(gx, gy); quadTo(gx + 14f * u, gy + (rnd.nextFloat() - 0.5f) * 2f * u, gx + (22f + rnd.nextFloat() * 30f) * u, gy) }, grainLine)
            }
            for (x in listOf(4f * u, s.w - 4f * u)) c.drawCircle(x, y + s.h / boards / 2, 0.7f * u, s.fill(faint(Color.BLACK, if (dark) 130 else 80)))
        }
    }
    // Warning stripes at head and foot.
    val stripeDark = if (dark) mix(s.c.ground, Color.BLACK, 0.6f) else s.c.ink
    for (top in listOf(0f, s.h - 6.4f * u)) {
        c.save()
        c.clipRect(0f, top, s.w, top + 6.4f * u)
        c.drawColor(s.c.accent)
        var d = -10f * u
        while (d < s.w + 10f * u) {
            c.drawPath(Path().apply { moveTo(d, top + 6.4f * u); lineTo(d + 4f * u, top + 6.4f * u); lineTo(d + 10.4f * u, top); lineTo(d + 6.4f * u, top); close() }, s.fill(stripeDark))
            d += 8f * u
        }
        c.restore()
    }
    // Marks sprayed on: this way up, and what's inside.
    val sprayed = s.fill(faint(s.c.ink, 215))
    val low = 0.7f * s.h
    for (k in 0..1) CoverMotifs.draw(c, Motif.ARROW, (14f + k * 11f) * u, low, 5f * u, sprayed, -45f)
    val what = CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)]
    c.drawCircle(76f * u, low, 11f * u, s.stroke(faint(s.c.ink, 215), 1f * u))
    CoverMotifs.draw(c, what, 76f * u, low, 6.4f * u, sprayed)
    titleIn(
        s, t, RectF(8f * u, 14f * u, 92f * u, 0.56f * s.h), stencil, 15f, faint(s.c.ink, 240), s.c.accent, upper = true, align = Layout.Alignment.ALIGN_NORMAL, pitch = 1.08f,
        seriesFace = stencil, seriesSize = 3.6f, seriesTracking = 0.08f, seriesAbove = true, gap = 4f, place = -1, smallest = 4.6f,
    )
    Painter(c, whole(s), rnd).apply {
        grain(1.6f)
        vignette(80)
    }
    authorIn(s, t, RectF(8f * u, s.h - 23f * u, 92f * u, s.h - 11f * u), stencil, 5f, faint(s.c.ink, 240), tracking = 0.08f, align = Layout.Alignment.ALIGN_NORMAL, place = 1)
}

// ---- night and home ----------------------------------------------------------------------------------------

/** The moon through its month, in an arch: new to full and back again. */
internal fun CoverGenerator.moonPhases(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    board(s, art.seed, vignette = 110)
    val rnd = Random(art.seed)
    val lit = s.c.accent
    val unlit = mix(s.c.ground, s.c.ink, 0.16f)
    val cx = s.w / 2
    val took = titleIn(s, t, RectF(9f * u, 10f * u, 91f * u, 0.33f * s.h), didone, 10.4f, s.c.ink, s.c.accent, upper = true, tracking = 0.08f, pitch = 1.14f, place = -1, gap = 3.4f)
    val footTop = s.h - 22f * u
    val midY = (took.bottom + footTop) / 2
    repeat(26) {
        val x = (6f + rnd.nextFloat() * 88f) * u
        val y = took.bottom + 4f * u + rnd.nextFloat() * (footTop - took.bottom - 8f * u)
        CoverMotifs.draw(c, Motif.SPARK, x, y, (0.5f + rnd.nextFloat() * 1f) * u, s.fill(faint(s.c.ink, 120 + rnd.nextInt(100))))
    }
    Painter(c, whole(s), rnd).glow(cx, midY - 5f * u, 26f * u, lit, 60)
    for (i in -3..3) {
        val r = (if (i == 0) 9.4f else 5.6f - abs(i) * 0.5f) * u
        val x = cx + i * 12.4f * u + (if (i > 0) 3.4f else if (i < 0) -3.4f else 0f) * u
        val y = midY - 5f * u + i * i * 2.1f * u
        val full = 1f - abs(i) / 3.4f
        c.drawCircle(x, y, r, s.fill(unlit))
        val moon = Path()
        // The lit half, and the edge of the shadow: an oval that fattens or cuts it.
        val halfDisc = Path().apply { arcTo(RectF(x - r, y - r, x + r, y + r), -90f, if (i <= 0) 180f else -180f, true); close() }
        val oval = Path().apply { addOval(RectF(x - r * abs(2 * full - 1f), y - r, x + r * abs(2 * full - 1f), y + r), Path.Direction.CW) }
        moon.op(halfDisc, oval, if (full >= 0.5f) Path.Op.UNION else Path.Op.DIFFERENCE)
        c.drawPath(moon, s.fill(lit))
        c.drawCircle(x, y, r, s.stroke(s.deco(faint(s.c.ink, 120)), 0.16f * u))
    }
    ornament(s, cx, min(midY + 22f * u, footTop - 4f * u), s.deco(s.c.accent))
    authorIn(s, t, RectF(12f * u, footTop, 88f * u, s.h - 8f * u), romanLight, 4f, s.c.ink, tracking = 0.22f)
}

/** Shelves of books, spine after spine, with the title between them. */
internal fun CoverGenerator.bookshelf(s: Sheet, t: Words, art: Art) {
    val u = s.u
    val c = s.canvas
    c.drawColor(s.c.ground)
    val rnd = Random(art.seed)
    val dark = isDark(s.c.ground)
    val a = s.c.accent
    val colours = listOf(
        a, hueShift(a, 150f, if (dark) 0f else 0.06f), hueShift(a, -42f, if (dark) -0.08f else 0.1f), mix(s.c.ground, s.c.ink, 0.72f), mix(s.c.ground, s.c.ink, 0.3f),
        mix(a, s.c.ink, 0.5f), mix(a, s.c.ground, 0.45f),
    )
    val wood = mix(s.c.ground, s.c.ink, 0.5f)
    val thing = CoverMotifs.forGenre(art.genre)[rnd.nextInt(3)]
    fun row(top: Float, shelf: Float, withThing: Boolean) {
        var x = 5f * u
        var last = -1
        val spot = if (withThing) (30f + rnd.nextFloat() * 36f) * u else -1f
        var placed = !withThing
        while (x < s.w - 9f * u) {
            if (!placed && x > spot) {
                // Something kept on the shelf among the books.
                val size = (shelf - top) * 0.3f
                CoverMotifs.draw(c, thing, x + size * 1.2f, shelf - size, size, s.fill(s.c.ink))
                x += size * 2.6f
                placed = true
                continue
            }
            val wide = (4f + rnd.nextFloat() * 5.4f) * u
            val tall = (shelf - top) * (0.68f + rnd.nextFloat() * 0.32f)
            var colour = rnd.nextInt(colours.size)
            if (colour == last) colour = (colour + 1) % colours.size
            last = colour
            val lean = if (rnd.nextInt(9) == 0 && x > 12f * u) -9f else 0f
            c.save()
            if (lean != 0f) c.rotate(lean, x + wide, shelf)
            val spine = RectF(x, shelf - tall, x + wide, shelf)
            c.drawRect(spine, s.fill(colours[colour]))
            c.drawRect(spine.left, spine.top, spine.left + 0.5f * u, spine.bottom, s.fill(faint(Color.BLACK, 40)))
            val mark = s.fill(faint(if (colour == 3 || colour == 5) s.c.ground else s.c.ink, 150))
            when (rnd.nextInt(4)) {
                0 -> {
                    c.drawRect(spine.left, spine.top + tall * 0.14f, spine.right, spine.top + tall * 0.14f + 0.5f * u, mark)
                    c.drawRect(spine.left, spine.bottom - tall * 0.14f, spine.right, spine.bottom - tall * 0.14f + 0.5f * u, mark)
                }
                1 -> c.drawRect(spine.left + wide * 0.22f, spine.top + tall * 0.2f, spine.right - wide * 0.22f, spine.top + tall * 0.5f, mark)
                2 -> c.drawCircle(spine.centerX(), spine.top + tall * 0.24f, wide * 0.2f, mark)
                else -> c.drawRect(spine.left, spine.top + tall * 0.08f, spine.right, spine.top + tall * 0.08f + 1.2f * u, mark)
            }
            c.restore()
            x += wide + (if (lean != 0f) 2.6f * u else 0.3f * u)
        }
        c.drawRect(0f, shelf, s.w, shelf + 2f * u, s.fill(wood))
        c.drawRect(0f, shelf + 2f * u, s.w, shelf + 2.8f * u, s.fill(faint(Color.BLACK, 50)))
    }
    row(6f * u, 0.25f * s.h, false)
    row(0.64f * s.h, 0.84f * s.h, true)
    Painter(c, whole(s), rnd).grain(0.7f)
    titleIn(
        s, t, RectF(10f * u, 0.25f * s.h + 7f * u, 90f * u, 0.64f * s.h - 4f * u), serifBold, 10.4f, s.c.ink, a, pitch = 1.1f,
        seriesFace = sans, seriesSize = 2.8f, seriesTracking = 0.2f, gap = 3.6f,
    )
    authorIn(s, t, RectF(10f * u, 0.84f * s.h + 4.4f * u, 90f * u, s.h - 5f * u), sans, 3.9f, s.c.ink, tracking = 0.22f)
}
