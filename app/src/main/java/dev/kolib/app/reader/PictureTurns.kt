package dev.kolib.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

/**
 * Pictures a reader has turned: a picture a book prints on its side to fit a page (a scene across two pages, in
 * many light novels), turned back the right way up to be shown across both. A quarter turn clockwise at a time.
 */
object PictureTurns {

    /** [bytes] turned [quarters] quarter turns clockwise, in the same kind of file; null if there's nothing to turn or it can't be read. */
    fun turn(bytes: ByteArray, quarters: Int): ByteArray? {
        val q = Math.floorMod(quarters, 4)
        if (q == 0) return null
        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val turned = try {
            Bitmap.createBitmap(picture, 0, 0, picture.width, picture.height, Matrix().apply { postRotate(90f * q) }, true)
        } catch (e: OutOfMemoryError) {
            picture.recycle()
            return null
        }
        if (turned !== picture) picture.recycle()
        val jpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        return ByteArrayOutputStream().use { out ->
            turned.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 92, out)
            turned.recycle()
            out.toByteArray()
        }
    }

    /** A picture [w] by [h] turned [quarters] quarter turns: its width and height. */
    fun size(w: Int, h: Int, quarters: Int): Pair<Int, Int> = if (Math.floorMod(quarters, 4) % 2 == 1) h to w else w to h

    /** The media type of a picture's [bytes], from how they start (a turned picture may not be the kind its name says). */
    fun mediaType(bytes: ByteArray): String? = when {
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        else -> null
    }
}
