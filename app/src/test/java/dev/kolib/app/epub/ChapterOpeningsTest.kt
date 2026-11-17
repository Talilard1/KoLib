package dev.kolib.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterOpeningsTest {

    private fun ch(i: Int, title: String, linear: Boolean = true) =
        Chapter(index = i, id = "f$i", path = "OEBPS/text/f$i.xhtml", linear = linear, title = title, titleFromToc = true)

    private val words = (1..300).joinToString(" ") { "word$it" }

    private fun page(body: String, css: String = "../styles/a.css", extraNs: String = "") = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"$extraNs lang="en"><head><title>t</title>
<link rel="stylesheet" type="text/css" href="$css"/>
</head>
<body class="chapter">
$body
</body>
</html>
"""

    private val hook = page("<h2>The Hook</h2><p>$words</p>")

    @Test
    fun findsAChaptersOpeningInAFileOfItsOwn() {
        // "1 The Hook" (the number and an ornament) before "The Hook" (its text).
        assertTrue(ChapterOpenings.joins(ch(0, "1 The Hook"), page("<h1>Chapter 1</h1><p><img src=\"../images/orn.png\" alt=\"\"/></p>"), ch(1, "The Hook"), hook))
        // Named by its title only, the number drawn as a picture.
        assertTrue(ChapterOpenings.joins(ch(0, "Chapter 2: Strangers"), page("<p><img src=\"../images/2.png\" alt=\"\"/></p>"), ch(1, "Strangers"), hook))
        // Saying nothing but the chapter's number, whatever the files are called.
        assertTrue(ChapterOpenings.joins(ch(0, "ch02a"), page("<p>CHAPTER TWO</p>"), ch(1, "ch02b"), hook))
        assertTrue(ChapterOpenings.joins(ch(0, "x"), page("<h1>XIV</h1>"), ch(1, "y"), hook))
    }

    @Test
    fun leavesOtherPagesOnTheirOwn() {
        // A part's title page stands alone, as in print.
        assertFalse(ChapterOpenings.joins(ch(0, "Part One: The Hook"), page("<h1>Part One</h1>"), ch(1, "The Hook"), hook))
        // A picture between chapters.
        assertFalse(ChapterOpenings.joins(ch(0, "plate3"), page("<p><img src=\"../images/p3.png\" alt=\"\"/></p>"), ch(1, "Chapter 4"), hook))
        // An epigraph is more than a number.
        assertFalse(ChapterOpenings.joins(ch(0, "Epigraph"), page("<p>The Wheel of Time turns, and Ages come and pass.</p>"), ch(1, "Chapter 1"), hook))
        // The next one isn't a chapter's worth of text.
        assertFalse(ChapterOpenings.joins(ch(0, "1 The Hook"), page("<h1>Chapter 1</h1>"), ch(1, "The Hook"), page("<h2>The Hook</h2><p>Short.</p>")))
        // Either read only by link.
        assertFalse(ChapterOpenings.joins(ch(0, "1 The Hook", linear = false), page("<h1>Chapter 1</h1>"), ch(1, "The Hook"), hook))
        // An opening of more than a few words is a chapter in its own right.
        assertFalse(ChapterOpenings.joins(ch(0, "1 The Hook"), page("<h1>Chapter 1</h1><p>$words</p>"), ch(1, "The Hook"), hook))
    }

    @Test
    fun putsTheOpeningInFrontOfTheChaptersBody() {
        val opening = page(
            "<h1 class=\"num\">Chapter 1</h1><div><svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><image xlink:href=\"../images/orn.png\" width=\"10\" height=\"10\"/></svg></div>",
            css = "../styles/opening.css",
            extraNs = " xmlns:xlink=\"http://www.w3.org/1999/xlink\"",
        )
        val out = ChapterOpenings.join(hook, "OEBPS/text/f1.xhtml", opening, "OEBPS/text/f0.xhtml")!!
        val box = out.indexOf("<div id=\"kl-opening\"")
        assertTrue(out, box in 0 until out.indexOf("<body"))
        // Still well-formed, the svg's xlink with it.
        assertNull(XmlCheck.check(out, htmlEntities = true))
        assertTrue(out.substring(box, out.indexOf('>', box)).contains("xmlns:xlink=\"http://www.w3.org/1999/xlink\""))
        assertTrue(out.substring(box, out.indexOf('>', box)).contains("class=\"chapter\""))
        // The opening's style sheet is added; the chapter's own body is untouched, so places in it still fit.
        assertTrue(out.contains("href=\"../styles/opening.css\""))
        assertEquals(hook.substring(hook.indexOf("<body")), out.substring(out.indexOf("<body")))
        // Not across folders (its links would break), and not when a page isn't well-formed.
        assertNull(ChapterOpenings.join(hook, "OEBPS/b/f1.xhtml", opening, "OEBPS/a/f0.xhtml"))
        assertNull(ChapterOpenings.join("<html><body><p>Broken</body></html>", "OEBPS/text/f1.xhtml", opening, "OEBPS/text/f0.xhtml"))
    }
}
