package dev.kolib.app.reader

import dev.kolib.app.log.AppLog
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

/**
 * Where a book added as a PDF was left in the PDF reader: its [page] (from 0) of [pages], and whether it's
 * read [asText] (the EPUB made from it) instead.
 */
data class PdfPlace(val page: Int, val pages: Int, val at: Long = System.currentTimeMillis(), val asText: Boolean = false) {
    /** How far through: the pages before this one, and the whole book at the last page. */
    val readHere: ReadHere get() = ReadHere(if (page >= pages - 1) 1f else page.toFloat() / pages, at)
}

data class ReaderState(
    val position: ReaderPosition? = null,
    val bookmarks: List<ReaderPosition> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
)

/** The files a book's own look is kept in, for the device held upright and sideways. */
const val LOOK_PORTRAIT = "look-portrait.json"
const val LOOK_LANDSCAPE = "look-landscape.json"
private const val CAMERA_PLACED = "cameraPlaced"

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

    /**
     * A book's own look is kept for each way the device is held: changing one leaves the other. From
     * before that, a book had one look for both ("look.json"), which becomes both the first time it's read.
     */
    private fun lookFile(landscape: Boolean) = File(bookDir, if (landscape) LOOK_LANDSCAPE else LOOK_PORTRAIT)

    private fun splitOldLook() {
        val old = File(bookDir, "look.json")
        if (!old.exists()) return
        try {
            for (file in listOf(lookFile(false), lookFile(true))) if (!file.exists()) old.copyTo(file)
            old.delete()
        } catch (e: Exception) {
            AppLog.w("Reader", "The book's own settings couldn't be kept for each way the device is held", e)
        }
    }

    private fun lookJson(landscape: Boolean): JSONObject? = try {
        splitOldLook()
        lookFile(landscape).takeIf { it.exists() }?.let { JSONObject(it.readText()) }
    } catch (e: Exception) {
        AppLog.w("Reader", "The book's own settings couldn't be read", e)
        null
    }

    /**
     * The book's own look with the device held one way, over [base] (see [ReaderSettings.withLookOf]);
     * null if it follows the usual settings that way.
     */
    fun look(base: ReaderSettings, landscape: Boolean = false): ReaderSettings? = lookJson(landscape)?.let { ReaderSettings.withLook(base, it) }

    /**
     * Whether the book's own look that way has been seen to on a screen with a camera in its top (see
     * [ReaderSettingsStore.placeAroundCamera]): true once it's been kept by a version that does that.
     */
    fun lookPlacedForCamera(landscape: Boolean): Boolean = lookJson(landscape)?.optBoolean(CAMERA_PLACED) ?: true

    /** Keeps [s]'s look as the book's own, with the device held one way. */
    fun saveLook(s: ReaderSettings, landscape: Boolean = false) {
        try {
            splitOldLook()
            bookDir.mkdirs()
            val file = lookFile(landscape)
            val tmp = File(bookDir, file.name + ".part")
            tmp.writeText(s.lookJson().put(CAMERA_PLACED, true).toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("Reader", "The book's own settings couldn't be saved", e)
        }
    }

    /** The book goes back to the usual settings, with the device held one way. */
    fun forgetLook(landscape: Boolean = false) {
        splitOldLook()
        lookFile(landscape).delete()
    }

    private val picturesFile get() = File(bookDir, "pictures.json")

    /** Pictures turned in the book (printed on their side to fit a page): each one's path, and its quarter turns clockwise. */
    fun turns(): Map<String, Int> = try {
        if (!picturesFile.exists()) emptyMap() else {
            val o = JSONObject(picturesFile.readText()).optJSONObject("turns") ?: JSONObject()
            o.keys().asSequence().associateWith { o.optInt(it) }.filterValues { Math.floorMod(it, 4) != 0 }
        }
    } catch (e: Exception) {
        AppLog.w("Reader", "The book's turned pictures couldn't be read", e)
        emptyMap()
    }

    /** Pictures already looked at to see whether they're on their side (so they aren't again, and a reader's own turn stands). */
    fun checkedPictures(): Set<String> = try {
        if (!picturesFile.exists()) emptySet() else {
            val a = JSONObject(picturesFile.readText()).optJSONArray("checked") ?: JSONArray()
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet()
        }
    } catch (e: Exception) {
        emptySet()
    }

    fun saveTurns(turns: Map<String, Int>, checked: Set<String> = checkedPictures()) {
        try {
            bookDir.mkdirs()
            if (turns.isEmpty() && checked.isEmpty()) {
                picturesFile.delete()
                return
            }
            val tmp = File(bookDir, "pictures.json.part")
            tmp.writeText(JSONObject().put("turns", JSONObject(turns)).put("checked", JSONArray(checked.sorted())).toString())
            if (!tmp.renameTo(picturesFile)) {
                picturesFile.delete()
                tmp.renameTo(picturesFile)
            }
        } catch (e: Exception) {
            AppLog.w("Reader", "The book's turned pictures couldn't be saved", e)
        }
    }

    private val pagesFile get() = File(bookDir, "pages.json")

    /** Each chapter's pages as counted before at [key] (the layout and the book's text), if they were. */
    fun pageCounts(key: String): IntArray? = try {
        if (!pagesFile.exists()) null else {
            JSONObject(pagesFile.readText()).optJSONObject("counts")?.optJSONArray(key)?.let { a -> IntArray(a.length()) { a.optInt(it) } }
        }
    } catch (e: Exception) {
        null
    }

    /** Keeps [counts] for [key], with the last few other layouts' (a phone's and a tablet's, a few text sizes). */
    fun savePageCounts(key: String, counts: IntArray) {
        try {
            bookDir.mkdirs()
            val old = runCatching { JSONObject(pagesFile.readText()) }.getOrNull()
            val before = old?.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val keep = (listOf(key) + before.filter { it != key }).take(8)
            val all = JSONObject()
            for (k in keep) {
                if (k == key) all.put(k, JSONArray().apply { counts.forEach { put(it) } })
                else old?.optJSONObject("counts")?.optJSONArray(k)?.let { all.put(k, it) }
            }
            val tmp = File(bookDir, "pages.json.part")
            tmp.writeText(JSONObject().put("order", JSONArray(keep)).put("counts", all).toString())
            if (!tmp.renameTo(pagesFile)) {
                pagesFile.delete()
                tmp.renameTo(pagesFile)
            }
        } catch (e: Exception) {
            AppLog.w("Reader", "The page counts couldn't be saved", e)
        }
    }

    companion object {
        /** How far through the book it was left, for the library (0 to 1), or null if never opened. */
        fun progressOf(bookDir: File): Float? = readHereOf(bookDir)?.progress

        /**
         * How far through the book it was left and when, or null if it's never been opened (or it's been
         * marked as not started). A book added as a PDF has a place in the PDF too: the later one counts.
         */
        fun readHereOf(bookDir: File): ReadHere? {
            val text = try {
                val f = File(bookDir, "reader.json")
                if (!f.exists()) null else {
                    val o = JSONObject(f.readText())
                    val p = o.optJSONObject("position")
                    when {
                        o.optBoolean("finished") -> return ReadHere(1f, o.optLong("finishedAt").takeIf { t -> t > 0 } ?: f.lastModified(), finished = true)
                        else -> p?.let { ReadHere(it.optDouble("book", 0.0).toFloat(), it.optLong("at").takeIf { t -> t > 0 } ?: f.lastModified()) }
                    }
                }
            } catch (e: Exception) {
                null
            }
            val pdf = pdfPlace(bookDir)?.takeIf { it.pages > 0 }?.readHere
            return listOfNotNull(text, pdf).maxByOrNull { it.at }
        }

        private fun pdfFile(bookDir: File) = File(bookDir, "pdf.json")

        /** Where the book was left in the PDF reader, if it's been read there. */
        fun pdfPlace(bookDir: File): PdfPlace? = try {
            val f = pdfFile(bookDir)
            if (!f.exists()) null else JSONObject(f.readText()).let { o ->
                PdfPlace(o.optInt("page"), o.optInt("pages"), o.optLong("at"), o.optBoolean("asText"))
            }
        } catch (e: Exception) {
            null
        }

        /** Keeps [place]; a book that was marked as finished is being read again. */
        fun savePdfPlace(bookDir: File, place: PdfPlace) {
            try {
                bookDir.mkdirs()
                val tmp = File(bookDir, "pdf.json.part")
                tmp.writeText(
                    JSONObject().put("page", place.page).put("pages", place.pages).put("at", place.at).put("asText", place.asText).toString()
                )
                if (!tmp.renameTo(pdfFile(bookDir))) {
                    pdfFile(bookDir).delete()
                    tmp.renameTo(pdfFile(bookDir))
                }
                if (!place.asText && File(bookDir, "reader.json").exists()) rewrite(bookDir) { o ->
                    o.remove("finished")
                    o.remove("finishedAt")
                }
            } catch (e: Exception) {
                AppLog.w("Reader", "The place in the PDF couldn't be saved", e)
            }
        }

        /**
         * Marks the book as finished: it shows as finished, not how far through, and its place is let go
         * so it opens at the beginning. Reading it again saves a place, which shows how far through again.
         * Its bookmarks and highlights are kept.
         */
        fun markFinished(bookDir: File, now: Long = System.currentTimeMillis()): ReadHere {
            forgetPdfPage(bookDir)
            rewrite(bookDir) { o ->
                o.remove("position")
                o.put("finished", true)
                o.put("finishedAt", now)
            }
            return ReadHere(1f, now, finished = true)
        }

        /**
         * Moves the book's place to [position] (where the Kobo is): it shows as read that far, and opens
         * there. Its bookmarks and highlights are kept; marking it as finished is undone.
         */
        fun movePosition(bookDir: File, position: ReaderPosition) = rewrite(bookDir) { o ->
            o.put("position", position.toJson())
            o.remove("finished")
            o.remove("finishedAt")
        }

        /** Marks the book as not started: its place is let go (it opens at the beginning); bookmarks and highlights are kept. */
        fun markUnread(bookDir: File) = rewrite(bookDir.also { forgetPdfPage(it) }) { o ->
            o.remove("position")
            o.remove("finished")
            o.remove("finishedAt")
        }

        /** The PDF opens at its start again; whether it's read as text is kept. */
        private fun forgetPdfPage(bookDir: File) {
            val place = pdfPlace(bookDir) ?: return
            if (place.asText) savePdfPlace(bookDir, place.copy(page = 0, pages = 0)) else pdfFile(bookDir).delete()
        }

        /** Writes a place in the PDF brought from another device as it is (a book finished later stays finished). */
        internal fun writePdfPlace(bookDir: File, place: JSONObject) {
            runCatching {
                bookDir.mkdirs()
                val tmp = File(bookDir, "pdf.json.part")
                tmp.writeText(place.toString())
                if (!tmp.renameTo(pdfFile(bookDir))) {
                    pdfFile(bookDir).delete()
                    tmp.renameTo(pdfFile(bookDir))
                }
            }.onFailure { AppLog.w("Reader", "The place in the PDF couldn't be saved", it) }
        }

        internal fun rewrite(bookDir: File, change: (JSONObject) -> Unit) {
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
