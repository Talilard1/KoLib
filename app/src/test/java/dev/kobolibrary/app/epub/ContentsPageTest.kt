package dev.kobolibrary.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A book's own contents page: telling one, and making one list another book's chapters in its own style. */
class ContentsPageTest {

    private fun page(body: String, title: String = "Contents") = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title><link rel="stylesheet" type="text/css" href="../styles/toc.css"/></head>
<body>
$body
</body>
</html>
"""

    private val styled = page("""  <h1 class="toc-title">Contents</h1>
  <p class="toc-part"><a href="part1.xhtml">Part One</a></p>
  <p class="toc-ch"><a href="ch1.xhtml">Chapter 1</a></p>
  <p class="toc-ch"><a href="ch2.xhtml">Chapter 2</a></p>
  <p class="toc-part"><a href="part2.xhtml">Part Two</a></p>
  <p class="toc-ch"><a href="ch3.xhtml"><span class="t">Chapter 3</span></a></p>
  <p class="end">Thanks for reading.</p>""")

    /** Its book's contents: parts at the top, chapters under them. */
    private val levels = mapOf("OEBPS/text/part1.xhtml" to 0, "OEBPS/text/ch1.xhtml" to 1, "OEBPS/text/ch2.xhtml" to 1,
        "OEBPS/text/part2.xhtml" to 0, "OEBPS/text/ch3.xhtml" to 1)

    /** Another book's chapters: a book at the top with chapters under it, then an epilogue. */
    private val other = listOf(
        ContentsPage.Entry("The Eye of the World", "OEBPS/Text/book.xhtml", null, 0),
        ContentsPage.Entry("An Empty Road", "OEBPS/Text/c01.xhtml", null, 1),
        ContentsPage.Entry("Strangers & Friends", "OEBPS/Text/c02.xhtml", "s2", 1),
        ContentsPage.Entry("Epilogue", "OEBPS/Text/end.xhtml", null, 0),
    )

    @Test
    fun tellsAContentsPage() {
        assertTrue(ContentsPage.isContents(styled, "OEBPS/text/contents.xhtml", "Contents"))
        // Not named so, but nearly all links.
        assertTrue(ContentsPage.isContents(page("""<p><a href="a.xhtml">One</a></p><p><a href="b.xhtml">Two</a></p><p><a href="c.xhtml">Three</a></p>"""),
            "OEBPS/text/p.xhtml", "Untitled"))
        // A chapter with a few notes linked from its prose isn't one; nor is a page with two links.
        val prose = (1..40).joinToString(" ") { "word" }
        assertFalse(ContentsPage.isContents(page("""<p>$prose<a href="n.xhtml#1">1</a> $prose<a href="n.xhtml#2">2</a> $prose<a href="n.xhtml#3">3</a></p>""", "Chapter 4"),
            "OEBPS/text/ch4.xhtml", "Chapter 4"))
        assertFalse(ContentsPage.isContents(page("""<p><a href="a.xhtml">One</a></p><p><a href="b.xhtml">Two</a></p>"""), "OEBPS/text/toc.xhtml", "Contents"))
    }

    @Test
    fun listsAnotherBooksChaptersInItsOwnStyle() {
        val out = ContentsPage.relist(styled, "OEBPS/text/contents.xhtml", other, levels)
        assertNotNull(out)
        out!!
        assertNull(XmlCheck.check(out, false))
        // Its heading, styles and the rest stay; its own entries go.
        assertTrue(out.contains("""<h1 class="toc-title">Contents</h1>"""))
        assertTrue(out.contains("""href="../styles/toc.css""""))
        assertTrue(out.contains("""<p class="end">Thanks for reading.</p>"""))
        assertFalse(out.contains("Part One") || out.contains("Chapter 2"))
        // Each entry like the page's own at its depth, linking to the other book's chapters from where the page is.
        assertTrue(out, out.contains("""<p class="toc-part"><a href="../Text/book.xhtml">The Eye of the World</a></p>"""))
        assertTrue(out, out.contains("""<p class="toc-ch"><a href="../Text/c01.xhtml">An Empty Road</a></p>"""))
        assertTrue(out, out.contains("""<p class="toc-ch"><a href="../Text/c02.xhtml#s2">Strangers &amp; Friends</a></p>"""))
        assertTrue(out, out.contains("""<p class="toc-part"><a href="../Text/end.xhtml">Epilogue</a></p>"""))
        // One after another, as the page had them.
        assertTrue(out, out.contains("</p>\n  <p class=\"toc-ch\"><a href=\"../Text/c01.xhtml\">"))
    }

    @Test
    fun withoutItsBooksContentsTheCommonestKindIsTheChapters() {
        val out = ContentsPage.relist(styled, "OEBPS/text/contents.xhtml", other)!!
        assertTrue(out, out.contains("""<p class="toc-part"><a href="../Text/book.xhtml">The Eye of the World</a></p>"""))
        assertTrue(out, out.contains("""<p class="toc-ch"><a href="../Text/c01.xhtml">An Empty Road</a></p>"""))
    }

    @Test
    fun aListStaysAListWithItsDeeperLists() {
        val listed = page("""  <h1>Contents</h1>
  <ol class="toc">
    <li class="part"><a href="part1.xhtml">Part One</a>
      <ol class="sub">
        <li class="ch"><span class="n"><a href="ch1.xhtml" class="go">One</a></span></li>
        <li class="ch"><span class="n"><a href="ch2.xhtml" class="go">Two</a></span></li>
      </ol>
    </li>
    <li class="part" id="p2"><a href="part2.xhtml">Part Two</a></li>
  </ol>""")
        val out = ContentsPage.relist(listed, "OEBPS/text/contents.xhtml", other, levels)!!
        assertNull(XmlCheck.check(out, false))
        assertTrue(out, out.contains("""<li class="part"><a href="../Text/book.xhtml">The Eye of the World</a>"""))
        assertTrue(out, out.contains("""<ol class="sub">"""))
        assertTrue(out, out.contains("""<li class="ch"><span class="n"><a class="go" href="../Text/c01.xhtml">An Empty Road</a></span></li>"""))
        assertTrue(out, out.contains("""<li class="part"><a href="../Text/end.xhtml">Epilogue</a></li>"""))
        // An id isn't repeated; the old entries are gone.
        assertFalse(out.contains("p2") || out.contains("Part Two"))
        assertEquals(1, Regex("""<ol class="toc">""").findAll(out).count())
    }

    @Test
    fun entriesInOneLineKeepWhatWentBetweenThem() {
        val oneLine = page("""<p class="toc"><a href="1.xhtml">One</a><br/><a href="2.xhtml">Two</a><br/><a href="3.xhtml">Three</a></p>""")
        val out = ContentsPage.relist(oneLine, "OEBPS/text/contents.xhtml", other.take(2))!!
        assertTrue(out, out.contains("""<p class="toc"><a href="../Text/book.xhtml">The Eye of the World</a><br/><a href="../Text/c01.xhtml">An Empty Road</a></p>"""))
    }

    @Test
    fun twoLinksToOneChapterAreOneEntry() {
        val split = page("""<p class="e"><a href="c1.xhtml#n">1</a> <a href="c1.xhtml">The Hook</a></p>
<p class="e"><a href="c2.xhtml#n">2</a> <a href="c2.xhtml">A Wish Fulfilled</a></p>""")
        val out = ContentsPage.relist(split, "OEBPS/text/contents.xhtml", other.take(2))!!
        assertTrue(out, out.contains("""<p class="e"><a href="../Text/book.xhtml">The Eye of the World</a></p>"""))
        assertEquals(2, Regex("""<p class="e">""").findAll(out).count())
    }

    @Test
    fun linksToPagesTheBookHasntGotLoseTheirLinkButNotTheirWords() {
        val text = page("""<p>See <a href="gone.xhtml" class="x" id="k">the map</a>, <a href="here.xhtml">the notes</a>, <a href="#top">the top</a> and <a href="https://example.com/">the site</a>.</p>""")
        val out = ContentsPage.unlinkMissing(text, "OEBPS/text/p.xhtml") { it == "OEBPS/text/here.xhtml" }
        assertTrue(out, out.contains("""See <span class="x" id="k">the map</span>,"""))
        assertTrue(out.contains("""<a href="here.xhtml">the notes</a>"""))
        assertTrue(out.contains("""<a href="#top">the top</a>"""))
        assertTrue(out.contains("""<a href="https://example.com/">the site</a>"""))
    }
}
