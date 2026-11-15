package dev.kobolibrary.app.ocr

/**
 * A scanned page made ready to read. On thin paper the page behind shows through, faint and back to front, and its
 * words get read as part of the page's own: a line of capitals spaced out across a title page came out as nonsense.
 * Everything paler than halfway between the print and the paper is made paper, and the print is darkened. A page
 * mostly of pictures (more of it dark than a page of print ever is) is left as it is, so its pictures are still found.
 */
object ScanCleanup {

    /** [gray] (a byte a pixel, [width] by [height]) cleaned, as a new array; or [gray] itself if there's nothing to do. */
    fun clean(gray: ByteArray, width: Int, height: Int): ByteArray {
        val n = minOf(gray.size, width.toLong().times(height).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (n <= 0) return gray
        val hist = LongArray(256)
        for (i in 0 until n) hist[gray[i].toInt() and 0xff]++
        fun level(fraction: Double): Int {
            val want = (n * fraction).toLong()
            var seen = 0L
            for (v in 0..255) {
                seen += hist[v]
                if (seen > want) return v
            }
            return 255
        }
        // The print: the darkest of it (a two-hundredth of the page). The paper: what most of the page is.
        val ink = level(0.005)
        val paper = level(0.5)
        if (paper - ink < 60) return gray
        val cut = ink + (paper - ink) / 2
        var dark = 0L
        for (v in 0 until cut) dark += hist[v]
        if (dark > n * 0.25) return gray
        val span = maxOf(1, cut - ink)
        val shade = ByteArray(256) { v -> if (v >= cut) 255.toByte() else ((v - ink).coerceAtLeast(0) * 153 / span).toByte() }
        return ByteArray(n) { shade[gray[it].toInt() and 0xff] }
    }
}
