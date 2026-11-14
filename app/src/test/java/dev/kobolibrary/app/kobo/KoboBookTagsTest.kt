package dev.kobolibrary.app.kobo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.TestEpubs
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Tags and ratings that only the Kobo's book files have. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KoboBookTagsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val drive = File(context.cacheDir, "tags-" + UUID.randomUUID())

    @Test
    fun zipPeekReadsSingleFilesLikeAZipReaderDoes() {
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "peek-${UUID.randomUUID()}.epub"),
            extra = mapOf("OEBPS/big.txt" to ByteArray(200_000) { (it % 7 + 65).toByte() }))
        val peek = ZipPeek.of(file)
        ZipFile(file).use { z ->
            assertEquals(z.entries().toList().map { it.name }, peek.entries.keys.toList())
            for (e in z.entries()) assertArrayEquals(e.name, z.getInputStream(e).readBytes(), peek.read(e.name))
        }
        assertNull(peek.read("nope.xhtml"))
        val (tags, rating) = ZipPeek.bookTags(peek)!!
        assertEquals(listOf("History", "Classics"), tags)
        assertEquals(8, rating)
    }

    @Test
    fun readsTagsFromTheKobosFilesOnceAndKeepsThem() = runBlocking {
        FakeKobo.create(drive)
        val repo = KoboRepository(context)
        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val books = session.snapshot.books
        var progressCalls = 0
        val found = KoboTagReader.read(session, books, emptyMap()) { _, _, _ -> progressCalls++ }
        assertTrue(progressCalls > 0)
        val meditations = books.first { it.title == "Meditations" }
        assertEquals(listOf("Philosophy", "Stoicism"), found[KoboTagReader.key(meditations.relativePath!!)]?.tags)
        assertEquals(listOf("Horror", "Gothic"), found[KoboTagReader.key(books.first { it.title == "Dracula" }.relativePath!!)]?.tags)
        // Store books have no file to read.
        assertEquals(books.count { it.isSideloaded }, found.size)

        val store = KoboTagStore(File(context.filesDir, "tags-${UUID.randomUUID()}.json"))
        store.save(found)
        assertEquals(found, store.load())

        // Next time only new or changed files are opened.
        drive.resolve("Books/Plato").deleteRecursively()
        var read = -1
        val again = KoboTagReader.read(session, books, found) { _, total, _ -> read = total }
        assertEquals(-1, read)
        assertEquals(found, again)

        // The Kobo's own stars and word counts come from its database.
        assertEquals(5, meditations.rating)
        assertEquals(189_000, books.first { it.title == "The Histories" }.words)
    }
}
