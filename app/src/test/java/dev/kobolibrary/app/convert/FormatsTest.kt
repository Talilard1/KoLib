package dev.kobolibrary.app.convert

import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Telling what kind of file a book is, and making an EPUB of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormatsTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file(name: String, bytes: ByteArray): File = File(tmp.root, name).apply { writeBytes(bytes) }
    private fun file(name: String, text: String): File = file(name, text.toByteArray())

    private fun zip(name: String, entries: Map<String, ByteArray>): File {
        val f = File(tmp.root, name)
        ZipOutputStream(f.outputStream()).use { z ->
            for ((n, b) in entries) {
                z.putNextEntry(ZipEntry(n))
                z.write(b)
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun theSamplesAreToldApartByWhatsInThem() {
        val expected = mapOf(
            "11.kindle.images" to SourceFormat.MOBI,
            "alice.docx" to SourceFormat.DOCX,
            "alice.odt" to SourceFormat.ODT,
            "alice.fb2" to SourceFormat.FB2,
            "alice-objstm.pdf" to SourceFormat.PDF,
            "reportlab.pdf" to SourceFormat.PDF,
            "comic.cbz" to SourceFormat.CBZ,
            "alice.rtf" to SourceFormat.RTF,
        )
        for ((name, format) in expected) {
            val f = TestSamples.file(name)
            assumeTrue(name, f != null)
            // The name doesn't matter: a file called "book" is still found out.
            assertEquals(name, format, BookFormats.detect(f!!, "book"))
        }
    }

    @Test
    fun smallFilesAreToldApartToo() {
        assertEquals(SourceFormat.RTF, BookFormats.detect(file("a", "{\\rtf1\\ansi Hello}"), "a"))
        assertEquals(SourceFormat.FB2, BookFormats.detect(file("b", "﻿<?xml version=\"1.0\"?>\n<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"></FictionBook>"), "b"))
        assertEquals(SourceFormat.MARKDOWN, BookFormats.detect(file("c.md", "# Title\n\nText."), "c.md"))
        assertEquals(SourceFormat.HTML, BookFormats.detect(file("d", "<!DOCTYPE html><html><body><p>Hi</p></body></html>"), "d"))
        assertEquals(SourceFormat.HTML, BookFormats.detect(file("e.htm", "<p>Hi</p>"), "e.htm"))
        assertEquals(SourceFormat.TXT, BookFormats.detect(file("f", "Just some words."), "f"))
        assertEquals(SourceFormat.PDF, BookFormats.detect(file("g", "%PDF-1.7\n%..."), "g"))
        // Something binary that's none of them.
        assertNull(BookFormats.detect(file("h.bin", ByteArray(300) { (it % 7).toByte() }), "h.bin"))
        // Zips: an EPUB, a Word file, a zipped FictionBook, a comic, and one that's none of these.
        assertEquals(SourceFormat.EPUB, BookFormats.detect(zip("i", mapOf("mimetype" to "application/epub+zip".toByteArray(), "META-INF/container.xml" to ByteArray(1))), "i"))
        assertEquals(SourceFormat.DOCX, BookFormats.detect(zip("j", mapOf("word/document.xml" to ByteArray(1))), "j"))
        assertEquals(SourceFormat.FB2, BookFormats.detect(zip("k.fb2.zip", mapOf("book.fb2" to ByteArray(1))), "k.fb2.zip"))
        assertEquals(SourceFormat.CBZ, BookFormats.detect(zip("l", mapOf("001.jpg" to ByteArray(1), "002.jpg" to ByteArray(1))), "l"))
        assertNull(BookFormats.detect(zip("m.zip", mapOf("readme" to ByteArray(1))), "m.zip"))
    }

    @Test
    fun bookFilesAreKnownByTheirEndings() {
        for (n in listOf("a.epub", "b.kepub.epub", "c.PDF", "d.mobi", "e.azw3", "f.fb2", "g.docx", "h.odt", "i.rtf", "j.txt", "k.html", "l.md", "m.cbz")) {
            assertTrue(n, BookFormats.isBook(n))
        }
        for (n in listOf("a.jpg", "b.zip", "c", "d.exe")) assertFalse(n, BookFormats.isBook(n))
    }

    @Test
    fun aZippedFictionBookIsUnzippedFirst() {
        val fb2 = TestSamples.bytes("alice.fb2")
        assumeTrue(fb2 != null)
        val zipped = zip("alice.fbz", mapOf("alice.fb2" to fb2!!))
        val target = File(tmp.root, "out.epub")
        assertNull(BookFormats.toEpub(SourceFormat.FB2, zipped, "alice.fbz", target).kind)
        EpubFile.open(target).use { epub -> epub.opf.readMetadata().title.let { assertTrue(it, it.startsWith("Alice’s Adventures in Wonderland")) } }
    }

    @Test
    fun aPdfTooBigToConvertGetsAStandIn() {
        val target = File(tmp.root, "scan.epub")
        PdfConverter.standIn("My Old Scans.pdf", target, null)
        EpubFile.open(target).use { epub ->
            assertEquals("My Old Scans", epub.opf.readMetadata().title)
            val text = epub.contentDocuments().joinToString(" ") { EpubText.plainText(epub.readText(it).orEmpty()) }
            assertTrue(text, text.contains("reads as the PDF it came as"))
        }
    }

    @Test
    fun aPdfWithTextIsMadeIntoABook() {
        val pdf = TestSamples.file("alice-objstm.pdf")
        assumeTrue(pdf != null)
        val target = File(tmp.root, "alice.epub")
        // No drawing of pages here (Android does that): it's the text that counts.
        assertNull(BookFormats.toEpub(SourceFormat.PDF, pdf!!, "alice.pdf", target).kind)
        EpubFile.open(target).use { epub ->
            val words = epub.contentDocuments().sumOf { EpubText.countWords(EpubText.plainText(epub.readText(it).orEmpty())) }
            assertTrue("$words words", words > 20_000)
        }
        val contents = PdfConverter.contents(pdf)
        assertTrue(contents.toString(), contents.size >= 10)
        assertEquals(0, contents.first().third)
    }
}
