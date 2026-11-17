package dev.kolib.app.comic

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A comic's pages written out as a CBZ, one page at a time (for a CB7, which the Kobo doesn't read). */
object ComicRepack {
    fun toCbz(archive: ComicArchive, target: File) {
        ZipOutputStream(target.outputStream().buffered(256 * 1024)).use { out ->
            val width = maxOf(3, archive.pages.size.toString().length)
            archive.pages.forEachIndexed { i, name ->
                val bytes = archive.bytes(i)
                val ext = name.substringAfterLast('.', "jpg").lowercase()
                // Pictures are already compressed: stored as they are, in reading order.
                out.putNextEntry(ZipEntry("%0${width}d.%s".format(i + 1, ext)).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                })
                out.write(bytes)
                out.closeEntry()
            }
            archive.comicInfo?.let { info ->
                out.putNextEntry(ZipEntry("ComicInfo.xml"))
                out.write(info)
                out.closeEntry()
            }
        }
    }
}
