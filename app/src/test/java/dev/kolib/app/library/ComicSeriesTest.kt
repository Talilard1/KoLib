package dev.kolib.app.library

import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.reader.ReadHere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicSeriesTest {

    private fun comic(file: String, series: String? = null, index: Double? = null, title: String = file.substringBeforeLast('.'), id: String = file) = LibraryBook(
        id = id,
        meta = BookMetadata(title = title, series = series, seriesIndex = index),
        collections = emptyList(), fileSize = 1000, hasCover = false, coverVersion = 1, addedAt = 0, modifiedAt = 0,
        source = BookSource.FILE, originalName = file, koboPath = null, sourceFormat = "CBZ",
    )

    @Test
    fun fileNamesSayTheSeriesAndNumber() {
        fun p(s: String) = ComicSeries.parse(s).let { Triple(it.stem, it.volume, it.number) }
        assertEquals(Triple("Saga", null, 1.0), p("Saga 001.cbz"))
        assertEquals(Triple("Saga", null, 12.0), p("Saga 012 (2013) (Digital) (Empire).cbz"))
        assertEquals(Triple("Saga", null, 1.5), p("Saga_001.5.cbr"))
        assertEquals(Triple("Saga", null, 3.0), p("Saga #3 - The Beginning.cbz"))
        assertEquals(Triple("One Piece", 1.0, 1.0), p("One Piece v01 c001.cbz"))
        assertEquals(Triple("Berserk", 3.0, null), p("Berserk Volume 3.cbz"))
        assertEquals(Triple("X-Men 2099", null, 1.0), p("X-Men 2099 001.cbz"))
        assertEquals(Triple("Spy x Family", null, 42.0), p("Spy x Family - Chapter 42.cbz"))
        assertEquals(Triple("Astérix", 5.0, null), p("Astérix T05.cbz"))
        // Names that are only numbers, or have none, name no series.
        assertEquals(Triple("", null, 12.0), p("Chapter 12.cbz"))
        assertEquals(Triple("", null, 7.0), p("007.cbz"))
        assertEquals(Triple("", null, null), p("Watchmen.cbz"))
        // A word that starts like one for numbers isn't taken for one.
        assertEquals(Triple("Avatar The Last Airbender", null, 2.0), p("Avatar The Last Airbender 2.cbz"))
    }

    @Test
    fun aSeriesIsOneEntryWithItsIssuesInOrder() {
        val books = listOf(
            comic("Saga 010.cbz", "Saga", 10.0),
            comic("Saga 002.cbz", "Saga", 2.0),
            comic("saga 001.cbz", "saga", 1.0),
            comic("Watchmen.cbz"),
            comic("Akira v02.cbz", "Akira"),
            comic("Akira v01.cbz", "Akira"),
        )
        val entries = ComicSeries.entries(books)
        assertEquals(listOf("Akira", "Saga", "Watchmen"), entries.map { it.title })
        assertEquals(listOf("Akira v01.cbz", "Akira v02.cbz"), entries[0].issues.map { it.id })
        assertEquals(listOf("saga 001.cbz", "Saga 002.cbz", "Saga 010.cbz"), entries[1].issues.map { it.id })
        assertTrue(entries[1].isSeries)
        assertFalse(entries[2].isSeries)
        assertEquals("Saga 010.cbz", entries[1].after("Saga 002.cbz")?.id)
        assertNull(entries[1].after("Saga 010.cbz"))
    }

    @Test
    fun carryingOnIsTheFirstNotRead() {
        val e = ComicSeries.entries((1..4).map { comic("Saga 00$it.cbz", "Saga", it.toDouble()) }).single()
        val here = mapOf(
            "Saga 001.cbz" to ReadHere(1f, 10),
            "Saga 002.cbz" to ReadHere(0.4f, 20, finished = false),
            "Saga 004.cbz" to ReadHere(1f, 5, finished = true),
        )
        val at = { b: LibraryBook -> here[b.id] }
        assertEquals("Saga 002.cbz", e.next(at)?.id)
        assertEquals(listOf("Saga 002.cbz", "Saga 003.cbz"), e.unread(at).map { it.id })
        assertEquals(20L, e.lastRead(at))
        assertTrue(e.started(at))
        assertNull(e.next { ReadHere(1f, 1, finished = true) })
    }

    @Test
    fun comicsAddedTogetherWithTheSameNameAreASeries() {
        val added = listOf(comic("Saga 001.cbz"), comic("Saga 002.cbz"), comic("Watchmen.cbz"), comic("Monstress 001.cbz"))
        val given = ComicSeries.assign(added, others = emptyList())
        assertEquals(ComicSeries.Given("Saga", 1.0), given["Saga 001.cbz"])
        assertEquals(ComicSeries.Given("Saga", 2.0), given["Saga 002.cbz"])
        // On its own, a name with a number isn't taken for a series.
        assertNull(given["Monstress 001.cbz"])
        assertNull(given["Watchmen.cbz"])
    }

    @Test
    fun aComicAddedLaterJoinsItsSeries() {
        val others = listOf(comic("Saga 001.cbz", "Saga", 1.0), comic("Monstress 001.cbz"))
        val given = ComicSeries.assign(listOf(comic("SAGA_002.cbz"), comic("Monstress 002.cbz")), others)
        // Spelt as the series already is.
        assertEquals(ComicSeries.Given("Saga", 2.0), given["SAGA_002.cbz"])
        // One already there with no series goes in the new one with it.
        assertEquals(ComicSeries.Given("Monstress", 1.0), given["Monstress 001.cbz"])
        assertEquals(ComicSeries.Given("Monstress", 2.0), given["Monstress 002.cbz"])
    }

    @Test
    fun aFolderNamesTheSeriesOfTheComicsInIt() {
        val added = listOf(
            comic("Chapter 1.cbz", id = "a"), comic("Chapter 2.cbz", id = "b"), comic("Chapter 1.cbz", id = "c"), comic("Ch. 3.cbz", id = "d"),
            comic("Saga 001.cbz", id = "e"), comic("Saga 002.cbz", id = "f"),
        )
        val given = ComicSeries.assign(
            added, emptyList(),
            // As Mihon keeps them (a folder for each series), a volume in a folder of its own, and comics kept in a folder for comics.
            folders = mapOf("a" to "Frieren", "b" to "local/Frieren", "c" to "Dandadan/Volume 1", "e" to "Comics", "f" to "Comics"),
            rootFolder = "Download/Mangas/Blue Period",
        )
        assertEquals(ComicSeries.Given("Frieren", 1.0), given["a"])
        assertEquals(ComicSeries.Given("Frieren", 2.0), given["b"])
        assertEquals(ComicSeries.Given("Dandadan", 1.0), given["c"])
        // Straight inside the folder chosen, numbered only: that folder's.
        assertEquals(ComicSeries.Given("Blue Period", 3.0), given["d"])
        // A folder named for what's kept in it isn't a series: their names are.
        assertEquals(ComicSeries.Given("Saga", 1.0), given["e"])
        assertNull(ComicSeries.assign(listOf(comic("Ch. 3.cbz")), emptyList(), rootFolder = "Download")["Ch. 3.cbz"])
        assertEquals("Saga", ComicSeries.seriesFolder("Comics/Saga/Vol. 2"))
        assertNull(ComicSeries.seriesFolder("Download/Comics/001"))
    }

    @Test
    fun comicsWithASeriesKeepIt() {
        val given = ComicSeries.assign(listOf(comic("Saga 003.cbz", "The Saga", 3.0), comic("Saga 004.cbz")), emptyList())
        assertNull(given["Saga 003.cbz"])
        // Its name alone (one comic with no series) isn't enough.
        assertNull(given["Saga 004.cbz"])
        val all = ComicSeries.assignAll(listOf(comic("Hellboy 1.cbz"), comic("Hellboy 2.cbz"), comic("Hellboy 3.cbz", "Hellboy", 3.0)))
        assertEquals(setOf("Hellboy 1.cbz", "Hellboy 2.cbz"), all.keys)
        assertEquals("Hellboy", all.getValue("Hellboy 1.cbz").series)
    }

    @Test
    fun namesCompareWithTheirNumbersAsNumbers() {
        val names = listOf("Issue 10", "issue 2", "Issue 1", "Issue 1.5", "Annual")
        assertEquals(listOf("Annual", "Issue 1", "Issue 1.5", "issue 2", "Issue 10"), names.sortedWith { a, b -> ComicSeries.naturalCompare(a, b) })
    }
}
