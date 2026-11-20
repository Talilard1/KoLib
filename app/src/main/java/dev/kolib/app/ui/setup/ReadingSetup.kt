package dev.kolib.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kolib.app.L10n
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.reader.PageTurn
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderFlow
import dev.kolib.app.reader.ReaderMargins
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.reader.ProgressLine
import dev.kolib.app.reader.RunningHeads
import dev.kolib.app.reader.ReaderSpread
import dev.kolib.app.reader.TapZones
import dev.kolib.app.tr
import dev.kolib.app.ui.reader.GuideSpace
import dev.kolib.app.ui.reader.ReaderGuide
import dev.kolib.app.ui.reader.ReaderScreen
import kotlin.math.roundToInt

/** The reading setup's steps, in order. */
internal enum class SetupStep { COLOUR, FONT, SIZE, SPACING, ROTATION, TURNING, EDGES, MENU, DONE }

/**
 * The reading setup: the reader itself, open at an example book, with a card over the page that goes
 * through the choices that matter most (the page's colour, the font, its size, the spacing, which way the
 * screen is held, how pages turn) and then shows how the reader works (turning a page, its menu). Each choice
 * changes the page under it there and then, so what's seen is what books will look like. It's laid out for
 * the screen as it's held, upright or on its side, on a phone or a tablet.
 *
 * Told when it's left: [finished] when it was gone through or skipped part-way (what was chosen by then is
 * kept), not when it was backed out of at its first step (nothing is changed).
 */
@Composable
fun ReadingSetup(settings: ReaderSettingsStore, onClose: (finished: Boolean) -> Unit) {
    val context = LocalContext.current
    val landscape = dev.kolib.app.ui.reader.heldSideways()
    val controller = remember {
        val files = ExampleBook.files()
        ReaderController(files, BookStructure(files), ExampleBook.title, L10n.language, store = null, initialSettings = settings.usual(landscape), assets = context.assets)
    }
    // On a screen with a camera in the middle of its top, the headings and the time start clear of it.
    val cut = controller.cutouts
    androidx.compose.runtime.LaunchedEffect(cut, landscape) {
        if (settings.placeAroundCamera(landscape, cut)) {
            val placed = settings.usual(landscape)
            controller.applySettings(controller.settings.copy(runningHeads = placed.runningHeads, marginItems = placed.marginItems))
        }
    }
    ReadingSetupScreen(controller, onClose = { chosen ->
        // What's chosen here is where reading starts, whichever way the device is held.
        if (chosen != null) settings.setBoth(chosen)
        onClose(chosen != null)
    })
}

