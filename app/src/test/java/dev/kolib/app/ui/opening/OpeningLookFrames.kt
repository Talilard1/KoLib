package dev.kolib.app.ui.opening

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kolib.app.ui.theme.ThemeChoice
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Key moments of the opening in each look, to look at them side by side. Only runs where /tmp/opening_looks/render is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningLookFrames {

    @get:Rule
    val compose = createComposeRule()

    private val home = File("/tmp/opening_looks")

    private fun render(light: Boolean) {
        assumeTrue(File(home, "render").exists())
        home.mkdirs()
        var t by mutableFloatStateOf(0f)
        var look by mutableStateOf(ThemeChoice.SYSTEM)
        compose.setContent {
            key(look, light) {
                OpeningColours.look = OpeningLook.of(look)
                OpeningScene({ t }, light = light)
            }
        }
        val looks = listOf(
            ThemeChoice.DARK, ThemeChoice.OLED, ThemeChoice.BOOKCASE, ThemeChoice.CLEAN_DARK, ThemeChoice.MANUSCRIPT, ThemeChoice.ROMANCE,
            ThemeChoice.CINEMA, ThemeChoice.TERMINAL, ThemeChoice.STARSHIP, ThemeChoice.HOLOGRAM, ThemeChoice.RELIC,
        )
        for (l in looks) {
            look = l
            for ((i, time) in listOf(0.15f, 1.5f, 2.85f, 3.8f).withIndex()) {
                t = time
                compose.waitForIdle()
                compose.onRoot().captureRoboImage(File(home, "${if (light) "light" else "full"}_${l.name}_$i.png").path)
            }
        }
        OpeningColours.look = OpeningLook.Gold
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-mdpi")
    fun full() = render(light = false)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-mdpi")
    fun light() = render(light = true)
}
