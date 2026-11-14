package dev.kobolibrary.app.kobo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
import java.io.File
import java.util.UUID

/** Changing the (fake) Kobo's database: on a copy, checked, backed up, and swapped in safely. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KoboDatabaseWriterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val drive = File(context.cacheDir, "writer-" + UUID.randomUUID())
    private val dbOnDrive get() = File(drive, ".kobo/KoboReader.sqlite")

    private fun connect(): Pair<KoboRepository, KoboSession> = runBlocking {
        val repo = KoboRepository(context)
        repo to repo.connect(FakeDocumentsProvider.install(drive)) {}
    }

    /** Reads the Kobo's database as it is on the drive (from a copy, so the drive isn't touched). */
    private fun <T> onDrive(block: (SQLiteDatabase) -> T): T {
        val copy = File(context.cacheDir, "look-" + UUID.randomUUID() + ".sqlite")
        dbOnDrive.copyTo(copy)
        File(dbOnDrive.path + "-wal").takeIf { it.exists() }?.copyTo(File(copy.path + "-wal"))
        return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use(block)
    }

    private fun SQLiteDatabase.one(sql: String, vararg args: String): String? =
        rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun leftovers() = File(drive, ".kobo").list().orEmpty().filter { it.startsWith("KoboReader.sqlite") && it != "KoboReader.sqlite" }

    @Test
    fun changesGoOntoTheKoboAfterABackupAndACheck() = runBlocking {
        FakeKobo.create(drive)
        val (repo, session) = connect()
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        val republic = session.snapshot.books.first { it.title == "The Republic" }
        val before = dbOnDrive.readBytes()
        val backupsBefore = repo.backups.list().size
        val tablesBefore = onDrive { db -> db.one("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'") }

        val after = repo.writeDatabase(session) { e ->
            var n = 0
            if (e.addToCollection("Philosophy", iliad.contentId)) n++
            if (e.addToCollection("history", iliad.contentId)) n++ // the Kobo's "History", whatever the case
            if (e.removeFromCollection("To Read", iliad.contentId)) n++
            if (e.addToCollection("Classics", iliad.contentId)) n++ // already in it: no change
            if (e.updateBook(
                    republic.contentId,
                    mapOf("Series" to "Dialogues", "SeriesNumber" to "2", "SeriesNumberFloat" to 2.0, "SeriesID" to "Dialogues", "NotAColumn" to "x"),
                )
            ) n++
            n
        }
        assertNotNull(after)
        after!!
        // The connection now reads the new database.
        assertEquals(setOf("Classics", "History", "Philosophy"), after.snapshot.collectionsByBook[iliad.contentId].orEmpty().toSet())
        assertEquals("Dialogues #2", after.snapshot.booksById[republic.contentId]!!.seriesLabel)

        onDrive { db ->
            assertEquals("ok", db.one("PRAGMA integrity_check"))
            assertEquals("true", db.one("SELECT _IsDeleted FROM ShelfContent WHERE ShelfName = 'To Read' AND ContentId = ?", iliad.contentId))
            assertEquals("false", db.one("SELECT _IsDeleted FROM ShelfContent WHERE ShelfName = 'History' AND ContentId = ?", iliad.contentId))
            assertEquals("false", db.one("SELECT _IsDeleted FROM ShelfContent WHERE ShelfName = 'Philosophy' AND ContentId = ?", iliad.contentId))
            assertEquals("Dialogues", db.one("SELECT SeriesID FROM content WHERE ContentID = ?", republic.contentId))
            assertEquals("real", db.one("SELECT typeof(SeriesNumberFloat) FROM content WHERE ContentID = ?", republic.contentId))
            assertEquals(2.0, db.one("SELECT SeriesNumberFloat FROM content WHERE ContentID = ?", republic.contentId)!!.toDouble(), 0.0)
            assertEquals("no tables added (Android's own, say)", tablesBefore, db.one("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'"))
        }
        // Same journal mode as before; no temporary or stale files left beside it.
        assertEquals(before[18], dbOnDrive.readBytes()[18])
        assertEquals(emptyList<String>(), leftovers())
        // The database as it was is backed up.
        assertEquals(backupsBefore + 1, repo.backups.list().size)
        assertEquals(dbOnDrive.length(), after.databaseBytes)
    }

    @Test
    fun nothingToChangeWritesNothing() = runBlocking {
        FakeKobo.create(drive)
        val (repo, session) = connect()
        val before = dbOnDrive.readBytes()
        assertNull(repo.writeDatabase(session) { 0 })
        assertArrayEquals(before, dbOnDrive.readBytes())
        // A collection the book is already in changes nothing either.
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        assertNull(repo.writeDatabase(session) { e -> if (e.addToCollection("Classics", iliad.contentId)) 1 else 0 })
        assertArrayEquals(before, dbOnDrive.readBytes())
    }

    @Test
    fun aDatabaseThatChangedSinceItWasReadIsLeftAlone() = runBlocking {
        FakeKobo.create(drive)
        val (repo, session) = connect()
        dbOnDrive.appendBytes(ByteArray(4096))
        val changed = dbOnDrive.readBytes()
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        try {
            repo.writeDatabase(session) { e -> if (e.addToCollection("Philosophy", iliad.contentId)) 1 else 0 }
            fail("expected the write to be refused")
        } catch (e: KoboDatabaseChangedException) {
            assertTrue(e.message!!, e.message!!.contains("changed since it was read"))
        }
        assertArrayEquals(changed, dbOnDrive.readBytes())
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun aPendingWriteAheadLogIsKeptAndThenRemoved() = runBlocking {
        FakeKobo.create(drive)
        // Leave a change in the Kobo's -wal file, as when it was unplugged mid-write.
        val wal = File(dbOnDrive.path + "-wal")
        val savedDb = File(context.cacheDir, "saved-" + UUID.randomUUID())
        val savedWal = File(savedDb.path + "-wal")
        SQLiteDatabase.openDatabase(dbOnDrive.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            db.execSQL("UPDATE content SET ___PercentRead = 99, ReadStatus = 1 WHERE Title = 'The Iliad'")
            dbOnDrive.copyTo(savedDb)
            wal.copyTo(savedWal)
        }
        savedDb.copyTo(dbOnDrive, overwrite = true)
        savedWal.copyTo(wal, overwrite = true)
        File(dbOnDrive.path + "-shm").delete()
        assertTrue(wal.length() > 0)

        val (repo, session) = connect()
        assertTrue(session.databaseHadWal)
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        assertEquals("the change in the -wal file was read", 99, iliad.percentRead)

        val after = repo.writeDatabase(session) { e -> if (e.addToCollection("Philosophy", iliad.contentId)) 1 else 0 }!!
        assertFalse("the old -wal file is gone", wal.exists())
        assertFalse(after.databaseHadWal)
        onDrive { db ->
            assertEquals("99", db.one("SELECT ___PercentRead FROM content WHERE ContentID = ?", iliad.contentId))
            assertEquals("false", db.one("SELECT _IsDeleted FROM ShelfContent WHERE ShelfName = 'Philosophy' AND ContentId = ?", iliad.contentId))
            assertEquals("ok", db.one("PRAGMA integrity_check"))
        }
        assertEquals(emptyList<String>(), leftovers().filter { !it.endsWith("-shm") })

        // A second write starts from the first one's result.
        val third = repo.writeDatabase(after) { e -> if (e.removeFromCollection("Philosophy", iliad.contentId)) 1 else 0 }!!
        assertEquals(setOf("Classics", "To Read"), third.snapshot.collectionsByBook[iliad.contentId].orEmpty().toSet())
    }

    @Test
    fun aSwapCutShortIsFinishedWhenTheKoboIsConnectedAgain() = runBlocking {
        FakeKobo.create(drive)
        assertTrue(dbOnDrive.renameTo(File(drive, ".kobo/KoboReader.sqlite.kl-new")))
        val (_, session) = connect()
        assertTrue(dbOnDrive.exists())
        assertEquals(18, session.snapshot.allBooks.size)
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun aCollectionTheKoboDoesntHaveIsntMade() = runBlocking {
        // Collections made in the database don't show on the Kobo, so books only go into ones it has.
        FakeKobo.create(drive)
        val (repo, session) = connect()
        val before = dbOnDrive.readBytes()
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        assertNull(repo.writeDatabase(session) { e -> if (e.addToCollection("Epics", iliad.contentId)) 1 else 0 })
        assertArrayEquals(before, dbOnDrive.readBytes())
        onDrive { db -> assertEquals("0", db.one("SELECT COUNT(*) FROM Shelf WHERE Name = 'Epics'")) }
    }

    @Test
    fun aCollectionDeletedOnTheKoboIsntBroughtBack() = runBlocking {
        FakeKobo.create(drive)
        SQLiteDatabase.openDatabase(dbOnDrive.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("UPDATE Shelf SET _IsDeleted = 'true' WHERE Name = 'Gothic'")
        }
        val (repo, session) = connect()
        assertFalse(session.snapshot.collections.any { it.name == "Gothic" })
        val dracula = session.snapshot.books.first { it.title == "Dracula" }
        assertNull(repo.writeDatabase(session) { e -> if (e.addToCollection("Gothic", dracula.contentId)) 1 else 0 })
        onDrive { db -> assertEquals("true", db.one("SELECT _IsDeleted FROM Shelf WHERE Name = 'Gothic'")) }
    }
}
