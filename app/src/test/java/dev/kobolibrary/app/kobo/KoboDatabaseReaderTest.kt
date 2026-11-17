package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.kobo.db.SqlRow
import dev.kobolibrary.app.kobo.db.SqlSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Runs the reader against a real SQLite file shaped like a Kobo database. */
class KoboDatabaseReaderTest {

    private lateinit var file: File
    private lateinit var conn: Connection

    private class JdbcSource(private val conn: Connection) : SqlSource {
        override fun query(sql: String, args: List<String>): List<SqlRow> {
            conn.prepareStatement(sql).use { st ->
                args.forEachIndexed { i, a -> st.setString(i + 1, a) }
                st.executeQuery().use { rs ->
                    val md = rs.metaData
                    val rows = ArrayList<SqlRow>()
                    while (rs.next()) {
                        val values = HashMap<String, Any?>()
                        for (c in 1..md.columnCount) values[md.getColumnLabel(c)] = rs.getObject(c)
                        rows += SqlRow(values)
                    }
                    return rows
                }
            }
        }
    }

    @Before
    fun setUp() {
        file = File.createTempFile("KoboReader", ".sqlite")
        conn = DriverManager.getConnection("jdbc:sqlite:${file.path}")
    }

    @After
    fun tearDown() {
        conn.close()
        file.delete()
    }

    private fun exec(vararg statements: String) {
        conn.createStatement().use { st -> statements.forEach { st.execute(it) } }
    }

    private fun modernSchema() = exec(
        "CREATE TABLE DbVersion(version INTEGER)",
        "INSERT INTO DbVersion VALUES (174)",
        """CREATE TABLE content(
            ContentID TEXT NOT NULL, ContentType TEXT NOT NULL, MimeType TEXT NOT NULL, BookID TEXT, BookTitle TEXT,
            ImageId TEXT, Title TEXT COLLATE NOCASE, Attribution TEXT COLLATE NOCASE, Description TEXT, DateCreated TEXT,
            ShortCoverKey TEXT, adobe_location TEXT, Publisher TEXT, IsEncrypted BOOL, DateLastRead TEXT, FirstTimeReading BOOL,
            ChapterIDBookmarked TEXT, ParagraphBookmarked INTEGER, BookmarkWordOffset INTEGER, NumShortcovers INTEGER,
            VolumeIndex INTEGER, ___NumPages INTEGER, ReadStatus INTEGER, ___SyncTime TEXT, ___UserID TEXT NOT NULL,
            PublicationId TEXT, ___FileOffset INTEGER, ___FileSize INTEGER, ___PercentRead INTEGER, ___ExpirationStatus INTEGER,
            FavouritesIndex NUMERIC NOT NULL DEFAULT -1, Accessibility INTEGER DEFAULT 1, ContentURL TEXT, Language TEXT,
            BookshelfTags TEXT, IsDownloaded BIT NOT NULL DEFAULT 1, FeedbackType INTEGER DEFAULT 0, AverageRating INTEGER DEFAULT 0,
            Depth INTEGER, PageProgressDirection TEXT, InWishlist TEXT NOT NULL DEFAULT 'FALSE', ISBN TEXT,
            WishlistedDate TEXT NOT NULL DEFAULT '0000-00-00T00:00:00.000', FeedbackTypeSynced INTEGER NOT NULL DEFAULT 0,
            IsSocialEnabled TEXT NOT NULL DEFAULT 'TRUE', EpubType INTEGER DEFAULT -1, Monetization INTEGER DEFAULT 2,
            ExternalId TEXT, Series TEXT, SeriesNumber TEXT, Subtitle TEXT, WordCount INTEGER DEFAULT -1, Fallback TEXT,
            RestOfBookEstimate INTEGER, CurrentChapterEstimate INTEGER, CurrentChapterProgress FLOAT, PocketStatus INTEGER DEFAULT 0,
            UnsyncedPocketChanges TEXT, ImageUrl TEXT, DateAdded TEXT, WorkId TEXT, Properties TEXT, RenditionSpread TEXT,
            RatingCount INTEGER DEFAULT 0, ReviewsSyncDate TEXT, MediaOverlay TEXT, MediaOverlayType TEXT, RedirectPreviewUrl BOOL,
            PreviewFileSize INTEGER, EntitlementId TEXT, CrossRevisionId TEXT, DownloadUrl BOOL, ReadStateSynced BOOL DEFAULT false,
            TimesStartedReading INTEGER, TimeSpentReading INTEGER, LastTimeStartedReading TEXT, LastTimeFinishedReading TEXT,
            ApplicableSubscriptions TEXT, ExternalIds TEXT, PurchaseRevisionId TEXT, SeriesID TEXT, SeriesNumberFloat REAL,
            AdobeLoanExpiration TEXT, HideFromHomePage BOOL, IsInternetArchive BOOL, titleKana TEXT, subtitleKana TEXT,
            seriesKana TEXT, attributionKana TEXT, publisherKana TEXT, IsPurchaseable BOOL, IsSupported BOOL,
            AnnotationsSyncToken TEXT, DateModified TEXT, StorePages INTEGER, StoreWordCount INTEGER,
            StoreTimeToReadLowerEstimate INTEGER, StoreTimeToReadUpperEstimate INTEGER, Duration INTEGER, IsAbridged BOOL,
            PRIMARY KEY (ContentID))""",
        """CREATE TABLE Shelf(CreationDate TEXT, Id TEXT, InternalName TEXT, LastModified TEXT, Name TEXT, Type TEXT,
            _IsDeleted BOOL, _IsVisible BOOL, _IsSynced BOOL, _SyncTime TEXT, LastAccessed TEXT, PRIMARY KEY(Id))""",
        """CREATE TABLE ShelfContent(ShelfName TEXT, ContentId TEXT, DateModified TEXT, _IsDeleted BOOL, _IsSynced BOOL,
            PRIMARY KEY(ShelfName, ContentId))""",
    )

