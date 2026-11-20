package dev.kolib.app.ui.reader

import androidx.compose.runtime.saveable.rememberSaveable
import dev.kolib.app.ui.tour.tourPlace
import dev.kolib.app.ui.tour.TourPlaces
import dev.kolib.app.ui.tour.Tour
import dev.kolib.app.ui.tour.LocalTourPlaces
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicBackground
import dev.kolib.app.comic.ComicBlend
import dev.kolib.app.comic.ComicFlash
import dev.kolib.app.comic.ComicInfo
import dev.kolib.app.comic.ComicLayout
import dev.kolib.app.comic.ComicMode
import dev.kolib.app.comic.ComicOwn
import dev.kolib.app.comic.ComicOwnStore
import dev.kolib.app.comic.ComicPages
import dev.kolib.app.comic.ComicRotation
import dev.kolib.app.comic.ComicScale
import dev.kolib.app.comic.ComicSettings
import dev.kolib.app.comic.ComicSettingsStore
import dev.kolib.app.library.ComicEntry
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.PdfPlace
import dev.kolib.app.reader.ReaderKeys
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.reader.ReadingHereIndex
import dev.kolib.app.tr
import dev.kolib.app.trc
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A comic open in the reader: its pages, and where its place is kept. */
internal class OpenComic(
    val book: LibraryBook,
    val pages: ComicPages,
    val dir: File,
    /** Its ComicInfo says it reads right to left (as a manga's does). */
    val rtl: Boolean = false,
    /** Its name in its series ("#2"), or its title. */
    val name: String = book.title,
) {
    val id: String get() = book.id
}

/** A comic before or after the one being read in its series: [comic] once it's open, [failed] if it couldn't be. */
internal class Neighbour(val id: String, val name: String, val comic: OpenComic? = null, val failed: Boolean = false)

/** The page between two comics in a series, or after the last: [before] read, [after] next (null past an end). */
internal class Between(val before: Neighbour?, val after: Neighbour?) {
    val key: String = "between:${before?.id}:${after?.id}"
}

/** What the reader shows in its turn: a page (or part of one) of one of the comics, or the page between two. */
internal sealed class ReadItem<out P> {
    abstract val key: String

    class Page<P>(val comic: OpenComic, val piece: P, val page: Int, override val key: String) : ReadItem<P>()

    class Gap(val between: Between) : ReadItem<Nothing>() {
        override val key: String get() = between.key
    }
}

/**
 * What the reader shows, in order: the comic before's pages (once it's open), the comic being read's, and the next's,
 * as Mihon has them. Between them, the page between comics, unless they're [joined] and the comic's open; and after
 * the last comic, a page that says it's the end. [pieces] are a comic's pages as they're shown.
 */
internal fun <P> readItems(
    current: OpenComic,
    before: Neighbour?,
    after: Neighbour?,
    joined: Boolean,
    pieces: (OpenComic) -> List<P>,
    keyOf: (P) -> String,
    pageOf: (P) -> Int,
): List<ReadItem<P>> {
    val out = ArrayList<ReadItem<P>>()
    fun addPages(c: OpenComic) {
        for (p in pieces(c)) out += ReadItem.Page(c, p, pageOf(p), c.id + "/" + keyOf(p))
    }
    val me = Neighbour(current.id, current.name, current)
    if (before != null) {
        before.comic?.let { addPages(it) }
        if (before.comic == null || !joined) out += ReadItem.Gap(Between(before, me))
    }
    addPages(current)
    if (after == null) {
        out += ReadItem.Gap(Between(me, null))
    } else {
        if (after.comic == null || !joined) out += ReadItem.Gap(Between(me, after))
        after.comic?.let { addPages(it) }
    }
    return out
}

/** Where the page at [at] in [was] is in [now], if it's still there and has moved: null if it hasn't. */
internal fun movedTo(was: List<ReadItem<*>>, now: List<ReadItem<*>>, at: Int): Int? {
    val key = was.getOrNull(at)?.key ?: return null
    val i = now.indexOfFirst { it.key == key }
    return i.takeIf { it >= 0 && it != at }
}

/** The 4 × 5 colour matrix that makes pages greyscale or inverted (or both); null for none. */
internal fun pageFilterMatrix(greyscale: Boolean, inverted: Boolean): FloatArray? {
    if (!greyscale && !inverted) return null
    // Greyscale as Android's own (its weights for red, green and blue); inverted, each channel taken from 255.
    val r = 0.213f
    val g = 0.715f
    val b = 0.072f
    return when {
        greyscale && inverted -> floatArrayOf(-r, -g, -b, 0f, 255f, -r, -g, -b, 0f, 255f, -r, -g, -b, 0f, 255f, 0f, 0f, 0f, 1f, 0f)
        greyscale -> floatArrayOf(r, g, b, 0f, 0f, r, g, b, 0f, 0f, r, g, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        else -> floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)
    }
}

/** How the custom colour filter is drawn over the page. */
internal fun ComicBlend.toBlendMode(): BlendMode = when (this) {
    ComicBlend.DEFAULT -> BlendMode.SrcOver
    ComicBlend.MULTIPLY -> BlendMode.Multiply
    ComicBlend.SCREEN -> BlendMode.Screen
    ComicBlend.OVERLAY -> BlendMode.Overlay
    ComicBlend.LIGHTEN -> BlendMode.Lighten
    ComicBlend.DARKEN -> BlendMode.Darken
}

/** The window's brightness for the comic reader: its own if set, else the book reader's; 0 there is the tablet's own. */
internal fun comicBrightness(s: ComicSettings, readerBrightness: Float): Float = when {
    s.customBrightness && s.brightness > 0 -> s.brightness / 100f
    s.customBrightness && s.brightness < 0 -> 0.01f
    s.customBrightness -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    readerBrightness >= 0f -> readerBrightness
    else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
}

/** The rotation comics had before they had their own: the book reader's. */
internal fun ReaderRotation.forComics(): ComicRotation = when (this) {
    ReaderRotation.AUTO -> ComicRotation.FREE
    ReaderRotation.PORTRAIT -> ComicRotation.PORTRAIT
    ReaderRotation.LANDSCAPE -> ComicRotation.LANDSCAPE
}

private sealed interface Opened {
    class Ok(val comic: OpenComic) : Opened
    class Failed(val message: String) : Opened
}

/** Opens [b]'s comic file: its list of pages read, and whether its ComicInfo says it reads right to left. */
private suspend fun openComic(library: Library, b: LibraryBook, name: String): Opened = withContext(Dispatchers.IO) {
    try {
        val file = library.sourceFile(b.id) ?: return@withContext Opened.Failed(tr("The comic isn't in the library any more."))
        val p = ComicPages(ComicArchives.open(file))
        if (p.count == 0) {
            p.close()
            return@withContext Opened.Failed(tr("There are no pictures in this comic book file."))
        }
        val rtl = runCatching { ComicInfo.read(p.archive.comicInfo, file.name).rightToLeft }.getOrDefault(false)
        Opened.Ok(OpenComic(b, p, library.bookDir(b.id), rtl, name))
    } catch (e: Exception) {
        AppLog.e("Comic", "The comic couldn't be opened", e)
        Opened.Failed(tr("This comic couldn't be opened. The file may be damaged, or locked with a password."))
    }
}

/** Places and finishes are written one after another, so a finish is never undone by a place saved just before it. */
@OptIn(ExperimentalCoroutinesApi::class)
private val placeWriter = Dispatchers.IO.limitedParallelism(1)

/**
 * Reads a comic (a CBZ, CBR or CB7 kept in the library) the way Mihon does: page by page left to right, right to
 * left or top to bottom, or as one long strip, with Mihon's ways of fitting pages to the screen, cropping their
 * borders, splitting wide pages and tapping to turn. The pages are read from the comic file as they're needed.
 * In a series, the comics before and after are opened too, so reading goes on from one into the next: with the page
 * between them, or straight on, as one.
 */
