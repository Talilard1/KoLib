package dev.kobolibrary.app.ui.textedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
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
import dev.kobolibrary.app.epub.Chapter
import dev.kobolibrary.app.epub.EpubText
import dev.kobolibrary.app.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** What can be done to a chapter from its menu. */
sealed interface ChapterAction {
    data class Rename(val chapter: Chapter) : ChapterAction
    data class Split(val chapter: Chapter) : ChapterAction
    data class Merge(val chapter: Chapter) : ChapterAction
    data class Delete(val chapter: Chapter) : ChapterAction
    /** A new, empty chapter after [after] (the last one if null). */
    data class Add(val after: String?) : ChapterAction
}

/**
 * The book's chapters in reading order. Drag one by its handle (or press and hold it) to move it;
 * its menu renames, splits, joins, hides, deletes or makes it the start.
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
    val words by produceState(emptyMap<String, Int>(), version) {
        value = withContext(Dispatchers.Default) {
            chapters.associate { c -> c.path to (state.edit.text(c.path)?.let { EpubText.countWords(EpubText.plainText(it)) } ?: 0) }
        }
    }
    var order by remember(version) { mutableStateOf(chapters.map { it.path }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val byPath = remember(chapters) { chapters.associateBy { it.path } }

    fun dragBy(dy: Float) {
        val key = dragging ?: return
        dragOffset += dy
        val info = listState.layoutInfo.visibleItemsInfo
        val cur = info.firstOrNull { it.key == key } ?: return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = info.firstOrNull { it.key != key && it.key is String && center >= it.offset && center <= it.offset + it.size } ?: return
        val from = order.indexOf(key)
        val to = order.indexOf(target.key as String)
        if (from < 0 || to < 0) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += (cur.offset - target.offset)
    }

    fun endDrag() {
        if (dragging != null && order != chapters.map { it.path }) state.reorder(order)
        dragging = null
        dragOffset = 0f
    }

    // Near the top or bottom while dragging, the list scrolls.
    LaunchedEffect(dragging) {
        while (isActive && dragging != null) {
            val info = listState.layoutInfo
            val cur = info.visibleItemsInfo.firstOrNull { it.key == dragging }
            if (cur != null) {
                val top = cur.offset + dragOffset
                val bottom = top + cur.size
                val step = when {
                    top < info.viewportStartOffset + 40 -> -18f
                    bottom > info.viewportEndOffset - 40 -> 18f
                    else -> 0f
                }
                if (step != 0f) {
                    val moved = listState.scrollBy(step)
                    dragBy(moved)
                }
            }
            delay(16)
        }
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chapters", style = MaterialTheme.typography.titleMedium)
                Text("Drag to reorder · ${Fmt.plural(chapters.size, "chapter")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onAction(ChapterAction.Add(state.currentPath)) }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Table of contents options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rebuild contents from headings") },
                        leadingIcon = { Icon(Icons.Outlined.Toc, null) },
                        onClick = { menu = false; state.contentsFromHeadings() },
                    )
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
                    dragged = isDragged,
                    first = index == 0,
                    last = index == order.size - 1,
                    onOpen = { onOpen(path) },
                    onAction = onAction,
                    onMove = { delta ->
                        val o = order.toMutableList()
                        val i = o.indexOf(path)
                        val j = (i + delta).coerceIn(0, o.size - 1)
                        if (i != j) {
                            o.add(j, o.removeAt(i))
                            state.reorder(o)
                        }
                    },
                    onHide = { state.setHidden(path, c.linear) },
                    onStart = { state.setStart(path) },
                    handle = Modifier.pointerInput(path) {
                        detectDragGestures(
                            onDragStart = { dragging = path; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        )
                    },
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .then(if (dragging == null) Modifier.animateItem() else Modifier)
                        .pointerInput(path) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = path; dragOffset = 0f },
                                onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                            )
                        },
                )
            }
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
    dragged: Boolean,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onAction: (ChapterAction) -> Unit,
    onMove: (Int) -> Unit,
    onHide: () -> Unit,
    onStart: () -> Unit,
    handle: Modifier,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = when {
            dragged -> MaterialTheme.colorScheme.surfaceContainerHighest
            current -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .then(if (dragged) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragIndicator, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        words?.let { if (it == 1) "1 word" else "%,d words".format(it) },
                        if (!chapter.linear) "hidden" else null,
                        if (isStart) "book opens here" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for “${chapter.title}”") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = { menu = false; onAction(ChapterAction.Rename(chapter)) })
                    DropdownMenuItem(text = { Text("Add chapter after…") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { menu = false; onAction(ChapterAction.Add(chapter.path)) })
                    DropdownMenuItem(text = { Text("Split into two…") }, leadingIcon = { Icon(Icons.Outlined.CallSplit, null) },
                        onClick = { menu = false; onAction(ChapterAction.Split(chapter)) })
                    DropdownMenuItem(text = { Text("Join with previous chapter") }, leadingIcon = { Icon(Icons.Outlined.CallMerge, null) }, enabled = !first,
                        onClick = { menu = false; onAction(ChapterAction.Merge(chapter)) })
                    DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }, enabled = !first,
                        onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }, enabled = !last,
                        onClick = { menu = false; onMove(1) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (chapter.linear) "Hide (skipped when reading)" else "Show again") },
                        leadingIcon = { Icon(if (chapter.linear) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) },
                        onClick = { menu = false; onHide() },
                    )
                    DropdownMenuItem(text = { Text("Open the book here") }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }, enabled = !isStart,
                        onClick = { menu = false; onStart() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete…", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onAction(ChapterAction.Delete(chapter)) },
                    )
                }
            }
        }
    }
}
