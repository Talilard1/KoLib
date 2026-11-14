package dev.kobolibrary.app.ui.onboarding

import dev.kobolibrary.app.tr
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import dev.kobolibrary.app.ui.theme.KoLibGold
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The introduction's pictures: line drawings in the gold of KoLib's mark, glowing softly as the mark does
 * in the opening. Each is drawn on a 400 × 300 page and scaled to fit.
 */
private val Gold = Brush.linearGradient(listOf(KoLibGold.light, KoLibGold.gold, KoLibGold.deep), start = Offset(60f, 20f), end = Offset(340f, 290f))

private fun DrawScope.line(path: Path, width: Float = 3.2f, glow: Boolean = true, brush: Brush = Gold, alpha: Float = 1f) {
    if (glow) {
        for ((w, a) in listOf(width * 4.2f to 0.045f, width * 2.7f to 0.08f, width * 1.8f to 0.13f)) {
            drawPath(path, brush, alpha = a * alpha, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    drawPath(path, brush, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) = Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(l, t, r, b), CornerRadius(radius)))
}

private fun DrawScope.label(text: String, x: Float, y: Float, size: Float, color: Color = KoLibGold.light, bold: Boolean = false, left: Boolean = false) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        textSize = size
        textAlign = if (left) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.CENTER
        isFakeBoldText = bold
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** Draws [art] on a 400 × 300 page, scaled to fit and centred, behind a warm glow. */
@Composable
private fun Art(modifier: Modifier, art: DrawScope.() -> Unit) {
    Canvas(modifier) {
        val s = min(size.width / 400f, size.height / 300f)
        val dx = (size.width - 400f * s) / 2f
        val dy = (size.height - 300f * s) / 2f
        drawCircle(
            Brush.radialGradient(listOf(KoLibGold.ember.copy(alpha = 0.32f), Color.Transparent), center = center, radius = min(size.width, size.height) * 0.62f),
            radius = min(size.width, size.height) * 0.62f,
        )
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = Offset.Zero)
        }) { art() }
    }
}

/** A globe with words in other alphabets around it: choosing a language. */
@Composable
fun GlobeArt(modifier: Modifier = Modifier) = Art(modifier) {
    val c = Offset(200f, 156f)
    val r = 92f
    line(Path().apply { addOval(Rect(c, r)) })
    // Lines of longitude: ellipses narrower and narrower.
    for (k in listOf(0.38f, 0.75f)) line(Path().apply { addOval(Rect(c.x - r * k, c.y - r, c.x + r * k, c.y + r)) }, width = 2.2f)
    line(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x, c.y + r) }, width = 2.2f)
    // Lines of latitude.
    for (dy in listOf(-46f, 0f, 46f)) {
        val half = sqrt(r * r - dy * dy)
        line(Path().apply { moveTo(c.x - half, c.y + dy); lineTo(c.x + half, c.y + dy) }, width = 2.2f)
    }
    // Speech, in a few of the languages.
    fun bubble(l: Float, t: Float, w: Float, h: Float, tailLeft: Boolean, text: String) {
        val p = roundRect(l, t, l + w, t + h, 14f)
        val tx = if (tailLeft) l + 18f else l + w - 18f
        p.moveTo(tx - 8f, t + h)
        p.lineTo(tx + (if (tailLeft) -10f else 10f), t + h + 16f)
        p.lineTo(tx + 8f, t + h)
        line(p, width = 2.6f)
        label(text, l + w / 2, t + h / 2 + 11f, 30f)
    }
    bubble(272f, 42f, 82f, 52f, tailLeft = true, text = tr("Aa"))
    bubble(46f, 70f, 78f, 52f, tailLeft = false, text = "あ")
    bubble(286f, 200f, 72f, 48f, tailLeft = true, text = "Ç")
}

