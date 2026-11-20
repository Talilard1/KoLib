package dev.kolib.app.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** The light and shade of the patterned backdrops, over the look's background colour. */
internal fun patternLayers(backdrop: Backdrop, w: Float, h: Float, big: Float): List<Brush> {
    val centre = Offset(w / 2, h / 2)
    val corner = hypot(w, h) / 2
    return when (backdrop) {
        // A screen's glow, strongest at the top left, falling off into black glass.
        Backdrop.SCANLINES -> listOf(
            Brush.radialGradient(0f to Color(0xFF1AFF7A).copy(alpha = 0.105f), 1f to Color.Transparent, center = Offset(w * 0.18f, -h * 0.05f), radius = big * 0.9f),
            Brush.radialGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), center = centre, radius = corner),
        )
        // Parchment: lighter where it's handled least, browned toward its edges.
        Backdrop.PARCHMENT -> listOf(
            Brush.radialGradient(0f to Color(0xFFFFF8E6).copy(alpha = 0.55f), 1f to Color.Transparent, center = Offset(w * 0.42f, h * 0.36f), radius = big * 0.7f),
            Brush.radialGradient(0.5f to Color.Transparent, 1f to Color(0xFF7A5A2E).copy(alpha = 0.24f), center = centre, radius = corner),
        )
        // Deep space through the glass: a nebula's faint colour, and the edges in shadow.
        Backdrop.SPACE -> listOf(
            Brush.radialGradient(0f to Color(0xFF5A46B8).copy(alpha = 0.17f), 0.55f to Color(0xFF5A46B8).copy(alpha = 0.05f), 1f to Color.Transparent, center = Offset(w * 0.8f, h * 0.16f), radius = big * 0.62f),
            Brush.radialGradient(0f to Color(0xFF1E7C93).copy(alpha = 0.13f), 1f to Color.Transparent, center = Offset(w * 0.1f, h * 0.58f), radius = big * 0.52f),
            Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.40f), center = centre, radius = corner),
        )
        // A projector's light, rising from below and fading toward the top; the room dark around it.
        Backdrop.PROJECTION -> listOf(
            Brush.radialGradient(0f to Color(0xFF3A9BFF).copy(alpha = 0.24f), 0.45f to Color(0xFF3A9BFF).copy(alpha = 0.08f), 1f to Color.Transparent, center = Offset(w * 0.5f, h * 1.06f), radius = big * 0.8f),
            Brush.radialGradient(0f to Color(0xFF1C5FA0).copy(alpha = 0.10f), 1f to Color.Transparent, center = Offset(w * 0.5f, -h * 0.25f), radius = big * 0.7f),
            Brush.radialGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f), center = centre, radius = corner),
        )
        // Lamps over the deck: cool from above, and a warning light's red at one corner.
        Backdrop.PANELS -> listOf(
            Brush.radialGradient(0f to Color(0xFF454852).copy(alpha = 0.32f), 1f to Color.Transparent, center = Offset(w * 0.3f, -h * 0.18f), radius = big * 0.85f),
            Brush.radialGradient(0f to Color(0xFFFF5A3C).copy(alpha = 0.07f), 1f to Color.Transparent, center = Offset(w * 1.02f, -h * 0.04f), radius = big * 0.45f),
        )
        // Cold light on the plating, and the furnace's glow from below.
        Backdrop.PLATES -> listOf(
            Brush.radialGradient(0f to Color(0xFF9FB3AC).copy(alpha = 0.075f), 1f to Color.Transparent, center = Offset(w * 0.2f, -h * 0.1f), radius = big * 0.85f),
            Brush.radialGradient(0f to Color(0xFFFF7A2E).copy(alpha = 0.11f), 1f to Color.Transparent, center = Offset(w * 1.02f, h * 1.1f), radius = big * 0.7f),
        )
        // The screen's red light over the front rows; the back of the room in the dark.
        Backdrop.SPOTLIGHT -> listOf(
            Brush.radialGradient(0f to Color(0xFFC0161F).copy(alpha = 0.26f), 0.5f to Color(0xFFC0161F).copy(alpha = 0.08f), 1f to Color.Transparent, center = Offset(w * 0.5f, -h * 0.22f), radius = big * 0.85f),
            Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.42f)),
        )
        // Morning light on blush paper, warmer and rosier toward the foot.
        Backdrop.BLUSH -> listOf(
            Brush.radialGradient(0f to Color.White.copy(alpha = 0.80f), 1f to Color.Transparent, center = Offset(w * 0.15f, -h * 0.1f), radius = big * 0.9f),
            Brush.radialGradient(0f to Color(0xFFF3B7C6).copy(alpha = 0.42f), 1f to Color.Transparent, center = Offset(w * 1.04f, h * 1.08f), radius = big * 0.75f),
        )
        else -> emptyList()
    }
}

