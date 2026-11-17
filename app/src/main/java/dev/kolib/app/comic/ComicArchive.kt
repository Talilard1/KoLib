package dev.kolib.app.comic

import dev.kolib.app.convert.TextDecoding
import dev.kolib.app.convert.HtmlSoup
import dev.kolib.app.convert.TextConverter
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.epub.Xml
import dev.kolib.app.tr
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipFile

/**
 * A comic book file (CBZ, CBR, CB7): its pictures, in order, read from the file as they're wanted. Nothing
 * is unpacked or held whole, so a comic of several gigabytes opens as quickly as a small one.
 */
interface ComicArchive : Closeable {
    /** The pages: the pictures' names in the file, in reading order (page2 before page10). */
    val pages: List<String>

    /** Page [index]'s picture file. */
    fun bytes(index: Int): ByteArray

    /** The comic's ComicInfo.xml (its details), if it has one. */
    val comicInfo: ByteArray?
}

object ComicArchives {
    private val pictures = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif")

    /** Whether [name] (a file in a comic) is one of its pages: a picture, not a hidden or system file. */
    fun isPage(name: String): Boolean {
        val n = name.replace('\\', '/')
        val file = n.substringAfterLast('/')
        return file.substringAfterLast('.', "").lowercase() in pictures && !file.startsWith(".") &&
            !n.contains("__MACOSX/") && file.isNotEmpty()
    }

    fun isComicInfo(name: String) = name.replace('\\', '/').substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)

    /** Numbers in names sorted as numbers: page2 before page10. */
    fun natural(a: String, b: String): Int {
        val re = Regex("""\d+|\D+""")
        val x = re.findAll(a.lowercase()).map { it.value }.toList()
        val y = re.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) (p.toBigInteger().compareTo(q.toBigInteger())).takeIf { it != 0 } ?: p.length.compareTo(q.length) else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }

    /** What kind of comic file [file] is, from its first bytes: "CBZ", "CBR", "CB7", or null. */
    fun kindOf(file: File): String? {
        val head = ByteArray(8)
        val n = runCatching { RandomAccessFile(file, "r").use { it.read(head) } }.getOrDefault(0)
        if (n < 4) return null
        return when {
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> "CBZ"
            head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() && head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte() -> "CBR"
            n >= 6 && head[0] == '7'.code.toByte() && head[1] == 'z'.code.toByte() && (head[2].toInt() and 0xFF) == 0xBC && (head[3].toInt() and 0xFF) == 0xAF -> "CB7"
            else -> null
        }
    }

    /**
     * Opens [file] as a comic: a zip (CBZ) is read straight from its own index of what's in it; anything
     * else (a RAR, a 7z) through libarchive, as Mihon reads them. [libArchive] makes the reader for those
     * (tests have none: it's native code).
     */
    fun open(file: File, libArchive: ((File) -> ComicArchive)? = { LibArchiveComic(it) }): ComicArchive {
        val kind = kindOf(file)
        if (kind == "CBZ") {
            try {
                return ZipComic(file)
            } catch (e: IOException) {
                // A zip Java can't read (an unusual one): libarchive may.
                if (libArchive == null) throw e
            }
        }
        val other = libArchive ?: throw IOException(tr("This kind of comic file can't be opened here."))
        return other(file)
    }
}

/** A CBZ: a zip, each page read from where the zip's index says it is. */
class ZipComic(file: File) : ComicArchive {
    // Names not marked as UTF-8 are in the old DOS character set, as zip programs wrote them.
    private val zip = try {
        ZipFile(file, Charset.forName("CP437"))
    } catch (e: Exception) {
        throw IOException(e.message ?: tr("This comic book file can't be read."), e)
    }

    override val pages: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory && ComicArchives.isPage(it.name) }
        .map { it.name }
        .sortedWith(ComicArchives::natural)
        .toList()

    override val comicInfo: ByteArray? by lazy {
        zip.entries().asSequence().firstOrNull { !it.isDirectory && ComicArchives.isComicInfo(it.name) }
            ?.let { e -> runCatching { zip.getInputStream(e).use { it.readBytes() } }.getOrNull() }
    }

    override fun bytes(index: Int): ByteArray {
        val e = zip.getEntry(pages[index]) ?: throw IOException(tr("Page {0} isn't in the file.", index + 1))
        return zip.getInputStream(e).use { it.readBytes() }
    }

    override fun close() = zip.close()
}

/**
 * A CBR, CB7 (or a zip Java can't read), through libarchive. It only reads a file from the start, so
 * pages are found by going through the file: one reader is kept where the last page read was, and reading
 * on from there (as a comic is read) costs nothing extra; going back starts again from the beginning.
 */
class LibArchiveComic(private val file: File) : ComicArchive {
    private val lock = Any()
    /** Each page's place among the files in the archive, as libarchive goes through them. */
    private val position = HashMap<String, Int>()
    override val pages: List<String>
    override var comicInfo: ByteArray? = null
        private set

