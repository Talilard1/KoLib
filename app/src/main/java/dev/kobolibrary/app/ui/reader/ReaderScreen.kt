package dev.kobolibrary.app.ui.reader

import dev.kobolibrary.app.ui.components.phoneLayout
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kobolibrary.app.reader.PictureBlend
import dev.kobolibrary.app.reader.TapWidth
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import dev.kobolibrary.app.reader.MarginSpot
import dev.kobolibrary.app.reader.MarginItem
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import dev.kobolibrary.app.reader.HeadTitles
import dev.kobolibrary.app.reader.MarginRoom
import dev.kobolibrary.app.reader.PageFrame
import dev.kobolibrary.app.reader.PageSide
import dev.kobolibrary.app.reader.RunningHeads
import dev.kobolibrary.app.reader.HeadTitlePlace
import dev.kobolibrary.app.reader.CollectionTitle
import dev.kobolibrary.app.ui.theme.Garamond
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
import dev.kobolibrary.app.epub.Contents
import dev.kobolibrary.app.reader.BookServer
import dev.kobolibrary.app.reader.PageTurn
import dev.kobolibrary.app.reader.ReaderFlow
import dev.kobolibrary.app.reader.ProgressLine
import dev.kobolibrary.app.reader.BarShade
import dev.kobolibrary.app.reader.AutoEnd
import dev.kobolibrary.app.reader.AutoResume
import dev.kobolibrary.app.reader.TapZones
import dev.kobolibrary.app.reader.ReaderRotation
import dev.kobolibrary.app.reader.ReaderFrame
import dev.kobolibrary.app.reader.ReaderAlign
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReaderFont
import dev.kobolibrary.app.reader.ReaderHit
import dev.kobolibrary.app.reader.ReaderHyphens
import dev.kobolibrary.app.reader.ReaderKeys
import dev.kobolibrary.app.reader.ReaderMargins
import dev.kobolibrary.app.reader.ReaderPosition
import dev.kobolibrary.app.reader.ReaderSettings
import dev.kobolibrary.app.reader.ReaderSpread
import dev.kobolibrary.app.reader.ReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class Panel { NONE, CONTENTS, SEARCH, LOOK }

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
    /** Shown instead of the page in screenshots, where a WebView can't draw. */
    pagePreview: (@Composable () -> Unit)? = null,
    initialPanel: Panel = Panel.NONE,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val s = controller.settings
    var panel by remember { mutableStateOf(initialPanel) }
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
    // The camera's cutout at the top of the screen: a row of headings at the top goes below it.
    val cutoutTop = with(LocalDensity.current) { (WindowInsets.displayCutout.getTop(this) / density).roundToInt() }
    SideEffect { controller.setCutoutTop(cutoutTop) }
    LaunchedEffect(Unit) { controller.start() }

    // Going back closes what's over the page, else the book: from Android's back, or KoLib's own back swipe.
    fun goBack() {
        when {
            controller.openAnnotation != null -> controller.openAnnotation = null
            panel != Panel.NONE -> panel = Panel.NONE
            controller.note != null -> controller.note = null
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
    fun pageClear(): Boolean = panel == Panel.NONE && controller.note == null && controller.image == null &&
        controller.externalLink == null && !controller.atEnd && controller.openAnnotation == null && controller.editingNote == null &&
        controller.lookUpText == null
    BoxWithConstraints(Modifier.fillMaxSize().background(pageColor).edgeBackSwipe(edgeBack, backSwipe) { goBack() }) {
        val wide = maxWidth >= 720.dp
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
                    frame.sideSwipes = s.edgePanels && pageClear()
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
            if (s.scrolling) ScrollBands(s, pageColor)
            if (s.headsShown) PageHeads(controller, s, inkColor)
            if (s.scrolling && s.runningHeads != RunningHeads.OFF) ScrollHead(controller, s, inkColor)
            Margins(controller, s, inkColor.copy(alpha = 0.5f))
            if (s.progressLine != ProgressLine.OFF) ProgressLineBar(controller, s)
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
                        if (v < 0.01f) "Extra dim ${((-v / ReaderSettings.MAX_DIM) * 100).roundToInt().coerceAtLeast(1)}%" else "Brightness ${(v * 100).roundToInt()}%",
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
                        Text("Back to ${from.chapterTitle.ifBlank { "where you were" }.take(40)}")
                    }
                    IconButton(onClick = { controller.jumpedFrom = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss", Modifier.size(16.dp))
                    }
                }
            }
        }

        // Autoscroll waits while something is shown over the page, the controls included.
        val overPage = controller.controlsVisible || !pageClear()
        LaunchedEffect(overPage, controller.loading) { controller.holdAutoscroll(overPage) }

        // The controls.
        AnimatedVisibility(controller.controlsVisible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }, modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                controller = controller,
                onClose = onClose,
                onContents = { panel = Panel.CONTENTS },
                onSearch = { panel = Panel.SEARCH },
                onLook = { panel = Panel.LOOK },
                onEdit = onEdit?.let { edit -> { controller.chapter?.let { edit(it.path) } } },
            )
        }
        AnimatedVisibility(controller.controlsVisible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(controller, onSettings)
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
            when (panel) {
                Panel.CONTENTS -> ContentsPanel(controller, wide, onPicked = { panel = Panel.NONE; controller.controlsVisible = false }, onClose = dismiss)
                Panel.SEARCH -> SearchPanel(controller, wide, onPicked = { panel = Panel.NONE; controller.controlsVisible = false }, onClose = dismiss)
                Panel.LOOK -> LookPanel(s, onSettings, controller.lookTab, wide, onTab = { controller.lookTab = it }, onClose = dismiss)
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
        // Highlights: the card for a tapped one, writing a note, looking words up.
        AnnotationCard(controller, s, onSettings)
        NoteDialog(controller)
        LookUpSheet(controller, s, onSettings, controller.bookLanguage)
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
                title = { Text("Open this link?") },
                text = { Text("This link opens a web page outside the book:\n\n$url") },
                confirmButton = {
                    TextButton(onClick = {
                        controller.externalLink = null
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }) { Text("Open") }
                },
                dismissButton = { TextButton(onClick = { controller.externalLink = null }) { Text("Cancel") } },
            )
        }
        if (controller.atEnd) {
            AlertDialog(
                onDismissRequest = { controller.dismissEnd() },
                title = { Text("The end") },
                text = { Text("You've reached the end of “${controller.bookTitle}”.") },
                confirmButton = { TextButton(onClick = { controller.dismissEnd(); onClose() }) { Text("Back to library") } },
                dismissButton = { TextButton(onClick = { controller.dismissEnd() }) { Text("Stay on this page") } },
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
    onEdit: (() -> Unit)?,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close the book") }
            // The contents on the left, where their panel opens.
            IconButton(onClick = onContents) { Icon(Icons.AutoMirrored.Filled.List, "Contents and bookmarks") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(controller.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                controller.chapterTitle.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search the book") }
            val marked = controller.currentBookmark != null
            IconButton(onClick = { controller.toggleBookmark() }) {
                Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (marked) "Remove bookmark" else "Bookmark this page",
                    tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onLook) { Icon(Icons.Outlined.Settings, "Reading settings") }
            if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.EditNote, "Edit this chapter's text") }
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
                IconButton(onClick = { controller.previousChapter() }) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous chapter") }
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
                IconButton(onClick = { controller.nextChapter() }) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next chapter") }
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
                    Text("Page $shown of $total", style = footStyle, color = footColor, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("  ·  ", style = footStyle, color = footColor, maxLines = 1)
                    Text(
                        controller.chapterTitleAt(value), style = footStyle, color = footColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val details = if (dragging) "Go to ${(value * 100).roundToInt()}%" else listOfNotNull(
                    controller.chapterTitle.ifBlank { null },
                    controller.bookPage?.let { "${controller.pagesLabel(it, controller.bookPageLast)} of ${book?.total}" }
                        ?: if (controller.chapterPages > 1) "${controller.pagesLabel(controller.firstShown + 1, controller.lastShown + 1)} of ${controller.chapterPages} in chapter" else null,
                    // The book's page numbers show once every chapter is counted, so they never change.
                    if (controller.countingPages && book == null) "Counting pages…" else null,
                    controller.minutesLeftInChapter.takeIf { it > 0 }?.let { "$it min left in chapter" },
                    "${(controller.bookProgress * 100).roundToInt()}% of book read",
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
    val label = controller.barReturnBookPage?.let { "Back to page $it" } ?: "Back to ${(back.bookProgress * 100).roundToInt()}%"
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
                Text(if (on) "Pause autoscroll" else "Autoscroll")
            }
            Spacer(Modifier.weight(1f))
            if (phone && back != null) {
                BarReturnButton(controller, back, compact = true)
                Spacer(Modifier.weight(1f))
            }
            if (!phone) Text("Speed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(
                onClick = { onSettings(s.copy(autoWpm = (s.autoWpm - 10).coerceAtLeast(ReaderSettings.MIN_WPM))) },
                enabled = s.autoWpm > ReaderSettings.MIN_WPM,
            ) { Icon(Icons.Outlined.Remove, "Slower") }
            if (phone) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.autoWpm}", style = MaterialTheme.typography.titleSmall)
                    Text("words per minute", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("${s.autoWpm} words per minute", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(
                onClick = { onSettings(s.copy(autoWpm = (s.autoWpm + 10).coerceAtMost(ReaderSettings.MAX_WPM))) },
                enabled = s.autoWpm < ReaderSettings.MAX_WPM,
            ) { Icon(Icons.Outlined.Add, "Faster") }
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
private fun bottomItemsInset(s: ReaderSettings) = if (s.progressLine == ProgressLine.OFF) 5.dp else 10.dp

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
private fun androidx.compose.foundation.layout.BoxScope.ScrollBands(s: ReaderSettings, paper: Color) {
    if (s.bandTop > 0) Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(s.bandTop.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to paper, 0.75f to paper, 1f to paper.copy(alpha = 0f)))
    )
    if (s.bandBottom > 0) Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(s.bandBottom.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.25f to paper, 1f to paper))
    )
}

/**
 * A panel over the page that slides in from the edge it's swiped in from (the contents from the left, the
 * rest from the right), over a shade that closes it when tapped. On a tablet it's a plain 420dp side panel;
 * on a phone, a drawer with its inner corners rounded.
 */
@Composable
private fun ReaderPanel(visible: Boolean, wide: Boolean, width: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit, fromLeft: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (wide) 0.32f else 0.4f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }
        if (wide) {
            AnimatedVisibility(
                visible,
                enter = slideInHorizontally { if (fromLeft) -it else it },
                exit = slideOutHorizontally { if (fromLeft) -it else it },
                modifier = Modifier.align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(420.dp).fillMaxHeight(),
                ) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
                }
            }
        } else {
            AnimatedVisibility(
                visible,
                enter = slideInHorizontally { if (fromLeft) -it else it },
                exit = slideOutHorizontally { if (fromLeft) -it else it },
                modifier = Modifier.align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd),
            ) {
                val corner = 28.dp
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 12.dp,
                    shape = if (fromLeft) RoundedCornerShape(topEnd = corner, bottomEnd = corner) else RoundedCornerShape(topStart = corner, bottomStart = corner),
                    modifier = Modifier
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
}

