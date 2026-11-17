package dev.kolib.app.ocr

/**
 * Reads the hOCR that Tesseract gives for a page (HTML with the page's areas, paragraphs, lines and words,
 * each with its place in a title attribute: "bbox 36 92 96 116; x_wconf 93") into an [OcrPage].
 * https://kba.github.io/hocr-spec/1.2/
 */
object Hocr {

    private val tag = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)\b([^>]*?)(/?)>""")
    private val attribute = Regex("""([a-zA-Z_:-]+)\s*=\s*(?:'([^']*)'|"([^"]*)")""")

    private class Open(val name: String, val cls: String?)

    fun parse(hocr: String, dpi: Int): OcrPage {
        var width = 0
        var height = 0
        val blocks = ArrayList<OcrBlock>()
        var blockBox: Box? = null
        var blockPicture = false
        var paras = ArrayList<OcrParagraph>()
        var paraBox: Box? = null
        var lines = ArrayList<OcrLine>()
        var lineBox: Box? = null
        var lineSize = 0
        var lineKind = OcrLine.Kind.TEXT
        var words = ArrayList<OcrWord>()
        var wordBox: Box? = null
        var wordConf = 0
        var wordStart = -1

        fun endLine() {
            val box = lineBox ?: return
            val kept = words.filter { it.text.isNotBlank() }
            if (kept.isNotEmpty()) lines += OcrLine(box, lineSize.takeIf { it > 0 } ?: box.height, kept, lineKind)
            lineBox = null
            words = ArrayList()
        }
        fun endParagraph() {
            endLine()
            val box = paraBox ?: return
            if (lines.isNotEmpty()) paras += OcrParagraph(box, lines)
            paraBox = null
            lines = ArrayList()
        }
        fun endBlock() {
            endParagraph()
            val box = blockBox ?: return
            if (paras.isNotEmpty() || blockPicture) blocks += OcrBlock(box, paras, blockPicture)
            blockBox = null
            blockPicture = false
            paras = ArrayList()
        }

        val stack = ArrayList<Open>()
        for (m in tag.findAll(hocr)) {
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val selfClosing = m.groupValues[4] == "/"
            if (name !in setOf("div", "p", "span", "strong", "em", "b", "i")) continue
            if (closing) {
                // Up to the element this closes.
                val at = stack.indexOfLast { it.name == name }
                if (at < 0) continue
                for (k in stack.size - 1 downTo at) {
                    val open = stack.removeAt(k)
                    when (open.cls) {
                        "ocrx_word" -> {
                            val box = wordBox
                            if (box != null && wordStart >= 0) {
                                val text = decode(hocr.substring(wordStart, m.range.first).replace(Regex("<[^>]*>"), "")).trim()
                                if (text.isNotEmpty()) words += OcrWord(text, box, wordConf)
                            }
                            wordBox = null
                            wordStart = -1
                        }
                        "ocr_line", "ocr_header", "ocr_caption", "ocr_textfloat" -> endLine()
                        "ocr_par" -> endParagraph()
                        "ocr_carea", "ocr_photo", "ocr_image", "ocr_separator" -> endBlock()
                    }
                }
                continue
            }
            val attrs = attributes(m.groupValues[3])
            val cls = attrs["class"]?.trim()?.split(Regex("\\s+"))?.firstOrNull { it.startsWith("ocr") }
            val props = properties(attrs["title"].orEmpty())
            val box = props["bbox"]?.let { box(it) }
            if (!selfClosing) stack += Open(name, cls)
            when (cls) {
                "ocr_page" -> box?.let {
                    width = it.right
                    height = it.bottom
                }
                "ocr_carea" -> {
                    endBlock()
                    blockBox = box
                }
                "ocr_photo", "ocr_image" -> {
                    endBlock()
                    blockBox = box
                    blockPicture = true
                }
                "ocr_par" -> {
                    endParagraph()
                    if (blockBox == null) blockBox = box
                    paraBox = box
                }
                "ocr_line", "ocr_header", "ocr_caption", "ocr_textfloat" -> {
                    endLine()
                    if (blockBox == null) blockBox = box
                    if (paraBox == null) paraBox = box
                    lineBox = box
                    lineSize = props["x_size"]?.trim()?.toFloatOrNull()?.toInt() ?: 0
                    lineKind = when (cls) {
                        "ocr_header" -> OcrLine.Kind.HEADING
                        "ocr_caption" -> OcrLine.Kind.CAPTION
                        "ocr_textfloat" -> OcrLine.Kind.FLOATING
                        else -> OcrLine.Kind.TEXT
                    }
                }
                "ocrx_word" -> {
                    wordBox = box
                    wordConf = props["x_wconf"]?.trim()?.toFloatOrNull()?.toInt() ?: 0
                    wordStart = m.range.last + 1
                    if (selfClosing) {
                        wordBox = null
                        wordStart = -1
                    }
                }
            }
        }
        endBlock()
        if (width == 0 || height == 0) {
            val all = blocks.map { it.box }
            width = all.maxOfOrNull { it.right } ?: 0
            height = all.maxOfOrNull { it.bottom } ?: 0
        }
        return OcrPage(width, height, dpi, blocks)
    }

    private fun attributes(s: String): Map<String, String> =
        attribute.findAll(s).associate { it.groupValues[1].lowercase() to decode(it.groupValues[2].ifEmpty { it.groupValues[3] }) }

    /** "bbox 1 2 3 4; x_wconf 93" → {bbox: "1 2 3 4", x_wconf: "93"}. */
    private fun properties(title: String): Map<String, String> =
        title.split(';').mapNotNull { part ->
            val p = part.trim()
            val space = p.indexOf(' ')
            if (space <= 0) null else p.substring(0, space) to p.substring(space + 1)
        }.toMap()

    private fun box(s: String): Box? {
        val n = s.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
        return if (n.size >= 4) Box(n[0], n[1], n[2], n[3]) else null
    }

    private val entity = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);""")

    fun decode(s: String): String = if ('&' !in s) s else entity.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                code?.let { String(Character.toChars(it)) } ?: m.value
            }
        }
    }
}
