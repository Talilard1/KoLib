package dev.kobolibrary.app.epub

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EpubTextTest {

    @Test
    fun plainTextDropsMarkupAndDecodesCharacters() {
        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Not counted</title><style>p { color: red }</style></head>
<body><h1>Chapter&#160;One</h1><p>Fish &amp; chips &#x2014; &lt;tasty&gt;. Caf&eacute;</p><script>var x = 1;</script></body></html>"""
        val text = EpubText.plainText(xhtml).replace(Regex("\\s+"), " ").trim()
        assertEquals("Chapter One Fish & chips — <tasty>. Caf", text)
        assertEquals(6, EpubText.countWords(EpubText.plainText(xhtml)))
    }

    @Test
    fun countsWordsTheWayAReaderWould() {
        assertEquals(0, EpubText.countWords(""))
        assertEquals(0, EpubText.countWords(" — … ! "))
        assertEquals(4, EpubText.countWords("It was the best"))
        assertEquals("contractions and hyphens stay whole", 4, EpubText.countWords("Don't stop the well-known"))
        assertEquals(3, EpubText.countWords("It’s 1890, and—"))
        assertEquals("a trailing hyphen or apostrophe splits nothing", 2, EpubText.countWords("rock- 'n"))
        assertEquals(3, EpubText.countWords("Ἐν ἀρχῇ ἦν"))
        assertEquals("Chinese counts each character", 4, EpubText.countWords("我爱读书"))
        assertEquals(5, EpubText.countWords("Read 本を読む"))
    }

    @Test
    fun countsAWholeBook() {
        val dir = Files.createTempDirectory("words").toFile()
        val book = TestEpubs.build(
            File(dir, "b.epub"),
            mapOf(
                "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
                "OEBPS/content.opf" to """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title><dc:identifier id="id">x</dc:identifier></metadata>
  <manifest>
    <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
    <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
    <item id="css" href="s.css" media-type="text/css"/>
  </manifest>
  <spine><itemref idref="a"/><itemref idref="b"/></spine>
</package>""".toByteArray(),
                "OEBPS/a.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Ignored words here</title></head><body><p>One two three.</p></body></html>".toByteArray(),
                "OEBPS/b.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Four <i>five</i></p></body></html>".toByteArray(),
                "OEBPS/s.css" to "p { margin: 0 } /* not words either */".toByteArray(),
            ),
        )
        assertEquals(5, EpubFile.open(book).use { EpubText.countWords(it) })
        dir.deleteRecursively()
    }
}
