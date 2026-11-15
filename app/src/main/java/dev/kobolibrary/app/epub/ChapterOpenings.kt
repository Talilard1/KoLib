package dev.kobolibrary.app.epub

/**
 * Some books keep each chapter's opening (its number, an ornament, sometimes its title) in a little
 * file of its own, just before the file with the chapter's text. Readers start every file on a new
 * page, so the opening ends up on a page by itself. This finds those pairs, and puts an opening in
 * front of its chapter's text for the reader to show them together.
 */
object ChapterOpenings {

    /** An opening is at most this many words: "Chapter 12", a title, perhaps a short line under it. */
    const val MOST_WORDS = 40

    /** And the chapter after it has at least this many: a chapter, not another title page. */
    const val LEAST_WORDS = 150

    /** Just a chapter's number: "Chapter 12", "CHAPTER TWO", "Ch. 3", "IV", "12", "Prologue". */
    private val numberOnly = Regex(
        """^(?:(?:chapter|chap|ch)\s*(?:[0-9]+|[ivxlcdm]+|[a-z]+(?:[ -][a-z]+)?)|[0-9]+|[ivxlcdm]+|prologue|epilogue|interlude)$""",
        RegexOption.IGNORE_CASE,
    )

    private fun norm(s: String) = s.lowercase().replace(Regex("""[\p{Punct}–—‘’“”]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /**
     * Whether [opening] (with its file's text) is the opening of the chapter [next] (with its text):
     * both are read through, the opening is a few words at most and the next one is a real chapter,
     * and either the opening's title ends with the chapter's ("1 The Hook" before "The Hook") or the
     * opening says no more than the chapter's number. A part's title page ("Part One") isn't one: a
     * part stands on a page of its own in a printed book too.
     */
    fun joins(opening: Chapter, openingText: String, next: Chapter, nextText: String): Boolean {
        if (!opening.linear || !next.linear) return false
        val openingWords = EpubText.plainText(openingText).replace(Regex("""\s+"""), " ").trim()
        if (EpubText.countWords(openingWords) > MOST_WORDS) return false
        if (EpubText.countWords(EpubText.plainText(nextText)) < LEAST_WORDS) return false
        val a = norm(opening.title)
        val b = norm(next.title)
        val titled = b.length >= 3 && a.endsWith(b) && !a.startsWith("part ") && !a.startsWith("book ")
        val numbered = openingWords.isNotEmpty() && numberOnly.matches(norm(openingWords))
        return titled || numbered
    }

    /**
     * [text] (a chapter at [textPath]) with [opening] (its opening's page, at [openingPath]) put in
     * front of it, for the reader: the opening's content goes in a box of its own just before the
     * chapter's body, so the body (and every place in it the reader remembers) is untouched. Null if
     * it can't be done safely: the two aren't in the same folder (links would break), or either
     * isn't well-formed XHTML (a browser reading it as HTML would move the box inside the body).
     * [boxId] names the box: "kl-opening" for an opening, "kl-pair" for a short page shown beside the next.
     */
    fun join(text: String, textPath: String, opening: String, openingPath: String, boxId: String = OPENING_BOX): String? =
        joinAll(text, textPath, listOf(opening to openingPath), boxId)

    /**
     * [text] (a chapter at [textPath]) with [pieces] (the texts and paths of the little pages before it, in order: a
     * part's title, an ornament) put in front of it, as [join] puts an opening: in one box, each piece in a box of
     * its own within it. Null if it can't be done safely.
     */
    fun joinAll(text: String, textPath: String, pieces: List<Pair<String, String>>, boxId: String = OPENING_BOX): String? {
        if (pieces.isEmpty()) return null
        if (pieces.any { (_, p) -> textPath.substringBeforeLast('/', "") != p.substringBeforeLast('/', "") }) return null
        val doc = try { Xml.parse(text) } catch (e: XmlException) { return null }
        if (doc.root.namespaceUri() != XHTML_NS) return null
        val body = doc.root.elements().firstOrNull { it.localName == "body" } ?: return null
        val head = doc.root.elements().firstOrNull { it.localName == "head" }
        val parsed = pieces.map { (t, _) ->
            val odoc = try { Xml.parse(t) } catch (e: XmlException) { return null }
            val obody = odoc.root.elements().firstOrNull { it.localName == "body" } ?: return null
            if (obody.selfClosing) return null
            Triple(t, odoc, obody)
        }

        // Its namespaces (an svg's xlink, say) and its body's class come along, so it reads and looks as it did.
        val attrs = LinkedHashMap<String, String>()
        attrs["id"] = boxId
        val box = if (parsed.size == 1) {
            val (t, odoc, obody) = parsed[0]
            obody.attributes["class"]?.let { attrs["class"] = it }
            for ((k, v) in odoc.root.attributes + obody.attributes) if (k.startsWith("xmlns:")) attrs.putIfAbsent(k, v)
            Xml.startTag("div", attrs, false) + t.substring(obody.startTagEnd, obody.endTagStart) + "</div>\n"
        } else {
            for ((_, odoc, obody) in parsed) for ((k, v) in odoc.root.attributes + obody.attributes) if (k.startsWith("xmlns:")) attrs.putIfAbsent(k, v)
            Xml.startTag("div", attrs, false) + parsed.joinToString("") { (t, _, obody) ->
                val each = LinkedHashMap<String, String>()
                obody.attributes["class"]?.let { each["class"] = it }
                Xml.startTag("div", each, false) + t.substring(obody.startTagEnd, obody.endTagStart) + "</div>"
            } + "</div>\n"
        }

        // Its style sheets, where the chapter doesn't already have them (same folder, so the links work as they are).
        val linkRe = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
        val hrefRe = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        fun sheets(t: String, end: Int) = linkRe.findAll(t.substring(0, end))
            .filter { it.value.contains("stylesheet", ignoreCase = true) }
            .mapNotNull { m -> hrefRe.find(m.value)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            .toList()
        val have = if (head != null) sheets(text, head.end).toSet() else emptySet()
        val missing = parsed.flatMap { (t, odoc, _) ->
            val ohead = odoc.root.elements().firstOrNull { it.localName == "head" }
            if (ohead != null) sheets(t, ohead.end) else emptyList()
        }.distinct().filter { it !in have }

        val edits = TextEdits(text)
        if (missing.isNotEmpty() && head != null && !head.selfClosing) {
            edits.insert(head.endTagStart, missing.joinToString("") { "<link rel=\"stylesheet\" type=\"text/css\" href=\"${Xml.escapeAttr(it)}\"/>" })
        }
        edits.insert(body.start, box)
        val out = edits.apply()
        return out.takeIf { XmlCheck.check(it, htmlEntities = true) == null }
    }

    private const val XHTML_NS = "http://www.w3.org/1999/xhtml"

    /** The box an opening goes in, in front of its chapter. */
    const val OPENING_BOX = "kl-opening"

    /** The box a short page goes in, in front of the short page shown beside it. */
    const val PAIR_BOX = "kl-pair"
}
