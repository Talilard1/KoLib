package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.Chapter
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.KepubConverter
import dev.kobolibrary.app.epub.XElement
import dev.kobolibrary.app.epub.XText
import dev.kobolibrary.app.epub.Xml
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.reader.ReaderPosition
import java.io.File
import java.net.URLDecoder

/**
 * Where the Kobo is in a book, as its database keeps it: the [chapter] file, and the spot in it. In a
 * kepub that's the sentence at the top of the page ([span], "kobo.12.1"); in an EPUB read with Adobe's
 * renderer it's a [point] ("/1/4/2/1:0"); and the database may also say how far through the chapter it is.
 */
data class KoboPlace(
    val chapter: String,
    val span: String? = null,
    val point: String? = null,
    val chapterProgress: Float? = null,
)

/** The Kobo's place in a book, and where that is in the copy in the library. */
object KoboPlaces {
    private val SPAN = Regex("""^kobo\.\d+\.\d+$""")
    private val POINT = Regex("""point\(([^)]*)\)""")

    /** How exactly the Kobo's place was found in the library's copy. */
    enum class Match {
        /** The sentence (kepub) or spot (EPUB) the Kobo is at. */
        EXACT,
        /** The chapter, and how far through it the Kobo's database says it is. */
        CHAPTER,
        /** Only how far through the whole book the Kobo is. */
        ROUGH,
    }

    data class Found(val position: ReaderPosition, val match: Match)

    fun of(book: KoboBook): KoboPlace? = parse(book.chapterBookmark, book.adobeLocation, book.chapterProgress)

    /**
     * Reads the Kobo's ChapterIDBookmarked and adobe_location. A sideloaded kepub has
     * "OEBPS/Text/chapter3.xhtml#kobo.12.1"; a store book "book id!OEBPS!Text/chapter3.xhtml#kobo.12.1";
     * an EPUB "OEBPS/chapter3.xhtml", with the spot in adobe_location ("point(/1/4/2/1:0)", or a path).
     */
    fun parse(chapterId: String?, adobeLocation: String?, chapterProgress: Double?): KoboPlace? {
        val raw = chapterId?.trim().orEmpty()
        val ade = adobeLocation?.trim().orEmpty()
        var file = raw.substringBefore('#')
        val fragment = raw.substringAfter('#', "")
        if (file.contains('!')) file = file.split('!').drop(1).filter { it.isNotEmpty() }.joinToString("/")
        if (file.isEmpty() && ade.contains('#')) file = ade.substringBefore('#')
        val chapter = cleanPath(file) ?: return null
        val span = fragment.takeIf { SPAN.matches(it) }
        val point = POINT.find(fragment)?.groupValues?.get(1)
            ?: POINT.find(ade)?.groupValues?.get(1)
            ?: ade.substringAfter('#').takeIf { it.startsWith("/") && it.contains(':') }
        val progress = chapterProgress?.toFloat()?.takeIf { it > 0f && it <= 1f }
        return KoboPlace(chapter, span, point?.trim()?.takeIf { it.isNotEmpty() }, progress)
    }

