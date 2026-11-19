package dev.kolib.app.ui.ai

import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.Job
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.kolib.app.ai.AiClosed
import dev.kolib.app.KoLibApp
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Button
import dev.kolib.app.ui.reader.SummaryTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The AI's window, the same over every screen of the app: open (where it was put), minimised to a bubble, or
 * closed, when the AI button at the top shows what it's doing. Whichever it is, the AI goes on with what it was
 * asked until it's done or its Stop is pressed.
 */
class AiPanel(private val prefs: SharedPreferences? = null) {
    enum class Mode { CLOSED, OPEN, MINIMISED }

    var mode by mutableStateOf(Mode.CLOSED)
        private set

    /** In a book: which of its tabs shows, the chapter's summary (0) or the chat (1). */
    var tab by mutableIntStateOf(prefs?.getInt("panel_tab", 0) ?: 0)
        private set

    /** Where the window is: how far across and down the room it has to move in (0 to 1, each way). */
    var at by mutableStateOf(Offset(prefs?.getFloat("panel_x", 0.5f) ?: 0.5f, prefs?.getFloat("panel_y", 1f) ?: 1f))
        private set

    /** Where the bubble is, the same way: on one side or the other, at any height. */
    var bubbleAt by mutableStateOf(Offset(prefs?.getFloat("bubble_x", 1f) ?: 1f, prefs?.getFloat("bubble_y", 0.72f) ?: 0.72f))
        private set

    /** The window fills the screen, rather than taking only the room it needs (up to about half the screen). */
    var full by mutableStateOf(prefs?.getBoolean("panel_full", false) ?: false)
        private set

    /** Something finished while the window wasn't open: the bubble and the AI button say so until it's opened. */
    var unseen by mutableStateOf(false)
        private set

    /** The start of what finished last, for the bubble to show for a moment; and when it finished. */
    var lastDone by mutableStateOf<String?>(null)
        private set
    var doneAt by mutableStateOf(0L)
        private set

    /** Where the AI button at the top is on the screen, for the window to warp into as it closes (and out of). */
    var buttonAt by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** Whether the bubble has said how it's used (the first time the window is minimised). */
    var hinted: Boolean
        get() = prefs?.getBoolean("bubble_hinted", false) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("bubble_hinted", value)?.apply()
        }

    fun open(tab: Int? = null) {
        if (tab != null) showTab(tab)
        mode = Mode.OPEN
        unseen = false
    }

    /** For the AI button at the top: opens the window, or closes it if it's open (like its ✕). */
    fun toggle() {
        if (mode == Mode.OPEN) close() else open()
    }

    fun fill(on: Boolean) {
        full = on
        prefs?.edit()?.putBoolean("panel_full", on)?.apply()
    }

    fun showTab(t: Int) {
        tab = t
        prefs?.edit()?.putInt("panel_tab", t)?.apply()
    }

    fun minimise() {
        mode = Mode.MINIMISED
    }

    /** Closed: back to the AI button at the top. What it's doing goes on. */
    fun close() {
        mode = Mode.CLOSED
    }

    fun moveTo(to: Offset) {
        at = Offset(to.x.coerceIn(0f, 1f), to.y.coerceIn(0f, 1f))
        prefs?.edit()?.putFloat("panel_x", at.x)?.putFloat("panel_y", at.y)?.apply()
    }

    fun moveBubbleTo(to: Offset) {
        bubbleAt = Offset(to.x.coerceIn(0f, 1f), to.y.coerceIn(0f, 1f))
        prefs?.edit()?.putFloat("bubble_x", bubbleAt.x)?.putFloat("bubble_y", bubbleAt.y)?.apply()
    }

    /** Something asked of the AI has finished ([what]: the start of the answer). Out of sight, it's flagged. */
    fun finished(what: String, now: Long = System.currentTimeMillis()) {
        if (mode == Mode.OPEN) return
        unseen = true
        lastDone = what.replace(Regex("\\s+"), " ").trim().let { if (it.length > 60) it.take(58).trimEnd() + "…" else it }
        doneAt = now
    }
}

/**
 * Where the AI window is drawn, given by the app's main screen: a screen inside it (a book being read) says what
 * the AI is about there, rather than drawing a window of its own.
 */
