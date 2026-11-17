package dev.kolib.app.convert

import dev.kolib.app.tr
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.epub.XElement
import dev.kolib.app.epub.XText
import dev.kolib.app.epub.Xml
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** A zip file's entries, read into memory (office documents are small). */
internal fun unzip(bytes: ByteArray, limit: Long = 400L * 1024 * 1024): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    var total = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        while (true) {
            val e = z.nextEntry ?: break
            if (e.isDirectory) continue
            val data = z.readBytes()
            total += data.size
            if (total > limit) throw ConversionException(tr("The file is too big to open."))
            out[e.name.replace('\\', '/').trimStart('/')] = data
        }
    }
    return out
}

private fun XElement.kids(name: String) = elements().filter { it.localName == name }
private fun XElement.kid(name: String) = elements().firstOrNull { it.localName == name }
private fun XElement.at(name: String): String? = attributes[name] ?: attributes.entries.firstOrNull { it.key.substringAfter(':') == name.substringAfter(':') }?.value
private fun parseXml(bytes: ByteArray?, what: String): XElement {
    bytes ?: throw ConversionException(tr("The document's {0} is missing.", what))
    return try {
        Xml.parse(TextDecoding.decode(bytes)).root
    } catch (e: Exception) {
        throw ConversionException(tr("The document's {0} is damaged.", what))
    }
}

/** Office documents' own details (docProps/core.xml, meta.xml): title, author, description, language. */
private fun officeMeta(xml: XElement?, fallbackTitle: String): BookMetadata {
    fun find(name: String): String? {
        var found: String? = null
        fun walk(e: XElement) {
            if (found != null) return
            if (e.localName == name && e.text().isNotBlank()) found = e.text().trim()
            e.elements().forEach { walk(it) }
        }
        xml?.let { walk(it) }
        return found
    }
    return BookMetadata(
        title = find("title") ?: fallbackTitle,
        authors = listOfNotNull(find("creator") ?: find("initial-creator")),
        description = find("description")?.let { "<p>${HtmlSoup.escapeText(it)}</p>" },
        language = find("language")?.substringBefore('-')?.lowercase(),
        tags = find("keywords")?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    )
}

/** Pieces of a document as paragraphs, with the level of heading each is (0: not a heading). */
private class Para(val html: String, val heading: Int = 0, val plain: String = "", val pageBreakBefore: Boolean = false)

/** Paragraphs as pages: a new one at each top heading (or, with no headings, at page breaks). */
private fun writePages(out: EpubOut, paras: List<Para>, notes: String?) {
    // The highest level of heading there's more than one of (a document's one title doesn't count).
    val splitHeading = (1..6).firstOrNull { lvl -> paras.count { it.heading == lvl } >= 2 }
    val byBreaks = splitHeading == null && paras.count { it.pageBreakBefore } >= 2
    var title: String? = null
    val body = StringBuilder()
    fun flush() {
        if (body.isNotBlank()) out.page(title ?: if (out.isEmpty) out.meta.title else null, body.toString())
        body.setLength(0)
        title = null
    }
    for (p in paras) {
        if ((splitHeading != null && p.heading == splitHeading) || (byBreaks && p.pageBreakBefore)) {
            flush()
            if (p.heading > 0) title = p.plain
        }
        body.append(p.html).append('\n')
    }
    flush()
    if (notes != null) out.page("Notes", "<div class=\"notes\">\n$notes</div>", name = "notes.xhtml", linear = false)
}

/**
 * A Word document (DOCX) as a book: its paragraphs with bold, italic and the rest, headings as chapters,
 * its pictures, tables, lists, links and footnotes.
 */
object DocxConverter {

