package dev.kolib.app.log

/**
 * Keeps the names of people's books out of the log, so it can be shared without them. A book's title, author or
 * file name is written as a short code instead ("‹book 3fa2›"), the same code each time, so one book can still be
 * told from another. Hidden are: every name KoLib knows (the books in the library and on the Kobo, given by
 * [know]), anything quoted in a message (that's how messages name a book), and the name of any book file.
 */
object LogPrivacy {

    /** The names known to hide, by where they come from ("library", "kobo"). */
    private val known = HashMap<String, Collection<String>>()

    /**
     * The names to hide, by the word each starts with (or its first character, when that isn't part of a word), the
     * longest first: a message is looked through a word at a time, so a big library doesn't slow the log.
     */
    @Volatile private var byFirstWord: Map<String, List<String>> = emptyMap()

    /**
     * Names in Chinese, Japanese or Korean script, by their first character, the longest first. Those scripts don't
     * put spaces between words, so these are looked for anywhere, not only where a word starts.
     */
    @Volatile private var byFirstChar: Map<Char, List<String>> = emptyMap()

    /** The names from [source] to hide from now on, in place of those it gave before. */
    @Synchronized
    fun know(source: String, names: Collection<String>) {
        known[source] = names
        // Short names (under four letters) aren't hidden on their own: they'd hide ordinary words too. Nor are the
        // app's own words, should a book be called by one ("KoLib" is the sample comic's author): the log is full of them.
        // (A name in Chinese, Japanese or Korean is often two or three characters: those are hidden too.)
        val all = known.values.asSequence().flatten().map { it.trim() }
            .filter { (it.length >= 4 || (it.length >= 2 && isCjk(it[0]))) && it.lowercase() !in APP_WORDS }.distinct()
            .sortedByDescending { it.length }.take(MAX_NAMES).toList()
        val (cjk, words) = all.partition { isCjk(it[0]) }
        byFirstWord = words.groupBy { tokenAt(it, 0) }
        byFirstChar = cjk.groupBy { it[0] }
    }

    /** [message] with the names of books in it hidden. */
    fun clean(message: String): String = try {
        var m = hideKnown(message)
        m = IN_PATH.replace(m) { r -> "/" + code(r.groupValues[1]) + r.groupValues[2] }
        m = FILE.replace(m) { r -> code(r.groupValues[1]) + r.groupValues[2] }
        m = CURLY.replace(m) { r -> if (keep(r.groupValues[1])) r.value else "“" + code(r.groupValues[1]) + "”" }
        m = SINGLE.replace(m) { r -> if (keep(r.groupValues[2])) r.value else r.groupValues[1] + "'" + code(r.groupValues[2]) + "'" }
        m
    } catch (_: Exception) {
        // Logging must never fail.
        message
    }

    /** The known names in [message], each a whole word or words (not part of a longer word), hidden. */
    private fun hideKnown(message: String): String {
        val index = byFirstWord
        val chars = byFirstChar
        if (index.isEmpty() && chars.isEmpty()) return message
        var out: StringBuilder? = null
        var i = 0
        var copied = 0
        while (i < message.length) {
            val hit = chars[message[i]]?.firstOrNull { n -> message.startsWith(n, i) }
                ?: if (i == 0 || !isWord(message[i - 1])) index[tokenAt(message, i)]?.firstOrNull { n ->
                    message.startsWith(n, i) && (i + n.length == message.length || !isWord(message[i + n.length]))
                } else null
            if (hit == null) {
                i++
                continue
            }
            val b = out ?: StringBuilder(message.length).also { out = it }
            b.append(message, copied, i).append(code(hit))
            i += hit.length
            copied = i
        }
        return out?.append(message, copied, message.length)?.toString() ?: message
    }

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
        else -> false
    }

    /** The word that starts at [i] in [s], or the one character there when it isn't part of a word. */
    private fun tokenAt(s: String, i: Int): String {
        if (i >= s.length) return ""
        if (!isWord(s[i])) return s[i].toString()
        var j = i
        while (j < s.length && isWord(s[j])) j++
        return s.substring(i, j)
    }

    /** Quoted text left as it is: already hidden, or a name of the app's own (a thread, a setting) rather than a book's. */
    private fun keep(quoted: String) = quoted.startsWith("‹") || TECHNICAL.matches(quoted)

    private val TECHNICAL = Regex("[a-z0-9_.:\\-]+")

    /** The code a name is written as: the same for the same name, whatever its case. */
    fun code(name: String): String = "‹book " + Integer.toHexString(name.trim().lowercase().hashCode() and 0xffff).padStart(4, '0') + "›"

    private const val MAX_NAMES = 20_000

    private val APP_WORDS = setOf(
        "kolib", "kobo", "library", "reader", "book", "books", "comic", "comics", "unknown", "untitled", "sync", "backup",
        "backups", "settings", "android", "epub", "kepub", "chapter", "error", "none", "null", "true", "false", "sample books",
    )

    private const val EXTENSIONS = "(\\.kepub\\.epub|\\.epub|\\.pdf|\\.mobi|\\.azw3|\\.azw|\\.cbz|\\.cbr|\\.cb7|\\.fb2|\\.docx|\\.doc|\\.rtf|\\.txt|\\.html?)"

    /** A book file's name in a path: whatever follows the last slash. */
    private val IN_PATH = Regex("/([^/\\n‹›]+?)$EXTENSIONS(?![\\w])", RegexOption.IGNORE_CASE)

    /** A book file's name on its own: a run without spaces. */
    private val FILE = Regex("(?<![/\\w‹›.])([\\w\\-]+)$EXTENSIONS(?![\\w])", RegexOption.IGNORE_CASE)

    /** “Quoted” names. */
    private val CURLY = Regex("“([^”\\n]{1,300})”")

    /** 'Quoted' names: the quote marks at the ends of words, so an apostrophe inside one ("Kobo's") isn't taken for them. */
    private val SINGLE = Regex("(^|[\\s(\\[\"])'([^\\n]{1,300}?)'(?=$|[\\s).,:;!?\\]\"])")
}
