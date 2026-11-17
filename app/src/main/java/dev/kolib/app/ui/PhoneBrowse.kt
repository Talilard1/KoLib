package dev.kolib.app.ui

import dev.kolib.app.tr
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.library.TagPickerDialog

/** A row of chips that scrolls sideways, so the filters take one line on a phone. */
@Composable
fun PhoneChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A filter that's on or off, with a tick while it's on. */
@Composable
fun PhoneToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
    )
}

/**
 * The first chip in the row: the sort order. Its menu lists the orders, with the direction of the
 * current one under it; picking the current one again turns it round.
 */
@Composable
fun <T> PhoneSortChip(
    orders: List<T>,
    current: T,
    descending: Boolean,
    label: (T) -> String,
    direction: (T, Boolean) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text(label(current)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, tr("Sort"), Modifier.size(18.dp)) },
            trailingIcon = { Icon(if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(16.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 520.dp)) {
            orders.forEach { order ->
                val now = order == current
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(order))
                            if (now) {
                                Text(direction(order, descending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    leadingIcon = { if (now) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                    trailingIcon = { if (now) Icon(if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, tr("Reverse order")) },
                    onClick = { onPick(order); open = false },
                )
            }
        }
    }
}

/** A chip that shows one collection's books, picked from a menu. */
@Composable
fun PhoneCollectionChip(collections: List<String>, current: String?, onPick: (String?) -> Unit) {
    if (collections.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = current != null,
            onClick = { open = true },
            label = { Text(current ?: tr("Collection")) },
            leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            DropdownMenuItem(text = { Text(tr("All collections")) }, onClick = { onPick(null); open = false })
            HorizontalDivider()
            collections.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c) },
                    leadingIcon = { if (c == current) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = { onPick(c); open = false },
                )
            }
        }
    }
}

/** A chip that filters by tags, chosen in the tag picker. */
@Composable
fun PhoneTagsChip(tags: List<Pair<String, Int>>, chosen: Set<String>, matchAll: Boolean, onApply: (Set<String>, Boolean) -> Unit) {
    if (tags.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    FilterChip(
        selected = chosen.isNotEmpty(),
        onClick = { open = true },
        label = {
            Text(
                when (chosen.size) {
                    0 -> tr("Tags")
                    1 -> chosen.first()
                    else -> if (matchAll) tr("{0} tags (all)", chosen.size) else tr("{0} tags (any)", chosen.size)
                }
            )
        },
        leadingIcon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
    )
    if (open) {
        TagPickerDialog(
            tags = tags,
            chosen = chosen,
            matchAll = matchAll,
            onApply = { c, all -> onApply(c, all); open = false },
            onDismiss = { open = false },
        )
    }
}

/** Under the chips while searching or filtering: how many books are showing, and a way back to all of them. */
@Composable
fun PhoneNarrowedLine(shown: Int, total: Int, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (shown == total) Fmt.plural(total, "book") else tr("{0} of {1}", shown, Fmt.plural(total, "book")),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text(tr("Clear filters")) }
    }
}

/** An item in a phone's menu of list options. */
class MenuEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** The menu beside a phone's search box: choosing several books, grid or list, and anything else the screen adds. */
@Composable
fun PhoneListMenu(
    grid: Boolean,
    onSelect: () -> Unit,
    onGrid: (Boolean) -> Unit,
    more: List<MenuEntry> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, tr("More options")) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr("Select books")) },
                leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                onClick = { open = false; onSelect() },
            )
            DropdownMenuItem(
                text = { Text(if (grid) tr("Show as list") else tr("Show as grid")) },
                leadingIcon = { Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, null) },
                onClick = { open = false; onGrid(!grid) },
            )
            if (more.isNotEmpty()) {
                HorizontalDivider()
                more.forEach { e ->
                    DropdownMenuItem(
                        text = { Text(e.label) },
                        leadingIcon = { Icon(e.icon, null) },
                        onClick = { open = false; e.onClick() },
                    )
                }
            }
        }
    }
}
