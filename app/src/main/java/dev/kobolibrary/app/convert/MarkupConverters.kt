package dev.kobolibrary.app.convert

import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.TocNode
import dev.kobolibrary.app.epub.XElement
import dev.kobolibrary.app.epub.XText
import dev.kobolibrary.app.epub.Xml
import java.io.File
import java.util.Base64

/**
 * A web page saved as a file, as a book: its text and pictures that are in the file itself, split into
 * chapters at its main headings. Scripts and anything fetched from the web are left out.
 */
object HtmlConverter {

    private val dropped = setOf("script", "noscript", "iframe", "object", "embed", "form", "input", "button", "select", "textarea", "nav", "template", "canvas", "video", "audio", "link", "meta", "base")

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        val sniff = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val declared = Regex("""charset\s*=\s*["']?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE).find(sniff)?.groupValues?.get(1)
        val doc = HtmlSoup.parse(TextDecoding.decode(bytes, declared))
        val head = doc.children.filterIsInstance<HtmlSoup.Element>().firstOrNull { it.name == "head" }
        val body = doc.children.filterIsInstance<HtmlSoup.Element>().firstOrNull { it.name == "body" } ?: doc
        fun metaContent(name: String) = head?.elements()?.firstOrNull { it.name == "meta" && it.attrs["name"].equals(name, true) }?.attrs?.get("content")?.trim()?.ifEmpty { null }
        val title = head?.first("title")?.text()?.trim()?.ifEmpty { null } ?: body.first("h1")?.text()?.trim()?.ifEmpty { null } ?: TextConverter.titleFromName(fileName)
        val meta = BookMetadata(
            title = title,
            authors = listOfNotNull((metaContent("author") ?: metaContent("dc.creator"))?.let { personName(it) }),
            language = doc.attrs["lang"] ?: doc.attrs["xml:lang"],
            description = metaContent("description")?.let { "<p>${HtmlSoup.escapeText(it)}</p>" },
        )
        val out = EpubOut(meta)
        val css = head?.elements()?.filter { it.name == "style" }?.joinToString("\n") { it.text() }.orEmpty() +
            body.elements().filter { it.name == "style" }.joinToString("\n") { it.text() }
        val extra = if (css.isNotBlank()) listOf(out.style("page.css", cleanCss(css))) else emptyList()
        clean(body, out)
        writeSplit(body, out, extra)
        out.write(target)
    }

    /** "Carroll, Lewis, 1832-1898" as "Lewis Carroll". */
    fun personName(s: String): String {
        val parts = s.split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.any(Char::isDigit) }
        return if (parts.size == 2) "${parts[1]} ${parts[0]}" else s.trim()
    }

    /** CSS with nothing that reaches outside the book. */
    fun cleanCss(css: String): String = css
        .replace(Regex("""@import[^;]*;"""), "")
        .replace(Regex("""url\(\s*['"]?(https?:)?//[^)]*\)"""), "none")
        .replace(Regex("""position\s*:\s*fixed"""), "position: static")

    /** Leaves out what a book can't have, and takes in pictures that are in the page itself. */
    fun clean(e: HtmlSoup.Element, out: EpubOut) {
        val it = e.children.listIterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c !is HtmlSoup.Element) continue
            if (c.name in dropped || c.name == "style" || c.name == "head" || c.name == "title") {
                it.remove()
                continue
            }
            c.attrs.keys.removeAll { k -> k.startsWith("on") || k == "contenteditable" || k == "draggable" || k == "tabindex" }
            when (c.name) {
                "img" -> {
                    val src = c.attrs["src"].orEmpty()
                    if (src.startsWith("data:image")) {
                        val comma = src.indexOf(',')
                        val bytes = runCatching { Base64.getMimeDecoder().decode(src.substring(comma + 1)) }.getOrNull()
                        if (bytes != null) c.attrs["src"] = out.image("picture.${src.substringAfter("image/").substringBefore(';').substringBefore('+')}", bytes)
                        else it.set(HtmlSoup.Text(c.attrs["alt"].orEmpty()))
                    } else it.set(HtmlSoup.Text(c.attrs["alt"].orEmpty()))
                    c.attrs.remove("srcset")
                    c.attrs.putIfAbsent("alt", "")
                }
                "a" -> {
                    val href = c.attrs["href"].orEmpty()
                    if (!(href.startsWith("http") || href.startsWith("mailto:") || href.startsWith("#"))) c.attrs.remove("href")
                    c.attrs.remove("target")
                }
                "font" -> {
                    c.name = "span"
                    c.attrs.clear()
                }
                "center" -> {
                    c.name = "div"
                    c.attrs["class"] = "center"
                }
            }
            clean(c, out)
        }
    }

    /** The body's content, as pages: a new one at each of its main headings. */
    fun writeSplit(body: HtmlSoup.Element, out: EpubOut, extraStyles: List<String> = emptyList()) {
        // Down through a single wrapper (a page that's all in one <div>).
        var top = body
        while (true) {
            val els = top.children.filterIsInstance<HtmlSoup.Element>()
            val loose = top.children.filterIsInstance<HtmlSoup.Text>().any { it.text.isNotBlank() }
            if (els.size == 1 && !loose && els[0].name in setOf("div", "main", "article", "section", "body")) top = els[0] else break
        }
        val nodes = top.children
        // Each heading, or block that starts with one (a chapter in a <div>): the level of heading.
        fun headingOf(n: HtmlSoup.Node): HtmlSoup.Element? {
            if (n !is HtmlSoup.Element) return null
            if (n.name.length == 2 && n.name[0] == 'h' && n.name[1] in '1'..'6') return n
            if (n.name in setOf("div", "section", "article")) {
                val first = n.children.firstOrNull { it !is HtmlSoup.Text || it.text.isNotBlank() }
                if (first is HtmlSoup.Element && first.name.length == 2 && first.name[0] == 'h' && first.name[1] in '1'..'6') return first
            }
            return null
        }
        val levels = nodes.map { headingOf(it)?.name?.get(1)?.digitToInt() ?: 0 }
        val at = (1..6).firstOrNull { lvl -> levels.count { it == lvl } >= 2 }
        // Where each id ends up, so links between the parts still work.
        val pages = ArrayList<Pair<String?, List<HtmlSoup.Node>>>()
        var title: String? = null
        var cur = ArrayList<HtmlSoup.Node>()
        for ((k, n) in nodes.withIndex()) {
            if (at != null && levels[k] == at) {
                if (cur.any { c -> c !is HtmlSoup.Text || c.text.isNotBlank() } || title != null) {
                    pages += title to cur
                    cur = ArrayList()
                }
                title = headingOf(n)!!.text().trim().replace(Regex("\\s+"), " ")
            }
            cur += n
        }
        pages += title to cur
        val paths = pages.indices.map { i -> out.nextPagePath("part%04d.xhtml".format(i + 1)) }
        val idPage = HashMap<String, Int>()
        pages.forEachIndexed { i, (_, ns) ->
            for (n in ns) if (n is HtmlSoup.Element) {
                n.attrs["id"]?.let { idPage[it] = i }
                n.elements().forEach { e -> e.attrs["id"]?.let { idPage[it] = i } }
            }
        }
        pages.forEachIndexed { i, (t, ns) ->
            val html = StringBuilder()
            for (n in ns) {
                if (n is HtmlSoup.Element) relink(n, i, idPage, paths)
                HtmlSoup.write(n, html)
            }
            out.page(t ?: if (i == 0) out.meta.title else null, html.toString(), name = paths[i].substringAfterLast('/'), extraStyles = extraStyles)
        }
    }

    private fun relink(e: HtmlSoup.Element, page: Int, idPage: Map<String, Int>, paths: List<String>) {
        if (e.name == "a") {
            val href = e.attrs["href"]
            if (href != null && href.startsWith("#")) {
                val target = idPage[href.substring(1)]
                if (target == null) e.attrs.remove("href") else if (target != page) e.attrs["href"] = paths[target].substringAfterLast('/') + href
            }
        }
        for (c in e.children) if (c is HtmlSoup.Element) relink(c, page, idPage, paths)
    }
}

/**
 * A FictionBook (FB2) file as a book: its details from the description, each section a chapter, its
 * pictures from the file, and its notes on a page of their own.
 */
object Fb2Converter {

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        val sniff = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val declared = Regex("""encoding\s*=\s*["']([A-Za-z0-9_-]+)""").find(sniff)?.groupValues?.get(1)
        val doc = try {
            Xml.parse(TextDecoding.decode(bytes, declared))
        } catch (e: Exception) {
            throw ConversionException("The FictionBook file is damaged: ${e.message}")
        }
        val root = doc.root
        if (root.localName != "FictionBook") throw ConversionException("This isn't a FictionBook file.")
        val desc = root.child("description")
        val info = desc?.child("title-info")
        val publish = desc?.child("publish-info")
        val authors = info?.elements()?.filter { it.localName == "author" }?.mapNotNull { a ->
            listOf("first-name", "middle-name", "last-name").mapNotNull { a.child(it)?.text()?.trim()?.ifEmpty { null } }.joinToString(" ")
                .ifEmpty { a.child("nickname")?.text()?.trim() }
        }.orEmpty().filter { it.isNotBlank() }
        val sequence = info?.elements()?.firstOrNull { it.localName == "sequence" }
        val annotation = info?.child("annotation")
        val meta = BookMetadata(
            title = info?.child("book-title")?.text()?.trim()?.ifEmpty { null } ?: TextConverter.titleFromName(fileName),
            authors = authors,
            language = info?.child("lang")?.text()?.trim()?.ifEmpty { null },
            series = sequence?.attr("name")?.trim()?.ifEmpty { null },
            seriesIndex = sequence?.attr("number")?.trim()?.toDoubleOrNull(),
            tags = info?.elements()?.filter { it.localName == "genre" }?.map { it.text().trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            publisher = publish?.child("publisher")?.text()?.trim()?.ifEmpty { null },
            published = (publish?.child("year") ?: info?.child("date"))?.let { it.attr("value") ?: it.text() }?.trim()?.ifEmpty { null },
            isbn = publish?.child("isbn")?.text()?.trim()?.ifEmpty { null },
            description = annotation?.let { a -> a.elements().joinToString("") { p -> "<p>${HtmlSoup.escapeText(p.text().trim())}</p>" } }?.ifEmpty { null },
        )
        val out = EpubOut(meta)
        // Pictures, by id.
        val images = HashMap<String, String>()
        for (b in root.elements().filter { it.localName == "binary" }) {
            val id = b.attr("id") ?: continue
            val data = runCatching { Base64.getMimeDecoder().decode(b.text().trim()) }.getOrNull() ?: continue
            val type = b.attr("content-type").orEmpty()
            val coverId = info?.child("coverpage")?.elements()?.firstOrNull()?.href()?.removePrefix("#")
            images[id] = out.image(if (id.contains('.')) id else id + "." + type.substringAfter('/').replace("jpeg", "jpg"), data, asCover = id == coverId)
        }
        out.coverPage()
        val bodies = root.elements().filter { it.localName == "body" }
        val main = bodies.firstOrNull { it.attr("name") == null } ?: bodies.firstOrNull() ?: throw ConversionException("There's no text in this FictionBook file.")
        val notes = bodies.filter { it !== main }
        // Each section (and what's before them) a page; where every id is, for links.
        val sections = ArrayList<Pair<XElement?, Int>>()
        fun collect(e: XElement, depth: Int) {
            val subs = e.elements().filter { it.localName == "section" }
            val titled = subs.count { it.child("title") != null }
            if (depth == 0 || (titled >= 2 && depth < 2 && subs.size > 1)) {
                sections += e to depth
                for (s in subs) collect(s, depth + 1)
            } else sections += e to depth
        }
        collect(main, 0)
        val paths = sections.indices.map { out.nextPagePath("part%04d.xhtml".format(it + 1)) }
        val notesPath = out.nextPagePath("notes.xhtml")
        val idPath = HashMap<String, String>()
        fun ids(e: XElement, path: String, own: Set<XElement>) {
            e.attr("id")?.let { idPath[it] = path }
            for (c in e.elements()) if (c !in own) ids(c, path, own)
        }
        val split: Set<XElement> = sections.mapNotNull { it.first }.toHashSet()
        sections.forEachIndexed { i, (e, _) -> if (e != null) ids(e, paths[i].substringAfterLast('/'), split - e) }
        for (b in notes) ids(b, notesPath.substringAfterLast('/'), emptySet())
        val ctx = Ctx(images, idPath)
        class T(val title: String, val path: String, val kids: MutableList<T> = ArrayList())
        val toc = ArrayList<T>()
        val stack = ArrayList<Pair<Int, MutableList<T>>>()
        sections.forEachIndexed { i, (e, depth) ->
            e ?: return@forEachIndexed
            val title = e.child("title")?.let { t -> t.elements().joinToString(": ") { it.text().trim() }.ifEmpty { t.text().trim() } }
            val html = StringBuilder()
            for (c in e.elements()) {
                if (c.localName == "section" && c in split) continue
                block(c, html, ctx, depth)
            }
            if (html.isBlank() && title == null) return@forEachIndexed
            val path = out.page(null, html.toString(), name = paths[i].substringAfterLast('/'))
            if (title != null || i == 0) {
                val node = T(title?.ifEmpty { null } ?: meta.title, path)
                while (stack.isNotEmpty() && stack.last().first >= depth) stack.removeAt(stack.size - 1)
                (stack.lastOrNull()?.second ?: toc) += node
                stack += depth to node.kids
            }
        }
        if (notes.isNotEmpty()) {
            val html = StringBuilder("<div class=\"notes\">\n")
            for (b in notes) for (c in b.elements()) block(c, html, ctx, 1)
            html.append("</div>")
            out.page(notes.first().child("title")?.text()?.trim()?.ifEmpty { null } ?: "Notes", html.toString(), name = "notes.xhtml", linear = false)
        }
        fun node(t: T): TocNode = TocNode(t.title, t.path, null, t.kids.map { node(it) })
        out.toc = toc.map { node(it) }.toMutableList()
        if (notes.isNotEmpty()) out.toc += TocNode("Notes", notesPath, null)
        out.write(target)
    }

