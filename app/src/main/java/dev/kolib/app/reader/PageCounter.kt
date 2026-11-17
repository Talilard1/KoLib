package dev.kolib.app.reader

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import dev.kolib.app.log.AppLog
import java.util.zip.CRC32
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Counts how many pages a chapter comes to, laid out just as the reader will lay it out. */
interface ChapterCounter {
    /**
     * A chapter's screens of pages, its pages of text ([columns]: with two side by side, up to twice the
     * screens), and what they were laid out at (the reader's layout key, as reader.js gives it).
     */
    class Counted(val pages: Int, val layout: String, val columns: Int = pages)

    /** The pages of the chapter at [path], opened with [start] (as the reader would be); null if it can't be counted. */
    suspend fun count(path: String, start: String): Counted?

    /** Done with counting for now: lets go of what counting needs. */
    fun close()
}

/**
 * Counts chapters' pages in a WebView of its own, which is never seen: it lies under the reader's,
 * the same size, with the same settings, so each chapter is laid out there just as it will be read.
 * The reader numbers pages through the whole book from these counts, so a page's number is the same
 * however the reader got there. The WebView is made when there's counting to do, and let go after.
 *
 * The WebView holds one page (reader/counter.xhtml, or .html for chapters served as HTML) that each
 * chapter in turn is put into, in place of the one before, and counted (see countInShell in reader.js):
 * the reader's script, styles and fonts, and the book's style sheets, are loaded once rather than for
 * every chapter, which is most of the time a chapter opened on its own takes to count. A chapter that
 * can't be counted that way is opened on its own, as the reader opens it, and counted there.
 */
class PageCounter(private val frame: ViewGroup, private val server: BookServer) : ChapterCounter {
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    @Volatile
    private var startJson = "{}"
    private var asked = 0
    private var waiting: Pair<Int, CancellableContinuation<Answer?>>? = null
    /** The counting page loaded: "xhtml" or "html"; null when there's none (or a chapter's open on its own). */
    private var shell: String? = null
    private var shellLoaded: CompletableDeferred<Boolean>? = null
    /** The counting page couldn't be used: every chapter is opened on its own. */
    private var shellBroken = false

    /** What the page said: a count, or which counting page the chapter needs, or that it failed. */
    private class Answer(val pages: Int, val columns: Int, val layout: String, val shell: String?, val failed: String?)

