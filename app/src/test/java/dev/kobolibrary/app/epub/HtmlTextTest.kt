package dev.kobolibrary.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {

    @Test
    fun keepsSimpleFormatting() {
        val html = "<p>The first great work of <b>history</b>.</p>"
        assertEquals(html, HtmlText.sanitize(html))
    }

    @Test
    fun tidiesWhatTheEditorProduces() {
        // What a browser's editable area gives back: empty paragraphs, trailing breaks, divs.
        val html = "<p>One <b>bold</b> and <i>it</i>.</p><p><br></p><ul><li>a</li><li>b<br></li></ul><div>Plain div</div>"
        assertEquals("<p>One <b>bold</b> and <i>it</i>.</p><ul><li>a</li><li>b</li></ul><p>Plain div</p>", HtmlText.sanitize(html))
    }

    @Test
    fun dropsScriptsStylesAttributesAndLinks() {
        val html = "<div class=\"x\" onclick=\"evil()\"><script>alert(1)</script><style>p{}</style>" +
            "<p style=\"color:red\">Hi <img src=x onerror=alert(1)><a href=\"javascript:x\">link</a></p></div>"
        assertEquals("<p>Hi link</p>", HtmlText.sanitize(html))
    }

    @Test
    fun looseTextBecomesParagraphs() {
        assertEquals("<p>Just text &amp; more</p>", HtmlText.sanitize("Just text &amp; more"))
        assertEquals("<p>Line one<br/>Line two</p><p>After</p>", HtmlText.sanitize("Line one<br>Line two<p>After</p>"))
    }

    @Test
    fun decodesEntities() {
        assertEquals("<p>It’s — fine… 1 &lt; 2</p>", HtmlText.sanitize("<p>It&rsquo;s &mdash; fine&hellip; 1 &lt; 2</p>"))
        assertEquals("<p>Caf&amp;unknown; é</p>", HtmlText.sanitize("<p>Caf&unknown; &#233;</p>"))
    }

    @Test
    fun headingsBecomeBoldParagraphs() {
        // Calibre wraps descriptions in a div.
        assertEquals("<p><b>Review</b></p><p>Text</p>", HtmlText.sanitize("<div><h3>Review</h3><p>Text</p></div>"))
    }

    @Test
    fun handlesNestedListsAndUnclosedTags() {
        assertEquals("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>", HtmlText.sanitize("<ul><li>a<ul><li>b</ul><li>c</ul>"))
        assertEquals("<p>one</p><p>two</p>", HtmlText.sanitize("<p>one<p>two"))
        assertEquals("<ol><li>first</li></ol>", HtmlText.sanitize("<ol><li>first"))
        assertEquals("<p><b>bold to the end</b></p>", HtmlText.sanitize("<p><strong>bold to the end"))
    }

    @Test
    fun emptyMeansEmpty() {
        assertEquals("", HtmlText.sanitize("<p><br></p><p>&nbsp;</p><div> </div>"))
        assertTrue(HtmlText.isBlank("   "))
        assertTrue(HtmlText.isBlank(null))
        assertFalse(HtmlText.isBlank("<p>x</p>"))
    }

    @Test
    fun keepsSpacesOutsideFormatting() {
        assertEquals("<p>a <b>b</b> c</p>", HtmlText.sanitize("<p>a<b> b </b>c</p>"))
        assertEquals("<p>a b</p>", HtmlText.sanitize("<p>a\n   \t b</p>"))
    }

    @Test
    fun plainTextBecomesParagraphs() {
        assertEquals("<p>One<br/>two</p><p>Three &amp; four</p>", HtmlText.fromPlainText("One\ntwo\n\nThree & four"))
        assertEquals("", HtmlText.fromPlainText("  \n\n "))
    }

    @Test
    fun cleaningTwiceChangesNothing() {
        val messy = "<div><h2>A</h2>text <em>x</em><ul><li>1<li>2</ul><blockquote><p>q</p></blockquote></div>"
        val once = HtmlText.sanitize(messy)
        assertEquals("<p><b>A</b></p><p>text <i>x</i></p><ul><li>1</li><li>2</li></ul><blockquote><p>q</p></blockquote>", once)
        assertEquals(once, HtmlText.sanitize(once))
    }
}
