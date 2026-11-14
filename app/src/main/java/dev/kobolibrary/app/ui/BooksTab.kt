@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package dev.kobolibrary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.kobo.BookKind
import dev.kobolibrary.app.kobo.CoverSize
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboCoverLoader
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.kobo.KoboSnapshot
import dev.kobolibrary.app.ui.library.SearchHelpDialog
import dev.kobolibrary.app.ui.library.TagPickerDialog
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.foundation.layout.heightIn
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.ui.components.BookCover
import dev.kobolibrary.app.ui.components.EmptyState
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.components.InfoRow
import dev.kobolibrary.app.ui.components.SectionCard
import dev.kobolibrary.app.ui.theme.LocalBookcase
import dev.kobolibrary.app.ui.theme.ShelfColors
import dev.kobolibrary.app.ui.components.ShelfSpace
import dev.kobolibrary.app.ui.components.shelfUnderCover
import androidx.compose.material3.ButtonDefaults
import dev.kobolibrary.app.ui.components.ShelfGridBox
import dev.kobolibrary.app.ui.components.gridCover
import dev.kobolibrary.app.ui.components.phoneLayout

/** Width at which a list and its details sit side by side. */
val TwoPaneWidth = 900.dp

@Composable
fun BooksTab(
    view: KoboView,
    live: Boolean,
    browse: BrowseState,
    extras: KoboExtras,
    onAddToLibrary: (List<KoboBook>) -> Unit,
    onDeleteFromKobo: (List<KoboBook>) -> Unit,
    /** While tags are being read from the books on the Kobo: done and total. */
    readingTags: Pair<Int, Int>? = null,
) {
    val snapshot = view.snapshot
    val visible = remember(snapshot, extras, browse.query, browse.source, browse.status, browse.sort, browse.descending, browse.collection, browse.tags, browse.tagsMatchAll) {
        BookSorting.filter(
            snapshot.books, snapshot.collectionsByBook, browse.query, browse.source, browse.status, browse.sort, browse.descending,
            browse.collection, browse.tags, browse.tagsMatchAll, extras,
        )
    }
    val selected = browse.selectedBookId?.let { snapshot.booksById[it] }
    // On a phone a book opens on a page of its own, over the Kobo's bar and tabs (see DeviceScreen).
    if (phoneLayout()) {
        PhoneKoboBrowser(view, live, browse, visible, extras, onAddToLibrary, onDeleteFromKobo, readingTags)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPanes = maxWidth >= TwoPaneWidth
        val detail: @Composable (KoboBook, (() -> Unit)?) -> Unit = { book, onBack ->
            BookDetailPane(
                book,
                snapshot.collectionsByBook[book.contentId].orEmpty(),
                view.covers,
                inLibrary = extras.inLibrary(book),
                onBack = onBack,
                onOpenCollection = { name ->
                    browse.collection = name
                    if (!twoPanes) browse.selectedBookId = null
                },
                onAddToLibrary = if (live) ({ onAddToLibrary(listOf(book)) }) else null,
                onDeleteFromKobo = if (live && book.isSideloaded) ({
                    if (browse.selectedBookId == book.contentId) browse.selectedBookId = null
                    onDeleteFromKobo(listOf(book))
                }) else null,
                tags = extras.tags(book),
                onTag = { tag ->
                    browse.tags = setOf(tag)
                    if (!twoPanes) browse.selectedBookId = null
                },
                stars = extras.stars(book),
                pages = extras.pages(book),
            )
        }
        if (twoPanes) {
            Row(Modifier.fillMaxSize()) {
                BookBrowser(view, live, browse, visible, selected?.contentId, extras, onAddToLibrary, onDeleteFromKobo, Modifier.weight(1.45f), readingTags)
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) detail(selected, null)
                    else EmptyState(Icons.Outlined.AutoStories, "Tap a book", "Its details from the Kobo will show here.")
                }
            }
        } else if (selected != null && !browse.selecting) {
            BackHandler { browse.selectedBookId = null }
            detail(selected) { browse.selectedBookId = null }
        } else {
            BookBrowser(view, live, browse, visible, null, extras, onAddToLibrary, onDeleteFromKobo, Modifier.fillMaxSize(), readingTags)
        }
    }
}

