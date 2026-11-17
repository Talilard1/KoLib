package dev.kolib.app.kobo

import dev.kolib.app.tr
import android.os.ParcelFileDescriptor
import dev.kolib.app.log.AppLog
import dev.kolib.app.saf.SafTree
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random

/**
 * Puts a changed database on the Kobo by rewriting only the parts (pages) that changed, which is
 * much quicker than copying the whole file. It's done the way SQLite itself does it:
 *
 *  1. the pages about to change are saved, as they are now, in a "rollback journal" beside the database;
 *  2. the new pages are written over the old ones;
 *  3. the journal is deleted, and that's the moment the change takes effect.
 *
 * If the Kobo is unplugged before step 3, its own SQLite finds the journal the next time it opens the
 * database and puts the saved pages back, so the database is as it was before. The journal's layout is
 * SQLite's: https://www.sqlite.org/fileformat.html#the_rollback_journal
 */
internal object PageWriter {

    enum class Result {
        /** The Kobo's database now matches the new one. */
        DONE,

        /** Nothing on the Kobo was changed; copy the whole file instead. */
        NOT_POSSIBLE,
    }

    const val JOURNAL_NAME = KoboDatabaseWriter.DB_NAME + "-journal"
    private const val SECTOR = 512
    private val MAGIC = byteArrayOf(0xd9.toByte(), 0xd5.toByte(), 0x05, 0xf9.toByte(), 0x20, 0xa1.toByte(), 0x63, 0xd7.toByte())

    /** The size of a database's pages, from its header, or null if it isn't one SQLite would accept. */
    fun pageSize(file: File): Int? = RandomAccessFile(file, "r").use { r ->
        if (r.length() < 100) return null
        r.seek(16)
        val v = (r.read() shl 8) or r.read()
        val size = if (v == 1) 65536 else v
        size.takeIf { it in 512..65536 && it and (it - 1) == 0 }
    }

    /** A journal is "hot" (still to be played back) when it has something in it and its header isn't blanked out. */
    fun isHot(journal: InputStream): Boolean = journal.use { it.read() > 0 }

    /** SQLite's checksum for a page in the journal: the nonce plus every 200th byte, counted from the end. */
    fun checksum(nonce: Int, page: ByteArray): Int {
        var sum = nonce
        var i = page.size - 200
        while (i > 0) {
            sum += page[i].toInt() and 0xff
            i -= 200
        }
        return sum
    }

    /** The pages (counted from 0) of the old file that change or go, kept in the journal, and those of the new file to write. */
    class Changes(val pageSize: Int, val oldPages: Int, val newPages: Int, val saved: List<Int>, val written: List<Int>) {
        /** Saving and writing pages costs about as much as copying the whole file once they add up to it. */
        val worthIt: Boolean get() = saved.size + written.size < maxOf(oldPages, newPages)
    }

    fun changes(old: File, new: File): Changes? {
        val size = pageSize(old) ?: return null
        if (pageSize(new) != size || old.length() % size != 0L || new.length() % size != 0L) return null
        val oldPages = (old.length() / size).toInt()
        val newPages = (new.length() / size).toInt()
        val saved = ArrayList<Int>()
        val written = ArrayList<Int>()
        RandomAccessFile(old, "r").use { o ->
            RandomAccessFile(new, "r").use { n ->
                val a = ByteArray(size)
                val b = ByteArray(size)
                for (p in 0 until maxOf(oldPages, newPages)) {
                    val inOld = p < oldPages
                    val inNew = p < newPages
                    if (inOld) o.readFully(a)
                    if (inNew) n.readFully(b)
                    when {
                        inOld && inNew -> if (!a.contentEquals(b)) {
                            saved += p
                            written += p
                        }
                        inOld -> saved += p
                        else -> written += p
                    }
                }
            }
        }
        return Changes(size, oldPages, newPages, saved, written)
    }