/**
 * The pattern drawn over a patterned backdrop's light, for a screen [w] by [h]; null for the looks without
 * one. It's drawn once and kept as a picture, so it can be made of as many small shapes as it needs.
 */
internal fun Density.backdropPattern(backdrop: Backdrop, w: Float, h: Float): (DrawScope.() -> Unit)? = when (backdrop) {
    Backdrop.SCANLINES -> scanLines(w, h)
    Backdrop.PARCHMENT -> diaper(w, h)
    Backdrop.SPACE -> space(w, h)
    Backdrop.PROJECTION -> projection(w, h)
    Backdrop.PANELS -> panels(w, h)
    Backdrop.PLATES -> plates(w, h)
    Backdrop.BLUSH -> hearts(w, h)
    else -> null
}

/** A tube's scan lines: fine, even, barely there. */
private fun Density.scanLines(w: Float, h: Float): DrawScope.() -> Unit {
    val step = 4.dp.toPx()
    val line = 1.dp.toPx()
    return {
        var y = 0f
        while (y < h) {
            drawRect(Color(0xFF3DFF8B), Offset(0f, y), Size(w, line), alpha = 0.032f)
            y += step
        }
    }
}

/**
 * An illuminated page's diaper ground: a lattice of fine ruled diagonals, a gold point where they cross, and a
 * small quatrefoil in each lozenge between.
 */
private fun Density.diaper(w: Float, h: Float): DrawScope.() -> Unit {
    val period = 58.dp.toPx()
    val half = period / 2
    val lines = Path()
    var c = -h
    while (c < w + h) {
        // Down to the right, and down to the left, through every other crossing.
        lines.moveTo(c, 0f); lines.lineTo(c + h, h)
        lines.moveTo(c, 0f); lines.lineTo(c - h, h)
        c += period
    }
    val petals = Path()
    val points = Path()
    val petal = 3.1.dp.toPx()
    val out = 3.5.dp.toPx()
    val point = 1.25.dp.toPx()
    var b = 0
    while (b * half <= h + half) {
        var a = 0
        while (a * half <= w + half) {
            val x = a * half
            val y = b * half
            if ((a + b) % 2 == 0) {
                points.addOval(androidx.compose.ui.geometry.Rect(x - point, y - point, x + point, y + point))
            } else {
                for ((dx, dy) in listOf(out to 0f, -out to 0f, 0f to out, 0f to -out)) {
                    petals.addOval(androidx.compose.ui.geometry.Rect(x + dx - petal, y + dy - petal, x + dx + petal, y + dy + petal))
                }
            }
            a++
        }
        b++
    }
    val hair = 0.8.dp.toPx()
    return {
        drawPath(lines, Color(0xFF6B4A1E), alpha = 0.075f, style = Stroke(hair))
        drawPath(petals, Color(0xFF8C1D18), alpha = 0.105f, style = Stroke(0.9.dp.toPx()))
        drawPath(points, Color(0xFFB8892A), alpha = 0.42f)
    }
}

/**
 * Deep space: stars at three distances, the nearest few with a glow and fine rays, and low at one corner the curve
 * of a planet, its air lit at the rim.
 */
