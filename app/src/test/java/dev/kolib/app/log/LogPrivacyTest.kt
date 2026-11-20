package dev.kolib.app.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The log keeps the names of people's books out of it. */
class LogPrivacyTest {

    @After
    fun tearDown() {
        LogPrivacy.know("library", emptyList())
        LogPrivacy.know("kobo", emptyList())
    }

    @Test
    fun knownTitlesAndAuthorsAreHiddenWhereverTheyAre() {
        LogPrivacy.know("library", listOf("The Way of Kings", "Brandon Sanderson"))
        val m = LogPrivacy.clean("Sent The Way of Kings by Brandon Sanderson to the Kobo.")
        assertFalse(m, m.contains("Way of Kings"))
        assertFalse(m, m.contains("Sanderson"))
        assertTrue(m, m.startsWith("Sent ‹book "))
        // The same book is the same code each time.
        assertEquals(LogPrivacy.code("The Way of Kings"), LogPrivacy.code("the way of kings"))
    }

    @Test
    fun quotedNamesAndBookFilesAreHiddenEvenWhenNotKnown() {
        val m = LogPrivacy.clean("Couldn't take in 'Harry's Game' from “A Secret Shelf” at Books/Some One/My Diary.kepub.epub")
        assertFalse(m, m.contains("Harry"))
        assertFalse(m, m.contains("Secret Shelf"))
        assertFalse(m, m.contains("My Diary"))
        // What the message says is kept: the apostrophe of "Couldn't" isn't taken for a quote, nor the file's kind.
        assertTrue(m, m.startsWith("Couldn't take in '‹book "))
        assertTrue(m, m.endsWith(".kepub.epub"))
        val f = LogPrivacy.clean("Couldn't read secret-plans.pdf (No such file)")
        assertFalse(f, f.contains("secret-plans"))
        assertTrue(f, f.endsWith(".pdf (No such file)"))
    }

    @Test
    fun theAppsOwnNamesStay() {
        assertEquals("Uncaught exception on thread 'main'", LogPrivacy.clean("Uncaught exception on thread 'main'"))
        assertEquals("KoLib 3.0 (103), Android 16 (API 36)", LogPrivacy.clean("KoLib 3.0 (103), Android 16 (API 36)"))
        // A known name inside a longer word isn't touched.
        LogPrivacy.know("library", listOf("Ender"))
        assertEquals("Rendering the page", LogPrivacy.clean("Rendering the page"))
    }

    @Test
    fun theAppsOwnWordsStayEvenWhenABookIsCalledByOne() {
        LogPrivacy.know("library", listOf("KoLib", "Unknown", "Kobo", "Persuasion"))
        val out = LogPrivacy.clean("KoLib started. Unknown error with the Kobo while sending Persuasion")
        assertTrue(out, out.startsWith("KoLib started. Unknown error with the Kobo while sending ‹book "))
        LogPrivacy.know("library", emptyList())
    }

    @Test
    fun namesInOtherAlphabetsAreHiddenOnlyAsWholeWords() {
        LogPrivacy.know("kobo", listOf("Мастер и Маргарита", "Булгаков", "三体", "刘慈欣"))
        val m = LogPrivacy.clean("Sent Мастер и Маргарита (Булгаков) and 三体; Булгакова stays")
        assertFalse(m, m.contains("Маргарита"))
        assertFalse(m, m.contains("三体"))
        assertTrue(m, m.endsWith("; Булгакова stays"))
        // Chinese, Japanese and Korean have no spaces between words: their names are found anywhere.
        val c = LogPrivacy.clean("已发送三体（刘慈欣）")
        assertFalse(c, c.contains("三体"))
        assertFalse(c, c.contains("刘慈欣"))
    }

    @Test
    fun aBigLibraryDoesntSlowTheLog() {
        LogPrivacy.know("library", (1..20_000).map { "Book number $it of the shelf" })
        val line = "Sending Book number 19999 of the shelf to the Kobo, then the rest of the books and the covers, and checking the database"
        val start = System.nanoTime()
        repeat(2_000) { LogPrivacy.clean(line) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
        assertTrue(LogPrivacy.clean(line).startsWith("Sending ‹book "))
    }
}