    private fun cleanPath(path: String): String? {
        var p = path.trim().replace('\\', '/')
        p = runCatching { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") }.getOrDefault(p)
        p = p.removePrefix("file:///mnt/onboard/").trimStart('/')
        while (p.startsWith("./")) p = p.removePrefix("./")
        return p.takeIf { it.isNotEmpty() }
    }

    /**
     * Each of [chapters]' share of the book (in percent) as the Kobo gives it, from its [spans]: null if they
     * don't match the chapters well enough to count by (the Kobo has another version of the book, say). A chapter
     * the Kobo has no row for has no share. [weights] (the text in each chapter) say what's well enough: the
     * chapters read through that the Kobo has rows for must hold nearly all the book's text.
     */
    fun shares(chapters: List<Chapter>, spans: List<KoboChapterSpan>, weights: FloatArray): FloatArray? {
        if (spans.isEmpty() || chapters.isEmpty()) return null
        val out = FloatArray(chapters.size)
        val found = BooleanArray(chapters.size)
        val index = chapters.withIndex().associate { (i, c) -> c.path to i }
        for (s in spans) {
            val chapter = spanPaths(s).firstNotNullOfOrNull { chapterFor(chapters, it) } ?: continue
            val i = index[chapter.path] ?: continue
            out[i] += s.size.coerceAtLeast(0.0).toFloat()
            found[i] = true
        }
        val total = out.sum()
        if (total < 90f || total > 110f) return null
        var text = 0f
        var covered = 0f
        for (i in chapters.indices) {
            if (!chapters[i].linear) continue
            val w = weights.getOrElse(i) { 0f }
            text += w
            if (found[i]) covered += w
        }
        if (text <= 0f || covered / text < 0.98f) return null
        return out
    }

    /**
     * The chapter files a row of the Kobo's chapters might name, most likely first: its ContentID
     * ("book id!OEBPS!Text/chapter3.xhtml", "file:///mnt/onboard/Book.epub#(3)OEBPS/chapter3.xhtml") and its
     * title, when that's a file's path.
     */
    internal fun spanPaths(s: KoboChapterSpan): List<String> {
        fun clean(raw: String): String? {
            var p = raw.trim()
            p = if (p.contains("#(")) p.substringAfter("#(").substringAfter(')') else p.substringBefore('#')
            // A file with several entries in the contents is listed once for each: "chapter3.xhtml-2".
            Regex("""^(.*\.(?:x?html?|xml))-\d+$""", RegexOption.IGNORE_CASE).matchEntire(p)?.let { p = it.groupValues[1] }
            if (p.contains('!')) p = p.split('!').drop(1).filter { it.isNotEmpty() }.joinToString("/")
            return cleanPath(p)
        }
        val fromId = clean(s.id)
        val fromTitle = s.title?.takeIf { it.contains('/') || Regex("""\.(?:x?html?|xml)$""", RegexOption.IGNORE_CASE).containsMatchIn(it) }?.let { clean(it) }
        return listOfNotNull(fromId, fromTitle).distinct()
    }

    /** The library's chapter for the Kobo's [path]: the same path, or failing that the one path that ends the same way. */
    fun chapterFor(chapters: List<Chapter>, path: String): Chapter? {
        chapters.firstOrNull { it.path == path }?.let { return it }
        val wanted = path.lowercase()
        chapters.firstOrNull { it.path.lowercase() == wanted }?.let { return it }
        chapters.filter {
            val c = it.path.lowercase()
            c.endsWith("/$wanted") || wanted.endsWith("/$c")
        }.singleOrNull()?.let { return it }
        val name = wanted.substringAfterLast('/')
        return chapters.filter { it.path.lowercase().substringAfterLast('/') == name }.singleOrNull()
    }

    /**
     * Where [kobo]'s place is in the library's copy of the book, [epub]: as a reader position whose anchor
     * is the letters into the chapter (see [ChapterLetters]), which the reader finds again. Null if the
     * Kobo hasn't a place in the book, or the book has nothing to read.
     */
    fun find(epub: File, kobo: KoboBook): Found? = EpubFileFiles(EpubFile.open(epub)).use { files ->
        val structure = BookStructure(files)
        val chapters = structure.chapters
        if (chapters.isEmpty()) return null
        val place = of(kobo)
        val percent = kobo.percentRead.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val chapter = place?.let { chapterFor(chapters, it.chapter) }
        AppLog.i(
            "Kobo place",
            "“${kobo.title}”: the Kobo has ${kobo.chapterBookmark ?: "no chapter"}" +
                (kobo.adobeLocation?.let { ", Adobe place $it" } ?: "") +
                (kobo.chapterProgress?.let { ", ${"%.3f".format(it)} through the chapter" } ?: "") +
                ", $percent% through the book; " + (chapter?.let { "that's ${it.path} here" } ?: "no chapter here matches"),
        )

        if (place != null && chapter != null) {
            val xhtml = files.text(chapter.path)
            val text = xhtml?.let { runCatching { ChapterLetters(it) }.getOrNull() }
            val exact = if (xhtml == null || text == null) null else {
                place.span?.let { spanLetters(xhtml, it) } ?: place.point?.let { runCatching { text.lettersAtPoint(it) }.getOrNull() }
            }
            AppLog.i("Kobo place", if (exact != null) "Found it $exact letters into the chapter, of ${text?.total}." else "The exact spot wasn't found; going by the chapter.")
            if (text != null && exact != null) {
                val progress = if (text.total > 0) exact.toFloat() / text.total else 0f
                val book = if (percent > 0) percent / 100f else bookProgress(files, chapters, chapter, progress)
                return Found(
                    ReaderPosition(chapter.path, ChapterLetters.anchor(exact), progress.coerceIn(0f, 1f), book, text.snippetAt(exact), chapter.title, now),
                    Match.EXACT,
                )
            }
            // The chapter, and how far through it (the Kobo's own figure, else the start).
            val progress = place.chapterProgress ?: 0f
            val letters = text?.let { (it.total * progress).toInt().coerceIn(0, maxOf(0, it.total - 1)) }
            val book = if (percent > 0) percent / 100f else bookProgress(files, chapters, chapter, progress)
            return Found(
                ReaderPosition(chapter.path, letters?.let { ChapterLetters.anchor(it) }, progress, book, letters?.let { text?.snippetAt(it) }.orEmpty(), chapter.title, now),
                Match.CHAPTER,
            )
        }

        // Only how far through the book: the chapter that far through it, by the letters in each.
        if (percent <= 0) return null
        val linear = chapters.filter { it.linear }.ifEmpty { chapters }
        val counted = linear.map { c -> c to (files.text(c.path)?.let { runCatching { ChapterLetters(it) }.getOrNull() }) }
        val total = counted.sumOf { it.second?.total ?: 0 }
        if (total <= 0) return null
        val target = (total * (percent / 100.0)).toLong().coerceIn(0, total - 1L)
        var before = 0L
        for ((c, t) in counted) {
            val n = t?.total ?: 0
            if (t != null && target < before + n) {
                val letters = (target - before).toInt()
                return Found(
                    ReaderPosition(c.path, ChapterLetters.anchor(letters), letters.toFloat() / n, percent / 100f, t.snippetAt(letters), c.title, now),
                    Match.ROUGH,
                )
            }
            before += n
        }
        val c = counted.last { it.second != null }.first
        Found(ReaderPosition(c.path, null, 1f, percent / 100f, "", c.title, now), Match.ROUGH)
    }

    /**
     * How many letters into the chapter the Kobo's [span] starts. The library's copy is an EPUB, so it's
     * made into a kepub the way it was when it was sent, which numbers its sentences the same way.
     */
    internal fun spanLetters(xhtml: String, span: String): Int? = try {
        val kepub = if (KepubConverter.isKepub(xhtml)) xhtml else KepubConverter.convert(xhtml)
        ChapterLetters(kepub).lettersBeforeId(span)
    } catch (e: Exception) {
        AppLog.w("Kobo place", "The chapter couldn't be read to find ${span}: ${e.message}")
        null
    }

    /** How far through the book a place [progress] through [chapter] is, by the letters in the chapters before it. */
    private fun bookProgress(files: EpubFileFiles, chapters: List<Chapter>, chapter: Chapter, progress: Float): Float {
        val linear = chapters.filter { it.linear }.ifEmpty { chapters }
        val sizes = linear.map { c -> c to (files.text(c.path)?.let { runCatching { ChapterLetters(it).total }.getOrNull() } ?: 0) }
        val total = sizes.sumOf { it.second }
        if (total <= 0) return 0f
        val before = sizes.takeWhile { it.first.path != chapter.path }.sumOf { it.second }
        val here = sizes.firstOrNull { it.first.path == chapter.path }?.second ?: 0
        return ((before + here * progress) / total).coerceIn(0f, 1f)
    }
}

/**
 * A chapter's text counted in letters: every character but spaces (and the invisible ones), in the
 * body, outside scripts and styles. reader.js counts the same way (its uncounted()), so "so many letters
 * into the chapter" is the same place in both, however differently they read the markup.
 */
class ChapterLetters(xhtml: String) {
    private class Piece(val text: String, val before: Int)

