package dev.kobolibrary.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** A picture printed on its side, turned the right way up: the same picture, a quarter turn round, in the same kind of file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PictureTurnsTest {

    /** A picture [w] by [h], white with a red square at its top left, as a [format] file. */
    private fun picture(w: Int, h: Int, format: Bitmap.CompressFormat): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in 0 until h / 4) for (x in 0 until w / 4) bmp.setPixel(x, y, Color.RED)
        return ByteArrayOutputStream().also { bmp.compress(format, 95, it) }.toByteArray()
    }

    private fun decode(bytes: ByteArray) = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun red(c: Int) = Color.red(c) > 200 && Color.green(c) < 80 && Color.blue(c) < 80

    @Test
    fun aQuarterTurnClockwiseAndBack() {
        val png = picture(80, 120, Bitmap.CompressFormat.PNG)
        val right = decode(PictureTurns.turn(png, 1)!!)
        // Taller than wide, now wider than tall; the top left corner is now at the top right.
        assertEquals(120, right.width)
        assertEquals(80, right.height)
        assertEquals(true, red(right.getPixel(right.width - 3, 3)))
        assertEquals(false, red(right.getPixel(3, 3)))
        // A quarter turn the other way: the top left corner at the bottom left.
        val left = decode(PictureTurns.turn(png, -1)!!)
        assertEquals(120, left.width)
        assertEquals(true, red(left.getPixel(3, left.height - 3)))
        // Half a turn, and none.
        assertEquals(80, decode(PictureTurns.turn(png, 2)!!).width)
        assertNull(PictureTurns.turn(png, 4))
        assertNull(PictureTurns.turn(png, 0))
    }

    @Test
    fun theSameKindOfFile() {
        assertEquals("image/png", PictureTurns.mediaType(PictureTurns.turn(picture(40, 60, Bitmap.CompressFormat.PNG), 1)!!))
        assertEquals("image/jpeg", PictureTurns.mediaType(PictureTurns.turn(picture(40, 60, Bitmap.CompressFormat.JPEG), 1)!!))
        // Nothing to turn in what isn't a picture.
        assertNull(PictureTurns.turn("<svg/>".toByteArray(), 1))
        assertEquals(60 to 40, PictureTurns.size(40, 60, 1))
        assertEquals(40 to 60, PictureTurns.size(40, 60, 2))
        assertEquals(60 to 40, PictureTurns.size(40, 60, -1))
    }
}