/** The setup over [controller]'s book; [onClose] is given the settings chosen, or null when it's backed out of. */
@Composable
internal fun ReadingSetupScreen(
    controller: ReaderController,
    onClose: (chosen: ReaderSettings?) -> Unit,
    /** Shown instead of the page in screenshots, where a WebView can't draw. */
    pagePreview: (@Composable () -> Unit)? = null,
    startAt: SetupStep = SetupStep.COLOUR,
) {
    var step by rememberSaveable { mutableStateOf(startAt) }
    val steps = SetupStep.entries

    // What's been tried on the page while it's shown how pages turn: a page forward, then one back.
    var turns by remember { mutableIntStateOf(0) }
    val stepNow by rememberUpdatedState(step)
    LaunchedEffect(controller) {
        var last = controller.page
        snapshotFlow { controller.page }.collect { page ->
            if (stepNow == SetupStep.TURNING) {
                if (page > last && turns == 0) turns = 1 else if (page < last && turns == 1) turns = 2
            }
            last = page
        }
    }
    LaunchedEffect(controller) {
        snapshotFlow { controller.chapterProgress }.collect { p ->
            if (stepNow == SetupStep.TURNING && controller.settings.scrolling && p > 0.02f && turns == 0) turns = 1
        }
    }
    // Each step starts on the chapter's first page, with its heading: pages turned to are turned back.
    LaunchedEffect(step) {
        if (step != SetupStep.EDGES && !controller.loading && (controller.page > 0 || controller.chapterProgress > 0.02f)) controller.goToStart()
        if (step == SetupStep.TURNING) turns = 0
    }
    // But what shows round the page is chosen on a page that has it all: a chapter's first page has no heading over
    // it (as in print), so it's the one after, where the book's title and the chapter's are over the text.
    LaunchedEffect(step, controller.loading, controller.settings.scrolling, controller.pages) {
        if (step == SetupStep.EDGES && !controller.loading && !controller.settings.scrolling && controller.page == 0 && controller.pages > 1) controller.nextPage()
    }
    // A book too short to have such a page: its one page is given the headings while they're being chosen.
    LaunchedEffect(step, controller.loading, controller.chapterPages) {
        controller.headsOnOpenings = step == SetupStep.EDGES && !controller.loading && controller.chapterPages <= 1
    }
    DisposableEffect(controller) { onDispose { controller.headsOnOpenings = false } }

    ReaderScreen(
        controller = controller,
        onSettings = controller::applySettings,
        onClose = { onClose(controller.settings) },
        pagePreview = pagePreview,
        guide = ReaderGuide(menu = step == SetupStep.MENU) { over ->
            SetupGuide(
                controller = controller,
                step = step,
                turns = turns,
                over = over,
                // Something of the reader's own is open over the page: Back closes that first.
                readerHasBack = over.panelOpen || controller.note != null || controller.image != null || controller.browsing != null || controller.openAnnotation != null,
                onBack = { if (step.ordinal == 0) onClose(null) else step = steps[step.ordinal - 1] },
                onNext = { if (step == steps.last()) onClose(controller.settings) else step = steps[step.ordinal + 1] },
                onSkip = { onClose(controller.settings) },
            )
        },
    )
}

/** Two pages show side by side with these settings on a screen this size (as the page itself decides it). */
private fun twoPages(s: ReaderSettings, width: Dp, height: Dp): Boolean =
    !s.scrolling && (s.spread == ReaderSpread.TWO || (s.spread == ReaderSpread.AUTO && width > height && width >= 880.dp))

