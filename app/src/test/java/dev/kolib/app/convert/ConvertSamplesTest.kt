package dev.kolib.app.convert

import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.EpubText
import dev.kolib.app.epub.XmlCheck
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Real books in other formats (made from Alice in Wonderland, and Project Gutenberg's Kindle books), converted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConvertSamplesTest {

    private val samples = File("/home/claude/formats/samples")
    private val outDir = File(System.getProperty("java.io.tmpdir"), "kolib-converted").apply { mkdirs() }

    private fun check(name: String, convert: (ByteArray, String, File) -> Unit): String {
        val src = TestSamples.file(name) ?: File(samples, name)
        assumeTrue(src.exists())
        val target = File(outDir, "$name.epub")
        target.delete()
        val t0 = System.nanoTime()
        convert(src.readBytes(), name, target)
        val ms = (System.nanoTime() - t0) / 1_000_000
        val report = StringBuilder()
        EpubFile.open(target).use { epub ->
            val meta = epub.opf.readMetadata()
            val files = EpubFileFiles(epub)
            val structure = BookStructure(files)
            var bad = 0
            var words = 0
            for (c in structure.chapters) {
                val text = files.text(c.path).orEmpty()
                XmlCheck.check(text, htmlEntities = false)?.let { problem ->
                    bad++
                    if (bad <= 3) report.append("  BAD ${c.path}: $problem\n")
                }
                words += EpubText.countWords(EpubText.plainText(text))
            }
            val cover = epub.findCover()
            report.insert(0, "$name -> '${meta.title}' by ${meta.authors} [${meta.language}] ${structure.chapters.size} pages, toc ${structure.toc.size} " +
                "(${structure.toc.take(4).joinToString { it.title }}${if (structure.toc.size > 4) ", …" else ""}), $words words, cover ${cover?.path}, ${ms}ms\n")
            assertTrue("$name: badly formed pages\n$report", bad == 0)
            assertTrue("$name: no text", words > 100 || name.endsWith(".cbz"))
        }
        println(report)
        return report.toString()
    }

    @Test fun text() { check("11_pg11.txt", TextConverter::convert) }
    @Test fun markdown() { check("alice.md", MarkdownConverter::convert) }
    @Test fun html() { check("alice.html", HtmlConverter::convert) }
    @Test fun fb2() { check("alice.fb2", Fb2Converter::convert) }
    @Test fun docx() { check("alice.docx", DocxConverter::convert) }
    @Test fun odt() { check("alice.odt", OdtConverter::convert) }
    @Test fun rtf() { check("alice.rtf", RtfConverter::convert) }
    @Test fun rtfFromLibreOffice() { check("lo/alice.rtf", RtfConverter::convert) }
    @Test fun cbz() {
        check("comic.cbz") { bytes, name, target ->
            val file = File(outDir, name).apply { writeBytes(bytes) }
            dev.kolib.app.comic.ComicArchives.open(file, libArchive = null).use { c ->
                ComicConverter.wholeEpub(c, dev.kolib.app.comic.ComicInfo.read(c.comicInfo, name).meta, target)
            }
        }
    }
    @Test fun mobi6() { check("11.kindle.images", MobiConverter::convert) }
    private fun pdf(bytes: ByteArray, name: String, target: File) = PdfConverter.convert(bytes, name, target)
    @Test fun pdfLibreOffice() { check("alice.pdf", ::pdf) }
    @Test fun pdfChrome() { check("alice-chrome.pdf", ::pdf) }
    @Test fun pdfReportlab() { check("reportlab.pdf", ::pdf) }
    @Test fun pdfObjectStreams() { check("alice-objstm.pdf", ::pdf) }
    @Test fun pdfLockedRc4() { check("alice-rc4.pdf", ::pdf) }
    @Test fun pdfLockedAes128() { check("alice-aes128.pdf", ::pdf) }
    @Test fun pdfLockedAes256() { check("alice-aes256.pdf", ::pdf) }
    @Test fun kf8() { check("2701.kf8.images", MobiConverter::convert) }
}
