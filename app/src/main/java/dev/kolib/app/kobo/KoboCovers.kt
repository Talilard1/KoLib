package dev.kolib.app.kobo

import dev.kolib.app.tr
import android.graphics.Bitmap
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.log.AppLog
import dev.kolib.app.ui.cover.CoverCrop
import dev.kolib.app.ui.cover.CoverFit
import dev.kolib.app.ui.cover.CoverRendering
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** What happens, when sending, to covers that aren't the shape of the Kobo's screen. */
enum class CoverFitting(val label: String, val explanation: String) {
    BLUR(
        tr("Blur the gaps"),
        tr("Whole cover, with a blurred copy filling the gaps."),
    ),
    FILL(
        tr("Crop to fill"),
        tr("Fills the screen. Edges that don't fit are cropped."),
    ),
    AS_IS(
        tr("Original size"),
        tr("Original size. The Kobo shows blank space around it."),
    ),
}

/**
 * A book's cover drawn at the Kobo's screen size, and the book's own cover ([own]) it was made from.
 * [jpegForBook] is set when the book's own cover has to change to match.
 */
class ShapedCover(val bitmap: Bitmap, val jpegForBook: ByteArray?, val sourceWidth: Int, val sourceHeight: Int, val own: Bitmap = bitmap)

/** Covers for the Kobo: shaped to its screen, and the pictures it shows in its library. */
object KoboCovers {

    /**
     * Covers within this much of the screen's shape are left alone, and fill the screen with a sliver lost
     * at two edges. Kobo screens differ from one another by less, so a cover made for one fills them all.
     */
    const val TOLERANCE = 0.025f

    /** One of the pictures the Kobo keeps for each book: the end of its file name, and its size. */
    data class Picture(val suffix: String, val width: Int, val height: Int, val fullScreen: Boolean)

    /**
     * The pictures the Kobo keeps for each book: the full-screen one, the size of its [screen]; the one for
     * the home screen and the book's details (355 × 530); and the library's (149 × 223). The smaller two
     * are the same on every model and the shape of an ordinary book cover, as the Kobo's own are, so every
     * book in its lists is the same height.
     */
    fun pictures(screen: KoboScreen) = listOf(
        Picture(" - N3_FULL.parsed", screen.width, screen.height, fullScreen = true),
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

    fun isKoboShaped(width: Int, height: Int, screen: KoboScreen): Boolean =
        width > 0 && height > 0 && abs((width.toFloat() / height) / screen.aspect - 1f) <= TOLERANCE

    /**
     * The book's cover at the size of the Kobo's [screen], or null if it has none, or [fitting] says to
     * leave it unchanged and it isn't the screen's shape already (then the Kobo makes its own pictures of it).
     */
    fun shape(epub: File, fitting: CoverFitting, screen: KoboScreen): ShapedCover? {
        val bytes = runCatching { EpubFile.open(epub).use { e -> e.findCover()?.let { e.readBytes(it.path) } } }.getOrNull() ?: return null
        val decoded = CoverRendering.decode(bytes) ?: run {
            AppLog.w("Covers", "The cover of ${epub.name} couldn't be read as a picture, so it's left as it is.")
            return null
        }
        val shaped = isKoboShaped(decoded.width, decoded.height, screen)
        // A cover left unchanged that is the screen's shape anyway needs no change to get the app's pictures.
        if (fitting == CoverFitting.AS_IS && !shaped) return null
        val fit = if (shaped || fitting == CoverFitting.FILL) CoverFit.FILL else CoverFit.FIT_BLUR
        val out = CoverRendering.render(CoverRendering.prepare(decoded, "cover"), CoverCrop(fit = fit), screen.width, screen.height)
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
