package dev.kolib.app.reader

import dev.kolib.app.tr
import dev.kolib.app.trc
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Page colours. Tinted and dark ones recolour the book's text too, so it stays readable. */
enum class ReaderTheme(val label: String, val bg: Long, val fg: Long, val link: Long, val recolor: Boolean, val dark: Boolean) {
    PAPER(tr("Paper"), 0xFFFBF8F1, 0xFF1F1C18, 0xFF1D4F91, false, false),
    WHITE(tr("White"), 0xFFFFFFFF, 0xFF141414, 0xFF1A4FA0, false, false),
    SEPIA(tr("Sepia"), 0xFFF2E6CE, 0xFF3A2D1F, 0xFF7A4A12, true, false),
    SLATE(tr("Slate"), 0xFF3B3D41, 0xFFE6E3DE, 0xFFA9C8FF, true, true),
    NIGHT(tr("Night"), 0xFF1E1D1B, 0xFFD8D2C8, 0xFF94B6EE, true, true),
    BLACK(tr("Black"), 0xFF000000, 0xFFC4C0B8, 0xFF8FB0E6, true, true),
}

enum class ReaderFont(val label: String, val css: String) {
    BOOK(tr("Book default"), ""),
    LITERATA("Literata", "'Literata', serif"),
    GARAMOND("Garamond", "'EB Garamond', serif"),
    SERIF("Noto Serif", "'Noto Serif', serif"),
    SANS("Roboto", "Roboto, 'Noto Sans', sans-serif"),
    HYPERLEGIBLE("Atkinson", "'Atkinson Hyperlegible', sans-serif"),
    DYSLEXIC("OpenDyslexic", "'OpenDyslexic', sans-serif"),
}

enum class ReaderMargins(val label: String, val fraction: Float) {
    NARROW(tr("Narrow"), 0.035f),
    MEDIUM(tr("Medium"), 0.065f),
    WIDE(tr("Wide"), 0.11f),
}

enum class ReaderAlign(val label: String, val js: String) {
    BOOK(tr("Book default"), "book"),
    LEFT(tr("Left"), "left"),
    JUSTIFY(tr("Justified"), "justify"),
}

enum class ReaderHyphens(val label: String, val js: String) {
    BOOK(tr("Book default"), "book"),
    ON(tr("On"), "on"),
    OFF(tr("Off"), "off"),
}

enum class ReaderSpread(val label: String, val columns: Int) {
    AUTO(tr("Two in landscape"), 0),
    ONE(tr("Always one"), 1),
    TWO(tr("Always two"), 2),
}

/** Which way the screen faces while reading. */
enum class ReaderRotation(val label: String, val help: String) {
    AUTO(tr("Automatic"), tr("Follows the device.")),
    PORTRAIT(tr("Portrait"), tr("Locked upright.")),
    LANDSCAPE(tr("Landscape"), tr("Locked sideways.")),
}

/**
 * Where the page goes beside a camera in the screen (a punch hole, a notch), while reading full screen: below it, with
 * the camera in a black strip; round it, the text keeping clear of it; or round it with the text going under it too.
 */
enum class CameraRoom(val label: String, val help: String) {
    BELOW(trc("the page and the camera", "Below it"), tr("A black strip hides the camera, and the page starts below it.")),
    ROUND(trc("the page and the camera", "Round it"), tr("The page fills the screen, and its text keeps clear of the camera.")),
    UNDER(trc("the page and the camera", "Under it"), tr("The page fills the screen, and its text can go under the camera. What's shown round the page still keeps clear of it.")),
}

/** What taps on the page do. The middle always brings up the controls. */
enum class TapZones(val label: String, val js: String, val help: String) {
    SIDES(tr("Left goes back, right goes forward"), "sides", ""),
    FORWARD(tr("Both sides go forward"), "forward", tr("Handy for reading with one hand. Swipe right to go back.")),
    TOP_BOTTOM(tr("Top goes back, bottom goes forward"), "topbottom", tr("The left and right sides work too.")),
    OFF(tr("Off"), "none", tr("Swipe, or use the volume buttons, to turn pages.")),
}

/** How far in from the screen's edges a tap turns the page. */
enum class TapWidth(val label: String, val fraction: Float) {
    NARROW(tr("Small"), 0.12f),
    MEDIUM(tr("Medium"), 0.2f),
    WIDE(tr("Large"), 0.28f),
}

/** Which side of the page a swipe up or down sets the brightness on (or neither). */
enum class LevelSide(val label: String) {
    LEFT(tr("Left")),
    RIGHT(tr("Right")),
    BOTH(tr("Both")),
    OFF(tr("Off")),
}

/**
 * Where up the side a swipe that sets the brightness has to start: anywhere, or in its top, middle or bottom third
 * (kept clear of where a thumb rests to scroll). [from] and [to] are how far down the page that is.
 */
enum class LevelStart(val label: String, val from: Float, val to: Float) {
    ANYWHERE(tr("Anywhere"), 0f, 1f),
    TOP(tr("Top"), 0f, 1f / 3),
    MIDDLE(tr("Middle"), 1f / 3, 2f / 3),
    BOTTOM(tr("Bottom"), 2f / 3, 1f);

    /** A touch [down] of the way down the page (0 the top, 1 the foot) is in it. */
    fun covers(down: Float): Boolean = down >= from && (down < to || to >= 1f)
}

/** What's done with pictures so they sit on the page's colour rather than in a white box. */
enum class PictureBlend(val label: String, val js: String, val help: String) {
    OFF(tr("Off"), "off", tr("Pictures aren't changed.")),
    ORNAMENTS(tr("Decorations"), "ornaments", tr("Black-and-white decorations and charts match the page colour, turning light on dark pages.")),
    ALL(tr("All pictures"), "all", tr("Every picture matches the page colour. Photos look inverted on dark pages.")),
}

/** The thin line along the foot of the page that grows as you read, as a Kobo has. */
enum class ProgressLine(val label: String) {
    BOOK(tr("Book")),
    CHAPTER(tr("Chapter")),
    OFF(tr("Off")),
}

/**
 * How strongly the progress bar (and autoscroll's bar) is drawn: in the text's colour, or dimmed toward
 * the page's, which wears an OLED screen less where the bar always is.
 */
enum class BarShade(val label: String, val strength: Float) {
    TEXT(tr("Same as text"), 1f),
    DIM(tr("Dimmed"), 0.35f),
}

/** How a chapter is laid out: as pages to turn, or as one long page to scroll down. */
enum class ReaderFlow(val label: String, val js: String) {
    PAGES(dev.kolib.app.trc("reading mode", "Pages"), "pages"),
    SCROLL(tr("Scrolling"), "scroll"),
}

/** What autoscroll does when it gets to the end of a chapter. */
enum class AutoEnd(val label: String, val seconds: Int) {
    GO_ON(tr("Go straight to the next chapter"), 0),
    WAIT(tr("Wait 3 seconds, then continue"), 3),
    WAIT_LONG(tr("Wait 10 seconds, then continue"), 10),
    STOP(tr("Stop"), -1),
}

/** What autoscroll does after you touch the page (opening the controls or a menu always pauses it). */
enum class AutoResume(val label: String, val seconds: Int) {
    KEEP_GOING(tr("Keep going"), 0),
    SOON(tr("Pause for 2 seconds"), 2),
    LATER(tr("Pause for 5 seconds"), 5),
    STOP(tr("Stop"), -1),
}