/** Over the page: where to touch it, when it's to be tried, and the card with the step's choices. */
@Composable
private fun BoxScope.SetupGuide(
    controller: ReaderController,
    step: SetupStep,
    turns: Int,
    over: GuideSpace,
    readerHasBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val s = controller.settings
    val c = SetupColours.over(s.theme).fading()
    val ink = Color(s.theme.fg)
    val change: (ReaderSettings) -> Unit = controller::applySettings
    BackHandler(enabled = !readerHasBack, onBack = onBack)

    BoxWithConstraints(Modifier.matchParentSize()) {
        val w = maxWidth
        val h = maxHeight
        // With two pages side by side the example chapter opens on the right, as chapters do, and the left page
        // is blank: the card goes there (where that page is wide enough for it). On a phone on its side with one
        // page across it, the card goes at the left too: at the foot, it would cover the page top to bottom.
        // Otherwise it sits at the foot of the page.
        val onBlankPage = twoPages(s, w, h) && s.rectoOpenings && w / 2 >= 360.dp
        val side = onBlankPage || (w > h && h < 520.dp)
        val menuOpen = controller.controlsVisible
        val pageLive = step == SetupStep.TURNING || step == SetupStep.MENU
        // Trying page turns with the card on the left-hand page: once that page has text on it, the card makes way,
        // down to the corner with only what it says, until the first page is turned back to.
        val madeWay = onBlankPage && step == SetupStep.TURNING && controller.page > 0

        // While choosing, the page is only to look at: touches stop here.
        if (!pageLive) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })

        val cardWidth = when {
            onBlankPage -> minOf(440.dp, w / 2 - 48.dp)
            side -> minOf(380.dp, w * 0.46f)
            else -> minOf(w - 20.dp, 520.dp)
        }
        var cardHeight by remember { mutableIntStateOf(0) }
        val cardHeightDp = with(LocalDensity.current) { cardHeight.toDp() }

        // The sides of the page turn it when they're tapped; the left goes back, unless the card is over it.
        val tapsTurn = s.taps != TapZones.OFF && !s.scrolling
        val tapBack = tapsTurn && s.taps == TapZones.SIDES && (!side || onBlankPage)

        // Where to touch the page.
        if (pageLive) {
            // In the middle of the page that shows above the card (or of the whole page, with the card beside it).
            val y = when {
                madeWay -> (h - cardHeightDp - 12.dp) / 2
                side -> h / 2
                else -> ((h - cardHeightDp - 12.dp) / 2).coerceAtLeast(110.dp)
            }
            val edge = w * s.tapWidth.fraction / 2
            val size = 88.dp
            // The middle of the page, clear of a card beside it, and still between the sides that turn the page.
            val middleX = if (side && !onBlankPage) maxOf(w / 2, cardWidth + 60.dp).coerceAtMost(w * (1f - s.tapWidth.fraction) - 30.dp) else w / 2
            val forward = step == SetupStep.TURNING && tapsTurn && turns == 0
            val backward = step == SetupStep.TURNING && tapBack && turns == 1
            val middle = step == SetupStep.MENU && !menuOpen
            // (Each is there only while it shows, so nothing is drawn over and over for a hint that isn't.)
            easedIn(forward).let { a -> if (a > 0f) TouchHere(ink, Modifier.offset(x = w - edge - size / 2, y = y - size / 2).alpha(a)) }
            easedIn(backward).let { a -> if (a > 0f) TouchHere(ink, Modifier.offset(x = edge - size / 2, y = y - size / 2).alpha(a)) }
            easedIn(middle).let { a -> if (a > 0f) TouchHere(ink, Modifier.offset(x = middleX - size / 2, y = y - size / 2).alpha(a)) }
        }

        // The card: at the foot of the page, in the middle of it while the reader's own bars are up, or on the blank
        // left-hand page.
        val across by animateFloatAsState(
            when {
                // Its middle a quarter of the way across: the middle of the left-hand page.
                onBlankPage -> ((w / 2 - cardWidth - 20.dp) / (w - cardWidth - 20.dp) - 1f).coerceIn(-1f, 0f)
                side -> -1f
                else -> 0f
            },
            spring(stiffness = Spring.StiffnessLow), label = "across",
        )
        val down by animateFloatAsState(if ((side && !madeWay) || menuOpen) 0f else 1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow), label = "down")
        var arrived by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { arrived = true }
        val arriving by animateFloatAsState(if (arrived) 1f else 0f, tween(420), label = "arriving")
        // While the reader's own bars are up, the card keeps between them.
        val barTop by animateDpAsState(over.barTop, label = "barTop")
        val barBottom by animateDpAsState(over.barBottom, label = "barBottom")
        val shape = RoundedCornerShape(30.dp)
        Box(Modifier.fillMaxSize().padding(top = barTop, bottom = barBottom)) {
            Column(
                Modifier
                    .align(BiasAlignment(across, down))
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)
                    .width(cardWidth)
                    .heightIn(max = (h - barTop - barBottom - 22.dp).coerceAtLeast(160.dp))
                    .graphicsLayer {
                        alpha = arriving
                        translationY = (1f - arriving) * 36.dp.toPx()
                    }
                    .onSizeChanged { cardHeight = it.height }
                    .shadow(26.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .clip(shape)
                    .background(c.card)
                    .border(0.5.dp, c.line, shape)
                    // Touches on the card are its own: none reach the page under it.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .testTag("setup-card"),
            ) {
                GuideHeader(SetupStep.entries.size, step.ordinal, c, onBack = onBack.takeIf { step.ordinal > 0 }, onSkip = onSkip.takeIf { step != SetupStep.DONE })
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val on = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(320)) { on * it / 5 } + fadeIn(tween(240, delayMillis = 60)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -on * it / 5 } + fadeOut(tween(140)))
                            .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) })
                    },
                    label = "step",
                ) { shown ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                        // The step's choices scroll where the screen is too short for them (fading out at the foot while
                        // there's more below); the button under them stays put.
                        val scroll = rememberScrollState()
                        val more = easedIn(scroll.canScrollForward)
                        Column(
                            Modifier.weight(1f, fill = false)
                                .drawWithContent {
                                    drawContent()
                                    if (more > 0f) {
                                        val fade = 28.dp.toPx()
                                        drawRect(
                                            Brush.verticalGradient(listOf(c.card.copy(alpha = 0f), c.card.copy(alpha = more)), startY = size.height - fade, endY = size.height),
                                            topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade),
                                        )
                                    }
                                }
                                .verticalScroll(scroll),
                        ) {
                            when (shown) {
                                SetupStep.COLOUR -> ColourStep(s, c, change)
                                SetupStep.FONT -> FontStep(s, c, change)
                                SetupStep.SIZE -> SizeStep(s, c, change)
                                SetupStep.SPACING -> SpacingStep(s, c, change)
                                SetupStep.ROTATION -> RotationStep(s, c, change)
                                SetupStep.TURNING -> TurningStep(s, c, turns, tapBack, choices = !madeWay, change)
                                SetupStep.EDGES -> EdgesStep(s, c, change)
                                SetupStep.MENU -> MenuStep(c, menuOpen)
                                SetupStep.DONE -> DoneStep(s, c)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        val showMe = shown == SetupStep.MENU && !menuOpen
                        SetupButton(
                            when {
                                showMe -> tr("Show me")
                                shown == SetupStep.DONE -> tr("Done")
                                else -> tr("Continue")
                            },
                            c, Modifier.padding(horizontal = Edge).testTag("setup-next"),
                        ) { if (showMe) controller.controlsVisible = true else onNext() }
                    }
                }
            }
        }
    }
}

