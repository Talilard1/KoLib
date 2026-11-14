package dev.kobolibrary.app.ui.textedit

import android.content.Context
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.FindReplace
import dev.kobolibrary.app.epub.TestBooks
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.reader.BookServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** The editor's Kotlin side, with the page's script standing in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextEditStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun setUp(): Triple<Library, String, TextEditState> {
        val lib = Library(context, File(context.filesDir, "te-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        val id = lib.books.value.first().id
        val state = TextEditState(id, "The Garden", lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch3.xhtml")
        return Triple(lib, id, state)
    }

    private fun bridge(view: WebView, name: String): Any = shadowOf(view).getJavascriptInterface(name)!!

    private fun call(target: Any, method: String, vararg args: Any) {
        target.javaClass.methods.first { it.name == method && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(target, *args)
        idle()
    }

    @Test
    fun typingInThePageChangesTheChapterAndCanBeUndone() {
        val (lib, id, state) = setUp()
        val view = state.createView(context, dark = false)
        idle()
        assertTrue(shadowOf(view).lastLoadedUrl.startsWith(BookServer.BOOK + "OEBPS/text/ch3.xhtml"))
        val kle = bridge(view, "KLE")
        assertTrue((kle.javaClass.methods.first { it.name == "start" }.invoke(kle) as String).contains("\"whole\":false"))
        call(kle, "changed", "\n  <h2 xmlns=\"http://www.w3.org/1999/xhtml\" id=\"c3\">Chapter 3</h2>\n  <p xmlns=\"http://www.w3.org/1999/xhtml\">Rewritten.</p>\n")
        assertTrue(state.dirty)
        val text = state.edit.text("OEBPS/text/ch3.xhtml")!!
        assertTrue(text.contains("<p>Rewritten.</p>"))
        assertTrue(text.contains("<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/style.css\"/>"))
        assertEquals("Typing in “Chapter 3”", state.undoLabel)

        state.undo()
        idle()
        assertFalse(state.dirty)
        assertTrue(state.edit.text("OEBPS/text/ch3.xhtml")!!.contains("Back in"))
        state.redo()
        idle()

        // Saving checks the book, keeps the original and updates the library.
        state.save()
        state.awaitWork()
        idle()
        assertFalse(state.dirty)
        assertTrue(lib.hasOriginalText(id))
        EpubFile.open(lib.epubFile(id)).use { assertTrue(it.readText("OEBPS/text/ch3.xhtml")!!.contains("Rewritten.")) }
        assertTrue(lib.book(id)!!.modifiedAt > lib.book(id)!!.addedAt)

        // And it can all be put back.
        var reverted = false
        state.revert { reverted = true }
        state.awaitWork()
        idle()
        assertTrue(reverted)
        assertFalse(lib.hasOriginalText(id))
        EpubFile.open(lib.epubFile(id)).use { assertTrue(it.readText("OEBPS/text/ch3.xhtml")!!.contains("Back in")) }
        state.close()
    }

    @Test
    fun codeWithAMistakeIsntTakenIn() {
        val (_, _, state) = setUp()
        state.setCode(true)
        val view = state.createView(context, dark = true)
        idle()
        assertTrue(shadowOf(view).lastLoadedUrl.startsWith(BookServer.APP + "editor/code.html"))
        val klc = bridge(view, "KLC")
        val good = state.edit.text("OEBPS/text/ch3.xhtml")!!
        call(klc, "changed", good.replace("</p>", ""))
        assertNotNull(state.codeError)
        assertFalse(state.dirty)
        call(klc, "changed", good.replace("Back in", "Forward in"))
        assertEquals(null, state.codeError)
        assertTrue(state.dirty)
        assertTrue(state.edit.text("OEBPS/text/ch3.xhtml")!!.contains("Forward in"))
        state.close()
    }

    @Test
    fun findAndReplaceGoesThroughTheListAndLeavesWhatWasTakenOff() {
        val (_, _, state) = setUp()
        val find = state.find
        find.open = true
        find.query = "Rose"
        find.replacement = "Lily"
        find.nameMode = true
        find.search()
        find.awaitWork()
        idle()
        assertEquals(9, find.entries.size)
        // The ordinary word and "the Rose Inn" are taken off; "her Rose" too, by hand.
        find.removeFlagged()
        assertEquals(2, find.removedCount)
        find.remove(find.visible.first { it.match.before.endsWith("told her ") })
        assertEquals(6, find.pendingCount)
        // Replace goes to the first one, then replaces it and moves on.
        find.replaceCurrent()
        find.awaitWork()
        idle()
        assertEquals(find.visible.first { it.status == EntryStatus.PENDING }.id, find.currentId)
        find.replaceCurrent()
        find.awaitWork()
        idle()
        assertEquals(1, find.replacedCount)
        find.replaceAll()
        find.awaitWork()
        idle()
        assertEquals(0, find.pendingCount)
        val ch1 = state.edit.text("OEBPS/text/ch1.xhtml")!!
        assertTrue(ch1.contains("Lily walked into the garden. She saw a rose by the wall, and Lily smiled."))
        assertTrue(ch1.contains("the Rose Inn. He told her Rose was late"))
        assertTrue(ch1.contains("The sign said LILY"))
        assertTrue(state.edit.text("OEBPS/text/ch2.xhtml")!!.contains("Lily went home."))
        // Two changes: the single replace and the rest together.
        assertTrue(state.canUndo)
        state.undo()
        state.awaitWork()
        idle()
        assertTrue(state.edit.text("OEBPS/text/ch1.xhtml")!!.contains("and Rose smiled"))
        assertEquals(FindReplace.Flag.NOT_NAME, FindReplace.nameFlag("Rose", "rose", "a rose", 2, 6, false).first)
        state.close()
    }

    @Test
    fun aNewBookOpensReadyToWriteAndGrowsAChapterAtATime() {
        val lib = Library(context, File(context.filesDir, "te-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val book = runBlocking { lib.createBook(dev.kobolibrary.app.epub.BookMetadata(title = "My Story", authors = listOf("Sam Writer"), language = "en")) }
        val state = TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, null)
        assertEquals(listOf("Chapter 1"), state.structure.chapters.map { it.title })
        val first = state.structure.chapters[0].path
        state.addChapter(first, nextChapterName("Chapter 1", 1))
        state.awaitWork()
        idle()
        assertEquals(listOf("Chapter 1", "Chapter 2"), state.structure.chapters.map { it.title })
        assertEquals(listOf("Chapter 1", "Chapter 2"), state.structure.toc.map { it.title })
        // The new chapter is the one open, to write in straight away.
        assertEquals(state.structure.chapters[1].path, state.currentPath)
        assertEquals("Added “Chapter 2”", state.undoLabel)
        state.save()
        state.awaitWork()
        idle()
        assertFalse(state.dirty)
        EpubFile.open(lib.epubFile(book.id)).use { epub ->
            val s = dev.kobolibrary.app.epub.BookStructure(dev.kobolibrary.app.epub.EpubFileFiles(epub))
            assertEquals(listOf("Chapter 1", "Chapter 2"), s.chapters.map { it.title })
            assertTrue(epub.readText(s.chapters[1].path)!!.contains("<h2>Chapter 2</h2>"))
        }
    }
}
