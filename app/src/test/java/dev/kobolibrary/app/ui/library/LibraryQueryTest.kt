package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.kobo.BookKind
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryTest {

    private fun book(
        title: String,
        authors: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        series: String? = null,
        index: Double? = null,
        collections: List<String> = emptyList(),
        publisher: String? = null,
        language: String? = null,
        published: String? = null,
        rating: Int? = null,
        isbn: String? = null,
        words: Int? = null,
        size: Long = 1000,
        added: Long = 0,
        koboPath: String? = null,
        modified: Long = 0,
        sent: Long? = null,
    ) = LibraryBook(
        id = title,
        meta = BookMetadata(
            title = title, authors = authors, tags = tags, series = series, seriesIndex = index, publisher = publisher,
            language = language, published = published, rating = rating, isbn = isbn,
        ),
        collections = collections, fileSize = size, hasCover = false, coverVersion = 1, addedAt = added, modifiedAt = modified,
        source = BookSource.FILE, originalName = null, koboPath = koboPath, sentAt = sent, words = words,
    )

    private val books = listOf(
        book("The Histories", listOf("Herodotus"), listOf("History", "Ancient Greece"), publisher = "Penguin Classics",
            language = "en", published = "2003-02-27", rating = 8, isbn = "9780140449082", words = 190_000, size = 900_000, added = 5,
            koboPath = "Herodotus/The Histories - Herodotus.kepub.epub", modified = 10_000, sent = 1_000),
        book("Art History Basics", listOf("Jane Smith"), listOf("Art History"), words = 40_000, size = 3_000_000, added = 9),
        book("The Republic", listOf("Plato"), listOf("Philosophy", "Ancient Greece"), collections = listOf("Classics"),
            published = "1871", rating = 10, words = 118_000, koboPath = "Plato/The Republic - Plato.kepub.epub", modified = 1_500, sent = 1_000, added = 1),
        book("The Sign of the Four", listOf("Arthur Conan Doyle"), listOf("Mystery"), series = "Sherlock Holmes", index = 2.0,
            published = "1890", rating = 7, words = 43_000, added = 3),
        book("A Study in Scarlet", listOf("Arthur Conan Doyle"), listOf("Mystery"), series = "Sherlock Holmes", index = 1.0,
            published = "1887", words = 43_500, added = 2),
        book("Jane Eyre", listOf("Charlotte Brontë"), listOf("Gothic", "Romance"), language = "fr", added = 4),
        book("Le Comte de Monte-Cristo", listOf("Alexandre Dumas", "Auguste Maquet"), language = "fr", rating = 9, words = 460_000, added = 6),
    )

    private fun search(q: String): List<String> = books.filter { LibraryQuery.parse(q)?.matches(it) ?: true }.map { it.title }

    @Test
    fun plainWordsSearchEverything() {
        assertEquals(listOf("The Sign of the Four", "A Study in Scarlet"), search("holmes"))
        assertEquals(listOf("The Republic"), search("classics plato"))
        assertEquals(listOf("The Histories"), search("penguin"))
        assertEquals("accents don't matter", listOf("Jane Eyre"), search("bronte"))
        assertEquals(listOf("Jane Eyre"), search("BRONTË"))
        assertEquals(books.map { it.title }, search("   "))
    }

    @Test
    fun fieldsAndWholeValues() {
        assertEquals(listOf("The Histories", "Art History Basics"), search("tag:history"))
        assertEquals(listOf("The Histories"), search("tag:=history"))
        assertEquals(listOf("The Histories"), search("TAG:=History"))
        assertEquals(listOf("The Sign of the Four", "A Study in Scarlet"), search("author:\"conan doyle\""))
        assertEquals(listOf("Le Comte de Monte-Cristo"), search("by:maquet"))
        assertEquals(listOf("The Republic"), search("collection:classics"))
        assertEquals(listOf("The Republic"), search("shelf:=Classics"))
        assertEquals(listOf("The Histories"), search("title:histories"))
        assertEquals(listOf("Jane Eyre", "Le Comte de Monte-Cristo"), search("lang:french"))
        assertEquals(listOf("Jane Eyre", "Le Comte de Monte-Cristo"), search("language:fr"))
        assertEquals(listOf("The Histories"), search("isbn:978-0-14-044908-2"))
        assertEquals("an unknown field is just a word", emptyList<String>(), search("colour:blue"))
    }

    @Test
    fun aBooksTitleAuthorOrSeriesCanBeSearchedFor() {
        // What a tap on "Sherlock Holmes #2" in a book's details searches for: the series by its name, without the number.
        assertEquals("series:\"Sherlock Holmes\"", dev.kobolibrary.app.ui.components.seriesSearch("Sherlock Holmes"))
        assertEquals(listOf("The Sign of the Four", "A Study in Scarlet"), search(dev.kobolibrary.app.ui.components.seriesSearch("Sherlock Holmes")))
        // And a book by its title, and the books by any of its authors.
        assertEquals(listOf("The Sign of the Four"), search(dev.kobolibrary.app.ui.components.titleSearch("The Sign of the Four")))
        assertEquals(listOf("The Sign of the Four", "A Study in Scarlet"), search(dev.kobolibrary.app.ui.components.authorSearch(listOf("Arthur Conan Doyle"))!!))
        assertEquals("author:\"A\" or author:\"B\"", dev.kobolibrary.app.ui.components.authorSearch(listOf("A", " B ", "Unknown", "")))
        // Quotes in a name don't break the search.
        assertEquals("series:\"The  Name\"", dev.kobolibrary.app.ui.components.seriesSearch("The \"Name\""))
    }

    @Test
    fun combinations() {
        assertEquals(listOf("Art History Basics"), search("tag:history and not tag:greece"))
        assertEquals(listOf("Art History Basics"), search("tag:history -tag:greece"))
        assertEquals(listOf("The Histories", "Art History Basics", "The Sign of the Four", "A Study in Scarlet"), search("tag:history or series:holmes"))
        assertEquals(listOf("The Republic"), search("(tag:history or tag:philosophy) and author:plato"))
        assertEquals(listOf("The Histories", "The Republic"), search("tag:\"ancient greece\" (rating:>=4 or year:<1900)"))
        assertEquals(listOf("The Histories", "The Republic"), search("tag:greece || tag:greece"))
        assertEquals(listOf("Art History Basics", "The Sign of the Four", "A Study in Scarlet", "Jane Eyre", "Le Comte de Monte-Cristo"), search("not tag:greece"))
    }

    @Test
    fun numbersCompare() {
        assertEquals(listOf("The Histories", "Le Comte de Monte-Cristo"), search("pages:>500"))
        assertEquals(listOf("Art History Basics", "The Sign of the Four", "A Study in Scarlet"), search("pages:<=200"))
        assertEquals("rating:4 means four stars, not five", listOf("The Histories", "Le Comte de Monte-Cristo"), search("rating:4"))
        assertEquals(listOf("The Republic"), search("stars:5"))
        assertEquals(listOf("The Histories", "The Republic", "Le Comte de Monte-Cristo"), search("rating:>=4"))
        assertEquals(listOf("Art History Basics", "A Study in Scarlet", "Jane Eyre"), search("rating:none"))
        assertEquals(listOf("The Republic", "The Sign of the Four", "A Study in Scarlet"), search("year:<1900"))
        assertEquals(listOf("The Sign of the Four"), search("published:1890"))
        assertEquals("not a number", emptyList<String>(), search("pages:many"))
    }

    @Test
    fun onTheKobo() {
        assertEquals(listOf("The Histories", "The Republic"), search("kobo:yes"))
        assertEquals(5, search("kobo:no").size)
        assertEquals("edited since it was sent", listOf("The Histories"), search("kobo:changed"))
    }

    @Test
    fun nonsenseNeverThrows() {
        for (q in listOf("((", ")", "and or not", "-", "- -", "tag:", "tag:=", "\"unclosed", "pages:>", "rating:>=", "()", "not", "or", "-(", ":", "a:b:c", "tag:\"\"")) {
            search(q)
        }
        assertEquals("a stray bracket doesn't lose the rest", listOf("The Republic"), search("plato ) classics"))
        assertEquals(listOf("The Republic"), search("(plato"))
        assertEquals("a lone dash is ignored", listOf("The Republic"), search("plato -"))
    }

    private fun onKobo(status: ReadStatus, percent: Int, lastRead: Long?) = KoboBook(
        contentId = "x", title = "x", author = null, subtitle = null, series = null, seriesNumber = null, description = null,
        publisher = null, language = null, isbn = null, mimeType = null, format = "KEPUB", imageId = "x", kind = BookKind.SIDELOADED,
        isDownloaded = true, isExpired = false, readStatus = status, percentRead = percent, lastReadMillis = lastRead, addedMillis = null,
        fileSize = null, relativePath = "x",
    )

    @Test
    fun readingProgressFromTheKobo() {
        val kobo = mapOf(
            "The Histories" to onKobo(ReadStatus.READING, 37, 2_000L),
            "The Republic" to onKobo(ReadStatus.FINISHED, 100, 1_000L),
            "The Sign of the Four" to onKobo(ReadStatus.UNREAD, 0, null),
        )
        val reading: (LibraryBook) -> KoboBook? = { kobo[it.title] }
        fun search(q: String) = books.filter { LibraryQuery.parse(q)?.matches(it, reading) ?: true }.map { it.title }
        assertEquals(listOf("The Histories"), search("read:reading"))
        assertEquals(listOf("The Republic"), search("status:finished"))
        assertEquals(listOf("The Sign of the Four"), search("read:unread"))
        assertEquals(listOf("The Histories", "The Republic"), search("read:reading or read:finished"))
        val byLastRead = LibrarySorting.filter(books, "", LibraryFilter.ALL, null, LibrarySort.LAST_READ, reading = reading).map { it.title }
        assertEquals("most recent first, never-read after", listOf("The Histories", "The Republic"), byLastRead.take(2))
        assertEquals(books.size, byLastRead.size)
    }

    @Test
    fun sortingAndTagPicking() {
        fun titles(sort: LibrarySort, descending: Boolean = sort.startsDescending, tags: Set<String> = emptySet(), all: Boolean = true, query: String = "") =
            LibrarySorting.filter(books, query, LibraryFilter.ALL, null, sort, descending, tags, all).map { it.title }

        assertEquals("longest first; books without a count last", "Le Comte de Monte-Cristo", titles(LibrarySort.PAGES).first())
        assertEquals("Jane Eyre", titles(LibrarySort.PAGES).last())
        assertEquals("Jane Eyre", titles(LibrarySort.PAGES, descending = false).last())
        assertEquals("Art History Basics", titles(LibrarySort.PAGES, descending = false).first())
        assertEquals(listOf("A Study in Scarlet", "The Sign of the Four"), titles(LibrarySort.SERIES).take(2))
        assertEquals("newest added first", listOf("Art History Basics", "Le Comte de Monte-Cristo"), titles(LibrarySort.ADDED).take(2))
        assertEquals("Art History Basics", titles(LibrarySort.SIZE).first())
        assertEquals("newest first", listOf("The Histories", "The Sign of the Four", "A Study in Scarlet", "The Republic"), titles(LibrarySort.PUBLISHED).take(4))
        assertEquals("The Republic", titles(LibrarySort.RATING).first())
        assertEquals("“The” and “A” don't count", listOf("Art History Basics", "The Histories", "Jane Eyre", "Le Comte de Monte-Cristo", "The Republic", "The Sign of the Four", "A Study in Scarlet"), titles(LibrarySort.TITLE))

        assertEquals(listOf("The Histories", "The Republic"), titles(LibrarySort.TITLE, tags = setOf("ancient greece")).sorted())
        assertEquals(listOf("The Histories"), titles(LibrarySort.TITLE, tags = setOf("Ancient Greece", "History")))
        assertEquals(listOf("The Histories", "The Republic"), titles(LibrarySort.TITLE, tags = setOf("Ancient Greece", "History"), all = false).sorted())
        assertEquals(listOf("The Republic"), titles(LibrarySort.TITLE, tags = setOf("Ancient Greece"), query = "plato"))
    }
}
