package dev.kobolibrary.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.convert.TestSamples
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubText
import dev.kobolibrary.app.reader.PdfPlace
import dev.kobolibrary.app.reader.ReaderStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** Books added as PDFs, MOBIs, Word files and so on: made into EPUBs, with the file they came as kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryFormatsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "formats-" + UUID.randomUUID()
    private val root = File(context.filesDir, name)

    private fun library() = Library(context, root, "$name.db")

    private val story = buildString {
        append("The Lighthouse Keeper\n\nby Mara Ellis\n\n\n")
        for (c in 1..3) {
            append("CHAPTER $c\n\n")
            repeat(12) { p -> append("Paragraph $p of chapter $c. The lamp turned slowly over the dark water, and the keeper wrote it all down in his book.\n\n") }
        }
    }

    private fun words(file: File) = EpubFile.open(file).use { epub ->
        epub.contentDocuments().sumOf { EpubText.countWords(EpubText.plainText(epub.readText(it).orEmpty())) }
    }

    @Test
    fun aTextFileIsMadeIntoAnEpubAndKeptBesideIt() = runBlocking {
        val lib = library().apply { load() }
        val added = lib.import("The Lighthouse Keeper.txt", { story.byteInputStream() }, BookSource.FILE)
        assertTrue(added.toString(), added is ImportOutcome.Added)
        val book = (added as ImportOutcome.Added).book
        assertEquals("TXT", book.sourceFormat)
        assertEquals("Text file, made into an EPUB", book.addedAs)
        assertFalse(book.isPdf)
        assertEquals("source.txt", lib.sourceFile(book.id)?.name)
        assertEquals(story, lib.sourceFile(book.id)!!.readText())
        assertTrue(words(lib.epubFile(book.id)) > 500)
        assertTrue(book.words!! > 500)

        // Kept when the library is opened again.
        val again = library().apply { load() }
        assertEquals("TXT", again.book(book.id)?.sourceFormat)
    }

    @Test
    fun somethingThatIsntABookIsTurnedAway() = runBlocking {
        val lib = library().apply { load() }
        val junk = lib.import("photo.bin", { ByteArray(500) { (it * 31).toByte() }.inputStream() }, BookSource.FILE)
        assertTrue(junk.toString(), junk is ImportOutcome.Failed)
        assertEquals("KoLib can't open this kind of file.", (junk as ImportOutcome.Failed).reason)
        assertTrue(lib.books.value.isEmpty())
    }

    @Test
    fun aPdfIsKeptToReadAndSendAsItIs() = runBlocking {
        val pdf = TestSamples.file("alice-objstm.pdf")
        assumeTrue(pdf != null)
        val lib = library().apply { load() }
        val added = lib.import("alice.pdf", { pdf!!.inputStream() }, BookSource.FILE) as ImportOutcome.Added
        val book = added.book
        assertEquals("PDF", book.sourceFormat)
        assertTrue(book.isPdf)
        assertFalse(book.isScan)
        assertTrue(book.goesAsPdf(pdfs = true))
        assertFalse(book.goesAsPdf(pdfs = false))
        assertArrayEquals(pdf!!.readBytes(), lib.sourceFile(book.id)!!.readBytes())
        assertEquals("source.pdf", lib.sourceFile(book.id)!!.name)
        assertTrue(words(lib.epubFile(book.id)) > 20_000)

        // The same PDF again: kept aside, with its PDF, to replace the book or be added as well.
        val again = lib.import("alice (1).pdf", { pdf.inputStream() }, BookSource.FILE, hold = true)
        assertTrue(again.toString(), again is ImportOutcome.AlreadyThere)
        val held = (again as ImportOutcome.AlreadyThere).held!!
        val heldSource = lib.heldSource(held)
        assertNotNull(heldSource)
        assertEquals("PDF", heldSource!!.second)
        val second = lib.addAnyway(held, "alice (1).pdf")
        assertTrue(second.id != book.id)
        assertEquals("PDF", second.sourceFormat)
        assertArrayEquals(pdf.readBytes(), lib.sourceFile(second.id)!!.readBytes())
        lib.discardHeld(held)
        assertFalse(held.exists())
        assertFalse(heldSource.first.exists())

        // A copy of the book has its PDF too.
        val copy = lib.duplicate(book.id)
        assertEquals("PDF", copy.sourceFormat)
        assertNotNull(lib.sourceFile(copy.id))

        // Replaced by a book that came as a text file: the PDF goes, the text file comes.
        val txt = lib.import("The Lighthouse Keeper.txt", { story.byteInputStream() }, BookSource.FILE, hold = true)
        val asNew = (txt as? ImportOutcome.Added)?.book
        assertNotNull(asNew)
        lib.delete(listOf(asNew!!.id))
        val txtAgain = lib.import("The Lighthouse Keeper.txt", { story.byteInputStream() }, BookSource.FILE) as ImportOutcome.Added
        assertEquals("TXT", txtAgain.book.sourceFormat)
    }

    @Test
    fun replacingABookTakesTheNewFilesOriginal() = runBlocking {
        val pdf = TestSamples.file("alice-objstm.pdf")
        val docx = TestSamples.file("alice.docx")
        assumeTrue(pdf != null && docx != null)
        val lib = library().apply { load() }
        val book = (lib.import("alice.pdf", { pdf!!.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        // The Word file is the same book, so it's held to ask what to do.
        val held = (lib.import("alice.docx", { docx!!.inputStream() }, BookSource.FILE, hold = true) as ImportOutcome.AlreadyThere).held!!
        val replaced = lib.replaceWith(book.id, held, "alice.docx")
        assertEquals("DOCX", replaced.sourceFormat)
        assertEquals("source.docx", lib.sourceFile(book.id)?.name)
        assertFalse(replaced.isPdf)
    }

    @Test
    fun theReaderRemembersThePlaceInAPdf() {
        val dir = File(root, "book").apply { mkdirs() }
        assertNull(ReaderStore.readHereOf(dir))
        ReaderStore.savePdfPlace(dir, PdfPlace(page = 24, pages = 100, at = 1000))
        assertEquals(0.24f, ReaderStore.readHereOf(dir)!!.progress, 0.001f)
        assertEquals(24, ReaderStore.pdfPlace(dir)!!.page)
        // The last page is the end of the book.
        ReaderStore.savePdfPlace(dir, PdfPlace(page = 99, pages = 100, at = 2000))
        assertEquals(1f, ReaderStore.readHereOf(dir)!!.progress, 0.001f)
        // Marked as finished, then not started: the PDF opens at its start again.
        ReaderStore.markFinished(dir, now = 3000)
        assertTrue(ReaderStore.readHereOf(dir)!!.finished)
        assertNull(ReaderStore.pdfPlace(dir))
        ReaderStore.savePdfPlace(dir, PdfPlace(page = 10, pages = 100, at = 4000))
        assertFalse("reading it again", ReaderStore.readHereOf(dir)!!.finished)
        ReaderStore.markUnread(dir)
        assertNull(ReaderStore.readHereOf(dir))
        // Read as text, the choice is kept when the place is let go.
        ReaderStore.savePdfPlace(dir, PdfPlace(page = 5, pages = 100, at = 5000, asText = true))
        ReaderStore.markUnread(dir)
        assertTrue(ReaderStore.pdfPlace(dir)!!.asText)
        assertNull(ReaderStore.readHereOf(dir))
    }
}
