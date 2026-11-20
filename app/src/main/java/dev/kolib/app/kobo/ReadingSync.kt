package dev.kolib.app.kobo

import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.Chapter
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.KepubConverter
import dev.kolib.app.kobo.db.SqlSource
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.HighlightColor
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.reader.ReaderPosition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** A highlight, note or bookmark in the Kobo's database: a row of its Bookmark table. */
data class KoboMark(
    val id: String,
    /** The chapter's row (its ContentID). */
    val contentId: String,
    val startPath: String,
    val startOffset: Int,
    val endPath: String,
    val endOffset: Int,
    val text: String?,
    val note: String?,
    /** "highlight", "note" (a highlight with a note), "dogear" (a bookmark), "markup" (drawn on). */
    val type: String,
    val created: String?,
    val modified: String?,
    val chapterProgress: Double?,
    val color: Int?,
) {
    val isBookmark: Boolean get() = type.equals("dogear", true)
    val isHighlight: Boolean get() = type.equals("highlight", true) || type.equals("note", true)
    val createdMillis: Long get() = KoboDates.parseMillis(created) ?: 0L
    val changedMillis: Long get() = KoboDates.parseMillis(modified) ?: createdMillis
}

/** A chapter of a book on the Kobo (a row of ContentType 9): its ContentID, and the file it's in, as the Kobo writes it and plain. */
data class KoboChapterRow(val contentId: String, val raw: String, val path: String)

/** What the Kobo has about reading one book. */
data class KoboReading(
    val contentId: String,
    val kepub: Boolean,
    val lastRead: Long?,
    val readStatus: Int,
    val percent: Int,
    val chapterBookmark: String?,
    val adobeLocation: String?,
    val chapterProgress: Double?,
    /** When the Kobo took the book in (DateAdded): a different one means it's started the book afresh. */
    val added: String,
    val chapters: List<KoboChapterRow>,
    val marks: List<KoboMark>,
)

