package dev.kobolibrary.app.epub

/**
 * Turns a kepub's content files back into plain EPUB content by removing what kepubify (or Kobo)
 * added: `span.koboSpan` wrappers around text, the `div#book-columns > div#book-inner` wrapper,
 * and the `kobostylehacks` / `kepubify-*` style blocks.
 *
 * Works tag by tag on the original text, so nothing else in the file changes.
 */
object KepubStripper {

    fun looksLikeKepub(xhtml: String): Boolean =
        xhtml.contains("koboSpan") || xhtml.contains("book-columns") || xhtml.contains("kobostylehacks")

    private val koboId = Regex("^kobo\\.\\d+\\.\\d+$")

    fun strip(xhtml: String): String {
        if (!looksLikeKepub(xhtml)) return xhtml
        val out = StringBuilder(xhtml.length)
        // One stack per element name we rewrite: true = this open tag was dropped, so drop its end tag too.
        val spanStack = ArrayDeque<Boolean>()
        val divStack = ArrayDeque<Boolean>()
        var i = 0
        val n = xhtml.length
        while (i < n) {
            val lt = xhtml.indexOf('<', i)
            if (lt < 0) {
                out.append(xhtml, i, n)
                break
            }
            out.append(xhtml, i, lt)
            // Comments, CDATA, declarations and processing instructions pass through untouched.
            val passEnd = when {
                xhtml.startsWith("<!--", lt) -> xhtml.indexOf("-->", lt + 4).let { if (it < 0) n else it + 3 }
                xhtml.startsWith("<![CDATA[", lt) -> xhtml.indexOf("]]>", lt + 9).let { if (it < 0) n else it + 3 }
                xhtml.startsWith("<?", lt) || xhtml.startsWith("<!", lt) -> xhtml.indexOf('>', lt).let { if (it < 0) n else it + 1 }
                else -> -1
            }
            if (passEnd >= 0) {
                out.append(xhtml, lt, passEnd)
                i = passEnd
                continue
            }
            val gt = tagEnd(xhtml, lt)
            if (gt < 0) {
                out.append(xhtml, lt, n)
                break
            }
            val tag = xhtml.substring(lt, gt + 1)
            val isEnd = tag.startsWith("</")
            val name = tagName(tag)
            val selfClosing = !isEnd && tag.endsWith("/>")
            when {
                name == "span" && isEnd -> {
                    val dropped = spanStack.removeLastOrNull() ?: false
                    if (!dropped) out.append(tag)
                }
                name == "span" && !selfClosing -> {
                    val attrs = attributes(tag)
                    val classes = attrs["class"]?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
                    if ("koboSpan" in classes) {
                        val others = classes.filter { it != "koboSpan" }
                        if (others.isEmpty()) {
                            spanStack.addLast(true) // unwrap entirely
                        } else {
                            // A real span that also carries Kobo's markers: keep it, minus the markers.
                            val kept = LinkedHashMap(attrs)
                            kept["class"] = others.joinToString(" ")
                            if (kept["id"]?.let { koboId.matches(it) } == true) kept.remove("id")
                            out.append(Xml.startTag(rawName(tag), kept, false))
                            spanStack.addLast(false)
                        }
                    } else {
                        out.append(tag)
                        spanStack.addLast(false)
                    }
                }
                name == "div" && isEnd -> {
                    val dropped = divStack.removeLastOrNull() ?: false
                    if (!dropped) out.append(tag)
                }
                name == "div" && !selfClosing -> {
                    val id = attributes(tag)["id"]
                    val drop = id == "book-columns" || id == "book-inner"
                    if (!drop) out.append(tag)
                    divStack.addLast(drop)
                }
                name == "style" && !isEnd && !selfClosing && isKoboStyle(attributes(tag)) -> {
                    val close = xhtml.indexOf("</style", gt + 1, ignoreCase = true)
                    val closeEnd = if (close < 0) n else xhtml.indexOf('>', close).let { if (it < 0) n else it + 1 }
                    i = closeEnd
                    // Drop the line the style block sat on, if it was alone there.
                    trimTrailingBlankLine(out)
                    continue
                }
                else -> out.append(tag)
            }
            i = gt + 1
        }
        return out.toString()
    }

    private fun isKoboStyle(attrs: Map<String, String>): Boolean {
        if (attrs["id"] == "kobostylehacks") return true
        val classes = attrs["class"]?.split(Regex("\\s+")).orEmpty()
        return classes.any { it == "kobostylehacks" || it.startsWith("kepubify-") }
    }

    private fun trimTrailingBlankLine(sb: StringBuilder) {
        var j = sb.length
        while (j > 0 && (sb[j - 1] == ' ' || sb[j - 1] == '\t')) j--
        if (j > 0 && sb[j - 1] == '\n') sb.setLength(j - 1) else sb.setLength(j)
    }

    /** Index of the '>' ending the tag that starts at [lt], skipping quoted attribute values. */
    private fun tagEnd(s: String, lt: Int): Int {
        var j = lt + 1
        var quote = 0.toChar()
        while (j < s.length) {
            val c = s[j]
            if (quote != 0.toChar()) {
                if (c == quote) quote = 0.toChar()
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return j
            }
            j++
        }
        return -1
    }

    private fun rawName(tag: String): String {
        var j = if (tag.startsWith("</")) 2 else 1
        val start = j
        while (j < tag.length && !tag[j].isWhitespace() && tag[j] != '>' && tag[j] != '/') j++
        return tag.substring(start, j)
    }

    /** Local name in lower case ("span", "div"), ignoring any namespace prefix. */
    private fun tagName(tag: String): String = rawName(tag).substringAfter(':').lowercase()

    private fun attributes(tag: String): LinkedHashMap<String, String> {
        val inner = tag.removePrefix("<").removeSuffix(">").removeSuffix("/")
        return try {
            Xml.parse("<x" + inner.substring(rawName(tag).length) + "/>").root.attributes
        } catch (_: Exception) {
            LinkedHashMap()
        }
    }
}
