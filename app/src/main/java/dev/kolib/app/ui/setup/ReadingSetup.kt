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
import androidx.compose.material.icons.outlined.SwapVert
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.animation.animateContentSize
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
import dev.kolib.app.ui.reader.TabLineSetup
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.kolib.app.reader.TapWidth
import dev.kolib.app.reader.LevelStart
import dev.kolib.app.reader.LevelSide

/** The reading setup's steps, in order. */
internal enum class SetupStep { COLOUR, FONT, SIZE, SPACING, ROTATION, TURNING, TAPS, TAP_SIZE, KEYS, SWIPES, TAB_LINE, LEVEL, EDGES, MENU, KEPT, DONE }

/** The steps gone through with these settings: how far in a tap turns the page is only asked while taps do. */
internal fun setupSteps(s: ReaderSettings): List<SetupStep> = SetupStep.entries.filter { it != SetupStep.TAP_SIZE || s.taps != TapZones.OFF }

/** Steps where the page can be touched: to turn pages, and to try what's being chosen. */
private val LiveSteps = setOf(SetupStep.TURNING, SetupStep.TAPS, SetupStep.TAP_SIZE, SetupStep.KEYS, SetupStep.SWIPES, SetupStep.LEVEL, SetupStep.MENU)

/**
 * The reading setup: the reader itself, open at an example book, with a card over the page that goes
 * through the choices that matter most (the page's colour, the font, its size, the spacing, which way the
 * screen is held, how pages turn, the controls, a card for each and tried on the page) and then shows how the reader
 * works (its menu). Each choice
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
    val steps = setupSteps(controller.settings)
    val before = steps.lastOrNull { it.ordinal < step.ordinal }
    val after = steps.firstOrNull { it.ordinal > step.ordinal }
    val goBack = { if (before == null) onClose(null) else step = before }
    // KoLib's back swipe, tried on its step: it's said that it worked, rather than going back a step.
    var backTried by remember { mutableStateOf(false) }

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
        backTried = false
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
    // On its step, the line in the contents a swipe across changes tab above (and closes them below) is set: the contents
    // open for it, and once it's set (or kept), it's on to the next step.
    val tabLine = remember(step) { if (step == SetupStep.TAB_LINE) TabLineSetup(controller.settings.tabSwipeLine) else null }

    ReaderScreen(
        controller = controller,
        onSettings = controller::applySettings,
        onClose = { onClose(controller.settings) },
        pagePreview = pagePreview,
        guide = ReaderGuide(
            menu = step == SetupStep.MENU,
            // The swipes in from the sides are tried on their own step, and swipes in the contents on the next.
            panels = step == SetupStep.MENU || step == SetupStep.SWIPES || step == SetupStep.TAB_LINE,
            onBack = { if (step == SetupStep.SWIPES) backTried = true else goBack() },
            tabLine = tabLine,
            onTabLine = { if (after == null) onClose(controller.settings) else step = after },
        ) { over ->
            SetupGuide(
                controller = controller,
                step = step,
                count = steps.size,
                at = steps.count { it.ordinal < step.ordinal },
                turns = turns,
                backTried = backTried,
                over = over,
                // Something of the reader's own is open over the page: Back closes that first.
                readerHasBack = over.panelOpen || controller.note != null || controller.image != null || controller.browsing != null || controller.openAnnotation != null,
                onBack = goBack,
                onNext = { if (after == null) onClose(controller.settings) else step = after },
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
    /** How many steps there are, and how many come before this one. */
    count: Int,
    at: Int,
    turns: Int,
    backTried: Boolean,
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
        val pageLive = step in LiveSteps
        // Trying page turns with the card on the left-hand page: once that page has text on it, the card makes way,
        // down to the corner with only what it says, until the first page is turned back to.
        val madeWay = onBlankPage && step == SetupStep.TURNING && controller.page > 0

        // While choosing, the page is only to look at: touches stop here.
        if (!pageLive) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })

        // While the brightness swipe is tried, the card keeps off the sides it's on, and away from where it starts.
        val levelStep = step == SetupStep.LEVEL && s.levelSide != LevelSide.OFF
        val keep = w * ReaderSettings.LEVEL_STRIP + 4.dp
        val clearStart by animateDpAsState(if (levelStep && s.levelSide != LevelSide.RIGHT) keep else 0.dp, label = "clearStart")
        val clearEnd by animateDpAsState(if (levelStep && s.levelSide != LevelSide.LEFT) keep else 0.dp, label = "clearEnd")
        val cardWidth = when {
            onBlankPage -> minOf(440.dp, w / 2 - 48.dp)
            side -> minOf(380.dp, w * 0.46f)
            else -> minOf(w - 20.dp - clearStart - clearEnd, 520.dp)
        }
        var cardHeight by remember { mutableIntStateOf(0) }
        val cardHeightDp = with(LocalDensity.current) { cardHeight.toDp() }

        // The sides of the page turn it when they're tapped; the left goes back, unless the card is over it.
        val tapsTurn = s.taps != TapZones.OFF && !s.scrolling
        val tapBack = tapsTurn && s.taps == TapZones.SIDES && (!side || onBlankPage)

        // While where taps go is chosen, the card goes to the middle when the top and the foot of the page turn it.
        val tapSteps = step == SetupStep.TAPS || step == SetupStep.TAP_SIZE
        val middled = tapSteps && s.taps == TapZones.TOP_BOTTOM && !side
        // How far down the page a hint goes: the middle of the page that shows above the card (or of the whole page,
        // with the card beside it or in the middle).
        val y = when {
            madeWay -> (h - cardHeightDp - 12.dp) / 2
            side || middled -> h / 2
            else -> ((h - cardHeightDp - 12.dp) / 2).coerceAtLeast(110.dp)
        }
        // Where taps go, shown for a moment as the step starts and each time it's changed (under the card); and where
        // the brightness swipe starts, while it's tried.
        if (tapSteps) TapZonesHint(s, step, ink, if (middled) h * s.tapWidth.fraction / 2 else y)
        if (step == SetupStep.LEVEL) LevelZonesHint(s, ink)

        // Where to touch the page.
        if (pageLive) {
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

        // The card: at the foot of the page, in the middle of it while the reader's own bars are up (or while what
        // shows round the page is chosen, so the headings above it and the progress bar below it can be seen), or on
        // the blank left-hand page.
        val across by animateFloatAsState(
            when {
                // Its middle a quarter of the way across: the middle of the left-hand page.
                onBlankPage -> ((w / 2 - cardWidth - 20.dp) / (w - cardWidth - 20.dp) - 1f).coerceIn(-1f, 0f)
                // On its side, it goes to the right while the brightness swipe is tried on the left.
                side -> if (levelStep && s.levelSide == LevelSide.LEFT) 1f else -1f
                else -> 0f
            },
            spring(stiffness = Spring.StiffnessLow), label = "across",
        )
        val down by animateFloatAsState(
            when {
                // The brightness swipe tried from the foot of the page: the card goes to the top.
                levelStep && s.levelStart == LevelStart.BOTTOM && !side -> -1f
                (side && !madeWay) || menuOpen || step == SetupStep.EDGES || middled -> 0f
                else -> 1f
            },
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow), label = "down",
        )
        var arrived by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { arrived = true }
        val arriving by animateFloatAsState(if (arrived) 1f else 0f, tween(420), label = "arriving")
        // While the reader's own bars are up, the card keeps between them.
        val barTop by animateDpAsState(over.barTop, label = "barTop")
        val barBottom by animateDpAsState(over.barBottom, label = "barBottom")
        val shape = RoundedCornerShape(30.dp)
        Box(Modifier.fillMaxSize().padding(top = barTop, bottom = barBottom, start = clearStart, end = clearEnd)) {
            Column(
                Modifier
                    .align(BiasAlignment(across, down))
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)
                    .width(cardWidth)
                    // In the middle, between where the top and the foot of the page turn it, it keeps clear of both
                    // (what's in it scrolls, if it has to).
                    .heightIn(max = (if (middled) h * (1f - 2 * s.tapWidth.fraction) - 16.dp else h - barTop - barBottom - 22.dp).coerceAtLeast(160.dp))
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
                GuideHeader(count, at, c, onBack = onBack.takeIf { at > 0 }, onSkip = onSkip.takeIf { step != SetupStep.DONE })
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
                                SetupStep.TAPS -> TapsStep(s, c, change)
                                SetupStep.TAP_SIZE -> TapSizeStep(s, c, change)
                                SetupStep.KEYS -> KeysStep(s, c, change)
                                SetupStep.SWIPES -> SwipesStep(s, c, backTried, change)
                                SetupStep.TAB_LINE -> TabLineStep(c)
                                SetupStep.LEVEL -> LevelStep(s, c, change)
                                SetupStep.EDGES -> EdgesStep(s, c, change)
                                SetupStep.MENU -> MenuStep(c, menuOpen)
                                SetupStep.KEPT -> KeptStep(c)
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
            Segments(ReaderFlow.entries, s.flow, c, "flow", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge), onPick = { change(s.withFlow(it)) }) { f, chosen ->
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

/** What a tap on either side of the page does: tried there and then, with where taps go shown on the page. */
@Composable
private fun TapsStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Tapping the page"), tr("Choose what a tap on each side does, then try it."), c)
    Spacer(Modifier.height(16.dp))
    ChoiceList(TapZones.entries, s.taps, c, "taps", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge)) { change(s.copy(taps = it)) }
    Box(Modifier.animateContentSize()) {
        if (s.taps.help.isNotEmpty()) {
            Text(s.taps.help, color = c.soft, style = SetupLabel.copy(fontWeight = FontWeight.Normal), modifier = Modifier.padding(start = Edge + 2.dp, end = Edge, top = 7.dp))
        }
    }
}