    private class Ctx(
        val rels: Map<String, String>,
        val styles: Map<String, String>,
        val files: Map<String, ByteArray>,
        val out: EpubOut,
        val images: HashMap<String, String> = HashMap(),
        val notes: StringBuilder = StringBuilder(),
        val noteTexts: Map<String, XElement> = emptyMap(),
        val numbering: Map<String, Boolean> = emptyMap(),
    )

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        val files = unzip(bytes)
        val document = parseXml(files["word/document.xml"], "text")
        val meta = officeMeta(files["docProps/core.xml"]?.let { runCatching { parseXml(it, "details") }.getOrNull() }, TextConverter.titleFromName(fileName))
        val rels = HashMap<String, String>()
        files["word/_rels/document.xml.rels"]?.let { r ->
            runCatching { parseXml(r, "links") }.getOrNull()?.kids("Relationship")?.forEach { rel ->
                val id = rel.at("Id") ?: return@forEach
                val t = rel.at("Target") ?: return@forEach
                rels[id] = if (rel.at("TargetMode") == "External") t else "word/" + t.removePrefix("/word/").removePrefix("./")
            }
        }
        // Style ids to names ("Heading1" -> "heading 1").
        val styles = HashMap<String, String>()
        files["word/styles.xml"]?.let { s ->
            runCatching { parseXml(s, "styles") }.getOrNull()?.kids("style")?.forEach { st ->
                val id = st.at("w:styleId") ?: return@forEach
                styles[id] = st.kid("name")?.at("w:val")?.lowercase() ?: id.lowercase()
            }
        }
        val noteTexts = HashMap<String, XElement>()
        files["word/footnotes.xml"]?.let { f ->
            runCatching { parseXml(f, "footnotes") }.getOrNull()?.kids("footnote")?.forEach { fn -> fn.at("w:id")?.let { noteTexts[it] = fn } }
        }
        val out = EpubOut(meta)
        val ctx = Ctx(rels, styles, files, out, noteTexts = noteTexts)
        val body = document.kid("body") ?: throw ConversionException(tr("There's no text in this document."))
        val paras = ArrayList<Para>()
        var list: StringBuilder? = null
        fun endList() {
            list?.let { paras += Para("<ul>$it</ul>") }
            list = null
        }
        for (el in body.elements()) {
            when (el.localName) {
                "p" -> {
                    val p = paragraph(el, ctx)
                    if (el.kid("pPr")?.kid("numPr") != null && p.heading == 0) {
                        if (list == null) list = StringBuilder()
                        list!!.append("<li>").append(p.html.removePrefix("<p>").removeSuffix("</p>").substringAfter('>', p.html)).append("</li>")
                    } else {
                        endList()
                        paras += p
                    }
                }
                "tbl" -> {
                    endList()
                    paras += Para(table(el, ctx))
                }
                "sdt" -> el.kid("sdtContent")?.kids("p")?.forEach { endList(); paras += paragraph(it, ctx) }
            }
        }
        endList()
        // A cover: the first picture, if the document starts with one.
        writePages(out, paras, ctx.notes.toString().ifBlank { null })
        out.write(target)
    }

    private fun paragraph(p: XElement, ctx: Ctx): Para {
        val ppr = p.kid("pPr")
        val styleId = ppr?.kid("pStyle")?.at("w:val")
        val styleName = styleId?.let { ctx.styles[it] ?: it.lowercase() }.orEmpty()
        val heading = when {
            styleName == "title" -> 0
            styleName.startsWith("heading") -> styleName.removePrefix("heading").trim().toIntOrNull()?.coerceIn(1, 6) ?: 0
            else -> ppr?.kid("outlineLvl")?.at("w:val")?.toIntOrNull()?.let { it + 1 }?.coerceIn(1, 6) ?: 0
        }
        val align = when (ppr?.kid("jc")?.at("w:val")) {
            "center" -> " class=\"center\""
            "right", "end" -> " class=\"right\""
            else -> ""
        }
        val pageBreak = ppr?.kid("pageBreakBefore") != null || p.elements().any { r -> r.localName == "r" && r.kids("br").any { it.at("w:type") == "page" } && r === p.elements().firstOrNull { it.localName == "r" } }
        val inner = StringBuilder()
        runs(p, inner, ctx)
        val text = inner.toString().trim()
        val plain = HtmlSoup.decode(text.replace(Regex("<[^>]+>"), "")).trim()
        val quote = styleName.contains("quote")
        return when {
            // The document's title: shown as one, but not where chapters start.
            styleName == "title" && plain.isNotEmpty() -> Para("<h1$align>$text</h1>", 0, plain, pageBreak)
            heading > 0 && plain.isNotEmpty() -> Para("<h${heading.coerceAtMost(4)}$align>$text</h${heading.coerceAtMost(4)}>", heading, plain, pageBreak)
            quote -> Para("<blockquote><p>$text</p></blockquote>", 0, plain, pageBreak)
            text.isEmpty() -> Para("<p class=\"space\"></p>", 0, "", pageBreak)
            else -> Para("<p$align>$text</p>", 0, plain, pageBreak)
        }
    }

    private fun runs(parent: XElement, out: StringBuilder, ctx: Ctx) {
        for (c in parent.elements()) {
            when (c.localName) {
                "r" -> run(c, out, ctx)
                "hyperlink" -> {
                    val href = c.at("r:id")?.let { ctx.rels[it] }?.takeIf { it.startsWith("http") || it.startsWith("mailto:") }
                    if (href != null) out.append("<a href=\"").append(HtmlSoup.escapeAttr(href)).append("\">")
                    runs(c, out, ctx)
                    if (href != null) out.append("</a>")
                }
                "ins", "smartTag", "customXml", "fldSimple", "sdt", "sdtContent" -> runs(c, out, ctx)
            }
        }
    }

    private fun on(e: XElement?) = e != null && e.at("w:val").let { it == null || (it != "0" && it != "false" && it != "none") }

    private fun run(r: XElement, out: StringBuilder, ctx: Ctx) {
        val rpr = r.kid("rPr")
        val open = ArrayList<String>()
        if (on(rpr?.kid("b"))) open += "strong"
        if (on(rpr?.kid("i"))) open += "em"
        if (on(rpr?.kid("u"))) open += "u"
        if (on(rpr?.kid("strike")) || on(rpr?.kid("dstrike"))) open += "del"
        when (rpr?.kid("vertAlign")?.at("w:val")) {
            "superscript" -> open += "sup"
            "subscript" -> open += "sub"
        }
        val smallCaps = on(rpr?.kid("smallCaps"))
        val text = StringBuilder()
        for (c in r.elements()) {
            when (c.localName) {
                "t" -> text.append(HtmlSoup.escapeText(c.text()))
                "tab" -> text.append(' ')
                "br", "cr" -> if (c.at("w:type") != "page") text.append("<br/>")
                "noBreakHyphen" -> text.append('‑')
                "softHyphen" -> text.append('­')
                "sym" -> c.at("w:char")?.toIntOrNull(16)?.let { if (it in 0x20..0xFFFF) text.appendCodePoint(it) }
                "drawing", "pict", "object" -> picture(c, ctx)?.let { text.append("<img src=\"").append(HtmlSoup.escapeAttr(it)).append("\" alt=\"\"/>") }
                "footnoteReference" -> c.at("w:id")?.let { id ->
                    val note = ctx.noteTexts[id] ?: return@let
                    val n = Regex("""id="fn(\d+)"""").findAll(ctx.notes).count() + 1
                    text.append("<sup><a id=\"ref$n\" href=\"notes.xhtml#fn$n\" epub:type=\"noteref\">$n</a></sup>")
                    val body = StringBuilder()
                    for (p in note.kids("p")) {
                        val inner = StringBuilder()
                        runs(p, inner, ctx)
                        if (inner.isNotBlank()) body.append(inner).append(' ')
                    }
                    ctx.notes.append("<p id=\"fn$n\"><a href=\"").append("#ref$n").append("\">$n.</a> ").append(body.toString().trim()).append("</p>\n")
                }
            }
        }
        if (text.isEmpty()) return
        for (t in open) out.append('<').append(t).append('>')
        if (smallCaps) out.append("<span style=\"font-variant: small-caps\">")
        out.append(text)
        if (smallCaps) out.append("</span>")
        for (t in open.asReversed()) out.append("</").append(t).append('>')
    }

    /** A picture in the document, added to the book; its address from a page. */
    private fun picture(e: XElement, ctx: Ctx): String? {
        var id: String? = null
        fun walk(x: XElement) {
            if (id != null) return
            if (x.localName == "blip") id = x.at("r:embed") ?: x.at("r:link")
            if (x.localName == "imagedata") id = x.at("r:id")
            x.elements().forEach { walk(it) }
        }
        walk(e)
        val path = id?.let { ctx.rels[it] } ?: return null
        return ctx.images.getOrPut(path) {
            val bytes = ctx.files[path] ?: return null
            if (path.endsWith(".emf", true) || path.endsWith(".wmf", true)) return null
            ctx.out.image(path.substringAfterLast('/'), bytes)
        }
    }

    private fun table(t: XElement, ctx: Ctx): String = buildString {
        append("<table>")
        for (tr in t.kids("tr")) {
            append("<tr>")
            for (tc in tr.kids("tc")) {
                append("<td>")
                tc.kids("p").forEachIndexed { i, p ->
                    if (i > 0) append("<br/>")
                    runs(p, this, ctx)
                }
                append("</td>")
            }
            append("</tr>")
        }
        append("</table>")
    }
}

