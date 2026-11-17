package dev.kobolibrary.app.fake

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dev.kobolibrary.app.kobo.KoboImages
import java.io.File
import kotlin.math.abs

/** A realistic Clara 2E drive: version file, database, a few book files and cached covers. */
object FakeKobo {

    data class Book(
        val title: String,
        val author: String,
        val series: String? = null,
        val number: String? = null,
        val status: Int = 0,
        val percent: Int = 0,
        val lastRead: String? = null,
        val collections: List<String> = emptyList(),
        val store: Boolean = false,
        val cover: Boolean = true,
        val mime: String = "application/x-kobo-epub+zip",
        val description: String? = null,
        /** Subjects in the book's own file (the Kobo's database doesn't keep them). */
        val tags: List<String> = emptyList(),
    ) {
        val contentId: String
            get() = if (store) {
                "%08x-0000-4000-8000-%012x".format(abs(title.hashCode()), abs((title + author).hashCode()).toLong())
            } else {
                val ext = if (mime == "application/epub+zip") ".epub" else ".kepub.epub"
                "file:///mnt/onboard/Books/$author/$title$ext"
            }
        val imageId: String get() = if (store) contentId else KoboImages.imageIdFromContentId(contentId)
    }

    /**
     * For pictures of the app with other books (the advert's): these books, and real cover art for them
     * (JPEG, by title), in place of the usual ones. Set back to null and empty after.
     */
    var bookList: List<Book>? = null
    var collectionList: List<String>? = null
    var coverArt: Map<String, ByteArray> = emptyMap()

    val books: List<Book> get() = bookList ?: defaultBooks
    val collections: List<String> get() = collectionList ?: defaultCollections

    private val defaultBooks = listOf(
        Book("Meditations", "Marcus Aurelius", status = 2, percent = 100, lastRead = "2026-09-02T21:14:00Z",
            collections = listOf("Philosophy", "Classics"), tags = listOf("Philosophy", "Stoicism"),
            description = "<p>Private notes of a Roman emperor on <b>duty</b>, virtue and the brevity of life.</p>"),
        Book("The Histories", "Herodotus", status = 1, percent = 37, lastRead = "2026-11-12T20:41:00Z",
            collections = listOf("History", "Classics", "To Read"), tags = listOf("History", "Ancient Greece")),
        Book("The Peloponnesian War", "Thucydides", status = 1, percent = 12, lastRead = "2026-11-10T09:05:00Z",
            collections = listOf("History", "Classics"), tags = listOf("History", "Ancient Greece", "War")),
        Book("The Republic", "Plato", collections = listOf("Philosophy", "Classics"), tags = listOf("Philosophy", "Politics")),
        Book("Nicomachean Ethics", "Aristotle", collections = listOf("Philosophy")),
        Book("The Odyssey", "Homer", status = 2, percent = 100, lastRead = "2026-06-30T19:00:00Z", collections = listOf("Classics"), tags = listOf("Poetry", "Ancient Greece")),
        Book("The Iliad", "Homer", collections = listOf("Classics", "To Read"), tags = listOf("Poetry", "Ancient Greece", "War")),
        Book("The Prince", "Niccolò Machiavelli", collections = listOf("Philosophy", "History"), tags = listOf("Politics")),
        Book("Leviathan", "Thomas Hobbes", collections = listOf("Philosophy"), cover = false),
        Book("A Study in Scarlet", "Arthur Conan Doyle", "Sherlock Holmes", "1", status = 2, percent = 100,
            lastRead = "2026-03-11T22:30:00Z", collections = listOf("Sherlock Holmes")),
        Book("The Sign of the Four", "Arthur Conan Doyle", "Sherlock Holmes", "2", status = 1, percent = 64,
            lastRead = "2026-11-01T22:00:00Z", collections = listOf("Sherlock Holmes")),
        Book("The Hound of the Baskervilles", "Arthur Conan Doyle", "Sherlock Holmes", "5", collections = listOf("Sherlock Holmes", "To Read")),
        Book("Dracula", "Bram Stoker", mime = "application/epub+zip", collections = listOf("Gothic"), tags = listOf("Horror", "Gothic")),
        Book("Frankenstein", "Mary Shelley", status = 2, percent = 100, lastRead = "2025-10-31T23:00:00Z", collections = listOf("Gothic"), tags = listOf("Horror", "Science Fiction")),
        Book("The Count of Monte Cristo", "Alexandre Dumas", status = 1, percent = 81, lastRead = "2026-10-20T18:00:00Z"),
        Book("War and Peace", "Leo Tolstoy", store = true, collections = listOf("To Read")),
        Book("The Wealth of Nations", "Adam Smith", store = true, cover = false),
        Book("Moby-Dick", "Herman Melville", mime = "application/epub+zip"),
    )

    private val defaultCollections = listOf("Classics", "Gothic", "History", "Philosophy", "Sherlock Holmes", "To Read")

