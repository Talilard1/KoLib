package dev.kolib.app.ui.reader

import dev.kolib.app.tr
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.LruCache
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.convert.AndroidPdfPages
import dev.kolib.app.convert.PdfDocument
import dev.kolib.app.convert.PdfConverter
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.PdfPlace
import dev.kolib.app.reader.ReaderKeys
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.reader.ReaderSpread
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.reader.ReadingHereIndex
import dev.kolib.app.reader.TapZones
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The pages of an open PDF, drawn for the screen. Android draws one page of a PDF at a time, so all the
 * drawing is done on one thread; pages drawn lately are kept, to turn back to.
 */
internal class PdfPages(val doc: PdfDocument) {
    val count = doc.count

    @OptIn(ExperimentalCoroutinesApi::class)
    val thread: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** The size in points of each page that's been shown. */
    val sizes = mutableStateMapOf<Int, IntSize>()

    /** Every page's size, found once (only on the drawing thread). */
    private val all = arrayOfNulls<IntSize>(count)

    private fun sizeHere(i: Int): IntSize = all[i] ?: (runCatching { doc.size(i) }.getOrNull()?.let { IntSize(it.first.coerceAtLeast(1), it.second.coerceAtLeast(1)) } ?: IntSize(612, 792)).also { all[i] = it }

    /** Which pages are wider than they're tall: a map or a spread across two pages. One page at a time, between drawing the ones shown. */
    suspend fun widePages(): Set<Int> {
        val out = HashSet<Int>()
        for (i in 0 until count) {
            val s = withContext(thread) { sizeHere(i) }
            if (s.width > s.height * 1.15f) out += i
        }
        return out
    }

    private val kept = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt().coerceIn(24 shl 20, 160 shl 20)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun size(i: Int): IntSize {
        sizes[i]?.let { return it }
        return withContext(thread) { sizeHere(i) }.also { sizes[i] = it }
    }

    /** Page [i] drawn [w] × [h] pixels. */
    suspend fun page(i: Int, w: Int, h: Int): Bitmap? = withContext(thread) {
        val key = "$i:$w:$h"
        kept.get(key) ?: runCatching { doc.bitmap(i, w, h) }
            .onFailure { if (it !is IllegalStateException) AppLog.w("PDF", "Page ${i + 1} couldn't be drawn", it) }
            .getOrNull()?.also { kept.put(key, it) }
    }

    /**
     * The parts of [pages] on the screen while it's zoomed, drawn sharp: a [w] × [h] picture of the screen,
     * with each page where [placed] puts it (its rectangle on the screen).
     */
    suspend fun sharp(pages: IntArray, placed: List<Rect>, w: Int, h: Int): Bitmap? = withContext(thread) {
        runCatching {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            for ((k, p) in pages.withIndex()) {
                val r = placed[k]
                val size = sizes[p] ?: continue
                val clip = android.graphics.Rect(
                    max(0, floor(r.left).toInt()), max(0, floor(r.top).toInt()),
                    min(w, ceil(r.right).toInt()), min(h, ceil(r.bottom).toInt()),
                )
                if (clip.width() <= 0 || clip.height() <= 0) continue
                val m = Matrix().apply {
                    setScale(r.width / size.width, r.height / size.height)
                    postTranslate(r.left, r.top)
                }
                doc.drawInto(p, out, clip, m)
            }
            out
        }.getOrNull()
    }

    fun close() {
        // After any page being drawn.
        Thread { doc.close() }.start()
        kept.evictAll()
    }
}

/** Which pages go on each screen: one, or two side by side, with the first page alone, as a book's cover is. */
internal fun pdfScreens(count: Int, two: Boolean, wide: (Int) -> Boolean = { false }): List<IntArray> {
    if (!two) return List(count) { intArrayOf(it) }
    val out = ArrayList<IntArray>()
    var i = 0
    if (count > 0) {
        out += intArrayOf(0)
        i = 1
    }
    while (i < count) {
        // A page wider than it's tall (a map, a spread) has the screen to itself.
        if (wide(i) || i + 1 >= count || wide(i + 1)) {
            out += intArrayOf(i)
            i++
        } else {
            out += intArrayOf(i, i + 1)
            i += 2
        }
    }
    return out
}

