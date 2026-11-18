package dev.kolib.app.ai

import dev.kolib.app.epub.EpubText

/** A book's words as a model is given them: plain paragraphs, in pieces that fit what it can hold. */
object BookText {
    private val head = Regex("(?is)<(script|style|head)\\b.*?</\\1\\s*>")
    private val breaks = Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article)\\s*>|<br\\s*/?>|<hr\\s*/?>")

    /** The paragraphs of one chapter file, in order, each on one line. */
    fun paragraphs(xhtml: String): List<String> =
        EpubText.plainText(xhtml.replace(head, " ").replace(Regex("\\s+"), " ").replace(breaks, "\n"))
            .split('\n')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }

    /**
     * [paragraphs] up to [fraction] of the way through (by length), ending at the end of a paragraph: what's
     * been read of a chapter the reader is part-way through.
     */
    fun upTo(paragraphs: List<String>, fraction: Float): List<String> {
        if (fraction >= 0.995f) return paragraphs
        val total = paragraphs.sumOf { it.length }
        val want = (total * fraction.coerceIn(0f, 1f)).toLong()
        var have = 0L
        val out = ArrayList<String>()
        for (p in paragraphs) {
            if (have + p.length / 2 > want) break
            out += p
            have += p.length
        }
        return out
    }

    private fun invisible(c: Char) = c == '\u00ad' || c == '\u200b' || c == '\u200c' || c == '\u200d' || c == '\ufeff'

    /** [s] as the page shows it, without its invisible characters, its spaces one at a time; and where each of its characters was in [s]. */
    private fun flatWithPlaces(s: String): Pair<String, IntArray> {
        val out = StringBuilder()
        val places = ArrayList<Int>()
        var space = false
        for ((i, c) in s.withIndex()) {
            if (invisible(c)) continue
            if (c.isWhitespace()) {
                space = out.isNotEmpty()
                continue
            }
            if (space) {
                out.append(' ')
                places += i - 1
                space = false
            }
            out.append(c)
            places += i
        }
        return out.toString() to places.toIntArray()
    }

    private fun flat(s: String) = flatWithPlaces(s).first

    /**
     * [paragraphs] up to the end of the text on the reader's screen ([screen], a line to a paragraph), or up to its
     * start ([atEnd] false). It's found by the words at that end of it; where they come more than once, the place
     * nearest [near] of the way through (by length) is taken. Null when they aren't found.
     */
    fun upToScreen(paragraphs: List<String>, screen: String, near: Float, atEnd: Boolean = true): List<String>? {
        val lines = screen.lines().map { flat(it) }.filter { it.length >= 2 }
        if (lines.isEmpty() || paragraphs.isEmpty()) return null
        val flatsWithPlaces = paragraphs.map { flatWithPlaces(it) }
        val flats = flatsWithPlaces.map { it.first }
        val total = paragraphs.sumOf { it.length }.coerceAtLeast(1)
        // The line at that end of the screen, by its last (or first) words; fewer of them, or the next line in,
        // where those aren't found as they are (a note's number on the page that isn't in the text, say).
        val tries = (if (atEnd) lines.asReversed() else lines).take(2).flatMap { l -> listOf(48, 24).map { n -> if (atEnd) l.takeLast(n) else l.take(n) } }
        for ((attempt, key) in tries.withIndex()) {
            if (key.length < 8) continue
            var best = -1
            var bestAt = 0
            var bestDistance = Double.MAX_VALUE
            var before = 0
            for ((i, f) in flats.withIndex()) {
                var from = 0
                while (true) {
                    val k = f.indexOf(key, from)
                    if (k < 0) break
                    val pos = before + k.toDouble() * paragraphs[i].length / f.length.coerceAtLeast(1)
                    val d = kotlin.math.abs(pos - near * total)
                    if (d < bestDistance) {
                        best = i
                        bestAt = k
                        bestDistance = d
                    }
                    from = k + 1
                }
                before += paragraphs[i].length
            }
            if (best < 0) continue
            // Found by the line before the last (or after the first): the whole of that paragraph is on the screen.
            val whole = attempt >= 2
            val (f, places) = flatsWithPlaces[best]
            val p = paragraphs[best]
            val cutFlat = when {
                whole && atEnd -> f.length
                whole -> 0
                atEnd -> bestAt + key.length
                else -> bestAt
            }
            // Back to where that is in the paragraph as written.
            var cut = when {
                f.isEmpty() -> 0
                cutFlat >= f.length -> p.length
                atEnd -> places[cutFlat - 1] + 1
                else -> places[cutFlat]
            }.coerceIn(0, p.length)
            // To the end (or start) of a word.
            if (atEnd) while (cut < p.length && !p[cut].isWhitespace()) cut++ else while (cut > 0 && !p[cut - 1].isWhitespace()) cut--
            val part = p.substring(0, cut).trim()
            return paragraphs.take(best) + listOfNotNull(part.takeIf { it.isNotEmpty() })
        }
        return null
    }

    /**
     * [paragraphs] in runs of at most [mostTokens] tokens each, counted by [count]; a paragraph longer than that
     * by itself is cut at sentences.
     */
    fun chunks(paragraphs: List<String>, mostTokens: Int, count: (String) -> Int): List<String> {
        val out = ArrayList<String>()
        val now = StringBuilder()
        var held = 0
        fun flush() {
            if (now.isNotEmpty()) out += now.toString().trim()
            now.setLength(0)
            held = 0
        }
        for (p in paragraphs.flatMap { if (count(it) > mostTokens) sentences(it, mostTokens, count) else listOf(it) }) {
            val n = count(p) + 1
            if (held + n > mostTokens) flush()
            now.append(p).append('\n')
            held += n
        }
        flush()
        return out
    }

    private fun sentences(text: String, mostTokens: Int, count: (String) -> Int): List<String> {
        val out = ArrayList<String>()
        val now = StringBuilder()
        for (s in text.split(Regex("(?<=[.!?…。！？][\"”’)]?)\\s+"))) {
            if (now.isNotEmpty() && count(now.toString() + " " + s) > mostTokens) {
                out += now.toString()
                now.setLength(0)
            }
            if (now.isNotEmpty()) now.append(' ')
            // A sentence too long by itself (or text with no sentences): cut where it has to be.
            if (count(s) > mostTokens) {
                val step = (s.length * mostTokens / count(s).coerceAtLeast(1)).coerceAtLeast(40)
                s.chunked(step).forEach { out += it }
            } else now.append(s)
        }
        if (now.isNotEmpty()) out += now.toString()
        return out
    }

    /**
     * The sentence of [paragraph] that [word] is in (the first one, if it's in several), with the sentence
     * before it when it's a short one that says little by itself. For showing a model how a word is used.
     */
    fun sentenceWith(paragraph: String, word: String): String {
        val p = paragraph.replace(Regex("\\s+"), " ").trim()
        val at = p.indexOf(word.trim()).takeIf { it >= 0 } ?: return p.take(MOST_SENTENCE)
        val ends = Regex("[.!?…。！？][\"”’)]?\\s").findAll(p).map { it.range.last + 1 }.toList()
        var start = ends.lastOrNull { it <= at } ?: 0
        val end = ends.firstOrNull { it > at + word.trim().length } ?: p.length
        if (end - start < 40 && start > 0) start = ends.lastOrNull { it < start } ?: 0
        return p.substring(start, end).trim().take(MOST_SENTENCE)
    }

    private const val MOST_SENTENCE = 600

    private val wordRe = Regex("[\\p{L}\\p{N}]+")
    private val common = setOf(
        "the", "and", "for", "that", "this", "with", "from", "have", "has", "had", "was", "were", "are", "is", "what", "who", "whom", "why", "how", "when",
        "where", "which", "does", "did", "do", "about", "into", "they", "them", "their", "his", "her", "him", "she", "he", "you", "your", "not", "but",
        "can", "could", "would", "should", "tell", "me", "of", "in", "on", "at", "to", "a", "an", "it", "its", "as", "be", "been", "so", "or", "if", "by",
        "happened", "happen", "happens", "book", "chapter", "story", "far", "again", "explain", "mean", "means",
    )

    private val frontRe = Regex(
        "(?i)^\\W*(cover|title(\\s*page)?|half[- ]?title|copyright( page)?|dedication|epigraph|(table of )?contents|foreword|introduction|preface|prologue to the .* edition|" +
            "acknowledge?ments?|about the (author|publisher|book|illustrator)s?|also by.*|books by.*|praise( for.*)?|maps?|list of (maps|illustrations)|illustrations|" +
            "a note (on|from|about).*|author'?s?’?s? note|publisher'?s?’?s? note|imprint|frontispiece)\\W*$"
    )

    /**
     * Whether a part of a book called [name] is what's put round the book rather than the book itself: its
     * title page, the copyright, an introduction or foreword, the acknowledgements.
     */
    fun frontMatter(name: String): Boolean = frontRe.matches(name.trim())

    /** The words of a question worth looking for in the book: its names and its uncommon words. */
    fun keywords(question: String): List<String> =
        wordRe.findAll(question).map { it.value }.filter { it.length > 1 && it.lowercase() !in common }.distinctBy { it.lowercase() }.toList()

    /** A passage of the book: which chapter (by its place in the book) and paragraph it is, and its words. */
    data class Passage(val chapter: Int, val paragraph: Int, val text: String)

    /**
     * The passages of [chapters] (each a list of paragraphs, in the book's order) that best answer [question]:
     * the ones its words are in, names counting most, with the paragraphs either side of each; the first
     * place each word comes up (where the book introduces it) and the latest ones (where things stand now).
     * At most [mostTokens] tokens of them, in the book's order.
     */
    fun passages(chapters: List<Pair<Int, List<String>>>, question: String, mostTokens: Int, count: (String) -> Int): List<Passage> {
        val keys = keywords(question)
        if (keys.isEmpty()) return emptyList()
        data class Hit(val p: Passage, var score: Double)
        val hits = ArrayList<Hit>()
        val firstSeen = HashSet<String>()
        for ((ci, paragraphs) in chapters) {
            paragraphs.forEachIndexed { pi, text ->
                var score = 0.0
                for (k in keys) {
                    val name = k[0].isUpperCase()
                    val found = if (name) text.contains(k) else text.contains(k, ignoreCase = true)
                    if (!found) continue
                    score += if (name) 3.0 else 1.0
                    // Where the book first brings it up: that's where it says who or what it is.
                    if (firstSeen.add(k.lowercase())) score += 4.0
                }
                if (score > 0) hits += Hit(Passage(ci, pi, text), score)
            }
        }
        if (hits.isEmpty()) return emptyList()
        // Later in the book counts a little more: where things stand now.
        hits.forEachIndexed { i, h -> h.score += 1.5 * i / hits.size }
        val chosen = LinkedHashMap<Pair<Int, Int>, Passage>()
        var held = 0
        val byChapter = chapters.toMap()
        for (h in hits.sortedByDescending { it.score }) {
            val n = count(h.p.text) + 2
            if (held + n > mostTokens) continue
            if (chosen.put(h.p.chapter to h.p.paragraph, h.p) == null) held += n
            // The lines on either side, if there's room: who's speaking is often in the one before, the answer in the one after.
            for (near in intArrayOf(h.p.paragraph - 1, h.p.paragraph + 1)) {
                val text = byChapter[h.p.chapter]?.getOrNull(near) ?: continue
                if ((h.p.chapter to near) in chosen) continue
                val m = count(text) + 2
                if (held + m <= mostTokens) {
                    chosen[h.p.chapter to near] = Passage(h.p.chapter, near, text)
                    held += m
                }
            }
        }
        return chosen.values.sortedWith(compareBy({ it.chapter }, { it.paragraph }))
    }
}