    private fun insertBook(
        id: String, title: String?, author: String?, mime: String = "application/x-kobo-epub+zip",
        accessibility: Int = -1, userId: String = "", downloaded: Any = "true", readStatus: Int = 0,
        percent: Int = 0, series: String? = null, seriesNumber: String? = null, expiration: Int? = null,
        lastRead: String? = null, imageId: String? = null, bookId: String? = null, contentType: String = "6",
    ) {
        conn.prepareStatement(
            """INSERT INTO content (ContentID, ContentType, MimeType, BookID, Title, Attribution, Accessibility, ___UserID,
               IsDownloaded, ReadStatus, ___PercentRead, Series, SeriesNumber, ___ExpirationStatus, DateLastRead, ImageId,
               ___FileSize, DateAdded)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"""
        ).use { st ->
            st.setString(1, id); st.setString(2, contentType); st.setString(3, mime); st.setString(4, bookId)
            st.setString(5, title); st.setString(6, author); st.setInt(7, accessibility); st.setString(8, userId)
            st.setObject(9, downloaded); st.setInt(10, readStatus); st.setInt(11, percent); st.setString(12, series)
            st.setString(13, seriesNumber)
            if (expiration == null) st.setNull(14, java.sql.Types.INTEGER) else st.setInt(14, expiration)
            st.setString(15, lastRead); st.setString(16, imageId); st.setLong(17, 123456); st.setString(18, "2023-04-01T10:00:00Z")
            st.executeUpdate()
        }
    }

    private fun insertShelf(name: String, internal: String = name, deleted: Any = "false", visible: Any = "true", type: String? = "UserTag") {
        conn.prepareStatement(
            "INSERT INTO Shelf (CreationDate, Id, InternalName, LastModified, Name, Type, _IsDeleted, _IsVisible, _IsSynced) VALUES (?,?,?,?,?,?,?,?,?)"
        ).use { st ->
            st.setString(1, "2023-05-01T00:00:00Z"); st.setString(2, "id-$name"); st.setString(3, internal)
            st.setString(4, "2023-05-02T00:00:00Z"); st.setString(5, name); st.setString(6, type)
            st.setObject(7, deleted); st.setObject(8, visible); st.setString(9, "false")
            st.executeUpdate()
        }
    }

    private fun insertMembership(shelf: String, contentId: String, deleted: Any = "false", modified: String = "2023-05-03T00:00:00Z") {
        conn.prepareStatement(
            "INSERT INTO ShelfContent (ShelfName, ContentId, DateModified, _IsDeleted, _IsSynced) VALUES (?,?,?,?,?)"
        ).use { st ->
            st.setString(1, shelf); st.setString(2, contentId); st.setString(3, modified); st.setObject(4, deleted); st.setString(5, "false")
            st.executeUpdate()
        }
    }

    private val mistborn = "file:///mnt/onboard/Books/Brandon Sanderson/The Final Empire.kepub.epub"
    private val dune = "file:///mnt/onboard/Books/Frank Herbert/Dune.epub"
    private val notes = "file:///mnt/onboard/Documents/notes.pdf"
    private val storeBook = "a1b2c3d4-0000-1111-2222-333344445555"
    private val cloudBook = "b1b2c3d4-0000-1111-2222-333344445555"
    private val preview = "c1b2c3d4-0000-1111-2222-333344445555"
    private val recommendation = "d1b2c3d4-0000-1111-2222-333344445555"
    private val expiredLoan = "e1b2c3d4-0000-1111-2222-333344445555"