    private var reader = 0L
    /** The file the reader is at: its place among the archive's files (-1 before the first). */
    private var at = -1

    init {
        val names = ArrayList<String>()
        val a = openReader()
        try {
            var i = 0
            while (true) {
                val entry = me.zhanghai.android.libarchive.Archive.readNextHeader(a)
                if (entry == 0L) break
                val name = nameOf(entry)
                val isFile = me.zhanghai.android.libarchive.ArchiveEntry.filetype(entry) == me.zhanghai.android.libarchive.ArchiveEntry.AE_IFREG
                if (isFile && name != null) {
                    if (ComicArchives.isComicInfo(name) && comicInfo == null) comicInfo = readData(a)
                    else if (ComicArchives.isPage(name) && name !in position) {
                        position[name] = i
                        names += name
                    }
                }
                i++
            }
        } finally {
            freeReader(a)
        }
        pages = names.sortedWith(ComicArchives::natural)
    }

    private fun nameOf(entry: Long): String? =
        me.zhanghai.android.libarchive.ArchiveEntry.pathnameUtf8(entry)
            ?: me.zhanghai.android.libarchive.ArchiveEntry.pathname(entry)?.let { String(it, Charsets.ISO_8859_1) }

    private fun openReader(): Long {
        val a = me.zhanghai.android.libarchive.Archive.readNew()
        try {
            // Names in UTF-8, as Mihon reads them.
            runCatching { me.zhanghai.android.libarchive.Archive.setCharset(a, "UTF-8".toByteArray()) }
            me.zhanghai.android.libarchive.Archive.readSupportFilterAll(a)
            me.zhanghai.android.libarchive.Archive.readSupportFormatAll(a)
            // Read from the file itself, which lets libarchive skip over what it doesn't need.
            me.zhanghai.android.libarchive.Archive.readOpenFileName(a, file.path.toByteArray(), 128 * 1024)
            return a
        } catch (e: Exception) {
            freeReader(a)
            throw IOException(e.message ?: tr("This comic book file can't be read."), e)
        }
    }

    private fun freeReader(a: Long) {
        runCatching { me.zhanghai.android.libarchive.Archive.readFree(a) }
    }

    /** The rest of the file the reader is at. */
    private fun readData(a: Long): ByteArray {
        val out = ByteArrayOutputStream(256 * 1024)
        val buffer = ByteBuffer.allocateDirect(256 * 1024)
        val chunk = ByteArray(256 * 1024)
        while (true) {
            buffer.clear()
            me.zhanghai.android.libarchive.Archive.readData(a, buffer)
            val n = buffer.position()
            if (n <= 0) break
            buffer.flip()
            buffer.get(chunk, 0, n)
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    override fun bytes(index: Int): ByteArray = synchronized(lock) {
        val want = position[pages[index]] ?: throw IOException(tr("Page {0} isn't in the file.", index + 1))
        try {
            if (reader == 0L || at >= want) {
                if (reader != 0L) freeReader(reader)
                reader = 0L
                reader = openReader()
                at = -1
            }
            while (at < want) {
                val entry = me.zhanghai.android.libarchive.Archive.readNextHeader(reader)
                if (entry == 0L) throw IOException(tr("Page {0} isn't in the file.", index + 1))
                at++
                if (at == want) return readData(reader)
            }
            throw IOException(tr("Page {0} isn't in the file.", index + 1))
        } catch (e: IOException) {
            // Start afresh next time.
            if (reader != 0L) freeReader(reader)
            reader = 0L
            at = -1
            throw e
        }
    }

    override fun close() = synchronized(lock) {
        if (reader != 0L) freeReader(reader)
        reader = 0L
    }
}

/** A comic's ComicInfo.xml, as book details. */
object ComicInfo {
    class Info(val meta: BookMetadata, val rightToLeft: Boolean)

    /** The details in [xml] (ComicInfo.xml) for the comic file named [fileName], or just its name for a title. */
    fun read(xml: ByteArray?, fileName: String): Info {
        var meta = BookMetadata(title = TextConverter.titleFromName(fileName))
        var rtl = false
        if (xml != null) runCatching {
            val root = Xml.parse(TextDecoding.decode(xml)).root
            fun f(name: String) = root.elements().firstOrNull { it.localName == name }?.text()?.trim()?.ifEmpty { null }
            meta = meta.copy(
                title = f("Title") ?: listOfNotNull(f("Series"), f("Number")?.let { "#$it" }).joinToString(" ").ifEmpty { meta.title },
                authors = listOfNotNull(f("Writer"), f("Penciller")).flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                series = f("Series"),
                seriesIndex = f("Number")?.toDoubleOrNull(),
                publisher = f("Publisher"),
                language = f("LanguageISO"),
                description = f("Summary")?.let { "<p>${HtmlSoup.escapeText(it)}</p>" },
                published = f("Year"),
                tags = f("Genre")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            )
            rtl = f("Manga")?.equals("YesAndRightToLeft", ignoreCase = true) == true
        }
        return Info(meta, rtl)
    }
}
