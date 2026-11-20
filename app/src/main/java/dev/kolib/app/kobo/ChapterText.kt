package dev.kolib.app.kobo

import dev.kolib.app.epub.XElement
import dev.kolib.app.epub.XNode
import dev.kolib.app.epub.XText
import dev.kolib.app.epub.Xml

/**
 * A chapter's text, a piece at a time (its text nodes, in order), for finding the same place three ways:
 * as KoLib's reader finds it (letters into the chapter, or words and which time they occur), as a kepub on the Kobo
 * has it (a sentence's span and the character in it), and as an EPUB on the Kobo has it (one of Adobe's points).
 *
 * For a kepub's spans, build it from the chapter made into a kepub (see [KepubConverter]): the text is the same,
 * cut into sentences. For Adobe's points, build it from the chapter as it is in the book.
 */
internal class ChapterText(xhtml: String) {

    class Piece(
        val node: XText,
        /** Its text, with character references turned into characters. */
        val text: String,
        /** Whether it's read (not in a script or a style): only then are its letters counted. */
        val read: Boolean,
        /** Letters before it in the chapter. */
        val lettersBefore: Int,
        /** The kepub sentence it's in ("kobo.12.1"), if any, and where in that sentence's text it starts. */
        val span: String?,
        val spanStart: Int,
        /** Where it is among the nodes from the html element down: Adobe's way (each 1 for the first node). */
        val nodePath: IntArray,
    )

    /** A stretch of the chapter: from [startOffset] in piece [start] to just before [endOffset] in piece [end]. */
    data class Stretch(val start: Int, val startOffset: Int, val end: Int, val endOffset: Int)

    private val root: XElement
    val pieces = ArrayList<Piece>()

    /** Every letter in the chapter (as [ChapterLetters] counts them). */
    val total: Int

    init {
        root = Xml.parse(xhtml).root
        val body = root.elements().firstOrNull { it.localName.equals("body", true) }
        var letters = 0
        val spanSeen = HashMap<String, Int>()
        fun walk(e: XElement, read: Boolean, span: String?, path: IntArray) {
            for ((i, c) in e.children.withIndex()) when (c) {
                is XElement -> {
                    val id = c.attributes["id"]
                    val inSpan = if (id != null && c.localName.equals("span", true) && SPAN.matches(id)) id else span
                    walk(c, read && c.localName.lowercase() !in ChapterLetters.SKIPPED, inSpan, path + (i + 1))
                }
                is XText -> if (!c.isCData) {
                    val t = c.text
                    val start = span?.let { spanSeen[it] ?: 0 } ?: 0
                    pieces += Piece(c, t, read, letters, span, start, path + (i + 1))
                    if (span != null) spanSeen[span] = start + t.length
                    if (read) letters += t.count { ChapterLetters.counts(it) }
                }
                else -> Unit
            }
        }
        if (body != null) {
            val bodyAt = root.children.indexOf(body)
            walk(body, true, null, intArrayOf(1, bodyAt + 1))
        }
        total = letters
    }

    // ---- letters (KoLib's "chars=N" places) ----------------------------------------------------------

    /** How many letters come before [offset] in piece [piece]. */
    fun lettersAt(piece: Int, offset: Int): Int {
        val p = pieces.getOrNull(piece) ?: return total
        if (!p.read) return p.lettersBefore
        val end = offset.coerceIn(0, p.text.length)
        var n = 0
        for (k in 0 until end) if (ChapterLetters.counts(p.text[k])) n++
        return p.lettersBefore + n
    }

    /** Where the [n]th letter is (from 0): its piece and character. The last letter if there are fewer. */
    fun atLetters(n: Int): Pair<Int, Int>? {
        var last: Pair<Int, Int>? = null
        for ((i, p) in pieces.withIndex()) {
            if (!p.read) continue
            var k = p.lettersBefore
            for (j in p.text.indices) {
                if (!ChapterLetters.counts(p.text[j])) continue
                if (k == n) return i to j
                k++
                last = i to j
            }
        }
        return last
    }

