package dev.kolib.app.ui.textedit

import dev.kolib.app.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import dev.kolib.app.ui.PickRange
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.kolib.app.epub.Chapter
import dev.kolib.app.epub.EpubText
import dev.kolib.app.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** What can be done to a chapter from its menu. */
sealed interface ChapterAction {
    data class Rename(val chapter: Chapter) : ChapterAction
    data class Split(val chapter: Chapter) : ChapterAction
    data class Merge(val chapter: Chapter) : ChapterAction
    data class Delete(val chapter: Chapter) : ChapterAction
    /** Several chapters, chosen together, to delete. */
    data class DeleteMany(val chapters: List<Chapter>) : ChapterAction
    /** A new, empty chapter after [after] (the last one if null). */
    data class Add(val after: String?) : ChapterAction
    /** A copy of the chapter, straight after it. */
    data class Duplicate(val chapter: Chapter) : ChapterAction
}

/** Moving chosen chapters together, keeping them in the order they were in. */
internal object ChapterMoves {
    /** [order] with each of [picked] a place earlier ([by] < 0) or later; any already at that end stay there. */
    fun step(order: List<String>, picked: Set<String>, by: Int): List<String> {
        val o = order.toMutableList()
        if (by < 0) {
            for (i in 1 until o.size) if (o[i] in picked && o[i - 1] !in picked) o[i - 1] = o[i].also { o[i] = o[i - 1] }
        } else {
            for (i in o.size - 2 downTo 0) if (o[i] in picked && o[i + 1] !in picked) o[i + 1] = o[i].also { o[i] = o[i + 1] }
        }
        return o
    }

    /** [order] with [picked] taken out and put back together, in their order, after [after] (at the start if null). */
    fun after(order: List<String>, picked: Set<String>, after: String?): List<String> {
        val moving = order.filter { it in picked }
        val rest = order.filter { it !in picked }
        val at = if (after == null) 0 else rest.indexOf(after) + 1
        return rest.take(at) + moving + rest.drop(at)
    }
}

/**
 * The book's chapters in reading order. Drag one by its handle, or hold it and drag, to move it; held and let
 * go, it's chosen, and more can be chosen to move or delete together. Its menu renames, splits, joins, hides,
 * deletes or makes it the start or the cover page.
 */
