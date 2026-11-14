package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.library.LibraryBook
import java.text.Normalizer

/**
 * The library's search language, a friendlier cousin of Calibre's:
 *
 *  - plain words search titles, authors, series, tags, collections and publishers;
 *  - `field:word` searches one field (title, author, tag, series, collection, publisher, language, isbn);
 *  - `field:=word` must match the whole value (`tag:=history` but not "Art History");
 *  - quotes keep words together (`author:"conan doyle"`);
 *  - `and` (or just a space), `or`, `not` or a leading `-`, and brackets combine terms;
 *  - numbers compare: `pages:>300`, `rating:>=4` (stars), `year:<1900`;
 *  - `kobo:yes`, `kobo:no` and `kobo:changed` (edited since it went to the Kobo);
 *  - `read:unread`, `read:reading` and `read:finished`, from the Kobo's reading progress;
 *  - `library:yes` / `library:no` (for the Kobo's books) and `format:kepub`.
 */
/** What the search language looks at, for a book in the library or one on the Kobo. */
interface SearchFacts {
    val title: String
    val authors: List<String>
    val series: String?
    val tags: List<String>
    val collections: List<String>
    val publisher: String?
    val language: String?
    val isbn: String?
    /** Stars, 0 to 5 (halves allowed); 0 when it has no rating. */
    val stars: Double
    val pages: Int?
    val year: Int?
    val onKobo: Boolean
    val changedSinceOnKobo: Boolean
    /** How far it's been read on the Kobo, if the Kobo has it. */
    val readStatus: ReadStatus?
    val inLibrary: Boolean
    val format: String?
}

/** A library book (with the Kobo's copy, for its reading progress) as search facts. */
class LibraryFacts(val book: LibraryBook, private val onKoboAs: KoboBook?) : SearchFacts {
    override val title get() = book.meta.title
    override val authors get() = book.meta.authors
    override val series get() = book.meta.series
    override val tags get() = book.meta.tags
    override val collections get() = book.collections
    override val publisher get() = book.meta.publisher
    override val language get() = book.meta.language
    override val isbn get() = book.meta.isbn
    override val stars get() = (book.meta.rating ?: 0) / 2.0
    override val pages get() = book.pages
    override val year get() = book.meta.published?.take(4)?.toIntOrNull()
    override val onKobo get() = book.koboPath != null
    override val changedSinceOnKobo get() = book.changedSinceOnKobo
    override val readStatus get() = onKoboAs?.readStatus
    override val inLibrary get() = true
    /** What the book was added as ("PDF", "MOBI"...), or EPUB. */
    override val format get() = when (book.sourceFormat) {
        null -> "EPUB"
        dev.kobolibrary.app.convert.BookFormats.PDF_SCAN, dev.kobolibrary.app.convert.BookFormats.PDF_ONLY -> "PDF"
        else -> book.sourceFormat
    }
}

/** [reading] gives the book as the Kobo has it (for its reading progress), if it's there. */
fun LibraryQuery.Node.matches(b: LibraryBook, reading: (LibraryBook) -> KoboBook? = { null }): Boolean = matches(LibraryFacts(b, reading(b)))

object LibraryQuery {

    enum class Field(vararg val names: String) {
        TITLE("title"),
        AUTHOR("author", "authors", "by"),
        TAG("tag", "tags"),
        SERIES("series"),
        COLLECTION("collection", "collections", "shelf"),
        PUBLISHER("publisher"),
        LANGUAGE("language", "lang"),
        ISBN("isbn"),
        RATING("rating", "stars"),
        PAGES("pages"),
        YEAR("year", "published"),
        KOBO("kobo"),
        READ("read", "status"),
        LIBRARY("library", "lib"),
        FORMAT("format", "type"),
        ;

        companion object {
            fun of(name: String): Field? = entries.firstOrNull { name.lowercase() in it.names }
        }
    }

    sealed interface Node {
        fun matches(f: SearchFacts): Boolean
    }

    data class Term(val field: Field?, val value: String, val exact: Boolean) : Node {
        private val wanted = fold(value)

        override fun matches(f: SearchFacts): Boolean {
            if (field != null) numeric(f)?.let { return it }
            fun hit(s: String?) = s != null && if (exact) fold(s) == wanted else fold(s).contains(wanted)
            fun any(list: List<String>) = list.any { hit(it) }
            return when (field) {
                null -> hit(f.title) || any(f.authors) || hit(f.series) || any(f.tags) || any(f.collections) || hit(f.publisher)
                Field.TITLE -> hit(f.title)
                Field.AUTHOR -> any(f.authors)
                Field.TAG -> any(f.tags)
                Field.SERIES -> hit(f.series)
                Field.COLLECTION -> any(f.collections)
                Field.PUBLISHER -> hit(f.publisher)
                Field.LANGUAGE -> hit(f.language) || f.language?.let { hit(dev.kobolibrary.app.ui.languageName(it)) } == true
                Field.ISBN -> f.isbn?.contains(value.filter { it.isDigit() || it == 'X' || it == 'x' }, ignoreCase = true) == true
                Field.KOBO -> when (wanted) {
                    "yes", "on", "true" -> f.onKobo
                    "no", "off", "false" -> !f.onKobo
                    "changed", "edited", "outdated", "update" -> f.changedSinceOnKobo
                    else -> false
                }
                Field.LIBRARY -> when (wanted) {
                    "yes", "on", "true", "in" -> f.inLibrary
                    "no", "off", "false", "out", "not" -> !f.inLibrary
                    else -> false
                }
                Field.FORMAT -> hit(f.format)
                Field.READ -> {
                    val status = f.readStatus
                    when (wanted) {
                        "unread", "no", "not", "new", "none" -> status == ReadStatus.UNREAD
                        "reading", "started", "current", "yes" -> status == ReadStatus.READING
                        "finished", "read", "done", "complete" -> status == ReadStatus.FINISHED
                        else -> false
                    }
                }
                Field.RATING, Field.PAGES, Field.YEAR -> false
            }
        }