/** How this Kobo writes things, learnt from what its database has already. */
class KoboMarkStyle(
    /** The Bookmark table's columns (none: it has no such table). */
    val markColumns: Set<String>,
    val userId: String,
    private val markDate: DateTimeFormatter,
    private val readDate: DateTimeFormatter,
) {
    val hasMarks: Boolean get() = "BookmarkID" in markColumns
    val hasColor: Boolean get() = "Color" in markColumns
    fun markStamp(millis: Long): String = markDate.format(Instant.ofEpochMilli(millis))
    fun readStamp(millis: Long): String = readDate.format(Instant.ofEpochMilli(millis))

    companion object {
        private fun pattern(p: String): DateTimeFormatter = DateTimeFormatter.ofPattern(p).withZone(ZoneOffset.UTC)
        private val MARK_DEFAULT = pattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        private val READ_DEFAULT = pattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

        /** The way [sample] is written, to write new dates the same way. */
        fun dateStyle(sample: String?, fallback: DateTimeFormatter): DateTimeFormatter {
            val s = sample?.trim() ?: return fallback
            return when {
                Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z""").matches(s) -> pattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}""").matches(s) -> pattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
                Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ""").matches(s) -> pattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d""").matches(s) -> pattern("yyyy-MM-dd'T'HH:mm:ss")
                Regex("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}\+00:00""").matches(s) -> pattern("yyyy-MM-dd HH:mm:ss.SSS'+00:00'")
                Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}\+00:00""").matches(s) -> pattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'")
                else -> fallback
            }
        }

        fun read(db: SqlSource): KoboMarkStyle {
            fun columns(table: String): Set<String> = runCatching {
                db.query("PRAGMA table_info(\"$table\")").mapNotNull { it.string("name") }.toSet()
            }.getOrDefault(emptySet())
            val marks = columns("Bookmark")
            // Each asks for one value, called "v".
            fun one(sql: String): String? = runCatching { db.query(sql).firstOrNull()?.string("v") }.getOrNull()?.takeIf { it.isNotBlank() }
            val markSample = if ("DateCreated" in marks) one("SELECT DateCreated AS v FROM Bookmark WHERE DateCreated IS NOT NULL AND DateCreated != '' ORDER BY DateCreated DESC LIMIT 1") else null
            val readSample = one("SELECT DateLastRead AS v FROM content WHERE ContentType = 6 AND DateLastRead IS NOT NULL AND DateLastRead != '' ORDER BY DateLastRead DESC LIMIT 1")
            val user = (if ("UserID" in marks) one("SELECT UserID AS v FROM Bookmark WHERE UserID IS NOT NULL AND UserID != '' ORDER BY DateCreated DESC LIMIT 1") else null)
                ?: if ("UserID" in columns("user")) one("SELECT UserID AS v FROM user WHERE UserID IS NOT NULL AND UserID != '' LIMIT 1") else null
            return KoboMarkStyle(marks, user.orEmpty(), dateStyle(markSample, MARK_DEFAULT), dateStyle(readSample, READ_DEFAULT))
        }
    }
}

/** Reads what the Kobo has about reading a book. */
object KoboReadingDb {
    fun read(db: SqlSource, contentId: String, style: KoboMarkStyle): KoboReading? {
        val row = db.query("SELECT * FROM content WHERE ContentID = ? AND ContentType = 6", listOf(contentId)).firstOrNull() ?: return null
        val mime = row.string("MimeType").orEmpty()
        val chapters = runCatching {
            db.query("SELECT ContentID FROM content WHERE ContentType = 9 AND BookID = ?", listOf(contentId)).mapNotNull { r ->
                val id = r.string("ContentID") ?: return@mapNotNull null
                val raw = rawPath(id, contentId) ?: return@mapNotNull null
                KoboChapterRow(id, raw, KoboPlaces.cleanPath(raw) ?: return@mapNotNull null)
            }
        }.getOrDefault(emptyList())
        val marks = if (!style.hasMarks) emptyList() else db.query("SELECT * FROM Bookmark WHERE VolumeID = ?", listOf(contentId)).mapNotNull { r ->
            if (r.string("Hidden").equals("true", true)) return@mapNotNull null
            KoboMark(
                id = r.string("BookmarkID") ?: return@mapNotNull null,
                contentId = r.string("ContentID").orEmpty(),
                startPath = r.string("StartContainerPath").orEmpty(),
                startOffset = r.long("StartOffset")?.toInt() ?: 0,
                endPath = r.string("EndContainerPath").orEmpty(),
                endOffset = r.long("EndOffset")?.toInt() ?: 0,
                text = r.string("Text"),
                note = r.string("Annotation"),
                type = r.string("Type") ?: if (r.string("Text").isNullOrEmpty()) "dogear" else "highlight",
                created = r.string("DateCreated"),
                modified = r.string("DateModified"),
                chapterProgress = r.double("ChapterProgress"),
                color = if (r.has("Color")) r.long("Color")?.toInt() else null,
            )
        }
        return KoboReading(
            contentId = contentId,
            kepub = mime == "application/x-kobo-epub+zip" || contentId.lowercase().endsWith(".kepub.epub"),
            lastRead = KoboDates.parseMillis(row.string("DateLastRead")),
            readStatus = row.long("ReadStatus")?.toInt() ?: 0,
            percent = (row.long("___PercentRead") ?: 0L).toInt().coerceIn(0, 100),
            chapterBookmark = row.string("ChapterIDBookmarked")?.trim()?.takeIf { it.isNotEmpty() },
            adobeLocation = row.string("adobe_location")?.trim()?.takeIf { it.isNotEmpty() },
            chapterProgress = row.double("CurrentChapterProgress")?.takeIf { it in 0.0..1.0 },
            added = row.string("DateAdded").orEmpty(),
            chapters = chapters,
            marks = marks,
        )
    }

    /**
     * The chapter file a chapter's ContentID names, as the Kobo writes it: a kepub's "book!!OEBPS/Text/ch3.xhtml",
     * an EPUB's "book#(3)OEBPS/ch3.xhtml" (a contents entry, so maybe with a #part), a store book's "id!OEBPS!Text/ch3.xhtml".
     */
    fun rawPath(chapterId: String, bookId: String): String? {
        val rest = chapterId.removePrefix(bookId)
        val raw = when {
            rest.startsWith("!!") -> rest.removePrefix("!!")
            rest.startsWith("#(") -> rest.substringAfter(')')
            chapterId.contains('!') -> chapterId.split('!').drop(1).filter { it.isNotEmpty() }.joinToString("/")
            else -> return null
        }.substringBefore('#')
        return Regex("""^(.*\.(?:x?html?|xml))-\d+$""", RegexOption.IGNORE_CASE).matchEntire(raw)?.groupValues?.get(1) ?: raw.takeIf { it.isNotEmpty() }
    }
}

/**
 * Brings a book's reading in KoLib and on the Kobo together: where it's up to (wherever it was read last), and its
 * bookmarks, highlights and notes, both ways (one deleted on either side goes from the other too).
 *
 * KoLib keeps, in the book's reader.json, what it needs to tell new from deleted: "deleted", the bookmarks and
 * highlights deleted in KoLib (by id, with when), and "kobo", which of them have been on which Kobo (and as what).
 * A bookmark or highlight has the same id in both places: one KoLib sends keeps its id on the Kobo, and one from the
 * Kobo keeps the Kobo's.
 */
class ReadingSync(
    /** Which Kobo this is: its reading is kept apart from any other's. */
    private val koboKey: String,
    private val style: KoboMarkStyle,
    private val now: Long = System.currentTimeMillis(),
) {
    /** A book in the library and on the Kobo. */
    class Book(val id: String, val title: String, val dir: File, val epub: File, val kobo: KoboBook, val reading: KoboReading)

    data class Counts(
        val placeToKobo: Boolean = false,
        val placeFromKobo: Boolean = false,
        val toKobo: Int = 0,
        val fromKobo: Int = 0,
        val removedOnKobo: Int = 0,
        val removedHere: Int = 0,
        /** Bookmarks and highlights that couldn't go to the Kobo: its copy is an EPUB, or the place wasn't found. */
        val notSent: Int = 0,
    ) {
        val anything: Boolean get() = placeToKobo || placeFromKobo || toKobo + fromKobo + removedOnKobo + removedHere > 0
    }

    class Plan(
        val book: Book,
        /** Columns of the book's row to set on the Kobo, for where it's up to. */
        val place: Map<String, Any?>?,
        val add: List<Map<String, Any?>>,
        val update: List<Pair<String, Map<String, Any?>>>,
        val delete: List<String>,
        /** reader.json as it should be afterwards, and when it was read (null: no change). */
        val reader: JSONObject?,
        val readAt: Long,
        /** How far through the book is afterwards, for the library to show (null: unchanged). */
        val readHere: Pair<ReaderPosition?, ReadHere?>?,
        val counts: Counts,
    ) {
        val touchesKobo: Boolean get() = place != null || add.isNotEmpty() || update.isNotEmpty() || delete.isNotEmpty()

        /** Makes the Kobo's side of it in its database; how many changes that made. */
        fun applyTo(editor: KoboDbEditor): Int {
            var n = 0
            place?.let { if (editor.updateBook(book.reading.contentId, it)) n++ }
            delete.forEach { if (editor.deleteMark(it)) n++ }
            add.forEach { if (editor.addMark(it)) n++ }
            update.forEach { (id, values) -> if (editor.updateMark(id, values)) n++ }
            return n
        }
    }

    fun plan(b: Book): Plan = EpubFileFiles(EpubFile.open(b.epub)).use { files ->
        val chapters = BookStructure(files).chapters
        val readerFile = File(b.dir, "reader.json")
        val readAt = readerFile.lastModified()
        val o = readerFile.takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: JSONObject()
        Planner(b, files, chapters, o, readAt).run()
    }

    private inner class Planner(
        val b: Book,
        val files: EpubFileFiles,
        val chapters: List<Chapter>,
        val o: JSONObject,
        val readAt: Long,
    ) {
        val r = b.reading
        val bookmarks: MutableList<JSONObject> = objects(o.optJSONArray("bookmarks"))
        val annotations: MutableList<JSONObject> = objects(o.optJSONArray("annotations"))
        val deleted: JSONObject = o.optJSONObject("deleted") ?: JSONObject()
        val links: JSONObject = o.optJSONObject("kobo") ?: JSONObject()
        var changed = false

        var place: Map<String, Any?>? = null
        val add = ArrayList<Map<String, Any?>>()
        val update = ArrayList<Pair<String, Map<String, Any?>>>()
        val delete = ArrayList<String>()
        var counts = Counts()
        var readHere: Pair<ReaderPosition?, ReadHere?>? = null

        private val texts = HashMap<String, ChapterText?>()

        /** The chapter's text: as the kepub on the Kobo has it, or as it is in the book (for an EPUB's points). */
        fun text(path: String): ChapterText? = texts.getOrPut(path) {
            val xhtml = files.text(path) ?: return@getOrPut null
            runCatching {
                ChapterText(if (r.kepub && !KepubConverter.isKepub(xhtml)) KepubConverter.convert(xhtml) else xhtml)
            }.onFailure { AppLog.w("Kobo sync", "“${b.title}”: $path couldn't be read: ${it.message}") }.getOrNull()
        }

        private val sizes: Pair<Map<String, Int>, Int> by lazy {
            val linear = chapters.filter { it.linear }.ifEmpty { chapters }
            val each = linear.associate { c -> c.path to (files.text(c.path)?.let { runCatching { ChapterLetters(it).total }.getOrNull() } ?: 0) }
            each to each.values.sum()
        }

        /** How far through the book [letters] into chapter [path] is. */
        fun bookProgress(path: String, letters: Int): Float {
            val (each, total) = sizes
            if (total <= 0) return 0f
            var before = 0
            for ((p, n) in each) {
                if (p == path) return ((before + letters).toFloat() / total).coerceIn(0f, 1f)
                before += n
            }
            return 0f
        }

        fun chapterIndex(path: String): Int = chapters.indexOfFirst { it.path == path }.let { if (it < 0) Int.MAX_VALUE else it }

        fun run(): Plan {
            syncPlace()
            if (style.hasMarks) syncMarks()
            if (changed) {
                o.put("bookmarks", JSONArray(bookmarks.sortedWith(compareBy({ chapterIndex(it.optString("path")) }, { it.optDouble("progress", 0.0) }))))
                o.put("annotations", JSONArray(annotations))
                o.put("deleted", deleted)
                o.put("kobo", links)
            }
            return Plan(b, place, add, update, delete, if (changed) o else null, readAt, readHere, counts)
        }

        // ---- where it's up to -------------------------------------------------------------------------------

        private fun syncPlace() {
            val position = ReaderPosition.from(o.optJSONObject("position"))
            val finished = o.optBoolean("finished")
            val hereAt = when {
                finished -> o.optLong("finishedAt")
                position != null -> position.at
                else -> 0L
            }
            val koboAt = r.lastRead ?: 0L
            val koboStarted = r.readStatus != 0
            when {
                koboStarted && koboAt > hereAt + 1000 -> takeKobosPlace(koboAt)
                (finished || position != null) && hereAt > koboAt + 1000 -> sendPlace(position, finished, hereAt)
            }
        }

        private fun takeKobosPlace(at: Long) {
            if (r.readStatus == 2) {
                o.remove("position")
                o.put("finished", true)
                o.put("finishedAt", at)
                changed = true
                readHere = null to ReadHere(1f, at, finished = true)
                counts = counts.copy(placeFromKobo = true)
                return
            }
            val kobo = b.kobo.copy(
                chapterBookmark = r.chapterBookmark, adobeLocation = r.adobeLocation, chapterProgress = r.chapterProgress,
                percentRead = r.percent,
            )
            val found = runCatching { KoboPlaces.find(b.epub, kobo) }.getOrNull() ?: return
            val position = found.position.copy(at = at)
            o.put("position", position.toJson())
            o.remove("finished")
            o.remove("finishedAt")
            changed = true
            readHere = position to null
            counts = counts.copy(placeFromKobo = true)
        }

        private fun sendPlace(position: ReaderPosition?, finished: Boolean, at: Long) {
            val columns = LinkedHashMap<String, Any?>()
            columns["DateLastRead"] = style.readStamp(at)
            columns["FirstTimeReading"] = "false"
            if (finished || position == null) {
                columns["ReadStatus"] = 2
                columns["___PercentRead"] = 100
            } else {
                val row = rowFor(position.path)
                val t = text(position.path)
                val spot = t?.let { locate(it, position) }
                val raw = row?.raw ?: position.path
                val bookmark = when {
                    t == null || spot == null -> null
                    r.kepub -> t.spanAt(spot.first, spot.second)?.let { "$raw#${it.first}" }
                    else -> t.adobePoint(spot.first, spot.second)?.let { "$raw#point($it)" }
                } ?: run {
                    AppLog.w("Kobo sync", "“${b.title}”: the place in ${position.path} wasn't found to send.")
                    return
                }
                columns["ChapterIDBookmarked"] = bookmark
                if (!r.kepub) columns["adobe_location"] = bookmark
                columns["ReadStatus"] = 1
                columns["___PercentRead"] = (position.bookProgress * 100).roundToInt().coerceIn(0, 99)
                columns["CurrentChapterProgress"] = position.progress.toDouble().coerceIn(0.0, 1.0)
            }
            place = columns
            counts = counts.copy(placeToKobo = true)
        }

        /** Where a KoLib place is: its letters, else its words (nearest how far through the chapter it is), else just that. */
        fun locate(t: ChapterText, p: ReaderPosition): Pair<Int, Int>? {
            p.anchor?.takeIf { it.startsWith("chars=") }?.substringAfter('=')?.toIntOrNull()?.let { return t.atLetters(it) }
            val near = t.atLetters((t.total * p.progress.coerceIn(0f, 1f)).toInt())
            if (p.snippet.isNotBlank()) {
                t.findNear(p.snippet, near)?.let { return it.start to it.startOffset }
                // Its first words only, should the rest have changed.
                t.findNear(p.snippet.take(40), near)?.let { return it.start to it.startOffset }
            }
            return near
        }

        /** The Kobo's chapter row for the library's chapter [path]. */
        fun rowFor(path: String): KoboChapterRow? {
            val rows = r.chapters
            rows.firstOrNull { it.path == path }?.let { return it }
            val wanted = path.lowercase()
            rows.firstOrNull { it.path.lowercase() == wanted }?.let { return it }
            rows.filter { val p = it.path.lowercase(); p.endsWith("/$wanted") || wanted.endsWith("/$p") }.distinctBy { it.path }.singleOrNull()?.let { return it }
            val name = wanted.substringAfterLast('/')
            return rows.filter { it.path.lowercase().substringAfterLast('/') == name }.distinctBy { it.path }.singleOrNull()
        }

        // ---- bookmarks, highlights and notes ----------------------------------------------------------------

        private fun syncMarks() {
            val here = LinkedHashMap<String, JSONObject>()
            for (m in bookmarks) here[markId(m)] = m
            for (a in annotations) a.optString("id").takeIf { it.isNotEmpty() }?.let { here[it] = a }
            val there = r.marks.filter { it.isBookmark || it.isHighlight }.associateBy { it.id }
            val linked = links.keys().asSequence().filter { links.optJSONObject(it)?.has(koboKey) == true }.toList()
            val ids = LinkedHashSet<String>().apply { addAll(here.keys); addAll(there.keys); addAll(linked) }
            for (id in ids) {
                val item = here[id]
                val row = there[id]
                val link = links.optJSONObject(id)?.optJSONObject(koboKey)
                val onThisKobo = link != null && link.optString("added") == r.added
                when {
                    item != null && row != null -> both(id, item, row)
                    item != null -> if (onThisKobo) removeHere(id, item) else send(id, item)
                    row != null -> if (deleted.has(id) || onThisKobo) removeThere(id) else take(row)
                    else -> unlink(id)
                }
            }
        }

        private fun isBookmark(item: JSONObject) = !item.has("start")

        private fun colorCode(c: HighlightColor): Int = when (c) {
            HighlightColor.YELLOW -> 0
            HighlightColor.PINK -> 1
            HighlightColor.BLUE -> 2
            HighlightColor.GREEN -> 3
        }

        private fun colorOf(code: Int?): HighlightColor = when (code) {
            1 -> HighlightColor.PINK
            2 -> HighlightColor.BLUE
            3 -> HighlightColor.GREEN
            else -> HighlightColor.YELLOW
        }

        private fun hereColor(item: JSONObject) = HighlightColor.entries.firstOrNull { it.js == item.optString("color") } ?: HighlightColor.YELLOW

        /** What a highlight is, to tell which side changed it: its note, and its colour where the Kobo has colours. */
        private fun stateHere(item: JSONObject) = item.optString("note").trim() + if (style.hasColor) "|" + colorCode(hereColor(item)) else ""
        private fun stateThere(row: KoboMark) = row.note.orEmpty().trim() + if (style.hasColor) "|" + (row.color ?: 0) else ""

        private fun link(id: String, state: String) {
            val all = links.optJSONObject(id) ?: JSONObject().also { links.put(id, it) }
            val had = all.optJSONObject(koboKey)
            if (had != null && had.optString("added") == r.added && had.optString("seen") == state) return
            all.put(koboKey, JSONObject().put("added", r.added).put("seen", state).put("at", now))
            changed = true
        }

        private fun unlink(id: String) {
            val all = links.optJSONObject(id) ?: return
            if (all.has(koboKey)) {
                all.remove(koboKey)
                if (all.length() == 0) links.remove(id)
                changed = true
            }
        }

        /** On both: a highlight changed on one side since they were last brought together goes to the other. */
        private fun both(id: String, item: JSONObject, row: KoboMark) {
            if (isBookmark(item) || row.isBookmark) {
                if (!item.has("id")) {
                    item.put("id", id)
                    changed = true
                }
                link(id, "")
                return
            }
            val mine = stateHere(item)
            val theirs = stateThere(row)
            if (mine == theirs) {
                link(id, mine)
                return
            }
            val seen = links.optJSONObject(id)?.optJSONObject(koboKey)?.takeIf { it.optString("added") == r.added }?.optString("seen")
            val koboChanged = when {
                seen == mine -> true
                seen == theirs -> false
                else -> row.changedMillis > item.optLong("modified")
            }
            if (koboChanged) {
                item.put("note", row.note.orEmpty().trim())
                if (style.hasColor) item.put("color", colorOf(row.color).js)
                item.put("modified", maxOf(row.changedMillis, item.optLong("modified") + 1))
                changed = true
                counts = counts.copy(fromKobo = counts.fromKobo + 1)
                link(id, theirs)
            } else {
                val note = item.optString("note").trim()
                val columns = LinkedHashMap<String, Any?>()
                columns["Annotation"] = note.ifEmpty { null }
                columns["Type"] = if (note.isNotEmpty()) "note" else "highlight"
                columns["DateModified"] = style.markStamp(item.optLong("modified").takeIf { it > 0 } ?: now)
                if (style.hasColor) columns["Color"] = colorCode(hereColor(item))
                update += id to columns
                counts = counts.copy(toKobo = counts.toKobo + 1)
                link(id, mine)
            }
        }

        /** Deleted on the Kobo: it goes here too. */
        private fun removeHere(id: String, item: JSONObject) {
            bookmarks.remove(item)
            annotations.remove(item)
            deleted.put(id, now)
            unlink(id)
            changed = true
            counts = counts.copy(removedHere = counts.removedHere + 1)
        }

        /** Deleted in KoLib: it goes from the Kobo too. */
        private fun removeThere(id: String) {
            delete += id
            unlink(id)
            counts = counts.copy(removedOnKobo = counts.removedOnKobo + 1)
        }

        /** New in KoLib: a row for it on the Kobo. Only a kepub's places can be written. */
        private fun send(id: String, item: JSONObject) {
            if (!r.kepub) {
                counts = counts.copy(notSent = counts.notSent + 1)
                return
            }
            val path = item.optString("path")
            val row = rowFor(path)
            val t = text(path)
            if (row == null || t == null) {
                counts = counts.copy(notSent = counts.notSent + 1)
                return
            }
            val values = LinkedHashMap<String, Any?>()
            if (isBookmark(item)) {
                val p = ReaderPosition.from(item) ?: return
                val spot = locate(t, p)
                val span = spot?.let { t.spanAt(it.first, it.second) }
                if (spot == null || span == null) {
                    counts = counts.copy(notSent = counts.notSent + 1)
                    return
                }
                val at = item.optLong("at").takeIf { it > 0 } ?: now
                values += common(id, row, "dogear", at, at, t.lettersAt(spot.first, spot.second), t.total)
                values["StartContainerPath"] = spanPath(span.first)
                values["StartOffset"] = span.second
                values["EndContainerPath"] = spanPath(span.first)
                values["EndOffset"] = span.second
                values["Text"] = null
                values["Annotation"] = null
                if (!item.has("id")) item.put("id", id)
                link(id, "")
            } else {
                val words = item.optString("text")
                val found = t.find(words, item.optInt("occurrence", 0)) ?: t.find(words, 0)
                val start = found?.let { t.spanAt(it.start, it.startOffset) }
                val end = found?.let { t.spanAt(it.end, it.endOffset) }
                if (found == null || start == null || end == null) {
                    counts = counts.copy(notSent = counts.notSent + 1)
                    return
                }
                val note = item.optString("note").trim()
                val created = item.optLong("created").takeIf { it > 0 } ?: now
                val modified = item.optLong("modified").takeIf { it > 0 } ?: created
                values += common(id, row, if (note.isNotEmpty()) "note" else "highlight", created, modified, t.lettersAt(found.start, found.startOffset), t.total)
                values["StartContainerPath"] = spanPath(start.first)
                values["StartOffset"] = start.second
                values["EndContainerPath"] = spanPath(end.first)
                values["EndOffset"] = end.second
                values["Text"] = t.textOf(found)
                values["Annotation"] = note.ifEmpty { null }
                if (style.hasColor) values["Color"] = colorCode(hereColor(item))
                link(id, stateHere(item))
            }
            add += values
            changed = true
            counts = counts.copy(toKobo = counts.toKobo + 1)
        }

        private fun common(id: String, row: KoboChapterRow, type: String, created: Long, modified: Long, letters: Int, total: Int) = linkedMapOf<String, Any?>(
            "BookmarkID" to id,
            "VolumeID" to r.contentId,
            "ContentID" to row.contentId,
            "StartContainerChildIndex" to -99,
            "EndContainerChildIndex" to -99,
            "ExtraAnnotationData" to null,
            "DateCreated" to style.markStamp(created),
            "DateModified" to if (modified > created) style.markStamp(modified) else null,
            "ChapterProgress" to if (total > 0) letters.toDouble() / total else 0.0,
            "Hidden" to "false",
            "Version" to null,
            "Creator" to null,
            "UUID" to null,
            "UserID" to style.userId,
            "SyncTime" to null,
            "Published" to "false",
            "ContextString" to null,
            "Type" to type,
        )

        /** New on the Kobo: brought into KoLib, with the Kobo's id. */
        private fun take(row: KoboMark) {
            val path = chapterOf(row) ?: run {
                AppLog.w("Kobo sync", "“${b.title}”: a ${row.type} in ${row.contentId} isn't in a chapter KoLib has.")
                return
            }
            val chapter = chapters.first { it.path == path }
            val t = text(path) ?: return
            val start = spotOf(t, row.startPath, row.startOffset)
            if (row.isBookmark) {
                val spot = start ?: row.chapterProgress?.let { t.atLetters((t.total * it).toInt()) } ?: return
                val letters = t.lettersAt(spot.first, spot.second)
                val p = ReaderPosition(
                    path = path,
                    anchor = "chars=$letters",
                    progress = if (t.total > 0) letters.toFloat() / t.total else 0f,
                    bookProgress = bookProgress(path, letters),
                    snippet = t.snippetAt(spot.first, spot.second),
                    chapterTitle = chapter.title,
                    at = row.createdMillis.takeIf { it > 0 } ?: now,
                )
                bookmarks += p.toJson().put("id", row.id)
                link(row.id, "")
            } else {
                val end = spotOf(t, row.endPath, row.endOffset)
                var stretch = if (start != null && end != null) ChapterText.Stretch(start.first, start.second, end.first, end.second) else null
                val koboWords = row.text?.takeIf { ChapterText.norm(it).isNotEmpty() }
                // Its words at that place: the Kobo's, if they're what's there (else the place's own).
                var words = stretch?.let { s ->
                    val ours = t.textOf(s)
                    if (koboWords != null && ChapterText.norm(koboWords).equals(ChapterText.norm(ours), ignoreCase = true)) koboWords else ours.takeIf { ChapterText.norm(it).isNotEmpty() }
                }
                if (words == null && koboWords != null) {
                    // The place isn't there: the Kobo's words, nearest where it says they are.
                    val near = row.chapterProgress?.let { t.atLetters((t.total * it).toInt()) }
                    stretch = t.findNear(koboWords, near)
                    words = koboWords.takeIf { stretch != null }
                }
                val s = stretch ?: return
                if (words == null) return
                val letters = t.lettersAt(s.start, s.startOffset)
                val created = row.createdMillis.takeIf { it > 0 } ?: now
                val a = JSONObject()
                    .put("id", row.id)
                    .put("path", path)
                    .put("start", "chars=$letters")
                    .put("end", "chars=${t.lettersAt(s.end, s.endOffset)}")
                    .put("text", words)
                    .put("occurrence", t.occurrenceBefore(words, s.start, s.startOffset))
                    .put("color", colorOf(row.color).js)
                    .put("style", "highlight")
                    .put("note", row.note.orEmpty().trim())
                    .put("chapter", chapter.title)
                    .put("book", bookProgress(path, letters).toDouble())
                    .put("created", created)
                    .put("modified", maxOf(created, row.changedMillis))
                annotations += a
                link(row.id, stateThere(row))
            }
            changed = true
            counts = counts.copy(fromKobo = counts.fromKobo + 1)
        }

        /** The library's chapter a Kobo bookmark or highlight is in. */
        private fun chapterOf(row: KoboMark): String? {
            val raw = row.startPath.substringBefore('#').takeIf { !r.kepub && it.isNotEmpty() && !it.startsWith("span") }
                ?: KoboReadingDb.rawPath(row.contentId, r.contentId)
                ?: return null
            val clean = KoboPlaces.cleanPath(raw) ?: return null
            return KoboPlaces.chapterFor(chapters, clean)?.path
        }

        /** Where a Kobo container path is: a kepub's sentence ("span#kobo\.12\.1") and character, or an EPUB's point. */
        private fun spotOf(t: ChapterText, path: String, offset: Int): Pair<Int, Int>? {
            SPAN_IN_PATH.find(path)?.let { m -> return t.atSpan("kobo.${m.groupValues[1]}.${m.groupValues[2]}", offset) }
            POINT_IN_PATH.find(path)?.let { m -> return t.atAdobePoint(m.groupValues[1]) }
            return null
        }
    }

    companion object {
        private val SPAN_IN_PATH = Regex("""kobo\\?\.(\d+)\\?\.(\d+)""")
        private val POINT_IN_PATH = Regex("""point\(([^)]*)\)""")

        /** A sentence's span as the Kobo names it in a bookmark: "span#kobo\.12\.1". */
        fun spanPath(span: String) = "span#" + span.replace(".", "\\.")

        /** A bookmark's id: its own, or one made from its place (the same on every device) for one from before ids. */
        fun markId(m: JSONObject): String = m.optString("id").ifEmpty {
            ReaderPosition.markIdOf(m.optString("path"), if (m.has("anchor") && !m.isNull("anchor")) m.optString("anchor") else null)
        }

        private fun objects(a: JSONArray?): MutableList<JSONObject> =
            (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it) }.toMutableList()
    }
}
