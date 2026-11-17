package dev.kolib.app.epub

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small but realistic EPUB files for tests. */
object TestEpubs {

    val CALIBRE_EPUB2_OPF = """<?xml version='1.0' encoding='utf-8'?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uuid_id" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>The Histories</dc:title>
    <dc:creator opf:file-as="Herodotus" opf:role="aut">Herodotus</dc:creator>
    <dc:creator opf:role="trl" opf:file-as="Rawlinson, George">George Rawlinson</dc:creator>
    <dc:identifier opf:scheme="calibre" id="calibre_id">42</dc:identifier>
    <dc:identifier opf:scheme="uuid" id="uuid_id">0d8f2b6a-1c2d-4e5f-8a9b-0c1d2e3f4a5b</dc:identifier>
    <dc:identifier opf:scheme="ISBN">9780140449082</dc:identifier>
    <dc:contributor opf:file-as="calibre" opf:role="bkp">calibre (7.2.0) [https://calibre-ebook.com]</dc:contributor>
    <dc:date>2003-02-27T13:00:00+00:00</dc:date>
    <dc:description>&lt;p&gt;The first great work of &lt;b&gt;history&lt;/b&gt;.&lt;/p&gt;</dc:description>
    <dc:publisher>Penguin Classics</dc:publisher>
    <dc:subject>History</dc:subject>
    <dc:subject>Classics</dc:subject>
    <dc:language>en</dc:language>
    <meta name="calibre:series" content="Penguin Classics"/>
    <meta name="calibre:series_index" content="3.0"/>
    <meta name="calibre:rating" content="8"/>
    <meta name="calibre:timestamp" content="2023-01-01T00:00:00+00:00"/>
    <meta name="calibre:title_sort" content="Histories, The"/>
    <!-- a comment the app must keep -->
    <meta name="cover" content="cover"/>
  </metadata>
  <manifest>
    <item href="cover.jpeg" id="cover" media-type="image/jpeg"/>
    <item href="titlepage.xhtml" id="titlepage" media-type="application/xhtml+xml"/>
    <item href="text/part0000.html" id="html0" media-type="application/xhtml+xml"/>
    <item href="toc.ncx" id="ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="titlepage"/>
    <itemref idref="html0"/>
  </spine>
  <guide>
    <reference href="titlepage.xhtml" title="Cover" type="cover"/>
  </guide>
</package>
"""

    val EPUB3_OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id" xml:lang="en">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:identifier id="pub-id">urn:uuid:12345678-1234-1234-1234-123456789abc</dc:identifier>
        <dc:title id="t1">Meditations</dc:title>
        <dc:title id="t2">A New Translation</dc:title>
        <meta refines="#t1" property="title-type">main</meta>
        <meta refines="#t2" property="title-type">subtitle</meta>
        <dc:creator id="ill">Some Illustrator</dc:creator>
        <meta refines="#ill" property="role" scheme="marc:relators">ill</meta>
        <dc:creator id="c1">Marcus Aurelius</dc:creator>
        <meta refines="#c1" property="role" scheme="marc:relators">aut</meta>
        <meta refines="#c1" property="file-as">Aurelius, Marcus</meta>
        <dc:language>en</dc:language>
        <dc:date>2002-05-07</dc:date>
        <meta property="belongs-to-collection" id="s1">Modern Library</meta>
        <meta refines="#s1" property="collection-type">series</meta>
        <meta refines="#s1" property="group-position">12</meta>
        <meta property="dcterms:modified">2020-01-01T00:00:00Z</meta>
    </metadata>
    <manifest>
        <item id="img" href="images/cover.png" media-type="image/png" properties="cover-image"/>
        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
        <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
    </manifest>
    <spine><itemref idref="ch1"/></spine>
</package>
"""

    const val CONTAINER = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    const val SVG_TITLEPAGE = """<?xml version='1.0' encoding='utf-8'?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Cover</title></head>
<body>
<div>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" width="100%" height="100%" viewBox="0 0 600 900" preserveAspectRatio="none">
<image width="600" height="900" xlink:href="cover.jpeg"/>
</svg>
</div>
</body>
</html>"""

    fun calibreEpub2(file: File, extra: Map<String, ByteArray> = emptyMap()): File = build(
        file,
        mapOf(
            "META-INF/container.xml" to CONTAINER.toByteArray(),
            "OEBPS/content.opf" to CALIBRE_EPUB2_OPF.toByteArray(),
            "OEBPS/cover.jpeg" to fakeJpeg(1),
            "OEBPS/titlepage.xhtml" to SVG_TITLEPAGE.toByteArray(),
            "OEBPS/text/part0000.html" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Herodotus of Halicarnassus...</p></body></html>".toByteArray(),
            "OEBPS/toc.ncx" to "<ncx/>".toByteArray(),
        ) + extra,
    )

    fun epub3(file: File): File = build(
        file,
        mapOf(
            "META-INF/container.xml" to CONTAINER.toByteArray(),
            "OEBPS/content.opf" to EPUB3_OPF.toByteArray(),
            "OEBPS/images/cover.png" to byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3),
            "OEBPS/nav.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><nav/></body></html>".toByteArray(),
            "OEBPS/text/ch1.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Begin the morning...</p></body></html>".toByteArray(),
        ),
    )

    fun fakeJpeg(seed: Int): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), seed.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())

    /** Writes a zip with mimetype first and stored, like a proper EPUB. */
    fun build(file: File, entries: Map<String, ByteArray>, mimetype: Boolean = true): File {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream()).use { out ->
            if (mimetype) {
                val bytes = "application/epub+zip".toByteArray()
                val crc = CRC32().apply { update(bytes) }
                out.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = crc.value
                })
                out.write(bytes)
                out.closeEntry()
            }
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }
}