    @SuppressLint("JavascriptInterface")
    private fun view(): WebView = view ?: WebView(frame.context).also { v ->
        v.setUpForReading()
        // Laid out with the reader, but never drawn or touched.
        v.alpha = 0f
        v.isClickable = false
        v.isFocusable = false
        v.isFocusableInTouchMode = false
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        v.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                server.respond(request.url.toString())

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        v.addJavascriptInterface(Bridge(), "KL")
        frame.addView(v, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view = v
    }

    override suspend fun count(path: String, start: String): ChapterCounter.Counted? {
        if (!shellBroken) {
            var mode = shell ?: "xhtml"
            // At most once more, in the other kind of counting page, if the chapter asks for it.
            for (attempt in 0..1) {
                if (!openShell(mode)) {
                    AppLog.w("Reader", "The page for counting pages didn't load; counting each chapter on its own")
                    shellBroken = true
                    break
                }
                val a = ask(path, start) ?: return null
                if (a.shell != null && a.shell != mode) {
                    mode = a.shell
                    continue
                }
                if (a.failed != null || a.shell != null) {
                    AppLog.d("Reader", "Counting $path on its own (${a.failed ?: "needs a ${a.shell} page"})")
                    break
                }
                return ChapterCounter.Counted(a.pages, a.layout, a.columns)
            }
        }
        return countAlone(path, start)
    }

    /** Has the counting page for [mode] loaded, and ready; false if it can't be. */
    private suspend fun openShell(mode: String): Boolean {
        if (shell == mode && shellLoaded?.isCompleted == true) return true
        val loaded = CompletableDeferred<Boolean>()
        shellLoaded = loaded
        shell = mode
        startJson = SHELL_START
        main.post { view().loadUrl(BookServer.APP + "reader/counter.$mode") }
        val ok = withTimeoutOrNull(15_000) { loaded.await() } ?: false
        if (!ok) shell = null
        return ok
    }

    /** Asks the counting page to count the chapter at [path], opened with [start]. */
    private suspend fun ask(path: String, start: String): Answer? = suspendCancellableCoroutine { cont ->
        val id = ++asked
        waiting = id to cont
        cont.invokeOnCancellation { main.post { if (waiting?.first == id) waiting = null } }
        val call = "KLR.count($id, ${JSONObject.quote(server.urlFor(path))}, ${JSONObject.quote(start)})"
        main.post { view?.evaluateJavascript(call, null) ?: run { if (waiting?.first == id) { waiting = null; cont.resume(null) } } }
    }

    /** Counts the chapter at [path] opened on its own, as the reader opens it. */
    private suspend fun countAlone(path: String, start: String): ChapterCounter.Counted? {
        val a = suspendCancellableCoroutine<Answer?> { cont ->
            val id = ++asked
            // The page is told which count it's for, so a page still loading from a count given up on can't answer this one.
            startJson = JSONObject(start).put("measure", id).toString()
            waiting = id to cont
            cont.invokeOnCancellation { main.post { if (waiting?.first == id) waiting = null } }
            // Not in the counting page any more: it's loaded again for the next chapter.
            shell = null
            shellLoaded = null
            // A new address each time, so the same chapter is laid out afresh.
            main.post { view().loadUrl(server.urlFor(path) + "?count=$id") }
        } ?: return null
        return if (a.failed != null) null else ChapterCounter.Counted(a.pages, a.layout, a.columns)
    }

    override fun close() {
        waiting?.second?.cancel()
        waiting = null
        shell = null
        shellLoaded = null
        view?.let { v ->
            frame.removeView(v)
            v.stopLoading()
            v.removeJavascriptInterface("KL")
            v.destroy()
        }
        view = null
    }

    /** The page's side, as reader.js calls it: only the start and the count matter here. */
    private inner class Bridge {
        @JavascriptInterface
        fun start(): String = startJson

        @JavascriptInterface
        fun counterReady() = main.post { shellLoaded?.complete(true) }

        @JavascriptInterface
        fun measured(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            val (id, cont) = waiting ?: return@post
            if (o.optInt("id") != id) return@post
            waiting = null
            val screens = o.optInt("pages", 1).coerceAtLeast(1)
            // Pages of text: with two side by side, as many as twice the screens, less a blank last one.
            val columns = o.optInt("columns", screens).coerceIn(1, screens * 2)
            val answer = Answer(screens, columns, o.optString("layout"), o.optString("shell").ifEmpty { null }, o.optString("failed").ifEmpty { null })
            if (cont.isActive) cont.resume(answer)
        }

        @JavascriptInterface
        fun log(message: String) = AppLog.d("Reader", "Counting pages: $message")

        // Nothing is shown or touched here, so the rest come to nothing.
        @JavascriptInterface fun ready(json: String) {}
        @JavascriptInterface fun position(json: String) {}
        @JavascriptInterface fun edge(direction: Int) {}
        @JavascriptInterface fun turn(direction: Int, y: Double) {}
        @JavascriptInterface fun selecting(on: Boolean) {}
        @JavascriptInterface fun annotation(json: String) {}
        @JavascriptInterface fun tap(zone: String) {}
        @JavascriptInterface fun link(json: String) {}
        @JavascriptInterface fun jumped(json: String) {}
        @JavascriptInterface fun note(json: String) {}
        @JavascriptInterface fun image(src: String) {}
        @JavascriptInterface fun external(url: String) {}
        @JavascriptInterface fun auto(json: String) {}
    }

    companion object {
        private const val SHELL_START = "{\"shell\":true}"

        /**
         * Bumped whenever what the app does to a chapter before the reader lays it out changes (in
         * BookServer, ChapterOpenings, PagePlacement, or the settings ReaderSettings gives the page), so
         * pages counted before aren't used for a book laid out differently now. Changes to the reader's
         * own script and styles are noticed by themselves (see [fingerprint]).
         */
        const val COUNT_RULES = 2

        @Volatile
        private var printed: String? = null

        /**
         * What counts were made with: the reader's script, styles and counting pages, and [COUNT_RULES].
         * Counts kept from before are used only while it's the same, so a new version of KoLib that lays
         * pages out the same way keeps them, and one that doesn't counts again.
         */
        fun fingerprint(assets: AssetManager): String = printed ?: run {
            val crc = CRC32()
            for (name in listOf("reader/reader.js", "reader/reader.css", "reader/fonts.css", "reader/counter.xhtml", "reader/counter.html")) {
                runCatching { assets.open(name).use { crc.update(it.readBytes()) } }
                crc.update(0)
            }
            "r$COUNT_RULES-" + crc.value.toString(16)
        }.also { printed = it }
    }
}

/** The settings every reader WebView has, so a chapter is laid out the same in each. */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.setUpForReading() {
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
}
