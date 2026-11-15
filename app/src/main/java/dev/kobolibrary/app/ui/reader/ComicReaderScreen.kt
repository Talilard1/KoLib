package dev.kobolibrary.app.ui.reader

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.comic.ComicArchives
import dev.kobolibrary.app.comic.ComicBackground
import dev.kobolibrary.app.comic.ComicInfo
import dev.kobolibrary.app.comic.ComicInvert
import dev.kobolibrary.app.comic.ComicLayout
import dev.kobolibrary.app.comic.ComicMode
import dev.kobolibrary.app.comic.ComicPages
import dev.kobolibrary.app.comic.ComicScale
import dev.kobolibrary.app.comic.ComicSettings
import dev.kobolibrary.app.comic.ComicSettingsStore
import dev.kobolibrary.app.comic.ComicTaps
import dev.kobolibrary.app.comic.ComicZoomStart
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.reader.PdfPlace
import dev.kobolibrary.app.reader.ReaderKeys
import dev.kobolibrary.app.reader.ReaderRotation
import dev.kobolibrary.app.reader.ReaderSettingsStore
import dev.kobolibrary.app.reader.ReaderStore
import dev.kobolibrary.app.reader.ReadingHereIndex
import dev.kobolibrary.app.tr
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads a comic (a CBZ, CBR or CB7 kept in the library) the way Mihon does: page by page left to right, right to
 * left or top to bottom, or as one long strip, with Mihon's ways of fitting pages to the screen, cropping their
 * borders, splitting wide pages and tapping to turn. The pages are read from the comic file as they're needed.
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
) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember { ComicSettingsStore.get(context) }
    val s by store.settings.collectAsStateWithLifecycle()
    val rs by readerSettings.settings.collectAsStateWithLifecycle()
    val dir = remember(book.id) { library.bookDir(book.id) }
    var pages by remember(book.id) { mutableStateOf<ComicPages?>(null) }
    var rtlByBook by remember(book.id) { mutableStateOf(false) }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(book.id) {
        var opened: ComicPages? = null
        val job = scope.launch {
            try {
                val file = library.sourceFile(book.id) ?: throw java.io.IOException(tr("The comic isn't in the library any more."))
                val p = withContext(Dispatchers.IO) { ComicPages(ComicArchives.open(file)) }
                if (p.count == 0) {
                    p.close()
                    error = tr("There are no pictures in this comic book file.")
                    return@launch
                }
                rtlByBook = withContext(Dispatchers.IO) { ComicInfo.read(p.archive.comicInfo, file.name).rightToLeft }
                opened = p
                pages = p
            } catch (e: Exception) {
                AppLog.e("Comic", "The comic couldn't be opened", e)
                error = tr("This comic couldn't be opened. The file may be damaged, or locked with a password.")
            }
        }
        onDispose {
            job.cancel()
            pages = null
            opened?.let { p -> Thread { p.close() }.start() }
        }
    }
    // The whole screen, as in Mihon.
    var menu by remember { mutableStateOf(false) }
    DisposableEffect(menu) {
        val window = (context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (menu) insets?.show(WindowInsetsCompat.Type.systemBars()) else insets?.hide(WindowInsetsCompat.Type.systemBars())
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
    DisposableEffect(rs.rotation) {
        val activity = context as? Activity
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = when (rs.rotation) {
            ReaderRotation.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderRotation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ReaderRotation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    DisposableEffect(rs.brightness) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = if (rs.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else rs.brightness } }
        onDispose { window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } } }
    }
    val p = pages
    when {
        p != null -> {
            var bookMode by remember(book.id) { mutableStateOf(ComicSettingsStore.modeOf(dir)) }
            val mode = bookMode ?: if (rtlByBook) ComicMode.RIGHT_TO_LEFT else s.mode
            ComicView(
                book = book, pages = p, dir = dir, s = s, mode = mode, menu = menu, setMenu = { menu = it },
                onMode = { m ->
                    bookMode = m
                    ComicSettingsStore.saveMode(dir, m)
                },
                onSettings = { change -> store.update(change) },
                onClose = onClose,
                onCover = { bytes ->
                    scope.launch {
                        val ok = runCatching {
                            val jpeg = withContext(Dispatchers.IO) { dev.kobolibrary.app.convert.ComicConverter.coverJpeg(bytes) } ?: error("unreadable")
                            // The cover's own size, as made (no bigger than the library needs).
                            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
                            library.updateCover(book.id, jpeg, "image/jpeg", o.outWidth, o.outHeight)
                        }.isSuccess
                        Toast.makeText(context, if (ok) tr("That page is the cover now.") else tr("That page couldn't be made the cover."), Toast.LENGTH_SHORT).show()
                    }
                },
                readingHere = readingHere,
                onAppSettings = onAppSettings,
            )
        }
        error != null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error!!, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text(tr("Back")) }
            }
        }
        else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** A comic open in the reader: its pages in the reading mode chosen, the menu over them, the settings. */