/** The top of a drawer: its name, a line under it, and a button to close it. */
@Composable
private fun DrawerHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
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
                Text(
                    t.label,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ContentsPanel(controller: ReaderController, wide: Boolean, onPicked: () -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = controller.contentsTab.coerceIn(0, 2)) { 3 }
    SwipedTabs(pager, controller.contentsTab) { controller.contentsTab = it }
    Column(Modifier.fillMaxSize()) {
        if (wide) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
                val scope = rememberCoroutineScope()
                listOf("Contents", "Bookmarks (${controller.bookmarks.size})", "Highlights (${controller.annotations.size})").forEachIndexed { i, label ->
                    Tab(
                        selected = i == pager.currentPage,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }
        } else {
            DrawerHeader(
                controller.bookTitle,
                subtitle = controller.bookAuthor?.takeIf { it.isNotBlank() } ?: "${(controller.bookProgress * 100).roundToInt()}% read",
                onClose = onClose,
            )
            DrawerTabs(
                listOf(
                    DrawerTab("Contents", Icons.Outlined.Toc),
                    DrawerTab("Bookmarks", Icons.Outlined.BookmarkBorder, controller.bookmarks.size),
                    DrawerTab("Highlights", Icons.Outlined.BorderColor, controller.annotations.size),
                ),
                pager,
            )
        }
        // Swipe left or right for the next tab.
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> ContentsList(controller, onPicked)
                1 -> BookmarksList(controller, onPicked)
                else -> HighlightsList(controller, onPicked)
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

/** The bookmarks, in the order of the book. */
@Composable
private fun BookmarksList(controller: ReaderController, onPicked: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (controller.bookmarks.isEmpty()) {
            Text(
                "No bookmarks yet. To bookmark a page, tap the middle of the page, then tap the bookmark icon at the top.",
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
            title.ifBlank { "Untitled" },
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
                "${b.chapterTitle.ifBlank { "Bookmark" }} · ${(b.bookProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (b.snippet.isNotBlank()) {
                Text(b.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Remove bookmark") }
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
            DrawerHeader("Search", subtitle = controller.bookTitle, onClose = onClose)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search in book") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; hits = emptyList() }) { Icon(Icons.Filled.Close, "Clear") } },
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
                searching -> "Searching…"
                query.isBlank() -> "Type a word or phrase to find it in the book."
                hits.isEmpty() -> "No matches found."
                hits.size >= 2000 -> "Showing the first 2,000 matches"
                else -> "${hits.size} match${if (hits.size == 1) "" else "es"}"
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
    TEXT("Text", Icons.Outlined.TextFields),
    PAGE("Page", Icons.Outlined.Palette),
    LAYOUT("Layout", Icons.Outlined.AutoStories),
    CONTROLS("Controls", Icons.Outlined.TouchApp),
    AUTOSCROLL("Autoscroll", Icons.Outlined.Speed),
}

@Composable
private fun LookPanel(s: ReaderSettings, onChange: (ReaderSettings) -> Unit, tab: Int, wide: Boolean, onTab: (Int) -> Unit, onClose: () -> Unit) {
    val tabs = LookTab.entries
    val pager = rememberPagerState(initialPage = tab.coerceIn(0, tabs.size - 1)) { tabs.size }
    SwipedTabs(pager, tab, onTab)
    Column(Modifier.fillMaxSize()) {
        if (wide) {
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
                        Text(
                            t.label,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp),
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        } else {
            DrawerHeader("Reading settings", onClose = onClose)
            DrawerTabs(tabs.map { DrawerTab(it.label, it.icon) }, pager)
        }
        // Swipe left or right for the next tab; each starts at its top.
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
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
    Heading("Font", first = true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        ReaderFont.entries.forEach { f ->
            FilterChip(selected = s.font == f, onClick = { onChange(s.copy(font = f)) }, label = { Text(f.label) })
        }
    }

    Heading("Text size")
    val scales = ReaderSettings.FONT_SCALES
    val at = scales.indexOfFirst { it >= s.fontScale - 0.001f }.let { if (it < 0) scales.size - 1 else it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(s.copy(fontScale = scales[(at - 1).coerceAtLeast(0)])) }, enabled = at > 0) { Icon(Icons.Outlined.Remove, "Smaller") }
        Slider(
            value = at.toFloat(),
            onValueChange = { onChange(s.copy(fontScale = scales[it.roundToInt().coerceIn(0, scales.size - 1)])) },
            valueRange = 0f..(scales.size - 1).toFloat(),
            steps = scales.size - 2,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(s.copy(fontScale = scales[(at + 1).coerceAtMost(scales.size - 1)])) }, enabled = at < scales.size - 1) { Icon(Icons.Outlined.Add, "Bigger") }
        Text("${(s.fontScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }

    Heading("Line spacing")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        ReaderSettings.LINE_SPACINGS.forEach { v ->
            FilterChip(
                selected = s.lineSpacing == v,
                onClick = { onChange(s.copy(lineSpacing = v)) },
                label = { Text(if (v == 0f) "Book default" else "%.1f".format(v)) },
            )
        }
    }

    Heading("Margins")
    Choices(ReaderMargins.entries, s.margins, { it.label }) { onChange(s.copy(margins = it)) }

    Heading("Alignment")
    Choices(ReaderAlign.entries, s.align, { it.label }) { onChange(s.copy(align = it)) }

    Heading("Hyphens at line ends")
    Choices(ReaderHyphens.entries, s.hyphens, { it.label }) { onChange(s.copy(hyphens = it)) }

    Spacer(Modifier.height(14.dp))
    TextButton(onClick = {
        onChange(s.copy(font = ReaderFont.BOOK, fontScale = 1f, lineSpacing = 0f, margins = ReaderMargins.MEDIUM, align = ReaderAlign.BOOK, hyphens = ReaderHyphens.BOOK))
    }) { Text("Reset to book defaults") }
}

/** The page's colour and brightness, its pictures, and what shows around it. */
@Composable
private fun PageSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading("Page colour", first = true)
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
                    Text("Aa", color = Color(t.fg), style = MaterialTheme.typography.labelLarge)
                }
                Text(t.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    Heading("Brightness")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.BrightnessLow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = s.level ?: 0.5f,
            onValueChange = { onChange(s.withLevel(it)) },
            valueRange = -ReaderSettings.MAX_DIM..1f,
            enabled = s.level != null,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Icon(Icons.Outlined.BrightnessHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Help("Slide all the way left to go darker than the screen's lowest setting. You can also swipe up or down the left edge of the page.")
    SwitchRow("Use the system brightness", s.level == null) { system ->
        onChange(if (system) s.copy(brightness = -1f, dim = 0f) else s.copy(brightness = 0.5f, dim = 0f))
    }

    Heading("Match pictures to the page colour")
    Choices(PictureBlend.entries, s.pictures, { it.label }) { onChange(s.copy(pictures = it)) }
    Help(s.pictures.help)

    Heading("Page headings")
    Choices(RunningHeads.entries, s.runningHeads, { it.label }) { onChange(s.withHeads(it)) }
    Help(
        if (s.runningHeads == RunningHeads.OFF) "Off: put the page number and titles where you like below."
        else "Like a printed book: each page's number at its outside corner, with the book's title over left-hand pages and the chapter's over right-hand ones." +
            if (s.scrolling) " They show when you're turning pages, not scrolling." else ""
    )
    if (s.runningHeads != RunningHeads.OFF) {
        Heading("Heading titles")
        Choices(HeadTitlePlace.entries, s.headTitlePlace, { it.label }) { onChange(s.copy(headTitlePlace = it)) }
        Help("Centred on the page, or nearer the middle of the book, as some printed books set them.")
    }

    Heading("Book of several books")
    Choices(CollectionTitle.entries, s.collectionTitle, { it.label }) { onChange(s.copy(collectionTitle = it)) }
    Help("For a collection such as an omnibus: the title shown over left-hand pages (and as the book title around the page) is the one of the book you're reading in it, or the whole collection's.")

    Heading("Info around the page")
    Help(
        "Choose what to show around the text, and where. Tap a position to change it." +
            if (s.runningHeads != RunningHeads.OFF) " The ${if (s.runningHeads.top) "top" else "bottom"} is kept for the page headings." else ""
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

    Heading("Progress bar at the bottom")
    Choices(ProgressLine.entries, s.progressLine, { it.label }) { onChange(s.copy(progressLine = it)) }
    Help("Shows how much of the book or chapter you've read.")

    Heading("Bar colour")
    Choices(BarShade.entries, s.barShade, { it.label }) { onChange(s.copy(barShade = it)) }
    Help("Dimmed helps prevent burn-in on OLED screens. Autoscroll's timer bar uses the same colour.")
}

/** Pages or scrolling, and how pages turn. */
@Composable
private fun LayoutSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading("Reading mode", first = true)
    Choices(ReaderFlow.entries, s.flow, { it.label }) { onChange(s.copy(flow = it)) }
    Help(
        if (s.scrolling) "Scroll down through each chapter. At the end, tap Next chapter or keep pulling up."
        else "Turn pages, like a printed book."
    )

    if (!s.scrolling) {
        Heading("Pages side by side")
        Choices(ReaderSpread.entries, s.spread, { it.label }) { onChange(s.copy(spread = it)) }

        Heading("Page turn effect")
        Choices(PageTurn.entries, s.pageTurn, { it.label }) { onChange(s.copy(pageTurn = it)) }
    }

    Heading("Chapter title pages")
    SwitchRow("Show them with their chapter", s.joinOpenings) { onChange(s.copy(joinOpenings = it)) }
    Help("Some books put a chapter's number or picture on a page of its own. This shows it at the top of the chapter instead.")
}

/** Taps, buttons, the screen, and looking words up. */
@Composable
private fun ControlSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Heading("Tap to turn pages", first = true)
    RadioChoices(TapZones.entries, s.taps, { it.label }) { onChange(s.copy(taps = it)) }
    Help(listOf(s.taps.help, "Tap the middle of the page to open the menu.").filter { it.isNotEmpty() }.joinToString(" "))
    if (s.taps != TapZones.OFF) {
        Heading("Size of the tap areas at the edges")
        Choices(TapWidth.entries, s.tapWidth, { it.label }) { onChange(s.copy(tapWidth = it)) }
    }

    Spacer(Modifier.height(10.dp))
    SwitchRow("Volume buttons turn pages", s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
    SwitchRow("Keep the screen on while reading", s.keepScreenOn) { onChange(s.copy(keepScreenOn = it)) }
    SwitchRow("Swipe in from the sides to open panels", s.edgePanels) { onChange(s.copy(edgePanels = it)) }
    Help(
        if (s.edgePanels) "Swipe in from the left for the contents, or from the right for reading settings. Turn this off to use the sides for going back."
        else "The sides are for going back. The contents and reading settings are in the menu: tap the middle of the page."
    )
    if (!s.edgePanels) {
        SwitchRow("KoLib's own back swipe", s.edgeBack) { onChange(s.copy(edgeBack = it)) }
        Help(
            if (s.edgeBack) "Swipe in from either side to go back, with an arrow in the app's colours. Let go once the arrow fills in."
            else "Android's own back swipe is used."
        )
    }

    Heading("Screen rotation")
    Choices(ReaderRotation.entries, s.rotation, { it.label }) { onChange(s.copy(rotation = it)) }
    Help(s.rotation.help)

    Heading("Looking up words")
    val key = s.dictionary
    if (key == null) {
        Help("When you look up a word, you'll be asked which app to use.")
    } else {
        val context = LocalContext.current
        val app = remember(key) { dev.kobolibrary.app.reader.LookUp.app(context, key) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (app != null) "Words are looked up in ${app.label}." else "The app you chose for looking up words isn't installed now.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange(s.copy(dictionary = null)) }) { Text("Ask each time") }
        }
    }
}

/** How fast autoscroll goes, and what it does at a chapter's end and when the page is touched. */
@Composable
private fun AutoscrollSettings(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Help(
        (if (s.scrolling) "Scrolls the page for you at your reading speed." else "Turns the page for you at your reading speed.") +
            " To start: tap the middle of the page, then tap Autoscroll."
    )
    Heading("Speed")
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange(s.copy(autoWpm = (s.autoWpm - 10).coerceAtLeast(ReaderSettings.MIN_WPM))) },
            enabled = s.autoWpm > ReaderSettings.MIN_WPM,
        ) { Icon(Icons.Outlined.Remove, "Slower") }
        Slider(
            value = s.autoWpm.toFloat(),
            onValueChange = { onChange(s.copy(autoWpm = ((it / 10f).roundToInt() * 10).coerceIn(ReaderSettings.MIN_WPM, ReaderSettings.MAX_WPM))) },
            valueRange = ReaderSettings.MIN_WPM.toFloat()..ReaderSettings.MAX_WPM.toFloat(),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange(s.copy(autoWpm = (s.autoWpm + 10).coerceAtMost(ReaderSettings.MAX_WPM))) },
            enabled = s.autoWpm < ReaderSettings.MAX_WPM,
        ) { Icon(Icons.Outlined.Add, "Faster") }
    }
    Text("${s.autoWpm} words per minute", style = MaterialTheme.typography.bodyMedium)
    Help("Most people read 200 to 300 words per minute.")

    Heading("At the end of a chapter")
    RadioChoices(AutoEnd.entries, s.autoEnd, { it.label }) { onChange(s.copy(autoEnd = it)) }

    Heading("When you touch the page")
    RadioChoices(AutoResume.entries, s.autoResume, { it.label }) { onChange(s.copy(autoResume = it)) }
    Help("Opening the menu always pauses it until you close the menu.")
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
            ) { Text(label(o), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
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
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                Text("Note", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onGoTo) { Text("Go to the note") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ImageViewer(controller: ReaderController, src: String, onClose: () -> Unit) {
    val bitmap = remember(src) {
        val path = BookServer.pathOf(src)?.first
        path?.let { controller.files.bytes(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = androidx.compose.ui.geometry.Offset.Zero }) }
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        } else {
            Text("This picture can't be shown.", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Filled.Close, "Close picture", tint = Color.White)
        }
    }
}

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
    MarginItem.PAGE -> if (c.chapterPages > 1) MarginPiece.Words("${c.pagesLabel(c.firstShown + 1, c.lastShown + 1)} of ${c.chapterPages}") else null
    MarginItem.BOOK_PAGE -> c.bookPage?.let { n -> c.bookPages?.let { MarginPiece.Words("${c.pagesLabel(n, c.bookPageLast)} of ${it.total}") } }
    MarginItem.PAGES_LEFT -> if (c.chapterPages > 1) (c.chapterPages - c.lastShown - 1).let { n ->
        MarginPiece.Words(when (n) { 0 -> "Last page in chapter"; 1 -> "1 page left"; else -> "$n pages left" })
    } else null
    MarginItem.TIME_LEFT -> if (c.chapterWordsKnown) c.minutesLeftInChapter.let { m ->
        MarginPiece.Words(if (m < 1) "Under a minute left" else "$m min left in chapter")
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
    val rowTop = if (s.scrolling) 12 else s.topRowAt(controller.cutoutTop)
    for (spot in MarginSpot.entries) {
        if (spot == MarginSpot.OFF) continue
        val pieces = MarginItem.entries.filter { s.shownSpotOf(it) == spot }.mapNotNull { marginPiece(it, controller, time, battery) }
        if (pieces.isEmpty()) continue
        Row(
            Modifier
                .align(spot.alignment())
                .padding(horizontal = 24.dp)
                .padding(top = if (spot.top) rowTop.dp else 0.dp, bottom = if (spot.top) 0.dp else bottomItemsInset(s))
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
                        Icon(icon, "Battery", tint = color, modifier = Modifier.size(14.dp).rotate(90f))
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
        ?: PageFrame.of(maxWidth.value, maxHeight.value, s, s.marginRoom(controller.cutoutTop))
    val top = s.runningHeads.top
    // Close over (or under) the text, as in print; never under the camera, nor nearer the edge than the margins' rows.
    val inset = if (top) {
        maxOf(s.topRowAt(controller.cutoutTop).toFloat(), frame.padTop - MarginRoom.GAP - MarginRoom.ROW_HEIGHT)
    } else {
        maxOf(bottomItemsInset(s).value, frame.padBottom - MarginRoom.GAP - MarginRoom.ROW_HEIGHT)
    }
    val color = ink.copy(alpha = 0.72f)
    for (p in pages) {
        val title = when {
            p.opening -> ""
            p.side == PageSide.LEFT -> controller.headBookTitle
            else -> controller.headChapterTitle.ifBlank { controller.headBookTitle }
        }
        Box(
            Modifier
                .align(if (top) Alignment.TopStart else Alignment.BottomStart)
                .offset(x = frame.textLeft(p.slot).dp)
                .padding(top = if (top) inset.dp else 0.dp, bottom = if (top) 0.dp else inset.dp)
                .width(frame.col.dp)
                .height(MarginRoom.ROW_HEIGHT.dp),
        ) {
            p.number?.let { n ->
                Text(
                    n.toString(), style = HeadNumber, color = color, maxLines = 1,
                    modifier = Modifier.align(if (p.side == PageSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd),
                )
            }
            if (title.isNotBlank()) HeadTitleLine(title, p.side, s.headTitlePlace, color)
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
            .padding(top = if (top) 10.dp else 0.dp, bottom = if (top) 0.dp else bottomItemsInset(s))
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
        Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = if (heads) 18.dp else 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            repeat(if (heads) 2 else 1) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(6) { i -> Box(Modifier.fillMaxWidth(if (i == 5) 0.6f else 1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.14f))) }
                }
            }
        }
        if (heads) {
            val headStyle = HeadTitle.copy(fontSize = 8.5.sp, lineHeight = 11.sp, letterSpacing = 0.08.em, fontFeatureSettings = "smcp, onum")
            val numberStyle = HeadNumber.copy(fontSize = 9.sp, lineHeight = 11.sp)
            Row(
                Modifier
                    .align(if (s.runningHeads.top) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = if (s.runningHeads.top) 7.dp else 0.dp, bottom = if (s.runningHeads.top) 0.dp else if (bar) 8.dp else 5.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                listOf(Triple("44", "The Eye of the World", true), Triple("45", "The Hook", false)).forEach { (n, t, left) ->
                    Box(Modifier.weight(1f).height(12.dp)) {
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
