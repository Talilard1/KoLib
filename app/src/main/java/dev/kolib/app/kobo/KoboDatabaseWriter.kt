package dev.kolib.app.kobo

import dev.kolib.app.tr
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.kolib.app.backup.BackupManager
import dev.kolib.app.log.AppLog
import dev.kolib.app.saf.SafTree
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The Kobo's database isn't as the app last read it, so writing it back could lose something. */
class KoboDatabaseChangedException(message: String) : IOException(message)

/**
 * Changes the Kobo's database. Android can't open a database that's on a USB drive, so the app
 * changes a copy and puts the copy back:
 *
 *  1. the database as it is on the Kobo now is backed up on the tablet, unless that exact copy already is;
 *  2. a fresh copy is changed in one transaction, folded into a single file and integrity-checked;
 *  3. usually just the pages that changed are rewritten on the Kobo, with a journal the Kobo uses to undo
 *     them if it's unplugged part way (see [PageWriter]). Otherwise:
 *  4. it's written to the Kobo under a temporary name and read back, to be sure it arrived intact;
 *  5. the old database's -wal and -shm files are removed (what they held is already in the new file, and
 *     left behind they would be replayed over it), then the new file takes the old one's place.
 *
 * Until step 3 or 5 nothing on the Kobo has changed, so any failure before it leaves the Kobo as it was.
 */
class KoboDatabaseWriter(private val workRoot: File, private val backups: BackupManager) {

    /** The database now on the Kobo, as a file on the tablet, and how many changes went into it. */
    class Written(val database: File, val changes: Int)

    /**
     * Runs [edit] on a copy of [current] (the database as it is on the Kobo now, with [currentWal] if it
     * had one) and puts the result on the Kobo. [edit] returns how many changes it made; with none,
     * nothing is written and null is returned.
     */
    fun write(tree: SafTree, current: File, currentWal: File?, deviceLabel: String, edit: (KoboDbEditor) -> Int): Written? {
        val started = System.currentTimeMillis()
        val koboDir = tree.child(tree.rootDocId, ".kobo")
            ?: throw IOException(tr("The Kobo's .kobo folder can't be found. Is it still connected?"))
        tree.child(koboDir.docId, TEMP_NAME)?.let { runCatching { tree.delete(it.docId) } }
        val onKobo = tree.child(koboDir.docId, DB_NAME) ?: throw IOException(tr("The Kobo's database can't be found."))
        if (onKobo.size > 0 && onKobo.size != current.length()) {
            throw KoboDatabaseChangedException(
                tr("The Kobo's database has changed since it was read ({0} bytes, expected {1}). Connect the Kobo again first.", onKobo.size, current.length())
            )
        }
        val walOnKobo = tree.child(koboDir.docId, "$DB_NAME-wal")?.size ?: 0L
        if (walOnKobo != (currentWal?.length() ?: 0L)) {
            throw KoboDatabaseChangedException(tr("The Kobo's database has changed since it was read. Connect the Kobo again first."))
        }
        // A change to the database that wasn't finished, which the Kobo undoes when it next opens it.
        // Writing now could be undone along with it, or mix with it.
        tree.child(koboDir.docId, PageWriter.JOURNAL_NAME)?.takeIf { it.size > 0 }?.let { journal ->
            if (PageWriter.isHot(tree.openInput(journal.docId))) {
                throw KoboDatabaseChangedException(
                    tr("The Kobo has an unfinished change to its database. Eject it and wait until it shows its books, then connect it again.")
                )
            }
        }

        // 1. A backup of the database as it is now. No backup, no write.
        backups.backupIfChanged(current, currentWal, deviceLabel)

        // 2. Change a fresh copy.
        workRoot.mkdirs()
        workRoot.listFiles()
            ?.filter { it != current.parentFile && it.lastModified() < started - 6 * 60 * 60 * 1000L }
            ?.forEach { it.deleteRecursively() }
        val dir = File(workRoot, started.toString()).apply { deleteRecursively(); mkdirs() }
        val db = File(dir, DB_NAME)
        current.copyTo(db)
        currentWal?.copyTo(File(dir, "$DB_NAME-wal"))
        val walMode = usesWal(db)
        val changes = open(db, walMode).use { sql ->
            sql.beginTransaction()
            val n = try {
                edit(KoboDbEditor(sql)).also { if (it > 0) sql.setTransactionSuccessful() }
            } finally {
                sql.endTransaction()
            }
            if (walMode) sql.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            n
        }
        if (changes <= 0) {
            dir.deleteRecursively()
            return null
        }
        val leftoverWal = File(dir, "$DB_NAME-wal")
        if (leftoverWal.length() > 0) throw IOException(tr("The updated database couldn't be combined into a single file, so it wasn't copied to the Kobo."))
        listOf("-wal", "-shm", "-journal").forEach { File(dir, DB_NAME + it).delete() }

        // 3. Check it.
        val check = open(db, walMode).use { sql ->
            sql.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "no answer" }
        }
        listOf("-wal", "-shm", "-journal").forEach { File(dir, DB_NAME + it).delete() }
        if (check != "ok") throw IOException(tr("The updated database failed its check ({0}), so it wasn't copied to the Kobo.", check))