    private fun populate() {
        modernSchema()
        insertBook(mistborn, "The Final Empire", "Brandon Sanderson", series = "Mistborn", seriesNumber = "1",
            readStatus = 2, percent = 100, lastRead = "2024-01-15 14:30:00.000+00:00")
        insertBook(dune, "Dune", "Frank Herbert", mime = "application/epub+zip", readStatus = 1, percent = 42, downloaded = 1)
        insertBook(notes, null, null, mime = "application/pdf")
        insertBook(storeBook, "Store Book", "Some Author", accessibility = 1, userId = "user-1", imageId = "store-image")
        insertBook(cloudBook, "Not Downloaded", "Some Author", accessibility = 1, userId = "user-1", downloaded = "false")
        insertBook(preview, "A Preview", "Someone", accessibility = 6, userId = "user-1")
        insertBook(recommendation, "Free Sample", "Someone", accessibility = 4, userId = "")
        insertBook(expiredLoan, "Expired Loan", "Someone", accessibility = 8, userId = "user-1", expiration = 3)
        // A chapter row of a kepub: must never be treated as a book.
        insertBook("$mistborn#(1)chapter1.html", "Chapter 1", null, contentType = "9", bookId = mistborn)

        insertShelf("Fantasy")
        insertShelf("Sci-Fi")
        insertShelf("Old Shelf", deleted = "true")
        insertShelf("Hidden One", visible = 0)
        insertShelf("Renamed", internal = "internal-renamed")

        insertMembership("Fantasy", mistborn)
        insertMembership("Sci-Fi", dune)
        insertMembership("Fantasy", dune, modified = "2023-05-04T00:00:00Z")
        insertMembership("Sci-Fi", storeBook, deleted = 1)              // removed from the collection
        insertMembership("Old Shelf", notes)                            // collection itself deleted
        insertMembership("internal-renamed", notes)                     // refers to the internal name
        insertMembership("Fantasy", "file:///mnt/onboard/gone.epub", modified = "2023-05-05T00:00:00Z") // book no longer exists
    }

    @Test
    fun readsBooksLikeCalibre() {
        populate()
        val snap = KoboDatabaseReader(JdbcSource(conn)).read()

        assertEquals(174, snap.dbVersion)
        // 8 book rows; the chapter row is excluded.
        assertEquals(8, snap.allBooks.size)
        assertEquals(setOf(mistborn, dune, notes, storeBook), snap.books.map { it.contentId }.toSet())
        assertEquals(3, snap.sideloadedCount)
        assertEquals(1, snap.purchasedOnDeviceCount)
        assertEquals(1, snap.purchasedInCloudCount)
        assertEquals(1, snap.previewCount)

        val m = snap.booksById.getValue(mistborn)
        assertEquals(BookKind.SIDELOADED, m.kind)
        assertEquals("The Final Empire", m.title)
        assertEquals("Brandon Sanderson", m.author)
        assertEquals("Mistborn #1", m.seriesLabel)
        assertEquals("KEPUB", m.format)
        assertEquals(ReadStatus.FINISHED, m.readStatus)
        assertEquals(1705329000000L, m.lastReadMillis)
        assertEquals("Books/Brandon Sanderson/The Final Empire.kepub.epub", m.relativePath)
        assertEquals("file____mnt_onboard_Books_Brandon_Sanderson_The_Final_Empire_kepub_epub", m.imageId)

        val d = snap.booksById.getValue(dune)
        assertEquals("EPUB", d.format)
        assertEquals(ReadStatus.READING, d.readStatus)
        assertEquals(42, d.percentRead)
        assertTrue(d.isDownloaded) // stored as integer 1

        val n = snap.booksById.getValue(notes)
        assertEquals("notes", n.title) // falls back to the file name
        assertEquals("PDF", n.format)
        assertNull(n.author)

        assertEquals("store-image", snap.booksById.getValue(storeBook).imageId)
        assertEquals(BookKind.PREVIEW, snap.booksById.getValue(preview).kind)
        assertEquals(BookKind.RECOMMENDATION, snap.booksById.getValue(recommendation).kind)
        assertFalse(snap.booksById.getValue(expiredLoan).isOnDevice)
        assertFalse(snap.booksById.getValue(cloudBook).isOnDevice)
    }

