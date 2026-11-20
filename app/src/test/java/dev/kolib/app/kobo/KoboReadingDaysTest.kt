package dev.kolib.app.kobo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Kobo's reading, day by day: worked out from how long each book has been read for in all, each time the Kobo's
 * copy is saved, and kept apart from the reading done in KoLib.
 */
@RunWith(RobolectricTestRunner::class)
class KoboReadingDaysTest {

    private val zone = ZoneOffset.UTC
    private fun at(day: String, hour: Int = 12): Long = LocalDate.parse(day).atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun book(id: String, seconds: Int?, lastRead: Long?) = KoboBook(
        contentId = id, title = id, author = null, subtitle = null, series = null, seriesNumber = null, description = null,
        publisher = null, language = null, isbn = null, mimeType = null, format = "EPUB", imageId = id, kind = BookKind.SIDELOADED,
        isDownloaded = true, isExpired = false, readStatus = ReadStatus.READING, percentRead = 10, lastReadMillis = lastRead, addedMillis = null,
        fileSize = null, relativePath = "Books/$id.kepub.epub", secondsRead = seconds,
    )

    private fun snapshot(vararg books: KoboBook) = KoboSnapshot(dbVersion = 170, allBooks = books.toList(), collections = emptyList(), collectionsByBook = emptyMap(), warnings = emptyList())

    private val folder: File = Files.createTempDirectory("kobo-days").toFile()

    @Test
    fun theTimeAddedSinceTheLastCopyGoesOnTheDayTheBookWasLastRead() {
        // The first time a book is seen, its earlier reading isn't counted: when it was done isn't known.
        KoboReadingDays.update(folder, snapshot(book("a", 600, at("2026-11-10")), book("b", null, null)), at("2026-11-10", 20), zone)
        assertTrue(KoboReadingDays.load(folder).isEmpty())

        // Read for ten more minutes on the 18th, connected on the 20th.
        KoboReadingDays.update(folder, snapshot(book("a", 1200, at("2026-11-18")), book("b", null, null)), at("2026-11-20"), zone)
        assertEquals(mapOf("2026-11-18" to 600), KoboReadingDays.load(folder))

        // Another book, and more of the first, read on the 20th; nothing changed is nothing added.
        KoboReadingDays.update(folder, snapshot(book("a", 1500, at("2026-11-20")), book("c", 100, at("2026-11-19"))), at("2026-11-20", 21), zone)
        KoboReadingDays.update(folder, snapshot(book("a", 1500, at("2026-11-20")), book("c", 400, at("2026-11-20"))), at("2026-11-20", 22), zone)
        KoboReadingDays.update(folder, snapshot(book("a", 1500, at("2026-11-20")), book("c", 400, at("2026-11-20"))), at("2026-11-20", 23), zone)
        assertEquals(mapOf("2026-11-18" to 600, "2026-11-20" to 600), KoboReadingDays.load(folder))
    }

    @Test
    fun aDayAheadOfNowCountsAsTodayAndOldDaysGo() {
        KoboReadingDays.update(folder, snapshot(book("a", 100, at("2025-01-01"))), at("2025-01-01"), zone)
        KoboReadingDays.update(folder, snapshot(book("a", 200, at("2025-01-02"))), at("2025-01-02"), zone)
        // The Kobo's clock is a week fast.
        KoboReadingDays.update(folder, snapshot(book("a", 500, at("2026-11-27"))), at("2026-11-20"), zone)
        // Over a year on, the day from 2025 has gone.
        assertEquals(mapOf("2026-11-20" to 300), KoboReadingDays.load(folder))
    }

    @Test
    fun aDamagedRecordStartsAgain() {
        File(folder, "reading-days.json").writeText("{not json")
        assertTrue(KoboReadingDays.load(folder).isEmpty())
        KoboReadingDays.update(folder, snapshot(book("a", 100, at("2026-11-19"))), at("2026-11-19"), zone)
        KoboReadingDays.update(folder, snapshot(book("a", 160, at("2026-11-20"))), at("2026-11-20"), zone)
        assertEquals(mapOf("2026-11-20" to 60), KoboReadingDays.load(folder))
    }
}
