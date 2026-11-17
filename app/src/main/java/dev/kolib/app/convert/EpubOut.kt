package dev.kolib.app.convert

import dev.kolib.app.tr
import dev.kolib.app.epub.BookAssembly
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.epub.TocNode
import dev.kolib.app.epub.Xml
import java.io.File

/** A file that couldn't be turned into a book, with why (said to the person). */
class ConversionException(message: String) : Exception(message)

/**
 * A book being made from another kind of file: its pages, pictures, styles and details, written out as
 * an EPUB the rest of the app reads, edits and sends like any other.
 */
class EpubOut(var meta: BookMetadata) {

    private val parts = ArrayList<BookAssembly.Part>()
    private val spine = ArrayList<BookAssembly.SpineEntry>()
    private val taken = HashSet<String>()
    private var pageCount = 0
    private var cover: String? = null

    /** The contents, as pages are added (or set outright, for a book that has its own). */
    var toc: MutableList<TocNode> = ArrayList()

    val lang: String get() = meta.language?.takeIf { it.isNotBlank() } ?: "en"

    /** Style sheets every page links to (as seen from a page). */
    val styles = ArrayList<String>()

    init {
        styles += style("book.css", BOOK_CSS)
    }

    private fun unique(dir: String, name: String): String {
        val clean = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.').ifEmpty { "file" }
        val base = clean.substringBeforeLast('.', clean)
        val ext = clean.substringAfterLast('.', "")
        var candidate = clean
        var n = 2
        while (!taken.add("$dir/$candidate".lowercase())) candidate = "$base-${n++}" + if (ext.isNotEmpty()) ".$ext" else ""
        return "$dir/$candidate"
    }

    /** Adds a picture (named by [name]'s extension, or its contents); returns its address from a page. */
    fun image(name: String, bytes: ByteArray, asCover: Boolean = false): String {
        val type = imageType(name, bytes)
        val named = if (name.substringAfterLast('.', "").lowercase() in IMAGE_EXT) name else name.substringBeforeLast('.') + "." + type.second
        val path = unique("OEBPS/images", named)
        parts += BookAssembly.Part(path, type.first, bytes, if (asCover) setOf("cover-image") else emptySet())
        if (asCover && cover == null) cover = path
        return "../" + path.removePrefix("OEBPS/")
    }