        /** Number comparisons for rating, pages and year; null if this term isn't one. */
        private fun numeric(f: SearchFacts): Boolean? {
            if (field != Field.RATING && field != Field.PAGES && field != Field.YEAR) return null
            val m = Regex("""^(>=|<=|>|<|=)?\s*(\d+(?:\.\d+)?)$""").matchEntire(value.trim())
            if (field == Field.RATING && (wanted == "none" || wanted == "no")) return f.stars == 0.0
            m ?: return false
            val op = m.groupValues[1].ifEmpty { "=" }
            val n = m.groupValues[2].toDouble()
            val actual: Double = when (field) {
                Field.RATING -> f.stars
                Field.PAGES -> f.pages?.toDouble() ?: return false
                else -> f.year?.toDouble() ?: return false
            }
            return when (op) {
                ">" -> actual > n
                "<" -> actual < n
                ">=" -> actual >= n
                "<=" -> actual <= n
                else -> if (field == Field.RATING) actual >= n && actual < n + 1 else actual == n
            }
        }
    }

    data class And(val a: Node, val b: Node) : Node {
        override fun matches(f: SearchFacts) = a.matches(f) && b.matches(f)
    }

    data class Or(val a: Node, val b: Node) : Node {
        override fun matches(f: SearchFacts) = a.matches(f) || b.matches(f)
    }

    data class Not(val a: Node) : Node {
        override fun matches(f: SearchFacts) = !a.matches(f)
    }

    /** Lower case, without accents, so "Bronte" finds "Brontë". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    // ---- parsing --------------------------------------------------------------------------

    private sealed interface Tok {
        data class Word(val field: String?, val text: String, val exact: Boolean) : Tok
        data object AndT : Tok
        data object OrT : Tok
        data object NotT : Tok
        data object Open : Tok
        data object Close : Tok
    }

    /** The query as a tree, or null when it has no terms (everything matches). Never throws. */
    fun parse(query: String): Node? {
        val toks = tokenize(query)
        if (toks.isEmpty()) return null
        val parser = Parser(toks)
        var node = parser.expression()
        // A stray ")" ends an expression early; whatever follows it still counts.
        while (parser.pos < toks.size) {
            parser.pos++
            val more = parser.expression() ?: continue
            node = if (node == null) more else And(node, more)
        }
        return node
    }

    private val fieldPrefix = Regex("^([A-Za-z]+):")

    private fun tokenize(q: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < q.length) {
            val c = q[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out += Tok.Open; i++ }
                c == ')' -> { out += Tok.Close; i++ }
                c == '-' -> {
                    // "-tag:greece" leaves those out; a lone dash means nothing.
                    if (i + 1 < q.length && !q[i + 1].isWhitespace()) out += Tok.NotT
                    i++
                }
                else -> {
                    var field: String? = null
                    fieldPrefix.find(q.substring(i))?.let { m ->
                        if (Field.of(m.groupValues[1]) != null) {
                            field = m.groupValues[1]
                            i += m.value.length
                        }
                    }
                    var exact = false
                    if (i < q.length && q[i] == '=') {
                        exact = true
                        i++
                    }
                    val sb = StringBuilder()
                    var quoted = false
                    if (i < q.length && q[i] == '"') {
                        quoted = true
                        i++
                        while (i < q.length && q[i] != '"') sb.append(q[i++])
                        if (i < q.length) i++
                    } else {
                        while (i < q.length && !q[i].isWhitespace() && q[i] != '(' && q[i] != ')') sb.append(q[i++])
                    }
                    val text = sb.toString()
                    if (field == null && !quoted && !exact) {
                        when (text.lowercase()) {
                            "and", "&&" -> { out += Tok.AndT; continue }
                            "or", "||" -> { out += Tok.OrT; continue }
                            "not" -> { out += Tok.NotT; continue }
                        }
                    }
                    if (text.isNotEmpty()) out += Tok.Word(field, text, exact)
                }
            }
        }
        return out
    }

    private class Parser(val toks: List<Tok>) {
        var pos = 0
        fun peek(): Tok? = toks.getOrNull(pos)

        fun expression(): Node? {
            var left = andExpr()
            while (peek() == Tok.OrT) {
                pos++
                val right = andExpr()
                left = when {
                    left == null -> right
                    right == null -> left
                    else -> Or(left, right)
                }
            }
            return left
        }

        fun andExpr(): Node? {
            var left = notExpr()
            while (true) {
                val t = peek() ?: break
                if (t == Tok.OrT || t == Tok.Close) break
                if (t == Tok.AndT) pos++
                val right = notExpr() ?: continue
                left = if (left == null) right else And(left, right)
            }
            return left
        }

        fun notExpr(): Node? {
            if (peek() == Tok.NotT) {
                pos++
                return notExpr()?.let { Not(it) }
            }
            return primary()
        }

        fun primary(): Node? = when (val t = peek()) {
            null -> null
            Tok.Open -> {
                pos++
                val inner = expression()
                if (peek() == Tok.Close) pos++
                inner
            }
            Tok.Close, Tok.AndT, Tok.OrT -> {
                pos++
                null
            }
            Tok.NotT -> notExpr()
            is Tok.Word -> {
                pos++
                Term(t.field?.let { Field.of(it) }, t.text, t.exact)
            }
        }
    }
}