class AiHost {
    /** The book being read, while one is open. */
    var book by mutableStateOf<ChatPlace.Book?>(null)

    /** What the library is showing, for the AI to know. */
    var library: LibraryOnScreen? = null
}

val LocalAiHost = staticCompositionLocalOf<AiHost?> { null }

/** Whether the AI's button belongs in a bar: not while the AI is out as its bubble, which stands in for it. */
@Composable
fun aiButtonShown(): Boolean = (LocalContext.current.applicationContext as KoLibApp).aiPanel.mode != AiPanel.Mode.MINIMISED

/**
 * The AI's icon, for the button at the top that opens it: its face, which shows what it's doing (reading, thinking,
 * writing), and a dot once something is done out of sight, until the window is opened.
 */
@Composable
fun AiButtonIcon(description: String, place: ChatPlace? = null) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val panel = app.aiPanel
    Box(
        Modifier.size(28.dp)
            .onGloballyPositioned { panel.buttonAt = it.boundsInRoot() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val settings by app.ai.settings.collectAsState()
        val mood = aiMood(place)
        if (settings.mascotButton) MascotIcon(chosenMascot(), mood, Modifier.fillMaxSize().testTag("ai-button-face"))
        else {
            // The plain icon: in the theme's colour while the AI is at work.
            val working = mood == BotMood.READING || mood == BotMood.THINKING || mood == BotMood.WRITING || mood == BotMood.NOTING
            Icon(
                Icons.Outlined.AutoAwesome, null, Modifier.size(24.dp).testTag("ai-button-plain"),
                tint = if (working) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
            )
        }
        if (panel.unseen) {
            Box(Modifier.align(Alignment.TopEnd).size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape).border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape).testTag("ai-button-done"))
        }
    }
}

/**
 * The AI over the screen: its window where it was put, or its bubble, or nothing (closed). Nothing else of it
 * takes a touch: the page or list under it carries on working around it.
 */
@Composable
fun AiLayer(place: ChatPlace, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val panel = app.aiPanel
    val density = LocalDensity.current
    var layerAt by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { layerAt = it.positionInRoot() }) {
        val room = BubbleRoom(
            w = constraints.maxWidth.toFloat(), h = constraints.maxHeight.toFloat(), margin = px(6f), bubble = with(density) { BUBBLE.toPx() },
            top = WindowInsets.systemBars.getTop(density) + px(10f), bottom = WindowInsets.systemBars.getBottom(density) + px(10f),
        )
        // The window warps into the bubble as it's minimised, and into the AI button at the top as it's closed; out
        // of whichever it was in as it opens. The bubble shows once the window has gone into it.
        val warper = rememberWarper()
        val warp = remember { Animatable(if (panel.mode == AiPanel.Mode.OPEN) 0f else 1f) }
        var windowShown by remember { mutableStateOf(panel.mode == AiPanel.Mode.OPEN) }
        var bubbleShown by remember { mutableStateOf(panel.mode == AiPanel.Mode.MINIMISED) }
        var windowAt by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
        var point by remember { mutableStateOf<Offset?>(null) }
        var pointSize by remember { mutableFloatStateOf(0f) }
        var was by remember { mutableStateOf(panel.mode) }
        val buttonSize = px(28f)
        val noButton = Offset(room.w - px(52f), room.top + px(18f))
        fun button(): Offset = panel.buttonAt?.center?.minus(layerAt) ?: noButton
        LaunchedEffect(panel.mode) {
            val from = was
            was = panel.mode
            when (panel.mode) {
                AiPanel.Mode.OPEN -> {
                    if (from == AiPanel.Mode.MINIMISED) {
                        point = room.centreAt(panel.bubbleAt)
                        pointSize = room.bubble
                    } else {
                        point = button()
                        pointSize = buttonSize
                    }
                    bubbleShown = false
                    windowShown = true
                    warp.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
                }
                AiPanel.Mode.MINIMISED -> {
                    if (windowShown) {
                        point = room.centreAt(panel.bubbleAt)
                        pointSize = room.bubble
                        warp.animateTo(1f, tween(440, easing = FastOutLinearInEasing))
                        windowShown = false
                    }
                    bubbleShown = true
                }
                AiPanel.Mode.CLOSED -> {
                    bubbleShown = false
                    if (windowShown) {
                        point = button()
                        pointSize = buttonSize
                        warp.animateTo(1f, tween(400, easing = FastOutLinearInEasing))
                        windowShown = false
                    }
                }
            }
        }
        if (windowShown) {
            BoxWithConstraints(Modifier.fillMaxSize().warp(warper, { warp.value }, { windowAt }, { point }, pointSize, px(16f))) {
                AiWindow(panel, place) { windowAt = it }
            }
        }
        if (panel.mode == AiPanel.Mode.OPEN) {
            BackHandler { panel.close() }
            // Open, there's nothing to tell of in a notification.
            val context = LocalContext.current
            LaunchedEffect(Unit) { dev.kolib.app.ai.AiAnswerService.seen(context) }
        }
        if (bubbleShown) AiBubble(panel, room, aiMood(place), place is ChatPlace.Library)
    }
}

