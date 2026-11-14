package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr

/**
 * Writes a book's table of contents: the NCX (which EPUB 2 readers and the Kobo use) and the EPUB 3
 * navigation document, whichever the book has. Everything else in those files is kept.
 */
object Contents {

    fun flatten(toc: List<TocNode>): List<Pair<TocNode, Int>> = toc.flatMap { it.flatten() }

    /** Builds a tree back from entries and their levels (a level can only go one deeper than the last). */
    fun nest(flat: List<Pair<TocNode, Int>>): List<TocNode> {
        class Builder(val node: TocNode) { val kids = ArrayList<Builder>() }
        val roots = ArrayList<Builder>()
        val stack = ArrayList<Pair<Builder, Int>>()
        for ((node, wanted) in flat) {
            val level = minOf(wanted, (stack.lastOrNull()?.second ?: -1) + 1).coerceAtLeast(0)
            while (stack.isNotEmpty() && stack.last().second >= level) stack.removeAt(stack.size - 1)
            val b = Builder(node.copy(children = emptyList()))
            if (stack.isEmpty()) roots += b else stack.last().first.kids += b
            stack += b to level
        }
        fun build(b: Builder): TocNode = b.node.copy(children = b.kids.map(::build))
        return roots.map(::build)
    }

    /** The changes that write [toc] into the book, on top of [pending] changes not yet in [files]. */
    fun write(files: BookFiles, structure: BookStructure, toc: List<TocNode>, pending: Map<String, ByteArray?> = emptyMap()): Map<String, ByteArray?> {
        val out = LinkedHashMap<String, ByteArray?>()
        fun text(path: String): String? = if (pending.containsKey(path)) pending[path]?.let { EpubFile.decodeText(it) } else files.text(path)
        val opf = structure.opf
        val ncxItem = opf.ncxItem()
        val navItem = opf.navItem()
        if (ncxItem != null) {
            val path = structure.pathOf(ncxItem)
            text(path)?.let { t -> writeNcx(t, path, toc)?.let { out[path] = it.toByteArray(Charsets.UTF_8) } }
        }
        if (navItem != null) {
            val path = structure.pathOf(navItem)
            text(path)?.let { t -> writeNav(t, path, toc)?.let { out[path] = it.toByteArray(Charsets.UTF_8) } }
        }
        if (ncxItem == null && !opf.isEpub3) {
            // An EPUB 2 book needs an NCX for its contents to show; make one.
            val dir = structure.opfDir
            var name = "toc.ncx"
            var n = 2
            while (files.has(join(dir, name))) name = "toc-${n++}.ncx"
            val path = join(dir, name)
            val title = opf.readMetadata().title
            val uid = opf.uniqueIdentifierId?.let { id -> opf.doc.root.descendants().firstOrNull { it.attributes["id"] == id }?.text()?.trim() }.orEmpty()
            out[path] = newNcx(title, uid, path, toc).toByteArray(Charsets.UTF_8)
            val currentOpf = text(structure.opfPath) ?: structure.opfText
            out[structure.opfPath] = addNcxToOpf(currentOpf, OpfDocument(currentOpf), name).toByteArray(Charsets.UTF_8)
        }
        return out
    }

