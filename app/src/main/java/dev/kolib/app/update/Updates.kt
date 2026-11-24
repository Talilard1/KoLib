package dev.kolib.app.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A version of KoLib on GitHub: its number ("2.1"), what's new in it as its release says, its page there,
 * and the app file for this device (none when the release has no file that suits it).
 */
data class Release(val version: String, val notes: String, val page: String, val apkName: String?, val apkUrl: String?, val apkBytes: Long)

/** Reading GitHub's word on the newest KoLib. */
object Updates {
    const val REPO = "Talilard1/KoLib"
    const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    const val PAGE = "https://github.com/$REPO/releases/latest"
    /** Where problems are reported. */
    const val ISSUES = "https://github.com/$REPO/issues"
    private const val DOWNLOADS = "https://github.com/$REPO/releases/download/"

    /** A version as numbers: "v1.30" is 1, 30; "2.0" is 2, 0. */
    fun numbers(version: String): List<Int> = Regex("\\d+").findAll(version).map { it.value.take(9).toInt() }.toList()

    /**
     * Whether [candidate] is a later version than [installed]: 2.0 is later than 1.30, and 1.30 than 1.4. A version is
     * later than its previews ("3.0" than "3.0 preview"), and a preview than the ones before it ("3.0 preview 2" than "3.0 preview").
     */
    fun isNewer(candidate: String, installed: String): Boolean {
        val (a, aPreview) = parts(candidate)
        val (b, bPreview) = parts(installed)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return when {
            aPreview == null -> bPreview != null
            bPreview == null -> false
            else -> aPreview > bPreview
        }
    }

    /** A version's numbers and, for a preview of it, which preview it is (1 for the first): "3.0 preview 2" is 3, 0 and 2. */
    private fun parts(version: String): Pair<List<Int>, Int?> {
        val at = Regex("preview", RegexOption.IGNORE_CASE).find(version) ?: return numbers(version) to null
        return numbers(version.substring(0, at.range.first)) to (numbers(version.substring(at.range.last + 1)).firstOrNull() ?: 1)
    }

    /**
     * What GitHub says of the latest release ([json]), with the app file for a device that runs [abis]
     * (the kinds of processor code it takes, the best first): the file made for the first of those there
     * is one for, else the one for all devices.
     */
    fun parse(json: String, abis: List<String>): Release? {
        val o = JSONObject(json)
        val version = o.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (numbers(version).isEmpty()) return null
        val assets = o.optJSONArray("assets")
        val files = (0 until (assets?.length() ?: 0)).mapNotNull { i ->
            val a = assets!!.optJSONObject(i) ?: return@mapNotNull null
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            // Only an app file, and only from this project's own releases.
            if (!name.endsWith(".apk", ignoreCase = true) || !url.startsWith(DOWNLOADS)) null else Triple(name, url, a.optLong("size"))
        }
        fun made(forAbi: String) = files.firstOrNull { it.first.endsWith("-$forAbi.apk", ignoreCase = true) || it.first.endsWith("_$forAbi.apk", ignoreCase = true) }
        val file = abis.firstNotNullOfOrNull { made(it) } ?: made("universal") ?: files.singleOrNull()
        return Release(version, o.optString("body").trim(), o.optString("html_url").ifBlank { PAGE }, file?.first, file?.second, file?.third ?: 0L)
    }

    /**
     * [notes] without a first line that only says what they are ("What's new", "Changelog"): where they're
     * shown says that already.
     */
    fun withoutTitle(notes: String): String {
        val lines = notes.trim().lines()
        val first = lines.firstOrNull()?.trim()?.trimEnd(':', '.', '!')?.lowercase()?.replace('’', '\'') ?: return notes
        return if (first in TITLES || first.startsWith("what's new in ")) lines.drop(1).joinToString("\n").trim() else notes.trim()
    }

    private val TITLES = setOf("what's new", "whats new", "changelog", "change log", "changes", "release notes", "new in this version")

    /**
     * A release's notes as plain lines to read: its headings, bullets and emphasis without their marks,
     * and links as the words they're on.
     */
    fun plainNotes(markdown: String): String = markdown.replace("\r\n", "\n").lines().joinToString("\n") { raw ->
        var line = raw.trimEnd()
        line = line.replace(Regex("^\\s{0,3}#{1,6}\\s*"), "")
        line = line.replace(Regex("^(\\s*)[-*+]\\s+"), "$1•  ")
        line = line.replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        line = line.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        line = line.replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
        line = line.replace(Regex("(?<![\\w*])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![\\w*])"), "$1")
        line.replace("`", "")
    }.replace(Regex("\n{3,}"), "\n\n").trim()

    /** Asks GitHub. */
    fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "KoLib")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
