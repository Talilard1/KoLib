package dev.kobolibrary.app.epub

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Find and replace across a book's text. Only the words a reader sees are searched (not tags or
 * styles), each match comes with the text around it, and replacements go back into the files without
 * disturbing anything else.
 *
 * Name mode is for swapping a name that is also an ordinary word (Rose, Hope, Bill, Grace): every
 * match is found, and the ones that probably aren't the name ("a rose", "the Rose Inn") are flagged.
 */
object FindReplace {

    data class Options(
        val query: String,
        val matchCase: Boolean = false,
        val wholeWords: Boolean = false,
        val nameMode: Boolean = false,
        /** The query is a regular expression (and $1… in the replacement refer to its groups). */
        val pattern: Boolean = false,
    )

    enum class Flag {
        /** Not in name mode. */
        NONE,
        /** Looks like the name. */
        NAME,
        /** Can't tell (starts a sentence, all capitals, a heading). */
        UNSURE,
        /** Probably the ordinary word, not the name. */
        NOT_NAME,
    }

    data class Match(
        val path: String,
        val chapter: String,
        /** Which text node in the file's body (as a browser counts them). */
        val segment: Int,
        /** Where the match is within that text node's text. */
        val start: Int,
        val end: Int,
        /** Where the match is in the file. */
        val rawStart: Int,
        val rawEnd: Int,
        val text: String,
        val before: String,
        val after: String,
        val flag: Flag = Flag.NONE,
        val reason: String? = null,
        /** Groups of a pattern match, for $1… in the replacement. */
        val groups: List<String?> = emptyList(),
    ) {
        /** Identifies the match while the file doesn't change. */
        val key: String get() = "$path#$rawStart"
        /** Identifies the match by its words, which survive changes elsewhere in the file. */
        val contextKey: String get() = "$path|$before|$text|$after"
    }

    class BadPattern(message: String) : Exception(message)

    private const val WORD = "\\p{L}\\p{N}_"

    fun compile(options: Options): Regex? {
        val q = options.query
        if (q.isEmpty()) return null
        val body = if (options.pattern) q else Pattern.quote(q)
        val whole = options.wholeWords || options.nameMode
        val src = if (whole) "(?<![$WORD])(?:$body)(?![$WORD])" else body
        val flags = if (options.matchCase && !options.nameMode) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        val regex = try {
            Pattern.compile(src, flags).toRegex()
        } catch (e: PatternSyntaxException) {
            throw BadPattern(e.description ?: "That pattern isn't valid.")
        }
        if (regex.matches("")) throw BadPattern("That pattern would match empty text, so it can't be used.")
        return regex
    }

    /** One file's text as a browser would show it, with what's needed to search and change it. */
    class FileText(val path: String, val title: String, val doc: XDocument, val segments: List<TextSegment>) {
        /** The text of each block (paragraph, heading…), for context and sentence starts. */
        private val blockText = HashMap<XElement?, StringBuilder>()
        private val segmentOffset = IntArray(segments.size)

        init {
            for ((i, s) in segments.withIndex()) {
                val sb = blockText.getOrPut(s.block) { StringBuilder() }
                segmentOffset[i] = sb.length
                sb.append(s.text)
            }
        }

        fun blockOf(i: Int): String = blockText[segments[i].block]?.toString().orEmpty()
        fun offsetInBlock(i: Int): Int = segmentOffset[i]
    }

    fun read(files: BookFiles, path: String, title: String): FileText? {
        val text = files.text(path) ?: return null
        val doc = try {
            Xml.parse(text)
        } catch (e: XmlException) {
            return null
        }
        return FileText(path, title, doc, TextMap.segments(doc))
    }

    /** The files searched: the chapters in reading order (or just [only]). */
    fun searchPaths(structure: BookStructure, only: Set<String>? = null): List<Pair<String, String>> =
        structure.chapters.filter { only == null || it.path in only }.map { it.path to it.title }

    fun search(files: BookFiles, structure: BookStructure, options: Options, only: Set<String>? = null, limit: Int = 20_000): List<Match> {
        val regex = compile(options) ?: return emptyList()
        val out = ArrayList<Match>()
        for ((path, title) in searchPaths(structure, only)) {
            val ft = read(files, path, title) ?: continue
            searchFile(ft, regex, options, out, limit)
            if (out.size >= limit) break
        }
        return out
    }