    private val pieces = ArrayList<Piece>()
    private val idAt = HashMap<String, Int>()
    private val body: XElement?

    /** All the letters in the chapter. */
    val total: Int

    init {
        val doc = Xml.parse(xhtml)
        body = doc.root.elements().firstOrNull { it.localName.equals("body", true) }
        var n = 0
        fun walk(e: XElement) {
            for (c in e.children) when (c) {
                is XElement -> {
                    c.attributes["id"]?.let { idAt.putIfAbsent(it, n) }
                    if (c.localName.lowercase() !in SKIPPED) walk(c)
                }
                is XText -> if (!c.isCData) {
                    val t = c.text
                    pieces += Piece(t, n)
                    n += t.count { counts(it) }
                }
                else -> Unit
            }
        }
        body?.let { walk(it) }
        total = n
    }

    /** How many letters come before the element with this [id]. */
    fun lettersBeforeId(id: String): Int? = idAt[id]

    /**
     * Where an Adobe point ("/1/4/2/1:6") is, in letters. Its first step is the html element; after that,
     * as in an EPUB CFI, an even step is an element (2 for the first) and an odd one the text around them,
     * and the number after the colon is the character in that text.
     */
    fun lettersAtPoint(point: String): Int? {
        val path = point.substringBefore(':').replace(Regex("""\[[^\]]*]"""), "")
        val offset = point.substringAfter(':', "0").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val steps = path.split('/').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        if (steps.isEmpty()) return null
        val root = body?.parent ?: return null
        var at: XElement = root
        for ((i, s) in steps.withIndex()) {
            if (i == 0) continue // the html element itself
            if (s % 2 == 0) {
                at = at.elements().getOrNull(s / 2 - 1) ?: return null
                continue
            }
            // Text: the run between element (s - 1) / 2 and the next. Only as the last step.
            if (i != steps.lastIndex) return null
            val after = (s - 1) / 2
            var elementsSeen = 0
            val run = StringBuilder()
            var start: XText? = null
            for (c in at.children) {
                if (c is XElement) {
                    elementsSeen++
                    if (elementsSeen > after) break
                } else if (c is XText && elementsSeen == after && !c.isCData) {
                    if (start == null) start = c
                    run.append(c.text)
                }
            }
            val first = start ?: return lettersBeforeElementEnd(at, after)
            val base = piecesBefore(first) ?: return null
            return base + run.substring(0, offset.coerceIn(0, run.length)).count { counts(it) }
        }
        return lettersAtElement(at)
    }

