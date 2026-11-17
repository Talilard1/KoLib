package dev.kobolibrary.app.ocr

import dev.kobolibrary.app.tr
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import dev.kobolibrary.app.convert.AndroidPdfPages
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tesseract, set up for [languages] (their data under [dataRoot]/tessdata), reading whole pages. */
class TesseractEngine(dataRoot: File, languages: List<String>) : OcrEngine {
    private val api = TessBaseAPI()

    init {
        if (!api.init(dataRoot.path, languages.joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) {
            api.recycle()
            throw IOException(tr("Text recognition couldn't start. The language data may be damaged. Try again to re-download it."))
        }
        // The page's own layout: columns, headings and all, rather than one block of text.
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
    }

    override fun read(gray: ByteArray, width: Int, height: Int, dpi: Int): String {
        api.setVariable("user_defined_dpi", dpi.toString())
        api.setImage(gray, width, height, 1, width)
        return try {
            api.getHOCRText(0).orEmpty()
        } finally {
            api.clear()
        }
    }

    override fun stop() = api.stop()

    override fun close() = api.recycle()
}

/** A PDF's pages for reading their text, and pictures of them (or parts of them) for the book made from it. */
class AndroidOcrPages(pdf: File) : OcrPageSource, PagePictures, Closeable {
    private val pages = AndroidPdfPages(pdf)

    override val count: Int get() = pages.count

    /** Pages aren't drawn bigger than this each way, whatever the resolution asked for. */
    private val maxSide = 5000

    override fun gray(index: Int, dpi: Int): GrayPage? {
        val (pw, ph) = pages.size(index)
        if (pw <= 0 || ph <= 0) return null
        val scale = min(dpi / 72f, maxSide.toFloat() / max(pw, ph))
        val w = (pw * scale).roundToInt().coerceAtLeast(1)
        val h = (ph * scale).roundToInt().coerceAtLeast(1)
        val bmp = pages.bitmap(index, w, h)
        try {
            val gray = ByteArray(w * h)
            val strip = 64
            val row = IntArray(w * strip)
            var dark = 0L
            var y = 0
            while (y < h) {
                val rows = min(strip, h - y)
                bmp.getPixels(row, 0, w, 0, y, w, rows)
                var at = y * w
                for (k in 0 until rows * w) {
                    val c = row[k]
                    val g = (299 * ((c shr 16) and 0xff) + 587 * ((c shr 8) and 0xff) + 114 * (c and 0xff)) / 1000
                    gray[at++] = g.toByte()
                    if (g < 128) dark++
                }
                y += rows
            }
            // Next to no ink: a blank page (a speck or two of dust aside).
            return GrayPage(gray, w, h, (scale * 72).roundToInt(), blank = dark * 4000 < w.toLong() * h)
        } finally {
            bmp.recycle()
        }
    }

    override fun page(index: Int, maxSide: Int): ByteArray = pages.render(index, maxSide) ?: white()

    override fun part(index: Int, box: Box, width: Int, height: Int, maxSide: Int): ByteArray = runCatching {
        val (pw, ph) = pages.size(index)
        // From the picture's pixels to the page's points.
        val toPoints = pw.toFloat() / width.coerceAtLeast(1)
        val left = box.left * toPoints
        val top = box.top * toPoints
        val bw = box.width * toPoints
        val bh = box.height * toPoints
        val scale = min(maxSide / max(bw, bh), 300f / 72f)
        val outW = (bw * scale).roundToInt().coerceAtLeast(1)
        val outH = (bh * scale).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-left * scale, -top * scale)
        }
        pages.drawInto(index, bmp, Rect(0, 0, outW, outH), m)
        jpeg(bmp).also { bmp.recycle() }
    }.getOrElse { white() }

    private fun jpeg(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()

    /** A small white picture, for a page that couldn't be drawn. */
    private fun white(): ByteArray {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return jpeg(bmp).also { bmp.recycle() }
    }

    override fun close() = pages.close()
}
