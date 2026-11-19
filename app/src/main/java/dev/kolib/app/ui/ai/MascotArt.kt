package dev.kolib.app.ui.ai

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Shared ways of drawing the characters' parts. Each character's box is s across; points are given as fractions of it.

private fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)
private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)
private fun Path.quadTo(c: Offset, p: Offset) = quadraticTo(c.x, c.y, p.x, p.y)
private fun Path.cubicTo(a: Offset, b: Offset, p: Offset) = cubicTo(a.x, a.y, b.x, b.y, p.x, p.y)

private fun DrawScope.shape(path: Path, fill: Color, line: Color, width: Float) {
    drawPath(path, fill)
    drawPath(path, line, style = Stroke(width, join = StrokeJoin.Round))
}

private fun DrawScope.shape(path: Path, fill: Brush, line: Color, width: Float) {
    drawPath(path, fill)
    drawPath(path, line, style = Stroke(width, join = StrokeJoin.Round))
}

private fun cubicAt(a: Offset, b: Offset, c: Offset, d: Offset, t: Float): Offset {
    val u = 1f - t
    return a * (u * u * u) + b * (3f * u * u * t) + c * (3f * u * t * t) + d * (t * t * t)
}

/** Draws [block] with the [region] of the box (as fractions) blown up to fill it: for an icon of the head. */
private inline fun DrawScope.fit(s: Float, region: Rect, block: DrawScope.() -> Unit) {
    val k = min(1f / region.width, 1f / region.height)
    val dx = (1f - region.width * k) / 2f - region.left * k
    val dy = (1f - region.height * k) / 2f - region.top * k
    withTransform({
        translate(dx * s, dy * s)
        scale(k, k, pivot = Offset.Zero)
    }) { block() }
}

/** A limb from [from] to [to], bending outwards ([side]: −1 left, 1 right), as a soft tube. */
private fun DrawScope.limb(from: Offset, to: Offset, side: Int, s: Float, fill: Color, line: Color, width: Float, bend: Float = 0.035f) {
    val mid = mix(from, to, 0.5f) + Offset(side * bend * s, 0.01f * s)
    val p = Path().apply {
        moveTo(from)
        quadTo(mid, to)
    }
    drawPath(p, line, style = Stroke(width + 0.022f * s, cap = StrokeCap.Round))
    drawPath(p, fill, style = Stroke(width, cap = StrokeCap.Round))
}

/** Where the hands go: at rest, and for each thing it might be doing, as [m] says how much of each. */
private class Poses(
    val rest: Pair<Offset, Offset>,
    val sad: Pair<Offset, Offset>,
    val book: Pair<Offset, Offset>,
    val note: Pair<Offset, Offset>,
    val chin: Offset,
    val wave: Offset,
    val cheer: Pair<Offset, Offset>,
    val dangle: Pair<Offset, Offset>,
    val special: Offset? = null,
)

private fun hands(m: MascotMotion, p: Poses, s: Float): Pair<Offset, Offset> {
    var l = p.rest.first
    var r = p.rest.second
    fun to(t: Pair<Offset, Offset>, w: Float) {
        l = mix(l, t.first, w)
        r = mix(r, t.second, w)
    }
    to(p.sad, m.sad)
    to(p.book, m.book)
    to(p.note, m.note)
    r = mix(r, p.chin, m.chin)
    r = mix(r, p.wave, m.wave)
    to(p.cheer, m.cheer)
    to(p.dangle, m.dangle)
    p.special?.let { r = mix(r, it, (m.special * 3f).coerceAtMost(1f) * (1f - ((m.special - 0.8f) * 5f).coerceIn(0f, 1f))) }
    return Offset(l.x * s, l.y * s) to Offset(r.x * s, r.y * s)
}

/** Closed eyes, happy (an upturned arc) or asleep (a gentle curve down). */
private fun DrawScope.closedEye(c: Offset, w: Float, s: Float, happy: Boolean, line: Color, width: Float) {
    val p = Path().apply {
        if (happy) {
            moveTo(c.x - w / 2, c.y + 0.012f * s)
            quadraticTo(c.x, c.y - 0.04f * s, c.x + w / 2, c.y + 0.012f * s)
        } else {
            moveTo(c.x - w / 2, c.y - 0.004f * s)
            quadraticTo(c.x, c.y + 0.02f * s, c.x + w / 2, c.y - 0.004f * s)
        }
    }
    drawPath(p, line, style = Stroke(width, cap = StrokeCap.Round))
}

/** A spiral, for dizzy eyes. */
private fun DrawScope.spiral(c: Offset, r: Float, turn: Float, line: Color, width: Float) {
    val p = Path()
    val n = 28
    for (i in 0..n) {
        val f = i / n.toFloat()
        val ang = turn + f * 4f * PI.toFloat()
        val rr = r * f
        val x = c.x + cos(ang) * rr
        val y = c.y + sin(ang) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, line, style = Stroke(width, cap = StrokeCap.Round))
}

