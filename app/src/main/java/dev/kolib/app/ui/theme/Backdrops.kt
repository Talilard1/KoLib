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
        // Cool light over the hull from above, and the amber of a console's lamps low at one side.
        Backdrop.HULL -> listOf(
            Brush.radialGradient(0f to Color(0xFF6E9BFF).copy(alpha = 0.09f), 1f to Color.Transparent, center = Offset(w * 0.2f, -h * 0.1f), radius = big * 0.85f),
            Brush.radialGradient(0f to Color(0xFFFFB866).copy(alpha = 0.07f), 1f to Color.Transparent, center = Offset(w * 1.02f, h * 1.1f), radius = big * 0.65f),
        )
        // The projector's light, from below; and a little from above, where the picture fades.
        Backdrop.MESH -> listOf(
            Brush.radialGradient(0f to Color(0xFF3A9BFF).copy(alpha = 0.15f), 1f to Color.Transparent, center = Offset(w * 0.5f, h * 1.1f), radius = big * 0.85f),
            Brush.radialGradient(0f to Color(0xFF7CCBFF).copy(alpha = 0.06f), 1f to Color.Transparent, center = Offset(w * 0.15f, -h * 0.1f), radius = big * 0.8f),
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
    Backdrop.HULL -> hull(w, h)
    Backdrop.MESH -> mesh(w, h)
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
 * A ship's inner hull: panels in staggered rows with two corners cut off, the seams between them faint; a panel here
 * and there catching the light, and on some a small lit slot, most ice blue, a few amber.
 */
private fun Density.hull(w: Float, h: Float): DrawScope.() -> Unit {
    val across = 116.dp.toPx()
    val down = 70.dp.toPx()
    val gap = 5.dp.toPx()
    val cut = 9.dp.toPx()
    val seams = Path()
    val lit = Path()
    val slots = ArrayList<Pair<Rect, Color>>()
    val random = Random(7)
    var row = 0
    var y = 0f
    while (y < h) {
        var x = if (row % 2 == 0) 0f else -across / 2
        while (x < w) {
            val l = x + gap / 2
            val t = y + gap / 2
            val r = x + across - gap / 2
            val b = y + down - gap / 2
            fun panel(p: Path) {
                p.moveTo(l + cut, t); p.lineTo(r, t); p.lineTo(r, b - cut); p.lineTo(r - cut, b); p.lineTo(l, b); p.lineTo(l, t + cut); p.close()
            }
            panel(seams)
            val roll = random.nextFloat()
            if (roll < 0.06f) {
                panel(lit)
            } else if (roll < 0.14f) {
                val sw = 16.dp.toPx()
                val sx = if (random.nextBoolean()) l + 12.dp.toPx() else r - 12.dp.toPx() - sw
                val sy = b - 11.dp.toPx()
                slots += Rect(sx, sy, sx + sw, sy + 2.dp.toPx()) to if (random.nextFloat() < 0.3f) Color(0xFFFFB866) else Color(0xFF8FC2FF)
            }
            x += across
        }
        y += down
        row++
    }
    val seam = 1.dp.toPx()
    return {
        drawPath(lit, Color(0xFF9DB7E0), alpha = 0.035f)
        drawPath(seams, Color(0xFF9DB7E0), alpha = 0.075f, style = Stroke(seam))
        for ((slot, colour) in slots) drawRoundRect(colour, slot.topLeft, slot.size, CornerRadius(slot.height / 2), alpha = 0.18f)
    }
}

/**
 * A mesh of light: fine lines in three directions making triangles, a point of light where they cross, and a
 * triangle here and there lit faintly, as if the picture were still being drawn.
 */
private fun Density.mesh(w: Float, h: Float): DrawScope.() -> Unit {
    val side = 36.dp.toPx()
    val rise = side * sqrt(3f) / 2
    val slope = rise / (side / 2)
    val lines = Path()
    val lit = Path()
    val points = Path()
    val random = Random(13)
    // Across, and the two slants, each through every point of the lattice.
    var y = 0f
    while (y <= h) {
        lines.moveTo(0f, y); lines.lineTo(w, y)
        y += rise
    }
    val reach = h / slope
    var x = -reach - side
    while (x <= w + reach + side) {
        lines.moveTo(x, 0f); lines.lineTo(x + reach, h)
        lines.moveTo(x, 0f); lines.lineTo(x - reach, h)
        x += side
    }
    val dot = 1.1.dp.toPx()
    var j = 0
    while (j * rise <= h + rise) {
        val top = j * rise
        val shift = if (j % 2 == 0) 0f else side / 2
        var i = -1
        while (i * side + shift <= w + side) {
            val left = i * side + shift
            points.addOval(androidx.compose.ui.geometry.Rect(left - dot, top - dot, left + dot, top + dot))
            if (random.nextFloat() < 0.045f) {
                // The triangle pointing down from this row to the next.
                lit.moveTo(left, top); lit.lineTo(left + side, top); lit.lineTo(left + side / 2, top + rise); lit.close()
            }
            i++
        }
        j++
    }
    val hair = 1.dp.toPx()
    val blue = Color(0xFF7CCBFF)
    return {
        drawPath(lit, blue, alpha = 0.03f)
        drawPath(lines, blue, alpha = 0.055f, style = Stroke(hair))
        drawPath(points, blue, alpha = 0.16f)
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
