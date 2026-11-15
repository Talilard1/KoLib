package dev.kobolibrary.app.comic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Comic book files made for tests: pages drawn as panels in colours, so each can be told apart. */
object TestComics {
    private val colours = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(), 0xFF8E24AA.toInt(), 0xFFFB8C00.toInt())

    /** A page [w] × [h]: white paper, a border of [margin] pixels round it, and panels in colour [n]. */
    fun page(n: Int, w: Int = 600, h: Int = 900, margin: Int = 30, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = colours[n % colours.size] }
        val ink = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 6f }
        val rows = 3
        val ph = (h - 2 * margin - (rows - 1) * 16) / rows
        for (r in 0 until rows) {
            val top = margin + r * (ph + 16)
            if (r == 1) {
                val half = (w - 2 * margin - 16) / 2
                c.drawRect(margin.toFloat(), top.toFloat(), (margin + half).toFloat(), (top + ph).toFloat(), p)
                c.drawRect(margin.toFloat(), top.toFloat(), (margin + half).toFloat(), (top + ph).toFloat(), ink)
                c.drawRect((margin + half + 16).toFloat(), top.toFloat(), (w - margin).toFloat(), (top + ph).toFloat(), p)
                c.drawRect((margin + half + 16).toFloat(), top.toFloat(), (w - margin).toFloat(), (top + ph).toFloat(), ink)
            } else {
                c.drawRect(margin.toFloat(), top.toFloat(), (w - margin).toFloat(), (top + ph).toFloat(), p)
                c.drawRect(margin.toFloat(), top.toFloat(), (w - margin).toFloat(), (top + ph).toFloat(), ink)
            }
        }
        val out = ByteArrayOutputStream()
        bmp.compress(format, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** A CBZ at [file] holding [entries] (name to bytes), in that order, stored as they are. */
    fun zip(file: File, entries: List<Pair<String, ByteArray>>): File {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                })
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    const val INFO = """<?xml version="1.0" encoding="utf-8"?>
<ComicInfo>
  <Title>The Lantern Thief</Title>
  <Series>Harbour Lights</Series>
  <Number>3</Number>
  <Writer>Ada Fenwick</Writer>
  <Penciller>Tom Reyes</Penciller>
  <Publisher>Quayside Press</Publisher>
  <Genre>Mystery, Adventure</Genre>
  <Year>1931</Year>
  <LanguageISO>en</LanguageISO>
  <Manga>YesAndRightToLeft</Manga>
</ComicInfo>"""

    /** A comic of [pages] pages, numbered out of order in the file as some are (page10 before page2), with a ComicInfo. */
    fun comic(file: File, pages: Int = 12, info: Boolean = true): File {
        val entries = ArrayList<Pair<String, ByteArray>>()
        if (info) entries += "ComicInfo.xml" to INFO.toByteArray()
        entries += "__MACOSX/._page1.jpg" to ByteArray(10)
        entries += "pages/.hidden.jpg" to ByteArray(10)
        for (i in (1..pages).sortedBy { it.toString() }) entries += "pages/page$i.jpg" to page(i - 1)
        return zip(file, entries)
    }
}
