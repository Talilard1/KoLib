package dev.kobolibrary.app.reader

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
) {
    /** The book's language, for looking words up in the right Wikipedia. */
    val bookLanguage: String? get() = language

    val chapters = structure.chapters
    val toc: List<TocNode> = structure.toc
    private val server = BookServer(assets, files).apply { transform = { path, text -> withOpening(path, text) } }
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var chapterIndex by mutableStateOf(0)
        private set
    var page by mutableStateOf(0)
        private set
    var pages by mutableStateOf(1)
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
                v.evaluateJavascript("KLR.turnNow($dir)") { r -> done(r == "true") }
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

    /** The chapter's words have been counted (so the time left in it means something). */
    val chapterWordsKnown: Boolean get() = chapterWords.getOrElse(chapterIndex) { 0 } > 0

    init {
        store?.load()?.let { s ->
            annotations = s.annotations
            bookmarks = s.bookmarks
            position = s.position
        }
        scope.launch {
            val counted = withContext(Dispatchers.IO) {
                chapters.map { c -> runCatching { files.text(c.path)?.let { EpubText.plainText(it) } }.getOrNull().orEmpty() }
            }
            chapterWords = counted.map { EpubText.countWords(it) }.toIntArray()
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

    /** Pages through the whole book at the present text size; null until a chapter has been laid out to go by. */
    val bookPages: BookPages?
        get() {
            if (!lengthsKnown || chapters.isEmpty()) return null
            val perPage = textPerPage() ?: return null
            return BookPages(IntArray(chapters.size) { i ->
                if (openings[i] == true) 0
                else laidOutPages[i] ?: ceil(textWithOpening(i) / perPage).toInt().coerceAtLeast(1)
            })
        }

    /** The page showing, counted through the whole book (the first is 1). */
    val bookPage: Int?
        get() = bookPages?.let { (it.before(chapterIndex) + page + 1).coerceIn(1, it.total) }

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

    /** A chapter's text, with its opening's when the opening is shown in front of it. */
    private fun textWithOpening(i: Int): Float =
        weights.getOrElse(i) { 0f } + if (i > 0 && openings[i - 1] == true) weights.getOrElse(i - 1) { 0f } else 0f

    /** How much text a page holds at the present size, from the chapters laid out so far. */
    private fun textPerPage(): Float? {
        var text = 0f
        var pages = 0f
        // Chapters of two pages or more (the last page of each only part full).
        for ((i, n) in laidOutPages) {
            if (n >= 2 && openings[i] != true) {
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
        if (!settings.joinOpenings) return
        withContext(Dispatchers.IO) { for (i in chapters.indices) runCatching { isOpening(i) } }
    }

    // ---- the WebView ----------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun createView(context: Context): WebView = ReaderWebView(context).apply {
        onSelectionAction = { action, mode -> selectionAction(action) { mode.finish() } }
        // A WebView left to wrap its content treats the page as having no height (100vh = 0),
        // which squeezed every page down to one line on the tablet.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.textZoom = 100
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.mediaPlaybackRequiresUserGesture = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
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
        // text opens instead, at its start (a link into the opening still finds its place).
        if (isOpening(index)) {
            open(index + 1, if (target.optString("type") == "fragment") target else target("start"))
            return
        }
        val c = chapters.getOrNull(index) ?: return
        atEnd = false
        startJson = JSONObject()
            .put("settings", JSONObject(settings.toJs(language)))
            .put("target", target)
            .put("annotations", annotationsJs(c.path))
            .put("chapters", aroundJs(index))
            .apply { if (autoscroll) put("auto", autoJs().put("startDelay", 1500)) }
            .toString()
        if (index == chapterIndex && !loading && webView?.url?.let { BookServer.pathOf(it)?.first } == c.path) {
            js("KLR.go(${quote(target.toString())}, false)")
            return
        }
        chapterIndex = index
        loading = true
        main.post { webView?.loadUrl(server.urlFor(c.path)) }
    }

    fun nextPage() = js("KLR.next()")
    fun previousPage() = js("KLR.prev()")

    /** Moving somewhere else in the book stops a turn that's under way. */
    private fun stopTurn() = turner?.cancel()

    private fun nextLinear(from: Int, step: Int): Int? {
        var i = from + step
        while (i in chapters.indices) {
            if (chapters[i].linear) {
                // Forward onto an opening: its chapter, which shows it. Back onto one: it's the opening of the
                // chapter being left, already shown, so on to the chapter before.
                if (isOpening(i)) {
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
            a != null && b != null && runCatching { ChapterOpenings.joins(a, files.text(a.path).orEmpty(), b, files.text(b.path).orEmpty()) }.getOrDefault(false)
        }
    }

    /** The chapter at [path] with its opening in front of it, if it has one. */
    private fun withOpening(path: String, text: String): String? {
        val i = chapters.indexOfFirst { it.path == path }
        if (i <= 0 || !isOpening(i - 1)) return null
        val opening = chapters[i - 1]
        return ChapterOpenings.join(text, path, files.text(opening.path) ?: return null, opening.path)
    }

    private fun titleOf(index: Int): String =
        (if (index > 0 && isOpening(index - 1)) chapters[index - 1].title else chapters.getOrNull(index)?.title).orEmpty()

    /** The chapter's title as the reader shows it: its opening's, if it has one ("1 The Hook" rather than "The Hook"). */
    val chapterTitle: String get() = titleOf(chapterIndex)

    /** Where the contents point for the chapter on screen: its own file, and its opening's. */
    val contentsPaths: List<String>
        get() = listOfNotNull(chapter?.path, chapters.getOrNull(chapterIndex - 1)?.takeIf { chapterIndex > 0 && isOpening(chapterIndex - 1) }?.path)

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
        open(i, target("progress", p.toDouble()))
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

    /** The progress bar let go at [f]: there, remembering where the reader was. */
    fun goToBookProgressFromBar(f: Float) {
        val here = position
        // Let go where it already was: not a move.
        if (here != null && abs(f - bookProgress) >= 0.002f) barReturn = here
        goToBookProgress(f)
    }

    /** To the place remembered from the progress bar, remembering this one to come back to. */
    fun swapWithBarReturn() {
        val there = barReturn ?: return
        barReturn = position
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
            laidOutPages.clear()
            scope.launch { findOpenings() }
            stopTurn()
            val p = position
            if (!loading && p != null && p.path == chapter?.path) {
                loading = true
                startJson = JSONObject(startJson.ifEmpty { "{}" }).put("settings", JSONObject(s.toJs(language)))
                    .put("target", target("anchor", p.anchor, p.progress)).toString()
                main.post { webView?.loadUrl(server.urlFor(p.path)) }
                return
            }
        }
        if (s.pageOnly() != old.pageOnly()) stopTurn()
        startJson = JSONObject(startJson.ifEmpty { "{}" }).put("settings", JSONObject(s.toJs(language))).toString()
        main.post {
            webView?.setBackgroundColor(s.theme.bg.toInt())
            webView?.keepScreenOn = s.keepScreenOn || autoscroll
        }
        if (s.pageOnly() != old.pageOnly()) {
            js("KLR.apply(${quote(s.toJs(language))})")
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
                if (i > 0 && chapters[i].linear && openings[i - 1] != true) out += acc / weightTotal
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
            val key = o.optString("layout") + "|" + settings.joinOpenings
            if (key != layoutKey) {
                layoutKey = key
                laidOutPages.clear()
            }
            if (laidOutPages[chapterIndex] != pages) laidOutPages[chapterIndex] = pages
        }
        if (o.has("scrolled")) scrolledHere = o.optBoolean("scrolled")
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

    /** For pictures of the reading screen in tests, where there's no WebView to report pages. */
    internal fun showForPreview(chapter: Int, page: Int, pages: Int) {
        chapterIndex = chapter
        this.page = page
        this.pages = pages
        chapterProgress = if (pages > 1) page.toFloat() / (pages - 1) else 0f
        loading = false
        laidOutPages[chapter] = pages
    }

    companion object {
        const val WORDS_PER_MINUTE = 250f
    }
}
