package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr

/**
 * A strict well-formedness check, the way an EPUB reader's XML parser sees a file. The app's own
 * reader ([Xml]) forgives mistakes so it can edit damaged books; this says whether a file is right.
 */
object XmlCheck {

    data class Problem(val message: String, val line: Int, val column: Int, val offset: Int) {
        override fun toString() = tr("Line {0}, column {1}: {2}", line, column, message)
    }

    private val nameStart = Regex("[A-Za-z_:\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD]")

    private fun isNameStart(c: Char) = c.isLetter() || c == '_' || c == ':'
    private fun isNameChar(c: Char) = isNameStart(c) || c.isDigit() || c == '-' || c == '.' || c == '·'

    /**
     * The first problem in [source], or null if it's well-formed. With [htmlEntities], named characters
     * such as &nbsp; are allowed (an XHTML doctype lets readers know them).
     */
    fun check(source: String, htmlEntities: Boolean = false): Problem? {
        val s = source
        val n = s.length
        var i = if (n > 0 && s[0] == '\uFEFF') 1 else 0
        val stack = ArrayList<String>()
        var rootSeen = false
        var rootClosed = false

        fun problem(msg: String, at: Int): Problem {
            var line = 1
            var col = 1
            for (k in 0 until minOf(at, n)) {
                if (s[k] == '\n') { line++; col = 1 } else col++
            }
            return Problem(msg, line, col, at)
        }

        fun checkReference(amp: Int, limit: Int): Problem? {
            val semi = s.indexOf(';', amp + 1)
            if (semi < 0 || semi > limit || semi - amp > 40) return problem(tr("“&” must be written as &amp;"), amp)
            val name = s.substring(amp + 1, semi)
            val ok = when {
                name.startsWith("#x") || name.startsWith("#X") -> name.length > 2 && name.substring(2).toIntOrNull(16)?.let { Character.isValidCodePoint(it) && it != 0 } == true
                name.startsWith("#") -> name.length > 1 && name.substring(1).toIntOrNull()?.let { Character.isValidCodePoint(it) && it != 0 } == true
                name in setOf("amp", "lt", "gt", "quot", "apos") -> true
                htmlEntities -> name in HtmlEntities.codepoints
                else -> false
            }
            return if (ok) null else problem(tr("Unknown character reference &{0};", name), amp)
        }

        fun checkText(from: Int, to: Int): Problem? {
            var k = from
            while (k < to) {
                val c = s[k]
                if (c == '&') checkReference(k, to)?.let { return it }
                else if (c == '<') return problem(tr("Stray “<”"), k)
                else if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') return problem(tr("A control character (code {0}) isn't allowed", c.code), k)
                else if (c == ']' && s.startsWith("]]>", k)) return problem(tr("“]]>” isn't allowed in text"), k)
                k++
            }
            return null
        }

        while (i < n) {
            val lt = s.indexOf('<', i)
            val textEnd = if (lt < 0) n else lt
            if (textEnd > i) {
                if (stack.isEmpty()) {
                    // Outside the root only whitespace may appear.
                    for (k in i until textEnd) if (!s[k].isWhitespace()) return problem(if (rootClosed) tr("Text after the end of the document") else tr("Text before the start of the document"), k)
                } else {
                    checkText(i, textEnd)?.let { return it }
                }
            }
            if (lt < 0) break
            when {
                s.startsWith("<!--", lt) -> {
                    val close = s.indexOf("-->", lt + 4)
                    if (close < 0) return problem(tr("A comment isn't closed"), lt)
                    val dd = s.indexOf("--", lt + 4)
                    if (dd in (lt + 4) until close) return problem(tr("“--” isn't allowed inside a comment"), dd)
                    i = close + 3
                }
                s.startsWith("<![CDATA[", lt) -> {
                    if (stack.isEmpty()) return problem(tr("CDATA isn't allowed outside the document"), lt)
                    val close = s.indexOf("]]>", lt + 9)
                    if (close < 0) return problem(tr("A CDATA section isn't closed"), lt)
                    i = close + 3
                }
                s.startsWith("<?", lt) -> {
                    val close = s.indexOf("?>", lt + 2)
                    if (close < 0) return problem(tr("A processing instruction isn't closed"), lt)
                    val target = s.substring(lt + 2, close).trimStart().takeWhile { !it.isWhitespace() }
                    if (target.equals("xml", true) && lt != (if (n > 0 && s[0] == '\uFEFF') 1 else 0)) {
                        return problem(tr("The <?xml …?> declaration must come first"), lt)
                    }
                    i = close + 2
                }
                s.startsWith("<!DOCTYPE", lt) || s.startsWith("<!doctype", lt) -> {
                    if (rootSeen) return problem(tr("A doctype can't come after the document starts"), lt)
                    var j = lt + 9
                    var depth = 0
                    var quote = 0.toChar()
                    while (j < n) {
                        val c = s[j]
                        if (quote != 0.toChar()) {
                            if (c == quote) quote = 0.toChar()
                        } else if (c == '"' || c == '\'') quote = c
                        else if (c == '[') depth++
                        else if (c == ']') depth--
                        else if (c == '>' && depth <= 0) break
                        j++
                    }
                    if (j >= n) return problem(tr("The doctype isn't closed"), lt)
                    i = j + 1
                }
                s.startsWith("</", lt) -> {
                    var j = lt + 2
                    while (j < n && isNameChar(s[j])) j++
                    val name = s.substring(lt + 2, j)
                    while (j < n && s[j].isWhitespace()) j++
                    if (j >= n || s[j] != '>') return problem(tr("The end tag </{0}> isn't closed properly", name), lt)
                    if (stack.isEmpty()) return problem(tr("</{0}> doesn't match any open tag", name), lt)
                    val open = stack.removeAt(stack.size - 1)
                    if (open != name) return problem(tr("Found </{0}>, but expected </{1}>", name, open), lt)
                    if (stack.isEmpty()) rootClosed = true
                    i = j + 1
                }
                else -> {
                    var j = lt + 1
                    if (j >= n || !isNameStart(s[j])) return problem(tr("“<” must be written as &lt;"), lt)
                    while (j < n && isNameChar(s[j])) j++
                    val name = s.substring(lt + 1, j)
                    if (stack.isEmpty()) {
                        if (rootClosed) return problem(tr("A second root element <{0}>", name), lt)
                        rootSeen = true
                    }
                    val seen = HashSet<String>()
                    var selfClosing = false
                    while (true) {
                        val ws = j
                        while (j < n && s[j].isWhitespace()) j++
                        if (j >= n) return problem(tr("The tag <{0}> isn't closed", name), lt)
                        if (s[j] == '>') { j++; break }
                        if (s[j] == '/') {
                            if (j + 1 < n && s[j + 1] == '>') { selfClosing = true; j += 2; break }
                            return problem(tr("Unexpected “/” in <{0}>", name), j)
                        }
                        if (j == ws) return problem(tr("Attributes in <{0}> need spaces between them", name), j)
                        if (!isNameStart(s[j])) return problem(tr("Unexpected “{0}” in <{1}>", s[j], name), j)
                        val an = j
                        while (j < n && isNameChar(s[j])) j++
                        val attr = s.substring(an, j)
                        if (!seen.add(attr)) return problem(tr("The attribute {0} appears twice in <{1}>", attr, name), an)
                        while (j < n && s[j].isWhitespace()) j++
                        if (j >= n || s[j] != '=') return problem(tr("The attribute {0} in <{1}> has no value", attr, name), an)
                        j++
                        while (j < n && s[j].isWhitespace()) j++
                        if (j >= n || (s[j] != '"' && s[j] != '\'')) return problem(tr("The value of {0} in <{1}> needs quotes", attr, name), j)
                        val q = s[j]
                        val close = s.indexOf(q, j + 1)
                        if (close < 0) return problem(tr("The value of {0} in <{1}> isn't closed", attr, name), j)
                        val lt2 = s.indexOf('<', j + 1)
                        if (lt2 in (j + 1) until close) return problem(tr("“<” isn't allowed in an attribute value"), lt2)
                        var k = j + 1
                        while (k < close) {
                            if (s[k] == '&') checkReference(k, close)?.let { return it }
                            k++
                        }
                        j = close + 1
                    }
                    if (!selfClosing) stack += name
                    else if (stack.isEmpty()) rootClosed = true
                    i = j
                }
            }
        }
        if (stack.isNotEmpty()) return problem(tr("<{0}> is never closed", stack.last()), n)
        if (!rootSeen) return problem(tr("The file has no content"), 0)
        return null
    }

    /** True when the file's doctype is an XHTML one, so readers know HTML's named characters. */
    fun hasXhtmlDoctype(source: String): Boolean {
        val head = source.take(1024)
        val dt = head.indexOf("<!DOCTYPE", ignoreCase = true)
        return dt >= 0 && head.indexOf("XHTML", dt, ignoreCase = true).let { it in dt..(dt + 200) }
    }
}
