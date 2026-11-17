package dev.kobolibrary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.Fmt

/** Width of the collections down the left of a Collections tab, and of the strip they fold away to. */
private val BarWidth = 380.dp
private val RailWidth = 64.dp

/** A collection in a Collections tab's list: [note] goes after its count ("hidden on the Kobo", "3 on the Kobo"). */
data class CollectionRow(val key: String, val name: String, val count: Int, val note: String? = null, val hidden: Boolean = false)

/**
 * The Collections tabs' layout, the Kobo's and the library's. On a wide screen the collections go down the left and
 * the chosen collection's books beside them; a book tapped there opens on the right, and the collections fold away
 * to a strip at the edge to make room. Tapping the strip brings them back and puts the book away. On a narrower
 * screen it's one at a time: the collections, a collection's books, a book, each with a way back.
 */
@Composable
fun CollectionsLayout(
    hasCollection: Boolean,
    hasBook: Boolean,
    onCloseCollection: () -> Unit,
    onCloseBook: () -> Unit,
    list: @Composable (Modifier) -> Unit,
    books: @Composable (onBack: (() -> Unit)?) -> Unit,
    detail: @Composable (onBack: (() -> Unit)?) -> Unit,
    nothingChosen: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneWidth) {
            val open = !(hasCollection && hasBook)
            val barWidth by animateDpAsState(if (open) BarWidth else RailWidth, label = "collections")
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(barWidth).fillMaxHeight().clipToBounds()) {
                    if (open) list(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(BarWidth).fillMaxHeight())
                    else CollectionsRail(onCloseBook)
                }
                VerticalDivider()
                if (hasCollection) {
                    Box(Modifier.weight(1.45f).fillMaxHeight()) { books(null) }
                    if (hasBook) {
                        VerticalDivider()
                        Box(Modifier.weight(1f).fillMaxHeight()) { detail(null) }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight()) { nothingChosen() }
                }
            }
        } else when {
            hasCollection && hasBook -> {
                BackHandler(onBack = onCloseBook)
                detail(onCloseBook)
            }
            hasCollection -> {
                BackHandler(onBack = onCloseCollection)
                books(onCloseCollection)
            }
            else -> list(Modifier.fillMaxSize())
        }
    }
}

/** The collections folded away: a strip at the left edge that brings them back. */
@Composable
private fun CollectionsRail(onOpen: () -> Unit) {
    val label = tr("Show collections")
    Column(
        Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, label) }
        Spacer(Modifier.height(14.dp))
        Icon(Icons.Outlined.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        // Down the strip, as a book's spine reads.
        Text(
            tr("Collections"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .layout { measurable, _ ->
                    val p = measurable.measure(Constraints())
                    layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
                }
                .rotate(90f),
        )
    }
}

/** The collections, one to a line with how many books each has; [heading] above them. The last can be "Not in any collection". */
@Composable
fun CollectionList(
    rows: List<CollectionRow>,
    heading: String,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    // On a phone, room under the last for the Add books button.
    LazyColumn(modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = if (dev.kobolibrary.app.ui.components.phoneLayout()) 104.dp else 24.dp)) {
        item {
            Text(
                heading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        items(rows, key = { it.key }) { e ->
            if (e.key == NO_COLLECTION_KEY) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ListItem(
                headlineContent = { Text(e.name) },
                supportingContent = { Text(Fmt.plural(e.count, "book") + (e.note ?: "")) },
                leadingContent = {
                    Icon(
                        when {
                            e.key == NO_COLLECTION_KEY -> Icons.Outlined.Inventory2
                            e.hidden -> Icons.Outlined.VisibilityOff
                            else -> Icons.Outlined.CollectionsBookmark
                        },
                        null,
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                colors = ListItemDefaults.colors(
                    containerColor = if (e.key == selectedKey) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
                modifier = Modifier.clickable { onSelect(e.key) },
            )
        }
    }
}
