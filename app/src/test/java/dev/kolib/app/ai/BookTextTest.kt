package dev.kolib.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTextTest {
    private val words: (String) -> Int = { t -> t.split(Regex("\\s+")).count { it.isNotEmpty() } }

    @Test
    fun aChaptersParagraphsAreReadFromItsFile() {
        val xhtml = """<html><head><title>One</title><style>p { color: red }</style></head><body>
            <h1>PROLOGUE</h1>
            <p><span class="sc">“We should start back,”</span> Gared urged as the woods
               began to grow dark.</p>
            <div><p>“Do the dead frighten you?”<br/>Ser Waymar asked.</p></div>
            <p>&nbsp;</p><p>Caf&#233; &amp; bar.</p></body></html>"""
        assertEquals(
            listOf("PROLOGUE", "“We should start back,” Gared urged as the woods began to grow dark.", "“Do the dead frighten you?”", "Ser Waymar asked.", "Café & bar."),
            BookText.paragraphs(xhtml),
        )
    }

    @Test
    fun whatsBeenReadOfAChapterEndsAtAParagraph() {
        val paragraphs = listOf("a".repeat(100), "b".repeat(100), "c".repeat(100), "d".repeat(100))
        assertEquals(emptyList<String>(), BookText.upTo(paragraphs, 0f))
        assertEquals(paragraphs.take(2), BookText.upTo(paragraphs, 0.5f))
        // Most of the way through a paragraph counts it as read.
        assertEquals(paragraphs.take(3), BookText.upTo(paragraphs, 0.7f))
        assertEquals(paragraphs, BookText.upTo(paragraphs, 1f))
    }

    @Test
    fun whatsBeenReadEndsWithThePageOnTheScreen() {
        val paragraphs = listOf(
            "The ferry left Harrow Point an hour late.",
            "Old Tobias Wren met her on the quay, his coat\u00ADbuttoned to the chin, and took her bag without a word.",
            "They walked up through the village in the rain.",
            "At the top, the old house brooded over the valley, its windows dark except for one.",
            "Mirren stopped at the gate.",
        )
        // The screen ends part-way through the fourth paragraph, as the page shows it (a line to a paragraph).
        val screen = "took her bag without a word.\nThey walked up through the village in the rain.\nAt the top, the old house brooded over"
        val read = BookText.upToScreen(paragraphs, screen, 0.5f)!!
        assertEquals(paragraphs.take(3) + "At the top, the old house brooded over", read)
        // Up to its start instead: what came before the page, ending part-way through the second paragraph.
        val before = BookText.upToScreen(paragraphs, screen, 0.5f, atEnd = false)!!
        assertEquals(2, before.size)
        assertTrue(before.last().startsWith("Old Tobias Wren met her on the quay") && before.last().endsWith("and"))
        // A screen whose text isn't in the chapter: not found.
        assertNull(BookText.upToScreen(paragraphs, "Something else altogether, from another book.", 0.5f))
        assertNull(BookText.upToScreen(paragraphs, "", 0.5f))
    }

    @Test
    fun wordsThatComeTwiceAreFoundNearWhereTheReaderIs() {
        val paragraphs = (1..10).map { n -> "Chapter text $n goes on a while. She blew the whistle and waited." }
        val screen = "goes on a while. She blew the whistle and waited."
        // Near the start, the first time; near the end, the last.
        assertEquals(1, BookText.upToScreen(paragraphs, screen, 0.05f)!!.size)
        assertEquals(10, BookText.upToScreen(paragraphs, screen, 0.97f)!!.size)
        assertEquals(6, BookText.upToScreen(paragraphs, screen, 0.55f)!!.size)
    }

    @Test
    fun aLongChapterIsCutIntoPartsThatFit() {
        val paragraphs = (1..10).map { n -> (1..30).joinToString(" ") { "word$n" } }
        val parts = BookText.chunks(paragraphs, 100, words)
        assertEquals(4, parts.size)
        assertTrue(parts.all { words(it) <= 100 })
        // Nothing lost, nothing out of order.
        assertEquals(paragraphs.joinToString(" "), parts.joinToString(" ") { it.replace("\n", " ") })
        // One paragraph longer than a part is cut at its sentences.
        val long = (1..12).joinToString(" ") { "Sentence number $it has six words." }
        val cut = BookText.chunks(listOf(long), 20, words)
        assertTrue(cut.size >= 4)
        assertTrue(cut.all { words(it) <= 20 && it.trim().endsWith(".") })
        assertEquals(listOf("short one"), BookText.chunks(listOf("short one"), 100, words))
        assertEquals(emptyList<String>(), BookText.chunks(emptyList(), 100, words))
    }

    @Test
    fun aWordsSentenceIsFoundInItsParagraph() {
        val short = "At the top, the old house brooded over the valley."
        assertEquals(short, BookText.sentenceWith(short, "brooded"))
        val long = "The road climbed out of the village in slow, patient bends. ".repeat(6) +
            "At the top, the old house brooded over the valley, its windows dark except for one. " + "A lamp burned in the turret. ".repeat(6)
        assertEquals("At the top, the old house brooded over the valley, its windows dark except for one.", BookText.sentenceWith(long, "brooded"))
        // A few words of speech say little alone: the sentence before comes with them.
        assertEquals("He looked up at the dark window. “It broods,” he said.", BookText.sentenceWith("The lamp was out. He looked up at the dark window. “It broods,” he said.", "broods"))
        // Not found in what the page gave (a word across two paragraphs): the start of it.
        assertEquals(short, BookText.sentenceWith(short, "missing"))
    }

    @Test
    fun aQuestionsNamesAndUncommonWordsAreWhatsLookedFor() {
        assertEquals(listOf("Ansel"), BookText.keywords("Who is Ansel and what happened to him?"))
        assertEquals(listOf("lamp", "keep", "going", "out"), BookText.keywords("Why did the lamp keep going out?"))
        assertEquals(emptyList<String>(), BookText.keywords("What has happened so far?"))
    }

    @Test
    fun thePassagesThatAnswerAQuestionAreFound() {
        val one = listOf(
            "The ferry left Harrow Point an hour late.",
            "Old Tobias Wren met her on the quay.",
            "“If it comes,” he said, “don't look at it straight.”",
            "“My brother Ansel looked at it straight, and he walked into the sea.”",
            "“If what comes?”",
            "But the old man had already started down the stair.",
        )
        val two = listOf("The wind dropped a little before midnight.", "Mirren thought of Ansel and kept her eyes on the flame.", "The sea went quiet.")
        val found = BookText.passages(listOf(0 to one, 1 to two), "Who is Ansel?", 200, words)
        val texts = found.map { it.text }
        // Where he first comes up, with the lines either side of it (who's speaking; what's said next), and where he comes up again.
        assertTrue(texts.contains(one[3]) && texts.contains(one[2]) && texts.contains(one[4]))
        assertTrue(texts.contains(two[1]))
        assertFalse(texts.contains(one[0]))
        // In the book's order.
        assertEquals(found.sortedWith(compareBy({ it.chapter }, { it.paragraph })), found)
        // Only as much as there's room for: the best first.
        val few = BookText.passages(listOf(0 to one, 1 to two), "Who is Ansel?", 16, words)
        assertEquals(listOf(one[3]), few.map { it.text })
        // Nothing in the book matches: nothing found.
        assertEquals(emptyList<BookText.Passage>(), BookText.passages(listOf(0 to one), "Who is Zebedee?", 200, words))
    }
}