/**
 * Where the bubble can go in a layer [w] by [h] across: inside a [margin] at the sides, clear of the bars at the
 * [top] and [bottom]. Its place is kept as fractions of that room, so it keeps its place when the screen turns.
 */
private class BubbleRoom(val w: Float, val h: Float, val margin: Float, val bubble: Float, val top: Float, val bottom: Float) {
    private val freeX = (w - 2 * margin - bubble).coerceAtLeast(1f)
    private val freeY = (h - top - bottom - bubble).coerceAtLeast(1f)

    /** Where the bubble's corner may be. */
    val area = androidx.compose.ui.geometry.Rect(margin, top, margin + freeX, top + freeY)

    /** The bubble's corner, at [f] of the room each way. */
    fun at(f: Offset) = Offset(margin + freeX * f.x, top + freeY * f.y)

    fun centreAt(f: Offset) = at(f) + Offset(bubble / 2f, bubble / 2f)

    /** How far across and down the room the bubble's corner at [p] is. */
    fun fraction(p: Offset) = Offset(((p.x - margin) / freeX).coerceIn(0f, 1f), ((p.y - top) / freeY).coerceIn(0f, 1f))

    override fun equals(other: Any?) = other is BubbleRoom && other.w == w && other.h == h && other.top == top && other.bottom == bottom

    override fun hashCode() = (w * 31 + h).hashCode() * 31 + (top * 31 + bottom).hashCode()
}

/** The bubble's size: the character, with a little room round it. */
private val BUBBLE = 76.dp

/** Where the bubble is after a [glide]: how fast it's going then, and how hard it hit an edge on the way (0 if not). */
internal class Glide(val at: Offset, val velocity: Offset, val impact: Float)

/**
 * One step of the bubble thrown across [area] (where its corner may go): from [at] at [velocity] (pixels a second)
 * for [seconds], slowing as it goes ([friction]), and bouncing off the edges with [bounce] of its speed.
 */
internal fun glide(
    at: Offset, velocity: Offset, seconds: Float, area: androidx.compose.ui.geometry.Rect,
    friction: Float = 3.2f, bounce: Float = 0.5f,
): Glide {
    var x = at.x + velocity.x * seconds
    var y = at.y + velocity.y * seconds
    var vx = velocity.x
    var vy = velocity.y
    var impact = 0f
    if (x < area.left) { x = area.left + (area.left - x) * bounce; impact = kotlin.math.abs(vx); vx = -vx * bounce }
    if (x > area.right) { x = area.right - (x - area.right) * bounce; impact = kotlin.math.abs(vx); vx = -vx * bounce }
    if (y < area.top) { y = area.top + (area.top - y) * bounce; impact = maxOf(impact, kotlin.math.abs(vy)); vy = -vy * bounce }
    if (y > area.bottom) { y = area.bottom - (y - area.bottom) * bounce; impact = maxOf(impact, kotlin.math.abs(vy)); vy = -vy * bounce }
    val keep = kotlin.math.exp(-friction * seconds)
    return Glide(Offset(x.coerceIn(area.left, area.right), y.coerceIn(area.top, area.bottom)), Offset(vx * keep, vy * keep), impact)
}

