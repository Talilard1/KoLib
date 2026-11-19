package dev.kolib.app.ui.ai

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kolib.app.KoLibApp
import dev.kolib.app.ai.NotesWriter
import dev.kolib.app.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * The AI's character on the screen, chosen in the AI settings: each a little creature of its own, which shows what
 * the AI is up to (reading, thinking, writing) and reacts to what's done to it (picked up, thrown, left alone).
 */
enum class Mascot(val id: String, val title: String) {
    ROBOT("robot", "Bolt"),
    ANIME("anime", "Yume"),
    CAT("cat", "Mochi"),
    OWL("owl", "Sage"),
    GHOST("ghost", "Boo");

    /** What it is, in a word. */
    val kind: String
        get() = when (this) {
            ROBOT -> tr("Robot")
            ANIME -> tr("Anime")
            CAT -> tr("Cat")
            OWL -> tr("Owl")
            GHOST -> tr("Ghost")
        }

    internal val art: MascotArt
        get() = when (this) {
            ROBOT -> RobotArt
            ANIME -> AnimeArt
            CAT -> CatArt
            OWL -> OwlArt
            GHOST -> GhostArt
        }

    companion object {
        fun of(id: String?): Mascot = entries.firstOrNull { it.id == id } ?: ROBOT
    }
}

/** What the AI is doing, for its character to show. */
enum class BotMood { IDLE, READING, THINKING, WRITING, NOTING, HAPPY, SAD }

/** What the AI is doing now: answering in the chat; in a book, the chapter's summary, or notes on the chapters. */
@Composable
fun aiMood(place: ChatPlace? = null): BotMood {
    val app = LocalContext.current.applicationContext as KoLibApp
    val chat = app.chat
    val reading by chat.reading.collectAsState()
    val book = place as? ChatPlace.Book
    val stage = book?.companion?.stage?.collectAsState()?.value
    val summary = book?.companion?.summary?.collectAsState()?.value.orEmpty()
    val notes = book?.writer?.status?.collectAsState()?.value
    val failed = chat.turns.lastOrNull()?.failed?.collectAsState()?.value
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
        app.aiPanel.unseen -> if (failed != null) BotMood.SAD else BotMood.HAPPY
        notes is NotesWriter.Status.Writing -> BotMood.NOTING
        else -> BotMood.IDLE
    }
}

/** What the character is doing: what the AI is up to, what's being done to it, or what it does to pass the time. */
enum class Act { IDLE, HELLO, READING, THINKING, WRITING, HAPPY, SAD, SLEEPING, HELD, SCARED, FLYING, DIZZY, BUMPED }

/** Things it does now and then while it waits. */
enum class Fidget { NONE, LOOK, HOP, WAVE, YAWN, HUM, WINK, SPECIAL }

/**
 * The character's motion, frame by frame: what it's doing ([act]) and how each part of it moves, eased from one act
 * to the next. Told what the AI is up to ([mood]) and what's done to it (held, thrown, bumped). The drawing reads
 * [frame], so it's drawn again each time it moves. [calm] (an icon): it doesn't fidget or fall asleep.
 */
@Stable
class MascotMotion(private val calm: Boolean = false, seed: Int = 0) {
    var frame by mutableLongStateOf(0L)
        private set

    // What it's told.
    var mood = BotMood.IDLE
    var library = false
    var held = false
    var velocity = Offset.Zero

    /** Held over the ✕ that would close it. */
    var scared = false

    /** Showing itself off (in the settings): it goes through its acts in turn, starting [demoShift] seconds in. */
    var demo = false
    var demoShift = 0f

    // For pictures of it (in tests): an act it's put in and a fidget part-way through, whatever it's told; no blinks.
    internal var forced: Act? = null
    internal var forcedFidget: Fidget? = null
    internal var forcedFidgetAt = 0.5f
    internal var noBlinks = false

    var t = 0f
        private set
    var act = Act.IDLE
        private set
    var actT = 0f
        private set
    var fidget = Fidget.NONE
        private set
    var fidgetT = 0f
        private set

    /** How long ago it was last bumped, in seconds. */
    val sinceBump: Float get() = t - bumpAt

    // How each part of it is, 0 to 1 unless said: eased towards what its act calls for.
    var open = 1f; private set
    var wink = 0f; private set
    var lookX = 0f; private set
    var lookY = 0f; private set
    var joy = 0f; private set
    var sleepy = 0f; private set
    var wide = 0f; private set
    var swirl = 0f; private set
    var sad = 0f; private set
    var book = 0f; private set
    var note = 0f; private set
    var chin = 0f; private set
    var cheer = 0f; private set
    var wave = 0f; private set
    var dangle = 0f; private set
    var yawn = 0f; private set
    var hum = 0f; private set
    var special = 0f; private set
    var talk = 0f; private set

    /** Degrees it leans its head (or itself). */
    var tilt = 0f; private set

    /** How much it floats up and down, at rest. */
    var hover = 1f; private set

    /** How high it is in a hop. */
    var hop = 0f; private set

    /** Squashed (below 0) or stretched (above). */
    var squash = 0f; private set

    /** Leaning into its motion, −1 to 1. */
    var lean = 0f; private set

    /** Degrees it's spun round, thrown hard. */
    var spin = 0f; private set

