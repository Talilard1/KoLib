package dev.kolib.app.kobo

import dev.kolib.app.tr
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.OpfDocument
import dev.kolib.app.epub.Xml
import dev.kolib.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import kotlin.coroutines.coroutineContext

/**
 * Reads single files out of a zip with a few positioned reads, without going through the rest of it.
 * Used to read a book's package file straight off the Kobo, where reading whole books would be slow.
 */
class ZipPeek(private val size: Long, private val readAt: (position: Long, buffer: ByteArray, length: Int) -> Int) {

    data class Entry(val name: String, val method: Int, val compressedSize: Long, val size: Long, val localHeaderOffset: Long)

    val entries: Map<String, Entry> = readDirectory()

    private fun readFully(position: Long, length: Int): ByteArray {
        val buf = ByteArray(length)
        var done = 0
        while (done < length) {
            val chunk = ByteArray(length - done)
            val n = readAt(position + done, chunk, length - done)
            if (n <= 0) throw IOException(tr("The file ended early"))
            System.arraycopy(chunk, 0, buf, done, n)
            done += n
        }
        return buf
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, at: Int) = (u16(b, at).toLong()) or (u16(b, at + 2).toLong() shl 16)

    private fun readDirectory(): Map<String, Entry> {
        if (size < 22) throw IOException(tr("Not a zip file"))
        val tailLength = minOf(size, 65_557L).toInt()
        val tail = readFully(size - tailLength, tailLength)
        var eocd = -1
        for (i in tailLength - 22 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() && tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()) {
                eocd = i
                break
            }
        }
        if (eocd < 0) throw IOException(tr("Not a zip file (no directory)"))
        val count = u16(tail, eocd + 10)
        val dirSize = u32(tail, eocd + 12)
        val dirOffset = u32(tail, eocd + 16)
        if (dirOffset + dirSize > size || dirSize > 16L * 1024 * 1024) throw IOException(tr("The zip's directory is damaged"))
        val dir = readFully(dirOffset, dirSize.toInt())
        val out = LinkedHashMap<String, Entry>()
        var p = 0
        repeat(count) {
            if (p + 46 > dir.size || u32(dir, p) != 0x02014b50L) return out
            val flags = u16(dir, p + 8)
            val method = u16(dir, p + 10)
            val compressed = u32(dir, p + 20)
            val plain = u32(dir, p + 24)
            val nameLength = u16(dir, p + 28)
            val extraLength = u16(dir, p + 30)
            val commentLength = u16(dir, p + 32)
            val local = u32(dir, p + 42)
            val nameBytes = dir.copyOfRange(p + 46, minOf(dir.size, p + 46 + nameLength))
            val name = if (flags and 0x800 != 0) String(nameBytes, Charsets.UTF_8) else String(nameBytes, Charsets.UTF_8)
            out[name] = Entry(name, method, compressed, plain, local)
            p += 46 + nameLength + extraLength + commentLength
        }
        return out
    }

    /** The contents of [name], or null if it isn't there (or is bigger than [limit]). */
    fun read(name: String, limit: Long = 8L * 1024 * 1024): ByteArray? {
        val e = entries[name] ?: return null
        if (e.size > limit || e.compressedSize > limit) return null
        val header = readFully(e.localHeaderOffset, 30)
        if (u32(header, 0) != 0x04034b50L) throw IOException(tr("Damaged zip entry {0}", name))
        val dataStart = e.localHeaderOffset + 30 + u16(header, 26) + u16(header, 28)
        val data = readFully(dataStart, e.compressedSize.toInt())
        return when (e.method) {
            0 -> data
            8 -> {
                val inflater = Inflater(true)
                try {
                    inflater.setInput(data)
                    val out = ByteArray(e.size.toInt())
                    var n = 0
                    while (n < out.size && !inflater.finished()) {
                        val got = inflater.inflate(out, n, out.size - n)
                        if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                        n += got
                    }
                    if (n == out.size) out else out.copyOf(n)
                } finally {
                    inflater.end()
                }
            }
            else -> null
        }
    }

    companion object {
        fun of(channel: FileChannel): ZipPeek = ZipPeek(channel.size()) { pos, buf, len ->
            channel.read(ByteBuffer.wrap(buf, 0, len), pos)
        }

        fun of(file: File): ZipPeek {
            val ch = FileInputStream(file).channel
            return of(ch)
        }

        /** The tags (and Calibre rating, 0 to 10) in a book's package file. */
        fun bookTags(zip: ZipPeek): Pair<List<String>, Int?>? {
            val container = zip.read("META-INF/container.xml")?.let { EpubFile.decodeText(it) } ?: return null
            val opfPath = Xml.parse(container).root.descendants().firstOrNull { it.localName.equals("rootfile", true) }
                ?.attributes?.get("full-path")?.trimStart('/') ?: return null
            val opf = zip.read(opfPath)?.let { EpubFile.decodeText(it) } ?: return null
            val meta = OpfDocument(opf).readMetadata()
            return meta.tags to meta.rating
        }
    }
}

