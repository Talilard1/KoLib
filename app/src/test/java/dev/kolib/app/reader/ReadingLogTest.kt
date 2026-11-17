package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The time a book is read for, as the readers count it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private fun log(file: File = File(tmp.root, "reading-log.json")) = ReadingLog(file) { it.run() }
    private fun session(log: ReadingLog, id: String = "a") = ReadingSession(log, id, "The Garden") { now }
    private fun wait(seconds: Int) { now += seconds * 1000L }

    @Test
    fun timeOnEachPageIsCountedWhenItsTurned() {
        val log = log()
        val s = session(log)
        s.resume()
        wait(40); s.moved(words = 220)
        wait(50); s.moved(words = 260)
        wait(30); s.pause()
        val read = log.books.value.getValue("a")
        assertEquals(120L, read.seconds)
        assertEquals(2, read.pages)
        assertEquals(480L, read.words)
        // The words took 90 seconds: the half minute before leaving had none counted to it.
        assertEquals(90L, read.wordSeconds)
        assertEquals("One sitting, once it had lasted a minute", 1, read.sessions)
        assertEquals("The Garden", read.title)
    }

    @Test
    fun aBookLeftOpenIsntReadAllNight() {
        val log = log()
        val s = session(log)
        s.resume()
        wait(8 * 3600); s.moved(words = 200)
        assertEquals(ReadingSession.LONGEST, log.books.value.getValue("a").seconds)
    }

    @Test
    fun pagesLeafedThroughArentRead() {
        val log = log()
        val s = session(log)
        s.resume()
        repeat(20) { wait(1); s.moved(words = 250) }
        val read = log.books.value.getValue("a")
        assertEquals(0, read.pages)
        assertEquals(0L, read.words)
        assertEquals("The time still counts", 20L, read.seconds)
        assertEquals("but twenty seconds is no sitting", 0, read.sessions)
    }

    @Test
    fun nothingIsCountedWhileTheReaderIsOffScreen() {
        val log = log()
        val s = session(log)
        s.resume()
        wait(60); s.pause()
        wait(3600)
        s.moved(words = 100)
        s.resume()
        wait(60); s.pause()
        val read = log.books.value.getValue("a")
        assertEquals(120L, read.seconds)
        assertEquals("Two sittings", 2, read.sessions)
    }

    @Test
    fun theSpeedIsTheReadersOwnOnceEnoughIsRead() {
        val log = log()
        assertEquals(ReadingLog.ORDINARY, log.speedFor("a"), 0.01f)
        val s = session(log)
        s.resume()
        // Twelve pages at 300 words in a minute each.
        repeat(12) { wait(60); s.moved(words = 300) }
        s.pause()
        assertEquals(300f, log.books.value.getValue("a").wordsPerMinute!!, 0.5f)
        assertEquals(300f, log.speedFor("a"), 0.5f)
        assertEquals("A book not yet read goes by the reader's speed in others", 300f, log.speedFor("b"), 0.5f)
        assertEquals(12, log.books.value.getValue("a").minutesPerSession)
        assertEquals(1f, log.books.value.getValue("a").pagesPerMinute!!, 0.01f)
    }

    @Test
    fun aFewPagesArentEnoughToSayHowFast() {
        val log = log()
        val s = session(log)
        s.resume()
        wait(60); s.moved(words = 300)
        s.pause()
        assertNull(log.books.value.getValue("a").wordsPerMinute)
        assertEquals(ReadingLog.ORDINARY, log.speedFor("a"), 0.01f)
    }

    @Test
    fun theLogIsKeptAndReadBack() {
        val file = File(tmp.root, "reading-log.json")
        val log = log(file)
        val s = session(log)
        s.resume()
        wait(90); s.moved(words = 400)
        s.pause()
        session(log, "b").apply { resume(); wait(70); pause() }
        val again = log(file).also { it.load() }
        assertEquals(log.books.value, again.books.value)
        assertEquals(160L, again.total.seconds)
        assertEquals(2, again.total.sessions)
        assertEquals("The day it was read on", 1, again.books.value.getValue("a").days.size)
        assertTrue(again.books.value.getValue("a").days.values.single() == 90)
    }

    @Test
    fun onlyTheLastDaysAreKept() {
        val log = log()
        val day = 24 * 3600 * 1000L
        repeat(ReadingLog.DAYS_KEPT + 20) { log.record("a", "The Garden", 600, 10, 0, session = true, now = now + it * day) }
        val read = log.books.value.getValue("a")
        assertEquals(ReadingLog.DAYS_KEPT, read.days.size)
        assertEquals("but the whole time", (ReadingLog.DAYS_KEPT + 20) * 600L, read.seconds)
        assertNotNull(read.days.keys.maxOrNull())
    }
}