private fun Density.space(w: Float, h: Float): DrawScope.() -> Unit {
    val unit = 1.dp.toPx()
    val random = Random(19)
    val big = maxOf(w, h)
    // The planet: mostly below the screen, its curve across the foot.
    val r = big * 0.62f
    val planet = Offset(w * 0.86f, h + r * 0.78f)
    fun clear(x: Float, y: Float) = hypot(x - planet.x, y - planet.y) > r + 4 * unit
    val area = (w / unit) * (h / unit)
    fun count(per: Float, least: Int, most: Int) = (area / per).toInt().coerceIn(least, most)
    fun stars(n: Int, size: Float, spread: Float, light: Float, lift: Float, high: Float = 1f) =
        List(n) { floatArrayOf(random.nextFloat() * w, random.nextFloat() * h * high, (size + random.nextFloat() * spread) * unit, light + random.nextFloat() * lift, random.nextFloat()) }
            .filter { clear(it[0], it[1]) }
    val far = stars(count(1400f, 120, 1400), 0.35f, 0.35f, 0.10f, 0.22f)
    val mid = stars(count(9000f, 30, 260), 0.7f, 0.5f, 0.35f, 0.35f)
    val near = stars(count(60000f, 4, 18), 1.2f, 0.8f, 0.75f, 0.25f, high = 0.8f)
    fun tint(t: Float) = when {
        t > 0.85f -> Color(0xFFFFE2B8)
        t > 0.6f -> Color(0xFFCFE0FF)
        else -> Color(0xFFF2F6FF)
    }
    return {
        for (s in far) drawCircle(tint(s[4]), radius = s[2], center = Offset(s[0], s[1]), alpha = s[3])
        for (s in mid) drawCircle(tint(s[4]), radius = s[2], center = Offset(s[0], s[1]), alpha = s[3])
        for (s in near) {
            val c = Offset(s[0], s[1])
            drawCircle(Brush.radialGradient(listOf(tint(s[4]).copy(alpha = 0.32f), Color.Transparent), center = c, radius = 7 * unit), radius = 7 * unit, center = c)
            drawCircle(tint(s[4]), radius = s[2], center = c, alpha = s[3])
            // Fine rays across the brightest.
            if (s[2] > 1.7f * unit) {
                val ray = 9 * unit
                drawLine(tint(s[4]), Offset(c.x - ray, c.y), Offset(c.x + ray, c.y), strokeWidth = 0.6f * unit, alpha = 0.35f)
                drawLine(tint(s[4]), Offset(c.x, c.y - ray), Offset(c.x, c.y + ray), strokeWidth = 0.6f * unit, alpha = 0.35f)
            }
        }
        // The planet's air, glowing at its rim; then the planet itself, dark, a little lighter toward the light.
        val air = r * 1.10f
        drawCircle(
            Brush.radialGradient(
                (r * 0.94f) / air to Color.Transparent,
                r / air to Color(0xFF7DB4FF).copy(alpha = 0.42f),
                1f to Color.Transparent,
                center = planet, radius = air,
            ),
            radius = air, center = planet,
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF13224A), Color(0xFF070C1A), Color(0xFF03050C)), center = Offset(planet.x - r * 0.35f, planet.y - r * 0.55f), radius = r * 1.3f),
            radius = r, center = planet,
        )
        drawCircle(Color(0xFFB9D6FF), radius = r, center = planet, alpha = 0.35f, style = Stroke(1.2f * unit))
    }
}

/**
 * A projection: fine scan lines, a few bands where the picture wavers, a thin bright flicker here and there, a grain
 * of light, and at the foot the projector's rings with its beam rising from them.
 */