    private fun join(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"

    fun href(fromFile: String, node: TocNode): String =
        EpubFile.relativeHref(fromFile, node.path).let { rel -> encodeHref(rel) } + (node.fragment?.let { "#$it" } ?: "")

    /** Spaces and the like in file names, written so readers can follow the link. */
    fun encodeHref(path: String): String = buildString {
        for (c in path) when (c) {
            ' ' -> append("%20")
            '#' -> append("%23")
            '%' -> append("%25")
            '?' -> append("%3F")
            else -> append(c)
        }
    }

    private fun depth(toc: List<TocNode>): Int = if (toc.isEmpty()) 0 else 1 + toc.maxOf { depth(it.children) }

    private fun ncxPoints(toc: List<TocNode>, ncxPath: String, indent: String, unit: String, counter: IntArray, prefix: String): String = buildString {
        for (node in toc) {
            val n = ++counter[0]
            append('\n').append(indent).append("<${prefix}navPoint id=\"navPoint-$n\" playOrder=\"$n\">")
            append('\n').append(indent).append(unit).append("<${prefix}navLabel><${prefix}text>")
                .append(Xml.escapeText(node.title.ifBlank { tr("Untitled") })).append("</${prefix}text></${prefix}navLabel>")
            append('\n').append(indent).append(unit).append("<${prefix}content src=\"").append(Xml.escapeAttr(href(ncxPath, node))).append("\"/>")
            append(ncxPoints(node.children, ncxPath, indent + unit, unit, counter, prefix))
            append('\n').append(indent).append("</${prefix}navPoint>")
        }
    }

    fun writeNcx(text: String, ncxPath: String, toc: List<TocNode>): String? {
        val doc = try { Xml.parse(text) } catch (e: XmlException) { return null }
        val navMap = doc.root.descendants().firstOrNull { it.localName.equals("navMap", true) } ?: return null
        val prefix = navMap.prefix?.let { "$it:" } ?: ""
        val edits = TextEdits(text)
        val indent = indentFor(text, navMap) + "  "
        val points = ncxPoints(toc, ncxPath, indent, "  ", intArrayOf(0), prefix)
        // Keep a navLabel the navMap itself may have.
        val keep = navMap.elements().filter { !it.localName.equals("navPoint", true) }.joinToString("") { "\n" + indent + text.substring(it.start, it.end) }
        if (navMap.selfClosing) {
            edits.replace(navMap.start, navMap.end, "<${navMap.name}>$keep$points\n${indentFor(text, navMap)}</${navMap.name}>")
        } else {
            edits.replace(navMap.startTagEnd, navMap.endTagStart, keep + points + "\n" + indentFor(text, navMap))
        }
        // The depth the NCX declares.
        doc.root.descendants().firstOrNull { it.localName.equals("meta", true) && it.attributes["name"] == "dtb:depth" }?.let { m ->
            val attrs = LinkedHashMap(m.attributes).apply { this["content"] = maxOf(1, depth(toc)).toString() }
            edits.replace(m.start, m.startTagEnd, Xml.startTag(m.name, attrs, m.selfClosing))
        }
        return edits.apply()
    }

    private fun indentFor(text: String, e: XElement): String {
        var s = e.start
        while (s > 0 && (text[s - 1] == ' ' || text[s - 1] == '\t')) s--
        return if (s > 0 && text[s - 1] == '\n') text.substring(s, e.start) else ""
    }

    private fun navList(toc: List<TocNode>, navPath: String, indent: String, unit: String): String = buildString {
        append("<ol>")
        for (node in toc) {
            if (node.linked || node.children.isEmpty()) {
                append('\n').append(indent).append(unit).append("<li><a href=\"").append(Xml.escapeAttr(href(navPath, node))).append("\">")
                    .append(Xml.escapeText(node.title.ifBlank { tr("Untitled") })).append("</a>")
            } else {
                append('\n').append(indent).append(unit).append("<li><span>").append(Xml.escapeText(node.title.ifBlank { tr("Untitled") })).append("</span>")
            }
            if (node.children.isNotEmpty()) {
                append('\n').append(indent).append(unit).append(unit)
                append(navList(node.children, navPath, indent + unit + unit, unit))
                append('\n').append(indent).append(unit)
            }
            append("</li>")
        }
        append('\n').append(indent).append("</ol>")
    }

    fun writeNav(text: String, navPath: String, toc: List<TocNode>): String? {
        val doc = try { Xml.parse(text) } catch (e: XmlException) { return null }
        val navs = doc.root.descendants().filter { it.localName.equals("nav", true) }.toList()
        val nav = navs.firstOrNull { e -> e.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("toc") } }
            ?: return null
        val edits = TextEdits(text)
        val ol = nav.elements().firstOrNull { it.localName.equals("ol", true) }
        val indent = ol?.let { indentFor(text, it) } ?: (indentFor(text, nav) + "  ")
        val list = navList(toc, navPath, indent, "  ")
        if (ol != null) edits.replace(ol.start, ol.end, list)
        else if (nav.selfClosing) edits.replace(nav.start, nav.end, Xml.startTag(nav.name, nav.attributes, false) + "\n$indent$list\n</${nav.name}>")
        else edits.insert(nav.endTagStart, "\n$indent$list\n")
        return edits.apply()
    }

    internal fun newNcx(title: String, uid: String, ncxPath: String, toc: List<TocNode>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n")
        append("  <head>\n")
        append("    <meta name=\"dtb:uid\" content=\"").append(Xml.escapeAttr(uid)).append("\"/>\n")
        append("    <meta name=\"dtb:depth\" content=\"").append(maxOf(1, depth(toc))).append("\"/>\n")
        append("    <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n")
        append("    <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n")
        append("  </head>\n")
        append("  <docTitle><text>").append(Xml.escapeText(title)).append("</text></docTitle>\n")
        append("  <navMap>")
        append(ncxPoints(toc, ncxPath, "    ", "  ", intArrayOf(0), ""))
        append("\n  </navMap>\n</ncx>\n")
    }

    private fun addNcxToOpf(opfText: String, opf: OpfDocument, name: String): String {
        val edits = TextEdits(opfText)
        val ids = opf.doc.root.descendants().mapNotNull { it.attributes["id"] }.toSet()
        var id = "ncx"
        var n = 2
        while (id in ids) id = "ncx-${n++}"
        val manifest = opf.manifestElement ?: return opfText
        val itemName = manifest.prefix?.let { "$it:item" } ?: "item"
        val anchor = manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd
        edits.insert(anchor, "\n${opf.indentOf(manifest) ?: "    "}<$itemName id=\"$id\" href=\"${Xml.escapeAttr(name)}\" media-type=\"application/x-dtbncx+xml\"/>")
        opf.spineElement?.let { spine ->
            val attrs = LinkedHashMap(spine.attributes).apply { this["toc"] = id }
            edits.replace(spine.start, spine.startTagEnd, Xml.startTag(spine.name, attrs, spine.selfClosing))
        }
        return edits.apply()
    }
}
