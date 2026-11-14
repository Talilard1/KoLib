package dev.kobolibrary.app.convert

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/** The kinds of file the library takes in. All but EPUB are made into EPUBs; a PDF is kept as well, to read and send as it is. */
enum class SourceFormat(val label: String, val extension: String) {
    EPUB("EPUB", "epub"),
    PDF("PDF", "pdf"),
    MOBI("MOBI", "mobi"),
    AZW3("AZW3", "azw3"),
    FB2("FB2", "fb2"),
    DOCX("Word", "docx"),
    ODT("OpenDocument", "odt"),
    RTF("RTF", "rtf"),
    TXT("Text", "txt"),
    HTML("HTML", "html"),
    MARKDOWN("Markdown", "md"),
    CBZ("CBZ", "cbz"),
}

object BookFormats {

    /** File endings the library takes in, for choosing files and searching folders. */
    val extensions = setOf(
        "epub", "kepub", "pdf", "mobi", "azw", "azw3", "prc", "pdb", "fb2", "fbz", "docx", "odt", "rtf", "txt", "text",
        "html", "htm", "xhtml", "md", "markdown", "cbz",
    )

    /**
     * What the file picker shows. Android doesn't know some of these kinds of file, and calls them
     * "application/octet-stream", so that's shown too; anything that isn't a book is turned away when added.
     */
    val mimeTypes = arrayOf(
        "application/epub+zip", "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook",
        "application/vnd.amazon.mobi8-ebook", "application/x-mobi8-ebook", "application/x-fictionbook+xml", "application/x-fictionbook",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.oasis.opendocument.text",
        "application/rtf", "text/*", "application/xhtml+xml", "application/xml", "application/vnd.comicbook+zip", "application/x-cbz",
        "application/zip", "application/x-zip-compressed", "application/octet-stream",
    )

    /** Endings of files that are never plain text. */
    private val NOT_TEXT = setOf("epub", "kepub", "pdf", "mobi", "azw", "azw3", "prc", "pdb", "docx", "odt", "cbz", "fbz", "zip")

    fun isBook(name: String): Boolean = name.lowercase().let { n -> n.endsWith(".kepub.epub") || n.substringAfterLast('.', "") in extensions }

    /** What [file] (named [name]) is, by its contents first and its name after; null if it's none of them. */
    fun detect(file: File, name: String): SourceFormat? {
        val head = ByteArray(1024)
        val n = RandomAccessFile(file, "r").use { it.read(head) }.coerceAtLeast(0)
        val start = String(head, 0, n, Charsets.ISO_8859_1)
        val ext = name.lowercase().substringAfterLast('.', "")
        if (start.startsWith("%PDF") || start.take(1024).contains("%PDF-")) return SourceFormat.PDF
        if (n >= 68 && (start.substring(60, 68) == "BOOKMOBI" || start.substring(60, 68) == "TEXtREAd")) {
            return if (ext == "azw3") SourceFormat.AZW3 else SourceFormat.MOBI
        }
        if (start.startsWith("PK")) {
            val (names, mimetype) = runCatching {
                ZipFile(file).use { z ->
                    z.entries().asSequence().map { it.name }.toList() to z.getEntry("mimetype")?.let { e -> z.getInputStream(e).use { String(it.readBytes()).trim() } }
                }
            }.getOrDefault(emptyList<String>() to null)
            return when {
                mimetype == "application/epub+zip" || "META-INF/container.xml" in names -> SourceFormat.EPUB
                "word/document.xml" in names -> SourceFormat.DOCX
                mimetype == "application/vnd.oasis.opendocument.text" || ("content.xml" in names && "meta.xml" in names) -> SourceFormat.ODT
                names.any { it.endsWith(".fb2", true) } -> SourceFormat.FB2
                names.any { it.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp") } -> SourceFormat.CBZ
                // Called an EPUB but not quite one: reading it as an EPUB says what's wrong with it.
                ext == "epub" || ext == "kepub" -> SourceFormat.EPUB
                else -> null
            }
        }
        if (start.startsWith("{\\rtf")) return SourceFormat.RTF
        val text = start.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (text.contains("<FictionBook")) return SourceFormat.FB2
        // What's left is text, which nothing marks as such: a file named as another kind isn't taken for text.
        if (ext in NOT_TEXT) return null
        return when (ext) {
            "md", "markdown" -> SourceFormat.MARKDOWN
            "html", "htm", "xhtml" -> SourceFormat.HTML
            "txt", "text" -> SourceFormat.TXT
            "fb2" -> SourceFormat.FB2
            else -> when {
                text.startsWith("<!DOCTYPE html", true) || text.startsWith("<html", true) -> SourceFormat.HTML
                n > 0 && head.take(n).count { it.toInt() == 0 } == 0 -> SourceFormat.TXT
                else -> null
            }
        }
    }

    /**
     * Makes an EPUB at [target] from [file] (named [name]) of [format]. [pdfPages] draws a PDF's pages, for
     * its cover and the pages that are pictures.
     */
    fun toEpub(format: SourceFormat, file: File, name: String, target: File, pdfPages: PdfPageRenderer? = null): Converted {
        if (format == SourceFormat.EPUB) {
            file.copyTo(target, overwrite = true)
            return Converted(null)
        }
        if (format == SourceFormat.PDF) {
            // A very big PDF (usually scans) is read and sent as it is: its text isn't taken out.
            val made = if (file.length() > BIG_PDF) null else try {
                PdfConverter.convert(file.readBytes(), name, target, pdfPages)
            } catch (e: OutOfMemoryError) {
                null
            }
            return when (made) {
                true -> Converted(null)
                false -> Converted(PDF_SCAN)
                null -> {
                    PdfConverter.standIn(name, target, pdfPages)
                    Converted(PDF_ONLY)
                }
            }
        }
        val bytes = file.readBytes()
        when (format) {
            SourceFormat.EPUB, SourceFormat.PDF -> Unit
            SourceFormat.MOBI, SourceFormat.AZW3 -> MobiConverter.convert(bytes, name, target)
            SourceFormat.FB2 -> {
                val xml = if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                    unzip(bytes).entries.firstOrNull { it.key.endsWith(".fb2", true) }?.value ?: throw ConversionException("There's no FictionBook file in this zip.")
                } else bytes
                Fb2Converter.convert(xml, name, target)
            }
            SourceFormat.DOCX -> DocxConverter.convert(bytes, name, target)
            SourceFormat.ODT -> OdtConverter.convert(bytes, name, target)
            SourceFormat.RTF -> RtfConverter.convert(bytes, name, target)
            SourceFormat.TXT -> TextConverter.convert(bytes, name, target)
            SourceFormat.HTML -> HtmlConverter.convert(bytes, name, target)
            SourceFormat.MARKDOWN -> MarkdownConverter.convert(bytes, name, target)
            SourceFormat.CBZ -> CbzConverter.convert(bytes, name, target)
        }
        return Converted(null)
    }