/** How far in from the card's sides its words and choices sit. */
private val Edge = 20.dp

@Composable
private fun StepTitle(title: String, about: String?, c: SetupColours) {
    Column(Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 6.dp)) {
        Text(title, color = c.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (about != null) Text(about, color = c.soft, style = SetupBody, modifier = Modifier.padding(top = 3.dp))
    }
}

/** The name of a group of choices, over it. */
@Composable
private fun GroupName(text: String, c: SetupColours) {
    Text(text, color = c.soft, style = SetupLabel, modifier = Modifier.padding(start = Edge + 2.dp, end = Edge, bottom = 7.dp))
}

@Composable
private fun ColourStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Page colour"), tr("Pick the one that’s easiest on your eyes."), c)
    Spacer(Modifier.height(16.dp))
    Box(Modifier.padding(horizontal = Edge - 8.dp)) { ThemeSwatches(s.theme, c) { change(s.copy(theme = it)) } }
}

@Composable
private fun FontStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Font"), tr("Choose how the words look."), c)
    Spacer(Modifier.height(18.dp))
    FontTiles(s.font, c, Edge) { change(s.copy(font = it)) }
}

@Composable
private fun SizeStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Text size"), tr("Drag until it’s comfortable to read."), c)
    Spacer(Modifier.height(14.dp))
    val scales = ReaderSettings.FONT_SCALES
    val at = scales.indexOfFirst { it >= s.fontScale - 0.001f }.let { if (it < 0) scales.size - 1 else it }
    Row(Modifier.fillMaxWidth().padding(horizontal = Edge), verticalAlignment = Alignment.CenterVertically) {
        Text("A", color = c.soft, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium), modifier = Modifier.clearAndSetSemantics { })
        StepSlider(at, scales.size, c, tr("Text size"), Modifier.weight(1f).padding(horizontal = 12.dp)) { change(s.copy(fontScale = scales[it])) }
        Text("A", color = c.soft, style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium), modifier = Modifier.clearAndSetSemantics { })
    }
    Text(
        "${(s.fontScale * 100).roundToInt()}%", color = c.soft, style = SetupLabel.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

/** The line spacings the setup offers: the book's own, then closer to wider. */
private val SetupLineSpacings = listOf(0f, 1.2f, 1.4f, 1.6f, 1.8f)

@Composable
private fun SpacingStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Spacing"), tr("Give the words more room, or fit more on a page."), c)
    Spacer(Modifier.height(16.dp))
    GroupName(tr("Line spacing"), c)
    Segments(
        SetupLineSpacings, SetupLineSpacings.firstOrNull { it == s.lineSpacing }, c, "line",
        describe = { if (it == 0f) tr("Book default") else "%.1f".format(it) },
        modifier = Modifier.padding(horizontal = Edge),
        onPick = { change(s.copy(lineSpacing = it)) },
    ) { v, chosen ->
        val colour = if (chosen) c.text else c.soft
        if (v == 0f) Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = colour, modifier = Modifier.size(22.dp))
        else LinesPicture(gap = 3.1f + (v - 1.2f) / 0.2f * 1.15f, colour = colour)
    }
    Spacer(Modifier.height(16.dp))
    GroupName(tr("Margins"), c)
    Segments(
        ReaderMargins.entries, s.margins, c, "margins", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge),
        onPick = { change(s.copy(margins = it)) },
    ) { m, chosen ->
        MarginsPicture(inset = when (m) { ReaderMargins.NARROW -> 2.4f; ReaderMargins.MEDIUM -> 4.2f; ReaderMargins.WIDE -> 6f }, colour = if (chosen) c.text else c.soft)
    }
}