    fun create(root: File) {
        root.deleteRecursively()
        val kobo = File(root, ".kobo").apply { mkdirs() }
        File(kobo, "version").writeText("N418231234567,4.1.15,4.38.23171,4.1.15,4.1.15,00000000-0000-0000-0000-000000000386\n")
        File(root, ".adds").mkdirs()
        createDatabase(File(kobo, "KoboReader.sqlite"))
        for (b in books.filter { !it.store }) {
            val path = b.contentId.removePrefix("file:///mnt/onboard/")
            epub(File(root, path), b)
        }
        for (b in books.filter { it.cover }) writeCovers(root, b)
    }

    private fun createDatabase(file: File) {
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE DbVersion(version INTEGER)")
        db.execSQL("INSERT INTO DbVersion VALUES (174)")
        db.execSQL(
            """CREATE TABLE content(ContentID TEXT NOT NULL, ContentType TEXT NOT NULL, MimeType TEXT NOT NULL, BookID TEXT,
               BookTitle TEXT, ImageId TEXT, Title TEXT COLLATE NOCASE, Attribution TEXT COLLATE NOCASE, Description TEXT,
               Publisher TEXT, DateLastRead TEXT, ReadStatus INTEGER, ___UserID TEXT NOT NULL, ___FileSize INTEGER,
               ___PercentRead INTEGER, ___ExpirationStatus INTEGER, Accessibility INTEGER DEFAULT 1, Language TEXT,
               IsDownloaded BIT NOT NULL DEFAULT 1, ISBN TEXT, Series TEXT, SeriesNumber TEXT, Subtitle TEXT, DateAdded TEXT,
               SeriesNumberFloat REAL, SeriesID TEXT, DateCreated TEXT, WordCount INTEGER DEFAULT -1,
               TimeSpentReading INTEGER DEFAULT 0, RestOfBookEstimate INTEGER DEFAULT 0, PRIMARY KEY (ContentID))"""
        )
        db.execSQL(
            """CREATE TABLE Shelf(CreationDate TEXT, Id TEXT, InternalName TEXT, LastModified TEXT, Name TEXT, Type TEXT,
               _IsDeleted BOOL, _IsVisible BOOL, _IsSynced BOOL, _SyncTime TEXT, LastAccessed TEXT, PRIMARY KEY(Id))"""
        )
        db.execSQL(
            """CREATE TABLE ShelfContent(ShelfName TEXT, ContentId TEXT, DateModified TEXT, _IsDeleted BOOL, _IsSynced BOOL,
               PRIMARY KEY(ShelfName, ContentId))"""
        )
        books.forEachIndexed { i, b ->
            db.execSQL(
                """INSERT INTO content (ContentID, ContentType, MimeType, BookID, ImageId, Title, Attribution, Description,
                   Publisher, DateLastRead, ReadStatus, ___UserID, ___FileSize, ___PercentRead, Accessibility, Language,
                   IsDownloaded, Series, SeriesNumber, DateAdded)
                   VALUES (?, '6', ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'en', 'true', ?, ?, ?)""",
                arrayOf<Any?>(
                    b.contentId, b.mime, b.imageId, b.title, b.author, b.description,
                    if (b.store) "Kobo Classics" else null, b.lastRead, b.status,
                    if (b.store) "user-0001" else "", 400_000 + i * 37_000, b.percent, if (b.store) 1 else -1,
                    b.series, b.number, "2026-%02d-%02dT10:00:00Z".format(1 + i % 10, 1 + i),
                ),
            )
        }
        // How long the Kobo says each book it's opened has been read for, and how long it reckons is left.
        books.forEachIndexed { i, b ->
            if (b.status == 0) return@forEachIndexed
            val spent = 3_600 * (2 + i % 5) + 540 * i
            val left = if (b.status == 2) 0 else (spent * (100 - b.percent) / b.percent.coerceAtLeast(1)).coerceAtMost(400_000)
            db.execSQL("UPDATE content SET TimeSpentReading = ?, RestOfBookEstimate = ? WHERE ContentID = ?", arrayOf<Any?>(spent, left, b.contentId))
        }
        // Stars given on the Kobo, and the Kobo's own word counts for a few books.
        db.execSQL("CREATE TABLE ratings(ContentID TEXT NOT NULL, Rating INTEGER, DateModified TEXT NOT NULL, PRIMARY KEY (ContentID))")
        for ((title, stars) in listOf("Meditations" to 5, "The Odyssey" to 4, "A Study in Scarlet" to 4, "Frankenstein" to 3)) {
            val b = books.firstOrNull { it.title == title } ?: continue
            db.execSQL("INSERT INTO ratings VALUES (?, ?, '2026-09-02T21:20:00Z')", arrayOf<Any?>(b.contentId, stars))
        }
        for ((title, words) in listOf("The Histories" to 189_000, "War and Peace" to 587_000, "The Republic" to 118_000)) {
            val b = books.firstOrNull { it.title == title } ?: continue
            db.execSQL("UPDATE content SET WordCount = ? WHERE ContentID = ?", arrayOf<Any?>(words, b.contentId))
        }
        for (name in collections) {
            db.execSQL(
                "INSERT INTO Shelf VALUES ('2026-01-01T00:00:00Z', ?, ?, '2026-01-02T00:00:00Z', ?, 'UserTag', 'false', 'true', 'false', NULL, NULL)",
                arrayOf<Any?>("id-$name", name, name),
            )
        }
        for (b in books) for (c in b.collections) {
            db.execSQL(
                "INSERT INTO ShelfContent VALUES (?, ?, '2026-02-01T00:00:00Z', 'false', 'false')",
                arrayOf<Any?>(c, b.contentId),
            )
        }
        db.close()
    }

