package dev.kobolibrary.app.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A section copied out of one book (with its pictures, styles and fonts) and put into another. */
class CopyPasteSectionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun garden() = BookEdit(TestBooks.garden(tmp.newFile("garden.epub")))
    private var made = 0
    private fun three() = BookEdit(TestBooks.threeParts(tmp.newFile("three-${++made}.epub")))

    private fun BookEdit.paste(after: String?, page: ChapterOps.CopiedPage, relist: Boolean = false): ChapterOps.Result {
        val r = ChapterOps.paste(this, structure, after, page, relist)
        assertTrue(apply(r.label, r.changes))
        return r
    }

    @Test
    fun aChapterGoesIntoAnotherBookWithItsStylesAndFont() {
        val page = garden().use { g -> ChapterOps.copyPage(g, g.structure, "OEBPS/text/ch1.xhtml") }
        assertEquals("Chapter 1: Rose", page.title)
        assertEquals(listOf("OEBPS/styles/style.css", "OEBPS/fonts/fancy.ttf"), page.files.map { it.path })
        assertFalse(page.isContents)

        three().use { e ->
            val r = e.paste("OEBPS/text/one.xhtml", page)
            assertEquals("OEBPS/text/ch1.xhtml", r.focus)
            // After the chapter it was added after, in the reading order and the contents.
            assertEquals(listOf("OEBPS/text/one.xhtml", "OEBPS/text/ch1.xhtml", "OEBPS/text/two.xhtml", "OEBPS/text/three.xhtml"),
                e.structure.chapters.map { it.path })
            assertEquals(listOf("One", "Chapter 1: Rose", "Middle"), e.structure.toc.map { it.title })
            // Its style sheet and font, where a book keeps them, the links between them written for there.
            val text = e.text("OEBPS/text/ch1.xhtml")!!
            assertTrue(text, text.contains("""href="../styles/style.css""""))
            assertTrue(e.text("OEBPS/styles/style.css")!!.contains("url(../fonts/fancy.ttf)"))
            assertArrayEquals(ByteArray(64) { it.toByte() }, e.bytes("OEBPS/fonts/fancy.ttf"))
            assertTrue(e.structure.opf.items.any { e.structure.pathOf(it) == "OEBPS/fonts/fancy.ttf" })
            // Its link to a note this book hasn't got is taken out; its words, and its own anchor, stay.
            assertFalse(text.contains("notes.xhtml"))
            assertTrue(text, text.contains("""<span id="r1">1</span>"""))
            assertTrue(BookCheck.run(e).let { it.isEmpty() || it.all { p -> !p.toString().contains("ch1") } })
        }
    }

    @Test
    fun aChapterIsDuplicatedStraightAfterItself() {
        garden().use { g ->
            val before = g.structure.chapters.map { it.path }
            val items = g.structure.opf.items.size
            val original = g.text("OEBPS/text/ch1.xhtml")!!
            val r = ChapterOps.duplicate(g, g.structure, "OEBPS/text/ch1.xhtml")
            assertTrue(g.apply(r.label, r.changes))
            assertEquals("Duplicated “Chapter 1: Rose”", r.label)
            assertEquals("OEBPS/text/ch1-2.xhtml", r.focus)
            // Straight after the chapter it's a copy of, in the reading order and the contents.
            val at = before.indexOf("OEBPS/text/ch1.xhtml")
            assertEquals(before.take(at + 1) + "OEBPS/text/ch1-2.xhtml" + before.drop(at + 1), g.structure.chapters.map { it.path })
            val titles = Contents.flatten(g.structure.toc).map { it.first.title }
            assertEquals("Chapter 1: Rose (copy)", titles[titles.indexOf("Chapter 1: Rose") + 1])
            // Only the page is new (its style sheet and font are the book's own), and its words are the same.
            assertEquals(items + 1, g.structure.opf.items.size)
            assertEquals(original, g.text("OEBPS/text/ch1-2.xhtml")!!.replace("ch1-2.xhtml", "ch1.xhtml"))
            assertEquals(original, g.text("OEBPS/text/ch1.xhtml"))
            val problems = BookCheck.run(g).map { it.toString() }
            assertTrue(problems.toString(), problems.none { "ch1-2" in it })
            // One step to undo.
            g.undo()
            assertEquals(before, g.structure.chapters.map { it.path })
            assertEquals(items, g.structure.opf.items.size)
        }
    }

    @Test
    fun aFileTheBookHasAlreadyIsUsedAsItIs() {
        garden().use { g ->
            val page = ChapterOps.copyPage(g, g.structure, "OEBPS/text/ch2.xhtml")
            val before = g.structure.opf.items.size
            val r = g.paste("OEBPS/text/ch3.xhtml", page)
            assertEquals("OEBPS/text/ch2-2.xhtml", r.focus)
            // Only the page is new: the same style sheet and font are there already.
            assertEquals(before + 1, g.structure.opf.items.size)
            assertFalse(r.changes.keys.any { it.endsWith(".css") || it.endsWith(".ttf") })
            assertTrue(g.text("OEBPS/text/ch2-2.xhtml")!!.contains("""href="../styles/style.css""""))
            assertEquals("OEBPS/text/ch2-2.xhtml", g.structure.chapters[g.structure.chapters.indexOfFirst { it.path == "OEBPS/text/ch3.xhtml" } + 1].path)
        }
    }

    @Test
    fun aContentsPageCanListTheNewBooksChapters() {
        val contents = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Contents</title><link rel="stylesheet" type="text/css" href="../styles/style.css"/></head>
<body>
  <h1 class="toc">Contents</h1>
  <p class="toc-part"><a href="part1.xhtml">Part One</a></p>
  <p class="toc-ch"><a href="ch1.xhtml">Chapter 1</a></p>
  <p class="toc-ch"><a href="ch2.xhtml">Chapter 2</a></p>
  <p class="toc-part"><a href="part2.xhtml">Part Two</a></p>
  <p class="toc-ch"><a href="ch3.xhtml">Chapter 3</a></p>
</body></html>"""
        // The garden book with a contents page of its own, before its first part.
        val file = TestEpubs.build(tmp.newFile("with-contents.epub"), linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to TestBooks.OPF
                .replace("""<item id="part1"""", """<item id="contents" href="text/contents.xhtml" media-type="application/xhtml+xml"/>
    <item id="part1"""")
                .replace("""<itemref idref="part1"""", """<itemref idref="contents"/>
    <itemref idref="part1"""").toByteArray(),
            "OEBPS/toc.ncx" to TestBooks.NCX.toByteArray(),
            "OEBPS/styles/style.css" to TestBooks.CSS.toByteArray(),
            "OEBPS/fonts/fancy.ttf" to ByteArray(64) { it.toByte() },
            "OEBPS/text/contents.xhtml" to contents.toByteArray(),
            "OEBPS/text/part1.xhtml" to TestBooks.PART1.toByteArray(),
            "OEBPS/text/ch1.xhtml" to TestBooks.CH1.toByteArray(),
            "OEBPS/text/ch2.xhtml" to TestBooks.CH2.toByteArray(),
            "OEBPS/text/part2.xhtml" to TestBooks.PART2.toByteArray(),
            "OEBPS/text/ch3.xhtml" to TestBooks.CH3.toByteArray(),
            "OEBPS/text/notes.xhtml" to TestBooks.NOTES.toByteArray(),
        ))
        val page = BookEdit(file).use { b ->
            assertTrue(b.structure.chapters.any { it.path == "OEBPS/text/contents.xhtml" })
            ChapterOps.copyPage(b, b.structure, "OEBPS/text/contents.xhtml")
        }
        assertTrue(page.isContents)
        assertEquals(0, page.levels["OEBPS/text/part1.xhtml"])
        assertEquals(1, page.levels["OEBPS/text/ch1.xhtml"])

        three().use { e ->
            e.paste(null, page, relist = true)
            val text = e.text("OEBPS/text/contents.xhtml")!!
            org.junit.Assert.assertNull(XmlCheck.check(text, false))
            // This book's chapters, styled as the page styled its own: the top ones as parts, those under them as chapters.
            assertTrue(text, text.contains("""<p class="toc-part"><a href="one.xhtml">One</a></p>"""))
            assertTrue(text, text.contains("""<p class="toc-ch"><a href="two.xhtml">Two</a></p>"""))
            assertTrue(text, text.contains("""<p class="toc-ch"><a href="three.xhtml#t">Three</a></p>"""))
            assertFalse(text.contains("Part One") || text.contains("Chapter 1"))
            assertTrue(text.contains("""<h1 class="toc">Contents</h1>"""))
        }
        // Or as it is: links only to chapters the book has, the rest left as words.
        three().use { e ->
            e.paste(null, page, relist = false)
            val text = e.text("OEBPS/text/contents.xhtml")!!
            assertFalse(text.contains("<a href"))
            assertTrue(text.contains("Chapter 2"))
        }
    }
}
