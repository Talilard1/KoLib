package dev.kolib.app.kobo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
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

    private fun inode(f: File): Any = java.nio.file.Files.getAttribute(f.toPath(), "unix:ino")

    private fun walMode(on: Boolean) {
        SQLiteDatabase.openDatabase(dbOnDrive.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("PRAGMA journal_mode=" + if (on) "WAL" else "DELETE", null).use { it.moveToFirst() }
        }
        listOf("-wal", "-shm").forEach { File(dbOnDrive.path + it).delete() }
    }

    @Test
    fun aSmallChangeRewritesOnlyItsPages() = runBlocking {
        for (wal in listOf(false, true)) {
            drive.deleteRecursively()
            FakeKobo.create(drive)
            walMode(wal)
            val (repo, session) = connect()
            val iliad = session.snapshot.books.first { it.title == "The Iliad" }
            val before = dbOnDrive.readBytes()
            val file = inode(dbOnDrive)
            val after = repo.writeDatabase(session) { e -> if (e.addToCollection("Philosophy", iliad.contentId)) 1 else 0 }!!
            // The same file, changed in place (copying swaps in a new one), with no journal left beside it.
            assertEquals("wal=$wal", file, inode(dbOnDrive))
            assertEquals(emptyList<String>(), leftovers())
            val now = dbOnDrive.readBytes()
            assertEquals(before[18], now[18])
            val pages = before.size / 4096
            val differ = (0 until pages).count { p -> !before.copyOfRange(p * 4096, (p + 1) * 4096).contentEquals(now.copyOfRange(p * 4096, (p + 1) * 4096)) }
            assertTrue("only a few of $pages pages change: $differ", differ in 1..pages / 2)
            onDrive { db ->
                assertEquals("ok", db.one("PRAGMA integrity_check"))
                assertEquals("false", db.one("SELECT _IsDeleted FROM ShelfContent WHERE ShelfName = 'Philosophy' AND ContentId = ?", iliad.contentId))
            }
            assertEquals(setOf("Classics", "Philosophy", "To Read"), after.snapshot.collectionsByBook[iliad.contentId].orEmpty().toSet())
            // And again from there.
            val third = repo.writeDatabase(after) { e -> if (e.removeFromCollection("Philosophy", iliad.contentId)) 1 else 0 }!!
            assertEquals(file, inode(dbOnDrive))
            assertEquals(setOf("Classics", "To Read"), third.snapshot.collectionsByBook[iliad.contentId].orEmpty().toSet())
            onDrive { db -> assertEquals("true", db.one("SELECT _IsDeleted FROM ShelfContent WHERE ShelfName = 'Philosophy' AND ContentId = ?", iliad.contentId)) }
        }
    }

    /**
     * What the Kobo finds if it's unplugged part way through: the journal and some of the new pages. Its
     * SQLite (here Android's) puts the old pages back from the journal the next time it opens the database.
     */
    @Test
    fun aRewriteCutShortIsUndoneWhenTheKoboOpensItsDatabase() {
        for (wal in listOf(false, true)) {
            for (grow in listOf(false, true)) {
                drive.deleteRecursively()
                FakeKobo.create(drive)
                walMode(wal)
                val old = File(context.cacheDir, "old-" + UUID.randomUUID()).also { dbOnDrive.copyTo(it) }
                val new = File(context.cacheDir, "new-" + UUID.randomUUID()).also { dbOnDrive.copyTo(it) }
                KoboDatabaseWriter.open(new, wal).use { db ->
                    db.execSQL("UPDATE content SET Title = 'Changed' WHERE Title = 'The Iliad'")
                    if (grow) repeat(400) { db.execSQL("INSERT INTO Shelf (Name, InternalName, _IsDeleted) VALUES (?, ?, 'false')", arrayOf("Shelf $it" + "x".repeat(300), "Shelf $it")) }
                    if (wal) db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                }
                listOf("-wal", "-shm", "-journal").forEach { File(new.path + it).delete() }
                val c = PageWriter.changes(old, new)!!
                assertTrue(c.written.isNotEmpty())
                assertEquals(grow, c.newPages > c.oldPages)
                val oldBytes = old.readBytes()
                val newBytes = new.readBytes()
                val size = c.pageSize
                for (cut in listOf(0, c.written.size / 2, c.written.size)) {
                    old.copyTo(dbOnDrive, overwrite = true)
                    PageWriter.writeJournal(
                        File(drive, ".kobo/" + PageWriter.JOURNAL_NAME), size, c.oldPages,
                        c.saved.map { it to oldBytes.copyOfRange(it * size, (it + 1) * size) },
                    )
                    java.io.RandomAccessFile(dbOnDrive, "rw").use { f ->
                        for (p in c.written.take(cut)) {
                            f.seek(p.toLong() * size)
                            f.write(newBytes, p * size, size)
                        }
                    }
                    // Opened in its own journal mode, as the Kobo would (Android's default would switch it).
                    val title = KoboDatabaseWriter.open(dbOnDrive, wal).use { db ->
                        assertEquals("wal=$wal grow=$grow cut=$cut", "ok", db.one("PRAGMA integrity_check"))
                        db.one("SELECT COUNT(*) FROM content WHERE Title = 'The Iliad'")
                    }
                    assertEquals("wal=$wal grow=$grow cut=$cut: the old database is back", "1", title)
                    assertFalse("the journal was played back and removed", File(drive, ".kobo/" + PageWriter.JOURNAL_NAME).exists())
                    listOf("-wal", "-shm").forEach { File(dbOnDrive.path + it).delete() }
                    assertArrayEquals("wal=$wal grow=$grow cut=$cut", oldBytes.copyOf(c.oldPages * size), dbOnDrive.readBytes().copyOf(c.oldPages * size))
                    assertEquals(old.length(), dbOnDrive.length())
                }
            }
        }
    }

    @Test
    fun anUnfinishedChangeOnTheKoboStopsTheWrite() = runBlocking {
        FakeKobo.create(drive)
        val (repo, session) = connect()
        val journal = File(drive, ".kobo/" + PageWriter.JOURNAL_NAME)
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        // A blanked-out journal (SQLite leaves those in some modes) is nothing to undo, so it doesn't matter.
        journal.writeBytes(ByteArray(512))
        assertNotNull(repo.writeDatabase(session) { e -> if (e.addToCollection("Philosophy", iliad.contentId)) 1 else 0 })
        val (repo2, session2) = connect()
        val before = dbOnDrive.readBytes()
        PageWriter.writeJournal(journal, 4096, (before.size / 4096), listOf(1 to before.copyOfRange(4096, 8192)))
        try {
            repo2.writeDatabase(session2) { e -> if (e.removeFromCollection("Philosophy", iliad.contentId)) 1 else 0 }
            fail("expected the write to be refused")
        } catch (e: KoboDatabaseChangedException) {
            assertTrue(e.message!!, e.message!!.contains("unfinished change"))
        }
        assertArrayEquals(before, dbOnDrive.readBytes())
    }

    @Test
    fun pagesThatArentAsReadMeanTheWholeFileIsCopied() = runBlocking {
        FakeKobo.create(drive)
        walMode(false)
        val (repo, session) = connect()
        // The header changes, the size doesn't: rewriting pages over it could mix two databases.
        SQLiteDatabase.openDatabase(dbOnDrive.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("PRAGMA user_version = 7")
        }
        assertEquals(session.databaseBytes, dbOnDrive.length())
        val file = inode(dbOnDrive)
        val iliad = session.snapshot.books.first { it.title == "The Iliad" }
        val after = repo.writeDatabase(session) { e -> if (e.addToCollection("Philosophy", iliad.contentId)) 1 else 0 }!!
        assertTrue("a whole new file was swapped in", file != inode(dbOnDrive))
        onDrive { db ->
            assertEquals("ok", db.one("PRAGMA integrity_check"))
            assertEquals("0", db.one("PRAGMA user_version"))
        }
        assertEquals(emptyList<String>(), leftovers())
        assertEquals(dbOnDrive.length(), after.databaseBytes)
    }

    @Test
    fun rewritingPagesIsOnlyForChangesSmallerThanTheFile() {
        FakeKobo.create(drive)
        walMode(false)
        val old = File(context.cacheDir, "old-" + UUID.randomUUID()).also { dbOnDrive.copyTo(it) }
        val small = File(context.cacheDir, "small-" + UUID.randomUUID()).also { dbOnDrive.copyTo(it) }
        KoboDatabaseWriter.open(small, false).use { it.execSQL("UPDATE content SET ReadStatus = 2 WHERE Title = 'The Iliad'") }
        assertTrue(PageWriter.changes(old, small)!!.worthIt)
        // Saving and rewriting most of the pages is no quicker than copying the file.
        fun pages(changed: Int): File = File(context.cacheDir, "pages-" + UUID.randomUUID()).apply {
            val bytes = ByteArray(10 * 4096)
            bytes[16] = 0x10 // page size 4096, as a database's header gives it
            for (p in 1..changed) bytes[p * 4096 + 7] = 1
            writeBytes(bytes)
        }
        val ten = pages(0)
        val most = PageWriter.changes(ten, pages(7))!!
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), most.written)
        assertFalse(most.worthIt)
        assertTrue(PageWriter.changes(ten, pages(2))!!.worthIt)
        assertNull("not a database", PageWriter.changes(ten, File(context.cacheDir, "x").apply { writeBytes(ByteArray(4096 * 3)) }))
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
