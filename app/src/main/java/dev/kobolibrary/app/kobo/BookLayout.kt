package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.epub.BookFiles
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.Contents
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.OpfDocument
import dev.kobolibrary.app.epub.Xml
import dev.kobolibrary.app.saf.SafTree
import java.io.File
import java.io.FileInputStream

/**
 * What the Kobo records about a book's insides when it first takes it in: its chapters in reading
 * order and its table of contents. A new file with the same layout can take an old one's place while
 * the Kobo keeps its record of the book (reading progress, highlights, collections); a file whose
 * layout changed has to go to the Kobo as a new book, or the Kobo's record would no longer fit it.
 * [cover] tells covers apart (a hash of the cover picture), since the Kobo keeps showing the cover it
 * remembers for a book until it restarts.
 */
data class BookLayout(val chapters: List<String>, val contents: List<String>, val cover: String? = null) {

    /** The same chapters and contents: the Kobo's record of the book fits [other] too. */
    fun sameInsides(other: BookLayout): Boolean = chapters == other.chapters && contents == other.contents


    companion object {
        /** The layout of a book file on the tablet, or null if it can't be read. */
        fun of(file: File): BookLayout? = runCatching {
            FileInputStream(file).use { read(ZipPeek.of(it.channel)) }
        }.getOrNull()

        /** The layout of the book file at [path] on the Kobo, read with a few small reads. */
        fun onKobo(tree: SafTree, path: String): BookLayout? = runCatching {
            tree.openFileDescriptor(path)?.use { pfd -> FileInputStream(pfd.fileDescriptor).use { read(ZipPeek.of(it.channel)) } }
        }.getOrNull()

        fun read(zip: ZipPeek): BookLayout? {
            val container = zip.read("META-INF/container.xml")?.let { EpubFile.decodeText(it) } ?: return null
            val opfPath = Xml.parse(container).root.descendants().firstOrNull { it.localName.equals("rootfile", true) }
                ?.attributes?.get("full-path")?.trimStart('/') ?: return null
            val opfText = zip.read(opfPath)?.let { EpubFile.decodeText(it) } ?: return null
            val opf = OpfDocument(opfText)
            val dir = opfPath.substringBeforeLast('/', "")
            // Only the package file and the contents files are read; the chapters themselves needn't be.
            val coverPath = (opf.declaredCoverItem() ?: opf.guessedCoverItem())?.let { EpubFile.resolvePath(dir, it.href) }
            val wanted = buildSet {
                add(opfPath)
                listOfNotNull(opf.ncxItem(), opf.navItem()).forEach { add(EpubFile.resolvePath(dir, it.href)) }
            }
            val files = object : BookFiles {
                override val opfPath: String = opfPath
                override fun bytes(path: String): ByteArray? = if (path in wanted) zip.read(path) else null
                override fun has(path: String): Boolean = zip.entries.containsKey(path)
                override fun names(): List<String> = zip.entries.keys.toList()
            }
            val structure = BookStructure(files)
            val cover = coverPath?.let { zip.read(it, limit = 40L * 1024 * 1024) }?.let { bytes ->
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            return BookLayout(
                chapters = structure.chapters.map { it.path },
                contents = Contents.flatten(structure.toc).map { (node, level) -> "$level|${node.path}#${node.fragment.orEmpty()}|${node.title}" },
                cover = cover,
            )
        }
    }
}