/** Which way the screen faces while reading, and whether two pages show side by side: both seen at once on the page. */
@Composable
private fun RotationStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Screen rotation"), tr("Choose which way you hold your device to read."), c)
    Spacer(Modifier.height(16.dp))
    Segments(
        ReaderRotation.entries, s.rotation, c, "rotation", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge), height = 64.dp,
        onPick = { change(s.copy(rotation = it)) },
    ) { r, chosen ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (r) {
                    ReaderRotation.AUTO -> Icons.Outlined.ScreenRotation
                    ReaderRotation.PORTRAIT -> Icons.Outlined.StayCurrentPortrait
                    ReaderRotation.LANDSCAPE -> Icons.Outlined.StayCurrentLandscape
                },
                null, tint = if (chosen) c.text else c.soft, modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(3.dp))
            SegmentWords(r.label, chosen, c)
        }
    }
    AnimatedVisibility(!s.scrolling, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            Spacer(Modifier.height(16.dp))
            GroupName(tr("Pages side by side"), c)
            Segments(ReaderSpread.entries, s.spread, c, "spread", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge), onPick = { change(s.copy(spread = it)) }) { p, chosen ->
                SegmentWords(p.label, chosen, c)
            }
        }
    }
}

@Composable
private fun TurningStep(s: ReaderSettings, c: SetupColours, turns: Int, tapBack: Boolean, choices: Boolean, change: (ReaderSettings) -> Unit) {
    val tryIt = when {
        s.scrolling -> tr("Try it: scroll the page up.")
        turns == 0 && s.taps == TapZones.OFF -> tr("Try it: swipe the page.")
        turns == 0 -> tr("Try it: tap the right side of the page, or swipe.")
        turns == 1 && tapBack -> tr("Now tap the left side to go back.")
        turns == 1 -> tr("Now swipe the other way to go back.")
        else -> tr("Try the other page turns too.")
    }
    StepTitle(tr("Turning pages"), tryIt, c)
    AnimatedVisibility(choices, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            Spacer(Modifier.height(16.dp))
            Segments(ReaderFlow.entries, s.flow, c, "flow", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge), onPick = { change(s.copy(flow = it)) }) { f, chosen ->
                SegmentWords(f.label, chosen, c)
            }
            AnimatedVisibility(!s.scrolling, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    GroupName(tr("Page turn effect"), c)
                    Segments(PageTurn.entries, s.pageTurn, c, "turn", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge), onPick = { change(s.copy(pageTurn = it)) }) { t, chosen ->
                        SegmentWords(t.label, chosen, c)
                    }
                }
            }
        }
    }
}