    private val random = Random(seed)
    private var bumpAt = -99f
    private var dizzyUntil = -99f
    private var helloAt = -99f
    private var quietSince = 0f
    private var lastMood = BotMood.IDLE
    private var squashV = 0f
    private var nextBlink = 1.2f + random.nextFloat() * 2f
    private var blinkAt = -99f
    private var twice = false
    private var nextFidget = 5f + random.nextFloat() * 4f
    private var nextSyllable = 0f
    private var talkTo = 0f
    private var glanceX = 0f
    private var glanceY = 0f
    private var nextGlance = 2f
    private var night = false
    private var nightAt = -99f
    private var settled = false

    /** Whether an icon has come to rest: nothing about it moves until the AI does something. */
    val resting: Boolean get() = calm && settled && act == Act.IDLE

    /** Whether it's being thrown about, held or knocked: then it's drawn on every frame the screen shows. */
    val lively: Boolean get() = act == Act.HELD || act == Act.SCARED || act == Act.FLYING || act == Act.BUMPED || abs(squash) > 0.02f

    fun appear() {
        helloAt = t
    }

    /** It hit an edge at [impact] pixels a second. */
    fun bump(impact: Float) {
        if (impact < 500f) return
        bumpAt = t
        squashV -= min(impact / 2500f, 2.5f) * 7f
    }

    /** Thrown, at most [peak] pixels a second, bouncing [bounces] times: dizzy, if it was hard. */
    fun thrown(peak: Float, bounces: Int) {
        if (peak > 4200f || bounces >= 2) dizzyUntil = t + 1.3f + 0.45f * bounces.coerceAtMost(4)
    }

    fun step(dt: Float) {
        t += dt
        if (t - nightAt > 30f) {
            nightAt = t
            night = java.time.LocalTime.now().hour.let { it < 7 || it >= 22 }
        }
        val speed = velocity.getDistance()
        if (mood != lastMood || held || speed > 60f) quietSince = t
        lastMood = mood
        val next = forced ?: when {
            demo -> demoAct()
            held -> if (scared) Act.SCARED else Act.HELD
            speed > 900f -> Act.FLYING
            t - bumpAt < 0.4f -> Act.BUMPED
            t < dizzyUntil -> Act.DIZZY
            mood == BotMood.READING -> Act.READING
            mood == BotMood.THINKING -> Act.THINKING
            mood == BotMood.WRITING || mood == BotMood.NOTING -> Act.WRITING
            mood == BotMood.SAD -> Act.SAD
            mood == BotMood.HAPPY -> Act.HAPPY
            t - helloAt < 1.8f -> Act.HELLO
            !calm && t - quietSince > (if (night) 25f else 45f) -> Act.SLEEPING
            else -> Act.IDLE
        }
        if (next != act) {
            act = next
            actT = 0f
            if (next != Act.IDLE) fidget = Fidget.NONE
        } else actT += dt
        fidgets(dt)
        blinks()
        chatter()
        ease(dt)
        physics(dt, speed)
        frame++
    }

    /** In the settings: its acts in turn, a few seconds each. */
    private fun demoAct(): Act {
        val acts = arrayOf(Act.HELLO, Act.READING, Act.THINKING, Act.WRITING, Act.HAPPY, Act.IDLE, Act.SLEEPING, Act.DIZZY)
        val each = 2.6f
        return acts[(((t + demoShift) / each).toInt()) % acts.size]
    }

    private fun fidgets(dt: Float) {
        forcedFidget?.let {
            fidget = it
            fidgetT = forcedFidgetAt * lengthOf(it)
            return
        }
        if (calm || act != Act.IDLE) {
            fidget = Fidget.NONE
            nextFidget = t + 4f + random.nextFloat() * 4f
            return
        }
        if (fidget != Fidget.NONE) {
            fidgetT += dt
            if (fidgetT > lengthOf(fidget)) {
                fidget = Fidget.NONE
                nextFidget = t + 4.5f + random.nextFloat() * 6f
            }
            return
        }
        if (t < nextFidget) return
        // Sleepier at night, and as it's left alone longer: it yawns, then nods off.
        val weights = if (night || t - quietSince > 25f) intArrayOf(2, 1, 1, 5, 1, 1, 2) else intArrayOf(3, 2, 2, 1, 2, 2, 3)
        var pick = random.nextInt(weights.sum())
        val kinds = arrayOf(Fidget.LOOK, Fidget.HOP, Fidget.WAVE, Fidget.YAWN, Fidget.HUM, Fidget.WINK, Fidget.SPECIAL)
        for (i in kinds.indices) {
            if (pick < weights[i]) {
                fidget = kinds[i]
                break
            }
            pick -= weights[i]
        }
        fidgetT = 0f
    }

    private fun lengthOf(f: Fidget): Float = when (f) {
        Fidget.NONE -> 0f
        Fidget.LOOK -> 2.4f
        Fidget.HOP -> 0.8f
        Fidget.WAVE -> 1.8f
        Fidget.YAWN -> 2.4f
        Fidget.HUM -> 3.2f
        Fidget.WINK -> 1.1f
        Fidget.SPECIAL -> 2.6f
    }

    private fun blinks() {
        // An icon doesn't blink: at rest, it's still.
        if (noBlinks || calm) return
        if (t >= nextBlink) {
            blinkAt = t
            twice = random.nextInt(5) == 0
            nextBlink = t + 2.2f + random.nextFloat() * 3.6f
        }
    }

