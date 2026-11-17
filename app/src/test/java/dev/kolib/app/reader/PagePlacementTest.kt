package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** Telling a short page's kind from its file: a cover, a title page, a dedication, a quote, or another. */
class PagePlacementTest {

    private fun page(body: String, extra: String = "") = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>X</title></head><body$extra>$body</body></html>"""

    /** Pictures' sizes by path: the publisher's mark is small, a map wide; everything else as big as a page. */
    private val sizes = mapOf("text/logo.png" to (160 to 120), "images/tor.png" to (200 to 180), "text/wide.jpg" to (2400 to 1600), "text/divider.png" to (1200 to 300))
    private fun kind(body: String, path: String = "text/page.xhtml", title: String = "", extra: String = "", cover: Boolean = false, titlePlace: Boolean = false) =
        PagePlacement.kind(PagePlacement.facts(page(body, extra), path, title) { sizes[it] ?: (1200 to 1800) }, cover, titlePlace)

    private val lorem = (1..50).joinToString(" ") { "word$it and more" }

    @Test
    fun whatAShortPageIs() {
        // A picture with next to no words: the book's cover where a cover goes, or one it names as a cover; a plate otherwise.
        assertEquals(PageKind.COVER, kind("""<div><img src="c.jpg" alt="Cover"/></div>""", cover = true))
        assertEquals(PageKind.COVER, kind("""<div><img src="c.jpg"/></div>""", path = "text/cover.xhtml"))
        assertEquals(PageKind.PICTURE, kind("""<div><img src="map.jpg" alt="Map"/></div>"""))
        assertEquals(PageKind.PICTURE, kind("""<svg xmlns="http://www.w3.org/2000/svg"><image href="p.jpg"/></svg>"""))
        // Wider than it's tall: a map or a scene across two pages, alone across both.
        assertEquals(PageKind.WIDE_PICTURE, kind("""<div><img src="wide.jpg"/></div>"""))
        assertEquals(PageKind.WIDE_PICTURE, kind("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><image xlink:href="wide.jpg"/></svg>"""))
        // A book's title page inside a book of several.
        assertEquals(PageKind.TITLE, kind("""<h1>LORD OF CHAOS</h1><p>Robert Jordan</p><p><img src="tor.png"/></p>""", titlePlace = true))
        // Dedications: marked as one, or reading like one.
        assertEquals(PageKind.DEDICATION, kind("""<p>For Harriet</p>""", path = "text/dedication.xhtml"))
        assertEquals(PageKind.DEDICATION, kind("""<p>Harriet</p>""", extra = """ epub:type="dedication""""))
        assertEquals(PageKind.DEDICATION, kind("""<p>For Harriet</p><p>the light of my life</p>"""))
        assertEquals(PageKind.DEDICATION, kind("""<p>To my mother, who read to me.</p>"""))
        // Quotes: marked as one, in a block quote, or with where it's from under it.
        assertEquals(PageKind.QUOTE, kind("""<section epub:type="epigraph"><p>The Wheel weaves as the Wheel wills.</p></section>"""))
        assertEquals(PageKind.QUOTE, kind("""<blockquote><p>All is well.</p></blockquote>"""))
        assertEquals(PageKind.QUOTE, kind("""<p>And it shall come to pass that what men made shall be shattered.</p><p>—from The Karaethon Cycle</p>"""))
        // A plate with a caption is a picture; a title page with its publisher's mark isn't.
        assertEquals(PageKind.PICTURE, kind("""<div><img src="p.jpg"/></div><p>Rand al'Thor, as drawn by the author</p>"""))
        assertEquals(PageKind.SHORT, kind("""<h1>DUNE</h1><p>Frank Herbert</p><p><img src="logo.png"/></p>"""))
        assertEquals(PageKind.SHORT, kind("""<p>DUNE</p><p><img src="../images/tor.png"/></p>"""))
        // A small picture alone is an ornament (a divider, a flourish), not a page's picture; a big one with a heading is still a picture.
        assertEquals(PageKind.ORNAMENT, kind("""<p><img src="logo.png"/></p>"""))
        assertEquals(PageKind.ORNAMENT, kind("""<div><img src="divider.png"/></div>"""))
        assertEquals(PageKind.PICTURE, kind("""<h2>Map</h2><p><img src="map.png"/></p>"""))
        // Anything else short.
        assertEquals(PageKind.SHORT, kind("""<h1>THE WHEEL OF TIME</h1><p>ROBERT JORDAN</p><p>AND BRANDON SANDERSON</p><p><img src="tor.png"/></p>"""))
        assertEquals(PageKind.SHORT, kind("""<p>The author and publisher have provided this e-book to you without Digital Rights Management software (DRM) applied.</p><p>Copyright infringement is against the law. Notify the publisher at: http://us.macmillanusa.com/piracy.</p>"""))
        // A short chapter, or a part's title page: a chapter all the same.
        assertEquals(PageKind.TEXT, kind("""<h2>Chapter 1</h2><p>Rose walked into the garden.</p>"""))
        assertEquals(PageKind.TEXT, kind("""<h1>Part One</h1><p>The Garden</p>"""))
        assertEquals(PageKind.TEXT, kind("""<p>The garden was quiet.</p>""", title = "Chapter 12"))
        assertEquals(PageKind.TEXT, kind("""<h2>12</h2><p>The garden was quiet.</p>"""))
        // Longer than a short page: a chapter, whatever it's called.
        assertEquals(PageKind.TEXT, kind("<p>$lorem</p>", path = "text/dedication.xhtml"))
        assertEquals(PageKind.TEXT, kind("<p>$lorem</p>", titlePlace = true))
    }

    @Test
    fun aBooksOwnTitlePage() {
        fun facts(body: String, path: String = "text/page.xhtml", title: String = "", extra: String = "") =
            PagePlacement.facts(page(body, extra), path, title) { sizes[it] ?: (1200 to 1800) }
        // Named as one in the contents, by its file, or by the book's own marks.
        assertEquals(true, PagePlacement.isTitlePage(facts("""<div><img src="tp.jpg"/></div>""", title = "Title Page"), "OEBPS/x.xhtml", "The Wheel of Time"))
        assertEquals(true, PagePlacement.isTitlePage(facts("""<p>DUNE</p>"""), "OEBPS/text/titlepage.xhtml", null))
        assertEquals(true, PagePlacement.isTitlePage(facts("""<p>DUNE</p>"""), "OEBPS/Jord_9781250256232_epub3_tp_r1.xhtml", null))
        assertEquals(true, PagePlacement.isTitlePage(facts("""<p>DUNE</p>""", extra = """ epub:type="titlepage""""), "OEBPS/p2.xhtml", null))
        // Or a short page that starts with the book's title.
        assertEquals(true, PagePlacement.isTitlePage(facts("""<h1>THE WHEEL OF TIME</h1><p>ROBERT JORDAN</p>"""), "OEBPS/p2.xhtml", "The Wheel of Time"))
        // Not a list of the author's books that names it, a chapter, or a long page.
        assertEquals(false, PagePlacement.isTitlePage(facts("""<p>Also by Robert Jordan</p><p>The Wheel of Time</p>"""), "OEBPS/p3.xhtml", "The Wheel of Time"))
        assertEquals(false, PagePlacement.isTitlePage(facts("""<h2>Chapter 1</h2><p>Rose walked into the garden.</p>"""), "OEBPS/subtitle.xhtml", "Dune"))
        assertEquals(false, PagePlacement.isTitlePage(facts("<p>$lorem</p>", title = "Title Page"), "OEBPS/p2.xhtml", null))
        // A title page that's only a picture (the title set as an image) is still the title page, in the middle; where
        // the cover goes, it's the cover.
        assertEquals(PageKind.TITLE, kind("""<div><img src="tp.jpg" alt="The Wheel of Time"/></div>""", titlePlace = true))
        assertEquals(PageKind.COVER, kind("""<div><img src="c.jpg"/></div>""", cover = true, titlePlace = true))
        assertEquals(PageKind.TITLE, kind("""<h1>THE WHEEL OF TIME</h1><p>ROBERT JORDAN</p><p><img src="tor.png"/></p>""", titlePlace = true))
    }

    @Test
    fun picturesSizesFromTheirFiles() {
        // PNG: 8-byte signature, IHDR length and type, then width and height.
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0, 0, 0x05, 0x00, 0, 0, 0x03, 0x20, 8, 6, 0, 0, 0)
        assertEquals(1280 to 800, ImageSize.of(png))
        // GIF: width and height, little-endian, after "GIF89a".
        val gif = "GIF89a".toByteArray() + byteArrayOf(0x40, 0x01, 0xF0.toByte(), 0x00, 0, 0)
        assertEquals(320 to 240, ImageSize.of(gif))
        // JPEG: start, an APP0 segment, then a frame header with height and width.
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 0, 0,
            0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0x06, 0x40, 0x04, 0x00, 3, 0, 0, 0)
        assertEquals(1024 to 1600, ImageSize.of(jpeg))
        assertEquals(null, ImageSize.of("<svg/>".toByteArray()))
    }

    @Test
    fun whereEachGoesAndWhatGoesBesideWhat() {
        assertEquals("cover", PageKind.COVER.place)
        assertEquals("title", PageKind.TITLE.place)
        assertEquals("left", PageKind.DEDICATION.place)
        assertEquals(null, PageKind.QUOTE.place)
        assertEquals(null, PageKind.SHORT.place)
        assertEquals(null, PageKind.PICTURE.place)
        assertEquals(null, PageKind.WIDE_PICTURE.place)
        // A dedication on the left, a quote on the right; other short pages either side; covers and title pages alone.
        assertEquals(listOf(PageKind.DEDICATION, PageKind.SHORT), PageKind.entries.filter { it.canBeLeft })
        assertEquals(listOf(PageKind.QUOTE, PageKind.SHORT), PageKind.entries.filter { it.canBeRight })
    }
}
