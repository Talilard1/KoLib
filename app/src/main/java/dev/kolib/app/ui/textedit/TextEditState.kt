package dev.kolib.app.ui.textedit

import dev.kolib.app.tr
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kolib.app.epub.BookCheck
import dev.kolib.app.epub.BookEdit
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.ChapterOps
import dev.kolib.app.epub.Cleanups
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.OverlayFiles
import dev.kolib.app.epub.TextMap
import dev.kolib.app.epub.Xml
import dev.kolib.app.epub.XmlCheck
import dev.kolib.app.epub.XmlException
import dev.kolib.app.library.Library
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.BookServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import dev.kolib.app.ui.cover.CoverDesign
import dev.kolib.app.ui.cover.CoverGenerator
import dev.kolib.app.ui.cover.CoverGenre
import dev.kolib.app.ui.cover.CoverText
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** What the formatting toolbar shows for where the cursor is. */
data class ToolbarState(
    val bold: Boolean = false, val italic: Boolean = false, val block: String? = null, val image: String? = null,
    /** How the paragraph the cursor is in is lined up: left, center, right or justify. */
    val align: String? = null,
    /** Some text is selected. */
    val selection: Boolean = false,
)

/**
 * A book's text being edited: the chapters, the page or code open in the editor, find & replace,
 * clean-ups, and saving. Everything goes through one [BookEdit], so it can all be undone.
 */
