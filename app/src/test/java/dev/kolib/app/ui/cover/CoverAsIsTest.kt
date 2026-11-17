package dev.kolib.app.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.kobo.CoverScreen
import dev.kolib.app.kobo.KoboModels
import dev.kolib.app.kobo.KoboScreen
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Saving a cover "as it is": the picture's own file, or its own pixels, never cropped or resized. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverAsIsTest {

    private fun encoded(w: Int, h: Int, format: Bitmap.CompressFormat): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 40, 30)) }
        return ByteArrayOutputStream().also { bmp.compress(format, 90, it) }.toByteArray()
    }

    private fun picture(bytes: ByteArray?, w: Int, h: Int, keep: Boolean = true): CoverPicture {
        val bmp = bytes?.let { CoverRendering.decode(it) } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return CoverRendering.prepare(bmp, "test", if (keep) bytes else null)
    }

    @Test
    fun aJpegOrPngIsKeptByteForByte() {
        for ((format, type) in listOf(Bitmap.CompressFormat.JPEG to "image/jpeg", Bitmap.CompressFormat.PNG to "image/png")) {
            val bytes = encoded(900, 1300, format)
            assertEquals(type, CoverRendering.mediaTypeOf(bytes))
            val saved = CoverRendering.asIs(picture(bytes, 900, 1300))
            assertArrayEquals(bytes, saved.bytes)
            assertEquals(type, saved.mediaType)
            assertEquals(900 to 1300, saved.width to saved.height)
            assertTrue(saved.asIs)
        }
    }

    @Test
    fun aPictureEpubsDontTakeKeepsItsPixelsAsAJpeg() {
        val webp = encoded(640, 960, Bitmap.CompressFormat.WEBP_LOSSLESS)
        assertNull(CoverRendering.mediaTypeOf(webp))
        val saved = CoverRendering.asIs(picture(webp, 640, 960))
        assertEquals("image/jpeg", saved.mediaType)
        assertEquals(640 to 960, saved.width to saved.height)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(saved.bytes, 0, saved.bytes.size, o)
        assertEquals(640 to 960, o.outWidth to o.outHeight)

        // One made here (no file of its own) keeps its size too.
        val made = CoverRendering.asIs(picture(null, 1072, 1448, keep = false))
        assertEquals(1072 to 1448, made.width to made.height)
    }

    @Test
    fun fittedCoversAreTheKobosSizeAndNotAsTheyAre() {
        val bytes = encoded(900, 900, Bitmap.CompressFormat.JPEG)
        val saved = CoverRendering.fitted(picture(bytes, 900, 900), CoverCrop(fit = CoverFit.FIT_WHITE))
        assertFalse(saved.asIs)
        assertEquals("image/jpeg", saved.mediaType)
        assertEquals(CoverCrop.OUT_WIDTH to CoverCrop.OUT_HEIGHT, saved.width to saved.height)
    }

    @Test
    fun coversAreMadeTheSizeOfTheKoboThatWasPluggedIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            // Until a Kobo has been plugged in, the Clara's size.
            assertFalse(CoverScreen.fromKobo)
            assertEquals(1072 to 1448, CoverCrop.OUT_WIDTH to CoverCrop.OUT_HEIGHT)

            // A Sage's screen is 1440 × 1920: covers fitted and covers designed are both that size.
            CoverScreen.remember(context, KoboModels.screen("Sage")!!)
            val bytes = encoded(900, 900, Bitmap.CompressFormat.JPEG)
            val saved = CoverRendering.fitted(picture(bytes, 900, 900), CoverCrop(fit = CoverFit.FIT_BLUR))
            assertEquals(1440 to 1920, saved.width to saved.height)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(saved.bytes, 0, saved.bytes.size, o)
            assertEquals(1440 to 1920, o.outWidth to o.outHeight)
            val designed = CoverGenerator(context).render(CoverText("Emma", listOf("Jane Austen"), null, null), CoverDesign(CoverStyle.CLASSIC, CoverColors.themes[0]))
            assertEquals(1440 to 1920, designed.width to designed.height)
            assertEquals(0.75f, CoverCrop.ASPECT, 0.0001f)

            // It's remembered for the next time the app starts.
            CoverScreen.load(context)
            assertTrue(CoverScreen.fromKobo)
            assertEquals(KoboScreen(1440, 1920), CoverScreen.current)
        } finally {
            CoverScreen.forget(context)
        }
        assertEquals(KoboScreen.CLARA, CoverScreen.current)
        CoverScreen.load(context)
        assertFalse(CoverScreen.fromKobo)
    }
}