/** Eyes squeezed shut: > and <. */
private fun DrawScope.squeezed(c: Offset, w: Float, side: Int, line: Color, width: Float) {
    val p = Path().apply {
        moveTo(c.x - side * w / 2, c.y - w * 0.4f)
        lineTo(c.x + side * w / 2, c.y)
        lineTo(c.x - side * w / 2, c.y + w * 0.4f)
    }
    drawPath(p, line, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Which way an eye should go, by what the face is doing: the shape it takes. */
private enum class EyeLook { OPEN, HAPPY, SHUT, SPIRAL, SQUEEZED }

private fun eyeLook(m: MascotMotion, right: Boolean): EyeLook = when {
    m.act == Act.BUMPED -> EyeLook.SQUEEZED
    m.swirl > 0.5f -> EyeLook.SPIRAL
    m.joy > 0.55f || (right && m.wink > 0.5f) -> EyeLook.HAPPY
    m.open < 0.18f -> EyeLook.SHUT
    else -> EyeLook.OPEN
}

/** Tears, when it's sad. */
private fun DrawScope.tear(c: Offset, s: Float, t: Float) {
    val slide = ((t * 0.5f) % 1f) * 0.06f * s
    val d = dropPath(c + Offset(0f, slide), 0.016f * s)
    drawPath(d, Color(0xFF8FD8FF))
    drawPath(d, Color.White, style = Stroke(0.005f * s))
}

private fun DrawScope.blush(c: Offset, s: Float, w: Float, strength: Float, hatch: Boolean) {
    if (strength <= 0.02f) return
    drawOval(Color(0xFFFF8FA8).copy(alpha = strength.coerceAtMost(0.75f)), topLeft = Offset(c.x - w / 2, c.y - w * 0.2f), size = Size(w, w * 0.4f))
    if (hatch) for (i in -1..1) {
        val x = c.x + i * w * 0.25f
        drawLine(Color(0xFFE0507A).copy(alpha = strength), Offset(x + w * 0.06f, c.y - w * 0.12f), Offset(x - w * 0.06f, c.y + w * 0.12f), strokeWidth = 0.006f * s, cap = StrokeCap.Round)
    }
}

/** Where a hand holds the pencil, from its point. */
private val GRIP = Offset(0.045f, -0.032f)

/** What it holds: an open book (a pile, in the library), or a notepad and pencil. */
private fun DrawScope.held(m: MascotMotion, s: Float, bookAt: Offset, bookW: Float, padAt: Offset, padW: Float, cover: Color, line: Color) {
    if (m.book > 0.05f) {
        val c = Offset(bookAt.x * s, bookAt.y * s + (1f - m.book) * 0.12f * s)
        if (m.library) bookPile(c, bookW * s * m.book.coerceAtLeast(0.4f), line)
        else openBook(c, bookW * s * m.book.coerceAtLeast(0.4f), m.t, cover, line, m.act == Act.READING)
    }
    if (m.note > 0.05f) {
        val c = Offset(padAt.x * s, padAt.y * s + (1f - m.note) * 0.12f * s)
        notepad(c, padW * s, m.t, line)
        pencil(pencilTip(c, padW * s, m.t), 0.22f * s, line)
    }
}

// =================================================================================================================
// Bolt: a little robot, floating on a thruster, with a screen for a face and a light on its aerial.

internal object RobotArt : MascotArt {
    private val shell = Color(0xFFF5F3EE)
    private val shellShade = Color(0xFFD7D1C6)
    private val line = Color(0xFF2E3442)
    private val screen = Color(0xFF142030)
    private val led = Color(0xFF72F1FF)
    private val amber = Color(0xFFFFB23E)
    private val coral = Color(0xFFFF6B7D)
    private val mint = Color(0xFF6FF0A6)
    private val pink = Color(0xFFFF8FB8)
    override val head = Offset(0.5f, 0.34f)
    override val headR = 0.27f

    private fun light(m: MascotMotion) = when (m.act) {
        Act.THINKING -> amber
        Act.WRITING, Act.READING -> mint
        Act.HAPPY, Act.HELLO -> pink
        Act.SAD, Act.SCARED -> Color(0xFF7FA8FF)
        Act.SLEEPING -> Color(0xFF7A8090)
        else -> coral
    }

    override fun DrawScope.figure(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.02f * s
        // Its thruster, blazing as it flies, low as it sleeps.
        val flicker = 0.82f + 0.18f * sin(m.t * 41f) * sin(m.t * 27f)
        val power = ((0.3f + 0.7f * m.hover) + (if (m.act == Act.FLYING) 0.9f else 0f) + m.hop * 0.8f) * flicker
        glow(p(0.5f, 0.885f), 0.16f * s * (0.7f + 0.3f * min(power, 1.6f)), led.copy(alpha = 0.75f), min(0.35f + 0.4f * power, 1f))
        val flame = Path().apply {
            moveTo(p(0.44f, 0.845f))
            quadTo(p(0.5f, 0.845f + 0.085f * power), p(0.56f, 0.845f))
            close()
        }
        drawPath(flame, led.copy(alpha = 0.85f))
        val core = Path().apply {
            moveTo(p(0.47f, 0.845f))
            quadTo(p(0.5f, 0.845f + 0.045f * power), p(0.53f, 0.845f))
            close()
        }
        drawPath(core, Color.White)

        // Its body, with a light on its chest that shows what it's doing.
        val body = Rect(p(0.335f, 0.585f), p(0.665f, 0.855f))
        drawRoundRect(Brush.verticalGradient(listOf(shell, shellShade), body.top, body.bottom), body.topLeft, body.size, CornerRadius(0.085f * s))
        drawRoundRect(line, body.topLeft, body.size, CornerRadius(0.085f * s), style = Stroke(o))
        drawLine(line.copy(alpha = 0.25f), p(0.36f, 0.78f), p(0.64f, 0.78f), strokeWidth = 0.008f * s)
        val chest = p(0.5f, 0.685f)
        val pulse = 0.5f + 0.5f * sin(m.t * (if (m.act == Act.THINKING) 9f else 3f))
        glow(chest, 0.075f * s, light(m), 0.3f + 0.5f * pulse)
        drawCircle(light(m), 0.032f * s, chest)
        drawCircle(line, 0.032f * s, chest, style = Stroke(o * 0.8f))
        drawCircle(Color.White.copy(alpha = 0.8f), 0.01f * s, chest + Offset(-0.01f * s, -0.01f * s))

        // Bendy arms, and what it holds.
        val typing = if (m.note > 0.1f) sin(m.t * 22f) * 0.012f else 0f
        val (lh, rh) = hands(m, Poses(
            rest = Offset(0.27f, 0.79f + 0.01f * sin(m.t * 2.3f)) to Offset(0.73f, 0.79f + 0.01f * sin(m.t * 2.3f + 1f)),
            sad = Offset(0.4f, 0.84f) to Offset(0.6f, 0.84f),
            book = Offset(0.33f, 0.74f) to Offset(0.67f, 0.74f),
            note = Offset(0.41f, 0.8f + typing) to Offset(0.59f, 0.8f - typing),
            chin = Offset(0.585f, 0.51f),
            wave = Offset(0.8f + 0.03f * sin(m.t * 13f), 0.42f),
            cheer = Offset(0.21f, 0.42f + 0.02f * sin(m.t * 10f)) to Offset(0.79f, 0.42f + 0.02f * sin(m.t * 10f + 1.5f)),
            dangle = Offset(0.22f + 0.04f * sin(m.t * 11f), 0.4f) to Offset(0.78f + 0.04f * sin(m.t * 11f + 2f), 0.4f),
        ), s)
        limb(p(0.34f, 0.635f), lh, -1, s, shell, line, 0.045f * s)
        limb(p(0.66f, 0.635f), rh, 1, s, shell, line, 0.045f * s)
        if (m.book > 0.05f) {
            // A beam from its screen, reading the page.
            val a = 0.2f * m.book
            val beam = Path().apply {
                moveTo(p(0.41f, 0.48f))
                lineTo(p(0.59f, 0.48f))
                lineTo(p(0.67f, 0.7f))
                lineTo(p(0.33f, 0.7f))
                close()
            }
            drawPath(beam, Brush.verticalGradient(listOf(led.copy(alpha = a * 1.4f), led.copy(alpha = a * 0.3f)), 0.48f * s, 0.7f * s))
        }
        if (m.note > 0.05f) keyboard(m, s) else held(m, s, Offset(0.5f, 0.735f), 0.36f, Offset(0.5f, 0.8f), 0.22f, Color(0xFF3D7BD9), line)
        if (m.book > 0.5f && !m.library) {
            val y = (0.69f + 0.09f * ((m.t * 0.8f) % 1f)) * s
            drawLine(led.copy(alpha = 0.8f * m.book), Offset(0.35f * s, y), Offset(0.65f * s, y), strokeWidth = 0.008f * s)
        }
        for (h in listOf(lh, rh)) {
            drawCircle(shellShade, 0.034f * s, h)
            drawCircle(line, 0.034f * s, h, style = Stroke(o * 0.8f))
        }
        drawHead(m, s)
    }

    /** A keyboard of light, as it types; letters flying off it. */
    private fun DrawScope.keyboard(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val a = m.note
        val board = Path().apply {
            moveTo(p(0.34f, 0.765f))
            lineTo(p(0.66f, 0.765f))
            lineTo(p(0.71f, 0.835f))
            lineTo(p(0.29f, 0.835f))
            close()
        }
        drawPath(board, led.copy(alpha = 0.22f * a))
        drawPath(board, led.copy(alpha = 0.85f * a), style = Stroke(0.008f * s, join = StrokeJoin.Round))
        val tick = (m.t * 12f).toInt()
        for (row in 0..2) for (col in 0..6) {
            val f = row / 2f
            val x = mix(0.36f + 0.0f, 0.31f, f) + col * mix(0.047f, 0.063f, f)
            val y = 0.78f + row * 0.022f
            val lit = (col * 7 + row * 13 + tick * 5) % 11 == 0
            drawCircle(if (lit) Color.White else led.copy(alpha = 0.6f), (if (lit) 0.009f else 0.006f) * s * a, p(x, y))
        }
        for (i in 0..2) {
            val phase = ((m.t * 0.9f + i / 3f) % 1f)
            val c = p(0.5f + (i - 1) * 0.09f - phase * 0.05f, 0.75f - phase * 0.28f)
            drawRect(led.copy(alpha = a * (1f - phase)), Offset(c.x - 0.012f * s, c.y - 0.012f * s), Size(0.024f * s, 0.024f * s))
        }
    }

    private fun DrawScope.drawHead(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.02f * s
        // The aerial, swaying, drooping when it's sad or asleep; a heart on it when it's happy.
        val droop = max(m.sleepy, m.sad)
        val tip = p(0.5f + 0.022f * sin(m.t * 2.7f) - m.lean * 0.05f + droop * 0.06f, 0.045f + droop * 0.05f)
        val stem = Path().apply {
            moveTo(p(0.5f, 0.13f))
            quadTo(p(0.5f, 0.08f), tip)
        }
        drawPath(stem, line, style = Stroke(0.016f * s, cap = StrokeCap.Round))
        val bulb = light(m)
        val blinkOn = m.act != Act.THINKING || (m.t * 3f) % 1f < 0.55f
        glow(tip, 0.07f * s, bulb, if (blinkOn) 0.7f else 0.15f)
        if (m.act == Act.HAPPY || m.act == Act.HELLO) {
            val heart = heartPath(tip, 0.034f * s)
            drawPath(heart, pink)
            drawPath(heart, line, style = Stroke(o * 0.7f))
        } else {
            drawCircle(if (blinkOn) bulb else bulb.copy(alpha = 0.45f), 0.032f * s, tip)
            drawCircle(line, 0.032f * s, tip, style = Stroke(o * 0.7f))
            drawCircle(Color.White.copy(alpha = 0.8f), 0.009f * s, tip + Offset(-0.01f * s, -0.01f * s))
        }
        // A spark off it, now and then, when it fidgets.
        if (m.special > 0.15f && m.special < 0.45f) {
            val z = Path().apply {
                moveTo(tip + Offset(0.03f * s, -0.01f * s))
                lineTo(tip + Offset(0.06f * s, -0.03f * s))
                lineTo(tip + Offset(0.05f * s, 0f))
                lineTo(tip + Offset(0.085f * s, -0.02f * s))
            }
            drawPath(z, amber, style = Stroke(0.012f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Ears.
        for (x in floatArrayOf(0.2f, 0.8f)) {
            drawCircle(amber, 0.047f * s, p(x, 0.35f))
            drawCircle(line, 0.047f * s, p(x, 0.35f), style = Stroke(o * 0.8f))
            drawCircle(shellShade, 0.019f * s, p(x, 0.35f))
        }
        // The head, and its screen.
        val head = Rect(p(0.205f, 0.125f), p(0.795f, 0.56f))
        drawRoundRect(Brush.verticalGradient(listOf(shell, shell, shellShade), head.top, head.bottom), head.topLeft, head.size, CornerRadius(0.14f * s))
        drawRoundRect(line, head.topLeft, head.size, CornerRadius(0.14f * s), style = Stroke(o))
        val glass = Rect(p(0.265f, 0.2f), p(0.735f, 0.485f))
        val dim = if (m.act == Act.SLEEPING) 0.55f + 0.1f * sin(m.t * 1.5f) else 1f
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF223450), screen), glass.top, glass.bottom), glass.topLeft, glass.size, CornerRadius(0.085f * s))
        clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(glass, CornerRadius(0.085f * s))) }) {
            var y = glass.top
            while (y < glass.bottom) {
                drawLine(Color.White.copy(alpha = 0.035f), Offset(glass.left, y), Offset(glass.right, y), strokeWidth = 0.004f * s)
                y += 0.017f * s
            }
            // A glint across the glass, saying hello or for joy.
            if (m.act == Act.HELLO || m.act == Act.HAPPY) {
                val g = ((m.actT % 1.6f) / 0.6f).coerceAtMost(1.2f)
                val x = glass.left - 0.15f * s + g * (glass.width + 0.3f * s)
                val band = Path().apply {
                    moveTo(x, glass.top)
                    lineTo(x + 0.06f * s, glass.top)
                    lineTo(x - 0.04f * s, glass.bottom)
                    lineTo(x - 0.1f * s, glass.bottom)
                    close()
                }
                drawPath(band, Color.White.copy(alpha = 0.18f))
            }
            face(m, s, dim)
        }
        drawRoundRect(line, glass.topLeft, glass.size, CornerRadius(0.085f * s), style = Stroke(o * 0.8f))
        drawRoundRect(Color.White.copy(alpha = 0.1f), Offset(glass.left + 0.03f * s, glass.top + 0.02f * s), Size(0.12f * s, 0.025f * s), CornerRadius(0.012f * s))
    }

    /** Its face, in light on the screen. */
    private fun DrawScope.face(m: MascotMotion, s: Float, dim: Float) {
        val c = led.copy(alpha = dim)
        val w = 0.022f * s
        for (side in intArrayOf(-1, 1)) {
            val e = Offset((0.5f + side * 0.095f + m.lookX * 0.028f) * s, (0.335f + m.lookY * 0.022f) * s)
            when (val look = eyeLook(m, side == 1)) {
                EyeLook.HAPPY -> closedEye(e, 0.075f * s, s, true, c, w)
                EyeLook.SHUT -> drawLine(c, e - Offset(0.035f * s, 0f), e + Offset(0.035f * s, 0f), strokeWidth = w, cap = StrokeCap.Round)
                EyeLook.SPIRAL -> spiral(e, 0.045f * s, m.t * 6f * side, c, 0.012f * s)
                EyeLook.SQUEEZED -> squeezed(e, 0.06f * s, side, c, w)
                EyeLook.OPEN -> {
                    if (m.special in 0.5f..0.85f) {
                        // Its fidget: a flicker of hearts in its eyes.
                        val heart = heartPath(e, 0.036f * s)
                        drawPath(heart, pink.copy(alpha = dim))
                    } else {
                        val big = m.wide
                        val ew = mix(0.068f, 0.09f, big) * s
                        val eh = mix(0.098f, 0.09f, big) * s * m.open.coerceAtLeast(0.14f) * (1f - 0.35f * m.sad)
                        glow(e, ew * 1.2f, c, 0.35f)
                        if (m.sad > 0.3f) rotate(-side * 14f * m.sad, e) {
                            drawRoundRect(c, Offset(e.x - ew / 2, e.y - eh / 2), Size(ew, eh), CornerRadius(min(ew, eh) / 2))
                        } else drawRoundRect(c, Offset(e.x - ew / 2, e.y - eh / 2), Size(ew, eh), CornerRadius(min(ew, eh) / 2))
                        if (look == EyeLook.OPEN && big < 0.5f) drawCircle(Color.White.copy(alpha = 0.7f * dim), 0.011f * s, e + Offset(-ew * 0.2f, -eh * 0.25f))
                    }
                }
            }
        }
        if (m.sad > 0.3f) tear(Offset(0.405f * s, 0.4f * s), s, m.t)
        // The mouth.
        val mc = Offset((0.5f + m.lookX * 0.01f) * s, 0.43f * s)
        val open = max(m.talk, m.yawn)
        when {
            open > 0.08f -> {
                val h = (0.012f + 0.045f * open) * s
                drawRoundRect(c, Offset(mc.x - 0.028f * s, mc.y - h / 2), Size(0.056f * s, h), CornerRadius(min(h, 0.028f * s) / 2))
            }
            m.wide > 0.4f -> drawCircle(c, (0.012f + 0.01f * m.wide) * s, mc, style = Stroke(0.012f * s))
            m.sad > 0.3f -> drawPath(Path().apply {
                moveTo(mc.x - 0.035f * s, mc.y + 0.012f * s)
                quadraticTo(mc.x, mc.y - 0.018f * s, mc.x + 0.035f * s, mc.y + 0.012f * s)
            }, c, style = Stroke(0.016f * s, cap = StrokeCap.Round))
            else -> drawPath(Path().apply {
                val bend = (0.012f + 0.03f * max(m.joy, 0.3f)) * s
                moveTo(mc.x - 0.04f * s, mc.y - 0.004f * s)
                quadraticTo(mc.x, mc.y + bend, mc.x + 0.04f * s, mc.y - 0.004f * s)
            }, c, style = Stroke(0.016f * s, cap = StrokeCap.Round))
        }
        // Rosy lights for cheeks.
        val rosy = max(max(m.joy * 0.9f, m.dangle * 0.7f), m.wink)
        if (rosy > 0.05f) for (side in intArrayOf(-1, 1)) for (i in 0..2) {
            val x = (0.5f + side * 0.165f + (i - 1) * 0.02f) * s
            drawLine(pink.copy(alpha = rosy * dim), Offset(x + 0.008f * s, 0.395f * s), Offset(x - 0.008f * s, 0.415f * s), strokeWidth = 0.008f * s, cap = StrokeCap.Round)
        }
    }

    override fun DrawScope.icon(m: MascotMotion, s: Float) = fit(s, Rect(0.15f, 0.02f, 0.85f, 0.58f)) { drawHead(m, s) }

    /** Gears turning over its head. */
    override fun DrawScope.thinking(m: MascotMotion, s: Float) {
        gear(Offset(0.83f * s, 0.12f * s), 0.065f * s, 8, m.t * 2.2f, amber, line)
        gear(Offset(0.935f * s, 0.035f * s), 0.042f * s, 6, -m.t * 3.4f, shell, line)
    }
}