@Composable
fun ComicBookReader(
    book: LibraryBook,
    library: Library,
    readerSettings: ReaderSettingsStore,
    onClose: () -> Unit,
    readingHere: ReadingHereIndex? = null,
    /** Opens the app's own settings: its look, language and books folder. */
    onAppSettings: (() -> Unit)? = null,
    /** The series the comic is in, if it's in one: it's read on into the comics before and after. */
    series: ComicEntry? = null,
    /** The comic being read is now another in the series (read on into, or chosen with the buttons). */
    onOpen: ((String) -> Unit)? = null,
    /** The comic reader's tour is given: it's the first time a comic is read. */
    tour: Boolean = false,
    onTourDone: () -> Unit = {},
    /** Turns every tour off, from the tour. */
    onToursOff: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val rs by readerSettings.settings.collectAsStateWithLifecycle()
    val store = remember { ComicSettingsStore.get(context, firstRotation = readerSettings.settings.value.rotation.forComics()) }
    val s by store.settings.collectAsStateWithLifecycle()
    val ownStore = remember { ComicOwnStore.get(context) }
    val owns by ownStore.all.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // The comic being read: the one opened, until reading goes on into another.
    var currentId by remember { mutableStateOf(book.id) }
    // Counted up when a comic's opened at a place of its own (rather than read on into): the pages start again there.
    var generation by remember { mutableIntStateOf(0) }
    var startOverride by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(book.id) {
        if (book.id != currentId) {
            currentId = book.id
            startOverride = null
            generation++
        }
    }
    val issues = series?.issues?.takeIf { l -> l.any { it.id == currentId } } ?: listOf(book)
    val at = issues.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val currentBook = issues[at]
    val beforeBook = issues.getOrNull(at - 1)
    val afterBook = issues.getOrNull(at + 1)
    val nameOf = { b: LibraryBook -> series?.let { dev.kolib.app.ui.library.issueName(it, b) } ?: b.title }

    // The comics open: the one being read first, then those either side; any others closed a moment after they're left.
    val opened = remember { mutableStateMapOf<String, OpenComic>() }
    val failed = remember { mutableStateMapOf<String, String>() }
    val alive = remember { AtomicBoolean(true) }
    val wanted = listOfNotNull(currentBook, afterBook, beforeBook)
    LaunchedEffect(wanted.map { it.id }) {
        for (b in wanted) {
            if (opened.containsKey(b.id) || failed.containsKey(b.id)) continue
            withContext(NonCancellable) {
                when (val r = openComic(library, b, nameOf(b))) {
                    is Opened.Ok -> if (alive.get()) opened[b.id] = r.comic else withContext(Dispatchers.IO) { r.comic.pages.close() }
                    is Opened.Failed -> failed[b.id] = r.message
                }
            }
        }
        delay(3_000)
        val keep = wanted.map { it.id }.toSet()
        for (id in opened.keys.toList()) {
            if (id !in keep) opened.remove(id)?.let { c -> withContext(Dispatchers.IO) { c.pages.close() } }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            alive.set(false)
            val all = opened.values.toList()
            opened.clear()
            Thread { all.forEach { runCatching { it.pages.close() } } }.start()
        }
    }

    val current = opened[currentId]
    // Chosen for this series (or this comic, in none), as Mihon keeps them.
    val ownKey = series?.key ?: ("b:" + currentId)
    val own = owns[ownKey] ?: ComicOwn()
    val oldMode = remember(currentId) { ComicSettingsStore.modeOf(library.bookDir(currentId)) }
    val mode = own.mode ?: oldMode ?: if (current?.rtl == true) ComicMode.RIGHT_TO_LEFT else s.mode
    val rotation = own.rotation ?: s.rotation

    // The whole screen, as in Mihon, unless the bars are to stay.
    var menu by remember { mutableStateOf(false) }
    DisposableEffect(menu, s.fullscreen) {
        val window = (context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (menu || !s.fullscreen) insets?.show(WindowInsetsCompat.Type.systemBars()) else insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    DisposableEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    ScreenFacing(rotation.orientation)
    val brightness = comicBrightness(s, rs.brightness)
    DisposableEffect(brightness) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
        onDispose { window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } } }
    }

    // The tour: over the comic with its bars showing, lighting each part where it is.
    var touring by rememberSaveable { mutableStateOf(tour) }
    LaunchedEffect(tour) { if (tour) touring = true }
    val tourPlaces = remember { TourPlaces() }
    LaunchedEffect(touring, current != null, menu) { if (touring && current != null) menu = true }

    val error = failed[currentId]
    CompositionLocalProvider(LocalTourPlaces provides tourPlaces.takeIf { touring }) { Box(Modifier.fillMaxSize()) {
    when {
        current != null -> ComicView(
            comic = current,
            s = s,
            mode = mode,
            menu = menu,
            setMenu = { menu = it },
            onSettings = { change -> store.update(change) },
            onClose = onClose,
            onCover = { b, bytes ->
                scope.launch {
                    val ok = runCatching {
                        val jpeg = withContext(Dispatchers.IO) { dev.kolib.app.convert.ComicConverter.coverJpeg(bytes) } ?: error("unreadable")
                        // The cover's own size, as made (no bigger than the library needs).
                        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
                        library.updateCover(b.id, jpeg, "image/jpeg", o.outWidth, o.outHeight)
                    }.isSuccess
                    Toast.makeText(context, if (ok) tr("Set as the cover.") else tr("Couldn't set that page as the cover."), Toast.LENGTH_SHORT).show()
                }
            },
            readingHere = readingHere,
            startPage = startOverride,
            generation = generation,
            series = series?.title,
            before = beforeBook?.let { Neighbour(it.id, nameOf(it), opened[it.id], failed.containsKey(it.id)) },
            after = afterBook?.let { Neighbour(it.id, nameOf(it), opened[it.id], failed.containsKey(it.id)) },
            onCurrent = { id ->
                if (id != currentId) {
                    startOverride = null
                    currentId = id
                    onOpen?.invoke(id)
                }
            },
            onGoTo = { id ->
                startOverride = 0
                currentId = id
                generation++
                onOpen?.invoke(id)
            },
            own = own,
            onOwn = { o ->
                ownStore.set(ownKey, o)
                // A mode chosen before modes were kept for each series gives way to this choice, whatever it is.
                if (o.mode != own.mode) issues.forEach { ComicSettingsStore.forgetMode(library.bookDir(it.id)) }
            },
            onAppSettings = onAppSettings,
        )
        error != null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text(tr("Back")) }
            }
        }
        else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    if (touring && current != null) {
        val stops = remember { dev.kolib.app.ui.tour.Tours.comicReader() }
        Tour(stops, tourPlaces, onDone = {
            touring = false
            onTourDone()
        }, onTurnOff = onToursOff?.let { off -> { touring = false; off() } })
    }
    } }
}

/** For tests and pictures of the screen: one comic on its own, its reading mode kept here. */
@Composable
internal fun ComicView(
    book: LibraryBook,
    pages: ComicPages,
    dir: File,
    s: ComicSettings,
    mode: ComicMode,
    menu: Boolean,
    setMenu: (Boolean) -> Unit,
    onMode: (ComicMode?) -> Unit,
    onSettings: ((ComicSettings) -> ComicSettings) -> Unit,
    onClose: () -> Unit,
    onCover: (ByteArray) -> Unit,
    readingHere: ReadingHereIndex?,
    onAppSettings: (() -> Unit)? = null,
    settingsOpen: Boolean = false,
    settingsTab: Int = SETTINGS_TAB_MODE,
) {
    val comic = remember(book.id, pages) { OpenComic(book, pages, dir) }
    var own by remember { mutableStateOf(ComicOwn()) }
    ComicView(
        comic = comic, s = s, mode = mode, menu = menu, setMenu = setMenu, onSettings = onSettings, onClose = onClose,
        onCover = { _, bytes -> onCover(bytes) }, readingHere = readingHere, own = own,
        onOwn = { own = it; onMode(it.mode) }, onAppSettings = onAppSettings, settingsOpen = settingsOpen, settingsTab = settingsTab,
    )
}

private enum class ComicSheet { MODE, ROTATION, SETTINGS }

