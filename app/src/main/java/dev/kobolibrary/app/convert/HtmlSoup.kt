package dev.kobolibrary.app.convert

import dev.kobolibrary.app.epub.HtmlEntities

/**
 * Reads HTML however it's written (unclosed paragraphs, capital letters, unquoted attributes, as old
 * web pages and Kindle books have it) into a tree, and writes the tree back out as well-formed XHTML.
 */
object HtmlSoup {

    sealed class Node
    class Text(var text: String) : Node()
    class Element(var name: String, val attrs: LinkedHashMap<String, String> = LinkedHashMap()) : Node() {
        val children: MutableList<Node> = ArrayList()
        var parent: Element? = null

        fun add(n: Node) {
            if (n is Element) n.parent = this
            children += n
        }

        fun elements(): Sequence<Element> = sequence {
            for (c in children) if (c is Element) {
                yield(c)
                yieldAll(c.elements())
            }
        }

        fun first(name: String): Element? = elements().firstOrNull { it.name == name }

        fun text(): String = buildString { appendText(this@Element, this) }

        private fun appendText(e: Element, sb: StringBuilder) {
            for (c in e.children) when (c) {
                is Text -> sb.append(c.text)
                is Element -> appendText(c, sb)
            }
        }
    }

    private val void = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
        "basefont", "frame", "keygen", "mbp:pagebreak",
    )
    private val rawText = setOf("script", "style", "xmp", "textarea", "title")
    /** Starting one of these ends a paragraph that's still open. */
    private val closesP = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dl", "table", "blockquote", "pre", "hr",
        "section", "article", "aside", "header", "footer", "nav", "figure", "address", "center", "form", "fieldset",
        "mbp:pagebreak", "li", "dt", "dd",
    )
    /** Where looking back for an element to close stops. */
    private val scopes = setOf("table", "td", "th", "html", "body", "blockquote", "li", "div", "section", "svg")

    /** [html] as a tree under an "html" element (with "head" and "body" if the page has them). */
    fun parse(html: String): Element {
        val root = Element("#root")
        var cur = root
        var i = 0
        val n = html.length
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                val t = decode(text.toString())
                val last = cur.children.lastOrNull()
                if (last is Text) last.text += t else cur.add(Text(t))
                text.setLength(0)
            }
        }
        fun inSvg(): Boolean {
            var e: Element? = cur
            while (e != null) {
                if (e.name == "svg" || e.name == "math") return true
                e = e.parent
            }
            return false
        }
        fun close(name: String): Boolean {
            var e: Element? = cur
            while (e != null && e !== root) {
                if (e.name == name) {
                    cur = e.parent ?: root
                    return true
                }
                if (e.name in scopes && name !in scopes) return false
                e = e.parent
            }
            return false
        }
        /** Closes the nearest open element named one of [names], unless one of [stopAt] comes first. */
        fun closeNearest(names: Set<String>, stopAt: Set<String>) {
            var e: Element? = cur
            while (e != null && e !== root) {
                if (e.name in names) {
                    cur = e.parent ?: root
                    return
                }
                if (e.name in stopAt) return
                e = e.parent
            }
        }
        fun open(name: String, attrs: LinkedHashMap<String, String>, selfClosing: Boolean) {
            if (!inSvg()) {
                if (name in closesP) closeNearest(setOf("p"), stopAt = scopes)
                when (name) {
                    "li" -> closeNearest(setOf("li"), stopAt = setOf("ul", "ol", "table"))
                    "dt", "dd" -> closeNearest(setOf("dt", "dd"), stopAt = setOf("dl", "table"))
                    "tr" -> closeNearest(setOf("tr"), stopAt = setOf("table", "tbody", "thead", "tfoot"))
                    "td", "th" -> closeNearest(setOf("td", "th"), stopAt = setOf("tr", "table"))
                    "option" -> closeNearest(setOf("option"), stopAt = setOf("select"))
                }
            }
            val e = Element(name, attrs)
            cur.add(e)
            if (!selfClosing && name !in void) cur = e
        }
        while (i < n) {
            val c = html[i]
            if (c != '<') {
                text.append(c)
                i++
                continue
            }
            when {
                html.startsWith("<!--", i) -> {
                    flush()
                    val end = html.indexOf("-->", i + 4)
                    i = if (end < 0) n else end + 3
                }
                html.startsWith("<![CDATA[", i) -> {
                    flush()
                    val end = html.indexOf("]]>", i + 9)
                    val t = if (end < 0) html.substring(i + 9) else html.substring(i + 9, end)
                    cur.add(Text(t))
                    i = if (end < 0) n else end + 3
                }
                html.startsWith("<!", i) || html.startsWith("<?", i) -> {
                    flush()
                    val end = html.indexOf('>', i + 2)
                    i = if (end < 0) n else end + 1
                }
                i + 1 < n && html[i + 1] == '/' -> {
                    val end = html.indexOf('>', i + 2)
                    if (end < 0) {
                        text.append(html, i, n)
                        i = n
                        continue
                    }
                    flush()
                    val raw = html.substring(i + 2, end).trim()
                    val name = raw.takeWhile { !it.isWhitespace() }.let { if (inSvg()) it else it.lowercase() }
                    if (name.isNotEmpty() && name !in void) close(name)
                    i = end + 1
                }
                i + 1 < n && (html[i + 1].isLetter()) -> {
                    val tag = readTag(html, i, inSvg()) ?: run {
                        text.append(c)
                        i++
                        null
                    } ?: continue
                    flush()
                    open(tag.name, tag.attrs, tag.selfClosing)
                    i = tag.end
                    if (tag.name in rawText && !tag.selfClosing) {
                        val endTag = Regex("</" + Regex.escape(tag.name) + "\\s*>", RegexOption.IGNORE_CASE).find(html, i)
                        val body = if (endTag == null) html.substring(i) else html.substring(i, endTag.range.first)
                        if (body.isNotEmpty()) cur.add(Text(if (tag.name == "title" || tag.name == "textarea") decode(body) else body))
                        cur = cur.parent ?: root
                        i = endTag?.range?.last?.plus(1) ?: n
                    }
                }
                else -> {
                    text.append(c)
                    i++
                }
            }
        }
        flush()
        // As a page: everything under "html", with its "head" and "body".
        val html0 = root.children.filterIsInstance<Element>().firstOrNull { it.name == "html" }
        val doc = html0 ?: Element("html").also { h -> root.children.toList().forEach { h.add(it) } }
        if (doc.children.none { it is Element && it.name == "body" }) {
            val body = Element("body")
            val head = doc.children.filterIsInstance<Element>().firstOrNull { it.name == "head" }
            for (c in doc.children.toList()) if (c !== head) body.add(c)
            doc.children.clear()
            head?.let { doc.add(it) }
            doc.add(body)
        }
        return doc
    }

    private class Tag(val name: String, val attrs: LinkedHashMap<String, String>, val selfClosing: Boolean, val end: Int)

    private fun readTag(s: String, start: Int, svg: Boolean): Tag? {
        var i = start + 1
        val n = s.length
        val nameStart = i
        while (i < n && (s[i].isLetterOrDigit() || s[i] == ':' || s[i] == '-' || s[i] == '_' || s[i] == '.')) i++
        if (i == nameStart) return null
        val rawName = s.substring(nameStart, i)
        val name = if (svg || rawName == "svg" || rawName == "math") rawName.let { if (it.equals("svg", true)) "svg" else it } else rawName.lowercase()
        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (i < n) {
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n) return null
            if (s[i] == '>') return Tag(name, attrs, selfClosing, i + 1)
            if (s[i] == '/') {
                selfClosing = true
                i++
                continue
            }
            if (s[i] == '<') return Tag(name, attrs, selfClosing, i) // a tag left open: stop before the next one
            val an = i
            while (i < n && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && !(s[i] == '/' && i + 1 < n && s[i + 1] == '>')) i++
            val attrName = s.substring(an, i).let { if (svg) it else it.lowercase() }
            while (i < n && s[i].isWhitespace()) i++
            var value = ""
            if (i < n && s[i] == '=') {
                i++
                while (i < n && s[i].isWhitespace()) i++
                if (i < n && (s[i] == '"' || s[i] == '\'')) {
                    val q = s[i]
                    val end = s.indexOf(q, i + 1)
                    if (end < 0) return null
                    value = s.substring(i + 1, end)
                    i = end + 1
                } else {
                    val vs = i
                    while (i < n && !s[i].isWhitespace() && s[i] != '>') i++
                    value = s.substring(vs, i)
                }
            }
            selfClosing = false
            if (attrName.isNotEmpty() && attrName !in attrs) attrs[attrName] = decode(value)
        }
        return null
    }

    private val entity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);?")

    /** Text with its character references (&amp;, &#233;, &eacute;) turned into the characters. */
    fun decode(t: String): String {
        if (t.indexOf('&') < 0) return t
        return entity.replace(t) { m ->
            val e = m.groupValues[1]
            val cp = when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)
                e.startsWith("#") -> e.substring(1).toIntOrNull()
                else -> when (e) {
                    "amp" -> 38; "lt" -> 60; "gt" -> 62; "quot" -> 34; "apos" -> 39
                    else -> HtmlEntities.codepoints[e]
                }
            }
            if (cp != null && Character.isValidCodePoint(cp) && cp != 0) {
                // Windows' own characters, written as their codes in Latin-1's range (&#151; for a dash).
                val fixed = if (cp in 0x80..0x9F) cp1252[cp - 0x80].code else cp
                String(Character.toChars(fixed))
            } else if (!m.value.endsWith(";")) m.value else m.value
        }
    }

    private val cp1252 = "€\u0081‚ƒ„…†‡ˆ‰Š‹Œ\u008DŽ\u008F\u0090‘’“”•–—˜™š›œ\u009DžŸ"

    // ---- writing ----------------------------------------------------------------------------------

    private val xmlName = Regex("^[A-Za-z_][A-Za-z0-9._:-]*$")

    /** [e]'s children as XHTML. */
    fun inner(e: Element): String = buildString { for (c in e.children) write(c, this) }

    /** [n] as XHTML. */
    fun write(n: Node, out: StringBuilder) {
        when (n) {
            is Text -> out.append(escapeText(n.text))
            is Element -> {
                if (n.name.startsWith("#")) {
                    for (c in n.children) write(c, out)
                    return
                }
                out.append('<').append(n.name)
                for ((k, v) in n.attrs) {
                    if (!xmlName.matches(k) || k.startsWith("xmlns") && n.name != "svg" && n.name != "math" && n.name != "html") continue
                    if (k.contains(':') && !k.startsWith("xml:") && !k.startsWith("xlink:") && !k.startsWith("epub:") && !k.startsWith("xmlns")) continue
                    out.append(' ').append(k).append("=\"").append(escapeAttr(v)).append('"')
                }
                if (n.name == "svg" && "xmlns" !in n.attrs) out.append(" xmlns=\"http://www.w3.org/2000/svg\"")
                if (n.name == "svg" && "xmlns:xlink" !in n.attrs && n.elements().any { e -> e.attrs.keys.any { it.startsWith("xlink:") } }) {
                    out.append(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
                }
                if (n.name == "math" && "xmlns" !in n.attrs) out.append(" xmlns=\"http://www.w3.org/1998/Math/MathML\"")
                if (n.children.isEmpty() && (n.name in void || n.name.contains(':') || isSvgLeaf(n))) {
                    out.append("/>")
                } else {
                    out.append('>')
                    if (n.name == "style" || n.name == "script") {
                        val body = n.children.joinToString("") { (it as? Text)?.text.orEmpty() }
                        out.append(body.replace("]]>", "]]]]><![CDATA[>").let { if (it.contains('<') || it.contains('&')) "/*<![CDATA[*/$it/*]]>*/" else it })
                    } else for (c in n.children) write(c, out)
                    out.append("</").append(n.name).append('>')
                }
            }
        }
    }

    private fun isSvgLeaf(e: Element): Boolean {
        var p: Element? = e.parent
        while (p != null) {
            if (p.name == "svg") return true
            p = p.parent
        }
        return false
    }

    fun escapeText(s: String): String = buildString(s.length + 8) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> if (ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r') Unit else append(ch)
        }
    }

    fun escapeAttr(s: String): String = escapeText(s).replace("\"", "&quot;")
}