    @Test
    fun readsHowTheKoboSharesOutTheBooksItHasAPlaceIn() {
        populate()
        val book = "file:///mnt/onboard/Books/Sea.kepub.epub"
        insertBook(book, "The Sea", "Someone", readStatus = 1, percent = 38)
        exec("UPDATE content SET ChapterIDBookmarked = 'OEBPS/Text/ch2.xhtml#kobo.3.1' WHERE ContentID = '$book'")
        // Its chapters, with where each starts and how much of the book it is (in percent, not whole numbers).
        for ((i, row) in listOf(Triple("ch1", 0.0, 20.5), Triple("ch2", 20.5, 49.25), Triple("ch3", 69.75, 30.25)).withIndex()) {
            insertBook("$book!!OEBPS/Text/${row.first}.xhtml", "Chapter ${i + 1}", null, contentType = "9", bookId = book)
            exec("UPDATE content SET ___FileOffset = ${row.second}, ___FileSize = ${row.third} WHERE ContentID = '$book!!OEBPS/Text/${row.first}.xhtml'")
        }
        val snap = KoboDatabaseReader(JdbcSource(conn)).read()
        val spans = snap.booksById.getValue(book).chapterSpans
        assertEquals(listOf("$book!!OEBPS/Text/ch1.xhtml", "$book!!OEBPS/Text/ch2.xhtml", "$book!!OEBPS/Text/ch3.xhtml"), spans.map { it.id })
        assertEquals(listOf(0.0, 20.5, 69.75), spans.map { it.offset })
        assertEquals(49.25, spans[1].size, 0.0)
        // Only for books it has a place in: the finished one's chapter isn't kept.
        assertTrue(snap.booksById.getValue(mistborn).chapterSpans.isEmpty())
    }

    @Test
    fun readsTheKobosOwnReadingTimes() {
        populate()
        conn.createStatement().use { st ->
            st.execute("UPDATE content SET TimeSpentReading = 467640, RestOfBookEstimate = 781920 WHERE Title = 'Dune'")
        }
        val snap = KoboDatabaseReader(JdbcSource(conn)).read()
        // 129.9 hours read and 217.2 to go, as the Kobo's own stats screen would say.
        assertEquals(467640, snap.booksById.getValue(dune).secondsRead)
        assertEquals(781920, snap.booksById.getValue(dune).secondsLeft)
        // A book that's never been opened has neither.
        assertEquals(null, snap.booksById.getValue(notes).secondsRead)
        assertEquals(null, snap.booksById.getValue(notes).secondsLeft)
    }

    @Test
    fun readsCollections() {
        populate()
        val snap = KoboDatabaseReader(JdbcSource(conn)).read()

        assertEquals(listOf("Fantasy", "Hidden One", "Renamed", "Sci-Fi"), snap.collections.map { it.name })
        val fantasy = snap.collections.first { it.name == "Fantasy" }
        assertEquals(listOf(mistborn, dune, "file:///mnt/onboard/gone.epub"), fantasy.memberIds)
        assertEquals(listOf(mistborn, dune), snap.booksIn(fantasy).map { it.contentId })
        assertEquals("UserTag", fantasy.type)

        assertEquals(listOf("Fantasy", "Sci-Fi"), snap.collectionsByBook[dune])
        assertEquals(listOf("Renamed"), snap.collectionsByBook[notes])
        assertNull(snap.collectionsByBook[storeBook]) // its membership row is deleted
        assertFalse(snap.collections.first { it.name == "Hidden One" }.isVisible)
        assertEquals(listOf(storeBook), snap.booksWithoutCollection().map { it.contentId })

        assertEquals(2, snap.warnings.size)
        assertTrue(snap.warnings.any { it.startsWith("1 collection entry points to a deleted or missing collection") })
        assertTrue(snap.warnings.any { it.startsWith("1 collection entry points to a book") })
    }

    @Test
    fun copesWithOldFirmwareWithoutCollectionsOrNewColumns() {
        exec(
            "CREATE TABLE content(ContentID TEXT NOT NULL, ContentType TEXT NOT NULL, MimeType TEXT, BookID TEXT, Title TEXT, Attribution TEXT, ReadStatus INTEGER, PRIMARY KEY (ContentID))",
            "INSERT INTO content VALUES ('file:///mnt/onboard/a.epub', '6', 'application/epub+zip', NULL, 'A', 'Author A', 0)",
            "INSERT INTO content VALUES ('file:///mnt/onboard/b.kepub.epub', 6, 'application/x-kobo-epub+zip', NULL, 'B', NULL, 2)",
        )
        val snap = KoboDatabaseReader(JdbcSource(conn)).read()
        assertEquals(0, snap.dbVersion)
        assertEquals(listOf("A", "B"), snap.books.map { it.title }.sorted())
        assertTrue(snap.collections.isEmpty())
        assertTrue(snap.warnings.any { it.contains("database version") })
        assertTrue(snap.warnings.any { it.contains("no collections table") })
    }

    @Test(expected = KoboDatabaseException::class)
    fun refusesSomethingThatIsNotAKoboDatabase() {
        exec("CREATE TABLE notes(id INTEGER)")
        KoboDatabaseReader(JdbcSource(conn)).read()
    }
}
