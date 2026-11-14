package dev.kobolibrary.app.epub

/** Plain-text views of a book's contents. */
object EpubText {

    private val dropped = Regex("(?is)<(script|style|head)\\b.*?</\\1\\s*>")
    private val tags = Regex("(?s)<[^>]*>")
    private val entity = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    /** The visible text of one XHTML file, with tags removed and character references decoded. */
    fun plainText(xhtml: String): String {
        val noTags = xhtml.replace(dropped, " ").replace(tags, " ")
        return entity.replace(noTags) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)
                e.startsWith("#") -> e.substring(1).toIntOrNull()
                else -> null
            }?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
                ?: when (e) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                    else -> " "
                }
        }
    }

    /**
     * Words in [text]: runs of letters or digits. Chinese, Japanese and Korean characters count one
     * each, as those languages don't put spaces between words.
     */
    fun countWords(text: String): Int {
        var words = 0
        var inWord = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val ideographic = Character.isIdeographic(cp) || isKana(cp) || isHangul(cp)
            when {
                ideographic -> {
                    words++
                    inWord = false
                }
                Character.isLetterOrDigit(cp) -> {
                    if (!inWord) words++
                    inWord = true
                }
                // Apostrophes and hyphens inside words don't split them ("don't", "well-known").
                inWord && (cp == '\''.code || cp == 0x2019 || cp == '-'.code) &&
                    i + 1 < text.length && Character.isLetterOrDigit(text.codePointAt(i + 1)) -> Unit
                else -> inWord = false
            }
            i += Character.charCount(cp)
        }
        return words
    }

    private fun isKana(cp: Int) = cp in 0x3040..0x30FF
    private fun isHangul(cp: Int) = cp in 0xAC00..0xD7AF

    /** Words in the whole book's text files. */
    fun countWords(epub: EpubFile): Int =
        epub.contentDocuments().sumOf { path -> epub.readText(path)?.let { countWords(plainText(it)) } ?: 0 }
}
