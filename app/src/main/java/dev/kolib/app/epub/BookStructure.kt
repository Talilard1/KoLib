package dev.kolib.app.epub

import dev.kolib.app.tr
import java.io.Closeable

/** Anything a book's files can be read from: the EPUB itself, or an edit in progress. */
interface BookFiles {
    val opfPath: String
    fun bytes(path: String): ByteArray?
    fun text(path: String): String? = bytes(path)?.let { EpubFile.decodeText(it) }
    fun has(path: String): Boolean = bytes(path) != null
    /** Every file in the book, in zip order. */
    fun names(): List<String>
}

/** An EPUB file as [BookFiles]. */
class EpubFileFiles(val epub: EpubFile) : BookFiles, Closeable {
    override val opfPath: String get() = epub.opfPath
    override fun bytes(path: String): ByteArray? = epub.readBytes(path)
    override fun has(path: String): Boolean = epub.has(path)
    override fun names(): List<String> = epub.entryNames()
    override fun close() = epub.close()
}

/** [base] with some files changed (null: gone), for working out a change before it's made. */
class OverlayFiles(private val base: BookFiles, private val overrides: Map<String, ByteArray?>) : BookFiles {
    override val opfPath: String get() = base.opfPath
    override fun bytes(path: String): ByteArray? = if (overrides.containsKey(path)) overrides[path] else base.bytes(path)
    override fun has(path: String): Boolean = if (overrides.containsKey(path)) overrides[path] != null else base.has(path)
    override fun names(): List<String> {
        val existing = base.names()
        val set = existing.toHashSet()
        return existing.filter { !overrides.containsKey(it) || overrides[it] != null } + overrides.filter { (k, v) -> v != null && k !in set }.keys
    }
}

/** A document in the book's reading order. */
data class Chapter(
    val index: Int,
    /** Manifest id. */
    val id: String,
    /** Path inside the zip. */
    val path: String,
    val linear: Boolean,
    val title: String,
    /** The title comes from the table of contents (rather than a heading or the file name). */
    val titleFromToc: Boolean,
)

/**
 * One entry of the table of contents, with the entries under it. An entry that isn't a link of its own
 * (a heading over a group, in an EPUB 3 contents page) points where its first entry does.
 */
data class TocNode(
    val title: String,
    val path: String,
    val fragment: String?,
    val children: List<TocNode> = emptyList(),
    val linked: Boolean = true,
) {
    fun flatten(level: Int = 0): List<Pair<TocNode, Int>> = listOf(this to level) + children.flatMap { it.flatten(level + 1) }
}

/** The reading order and table of contents of a book, as they are in [source]. */
class BookStructure(val source: BookFiles) {

    val opfText: String = source.text(source.opfPath) ?: throw EpubException(tr("The book's package file is missing."))
    val opf = OpfDocument(opfText)
    val opfPath: String = source.opfPath
    val opfDir: String = opfPath.substringBeforeLast('/', "")

    fun pathOf(item: ManifestItem): String = EpubFile.resolvePath(opfDir, item.href)

    val toc: List<TocNode> = readToc()

    val chapters: List<Chapter> = run {
        val firstTocTitle = HashMap<String, String>()
        for ((node, _) in toc.flatMap { it.flatten() }) {
            if (node.title.isNotBlank() && node.linked) firstTocTitle.putIfAbsent(node.path, node.title)
        }
        opf.spineRefs().mapNotNull { ref ->
            val item = opf.item(ref.idref)?.takeIf { it.isContent } ?: return@mapNotNull null
            val path = pathOf(item)
            ref to (item to path)
        }.mapIndexed { i, (ref, pair) ->
            val (item, path) = pair
            val fromToc = firstTocTitle[path]
            Chapter(
                index = i,
                id = item.id,
                path = path,
                linear = ref.linear,
                title = fromToc ?: headingTitle(path) ?: path.substringAfterLast('/').substringBeforeLast('.'),
                titleFromToc = fromToc != null,
            )
        }
    }

