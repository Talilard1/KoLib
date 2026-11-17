package dev.kolib.app.ocr

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What's been recognised of a book, page by page, in its folder ("ocr/"): so a long book can be stopped and
 * carried on, and its pages compared with the text afterwards. [settings] says how the pages were read;
 * pages read another way (other languages) don't count.
 */
class OcrStore(val dir: File) {

    /** How the pages are read: the recognition data used, and the resolution the pages were drawn at. */
    data class Settings(val languages: List<String>, val dpi: Int, val pages: Int) {
        fun toJson(): JSONObject = JSONObject().put("languages", JSONArray(languages)).put("dpi", dpi).put("pages", pages)

        companion object {
            fun fromJson(o: JSONObject) = Settings(
                o.getJSONArray("languages").let { a -> (0 until a.length()).map { a.getString(it) } },
                o.getInt("dpi"),
                o.getInt("pages"),
            )
        }
    }

    /** Once the text was made into the book: what the book was before and the languages it was read in. */
    data class Applied(val was: String?, val languages: List<String>, val at: Long)

    private val settingsFile get() = File(dir, "settings.json")
    private val appliedFile get() = File(dir, "applied.json")

    private fun pageFile(index: Int) = File(dir, "p%05d.json".format(index + 1))

    val settings: Settings?
        get() = runCatching { Settings.fromJson(JSONObject(settingsFile.readText())) }.getOrNull()

    /** Starts reading with [s]; pages read another way are thrown away. */
    fun begin(s: Settings) {
        val before = settings
        if (before != null && (before.languages != s.languages || before.dpi != s.dpi || before.pages != s.pages)) clearPages()
        dir.mkdirs()
        write(settingsFile, s.toJson().toString())
    }

    fun clearPages() {
        dir.listFiles()?.filter { it.name.startsWith("p") && it.name.endsWith(".json") }?.forEach { it.delete() }
    }

    fun has(index: Int): Boolean = pageFile(index).exists()

    fun page(index: Int): OcrPage? = runCatching { OcrPage.fromJson(JSONObject(pageFile(index).readText())) }.getOrNull()

    fun save(index: Int, page: OcrPage) {
        dir.mkdirs()
        write(pageFile(index), page.toJson().toString())
    }

    /** How many of [total] pages are done. */
    fun done(total: Int): Int = (0 until total).count { has(it) }

    var applied: Applied?
        get() = runCatching {
            val o = JSONObject(appliedFile.readText())
            Applied(
                o.optString("was").takeIf { it.isNotEmpty() },
                o.getJSONArray("languages").let { a -> (0 until a.length()).map { a.getString(it) } },
                o.optLong("at"),
            )
        }.getOrNull()
        set(value) {
            if (value == null) appliedFile.delete()
            else {
                dir.mkdirs()
                write(appliedFile, JSONObject().put("was", value.was ?: "").put("languages", JSONArray(value.languages)).put("at", value.at).toString())
            }
        }

    fun deleteAll() {
        dir.deleteRecursively()
    }

    /** Written whole or not at all. */
    private fun write(f: File, text: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
