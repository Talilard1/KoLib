package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Read access to an EPUB on disk. */
class EpubFile private constructor(val file: File, private val zip: ZipFile) : Closeable {

    val opfPath: String
    val opf: OpfDocument

    init {
        try {
            val container = readText("META-INF/container.xml")
                ?: throw EpubException(tr("This file isn't an EPUB (it has no META-INF/container.xml)."))
            val rootfile = try {
                Xml.parse(container).root.descendants().firstOrNull { it.localName.equals("rootfile", true) }
            } catch (e: XmlException) {
                throw EpubException(tr("The book's container.xml is damaged."))
            }
            opfPath = rootfile?.attributes?.get("full-path")?.trimStart('/')
                ?: throw EpubException(tr("The book's container.xml doesn't say where its package file is."))
            val opfText = readText(opfPath) ?: throw EpubException(tr("The book's package file ({0}) is missing.", opfPath))
            opf = OpfDocument(opfText)
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }

    val opfDir: String get() = opfPath.substringBeforeLast('/', "")

    fun entryNames(): List<String> = zip.entries().asSequence().map { it.name }.toList()

    fun has(path: String): Boolean = zip.getEntry(path) != null

    fun readBytes(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }

    fun readText(path: String): String? = readBytes(path)?.let { decodeText(it) }

    /** A manifest href (relative to the package file, or to [relativeTo]) as a path inside the zip. */
    fun resolve(href: String, relativeTo: String = opfPath): String = resolvePath(relativeTo.substringBeforeLast('/', ""), href)

    /** Zip paths of the book's text files, in manifest order. */
    fun contentDocuments(): List<String> = opf.items.filter { it.isContent }.map { resolve(it.href) }

    data class Cover(val path: String, val mediaType: String, val declared: Boolean)

    /** Finds the cover image the way most readers do. */
    fun findCover(): Cover? {
        opf.declaredCoverItem()?.let { item ->
            val path = resolve(item.href)
            if (has(path)) return Cover(path, item.mediaType, declared = true)
        }
        // The cover page named in the guide, then the first page of the book.
        val pages = listOfNotNull(opf.guideCoverHref()?.let { resolve(it) }) + opf.spineHrefs().take(1).map { resolve(it) }
        for (page in pages) {
            val html = readText(page) ?: continue
            firstImageReference(html)?.let { ref ->
                val path = resolvePath(page.substringBeforeLast('/', ""), ref)
                if (has(path)) return Cover(path, mediaTypeFor(path), declared = false)
            }
        }
        opf.guessedCoverItem()?.let { item ->
            val path = resolve(item.href)
            if (has(path)) return Cover(path, item.mediaType, declared = false)
        }
        return null
    }

    override fun close() = zip.close()

    companion object {
        fun open(file: File): EpubFile {
            val zip = try {
                ZipFile(file)
            } catch (e: ZipException) {
                throw EpubException(tr("This file isn't an EPUB (it isn't a zip archive)."))
            } catch (e: IllegalArgumentException) {
                throw EpubException(tr("This EPUB uses unusual file names inside it that can't be read."))
            } catch (e: IOException) {
                throw EpubException(tr("The file couldn't be opened: {0}", e.message))
            }
            return EpubFile(file, zip)
        }

        fun decodeText(bytes: ByteArray): String {
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
            return String(bytes, start, bytes.size - start, Charsets.UTF_8)
        }

        fun resolvePath(baseDir: String, href: String): String {
            val clean = try {
                URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                href.substringBefore('#')
            }
            if (clean.startsWith("/")) return clean.trimStart('/')
            val parts = ArrayDeque<String>()
            if (baseDir.isNotEmpty()) baseDir.split('/').filter { it.isNotEmpty() }.forEach { parts.addLast(it) }
            for (p in clean.split('/')) {
                when (p) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull()
                    else -> parts.addLast(p)
                }
            }
            return parts.joinToString("/")
        }

        /** Relative href from the folder of [fromPath] to [toPath] (both zip paths). */
        fun relativeHref(fromPath: String, toPath: String): String {
            val from = fromPath.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }
            val to = toPath.split('/').filter { it.isNotEmpty() }
            var common = 0
            while (common < from.size && common < to.size - 1 && from[common] == to[common]) common++
            return (List(from.size - common) { ".." } + to.drop(common)).joinToString("/")
        }

        private val imgSrc = Regex("<(?:\\w+:)?img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        private val svgImage = Regex("<(?:\\w+:)?image\\b[^>]*?\\b(?:xlink:)?href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        fun firstImageReference(html: String): String? {
            val a = imgSrc.find(html)
            val b = svgImage.find(html)
            return listOfNotNull(a, b).minByOrNull { it.range.first }?.groupValues?.get(1)
        }

        fun mediaTypeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "xhtml", "xht" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            else -> "application/octet-stream"
        }
    }
}