    private class Ctx(val images: Map<String, String>, val ids: Map<String, String>)

    private fun XElement.child(name: String): XElement? = elements().firstOrNull { it.localName == name }
    private fun XElement.attr(name: String): String? = attributes[name] ?: attributes.entries.firstOrNull { it.key.substringAfter(':') == name && name != "href" }?.value
    private fun XElement.href(): String? = attributes.entries.firstOrNull { it.key == "href" || it.key.endsWith(":href") }?.value

    private fun idAttr(e: XElement) = e.attr("id")?.let { " id=\"${HtmlSoup.escapeAttr(it)}\"" }.orEmpty()

    private fun block(e: XElement, out: StringBuilder, ctx: Ctx, depth: Int) {
        when (e.localName) {
            "title" -> {
                val h = (depth + 1).coerceIn(1, 4)
                out.append("<h$h").append(idAttr(e)).append(">")
                out.append(e.elements().joinToString("<br/>") { inline(it, ctx) }.ifEmpty { HtmlSoup.escapeText(e.text()) })
                out.append("</h$h>\n")
            }
            "p" -> out.append("<p").append(idAttr(e)).append(">").append(inline(e, ctx)).append("</p>\n")
            "subtitle" -> out.append("<p class=\"center\"").append(idAttr(e)).append("><strong>").append(inline(e, ctx)).append("</strong></p>\n")
            "empty-line" -> out.append("<p class=\"space\"></p>\n")
            "image" -> e.href()?.removePrefix("#")?.let { ctx.images[it] }?.let { src ->
                out.append("<div class=\"picture\"").append(idAttr(e)).append("><img src=\"").append(HtmlSoup.escapeAttr(src)).append("\" alt=\"\"/></div>\n")
            }
            "epigraph", "cite", "annotation" -> {
                out.append("<blockquote").append(idAttr(e)).append(">\n")
                for (c in e.elements()) block(c, out, ctx, depth)
                out.append("</blockquote>\n")
            }
            "text-author" -> out.append("<p class=\"right\">— ").append(inline(e, ctx)).append("</p>\n")
            "poem" -> {
                out.append("<div class=\"poem\"").append(idAttr(e)).append(">\n")
                for (c in e.elements()) block(c, out, ctx, depth)
                out.append("</div>\n")
            }
            "stanza" -> {
                out.append("<p class=\"verse space\">")
                out.append(e.elements().filter { it.localName == "v" }.joinToString("<br/>") { inline(it, ctx) })
                out.append("</p>\n")
            }
            "v" -> out.append("<p class=\"verse\">").append(inline(e, ctx)).append("</p>\n")
            "section" -> {
                out.append("<div").append(idAttr(e)).append(">\n")
                for (c in e.elements()) block(c, out, ctx, depth + 1)
                out.append("</div>\n")
            }
            "table" -> {
                out.append("<table>")
                for (tr in e.elements()) {
                    out.append("<tr>")
                    for (td in tr.elements()) out.append(if (td.localName == "th") "<th>" else "<td>").append(inline(td, ctx)).append(if (td.localName == "th") "</th>" else "</td>")
                    out.append("</tr>")
                }
                out.append("</table>\n")
            }
            else -> if (e.elements().isNotEmpty()) for (c in e.elements()) block(c, out, ctx, depth) else if (e.text().isNotBlank()) out.append("<p>").append(HtmlSoup.escapeText(e.text())).append("</p>\n")
        }
    }

