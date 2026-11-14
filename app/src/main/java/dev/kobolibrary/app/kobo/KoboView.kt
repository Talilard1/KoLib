package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.log.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the Kobo screens show: the books, collections and device details read from the Kobo,
 * either over the live connection ([session] set) or as saved the last time it was connected.
 */
class KoboView(
    val info: KoboDeviceInfo,
    val snapshot: KoboSnapshot,
    val covers: KoboCoverLoader,
    /** When the Kobo's database was read. */
    val readAtMillis: Long,
    val databaseBytes: Long,
    val databaseHadWal: Boolean,
    /** The live connection, or null when this is the saved copy. */
    val session: KoboSession?,
) {
    val live: Boolean get() = session != null

    companion object {
        fun of(session: KoboSession, snapshot: KoboSnapshot = session.snapshot) = KoboView(
            info = session.info,
            snapshot = snapshot,
            covers = session.covers,
            readAtMillis = session.connectedAtMillis,
            databaseBytes = session.databaseBytes,
            databaseHadWal = session.databaseHadWal,
            session = session,
        )
    }
}

/** Keeps a copy of what was on the Kobo, so it can be browsed while the Kobo isn't plugged in. */
class LastSeenStore(private val dir: File) {

    data class LastSeen(
        val info: KoboDeviceInfo,
        val snapshot: KoboSnapshot,
        val readAtMillis: Long,
        val databaseBytes: Long,
        val databaseHadWal: Boolean,
    )

    private val file = File(dir, "last-seen.json")
    val coverDir: File get() = File(dir, "covers")

