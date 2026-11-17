package dev.kolib.app.ui.setup

import dev.kolib.app.L10n
import dev.kolib.app.epub.BookFiles
import dev.kolib.app.tr

/**
 * The book the reading setup shows its choices on: one short chapter of a story written for KoLib, in the
 * language KoLib is shown in. It's made when it's wanted and kept nowhere: the reader reads it straight
 * from here, as it reads a book in the library, so what the setup shows is how books will look.
 */
object ExampleBook {
    val title: String get() = tr("The Lamp in the Window")
    val chapterTitle: String get() = tr("The Last Ferry")

    /** The chapter's paragraphs. Words between <i> and </i> are in italics. */
    private fun paragraphs(): List<String> = listOf(
        tr("Every evening, when the last ferry had gone and the harbour lights came on one by one, Mara carried her book up the narrow stairs to the room above the bakery. The window there faced the sea. She lit the lamp, set it on the sill, and opened the book at the page where she had stopped the night before."),
        tr("It was an old book, older than the house. Its cover had faded to the colour of dry sand, and someone long ago had written a name inside it in careful blue ink. Mara did not know whose name it was. She liked to think it belonged to another reader, at another window, on some other shore."),
        tr("“You’ll ruin your eyes,” her grandmother said, as she said every night, and set a cup of tea beside her. But she never told her to stop. She only stood in the doorway for a moment, listening to the pages turn, and then went quietly back down to the ovens."),
        tr("Outside, the tide came in. A fishing boat knocked gently against the quay. Somewhere a dog barked twice and was still. Mara heard none of it. She was far away by then, crossing a desert on a tired horse, with <i>three days of water and no map</i>, and a storm rising behind her."),
        tr("The rider in the book was looking for a well that nobody had seen in a hundred years. Everyone she met told her it was only a story. She went on anyway. Mara understood that. She had never left the island, but she knew what it was to want something that other people called a story."),
        tr("By the time the horse reached the red hills, the tea was cold and the bakery below had gone dark. Mara turned the page. The storm broke. The rider pulled her scarf over her face, put her head down, and walked the horse into the wind, counting her steps so as not to lose her way."),
        tr("“One more chapter,” Mara said aloud, to no one."),
        tr("It was never one more chapter. The lamp burned low, and still she read, until the storm had passed and the rider stood at the top of the last hill. Below her, small and green in all that sand, lay a ring of palm trees. In the middle of it, something shone like a coin."),
        tr("Mara closed the book there. That was the strange thing about reading, she thought. The room had not moved. The lamp was where she had left it. Yet she had been gone for hours, and she had come back with sand in her shoes."),
        tr("She marked her place with a ribbon and held the book shut in both hands. Tomorrow the ferry would run again, the bread would rise, and the town would go about its small business. And tomorrow evening the lamp would be in the window, and the well would be waiting where she had left it."),
        tr("The stars were out over the water now. Mara turned down the lamp, but not all the way. It seemed wrong, somehow, to leave the story in the dark."),
    )

    const val OPF = "OEBPS/content.opf"
    const val CHAPTER = "OEBPS/chapter.xhtml"
    private const val NAV = "OEBPS/nav.xhtml"
    private const val STYLE = "OEBPS/style.css"

    /** The book's files, as an EPUB has them. */
    fun files(): BookFiles {
        val language = L10n.language
        val made = linkedMapOf(
            OPF to opf(language),
            NAV to nav(language),
            STYLE to CSS,
            CHAPTER to chapter(language),
        ).mapValues { it.value.toByteArray(Charsets.UTF_8) }
        return object : BookFiles {
            override val opfPath: String = OPF
            override fun bytes(path: String): ByteArray? = made[path]
            override fun names(): List<String> = made.keys.toList()
        }
    }

    /** Text for a place in the book's files: its &, < and > written out. */
    private fun plain(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** A paragraph's text for the chapter's file: written out as [plain], but for its italics. */
    private fun withItalics(text: String): String = plain(text).replace("&lt;i&gt;", "<i>").replace("&lt;/i&gt;", "</i>")

    private fun opf(language: String) = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id" xml:lang="$language">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="id">kolib-example-book</dc:identifier>
<dc:title>${plain(title)}</dc:title>
<dc:language>$language</dc:language>
<dc:creator>KoLib</dc:creator>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="style" href="style.css" media-type="text/css"/>
<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
</manifest>
<spine>
<itemref idref="chapter"/>
</spine>
</package>
"""

    private fun nav(language: String) = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$language" xml:lang="$language">
<head><title>${plain(title)}</title></head>
<body>
<nav epub:type="toc"><ol><li><a href="chapter.xhtml">${plain(chapterTitle)}</a></li></ol></nav>
</body>
</html>
"""

    /** Set as a printed novel is: paragraphs run on with their first lines indented, the text squared off at both sides. */
    private const val CSS = """body { margin: 0; }
h2, h3 { text-align: center; font-weight: normal; margin: 0; }
h2 { font-size: 1.3em; margin-top: 3em; }
h3 { font-size: 1.15em; font-style: italic; margin-top: 0.8em; margin-bottom: 2.6em; }
p { margin: 0; text-indent: 1.4em; text-align: justify; hyphens: auto; -webkit-hyphens: auto; }
h3 + p { text-indent: 0; }
"""

    private fun chapter(language: String) = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$language" xml:lang="$language">
<head><title>${plain(chapterTitle)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
<section epub:type="chapter">
<h2>I</h2>
<h3>${plain(chapterTitle)}</h3>
""")
        for (p in paragraphs()) append("<p>").append(withItalics(p)).append("</p>\n")
        append("</section>\n</body>\n</html>\n")
    }
}
