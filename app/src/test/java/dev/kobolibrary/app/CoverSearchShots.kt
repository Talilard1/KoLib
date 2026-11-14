package dev.kobolibrary.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.covers.CoverSource
import dev.kobolibrary.app.covers.FoundCover
import dev.kobolibrary.app.ui.cover.CoverSearchScreen
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** The window of covers found online, with made-up covers in it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverSearchShots {

    @get:Rule
    val compose = createComposeRule()

    private fun cover(w: Int, h: Int, colour: Int, source: CoverSource, n: Int): FoundCover {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(colour)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3E7C9.toInt(); textAlign = Paint.Align.CENTER; textSize = w / 9f }
        c.drawText("THE", w / 2f, h * 0.36f, p)
        c.drawText("LANTERN", w / 2f, h * 0.46f, p)
        c.drawText("ROAD", w / 2f, h * 0.56f, p)
        p.textSize = w / 16f
        c.drawText("A. N. AUTHOR", w / 2f, h * 0.86f, p)
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return FoundCover(source, "https://example.com/$n.jpg", out.toByteArray(), w, h)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phone() {
        val found = listOf(
            cover(1400, 2135, 0xFF2B3A55.toInt(), CoverSource.AMAZON, 1),
            cover(1000, 1600, 0xFF5B2333.toInt(), CoverSource.AMAZON, 2),
            cover(328, 500, 0xFF2F4F3A.toInt(), CoverSource.AMAZON, 3),
            cover(800, 1237, 0xFF473B2A.toInt(), CoverSource.GOOGLE_BOOKS, 4),
            cover(575, 889, 0xFF3A2F4F.toInt(), CoverSource.GOOGLE_BOOKS, 5),
            cover(312, 475, 0xFF4F3A2F.toInt(), CoverSource.OPEN_LIBRARY, 6),
        )
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                CoverSearchScreen("The Lantern Road", listOf("A. N. Author"), null, onPick = { _, _ -> }, onClose = {}, initial = found)
            }
        }
        compose.waitForIdle()
        File("/home/claude/heads/shots").mkdirs()
        captureScreenRoboImage("/home/claude/heads/shots/cover_search.png")
    }
}
