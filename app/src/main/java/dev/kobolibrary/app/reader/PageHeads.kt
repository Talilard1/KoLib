package dev.kobolibrary.app.reader

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Which side of a printed book a page is: a left-hand (even) page, or a right-hand (odd) one. */
enum class PageSide { LEFT, RIGHT }

/**
 * One page on screen, as its heading shows it: [slot] 0 is the only page, or the left of two side by side;
 * [number], counted through the whole book (null while the book's pages are being counted); [side], the
 * side of the book it's on, which puts the number at its outside corner and decides the title over it;
 * [opening], the first page of a chapter, which (as in print) has its number but not the title over it.
 */
data class ShownPage(val slot: Int, val number: Int?, val side: PageSide, val opening: Boolean)

/**
 * Where the text of the pages sits on screen, in dp, as the page laid it out: the screen's size, one page
 * or two to a screen, each page's margin at the sides and its text's width, and the space above and below.
 */
data class PageFrame(
    val width: Float,
    val height: Float,
    val perScreen: Int,
    val padX: Float,
    val col: Float,
    val padTop: Float,
    val padBottom: Float,
) {
    /** The left edge of the text of the page in [slot] (0, or 1 for the right-hand of two). */
    fun textLeft(slot: Int): Float = padX + slot * (col + 2 * padX)

    companion object {
        /**
         * Worked out as reader.js lays pages out, for when the page hasn't said (pictures of the reader, or
         * before the chapter is laid out): [width] by [height] dp, with the room the margins need.
         */
        fun of(width: Float, height: Float, s: ReaderSettings, room: MarginRoom): PageFrame {
            val perScreen = when (s.spread.columns) {
                2 -> 2
                1 -> 1
                else -> if (width > height && width >= 880f) 2 else 1
            }
            val padX = (width / perScreen * s.margins.fraction).coerceIn(16f, 140f).roundToInt().toFloat()
            val padY = (height * 0.05f).coerceIn(28f, 64f).roundToInt().toFloat()
            val col = if (perScreen == 2) floor((width - 4 * padX) / 2) else floor(width - 2 * padX)
            return PageFrame(width, height, perScreen, padX, col, max(padY, room.top.toFloat()), max(padY, room.bottom.toFloat()))
        }
    }
}

/** What goes in the page headings: the book's title without what's only there to sort it, and the chapter's. */
object HeadTitles {

    /**
     * A number put in front of a title only so a list of files sorts in order: "01 The Eye of the World",
     * "03 - Lord of Chaos", "[06] Lord of Chaos", "6. Lord of Chaos". The number, any brackets round it, and
     * what separates it from the title.
     */
    private val sortNumber = Regex("""^\s*([\[(#]?)\s*(\d{1,4}(?:\.\d+)?)\s*([\])]?)(\s+[-–—]\s*|\s*[-–—]\s+|[-–—]|\s*_+\s*|\s*[.)]\s+|\s*:\s+|\s+)(?=\S)""")

    /** Series details at the end of a title, in brackets: "(The Wheel of Time, Book 6)", "[Discworld #2]". */
    private val seriesTail = Regex("""\s*[(\[]([^()\[\]]*)[)\]]\s*$""")
    private val seriesWords = Regex("""(?i)\b(book|bk|vol|volume|part|series|novel)\b|#\s*\d""")

    /**
     * [title] as a printed book's heading would have it: without a number in front that's only there to
     * sort the files (one with a nought in front, in brackets, set off with a dash or a full stop, or the
     * same as its number in [seriesIndex]), and without series details in brackets at the end. A number
     * that's part of the title stays: "1984", "2001: A Space Odyssey", "12 Rules for Life".
     */
    fun book(title: String, seriesIndex: Double? = null): String {
        var t = title.trim()
        sortNumber.find(t)?.let { m ->
            val open = m.groupValues[1]
            val number = m.groupValues[2]
            val close = m.groupValues[3]
            val after = m.groupValues[4]
            val rest = t.substring(m.range.last + 1).trim()
            val separator = after.trim()
            val padded = number.length > 1 && number.startsWith("0")
            val sorting = when {
                padded -> true
                // "2001: A Space Odyssey" keeps its number; so does "1-800-...", a dash with no space by it.
                separator == ":" -> false
                separator.isNotEmpty() && after.none { it.isWhitespace() } && separator != "_" -> false
                open.isNotEmpty() || close.isNotEmpty() -> true
                separator.isNotEmpty() -> true
                else -> seriesIndex != null && number.toDoubleOrNull()?.let { abs(it - seriesIndex) < 0.001 } == true
            }
            // Only if a title is left, with a letter (or a number of its own) in it.
            if (sorting && rest.any { it.isLetterOrDigit() } && (open.isEmpty() || close.isNotEmpty() || open == "#")) t = rest
        }
        seriesTail.find(t)?.let { m ->
            val inside = m.groupValues[1]
            val left = t.substring(0, m.range.first).trim()
            if (left.any { it.isLetter() } && seriesWords.containsMatchIn(inside) && inside.any { it.isDigit() }) t = left
        }
        return t
    }

    /** Small capitals for a heading: all of them small when the title is set in capitals already. */
    fun smallCapsFeatures(title: String): String =
        if (title.any { it.isLetter() } && title.none { it.isLowerCase() }) "c2sc, smcp, onum" else "smcp, onum"
}
