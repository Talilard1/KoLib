package dev.kobolibrary.app.ocr

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.convert.EpubOut
import dev.kobolibrary.app.convert.HtmlSoup
import dev.kobolibrary.app.epub.BookMetadata
import java.io.File

/** Pictures of a book's pages, for the pages (and parts of pages) kept as pictures. */
interface PagePictures {
    /** Page [index] as a JPEG, no bigger than [maxSide] pixels each way. */
    fun page(index: Int, maxSide: Int): ByteArray

    /** The part [box] of page [index] as a JPEG, [box] being in the pixels of a picture of the page [width] × [height]. */
    fun part(index: Int, box: Box, width: Int, height: Int, maxSide: Int): ByteArray
}

/**
 * A book made from the text recognised on its pages: the lines of each paragraph joined (words broken at
 * the ends of lines mended), running heads, feet and page numbers left out, paragraphs carried on over
 * pages and columns, bigger type as headings, pages with little or no text kept as pictures, and a
 * chapter at each of the PDF's bookmarks (else at each big heading, else every 20 pages).
 */
object OcrBook {

    /** A line as the book needs it: its text, where it is, its size, and how many words. */
    class TextLine(val text: String, val box: Box, val size: Int, val kind: OcrLine.Kind, val words: Int)
    class TextPara(val lines: List<TextLine>)
    class TextBlock(val box: Box, val paras: List<TextPara>, val picture: Boolean)

    /** A page's text without each word's place (a long book's pages, held at once, would take too much room). */
    class PageText(val width: Int, val height: Int, val blank: Boolean, val letters: Int, val confidence: Int, val blocks: List<TextBlock>) {
        val lines: List<TextLine> get() = blocks.flatMap { b -> b.paras.flatMap { it.lines } }

        companion object {
            fun of(p: OcrPage) = PageText(
                p.width, p.height, p.blank, p.letters, p.confidence,
                p.blocks.map { b -> TextBlock(b.box, b.paragraphs.map { para -> TextPara(para.lines.map { TextLine(it.text, it.box, it.size, it.kind, it.words.size) }) }, b.picture) },
            )
        }
    }

    sealed class Item(val page: Int) {
        class Para(page: Int, var text: String, val heading: Int) : Item(page)
        class Picture(page: Int, val box: Box?, val pageWidth: Int, val pageHeight: Int) : Item(page)
    }

    private val pageNumber = Regex("""^[\s\p{P}]*(\d{1,4}|[ivxlcdm]{1,8}|[IVXLCDM]{1,8})[\s\p{P}]*$""")
    private val hasNumber = Regex("""(^|[\s\p{P}])\d{1,4}([\s\p{P}]|$)""")
    private val chapterWord = Regex("""^(chapter|book|part|section|canto|act|scene|chapitre|livre|kapitel|teil|capítulo|capitolo|libro|hoofdstuk|глава|часть)\b""", RegexOption.IGNORE_CASE)
    private val sentenceEnd = Regex("""[.!?:;…"”’»)\]]$""")

    private fun edgeKey(t: String) = t.lowercase().replace(Regex("""\d+"""), "#").replace(Regex("""[^\p{L}#]+"""), " ").trim()

    /** The size of the book's ordinary text: the middle of its lines' sizes. */
    fun bodySize(pages: List<PageText?>): Int {
        val sizes = pages.filterNotNull().flatMap { p -> p.lines.filter { it.words >= 3 }.map { it.size } }.sorted()
        return sizes.getOrNull(sizes.size / 2)?.coerceAtLeast(1) ?: 30
    }