/** How a page turns. Curl and Fade are drawn by the app over the page; Slide is the page itself moving. */
enum class PageTurn(val label: String, val js: String) {
    SLIDE(tr("Slide"), "slide"),
    CURL(tr("Curl"), "curl"),
    /** The new page swept in from the top corner, across to the opposite one. */
    SWEEP(tr("Sweep"), "sweep"),
    FADE(tr("Fade"), "fade"),
    /** Stored under its old name. */
    INSTANT(tr("Instant"), "none"),
}

/** A place in the page's margins for something to show while reading, or nowhere. */
enum class MarginSpot(val label: String, val top: Boolean, val align: Int) {
    OFF(tr("Off"), false, 0),
    TOP_LEFT(tr("Top left"), true, -1),
    TOP_CENTER(tr("Top centre"), true, 0),
    TOP_RIGHT(tr("Top right"), true, 1),
    BOTTOM_LEFT(tr("Bottom left"), false, -1),
    BOTTOM_CENTER(tr("Bottom centre"), false, 0),
    BOTTOM_RIGHT(tr("Bottom right"), false, 1);

    /** The same place at the other end of the page (top for bottom, bottom for top). */
    fun otherEnd(): MarginSpot = when (this) {
        OFF -> OFF
        TOP_LEFT -> BOTTOM_LEFT
        TOP_CENTER -> BOTTOM_CENTER
        TOP_RIGHT -> BOTTOM_RIGHT
        BOTTOM_LEFT -> TOP_LEFT
        BOTTOM_CENTER -> TOP_CENTER
        BOTTOM_RIGHT -> TOP_RIGHT
    }
}

/**
 * Page headings as a printed book has them, over (or under) each page: the page's number at its outside
 * corner, with the book's title on left-hand pages and the chapter's on right-hand ones. Two pages side by
 * side are a left and a right; one page at a time, even pages are left-hand pages and odd ones right-hand.
 */
enum class RunningHeads(val label: String, val top: Boolean) {
    TOP(tr("Top"), true),
    BOTTOM(tr("Bottom"), false),
    OFF(tr("Off"), false),
}

/** Where a page heading's title sits: in the middle of its page, or nearer the spine, as some printed books have it. */
enum class HeadTitlePlace(val label: String) {
    CENTRED(tr("Centred")),
    SPINE(tr("Near the spine")),
}

/** How big the page headings' type is: as before, smaller than the text; as a printed book has it; or bigger. */
enum class HeadSize(val label: String, val ofText: Float) {
    SMALL(tr("Smaller"), 0f),
    PRINT(tr("Standard"), 0.92f),
    LARGE(tr("Larger"), 1.15f),
}

/** The space between the page headings and the text: as before, a line's worth as in print, or more. */
enum class HeadGap(val label: String, val lines: Float) {
    CLOSE(trc("space under headings", "Less"), 0f),
    LINE(tr("One line"), 1f),
    MORE(trc("space under headings", "More"), 1.6f),
}

/** In a book that holds several (an omnibus, a box set), the title over left-hand pages and in the margins. */
enum class CollectionTitle(val label: String) {
    BOOK(tr("Current book")),
    COLLECTION(tr("Whole omnibus")),
}

/** What the margins can show while reading, in the order they're shown when they share a place. */
enum class MarginItem(val label: String, val example: String, val default: MarginSpot) {
    BOOK(tr("Book title"), tr("The Eye of the World"), MarginSpot.OFF),
    // The chapter's title and the page number are in the page headings, which are on to start with.
    CHAPTER(tr("Chapter title"), tr("1 The Hook"), MarginSpot.OFF),
    PAGE(tr("Page number in chapter"), tr("Page 3 of 12"), MarginSpot.OFF),
    BOOK_PAGE(tr("Page number in book"), tr("Page 45 of 380"), MarginSpot.OFF),
    PAGES_LEFT(tr("Pages left in chapter"), tr("9 pages left"), MarginSpot.OFF),
    TIME_LEFT(tr("Reading time left in chapter"), tr("12 min left"), MarginSpot.OFF),
    CLOCK(tr("Clock"), "3:42", MarginSpot.BOTTOM_CENTER),
    BATTERY(tr("Battery"), "70%", MarginSpot.BOTTOM_CENTER),
    PERCENT(tr("Percent of book read"), "34%", MarginSpot.OFF);

    /** About how wide it shows, in dp: to tell whether a camera in the top of the screen would be over it. A title takes what room it's given. */
    val room: Float get() = when (this) {
        BOOK, CHAPTER -> 1000f
        PAGE -> 70f
        BOOK_PAGE -> 92f
        PAGES_LEFT -> 110f
        TIME_LEFT -> 150f
        CLOCK -> 58f
        BATTERY -> 46f
        PERCENT -> 34f
    }

    companion object {
        val DEFAULTS: Map<MarginItem, MarginSpot> = entries.associateWith { it.default }

        /** As stored: "CHAPTER=TOP_CENTER,PAGE=BOTTOM_LEFT,..." (anything not named keeps its usual place). */
        fun parse(stored: String?): Map<MarginItem, MarginSpot> {
            val out = DEFAULTS.toMutableMap()
            stored?.split(',')?.forEach { pair ->
                val item = entries.firstOrNull { it.name == pair.substringBefore('=') } ?: return@forEach
                val spot = MarginSpot.entries.firstOrNull { it.name == pair.substringAfter('=') } ?: return@forEach
                out[item] = spot
            }
            return out
        }

        fun write(map: Map<MarginItem, MarginSpot>): String = entries.joinToString(",") { "${it.name}=${(map[it] ?: it.default).name}" }

        /** Some of them, as stored: "CLOCK,BATTERY". */
        fun writeSet(items: Set<MarginItem>): String = entries.filter { it in items }.joinToString(",") { it.name }

        fun parseSet(stored: String?): Set<MarginItem> =
            stored?.split(',')?.mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }?.toSet().orEmpty()

        /** What the page headings show anyway: shown in the margins as well, they'd be there twice. */
        val IN_HEADINGS = listOf(BOOK, CHAPTER, PAGE, BOOK_PAGE)
    }
}

/**
 * The camera's cutouts (a punch hole, a notch) in the screen the reader fills, in dp: the screen's size, the
 * one at the top and the one at the bottom, and how far in from the sides one in a side comes. What's shown
 * round the page keeps clear of them: beside the camera where there's room, below it otherwise.
 */
data class Cutouts(val width: Float = 0f, val height: Float = 0f, val top: Span? = null, val bottom: Span? = null, val side: Float = 0f) {
    /** A cutout in the top or bottom edge: from [from] to [to] across the screen, and [depth] deep. */
    data class Span(val from: Float, val to: Float, val depth: Float) {
        fun over(a: Float, b: Float): Boolean = to > a && from < b
    }

    /** There's a camera in the middle of the top of the screen: where a heading, or the time in the middle, would be. */
    val topMiddle: Boolean get() = top != null && (width <= 0f || top.over(width * 0.38f, width * 0.62f))

    companion object {
        val NONE = Cutouts()

        /** A cutout [depth] dp deep at the top, and nothing known of where along it: as if it were all the way across. */
        fun top(depth: Int): Cutouts = if (depth <= 0) NONE else Cutouts(top = Span(0f, Float.MAX_VALUE, depth.toFloat()))
    }
}

/**
 * Room kept above and below the text for what shows in the margins, in dp: the text starts (and ends) at
 * least this far from the edge, so a heading or the time never sits on it, or under a camera's cutout.
 */