/** A folder of books, and the books going onto a shelf: adding a folder of books. */
@Composable
fun FolderArt(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "folder")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rise")
    Art(modifier) {
        // Books standing up out of the folder, behind its front.
        val spines = listOf(Triple(78f, 120f, 26f), Triple(108f, 104f, 22f), Triple(134f, 126f, 30f))
        for ((x, top, w) in spines) {
            line(roundRect(x, top, x + w, 200f, 3f), width = 2.4f)
            line(Path().apply { moveTo(x + 5f, top + 14f); lineTo(x + w - 5f, top + 14f) }, width = 1.6f, glow = false)
        }
        // The folder: its back with the tab, and its front.
        val back = Path().apply {
            moveTo(52f, 238f); lineTo(52f, 150f); lineTo(96f, 150f); lineTo(108f, 162f); lineTo(196f, 162f); lineTo(196f, 238f); close()
        }
        line(back, width = 2.6f)
        val front = Path().apply { moveTo(44f, 182f); lineTo(204f, 182f); lineTo(196f, 246f); lineTo(52f, 246f); close() }
        drawPath(front, KoLibGold.glow)
        line(front)
        // Dots from the folder to the shelf, one moving along them.
        val dots = Path().apply { moveTo(214f, 150f); cubicTo(236f, 106f, 268f, 98f, 296f, 112f) }
        drawPath(dots, Gold, alpha = 0.7f, style = Stroke(2.4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 10f))))
        val p = 1f - (1f - rise) * (1f - rise)
        val mt = 1 - p
        val x = mt * mt * mt * 214f + 3 * mt * mt * p * 236f + 3 * mt * p * p * 268f + p * p * p * 296f
        val y = mt * mt * mt * 150f + 3 * mt * mt * p * 106f + 3 * mt * p * p * 98f + p * p * p * 112f
        drawCircle(KoLibGold.spark, radius = 4f, center = Offset(x, y), alpha = 1f - rise * 0.6f)
        // The shelf, with books on it.
        line(Path().apply { moveTo(236f, 232f); lineTo(362f, 232f) }, width = 3.4f)
        val shelf = listOf(Triple(244f, 150f, 22f), Triple(268f, 162f, 18f), Triple(288f, 140f, 26f), Triple(316f, 158f, 20f))
        for ((bx, top, w) in shelf) line(roundRect(bx, top, bx + w, 232f, 3f), width = 2.4f)
        // One leaning on the last.
        val lean = Path().apply { moveTo(338f, 232f); lineTo(358f, 232f); lineTo(372f, 166f); lineTo(352f, 162f); close() }
        line(lean, width = 2.4f)
    }
}

/** This device and a Kobo, joined by a cable, with Connect on the Kobo's screen. */
@Composable
fun KoboArt(modifier: Modifier = Modifier, connected: Boolean = false) {
    val t = rememberInfiniteTransition(label = "kobo")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    Art(modifier) {
        // The tablet, on its side.
        line(roundRect(26f, 82f, 196f, 196f, 14f))
        line(roundRect(36f, 92f, 186f, 186f, 6f), width = 1.8f, glow = false, alpha = 0.6f)
        // The Kobo, upright, with its thicker foot.
        line(roundRect(254f, 54f, 360f, 228f, 12f))
        line(roundRect(264f, 64f, 350f, 196f, 4f), width = 1.8f, glow = false, alpha = 0.6f)
        // The cable, from the tablet's side to the Kobo's foot.
        val cable = Path().apply { moveTo(196f, 140f); cubicTo(230f, 140f, 222f, 262f, 262f, 262f); cubicTo(290f, 262f, 307f, 250f, 307f, 228f) }
        line(cable, width = 2.6f)
        // What the Kobo shows: Connect, or a tick once it's connected.
        if (connected) {
            line(Path().apply { addOval(Rect(Offset(307f, 130f), 28f)) })
            line(Path().apply { moveTo(294f, 131f); lineTo(303f, 141f); lineTo(321f, 120f) }, width = 3.6f)
        } else {
            line(roundRect(272f, 116f, 342f, 150f, 8f), width = 2.4f)
            label(tr("Connect"), 307f, 139f, 17f, bold = true)
            // Rings going out from it, as a tap.
            val ring = 10f + 26f * pulse
            drawCircle(KoLibGold.light, radius = ring, center = Offset(330f, 146f), alpha = (1f - pulse) * 0.6f, style = Stroke(2f))
        }
    }
}

