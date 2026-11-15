package dev.kobolibrary.app.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * A comic's pages as pictures to show: each read from the comic file when it's wanted, and drawn only as big as
 * it's shown (a part of it, sharp, when zoomed in). A few are kept for going back and forth.
 */
class ComicPages(val archive: ComicArchive) : Closeable {
    val count: Int get() = archive.pages.size

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(2)

    // The files of the last few pages read, up to 48 MB (held in the app's own memory, which a phone keeps small).
    private val files = object : LruCache<Int, ByteArray>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ByteArray) = value.size
    }
    // Pictures drawn, up to 120 MB: the pages either side of the one being read, at the screen's size.
    private val drawn = object : LruCache<String, Bitmap>(120 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val sizes = ConcurrentHashMap<Int, PageSize>()
    private val crops = ConcurrentHashMap<Int, Rect>()
    private val tones = ConcurrentHashMap<Int, Int>()

    /** A page's picture size, in pixels; [wide]: wider than it's tall, as a spread over two pages is. */
    data class PageSize(val width: Int, val height: Int) {
        val wide: Boolean get() = width > height
    }

    fun knownSize(index: Int): PageSize? = sizes[index]

    suspend fun file(index: Int): ByteArray = withContext(io) {
        files.get(index) ?: archive.bytes(index).also { files.put(index, it) }
    }

    /** Page [index]'s picture size, read from the start of its file. */
    suspend fun size(index: Int): PageSize? {
        sizes[index]?.let { return it }
        return withContext(io) {
            val bytes = runCatching { file(index) }.getOrNull() ?: return@withContext null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) null else PageSize(o.outWidth, o.outHeight).also { sizes[index] = it }
        }
    }

    /**
     * The part of page [index] that's left once plain borders round it (white or black, the same colour all the
     * way round its edge) are taken off; the whole page if there are none.
     */
    suspend fun crop(index: Int): Rect? {
        crops[index]?.let { return it }
        val size = size(index) ?: return null
        return withContext(io) {
            val small = decode(index, Rect(0, 0, size.width, size.height), 256, 256) ?: return@withContext null
            val found = ComicLayout.borders(small) { x, y -> small.getPixel(x, y) }
            val k = size.width.toFloat() / small.width
            val r = Rect((found.left * k).toInt(), (found.top * k).toInt(), (found.right * k).toInt().coerceAtMost(size.width), (found.bottom * k).toInt().coerceAtMost(size.height))
            // Next to nothing left: it's not a border, it's the page.
            val kept = if (r.width() < size.width / 3 || r.height() < size.height / 3) Rect(0, 0, size.width, size.height) else r
            crops[index] = kept
            kept
        }
    }

    /** The colour round page [index]'s edges, for a background that matches it: black or white. */
    suspend fun tone(index: Int): Int {
        tones[index]?.let { return it }
        val size = size(index) ?: return Color.BLACK
        return withContext(io) {
            val small = decode(index, Rect(0, 0, size.width, size.height), 64, 64) ?: return@withContext Color.BLACK
            var light = 0
            var all = 0
            for (x in 0 until small.width) for (y in listOf(0, small.height - 1)) { all++; if (lum(small.getPixel(x, y)) > 128) light++ }
            for (y in 0 until small.height) for (x in listOf(0, small.width - 1)) { all++; if (lum(small.getPixel(x, y)) > 128) light++ }
            (if (light * 2 > all) Color.WHITE else Color.BLACK).also { tones[index] = it }
        }
    }

    private fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    /**
     * Part [region] of page [index] (in the picture's pixels), drawn about [outW] × [outH] (never more than the
     * picture has: it's read at a power-of-two fraction of its size, no smaller than asked for). Null if it
     * can't be read.
     */
    suspend fun bitmap(index: Int, region: Rect, outW: Int, outH: Int): Bitmap? {
        val sample = ComicLayout.sampleFor(region.width(), region.height(), outW, outH)
        val key = "$index:${region.flattenToString()}:$sample"
        drawn.get(key)?.takeIf { !it.isRecycled }?.let { return it }
        return withContext(io) { decode(index, region, outW, outH)?.also { drawn.put(key, it) } }
    }

    private suspend fun decode(index: Int, region: Rect, outW: Int, outH: Int): Bitmap? {
        val bytes = runCatching { file(index) }.getOrNull() ?: return null
        val sample = ComicLayout.sampleFor(region.width(), region.height(), outW, outH)
        val size = sizes[index]
        val whole = size != null && region.left == 0 && region.top == 0 && region.right == size.width && region.bottom == size.height
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        if (!whole) {
            // Only the part asked for, read straight from the file (JPEG, PNG and WebP can be).
            val part = runCatching {
                val d = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
                else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
                try { d?.decodeRegion(region, opts) } finally { d?.recycle() }
            }.getOrNull()
            if (part != null) return part
        }
        val full = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull() ?: return null
        if (whole) return full
        // The part, cut from the whole picture read smaller.
        val k = full.width.toFloat() / (size?.width ?: full.width)
        val r = Rect((region.left * k).toInt(), (region.top * k).toInt(), (region.right * k).toInt().coerceAtMost(full.width), (region.bottom * k).toInt().coerceAtMost(full.height))
        if (r.width() <= 0 || r.height() <= 0) return full
        return Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height()).also { if (it !== full) full.recycle() }
    }

    override fun close() {
        drawn.evictAll()
        files.evictAll()
        runCatching { archive.close() }
    }
}