    /** Writes a journal that puts [pages] (page and its bytes as they are now) back, and the file back to [oldPages] pages. */
    fun writeJournal(to: File, pageSize: Int, oldPages: Int, pages: List<Pair<Int, ByteArray>>, nonce: Int = Random.nextInt()) {
        to.outputStream().buffered(256 * 1024).use { out ->
            val header = ByteBuffer.allocate(SECTOR) // big-endian, and the rest of the sector stays zeros
            header.put(MAGIC).putInt(pages.size).putInt(nonce).putInt(oldPages).putInt(SECTOR).putInt(pageSize)
            out.write(header.array())
            val four = ByteBuffer.allocate(4)
            fun int(v: Int) {
                four.clear()
                four.putInt(v)
                out.write(four.array())
            }
            for ((index, bytes) in pages) {
                int(index + 1) // SQLite counts pages from 1
                out.write(bytes)
                int(checksum(nonce, bytes))
            }
        }
    }

    private fun RandomAccessFile.page(index: Int, size: Int): ByteArray =
        ByteArray(size).also {
            seek(index.toLong() * size)
            readFully(it)
        }

    private fun FileChannel.readPage(index: Int, size: Int): ByteArray {
        val buffer = ByteBuffer.allocate(size)
        var at = index.toLong() * size
        while (buffer.hasRemaining()) {
            val n = read(buffer, at)
            if (n < 0) throw IOException(tr("The Kobo's database is shorter than expected."))
            at += n
        }
        return buffer.array()
    }

    private fun FileChannel.writePage(index: Int, bytes: ByteArray) {
        val buffer = ByteBuffer.wrap(bytes)
        var at = index.toLong() * bytes.size
        while (buffer.hasRemaining()) at += write(buffer, at)
    }

    /**
     * Makes the Kobo's database (document [dbDocId] in the folder [koboDirId]) match [new], given that
     * it now matches [old]. Returns [Result.NOT_POSSIBLE], having changed nothing, when it can't be
     * done this way. Throws if it started and couldn't finish; the Kobo's database is then put back as
     * it was, either here or, if the Kobo can't be reached, by the Kobo itself when it next opens it.
     */
    fun write(tree: SafTree, koboDirId: String, dbDocId: String, old: File, new: File, scratch: File): Result {
        val changes = changes(old, new) ?: return Result.NOT_POSSIBLE
        if (changes.saved.isEmpty() && changes.written.isEmpty()) return Result.DONE
        if (!changes.worthIt) {
            AppLog.i("KoboDb", "Most of the database changed (${changes.written.size} of ${changes.newPages} pages); copying all of it.")
            return Result.NOT_POSSIBLE
        }
        val pfd = tree.openDescriptor(dbDocId, "rw") ?: return Result.NOT_POSSIBLE
        return pfd.use { writeWith(it, tree, koboDirId, old, new, changes, scratch) }
    }