private fun Density.projection(w: Float, h: Float): DrawScope.() -> Unit {
    val unit = 1.dp.toPx()
    val blue = Color(0xFF7CCBFF)
    val random = Random(23)
    val bands = List(8) { floatArrayOf(random.nextFloat() * h, (6 + random.nextFloat() * 34) * unit, 0.018f + random.nextFloat() * 0.03f) }
    val flickers = List(4) { floatArrayOf(random.nextFloat() * h, random.nextFloat() * w * 0.5f, (0.25f + random.nextFloat() * 0.5f) * w, 0.10f + random.nextFloat() * 0.10f) }
    val grain = List((((w / unit) * (h / unit)) / 2600f).toInt().coerceIn(60, 900)) { floatArrayOf(random.nextFloat() * w, random.nextFloat() * h, 0.04f + random.nextFloat() * 0.08f) }
    val step = 3 * unit
    val base = Offset(w / 2, h - 22 * unit)
    val beam = Path().apply {
        moveTo(base.x - 34 * unit, base.y)
        lineTo(base.x + 34 * unit, base.y)
        lineTo(w / 2 + w * 0.62f, 0f)
        lineTo(w / 2 - w * 0.62f, 0f)
        close()
    }
    return {
        // The beam, brightest at the projector.
        drawPath(beam, Brush.verticalGradient(0f to Color.Transparent, 0.55f to blue.copy(alpha = 0.025f), 1f to blue.copy(alpha = 0.07f), startY = 0f, endY = base.y))
        var y = 0f
        while (y < h) {
            drawRect(blue, Offset(0f, y), Size(w, 0.8f * unit), alpha = 0.04f)
            y += step
        }
        for (b in bands) drawRect(blue, Offset(0f, b[0]), Size(w, b[1]), alpha = b[2])
        for (f in flickers) drawRect(Color(0xFFCFEFFF), Offset(f[1], f[0]), Size(f[2], 0.8f * unit), alpha = f[3])
        for (g in grain) drawRect(blue, Offset(g[0], g[1]), Size(unit, unit), alpha = g[2])
        // The projector's rings, seen from a little above.
        for ((i, across) in listOf(36f, 62f, 92f).withIndex()) {
            val rx = across * unit
            drawOval(blue, Offset(base.x - rx, base.y - rx * 0.16f), Size(rx * 2, rx * 0.32f), alpha = 0.26f - i * 0.07f, style = Stroke(unit))
        }
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFBFE6FF).copy(alpha = 0.55f), Color.Transparent), center = base, radius = 30 * unit),
            Offset(base.x - 30 * unit, base.y - 5 * unit), Size(60 * unit, 10 * unit),
        )
    }
}

/**
 * A deck's panels: the screen split into panels of different sizes with seams between, a few a shade lighter, and
 * here and there a strip of signal red along one side, a short row of small lights, or a fine dial.
 */
private fun Density.panels(w: Float, h: Float): DrawScope.() -> Unit {
    val unit = 1.dp.toPx()
    val random = Random(31)
    val gap = 3 * unit
    val rects = ArrayList<Rect>()
    fun split(r: Rect, depth: Int) {
        val wide = r.width > 190 * unit
        val tall = r.height > 150 * unit
        if (depth > 6 || (!wide && !tall) || (depth > 2 && random.nextFloat() < 0.18f)) {
            rects += r
            return
        }
        val across = if (wide && tall) r.width > r.height * (0.8f + random.nextFloat() * 0.6f) else wide
        val t = 0.3f + random.nextFloat() * 0.4f
        if (across) {
            val x = r.left + r.width * t
            split(Rect(r.left, r.top, x, r.bottom), depth + 1)
            split(Rect(x, r.top, r.right, r.bottom), depth + 1)
        } else {
            val y = r.top + r.height * t
            split(Rect(r.left, r.top, r.right, y), depth + 1)
            split(Rect(r.left, y, r.right, r.bottom), depth + 1)
        }
    }
    split(Rect(0f, 0f, w, h), 0)
    val lights = listOf(Color(0xFFFF6A4D), Color(0xFFF2B142), Color(0xFF6CCDF2), Color(0xFF8A8D96))
    val cyan = Color(0xFF6CCDF2)
    // What each panel has, decided here so it's the same each time.
    val kinds = rects.map { random.nextFloat() }
    val counts = rects.map { 3 + random.nextInt(4) }
    val corner = CornerRadius(4 * unit)
    return {
        rects.forEachIndexed { i, whole ->
            val r = whole.deflate(gap / 2)
            if (r.width < 8 * unit || r.height < 8 * unit) return@forEachIndexed
            val k = kinds[i]
            if (k < 0.3f) drawRoundRect(Color.White, r.topLeft, r.size, corner, alpha = 0.022f)
            drawRoundRect(Color.White, r.topLeft, r.size, corner, alpha = 0.055f, style = Stroke(unit))
            when {
                k < 0.12f && r.height > 60 * unit -> drawRect(Color(0xFFFF6A4D), Offset(r.left + 1.5f * unit, r.top + 8 * unit), Size(2.5f * unit, (r.height * 0.4f).coerceAtMost(90 * unit)), alpha = 0.24f)
                k < 0.32f -> for (j in 0 until counts[i]) {
                    drawRect(lights[(j + i) % lights.size], Offset(r.right - 8 * unit - j * 6 * unit, r.top + 6 * unit), Size(3 * unit, 3 * unit), alpha = 0.42f)
                }
                k < 0.40f && r.width > 60 * unit && r.height > 60 * unit -> {
                    val c = r.center
                    val rad = minOf(r.width, r.height) * 0.22f
                    drawCircle(cyan, radius = rad, center = c, alpha = 0.10f, style = Stroke(unit))
                    drawCircle(cyan, radius = rad * 0.55f, center = c, alpha = 0.07f, style = Stroke(unit))
                    for (t in 0 until 12) {
                        val a = t * PI / 6
                        val dx = cos(a).toFloat()
                        val dy = sin(a).toFloat()
                        drawLine(cyan, Offset(c.x + dx * rad * 1.08f, c.y + dy * rad * 1.08f), Offset(c.x + dx * rad * 1.22f, c.y + dy * rad * 1.22f), strokeWidth = unit, alpha = 0.10f)
                    }
                }
                else -> Unit
            }
        }
    }
}