@OptIn(ExperimentalMaterial3Api::class)
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
    /** Opens the app's own settings: its look, language and books folder. */
    onAppSettings: (() -> Unit)? = null,
    /** For pictures of the screen in tests: the settings open. */
    settingsOpen: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val count = pages.count
    val saved = remember { ReaderStore.pdfPlace(dir) }
    var page by remember { mutableIntStateOf(saved?.page?.coerceIn(0, count - 1) ?: 0) }
    var showSettings by remember { mutableStateOf(settingsOpen) }
    var actionsFor by remember { mutableStateOf<Int?>(null) }
    // Taps, keys and the bar's buttons: on or back, for the page or strip showing to act on; and pages to go to.
    val nudges = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }
    val jumps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    // The menu, shown and hidden by taps on the page. Those are handled for as long as the page shows, so they
    // read whether it's showing as it is at the time.
    val menuNow by rememberUpdatedState(menu)
    val setMenuNow by rememberUpdatedState(setMenu)
    val toggleMenu = remember { { setMenuNow(!menuNow) } }
    // As in Mihon, the menu goes when the page is turned or the strip scrolled, other than from the menu itself.
    var menuMovedAt by remember { mutableLongStateOf(-1_000_000L) }
    val fromMenu = { menuMovedAt = SystemClock.uptimeMillis() }
    val moved = remember { { if (menuNow && SystemClock.uptimeMillis() - menuMovedAt > 1500) setMenuNow(false) } }
    LaunchedEffect(mode.paged) { if (mode.paged) snapshotFlow { page }.drop(1).collect { moved() } }
    LaunchedEffect(page) {
        val place = PdfPlace(page, count)
        withContext(Dispatchers.IO) { ReaderStore.savePdfPlace(dir, place) }
        readingHere?.set(book.id, place.readHere)
    }
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
    BackHandler {
        when {
            showSettings -> showSettings = false
            actionsFor != null -> actionsFor = null
            menu -> setMenu(false)
            else -> onClose()
        }
    }
    val tone by produceState(Color.Black, s.background, page) {
        value = when (s.background) {
            ComicBackground.BLACK -> Color.Black
            ComicBackground.GRAY -> Color(0xFF3A3A3A)
            ComicBackground.WHITE -> Color.White
            ComicBackground.AUTO -> Color(pages.tone(page))
        }
    }
    Box(Modifier.fillMaxSize().background(tone)) {
        val longPress = { i: Int -> if (s.longTap) actionsFor = i }
        if (mode.paged) {
            PagedComic(pages, s, mode, page, onPage = { page = it }, onMenu = toggleMenu, onLongPress = longPress, nudges = nudges, jumps = jumps)
        } else {
            StripComic(pages, s, mode, page, onPage = { page = it }, onMenu = toggleMenu, onLongPress = longPress, onMoved = moved, nudges = nudges, jumps = jumps)
        }
        // Where the taps go, shown for a moment as the comic opens, if asked for.
        var zones by remember(mode) { mutableStateOf(s.showTapZones) }
        LaunchedEffect(zones) { if (zones) { delay(1800); zones = false } }
        if (zones) TapZonesOverlay(s, mode)
        if (s.pageNumber && !menu) {
            Text(
                "${page + 1} / $count",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        AnimatedVisibility(menu, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }, modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book")) }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr("Page {0} of {1}", page + 1, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, tr("Comic settings")) }
                }
            }
        }
        AnimatedVisibility(menu, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val rtl = mode == ComicMode.RIGHT_TO_LEFT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { fromMenu(); nudges.tryEmit(if (rtl) 1 else -1) }) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, if (rtl) tr("Next page") else tr("Previous page")) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (count > 1) {
                                // Right to left, the bar goes that way too, as the pages do.
                                val v = dragging ?: page.toFloat()
                                Slider(
                                    value = if (rtl) (count - 1) - v else v,
                                    onValueChange = { x -> dragging = if (rtl) (count - 1) - x else x },
                                    onValueChangeFinished = {
                                        fromMenu()
                                        dragging?.let { d -> jumps.tryEmit(d.roundToInt().coerceIn(0, count - 1)) }
                                        dragging = null
                                    },
                                    valueRange = 0f..(count - 1).toFloat(),
                                )
                            }
                            dragging?.let { d -> Text(tr("Page {0}", d.roundToInt() + 1), style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { fromMenu(); nudges.tryEmit(if (rtl) -1 else 1) }) { Icon(Icons.AutoMirrored.Filled.NavigateNext, if (rtl) tr("Previous page") else tr("Next page")) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        var modes by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { modes = true }) { Icon(Icons.Outlined.ChromeReaderMode, tr("Reading mode")) }
                            DropdownMenu(modes, onDismissRequest = { modes = false }) {
                                ComicMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label()) },
                                        leadingIcon = { RadioButton(selected = m == mode, onClick = null) },
                                        onClick = { modes = false; onMode(m) },
                                    )
                                }
                            }
                        }
                        val cropping = if (mode.paged) s.cropPaged else s.cropStrip
                        IconButton(onClick = { onSettings { if (mode.paged) it.copy(cropPaged = !it.cropPaged) else it.copy(cropStrip = !it.cropStrip) } }) {
                            Icon(Icons.Outlined.Crop, if (cropping) tr("Stop cropping borders") else tr("Crop borders"), tint = if (cropping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, tr("Comic settings")) }
                    }
                }
            }
        }
        if (showSettings) {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = sheet) {
                ComicSettingsSheet(
                    s, mode, onMode = onMode, onSettings = onSettings,
                    onAppSettings = onAppSettings?.let { open -> { showSettings = false; open() } },
                )
            }
        }
        actionsFor?.let { i ->
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { actionsFor = null }, sheetState = sheet) {
                Text(tr("Page {0}", i + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                ListItem(
                    headlineContent = { Text(tr("Use as the cover")) },
                    leadingContent = { Icon(Icons.Outlined.Image, null) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        scope.launch { runCatching { pages.file(i) }.getOrNull()?.let(onCover) }
                    },
                )
                ListItem(
                    headlineContent = { Text(tr("Share")) },
                    leadingContent = { Icon(Icons.Outlined.Share, null) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        scope.launch { sharePage(context, pages, i, book.title) }
                    },
                )
                ListItem(
                    headlineContent = { Text(tr("Save to Pictures")) },
                    leadingContent = { Icon(Icons.Outlined.Save, null) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        scope.launch { savePage(context, pages, i, book.title) }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
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
    Toast.makeText(context, if (ok) tr("Saved in Pictures, in the KoLib folder.") else tr("The page couldn't be saved."), Toast.LENGTH_SHORT).show()
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
                    val a = ComicLayout.tapAction((cx + 0.5f) / 3, (cy + 0.5f) / 3, s.taps, mode, s.invertTaps)
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
 */
private class PagerPull(private val pager: PagerState, private val scope: CoroutineScope) {
    private var deltas: Channel<Float>? = null
    private var job: Job? = null
    private var from = 0

    val pulling get() = deltas != null

    /** Moves the pager on (positive) or back (negative) by [by] pixels. */
    fun move(by: Float) {
        val ch = deltas ?: Channel<Float>(Channel.UNLIMITED).also { c ->
            deltas = c
            from = pager.settledPage
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
            val moved = pager.currentPage + pager.currentPageOffsetFraction - from
            val target = when {
                moved > 0.14f || (moved > 0.02f && speed > flingSpeed) -> from + 1
                moved < -0.14f || (moved < -0.02f && speed < -flingSpeed) -> from - 1
                else -> from
            }
            pager.animateScrollToPage(target.coerceIn(0, last))
        }
    }
}

// ---- page by page ----------------------------------------------------------------------------------

@Composable
private fun PagedComic(
    pages: ComicPages,
    s: ComicSettings,
    mode: ComicMode,
    page: Int,
    onPage: (Int) -> Unit,
    onMenu: () -> Unit,
    onLongPress: (Int) -> Unit,
    nudges: SharedFlow<Int>,
    jumps: SharedFlow<Int>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = mode == ComicMode.RIGHT_TO_LEFT
    // Which pages are known to be wide (as each is read): with "Split wide pages", each is two.
    val wide = remember { mutableStateMapOf<Int, Boolean>() }
    val count = pages.count
    LaunchedEffect(page, s.splitWide) {
        if (!s.splitWide) return@LaunchedEffect
        for (i in (page - 2).coerceAtLeast(0)..(page + 4).coerceAtMost(count - 1)) {
            pages.size(i)?.let { if (it.wide && wide[i] != true) wide[i] = true }
        }
    }
    val pieces = remember(count, s.splitWide, rtl, s.invertSplit, wide.toMap()) {
        ComicLayout.pieces(count, { wide[it] == true }, s.splitWide, rtl, s.invertSplit)
    }
    val pageNow by rememberUpdatedState(page)
    key(pieces) {
        val pager = rememberPagerState(initialPage = pieces.indexOfFirst { it.page == pageNow }.coerceAtLeast(0)) { pieces.size }
        var locked by remember { mutableStateOf(false) }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { sc -> pieces.getOrNull(sc)?.let { onPage(it.page) } }
        }
        fun turn(by: Int) {
            val to = (pager.currentPage + by).coerceIn(0, pieces.size - 1)
            if (to == pager.currentPage) return
            scope.launch { if (s.animate) pager.animateScrollToPage(to) else pager.scrollToPage(to) }
        }
        LaunchedEffect(pager) {
            jumps.collect { p -> pieces.indexOfFirst { it.page == p }.takeIf { it >= 0 }?.let { pager.scrollToPage(it) } }
        }
        // A key or button press goes to the page showing, which moves along itself first if it's bigger than the screen.
        val toPage = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }
        LaunchedEffect(Unit) { nudges.collect { toPage.emit(it) } }
        // A zoomed page pulled on past its edge moves the pager with the finger. A finger moving this way
        // (to the left, up, or to the right reading right to left) brings the next page on.
        val pull = remember(pager) { PagerPull(pager, scope) }
        val onward = if (!mode.horizontal || !rtl) -1f else 1f
        val flingSpeed = with(density) { 400.dp.toPx() }
        val content: @Composable (Int) -> Unit = { sc ->
            val piece = pieces[sc]
            ComicPage(
                piece = piece,
                pages = pages,
                s = s,
                mode = mode,
                current = pager.settledPage == sc,
                onLocked = { if (pager.settledPage == sc) locked = it },
                onTurn = ::turn,
                canPull = { finger -> if (finger * onward > 0) pager.canScrollForward else pager.canScrollBackward },
                onPull = { finger -> pull.move(finger * onward) },
                onLetGo = { speed -> pull.letGo(speed * onward, flingSpeed, pieces.size - 1) },
                onMenu = onMenu,
                onLongPress = { onLongPress(piece.page) },
                nudges = if (pager.settledPage == sc) toPage else null,
            )
        }
        if (mode.horizontal) {
            HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = rtl, userScrollEnabled = !locked, beyondViewportPageCount = 1, key = { pieces[it].key }) { content(it) }
        } else {
            VerticalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !locked, beyondViewportPageCount = 1, key = { pieces[it].key }) { content(it) }
        }
    }
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
                                val action = ComicLayout.tapAction(up.x / size.width, up.y / size.height, st.taps, mode, st.invertTaps)
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
                        drawIntoCanvas { canvas ->
                            val c = canvas.nativeCanvas
                            if (rotated) {
                                // Drawn as it is, then turned a quarter clockwise about the page's middle.
                                c.save()
                                c.rotate(90f, size.width / 2, size.height / 2)
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

/** One stretch of the long strip: a page, or part of a very tall one (drawn in parts so none is huge). */
private data class StripPiece(val page: Int, val part: Int, val parts: Int, val region: android.graphics.Rect?) {
    val key: String get() = "$page:$part"
}

/**
 * The pages one under another, scrolled through; zoomed as in Mihon, about the screen's middle: pinched, double-tapped
 * (twice the size about the spot, or back), or double-tapped and held then moved up or down. Zoomed in, a finger
 * moves the strip sideways too, and at either end down or up to its very edge.
 */
@Composable
private fun StripComic(
    pages: ComicPages,
    s: ComicSettings,
    mode: ComicMode,
    page: Int,
    onPage: (Int) -> Unit,
    onMenu: () -> Unit,
    onLongPress: (Int) -> Unit,
    /** The strip was scrolled. */
    onMoved: () -> Unit,
    nudges: SharedFlow<Int>,
    jumps: SharedFlow<Int>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val count = pages.count
    // Each page's part to show (cropped, if asked for), as it's read.
    val regions = remember(s.cropStrip) { mutableStateMapOf<Int, android.graphics.Rect>() }
    // Read here, outside the box that measures the screen, so the strip is laid out again as pages become known.
    val known = regions.toMap()
    val sNow by rememberUpdatedState(s)
    val menuNow by rememberUpdatedState(onMenu)
    val longPressNow by rememberUpdatedState(onLongPress)
    val movedNow by rememberUpdatedState(onMoved)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val column = w * (1 - 2 * s.sidePadding / 100f)
        val gap = if (mode == ComicMode.STRIP_GAPS) with(density) { 16.dp.toPx() } else 0f
        val pieces = remember(count, known, column) {
            buildList {
                for (i in 0 until count) {
                    val r = known[i]
                    if (r == null) add(StripPiece(i, 0, 1, null))
                    else {
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
        val piecesNow by rememberUpdatedState(pieces)
        val list = rememberLazyListState(initialFirstVisibleItemIndex = pieces.indexOfFirst { it.page == page }.coerceAtLeast(0))
        // The pages near the screen read first, so the strip knows their shapes before they come into view.
        val first by remember { derivedStateOf { list.firstVisibleItemIndex } }
        LaunchedEffect(first, s.cropStrip) {
            val at = pieces.getOrNull(first)?.page ?: page
            for (i in listOf(at) + ((at - 2).coerceAtLeast(0)..(at + 6).coerceAtMost(count - 1)).filter { it != at }) {
                if (regions[i] != null) continue
                val size = pages.size(i) ?: continue
                val whole = android.graphics.Rect(0, 0, size.width, size.height)
                regions[i] = if (s.cropStrip) pages.crop(i) ?: whole else whole
            }
        }
        LaunchedEffect(list, pieces) {
            snapshotFlow { list.firstVisibleItemIndex }.collect { idx -> pieces.getOrNull(idx)?.let { onPage(it.page) } }
        }
        LaunchedEffect(list, pieces) {
            jumps.collect { p -> pieces.indexOfFirst { it.page == p }.takeIf { it >= 0 }?.let { list.scrollToItem(it) } }
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
                } else {
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
                                piecesNow.getOrNull(item?.index ?: list.firstVisibleItemIndex)?.let { longPressNow(it.page) }
                                waitForAllUp(pass)
                            }
                            Start.PINCH -> pinch(first!!)
                            Start.DRAG -> drag(first!!)
                            Start.TAP -> {
                                val up = first!!.changes.first { it.id == down.id }.position
                                val action = ComicLayout.tapAction(up.x / size.width, up.y / size.height, st.taps, mode, st.invertTaps)
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
                items(pieces.size, key = { pieces[it].key }) { idx ->
                    val piece = pieces[idx]
                    val r = piece.region
                    val shownH = if (r == null) column * 1.45f else column * r.height() / r.width()
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
                            val bmp by produceState<Bitmap?>(null, piece.key, r, column) {
                                value = pages.bitmap(piece.page, r, column.toInt().coerceAtLeast(1), shownH.toInt().coerceAtLeast(1))
                            }
                            bmp?.let {
                                Image(it.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxWidth().height(with(density) { shownH.toDp() }))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- settings -----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComicSettingsSheet(
    s: ComicSettings,
    mode: ComicMode,
    onMode: (ComicMode?) -> Unit,
    onSettings: ((ComicSettings) -> ComicSettings) -> Unit,
    onAppSettings: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(tr("Comic settings"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        if (onAppSettings != null) AppSettingsLink(onAppSettings, Modifier.padding(bottom = 8.dp))
        Choices(tr("Reading mode for this comic"), ComicMode.entries, mode, { it.label() }) { onMode(it) }
        Choices(tr("Reading mode for other comics"), ComicMode.entries, s.mode, { it.label() }) { m -> onSettings { it.copy(mode = m) } }

        Section(tr("Pages one at a time"))
        Choices(tr("Fit"), ComicScale.entries, s.scale, { it.label() }) { v -> onSettings { it.copy(scale = v) } }
        Choices(tr("Where a page bigger than the screen starts"), ComicZoomStart.entries, s.zoomStart, { it.label() }) { v -> onSettings { it.copy(zoomStart = v) } }
        Toggle(tr("Crop borders"), s.cropPaged) { v -> onSettings { it.copy(cropPaged = v) } }
        Toggle(tr("Split wide pages in two"), s.splitWide) { v -> onSettings { it.copy(splitWide = v) } }
        if (s.splitWide) Toggle(tr("Other half first"), s.invertSplit) { v -> onSettings { it.copy(invertSplit = v) } }
        Toggle(tr("Turn wide pages on their side to fit"), s.rotateWide) { v -> onSettings { it.copy(rotateWide = v) } }
        Toggle(tr("Zoom into wide pages"), s.zoomWide, tr("On a screen held upright, a wide page fills its height and is moved along.")) { v -> onSettings { it.copy(zoomWide = v) } }
        Toggle(tr("Move along wide pages before turning"), s.panWide) { v -> onSettings { it.copy(panWide = v) } }

        Section(tr("Long strip"))
        Choices(tr("Space at the sides"), ComicSettings.SIDE_PADDINGS, s.sidePadding, { if (it == 0) tr("None") else "$it%" }) { v -> onSettings { it.copy(sidePadding = v) } }
        Toggle(tr("Crop borders"), s.cropStrip) { v -> onSettings { it.copy(cropStrip = v) } }
        Toggle(tr("Double-tap to zoom"), s.doubleTapZoom) { v -> onSettings { it.copy(doubleTapZoom = v) } }

        Section(tr("Taps and keys"))
        Choices(tr("Tap zones"), ComicTaps.entries, s.taps, { it.label() }) { v -> onSettings { it.copy(taps = v) } }
        Choices(tr("Swap tap zones"), ComicInvert.entries, s.invertTaps, { it.label() }) { v -> onSettings { it.copy(invertTaps = v) } }
        Toggle(tr("Show the tap zones when a comic opens"), s.showTapZones) { v -> onSettings { it.copy(showTapZones = v) } }
        Toggle(tr("Volume keys turn pages"), s.volumeKeys) { v -> onSettings { it.copy(volumeKeys = v) } }
        if (s.volumeKeys) Toggle(tr("Volume keys the other way round"), s.invertVolume) { v -> onSettings { it.copy(invertVolume = v) } }
        Toggle(tr("Hold a page for what you can do with it"), s.longTap) { v -> onSettings { it.copy(longTap = v) } }

        Section(tr("Screen"))
        Choices(tr("Background"), ComicBackground.entries, s.background, { it.label() }) { v -> onSettings { it.copy(background = v) } }
        Toggle(tr("Animate page turns"), s.animate) { v -> onSettings { it.copy(animate = v) } }
        Toggle(tr("Show the page number"), s.pageNumber) { v -> onSettings { it.copy(pageNumber = v) } }
        Toggle(tr("Keep the screen on"), s.keepScreenOn) { v -> onSettings { it.copy(keepScreenOn = v) } }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(label: String, options: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(name(o)) }) }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
