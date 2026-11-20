package dev.kolib.app.reader

import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.ui.library.LibrarySort
import dev.kolib.app.ui.library.LibrarySorting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for org.json, which the reader's saved places are written in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingHereTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun savingAPlaceUpdatesTheLibrarysIndex() {
        val dirs = mapOf("a" to tmp.newFolder("a"), "b" to tmp.newFolder("b"), "c" to tmp.newFolder("c"))
        // One book read before, one never opened.
        ReaderStore(dirs.getValue("a")).save(ReaderState(ReaderPosition("ch1.xhtml", null, 0.5f, bookProgress = 0.25f, at = 1000L)))
        val index = ReadingHereIndex()
        index.load(dirs.keys) { dirs.getValue(it) }
        assertEquals(ReadHere(0.25f, 1000L), index.books.value["a"])
        assertNull(index.books.value["b"])

        // Reading another book: its saves reach the index straight away.
        val store = ReaderStore(dirs.getValue("c")) { s -> index.update("c", s.position) }
        store.save(ReaderState(ReaderPosition("ch9.xhtml", "0/3:12", 0.9f, bookProgress = 0.874f, at = 2000L)))
        assertEquals(87, index.books.value["c"]?.percent)
        index.forget(listOf("a"))
        assertNull(index.books.value["a"])
    }

    @Test
    fun aBookLeftOnItsFirstPageIsNotReadYetOrStillFinished() {
        val dir = tmp.newFolder("first")
        val store = ReaderStore(dir)
        // Opened and left with no place (on its first page): not read yet.
        store.save(ReaderState(null))
        assertNull(ReaderStore.readHereOf(dir))
        // Finished, then opened and left on its first page: still finished.
        ReaderStore.markFinished(dir, now = 5_000L)
        store.save(ReaderState(null))
        assertTrue(ReaderStore.readHereOf(dir)!!.finished)
        // Read past it: being read again.
        store.save(ReaderState(ReaderPosition("ch2.xhtml", null, 0.2f, bookProgress = 0.1f, at = 6_000L)))
        assertEquals(ReadHere(0.1f, 6_000L), ReaderStore.readHereOf(dir))
    }

    @Test
    fun aComicOrPdfLeftOnItsFirstPageIsNotReadYetOrStillFinished() {
        val dir = tmp.newFolder("comic")
        ReaderStore.savePdfPlace(dir, PdfPlace(3, 20, at = 1_000L))
        assertTrue(ReaderStore.readHereOf(dir)!!.started)
        // Back to its cover.
        assertNull(ReaderStore.leaveAtStart(dir))
        assertNull(ReaderStore.pdfPlace(dir))
        // Finished, then looked at the cover of again.
        ReaderStore.markFinished(dir, now = 2_000L)
        assertTrue(ReaderStore.leaveAtStart(dir)!!.finished)
    }

    @Test
    fun aStartedBookNeverShowsAsNoneRead() {
        assertEquals(1, ReadHere(0.001f, 0L).percent)
        assertEquals(0, ReadHere(0f, 0L).percent)
        assertEquals(100, ReadHere(1f, 0L).percent)
        assertTrue(ReadHere(0.001f, 0L).started)
    }

    @Test
    fun lastReadHereSortsMostRecentFirstWithUnreadBooksLast() {
        val books = listOf("Never opened", "Last week", "Today").map { t ->
            LibraryBook(
                id = t, meta = BookMetadata(title = t), collections = emptyList(), fileSize = 1, hasCover = false, coverVersion = 1,
                addedAt = 0, modifiedAt = 0, source = BookSource.FILE, originalName = null, koboPath = null,
            )
        }
        val here = mapOf(books[1].id to ReadHere(0.3f, 1_000L), books[2].id to ReadHere(0.6f, 9_000L))
        val sorted = LibrarySorting.filter(books, "", dev.kolib.app.ui.library.LibraryFilter.ALL, null, LibrarySort.READ_HERE, here = { here[it.id] })
        assertEquals(listOf("Today", "Last week", "Never opened"), sorted.map { it.title })
    }
}