/**
 * A comic open in the reader: its pages in the reading mode chosen (with the comics either side of it in its series,
 * as they're opened), the bars over them, and the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComicView(
    comic: OpenComic,
    s: ComicSettings,
    mode: ComicMode,
    menu: Boolean,
    setMenu: (Boolean) -> Unit,
    onSettings: ((ComicSettings) -> ComicSettings) -> Unit,
    onClose: () -> Unit,
    /** A page chosen as the cover of the comic it's in. */
    onCover: (LibraryBook, ByteArray) -> Unit,
    readingHere: ReadingHereIndex?,
    /** The page to open at; null for where the comic was left. */
    startPage: Int? = null,
    /** Counted up when the comic's opened anew at a place of its own: the pages start again there. */
    generation: Int = 0,
    /** The series' name, if the comic's in one. */
    series: String? = null,
    before: Neighbour? = null,
    after: Neighbour? = null,
    /** Reading went on (or back) into [String], the comic before or after. */
    onCurrent: (String) -> Unit = {},
    /** The comic before or after, chosen with the buttons beside the slider: opened at its first page. */
    onGoTo: (String) -> Unit = {},
    own: ComicOwn = ComicOwn(),
    onOwn: (ComicOwn) -> Unit = {},
    /** Opens the app's own settings: its look, language and books folder. */
    onAppSettings: (() -> Unit)? = null,
    /** For pictures of the screen in tests: the settings open. */
    settingsOpen: Boolean = false,
    settingsTab: Int = SETTINGS_TAB_MODE,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val count = comic.pages.count
    val start = remember(generation) {
        (startPage ?: ReaderStore.pdfPlace(comic.dir)?.page ?: 0).coerceIn(0, (count - 1).coerceAtLeast(0))
    }
    // The page reached in each comic, as reading goes on.
    val placeIn = remember(generation) { mutableStateMapOf(comic.id to start) }
    val page = (placeIn[comic.id] ?: 0).coerceIn(0, (count - 1).coerceAtLeast(0))
    // On the page between two comics (or after the last).
    var onGap by remember(generation) { mutableStateOf(false) }
    // Pages turned to (or scrolled to), counted: the menu goes, and the screen may flash.
    var turns by remember { mutableIntStateOf(0) }
    key(comic.id) { CountReading(rememberReadingSession(comic.book), page) }
    var sheet by remember { mutableStateOf(if (settingsOpen) ComicSheet.SETTINGS else null) }
    var settingsTabNow by remember { mutableIntStateOf(settingsTab) }
    var actionsFor by remember { mutableStateOf<Pair<OpenComic, Int>?>(null) }
    // Taps, keys and the bar's buttons: on or back, for the page or strip showing to act on; and pages to go to.
    val nudges = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }
    val jumps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    // The menu, shown and hidden by taps on the page. Those are handled for as long as the page shows, so they
    // read whether it's showing as it is at the time.
    val menuNow by rememberUpdatedState(menu)
    val setMenuNow by rememberUpdatedState(setMenu)
    val toggleMenu = remember { { setMenuNow(!menuNow) } }
    // As in Mihon, the menu goes when the page is turned or the strip scrolled, other than from the menu itself.
    var menuMovedAt by remember { mutableLongStateOf(-1_000_000L) }
    val fromMenu = { menuMovedAt = SystemClock.uptimeMillis() }
    val moved = remember { { if (menuNow && SystemClock.uptimeMillis() - menuMovedAt > 1500) setMenuNow(false) } }
    LaunchedEffect(mode.paged) { if (mode.paged) snapshotFlow { turns }.drop(1).collect { moved() } }
    LaunchedEffect(comic.id, page) {
        // On its first page (its cover) it isn't being read: not read yet, or still finished if it was.
        if (page == 0) {
            val here = withContext(placeWriter) { ReaderStore.leaveAtStart(comic.dir) }
            if (here != null) readingHere?.set(comic.id, here) else readingHere?.forget(listOf(comic.id))
            return@LaunchedEffect
        }
        val place = PdfPlace(page, count)
        withContext(placeWriter) { ReaderStore.savePdfPlace(comic.dir, place) }
        readingHere?.set(comic.id, place.readHere)
    }
    // A comic read to its end is marked as finished: on the page after its last, or on into the next.
    val finished = remember { mutableSetOf<String>() }
    val readingHereNow by rememberUpdatedState(readingHere)
    val finish = remember {
        { c: OpenComic ->
            if (finished.add(c.id)) {
                scope.launch {
                    val here = withContext(placeWriter) { ReaderStore.markFinished(c.dir) }
                    readingHereNow?.set(c.id, here)
                }
            }
        }
    }
    val comicNow by rememberUpdatedState(comic)
    val afterNow by rememberUpdatedState(after)
    val onCurrentNow by rememberUpdatedState(onCurrent)
    // A page settled on (or the page between), as the pager or strip says.
    val onAt = remember(generation) {
        // The first is where the comic opened: not a page turned to.
        var last: String? = null
        { item: ReadItem<*> ->
            if (last != null && last != item.key) turns++
            last = item.key
            when (item) {
                is ReadItem.Page -> {
                    onGap = false
                    val c = item.comic
                    if (item.page < c.pages.count - 1) finished.remove(c.id)
                    placeIn[c.id] = item.page
                    if (c.id != comicNow.id) {
                        if (c.id == afterNow?.id) finish(comicNow)
                        onCurrentNow(c.id)
                    }
                }
                is ReadItem.Gap -> {
                    onGap = true
                    if (item.between.before?.id == comicNow.id) finish(comicNow)
                }
            }
        }
    }
    val onGapShown = remember(generation) { { b: Between -> if (b.before?.id == comicNow.id) finish(comicNow) } }
    DisposableEffect(s.volumeKeys, s.invertVolume) {
        ReaderKeys.handler = if (!s.volumeKeys) null else { e ->
            val by = when (e.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                else -> 0
            }
            if (by != 0 && e.action == KeyEvent.ACTION_DOWN) nudges.tryEmit(if (s.invertVolume) -by else by)
            by != 0
        }
        onDispose { ReaderKeys.handler = null }
    }
    // Back closes the comic, whether the bars are showing or not (a sheet open over it closes first).
    BackHandler {
        when {
            sheet != null -> sheet = null
            actionsFor != null -> actionsFor = null
            else -> onClose()
        }
    }
    val tone by produceState(Color.Black, s.background, comic.id, page) {
        value = when (s.background) {
            ComicBackground.BLACK -> Color.Black
            ComicBackground.GRAY -> Color(0xFF3A3A3A)
            ComicBackground.WHITE -> Color.White
            ComicBackground.AUTO -> Color(comic.pages.tone(page))
        }
    }
    // Writing on the page between comics: dark on a light background, light on a dark one.
    val ink = if (tone.luminance() > 0.5f) Color.Black else Color.White
    val matrix = remember(s.greyscale, s.inverted) { pageFilterMatrix(s.greyscale, s.inverted) }
    val hasCutout = view.rootWindowInsets?.displayCutout != null
    val insets = when {
        !s.fullscreen -> WindowInsets.systemBars
        !s.cutout -> WindowInsets.displayCutout
        else -> WindowInsets(0)
    }
    Box(Modifier.fillMaxSize().background(tone)) {
        val longPress = { c: OpenComic, i: Int -> if (s.longTap) actionsFor = c to i }
        // The pages, and the colour filter over them (mixed with them alone, where it's blended).
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(insets).tourPlace("comic-page")
                .then(if (s.colourFilter && s.blend != ComicBlend.DEFAULT) Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) else Modifier),
        ) {
            key(generation) {
                if (mode.paged) {
                    PagedComic(
                        current = comic, before = before, after = after, s = s, mode = mode, startPage = start, series = series, ink = ink,
                        matrix = matrix, onAt = onAt, onMenu = toggleMenu, onLongPress = longPress, onClose = onClose, nudges = nudges, jumps = jumps,
                    )
                } else {
                    StripComic(
                        current = comic, before = before, after = after, s = s, mode = mode, startPage = start, series = series, ink = ink,
                        matrix = matrix, onAt = onAt, onGapShown = onGapShown, onMenu = toggleMenu, onLongPress = longPress, onMoved = moved,
                        onClose = onClose, nudges = nudges, jumps = jumps,
                    )
                }
            }
            if (s.colourFilter) {
                val blend = s.blend.toBlendMode()
                Canvas(Modifier.fillMaxSize().testTag("comic-colour-filter")) { drawRect(Color(s.colour), blendMode = blend) }
            }
        }
        // Darker than the screen goes: the rest done by darkening the page, as in Mihon.
        if (s.customBrightness && s.brightness < 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = -s.brightness / 100f)).testTag("comic-dimming"))
        }
        FlashOnTurn(s, turns)
        // Where the taps go, shown for a moment as the comic opens, if asked for.
        var zones by remember(mode) { mutableStateOf(s.showTapZones) }
        LaunchedEffect(zones) { if (zones) { delay(1800); zones = false } }
        if (zones) TapZonesOverlay(s, mode)
        if (s.pageNumber && !menu && !onGap) {
            Text(
                "${page + 1} / $count",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        ComicBars(
            visible = menu,
            title = series ?: comic.book.title,
            subtitle = if (series != null) comic.book.title else null,
            onBack = onClose,
            nav = ComicNavigation(
                page = page,
                count = count,
                rtl = mode == ComicMode.RIGHT_TO_LEFT,
                onPage = { p -> fromMenu(); jumps.tryEmit(p) },
                previous = before?.let { b -> { fromMenu(); onGoTo(b.id) } },
                next = after?.let { a -> { fromMenu(); onGoTo(a.id) } },
            ),
            vertical = mode in s.verticalIn,
            verticalLeft = s.verticalLeft,
            verticalHeight = s.verticalHeight / 100f,
            modeIcon = own.mode?.icon() ?: defaultModeIcon,
            rotationIcon = own.rotation.icon(),
            cropping = s.cropping(mode),
            onMode = { sheet = ComicSheet.MODE },
            onRotation = { sheet = ComicSheet.ROTATION },
            onCrop = {
                val on = !s.cropping(mode)
                onSettings { if (mode.paged) it.copy(cropPaged = on) else it.copy(cropStrip = on) }
                Toast.makeText(context, if (on) tr("Crop borders: on") else tr("Crop borders: off"), Toast.LENGTH_SHORT).show()
            },
            onSettings = { settingsTabNow = SETTINGS_TAB_MODE; sheet = ComicSheet.SETTINGS },
        )
        when (sheet) {
            ComicSheet.MODE -> ComicChoiceSheet(
                title = tr("Reading mode"), options = ComicMode.entries, own = own.mode, icon = { it.icon() }, label = { it.label() },
                onApply = { m -> onOwn(own.copy(mode = m)); sheet = null }, onDismiss = { sheet = null }, tag = "comic-mode-sheet",
            )
            ComicSheet.ROTATION -> ComicChoiceSheet(
                title = tr("Rotation"), options = ComicRotation.entries, own = own.rotation, icon = { it.icon() }, label = { it.label() },
                onApply = { r ->
                    onOwn(own.copy(rotation = r))
                    sheet = null
                    scope.launch { sayIfNotTurned(context, (r ?: s.rotation).orientation) }
                },
                onDismiss = { sheet = null }, tag = "comic-rotation-sheet",
                note = { RotationTip(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) },
                // A tap turns the screen there and then, as the book reader's rotation does. The way it turns now is
                // shown chosen, the series' own or the default.
                applyOnTap = true,
                current = own.rotation ?: s.rotation,
            )
            ComicSheet.SETTINGS -> ComicSettingsSheet(
                s = s, mode = mode, own = own, inSeries = series != null, hasCutout = hasCutout, onOwn = onOwn, onSettings = onSettings,
                onAppSettings = onAppSettings?.let { open -> { sheet = null; open() } },
                // On the filter's tab, the bars go, so the page can be seen as it's filtered.
                onTab = { t -> settingsTabNow = t; setMenu(t != SETTINGS_TAB_FILTER) },
                onDismiss = { sheet = null; setMenu(true) },
                startTab = settingsTabNow,
            )
            null -> Unit
        }
        actionsFor?.let { (c, i) ->
            val actions = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { actionsFor = null }, sheetState = actions) {
                Text(tr("Page {0}", i + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                ListItem(
                    headlineContent = { Text(tr("Use as the cover")) },
                    leadingContent = { Icon(Icons.Outlined.Image, null) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        scope.launch { runCatching { c.pages.file(i) }.getOrNull()?.let { onCover(c.book, it) } }
                    },
                )
                ListItem(
                    headlineContent = { Text(tr("Share")) },
                    leadingContent = { Icon(Icons.Outlined.Share, null) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        scope.launch { sharePage(context, c.pages, i, c.book.title) }
                    },
                )
                ListItem(
                    headlineContent = { Text(tr("Save to Pictures")) },
                    leadingContent = { Icon(Icons.Outlined.Save, null) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        scope.launch { savePage(context, c.pages, i, c.book.title) }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The screen flashed black or white as the page changes, every so many pages, to clear an e-ink screen's ghosting. */
@Composable
private fun FlashOnTurn(s: ComicSettings, turns: Int) {
    if (!s.flash) return
    var colour by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(turns) {
        // Not as the comic opens: only for pages turned to.
        if (turns <= 1 || (turns - 1) % s.flashEvery != 0) return@LaunchedEffect
        val ms = s.flashMillis.toLong()
        when (s.flashWith) {
            ComicFlash.BLACK -> { colour = Color.Black; delay(ms) }
            ComicFlash.WHITE -> { colour = Color.White; delay(ms) }
            ComicFlash.WHITE_BLACK -> { colour = Color.White; delay(ms / 2); colour = Color.Black; delay(ms - ms / 2) }
        }
        colour = null
    }
    colour?.let { Box(Modifier.fillMaxSize().background(it).testTag("comic-flash")) }
}

/** The page's picture file's ending, for naming a copy of it. */
private fun extensionOf(pages: ComicPages, i: Int) = pages.archive.pages[i].substringAfterLast('.', "jpg").lowercase()

private fun mimeOf(ext: String) = when (ext) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "avif" -> "image/avif"
    "heic", "heif" -> "image/heif"
    else -> "image/jpeg"
}

private fun safeName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60).ifEmpty { "Comic" }

private suspend fun sharePage(context: android.content.Context, pages: ComicPages, i: Int, title: String) {
    val ext = extensionOf(pages, i)
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "${safeName(title)} ${i + 1}.$ext").also { it.writeBytes(pages.file(i)) }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mimeOf(ext)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, tr("Share"))) }
}

private suspend fun savePage(context: android.content.Context, pages: ComicPages, i: Int, title: String) {
    val ext = extensionOf(pages, i)
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName(title)} ${i + 1}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mimeOf(ext))
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KoLib")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no place")
            context.contentResolver.openOutputStream(uri)?.use { it.write(pages.file(i)) } ?: error("not written")
        }.isSuccess
    }
    Toast.makeText(context, if (ok) tr("Saved to Pictures/KoLib.") else tr("The page couldn't be saved."), Toast.LENGTH_SHORT).show()
}