    fun chapterAt(path: String): Chapter? = chapters.firstOrNull { it.path == path }

    // ---- contents ------------------------------------------------------------------------------

    private fun readToc(): List<TocNode> {
        opf.navItem()?.let { nav ->
            val path = pathOf(nav)
            source.text(path)?.let { text -> runCatching { navToc(text, path) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it } }
        }
        opf.ncxItem()?.let { ncx ->
            val path = pathOf(ncx)
            source.text(path)?.let { text -> runCatching { ncxToc(text, path) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it } }
        }
        return emptyList()
    }

    private fun target(base: String, href: String): Pair<String, String?> =
        EpubFile.resolvePath(base.substringBeforeLast('/', ""), href) to href.substringAfter('#', "").ifEmpty { null }

    private fun ncxToc(text: String, ncxPath: String): List<TocNode> {
        val root = Xml.parse(text).root
        val navMap = root.descendants().firstOrNull { it.localName.equals("navMap", true) } ?: return emptyList()
        fun point(e: XElement): TocNode? {
            val label = e.elements().firstOrNull { it.localName.equals("navLabel", true) }
                ?.descendants()?.firstOrNull { it.localName.equals("text", true) }?.text()?.let(::clean).orEmpty()
            val src = e.elements().firstOrNull { it.localName.equals("content", true) }?.attributes?.get("src") ?: return null
            val (path, fragment) = target(ncxPath, src)
            val kids = e.elements().filter { it.localName.equals("navPoint", true) }.mapNotNull(::point)
            return TocNode(label, path, fragment, kids)
        }
        return navMap.elements().filter { it.localName.equals("navPoint", true) }.mapNotNull(::point)
    }

    private fun navToc(text: String, navPath: String): List<TocNode> {
        val root = Xml.parse(text).root
        val navs = root.descendants().filter { it.localName.equals("nav", true) }.toList()
        val nav = navs.firstOrNull { e -> e.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("toc") } }
            ?: navs.firstOrNull() ?: return emptyList()
        fun list(ol: XElement): List<TocNode> = ol.elements().filter { it.localName.equals("li", true) }.mapNotNull { li ->
            val link = li.elements().firstOrNull { it.localName.equals("a", true) }
            val label = (link ?: li.elements().firstOrNull { it.localName.equals("span", true) })?.text()?.let(::clean).orEmpty()
            val kids = li.elements().firstOrNull { it.localName.equals("ol", true) }?.let(::list).orEmpty()
            val href = link?.attributes?.get("href")
            if (href == null) {
                // A heading with no link of its own: it stands for its first child.
                val first = kids.firstOrNull() ?: return@mapNotNull null
                TocNode(label, first.path, first.fragment, kids, linked = false)
            } else {
                val (path, fragment) = target(navPath, href)
                TocNode(label, path, fragment, kids)
            }
        }
        val ol = nav.descendants().firstOrNull { it.localName.equals("ol", true) } ?: return emptyList()
        return list(ol)
    }

    private fun headingTitle(path: String): String? {
        val text = source.text(path) ?: return null
        heading.find(text)?.let { m -> clean(EpubText.plainText(m.groupValues[2])).takeIf { it.isNotEmpty() && it.length < 120 }?.let { return it } }
        return titleTag.find(text)?.let { clean(EpubText.plainText(it.groupValues[1])) }?.takeIf { it.isNotEmpty() && it.length < 120 }
    }

    companion object {
        fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim()
        private val heading = Regex("(?is)<(?:\\w+:)?h([1-3])\\b[^>]*>(.*?)</(?:\\w+:)?h\\1\\s*>")
        private val titleTag = Regex("(?is)<(?:\\w+:)?title\\b[^>]*>(.*?)</(?:\\w+:)?title\\s*>")
    }
}