/**
 * An OpenDocument text (ODT, from LibreOffice) as a book: its paragraphs and their styles, headings as
 * chapters, its pictures, lists, tables, links and notes.
 */
object OdtConverter {

    private class Style(val bold: Boolean, val italic: Boolean, val underline: Boolean, val strike: Boolean, val position: String?, val align: String?, val heading: Int, val parent: String?)

    private class Ctx(val styles: Map<String, Style>, val files: Map<String, ByteArray>, val out: EpubOut, val notes: StringBuilder = StringBuilder(), val images: HashMap<String, String> = HashMap()) {
        var noteCount = 0
    }

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        val files = unzip(bytes)
        val content = parseXml(files["content.xml"], "text")
        val meta = officeMeta(files["meta.xml"]?.let { runCatching { parseXml(it, "details") }.getOrNull() }, TextConverter.titleFromName(fileName))
        val styles = HashMap<String, Style>()
        fun read(root: XElement?) {
            root ?: return
            fun walk(e: XElement) {
                if (e.localName == "style" && e.at("style:name") != null) {
                    val tp = e.kid("text-properties")
                    val pp = e.kid("paragraph-properties")
                    val name = e.at("style:name")!!
                    val display = e.at("style:display-name")?.lowercase().orEmpty()
                    val heading = Regex("""heading[ _]*(?:_20_)?(\d)""").find(name.lowercase() + " " + display)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    styles[name] = Style(
                        bold = tp?.at("fo:font-weight")?.let { it == "bold" || (it.toIntOrNull() ?: 0) >= 600 } == true,
                        italic = tp?.at("fo:font-style") == "italic",
                        underline = tp?.at("style:text-underline-style")?.let { it != "none" } == true,
                        strike = tp?.at("style:text-line-through-style")?.let { it != "none" } == true,
                        position = tp?.at("style:text-position"),
                        align = pp?.at("fo:text-align"),
                        heading = heading,
                        parent = e.at("style:parent-style-name"),
                    )
                }
                e.elements().forEach { walk(it) }
            }
            walk(root)
        }
        read(files["styles.xml"]?.let { runCatching { parseXml(it, "styles") }.getOrNull() })
        read(content)
        val out = EpubOut(meta)
        val ctx = Ctx(styles, files, out)
        val text = content.kid("body")?.kid("text") ?: throw ConversionException(tr("There's no text in this document."))
        val paras = ArrayList<Para>()
        blocks(text, paras, ctx)
        writePages(out, paras, ctx.notes.toString().ifBlank { null })
        out.write(target)
    }

    private fun style(name: String?, ctx: Ctx): Style? = name?.let { ctx.styles[it] }

    /** A style's setting, looking through the styles it's based on. */
    private fun <T> inherited(name: String?, ctx: Ctx, get: (Style) -> T?): T? {
        var n = name
        var guard = 0
        while (n != null && guard++ < 10) {
            val s = ctx.styles[n] ?: return null
            get(s)?.let { return it }
            n = s.parent
        }
        return null
    }

    private fun blocks(parent: XElement, paras: MutableList<Para>, ctx: Ctx) {
        for (e in parent.elements()) {
            when (e.localName) {
                "h" -> {
                    val level = e.at("text:outline-level")?.toIntOrNull() ?: inherited(e.at("text:style-name"), ctx) { s -> s.heading.takeIf { it > 0 } } ?: 1
                    val inner = inline(e, ctx).trim()
                    val plain = HtmlSoup.decode(inner.replace(Regex("<[^>]+>"), "")).trim()
                    if (plain.isNotEmpty()) paras += Para("<h${level.coerceIn(1, 4)}>$inner</h${level.coerceIn(1, 4)}>", level, plain)
                }
                "p" -> {
                    val styleName = e.at("text:style-name")
                    val heading = inherited(styleName, ctx) { s -> s.heading.takeIf { it > 0 } } ?: 0
                    val inner = inline(e, ctx).trim()
                    val plain = HtmlSoup.decode(inner.replace(Regex("<[^>]+>"), "")).trim()
                    val align = when (inherited(styleName, ctx) { it.align }) {
                        "center" -> " class=\"center\""
                        "end", "right" -> " class=\"right\""
                        else -> ""
                    }
                    paras += when {
                        heading > 0 && plain.isNotEmpty() -> Para("<h${heading.coerceIn(1, 4)}$align>$inner</h${heading.coerceIn(1, 4)}>", heading, plain)
                        inner.isEmpty() -> Para("<p class=\"space\"></p>")
                        else -> Para("<p$align>$inner</p>", 0, plain)
                    }
                }
                "list" -> paras += Para(list(e, ctx))
                "table" -> paras += Para(table(e, ctx))
                "section", "index-body", "table-of-content" -> if (e.localName != "table-of-content") blocks(e, paras, ctx)
            }
        }
    }

    private fun list(l: XElement, ctx: Ctx): String = buildString {
        append("<ul>")
        for (item in l.kids("list-item")) {
            append("<li>")
            item.elements().forEachIndexed { i, c ->
                when (c.localName) {
                    "p", "h" -> {
                        if (i > 0) append("<br/>")
                        append(inline(c, ctx))
                    }
                    "list" -> append(list(c, ctx))
                }
            }
            append("</li>")
        }
        append("</ul>")
    }

    private fun table(t: XElement, ctx: Ctx): String = buildString {
        append("<table>")
        fun rows(e: XElement) {
            for (c in e.elements()) when (c.localName) {
                "table-row" -> {
                    append("<tr>")
                    for (cell in c.kids("table-cell")) {
                        append("<td>")
                        cell.elements().filter { it.localName == "p" || it.localName == "h" }.forEachIndexed { i, p ->
                            if (i > 0) append("<br/>")
                            append(inline(p, ctx))
                        }
                        append("</td>")
                    }
                    append("</tr>")
                }
                "table-header-rows", "table-rows", "table-row-group" -> rows(c)
            }
        }
        rows(t)
        append("</table>")
    }

    private fun inline(e: XElement, ctx: Ctx): String = buildString {
        for (c in e.children) when (c) {
            is XText -> append(HtmlSoup.escapeText(c.text))
            is XElement -> when (c.localName) {
                "span" -> {
                    val s = style(c.at("text:style-name"), ctx)
                    val tags = ArrayList<String>()
                    if (s?.bold == true) tags += "strong"
                    if (s?.italic == true) tags += "em"
                    if (s?.underline == true) tags += "u"
                    if (s?.strike == true) tags += "del"
                    // "super 58%", "33% 58%": raised; "sub 58%", "-33% 58%": lowered.
                    s?.position?.trim()?.substringBefore(' ')?.let { p ->
                        when {
                            p == "super" -> tags += "sup"
                            p == "sub" -> tags += "sub"
                            p.endsWith("%") -> p.dropLast(1).toDoubleOrNull()?.let { v -> if (v > 0) tags += "sup" else if (v < 0) tags += "sub" }
                        }
                    }
                    for (t in tags) append('<').append(t).append('>')
                    append(inline(c, ctx))
                    for (t in tags.asReversed()) append("</").append(t).append('>')
                }
                "s" -> append(" ".repeat((c.at("text:c")?.toIntOrNull() ?: 1).coerceIn(1, 8)))
                "tab" -> append(' ')
                "line-break" -> append("<br/>")
                "a" -> {
                    val href = c.at("xlink:href").orEmpty()
                    if (href.startsWith("http") || href.startsWith("mailto:")) append("<a href=\"").append(HtmlSoup.escapeAttr(href)).append("\">").append(inline(c, ctx)).append("</a>")
                    else append(inline(c, ctx))
                }
                "frame" -> c.kid("image")?.at("xlink:href")?.let { href ->
                    val bytes = ctx.files[href.removePrefix("./")] ?: return@let
                    val src = ctx.images.getOrPut(href) { ctx.out.image(href.substringAfterLast('/'), bytes) }
                    append("<img src=\"").append(HtmlSoup.escapeAttr(src)).append("\" alt=\"\"/>")
                }
                "note" -> {
                    ctx.noteCount++
                    val n = ctx.noteCount
                    append("<sup><a id=\"ref$n\" href=\"notes.xhtml#fn$n\" epub:type=\"noteref\">$n</a></sup>")
                    val body = c.kid("note-body")?.elements()?.joinToString(" ") { inline(it, ctx) }.orEmpty()
                    ctx.notes.append("<p id=\"fn$n\"><a href=\"#ref$n\">$n.</a> ").append(body).append("</p>\n")
                }
                "bookmark", "bookmark-start", "bookmark-end", "soft-page-break", "annotation", "annotation-end", "sequence-decls", "tracked-changes" -> Unit
                else -> append(inline(c, ctx))
            }
            else -> Unit
        }
    }
}