@Composable
private fun BookBrowser(
    view: KoboView,
    live: Boolean,
    browse: BrowseState,
    books: List<KoboBook>,
    selectedId: String?,
    extras: KoboExtras,
    onAddToLibrary: (List<KoboBook>) -> Unit,
    onDeleteFromKobo: (List<KoboBook>) -> Unit,
    modifier: Modifier,
    readingTags: Pair<Int, Int>?,
) {
    val inLibrary: (KoboBook) -> Boolean = { extras.inLibrary(it) }
    if (browse.selecting) BackHandler { browse.stopSelecting() }
    var confirmDelete by remember { mutableStateOf<List<KoboBook>?>(null) }
    Column(modifier.fillMaxHeight()) {
        if (browse.selecting) {
            val chosen = { books.filter { browse.picked.containsKey(it.contentId) } }
            SelectionBar(
                count = browse.picked.size,
                onSelectAll = { books.filter { it.isSideloaded && !inLibrary(it) }.forEach { browse.picked[it.contentId] = true } },
                onCancel = { browse.stopSelecting() },
                hint = if (!live) "Connect your Kobo to add or delete books" else null,
            ) {
                OutlinedButton(
                    onClick = { confirmDelete = chosen().filter { it.isSideloaded } },
                    enabled = live && browse.picked.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete from Kobo")
                }
                Button(
                    onClick = {
                        onAddToLibrary(chosen())
                        browse.stopSelecting()
                    },
                    enabled = live && browse.picked.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to library")
                }
            }
        }
        BrowserControls(browse, books.size, view.snapshot, extras, readingTags)
        if (books.isEmpty()) {
            if (view.snapshot.books.isEmpty()) {
                EmptyState(Icons.Outlined.AutoStories, "No books on this Kobo", "Books you add to the Kobo will appear here.")
            } else {
                EmptyState(Icons.Outlined.SearchOff, "No matching books", "Try a different search or clear the filters.")
            }
            return@Column
        }
        val onTap: (KoboBook) -> Unit = { book ->
            if (browse.selecting) browse.togglePicked(book.contentId) else browse.selectedBookId = book.contentId
        }
        val onLongPress: (KoboBook) -> Unit = { book ->
            if (!browse.selecting) browse.selecting = true
            browse.togglePicked(book.contentId)
        }
        if (browse.grid) ShelfGridBox(minCell = 132.dp, coverPadding = 8.dp) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                state = browse.gridState,
                // In the Bookcase look the covers stand on shelves that run edge to edge.
                contentPadding = if (LocalBookcase.current) PaddingValues(top = 8.dp, bottom = 24.dp)
                else PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (LocalBookcase.current) 0.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (LocalBookcase.current) Modifier.background(ShelfColors.back) else Modifier),
            ) {
                itemsIndexed(books, key = { _, b -> b.contentId }) { index, book ->
                    BookGridItem(
                        book,
                        view.covers,
                        highlighted = book.contentId == selectedId && !browse.selecting,
                        picked = if (browse.selecting) browse.picked.containsKey(book.contentId) else null,
                        inLibrary = inLibrary(book),
                        onClick = { onTap(book) },
                        onLongClick = { onLongPress(book) },
                        shelfIndex = index,
                        shelfCount = books.size,
                        detail = koboSortDetail(book, browse.sort, extras),
                    )
                }
            }
        } else {
            LazyColumn(state = browse.listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                items(books, key = { it.contentId }) { book ->
                    BookListRow(
                        book,
                        view.covers,
                        view.snapshot.collectionsByBook[book.contentId]?.size ?: 0,
                        detail = koboSortDetail(book, browse.sort, extras),
                        highlighted = book.contentId == selectedId && !browse.selecting,
                        picked = if (browse.selecting) browse.picked.containsKey(book.contentId) else null,
                        inLibrary = inLibrary(book),
                        onClick = { onTap(book) },
                        onLongClick = { onLongPress(book) },
                    )
                }
            }
        }
    }
    confirmDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (doomed.size == 1) "Delete “${doomed[0].title}” from the Kobo?" else "Delete ${doomed.size} books from the Kobo?") },
            text = {
                Text(
                    "This deletes their files from the Kobo, with their reading progress and highlights. " +
                        "Copies in your library on this device won't be deleted. Books from the Kobo Store can't be deleted here." +
                        if (doomed.isEmpty()) "\n\nNone of the selected books can be deleted, because they're from the Kobo Store." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFromKobo(doomed)
                        confirmDelete = null
                        browse.stopSelecting()
                    },
                    enabled = doomed.isNotEmpty(),
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/** The bar shown while choosing books: how many are chosen, and what can be done with them. */
@Composable
fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    hint: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel selection") }
            Column(Modifier.padding(end = 8.dp)) {
                Text(
                    if (count == 0) "Tap books to select them" else "${Fmt.plural(count, "book")} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSelectAll) { Text("Select all") }
            Spacer(Modifier.width(8.dp))
            // The buttons wrap onto another line when there are more than fit.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/** A line under each book that shows what it's sorted by, when that isn't obvious. */
internal fun koboSortDetail(book: KoboBook, sort: KoboSort, extras: KoboExtras): String? = when (sort) {
    KoboSort.PUBLISHED -> BookSorting.year(book)?.toString()
    KoboSort.ADDED -> book.addedMillis?.let { Fmt.date(it) }
    KoboSort.SIZE -> book.fileSize?.let { size -> if (size < 1_048_576) "${(size + 1023) / 1024} kB" else "%.1f MB".format(size / 1_048_576.0) }
    KoboSort.PAGES -> extras.pages(book)?.let { if (it == 1) "1 page" else "%,d pages".format(it) }
    KoboSort.RATING -> extras.stars(book).takeIf { it > 0 }?.let { stars -> "★".repeat(stars.toInt()) + if (stars % 1.0 >= 0.5) "½" else "" }
    KoboSort.LAST_READ -> book.lastReadMillis?.let { Fmt.date(it) }
    KoboSort.PROGRESS -> when (book.readStatus) {
        ReadStatus.FINISHED -> "Finished"
        ReadStatus.READING -> "${book.percentRead}% read"
        ReadStatus.UNREAD -> null
    }
    else -> null
}

@Composable
private fun BrowserControls(browse: BrowseState, shown: Int, snapshot: KoboSnapshot, extras: KoboExtras, readingTags: Pair<Int, Int>?) {
    val total = snapshot.books.size
    val collections = remember(snapshot) {
        snapshot.collections.filter { c -> c.memberIds.any { id -> snapshot.booksById[id]?.isOnDevice == true } }
            .map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val tagCounts = remember(snapshot, extras) {
        snapshot.books.flatMap { b -> extras.tags(b).distinctBy { it.lowercase() } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).map { it.key to it.value }
    }
    var showHelp by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = browse.query,
                onValueChange = { browse.query = it },
                placeholder = { Text("Search titles, authors, tags…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    Row {
                        if (browse.query.isNotEmpty()) IconButton(onClick = { browse.query = "" }) { Icon(Icons.Filled.Clear, "Clear search") }
                        IconButton(onClick = { showHelp = true }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, "How to search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            if (!browse.selecting) {
                TextButton(onClick = { browse.selecting = true }) {
                    Icon(Icons.Outlined.Checklist, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select")
                }
            }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    KoboSort.entries.forEach { order ->
                        val current = order == browse.sort
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(order.label)
                                    if (current) {
                                        Text(
                                            if (browse.descending) order.descending else order.ascending,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            leadingIcon = { if (current) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                            trailingIcon = {
                                if (current) Icon(if (browse.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, "Reverse order")
                            },
                            onClick = { browse.sortBy(order); sortOpen = false },
                        )
                    }
                }
            }
            IconButton(onClick = { browse.grid = !browse.grid }) {
                if (browse.grid) Icon(Icons.AutoMirrored.Filled.ViewList, "Show as list")
                else Icon(Icons.Filled.GridView, "Show as grid")
            }
        }
        // Wraps onto a second line rather than hiding filters off the edge.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SourceFilter.entries.forEach { f ->
                FilterChip(selected = browse.source == f, onClick = { browse.source = f }, label = { Text(f.label) })
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            StatusFilter.entries.forEach { f ->
                FilterChip(
                    selected = browse.status == f,
                    onClick = { browse.status = if (browse.status == f) null else f },
                    label = { Text(f.label) },
                )
            }
            if (collections.isNotEmpty() || tagCounts.isNotEmpty()) VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            if (collections.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = browse.collection != null,
                        onClick = { open = true },
                        label = { Text(browse.collection ?: "Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                        DropdownMenuItem(text = { Text("All collections") }, onClick = { browse.collection = null; open = false })
                        HorizontalDivider()
                        collections.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                leadingIcon = { if (c == browse.collection) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                                onClick = { browse.collection = c; open = false },
                            )
                        }
                    }
                }
            }
            if (tagCounts.isNotEmpty()) {
                FilterChip(
                    selected = browse.tags.isNotEmpty(),
                    onClick = { showTags = true },
                    label = {
                        Text(
                            when (browse.tags.size) {
                                0 -> "Tags"
                                1 -> browse.tags.first()
                                else -> "${browse.tags.size} tags (${if (browse.tagsMatchAll) "all" else "any"})"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                (if (shown == total) Fmt.plural(total, "book") else "$shown of ${Fmt.plural(total, "book")}") + " · sorted by ${browse.sortDescription}" +
                    (readingTags?.let { (done, all) -> " · reading tags from book files: $done of $all" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
            )
            if (browse.narrowed) TextButton(onClick = { browse.clearFilters() }) { Text("Clear search and filters") }
        }
    }
    if (showHelp) SearchHelpDialog(onPick = { browse.query = it; showHelp = false }, onDismiss = { showHelp = false }, forKobo = true)
    if (showTags) {
        TagPickerDialog(
            tags = tagCounts,
            chosen = browse.tags,
            matchAll = browse.tagsMatchAll,
            onApply = { chosen, all ->
                browse.tags = chosen
                browse.tagsMatchAll = all
                showTags = false
            },
            onDismiss = { showTags = false },
        )
    }
}

@Composable
fun BookGridItem(
    book: KoboBook,
    covers: KoboCoverLoader?,
    highlighted: Boolean,
    picked: Boolean? = null,
    inLibrary: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /** Where it sits in the grid, so the last book can carry the Bookcase shelf to the edge. */
    shelfIndex: Int = 0,
    shelfCount: Int = 0,
    /** What the list is sorted by, when that isn't obvious from the title and author. */
    detail: String? = null,
    /** Space around the cover, inside the cell. */
    padding: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Column(
        Modifier
            .shelfUnderCover(coverTop = padding, index = shelfIndex, count = shelfCount)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(padding)
    ) {
        BookCover(book, covers, CoverSize.THUMBNAIL, Modifier.gridCover(), selected = picked, inLibrary = inLibrary)
        ShelfSpace()
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        book.author?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        (detail ?: book.seriesLabel)?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BookListRow(
    book: KoboBook,
    covers: KoboCoverLoader?,
    collectionCount: Int,
    detail: String?,
    highlighted: Boolean,
    picked: Boolean?,
    inLibrary: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(book.author, book.seriesLabel).joinToString(" · ").ifEmpty { book.format },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (picked != null) Checkbox(checked = picked, onCheckedChange = { onClick() })
                BookCover(book, covers, CoverSize.THUMBNAIL, Modifier.width(44.dp), showProgress = false)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(statusText(book), style = MaterialTheme.typography.labelMedium)
                detail?.takeIf { it != statusText(book) && it != "Finished" }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (inLibrary) {
                    Text("In library", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (collectionCount > 0) {
                    Text(
                        Fmt.plural(collectionCount, "collection"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                picked == true || highlighted -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            },
        ),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

private fun statusText(book: KoboBook): String = when (book.readStatus) {
    ReadStatus.UNREAD -> "Unread"
    ReadStatus.READING -> "${book.percentRead}% read"
    ReadStatus.FINISHED -> "Finished"
}

/** "en" -> "English", "fr-CA" -> "French (Canada)"; anything unrecognised is shown as stored. */
fun languageName(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code.replace('_', '-'))
    val name = locale.getDisplayName(java.util.Locale.getDefault())
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name
}

/** Book descriptions are HTML; show their bold and italic, nothing riskier. */
fun descriptionText(html: String): AnnotatedString =
    runCatching { AnnotatedString.fromHtml(html.trim()) }.getOrElse { AnnotatedString(html) }

@Composable
fun BookDetailPane(
    book: KoboBook,
    collections: List<String>,
    covers: KoboCoverLoader?,
    inLibrary: Boolean,
    onBack: (() -> Unit)?,
    onOpenCollection: (String) -> Unit,
    onAddToLibrary: (() -> Unit)?,
    onDeleteFromKobo: (() -> Unit)? = null,
    tags: List<String> = emptyList(),
    onTag: (String) -> Unit = {},
    /** Stars from the Kobo, or the book's own rating; 0 when it has none. */
    stars: Double = 0.0,
    pages: Int? = null,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDeleteFromKobo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Delete “${book.title}” from the Kobo?") },
            text = {
                Text(
                    "This deletes the book's file from the Kobo, with your reading progress and highlights. " +
                        if (inLibrary) "The copy in your library won't be deleted." else "It isn't in your library, so add it to your library first if you want to keep it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteFromKobo()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("All books")
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BookCover(book, covers, CoverSize.FULL, Modifier.width(if (onBack != null) 200.dp else 160.dp), showProgress = false)
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
                book.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                book.author?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                book.seriesLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                }
                if (stars > 0) {
                    Row(Modifier.padding(top = 6.dp)) {
                        repeat(5) { i ->
                            Icon(
                                if (stars >= i + 1 || stars < i + 0.5) Icons.Filled.Star else Icons.AutoMirrored.Filled.StarHalf,
                                null,
                                tint = if (stars >= i + 0.5) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(onClick = {}, label = { Text(book.format) })
                    SuggestionChip(onClick = {}, label = { Text(if (book.kind == BookKind.SIDELOADED) "Sideloaded" else "Kobo Store") })
                    SuggestionChip(onClick = {}, label = { Text(book.readStatus.label) })
                }
                if (book.readStatus == ReadStatus.READING) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { book.percentRead / 100f }, modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth())
                    Text(
                        "${book.percentRead}% read",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                when {
                    inLibrary -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("In your library", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    book.isSideloaded && onAddToLibrary != null -> FilledTonalButton(onClick = onAddToLibrary) {
                        Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to library")
                    }
                }
                if (onDeleteFromKobo != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete from Kobo")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Collections on the Kobo", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        if (collections.isEmpty()) {
            Text("Not in any collection.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                collections.forEach { name ->
                    AssistChip(
                        onClick = { onOpenCollection(name) },
                        label = { Text(name) },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Tags", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    SuggestionChip(onClick = { onTag(t) }, label = { Text(t) }, icon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(16.dp)) })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionCard("Details") {
            InfoRow("Last read", Fmt.dateTime(book.lastReadMillis))
            InfoRow("Added", Fmt.date(book.addedMillis))
            InfoRow("Published", BookSorting.year(book)?.toString())
            InfoRow("Publisher", book.publisher)
            InfoRow("Language", book.language?.let { languageName(it) })
            InfoRow("Length", pages?.let { p -> "About ${Fmt.plural(p, "page")}" })
            InfoRow("ISBN", book.isbn, selectable = true)
            InfoRow("File", book.relativePath, selectable = true)
            InfoRow("Size", Fmt.bytes(context, book.fileSize))
        }

        book.description?.let { html ->
            val text = remember(html) { descriptionText(html) }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                SectionCard("Description") {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            "These details come from the Kobo. To change the details or cover, add the book to your library and edit it there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
    }
}
