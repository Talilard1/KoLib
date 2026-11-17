package dev.kolib.app.ui.opening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The opening at the advert's size (2560 x 1440), thirty frames a second, once over black and once over
 * white: from the two, the advert works out how much of what is behind shows through each dot, and so can
 * put its own first scene behind the opening where the app would be. Only runs when asked for: when the file
 * /home/claude/advert5/opening/render-now is there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningForAdvert {

    @get:Rule
    val compose = createComposeRule()

    private val home = File("/home/claude/advert5")

    private fun render(dir: File, behind: Color) {
        assumeTrue(File(home, "opening/render-now").exists())
        dir.mkdirs()
        var t by mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(behind)) { OpeningScene({ t }, light = false) }
        }
        val frames = (Opening.LENGTH * 30).toInt()
        for (i in 0 until frames) {
            t = i / 30f
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(dir, "f_%04d.png".format(i)).path)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-xhdpi")
    fun overBlack() = render(File(home, "opening/k"), Color.Black)

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-xhdpi")
    fun overWhite() = render(File(home, "opening/w"), Color.White)
}