    fun save(seen: LastSeen) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "last-seen.json.part")
            tmp.writeText(toJson(seen).toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("Kobo", "Couldn't save what's on the Kobo for later", e)
        }
    }

    fun load(): LastSeen? {
        if (!file.exists()) return null
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            AppLog.w("Kobo", "The saved copy of what's on the Kobo couldn't be read", e)
            null
        }
    }

    fun clear() {
        file.delete()
        coverDir.deleteRecursively()
    }

    private fun toJson(s: LastSeen) = JSONObject().apply {
        put("version", 1)
        put("readAt", s.readAtMillis)
        put("databaseBytes", s.databaseBytes)
        put("databaseHadWal", s.databaseHadWal)
        put("info", JSONObject().apply {
            put("modelName", s.info.modelName)
            putOpt("usbProductId", s.info.usbProductId)
            s.info.version?.let { v ->
                put("version", JSONObject().put("serial", v.serial).put("firmware", v.firmware).put("modelId", v.modelId))
            }
            putOpt("volumeUuid", s.info.volumeUuid)
            putOpt("volumeDescription", s.info.volumeDescription)
            putOpt("totalBytes", s.info.totalBytes)
            putOpt("freeBytes", s.info.freeBytes)
            put("directFileAccess", s.info.directFileAccess)
        })
        put("dbVersion", s.snapshot.dbVersion)
        put("books", JSONArray().apply { s.snapshot.allBooks.forEach { put(bookJson(it)) } })
        put("collections", JSONArray().apply {
            s.snapshot.collections.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    putOpt("internalName", c.internalName)
                    putOpt("id", c.id)
                    putOpt("type", c.type)
                    put("visible", c.isVisible)
                    put("synced", c.isSynced)
                    putOpt("created", c.createdMillis)
                    putOpt("modified", c.modifiedMillis)
                    put("members", JSONArray(c.memberIds))
                })
            }
        })
        put("collectionsByBook", JSONObject().apply {
            s.snapshot.collectionsByBook.forEach { (id, names) -> put(id, JSONArray(names)) }
        })
        put("warnings", JSONArray(s.snapshot.warnings))
    }

    private fun bookJson(b: KoboBook) = JSONObject().apply {
        put("contentId", b.contentId)
        put("title", b.title)
        putOpt("author", b.author)
        putOpt("subtitle", b.subtitle)
        putOpt("series", b.series)
        putOpt("seriesNumber", b.seriesNumber)
        putOpt("description", b.description)
        putOpt("publisher", b.publisher)
        putOpt("language", b.language)
        putOpt("isbn", b.isbn)
        putOpt("mimeType", b.mimeType)
        put("format", b.format)
        put("imageId", b.imageId)
        put("kind", b.kind.name)
        put("downloaded", b.isDownloaded)
        put("expired", b.isExpired)
        put("status", b.readStatus.name)
        put("percent", b.percentRead)
        putOpt("lastRead", b.lastReadMillis)
        putOpt("added", b.addedMillis)
        putOpt("size", b.fileSize)
        putOpt("path", b.relativePath)
        putOpt("published", b.published)
        putOpt("seriesId", b.seriesId)
        putOpt("rating", b.rating)
        putOpt("words", b.words)
    }

    private fun fromJson(o: JSONObject): LastSeen {
        val i = o.getJSONObject("info")
        val info = KoboDeviceInfo(
            modelName = i.getString("modelName"),
            usbProductId = i.optIntOrNull("usbProductId"),
            version = i.optJSONObject("version")?.let { KoboVersionInfo(it.getString("serial"), it.getString("firmware"), it.getString("modelId")) },
            volumeUuid = i.optStringOrNull("volumeUuid"),
            volumeDescription = i.optStringOrNull("volumeDescription"),
            totalBytes = i.optLongOrNull("totalBytes"),
            freeBytes = i.optLongOrNull("freeBytes"),
            directFileAccess = i.optBoolean("directFileAccess"),
        )
        val books = o.getJSONArray("books").let { a -> List(a.length()) { book(a.getJSONObject(it)) } }
        val collections = o.getJSONArray("collections").let { a ->
            List(a.length()) { idx ->
                val c = a.getJSONObject(idx)
                KoboCollection(
                    name = c.getString("name"),
                    internalName = c.optStringOrNull("internalName"),
                    id = c.optStringOrNull("id"),
                    type = c.optStringOrNull("type"),
                    isVisible = c.optBoolean("visible", true),
                    isSynced = c.optBoolean("synced"),
                    createdMillis = c.optLongOrNull("created"),
                    modifiedMillis = c.optLongOrNull("modified"),
                    memberIds = c.getJSONArray("members").strings(),
                )
            }
        }
        val byBook = o.getJSONObject("collectionsByBook").let { m -> m.keys().asSequence().associateWith { m.getJSONArray(it).strings() } }
        return LastSeen(
            info = info,
            snapshot = KoboSnapshot(o.getInt("dbVersion"), books, collections, byBook, o.optJSONArray("warnings")?.strings().orEmpty()),
            readAtMillis = o.getLong("readAt"),
            databaseBytes = o.optLong("databaseBytes"),
            databaseHadWal = o.optBoolean("databaseHadWal"),
        )
    }

    private fun book(o: JSONObject) = KoboBook(
        contentId = o.getString("contentId"),
        title = o.getString("title"),
        author = o.optStringOrNull("author"),
        subtitle = o.optStringOrNull("subtitle"),
        series = o.optStringOrNull("series"),
        seriesNumber = o.optStringOrNull("seriesNumber"),
        description = o.optStringOrNull("description"),
        publisher = o.optStringOrNull("publisher"),
        language = o.optStringOrNull("language"),
        isbn = o.optStringOrNull("isbn"),
        mimeType = o.optStringOrNull("mimeType"),
        format = o.getString("format"),
        imageId = o.getString("imageId"),
        kind = runCatching { BookKind.valueOf(o.getString("kind")) }.getOrDefault(BookKind.OTHER),
        isDownloaded = o.optBoolean("downloaded", true),
        isExpired = o.optBoolean("expired"),
        readStatus = runCatching { ReadStatus.valueOf(o.getString("status")) }.getOrDefault(ReadStatus.UNREAD),
        percentRead = o.optInt("percent"),
        lastReadMillis = o.optLongOrNull("lastRead"),
        addedMillis = o.optLongOrNull("added"),
        fileSize = o.optLongOrNull("size"),
        relativePath = o.optStringOrNull("path"),
        published = o.optStringOrNull("published"),
        seriesId = o.optStringOrNull("seriesId"),
        rating = o.optIntOrNull("rating"),
        words = o.optIntOrNull("words"),
    )

    private fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optIntOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
}
