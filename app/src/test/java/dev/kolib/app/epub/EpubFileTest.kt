package dev.kolib.app.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun findsCoversThreeWays() {
        EpubFile.open(TestEpubs.calibreEpub2(tmp.newFile("a.epub"))).use {
            assertEquals(EpubFile.Cover("OEBPS/cover.jpeg", "image/jpeg", declared = true), it.findCover())
        }
        EpubFile.open(TestEpubs.epub3(tmp.newFile("b.epub"))).use {
            assertEquals(EpubFile.Cover("OEBPS/images/cover.png", "image/png", declared = true), it.findCover())
        }
        // No declaration: found through the cover page named in the guide.
        val undeclared = TestEpubs.CALIBRE_EPUB2_OPF.replace("<meta name=\"cover\" content=\"cover\"/>", "")
            .replace("id=\"cover\"", "id=\"img1\"")
        val f = TestEpubs.build(tmp.newFile("c.epub"), mapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to undeclared.toByteArray(),
            "OEBPS/cover.jpeg" to TestEpubs.fakeJpeg(3),
            "OEBPS/titlepage.xhtml" to TestEpubs.SVG_TITLEPAGE.toByteArray(),
        ))
        EpubFile.open(f).use { assertEquals(EpubFile.Cover("OEBPS/cover.jpeg", "image/jpeg", declared = false), it.findCover()) }
    }

    @Test
    fun refusesThingsThatAreNotEpubs() {
        val notZip = tmp.newFile("x.epub").apply { writeText("hello") }
        assertEquals("This file isn't an EPUB (it isn't a zip archive).", runCatching { EpubFile.open(notZip) }.exceptionOrNull()?.message)
        val noContainer = TestEpubs.build(tmp.newFile("y.epub"), mapOf("hello.txt" to "hi".toByteArray()))
        assertTrue(runCatching { EpubFile.open(noContainer) }.exceptionOrNull()?.message!!.contains("container.xml"))
    }

    @Test
    fun rewritingKeepsTheZipValidAndUntouchedEntriesIdentical() {
        val file = TestEpubs.calibreEpub2(tmp.newFile("book.epub"), extra = mapOf("OEBPS/big.jpg" to ByteArray(300_000) { (it % 251).toByte() }))
        val before = ZipFile(file).use { z -> z.entries().asSequence().associate { it.name to z.getInputStream(it).readBytes() } }

        EpubEditor.writeMetadata(file, BookMetadata(title = "Changed", authors = listOf("Someone Else"), language = "en"))

        ZipFile(file).use { z ->
            val entries = z.entries().toList()
            val first = entries.first()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertTrue(first.extra == null || first.extra.isEmpty())
            assertEquals(before.keys.toList(), entries.map { it.name })
            for (e in entries) {
                if (e.name == "OEBPS/content.opf") continue
                assertArrayEquals(e.name, before.getValue(e.name), z.getInputStream(e).readBytes())
            }
            assertEquals(ZipEntry.STORED, z.getEntry("OEBPS/big.jpg").method)
        }
        EpubFile.open(file).use {
            val m = it.opf.readMetadata()
            assertEquals("Changed", m.title)
            assertEquals(listOf("Someone Else"), m.authors)
        }
        assertFalse(tmp.root.listFiles()!!.any { it.name.endsWith(".saving") })
    }

    @Test
    fun replacesASameTypeCoverInPlaceAndResizesTheSvgPage() {
        val file = TestEpubs.calibreEpub2(tmp.newFile("book.epub"))
        val newJpeg = TestEpubs.fakeJpeg(9)
        EpubEditor.replaceCover(file, newJpeg, "image/jpeg", 1072, 1448)
        EpubFile.open(file).use {
            assertArrayEquals(newJpeg, it.readBytes("OEBPS/cover.jpeg"))
            assertEquals(EpubFile.Cover("OEBPS/cover.jpeg", "image/jpeg", declared = true), it.findCover())
            val page = it.readText("OEBPS/titlepage.xhtml")!!
            assertTrue(page.contains("viewBox=\"0 0 1072 1448\""))
            assertTrue(page.contains("<image width=\"1072\" height=\"1448\" xlink:href=\"cover.jpeg\"/>"))
        }
    }

    @Test
    fun addsAJpegCoverToAPngBook() {
        val file = TestEpubs.epub3(tmp.newFile("book.epub"))
        val jpeg = TestEpubs.fakeJpeg(5)
        EpubEditor.replaceCover(file, jpeg, "image/jpeg", 1072, 1448)
        EpubFile.open(file).use {
            val cover = it.findCover()
            assertNotNull(cover)
            assertEquals("OEBPS/cover-kl.jpg", cover!!.path)
            assertArrayEquals(jpeg, it.readBytes(cover.path))
            val opf = it.readText(it.opfPath)!!
            // Only the new image carries cover-image; the old one lost it.
            assertEquals(1, Regex("properties=\"cover-image\"").findAll(opf).count())
            assertTrue(opf.contains("<item id=\"img\" href=\"images/cover.png\" media-type=\"image/png\"/>"))
            assertTrue(opf.contains("href=\"cover-kl.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>"))
            assertTrue(opf.contains("<meta name=\"cover\" content=\"cover-image\"/>"))
        }
    }

    @Test
    fun resolvesPaths() {
        assertEquals("OEBPS/Images/a b.jpg", EpubFile.resolvePath("OEBPS/Text", "../Images/a%20b.jpg"))
        assertEquals("cover.jpg", EpubFile.resolvePath("", "./cover.jpg"))
        assertEquals("../Images/c.jpg", EpubFile.relativeHref("OEBPS/Text/p.xhtml", "OEBPS/Images/c.jpg"))
        assertEquals("cover-kl.jpg", EpubFile.relativeHref("OEBPS/content.opf", "OEBPS/cover-kl.jpg"))
        assertEquals("images/x.png", EpubFile.firstImageReference("<p><img alt='' src=\"images/x.png\"/></p><img src='y.png'/>"))
    }
}
