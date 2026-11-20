package dev.kolib.app.ui.reader

import dev.kolib.app.ui.components.FittedText
import dev.kolib.app.tr
import dev.kolib.app.ui.components.phoneLayout
import androidx.compose.material3.ListItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.BrightnessHigh
import android.view.WindowManager
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.outlined.Battery6Bar
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Battery4Bar
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.Battery2Bar
import androidx.compose.material.icons.outlined.Battery1Bar
import androidx.compose.material.icons.outlined.Battery0Bar
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatteryChargingFull
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material3.ButtonDefaults
import dev.kolib.app.reader.BookPictures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import dev.kolib.app.ui.components.AlertDialog
import dev.kolib.app.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import dev.kolib.app.ui.components.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kolib.app.reader.PictureBlend
import dev.kolib.app.reader.TapWidth
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import dev.kolib.app.reader.MarginSpot
import dev.kolib.app.reader.MarginItem
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import dev.kolib.app.reader.HeadTitles
import dev.kolib.app.reader.MarginRoom
import dev.kolib.app.reader.PageFrame
import dev.kolib.app.reader.PageSide
import dev.kolib.app.reader.RunningHeads
import dev.kolib.app.reader.HeadTitlePlace
import dev.kolib.app.reader.HeadSize
import dev.kolib.app.reader.HeadGap
import dev.kolib.app.reader.CollectionTitle
import dev.kolib.app.ui.theme.Garamond
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.kolib.app.epub.Contents
import dev.kolib.app.reader.BookServer
import dev.kolib.app.reader.PageTurn
import dev.kolib.app.reader.ReaderFlow
import dev.kolib.app.reader.ProgressLine
import dev.kolib.app.reader.BarShade
import dev.kolib.app.reader.AutoEnd
import dev.kolib.app.reader.AutoResume
import dev.kolib.app.reader.TapZones
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderFrame
import dev.kolib.app.reader.ReaderAlign
import dev.kolib.app.reader.Cutouts
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderFont
import dev.kolib.app.reader.ReaderHit
import dev.kolib.app.reader.ReaderHyphens
import dev.kolib.app.reader.ReaderKeys
import dev.kolib.app.reader.ReaderMargins
import dev.kolib.app.reader.ReaderPosition
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderSpread
import dev.kolib.app.reader.ReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class Panel { NONE, CONTENTS, SEARCH, LOOK, STATS }

/**
 * The reading setup leading someone through the reader, on its example book: what it shows over the page
 * (under the controls and the panels, so they open over it), and whether the controls can be brought up yet.
 */
internal class ReaderGuide(
    /** A tap in the middle brings up the controls, and a swipe in from a side its panel. */
    val menu: Boolean,
    /** Shown over the page; told what of the reader's own is over it. */
    val content: @Composable BoxScope.(over: GuideSpace) -> Unit,
)

/** What of the reader's own is over the page, for a [ReaderGuide] to keep clear of: a panel, and the controls' bars. */
internal class GuideSpace(
    val panelOpen: Boolean,
    /** How far down the top bar comes while the controls are up (none while they aren't). */
    val barTop: androidx.compose.ui.unit.Dp,
    /** How far up the bottom bar comes while the controls are up. */
    val barBottom: androidx.compose.ui.unit.Dp,
)