    fun searchFile(ft: FileText, regex: Regex, options: Options, out: MutableList<Match>, limit: Int = Int.MAX_VALUE) {
        for ((i, seg) in ft.segments.withIndex()) {
            if (seg.skip || seg.text.isBlank()) continue
            for (m in regex.findAll(seg.text)) {
                if (m.value.isEmpty()) continue
                val block = ft.blockOf(i)
                val inBlock = ft.offsetInBlock(i)
                val bStart = inBlock + m.range.first
                val bEnd = inBlock + m.range.last + 1
                val (flag, reason) = if (options.nameMode) nameFlag(options.query, m.value, block, bStart, bEnd, seg.inHeading) else Flag.NONE to null
                val raw = seg.decoded.rawRange(m.range.first, m.range.last + 1)
                out += Match(
                    path = ft.path,
                    chapter = ft.title,
                    segment = seg.ordinal,
                    start = m.range.first,
                    end = m.range.last + 1,
                    rawStart = raw.first,
                    rawEnd = raw.last + 1,
                    text = m.value,
                    before = contextBefore(block, bStart),
                    after = contextAfter(block, bEnd),
                    flag = flag,
                    reason = reason,
                    groups = if (options.pattern) m.groups.map { it?.value } else emptyList(),
                )
                if (out.size >= limit) return
            }
        }
    }

    private const val CONTEXT = 70

    private fun tidy(s: String) = s.replace(Regex("\\s+"), " ")

    fun contextBefore(block: String, at: Int): String {
        val from = maxOf(0, at - CONTEXT)
        var s = tidy(block.substring(from, at))
        if (from > 0) {
            val space = s.indexOf(' ')
            if (space in 0..20) s = s.substring(space + 1)
            s = "…$s"
        }
        return s.trimStart()
    }

    fun contextAfter(block: String, at: Int): String {
        val to = minOf(block.length, at + CONTEXT)
        var s = tidy(block.substring(at, to))
        if (to < block.length) {
            val space = s.lastIndexOf(' ')
            if (space > s.length - 20 && space > 0) s = s.substring(0, space)
            s = "$s…"
        }
        return s.trimEnd()
    }

    // ---- name mode ------------------------------------------------------------------------------

    private val determiners = setOf(
        "the", "a", "an", "these", "those", "my", "your", "his", "its", "our", "their", "some", "any", "each", "every",
        "another", "whose", "such", "several", "many", "few", "both", "either", "neither", "much", "more", "most",
        "all", "no", "own", "every", "thy", "thine", "yon",
    )
    /** Words that come before both names and ordinary words ("told her Rose…", "her rose"). */
    private val ambiguous = setOf("her", "that", "this", "one", "what", "which", "other")
    private val sentenceEnd = setOf('.', '!', '?', '…', ':', ';')
    private val openers = setOf('"', '“', '‘', '\'', '(', '[', '«', '‹', '—', '–', '-')

    /** Whether a match looks like the name, and if not why. */
    fun nameFlag(name: String, matched: String, block: String, start: Int, end: Int, inHeading: Boolean): Pair<Flag, String?> {
        val first = matched.firstOrNull() ?: return Flag.UNSURE to null
        if (!first.isUpperCase()) return Flag.NOT_NAME to "lower case"
        // The word before it.
        var k = start - 1
        while (k >= 0 && block[k].isWhitespace()) k--
        val wordEnd = k + 1
        while (k >= 0 && (block[k].isLetter() || block[k] == '\'' || block[k] == '’')) k--
        val previous = block.substring(k + 1, wordEnd).lowercase()
        if (previous.isNotEmpty() && previous in determiners) return Flag.NOT_NAME to "after “$previous”"
        if (previous.isNotEmpty() && previous in ambiguous) return Flag.UNSURE to "after “$previous”"
        // Joined to another word: "Rose-bush", "Hope-filled".
        if ((start > 0 && block[start - 1] == '-') || (end < block.length && block[end] == '-' && end + 1 < block.length && block[end + 1].isLetter())) {
            return Flag.UNSURE to "part of a hyphenated word"
        }
        val allCaps = matched.length > 1 && matched.none { it.isLowerCase() } && name.any { it.isLowerCase() }
        if (allCaps) return Flag.UNSURE to "all capitals"
        if (inHeading) return Flag.UNSURE to "in a heading"
        // At the start of a sentence a capital letter says nothing.
        var j = start - 1
        while (j >= 0 && (block[j].isWhitespace() || block[j] in openers)) j--
        if (j < 0) return Flag.UNSURE to "starts a paragraph"
        if (block[j] in sentenceEnd || (block[j] in setOf('"', '”', '’') && j > 0 && block[j - 1] in sentenceEnd)) {
            return Flag.UNSURE to "starts a sentence"
        }
        return Flag.NAME to null
    }

