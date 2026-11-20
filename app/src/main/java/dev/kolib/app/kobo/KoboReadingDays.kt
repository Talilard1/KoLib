package dev.kolib.app.kobo

import dev.kolib.app.log.AppLog
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The reading done on a Kobo, day by day. The Kobo keeps only how long each book has been read for in all, so
 * each time its copy is saved the time added to each book since the last copy is put on the day the book was
 * last read. Books seen for the first time aren't counted: when their earlier reading was done isn't known.
 * Kept beside the Kobo's copy, apart from the reading done in KoLib, which it never changes.
 */
object KoboReadingDays {

    private const val FILE = "reading-days.json"

    /** Days are kept for a little over a year. */
    private const val KEEP_DAYS = 400L

    /** The seconds read on each day ("2026-11-21"), from [folder], the Kobo's own folder. */
    fun load(folder: File): Map<String, Int> {
        val o = read(folder) ?: return emptyMap()
        val days = o.optJSONObject("days") ?: return emptyMap()
        return days.keys().asSequence().associateWith { days.optInt(it) }.filterValues { it > 0 }
    }

    /** Brings the record in [folder] up to date with [snapshot], read at [now]. */
    fun update(folder: File, snapshot: KoboSnapshot, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        try {
            val o = read(folder) ?: JSONObject()
            val books = o.optJSONObject("books") ?: JSONObject()
            val days = o.optJSONObject("days") ?: JSONObject()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            for (b in snapshot.books) {
                val seconds = b.secondsRead ?: continue
                val before = if (books.has(b.contentId)) books.optInt(b.contentId) else null
                if (before != null && seconds > before) {
                    // Put on the day the Kobo says the book was last read; a time ahead of now (a wrong clock) counts as today.
                    val day = b.lastReadMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?.takeIf { !it.isAfter(today) } ?: today
                    val key = day.toString()
                    days.put(key, days.optInt(key) + (seconds - before))
                }
                books.put(b.contentId, seconds)
            }
            // Old days go.
            val oldest = today.minusDays(KEEP_DAYS)
            days.keys().asSequence().toList().forEach { k ->
                val d = runCatching { LocalDate.parse(k) }.getOrNull()
                if (d == null || d.isBefore(oldest)) days.remove(k)
            }
            o.put("books", books).put("days", days).put("at", now)
            folder.mkdirs()
            val tmp = File(folder, "$FILE.part")
            tmp.writeText(o.toString())
            val file = File(folder, FILE)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("Kobo", "The Kobo's reading by day couldn't be kept", e)
        }
    }

    private fun read(folder: File): JSONObject? =
        File(folder, FILE).takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
}
