package dev.kobolibrary.app.epub

import java.io.File

/** Fuller books for the reader and editor tests: parts, chapters, notes, styles and a font. */
object TestBooks {

    private fun page(title: String, body: String, extraHead: String = "") = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>$title</title>
  <link rel="stylesheet" type="text/css" href="../styles/style.css"/>$extraHead
</head>
<body class="chapter">
$body
</body>
</html>
"""

    val PART1 = page("Part One", """  <h1 class="part">Part One</h1>
  <p class="center">The Garden</p>""")

    val CH1 = page("Chapter 1", """  <h2 id="c1">Chapter 1</h2>
  <p>Rose walked into the garden. She saw a rose by the wall, and Rose smiled.</p>
  <p>“Is that you?” called the man from the Rose Inn. He told her Rose was late.</p>
  <p>The sign said ROSE in red letters.<a id="r1" href="notes.xhtml#n1">1</a></p>
  <p>He said "it's fine" and left -- quickly.</p>
  <p></p>
  <p>&#160;</p>
  <p><br/></p>
  <p>Fish &amp; chips for Rose&#8217;s supper.</p>
  <p id="end1">The end of the first chapter.</p>""")

    val CH2 = page("Chapter 2", """  <h2 id="c2">Chapter 2</h2>
  <p>The rain fell on the</p>
  <p>roof all night, and the gar-</p>
  <p>den was soaked by morning.</p>
  <p>A well-</p>
  <p>known story about the garden.</p>
  <p id="mid2">Middle of chapter two.</p>
  <h3 id="s2">A Section</h3>
  <p>Rose went home.</p>""")

    val PART2 = page("Part Two", """  <h1 class="part">Part Two</h1>""")

    val CH3 = page("Chapter 3", """  <h2 id="c3">Chapter 3</h2>
  <p>Back in <a href="ch1.xhtml#end1">the first chapter</a> and <a href="ch2.xhtml">the second</a>.</p>""")

    val NOTES = page("Notes", """  <h2>Notes</h2>
  <p id="n1"><a href="ch1.xhtml#r1">1</a> A note about Rose.</p>""")

    const val CSS = """@font-face {
  font-family: "Fancy";
  src: url(../fonts/fancy.ttf);
}
body { font-family: "Fancy", serif; }
p { text-indent: 1.5em; margin: 0; }
.center { text-align: center; }
"""

    const val NCX = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:1234"/>
    <meta name="dtb:depth" content="2"/>
  </head>
  <docTitle><text>The Garden</text></docTitle>
  <navMap>
    <navPoint id="p1" playOrder="1">
      <navLabel><text>Part One</text></navLabel>
      <content src="text/part1.xhtml"/>
      <navPoint id="p2" playOrder="2">
        <navLabel><text>Chapter 1: Rose</text></navLabel>
        <content src="text/ch1.xhtml"/>
      </navPoint>
      <navPoint id="p3" playOrder="3">
        <navLabel><text>Chapter 2</text></navLabel>
        <content src="text/ch2.xhtml#c2"/>
      </navPoint>
    </navPoint>
    <navPoint id="p4" playOrder="4">
      <navLabel><text>Part Two</text></navLabel>
      <content src="text/part2.xhtml"/>
      <navPoint id="p5" playOrder="5">
        <navLabel><text>Chapter 3</text></navLabel>
        <content src="text/ch3.xhtml"/>
      </navPoint>
    </navPoint>
    <navPoint id="p6" playOrder="6">
      <navLabel><text>Notes</text></navLabel>
      <content src="text/notes.xhtml"/>
    </navPoint>
  </navMap>
</ncx>
"""