    private fun piecesBefore(t: XText): Int? {
        // The text pieces were made in document order from the same nodes; find this one by its place.
        var n = 0
        var found: Int? = null
        fun walk(e: XElement): Boolean {
            for (c in e.children) when (c) {
                is XElement -> if (c.localName.lowercase() !in SKIPPED && walk(c)) return true
                is XText -> if (!c.isCData) {
                    if (c === t) {
                        found = n
                        return true
                    }
                    n += c.text.count { counts(it) }
                }
                else -> Unit
            }
            return false
        }
        body?.let { walk(it) }
        return found
    }

    private fun lettersAtElement(e: XElement): Int? {
        if (e === body) return 0
        var n = 0
        var found: Int? = null
        fun walk(x: XElement): Boolean {
            for (c in x.children) when (c) {
                is XElement -> {
                    if (c === e) {
                        found = n
                        return true
                    }
                    if (c.localName.lowercase() !in SKIPPED && walk(c)) return true
                }
                is XText -> if (!c.isCData) n += c.text.count { counts(it) }
                else -> Unit
            }
            return false
        }
        body?.let { walk(it) }
        return found
    }

    /** Text with nothing after its last element: where that element ends. */
    private fun lettersBeforeElementEnd(parent: XElement, after: Int): Int? {
        val e = parent.elements().getOrNull(after - 1) ?: return lettersAtElement(parent)
        val start = lettersAtElement(e) ?: return null
        return start + if (e.localName.lowercase() in SKIPPED) 0 else e.text().count { counts(it) }
    }

    /** About a line of the text from [letters] on, for showing where the place is. */
    fun snippetAt(letters: Int): String {
        val out = StringBuilder()
        for (p in pieces) {
            val end = p.before + p.text.count { counts(it) }
            if (end <= letters) continue
            if (out.isEmpty()) {
                var k = p.before
                var i = 0
                while (i < p.text.length && (k < letters || !counts(p.text[i]))) {
                    if (counts(p.text[i])) k++
                    i++
                }
                out.append(p.text.substring(i))
            } else {
                out.append(p.text)
            }
            if (out.length > 200) break
        }
        return out.toString().replace(Regex("\\s+"), " ").trim().take(140)
    }

    companion object {
        /** Elements whose text isn't counted: it isn't part of what's read (reader.js leaves out the same). */
        val SKIPPED = setOf("script", "style", "noscript", "template", "noembed", "noframes", "xmp", "iframe")

        /** The reader position's anchor for a place this many letters into a chapter (see reader.js). */
        fun anchor(letters: Int) = "chars=$letters"

        /**
         * Whether a character counts as a letter: everything but spaces, control characters and the
         * invisible ones (soft hyphens, zero-width spaces, joiners). The same ranges as reader.js's uncounted().
         */
        fun counts(c: Char): Boolean {
            val x = c.code
            return !(x <= 0x20 || x in 0x7F..0xA0 || x == 0xAD || x == 0x1680 || x in 0x2000..0x200F ||
                x in 0x2028..0x202F || x in 0x205F..0x2064 || x == 0x3000 || x == 0xFEFF)
        }
    }
}
