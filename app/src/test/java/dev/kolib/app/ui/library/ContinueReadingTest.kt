package dev.kolib.app.ui.library

import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.reader.ReadHere
import org.junit.Assert.assertEquals
import org.junit.Test

/** The row of what's being read, over the shelf. */
class ContinueReadingTest {
    private fun book(id: String) = LibraryBook(
        id = id, meta = BookMetadata(title = id), collections = emptyList(), fileSize = 1000, hasCover = false, coverVersion = 1,
        addedAt = 0, modifiedAt = 0, source = BookSource.FILE, originalName = "$id.epub", koboPath = null,
    )

    @Test
    fun startedBooksShowTheLastReadFirstAndNotOnesReadToTheEndOrMarkedFinished() {
        val books = listOf("unopened", "middle", "recent", "end", "marked", "old").map(::book)
        val here = mapOf(
            "middle" to ReadHere(0.4f, at = 200),
            "recent" to ReadHere(0.1f, at = 300),
            "end" to ReadHere(1f, at = 400),
            "marked" to ReadHere(0.6f, at = 500, finished = true),
            "old" to ReadHere(0.9f, at = 100),
        )
        assertEquals(listOf("recent", "middle", "old"), beingRead(books, { here[it.id] }).map { it.id })
    }

    @Test
    fun atMostTheNumberAsked() {
        val books = (1..20).map { book("b$it") }
        val here = books.associate { it.id to ReadHere(0.5f, at = it.id.drop(1).toLong()) }
        assertEquals(listOf("b20", "b19", "b18"), beingRead(books, { here[it.id] }, most = 3).map { it.id })
    }
}