// =================================================================================================================
// Yume: a glamorous woman, long wavy blonde hair swept to one side, green eyes under heavy lashes, red lips; an
// off-the-shoulder wine-red dress, gold hoops and a pendant, a red rose in her hair. Cat-eye glasses when she reads.

internal object AnimeArt : MascotArt {
    private val hair = Color(0xFFF2C14E)
    private val hairShade = Color(0xFFD69A2D)
    private val hairDeep = Color(0xFFB27420)
    private val sheen = Color(0xFFFFF1BE)
    private val skin = Color(0xFFFFE7DA)
    private val skinShade = Color(0xFFF0BFA8)
    private val line = Color(0xFF3A2418)
    private val irisTop = Color(0xFF145A30)
    private val irisLow = Color(0xFF6FDB98)
    private val pupil = Color(0xFF0A2414)
    private val shadow = Color(0xFFD9818F)
    private val lips = Color(0xFFD2283F)
    private val lipsDark = Color(0xFF9E1229)
    private val dress = Color(0xFF8E1E3A)
    private val dressShade = Color(0xFF64122A)
    private val gold = Color(0xFFFFCB52)
    private val rose = Color(0xFFD81E3C)
    private val leaf = Color(0xFF3E8E4E)
    override val head = Offset(0.5f, 0.266f)
    override val headR = 0.19f

    /** Her head, drawn a little larger than her figure's proportions, so her face reads in a small bubble. */
    private const val HEAD = 1.14f

    private fun sway(m: MascotMotion, phase: Float = 0f) =
        0.01f * sin(m.t * 1.4f + phase) + m.lean * 0.05f + m.dangle * 0.025f * sin(m.t * 9f + phase) + m.hop * 0.015f

    /** Her hips' sway as she stands, slow. */
    private fun hips(m: MascotMotion) = 0.008f * sin(m.t * 1.3f) * m.hover

    override fun DrawScope.figure(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.011f * s
        val hp = hips(m)
        val neck = p(0.5f, 0.45f)
        withTransform({ scale(HEAD, HEAD, pivot = neck) }) { backHair(m, s) }
        // Her neck, and her bare shoulders.
        drawRect(skin, p(0.476f, 0.4f), Size(0.048f * s, 0.15f * s))
        drawRect(skinShade, p(0.476f, 0.42f), Size(0.048f * s, 0.03f * s))
        val shoulders = Path().apply {
            moveTo(p(0.476f, 0.5f))
            cubicTo(p(0.445f, 0.53f), p(0.395f, 0.535f), p(0.37f, 0.553f))
            quadTo(p(0.353f, 0.565f), p(0.355f, 0.6f))
            lineTo(p(0.645f, 0.6f))
            quadTo(p(0.647f, 0.565f), p(0.63f, 0.553f))
            cubicTo(p(0.605f, 0.535f), p(0.555f, 0.53f), p(0.524f, 0.5f))
            close()
        }
        shape(shoulders, skin, line, o)
        drawRect(skin, p(0.477f, 0.47f), Size(0.046f * s, 0.06f * s))
        drawLine(line, p(0.476f, 0.43f), p(0.476f, 0.5f), strokeWidth = o * 0.8f)
        drawLine(line, p(0.524f, 0.43f), p(0.524f, 0.5f), strokeWidth = o * 0.8f)
        for (side in intArrayOf(-1, 1)) {
            drawPath(Path().apply {
                moveTo(p(0.5f + side * 0.075f, 0.552f))
                quadTo(p(0.5f + side * 0.045f, 0.548f), p(0.5f + side * 0.018f, 0.556f))
            }, skinShade, style = Stroke(0.005f * s, cap = StrokeCap.Round))
        }
        // A gold chain and its pendant.
        drawPath(Path().apply {
            moveTo(p(0.468f, 0.505f))
            quadTo(p(0.48f, 0.55f), p(0.5f, 0.56f))
            quadTo(p(0.52f, 0.55f), p(0.532f, 0.505f))
        }, gold, style = Stroke(0.004f * s))
        val gem = dropPath(p(0.5f, 0.567f), 0.008f * s)
        shape(gem, rose, line, 0.003f * s)

        // Her dress: off the shoulder, fitted, with a slit at the side.
        val body = Path().apply {
            moveTo(p(0.355f, 0.592f))
            quadTo(p(0.43f, 0.58f), p(0.5f, 0.598f))
            quadTo(p(0.57f, 0.58f), p(0.645f, 0.592f))
            cubicTo(p(0.652f, 0.635f), p(0.622f, 0.675f), p(0.585f + hp, 0.71f))
            cubicTo(p(0.632f + hp, 0.75f), p(0.642f + hp, 0.79f), p(0.638f + hp, 0.83f))
            lineTo(p(0.655f + hp, 0.95f))
            quadTo(p(0.5f + hp, 0.965f), p(0.345f + hp, 0.95f))
            lineTo(p(0.362f + hp, 0.83f))
            cubicTo(p(0.358f + hp, 0.79f), p(0.368f + hp, 0.75f), p(0.415f + hp, 0.71f))
            cubicTo(p(0.378f, 0.675f), p(0.348f, 0.635f), p(0.355f, 0.592f))
            close()
        }
        shape(body, Brush.verticalGradient(listOf(dress, dress, dressShade), 0.59f * s, 0.96f * s), line, o)
        // Light along her side, the shape of her waist.
        drawPath(Path().apply {
            moveTo(p(0.395f, 0.64f))
            quadTo(p(0.42f, 0.69f), p(0.43f + hp, 0.72f))
        }, Color.White.copy(alpha = 0.18f), style = Stroke(0.012f * s, cap = StrokeCap.Round))
        val slit = Path().apply {
            moveTo(p(0.604f + hp, 0.83f))
            lineTo(p(0.632f + hp, 0.951f))
            lineTo(p(0.59f + hp, 0.954f))
            close()
        }
        drawPath(slit, skin)
        drawPath(Path().apply {
            moveTo(p(0.59f + hp, 0.954f))
            lineTo(p(0.604f + hp, 0.83f))
            lineTo(p(0.632f + hp, 0.951f))
        }, line, style = Stroke(o * 0.8f, join = StrokeJoin.Round))

        withTransform({ scale(HEAD, HEAD, pivot = neck) }) { drawHead(m, s) }

        // Her arms, a hand on her hip as she waits; what she holds; her hands.
        val (lh, rh0) = hands(m, Poses(
            rest = Offset(0.418f + hp, 0.715f) to Offset(0.655f + hp, 0.83f),
            sad = Offset(0.47f, 0.8f) to Offset(0.53f, 0.8f),
            book = Offset(0.39f, 0.74f) to Offset(0.61f, 0.74f),
            note = Offset(0.41f, 0.8f) to (pencilTip(Offset(0.47f, 0.78f), 0.2f, m.t) + GRIP),
            chin = Offset(0.53f, 0.41f),
            wave = Offset(0.74f + 0.022f * sin(m.t * 12f), 0.4f),
            cheer = Offset(0.3f, 0.36f + 0.012f * sin(m.t * 9f)) to Offset(0.7f, 0.36f + 0.012f * sin(m.t * 9f + 1.4f)),
            dangle = Offset(0.31f + 0.03f * sin(m.t * 10f), 0.35f) to Offset(0.69f + 0.03f * sin(m.t * 10f + 1f), 0.35f),
            special = Offset(0.655f + 0.012f * cos(m.t * 7f), 0.23f + 0.04f * (m.special - 0.5f)),
        ), s)
        // A kiss blown: her fingers to her lips, then away.
        val kiss = max(m.wink, if (m.act == Act.HAPPY && m.actT < 1.4f) sin(PI.toFloat() * m.actT / 1.4f) else 0f)
        val rh = mix(rh0, p(0.54f + 0.06f * (1f - kiss), 0.382f - 0.01f * (1f - kiss)), kiss)
        val akimbo = 0.075f * (1f - max(max(m.book, m.note), max(m.cheer, m.dangle))) * (1f - m.sad)
        limb(p(0.372f, 0.6f), lh, -1, s, skin, line, 0.03f * s, bend = 0.035f + akimbo)
        limb(p(0.628f, 0.6f), rh, 1, s, skin, line, 0.03f * s)
        // The off-the-shoulder sleeves, over her arms' tops.
        for (side in intArrayOf(-1, 1)) {
            val c = p(0.5f + side * 0.142f, 0.607f)
            val puff = Path().apply { addOval(Rect(c.x - 0.035f * s, c.y - 0.022f * s, c.x + 0.035f * s, c.y + 0.022f * s)) }
            shape(puff, dress, line, o * 0.9f)
        }
        held(m, s, Offset(0.5f, 0.74f), 0.26f, Offset(0.47f, 0.78f), 0.2f, Color(0xFF2B2B3A), line)
        for (h in listOf(lh, rh)) {
            drawCircle(skin, 0.019f * s, h)
            drawCircle(line, 0.019f * s, h, style = Stroke(o * 0.8f))
        }
        // Red nails, a touch of glamour.
        drawCircle(lips, 0.006f * s, rh + Offset(-0.008f * s, -0.012f * s))
    }

