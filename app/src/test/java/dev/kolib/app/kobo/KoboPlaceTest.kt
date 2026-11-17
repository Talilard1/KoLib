package dev.kolib.app.kobo

import dev.kolib.app.epub.KepubConverter
import dev.kolib.app.epub.TestEpubs
import dev.kolib.app.epub.Xml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KoboPlaceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun readsTheKobosPlaceInEachForm() {
        // A kepub sent by USB: the chapter's path in the book, and the sentence.
        assertEquals(
            KoboPlace("OEBPS/xhtml/chapter6.xhtml", span = "kobo.2.1"),
            KoboPlaces.parse("OEBPS/xhtml/chapter6.xhtml#kobo.2.1", null, null),
        )
        // A store book: its id, the package's folder and the chapter, split by "!".
        assertEquals(
            KoboPlace("OEBPS/0_cover.xhtml", span = "kobo.1.1"),
            KoboPlaces.parse("20713488-3bf9-4649-a771-9b8c63b1b77e!OEBPS!0_cover.xhtml#kobo.1.1", null, null),
        )
        // An EPUB the Kobo reads with Adobe's renderer: the spot is in adobe_location.
        assertEquals(
            KoboPlace("OEBPS/ch1.xhtml", point = "/1/4/2/1:5"),
            KoboPlaces.parse("OEBPS/ch1.xhtml", "OEBPS/ch1.xhtml#point(/1/4/2/1:5)", null),
        )
        assertEquals("/1/4/2/1:5", KoboPlaces.parse("OEBPS/ch1.xhtml", "/1/4/2/1:5", null)?.point)
        // Spaces written as %20, and how far through the chapter, from the database.
        assertEquals(
            KoboPlace("OEBPS/Text/Chapter 3.xhtml", span = "kobo.4.2", chapterProgress = 0.5f),
            KoboPlaces.parse("OEBPS/Text/Chapter%203.xhtml#kobo.4.2", null, 0.5),
        )
        assertNull(KoboPlaces.parse(null, null, null))
        assertNull(KoboPlaces.parse("", "", 0.3))
    }

    @Test
    fun findsTheSentenceTheKoboIsAtInTheLibrarysCopy() {
        val epub = book()
        // The Kobo's kepub numbers its sentences as KoLib does when it sends the book: find this one's number.
        val span = spanIdOf(CH2, "The tide came in again.")
        val found = KoboPlaces.find(epub, kobo("OEBPS/Text/ch2.xhtml#$span", percent = 40))!!
        assertEquals(KoboPlaces.Match.EXACT, found.match)
        assertEquals("OEBPS/Text/ch2.xhtml", found.position.path)
        assertTrue(found.position.snippet, found.position.snippet.startsWith("The tide came in again."))
        // The anchor counts the letters before it, spaces left out, as reader.js does.
        val before = ("Two" + "The sea was grey." + "Gulls cried over it.").count { ChapterLetters.counts(it) }
        assertEquals("chars=$before", found.position.anchor)
        assertEquals(0.4f, found.position.bookProgress, 0.0001f)
        assertEquals("Two", found.position.chapterTitle)
        assertTrue(found.position.progress > 0f && found.position.progress < 1f)
    }

    @Test
    fun findsAnAdobePoint() {
        val epub = book()
        // html, body, the div, its second paragraph, the text in it, 7 characters in ("Second ").
        val found = KoboPlaces.find(epub, kobo("OEBPS/Text/ch3.xhtml", adobe = "OEBPS/Text/ch3.xhtml#point(/1/4/2/4/1:7)"))!!
        assertEquals(KoboPlaces.Match.EXACT, found.match)
        assertEquals("OEBPS/Text/ch3.xhtml", found.position.path)
        assertEquals("chars=${"Hellothere.Second".length}", found.position.anchor)
        assertTrue(found.position.snippet, found.position.snippet.startsWith("one here."))
    }

    @Test
    fun withoutTheSentenceItGoesByHowFarThroughTheChapterThenTheBook() {
        val epub = book()
        // A sentence the library's copy doesn't have (it was edited since): the chapter, half way.
        val half = KoboPlaces.find(epub, kobo("OEBPS/Text/ch2.xhtml#kobo.99.1", chapterProgress = 0.5))!!
        assertEquals(KoboPlaces.Match.CHAPTER, half.match)
        assertEquals("OEBPS/Text/ch2.xhtml", half.position.path)
        assertEquals(0.5f, half.position.progress, 0.0001f)
        val letters = ChapterLetters(CH2).total
        assertEquals("chars=${letters / 2}", half.position.anchor)

        // A chapter that isn't in the book: only how far through it, by the letters in each chapter.
        val rough = KoboPlaces.find(epub, kobo("OEBPS/Text/gone.xhtml#kobo.1.1", percent = 99))!!
        assertEquals(KoboPlaces.Match.ROUGH, rough.match)
        assertEquals("OEBPS/Text/ch3.xhtml", rough.position.path)
        assertEquals(0.99f, rough.position.bookProgress, 0.0001f)

        // Nothing at all: the Kobo hasn't started it.
        assertNull(KoboPlaces.find(epub, kobo(null)))
    }

    @Test
    fun aChapterPathFromTheBooksPackageFolderStillMatches() {
        val epub = book()
        // Some books' places are written from the package's folder rather than the book's.
        val span = spanIdOf(CH2, "Gulls cried over it.")
        val found = KoboPlaces.find(epub, kobo("Text/ch2.xhtml#$span"))!!
        assertEquals("OEBPS/Text/ch2.xhtml", found.position.path)
        assertTrue(found.position.snippet.startsWith("Gulls cried over it."))
    }

    @Test
    fun countsHowFarThroughTheBookAsTheKoboDoes() {
        val chapters = dev.kolib.app.epub.BookStructure(dev.kolib.app.epub.EpubFileFiles(dev.kolib.app.epub.EpubFile.open(book()))).chapters
        val weights = FloatArray(chapters.size) { 100f }
        val id = "file:///mnt/onboard/Books/Sea.kepub.epub"
        // A sideloaded kepub's rows, a store book's, and an EPUB's (read with Adobe's renderer) name the files differently.
        for (form in listOf("$id!!OEBPS/Text/%s.xhtml", "0N3773Z7HFPXB!OEBPS!Text/%s.xhtml-1", "$id#(2)OEBPS/Text/%s.xhtml")) {
            val spans = listOf(KoboChapterSpan(form.format("ch1"), null, 0.0, 20.0), KoboChapterSpan(form.format("ch2"), null, 20.0, 50.0),
                KoboChapterSpan(form.format("ch3"), null, 70.0, 30.0))
            val shares = KoboPlaces.shares(chapters, spans, weights)
            assertNotNull(form, shares)
            assertEquals(listOf(20f, 50f, 30f), chapters.indices.map { shares!![it] }.filter { it > 0f })
        }
        // Or by the title, when that's the file.
        val byTitle = listOf(KoboChapterSpan("a", "OEBPS/Text/ch1.xhtml", 0.0, 34.0), KoboChapterSpan("b", "OEBPS/Text/ch2.xhtml", 34.0, 33.0),
            KoboChapterSpan("c", "OEBPS/Text/ch3.xhtml", 67.0, 33.0))
        assertNotNull(KoboPlaces.shares(chapters, byTitle, weights))
        // Another version of the book, whose chapters aren't these: counted from its own text instead.
        val other = listOf(KoboChapterSpan("$id!!OEBPS/Text/part1.xhtml", null, 0.0, 60.0), KoboChapterSpan("$id!!OEBPS/Text/ch3.xhtml", null, 60.0, 40.0))
        assertNull(KoboPlaces.shares(chapters, other, weights))
    }

    @Test
    fun lettersLeaveOutSpacesScriptsAndTheInvisible() {
        val x = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title><style>p{}</style></head>
            <body> <p>A b&#160;c</p><script>var x = 1;</script><p id="p2">d&#173;e&#8203;f</p></body></html>"""
        val letters = ChapterLetters(x)
        assertEquals(6, letters.total)
        assertEquals(3, letters.lettersBeforeId("p2"))
        assertEquals("def", letters.snippetAt(3).filter { ChapterLetters.counts(it) })
    }

    private fun kobo(bookmark: String?, adobe: String? = null, percent: Int = 0, chapterProgress: Double? = null) = KoboBook(
        contentId = "file:///mnt/onboard/Books/Sea.kepub.epub", title = "The Sea", author = null, subtitle = null,
        series = null, seriesNumber = null, description = null, publisher = null, language = null, isbn = null,
        mimeType = "application/x-kobo-epub+zip", format = "KEPUB", imageId = "x", kind = BookKind.SIDELOADED,
        isDownloaded = true, isExpired = false, readStatus = ReadStatus.READING, percentRead = percent,
        lastReadMillis = null, addedMillis = null, fileSize = null, relativePath = "Books/Sea.kepub.epub",
        chapterBookmark = bookmark, adobeLocation = adobe, chapterProgress = chapterProgress,
    )

    /** The id KoLib's kepub gives the sentence starting with [text]. */
    private fun spanIdOf(xhtml: String, text: String): String {
        val doc = Xml.parse(KepubConverter.convert(xhtml))
        return doc.root.descendants().first { it.attributes["class"] == "koboSpan" && it.text().startsWith(text) }.attributes["id"]!!
    }

    private fun book(): File = TestEpubs.build(
        File(tmp.root, "sea.epub"),
        mapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to OPF.toByteArray(),
            "OEBPS/Text/ch1.xhtml" to CH1.toByteArray(),
            "OEBPS/Text/ch2.xhtml" to CH2.toByteArray(),
            "OEBPS/Text/ch3.xhtml" to CH3.toByteArray(),
            "OEBPS/nav.xhtml" to NAV.toByteArray(),
        ),
    )

    private companion object {
        val OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>The Sea</dc:title><dc:language>en</dc:language></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="c3" href="Text/ch3.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="c1"/><itemref idref="c2"/><itemref idref="c3"/></spine>
</package>"""
        val NAV = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol>
<li><a href="Text/ch1.xhtml">One</a></li><li><a href="Text/ch2.xhtml">Two</a></li><li><a href="Text/ch3.xhtml">Three</a></li></ol></nav></body></html>"""
        val CH1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head>
<body><h1>One</h1><p>It began on a calm morning. Nobody expected the storm.</p><p>By noon the sky had turned.</p></body></html>"""
        val CH2 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head>
<body>
  <h1>Two</h1>
  <p>The sea was grey. Gulls cried over it.</p>
  <p>The tide came in again. It always did.</p>
  <p>Night fell, and the lamps were lit along the harbour wall.</p>
</body></html>"""
        val CH3 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Three</title></head>
<body><div><p>Hello there.</p><p>Second one here.</p></div></body></html>"""
    }
}
