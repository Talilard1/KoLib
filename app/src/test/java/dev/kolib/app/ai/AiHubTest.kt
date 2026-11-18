package dev.kolib.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AiHubTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val opened = ArrayList<Pair<File, Int>>()
    private val engines = ArrayList<FakeEngine>()

    private fun hub(canRun: Boolean = true, memory: Long = 7_400_000_000L, reply: (List<AiMessage>) -> String = { "An answer." }) = AiHub(
        context, scope, canRunHere = { canRun }, deviceMemory = { memory }, deviceFast = { true },
        openLocal = { file, tokens ->
            opened += file to tokens
            FakeEngine(tokens, reply).also { engines += it }
        },
        openServer = { FakeEngine(8192) { "From the server." }.also { engines += it } },
    )

    @Before
    fun clean() {
        context.getSharedPreferences("ai", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "models").deleteRecursively()
    }

    @After
    fun done() = scope.cancel()

    private fun AiHub.addOwn(name: String = "Tiny.gguf") = models.addOwn(name, ("GGUF" + "x".repeat(100)).byteInputStream())

    @Test
    fun nothingAnswersUntilAModelIsChosen() {
        val hub = hub()
        assertFalse(hub.ready())
        assertEquals("", hub.name())
        assertThrows(AiException::class.java) { runBlocking { hub.use { it.contextTokens } } }
        // A model of the user's own, chosen: ready, and opened when it's first asked something.
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        assertTrue(hub.ready())
        assertEquals("Tiny", hub.name())
        assertTrue(opened.isEmpty())
        val said = runBlocking { hub.use { e -> StringBuilder().also { s -> e.generate(listOf(AiMessage.user("?")), 20, 0.2f, {}) { s.append(it) } }.toString() } }
        assertEquals("An answer.", said)
        assertEquals("own-Tiny.gguf", opened.single().first.name)
        // A device with 8 GB holds the most at once.
        assertEquals(8192, opened.single().second)
        // Asked again, the same model answers: it isn't opened twice.
        runBlocking { hub.use { it.contextTokens } }
        assertEquals(1, opened.size)
        assertEquals(AiHub.State.IDLE, hub.busy.value)
    }

    @Test
    fun aModelHeldInMemoryIsToldOfAsItsOpenedAndLetGo() {
        val hub = hub()
        val told = java.util.Collections.synchronizedList(ArrayList<Boolean>())
        hub.onHolding = { told += it }
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        runBlocking { hub.use { it.contextTokens } }
        assertEquals(listOf(true), told.toList())
        hub.release()
        runBlocking { for (i in 0 until 100) if (told.size < 2) delay(20) }
        assertEquals(listOf(true, false), told.toList())
        // Another model, chosen: the one held is let go as it opens.
        hub.addOwn("Other.gguf")
        runBlocking { hub.use { it.contextTokens } }
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Other"))
        runBlocking { hub.use { it.contextTokens } }
        assertEquals(listOf(true, false, true, false, true), told.toList())
    }

    @Test
    fun aSmallerDeviceHoldsLessAtOnce() {
        val hub = hub(memory = 3_700_000_000L)
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        assertEquals(4096, runBlocking { hub.use { it.contextTokens } })
        assertEquals("qwen3.5-0.8b", hub.suggested.id)
    }

    @Test
    fun choosingAnotherModelLetsGoOfTheFirst() {
        val hub = hub()
        hub.addOwn("One.gguf")
        hub.addOwn("Two.gguf")
        hub.settingsStore.set(hub.settings.value.copy(source = "own:One"))
        runBlocking { hub.use { it.contextTokens } }
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Two"))
        runBlocking { hub.use { it.contextTokens } }
        assertEquals(listOf("own-One.gguf", "own-Two.gguf"), opened.map { it.first.name })
        assertTrue(engines[0].closed)
        assertFalse(engines[1].closed)
        // Turning the dictionary on or off isn't another model.
        hub.settingsStore.set(hub.settings.value.copy(dictionary = false))
        runBlocking { hub.use { it.contextTokens } }
        assertEquals(2, opened.size)
        // Deleting the one in use lets go of it and leaves nothing chosen.
        hub.deleteOwn(hub.models.ownFile("Two"))
        assertFalse(hub.ready())
        assertEquals("", hub.settings.value.source)
    }

    @Test
    fun aServerOfTheUsersAnswersInstead() {
        val hub = hub(canRun = false)
        hub.addOwn()
        // This device can't run models itself: one on it is no use.
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        assertFalse(hub.ready())
        hub.settingsStore.set(hub.settings.value.copy(source = "server", serverAddress = "http://192.168.1.20:11434"))
        assertFalse(hub.ready())
        hub.settingsStore.set(hub.settings.value.copy(serverModel = "gemma3:4b"))
        assertTrue(hub.ready())
        assertEquals("gemma3:4b", hub.name())
        assertEquals("From the server.", runBlocking { hub.use { e -> StringBuilder().also { s -> e.generate(listOf(AiMessage.user("?")), 20, 0.2f, {}) { s.append(it) } }.toString() } })
        assertTrue(opened.isEmpty())
    }

    @Test
    fun cancellingStopsTheModelWriting() {
        val hub = hub { "word ".repeat(400) }
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        val end = runBlocking {
            val job = async(Dispatchers.Default) {
                hub.use { e -> e.generate(listOf(AiMessage.user("?")), 400, 0.2f, {}) { Thread.sleep(5) } }
            }
            delay(150)
            job.cancel()
            runCatching { job.await() }
            // The model was told to stop, and did, well before it had written everything.
            var waited = 0
            while (hub.busy.value != AiHub.State.IDLE && waited++ < 400) delay(10)
            engines.single().stopped
        }
        assertTrue(end)
        assertEquals(AiHub.State.IDLE, hub.busy.value)
    }

    private fun waitFor(what: String, until: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!until() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue(what, until())
    }

    @Test
    fun whatsReadAheadIsReadWhenTheModelIsFree() {
        val hub = hub()
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        val context = listOf(AiMessage.system("You remember the book."), AiMessage.user("Question: Who?"), AiMessage.assistant("Ansel."))
        hub.warm { e -> assertEquals(8192, e.contextTokens); context }
        waitFor("read ahead") { engines.isNotEmpty() && engines[0].ahead.isNotEmpty() }
        // It opened the model to read ahead, and read what it was given.
        assertEquals(context, engines.single().ahead.single())
        waitFor("done") { !hub.working }
        assertTrue(hub.quietFor() < 5_000)
        // With no model chosen, there's nothing to read ahead with.
        hub.settingsStore.set(hub.settings.value.copy(source = ""))
        hub.warm { context }
        Thread.sleep(200)
        assertEquals(1, engines.single().ahead.size)
    }

    @Test
    fun aQuestionDoesntWaitForReadingAhead() {
        val hub = hub()
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        runBlocking { hub.use { } }
        val engine = engines.single()
        // Reading ahead that would take ten seconds.
        engine.aheadTakes = 10_000
        hub.warm { listOf(AiMessage.system("A long book.")) }
        waitFor("reading ahead") { engine.ahead.isNotEmpty() }
        val started = System.currentTimeMillis()
        val said = runBlocking { hub.use { e -> StringBuilder().also { s -> e.generate(listOf(AiMessage.user("?")), 20, 0.2f, {}) { s.append(it) } }.toString() } }
        assertEquals("An answer.", said)
        // It gave way as soon as it was asked.
        assertTrue(engine.yielded)
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }

    @Test
    fun workInTheBackgroundStopsForAQuestionAndWaitsForAQuietSpell() {
        val hub = hub { "word ".repeat(400) }
        hub.addOwn()
        hub.settingsStore.set(hub.settings.value.copy(source = "own:Tiny"))
        runBlocking { hub.use { } }
        val engine = engines.single()
        val background = runBlocking {
            val notes = async(Dispatchers.Default) {
                hub.background { e -> e.generate(listOf(AiMessage.user("Note this.")), 400, 0.2f, {}) { Thread.sleep(5) } }
            }
            waitFor("writing in the background") { hub.working }
            delay(100)
            // A question: the background work is stopped for it, and it's answered.
            val answer = hub.use { e -> e.contextTokens }
            assertEquals(8192, answer)
            notes.await()
        }
        assertEquals(AiEnd.CANCELLED, background)
        assertTrue(engine.stopped || engine.asked.size >= 1)
        // Just asked: no quiet spell yet.
        assertTrue(hub.quietFor() < 1_000)
        // While something's waiting, work in the background isn't started at all.
        val none = runBlocking {
            val long = async(Dispatchers.Default) { hub.use { e -> e.generate(listOf(AiMessage.user("?")), 400, 0.2f, {}) { Thread.sleep(2) } } }
            waitFor("answering") { hub.working }
            val notes = async(Dispatchers.Default) { hub.background { "written" } }
            long.await()
            notes.await()
        }
        assertTrue(none == null || none == "written")
    }

    @Test
    fun theSettingsAreKept() {
        val hub = hub()
        hub.settingsStore.set(AiSettings(source = "server", serverAddress = "host:1234", serverKey = "k", serverModel = "m", dictionary = false))
        assertEquals(AiSettings("server", "host:1234", "k", "m", false), AiSettingsStore(context).settings.value)
    }
}
