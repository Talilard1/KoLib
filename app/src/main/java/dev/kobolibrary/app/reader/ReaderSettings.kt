package dev.kobolibrary.app.reader

import dev.kobolibrary.app.tr
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
    AUTO(tr("Automatic"), tr("Turns with the device.")),
    PORTRAIT(tr("Portrait"), tr("Always upright, however you hold the device.")),
    LANDSCAPE(tr("Landscape"), tr("Always sideways, however you hold the device.")),
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

/** What's done with pictures so they sit on the page's colour rather than in a white box. */
enum class PictureBlend(val label: String, val js: String, val help: String) {
    OFF(tr("Off"), "off", tr("Pictures look the same as in the book.")),
    ORNAMENTS(tr("Decorations"), "ornaments", tr("Black-and-white decorations and charts take the page colour. On dark pages they turn light. Framed pictures keep their white background.")),
    ALL(tr("All pictures"), "all", tr("Every picture takes the page colour. On dark pages, photos look like negatives.")),
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
    PAGES(dev.kobolibrary.app.trc("reading mode", "Pages"), "pages"),
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
    PERCENT(tr("Percent of book read"), "34%", MarginSpot.BOTTOM_RIGHT);

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

        /** What the page headings show anyway: shown in the margins as well, they'd be there twice. */
        val IN_HEADINGS = listOf(BOOK, CHAPTER, PAGE, BOOK_PAGE)
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
    val font: ReaderFont = ReaderFont.BOOK,
    /** Text size, 1 = as the book sets it. */
    val fontScale: Float = 1f,
    /** Line spacing; 0 = as the book sets it. */
    val lineSpacing: Float = 0f,
    val margins: ReaderMargins = ReaderMargins.MEDIUM,
    val align: ReaderAlign = ReaderAlign.BOOK,
    val hyphens: ReaderHyphens = ReaderHyphens.BOOK,
    val spread: ReaderSpread = ReaderSpread.AUTO,
    val pageTurn: PageTurn = PageTurn.SLIDE,
    val volumeKeys: Boolean = true,
    /**
     * A swipe in from the left side opens the contents, from the right the reading settings. Off, the
     * sides are left to Android's back swipe.
     */
    val edgePanels: Boolean = true,
    /** With [edgePanels] off: KoLib's own back swipe at the sides, rather than Android's. */
    val edgeBack: Boolean = false,
    /** Where the chapter, page number, time and the rest show in the margins (or don't). */
    val marginItems: Map<MarginItem, MarginSpot> = MarginItem.DEFAULTS,
    /** Page headings as a printed book has them: at the top of each page, the bottom, or none. */
    val runningHeads: RunningHeads = RunningHeads.TOP,
    /** Where the page headings' titles sit along the page. */
    val headTitlePlace: HeadTitlePlace = HeadTitlePlace.CENTRED,
    /** In a book of several books, whose title the headings and margins show: the one being read, or the whole book's. */
    val collectionTitle: CollectionTitle = CollectionTitle.BOOK,
    /** The screen stays on while reading. */
    val keepScreenOn: Boolean = true,
    /** Screen brightness while reading, 0 to 1; below 0, the tablet's own. */
    val brightness: Float = -1f,
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
    val colourLinks: Boolean = true,
    /** A chapter's opening page (its number, an ornament) shown with its text, when a book keeps it in a file of its own. */
    val joinOpenings: Boolean = true,
    /** Pages to turn, or one long page to scroll. */
    val flow: ReaderFlow = ReaderFlow.PAGES,
    /** Autoscroll's speed, in words a minute. */
    val autoWpm: Int = 230,
    val autoEnd: AutoEnd = AutoEnd.WAIT,
    val autoResume: AutoResume = AutoResume.SOON,
    /** How much of the book (or the chapter) has been read, as a line along the foot of the page. */
    val progressLine: ProgressLine = ProgressLine.BOOK,
    /** How strongly that line, and autoscroll's, is drawn. */
    val barShade: BarShade = BarShade.TEXT,
) {
    val scrolling: Boolean get() = flow == ReaderFlow.SCROLL

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
     * end. Turned off, the chapter's title and the page number come back to where they used to be, if
     * nothing else is there.
     */
    fun withHeads(h: RunningHeads): ReaderSettings {
        if (h == runningHeads) return this
        val items = marginItems.toMutableMap()
        if (h != RunningHeads.OFF) {
            MarginItem.IN_HEADINGS.forEach { items[it] = MarginSpot.OFF }
            for ((item, spot) in items.toMap()) if (spot != MarginSpot.OFF && spot.top == h.top) items[item] = spot.otherEnd()
        } else {
            fun free(spot: MarginSpot) = items.values.none { it == spot }
            if (items[MarginItem.CHAPTER] == MarginSpot.OFF && free(MarginSpot.TOP_CENTER)) items[MarginItem.CHAPTER] = MarginSpot.TOP_CENTER
            if (items[MarginItem.PAGE] == MarginSpot.OFF && items[MarginItem.BOOK_PAGE] == MarginSpot.OFF && free(MarginSpot.BOTTOM_LEFT)) {
                items[MarginItem.PAGE] = MarginSpot.BOTTOM_LEFT
            }
        }
        return copy(runningHeads = h, marginItems = items)
    }

    /**
     * The room the margins need above and below the text, with a camera's cutout [cutoutTop] dp deep at
     * the top of the screen: a row at the top sits below it. None when scrolling, which has bands of its own.
     */
    fun marginRoom(cutoutTop: Int = 0): MarginRoom {
        if (scrolling) return MarginRoom()
        val atTop = (headsShown && runningHeads.top) || MarginItem.entries.any { shownSpotOf(it).top }
        val atBottom = (headsShown && !runningHeads.top) || MarginItem.entries.any { shownSpotOf(it).let { s -> s != MarginSpot.OFF && !s.top } }
        val row = MarginRoom.ROW_HEIGHT + MarginRoom.GAP
        return MarginRoom(
            top = if (atTop) topRowAt(cutoutTop) + row else 0,
            bottom = if (atBottom) bottomRowAt() + row else 0,
        )
    }

    /** How far down the top row of the margins sits: below a camera's cutout [cutoutTop] dp deep, if there is one. */
    fun topRowAt(cutoutTop: Int): Int = maxOf(MarginRoom.ROW_TOP, if (cutoutTop > 0) cutoutTop + 2 else 0)

    /** How far up the bottom row of the margins sits: above the progress bar, when there is one. */
    fun bottomRowAt(): Int = if (progressLine == ProgressLine.OFF) 5 else 10

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
    fun pageOnly(): ReaderSettings = copy(
        volumeKeys = true, edgePanels = true, edgeBack = false, keepScreenOn = true, brightness = -1f,
        marginItems = if (scrolling) marginItems else MarginItem.DEFAULTS,
        runningHeads = if (scrolling) runningHeads else RunningHeads.TOP,
        headTitlePlace = HeadTitlePlace.CENTRED, collectionTitle = CollectionTitle.BOOK,
        highlightColor = HighlightColor.YELLOW, highlightStyle = HighlightStyle.HIGHLIGHT, dictionary = null,
        dim = 0f, rotation = ReaderRotation.AUTO, autoWpm = 230, autoEnd = AutoEnd.WAIT, autoResume = AutoResume.SOON,
        progressLine = ProgressLine.BOOK,
    )

    /**
     * When scrolling, the bands the margins' items show in, top and bottom (none where nothing shows). With
     * page headings on, their end has one too, with the chapter's title in it.
     */
    val bandTop: Int get() = if (scrolling && (marginItems.values.any { it.top } || runningHeads == RunningHeads.TOP)) BAND else 0
    val bandBottom: Int get() = if (scrolling && (marginItems.values.any { !it.top && it != MarginSpot.OFF } || runningHeads == RunningHeads.BOTTOM)) BAND else 0

    /** What reader.js is told; [cutoutTop], how deep a camera's cutout is at the top of the screen, in dp. */
    fun toJs(language: String?, cutoutTop: Int = 0): String = JSONObject().apply {
        put("bg", css(theme.bg))
        put("fg", css(theme.fg))
        put("link", css(theme.link))
        put("recolor", theme.recolor)
        put("dimImages", theme.dark)
        put("sel", if (theme.dark) "rgba(120, 160, 255, 0.35)" else "rgba(255, 196, 0, 0.35)")
        put("hl", if (theme.dark) "rgba(255, 196, 0, 0.35)" else "rgba(255, 196, 0, 0.55)")
        put("fontScale", fontScale.toDouble())
        put("font", font.css)
        put("line", lineSpacing.toDouble())
        put("align", align.js)
        put("hyphens", hyphens.js)
        put("marginFrac", margins.fraction.toDouble())
        put("columns", spread.columns)
        put("flow", flow.js)
        put("bandTop", bandTop)
        put("bandBottom", bandBottom)
        marginRoom(cutoutTop).let { room ->
            put("minTop", room.top)
            put("minBottom", room.bottom)
        }
        put("animate", pageTurn == PageTurn.SLIDE)
        put("turn", if (scrolling) PageTurn.SLIDE.js else pageTurn.js)
        put("taps", taps.js)
        put("tapWidth", tapWidth.fraction.toDouble())
        put("blend", pictures.js)
        put("plainLinks", !colourLinks)
        put("barColor", css(barColor))
        language?.let { put("lang", it) }
    }.toString()

    companion object {
        fun css(argb: Long): String = "#%06x".format(argb and 0xFFFFFF)
        val FONT_SCALES = listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.45f, 1.6f, 1.8f, 2f, 2.3f, 2.6f)
        val LINE_SPACINGS = listOf(0f, 1.2f, 1.4f, 1.6f, 1.8f, 2f)
        const val MAX_DIM = 0.7f
        /** How tall the bands are that margin items show in when scrolling, in dp (the page's pixels). */
        const val BAND = 36
        const val MIN_WPM = 80
        const val MAX_WPM = 600
    }
}

