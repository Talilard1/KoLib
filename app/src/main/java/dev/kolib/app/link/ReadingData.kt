package dev.kolib.app.link

import dev.kolib.app.reader.ReaderPosition
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.reader.deletionsFrom
import dev.kolib.app.reader.deletionsJson
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
 * last), except those deleted on either: deletions are kept, by id, and go everywhere. So do the notes of which have been
 * on a Kobo (see dev.kolib.app.kobo.ReadingSync).
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

    /**
     * Where the book was left, from what [readingOf] gives: `{"text": position}` or `{"finished": when}`, and `{"pdf": place}`;
     * with its bookmarks, highlights and notes, and those deleted, as `{"marks": …}` (see [marksOf]).
     */
    fun placeOf(reading: JSONObject): JSONObject {
        val o = JSONObject()
        reading.optJSONObject("reader")?.let { r ->
            when {
                r.optBoolean("finished") -> o.put("finished", r.optLong("finishedAt"))
                r.optJSONObject("position") != null -> o.put("text", r.getJSONObject("position"))
            }
            marksOf(r)?.let { o.put("marks", it) }
        }
        reading.optJSONObject("pdf")?.let { o.put("pdf", it) }
        return o
    }

    /** A book's bookmarks, highlights (with their notes), deletions and Kobo notes from its reader.json; null if it has none. */
    fun marksOf(r: JSONObject?): JSONObject? {
        r ?: return null
        val o = JSONObject()
        for (k in MARK_KEYS) r.opt(k)?.takeIf { it != JSONObject.NULL }?.let { o.put(k, it) }
        val any = (o.optJSONArray("bookmarks")?.length() ?: 0) + (o.optJSONArray("annotations")?.length() ?: 0) +
            (o.optJSONObject("deleted")?.length() ?: 0) + (o.optJSONObject("kobo")?.length() ?: 0)
        return o.takeIf { any > 0 }
    }

    private val MARK_KEYS = listOf("bookmarks", "annotations", "deleted", "kobo")

    /**
     * Two devices' bookmarks and highlights together: those of both, each once (a highlight as it was changed last; a
     * bookmark by id, and one place once), except any deleted on either. Both devices come to the same.
     */
    fun mergeMarks(a: JSONObject?, b: JSONObject?): JSONObject? {
        if (a == null && b == null) return null
        val deleted = deletionsFrom(a?.optJSONObject("deleted")).toMutableMap()
        for ((id, at) in deletionsFrom(b?.optJSONObject("deleted"))) if (at > (deleted[id] ?: 0L)) deleted[id] = at
        // Bookmarks: one for each place (the one made first, the same whichever device puts them together), in the order they come.
        val allMarks = listOfNotNull(a?.optJSONArray("bookmarks"), b?.optJSONArray("bookmarks"))
            .flatMap { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
        val standing = HashMap<String, JSONObject>()
        val ids = HashSet<String>()
        for (m in allMarks.sortedWith(compareBy({ it.optLong("at") }, { markId(it) }))) {
            val id = markId(m)
            if (id in deleted || !ids.add(id)) continue
            standing.putIfAbsent(m.optString("path") + "|" + m.optString("anchor"), m)
        }
        val marks = LinkedHashMap<String, JSONObject>()
        for (m in allMarks) {
            if (standing[m.optString("path") + "|" + m.optString("anchor")] !== m) continue
            marks.putIfAbsent(markId(m), m)
        }
        val notes = LinkedHashMap<String, JSONObject>()
        for (arr in listOfNotNull(a?.optJSONArray("annotations"), b?.optJSONArray("annotations"))) {
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i) ?: continue
                val id = n.optString("id")
                if (id.isEmpty() || id in deleted) continue
                val had = notes[id]
                if (had == null || n.optLong("modified") > had.optLong("modified") ||
                    (n.optLong("modified") == had.optLong("modified") && n.toString() > had.toString())
                ) notes[id] = n
            }
        }
        val out = JSONObject()
        out.put("bookmarks", JSONArray(marks.values.toList()))
        out.put("annotations", JSONArray(notes.values.sortedWith(compareBy({ it.optLong("created") }, { it.optString("id") }))))
        if (deleted.isNotEmpty()) out.put("deleted", deletionsJson(deleted))
        mergeKobo(a?.optJSONObject("kobo"), b?.optJSONObject("kobo"))?.let { out.put("kobo", it) }
        return out
    }

    /** Whether two sets of marks hold the same: the same bookmarks and highlights (as last changed), deletions and Kobo notes. */
    private fun marksKey(m: JSONObject?): String {
        m ?: return ""
        val sb = StringBuilder()
        m.optJSONArray("bookmarks")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::markId) }.sorted().forEach { sb.append("b").append(it).append(';') } }
        m.optJSONArray("annotations")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("id") + "@" + it.optLong("modified") }.sorted().forEach { sb.append("a").append(it).append(';') }
        }
        m.optJSONObject("deleted")?.let { d -> d.keys().asSequence().sorted().forEach { sb.append("d").append(it).append(';') } }
        m.optJSONObject("kobo")?.let { k ->
            k.keys().asSequence().sorted().forEach { id ->
                val per = k.optJSONObject(id) ?: return@forEach
                per.keys().asSequence().sorted().forEach { kk -> sb.append("k").append(id).append('/').append(kk).append('@').append(per.optJSONObject(kk)?.optLong("at")).append(';') }
            }
        }
        return sb.toString()
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
        mergeMarks(a.optJSONObject("marks"), b.optJSONObject("marks"))?.let { out.put("marks", it) }
        return out
    }

    /** Whether two places are the same, bookmarks and highlights and all. */
    fun samePlace(a: JSONObject, b: JSONObject): Boolean =
        textAt(a) == textAt(b) && textKey(a) == textKey(b) &&
            a.optJSONObject("pdf")?.let(::pdfKey) == b.optJSONObject("pdf")?.let(::pdfKey) &&
            marksKey(a.optJSONObject("marks")) == marksKey(b.optJSONObject("marks"))

    /** The place a book was left at: in the text, or finished, and when (null if neither). */
    fun textAt(place: JSONObject): Long? =
        place.optJSONObject("text")?.optLong("at") ?: if (place.has("finished")) place.optLong("finished") else null

    /** When the book was last left, in any part of its place (0 if it never was). */
    fun latest(place: JSONObject): Long = maxOf(textAt(place) ?: 0L, place.optJSONObject("pdf")?.optLong("at") ?: 0L)

    private fun textKey(place: JSONObject): String =
        place.optJSONObject("text")?.let { "t|" + it.optString("path") + "|" + it.optString("anchor") + "|" + it.optDouble("progress", 0.0) } ?: if (place.has("finished")) "f" else ""

    private fun pdfKey(p: JSONObject): String = "${p.optLong("at")}|${p.optInt("page")}|${p.optInt("pages")}|${p.optBoolean("asText")}"

    /**
     * Puts [place] in [dir]: the text place (or finished) and the PDF page; and its bookmarks and highlights, when it
     * has them (already put together with this device's: see [mergePlace]).
     */
    fun applyPlace(dir: File, place: JSONObject) {
        val text = place.optJSONObject("text")
        val finished = if (place.has("finished")) place.optLong("finished") else null
        val marks = place.optJSONObject("marks")
        if (text != null || finished != null || marks != null) ReaderStore.rewrite(dir) { o ->
            if (text != null) {
                o.put("position", text)
                o.remove("finished")
                o.remove("finishedAt")
            } else if (finished != null) {
                o.remove("position")
                o.put("finished", true)
                o.put("finishedAt", finished)
            }
            if (marks != null) {
                // What this device has now, with what came: anything changed here since is kept too.
                val merged = mergeMarks(marksOf(o), marks)
                for (k in MARK_KEYS) {
                    o.remove(k)
                    merged?.opt(k)?.let { o.put(k, it) }
                }
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
            val marks = mergeMarks(marksOf(r), marksOf(t))
            for (k in MARK_KEYS) {
                o.remove(k)
                marks?.opt(k)?.let { o.put(k, it) }
            }
            if (!o.has("bookmarks")) o.put("bookmarks", JSONArray())
            if (!o.has("annotations")) o.put("annotations", JSONArray())
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

    /** A bookmark's id: its own, or one made from its place, as [ReaderPosition.markId] makes it. */
    private fun markId(m: JSONObject): String = m.optString("id").ifEmpty {
        ReaderPosition.markIdOf(m.optString("path"), if (m.has("anchor") && !m.isNull("anchor")) m.optString("anchor") else null)
    }

    /** Which bookmarks and highlights have been on which Kobo, from both: for each, the later word of each Kobo. */
    private fun mergeKobo(a: JSONObject?, b: JSONObject?): JSONObject? {
        if (a == null && b == null) return null
        val out = JSONObject()
        for (src in listOfNotNull(a, b)) {
            for (id in src.keys()) {
                val kobos = src.optJSONObject(id) ?: continue
                val into = out.optJSONObject(id) ?: JSONObject().also { out.put(id, it) }
                for (k in kobos.keys()) {
                    val v = kobos.optJSONObject(k) ?: continue
                    val had = into.optJSONObject(k)
                    if (had == null || v.optLong("at") > had.optLong("at")) into.put(k, v)
                }
            }
        }
        return out.takeIf { it.length() > 0 }
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