/** Where taps go, coloured, for a moment: back a page, on a page, and the menu. */
@Composable
private fun TapZonesOverlay(s: ComicSettings, mode: ComicMode) {
    val back = Color(0x66E53935)
    val on = Color(0x6643A047)
    Row(Modifier.fillMaxSize()) {
        // Drawn as a grid of thirds, each coloured by what a tap in its middle does.
        for (cx in 0 until 3) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                for (cy in 0 until 3) {
                    val a = ComicLayout.tapAction((cx + 0.5f) / 3, (cy + 0.5f) / 3, s.taps(mode), mode, s.invertTaps(mode))
                    Box(Modifier.weight(1f).fillMaxWidth().background(if (a < 0) back else if (a > 0) on else Color.Transparent))
                }
            }
        }
    }
}

// ---- touches ----------------------------------------------------------------------------------------

/** How a touch begins: let go where it went down (a tap), held there, moved, joined by a second finger, or gone. */
private enum class Start { TAP, HOLD, DRAG, PINCH, GONE }

/**
 * Follows a touch from [down] until it's clear how it begins, returning that and the event that showed it. Held
 * still only counts if [holds]; otherwise it waits on for the finger to move or let go.
 */
private suspend fun AwaitPointerEventScope.howItStarts(
    down: PointerInputChange,
    holds: Boolean,
    pass: PointerEventPass = PointerEventPass.Main,
): Pair<Start, PointerEvent?> {
    val slop = viewConfiguration.touchSlop
    var last: PointerEvent? = null
    suspend fun AwaitPointerEventScope.follow(): Start {
        while (true) {
            val e = awaitPointerEvent(pass)
            last = e
            val c = e.changes.firstOrNull { it.id == down.id } ?: return Start.GONE
            when {
                e.changes.count { it.pressed } >= 2 -> return Start.PINCH
                !c.pressed -> return if (c.isConsumed) Start.GONE else Start.TAP
                (c.position - down.position).getDistance() > slop -> return Start.DRAG
            }
        }
    }
    val start = if (holds) withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { follow() } ?: Start.HOLD else follow()
    return start to last
}

/** Waits for every finger to let go. */
private suspend fun AwaitPointerEventScope.waitForAllUp(pass: PointerEventPass = PointerEventPass.Main) {
    do {
        val e = awaitPointerEvent(pass)
    } while (e.changes.any { it.pressed })
}

/** How fast one finger moved up or down zooms in or out (double-tap and hold, then move): about e times per this far. */
private val QUICK_ZOOM_DISTANCE = 160.dp

/**
 * The pager moved by a finger that's pulling a zoomed page on past its edge, as in Mihon: one scroll from the
 * first pull until the finger lets go, then on to the page coming into view or back to the one it started from.
 * The page it started from is known by its key, as pages may come and go before it (another comic opening).
 */
private class PagerPull(
    private val pager: PagerState,
    private val scope: CoroutineScope,
    private val keyAt: (Int) -> Any?,
    private val indexOf: (Any?) -> Int?,
) {
    private var deltas: Channel<Float>? = null
    private var job: Job? = null
    private var from = 0
    private var fromKey: Any? = null

    val pulling get() = deltas != null

    /** Moves the pager on (positive) or back (negative) by [by] pixels. */
    fun move(by: Float) {
        val ch = deltas ?: Channel<Float>(Channel.UNLIMITED).also { c ->
            deltas = c
            from = pager.settledPage
            fromKey = keyAt(from)
            job = scope.launch {
                pager.scroll(MutatePriority.UserInput) { for (d in c) scrollBy(d) }
            }
        }
        ch.trySend(by)
    }

    /**
     * Lets go: on to the next page or back to the one before if the pager has come far enough that way (a seventh
     * of the screen), or was flung that way at [speed] (pixels a second, on positive), else back where it was.
     */
    fun letGo(speed: Float, flingSpeed: Float, last: Int) {
        val ch = deltas ?: return
        deltas = null
        ch.close()
        val scrolling = job
        scope.launch {
            scrolling?.join()
            val start = indexOf(fromKey) ?: from
            val moved = pager.currentPage + pager.currentPageOffsetFraction - start
            val target = when {
                moved > 0.14f || (moved > 0.02f && speed > flingSpeed) -> start + 1
                moved < -0.14f || (moved < -0.02f && speed < -flingSpeed) -> start - 1
                else -> start
            }
            pager.animateScrollToPage(target.coerceIn(0, last))
        }
    }
}

// ---- page by page ----------------------------------------------------------------------------------