    private fun DrawScope.backHair(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val sw = sway(m)
        val back = Path().apply {
            moveTo(p(0.37f, 0.3f))
            cubicTo(p(0.31f, 0.06f), p(0.69f, 0.06f), p(0.63f, 0.3f))
            cubicTo(p(0.71f, 0.4f), p(0.73f + sw, 0.5f), p(0.7f + sw, 0.6f))
            cubicTo(p(0.685f + sw, 0.66f), p(0.74f + sw, 0.7f), p(0.7f + sw, 0.76f))
            quadTo(p(0.665f + sw, 0.79f), p(0.64f + sw, 0.75f))
            lineTo(p(0.36f + sw, 0.75f))
            quadTo(p(0.335f + sw, 0.79f), p(0.3f + sw, 0.76f))
            cubicTo(p(0.26f + sw, 0.7f), p(0.315f + sw, 0.66f), p(0.3f + sw, 0.6f))
            cubicTo(p(0.27f + sw, 0.5f), p(0.29f, 0.4f), p(0.37f, 0.3f))
            close()
        }
        shape(back, Brush.verticalGradient(listOf(hair, hairShade, hairDeep), 0.1f * s, 0.78f * s), line, 0.011f * s)
        // Waves in it.
        for (side in intArrayOf(-1, 1)) {
            drawPath(Path().apply {
                moveTo(p(0.5f + side * 0.17f, 0.42f))
                cubicTo(p(0.5f + side * 0.2f + sw, 0.52f), p(0.5f + side * 0.15f + sw, 0.6f), p(0.5f + side * 0.18f + sw, 0.7f))
            }, hairDeep.copy(alpha = 0.5f), style = Stroke(0.006f * s, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.drawHead(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.011f * s
        val faceShape = Path().apply {
            moveTo(p(0.5f, 0.115f))
            cubicTo(p(0.585f, 0.115f), p(0.638f, 0.17f), p(0.636f, 0.27f))
            cubicTo(p(0.634f, 0.36f), p(0.56f, 0.425f), p(0.5f, 0.445f))
            cubicTo(p(0.44f, 0.425f), p(0.366f, 0.36f), p(0.364f, 0.27f))
            cubicTo(p(0.362f, 0.17f), p(0.415f, 0.115f), p(0.5f, 0.115f))
            close()
        }
        shape(faceShape, skin, line, o)
        clipPath(faceShape) {
            drawPath(Path().apply {
                moveTo(p(0.36f, 0.215f))
                quadTo(p(0.5f, 0.255f), p(0.64f, 0.215f))
                lineTo(p(0.64f, 0.17f))
                lineTo(p(0.36f, 0.17f))
                close()
            }, skinShade.copy(alpha = 0.45f))
        }
        face(m, s)
        brows(m, s)
        fringe(m, s)
        sideLocks(m, s)
        earrings(m, s)
        rose(m, s)
    }

    /** Her fringe, swept from a parting on her left across her brow to the right. */
    private fun DrawScope.fringe(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.011f * s
        val sw = sway(m) * 0.4f
        val top = Path().apply {
            moveTo(p(0.36f, 0.31f))
            cubicTo(p(0.315f, 0.03f), p(0.685f, 0.03f), p(0.64f, 0.31f))
            lineTo(p(0.646f + sw, 0.345f))
            // The long lock, curving down past her right eye.
            cubicTo(p(0.6f + sw, 0.31f), p(0.59f, 0.235f), p(0.545f, 0.212f))
            quadTo(p(0.53f, 0.232f), p(0.5f, 0.2f))
            quadTo(p(0.472f, 0.21f), p(0.45f, 0.165f))
            // To her left, the short side, back down to her temple.
            cubicTo(p(0.42f, 0.2f), p(0.385f, 0.24f), p(0.372f, 0.29f))
            lineTo(p(0.36f, 0.31f))
            close()
        }
        shape(top, Brush.verticalGradient(listOf(sheen, hair, hairShade), 0.08f * s, 0.34f * s), line, o)
        // Strands, and the shine on her crown.
        drawPath(Path().apply {
            moveTo(p(0.47f, 0.1f))
            cubicTo(p(0.52f, 0.13f), p(0.58f, 0.2f), p(0.615f, 0.29f))
        }, hairDeep.copy(alpha = 0.55f), style = Stroke(0.005f * s, cap = StrokeCap.Round))
        drawPath(Path().apply {
            moveTo(p(0.455f, 0.12f))
            quadTo(p(0.41f, 0.17f), p(0.39f, 0.24f))
        }, hairDeep.copy(alpha = 0.5f), style = Stroke(0.005f * s, cap = StrokeCap.Round))
        drawPath(Path().apply {
            moveTo(p(0.4f, 0.12f))
            quadTo(p(0.47f, 0.085f), p(0.56f, 0.1f))
        }, Color.White.copy(alpha = 0.55f), style = Stroke(0.012f * s, cap = StrokeCap.Round))
    }

    /** The long locks either side of her face, in waves over her shoulders; she flips the right one back. */
    private fun DrawScope.sideLocks(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.011f * s
        val flip = if (m.special > 0.05f && m.special < 0.95f) sin(PI.toFloat() * m.special) else 0f
        for (side in intArrayOf(-1, 1)) {
            val sw = sway(m, if (side < 0) 0f else 1.3f)
            fun q(x: Float, y: Float) = p(0.5f + side * (x - 0.5f), y)
            val tossed = if (side == 1) Offset(0.06f * flip * s, -0.12f * flip * s) else Offset.Zero
            val tip = q(0.37f, 0.67f) + Offset(sw * s, 0f) + tossed
            val lock = Path().apply {
                moveTo(q(0.368f, 0.25f))
                cubicTo(q(0.33f, 0.36f), q(0.325f, 0.47f) + Offset(sw * 0.5f * s, 0f) + tossed * 0.4f, q(0.35f, 0.56f) + Offset(sw * s, 0f) + tossed * 0.7f)
                cubicTo(q(0.37f, 0.6f) + Offset(sw * s, 0f) + tossed * 0.8f, q(0.335f, 0.63f) + Offset(sw * s, 0f) + tossed, tip)
                cubicTo(q(0.4f, 0.62f) + Offset(sw * s, 0f) + tossed, q(0.38f, 0.5f) + tossed * 0.5f, q(0.392f, 0.33f))
                close()
            }
            shape(lock, Brush.verticalGradient(listOf(hair, hairShade), 0.25f * s, 0.67f * s), line, o)
            drawPath(Path().apply {
                moveTo(q(0.36f, 0.32f))
                quadTo(q(0.34f, 0.44f) + tossed * 0.4f, mix(q(0.355f, 0.55f), tip, 0.3f))
            }, sheen.copy(alpha = 0.7f), style = Stroke(0.008f * s, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.earrings(m: MascotMotion, s: Float) {
        for (side in intArrayOf(-1, 1)) {
            val at = Offset((0.5f + side * 0.128f) * s, 0.352f * s)
            rotate(-m.lean * 25f + 7f * sin(m.t * 2f + side), Offset(at.x, at.y - 0.012f * s)) {
                drawCircle(gold, 0.013f * s, at, style = Stroke(0.004f * s))
            }
        }
    }

    /** A red rose in her hair. */
    private fun DrawScope.rose(m: MascotMotion, s: Float) {
        val c = Offset(0.618f * s, 0.16f * s)
        val r = 0.024f * s
        for (side in intArrayOf(-1, 1)) {
            val leafPath = Path().apply {
                moveTo(c)
                quadTo(c + Offset(side * 0.03f * s, 0.005f * s), c + Offset(side * 0.042f * s, 0.024f * s))
                quadTo(c + Offset(side * 0.012f * s, 0.03f * s), c)
                close()
            }
            shape(leafPath, leaf, line, 0.004f * s)
        }
        drawCircle(rose, r, c)
        drawCircle(line, r, c, style = Stroke(0.005f * s))
        spiral(c, r * 0.8f, 0.6f + 0.15f * sin(m.t), lipsDark, 0.005f * s)
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.25f, c + Offset(-r * 0.4f, -r * 0.45f))
    }

    private fun DrawScope.face(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        for (side in intArrayOf(-1, 1)) eye(m, s, p(0.5f + side * 0.06f, 0.287f), side)
        // A beauty mark by her lips.
        drawCircle(line.copy(alpha = 0.85f), 0.0042f * s, p(0.538f, 0.405f))
        // Cat-eye glasses, as she reads.
        if (m.book > 0.3f && !m.library) {
            val g = Color(0xFF2B1A12).copy(alpha = ((m.book - 0.3f) / 0.5f).coerceIn(0f, 1f))
            for (side in intArrayOf(-1, 1)) {
                val c = p(0.5f + side * 0.058f, 0.29f)
                val frame = Path().apply {
                    moveTo(c.x - side * 0.04f * s, c.y - 0.012f * s)
                    quadraticTo(c.x, c.y - 0.03f * s, c.x + side * 0.05f * s, c.y - 0.03f * s)
                    quadraticTo(c.x + side * 0.044f * s, c.y + 0.03f * s, c.x, c.y + 0.026f * s)
                    quadraticTo(c.x - side * 0.042f * s, c.y + 0.022f * s, c.x - side * 0.04f * s, c.y - 0.012f * s)
                    close()
                }
                drawPath(frame, g, style = Stroke(0.006f * s, join = StrokeJoin.Round))
            }
            drawLine(g, p(0.482f, 0.282f), p(0.518f, 0.282f), strokeWidth = 0.005f * s)
        }
        for (side in intArrayOf(-1, 1)) blush(p(0.5f + side * 0.085f, 0.345f), s, 0.045f * s, 0.18f + 0.45f * max(max(m.joy, m.dangle * 0.8f), m.wink), m.dangle > 0.5f)
        drawLine(skinShade, p(0.499f, 0.34f), p(0.503f, 0.35f), strokeWidth = 0.006f * s, cap = StrokeCap.Round)
        mouth(m, s, p(0.5f, 0.388f))
        if (m.sad > 0.3f) tear(p(0.445f, 0.325f), s, m.t)
    }

    private fun DrawScope.brows(m: MascotMotion, s: Float) {
        for (side in intArrayOf(-1, 1)) {
            val up = 0.01f * m.wide + (if (side == -1) 0.012f * m.chin else 0f) + 0.006f * m.dangle
            val y = 0.247f - up
            val inner = Offset((0.5f + side * 0.026f) * s, (y + 0.006f - 0.014f * m.sad) * s)
            val outer = Offset((0.5f + side * 0.098f) * s, (y + 0.01f + 0.006f * m.sad) * s)
            val arch = Offset((0.5f + side * 0.072f) * s, (y - 0.01f) * s)
            drawPath(Path().apply {
                moveTo(inner)
                quadTo(arch, outer)
            }, hairDeep, style = Stroke(0.0055f * s, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.eye(m: MascotMotion, s: Float, c: Offset, side: Int) {
        val ew = 0.084f * s
        // Heavy-lidded as she waits: wide open when something's done to her.
        val sultry = (0.32f * (1f - m.wide) * (1f - m.dangle) * (1f - m.sad * 0.6f)).coerceIn(0f, 0.32f)
        when (eyeLook(m, side == 1)) {
            EyeLook.HAPPY -> {
                closedEye(c, ew * 0.9f, s, true, line, 0.012f * s)
                drawLine(line, c + Offset(side * ew * 0.44f, -0.002f * s), c + Offset(side * ew * 0.62f, -0.014f * s), strokeWidth = 0.007f * s, cap = StrokeCap.Round)
            }
            EyeLook.SHUT -> {
                // Asleep, or a long slow blink: lids down, lashes along them.
                closedEye(c + Offset(0f, 0.004f * s), ew * 0.95f, s, false, line, 0.011f * s)
                for (k in 0..2) {
                    val x = c.x + side * ew * (0.18f + k * 0.14f)
                    drawLine(line, Offset(x, c.y + 0.008f * s), Offset(x + side * 0.004f * s, c.y + 0.016f * s), strokeWidth = 0.004f * s, cap = StrokeCap.Round)
                }
            }
            EyeLook.SPIRAL -> spiral(c, 0.034f * s, m.t * 6f * side, line, 0.008f * s)
            EyeLook.SQUEEZED -> squeezed(c, 0.045f * s, side, line, 0.011f * s)
            EyeLook.OPEN -> {
                val eh = 0.062f * s * (1f + 0.3f * m.wide) * m.open
                val inner = Offset(c.x - side * ew * 0.5f, c.y + 0.004f * s)
                val outer = Offset(c.x + side * ew * 0.52f, c.y - 0.009f * s)
                val lid = eh * sultry
                val topA = Offset(c.x - side * ew * 0.25f, c.y - eh * 0.8f + lid * 1.6f)
                val topB = Offset(c.x + side * ew * 0.3f, c.y - eh * 0.85f + lid * 1.6f)
                // Shadow over the lid.
                drawPath(Path().apply {
                    moveTo(inner + Offset(0f, -0.004f * s))
                    cubicTo(topA + Offset(0f, -0.018f * s), topB + Offset(0f, -0.02f * s), outer + Offset(side * 0.01f * s, -0.01f * s))
                    cubicTo(topB, topA, inner)
                    close()
                }, shadow.copy(alpha = 0.55f))
                val almond = Path().apply {
                    moveTo(inner)
                    cubicTo(topA, topB, outer)
                    cubicTo(Offset(c.x + side * ew * 0.3f, c.y + eh * 0.7f), Offset(c.x - side * ew * 0.25f, c.y + eh * 0.65f), inner)
                    close()
                }
                clipPath(almond) {
                    drawPath(almond, Color.White)
                    val ic = Offset(c.x + m.lookX * 0.01f * s, c.y + 0.004f * s + m.lookY * 0.008f * s)
                    val iris = Rect(ic.x - 0.027f * s, ic.y - 0.033f * s, ic.x + 0.027f * s, ic.y + 0.033f * s)
                    drawOval(Brush.verticalGradient(listOf(irisTop, irisLow), iris.top, iris.bottom), iris.topLeft, iris.size)
                    drawOval(line.copy(alpha = 0.5f), iris.topLeft, iris.size, style = Stroke(0.004f * s))
                    val pw = mix(0.016f, 0.011f, m.wide) * s
                    val ph = mix(0.026f, 0.019f, m.wide) * s
                    drawOval(pupil, Offset(ic.x - pw / 2, ic.y - ph / 2), Size(pw, ph))
                    drawCircle(Color.White, (0.01f + 0.003f * m.sad) * s, ic + Offset(-0.009f * s, -0.012f * s))
                    drawCircle(Color.White.copy(alpha = 0.9f), 0.0045f * s, ic + Offset(0.01f * s, 0.012f * s))
                    if (m.sad > 0.3f) drawOval(Color.White.copy(alpha = 0.45f * m.sad), Offset(c.x - ew / 2, c.y + eh * 0.1f), Size(ew, eh * 0.4f))
                }
                // Winged liner along the lid, long lashes at its corner; a light line under.
                val top = Path().apply {
                    moveTo(inner)
                    cubicTo(topA, topB, outer)
                    lineTo(outer + Offset(side * 0.02f * s, -0.014f * s))
                }
                drawPath(top, line, style = Stroke(0.012f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                for (k in 0..1) {
                    val from = mix(topB, outer, 0.4f + k * 0.4f)
                    drawLine(line, from, from + Offset(side * (0.006f + k * 0.004f) * s, -(0.012f + k * 0.003f) * s), strokeWidth = 0.004f * s, cap = StrokeCap.Round)
                }
                drawLine(line, Offset(c.x + side * ew * 0.1f, c.y + eh * 0.48f), Offset(c.x + side * ew * 0.45f, c.y + eh * 0.22f), strokeWidth = 0.004f * s, cap = StrokeCap.Round)
            }
        }
    }

    private fun DrawScope.mouth(m: MascotMotion, s: Float, c: Offset) {
        val open = max(max(m.talk, m.yawn), max(m.wide * 0.6f, m.joy * 0.5f))
        val kiss = max(m.wink, if (m.act == Act.HAPPY && m.actT < 1.4f) sin(PI.toFloat() * m.actT / 1.4f) else 0f)
        when {
            kiss > 0.4f -> {
                // A kiss: lips pursed.
                val k = Rect(c.x - 0.012f * s, c.y - 0.01f * s, c.x + 0.012f * s, c.y + 0.012f * s)
                drawOval(lips, k.topLeft, k.size)
                drawOval(lipsDark, k.topLeft, k.size, style = Stroke(0.004f * s))
                drawLine(lipsDark, Offset(k.left + 0.004f * s, c.y + 0.001f * s), Offset(k.right - 0.004f * s, c.y + 0.001f * s), strokeWidth = 0.003f * s)
            }
            open > 0.12f -> {
                val w = (0.03f + 0.018f * m.joy) * s
                val h = (0.01f + 0.026f * open) * s
                val shapeOf = Path().apply {
                    if (m.joy > 0.5f) {
                        moveTo(c.x - w / 2, c.y - h * 0.3f)
                        lineTo(c.x + w / 2, c.y - h * 0.3f)
                        quadraticTo(c.x + w / 2, c.y + h * 0.9f, c.x, c.y + h * 0.9f)
                        quadraticTo(c.x - w / 2, c.y + h * 0.9f, c.x - w / 2, c.y - h * 0.3f)
                    } else addOval(Rect(c.x - w * 0.36f, c.y - h / 2, c.x + w * 0.36f, c.y + h / 2))
                    close()
                }
                drawPath(shapeOf, lipsDark)
                clipPath(shapeOf) { drawOval(Color(0xFFFF8FA0), Offset(c.x - w * 0.3f, c.y + h * 0.15f), Size(w * 0.6f, h * 0.8f)) }
                drawPath(shapeOf, lips, style = Stroke(0.006f * s, join = StrokeJoin.Round))
            }
            m.sad > 0.3f -> {
                // A pout.
                drawOval(lips, Offset(c.x - 0.016f * s, c.y - 0.004f * s), Size(0.032f * s, 0.012f * s))
                drawPath(Path().apply {
                    moveTo(c.x - 0.016f * s, c.y + 0.004f * s)
                    quadraticTo(c.x, c.y - 0.006f * s, c.x + 0.016f * s, c.y + 0.004f * s)
                }, lipsDark, style = Stroke(0.004f * s, cap = StrokeCap.Round))
            }
            else -> {
                // Full red lips, the corner of her mouth turned up in a knowing smile.
                val w = 0.022f * s
                val smirk = 0.005f * s * (1f + m.joy)
                val upper = Path().apply {
                    moveTo(c.x - w, c.y)
                    quadraticTo(c.x - w * 0.5f, c.y - 0.009f * s, c.x - w * 0.12f, c.y - 0.005f * s)
                    lineTo(c.x, c.y - 0.003f * s)
                    lineTo(c.x + w * 0.12f, c.y - 0.005f * s)
                    quadraticTo(c.x + w * 0.5f, c.y - 0.009f * s, c.x + w, c.y - smirk)
                    quadraticTo(c.x, c.y + 0.002f * s, c.x - w, c.y)
                    close()
                }
                val lower = Path().apply {
                    moveTo(c.x - w * 0.9f, c.y + 0.001f * s)
                    quadraticTo(c.x, c.y + 0.003f * s, c.x + w * 0.9f, c.y - smirk + 0.001f * s)
                    quadraticTo(c.x + w * 0.4f, c.y + 0.016f * s, c.x, c.y + 0.016f * s)
                    quadraticTo(c.x - w * 0.5f, c.y + 0.015f * s, c.x - w * 0.9f, c.y + 0.001f * s)
                    close()
                }
                drawPath(lower, lips)
                drawPath(upper, lipsDark)
                drawLine(lipsDark, Offset(c.x - w, c.y), Offset(c.x + w, c.y - smirk), strokeWidth = 0.003f * s, cap = StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.75f), 0.0035f * s, Offset(c.x - 0.004f * s, c.y + 0.009f * s))
            }
        }
    }

    override fun DrawScope.icon(m: MascotMotion, s: Float) = fit(s, Rect(0.28f, 0.04f, 0.72f, 0.48f)) {
        clipPath(Path().apply { addRect(Rect(0f, 0f, s, 0.48f * s)) }) {
            backHair(m, s)
            drawHead(m, s)
        }
    }
}

// =================================================================================================================
// Mochi: a ginger cat with a bell on her collar, a tail with a mind of its own.

internal object CatArt : MascotArt {
    private val fur = Color(0xFFF6A85C)
    private val furLight = Color(0xFFFFE6C8)
    private val stripe = Color(0xFFD9813A)
    private val line = Color(0xFF5A3415)
    private val ear = Color(0xFFFFB3C1)
    private val nose = Color(0xFFFF7E95)
    private val iris = Color(0xFF8BD45C)
    private val irisDark = Color(0xFF4E9A2A)
    private val pupil = Color(0xFF1F1A12)
    override val head = Offset(0.5f, 0.44f)
    override val headR = 0.25f

    override fun DrawScope.figure(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.016f * s
        tail(m, s)
        // Body, belly and back feet.
        val body = Rect(p(0.3f, 0.61f), p(0.7f, 0.94f))
        drawOval(fur, body.topLeft, body.size)
        drawOval(line, body.topLeft, body.size, style = Stroke(o))
        drawOval(furLight, p(0.39f, 0.69f), Size(0.22f * s, 0.23f * s))
        for (x in floatArrayOf(0.37f, 0.63f)) {
            val foot = Rect(p(x - 0.065f, 0.885f), p(x + 0.065f, 0.95f))
            drawOval(fur, foot.topLeft, foot.size)
            drawOval(line, foot.topLeft, foot.size, style = Stroke(o * 0.9f))
        }
        val (lh, rh) = hands(m, Poses(
            rest = Offset(0.43f, 0.905f) to Offset(0.57f, 0.905f),
            sad = Offset(0.45f, 0.9f) to Offset(0.55f, 0.9f),
            book = Offset(0.38f, 0.79f) to Offset(0.62f, 0.79f),
            note = Offset(0.4f, 0.86f) to (pencilTip(Offset(0.47f, 0.83f), 0.2f, m.t) + GRIP),
            chin = Offset(0.555f, 0.6f),
            wave = Offset(0.77f + 0.025f * sin(m.t * 12f), 0.54f),
            cheer = Offset(0.27f, 0.5f + 0.015f * sin(m.t * 9f)) to Offset(0.73f, 0.5f + 0.015f * sin(m.t * 9f + 1.4f)),
            dangle = Offset(0.4f + 0.015f * sin(m.t * 9f), 1.0f) to Offset(0.6f + 0.015f * sin(m.t * 9f + 1f), 1.0f),
            special = Offset(0.535f, 0.575f),
        ), s)
        limb(p(0.38f, 0.7f), lh, -1, s, fur, line, 0.06f * s)
        limb(p(0.62f, 0.7f), rh, 1, s, fur, line, 0.06f * s)
        held(m, s, Offset(0.5f, 0.78f), 0.3f, Offset(0.47f, 0.83f), 0.2f, Color(0xFF4A90D9), line)
        for (h in listOf(lh, rh)) paw(h, s, o)
        drawHead(m, s)
        // Licking a paw: the paw over the mouth, the tongue at it.
        if (m.special in 0.25f..0.8f) {
            paw(rh, s, o)
            drawOval(nose, Offset(rh.x - 0.03f * s, rh.y - 0.005f * s), Size(0.025f * s, 0.018f * s))
        }
    }

    private fun DrawScope.paw(c: Offset, s: Float, o: Float) {
        val r = Rect(c.x - 0.036f * s, c.y - 0.028f * s, c.x + 0.036f * s, c.y + 0.028f * s)
        drawOval(fur, r.topLeft, r.size)
        drawOval(line, r.topLeft, r.size, style = Stroke(o * 0.85f))
        for (i in -1..1 step 2) drawLine(line, Offset(c.x + i * 0.012f * s, c.y + 0.005f * s), Offset(c.x + i * 0.012f * s, c.y + 0.026f * s), strokeWidth = o * 0.6f, cap = StrokeCap.Round)
    }

    /** Her tail: swishing; up and quivering for joy; low when sad; curled into a question as she thinks. */
    private fun DrawScope.tail(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val base = p(0.64f, 0.86f)
        var c1 = p(0.86f, 0.9f)
        var c2 = p(0.93f, 0.7f)
        var tip = p(0.84f + 0.04f * sin(m.t * 1.8f), 0.56f + 0.02f * cos(m.t * 1.8f))
        var d1 = tip
        var d2 = tip
        var end = tip
        fun to(a: Offset, b: Offset, c: Offset, w: Float, e1: Offset = c, e2: Offset = c, e3: Offset = c) {
            c1 = mix(c1, a, w)
            c2 = mix(c2, b, w)
            tip = mix(tip, c, w)
            d1 = mix(d1, e1, w)
            d2 = mix(d2, e2, w)
            end = mix(end, e3, w)
        }
        val quiver = 0.012f * sin(m.t * 30f)
        to(p(0.76f, 0.86f), p(0.8f, 0.7f), p(0.79f + quiver, 0.46f), max(m.joy, m.cheer))
        to(p(0.8f, 0.93f), p(0.88f, 0.94f), p(0.97f, 0.92f), max(m.sad, m.sleepy))
        to(p(0.9f, 0.88f), p(0.95f, 0.64f), p(0.84f, 0.56f), m.chin, p(0.76f, 0.5f), p(0.8f, 0.4f), p(0.88f, 0.42f))
        to(p(0.72f, 0.97f), p(0.62f, 1.02f), p(0.56f, 1.06f), m.dangle)
        val path = Path().apply {
            moveTo(base)
            cubicTo(c1, c2, tip)
            cubicTo(d1, d2, end)
        }
        drawPath(path, line, style = Stroke(0.085f * s, cap = StrokeCap.Round))
        drawPath(path, fur, style = Stroke(0.062f * s, cap = StrokeCap.Round))
        for (f in floatArrayOf(0.55f, 0.78f)) {
            val at = cubicAt(base, c1, c2, tip, f)
            drawCircle(stripe, 0.018f * s, at)
        }
    }

    private fun DrawScope.drawHead(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.016f * s
        // Ears: flat back when sad, up when startled, flicking now and then.
        for (side in intArrayOf(-1, 1)) {
            fun q(x: Float, y: Float) = p(0.5f + side * (x - 0.5f), y)
            val flick = if (side == 1 && m.special in 0.85f..0.95f || m.act == Act.IDLE && (m.t % 7.3f) < 0.15f && side == -1) 0.02f else 0f
            val tipX = mix(mix(0.265f, 0.18f, m.sad), 0.26f, m.wide) - flick
            val tipY = mix(mix(0.145f, 0.27f, m.sad), 0.115f, m.wide)
            val outer = Path().apply {
                moveTo(q(0.29f, 0.36f))
                lineTo(q(tipX, tipY))
                lineTo(q(0.44f, 0.265f))
                close()
            }
            shape(outer, fur, line, o)
            val inner = Path().apply {
                moveTo(q(0.31f, 0.33f))
                lineTo(q(tipX + 0.02f, tipY + 0.045f))
                lineTo(q(0.4f, 0.28f))
                close()
            }
            drawPath(inner, ear)
        }
        val head = Path().apply {
            moveTo(p(0.5f, 0.24f))
            cubicTo(p(0.66f, 0.24f), p(0.76f, 0.33f), p(0.755f, 0.45f))
            lineTo(p(0.795f, 0.5f))
            lineTo(p(0.748f, 0.508f))
            cubicTo(p(0.72f, 0.6f), p(0.62f, 0.655f), p(0.5f, 0.655f))
            cubicTo(p(0.38f, 0.655f), p(0.28f, 0.6f), p(0.252f, 0.508f))
            lineTo(p(0.205f, 0.5f))
            lineTo(p(0.245f, 0.45f))
            cubicTo(p(0.24f, 0.33f), p(0.34f, 0.24f), p(0.5f, 0.24f))
            close()
        }
        shape(head, fur, line, o)
        for (i in -1..1) drawLine(stripe, p(0.5f + i * 0.04f, 0.262f), p(0.5f + i * 0.035f, 0.31f - abs(i) * 0.01f), strokeWidth = 0.016f * s, cap = StrokeCap.Round)
        drawOval(furLight, p(0.415f, 0.49f), Size(0.17f * s, 0.12f * s))
        for (side in intArrayOf(-1, 1)) eye(m, s, p(0.5f + side * 0.105f, 0.455f), side)
        for (side in intArrayOf(-1, 1)) blush(p(0.5f + side * 0.12f, 0.555f), s, 0.055f * s, 0.2f + 0.45f * max(m.joy, m.wink), m.dangle > 0.5f)
        // Nose and mouth.
        val n = Path().apply {
            moveTo(p(0.484f, 0.508f))
            lineTo(p(0.516f, 0.508f))
            lineTo(p(0.5f, 0.527f))
            close()
        }
        shape(n, nose, line, o * 0.5f)
        val open = max(max(m.talk, m.yawn), m.wide * 0.6f)
        if (open > 0.12f) {
            val h = (0.012f + 0.038f * open) * s
            val mouthBox = Rect(p(0.5f, 0.555f).x - 0.026f * s, p(0.5f, 0.548f).y, p(0.5f, 0.555f).x + 0.026f * s, p(0.5f, 0.548f).y + h)
            drawOval(Color(0xFFB8455E), mouthBox.topLeft, mouthBox.size)
            drawOval(nose, Offset(mouthBox.left + mouthBox.width * 0.25f, mouthBox.top + mouthBox.height * 0.45f), Size(mouthBox.width * 0.5f, mouthBox.height * 0.5f))
            if (open > 0.4f) for (side in intArrayOf(-1, 1)) {
                val fang = Path().apply {
                    moveTo(mouthBox.center.x + side * 0.016f * s, mouthBox.top + 0.002f * s)
                    lineTo(mouthBox.center.x + side * 0.007f * s, mouthBox.top + 0.002f * s)
                    lineTo(mouthBox.center.x + side * 0.012f * s, mouthBox.top + 0.016f * s)
                    close()
                }
                drawPath(fang, Color.White)
            }
            drawOval(line, mouthBox.topLeft, mouthBox.size, style = Stroke(o * 0.5f))
        } else {
            val w = Path().apply {
                moveTo(p(0.5f, 0.527f))
                quadTo(p(0.497f, 0.553f), p(0.475f, 0.546f))
                moveTo(p(0.5f, 0.527f))
                quadTo(p(0.503f, 0.553f), p(0.525f, 0.546f))
            }
            drawPath(w, line, style = Stroke(o * 0.6f, cap = StrokeCap.Round))
        }
        // Whiskers, twitching.
        for (side in intArrayOf(-1, 1)) for (i in 0..2) {
            val twitch = 0.006f * sin(m.t * 3f + i + side)
            drawLine(line.copy(alpha = 0.75f), p(0.5f + side * 0.085f, 0.533f + i * 0.012f), p(0.5f + side * 0.27f, 0.515f + i * 0.03f + twitch), strokeWidth = 0.006f * s, cap = StrokeCap.Round)
        }
        // Her collar and bell, which swings as she moves.
        drawPath(Path().apply {
            moveTo(p(0.4f, 0.632f))
            quadTo(p(0.5f, 0.668f), p(0.6f, 0.632f))
        }, Color(0xFFE5486A), style = Stroke(0.026f * s, cap = StrokeCap.Round))
        val jingle = 22f * sin(m.t * 14f) * min(1f, m.hop * 2f + abs(m.lean) + m.dangle + m.cheer)
        rotate(jingle, p(0.5f, 0.655f)) {
            drawCircle(Color(0xFFFFCB4F), 0.024f * s, p(0.5f, 0.68f))
            drawCircle(line, 0.024f * s, p(0.5f, 0.68f), style = Stroke(o * 0.6f))
            drawLine(line, p(0.5f, 0.685f), p(0.5f, 0.702f), strokeWidth = o * 0.6f)
            drawCircle(Color.White.copy(alpha = 0.8f), 0.007f * s, p(0.492f, 0.672f))
        }
    }

    private fun DrawScope.eye(m: MascotMotion, s: Float, c: Offset, side: Int) {
        val ew = 0.104f * s
        val licking = m.special in 0.25f..0.8f
        val look = if (licking) EyeLook.HAPPY else eyeLook(m, side == 1)
        when (look) {
            EyeLook.HAPPY -> closedEye(c, ew * 0.9f, s, true, line, 0.016f * s)
            EyeLook.SHUT -> closedEye(c, ew * 0.9f, s, false, line, 0.014f * s)
            EyeLook.SPIRAL -> spiral(c, 0.045f * s, m.t * 6f * side, line, 0.01f * s)
            EyeLook.SQUEEZED -> squeezed(c, 0.06f * s, side, line, 0.014f * s)
            EyeLook.OPEN -> {
                val eh = 0.124f * s * m.open
                val box = Rect(c.x - ew / 2, c.y - eh / 2, c.x + ew / 2, c.y + eh / 2)
                clipPath(Path().apply { addOval(box) }) {
                    drawOval(Brush.verticalGradient(listOf(irisDark, iris), box.top, box.bottom), box.topLeft, box.size)
                    val pc = Offset(c.x + m.lookX * 0.012f * s, c.y + m.lookY * 0.01f * s)
                    // Pupils: slits at ease, round and wide when she's excited or startled.
                    val round = max(m.wide, max(m.dangle, m.joy * 0.5f))
                    val pw = mix(0.026f, 0.07f, round) * s
                    val ph = mix(0.1f, 0.08f, round) * s
                    drawOval(pupil, Offset(pc.x - pw / 2, pc.y - ph / 2), Size(pw, ph))
                    drawCircle(Color.White, 0.016f * s, pc + Offset(-0.016f * s, -0.024f * s))
                    drawCircle(Color.White.copy(alpha = 0.85f), 0.007f * s, pc + Offset(0.014f * s, 0.02f * s))
                }
                drawOval(line, box.topLeft, box.size, style = Stroke(0.013f * s))
                if (m.sad > 0.3f) tear(Offset(c.x - side * 0.01f * s, c.y + eh * 0.55f), s, m.t)
            }
        }
    }

    override fun DrawScope.icon(m: MascotMotion, s: Float) = fit(s, Rect(0.16f, 0.1f, 0.84f, 0.68f)) { drawHead(m, s) }
}

// =================================================================================================================
// Sage: a round little owl, all eyes; spectacles for reading, and a head that turns.

internal object OwlArt : MascotArt {
    private val brown = Color(0xFF8C6A4A)
    private val brownLight = Color(0xFFA5815C)
    private val dark = Color(0xFF5B412B)
    private val belly = Color(0xFFF1E2C3)
    private val chevron = Color(0xFFC7AC80)
    private val disc = Color(0xFFF7EDD9)
    private val lid = Color(0xFFDCC7A2)
    private val iris = Color(0xFFFFC23A)
    private val irisDeep = Color(0xFFE08A12)
    private val pupil = Color(0xFF241608)
    private val beak = Color(0xFFF29E38)
    private val line = Color(0xFF3F2A1A)
    override val head = Offset(0.5f, 0.4f)
    override val headR = 0.27f

    override fun DrawScope.figure(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.016f * s
        // Its wings, behind or beside it, flapping for joy.
        val flap = if (m.joy > 0.5f || m.dangle > 0.5f) sin(m.t * 16f) * 28f else 0f
        wing(m, s, -1, flap)
        wing(m, s, 1, flap)
        val body = Path().apply {
            moveTo(p(0.5f, 0.2f))
            cubicTo(p(0.7f, 0.2f), p(0.79f, 0.38f), p(0.785f, 0.58f))
            cubicTo(p(0.78f, 0.8f), p(0.66f, 0.91f), p(0.5f, 0.91f))
            cubicTo(p(0.34f, 0.91f), p(0.22f, 0.8f), p(0.215f, 0.58f))
            cubicTo(p(0.21f, 0.38f), p(0.3f, 0.2f), p(0.5f, 0.2f))
            close()
        }
        shape(body, Brush.verticalGradient(listOf(brownLight, brown), 0.2f * s, 0.91f * s), line, o)
        drawOval(belly, p(0.325f, 0.53f), Size(0.35f * s, 0.34f * s))
        for ((row, y) in listOf(0.6f, 0.68f, 0.76f).withIndex()) {
            val n = if (row == 1) 4 else 3
            for (i in 0 until n) {
                val x = 0.5f + (i - (n - 1) / 2f) * 0.07f
                drawPath(Path().apply {
                    moveTo(p(x - 0.02f, y))
                    lineTo(p(x, y + 0.018f))
                    lineTo(p(x + 0.02f, y))
                }, chevron, style = Stroke(0.011f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // Feet.
        for (x in floatArrayOf(0.43f, 0.57f)) for (i in -1..1) {
            drawLine(beak, p(x, 0.9f), p(x + i * 0.022f, 0.935f), strokeWidth = 0.018f * s, cap = StrokeCap.Round)
        }
        held(m, s, Offset(0.5f, 0.75f), 0.32f, Offset(0.47f, 0.8f), 0.2f, Color(0xFF2A9D8F), line)
        // In front of what it holds: the wing tips gripping it.
        if (m.book > 0.4f || m.note > 0.4f) for (side in intArrayOf(-1, 1)) drawCircle(brown, 0.03f * s, p(0.5f + side * (if (m.book > 0.4f) 0.155f else 0.1f), if (m.book > 0.4f) 0.76f else 0.82f))
        drawHead(m, s)
        // Feathers, knocked loose by a bump.
        if (m.sinceBump < 1.6f) {
            val f = m.sinceBump / 1.6f
            for (i in 0..2) {
                val c = p(0.3f + i * 0.2f + sin(f * 6f + i) * 0.04f, 0.45f + f * 0.45f)
                rotate(sin(f * 8f + i) * 40f, c) {
                    val feather = Path().apply {
                        moveTo(c.x, c.y - 0.03f * s)
                        quadraticTo(c.x + 0.018f * s, c.y, c.x, c.y + 0.03f * s)
                        quadraticTo(c.x - 0.018f * s, c.y, c.x, c.y - 0.03f * s)
                        close()
                    }
                    drawPath(feather, brownLight.copy(alpha = 1f - f))
                    drawPath(feather, line.copy(alpha = 1f - f), style = Stroke(0.005f * s))
                }
            }
        }
    }

    /** A wing ([side]: −1 left, 1 right), turned at the shoulder for what it's doing. */
    private fun DrawScope.wing(m: MascotMotion, s: Float, side: Int, flap: Float) {
        val pivot = Offset((0.5f + side * 0.255f) * s, 0.52f * s)
        var a = 8f + 4f * sin(m.t * 2f)
        a = mix(a, -2f, m.sad)
        a = mix(a, -42f, m.book)
        a = mix(a, if (side < 0) -40f else -30f + 12f * sin(m.t * 16f), m.note)
        if (side == 1) a = mix(a, -105f, m.chin)
        if (side == 1) a = mix(a, 125f + 22f * sin(m.t * 12f), m.wave)
        a = mix(a, 115f, m.cheer)
        a = mix(a, 130f, m.dangle)
        a += flap
        rotate(-side * a, pivot) {
            fun w(x: Float, y: Float) = Offset(pivot.x + side * x * s, pivot.y + y * s)
            val shapeOf = Path().apply {
                moveTo(w(0f, -0.02f))
                cubicTo(w(0.085f, 0.04f), w(0.075f, 0.22f), w(0.015f, 0.29f))
                cubicTo(w(-0.03f, 0.2f), w(-0.04f, 0.06f), w(0f, -0.02f))
                close()
            }
            shape(shapeOf, dark, line, 0.016f * s)
            for (i in 0..1) drawPath(Path().apply {
                moveTo(w(0.01f, 0.08f + i * 0.07f))
                quadTo(w(0.04f, 0.12f + i * 0.07f), w(0.035f, 0.18f + i * 0.06f))
            }, brownLight, style = Stroke(0.01f * s, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.drawHead(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val o = 0.016f * s
        // Ear tufts.
        for (side in intArrayOf(-1, 1)) {
            fun q(x: Float, y: Float) = p(0.5f + side * (x - 0.5f), y)
            val wiggle = 0.01f * sin(m.t * 3f + side) * (1f + m.wide)
            val tuft = Path().apply {
                moveTo(q(0.3f, 0.3f))
                lineTo(q(0.26f - wiggle - m.wide * 0.01f, 0.13f - m.wide * 0.02f))
                lineTo(q(0.41f, 0.24f))
                close()
            }
            shape(tuft, dark, line, o)
        }
        // Its head turns: the face slides across, as it looks round, and tilts as it wonders.
        val turn = 0.12f * sin(2f * PI.toFloat() * m.special)
        val squeeze = 1f - 0.25f * abs(sin(2f * PI.toFloat() * m.special))
        withTransform({
            rotate(m.tilt * 1.1f, p(0.5f, 0.45f))
            translate(turn * s, 0f)
            scale(squeeze, 1f, pivot = p(0.5f, 0.43f))
        }) {
            val faceDisc = Path().apply {
                addOval(Rect(p(0.39f, 0.425f), 0.125f * s))
                addOval(Rect(p(0.61f, 0.425f), 0.125f * s))
            }
            drawPath(faceDisc, line.copy(alpha = 0.35f), style = Stroke(o * 1.4f))
            drawPath(faceDisc, disc)
            val dip = 0.035f - 0.06f * m.sad - 0.015f * m.wide
            drawPath(Path().apply {
                moveTo(p(0.425f, 0.318f))
                quadTo(p(0.47f, 0.318f + dip * 0.7f), p(0.5f, 0.335f + dip))
                quadTo(p(0.53f, 0.318f + dip * 0.7f), p(0.575f, 0.318f))
            }, brownLight, style = Stroke(0.016f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (side in intArrayOf(-1, 1)) eye(m, s, p(0.5f + side * 0.11f, 0.425f), side)
            if (m.book > 0.3f && !m.library) {
                val g = Color(0xFFCFA234).copy(alpha = ((m.book - 0.3f) / 0.5f).coerceIn(0f, 1f))
                for (side in intArrayOf(-1, 1)) drawCircle(g, 0.1f * s, p(0.5f + side * 0.11f, 0.425f), style = Stroke(0.012f * s))
                drawLine(g, p(0.48f, 0.415f), p(0.52f, 0.415f), strokeWidth = 0.01f * s)
            }
            for (side in intArrayOf(-1, 1)) blush(p(0.5f + side * 0.16f, 0.52f), s, 0.06f * s, 0.15f + 0.45f * max(m.joy, m.wink), m.dangle > 0.5f)
            beak(m, s)
        }
    }

    private fun DrawScope.beak(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val open = max(m.talk, max(m.yawn, m.wide * 0.5f))
        val upper = Path().apply {
            moveTo(p(0.468f, 0.488f))
            quadTo(p(0.5f, 0.475f), p(0.532f, 0.488f))
            quadTo(p(0.515f, 0.515f), p(0.5f, 0.54f - 0.01f * open))
            quadTo(p(0.485f, 0.515f), p(0.468f, 0.488f))
            close()
        }
        if (open > 0.1f) {
            val lower = Path().apply {
                moveTo(p(0.478f, 0.512f + 0.01f * open))
                lineTo(p(0.522f, 0.512f + 0.01f * open))
                lineTo(p(0.5f, 0.54f + 0.03f * open))
                close()
            }
            shape(lower, irisDeep, line, 0.008f * s)
        }
        shape(upper, beak, line, 0.009f * s)
    }

    private fun DrawScope.eye(m: MascotMotion, s: Float, c: Offset, side: Int) {
        val r = 0.082f * s
        // Asleep, it opens one eye now and then to look about.
        val peek = m.act == Act.SLEEPING && side == 1 && (m.actT % 5.5f) in 3.4f..4.3f
        val look = if (peek) EyeLook.OPEN else eyeLook(m, side == 1)
        val open = if (peek) 0.55f else m.open
        when (look) {
            EyeLook.HAPPY -> closedEye(c, r * 1.4f, s, true, line, 0.018f * s)
            EyeLook.SHUT -> {
                drawCircle(lid, r, c)
                drawCircle(line, r, c, style = Stroke(0.012f * s))
                closedEye(c, r * 1.3f, s, false, line, 0.014f * s)
            }
            EyeLook.SPIRAL -> {
                drawCircle(iris, r, c)
                drawCircle(line, r, c, style = Stroke(0.012f * s))
                spiral(c, r * 0.8f, m.t * 6f * side, line, 0.011f * s)
            }
            EyeLook.SQUEEZED -> {
                drawCircle(lid, r, c)
                squeezed(c, 0.07f * s, side, line, 0.015f * s)
            }
            EyeLook.OPEN -> {
                drawCircle(Brush.radialGradient(listOf(iris, irisDeep), center = c, radius = r), r, c)
                val pc = Offset(c.x + m.lookX * 0.014f * s, c.y + m.lookY * 0.012f * s)
                val pr = mix(0.042f, 0.06f, max(m.wide, m.dangle * 0.6f)) * s
                drawCircle(pupil, pr, pc)
                drawCircle(Color.White, 0.02f * s, pc + Offset(-0.02f * s, -0.022f * s))
                drawCircle(Color.White.copy(alpha = 0.85f), 0.009f * s, pc + Offset(0.016f * s, 0.018f * s))
                // The lid, coming down as it blinks or drowses.
                if (open < 0.98f) clipPath(Path().apply { addOval(Rect(c, r)) }) {
                    val h = 2f * r * (1f - open)
                    drawRect(lid, Offset(c.x - r, c.y - r), Size(2f * r, h))
                    drawLine(line, Offset(c.x - r, c.y - r + h), Offset(c.x + r, c.y - r + h), strokeWidth = 0.012f * s)
                }
                drawCircle(line, r, c, style = Stroke(0.012f * s))
                if (m.sad > 0.3f) tear(Offset(c.x, c.y + r * 1.1f), s, m.t)
            }
        }
    }

    override fun DrawScope.icon(m: MascotMotion, s: Float) = fit(s, Rect(0.18f, 0.1f, 0.82f, 0.62f)) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val headShape = Path().apply { addOval(Rect(p(0.22f, 0.2f), p(0.78f, 0.62f))) }
        shape(headShape, brown, line, 0.016f * s)
        drawHead(m, s)
    }
}

// =================================================================================================================
// Boo: a friendly little ghost that glows, fades as it dozes, and loves to say boo.

internal object GhostArt : MascotArt {
    private val sheet = Color(0xFFFFFFFF)
    private val shade = Color(0xFFE6E0FF)
    private val line = Color(0xFF5B4FA8)
    private val eyes = Color(0xFF2A2440)
    private val aura = Color(0xFFBFB2FF)
    private val tongue = Color(0xFFFF7FA8)
    override val head = Offset(0.5f, 0.38f)
    override val headR = 0.25f

    override fun DrawScope.figure(m: MascotMotion, s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        glow(p(0.5f, 0.52f), 0.45f * s, aura.copy(alpha = 0.6f), 0.55f + 0.2f * sin(m.t * 2f))
        // Saying boo: it puffs itself up. Happy: a twirl.
        val boo = if (m.special > 0.2f) sin(PI.toFloat() * ((m.special - 0.2f) / 0.6f).coerceIn(0f, 1f)) else 0f
        val twirl = if (m.act == Act.HAPPY && m.actT < 0.8f) m.actT / 0.8f * 360f else 0f
        val fade = if (m.act == Act.SLEEPING) 0.55f + 0.15f * sin(m.t * 1.5f) else 0.96f
        withTransform({
            rotate(twirl, p(0.5f, 0.5f))
            scale(1f + 0.15f * boo, 1f + 0.15f * boo, pivot = p(0.5f, 0.8f))
        }) {
            val (lh, rh) = hands(m, Poses(
                rest = Offset(0.215f, 0.66f + 0.015f * sin(m.t * 2.4f)) to Offset(0.785f, 0.66f + 0.015f * sin(m.t * 2.4f + 1f)),
                sad = Offset(0.38f, 0.8f) to Offset(0.62f, 0.8f),
                book = Offset(0.36f, 0.75f) to Offset(0.64f, 0.75f),
                note = Offset(0.4f, 0.8f) to (pencilTip(Offset(0.47f, 0.78f), 0.2f, m.t) + GRIP),
                chin = Offset(0.575f, 0.525f),
                wave = Offset(0.83f + 0.025f * sin(m.t * 12f), 0.42f),
                cheer = Offset(0.17f, 0.4f) to Offset(0.83f, 0.4f),
                dangle = Offset(0.2f + 0.03f * sin(m.t * 10f), 0.36f) to Offset(0.8f + 0.03f * sin(m.t * 10f + 1f), 0.36f),
            ), s)
            val (lb, rb) = if (boo > 0.1f) mix(lh, p(0.18f, 0.36f), boo) to mix(rh, p(0.82f, 0.36f), boo) else lh to rh
            body(m, s, fade)
            limb(p(0.27f, 0.56f), lb, -1, s, sheet.copy(alpha = fade), line.copy(alpha = fade), 0.06f * s)
            limb(p(0.73f, 0.56f), rb, 1, s, sheet.copy(alpha = fade), line.copy(alpha = fade), 0.06f * s)
            held(m, s, Offset(0.5f, 0.74f), 0.3f, Offset(0.47f, 0.78f), 0.2f, Color(0xFF8E7CFF), line)
            if (m.note > 0.3f) glow(pencilTip(p(0.47f, 0.78f), 0.2f * s, m.t), 0.04f * s, Color(0xFFB39BFF), m.note)
            face(m, s, boo, fade)
        }
    }

    private fun DrawScope.body(m: MascotMotion, s: Float, fade: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val trail = -m.lean * 0.06f + 0.02f * sin(m.t * 2f)
        val sheetPath = Path().apply {
            moveTo(p(0.25f, 0.48f))
            cubicTo(p(0.25f, 0.25f), p(0.36f, 0.17f), p(0.5f, 0.17f))
            cubicTo(p(0.64f, 0.17f), p(0.75f, 0.25f), p(0.75f, 0.48f))
            lineTo(p(0.76f + trail, 0.8f))
            val n = 4
            for (i in 0 until n) {
                val x0 = 0.76f - i * (0.52f / n)
                val x1 = x0 - 0.52f / n
                quadTo(p((x0 + x1) / 2f + trail, 0.875f + 0.03f * sin(m.t * 4f + i * 1.3f)), p(x1 + trail, 0.8f))
            }
            close()
        }
        drawPath(sheetPath, Brush.verticalGradient(listOf(sheet.copy(alpha = fade), shade.copy(alpha = fade)), 0.2f * s, 0.9f * s))
        drawPath(sheetPath, line.copy(alpha = 0.8f * fade), style = Stroke(0.014f * s, join = StrokeJoin.Round))
        drawOval(Color.White.copy(alpha = 0.7f * fade), p(0.33f, 0.24f), Size(0.07f * s, 0.045f * s))
    }

    private fun DrawScope.face(m: MascotMotion, s: Float, boo: Float, fade: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val ink = eyes.copy(alpha = fade)
        for (side in intArrayOf(-1, 1)) {
            val c = p(0.5f + side * 0.085f + m.lookX * 0.012f, 0.42f + m.lookY * 0.01f)
            when (eyeLook(m, side == 1)) {
                EyeLook.HAPPY -> closedEye(c, 0.07f * s, s, true, ink, 0.016f * s)
                EyeLook.SHUT -> closedEye(c, 0.07f * s, s, false, ink, 0.014f * s)
                EyeLook.SPIRAL -> spiral(c, 0.04f * s, m.t * 6f * side, ink, 0.01f * s)
                EyeLook.SQUEEZED -> squeezed(c, 0.055f * s, side, ink, 0.014f * s)
                EyeLook.OPEN -> {
                    val big = max(m.wide, boo)
                    val w = mix(0.068f, 0.09f, big) * s
                    val h = mix(0.1f, 0.12f, big) * s * m.open * (1f - 0.3f * m.sad)
                    drawOval(ink, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
                    drawCircle(Color.White.copy(alpha = fade), 0.013f * s, c + Offset(-w * 0.18f, -h * 0.22f))
                    drawCircle(Color.White.copy(alpha = 0.8f * fade), 0.006f * s, c + Offset(w * 0.18f, h * 0.2f))
                }
            }
        }
        for (side in intArrayOf(-1, 1)) blush(p(0.5f + side * 0.12f, 0.485f), s, 0.06f * s, (0.3f + 0.4f * max(m.joy, m.wink)) * fade, m.dangle > 0.5f)
        if (m.sad > 0.3f) tear(p(0.41f, 0.47f), s, m.t)
        val c = p(0.5f, 0.505f)
        val open = max(max(m.talk, m.yawn), max(boo, m.wide * 0.8f))
        when {
            open > 0.12f -> {
                val w = (0.04f + 0.03f * boo) * s
                val h = (0.015f + 0.06f * open) * s
                val mouthBox = Rect(c.x - w / 2, c.y - h * 0.3f, c.x + w / 2, c.y + h * 0.7f)
                drawOval(ink, mouthBox.topLeft, mouthBox.size)
                drawOval(tongue.copy(alpha = fade), Offset(mouthBox.left + w * 0.22f, mouthBox.top + h * 0.5f), Size(w * 0.56f, h * 0.45f))
            }
            m.joy > 0.3f || m.act == Act.HELLO -> {
                drawPath(Path().apply {
                    moveTo(c.x - 0.03f * s, c.y - 0.005f * s)
                    quadraticTo(c.x, c.y + 0.03f * s, c.x + 0.03f * s, c.y - 0.005f * s)
                    close()
                }, ink)
                // Its tongue out, cheekily.
                drawOval(tongue.copy(alpha = fade), Offset(c.x - 0.004f * s, c.y + 0.004f * s), Size(0.02f * s, 0.022f * s))
            }
            m.sad > 0.3f -> drawPath(Path().apply {
                moveTo(c.x - 0.022f * s, c.y + 0.01f * s)
                quadraticTo(c.x, c.y - 0.012f * s, c.x + 0.022f * s, c.y + 0.01f * s)
            }, ink, style = Stroke(0.01f * s, cap = StrokeCap.Round))
            else -> drawPath(Path().apply {
                moveTo(c.x - 0.02f * s, c.y - 0.004f * s)
                quadraticTo(c.x, c.y + 0.016f * s, c.x + 0.02f * s, c.y - 0.004f * s)
            }, ink, style = Stroke(0.01f * s, cap = StrokeCap.Round))
        }
    }

    override fun DrawScope.icon(m: MascotMotion, s: Float) = fit(s, Rect(0.2f, 0.13f, 0.8f, 0.62f)) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val dome = Path().apply {
            moveTo(p(0.25f, 0.62f))
            lineTo(p(0.25f, 0.48f))
            cubicTo(p(0.25f, 0.25f), p(0.36f, 0.17f), p(0.5f, 0.17f))
            cubicTo(p(0.64f, 0.17f), p(0.75f, 0.25f), p(0.75f, 0.48f))
            lineTo(p(0.75f, 0.62f))
            close()
        }
        shape(dome, Brush.verticalGradient(listOf(sheet, shade), 0.17f * s, 0.62f * s), line, 0.016f * s)
        face(m, s, 0f, 1f)
    }
}