/** Where [pages] go on a [w] × [h] screen: as big as fits, two side by side meeting in the middle. */
private fun placePages(pages: IntArray, sizeOf: (Int) -> IntSize?, w: Float, h: Float): List<Rect> {
    val first = sizeOf(pages[0]) ?: IntSize(612, 792)
    if (pages.size == 1) {
        val k = min(w / first.width, h / first.height)
        val pw = first.width * k
        val ph = first.height * k
        return listOf(Rect((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2))
    }
    val second = sizeOf(pages[1]) ?: first
    val ra = first.width.toFloat() / first.height
    val rb = second.width.toFloat() / second.height
    val ph = min(h, w / (ra + rb))
    val wa = ra * ph
    val wb = rb * ph
    val left = (w - wa - wb) / 2
    val top = (h - ph) / 2
    return listOf(Rect(left, top, left + wa, top + ph), Rect(left + wa, top, left + wa + wb, top + ph))
}

/** How far a screen is zoomed in, and where to: pinched or double-tapped. */
private class Zoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    val zoomed get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Zooms by [by] around [focus] and moves by [pan], keeping [content] (the pages) on the screen. */
    fun change(by: Float, pan: Offset, focus: Offset, screen: Size, content: Rect) {
        val before = scale
        val after = (before * by).coerceIn(1f, MAX)
        val z = after / before
        val c = Offset(screen.width / 2, screen.height / 2)
        scale = after
        offset = held((focus - c) * (1 - z) + offset * z + pan, screen, content)
    }

    /** [o] moved as little as needed for the pages to cover the screen, or be in its middle where they're smaller. */
    fun held(o: Offset, screen: Size, content: Rect, s: Float = scale): Offset {
        fun axis(o: Float, lo: Float, hi: Float, c: Float, view: Float): Float {
            val a = c + (lo - c) * s
            val b = c + (hi - c) * s
            return if (b - a <= view) (view - (b - a)) / 2 - a else o.coerceIn(view - b, -a)
        }
        return Offset(
            axis(o.x, content.left, content.right, screen.width / 2, screen.width),
            axis(o.y, content.top, content.bottom, screen.height / 2, screen.height),
        )
    }

    /** Where a point on the unzoomed screen is now. */
    fun onScreen(r: Rect, screen: Size): Rect {
        val cx = screen.width / 2
        val cy = screen.height / 2
        return Rect(
            cx + (r.left - cx) * scale + offset.x, cy + (r.top - cy) * scale + offset.y,
            cx + (r.right - cx) * scale + offset.x, cy + (r.bottom - cy) * scale + offset.y,
        )
    }

    companion object {
        const val MAX = 6f
        const val DOUBLE_TAP = 2.5f
    }
}

