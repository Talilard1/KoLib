package dev.kolib.app.kobo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** What's on the Kobo is kept on the tablet, covers included, for when it isn't plugged in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LastSeenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val drive = File(context.cacheDir, "lastseen-" + UUID.randomUUID())

    @Test
    fun everythingReadFromTheKoboComesBackTheSame() = runBlocking {
        FakeKobo.create(drive)
        val repo = KoboRepository(context)
        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val store = LastSeenStore(File(context.filesDir, "seen-" + UUID.randomUUID()))
        assertNull(store.load())

        val seen = LastSeenStore.LastSeen(session.info, session.snapshot, session.connectedAtMillis, session.databaseBytes, session.databaseHadWal)
        store.save(seen)
        val back = store.load()
        assertNotNull(back)
        assertEquals(seen.info, back!!.info)
        assertEquals(seen.snapshot.allBooks, back.snapshot.allBooks)
        assertEquals(seen.snapshot.collections, back.snapshot.collections)
        assertEquals(seen.snapshot.collectionsByBook, back.snapshot.collectionsByBook)
        assertEquals(seen, back)
        assertEquals(session.snapshot.books.size, back.snapshot.books.size)

        // How the Kobo shares a book out between its chapters comes back too.
        val first = session.snapshot.allBooks.first()
        val spans = listOf(KoboChapterSpan("${first.contentId}!!OEBPS/ch1.xhtml", null, 0.0, 12.5), KoboChapterSpan("x", "OEBPS/ch2.xhtml", 12.5, 87.5))
        val withSpans = seen.copy(snapshot = session.snapshot.copy(allBooks = listOf(first.copy(chapterSpans = spans)) + session.snapshot.allBooks.drop(1)))
        store.save(withSpans)
        assertEquals(spans, store.load()!!.snapshot.allBooks.first().chapterSpans)
        store.save(seen)

        // A damaged copy is ignored rather than crashing the app.
        File(store.coverDir.parentFile, "last-seen.json").writeText("{ \"version\": 1, \"books\": [")
        assertNull(store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun coversStillShowWhenTheKoboIsUnplugged() = runBlocking {
        FakeKobo.create(drive)
        val repo = KoboRepository(context)
        repo.lastSeen.clear()
        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val books = session.snapshot.books
        val withCovers = books.filter { b -> FakeKobo.books.first { it.title == b.title }.cover }

        // Opening a big cover keeps a copy; the small ones are copied in the background.
        assertNotNull(session.covers.load(withCovers[0].imageId, CoverSize.FULL, 400))
        val copied = session.covers.keepThumbnails(books.map { it.imageId }) { true }
        assertEquals(withCovers.size, copied)
        assertEquals("nothing is copied twice", 0, session.covers.keepThumbnails(books.map { it.imageId }) { true })
        repo.saveLastSeen(session)

        // Unplugged: the Kobo's files are gone, but the saved view still has every cover.
        drive.deleteRecursively()
        val offline = repo.loadLastSeen()
        assertNotNull(offline)
        assertFalse(offline!!.live)
        assertEquals(session.snapshot, offline.snapshot)
        for (b in withCovers) assertNotNull(b.title, offline.covers.load(b.imageId, CoverSize.THUMBNAIL, 100))
        assertNotNull(offline.covers.load(withCovers[0].imageId, CoverSize.FULL, 400))
        assertNull("only covers looked at are kept full size", offline.covers.load(withCovers[1].imageId, CoverSize.FULL, 400))
        val leviathan = books.first { it.title == "Leviathan" }
        assertNull(offline.covers.load(leviathan.imageId, CoverSize.THUMBNAIL, 100))
        assertTrue(offline.readAtMillis > 0)
    }
}
