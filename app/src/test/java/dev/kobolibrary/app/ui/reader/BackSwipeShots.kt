package dev.kobolibrary.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The back swipe's arrow at each stage, from the left and the right edge, in two of the app's themes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h600dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackSwipeShots {

    @get:Rule
    val compose = createComposeRule()

    /** Each stage: the pull as a part of the threshold, how far it's gone back in, and how filled in it is. */
    private val stages = listOf(
        Triple(0.08f, 0f, 0f), Triple(0.3f, 0f, 0f), Triple(0.55f, 0f, 0f), Triple(0.85f, 0f, 0f),
        Triple(1.2f, 0f, 1f), Triple(1.2f, 0.45f, 0f), Triple(1.2f, 0.75f, 0f), Triple(1.2f, 0.92f, 0f),
    )

    @Composable
    private fun Stage(side: Int, pull: Float, retract: Float, armed: Float, page: Color) {
        val threshold = with(LocalDensity.current) { EdgeBackState.THRESHOLD.toPx() }
        val y = with(LocalDensity.current) { 60.dp.toPx() }
        Box(Modifier.width(100.dp).height(120.dp).background(page)) {
            EdgeBackArrow(side, pull * threshold, y, retract, armed)
        }
    }

    @Composable
    private fun Sheet(page: Color) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (side in listOf(-1, 1)) {
                Text(if (side < 0) "From the left" else "From the right", color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stages.forEach { (p, r, a) -> Stage(side, p, r, a, page) }
                }
            }
        }
    }

    private fun shot(name: String, theme: ThemeChoice, page: Color) {
        compose.setContent { AppSurface(theme) { Sheet(page) } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    /**
     * Two swipes, frame by frame at 60 a second over a page of a book: one in from the right that goes
     * back, one in from the left let go too soon. For a preview video; only runs where
     * /home/claude/opening/render is.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun swipeFrames() {
        val home = java.io.File("/home/claude/opening")
        org.junit.Assume.assumeTrue(java.io.File(home, "render").exists())
        val dir = java.io.File(home, "back").apply { mkdirs() }
        val page = android.graphics.BitmapFactory.decodeFile("/home/claude/kobolibrary/app/build/outputs/roborazzi/51_reader_reading.png")
        var side by androidx.compose.runtime.mutableIntStateOf(0)
        var pull by androidx.compose.runtime.mutableFloatStateOf(0f)
        var retract by androidx.compose.runtime.mutableFloatStateOf(0f)
        var armed by androidx.compose.runtime.mutableFloatStateOf(0f)
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                Box(Modifier.fillMaxSize()) {
                    page?.let {
                        androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    if (side != 0) EdgeBackArrow(side, pull, 360f, retract, armed)
                }
            }
        }
        val threshold = 64f
        fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
        val ease = androidx.compose.animation.core.FastOutSlowInEasing
        // (side, how far in at the most as a part of the threshold): the first goes back, the second doesn't.
        val swipes = listOf(1 to 1.35f, -1 to 0.62f)
        var frame = 0
        fun snap() {
            compose.waitForIdle()
            captureScreenRoboImage(java.io.File(dir, "f_%04d.png".format(frame++)).path)
        }
        swipes.forEach { (s, most) ->
            side = 0; snap(); repeat(5) { snap() }
            side = s; retract = 0f; armed = 0f
            // In over a third of a second, then held for a moment.
            for (i in 1..22) {
                pull = threshold * most * easeOut(i / 22f)
                armed = if (pull >= threshold) (armed + 1f / 8f).coerceAtMost(1f) else (armed - 1f / 8f).coerceAtLeast(0f)
                snap()
            }
            repeat(18) {
                armed = if (pull >= threshold) (armed + 1f / 8f).coerceAtMost(1f) else 0f
                snap()
            }
            // Let go: back into the edge over a quarter of a second, its colour going back too.
            for (i in 1..16) {
                retract = ease.transform(i / 16f)
                armed = (armed - 1f / 8f).coerceAtLeast(0f)
                snap()
            }
            side = 0
            repeat(8) { snap() }
        }
    }

    @Test fun bookcase() = shot("90_back_swipe_bookcase", ThemeChoice.BOOKCASE, Color(0xFFF6EEDC))
    @Test fun light() = shot("90_back_swipe_light", ThemeChoice.LIGHT, Color(0xFFFFFFFF))
    @Test fun nightPage() = shot("90_back_swipe_night", ThemeChoice.DARK, Color(0xFF121212))
}
