package dev.kobolibrary.app.reader

import dev.kobolibrary.app.tr
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kobolibrary.app.epub.BookFiles
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.ChapterOps
import dev.kobolibrary.app.epub.ChapterOpenings
import dev.kobolibrary.app.epub.EpubText
import dev.kobolibrary.app.epub.FindReplace
import dev.kobolibrary.app.epub.TocNode
import dev.kobolibrary.app.log.AppLog
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** A footnote to show over the page. */
data class ReaderNote(val html: String, val href: String)

/** One place a search found. */
data class ReaderHit(val chapterIndex: Int, val chapter: String, val before: String, val text: String, val after: String, val target: String)

/**
 * The reader's side of a book being read: which chapter is open in the WebView, where in it the reader
 * is, and everything the reading screen can ask for. reader.js does the paging; this keeps the place.
 */
class ReaderController(
    val files: BookFiles,
    val structure: BookStructure,
    val bookTitle: String,
    private val language: String?,
    private val store: ReaderStore?,
    initialSettings: ReaderSettings,
    private val assets: android.content.res.AssetManager,
    /** For quotes shared from the book. */
    val bookAuthor: String? = null,
    /** The book's number in its series, if it has one: a title starting with it has it there to sort by. */
    private val bookSeriesIndex: Double? = null,
) {
    /** The book's language, for looking words up in the right Wikipedia. */
    val bookLanguage: String? get() = language

    val chapters = structure.chapters
    val toc: List<TocNode> = structure.toc
    private val server = BookServer(assets, files).apply { transform = { path, text -> joined(path, text) } }
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var chapterIndex by mutableStateOf(0)
        private set
    var page by mutableStateOf(0)
        private set
    var pages by mutableStateOf(1)
        private set
    /**
     * The pages of text in the chapter: one a screen, or with two side by side, up to twice the screens
     * (the last can have text on its left-hand page only). Pages are numbered by these, each on its own.
     */
    var chapterPages by mutableStateOf(1)
        private set
    /** Where the pages' text sits on screen, as the page last laid it out (null until it has). */
    var frame by mutableStateOf<PageFrame?>(null)
        private set
    /** The chapter on screen is only a picture (a cover, a plate), with no headings over it. */
    var picturePage by mutableStateOf(false)
        private set
    /**
     * Two pages side by side, the blank pages on screen before the chapter's first: one when the chapter is a
     * single page (an epigraph, a dedication), which goes on the right as in a printed book.
     */
    var blankBefore by mutableStateOf(0)
        private set
    /**
     * The chapter is a page a printed book doesn't number (a dedication, an epigraph or other quote, a map or
     * plate): no headings over it, not even its number.
     */
    var displayPage by mutableStateOf(false)
        private set
    /** How deep a camera's cutout is at the top of the screen, in dp: a top row in the margins sits below it. */
    var cutoutTop = 0
        private set
    var chapterProgress by mutableStateOf(0f)
        private set
    var loading by mutableStateOf(true)
        private set
    var controlsVisible by mutableStateOf(false)
    var note by mutableStateOf<ReaderNote?>(null)
    var image by mutableStateOf<String?>(null)
    var externalLink by mutableStateOf<String?>(null)
    /** Where the reader was before following a link, to go back to. */
    var jumpedFrom by mutableStateOf<ReaderPosition?>(null)
    /** Page turns since following the link: after a few, the way back is no longer offered. */
    private var movesSinceJump = 0
    private var reportedChapter = -1

    private fun jumpedFrom(p: ReaderPosition) {
        jumpedFrom = p
        movesSinceJump = 0
    }
    // Everything the saved state is loaded into is declared before the init block that loads it.
    var bookmarks by mutableStateOf<List<ReaderPosition>>(emptyList())
        private set
    var position by mutableStateOf<ReaderPosition?>(null)
        private set
    /** Highlights and notes in this book. */
    var annotations by mutableStateOf<List<Annotation>>(emptyList())
        private set
    /** At the very end of the book. */
    var atEnd by mutableStateOf(false)
        private set
    var settings by mutableStateOf(initialSettings)
        private set

    /** Draws Curl and Fade turns over the page, when the page is on screen. */
    var turner: PageTurner? = null
    /** Two pages side by side on screen. */
    var spread by mutableStateOf(false)
        private set
    /** The chapter on screen is laid out for scrolling (pages are then screenfuls). */
    var scrolledHere by mutableStateOf(false)
        private set
    /** Text is selected in the page (a drag then moves the selection rather than turning). */
    @Volatile
    var selecting = false
        private set

    /** Something is shown over the page, so a turn isn't animated. */
    val somethingOver: Boolean
        get() = controlsVisible || note != null || image != null || externalLink != null || atEnd ||
            openAnnotation != null || editingNote != null || lookUpText != null

    /** There's a chapter to go on to that way. */
    fun canMove(dir: Int): Boolean = nextLinear(chapterIndex, dir) != null

    /** The reader as a page turn sees it. */
    val turnPages: TurnPages = object : TurnPages {
        override val pageTurn get() = if (settings.scrolling) PageTurn.SLIDE else settings.pageTurn
        override val spread get() = this@ReaderController.spread
        override val selecting get() = this@ReaderController.selecting
        override val blocked get() = loading || somethingOver
        override val page get() = this@ReaderController.page
        override val pages get() = this@ReaderController.pages
        // As shaded on screen, for the paper drawn in a turn.
        override val paper get() = settings.theme.bg.toInt().let { c ->
            val f = 1f - settings.dim
            android.graphics.Color.rgb((android.graphics.Color.red(c) * f).toInt(), (android.graphics.Color.green(c) * f).toInt(), (android.graphics.Color.blue(c) * f).toInt())
        }
        override fun canMove(dir: Int) = this@ReaderController.canMove(dir)
        override fun turnPageNow(dir: Int, done: (Boolean) -> Unit) = this@ReaderController.turnPageNow(dir, done)
        override fun openChapter(dir: Int) = if (dir > 0) nextChapter() else previousChapter(atItsEnd = true)
        override fun askForTurn(dir: Int) = if (dir > 0) nextPage() else previousPage()
    }

    /** Moves the page on (or back) at once, without the page's own animation. */
    fun turnPageNow(dir: Int, done: (Boolean) -> Unit) {
        val v = webView
        if (v == null) {
            done(false)
            return
        }
        main.post {
            try {
                v.evaluateJavascript("KLR.turnNow($dir)") { r ->
                    // Where the page is now comes back with it: taken in at once, so the headings and the
                    // page number are this page's before a turn copies it.
                    val moved = r != null && r != "false" && r != "null"
                    if (moved && r != "true") runCatching { org.json.JSONTokener(r).nextValue() as String }.getOrNull()?.let { update(it) }
                    done(moved)
                }
            } catch (e: Exception) {
                done(false)
            }
        }
    }

    @Volatile
    private var startJson: String = "{}"
    /** What the next chapter opened is told, for tests. */
    internal val startState: String get() = startJson
    private var webView: WebView? = null
    private var saveJob: Job? = null

    /** How much text each chapter has, for progress through the whole book. */
    private var weights: FloatArray = FloatArray(chapters.size) { 1f }
    private var weightTotal = chapters.size.toFloat()

    /** The chapters' lengths have been counted. */
    private var lengthsKnown by mutableStateOf(false)
    /**
     * How many pages each chapter came to when it was laid out, at the present text size, page size and
     * the rest ([layoutKey] says which): a chapter not laid out yet is worked out from its length.
     */
    private val laidOutPages = mutableStateMapOf<Int, Int>()
    private var layoutKey = ""

    val chapter get() = chapters.getOrNull(chapterIndex)

    val bookProgress: Float
        get() {
            if (chapters.isEmpty()) return 0f
            var before = 0f
            for (i in 0 until chapterIndex) before += weights[i]
            return ((before + weights.getOrElse(chapterIndex) { 0f } * chapterProgress) / weightTotal).coerceIn(0f, 1f)
        }

    /** Minutes left in the chapter at an ordinary reading speed. */
    val minutesLeftInChapter: Int
        get() = (chapterWords.getOrElse(chapterIndex) { 0 } * (1f - chapterProgress) / WORDS_PER_MINUTE).toInt()

    private var chapterWords = IntArray(chapters.size)
    /** [chapterWords] are counted (a moment before [lengthsKnown]: the openings are found from them first). */
    @Volatile
    private var wordsCounted = false

    /** The chapter's words have been counted (so the time left in it means something). */
    val chapterWordsKnown: Boolean get() = chapterWords.getOrElse(chapterIndex) { 0 } > 0

    init {
        store?.load()?.let { s ->
            annotations = s.annotations
            bookmarks = s.bookmarks
            position = s.position
        }
        scope.launch {
            val stamp = java.util.zip.CRC32()
            val counted = withContext(Dispatchers.IO) {
                chapters.map { c ->
                    val text = runCatching { files.text(c.path) }.getOrNull()
                    stamp.update((c.path + "\u0000" + text.orEmpty()).toByteArray())
                    text?.let { runCatching { EpubText.plainText(it) }.getOrNull() }.orEmpty()
                }
            }
            textStamp = stamp.value.toString(16)
            chapterWords = counted.map { EpubText.countWords(it) }.toIntArray()
            wordsCounted = true
            weights = counted.map { maxOf(1f, it.length.toFloat()) }.toFloatArray()
            weightTotal = weights.sum()
            findOpenings()
            lengthsKnown = true
        }
    }

    // ---- pages through the whole book -----------------------------------------------------------

    /** Pages in each chapter, and in the book. */
    class BookPages(val counts: IntArray) {
        val total: Int = counts.sum().coerceAtLeast(1)
        fun before(i: Int): Int {
            var n = 0
            for (j in 0 until i.coerceAtMost(counts.size)) n += counts[j]
            return n
        }
    }

    /**
     * Pages through the whole book at the present text size and page size: every chapter counted (see
     * [ChapterCounter]), so a page has the same number however the reader got to it. Null while they're
     * being counted (no number rather than one that changes). Without a counter (or if counting failed),
     * worked out from the chapters laid out so far.
     */
    val bookPages: BookPages?
        get() {
            if (chapters.isEmpty()) return null
            // Counted (or kept from before): there straight away, before the chapters' lengths are.
            exactPages()?.let { return BookPages(it) }
            if (!lengthsKnown) return null
            if (counter != null && !countFailed) return null
            val perPage = textPerPage() ?: return null
            return BookPages(IntArray(chapters.size) { i ->
                if (isKnownJoined(i)) 0
                else laidOutPages[i] ?: ceil(textWithOpening(i) / perPage).toInt().coerceAtLeast(1)
            })
        }

    /** Each chapter's pages as counted, for the layout on screen; null if they're not all counted for it. */
    private fun exactPages(): IntArray? = counted?.takeIf { countedKey == layoutKey && it.size == chapters.size }

    /** Pages on a screen: two side by side, or one. */
    val perScreen: Int get() = if (spread && !scrolledHere) 2 else 1

    /** The first page with text on screen, in the chapter (0 is its first). */
    val firstShown: Int get() = (page * perScreen - blankBefore).coerceIn(0, (chapterPages - 1).coerceAtLeast(0))

    /** The last page on screen, in the chapter: the same as [firstShown] unless two show and the right-hand one has text. */
    val lastShown: Int get() = (page * perScreen + perScreen - 1 - blankBefore).coerceIn(firstShown, (chapterPages - 1).coerceAtLeast(0))

    /** The page showing (the left-hand one, with two side by side), counted through the whole book (the first is 1). */
    val bookPage: Int?
        get() = bookPageOfShown(firstShown)

    /** The last page showing, counted through the whole book: with two side by side, the right-hand one. */
    val bookPageLast: Int?
        get() = if (lastShown == firstShown) bookPage else bookPageOfShown(lastShown)?.takeIf { n -> bookPage.let { it == null || n > it } }

    /** The chapter's page [inChapter] (0 is its first), counted through the whole book; null while they're counted. */
    private fun bookPageOfShown(inChapter: Int): Int? = bookPages?.let { bp ->
        val i = inChapter.coerceIn(0, (bp.counts.getOrElse(chapterIndex) { 1 } - 1).coerceAtLeast(0))
        (bp.before(chapterIndex) + i + 1).coerceIn(1, bp.total)
    }

    /** "Page 45", or with two pages showing, "Pages 45–46". */
    fun pagesLabel(first: Int, last: Int?): String = if (last != null && last > first) tr("Pages {0}–{1}", first, last) else tr("Page {0}", first)

    /**
     * The pages on screen as their headings show them: their numbers through the book, which side of a
     * printed book each is (two side by side are a left and a right; one alone, a left if its number is
     * even), and whether it's the first page of a chapter. None while scrolling, or over a picture.
     */
    val shownPages: List<ShownPage>
        get() {
            if (scrolledHere || settings.scrolling || picturePage || displayPage || loading) return emptyList()
            val first = bookPage
            val last = bookPageLast
            val firstOnScreen = firstShown
            val start = page * perScreen - blankBefore
            return (0 until perScreen).mapNotNull { slot ->
                val inChapter = start + slot
                if (inChapter < 0 || inChapter >= chapterPages) return@mapNotNull null
                val number = if (inChapter == firstOnScreen) first else last
                val side = when {
                    perScreen == 2 -> if (slot == 0) PageSide.LEFT else PageSide.RIGHT
                    number != null && number % 2 == 0 -> PageSide.LEFT
                    else -> PageSide.RIGHT
                }
                // A page on its own (an epigraph, a dedication) has its number but no title over it, as in print.
                val alone = blankBefore > 0 && chapterPages == 1
                ShownPage(slot, number, side, opening = alone || (inChapter == 0 && startsChapter(chapterIndex)))
            }
        }

    /** The books inside this one, if it holds several (an omnibus); none if it's a single book. */
    val innerBooks: List<InnerBook> by lazy { runCatching { Omnibus.books(toc, chapters, bookTitle) }.getOrDefault(emptyList()) }

    /** The book being read, in a book that holds several; null in one that doesn't, or in the pages around its books. */
    val innerBook: InnerBook? get() = innerBooks.firstOrNull { chapterIndex in it.first..it.last }

    /** The whole book's title as the headings show it: without a number in front that's only there to sort it. */
    private val ownHeadTitle: String by lazy { HeadTitles.book(bookTitle, bookSeriesIndex, listOfNotNull(bookAuthor)) }

    /**
     * The book's title as the headings (and the margins) show it: in a book of several books, the one being
     * read, unless the whole book's is chosen.
     */
    val headBookTitle: String
        get() = (if (settings.collectionTitle == CollectionTitle.BOOK) innerBook?.title else null) ?: ownHeadTitle

    /**
     * The chapter's title as the headings show it: the contents' name for it, or for a file that only
     * carries on a chapter, the chapter it carries on; blank if the contents don't name one.
     */
    val headChapterTitle: String
        get() = HeadTitles.chapter(headChapterName())

    /** The chapter's name for [headChapterTitle], with its number if it has one in front. */
    private fun headChapterName(): String {
        var i = chapterIndex
        while (i >= 0) {
            val c = chapters.getOrNull(i) ?: return ""
            val fromToc = c.titleFromToc || (i > 0 && isKnownOpening(i - 1) && chapters[i - 1].titleFromToc)
            if (fromToc) return titleOf(i)
            if (toc.isEmpty()) return titleOf(i).takeUnless { it == c.path.substringAfterLast('/').substringBeforeLast('.') }.orEmpty()
            i--
        }
        return ""
    }

    /** Chapter [i] starts a chapter of the contents (or the book has none), rather than carrying one on. */
    private fun startsChapter(i: Int): Boolean {
        val c = chapters.getOrNull(i) ?: return false
        return toc.isEmpty() || c.titleFromToc || (i > 0 && isKnownOpening(i - 1) && chapters[i - 1].titleFromToc)
    }

    /** The camera's cutout at the top of the screen is [dp] deep: the margins' top row goes below it, the text with it. */
    fun setCutoutTop(dp: Int) {
        if (dp == cutoutTop) return
        val before = settings.marginRoom(cutoutTop)
        cutoutTop = dp
        if (settings.marginRoom(dp) != before) {
            startJson = JSONObject(startJson.ifEmpty { "{}" }).put("settings", JSONObject(settingsJs(settings))).toString()
            js("KLR.apply(${quote(settingsJs(settings))})")
        }
    }

    /** The reader's settings as the page is told them. */
    private fun settingsJs(s: ReaderSettings): String = s.toJs(language, cutoutTop)

    // ---- counting every chapter's pages ------------------------------------------------------------

    /** Counts chapters' pages away from the screen; set by the reader's frame (none in tests of the reader alone). */
    var counter: ChapterCounter? = null

    /** Each chapter's pages, counted at [countedKey]; null until all are counted. */
    private var counted by mutableStateOf<IntArray?>(null)
    private var countedKey by mutableStateOf("")
    /** Counting couldn't be done at this layout: pages are worked out from the chapters laid out instead. */
    private var countFailed by mutableStateOf(false)
    /** Every chapter's pages are being counted. */
    var countingPages by mutableStateOf(false)
        private set
    private var countJob: Job? = null
    private var countToken: Any? = null
    /** A stamp of the book's text, so counts kept from before are used only for the book as it is. */
    private var textStamp = ""

    /** Makes the counter for this book, in the reader's [frame]. */
    fun createCounter(frame: ViewGroup) {
        counter?.close()
        counter = PageCounter(frame, server)
    }

    /**
     * Has every chapter's pages counted at the layout [key] (the reader's, as it reported it), unless they
     * already are or are being: kept from an earlier visit if the book and the layout are the same, or
     * counted one chapter after another, away from the screen.
     */
    private fun countPagesFor(key: String, rawLayout: String) {
        if (key == countedKey && (counted != null || countingPages || countFailed)) return
        countJob?.cancel()
        countedKey = key
        counted = null
        countFailed = false
        val c = counter ?: return
        countingPages = true
        // This count, as against one given up on for a new layout (which mustn't undo this one as it ends).
        val token = Any()
        countToken = token
        countJob = scope.launch {
            try {
                // Counts kept from before, for the book as it is, laid out as it is now, by the same rules:
                // looked for straight away, from the book file's own checksums where it has them.
                val stamp = withContext(Dispatchers.IO) { runCatching { (files as? EpubFileFiles)?.epub?.stamp() }.getOrNull() }
                    ?: run { while (!lengthsKnown) delay(50); textStamp }
                val stored = "$key|$stamp|${PageCounter.fingerprint(assets)}"
                val kept = withContext(Dispatchers.IO) { store?.pageCounts(stored) }
                if (kept != null && kept.size == chapters.size) {
                    counted = kept
                    return@launch
                }
                // The chapters' lengths and openings first (the openings are counted with their chapters).
                while (!lengthsKnown) delay(50)
                // Let the chapter being opened settle before counting the rest.
                delay(400)
                val result = IntArray(chapters.size)
                for (i in chapters.indices) {
                    if (withContext(Dispatchers.IO) { isOpening(i) || isPairedLeft(i) }) continue
                    // As the reader opens it (where a page of its own kind goes changes how it's laid out).
                    val start = JSONObject()
                        .put("settings", JSONObject(settingsJs(settings)))
                        .put("chapters", aroundJs(i))
                        .put("words", wordsJs())
                        .put("opening", startsChapter(i))
                        .apply { runCatching { pageKind(i).place }.getOrNull()?.let { put("place", it) } }
                        .toString()
                    val got = withTimeoutOrNull(20_000) {
                        try {
                            c.count(chapters[i].path, start)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.w("Reader", "Counting pages went wrong", e)
                            null
                        }
                    }
                    if (got == null || got.layout != rawLayout) {
                        AppLog.w("Reader", if (got == null) "Couldn't count the pages of ${chapters[i].path}" else "Pages counted at a different layout (${got.layout}, not $rawLayout)")
                        countFailed = true
                        return@launch
                    }
                    result[i] = got.columns
                }
                counted = result
                withContext(Dispatchers.IO) { store?.savePageCounts(stored, result) }
                AppLog.i("Reader", "Counted the pages of ${chapters.size} chapters: ${result.sum()} in all")
            } finally {
                if (countToken === token) {
                    countingPages = false
                    c.close()
                }
            }
        }
    }

    /** The page the progress bar at [f] goes to, counted through the whole book. */
    fun bookPageAt(f: Float): Int? {
        val bp = bookPages ?: return null
        val (i, p) = chapterAtBookProgress(f) ?: return null
        return (bp.before(i) + ((bp.counts[i] - 1).coerceAtLeast(0) * p).roundToInt() + 1).coerceIn(1, bp.total)
    }

    /** The chapter the progress bar at [f] goes to, by its title. */
    fun chapterTitleAt(f: Float): String = chapterAtBookProgress(f)?.let { (i, _) -> titleOf(if (isKnownOpening(i)) i + 1 else i) }.orEmpty()

    /** The page [p] is on, counted through the whole book. */
    fun bookPageOf(p: ReaderPosition): Int? {
        val bp = bookPages ?: return null
        val i = chapters.indexOfFirst { it.path == p.path }.takeIf { it >= 0 } ?: return null
        return (bp.before(i) + ((bp.counts[i] - 1).coerceAtLeast(0) * p.progress.coerceIn(0f, 1f)).roundToInt() + 1).coerceIn(1, bp.total)
    }

    /** Where the progress bar at [f] goes: the chapter, and how far through it. */
    private fun chapterAtBookProgress(f: Float): Pair<Int, Float>? {
        if (chapters.isEmpty()) return null
        val wanted = f.coerceIn(0f, 1f) * weightTotal
        var acc = 0f
        for (i in chapters.indices) {
            val w = weights[i]
            if (acc + w >= wanted || i == chapters.size - 1) return i to ((wanted - acc) / w).coerceIn(0f, 1f)
            acc += w
        }
        return null
    }

    private fun isKnownOpening(i: Int) = openings[i] == true

    /** Chapter [i] is known to be shown in front of the next: an opening, or a short page beside it. */
    private fun isKnownJoined(i: Int) = openings[i] == true || pairs[i] == true

    /** A chapter's text, with its opening's (or the short page beside it) when that's shown in front of it. */
    private fun textWithOpening(i: Int): Float =
        weights.getOrElse(i) { 0f } + if (i > 0 && isKnownJoined(i - 1)) weights.getOrElse(i - 1) { 0f } else 0f

    /** How much text a page holds at the present size, from the chapters laid out so far. */
    private fun textPerPage(): Float? {
        var text = 0f
        var pages = 0f
        // Chapters of two pages or more (the last page of each only part full).
        for ((i, n) in laidOutPages) {
            if (n >= 2 && !isKnownJoined(i)) {
                text += textWithOpening(i)
                pages += n - 0.5f
            }
        }
        if (pages > 0f && text > 0f) return text / pages
        // Only short chapters so far: the one showing, as it is.
        val n = laidOutPages[chapterIndex] ?: return null
        return (textWithOpening(chapterIndex) / n).takeIf { it > 0f }
    }

    /** Which chapters are openings shown with the chapter after (for counting pages), worked out away from the screen. */
    private suspend fun findOpenings() {
        withContext(Dispatchers.IO) {
            for (i in chapters.indices) {
                if (settings.joinOpenings) runCatching { isOpening(i) }
                runCatching { isPairedLeft(i) }
            }
        }
    }

    // ---- the WebView ----------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun createView(context: Context): WebView = ReaderWebView(context).apply {
        onSelectionAction = { action, mode -> selectionAction(action) { mode.finish() } }
        setUpForReading()
        setBackgroundColor(this@ReaderController.settings.theme.bg.toInt())
        keepScreenOn = this@ReaderController.settings.keepScreenOn || autoscroll
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                server.respond(request.url.toString())

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(BookServer.BOOK)) {
                    // Only the app moves between chapters; anything else is a link that got through.
                    if (url.substringBefore('#') != view.url?.substringBefore('#')) followBookLink(url)
                    return true
                }
                if (!url.startsWith(BookServer.ORIGIN)) externalLink = url
                return true
            }
        }
        addJavascriptInterface(Bridge(), "KL")
        webView = this
    }

    fun release() {
        saveNow()
        turner?.release()
        turner = null
        countJob?.cancel()
        counter?.close()
        counter = null
        scope.cancel()
        webView?.let { v ->
            v.stopLoading()
            v.removeJavascriptInterface("KL")
            v.destroy()
        }
        webView = null
    }

    private fun js(code: String) {
        main.post { webView?.evaluateJavascript(code, null) }
    }

    private fun quote(s: String) = JSONObject.quote(s)

    // ---- moving around --------------------------------------------------------------------------

    /** A chapter to open at instead of where the book was left (reading from the editor). */
    var initialPath: String? = null

    /** Opens where the book was left, or at its start. */
    fun start() {
        initialPath?.let { p ->
            val i = chapters.indexOfFirst { it.path == p }
            if (i >= 0) {
                open(i, target("start"))
                return
            }
        }
        val pos = position
        val index = pos?.let { p -> chapters.indexOfFirst { it.path == p.path } }?.takeIf { it >= 0 }
        if (pos != null && index != null) {
            open(index, target("anchor", pos.anchor, pos.progress))
        } else {
            val startPath = runCatching { ChapterOps.startPath(structure) }.getOrNull()
            val first = chapters.indexOfFirst { it.path == startPath }.takeIf { it >= 0 }
                ?: chapters.indexOfFirst { it.linear }.takeIf { it >= 0 } ?: 0
            open(first, target("start"))
        }
    }

    private fun target(type: String, value: Any? = null, progress: Float? = null, extra: Map<String, Any?> = emptyMap()) =
        JSONObject().apply {
            put("type", type)
            value?.let { put("value", it) }
            progress?.let { put("progress", it.toDouble()) }
            extra.forEach { (k, v) -> if (v != null) put(k, v) }
        }

    fun open(index: Int, target: JSONObject) {
        // A chapter's opening page is shown with the chapter's text, so it's never opened alone: its
        // text opens instead, at its start (a link into the opening still finds its place). The same
        // for a short page shown beside the next.
        if (isOpening(index) || isPairedLeft(index)) {
            open(index + 1, if (target.optString("type") == "fragment") target else target("start"))
            return
        }
        val c = chapters.getOrNull(index) ?: return
        atEnd = false
        startJson = JSONObject()
            .put("settings", JSONObject(settingsJs(settings)))
            .put("target", target)
            .put("annotations", annotationsJs(c.path))
            .put("chapters", aroundJs(index))
            .put("words", wordsJs())
            .apply { if (autoscroll) put("auto", autoJs().put("startDelay", 1500)) }
            // It starts a chapter: its first page can go on the right, its first letter dropped (see reader.js).
            .put("opening", startsChapter(index))
            .apply { runCatching { pageKind(index).place }.getOrNull()?.let { put("place", it) } }
            .toString()
        if (index == chapterIndex && !loading && webView?.url?.let { BookServer.pathOf(it)?.first } == c.path) {
            js("KLR.go(${quote(target.toString())}, false)")
            return
        }
        chapterIndex = index
        loading = true
        main.post { webView?.loadUrl(server.urlFor(c.path)) }
    }

    /** The words the reader's script shows itself (at the end of a scrolled chapter), in KoLib's language. */
    private fun wordsJs(): JSONObject = JSONObject()
        .put("endOfBook", tr("End of book"))
        .put("endOfChapter", tr("End of chapter"))
        .put("nextChapterTitled", tr("Next chapter: {0}"))
        .put("nextChapter", tr("Next chapter"))
        .put("keepReading", tr("Tap the button or pull up to keep reading"))
        .put("letGoNext", tr("Let go to open the next chapter"))
        .put("pullNext", tr("Pull up for the next chapter"))
        .put("letGoPrevious", tr("Let go to open the previous chapter"))
        .put("pullPrevious", tr("Pull down for the previous chapter"))

    fun nextPage() = js("KLR.next()")
    fun previousPage() = js("KLR.prev()")

    /** Moving somewhere else in the book stops a turn that's under way. */
    private fun stopTurn() = turner?.cancel()

    private fun nextLinear(from: Int, step: Int): Int? {
        var i = from + step
        while (i in chapters.indices) {
            if (chapters[i].linear) {
                // Forward onto an opening: its chapter, which shows it. Back onto one: it's the opening of the
                // chapter being left, already shown, so on to the chapter before. (A short page shown
                // beside the next, the same.)
                if (isOpening(i) || isPairedLeft(i)) {
                    if (step > 0) return i + 1
                    i += step
                    continue
                }
                return i
            }
            i += step
        }
        return null
    }

    // ---- chapter openings ----------------------------------------------------------------------------

    /** Whether each chapter is an opening shown with the chapter after it, as found so far. */
    private val openings = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    /**
     * Chapter [i] is a chapter's opening (its number, an ornament) in a file of its own, shown in front
     * of the chapter after it rather than on a page by itself.
     */
    fun isOpening(i: Int): Boolean {
        if (!settings.joinOpenings) return false
        return openings.getOrPut(i) {
            val a = chapters.getOrNull(i)
            val b = chapters.getOrNull(i + 1)
            // Too long to be an opening, or followed by too little to be its chapter, as the words counted
            // already say: not read through again (in a long book, most chapters are one or the other).
            val plainlyNot = wordsCounted && i + 1 < chapterWords.size &&
                (chapterWords[i] > ChapterOpenings.MOST_WORDS || chapterWords[i + 1] < ChapterOpenings.LEAST_WORDS)
            !plainlyNot && a != null && b != null &&
                runCatching { ChapterOpenings.joins(a, files.text(a.path).orEmpty(), b, files.text(b.path).orEmpty()) }.getOrDefault(false)
        }
    }

    /** The chapter at [path] with its opening in front of it, if it has one; or the short page shown beside it. */
    private fun joined(path: String, text: String): String? {
        val i = chapters.indexOfFirst { it.path == path }
        if (i <= 0) return null
        val before = chapters[i - 1]
        if (isOpening(i - 1)) return ChapterOpenings.join(text, path, files.text(before.path) ?: return null, before.path)
        if (isPairedLeft(i - 1)) return ChapterOpenings.join(text, path, files.text(before.path) ?: return null, before.path, ChapterOpenings.PAIR_BOX)
        return null
    }

    // ---- short pages, two pages side by side --------------------------------------------------------

    /** What each chapter is, if it's a page of its own kind, as found so far. */
    private val kinds = java.util.concurrent.ConcurrentHashMap<Int, PageKind>()

    /** Whether each chapter is a short page shown on the left beside the next, as found so far. */
    private val pairs = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    /** The first chapter in the reading order: where the book's cover is. */
    private val firstLinear: Int by lazy { chapters.indexOfFirst { it.linear }.coerceAtLeast(0) }

    /** What chapter [i] is: a cover, a title page, a dedication, a quote, a short page, or a chapter of text. */
    fun pageKind(i: Int): PageKind = kinds[i] ?: run {
        val c = chapters.getOrNull(i) ?: return PageKind.TEXT
        // Words counted already (when the book opened): more than a short page has, it's a chapter of text.
        val counted = chapterWords.getOrNull(i)
        val kind = if (!c.linear || (counted != null && counted > PagePlacement.SHORT_WORDS)) PageKind.TEXT else {
            val f = PagePlacement.facts(files.text(c.path).orEmpty(), c.path, c.title) { p -> files.bytes(p)?.let { ImageSize.of(it) } }
            val bookStart = i == firstLinear || innerBooks.any { it.first == i }
            // A book inside this one starts with its cover, or its title page: the title page, after a cover if it has one.
            val titlePlace = innerBooks.any { it.first == i || (it.first == i - 1 && pageKind(i - 1) == PageKind.COVER) }
            PagePlacement.kind(f, coverPlace = bookStart, titlePlace = titlePlace)
        }
        kinds[i] = kind
        kind
    }

    /**
     * Chapter [i] is a short page (a title page, a notice, a dedication) shown on the left beside the
     * chapter after it, another short page (a notice, a quote), rather than each alone on a screen. In one
     * page for the reader: the left one in a box in front of the right one, with a page break between.
     */
    fun isPairedLeft(i: Int): Boolean = pairs[i] ?: run {
        val a = chapters.getOrNull(i)
        val b = chapters.getOrNull(i + 1)
        val paired = a != null && b != null && a.linear && b.linear &&
            pageKind(i).canBeLeft && pageKind(i + 1).canBeRight &&
            !isOpening(i) && !isOpening(i + 1) && (i == 0 || !isPairedLeft(i - 1)) &&
            runCatching {
                ChapterOpenings.join(files.text(b.path)!!, b.path, files.text(a.path)!!, a.path, ChapterOpenings.PAIR_BOX) != null
            }.getOrDefault(false)
        pairs[i] = paired
        paired
    }

    private fun titleOf(index: Int): String =
        (if (index > 0 && isOpening(index - 1)) chapters[index - 1].title else chapters.getOrNull(index)?.title).orEmpty()

    /** The chapter's title as the reader shows it: its opening's, if it has one ("1 The Hook" rather than "The Hook"). */
    val chapterTitle: String get() = titleOf(chapterIndex)

    /** Where the contents point for the chapter on screen: its own file, and its opening's. */
    val contentsPaths: List<String>
        get() = listOfNotNull(
            chapter?.path,
            chapters.getOrNull(chapterIndex - 1)?.takeIf { chapterIndex > 0 && (isOpening(chapterIndex - 1) || isPairedLeft(chapterIndex - 1)) }?.path,
        )

    fun nextChapter() {
        val i = nextLinear(chapterIndex, 1)
        if (i == null) {
            atEnd = true
            stopAutoscroll()
        } else open(i, target("start"))
    }

    /** For the end of a scrolled chapter: the next chapter's title, and whether there are chapters either side. */
    private fun aroundJs(index: Int): JSONObject = JSONObject().apply {
        val next = nextLinear(index, 1)
        put("hasNext", next != null)
        put("hasPrev", nextLinear(index, -1) != null)
        next?.let { n -> titleOf(n).takeIf { it.isNotBlank() }?.let { put("next", it) } }
    }

    // ---- autoscroll ----------------------------------------------------------------------------------

    /**
     * Moving on by itself at the reading speed chosen: scrolling steadily, or turning each page when
     * there's been time to read it. It carries on into the next chapter.
     */
    var autoscroll by mutableStateOf(false)
        private set

    private fun autoJs(on: Boolean = autoscroll): JSONObject = JSONObject()
        .put("on", on)
        .put("wpm", settings.autoWpm)
        .put("endPause", settings.autoEnd.seconds)
        .put("resume", settings.autoResume.seconds)

    fun startAutoscroll() {
        if (autoscroll) return
        autoscroll = true
        tellAuto()
    }

    fun stopAutoscroll() {
        if (!autoscroll) return
        autoscroll = false
        tellAuto()
    }

    fun toggleAutoscroll() = if (autoscroll) stopAutoscroll() else startAutoscroll()

    private fun tellAuto() {
        // A chapter opened from here on carries it on.
        startJson = runCatching {
            JSONObject(startJson).apply { if (autoscroll) put("auto", autoJs().put("startDelay", 1500)) else remove("auto") }.toString()
        }.getOrDefault(startJson)
        js("KLR.auto(${quote(autoJs().toString())})")
        main.post { webView?.keepScreenOn = settings.keepScreenOn || autoscroll }
    }

    /** Something is shown over the page (a panel, a note...): autoscroll waits till it's gone. */
    fun holdAutoscroll(held: Boolean) = js("KLR.autoHold($held)")

    fun previousChapter(atItsEnd: Boolean = false) {
        val i = nextLinear(chapterIndex, -1) ?: return
        open(i, target(if (atItsEnd) "end" else "start"))
    }

    fun goTo(node: TocNode) {
        val i = chapters.indexOfFirst { it.path == node.path }.takeIf { it >= 0 } ?: return
        stopTurn()
        remember()
        open(i, if (node.fragment != null) target("fragment", node.fragment) else target("start"))
    }

    fun goTo(p: ReaderPosition) {
        val i = chapters.indexOfFirst { it.path == p.path }.takeIf { it >= 0 } ?: return
        stopTurn()
        open(i, target("anchor", p.anchor, p.progress))
    }

    /** Somewhere in the book by how far through it (the progress slider). */
    fun goToBookProgress(f: Float) {
        stopTurn()
        val (i, p) = chapterAtBookProgress(f) ?: return
        val exact = exactPages()
        if (exact != null && exact[i] > 0) {
            // With every chapter counted: the very page the bar named while it was dragged (on the screen
            // it's on, when two pages show side by side).
            open(i, target("page", ((exact[i] - 1) * p).roundToInt() / perScreen))
        } else {
            open(i, target("progress", p.toDouble()))
        }
    }

    fun goToHit(hit: ReaderHit) {
        stopTurn()
        remember()
        open(hit.chapterIndex, JSONObject(hit.target))
    }

    /**
     * Where the reader was before last moving with the progress bar: a button with the controls
     * goes back there, and then back again, to and fro between the two. Kept only while the book is open.
     */
    var barReturn by mutableStateOf<ReaderPosition?>(null)
        private set
    /** The page [barReturn] was on as numbered there, and the layout it was numbered at. */
    private var barReturnPage by mutableStateOf<Pair<String, Int>?>(null)

    /** The page the button with the controls goes back to, counted through the whole book. */
    val barReturnBookPage: Int?
        get() {
            val back = barReturn ?: return null
            barReturnPage?.let { (key, n) -> if (key == layoutKey && exactPages() != null) return n }
            return bookPageOf(back)
        }

    /** The progress bar let go at [f]: there, remembering where the reader was. */
    fun goToBookProgressFromBar(f: Float) {
        val here = position
        // Let go where it already was: not a move.
        if (here != null && abs(f - bookProgress) >= 0.002f) {
            barReturn = here
            barReturnPage = bookPage?.let { layoutKey to it }
        }
        goToBookProgress(f)
    }

    /** To the place remembered from the progress bar, remembering this one to come back to. */
    fun swapWithBarReturn() {
        val there = barReturn ?: return
        val herePage = bookPage
        barReturn = position
        barReturnPage = herePage?.let { layoutKey to it }
        goTo(there)
    }

    /** For pictures of the reading screen in tests. */
    internal fun showBarReturnForPreview(p: ReaderPosition) {
        barReturn = p
    }

    /** Goes back to where the reader was before following a link. */
    fun goBack() {
        val p = jumpedFrom ?: return
        jumpedFrom = null
        goTo(p)
    }

    private fun remember() {
        position?.let { jumpedFrom(it) }
    }

    private fun followBookLink(url: String) {
        val (path, fragment) = BookServer.pathOf(url) ?: return
        val i = chapters.indexOfFirst { it.path == path }
        if (i < 0) {
            if (BookServer.mediaType(path).startsWith("image/")) image = url
            return
        }
        open(i, if (fragment != null) target("fragment", fragment, extra = mapOf("flash" to true)) else target("start"))
    }

    // ---- settings and bookmarks -----------------------------------------------------------------

    fun applySettings(s: ReaderSettings) {
        val old = settings
        settings = s
        if (s.joinOpenings != old.joinOpenings) {
            // The chapter is served differently now: open it again where it was.
            openings.clear()
            pairs.clear()
            laidOutPages.clear()
            scope.launch { findOpenings() }
            stopTurn()
            val p = position
            if (!loading && p != null && p.path == chapter?.path) {
                loading = true
                startJson = JSONObject(startJson.ifEmpty { "{}" }).put("settings", JSONObject(settingsJs(s)))
                    .put("target", target("anchor", p.anchor, p.progress)).toString()
                main.post { webView?.loadUrl(server.urlFor(p.path)) }
                return
            }
        }
        // What the page shows, or the room kept round it for the margins, changed: laid out again.
        val pageChanged = s.pageOnly() != old.pageOnly() || s.marginRoom(cutoutTop) != old.marginRoom(cutoutTop)
        if (pageChanged) stopTurn()
        startJson = JSONObject(startJson.ifEmpty { "{}" }).put("settings", JSONObject(settingsJs(s))).toString()
        main.post {
            webView?.setBackgroundColor(s.theme.bg.toInt())
            webView?.keepScreenOn = s.keepScreenOn || autoscroll
        }
        if (pageChanged) {
            js("KLR.apply(${quote(settingsJs(s))})")
        }
        if (autoscroll && (s.autoWpm != old.autoWpm || s.autoEnd != old.autoEnd || s.autoResume != old.autoResume)) tellAuto()
    }

    val currentBookmark: ReaderPosition?
        get() {
            val p = position ?: return null
            val half = if (pages > 1) 0.5f / (pages - 1) else 1f
            return bookmarks.firstOrNull { b ->
                b.path == p.path && (b.anchor == p.anchor || kotlin.math.abs(b.progress - p.progress) < half)
            }
        }

    fun toggleBookmark() {
        val existing = currentBookmark
        bookmarks = if (existing != null) bookmarks - existing else {
            val p = position ?: return
            (bookmarks + p.copy(at = System.currentTimeMillis())).sortedWith(compareBy({ b -> chapters.indexOfFirst { it.path == b.path } }, { it.progress }))
        }
        saveSoon()
    }

    fun removeBookmark(b: ReaderPosition) {
        bookmarks = bookmarks - b
        saveSoon()
    }

    // ---- highlights and notes ------------------------------------------------------------------------

    /** A highlight that was tapped, shown over the page with what can be done to it. [rect] is in page pixels. */
    data class OpenAnnotation(val id: String, val rect: android.graphics.RectF)

    var openAnnotation by mutableStateOf<OpenAnnotation?>(null)
    /** The highlight whose note is being written. */
    var editingNote by mutableStateOf<Annotation?>(null)
    /** Words to look up (the look-up sheet is showing). */
    var lookUpText by mutableStateOf<String?>(null)
    /** Words to search the book for (the search panel opens with them). */
    var searchRequest by mutableStateOf<String?>(null)

    fun annotation(id: String): Annotation? = annotations.firstOrNull { it.id == id }

    /** Shown for a moment while a swipe down the left edge changes the brightness. */
    var levelHint by mutableStateOf<Float?>(null)

    /** Where each chapter starts, as a share of the book, for marks on the progress bar. */
    val chapterMarks: List<Float>
        get() {
            if (chapters.size < 2) return emptyList()
            val out = ArrayList<Float>()
            var acc = 0f
            for (i in chapters.indices) {
                if (i > 0 && chapters[i].linear && !isKnownJoined(i - 1)) out += acc / weightTotal
                acc += weights.getOrElse(i) { 0f }
            }
            return out
        }

    /** The contents panel's tab (contents, bookmarks, highlights), kept while the book is open. */
    var contentsTab by mutableStateOf(0)

    /** The reading settings' tab (text, page, layout, controls, autoscroll), kept while the book is open. */
    var lookTab by mutableStateOf(0)

    /** For pictures of the reading screen in tests. */
    internal fun showAnnotationsForPreview(list: List<Annotation>) {
        annotations = list
    }

    private fun annotationsJs(path: String) = org.json.JSONArray().apply {
        annotations.filter { it.path == path }.forEach { put(it.toJs()) }
    }

    private fun pushAnnotations() {
        val c = chapter ?: return
        js("KLR.setAnnotations(${quote(JSONObject().put("list", annotationsJs(c.path)).toString())})")
    }

    private fun changeAnnotations(list: List<Annotation>) {
        annotations = list
        // The next chapter opened carries them; this one is told now.
        startJson = runCatching { JSONObject(startJson).put("annotations", annotationsJs(chapter?.path.orEmpty())).toString() }.getOrDefault(startJson)
        pushAnnotations()
        saveSoon()
    }

    /** Where the chapter order puts a chapter file, for sorting highlights. */
    fun chapterOrder(path: String): Int = chapters.indexOfFirst { it.path == path }.let { if (it < 0) Int.MAX_VALUE else it }

    /** What's selected in the page, or null. */
    private fun withSelection(then: (JSONObject?) -> Unit) {
        val v = webView ?: return then(null)
        v.evaluateJavascript("KLR.selection()") { r ->
            val text = runCatching { org.json.JSONTokener(r).nextValue() as? String }.getOrNull()
            then(text?.takeIf { it.isNotEmpty() }?.let { runCatching { JSONObject(it) }.getOrNull() })
        }
    }

    /** One of the reader's items on the selection toolbar; [done] closes the toolbar. */
    fun selectionAction(action: SelectionAction, done: () -> Unit) {
        withSelection { sel ->
            if (sel == null) {
                done()
                return@withSelection
            }
            when (action) {
                SelectionAction.HIGHLIGHT -> addAnnotation(sel)
                SelectionAction.NOTE -> addAnnotation(sel)?.let { editingNote = it }
                SelectionAction.LOOK_UP -> lookUpText = sel.optString("text").trim()
                SelectionAction.SEARCH -> searchRequest = sel.optString("text").replace(Regex("\\s+"), " ").trim()
            }
            js("KLR.clearSelection()")
            done()
        }
    }

    private fun addAnnotation(sel: JSONObject): Annotation? {
        val c = chapter ?: return null
        // The same passage again: it's already highlighted.
        annotations.firstOrNull { it.path == c.path && it.start == sel.optString("start") && it.end == sel.optString("end") }?.let { return it }
        val chapterAt = sel.optDouble("progress", 0.0).toFloat()
        var before = 0f
        for (i in 0 until chapterIndex) before += weights.getOrElse(i) { 0f }
        val book = ((before + weights.getOrElse(chapterIndex) { 0f } * chapterAt) / weightTotal).coerceIn(0f, 1f)
        val a = Annotation(
            path = c.path,
            start = sel.optString("start"),
            end = sel.optString("end"),
            text = sel.optString("text"),
            occurrence = sel.optInt("occurrence", 0),
            color = settings.highlightColor,
            style = settings.highlightStyle,
            chapterTitle = chapterTitle,
            bookProgress = book,
        )
        changeAnnotations(annotations + a)
        return a
    }

    fun updateAnnotation(a: Annotation) {
        changeAnnotations(annotations.map { if (it.id == a.id) a.copy(modified = System.currentTimeMillis()) else it })
    }

    fun removeAnnotation(id: String) {
        if (openAnnotation?.id == id) openAnnotation = null
        changeAnnotations(annotations.filterNot { it.id == id })
    }

    /** Goes to a highlight, wherever it is in the book, and flashes it. */
    fun goTo(a: Annotation) {
        val i = chapters.indexOfFirst { it.path == a.path }.takeIf { it >= 0 } ?: return
        stopTurn()
        open(i, target("range", extra = mapOf("start" to a.start, "end" to a.end, "text" to a.text, "occurrence" to a.occurrence, "flash" to true)))
    }

    fun dismissEnd() {
        atEnd = false
    }

    /** Goes to the footnote itself, remembering the page to come back to. */
    fun followNote(href: String) {
        remember()
        followBookLink(href)
    }

    // ---- search ---------------------------------------------------------------------------------

    /** The last search, so it's still there when the search panel opens again. */
    var lastSearch: String = ""
    var lastHits: List<ReaderHit> = emptyList()

    suspend fun search(query: String): List<ReaderHit> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        val matches = runCatching { FindReplace.search(files, structure, FindReplace.Options(query.trim()), limit = 2000) }.getOrDefault(emptyList())
        val seen = HashMap<String, Int>()
        matches.map { m ->
            val k = seen.merge(m.path, 1, Int::plus)!! - 1
            val index = chapters.indexOfFirst { it.path == m.path }
            ReaderHit(
                chapterIndex = index,
                chapter = m.chapter,
                before = m.before,
                text = m.text,
                after = m.after,
                target = target("search", extra = mapOf("text" to m.text, "occurrence" to k, "segment" to m.segment, "offset" to m.start)).toString(),
            )
        }.filter { it.chapterIndex >= 0 }
    }

    fun clearSearch() = js("KLR.clear()")

    // ---- keeping the place ----------------------------------------------------------------------

    private fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            saveNow()
        }
    }

    fun saveNow() {
        val s = store ?: return
        s.save(ReaderState(position, bookmarks, annotations))
    }

    private inner class Bridge {
        @JavascriptInterface
        fun start(): String = startJson

        @JavascriptInterface
        fun ready(json: String) = main.post {
            loading = false
            update(json)
            turner?.chapterReady()
        }

        @JavascriptInterface
        fun position(json: String) = main.post { update(json) }

        @JavascriptInterface
        fun edge(direction: Int) = main.post {
            if (turner?.edge(direction) != true) {
                if (direction > 0) nextChapter() else previousChapter(atItsEnd = true)
            }
        }

        /** A page turn the app is to draw (Curl, Fade); [y] is where on the page it was asked for, 0 to 1. */
        @JavascriptInterface
        fun turn(direction: Int, y: Double) = main.post {
            if (turner?.turn(direction, y.toFloat()) != true) js("KLR.turnNow($direction)")
        }

        @JavascriptInterface
        fun selecting(on: Boolean) {
            selecting = on
        }

        /** A highlight was tapped. */
        @JavascriptInterface
        fun annotation(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            val r = o.optJSONObject("rect") ?: JSONObject()
            val x = r.optDouble("x", 0.0).toFloat()
            val y = r.optDouble("y", 0.0).toFloat()
            openAnnotation = OpenAnnotation(o.optString("id"), android.graphics.RectF(x, y, x + r.optDouble("w", 0.0).toFloat(), y + r.optDouble("h", 0.0).toFloat()))
        }

        @JavascriptInterface
        fun tap(zone: String) = main.post { if (zone == "center") controlsVisible = !controlsVisible }

        @JavascriptInterface
        fun link(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            positionFrom(o.optString("from"))?.let { jumpedFrom(it) }
            followBookLink(o.optString("href"))
        }

        @JavascriptInterface
        fun jumped(json: String) = main.post { positionFrom(json)?.let { jumpedFrom(it) } }

        @JavascriptInterface
        fun note(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            note = ReaderNote(o.optString("html"), o.optString("href"))
        }

        @JavascriptInterface
        fun image(src: String) = main.post { image = src }

        @JavascriptInterface
        fun external(url: String) = main.post { externalLink = url }

        /** Autoscroll stopped in the page (a touch, the end of a chapter), as it was set to. */
        @JavascriptInterface
        fun auto(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            if (!o.optBoolean("on")) {
                autoscroll = false
                startJson = runCatching { JSONObject(startJson).apply { remove("auto") }.toString() }.getOrDefault(startJson)
                webView?.keepScreenOn = settings.keepScreenOn
            }
        }

        @JavascriptInterface
        fun log(message: String) = AppLog.d("Reader", message)
    }

    private fun positionFrom(json: String): ReaderPosition? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val c = chapter ?: return null
        return ReaderPosition(c.path, o.optString("anchor").takeIf { it.isNotEmpty() && it != "null" }, o.optDouble("progress", 0.0).toFloat(),
            bookProgress, o.optString("snippet"), c.title)
    }

    private fun update(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        pages = o.optInt("pages", 1).coerceAtLeast(1)
        if (o.has("spread")) spread = o.optBoolean("spread")
        if (o.has("scrolled")) scrolledHere = o.optBoolean("scrolled")
        chapterPages = o.optInt("columns", pages * perScreen).coerceIn(1, pages * perScreen)
        o.optJSONObject("frame")?.let { f ->
            frame = PageFrame(
                width = f.optDouble("w", 0.0).toFloat(),
                height = f.optDouble("h", 0.0).toFloat(),
                perScreen = f.optInt("perScreen", 1).coerceIn(1, 2),
                padX = f.optDouble("padX", 0.0).toFloat(),
                col = f.optDouble("col", 0.0).toFloat(),
                padTop = f.optDouble("padTop", 0.0).toFloat(),
                padBottom = f.optDouble("padBottom", 0.0).toFloat(),
            ).takeIf { it.width > 0f && it.col > 0f }
        }
        if (o.has("picture")) picturePage = o.optBoolean("picture")
        if (o.has("blank")) blankBefore = if (perScreen == 2) o.optInt("blank", 0).coerceIn(0, 1) else 0
        else if (perScreen != 2) blankBefore = 0
        if (o.has("display")) displayPage = o.optBoolean("display")
        val newPage = o.optInt("page", 0)
        if (jumpedFrom != null && (newPage != page || chapterIndex != reportedChapter)) {
            // Landing is one move; three pages on from there, going back isn't offered any more.
            if (++movesSinceJump > 3) jumpedFrom = null
        }
        reportedChapter = chapterIndex
        page = newPage
        // The chapter's pages at this text size and page size, for counting pages through the book. Laid
        // out differently, the pages counted so far no longer hold.
        if (o.has("layout")) {
            val raw = o.optString("layout")
            val key = raw + "|" + settings.joinOpenings
            if (key != layoutKey) {
                layoutKey = key
                laidOutPages.clear()
            }
            if (laidOutPages[chapterIndex] != chapterPages) laidOutPages[chapterIndex] = chapterPages
            // Every chapter counted at this layout, so pages keep their numbers wherever the reader goes.
            countPagesFor(key, raw)
            exactPages()?.getOrNull(chapterIndex)?.let { n ->
                if (n != chapterPages && n > 0) AppLog.d("Reader", "Chapter $chapterIndex laid out at $chapterPages pages; counted at $n")
            }
        }
        // Scrolling says how far down the chapter it is; pages are counted.
        chapterProgress = if (o.has("progress")) o.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
        else if (pages > 1) page.toFloat() / (pages - 1) else 0f
        val c = chapter ?: return
        if (o.has("anchor") || o.has("snippet")) {
            position = ReaderPosition(
                path = c.path,
                anchor = o.optString("anchor").takeIf { it.isNotEmpty() && it != "null" },
                progress = chapterProgress,
                bookProgress = bookProgress,
                snippet = o.optString("snippet"),
                chapterTitle = chapterTitle,
            )
            saveSoon()
        }
    }

    /** For tests: the chapter at [path] as the reader is given it (with what's shown in front of it). */
    internal fun servedText(path: String): String? = files.text(path)?.let { joined(path, it) ?: it }

    /** For tests: as if the page reported [json] (where it is, and how it's laid out). */
    internal fun reported(json: String) = update(json)

    /** For tests: as if the chapter had finished laying out, at [json]. */
    internal fun readied(json: String) {
        loading = false
        update(json)
    }

    /** For tests: the chapters' lengths have been counted. */
    internal val lengthsCounted: Boolean get() = lengthsKnown

    /** For pictures of the reading screen in tests, where there's no WebView to report pages. */
    internal fun showForPreview(chapter: Int, page: Int, pages: Int, columns: Int = pages, twoUp: Boolean = false, blank: Int = 0, display: Boolean = false) {
        chapterIndex = chapter
        this.page = page
        this.pages = pages
        spread = twoUp
        blankBefore = if (twoUp) blank else 0
        displayPage = display
        chapterPages = columns
        chapterProgress = if (pages > 1) page.toFloat() / (pages - 1) else 0f
        loading = false
        laidOutPages[chapter] = columns
    }

    companion object {
        const val WORDS_PER_MINUTE = 250f
    }
}
