package dev.kobolibrary.app.reader

import dev.kobolibrary.app.log.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A place in a book: the chapter file, the spot in it, and how far through the chapter that is. */
data class ReaderPosition(
    val path: String,
    /** Where in the chapter (element path and character), as reader.js finds it again. */
    val anchor: String?,
    /** How far through the chapter, 0 to 1 (used if the anchor can't be found). */
    val progress: Float,
    /** How far through the whole book, 0 to 1. */
    val bookProgress: Float = 0f,
    val snippet: String = "",
    val chapterTitle: String = "",
    val at: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        putOpt("anchor", anchor)
        put("progress", progress.toDouble())
        put("book", bookProgress.toDouble())
        put("snippet", snippet)
        put("chapter", chapterTitle)
        put("at", at)
    }

    companion object {
        fun from(o: JSONObject?): ReaderPosition? {
            o ?: return null
            val path = o.optString("path").takeIf { it.isNotEmpty() } ?: return null
            return ReaderPosition(
                path = path,
                anchor = if (o.has("anchor") && !o.isNull("anchor")) o.getString("anchor") else null,
                progress = o.optDouble("progress", 0.0).toFloat(),
                bookProgress = o.optDouble("book", 0.0).toFloat(),
                snippet = o.optString("snippet"),
                chapterTitle = o.optString("chapter"),
                at = o.optLong("at"),
            )
        }
    }
}

data class ReaderState(
    val position: ReaderPosition? = null,
    val bookmarks: List<ReaderPosition> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
)

/** Where each book was left, and its bookmarks: kept in the book's folder in the library. */
class ReaderStore(
    private val bookDir: File,
    /** Told after each save, so the library can show how far the book has been read. */
    private val onSaved: ((ReaderState) -> Unit)? = null,
) {
    private val file get() = File(bookDir, "reader.json")

    fun load(): ReaderState = try {
        if (!file.exists()) ReaderState() else {
            val o = JSONObject(file.readText())
            ReaderState(
                position = ReaderPosition.from(o.optJSONObject("position")),
                bookmarks = o.optJSONArray("bookmarks")?.let { a -> (0 until a.length()).mapNotNull { ReaderPosition.from(a.optJSONObject(it)) } }.orEmpty(),
                annotations = Annotation.listFrom(o.optJSONArray("annotations")),
            )
        }
    } catch (e: Exception) {
        AppLog.w("Reader", "The reading position couldn't be read", e)
        ReaderState()
    }

    fun save(state: ReaderState) {
        try {
            bookDir.mkdirs()
            val o = JSONObject().apply {
                state.position?.let { put("position", it.toJson()) }
                put("bookmarks", JSONArray().apply { state.bookmarks.forEach { put(it.toJson()) } })
                put("annotations", JSONArray().apply { state.annotations.forEach { put(it.toJson()) } })
            }
            val tmp = File(bookDir, "reader.json.part")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("Reader", "The reading position couldn't be saved", e)
        }
        onSaved?.invoke(state)
    }

    companion object {
        /** How far through the book it was left, for the library (0 to 1), or null if never opened. */
        fun progressOf(bookDir: File): Float? = readHereOf(bookDir)?.progress

        /** How far through the book it was left and when, or null if it's never been opened (or it's been marked as not started). */
        fun readHereOf(bookDir: File): ReadHere? = try {
            val f = File(bookDir, "reader.json")
            if (!f.exists()) null else {
                val o = JSONObject(f.readText())
                val p = o.optJSONObject("position")
                when {
                    o.optBoolean("finished") -> ReadHere(1f, o.optLong("finishedAt").takeIf { t -> t > 0 } ?: f.lastModified(), finished = true)
                    else -> p?.let { ReadHere(it.optDouble("book", 0.0).toFloat(), it.optLong("at").takeIf { t -> t > 0 } ?: f.lastModified()) }
                }
            }
        } catch (e: Exception) {
            null
        }

        /**
         * Marks the book as finished: it shows as finished, not how far through, and its place is let go
         * so it opens at the beginning. Reading it again saves a place, which shows how far through again.
         * Its bookmarks and highlights are kept.
         */
        fun markFinished(bookDir: File, now: Long = System.currentTimeMillis()): ReadHere {
            rewrite(bookDir) { o ->
                o.remove("position")
                o.put("finished", true)
                o.put("finishedAt", now)
            }
            return ReadHere(1f, now, finished = true)
        }

        /** Marks the book as not started: its place is let go (it opens at the beginning); bookmarks and highlights are kept. */
        fun markUnread(bookDir: File) = rewrite(bookDir) { o ->
            o.remove("position")
            o.remove("finished")
            o.remove("finishedAt")
        }

        private fun rewrite(bookDir: File, change: (JSONObject) -> Unit) {
            val f = File(bookDir, "reader.json")
            val o = if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject()) else JSONObject()
            change(o)
            bookDir.mkdirs()
            val tmp = File(bookDir, "reader.json.part")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }
}