@Composable
fun ChaptersPane(
    state: TextEditState,
    onOpen: (String) -> Unit,
    onAction: (ChapterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val version = state.version
    val chapters = remember(version) { state.structure.chapters }
    val startPath = state.startPath
    val coverPath = state.coverPath
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val words by produceState(emptyMap<String, Int>(), version) {
        value = withContext(Dispatchers.Default) {
            chapters.associate { c -> c.path to (state.edit.text(c.path)?.let { EpubText.countWords(EpubText.plainText(it)) } ?: 0) }
        }
    }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Which way the finger last went (-1 up, 1 down), so the list only runs on the way it's being dragged.
    var heading by remember { mutableIntStateOf(0) }
    // The order shown, which follows the book; a drag still going when the book changes keeps its place.
    val lastOrder = remember { arrayOfNulls<MutableState<List<String>>>(1) }
    val orderState = remember(version) {
        // Each page once, even in a book whose reading order lists one twice (a faulty book): the list goes by them.
        val book = chapters.map { it.path }.distinct()
        val inFlight = lastOrder[0]?.value?.takeIf { dragging != null && it.toSet() == book.toSet() }
        mutableStateOf(inFlight ?: book).also { lastOrder[0] = it }
    }
    val order by orderState
    // The gestures outlive changes to the book, so they go by these, which always hold the latest.
    val latestOrder by rememberUpdatedState(orderState)
    val latestChapters by rememberUpdatedState(chapters)
    val listState = rememberLazyListState()
    val byPath = remember(chapters) { chapters.associateBy { it.path } }

    // A chapter that's just been added (a new one, a copy, a cover page at the front) is brought into view. The
    // list keeps its place when the book changes, so one added above what's showing would be out of sight.
    val listed = remember { arrayOfNulls<Set<String>>(1) }
    LaunchedEffect(order) {
        val before = listed[0]
        listed[0] = order.toSet()
        if (before == null || dragging != null) return@LaunchedEffect
        val added = order.indices.filter { order[it] !in before }
        if (added.isEmpty()) return@LaunchedEffect
        val target = added.firstOrNull { order[it] == state.currentPath } ?: added.first()
        // Once the list has been laid out with it in.
        withFrameNanos { }
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == target }
        val inView = row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset - info.afterContentPadding
        // With the chapter before it showing too, so it's seen where it went.
        if (!inView) listState.animateScrollToItem((target - 1).coerceAtLeast(0))
    }

    // ---- choosing several ---------------------------------------------------------------------
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    val range = remember { PickRange() }
    var moving by remember { mutableStateOf(false) }

    fun stopSelecting() {
        selecting = false
        picked.clear()
        range.reset()
    }

    /** Held: chooses it, or, choosing already, everything between it and the one chosen last. */
    fun hold(path: String) {
        if (!selecting) {
            selecting = true
            picked.clear()
            range.reset()
        }
        range.hold(picked, path, latestOrder.value)
    }

    fun tap(path: String) {
        if (!selecting) {
            onOpen(path)
            return
        }
        range.toggle(picked, path)
        if (picked.isEmpty()) stopSelecting()
    }

    // Chosen chapters that were deleted are let go; with none left, choosing ends.
    LaunchedEffect(chapters) {
        val live = chapters.mapTo(HashSet()) { it.path }
        val gone = picked.keys.filter { it !in live }
        if (gone.isNotEmpty()) {
            gone.forEach { picked.remove(it) }
            if (picked.isEmpty()) stopSelecting()
        }
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    fun reorderTo(next: List<String>, count: Int) {
        if (next == latestOrder.value) return
        latestOrder.value = next
        state.reorder(next, if (count == 1) tr("Moved a chapter") else tr("Moved {0} chapters", count))
    }

    // ---- dragging -----------------------------------------------------------------------------
    fun dragBy(dy: Float) {
        val key = dragging ?: return
        dragOffset += dy
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo
        val cur = info.firstOrNull { it.key == key } ?: return
        val now = latestOrder.value
        // Until the list has been laid out again after the last move, it's not known where things are.
        if (cur.index != now.indexOf(key)) return
        // Dragged past the top or bottom, it goes to whichever chapter is at that edge.
        val center = (cur.offset + dragOffset + cur.size / 2f)
            .coerceIn(layout.viewportStartOffset.toFloat(), (layout.viewportEndOffset - 1).toFloat().coerceAtLeast(layout.viewportStartOffset.toFloat()))
        val target = info.firstOrNull { it.key != key && it.key is String && center >= it.offset && center < it.offset + it.size } ?: return
        val from = now.indexOf(key)
        val to = now.indexOf(target.key as String)
        if (from < 0 || to < 0) return
        // The list keeps its top chapter in place when the chapters move; when that's one of these two, it stays where it is instead.
        val first = listState.firstVisibleItemIndex
        if (cur.index == first || target.index == first) listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        latestOrder.value = now.toMutableList().apply { add(to, removeAt(from)) }
        val landsAt = if (target.index > cur.index) target.offset + target.size - cur.size else target.offset
        dragOffset += (cur.offset - landsAt)
    }

    fun startDrag(path: String) {
        if (dragging != null) return
        dragging = path
        dragOffset = 0f
        heading = 0
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun fingerBy(dy: Float) {
        if (dy != 0f) heading = if (dy < 0) -1 else 1
        dragBy(dy)
    }

    fun endDrag() {
        val now = latestOrder.value
        if (dragging != null && now != latestChapters.map { it.path }) state.reorder(now)
        dragging = null
        dragOffset = 0f
        heading = 0
    }

    // Near the top or bottom while dragging that way, the list runs, faster the further past the edge.
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val fastest = with(density) { 14.dp.toPx() }
    LaunchedEffect(dragging) {
        while (isActive && dragging != null) {
            withFrameNanos { }
            val key = dragging ?: break
            val info = listState.layoutInfo
            val cur = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = cur.offset + dragOffset
            val bottom = top + cur.size
            val step = when {
                heading < 0 && top < info.viewportStartOffset + edge -> -fastest * ((info.viewportStartOffset + edge - top) / edge).coerceIn(0.2f, 1f)
                heading > 0 && bottom > info.viewportEndOffset - edge -> fastest * ((bottom - info.viewportEndOffset + edge) / edge).coerceIn(0.2f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                val moved = listState.scrollBy(step)
                if (moved != 0f) dragBy(moved)
            }
        }
    }

    Column(modifier) {
        if (selecting) {
            val all = picked.size == order.size
            val chosen = picked.keys.toSet()
            val canUp = order.indices.any { i -> i > 0 && order[i] in chosen && order[i - 1] !in chosen }
            val canDown = order.indices.any { i -> i < order.size - 1 && order[i] in chosen && order[i + 1] !in chosen }
            val someLeft = picked.isNotEmpty() && !all
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { stopSelecting() }) { Icon(Icons.Filled.Close, tr("Stop selecting")) }
                    Text(
                        when (picked.size) {
                            0 -> tr("Tap chapters to select them")
                            1 -> tr("1 chapter selected")
                            else -> tr("{0} chapters selected", picked.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (all) {
                            picked.clear()
                            range.reset()
                        } else order.forEach { picked[it] = true }
                    }) { Text(if (all) tr("Select none") else tr("Select all")) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { reorderTo(ChapterMoves.step(latestOrder.value, chosen, -1), chosen.size) }, enabled = canUp) {
                        Icon(Icons.Outlined.ArrowUpward, tr("Move up"))
                    }
                    IconButton(onClick = { reorderTo(ChapterMoves.step(latestOrder.value, chosen, 1), chosen.size) }, enabled = canDown) {
                        Icon(Icons.Outlined.ArrowDownward, tr("Move down"))
                    }
                    TextButton(onClick = { moving = true }, enabled = someLeft) { Text(tr("Move to…")) }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onAction(ChapterAction.DeleteMany(order.filter { it in chosen }.mapNotNull { byPath[it] })) },
                        enabled = someLeft,
                    ) {
                        Text(tr("Delete…"), color = if (someLeft) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Chapters"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Drag the dots to reorder, hold to select · {0}", Fmt.plural(chapters.size, "chapter")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onAction(ChapterAction.Add(state.currentPath)) }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Add"))
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("Table of contents options")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Select chapters")) },
                            leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                            onClick = { menu = false; selecting = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Rebuild contents from headings")) },
                            leadingIcon = { Icon(Icons.Outlined.Toc, null) },
                            onClick = { menu = false; state.contentsFromHeadings() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Make a cover page")) },
                            leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                            enabled = coverPath == null,
                            onClick = { menu = false; state.makeCoverPage(context) },
                        )
                    }
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(order, key = { _, p -> p }) { index, path ->
                val c = byPath[path] ?: return@itemsIndexed
                val isDragged = dragging == path
                ChapterRow(
                    chapter = c,
                    number = index + 1,
                    words = words[path],
                    current = path == state.currentPath,
                    isStart = path == startPath,
                    isCover = path == coverPath,
                    dragged = isDragged,
                    selecting = selecting,
                    picked = picked[path] == true,
                    first = index == 0,
                    last = index == order.size - 1,
                    onTap = { tap(path) },
                    onHold = { hold(path) },
                    onAction = onAction,
                    onMove = { delta ->
                        val o = latestOrder.value.toMutableList()
                        val i = o.indexOf(path)
                        val j = (i + delta).coerceIn(0, o.size - 1)
                        if (i >= 0 && i != j) {
                            o.add(j, o.removeAt(i))
                            reorderTo(o, 1)
                        }
                    },
                    onHide = { state.setHidden(path, c.linear) },
                    onStart = { state.setStart(path) },
                    onCover = { state.setCoverPage(path) },
                    onCopy = if (state.copied != null) ({ state.copySection(path) }) else null,
                    handle = Modifier.pointerInput(path) {
                        dragAtOnce(onStart = { startDrag(path) }, onDrag = { fingerBy(it) }, onEnd = { endDrag() })
                    },
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (!isDragged) 0f else {
                                // Kept in sight while the list runs under it.
                                val info = listState.layoutInfo
                                val cur = info.visibleItemsInfo.firstOrNull { it.key == path }
                                val low = cur?.let { (info.viewportStartOffset - it.offset).toFloat() }
                                val high = cur?.let { (info.viewportEndOffset - it.offset - it.size).toFloat() }
                                if (low == null || high == null || high < low) dragOffset else dragOffset.coerceIn(low, high)
                            }
                        }
                        .then(if (dragging == null) Modifier.animateItem() else Modifier)
                        // Held, it's chosen there and then; chapters are moved by their handles alone, so the list scrolls
                        // under a finger anywhere else.
                        .pointerInput(path) {
                            holdToChoose {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                hold(path)
                            }
                        }
                        .semantics { onLongClick(tr("Select")) { hold(path); true } },
                )
            }
        }
    }

    if (moving) {
        val chosen = picked.keys.toSet()
        val rest = order.filter { it !in chosen }
        fun place(after: String?) {
            moving = false
            reorderTo(ChapterMoves.after(latestOrder.value, chosen, after), chosen.size)
        }
        AlertDialog(
            onDismissRequest = { moving = false },
            title = { Text(tr("Move {0} to…", Fmt.plural(chosen.size, "chapter"))) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item { MovePlace(null, tr("The start of the book")) { place(null) } }
                    items(rest, key = { it }) { p -> MovePlace(order.indexOf(p) + 1, tr("After “{0}”", byPath[p]?.title ?: p)) { place(p) } }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moving = false }) { Text(tr("Cancel")) } },
        )
    }
}

