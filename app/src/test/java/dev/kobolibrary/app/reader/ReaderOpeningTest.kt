package dev.kobolibrary.app.reader

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.TestBooks
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.reader.BookReader
import dev.kobolibrary.app.ui.theme.ThemeChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** Opening a library book in the reader, as the app does: with its saved place, bookmarks and highlights. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderOpeningTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private fun garden(): Pair<Library, LibraryBook> {
        val lib = Library(context, File(context.filesDir, "open-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        return lib to lib.books.value.first()
    }

    private val place = ReaderPosition("OEBPS/text/ch2.xhtml", "1/0:0", 0.5f, 0.4f, "The rain fell", "Chapter 2", 1_794_300_000_000L)

    private fun readBefore(lib: Library, book: LibraryBook) {
        val note = Annotation(path = "OEBPS/text/ch1.xhtml", start = "1/0:0", end = "1/0:20", text = "Rose walked into the garden.",
            color = HighlightColor.YELLOW, note = "A name and a flower.", chapterTitle = "Chapter 1: Rose", bookProgress = 0.1f, created = 1_794_300_000_000L)
        ReaderStore(lib.bookDir(book.id)).save(ReaderState(place, listOf(place), listOf(note)))
    }

    @Test
    fun aBookReadBeforeOpensWhereItWasLeft() {
        val (lib, book) = garden()
        readBefore(lib, book)
        EpubFile.open(lib.epubFile(book.id)).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), book.title, "en", ReaderStore(lib.bookDir(book.id)), ReaderSettings(), context.assets)
            assertEquals(place, c.position)
            assertEquals(listOf(place), c.bookmarks)
            assertEquals(listOf("A name and a flower."), c.annotations.map { it.note })
        }
    }

    @Test
    fun theLibrarysReaderOpensABook() {
        val (lib, book) = garden()
        readBefore(lib, book)
        val settings = ReaderSettingsStore(context)
        AppLog.clear()
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { BookReader(book, lib, settings, onClose = {}, onEdit = null) } }
        // Either the page with its margins, or the "couldn't be opened" screen.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Back").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("%", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val problems = AppLog.snapshot().filter { it.level == AppLog.Level.ERROR }.map { it.format() }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue(compose.onAllNodesWithText("Back").fetchSemanticsNodes().isEmpty())
    }

    /** Each chapter's number and ornament in a file of its own, as in some omnibus editions. */
    private fun openings(): File {
        val words = (1..400).joinToString(" ") { "word$it" }
        fun page(t: String, body: String) = dev.kobolibrary.app.epub.BookAssembly.chapterPage(t, "en", body, styles = emptyList(), heading = "h2")
        val parts = listOf(
            dev.kobolibrary.app.epub.BookAssembly.Part("OEBPS/text/o1.xhtml", "application/xhtml+xml", page("Chapter 1", "").toByteArray()),
            dev.kobolibrary.app.epub.BookAssembly.Part("OEBPS/text/c1.xhtml", "application/xhtml+xml", page("The Hook", "<p>$words</p>").toByteArray()),
            dev.kobolibrary.app.epub.BookAssembly.Part("OEBPS/text/o2.xhtml", "application/xhtml+xml", page("Chapter 2", "").toByteArray()),
            dev.kobolibrary.app.epub.BookAssembly.Part("OEBPS/text/c2.xhtml", "application/xhtml+xml", page("Strangers", "<p>$words</p>").toByteArray()),
        )
        val spine = parts.map { dev.kobolibrary.app.epub.BookAssembly.SpineEntry(it.path) }
        val toc = listOf(dev.kobolibrary.app.epub.TocNode("1 The Hook", "OEBPS/text/o1.xhtml", null), dev.kobolibrary.app.epub.TocNode("2 Strangers", "OEBPS/text/o2.xhtml", null))
        val f = File(context.cacheDir, "openings-${UUID.randomUUID()}.epub")
        dev.kobolibrary.app.epub.BookAssembly.write(f, dev.kobolibrary.app.epub.BookMetadata(title = "Openings"), parts, spine, toc)
        return f
    }

    @Test
    fun aChaptersOpeningIsShownWithItsChapter() {
        EpubFile.open(openings()).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "Openings", "en", null, ReaderSettings(), context.assets)
            assertTrue(c.isOpening(0) && !c.isOpening(1) && c.isOpening(2))
            // Opening the first chapter's opening opens its text, which shows it; titled as the contents title it.
            c.open(0, org.json.JSONObject().put("type", "start"))
            assertEquals(1, c.chapterIndex)
            assertEquals("1 The Hook", c.chapterTitle)
            assertTrue("OEBPS/text/o1.xhtml" in c.contentsPaths)
            // Nothing before it: its opening is already on screen.
            assertTrue(!c.canMove(-1))
            // On to the next chapter (past its opening), and back.
            c.nextChapter()
            assertEquals(3, c.chapterIndex)
            assertEquals("2 Strangers", c.chapterTitle)
            c.previousChapter()
            assertEquals(1, c.chapterIndex)
            // Switched off, the openings are pages of their own again.
            c.applySettings(c.settings.copy(joinOpenings = false))
            assertTrue(!c.isOpening(0))
            c.open(0, org.json.JSONObject().put("type", "start"))
            assertEquals(0, c.chapterIndex)
        }
    }
}
