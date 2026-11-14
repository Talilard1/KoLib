package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.ui.textedit.nextChapterName
import org.junit.Assert.assertEquals
import org.junit.Test

class MakeBooksTest {

    private fun book(title: String, authors: List<String> = emptyList(), series: String? = null, index: Double? = null) = LibraryBook(
        id = title,
        meta = BookMetadata(title = title, authors = authors, series = series, seriesIndex = index),
        collections = emptyList(), fileSize = 1000, hasCover = false, coverVersion = 1, addedAt = 0, modifiedAt = 0,
        source = BookSource.FILE, originalName = null, koboPath = null,
    )

    @Test
    fun aSeriesGoesInItsOrderAndIsNamedForIt() {
        val picked = listOf(
            book("The Two Towers", listOf("J. R. R. Tolkien"), "The Lord of the Rings", 2.0),
            book("The Return of the King", listOf("J. R. R. Tolkien"), "The Lord of the Rings", 3.0),
            book("The Fellowship of the Ring", listOf("J. R. R. Tolkien"), "the lord of the rings", 1.0),
        )
        val order = MakeBooks.mergeOrder(picked)
        assertEquals(listOf("The Fellowship of the Ring", "The Two Towers", "The Return of the King"), order.map { it.title })
        assertEquals("The Lord of the Rings: Books 1–3", MakeBooks.mergedTitle(order))
        assertEquals("J. R. R. Tolkien", MakeBooks.mergedAuthor(order))
        // Not all in a row.
        assertEquals("The Lord of the Rings: Books 1 & 3", MakeBooks.mergedTitle(listOf(order[0], order[2])))
        // Not all numbered: as picked, and an omnibus.
        val loose = listOf(order[1], book("Unnumbered", series = "The Lord of the Rings"))
        assertEquals(loose, MakeBooks.mergeOrder(loose))
        assertEquals("The Lord of the Rings Omnibus", MakeBooks.mergedTitle(loose))
    }

    @Test
    fun otherBooksAreNamedForWhatsInThem() {
        val dracula = book("Dracula", listOf("Bram Stoker"))
        val carmilla = book("Carmilla", listOf("J. Sheridan Le Fanu"))
        val guest = book("Dracula's Guest", listOf("bram stoker"))
        assertEquals(listOf(dracula, carmilla), MakeBooks.mergeOrder(listOf(dracula, carmilla)))
        assertEquals("Dracula & Carmilla", MakeBooks.mergedTitle(listOf(dracula, carmilla)))
        assertEquals("Dracula, Carmilla & Dracula's Guest", MakeBooks.mergedTitle(listOf(dracula, carmilla, guest)))
        // Authors once each, in order.
        assertEquals("Bram Stoker & J. Sheridan Le Fanu", MakeBooks.mergedAuthor(listOf(dracula, carmilla, guest)))
        val stoker = (1..4).map { book("Story $it", listOf("Bram Stoker")) }
        assertEquals("Bram Stoker Omnibus", MakeBooks.mergedTitle(stoker))
        val crowd = (1..6).map { book("Tale $it", listOf("Writer $it")) }
        assertEquals("Tale 1 and Others", MakeBooks.mergedTitle(crowd))
        assertEquals("Various", MakeBooks.mergedAuthor(crowd))
    }

    @Test
    fun aNewChapterIsNamedAfterTheOneBefore() {
        assertEquals("Chapter 2", nextChapterName("Chapter 1", 1))
        assertEquals("Part 11", nextChapterName("Part 10", 12))
        assertEquals("Chapter 4", nextChapterName("Epilogue", 3))
        assertEquals("Chapter 7", nextChapterName("Chapter 6: The Storm", 9))
        assertEquals("Chapter 1", nextChapterName(null, 0))
        // Chapters named only by number stay that way.
        assertEquals("13", nextChapterName("12", 12))
    }
}
