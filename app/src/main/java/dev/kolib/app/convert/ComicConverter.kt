package dev.kolib.app.convert

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.kolib.app.comic.ComicArchive
import dev.kolib.app.comic.ComicInfo
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.tr
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Comic book files (CBZ, CBR, CB7) as books. In the library a comic is kept as the file it came as, read in
 * the comic reader and sent to the Kobo as it is; its EPUB is a stand-in, with its details and its cover
 * (the first page), for the library to show and edit. A whole EPUB, every page a picture, is made only when
 * it's asked for (saving the comic as an EPUB), one page at a time.
 */
object ComicConverter {

    /** The stand-in EPUB for the comic in [archive] (a file named [fileName]): its details and its cover. */
    fun standIn(archive: ComicArchive, fileName: String, target: File) {
        if (archive.pages.isEmpty()) throw ConversionException(tr("There are no pictures in this comic book file."))
        val info = ComicInfo.read(archive.comicInfo, fileName)
        val out = EpubOut(info.meta)
        val cover = runCatching { coverJpeg(archive.bytes(0)) }.getOrNull()
        if (cover != null) {
            val src = out.image("cover.jpg", cover, asCover = true)
            out.page(info.meta.title, "<div class=\"page\"><img src=\"${HtmlSoup.escapeAttr(src)}\" alt=\"\"/></div>", extraStyles = listOf(out.style("comic.css", COMIC_CSS)))
        } else {
            out.page(info.meta.title, "<p>${HtmlSoup.escapeText(info.meta.title)}</p>")
        }
        out.write(target)
    }

    /** A whole EPUB of the comic in [archive]: each page a picture, in order, the first one the cover. */
    fun wholeEpub(archive: ComicArchive, meta: BookMetadata, target: File) {
        if (archive.pages.isEmpty()) throw ConversionException(tr("There are no pictures in this comic book file."))
        val out = EpubOut(meta)
        val css = out.style("comic.css", COMIC_CSS)
        archive.pages.forEachIndexed { i, path ->
            val src = out.imageLater("p%05d.%s".format(i + 1, path.substringAfterLast('.', "jpg").lowercase()), asCover = i == 0) { archive.bytes(i) }
            out.page(if (i == 0) meta.title else null, "<div class=\"page\"><img src=\"${HtmlSoup.escapeAttr(src)}\" alt=\"\"/></div>", extraStyles = listOf(css))
        }
        out.write(target)
    }

    private const val COMIC_CSS = """html, body { margin: 0; padding: 0; height: 100%; }
div.page { height: 100%; text-align: center; }
div.page img { max-width: 100%; max-height: 100%; height: auto; }
"""

    /** A comic page as a cover: no bigger than the library needs, as a JPEG. */
    fun coverJpeg(page: ByteArray, most: Int = 1600): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(page, 0, page.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= most) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(page, 0, page.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val k = minOf(1f, most.toFloat() / maxOf(bmp.width, bmp.height))
        val sized = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
        val out = ByteArrayOutputStream()
        sized.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (sized !== bmp) sized.recycle()
        bmp.recycle()
        return out.toByteArray()
    }
}
