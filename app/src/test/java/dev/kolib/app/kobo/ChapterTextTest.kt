package dev.kolib.app.kobo

import dev.kolib.app.epub.KepubConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTextTest {
    private val ch = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head>
<body>
  <h1>Two</h1>
  <p>The sea was grey. Gulls cried over it.</p>
  <p>The tide came in again. It always did.</p>
</body></html>"""

    @Test
    fun aSentenceOnTheKoboAndLettersInKoLibAreTheSamePlace() {
        val kepub = KepubConverter.convert(ch)
        val t = ChapterText(kepub)
        val tide = t.find("The tide came in again.", 0)!!
        val (span, offset) = t.spanAt(tide.start, tide.startOffset)!!
        assertEquals(0, offset)
        assertEquals(tide.start to tide.startOffset, t.atSpan(span, 0))
        // Counted as the Kobo's place is counted when KoLib takes it.
        assertEquals(ChapterLetters(kepub).lettersBeforeId(span), t.lettersAt(tide.start, tide.startOffset))
        val letters = t.lettersAt(tide.start, tide.startOffset)
        assertEquals(tide.start to tide.startOffset, t.atLetters(letters))
        // The end of the sentence: the same span, all of it.
        val (endSpan, endOffset) = t.spanAt(tide.end, tide.endOffset)!!
        assertEquals(span, endSpan)
        assertEquals("The tide came in again.".length, endOffset)
        // Part way through a sentence.
        val came = t.find("came", 0)!!
        assertEquals(span to "The tide ".length, t.spanAt(came.start, came.startOffset))
    }

    @Test
    fun wordsAreFoundAsTheReaderFindsThem() {
        val t = ChapterText(ch)
        // Case and spacing aside, as reader.js's findText() goes.
        val second = t.find("the", 1)!!
        assertEquals("The", t.textOf(second))
        assertEquals(1, t.occurrenceBefore("the", second.start, second.startOffset))
        assertNotNull(t.find("grey.\n   Gulls", 0))
        assertNull(t.find("the", 9))
        // Nearest a place: the second "The" when that's nearer.
        val near = t.findNear("the", second.start to second.startOffset + 2)!!
        assertEquals(second, near)
    }

    @Test
    fun adobePointsCountEveryNodeAndTheBytesBefore() {
        val x = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head>
<body>
<p>Caf&#233; one.</p>
<p>Second <b>bold</b> rest é here.</p>
</body></html>"""
        val t = ChapterText(x)
        // html is /1; in it head (1), a line break (2), body (3); in body a line break (1), p (2), a line break (3), p (4);
        // in that p, "Second " (1), b (2), " rest é here." (3).
        val rest = t.find("rest", 0)!!
        assertEquals("/1/3/4/3:1", t.adobePoint(rest.start, rest.startOffset))
        assertEquals(rest.start to rest.startOffset, t.atAdobePoint("/1/3/4/3:1"))
        // After a letter two bytes long.
        val here = t.find("here", 0)!!
        assertEquals("/1/3/4/3:9", t.adobePoint(here.start, here.startOffset))
        assertEquals(here.start to here.startOffset, t.atAdobePoint("/1/3/4/3:9"))
        // An element: its first text.
        val bold = t.find("bold", 0)!!
        assertEquals(bold.start to 0, t.atAdobePoint("/1/3/4/2"))
    }

    @Test
    fun aPointReadTheWayEpubCfisAreStillFindsItsPlace() {
        // No space between the elements, and steps an EPUB CFI's way (even for elements): not Adobe's own counting.
        val x = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head><body><div><p>Hello there.</p><p>Second one here.</p></div></body></html>"""
        val t = ChapterText(x)
        val at = t.atAdobePoint("/1/4/2/4/1:7")!!
        assertEquals("one here.", t.pieces[at.first].text.substring(at.second))
    }
}