    const val OPF = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uid" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>The Garden</dc:title>
    <dc:creator opf:role="aut">A. Writer</dc:creator>
    <dc:identifier id="uid">urn:uuid:1234</dc:identifier>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="styles/style.css" media-type="text/css"/>
    <item id="font" href="fonts/fancy.ttf" media-type="application/vnd.ms-opentype"/>
    <item id="part1" href="text/part1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="part2" href="text/part2.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch3" href="text/ch3.xhtml" media-type="application/xhtml+xml"/>
    <item id="notes" href="text/notes.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="part1"/>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
    <itemref idref="part2"/>
    <itemref idref="ch3"/>
    <itemref idref="notes" linear="no"/>
  </spine>
  <guide>
    <reference type="text" title="Start" href="text/part1.xhtml"/>
  </guide>
</package>
"""

    fun garden(file: File): File = TestEpubs.build(
        file,
        linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to OPF.toByteArray(),
            "OEBPS/toc.ncx" to NCX.toByteArray(),
            "OEBPS/styles/style.css" to CSS.toByteArray(),
            "OEBPS/fonts/fancy.ttf" to ByteArray(64) { it.toByte() },
            "OEBPS/text/part1.xhtml" to PART1.toByteArray(),
            "OEBPS/text/ch1.xhtml" to CH1.toByteArray(),
            "OEBPS/text/ch2.xhtml" to CH2.toByteArray(),
            "OEBPS/text/part2.xhtml" to PART2.toByteArray(),
            "OEBPS/text/ch3.xhtml" to CH3.toByteArray(),
            "OEBPS/text/notes.xhtml" to NOTES.toByteArray(),
        ),
    )

    val NAV = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Contents</h1>
    <ol>
      <li><a href="text/one.xhtml">One</a></li>
      <li><span>Middle</span>
        <ol>
          <li><a href="text/two.xhtml">Two</a></li>
          <li><a href="text/three.xhtml#t">Three</a></li>
        </ol>
      </li>
    </ol>
  </nav>
  <nav epub:type="landmarks" hidden="">
    <ol>
      <li><a epub:type="bodymatter" href="text/one.xhtml">Start</a></li>
    </ol>
  </nav>
</body>
</html>
"""

    fun simplePage(title: String, text: String) = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head><body><h1>$title</h1><p>$text</p></body></html>"""

    val NAV_OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">urn:uuid:5678</dc:identifier>
    <dc:title>Three Parts</dc:title>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/>
    <item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/>
    <item id="three" href="text/three.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="one"/>
    <itemref idref="two"/>
    <itemref idref="three"/>
  </spine>
</package>
"""

    /** A book of [n] short chapters, "Chapter 1" to "Chapter n", listed in its contents. */
    fun long(file: File, n: Int): File {
        val ids = (1..n).map { "c$it" }
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">urn:uuid:9012</dc:identifier>
    <dc:title>Many Chapters</dc:title>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
${ids.joinToString("\n") { "    <item id=\"$it\" href=\"text/$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }}
  </manifest>
  <spine>
${ids.joinToString("\n") { "    <itemref idref=\"$it\"/>" }}
  </spine>
</package>
"""
        val nav = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head>
<body>
  <nav epub:type="toc">
    <ol>
${(1..n).joinToString("\n") { "      <li><a href=\"text/c$it.xhtml\">Chapter $it</a></li>" }}
    </ol>
  </nav>
</body>
</html>
"""
        val entries = linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
        )
        for (k in 1..n) entries["OEBPS/text/c$k.xhtml"] = simplePage("Chapter $k", "Text of chapter $k.").toByteArray()
        return TestEpubs.build(file, entries)
    }

    fun threeParts(file: File): File = TestEpubs.build(
        file,
        linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to NAV_OPF.toByteArray(),
            "OEBPS/nav.xhtml" to NAV.toByteArray(),
            "OEBPS/text/one.xhtml" to simplePage("One", "First.").toByteArray(),
            "OEBPS/text/two.xhtml" to simplePage("Two", "Second.").toByteArray(),
            "OEBPS/text/three.xhtml" to simplePage("Three", "Third <span id=\"t\">here</span>.").toByteArray(),
        ),
    )
}
