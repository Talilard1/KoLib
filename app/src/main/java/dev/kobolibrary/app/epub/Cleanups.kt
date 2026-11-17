package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr

/** One-tap tidying of a book's text. Each finds what it would change first, so its count can be shown. */
object Cleanups {

    enum class Kind(val label: String, val explanation: String, private val one: String, private val many: String) {
        BLANK_LINES(
            tr("Remove extra blank lines"),
            tr("Collapses repeated blank lines and removes them from the start and end of chapters. Scene breaks aren't affected."),
            tr("1 blank line"), tr("{0} blank lines"),
        ),
        CURLY_QUOTES(
            tr("Straight quotes to curly"),
            tr("\"Hello,\" he said, 'it's mine.' becomes “Hello,” he said, ‘it’s mine.’"),
            tr("1 quote"), tr("{0} quotes"),
        ),
        JOIN_LINES(
            tr("Join lines broken mid-sentence"),
            tr("Rejoins paragraphs split mid-sentence, common in books converted from PDFs or scans."),
            tr("1 broken line"), tr("{0} broken lines"),
        ),
        DASHES(
            tr("Double hyphens to dashes"),
            tr("Changes -- and --- into a proper dash (—)."),
            tr("1 dash"), tr("{0} dashes"),
        ),
        STRIP_FONTS(
            tr("Remove embedded fonts"),
            tr("Deletes the font files inside the book, so the Kobo uses the font you pick on it."),
            tr("1 font"), tr("{0} fonts"),
        ),
        ;

        /** "3 dashes", in the language KoLib is shown in. */
        fun describe(count: Int): String = if (count == 1) one else many.replace("{0}", "%,d".format(count))
    }

    data class Result(val kind: Kind, val changes: Map<String, ByteArray?>, val count: Int)

    fun run(kind: Kind, files: BookFiles, structure: BookStructure, only: Set<String>? = null): Result {
        val chapters = structure.chapters.filter { only == null || it.path in only }.map { it.path }
        return when (kind) {
            Kind.BLANK_LINES -> perFile(kind, files, chapters, ::blankLines)
            Kind.CURLY_QUOTES -> perFile(kind, files, chapters, ::curlyQuotes)
            Kind.JOIN_LINES -> {
                val words = bookWords(files, structure.chapters.map { it.path })
                perFile(kind, files, chapters) { text -> joinLines(text, words) }
            }
            Kind.DASHES -> perFile(kind, files, chapters, ::dashes)
            Kind.STRIP_FONTS -> stripFonts(files, structure)
        }
    }

    private fun perFile(kind: Kind, files: BookFiles, paths: List<String>, fix: (String) -> Pair<String, Int>): Result {
        val changes = LinkedHashMap<String, ByteArray?>()
        var count = 0
        for (path in paths) {
            val text = files.text(path) ?: continue
            val (fixed, n) = try {
                fix(text)
            } catch (e: XmlException) {
                continue
            }
            if (n > 0 && fixed != text) {
                changes[path] = fixed.toByteArray(Charsets.UTF_8)
                count += n
            }
        }
        return Result(kind, changes, count)
    }

    // ---- blank lines ----------------------------------------------------------------------------

    private val emptyBlockNames = setOf("p", "div")
    private val invisible = Regex("[\\s\\u00A0\\u2007\\u202F\\u200B]*")

    private fun isEmptyBlock(e: XElement): Boolean {
        if (e.localName.lowercase() !in emptyBlockNames) return false
        if (e.attributes.keys.any { it == "id" || it.endsWith(":id") || it == "name" }) return false
        return isEmptyContent(e)
    }

    private fun isEmptyContent(e: XElement): Boolean = e.children.all { c ->
        when (c) {
            is XText -> c.text.matches(invisible)
            is XOther -> true
            is XElement -> c.localName.equals("br", true) || (c.localName.lowercase() in setOf("span", "i", "b", "em", "strong", "font") && c.attributes.isEmpty() && isEmptyContent(c))
        }
    }

    private fun isBr(n: XNode) = n is XElement && n.localName.equals("br", true)
    private fun isSpace(n: XNode) = (n is XText && n.text.matches(invisible) && !n.text.contains(' ')) || n is XOther

