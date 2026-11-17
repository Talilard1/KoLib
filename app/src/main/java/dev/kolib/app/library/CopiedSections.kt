package dev.kolib.app.library

import dev.kolib.app.epub.ChapterOps
import dev.kolib.app.epub.EpubText
import dev.kolib.app.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sections (chapters, title pages, contents pages…) copied out of books in the text editor, to put into others. Kept
 * in the app's own files, so they stay through restarts and updates until they're deleted. Each is a folder: the
 * page, the files it uses (pictures, style sheets, fonts), and what it is.
 */
class CopiedSections(private val dir: File) {

    /** A copied section as the list of them shows it. */
    data class Item(
        val id: String,
        val title: String,
        /** The book it was copied from. */
        val fromBook: String,
        val copiedAt: Long,
        val words: Int,
        val pictures: Int,
        /** It's a contents page: it can be made to list the chapters of the book it goes into. */
        val isContents: Boolean,
    )

    private val _items = MutableStateFlow<List<Item>>(emptyList())

    /** Newest first. */
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _items.value = dir.listFiles()?.filter { it.isDirectory && !it.name.endsWith(".part") }
            ?.mapNotNull { f -> runCatching { readItem(f) }.getOrNull() }
            ?.sortedByDescending { it.copiedAt }.orEmpty()
    }

    private fun readItem(folder: File): Item? {
        val o = JSONObject(File(folder, SECTION).takeIf { it.isFile }?.readText() ?: return null)
        return Item(
            id = folder.name,
            title = o.optString("title"),
            fromBook = o.optString("book"),
            copiedAt = o.optLong("copied"),
            words = o.optInt("words"),
            pictures = o.optInt("pictures"),
            isContents = o.optBoolean("contents"),
        )
    }

    /** Keeps [page], copied from the book called [fromBook]. */
    @Synchronized
    fun add(page: ChapterOps.CopiedPage, fromBook: String): Item {
        dir.mkdirs()
        val now = System.currentTimeMillis()
        var id = "s$now"
        var n = 2
        while (File(dir, id).exists()) id = "s$now-${n++}"
        val part = File(dir, "$id.part")
        part.deleteRecursively()
        part.mkdirs()
        File(part, PAGE).writeText(page.text)
        page.files.forEachIndexed { i, f -> File(part, "file-$i").writeBytes(f.bytes) }
        val o = JSONObject()
            .put("title", page.title)
            .put("book", fromBook)
            .put("path", page.path)
            .put("copied", now)
            .put("words", runCatching { EpubText.countWords(EpubText.plainText(page.text)) }.getOrDefault(0))
            .put("pictures", page.files.count { it.mediaType.startsWith("image/") })
            .put("contents", page.isContents)
            .put("files", JSONArray().apply { page.files.forEachIndexed { i, f -> put(JSONObject().put("path", f.path).put("type", f.mediaType).put("file", "file-$i")) } })
            .put("levels", JSONObject().apply { page.levels.forEach { (k, v) -> put(k, v) } })
        File(part, SECTION).writeText(o.toString())
        val folder = File(dir, id)
        if (!part.renameTo(folder)) {
            part.deleteRecursively()
            throw java.io.IOException("Couldn't keep the copied section.")
        }
        refresh()
        AppLog.i("Copied", "Copied “${page.title}” from “$fromBook”, with ${page.files.size} file(s).")
        return _items.value.first { it.id == id }
    }

    /** The section kept as [id], ready to put into a book. */
    fun load(id: String): ChapterOps.CopiedPage? = runCatching {
        val folder = File(dir, id)
        val o = JSONObject(File(folder, SECTION).readText())
        val files = o.optJSONArray("files")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i) }.map { f ->
                ChapterOps.CopiedFile(f.getString("path"), f.getString("type"), File(folder, f.getString("file")).readBytes())
            }
        }.orEmpty()
        val levels = o.optJSONObject("levels")?.let { l -> l.keys().asSequence().associateWith { l.getInt(it) } }.orEmpty()
        ChapterOps.CopiedPage(o.optString("title"), o.optString("path"), File(folder, PAGE).readText(), files, levels, o.optBoolean("contents"))
    }.onFailure { AppLog.e("Copied", "The copied section $id couldn't be read", it) }.getOrNull()

    @Synchronized
    fun delete(id: String) {
        if (id.isEmpty() || id.contains('/') || id.contains("..")) return
        File(dir, id).deleteRecursively()
        refresh()
    }

    private companion object {
        const val SECTION = "section.json"
        const val PAGE = "page.xhtml"
    }
}
