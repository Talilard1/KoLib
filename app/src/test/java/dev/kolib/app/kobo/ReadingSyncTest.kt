package dev.kolib.app.kobo

import android.database.sqlite.SQLiteDatabase
import dev.kolib.app.epub.KepubConverter
import dev.kolib.app.epub.TestEpubs
import dev.kolib.app.kobo.db.AndroidSqlSource
import dev.kolib.app.reader.Annotation
import dev.kolib.app.reader.HighlightColor
import dev.kolib.app.reader.ReaderPosition
import dev.kolib.app.reader.ReaderState
import dev.kolib.app.reader.ReaderStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingSyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var epub: File
    private lateinit var dir: File
    private lateinit var dbFile: File
    private var book = KEPUB_ID
    private var kepub = true

    /** Times: the Kobo last read the book at T0, KoLib at T1 (later), unless a test says otherwise. */
    private val t0 = 1_700_000_000_000L
    private val t1 = t0 + 3_600_000L

    @Before
    fun setUp() {
        epub = TestEpubs.build(
            File(tmp.root, "sea.epub"),
            mapOf(
                "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
                "OEBPS/content.opf" to OPF.toByteArray(),
                "OEBPS/Text/ch1.xhtml" to CH1.toByteArray(),
                "OEBPS/Text/ch2.xhtml" to CH2.toByteArray(),
                "OEBPS/Text/ch3.xhtml" to CH3.toByteArray(),
                "OEBPS/nav.xhtml" to NAV.toByteArray(),
            ),
        )
        dir = File(tmp.root, "book").apply { mkdirs() }
        dbFile = File(tmp.root, "KoboReader.sqlite")
    }

    private fun koboDb(status: Int = 0, lastRead: Long? = null, bookmark: String? = null, epubOnKobo: Boolean = false) {
        kepub = !epubOnKobo
        book = if (epubOnKobo) EPUB_ID else KEPUB_ID
        dbFile.delete()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                """CREATE TABLE content(ContentID TEXT NOT NULL, ContentType TEXT NOT NULL, MimeType TEXT NOT NULL, BookID TEXT, Title TEXT,
                   DateLastRead TEXT, ReadStatus INTEGER, ___PercentRead INTEGER, ChapterIDBookmarked TEXT, adobe_location TEXT,
                   CurrentChapterProgress REAL, DateAdded TEXT, FirstTimeReading TEXT, ___UserID TEXT NOT NULL, PRIMARY KEY (ContentID))"""
            )
            db.execSQL(
                """CREATE TABLE Bookmark(BookmarkID TEXT NOT NULL, VolumeID TEXT NOT NULL, ContentID TEXT NOT NULL,
                   StartContainerPath TEXT NOT NULL, StartContainerChildIndex INTEGER NOT NULL, StartOffset INTEGER NOT NULL,
                   EndContainerPath TEXT NOT NULL, EndContainerChildIndex INTEGER NOT NULL, EndOffset INTEGER NOT NULL,
                   Text TEXT, Annotation TEXT, ExtraAnnotationData BLOB, DateCreated TEXT, ChapterProgress REAL NOT NULL DEFAULT 0,
                   Hidden BOOL NOT NULL DEFAULT 0, Version TEXT, DateModified TEXT, Creator TEXT, UUID TEXT, UserID TEXT, SyncTime TEXT,
                   Published BIT DEFAULT false, ContextString TEXT, Type TEXT, Color INTEGER DEFAULT 0, PRIMARY KEY(BookmarkID))"""
            )
            db.execSQL("CREATE TABLE user(UserID TEXT NOT NULL, UserKey TEXT)")
            db.execSQL("INSERT INTO user VALUES ('user-123', 'key')")
            val mime = if (epubOnKobo) "application/epub+zip" else "application/x-kobo-epub+zip"
            db.execSQL(
                "INSERT INTO content VALUES (?, '6', ?, NULL, 'The Sea', ?, ?, 0, ?, NULL, NULL, '2026-01-01T00:00:00Z', 'true', '')",
                arrayOf<Any?>(book, mime, lastRead?.let { KoboMarkStyle.dateStyle("2026-01-01T00:00:00Z", java.time.format.DateTimeFormatter.ISO_INSTANT).format(java.time.Instant.ofEpochMilli(it)) }, status, bookmark),
            )
            for ((i, c) in listOf("ch1", "ch2", "ch3").withIndex()) {
                val id = if (epubOnKobo) "$book#(${i + 1})OEBPS/Text/$c.xhtml" else "$book!!OEBPS/Text/$c.xhtml"
                db.execSQL("INSERT INTO content VALUES (?, '9', ?, ?, ?, NULL, 0, 0, NULL, NULL, NULL, NULL, NULL, '')", arrayOf<Any?>(id, mime, book, c))
            }
        }
    }

    private fun <T> onKobo(block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use(block)

    private fun SQLiteDatabase.one(sql: String, vararg args: String): String? =
        rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Syncs, as the app does: the Kobo's side in one transaction, then KoLib's. */
    private fun sync(now: Long = t1 + 60_000L): ReadingSync.Plan = onKobo { db ->
        val src = AndroidSqlSource(db)
        val style = KoboMarkStyle.read(src)
        val reading = KoboReadingDb.read(src, book, style)!!
        val plan = ReadingSync("kobo-1", style, now).plan(ReadingSync.Book("b", "The Sea", dir, epub, koboBook(reading), reading))
        db.beginTransaction()
        try {
            plan.applyTo(KoboDbEditor(db))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        plan.reader?.let { File(dir, "reader.json").writeText(it.toString()) }
        plan
    }

    private fun reader(): JSONObject = JSONObject(File(dir, "reader.json").readText())

    private fun spanOf(xhtml: String, words: String): String {
        val t = ChapterText(KepubConverter.convert(xhtml))
        val s = t.find(words, 0)!!
        return t.spanAt(s.start, s.startOffset)!!.first
    }

    @Test
    fun whatsReadInKoLibGoesToTheKobo() {
        koboDb(lastRead = t0)
        val place = ReaderPosition("OEBPS/Text/ch2.xhtml", "3/1:0", 0.5f, 0.5f, "The tide came in again. It always did.", "Two", at = t1)
        val note = Annotation(
            id = "aaaaaaaa-0000-4000-8000-000000000001", path = "OEBPS/Text/ch2.xhtml", start = "3/1:0", end = "3/1:20",
            text = "Gulls cried over it.", occurrence = 0, color = HighlightColor.GREEN, note = "birds", created = t0, modified = t0,
        )
        val mark = ReaderPosition("OEBPS/Text/ch3.xhtml", "chars=5", 0.3f, 0.8f, "there.", "Three", at = t0, id = "bbbbbbbb-0000-4000-8000-000000000002")
        ReaderStore(dir).save(ReaderState(place, listOf(mark), listOf(note)))

        val plan = sync()
        assertTrue(plan.counts.placeToKobo)
        assertEquals(2, plan.counts.toKobo)
        onKobo { db ->
            // Where it's up to: the sentence at the place.
            assertEquals("OEBPS/Text/ch2.xhtml#" + spanOf(CH2, "The tide came in again."), db.one("SELECT ChapterIDBookmarked FROM content WHERE ContentID = ?", book))
            assertEquals("1", db.one("SELECT ReadStatus FROM content WHERE ContentID = ?", book))
            assertEquals("50", db.one("SELECT ___PercentRead FROM content WHERE ContentID = ?", book))
            assertEquals(t1 / 1000, KoboDates.parseMillis(db.one("SELECT DateLastRead FROM content WHERE ContentID = ?", book))!! / 1000)
            // The highlight and its note, as the Kobo keeps them.
            val gulls = spanOf(CH2, "Gulls cried over it.").replace(".", "\\.")
            db.rawQuery("SELECT * FROM Bookmark WHERE BookmarkID = ?", arrayOf(note.id)).use { c ->
                assertTrue(c.moveToFirst())
                fun s(col: String) = c.getString(c.getColumnIndexOrThrow(col))
                assertEquals(book, s("VolumeID"))
                assertEquals("$book!!OEBPS/Text/ch2.xhtml", s("ContentID"))
                assertEquals("span#$gulls", s("StartContainerPath"))
                assertEquals("0", s("StartOffset"))
                assertEquals("span#$gulls", s("EndContainerPath"))
                assertEquals("20", s("EndOffset"))
                assertEquals("Gulls cried over it.", s("Text"))
                assertEquals("birds", s("Annotation"))
                assertEquals("note", s("Type"))
                assertEquals("3", s("Color"))
                assertEquals("user-123", s("UserID"))
                assertEquals("-99", s("StartContainerChildIndex"))
                assertEquals("false", s("Hidden"))
            }
            // The bookmark.
            assertEquals("dogear", db.one("SELECT Type FROM Bookmark WHERE BookmarkID = ?", mark.id!!))
            assertEquals("$book!!OEBPS/Text/ch3.xhtml", db.one("SELECT ContentID FROM Bookmark WHERE BookmarkID = ?", mark.id!!))
        }
        // KoLib knows they're on this Kobo; a second sync has nothing to do.
        assertTrue(reader().getJSONObject("kobo").getJSONObject(note.id).has("kobo-1"))
        val again = sync()
        assertFalse(again.touchesKobo)
        assertNull(again.reader)
    }

    @Test
    fun whatsReadOnTheKoboComesToKoLib() {
        val tide = spanOf(CH2, "The tide came in again.")
        val nobody = "span#" + spanOf(CH1, "Nobody expected the storm.").replace(".", "\\.")
        koboDb(status = 1, lastRead = t1, bookmark = "OEBPS/Text/ch3.xhtml#" + spanOf(CH3, "Second one here."))
        onKobo { db ->
            db.execSQL(
                """INSERT INTO Bookmark (BookmarkID, VolumeID, ContentID, StartContainerPath, StartContainerChildIndex, StartOffset,
                   EndContainerPath, EndContainerChildIndex, EndOffset, Text, Annotation, DateCreated, ChapterProgress, Hidden, Type, Color)
                   VALUES ('kobo-hl', ?, ?, ?, -99, 4, ?, -99, 13, 'tide came', 'note!', '2026-02-01T10:00:00.000', 0.5, 'false', 'note', 2)""",
                arrayOf<Any?>(book, "$book!!OEBPS/Text/ch2.xhtml", "span#" + tide.replace(".", "\\."), "span#" + tide.replace(".", "\\.")),
            )
            db.execSQL(
                """INSERT INTO Bookmark (BookmarkID, VolumeID, ContentID, StartContainerPath, StartContainerChildIndex, StartOffset,
                   EndContainerPath, EndContainerChildIndex, EndOffset, DateCreated, ChapterProgress, Hidden, Type)
                   VALUES ('kobo-dog', ?, ?, ?, -99, 0, ?, -99, 0, '2026-02-01T10:00:00.000', 0.1, 'false', 'dogear')""",
                arrayOf<Any?>(book, "$book!!OEBPS/Text/ch1.xhtml", nobody, nobody),
            )
        }
        val plan = sync()
        assertTrue(plan.counts.placeFromKobo)
        assertEquals(2, plan.counts.fromKobo)
        val r = reader()
        // The place: ch3, where the Kobo is, as of when it was read there.
        val p = ReaderPosition.from(r.getJSONObject("position"))!!
        assertEquals("OEBPS/Text/ch3.xhtml", p.path)
        assertTrue(p.snippet, p.snippet.startsWith("Second one here."))
        assertEquals(t1 / 1000, p.at / 1000)
        // The highlight: found again by its words, as KoLib's reader finds it.
        val a = Annotation.from(r.getJSONArray("annotations").getJSONObject(0))!!
        assertEquals("kobo-hl", a.id)
        assertEquals("tide came", a.text)
        assertEquals("note!", a.note)
        assertEquals(HighlightColor.BLUE, a.color)
        val t = ChapterText(CH2)
        val found = t.find(a.text, a.occurrence)!!
        assertEquals("tide came", t.textOf(found))
        // The bookmark: in ch1, at the start of its second sentence.
        val m = ReaderPosition.from(r.getJSONArray("bookmarks").getJSONObject(0))!!
        assertEquals("kobo-dog", m.id)
        assertEquals("OEBPS/Text/ch1.xhtml", m.path)
        assertTrue(m.snippet, m.snippet.startsWith("Nobody expected the storm."))
        // Brought together: nothing more next time.
        val again = sync()
        assertFalse(again.touchesKobo)
        assertNull(again.reader)
    }

    @Test
    fun deletedOnEitherSideIsDeletedOnTheOther() {
        whatsReadInKoLibGoesToTheKobo()
        val noteId = "aaaaaaaa-0000-4000-8000-000000000001"
        val markId = "bbbbbbbb-0000-4000-8000-000000000002"
        // The highlight deleted on the Kobo: it goes from KoLib, and KoLib remembers it went.
        onKobo { db -> db.execSQL("DELETE FROM Bookmark WHERE BookmarkID = ?", arrayOf(noteId)) }
        val first = sync()
        assertEquals(1, first.counts.removedHere)
        val state = ReaderStore(dir).load()
        assertTrue(state.annotations.none { it.id == noteId })
        assertTrue(noteId in state.deleted)
        // The bookmark deleted in KoLib (as the reader deletes it): it goes from the Kobo.
        ReaderStore(dir).save(state.copy(bookmarks = emptyList(), deleted = state.deleted + (markId to t1)))
        val second = sync()
        assertEquals(listOf(markId), second.delete)
        onKobo { db -> assertNull(db.one("SELECT BookmarkID FROM Bookmark WHERE BookmarkID = ?", markId)) }
        // And neither comes back.
        val third = sync()
        assertFalse(third.touchesKobo)
    }

    @Test
    fun aNoteChangedOnOneSideIsChangedOnTheOther() {
        whatsReadInKoLibGoesToTheKobo()
        val id = "aaaaaaaa-0000-4000-8000-000000000001"
        // Changed on the Kobo (with an older date than KoLib's, as the Kobo may leave it): it's the Kobo's change.
        onKobo { db -> db.execSQL("UPDATE Bookmark SET Annotation = 'seabirds', DateModified = '2020-01-01T00:00:00.000' WHERE BookmarkID = ?", arrayOf(id)) }
        sync()
        assertEquals("seabirds", ReaderStore(dir).load().annotations.single { it.id == id }.note)
        // Changed in KoLib: it goes to the Kobo.
        val state = ReaderStore(dir).load()
        ReaderStore(dir).save(state.copy(annotations = state.annotations.map { if (it.id == id) it.copy(note = "gulls", modified = t1 + 5_000) else it }))
        sync()
        onKobo { db -> assertEquals("gulls", db.one("SELECT Annotation FROM Bookmark WHERE BookmarkID = ?", id)) }
        assertFalse(sync().touchesKobo)
    }

    @Test
    fun anEpubOnTheKoboGetsItsPlaceAsAnAdobePoint() {
        koboDb(lastRead = t0, epubOnKobo = true)
        val place = ReaderPosition("OEBPS/Text/ch2.xhtml", "3/1:0", 0.5f, 0.5f, "The tide came in again. It always did.", "Two", at = t1)
        val note = Annotation(path = "OEBPS/Text/ch2.xhtml", start = "3/1:0", end = "3/1:20", text = "Gulls cried over it.", created = t0)
        ReaderStore(dir).save(ReaderState(place, emptyList(), listOf(note)))
        val plan = sync()
        // Highlights go to the Kobo only in kepubs.
        assertEquals(1, plan.counts.notSent)
        assertEquals(0, plan.counts.toKobo)
        val bookmark = onKobo { db -> db.one("SELECT ChapterIDBookmarked FROM content WHERE ContentID = ?", book) }!!
        assertTrue(bookmark, bookmark.startsWith("OEBPS/Text/ch2.xhtml#point(/1/"))
        assertEquals(bookmark, onKobo { db -> db.one("SELECT adobe_location FROM content WHERE ContentID = ?", book) })
        // Read back the way KoLib reads the Kobo's place: the same sentence.
        val kobo = koboBook(onKobo { db -> KoboReadingDb.read(AndroidSqlSource(db), book, KoboMarkStyle.read(AndroidSqlSource(db))) }!!)
        val found = KoboPlaces.find(epub, kobo.copy(chapterBookmark = bookmark, adobeLocation = bookmark))!!
        assertEquals(KoboPlaces.Match.EXACT, found.match)
        assertTrue(found.position.snippet, found.position.snippet.startsWith("The tide came in again."))
    }

    @Test
    fun aBookFinishedInKoLibIsFinishedOnTheKobo() {
        koboDb(status = 1, lastRead = t0)
        ReaderStore.markFinished(dir, now = t1)
        assertTrue(sync().counts.placeToKobo)
        onKobo { db ->
            assertEquals("2", db.one("SELECT ReadStatus FROM content WHERE ContentID = ?", book))
            assertEquals("100", db.one("SELECT ___PercentRead FROM content WHERE ContentID = ?", book))
        }
    }

    private fun koboBook(r: KoboReading) = KoboBook(
        contentId = r.contentId, title = "The Sea", author = null, subtitle = null, series = null, seriesNumber = null,
        description = null, publisher = null, language = null, isbn = null,
        mimeType = if (kepub) "application/x-kobo-epub+zip" else "application/epub+zip", format = if (kepub) "KEPUB" else "EPUB",
        imageId = "x", kind = BookKind.SIDELOADED, isDownloaded = true, isExpired = false,
        readStatus = ReadStatus.fromKobo(r.readStatus.toLong()), percentRead = r.percent, lastReadMillis = r.lastRead, addedMillis = null,
        fileSize = null, relativePath = r.contentId.removePrefix("file:///mnt/onboard/"),
        chapterBookmark = r.chapterBookmark, adobeLocation = r.adobeLocation, chapterProgress = r.chapterProgress,
    )

    private companion object {
        const val KEPUB_ID = "file:///mnt/onboard/Books/Sea.kepub.epub"
        const val EPUB_ID = "file:///mnt/onboard/Books/Sea.epub"

        val OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>The Sea</dc:title><dc:language>en</dc:language></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="c3" href="Text/ch3.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="c1"/><itemref idref="c2"/><itemref idref="c3"/></spine>
</package>"""
        val NAV = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol>
<li><a href="Text/ch1.xhtml">One</a></li><li><a href="Text/ch2.xhtml">Two</a></li><li><a href="Text/ch3.xhtml">Three</a></li></ol></nav></body></html>"""
        val CH1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head>
<body><h1>One</h1><p>It began on a calm morning. Nobody expected the storm.</p><p>By noon the sky had turned.</p></body></html>"""
        val CH2 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head>
<body>
  <h1>Two</h1>
  <p>The sea was grey. Gulls cried over it.</p>
  <p>The tide came in again. It always did.</p>
  <p>Night fell, and the lamps were lit along the harbour wall.</p>
</body></html>"""
        val CH3 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Three</title></head>
<body><div><p>Hello there.</p><p>Second one here.</p></div></body></html>"""
    }
}
