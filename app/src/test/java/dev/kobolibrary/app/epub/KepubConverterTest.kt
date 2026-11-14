package dev.kobolibrary.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KepubConverterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val span = { p: Int, s: Int -> """<span class="koboSpan" id="kobo.$p.$s">""" }

    @Test
    fun addsSpansWrappersAndTheStyle() {
        val doc = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head>""" +
            """<body><h1>Chapter One</h1><p>It begins here. And so it goes.</p></body></html>"""
        val expected = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title>""" + "\n" + KepubConverter.STYLE + "</head>" +
            """<body><div id="book-columns"><div id="book-inner">""" +
            "<h1>${span(1, 1)}Chapter One</span></h1>" +
            "<p>${span(2, 1)}It begins here. </span>${span(2, 2)}And so it goes.</span></p>" +
            "</div></div></body></html>"
        assertEquals(expected, KepubConverter.convert(doc))
    }

    @Test
    fun numbersLikeKepubify() {
        // Images start a paragraph and get a span; divs and inline elements don't start one;
        // text in an inline element gets its own span.
        val doc = """<html xmlns="http://www.w3.org/1999/xhtml"><head/><body>""" +
            """<p>Hello <b>world</b>.</p><div>Loose text. More!</div><p><img src="a.jpg" alt=""/>Caption.</p></body></html>"""
        val out = KepubConverter.convert(doc)
        assertTrue(out, out.contains("<p>${span(1, 1)}Hello </span><b>${span(1, 2)}world</span></b>${span(1, 3)}.</span></p>"))
        assertTrue(out, out.contains("<div>${span(1, 4)}Loose text. </span>${span(1, 5)}More!</span></div>"))
        assertTrue(out, out.contains("""<p>${span(2, 1)}<img src="a.jpg" alt=""/></span>${span(2, 2)}Caption.</span></p>"""))
    }

    @Test
    fun leavesSpaceOnlyTextOutsideParagraphsAndSkipsCodeAndScripts() {
        val doc = "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n  <title>T</title>\n</head>\n<body>\n  <div>\n    <p>A. B</p>\n  </div>\n" +
            "  <pre>keep. as is.</pre>\n  <script>var a = 1. ;</script>\n</body>\n</html>"
        val out = KepubConverter.convert(doc)
        assertTrue(out, out.contains("<div>\n    <p>${span(1, 1)}A. </span>${span(1, 2)}B</span></p>\n  </div>"))
        assertTrue(out, out.contains("<pre>keep. as is.</pre>"))
        assertTrue(out, out.contains("<script>var a = 1. ;</script>"))
        assertTrue(out, out.contains("<title>T</title>\n" + KepubConverter.STYLE + "\n</head>"))
    }

    @Test
    fun splitsAfterQuotesWrittenAsCharacterReferences() {
        val doc = """<html xmlns="http://www.w3.org/1999/xhtml"><head/><body><p>He said &#8220;Stop.&#8221; Then &amp; left.</p></body></html>"""
        val out = KepubConverter.convert(doc)
        assertTrue(out, out.contains("<p>${span(1, 1)}He said &#8220;Stop.&#8221; </span>${span(1, 2)}Then &amp; left.</span></p>"))
    }

    @Test
    fun strippingGivesBackTheOriginalExactly() {
        val docs = listOf(
            "<?xml version='1.0' encoding='utf-8'?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n  <head>\n    <title>One</title>\n    <link rel=\"stylesheet\" href=\"s.css\"/>\n  </head>\n" +
                "  <body class=\"c\">\n    <h2 id=\"x\">Part <i>One</i></h2>\n    <p class=\"t\">It was late. &#8220;Who&#8217;s there?&#8221; she asked&#8230; Nobody.</p>\n" +
                "    <p><img src=\"../i/a.png\" alt=\"A\"/></p>\n    <ul><li>One. Two</li><li>Three</li></ul>\n    <table><tr><td>Cell.</td></tr></table>\n  </body>\n</html>\n",
            """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head><body><p>x</p></body></html>""",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head><body>\n</body></html>",
        )
        for (d in docs) {
            val kepub = KepubConverter.convert(d)
            assertTrue(KepubStripper.looksLikeKepub(kepub))
            assertEquals(d, KepubStripper.strip(kepub))
        }
    }

    @Test
    fun convertingTwiceChangesNothingMore() {
        val doc = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head><body><p>A. B.</p></body></html>"""
        val once = KepubConverter.convert(doc)
        assertEquals(once, KepubConverter.convert(once))
    }

    @Test(expected = XmlException::class)
    fun refusesBrokenXhtml() {
        KepubConverter.convert("<html><body><p class=\"x>Never closed</p></body></html>")
    }

    /** kepubify's own sentence tests, checked against the regular expression it documents. */
    @Test
    fun splitsSentencesLikeKepubify() {
        val reference = Regex("""(?s).*?[.!?]['"”’“…]?[ \t\n\u000c\r]+""")
        fun byRegex(s: String): List<String> {
            val matches = reference.findAll(s).toList()
            if (matches.isEmpty()) return listOf(s)
            val out = mutableListOf<String>()
            var pos = 0
            for (m in matches) {
                out += s.substring(pos, m.range.last + 1)
                pos = m.range.last + 1
            }
            if (s.length > pos) out += s.substring(pos)
            return out
        }
        fun byConverter(s: String): List<String> {
            val starts = KepubConverter.sentenceStarts(s)
            return starts.indices.map { i -> s.substring(starts[i], if (i + 1 < starts.size) starts[i + 1] else s.length) }
        }
        val cases = listOf(
            " ! Lorem ipsum dolor, sit amet. Consectetur adipiscing elit?\n Sed do eiusmod tempor incididunt!?! Ut labore et dolore \"magna aliqua.\". " +
                "Ut enim ad “minim veniam”, quis nostrud exercitation 'ullamco laboris' nisi ut aliquip ex ea commodo consequat?… Duis aute irure dolor in " +
                "reprehenderit in voluptate velit’s esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in " +
                "culpa qui officia deserunt mollit anim id est laborum.      sdfsdfsdf",
            "Lorem ipsum dolor, sit amet. Consectetur adipiscing elit? Sed do eiusmod tempor incididunt!?! Ut labore et dolore magna aliqua.",
            "Lorem ipsum dolor sit amet. Consectetur adipiscing elit ut labore et dolore magna aliqua. ".repeat(40),
            "                                ",
            "...       !!!       ???       .'.'.'.'   ",
            "test .\u0080. .",
            "🌝. 🌝      🌝.    🌝",
            "!", "? ", "? ?", "?  ", "  ?  ", " ?'  .", " ?'  .   ",
        )
        for (c in cases) assertEquals(c, byRegex(c), byConverter(c))
    }

    @Test
    fun convertsAWholeBookAndMarksTheCover() {
        val source = TestEpubs.calibreEpub2(tmp.newFile("h.epub"))
        val target = tmp.newFile("h.kepub.epub")
        val result = EpubEditor.toKepub(source, target)
        assertEquals(2, result.documents)
        assertTrue(result.skipped.isEmpty())
        EpubFile.open(target).use { epub ->
            val text = epub.readText("OEBPS/text/part0000.html")!!
            assertTrue(text, text.contains("""<span class="koboSpan" id="kobo.1.1">Herodotus of Halicarnassus...</span>"""))
            val cover = epub.opf.items.first { it.id == "cover" }
            assertTrue("cover-image" in cover.properties)
            assertEquals(epub.opf.readMetadata(), EpubFile.open(source).use { it.opf.readMetadata() })
        }
        // And back again.
        assertTrue(EpubEditor.stripKepub(target))
        EpubFile.open(target).use { epub ->
            assertFalse(epub.readText("OEBPS/text/part0000.html")!!.contains("koboSpan"))
        }
    }
}
