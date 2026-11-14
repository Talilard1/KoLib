package dev.kobolibrary.app.reader

import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubText

/** What a chapter that's one short page is, for where it goes when two pages show side by side. */
enum class PageKind {
    /** A chapter of text, or anything longer than a page or two of front matter: laid out as it is. */
    TEXT,
    /** The book's cover, or the cover of a book inside it: in the middle of the screen. */
    COVER,
    /** The title page of a book inside a book of several: in the middle of the screen. */
    TITLE,
    /** A dedication: on the left-hand page. */
    DEDICATION,
    /** An epigraph or another quote: on the right-hand page. */
    QUOTE,
    /** A picture on its own (a plate, a map): as the reader lays pictures out. */
    PICTURE,
    /** Any other short page (a title page, a notice, a list of the author's books): beside another, or on the right. */
    SHORT;

    /** It can be the left-hand page of two short pages side by side. */
    val canBeLeft: Boolean get() = this == SHORT || this == DEDICATION

    /** It can be the right-hand page of two short pages side by side. */
    val canBeRight: Boolean get() = this == SHORT || this == QUOTE

    /**
     * Where the reader puts it alone, two pages side by side: "cover" and "title" in the middle of the
     * screen (a title page's pictures are its publisher's mark, not a picture to fill the page), "left",
     * or (null) as it lays out.
     */
    val place: String?
        get() = when (this) {
            COVER -> "cover"
            TITLE -> "title"
            DEDICATION -> "left"
            else -> null
        }
}

/**
 * Tells what a chapter of one short page is from its file: a picture with next to no words, a page the
 * book marks as a dedication or epigraph (or that reads like one), or just a short page.
 */
object PagePlacement {

    /** At most this many words is a short page: a dedication, a notice, a title page. */
    const val SHORT_WORDS = 120

    /** And this many, with a picture, is a picture page: a cover, a plate with its caption. */
    private const val PICTURE_WORDS = 8

    /** A picture smaller than this each way, with words by it, is a mark (a publisher's, an ornament), not the page's picture. */
    private const val MARK_SIZE = 400

    private val pictures = Regex("""<(img|image)\b""", RegexOption.IGNORE_CASE)
    private val attrs = Regex("""\b(?:epub:type|class|id|role)\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val blockTag = Regex("""(?is)<(?:/?(?:p|div|h[1-6]|blockquote|li|section|aside|figure|figcaption|header|footer|table|tr|td)\b[^>]*|br\b[^>]*)>""")
    private val bodyStart = Regex("""(?is)<body\b[^>]*>""")
    private val headingTag = Regex("""(?i)<h[1-6]\b""")
    private val pictureSource = Regex("""(?is)<(?:img\b[^>]*?\bsrc|image\b[^>]*?\b(?:xlink:)?href)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val dedicated = Regex("""dedicat""")
    private val quoted = Regex("""epigraph|quot""")
    private val coverMark = Regex("""cover""")
    /** A quote's source under it: "—from The Karaethon Cycle". */
    private val attribution = Regex("""^\s*(?:[—–―]|-\s)""")
    /** How a chapter (or a part of the book) starts: a short one is still a chapter, not a page of front matter. */
    private val chapterStart = Regex(
        """^\s*(?:chapter|chap\.|ch\.|prologue|epilogue|interlude|part|book|section|act|appendix|afterword|foreword|introduction|preface)\b|^\s*\d{1,3}\b""",
        RegexOption.IGNORE_CASE,
    )

    /** How a dedication starts when the book doesn't mark it as one: "For Harriet", "To my mother", "In memory of...". */
    private val dedicationStart = Regex("""^\s*(?:for|to|in (?:loving )?memory|dedicated)\b""", RegexOption.IGNORE_CASE)

    /** What's on a chapter's page, from its file: enough to tell what it is. */
    data class Facts(
        val words: Int,
        val pictures: Int,
        /** The file's name, its title, and the names the book gives its parts (epub:type, class, id), in small letters. */
        val marks: String,
        /** Its title, as the contents (or its heading) name it. */
        val title: String = "",
        val firstLine: String,
        val lastLine: String,
        val blockquote: Boolean,
        val heading: Boolean = false,
        /** The biggest of its pictures, its longer side in pixels; 0 if none could be measured. */
        val biggestPicture: Int = 0,
    )

    /** [sizeOf]: a picture's size, by its path in the book (null if it can't be told). */
    fun facts(xhtml: String, path: String, title: String, sizeOf: (String) -> Pair<Int, Int>? = { null }): Facts {
        val body = bodyStart.find(xhtml)?.let { xhtml.substring(it.range.first) } ?: xhtml
        val text = EpubText.plainText(xhtml)
        val marks = buildString {
            append(path.substringAfterLast('/').lowercase()).append(' ').append(title.lowercase())
            for (m in attrs.findAll(body)) append(' ').append(m.groupValues[1].ifEmpty { m.groupValues[2] }.lowercase())
        }
        val lines = body.split(blockTag).map { EpubText.plainText(it).replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        return Facts(
            words = EpubText.countWords(text),
            pictures = pictures.findAll(body).count(),
            marks = marks,
            title = title,
            firstLine = lines.firstOrNull().orEmpty(),
            lastLine = lines.lastOrNull().orEmpty(),
            blockquote = body.contains("<blockquote", ignoreCase = true),
            heading = headingTag.containsMatchIn(body),
            biggestPicture = pictureSource.findAll(body).mapNotNull { m ->
                val src = m.groupValues[1].ifEmpty { m.groupValues[2] }.substringBefore('?')
                if (src.isEmpty() || src.contains(':')) null
                else sizeOf(EpubFile.resolvePath(path.substringBeforeLast('/', ""), src))?.let { (w, h) -> maxOf(w, h) }
            }.maxOrNull() ?: 0,
        )
    }

    /**
     * What a chapter with [f] on its page is. [coverPlace]: where a book's cover goes (the book's first
     * page, or the first of a book inside it). [titlePlace]: where the title page of a book inside it is.
     */
    fun kind(f: Facts, coverPlace: Boolean, titlePlace: Boolean): PageKind {
        // Pictures with next to no words; but only a mark if they're small and there are words (a title page's).
        val mark = f.words > 0 && f.biggestPicture in 1 until MARK_SIZE
        val picture = f.pictures >= 1 && f.words <= PICTURE_WORDS && !mark
        // A book's title page with its publisher's mark is its title page; only a picture alone is its cover.
        if (picture && !(titlePlace && f.words > 0)) {
            return if (coverPlace || coverMark.containsMatchIn(f.marks)) PageKind.COVER else PageKind.PICTURE
        }
        if (f.words > SHORT_WORDS) return PageKind.TEXT
        if (titlePlace) return PageKind.TITLE
        if (dedicated.containsMatchIn(f.marks)) return PageKind.DEDICATION
        if (quoted.containsMatchIn(f.marks) || f.blockquote) return PageKind.QUOTE
        // A short chapter, or a part's title page: laid out as it is, on its own.
        if (chapterStart.containsMatchIn(f.firstLine) || chapterStart.containsMatchIn(f.title)) return PageKind.TEXT
        if (f.lastLine != f.firstLine && attribution.containsMatchIn(f.lastLine)) return PageKind.QUOTE
        if (f.words <= 60 && dedicationStart.containsMatchIn(f.firstLine)) return PageKind.DEDICATION
        return PageKind.SHORT
    }
}
