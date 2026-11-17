package dev.kobolibrary.app.convert

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.epub.BookMetadata
import java.io.File

/** Draws a PDF's page as a picture (JPEG), no bigger than [maxSide] pixels each way; on a device, Android does. */
fun interface PdfPageRenderer {
    fun render(page: Int, maxSide: Int): ByteArray?
}

/**
 * A PDF as a reflowing book: its text joined back into paragraphs (headers, footers and page numbers left
 * out, words broken at line ends mended), bigger text as headings, a chapter at each of its bookmarks, and
 * pages that are mostly pictures kept as pictures.
 */
object PdfConverter {

    /** A PDF's details, as far as it has them. */
    fun metadata(bytes: ByteArray, fileName: String): BookMetadata = metadata(PdfFile(bytes), fileName)

    /** How many pages the PDF has. */
    fun pageCount(bytes: ByteArray): Int = runCatching { PdfFile(bytes).pages.size }.getOrDefault(0)

    /**
     * For a PDF that is only read and sent as the PDF (it's too big to convert here): a stand-in EPUB with
     * its first page as the cover, so it has a place in the library like any other book.
     */
    fun standIn(fileName: String, target: File, renderer: PdfPageRenderer?) {
        val out = EpubOut(BookMetadata(title = TextConverter.titleFromName(fileName)))
        placeholder(out, target, renderer, "This book is too big to make into an EPUB on this device, so it reads as the PDF it came as.")
    }

    private fun placeholder(out: EpubOut, target: File, renderer: PdfPageRenderer?, why: String) {
        renderer?.render(0, 1600)?.let { out.image("cover.jpg", it, asCover = true) }
        if (out.hasCover) out.coverPage()
        out.page(out.meta.title, "<p class=\"first\">${HtmlSoup.escapeText(why)}</p>\n<p>Open it in KoLib to read it as a PDF. On the Kobo, it goes as a PDF too.</p>")
        out.write(target)
    }

    /** The PDF's contents list (its bookmarks): each title, the page it's on (from 0), and how deep it is. */
    fun contents(file: File): List<Triple<String, Int, Int>> = runCatching { PdfFile(file.readBytes()).outline() }.getOrDefault(emptyList())

    private fun metadata(pdf: PdfFile, fileName: String): BookMetadata {
        val info = pdf.info()
        val fallback = TextConverter.titleFromName(fileName)
        val title = info["Title"]?.trim()?.takeUnless { t ->
            t.isBlank() || t.equals("untitled", true) || t.startsWith("Microsoft Word - ") || t.matches(Regex(""".*\.(docx?|pdf|odt|rtf|txt|indd|qxd)$""", RegexOption.IGNORE_CASE))
        } ?: fallback
        val authors = info["Author"]?.split(Regex(""";|\s+and\s+|\s*&\s*"""))?.map { it.trim() }?.filter { it.isNotEmpty() && !it.equals("unknown", true) }.orEmpty()
        return BookMetadata(
            title = title,
            authors = authors,
            description = info["Subject"]?.takeIf { it.length > 3 }?.let { "<p>${HtmlSoup.escapeText(it)}</p>" },
            language = info["Lang"]?.substringBefore('-')?.lowercase()?.takeIf { it.length in 2..3 },
            tags = info["Keywords"]?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        )
    }

    private class Line(val text: String, val x: Double, val right: Double, val y: Double, val size: Double, val bold: Boolean, val page: Int)

