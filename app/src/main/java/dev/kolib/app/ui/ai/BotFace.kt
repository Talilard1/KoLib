package dev.kolib.app.ui.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kolib.app.KoLibApp
import dev.kolib.app.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * What the AI is doing, as its face shows it. Each has a look of its own (see [BotFace]); more moods, and more
 * character, can be added here as it grows.
 */
enum class BotMood {
    /** Waiting, at ease: it blinks now and then, and glances about. */
    IDLE,

    /** Getting ready, or reading what it's been given: its eyes run along the lines. */
    READING,

    /** Working out its answer before it writes: eyes up and to the side, and thought bubbles. */
    THINKING,

    /** Writing its answer: it chatters along, bobbing as it goes. */
    WRITING,

    /** Done, with something new to see: a happy face. */
    HAPPY,
}

/** How the face is drawn: in the bubble, filled in and with rosy cheeks; or as a line drawing, like a bar's icons. */
enum class BotLook { BUBBLE, ICON }

/** What the AI is doing now, for its face: answering in the chat, and in a book, writing the chapter's summary. */
@Composable
fun aiMood(place: ChatPlace? = null): BotMood {
    val app = LocalContext.current.applicationContext as KoLibApp
    val chat = app.chat
    val reading by chat.reading.collectAsState()
    val companion = (place as? ChatPlace.Book)?.companion
    val stage = companion?.stage?.collectAsState()?.value
    val summary = companion?.summary?.collectAsState()?.value.orEmpty()
    return when {
        // Its progress through what it was given is known until it starts writing.
        chat.asking -> reading.let { r ->
            when {
                r == null -> BotMood.WRITING
                r < 1f -> BotMood.READING
                else -> BotMood.THINKING
            }
        }
        stage != null -> if (summary.isNotEmpty()) BotMood.WRITING else BotMood.READING
        app.aiPanel.unseen -> BotMood.HAPPY
        else -> BotMood.IDLE
    }
}

/** What a face in this [mood] tells someone who can't see it. */
private fun said(mood: BotMood): String = when (mood) {
    BotMood.IDLE -> ""
    BotMood.READING -> tr("Reading…")
    BotMood.THINKING -> tr("Thinking…")
    BotMood.WRITING -> tr("Writing…")
    BotMood.HAPPY -> tr("Answer ready")
}

/**
 * The AI's face, in its [mood]: a [look] for the bubble or for a bar, drawn in [color] (with [cheeks] and [bubbles]
 * for its thoughts, in the bubble). Its moods change smoothly from one to the next. Blinks and glances come every
 * few seconds; only while it works does it move all the time.
 */
