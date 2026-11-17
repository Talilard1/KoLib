package dev.kolib.app.ui.cover

import dev.kolib.app.kobo.CoverScreen
import dev.kolib.app.tr
import kotlin.math.max
import kotlin.math.min

/** How a picture is placed in the cover frame. */
enum class CoverFit(val label: String, val explanation: String) {
    /** Fills the whole frame; whatever sticks out is cut off. */
    FILL(tr("Crop to fill"), tr("Fills the screen. Edges that don't fit are cropped.")),
    /** Shows the whole picture, with a blurred copy of it behind. */
    FIT_BLUR(tr("Blur the gaps"), tr("Whole picture, with a blurred copy filling the gaps.")),
    /** Shows the whole picture on white. */
    FIT_WHITE(tr("White gaps"), tr("Whole picture, with white filling the gaps.")),
    /** The picture itself, untouched: not cropped, resized or redrawn, here or when it's sent. */
    AS_IS(tr("Original size"), tr("No cropping or resizing.")),
}

/**
 * Where a picture sits in the cover frame, independent of the frame's size on screen: [zoom] on top of
 * the fitted size, and the point of the picture (as fractions of its width and height) at the frame's
 * centre. The same crop gives the same cover on screen and when saved at full size.
 */
data class CoverCrop(
    val zoom: Float = 1f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val fit: CoverFit = CoverFit.FILL,
) {
    data class Placement(val left: Float, val top: Float, val width: Float, val height: Float)

    private fun baseScale(frameW: Float, frameH: Float, imageW: Int, imageH: Int): Float =
        if (fit == CoverFit.FILL) max(frameW / imageW, frameH / imageH) else min(frameW / imageW, frameH / imageH)

    /** Where the picture is drawn in a frame of the given size, kept within the allowed range. */
    fun place(frameW: Float, frameH: Float, imageW: Int, imageH: Int): Placement {
        if (fit == CoverFit.AS_IS) {
            // Shown whole and in the middle; it can't be moved or zoomed, since it's left unchanged.
            val s = baseScale(frameW, frameH, imageW, imageH)
            return Placement((frameW - imageW * s) / 2, (frameH - imageH * s) / 2, imageW * s, imageH * s)
        }
        val s = baseScale(frameW, frameH, imageW, imageH) * zoom
        val w = imageW * s
        val h = imageH * s
        val left = clampAxis(frameW / 2 - centerX * w, frameW, w)
        val top = clampAxis(frameH / 2 - centerY * h, frameH, h)
        return Placement(left, top, w, h)
    }

    /** The same crop with its centre moved back inside the allowed range. */
    fun clamped(frameW: Float, frameH: Float, imageW: Int, imageH: Int): CoverCrop {
        val p = place(frameW, frameH, imageW, imageH)
        return copy(centerX = (frameW / 2 - p.left) / p.width, centerY = (frameH / 2 - p.top) / p.height)
    }

    /**
     * After a pinch or drag: [panX]/[panY] move the picture, and [zoomBy] scales it around the point
     * ([pivotX], [pivotY]) of the frame, so what's under the fingers stays under them.
     */
    fun transformed(
        panX: Float,
        panY: Float,
        zoomBy: Float,
        pivotX: Float,
        pivotY: Float,
        frameW: Float,
        frameH: Float,
        imageW: Int,
        imageH: Int,
    ): CoverCrop {
        if (fit == CoverFit.AS_IS) return this
        val before = place(frameW, frameH, imageW, imageH)
        val newZoom = (zoom * zoomBy).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val factor = newZoom / zoom
        // The picture point under the pivot, as a fraction of the picture.
        val px = (pivotX - before.left) / before.width
        val py = (pivotY - before.top) / before.height
        val w = before.width * factor
        val h = before.height * factor
        val left = pivotX + panX - px * w
        val top = pivotY + panY - py * h
        return copy(
            zoom = newZoom,
            centerX = (frameW / 2 - left) / w,
            centerY = (frameH / 2 - top) / h,
        ).clamped(frameW, frameH, imageW, imageH)
    }

    /** Zoom around the middle of the frame (for the slider). */
    fun zoomedTo(newZoom: Float, frameW: Float, frameH: Float, imageW: Int, imageH: Int): CoverCrop =
        transformed(0f, 0f, newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM) / zoom, frameW / 2, frameH / 2, frameW, frameH, imageW, imageH)

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f

        /**
         * The size covers are saved at, in pixels: the screen of the Kobo last plugged in, or the Kobo
         * Clara's (1072 × 1448) until one has been. See [CoverScreen].
         */
        val OUT_WIDTH: Int get() = CoverScreen.current.width
        val OUT_HEIGHT: Int get() = CoverScreen.current.height
        val ASPECT: Float get() = CoverScreen.current.aspect

        private fun clampAxis(start: Float, frame: Float, drawn: Float): Float {
            val lo = min(0f, frame - drawn)
            val hi = max(0f, frame - drawn)
            return start.coerceIn(lo, hi)
        }
    }
}
