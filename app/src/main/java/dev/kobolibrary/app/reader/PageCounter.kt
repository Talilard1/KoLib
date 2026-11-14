package dev.kobolibrary.app.reader

import android.annotation.SuppressLint
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
import dev.kobolibrary.app.log.AppLog
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
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
 */
class PageCounter(private val frame: ViewGroup, private val server: BookServer) : ChapterCounter {
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    @Volatile
    private var startJson = "{}"
    private var asked = 0
    private var waiting: Pair<Int, CancellableContinuation<ChapterCounter.Counted?>>? = null

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

    override suspend fun count(path: String, start: String): ChapterCounter.Counted? = suspendCancellableCoroutine { cont ->
        val id = ++asked
        // The page is told which count it's for, so a page still loading from a count given up on can't answer this one.
        startJson = JSONObject(start).put("measure", id).toString()
        waiting = id to cont
        cont.invokeOnCancellation { main.post { if (waiting?.first == id) waiting = null } }
        // A new address each time, so the same chapter is laid out afresh.
        view().loadUrl(server.urlFor(path) + "?count=$id")
    }

    override fun close() {
        waiting?.second?.cancel()
        waiting = null
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
        fun measured(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            val (id, cont) = waiting ?: return@post
            if (o.optInt("id") != id) return@post
            waiting = null
            val screens = o.optInt("pages", 1).coerceAtLeast(1)
            // Pages of text: with two side by side, as many as twice the screens, less a blank last one.
            val columns = o.optInt("columns", screens).coerceIn(1, screens * 2)
            if (cont.isActive) cont.resume(ChapterCounter.Counted(screens, o.optString("layout"), columns))
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
