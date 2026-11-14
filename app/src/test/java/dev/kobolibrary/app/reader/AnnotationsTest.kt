package dev.kobolibrary.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnnotationsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val chapters = listOf("ch1.xhtml", "ch2.xhtml", "ch3.xhtml")
    private fun order(path: String) = chapters.indexOf(path)

    private val a = Annotation(path = "ch2.xhtml", start = "3/1:5", end = "3/1:40", text = "The gate was\n shut.", occurrence = 1,
        color = HighlightColor.BLUE, note = "Why shut?", chapterTitle = "Two", created = 100)
    private val b = Annotation(path = "ch1.xhtml", start = "10/0:0", end = "10/0:9", text = "Rose", chapterTitle = "One", created = 300)
    private val c = Annotation(path = "ch1.xhtml", start = "2/0:7", end = "2/0:30", text = "garden", style = HighlightStyle.UNDERLINE, chapterTitle = "One", created = 200)

    @Test
    fun highlightsAndNotesAreKeptWithTheBook() {
        val dir = tmp.newFolder("book")
        val pos = ReaderPosition("ch2.xhtml", "3/1:5", 0.4f, 0.5f)
        ReaderStore(dir).save(ReaderState(pos, emptyList(), listOf(a, b)))
        val back = ReaderStore(dir).load()
        assertEquals(listOf(a, b), back.annotations)
        assertEquals("ch2.xhtml", back.position?.path)
    }

    @Test
    fun theyAreListedInReadingOrder() {
        // Chapter by chapter, and within a chapter by where they are ("2/…" before "10/…"), not when made.
        assertEquals(listOf(c, b, a), Annotation.ordered(listOf(a, b, c), ::order))
        assertTrue(AnchorOrder.key("2/0:7") < AnchorOrder.key("10/0:0"))
        // Inside a child comes before a later point in its parent.
        assertTrue(AnchorOrder.key("3/1/0:0") < AnchorOrder.key("3/1:5"))
    }

    @Test
    fun exportedAsTextByChapterWithNotes() {
        val text = Annotation.export("The Garden", "Ada Lane", listOf(a, b, c), ::order, "13 Nov 2026")
        val expected = """
            The Garden — Ada Lane
            Highlights and notes, 13 Nov 2026

            ## One

            > garden

            > Rose

            ## Two

            > The gate was shut.
            Note: Why shut?
        """.trimIndent() + "\n"
        assertEquals(expected, text)
    }
}