    /**
     * The lines on each page that are running heads, feet or page numbers: in the top or bottom tenth of the
     * page, and only a number, or the same (but for numbers) on three pages or more, or (at the top) a short
     * line with a number in it or one the recognition took for a heading that isn't much bigger than the text.
     */
    fun edges(pages: List<PageText?>, body: Int = bodySize(pages)): List<Set<TextLine>> {
        fun candidates(p: PageText): Pair<List<TextLine>, List<TextLine>> {
            val lines = p.lines.sortedBy { it.box.top }
            val top = lines.takeWhile { it.box.centreY < p.height * 0.1 }.take(2)
            val bottom = lines.reversed().takeWhile { it.box.centreY > p.height * 0.9 }.take(2)
            return top to bottom
        }
        val seen = HashMap<String, MutableSet<Int>>()
        pages.forEachIndexed { i, p ->
            if (p == null) return@forEachIndexed
            val (top, bottom) = candidates(p)
            for (l in top + bottom) {
                val key = edgeKey(l.text)
                if (key.isNotEmpty()) seen.getOrPut(key) { HashSet() } += i
            }
        }
        val repeated = { l: TextLine -> (seen[edgeKey(l.text)]?.size ?: 0) >= 3 }
        return pages.map { p ->
            if (p == null) return@map emptySet()
            val (top, bottom) = candidates(p)
            val small = { l: TextLine -> l.size <= body * 1.3 }
            val dropped = HashSet<TextLine>()
            for (l in top) {
                val words = l.words
                if (pageNumber.matches(l.text) || (small(l) && (repeated(l) || (hasNumber.containsMatchIn(l.text) && words <= 10) ||
                        (l.kind == OcrLine.Kind.HEADING && words <= 10)))) dropped += l
            }
            for (l in bottom) if (pageNumber.matches(l.text) || (small(l) && repeated(l))) dropped += l
            dropped
        }
    }

    /** A page that's kept as a picture: one with little or no text, or text that's only what a picture looked like. */
    fun isPicture(p: PageText): Boolean {
        if (p.blank) return false
        val letters = p.letters
        return letters < 40 || (p.confidence < 30 && letters < 400)
    }

    /**
     * A paragraph's lines, split where the type changes size by more than a little: a chapter's number over
     * its title, or a heading the recognition took for the start of the text under it, are each their own.
     */
    fun bySize(lines: List<TextLine>, body: Int): List<List<TextLine>> {
        if (lines.size < 2) return listOf(lines)
        val out = ArrayList<MutableList<TextLine>>()
        for (l in lines) {
            val prev = out.lastOrNull()?.last()
            val bigger = maxOf(l.size, prev?.size ?: 0)
            if (prev == null || (kotlin.math.abs(l.size - prev.size) > 0.15 * bigger && bigger >= body * 1.25)) out += mutableListOf(l)
            else out.last() += l
        }
        return out
    }

    /** Joins line [b] onto [a], mending a word broken over the two with a hyphen. */
    fun join(a: String, b: String): String {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val last = a.last()
        val broken = (last == '-' || last == '¬' || last == '­') && a.length > 1 && a[a.length - 2].isLetter() && b.first().isLowerCase()
        return if (broken) a.dropLast(1) + b else "$a $b"
    }

    /** Everything in the book in order: paragraphs (some of them headings) and pictures. */
    fun items(pages: List<PageText?>, withPictures: Boolean = true): List<Item> {
        val body = bodySize(pages)
        val edges = edges(pages, body)
        val items = ArrayList<Item>()
        pages.forEachIndexed { index, page ->
            if (page == null || page.blank) return@forEachIndexed
            if (isPicture(page)) {
                if (withPictures) items += Item.Picture(index, null, page.width, page.height)
                return@forEachIndexed
            }
            val skip = edges[index]
            val pageArea = page.width.toLong() * page.height
            for (block in page.blocks) {
                if (block.picture) {
                    if (withPictures && block.box.area * 12 > pageArea) items += Item.Picture(index, block.box, page.width, page.height)
                    continue
                }
                for ((n, lines) in block.paras.flatMap { para -> bySize(para.lines.filter { it !in skip }, body) }.withIndex()) {
                    if (lines.isEmpty()) continue
                    val text = lines.fold("") { acc, l -> join(acc, l.text.trim()) }.replace(Regex("""\s+"""), " ").trim()
                    if (text.none { it.isLetterOrDigit() }) continue
                    val size = lines.map { it.size }.sorted()[lines.size / 2]
                    val words = lines.sumOf { it.words }
                    val heading = when {
                        lines.size <= 2 && words <= 8 && size >= body * 1.1 && chapterWord.containsMatchIn(text) -> 2
                        lines.size <= 2 && words <= 14 && size >= body * 1.8 -> 2
                        lines.size <= 2 && words <= 14 && size >= body * 1.25 -> 3
                        else -> 0
                    }
                    // A paragraph carried on from the column or page before.
                    val prev = items.lastOrNull() as? Item.Para
                    val carriedOn = prev != null && heading == 0 && prev.heading == 0 && prev.page >= index - 1 &&
                        n == 0 && !sentenceEnd.containsMatchIn(prev.text) &&
                        (text.first().isLowerCase() || prev.text.endsWith("-"))
                    if (carriedOn) prev!!.text = join(prev.text, text)
                    else items += Item.Para(index, text, heading)
                }
            }
        }
        return items
    }