/** Tags read from the Kobo's book files, kept between connections. Keyed by the file's path, lower case. */
class KoboTagStore(private val file: File) {

    data class Entry(val size: Long, val tags: List<String>, val rating: Int? = null)

    fun load(): Map<String, Entry> = try {
        if (!file.exists()) emptyMap() else {
            val o = JSONObject(file.readText())
            o.keys().asSequence().associateWith { k ->
                val e = o.getJSONObject(k)
                Entry(
                    e.optLong("size"),
                    e.optJSONArray("tags")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
                    if (e.has("rating")) e.getInt("rating") else null,
                )
            }
        }
    } catch (e: Exception) {
        AppLog.w("Kobo", "The saved tags of the Kobo's books couldn't be read", e)
        emptyMap()
    }

    fun save(entries: Map<String, Entry>) {
        try {
            file.parentFile?.mkdirs()
            val o = JSONObject()
            for ((k, e) in entries) {
                o.put(k, JSONObject().apply {
                    put("size", e.size)
                    put("tags", JSONArray(e.tags))
                    e.rating?.let { put("rating", it) }
                })
            }
            val tmp = File(file.path + ".part")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            AppLog.w("Kobo", "Couldn't save the tags of the Kobo's books", e)
        }
    }

    fun clear() {
        file.delete()
    }
}

/** Reads the tags of the Kobo's sideloaded books from their files, skipping ones already known. */
object KoboTagReader {

    fun key(path: String) = path.lowercase()

    /**
     * Reads tags for [books] that [known] doesn't have yet (or whose file has changed size).
     * [onProgress] gets (done, total) and the entries read so far, every few books.
     */
    suspend fun read(
        session: KoboSession,
        books: List<KoboBook>,
        known: Map<String, KoboTagStore.Entry>,
        onProgress: (done: Int, total: Int, found: Map<String, KoboTagStore.Entry>) -> Unit,
    ): Map<String, KoboTagStore.Entry> = withContext(Dispatchers.IO) {
        val wanted = books.filter { b ->
            val path = b.relativePath ?: return@filter false
            b.isSideloaded && (b.format == "EPUB" || b.format == "KEPUB") &&
                known[key(path)]?.size != (b.fileSize ?: -1L)
        }
        val found = LinkedHashMap(known)
        // Books no longer on the Kobo are forgotten.
        val present = books.mapNotNull { it.relativePath?.let(::key) }.toSet()
        found.keys.retainAll(present)
        var done = 0
        var failed = 0
        for (b in wanted) {
            coroutineContext.ensureActive()
            val path = b.relativePath ?: continue
            try {
                session.tree.openFileDescriptor(path)?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        val zip = ZipPeek.of(input.channel)
                        val (tags, rating) = ZipPeek.bookTags(zip) ?: (emptyList<String>() to null)
                        found[key(path)] = KoboTagStore.Entry(b.fileSize ?: -1L, tags, rating)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed++
                // Remembered as having no tags, so it isn't tried again until the file changes.
                found[key(path)] = KoboTagStore.Entry(b.fileSize ?: -1L, emptyList())
                if (failed <= 3) AppLog.w("Kobo", "Couldn't read the tags of $path: ${e.message}")
            }
            done++
            if (done % 25 == 0 || done == wanted.size) onProgress(done, wanted.size, found)
        }
        if (wanted.isNotEmpty()) {
            AppLog.i("Kobo", "Read the tags of ${wanted.size - failed} book(s) on the Kobo" + if (failed > 0) " ($failed couldn't be read)." else ".")
        }
        found
    }
}