    /**
     * Adds a picture whose bytes are only read as the book is written ([read]: a big comic's pages, one at
     * a time); its type is from [name]'s extension. Returns its address from a page.
     */
    fun imageLater(name: String, asCover: Boolean = false, read: () -> ByteArray): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            "heic", "heif" -> "image/heif"
            else -> "image/jpeg"
        }
        val path = unique("OEBPS/images", name)
        parts += BookAssembly.Part(path, type, null, if (asCover) setOf("cover-image") else emptySet(), later = read)
        if (asCover && cover == null) cover = path
        return "../" + path.removePrefix("OEBPS/")
    }

    /** Makes the picture at [href] (from a page) the book's cover, if it hasn't one. */
    fun useAsCover(href: String) {
        if (cover != null) return
        val path = "OEBPS/" + href.removePrefix("../")
        val i = parts.indexOfFirst { it.path == path }
        if (i < 0) return
        val p = parts[i]
        parts[i] = BookAssembly.Part(p.path, p.mediaType, p.bytes, p.properties + "cover-image")
        cover = path
    }

    val hasCover: Boolean get() = cover != null

    /** Adds a style sheet; returns its address from a page. */
    fun style(name: String, css: String): String {
        val path = unique("OEBPS/styles", if (name.endsWith(".css")) name else "$name.css")
        parts += BookAssembly.Part(path, "text/css", css.toByteArray())
        return "../" + path.removePrefix("OEBPS/")
    }

    /** Adds a font; returns its address from a page (or a style sheet). */
    fun font(name: String, bytes: ByteArray): String {
        val path = unique("OEBPS/fonts", name)
        val type = when (path.substringAfterLast('.').lowercase()) {
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "font/ttf"
        }
        parts += BookAssembly.Part(path, type, bytes)
        return "../" + path.removePrefix("OEBPS/")
    }

    /** Adds any other file the pages use; returns its address from a page. */
    fun other(dir: String, name: String, mediaType: String, bytes: ByteArray): String {
        val path = unique("OEBPS/$dir", name)
        parts += BookAssembly.Part(path, mediaType, bytes)
        return "../" + path.removePrefix("OEBPS/")
    }

    /** The next page's file name, before it's added (for links between pages). */
    fun nextPagePath(name: String? = null): String = "OEBPS/text/" + (name ?: "part%04d.xhtml".format(pageCount + 1))

    /**
     * Adds a page with [body] (XHTML, for inside the body) to the end of the reading order; it's in the
     * contents as [title] unless that's null. Returns its path in the book.
     */
    fun page(
        title: String?,
        body: String,
        name: String? = null,
        linear: Boolean = true,
        extraStyles: List<String> = emptyList(),
        bodyClass: String? = null,
        head: String = "",
        /** The page has its own styles (a book that had them): the plain book look isn't added. */
        ownStyles: Boolean = false,
    ): String {
        pageCount++
        val path = unique("OEBPS/text", name ?: "part%04d.xhtml".format(pageCount))
        val xhtml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n")
            append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"").append(Xml.escapeAttr(lang))
            append("\" lang=\"").append(Xml.escapeAttr(lang)).append("\">\n<head>\n  <title>").append(Xml.escapeText(title ?: meta.title)).append("</title>\n")
            for (s in (if (ownStyles) emptyList() else styles) + extraStyles) append("  <link rel=\"stylesheet\" type=\"text/css\" href=\"").append(Xml.escapeAttr(s)).append("\"/>\n")
            append(head)
            append("</head>\n<body").apply { if (bodyClass != null) append(" class=\"").append(Xml.escapeAttr(bodyClass)).append('"') }.append(">\n")
            append(body)
            append("\n</body>\n</html>\n")
        }
        parts += BookAssembly.Part(path, "application/xhtml+xml", xhtml.toByteArray())
        spine += BookAssembly.SpineEntry(path, linear)
        if (title != null && title.isNotBlank()) toc += TocNode(title.trim(), path, null)
        return path
    }

    /** A first page with the cover on it (if there is one, and nothing in the reading order yet shows it). */
    fun coverPage() {
        val c = cover ?: return
        val href = "../" + c.removePrefix("OEBPS/")
        pageCount++
        val path = unique("OEBPS/text", "cover.xhtml")
        val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${Xml.escapeAttr(lang)}" lang="${Xml.escapeAttr(lang)}">
<head>
  <title>${Xml.escapeText(meta.title.ifBlank { "Cover" })}</title>
  <style type="text/css">html, body { margin: 0; padding: 0; height: 100%; text-align: center; } img { max-width: 100%; max-height: 100%; }</style>
</head>
<body epub:type="cover">
  <div><img src="${Xml.escapeAttr(href)}" alt="Cover"/></div>
</body>
</html>
"""
        parts.add(0, BookAssembly.Part(path, "application/xhtml+xml", xhtml.toByteArray()))
        spine.add(0, BookAssembly.SpineEntry(path, true))
    }

    val isEmpty: Boolean get() = spine.isEmpty()

    /** Contents that are one entry over another over the chapters (a book's title, then its parts) as one list. */
    private fun flattened(list: List<TocNode>): List<TocNode> {
        if (list.size != 1) return list
        val out = ArrayList<TocNode>()
        var n: TocNode? = list[0]
        while (n != null) {
            out += n.copy(children = emptyList())
            if (n.children.size == 1) n = n.children[0]
            else {
                out += n.children
                n = null
            }
        }
        return out
    }

    fun write(target: File) {
        if (spine.isEmpty()) throw ConversionException(tr("There's no text in it to make a book from."))
        val finalToc = flattened(toc).ifEmpty { listOf(TocNode(meta.title.ifBlank { "Start" }, spine.first().path, null)) }
        BookAssembly.write(target, meta.copy(title = meta.title.ifBlank { "Untitled" }), parts, spine, finalToc)
    }

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "svg", "webp", "bmp")

        /** A picture's media type and file extension, from its name or (if that says nothing) its first bytes. */
        fun imageType(name: String, bytes: ByteArray): Pair<String, String> {
            fun b(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
            return when {
                b(0) == 0xFF && b(1) == 0xD8 -> "image/jpeg" to "jpg"
                b(0) == 0x89 && b(1) == 'P'.code -> "image/png" to "png"
                b(0) == 'G'.code && b(1) == 'I'.code && b(2) == 'F'.code -> "image/gif" to "gif"
                b(0) == 'R'.code && b(1) == 'I'.code && b(8) == 'W'.code && b(9) == 'E'.code -> "image/webp" to "webp"
                b(0) == 'B'.code && b(1) == 'M'.code -> "image/bmp" to "bmp"
                String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1).let { it.contains("<svg") } -> "image/svg+xml" to "svg"
                else -> when (name.substringAfterLast('.', "").lowercase()) {
                    "png" -> "image/png" to "png"
                    "gif" -> "image/gif" to "gif"
                    "svg" -> "image/svg+xml" to "svg"
                    "webp" -> "image/webp" to "webp"
                    else -> "image/jpeg" to "jpg"
                }
            }
        }

        /** The look of a book made from another kind of file: plain, like a printed book. */
        const val BOOK_CSS = """body { margin: 0; line-height: 1.4; }
h1, h2, h3, h4, h5, h6 { font-weight: normal; text-align: center; page-break-after: avoid; break-after: avoid; }
h1 { font-size: 1.7em; margin: 2em 0 1.5em; }
h2 { font-size: 1.4em; margin: 2em 0 1.2em; }
h3 { font-size: 1.15em; margin: 1.5em 0 0.8em; }
h4, h5, h6 { font-size: 1em; font-weight: bold; margin: 1.2em 0 0.6em; }
p { margin: 0; text-indent: 1.5em; }
p.first, h1 + p, h2 + p, h3 + p, h4 + p, hr + p, blockquote + p, .center + p { text-indent: 0; }
p.center, .center { text-align: center; text-indent: 0; }
p.right { text-align: right; text-indent: 0; }
p.verse { text-indent: 0; margin: 0 0 0 2em; }
p.space { margin-top: 1em; }
blockquote { margin: 1em 2em; }
pre { white-space: pre-wrap; font-size: 0.9em; }
hr { border: none; border-top: 1px solid currentColor; width: 30%; margin: 1.5em auto; opacity: 0.5; }
img { max-width: 100%; }
div.picture { text-align: center; text-indent: 0; margin: 1em 0; }
table { border-collapse: collapse; margin: 1em auto; }
td, th { border: 1px solid currentColor; padding: 0.2em 0.4em; vertical-align: top; }
.notes p { text-indent: 0; margin-bottom: 0.5em; }
"""
    }
}