    fun blankLines(text: String): Pair<String, Int> {
        val doc = Xml.parse(text)
        val body = TextMap.body(doc)
        val edits = TextEdits(text)
        var count = 0
        // The chapter's edges: the body, or a single wrapper inside it.
        val bodyElements = body.elements()
        val edges = setOf(body) + (if (bodyElements.size == 1 && body.children.none { it is XText && !isSpace(it) }) bodyElements else emptyList())
        fun walk(parent: XElement) {
            val kids = parent.children
            // Runs of empty paragraphs: keep the first, unless the run is at the start or end of the chapter.
            val elements = kids.filter { it is XElement || (it is XText && !isSpace(it)) }
            var k = 0
            while (k < elements.size) {
                val e = elements[k]
                if (e is XElement && isEmptyBlock(e)) {
                    var run = k
                    while (run + 1 < elements.size && (elements[run + 1] as? XElement)?.let { isEmptyBlock(it) } == true) run++
                    val atEdge = parent in edges && (k == 0 || run == elements.size - 1)
                    val from = if (atEdge) k else k + 1
                    for (r in from..run) {
                        edits.removeWithLeadingSpace(elements[r])
                        count++
                    }
                    k = run + 1
                    continue
                }
                if (e is XElement) walk(e)
                k++
            }
            // Runs of line breaks inside a paragraph: keep one.
            var i = 0
            while (i < kids.size) {
                if (isBr(kids[i])) {
                    var j = i + 1
                    val extra = ArrayList<XNode>()
                    while (j < kids.size && (isBr(kids[j]) || isSpace(kids[j]))) {
                        if (isBr(kids[j])) extra += kids[j]
                        j++
                    }
                    for (b in extra) {
                        edits.replace(b.start, b.end, "")
                        count++
                    }
                    i = j
                } else i++
            }
        }
        walk(body)
        return edits.apply() to count
    }

    // ---- curly quotes ---------------------------------------------------------------------------

    private val elisions = listOf(
        "tis", "twas", "twere", "twill", "em", "n", "til", "cause", "bout", "round", "neath", "twixt", "gainst",
        "cept", "fore", "scuse", "ere", "nother", "kay", "s",
    )

    private fun isWordChar(c: Char?) = c != null && (c.isLetterOrDigit())
    private fun isOpenContext(c: Char?) = c == null || c.isWhitespace() || c in "([{<—–-‘“ /"

    /** The curly form of the straight quote at [i] in [s] (a block's text). */
    fun curlyFor(s: CharSequence, i: Int): Char {
        val q = s[i]
        val prev = s.getOrNull(i - 1)
        val next = s.getOrNull(i + 1)
        if (q == '"') {
            return when {
                isOpenContext(prev) && next != null && !next.isWhitespace() -> '“'
                isOpenContext(prev) && next == null -> '”'
                prev == null -> '“'
                else -> '”'
            }
        }
        // A single quote: apostrophe, opening or closing.
        if (isWordChar(prev) && isWordChar(next)) return '’'
        if (isOpenContext(prev)) {
            if (next != null && next.isDigit()) return '’' // '90s
            val rest = s.subSequence(i + 1, minOf(s.length, i + 12)).takeWhile { it.isLetter() }.toString().lowercase()
            if (rest.isNotEmpty() && rest in elisions) {
                // "'em" and the like, unless it's really a quotation of that one word ('em').
                val after = s.getOrNull(i + 1 + rest.length)
                if (after != '\'' ) return '’'
            }
            return if (next == null || next.isWhitespace()) '’' else '‘'
        }
        return '’'
    }

    fun curlyQuotes(text: String): Pair<String, Int> = byBlock(text) { blockText, at ->
        val c = blockText[at]
        if (c == '"' || c == '\'') curlyFor(blockText, at) else null
    }

    fun dashes(text: String): Pair<String, Int> {
        val doc = Xml.parse(text)
        val edits = TextEdits(text)
        var count = 0
        for (seg in TextMap.segments(doc)) {
            if (seg.skip) continue
            val t = seg.text
            var i = t.indexOf("--")
            while (i >= 0) {
                var j = i
                while (j < t.length && t[j] == '-') j++
                if (j - i in 2..3) {
                    val raw = seg.decoded.rawRange(i, j)
                    edits.replace(raw.first, raw.last + 1, "—")
                    count++
                }
                i = t.indexOf("--", j)
            }
        }
        return edits.apply() to count
    }