/**
 * Reading a book: the page fills the screen; a tap in the middle brings up the controls (contents,
 * bookmarks, search, the look of the page, and where you are in the book).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderScreen(
    controller: ReaderController,
    onSettings: (ReaderSettings) -> Unit,
    onClose: () -> Unit,
    /** Opens the text editor at a chapter; null when reading from the editor itself. */
    onEdit: ((String) -> Unit)? = null,
    /** For a book added as a PDF: reads the PDF instead. */
    onReadAsPdf: (() -> Unit)? = null,
    /** Shown instead of the page in screenshots, where a WebView can't draw. */
    pagePreview: (@Composable () -> Unit)? = null,
    initialPanel: Panel = Panel.NONE,
    /** Opens the app's own settings (its look, language and books folder), from the reading settings. */
    onAppSettings: (() -> Unit)? = null,
    /** The book's own text, page and layout settings, when it can keep some. */
    bookLook: BookLook? = null,
    /** The reading setup, when this is its example book. */
    guide: ReaderGuide? = null,
    /** Where the camera's cutouts are taken to be, for pictures and tests (the window says otherwise). */
    cutouts: Cutouts? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val s = controller.settings
    var panel by remember { mutableStateOf(initialPanel) }
    // The AI, about this book: its chapter summary, its notes on the book (written as it's read, when they're
    // kept), and the chat, which goes on from wherever it was. Its window is the app's, over this screen.
    val app = context.applicationContext as dev.kolib.app.KoLibApp
    val companion = remember(controller) { CompanionState() }
    val aiPlace = remember(controller, companion) {
        val notes = controller.notesFile?.let { dev.kolib.app.ai.BookNotes(it) }
        val book = dev.kolib.app.ai.BookAi.Book(controller.bookTitle, controller.bookAuthor, controller.bookLanguage, controller.bookDescription)
        val writer = notes?.let { n ->
            dev.kolib.app.ai.NotesWriter(
                n, app.ai, book,
                finished = { dev.kolib.app.ui.ai.finishedChapters(controller) },
                paragraphs = { controller.paragraphsOf(it) },
                blocked = {
                    when {
                        !app.inFront -> dev.kolib.app.ai.NotesWriter.Wait.AWAY
                        app.ai.settings.value.notesOnCharge && !dev.kolib.app.ai.Device.charging(app) -> dev.kolib.app.ai.NotesWriter.Wait.CHARGER
                        else -> null
                    }
                },
            )
        }
        dev.kolib.app.ui.ai.ChatPlace.Book(controller, controller.bookLanguage, companion, notes, writer)
    }
    val aiHost = dev.kolib.app.ui.ai.LocalAiHost.current
    if (aiHost != null) DisposableEffect(aiHost, aiPlace) {
        aiHost.book = aiPlace
        onDispose { if (aiHost.book === aiPlace) aiHost.book = null }
    }
    // A summary being written stops when the book is closed; the notes are written while it's open.
    DisposableEffect(companion) { onDispose { companion.job?.cancel() } }
    LaunchedEffect(aiPlace) { aiPlace.writer?.run() }
    // Got ready for questions about the book as it opens, if asked to be: what's on the page read ahead.
    LaunchedEffect(aiPlace) {
        if (app.ai.settings.value.preload && app.ai.ready()) {
            kotlinx.coroutines.delay(3_000)
            app.chat.warm(app.ai, aiPlace)
        }
    }
    // "Ask AI" on the selection toolbar: the chat opens with those words, to ask about them.
    LaunchedEffect(controller.askRequest) {
        controller.askRequest?.let { words ->
            controller.askRequest = null
            app.chat.attached = words
            app.aiPanel.open(tab = 1)
        }
    }
    // Led by the reading setup, the controls and panels stay away until it gets to them (and go away if it goes back).
    val menuAllowed = guide?.menu ?: true
    SideEffect { controller.menuLocked = !menuAllowed }
    LaunchedEffect(menuAllowed) {
        if (!menuAllowed) {
            controller.controlsVisible = false
            panel = Panel.NONE
        }
    }
    val scope = rememberCoroutineScope()

    // Full screen while reading; the status bar comes back with the controls. The navigation bar stays
    // hidden while the book is open (a swipe up from the bottom shows it for a moment): while it's
    // hidden, Android lets the reader have the whole of each side edge, for the swipes in from them.
    DisposableEffect(controller.controlsVisible) {
        val window = (context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.navigationBars())
        if (controller.controlsVisible) insets?.show(WindowInsetsCompat.Type.statusBars()) else insets?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
            controller.saveNow()
        }
    }
    // Which way the screen faces while reading.
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
    // The reader's own brightness, while it's open.
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
    // The volume buttons turn pages.
    DisposableEffect(s.volumeKeys) {
        ReaderKeys.handler = if (!s.volumeKeys) null else { e ->
            when (e.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { if (e.action == KeyEvent.ACTION_DOWN) controller.nextPage(); true }
                KeyEvent.KEYCODE_VOLUME_UP -> { if (e.action == KeyEvent.ACTION_DOWN) controller.previousPage(); true }
                else -> false
            }
        }
        onDispose { ReaderKeys.handler = null }
    }
    // The camera's cutouts, where the window overlaps them. One in a side narrows the page from both sides
    // (so it stays in the middle); one at the top or bottom is kept clear of by what's shown round the page.
    val density = LocalDensity.current
    val cutInsets = WindowInsets.displayCutout
    val cutTopPx = cutInsets.getTop(density)
    val cutBottomPx = cutInsets.getBottom(density)
    val cutSidePx = maxOf(cutInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr), cutInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr))
    val cutSide = cutouts?.side ?: (cutSidePx / density.density)
    LaunchedEffect(Unit) { controller.start() }

    // Going back closes what's over the page, else the book: from Android's back, or KoLib's own back swipe.
    fun goBack() {
        when {
            controller.openAnnotation != null -> controller.openAnnotation = null
            panel != Panel.NONE -> panel = Panel.NONE
            controller.note != null -> controller.note = null
            controller.browsing != null -> controller.browsing = null
            controller.image != null -> controller.image = null
            else -> onClose()
        }
    }
    BackHandler { goBack() }
    val edgeBack = remember { EdgeBackState() }
    val backSwipe = s.edgeBack && !s.edgePanels && !controller.selecting

    val pageColor = Color(s.theme.bg)
    val inkColor = Color(s.theme.fg)
    // Nothing shown over the page but (perhaps) the controls: a panel, a note, a picture, a question...
    fun pageClear(): Boolean = panel == Panel.NONE && controller.note == null && controller.image == null && controller.browsing == null &&
        controller.externalLink == null && !controller.atEnd && controller.openAnnotation == null && controller.editingNote == null &&
        controller.lookUpText == null
    BoxWithConstraints(Modifier.fillMaxSize().background(pageColor).edgeBackSwipe(edgeBack, backSwipe) { goBack() }.padding(horizontal = cutSide.dp)) {
        val wide = maxWidth >= 720.dp
        val cut = cutouts?.copy(width = maxWidth.value, height = maxHeight.value) ?: remember(cutTopPx, cutBottomPx, cutSidePx, maxWidth, maxHeight) {
            val d = density.density
            val shape = view.rootWindowInsets?.displayCutout
            // Where along the edge it is, from the window's left: the page starts the side cutout's depth in from there.
            fun span(r: android.graphics.Rect?, depthPx: Int): Cutouts.Span? = when {
                depthPx <= 0 -> null
                r == null || r.isEmpty -> Cutouts.Span(0f, Float.MAX_VALUE, depthPx / d)
                else -> Cutouts.Span(r.left / d - cutSide, r.right / d - cutSide, depthPx / d)
            }
            Cutouts(maxWidth.value, maxHeight.value, top = span(shape?.boundingRectTop, cutTopPx), bottom = span(shape?.boundingRectBottom, cutBottomPx), side = cutSide)
        }
        SideEffect { controller.placeCutouts(cut) }
        if (pagePreview != null) pagePreview() else {
            AndroidView(
                factory = { ctx -> ReaderFrame(ctx, controller) },
                update = { frame ->
                    frame.useSurface(!s.scrolling && (s.pageTurn == PageTurn.CURL || s.pageTurn == PageTurn.SWEEP || s.pageTurn == PageTurn.FADE))
                    frame.onLevel = { v ->
                        onSettings(controller.settings.withLevel(v))
                        controller.levelHint = v
                    }
                    frame.currentLevel = { controller.settings.level ?: systemBrightness(context) }
                    // A swipe in from the left edge opens the contents, from the right the reading settings;
                    // with that turned off, or something else over the page, Android's back swipe is left as it is.
                    frame.sideSwipes = s.edgePanels && pageClear() && menuAllowed
                    frame.onSideSwipe = { side -> panel = if (side < 0) Panel.CONTENTS else Panel.LOOK }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { frame ->
                    frame.release()
                    controller.release()
                },
            )
        }

        // The page headings, as a printed book has them, and the time and the rest, each where it's been put.
        // When scrolling, they sit on strips of the page's colour that the text scrolls under.
        if (!controller.controlsVisible) {
            if (s.scrolling) ScrollBands(s, pageColor, cut)
            if (s.headsShown) PageHeads(controller, s, inkColor)
            if (s.scrolling && s.runningHeads != RunningHeads.OFF) ScrollHead(controller, s, inkColor)
            Margins(controller, s, inkColor.copy(alpha = 0.5f))
            if (s.progressLine != ProgressLine.OFF) ProgressLineBar(controller, s)
        }

        // The reading setup's card and hints: over the page, under the controls (and told how far in their bars come).
        var topBarHeight by remember { mutableStateOf(0) }
        var bottomBarHeight by remember { mutableStateOf(0) }
        if (guide != null) {
            val barTop = with(LocalDensity.current) { (if (controller.controlsVisible) topBarHeight else 0).toDp() }
            val barBottom = with(LocalDensity.current) { (if (controller.controlsVisible) bottomBarHeight else 0).toDp() }
            guide.content(this, GuideSpace(panel != Panel.NONE, barTop, barBottom))
        }

        // Darker than the screen goes: a shade over the page.
        if (s.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.dim)))

        // While a swipe down the left edge sets the brightness.
        controller.levelHint?.let { v ->
            LaunchedEffect(v) {
                delay(900)
                controller.levelHint = null
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (v < 0.01f) Icons.Outlined.BrightnessLow else Icons.Outlined.BrightnessHigh, null, tint = MaterialTheme.colorScheme.inverseOnSurface)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (v < 0.01f) tr("Extra dim {0}%", ((-v / ReaderSettings.MAX_DIM) * 100).roundToInt().coerceAtLeast(1)) else tr("Brightness {0}%", (v * 100).roundToInt()),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // A slow chapter gets a spinner.
        var showSpinner by remember { mutableStateOf(false) }
        LaunchedEffect(controller.loading) {
            showSpinner = false
            if (controller.loading) {
                delay(400)
                showSpinner = controller.loading
            }
        }
        if (showSpinner) CircularProgressIndicator(Modifier.align(Alignment.Center), color = inkColor.copy(alpha = 0.4f))

        controller.jumpedFrom?.let { from ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (controller.controlsVisible) 132.dp else 40.dp)
                    .navigationBarsPadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
                    TextButton(onClick = { controller.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Back to {0}", from.chapterTitle.ifBlank { tr("where you were") }.take(40)))
                    }
                    IconButton(onClick = { controller.jumpedFrom = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, tr("Dismiss"), Modifier.size(16.dp))
                    }
                }
            }
        }

        // Autoscroll waits while something is shown over the page, the controls included.
        val overPage = controller.controlsVisible || !pageClear()
        LaunchedEffect(overPage, controller.loading) { controller.holdAutoscroll(overPage) }

        // The controls.
        AnimatedVisibility(controller.controlsVisible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }, modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.onSizeChanged { topBarHeight = it.height }) {
                ReaderTopBar(
                    controller = controller,
                    onClose = onClose,
                    onContents = { panel = Panel.CONTENTS },
                    onSearch = { panel = Panel.SEARCH },
                    onLook = { panel = Panel.LOOK },
                    onStats = { panel = Panel.STATS },
                    onEdit = onEdit?.let { edit -> { controller.chapter?.let { edit(it.path) } } },
                    onPdf = onReadAsPdf,
                    onCompanion = { app.aiPanel.toggle() },
                    aiPlace = aiPlace,
                )
            }
        }
        AnimatedVisibility(controller.controlsVisible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.onSizeChanged { bottomBarHeight = it.height }) { ReaderBottomBar(controller, onSettings) }
        }

        // Panels slide in from the side: on a tablet as a plain side panel, on a phone as a drawer with a header.
        val dismiss = { panel = Panel.NONE }
        // The contents open from the left, the rest from the right; a closing panel goes back the way it came.
        val lastPanel = remember { mutableStateOf(Panel.NONE) }
        SideEffect { if (panel != Panel.NONE) lastPanel.value = panel }
        val fromLeft = (if (panel != Panel.NONE) panel else lastPanel.value) == Panel.CONTENTS
        // On a phone, the drawer leaves a strip of the page to tap to close it.
        val drawerWidth = (maxWidth - 56.dp).coerceIn(280.dp, 360.dp)
        ReaderPanel(panel != Panel.NONE, wide, drawerWidth, dismiss, fromLeft = fromLeft) {
            // A panel sliding shut still shows what it held.
            when (if (panel != Panel.NONE) panel else lastPanel.value) {
                Panel.CONTENTS -> ContentsPanel(controller, wide, onPicked = { panel = Panel.NONE; controller.controlsVisible = false }, onClose = dismiss, onLeaveBook = onClose)
                Panel.SEARCH -> SearchPanel(controller, wide, onPicked = { panel = Panel.NONE; controller.controlsVisible = false }, onClose = dismiss)
                Panel.LOOK -> LookPanel(s, onSettings, controller.lookTab, wide, onTab = { controller.lookTab = it }, onClose = dismiss, onAppSettings = onAppSettings, bookLook = bookLook)
                Panel.STATS -> ReadingStatsPanel(controller, onClose = dismiss) { title, subtitle, close -> DrawerHeader(title, subtitle, close) }
                Panel.NONE -> Unit
            }
        }

        controller.note?.let { note ->
            NoteSheet(note.html, onClose = { controller.note = null }, onGoTo = {
                controller.note = null
                controller.followNote(note.href)
            })
        }
        controller.image?.let { src -> ImageViewer(controller, src, onClose = { controller.image = null }) }
        controller.heldImage?.let { src -> HeldPicture(controller, src, onDone = { controller.heldImage = null }) }
        controller.browsing?.let { browse ->
            // Opened afresh each time, where it's opened at.
            key(browse) {
                PictureBrowser(
                    controller, browse,
                    onClose = { controller.browsing = null },
                    onGoTo = { p ->
                        controller.browsing = null
                        panel = Panel.NONE
                        controller.controlsVisible = false
                        controller.goToPicture(p.chapter, p.path)
                    },
                )
            }
        }
        // Highlights: the card for a tapped one, writing a note, looking words up.
        AnnotationCard(controller, s, onSettings)
        NoteDialog(controller)
        LookUpSheet(controller, s, onSettings, controller.bookLanguage)
        // Summaries of the chapter and questions about the book: the AI's window over the page, where nothing above
        // this screen draws it (in pictures and tests).
        if (aiHost == null) dev.kolib.app.ui.ai.AiLayer(aiPlace)
        // A font added or removed while the book is open: the page takes up the new list.
        val fontsVersion by (LocalContext.current.applicationContext as dev.kolib.app.KoLibApp).fonts.version.collectAsState()
        LaunchedEffect(fontsVersion) { if (fontsVersion > 0) controller.userFontsChanged() }
        // "Search in book" from the selection toolbar.
        LaunchedEffect(controller.searchRequest) {
            controller.searchRequest?.let { words ->
                controller.lastSearch = words
                controller.lastHits = emptyList()
                controller.searchRequest = null
                panel = Panel.SEARCH
            }
        }
        controller.externalLink?.let { url ->
            AlertDialog(
                onDismissRequest = { controller.externalLink = null },
                title = { Text(tr("Open this link?")) },
                text = { Text(tr("This link opens a web page outside the book:\n\n{0}", url)) },
                confirmButton = {
                    TextButton(onClick = {
                        controller.externalLink = null
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }) { Text(tr("Open")) }
                },
                dismissButton = { TextButton(onClick = { controller.externalLink = null }) { Text(tr("Cancel")) } },
            )
        }
        if (controller.atEnd && guide != null) {
            // The example book's last page: nothing to say about it.
            LaunchedEffect(Unit) { controller.dismissEnd() }
        } else if (controller.atEnd) {
            AlertDialog(
                onDismissRequest = { controller.dismissEnd() },
                title = { Text(tr("The end")) },
                text = { Text(tr("You've reached the end of “{0}”.", controller.bookTitle)) },
                confirmButton = { TextButton(onClick = { controller.dismissEnd(); onClose() }) { Text(tr("Back to library")) } },
                dismissButton = { TextButton(onClick = { controller.dismissEnd() }) { Text(tr("Stay on this page")) } },
            )
        }

        // KoLib's own back swipe: the sides are kept from Android's, and its arrow shows over everything.
        if (backSwipe) {
            Box(Modifier.align(Alignment.CenterStart).width(48.dp).fillMaxHeight().systemGestureExclusion())
            Box(Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight().systemGestureExclusion())
        }
        EdgeBackIndicator(edgeBack)
    }
}