    /** Returns false for a PDF with no text (scanned pages): the EPUB is then its first page and a note. */
    fun convert(bytes: ByteArray, fileName: String, target: File, renderer: PdfPageRenderer? = null): Boolean {
        val pdf = PdfFile(bytes)
        val out = EpubOut(metadata(pdf, fileName))
        val reader = PdfTextReader(pdf)
        val pages = pdf.pages
        if (pages.isEmpty()) throw ConversionException(tr("This PDF has no pages."))
        // Every page's lines, and whether it's mostly pictures.
        val pageLines = ArrayList<List<Line>>()
        val pictureOnly = BooleanArray(pages.size)
        val heights = DoubleArray(pages.size)
        for ((i, page) in pages.withIndex()) {
            val glyphs = runCatching { reader.read(page) }.getOrDefault(emptyList())
            val box = (pdf.list(page["CropBox"]) ?: pdf.list(page["MediaBox"]))?.mapNotNull { pdf.number(it) }
            heights[i] = if (box != null && box.size == 4) box[3] - box[1] else 792.0
            val lines = lines(glyphs, i)
            pageLines += lines
            val chars = lines.sumOf { it.text.length }
            pictureOnly[i] = chars < 40 && (reader.images > 0 || i == 0)
        }
        // Scanned pages, or a book that's mostly pictures (a comic, a picture book): there's little or no
        // text to reflow. It's read (and sent) as the PDF; the EPUB just stands in for it.
        val pictures = pictureOnly.count { it }
        if (pageLines.sumOf { lines -> lines.sumOf { it.text.length } } < 200 || (pictures > 40 && pictures * 2 > pages.size)) {
            if (renderer == null) throw ConversionException(tr("This PDF has no text (it may be a scan), so it can't be converted to a book."))
            placeholder(out, target, renderer, "This book's pages are pictures, so it reads as the PDF it came as.")
            return false
        }
        // Headers and footers: the same text (numbers aside) near the top or bottom of many pages.
        val edgeCounts = HashMap<String, Int>()
        fun edgeKey(l: Line) = l.text.replace(Regex("""\d+"""), "#").trim().lowercase()
        fun atEdge(l: Line): Boolean {
            val h = heights[l.page].takeIf { it > 0 } ?: 792.0
            return l.y > h * 0.9 || l.y < h * 0.08
        }
        for (lines in pageLines) for (l in lines.filter { atEdge(it) }.distinctBy { edgeKey(it) }) edgeCounts.merge(edgeKey(l), 1, Int::plus)
        val bodyLines = pageLines.map { lines ->
            lines.filterNot { l ->
                atEdge(l) && (l.text.trim().matches(Regex("""[\divxlcIVXLC\s.\-–—]+""")) || (edgeCounts[edgeKey(l)] ?: 0) >= 3)
            }
        }
        val all = bodyLines.flatten()
        // The body text's size: the commonest, by how much text is in it.
        val bySize = HashMap<Long, Int>()
        for (l in all) bySize.merge(Math.round(l.size * 2), l.text.length, Int::plus)
        val bodySize = (bySize.maxByOrNull { it.value }?.key ?: 24L) / 2.0
        val gaps = ArrayList<Double>()
        for (lines in bodyLines) for (k in 1 until lines.size) {
            val a = lines[k - 1]
            val b = lines[k]
            if (Math.abs(a.size - bodySize) < 0.6 && Math.abs(b.size - bodySize) < 0.6 && a.y > b.y) gaps += a.y - b.y
        }
        val lineGap = gaps.sorted().getOrNull(gaps.size / 2) ?: bodySize * 1.2
        val lefts = all.filter { Math.abs(it.size - bodySize) < 0.6 }.map { Math.round(it.x) }
        val left = lefts.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.toDouble() ?: 72.0
        val rights = all.filter { Math.abs(it.size - bodySize) < 0.6 }.map { it.right }.sorted()
        val right = rights.getOrNull((rights.size * 0.9).toInt()) ?: 540.0
        val sizes = all.map { it.size }.filter { it > bodySize * 1.15 }.distinct().sortedDescending()

        // Chapters: at the bookmarks (their top level, or the one under a single top entry), else at the big headings.
        val outline = runCatching { pdf.outline() }.getOrDefault(emptyList())
        val topDepth = if (outline.count { it.third == 0 } == 1 && outline.count { it.third == 1 } >= 2) 1 else 0
        val marks = outline.filter { it.third == topDepth }.sortedBy { it.second }.distinctBy { it.second }
        val starts = if (marks.size >= 2) marks.map { it.second to it.first } else emptyList()

        fun headingLevel(l: Line): Int {
            if (l.size < bodySize * 1.15 || l.text.length > 120) return 0
            val rank = sizes.indexOfFirst { Math.abs(it - l.size) < 0.6 }
            return if (rank <= 0 && l.size > bodySize * 1.5) 2 else if (rank <= 1) 3 else 4
        }

        // Everything in order: each page's lines, or the page as a picture.
        class Item(val page: Int, val line: Line?)
        val items = ArrayList<Item>()
        for (p in pages.indices) {
            if (pictureOnly[p] && renderer != null) items += Item(p, null)
            else for (l in bodyLines[p]) items += Item(p, l)
        }
        fun norm(t: String) = t.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        /** Where on [page] the heading [title] is (its first line), else the page's start. */
        fun startOf(page: Int, title: String?): Int {
            val first = items.indexOfFirst { it.page >= page }.let { if (it < 0) items.size else it }
            if (title == null) return first
            val want = norm(title)
            var k = first
            while (k < items.size && items[k].page == page) {
                val t = items[k].line?.text?.let { norm(it) }
                if (t != null && t.isNotEmpty() && (want.startsWith(t) || t.startsWith(want))) return k
                k++
            }
            return first
        }

        fun itemsHtml(from: Int, to: Int, title: String?): String {
            val html = StringBuilder()
            if (title != null) html.append("<h2>").append(HtmlSoup.escapeText(title)).append("</h2>\n")
            var para = StringBuilder()
            var paraHeading = 0
            var prev: Line? = null
            var skipTitle = title != null
            fun flush() {
                val t = para.toString().trim()
                if (t.isNotEmpty()) {
                    // The page's own heading, the same as the bookmark's title over it: once.
                    val sameAsTitle = skipTitle && title != null && norm(t) == norm(title)
                    if (!sameAsTitle) {
                        if (paraHeading > 0) html.append("<h$paraHeading>").append(HtmlSoup.escapeText(t)).append("</h$paraHeading>\n")
                        else html.append("<p>").append(HtmlSoup.escapeText(t)).append("</p>\n")
                    }
                    skipTitle = false
                }
                para = StringBuilder()
                paraHeading = 0
            }
            for (k in from until to) {
                val item = items[k]
                val l = item.line
                if (l == null) {
                    flush()
                    prev = null
                    renderer?.render(item.page, 1600)?.let { jpg ->
                        val src = out.image("page%04d.jpg".format(item.page + 1), jpg, asCover = item.page == 0 && !out.hasCover)
                        if (item.page != 0) html.append("<div class=\"picture\"><img src=\"").append(HtmlSoup.escapeAttr(src)).append("\" alt=\"\"/></div>\n")
                    }
                    continue
                }
                val level = headingLevel(l)
                val pv = prev
                val newPara = when {
                    pv == null -> true
                    level != paraHeading -> true
                    level > 0 -> pv.page != l.page || pv.y - l.y > l.size * 2.2
                    pv.page != l.page -> {
                        // A new page: the paragraph goes on unless it had ended (short last line, ending a sentence), or this one's indented.
                        val ended = pv.right < right - bodySize * 3 && pv.text.trimEnd().lastOrNull()?.let { it in ".!?:\"”’)" } == true
                        ended || l.x > left + bodySize * 0.8
                    }
                    pv.y - l.y > lineGap * 1.45 -> true
                    l.x > left + bodySize * 0.8 && pv.x <= left + bodySize * 0.5 -> true
                    pv.right < right - bodySize * 4 && pv.text.trimEnd().lastOrNull()?.let { it in ".!?:\"”’" } == true -> true
                    else -> false
                }
                if (newPara) {
                    flush()
                    paraHeading = level
                    para.append(l.text.trim())
                } else {
                    val cur = para.toString()
                    val t = l.text.trim()
                    if (cur.endsWith("-") && !cur.endsWith(" -") && t.firstOrNull()?.isLowerCase() == true) {
                        para.setLength(para.length - 1)
                        para.append(t)
                    } else para.append(' ').append(t)
                }
                prev = l
            }
            flush()
            return html.toString()
        }

        // Where chapters start: at each bookmark's heading, else at each of the biggest headings on more than one page.
        val cuts = ArrayList<Pair<Int, String?>>()
        if (starts.isNotEmpty()) {
            for ((page, title) in starts) cuts += startOf(page, title) to title
        } else {
            val topSize = sizes.firstOrNull { s -> all.filter { Math.abs(it.size - s) < 0.6 && headingLevel(it) > 0 }.map { it.page }.distinct().size >= 2 }
            if (topSize != null) {
                var prevLine: Line? = null
                items.forEachIndexed { k, it ->
                    val l = it.line
                    // The first line of each heading (a heading over two lines is one).
                    if (l != null && Math.abs(l.size - topSize) < 0.6 && headingLevel(l) > 0) {
                        val pl = prevLine
                        val continues = pl != null && pl.page == l.page && Math.abs(pl.size - l.size) < 0.6 && pl.y - l.y < l.size * 2.2
                        if (!continues) cuts += k to null
                    }
                    prevLine = l
                }
            } else for (p in 20 until pages.size step 20) cuts += startOf(p, null) to tr("Pages {0}–{1}", p + 1, minOf(p + 20, pages.size))
        }
        val sortedCuts = cuts.distinctBy { it.first }.sortedBy { it.first }
        val firstCut = sortedCuts.firstOrNull()?.first ?: items.size
        if (firstCut > 0) {
            val html = itemsHtml(0, firstCut, null)
            if (html.isNotBlank()) out.page(out.meta.title, html)
        }
        sortedCuts.forEachIndexed { k, (from, title) ->
            val to = sortedCuts.getOrNull(k + 1)?.first ?: items.size
            if (to <= from) return@forEachIndexed
            val html = itemsHtml(from, to, title)
            if (html.isBlank()) return@forEachIndexed
            val name = title ?: Regex("""<h[234]>(.*?)</h[234]>""").find(html)?.groupValues?.get(1)?.let { HtmlSoup.decode(it) }
            out.page(name, html)
        }
        if (out.isEmpty) throw ConversionException(tr("This PDF has no text (it may be a scan), so it can't be converted to a book."))
        // A cover: the first page as a picture, if it wasn't one already.
        if (!out.hasCover) renderer?.render(0, 1600)?.let { out.image("cover.jpg", it, asCover = true) }
        if (out.hasCover && !pictureOnly[0]) out.coverPage()
        out.write(target)
        return true
    }