    /** How closed its eyes are in a blink, now. */
    private fun blinking(): Float {
        val b = t - blinkAt
        fun one(x: Float) = when {
            x < 0f -> 0f
            x < 0.07f -> x / 0.07f
            x < 0.19f -> 1f - (x - 0.07f) / 0.12f
            else -> 0f
        }
        return maxOf(one(b), if (twice) one(b - 0.26f) else 0f)
    }

    private fun chatter() {
        val talking = act == Act.WRITING && mood != BotMood.NOTING
        if (!talking) {
            talkTo = 0f
            return
        }
        if (t >= nextSyllable) {
            talkTo = if (talkTo > 0.3f) random.nextFloat() * 0.2f else 0.4f + random.nextFloat() * 0.6f
            nextSyllable = t + 0.07f + random.nextFloat() * 0.07f + if (random.nextInt(7) == 0) 0.25f else 0f
        }
    }

    private fun ease(dt: Float) {
        fun toward(now: Float, to: Float, rate: Float) = now + (to - now) * (1f - exp(-rate * dt))
        val a = act
        val ft = fidgetT
        val f = fidget
        fun inFidget(k: Fidget) = if (f == k) sin(PI.toFloat() * (ft / lengthOf(k)).coerceIn(0f, 1f)).coerceAtLeast(0f).let { min(1f, it * 1.6f) } else 0f

        // Where it looks.
        if (a == Act.IDLE && t >= nextGlance && !calm) {
            val away = random.nextInt(3) != 0
            glanceX = if (away) random.nextFloat() * 1.4f - 0.7f else 0f
            glanceY = if (away) random.nextFloat() * 0.8f - 0.4f else 0f
            nextGlance = t + 1.6f + random.nextFloat() * 3.4f
        }
        val sweep = (actT % 1.25f) / 1.25f
        val (lx, ly) = when (a) {
            Act.IDLE -> if (f == Fidget.LOOK) (if (ft < 1.2f) -0.9f else 0.9f) to -0.1f else glanceX to glanceY
            Act.HELLO -> 0.15f to 0f
            Act.READING -> (if (sweep < 0.86f) -0.75f + 1.5f * sweep / 0.86f else 0.75f - 1.5f * (sweep - 0.86f) / 0.14f) to 0.55f
            Act.THINKING -> (0.55f + 0.15f * sin(t * 0.9f)) to -0.75f
            Act.WRITING -> (-0.25f + 0.2f * sin(t * 1.3f)) to 0.6f
            Act.HAPPY, Act.BUMPED -> 0f to 0f
            Act.SAD -> 0f to 0.6f
            Act.SLEEPING -> 0f to 0.3f
            Act.HELD -> (0.6f * sin(t * 6f)) to -0.35f
            Act.SCARED -> (0.25f * sin(t * 23f)) to 0.75f
            Act.FLYING -> (velocity.x / 2500f).coerceIn(-1f, 1f) to (velocity.y / 2500f).coerceIn(-1f, 1f)
            Act.DIZZY -> 0.6f * cos(t * 9f) to 0.6f * sin(t * 9f)
        }
        val quick = if (a == Act.READING && sweep >= 0.86f) 30f else 9f
        lookX = toward(lookX, lx, quick)
        lookY = toward(lookY, ly, 9f)

        val heldLong = a == Act.HELD && actT > 2f
        joy = toward(joy, when (a) {
            Act.HAPPY -> 1f
            Act.HELLO -> 0.35f
            Act.FLYING -> 0.5f
            Act.HELD -> if (heldLong) 0.7f else 0f
            else -> 0.6f * inFidget(Fidget.HUM)
        }, 7f)
        sleepy = toward(sleepy, if (a == Act.SLEEPING) 1f else 0f, if (a == Act.SLEEPING) 1.6f else 6f)
        wide = toward(wide, when (a) {
            Act.HELD -> if (heldLong) 0.3f else 1f
            Act.SCARED -> 1f
            Act.FLYING -> 0.7f
            Act.BUMPED -> 1f
            else -> if (f == Fidget.SPECIAL) 0.3f else 0f
        }, 14f)
        swirl = toward(swirl, if (a == Act.DIZZY) 1f else 0f, 10f)
        sad = toward(sad, when (a) {
            Act.SAD -> 1f
            Act.SCARED -> 0.75f
            else -> 0f
        }, if (a == Act.SCARED) 12f else 5f)
        book = toward(book, if (a == Act.READING) 1f else 0f, 6f)
        note = toward(note, if (a == Act.WRITING) 1f else 0f, 6f)
        chin = toward(chin, if (a == Act.THINKING) 1f else 0f, 6f)
        cheer = toward(cheer, when {
            a == Act.HAPPY -> if (actT < 2.4f) 1f else 0.3f
            f == Fidget.YAWN -> 0.8f * inFidget(Fidget.YAWN)
            else -> 0f
        }, 7f)
        wave = toward(wave, if (a == Act.HELLO || f == Fidget.WAVE) 1f else 0f, 8f)
        dangle = toward(dangle, when (a) {
            Act.HELD, Act.SCARED -> 1f
            Act.FLYING -> 0.5f
            else -> 0f
        }, 10f)
        yawn = toward(yawn, inFidget(Fidget.YAWN), 6f)
        hum = toward(hum, inFidget(Fidget.HUM), 5f)
        special = if (f == Fidget.SPECIAL) (ft / lengthOf(Fidget.SPECIAL)).coerceIn(0f, 1f) else toward(special, 0f, 8f)
        wink = toward(wink, inFidget(Fidget.WINK), 14f)
        talk = toward(talk, maxOf(talkTo, yawn), 26f)
        open = ((1f - blinking()) * (1f - sleepy) * (1f - 0.75f * yawn)).coerceIn(0f, 1f)

        tilt = toward(tilt, when (a) {
            Act.THINKING -> 7f + 2f * sin(t * 0.8f)
            Act.SAD -> -4f
            Act.SCARED -> 3.5f * sin(t * 47f)
            Act.SLEEPING -> -9f + 3f * sin(t * 0.9f)
            Act.DIZZY -> 11f * sin(t * 5.5f)
            Act.HAPPY -> 5f * sin(t * 7f)
            else -> 6f * sin(t * 2.4f) * inFidget(Fidget.HUM) + 7f * inFidget(Fidget.WINK)
        }, 6f)
        hover = toward(hover, when (a) {
            Act.READING, Act.WRITING -> 0.5f
            Act.SAD -> 0.3f
            Act.SLEEPING -> 0.25f
            Act.HELD, Act.SCARED -> 0f
            else -> 1f
        }, 3f)
        hop = when {
            a == Act.HAPPY -> abs(sin(actT * 6.6f)) * (1f - actT / 1.9f).coerceIn(0f, 1f)
            f == Fidget.HOP -> sin(PI.toFloat() * (ft / 0.8f).coerceIn(0f, 1f))
            a == Act.HELLO -> abs(sin(actT * 5f)) * (1f - actT / 0.7f).coerceIn(0f, 1f) * 0.6f
            else -> toward(hop, 0f, 10f)
        }
        settled = abs(lookX - lx) < 0.01f && abs(lookY - ly) < 0.01f && joy < 0.01f && wide < 0.01f && cheer < 0.01f &&
            wave < 0.01f && book < 0.01f && note < 0.01f && chin < 0.01f && abs(tilt) < 0.2f
    }