/** A place chosen chapters can be moved to: the start, or after chapter [number]. */
@Composable
private fun MovePlace(number: Int?, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number?.toString() ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A drag on a chapter's handle: it starts as soon as the finger moves. Whether lifted or not, it ends ([onEnd]). */
private suspend fun PointerInputScope.dragAtOnce(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var over = 0f
        val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            over = overSlop
        } ?: return@awaitEachGesture
        onStart()
        try {
            onDrag(over)
            verticalDrag(slop.id) { change ->
                onDrag(change.positionChange().y)
                change.consume()
            }
        } finally {
            onEnd()
        }
    }
}

/**
 * A chapter's row held down: it's [onHold] as soon as it's been held long enough, not when it's let go, so it's plain
 * it's been chosen. The rest of the press is taken before the row sees it, so it doesn't count as a tap too.
 */
private suspend fun PointerInputScope.holdToChoose(onHold: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        onHold()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == held.id } ?: break
            change.consume()
            if (!change.pressed) break
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    number: Int,
    words: Int?,
    current: Boolean,
    isStart: Boolean,
    isCover: Boolean,
    dragged: Boolean,
    selecting: Boolean,
    picked: Boolean,
    first: Boolean,
    last: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onAction: (ChapterAction) -> Unit,
    onMove: (Int) -> Unit,
    onHide: () -> Unit,
    onStart: () -> Unit,
    onCover: () -> Unit,
    /** Copies it into the copied sections, to add to any book. */
    onCopy: (() -> Unit)? = null,
    handle: Modifier,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = when {
            dragged -> MaterialTheme.colorScheme.surfaceContainerHighest
            picked -> MaterialTheme.colorScheme.primaryContainer
            current && !selecting -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .then(if (dragged) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap),
    ) {
        Row(Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp, end = if (selecting) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = picked, onCheckedChange = { onTap() })
                }
            } else {
                Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DragIndicator, tr("Drag to reorder"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = if (!chapter.linear) FontStyle.Italic else null,
                    color = if (!chapter.linear) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        words?.let { if (it == 1) tr("1 word") else tr("{0} words", "%,d".format(it)) },
                        if (!chapter.linear) tr("hidden") else null,
                        if (isCover) tr("cover page") else null,
                        if (isStart) tr("book opens here") else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selecting) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("Options for “{0}”", chapter.title)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Rename…")) }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = { menu = false; onAction(ChapterAction.Rename(chapter)) })
                    onCopy?.let { copy ->
                        DropdownMenuItem(text = { Text(tr("Copy")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; copy() })
                    }
                    DropdownMenuItem(text = { Text(tr("Duplicate")) }, leadingIcon = { Icon(Icons.Outlined.FileCopy, null) },
                        onClick = { menu = false; onAction(ChapterAction.Duplicate(chapter)) })
                    DropdownMenuItem(text = { Text(tr("Select")) }, leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                        onClick = { menu = false; onHold() })
                    DropdownMenuItem(text = { Text(tr("Add chapter after…")) }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { menu = false; onAction(ChapterAction.Add(chapter.path)) })
                    DropdownMenuItem(text = { Text(tr("Split into two…")) }, leadingIcon = { Icon(Icons.Outlined.CallSplit, null) },
                        onClick = { menu = false; onAction(ChapterAction.Split(chapter)) })
                    DropdownMenuItem(text = { Text(tr("Join with previous chapter")) }, leadingIcon = { Icon(Icons.Outlined.CallMerge, null) }, enabled = !first,
                        onClick = { menu = false; onAction(ChapterAction.Merge(chapter)) })
                    DropdownMenuItem(text = { Text(tr("Move up")) }, leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }, enabled = !first,
                        onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text(tr("Move down")) }, leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }, enabled = !last,
                        onClick = { menu = false; onMove(1) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (chapter.linear) tr("Hide (skipped when reading)") else tr("Show again")) },
                        leadingIcon = { Icon(if (chapter.linear) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) },
                        onClick = { menu = false; onHide() },
                    )
                    DropdownMenuItem(text = { Text(tr("Open the book here")) }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }, enabled = !isStart,
                        onClick = { menu = false; onStart() })
                    DropdownMenuItem(text = { Text(tr("Use as the cover page")) }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, enabled = !isCover,
                        onClick = { menu = false; onCover() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(tr("Delete…"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onAction(ChapterAction.Delete(chapter)) },
                    )
                }
            }
        }
    }
}
