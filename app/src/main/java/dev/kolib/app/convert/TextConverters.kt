package dev.kolib.app.convert

import dev.kolib.app.epub.BookMetadata
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Turning bytes into text whatever they were saved as: with a byte-order mark, UTF-8, or Windows' Western one. */
object TextDecoding {
    fun decode(bytes: ByteArray, declared: String? = null): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        if (declared != null) runCatching { return String(bytes, Charset.forName(declared)) }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}

/**
 * A plain text file as a book: paragraphs where there are blank lines (or, with none, each line), and a
 * new chapter at each line that reads like a chapter's heading ("CHAPTER I.", "Part Two", "IV").
 * A Project Gutenberg file's header and licence are left out, and its title and author used.
 */
object TextConverter {

    private val headingWords = Regex(
        """^(chapter|chap\.|book|part|volume|vol\.|section|prologue|prolog|epilogue|interlude|appendix|preface|foreword|introduction|afterword|letter|canto|act|scene)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val numeral = Regex("""^([IVXLCDM]+|\d{1,3})\.?$""")
    private val gutenbergStart = Regex("""^\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG.*$""", RegexOption.IGNORE_CASE)
    private val gutenbergEnd = Regex("""^\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG.*$""", RegexOption.IGNORE_CASE)

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        var text = TextDecoding.decode(bytes).replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
        var meta = BookMetadata(title = titleFromName(fileName))
        // Project Gutenberg: its details at the top, then the book between the START and END lines.
        val lines0 = text.split('\n')
        val start = lines0.indexOfFirst { gutenbergStart.matches(it.trim()) }
        if (start >= 0) {
            val head = lines0.subList(0, start)
            fun field(name: String) = head.firstOrNull { it.trim().startsWith("$name:") }?.substringAfter(':')?.trim()?.ifEmpty { null }
            meta = meta.copy(
                title = field("Title") ?: meta.title,
                authors = field("Author")?.let { a -> a.split(Regex("""\s+and\s+|;""")).map { it.trim() }.filter { it.isNotEmpty() } } ?: emptyList(),
                language = field("Language")?.let { languageCode(it) },
            )
            val end = lines0.indexOfFirst { gutenbergEnd.matches(it.trim()) }.let { if (it > start) it else lines0.size }
            text = lines0.subList(start + 1, end).joinToString("\n")
        }
        val out = EpubOut(meta)
        writeChapters(out, text)
        out.write(target)
    }

    fun titleFromName(fileName: String): String =
        fileName.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { "Untitled" }

    private val languages = mapOf(
        "english" to "en", "french" to "fr", "german" to "de", "spanish" to "es", "italian" to "it", "portuguese" to "pt",
        "dutch" to "nl", "finnish" to "fi", "swedish" to "sv", "danish" to "da", "norwegian" to "no", "polish" to "pl",
        "russian" to "ru", "chinese" to "zh", "japanese" to "ja", "latin" to "la", "greek" to "el", "hungarian" to "hu",
        "czech" to "cs", "esperanto" to "eo", "catalan" to "ca", "tagalog" to "tl", "welsh" to "cy", "irish" to "ga",
    )

    fun languageCode(name: String): String? = languages[name.trim().lowercase()] ?: name.trim().lowercase().takeIf { it.length in 2..3 && it.all(Char::isLetter) }

    private class Block(val lines: List<String>) {
        val text: String get() = lines.joinToString(" ") { it.trim() }
    }

    /** The text's blocks, between blank lines (or each line its own, if there are none). */
    private fun blocks(text: String): List<Block> {
        val lines = text.split('\n')
        val blank = lines.count { it.isBlank() }
        if (blank < lines.size / 20) return lines.filter { it.isNotBlank() }.map { Block(listOf(it)) }
        val out = ArrayList<Block>()
        var cur = ArrayList<String>()
        for (l in lines) {
            if (l.isBlank()) {
                if (cur.isNotEmpty()) out += Block(cur)
                cur = ArrayList()
            } else cur += l
        }
        if (cur.isNotEmpty()) out += Block(cur)
        return out
    }

    private fun isHeading(b: Block): Boolean {
        if (b.lines.size > 2) return false
        val first = b.lines[0].trim()
        if (first.length > 70 || first.isEmpty()) return false
        if (headingWords.matches(first) && first.split(Regex("\\s+")).size <= 10) return true
        if (numeral.matches(first)) return true
        return false
    }

    private fun headingTitle(b: Block): String {
        val parts = b.lines.map { it.trim() }.filter { it.isNotEmpty() }
        val head = parts.first().trimEnd('.').let { tidyCase(it) }
        return if (parts.size > 1) "$head: ${parts[1]}" else head
    }

    /** "CHAPTER I" as "Chapter I": a heading in capitals, with its numeral left as it is. */
    private fun tidyCase(s: String): String {
        if (s.any { it.isLowerCase() }) return s
        return s.split(' ').joinToString(" ") { w ->
            if (numeral.matches(w.trimEnd('.', ':'))) w else w.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    private fun writeChapters(out: EpubOut, text: String) {
        val all = blocks(text).filter { b -> b.lines.joinToString("").trim().let { it != "[Illustration]" } }
        // A run of lines that each look like a heading is a list of contents, not a heading.
        val headings = all.indices.filter { i -> isHeading(all[i]) }
        val chapters = ArrayList<Pair<String?, List<Block>>>()
        var title: String? = null
        var body = ArrayList<Block>()
        for ((i, b) in all.withIndex()) {
            if (i in headings && (body.isNotEmpty() || title != null)) {
                chapters += title to body
                body = ArrayList()
                title = headingTitle(b)
            } else if (i in headings) {
                title = headingTitle(b)
            } else body += b
        }
        chapters += title to body
        // Too long without a heading: split up, so pages stay quick to open.
        for ((t, blocks) in chapters) {
            if (t == null && blocks.isEmpty()) continue
            val pieces = blocks.chunked(400)
            pieces.ifEmpty { listOf(emptyList()) }.forEachIndexed { k, piece ->
                val name = when {
                    t != null && k == 0 -> t
                    t != null -> null
                    out.isEmpty && k == 0 -> out.meta.title
                    else -> null
                }
                val html = StringBuilder()
                if (t != null && k == 0) html.append("<h2>").append(HtmlSoup.escapeText(t)).append("</h2>\n")
                piece.forEachIndexed { j, b -> paragraph(b, html, first = j == 0) }
                out.page(name, html.toString())
            }
        }
    }

    private val italic = Regex("""(?<![\w*])_([^_\n]+?)_(?![\w])""")
    private val bold = Regex("""(?<![\w*])\*([^*\n]+?)\*(?![\w])""")

    private fun inline(s: String): String {
        var t = HtmlSoup.escapeText(s)
        t = italic.replace(t) { "<em>${it.groupValues[1]}</em>" }
        t = bold.replace(t) { "<strong>${it.groupValues[1]}</strong>" }
        return t
    }

    private fun paragraph(b: Block, html: StringBuilder, first: Boolean) {
        val lines = b.lines
        val indented = lines.all { it.startsWith("  ") }
        val short = lines.size > 1 && lines.all { it.trim().length < 50 }
        val cls = when {
            first -> " class=\"first\""
            lines.size == 1 && lines[0].trim().length < 40 && lines[0].startsWith("    ") -> " class=\"center\""
            short || indented && lines.size > 1 -> " class=\"verse\""
            else -> ""
        }
        if (short || (indented && lines.size > 1)) {
            // Verse, an address, a list: kept to its lines.
            html.append("<p").append(cls).append(">").append(lines.joinToString("<br/>") { inline(it.trim()) }).append("</p>\n")
        } else {
            html.append("<p").append(cls).append(">").append(inline(b.text)).append("</p>\n")
        }
    }
}

/**
 * A Markdown file as a book: headings, paragraphs, emphasis, links, lists, quotes, code and rules; a
 * new chapter at each top heading. A title and author in front matter at the top are used.
 */
object MarkdownConverter {

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        var text = TextDecoding.decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        var meta = BookMetadata(title = TextConverter.titleFromName(fileName))
        // Front matter: --- title: ... author: ... ---
        if (text.startsWith("---\n")) {
            val end = text.indexOf("\n---", 4)
            if (end > 0) {
                val front = text.substring(4, end).lines()
                fun field(name: String) = front.firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()?.trim('"', '\'')?.ifEmpty { null }
                meta = meta.copy(
                    title = field("title") ?: meta.title,
                    authors = (field("author") ?: field("authors"))?.trim('[', ']')?.split(',', ';')?.map { it.trim().trim('"', '\'') }?.filter { it.isNotEmpty() } ?: emptyList(),
                    language = field("lang") ?: field("language"),
                    description = field("description")?.let { "<p>${HtmlSoup.escapeText(it)}</p>" },
                )
                text = text.substring(end + 4).trimStart('\n')
            }
        }
        val blocks = toHtml(text)
        val out = EpubOut(meta)
        // Chapters at the top level of heading used more than once (h1, or h2 if there's one h1, a title).
        val h1 = blocks.count { it.level == 1 }
        val splitAt = if (h1 >= 2) 1 else if (blocks.count { it.level == 2 } >= 2) 2 else 0
        if (h1 == 1 && splitAt == 2) blocks.firstOrNull { it.level == 1 }?.let { if (meta.title == TextConverter.titleFromName(fileName)) out.meta = meta.copy(title = it.plain) }
        var title: String? = null
        val body = StringBuilder()
        fun flush() {
            if (body.isNotBlank() || title != null) out.page(title ?: if (out.isEmpty) meta.title else null, body.toString())
            body.setLength(0)
        }
        for (b in blocks) {
            if (splitAt > 0 && b.level == splitAt) {
                flush()
                title = b.plain
            }
            body.append(b.html).append('\n')
        }
        flush()
        out.write(target)
    }

    class Html(val html: String, val level: Int = 0, val plain: String = "")

    private val atx = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val rule = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val bullet = Regex("""^(\s*)([-*+])\s+(.*)$""")
    private val ordered = Regex("""^(\s*)(\d{1,9})[.)]\s+(.*)$""")

    /** Markdown [text] as blocks of XHTML. */
    fun toHtml(text: String): List<Html> {
        val lines = text.split('\n')
        val out = ArrayList<Html>()
        var i = 0
        val para = ArrayList<String>()
        fun flushPara() {
            if (para.isEmpty()) return
            val joined = para.joinToString("\n") { it.trim() }
            out += Html("<p>" + inline(joined).replace(Regex("""( {2,}|\\)\n"""), "<br/>").replace("\n", " ") + "</p>")
            para.clear()
        }
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.isEmpty() -> {
                    flushPara()
                    i++
                }
                t.startsWith("```") || t.startsWith("~~~") -> {
                    flushPara()
                    val fence = t.take(3)
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) code.append(lines[i++]).append('\n')
                    i++
                    out += Html("<pre><code>" + HtmlSoup.escapeText(code.toString().trimEnd()) + "</code></pre>")
                }
                atx.matches(t) -> {
                    flushPara()
                    val m = atx.find(t)!!
                    val level = m.groupValues[1].length
                    val inner = inline(stripAttributes(m.groupValues[2]))
                    out += Html("<h$level>$inner</h$level>", level, plain(m.groupValues[2]))
                    i++
                }
                para.isNotEmpty() && (t.matches(Regex("^=+$")) || t.matches(Regex("^-+$"))) -> {
                    // Setext: the paragraph above is a heading.
                    val level = if (t.startsWith("=")) 1 else 2
                    val head = para.joinToString(" ") { it.trim() }
                    para.clear()
                    out += Html("<h$level>${inline(head)}</h$level>", level, plain(head))
                    i++
                }
                rule.matches(line) -> {
                    flushPara()
                    out += Html("<hr/>")
                    i++
                }
                t.startsWith(">") -> {
                    flushPara()
                    val quoted = ArrayList<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) quoted += lines[i++].trim().removePrefix(">").removePrefix(" ")
                    out += Html("<blockquote>" + toHtml(quoted.joinToString("\n")).joinToString("\n") { it.html } + "</blockquote>")
                }
                bullet.matches(line) || ordered.matches(line) -> {
                    flushPara()
                    val isOrdered = ordered.matches(line)
                    val items = ArrayList<String>()
                    while (i < lines.size && (bullet.matches(lines[i]) || ordered.matches(lines[i]) || (lines[i].startsWith("  ") && lines[i].isNotBlank() && items.isNotEmpty()))) {
                        val l = lines[i]
                        val m = bullet.find(l) ?: ordered.find(l)
                        if (m != null && m.groupValues[1].length < 2) items += m.groupValues[3]
                        else items[items.size - 1] = items.last() + " " + (m?.groupValues?.get(3) ?: l.trim())
                        i++
                    }
                    val tag = if (isOrdered) "ol" else "ul"
                    out += Html("<$tag>" + items.joinToString("") { "<li>${inline(it)}</li>" } + "</$tag>")
                }
                line.startsWith("    ") && para.isEmpty() -> {
                    val code = StringBuilder()
                    while (i < lines.size && (lines[i].startsWith("    ") || lines[i].isBlank())) code.append(lines[i++].removePrefix("    ")).append('\n')
                    out += Html("<pre><code>" + HtmlSoup.escapeText(code.toString().trimEnd()) + "</code></pre>")
                }
                else -> {
                    para += line
                    i++
                }
            }
        }
        flushPara()
        return out
    }

    private fun plain(s: String) = HtmlSoup.decode(stripAttributes(s).replace(Regex("""[*_`\\]|!?\[([^\]]*)\]\([^)]*\)"""), "$1")).trim()

    /** Pandoc's attributes on headings and spans ("Title {#id .class}", "[text]{lang=en}"): the text alone. */
    private fun stripAttributes(s: String): String = s
        .replace(Regex("""\[([^\]]*)\]\{[^}]*\}"""), "$1")
        .replace(Regex("""\s*\{[#.][^}]*\}\s*"""), " ")
        .trim()

    private val code = Regex("""`([^`]+)`""")
    private val image = Regex("""!\[([^\]]*)\]\(([^)\s]*)(?:\s+"[^"]*")?\)""")
    private val link = Regex("""\[([^\]]+)\]\(([^)\s]*)(?:\s+"[^"]*")?\)""")
    private val auto = Regex("""<(https?://[^>\s]+)>""")
    private val strongEm = Regex("""(\*\*\*|___)(?=\S)(.+?)(?<=\S)\1""")
    private val strong = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
    private val em = Regex("""(?<![\w*])([*_])(?=\S)(.+?)(?<=\S)\1(?![\w*])""")
    private val strike = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")

    /** Markdown's inline marks in [s] as XHTML. */
    fun inline(s0: String): String {
        val s = s0.replace(Regex("""\[([^\]]*)\]\{[^}]*\}"""), "$1").replace("\\'", "'").replace("\\\"", "\"")
        // Code first, kept out of the rest.
        val kept = ArrayList<String>()
        var t = code.replace(s) { m -> kept += "<code>" + HtmlSoup.escapeText(m.groupValues[1]) + "</code>"; "\u0000${kept.size - 1}\u0000" }
        t = HtmlSoup.escapeText(t)
        t = image.replace(t) { m -> m.groupValues[1].ifEmpty { "" } }
        t = link.replace(t) { m ->
            val href = m.groupValues[2]
            if (href.startsWith("http") || href.startsWith("mailto:")) "<a href=\"${HtmlSoup.escapeAttr(HtmlSoup.decode(href))}\">${m.groupValues[1]}</a>" else m.groupValues[1]
        }
        t = auto.replace(t) { m -> "<a href=\"${m.groupValues[1]}\">${m.groupValues[1]}</a>" }
        t = t.replace(Regex("""&lt;(https?://[^&\s]+)&gt;""")) { m -> "<a href=\"${m.groupValues[1]}\">${m.groupValues[1]}</a>" }
        t = strongEm.replace(t) { "<strong><em>${it.groupValues[2]}</em></strong>" }
        t = strong.replace(t) { "<strong>${it.groupValues[2]}</strong>" }
        t = em.replace(t) { "<em>${it.groupValues[2]}</em>" }
        t = strike.replace(t) { "<del>${it.groupValues[1]}</del>" }
        return t.replace(Regex("\u0000(\\d+)\u0000")) { kept[it.groupValues[1].toInt()] }
    }
}