@Composable
private fun PagedComic(
    current: OpenComic,
    before: Neighbour?,
    after: Neighbour?,
    s: ComicSettings,
    mode: ComicMode,
    startPage: Int,
    series: String?,
    ink: Color,
    matrix: FloatArray?,
    onAt: (ReadItem<*>) -> Unit,
    onMenu: () -> Unit,
    onLongPress: (OpenComic, Int) -> Unit,
    onClose: () -> Unit,
    nudges: SharedFlow<Int>,
    jumps: SharedFlow<Int>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = mode == ComicMode.RIGHT_TO_LEFT
    val onAtNow by rememberUpdatedState(onAt)
    val sNow by rememberUpdatedState(s)
    val currentNow by rememberUpdatedState(current)
    // Which pages are known to be wide (as each is read), of each comic: with "Split wide pages", each is two.
    val wide = remember { mutableStateMapOf<String, Boolean>() }
    val looked = remember { HashSet<String>() }
    // The page the pager has settled on, by its key: kept while the pager moves.
    var settledKey by remember { mutableStateOf<Any?>(null) }
    // On the page between comics, they're kept apart even where they'd be joined, as Mihon keeps them.
    val onGap = (settledKey as? String)?.startsWith("between:") == true
    val joined = !s.alwaysTransition && !onGap
    val wideNow = wide.toMap()
    val items = remember(current, before?.comic, after?.comic, before?.id, after?.id, before?.failed, after?.failed, joined, s.splitWide, rtl, s.invertSplit, wideNow) {
        readItems(
            current, before, after, joined,
            pieces = { c -> ComicLayout.pieces(c.pages.count, { wideNow["${c.id}:$it"] == true }, s.splitWide, rtl, s.invertSplit) },
            keyOf = { it.key },
            pageOf = { it.page },
        )
    }
    val itemsNow by rememberUpdatedState(items)
    val startIndex = remember { items.indexOfFirst { it is ReadItem.Page && it.comic.id == current.id && it.page == startPage }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { itemsNow.size }
    // Pages come and go before the one showing (the comic before, opened after this one, or let go as reading goes on
    // into the next): the pager stays on its page. Its own keeping by key looks only so far, and past a long comic's
    // pages it loses the page and keeps its number, which is then in another comic.
    val lastItems = remember { arrayOfNulls<List<ReadItem<*>>>(1) }
    SideEffect {
        val was = lastItems[0]
        lastItems[0] = items
        if (was != null && was !== items) movedTo(was, items, pager.currentPage)?.let { pager.requestScrollToPage(it, pager.currentPageOffsetFraction) }
    }
    var locked by remember { mutableStateOf(false) }
    // The page settled on, known by its key in what's laid out (the list may have changed since the pager last looked).
    LaunchedEffect(pager) {
        snapshotFlow {
            if (pager.isScrollInProgress) null else pager.layoutInfo.visiblePagesInfo.firstOrNull { abs(it.offset) <= 1 }?.key
        }.filterNotNull().distinctUntilChanged().collect { k ->
            settledKey = k
            itemsNow.firstOrNull { it.key == k }?.let { onAtNow(it) }
        }
    }
    // The sizes of the pages near the one showing, read so wide ones are known before they come into view.
    LaunchedEffect(settledKey, s.splitWide) {
        if (!s.splitWide) return@LaunchedEffect
        val list = itemsNow
        val at = list.indexOfFirst { it.key == settledKey }.takeIf { it >= 0 } ?: startIndex
        for (i in (at - 3).coerceAtLeast(0)..(at + 5).coerceAtMost(list.size - 1)) {
            val item = list[i] as? ReadItem.Page ?: continue
            val k = "${item.comic.id}:${item.page}"
            if (!looked.add(k)) continue
            item.comic.pages.size(item.page)?.let { if (it.wide) wide[k] = true }
        }
    }
    fun turn(by: Int) {
        val to = (pager.currentPage + by).coerceIn(0, itemsNow.size - 1)
        if (to == pager.currentPage) return
        scope.launch { if (sNow.animate) pager.animateScrollToPage(to) else pager.scrollToPage(to) }
    }
    LaunchedEffect(pager) {
        jumps.collect { p ->
            val i = itemsNow.indexOfFirst { it is ReadItem.Page && it.comic.id == currentNow.id && it.page == p }
            if (i >= 0) pager.scrollToPage(i)
        }
    }
    // A key or button press goes to the page showing, which moves along itself first if it's bigger than the screen.
    val toPage = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }
    LaunchedEffect(Unit) { nudges.collect { toPage.emit(it) } }
    // A zoomed page pulled on past its edge moves the pager with the finger. A finger moving this way
    // (to the left, up, or to the right reading right to left) brings the next page on.
    val pull = remember(pager) {
        PagerPull(pager, scope, keyAt = { i -> itemsNow.getOrNull(i)?.key }, indexOf = { k -> itemsNow.indexOfFirst { it.key == k }.takeIf { it >= 0 } })
    }
    val onward = if (!mode.horizontal || !rtl) -1f else 1f
    val flingSpeed = with(density) { 400.dp.toPx() }
    val filter = remember(matrix) { matrix?.let { android.graphics.ColorMatrixColorFilter(it) } }
    val content: @Composable (Int) -> Unit = { idx ->
        when (val item = itemsNow.getOrNull(idx)) {
            is ReadItem.Page -> {
                val here = item.key == settledKey
                ComicPage(
                    piece = item.piece,
                    pages = item.comic.pages,
                    s = s,
                    mode = mode,
                    current = here,
                    onLocked = { if (item.key == settledKey) locked = it },
                    onTurn = ::turn,
                    canPull = { finger -> if (finger * onward > 0) pager.canScrollForward else pager.canScrollBackward },
                    onPull = { finger -> pull.move(finger * onward) },
                    onLetGo = { speed -> pull.letGo(speed * onward, flingSpeed, itemsNow.size - 1) },
                    onMenu = onMenu,
                    onLongPress = { onLongPress(item.comic, item.page) },
                    nudges = if (here) toPage else null,
                    filter = filter,
                )
            }
            is ReadItem.Gap -> BetweenPage(
                between = item.between, currentId = current.id, series = series, ink = ink, s = s, mode = mode,
                onTurn = ::turn, onMenu = onMenu, nudges = if (item.key == settledKey) toPage else null, onClose = onClose,
            )
            null -> Unit
        }
    }
    // A page zoomed in has the swipes, not the pager (the page between comics never does).
    val swipes = !(locked && !onGap)
    if (mode.horizontal) {
        HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = rtl, userScrollEnabled = swipes, beyondViewportPageCount = 1, key = { itemsNow.getOrNull(it)?.key ?: it }) { content(it) }
    } else {
        VerticalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = swipes, beyondViewportPageCount = 1, key = { itemsNow.getOrNull(it)?.key ?: it }) { content(it) }
    }
}

/**
 * The page between two comics in a series, as Mihon's between chapters: tapped (or pressed past) like a page, swiped on
 * or back like one.
 */
