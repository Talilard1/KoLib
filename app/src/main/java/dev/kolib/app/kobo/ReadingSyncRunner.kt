package dev.kolib.app.kobo

import android.content.Context
import dev.kolib.app.library.Library
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.ReadingHereIndex
import dev.kolib.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Books whose reading is to be synced with the Kobo as soon as it has them: books just sent (which the Kobo takes in
 * after it's ejected), or ones asked for while it wasn't connected. Kept until they're done.
 */
class ReadingSyncQueue(context: Context) {
    private val prefs = context.getSharedPreferences("reading_sync", Context.MODE_PRIVATE)

    @Synchronized
    fun add(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY, all() + ids).apply()
    }

    @Synchronized
    fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY, all() - ids.toSet()).apply()
    }

    @Synchronized
    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    private companion object {
        const val KEY = "ids"
    }
}

/** Carries out [ReadingSync] for books in the library and on the Kobo: one write of the Kobo's database for them all, then KoLib's side. */
class ReadingSyncRunner(
    private val repo: KoboRepository,
    private val library: Library,
    private val readingHere: ReadingHereIndex,
) {
    data class Outcome(
        /** The connection afterwards (a fresh read of the Kobo's database if it changed). */
        val session: KoboSession,
        /** Books synced (whether or not anything needed to change). */
        val synced: Set<String>,
        /** Books the Kobo hasn't got yet (it takes books in once it's ejected). */
        val waiting: Set<String>,
        val failed: Int,
        val placesToKobo: Int,
        val placesFromKobo: Int,
        val toKobo: Int,
        val fromKobo: Int,
        val removed: Int,
        val notSent: Int,
    ) {
        /** What happened, to show. */
        fun describe(): String {
            val parts = ArrayList<String>()
            if (placesToKobo + placesFromKobo + toKobo + fromKobo + removed == 0) {
                if (synced.isNotEmpty()) parts += tr("Your reading already matches the Kobo.")
            } else {
                if (placesToKobo > 0) parts += if (placesToKobo == 1) tr("The Kobo now opens 1 book where you are in KoLib.") else tr("The Kobo now opens {0} books where you are in KoLib.", placesToKobo)
                if (placesFromKobo > 0) parts += if (placesFromKobo == 1) tr("KoLib now opens 1 book where you are on the Kobo.") else tr("KoLib now opens {0} books where you are on the Kobo.", placesFromKobo)
                if (toKobo > 0) parts += if (toKobo == 1) tr("1 bookmark or highlight copied to the Kobo.") else tr("{0} bookmarks and highlights copied to the Kobo.", toKobo)
                if (fromKobo > 0) parts += if (fromKobo == 1) tr("1 bookmark or highlight copied from the Kobo.") else tr("{0} bookmarks and highlights copied from the Kobo.", fromKobo)
                if (removed > 0) parts += if (removed == 1) tr("1 deleted on one side was deleted on the other.") else tr("{0} deleted on one side were deleted on the other.", removed)
            }
            if (notSent > 0) parts += if (notSent == 1) tr("1 bookmark or highlight stays in KoLib only. The Kobo takes them only in books sent as Kepub.") else tr("{0} bookmarks and highlights stay in KoLib only. The Kobo takes them only in books sent as Kepub.", notSent)
            if (waiting.isNotEmpty()) parts += if (waiting.size == 1) tr("1 book syncs once the Kobo has added it. Eject the Kobo, then connect it again.") else tr("{0} books sync once the Kobo has added them. Eject the Kobo, then connect it again.", waiting.size)
            if (failed > 0) parts += if (failed == 1) tr("1 book couldn't be synced. The log has the details.") else tr("{0} books couldn't be synced. The log has the details.", failed)
            return parts.joinToString(" ")
        }
    }

    /** [onKoboDone] is called once nothing more is read from or written to the Kobo, so it can be ejected. */
    suspend fun run(session: KoboSession, ids: Collection<String>, choices: SyncChoices = SyncChoices(), onKoboDone: () -> Unit = {}): Outcome = withContext(Dispatchers.IO) {
        val books = ids.mapNotNull { library.book(it) }.filter { it.koboPath != null && !it.isPdf && !it.isComic }
        val style = repo.readLocal(session) { KoboMarkStyle.read(it) }
        val sync = ReadingSync(koboKey(session.info), style, choices = choices)
        val readings = repo.readLocal(session) { db ->
            books.associate { b -> b.id to runCatching { KoboReadingDb.read(db, KoboPaths.contentId(b.koboPath!!), style) }.getOrNull() }
        }
        val plans = ArrayList<ReadingSync.Plan>()
        val waiting = HashSet<String>()
        var failed = 0
        for (b in books) {
            val contentId = KoboPaths.contentId(b.koboPath!!)
            val reading = readings[b.id]
            val kobo = session.snapshot.booksById[contentId]
            if (reading == null || kobo == null) {
                waiting += b.id
                continue
            }
            try {
                val epub = library.epubFile(b.id)
                if (!epub.isFile) throw IllegalStateException("The book's file isn't there.")
                plans += sync.plan(ReadingSync.Book(b.id, b.title, library.bookDir(b.id), epub, kobo, reading))
            } catch (e: Exception) {
                AppLog.e("Kobo sync", "“${b.title}” couldn't be synced", e)
                failed++
            }
        }
        for (p in plans) {
            val c = p.counts
            if (c.anything || c.notSent > 0) {
                AppLog.i(
                    "Kobo sync",
                    "“${p.book.title}”: " + listOfNotNull(
                        "place to the Kobo".takeIf { c.placeToKobo }, "place from the Kobo".takeIf { c.placeFromKobo },
                        c.toKobo.takeIf { it > 0 }?.let { "$it to the Kobo" }, c.fromKobo.takeIf { it > 0 }?.let { "$it from the Kobo" },
                        c.removedOnKobo.takeIf { it > 0 }?.let { "$it deleted on the Kobo" }, c.removedHere.takeIf { it > 0 }?.let { "$it deleted here" },
                        c.notSent.takeIf { it > 0 }?.let { "$it not sent" },
                    ).joinToString(", ") + ".",
                )
            }
        }
        var after = session
        val forKobo = plans.filter { it.touchesKobo }
        if (forKobo.isNotEmpty()) {
            // Nothing here is kept unless the Kobo has its side: an error stops it all.
            after = repo.writeDatabase(session, onWritten = onKoboDone) { editor -> forKobo.sumOf { it.applyTo(editor) } } ?: session
        }
        onKoboDone()
        // The Kobo has its side now, so a book that can't be saved here is only counted, not the end of the rest.
        val notSaved = HashSet<String>()
        for (p in plans) {
            val o = p.reader ?: continue
            val f = File(p.book.dir, "reader.json")
            if (f.lastModified() != p.readAt) {
                // Read meanwhile (on another device, brought over): the next sync puts the two together.
                AppLog.w("Kobo sync", "“${p.book.title}” changed while it was being synced; KoLib's side is left for next time.")
                continue
            }
            try {
                write(f, o.toString())
                p.readHere?.let { (position, here) ->
                    if (position != null) readingHere.update(p.book.id, position) else if (here != null) readingHere.set(p.book.id, here)
                }
            } catch (e: Exception) {
                AppLog.e("Kobo sync", "“${p.book.title}”: KoLib's side couldn't be saved", e)
                notSaved += p.book.id
            }
        }
        Outcome(
            session = after,
            synced = plans.map { it.book.id }.toSet() - notSaved,
            waiting = waiting,
            failed = failed + notSaved.size,
            placesToKobo = plans.count { it.counts.placeToKobo },
            placesFromKobo = plans.count { it.counts.placeFromKobo },
            toKobo = plans.sumOf { it.counts.toKobo },
            fromKobo = plans.sumOf { it.counts.fromKobo },
            removed = plans.sumOf { it.counts.removedHere + it.counts.removedOnKobo },
            notSent = plans.sumOf { it.counts.notSent },
        )
    }

    private fun write(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".part")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    companion object {
        /** Which Kobo this is, without keeping its serial number: from it, or failing that its drive's id. */
        fun koboKey(info: KoboDeviceInfo): String {
            val source = info.version?.serial ?: info.volumeUuid ?: "kobo"
            val md = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            return md.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
