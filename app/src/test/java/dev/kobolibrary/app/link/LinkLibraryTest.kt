package dev.kobolibrary.app.link

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.TestEpubs
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.ImportOutcome
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.ReaderStore
import dev.kobolibrary.app.reader.ReadingHereIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Books going from one device's library to another's, with everything kept with them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LinkLibraryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "linklib-" + UUID.randomUUID()
    private val base = File(context.filesDir, name)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val links = ArrayList<LinkManager>()

    private class Side(val library: Library, val here: ReadingHereIndex, val link: LinkManager)

    private fun side(who: String): Side {
        val library = Library(context, File(base, "$who-books"), "$name-$who.db")
        runBlocking { library.load() }
        val here = ReadingHereIndex()
        val link = LinkManager(LinkStore(File(base, "$who-link")), AppLinkHost(context, library, here), { who }, scope, port = 0, discover = false)
        link.start()
        links += link
        return Side(library, here, link)
    }

    @After
    fun tearDown() {
        links.forEach { it.stop() }
        scope.cancel()
        base.deleteRecursively()
        File(context.cacheDir, name).deleteRecursively()
    }

    private fun waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000
        while (!done()) {
            if (System.currentTimeMillis() > until) throw AssertionError("Waited too long for $what")
            Thread.sleep(20)
        }
    }

    private fun pair(a: Side, b: Side): PairedDevice {
        val near = runBlocking { a.link.reach("127.0.0.1:${b.link.listeningPort.value}") }!!
        a.link.pair(near)
        waitFor("codes") { a.link.pairing.value?.code != null && b.link.pairing.value?.code != null }
        a.link.answer(true)
        b.link.answer(true)
        waitFor("pairing") { a.link.paired.value.isNotEmpty() && b.link.paired.value.isNotEmpty() }
        return a.link.paired.value.single()
    }

    private fun jpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(0xFF2C4A6E.toInt())
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun add(side: Side): LibraryBook = runBlocking {
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/The Histories.epub"), extra = mapOf("OEBPS/cover.jpeg" to jpeg()))
        (side.library.import("The Histories.epub", { file.inputStream() }, BookSource.FILE, collections = listOf("Classics")) as ImportOutcome.Added).book
    }

    @Test
    fun aBookGoesWithItsDetailsCoverCollectionsAndReading() {
        val tablet = side("tablet")
        val phone = side("phone")
        val toPhone = pair(tablet, phone)
        val book = add(tablet)
        // Read on the tablet, with a highlight and a bookmark.
        File(tablet.library.bookDir(book.id), "reader.json").writeText(
            JSONObject()
                .put("position", JSONObject().put("path", "OEBPS/text.xhtml").put("progress", 0.3).put("book", 0.42).put("at", 10_000L))
                .put("bookmarks", JSONArray().put(JSONObject().put("path", "OEBPS/text.xhtml").put("anchor", "p:2").put("progress", 0.1)))
                .put("annotations", JSONArray().put(JSONObject().put("id", "h1").put("path", "OEBPS/text.xhtml").put("start", "a").put("end", "b").put("text", "Herodotus").put("modified", 5L)))
                .toString()
        )

        runBlocking { tablet.link.sendBooks(toPhone, listOf(book.id)).join() }
        val there = phone.library.book(book.id)
        assertNotNull("The book didn't arrive", there)
        assertEquals("The Histories", there!!.title)
        assertEquals(listOf("Herodotus"), there.meta.authors)
        assertEquals(listOf("Classics"), there.collections)
        assertTrue(there.hasCover)
        assertArrayEquals(tablet.library.epubFile(book.id).readBytes(), phone.library.epubFile(book.id).readBytes())
        assertArrayEquals(tablet.library.coverFile(book.id).readBytes(), phone.library.coverFile(book.id).readBytes())
        val reading = JSONObject(File(phone.library.bookDir(book.id), "reader.json").readText())
        assertEquals(10_000L, reading.getJSONObject("position").getLong("at"))
        assertEquals("Herodotus", reading.getJSONArray("annotations").getJSONObject(0).getString("text"))
        assertEquals(1, reading.getJSONArray("bookmarks").length())
        assertEquals(0.42f, phone.here.books.value[book.id]!!.progress, 0.001f)

        // Edited on the tablet: sent again, the phone's copy is replaced, and where the phone had got to is kept.
        File(phone.library.bookDir(book.id), "reader.json").writeText(
            JSONObject(File(phone.library.bookDir(book.id), "reader.json").readText())
                .put("position", JSONObject().put("path", "OEBPS/text.xhtml").put("progress", 0.9).put("book", 0.8).put("at", 20_000L)).toString()
        )
        val edited = runBlocking { tablet.library.updateDetails(book.id, book.meta.copy(title = "The Histories (Penguin)"), listOf("Classics", "Greek")) }
        runBlocking { tablet.link.sendBooks(toPhone, listOf(book.id)).join() }
        val updated = phone.library.book(book.id)!!
        assertEquals("The Histories (Penguin)", updated.title)
        assertEquals(edited.modifiedAt, updated.modifiedAt)
        assertEquals(listOf("Classics", "Greek"), updated.collections)
        assertTrue(updated.coverVersion > there.coverVersion)
        assertEquals(20_000L, JSONObject(File(phone.library.bookDir(book.id), "reader.json").readText()).getJSONObject("position").getLong("at"))
        assertEquals(1, phone.library.books.value.size)

        // Only its collections changed: they go, without the book.
        runBlocking { tablet.library.updateDetails(book.id, edited.meta, listOf("Greek")) }
        runBlocking { tablet.link.sendBooks(toPhone, listOf(book.id)).join() }
        assertEquals(listOf("Greek"), phone.library.book(book.id)!!.collections)

        // Places: the phone's later place goes back to the tablet.
        runBlocking { tablet.link.syncPlaces(toPhone).join() }
        assertEquals(0.8f, ReaderStore.readHereOf(tablet.library.bookDir(book.id))!!.progress, 0.001f)
        assertEquals(0.8f, tablet.here.books.value[book.id]!!.progress, 0.001f)
        // The highlight is still there.
        assertEquals(1, JSONObject(File(tablet.library.bookDir(book.id), "reader.json").readText()).getJSONArray("annotations").length())
    }

    @Test
    fun aBookAddedSeparatelyOnBothIsntSentTwice() {
        val tablet = side("tablet")
        val phone = side("phone")
        val toPhone = pair(tablet, phone)
        val mine = add(tablet)
        val theirs = add(phone)
        File(tablet.library.bookDir(mine.id), "reader.json").writeText(
            JSONObject().put("finished", true).put("finishedAt", 30_000L).toString()
        )
        runBlocking { tablet.link.sendBooks(toPhone, listOf(mine.id)).join() }
        // Still one book on the phone, with the tablet's reading put with it.
        assertEquals(listOf(theirs.id), phone.library.books.value.map { it.id })
        assertTrue(ReaderStore.readHereOf(phone.library.bookDir(theirs.id))!!.finished)
    }
}
