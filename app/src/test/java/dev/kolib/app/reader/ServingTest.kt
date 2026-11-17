package dev.kolib.app.reader

import dev.kolib.app.epub.TestBooks
import dev.kolib.app.epub.XmlCheck
import dev.kolib.app.ui.textedit.TextEditState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingTest {

    @Test
    fun wellFormedXhtmlIsServedAsXhtml() {
        val (type, text) = BookServer.prepare(TestBooks.CH1.replace("&#160;", "&nbsp;"), "<!--injected-->")
        assertEquals("application/xhtml+xml", type)
        assertTrue(text.contains("<p>&#160;</p>"))
        assertTrue(text.contains("<!--injected--></head>"))
        assertNull(XmlCheck.check(text))
    }

    @Test
    fun anythingElseIsServedAsHtmlInStandardsMode() {
        val noNamespace = "<?xml version=\"1.0\"?>\n<html><head><title>x</title></head><body><p>Hi</p></body></html>"
        val (type, text) = BookServer.prepare(noNamespace, "<!--i-->")
        assertEquals("text/html", type)
        assertTrue(text.startsWith("<!DOCTYPE html>\n<html>"))
        assertTrue(text.contains("<!--i--></head>"))
        val broken = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>One<p>Two</body></html>"
        val (type2, text2) = BookServer.prepare(broken, "<!--i-->")
        assertEquals("text/html", type2)
        assertTrue(text2.contains("<head><!--i--></head>"))
    }

    @Test
    fun addressesRoundTrip() {
        val server = BookServer::class
        val url = BookServer.BOOK + "OEBPS/Text/Chapter%20One.xhtml#n%C3%A91"
        assertEquals("OEBPS/Text/Chapter One.xhtml" to "né1", BookServer.pathOf(url))
        assertEquals("a%20b/%C3%A9.xhtml", BookServer.encode("a b/é.xhtml").replace("%2F", "/"))
        assertFalse(server.simpleName.isNullOrEmpty())
    }

    @Test
    fun theEditorsBodyGoesBackIntoTheChapter() {
        val chapter = TestBooks.CH1
        val fromBrowser = "\n  <h2 xmlns=\"http://www.w3.org/1999/xhtml\" id=\"c1\">Chapter 1</h2>\n" +
            "  <p xmlns=\"http://www.w3.org/1999/xhtml\">New text.</p>\n" +
            "  <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>\n"
        val out = TextEditState.withBody(chapter, fromBrowser)!!
        assertTrue(out.startsWith(chapter.substring(0, chapter.indexOf("<body class=\"chapter\">") + "<body class=\"chapter\">".length)))
        assertTrue(out.contains("<p>New text.</p>"))
        assertTrue(out.contains("<svg xmlns=\"http://www.w3.org/2000/svg\""))
        assertFalse(out.contains("Rose walked"))
        assertTrue(out.endsWith("</body>\n</html>\n"))
        assertNull(XmlCheck.check(out, htmlEntities = true))
    }
}
