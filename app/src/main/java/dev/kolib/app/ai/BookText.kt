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
