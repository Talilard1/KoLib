package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr
import java.io.File
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A book can't be put together, for a reason the person can act on. */
class AssemblyException(message: String) : Exception(message)

/**
 * Makes whole books: a new one to write, one made of several, or several cut from one. Each comes out
 * as a plain EPUB 3 with an NCX for older readers (the Kobo among them), ready for the library to take
 * in like any other book. The books it's made from are only read, never changed.
 */
object BookAssembly {

    /**
     * A file going into a book: its path in the zip, its type, its bytes, and any manifest properties. The
     * bytes can be read only as the book is written ([later]), so a book of many big pictures (a comic of
     * gigabytes) is never held whole.
     */
    class Part(val path: String, val mediaType: String, bytes: ByteArray?, val properties: Set<String> = emptySet(), private val later: (() -> ByteArray)? = null) {
        private val held = bytes
        val bytes: ByteArray get() = held ?: later?.invoke() ?: ByteArray(0)
    }

    /** A page in the reading order; [linear] false for one only reached by a link (notes, say). */
    data class SpineEntry(val path: String, val linear: Boolean = true)

    private const val OPF = "OEBPS/content.opf"
    private const val NAV = "OEBPS/nav.xhtml"
    private const val NCX = "OEBPS/toc.ncx"

    // ---- writing a book ---------------------------------------------------------------------------

    /** Writes the book to [target]: its details, its files, the reading order and the contents. */
    fun write(target: File, meta: BookMetadata, parts: List<Part>, spine: List<SpineEntry>, toc: List<TocNode>) {
        require(spine.isNotEmpty()) { tr("A book must have at least one page.") }
        val uid = meta.identifier?.takeIf { it.isNotBlank() } ?: "urn:uuid:${UUID.randomUUID()}"
        val lang = meta.language?.takeIf { it.isNotBlank() } ?: "en"
        val ids = HashMap<String, String>()
        parts.forEachIndexed { i, p -> ids[p.path] = "i${i + 1}" }
        val modified = ZonedDateTime.now(ZoneOffset.UTC).withNano(0).format(DateTimeFormatter.ISO_INSTANT)
        val opf = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\" xml:lang=\"").append(Xml.escapeAttr(lang)).append("\">\n")
            append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">\n")
            append("    <dc:identifier id=\"bookid\">").append(Xml.escapeText(uid)).append("</dc:identifier>\n")
            append("    <dc:title>").append(Xml.escapeText(meta.title.ifBlank { "Untitled" })).append("</dc:title>\n")
            append("    <dc:language>").append(Xml.escapeText(lang)).append("</dc:language>\n")
            append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n")
            append("  </metadata>\n")
            append("  <manifest>\n")
            append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
            for (p in parts) {
                append("    <item id=\"").append(ids[p.path]).append("\" href=\"")
                append(Xml.escapeAttr(Contents.encodeHref(EpubFile.relativeHref(OPF, p.path))))
                append("\" media-type=\"").append(Xml.escapeAttr(p.mediaType)).append('"')
                val props = p.properties - "nav"
                if (props.isNotEmpty()) append(" properties=\"").append(props.joinToString(" ")).append('"')
                append("/>\n")
            }
            append("  </manifest>\n")
            append("  <spine toc=\"ncx\">\n")
            for (s in spine) {
                val id = ids[s.path] ?: throw AssemblyException(tr("A page in the reading order is missing: {0}", s.path))
                append("    <itemref idref=\"").append(id).append('"')
                if (!s.linear) append(" linear=\"no\"")
                append("/>\n")
            }
            append("  </spine>\n")
            append("</package>\n")
        }
        // The details as the details editor writes them: authors, series, tags and the rest.
        val cover = parts.firstOrNull { "cover-image" in it.properties }
        var finalOpf = OpfDocument(opf).withMetadata(meta.copy(identifier = uid, language = lang))
        if (cover != null) finalOpf = OpfDocument(finalOpf).withCover(EpubFile.relativeHref(OPF, cover.path), cover.mediaType)
        val navText = Contents.writeNav(navTemplate(meta.title, lang), NAV, toc) ?: navTemplate(meta.title, lang)
        val ncxText = Contents.newNcx(meta.title, uid, NCX, toc)
        val entries = ArrayList<Pair<String, () -> ByteArray>>()
        entries += "META-INF/container.xml" to { CONTAINER.toByteArray() }
        entries += OPF to { finalOpf.toByteArray() }
        entries += NAV to { navText.toByteArray() }
        entries += NCX to { ncxText.toByteArray() }
        // Each part read as it's written, one at a time.
        for (p in parts) entries += p.path to { p.bytes }
        target.parentFile?.mkdirs()
        EpubWriter.createLazily(target, entries)
    }

    private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private fun navTemplate(title: String, lang: String) = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${Xml.escapeAttr(lang)}" lang="${Xml.escapeAttr(lang)}">