@Composable
private fun BetweenPage(
    between: Between,
    currentId: String,
    series: String?,
    ink: Color,
    s: ComicSettings,
    mode: ComicMode,
    onTurn: (Int) -> Unit,
    onMenu: () -> Unit,
    nudges: SharedFlow<Int>?,
    onClose: () -> Unit,
) {
    val turnNow by rememberUpdatedState(onTurn)
    val menuNow by rememberUpdatedState(onMenu)
    val sNow by rememberUpdatedState(s)
    if (nudges != null) LaunchedEffect(nudges) { nudges.collect { turnNow(it) } }
    Box(
        Modifier.fillMaxSize().testTag("comic-between").pointerInput(mode) {
            detectTapGestures { p ->
                val st = sNow
                val a = ComicLayout.tapAction(p.x / size.width, p.y / size.height, st.taps(mode), mode, st.invertTaps(mode))
                if (a != 0) turnNow(a) else menuNow()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        BetweenText(between, currentId, series, ink, onClose)
    }
}

/**
 * What the page between comics says, as Mihon's: the comic finished and the next (or that it was the last); or, come
 * to from the comic after, the one before it and the one being read.
 */
@Composable
private fun BetweenText(between: Between, currentId: String, series: String?, ink: Color, onClose: (() -> Unit)?) {
    val back = between.after?.id == currentId && between.before != null
    CompositionLocalProvider(LocalContentColor provides ink) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)) {
            if (back) {
                Named(trc("between", "Previous"), between.before!!.name)
                Spacer(Modifier.height(24.dp))
                Named(trc("between", "Current"), between.after!!.name)
            } else {
                between.before?.let { Named(trc("between", "Finished"), it.name) }
                Spacer(Modifier.height(24.dp))
                val next = between.after
                if (next != null) {
                    Named(trc("between", "Next"), next.name)
                    if (next.failed) {
                        Spacer(Modifier.height(8.dp))
                        Text(tr("It couldn't be opened."), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Row(
                        Modifier.border(1.dp, ink.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (series != null) tr("That's the last comic in {0}.", series) else tr("That's the end of the comic."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (onClose != null) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onClose, colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)) { Text(tr("Close the comic")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Named(label: String, name: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 4.dp))
    Text(name, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
}

/**
 * One page (or half of a wide one) on the screen, fitted as the settings say, and touched as in Mihon: pinched to
 * zoom; double-tapped to zoom in on the spot tapped (brought to the middle) or out again; double-tapped and held,
 * then moved up or down, to zoom with one finger; moved about when it's bigger than the screen (pulled on past its
 * edge, the next page comes on); tapped to turn or for the menu; held for what can be done with it.
 */
@Composable
private fun ComicPage(
    piece: ComicLayout.Piece,
    pages: ComicPages,
    s: ComicSettings,
    mode: ComicMode,
    current: Boolean,
    onLocked: (Boolean) -> Unit,
    onTurn: (Int) -> Unit,
    /** Whether the pager can move with a finger moving [finger] pixels along the way pages turn. */
    canPull: (Float) -> Boolean,
    /** The pager moved with the finger, pulling the page on past its edge. */
    onPull: (Float) -> Unit,
    /** The finger let go of a page pulled past its edge, moving at that speed (along the way pages turn). */
    onLetGo: (Float) -> Unit,
    onMenu: () -> Unit,
    onLongPress: () -> Unit,
    nudges: SharedFlow<Int>?,
    /** Greyscale or inverted, as the custom filter says; null for the page as it is. */
    filter: android.graphics.ColorFilter? = null,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = mode == ComicMode.RIGHT_TO_LEFT
    var region by remember(piece.key, s.cropPaged) { mutableStateOf<android.graphics.Rect?>(null) }
    var failed by remember(piece.key) { mutableStateOf(false) }
    LaunchedEffect(piece.key, s.cropPaged) {
        val size = pages.size(piece.page)
        if (size == null) { failed = true; return@LaunchedEffect }
        val whole = android.graphics.Rect(0, 0, size.width, size.height)
        val crop = if (s.cropPaged) pages.crop(piece.page) ?: whole else whole
        region = ComicLayout.regionOf(piece.part, crop)
    }
    // What touches act with, kept up to date without starting the touch handling again.
    val sNow by rememberUpdatedState(s)
    val turnNow by rememberUpdatedState(onTurn)
    val canPullNow by rememberUpdatedState(canPull)
    val pullNow by rememberUpdatedState(onPull)
    val letGoNow by rememberUpdatedState(onLetGo)
    val menuNow by rememberUpdatedState(onMenu)
    val longPressNow by rememberUpdatedState(onLongPress)
    val filterNow by rememberUpdatedState(filter)
    // Read here, outside the box that measures the screen, so the page is drawn again as soon as it's known.
    val known = region
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val r = known
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (failed) Text(tr("This page can't be shown."), color = Color.White) else CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
            }
            return@BoxWithConstraints
        }
        // Turned on its side to fill an upright screen, if asked for.
        val rotated = s.rotateWide && piece.part == ComicLayout.Part.WHOLE && r.width() > r.height() && h > w
        val cw = (if (rotated) r.height() else r.width()).toFloat()
        val ch = (if (rotated) r.width() else r.height()).toFloat()
        // Its size on the screen before zooming, in whole pixels.
        val (fw, fh) = ComicLayout.fit(cw, ch, w, h, s.scale, s.zoomWide && !rotated)
        val pw = fw.roundToInt().coerceIn(1, 32000).toFloat()
        val ph = fh.roundToInt().coerceIn(1, 32000).toFloat()
        val minZoom = if (s.scale == ComicScale.STRETCH) 1f else min(1f, min(w / pw, h / ph))
        // Up to five times as big, as in Mihon (or twice the picture's own size, for very big pictures).
        val maxZoom = max(5f, 2f * cw / pw)
        val along = mode.horizontal
        // How much bigger it's shown, and where its top left corner is on the screen.
        var zoom by remember(piece.key, pw, ph) { mutableFloatStateOf(1f) }
        var offset by remember(piece.key, pw, ph) {
            val (x, y) = ComicLayout.startOffset(pw, ph, w, h, s.zoomStart, rtl)
            mutableStateOf(Offset(x, y))
        }
        // Kept on the screen: in the middle where it's smaller than the screen, and covering it where it's bigger.
        fun held(o: Offset, z: Float): Offset {
            val dw = pw * z
            val dh = ph * z
            return Offset(if (dw <= w) (w - dw) / 2 else o.x.coerceIn(w - dw, 0f), if (dh <= h) (h - dh) / 2 else o.y.coerceIn(h - dh, 0f))
        }
        // Back to how it first showed when it's turned away from.
        LaunchedEffect(current) {
            if (!current) {
                zoom = 1f
                val (x, y) = ComicLayout.startOffset(pw, ph, w, h, sNow.zoomStart, rtl)
                offset = Offset(x, y)
            }
        }
        // Bigger than the screen the way pages turn (or zoomed): swipes move the page, not to the next.
        val lockedNow by remember(piece.key, pw, ph, w, h, along) {
            derivedStateOf { zoom > 1.01f || (if (along) pw * zoom > w + 1 else ph * zoom > h + 1) }
        }
        LaunchedEffect(lockedNow, current) { if (current) onLocked(lockedNow) }
        var moving by remember { mutableStateOf<Job?>(null) }
        fun animateTo(z1: Float, o1: Offset) {
            val z0 = zoom
            val o0 = offset
            val target = held(o1, z1)
            moving?.cancel()
            moving = scope.launch {
                animate(0f, 1f, animationSpec = tween(if (sNow.animate) 220 else 1)) { t, _ ->
                    zoom = z0 + (z1 - z0) * t
                    offset = o0 + (target - o0) * t
                }
            }
        }
        /** On (1) or back (-1): along the page first if it's bigger than the screen and there's more of it that way. */
        fun step(by: Int) {
            if (by == 0) return
            val z = zoom
            val o = offset
            if (sNow.panWide) {
                if (along && pw * z > w + 1) {
                    // Which way the page moves: on is to the right reading left to right, to the left reading right to left.
                    val dx = (if ((by > 0) != rtl) -1 else 1) * w * 0.85f
                    val moved = held(Offset(o.x + dx, o.y), z)
                    if (abs(moved.x - o.x) > 2) { animateTo(z, moved); return }
                } else if (!along && ph * z > h + 1) {
                    val moved = held(Offset(o.x, o.y - by * h * 0.85f), z)
                    if (abs(moved.y - o.y) > 2) { animateTo(z, moved); return }
                }
            }
            turnNow(by)
        }
        if (nudges != null) LaunchedEffect(nudges, pw, ph, w, h) { nudges.collect { step(it) } }
        // Drawn about the size it's shown, no bigger than about twice the screen; sharper again when zoomed in.
        val k = min(1f, sqrt(2f * w * h / (pw * ph)))
        val baseW = ((if (rotated) ph else pw) * k).toInt().coerceAtLeast(1)
        val baseH = ((if (rotated) pw else ph) * k).toInt().coerceAtLeast(1)
        val base by produceState<Bitmap?>(null, piece.key, r, baseW, baseH) { value = pages.bitmap(piece.page, r, baseW, baseH) }
        // Zoomed in further than it's drawn: the part on the screen drawn again sharp, once it stops moving. Kept
        // where it is on the page, so it moves with the page until the next one's ready.
        var sharp by remember(piece.key, pw, ph) { mutableStateOf<Pair<RectF, Bitmap>?>(null) }
        LaunchedEffect(piece.key, base, current, pw, ph, w, h, rotated) {
            sharp = null
            val b = base ?: return@LaunchedEffect
            if (!current || rotated) return@LaunchedEffect
            snapshotFlow { zoom to offset }.collectLatest { (z, o) ->
                val dw = pw * z
                val dh = ph * z
                if (dw / b.width < 1.25f) {
                    sharp = null
                    return@collectLatest
                }
                delay(150)
                val left = max(0f, o.x)
                val top = max(0f, o.y)
                val right = min(w, o.x + dw)
                val bottom = min(h, o.y + dh)
                if (right - left < 2 || bottom - top < 2) return@collectLatest
                val sx = r.width() / dw
                val sy = r.height() / dh
                val src = android.graphics.Rect(
                    (r.left + (left - o.x) * sx).toInt(), (r.top + (top - o.y) * sy).toInt(),
                    (r.left + (right - o.x) * sx).toInt().coerceAtMost(r.right), (r.top + (bottom - o.y) * sy).toInt().coerceAtMost(r.bottom),
                )
                if (src.width() < 2 || src.height() < 2) return@collectLatest
                val bmp = pages.bitmap(piece.page, src, (right - left).toInt(), (bottom - top).toInt()) ?: return@collectLatest
                // That part of the picture, placed on the page as it's laid out before zooming.
                val where = RectF(
                    (src.left - r.left) * pw / r.width(), (src.top - r.top) * ph / r.height(),
                    (src.right - r.left) * pw / r.width(), (src.bottom - r.top) * ph / r.height(),
                )
                sharp = where to bmp
            }
        }
        val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val quickZoom = with(density) { QUICK_ZOOM_DISTANCE.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(piece.key, pw, ph, w, h) {
                    val slop = viewConfiguration.touchSlop
                    val doubleTapSlop = 100.dp.toPx()

                    // Two fingers: the point of the page between them stays between them as they spread, pinch and move.
                    // Should one let go, the other moves the page on.
                    suspend fun AwaitPointerEventScope.pinch(first: PointerEvent) {
                        var e = first
                        while (true) {
                            val by = e.calculateZoom()
                            val pan = e.calculatePan()
                            if (by != 1f || pan != Offset.Zero) {
                                val z0 = zoom
                                val z1 = (z0 * by).coerceIn(minZoom, maxZoom)
                                val c = e.calculateCentroid(useCurrent = true)
                                if (c.isSpecified) {
                                    offset = held(c - (c - pan - offset) * (z1 / z0), z1)
                                    zoom = z1
                                }
                            }
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                            if (e.changes.none { it.pressed }) return
                            e = awaitPointerEvent()
                        }
                    }

                    // One finger moving: the page moves, if it's bigger than the screen that way or zoomed in; otherwise
                    // the pager has it, to turn. Pulled on past its edge the way pages turn, the pager comes along.
                    suspend fun AwaitPointerEventScope.drag(down: PointerInputChange, first: PointerEvent) {
                        val z = zoom
                        val moved = (first.changes.firstOrNull { it.id == down.id }?.position ?: down.position) - down.position
                        val bigAlong = if (along) pw * z > w + 1 else ph * z > h + 1
                        val bigAcross = if (along) ph * z > h + 1 else pw * z > w + 1
                        val acrossMove = if (along) abs(moved.y) > abs(moved.x) else abs(moved.x) > abs(moved.y)
                        if (!(z > 1.01f || bigAlong || (bigAcross && acrossMove))) {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.count { it.pressed } >= 2) { pinch(e); return }
                                if (e.changes.none { it.pressed }) return
                            }
                        }
                        // Pulling the pager along only where the page can be moved the way pages turn, or the move began that way.
                        val canTurnHere = z > 1.01f || bigAlong || !acrossMove
                        val speed = VelocityTracker()
                        var pulled = 0f
                        val most = if (along) w else h
                        var e = first
                        while (true) {
                            if (e.changes.count { it.pressed } >= 2) {
                                if (pulled != 0f) letGoNow(0f)
                                pinch(e)
                                return
                            }
                            val finger = e.changes.firstOrNull { it.id == down.id && it.pressed } ?: e.changes.firstOrNull { it.pressed }
                            if (finger == null) break
                            speed.addPosition(finger.uptimeMillis, finger.position)
                            val pan = e.calculatePan()
                            var a = if (along) pan.x else pan.y
                            val across = if (along) pan.y else pan.x
                            // The pager already pulled along: moving back gives that back first.
                            if (pulled != 0f && a != 0f) {
                                val p = pulled + a
                                if (p == 0f || (p > 0) == (pulled > 0)) {
                                    val to = p.coerceIn(-most, most)
                                    pullNow(to - pulled)
                                    pulled = to
                                    a = 0f
                                } else {
                                    pullNow(-pulled)
                                    pulled = 0f
                                    a = p
                                }
                            }
                            val o = offset
                            val wanted = if (along) Offset(o.x + a, o.y + across) else Offset(o.x + across, o.y + a)
                            val kept = held(wanted, zoom)
                            offset = kept
                            val left = if (along) wanted.x - kept.x else wanted.y - kept.y
                            if (canTurnHere && left != 0f && (pulled != 0f || canPullNow(left))) {
                                val to = (pulled + left).coerceIn(-most, most)
                                pullNow(to - pulled)
                                pulled = to
                            }
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                            e = awaitPointerEvent()
                        }
                        if (pulled != 0f) {
                            val v = speed.calculateVelocity()
                            letGoNow(if (along) v.x else v.y)
                        }
                    }

                    // The second of two taps: let go, it zooms in on the spot (brought to the middle, as in Mihon) or out
                    // again; held and moved up or down, it zooms with the finger, about that spot.
                    suspend fun AwaitPointerEventScope.secondTap(second: PointerInputChange) {
                        val f = second.position
                        val z0 = zoom
                        val o0 = offset
                        var quick = false
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.count { it.pressed } >= 2) { pinch(e); return }
                            val c = e.changes.firstOrNull { it.id == second.id }
                            if (c == null || !c.pressed) break
                            if (!quick && (c.position - f).getDistance() > slop) quick = true
                            if (quick) {
                                val z1 = (z0 * exp((c.position.y - f.y) / quickZoom)).coerceIn(minZoom, maxZoom)
                                offset = held(f - (f - o0) * (z1 / z0), z1)
                                zoom = z1
                            }
                            e.changes.forEach { it.consume() }
                        }
                        if (quick) return
                        val z = zoom
                        val spot = (f - offset) / z
                        val middle = Offset(w / 2, h / 2)
                        // In to twice the size unless it's most of the way there already; then back out.
                        if (z <= 1.8f) {
                            val z1 = min(maxZoom, 2f)
                            animateTo(z1, middle - spot * z1)
                        } else {
                            animateTo(1f, middle - spot)
                        }
                    }

                    suspend fun AwaitPointerEventScope.touch(down: PointerInputChange) {
                        moving?.cancel()
                        val st = sNow
                        val (start, first) = howItStarts(down, holds = st.longTap)
                        when (start) {
                            Start.GONE -> Unit
                            Start.HOLD -> {
                                longPressNow()
                                waitForAllUp()
                            }
                            Start.PINCH -> pinch(first!!)
                            Start.DRAG -> drag(down, first!!)
                            Start.TAP -> {
                                val up = first!!.changes.first { it.id == down.id }.position
                                val action = ComicLayout.tapAction(up.x / size.width, up.y / size.height, st.taps(mode), mode, st.invertTaps(mode))
                                // As in Mihon, a tap waits a moment in case a second follows: two zoom.
                                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
                                when {
                                    second == null -> if (action != 0) step(action) else menuNow()
                                    (second.position - up).getDistance() > doubleTapSlop -> {
                                        if (action != 0) step(action) else menuNow()
                                        touch(second)
                                    }
                                    else -> secondTap(second)
                                }
                            }
                        }
                    }

                    awaitEachGesture { touch(awaitFirstDown(requireUnconsumed = false)) }
                },
        ) {
            // The page as laid out before zooming, made bigger and moved where it's shown (drawn, not laid out again).
            val iw = pw.toInt()
            val ih = ph.toInt()
            Spacer(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(iw, ih))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeWithLayer(0, 0) {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            }
                        }
                    }
                    .drawBehind {
                        val b = base ?: return@drawBehind
                        paint.colorFilter = filterNow
                        drawIntoCanvas { canvas ->
                            val c = canvas.nativeCanvas
                            if (rotated) {
                                // Drawn as it is, then turned a quarter clockwise about the page's middle.
                                c.save()
                                c.rotate(if (sNow.invertRotate) -90f else 90f, size.width / 2, size.height / 2)
                                val l = (size.width - size.height) / 2
                                val t = (size.height - size.width) / 2
                                c.drawBitmap(b, null, RectF(l, t, l + size.height, t + size.width), paint)
                                c.restore()
                            } else {
                                c.drawBitmap(b, null, RectF(0f, 0f, size.width, size.height), paint)
                                sharp?.let { (where, bmp) -> c.drawBitmap(bmp, null, where, paint) }
                            }
                        }
                    },
            )
        }
    }
}

