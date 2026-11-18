package dev.kolib.app.ai

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The summaries a model has written, kept so one asked for again is there at once: a summary takes a model on
 * a phone a minute or two. The newest few hundred are kept.
 */
class SummaryStore(private val file: File) {
    private val kept = LinkedHashMap<String, String>()
    private var loaded = false

    /**
     * What a summary is filed under: the book, the chapter and how much of it was summarised, the model that
     * wrote it and the language it's in. A chapter that's been edited, or read further, is summarised afresh.
     */
    fun key(title: String, author: String?, chapter: String, length: Int, model: String, language: String): String {
        val text = listOf(title, author.orEmpty(), chapter, length.toString(), model, language).joinToString("\u0001")
        return MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file.readText())
            for (k in o.keys()) kept[k] = o.getString(k)
        }
    }

    @Synchronized
    fun get(key: String): String? {
        load()
        return kept[key]
    }

    @Synchronized
    fun put(key: String, summary: String) {
        load()
        kept.remove(key)
        kept[key] = summary
        while (kept.size > MOST) kept.remove(kept.keys.first())
        runCatching {
            file.parentFile?.mkdirs()
            val o = JSONObject()
            for ((k, v) in kept) o.put(k, v)
            file.writeText(o.toString())
        }
    }

    companion object {
        private const val MOST = 300
    }
}