/**
 * Reads a book added as a PDF, page by page as it was printed: turned like the reader's pages, pinched
 * or double-tapped to zoom, two side by side in landscape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBookReader(
    book: LibraryBook,
    library: Library,
    settings: ReaderSettingsStore,
    onClose: () -> Unit,
    /** Switches to reading the EPUB made from it; null for scanned pages, which have no text. */
    onReadAsText: (() -> Unit)?,
    readingHere: ReadingHereIndex? = null,
    /** Opens the app's own settings: its look, language and books folder. */
    onAppSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val s by settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dir = remember(book.id) { library.bookDir(book.id) }
    var pages by remember(book.id) { mutableStateOf<PdfPages?>(null) }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
    DisposableEffect(book.id) {
        var opened: PdfPages? = null
        val job = scope.launch {
            try {
                val file: File = library.sourceFile(book.id) ?: throw java.io.IOException(tr("The PDF isn't in the library any more."))
                val doc = withContext(Dispatchers.IO) { AndroidPdfPages(file) }
                if (doc.count == 0) {
                    doc.close()
                    error = tr("This PDF has no pages.")
                    return@launch
                }
                opened = PdfPages(doc)
                pages = opened
            } catch (e: Exception) {
                AppLog.e("PDF", "The PDF couldn't be opened", e)
                error = tr("This PDF couldn't be opened. It may be damaged, or locked with a password.")
            }
        }
        onDispose {
            job.cancel()
            pages = null
            opened?.close()
        }
    }
    // Full screen while reading, as in the reader.
    var controls by remember { mutableStateOf(false) }
    DisposableEffect(controls) {
        val window = (context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.navigationBars())
        if (controls) insets?.show(WindowInsetsCompat.Type.statusBars()) else insets?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    DisposableEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(s.rotation) {
        val activity = context as? Activity
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = when (s.rotation) {
            ReaderRotation.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderRotation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ReaderRotation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    DisposableEffect(s.brightness) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = if (s.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else s.brightness }
        }
        onDispose {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }
    val background = Color(s.theme.bg)
    val p = pages
    when {
        p != null -> PdfPagesView(book, p, dir, s, background, controls, { controls = it }, onClose, onReadAsText, readingHere, onAppSettings)
        error != null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error!!, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row {
                    if (onReadAsText != null) TextButton(onClick = onReadAsText) { Text(tr("Read as text")) }
                    TextButton(onClick = onClose) { Text(tr("Back")) }
                }
            }
        }
        else -> Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPagesView(
    book: LibraryBook,
    pages: PdfPages,
    dir: File,
    s: ReaderSettings,
    background: Color,
    controls: Boolean,
    setControls: (Boolean) -> Unit,
    onClose: () -> Unit,
    onReadAsText: (() -> Unit)?,
    readingHere: ReadingHereIndex?,
    /** Opens the app's own settings: its look, language and books folder. */
    onAppSettings: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val count = pages.count
    // Where it was left.
    val saved = remember { ReaderStore.pdfPlace(dir) }
    var page by remember { mutableIntStateOf(saved?.page?.coerceIn(0, count - 1) ?: 0) }
    CountReading(rememberReadingSession(book), page)
    var savedPage by remember { mutableIntStateOf(if (saved != null && saved.pages == count) saved.page else -1) }
    var contents by remember { mutableStateOf<List<Triple<String, Int, Int>>?>(null) }
    var showContents by remember { mutableStateOf(false) }
    // Which pages are wider than they're tall, once their sizes are known: they have a screen to themselves.
    var wide by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(pages) {
        pages.size(page)
        wide = pages.widePages()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val density = LocalDensity.current
        val landscape = constraints.maxWidth > constraints.maxHeight * 1.1f && with(density) { constraints.maxWidth.toDp() } >= 560.dp
        var twoChosen by remember { mutableStateOf<Boolean?>(null) }
        val two = twoChosen ?: when (s.spread) {
            ReaderSpread.AUTO -> landscape
            ReaderSpread.ONE -> false
            ReaderSpread.TWO -> true
        }
        val screens = remember(count, two, wide) { pdfScreens(count, two) { it in wide } }
        fun screenOf(pg: Int) = screens.indexOfFirst { pg in it }.coerceAtLeast(0)
        // A new set of screens (turned on its side, two pages or one) starts at the page being read.
        key(screens) {
            val pager = rememberPagerState(initialPage = screenOf(page)) { screens.size }
            var zoomed by remember { mutableStateOf(false) }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { sc ->
                    val first = screens.getOrNull(sc)?.first() ?: return@collect
                    page = first
                    if (first == savedPage) return@collect
                    savedPage = first
                    val place = PdfPlace(first, count)
                    withContext(Dispatchers.IO) { ReaderStore.savePdfPlace(dir, place) }
                    readingHere?.set(book.id, place.readHere)
                }
            }
            fun turn(by: Int) {
                val to = (pager.currentPage + by).coerceIn(0, screens.size - 1)
                if (to == pager.currentPage) return
                scope.launch { if (s.pageTurn == dev.kolib.app.reader.PageTurn.INSTANT) pager.scrollToPage(to) else pager.animateScrollToPage(to) }
            }
            DisposableEffect(s.volumeKeys, pager) {
                ReaderKeys.handler = if (!s.volumeKeys) null else { e ->
                    when (e.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_DOWN -> { if (e.action == KeyEvent.ACTION_DOWN) turn(1); true }
                        KeyEvent.KEYCODE_VOLUME_UP -> { if (e.action == KeyEvent.ACTION_DOWN) turn(-1); true }
                        else -> false
                    }
                }
                onDispose { ReaderKeys.handler = null }
            }
            BackHandler {
                when {
                    showContents -> showContents = false
                    controls -> setControls(false)
                    else -> onClose()
                }
            }
            HorizontalPager(
                pager,
                Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                key = { screens[it].first() },
            ) { sc ->
                PdfScreen(
                    pages = pages,
                    shown = screens[sc],
                    current = pager.settledPage == sc,
                    taps = s.taps,
                    tapWidth = s.tapWidth.fraction,
                    onZoomed = { if (pager.settledPage == sc) zoomed = it },
                    onTurn = ::turn,
                    onMiddle = { setControls(!controls) },
                )
            }
            AnimatedVisibility(controls, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }, modifier = Modifier.align(Alignment.TopCenter)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book")) }
                        IconButton(onClick = {
                            showContents = true
                            if (contents == null) scope.launch {
                                contents = withContext(Dispatchers.IO) { dirSource(dir)?.let { PdfConverter.contents(it) }.orEmpty() }
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.List, tr("Contents")) }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val shown = screens.getOrNull(pager.currentPage)
                            Text(
                                when {
                                    shown == null -> ""
                                    shown.size == 2 -> tr("Pages {0} and {1} of {2}", shown[0] + 1, shown[1] + 1, count)
                                    else -> tr("Page {0} of {1}", shown[0] + 1, count)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { twoChosen = !two }) {
                            if (two) Icon(Icons.Outlined.Description, tr("Show one page at a time"))
                            else Icon(Icons.Outlined.AutoStories, tr("Show two pages side by side"))
                        }
                        if (onReadAsText != null) IconButton(onClick = onReadAsText) { Icon(Icons.Outlined.FormatSize, tr("Read as text")) }
                        if (onAppSettings != null) IconButton(onClick = onAppSettings) { Icon(Icons.Outlined.Settings, tr("Settings")) }
                    }
                }
            }
            AnimatedVisibility(controls, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { turn(-1) }) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, tr("Previous page")) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (count > 1) {
                                Slider(
                                    value = dragging ?: (screens.getOrNull(pager.currentPage)?.first() ?: 0).toFloat(),
                                    onValueChange = { dragging = it },
                                    onValueChangeFinished = {
                                        dragging?.let { v -> scope.launch { pager.scrollToPage(screenOf(v.toInt())) } }
                                        dragging = null
                                    },
                                    valueRange = 0f..(count - 1).toFloat(),
                                )
                            }
                            dragging?.let { v -> Text(tr("Page {0}", v.toInt() + 1), style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { turn(1) }) { Icon(Icons.AutoMirrored.Filled.NavigateNext, tr("Next page")) }
                    }
                }
            }
            if (showContents) {
                val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { showContents = false }, sheetState = sheet) {
                    val list = contents
                    Text(tr("Contents"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    when {
                        list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        list.isEmpty() -> Text(
                            tr("This PDF has no contents list. Use the bar at the bottom to go to a page."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                        else -> {
                            val here = list.indexOfLast { it.second <= page }.coerceAtLeast(0)
                            val state = rememberLazyListState((here - 3).coerceAtLeast(0))
                            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                itemsIndexed(list) { i, (title, pg, depth) ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable {
                                                showContents = false
                                                scope.launch { pager.scrollToPage(screenOf(pg.coerceIn(0, count - 1))) }
                                            }
                                            .background(if (i == here) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .padding(start = (24 + 16 * depth.coerceAtMost(4)).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(12.dp))
                                        Text("${pg + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (depth == 0 && i < list.size - 1 && list[i + 1].third == 0) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The PDF kept in a book's folder. */
private fun dirSource(dir: File): File? = dir.listFiles()?.firstOrNull { it.name.startsWith("source.") }

/** One screen: a page, or two side by side; pinch or double-tap to zoom in. */
@Composable
private fun PdfScreen(
    pages: PdfPages,
    shown: IntArray,
    current: Boolean,
    taps: TapZones,
    tapWidth: Float,
    onZoomed: (Boolean) -> Unit,
    onTurn: (Int) -> Unit,
    onMiddle: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zoom = remember { Zoom() }
    val turnNow by rememberUpdatedState(onTurn)
    val middleNow by rememberUpdatedState(onMiddle)
    // Turned away from: back to the whole page.
    LaunchedEffect(current) { if (!current) zoom.reset() }
    LaunchedEffect(zoom.zoomed, current) { if (current) onZoomed(zoom.zoomed) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val screen = Size(w, h)
        // Sizes come in as each page is first drawn.
        for (i in shown) LaunchedEffect(i) { pages.size(i) }
        val placed = placePages(shown, { pages.sizes[it] }, w, h)
        val content = placed.reduce { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
        // The gestures below last longer than one drawing of the screen: they read these as they are now.
        val contentNow by rememberUpdatedState(content)
        val density = LocalDensity.current
        fun zoomTo(target: Float, focus: Offset) {
            val s0 = zoom.scale
            val o0 = zoom.offset
            val s1 = target.coerceIn(1f, Zoom.MAX)
            val c = Offset(w / 2, h / 2)
            val o1 = zoom.held((focus - c) * (1 - s1 / s0) + o0 * (s1 / s0), screen, contentNow, s1)
            scope.launch {
                animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                    zoom.scale = s0 + (s1 - s0) * t
                    zoom.offset = o0 + (o1 - o0) * t
                }
            }
        }
        Box(
            Modifier.fillMaxSize()
                // Pinching zooms; once zoomed, one finger moves the page about.
                .pointerInput(shown.toList()) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = 0f
                        var active = false
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val by = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (fingers >= 2) active = true
                            else if (zoom.zoomed && !active) {
                                moved += pan.getDistance()
                                if (moved > viewConfiguration.touchSlop) active = true
                            }
                            if (active && (by != 1f || pan != Offset.Zero)) {
                                zoom.change(by, pan, event.calculateCentroid(useCurrent = true), screen, contentNow)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Taps: the sides turn the page, the middle shows the controls, twice zooms.
                .pointerInput(taps, tapWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                        val x = up.position.x / size.width
                        val y = up.position.y / size.height
                        val side = when {
                            zoom.zoomed || taps == TapZones.OFF -> 0
                            taps == TapZones.TOP_BOTTOM && y < tapWidth -> -1
                            taps == TapZones.TOP_BOTTOM && y > 1 - tapWidth -> 1
                            x < tapWidth -> if (taps == TapZones.FORWARD) 1 else -1
                            x > 1 - tapWidth -> 1
                            else -> 0
                        }
                        if (side != 0) {
                            turnNow(side)
                            return@awaitEachGesture
                        }
                        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
                        if (second == null) {
                            middleNow()
                        } else {
                            waitForUpOrCancellation()
                            if (zoom.zoomed) zoomTo(1f, second.position) else zoomTo(Zoom.DOUBLE_TAP, second.position)
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            }) {
                for ((k, i) in shown.withIndex()) {
                    val r = placed[k]
                    val pw = r.width.toInt().coerceAtLeast(1)
                    val ph = r.height.toInt().coerceAtLeast(1)
                    val drawn by produceState<Bitmap?>(null, i, pw, ph) { value = pages.page(i, pw, ph) }
                    Box(
                        Modifier.offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                            .size(with(density) { pw.toDp() }, with(density) { ph.toDp() })
                            .background(Color.White),
                    ) {
                        drawn?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
            // Zoomed in, the part on the screen is drawn again sharp once the page stops moving.
            var sharp by remember { mutableStateOf<Pair<Pair<Float, Offset>, Bitmap>?>(null) }
            val at = zoom.scale to zoom.offset
            LaunchedEffect(at, placed, current) {
                if (!zoom.zoomed || !current) {
                    sharp = null
                    return@LaunchedEffect
                }
                delay(160)
                val onScreen = placed.map { zoom.onScreen(it, screen) }
                pages.sharp(shown, onScreen, w.toInt(), h.toInt())?.let { sharp = at to it }
            }
            sharp?.takeIf { it.first == at }?.let { (_, bmp) ->
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
