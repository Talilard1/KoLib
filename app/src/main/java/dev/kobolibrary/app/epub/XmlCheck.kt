package dev.kobolibrary.app.epub

/**
 * A strict well-formedness check, the way an EPUB reader's XML parser sees a file. The app's own
 * reader ([Xml]) forgives mistakes so it can edit damaged books; this says whether a file is right.
 */
object XmlCheck {

    data class Problem(val message: String, val line: Int, val column: Int, val offset: Int) {
        override fun toString() = "Line $line, column $column: $message"
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
        var i = if (n > 0 && s[0] == '﻿') 1 else 0
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
            if (semi < 0 || semi > limit || semi - amp > 40) return problem("“&” must be written as &amp;", amp)
            val name = s.substring(amp + 1, semi)
            val ok = when {
                name.startsWith("#x") || name.startsWith("#X") -> name.length > 2 && name.substring(2).toIntOrNull(16)?.let { Character.isValidCodePoint(it) && it != 0 } == true
                name.startsWith("#") -> name.length > 1 && name.substring(1).toIntOrNull()?.let { Character.isValidCodePoint(it) && it != 0 } == true
                name in setOf("amp", "lt", "gt", "quot", "apos") -> true
                htmlEntities -> name in HtmlEntities.codepoints
                else -> false
            }
            return if (ok) null else problem("Unknown character reference &$name;", amp)
        }

        fun checkText(from: Int, to: Int): Problem? {
            var k = from
            while (k < to) {
                val c = s[k]
                if (c == '&') checkReference(k, to)?.let { return it }
                else if (c == '<') return problem("Stray “<”", k)
                else if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') return problem("A control character (code ${c.code}) isn't allowed", k)
                else if (c == ']' && s.startsWith("]]>", k)) return problem("“]]>” isn't allowed in text", k)
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
                    for (k in i until textEnd) if (!s[k].isWhitespace()) return problem(if (rootClosed) "Text after the end of the document" else "Text before the start of the document", k)
                } else {
                    checkText(i, textEnd)?.let { return it }
                }
            }
            if (lt < 0) break
            when {
                s.startsWith("<!--", lt) -> {
                    val close = s.indexOf("-->", lt + 4)
                    if (close < 0) return problem("A comment isn't closed", lt)
                    val dd = s.indexOf("--", lt + 4)
                    if (dd in (lt + 4) until close) return problem("“--” isn't allowed inside a comment", dd)
                    i = close + 3
                }
                s.startsWith("<![CDATA[", lt) -> {
                    if (stack.isEmpty()) return problem("CDATA isn't allowed outside the document", lt)
                    val close = s.indexOf("]]>", lt + 9)
                    if (close < 0) return problem("A CDATA section isn't closed", lt)
                    i = close + 3
                }
                s.startsWith("<?", lt) -> {
                    val close = s.indexOf("?>", lt + 2)
                    if (close < 0) return problem("A processing instruction isn't closed", lt)
                    val target = s.substring(lt + 2, close).trimStart().takeWhile { !it.isWhitespace() }
                    if (target.equals("xml", true) && lt != (if (n > 0 && s[0] == '﻿') 1 else 0)) {
                        return problem("The <?xml …?> declaration must come first", lt)
                    }
                    i = close + 2
                }
                s.startsWith("<!DOCTYPE", lt) || s.startsWith("<!doctype", lt) -> {
                    if (rootSeen) return problem("A doctype can't come after the document starts", lt)
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
                    if (j >= n) return problem("The doctype isn't closed", lt)
                    i = j + 1
                }
                s.startsWith("</", lt) -> {
                    var j = lt + 2
                    while (j < n && isNameChar(s[j])) j++
                    val name = s.substring(lt + 2, j)
                    while (j < n && s[j].isWhitespace()) j++
                    if (j >= n || s[j] != '>') return problem("The end tag </$name> isn't closed properly", lt)
                    if (stack.isEmpty()) return problem("</$name> doesn't match any open tag", lt)
                    val open = stack.removeAt(stack.size - 1)
                    if (open != name) return problem("Found </$name>, but expected </$open>", lt)
                    if (stack.isEmpty()) rootClosed = true
                    i = j + 1
                }
                else -> {
                    var j = lt + 1
                    if (j >= n || !isNameStart(s[j])) return problem("“<” must be written as &lt;", lt)
                    while (j < n && isNameChar(s[j])) j++
                    val name = s.substring(lt + 1, j)
                    if (stack.isEmpty()) {
                        if (rootClosed) return problem("A second root element <$name>", lt)
                        rootSeen = true
                    }
                    val seen = HashSet<String>()
                    var selfClosing = false
                    while (true) {
                        val ws = j
                        while (j < n && s[j].isWhitespace()) j++
                        if (j >= n) return problem("The tag <$name> isn't closed", lt)
                        if (s[j] == '>') { j++; break }
                        if (s[j] == '/') {
                            if (j + 1 < n && s[j + 1] == '>') { selfClosing = true; j += 2; break }
                            return problem("Unexpected “/” in <$name>", j)
                        }
                        if (j == ws) return problem("Attributes in <$name> need spaces between them", j)
                        if (!isNameStart(s[j])) return problem("Unexpected “${s[j]}” in <$name>", j)
                        val an = j
                        while (j < n && isNameChar(s[j])) j++
                        val attr = s.substring(an, j)
                        if (!seen.add(attr)) return problem("The attribute $attr appears twice in <$name>", an)
                        while (j < n && s[j].isWhitespace()) j++
                        if (j >= n || s[j] != '=') return problem("The attribute $attr in <$name> has no value", an)
                        j++
                        while (j < n && s[j].isWhitespace()) j++
                        if (j >= n || (s[j] != '"' && s[j] != '\'')) return problem("The value of $attr in <$name> needs quotes", j)
                        val q = s[j]
                        val close = s.indexOf(q, j + 1)
                        if (close < 0) return problem("The value of $attr in <$name> isn't closed", j)
                        val lt2 = s.indexOf('<', j + 1)
                        if (lt2 in (j + 1) until close) return problem("“<” isn't allowed in an attribute value", lt2)
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
        if (stack.isNotEmpty()) return problem("<${stack.last()}> is never closed", n)
        if (!rootSeen) return problem("The file has no content", 0)
        return null
    }

    /** True when the file's doctype is an XHTML one, so readers know HTML's named characters. */
    fun hasXhtmlDoctype(source: String): Boolean {
        val head = source.take(1024)
        val dt = head.indexOf("<!DOCTYPE", ignoreCase = true)
        return dt >= 0 && head.indexOf("XHTML", dt, ignoreCase = true).let { it in dt..(dt + 200) }
    }
}
