package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr
import android.graphics.Bitmap
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.ui.cover.CoverCrop
import dev.kobolibrary.app.ui.cover.CoverFit
import dev.kobolibrary.app.ui.cover.CoverRendering
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** What happens, when sending, to covers that aren't the shape of the Kobo's screen. */
enum class CoverFitting(val label: String, val explanation: String) {
    BLUR(
        tr("Blur the gaps"),
        tr("Shows the whole cover, with a blurred copy of it filling the empty space."),
    ),
    FILL(
        tr("Crop to fill"),
        tr("Enlarges the cover to fill the screen and cuts off the edges that don't fit."),
    ),
    AS_IS(
        tr("Leave unchanged"),
        tr("Sends the cover unchanged. The Kobo shows blank space around it."),
    ),
}

/**
 * A book's cover drawn at the Kobo's screen size, and the book's own cover ([own]) it was made from.
 * [jpegForBook] is set when the book's own cover has to change to match.
 */
class ShapedCover(val bitmap: Bitmap, val jpegForBook: ByteArray?, val sourceWidth: Int, val sourceHeight: Int, val own: Bitmap = bitmap)

/** Covers for the Kobo: shaped to its screen, and the pictures it shows in its library. */
object KoboCovers {

    /** Covers within this much of the screen's shape are left alone. */
    const val TOLERANCE = 0.015f

    /** One of the pictures the Kobo keeps for each book: the end of its file name, and its size. */
    data class Picture(val suffix: String, val width: Int, val height: Int, val fullScreen: Boolean)

    /**
     * The pictures the Kobo keeps for each book, for 1072 × 1448 screens like the Clara's: the full-screen
     * one, the one for the home screen and the book's details (355 × 530), and the library's (149 × 223).
     * The smaller two are the shape of an ordinary book cover, as the Kobo's own are, so every book in its
     * lists is the same height.
     */
    val PICTURES = listOf(
        Picture(" - N3_FULL.parsed", CoverCrop.OUT_WIDTH, CoverCrop.OUT_HEIGHT, fullScreen = true),
        Picture(" - N3_LIBRARY_FULL.parsed", 355, 530, fullScreen = false),
        Picture(" - N3_LIBRARY_GRID.parsed", 149, 223, fullScreen = false),
    )

    /**
     * What the library pictures are made from: the book's own cover when it's the shape of a book cover,
     * so the Kobo's lists show the whole cover as it is; otherwise (a square picture, say) the cover as
     * fitted to the screen.
     */
    fun thumbnailSource(cover: ShapedCover): Bitmap {
        val aspect = cover.own.width.toFloat() / cover.own.height
        return if (aspect in 0.6f..0.8f) cover.own else cover.bitmap
    }

    /** [bitmap] scaled to cover exactly [w] × [h], with whatever sticks out trimmed evenly from both sides. */
    fun boxed(bitmap: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        val sw = max(w, (bitmap.width * scale).roundToInt())
        val sh = max(h, (bitmap.height * scale).roundToInt())
        val scaledToCover = scaled(bitmap, sw, sh)
        if (sw == w && sh == h) return scaledToCover
        return Bitmap.createBitmap(scaledToCover, (sw - w) / 2, (sh - h) / 2, w, h)
    }

    fun isKoboShaped(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && abs((width.toFloat() / height) / CoverCrop.ASPECT - 1f) <= TOLERANCE

    /**
     * The book's cover at the Kobo's screen size, or null if it has none, or [fitting] says to leave it
     * unchanged and it isn't the screen's shape already (then the Kobo makes its own pictures of it).
     */
    fun shape(epub: File, fitting: CoverFitting): ShapedCover? {
        val bytes = runCatching { EpubFile.open(epub).use { e -> e.findCover()?.let { e.readBytes(it.path) } } }.getOrNull() ?: return null
        val decoded = CoverRendering.decode(bytes) ?: run {
            AppLog.w("Covers", "The cover of ${epub.name} couldn't be read as a picture, so it's left as it is.")
            return null
        }
        val shaped = isKoboShaped(decoded.width, decoded.height)
        // A cover left unchanged that is the screen's shape anyway needs no change to get the app's pictures.
        if (fitting == CoverFitting.AS_IS && !shaped) return null
        val fit = if (shaped || fitting == CoverFitting.FILL) CoverFit.FILL else CoverFit.FIT_BLUR
        val out = CoverRendering.render(CoverRendering.prepare(decoded, "cover"), CoverCrop(fit = fit))
        return ShapedCover(out, if (shaped) null else CoverRendering.toJpeg(out), decoded.width, decoded.height, own = decoded)
    }

    /** [bitmap] scaled to exactly [w] × [h], smoothly. */
    fun scaled(bitmap: Bitmap, w: Int, h: Int): Bitmap {
        var b = bitmap
        while (b.width / 2 >= w && b.height / 2 >= h && b.width >= 4 && b.height >= 4) {
            b = Bitmap.createScaledBitmap(b, max(1, (b.width / 2f).roundToInt()), max(1, (b.height / 2f).roundToInt()), true)
        }
        return if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
    }
}
