package dev.kolib.app.ui.theme

import androidx.compose.ui.geometry.Offset
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
        // Lamps low on the bridge: amber from above, the teal of read-outs from below.
        Backdrop.STARS -> listOf(
            Brush.radialGradient(0f to Color(0xFFFFC46B).copy(alpha = 0.075f), 1f to Color.Transparent, center = Offset(w * 0.86f, -h * 0.12f), radius = big * 0.8f),
            Brush.radialGradient(0f to Color(0xFF3FB8C4).copy(alpha = 0.10f), 1f to Color.Transparent, center = Offset(-w * 0.05f, h * 1.08f), radius = big * 0.75f),
        )
        // The projector's light: from the top left, and a glow along the floor.
        Backdrop.GRID -> listOf(
            Brush.radialGradient(0f to Color(0xFF1FB6E6).copy(alpha = 0.13f), 1f to Color.Transparent, center = Offset(w * 0.2f, -h * 0.1f), radius = big * 0.9f),
            Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color(0xFF1FB6E6).copy(alpha = 0.085f)),
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
    Backdrop.STARS -> stars(w, h)
    Backdrop.GRID -> grid(w, h)
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

/** Stars through the viewport, some brighter than others, and the fine arcs of a chart drawn over them. */
private fun Density.stars(w: Float, h: Float): DrawScope.() -> Unit {
    val unit = 1.dp.toPx()
    val random = Random(11)
    val count = ((w / unit) * (h / unit) / 5200f).toInt().coerceIn(40, 420)
    val all = List(count) {
        val bright = random.nextFloat()
        floatArrayOf(random.nextFloat() * w, random.nextFloat() * h, (0.45f + bright * bright * 0.9f) * unit, 0.10f + bright * bright * 0.5f, random.nextFloat())
    }
    val big = maxOf(w, h)
    return {
        for (s in all) drawCircle(if (s[4] > 0.8f) Color(0xFFFFE3B0) else Color(0xFFDDE8FF), radius = s[2], center = Offset(s[0], s[1]), alpha = s[3])
        val from = Offset(w * 1.04f, h * 1.14f)
        drawCircle(Color(0xFF6FD8D2), radius = big * 0.62f, center = from, alpha = 0.085f, style = Stroke(unit))
        drawCircle(Color(0xFF6FD8D2), radius = big * 0.82f, center = from, alpha = 0.055f, style = Stroke(unit))
        drawCircle(Color(0xFFFFC46B), radius = big * 0.44f, center = from, alpha = 0.07f, style = Stroke(unit))
    }
}

/** A projection grid: fine lines, every fourth a little stronger, with a mark where the strong ones cross. */
private fun Density.grid(w: Float, h: Float): DrawScope.() -> Unit {
    val cell = 30.dp.toPx()
    val hair = 1.dp.toPx().coerceAtLeast(1f)
    val arm = 3.5.dp.toPx()
    val cyan = Color(0xFF45DAFF)
    return {
        var i = 0
        var x = 0f
        while (x <= w) {
            drawRect(cyan, Offset(x, 0f), Size(hair, h), alpha = if (i % 4 == 0) 0.085f else 0.04f)
            x += cell; i++
        }
        i = 0
        var y = 0f
        while (y <= h) {
            drawRect(cyan, Offset(0f, y), Size(w, hair), alpha = if (i % 4 == 0) 0.085f else 0.04f)
            y += cell; i++
        }
        y = 0f
        while (y <= h) {
            x = 0f
            while (x <= w) {
                drawLine(cyan, Offset(x - arm, y + hair / 2), Offset(x + arm, y + hair / 2), strokeWidth = hair, alpha = 0.34f)
                drawLine(cyan, Offset(x + hair / 2, y - arm), Offset(x + hair / 2, y + arm), strokeWidth = hair, alpha = 0.34f)
                x += cell * 4
            }
            y += cell * 4
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
