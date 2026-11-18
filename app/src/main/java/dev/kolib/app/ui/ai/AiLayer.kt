package dev.kolib.app.ui.ai

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
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

    /** Something finished while the window wasn't open: the bubble and the AI button say so until it's opened. */
    var unseen by mutableStateOf(false)
        private set

    /** The start of what finished last, for the bubble to show for a moment; and when it finished. */
    var lastDone by mutableStateOf<String?>(null)
        private set
    var doneAt by mutableStateOf(0L)
        private set

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

/** Whether the AI is working on something for the reader where they can't see it: a question, or the chapter's summary. */
@Composable
fun aiBusy(place: ChatPlace? = null): Boolean {
    val app = LocalContext.current.applicationContext as KoLibApp
    val summary = (place as? ChatPlace.Book)?.companion?.stage?.collectAsState()?.value
    return app.chat.asking || summary != null
}

/**
 * The AI's icon, for the button at the top that opens it: with a ring around it while it's working on something
 * out of sight, and a dot once something is done there, until the window is opened.
 */
@Composable
fun AiButtonIcon(description: String, place: ChatPlace? = null) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val panel = app.aiPanel
    val busy = aiBusy(place)
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.AutoAwesome, description)
        if (busy && panel.mode != AiPanel.Mode.OPEN) {
            CircularProgressIndicator(Modifier.size(34.dp).testTag("ai-button-busy"), strokeWidth = 2.dp)
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        when (panel.mode) {
            AiPanel.Mode.OPEN -> {
                AiWindow(panel, place)
                BackHandler { panel.close() }
                // Open, there's nothing to tell of in a notification.
                val context = LocalContext.current
                LaunchedEffect(Unit) { dev.kolib.app.ai.AiAnswerService.seen(context) }
            }
            AiPanel.Mode.MINIMISED -> AiBubble(panel, aiBusy(place))
            AiPanel.Mode.CLOSED -> {}
        }
    }
}

/** Pixels from dp, here. */
@Composable
private fun px(dp: Float): Float = with(LocalDensity.current) { dp.dp.toPx() }

@Composable
private fun BoxWithConstraintsScope.AiWindow(panel: AiPanel, place: ChatPlace) {
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
    val width = minOf(px(560f), w - 2 * margin)
    val most = (h - top - bottom).coerceAtLeast(px(180f))
    var size by remember { mutableStateOf(IntSize.Zero) }
    val freeX = (w - 2 * margin - width).coerceAtLeast(0f)
    val freeY = (h - top - bottom - size.height).coerceAtLeast(0f)
    val x = margin + freeX * panel.at.x
    val y = top + freeY * panel.at.y

    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 420f)) }

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
                .heightIn(max = most.toDp())
                .onSizeChanged { size = it }
                .graphicsLayer {
                    val s = 0.95f + 0.05f * shown.value
                    scaleX = s
                    scaleY = s
                    alpha = shown.value.coerceIn(0f, 1f)
                }
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
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Text(tr("AI"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        }
                        IconButton(onClick = { setup = true }) { Icon(Icons.Outlined.Settings, tr("AI and dictionary settings")) }
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
                    else -> ChatPane(chat, place, hub)
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
 * The window minimised: a bubble at the side of the screen, dragged anywhere and settling at the nearer side.
 * It turns while the AI works, and pulses with a dot once something is done, saying so for a moment. A tap opens
 * the window again; dragged onto the ✕ that shows at the foot of the screen, it closes (the AI carries on).
 */
@Composable
private fun BoxWithConstraintsScope.AiBubble(panel: AiPanel, busy: Boolean) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val margin = px(10f)
    val bubble = px(56f)
    val top = WindowInsets.systemBars.getTop(density) + margin
    val bottom = WindowInsets.systemBars.getBottom(density) + margin
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val freeX = (w - 2 * margin - bubble).coerceAtLeast(1f)
    val freeY = (h - top - bottom - bubble).coerceAtLeast(1f)
    val reach = px(64f)
    val half = px(28f)
    val gap = px(8f)
    // The label beside it, as big as its words: placed once it's been measured.
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val side = remember { Animatable(panel.bubbleAt.x) }
    var dragged by remember { mutableStateOf<Offset?>(null) }
    val x = dragged?.x ?: (margin + freeX * side.value)
    val y = dragged?.y ?: (top + freeY * panel.bubbleAt.y)
    // Where it's closed: a target at the foot of the screen, while it's being dragged.
    val target = Offset(w / 2, h - bottom - bubble)
    val overTarget = dragged?.let { hypot(it.x + bubble / 2 - target.x, it.y + bubble / 2 - target.y) < reach } == true
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
    val pulse by rememberInfiniteTransition(label = "bubble").animateFloat(1f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bubble")

    with(density) {
        if (dragged != null) {
            Box(
                Modifier.offset { IntOffset((target.x - half).roundToInt(), (target.y - half).roundToInt()) }.size(56.dp)
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
        if (label != null && dragged == null) {
            val leftSide = side.value < 0.5f
            Surface(
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            if (leftSide) (x + bubble + gap).roundToInt() else (x - gap - labelSize.width).roundToInt(),
                            (y + bubble / 2 - labelSize.height / 2f).roundToInt(),
                        )
                    }
                    .widthIn(max = 220.dp)
                    .onSizeChanged { labelSize = it }
                    .graphicsLayer { alpha = if (labelSize == IntSize.Zero) 0f else 1f },
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("ai-bubble-label"),
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(56.dp)
                .scale(if (panel.unseen && dragged == null) pulse else 1f)
                .pointerInput(freeX, freeY) {
                    detectDragGestures(
                        onDragStart = { dragged = Offset(margin + freeX * side.value, top + freeY * panel.bubbleAt.y) },
                        onDragEnd = {
                            val d = dragged
                            if (d != null) {
                                if (hypot(d.x + bubble / 2 - target.x, d.y + bubble / 2 - target.y) < reach) {
                                    dragged = null
                                    panel.close()
                                } else {
                                    val fx = ((d.x - margin) / freeX).coerceIn(0f, 1f)
                                    panel.moveBubbleTo(Offset(if (fx < 0.5f) 0f else 1f, ((d.y - top) / freeY).coerceIn(0f, 1f)))
                                    scope.launch {
                                        side.snapTo(fx)
                                        dragged = null
                                        side.animateTo(panel.bubbleAt.x, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                                    }
                                }
                            }
                        },
                        onDragCancel = { dragged = null },
                    ) { change, drag ->
                        change.consume()
                        dragged = (dragged ?: Offset(x, y)) + drag
                    }
                }
                .pointerInput(Unit) { detectTapGestures { panel.open() } }
                .semantics { contentDescription = tr("Open the AI"); onClick { panel.open(); true } }
                .testTag("ai-bubble"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(56.dp).testTag("ai-bubble-busy"), strokeWidth = 3.dp)
            if (panel.unseen) Box(Modifier.align(Alignment.TopEnd).size(14.dp).background(MaterialTheme.colorScheme.error, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape).testTag("ai-bubble-done"))
        }
    }
}