/** Pixels from dp, here. */
@Composable
private fun px(dp: Float): Float = with(LocalDensity.current) { dp.dp.toPx() }

@Composable
private fun BoxWithConstraintsScope.AiWindow(panel: AiPanel, place: ChatPlace, onPlaced: (androidx.compose.ui.geometry.Rect) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KoLibApp
    val hub = app.ai
    val chat = app.chat
    val density = LocalDensity.current
    val ai by hub.settings.collectAsState()
    val files by hub.filesChanged.collectAsState()
    val ready = remember(ai, files) { hub.ready(ai) }
    var setup by remember { mutableStateOf(false) }

    // The room it has: the screen less its bars (and the keyboard, while it's up), and a little margin.
    val margin = px(8f)
    val top = WindowInsets.systemBars.getTop(density) + margin
    val bottom = maxOf(WindowInsets.ime.getBottom(density), WindowInsets.systemBars.getBottom(density)) + margin
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val full = panel.full
    val width = if (full) w - 2 * margin else minOf(px(560f), w - 2 * margin)
    val room = (h - top - bottom).coerceAtLeast(px(180f))
    // As it grows with what's in it, it stops at about half the screen; full, it takes all the room.
    val most = if (full) room else minOf(room, maxOf(h * 0.55f, px(320f)))
    var size by remember { mutableStateOf(IntSize.Zero) }
    val freeX = if (full) 0f else (w - 2 * margin - width).coerceAtLeast(0f)
    val freeY = if (full) 0f else (h - top - bottom - size.height).coerceAtLeast(0f)
    val x = margin + freeX * panel.at.x
    val y = top + freeY * panel.at.y

    // Reading ahead, while the chat shows: when it opens, when the page changes, after each answer.
    val book = place as? ChatPlace.Book
    val chatShowing = ready && (book == null || panel.tab == 1)
    val page = book?.let { "${it.controller.chapterIndex}:${it.controller.page}:${it.controller.chapterProgress}" }
    LaunchedEffect(place, chatShowing, page, chat.asking, chat.readBook, chat.id, chat.turns.size) {
        if (!chatShowing || chat.asking) return@LaunchedEffect
        delay(800)
        chat.warm(hub, place)
    }

    with(density) {
        Surface(
            shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, shadowElevation = 12.dp,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(width.toDp())
                .then(if (full) Modifier.height(most.toDp()) else Modifier.heightIn(max = most.toDp()))
                .onSizeChanged { size = it }
                .onGloballyPositioned { onPlaced(it.boundsInParent()) }
                // A touch on the window is the window's, not the page's under it.
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("ai-window"),
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Its top: a handle to drag it by, and its buttons.
                Column(
                    Modifier.fillMaxWidth().pointerInput(freeX, freeY) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val nx = if (freeX > 0f) (panel.at.x * freeX + drag.x) / freeX else panel.at.x
                            val ny = if (freeY > 0f) (panel.at.y * freeY + drag.y) / freeY else panel.at.y
                            panel.moveTo(Offset(nx, ny))
                        }
                    }.testTag("ai-window-handle"),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = if (book != null && ready) 4.dp else 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (book != null && ready) {
                            TabRow(selectedTabIndex = panel.tab, modifier = Modifier.weight(1f), containerColor = Color.Transparent) {
                                Tab(selected = panel.tab == 0, onClick = { panel.showTab(0) }, text = { Text(tr("Summary")) })
                                Tab(selected = panel.tab == 1, onClick = { panel.showTab(1) }, text = { Text(tr("Ask")) }, modifier = Modifier.testTag("ai-tab-ask"))
                            }
                        } else {
                            Text(tr("AI"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        }
                        IconButton(onClick = { setup = true }) { Icon(Icons.Outlined.Settings, tr("AI and dictionary settings")) }
                        IconButton(onClick = { panel.fill(!full) }, modifier = Modifier.testTag("ai-full")) {
                            if (full) Icon(Icons.Filled.FullscreenExit, tr("Leave full screen"))
                            else Icon(Icons.Filled.Fullscreen, tr("Full screen"))
                        }
                        IconButton(onClick = {
                            panel.minimise()
                        }, modifier = Modifier.testTag("ai-minimise")) { Icon(Icons.Filled.Minimize, tr("Minimise")) }
                        IconButton(onClick = { panel.close() }, modifier = Modifier.testTag("ai-close")) { Icon(Icons.Filled.Close, tr("Close")) }
                    }
                }
                val closed by app.aiClosed.collectAsState()
                closed?.let { why -> ClosedNote(why) { app.aiClosed.value = null } }
                when {
                    !ready -> NotReady(book != null) { setup = true }
                    book != null && panel.tab == 0 -> SummaryTab(book.controller, book.companion, hub, book.language)
                    else -> ChatPane(chat, place, hub, onSettings = { setup = true })
                }
            }
        }
    }
    if (setup) AiSetupDialog(onDismiss = { setup = false })
}