@Composable
private fun ReaderTopBar(
    controller: ReaderController,
    onClose: () -> Unit,
    onContents: () -> Unit,
    onSearch: () -> Unit,
    onLook: () -> Unit,
    onStats: () -> Unit,
    onEdit: (() -> Unit)?,
    onPdf: (() -> Unit)? = null,
    onCompanion: (() -> Unit)? = null,
    aiPlace: dev.kolib.app.ui.ai.ChatPlace? = null,
) {
    // A phone's width hasn't room for every button: the ones used least go in a menu there.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    var more by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book")) }
            // The contents on the left, where their panel opens.
            IconButton(onClick = onContents) { Icon(Icons.AutoMirrored.Filled.List, tr("Contents and bookmarks")) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(controller.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                controller.chapterTitle.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, tr("Search the book")) }
            val marked = controller.currentBookmark != null
            IconButton(onClick = { controller.toggleBookmark() }) {
                Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (marked) tr("Remove bookmark") else tr("Bookmark this page"),
                    tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            // Out of sight while the AI is out as its bubble, which stands in for it.
            if (onCompanion != null && dev.kolib.app.ui.ai.aiButtonShown()) IconButton(onClick = onCompanion, modifier = Modifier.testTag("reader-companion")) {
                dev.kolib.app.ui.ai.AiButtonIcon(tr("Summary and questions"), aiPlace)
            }
            if (!compact) IconButton(onClick = onStats) { Icon(Icons.Outlined.BarChart, tr("Reading stats")) }
            IconButton(onClick = onLook) { Icon(Icons.Outlined.Settings, tr("Reading settings")) }
            if (!compact) {
                if (onPdf != null) IconButton(onClick = onPdf) { Icon(Icons.Outlined.PictureAsPdf, tr("Read as PDF")) }
                if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.EditNote, tr("Edit this chapter's text")) }
            } else Box {
                IconButton(onClick = { more = true }, modifier = Modifier.testTag("reader-more")) { Icon(Icons.Filled.MoreVert, tr("More")) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(tr("Reading stats")) }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = { more = false; onStats() })
                    if (onPdf != null) DropdownMenuItem(text = { Text(tr("Read as PDF")) }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { more = false; onPdf() })
                    if (onEdit != null) DropdownMenuItem(text = { Text(tr("Edit this chapter's text")) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { more = false; onEdit() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBottomBar(controller: ReaderController, onSettings: (ReaderSettings) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0f) }
    val progress = if (dragging) value else controller.bookProgress
    val book = controller.bookPages
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            AutoscrollRow(controller, onSettings)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { controller.previousChapter() }) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, tr("Previous chapter")) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Where chapters start.
                    val marks = controller.chapterMarks
                    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 10.dp)) {
                        for (m in marks) {
                            val x = size.width * m
                            drawLine(tick, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                    // The place the button with the controls goes back to: a line across the bar, like the bar's own handle.
                    val backAt = controller.barReturn?.bookProgress
                    val backMark = MaterialTheme.colorScheme.onSurfaceVariant
                    Slider(
                        value = progress,
                        onValueChange = { dragging = true; value = it },
                        onValueChangeFinished = {
                            dragging = false
                            controller.goToBookProgressFromBar(value)
                        },
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                modifier = if (backAt == null) Modifier else Modifier.drawWithContent {
                                    drawContent()
                                    val x = size.width * backAt.coerceIn(0f, 1f)
                                    val half = 13.dp.toPx()
                                    drawLine(
                                        backMark, Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half),
                                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                                    )
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { controller.nextChapter() }) { Icon(Icons.AutoMirrored.Filled.NavigateNext, tr("Next chapter")) }
            }
            // Pages are counted through the whole book, at the text size and page size being read at. Its
            // numbers are all one width, so they don't shift about as they change.
            val footStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            val footColor = MaterialTheme.colorScheme.onSurfaceVariant
            val dragAt = if (dragging) controller.bookPageAt(value) else null
            if (dragAt != null && book != null) {
                // While dragging: the page number held still, just left of the middle, and the chapter it's
                // in just right of it, however long the chapter's name.
                val total = book.total.toString()
                val shown = dragAt.toString().padStart(total.length, '\u2007')
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Page {0} of {1}", shown, total), style = footStyle, color = footColor, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("  ·  ", style = footStyle, color = footColor, maxLines = 1)
                    Text(
                        controller.chapterTitleAt(value), style = footStyle, color = footColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val details = if (dragging) tr("Go to {0}%", (value * 100).roundToInt()) else listOfNotNull(
                    controller.chapterTitle.ifBlank { null },
                    controller.bookPage?.let { tr("{0} of {1}", controller.pagesLabel(it, controller.bookPageLast), book?.total) }
                        ?: if (controller.chapterPages > 1) tr("{0} of {1} in chapter", controller.pagesLabel(controller.firstShown + 1, controller.lastShown + 1), controller.chapterPages) else null,
                    // The book's page numbers show once every chapter is counted, so they never change.
                    if (controller.countingPages && book == null) tr("Counting pages…") else null,
                    controller.minutesLeftInChapter.takeIf { it > 0 }?.let { tr("{0} min left in chapter", it) },
                    tr("{0}% of book read", (controller.bookProgress * 100).roundToInt()),
                ).joinToString(" · ")
                Text(
                    details,
                    style = footStyle,
                    color = footColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Back to [back], where the reader was before moving with the progress bar (and then back again).
 * On a phone, where the row is full, just its icon.
 */
@Composable
private fun BarReturnButton(controller: ReaderController, back: ReaderPosition, compact: Boolean, modifier: Modifier = Modifier) {
    val label = controller.barReturnBookPage?.let { tr("Back to page {0}", it) } ?: tr("Back to {0}%", (back.bookProgress * 100).roundToInt())
    if (compact) {
        FilledTonalIconButton(onClick = { controller.swapWithBarReturn() }, modifier = modifier) {
            Icon(Icons.AutoMirrored.Filled.Undo, label, Modifier.size(20.dp))
        }
    } else {
        FilledTonalButton(
            onClick = { controller.swapWithBarReturn() },
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            modifier = modifier,
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Starting and pausing autoscroll, and its speed. Starting it puts the controls away, to read. */
@Composable
private fun AutoscrollRow(controller: ReaderController, onSettings: (ReaderSettings) -> Unit) {
    val s = controller.settings
    val on = controller.autoscroll
    // A phone hasn't room for the speed on one line: the number goes over its unit, without the label.
    val phone = phoneLayout()
    val back = controller.barReturn
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = {
                if (on) controller.stopAutoscroll()
                else {
                    controller.startAutoscroll()
                    controller.controlsVisible = false
                }
            }) {
                Icon(if (on) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (on) tr("Pause autoscroll") else tr("Autoscroll"))
            }
            Spacer(Modifier.weight(1f))
            if (phone && back != null) {
                BarReturnButton(controller, back, compact = true)
                Spacer(Modifier.weight(1f))
            }
            if (!phone) Text(tr("Speed"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(
                onClick = { onSettings(s.copy(autoWpm = (s.autoWpm - 10).coerceAtLeast(ReaderSettings.MIN_WPM))) },
                enabled = s.autoWpm > ReaderSettings.MIN_WPM,
            ) { Icon(Icons.Outlined.Remove, tr("Slower")) }
            if (phone) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.autoWpm}", style = MaterialTheme.typography.titleSmall)
                    Text(tr("words per minute"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(tr("{0} words per minute", s.autoWpm), style = MaterialTheme.typography.labelLarge)
            }
            IconButton(
                onClick = { onSettings(s.copy(autoWpm = (s.autoWpm + 10).coerceAtMost(ReaderSettings.MAX_WPM))) },
                enabled = s.autoWpm < ReaderSettings.MAX_WPM,
            ) { Icon(Icons.Outlined.Add, tr("Faster")) }
        }
        // After moving with the progress bar: back to where the reader was, in the middle of the bar.
        if (!phone && back != null) BarReturnButton(controller, back, compact = false, modifier = Modifier.align(Alignment.Center).padding(bottom = 2.dp))
    }
}

/**
 * A thin line along the foot of the page, as long as the share of the chapter (or the book) read so
 * far, as a Kobo shows. On pages, the page showing counts as read.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ProgressLineBar(c: ReaderController, s: ReaderSettings) {
    val f = when (s.progressLine) {
        ProgressLine.BOOK -> c.bookProgress
        else -> if (c.scrolledHere) c.chapterProgress else (c.page + 1f) / c.pages.coerceAtLeast(1)
    }.coerceIn(0f, 1f)
    ProgressBarLine(f, Color(s.barColor), Modifier.align(Alignment.BottomStart).padding(bottom = ProgressBarInset))
}

/** How far up from the foot of the page the progress bar is. (Autoscroll's bar on pages is under it, right at the foot.) */
private val ProgressBarInset = 5.dp

/**
 * How far up from the foot of the page the items at the bottom sit: clear of the progress bar when it
 * shows, and down in its place when it's off.
 */
private fun bottomItemsInset(s: ReaderSettings, cut: Cutouts = Cutouts.NONE) = s.bottomRowAt(cut).dp

/**
 * The bar itself, in the text's colour: what's been read is a thicker line from the left, and what's
 * still to read a hairline across the rest, through the middle of it.
 */
@Composable
private fun ProgressBarLine(f: Float, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(1.dp).background(ink))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(ink))
    }
}

/** When scrolling: the strips at the top and bottom that the margins' items show on, with the text going by under them. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ScrollBands(s: ReaderSettings, paper: Color, cut: Cutouts) {
    val bandTop = s.bandTop(cut)
    val bandBottom = s.bandBottom(cut)
    if (bandTop > 0) Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(bandTop.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to paper, 0.75f to paper, 1f to paper.copy(alpha = 0f)))
    )
    if (bandBottom > 0) Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(bandBottom.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.25f to paper, 1f to paper))
    )
}

/**
 * A panel over the page that slides in from the edge it's swiped in from (the contents from the left, the
 * rest from the right), over a shade. A tap on the shade closes it; a finger dragged across the shade slides it
 * closed, and it springs back open if let go short of half way without a flick. On a tablet it's a plain 420dp side
 * panel; on a phone, a drawer with its inner corners rounded.
 */
@Composable
internal fun ReaderPanel(visible: Boolean, wide: Boolean, width: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit, fromLeft: Boolean = false, content: @Composable () -> Unit) {
    // How open it is: 1 open, 0 closed.
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        open.animateTo(if (visible) 1f else 0f, androidx.compose.animation.core.tween(if (visible) 260 else 220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    if (!visible && open.value <= 0f) return
    val panelWidth = if (wide) 420.dp else width
    val density = LocalDensity.current
    val widthPx = with(density) { panelWidth.toPx() }
    // Which way a drag closes it: left for one on the left.
    val closing = if (fromLeft) -1f else 1f
    Box(Modifier.fillMaxSize().testTag("reader-panel")) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open.value }
                .background(Color.Black.copy(alpha = if (wide) 0.32f else 0.4f))
                .testTag("reader-panel-shade")
                .pointerInput(visible) { if (visible) detectTapGestures { onDismiss() } }
                .pointerInput(visible, widthPx, fromLeft) {
                    if (!visible) return@pointerInput
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { open.snapTo((open.value - amount * closing / widthPx).coerceIn(0f, 1f)) }
                        },
                        onDragEnd = {
                            // Positive: thrown the way that closes it.
                            val thrown = tracker.calculateVelocity().x * closing
                            if (thrown > with(density) { 700.dp.toPx() } || open.value < 0.5f) onDismiss()
                            else scope.launch { open.animateTo(1f, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) }
                        },
                        onDragCancel = { scope.launch { open.animateTo(1f) } },
                    )
                }
        )
        val slide = Modifier.graphicsLayer { translationX = (1f - open.value) * widthPx * closing }
        if (wide) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
                modifier = Modifier.align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd).then(slide).width(420.dp).fillMaxHeight(),
            ) {
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
            }
        } else {
            val corner = 28.dp
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 12.dp,
                shape = if (fromLeft) RoundedCornerShape(topEnd = corner, bottomEnd = corner) else RoundedCornerShape(topStart = corner, bottomStart = corner),
                modifier = Modifier
                    .align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .then(slide)
                    .width(width)
                    .fillMaxHeight()
                    // Taps inside the drawer stay in it.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                val outer = if (fromLeft) WindowInsetsSides.Start else WindowInsetsSides.End
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + outer))) { content() }
            }
        }
    }
}