    private fun physics(dt: Float, speed: Float) {
        // Leaning into its flight, and stretched along it; spinning if thrown hard.
        lean += ((velocity.x / 2500f).coerceIn(-1f, 1f) - lean) * (1f - exp(-8f * dt))
        val stretch = if (act == Act.FLYING) min(speed / 5000f, 0.35f) else 0f
        squashV += (-170f * (squash - stretch) - 11f * squashV) * dt
        squash = (squash + squashV * dt).coerceIn(-0.7f, 0.7f)
        if (act == Act.FLYING && speed > 3200f) spin += sign(velocity.x) * speed / 3200f * 540f * dt
        else {
            spin %= 360f
            if (spin > 180f) spin -= 360f
            if (spin < -180f) spin += 360f
            spin += (0f - spin) * (1f - exp(-9f * dt))
        }
    }
}

/**
 * Whether the characters move. Not when Android's animations are turned off (for those who'd rather nothing moved),
 * nor in tests, which draw them as they are at a moment of their own choosing.
 */
internal var mascotsMove = true

/**
 * A [MascotMotion] that moves for as long as it's shown: about 30 times a second, on every frame while it's thrown
 * about. An icon ([calm]) moves only until it comes to rest, and again when [key] changes (what the AI is doing).
 * (It waits between frames rather than asking for every one, which saves battery and leaves the app idle in between.)
 */
@Composable
fun rememberMascotMotion(calm: Boolean = false, seed: Int = 0, key: Any? = null): MascotMotion {
    val motion = remember { MascotMotion(calm, seed) }
    LaunchedEffect(motion, key) {
        if (!mascotsMove || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            // Still: as it would be once settled into what it's doing.
            repeat(90) { motion.step(1 / 60f) }
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            if (motion.resting) break
            if (!motion.lively) delay(16)
            val now = withFrameNanos { it }
            if (last != 0L) motion.step(((now - last) / 1e9f).coerceIn(0f, 0.1f))
            last = now
        }
    }
    return motion
}

/** What the character is doing, for someone who can't see it. */
private fun said(act: Act): String = when (act) {
    Act.READING -> tr("Reading…")
    Act.THINKING -> tr("Thinking…")
    Act.WRITING -> tr("Writing…")
    Act.HAPPY -> tr("Answer ready")
    Act.SLEEPING -> tr("Asleep")
    else -> ""
}

/** The character, whole: for the bubble and the settings. */
@Composable
fun MascotView(mascot: Mascot, motion: MascotMotion, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { said(motion.act).takeIf { it.isNotEmpty() }?.let { stateDescription = it } }) {
        motion.frame
        drawMascot(mascot.art, motion, size.minDimension)
    }
}

/** Its head alone, as an icon: in the bar at the top, and the like. */
@Composable
fun MascotIcon(mascot: Mascot, mood: BotMood, modifier: Modifier = Modifier) {
    val motion = rememberMascotMotion(calm = true, key = mood)
    motion.mood = mood
    MascotHead(mascot, motion, modifier)
}

/** Its head alone, moving as [motion] says. */
@Composable
internal fun MascotHead(mascot: Mascot, motion: MascotMotion, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        motion.frame
        with(mascot.art) { icon(motion, size.minDimension) }
    }
}

