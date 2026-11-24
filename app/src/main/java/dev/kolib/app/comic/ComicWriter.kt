package dev.kolib.app.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.tr
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A comic's pages, as edited, written out as a CBZ; and its details as a ComicInfo.xml. */
object ComicWriter {
    /**
     * Writes [pages] as a CBZ at [target], in their order, with [info] as its ComicInfo.xml. A page used as it is keeps
     * its picture's bytes as they are; one halved, turned or cropped is drawn again, as a JPEG.
     */
    fun write(pages: List<DraftPage>, bytes: (PageSource) -> ByteArray, info: ByteArray?, target: File) {
        ZipOutputStream(target.outputStream().buffered(256 * 1024)).use { out ->
            val width = maxOf(3, pages.size.toString().length)
            pages.forEachIndexed { i, page ->
                val raw = bytes(page.source)
                val data = if (page.asIs) raw else render(raw, page)
                val ext = if (page.asIs) kindOf(raw) else "jpg"
                // Pictures are compressed already: stored as they are.
                out.putNextEntry(ZipEntry("%0${width}d.%s".format(i + 1, ext)).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                })
                out.write(data)
                out.closeEntry()
            }
            info?.let {
                out.putNextEntry(ZipEntry("ComicInfo.xml"))
                out.write(it)
                out.closeEntry()
            }
        }
    }

    /** What kind of picture [bytes] are, by their first bytes: the ending its file has. */
    fun kindOf(bytes: ByteArray): String {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 -> "jpg"
            at(0) == 0x89 && at(1) == 'P'.code && at(2) == 'N'.code -> "png"
            at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> "gif"
            at(0) == 'R'.code && at(1) == 'I'.code && at(8) == 'W'.code && at(9) == 'E'.code -> "webp"
            at(0) == 'B'.code && at(1) == 'M'.code -> "bmp"
            else -> "jpg"
        }
    }

    /** [bytes]'s picture halved, turned and cropped as [page] says, as a JPEG (made smaller only if it's huge). */
    fun render(bytes: ByteArray, page: DraftPage, maxPixels: Long = 40_000_000L): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > maxPixels) sample *= 2
        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException(tr("A page's picture couldn't be read."))
        val done = transform(picture, page)
        return ByteArrayOutputStream().also { done.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
    }

    /** [picture] halved, turned and cropped as [page] says (for pages written out, and for what's shown of them). */
    fun transform(picture: Bitmap, page: DraftPage): Bitmap {
        var b = picture
        page.half?.let { h ->
            val w = (b.width / 2).coerceAtLeast(1)
            b = Bitmap.createBitmap(b, if (h == Half.LEFT) 0 else b.width - w, 0, w, b.height)
        }
        val turns = ((page.turns % 4) + 4) % 4
        if (turns != 0) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f * turns) }, true)
        page.crop?.takeIf { !it.whole }?.let { c ->
            val x = (c.left * b.width).toInt().coerceIn(0, b.width - 1)
            val y = (c.top * b.height).toInt().coerceIn(0, b.height - 1)
            val w = ((c.right - c.left) * b.width).toInt().coerceIn(1, b.width - x)
            val h = ((c.bottom - c.top) * b.height).toInt().coerceIn(1, b.height - y)
            b = Bitmap.createBitmap(b, x, y, w, h)
        }
        return b
    }

    /** A ComicInfo.xml for [meta]: its title, series and number, writers, publisher, year, summary, genres and language. */
    fun info(meta: BookMetadata, rightToLeft: Boolean = false): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val summary = meta.description?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.ifEmpty { null }
        val number = meta.seriesIndex?.let { n -> if (n == Math.floor(n)) n.toLong().toString() else n.toString() }
        val fields = listOfNotNull(
            "Title" to meta.title,
            meta.series?.let { "Series" to it },
            number?.let { "Number" to it },
            summary?.let { "Summary" to it },
            meta.published?.take(4)?.takeIf { y -> y.all { it.isDigit() } }?.let { "Year" to it },
            meta.authors.takeIf { it.isNotEmpty() }?.let { "Writer" to it.joinToString(", ") },
            meta.publisher?.let { "Publisher" to it },
            meta.tags.takeIf { it.isNotEmpty() }?.let { "Genre" to it.joinToString(", ") },
            meta.language?.let { "LanguageISO" to it },
            if (rightToLeft) "Manga" to "YesAndRightToLeft" else null,
        )
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<ComicInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n")
            fields.forEach { (k, v) -> append("  <").append(k).append('>').append(esc(v)).append("</").append(k).append(">\n") }
            append("</ComicInfo>\n")
        }
        return xml.toByteArray(Charsets.UTF_8)
    }
}