/** Six-sided plating: the seams between the plates, and a plate here and there that catches the light. */
private fun Density.plates(w: Float, h: Float): DrawScope.() -> Unit {
    val r = 25.dp.toPx()
    val across = sqrt(3f) * r
    val seams = Path()
    val lit = Path()
    val random = Random(5)
    fun hex(p: Path, cx: Float, cy: Float, radius: Float) {
        for (k in 0 until 6) {
            val a = (PI / 180 * (60 * k - 90)).toFloat()
            val x = cx + radius * cos(a)
            val y = cy + radius * sin(a)
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }
    var row = 0
    var cy = 0f
    while (cy <= h + r) {
        var cx = if (row % 2 == 0) 0f else across / 2
        while (cx <= w + across) {
            hex(seams, cx, cy, r)
            if (random.nextFloat() < 0.07f) hex(lit, cx, cy, r - 1.5.dp.toPx())
            cx += across
        }
        cy += 1.5f * r
        row++
    }
    val seam = 1.dp.toPx()
    return {
        drawPath(lit, Color(0xFFB8C4BC), alpha = 0.035f)
        drawPath(seams, Color(0xFFB8C4BC), alpha = 0.06f, style = Stroke(seam))
    }
}

/** Small hearts scattered evenly over the paper, pale enough to be noticed second. */
private fun Density.hearts(w: Float, h: Float): DrawScope.() -> Unit {
    val step = 46.dp.toPx()
    val path = Path()
    fun heart(cx: Float, cy: Float, s: Float) {
        val t = s * 0.9f
        path.moveTo(cx, cy + t * 0.5f)
        path.cubicTo(cx - s * 0.6f, cy + t * 0.1f, cx - s * 0.55f, cy - t * 0.5f, cx - s * 0.25f, cy - t * 0.5f)
        path.cubicTo(cx - s * 0.08f, cy - t * 0.5f, cx, cy - t * 0.38f, cx, cy - t * 0.22f)
        path.cubicTo(cx, cy - t * 0.38f, cx + s * 0.08f, cy - t * 0.5f, cx + s * 0.25f, cy - t * 0.5f)
        path.cubicTo(cx + s * 0.55f, cy - t * 0.5f, cx + s * 0.6f, cy + t * 0.1f, cx, cy + t * 0.5f)
        path.close()
    }
    var row = 0
    var y = step / 2
    while (y <= h + step) {
        var x = if (row % 2 == 0) step / 2 else step
        var col = 0
        while (x <= w + step) {
            heart(x, y, if ((row + col) % 3 == 0) 9.dp.toPx() else 6.dp.toPx())
            x += step; col++
        }
        y += step * 0.86f
        row++
    }
    return { drawPath(path, Color(0xFFC2456B), alpha = 0.065f) }
}