/** A Kobo showing its collections, with New collection at the top: making a collection on the Kobo. */
@Composable
fun CollectionsArt(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "collections")
    val glow by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    Art(modifier) {
        line(roundRect(124f, 20f, 276f, 284f, 14f))
        line(roundRect(136f, 32f, 264f, 250f, 4f), width = 1.8f, glow = false, alpha = 0.6f)
        label(tr("Collections"), 144f, 58f, 12.5f, bold = true, left = true)
        // New collection, glowing.
        line(roundRect(220f, 45f, 257f, 63f, 9f), width = 2f, alpha = glow)
        label(tr("+ New"), 238.5f, 58.5f, 11f, color = KoLibGold.spark.copy(alpha = glow), bold = true)
        // The collections: a little stack of covers and a name.
        for ((i, y) in listOf(86f, 132f, 178f).withIndex()) {
            line(roundRect(148f, y, 172f, y + 32f, 2f), width = 2f, glow = false)
            line(roundRect(152f, y - 4f, 176f, y + 28f, 2f), width = 1.4f, glow = false, alpha = 0.6f)
            line(Path().apply { moveTo(186f, y + 12f); lineTo(186f + 54f - i * 10f, y + 12f) }, width = 2.6f, glow = false)
            line(Path().apply { moveTo(186f, y + 24f); lineTo(214f, y + 24f) }, width = 1.6f, glow = false, alpha = 0.6f)
        }
        // A book on its way into one, from beside the Kobo.
        line(roundRect(44f, 136f, 84f, 194f, 3f))
        line(Path().apply { moveTo(52f, 136f); lineTo(52f, 194f) }, width = 1.8f, glow = false)
        val arrow = Path().apply { moveTo(94f, 160f); cubicTo(112f, 150f, 124f, 146f, 140f, 146f) }
        drawPath(arrow, Gold, alpha = 0.75f, style = Stroke(2.4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 9f))))
    }
}

/** KoLib's open book over a shelf of books: ready to go. */
@Composable
fun ShelfArt(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shelf")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
    Art(modifier) {
        // The mark, large.
        val book = Path().apply {
            moveTo(200f, 70f); cubicTo(178f, 52f, 146f, 48f, 118f, 54f); lineTo(118f, 168f); cubicTo(146f, 162f, 178f, 166f, 200f, 182f)
            moveTo(200f, 70f); cubicTo(222f, 52f, 254f, 48f, 282f, 54f); lineTo(282f, 168f); cubicTo(254f, 162f, 222f, 166f, 200f, 182f)
        }
        line(book, width = 4f)
        line(Path().apply { moveTo(238f, 50f); lineTo(238f, 98f); lineTo(249f, 89f); lineTo(260f, 98f); lineTo(260f, 50f) }, width = 3.2f)
        // The shelf.
        line(Path().apply { moveTo(70f, 262f); lineTo(330f, 262f) }, width = 3.4f)
        val books = listOf(Triple(86f, 212f, 20f), Triple(108f, 202f, 16f), Triple(126f, 218f, 24f), Triple(154f, 206f, 18f), Triple(236f, 210f, 22f), Triple(260f, 200f, 16f), Triple(278f, 214f, 26f))
        for ((x, top, w) in books) line(roundRect(x, top, x + w, 262f, 3f), width = 2.2f)
        line(Path().apply { moveTo(306f, 262f); lineTo(324f, 262f); lineTo(334f, 214f); lineTo(316f, 210f); close() }, width = 2.2f)
        // Sparks rising from the open book.
        for ((i, x) in listOf(170f, 204f, 236f).withIndex()) {
            val p = (twinkle + i / 3f) % 1f
            drawCircle(KoLibGold.spark, radius = 3.2f * (1f - p * 0.5f), center = Offset(x + (i - 1) * 8f * p, 46f - p * 34f), alpha = (1f - p) * 0.9f)
        }
    }
}