/** KoLib was closed last time while it held the AI model: why, and what may help. */
@Composable
private fun ClosedNote(why: AiClosed, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).testTag("ai-closed-note"),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (why) {
                    AiClosed.MEMORY -> tr("Last time, Android closed KoLib while the AI model was loaded, most likely because the device ran short of memory. A smaller model, or a smaller size of this one, should help.")
                    AiClosed.CRASH -> tr("Last time, KoLib stopped suddenly while the AI model was loaded. If it happens again, try another model.")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("Close"), tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

/** With no model set up: what the AI can do, and the way to set it up. */
@Composable
private fun NotReady(inBook: Boolean, onSetup: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 20.dp)) {
        Text(
            if (inBook) tr("With an AI model set up, KoLib can summarise the chapter you're in and answer questions about the book so far, without giving away what's ahead.")
            else tr("With an AI model set up, you can ask about your library here, and about a book while you read it."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onSetup) { Text(tr("Set up AI")) }
    }
}

/**
 * The window minimised: a bubble that can be put anywhere, or thrown (it slides on and bounces off the edges). Its
 * face shows what the AI is doing, and a dot once something is done, saying so for a moment. A tap opens the window
 * again; dragged onto the ✕ that shows at the foot of the screen, it closes (the AI carries on).
 */