/** The arithmetic of showing a comic's pages: how they fit the screen, which are split, where taps go. */
object ComicLayout {
    /**
     * The biggest power of two a [w] × [h] picture (or part) can be read at a fraction of, and still be at least
     * [outW] × [outH] (or the whole of it, if it's smaller).
     */
    fun sampleFor(w: Int, h: Int, outW: Int, outH: Int): Int {
        var s = 1
        while (w / (s * 2) >= outW.coerceAtLeast(1) && h / (s * 2) >= outH.coerceAtLeast(1)) s *= 2
        return s
    }

    /** One page, or half of a wide one shown as a page of its own. */
    data class Piece(val page: Int, val part: Part = Part.WHOLE) {
        val key: String get() = "$page:${part.name}"
    }

    enum class Part { WHOLE, LEFT, RIGHT }

    /**
     * The pages in reading order, each wide page known to be wide shown as its two halves when [split]: the right
     * half first reading right to left ([rtl]), the left first otherwise, or the other way round ([invert]).
     */
    fun pieces(count: Int, wide: (Int) -> Boolean, split: Boolean, rtl: Boolean, invert: Boolean): List<Piece> {
        val out = ArrayList<Piece>(count)
        for (i in 0 until count) {
            if (split && wide(i)) {
                val rightFirst = rtl != invert
                out += Piece(i, if (rightFirst) Part.RIGHT else Part.LEFT)
                out += Piece(i, if (rightFirst) Part.LEFT else Part.RIGHT)
            } else out += Piece(i)
        }
        return out
    }

    /** The part of a page's picture a piece shows: all of it, or a half. */
    fun regionOf(part: Part, crop: Rect): Rect = when (part) {
        Part.WHOLE -> Rect(crop)
        Part.LEFT -> Rect(crop.left, crop.top, crop.left + crop.width() / 2, crop.bottom)
        Part.RIGHT -> Rect(crop.left + crop.width() / 2, crop.top, crop.right, crop.bottom)
    }

    /**
     * How big a [cw] × [ch] page is shown on a [w] × [h] screen at first, for [scale]. A wide page on an upright
     * screen fills its height when [zoomWide] (to be moved along).
     */
    fun fit(cw: Float, ch: Float, w: Float, h: Float, scale: ComicScale, zoomWide: Boolean): Pair<Float, Float> {
        val screen = minOf(w / cw, h / ch)
        val k = when (scale) {
            ComicScale.STRETCH -> return w to h
            ComicScale.FIT_SCREEN -> if (zoomWide && cw > ch && h > w) h / ch else screen
            ComicScale.FIT_WIDTH -> w / cw
            ComicScale.FIT_HEIGHT -> h / ch
            ComicScale.ORIGINAL -> 1f
            ComicScale.SMART -> when {
                cw > ch -> if (zoomWide && h > w) h / ch else screen
                else -> w / cw
            }
        }
        return cw * k to ch * k
    }

