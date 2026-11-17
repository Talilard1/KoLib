package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookPicturesTest {

    private fun page(body: String) = """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body>$body</body></html>"""

    private val pages = mapOf(
        "OEBPS/Text/cover.xhtml" to page("""<svg xmlns:xlink="http://www.w3.org/1999/xlink"><image xlink:href="../Images/cover.jpg" width="600" height="900"/></svg>"""),
        "OEBPS/Text/map.xhtml" to page("""<img src="../Images/map.png" alt=""/>"""),
        "OEBPS/Text/ch1.xhtml" to page("""<h1><img src="../Images/icon.png"/> One</h1><p>Text <img src="../Images/spacer.gif"/></p><hr/><img src="../Images/rule.png"/><p><img src="../Images/fig1.jpg"/></p>"""),
        "OEBPS/Text/ch2.xhtml" to page("""<h1><img src="../Images/icon.png"/> Two</h1><p><img src='../Images/fig2.jpg?x=1'/><img src="http://example.com/far.jpg"/></p>"""),
        "OEBPS/Text/ch3.xhtml" to page("""<p>No pictures here.</p>"""),
        "OEBPS/Text/ch4.xhtml" to page("""<p><img src="../Images/missing.jpg"/></p>"""),
    )

    private val sizes = mapOf(
        "OEBPS/Images/cover.jpg" to (600 to 900),
        "OEBPS/Images/map.png" to (1400 to 1000),
        "OEBPS/Images/icon.png" to (120 to 120),
        "OEBPS/Images/spacer.gif" to (1 to 1),
        "OEBPS/Images/rule.png" to (600 to 4),
        "OEBPS/Images/fig1.jpg" to (800 to 500),
        "OEBPS/Images/fig2.jpg" to (500 to 800),
    )

    @Test
    fun everyPictureOnceInReadingOrder() {
        val found = BookPictures.find(pages.keys.toList(), { pages[it] }, { sizes[it] })
        assertEquals(
            listOf("cover.jpg" to 0, "map.png" to 1, "icon.png" to 2, "fig1.jpg" to 2, "fig2.jpg" to 3),
            found.map { it.path.substringAfterLast('/') to it.chapter },
        )
        // Its size comes with it.
        assertEquals(1400 to 1000, found[1].width to found[1].height)
    }

    @Test
    fun spacersAndRulesLeftOut() {
        assertTrue(BookPictures.tiny(1, 1))
        assertTrue(BookPictures.tiny(600, 4))
        assertTrue(BookPictures.tiny(40, 40))
        assertEquals(false, BookPictures.tiny(120, 120))
        assertEquals(false, BookPictures.tiny(300, 40))
    }
}