    /**
     * Runs [change] over the quotes in each block's text, left to right, so the characters on either side
     * are known even across italics and other inline tags, and earlier quotes are already curly.
     */
    private fun byBlock(text: String, change: (blockText: CharSequence, at: Int) -> Char?): Pair<String, Int> {
        val doc = Xml.parse(text)
        val segments = TextMap.segments(doc)
        val edits = TextEdits(text)
        var count = 0
        val byBlock = LinkedHashMap<XElement?, MutableList<TextSegment>>()
        for (s in segments) byBlock.getOrPut(s.block) { mutableListOf() } += s
        for ((_, segs) in byBlock) {
            val sb = StringBuilder()
            val starts = IntArray(segs.size)
            for ((k, s) in segs.withIndex()) {
                starts[k] = sb.length
                sb.append(s.text)
            }
            for ((k, s) in segs.withIndex()) {
                if (s.skip) continue
                for (i in s.text.indices) {
                    val c = s.text[i]
                    if (c != '"' && c != '\'') continue
                    val repl = change(sb, starts[k] + i) ?: continue
                    if (repl == c) continue
                    sb.setCharAt(starts[k] + i, repl)
                    val raw = s.decoded.rawRange(i, i + 1)
                    edits.replace(raw.first, raw.last + 1, repl.toString())
                    count++
                }
            }
        }
        return edits.apply() to count
    }

    // ---- broken lines ---------------------------------------------------------------------------

    private val endsSentence = Regex("[.!?:;…”\"’')\\]—–]\\s*$")

    /** Every word in the book, lower case, to tell "exam-" + "ple" from "well-" + "known". */
    fun bookWords(files: BookFiles, paths: List<String>): Set<String> {
        val words = HashSet<String>()
        val word = Regex("[\\p{L}][\\p{L}'’-]*")
        for (p in paths) {
            val text = files.text(p) ?: continue
            for (m in word.findAll(EpubText.plainText(text))) words += m.value.lowercase()
        }
        return words
    }

    fun joinLines(text: String, words: Set<String>): Pair<String, Int> {
        val doc = Xml.parse(text)
        val body = TextMap.body(doc)
        val edits = TextEdits(text)
        var count = 0

        fun innerRaw(e: XElement) = text.substring(e.startTagEnd, e.endTagStart)

        fun walk(parent: XElement) {
            val elements = parent.children.filter { it !is XText || !it.text.isBlank() }
            var k = 0
            while (k < elements.size) {
                val a = elements[k] as? XElement
                if (a == null || !a.localName.equals("p", true) || a.selfClosing) {
                    (a)?.let { if (!TextMap.isHeading(it)) walk(it) }
                    k++
                    continue
                }
                // Collect the paragraphs that continue this one.
                val chain = mutableListOf(a)
                var joined = a.text()
                var j = k + 1
                while (j < elements.size) {
                    val b = elements[j] as? XElement ?: break
                    if (!b.localName.equals(a.localName, true) || b.selfClosing) break
                    if (b.attributes["class"] != a.attributes["class"] || b.attributes.containsKey("id")) break
                    val end = joined.trimEnd()
                    val start = b.text().trimStart()
                    if (end.isEmpty() || start.isEmpty()) break
                    if (endsSentence.containsMatchIn(end)) break
                    if (!start[0].isLowerCase()) break
                    chain += b
                    joined = end + " " + start
                    j++
                }
                if (chain.size > 1) {
                    val sb = StringBuilder(innerRaw(chain[0]).trimEnd())
                    for (b in chain.drop(1)) {
                        val next = innerRaw(b).trimStart()
                        val prevVisible = sb.toString().let { EpubText.plainText(it) }.trimEnd()
                        if (prevVisible.endsWith("-") && prevVisible.length > 1 && prevVisible[prevVisible.length - 2].isLetter()) {
                            // A word split over two lines: keep the hyphen only if the book spells it that way.
                            val head = prevVisible.takeLastWhile { it.isLetter() || it == '-' }.trimEnd('-').takeLastWhile { it.isLetter() }
                            val tail = EpubText.plainText(next).takeWhile { it.isLetter() }
                            val whole = (head + tail).lowercase()
                            if (whole in words && "$head-$tail".lowercase() !in words && sb.endsWith("-")) {
                                sb.setLength(sb.length - 1) // drop the hyphen
                            }
                            sb.append(next)
                        } else {
                            sb.append(' ').append(next)
                        }
                        edits.removeWithLeadingSpace(b)
                        count++
                    }
                    edits.replace(chain[0].startTagEnd, chain[0].endTagStart, sb.toString())
                    k = j
                    continue
                }
                k++
            }
            // A line break in the middle of a sentence inside one paragraph.
            if (parent.localName.equals("p", true)) {
                val kids = parent.children
                for ((idx, n) in kids.withIndex()) {
                    if (!isBr(n)) continue
                    val before = kids.subList(0, idx).joinToString("") { (it as? XText)?.text ?: (it as? XElement)?.text() ?: "" }.trimEnd()
                    val after = kids.subList(idx + 1, kids.size).joinToString("") { (it as? XText)?.text ?: (it as? XElement)?.text() ?: "" }.trimStart()
                    if (before.isEmpty() || after.isEmpty()) continue
                    if (endsSentence.containsMatchIn(before) || !after[0].isLowerCase()) continue
                    edits.replace(n.start, n.end, " ")
                    count++
                }
            }
        }
        walk(body)
        return edits.apply() to count
    }

