package dev.kobolibrary.app.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.kobolibrary.app.epub.BookMetadata
import org.json.JSONArray
import org.json.JSONObject

/** The library's index. Each book's file lives in its own folder; this table lists them. */
class LibraryDb(context: Context, name: String? = "library.db") : SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE books (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                title_sort TEXT,
                authors TEXT NOT NULL,
                author_sorts TEXT,
                series TEXT,
                series_index REAL,
                tags TEXT NOT NULL,
                collections TEXT NOT NULL,
                publisher TEXT,
                published TEXT,
                language TEXT,
                isbn TEXT,
                description TEXT,
                rating INTEGER,
                identifier TEXT,
                file_size INTEGER NOT NULL,
                has_cover INTEGER NOT NULL,
                cover_version INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                source TEXT NOT NULL,
                original_name TEXT,
                kobo_path TEXT,
                sent_at INTEGER,
                words INTEGER,
                kobo_collections TEXT,
                cover_as_is INTEGER NOT NULL DEFAULT 0,
                source_format TEXT
            )"""
        )
        db.execSQL("CREATE INDEX books_kobo_path ON books(kobo_path)")
        db.execSQL("CREATE TABLE kobo_collections (name TEXT PRIMARY KEY COLLATE NOCASE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE books ADD COLUMN sent_at INTEGER")
            db.execSQL("ALTER TABLE books ADD COLUMN words INTEGER")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE books ADD COLUMN kobo_collections TEXT")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE books ADD COLUMN cover_as_is INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE books ADD COLUMN source_format TEXT")
        }
    }

    fun all(): List<LibraryBook> = readableDatabase.rawQuery("SELECT * FROM books", null).use { c ->
        buildList { while (c.moveToNext()) add(c.toBook()) }
    }

    fun upsert(book: LibraryBook) {
        writableDatabase.insertWithOnConflict("books", null, book.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(ids: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("books", "id = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun koboCollections(): List<String> = readableDatabase.rawQuery("SELECT name FROM kobo_collections ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun replaceKoboCollections(names: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("kobo_collections", null, null)
            for (n in names) db.insertWithOnConflict("kobo_collections", null, ContentValues().apply { put("name", n) }, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun LibraryBook.toValues() = ContentValues().apply {
        put("id", id)
        put("title", meta.title)
        put("title_sort", meta.titleSort)
        put("authors", JSONArray(meta.authors).toString())
        put("author_sorts", JSONObject(meta.authorSorts as Map<*, *>).toString())
        put("series", meta.series)
        put("series_index", meta.seriesIndex)
        put("tags", JSONArray(meta.tags).toString())
        put("collections", JSONArray(collections).toString())
        put("publisher", meta.publisher)
        put("published", meta.published)
        put("language", meta.language)
        put("isbn", meta.isbn)
        put("description", meta.description)
        put("rating", meta.rating)
        put("identifier", meta.identifier)
        put("file_size", fileSize)
        put("has_cover", if (hasCover) 1 else 0)
        put("cover_version", coverVersion)
        put("added_at", addedAt)
        put("modified_at", modifiedAt)
        put("source", source.name)
        put("original_name", originalName)
        put("kobo_path", koboPath)
        put("sent_at", sentAt)
        put("words", words)
        put("kobo_collections", koboCollections?.let { JSONArray(it).toString() })
        put("cover_as_is", if (coverAsIs) 1 else 0)
        put("source_format", sourceFormat)
    }

    private fun Cursor.toBook(): LibraryBook {
        fun str(col: String): String? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getString(it) }
        fun lng(col: String): Long? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getLong(it) }
        fun dbl(col: String): Double? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getDouble(it) }
        fun listOrNull(col: String): List<String>? = str(col)?.let { s ->
            runCatching { JSONArray(s).let { a -> List(a.length()) { a.getString(it) } } }.getOrNull()
        }
        fun list(col: String): List<String> = listOrNull(col).orEmpty()
        val sorts = str("author_sorts")?.let { s ->
            runCatching { JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrNull()
        }.orEmpty()
        return LibraryBook(
            id = str("id")!!,
            meta = BookMetadata(
                title = str("title").orEmpty(),
                titleSort = str("title_sort"),
                authors = list("authors"),
                authorSorts = sorts,
                series = str("series"),
                seriesIndex = dbl("series_index"),
                tags = list("tags"),
                publisher = str("publisher"),
                published = str("published"),
                language = str("language"),
                isbn = str("isbn"),
                description = str("description"),
                rating = lng("rating")?.toInt(),
                identifier = str("identifier"),
            ),
            collections = list("collections"),
            fileSize = lng("file_size") ?: 0,
            hasCover = (lng("has_cover") ?: 0) != 0L,
            coverVersion = lng("cover_version") ?: 0,
            addedAt = lng("added_at") ?: 0,
            modifiedAt = lng("modified_at") ?: 0,
            source = runCatching { BookSource.valueOf(str("source")!!) }.getOrDefault(BookSource.FILE),
            originalName = str("original_name"),
            koboPath = str("kobo_path"),
            sentAt = lng("sent_at"),
            words = lng("words")?.toInt(),
            koboCollections = listOrNull("kobo_collections"),
            coverAsIs = (lng("cover_as_is") ?: 0) != 0L,
            sourceFormat = str("source_format"),
        )
    }

    private companion object {
        const val VERSION = 5
    }
}