    // ---- replacing ------------------------------------------------------------------------------

    /** What [match] becomes. Without Match case, the replacement follows the found text's capitals. */
    fun replacementFor(match: Match, replacement: String, options: Options): String {
        var r = if (options.pattern) expandGroups(replacement, match) else replacement
        if (!options.matchCase || options.nameMode) r = followCase(match.text, r)
        return r
    }

    private fun expandGroups(replacement: String, match: Match): String = buildString {
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '$' && i + 1 < replacement.length) {
                val d = replacement[i + 1]
                if (d == '$') { append('$'); i += 2; continue }
                if (d.isDigit()) {
                    append(match.groups.getOrNull(d - '0') ?: "")
                    i += 2
                    continue
                }
            }
            if (c == '\\' && i + 1 < replacement.length && replacement[i + 1] == 'n') { append('\n'); i += 2; continue }
            append(c)
            i++
        }
    }

    /** "ROSE" → "LILY"; "Rose" → "Lily"; otherwise the replacement as typed. */
    fun followCase(found: String, replacement: String): String {
        val letters = found.filter { it.isLetter() }
        if (letters.isEmpty() || replacement.isEmpty()) return replacement
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> replacement.uppercase()
            letters[0].isUpperCase() && replacement[0].isLowerCase() -> replacement[0].uppercaseChar() + replacement.substring(1)
            else -> replacement
        }
    }

    /**
     * Replaces [matches] (from one search, positions as they are now) and returns the changed files.
     * Matches that no longer fit the file are skipped and counted.
     */
    fun replace(files: BookFiles, matches: List<Match>, replacement: String, options: Options): Pair<Map<String, ByteArray>, Int> {
        val out = LinkedHashMap<String, ByteArray>()
        var skipped = 0
        for ((path, inFile) in matches.groupBy { it.path }) {
            val text = files.text(path) ?: continue
            val edits = TextEdits(text)
            var last = Int.MAX_VALUE
            for (m in inFile.sortedByDescending { it.rawStart }) {
                if (m.rawEnd > text.length || m.rawEnd > last || m.rawStart > m.rawEnd) { skipped++; continue }
                // Make sure the file still has this text here.
                val here = TextMap.decode(text, m.rawStart, m.rawEnd).text
                if (here != m.text) { skipped++; continue }
                edits.replace(m.rawStart, m.rawEnd, Xml.escapeText(replacementFor(m, replacement, options)))
                last = m.rawStart
            }
            val changed = edits.apply()
            if (changed != text) out[path] = changed.toByteArray(Charsets.UTF_8)
        }
        return out to skipped
    }

    /**
     * Where the remaining [matches] are after [done] was replaced by [newText] in its file: later
     * matches in the same file move along.
     */
    fun shiftAfter(matches: List<Match>, done: Match, newText: String): List<Match> {
        val rawDelta = Xml.escapeText(newText).length - (done.rawEnd - done.rawStart)
        val textDelta = newText.length - (done.end - done.start)
        return matches.map { m ->
            if (m.path != done.path || m.rawStart < done.rawEnd || m === done) m
            else m.copy(
                rawStart = m.rawStart + rawDelta,
                rawEnd = m.rawEnd + rawDelta,
                start = if (m.segment == done.segment) m.start + textDelta else m.start,
                end = if (m.segment == done.segment) m.end + textDelta else m.end,
            )
        }
    }
}