    /** About a line of the text from [offset] in piece [piece] on, for showing where a place is. */
    fun snippetAt(piece: Int, offset: Int): String {
        val out = StringBuilder()
        for (i in piece until pieces.size) {
            val p = pieces[i]
            if (!p.read) continue
            out.append(if (i == piece) p.text.substring(offset.coerceIn(0, p.text.length)) else p.text)
            if (out.length > 200) break
        }
        return out.toString().replace(Regex("\\s+"), " ").trim().take(140)
    }

    // ---- kepub sentences ---------------------------------------------------------------------------------

    /** The sentence a place is in, and the character in its text: the next sentence if the place is between them. */
    fun spanAt(piece: Int, offset: Int): Pair<String, Int>? {
        pieces.getOrNull(piece)?.let { p -> if (p.span != null) return p.span to (p.spanStart + offset.coerceIn(0, p.text.length)) }
        for (i in piece + 1 until pieces.size) pieces[i].span?.let { return it to pieces[i].spanStart }
        for (i in (0 until minOf(piece, pieces.size)).reversed()) {
            val p = pieces[i]
            if (p.span != null) return p.span to (p.spanStart + p.text.length)
        }
        return null
    }

    /** Where a sentence's [offset] character is. */
    fun atSpan(span: String, offset: Int): Pair<Int, Int>? {
        var last: Pair<Int, Int>? = null
        for ((i, p) in pieces.withIndex()) {
            if (p.span != span) continue
            if (offset <= p.spanStart + p.text.length) return i to (offset - p.spanStart).coerceIn(0, p.text.length)
            last = i to p.text.length
        }
        return last
    }

    // ---- Adobe's points (an EPUB on the Kobo) ------------------------------------------------------------

    /**
     * Adobe's point for a place, as the Kobo's EPUB reader writes it: "/1/4/2/1:6". Each step counts the nodes in
     * the one before (elements, text, everything; from 1), the first being the html element; the number after the
     * colon is how many bytes of the text (in UTF-8) come before the place.
     */
    fun adobePoint(piece: Int, offset: Int): String? {
        val p = pieces.getOrNull(piece) ?: return null
        val bytes = p.text.substring(0, offset.coerceIn(0, p.text.length)).toByteArray(Charsets.UTF_8).size
        return p.nodePath.joinToString("/", prefix = "/") + ":" + bytes
    }

    /** Where Adobe's [point] is, by its own counting (see [adobePoint]); else read as an EPUB CFI is. */
    fun atAdobePoint(point: String): Pair<Int, Int>? = byNodes(point) ?: byCfi(point)

    private fun byNodes(point: String): Pair<Int, Int>? {
        val steps = steps(point) ?: return null
        if (steps.firstOrNull() != 1) return null
        val bytes = point.substringAfter(':', "0").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        var at: XNode = root
        for (s in steps.drop(1)) {
            val e = at as? XElement ?: return null
            at = e.children.getOrNull(s - 1) ?: return null
        }
        return when (val n = at) {
            is XText -> pieces.indexOfFirst { it.node === n }.takeIf { it >= 0 }?.let { i -> i to charsIn(pieces[i].text, bytes) }
            is XElement -> firstPieceIn(n)?.let { it to 0 }
            else -> null
        }
    }

    /** An EPUB CFI's way: an even step is an element (2 for the first), an odd one the text between them. */
    private fun byCfi(point: String): Pair<Int, Int>? {
        val steps = steps(point) ?: return null
        val offset = point.substringAfter(':', "0").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        var at: XElement = root
        for ((i, s) in steps.withIndex()) {
            if (i == 0) continue
            if (s % 2 == 0) {
                at = at.elements().getOrNull(s / 2 - 1) ?: return null
                continue
            }
            if (i != steps.lastIndex) return null
            val after = (s - 1) / 2
            var seen = 0
            for (c in at.children) {
                if (c is XElement) {
                    seen++
                    if (seen > after) break
                } else if (c is XText && seen == after && !c.isCData) {
                    val k = pieces.indexOfFirst { it.node === c }
                    if (k >= 0) return k to offset.coerceIn(0, pieces[k].text.length)
                }
            }
            return null
        }
        return firstPieceIn(at)?.let { it to 0 }
    }

    private fun steps(point: String): List<Int>? {
        val path = point.substringBefore(':').replace(Regex("""\[[^\]]*]"""), "")
        val steps = path.split('/').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        return steps.takeIf { it.isNotEmpty() }
    }