    private fun norm(t: String) = t.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    /**
     * Where chapters start: (index in [items], title). At the bookmarks in [contents] (title, page from 0,
     * depth) if there are some, else at the biggest headings if they're on more than one page, else every
     * 20 pages.
     */
    fun chapters(items: List<Item>, contents: List<Triple<String, Int, Int>>, pages: Int): List<Pair<Int, String?>> {
        fun firstOn(page: Int) = items.indexOfFirst { it.page >= page }.let { if (it < 0) items.size else it }
        val topDepth = if (contents.count { it.third == 0 } == 1 && contents.count { it.third == 1 } >= 2) 1 else 0
        val marks = contents.filter { it.third == topDepth && it.second in 0 until pages }.sortedBy { it.second }.distinctBy { it.second }
        if (marks.size >= 2) return marks.map { (title, page, _) -> firstOn(page) to title.trim() }.distinctBy { it.first }
        val big = items.withIndex().filter { (it.value as? Item.Para)?.heading == 2 }
        if (big.map { it.value.page }.distinct().size >= 2) return big.map { it.index to (it.value as Item.Para).text }
        if (pages <= 30) return emptyList()
        return (0 until pages step 20).map { p -> firstOn(p) to tr("Pages {0}–{1}", p + 1, minOf(p + 20, pages)) }.distinctBy { it.first }
    }

    /** Makes the book at [target]: [pages] as recognised (null for any that weren't), with [meta] and [contents] (the PDF's bookmarks). */
    fun write(meta: BookMetadata, pages: List<PageText?>, contents: List<Triple<String, Int, Int>>, pictures: PagePictures?, target: File) {
        val out = EpubOut(meta)
        val items = items(pages, withPictures = pictures != null)
        val first = pages.firstOrNull()
        // The first page is the cover, unless it's blank.
        if (pictures != null && first != null && !first.blank) out.imageLater("cover.jpg", asCover = true) { pictures.page(0, 1600) }
        fun html(from: Int, to: Int, title: String?): String {
            val sb = StringBuilder()
            var skipTitle = title != null
            if (title != null) sb.append("<h2>").append(HtmlSoup.escapeText(title)).append("</h2>\n")
            for (k in from until to) {
                when (val it = items[k]) {
                    is Item.Para -> {
                        // The page's own heading, the same as the bookmark's title over it: once.
                        if (skipTitle && it.heading > 0 && title != null && norm(it.text).let { t -> t.isNotEmpty() && (norm(title).startsWith(t) || t.startsWith(norm(title))) }) {
                            skipTitle = false
                            continue
                        }
                        skipTitle = false
                        val t = HtmlSoup.escapeText(it.text)
                        if (it.heading > 0) sb.append("<h").append(it.heading).append('>').append(t).append("</h").append(it.heading).append(">\n")
                        else sb.append("<p>").append(t).append("</p>\n")
                    }
                    is Item.Picture -> {
                        val p = pictures ?: continue
                        // The cover page shows the first page already.
                        if (it.page == 0 && it.box == null && out.hasCover) continue
                        val name = if (it.box == null) "page%04d.jpg".format(it.page + 1) else "page%04d-part.jpg".format(it.page + 1)
                        val src = out.imageLater(name) {
                            if (it.box == null) p.page(it.page, 1600) else p.part(it.page, it.box, it.pageWidth, it.pageHeight, 1600)
                        }
                        sb.append("<div class=\"picture\"><img src=\"").append(HtmlSoup.escapeAttr(src)).append("\" alt=\"\"/></div>\n")
                    }
                }
            }
            return sb.toString()
        }
        val cuts = chapters(items, contents, pages.size)
        val firstCut = cuts.firstOrNull()?.first ?: items.size
        if (out.hasCover) out.coverPage()
        if (firstCut > 0) {
            val body = html(0, firstCut, null)
            if (body.isNotBlank()) out.page(if (cuts.isEmpty()) meta.title else null, body)
        }
        cuts.forEachIndexed { k, (from, title) ->
            val to = cuts.getOrNull(k + 1)?.first ?: items.size
            if (to <= from) return@forEachIndexed
            val body = html(from, to, title)
            if (body.isNotBlank()) out.page(title, body)
        }
        if (out.isEmpty) throw dev.kobolibrary.app.convert.ConversionException(tr("No text was found on the pages."))
        out.write(target)
    }
}