// ---- the long strip -----------------------------------------------------------------------------------

/**
 * One stretch of the long strip: a page, part of a very tall one (drawn in parts so none is huge), half of a wide
 * one (split), or a wide one [turned] on its side (90 or -90 degrees).
 */
private data class StripPiece(val page: Int, val part: Int, val parts: Int, val region: android.graphics.Rect?, val turned: Int = 0) {
    val key: String get() = "$page:$part"
}

/** A comic's pages as the strip shows them, for the parts of them known so far ([regions] by page). */
private fun stripPieces(c: OpenComic, regions: Map<String, android.graphics.Rect>, s: ComicSettings): List<StripPiece> = buildList {
    for (i in 0 until c.pages.count) {
        val r = regions["${c.id}:$i"]
        when {
            r == null -> add(StripPiece(i, 0, 1, null))
            r.width() > r.height() && s.rotateWideStrip -> add(StripPiece(i, 0, 1, r, turned = if (s.invertRotateStrip) -90 else 90))
            r.width() > r.height() && s.splitWideStrip -> {
                // As in Mihon, the right half above the left, unless that's inverted.
                val left = android.graphics.Rect(r.left, r.top, r.left + r.width() / 2, r.bottom)
                val right = android.graphics.Rect(r.left + r.width() / 2, r.top, r.right, r.bottom)
                add(StripPiece(i, 0, 2, if (s.invertSplitStrip) left else right))
                add(StripPiece(i, 1, 2, if (s.invertSplitStrip) right else left))
            }
            else -> {
                // Parts no taller than twice the strip's width.
                val n = ceil(r.height().toFloat() / (r.width() * 2f)).toInt().coerceAtLeast(1)
                for (k in 0 until n) {
                    val top = r.top + r.height() * k / n
                    val bottom = r.top + r.height() * (k + 1) / n
                    add(StripPiece(i, k, n, android.graphics.Rect(r.left, top, r.right, bottom)))
                }
            }
        }
    }
}

/**
 * The pages one under another, scrolled through; zoomed as in Mihon, about the screen's middle: pinched, double-tapped
 * (twice the size about the spot, or back), or double-tapped and held then moved up or down. Zoomed in, a finger
 * moves the strip sideways too, and at either end down or up to its very edge. The comics either side in the series
 * run on from it, with the page between them or without.
 */