    /**
     * A small but real book file. Kepubs have the Kobo's spans and wrappers, as kepubify and Calibre
     * add them. Moby-Dick's file has no author, so the Kobo's own record has to fill it in.
     */
    fun epub(file: File, b: Book): File {
        val kepub = b.mime != "application/epub+zip"
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        val creator = if (b.title == "Moby-Dick") "" else
            """<dc:creator opf:role="aut" opf:file-as="${esc(dev.kobolibrary.app.epub.Names.authorSort(b.author))}">${esc(b.author)}</dc:creator>"""
        val series = b.series?.let {
            """<meta name="calibre:series" content="${esc(it)}"/><meta name="calibre:series_index" content="${b.number ?: "1"}"/>"""
        }.orEmpty()
        val opf = """<?xml version='1.0' encoding='utf-8'?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uuid_id" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>${esc(b.title)}</dc:title>
    $creator
    <dc:identifier opf:scheme="uuid" id="uuid_id">${java.util.UUID.nameUUIDFromBytes(b.title.toByteArray())}</dc:identifier>
    <dc:language>en</dc:language>
    ${b.description?.let { "<dc:description>${esc(it)}</dc:description>" }.orEmpty()}
    ${b.tags.joinToString("") { "<dc:subject>${esc(it)}</dc:subject>" }}
    $series
    ${if (b.cover) "<meta name=\"cover\" content=\"cover\"/>" else ""}
  </metadata>
  <manifest>
    ${if (b.cover) "<item href=\"cover.jpeg\" id=\"cover\" media-type=\"image/jpeg\"/>" else ""}
    <item href="text/ch1.xhtml" id="ch1" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="ch1"/></spine>
</package>"""
        val chapter = if (kepub) {
            """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title><style type="text/css" class="kobostylehacks">div#book-inner { margin-top: 0; margin-bottom: 0;}</style></head>
<body><div id="book-columns"><div id="book-inner"><h1><span class="koboSpan" id="kobo.1.1">Chapter One</span></h1><p><span class="koboSpan" id="kobo.2.1">It begins here. </span><span class="koboSpan" id="kobo.2.2">And so it goes.</span></p></div></div></body></html>"""
        } else {
            """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head>
<body><h1>Chapter One</h1><p>It begins here. And so it goes.</p></body></html>"""
        }
        val entries = linkedMapOf(
            "META-INF/container.xml" to dev.kobolibrary.app.epub.TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/text/ch1.xhtml" to chapter.toByteArray(),
        )
        if (b.cover) entries["OEBPS/cover.jpeg"] = drawCover(b, 600, 900)
        return dev.kobolibrary.app.epub.TestEpubs.build(file, entries)
    }

    private val palette = intArrayOf(
        0xFF6E2C2C.toInt(), 0xFF2C4A6E.toInt(), 0xFF3E5A3A.toInt(), 0xFF5A4632.toInt(),
        0xFF2F2F3A.toInt(), 0xFF7A5C1E.toInt(), 0xFF4A2F5A.toInt(),
    )

    private fun writeCovers(root: File, b: Book) {
        val dir = File(root, ".kobo-images/" + KoboImages.folderFor(b.imageId)).apply { mkdirs() }
        File(dir, b.imageId + " - N3_LIBRARY_GRID.parsed").writeBytes(drawCover(b, 149, 223))
        File(dir, b.imageId + " - N3_FULL.parsed").writeBytes(drawCover(b, 536, 724))
    }

    fun drawCover(b: Book, w: Int, h: Int): ByteArray {
        coverArt[b.title]?.let { art ->
            // The real cover, scaled to fill the size asked for.
            val src = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
            val scaled = Bitmap.createScaledBitmap(src, w, h, true)
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            return out.toByteArray()
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = palette[abs(b.title.hashCode()) % palette.size]
        c.drawColor(bg)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8C77A.toInt(); style = Paint.Style.STROKE; strokeWidth = w / 60f }
        val inset = w / 14f
        c.drawRect(inset, inset, w - inset, h - inset, gold)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = w / 8.5f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val layout = StaticLayout.Builder.obtain(b.title, 0, b.title.length, titlePaint, (w - inset * 3).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save()
        c.translate(inset * 1.5f, h * 0.22f)
        layout.draw(c)
        c.restore()
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8C77A.toInt(); textSize = w / 13f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(b.author.uppercase(), w / 2f, h - inset * 2.2f, authorPaint)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }
}
