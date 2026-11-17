package dev.kobolibrary.app.ui

import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.kobo.BookKind
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which library book's cover a book on the Kobo shows. */
class KoboCoverCopyTest {

    private fun library(id: String, title: String, author: String?, number: Double? = null, koboPath: String? = null) = LibraryBook(
        id = id, meta = BookMetadata(title = title, authors = listOfNotNull(author), seriesIndex = number), collections = emptyList(),
        fileSize = 0, hasCover = true, coverVersion = 0, addedAt = 0, modifiedAt = 0,
        source = BookSource.FILE, originalName = null, koboPath = koboPath,
    )

    private fun kobo(id: String, title: String, author: String?, number: String? = null, path: String? = null) = KoboBook(
        contentId = id, title = title, author = author, subtitle = null, series = null, seriesNumber = number, description = null,
        publisher = null, language = null, isbn = null, mimeType = null, format = "EPUB", imageId = id, kind = BookKind.SIDELOADED,
        isDownloaded = true, isExpired = false, readStatus = ReadStatus.UNREAD, percentRead = 0, lastReadMillis = null, addedMillis = null,
        fileSize = null, relativePath = path,
    )

    @Test
    fun theCopyItWasSentFromComesFirst() {
        val sent = library("a", "The Odyssey", "Homer", koboPath = "Books/Homer/The Odyssey.kepub.epub")
        val other = library("b", "The Odyssey", "Homer")
        val extras = KoboExtras(listOf(sent, other))
        assertEquals(sent, extras.coverCopy(kobo("k", "Something else", "Nobody", path = "books/homer/the odyssey.kepub.epub")))
    }

    @Test
    fun elseTheOneBookWithTheSameTitleAuthorAndNumber() {
        val one = library("a", "The  Sign of the Four", "Arthur Conan Doyle", 2.0)
        val two = library("b", "Dune", "Frank Herbert", 1.0)
        val three = library("c", "Dune", "Frank Herbert", 2.0)
        val extras = KoboExtras(listOf(one, two, three))
        // Spacing and capitals don't matter; of several authors, the first counts.
        assertEquals(one, extras.coverCopy(kobo("k1", "the sign of the four ", "Arthur Conan Doyle & Someone Else", "2")))
        // Another author's book of the same name isn't it.
        assertNull(extras.coverCopy(kobo("k2", "The Sign of the Four", "Another Writer")))
        // Two with the same title and author: told apart by their number, and neither when there's none to go by.
        assertEquals(three, extras.coverCopy(kobo("k3", "Dune", "Frank Herbert", "2,0")))
        assertNull(extras.coverCopy(kobo("k4", "Dune", "Frank Herbert")))
        assertNull(extras.coverCopy(kobo("k5", "Emma", "Jane Austen")))
        // Asked again (as every redraw does), the answer is the same.
        assertEquals(three, extras.coverCopy(kobo("k3", "Dune", "Frank Herbert", "2,0")))
        assertNull(extras.coverCopy(kobo("k5", "Emma", "Jane Austen")))
    }
}
