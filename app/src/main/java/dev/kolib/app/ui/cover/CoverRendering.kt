package dev.kolib.app.ui.cover

import dev.kolib.app.kobo.CoverScreen
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import dev.kolib.app.log.AppLog
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A picture ready for the cover editor: the full picture, a smaller one for the screen, and its blur.
 * [source] is the picture's file as it came, when there is one, so it can be kept exactly as it is.
 */
class CoverPicture(val full: Bitmap, val preview: Bitmap, val blurred: Bitmap, val label: String, val source: ByteArray? = null) {
    val width get() = full.width
    val height get() = full.height
}

/** A cover ready to go into a book: the picture's bytes, what kind of picture it is, and its size. */
class SavedCover(val bytes: ByteArray, val mediaType: String, val width: Int, val height: Int, val asIs: Boolean)

object CoverRendering {
    /** Pictures are read at most this big; plenty for a cover the height of the largest Kobo screen (1920 pixels), even zoomed in. */
    private const val MAX_SIDE = 3000
    private const val PREVIEW_SIDE = 1600

    fun decode(bytes: ByteArray): Bitmap? = decode({ ImageDecoder.createSource(ByteBuffer.wrap(bytes)) }) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds) })
    }

    fun decode(context: Context, uri: Uri): Bitmap? = decode({ ImageDecoder.createSource(context.contentResolver, uri) }) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds) })
        }
    }

    private fun sampleFor(bounds: BitmapFactory.Options): Int {
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    private fun decode(source: () -> ImageDecoder.Source, fallback: () -> Bitmap?): Bitmap? {
        val decoded = try {
            ImageDecoder.decodeBitmap(source()) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val s = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
                }
            }
        } catch (e: Exception) {
            AppLog.w("Cover", "Android's image decoder couldn't read the picture; trying the older one", e)
            null
        } ?: runCatching { fallback() }.getOrNull()
        return decoded?.let { ensureSoftwareArgb(it) }
    }

    private fun ensureSoftwareArgb(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false) ?: b

    fun prepare(full: Bitmap, label: String, source: ByteArray? = null): CoverPicture =
        CoverPicture(full, scaleDownTo(full, PREVIEW_SIDE).apply { setHasMipMap(true) }, blurred(full), label, source)

    /** The kind of picture [bytes] hold, if it's one EPUB books can have as their cover. */
    fun mediaTypeOf(bytes: ByteArray): String? = when {
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
        bytes.size > 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() -> "image/gif"
        else -> null
    }

    /**
     * [picture] as it is: its own file, untouched, when it has one in a kind EPUB books take; otherwise
     * (a picture made here, or a WebP or HEIC photo) the same pixels at the same size, as a JPEG.
     */
    fun asIs(picture: CoverPicture): SavedCover {
        val source = picture.source
        val type = source?.let(::mediaTypeOf)
        if (source != null && type != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            val w = bounds.outWidth.takeIf { it > 0 } ?: picture.width
            val h = bounds.outHeight.takeIf { it > 0 } ?: picture.height
            return SavedCover(source, type, w, h, asIs = true)
        }
        return SavedCover(toJpeg(picture.full, 92), "image/jpeg", picture.width, picture.height, asIs = true)
    }

    /** [picture] fitted to the Kobo's screen as [crop] says, as a JPEG. */
    fun fitted(picture: CoverPicture, crop: CoverCrop): SavedCover {
        val screen = CoverScreen.current
        return SavedCover(toJpeg(render(picture, crop, screen.width, screen.height)), "image/jpeg", screen.width, screen.height, asIs = false)
    }

    /** Halves the picture (each step averages 2×2 pixels) until it's no more than [maxSide]. */
    fun scaleDownTo(src: Bitmap, maxSide: Int): Bitmap {
        var b = src
        while (max(b.width, b.height) > maxSide * 2 && b.width >= 4 && b.height >= 4) {
            b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        }
        val longest = max(b.width, b.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            b = Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true)
        }
        return b
    }

    /** A soft, blurred copy of the picture for behind a fitted cover. */
    fun blurred(src: Bitmap): Bitmap {
        val small = scaleDownTo(src, 72)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 3) }
        // Lighten it a little, so the picture in front stands out.
        for (i in px.indices) {
            val c = px[i]
            val r = (Color.red(c) * 0.75f + 255 * 0.25f).toInt()
            val g = (Color.green(c) * 0.75f + 255 * 0.25f).toInt()
            val b = (Color.blue(c) * 0.75f + 255 * 0.25f).toInt()
            px[i] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                for (i in 0 until inner) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var n = 0
                    for (k in -radius..radius) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val c = if (horizontal) src[o * w + j] else src[j * w + o]
                        r += Color.red(c)
                        g += Color.green(c)
                        b += Color.blue(c)
                        n++
                    }
                    val idx = if (horizontal) o * w + i else i * w + o
                    dst[idx] = Color.rgb(r / n, g / n, b / n)
                }
            }
        }
        pass(px, tmp, true)
        pass(tmp, px, false)
    }

    /** Draws the cover at full Kobo size. */
    fun render(picture: CoverPicture, crop: CoverCrop, outW: Int = CoverCrop.OUT_WIDTH, outH: Int = CoverCrop.OUT_HEIGHT): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        if (crop.fit == CoverFit.FIT_BLUR) {
            val bg = picture.blurred
            val s = max(outW.toFloat() / bg.width, outH.toFloat() / bg.height)
            val bw = bg.width * s
            val bh = bg.height * s
            canvas.drawBitmap(bg, null, RectF((outW - bw) / 2, (outH - bh) / 2, (outW + bw) / 2, (outH + bh) / 2), paint)
        }
        val p = crop.place(outW.toFloat(), outH.toFloat(), picture.width, picture.height)
        // Shrink in halving steps first, so the result is smooth rather than jagged.
        var src = picture.full
        while (p.width < src.width / 2f && src.width >= 4 && src.height >= 4) {
            src = Bitmap.createScaledBitmap(src, src.width / 2, src.height / 2, true)
        }
        canvas.drawBitmap(src, null, RectF(p.left, p.top, p.left + p.width, p.top + p.height), paint)
        return out
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray =
        ByteArrayOutputStream(512 * 1024).also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
