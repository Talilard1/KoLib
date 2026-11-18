package dev.kolib.app.kobo

import dev.kolib.app.log.AppLog
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

/** A Kobo the app has been connected to: what its copy is kept under, what it's called and what was on it. */
data class KnownKobo(
    val key: String,
    val name: String,
    /** The end of its serial number, to tell two of the same model apart. */
    val serialEnd: String,
    val readAtMillis: Long,
    val books: Int,
    val collections: Int,
)

/**
 * Keeps a copy of what was on each Kobo the app has been connected to, so it can be browsed while the Kobo
 * isn't plugged in. Someone with two Kobos has two copies, each with its own books, collections and covers;
 * the one [selected] is the one shown.
 */
class LastSeenStore(private val dir: File) {

    data class LastSeen(
        val info: KoboDeviceInfo,
        val snapshot: KoboSnapshot,
        val readAtMillis: Long,
        val databaseBytes: Long,
        val databaseHadWal: Boolean,
    )

    private val devices = File(dir, "devices")
    private val chosen = File(dir, "selected")
    private var movedOld = false

    private fun folder(key: String) = File(devices, key)
    private fun file(key: String) = File(folder(key), "last-seen.json")
    private fun about(key: String) = File(folder(key), "about.json")

    /** Where [key]'s covers are kept. */
    fun coverDir(key: String): File = File(folder(key), "covers")

    /** Where the covers of the Kobo shown are kept. */
    val coverDir: File get() = selected?.let { coverDir(it) } ?: File(dir, "covers")

    /** The Kobo shown while none is connected: the one chosen, or else the one seen last. */
    var selected: String?
        @Synchronized get() {
            moveOld()
            val named = runCatching { chosen.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() && folder(it).isDirectory }
            return named ?: known().firstOrNull()?.key
        }
        @Synchronized set(value) {
            runCatching {
                dir.mkdirs()
                if (value == null) chosen.delete() else chosen.writeText(value)
            }
        }

    /** The copy from before each Kobo had its own (one file for whichever Kobo was last connected): now that Kobo's. */
    @Synchronized
    private fun moveOld() {
        if (movedOld) return
        movedOld = true
        val old = File(dir, "last-seen.json")
        if (!old.isFile) return
        try {
            val seen = fromJson(JSONObject(old.readText()))
            val key = keyOf(seen.info)
            folder(key).mkdirs()
            if (!file(key).exists()) {
                old.renameTo(file(key))
                File(dir, "covers").takeIf { it.isDirectory }?.renameTo(coverDir(key))
                about(key).writeText(aboutJson(seen).toString())
            }
        } catch (e: Exception) {
            AppLog.w("Kobo", "The saved copy of what's on the Kobo couldn't be read", e)
        }
        old.delete()
        File(dir, "covers").deleteRecursively()
    }

    @Synchronized
    fun save(seen: LastSeen) {
        moveOld()
        val key = keyOf(seen.info)
        try {
            folder(key).mkdirs()
            val tmp = File(folder(key), "last-seen.json.part")
            tmp.writeText(toJson(seen).toString())
            if (!tmp.renameTo(file(key))) {
                file(key).delete()
                tmp.renameTo(file(key))
            }
            about(key).writeText(aboutJson(seen).toString())
            selected = key
        } catch (e: Exception) {
            AppLog.w("Kobo", "Couldn't save what's on the Kobo for later", e)
        }
    }

    /** The copy kept for [key]; for the Kobo shown, without one. */
    @Synchronized
    fun load(key: String? = null): LastSeen? {
        moveOld()
        val file = file(key ?: selected ?: return null)
        if (!file.exists()) return null
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            AppLog.w("Kobo", "The saved copy of what's on the Kobo couldn't be read", e)
            null
        }
    }

    /** Every Kobo there's a copy of, the one seen last first. */
    @Synchronized
    fun known(): List<KnownKobo> {
        moveOld()
        return (devices.listFiles { f -> f.isDirectory } ?: emptyArray()).mapNotNull { f ->
            runCatching {
                val o = JSONObject(about(f.name).readText())
                KnownKobo(f.name, o.getString("name"), o.optString("serialEnd"), o.optLong("readAt"), o.optInt("books"), o.optInt("collections"))
            }.getOrNull()
        }.sortedByDescending { it.readAtMillis }
    }

    /** Forgets [key]'s copy (the Kobo itself isn't touched). */
    @Synchronized
    fun forget(key: String) {
        moveOld()
        folder(key).deleteRecursively()
        if (runCatching { chosen.readText().trim() }.getOrNull() == key) chosen.delete()
    }

    /** Forgets the copy of the Kobo shown. */
    fun clear() {
        selected?.let { forget(it) }
    }

    private fun aboutJson(s: LastSeen) = JSONObject()
        .put("name", s.info.displayName)
        .put("serialEnd", s.info.version?.serial?.takeLast(4).orEmpty())
        .put("readAt", s.readAtMillis)
        .put("books", s.snapshot.books.size)
        .put("collections", s.snapshot.collections.size)

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
        putOpt("bookmark", b.chapterBookmark)
        putOpt("adobe", b.adobeLocation)
        putOpt("chapterProgress", b.chapterProgress)
        putOpt("secondsRead", b.secondsRead)
        putOpt("secondsLeft", b.secondsLeft)
        if (b.chapterSpans.isNotEmpty()) put("spans", JSONArray().apply {
            for (s in b.chapterSpans) put(JSONArray().put(s.id).put(s.title ?: JSONObject.NULL).put(s.offset).put(s.size))
        })
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
        chapterBookmark = o.optStringOrNull("bookmark"),
        adobeLocation = o.optStringOrNull("adobe"),
        chapterProgress = if (o.has("chapterProgress") && !o.isNull("chapterProgress")) o.getDouble("chapterProgress") else null,
        secondsRead = o.optIntOrNull("secondsRead"),
        secondsLeft = o.optIntOrNull("secondsLeft"),
        chapterSpans = o.optJSONArray("spans")?.let { a ->
            List(a.length()) { i -> a.getJSONArray(i) }.map { s ->
                KoboChapterSpan(s.getString(0), if (s.isNull(1)) null else s.getString(1), s.getDouble(2), s.getDouble(3))
            }
        }.orEmpty(),
    )

    private fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optIntOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    companion object {
        /** What a Kobo's copy is kept under: its serial number (or, failing that, its drive's number). */
        fun keyOf(info: KoboDeviceInfo): String =
            (info.version?.serial?.takeIf { it.isNotBlank() } ?: info.volumeUuid ?: "kobo").replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "kobo" }
    }
}
