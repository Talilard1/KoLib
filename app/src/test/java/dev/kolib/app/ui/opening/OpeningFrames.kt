package dev.kolib.app.ui.opening

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * The opening, frame by frame from the app's own drawing, over a picture of the library as it would be
 * behind it: to make a preview video of it. Only runs where /home/claude/opening/render is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningFrames {

    @get:Rule
    val compose = createComposeRule()

    private val home = File("/home/claude/opening")

    private fun render(dir: File, behind: String?, times: List<Float>, light: Boolean = false) {
        assumeTrue(File(home, "render").exists())
        dir.mkdirs()
        var t by mutableFloatStateOf(0f)
        val picture = behind?.let { BitmapFactory.decodeFile(it) }
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                picture?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                OpeningScene({ t }, light = light)
            }
        }
        times.forEachIndexed { i, time ->
            t = time
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(dir, "f_%04d.png".format(i)).path)
        }
    }

    /** Every frame at 60 a second, on a phone, and a moment of the library after. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun phone() = render(
        File(home, "phone"), "/home/claude/phone/after/p01_library.png",
        List((Opening.LENGTH * 60).toInt() + 24) { it / 60f },
    )

    /** Key moments of both versions, side by side, to compare them. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun keyMoments() {
        val times = listOf(0.6f, 1.1f, 1.6f, 2.0f, 2.5f, 2.85f, 3.3f, 3.9f, 4.6f)
        render(File(home, "key-full"), null, times)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun keyMomentsLight() {
        val times = listOf(0.6f, 1.1f, 1.6f, 2.0f, 2.5f, 2.85f, 3.3f, 3.9f, 4.6f)
        render(File(home, "key-light"), null, times, light = true)
    }

    /** The threads running in and the book drawn from them, to the flash, closely: both versions. */
    private val merging = List(34) { 1.3f + it * 0.05f }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun merging() = render(File(home, "merge-full"), null, merging)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun mergingLight() = render(File(home, "merge-light"), null, merging, light = true)

    /** The lighter version, the same way. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun phoneLight() = render(
        File(home, "phone-light"), "/home/claude/phone/after/p01_library.png",
        List((Opening.LENGTH * 60).toInt() + 24) { it / 60f }, light = true,
    )

    /** The moments the app shows through the book as it comes at the camera, and only through it. */
    private val through = listOf(4.3f, 4.45f, 4.52f, 4.6f, 4.68f, 4.76f, 4.84f, 4.9f, 4.96f)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun phoneThrough() = render(File(home, "through-phone"), "/home/claude/phone/after/p01_library.png", through)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-hdpi")
    fun phoneThroughLight() = render(File(home, "through-phone-light"), "/home/claude/phone/after/p01_library.png", through, light = true)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletThrough() = render(File(home, "through-tablet"), "/home/claude/advert/app140/ad_library.png", through)

    /** Every frame at 60 a second on a tablet held sideways, over the library, for a preview video. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletAll() = render(
        File(home, "tablet-all"), "/home/claude/advert/app140/ad_library.png",
        List((Opening.LENGTH * 60).toInt() + 24) { it / 60f },
    )

    /** A few moments on a tablet held sideways, to check the layout there. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tablet() = render(File(home, "tablet"), null, listOf(1.2f, 2.2f, 2.86f, 3.7f, 4.6f))

    /** The finished mark at a tablet's full sharpness, both ways, to look closely at where the lines meet. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletSharp() = render(File(home, "sharp-full"), null, listOf(2.75f, 3.7f))

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletSharpLight() = render(File(home, "sharp-light"), null, listOf(2.75f, 3.7f), light = true)
}
