package dev.kolib.app.ui

import dev.kolib.app.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.ui.components.EmptyState
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.ShelfGridBox
import dev.kolib.app.ui.components.phoneLayout
import dev.kolib.app.ui.theme.LocalBookcase
import dev.kolib.app.ui.theme.ShelfColors
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.outlined.AutoStories

/** Key used for the "not in any collection" entry. */
const val NO_COLLECTION_KEY = "\u0000none"

private data class CollectionEntry(val key: String, val name: String, val books: List<KoboBook>, val hidden: Boolean)

/**
 * The Kobo's collections. A collection's books open beside the list; a book tapped there opens beside them (the list
 * folding away to make room), so it's seen without leaving the collection. On a phone, the book opens on a page of
 * its own (see DeviceScreen).
 */
@Composable
fun CollectionsTab(
    view: KoboView,
    live: Boolean,
    browse: BrowseState,
    extras: KoboExtras,
    onAddToLibrary: (List<KoboBook>) -> Unit,
    onDeleteFromKobo: (List<KoboBook>) -> Unit = {},
    /** Shows the Kobo's books with this tag (in the Books tab). */
    onTag: (String) -> Unit = {},
    /** Searches the Kobo's books for this (in the Books tab). */
    onSearch: (String) -> Unit = {},
) {
    val snapshot = view.snapshot
    val entries = remember(snapshot) {
        snapshot.collections.map { c ->
            CollectionEntry(c.name, c.name, snapshot.booksIn(c), hidden = !c.isVisible)
        } + CollectionEntry(NO_COLLECTION_KEY, tr("Not in any collection"), snapshot.booksWithoutCollection(), hidden = false)
    }
    val selected = entries.firstOrNull { it.key == browse.collectionKey }
    val book = browse.collectionBookId?.let { snapshot.booksById[it] }?.takeIf { b -> selected?.books?.any { it.contentId == b.contentId } == true }
    val rows = remember(entries) { entries.map { CollectionRow(it.key, it.name, it.books.size, if (it.hidden) tr(" · hidden on the Kobo") else null, it.hidden) } }

    CollectionsLayout(
        hasCollection = selected != null,
        hasBook = book != null,
        onOpenFirst = { entries.firstOrNull()?.let { browse.collectionKey = it.key } },
        onCloseCollection = { browse.collectionKey = null; browse.collectionBookId = null },
        onCloseBook = { browse.collectionBookId = null },
        list = { modifier, panes ->
            CollectionList(
                rows,
                heading = if (snapshot.collections.isEmpty()) tr("This Kobo has no collections yet.") else tr("{0} on this Kobo", Fmt.plural(snapshot.collections.size, "collection")),
                selectedKey = selected?.key,
                onSelect = { key ->
                    // With the books and a book both in view, a tap on the open collection changes nothing.
                    if (key != browse.collectionKey || panes != CollectionPanes.THREE) browse.collectionBookId = null
                    browse.collectionKey = key
                },
                modifier = modifier,
                state = browse.collectionScroll.list,
                opens = panes == CollectionPanes.ONE,
            )
        },
        books = { onBack ->
            selected?.let { entry ->
                CollectionBooks(
                    view, live, entry, extras, book?.contentId, onBack, browse.collectionScroll.grid(entry.key),
                    onOpenBook = { browse.collectionBookId = it }, onAddToLibrary = onAddToLibrary,
                )
            }
        },
        detail = { onBack ->
            if (book != null && selected != null) {
                BookDetailPane(
                    book,
                    snapshot.collectionsByBook[book.contentId].orEmpty(),
                    view.covers,
                    inLibrary = extras.inLibrary(book),
                    onBack = onBack,
                    backLabel = selected.name,
                    // Another of the book's collections: that one opens, the book still beside it.
                    onOpenCollection = { name -> if (entries.any { it.key == name }) browse.collectionKey = name },
                    onAddToLibrary = if (live) ({ onAddToLibrary(listOf(book)) }) else null,
                    onDeleteFromKobo = if (live && book.isSideloaded) ({
                        browse.collectionBookId = null
                        onDeleteFromKobo(listOf(book))
                    }) else null,
                    tags = extras.tags(book),
                    onTag = onTag,
                    onSearch = onSearch,
                    stars = extras.stars(book),
                    pages = extras.pages(book),
                )
            }
        },
        nothingChosen = { EmptyState(Icons.Outlined.CollectionsBookmark, tr("Tap a collection"), tr("Its books appear here.")) },
        noBookChosen = { EmptyState(Icons.Outlined.AutoStories, tr("Tap a book"), tr("Its details appear here.")) },
    )
}

@Composable
private fun CollectionBooks(
    view: KoboView,
    live: Boolean,
    entry: CollectionEntry,
    extras: KoboExtras,
    selectedId: String?,
    onBack: (() -> Unit)?,
    gridState: LazyGridState,
    onOpenBook: (String) -> Unit,
    onAddToLibrary: (List<KoboBook>) -> Unit,
) {
    val notInLibrary = entry.books.filter { it.isSideloaded && !extras.inLibrary(it) }
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
        } else CollectionHeader(
            name = entry.name,
            count = entry.books.size,
            onBack = onBack,
            action = if (live && notInLibrary.isNotEmpty()) ({
                FilledTonalButton(onClick = { onAddToLibrary(notInLibrary) }) {
                    Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (notInLibrary.size == entry.books.size) tr("Add all to library") else tr("Add {0} to library", notInLibrary.size))
                }
            }) else null,
        )
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
                state = gridState,
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
                        highlighted = book.contentId == selectedId,
                        inLibrary = extras.inLibrary(book),
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

/**
 * A collection's name and how many books it has, over its books; [action] at the end (adding them to the library,
 * sending them), or under the name where there isn't the room beside it (between the collections and a book's details).
 */
@Composable
fun CollectionHeader(name: String, count: Int, onBack: (() -> Unit)?, action: (@Composable () -> Unit)?) {
    BoxWithConstraints {
        val under = action != null && maxWidth < 600.dp
        Column(Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        Fmt.plural(count, "book") + tr(" · tap a book to see its details"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!under) action?.invoke()
            }
            if (under) Box(Modifier.padding(start = 8.dp, top = 8.dp)) { action() }
        }
    }
}
