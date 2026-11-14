package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr

/**
 * A small XML reader that remembers where every element sits in the source text.
 *
 * Book metadata is edited by cutting and inserting text at those positions, so everything the app
 * doesn't manage (other metadata, comments, formatting) stays exactly as the publisher wrote it.
 * Pure Kotlin, so it behaves identically in tests and on the tablet.
 */
sealed class XNode {
    abstract val start: Int
    abstract val end: Int
}

class XText(val raw: String, override val start: Int, override val end: Int, val isCData: Boolean = false) : XNode() {
    val text: String get() = if (isCData) raw else Xml.unescape(raw)
}

/** Comments, processing instructions and doctypes: kept verbatim, never interpreted. */
class XOther(val raw: String, override val start: Int, override val end: Int) : XNode()

class XElement(
    /** Qualified name as written, e.g. "dc:title". */
    val name: String,
    /** Attribute names as written, values decoded. Order preserved. */
    val attributes: LinkedHashMap<String, String>,
    override val start: Int,
    /** Index just after the '>' of the start tag. */
    val startTagEnd: Int,
    val selfClosing: Boolean,
    val parent: XElement?,
) : XNode() {
    /** Index of the '<' of the end tag (equals [startTagEnd] for self-closing elements). */
    var endTagStart: Int = startTagEnd
        internal set
    override var end: Int = startTagEnd
        internal set
    val children = mutableListOf<XNode>()

    val prefix: String? get() = if (name.contains(':')) name.substringBefore(':') else null
    val localName: String get() = name.substringAfter(':')

    fun elements(): List<XElement> = children.filterIsInstance<XElement>()

    fun text(): String = buildString { appendText(this@XElement, this) }

    private fun appendText(e: XElement, sb: StringBuilder) {
        for (c in e.children) when (c) {
            is XText -> sb.append(c.text)
            is XElement -> appendText(c, sb)
            is XOther -> Unit
        }
    }

    fun namespaceUri(): String? = lookupNamespace(prefix)

    fun lookupNamespace(prefix: String?): String? {
        val key = if (prefix == null) "xmlns" else "xmlns:$prefix"
        var e: XElement? = this
        while (e != null) {
            e.attributes[key]?.let { return it }
            e = e.parent
        }
        return when (prefix) {
            "xml" -> "http://www.w3.org/XML/1998/namespace"
            else -> null
        }
    }

    /** A prefix bound to [uri] on this element or an ancestor ("" for the default namespace). */
    fun prefixFor(uri: String): String? {
        var e: XElement? = this
        while (e != null) {
            for ((k, v) in e.attributes) {
                if (v == uri) {
                    if (k == "xmlns") return ""
                    if (k.startsWith("xmlns:")) return k.removePrefix("xmlns:")
                }
            }
            e = e.parent
        }
        return null
    }

    /** Attribute by namespace and local name, accepting the usual prefixes if the document forgot to declare them. */
    fun attr(local: String, nsUri: String? = null): String? {
        if (nsUri == null) return attributes[local]
        for ((k, v) in attributes) {
            if (!k.contains(':')) continue
            val p = k.substringBefore(':')
            if (k.substringAfter(':') != local || p == "xmlns") continue
            if (lookupNamespace(p) == nsUri) return v
        }
        return null
    }

    fun descendants(): Sequence<XElement> = sequence {
        for (c in elements()) {
            yield(c)
            yieldAll(c.descendants())
        }
    }
}

class XDocument(val source: String, val root: XElement)

class XmlException(message: String) : Exception(message)

object Xml {

    fun parse(source: String): XDocument {
        var root: XElement? = null
        var current: XElement? = null
        var i = 0
        val n = source.length
        if (n > 0 && source[0] == '\uFEFF') i = 1

        fun addChild(node: XNode) {
            current?.children?.add(node)
        }

        while (i < n) {
            val lt = source.indexOf('<', i)
            if (lt < 0) {
                if (current != null) addChild(XText(source.substring(i), i, n))
                break
            }
            if (lt > i && current != null) addChild(XText(source.substring(i, lt), i, lt))
            when {
                source.startsWith("<!--", lt) -> {
                    val close = source.indexOf("-->", lt + 4)
                    if (close < 0) throw XmlException(tr("Unclosed comment at {0}", lt))
                    addChild(XOther(source.substring(lt, close + 3), lt, close + 3))
                    i = close + 3
                }
                source.startsWith("<![CDATA[", lt) -> {
                    val close = source.indexOf("]]>", lt + 9)
                    if (close < 0) throw XmlException(tr("Unclosed CDATA at {0}", lt))
                    addChild(XText(source.substring(lt + 9, close), lt, close + 3, isCData = true))
                    i = close + 3
                }
                source.startsWith("<?", lt) -> {
                    val close = source.indexOf("?>", lt + 2)
                    if (close < 0) throw XmlException(tr("Unclosed processing instruction at {0}", lt))
                    addChild(XOther(source.substring(lt, close + 2), lt, close + 2))
                    i = close + 2
                }
                source.startsWith("<!", lt) -> {
                    // DOCTYPE, possibly with an internal subset in [ ].
                    var j = lt + 2
                    var depth = 0
                    while (j < n) {
                        val c = source[j]
                        if (c == '[') depth++
                        else if (c == ']') depth--
                        else if (c == '>' && depth <= 0) break
                        j++
                    }
                    addChild(XOther(source.substring(lt, minOf(j + 1, n)), lt, minOf(j + 1, n)))
                    i = j + 1
                }
                source.startsWith("</", lt) -> {
                    val close = source.indexOf('>', lt)
                    if (close < 0) throw XmlException(tr("Unclosed end tag at {0}", lt))
                    val name = source.substring(lt + 2, close).trim()
                    // Pop to the matching element; tolerate small nesting mistakes.
                    var e = current
                    while (e != null && e.name != name) e = e.parent
                    if (e != null) {
                        var c = current
                        while (c != null && c !== e) {
                            c.endTagStart = lt
                            c.end = lt
                            c = c.parent
                        }
                        e.endTagStart = lt
                        e.end = close + 1
                        current = e.parent
                    }
                    i = close + 1
                }
                else -> {
                    val (element, next) = parseStartTag(source, lt, current)
                    if (current == null && root == null) root = element
                    else addChild(element)
                    if (!element.selfClosing) current = element
                    i = next
                }
            }
        }
        return XDocument(source, root ?: throw XmlException(tr("No root element")))
    }