@Composable
fun BotFace(
    mood: BotMood,
    look: BotLook,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    cheeks: Color = Color(0xFFFF8FA3),
    bubbles: Color = color,
) {
    val blink = remember { Animatable(1f) }
    val gaze = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val talk = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val happy by animateFloatAsState(if (mood == BotMood.HAPPY) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "happy")
    val smile by animateFloatAsState(
        when (mood) {
            BotMood.HAPPY -> 1f
            BotMood.IDLE -> 0.6f
            BotMood.WRITING -> 0.45f
            BotMood.READING -> 0.25f
            BotMood.THINKING -> 0.05f
        },
        spring(stiffness = 200f), label = "smile",
    )
    val musing by animateFloatAsState(if (mood == BotMood.THINKING) 1f else 0f, spring(stiffness = 200f), label = "musing")

    // A bounce of joy when it's done.
    LaunchedEffect(mood) {
        if (mood == BotMood.HAPPY) {
            pop.snapTo(0.82f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
        } else if (pop.value != 1f) pop.animateTo(1f)
    }
    // Blinks: now and then, sometimes two at once; not with happy, closed eyes.
    LaunchedEffect(mood) {
        if (mood == BotMood.HAPPY) {
            blink.animateTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(2_200, 5_600))
            blink.animateTo(0.06f, tween(70))
            blink.animateTo(1f, tween(120))
            if (Random.nextInt(5) == 0) {
                delay(130)
                blink.animateTo(0.06f, tween(60))
                blink.animateTo(1f, tween(110))
            }
        }
    }
    // Where it looks.
    LaunchedEffect(mood) {
        when (mood) {
            BotMood.IDLE -> while (true) {
                delay(Random.nextLong(1_800, 4_500))
                gaze.animateTo(Offset(Random.nextFloat() - 0.5f, (Random.nextFloat() - 0.5f) * 0.6f), spring(dampingRatio = 0.7f, stiffness = 160f))
                delay(Random.nextLong(900, 2_600))
                gaze.animateTo(Offset.Zero, spring(dampingRatio = 0.8f, stiffness = 120f))
            }
            // Along a line and back to the start of the next.
            BotMood.READING -> while (true) {
                gaze.animateTo(Offset(-0.75f, 0.35f), tween(160))
                gaze.animateTo(Offset(0.75f, 0.35f), tween(950, easing = LinearEasing))
            }
            BotMood.THINKING -> {
                gaze.animateTo(Offset(0.6f, -0.7f), spring(dampingRatio = 0.6f, stiffness = 140f))
                while (true) {
                    delay(1_400)
                    gaze.animateTo(Offset(0.3f, -0.8f), tween(700))
                    delay(900)
                    gaze.animateTo(Offset(0.65f, -0.6f), tween(700))
                }
            }
            BotMood.WRITING -> while (true) {
                gaze.animateTo(Offset(Random.nextFloat() * 0.6f - 0.4f, 0.4f), tween(380))
                delay(Random.nextLong(350, 900))
            }
            BotMood.HAPPY -> gaze.animateTo(Offset.Zero, spring(dampingRatio = 0.7f, stiffness = 200f))
        }
    }
    // Chattering as it writes: a word's worth of mouth movements, and now and then a pause.
    LaunchedEffect(mood) {
        if (mood != BotMood.WRITING) {
            talk.animateTo(0f, tween(150))
            return@LaunchedEffect
        }
        while (true) {
            talk.animateTo(0.45f + Random.nextFloat() * 0.55f, tween(Random.nextInt(70, 120)))
            talk.animateTo(Random.nextFloat() * 0.25f, tween(Random.nextInt(70, 130)))
            if (Random.nextInt(6) == 0) delay(Random.nextLong(120, 280))
        }
    }
    // What moves all the time, only while it works: its thought bubbles, and its bobbing as it writes.
    val beat = if (mood == BotMood.THINKING || mood == BotMood.WRITING) {
        rememberInfiniteTransition(label = "bot").animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "beat").value
    } else 0f

    val said = said(mood)
    Canvas(modifier.semantics { if (said.isNotEmpty()) stateDescription = said }) {
        val face = Face(
            open = blink.value, gaze = gaze.value, talk = talk.value, happy = happy, smile = smile, musing = musing,
            bob = if (mood == BotMood.WRITING) sin(beat * 2f * PI.toFloat() * 3f) else 0f,
            thinking = if (mood == BotMood.THINKING) beat else null,
        )
        scale(pop.value) { drawFace(face, look, color, cheeks, bubbles) }
    }
}

/** The face at a moment: how open its eyes are, where they look, and the rest. */
private class Face(
    val open: Float,
    val gaze: Offset,
    val talk: Float,
    val happy: Float,
    val smile: Float,
    val musing: Float,
    val bob: Float,
    /** How far through a beat of its thought bubbles it is, while it thinks. */
    val thinking: Float?,
)

