package dev.kolib.app.epub

/** The details of a book that the app reads from, and writes into, the EPUB itself. */
data class BookMetadata(
    val title: String = "",
    /** How the title sorts ("Histories, The"); null means work it out from the title. */
    val titleSort: String? = null,
    val authors: List<String> = emptyList(),
    /** Sort names the book already had for particular authors ("Tolkien, J. R. R."). */
    val authorSorts: Map<String, String> = emptyMap(),
    val series: String? = null,
    val seriesIndex: Double? = null,
    val tags: List<String> = emptyList(),
    val publisher: String? = null,
    /** "2005", "2005-07" or "2005-07-17". */
    val published: String? = null,
    /** Language code such as "en". */
    val language: String? = null,
    val isbn: String? = null,
    /** Simple HTML (paragraphs, bold, italic). */
    val description: String? = null,
    /** 0–10, as Calibre stores it (two per star). */
    val rating: Int? = null,
    /** The EPUB's own unique identifier; read-only. */
    val identifier: String? = null,
) {
    val effectiveTitleSort: String get() = titleSort?.takeIf { it.isNotBlank() } ?: Names.titleSort(title)

    fun sortNameFor(author: String): String = authorSorts[author] ?: Names.authorSort(author)

    /** "Sanderson, Brandon & Herbert, Frank" — for sorting by author. */
    val authorSort: String get() = authors.joinToString(" & ") { sortNameFor(it) }

    val seriesLabel: String?
        get() {
            val s = series?.takeIf { it.isNotBlank() } ?: return null
            return seriesIndex?.let { "$s #${Names.formatIndex(it)}" } ?: s
        }
}

object Names {
    private val articles = listOf("the ", "a ", "an ")
    private val suffixes = setOf("jr", "jr.", "sr", "sr.", "junior", "senior", "i", "ii", "iii", "iv", "v", "phd", "ph.d", "ph.d.", "md", "m.d.")
    private val honorifics = setOf("mr", "mr.", "mrs", "mrs.", "ms", "ms.", "dr", "dr.", "prof", "prof.")

    fun titleSort(title: String): String {
        val t = title.trim()
        val lower = t.lowercase()
        for (a in articles) {
            if (lower.startsWith(a) && t.length > a.length) {
                return t.substring(a.length).trim() + ", " + t.substring(0, a.length - 1)
            }
        }
        return t
    }

    /** "Arthur Conan Doyle" → "Doyle, Arthur Conan"; "Martin Luther King Jr." → "King, Martin Luther, Jr." */
    fun authorSort(name: String): String {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty() || n.contains(',') || n.equals("unknown", ignoreCase = true)) return n
        val words = n.split(' ').toMutableList()
        while (words.size > 1 && words.first().lowercase() in honorifics) words.removeAt(0)
        val trailing = mutableListOf<String>()
        while (words.size > 1 && words.last().lowercase().trimEnd(',') in suffixes) trailing.add(0, words.removeAt(words.size - 1))
        if (words.size == 1) return (listOf(words[0]) + trailing).joinToString(", ")
        val last = words.removeAt(words.size - 1)
        val sb = StringBuilder(last).append(", ").append(words.joinToString(" "))
        if (trailing.isNotEmpty()) sb.append(", ").append(trailing.joinToString(" "))
        return sb.toString()
    }

    fun formatIndex(d: Double): String = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    fun normalizeSpace(s: String): String = s.trim().replace(Regex("\\s+"), " ")
}

object Isbn {
    /** Digits (and a final X for ISBN-10) only. */
    fun normalize(raw: String): String {
        val s = raw.trim().removePrefix("urn:isbn:").removePrefix("URN:ISBN:").removePrefix("isbn:").removePrefix("ISBN:")
            .removePrefix("ISBN").trim()
        return s.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
    }

    fun isValid(raw: String): Boolean {
        val s = normalize(raw)
        return when (s.length) {
            10 -> {
                if (!s.substring(0, 9).all { it.isDigit() }) return false
                var sum = 0
                for (i in 0 until 9) sum += (10 - i) * (s[i] - '0')
                val check = if (s[9] == 'X') 10 else s[9].digitToIntOrNull() ?: return false
                (sum + check) % 11 == 0
            }
            13 -> {
                if (!s.all { it.isDigit() }) return false
                var sum = 0
                for (i in 0 until 12) sum += (s[i] - '0') * (if (i % 2 == 0) 1 else 3)
                (10 - sum % 10) % 10 == s[12] - '0'
            }
            else -> false
        }
    }
}