@Composable
private fun StripComic(
    current: OpenComic,
    before: Neighbour?,
    after: Neighbour?,
    s: ComicSettings,
    mode: ComicMode,
    startPage: Int,
    series: String?,
    ink: Color,
    matrix: FloatArray?,
    onAt: (ReadItem<*>) -> Unit,
    /** The page between comics come into view. */
    onGapShown: (Between) -> Unit,
    onMenu: () -> Unit,
    onLongPress: (OpenComic, Int) -> Unit,
    /** The strip was scrolled. */
    onMoved: () -> Unit,
    onClose: () -> Unit,
    nudges: SharedFlow<Int>,
    jumps: SharedFlow<Int>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Each page's part to show (cropped, if asked for), of each comic, as it's read.
    val regions = remember(s.cropStrip) { mutableStateMapOf<String, android.graphics.Rect>() }
    // Read here, outside the box that measures the screen, so the strip is laid out again as pages become known.
    val known = regions.toMap()
    val sNow by rememberUpdatedState(s)
    val menuNow by rememberUpdatedState(onMenu)
    val longPressNow by rememberUpdatedState(onLongPress)
    val movedNow by rememberUpdatedState(onMoved)
    val onAtNow by rememberUpdatedState(onAt)
    val onGapShownNow by rememberUpdatedState(onGapShown)
    val currentNow by rememberUpdatedState(current)
    val filter = remember(matrix) { matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    // A page between comics on the screen: they're kept apart while it is, even where they'd be joined.
    var gapShowing by remember { mutableStateOf(false) }
    val joined = !s.alwaysTransition && !gapShowing
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val column = w * (1 - 2 * s.sidePadding / 100f)
        val gap = if (mode == ComicMode.STRIP_GAPS) with(density) { 16.dp.toPx() } else 0f
        val items = remember(current, before?.comic, after?.comic, before?.id, after?.id, before?.failed, after?.failed, joined, known, s.splitWideStrip, s.invertSplitStrip, s.rotateWideStrip, s.invertRotateStrip) {
            readItems(current, before, after, joined, pieces = { c -> stripPieces(c, known, s) }, keyOf = { it.key }, pageOf = { it.page })
        }
        val itemsNow by rememberUpdatedState(items)
        val startIndex = remember { items.indexOfFirst { it is ReadItem.Page && it.comic.id == current.id && it.page == startPage }.coerceAtLeast(0) }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
        // The strip stays where it is as pages come and go before it, as the pager does.
        val lastItems = remember { arrayOfNulls<List<ReadItem<*>>>(1) }
        SideEffect {
            val was = lastItems[0]
            lastItems[0] = items
            if (was != null && was !== items) movedTo(was, items, list.firstVisibleItemIndex)?.let { list.requestScrollToItem(it, list.firstVisibleItemScrollOffset) }
        }
        val firstKey by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull()?.key } }
        // The pages near the screen read first, so the strip knows their shapes before they come into view: the
        // comics either side's too, where they meet.
        LaunchedEffect(firstKey, s.cropStrip) {
            val all = itemsNow
            val at = all.indexOfFirst { it.key == firstKey }.takeIf { it >= 0 } ?: startIndex
            val near = ((at - 4)..(at + 8)).mapNotNull { all.getOrNull(it) as? ReadItem.Page }.distinctBy { "${it.comic.id}:${it.page}" }
            for (item in near.sortedBy { abs(all.indexOf(it) - at) }) {
                val k = "${item.comic.id}:${item.page}"
                if (regions[k] != null) continue
                val size = item.comic.pages.size(item.page) ?: continue
                val whole = android.graphics.Rect(0, 0, size.width, size.height)
                regions[k] = if (sNow.cropStrip) item.comic.pages.crop(item.page) ?: whole else whole
            }
        }
        // The page at the top of the screen, known by its key in what's laid out.
        LaunchedEffect(list) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.firstOrNull()?.key }.filterNotNull().distinctUntilChanged().collect { k ->
                itemsNow.firstOrNull { it.key == k }?.let { onAtNow(it) }
            }
        }
        LaunchedEffect(list) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { (it.key as? String)?.takeIf { k -> k.startsWith("between:") } } }
                .distinctUntilChanged().collect { keys ->
                    gapShowing = keys.isNotEmpty()
                    for (k in keys) (itemsNow.firstOrNull { it.key == k } as? ReadItem.Gap)?.let { onGapShownNow(it.between) }
                }
        }
        LaunchedEffect(list) {
            jumps.collect { p ->
                val i = itemsNow.indexOfFirst { it is ReadItem.Page && it.comic.id == currentNow.id && it.page == p }
                if (i >= 0) list.scrollToItem(i)
            }
        }
        // As in Mihon, the menu goes once the strip moves.
        LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { if (it) movedNow() } }

        // How much bigger the strip is shown, and how far it's moved from the middle of the screen.
        var zoom by remember { mutableFloatStateOf(1f) }
        var shift by remember { mutableStateOf(Offset.Zero) }
        val middle = Offset(w / 2, h / 2)
        // Moved no further than its edges: sideways where it's wider than the screen, and down or up as far as the
        // part of the list on the screen goes.
        fun heldShift(o: Offset, z: Float): Offset {
            val sx = max(0f, (column * z - w) / 2)
            val sy = max(0f, (z - 1) * h / 2)
            return Offset(o.x.coerceIn(-sx, sx), o.y.coerceIn(-sy, sy))
        }
        /** Zooms to [z1], what was at [from] on the screen going to [to]: by moving the strip, and where that's not enough, scrolling it. */
        fun zoomAbout(from: Offset, to: Offset, z1: Float) {
            val z0 = zoom
            val local = (from - middle - shift) / z0
            val wanted = to - middle - local * z1
            val kept = heldShift(wanted, z1)
            zoom = z1
            shift = kept
            val over = wanted.y - kept.y
            if (abs(over) > 0.5f) list.dispatchRawDelta(-over / z1)
        }
        var moving by remember { mutableStateOf<Job?>(null) }
        fun animateZoom(about: Offset, z1: Float) {
            moving?.cancel()
            moving = scope.launch {
                animate(zoom, z1, animationSpec = tween(if (sNow.animate) 220 else 1)) { z, _ -> zoomAbout(about, about, z) }
            }
        }
        /** On (1) or back (-1) by most of the screen; zoomed in at either end, the strip is moved to its very edge. */
        fun scrollStrip(by: Int) {
            if (by == 0) return
            moving?.cancel()
            moving = scope.launch {
                val atEnd = if (by > 0) !list.canScrollForward else !list.canScrollBackward
                if (atEnd && zoom > 1.01f) {
                    val to = heldShift(Offset(shift.x, if (by > 0) -h * 10 else h * 10), zoom)
                    val from = shift
                    animate(0f, 1f, animationSpec = tween(if (sNow.animate) 220 else 1)) { t, _ -> shift = from + (to - from) * t }
                } else if (!atEnd) {
                    val d = by * h * 0.75f / zoom
                    if (sNow.animate) list.animateScrollBy(d) else list.scrollBy(d)
                }
            }
        }
        LaunchedEffect(list, w, h, column) { nudges.collect { scrollStrip(it) } }
        // At either end of the strip, zoomed in, the rest of a drag moves the strip within the screen, so its very
        // top and bottom can be seen.
        val ends = remember(w, h, column) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (zoom <= 1.001f || available.y == 0f) return Offset.Zero
                    val before = shift
                    shift = heldShift(Offset(before.x, before.y + available.y * zoom), zoom)
                    return Offset(0f, (shift.y - before.y) / zoom)
                }
            }
        }
        val quickZoom = with(density) { QUICK_ZOOM_DISTANCE.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(ends)
                // Seen before the list sees them, so two fingers zoom without the list scrolling too.
                .pointerInput(w, h, column) {
                    val slop = viewConfiguration.touchSlop
                    val doubleTapSlop = 100.dp.toPx()
                    val pass = PointerEventPass.Initial

                    suspend fun AwaitPointerEventScope.pinch(first: PointerEvent) {
                        var e = first
                        while (true) {
                            val by = e.calculateZoom()
                            val pan = e.calculatePan()
                            if (by != 1f || pan != Offset.Zero) {
                                val c = e.calculateCentroid(useCurrent = true)
                                if (c.isSpecified) zoomAbout(c - pan, c, (zoom * by).coerceIn(1f, 3f))
                            }
                            e.changes.forEach { it.consume() }
                            if (e.changes.none { it.pressed }) return
                            e = awaitPointerEvent(pass)
                        }
                    }

                    // One finger: the list scrolls itself; zoomed in, the strip moves sideways here too.
                    suspend fun AwaitPointerEventScope.drag(first: PointerEvent) {
                        var e = first
                        while (true) {
                            if (e.changes.count { it.pressed } >= 2) { pinch(e); return }
                            if (e.changes.none { it.pressed }) return
                            val pan = e.calculatePan()
                            if (pan.x != 0f && zoom > 1.001f) shift = heldShift(Offset(shift.x + pan.x, shift.y), zoom)
                            e = awaitPointerEvent(pass)
                        }
                    }

                    suspend fun AwaitPointerEventScope.secondTap(second: PointerInputChange) {
                        val f = second.position
                        val z0 = zoom
                        var quick = false
                        while (true) {
                            val e = awaitPointerEvent(pass)
                            if (e.changes.count { it.pressed } >= 2) { pinch(e); return }
                            val c = e.changes.firstOrNull { it.id == second.id }
                            if (c == null || !c.pressed) break
                            if (!quick && (c.position - f).getDistance() > slop) quick = true
                            if (quick) zoomAbout(f, f, (z0 * exp((c.position.y - f.y) / quickZoom)).coerceIn(1f, 3f))
                            e.changes.forEach { it.consume() }
                        }
                        // Two taps: twice the size about where they were (as in Mihon), or back again.
                        if (!quick) animateZoom(f, if (zoom > 1.01f) 1f else 2f)
                    }

                    suspend fun AwaitPointerEventScope.touch(down: PointerInputChange) {
                        moving?.cancel()
                        val st = sNow
                        val (start, first) = howItStarts(down, holds = st.longTap, pass = pass)
                        when (start) {
                            Start.GONE -> Unit
                            Start.HOLD -> {
                                // The page held, found where it's drawn, zoomed or not.
                                val at = middle + (down.position - middle - shift) / zoom
                                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { at.y >= it.offset && at.y < it.offset + it.size }
                                (itemsNow.getOrNull(item?.index ?: list.firstVisibleItemIndex) as? ReadItem.Page)?.let { longPressNow(it.comic, it.page) }
                                waitForAllUp(pass)
                            }
                            Start.PINCH -> pinch(first!!)
                            Start.DRAG -> drag(first!!)
                            Start.TAP -> {
                                val up = first!!.changes.first { it.id == down.id }.position
                                val action = ComicLayout.tapAction(up.x / size.width, up.y / size.height, st.taps(mode), mode, st.invertTaps(mode))
                                val second = if (!st.doubleTapZoom) null else withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                    awaitFirstDown(requireUnconsumed = false, pass = pass)
                                }
                                when {
                                    second == null -> if (action != 0) scrollStrip(action) else menuNow()
                                    (second.position - up).getDistance() > doubleTapSlop -> {
                                        if (action != 0) scrollStrip(action) else menuNow()
                                        touch(second)
                                    }
                                    else -> secondTap(second)
                                }
                            }
                        }
                    }

                    awaitEachGesture { touch(awaitFirstDown(requireUnconsumed = false, pass = pass)) }
                },
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = shift.x
                    translationY = shift.y
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(items.size, key = { items[it].key }) { idx ->
                    when (val item = items[idx]) {
                        is ReadItem.Page -> StripPage(item, column, gap, filter)
                        is ReadItem.Gap -> Box(
                            Modifier.width(with(density) { w.toDp() }).height(with(density) { (h * 0.6f).toDp() }).testTag("comic-between"),
                            contentAlignment = Alignment.Center,
                        ) { BetweenText(item.between, current.id, series, ink, onClose) }
                    }
                }
            }
        }
    }
}

/** One stretch of the strip, drawn as it's read: [column] wide, with [gap] under a page's last part. */
@Composable
private fun StripPage(item: ReadItem.Page<StripPiece>, column: Float, gap: Float, filter: ColorFilter?) {
    val density = LocalDensity.current
    val piece = item.piece
    val r = piece.region
    val shownH = when {
        r == null -> column * 1.45f
        piece.turned != 0 -> column * r.width() / r.height()
        else -> column * r.height() / r.width()
    }
    val after = if (piece.part == piece.parts - 1) gap else 0f
    Box(
        Modifier.width(with(density) { column.toDp() }).height(with(density) { (shownH + after).toDp() }),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (r == null) {
            Box(Modifier.fillMaxWidth().height(with(density) { shownH.toDp() }), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            val bmp by produceState<Bitmap?>(null, item.key, r, column, piece.turned) {
                // A page turned on its side is read as it is, its width down the strip.
                value = if (piece.turned != 0) {
                    item.comic.pages.bitmap(item.page, r, shownH.toInt().coerceAtLeast(1), column.toInt().coerceAtLeast(1))
                } else {
                    item.comic.pages.bitmap(item.page, r, column.toInt().coerceAtLeast(1), shownH.toInt().coerceAtLeast(1))
                }
            }
            val picture = remember(bmp) { bmp?.asImageBitmap() }
            if (picture != null) {
                if (piece.turned == 0) {
                    Image(
                        picture, null, contentScale = ContentScale.FillBounds, colorFilter = filter,
                        modifier = Modifier.fillMaxWidth().height(with(density) { shownH.toDp() }),
                    )
                } else {
                    Spacer(
                        Modifier.fillMaxWidth().height(with(density) { shownH.toDp() }).drawBehind {
                            // Drawn as it is, then turned a quarter about the middle.
                            rotate(piece.turned.toFloat()) {
                                drawImage(
                                    picture,
                                    dstOffset = IntOffset(((size.width - size.height) / 2).roundToInt(), ((size.height - size.width) / 2).roundToInt()),
                                    dstSize = IntSize(size.height.roundToInt(), size.width.roundToInt()),
                                    colorFilter = filter,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