    private fun inline(e: XElement, ctx: Ctx): String = buildString {
        for (c in e.children) when (c) {
            is XText -> append(HtmlSoup.escapeText(c.text))
            is XElement -> {
                val inner = inline(c, ctx)
                when (c.localName) {
                    "emphasis" -> append("<em>").append(inner).append("</em>")
                    "strong" -> append("<strong>").append(inner).append("</strong>")
                    "strikethrough" -> append("<del>").append(inner).append("</del>")
                    "sup" -> append("<sup>").append(inner).append("</sup>")
                    "sub" -> append("<sub>").append(inner).append("</sub>")
                    "code" -> append("<code>").append(inner).append("</code>")
                    "a" -> {
                        val href = c.href().orEmpty()
                        val note = c.attributes["type"] == "note"
                        val target = if (href.startsWith("#")) ctx.ids[href.substring(1)]?.let { "$it$href" } else if (href.startsWith("http")) href else null
                        if (target == null) append(inner)
                        else {
                            if (note) append("<sup>")
                            append("<a href=\"").append(HtmlSoup.escapeAttr(target)).append('"')
                            if (note) append(" epub:type=\"noteref\"")
                            append('>').append(inner).append("</a>")
                            if (note) append("</sup>")
                        }
                    }
                    "image" -> c.href()?.removePrefix("#")?.let { ctx.images[it] }?.let { append("<img src=\"").append(HtmlSoup.escapeAttr(it)).append("\" alt=\"\"/>") }
                    else -> append(inner)
                }
            }
            else -> Unit
        }
    }
}
