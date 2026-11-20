package dev.kolib.app.ui.setup

import dev.kolib.app.L10n
import dev.kolib.app.epub.BookStructure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The reading setup's example book: a book the reader can open, in the language KoLib is shown in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleBookTest {

    @After
    fun english() = L10n.use("en", emptyMap())

    private fun table(lang: String): Map<String, String> {
        val out = HashMap<String, String>()
        val o = org.json.JSONObject(File("src/main/assets/i18n/$lang.json").readText(Charsets.UTF_8))
        for (k in o.keys()) out[k] = o.getString(k)
        return out
    }

    @Test
    fun itIsABookWithOneChapterByItsName() {
        val files = ExampleBook.files()
        val book = BookStructure(files)
        assertEquals(listOf(ExampleBook.CHAPTER), book.chapters.map { it.path })
        assertEquals("The Last Ferry", book.chapters.single().title)
        assertEquals(listOf("The Last Ferry"), book.toc.map { it.title })
        assertTrue(book.opfText.contains("<dc:title>The Lamp in the Window</dc:title>"))
        val text = files.text(ExampleBook.CHAPTER)!!
        // Its number and its name on lines of their own, for the reader to set as a printed book has them.
        assertTrue(text.contains("<h2>I</h2>\n<h3>The Last Ferry</h3>"))
        assertEquals(11, Regex("<p>").findAll(text).count())
        assertTrue(text.contains("with <i>three days of water and no map</i>, and"))
        assertTrue(text.contains("“You’ll ruin your eyes,” her grandmother said"))
        // Long enough for pages to turn, on a phone and on a tablet's two pages.
        val words = text.substringAfter("</h3>").replace(Regex("<[^>]+>"), " ").split(Regex("\\s+")).count { it.isNotBlank() }
        assertTrue("$words words", words in 480..600)
        // Every file it names is there.
        for (name in files.names()) assertTrue(name, files.has(name))
    }

    @Test
    fun itIsInEveryLanguageKoLibIs() {
        for (lang in listOf("fr", "de", "es", "it", "nl", "pt-BR", "ro", "ru", "ja", "ko", "zh-Hans")) {
            L10n.use(lang, table(lang))
            val files = ExampleBook.files()
            val book = BookStructure(files)
            val text = files.text(ExampleBook.CHAPTER)!!
            assertTrue("$lang: the book's language", text.contains("lang=\"$lang\""))
            assertFalse("$lang: its title is translated", ExampleBook.title == "The Lamp in the Window")
            assertEquals("$lang: the chapter's name", ExampleBook.chapterTitle, book.chapters.single().title)
            assertEquals("$lang: every paragraph", 11, Regex("<p>").findAll(text).count())
            assertFalse("$lang: none left in English", text.contains("Mara carried her book"))
            // The italics come through as italics, and nothing else as markup.
            assertEquals("$lang: the italics", 1, Regex("<i>[^<]+</i>").findAll(text).count())
            assertFalse("$lang: stray markup", text.contains("&lt;"))
            // It still parses as a page.
            dev.kolib.app.epub.Xml.parse(text)
        }
    }

    @Test
    fun wordsThatWouldBreakThePageAreWrittenOut() {
        L10n.use("xx", mapOf("The Last Ferry" to "Fish & Chips <now>"))
        val files = ExampleBook.files()
        val text = files.text(ExampleBook.CHAPTER)!!
        assertTrue(text.contains("<h3>Fish &amp; Chips &lt;now&gt;</h3>"))
        assertEquals("Fish & Chips <now>", BookStructure(files).chapters.single().title)
    }
}