/** How far in from the edges a tap turns the page: tried there and then, with where taps go shown on the page. */
@Composable
private fun TapSizeStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Tap zone size"), tr("How far in from the edges a tap turns the page. Try it."), c)
    Spacer(Modifier.height(16.dp))
    Segments(
        TapWidth.entries, s.tapWidth, c, "tapzone", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge),
        onPick = { change(s.copy(tapWidth = it)) },
    ) { w, chosen -> SegmentWords(w.label, chosen, c) }
}

/** The volume keys (tried there and then) and the screen staying on. */
@Composable
private fun KeysStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(tr("Volume keys and screen"), null, c)
    Spacer(Modifier.height(8.dp))
    SetupSwitch(
        tr("Volume keys turn pages"), if (s.volumeKeys) tr("Try it: press a volume key.") else null,
        s.volumeKeys, c, "setup-volume-keys",
    ) { change(s.copy(volumeKeys = it)) }
    SetupSwitch(tr("Keep screen on"), tr("The screen stays on while a book is open."), s.keepScreenOn, c, "setup-keep-on") { change(s.copy(keepScreenOn = it)) }
}

/**
 * Swipes in from the sides: they pull out the side panels (and go back with one open), or, set to, go back throughout,
 * with KoLib's own arrow. Tried there and then.
 */