    private fun writeWith(pfd: ParcelFileDescriptor, tree: SafTree, koboDirId: String, old: File, new: File, c: Changes, scratch: File): Result {
        val fd = pfd.fileDescriptor
        // Positioned reads and writes. The streams aren't closed here: that could close the descriptor under the other.
        val reader = FileInputStream(fd).channel
        val writer = FileOutputStream(fd).channel
        val size = c.pageSize
        if (reader.size() != old.length()) return Result.NOT_POSSIBLE

        // The pages about to change must be on the Kobo just as the app read them, and so must the first page (the header).
        val toSave = c.saved.toHashSet()
        val originals = HashMap<Int, ByteArray>()
        RandomAccessFile(old, "r").use { o ->
            for (p in (listOf(0) + c.saved).distinct()) {
                val onKobo = reader.readPage(p, size)
                if (!onKobo.contentEquals(o.page(p, size))) {
                    AppLog.w("KoboDb", "Page ${p + 1} of the Kobo's database isn't as it was read; copying the whole file instead.")
                    return Result.NOT_POSSIBLE
                }
                if (p in toSave) originals[p] = onKobo
            }
        }
        val saved = c.saved.map { it to originals.getValue(it) }

        // 1. The journal, on the drive itself before anything else changes.
        scratch.mkdirs()
        val journal = File(scratch, JOURNAL_NAME)
        writeJournal(journal, size, c.oldPages, saved)
        val journalId = putJournal(tree, koboDirId, journal)
        journal.delete()
        if (journalId == null) return Result.NOT_POSSIBLE

        // 2. The new pages. From here on, a failure puts the old ones back.
        try {
            RandomAccessFile(new, "r").use { n ->
                for (p in c.written) writer.writePage(p, n.page(p, size))
                if (c.newPages < c.oldPages) writer.truncate(new.length())
                fd.sync()
                if (reader.size() != new.length()) throw IOException(tr("The Kobo's database came out the wrong size."))
                for (p in c.written) {
                    if (!reader.readPage(p, size).contentEquals(n.page(p, size))) {
                        throw IOException(tr("Part of the Kobo's database didn't read back as it was written."))
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("KoboDb", "Rewriting pages of the Kobo's database failed; putting the old ones back", e)
            putBack(tree, journalId, reader, writer, pfd, saved, old.length())
            throw e
        }

        // 3. Deleting the journal makes it final.
        if (!dropJournal(tree, journalId)) {
            putBack(tree, journalId, reader, writer, pfd, saved, old.length())
            throw IOException(tr("The journal beside the Kobo's database couldn't be removed, so the change was undone."))
        }
        AppLog.i("KoboDb", "Rewrote ${c.written.size} of ${c.newPages} pages of the Kobo's database in place.")
        return Result.DONE
    }

    /** Writes the journal beside the database and waits until it's on the drive. Null, with nothing left behind, if that can't be done. */
    private fun putJournal(tree: SafTree, koboDirId: String, journal: File): String? {
        val id = tree.child(koboDirId, JOURNAL_NAME)?.docId
            ?: tree.createFile(koboDirId, "application/octet-stream", JOURNAL_NAME)
            ?: return null
        val ok = try {
            // The Kobo's SQLite only looks for it under exactly this name.
            tree.stat(id)?.name == JOURNAL_NAME &&
                tree.openDescriptor(id, "wt")?.use { p ->
                    val out = FileOutputStream(p.fileDescriptor)
                    journal.inputStream().use { it.copyTo(out, 256 * 1024) }
                    out.flush()
                    p.fileDescriptor.sync() // on the drive before any page of the database changes
                    true
                } == true &&
                tree.stat(id)?.size.let { it == null || it == 0L || it == journal.length() }
        } catch (e: IOException) {
            AppLog.w("KoboDb", "Couldn't write the journal beside the Kobo's database", e)
            false
        }
        if (!ok) {
            runCatching { tree.delete(id) }
            return null
        }
        return id
    }

    /** Deletes the journal, or failing that blanks out its header, which SQLite takes to mean there's nothing to undo. */
    private fun dropJournal(tree: SafTree, journalId: String): Boolean {
        if (runCatching { tree.delete(journalId) }.getOrDefault(false)) return true
        return runCatching {
            tree.openDescriptor(journalId, "rw")?.use { p ->
                FileOutputStream(p.fileDescriptor).channel.write(ByteBuffer.allocate(28), 0)
                p.fileDescriptor.sync()
                true
            } ?: false
        }.getOrDefault(false)
    }

    /** Undoes a part-made change: the saved pages back, the old size back, then the journal goes. */
    private fun putBack(
        tree: SafTree, journalId: String, reader: FileChannel, writer: FileChannel, pfd: ParcelFileDescriptor,
        saved: List<Pair<Int, ByteArray>>, oldLength: Long,
    ) {
        try {
            for ((p, bytes) in saved) writer.writePage(p, bytes)
            if (reader.size() != oldLength) writer.truncate(oldLength)
            pfd.fileDescriptor.sync()
            for ((p, bytes) in saved) if (!reader.readPage(p, bytes.size).contentEquals(bytes)) throw IOException("page ${p + 1} didn't go back")
            if (reader.size() != oldLength) throw IOException("the old size didn't come back")
            if (!dropJournal(tree, journalId)) throw IOException("the journal couldn't be removed")
            AppLog.i("KoboDb", "Undid a part-made change to the Kobo's database.")
        } catch (e: Exception) {
            // The journal stays, and the Kobo plays it back itself the next time it opens its database.
            AppLog.e("KoboDb", "Couldn't undo a part-made change to the Kobo's database; the Kobo will undo it itself", e)
            throw IOException(tr("The Kobo stopped answering while its database was being updated. The Kobo undoes the unfinished change itself once it's ejected."), e)
        }
    }
}
