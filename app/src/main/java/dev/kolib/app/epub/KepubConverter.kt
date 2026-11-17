package dev.kolib.app.epub

/**
 * Turns EPUB content documents into Kobo's kepub form, the way kepubify does (MIT licence,
 * github.com/pgaskin/kepubify), so the Kobo uses its faster kepub renderer and keeps reading stats:
 *
 *  - each sentence of text is wrapped in `<span class="koboSpan" id="kobo.P.S">`, numbered by
 *    paragraph (p, headings, lists, tables and images start a new one) and sentence;
 *  - images are wrapped in a span of their own;
 *  - the body's contents go inside `<div id="book-columns"><div id="book-inner">`;
 *  - a small `kobostylehacks` style goes at the end of the head.
 *
 * Only insertions are made, at exact positions, so [KepubStripper] gives back the original file.
 */
object KepubConverter {

    const val STYLE = """<style type="text/css" class="kobostylehacks">div#book-inner { margin-top: 0; margin-bottom: 0;}</style>"""
    private const val WRAP_START = """<div id="book-columns"><div id="book-inner">"""
    private const val WRAP_END = "</div></div>"
    private const val XHTML_NS = "http://www.w3.org/1999/xhtml"

    /** Elements whose contents are left alone. */
    private val noSpanElements = setOf("script", "style", "pre", "audio", "video", "svg", "math", "textarea", "title")

    /** Elements that start a new paragraph number. */
    private val newParagraph = setOf("p", "ol", "ul", "table", "h1", "h2", "h3", "h4", "h5", "h6")

    /** The converted document. Throws [XmlException] when it isn't well-formed XHTML. */
    fun convert(xhtml: String): String {
        val doc = Xml.parse(xhtml)
        val edits = TextEdits(xhtml)
        val root = doc.root
        val head = root.elements().firstOrNull { it.localName.equals("head", true) }
        val body = root.elements().firstOrNull { it.localName.equals("body", true) }

        if (head != null && !head.selfClosing && head.elements().none { isKoboStyle(it) }) {
            // On a line of its own, just before whatever space precedes </head>.
            var at = head.endTagStart
            while (at > head.startTagEnd && xhtml[at - 1].isWhitespace()) at--
            edits.insert(at, "\n" + STYLE)
        }
        if (body == null || body.selfClosing) return edits.apply()

        val hasContent = body.children.any { it is XElement || (it is XText && it.text.isNotBlank()) }
        val wrapped = alreadyWrapped(body)
        if (hasContent && !wrapped) edits.insert(body.startTagEnd, WRAP_START)

        if (body.descendants().none { hasClass(it, "koboSpan") }) {
            Spans(edits).walk(body, noSpans = false)
        }
        // Added last, so it closes after any span ending at the same place.
        if (hasContent && !wrapped) edits.insert(body.endTagStart, WRAP_END)
        return edits.apply()
    }

    fun isKepub(xhtml: String): Boolean = xhtml.contains("koboSpan")

    private fun isKoboStyle(e: XElement): Boolean {
        if (!e.localName.equals("style", true)) return false
        if (e.attributes["id"] == "kobostylehacks") return true
        return e.attributes["class"]?.split(' ')?.any { it == "kobostylehacks" } == true
    }

    private fun alreadyWrapped(body: XElement): Boolean {
        val first = body.elements().firstOrNull() ?: return false
        return first.localName == "div" && first.attributes["id"] == "book-columns" &&
            first.elements().firstOrNull()?.attributes?.get("id") == "book-inner"
    }

    private fun hasClass(e: XElement, cls: String) = e.attributes["class"]?.split(' ', '\t', '\n')?.any { it == cls } == true

    private fun isHtml(e: XElement): Boolean {
        val ns = e.namespaceUri()
        return ns == null || ns == XHTML_NS
    }

    /** Numbering state while walking a body. */
    private class Spans(val edits: TextEdits) {
        var para = 0
        var seg = 0
        var newPara = false

        fun spanStart(): String {
            if (newPara) {
                newPara = false
                para++
                seg = 0
            }
            seg++
            return """<span class="koboSpan" id="kobo.$para.$seg">"""
        }

