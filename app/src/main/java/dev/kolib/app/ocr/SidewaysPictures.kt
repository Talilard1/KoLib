package dev.kolib.app.ocr

import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import dev.kolib.app.tr
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Whether a picture is printed on its side (a scene across two pages turned to fit one, as many light novels have
 * them), told from the words in it: read the right way up they come out as words, read on their side they don't.
 * A picture with no words in it can't be told, and is left as it is. Uses the text recognition's data for
 * [languages] under [dataRoot], which must be on the device already (nothing is downloaded for this).
 */
class SidewaysPictures(dataRoot: File, languages: List<String>) : Closeable {
    private val api = TessBaseAPI()

    init {
        if (!api.init(dataRoot.path, languages.joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) {
            api.recycle()
            throw IOException(tr("Text recognition couldn't start. The language data may be damaged. Try again to re-download it."))
        }
        // Words anywhere, in no order: captions and speech scattered over a picture.
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
    }

    /** The quarter turns clockwise that put the picture in [bytes] the right way up: 1 or 3; 0 if it is; null if it can't be told. */
    fun turnsFor(bytes: ByteArray): Int? {
        val picture = GrayPicture.decode(bytes, MAX_SIDE) ?: return null
        return decide(words(picture), words(picture.turned(1)), words(picture.turned(3)))
    }

    /** How many sure words of three letters or more are read in [p]. */
    private fun words(p: GrayPicture): Int {
        api.setImage(p.pixels, p.width, p.height, 1, p.width)
        val hocr = try { api.getHOCRText(0).orEmpty() } finally { api.clear() }
        return Hocr.parse(hocr, 300).words.count { sure(it.text, it.confidence) }
    }

    override fun close() = api.recycle()

    companion object {
        /** Pictures are read at most this big each way: enough for a caption's words. */
        const val MAX_SIDE = 1600

        /** A word read surely: three letters or more, mostly letters, and the recognition sure of it. */
        fun sure(text: String, confidence: Int): Boolean {
            val t = text.trim().trim('.', ',', '!', '?', '"', '\'', '“', '”', '‘', '’', '…', ':', ';', '(', ')')
            val letters = t.count { it.isLetter() }
            return confidence >= 80 && letters >= 3 && letters >= t.length * 0.8
        }

        /**
         * From the words read as it is ([upright]), turned clockwise and turned anticlockwise: the turn that reads
         * plainly better (three words at least, and more than twice as many as the right way up), 0 if it reads
         * well as it is, null if there aren't words enough to tell.
         */
        fun decide(upright: Int, clockwise: Int, anticlockwise: Int): Int? {
            val (best, q) = if (anticlockwise > clockwise) anticlockwise to 3 else clockwise to 1
            return when {
                best >= 3 && best >= 2 * upright + 1 -> q
                upright >= 3 -> 0
                else -> null
            }
        }
    }
}

/** A picture in shades of grey, a byte a pixel, [width] by [height]. */
class GrayPicture(val pixels: ByteArray, val width: Int, val height: Int) {

    /** Turned [quarters] quarter turns clockwise (1 or 3; anything else is as it is). */
    fun turned(quarters: Int): GrayPicture {
        val q = Math.floorMod(quarters, 4)
        if (q != 1 && q != 3) return this
        val out = ByteArray(pixels.size)
        val w = width
        val h = height
        // Clockwise, (x, y) goes to (h - 1 - y, x) in a picture h wide; anticlockwise, to (y, w - 1 - x).
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val at = if (q == 1) x * h + (h - 1 - y) else (w - 1 - x) * h + y
                out[at] = pixels[row + x]
            }
        }
        return GrayPicture(out, h, w)
    }

    companion object {
        /** The picture in [bytes], made no bigger than [maxSide] each way; null if it can't be read. */
        fun decode(bytes: ByteArray, maxSide: Int): GrayPicture? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            try {
                val w = bmp.width
                val h = bmp.height
                val gray = ByteArray(w * h)
                val row = IntArray(w)
                for (y in 0 until h) {
                    bmp.getPixels(row, 0, w, 0, y, w, 1)
                    for (x in 0 until w) {
                        val c = row[x]
                        gray[y * w + x] = ((299 * ((c shr 16) and 0xff) + 587 * ((c shr 8) and 0xff) + 114 * (c and 0xff)) / 1000).toByte()
                    }
                }
                return GrayPicture(gray, w, h)
            } finally {
                bmp.recycle()
            }
        }
    }
}
