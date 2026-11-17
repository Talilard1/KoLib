package dev.kobolibrary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.Fmt

/** Width of the collections down the left of a Collections tab, where they stay. */
private val ListWidth = 264.dp

/** From this width the collections stay down the left, with the open collection (or a book of it) beside them. */
private val ListStaysWidth = 640.dp

/**
 * From this width the collections, a collection's books (two to a row at least) and a book's details all fit side by
 * side: a tablet on its side, also with Android's display size set larger.
 */
private val ThreePaneWidth = 960.dp

/** A collection in a Collections tab's list: [note] goes after its count ("hidden on the Kobo", "3 on the Kobo"). */
data class CollectionRow(val key: String, val name: String, val count: Int, val note: String? = null, val hidden: Boolean = false)

/** How a Collections tab is laid out, by the room there is. */
enum class CollectionPanes {
    /** One thing at a time: the collections, a collection's books, a book, each with a way back. */
    ONE,
    /** The collections stay down the left; beside them, the open collection's books or one of its books. */
    LIST_AND_ONE,
    /** The collections, the open collection's books and a book's details, side by side. */
    THREE,
}

/**
 * Where a Collections tab is scrolled to, kept while it's away (another tab, or a book open over it on a phone): the
 * list of collections, and the open collection's books.
 */
class CollectionScroll {
    val list = LazyListState()
    private var key: String? = null
    private var grid = LazyGridState()

    /** The books of the collection [of]: where they were left, or from the top when it's another collection. */
    fun grid(of: String?): LazyGridState {
        if (of != key) {
            key = of
            grid = LazyGridState()
        }
        return grid
    }
}

/**
 * The Collections tabs' layout, the Kobo's and the library's. Nothing moves when a collection or a book is tapped:
 * on a wide screen the collections stay down the left, the open collection's books in the middle and a book's
 * details on the right (where the Books tab has them), each in a place of its own. With less room the collections
 * still stay on the left, and a book opens in place of its collection's books, with a way back to them. With the
 * list always in view there's always a collection open beside it: the first ([onOpenFirst]) until another is chosen.
 * On a narrow screen it's one at a time: the collections, a collection's books, a book.
 */
@Composable
fun CollectionsLayout(
    hasCollection: Boolean,
    hasBook: Boolean,
    onOpenFirst: () -> Unit,
    onCloseCollection: () -> Unit,
    onCloseBook: () -> Unit,
    list: @Composable (Modifier, CollectionPanes) -> Unit,
    books: @Composable (onBack: (() -> Unit)?) -> Unit,
    detail: @Composable (onBack: (() -> Unit)?) -> Unit,
    nothingChosen: @Composable () -> Unit,
    noBookChosen: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        when {
            maxWidth >= ThreePaneWidth -> {
                if (!hasCollection) LaunchedEffect(Unit) { onOpenFirst() }
                Row(Modifier.fillMaxSize()) {
                    // The list and the books share the room the Books tab's books have, so a book's details are where they are there.
                    Row(Modifier.weight(1.45f).fillMaxHeight()) {
                        list(Modifier.width(ListWidth).fillMaxHeight(), CollectionPanes.THREE)
                        VerticalDivider()
                        Box(Modifier.weight(1f).fillMaxHeight()) { if (hasCollection) books(null) else nothingChosen() }
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) { if (hasCollection && hasBook) detail(null) else noBookChosen() }
                }
            }
            maxWidth >= ListStaysWidth -> {
                if (!hasCollection) LaunchedEffect(Unit) { onOpenFirst() }
                BackHandler(enabled = hasCollection && hasBook, onBack = onCloseBook)
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(ListWidth).fillMaxHeight(), CollectionPanes.LIST_AND_ONE)
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            hasCollection && hasBook -> detail(onCloseBook)
                            hasCollection -> books(null)
                            else -> nothingChosen()
                        }
                    }
                }
            }
            hasCollection && hasBook -> {
                BackHandler(onBack = onCloseBook)
                detail(onCloseBook)
            }
            hasCollection -> {
                BackHandler(onBack = onCloseCollection)
                books(onCloseCollection)
            }
            else -> list(Modifier.fillMaxSize(), CollectionPanes.ONE)
        }
    }
}

/**
 * The collections, one to a line with how many books each has; [heading] above them. The last can be "Not in any
 * collection". [opens]: a tap goes on to the collection's books (a list on its own), so each line has an arrow.
 */
@Composable
fun CollectionList(
    rows: List<CollectionRow>,
    heading: String,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    state: LazyListState = rememberLazyListState(),
    opens: Boolean = true,
) {
    // On a phone, room under the last for the Add books button.
    LazyColumn(modifier.fillMaxHeight(), state = state, contentPadding = PaddingValues(bottom = if (dev.kobolibrary.app.ui.components.phoneLayout()) 104.dp else 24.dp)) {
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
                trailingContent = if (opens) ({ Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) else null,
                colors = ListItemDefaults.colors(
                    containerColor = if (e.key == selectedKey) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
                // Beside what it shows, the list is a set of tabs, one of them open; on its own, each line goes on to its books.
                modifier = if (opens) Modifier.clickable { onSelect(e.key) }
                else Modifier.selectable(selected = e.key == selectedKey, role = Role.Tab, onClick = { onSelect(e.key) }),
            )
        }
    }
}
