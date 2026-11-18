package dev.kolib.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/** The AI's notes on a book: kept with it, and written on the chapters the reader finishes. */
@RunWith(RobolectricTestRunner::class)
class BookNotesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engines = ArrayList<FakeEngine>()
    private val dir = Files.createTempDirectory("notes").toFile()

    private fun hub() = AiHub(
        context, scope, canRunHere = { true }, deviceMemory = { 7_400_000_000L }, deviceSpeed = { Device.Speed.FAST },
        openLocal = { _, tokens ->
            FakeEngine(tokens) { m ->
                val asked = m.last().content
                Regex("chapter “([^”]+)”").find(asked)?.let { "Notes on ${it.groupValues[1]}: who's in it and what happens." } ?: "A note."
            }.also { engines += it }
        },
    ).also { hub ->
        hub.models.addOwn("Tiny.gguf", ("GGUF" + "x".repeat(100)).byteInputStream())
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
    }

    @Before
    fun clean() {
        context.getSharedPreferences("ai", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "models").deleteRecursively()
    }

    @After
    fun done() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun notesAreKeptWithTheBook() {
        val file = File(dir, "ai-notes.json")
        val notes = BookNotes(file)
        assertFalse(notes.on.value)
        notes.setOn(true)
        notes.put("one.xhtml", " Mirren crosses to the island. ")
        notes.skip("title.xhtml")
        assertEquals("Mirren crosses to the island.", notes.note("one.xhtml"))
        assertTrue(notes.done("one.xhtml") && notes.done("title.xhtml") && !notes.done("two.xhtml"))
        assertNull(notes.note("title.xhtml"))
        assertEquals(1, notes.written.value)
        // There again when the book is next opened.
        val again = BookNotes(file)
        assertTrue(again.on.value)
        assertEquals("Mirren crosses to the island.", again.note("one.xhtml"))
        assertTrue(again.done("title.xhtml"))
        assertFalse(File(dir, "ai-notes.json.part").exists())
    }

    private val chapters = listOf(NoteChapter(1, "one.xhtml", "The Ferry"), NoteChapter(2, "two.xhtml", "The Quay"), NoteChapter(3, "short.xhtml", "Epigraph"), NoteChapter(4, "three.xhtml", "The Lamp"))
    private fun text(i: Int) = if (i == 3) listOf("A short line.") else (1..30).map { "Line $it of chapter $i, with Mirren and the sea." }

    private fun waitFor(what: String, until: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!until() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue(what, until())
    }

    @Test
    fun notesAreWrittenOnTheChaptersFinishedTheLatestFirst() {
        val hub = hub()
        val notes = BookNotes(File(dir, "ai-notes.json"))
        val statuses = ArrayList<NotesWriter.Status>()
        val writer = NotesWriter(notes, hub, BookAi.Book("The Keeper's Island", "A. Writer", "en"), { chapters }, ::text, blocked = { null }, quiet = 0, every = 20)
        runBlocking {
            val job = launch(Dispatchers.Default) { writer.run() }
            // Off: nothing's written.
            Thread.sleep(200)
            assertEquals(NotesWriter.Status.Off, writer.status.value)
            assertTrue(engines.isEmpty())
            notes.setOn(true)
            waitFor("notes written") { notes.written.value == 3 }
            waitFor("up to date") { writer.status.value == NotesWriter.Status.UpToDate }
            job.cancel()
        }
        assertEquals("Notes on The Lamp: who's in it and what happens.", notes.note("three.xhtml"))
        assertEquals("Notes on The Ferry: who's in it and what happens.", notes.note("one.xhtml"))
        // The latest first; the short one isn't given a note.
        val asked = engines.single().asked.map { m -> Regex("chapter “([^”]+)”").find(m.last().content)?.groupValues?.get(1) }
        assertEquals(listOf("The Lamp", "The Quay", "The Ferry"), asked)
        assertTrue(notes.done("short.xhtml"))
        assertNull(notes.note("short.xhtml"))
        assertEquals(3 to 3, writer.progress())
    }

    @Test
    fun notesWaitForTheChargerWhenAskedTo() {
        val hub = hub()
        val notes = BookNotes(File(dir, "ai-notes.json")).apply { setOn(true) }
        var plugged = false
        val writer = NotesWriter(notes, hub, BookAi.Book("T", null, "en"), { chapters }, ::text,
            blocked = { if (plugged) null else NotesWriter.Wait.CHARGER }, quiet = 0, every = 20)
        runBlocking {
            val job = launch(Dispatchers.Default) { writer.run() }
            waitFor("waiting") { writer.status.value == NotesWriter.Status.Waiting(NotesWriter.Wait.CHARGER) }
            Thread.sleep(150)
            assertEquals(0, notes.written.value)
            plugged = true
            waitFor("written once plugged in") { notes.written.value == 3 }
            job.cancel()
        }
    }

    @Test
    fun aQuestionComesFirstAndTheNoteIsWrittenAfter() {
        val hub = hub()
        val notes = BookNotes(File(dir, "ai-notes.json")).apply { setOn(true) }
        val writer = NotesWriter(notes, hub, BookAi.Book("T", null, "en"), { chapters.take(1) }, ::text, blocked = { null }, quiet = 400, every = 20)
        runBlocking {
            // Something was just asked: the notes wait for a quiet spell.
            hub.use { }
            val job = launch(Dispatchers.Default) { writer.run() }
            Thread.sleep(200)
            assertEquals(0, notes.written.value)
            assertEquals(NotesWriter.Status.Waiting(null), writer.status.value)
            waitFor("written after the quiet spell") { notes.written.value == 1 }
            job.cancel()
        }
    }
}