/** The mascot chosen in the settings. */
@Composable
fun chosenMascot(): Mascot {
    val app = LocalContext.current.applicationContext as KoLibApp
    val settings by app.ai.settings.collectAsState()
    return Mascot.of(settings.mascot)
}

/** How a character is drawn. Its box is [s] across; its parts are placed as fractions of that. */
internal interface MascotArt {
    /** Where its head is, and how big, as fractions of its box: for its thoughts, its sleep and the like. */
    val head: Offset
    val headR: Float

    /** Draws it whole. */
    fun DrawScope.figure(m: MascotMotion, s: Float)

    /** Draws its head alone, filling the box: for icons. */
    fun DrawScope.icon(m: MascotMotion, s: Float)

    /** Its way of showing it's thinking: a thought cloud, unless it has its own. */
    fun DrawScope.thinking(m: MascotMotion, s: Float) = thoughtCloud(Offset((head.x + headR * 0.85f) * s, (head.y - headR * 0.9f) * s), s, m.t, Color.White, Color(0xFF4A4458))
}

/** The whole scene: its shadow, itself (bobbing, leaning, squashed or stretched), and what's going on round it. */
internal fun DrawScope.drawMascot(art: MascotArt, m: MascotMotion, s: Float) {
    val bob = (sin(m.t * 2.3f) * 0.022f * m.hover - m.hop * 0.13f) * s
    val ground = 0.955f * s
    // Its shadow on the "ground", smaller and fainter as it rises.
    val lift = (m.hop * 0.5f + (if (m.act == Act.HELD || m.act == Act.SCARED || m.act == Act.FLYING) 0.6f else 0f)).coerceAtMost(1f)
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - 0.6f * lift)), topLeft = Offset(s * (0.5f - 0.2f * (1f - 0.4f * lift)), ground - 0.025f * s), size = Size(0.4f * s * (1f - 0.4f * lift), 0.05f * s))
    if (m.act == Act.FLYING) speedLines(m, s)
    withTransform({
        translate(0f, bob)
        rotate(m.tilt * 0.6f + m.lean * 12f + m.spin, pivot = Offset(0.5f * s, 0.6f * s))
        scale(1f - 0.13f * m.squash, 1f + 0.17f * m.squash, pivot = Offset(0.5f * s, 0.93f * s))
    }) {
        with(art) { figure(m, s) }
    }
    effects(art, m, s, bob)
}

// ---------------------------------------------------------------------------------------------------------------
// What goes on round it: thoughts, sleep, joy, dizziness, alarm, music.

private val ink = Color(0xFF3A3346)
private val sparkleGold = Color(0xFFFFD45C)
private val heartPink = Color(0xFFFF6F91)
private val sweatBlue = Color(0xFF7FD3FF)

private fun DrawScope.effects(art: MascotArt, m: MascotMotion, s: Float, bob: Float) {
    val hx = art.head.x * s
    val hy = art.head.y * s + bob
    val r = art.headR * s
    when (m.act) {
        Act.THINKING -> {
            with(art) { thinking(m, s) }
            // Working at it a while: a bead of sweat.
            if (m.actT > 6f) sweat(Offset(hx - r * 0.95f, hy - r * 0.35f), s, m.t)
        }
        Act.SLEEPING -> zzz(Offset(hx + r * 0.75f, hy - r * 0.8f), s, m.t)
        Act.HAPPY -> if (m.actT < 3.2f) {
            sparkles(Offset(0.5f * s, 0.5f * s + bob), 0.46f * s, m.t, 5, (1f - (m.actT - 2.4f).coerceAtLeast(0f) / 0.8f))
            hearts(Offset(hx, hy - r), s, m.actT)
        }
        Act.HELLO -> sparkles(Offset(0.5f * s, 0.45f * s + bob), 0.44f * s, m.t, 4, (1f - m.actT / 1.8f).coerceIn(0f, 1f))
        Act.DIZZY -> dizzyStars(Offset(hx, hy - r * 0.95f), r * 1.05f, m.t)
        Act.SAD -> sweat(Offset(hx + r * 0.95f, hy - r * 0.3f), s, m.t)
        Act.HELD -> {
            if (m.actT < 2f) sweat(Offset(hx + r, hy - r * 0.4f), s, m.t)
            if (m.actT < 0.7f) mark(Offset(hx - r * 1.05f, hy - r * 1.05f), s * (0.9f + 0.4f * (1f - m.actT / 0.7f)))
        }
        Act.BUMPED -> mark(Offset(hx + r * 1.1f, hy - r * 1.1f), s)
        // Over the ✕: please, no.
        Act.SCARED -> {
            sweat(Offset(hx + r * 1.0f, hy - r * 0.45f), s, m.t * 1.7f)
            sweat(Offset(hx - r * 1.05f, hy - r * 0.15f), s, m.t * 1.3f + 0.4f)
        }
        else -> {}
    }
    if (m.hum > 0.05f) notes(Offset(hx + r * 0.8f, hy - r * 0.5f), s, m.t, m.hum)
    if (m.wink > 0.3f) sparkle(Offset(hx + r * 0.85f, hy - r * 0.25f), 0.06f * s * m.wink, m.t * 2f, sparkleGold)
}