@Composable
private fun SwipesStep(s: ReaderSettings, c: SetupColours, backTried: Boolean, change: (ReaderSettings) -> Unit) {
    val tryIt = when {
        s.edgeBack && backTried -> tr("That's it. In a book, it goes back, just like Back.")
        s.edgeBack -> tr("Try it: swipe in from either edge.")
        else -> tr("Try it: swipe in from an edge for a panel, then in from an edge again to close it.")
    }
    StepTitle(tr("Edge swipes"), tryIt, c)
    Spacer(Modifier.height(8.dp))
    SetupSwitch(
        tr("Edge swipes always go back"), dev.kolib.app.ui.reader.edgeSwipesHelp(s.edgeBack), s.edgeBack, c, "setup-edge-back",
    ) { change(s.copy(edgeBack = it)) }
}

/**
 * Where, in the contents, a swipe across changes tab, and where it closes them: set over the contents themselves (see
 * [TabLineSetup]), which open for it. This is what shows if they're closed.
 */
@Composable
private fun TabLineStep(c: SetupColours) {
    StepTitle(tr("Swiping in the contents"), tr("Swipe the contents closed as you usually would, and above that, a swipe across changes tab."), c)
}

/** Which side a swipe up or down sets the brightness on, and where up it it starts: tried there and then. */
@Composable
private fun LevelStep(s: ReaderSettings, c: SetupColours, change: (ReaderSettings) -> Unit) {
    StepTitle(
        tr("Brightness swipe"),
        if (s.levelSide == LevelSide.OFF) tr("Change the brightness in Reading settings instead.") else tr("Try it: swipe up or down where it's marked."),
        c,
    )
    Spacer(Modifier.height(16.dp))
    Segments(
        LevelSide.entries, s.levelSide, c, "levelside", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge),
        onPick = { change(s.copy(levelSide = it)) },
    ) { o, chosen -> SegmentWords(o.label, chosen, c) }
    AnimatedVisibility(s.levelSide != LevelSide.OFF, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            Spacer(Modifier.height(16.dp))
            GroupName(tr("Start the swipe from"), c)
            Segments(
                LevelStart.entries, s.levelStart, c, "levelstart", describe = { it.label }, modifier = Modifier.padding(horizontal = Edge),
                onPick = { change(s.copy(levelStart = it)) },
            ) { o, chosen -> SegmentWords(o.label, chosen, c) }
            Text(
                tr("Keep it clear of where your thumb rests to scroll."), color = c.soft, style = SetupLabel.copy(fontWeight = FontWeight.Normal),
                modifier = Modifier.padding(start = Edge + 2.dp, end = Edge, top = 7.dp),
            )
        }
    }
}