@Composable
private fun AiBubble(panel: AiPanel, room: BubbleRoom, mood: BotMood, library: Boolean) {
    val scope = rememberCoroutineScope()
    val bubble = room.bubble
    val mascot = chosenMascot()
    val motion = rememberMascotMotion()
    motion.mood = mood
    motion.library = library
    motion.shakeReach = px(18f)
    LaunchedEffect(Unit) { motion.appear() }
    val reach = px(64f)
    val gap = px(8f)
    var at by remember { mutableStateOf(room.at(panel.bubbleAt)) }
    var dragging by remember { mutableStateOf(false) }
    var gliding by remember { mutableStateOf<Job?>(null) }
    // It pops into being once the window's gone into it, and shrinks away into the ✕.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f)) }
    // When the room changes (the screen turns), it keeps its place in it.
    LaunchedEffect(room) { if (!dragging && gliding == null) at = room.at(panel.bubbleAt) }
    // Where it's closed: a target at the foot of the screen, while it's being dragged.
    val target = Offset(room.w / 2, room.h - room.bottom - bubble)
    fun over(p: Offset) = hypot(p.x + bubble / 2 - target.x, p.y + bubble / 2 - target.y) < reach
    val overTarget = dragging && over(at)
    motion.scared = overTarget
    var hint by remember { mutableStateOf(!panel.hinted) }
    LaunchedEffect(Unit) {
        if (hint) {
            panel.hinted = true
            delay(6_000)
            hint = false
        }
    }
    // A moment's notice that something is done.
    var notice by remember { mutableStateOf(false) }
    LaunchedEffect(panel.doneAt) {
        if (panel.unseen && System.currentTimeMillis() - panel.doneAt < 3_000) {
            notice = true
            delay(4_500)
            notice = false
        }
    }
    // The label beside it, as big as its words: placed once it's been measured, on the side with more room.
    var labelSize by remember { mutableStateOf(IntSize.Zero) }

    with(LocalDensity.current) {
        if (dragging) {
            Box(
                Modifier.offset { IntOffset((target.x - 28.dp.toPx()).roundToInt(), (target.y - 28.dp.toPx()).roundToInt()) }.size(56.dp)
                    .scale(if (overTarget) 1.2f else 1f)
                    .background(if (overTarget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f), CircleShape)
                    .testTag("ai-bubble-close"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, tr("Close"), tint = if (overTarget) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.inverseOnSurface) }
        }
        val label = when {
            hint -> tr("Tap to open. Drag to the ✕ to close.")
            notice -> tr("Answer ready")
            else -> null
        }
        if (label != null && !dragging && gliding == null) {
            val rightSide = at.x + bubble / 2 < room.w / 2
            Surface(
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            if (rightSide) (at.x + bubble + gap).roundToInt() else (at.x - gap - labelSize.width).roundToInt(),
                            (at.y + bubble / 2 - labelSize.height / 2f).roundToInt(),
                        )
                    }
                    .widthIn(max = 220.dp)
                    .onSizeChanged { labelSize = it }
                    .graphicsLayer { alpha = if (labelSize == IntSize.Zero) 0f else shown.value.coerceIn(0f, 1f) },
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("ai-bubble-label"),
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                .size(BUBBLE)
                .graphicsLayer {
                    val s = shown.value
                    scaleX = s
                    scaleY = s
                    alpha = s.coerceIn(0f, 1f)
                }
                .pointerInput(room) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            gliding?.cancel()
                            gliding = null
                            dragging = true
                            motion.held = true
                            motion.velocity = Offset.Zero
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            dragging = false
                            motion.held = false
                            if (over(at)) {
                                scope.launch {
                                    shown.animateTo(0f, tween(160))
                                    panel.close()
                                }
                            } else {
                                // Thrown: it slides on at the speed it was let go, slowing, until it stops.
                                val v = tracker.calculateVelocity()
                                var velocity = Offset(v.x, v.y)
                                val most = 9_000f
                                if (velocity.getDistance() > most) velocity *= most / velocity.getDistance()
                                gliding = scope.launch {
                                    // It feels the flight: leaning into it, bumping off the edges, dizzy after a hard throw.
                                    val peak = velocity.getDistance()
                                    var bounces = 0
                                    try {
                                        var last = withFrameNanos { it }
                                        while (velocity.getDistance() > 40f) {
                                            val now = withFrameNanos { it }
                                            val step = glide(at, velocity, ((now - last) / 1e9f).coerceIn(0f, 0.05f), room.area)
                                            last = now
                                            at = step.at
                                            velocity = step.velocity
                                            motion.velocity = velocity
                                            if (step.impact > 0f) {
                                                bounces++
                                                motion.bump(step.impact)
                                            }
                                        }
                                    } finally {
                                        motion.velocity = Offset.Zero
                                    }
                                    motion.thrown(peak, bounces)
                                    panel.moveBubbleTo(room.fraction(at))
                                    gliding = null
                                }
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            motion.held = false
                            panel.moveBubbleTo(room.fraction(at))
                        },
                    ) { change, drag ->
                        change.consume()
                        // Where the finger is on the screen, for how fast it's moving when it lets go.
                        tracker.addPosition(change.uptimeMillis, at + change.position)
                        // Shaken about, it gets dizzy.
                        motion.dragged(drag)
                        motion.velocity = Offset(drag.x * 60f, drag.y * 60f) * 0.2f
                        at = Offset(
                            (at.x + drag.x).coerceIn(room.area.left, room.area.right),
                            (at.y + drag.y).coerceIn(room.area.top, room.area.bottom),
                        )
                    }
                }
                .pointerInput(Unit) { detectTapGestures { panel.open() } }
                .semantics { contentDescription = tr("Open the AI"); onClick { panel.open(); true } }
                .testTag("ai-bubble"),
            contentAlignment = Alignment.Center,
        ) {
            // The character itself; what goes on round it (thoughts, sleep, stars) may go outside its box.
            MascotView(mascot, motion, Modifier.fillMaxSize().testTag("ai-bubble-face"))
            if (panel.unseen) Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(14.dp).background(MaterialTheme.colorScheme.error, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape).testTag("ai-bubble-done"))
        }
    }
}
