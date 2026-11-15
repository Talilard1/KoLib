package dev.kobolibrary.app.ocr

import org.json.JSONArray
import org.json.JSONObject

/** A rectangle on a page, in the pixels of the picture the text was read from. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centreY: Int get() = (top + bottom) / 2
    val area: Long get() = width.toLong().coerceAtLeast(0) * height.toLong().coerceAtLeast(0)

    fun toJson() = JSONArray().put(left).put(top).put(right).put(bottom)

    companion object {
        fun of(a: JSONArray) = Box(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))
    }
}

/** A word as recognised: its text, where it is, and how sure of it the recognition was (0 to 100). */
data class OcrWord(val text: String, val box: Box, val confidence: Int)

/**
 * A line of words. [size] is its height as the recognition measured it (about the size of its type);
 * [kind] says what sort of line it took it for: a line of text, a heading, a caption.
 */
data class OcrLine(val box: Box, val size: Int, val words: List<OcrWord>, val kind: Kind = Kind.TEXT) {
    enum class Kind { TEXT, HEADING, CAPTION, FLOATING }

    val text: String get() = words.joinToString(" ") { it.text }
}

data class OcrParagraph(val box: Box, val lines: List<OcrLine>)

/** An area of the page: a column of text, or a picture. */
data class OcrBlock(val box: Box, val paragraphs: List<OcrParagraph>, val picture: Boolean = false)

/** One page's text, as recognised from a picture [width] by [height] pixels made at [dpi]; [blank] if the page had nothing on it. */
data class OcrPage(val width: Int, val height: Int, val dpi: Int, val blocks: List<OcrBlock>, val blank: Boolean = false) {
    val lines: List<OcrLine> get() = blocks.flatMap { b -> b.paragraphs.flatMap { it.lines } }
    val words: List<OcrWord> get() = lines.flatMap { it.words }
    val letters: Int get() = words.sumOf { w -> w.text.count { it.isLetterOrDigit() } }

    /** How sure the recognition was of the page's words, on average, weighed by their length. */
    val confidence: Int
        get() {
            val ws = words.filter { w -> w.text.any { it.isLetterOrDigit() } }
            val total = ws.sumOf { it.text.length }
            return if (total == 0) 0 else (ws.sumOf { it.confidence * it.text.length } / total)
        }

    fun toJson(): JSONObject = JSONObject()
        .put("w", width).put("h", height).put("dpi", dpi).apply { if (blank) put("blank", true) }
        .put("b", JSONArray().apply {
            for (b in blocks) put(JSONObject().put("x", b.box.toJson()).put("pic", b.picture).put("p", JSONArray().apply {
                for (p in b.paragraphs) put(JSONObject().put("x", p.box.toJson()).put("l", JSONArray().apply {
                    for (l in p.lines) put(JSONObject().put("x", l.box.toJson()).put("s", l.size).put("k", l.kind.name).put("w", JSONArray().apply {
                        for (w in l.words) put(JSONArray().put(w.text).put(w.box.left).put(w.box.top).put(w.box.right).put(w.box.bottom).put(w.confidence))
                    }))
                }))
            }))
        })

    companion object {
        /** A page with nothing on it. */
        fun blank(width: Int, height: Int, dpi: Int) = OcrPage(width, height, dpi, emptyList(), blank = true)

        fun fromJson(o: JSONObject): OcrPage {
            val blocks = o.getJSONArray("b").let { ba ->
                (0 until ba.length()).map { i ->
                    val b = ba.getJSONObject(i)
                    val paras = b.getJSONArray("p").let { pa ->
                        (0 until pa.length()).map { j ->
                            val p = pa.getJSONObject(j)
                            val lines = p.getJSONArray("l").let { la ->
                                (0 until la.length()).map { k ->
                                    val l = la.getJSONObject(k)
                                    val words = l.getJSONArray("w").let { wa ->
                                        (0 until wa.length()).map { m ->
                                            val w = wa.getJSONArray(m)
                                            OcrWord(w.getString(0), Box(w.getInt(1), w.getInt(2), w.getInt(3), w.getInt(4)), w.getInt(5))
                                        }
                                    }
                                    val kind = runCatching { OcrLine.Kind.valueOf(l.optString("k", "TEXT")) }.getOrDefault(OcrLine.Kind.TEXT)
                                    OcrLine(Box.of(l.getJSONArray("x")), l.optInt("s"), words, kind)
                                }
                            }
                            OcrParagraph(Box.of(p.getJSONArray("x")), lines)
                        }
                    }
                    OcrBlock(Box.of(b.getJSONArray("x")), paras, b.optBoolean("pic"))
                }
            }
            return OcrPage(o.getInt("w"), o.getInt("h"), o.optInt("dpi", 300), blocks, o.optBoolean("blank"))
        }
    }
}