/**
 * Where a swipe up or down sets the brightness, marked on the page while it's tried: the strip down each side it's set
 * for, as far up it as it starts, with arrows up and down. It takes no touches, so they reach the page under it.
 */
@Composable
private fun LevelZonesHint(s: ReaderSettings, ink: Color) {
    if (s.levelSide == LevelSide.OFF) return
    Canvas(Modifier.fillMaxSize().testTag("setup-level-zones")) {
        val w = size.width
        val h = size.height
        val strip = w * ReaderSettings.LEVEL_STRIP
        val top = h * s.levelStart.from
        val bottom = h * s.levelStart.to
        val sides = when (s.levelSide) {
            LevelSide.LEFT -> listOf(0f)
            LevelSide.RIGHT -> listOf(w - strip)
            else -> listOf(0f, w - strip)
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        val stroke = 1.4.dp.toPx()
        sides.forEach { left ->
            drawRect(ink.copy(alpha = 0.1f), Offset(left, top), Size(strip, bottom - top))
            val inner = if (left == 0f) strip else left
            drawLine(ink.copy(alpha = 0.4f), Offset(inner, top), Offset(inner, bottom), stroke, pathEffect = dash)
            if (top > 0f) drawLine(ink.copy(alpha = 0.4f), Offset(left, top), Offset(left + strip, top), stroke, pathEffect = dash)
            if (bottom < h) drawLine(ink.copy(alpha = 0.4f), Offset(left, bottom), Offset(left + strip, bottom), stroke, pathEffect = dash)
            // Arrows up and down, in the middle of it.
            val x = left + strip / 2
            val y = (top + bottom) / 2
            val k = 6.dp.toPx()
            val gap = 9.dp.toPx()
            listOf(-1f, 1f).forEach { way ->
                val tip = y + way * (gap + k)
                val chevron = Path().apply {
                    moveTo(x - k, tip - way * k)
                    lineTo(x, tip)
                    lineTo(x + k, tip - way * k)
                }
                drawPath(chevron, ink.copy(alpha = 0.62f), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Where taps on the page go, shown over it for a moment when [step] starts and each time the taps are changed: the
 * parts that go back and those that go on, each with an arrow (at [arrowAt] down the page, on the sides). It takes no
 * touches, so taps reach the page under it.
 */
@Composable
private fun TapZonesHint(s: ReaderSettings, step: SetupStep, ink: Color, arrowAt: Dp) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(step, s.taps, s.tapWidth) {
        shown = true
        kotlinx.coroutines.delay(3_000)
        shown = false
    }
    val a = easedIn(shown)
    if (a <= 0f || s.taps == TapZones.OFF) return
    Canvas(Modifier.fillMaxSize().alpha(a).testTag("setup-tap-zones")) {
        val f = s.tapWidth.fraction
        val w = size.width
        val h = size.height
        val arrowY = arrowAt.toPx().coerceIn(0f, h)
        // Each part: where it is, whether it goes on, and where its arrow sits.
        val parts = when (s.taps) {
            TapZones.FORWARD -> listOf(
                Triple(Rect(0f, 0f, w * f, h), true, Offset(w * f / 2, arrowY)),
                Triple(Rect(w * (1 - f), 0f, w, h), true, Offset(w * (1 - f / 2), arrowY)),
            )
            TapZones.TOP_BOTTOM -> listOf(
                Triple(Rect(0f, 0f, w, h * f), false, Offset(w / 2, h * f / 2)),
                Triple(Rect(0f, h * (1 - f), w, h), true, Offset(w / 2, h * (1 - f / 2))),
                Triple(Rect(0f, h * f, w * f, h * (1 - f)), false, Offset(w * f / 2, h / 2)),
                Triple(Rect(w * (1 - f), h * f, w, h * (1 - f)), true, Offset(w * (1 - f / 2), h / 2)),
            )
            else -> listOf(
                Triple(Rect(0f, 0f, w * f, h), false, Offset(w * f / 2, arrowY)),
                Triple(Rect(w * (1 - f), 0f, w, h), true, Offset(w * (1 - f / 2), arrowY)),
            )
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        val stroke = 1.4.dp.toPx()
        parts.forEach { (r, on, arrow) ->
            drawRect(ink.copy(alpha = if (on) 0.11f else 0.06f), r.topLeft, r.size)
            // Its edges inside the page, dashed.
            if (r.left > 0f) drawLine(ink.copy(alpha = 0.4f), Offset(r.left, r.top), Offset(r.left, r.bottom), stroke, pathEffect = dash)
            if (r.right < w) drawLine(ink.copy(alpha = 0.4f), Offset(r.right, r.top), Offset(r.right, r.bottom), stroke, pathEffect = dash)
            if (r.top > 0f) drawLine(ink.copy(alpha = 0.4f), Offset(r.left, r.top), Offset(r.right, r.top), stroke, pathEffect = dash)
            if (r.bottom < h) drawLine(ink.copy(alpha = 0.4f), Offset(r.left, r.bottom), Offset(r.right, r.bottom), stroke, pathEffect = dash)
            // A chevron: pointing on, or back.
            val k = 7.dp.toPx() * (if (on) 1f else -1f)
            val v = 9.dp.toPx()
            val chevron = Path().apply {
                moveTo(arrow.x - k / 2, arrow.y - v)
                lineTo(arrow.x + k / 2, arrow.y)
                lineTo(arrow.x - k / 2, arrow.y + v)
            }
            drawPath(chevron, ink.copy(alpha = 0.62f), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Choices one under another, for those too long to sit side by side: the chosen one raised, its dot filled. */
@Composable
private fun <T> ChoiceList(
    options: List<T>,
    selected: T?,
    c: SetupColours,
    tag: String,
    describe: (T) -> String,
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.fill).padding(3.dp).selectableGroup()) {
        options.forEach { o ->
            val chosen = o == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .then(if (chosen) Modifier.shadow(2.dp, shape, clip = false).background(c.pill, shape) else Modifier)
                    .clip(shape)
                    .selectable(selected = chosen, role = Role.RadioButton) {
                        if (!chosen) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(o)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("$tag-${describe(o)}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).border(2.dp, if (chosen) c.ring else c.soft, CircleShape).padding(4.dp)) {
                    if (chosen) Box(Modifier.fillMaxSize().background(c.ring, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    describe(o), color = if (chosen) c.text else c.soft,
                    style = SetupLabel.copy(fontSize = 14.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                )
            }
        }
    }
}

/** A setting that's on or off, in the card's colours: its name (and a line about it) and a switch. */
@Composable
private fun SetupSwitch(text: String, about: String?, on: Boolean, c: SetupColours, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(horizontal = Edge, vertical = 7.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = c.text, style = SetupBody)
            if (about != null) Text(about, color = c.soft, style = SetupLabel.copy(fontWeight = FontWeight.Normal), modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = on, onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = c.onButton, checkedTrackColor = c.button, checkedBorderColor = c.button,
                uncheckedThumbColor = c.soft, uncheckedTrackColor = c.fill, uncheckedBorderColor = c.soft,
            ),
        )
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

/**
 * How settings are kept: changes to how a book looks while it's read are that book's own, till they're set as the
 * default; each way the device is held has settings of its own; and so do pages and scrolling.
 */
@Composable
private fun KeptStep(c: SetupColours) {
    StepTitle(tr("Each book's own settings"), null, c)
    Column(Modifier.padding(start = Edge, end = Edge, top = 10.dp)) {
        IconLine(
            Icons.AutoMirrored.Outlined.MenuBook, tr("Change the text, page or layout while reading, and only that book changes."), c,
            note = tr("Tap Set as default in Reading settings to use them for every book."),
        )
        IconLine(Icons.Outlined.ScreenRotation, tr("Portrait and landscape have separate settings."), c)
        IconLine(
            Icons.Outlined.SwapVert, tr("Pages and scrolling have separate settings too."), c,
            note = tr("Set each up as you like: changing one leaves the other as it is."),
        )
    }
}

@Composable
private fun DoneStep(s: ReaderSettings, c: SetupColours) {
    StepTitle(tr("You’re all set"), tr("Change anything later in Reading settings."), c)
    Column(Modifier.padding(start = Edge, end = Edge, top = 10.dp)) {
        IconLine(
            Icons.Outlined.Swipe,
            if (s.edgeBack) tr("Swipe in from either edge to go back.") else tr("Swipe from the left edge for contents, or the right edge for settings."), c,
        )
        when (s.levelSide) {
            LevelSide.LEFT -> tr("Swipe up or down the left edge to change the brightness.")
            LevelSide.RIGHT -> tr("Swipe up or down the right edge to change the brightness.")
            LevelSide.BOTH -> tr("Swipe up or down either edge to change the brightness.")
            LevelSide.OFF -> null
        }?.let { IconLine(Icons.Outlined.BrightnessMedium, it, c) }
        IconLine(Icons.Outlined.BorderColor, tr("Hold a word to highlight it, add a note or look it up."), c)
    }
}
