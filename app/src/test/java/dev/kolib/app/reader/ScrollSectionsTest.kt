package dev.kolib.app.reader

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.ChapterOpenings
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.TestEpubs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.util.UUID

/** Scrolling, the pages before a book's chapters read as one section, and those after them as another. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrollSectionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun settle(done: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(600) {
            if (done()) return
            looper.idleFor(Duration.ofMillis(100))
            Thread.sleep(2)
        }
        fail("The reader never got there")
    }

    private fun ReaderController.index(name: String) = chapters.indexOfFirst { it.path.endsWith(name) }

    /** Enough of a PNG for its size to be read. */
    private fun png(w: Int, h: Int) = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13,
        'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
        (w shr 24).toByte(), (w shr 16).toByte(), (w shr 8).toByte(), w.toByte(),
        (h shr 24).toByte(), (h shr 16).toByte(), (h shr 8).toByte(), h.toByte(), 8, 2, 0, 0, 0, 0, 0, 0, 0,
    )

    private fun book(): File {
        val words = (1..400).joinToString(" ") { "word$it" }
        // The cover in a folder of its own, as some books have it: its picture still shows from the section's page.
        val pages = linkedMapOf(
            "cover" to ("OEBPS/cover.xhtml" to """<div><img src="images/cover.png" alt="Cover"/></div>"""),
            "title" to ("OEBPS/text/title.xhtml" to "<h1>The Sea</h1><p>A novel</p>"),
            "copyright" to ("OEBPS/text/copyright.xhtml" to "<p>Copyright 2026. All rights reserved.</p>"),
            "ch1" to ("OEBPS/text/ch1.xhtml" to "<h1>Chapter One</h1><p>$words</p>"),
            "ch2" to ("OEBPS/text/ch2.xhtml" to "<h1>Chapter Two</h1><p>$words</p>"),
            "thanks" to ("OEBPS/text/thanks.xhtml" to "<h1>Acknowledgements</h1><p>Thanks to everyone.</p>"),
            "about" to ("OEBPS/text/about.xhtml" to "<h1>About the Author</h1><p>The author lives by the sea.</p>"),
        )
        val titles = mapOf(
            "cover" to "Cover", "title" to "Title Page", "copyright" to "Copyright", "ch1" to "Chapter One", "ch2" to "Chapter Two",
            "thanks" to "Acknowledgements", "about" to "About the Author",
        )
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:7</dc:identifier><dc:title>The Sea</dc:title><dc:language>en</dc:language></metadata>
  <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="img" href="images/cover.png" media-type="image/png" properties="cover-image"/>
${pages.entries.joinToString("\n") { (id, p) -> """<item id="$id" href="${p.first.removePrefix("OEBPS/")}" media-type="application/xhtml+xml"/>""" }}</manifest>
  <spine>${pages.keys.joinToString("") { """<itemref idref="$it"/>""" }}</spine>
</package>"""
        val nav = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc"><ol>${pages.entries.joinToString("") { (id, p) -> """<li><a href="${p.first.removePrefix("OEBPS/")}">${titles[id]}</a></li>""" }}</ol></nav></body></html>"""
        val entries = linkedMapOf<String, ByteArray>(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
            "OEBPS/images/cover.png" to png(1200, 1800),
        )
        for ((id, p) in pages) entries[p.first] = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${titles[id]}</title></head><body>${p.second}</body></html>""".toByteArray()
        return TestEpubs.build(File(context.cacheDir, "sea-${UUID.randomUUID()}.epub"), entries)
    }

    @Test
    fun scrollingReadsThePagesRoundTheChaptersAsOneSectionEach() {
        EpubFile.open(book()).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "The Sea", "en", null, ReaderSettings(flow = ReaderFlow.SCROLL), context.assets)
            settle { c.lengthsCounted }
            val at = { name: String -> c.index("$name.xhtml") }

            // Opened at the start: the front pages as one, shown as the last of them with the rest in front of it.
            c.open(at("cover"), JSONObject().put("type", "start"))
            assertEquals(at("copyright"), c.chapterIndex)
            val front = c.servedText(c.chapters[at("copyright")].path)!!
            val box = front.indexOf("id=\"${ChapterOpenings.SECTION_BOX}\"")
            assertTrue(front, box in 0 until front.indexOf("<body"))
            // The cover first, filling the first screen, its picture linked from this page's folder; then the title page.
            assertTrue(front, front.indexOf("kl-cover-part") in box until front.indexOf("A novel"))
            assertTrue(front, front.contains("src=\"../images/cover.png\""))
            assertTrue(front.indexOf("A novel") < front.indexOf("Copyright 2026"))

            // A page of the section from the contents: the section, at that page.
            c.open(at("title"), JSONObject().put("type", "start"))
            assertEquals(at("copyright"), c.chapterIndex)
            assertEquals(ChapterOpenings.SECTION_PART + at("title"), JSONObject(c.startState).getJSONObject("target").optString("value"))

            // On into the chapters, each as it is; then the back pages, as one.
            c.nextChapter()
            assertEquals(at("ch1"), c.chapterIndex)
            assertFalse(c.servedText(c.chapters[at("ch1")].path)!!.contains(ChapterOpenings.SECTION_BOX))
            c.nextChapter()
            assertEquals(at("ch2"), c.chapterIndex)
            c.nextChapter()
            assertEquals(at("about"), c.chapterIndex)
            val back = c.servedText(c.chapters[at("about")].path)!!
            assertTrue(back, back.indexOf("Thanks to everyone.") in 0 until back.indexOf("<body"))
            // Back from them: the last chapter.
            c.previousChapter()
            assertEquals(at("ch2"), c.chapterIndex)

            // Turning pages, the pages round the chapters stay each on their own.
            val paged = ReaderController(files, BookStructure(files), "The Sea", "en", null, ReaderSettings(), context.assets)
            settle { paged.lengthsCounted }
            assertFalse(paged.servedText(paged.chapters[at("copyright")].path)!!.contains(ChapterOpenings.SECTION_BOX))
        }
    }

    @Test
    fun linksAreMadeToWorkFromAnotherFolder() {
        assertEquals("../images/c.png", ChapterOpenings.relinked("images/c.png", "OEBPS", "OEBPS/text"))
        assertEquals("Images/c.jpg", ChapterOpenings.relinked("../Images/c.jpg", "OEBPS/Text", "OEBPS"))
        assertEquals("../../cover.xhtml#x", ChapterOpenings.relinked("cover.xhtml#x", "", "OEBPS/Text"))
        assertEquals("#note", ChapterOpenings.relinked("#note", "a", "b"))
        assertEquals("https://example.com/x", ChapterOpenings.relinked("https://example.com/x", "a", "b"))
        assertEquals("ch1.xhtml", ChapterOpenings.relinked("ch1.xhtml", "OEBPS/text", "OEBPS/text"))
    }
}
