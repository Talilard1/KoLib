package dev.kobolibrary.app.ui.textedit

import dev.kobolibrary.app.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.epub.FindReplace

/** Colours for the name-mode flags (red: probably not the name; amber: can't tell), in light and dark looks. */
@Composable
private fun flagColors(flag: FindReplace.Flag): Pair<Color, Color>? {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.4f
    return when (flag) {
        FindReplace.Flag.NOT_NAME -> if (dark) Color(0xFF5C2323) to Color(0xFFFFC9C4) else Color(0xFFFADAD6) to Color(0xFF8C1D18)
        FindReplace.Flag.UNSURE -> if (dark) Color(0xFF4A3B12) to Color(0xFFF6DE9C) else Color(0xFFFCEFC7) to Color(0xFF6B4E00)
        else -> null
    }
}

/**
 * Find & replace: what to look for and what to put instead, every place found with the text around it,
 * and the buttons to go through them. Swiping a place away takes it off the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindPanel(find: FindState, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val visible = find.visible
    // Keep the current place in view as Replace moves down the list.
    LaunchedEffect(find.currentId) {
        val i = visible.indexOfFirst { it.id == find.currentId }
        if (i >= 0) {
            val first = listState.firstVisibleItemIndex
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
            if (i < first || i > last - 1) listState.animateScrollToItem((i - 2).coerceAtLeast(0))
        }
    }
    // Looks as you type.
    LaunchedEffect(find.query, find.matchCase, find.wholeWords, find.nameMode, find.pattern, find.thisChapterOnly) {
        if (find.query.isEmpty() || find.searchedFor == find.options()) return@LaunchedEffect
        delay(450)
        find.search()
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Find & replace"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { find.search(keepRemoved = true) }, enabled = find.query.isNotEmpty()) { Icon(Icons.Filled.Refresh, tr("Search again")) }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close find & replace")) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = find.query,
                onValueChange = { find.query = it },
                label = { Text(if (find.nameMode) tr("Name to find") else tr("Find")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (find.query.isNotEmpty()) IconButton(onClick = { find.query = "" }) { Icon(Icons.Filled.Clear, tr("Clear")) } },
                singleLine = true,
                isError = find.error != null,
                supportingText = find.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { find.search() }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = find.replacement,
                onValueChange = { find.replacement = it },
                label = { Text(tr("Replace with")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            // Wraps rather than scrolls, so no option hides off the edge.
            FlowRow(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = find.nameMode, onClick = { find.nameMode = !find.nameMode }, label = { Text(tr("Name mode")) })
                FilterChip(selected = find.thisChapterOnly, onClick = { find.thisChapterOnly = !find.thisChapterOnly }, label = { Text(tr("This chapter only")) })
                FilterChip(selected = find.matchCase && !find.nameMode, enabled = !find.nameMode, onClick = { find.matchCase = !find.matchCase }, label = { Text(tr("Match case")) })
                FilterChip(selected = find.wholeWords || find.nameMode, enabled = !find.nameMode, onClick = { find.wholeWords = !find.wholeWords }, label = { Text(tr("Whole words")) })
                FilterChip(selected = find.pattern, onClick = { find.pattern = !find.pattern }, label = { Text(tr("Pattern")) })
            }
            if (find.nameMode) {
                Text(
                    tr("Red: probably the ordinary word, not the name. Amber: unsure. Swipe away any you want to skip."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { find.skip() }, enabled = find.pendingCount > 0 && !find.stale) { Text(tr("Skip")) }
                FilledTonalButton(onClick = { find.replaceCurrent() }, enabled = find.pendingCount > 0 && !find.stale) { Text(tr("Replace")) }
                Button(onClick = { find.replaceAll() }, enabled = find.pendingCount > 0 && !find.stale) { Text(tr("Replace all ({0})", find.pendingCount), maxLines = 1) }
            }
        }
        if (find.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val total = find.entries.size
            Text(
                when {
                    find.searchedFor == null -> tr("Type something, then tap Find.")
                    total == 0 -> tr("No matches found.")
                    else -> listOfNotNull(
                        tr("{0} left", find.pendingCount),
                        find.replacedCount.takeIf { it > 0 }?.let { tr("{0} replaced", it) },
                        find.removedCount.takeIf { it > 0 }?.let { tr("{0} skipped", it) },
                    ).joinToString(" · ")
                } + if (find.stale) tr(" · search again to include your new text") else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (find.lastRemoved != null) {
                TextButton(onClick = { find.undoRemove() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Undo"))
                }
            } else if (find.removedCount > 0) {
                TextButton(onClick = { find.restoreAllRemoved() }) { Text(tr("Restore all")) }
            }
        }
        if (find.nameMode && find.flaggedCount > 0) {
            TextButton(onClick = { find.removeFlagged() }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Outlined.FilterAltOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Skip the {0} that probably aren't the name", find.flaggedCount))
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(visible, key = { it.id }) { e ->
                FindEntryRow(
                    e,
                    current = e.id == find.currentId,
                    onClick = { find.select(e) },
                    onRemove = { find.remove(e) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FindEntryRow(e: FindEntry, current: Boolean, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = e.status == EntryStatus.PENDING,
        enableDismissFromEndToStart = e.status == EntryStatus.PENDING,
        onDismiss = { onRemove() },
        backgroundContent = {
            val showing = state.dismissDirection != SwipeToDismissBoxValue.Settled
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (showing) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (showing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Skip this one"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    ) {
        val m = e.match
        val flag = flagColors(m.flag).takeIf { e.status == EntryStatus.PENDING }
        val bg by animateColorAsState(
            when {
                current -> MaterialTheme.colorScheme.secondaryContainer
                flag != null -> flag.first.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            label = "entry",
        )
        Surface(
            color = bg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .then(if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(onClick = onClick),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.chapter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (e.status == EntryStatus.REPLACED) {
                        Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Replaced"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    } else if (m.reason != null && flag != null) {
                        Text(
                            (if (m.flag == FindReplace.Flag.NOT_NAME) tr("Probably not the name: ") else tr("Unsure: ")) + m.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = flag.second,
                        )
                    }
                }
                val hl = MaterialTheme.colorScheme.tertiaryContainer
                val newColor = MaterialTheme.colorScheme.tertiary
                val text = buildAnnotatedString {
                    append(m.before)
                    if (e.status == EntryStatus.REPLACED) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(m.text) }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = newColor)) { append(e.newText.orEmpty()) }
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = hl)) { append(m.text) }
                    }
                    append(m.after)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = if (e.status == EntryStatus.REPLACED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
