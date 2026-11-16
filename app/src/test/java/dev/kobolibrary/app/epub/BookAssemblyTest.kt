package dev.kobolibrary.app.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.zip.ZipFile

class BookAssemblyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun garden() = TestBooks.garden(tmp.newFile("garden.epub"))
    private fun three() = TestBooks.threeParts(tmp.newFile("three.epub"))

    /** Every file the package lists is there, the first entry is a bare mimetype, and every page is well-formed. */
    private fun assertSound(file: java.io.File) {
        ZipFile(file).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(java.util.zip.ZipEntry.STORED, first.method)
        }
        EpubFile.open(file).use { epub ->
            for (item in epub.opf.items) assertTrue("missing ${item.href}", epub.has(epub.resolve(item.href)))
            for (ref in epub.opf.spineRefs()) assertTrue(epub.opf.item(ref.idref) != null)
            for (path in epub.contentDocuments()) assertNull("$path: ${XmlCheck.check(epub.readText(path)!!, false)}", XmlCheck.check(epub.readText(path)!!, false))
        }
    }

    private fun structure(file: java.io.File): Triple<BookMetadata, BookStructure, EpubFile> {
        val epub = EpubFile.open(file)
        return Triple(epub.opf.readMetadata(), BookStructure(EpubFileFiles(epub)), epub)
    }

    @Test
    fun aNewBookIsReadyToWriteIn() {
        val out = tmp.newFile("new.epub")
        BookAssembly.create(out, BookMetadata(title = "My Story", authors = listOf("Sam Writer"), language = "en-AU"))
        assertSound(out)
        val (meta, s, epub) = structure(out)
        epub.use {
            assertEquals("My Story", meta.title)
            assertEquals(listOf("Sam Writer"), meta.authors)
            assertEquals("en-AU", meta.language)
            assertTrue(meta.identifier!!.startsWith("urn:uuid:"))
            assertEquals(listOf("Chapter 1"), s.chapters.map { it.title })
            assertEquals(listOf("Chapter 1"), s.toc.map { it.title })
            // A chapter heading, as the editor names them, under the book's own style sheet.
            val page = epub.readText(s.chapters[0].path)!!
            assertTrue(page, page.contains("<h2>Chapter 1</h2>") && page.contains("href=\"../styles/book.css\""))
            assertTrue(epub.opf.ncxItem() != null && epub.opf.navItem() != null)
        }
    }

    @Test
    fun twoBooksBecomeOneWithEachBooksContentsUnderItsTitle() {
        val out = tmp.newFile("merged.epub")
        BookAssembly.merge(out, listOf(garden(), three()), BookMetadata(title = "Garden and Parts", authors = listOf("A. Writer")))
        assertSound(out)
        val (meta, s, epub) = structure(out)
        epub.use {
            assertEquals("Garden and Parts", meta.title)
            // Every page of both, in order (each book's first page named for the book); the garden's notes still read only by link.
            assertEquals(listOf("The Garden", "Chapter 1: Rose", "Chapter 2", "Part Two", "Chapter 3", "Notes", "Three Parts", "Two", "Three"), s.chapters.map { it.title })
            assertEquals(listOf(true, true, true, true, true, false, true, true, true), s.chapters.map { it.linear })
            assertEquals(listOf("The Garden", "Three Parts"), s.toc.map { it.title })
            assertEquals(listOf("Part One", "Part Two", "Notes"), s.toc[0].children.map { it.title })
            assertEquals(listOf("One", "Middle", "Two", "Three"), s.toc[1].children.flatMap { it.flatten() }.map { it.first.title })
            // Links inside each book still lead somewhere: the chapter's style sheet and its note.
            val ch1 = s.chapters[1].path
            val text = epub.readText(ch1)!!
            for (href in listOf("../styles/style.css", "notes.xhtml")) assertTrue(href, epub.has(EpubFile.resolvePath(ch1.substringBeforeLast('/'), href)))
            assertTrue(text.contains("Rose walked into the garden"))
        }
    }

    @Test
    fun anOmnibusSplitsIntoBooksEachWithWhatItUses() {
        val src = garden()
        val pages = BookAssembly.pages(src)
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Chapter 2", "Part Two", "Chapter 3", "Notes"), pages.map { it.title })
        assertEquals(listOf(0, 1, 1, 0, 1, 0), pages.map { it.level })
        val a = tmp.newFile("a.epub")
        val b = tmp.newFile("b.epub")
        BookAssembly.split(src, listOf(3), listOf(BookMetadata(title = "The Garden, Part One"), BookMetadata(title = "The Garden, Part Two")), listOf(a, b))
        assertSound(a)
        assertSound(b)
        structure(a).let { (meta, s, epub) ->
            epub.use {
                assertEquals("The Garden, Part One", meta.title)
                // Its own pages, and the notes its first chapter links to, read only by link.
                assertEquals(listOf("Part One", "Chapter 1: Rose", "Chapter 2", "Notes"), s.chapters.map { it.title })
                assertEquals(listOf(true, true, true, false), s.chapters.map { it.linear })
                assertEquals(listOf("Part One"), s.toc.map { it.title })
                assertEquals(listOf("Chapter 1: Rose", "Chapter 2"), s.toc[0].children.map { it.title })
                assertTrue(epub.has("OEBPS/styles/style.css") && epub.has("OEBPS/fonts/fancy.ttf"))
                assertTrue(!epub.has("OEBPS/text/ch3.xhtml"))
            }
        }
        structure(b).let { (_, s, epub) ->
            epub.use {
                // A link back to a chapter in the first book doesn't drag that chapter along.
                assertEquals(listOf("Part Two", "Chapter 3", "Notes"), s.chapters.map { it.title })
                assertEquals(listOf("Part Two", "Notes"), s.toc.map { it.title })
                assertTrue(!epub.has("OEBPS/text/ch1.xhtml"))
            }
        }
    }

    /** An omnibus: a cover, a title page and a contents page, two books (each a title page and two chapters), and about the author. */
    private fun omnibus(): java.io.File {
        val names = listOf("cover", "title", "contents", "a", "a1", "a2", "b", "b1", "b2", "about")
        val titles = mapOf("title" to "Title Page", "contents" to "Contents", "a" to "Book A", "a1" to "A One", "a2" to "A Two",
            "b" to "Book B", "b1" to "B One", "b2" to "B Two", "about" to "About the Author")
        fun entry(n: String, cls: String) = """  <p class="$cls"><a href="$n.xhtml">${titles[n]}</a></p>"""
        val contents = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Contents</title></head><body>
  <h1>Contents</h1>
${listOf("a" to "tb", "a1" to "tc", "a2" to "tc", "b" to "tb", "b1" to "tc", "b2" to "tc", "about" to "tb").joinToString("\n") { (n, c) -> entry(n, c) }}
</body></html>"""
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:omni</dc:identifier><dc:title>Both Books</dc:title><dc:language>en</dc:language></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="cimg" href="images/cover.png" media-type="image/png" properties="cover-image"/>
${names.joinToString("\n") { "    <item id=\"$it\" href=\"text/$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }}
  </manifest>
  <spine>
${names.joinToString("\n") { "    <itemref idref=\"$it\"/>" }}
  </spine>
</package>"""
        fun li(n: String, kids: String = "") = "<li><a href=\"text/$n.xhtml\">${titles[n]}</a>$kids</li>"
        val nav = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body>
<nav epub:type="toc"><ol>${li("title")}${li("contents")}${li("a", "<ol>${li("a1")}${li("a2")}</ol>")}${li("b", "<ol>${li("b1")}${li("b2")}</ol>")}${li("about")}</ol></nav>
</body></html>"""
        val files = linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
            "OEBPS/images/cover.png" to ByteArray(32) { it.toByte() },
            "OEBPS/text/cover.xhtml" to """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Cover</title></head><body><div><img src="../images/cover.png" alt="Cover"/></div></body></html>""".toByteArray(),
            "OEBPS/text/contents.xhtml" to contents.toByteArray(),
        )
        for (n in names.drop(1)) if (n != "contents") files["OEBPS/text/$n.xhtml"] = TestBooks.simplePage(titles.getValue(n), "Words of ${titles[n]}.").toByteArray()
        return TestEpubs.build(tmp.newFile("omnibus.epub"), files)
    }

    @Test
    fun everyBookCanGetTheOmnibussOpeningAndClosingPages() {
        val src = omnibus()
        val pages = BookAssembly.pages(src)
        assertTrue(pages[0].cover)
        val cut = pages.indexOfFirst { it.title == "Book B" }
        // Before the first book (not its cover): the title page and contents; after the last: about the author.
        val shared = BookAssembly.sharedPages(pages, listOf(cut))
        assertEquals(listOf("Title Page", "Contents"), shared.front.map { pages[it].title })
        assertEquals(listOf("About the Author"), shared.back.map { pages[it].title })

        val outs = listOf(tmp.newFile("a.epub"), tmp.newFile("b.epub"))
        BookAssembly.split(src, listOf(cut), listOf(BookMetadata(title = "A"), BookMetadata(title = "B")), outs, shared)
        outs.forEach { assertSound(it) }
        val (_, first, e1) = structure(outs[0])
        val (_, second, e2) = structure(outs[1])
        e1.use {
            e2.use {
                assertEquals(listOf("cover", "title", "contents", "a", "a1", "a2", "about"), first.chapters.map { it.path.substringAfterLast('/').substringBefore('.') })
                assertEquals(listOf("title", "contents", "b", "b1", "b2", "about"), second.chapters.map { it.path.substringAfterLast('/').substringBefore('.') })
                assertEquals(listOf("Title Page", "Contents", "Book B", "About the Author"), second.toc.map { it.title })
                // Each book's contents page lists its own chapters, in the page's own style.
                val page2 = e2.readText(second.chapters[1].path)!!
                assertTrue(page2, page2.contains("""<p class="tb"><a href="b.xhtml">Book B</a></p>"""))
                assertTrue(page2, page2.contains("""<p class="tc"><a href="b1.xhtml">B One</a></p>"""))
                assertTrue(page2, page2.contains("""<p class="tb"><a href="about.xhtml">About the Author</a></p>"""))
                assertFalse(page2.contains("Book A") || page2.contains("A One"))
                val page1 = e1.readText(first.chapters[2].path)!!
                assertTrue(page1, page1.contains("""<p class="tc"><a href="a2.xhtml">A Two</a></p>"""))
                assertFalse(page1.contains("B One"))
            }
        }
        // Where the contents don't say which are books, the pages named as an omnibus's opening and closing ones.
        val flat = pages.map { it.copy(level = 0) }
        val named = BookAssembly.sharedPages(flat, listOf(cut))
        assertEquals(listOf("Title Page", "Contents"), named.front.map { pages[it].title })
        assertEquals(listOf("About the Author"), named.back.map { pages[it].title })
        // To choose from: the first book's opening pages and the last book's closing ones.
        val choices = BookAssembly.sharedCandidates(pages, listOf(cut))
        assertEquals((0 until cut).toList(), choices.front)
        assertEquals(listOf("B One", "B Two", "About the Author"), choices.back.map { pages[it].title })
        // Without: as before.
        BookAssembly.split(src, listOf(cut), listOf(BookMetadata(title = "A"), BookMetadata(title = "B")), outs)
        structure(outs[1]).third.use { e -> assertEquals(listOf("b", "b1", "b2", "about"), BookStructure(EpubFileFiles(e)).chapters.map { it.path.substringAfterLast('/').substringBefore('.') }) }
    }

    @Test
    fun scrambledFontsAreUnscrambledForTheirNewBook() {
        val font = ByteArray(2000) { (it * 7).toByte() }
        val key = MessageDigest.getInstance("SHA-1").digest("urn:uuid:1234".toByteArray())
        val scrambled = font.copyOf().also { for (i in 0 until 1040) it[i] = (it[i].toInt() xor key[i % key.size].toInt()).toByte() }
        val encryption = """<?xml version="1.0"?>
<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
  <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
    <enc:CipherData><enc:CipherReference URI="OEBPS/fonts/fancy.ttf"/></enc:CipherData></enc:EncryptedData>
</encryption>"""
        val src = TestEpubs.build(
            tmp.newFile("scrambled.epub"),
            linkedMapOf(
                "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
                "META-INF/encryption.xml" to encryption.toByteArray(),
                "OEBPS/content.opf" to TestBooks.OPF.toByteArray(),
                "OEBPS/toc.ncx" to TestBooks.NCX.toByteArray(),
                "OEBPS/styles/style.css" to TestBooks.CSS.toByteArray(),
                "OEBPS/fonts/fancy.ttf" to scrambled,
                "OEBPS/text/part1.xhtml" to TestBooks.PART1.toByteArray(),
                "OEBPS/text/ch1.xhtml" to TestBooks.CH1.toByteArray(),
                "OEBPS/text/ch2.xhtml" to TestBooks.CH2.toByteArray(),
                "OEBPS/text/part2.xhtml" to TestBooks.PART2.toByteArray(),
                "OEBPS/text/ch3.xhtml" to TestBooks.CH3.toByteArray(),
                "OEBPS/text/notes.xhtml" to TestBooks.NOTES.toByteArray(),
            ),
        )
        val out = tmp.newFile("merged.epub")
        BookAssembly.merge(out, listOf(src, three()), BookMetadata(title = "Both"))
        EpubFile.open(out).use { epub ->
            val path = epub.opf.items.map { epub.resolve(it.href) }.first { it.endsWith("fancy.ttf") }
            assertArrayEquals(font, epub.readBytes(path))
            assertTrue(!epub.has("META-INF/encryption.xml"))
        }
    }

    @Test
    fun aCopyProtectedBookIsLeftAlone() {
        val encryption = """<?xml version="1.0"?>
<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
  <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
    <enc:CipherData><enc:CipherReference URI="OEBPS/text/ch1.xhtml"/></enc:CipherData></enc:EncryptedData>
</encryption>"""
        val src = tmp.newFile("drm.epub")
        TestEpubs.build(src, linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "META-INF/encryption.xml" to encryption.toByteArray(),
            "OEBPS/content.opf" to TestBooks.NAV_OPF.toByteArray(),
            "OEBPS/nav.xhtml" to TestBooks.NAV.toByteArray(),
            "OEBPS/text/one.xhtml" to TestBooks.simplePage("One", "x").toByteArray(),
            "OEBPS/text/two.xhtml" to TestBooks.simplePage("Two", "x").toByteArray(),
            "OEBPS/text/three.xhtml" to TestBooks.simplePage("Three", "x").toByteArray(),
        ))
        try {
            BookAssembly.merge(tmp.newFile("x.epub"), listOf(src, garden()), BookMetadata(title = "X"))
            fail("merged a protected book")
        } catch (e: AssemblyException) {
            assertTrue(e.message!!.contains("copy-protected"))
        }
    }

    @Test
    fun omnibusSplitsAreSuggestedAtItsBooks() {
        val pages = listOf(
            BookAssembly.Page(0, "a", "Cover", true, 5, 0),
            BookAssembly.Page(1, "b", "Contents", true, 50, 0),
            BookAssembly.Page(2, "c", "Book One", true, 20, 0),
            BookAssembly.Page(3, "d", "Chapter 1", true, 6000, 1),
            BookAssembly.Page(4, "e", "Chapter 2", true, 6000, 1),
            BookAssembly.Page(5, "f", "Book Two", true, 20, 0),
            BookAssembly.Page(6, "g", "Chapter 1", true, 6000, 1),
            BookAssembly.Page(7, "h", "Afterword", true, 900, 0),
        )
        // Not before Book One (what comes before the first book goes with it), nor at a short afterword.
        assertEquals(listOf(5), BookAssembly.suggestedCuts(pages))
        // Each book is named for the top-level entry that covers most of it: not the cover, not the afterword.
        assertEquals(listOf("Book One", "Book Two"), BookAssembly.partTitles(pages, listOf(5), "Omnibus"))
    }

    @Test
    fun anIntroductionGoesWithTheFirstBook() {
        val pages = listOf(
            BookAssembly.Page(0, "a", "Cover", true, 2, 0),
            BookAssembly.Page(1, "b", "Introduction", true, 5200, 0),
            BookAssembly.Page(2, "c", "Book I", true, 12, 0),
            BookAssembly.Page(3, "d", "Part 1", true, 9000, 1),
            BookAssembly.Page(4, "e", "Book II", true, 12, 0),
            BookAssembly.Page(5, "f", "Part 1", true, 9000, 1),
        )
        val cuts = BookAssembly.suggestedCuts(pages)
        assertEquals(listOf(4), cuts)
        assertEquals(listOf("Book I", "Book II"), BookAssembly.partTitles(pages, cuts, "Histories"))
    }

    @Test
    fun aNovelCutBetweenChaptersIsNumberedInParts() {
        val novel = (0 until 10).map { BookAssembly.Page(it, "p$it", "Chapter ${it + 1}", true, 4000, 0) }
        // Its contents are one flat list, so no chapter looks like the start of a book.
        assertEquals(emptyList<Int>(), BookAssembly.suggestedCuts(novel))
        assertEquals(listOf("Novel (part 1)", "Novel (part 2)"), BookAssembly.partTitles(novel, listOf(5), "Novel"))
    }

    @Test
    fun pagesAreCountedJustAsTheSplitCountsThem() {
        // A chapter the package lists but the file lacks: the cut chosen at "Part Two" must land there.
        val src = garden()
        val broken = tmp.newFile("broken.epub")
        ZipFile(src).use { zin ->
            java.util.zip.ZipOutputStream(broken.outputStream()).use { out ->
                for (e in zin.entries()) {
                    if (e.name == "OEBPS/text/ch2.xhtml") continue
                    out.putNextEntry(java.util.zip.ZipEntry(e.name))
                    zin.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        val pages = BookAssembly.pages(broken)
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Part Two", "Chapter 3", "Notes"), pages.map { it.title })
        val cut = pages.first { it.title == "Part Two" }.index
        val a = tmp.newFile("a.epub")
        val b = tmp.newFile("b.epub")
        BookAssembly.split(broken, listOf(cut), listOf(BookMetadata(title = "A"), BookMetadata(title = "B")), listOf(a, b))
        structure(b).let { (_, s, epub) -> epub.use { assertEquals(listOf("Part Two", "Chapter 3", "Notes"), s.chapters.map { it.title }) } }
        structure(a).let { (_, s, epub) -> epub.use { assertEquals(listOf("Part One", "Chapter 1: Rose", "Notes"), s.chapters.map { it.title }) } }
    }
}
