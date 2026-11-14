package dev.kobolibrary.app.ui.edit

import dev.kobolibrary.app.epub.BookMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsDraftTest {

    private val book = BookMetadata(
        title = "The Histories",
        titleSort = "Histories, The",
        authors = listOf("Herodotus", "J.R.R. Tolkien"),
        authorSorts = mapOf("Herodotus" to "Herodotus", "J.R.R. Tolkien" to "Tolkien, J.R.R."),
        series = "Penguin Classics",
        seriesIndex = 3.0,
        tags = listOf("History"),
        publisher = "Penguin",
        published = "2003-02-27",
        language = "en",
        isbn = "9780140449082",
        description = "<p>The first great work of <b>history</b>.</p>",
        rating = 7,
        identifier = "uuid-1",
    )

    @Test
    fun anUntouchedDraftHasNoChanges() {
        val d = DetailsDraft(book, listOf("Classics"))
        assertFalse(d.isDirty)
        assertTrue(d.problems.isEmpty())
        assertEquals(book.title, d.toMetadata().title)
        assertEquals(7, d.toMetadata().rating) // half stars survive
        assertNull("an automatic title sort is left automatic", d.titleSort)
    }

    @Test
    fun editsShowUpInTheMetadata() {
        val d = DetailsDraft(book, listOf("Classics"))
        d.title = "  Histories  "
        d.seriesIndex = "2,5"
        d.tags.add("Greece")
        d.collections.add("To Read")
        d.published = "2005-7"
        d.rating = 10
        assertTrue(d.isDirty)
        val m = d.toMetadata()
        assertEquals("Histories", m.title)
        assertEquals(2.5, m.seriesIndex!!, 0.0)
        assertEquals(listOf("History", "Greece"), m.tags)
        assertEquals("2005-07", m.published)
        assertEquals(10, m.rating)
        assertEquals(listOf("Classics", "To Read"), d.collectionList())
        assertEquals("uuid-1", m.identifier)
    }

    @Test
    fun clearingTheSeriesClearsItsNumber() {
        val d = DetailsDraft(book, emptyList())
        d.series = " "
        val m = d.toMetadata()
        assertNull(m.series)
        assertNull(m.seriesIndex)
    }

    @Test
    fun badValuesAreCaught() {
        val d = DetailsDraft(book, emptyList())
        d.title = " "
        d.seriesIndex = "three"
        d.published = "2005-02-30"
        d.isbn = "9780140449083" // last digit wrong
        assertEquals(4, d.problems.size)
        d.isbn = "97801404"
        assertTrue(d.isbnProblem!!.contains("10 or 13"))
    }

    @Test
    fun anIsbnThatCameWithTheBookDoesNotBlockSaving() {
        val d = DetailsDraft(book.copy(isbn = "9780140449083"), emptyList())
        assertNull(d.isbnProblem)
        assertTrue(d.isbnWarning != null)
        d.title = "Changed"
        assertTrue(d.problems.isEmpty())
    }

    @Test
    fun authorsCanBeRenamedReorderedAndKeepTheirSortNames() {
        val d = DetailsDraft(book, emptyList())
        d.renameAuthor(1, "J. R. R. Tolkien", "Tolkien, J. R. R.")
        d.moveAuthorToFront(1)
        val m = d.toMetadata()
        assertEquals(listOf("J. R. R. Tolkien", "Herodotus"), m.authors)
        assertEquals("Tolkien, J. R. R.", m.sortNameFor("J. R. R. Tolkien"))
        assertEquals("Herodotus", m.sortNameFor("Herodotus"))
        assertTrue(d.isDirty)
    }

    @Test
    fun aRemovedAuthorTakesTheirSortNameWithThem() {
        val d = DetailsDraft(book, emptyList())
        d.authors.removeAt(1)
        assertEquals(mapOf("Herodotus" to "Herodotus"), d.toMetadata().authorSorts)
    }

    @Test
    fun titleSortCanBeSetAndPutBack() {
        val d = DetailsDraft(book, emptyList())
        d.titleSort = "Histories (Herodotus)"
        assertEquals("Histories (Herodotus)", d.toMetadata().effectiveTitleSort)
        d.titleSort = null
        assertFalse(d.isDirty)
    }

    @Test
    fun anEmptyDescriptionIsNone() {
        val d = DetailsDraft(book, emptyList())
        d.description = "<p><br></p>"
        assertNull(d.toMetadata().description)
    }

    @Test
    fun datesAreCheckedProperly() {
        assertEquals("2005", DetailsDraft.parseDate("2005"))
        assertEquals("2005-07", DetailsDraft.parseDate("2005-7"))
        assertEquals("2024-02-29", DetailsDraft.parseDate("2024-02-29"))
        assertNull(DetailsDraft.parseDate("2023-02-29"))
        assertNull(DetailsDraft.parseDate("17/07/2005"))
        assertNull(DetailsDraft.parseDate("0099"))
    }
}
