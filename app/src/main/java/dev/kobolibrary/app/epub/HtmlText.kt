package dev.kobolibrary.app.epub

/**
 * Book descriptions are small pieces of HTML. This turns whatever a book (or the description
 * editor) holds into plain, safe HTML that readers show well: paragraphs, bold, italic, underline,
 * lists, quotes and line breaks. Everything else is unwrapped, and scripts and styles are dropped.
 */
object HtmlText {

    private sealed interface Node
    private class Text(val text: String) : Node
    private class El(val tag: String, val parent: El?) : Node {
        val children = mutableListOf<Node>()
    }

    private val dropWithContent = setOf(
        "script", "style", "head", "title", "iframe", "object", "embed", "noscript", "svg", "math", "template",
        "select", "textarea", "button", "video", "audio", "canvas",
    )
    private val voidTags = setOf("br", "img", "hr", "input", "meta", "link", "wbr", "col", "area", "base", "source", "param")
    private val blockTags = setOf(
        "p", "div", "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article",
        "header", "footer", "aside", "main", "nav", "figure", "figcaption", "table", "tbody", "thead", "tfoot", "tr",
        "td", "th", "dl", "dt", "dd", "pre", "address", "center", "body", "html",
    )
    private val paragraphLike = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "address", "center",
        "dt", "dd", "td", "th", "figcaption", "tr")
    private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    private val tagPattern = Regex("""<(/?)([A-Za-z][A-Za-z0-9:-]*)((?:\s+[^>]*?)?)\s*(/?)>""")

    /** Clean HTML, or "" when there's no text at all. */
    fun sanitize(html: String): String {
        val root = parse(html)
        val out = StringBuilder()
        writeBlocks(root.children, out)
        return out.toString()
    }

    /** Plain text typed or pasted by someone, as paragraphs. */
    fun fromPlainText(text: String): String =
        text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .map { para -> para.trim().lines().joinToString("<br/>") { escape(it.trim()) } }
            .filter { it.isNotEmpty() }
            .joinToString("") { "<p>$it</p>" }

    /** True when the HTML has no visible text. */
    fun isBlank(html: String?): Boolean = html == null || sanitize(html).isEmpty()

    // ---- parsing ----------------------------------------------------------------------------

    private fun parse(html: String): El {
        val root = El("#root", null)
        var current = root
        var i = 0
        val s = html
        fun addText(t: String) {
            if (t.isNotEmpty()) current.children += Text(decodeEntities(t))
        }
        fun closeTo(tag: String): Boolean {
            var e: El? = current
            while (e != null && e !== root) {
                if (e.tag == tag) {
                    current = e.parent ?: root
                    return true
                }
                e = e.parent
            }
            return false
        }
        fun inScope(tag: String, stopAt: Set<String> = emptySet()): Boolean {
            var e: El? = current
            while (e != null && e !== root) {
                if (e.tag == tag) return true
                if (e.tag in stopAt) return false
                e = e.parent
            }
            return false
        }
        while (i < s.length) {
            val lt = s.indexOf('<', i)
            if (lt < 0) {
                addText(s.substring(i))
                break
            }
            addText(s.substring(i, lt))
            when {
                s.startsWith("<!--", lt) -> {
                    val end = s.indexOf("-->", lt + 4)
                    i = if (end < 0) s.length else end + 3
                    continue
                }
                s.startsWith("<![CDATA[", lt) -> {
                    val end = s.indexOf("]]>", lt + 9)
                    val text = if (end < 0) s.substring(lt + 9) else s.substring(lt + 9, end)
                    current.children += Text(text)
                    i = if (end < 0) s.length else end + 3
                    continue
                }
                s.startsWith("<!", lt) || s.startsWith("<?", lt) -> {
                    val end = s.indexOf('>', lt)
                    i = if (end < 0) s.length else end + 1
                    continue
                }
            }
            val m = tagPattern.matchAt(s, lt)
            if (m == null) {
                addText("<")
                i = lt + 1
                continue
            }
            i = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].lowercase().substringAfter(':')
            val selfClosing = m.groupValues[4] == "/"
            if (tag in dropWithContent) {
                if (!closing && !selfClosing) {
                    val end = Regex("</\\s*(?:[A-Za-z0-9-]+:)?$tag\\s*>", RegexOption.IGNORE_CASE).find(s, i)
                    i = end?.range?.last?.plus(1) ?: s.length
                }
                continue
            }
            if (closing) {
                if (tag == "br") current.children += El("br", current) // </br> is read as <br> by browsers
                else closeTo(tag)
                continue
            }
            // Implied ends, as browsers do them.
            if (tag == "p" || tag in blockTags && tag != "li") {
                if (inScope("p", setOf("li", "blockquote", "td", "th"))) closeTo("p")
            }
            if (tag == "li" && inScope("li", setOf("ul", "ol"))) closeTo("li")
            val el = El(tag, current)
            current.children += el
            if (tag !in voidTags && !selfClosing) current = el
        }
        return root
    }

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“",
        "rdquo" to "”", "laquo" to "«", "raquo" to "»", "copy" to "©", "reg" to "®", "trade" to "™",
        "bull" to "•", "middot" to "·", "deg" to "°", "times" to "×", "frac12" to "½", "frac14" to "¼",
        "frac34" to "¾", "pound" to "£", "euro" to "€", "yen" to "¥", "cent" to "¢", "sect" to "§",
        "para" to "¶", "dagger" to "†", "Dagger" to "‡", "shy" to "­", "thinsp" to " ",
        "ensp" to " ", "emsp" to " ", "zwnj" to "‌", "zwj" to "‍",
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
        "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í", "Oacute" to "Ó", "Uacute" to "Ú",
        "agrave" to "à", "egrave" to "è", "igrave" to "ì", "ograve" to "ò", "ugrave" to "ù",
        "Agrave" to "À", "Egrave" to "È", "acirc" to "â", "ecirc" to "ê", "icirc" to "î", "ocirc" to "ô",
        "ucirc" to "û", "auml" to "ä", "euml" to "ë", "iuml" to "ï", "ouml" to "ö", "uuml" to "ü",
        "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß", "ccedil" to "ç", "Ccedil" to "Ç",
        "ntilde" to "ñ", "Ntilde" to "Ñ", "aring" to "å", "Aring" to "Å", "aelig" to "æ", "AElig" to "Æ",
        "oslash" to "ø", "Oslash" to "Ø", "iexcl" to "¡", "iquest" to "¿",
    )

    private fun decodeEntities(t: String): String {
        if (!t.contains('&')) return t
        val sb = StringBuilder(t.length)
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '&') {
                val semi = t.indexOf(';', i + 1)
                if (semi > i + 1 && semi - i <= 12) {
                    val ent = t.substring(i + 1, semi)
                    val decoded = when {
                        ent.startsWith("#x") || ent.startsWith("#X") -> ent.substring(2).toIntOrNull(16)
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()
                        else -> null
                    }?.takeIf { Character.isValidCodePoint(it) && it != 0 }?.let { String(Character.toChars(it)) }
                        ?: namedEntities[ent]
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    // ---- writing ----------------------------------------------------------------------------

    private fun isBlockish(n: Node) = n is El && n.tag in blockTags

    /** Children at block level: runs of inline content become paragraphs. */
    private fun writeBlocks(nodes: List<Node>, out: StringBuilder) {
        val run = mutableListOf<Node>()
        fun flush() {
            if (run.isEmpty()) return
            paragraph(run, out)
            run.clear()
        }
        for (n in nodes) {
            if (n is El && (n.tag in blockTags || n.tag == "hr")) {
                flush()
                when (n.tag) {
                    "hr" -> Unit
                    "ul", "ol" -> list(n, out)
                    "li" -> list(El("ul", null).also { it.children += n }, out)
                    "blockquote" -> {
                        val inner = StringBuilder()
                        writeBlocks(n.children, inner)
                        if (inner.isNotEmpty()) out.append("<blockquote>").append(inner).append("</blockquote>")
                    }
                    in headings -> {
                        val inner = inline(n.children).trim()
                        if (inner.isNotEmpty()) out.append("<p><b>").append(inner).append("</b></p>")
                    }
                    in paragraphLike -> {
                        if (n.children.any { isBlockish(it) }) writeBlocks(n.children, out) else paragraph(n.children, out)
                    }
                    else -> writeBlocks(n.children, out)
                }
            } else {
                run += n
            }
        }
        flush()
    }

    private fun paragraph(nodes: List<Node>, out: StringBuilder) {
        val inner = trimBreaks(inline(nodes).trim())
        if (inner.isNotEmpty()) out.append("<p>").append(inner).append("</p>")
    }

    private fun list(el: El, out: StringBuilder) {
        val items = StringBuilder()
        for (child in el.children) {
            when {
                child is El && child.tag == "li" -> {
                    val nested = child.children.filter { it is El && (it.tag == "ul" || it.tag == "ol") }
                    val text = trimBreaks(inline(child.children.filter { it !in nested }).trim())
                    val sub = StringBuilder()
                    nested.forEach { list(it as El, sub) }
                    if (text.isNotEmpty() || sub.isNotEmpty()) items.append("<li>").append(text).append(sub).append("</li>")
                }
                child is El && (child.tag == "ul" || child.tag == "ol") -> list(child, items)
                else -> {
                    val text = trimBreaks(inline(listOf(child)).trim())
                    if (text.isNotEmpty()) items.append("<li>").append(text).append("</li>")
                }
            }
        }
        if (items.isNotEmpty()) {
            val tag = if (el.tag == "ol") "ol" else "ul"
            out.append('<').append(tag).append('>').append(items).append("</").append(tag).append('>')
        }
    }

    private fun inline(nodes: List<Node>): String {
        val sb = StringBuilder()
        for (n in nodes) {
            when (n) {
                is Text -> sb.append(escape(collapse(n.text)))
                is El -> when (n.tag) {
                    "br" -> sb.append("<br/>")
                    "b", "strong" -> wrap(sb, "b", inline(n.children))
                    "i", "em", "cite", "dfn", "var" -> wrap(sb, "i", inline(n.children))
                    "u", "ins" -> wrap(sb, "u", inline(n.children))
                    "img", "hr", "wbr" -> Unit
                    in blockTags -> {
                        // A block inside inline content: keep its text on its own line.
                        val inner = inline(n.children).trim()
                        if (inner.isNotEmpty()) {
                            if (sb.isNotEmpty() && !sb.endsWith("<br/>")) sb.append("<br/>")
                            sb.append(inner).append("<br/>")
                        }
                    }
                    else -> sb.append(inline(n.children))
                }
            }
        }
        return collapseSpaces(sb.toString())
    }

    private fun wrap(sb: StringBuilder, tag: String, inner: String) {
        if (inner.isBlank()) {
            sb.append(inner)
            return
        }
        // Keep spaces outside the tags: "<b>word</b> next", not "<b>word </b>next".
        val lead = inner.takeWhile { it == ' ' }
        val trail = inner.takeLastWhile { it == ' ' }
        sb.append(lead).append('<').append(tag).append('>').append(inner.trim(' ')).append("</").append(tag).append('>').append(trail)
    }

    private fun collapse(t: String): String = t.replace(Regex("[ \\t\\r\\n\\f]+"), " ")

    private fun collapseSpaces(t: String): String = t.replace(Regex(" {2,}"), " ").replace(" <br/>", "<br/>").replace("<br/> ", "<br/>")

    private fun trimBreaks(t: String): String {
        var s = t
        while (s.startsWith("<br/>")) s = s.removePrefix("<br/>").trimStart()
        while (s.endsWith("<br/>")) s = s.removeSuffix("<br/>").trimEnd()
        return s.trim().takeIf { it.replace(" ", "").isNotBlank() } ?: ""
    }

    private fun escape(t: String): String = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