        fun walk(element: XElement, noSpans: Boolean) {
            for (child in element.children) {
                when (child) {
                    is XElement -> {
                        val name = child.localName.lowercase()
                        val html = isHtml(child)
                        var childNoSpans = noSpans
                        var wrapImage = false
                        if (!noSpans) {
                            if (name in noSpanElements) childNoSpans = true
                            if (html) {
                                if (name == "img") {
                                    newPara = true
                                    edits.insert(child.start, spanStart())
                                    wrapImage = true
                                } else if (name in newParagraph) {
                                    newPara = true
                                }
                            }
                        }
                        if (!child.selfClosing) walk(child, childNoSpans)
                        if (wrapImage) edits.insert(child.end, "</span>")
                    }
                    is XText -> if (!noSpans && !child.isCData) {
                        text(child, directlyInP = isHtml(element) && element.localName.equals("p", ignoreCase = true))
                    }
                    is XOther -> Unit
                }
            }
        }

        private fun text(node: XText, directlyInP: Boolean) {
            val (decoded, rawAt) = decodeWithOffsets(node.raw)
            if (decoded.isEmpty()) return
            val starts = sentenceStarts(decoded)
            for (i in starts.indices) {
                val a = starts[i]
                val b = if (i + 1 < starts.size) starts[i + 1] else decoded.length
                // Outside a p, sentences that are only space get no span (kepubify does the same).
                if (!directlyInP && decoded.substring(a, b).all { it.isWhitespace() }) continue
                edits.insert(node.start + rawAt[a], spanStart())
                edits.insert(node.start + rawAt[b], "</span>")
            }
        }
    }

    /**
     * Where each sentence starts: a sentence ends after . ! or ?, optionally followed by a quote or
     * an ellipsis, and then spaces (which belong to it). The same rule as kepubify's.
     */
    internal fun sentenceStarts(s: String): List<Int> {
        val starts = mutableListOf(0)
        var state = DEFAULT
        for (i in s.indices) {
            val c = s[i]
            val punct = c == '.' || c == '!' || c == '?'
            val extra = c == '\'' || c == '"' || c == '”' || c == '’' || c == '“' || c == '…'
            val space = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000c'
            if (state == AFTER_SPACE && !space) {
                starts += i
                state = DEFAULT
            }
            state = when {
                punct -> PUNCT
                extra && state == PUNCT -> PUNCT_EXTRA
                space && state != DEFAULT -> AFTER_SPACE
                else -> DEFAULT
            }
        }
        return starts
    }

    private const val DEFAULT = 0
    private const val PUNCT = 1
    private const val PUNCT_EXTRA = 2
    private const val AFTER_SPACE = 3

    private val entities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "hellip" to "…",
        "mdash" to "—", "ndash" to "–", "thinsp" to " ", "ensp" to " ", "emsp" to " ",
    )

    /**
     * Decodes character references, remembering where each decoded character came from in the raw
     * text, so split points can be put back in the right place. The last entry is the raw length.
     */
    internal fun decodeWithOffsets(raw: String): Pair<String, IntArray> {
        val sb = StringBuilder(raw.length)
        val at = ArrayList<Int>(raw.length + 1)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '&') {
                val semi = raw.indexOf(';', i + 1)
                if (semi > i + 1 && semi - i <= 12) {
                    val name = raw.substring(i + 1, semi)
                    val decoded = when {
                        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
                        name.startsWith("#") -> name.substring(1).toIntOrNull()
                        else -> null
                    }?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: entities[name]
                    if (decoded != null) {
                        for (ch in decoded) {
                            sb.append(ch)
                            at += i
                        }
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            at += i
            i++
        }
        at += raw.length
        return sb.toString() to at.toIntArray()
    }

    /**
     * Gives the cover picture the EPUB 3 `cover-image` mark, which the Kobo's kepub reader uses to
     * find covers, even in EPUB 2 books (kepubify does this too). Returns null if nothing changes.
     */
    fun markCoverImage(opf: OpfDocument): String? {
        val item = opf.declaredCoverItem() ?: opf.guessedCoverItem() ?: return null
        if ("cover-image" in item.properties) return null
        val edits = TextEdits(opf.source)
        val attrs = LinkedHashMap(item.element.attributes)
        attrs["properties"] = (item.properties + "cover-image").joinToString(" ")
        edits.replace(item.element.start, item.element.startTagEnd, Xml.startTag(item.element.name, attrs, item.element.selfClosing))
        return edits.apply()
    }
}