private fun DrawScope.speedLines(m: MascotMotion, s: Float) {
    val v = m.velocity
    val speed = v.getDistance().coerceAtLeast(1f)
    val dir = Offset(-v.x / speed, -v.y / speed)
    val across = Offset(-dir.y, dir.x)
    val a = min(1f, speed / 4000f)
    for (i in -2..2) {
        val from = Offset(0.5f * s, 0.55f * s) + across * (i * 0.1f * s) + dir * (0.3f * s)
        val len = (0.18f + 0.08f * ((i + m.t * 7f) % 2f)) * s * a
        drawLine(Color.White.copy(alpha = 0.6f * a), from, from + dir * len, strokeWidth = 0.022f * s, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.25f * a), from, from + dir * len, strokeWidth = 0.008f * s, cap = StrokeCap.Round)
    }
}

/** A thought cloud above [at], with dots that come one after another. */
internal fun DrawScope.thoughtCloud(at: Offset, s: Float, t: Float, fill: Color, line: Color) {
    val w = 0.012f * s
    // Bubbles rising to it.
    for (k in 0..1) {
        val c = at + Offset(-0.12f * s + k * 0.05f * s, 0.13f * s - k * 0.06f * s)
        val rr = (0.018f + k * 0.012f) * s
        drawCircle(fill, rr, c)
        drawCircle(line, rr, c, style = Stroke(w))
    }
    val bob = sin(t * 2f) * 0.01f * s
    val puffs = listOf(Offset(-0.07f, 0.0f) to 0.055f, Offset(0f, -0.03f) to 0.068f, Offset(0.075f, 0f) to 0.055f, Offset(0.0f, 0.035f) to 0.05f)
    val cloud = Path()
    puffs.forEach { (o, rr) -> cloud.addOval(Rect(at + Offset(o.x * s, o.y * s + bob), rr * s)) }
    drawPath(cloud, line, style = Stroke(w * 2f))
    drawPath(cloud, fill)
    for (i in 0..2) {
        val phase = ((t * 1.6f - i * 0.25f) % 1f + 1f) % 1f
        val up = if (phase < 0.5f) sin(phase * 2f * PI.toFloat()) * 0.012f * s else 0f
        drawCircle(line.copy(alpha = 0.85f), 0.012f * s, at + Offset((i - 1) * 0.04f * s, 0.005f * s + bob - up))
    }
}