data class MarginRoom(val top: Int = 0, val bottom: Int = 0) {
    companion object {
        /** How far down the top row sits, with nothing in the way (where the margins' top row has always been). */
        const val ROW_TOP = 12
        /** A row's height, for its line of small type. */
        const val ROW_HEIGHT = 18
        /** The space between a row and the text. */
        const val GAP = 10
    }
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val font: ReaderFont = ReaderFont.GARAMOND,
    /** A font of the user's own (see [UserFonts]) to read in, by its family's name; empty for [font]. */
    val userFont: String = "",
    /** Text size, 1 = as the book sets it. */
    val fontScale: Float = 1.45f,
    /** Line spacing; 0 = as the book sets it. */
    val lineSpacing: Float = 0f,
    val margins: ReaderMargins = ReaderMargins.MEDIUM,
    val align: ReaderAlign = ReaderAlign.BOOK,
    val hyphens: ReaderHyphens = ReaderHyphens.BOOK,
    val spread: ReaderSpread = ReaderSpread.AUTO,
    val pageTurn: PageTurn = PageTurn.CURL,
    val volumeKeys: Boolean = false,
    /**
     * KoLib's back gesture: a swipe in from either side goes back, whatever's open (it closes that, or leaves the book).
     * Off, a swipe in from the left opens the contents and from the right the reading settings, and it goes back only
     * with something open. (Android's own back swipe is never used in the reader.)
     */
    val edgeBack: Boolean = false,
    /**
     * The text keeps out from under a camera in the screen (scrolling, it fades out going by behind it). Off, it has
     * the whole screen; what's shown in the margins still keeps clear of the camera.
     */
    val clearOfCamera: Boolean = true,
    /** The status bar hides while reading. Off, it stays, and the page sits below it. */
    val fullscreen: Boolean = true,
    /**
     * Full screen, the page goes round a camera in the screen (keeping clear of it as [clearOfCamera] says). Off, it
     * starts below it (or beside one in a side), the camera in a black strip.
     */
    val roundCamera: Boolean = true,
    /** Where the chapter, page number, time and the rest show in the margins (or don't). */
    val marginItems: Map<MarginItem, MarginSpot> = MarginItem.DEFAULTS,
    /** The margins' items shown in plain type, as they were before: the rest are in the page headings' type. */
    val plainMargins: Set<MarginItem> = PLAIN_TO_START,
    /** Page headings as a printed book has them: at the top of each page, the bottom, or none. */
    val runningHeads: RunningHeads = RunningHeads.TOP,
    /** Where the page headings' titles sit along the page. */
    val headTitlePlace: HeadTitlePlace = HeadTitlePlace.SPINE,
    /** How big the page headings are. */
    val headSize: HeadSize = HeadSize.PRINT,
    /** The space between the page headings and the text. */
    val headGap: HeadGap = HeadGap.LINE,
    /** In a book of several books, whose title the headings and margins show: the one being read, or the whole book's. */
    val collectionTitle: CollectionTitle = CollectionTitle.BOOK,
    /** The screen stays on while reading. */
    val keepScreenOn: Boolean = true,
    /** Screen brightness while reading, 0 to 1; below 0, the tablet's own. */
    val brightness: Float = -1f,
    /** Each book opens at the tablet's own brightness (changes while reading last until it's closed). */
    val brightnessFromScreen: Boolean = true,
    /** The colour and style new highlights get (the last ones chosen). */
    val highlightColor: HighlightColor = HighlightColor.YELLOW,
    val highlightStyle: HighlightStyle = HighlightStyle.HIGHLIGHT,
    /** The app words are looked up in ("package/activity"), once chosen. */
    val dictionary: String? = null,
    /** Darker than the screen goes: a shade over the page, 0 to 0.7. */
    val dim: Float = 0f,
    val rotation: ReaderRotation = ReaderRotation.AUTO,
    val taps: TapZones = TapZones.SIDES,
    val tapWidth: TapWidth = TapWidth.NARROW,
    val pictures: PictureBlend = PictureBlend.ORNAMENTS,
    /** Links in a colour of their own; off, they look like the text around them (and still work). */
    val colourLinks: Boolean = false,
    /** A chapter's opening page (its number, an ornament) shown with its text, when a book keeps it in a file of its own. */
    val joinOpenings: Boolean = true,
    /** Two pages side by side, each chapter starts on a right-hand page, with the left-hand one blank, as in print. */
    val rectoOpenings: Boolean = true,
    /** A chapter's number and name set as print has them (big spaced capitals, then italics), whatever the book says. */
    val printHeads: Boolean = true,
    /** The first letter of each chapter dropped three lines, unless the book has a big first letter of its own. */
    val dropCaps: Boolean = true,
    /** Pages to turn, or one long page to scroll. */
    val flow: ReaderFlow = ReaderFlow.PAGES,
    /** Autoscroll's speed, in words a minute. */
    val autoWpm: Int = 300,
    val autoEnd: AutoEnd = AutoEnd.STOP,
    val autoResume: AutoResume = AutoResume.KEEP_GOING,
    /** How much of the book (or the chapter) has been read, as a line along the foot of the page. */
    val progressLine: ProgressLine = ProgressLine.BOOK,
    /** How strongly that line, and autoscroll's, is drawn. */
    val barShade: BarShade = BarShade.DIM,
    /** Which side a swipe up or down sets the brightness on (or neither). */
    val levelSide: LevelSide = LevelSide.LEFT,
    /** Where up that side such a swipe has to start. */
    val levelStart: LevelStart = LevelStart.ANYWHERE,
    /**
     * The look (see [withLookOf]) kept for the other of pages and scrolling, as [lookJson] writes it: each has one of
     * its own, brought back when it's gone back to (see [withFlow]). Null till the other has been used.
     */
    val otherLook: String? = null,
    /**
     * Scrolling, a pull on past the end of a chapter (or back past its start) has the edge of the page rise in a hill to
     * meet the ring that fills with it; off, the ring shows on its own.
     */
    val pullHill: Boolean = true,
) {
    val scrolling: Boolean get() = flow == ReaderFlow.SCROLL

    /** A swipe in from a side, with nothing open, opens the panel on that side (see [edgeBack]). */
    val edgePanels: Boolean get() = !edgeBack

    /**
     * Gone over to pages or to scrolling ([to]): its own look comes back (the first time, it starts as this one), and
     * this one is kept for when it's come back to. So each is set up as it's liked, and changing one leaves the other.
     */
    fun withFlow(to: ReaderFlow): ReaderSettings {
        if (to == flow) return this
        val mine = lookJson().apply { remove(OTHER_LOOK) }.toString()
        val theirs = otherLook?.let { runCatching { JSONObject(it) }.getOrNull() }
        val back = if (theirs != null) withLook(this, theirs) else this
        return back.copy(flow = to, otherLook = mine)
    }

    /**
     * A swipe up or down that starts at ([x], [y]) on a page [w] wide and [h] high sets the brightness: it's in the
     * strip down a side that's set for it ([LEVEL_STRIP] of the page's width), in the part of it it's to start in.
     */
    fun startsLevelSwipe(x: Float, y: Float, w: Float, h: Float): Boolean {
        if (w <= 0f || h <= 0f) return false
        val strip = w * LEVEL_STRIP
        val onSide = when (levelSide) {
            LevelSide.LEFT -> x < strip
            LevelSide.RIGHT -> x > w - strip
            LevelSide.BOTH -> x < strip || x > w - strip
            LevelSide.OFF -> false
        }
        return onSide && levelStart.covers(y / h)
    }

    /** Where the page goes beside a camera in the screen, while reading full screen. */
    val cameraRoom: CameraRoom get() = when {
        !roundCamera -> CameraRoom.BELOW
        clearOfCamera -> CameraRoom.ROUND
        else -> CameraRoom.UNDER
    }

    fun withCameraRoom(room: CameraRoom): ReaderSettings = when (room) {
        CameraRoom.BELOW -> copy(roundCamera = false)
        CameraRoom.ROUND -> copy(roundCamera = true, clearOfCamera = true)
        CameraRoom.UNDER -> copy(roundCamera = true, clearOfCamera = false)
    }

    /**
     * The book's title shows somewhere: in the page headings (over left-hand pages, or scrolling as the chapter's
     * stands in for it) or in the margins. Only then does it matter which title a book of several shows.
     */
    val bookTitleShown: Boolean get() = runningHeads != RunningHeads.OFF || spotOf(MarginItem.BOOK) != MarginSpot.OFF

    /**
     * The bars' colour, as one solid colour: the text's, or part way from the page's colour to it. Solid,
     * so where a bar's parts overlap (its hairline under the thicker part) the overlap doesn't show.
     */
    val barColor: Long get() {
        val k = barShade.strength
        fun mix(shift: Int): Long {
            val bg = (theme.bg shr shift) and 0xFF
            val fg = (theme.fg shr shift) and 0xFF
            return Math.round(bg + (fg - bg) * k.toDouble()).coerceIn(0L, 255L)
        }
        return (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun spotOf(item: MarginItem): MarginSpot = marginItems[item] ?: item.default

    /** Page headings are shown: they're on, and there are pages (not one long scroll). */
    val headsShown: Boolean get() = runningHeads != RunningHeads.OFF && !scrolling

    /**
     * Where [item] shows: where it's been put, or, if the page headings are at that end of the page, the
     * same place at the other end.
     */
    fun shownSpotOf(item: MarginItem): MarginSpot {
        val spot = spotOf(item)
        return if (headsShown && spot != MarginSpot.OFF && spot.top == runningHeads.top) spot.otherEnd() else spot
    }

    /**
     * With the page headings at [h]. Turned on, what they show (the titles and page numbers) is taken out
     * of the margins, so it isn't there twice, and anything at their end of the page moves to the other
     * end. Turned off, nothing is put in their place: the chapter's title and the page numbers can be
     * shown in the margins, but they're off until they're asked for.
     */
    fun withHeads(h: RunningHeads): ReaderSettings {
        if (h == runningHeads) return this
        val items = marginItems.toMutableMap()
        if (h != RunningHeads.OFF) {
            MarginItem.IN_HEADINGS.forEach { items[it] = MarginSpot.OFF }
            for ((item, spot) in items.toMap()) if (spot != MarginSpot.OFF && spot.top == h.top) items[item] = spot.otherEnd()
        }
        return copy(runningHeads = h, marginItems = items)
    }

    /** The size of the text's type, about, in dp: the book's usual 16, at the size chosen. */
    private val textDp: Float get() = 16f * fontScale

    /** The page headings' type size, in sp. */
    val headFontSize: Float
        get() = if (headSize == HeadSize.SMALL) HEAD_SMALL else (textDp * headSize.ofText).coerceIn(HEAD_SMALL, 34f)

    /** The height of the page headings' row, in dp. */
    val headRow: Int get() = maxOf(MarginRoom.ROW_HEIGHT, Math.round(headFontSize * 1.35f))

    /** The space between the page headings and the text, in dp: as close as before, or a line of the text (or more). */
    val headGapDp: Int
        get() = if (headGap == HeadGap.CLOSE) MarginRoom.GAP
        else maxOf(MarginRoom.GAP, Math.round(textDp * (if (lineSpacing > 0f) lineSpacing else 1.45f) * headGap.lines))

    /** One page to a screen or two, on a screen [width] by [height] dp: as reader.js lays pages out. */
    fun pagesAcross(width: Float, height: Float): Int = when {
        scrolling -> 1
        spread.columns == 2 -> 2
        spread.columns == 1 -> 1
        else -> if (width > height && width >= 880f) 2 else 1
    }

    /** About how wide what's shown at [spot] is, in dp, on a screen [width] across: 0 with nothing there. */
    internal fun spotWidth(spot: MarginSpot, width: Float): Float {
        val items = MarginItem.entries.filter { shownSpotOf(it) == spot }
        if (items.isEmpty()) return 0f
        val all = items.sumOf { it.room.toDouble() }.toFloat() + (items.size - 1) * 22f
        return if (width <= 0f) all else minOf(all, width * (if (spot.align == 0) 0.4f else 0.28f))
    }

    /**
     * How far in from its edge of the screen what's at a top corner ([spot]) starts, in dp: where it always
     * has, or further in to clear a camera in that corner, when there's room before the middle. Null when the
     * camera can't be cleared that way (or [spot] is the middle, and the camera's over it).
     */
    internal fun topInset(spot: MarginSpot, cut: Cutouts): Float? {
        val w = cut.width
        val hole = cut.top ?: return SIDE
        val own = spotWidth(spot, w)
        if (own == 0f) return SIDE
        if (w <= 0f) return null
        val middle = spotWidth(MarginSpot.TOP_CENTER, w)
        return when (spot.align) {
            0 -> if (hole.over(w / 2 - own / 2 - CLEAR, w / 2 + own / 2 + CLEAR)) null else SIDE
            -1 -> {
                if (!hole.over(SIDE - CLEAR, SIDE + own + CLEAR)) return SIDE
                val start = hole.to + CLEAR
                val limit = if (middle > 0f) w / 2 - middle / 2 - CLEAR else w / 2
                if (hole.from < w * 0.3f && start + own <= limit) start else null
            }
            else -> {
                if (!hole.over(w - SIDE - own - CLEAR, w - SIDE + CLEAR)) return SIDE
                val end = hole.from - CLEAR
                val limit = if (middle > 0f) w / 2 + middle / 2 + CLEAR else w / 2
                if (hole.to > w * 0.7f && end - own >= limit) w - end else null
            }
        }
    }

    /**
     * Whether a camera in the top of the screen would be over something shown along the top: a page heading
     * (over the text of a page; not when it's over the gap between two pages), the chapter's title when
     * scrolling, or what's in the margins there. Then the top row goes below the camera; otherwise it stays
     * where it is, beside it.
     */
    fun cameraInTheWay(cut: Cutouts): Boolean {
        val hole = cut.top ?: return false
        val w = cut.width
        val items = MarginSpot.entries.any { it.top && spotWidth(it, w) > 0f }
        // Nothing known of where along the top it is: it's in the way of whatever's there.
        if (w <= 0f) return items || (runningHeads == RunningHeads.TOP)
        if (scrolling) {
            if (runningHeads == RunningHeads.TOP && hole.over(w * 0.2f, w * 0.8f)) return true
        } else if (headsShown && runningHeads.top && overText(hole, w, cut.height)) return true
        return MarginSpot.entries.any { it.top && spotWidth(it, w) > 0f && topInset(it, cut) == null }
    }

    /** Whether a cutout in the top or bottom edge is over the text of a page (not just the gap between two, or the margins). */
    private fun overText(hole: Cutouts.Span, w: Float, h: Float): Boolean {
        if (w <= 0f) return true
        val across = pagesAcross(w, h)
        val padX = Math.round((w / across * margins.fraction).coerceIn(16f, 140f)).toFloat()
        val col = if (across == 2) Math.floor(((w - 4 * padX) / 2).toDouble()).toFloat() else w - 2 * padX
        return (0 until across).any { slot ->
            val left = padX + slot * (col + 2 * padX)
            hole.over(left - CLEAR, left + col + CLEAR)
        }
    }

    /**
     * The room the margins need above and below the text, with the camera's cutouts where [cut] has them:
     * a row at the top sits below a camera that would be over it, and the text starts below one that's
     * over it. None when scrolling, which has bands of its own.
     * With the page headings at one end, the room there is for them and the space under them; at the
     * other end, there's room for a chapter's first page to have its number there, as in print.
     */
    fun marginRoom(cut: Cutouts): MarginRoom {
        if (scrolling) return MarginRoom()
        val atTop = (headsShown && runningHeads.top) || MarginItem.entries.any { shownSpotOf(it).top }
        val atBottom = (headsShown && !runningHeads.top) || MarginItem.entries.any { shownSpotOf(it).let { s -> s != MarginSpot.OFF && !s.top } }
        val row = MarginRoom.ROW_HEIGHT + MarginRoom.GAP
        val headRoom = headRow + headGapDp
        val top = when {
            headsShown && runningHeads.top -> topRowAt(cut) + headRoom
            atTop -> topRowAt(cut) + row
            else -> 0
        }
        val bottom = when {
            headsShown && !runningHeads.top -> bottomRowAt(cut) + headRoom
            // The page number of a chapter's first page goes at the foot too, as big as the headings'.
            atBottom && headsShown -> bottomRowAt(cut) + maxOf(row, headRow + MarginRoom.GAP)
            atBottom -> bottomRowAt(cut) + row
            else -> 0
        }
        // The text itself keeps out from under a camera, whatever's in the margins (unless it's to have the whole screen).
        val under = cut.top?.takeIf { clearOfCamera && overText(it, cut.width, cut.height) }?.let { Math.round(it.depth) + UNDER } ?: 0
        val over = cut.bottom?.takeIf { clearOfCamera && overText(it, cut.width, cut.height) }?.let { Math.round(it.depth) + UNDER } ?: 0
        return MarginRoom(top = maxOf(top, under), bottom = maxOf(bottom, over))
    }

    /** The same with a cutout [cutoutTop] dp deep at the top of the screen, and nothing known of where along it. */
    fun marginRoom(cutoutTop: Int = 0): MarginRoom = marginRoom(Cutouts.top(cutoutTop))

    /**
     * The room a page that's only a picture (a cover, a plate, a map) leaves for the margins: the rows of what's shown
     * in them, but not the page headings, which aren't shown over a picture. Unlike [marginRoom], it doesn't change
     * with the text size, so neither does the picture.
     */
    fun pictureRoom(cut: Cutouts): MarginRoom {
        if (scrolling) return MarginRoom()
        val row = MarginRoom.ROW_HEIGHT + MarginRoom.GAP
        val atTop = MarginItem.entries.any { shownSpotOf(it).top }
        val atBottom = MarginItem.entries.any { shownSpotOf(it).let { s -> s != MarginSpot.OFF && !s.top } }
        val top = if (atTop) topRowAt(cut) + row else 0
        val bottom = if (atBottom) bottomRowAt(cut) + row else 0
        val under = cut.top?.takeIf { clearOfCamera && overText(it, cut.width, cut.height) }?.let { Math.round(it.depth) + UNDER } ?: 0
        val over = cut.bottom?.takeIf { clearOfCamera && overText(it, cut.width, cut.height) }?.let { Math.round(it.depth) + UNDER } ?: 0
        return MarginRoom(top = maxOf(top, under), bottom = maxOf(bottom, over))
    }

    /** How far down the top row of the margins sits: where it always has, or below a camera that would be over it. */
    fun topRowAt(cut: Cutouts): Int = if (cameraInTheWay(cut)) maxOf(MarginRoom.ROW_TOP, Math.round(cut.top!!.depth) + 2) else MarginRoom.ROW_TOP

    fun topRowAt(cutoutTop: Int): Int = maxOf(MarginRoom.ROW_TOP, if (cutoutTop > 0) cutoutTop + 2 else 0)

    /** How far up the bottom row of the margins sits: above the progress bar, when there is one, and above a cutout there. */
    fun bottomRowAt(cut: Cutouts = Cutouts.NONE): Int = maxOf(if (progressLine == ProgressLine.OFF) 5 else 10, cut.bottom?.let { Math.round(it.depth) + 2 } ?: 0)

    /** Nobody has moved anything round the page: the headings and the margins are as the reader starts with them. */
    val stockArrangement: Boolean get() = runningHeads == RunningHeads.TOP && marginItems == MarginItem.DEFAULTS

    /**
     * For a screen with a camera in the middle of its top, showing one page at a time (or scrolling): the
     * page headings at the foot of the page, and the time and the battery in the top corners, either side
     * of the camera. Nothing then has to move down from under it, and the text keeps its room.
     */
    fun aroundCamera(): ReaderSettings = withHeads(RunningHeads.BOTTOM).withClock(true)

    /** The time or the battery is shown somewhere. */
    val clockShown: Boolean get() = spotOf(MarginItem.CLOCK) != MarginSpot.OFF || spotOf(MarginItem.BATTERY) != MarginSpot.OFF

    /**
     * With the time and the battery shown where they sit best with the page headings where they are (in
     * the top corners when the headings are at the foot of the page, together at the foot otherwise), or
     * not shown.
     */
    fun withClock(on: Boolean): ReaderSettings {
        val (clock, battery) = when {
            !on -> MarginSpot.OFF to MarginSpot.OFF
            runningHeads == RunningHeads.BOTTOM -> MarginSpot.TOP_LEFT to MarginSpot.TOP_RIGHT
            else -> MarginSpot.BOTTOM_CENTER to MarginSpot.BOTTOM_CENTER
        }
        return copy(marginItems = marginItems + (MarginItem.CLOCK to clock) + (MarginItem.BATTERY to battery))
    }

    /**
     * Brightness as one number, for the slider and the swipe down the left edge: 1 is the brightest,
     * 0 the screen's dimmest, and below that the page is shaded darker still. Null: the tablet's own.
     */
    val level: Float? get() = when {
        dim > 0f -> -dim
        brightness < 0f -> null
        else -> brightness
    }

    fun withLevel(v: Float): ReaderSettings =
        if (v >= 0.01f) copy(brightness = v.coerceAtMost(1f), dim = 0f)
        else copy(brightness = 0.01f, dim = (-v).coerceIn(0f, MAX_DIM))

    /**
     * Just what the page itself shows: the rest (keys, brightness, highlight colour...) changes nothing
     * there. When scrolling, the margins do: their items show over the text, in bands it scrolls under.
     * (Turning pages, the room kept for the margins does too: see [marginRoom].)
     */
    /**
     * These settings with [book]'s look in place of theirs: how its text is set (font, size, spacing,
     * margins, alignment, hyphens), how its pages are laid out (side by side, scrolling, the headings and
     * what shows round the page, pictures, links) and how its chapters open. A book keeps a look of its own
     * once one of those is changed while it's read. The page colour, the brightness, the controls, the page
     * turn and autoscroll are the same for every book.
     */
    fun withLookOf(book: ReaderSettings): ReaderSettings = copy(
        font = book.font, userFont = book.userFont, fontScale = book.fontScale, lineSpacing = book.lineSpacing, margins = book.margins, align = book.align,
        hyphens = book.hyphens, spread = book.spread, flow = book.flow, marginItems = book.marginItems, plainMargins = book.plainMargins, runningHeads = book.runningHeads,
        headTitlePlace = book.headTitlePlace, headSize = book.headSize, headGap = book.headGap, collectionTitle = book.collectionTitle,
        pictures = book.pictures, colourLinks = book.colourLinks, joinOpenings = book.joinOpenings, rectoOpenings = book.rectoOpenings,
        printHeads = book.printHeads, dropCaps = book.dropCaps, otherLook = book.otherLook,
    )

    /** These and [other] give a book the same look (see [withLookOf]). */
    fun sameLook(other: ReaderSettings): Boolean = withLookOf(other) == this

    /** The look (see [withLookOf]), to keep with a book. */
    fun lookJson(): JSONObject = JSONObject()
        .put("font", font.name).put("userFont", userFont).put("fontScale", fontScale.toDouble()).put("line", lineSpacing.toDouble()).put("margins", margins.name)
        .put("align", align.name).put("hyphens", hyphens.name).put("spread", spread.name).put("flow", flow.name)
        .put("marginItems", MarginItem.write(marginItems)).put("plainMargins", MarginItem.writeSet(plainMargins)).put("heads", runningHeads.name).put("headTitles", headTitlePlace.name)
        .put("headSize", headSize.name).put("headGap", headGap.name).put("collectionTitle", collectionTitle.name)
        .put("pictures", pictures.name).put("linkColour", colourLinks).put("openings", joinOpenings).put("rectoOpenings", rectoOpenings)
        .put("printHeads", printHeads).put("dropCaps", dropCaps).put(OTHER_LOOK, otherLook)

    fun pageOnly(): ReaderSettings = copy(
        volumeKeys = true, edgeBack = false, keepScreenOn = true, brightness = -1f, brightnessFromScreen = true,
        levelSide = LevelSide.LEFT, levelStart = LevelStart.ANYWHERE, otherLook = null, pullHill = true,
        marginItems = if (scrolling) marginItems else MarginItem.DEFAULTS,
        runningHeads = if (scrolling) runningHeads else RunningHeads.TOP,
        headTitlePlace = HeadTitlePlace.CENTRED, collectionTitle = CollectionTitle.BOOK,
        highlightColor = HighlightColor.YELLOW, highlightStyle = HighlightStyle.HIGHLIGHT, dictionary = null,
        dim = 0f, rotation = ReaderRotation.AUTO, autoWpm = 230, autoEnd = AutoEnd.WAIT, autoResume = AutoResume.SOON,
        progressLine = ProgressLine.BOOK, fullscreen = true, roundCamera = true,
    )

    /**
     * When scrolling, the bands the margins' items show in, top and bottom (none where nothing shows). With
     * page headings on, their end has one too, with the chapter's title in it.
     */
    val bandTop: Int get() = bandTop(Cutouts.NONE)
    val bandBottom: Int get() = bandBottom(Cutouts.NONE)

    /**
     * The same with the camera's cutouts where [cut] has them. The band at the top is deep enough to hide
     * the text as it goes by behind a camera there, even with nothing shown in it; and when the camera would
     * be over what's shown, deep enough for that to sit below it.
     */
    fun bandTop(cut: Cutouts): Int {
        if (!scrolling) return 0
        val used = marginItems.values.any { it.top } || runningHeads == RunningHeads.TOP
        val depth = cut.top?.let { Math.round(it.depth) } ?: 0
        return when {
            used && cameraInTheWay(cut) -> topRowAt(cut) + MarginRoom.ROW_HEIGHT + 8
            used -> maxOf(BAND, depth + 4)
            depth > 0 && clearOfCamera -> depth + 4
            else -> 0
        }
    }

    /** Something is shown along the foot of the page besides the progress bar: a margin item, or the headings. */
    val footItemsShown: Boolean get() = marginItems.values.any { !it.top && it != MarginSpot.OFF } || runningHeads == RunningHeads.BOTTOM

    fun bandBottom(cut: Cutouts): Int {
        if (!scrolling) return 0
        val depth = cut.bottom?.let { Math.round(it.depth) } ?: 0
        val camera = if (depth > 0 && clearOfCamera) depth + 4 else 0
        return when {
            footItemsShown -> maxOf(BAND, depth + MarginRoom.ROW_HEIGHT + 10)
            // Only the progress bar: a shallower band, for the text to fade out above it rather than run under it.
            progressLine != ProgressLine.OFF -> maxOf(BAR_BAND, camera)
            else -> camera
        }
    }

    /** What reader.js is told; [cutoutTop], how deep a camera's cutout is at the top of the screen, in dp. */
    fun toJs(language: String?, cutoutTop: Int = 0): String = toJs(language, Cutouts.top(cutoutTop))

    /** What reader.js is told, with the camera's cutouts where [cut] has them. */
    fun toJs(language: String?, cut: Cutouts): String = JSONObject().apply {
        put("bg", css(theme.bg))
        put("fg", css(theme.fg))
        put("link", css(theme.link))
        put("recolor", theme.recolor)
        put("dimImages", theme.dark)
        put("sel", if (theme.dark) "rgba(120, 160, 255, 0.35)" else "rgba(255, 196, 0, 0.35)")
        put("hl", if (theme.dark) "rgba(255, 196, 0, 0.35)" else "rgba(255, 196, 0, 0.55)")
        put("fontScale", fontScale.toDouble())
        put("font", UserFonts.shared?.cssFor(userFont) ?: font.css)
        put("line", lineSpacing.toDouble())
        put("align", align.js)
        put("hyphens", hyphens.js)
        put("marginFrac", margins.fraction.toDouble())
        put("columns", spread.columns)
        put("flow", flow.js)
        put("bandTop", bandTop(cut))
        put("bandBottom", bandBottom(cut))
        marginRoom(cut).let { room ->
            put("minTop", room.top)
            put("minBottom", room.bottom)
        }
        pictureRoom(cut).let { room ->
            put("picTop", room.top)
            put("picBottom", room.bottom)
        }
        put("animate", pageTurn == PageTurn.SLIDE)
        put("turn", if (scrolling) PageTurn.SLIDE.js else pageTurn.js)
        put("taps", taps.js)
        put("tapWidth", tapWidth.fraction.toDouble())
        put("blend", pictures.js)
        put("plainLinks", !colourLinks)
        put("printHeads", printHeads)
        put("dropCaps", dropCaps)
        put("rectoOpenings", rectoOpenings)
        put("barColor", css(barColor))
        language?.let { put("lang", it) }
    }.toString()

    companion object {
        /**
         * The margins' items in plain type to start with: the time and the battery, as the status bar has them. (First
         * in here: the settings made below start with it.)
         */
        val PLAIN_TO_START: Set<MarginItem> = setOf(MarginItem.CLOCK, MarginItem.BATTERY)

        /** [base] with the look kept in [o] (see [lookJson]); anything missing or unknown stays as [base] has it. */
        fun withLook(base: ReaderSettings, o: JSONObject): ReaderSettings {
            fun <E : Enum<E>> pick(key: String, values: Array<E>, d: E): E = o.optString(key).let { v -> values.firstOrNull { it.name == v } } ?: d
            return base.copy(
                font = pick("font", ReaderFont.values(), base.font),
                userFont = if (o.has("userFont")) o.optString("userFont") else base.userFont,
                fontScale = o.optDouble("fontScale", base.fontScale.toDouble()).toFloat(),
                lineSpacing = o.optDouble("line", base.lineSpacing.toDouble()).toFloat(),
                margins = pick("margins", ReaderMargins.values(), base.margins),
                align = pick("align", ReaderAlign.values(), base.align),
                hyphens = pick("hyphens", ReaderHyphens.values(), base.hyphens),
                spread = pick("spread", ReaderSpread.values(), base.spread),
                flow = pick("flow", ReaderFlow.values(), base.flow),
                marginItems = o.optString("marginItems").takeIf { it.isNotEmpty() }?.let { MarginItem.parse(it) } ?: base.marginItems,
                plainMargins = if (o.has("plainMargins")) MarginItem.parseSet(o.optString("plainMargins")) else base.plainMargins,
                runningHeads = pick("heads", RunningHeads.values(), base.runningHeads),
                headTitlePlace = pick("headTitles", HeadTitlePlace.values(), base.headTitlePlace),
                headSize = pick("headSize", HeadSize.values(), base.headSize),
                headGap = pick("headGap", HeadGap.values(), base.headGap),
                collectionTitle = pick("collectionTitle", CollectionTitle.values(), base.collectionTitle),
                pictures = pick("pictures", PictureBlend.values(), base.pictures),
                colourLinks = o.optBoolean("linkColour", base.colourLinks),
                joinOpenings = o.optBoolean("openings", base.joinOpenings),
                rectoOpenings = o.optBoolean("rectoOpenings", base.rectoOpenings),
                printHeads = o.optBoolean("printHeads", base.printHeads),
                dropCaps = o.optBoolean("dropCaps", base.dropCaps),
                otherLook = if (o.has(OTHER_LOOK)) o.optString(OTHER_LOOK).takeIf { it.isNotEmpty() } else base.otherLook,
            )
        }

        /** Where a look keeps the other of pages and scrolling's look (see [otherLook]). */
        private const val OTHER_LOOK = "otherLook"

        /**
         * How the reader starts on a phone, where a page is small and held close: a typeface made for small
         * screens at a size that gives about forty letters a line, steady line spacing, words broken at the
         * ends of the short lines, the lightest page turn, small page headings close to the text, and wide
         * sides to tap with a thumb. (On a tablet it starts as [ReaderSettings] itself.)
         */
        val PHONE = ReaderSettings(
            font = ReaderFont.LITERATA, fontScale = 1.1f, lineSpacing = 1.4f, hyphens = ReaderHyphens.ON,
            pageTurn = PageTurn.SLIDE, headTitlePlace = HeadTitlePlace.CENTRED, headSize = HeadSize.SMALL,
            headGap = HeadGap.CLOSE, tapWidth = TapWidth.WIDE,
        )

        /** How the reader starts on this kind of device. */
        fun startingOn(phone: Boolean): ReaderSettings = if (phone) PHONE else ReaderSettings()


        fun css(argb: Long): String = "#%06x".format(argb and 0xFFFFFF)
        val FONT_SCALES = listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.45f, 1.6f, 1.8f, 2f, 2.3f, 2.6f)
        val LINE_SPACINGS = listOf(0f, 1.2f, 1.4f, 1.6f, 1.8f, 2f)
        const val MAX_DIM = 0.7f
        /** How much of the page's width, in from a side, a swipe that sets the brightness starts in. */
        const val LEVEL_STRIP = 0.07f
        /** The page headings' size before they could be bigger, in sp. */
        const val HEAD_SMALL = 13f
        /** How tall the bands are that margin items show in when scrolling, in dp (the page's pixels). */
        const val BAND = 36
        /** How tall the band at the foot is when scrolling with only the progress bar there, in dp. */
        const val BAR_BAND = 24
        /** How far in from the sides what's in the margins sits, in dp. */
        const val SIDE = 24f
        /** The least space between a camera and what's beside it, in dp. */
        const val CLEAR = 8f
        /** The space between a camera and the text below it, in dp. */
        const val UNDER = 6
        const val MIN_WPM = 80
        const val MAX_WPM = 600
    }
}

/** The reader's settings, the same for every book. */
class ReaderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    /** A phone rather than a tablet: the reader starts differently on one (see [ReaderSettings.PHONE]). */
    val phone: Boolean = context.resources.configuration.smallestScreenWidthDp < 600
    private val _settings = MutableStateFlow(load())
    /** The usual settings, as they are with the device held upright. */
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _landscape = MutableStateFlow(loadLandscape(_settings.value))
    /**
     * The usual settings with the device held sideways: the same, but for the look (see
     * [ReaderSettings.withLookOf]), which each way of holding it has to itself. Changing one leaves the other.
     */
    val landscape: StateFlow<ReaderSettings> = _landscape.asStateFlow()

    /** The usual settings with the device held one way or the other. */
    fun usual(landscape: Boolean): ReaderSettings = if (landscape) _landscape.value else _settings.value

    fun update(change: (ReaderSettings) -> ReaderSettings) {
        val next = change(_settings.value)
        _settings.value = next
        save(next)
        // What isn't the look is the same both ways.
        _landscape.value = next.withLookOf(_landscape.value)
    }

    /** Changes the usual settings as they are with the device held one way: the look that way, and the rest for both. */
    fun update(landscape: Boolean, change: (ReaderSettings) -> ReaderSettings) {
        if (!landscape) return update(change)
        val next = change(_landscape.value)
        _landscape.value = next
        prefs.edit().putString(LANDSCAPE_LOOK, next.lookJson().toString()).apply()
        val upright = next.withLookOf(_settings.value)
        if (upright != _settings.value) {
            _settings.value = upright
            save(upright)
        }
    }

    /** These become the usual settings both ways the device is held: where the reading setup leaves them. */
    fun setBoth(next: ReaderSettings) {
        update { next }
        update(true) { next }
    }

    /**
     * For a screen with a camera in the middle of its top ([cut]), the first time the reader is shown on
     * it held this way: if nothing round the page has been moved and it shows one page at a time, the
     * headings go to the foot of the page and the time and battery to the top corners (see
     * [ReaderSettings.aroundCamera]). Once only: from then on it's as the reader leaves it. Returns whether
     * anything was moved.
     */
    fun placeAroundCamera(landscape: Boolean, cut: Cutouts): Boolean {
        val key = if (landscape) "cameraPlaced.landscape" else "cameraPlaced.portrait"
        if (cut.top == null || cut.width <= 0f || prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        val usual = usual(landscape)
        if (!cut.topMiddle || usual.pagesAcross(cut.width, cut.height) != 1 || !usual.stockArrangement) return false
        update(landscape) { it.aroundCamera() }
        return true
    }

    private fun loadLandscape(upright: ReaderSettings): ReaderSettings {
        val kept = prefs.getString(LANDSCAPE_LOOK, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        // From before each way had a look of its own: sideways starts with the one there was.
        if (kept == null) prefs.edit().putString(LANDSCAPE_LOOK, upright.lookJson().toString()).apply()
        return if (kept == null) upright else ReaderSettings.withLook(upright, kept)
    }

    /** A book is being opened: it starts at the tablet's own brightness, unless the reader's last is to be kept. */
    fun startReading() {
        if (_settings.value.brightnessFromScreen) update { it.copy(brightness = -1f, dim = 0f) }
    }

    private fun save(next: ReaderSettings) {
        prefs.edit()
            .putString("theme", next.theme.name)
            .putString("font", next.font.name)
            .putString("userFont", next.userFont)
            .putFloat("fontScale", next.fontScale)
            .putFloat("line", next.lineSpacing)
            .putString("margins", next.margins.name)
            .putString("align", next.align.name)
            .putString("hyphens", next.hyphens.name)
            .putString("spread", next.spread.name)
            .putString("turn", next.pageTurn.name)
            .putBoolean("volume", next.volumeKeys)
            .putBoolean(EDGE_BACK, next.edgeBack)
            .putString("levelSide", next.levelSide.name)
            .putString("levelStart", next.levelStart.name)
            .putString("otherLook", next.otherLook)
            .putBoolean("pullHill", next.pullHill)
            .putBoolean("clearOfCamera", next.clearOfCamera)
            .putBoolean("fullscreen", next.fullscreen)
            .putBoolean("roundCamera", next.roundCamera)
            .putString("marginItems", MarginItem.write(next.marginItems))
            .putString("plainMargins", MarginItem.writeSet(next.plainMargins))
            .putBoolean("awake", next.keepScreenOn)
            .putFloat("brightness", next.brightness)
            .putString("hlColor", next.highlightColor.name)
            .putString("hlStyle", next.highlightStyle.name)
            .putString("dictionary", next.dictionary)
            .putFloat("dim", next.dim)
            .putBoolean("openings", next.joinOpenings)
            .putString("rotation", next.rotation.name)
            .putString("taps", next.taps.name)
            .putString("tapWidth", next.tapWidth.name)
            .putString("pictures", next.pictures.name)
            .putBoolean("linkColour", next.colourLinks)
            .putString("flow", next.flow.name)
            .putInt("autoWpm", next.autoWpm)
            .putString("autoEnd", next.autoEnd.name)
            .putString("autoResume", next.autoResume.name)
            .putString("progressBar", next.progressLine.name)
            .putString("barShade", next.barShade.name)
            .putString("heads", next.runningHeads.name)
            .putString("headTitles", next.headTitlePlace.name)
            .putString("headSize", next.headSize.name)
            .putString("headGap", next.headGap.name)
            .putBoolean("brightnessFromScreen", next.brightnessFromScreen)
            .putBoolean("rectoOpenings", next.rectoOpenings)
            .putBoolean("printHeads", next.printHeads)
            .putBoolean("dropCaps", next.dropCaps)
            .putString("collectionTitle", next.collectionTitle.name)
            .apply()
    }

    /** From the two switches there were before each item had a place of its own. */
    private fun marginsBefore(): Map<MarginItem, MarginSpot> {
        val out = MarginItem.DEFAULTS.toMutableMap()
        if (!prefs.getBoolean("status", true)) listOf(MarginItem.CHAPTER, MarginItem.PAGE, MarginItem.PERCENT).forEach { out[it] = MarginSpot.OFF }
        if (!prefs.getBoolean("clock", true)) listOf(MarginItem.CLOCK, MarginItem.BATTERY).forEach { out[it] = MarginSpot.OFF }
        return out
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private companion object {
        const val LANDSCAPE_LOOK = "lookLandscape"
        /** KoLib's back gesture, as there's been one switch for the sides (see [ReaderSettings.edgeBack]). */
        const val EDGE_BACK = "edgeSwipesBack"
    }

    private fun load(): ReaderSettings {
        // Nothing kept yet, the first time the reader's settings are read: they start as suits the device, and are
        // kept from then on, so they stay as they are if the starting ones ever change.
        if (prefs.all.isEmpty()) return ReaderSettings.startingOn(phone).also { save(it) }
        val loaded = loadAsSaved()
        // Settings from before there were page headings: they're turned on, as they are to start with,
        // with the chapter's title and the page number now in them rather than in the margins.
        if (!prefs.contains("heads") && prefs.contains("marginItems")) {
            return loaded.copy(runningHeads = RunningHeads.OFF).withHeads(RunningHeads.TOP).also { save(it) }
        }
        return loaded
    }

    private fun loadAsSaved(): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            theme = enumOf("theme", d.theme),
            font = enumOf("font", d.font),
            userFont = prefs.getString("userFont", d.userFont).orEmpty(),
            fontScale = prefs.getFloat("fontScale", d.fontScale),
            lineSpacing = prefs.getFloat("line", d.lineSpacing),
            margins = enumOf("margins", d.margins),
            align = enumOf("align", d.align),
            hyphens = enumOf("hyphens", d.hyphens),
            spread = enumOf("spread", d.spread),
            pageTurn = enumOf("turn", d.pageTurn),
            volumeKeys = prefs.getBoolean("volume", d.volumeKeys),
            // From before there was one switch: the sides going back was the panels being off.
            edgeBack = if (prefs.contains(EDGE_BACK)) prefs.getBoolean(EDGE_BACK, d.edgeBack) else !prefs.getBoolean("edgePanels", true),
            levelSide = enumOf("levelSide", d.levelSide),
            levelStart = enumOf("levelStart", d.levelStart),
            otherLook = prefs.getString("otherLook", null),
            pullHill = prefs.getBoolean("pullHill", d.pullHill),
            clearOfCamera = prefs.getBoolean("clearOfCamera", d.clearOfCamera),
            fullscreen = prefs.getBoolean("fullscreen", d.fullscreen),
            roundCamera = prefs.getBoolean("roundCamera", d.roundCamera),
            brightness = prefs.getFloat("brightness", d.brightness),
            highlightColor = enumOf("hlColor", d.highlightColor),
            highlightStyle = enumOf("hlStyle", d.highlightStyle),
            dictionary = prefs.getString("dictionary", null),
            dim = prefs.getFloat("dim", d.dim),
            joinOpenings = prefs.getBoolean("openings", d.joinOpenings),
            rotation = enumOf("rotation", d.rotation),
            taps = enumOf("taps", d.taps),
            tapWidth = enumOf("tapWidth", d.tapWidth),
            pictures = enumOf("pictures", d.pictures),
            colourLinks = prefs.getBoolean("linkColour", d.colourLinks),
            flow = enumOf("flow", d.flow),
            autoWpm = prefs.getInt("autoWpm", d.autoWpm).coerceIn(ReaderSettings.MIN_WPM, ReaderSettings.MAX_WPM),
            autoEnd = enumOf("autoEnd", d.autoEnd),
            autoResume = enumOf("autoResume", d.autoResume),
            progressLine = enumOf("progressBar", d.progressLine),
            barShade = enumOf("barShade", d.barShade),
            marginItems = prefs.getString("marginItems", null)?.let { MarginItem.parse(it) } ?: marginsBefore(),
            plainMargins = prefs.getString("plainMargins", null)?.let { MarginItem.parseSet(it) } ?: d.plainMargins,
            runningHeads = enumOf("heads", d.runningHeads),
            headTitlePlace = enumOf("headTitles", d.headTitlePlace),
            headSize = enumOf("headSize", d.headSize),
            headGap = enumOf("headGap", d.headGap),
            brightnessFromScreen = prefs.getBoolean("brightnessFromScreen", d.brightnessFromScreen),
            rectoOpenings = prefs.getBoolean("rectoOpenings", d.rectoOpenings),
            printHeads = prefs.getBoolean("printHeads", d.printHeads),
            dropCaps = prefs.getBoolean("dropCaps", d.dropCaps),
            collectionTitle = enumOf("collectionTitle", d.collectionTitle),
            keepScreenOn = prefs.getBoolean("awake", d.keepScreenOn),
        )
    }
}
