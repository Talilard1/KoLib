package dev.kolib.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.kobo.BookKind
import dev.kolib.app.kobo.CoverSize
import dev.kolib.app.kobo.KoboNotFoundException
import dev.kolib.app.kobo.KoboRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The whole connect path, end to end, against a simulated Kobo drive: Android's document APIs,
 * copying and backing up the database, reading books and collections, covers, and the write check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KoboConnectionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun drive(): File = File(context.cacheDir, "fake-kobo").also { FakeKobo.create(it) }

    @Test
    fun connectsReadsAndBacksUp() = runBlocking {
        val root = drive()
        val tree = FakeDocumentsProvider.install(root)
        val repo = KoboRepository(context)
        val steps = mutableListOf<String>()

        val session = repo.connect(tree) { steps += it }

        assertTrue("progress steps reported", steps.size >= 4)
        assertEquals("4.38.23171", session.info.version?.firmware)
        assertEquals(174, session.snapshot.dbVersion)
        assertFalse(session.databaseHadWal)
        assertTrue("database copy progress reported", steps.any { it.startsWith("Copying the Kobo's database… ") && it.endsWith("100%") })
        assertEquals(true, session.tree.directIdsWork)

        val snap = session.snapshot
        assertEquals(FakeKobo.books.size, snap.books.size)
        assertEquals(16, snap.sideloadedCount)
        assertEquals(2, snap.purchasedOnDeviceCount)
        assertEquals(FakeKobo.collections, snap.collections.map { it.name })
        val holmes = snap.collections.first { it.name == "Sherlock Holmes" }
        assertEquals(
            listOf("A Study in Scarlet", "The Sign of the Four", "The Hound of the Baskervilles"),
            snap.booksIn(holmes).map { it.title },
        )
        val histories = snap.books.first { it.title == "The Histories" }
        assertEquals(listOf("Classics", "History", "To Read"), snap.collectionsByBook[histories.contentId])
        assertEquals(BookKind.SIDELOADED, histories.kind)
        assertEquals("Books/Herodotus/The Histories.kepub.epub", histories.relativePath)

        // The backup and health check run after connecting. The first connection always makes a backup,
        // zipped, holding exactly the Kobo's database; an identical database is not backed up twice.
        val after = repo.afterConnect(session)
        assertEquals("ok", after.integrity)
        assertNull(after.backupError)
        val backup = after.backup
        assertNotNull(backup)
        assertTrue(backup!!.isCompressed)
        val zipped = java.util.zip.ZipFile(backup.file).use { zip ->
            zip.getInputStream(zip.getEntry("KoboReader.sqlite")).readBytes()
        }
        assertTrue("backup matches the Kobo's database", zipped.contentEquals(File(root, ".kobo/KoboReader.sqlite").readBytes()))
        assertEquals(1, repo.backups.list().size)
        assertNull(repo.backupNow(session))
        assertEquals(1, repo.backups.list().size)
        assertNull("a second connection with nothing changed makes no backup", repo.afterConnect(repo.connect(tree) {}).backup)

        // Covers come from the Kobo's own .kobo-images cache.
        val thumb = session.covers.load(histories.imageId, CoverSize.THUMBNAIL, 300)
        assertNotNull(thumb)
        assertEquals(149, thumb!!.width)
        val full = session.covers.load(histories.imageId, CoverSize.FULL, 200)
        assertNotNull(full)
        assertEquals(268, full!!.width) // 536 px, sampled down by 2 for a 200 px target
        val leviathan = snap.books.first { it.title == "Leviathan" }
        assertNull(session.covers.load(leviathan.imageId, CoverSize.THUMBNAIL, 300))

        // The write check leaves nothing behind.
        val before = root.listFiles()!!.map { it.name }.toSet()
        val check = repo.writeCheck(session)
        assertTrue(check.message, check.ok)
        assertEquals(before, root.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun aChangedDatabaseIsBackedUpAgain() = runBlocking {
        val root = drive()
        val tree = FakeDocumentsProvider.install(root)
        val repo = KoboRepository(context)
        val session = repo.connect(tree) {}
        repo.afterConnect(session)
        File(root, ".kobo/KoboReader.sqlite").appendBytes(ByteArray(16)) // any change to the bytes
        assertNotNull(repo.backupNow(session))
        assertEquals(2, repo.backups.list().size)
    }

    @Test
    fun refusesADriveThatIsNotAKobo() = runBlocking {
        val root = File(context.cacheDir, "usb-stick").apply { deleteRecursively(); mkdirs() }
        File(root, "photo.jpg").writeText("x")
        val tree = FakeDocumentsProvider.install(root)
        try {
            KoboRepository(context).connect(tree) {}
            fail("Expected KoboNotFoundException")
        } catch (e: KoboNotFoundException) {
            assertTrue(e.message!!.contains(".kobo"))
        }
    }

    @Test
    fun reportsAnUnpluggedDrive() = runBlocking {
        val root = drive()
        val tree = FakeDocumentsProvider.install(root)
        root.deleteRecursively() // the drive vanished
        try {
            KoboRepository(context).connect(tree) {}
            fail("Expected KoboNotFoundException")
        } catch (e: KoboNotFoundException) {
            assertTrue(e.message!!.contains("isn't available"))
        }
    }
}