        // 4. Put it on the Kobo. Usually only a few pages changed, and rewriting just those is much quicker
        // than copying the whole file; that can't be done with a write-ahead log waiting to be added to it.
        if (walOnKobo == 0L) {
            val quick = PageWriter.write(tree, koboDir.docId, onKobo.docId, current, db, File(dir, "journal"))
            if (quick == PageWriter.Result.DONE) {
                listOf("-wal", "-shm").forEach { end -> tree.child(koboDir.docId, DB_NAME + end)?.let { runCatching { tree.delete(it.docId) } } }
                AppLog.i("KoboDb", "Wrote $changes change(s) to the Kobo's database (${db.length()} bytes) in ${System.currentTimeMillis() - started} ms.")
                return Written(db, changes)
            }
        }
        val hash = sha256(db.inputStream())

        // Otherwise the whole file, under a temporary name first.
        tree.writeFile(".kobo/$TEMP_NAME", db)
        val temp = tree.child(koboDir.docId, TEMP_NAME) ?: throw IOException(tr("The updated database disappeared from the Kobo while it was being written."))
        val back = tree.openInput(temp.docId).use { sha256(it) }
        if (back != hash) {
            runCatching { tree.delete(temp.docId) }
            throw IOException(tr("The updated database didn't read back correctly from the Kobo, so the Kobo's original database wasn't replaced."))
        }
        // From here on the Kobo's database is being replaced.
        tree.child(koboDir.docId, "$DB_NAME-wal")?.let { tree.delete(it.docId) }
        tree.child(koboDir.docId, "$DB_NAME-shm")?.let { tree.delete(it.docId) }
        val old = tree.child(koboDir.docId, DB_NAME)
        val aside = old?.let { tree.rename(it.docId, OLD_NAME) }
        val swapped = aside?.let { tree.rename(temp.docId, DB_NAME) }
        if (swapped == null) {
            // This drive wouldn't rename; write the new database over (or in place of) the old one instead.
            AppLog.w("KoboDb", "Renaming on the Kobo's drive didn't work; writing the database in place.")
            tree.writeFile(".kobo/$DB_NAME", db)
            tree.child(koboDir.docId, TEMP_NAME)?.let { runCatching { tree.delete(it.docId) } }
        }
        val placed = tree.child(koboDir.docId, DB_NAME)
        if (placed == null || (placed.size > 0 && placed.size != db.length())) {
            throw IOException(tr("The updated database couldn't replace the old one on the Kobo. Don't eject yet. Connect again and check the Kobo's books."))
        }
        aside?.let { id -> runCatching { tree.delete(id) }.onFailure { AppLog.w("KoboDb", "Couldn't delete the old database copy $OLD_NAME", it) } }
        AppLog.i("KoboDb", "Wrote $changes change(s) to the Kobo's database (${db.length()} bytes) in ${System.currentTimeMillis() - started} ms.")
        return Written(db, changes)
    }

    companion object {
        const val DB_NAME = "KoboReader.sqlite"
        private const val TEMP_NAME = "KoboReader.sqlite.kl-new"
        private const val OLD_NAME = "KoboReader.sqlite.kl-old"

        /** Opens a local copy for writing in the journal mode it already uses, without Android's extra table. */
        fun open(file: File, walMode: Boolean): SQLiteDatabase {
            val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            val params = SQLiteDatabase.OpenParams.Builder()
            if (walMode) params.setOpenFlags(flags or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
            else params.setOpenFlags(flags).setJournalMode("DELETE")
            return SQLiteDatabase.openDatabase(file, params.build())
        }

        /** Bytes 18 and 19 of a database's header are 2 when it uses a write-ahead log. */
        fun usesWal(file: File): Boolean = RandomAccessFile(file, "r").use { f ->
            if (f.length() < 100) return false
            f.seek(18)
            f.read() == 2 && f.read() == 2
        }

        fun sha256(input: InputStream): String = input.use { stream ->
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * The changes the app makes inside the Kobo's database, done the way the Kobo itself does them:
 * collections in Shelf / ShelfContent (deletions marked, not erased, so the Kobo can sync them),
 * and book details in the content table, for sideloaded books only.
 */
class KoboDbEditor(private val db: SQLiteDatabase, private val now: String = timestamp()) {

    private val tables: Set<String> by lazy {
        db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0).lowercase()) }
        }
    }

    private val columnCache = HashMap<String, Set<String>>()

    private fun columns(table: String): Set<String> = columnCache.getOrPut(table) {
        db.rawQuery("PRAGMA table_info(\"$table\")", null).use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIndex)) }
        }
    }

    val hasCollections: Boolean get() = "shelf" in tables && "shelfcontent" in tables

    private data class Shelf(val rowId: Long, val name: String?, val internalName: String?, val deleted: Boolean) {
        /** What ShelfContent calls it. */
        val key: String get() = internalName?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    }

    private fun findShelf(name: String): Shelf? {
        val cols = columns("Shelf")
        val nameCol = if ("Name" in cols) "Name" else "InternalName"
        val internalCol = if ("InternalName" in cols) "InternalName" else nameCol
        val deletedCol = if ("_IsDeleted" in cols) "_IsDeleted" else "NULL"
        val found = db.rawQuery(
            "SELECT rowid, \"$nameCol\", \"$internalCol\", $deletedCol FROM Shelf WHERE \"$nameCol\" = ? COLLATE NOCASE OR \"$internalCol\" = ? COLLATE NOCASE",
            arrayOf(name, name),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Shelf(c.getLong(0), c.getString(1), c.getString(2), c.getString(3).equals("true", ignoreCase = true)))
                }
            }
        }
        // Prefer one that isn't deleted, then the exact spelling.
        return found.sortedWith(compareBy<Shelf> { it.deleted }.thenBy { it.name != name }).firstOrNull()
    }

    private fun touchShelf(key: String) {
        val cols = columns("Shelf")
        if ("LastModified" !in cols) return
        val values = ContentValues().apply {
            put("LastModified", now)
            if ("_IsSynced" in cols) put("_IsSynced", "false")
        }
        val nameCol = if ("InternalName" in cols) "InternalName" else "Name"
        db.update("Shelf", values, "\"$nameCol\" = ?", arrayOf(key))
    }

    /**
     * Puts the book [contentId] in the collection [name] (any case). Returns true if it changed. Only a
     * collection the Kobo has can be used: collections made this way don't show on the Kobo, so one it
     * doesn't have (or that was deleted there) is left for the person to make on the Kobo first.
     */
    fun addToCollection(name: String, contentId: String): Boolean {
        val shelf = findShelf(name.trim())?.takeIf { !it.deleted } ?: return false
        val key = shelf.key
        val cols = columns("ShelfContent")
        val deletedCol = if ("_IsDeleted" in cols) "_IsDeleted" else "NULL"
        val existing = db.rawQuery(
            "SELECT rowid, $deletedCol FROM ShelfContent WHERE ShelfName = ? AND ContentId = ?",
            arrayOf(key, contentId),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getString(1).equals("true", ignoreCase = true) else null }
        val changed = when {
            existing == null -> {
                val values = ContentValues().apply {
                    put("ShelfName", key)
                    put("ContentId", contentId)
                    if ("DateModified" in cols) put("DateModified", now)
                    if ("_IsDeleted" in cols) put("_IsDeleted", "false")
                    if ("_IsSynced" in cols) put("_IsSynced", "false")
                }
                db.insertOrThrow("ShelfContent", null, values)
                true
            }
            existing.second -> {
                val values = ContentValues().apply {
                    put("_IsDeleted", "false")
                    if ("DateModified" in cols) put("DateModified", now)
                    if ("_IsSynced" in cols) put("_IsSynced", "false")
                }
                db.update("ShelfContent", values, "rowid = ?", arrayOf(existing.first.toString()))
                true
            }
            else -> false
        }
        if (changed) touchShelf(key)
        return changed
    }

    /** Takes the book [contentId] out of the collection [name]. Returns true if it was in it. */
    fun removeFromCollection(name: String, contentId: String): Boolean {
        val shelf = findShelf(name.trim()) ?: return false
        val cols = columns("ShelfContent")
        val keys = listOfNotNull(shelf.internalName, shelf.name).filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return false
        val where = "ContentId = ? AND ShelfName IN (${keys.joinToString { "?" }})" +
            if ("_IsDeleted" in cols) " AND (_IsDeleted IS NULL OR _IsDeleted != 'true')" else ""
        val args = arrayOf(contentId, *keys.toTypedArray())
        val n = if ("_IsDeleted" in cols) {
            val values = ContentValues().apply {
                put("_IsDeleted", "true")
                if ("DateModified" in cols) put("DateModified", now)
                if ("_IsSynced" in cols) put("_IsSynced", "false")
            }
            db.update("ShelfContent", values, where, args)
        } else {
            db.delete("ShelfContent", where, args)
        }
        if (n > 0) touchShelf(shelf.key)
        return n > 0
    }

    /**
     * Sets details of the sideloaded book [contentId]: column name to value (null clears it). Columns this
     * Kobo's database doesn't have are skipped. Returns true if the book was found and changed.
     */
    fun updateBook(contentId: String, changes: Map<String, Any?>): Boolean {
        if (!contentId.startsWith("file://")) return false
        val cols = columns("content")
        val values = ContentValues()
        for ((column, value) in changes) {
            if (column !in cols) continue
            when (value) {
                null -> values.putNull(column)
                is String -> values.put(column, value)
                is Double -> values.put(column, value)
                is Int -> values.put(column, value)
                is Long -> values.put(column, value)
                else -> values.put(column, value.toString())
            }
        }
        if (values.size() == 0) return false
        val where = "ContentID = ? AND ContentType = 6" + if ("BookID" in cols) " AND BookID IS NULL" else ""
        return db.update("content", values, where, arrayOf(contentId)) > 0
    }

    /** Whether this Kobo keeps bookmarks, highlights and notes (its Bookmark table). */
    val hasMarks: Boolean get() = "bookmark" in tables

    private class Column(val name: String, val type: String, val notNull: Boolean, val hasDefault: Boolean)

    private val markColumns: List<Column> by lazy {
        db.rawQuery("PRAGMA table_info(\"Bookmark\")", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Column(
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getString(c.getColumnIndexOrThrow("type")).orEmpty().uppercase(),
                        c.getInt(c.getColumnIndexOrThrow("notnull")) != 0,
                        !c.isNull(c.getColumnIndexOrThrow("dflt_value")),
                    ))
                }
            }
        }
    }

    private fun markValues(values: Map<String, Any?>, fillRequired: Boolean): ContentValues {
        val cols = markColumns.associateBy { it.name }
        val out = ContentValues()
        for ((column, value) in values) {
            if (column !in cols) continue
            put(out, column, value)
        }
        if (fillRequired) {
            // A column this Kobo requires that nothing was given for: empty, as the Kobo leaves it.
            for (c in markColumns) {
                if (!c.notNull || c.hasDefault) continue
                if (out.containsKey(c.name) && out.get(c.name) != null) continue
                if (c.type.contains("INT") || c.type.contains("REAL") || c.type.contains("NUM") || c.type.contains("BOOL")) out.put(c.name, 0) else out.put(c.name, "")
            }
        }
        return out
    }

    private fun put(out: ContentValues, column: String, value: Any?) = when (value) {
        null -> out.putNull(column)
        is String -> out.put(column, value)
        is Double -> out.put(column, value)
        is Float -> out.put(column, value.toDouble())
        is Int -> out.put(column, value)
        is Long -> out.put(column, value)
        is Boolean -> out.put(column, if (value) "true" else "false")
        else -> out.put(column, value.toString())
    }

    /** Adds a bookmark, highlight or note to a sideloaded book ([values]: column to value). False if there's nowhere to put it. */
    fun addMark(values: Map<String, Any?>): Boolean {
        if (!hasMarks) return false
        val volume = values["VolumeID"] as? String ?: return false
        if (!volume.startsWith("file://")) return false
        val id = values["BookmarkID"] as? String ?: return false
        db.delete("Bookmark", "BookmarkID = ?", arrayOf(id))
        db.insertOrThrow("Bookmark", null, markValues(values, fillRequired = true))
        return true
    }

    /** Changes a bookmark, highlight or note. */
    fun updateMark(id: String, values: Map<String, Any?>): Boolean {
        if (!hasMarks) return false
        val v = markValues(values, fillRequired = false)
        if (v.size() == 0) return false
        return db.update("Bookmark", v, "BookmarkID = ?", arrayOf(id)) > 0
    }

    /** Deletes a bookmark, highlight or note. */
    fun deleteMark(id: String): Boolean {
        if (!hasMarks) return false
        return db.delete("Bookmark", "BookmarkID = ?", arrayOf(id)) > 0
    }

    companion object {
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

        /** The Kobo's timestamp format, in UTC. */
        fun timestamp(at: Instant = Instant.now()): String = STAMP.format(at)
    }
}
