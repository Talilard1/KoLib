package dev.kolib.app.reader

import dev.kolib.app.epub.Chapter
import dev.kolib.app.epub.TocNode

/** One of the books in a book that holds several: its title, and its chapters (indices in reading order, both ends in). */
data class InnerBook(val title: String, val first: Int, val last: Int)

/**
 * The books inside a book that holds several (an omnibus, a box set), found from its contents. Either each
 * book is an entry with its chapters under it ("The Eye of the World" > "Prologue", "1 An Empty Road"...),
 * or the contents are one list where each book's title comes before its own prologue or first chapter.
 * A novel in parts ("Part One"), or one with a sample of the next book at the end, isn't one.
 */
object Omnibus {

    /** A prologue, or a first chapter: "Prologue: Dragonmount", "Chapter 1", "Chapter One: ...". Not "Chapter 10". */
    private val prologue = Regex("""^\s*prologue\b""", RegexOption.IGNORE_CASE)
    private val chapterOne = Regex("""^\s*chapter\s+(0*1|one|i)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    /** A chapter or what goes with one, rather than a book's title. */
    private val chapterLike = Regex(
        """^\s*(chapter|prologue|epilogue|interlude|part|section|act|appendix|appendices|glossary|afterword|postscript|coda|\d+\b|[ivxlc]+\.?\s)""",
        RegexOption.IGNORE_CASE,
    )

    /** What comes before a book's text: walked back over to find the book's title. */
    private val frontMatter = Regex(
        """^\s*(the\s+)?(maps?\b|map of|dedication|epigraphs?|contents|table of contents|title\b|title page|half[- ]?title|copyright|acknowledg|foreword|preface|introduction|author'?s note|a note\b|note on|praise|also by|other (books|titles|works)|by the same author|frontispiece|cover\b|about this book|the story so far|what has gone before|previously|dramatis personae|cast of characters|characters\b|timeline|chronology)""",
        RegexOption.IGNORE_CASE,
    )

    /** What comes after the last book (the whole collection's, not that book's), or a sample of another book. */
    private val backMatter = Regex(
        """^\s*(about the (author|authors|publisher)|also by|other (books|titles|works)|copyright|newsletter|more from|discover more|sign up|by the same author|(read an )?excerpt|sample|preview|a preview|teaser|sneak peek|bonus)""",
        RegexOption.IGNORE_CASE,
    )

    /** An entry that's only a heading over parts of one book: "Part One", "Section 2", "Book Three" with no title. */
    private val generic = Regex("""^\s*(part|section|act|chapter|phase|stage|episode|interlude|appendix|appendices|glossary|notes?|maps?|contents)\b""", RegexOption.IGNORE_CASE)

    /** "Book One:", "Volume 2 -", "Book III." in front of a book's title. */
    private val numberedBook = Regex(
        """^\s*(book|volume|vol\.?|novel|tome)\s+(\d+|[ivxlc]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen)\b\s*[:.\-–—]?\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** "... by Robert Jordan" at the end of a book's title. */
    private val byAuthor = Regex("""\s+by\s+\p{Lu}[\p{L}.'’\-]*(\s+\p{Lu}[\p{L}.'’\-]*){0,3}\s*$""")

    /** A title that says it's several books: a range of numbers, or "omnibus", "box set", "complete series"... */
    private val collectionWords = Regex(
        """(\d+\s*[-–—]\s*\d+)|\b(omnibus|collection|box(ed)?\s*set|bundle|complete\s+(series|saga|collection|novels|works|trilogy)|trilogy|duology|tetralogy|quartet|quintet|books\s+\d|novels)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A book's title as a heading has it, from its entry in the contents; null if the entry isn't a title. */
    fun bookName(label: String): String? {
        var t = label.trim()
        if (t.isEmpty()) return null
        numberedBook.find(t)?.let { m ->
            t = t.substring(m.range.last + 1).trim()
            if (t.none { it.isLetter() }) return null
        }
        if (generic.containsMatchIn(t) || frontMatter.containsMatchIn(t) || backMatter.containsMatchIn(t)) return null
        t = t.replace(byAuthor, "").trim()
        t = HeadTitles.book(t, null)
        return t.takeIf { it.any { c -> c.isLetter() } }
    }

    /** The books inside the one with [title], [toc] and [chapters]; none if it's a single book. */
    fun books(toc: List<TocNode>, chapters: List<Chapter>, title: String): List<InnerBook> {
        if (toc.isEmpty() || chapters.isEmpty()) return emptyList()
        val indexOf = HashMap<String, Int>()
        chapters.forEachIndexed { i, c -> indexOf.putIfAbsent(c.path, i) }
        val at: (TocNode) -> Int? = { indexOf[it.path] }
        val collection = collectionWords.containsMatchIn(title)
        val end = chapters.size - 1

        // Each book an entry with its chapters under it: at the top of the contents, or under one entry that
        // holds the rest (the collection's own title over its books).
        grouped(toc, at, end, collection)?.let { return it }
        for ((k, top) in toc.withIndex()) {
            if (top.children.size < 2) continue
            val after = (k + 1 until toc.size).firstNotNullOfOrNull { firstIndex(toc[it], at) }
            grouped(top.children, at, (after ?: (end + 1)) - 1, collection)?.let { return it }
        }
        // One list: each book its title, then its prologue or first chapter.
        return flat(toc.flatMap { n -> n.flatten().map { it.first } }, at, end)
    }

    /** Where [node] (or the first of the entries under it) is in the reading order. */
    private fun firstIndex(node: TocNode, at: (TocNode) -> Int?): Int? =
        node.flatten().mapNotNull { at(it.first) }.minOrNull()

    private fun grouped(level: List<TocNode>, at: (TocNode) -> Int?, end: Int, collection: Boolean): List<InnerBook>? {
        val starts = level.map { firstIndex(it, at) }
        val books = ArrayList<InnerBook>()
        var starting = 0
        for ((k, node) in level.withIndex()) {
            val first = starts[k] ?: continue
            val under = node.children.flatMap { c -> c.flatten().map { it.first } }
            if (under.size < 3) continue
            val name = bookName(node.title) ?: continue
            if (books.isNotEmpty() && first <= books.last().last) continue
            val next = (k + 1 until level.size).firstNotNullOfOrNull { j -> starts[j]?.takeIf { it > first } }
            val last = ((next ?: (end + 1)) - 1).coerceAtLeast(first)
            // Each starts over with a prologue or a first chapter, as a book does.
            if (under.take(16).any { prologue.containsMatchIn(it.title) || chapterOne.containsMatchIn(it.title) }) starting++
            books += InnerBook(name, first, last)
        }
        if (books.size < 2) return null
        if (starting < 2 && !collection) return null
        return books
    }

    private fun flat(entries: List<TocNode>, at: (TocNode) -> Int?, end: Int): List<InnerBook> {
        val list = entries.filter { at(it) != null }
        if (list.size < 6) return emptyList()
        // Where each book's text starts: a prologue, or a first chapter that isn't just after its own prologue.
        val starts = ArrayList<Int>()
        var afterPrologue = false
        var since = 0
        for ((k, e) in list.withIndex()) {
            val isPrologue = prologue.containsMatchIn(e.title)
            val isOne = chapterOne.containsMatchIn(e.title)
            if ((isPrologue || isOne) && !(isOne && afterPrologue && since <= 3)) {
                starts += k
                afterPrologue = isPrologue
                since = 0
            } else since++
        }
        if (starts.size < 2) return emptyList()

        val books = ArrayList<Pair<InnerBook, Int>>()
        for ((n, s) in starts.withIndex()) {
            val nextStart = starts.getOrNull(n + 1) ?: list.size
            // A book has more than a few chapters: fewer is a sample of another book.
            if (nextStart - s < 5) continue
            // Back from its first chapter, over its front matter, to its title.
            var j = s - 1
            while (j >= 0 && frontMatter.containsMatchIn(list[j].title)) j--
            val titleEntry = list.getOrNull(j)?.takeIf { j >= 0 && (n == 0 || j > starts[n - 1]) }
            val name = titleEntry?.takeIf { !chapterLike.containsMatchIn(it.title) }?.let { bookName(it.title) } ?: continue
            books += InnerBook(name, at(titleEntry)!!, end) to j
        }
        if (books.size < 2) return emptyList()
        // Each book runs to just before the next; the last, to the end or to the collection's own last pages.
        return books.mapIndexed { n, (b, from) ->
            val next = books.getOrNull(n + 1)?.first?.first
            val last = if (next != null) next - 1
            else list.drop(from + 1).firstOrNull { backMatter.containsMatchIn(it.title) }?.let { at(it)!! - 1 } ?: end
            b.copy(last = last.coerceAtLeast(b.first))
        }
    }
}