    private fun firstPieceIn(e: XElement): Int? {
        fun has(x: XElement, n: XText): Boolean = x.children.any { it === n || (it is XElement && has(it, n)) }
        return pieces.indices.firstOrNull { has(e, pieces[it].node) }
    }

    // ---- words, as KoLib's reader finds a highlight again ---------------------------------------------------

    private class Flat(val text: String, val piece: IntArray, val offset: IntArray)

    /** The text as reader.js's flatText() has it: runs of space as one, lower case, and where each character was. */
    private val flat: Flat by lazy {
        val sb = StringBuilder()
        val piece = ArrayList<Int>()
        val offset = ArrayList<Int>()
        for ((i, p) in pieces.withIndex()) {
            for ((j, c0) in p.text.withIndex()) {
                var c = c0
                if (isJsSpace(c)) {
                    if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') continue
                    c = ' '
                }
                sb.append(c.lowercaseChar())
                piece += i
                offset += j
            }
        }
        Flat(sb.toString(), piece.toIntArray(), offset.toIntArray())
    }

    /** Every time [words] occur, spacing and case aside, in order: as reader.js's findText() counts them. */
    fun findAll(words: String): List<Stretch> {
        val wanted = norm(words).lowercase()
        if (wanted.isEmpty()) return emptyList()
        val f = flat
        val out = ArrayList<Stretch>()
        var at = f.text.indexOf(wanted)
        while (at >= 0) {
            val last = at + wanted.length - 1
            out += Stretch(f.piece[at], f.offset[at], f.piece[last], f.offset[last] + 1)
            at = f.text.indexOf(wanted, at + 1)
        }
        return out
    }

    /** The [occurrence]th time (from 0) [words] occur: where reader.js finds a highlight by its words. */
    fun find(words: String, occurrence: Int): Stretch? = findAll(words).getOrNull(occurrence)

    /** How many times [words] occur before a place: the number reader.js keeps with a highlight. */
    fun occurrenceBefore(words: String, piece: Int, offset: Int): Int =
        findAll(words).count { it.start < piece || (it.start == piece && it.startOffset < offset) }

    /** Where [words] are nearest a place [near] (a piece and character), case and spacing aside. */
    fun findNear(words: String, near: Pair<Int, Int>?): Stretch? {
        val all = findAll(words)
        if (near == null || all.size < 2) return all.firstOrNull()
        return all.minByOrNull { distance(it, near) }
    }

    private fun distance(s: Stretch, near: Pair<Int, Int>): Long =
        kotlin.math.abs(lettersAt(s.start, s.startOffset).toLong() - lettersAt(near.first, near.second).toLong())

    /** The text of a stretch. */
    fun textOf(s: Stretch): String {
        val out = StringBuilder()
        for (i in s.start..s.end) {
            val t = pieces.getOrNull(i)?.text ?: continue
            val a = if (i == s.start) s.startOffset.coerceIn(0, t.length) else 0
            val b = if (i == s.end) s.endOffset.coerceIn(0, t.length) else t.length
            if (b > a) out.append(t, a, b)
        }
        return out.toString()
    }

    companion object {
        val SPAN = Regex("""^kobo\.\d+\.\d+$""")

        fun norm(t: String): String = buildString {
            for (c in t) {
                if (isJsSpace(c)) {
                    if (isNotEmpty() && this[length - 1] != ' ') append(' ')
                } else {
                    append(c)
                }
            }
        }.trim()

        /** JavaScript's \s. */
        fun isJsSpace(c: Char): Boolean = when (c) {
            ' ', '\t', '\n', '\u000B', '\u000C', '\r', ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿' -> true
            else -> c in ' '..' '
        }

        /** The characters in [text] that [bytes] bytes of it (in UTF-8) make up. */
        fun charsIn(text: String, bytes: Int): Int {
            var b = 0
            var i = 0
            while (i < text.length && b < bytes) {
                val cp = text.codePointAt(i)
                b += when {
                    cp < 0x80 -> 1
                    cp < 0x800 -> 2
                    cp < 0x10000 -> 3
                    else -> 4
                }
                i += Character.charCount(cp)
            }
            return i
        }
    }
}
