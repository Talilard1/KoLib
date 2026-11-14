package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.ui.BookSorting
import dev.kobolibrary.app.ui.KoboSort
import dev.kobolibrary.app.ui.KoboExtras
import dev.kobolibrary.app.kobo.KoboTagStore
import dev.kobolibrary.app.ui.SourceFilter
import dev.kobolibrary.app.ui.StatusFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSortingTest {

    private fun book(
        id: String, title: String, author: String? = null, series: String? = null, number: String? = null,
        kind: BookKind = BookKind.SIDELOADED, status: ReadStatus = ReadStatus.UNREAD, lastRead: Long? = null,
    ) = KoboBook(
        contentId = id, title = title, author = author, subtitle = null, series = series, seriesNumber = number,
        description = null, publisher = null, language = null, isbn = null, mimeType = null, format = "EPUB",
        imageId = id, kind = kind, isDownloaded = true, isExpired = false, readStatus = status, percentRead = 0,
        lastReadMillis = lastRead, addedMillis = null, fileSize = null, relativePath = "Books/$title.epub",
    )

    private val books = listOf(
        book("1", "The Well of Ascension", "Brandon Sanderson", "Mistborn", "2", status = ReadStatus.READING, lastRead = 30),
        book("2", "The Final Empire", "Brandon Sanderson", "Mistborn", "1", status = ReadStatus.FINISHED, lastRead = 10),
        book("3", "A Game of Thrones", "George R. R. Martin", "A Song of Ice and Fire", "1"),
        book("4", "Dune", "Frank Herbert", kind = BookKind.PURCHASED, lastRead = 20),
        book("5", "An Ember in the Ashes", "Sabaa Tahir"),
    )

    private fun ids(list: List<KoboBook>) = list.map { it.contentId }

    @Test
    fun titleSortIgnoresLeadingArticles() {
        val sorted = BookSorting.filter(books, emptyMap(), "", SourceFilter.ALL, null, KoboSort.TITLE)
        assertEquals(listOf("4", "5", "2", "3", "1"), ids(sorted)) // Dune, Ember, Final, Game, Well
    }

    @Test
    fun authorSortUsesSurnameThenSeriesOrder() {
        val sorted = BookSorting.filter(books, emptyMap(), "", SourceFilter.ALL, null, KoboSort.AUTHOR)
        assertEquals(listOf("4", "3", "2", "1", "5"), ids(sorted)) // Herbert, Martin, Sanderson #1, #2, Tahir
    }

    @Test
    fun seriesSortPutsBooksWithoutSeriesLast() {
        val sorted = BookSorting.filter(books, emptyMap(), "", SourceFilter.ALL, null, KoboSort.SERIES)
        assertEquals(listOf("3", "2", "1", "4", "5"), ids(sorted))
    }

    @Test
    fun recentlyReadFirst() {
        val sorted = BookSorting.filter(books, emptyMap(), "", SourceFilter.ALL, null, KoboSort.LAST_READ)
        assertEquals(listOf("1", "4", "2", "5", "3"), ids(sorted))
    }

    @Test
    fun filtersBySourceStatusAndSearch() {
        assertEquals(listOf("4"), ids(BookSorting.filter(books, emptyMap(), "", SourceFilter.STORE, null, KoboSort.TITLE)))
        assertEquals(listOf("1"), ids(BookSorting.filter(books, emptyMap(), "", SourceFilter.ALL, StatusFilter.READING, KoboSort.TITLE)))
        assertEquals(listOf("2", "1"), ids(BookSorting.filter(books, emptyMap(), "mistborn", SourceFilter.ALL, null, KoboSort.SERIES)))
        val inCollection = mapOf("5" to listOf("YA Fantasy"))
        assertEquals(listOf("5"), ids(BookSorting.filter(books, inCollection, "ya fan", SourceFilter.ALL, null, KoboSort.TITLE)))
    }

    @Test
    fun collectionTagAndFieldFilters() {
        val inCollection = mapOf("1" to listOf("Fantasy"), "2" to listOf("Fantasy", "Read again"), "3" to listOf("Fantasy"))
        val extras = KoboExtras(
            fileTags = mapOf(
                "books/the final empire.epub" to KoboTagStore.Entry(0, listOf("Fantasy", "Heists")),
                "books/a game of thrones.epub" to KoboTagStore.Entry(0, listOf("Fantasy", "Politics"), rating = 8),
                "books/dune.epub" to KoboTagStore.Entry(0, listOf("Science Fiction", "Politics")),
            ),
        )
        fun f(query: String = "", collection: String? = null, tags: Set<String> = emptySet(), all: Boolean = true, sort: KoboSort = KoboSort.TITLE) =
            ids(BookSorting.filter(books, inCollection, query, SourceFilter.ALL, null, sort, sort.startsDescending, collection, tags, all, extras))
        assertEquals(listOf("2", "3", "1"), f(collection = "fantasy"))
        assertEquals(listOf("4", "3"), f(tags = setOf("politics")))
        assertEquals(listOf("3"), f(tags = setOf("politics", "fantasy")))
        assertEquals(listOf("4", "2", "3"), f(tags = setOf("politics", "heists"), all = false))
        assertEquals(listOf("2"), f("tag:heists"))
        assertEquals(listOf("4", "3"), f("tag:politics -collection:\"read again\""))
        assertEquals(listOf("2"), f("collection:\"read again\" read:finished"))
        assertEquals(listOf("3"), f("rating:4"))
        // Rated books first (the Kobo's own stars, or the file's rating), the rest after them by title.
        assertEquals(listOf("3", "4", "5", "2", "1"), f(sort = KoboSort.RATING))
        // Progress: finished first when sorted most read first, then the one being read, then unread by title.
        assertEquals(listOf("2", "1", "4", "5", "3"), f(sort = KoboSort.PROGRESS))
        assertEquals(listOf("4", "5", "2", "3", "1"), f("library:no"))
    }
}