/**
 * What shows round the page while reading: the page headings, the time and battery, and the progress bar,
 * each seen on the page as it's chosen. The time and battery go where they sit best with the headings: in
 * the top corners when the headings are at the foot, together at the foot otherwise.
 */
@Composable
private fun EdgesStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Around the page"), tr("Choose what shows at the top and bottom of the page."), c)
    Spacer(Modifier.height(16.dp))
    GroupName(tr("Page headings"), c)
    Segments(
        RunningHeads.entries, s.runningHeads, c, "heads", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge),
        onPick = { h -> change(s.withHeads(h).let { it.withClock(s.clockShown) }) },
    ) { h, chosen -> SegmentWords(h.label, chosen, c) }
    Spacer(Modifier.height(16.dp))
    GroupName(tr("Time and battery"), c)
    Segments(
        listOf(true, false), s.clockShown, c, "clock", describe = { if (it) tr("On") else tr("Off") }, modifier = Modifier.padding(horizontal = Edge),
        onPick = { change(s.withClock(it)) },
    ) { on, chosen -> SegmentWords(if (on) tr("On") else tr("Off"), chosen, c) }
    Spacer(Modifier.height(16.dp))
    GroupName(tr("Progress bar"), c)
    Segments(
        ProgressLine.entries, s.progressLine, c, "progress", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge),
        onPick = { change(s.copy(progressLine = it)) },
    ) { p, chosen -> SegmentWords(p.label, chosen, c) }
}

/** The reader's menu: how to bring it up, and once it's up, what its buttons are, along the top and then along the foot. */
@Composable
private fun MenuStep(c: SetupColours, menuOpen: Boolean) {
    StepTitle(tr("The menu"), tr("Tap the middle of the page to open or close it."), c)
    AnimatedVisibility(menuOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.padding(start = Edge, end = Edge, top = 10.dp)) {
            IconLine(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book"), c)
            IconLine(Icons.Filled.Search, tr("Search the book"), c)
            IconLine(Icons.Outlined.BookmarkBorder, tr("Bookmark this page"), c)
            IconLine(Icons.AutoMirrored.Filled.List, tr("Contents and bookmarks"), c)
            IconLine(Icons.Filled.PlayArrow, tr("Autoscroll"), c)
            IconLine(Icons.Outlined.ScreenRotation, tr("Rotation"), c)
            IconLine(Icons.Outlined.Settings, tr("Reading settings"), c, note = tr("Everything you just chose, and more."), strong = true)
            Text(
                tr("Drag the bar at the bottom to move through the book, or skip a chapter with the buttons either side of it."),
                color = c.soft, style = SetupLabel.copy(fontWeight = FontWeight.Normal),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DoneStep(s: ReaderSettings, c: SetupColours) {
    StepTitle(tr("You’re all set"), tr("Change anything later in Reading settings."), c)
    Column(Modifier.padding(start = Edge, end = Edge, top = 10.dp)) {
        if (s.edgePanels) IconLine(Icons.Outlined.Swipe, tr("Swipe from the left edge for contents, or the right edge for settings."), c)
        IconLine(Icons.Outlined.BrightnessMedium, tr("Swipe up or down the left edge to change the brightness."), c)
        IconLine(Icons.Outlined.BorderColor, tr("Hold a word to highlight it, add a note or look it up."), c)
    }
}