/** The top of a drawer: its name, a line under it, and a button to close it; and, given [onLeave], one to leave the book. */
@Composable
internal fun DrawerHeader(title: String, subtitle: String? = null, onClose: () -> Unit, onLeave: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (onLeave != null) 4.dp else 24.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onLeave != null) {
            IconButton(onClick = onLeave, modifier = Modifier.testTag("contents-leave")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book")) }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close")) }
    }
}

/** A drawer's tab: an icon over a short name, with a count on the icon when there's something to count. */
private class DrawerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val count: Int = 0)

/** The tabs across the top of a drawer, small enough for five to fit side by side on a phone. */
@Composable
private fun DrawerTabs(tabs: List<DrawerTab>, pager: PagerState) {
    val scope = rememberCoroutineScope()
    PrimaryTabRow(
        selectedTabIndex = pager.currentPage,
        containerColor = Color.Transparent,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) },
    ) {
        tabs.forEachIndexed { i, t ->
            Tab(
                selected = i == pager.currentPage,
                onClick = { scope.launch { pager.animateScrollToPage(i) } },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(64.dp),
            ) {
                if (t.count > 0) {
                    BadgedBox(badge = { Badge { Text(if (t.count > 99) "99+" else "${t.count}") } }) { Icon(t.icon, null, Modifier.size(22.dp)) }
                } else {
                    Icon(t.icon, null, Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                FittedText(
                    t.label,
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ContentsPanel(controller: ReaderController, wide: Boolean, onPicked: () -> Unit, onClose: () -> Unit, onLeaveBook: (() -> Unit)? = null) {
    val pager = rememberPagerState(initialPage = controller.contentsTab.coerceIn(0, 3)) { 4 }
    // The book's pictures are looked for once the panel's open (the tab shows how many once they're found).
    LaunchedEffect(controller) { controller.findPictures() }
    val pictureCount = controller.pictures?.size ?: 0
    SwipedTabs(pager, controller.contentsTab) { controller.contentsTab = it }
    Column(Modifier.fillMaxSize()) {
        if (!wide) {
            DrawerHeader(
                controller.bookTitle,
                subtitle = controller.bookAuthor?.takeIf { it.isNotBlank() } ?: tr("{0}% read", (controller.bookProgress * 100).roundToInt()),
                onClose = onClose,
                onLeave = onLeaveBook,
            )
        } else if (onLeaveBook != null) {
            // Out of the book from here too, as from the bar over the page.
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLeaveBook, modifier = Modifier.testTag("contents-leave")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book")) }
                Spacer(Modifier.width(4.dp))
                Text(controller.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Four tabs, each an icon over its name (with how many it holds), so they fit side by side on any screen.
        DrawerTabs(
            listOf(
                DrawerTab(tr("Contents"), Icons.Outlined.Toc),
                DrawerTab(tr("Bookmarks"), Icons.Outlined.BookmarkBorder, controller.bookmarks.size),
                DrawerTab(tr("Highlights"), Icons.Outlined.BorderColor, controller.annotations.size),
                DrawerTab(tr("Pictures"), Icons.Outlined.Image, pictureCount),
            ),
            pager,
        )
        // Swipe left or right for the next tab.
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> ContentsList(controller, onPicked)
                1 -> BookmarksList(controller, onPicked)
                2 -> HighlightsList(controller, onPicked)
                else -> PicturesList(controller)
            }
        }
    }
}

/** The book's contents, opened at the chapter being read. */
@Composable
private fun ContentsList(controller: ReaderController, onPicked: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val flat = remember(controller.toc) { Contents.flatten(controller.toc) }
        // The chapter on screen, or the opening shown with it (where the contents may point instead).
        val currentIndex = controller.contentsPaths.firstNotNullOfOrNull { p -> flat.indexOfLast { it.first.path == p }.takeIf { it >= 0 } } ?: -1
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
        if (flat.isEmpty()) {
            // No contents: the chapters themselves.
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(controller.chapters) { i, c ->
                    ContentsRow(c.title, 0, i == controller.chapterIndex) {
                        controller.open(i, org.json.JSONObject().put("type", "start"))
                        onPicked()
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(flat) { i, (node, level) ->
                    ContentsRow(node.title, level, i == currentIndex) {
                        controller.goTo(node)
                        onPicked()
                    }
                }
            }
        }
    }
}

/**
 * Every picture in the book, small, in the order they come, each with the chapter it's in (and the book, in a book
 * of several). Tap one to see it whole.
 */
@Composable
private fun PicturesList(controller: ReaderController) {
    val all = controller.pictures
    when {
        all == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
        all.isEmpty() -> Text(
            tr("This book has no pictures."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        else -> {
            val large = remember(all) { all.filter { BookPictures.large(it.width, it.height) } }
            // Only worth a choice when the book has both: little marks (each chapter's, say) and big pictures.
            val mixed = large.isNotEmpty() && large.size < all.size
            val shown = if (controller.largePicturesOnly && mixed) large else all
            Column(Modifier.fillMaxSize()) {
                if (mixed) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !controller.largePicturesOnly, onClick = { controller.largePicturesOnly = false }, label = { Text(tr("All")) })
                        FilterChip(selected = controller.largePicturesOnly, onClick = { controller.largePicturesOnly = true }, label = { Text(tr("Large only ({0})", large.size)) })
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(shown, key = { _, p -> p.path }) { i, p ->
                        PictureCell(controller, p) { controller.browsing = ReaderController.Browse(shown, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PictureCell(controller: ReaderController, p: ReaderController.BookPicture, onOpen: () -> Unit) {
    val turned = controller.turns[p.path] ?: 0
    val small by produceState<android.graphics.Bitmap?>(null, p.path, turned) {
        value = withContext(Dispatchers.IO) { runCatching { controller.thumbnail(p.path, 360) }.getOrNull() }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onOpen)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            small?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) }
        }
        Text(
            controller.chapterNameOf(p.chapter).ifBlank { tr("Front of the book") },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        controller.innerBookOf(p.chapter)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The bookmarks, in the order of the book. */
@Composable
private fun BookmarksList(controller: ReaderController, onPicked: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (controller.bookmarks.isEmpty()) {
            Text(
                tr("No bookmarks yet. Tap the middle of a page, then the bookmark icon."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(controller.bookmarks, key = { "${it.path}#${it.anchor}#${it.at}" }) { b ->
                    BookmarkRow(b, onOpen = { controller.goTo(b); onPicked() }, onDelete = { controller.removeBookmark(b) })
                }
            }
        }
    }
}

@Composable
private fun ContentsRow(title: String, level: Int, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(start = 20.dp + 18.dp * level.coerceAtMost(4), end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.ifBlank { tr("Untitled") },
            style = if (level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookmarkRow(b: ReaderPosition, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${b.chapterTitle.ifBlank { tr("Bookmark") }} · ${(b.bookProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (b.snippet.isNotBlank()) {
                Text(b.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, tr("Remove bookmark")) }
    }
}

@Composable
private fun SearchPanel(controller: ReaderController, wide: Boolean, onPicked: () -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf(controller.lastSearch) }
    var hits by remember { mutableStateOf(controller.lastHits) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun run() {
        scope.launch {
            searching = true
            hits = controller.search(query)
            controller.lastSearch = query
            controller.lastHits = hits
            searching = false
        }
    }
    Column(Modifier.fillMaxSize().padding(top = if (wide) 12.dp else 0.dp)) {
        if (!wide) {
            DrawerHeader(tr("Search"), subtitle = controller.bookTitle, onClose = onClose)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Search in book")) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; hits = emptyList() }) { Icon(Icons.Filled.Close, tr("Clear")) } },
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { run() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LaunchedEffect(query) {
            if (query.trim().length >= 3) {
                delay(450)
                run()
            }
        }
        Text(
            when {
                searching -> tr("Searching…")
                query.isBlank() -> tr("Type a word or phrase to find it in the book.")
                hits.isEmpty() -> tr("No matches found.")
                hits.size >= 2000 -> tr("Showing the first 2,000 matches")
                else -> if (hits.size == 1) tr("1 match") else tr("{0} matches", hits.size)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            var lastChapter = -1
            hits.forEachIndexed { i, hit ->
                if (hit.chapterIndex != lastChapter) {
                    lastChapter = hit.chapterIndex
                    item(key = "c$i") {
                        Text(hit.chapter, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
                    }
                }
                item(key = "h$i") { HitRow(hit) { controller.goToHit(hit); onPicked() } }
            }
        }
    }
}

@Composable
internal fun HitRow(hit: ReaderHit, onClick: () -> Unit) {
    val text = buildAnnotatedString {
        append(hit.before)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer)) { append(hit.text) }
        append(hit.after)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** The reading settings, in groups: a tab for each. */
internal enum class LookTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TEXT(tr("Text"), Icons.Outlined.TextFields),
    PAGE(tr("Page"), Icons.Outlined.Palette),
    LAYOUT(tr("Layout"), Icons.Outlined.AutoStories),
    CONTROLS(tr("Controls"), Icons.Outlined.TouchApp),
    AUTOSCROLL(dev.kolib.app.trc("tab", "Autoscroll"), Icons.Outlined.Speed),
}

@Composable
private fun LookPanel(
    s: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    tab: Int,
    wide: Boolean,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onAppSettings: (() -> Unit)? = null,
    bookLook: BookLook? = null,
) {
    val tabs = LookTab.entries
    val pager = rememberPagerState(initialPage = tab.coerceIn(0, tabs.size - 1)) { tabs.size }
    SwipedTabs(pager, tab, onTab)
    Column(Modifier.fillMaxSize()) {
        // The app's own settings, a step away: its theme, language and books folder.
        val appSettings: @Composable () -> Unit = {
            if (onAppSettings != null) AppSettingsLink(onAppSettings, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (wide) {
            appSettings()
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                val scope = rememberCoroutineScope()
                tabs.forEachIndexed { i, t ->
                    // Tighter than a standard tab, so all five names fit across the panel.
                    Tab(
                        selected = i == pager.currentPage,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(48.dp),
                    ) {
                        FittedText(
                            t.label,
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp),
                            modifier = Modifier.padding(horizontal = 2.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            DrawerHeader(tr("Reading settings"), onClose = onClose)
            appSettings()
            DrawerTabs(tabs.map { DrawerTab(it.label, it.icon) }, pager)
        }
        // Swipe left or right for the next tab; each starts at its top.
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (bookLook != null && tabs[page] in BOOK_LOOK_TABS) BookLookNote(bookLook)
                when (tabs[page]) {
                    LookTab.TEXT -> TextSettings(s, onChange)
                    LookTab.PAGE -> PageSettings(s, onChange)
                    LookTab.LAYOUT -> LayoutSettings(s, onChange)
                    LookTab.CONTROLS -> ControlSettings(s, onChange)
                    LookTab.AUTOSCROLL -> AutoscrollSettings(s, onChange)
                }
            }
        }
    }
}

/**
 * A book's own text, page and layout settings: whether it has them (changed while reading it), and going
 * back to the usual ones or making its own the usual.
 */
class BookLook(val own: Boolean, val makeUsual: () -> Unit, val useUsual: () -> Unit, val landscape: Boolean = false)

/** The tabs whose settings a book keeps for itself. */
private val BOOK_LOOK_TABS = setOf(LookTab.TEXT, LookTab.PAGE, LookTab.LAYOUT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookLookNote(look: BookLook) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = if (look.own) 2.dp else 10.dp)) {
            Text(
                if (look.own) tr("This book has its own settings.")
                else tr("Changes here apply to this book only."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            // Each way of holding the device has settings of its own: which these are.
            Text(
                if (look.landscape) tr("Portrait and landscape have separate settings. These are landscape's.")
                else tr("Portrait and landscape have separate settings. These are portrait's."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp, top = 2.dp),
            )
            if (look.own) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = look.makeUsual) { Text(tr("Set as default")) }
                TextButton(onClick = look.useUsual) { Text(tr("Use default")) }
            }
        }
    }
}

/**
 * Keeps a panel's tabs and the tab remembered for the book in step: the tab swiped or tapped to is
 * remembered, and one set from outside is gone to.
 */
@Composable
private fun SwipedTabs(pager: PagerState, tab: Int, onTab: (Int) -> Unit) {
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onTab(it) } }
    LaunchedEffect(tab) { if (tab != pager.currentPage && !pager.isScrollInProgress) pager.scrollToPage(tab.coerceIn(0, pager.pageCount - 1)) }
}

@Composable
private fun Heading(text: String, first: Boolean = false) {
    if (!first) Spacer(Modifier.height(18.dp))
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Help(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Font, size, spacing and the shape of the text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading(tr("Font"), first = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        ReaderFont.entries.forEach { f ->
            FilterChip(selected = s.userFont.isEmpty() && s.font == f, onClick = { onChange(s.copy(font = f, userFont = "")) }, label = { Text(f.label) })
        }
        UserFontChips(s, onChange)
    }

    Heading(tr("Text size"))
    val scales = ReaderSettings.FONT_SCALES
    val at = scales.indexOfFirst { it >= s.fontScale - 0.001f }.let { if (it < 0) scales.size - 1 else it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(s.copy(fontScale = scales[(at - 1).coerceAtLeast(0)])) }, enabled = at > 0) { Icon(Icons.Outlined.Remove, tr("Smaller")) }
        Slider(
            value = at.toFloat(),
            onValueChange = { onChange(s.copy(fontScale = scales[it.roundToInt().coerceIn(0, scales.size - 1)])) },
            valueRange = 0f..(scales.size - 1).toFloat(),
            steps = scales.size - 2,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(s.copy(fontScale = scales[(at + 1).coerceAtMost(scales.size - 1)])) }, enabled = at < scales.size - 1) { Icon(Icons.Outlined.Add, tr("Bigger")) }
        Text("${(s.fontScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }

    Heading(tr("Line spacing"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        ReaderSettings.LINE_SPACINGS.forEach { v ->
            FilterChip(
                selected = s.lineSpacing == v,
                onClick = { onChange(s.copy(lineSpacing = v)) },
                label = { Text(if (v == 0f) tr("Book default") else "%.1f".format(v)) },
            )
        }
    }

    Heading(tr("Margins"))
    Choices(ReaderMargins.entries, s.margins, { it.label }) { onChange(s.copy(margins = it)) }

    Heading(tr("Alignment"))
    Choices(ReaderAlign.entries, s.align, { it.label }) { onChange(s.copy(align = it)) }

    Heading(tr("Hyphenation"))
    Choices(ReaderHyphens.entries, s.hyphens, { it.label }) { onChange(s.copy(hyphens = it)) }

    Spacer(Modifier.height(14.dp))
    TextButton(onClick = {
        onChange(s.copy(font = ReaderFont.BOOK, fontScale = 1f, lineSpacing = 0f, margins = ReaderMargins.MEDIUM, align = ReaderAlign.BOOK, hyphens = ReaderHyphens.BOOK))
    }) { Text(tr("Reset to book defaults")) }
}

/** The page's colour and brightness, its pictures, and what shows around it. */
@Composable
private fun PageSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading(tr("Page colour"), first = true)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReaderTheme.entries.forEach { t ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(t.bg))
                        .border(if (t == s.theme) 3.dp else 1.dp, if (t == s.theme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onChange(s.copy(theme = t)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Aa"), color = Color(t.fg), style = MaterialTheme.typography.labelLarge)
                }
                Text(t.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    Heading(tr("Brightness"))
    // With the tablet's own brightness, the slider starts from it.
    val context = LocalContext.current
    val screenNow = remember(s.level == null) { systemBrightness(context) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.BrightnessLow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = s.level ?: screenNow,
            onValueChange = { onChange(s.withLevel(it)) },
            valueRange = -ReaderSettings.MAX_DIM..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Icon(Icons.Outlined.BrightnessHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Help(tr("Slide fully left for extra dim. Swiping up or down the left edge of the page also works."))
    SwitchRow(tr("Use the system brightness"), s.level == null) { system ->
        onChange(if (system) s.copy(brightness = -1f, dim = 0f) else s.copy(brightness = screenNow, dim = 0f))
    }
    SwitchRow(tr("Open books at the system brightness"), s.brightnessFromScreen) { onChange(s.copy(brightnessFromScreen = it)) }
    Help(tr("Off, books open at the brightness you last set here."))

    Heading(tr("Match pictures to the page colour"))
    Choices(PictureBlend.entries, s.pictures, { it.label }) { onChange(s.copy(pictures = it)) }
    Help(s.pictures.help)

    Heading(tr("Links"))
    SwitchRow(tr("Colour links"), s.colourLinks) { onChange(s.copy(colourLinks = it)) }
    Help(tr("Off, links look like normal text but still work."))

    Heading(tr("Page headings"))
    Choices(RunningHeads.entries, s.runningHeads, { it.label }) { onChange(s.withHeads(it)) }
    Help(
        if (s.runningHeads == RunningHeads.OFF) tr("Choose where the page number and titles go, below.")
        else tr("The page number, with the book title on left pages and the chapter title on right pages.") +
            if (s.scrolling) tr(" Not shown when scrolling.") else ""
    )
    if (s.runningHeads != RunningHeads.OFF) {
        Heading(tr("Title position"))
        Choices(HeadTitlePlace.entries, s.headTitlePlace, { it.label }) { onChange(s.copy(headTitlePlace = it)) }
        Heading(tr("Heading size"))
        Choices(HeadSize.entries, s.headSize, { it.label }) { onChange(s.copy(headSize = it)) }
        Heading(tr("Space under the headings"))
        Choices(HeadGap.entries, s.headGap, { it.label }) { onChange(s.copy(headGap = it)) }
        Help(tr("Printed books use Standard size and One line."))
    }

    Heading(tr("Omnibus title"))
    Choices(CollectionTitle.entries, s.collectionTitle, { it.label }) { onChange(s.copy(collectionTitle = it)) }
    Help(tr("Which title to show in a book made of several."))

    Heading(tr("Info around the page"))
    Help(
        tr("Tap a position to choose what it shows.") + when {
            s.runningHeads == RunningHeads.OFF -> ""
            s.runningHeads.top -> tr(" The page headings use the top.")
            else -> tr(" The page headings use the bottom.")
        }
    )
    Spacer(Modifier.height(8.dp))
    MarginPreview(s)
    Spacer(Modifier.height(4.dp))
    val headsEnd: Boolean? = if (s.runningHeads != RunningHeads.OFF) s.runningHeads.top else null
    MarginItem.entries.forEach { item ->
        MarginRow(item, s.copy(flow = ReaderFlow.PAGES).shownSpotOf(item), taken = { spot -> spot != MarginSpot.OFF && spot.top == headsEnd }) { spot ->
            onChange(s.copy(marginItems = s.marginItems + (item to spot)))
        }
    }

    Heading(tr("Progress bar"))
    Choices(ProgressLine.entries, s.progressLine, { it.label }) { onChange(s.copy(progressLine = it)) }
    Help(tr("Shows how much of the book or chapter you've read."))

    Heading(tr("Bar colour"))
    Choices(BarShade.entries, s.barShade, { it.label }) { onChange(s.copy(barShade = it)) }
    Help(tr("Dimmed helps prevent burn-in on OLED screens."))
}

/** Pages or scrolling, and how pages turn. */
@Composable
private fun LayoutSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading(tr("Reading mode"), first = true)
    Choices(ReaderFlow.entries, s.flow, { it.label }) { onChange(s.copy(flow = it)) }
    Help(
        if (s.scrolling) tr("Scroll down through each chapter. At the end, tap Next chapter or keep pulling up.")
        else tr("Turn pages, like a printed book.")
    )

    if (!s.scrolling) {
        Heading(tr("Pages side by side"))
        Choices(ReaderSpread.entries, s.spread, { it.label }) { onChange(s.copy(spread = it)) }

        Heading(tr("Page turn effect"))
        Choices(PageTurn.entries, s.pageTurn, { it.label }) { onChange(s.copy(pageTurn = it)) }
    }

    Heading(tr("Chapter title pages"))
    SwitchRow(tr("Show at the top of the chapter"), s.joinOpenings) { onChange(s.copy(joinOpenings = it)) }
    Help(tr("For books that put a chapter's number or picture on a page of its own."))

    Heading(tr("Chapter openings"))
    SwitchRow(tr("Start chapters on a right-hand page"), s.rectoOpenings) { onChange(s.copy(rectoOpenings = it)) }
    Help(tr("With two pages side by side. A blank page is added where needed."))
    SwitchRow(tr("Print-style chapter headings"), s.printHeads) { onChange(s.copy(printHeads = it)) }
    Help(tr("The number (“Prologue”, “Chapter 1”) in spaced capitals with the title in italics below. Off, the book's own style is used."))
    SwitchRow(tr("Large first letter"), s.dropCaps) { onChange(s.copy(dropCaps = it)) }
    Help(tr("The first letter of each chapter spans three lines."))
}

/** Taps, buttons, the screen, and looking words up. */
@Composable
private fun ControlSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading(tr("Tap to turn pages"), first = true)
    RadioChoices(TapZones.entries, s.taps, { it.label }) { onChange(s.copy(taps = it)) }
    Help(listOf(s.taps.help, tr("Tap the middle of the page to open the menu.")).filter { it.isNotEmpty() }.joinToString(" "))
    if (s.taps != TapZones.OFF) {
        Heading(tr("Tap zone size"))
        Choices(TapWidth.entries, s.tapWidth, { it.label }) { onChange(s.copy(tapWidth = it)) }
    }

    Spacer(Modifier.height(10.dp))
    SwitchRow(tr("Volume keys turn pages"), s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
    SwitchRow(tr("Keep screen on"), s.keepScreenOn) { onChange(s.copy(keepScreenOn = it)) }
    SwitchRow(tr("Edge swipes open panels"), s.edgePanels) { onChange(s.copy(edgePanels = it)) }
    Help(
        if (s.edgePanels) tr("Swipe from the left edge for contents, or the right for settings. Off, both edges go back.")
        else tr("Both edges go back. Contents and settings are in the menu.")
    )
    if (!s.edgePanels) {
        SwitchRow(tr("KoLib's back gesture"), s.edgeBack) { onChange(s.copy(edgeBack = it)) }
        Help(
            if (s.edgeBack) tr("Swipe from either edge and let go when the arrow fills in.")
            else tr("Android's back gesture is used.")
        )
    }

    Heading(tr("Screen rotation"))
    Choices(ReaderRotation.entries, s.rotation, { it.label }) { onChange(s.copy(rotation = it)) }
    Help(s.rotation.help)

    Heading(tr("Dictionary"))
    val key = s.dictionary
    if (key == null) {
        Help(tr("You'll be asked which app to use each time."))
    } else {
        val context = LocalContext.current
        val app = remember(key) { dev.kolib.app.reader.LookUp.app(context, key) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (app != null) tr("Words are looked up in {0}.", app.label) else tr("The dictionary app you chose is no longer installed."),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange(s.copy(dictionary = null)) }) { Text(tr("Ask each time")) }
        }
    }
}

/** How fast autoscroll goes, and what it does at a chapter's end and when the page is touched. */
@Composable
private fun AutoscrollSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Help(
        (if (s.scrolling) tr("Scrolls the page for you at your reading speed.") else tr("Turns the page for you at your reading speed.")) +
            tr(" To start: tap the middle of the page, then tap Autoscroll.")
    )
    Heading(tr("Speed"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange(s.copy(autoWpm = (s.autoWpm - 10).coerceAtLeast(ReaderSettings.MIN_WPM))) },
            enabled = s.autoWpm > ReaderSettings.MIN_WPM,
        ) { Icon(Icons.Outlined.Remove, tr("Slower")) }
        Slider(
            value = s.autoWpm.toFloat(),
            onValueChange = { onChange(s.copy(autoWpm = ((it / 10f).roundToInt() * 10).coerceIn(ReaderSettings.MIN_WPM, ReaderSettings.MAX_WPM))) },
            valueRange = ReaderSettings.MIN_WPM.toFloat()..ReaderSettings.MAX_WPM.toFloat(),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange(s.copy(autoWpm = (s.autoWpm + 10).coerceAtMost(ReaderSettings.MAX_WPM))) },
            enabled = s.autoWpm < ReaderSettings.MAX_WPM,
        ) { Icon(Icons.Outlined.Add, tr("Faster")) }
    }
    Text(tr("{0} words per minute", s.autoWpm), style = MaterialTheme.typography.bodyMedium)
    Help(tr("Most people read 200 to 300 words per minute."))

    Heading(tr("At the end of a chapter"))
    RadioChoices(AutoEnd.entries, s.autoEnd, { it.label }) { onChange(s.copy(autoEnd = it)) }

    Heading(tr("When you touch the page"))
    RadioChoices(AutoResume.entries, s.autoResume, { it.label }) { onChange(s.copy(autoResume = it)) }
    Help(tr("Opening the menu always pauses it."))
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onPick(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { FittedText(label(o), style = MaterialTheme.typography.labelMedium, smallest = 0.7f) }
        }
    }
}

/** Choices with longer names, one under another. */
@Composable
private fun <T> RadioChoices(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        options.forEach { o ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = o == selected, onClick = { onPick(o) })
                Text(label(o), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun NoteSheet(html: String, onClose: () -> Unit, onGoTo: () -> Unit) {
    val text = remember(html) { runCatching { AnnotatedString.fromHtml(html) }.getOrElse { AnnotatedString(html) } }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { onClose() } })
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 420.dp),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(tr("Note"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onGoTo) { Text(tr("Go to the note")) }
                    TextButton(onClick = onClose) { Text(tr("Close")) }
                }
            }
        }
    }
}

/** A picture from the page, full screen: pinch or double-tap to zoom, and turn it if it's on its side. */
@Composable
private fun ImageViewer(controller: ReaderController, src: String, onClose: () -> Unit) {
    val parts = remember(src) { src.split('\n') }
    // A picture of its own (not the two halves of a map) can be turned: one printed on its side to fit a page.
    val path = remember(src) { if (parts.size >= 2) null else BookServer.pathOf(src)?.first }
    val turned = path?.let { controller.turns[it] } ?: 0
    // Null while it's read; a failure if it can't be.
    val bitmap by produceState<Result<ImageBitmap>?>(null, src, turned) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                checkNotNull(
                    if (parts.size >= 2) joinedHalves(controller, parts)?.asImageBitmap()
                    else path?.let { controller.pictureBytes(it) }?.let { viewerBitmap(it) }?.asImageBitmap()
                )
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ZoomablePicture(bitmap, src to turned, onZoomed = {}, onTapOutside = onClose)
        ViewerButtons(controller, path, onClose)
    }
}

/**
 * The book's pictures, full screen, from the list of them: swipe left or right for the next or the one before
 * (when it isn't zoomed in). Turn the one shown, or go to its page.
 */
@Composable
private fun PictureBrowser(controller: ReaderController, browse: ReaderController.Browse, onClose: () -> Unit, onGoTo: (ReaderController.BookPicture) -> Unit) {
    val pictures = browse.pictures
    if (pictures.isEmpty()) return
    val pager = rememberPagerState(initialPage = browse.start.coerceIn(0, pictures.lastIndex)) { pictures.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        controller.browsedTo = pager.currentPage
        zoomed = false
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            pager, Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { pictures[it].path },
        ) { page ->
            val p = pictures[page]
            val turned = controller.turns[p.path] ?: 0
            val bitmap by produceState<Result<ImageBitmap>?>(null, p.path, turned) {
                value = withContext(Dispatchers.IO) { runCatching { checkNotNull(controller.pictureBytes(p.path)?.let { viewerBitmap(it) }).asImageBitmap() } }
            }
            ZoomablePicture(bitmap, p.path to turned, onZoomed = { if (page == pager.currentPage) zoomed = it }, onTapOutside = onClose)
        }
        val current = pictures[pager.currentPage.coerceIn(0, pictures.lastIndex)]
        ViewerButtons(controller, current.path, onClose)
        TextButton(
            onClick = { onGoTo(current) },
            colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        ) {
            Text(tr("Go to its page"))
        }
    }
}

/**
 * A picture fitted to the screen, to pinch or double-tap to zoom into and drag about; [onZoomed] says when it's
 * zoomed in (and so, in a row of pictures, a drag moves it rather than going to the next). [key]: a new picture.
 * A tap beside the picture (or anywhere, on one that can't be shown) is [onTapOutside], as the close button.
 */
@Composable
internal fun ZoomablePicture(bitmap: Result<ImageBitmap>?, key: Any, onZoomed: (Boolean) -> Unit, onTapOutside: () -> Unit = {}) {
    val shown by rememberUpdatedState(bitmap)
    val tappedOutside by rememberUpdatedState(onTapOutside)
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offset by remember(key) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
        onZoomed(scale > 1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(key) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = androidx.compose.ui.geometry.Offset.Zero
                        onZoomed(scale > 1f)
                    },
                    onTap = { at ->
                        val result = shown ?: return@detectTapGestures
                        val p = result.getOrNull()
                        if (p == null || p.width <= 0 || p.height <= 0) return@detectTapGestures tappedOutside()
                        // Where the picture is: fitted to the screen, then zoomed about its middle and moved.
                        val fit = minOf(size.width / p.width.toFloat(), size.height / p.height.toFloat()) * scale
                        val halfW = p.width * fit / 2f
                        val halfH = p.height * fit / 2f
                        val cx = size.width / 2f + offset.x
                        val cy = size.height / 2f + offset.y
                        if (kotlin.math.abs(at.x - cx) > halfW || kotlin.math.abs(at.y - cy) > halfH) tappedOutside()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val picture = bitmap?.getOrNull()
        if (bitmap != null && picture == null) {
            Text(tr("This picture can't be shown."), color = Color.White)
        } else if (picture != null) {
            Image(
                picture,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // A drag only moves the picture about when it's zoomed in; else it's left to what's round it.
                    .transformable(transform, canPan = { scale > 1f })
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}

/** What can be done with a picture held down on the page: open it, share it, or save it to the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeldPicture(controller: ReaderController, src: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val path = remember(src) { BookServer.pathOf(src)?.first } ?: return onDone()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun withBytes(work: suspend (ByteArray) -> Unit) {
        onDone()
        scope.launch { withContext(Dispatchers.IO) { controller.pictureBytes(path) }?.let { work(it) } }
    }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet) {
        Text(tr("Picture"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text(tr("Open")) },
            leadingContent = { Icon(Icons.Outlined.ZoomIn, null) },
            modifier = Modifier.clickable { onDone(); controller.image = src },
        )
        ListItem(
            headlineContent = { Text(tr("Share")) },
            leadingContent = { Icon(Icons.Outlined.Share, null) },
            modifier = Modifier.clickable { withBytes { SavedPictures.share(context, it, path, controller.bookTitle) } },
        )
        ListItem(
            headlineContent = { Text(tr("Save to Pictures")) },
            leadingContent = { Icon(Icons.Outlined.Save, null) },
            modifier = Modifier.clickable { withBytes { SavedPictures.save(context, it, path, controller.bookTitle) } },
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Over a picture shown full screen: turning it, sharing and saving it (if it's a picture of its own, at [path]), and closing. */
@Composable
private fun BoxScope.ViewerButtons(controller: ReaderController, path: String?, onClose: () -> Unit) {
    // The buttons sit on a dark round backing, so they show over a pale picture too.
    val backing = Color.Black.copy(alpha = 0.5f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (path != null) {
            fun withBytes(work: suspend (ByteArray) -> Unit) {
                scope.launch { withContext(Dispatchers.IO) { controller.pictureBytes(path) }?.let { work(it) } }
            }
            IconButton(modifier = Modifier.clip(CircleShape).background(backing), onClick = { withBytes { SavedPictures.share(context, it, path, controller.bookTitle) } }) {
                Icon(Icons.Outlined.Share, tr("Share"), tint = Color.White)
            }
            IconButton(modifier = Modifier.clip(CircleShape).background(backing), onClick = { withBytes { SavedPictures.save(context, it, path, controller.bookTitle) } }) {
                Icon(Icons.Outlined.Save, tr("Save to Pictures"), tint = Color.White)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(backing)) {
            Icon(Icons.Filled.Close, tr("Close picture"), tint = Color.White)
        }
    }
    if (path != null) {
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(modifier = Modifier.clip(CircleShape).background(backing), onClick = { controller.turnPicture(path, -1) }) {
                Icon(Icons.Filled.RotateLeft, tr("Turn the picture left"), tint = Color.White)
            }
            IconButton(modifier = Modifier.clip(CircleShape).background(backing), onClick = { controller.turnPicture(path, 1) }) {
                Icon(Icons.Filled.RotateRight, tr("Turn the picture right"), tint = Color.White)
            }
        }
    }
}

/** A picture's bytes as a bitmap to show full screen: a very large one made smaller (no side over 4096), so it can be drawn. */
private fun viewerBitmap(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * The two halves of a map across two pages ([parts]: their addresses, then how much of each was left off
 * at the join, as reader.js found it), joined into one picture as the reader shows them: the same height,
 * side by side. Null if either can't be read.
 */
private fun joinedHalves(controller: ReaderController, parts: List<String>): android.graphics.Bitmap? = runCatching {
    val (a, b) = parts.take(2).map { src ->
        BookServer.pathOf(src)?.first?.let { controller.files.bytes(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: return null
    }
    val cutA = parts.getOrNull(2)?.toFloatOrNull()?.coerceIn(0f, 0.2f) ?: 0f
    val cutB = parts.getOrNull(3)?.toFloatOrNull()?.coerceIn(0f, 0.2f) ?: 0f
    val h = maxOf(a.height, b.height)
    val keptA = (a.width * (1 - cutA)).toInt().coerceAtLeast(1)
    val keptB = (b.width * (1 - cutB)).toInt().coerceAtLeast(1)
    val wa = (keptA.toFloat() * h / a.height).toInt().coerceAtLeast(1)
    val wb = (keptB.toFloat() * h / b.height).toInt().coerceAtLeast(1)
    val out = android.graphics.Bitmap.createBitmap(wa + wb, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(a, android.graphics.Rect(0, 0, keptA, a.height), android.graphics.Rect(0, 0, wa, h), paint)
    canvas.drawBitmap(b, android.graphics.Rect(b.width - keptB, 0, b.width, b.height), android.graphics.Rect(wa, 0, wa + wb, h), paint)
    a.recycle()
    b.recycle()
    out
}.getOrNull()

/** The tablet's own brightness setting as 0 to 1, to start a swipe from (half, if it can't be read). */
private fun systemBrightness(context: android.content.Context): Float =
    runCatching { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f }
        .getOrDefault(0.5f).coerceIn(0.01f, 1f)

/** The battery's charge and whether it's charging, or null if the tablet doesn't say. */
private fun batteryNow(context: android.content.Context): Pair<Int, Boolean>? =
    context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))?.let { i ->
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val charging = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1).let { st ->
            st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
        }
        if (level >= 0 && scale > 0) (level * 100 / scale) to charging else null
    }

/** One thing in a margin: some words, or the battery (an icon and its charge). */
private sealed interface MarginPiece {
    data class Words(val text: String, val stretch: Boolean = false) : MarginPiece
    data class Battery(val percent: Int, val charging: Boolean) : MarginPiece
}

private fun marginPiece(item: MarginItem, c: ReaderController, time: String, battery: Pair<Int, Boolean>?): MarginPiece? = when (item) {
    MarginItem.BOOK -> MarginPiece.Words(c.headBookTitle, stretch = true)
    MarginItem.CHAPTER -> c.chapterTitle.takeIf { it.isNotBlank() }?.let { MarginPiece.Words(it, stretch = true) }
    MarginItem.PAGE -> if (c.chapterPages > 1) MarginPiece.Words(tr("{0} of {1}", c.pagesLabel(c.firstShown + 1, c.lastShown + 1), c.chapterPages)) else null
    MarginItem.BOOK_PAGE -> c.bookPage?.let { n -> c.bookPages?.let { MarginPiece.Words(tr("{0} of {1}", c.pagesLabel(n, c.bookPageLast), it.total)) } }
    MarginItem.PAGES_LEFT -> if (c.chapterPages > 1) (c.chapterPages - c.lastShown - 1).let { n ->
        MarginPiece.Words(when (n) { 0 -> tr("Last page in chapter"); 1 -> tr("1 page left"); else -> tr("{0} pages left", n) })
    } else null
    MarginItem.TIME_LEFT -> if (c.chapterWordsKnown) c.minutesLeftInChapter.let { m ->
        MarginPiece.Words(if (m < 1) tr("Under a minute left") else tr("{0} min left in chapter", m))
    } else null
    MarginItem.CLOCK -> MarginPiece.Words(time)
    MarginItem.BATTERY -> battery?.let { MarginPiece.Battery(it.first, it.second) }
    MarginItem.PERCENT -> MarginPiece.Words("${(c.bookProgress * 100).roundToInt()}%")
}

private fun MarginSpot.alignment(): Alignment = when (this) {
    MarginSpot.TOP_LEFT -> Alignment.TopStart
    MarginSpot.TOP_CENTER -> Alignment.TopCenter
    MarginSpot.TOP_RIGHT -> Alignment.TopEnd
    MarginSpot.BOTTOM_LEFT -> Alignment.BottomStart
    MarginSpot.BOTTOM_CENTER -> Alignment.BottomCenter
    else -> Alignment.BottomEnd
}

/** What the margins show while reading, each thing in the place it's been given. The time is kept up to date each minute. */
@Composable
private fun BoxWithConstraintsScope.Margins(controller: ReaderController, s: ReaderSettings, color: Color) {
    val context = LocalContext.current
    val clocked = s.shownSpotOf(MarginItem.CLOCK) != MarginSpot.OFF || s.shownSpotOf(MarginItem.BATTERY) != MarginSpot.OFF
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    if (clocked) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
                now = System.currentTimeMillis()
            }
        }
    }
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    val battery = remember(now, clocked) { if (clocked) batteryNow(context) else null }
    val style = MaterialTheme.typography.labelMedium
    // The top row sits beside a camera in the top of the screen when it can (a corner's things moving in from one
    // in that corner), and below it when the camera would be over something.
    val cut = controller.cutouts
    val rowTop = s.topRowAt(cut)
    val beside = !s.cameraInTheWay(cut)
    for (spot in MarginSpot.entries) {
        if (spot == MarginSpot.OFF) continue
        val pieces = MarginItem.entries.filter { s.shownSpotOf(it) == spot }.mapNotNull { marginPiece(it, controller, time, battery) }
        if (pieces.isEmpty()) continue
        val inset = (if (spot.top && beside) s.topInset(spot, cut) else null) ?: ReaderSettings.SIDE
        Row(
            Modifier
                .align(spot.alignment())
                .padding(start = if (spot.align < 0) inset.dp else ReaderSettings.SIDE.dp, end = if (spot.align > 0) inset.dp else ReaderSettings.SIDE.dp)
                .padding(top = if (spot.top) rowTop.dp else 0.dp, bottom = if (spot.top) 0.dp else bottomItemsInset(s, cut))
                .widthIn(max = maxWidth * (if (spot.align == 0) 0.4f else 0.28f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pieces.forEachIndexed { i, piece ->
                if (i > 0) Text("  ·  ", color = color, style = style)
                when (piece) {
                    is MarginPiece.Words -> Text(
                        piece.text,
                        color = color,
                        style = style,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (piece.stretch) Modifier.weight(1f, fill = false) else Modifier,
                    )
                    is MarginPiece.Battery -> {
                        val pct = piece.percent
                        val icon = when {
                            piece.charging -> Icons.Outlined.BatteryChargingFull
                            pct >= 95 -> Icons.Outlined.BatteryFull
                            pct >= 80 -> Icons.Outlined.Battery6Bar
                            pct >= 65 -> Icons.Outlined.Battery5Bar
                            pct >= 50 -> Icons.Outlined.Battery4Bar
                            pct >= 35 -> Icons.Outlined.Battery3Bar
                            pct >= 20 -> Icons.Outlined.Battery2Bar
                            pct >= 8 -> Icons.Outlined.Battery1Bar
                            else -> Icons.Outlined.Battery0Bar
                        }
                        Icon(icon, tr("Battery"), tint = color, modifier = Modifier.size(14.dp).rotate(90f))
                        Spacer(Modifier.width(3.dp))
                        Text("$pct%", color = color, style = style)
                    }
                }
            }
        }
    }
}

/** The page headings' type: Garamond's small capitals, spaced out, and its old-style figures for the numbers. */
private val HeadTitle = TextStyle(
    fontFamily = Garamond,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.14.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val HeadNumber = HeadTitle.copy(letterSpacing = 0.04.em, fontFeatureSettings = "onum, pnum")

/**
 * The page headings, as a printed book has them: over (or under) each page on screen, its number at the
 * outside corner, and the book's title over a left-hand page or the chapter's over a right-hand one, in
 * line with the page's text. A chapter's first page has its number but not the title, its own heading
 * being right there.
 */
@Composable
private fun BoxWithConstraintsScope.PageHeads(controller: ReaderController, s: ReaderSettings, ink: Color) {
    val pages = controller.shownPages
    if (pages.isEmpty()) return
    val frame = controller.frame?.takeIf { it.perScreen == controller.perScreen }
        ?: PageFrame.of(maxWidth.value, maxHeight.value, s, s.marginRoom(controller.cutouts))
    val top = s.runningHeads.top
    val row = s.headRow
    // Over (or under) the text, the space chosen away from it, as in print; never under the camera, nor
    // nearer the edge than the margins' rows.
    val inset = if (top) {
        maxOf(s.topRowAt(controller.cutouts).toFloat(), frame.padTop - s.headGapDp - row)
    } else {
        maxOf(bottomItemsInset(s, controller.cutouts).value, frame.padBottom - s.headGapDp - row)
    }
    val color = ink.copy(alpha = 0.72f)
    val size = s.headFontSize.sp
    val titleStyle = HeadTitle.copy(
        fontSize = size, lineHeight = size * 1.2f,
        letterSpacing = if (s.headSize == HeadSize.SMALL) HeadTitle.letterSpacing else 0.18.em,
    )
    val numberStyle = HeadNumber.copy(fontSize = size, lineHeight = size * 1.2f)
    for (p in pages) {
        val title = when {
            p.opening -> ""
            p.side == PageSide.LEFT -> controller.headBookTitle
            else -> controller.headChapterTitle.ifBlank { controller.headBookTitle }
        }.let { HeadTitles.headingCase(it) }
        // A chapter's first page has its number at the foot, as in print: at the outside corner of two pages side
        // by side, and at the right of a page on its own. Not where something else is shown in that corner, nor
        // where no room is kept at the foot.
        val footRight = controller.perScreen == 1 || p.side != PageSide.LEFT
        val footSpot = if (footRight) MarginSpot.BOTTOM_RIGHT else MarginSpot.BOTTOM_LEFT
        val numberAtFoot = p.opening && top
        val footShown = numberAtFoot && frame.padBottom >= bottomItemsInset(s, controller.cutouts).value + row &&
            MarginItem.entries.none { s.shownSpotOf(it) == footSpot }
        Box(
            Modifier
                .align(if (top) Alignment.TopStart else Alignment.BottomStart)
                .offset(x = frame.textLeft(p.slot).dp)
                .padding(top = if (top) inset.dp else 0.dp, bottom = if (top) 0.dp else inset.dp)
                .width(frame.col.dp)
                .height(row.dp),
        ) {
            if (!numberAtFoot) p.number?.let { n ->
                Text(
                    n.toString(), style = numberStyle, color = color, maxLines = 1,
                    modifier = Modifier.align(if (p.side == PageSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd),
                )
            }
            if (title.isNotBlank()) HeadTitleLine(
                title, p.side, s.headTitlePlace, color,
                style = titleStyle.copy(fontFeatureSettings = HeadTitles.smallCapsFeatures(title)),
                clearOfNumber = maxOf(46f, s.headFontSize * 3.2f).dp,
            )
        }
        if (footShown) p.number?.let { n ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = frame.textLeft(p.slot).dp)
                    .padding(bottom = bottomItemsInset(s, controller.cutouts))
                    .width(frame.col.dp)
                    .height(row.dp),
            ) {
                Text(
                    n.toString(), style = numberStyle, color = color, maxLines = 1,
                    modifier = Modifier.align(if (footRight) Alignment.CenterEnd else Alignment.CenterStart),
                )
            }
        }
    }
}

/**
 * A page heading's title, along its page: in the middle, clear of the number at either end; or toward the
 * spine, in the middle of the page's inner half, moving out only as far as a longer title needs.
 */
@Composable
private fun HeadTitleLine(
    title: String,
    side: PageSide,
    place: HeadTitlePlace,
    color: Color,
    style: TextStyle = HeadTitle.copy(fontFeatureSettings = HeadTitles.smallCapsFeatures(title)),
    clearOfNumber: androidx.compose.ui.unit.Dp = 46.dp,
) {
    Layout(
        content = {
            Text(
                title,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val clear = clearOfNumber.roundToPx()
        val spine = place == HeadTitlePlace.SPINE
        // Centred, it keeps clear of a number at both ends; toward the spine, only at the outside one.
        val room = (if (spine) w - clear else w - 2 * clear).coerceAtLeast(0)
        val text = measurables.first().measure(Constraints(maxWidth = room, maxHeight = h))
        layout(w, h) {
            val x = if (!spine) (w - text.width) / 2 else {
                val left = side == PageSide.LEFT
                val middle = if (left) w * 0.75f else w * 0.25f
                val lo = if (left) clear else 0
                val hi = (if (left) w - text.width else w - clear - text.width).coerceAtLeast(lo)
                (middle - text.width / 2f).roundToInt().coerceIn(lo, hi)
            }
            text.place(x, (h - text.height) / 2)
        }
    }
}

/** Scrolling, there are no pages to head: the chapter's title, in the headings' band, as they'd have it. */
@Composable
private fun BoxWithConstraintsScope.ScrollHead(controller: ReaderController, s: ReaderSettings, ink: Color) {
    val title = controller.headChapterTitle.ifBlank { controller.headBookTitle }
    if (title.isBlank()) return
    val top = s.runningHeads.top
    Text(
        title,
        style = HeadTitle.copy(fontFeatureSettings = HeadTitles.smallCapsFeatures(title)),
        color = ink.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(horizontal = 24.dp)
            .padding(top = if (top) (s.topRowAt(controller.cutouts) - 2).dp else 0.dp, bottom = if (top) 0.dp else bottomItemsInset(s, controller.cutouts))
            .widthIn(max = maxWidth * 0.6f),
    )
}

/** A little page with each margin item where it's been put, as a guide to the choices under it. */
@Composable
private fun MarginPreview(s: ReaderSettings) {
    val paper = Color(s.theme.bg)
    val ink = Color(s.theme.fg)
    val bar = s.progressLine != ProgressLine.OFF
    Box(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(paper)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        // The text of the page, as grey lines: two pages side by side when they have headings, to show them as a book has them.
        val heads = s.runningHeads != RunningHeads.OFF
        // The text nearer the headings or further from them, as the space under them is chosen.
        val nearer = when (s.headGap) { HeadGap.CLOSE -> -6; HeadGap.LINE -> 0; HeadGap.MORE -> 6 }.let { if (s.runningHeads.top) it else -it }
        Row(
            Modifier.align(Alignment.Center).offset(y = if (heads) nearer.dp else 0.dp).fillMaxWidth().padding(horizontal = if (heads) 18.dp else 28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            repeat(if (heads) 2 else 1) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(6) { i -> Box(Modifier.fillMaxWidth(if (i == 5) 0.6f else 1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.14f))) }
                }
            }
        }
        if (heads) {
            val size = when (s.headSize) { HeadSize.SMALL -> 8.5f; HeadSize.PRINT -> 10f; HeadSize.LARGE -> 11.5f }
            val headStyle = HeadTitle.copy(fontSize = size.sp, lineHeight = (size + 2.5f).sp, letterSpacing = 0.08.em, fontFeatureSettings = "smcp, onum")
            val numberStyle = HeadNumber.copy(fontSize = (size + 0.5f).sp, lineHeight = (size + 2.5f).sp)
            Row(
                Modifier
                    .align(if (s.runningHeads.top) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = if (s.runningHeads.top) 7.dp else 0.dp, bottom = if (s.runningHeads.top) 0.dp else if (bar) 8.dp else 5.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                listOf(Triple("44", tr("The Eye of the World"), true), Triple("45", tr("The Hook"), false)).forEach { (n, t, left) ->
                    Box(Modifier.weight(1f).height((size + 4f).dp)) {
                        Text(n, style = numberStyle, color = ink.copy(alpha = 0.7f), modifier = Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd))
                        HeadTitleLine(
                            t, if (left) PageSide.LEFT else PageSide.RIGHT, s.headTitlePlace, ink.copy(alpha = 0.7f),
                            style = headStyle, clearOfNumber = 13.dp,
                        )
                    }
                }
            }
        }
        if (bar) {
            ProgressBarLine(0.35f, Color(s.barColor), Modifier.align(Alignment.BottomStart).padding(bottom = 3.dp))
        }
        for (spot in MarginSpot.entries) {
            if (spot == MarginSpot.OFF) continue
            val words = MarginItem.entries.filter { s.copy(flow = ReaderFlow.PAGES).shownSpotOf(it) == spot }.joinToString("  ·  ") { it.example }
            if (words.isEmpty()) continue
            Text(
                words,
                color = ink.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = when (spot.align) { -1 -> TextAlign.Start; 1 -> TextAlign.End; else -> TextAlign.Center },
                modifier = Modifier
                    .align(spot.alignment())
                    .padding(horizontal = 10.dp)
                    .padding(top = if (spot.top) 6.dp else 0.dp, bottom = if (spot.top) 0.dp else if (bar) 6.dp else 3.dp)
                    .fillMaxWidth(if (spot.align == 0) 0.4f else 0.3f),
            )
        }
    }
}

/** One margin item and the place it's in, with the places to move it to. */
@Composable
private fun MarginRow(item: MarginItem, spot: MarginSpot, taken: (MarginSpot) -> Boolean = { false }, onPick: (MarginSpot) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(spot.label, color = if (spot == MarginSpot.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                MarginSpot.entries.filterNot(taken).forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o.label) },
                        leadingIcon = { if (o == spot) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { open = false; onPick(o) },
                    )
                }
            }
        }
    }
}