    /** A page's pieces of text as lines, top to bottom, with spaces where there are gaps. */
    private fun lines(glyphs: List<Glyphs>, page: Int): List<Line> {
        val pieces = glyphs.filter { it.text.isNotBlank() || it.text == " " }.filter { it.size > 0.5 }
        if (pieces.isEmpty()) return emptyList()
        val sorted = pieces.sortedWith(compareByDescending<Glyphs> { it.y }.thenBy { it.x })
        val rows = ArrayList<MutableList<Glyphs>>()
        for (g in sorted) {
            val row = rows.lastOrNull()
            val ry = row?.let { r -> r.sumOf { it.y } / r.size }
            if (row != null && ry != null && Math.abs(ry - g.y) < maxOf(g.size, row.first().size) * 0.45) row += g else rows += mutableListOf(g)
        }
        return rows.mapNotNull { row ->
            val r = row.sortedBy { it.x }
            val sb = StringBuilder()
            var end = Double.NaN
            var last: Glyphs? = null
            for (g in r) {
                // Text drawn twice in the same place (a fake bold): once.
                if (last != null && Math.abs(last.x - g.x) < 0.5 && last.text == g.text) continue
                if (!end.isNaN()) {
                    val gap = g.x - end
                    if (gap > g.size * 0.15 && !sb.endsWith(" ") && !g.text.startsWith(" ")) sb.append(' ')
                }
                sb.append(g.text)
                end = maxOf(if (end.isNaN()) g.x + g.width else end, g.x + g.width)
                last = g
            }
            val text = sb.toString().replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
            if (text.isEmpty()) null else Line(text, r.first().x, end, r.sumOf { it.y } / r.size, r.maxOf { it.size }, r.all { it.bold }, page)
        }
    }
}
