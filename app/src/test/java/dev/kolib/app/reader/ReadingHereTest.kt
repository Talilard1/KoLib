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
