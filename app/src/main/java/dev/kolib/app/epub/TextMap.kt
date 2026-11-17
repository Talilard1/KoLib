package dev.kolib.app.epub

/**
 * The text a reader sees in a book file, tied back to where each character sits in the file.
 *
 * Find & replace and the clean-ups work on the visible text but change the file, so every decoded
 * character keeps the position it came from: `&amp;` is one "&" that came from five raw characters.
 */
class DecodedText(val text: String, private val rawAt: IntArray, private val rawBase: Int) {
    /** Where decoded character [i] starts in the file ([i] may be [text].length for the end). */
    fun rawStart(i: Int): Int = rawBase + rawAt[i]
    /** Where the decoded range [start, end) sits in the file. */
    fun rawRange(start: Int, end: Int): IntRange = rawStart(start) until rawStart(end)
}

/** One run of text in a document's body, as the browser will see it (one DOM text node). */
class TextSegment(
    val node: XText,
    /** Position among all the text nodes inside `<body>`, counted the way a browser's tree walker does. */
    val ordinal: Int,
    val decoded: DecodedText,
    /** The nearest block element around this text (p, h1, li, div…), for context and sentence starts. */
    val block: XElement?,
    /** Inside a heading. */
    val inHeading: Boolean,
    /** Inside script, style, code or pre: never searched or changed. */
    val skip: Boolean,
) {
    val text: String get() = decoded.text
}

object TextMap {

    /**
     * Decodes character references (XML's five, numbered ones and HTML's named ones), remembering where
     * each character came from. Line ends become "\n" as an XML reader makes them.
     */
    fun decode(raw: String, start: Int = 0, end: Int = raw.length, cdata: Boolean = false): DecodedText {
        val sb = StringBuilder(end - start)
        val at = IntArray(end - start + 1)
        var n = 0
        var i = start
        while (i < end) {
            val c = raw[i]
            if (c == '&' && !cdata) {
                val semi = raw.indexOf(';', i + 1)
                if (semi in (i + 2) until minOf(end, i + 34)) {
                    val name = raw.substring(i + 1, semi)
                    val cp = when {
                        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
                        name.startsWith("#") -> name.substring(1).toIntOrNull()
                        name == "amp" -> '&'.code
                        name == "lt" -> '<'.code
                        name == "gt" -> '>'.code
                        name == "quot" -> '"'.code
                        name == "apos" -> '\''.code
                        else -> HtmlEntities.codepoints[name]
                    }?.takeIf { Character.isValidCodePoint(it) }
                    if (cp != null) {
                        val chars = Character.toChars(cp)
                        for (ch in chars) {
                            sb.append(ch)
                            at[n++] = i - start
                        }
                        i = semi + 1
                        continue
                    }
                }
            }
            if (c == '\r') {
                sb.append('\n')
                at[n++] = i - start
                i += if (i + 1 < end && raw[i + 1] == '\n') 2 else 1
                continue
            }
            sb.append(c)
            at[n++] = i - start
            i++
        }
        at[n] = end - start
        return DecodedText(sb.toString(), if (n + 1 == at.size) at else at.copyOf(n + 1), start)
    }

    private val blockNames = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "dt", "dd", "blockquote", "td", "th", "caption",
        "figcaption", "pre", "section", "article", "aside", "header", "footer", "nav", "body", "table", "tr",
        "ul", "ol", "dl", "figure", "address", "center", "hr", "br",
    )
    private val headingNames = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val skipNames = setOf("script", "style", "code", "pre", "kbd", "samp", "tt", "head", "title", "svg", "math")

    fun isBlock(e: XElement): Boolean = e.localName.lowercase() in blockNames
    fun isHeading(e: XElement): Boolean = e.localName.lowercase() in headingNames

    /** The `<body>` of a parsed document (or its root when there's none). */
    fun body(doc: XDocument): XElement =
        doc.root.descendants().firstOrNull { it.localName.equals("body", true) } ?: doc.root

    /** Every text node inside `<body>`, in document order. */
    fun segments(doc: XDocument): List<TextSegment> {
        val out = ArrayList<TextSegment>()
        var ordinal = 0
        fun walk(e: XElement, block: XElement?, heading: Boolean, skip: Boolean) {
            val name = e.localName.lowercase()
            val b = if (name in blockNames && name != "br" && name != "hr") e else block
            val h = heading || name in headingNames
            val s = skip || name in skipNames
            for (c in e.children) when (c) {
                is XText -> {
                    val decoded = if (c.isCData) {
                        // The text between <![CDATA[ and ]]>.
                        decode(doc.source, c.start + 9, c.end - 3, cdata = true)
                    } else decode(doc.source, c.start, c.end)
                    out += TextSegment(c, ordinal++, decoded, b, h, s)
                }
                is XElement -> walk(c, b, h, s)
                is XOther -> Unit
            }
        }
        val body = body(doc)
        walk(body, body, false, false)
        return out
    }
}