<head>
  <title>${Xml.escapeText(title.ifBlank { "Contents" })}</title>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Contents</h1>
    <ol>
    </ol>
  </nav>
</body>
</html>
"""

    // ---- a new book -------------------------------------------------------------------------------

    /**
     * A chapter page, as the editor and the reader expect one: [styles] are the style sheets it links to
     * (as written in the page), and its title is a [heading] (the editor's "Heading 2 (a chapter)").
     */
    fun chapterPage(
        title: String,
        lang: String,
        body: String = "<p></p>",
        styles: List<String> = listOf("../styles/book.css"),
        heading: String = "h2",
    ): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${Xml.escapeAttr(lang)}" lang="${Xml.escapeAttr(lang)}">
<head>
  <title>${Xml.escapeText(title)}</title>
${styles.joinToString("") { "  <link rel=\"stylesheet\" type=\"text/css\" href=\"${Xml.escapeAttr(it)}\"/>\n" }}</head>
<body>
  <$heading>${Xml.escapeText(title)}</$heading>
  $body
</body>
</html>
"""

    private const val BOOK_CSS = """body { margin: 0 5%; line-height: 1.5; }
h1, h2, h3 { font-weight: normal; }
h1 { text-align: center; font-size: 1.8em; margin: 3em 0 2em; }
h2 { text-align: center; font-size: 1.4em; margin: 2em 0 1.5em; }
h3 { font-size: 1.1em; margin: 1.5em 0 0.75em; }
p { margin: 0; text-indent: 1.5em; }
h1 + p, h2 + p, h3 + p, hr + p { text-indent: 0; }
hr { border: none; text-align: center; margin: 1.5em 0; }
hr::after { content: "* * *"; }
"""

    /** A new, empty book: its details and a first chapter, ready to be written in the editor. */
    fun create(target: File, meta: BookMetadata, firstChapter: String = "Chapter 1") {
        val lang = meta.language?.takeIf { it.isNotBlank() } ?: "en"
        val parts = listOf(
            Part("OEBPS/text/chapter-1.xhtml", "application/xhtml+xml", chapterPage(firstChapter, lang).toByteArray()),
            Part("OEBPS/styles/book.css", "text/css", BOOK_CSS.toByteArray()),
        )
        val toc = listOf(TocNode(firstChapter, "OEBPS/text/chapter-1.xhtml", null))
        write(target, meta.copy(language = lang), parts, listOf(SpineEntry("OEBPS/text/chapter-1.xhtml")), toc)
    }

    // ---- merging ------------------------------------------------------------------------------------

    /**
     * One book made of [sources], in that order. Each keeps its own pages, pictures and styles (in a
     * folder of its own, so nothing clashes), and the contents get an entry for each book with its own
     * contents under it. The cover is the one from [coverFrom] (an index into [sources]), if it has one.
     */
    fun merge(target: File, sources: List<File>, meta: BookMetadata, coverFrom: Int? = 0, titles: List<String>? = null) {
        if (sources.size < 2) throw AssemblyException(tr("Select at least two books to combine."))
        val parts = ArrayList<Part>()
        val spine = ArrayList<SpineEntry>()
        val toc = ArrayList<TocNode>()
        sources.forEachIndexed { i, file ->
            EpubFile.open(file).use { epub ->
                val book = readBook(epub)
                val prefix = "OEBPS/book-${i + 1}/"
                val base = commonDir(book.files.keys)
                fun moved(path: String) = prefix + path.removePrefix(base)
                val coverPath = if (i == coverFrom) epub.findCover()?.path else null
                for ((path, item) in book.files) {
                    val props = item.properties - "nav" - "cover-image" + (if (path == coverPath) setOf("cover-image") else emptySet())
                    parts += Part(moved(path), item.mediaType, book.bytes(path), props)
                }
                val pages = book.spine.filter { it.path in book.files }
                if (pages.isEmpty()) throw AssemblyException(tr("“{0}” has no pages to read.", book.title))
                pages.forEach { spine += SpineEntry(moved(it.path), it.linear) }
                val title = titles?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: book.title
                toc += TocNode(title, moved((pages.firstOrNull { it.linear } ?: pages.first()).path), null, remap(book.toc) { moved(it) }.filter { it.path.startsWith(prefix) })
            }
        }
        write(target, meta, parts, spine, toc)
    }

    // ---- splitting ----------------------------------------------------------------------------------

    /** A chapter of a book, for choosing where it splits. */
    data class Page(val index: Int, val path: String, val title: String, val linear: Boolean, val words: Int, val level: Int?)

    /**
     * The pages of a book in reading order, each with a title (from the contents where it has one) and
     * the depth of that contents entry: top-level entries (Book One, Part Two) are where omnibuses split.
     */
    fun pages(source: File): List<Page> = EpubFile.open(source).use { epub ->
        // Counted exactly as split counts them, so a page's index is where the book is cut.
        val book = readBook(epub)
        val levels = HashMap<String, Int>()
        for ((node, level) in Contents.flatten(book.toc)) if (node.linked && node.fragment == null) levels.putIfAbsent(node.path, level)
        for ((node, level) in Contents.flatten(book.toc)) if (node.linked) levels.putIfAbsent(node.path, level)
        book.spine.filter { it.path in book.files }.mapIndexed { i, s ->
            val words = if (book.files.getValue(s.path).isContent) runCatching { EpubText.countWords(EpubText.plainText(book.text(s.path))) }.getOrDefault(0) else 0
            Page(i, s.path, book.titleOf(s.path), s.linear, words, levels[s.path])
        }
    }

    /**
     * A title for each book a split makes: the top-level contents entry that covers most of its text
     * (Book One: The Fellowship of the Ring), or "[bookTitle] (part n)" when no one entry does (a
     * novel cut between ordinary chapters, say).
     */
    fun partTitles(pages: List<Page>, cuts: List<Int>, bookTitle: String): List<String> {
        val starts = (listOf(0) + cuts.filter { it in 1 until pages.size }).distinct().sorted()
        return starts.mapIndexed { k, start ->
            val part = pages.subList(start, starts.getOrNull(k + 1) ?: pages.size)
            val total = part.sumOf { it.words }
            var best: Page? = null
            var bestWords = -1
            var current: Page? = null
            var words = 0
            fun close() {
                val c = current ?: return
                if (words > bestWords) { best = c; bestWords = words }
            }
            for (p in part) {
                if (p.level == 0) { close(); current = p; words = 0 }
                words += p.words
            }
            close()
            best?.takeIf { total == 0 || bestWords * 2 >= total }?.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: tr("{0} (part {1})", bookTitle, k + 1)
        }
    }

    /**
     * Where to split an omnibus: at each top-level contents entry with chapters under it (in a book
     * whose contents are one flat list, every chapter is top level, so none is suggested), once real
     * text has gone before it and with a book's worth after it. Not at the first such entry: what
     * comes before it (a cover, a title page, an introduction) goes with the first book.
     */
    fun suggestedCuts(pages: List<Page>): List<Int> {
        val tops = pages.filter { it.level == 0 }
        val books = tops.filterIndexed { i, p ->
            val next = tops.getOrNull(i + 1)?.index ?: pages.size
            p.index > 0 && (p.index + 1 until next).any { (pages.getOrNull(it)?.level ?: 0) >= 1 }
        }
        val out = ArrayList<Int>()
        var last = 0
        for ((i, p) in books.withIndex()) {
            if (i == 0) continue
            // A book's opening pages (title, contents) aren't a book: only cut once real text has gone before.
            val before = pages.subList(last, p.index).sumOf { it.words }
            // And a book's worth after it, up to the next one: an afterword isn't a book.
            val after = pages.subList(p.index, books.getOrNull(i + 1)?.index ?: pages.size).sumOf { it.words }
            if (before >= 3000 && after >= 3000) {
                out += p.index
                last = p.index
            }
        }
        return out
    }

    /**
     * Cuts [source] into books at [cuts] (indexes of the pages where each new book starts), writing
     * them to [targets] with [metas]. Each book takes its pages, the pictures, styles and fonts they
     * use, and any pages they link to elsewhere in the book (notes, say) as pages read only by link.
     */
    fun split(source: File, cuts: List<Int>, metas: List<BookMetadata>, targets: List<File>) {
        EpubFile.open(source).use { epub ->
            val book = readBook(epub)
            val spinePages = book.spine.filter { it.path in book.files }
            val starts = (listOf(0) + cuts.filter { it in 1 until spinePages.size }).distinct().sorted()
            if (starts.size < 2) throw AssemblyException(tr("Choose at least one chapter where a new book starts."))
            require(metas.size == starts.size && targets.size == starts.size) { tr("One title and one file for each book.") }
            val spinePaths = spinePages.map { it.path }.toSet()
            // Pages read only by link (notes, mostly) go with every book that links to them.
            val byLinkOnly = spinePages.filter { !it.linear }.map { it.path }.toSet()
            val coverPath = epub.findCover()?.path
            for ((k, start) in starts.withIndex()) {
                val end = starts.getOrNull(k + 1) ?: spinePages.size
                val pages = spinePages.subList(start, end)
                val docs = pages.map { it.path }.toMutableList()
                val linkedOut = LinkedHashSet<String>()
                val needed = LinkedHashSet<String>()
                val queue = ArrayDeque(docs)
                val seen = HashSet<String>()
                while (queue.isNotEmpty()) {
                    val path = queue.removeFirst()
                    if (!seen.add(path)) continue
                    needed += path
                    for (ref in references(book, path)) {
                        if (ref in spinePaths) {
                            // A link to a notes page elsewhere brings it along, as a page reached by link. (A link to a
                            // chapter in another book doesn't: that chapter belongs there.)
                            if (ref !in docs && ref in byLinkOnly && ref !in linkedOut) {
                                linkedOut += ref
                                queue += ref
                            }
                        } else if (ref in book.files) queue += ref
                    }
                }
                // The first book keeps the cover.
                if (k == 0 && coverPath != null && coverPath in book.files) needed += coverPath
                val partCover = if (k == 0) coverPath else coverOfOpening(book, pages.first().path)
                val parts = needed.filter { it in book.files }.map { path ->
                    val item = book.files.getValue(path)
                    val props = item.properties - "nav" - "cover-image" + (if (path == partCover) setOf("cover-image") else emptySet())
                    Part(path, item.mediaType, book.bytes(path), props)
                }
                val spine = pages.map { SpineEntry(it.path, it.linear) } + linkedOut.map { SpineEntry(it, linear = false) }
                // The contents entries for these pages, lifted to the top.
                val flat = Contents.flatten(book.toc).filter { (n, _) -> n.path in docs }
                val minLevel = flat.minOfOrNull { it.second } ?: 0
                var toc = Contents.nest(flat.map { (n, level) -> n.copy(children = emptyList()) to (level - minLevel) })
                if (toc.isEmpty()) toc = pages.filter { it.linear }.map { TocNode(book.titleOf(it.path), it.path, null) }
                write(targets[k], metas[k], parts, spine, toc)
            }
        }
    }

    // ---- reading the books things are made from ----------------------------------------------------------

    private class SourceBook(
        val epub: EpubFile,
        val title: String,
        val files: LinkedHashMap<String, ManifestItem>,
        val spine: List<SpineEntry>,
        val toc: List<TocNode>,
        val chapterTitles: Map<String, String>,
        private val obfuscated: Map<String, String>,
        private val uid: String,
    ) {
        private val cache = HashMap<String, ByteArray>()

        /** A file's bytes, with any font scrambling undone (it's keyed to the old book's identifier). */
        fun bytes(path: String): ByteArray = cache.getOrPut(path) {
            val raw = epub.readBytes(path) ?: ByteArray(0)
            obfuscated[path]?.let { alg -> FontObfuscation.undo(raw, alg, uid) } ?: raw
        }

        fun text(path: String): String = EpubFile.decodeText(bytes(path))
        fun titleOf(path: String): String = chapterTitles[path]?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').substringBeforeLast('.')
    }

    private fun readBook(epub: EpubFile): SourceBook {
        val obfuscated = FontObfuscation.read(epub) ?: throw AssemblyException(tr("“{0}” is copy-protected, so it can't be split or combined.", epub.opf.readMetadata().title.ifBlank { epub.file.name }))
        val uid = epub.opf.readMetadata().identifier.orEmpty()
        val nav = epub.opf.navItem()?.let { epub.resolve(it.href) }
        val ncx = epub.opf.ncxItem()?.let { epub.resolve(it.href) }
        val spineRefs = epub.opf.spineRefs()
        val spine = spineRefs.mapNotNull { ref -> epub.opf.item(ref.idref)?.let { SpineEntry(epub.resolve(it.href), ref.linear) } }
        val spinePaths = spine.map { it.path }.toSet()
        val files = LinkedHashMap<String, ManifestItem>()
        for (item in epub.opf.items) {
            val path = epub.resolve(item.href)
            if (path == ncx) continue
            // The book's own contents page is only kept if it's one of the pages read.
            if (path == nav && path !in spinePaths) continue
            if (!epub.has(path)) continue
            files[path] = item
        }
        val structure = BookStructure(EpubFileFiles(epub))
        val meta = epub.opf.readMetadata()
        return SourceBook(epub, meta.title.ifBlank { epub.file.nameWithoutExtension }, files, spine, structure.toc,
            structure.chapters.associate { it.path to it.title }, obfuscated, uid)
    }

    /** Remaps every path in a contents tree. */
    private fun remap(toc: List<TocNode>, map: (String) -> String): List<TocNode> =
        toc.map { it.copy(path = map(it.path), children = remap(it.children, map)) }

    /** The folder all of a book's files share, so they can be moved together. */
    private fun commonDir(paths: Collection<String>): String {
        val dirs = paths.map { it.substringBeforeLast('/', "").let { d -> if (d.isEmpty()) "" else "$d/" } }
        if (dirs.isEmpty()) return ""
        var prefix = dirs.first()
        for (d in dirs) while (!d.startsWith(prefix)) prefix = prefix.dropLast(1).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        return prefix
    }

    private val attrRef = Regex("""\s(?:src|href|xlink:href|poster|data)\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val cssRef = Regex("""url\(\s*(['"]?)([^'")]+?)\1\s*\)|@import\s+(['"])([^'"]+)\3""", RegexOption.IGNORE_CASE)

    /** The files a page or style sheet uses or links to, as paths in the book. */
    private fun references(book: SourceBook, path: String): List<String> {
        val item = book.files[path] ?: return emptyList()
        val isCss = item.mediaType == "text/css"
        val isPage = item.isContent || item.mediaType == "image/svg+xml"
        if (!isCss && !isPage) return emptyList()
        val text = book.text(path)
        val dir = path.substringBeforeLast('/', "")
        val raw = ArrayList<String>()
        if (isPage) attrRef.findAll(text).forEach { raw += it.groupValues[2] }
        cssRef.findAll(text).forEach { m -> raw += m.groupValues[2].ifEmpty { m.groupValues[4] } }
        return raw.asSequence()
            .map { Xml.unescape(it.trim()) }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it) }
            .map { EpubFile.resolvePath(dir, it) }
            .filter { it != path }
            .distinct()
            .toList()
    }

    /** A part of an omnibus often opens on its own cover: a page that's little more than one picture. */
    private fun coverOfOpening(book: SourceBook, firstPage: String): String? {
        val item = book.files[firstPage] ?: return null
        if (!item.isContent) return null
        val text = book.text(firstPage)
        val words = EpubText.countWords(EpubText.plainText(text))
        if (words > 40) return null
        val pictures = references(book, firstPage).filter { p -> book.files[p]?.isImage == true }
        return pictures.singleOrNull()
    }
}

/**
 * Some publishers scramble the fonts they embed, keyed to the book's identifier, so they only work in
 * that book. A merged or split book has a new identifier, so the scrambling is undone as the fonts are
 * copied over. Both the EPUB standard's way and Adobe's are handled; anything else is real copy
 * protection, and those books are left alone.
 */
object FontObfuscation {
    const val IDPF = "http://www.idpf.org/2008/embedding"
    const val ADOBE = "http://ns.adobe.com/pdf/enc#RC"

    /** Scrambled files and how (path to algorithm); null if the book is copy-protected. */
    fun read(epub: EpubFile): Map<String, String>? {
        val text = epub.readText("META-INF/encryption.xml") ?: return emptyMap()
        val doc = try { Xml.parse(text) } catch (e: XmlException) { return emptyMap() }
        val out = HashMap<String, String>()
        for (data in doc.root.descendants().filter { it.localName == "EncryptedData" }) {
            val alg = data.descendants().firstOrNull { it.localName == "EncryptionMethod" }?.attributes?.get("Algorithm") ?: continue
            val uri = data.descendants().firstOrNull { it.localName == "CipherReference" }?.attributes?.get("URI") ?: continue
            if (alg != IDPF && alg != ADOBE) return null
            out[EpubFile.resolvePath("", uri)] = alg
        }
        return out
    }

    /** The font as it was before it was scrambled (scrambling is its own undoing). */
    fun undo(bytes: ByteArray, algorithm: String, uid: String): ByteArray {
        val key: ByteArray
        val length: Int
        when (algorithm) {
            IDPF -> {
                val id = uid.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
                key = MessageDigest.getInstance("SHA-1").digest(id.toByteArray(Charsets.UTF_8))
                length = 1040
            }
            ADOBE -> {
                val hex = uid.removePrefix("urn:uuid:").replace("-", "").filter { it.isLetterOrDigit() }
                if (hex.length < 32) return bytes
                key = ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
                length = 1024
            }
            else -> return bytes
        }
        val out = bytes.copyOf()
        for (i in 0 until minOf(length, out.size)) out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }
}
