package dev.kobolibrary.app.convert

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.Xml
import java.io.File
import java.nio.charset.Charset

/**
 * A Rich Text (RTF) document as a book: its paragraphs with bold, italic, underline and the rest, its
 * headings (by their style's name) as chapters, and pictures saved in it as PNG or JPEG.
 */
object RtfConverter {

    private class State(
        var bold: Boolean = false, var italic: Boolean = false, var underline: Boolean = false, var strike: Boolean = false,
        var sup: Boolean = false, var sub: Boolean = false, var skip: Boolean = false, var uc: Int = 1, var style: Int = 0,
        var align: String? = null, var destination: String? = null, var outline: Int = -1,
    ) {
        fun copy() = State(bold, italic, underline, strike, sup, sub, skip, uc, style, align, destination, outline)
    }

    private val skipped = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "header", "footer", "headerl", "headerr", "headerf", "footerl", "footerr",
        "footerf", "pict", "object", "themedata", "colorschememapping", "datastore", "latentstyles", "rsidtbl", "listtable",
        "listoverridetable", "generator", "xmlnstbl", "mmathPr", "fldinst", "bkmkstart", "bkmkend", "field", "nonshppict",
        "shppict", "shp", "shpinst", "sp", "sv", "sn", "revtbl", "pgdsctbl", "filetbl", "operator", "author", "title",
        "subject", "keywords", "doccomm", "company", "creatim", "revtim", "printim", "buptim", "falt", "panose", "wgrffmtfilter",
    )

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        val raw = String(bytes, Charsets.ISO_8859_1)
        if (!raw.startsWith("{\\rtf")) throw ConversionException(tr("This isn't a Rich Text file."))
        var charset: Charset = Charset.forName("windows-1252")
        Regex("""\\ansicpg(\d+)""").find(raw.take(500))?.groupValues?.get(1)?.let { cp -> runCatching { charset = Charset.forName("windows-$cp") }.recoverCatching { charset = Charset.forName("cp$cp") } }
        // Paragraph style numbers that are headings: \s1 named "heading 1".
        val headingStyles = HashMap<Int, Int>()
        Regex("""\{[^{}]*?\\s(\d+)[^{}]*?\s([^\\{};]+);\}""").findAll(raw).forEach { m ->
            val name = m.groupValues[2].trim().lowercase()
            Regex("""heading\s*(\d)""").find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { headingStyles[m.groupValues[1].toInt()] = it }
            if (name == "title") headingStyles[m.groupValues[1].toInt()] = 1
        }
        val info = HashMap<String, String>()
        val out = EpubOut(BookMetadata(title = TextConverter.titleFromName(fileName)))
        val paras = ArrayList<Triple<String, Int, String>>()
        val line = StringBuilder()
        var state = State()
        val stack = ArrayList<State>()
        var openTags = ""
        val infoText = StringBuilder()
        val pict = StringBuilder()
        var pictKind: String? = null
        val pending = java.io.ByteArrayOutputStream()

        fun tags(s: State): String = buildString {
            if (s.bold) append("<strong>")
            if (s.italic) append("<em>")
            if (s.underline) append("<u>")
            if (s.strike) append("<del>")
            if (s.sup) append("<sup>")
            if (s.sub) append("<sub>")
        }
        fun closeTags(open: String) = Regex("<([a-z]+)>").findAll(open).map { it.groupValues[1] }.toList().asReversed().joinToString("") { "</$it>" }
        fun flushBytes() {
            if (pending.size() == 0) return
            val t = String(pending.toByteArray(), charset)
            pending.reset()
            if (state.destination != null) infoText.append(t) else if (!state.skip) line.append(HtmlSoup.escapeText(t))
        }
        fun restyle() {
            flushBytes()
            val now = tags(state)
            if (now != openTags) {
                line.append(closeTags(openTags)).append(now)
                openTags = now
            }
        }
        fun endPara() {
            restyle()
            line.append(closeTags(openTags))
            openTags = ""
            val html = line.toString().trim()
            line.setLength(0)
            val level = if (state.outline >= 0) (state.outline + 1).coerceAtMost(6) else headingStyles[state.style] ?: 0
            paras += Triple(html, level, state.align.orEmpty())
            openTags = tags(state)
            line.append(openTags)
        }

        var i = 0
        val n = raw.length
        var ucSkip = 0
        while (i < n) {
            val c = raw[i]
            when (c) {
                '{' -> {
                    flushBytes()
                    stack += state.copy()
                    i++
                }
                '}' -> {
                    flushBytes()
                    if (state.destination != null) {
                        info[state.destination!!] = infoText.toString().trim()
                        infoText.setLength(0)
                    }
                    if (pictKind != null && state.destination == null && pict.isNotEmpty() && stack.lastOrNull()?.skip != true) Unit
                    if (pict.isNotEmpty() && pictKind != null) {
                        val hex = pict.toString().filter { it.isLetterOrDigit() }
                        val data = ByteArray(hex.length / 2) { k -> hex.substring(2 * k, 2 * k + 2).toInt(16).toByte() }
                        pict.setLength(0)
                        val src = out.image("picture.$pictKind", data)
                        pictKind = null
                        restyle()
                        line.append("<img src=\"").append(HtmlSoup.escapeAttr(src)).append("\" alt=\"\"/>")
                    }
                    val before = state
                    state = if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) else State()
                    if (!before.skip && !state.skip) restyle()
                    i++
                }
                '\\' -> {
                    if (i + 1 >= n) break
                    val nx = raw[i + 1]
                    when {
                        nx == '\'' -> {
                            if (ucSkip > 0) ucSkip-- else if (i + 3 < n) raw.substring(i + 2, i + 4).toIntOrNull(16)?.let { pending.write(it) }
                            i += 4
                        }
                        nx == '*' -> {
                            state.skip = true
                            i += 2
                        }
                        nx == '\\' || nx == '{' || nx == '}' -> {
                            if (ucSkip > 0) ucSkip-- else if (!state.skip) { flushBytes(); line.append(HtmlSoup.escapeText(nx.toString())) }
                            i += 2
                        }
                        nx == '~' -> { if (!state.skip) { flushBytes(); line.append('\u00A0') }; i += 2 }
                        nx == '-' -> i += 2
                        nx == '_' -> { if (!state.skip) { flushBytes(); line.append('‑') }; i += 2 }
                        nx == '\n' || nx == '\r' -> {
                            if (!state.skip) endPara()
                            i += 2
                        }
                        nx.isLetter() -> {
                            var j = i + 1
                            while (j < n && raw[j].isLetter()) j++
                            val word = raw.substring(i + 1, j)
                            var k = j
                            if (k < n && (raw[k] == '-' || raw[k].isDigit())) {
                                k++
                                while (k < n && raw[k].isDigit()) k++
                            }
                            val param = raw.substring(j, k).toIntOrNull()
                            if (k < n && raw[k] == ' ') k++
                            i = k
                            if (word in skipped) {
                                if (word == "title" || word == "author" || word == "subject") {
                                    flushBytes()
                                    state.destination = word
                                } else if (word == "pict") {
                                    flushBytes()
                                    state.skip = true
                                    pictKind = "?"
                                } else state.skip = true
                                continue
                            }
                            if (pictKind == "?" || (pictKind != null && state.skip)) {
                                when (word) {
                                    "pngblip" -> pictKind = "png"
                                    "jpegblip" -> pictKind = "jpg"
                                    "emfblip", "wmetafile", "macpict", "dibitmap", "wbitmap" -> pictKind = null
                                }
                                continue
                            }
                            if (state.skip && state.destination == null) continue
                            when (word) {
                                "par", "sect" -> endPara()
                                "page" -> endPara()
                                "line" -> { restyle(); line.append("<br/>") }
                                "tab" -> { flushBytes(); line.append(' ') }
                                "emdash" -> { flushBytes(); line.append('—') }
                                "endash" -> { flushBytes(); line.append('–') }
                                "lquote" -> { flushBytes(); line.append('‘') }
                                "rquote" -> { flushBytes(); line.append('’') }
                                "ldblquote" -> { flushBytes(); line.append('“') }
                                "rdblquote" -> { flushBytes(); line.append('”') }
                                "bullet" -> { flushBytes(); line.append('•') }
                                "b" -> { state.bold = param != 0; restyle() }
                                "i" -> { state.italic = param != 0; restyle() }
                                "ul", "uld", "uldb", "ulw" -> { state.underline = param != 0; restyle() }
                                "ulnone" -> { state.underline = false; restyle() }
                                "strike", "striked" -> { state.strike = param != 0; restyle() }
                                "super" -> { state.sup = true; state.sub = false; restyle() }
                                "sub" -> { state.sub = true; state.sup = false; restyle() }
                                "nosupersub" -> { state.sup = false; state.sub = false; restyle() }
                                "plain" -> { state.bold = false; state.italic = false; state.underline = false; state.strike = false; state.sup = false; state.sub = false; restyle() }
                                "pard" -> { state.style = 0; state.align = null; state.outline = -1 }
                                "outlinelevel" -> state.outline = param ?: -1
                                "s" -> state.style = param ?: 0
                                "qc" -> state.align = "center"
                                "qr" -> state.align = "right"
                                "ql", "qj" -> state.align = null
                                "uc" -> state.uc = param ?: 1
                                "u" -> {
                                    if (param != null) {
                                        flushBytes()
                                        val cp = if (param < 0) param + 65536 else param
                                        if (state.destination != null) infoText.appendCodePoint(cp) else if (!state.skip) line.append(HtmlSoup.escapeText(String(Character.toChars(cp))))
                                        ucSkip = state.uc
                                    }
                                }
                            }
                        }
                        else -> i += 2
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (pictKind != null && state.skip) {
                        if (pictKind != "?") pict.append(c)
                    } else if (ucSkip > 0) ucSkip-- else if (state.destination != null || !state.skip) pending.write(c.code)
                    i++
                }
            }
        }
        endPara()
        out.meta = out.meta.copy(
            title = info["title"]?.ifBlank { null } ?: out.meta.title,
            authors = listOfNotNull(info["author"]?.ifBlank { null }),
        )
        // As pages: a new one at each top heading.
        val split = (1..6).firstOrNull { lvl -> paras.count { it.second == lvl } >= 2 }
        var title: String? = null
        val body = StringBuilder()
        fun flush() {
            if (body.isNotBlank()) out.page(title ?: if (out.isEmpty) out.meta.title else null, body.toString())
            body.setLength(0)
            title = null
        }
        for ((html, level, align) in paras) {
            val cls = if (align.isNotEmpty()) " class=\"$align\"" else ""
            val plain = HtmlSoup.decode(html.replace(Regex("<[^>]+>"), "")).trim()
            if (level > 0 && plain.isNotEmpty()) {
                if (level == split) {
                    flush()
                    title = plain
                }
                body.append("<h${level.coerceAtMost(4)}$cls>$html</h${level.coerceAtMost(4)}>\n")
            } else if (html.replace(Regex("<[^>]+>"), "").isBlank() && !html.contains("<img")) body.append("<p class=\"space\"></p>\n")
            else body.append("<p$cls>$html</p>\n")
        }
        flush()
        out.write(target)
    }
}
