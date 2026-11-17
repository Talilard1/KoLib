package dev.kolib.app.kobo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.ImportOutcome
import dev.kolib.app.library.Library
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KoboSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ---- collections, three ways ------------------------------------------------------------

    @Test
    fun neverComparedBeforeKeepsEverythingEitherSideHas() {
        val m = KoboSync.mergeCollections(listOf("A", "B"), listOf("B", "C"), base = null)
        assertEquals(listOf("A"), m.addOnKobo)
        assertEquals(emptyList<String>(), m.removeOnKobo)
        assertEquals(listOf("A", "B", "C"), m.library)
        assertEquals(listOf("A", "B", "C"), m.agreed)
    }

    @Test
    fun eachSidesChangesReachTheOther() {
        // Taken out in the app, added on the Kobo.
        val m = KoboSync.mergeCollections(library = listOf("A", "B"), kobo = listOf("A", "B", "C", "D"), base = listOf("A", "B", "C"))
        assertEquals(emptyList<String>(), m.addOnKobo)
        assertEquals(listOf("C"), m.removeOnKobo)
        assertEquals(listOf("A", "B", "D"), m.library)
        // Taken out on the Kobo, added in the app.
        val n = KoboSync.mergeCollections(library = listOf("A", "B", "E"), kobo = listOf("A"), base = listOf("A", "B"))
        assertEquals(listOf("E"), n.addOnKobo)
        assertEquals(emptyList<String>(), n.removeOnKobo)
        assertEquals(listOf("A", "E"), n.library)
        assertEquals(listOf("A", "E"), n.agreed)
    }

    @Test
    fun namesMatchWhateverTheirCaseAndNothingChangesWhenTheyAgree() {
        val m = KoboSync.mergeCollections(listOf("history"), listOf("History"), base = null)
        assertEquals(emptyList<String>(), m.addOnKobo)
        assertEquals(emptyList<String>(), m.removeOnKobo)
        assertNull(m.library)
        val same = KoboSync.mergeCollections(listOf("A"), listOf("A"), base = listOf("a"))
        assertEquals(emptyList<String>(), same.addOnKobo + same.removeOnKobo)
        assertNull(same.library)
    }

    @Test
    fun theKobosOwnNamesNeverGoOntoIt() {
        val m = KoboSync.mergeCollections(listOf("Wishlist", "A"), emptyList(), base = null)
        assertEquals(listOf("A"), m.addOnKobo)
        assertEquals(listOf("A"), m.agreed)
        assertNull(m.library)
        // And so it isn't taken out of the library next time either.
        val again = KoboSync.mergeCollections(listOf("Wishlist", "A"), listOf("A"), base = m.agreed)
        assertEquals(emptyList<String>(), again.addOnKobo + again.removeOnKobo)
        assertNull(again.library)
    }

    @Test
    fun aCollectionTheKoboDoesntHaveWaitsInTheLibraryUntilItDoes() {
        // "B" was added in the app, but the Kobo has no collection called B: KoLib can't make one there.
        val m = KoboSync.mergeCollections(listOf("A", "B"), listOf("A"), base = listOf("A"), exists = { it != "B" })
        assertEquals(emptyList<String>(), m.addOnKobo)
        assertEquals(listOf("B"), m.missingOnKobo)
        assertNull("the library keeps it", m.library)
        assertEquals("not agreed, so it isn't taken out next time", listOf("A"), m.agreed)
        // Once the collection is made on the Kobo, the book goes into it.
        val later = KoboSync.mergeCollections(listOf("A", "B"), listOf("A"), base = m.agreed)
        assertEquals(listOf("B"), later.addOnKobo)
        assertEquals(emptyList<String>(), later.missingOnKobo)
        assertEquals(listOf("A", "B"), later.agreed)
    }

    // ---- details --------------------------------------------------------------------------------

    private fun kobo(
        title: String = "The Hound of the Baskervilles",
        author: String? = "Arthur Conan Doyle",
        series: String? = "Sherlock Holmes",
        number: String? = "5",
        seriesId: String? = "Sherlock Holmes",
        description: String? = "<p>A hound.</p>",
        isbn: String? = "9780140437867",
        published: String? = "1902-01-01T00:00:00Z",
    ) = KoboBook(
        contentId = "file:///mnt/onboard/x.kepub.epub", title = title, author = author, subtitle = null, series = series, seriesNumber = number,
        description = description, publisher = "Penguin", language = "en", isbn = isbn, mimeType = null, format = "KEPUB",
        imageId = "x", kind = BookKind.SIDELOADED, isDownloaded = true, isExpired = false, readStatus = ReadStatus.UNREAD,
        percentRead = 0, lastReadMillis = null, addedMillis = null, fileSize = null, relativePath = "x.kepub.epub",
        published = published, seriesId = seriesId,
    )

    private val meta = BookMetadata(
        title = "The Hound of the Baskervilles", authors = listOf("Arthur Conan Doyle"), series = "Sherlock Holmes", seriesIndex = 5.0,
        description = "<p>A hound.</p> ", publisher = "Penguin", language = "EN", isbn = "978-0-14-043786-7", published = "1902",
    )

    @Test
    fun onlyWhatDiffersIsSet() {
        assertEquals(emptyMap<String, Any?>(), KoboSync.detailsFor(meta, kobo()))
        assertEquals(mapOf("Title" to "The Hound"), KoboSync.detailsFor(meta.copy(title = "The Hound"), kobo()))
        assertEquals(emptyMap<String, Any?>(), KoboSync.detailsFor(meta.copy(authors = listOf("A", "B")), kobo(author = "A & B")))
        assertEquals(mapOf("Attribution" to "A, B"), KoboSync.detailsFor(meta.copy(authors = listOf("A", "B")), kobo(author = "A")))
        assertEquals(
            mapOf("Series" to "Sherlock Holmes", "SeriesNumber" to "5", "SeriesNumberFloat" to 5.0, "SeriesID" to "Sherlock Holmes"),
            KoboSync.detailsFor(meta, kobo(seriesId = null)),
        )
        assertEquals(
            mapOf("Series" to "Sherlock Holmes", "SeriesNumber" to "2.5", "SeriesNumberFloat" to 2.5, "SeriesID" to "Sherlock Holmes"),
            KoboSync.detailsFor(meta.copy(seriesIndex = 2.5), kobo()),
        )
        assertEquals(
            mapOf("Series" to null, "SeriesNumber" to null, "SeriesNumberFloat" to null, "SeriesID" to null),
            KoboSync.detailsFor(meta.copy(series = null, seriesIndex = null), kobo()),
        )
        assertEquals(mapOf("Description" to null), KoboSync.detailsFor(meta.copy(description = null), kobo()))
        assertEquals(mapOf("DateCreated" to "1902-04-01T00:00:00Z"), KoboSync.detailsFor(meta.copy(published = "1902-04"), kobo()))
        assertEquals(emptyMap<String, Any?>(), KoboSync.detailsFor(meta.copy(published = null), kobo()))
    }

    // ---- end to end on the fake Kobo -----------------------------------------------------------

    /** A collection made on the Kobo itself, as the person would. */
    private fun makeCollectionOnKobo(dbFile: File, name: String) {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL(
                "INSERT INTO Shelf VALUES ('2026-03-01T00:00:00Z', ?, ?, '2026-03-01T00:00:00Z', ?, 'UserTag', 'false', 'true', 'false', NULL, NULL)",
                arrayOf("id-$name", name, name),
            )
        }
    }

    @Test
    fun theLibraryAndTheKoboEndUpAgreeing() = runBlocking {
        val drive = File(context.cacheDir, "sync-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val dbFile = File(drive, ".kobo/KoboReader.sqlite")
        val repo = KoboRepository(context)
        val lib = Library(context, File(context.filesDir, "sync-" + UUID.randomUUID()), null).apply { load() }
        run {
            val first = repo.connect(FakeDocumentsProvider.install(drive)) {}
            for (b in first.snapshot.books.filter { it.isSideloaded }) lib.importFromKobo(first, b)
        }
        fun libBook(title: String) = lib.books.value.first { it.title == title }

        // In the app: Herodotus leaves "To Read" and joins "Greek History"; The Republic gets a series and is sent again.
        val histories = libBook("The Histories")
        lib.updateDetails(histories.id, histories.meta, histories.collections - "To Read" + "Greek History")
        val republic = libBook("The Republic")
        lib.updateDetails(republic.id, republic.meta.copy(series = "Dialogues", seriesIndex = 2.0), republic.collections)
        lib.markSent(republic.id, republic.koboPath!!)
        // On the Kobo: The Odyssey joins "Favourites", Meditations leaves "Philosophy", Frankenstein is deleted.
        val odysseyId = libBook("The Odyssey").koboPath!!.let(KoboPaths::contentId)
        val meditationsId = libBook("Meditations").koboPath!!.let(KoboPaths::contentId)
        val frankenstein = libBook("Frankenstein")
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("INSERT INTO Shelf VALUES ('2026-03-01T00:00:00Z', 'id-fav', 'Favourites', '2026-03-01T00:00:00Z', 'Favourites', 'UserTag', 'false', 'true', 'false', NULL, NULL)")
            db.execSQL("INSERT INTO ShelfContent VALUES ('Favourites', ?, '2026-03-01T00:00:00Z', 'false', 'false')", arrayOf(odysseyId))
            db.execSQL("UPDATE ShelfContent SET _IsDeleted = 'true' WHERE ShelfName = 'Philosophy' AND ContentId = ?", arrayOf(meditationsId))
            db.execSQL("DELETE FROM content WHERE ContentID = ?", arrayOf(KoboPaths.contentId(frankenstein.koboPath!!)))
        }
        File(drive, frankenstein.koboPath!!).delete()

        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val runner = KoboSyncRunner(repo, lib)
        val outcome = runner.run(session)
        assertEquals(1, outcome.collectionsOnKobo)
        assertEquals(1, outcome.detailsOnKobo)
        assertEquals(2, outcome.collectionsInLibrary)
        assertEquals(1, outcome.noLongerOnKobo)
        assertTrue(outcome.describe()!!.startsWith("Put 1 book in their collections on the Kobo"))
        // The Kobo has no "Greek History", and KoLib can't make it there: the person is told.
        assertEquals(listOf("Greek History"), outcome.missingCollections)
        assertTrue(outcome.describe()!!.contains("The Kobo has no collection called “Greek History”"))

        val after = outcome.session.snapshot
        val historiesId = KoboPaths.contentId(histories.koboPath!!)
        assertEquals(setOf("Classics", "History"), after.collectionsByBook[historiesId].orEmpty().toSet())
        assertFalse(after.collections.any { it.name == "Greek History" })
        val onKobo = after.booksById[KoboPaths.contentId(republic.koboPath!!)]!!
        assertEquals("Dialogues", onKobo.series)
        assertEquals("Dialogues", onKobo.seriesId)
        assertEquals("2", onKobo.seriesNumber)

        assertEquals(setOf("Classics", "Favourites"), libBook("The Odyssey").collections.toSet())
        assertEquals(listOf("Classics"), libBook("Meditations").collections)
        assertEquals("the library keeps it", setOf("Classics", "History", "Greek History"), libBook("The Histories").collections.toSet())
        assertEquals(setOf("Classics", "History"), libBook("The Histories").koboCollections.orEmpty().toSet())
        assertNull(libBook("Frankenstein").koboPath)
        assertFalse("syncing isn't an edit", libBook("The Odyssey").changedSinceOnKobo)

        // Run again: they agree, so the Kobo's database isn't written.
        val before = dbFile.readBytes()
        val again = runner.run(outcome.session)
        assertFalse(again.anything)
        assertArrayEquals(before, dbFile.readBytes())

        // Once "Greek History" is made on the Kobo, the book goes into it the next time it's connected.
        makeCollectionOnKobo(dbFile, "Greek History")
        val made = runner.run(repo.connect(FakeDocumentsProvider.install(drive)) {})
        assertEquals(1, made.collectionsOnKobo)
        assertEquals(emptyList<String>(), made.missingCollections)
        assertEquals(setOf("Classics", "History", "Greek History"), made.session.snapshot.collectionsByBook[historiesId].orEmpty().toSet())
        assertEquals(setOf("Classics", "History", "Greek History"), libBook("The Histories").koboCollections.orEmpty().toSet())
    }

    // ---- books just sent, and books sent again -------------------------------------------------------

    private fun shelfRows(dbFile: File, contentId: String): List<String> =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("SELECT ShelfName FROM ShelfContent WHERE ContentId = ? AND _IsDeleted != 'true' ORDER BY ShelfName", arrayOf(contentId)).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }

    @Test
    fun aBookJustSentGoesIntoItsCollectionsBeforeTheKoboHasTakenItIn() = runBlocking {
        val drive = File(context.cacheDir, "sync-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val dbFile = File(drive, ".kobo/KoboReader.sqlite")
        val repo = KoboRepository(context)
        val lib = Library(context, File(context.filesDir, "sync-" + UUID.randomUUID()), null).apply { load() }
        val pending = PendingKoboRecords()
        val runner = KoboSyncRunner(repo, lib, pending)
        val source = dev.kolib.app.epub.TestEpubs.calibreEpub2(File(context.cacheDir, "sync-" + UUID.randomUUID() + "/h.epub"))
        val added = lib.import("h.epub", { source.inputStream() }, BookSource.FILE, collections = listOf("Greek History", "Favourites")) as ImportOutcome.Added
        // A copy of it, as the user made: new title, same collections, one more.
        val copy = lib.duplicate(added.book.id)
        lib.updateDetails(copy.id, copy.meta, copy.collections + "Copies")

        // The person made two of them on the Kobo; "Copies" isn't there.
        makeCollectionOnKobo(dbFile, "Greek History")
        makeCollectionOnKobo(dbFile, "Favourites")

        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val sent = KoboSender(context).send(session, copy.title, lib.book(copy.id)!!.meta, null, lib.epubFile(copy.id), SendFormat.KEPUB) as SendOutcome.Sent
        lib.markSent(copy.id, sent.path)
        val contentId = KoboPaths.contentId(sent.path)
        assertNull("the Kobo hasn't taken it in yet", session.snapshot.booksById[contentId])

        val outcome = runner.run(session)
        assertEquals(1, outcome.collectionsOnKobo)
        assertEquals(listOf("Favourites", "Greek History"), shelfRows(dbFile, contentId))
        assertEquals(listOf("Copies"), outcome.missingCollections)
        assertFalse(outcome.session.snapshot.collections.any { it.name == "Copies" })
        assertNull("nothing is agreed until the Kobo lists the book", lib.book(copy.id)!!.koboCollections)

        // Taken out of one before the Kobo is unplugged: it comes out there too.
        val now = lib.book(copy.id)!!
        lib.updateDetails(now.id, now.meta, now.collections - "Favourites")
        val again = runner.run(outcome.session)
        assertEquals(1, again.collectionsOnKobo)
        assertEquals(listOf("Greek History"), shelfRows(dbFile, contentId))

        // The Kobo takes it in (with the collections it found); next time they agree.
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL(
                "INSERT INTO content (ContentID, ContentType, MimeType, ImageId, Title, ___UserID, ___FileSize, IsDownloaded) VALUES (?, '6', 'application/x-kobo-epub+zip', ?, ?, '', ?, 'true')",
                arrayOf<Any?>(contentId, KoboImages.imageIdFromContentId(contentId), copy.title, File(drive, sent.path).length()),
            )
        }
        val later = runner.run(repo.connect(FakeDocumentsProvider.install(drive)) {})
        assertEquals(0, later.collectionsOnKobo)
        assertEquals(0, later.collectionsInLibrary)
        assertEquals(setOf("Greek History"), lib.book(copy.id)!!.koboCollections.orEmpty().toSet())
        assertEquals("the library keeps the one the Kobo doesn't have", setOf("Copies", "Greek History"), lib.book(copy.id)!!.collections.toSet())
    }

    @Test
    fun aBookTakenInWithoutItsCollectionsGetsThemTheNextTimeTheKoboIsConnected() = runBlocking {
        // The Kobo was ejected before the collections of a book just sent were saved (or it didn't
        // keep them): it took the book in without them. Nothing has to be added again.
        val drive = File(context.cacheDir, "sync-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val dbFile = File(drive, ".kobo/KoboReader.sqlite")
        makeCollectionOnKobo(dbFile, "Greek History")
        val repo = KoboRepository(context)
        val lib = Library(context, File(context.filesDir, "sync-" + UUID.randomUUID()), null).apply { load() }
        val runner = KoboSyncRunner(repo, lib, PendingKoboRecords())
        val source = dev.kolib.app.epub.TestEpubs.calibreEpub2(File(context.cacheDir, "sync-" + UUID.randomUUID() + "/h.epub"))
        val added = lib.import("h.epub", { source.inputStream() }, BookSource.FILE, collections = listOf("Greek History")) as ImportOutcome.Added
        val copy = lib.duplicate(added.book.id)

        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val sent = KoboSender(context).send(session, copy.title, lib.book(copy.id)!!.meta, null, lib.epubFile(copy.id), SendFormat.KEPUB) as SendOutcome.Sent
        lib.markSent(copy.id, sent.path)
        val contentId = KoboPaths.contentId(sent.path)
        // Ejected now: no sync. The Kobo takes the book in.
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL(
                "INSERT INTO content (ContentID, ContentType, MimeType, ImageId, Title, ___UserID, ___FileSize, IsDownloaded) VALUES (?, '6', 'application/x-kobo-epub+zip', ?, ?, '', ?, 'true')",
                arrayOf<Any?>(contentId, KoboImages.imageIdFromContentId(contentId), copy.title, File(drive, sent.path).length()),
            )
        }
        assertEquals(emptyList<String>(), shelfRows(dbFile, contentId))

        val next = runner.run(repo.connect(FakeDocumentsProvider.install(drive)) {})
        assertEquals(1, next.collectionsOnKobo)
        assertEquals(listOf("Greek History"), shelfRows(dbFile, contentId))
        assertEquals(listOf("Greek History"), lib.book(copy.id)!!.collections)
        assertEquals(listOf("Greek History"), lib.book(copy.id)!!.koboCollections)
    }

    @Test
    fun theRecordOfABookSentAgainIsKeptInTheSameWrite() = runBlocking {
        val drive = File(context.cacheDir, "sync-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val dbFile = File(drive, ".kobo/KoboReader.sqlite")
        val repo = KoboRepository(context)
        val lib = Library(context, File(context.filesDir, "sync-" + UUID.randomUUID()), null).apply { load() }
        val pending = PendingKoboRecords()
        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val histories = session.snapshot.books.first { it.title == "The Histories" }
        pending.put(histories.contentId, PendingKoboRecords.Pending("some-book", session.connectedAtMillis, mapOf("___FileSize" to 123_456L, "ImageId" to histories.imageId + "_abc")))

        val outcome = KoboSyncRunner(repo, lib, pending).run(session)
        assertEquals(1, outcome.recordsKept)
        assertTrue(outcome.anything)
        assertTrue(pending.isEmpty)
        val after = outcome.session.snapshot.booksById[histories.contentId]!!
        assertEquals(123_456L, after.fileSize)
        assertEquals(histories.imageId + "_abc", after.imageId)
        // Its reading progress and collections are as they were.
        assertEquals(histories.percentRead, after.percentRead)
        assertEquals(session.snapshot.collectionsByBook[histories.contentId], outcome.session.snapshot.collectionsByBook[histories.contentId])
        assertTrue(dbFile.length() > 0)
    }

    @Test
    fun recordsNotKeptBeforeTheKoboWasUnpluggedMeanItsCollectionsAreComparedAfresh() = runBlocking {
        val drive = File(context.cacheDir, "sync-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val repo = KoboRepository(context)
        val lib = Library(context, File(context.filesDir, "sync-" + UUID.randomUUID()), null).apply { load() }
        val first = repo.connect(FakeDocumentsProvider.install(drive)) {}
        for (b in first.snapshot.books.filter { it.isSideloaded }) lib.importFromKobo(first, b)
        val odyssey = lib.books.value.first { it.title == "The Odyssey" }
        lib.applyKoboAgreement(listOf(Triple(odyssey.id, null, odyssey.collections)))
        assertNotNull(lib.book(odyssey.id)!!.koboCollections)

        // Sent again last time, but the Kobo was unplugged before its record was kept: it took the book
        // in afresh and forgot its collections. The library mustn't read that as "taken out on the Kobo".
        val pending = PendingKoboRecords()
        val contentId = KoboPaths.contentId(odyssey.koboPath!!)
        pending.put(contentId, PendingKoboRecords.Pending(odyssey.id, connection = 1L, columns = mapOf("___FileSize" to 1L)))
        SQLiteDatabase.openDatabase(File(drive, ".kobo/KoboReader.sqlite").path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("DELETE FROM ShelfContent WHERE ContentId = ?", arrayOf(contentId))
        }
        val outcome = KoboSyncRunner(repo, lib, pending).run(repo.connect(FakeDocumentsProvider.install(drive)) {})
        assertTrue(pending.isEmpty)
        assertEquals(0, outcome.recordsKept)
        assertEquals(odyssey.collections.toSet(), lib.book(odyssey.id)!!.collections.toSet())
        assertEquals(odyssey.collections.toSet(), outcome.session.snapshot.collectionsByBook[contentId].orEmpty().toSet())
    }
}