    // ---- fonts ----------------------------------------------------------------------------------

    private val fontTypes = setOf(
        "application/vnd.ms-opentype", "application/font-sfnt", "application/x-font-ttf", "application/x-font-truetype",
        "application/x-font-opentype", "application/font-woff", "application/x-font-otf", "application/x-truetype-font",
    )
    private val fontExtensions = setOf("ttf", "otf", "woff", "woff2", "pfb", "pfm")
    private val fontFace = Regex("(?is)@font-face\\s*\\{[^}]*\\}\\s*")

    fun isFont(item: ManifestItem): Boolean =
        item.mediaType.startsWith("font/") || item.mediaType in fontTypes ||
            item.href.substringAfterLast('.').lowercase().substringBefore('?') in fontExtensions

    private fun stripFonts(files: BookFiles, structure: BookStructure): Result {
        val opf = structure.opf
        val changes = LinkedHashMap<String, ByteArray?>()
        val fonts = opf.items.filter { isFont(it) }
        val fontPaths = fonts.map { structure.pathOf(it) }.toSet()
        // Font files the manifest forgot to list go too.
        val stray = files.names().filter { it.substringAfterLast('.').lowercase() in fontExtensions && it !in fontPaths }
        if (fonts.isEmpty() && stray.isEmpty()) return Result(Kind.STRIP_FONTS, emptyMap(), 0)

        if (fonts.isNotEmpty()) {
            val edits = TextEdits(structure.opfText)
            fonts.forEach { edits.removeWithLeadingSpace(it.element) }
            changes[structure.opfPath] = edits.apply().toByteArray(Charsets.UTF_8)
        }
        (fontPaths + stray).forEach { if (files.has(it)) changes[it] = null }

        // @font-face rules in style sheets and <style> elements.
        for (item in opf.items) {
            val path = structure.pathOf(item)
            val isCss = item.mediaType == "text/css"
            val isDoc = item.isContent
            if (!isCss && !isDoc) continue
            val text = files.text(path) ?: continue
            if (!text.contains("@font-face", ignoreCase = true)) continue
            val fixed = if (isCss) text.replace(fontFace, "") else Regex("(?is)(<style\\b[^>]*>)(.*?)(</style\\s*>)").replace(text) { m ->
                m.groupValues[1] + m.groupValues[2].replace(fontFace, "") + m.groupValues[3]
            }
            if (fixed != text) changes[path] = fixed.toByteArray(Charsets.UTF_8)
        }

        // Obfuscated fonts are listed in encryption.xml; a list naming missing files upsets some readers.
        val encPath = "META-INF/encryption.xml"
        files.text(encPath)?.let { enc ->
            runCatching {
                val doc = Xml.parse(enc)
                val gone = (fontPaths + stray)
                val entries = doc.root.elements().filter { it.localName == "EncryptedData" }
                val doomed = entries.filter { e ->
                    val uri = e.descendants().firstOrNull { it.localName == "CipherReference" }?.attributes?.get("URI")
                    uri != null && EpubFile.resolvePath("", uri) in gone
                }
                if (doomed.isNotEmpty()) {
                    if (doomed.size == entries.size) changes[encPath] = null
                    else {
                        val edits = TextEdits(enc)
                        doomed.forEach { edits.removeWithLeadingSpace(it) }
                        changes[encPath] = edits.apply().toByteArray(Charsets.UTF_8)
                    }
                }
            }
        }
        return Result(Kind.STRIP_FONTS, changes, (fontPaths + stray).size)
    }
}
