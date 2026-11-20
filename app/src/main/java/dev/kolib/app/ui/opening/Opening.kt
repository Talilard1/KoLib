package dev.kolib.app.ui.opening

import dev.kolib.app.tr
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.kolib.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KoLib's opening, when the app starts: threads of gold light swirl in out of the dark, draw the book
 * mark, flash, name the app, then fly through it into the app. Tap to skip it.
 */
object Opening {
    /** How long it all takes, in seconds. */
    const val LENGTH = 5.0f

    internal const val SWIRL_START = 0.25f
    internal const val SWIRL_END = 2.3f
    internal const val DRAW_START = 1.9f
    internal const val DRAW_END = 2.7f
    internal const val RIBBON_START = 2.5f
    internal const val RIBBON_END = 2.92f
    internal const val FLASH = 2.8f
    internal const val NAME_START = 3.02f
    internal const val SHIMMER_START = 3.35f
    internal const val SHIMMER_END = 4.0f
    /** From the book forming, it eases back away from the camera... */
    internal const val RECEDE_START = 1.9f
    /** ...then comes at it, faster and faster, through into the app. */
    internal const val ZOOM_START = 4.25f
    /** From here the app shows through the book as it comes at the camera, and only through it. */
    internal const val REVEAL = ZOOM_START + 0.2f
}

/** Where the opening has got to, for what's under it: the app needn't be drawn until it starts to show. */
@Stable
class OpeningClock {
    /** Seconds in. */
    var time by mutableFloatStateOf(0f)
        internal set
    /** Being skipped, so fading out. */
    var skipping by mutableStateOf(false)
        internal set
    /** The app under the opening can be seen (or is about to be). */
    val showingApp: Boolean get() = skipping || time >= Opening.REVEAL - 0.05f
}

/** The same dark brown the system shows while the app starts: the opening starts from it, so the two meet without a seam. */
internal val StartingNight = Color(0xFF1E120B)

// The colours of the look the opening is drawn in (see OpeningLook): gold on dark brown, unless another look is chosen.
internal val OpeningNight: Color get() = OpeningColours.look.night
internal val Glow: Color get() = OpeningColours.look.glow
internal val Ember: Color get() = OpeningColours.look.ember
internal val GoldLight: Color get() = OpeningColours.look.goldLight
internal val Gold: Color get() = OpeningColours.look.gold
internal val GoldDeep: Color get() = OpeningColours.look.goldDeep
internal val Spark: Color get() = OpeningColours.look.spark
internal val Cream: Color get() = OpeningColours.look.cream
internal val MarkLight: Color get() = OpeningColours.look.markLight
internal val MarkMid: Color get() = OpeningColours.look.markMid

/**
 * The opening over the app: plays once, [sound] or not, then calls [onDone]; a tap skips it. [light] is the
 * lighter version, for phones and slower devices. It's drawn in the colours of [look], the app's look.
 */
@Composable
fun OpeningAnimation(
    sound: Boolean,
    light: Boolean = false,
    clock: OpeningClock = remember { OpeningClock() },
    look: dev.kolib.app.ui.theme.ThemeChoice = dev.kolib.app.ui.theme.ThemeChoice.SYSTEM,
    onDone: () -> Unit,
) {
    // Set before any of it is made: what's drawn once and kept is drawn in these colours.
    remember(look) { OpeningLook.of(look).also { OpeningColours.look = it } }
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    var fade by remember { mutableFloatStateOf(1f) }
    var finished by remember { mutableStateOf(false) }
    val player = remember { if (sound) OpeningSound.create(context) else null }
    fun finish() {
        if (finished) return
        finished = true
        done()
    }

    // Light status and navigation bar icons over the dark, put back after.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = bars?.isAppearanceLightStatusBars
        val navigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            status?.let { bars.isAppearanceLightStatusBars = it }
            navigation?.let { bars.isAppearanceLightNavigationBars = it }
            // Its sound plays out (or fades) by itself; it stops if the app goes away.
            player?.letFinish()
        }
    }
    // Leaving the app part way: it stops, and isn't played again on coming back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player?.stop()
                finish()
            }
        }
        lifecycle.addObserver(watcher)
        onDispose { lifecycle.removeObserver(watcher) }
    }
    // A tablet or phone can take half a second or so to get the sound going after it's asked, and the
    // sound then trails the picture: the rush at the end came after the app had already shown. So the
    // sound is started first thing, and the picture waits for it and keeps time with it.
    LaunchedEffect(Unit) {
        player?.start()
        // A couple of frames first, so the app underneath has done its first, heaviest layout.
        withFrameNanos { }
        withFrameNanos { }
        var start = withFrameNanos { it }
        val sound = player
        if (sound != null) {
            // Held on the dark it starts from until the sound is heard: not for more than a second.
            while (!clock.skipping) {
                val now = withFrameNanos { it }
                val heard = sound.heard()
                if (heard > 0f) {
                    start = now - (heard * 1_000_000_000.0).toLong()
                    break
                }
                if (now - start > 1_000_000_000L) {
                    start = now
                    break
                }
            }
        }
        while (!clock.skipping) {
            val now = withFrameNanos { it }
            if (sound != null) {
                // Kept to the sound as it plays, nudged a little at a time so the picture never jumps.
                val heard = sound.heard()
                if (heard > 0f) {
                    val drift = now - (heard * 1_000_000_000.0).toLong() - start
                    start += (drift / 8).coerceIn(-3_000_000L, 3_000_000L)
                }
            }
            clock.time = (now - start) / 1_000_000_000f
            if (clock.time >= Opening.LENGTH) {
                finish()
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(clock.skipping) {
        if (!clock.skipping) return@LaunchedEffect
        player?.fadeOut()
        animate(fade, 0f, animationSpec = tween(220)) { v, _ -> fade = v }
        finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = tr("KoLib. Tap to skip.") }
            .pointerInput(Unit) { detectTapGestures { clock.skipping = true } }
    ) {
        OpeningScene({ clock.time }, { fade }, light)
    }
}

/**
 * The opening at [time] seconds in, drawn at [alpha]: in full, or, [light], the lighter version that does
 * much less work each frame. Both are read while drawing, so each frame redraws without anything rebuilt.
 */
@Composable
fun OpeningScene(time: () -> Float, alpha: () -> Float = { 1f }, light: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (light) LightOpening(time, alpha, Modifier) else FullOpening(time, alpha, Modifier)
        // In another look, its dark comes in from the brown the system starts the app on, before the threads do.
        if (OpeningColours.look.night != StartingNight) Canvas(Modifier.fillMaxSize()) {
            val t = time()
            if (t < START_BLEND) drawRect(StartingNight, alpha = alpha() * (1f - (t / START_BLEND).let { it * it * (3f - 2f * it) }))
        }
    }
}

/** How long the dark of another look takes to come in from the system's, in seconds. */
private const val START_BLEND = 0.4f

/** The KoLib name laid out once: the whole word, and each letter on its own where the word puts it. */
internal class NameLayout(val whole: TextLayoutResult, val letters: List<Pair<TextLayoutResult, Float>>)
