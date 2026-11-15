package dev.kobolibrary.app.ui.reader

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
        val toggleMenu = { setMenu(!menu) }
        val longPress = { i: Int -> if (s.longTap) actionsFor = i }
        if (mode.paged) {
            PagedComic(pages, s, mode, page, onPage = { page = it }, onMenu = toggleMenu, onLongPress = longPress, nudges = nudges, jumps = jumps)
        } else {
            StripComic(pages, s, mode, page, onPage = { page = it }, onMenu = toggleMenu, onLongPress = longPress, nudges = nudges, jumps = jumps)
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
                        IconButton(onClick = { nudges.tryEmit(if (rtl) 1 else -1) }) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, if (rtl) tr("Next page") else tr("Previous page")) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (count > 1) {
                                // Right to left, the bar goes that way too, as the pages do.
                                val v = dragging ?: page.toFloat()
                                Slider(
                                    value = if (rtl) (count - 1) - v else v,
                                    onValueChange = { x -> dragging = if (rtl) (count - 1) - x else x },
                                    onValueChangeFinished = {
                                        dragging?.let { d -> jumps.tryEmit(d.roundToInt().coerceIn(0, count - 1)) }
                                        dragging = null
                                    },
                                    valueRange = 0f..(count - 1).toFloat(),
                                )
                            }
                            dragging?.let { d -> Text(tr("Page {0}", d.roundToInt() + 1), style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { nudges.tryEmit(if (rtl) -1 else 1) }) { Icon(Icons.AutoMirrored.Filled.NavigateNext, if (rtl) tr("Previous page") else tr("Next page")) }
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
                ComicSettingsSheet(s, mode, onMode = onMode, onSettings = onSettings)
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
 * One page (or half of a wide one) on the screen, fitted as the settings say: pinched or double-tapped to zoom,
 * moved about when it's bigger than the screen (pulled on past its edge, it turns), tapped to turn or for the menu,
 * held for what can be done with it.
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
        val (pw, ph) = ComicLayout.fit(cw, ch, w, h, s.scale, s.zoomWide && !rotated)
        val minZoom = if (s.scale == ComicScale.STRETCH) 1f else min(1f, min(w / pw, h / ph))
        val maxZoom = max(4f, 2f * cw / pw)
        var zoom by remember(piece.key, pw, ph) { mutableFloatStateOf(1f) }
        var offset by remember(piece.key, pw, ph) {
            val (x, y) = ComicLayout.startOffset(pw, ph, w, h, s.zoomStart, rtl)
            mutableStateOf(Offset(x, y))
        }
        fun held(o: Offset, z: Float): Offset {
            val dw = pw * z
            val dh = ph * z
            return Offset(if (dw <= w) (w - dw) / 2 else o.x.coerceIn(w - dw, 0f), if (dh <= h) (h - dh) / 2 else o.y.coerceIn(h - dh, 0f))
        }
        // Back to how it first showed when it's turned away from.
        LaunchedEffect(current) {
            if (!current) {
                zoom = 1f
                val (x, y) = ComicLayout.startOffset(pw, ph, w, h, s.zoomStart, rtl)
                offset = Offset(x, y)
            }
        }
        val along = mode.horizontal
        // Bigger than the screen the way pages turn (or zoomed): swipes move the page, not to the next.
        val lockedNow = zoom > 1.01f || (if (along) pw * zoom > w + 1 else ph * zoom > h + 1)
        LaunchedEffect(lockedNow, current) { if (current) onLocked(lockedNow) }
        val zoomNow by rememberUpdatedState(zoom)
        val offsetNow by rememberUpdatedState(offset)
        fun animateTo(z1: Float, o1: Offset) {
            val z0 = zoom
            val o0 = offset
            val target = held(o1, z1)
            scope.launch {
                animate(0f, 1f, animationSpec = tween(if (s.animate) 220 else 1)) { t, _ ->
                    zoom = z0 + (z1 - z0) * t
                    offset = o0 + (target - o0) * t
                }
            }
        }
        /** On (1) or back (-1): along the page first if it's bigger than the screen and there's more of it that way. */
        fun step(by: Int) {
            if (by == 0) return
            val z = zoomNow
            val o = offsetNow
            if (s.panWide) {
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
            onTurn(by)
        }
        if (nudges != null) LaunchedEffect(nudges) { nudges.collect { step(it) } }
        // Drawn about the size it's shown, no bigger than about twice the screen; sharper again when zoomed in.
        val k = min(1f, sqrt(2f * w * h / (pw * ph)))
        val baseW = ((if (rotated) ph else pw) * k).toInt().coerceAtLeast(1)
        val baseH = ((if (rotated) pw else ph) * k).toInt().coerceAtLeast(1)
        val base by produceState<Bitmap?>(null, piece.key, r, baseW, baseH) { value = pages.bitmap(piece.page, r, baseW, baseH) }
        val tapsNow by rememberUpdatedState(s)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(piece.key, pw, ph) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var mine = false
                        var moved = Offset.Zero
                        var over = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val by = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (fingers >= 2) mine = true
                            else if (!mine) {
                                moved += pan
                                if (moved.getDistance() > viewConfiguration.touchSlop) {
                                    val z = zoomNow
                                    val bigAlong = if (along) pw * z > w + 1 else ph * z > h + 1
                                    val bigAcross = if (along) ph * z > h + 1 else pw * z > w + 1
                                    val acrossMove = if (along) abs(moved.y) > abs(moved.x) else abs(moved.x) > abs(moved.y)
                                    // The page's own to move: zoomed, bigger than the screen the way pages turn, or moved across where it's bigger that way.
                                    mine = z > 1.01f || bigAlong || (bigAcross && acrossMove)
                                }
                            }
                            if (mine && (by != 1f || pan != Offset.Zero)) {
                                val z0 = zoomNow
                                val z1 = (z0 * by).coerceIn(minZoom, maxZoom)
                                val c = event.calculateCentroid(useCurrent = true)
                                val wanted = c - (c - offsetNow) * (z1 / z0) + pan
                                val kept = held(wanted, z1)
                                if (fingers < 2) over += wanted - kept
                                zoom = z1
                                offset = kept
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        // Pulled on past the page's edge the way pages turn: to the next page that way.
                        val pull = if (along) over.x else over.y
                        val far = (if (along) w else h) * 0.14f
                        if (mine && abs(pull) > far) {
                            val toward = if (pull < 0) 1 else -1 // the page after (to the right, or below)
                            onTurn(if (along && rtl) -toward else toward)
                        }
                    }
                }
                .pointerInput(piece.key, pw, ph) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val held = awaitLongPressOrCancellation(down.id)
                        if (held != null) {
                            onLongPress()
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        val up = currentEvent.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (up.pressed || up.isConsumed || (up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                        val st = tapsNow
                        val action = ComicLayout.tapAction(up.position.x / size.width, up.position.y / size.height, st.taps, mode, st.invertTaps)
                        if (action != 0) {
                            step(action)
                            return@awaitEachGesture
                        }
                        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
                        if (second == null) onMenu()
                        else {
                            waitForUpOrCancellation()
                            val z0 = zoomNow
                            if (z0 > 1.01f) {
                                val (x, y) = ComicLayout.startOffset(pw, ph, w, h, st.zoomStart, rtl)
                                animateTo(1f, Offset(x, y))
                            } else {
                                val z1 = min(maxZoom, 2f)
                                val f = second.position
                                animateTo(z1, f - (f - offsetNow) * (z1 / z0))
                            }
                        }
                    }
                },
        ) {
            val dw = pw * zoom
            val dh = ph * zoom
            base?.let { bmp ->
                Box(
                    Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .size(with(density) { dw.toDp() }, with(density) { dh.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = bmp.asImageBitmap()
                    if (rotated) {
                        Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.requiredSize(with(density) { dh.toDp() }, with(density) { dw.toDp() }).graphicsLayer { rotationZ = 90f })
                    } else {
                        Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            // Zoomed in further than it's drawn: the part on the screen drawn again sharp, once it stops moving.
            if (!rotated && current) {
                var sharp by remember(piece.key) { mutableStateOf<Pair<android.graphics.RectF, Bitmap>?>(null) }
                val at = zoom to offset
                LaunchedEffect(at, base) {
                    sharp = null
                    val b = base ?: return@LaunchedEffect
                    if (dw / b.width < 1.25f) return@LaunchedEffect
                    delay(150)
                    val left = max(0f, offset.x)
                    val top = max(0f, offset.y)
                    val right = min(w, offset.x + dw)
                    val bottom = min(h, offset.y + dh)
                    if (right - left < 2 || bottom - top < 2) return@LaunchedEffect
                    val sx = r.width() / dw
                    val sy = r.height() / dh
                    val src = android.graphics.Rect(
                        (r.left + (left - offset.x) * sx).toInt(), (r.top + (top - offset.y) * sy).toInt(),
                        (r.left + (right - offset.x) * sx).toInt().coerceAtMost(r.right), (r.top + (bottom - offset.y) * sy).toInt().coerceAtMost(r.bottom),
                    )
                    if (src.width() < 2 || src.height() < 2) return@LaunchedEffect
                    pages.bitmap(piece.page, src, (right - left).toInt(), (bottom - top).toInt())?.let { sharp = android.graphics.RectF(left, top, right, bottom) to it }
                }
                sharp?.let { (where, bmp) ->
                    Image(
                        bmp.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                        modifier = Modifier.offset { IntOffset(where.left.roundToInt(), where.top.roundToInt()) }
                            .size(with(density) { where.width().toDp() }, with(density) { where.height().toDp() }),
                    )
                }
            }
        }
    }
}

// ---- the long strip -----------------------------------------------------------------------------------

/** One stretch of the long strip: a page, or part of a very tall one (drawn in parts so none is huge). */
private data class StripPiece(val page: Int, val part: Int, val parts: Int, val region: android.graphics.Rect?) {
    val key: String get() = "$page:$part"
}

@Composable
private fun StripComic(
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
    val count = pages.count
    // Each page's part to show (cropped, if asked for), as it's read.
    val regions = remember(s.cropStrip) { mutableStateMapOf<Int, android.graphics.Rect>() }
    // Read here, outside the box that measures the screen, so the strip is laid out again as pages become known.
    val known = regions.toMap()
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
        val list = rememberLazyListState(initialFirstVisibleItemIndex = pieces.indexOfFirst { it.page == page }.coerceAtLeast(0))
        // The pages near the screen read first, so the strip knows their shapes before they come into view.
        val first by remember { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex } }
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
        LaunchedEffect(list) {
            nudges.collect { by -> if (s.animate) list.animateScrollBy(by * h * 0.75f) else list.scrollBy(by * h * 0.75f) }
        }
        val zoom = remember { Animatable(1f) }
        var shift by remember { mutableFloatStateOf(0f) }
        fun heldShift(x: Float, z: Float) = x.coerceIn(-(z - 1) * w / 2, (z - 1) * w / 2)
        Box(
            Modifier.fillMaxSize()
                // Pinching zooms the strip; zoomed, a finger moves it sideways too.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinching = false
                        var moved = Offset.Zero
                        var sideways = false
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2) pinching = true
                            val by = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (pinching && (by != 1f || pan != Offset.Zero)) {
                                val z0 = zoom.value
                                val z1 = (z0 * by).coerceIn(1f, 4f)
                                scope.launch { zoom.snapTo(z1) }
                                val c = event.calculateCentroid(useCurrent = true)
                                shift = heldShift((shift - (c.x - w / 2)) * (z1 / z0) + (c.x - w / 2) + pan.x, z1)
                                list.dispatchRawDelta(-pan.y / z1)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!pinching && zoom.value > 1.01f) {
                                moved += pan
                                if (!sideways && moved.getDistance() > viewConfiguration.touchSlop) sideways = abs(moved.x) > abs(moved.y)
                                if (sideways) {
                                    shift = heldShift(shift + pan.x, zoom.value)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val held = awaitLongPressOrCancellation(down.id)
                        if (held != null) {
                            val idx = list.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                val y = (down.position.y - h / 2) / zoom.value + h / 2
                                y >= item.offset && y < item.offset + item.size
                            }?.index
                            pieces.getOrNull(idx ?: list.firstVisibleItemIndex)?.let { onLongPress(it.page) }
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        val up = currentEvent.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (up.pressed || up.isConsumed || (up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                        val action = ComicLayout.tapAction(up.position.x / size.width, up.position.y / size.height, s.taps, mode, s.invertTaps)
                        if (action != 0) {
                            scope.launch { if (s.animate) list.animateScrollBy(action * h * 0.75f) else list.scrollBy(action * h * 0.75f) }
                            return@awaitEachGesture
                        }
                        val second = if (s.doubleTapZoom) withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) } else null
                        if (second == null) onMenu()
                        else {
                            waitForUpOrCancellation()
                            val target = if (zoom.value > 1.01f) 1f else 2f
                            val c = second.position.x - w / 2
                            val goal = heldShift(if (target == 1f) 0f else -c * (target - 1), target)
                            val from = shift
                            scope.launch { zoom.animateTo(target, tween(if (s.animate) 220 else 1)) { shift = from + (goal - from) * ((value - 1f) / (target - 1f).let { if (it == 0f) 1f else it }).coerceIn(0f, 1f) } }
                            if (target == 1f) shift = 0f
                        }
                    }
                },
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                    translationX = shift
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
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(tr("Comic settings"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
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
