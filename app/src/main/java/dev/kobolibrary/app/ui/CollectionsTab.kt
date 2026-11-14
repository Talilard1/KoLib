package dev.kobolibrary.app.ui

import dev.kobolibrary.app.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.ui.components.EmptyState
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.components.ShelfGridBox
import dev.kobolibrary.app.ui.theme.LocalBookcase
import dev.kobolibrary.app.ui.theme.ShelfColors
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import dev.kobolibrary.app.ui.components.phoneLayout

/** Key used for the "not in any collection" entry. */
const val NO_COLLECTION_KEY = "\u0000none"

private data class CollectionEntry(val key: String, val name: String, val books: List<KoboBook>, val hidden: Boolean)

@Composable
fun CollectionsTab(
    view: KoboView,
    live: Boolean,
    selectedKey: String?,
    libraryPaths: Set<String>,
    onSelect: (String?) -> Unit,
    onOpenBook: (String) -> Unit,
    onAddToLibrary: (List<KoboBook>) -> Unit,
) {
    val snapshot = view.snapshot
    val entries = remember(snapshot) {
        snapshot.collections.map { c ->
            CollectionEntry(c.name, c.name, snapshot.booksIn(c), hidden = !c.isVisible)
        } + CollectionEntry(NO_COLLECTION_KEY, tr("Not in any collection"), snapshot.booksWithoutCollection(), hidden = false)
    }
    val selected = entries.firstOrNull { it.key == selectedKey }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneWidth) {
            Row(Modifier.fillMaxSize()) {
                CollectionList(entries, snapshot.collections.size, selected?.key, onSelect, Modifier.width(380.dp))
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) {
                        CollectionBooks(view, live, selected, libraryPaths, null, onOpenBook, onAddToLibrary)
                    } else {
                        EmptyState(Icons.Outlined.CollectionsBookmark, tr("Tap a collection"), tr("Its books will show here."))
                    }
                }
            }
        } else if (selected != null) {
            BackHandler { onSelect(null) }
            CollectionBooks(view, live, selected, libraryPaths, onBack = { onSelect(null) }, onOpenBook = onOpenBook, onAddToLibrary = onAddToLibrary)
        } else {
            CollectionList(entries, snapshot.collections.size, null, onSelect, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CollectionList(
    entries: List<CollectionEntry>,
    collectionCount: Int,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                if (collectionCount == 0) tr("This Kobo has no collections yet.") else tr("{0} on this Kobo", Fmt.plural(collectionCount, "collection")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        items(entries, key = { it.key }) { e ->
            if (e.key == NO_COLLECTION_KEY) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ListItem(
                headlineContent = { Text(e.name) },
                supportingContent = {
                    Text(
                        buildString {
                            append(Fmt.plural(e.books.size, "book"))
                            if (e.hidden) append(tr(" · hidden on the Kobo"))
                        }
                    )
                },
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

@Composable
private fun CollectionBooks(
    view: KoboView,
    live: Boolean,
    entry: CollectionEntry,
    libraryPaths: Set<String>,
    onBack: (() -> Unit)?,
    onOpenBook: (String) -> Unit,
    onAddToLibrary: (List<KoboBook>) -> Unit,
) {
    val notInLibrary = entry.books.filter { it.isSideloaded && it.relativePath?.lowercase() !in libraryPaths }
    val phone = phoneLayout()
    Column(Modifier.fillMaxSize()) {
        // On a phone: back and the name on one line, and the button under them the width of the screen.
        if (phone) Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back to the collections")) }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.plural(entry.books.size, "book"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (live && notInLibrary.isNotEmpty()) {
                FilledTonalButton(onClick = { onAddToLibrary(notInLibrary) }, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp)) {
                    Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (notInLibrary.size == entry.books.size) tr("Add all to library") else tr("Add {0} to library", notInLibrary.size))
                }
            }
        } else Row(
            Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Collections"))
                }
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    Fmt.plural(entry.books.size, "book") + tr(" · tap a book to see its details"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (live && notInLibrary.isNotEmpty()) {
                FilledTonalButton(onClick = { onAddToLibrary(notInLibrary) }) {
                    Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (notInLibrary.size == entry.books.size) tr("Add all to library") else tr("Add {0} to library", notInLibrary.size))
                }
            }
        }
        if (entry.books.isEmpty()) {
            EmptyState(
                Icons.Outlined.CollectionsBookmark,
                tr("Empty collection"),
                if (entry.key == NO_COLLECTION_KEY) tr("Every book on this Kobo is in at least one collection.") else tr("None of this collection's books are on the Kobo right now."),
            )
            return@Column
        }
        val cellPadding = if (phone) 6.dp else 8.dp
        ShelfGridBox(minCell = 132.dp, coverPadding = cellPadding, columns = if (phone) 3 else null) {
            val bookcase = LocalBookcase.current
            LazyVerticalGrid(
                columns = if (phone) GridCells.Fixed(3) else GridCells.Adaptive(132.dp),
                contentPadding = if (bookcase) PaddingValues(top = 8.dp, bottom = 24.dp)
                else PaddingValues(start = if (phone) 8.dp else 12.dp, end = if (phone) 8.dp else 12.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (bookcase || phone) 0.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (bookcase) Modifier.background(ShelfColors.back) else Modifier),
            ) {
                itemsIndexed(entry.books, key = { _, b -> b.contentId }) { index, book ->
                    BookGridItem(
                        book,
                        view.covers,
                        highlighted = false,
                        inLibrary = book.relativePath?.lowercase() in libraryPaths,
                        onClick = { onOpenBook(book.contentId) },
                        shelfIndex = index,
                        shelfCount = entry.books.size,
                        padding = cellPadding,
                    )
                }
            }
        }
    }
}
