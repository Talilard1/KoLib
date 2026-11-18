package dev.kolib.app.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class ModelStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val content = ByteArray(300_000) { (it * 31 + it / 7).toByte() }
    private val sum = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val model = AiModel("test", "Test", "Nobody", "https://example.invalid/test.gguf", content.size.toLong(), sum, "Apache 2.0", 4, false) { "A model for tests." }

    private fun store() = ModelStore(File(folder.root, "models"))

    /** A server with the whole file, which gives the rest of it from where it's asked. */
    private fun serve(asked: MutableList<Long> = ArrayList()): (String, Long) -> ModelStore.Answer = { _, from ->
        asked += from
        ModelStore.Answer(if (from > 0) 206 else 200, ByteArrayInputStream(content, from.toInt(), content.size - from.toInt()))
    }

    @Test
    fun aModelIsDownloadedAndChecked() {
        val store = store()
        assertFalse(store.installed(model))
        val seen = ArrayList<Long>()
        store.download(model, serve()) { seen += it }
        assertTrue(store.installed(model))
        assertArrayEquals(content, store.file(model).readBytes())
        assertEquals(content.size.toLong(), seen.last())
        assertEquals(0L, store.partBytes(model))
        // Asked for again, it's already here: nothing is fetched.
        store.download(model, { _, _ -> throw AssertionError("fetched again") })
    }

    @Test
    fun aDownloadThatStoppedCarriesOnFromWhereItWas() {
        val store = store()
        // The connection drops a third of the way through.
        val broken: (String, Long) -> ModelStore.Answer = { _, _ -> ModelStore.Answer(200, ByteArrayInputStream(content, 0, 100_000)) }
        assertThrows(IOException::class.java) { store.download(model, broken) }
        assertFalse(store.installed(model))
        assertEquals(100_000L, store.partBytes(model))
        val asked = ArrayList<Long>()
        store.download(model, serve(asked))
        assertEquals(listOf(100_000L), asked)
        assertArrayEquals(content, store.file(model).readBytes())
    }

    @Test
    fun aServerThatStartsAgainFromTheBeginningIsFollowed() {
        val store = store()
        assertThrows(IOException::class.java) { store.download(model, { _, _ -> ModelStore.Answer(200, ByteArrayInputStream(content, 0, 50_000)) }) }
        // It ignores where it's asked to start from, and sends everything.
        store.download(model, { _, _ -> ModelStore.Answer(200, ByteArrayInputStream(content)) })
        assertArrayEquals(content, store.file(model).readBytes())
    }

    @Test
    fun aFileThatIsntTheModelIsThrownAway() {
        val store = store()
        val wrong = content.copyOf().also { it[1234] = (it[1234] + 1).toByte() }
        val e = assertThrows(IOException::class.java) { store.download(model, { _, _ -> ModelStore.Answer(200, ByteArrayInputStream(wrong)) }) }
        assertTrue(e.message!!.contains("whole"))
        assertFalse(store.installed(model))
        assertEquals(0L, store.partBytes(model))
    }

    @Test
    fun stoppingADownloadKeepsWhatsHere() {
        val store = store()
        var bytes = 0L
        assertThrows(ModelStore.StoppedException::class.java) {
            store.download(model, serve(), stillWanted = { bytes < 60_000 }) { bytes = it }
        }
        assertTrue(store.partBytes(model) in 60_000..content.size.toLong())
        assertFalse(store.installed(model))
    }

    @Test
    fun aServersErrorIsSaid() {
        val e = assertThrows(IOException::class.java) { store().download(model, { _, _ -> ModelStore.Answer(404, ByteArrayInputStream(ByteArray(0))) }) }
        assertTrue(e.message!!.contains("404"))
    }

    @Test
    fun deletingAModelRemovesItsFileAndAnyPartOfIt() {
        val store = store()
        store.download(model, serve())
        store.delete(model)
        assertFalse(store.installed(model))
        assertEquals(0L, store.storedBytes())
    }

    @Test
    fun aModelFoundOnHuggingFaceIsDownloadedAsOneOfTheUsersOwn() {
        val store = store()
        val found = ModelSearch.asModel(FoundFile("maker/Test-GGUF", "Test-Q4_K_M.gguf", content.size.toLong(), sum, "Q4_K_M"), "mit")
        assertEquals("https://huggingface.co/maker/Test-GGUF/resolve/main/Test-Q4_K_M.gguf", found.url)
        val asked = ArrayList<String>()
        store.download(found, { url, from -> asked += url; serve()(url, from) })
        assertEquals(listOf(found.url), asked)
        // Checked against what Hugging Face says the file is, and then listed with the user's own models.
        assertTrue(store.installed(found))
        assertEquals(listOf("Test-Q4_K_M"), store.own().map { store.ownName(it) })
        assertEquals(content.size.toLong(), store.storedBytes())
        // One that doesn't match is thrown away.
        val wrong = ModelSearch.asModel(FoundFile("maker/Test-GGUF", "Other-Q4_K_M.gguf", content.size.toLong(), "0".repeat(64), "Q4_K_M"), null)
        assertThrows(IOException::class.java) { store.download(wrong, serve()) }
        assertFalse(store.installed(wrong))
        assertEquals(1, store.own().size)
        // Deleted like any other.
        store.delete(found)
        assertTrue(store.own().isEmpty())
    }

    @Test
    fun theUsersOwnModelIsKeptIfItsAGgufFile() {
        val store = store()
        val gguf = "GGUF".toByteArray() + ByteArray(5000) { 7 }
        val file = store.addOwn("My Model-Q4_K_M.gguf", ByteArrayInputStream(gguf))
        assertEquals("My Model-Q4_K_M", store.ownName(file))
        assertEquals(listOf(file), store.own())
        assertArrayEquals(gguf, file.readBytes())
        assertEquals(file, store.ownFile("My Model-Q4_K_M"))
        // Anything else isn't taken, and leaves nothing behind.
        val e = assertThrows(IOException::class.java) { store.addOwn("notes.txt", ByteArrayInputStream("hello there".toByteArray())) }
        assertTrue(e.message!!.contains("GGUF"))
        assertThrows(IOException::class.java) { store.addOwn("empty.gguf", ByteArrayInputStream(ByteArray(0))) }
        assertEquals(1, store.own().size)
        assertEquals(1, File(folder.root, "models").listFiles()!!.size)
    }
}
