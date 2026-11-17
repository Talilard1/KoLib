package dev.kolib.app.link

import dev.kolib.app.reader.ReaderStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where a book was left reading, its bookmarks and its highlights, as they pass between devices and are put together
 * when both have the book.
 *
 * A book's place has two parts, each kept with the time it was left: the place in the text (or that the book was
 * marked as finished, which lets that place go), and the page in its PDF or comic. Putting two devices' together takes
 * the later of each part. Bookmarks and highlights from both are kept (a highlight changed on both, as it was changed
 * last).
 */
internal object ReadingData {
    private const val READER = "reader.json"
    private const val PDF = "pdf.json"

    /** Everything kept about reading the book in [dir]: `{"reader": reader.json, "pdf": pdf.json}`, either left out if there's none. */
    fun readingOf(dir: File): JSONObject {
        val o = JSONObject()
        read(File(dir, READER))?.let { r ->
            // A place saved before places had times: when the file was last written.
            r.optJSONObject("position")?.let { p -> if (p.optLong("at") <= 0) p.put("at", File(dir, READER).lastModified()) }
            if (r.optBoolean("finished") && r.optLong("finishedAt") <= 0) r.put("finishedAt", File(dir, READER).lastModified())
            o.put("reader", r)
        }
        read(File(dir, PDF))?.let { o.put("pdf", it) }
        return o
    }

    /** Just where the book was left, from what [readingOf] gives: `{"text": position}` or `{"finished": when}`, and `{"pdf": place}`. */
    fun placeOf(reading: JSONObject): JSONObject {
        val o = JSONObject()
        reading.optJSONObject("reader")?.let { r ->
            when {
                r.optBoolean("finished") -> o.put("finished", r.optLong("finishedAt"))
                r.optJSONObject("position") != null -> o.put("text", r.getJSONObject("position"))
            }
        }
        reading.optJSONObject("pdf")?.let { o.put("pdf", it) }
        return o
    }

    fun placeOf(dir: File): JSONObject = placeOf(readingOf(dir))

    /** The later of each part of two places. Which is taken doesn't depend on which is [a]: both devices come to the same. */
    fun mergePlace(a: JSONObject, b: JSONObject): JSONObject {
        val out = JSONObject()
        val ta = textAt(a)
        val tb = textAt(b)
        if (ta != null || tb != null) {
            val fromA = when {
                ta == null -> false
                tb == null -> true
                ta != tb -> ta > tb
                else -> textKey(a) >= textKey(b)
            }
            val src = if (fromA) a else b
            src.optJSONObject("text")?.let { out.put("text", it) } ?: out.put("finished", src.optLong("finished"))
        }
        val pa = a.optJSONObject("pdf")
        val pb = b.optJSONObject("pdf")
        val pdf = when {
            pa == null -> pb
            pb == null -> pa
            pa.optLong("at") != pb.optLong("at") -> if (pa.optLong("at") > pb.optLong("at")) pa else pb
            else -> if (pdfKey(pa) >= pdfKey(pb)) pa else pb
        }
        pdf?.let { out.put("pdf", it) }
        return out
    }

    /** Whether two places are the same. */
    fun samePlace(a: JSONObject, b: JSONObject): Boolean =
        textAt(a) == textAt(b) && textKey(a) == textKey(b) &&
            a.optJSONObject("pdf")?.let(::pdfKey) == b.optJSONObject("pdf")?.let(::pdfKey)

    /** The place a book was left at: in the text, or finished, and when (null if neither). */
    fun textAt(place: JSONObject): Long? =
        place.optJSONObject("text")?.optLong("at") ?: if (place.has("finished")) place.optLong("finished") else null

    /** When the book was last left, in any part of its place (0 if it never was). */
    fun latest(place: JSONObject): Long = maxOf(textAt(place) ?: 0L, place.optJSONObject("pdf")?.optLong("at") ?: 0L)

    private fun textKey(place: JSONObject): String =
        place.optJSONObject("text")?.let { "t|" + it.optString("path") + "|" + it.optString("anchor") + "|" + it.optDouble("progress", 0.0) } ?: if (place.has("finished")) "f" else ""

    private fun pdfKey(p: JSONObject): String = "${p.optLong("at")}|${p.optInt("page")}|${p.optInt("pages")}|${p.optBoolean("asText")}"

    /** Puts [place] in [dir]: the text place (or finished) and the PDF page, keeping the bookmarks and highlights. */
    fun applyPlace(dir: File, place: JSONObject) {
        val text = place.optJSONObject("text")
        val finished = if (place.has("finished")) place.optLong("finished") else null
        if (text != null || finished != null) ReaderStore.rewrite(dir) { o ->
            if (text != null) {
                o.put("position", text)
                o.remove("finished")
                o.remove("finishedAt")
            } else {
                o.remove("position")
                o.put("finished", true)
                o.put("finishedAt", finished)
            }
        }
        place.optJSONObject("pdf")?.let { ReaderStore.writePdfPlace(dir, it) }
    }

    /**
     * Puts reading data from another device ([incoming], as [readingOf] gives it) together with what's kept in [dir]:
     * the later place, and the bookmarks and highlights of both.
     */
    fun mergeInto(dir: File, incoming: JSONObject) {
        val mine = readingOf(dir)
        val place = mergePlace(placeOf(mine), placeOf(incoming))
        val r = mine.optJSONObject("reader")
        val t = incoming.optJSONObject("reader")
        if (r != null || t != null) {
            val o = r ?: JSONObject()
            // Bookmarks: the same place once.
            val marks = LinkedHashMap<String, JSONObject>()
            fun addMarks(a: JSONArray?) {
                for (i in 0 until (a?.length() ?: 0)) {
                    val m = a!!.optJSONObject(i) ?: continue
                    marks.putIfAbsent(m.optString("path") + "|" + m.optString("anchor"), m)
                }
            }
            addMarks(r?.optJSONArray("bookmarks"))
            addMarks(t?.optJSONArray("bookmarks"))
            // Highlights: each once, as last changed.
            val notes = LinkedHashMap<String, JSONObject>()
            fun addNotes(a: JSONArray?) {
                for (i in 0 until (a?.length() ?: 0)) {
                    val n = a!!.optJSONObject(i) ?: continue
                    val id = n.optString("id")
                    if (id.isEmpty()) continue
                    val had = notes[id]
                    if (had == null || n.optLong("modified") > had.optLong("modified")) notes[id] = n
                }
            }
            addNotes(r?.optJSONArray("annotations"))
            addNotes(t?.optJSONArray("annotations"))
            o.put("bookmarks", JSONArray(marks.values.toList()))
            o.put("annotations", JSONArray(notes.values.toList()))
            o.remove("position")
            o.remove("finished")
            o.remove("finishedAt")
            place.optJSONObject("text")?.let { o.put("position", it) }
            if (place.has("finished")) {
                o.put("finished", true)
                o.put("finishedAt", place.optLong("finished"))
            }
            write(File(dir, READER), o)
        }
        place.optJSONObject("pdf")?.let { ReaderStore.writePdfPlace(dir, it) }
    }

    private fun read(f: File): JSONObject? = if (!f.isFile) null else runCatching { JSONObject(f.readText()) }.getOrNull()

    private fun write(f: File, o: JSONObject) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".part")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