    /**
     * Where a page [pw] × [ph] (shown) sits at first on a [w] × [h] screen: in the middle where it's smaller, and
     * where it's bigger, at its top, and its left or right side as [start] says ([rtl]: automatic starts at the right).
     */
    fun startOffset(pw: Float, ph: Float, w: Float, h: Float, start: ComicZoomStart, rtl: Boolean): Pair<Float, Float> {
        val x = if (pw <= w) (w - pw) / 2 else when (start) {
            ComicZoomStart.LEFT -> 0f
            ComicZoomStart.RIGHT -> w - pw
            ComicZoomStart.CENTER -> (w - pw) / 2
            ComicZoomStart.AUTO -> if (rtl) w - pw else 0f
        }
        val y = if (ph <= h) (h - ph) / 2 else 0f
        return x to y
    }

    /** What a tap does: back a page (-1), on a page (1), or the menu (0). */
    fun tapAction(x: Float, y: Float, taps: ComicTaps, mode: ComicMode, invert: ComicInvert): Int {
        val ix = if (invert == ComicInvert.HORIZONTAL || invert == ComicInvert.BOTH) 1 - x else x
        val iy = if (invert == ComicInvert.VERTICAL || invert == ComicInvert.BOTH) 1 - y else y
        val rtl = mode == ComicMode.RIGHT_TO_LEFT
        val chosen = if (taps == ComicTaps.DEFAULT) (if (mode.horizontal) ComicTaps.RIGHT_LEFT else ComicTaps.L_SHAPED) else taps
        val third = 1f / 3
        fun leftRight(left: Boolean) = if (left == rtl) 1 else -1
        return when (chosen) {
            ComicTaps.OFF -> 0
            ComicTaps.RIGHT_LEFT -> when {
                ix < third -> leftRight(true)
                ix > 2 * third -> leftRight(false)
                else -> 0
            }
            ComicTaps.L_SHAPED -> when {
                iy < third -> -1
                iy > 2 * third -> 1
                ix < third -> -1
                ix > 2 * third -> 1
                else -> 0
            }
            ComicTaps.KINDLE -> when {
                iy < third -> 0
                ix < third -> -1
                else -> 1
            }
            ComicTaps.EDGE -> when {
                ix < third || ix > 2 * third -> 1
                iy > 2 * third -> -1
                else -> 0
            }
            ComicTaps.DEFAULT -> 0
        }
    }

    /**
     * The part of a picture inside plain borders round it: the edge's colour (white or black, the same at all four
     * corners) taken off each side as far as it goes. [pixel] reads the picture, [width] × [height] (a small copy).
     */
    fun borders(picture: Bitmap, pixel: (Int, Int) -> Int): Rect {
        val w = picture.width
        val h = picture.height
        if (w < 8 || h < 8) return Rect(0, 0, w, h)
        val corners = listOf(pixel(0, 0), pixel(w - 1, 0), pixel(0, h - 1), pixel(w - 1, h - 1))
        val lums = corners.map { (Color.red(it) * 299 + Color.green(it) * 587 + Color.blue(it) * 114) / 1000 }
        val white = lums.all { it > 220 }
        val black = lums.all { it < 35 }
        if (!white && !black) return Rect(0, 0, w, h)
        fun plain(c: Int): Boolean {
            val l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            return if (white) l > 215 else l < 40
        }
        // A line is a border while nearly all of it is the plain colour (a speck of dirt doesn't stop it).
        fun rowPlain(y: Int) = (0 until w).count { plain(pixel(it, y)) } >= w * 0.985
        fun colPlain(x: Int, t: Int, b: Int) = (t until b).count { plain(pixel(x, it)) } >= (b - t) * 0.985
        var top = 0
        while (top < h - 1 && rowPlain(top)) top++
        var bottom = h
        while (bottom > top + 1 && rowPlain(bottom - 1)) bottom--
        var left = 0
        while (left < w - 1 && colPlain(left, top, bottom)) left++
        var right = w
        while (right > left + 1 && colPlain(right - 1, top, bottom)) right--
        return Rect(left, top, right, bottom)
    }
}