    private fun parseStartTag(s: String, lt: Int, parent: XElement?): Pair<XElement, Int> {
        var j = lt + 1
        val n = s.length
        while (j < n && !s[j].isWhitespace() && s[j] != '>' && s[j] != '/') j++
        val name = s.substring(lt + 1, j)
        if (name.isEmpty()) throw XmlException(tr("Bad tag at {0}", lt))
        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (j < n) {
            while (j < n && s[j].isWhitespace()) j++
            if (j >= n) throw XmlException(tr("Unclosed tag <{0}>", name))
            val c = s[j]
            if (c == '>') {
                j++
                break
            }
            if (c == '/' && j + 1 < n && s[j + 1] == '>') {
                selfClosing = true
                j += 2
                break
            }
            val nameStart = j
            while (j < n && !s[j].isWhitespace() && s[j] != '=' && s[j] != '>' && s[j] != '/') j++
            val attrName = s.substring(nameStart, j)
            while (j < n && s[j].isWhitespace()) j++
            if (j < n && s[j] == '=') {
                j++
                while (j < n && s[j].isWhitespace()) j++
                if (j < n && (s[j] == '"' || s[j] == '\'')) {
                    val q = s[j]
                    val close = s.indexOf(q, j + 1)
                    if (close < 0) throw XmlException(tr("Unclosed attribute in <{0}>", name))
                    attrs[attrName] = unescape(s.substring(j + 1, close))
                    j = close + 1
                } else {
                    val vs = j
                    while (j < n && !s[j].isWhitespace() && s[j] != '>') j++
                    attrs[attrName] = unescape(s.substring(vs, j))
                }
            } else if (attrName.isNotEmpty()) {
                attrs[attrName] = attrName
            } else {
                j++
            }
        }
        val element = XElement(name, attrs, lt, j, selfClosing, parent)
        if (selfClosing) {
            element.endTagStart = j
            element.end = j
        }
        return element to j
    }

    fun escapeText(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }

    fun escapeAttr(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    fun unescape(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi > i && semi - i <= 10) {
                    val ent = s.substring(i + 1, semi)
                    val decoded: String? = when {
                        ent == "amp" -> "&"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") || ent.startsWith("#X") ->
                            ent.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
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

    /** Re-creates a start tag from an element's attributes (used when one tag must change). */
    fun startTag(name: String, attributes: Map<String, String>, selfClosing: Boolean): String = buildString {
        append('<').append(name)
        for ((k, v) in attributes) append(' ').append(k).append("=\"").append(escapeAttr(v)).append('"')
        append(if (selfClosing) "/>" else ">")
    }
}

/** Collects text cuts and inserts against one source string, then applies them back to front. */
class TextEdits(private val source: String) {
    private data class Edit(val start: Int, val end: Int, val text: String, val order: Int)

    private val edits = mutableListOf<Edit>()
    private var counter = 0

    fun replace(start: Int, end: Int, text: String) {
        edits += Edit(start, end, text, counter++)
    }

    fun insert(at: Int, text: String) = replace(at, at, text)

    /** Where [node] starts once the indentation and line break in front of it are counted. */
    fun leadingSpaceStart(node: XNode): Int {
        var s = node.start
        while (s > 0 && (source[s - 1] == ' ' || source[s - 1] == '\t')) s--
        if (s > 0 && source[s - 1] == '\n') {
            s--
            if (s > 0 && source[s - 1] == '\r') s--
        }
        return s
    }

    /** Removes [node] together with the indentation and line break in front of it. */
    fun removeWithLeadingSpace(node: XNode) = replace(leadingSpaceStart(node), node.end, "")

    fun apply(): String {
        // Back to front. At the same position a removal goes before an insert (so the insert survives),
        // and inserts added earlier end up first.
        val sorted = edits.sortedWith(
            compareByDescending<Edit> { it.start }.thenByDescending { it.end }.thenByDescending { it.order }
        )
        val sb = StringBuilder(source)
        var lastStart = Int.MAX_VALUE
        for (e in sorted) {
            // Skip anything overlapping an edit already applied further along.
            if (e.end > lastStart && e.start != e.end) continue
            sb.replace(e.start, e.end, e.text)
            lastStart = e.start
        }
        return sb.toString()
    }
}
