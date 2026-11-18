package dev.kolib.app

import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.TestEpubs
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.kobo.CoverFitting
import dev.kolib.app.kobo.KoboPaths
import dev.kolib.app.kobo.SendFormat
import dev.kolib.app.library.BookSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.util.UUID

/**
 * The whole way through, with the app's own two halves (the Kobo's and the library's) as they run together:
 * a book sent to the Kobo is put in its collections there in the same connection, before "Sent" is said, so
 * the Kobo doesn't have to be plugged in a second time for its collections to be right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SentBooksCollectionsTest {

    private val app = ApplicationProvider.getApplicationContext<KoLibApp>()

    private fun makeCollectionOnKobo(dbFile: File, name: String) {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL(
                "INSERT INTO Shelf VALUES ('2026-03-01T00:00:00Z', ?, ?, '2026-03-01T00:00:00Z', ?, 'UserTag', 'false', 'true', 'false', NULL, NULL)",
                arrayOf("id-$name", name, name),
            )
        }
    }

    private fun shelfRows(dbFile: File, contentId: String): List<String> =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("SELECT ShelfName FROM ShelfContent WHERE ContentId = ? AND _IsDeleted != 'true' ORDER BY ShelfName", arrayOf(contentId)).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }

    /** Lets the app's work (on the main thread and off it) go on until [done], for up to [seconds]. */
    private fun until(what: String, seconds: Int = 60, done: () -> Boolean) {
        val main = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + seconds * 1000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Still waiting after $seconds s: $what" }
            main.idleFor(Duration.ofMillis(50))
            Thread.sleep(10)
        }
    }

    @Test
    fun aBookSentIsInItsCollectionsOnTheKoboWithoutPluggingItInAgain() {
        val drive = File(app.cacheDir, "sent-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val dbFile = File(drive, ".kobo/KoboReader.sqlite")
        makeCollectionOnKobo(dbFile, "Greek History")
        makeCollectionOnKobo(dbFile, "Favourites")

        val vm = MainViewModel(app)
        val libraryVm = LibraryViewModel(app)
        vm.onTreePicked(FakeDocumentsProvider.install(drive))
        until("the Kobo is read, backed up and checked") {
            val s = vm.state.value
            s.connection is Connection.Connected && s.afterConnect != null && !s.afterConnectRunning && !s.syncing
        }
        val connected = vm.state.value.connection as Connection.Connected
        assertEquals("ok", vm.state.value.afterConnect?.integrity)

        // A book in the library, in two collections the Kobo has.
        val source = TestEpubs.calibreEpub2(File(app.cacheDir, "sent-" + UUID.randomUUID() + "/h.epub"))
        runBlocking { app.library.import("h.epub", { source.inputStream() }, BookSource.FILE, collections = listOf("Greek History", "Favourites")) }
        val book = app.library.books.value.first { it.collections.contains("Greek History") }
        assertNull(book.koboPath)

        libraryVm.sendToKobo(connected.session, listOf(book.id), SendFormat.KEPUB, CoverFitting.BLUR, false)
        until("the send says it's done") { libraryVm.job.value?.finished == true }
        assertEquals(1, libraryVm.job.value?.succeeded)

        // The book's file is on the Kobo, and its collections are in the Kobo's database already.
        val path = app.library.book(book.id)?.koboPath
        assertNotNull(path)
        assertTrue(File(drive, path!!).exists())
        assertEquals(listOf("Favourites", "Greek History"), shelfRows(dbFile, KoboPaths.contentId(path)))

        // All in the one connection: it was never opened a second time.
        val now = vm.state.value.connection as Connection.Connected
        assertEquals(connected.session.connectedAtMillis, now.session.connectedAtMillis)
        assertTrue(!now.unplugged)

        // Taken out of one in the library afterwards, still connected: it comes out on the Kobo too.
        val current = app.library.book(book.id)!!
        libraryVm.saveDetails(current.id, current.meta, current.collections - "Favourites") {}
        // (Looked at while the app may be part-way through writing it: a look that can't be had yet is tried again.)
        until("the change reaches the Kobo") { runCatching { shelfRows(dbFile, KoboPaths.contentId(path)) }.getOrNull() == listOf("Greek History") }
        until("the Kobo is no longer being written to") { !vm.state.value.syncing }
        // Nothing of the writing is left beside the database for the Kobo to trip over.
        assertEquals(emptyList<String>(), dbFile.parentFile!!.list()!!.filter { it.endsWith("-journal") || it.endsWith("-wal") })
    }
}
