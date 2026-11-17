package dev.kobolibrary.app.ui.library

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.CollectionHeader
import dev.kobolibrary.app.ui.CollectionList
import dev.kobolibrary.app.ui.CollectionRow
import dev.kobolibrary.app.ui.CollectionsLayout
import dev.kobolibrary.app.ui.NO_COLLECTION_KEY
import dev.kobolibrary.app.ui.components.EmptyState
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.reader.ReadHere
import dev.kobolibrary.app.ui.components.ShelfGridBox
import dev.kobolibrary.app.ui.components.phoneLayout
import dev.kobolibrary.app.ui.theme.LocalBookcase
import dev.kobolibrary.app.ui.theme.ShelfColors

/** A collection in the library: [key] is its name in lower case (a book's collections are matched whatever their case). */
internal class LibraryCollection(val key: String, val name: String, val books: List<LibraryBook>)

/** The library's collections, A to Z, then the books in none. */
internal fun libraryCollections(books: List<LibraryBook>): List<LibraryCollection> {
    val names = LinkedHashMap<String, String>()
    val members = HashMap<String, MutableList<LibraryBook>>()
    for (b in books) for (c in b.collections) {
        val name = c.trim()
        if (name.isEmpty()) continue
        val key = name.lowercase()
        names.putIfAbsent(key, name)
        val list = members.getOrPut(key) { ArrayList() }
        if (list.none { it.id == b.id }) list += b
    }
    return names.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value })
        .map { (key, name) -> LibraryCollection(key, name, members[key].orEmpty()) } +
        LibraryCollection(NO_COLLECTION_KEY, tr("Not in any collection"), books.filter { b -> b.collections.none { it.isNotBlank() } })
}

/** How many collections the library has (not counting the books in none). */
internal fun libraryCollectionCount(books: List<LibraryBook>): Int =
    books.flatMap { b -> b.collections.map { it.trim().lowercase() } }.filter { it.isNotEmpty() }.distinct().size

/**
 * The library's collections, laid out as the Kobo's are: a collection's books open beside the list, and a book tapped
 * there opens beside them, the list folding away to make room. [detail] is the book's details, with [onBack] for a
 * narrow screen. On a phone a book opens on a page of its own (see PhoneLibraryScreen).
 */
@Composable
internal fun LibraryCollectionsTab(
    books: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    koboConnected: Boolean,
    reading: (LibraryBook) -> KoboBook?,
    here: (LibraryBook) -> ReadHere?,
    onSend: (List<String>) -> Unit,
    detail: @Composable (book: LibraryBook, collection: String, onBack: (() -> Unit)?) -> Unit,
) {
    val collections = remember(books) { libraryCollections(books) }
    val chosen = collections.firstOrNull { it.key == browse.collectionKey }
    // The collection's books in the order the library is sorted in.
    val shown = remember(chosen, browse.sort, browse.descending, reading, here) {
        chosen?.let { LibrarySorting.filter(it.books, "", LibraryFilter.ALL, null, browse.sort, browse.descending, reading = reading, here = here) }.orEmpty()
    }
    val book = browse.collectionBookId?.let { id -> shown.firstOrNull { it.id == id } }
    val count = collections.size - 1
    val rows = remember(collections) {
        collections.map { c ->
            val onKobo = c.books.count { it.koboPath != null }
            CollectionRow(c.key, c.name, c.books.size, if (onKobo > 0) tr(" · {0} on the Kobo", onKobo) else null)
        }
    }
    CollectionsLayout(
        hasCollection = chosen != null,
        hasBook = book != null,
        onCloseCollection = { browse.collectionKey = null; browse.collectionBookId = null },
        onCloseBook = { browse.collectionBookId = null },
        list = { modifier ->
            CollectionList(
                rows,
                heading = if (count == 0) tr("No collections yet. To add a book to one, tap Edit details.") else tr("{0} in your library", Fmt.plural(count, "collection")),
                selectedKey = chosen?.key,
                onSelect = { key -> browse.collectionKey = key; browse.collectionBookId = null },
                modifier = modifier,
            )
        },
        books = { onBack ->
            if (chosen != null) CollectionShelf(chosen, shown, covers, browse, koboConnected, reading, here, book?.id, onBack, onSend)
        },
        detail = { onBack -> if (book != null && chosen != null) detail(book, chosen.name, onBack) },
        nothingChosen = { EmptyState(Icons.Outlined.CollectionsBookmark, tr("Tap a collection"), tr("Its books appear here.")) },
    )
}

@Composable
private fun CollectionShelf(
    collection: LibraryCollection,
    books: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    koboConnected: Boolean,
    reading: (LibraryBook) -> KoboBook?,
    here: (LibraryBook) -> ReadHere?,
    selectedId: String?,
    onBack: (() -> Unit)?,
    onSend: (List<String>) -> Unit,
) {
    // Books of the collection that aren't on the Kobo yet, to send in one go (as the Kobo's collections add theirs to the library).
    val notOnKobo = books.filter { it.koboPath == null }
    val phone = phoneLayout()
    val sendLabel = if (notOnKobo.size == books.size) tr("Send all to Kobo") else tr("Send {0} to Kobo", notOnKobo.size)
    Column(Modifier.fillMaxSize()) {
        if (phone) Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back to the collections")) }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(collection.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.plural(books.size, "book"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (koboConnected && notOnKobo.isNotEmpty()) {
                FilledTonalButton(onClick = { onSend(notOnKobo.map { it.id }) }, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(sendLabel)
                }
            }
        } else CollectionHeader(
            name = collection.name,
            count = books.size,
            onBack = onBack,
            action = if (koboConnected && notOnKobo.isNotEmpty()) ({
                FilledTonalButton(onClick = { onSend(notOnKobo.map { it.id }) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(sendLabel)
                }
            }) else null,
        )
        if (books.isEmpty()) {
            EmptyState(
                Icons.Outlined.CollectionsBookmark,
                tr("Empty collection"),
                if (collection.key == NO_COLLECTION_KEY) tr("Every book in your library is in at least one collection.") else tr("No books are in this collection."),
            )
            return@Column
        }
        val cellPadding = if (phone) 6.dp else 8.dp
        ShelfGridBox(minCell = if (phone) 100.dp else 132.dp, coverPadding = cellPadding, columns = if (phone) 3 else null) {
            val bookcase = LocalBookcase.current
            LazyVerticalGrid(
                columns = if (phone) GridCells.Fixed(3) else GridCells.Adaptive(132.dp),
                contentPadding = if (bookcase) PaddingValues(top = 8.dp, bottom = if (phone) 104.dp else 24.dp)
                else PaddingValues(start = if (phone) 8.dp else 12.dp, end = if (phone) 8.dp else 12.dp, top = 8.dp, bottom = if (phone) 104.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (bookcase || phone) 0.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (bookcase) Modifier.background(ShelfColors.back) else Modifier),
            ) {
                itemsIndexed(books, key = { _, b -> b.id }) { index, book ->
                    LibraryGridItem(
                        book, covers, index, books.size,
                        picked = null,
                        highlighted = book.id == selectedId,
                        sort = browse.sort, reading = reading(book), here = here(book),
                        onTap = { browse.collectionBookId = book.id },
                        onHold = { browse.collectionBookId = book.id },
                        padding = cellPadding,
                    )
                }
            }
        }
    }
}