/** Z's floating up from [at], as it sleeps. */
internal fun DrawScope.zzz(at: Offset, s: Float, t: Float) {
    for (i in 0..2) {
        val phase = ((t * 0.45f + i / 3f) % 1f)
        val size = (0.04f + 0.035f * phase) * s
        val c = at + Offset(phase * 0.14f * s + sin(phase * 6f) * 0.01f * s, -phase * 0.22f * s)
        val a = when {
            phase < 0.15f -> phase / 0.15f
            phase > 0.75f -> (1f - phase) / 0.25f
            else -> 1f
        }
        val z = Path().apply {
            moveTo(c.x - size / 2, c.y - size / 2)
            lineTo(c.x + size / 2, c.y - size / 2)
            lineTo(c.x - size / 2, c.y + size / 2)
            lineTo(c.x + size / 2, c.y + size / 2)
        }
        drawPath(z, Color.White.copy(alpha = a), style = Stroke(size * 0.42f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(z, Color(0xFF6C7BD9).copy(alpha = a), style = Stroke(size * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A four-pointed twinkle. */
internal fun DrawScope.sparkle(c: Offset, r: Float, turn: Float, color: Color) {
    if (r <= 0f) return
    val p = Path().apply {
        for (i in 0 until 8) {
            val ang = turn + i * PI.toFloat() / 4f
            val rr = if (i % 2 == 0) r else r * 0.28f
            val x = c.x + cos(ang) * rr
            val y = c.y + sin(ang) * rr
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.sparkles(c: Offset, radius: Float, t: Float, count: Int, strength: Float) {
    if (strength <= 0f) return
    for (i in 0 until count) {
        val ang = i * 2f * PI.toFloat() / count + 0.6f
        val tw = (sin(t * 6f + i * 1.7f) * 0.5f + 0.5f)
        val at = c + Offset(cos(ang) * radius, sin(ang) * radius * 0.85f)
        sparkle(at, (0.035f + 0.03f * tw) * radius / 0.46f * strength, t * 1.5f + i, if (i % 2 == 0) sparkleGold else Color.White)
    }
}

internal fun heartPath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y + r * 0.9f)
    cubicTo(c.x - r * 1.4f, c.y - r * 0.1f, c.x - r * 0.8f, c.y - r * 1.2f, c.x, c.y - r * 0.45f)
    cubicTo(c.x + r * 0.8f, c.y - r * 1.2f, c.x + r * 1.4f, c.y - r * 0.1f, c.x, c.y + r * 0.9f)
    close()
}

private fun DrawScope.hearts(at: Offset, s: Float, t: Float) {
    for (i in 0..2) {
        val phase = t * 0.7f - i * 0.28f
        if (phase < 0f || phase > 1f) continue
        val c = at + Offset((i - 1) * 0.12f * s + sin(phase * 7f + i) * 0.02f * s, -phase * 0.3f * s)
        val a = if (phase > 0.7f) (1f - phase) / 0.3f else 1f
        val heart = heartPath(c, (0.035f + 0.015f * i) * s)
        drawPath(heart, heartPink.copy(alpha = a))
        drawPath(heart, Color.White.copy(alpha = a * 0.8f), style = Stroke(0.008f * s))
    }
}

internal fun starPath(c: Offset, r: Float, turn: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val ang = turn - PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + cos(ang) * rr
        val y = c.y + sin(ang) * rr
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.dizzyStars(c: Offset, r: Float, t: Float) {
    for (i in 0..2) {
        val ang = t * 4.5f + i * 2f * PI.toFloat() / 3f
        val at = c + Offset(cos(ang) * r, sin(ang) * r * 0.3f)
        val star = starPath(at, r * 0.2f * (0.85f + 0.15f * sin(ang)), t * 3f)
        drawPath(star, sparkleGold)
        drawPath(star, Color(0xFFB8860B), style = Stroke(r * 0.03f, join = StrokeJoin.Round))
    }
}

internal fun dropPath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y - r * 1.6f)
    cubicTo(c.x + r * 0.2f, c.y - r * 0.9f, c.x + r, c.y - r * 0.3f, c.x + r, c.y + r * 0.2f)
    cubicTo(c.x + r, c.y + r * 0.8f, c.x - r, c.y + r * 0.8f, c.x - r, c.y + r * 0.2f)
    cubicTo(c.x - r, c.y - r * 0.3f, c.x - r * 0.2f, c.y - r * 0.9f, c.x, c.y - r * 1.6f)
    close()
}

private fun DrawScope.sweat(c: Offset, s: Float, t: Float) {
    val slide = ((t * 0.6f) % 1f) * 0.05f * s
    val drop = dropPath(c + Offset(0f, slide), 0.028f * s)
    drawPath(drop, sweatBlue)
    drawPath(drop, Color.White, style = Stroke(0.007f * s))
    drawCircle(Color.White.copy(alpha = 0.85f), 0.008f * s, c + Offset(-0.008f * s, slide))
}

/** An exclamation mark in a burst: alarm. */
private fun DrawScope.mark(c: Offset, s: Float) {
    val burst = starPath(c, 0.075f * s, 0.2f)
    drawPath(burst, Color(0xFFFFE066))
    drawPath(burst, Color(0xFFE0A100), style = Stroke(0.01f * s, join = StrokeJoin.Round))
    drawLine(Color(0xFFD62F2F), c + Offset(0f, -0.03f * s), c + Offset(0f, 0.008f * s), strokeWidth = 0.018f * s, cap = StrokeCap.Round)
    drawCircle(Color(0xFFD62F2F), 0.01f * s, c + Offset(0f, 0.03f * s))
}

private fun DrawScope.notes(at: Offset, s: Float, t: Float, strength: Float) {
    for (i in 0..1) {
        val phase = ((t * 0.55f + i * 0.5f) % 1f)
        val c = at + Offset(phase * 0.12f * s + sin(phase * 8f) * 0.015f * s, -phase * 0.2f * s)
        val a = strength * if (phase > 0.7f) (1f - phase) / 0.3f else 1f
        val col = Color(0xFF7A5CFF).copy(alpha = a)
        drawOval(col, topLeft = c + Offset(-0.02f * s, -0.012f * s), size = Size(0.035f * s, 0.026f * s))
        drawLine(col, c + Offset(0.014f * s, 0f), c + Offset(0.014f * s, -0.065f * s), strokeWidth = 0.009f * s)
        drawLine(col, c + Offset(0.014f * s, -0.065f * s), c + Offset(0.04f * s, -0.045f * s), strokeWidth = 0.009f * s, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Things it holds: a book, a pile of books (in the library), a notepad and pencil.

private val paper = Color(0xFFFFFBF0)
private val printed = Color(0xFFB9B2A4)

/** An open book, [w] across, centred on [c]; its pages turn now and then as it reads. */
internal fun DrawScope.openBook(c: Offset, w: Float, t: Float, cover: Color, line: Color, reading: Boolean) {
    val h = w * 0.52f
    val stroke = w * 0.035f
    val back = Path().apply {
        moveTo(c.x - w * 0.53f, c.y - h * 0.42f)
        lineTo(c.x, c.y - h * 0.32f)
        lineTo(c.x + w * 0.53f, c.y - h * 0.42f)
        lineTo(c.x + w * 0.53f, c.y + h * 0.55f)
        lineTo(c.x, c.y + h * 0.66f)
        lineTo(c.x - w * 0.53f, c.y + h * 0.55f)
        close()
    }
    drawPath(back, cover)
    drawPath(back, line, style = Stroke(stroke, join = StrokeJoin.Round))
    for (side in intArrayOf(-1, 1)) {
        val page = Path().apply {
            moveTo(c.x, c.y - h * 0.38f)
            quadraticTo(c.x + side * w * 0.24f, c.y - h * 0.5f, c.x + side * w * 0.48f, c.y - h * 0.44f)
            lineTo(c.x + side * w * 0.48f, c.y + h * 0.46f)
            quadraticTo(c.x + side * w * 0.24f, c.y + h * 0.4f, c.x, c.y + h * 0.54f)
            close()
        }
        drawPath(page, paper)
        drawPath(page, line.copy(alpha = 0.5f), style = Stroke(stroke * 0.6f))
        for (k in 0..3) {
            val y = c.y - h * 0.22f + k * h * 0.16f
            drawLine(printed, Offset(c.x + side * w * 0.07f, y), Offset(c.x + side * w * (0.38f - (k % 2) * 0.08f), y - side * 0f), strokeWidth = stroke * 0.55f, cap = StrokeCap.Round)
        }
    }
    // A page turning over, now and then.
    if (reading) {
        val p = (t % 2.6f) / 0.6f
        if (p < 1f) {
            val x = c.x + w * 0.46f * cos(p * PI.toFloat())
            val lift = sin(p * PI.toFloat()) * h * 0.25f
            val turning = Path().apply {
                moveTo(c.x, c.y - h * 0.38f)
                quadraticTo((c.x + x) / 2f, c.y - h * 0.5f - lift, x, c.y - h * 0.44f - lift * 0.4f)
                lineTo(x, c.y + h * 0.46f - lift * 0.4f)
                quadraticTo((c.x + x) / 2f, c.y + h * 0.4f - lift, c.x, c.y + h * 0.54f)
                close()
            }
            drawPath(turning, Color.White)
            drawPath(turning, line.copy(alpha = 0.5f), style = Stroke(stroke * 0.6f))
        }
    }
    drawLine(line.copy(alpha = 0.6f), Offset(c.x, c.y - h * 0.36f), Offset(c.x, c.y + h * 0.54f), strokeWidth = stroke * 0.7f)
}

/** A little pile of books, as it looks through the library. */
internal fun DrawScope.bookPile(c: Offset, w: Float, line: Color) {
    val colours = listOf(Color(0xFFE76F51), Color(0xFF2A9D8F), Color(0xFFE9C46A))
    val h = w * 0.17f
    colours.forEachIndexed { i, col ->
        val y = c.y + (1 - i) * h
        val dx = (i - 1) * w * 0.05f
        val r = Rect(c.x - w * (0.48f - i * 0.04f) + dx, y - h / 2, c.x + w * (0.48f - i * 0.03f) + dx, y + h / 2)
        drawRoundRect(col, r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(h * 0.25f))
        drawRoundRect(line, r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(h * 0.25f), style = Stroke(w * 0.03f))
        drawLine(paper, Offset(r.right - w * 0.1f, y - h * 0.25f), Offset(r.right - w * 0.1f, y + h * 0.25f), strokeWidth = w * 0.035f)
    }
}

/** A notepad, [w] across, with lines written on it as it goes. */
internal fun DrawScope.notepad(c: Offset, w: Float, t: Float, line: Color) {
    val h = w * 0.72f
    val r = Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
    drawRoundRect(paper, r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
    drawRoundRect(line, r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(w * 0.035f))
    for (i in 0..3) drawCircle(line, w * 0.03f, Offset(r.left + w * (0.2f + i * 0.2f), r.top))
    val done = (t * 0.9f) % 4f
    for (k in 0..3) {
        val y = r.top + h * (0.28f + k * 0.18f)
        val full = (done - k).coerceIn(0f, 1f)
        if (full > 0f) drawLine(Color(0xFF5B6CC9), Offset(r.left + w * 0.12f, y), Offset(r.left + w * (0.12f + 0.72f * full), y), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    }
}

/** Where the pencil's point is on a [notepad] centred on [c], [w] across, as it writes. */
internal fun pencilTip(c: Offset, w: Float, t: Float): Offset {
    val h = w * 0.72f
    val done = (t * 0.9f) % 4f
    val k = done.toInt().coerceIn(0, 3)
    val full = done - k
    return Offset(c.x - w / 2 + w * (0.12f + 0.72f * full), c.y - h / 2 + h * (0.28f + k * 0.18f) + sin(t * 40f) * w * 0.02f)
}

/** A pencil with its point at [tip], held from the lower right. */
internal fun DrawScope.pencil(tip: Offset, w: Float, line: Color) {
    rotate(-35f, tip) {
        val body = Rect(tip.x, tip.y - w * 0.07f, tip.x + w * 0.6f, tip.y + w * 0.07f)
        drawRect(Color(0xFFFFC24B), Offset(body.left + w * 0.12f, body.top), Size(body.width - w * 0.12f, body.height))
        drawRect(Color(0xFFFF8FA3), Offset(body.right - w * 0.1f, body.top), Size(w * 0.1f, body.height))
        val point = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + w * 0.12f, body.top)
            lineTo(tip.x + w * 0.12f, body.bottom)
            close()
        }
        drawPath(point, Color(0xFFF2D5A4))
        drawCircle(Color(0xFF3A3346), w * 0.025f, Offset(tip.x + w * 0.015f, tip.y))
        drawRect(line, body.topLeft, body.size, style = Stroke(w * 0.03f))
    }
}

/** A small gear, [r] across its teeth, turned [turn] radians. */
internal fun DrawScope.gear(c: Offset, r: Float, teeth: Int, turn: Float, fill: Color, line: Color) {
    val p = Path().apply {
        val n = teeth * 4
        for (i in 0 until n) {
            val ang = turn + i * 2f * PI.toFloat() / n
            val rr = if ((i / 2) % 2 == 0) r else r * 0.78f
            val x = c.x + cos(ang) * rr
            val y = c.y + sin(ang) * rr
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(p, fill)
    drawPath(p, line, style = Stroke(r * 0.12f, join = StrokeJoin.Round))
    drawCircle(line, r * 0.25f, c)
}

/** A soft glow round [c]. */
internal fun DrawScope.glow(c: Offset, r: Float, color: Color, strength: Float) {
    if (strength <= 0f || r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = color.alpha * strength), Color.Transparent), center = c, radius = r), r, c)
}

/** A point between [a] and [b]. */
internal fun mix(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

internal fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