    /**
     * How a conversion went: [kind] is set for a PDF that's only read and sent as the PDF, saying why:
     * [PDF_SCAN] or [PDF_ONLY].
     */
    class Converted(val kind: String?)

    /** A PDF of scanned pages: no text in it to make an EPUB of. */
    const val PDF_SCAN = "SCAN"

    /** A PDF too big to take the text out of on the device. */
    const val PDF_ONLY = "PDFONLY"

    /** PDFs bigger than this aren't converted (the converter holds the whole file). */
    private const val BIG_PDF = 64L shl 20
}

/** A PDF's pages, to draw on the screen. */
interface PdfDocument : Closeable {
    val count: Int

    /** Page [index]'s size in points. */
    fun size(index: Int): Pair<Int, Int>

    /** Page [index] as a bitmap [w] × [h], white behind it. */
    fun bitmap(index: Int, w: Int, h: Int): Bitmap

    /**
     * Draws page [index] into [into], where [transform] puts it (from the page's points to the bitmap's
     * pixels), only within [clip]; white behind it.
     */
    fun drawInto(index: Int, into: Bitmap, clip: android.graphics.Rect, transform: android.graphics.Matrix)
}

/** A PDF's pages drawn by Android, for covers, pictures and the PDF reader. One page is drawn at a time. */
class AndroidPdfPages(file: File) : PdfDocument, PdfPageRenderer {
    @Volatile
    private var closed = false
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }

    override val count: Int = renderer.pageCount

    override fun size(index: Int): Pair<Int, Int> = synchronized(this) {
        check(!closed) { "The PDF is closed." }
        renderer.openPage(index).use { it.width to it.height }
    }

    override fun drawInto(index: Int, into: Bitmap, clip: android.graphics.Rect, transform: android.graphics.Matrix) = synchronized(this) {
        check(!closed) { "The PDF is closed." }
        android.graphics.Canvas(into).drawRect(clip, android.graphics.Paint().apply { color = Color.WHITE })
        renderer.openPage(index).use { page -> page.render(into, clip, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
    }

    override fun bitmap(index: Int, w: Int, h: Int): Bitmap = synchronized(this) {
        check(!closed) { "The PDF is closed." }
        renderer.openPage(index).use { page ->
            val bmp = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

    override fun render(page: Int, maxSide: Int): ByteArray? = runCatching {
        val (pw, ph) = size(page)
        val scale = maxSide.toFloat() / maxOf(pw, ph)
        val bmp = bitmap(page, (pw * scale).toInt(), (ph * scale).toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrNull()

    /** Waits for a page being drawn to finish. */
    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            runCatching { renderer.close() }
            runCatching { fd.close() }
        }
    }
}