private fun DrawScope.drawFace(f: Face, look: BotLook, ink: Color, cheeks: Color, bubbles: Color) {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    fun at(x: Float, y: Float) = Offset(left + x * s, top + y * s)
    val icon = look == BotLook.ICON
    val bob = f.bob * 0.02f
    val lookX = f.gaze.x * 0.055f
    val lookY = f.gaze.y * 0.05f

    // A bar's icon has a head drawn round it; in the bubble, the bubble is its head.
    if (icon) drawCircle(ink, radius = 0.41f * s, center = at(0.5f, 0.52f + bob), style = Stroke(width = 0.08f * s))

    val eyeGap = if (icon) 0.15f else 0.165f
    val eyeY = (if (icon) 0.47f else 0.455f) + bob + lookY
    val eyeW = if (icon) 0.12f else 0.12f
    val eyeH = if (icon) 0.17f else 0.17f
    for (side in intArrayOf(-1, 1)) {
        val c = at(0.5f + side * eyeGap + lookX, eyeY)
        if (f.happy < 1f) {
            val h = max(eyeH * f.open.coerceIn(0f, 1f), 0.022f) * s
            drawOval(ink.copy(alpha = ink.alpha * (1f - f.happy)), topLeft = Offset(c.x - eyeW * s / 2f, c.y - h / 2f), size = Size(eyeW * s, h))
            // A glint, for life.
            if (!icon && f.open > 0.6f) drawCircle(Color.White.copy(alpha = 0.85f * (1f - f.happy)), radius = 0.022f * s, center = Offset(c.x - eyeW * s * 0.2f, c.y - h * 0.22f))
        }
        if (f.happy > 0f) {
            // Closed in a smile: an upturned arc.
            val arc = Path().apply {
                moveTo(c.x - eyeW * s * 0.7f, c.y + 0.025f * s)
                quadraticTo(c.x, c.y - 0.085f * s, c.x + eyeW * s * 0.7f, c.y + 0.025f * s)
            }
            drawPath(arc, ink.copy(alpha = ink.alpha * f.happy), style = Stroke(width = (if (icon) 0.075f else 0.045f) * s, cap = StrokeCap.Round))
        }
    }
    if (!icon) {
        for (side in intArrayOf(-1, 1)) {
            val c = at(0.5f + side * 0.28f, 0.6f + bob)
            drawOval(cheeks.copy(alpha = 0.22f + 0.3f * f.happy), topLeft = Offset(c.x - 0.065f * s, c.y - 0.035f * s), size = Size(0.13f * s, 0.07f * s))
        }
    }

    // The mouth: open as it chatters; otherwise a curve, a smile or less, pushed aside and tilted as it muses.
    val mouth = at(0.5f - f.musing * 0.05f + lookX * 0.3f, (if (icon) 0.66f else 0.64f) + bob)
    val line = (if (icon) 0.075f else 0.042f) * s
    if (f.talk > 0.05f) {
        val w = (if (icon) 0.16f else 0.11f) * (0.75f + 0.25f * f.talk) * s
        val h = ((if (icon) 0.05f else 0.03f) + 0.075f * f.talk) * s
        drawOval(ink, topLeft = Offset(mouth.x - w / 2f, mouth.y - h / 2f + 0.01f * s), size = Size(w, h))
    } else {
        val w = (if (icon) 0.22f else 0.16f) * (1f + f.happy * 0.35f) * s
        val curve = 0.075f * f.smile * s
        val tilt = f.musing * 0.025f * s
        val path = Path().apply {
            moveTo(mouth.x - w / 2f, mouth.y - tilt)
            quadraticTo(mouth.x, mouth.y + curve, mouth.x + w / 2f, mouth.y + tilt)
        }
        drawPath(path, ink, style = Stroke(width = line, cap = StrokeCap.Round))
    }

    // Thought bubbles, rising one after another from its head.
    f.thinking?.let { beat ->
        val spots = if (icon) listOf(Triple(0.86f, 0.14f, 0.055f), Triple(0.98f, 0.0f, 0.075f))
        else listOf(Triple(0.82f, 0.18f, 0.032f), Triple(0.94f, 0.04f, 0.044f), Triple(1.07f, -0.12f, 0.058f))
        spots.forEachIndexed { k, (x, y, r) ->
            val phase = ((beat - k * 0.2f) % 1f + 1f) % 1f
            val a = when {
                phase < 0.15f -> phase / 0.15f
                phase < 0.7f -> 1f
                else -> 1f - (phase - 0.7f) / 0.3f
            }
            drawCircle(bubbles.copy(alpha = bubbles.alpha * a), radius = r * s * (0.75f + 0.25f * a), center = at(x, y))
        }
    }
}