class TextEditState(
    val bookId: String,
    val title: String,
    private val library: Library,
    private val scope: CoroutineScope,
    private val assets: AssetManager,
    initialPath: String?,
    /** Sections copied out of books, to put into this one (and this one's to copy into). */
    val copied: dev.kolib.app.library.CopiedSections? = null,
) {
    val edit = BookEdit(library.epubFile(bookId))
    private val main = Handler(Looper.getMainLooper())
    private val server = BookServer(assets, edit).apply { inject = EDITOR_HEAD }

    /** Bumped with every change, so the screen looks again. */
    var version by mutableIntStateOf(edit.version)
        private set
    var currentPath by mutableStateOf(initialPath?.takeIf { p -> edit.structure.chapters.any { it.path == p } }
        ?: edit.structure.chapters.firstOrNull { it.linear }?.path ?: edit.structure.chapters.firstOrNull()?.path)
        private set
    var showCode by mutableStateOf(false)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    /** Changes that would damage the book, found when saving. */
    var refused by mutableStateOf<List<BookCheck.Problem>?>(null)
    var checkResult by mutableStateOf<List<BookCheck.Problem>?>(null)
    var toolbar by mutableStateOf(ToolbarState())
        private set
    /** A mistake in the code being typed; the chapter keeps its last good version meanwhile. */
    var codeError by mutableStateOf<XmlCheck.Problem?>(null)
        private set
    var hasOriginal by mutableStateOf(library.hasOriginalText(bookId))
        private set
    var pageReady by mutableStateOf(false)
        private set

    val find = FindState(this)

    val structure: BookStructure get() = edit.structure
    val dirty: Boolean get() = version.let { edit.dirty }
    val canUndo: Boolean get() = version.let { edit.canUndo }
    val canRedo: Boolean get() = version.let { edit.canRedo }
    val undoLabel: String? get() = version.let { edit.undoLabel }
    val redoLabel: String? get() = version.let { edit.redoLabel }
    val chapter get() = version.let { structure.chapters.firstOrNull { it.path == currentPath } }

    private var webView: WebView? = null
    @Volatile
    private var startJson = "{}"
    @Volatile
    private var codeStartJson = "{}"
    private var scrollY = 0
    /** The next commit from the page joins this change (adding a picture, then putting it in). */
    @Volatile
    private var joinNext: String? = null
    private var pendingFind: JSONObject? = null
    /** Whether the page open now came as a whole document (it wasn't valid XHTML), so it goes back whole. */
    private var loadedWhole = false

    fun bump() {
        version = edit.version
    }

    private var lastJob: kotlinx.coroutines.Job? = null

    private fun go(block: suspend CoroutineScope.() -> Unit): kotlinx.coroutines.Job = scope.launch(block = block).also { lastJob = it }

    /** Waits for the last piece of work to finish (for tests). */
    internal fun awaitWork() = kotlinx.coroutines.runBlocking { lastJob?.join() }

    // ---- the WebView ----------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun createView(context: Context, dark: Boolean): WebView = WebView(context).apply {
        this@TextEditState.dark = dark
        // Without this the WebView treats the page as having no height (100vh = 0).
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.textZoom = 100
        // The page is laid out to the width of the screen (see EDITOR_HEAD), so it's all in view across to start
        // with, and two fingers zoom in on it and back out again; no zoom buttons are drawn over it.
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setBackgroundColor(if (dark) 0xFF121110.toInt() else 0xFFE7E3DA.toInt())
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                server.respond(request.url.toString())

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        addJavascriptInterface(PageBridge(), "KLE")
        addJavascriptInterface(CodeBridge(), "KLC")
        webView = this
        load(keepScroll = true)
    }

    var dark = false

    fun releaseView() {
        webView?.let {
            it.stopLoading()
            it.removeJavascriptInterface("KLE")
            it.removeJavascriptInterface("KLC")
            it.destroy()
        }
        webView = null
    }

    private fun load(keepScroll: Boolean = false) {
        val path = currentPath ?: return
        val v = webView ?: return
        pageReady = false
        if (!keepScroll) scrollY = 0
        if (showCode) {
            val text = edit.text(path).orEmpty()
            val error = XmlCheck.check(text, htmlEntities = XmlCheck.hasXhtmlDoctype(text))
            codeError = error
            codeStartJson = JSONObject().apply {
                put("text", text)
                put("dark", dark)
                put("scrollY", scrollY)
                error?.let { put("error", errorJs(it)) }
            }.toString()
            v.loadUrl(BookServer.APP + "editor/code.html?v=$version")
        } else {
            loadedWhole = !servedAsXhtml(path)
            startJson = JSONObject().apply {
                put("whole", loadedWhole)
                put("dark", dark)
                put("scrollY", scrollY)
                pendingFind?.let { put("find", it) }
            }.toString()
            pendingFind = null
            v.loadUrl(server.urlFor(path) + "?v=$version")
        }
    }

    private fun servedAsXhtml(path: String): Boolean {
        val text = edit.text(path) ?: return false
        return BookServer.prepare(text, "").first == "application/xhtml+xml"
    }

    private fun js(code: String, result: ((String?) -> Unit)? = null) {
        main.post { webView?.evaluateJavascript(code, result) ?: result?.invoke(null) }
    }

    /** Takes in anything typed but not yet sent from the page or code. */
    suspend fun flush() {
        val path = currentPath ?: return
        if (webView == null || !pageReady) return
        // A page that doesn't answer (it's still loading, say) has nothing new to give.
        val raw = withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine<String?> { cont ->
                js(if (showCode) "KLCD.flush()" else "KLED.flush()") { r -> if (cont.isActive) cont.resume(r) }
            }
        }
        val value = raw?.let { runCatching { JSONArray("[$it]").optString(0) }.getOrNull() }.orEmpty()
        if (value.isEmpty()) return
        if (showCode) codeChanged(path, value) else pageChanged(path, value, whole = loadedWhole)
    }

    // ---- changes from the page and the code -----------------------------------------------------

    private fun pageChanged(path: String, markup: String, whole: Boolean) {
        val text = edit.text(path) ?: return
        val newText = if (whole) {
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n" + markup + "\n"
        } else withBody(text, markup) ?: return
        val key = joinNext ?: "type:$path"
        joinNext = null
        if (edit.apply(tr("Typing in “{0}”", chapterTitle(path)), mapOf(path to newText.toByteArray(Charsets.UTF_8)), coalesceKey = key)) bump()
    }

    /** A mistake in the code, for the code view: where it is, and what to show. */
    private fun errorJs(e: XmlCheck.Problem): JSONObject = JSONObject()
        .put("line", e.line)
        .put("message", e.message)
        .put("text", tr("Line {0}: {1}. Fix this to save your change.", e.line, e.message))

    private fun codeChanged(path: String, typed: String) {
        val old = edit.text(path) ?: return
        val text = if (old.endsWith("\n") && !typed.endsWith("\n")) typed + "\n" else typed
        val error = XmlCheck.check(text, htmlEntities = XmlCheck.hasXhtmlDoctype(text))
        codeError = error
        js("KLCD.error(${JSONObject.quote(error?.let { errorJs(it).toString() } ?: "null")})")
        if (error != null) return
        if (edit.apply(tr("Edited the code of “{0}”", chapterTitle(path)), mapOf(path to text.toByteArray(Charsets.UTF_8)), coalesceKey = "code:$path")) bump()
    }

    private fun chapterTitle(path: String) = structure.chapters.firstOrNull { it.path == path }?.title ?: path.substringAfterLast('/')

    private inner class PageBridge {
        @JavascriptInterface
        fun start(): String = startJson

        @JavascriptInterface
        fun ready(json: String) = main.post { pageReady = true }

        @JavascriptInterface
        fun changed(markup: String) = main.post {
            val path = currentPath ?: return@post
            if (!showCode) pageChanged(path, markup, whole = loadedWhole)
        }

        @JavascriptInterface
        fun state(json: String) = main.post {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return@post
            toolbar = ToolbarState(o.optBoolean("bold"), o.optBoolean("italic"), o.optString("block").takeIf { it.isNotEmpty() && it != "null" },
                o.optString("image").takeIf { it.isNotEmpty() && it != "null" },
                o.optString("align").takeIf { it.isNotEmpty() && it != "null" }, o.optBoolean("selection"))
            scrollY = o.optInt("scrollY", scrollY)
        }

        @JavascriptInterface
        fun undo() {
            main.post { this@TextEditState.undo() }
        }

        @JavascriptInterface
        fun redo() {
            main.post { this@TextEditState.redo() }
        }

        @JavascriptInterface
        fun log(m: String) = AppLog.d("Editor", m)
    }

    private inner class CodeBridge {
        @JavascriptInterface
        fun start(): String = codeStartJson

        @JavascriptInterface
        fun ready() = main.post { pageReady = true }

        @JavascriptInterface
        fun changed(text: String) = main.post {
            val path = currentPath ?: return@post
            if (showCode) codeChanged(path, text)
        }
    }

    // ---- moving around and undoing --------------------------------------------------------------

    fun open(path: String, then: JSONObject? = null) {
        go {
            flush()
            if (path == currentPath && then != null && !showCode && pageReady) {
                js("KLED.find(${JSONObject.quote(then.toString())})")
                return@go
            }
            currentPath = path
            pendingFind = then
            load()
        }
    }

    /** Switches between the page and its code. Code with a mistake in it isn't taken back to the page. */
    fun setCode(on: Boolean, discardBadCode: Boolean = false) {
        if (on == showCode) return
        go {
            if (showCode && codeError != null && !discardBadCode) {
                flush()
                if (codeError != null) {
                    message = tr("Fix the mistake in the code first, or undo it. ({0})", codeError)
                    return@go
                }
            } else flush()
            codeError = null
            showCode = on
            load()
        }
    }

    fun undo() {
        go {
            flush()
            val touched = edit.undo()
            afterHistory(touched)
        }
    }

    fun redo() {
        go {
            flush()
            val touched = edit.redo()
            afterHistory(touched)
        }
    }

    private fun afterHistory(touched: Set<String>) {
        bump()
        val current = currentPath
        if (current == null || !edit.has(current)) {
            currentPath = structure.chapters.firstOrNull()?.path
            load()
        } else if (current in touched) {
            load(keepScroll = true)
        }
        find.bookChanged()
    }

    // ---- chapter operations ---------------------------------------------------------------------

    /** Runs a change to the book's chapters as one undoable step, and opens [ChapterOps.Result.focus]. */
    fun change(work: suspend () -> ChapterOps.Result?) {
        go {
            flush()
            busy = tr("Working…")
            try {
                val result = withContext(Dispatchers.Default) { work() } ?: return@go
                if (edit.apply(result.label, result.changes)) {
                    bump()
                    val focus = result.focus?.takeIf { edit.has(it) }
                    val current = currentPath
                    when {
                        focus != null && focus != current -> { currentPath = focus; load() }
                        current == null || !edit.has(current) -> { currentPath = structure.chapters.firstOrNull()?.path; load() }
                        current in result.changes.keys -> load(keepScroll = true)
                    }
                    find.bookChanged()
                }
            } catch (e: ChapterOps.OpError) {
                message = e.message
            } catch (e: Exception) {
                AppLog.e("Editor", "A change to the book failed", e)
                message = tr("That didn't work: {0}", e.message ?: e.javaClass.simpleName)
            } finally {
                busy = null
            }
        }
    }

    fun rename(path: String, title: String, alsoHeading: Boolean) = change { ChapterOps.rename(edit, structure, path, title, alsoHeading) }
    fun reorder(order: List<String>, label: String = tr("Moved a chapter")) = change { ChapterOps.reorder(edit, structure, order, label) }
    fun mergeWithPrevious(path: String) = change { ChapterOps.mergeWithPrevious(edit, structure, path) }
    fun delete(path: String) = change { ChapterOps.delete(edit, structure, path) }

    /** Deletes the chapters at [paths] together, as one step to undo. */
    fun deleteAll(paths: Collection<String>) = change { ChapterOps.delete(edit, structure, paths) }
    fun setHidden(path: String, hidden: Boolean) = change { ChapterOps.setHidden(edit, structure, path, hidden) }
    fun setStart(path: String) = change { ChapterOps.setStart(edit, structure, path) }
    fun contentsFromHeadings() = change { ChapterOps.contentsFromHeadings(edit, structure) }
    fun split(path: String, offset: Int, title: String?) = change { ChapterOps.split(edit, structure, path, offset, title) }
    fun addChapter(after: String?, title: String) = change { ChapterOps.addChapter(edit, structure, after, title) }

    /** A copy of the chapter at [path], put straight after it and opened. */
    fun duplicate(path: String) = change { ChapterOps.duplicate(edit, structure, path) }

    val startPath: String? get() = version.let { runCatching { ChapterOps.startPath(structure) }.getOrNull() }

    /** The book's cover page, if it names one. */
    val coverPath: String? get() = version.let { runCatching { ChapterOps.coverPagePath(structure) }.getOrNull() }

    /** Makes the chapter at [path] the book's cover page, at the front. */
    fun setCoverPage(path: String) = change { ChapterOps.setCoverPage(edit, structure, path) }

    /**
     * Adds a cover page at the front of the book showing its cover. A book without a cover picture gets
     * one made for it here, in a design that suits its genre, which becomes its cover too.
     */
    fun makeCoverPage(context: Context) = change {
        if (ChapterOps.coverImagePath(edit, structure) != null) return@change ChapterOps.addCoverPage(edit, structure, null)
        ChapterOps.checkNoCoverPage(edit, structure)
        val meta = structure.opf.readMetadata()
        val design = CoverDesign.random(genre = CoverGenre.guess(meta.tags, meta.series, meta.title, meta.description))
        val cover = CoverGenerator(context.applicationContext).render(CoverText(meta.title, meta.authors, meta.series, meta.seriesIndex), design)
        val out = ByteArrayOutputStream()
        cover.compress(Bitmap.CompressFormat.JPEG, 90, out)
        ChapterOps.addCoverPage(edit, structure, ChapterOps.NewPicture(out.toByteArray(), "image/jpeg", cover.width, cover.height))
    }

    // ---- copied sections ------------------------------------------------------------------------

    /** The panel of copied sections is open. */
    var copiedOpen by mutableStateOf(false)

    /** Copies the chapter at [path] (with its pictures and styles) into the copied sections, to put into any book. */
    fun copySection(path: String) {
        val store = copied ?: return
        go {
            flush()
            try {
                val page = withContext(Dispatchers.Default) { ChapterOps.copyPage(edit, structure, path) }
                withContext(Dispatchers.IO) { store.add(page, title) }
                message = tr("Copied “{0}”. It's in the Copied panel, for this book or any other.", page.title)
            } catch (e: ChapterOps.OpError) {
                message = e.message
            } catch (e: Exception) {
                AppLog.e("Editor", "Copying a section failed", e)
                message = tr("That couldn't be copied: {0}", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Puts the copied section [id] into the book after the chapter open now. A contents page can be made to list this
     * book's chapters instead of its own ([relist]).
     */
    fun pasteSection(id: String, relist: Boolean) {
        val store = copied ?: return
        val after = currentPath
        change {
            val page = withContext(Dispatchers.IO) { store.load(id) } ?: throw ChapterOps.OpError(tr("That copied section couldn't be read."))
            ChapterOps.paste(edit, structure, after, page, relist)
        }
    }

    fun deleteCopied(id: String) {
        val store = copied ?: return
        go { withContext(Dispatchers.IO) { store.delete(id) } }
    }

    fun splitPoints(path: String): List<ChapterOps.SplitPoint> =
        edit.text(path)?.let { runCatching { ChapterOps.splitPoints(it) }.getOrNull() }.orEmpty()

    /** Splits the chapter open in the page editor at the paragraph the cursor is in. */
    fun splitAtCursor() {
        val path = currentPath ?: return
        if (showCode) {
            message = tr("Switch back from the code to split at the cursor.")
            return
        }
        js("KLED.splitHere()") { raw ->
            val marked = raw?.let { runCatching { JSONArray("[$it]").optString(0) }.getOrNull() }.orEmpty()
            if (marked.isEmpty()) {
                message = tr("Put the cursor in the paragraph the new chapter should start with.")
                return@js
            }
            change {
                val text = edit.text(path) ?: return@change null
                val whole = loadedWhole
                val withMarker = if (whole) "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n$marked\n" else {
                    withBody(text, marked) ?: throw ChapterOps.OpError(tr("This chapter has no text to split."))
                }
                val doc = Xml.parse(withMarker)
                val target = doc.root.descendants().firstOrNull { it.attributes.containsKey("data-kl-split") }
                    ?: throw ChapterOps.OpError(tr("The place to split couldn't be found."))
                val attrs = LinkedHashMap(target.attributes).apply { remove("data-kl-split") }
                val clean = withMarker.substring(0, target.start) + Xml.startTag(target.name, attrs, target.selfClosing) + withMarker.substring(target.startTagEnd)
                val overlay = OverlayFiles(edit, mapOf(path to clean.toByteArray(Charsets.UTF_8)))
                val r = ChapterOps.split(overlay, BookStructure(overlay), path, target.start, null)
                r.copy(changes = mapOf(path to clean.toByteArray(Charsets.UTF_8)) + r.changes)
            }
        }
    }

    // ---- formatting ----------------------------------------------------------------------------

    fun command(name: String, arg: String? = null) {
        if (showCode) return
        js("KLED.cmd(${JSONObject.quote(name)}, ${arg?.let { JSONObject.quote(it) } ?: "null"})")
    }

    /** Selects in the page without dragging: "all", "toStart", "toEnd" (from the cursor) or "paragraph". */
    fun select(what: String) {
        if (showCode) return
        js("KLED.select(${JSONObject.quote(what)})") { webView?.requestFocus() }
    }

    /** Copies what's selected in the page (as text, and with its italics and bold for pasting back in); with [cut], takes it out too. */
    fun copySelection(context: Context, cut: Boolean = false) {
        if (showCode) return
        js("KLED.copy($cut)") { raw ->
            val o = raw?.let { runCatching { JSONObject(JSONArray("[$it]").optString(0)) }.getOrNull() }
            val text = o?.optString("text").orEmpty()
            if (text.isEmpty()) {
                message = tr("Select some text first.")
                return@js
            }
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            runCatching { clipboard?.setPrimaryClip(ClipData.newHtmlText("KoLib", text, o?.optString("html").orEmpty())) }
                .onFailure { message = tr("That couldn't be copied: {0}", it.message ?: it.javaClass.simpleName) }
            // Android 13 and later say so themselves.
            if (Build.VERSION.SDK_INT < 33) message = if (cut) tr("Cut.") else tr("Copied.")
        }
    }

    /** Puts what's on the clipboard in where the cursor is, as clean paragraphs. */
    fun pasteClipboard(context: Context) {
        if (showCode) return
        val item = runCatching { context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) }.getOrNull()
        val text = item?.coerceToText(context)?.toString().orEmpty()
        val html = item?.htmlText.orEmpty()
        if (text.isBlank() && html.isBlank()) {
            message = tr("Nothing to paste.")
            return
        }
        js("KLED.paste(${JSONObject.quote(text)}, ${JSONObject.quote(html)})") { webView?.requestFocus() }
    }

    /** Adds a picture to the book and puts it in the page where the cursor is. */
    fun insertPicture(bytes: ByteArray) {
        val path = currentPath ?: return
        go {
            flush()
            val prepared = withContext(Dispatchers.Default) { preparePicture(bytes, null) }
            if (prepared == null) {
                message = tr("That picture couldn't be read.")
                return@go
            }
            val (data, type) = prepared
            val ext = if (type == "image/png") "png" else "jpg"
            val picPath = ChapterOps.newPicturePath(edit, structure, ext)
            val r = ChapterOps.addFile(edit, structure, picPath, data, type)
            val key = "pic:$path:${System.nanoTime()}"
            if (edit.apply(r.label, r.changes, coalesceKey = key)) {
                bump()
                joinNext = key
                command("image", EpubFile.relativeHref(path, picPath))
            }
        }
    }

    /** Replaces the picture picked in the page (everywhere it's used), keeping its file and type. */
    fun replacePicture(bytes: ByteArray) {
        val path = currentPath ?: return
        val src = toolbar.image ?: return
        go {
            flush()
            val picPath = EpubFile.resolvePath(path.substringBeforeLast('/', ""), src)
            val oldType = BookServer.mediaType(picPath)
            val prepared = withContext(Dispatchers.Default) { preparePicture(bytes, oldType) }
            if (prepared == null || !edit.has(picPath)) {
                message = tr("That picture couldn't be used.")
                return@go
            }
            if (edit.apply(tr("Replaced a picture"), mapOf(picPath to prepared.first))) {
                bump()
                command("reloadImage", src)
            }
        }
    }

    /** A picture as JPEG or PNG, at most 2400 pixels across, or as [keepType] when replacing one. */
    private fun preparePicture(bytes: ByteArray, keepType: String?): Pair<ByteArray, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val isJpeg = bounds.outMimeType == "image/jpeg"
        val isPng = bounds.outMimeType == "image/png"
        val wantType = keepType?.takeIf { it == "image/jpeg" || it == "image/png" } ?: if (isPng) "image/png" else "image/jpeg"
        val small = maxOf(bounds.outWidth, bounds.outHeight) <= 2400
        if (small && ((wantType == "image/jpeg" && isJpeg) || (wantType == "image/png" && isPng))) return bytes to wantType
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(if (wantType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray() to wantType
    }

    // ---- clean-ups --------------------------------------------------------------------------------

    suspend fun cleanupCounts(only: Set<String>?): Map<Cleanups.Kind, Int> {
        flush()
        return withContext(Dispatchers.Default) {
            Cleanups.Kind.entries.associateWith { k -> runCatching { Cleanups.run(k, edit, structure, only).count }.getOrDefault(0) }
        }
    }

    fun cleanup(kind: Cleanups.Kind, only: Set<String>?) = change {
        val r = Cleanups.run(kind, edit, structure, only)
        if (r.count == 0) null else ChapterOps.Result("${kind.label} (${kind.describe(r.count)})", r.changes, currentPath)
    }

    // ---- saving --------------------------------------------------------------------------------

    fun save(onSaved: (() -> Unit)? = null) {
        go {
            flush()
            if (codeError != null) {
                message = tr("The code has a mistake, so your change isn't in the book yet. Fix it or undo it first.")
                return@go
            }
            busy = tr("Saving and checking the book…")
            try {
                val result = withContext(Dispatchers.IO) { edit.save(library.originalTextFile(bookId)) }
                when (result) {
                    BookEdit.SaveResult.NothingToSave -> message = tr("Nothing to save.")
                    is BookEdit.SaveResult.Saved -> {
                        bump()
                        hasOriginal = true
                        library.afterTextEdit(bookId)
                        message = tr("Saved. No new problems found.") + when (result.problemsBefore) {
                            0 -> ""
                            1 -> tr(" (1 problem was already there before your edit.)")
                            else -> tr(" ({0} problems were already there before your edit.)", result.problemsBefore)
                        }
                        onSaved?.invoke()
                    }
                    is BookEdit.SaveResult.Refused -> refused = result.problems
                }
            } catch (e: Exception) {
                AppLog.e("Editor", "Saving the text failed", e)
                message = tr("Couldn't save: {0}. The book wasn't changed.", e.message ?: e.javaClass.simpleName)
            } finally {
                busy = null
            }
        }
    }

    fun discardChanges() {
        edit.discardUnsaved()
        bump()
        if (currentPath?.let { edit.has(it) } != true) currentPath = structure.chapters.firstOrNull()?.path
        load(keepScroll = true)
        find.bookChanged()
    }

    fun checkBook() {
        go {
            flush()
            busy = tr("Checking the book…")
            checkResult = withContext(Dispatchers.Default) { BookCheck.run(edit) }
            busy = null
        }
    }

    fun revert(onDone: () -> Unit) {
        go {
            busy = tr("Restoring the original text…")
            try {
                library.revertText(bookId)
                withContext(Dispatchers.IO) { edit.reload() }
                hasOriginal = false
                bump()
                if (currentPath?.let { edit.has(it) } != true) currentPath = structure.chapters.firstOrNull()?.path
                load()
                find.bookChanged()
                message = tr("Original text restored. The details and cover weren't changed.")
                onDone()
            } catch (e: Exception) {
                AppLog.e("Editor", "Reverting failed", e)
                message = tr("Couldn't restore the original text: {0}", e.message)
            } finally {
                busy = null
            }
        }
    }

    fun close() {
        releaseView()
        edit.close()
    }

    /** Shows a find match in the page (opening its chapter, or going back from the code, if needed). */
    fun showMatch(current: JSONObject, all: JSONArray, reload: Boolean = false) {
        val o = JSONObject().put("current", current).put("all", all).put("smooth", true)
        val path = current.optString("path")
        if (!reload && !showCode && path == currentPath && pageReady) {
            js("KLED.find(${JSONObject.quote(o.toString())})")
            return
        }
        go {
            flush()
            if (showCode && codeError != null) {
                message = tr("Fix the mistake in the code first, or undo it. ({0})", codeError)
                return@go
            }
            showCode = false
            codeError = null
            val same = path == currentPath
            currentPath = path
            pendingFind = o
            load(keepScroll = same)
        }
    }

    /** Shows the page again after the book changed under it, with [find] highlighted. */
    fun refreshPage(find: JSONObject?) {
        if (showCode) return
        pendingFind = find
        load(keepScroll = true)
    }

    val scopeForFind: CoroutineScope get() = scope

    fun clearMatches() = js("if (window.KLED) KLED.clearFind()")

    companion object {
        private val nsDecl = Regex("""\s+xmlns(?::([A-Za-z_][\w.-]*))?\s*=\s*"([^"]*)"""")

        /** Namespace declarations in effect inside the body (on html and body). */
        private fun declaredNamespaces(body: dev.kolib.app.epub.XElement): Map<String, String> {
            val out = HashMap<String, String>()
            var e: dev.kolib.app.epub.XElement? = body
            while (e != null) {
                for ((k, v) in e.attributes) {
                    if (k == "xmlns") out.putIfAbsent("", v)
                    else if (k.startsWith("xmlns:")) out.putIfAbsent(k.removePrefix("xmlns:"), v)
                }
                e = e.parent
            }
            return out
        }

        /**
         * [text] with its body's content replaced by [markup] from the browser. The browser repeats namespace
         * declarations on each element it writes out; ones the chapter already has are dropped.
         */
        fun withBody(text: String, markup: String): String? {
            val doc = try { Xml.parse(text) } catch (e: XmlException) { return null }
            val body = TextMap.body(doc)
            if (body === doc.root || body.selfClosing) return null
            val declared = declaredNamespaces(body)
            val clean = nsDecl.replace(markup) { m -> if (declared[m.groupValues[1]] == m.groupValues[2]) "" else m.value }
            return text.substring(0, body.startTagEnd) + clean + text.substring(body.endTagStart)
        }

        /**
         * What a chapter is given to be edited: the editor's look and script, and how wide to lay the page out. Without
         * being told, the WebView lays a page out 980 points wide whatever the screen: far wider than a phone, or than
         * the editor's side of a tablet held upright, so only part of the page showed. Laid out to the screen's own
         * width, the whole page is in view across, and can be zoomed in on from there.
         */
        val EDITOR_HEAD =
            "<meta data-kl=\"\" name=\"viewport\" content=\"width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5\"/>" +
                "<link data-kl=\"\" rel=\"stylesheet\" type=\"text/css\" href=\"${BookServer.APP}editor/editor.css\"/>" +
                "<script data-kl=\"\" type=\"text/javascript\" src=\"${BookServer.APP}editor/editor.js\"></script>"
    }
}