/** The reader's settings, the same for every book. */
class ReaderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    fun update(change: (ReaderSettings) -> ReaderSettings) {
        val next = change(_settings.value)
        _settings.value = next
        save(next)
    }

    private fun save(next: ReaderSettings) {
        prefs.edit()
            .putString("theme", next.theme.name)
            .putString("font", next.font.name)
            .putFloat("fontScale", next.fontScale)
            .putFloat("line", next.lineSpacing)
            .putString("margins", next.margins.name)
            .putString("align", next.align.name)
            .putString("hyphens", next.hyphens.name)
            .putString("spread", next.spread.name)
            .putString("turn", next.pageTurn.name)
            .putBoolean("volume", next.volumeKeys)
            .putBoolean("edgePanels", next.edgePanels)
            .putBoolean("edgeBack", next.edgeBack)
            .putString("marginItems", MarginItem.write(next.marginItems))
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

    private fun load(): ReaderSettings {
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
            fontScale = prefs.getFloat("fontScale", d.fontScale),
            lineSpacing = prefs.getFloat("line", d.lineSpacing),
            margins = enumOf("margins", d.margins),
            align = enumOf("align", d.align),
            hyphens = enumOf("hyphens", d.hyphens),
            spread = enumOf("spread", d.spread),
            pageTurn = enumOf("turn", d.pageTurn),
            volumeKeys = prefs.getBoolean("volume", d.volumeKeys),
            edgePanels = prefs.getBoolean("edgePanels", d.edgePanels),
            edgeBack = prefs.getBoolean("edgeBack", d.edgeBack),
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
            runningHeads = enumOf("heads", d.runningHeads),
            headTitlePlace = enumOf("headTitles", d.headTitlePlace),
            collectionTitle = enumOf("collectionTitle", d.collectionTitle),
            keepScreenOn = prefs.getBoolean("awake", d.keepScreenOn),
        )
    }
}
