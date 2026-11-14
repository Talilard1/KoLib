package dev.kobolibrary.app.reader

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class HighlightColor(val js: String, val argb: Long, val label: String) {
    YELLOW("yellow", 0xFFFFD400, "Yellow"),
    GREEN("green", 0xFF64C85A, "Green"),
    BLUE("blue", 0xFF5AA0FF, "Blue"),
    PINK("pink", 0xFFFF6EAA, "Pink"),
}

enum class HighlightStyle(val js: String) { HIGHLIGHT("highlight"), UNDERLINE("underline") }

/**
 * A highlighted passage, with or without a note. Where it is: the chapter file, and the start and
 * end as reader.js finds them again (element path and character). The words themselves, and which
 * time they occur in the chapter, find it again if the chapter's been edited since.
 */
data class Annotation(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val start: String,
    val end: String,
    val text: String,
    val occurrence: Int = 0,
    val color: HighlightColor = HighlightColor.YELLOW,
    val style: HighlightStyle = HighlightStyle.HIGHLIGHT,
    val note: String = "",
    val chapterTitle: String = "",
    /** How far through the book, 0 to 1, when it was made: for the list's order across chapters. */
    val bookProgress: Float = 0f,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = created,
) {
    val hasNote: Boolean get() = note.isNotBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("path", path)
        put("start", start)
        put("end", end)
        put("text", text)
        put("occurrence", occurrence)
        put("color", color.js)
        put("style", style.js)
        put("note", note)
        put("chapter", chapterTitle)
        put("book", bookProgress.toDouble())
        put("created", created)
        put("modified", modified)
    }

    /** What reader.js needs to show it. */
    fun toJs(): JSONObject = JSONObject().apply {
        put("id", id)
        put("start", start)
        put("end", end)
        put("text", text)
        put("occurrence", occurrence)
        put("color", color.js)
        put("style", style.js)
        put("note", hasNote)
    }

    companion object {
        fun from(o: JSONObject?): Annotation? {
            o ?: return null
            val path = o.optString("path").takeIf { it.isNotEmpty() } ?: return null
            val text = o.optString("text")
            if (text.isEmpty()) return null
            val created = o.optLong("created").takeIf { it > 0 } ?: System.currentTimeMillis()
            return Annotation(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                path = path,
                start = o.optString("start"),
                end = o.optString("end"),
                text = text,
                occurrence = o.optInt("occurrence", 0),
                color = HighlightColor.entries.firstOrNull { it.js == o.optString("color") } ?: HighlightColor.YELLOW,
                style = HighlightStyle.entries.firstOrNull { it.js == o.optString("style") } ?: HighlightStyle.HIGHLIGHT,
                note = o.optString("note"),
                chapterTitle = o.optString("chapter"),
                bookProgress = o.optDouble("book", 0.0).toFloat(),
                created = created,
                modified = o.optLong("modified").takeIf { it > 0 } ?: created,
            )
        }

        fun listFrom(a: JSONArray?): List<Annotation> = a?.let { arr -> (0 until arr.length()).mapNotNull { from(arr.optJSONObject(it)) } }.orEmpty()

        /** In reading order: by chapter, then where in the chapter. */
        fun ordered(list: List<Annotation>, chapterOrder: (String) -> Int): List<Annotation> =
            list.sortedWith(compareBy<Annotation>({ chapterOrder(it.path) }, { AnchorOrder.key(it.start) }, { it.created }))

        /**
         * Highlights and notes as plain text, to share or keep: grouped by chapter, each passage quoted
         * with its note under it.
         */
        fun export(title: String, author: String?, list: List<Annotation>, chapterOrder: (String) -> Int, date: String): String = buildString {
            append(title)
            if (!author.isNullOrBlank()) append(" — ").append(author)
            append("\nHighlights and notes, ").append(date).append('\n')
            var chapter: String? = null
            for (a in ordered(list, chapterOrder)) {
                val c = a.chapterTitle.ifBlank { "Untitled chapter" }
                if (c != chapter) {
                    append("\n## ").append(c).append('\n')
                    chapter = c
                }
                append('\n')
                a.text.replace(Regex("\\s+"), " ").trim().let { append("> ").append(it).append('\n') }
                if (a.hasNote) append("Note: ").append(a.note.trim()).append('\n')
            }
        }
    }
}

/** Orders anchors ("3/1/0:42") by where they are in the chapter. */
object AnchorOrder {
    fun key(anchor: String): String {
        val path = anchor.substringBefore(':')
        val offset = anchor.substringAfter(':', "0").toIntOrNull() ?: 0
        val parts = path.split('/').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        return parts.joinToString("/") { "%06d".format(it) } + ":" + "%08d".format(offset)
    }
}
