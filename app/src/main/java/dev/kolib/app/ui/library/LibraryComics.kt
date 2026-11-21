package dev.kolib.app.ui.library

import dev.kolib.app.ui.components.UnderCover
import dev.kolib.app.ui.components.shelfMinCell
import dev.kolib.app.ui.components.shelfCells
import dev.kolib.app.ui.components.chosenPerRow
import dev.kolib.app.ui.components.BarAction
import androidx.compose.foundation.layout.RowScope
import dev.kolib.app.ui.components.SelectAllButtons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kolib.app.library.ComicEntry
import dev.kolib.app.library.ComicSeries
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.tr
import dev.kolib.app.ui.SelectionBar
import dev.kolib.app.ui.TwoPaneWidth
import dev.kolib.app.ui.components.AlertDialog
import dev.kolib.app.ui.components.Button
import dev.kolib.app.ui.components.ComicSeriesCover
import dev.kolib.app.ui.components.EmptyState
import dev.kolib.app.ui.components.HideBottomBar
import dev.kolib.app.ui.components.LibraryCover
import dev.kolib.app.ui.components.OutlinedButton
import dev.kolib.app.ui.components.PhoneAction
import dev.kolib.app.ui.components.PhoneActionBar
import dev.kolib.app.ui.components.ShelfGridBox
import dev.kolib.app.ui.components.ShelfSpace
import dev.kolib.app.ui.components.TabBackHandler
import dev.kolib.app.ui.components.AtFoot
import dev.kolib.app.ui.components.TopInsets
import dev.kolib.app.ui.components.gridCover
import dev.kolib.app.ui.components.phoneLayout
import dev.kolib.app.ui.components.shelfUnderCover
import dev.kolib.app.ui.descriptionText
import dev.kolib.app.ui.theme.LocalBookcase
import dev.kolib.app.ui.theme.ShelfColors

/** What can be done with comics from the Comics tab, by their ids. */
internal class ComicActions(
    val onRead: (String) -> Unit = {},
    val onSend: (List<String>) -> Unit = {},
    val onDelete: (List<String>) -> Unit = {},
    val onMarkFinished: (List<String>) -> Unit = {},
    val onMarkUnread: (List<String>) -> Unit = {},
    /** Puts the comics in one series, of the name given. */
    val onGroup: (List<String>, String) -> Unit = { _, _ -> },
    /** Takes the comics out of their series: each is on its own again. */
    val onTakeOut: (List<String>) -> Unit = {},
)

/** The library's comics: kept in the Comics tab, not with the books. */
internal fun libraryComics(books: List<LibraryBook>): List<LibraryBook> = books.filter { it.isComic }

/** The Comics tab's entries, searched and in the order chosen. */
internal fun shownComicSeries(entries: List<ComicEntry>, query: String, sort: ComicSort, here: (LibraryBook) -> ReadHere?): List<ComicEntry> {
    val q = query.trim()
    val found = if (q.isEmpty()) entries else entries.filter { e ->
        e.title.contains(q, ignoreCase = true) || e.authors.any { it.contains(q, ignoreCase = true) } || e.issues.any { it.title.contains(q, ignoreCase = true) }
    }
    // Stable, so series never read (or added at the same time) stay A to Z.
    return when (sort) {
        ComicSort.TITLE -> found
        ComicSort.READ -> found.sortedByDescending { it.lastRead(here) ?: 0L }
        ComicSort.ADDED -> found.sortedByDescending { e -> e.issues.maxOf { it.addedAt } }
    }
}

private fun comicsLabel(n: Int) = if (n == 1) tr("1 comic") else tr("{0} comics", n)

/**
 * The library's comics as Mihon shows them: each series one entry, its cover showing how many of its comics haven't
 * been read. A series tapped opens beside the shelf (or in its place, on a narrow screen), its comics in order. [detail]
 * is a comic's details, in its series' place. Phones open a series on a page of its own (see PhoneLibraryScreen).
 */
@Composable
internal fun LibraryComicsTab(
    comics: List<LibraryBook>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    koboConnected: Boolean,
    here: (LibraryBook) -> ReadHere?,
    actions: ComicActions,
    detail: @Composable (book: LibraryBook, series: String, onBack: () -> Unit) -> Unit,
) {
    val entries = remember(comics) { ComicSeries.entries(comics) }
    val open = browse.comicKey?.let { k -> entries.firstOrNull { it.key == k } }
    // A series that's gone (its comics removed, or put in another) is closed.
    LaunchedEffect(open == null, browse.comicKey) {
        if (open == null && browse.comicKey != null) {
            browse.comicKey = null
            browse.comicBookId = null
            if (browse.comicSelecting == ComicSelecting.ISSUES) browse.stopComicSelecting()
        }
    }
    val book = browse.comicBookId?.let { id -> open?.issues?.firstOrNull { it.id == id } }
    val phone = phoneLayout()
    val closeSeries = { browse.comicKey = null; browse.comicBookId = null; browse.stopComicSelecting() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = !phone && maxWidth >= TwoPaneWidth
        // Beside the shelf, or (chosen, or on a narrower screen) in its place, with no side panel.
        val beside = wide && !browse.seriesInPlace
        when {
            beside -> {
                // Back from a comic's details is its series again; from a series, the shelf on its own.
                if (open != null && book != null) TabBackHandler { browse.comicBookId = null }
                else if (open != null && browse.comicSelecting == null) TabBackHandler { closeSeries() }
                Row(Modifier.fillMaxSize()) {
                    ComicShelf(entries, covers, browse, here, open?.key, actions, Modifier.weight(1.45f), wide = true)
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            open != null && book != null -> detail(book, open.title) { browse.comicBookId = null }
                            open != null -> ComicSeriesPage(open, covers, browse, koboConnected, here, actions, onBack = null, others = entries)
                            else -> EmptyState(Icons.Outlined.AutoStories, tr("Tap a series"), tr("Its comics show here, in order. Hold a series to select several."))
                        }
                    }
                }
            }
            phone || open == null -> ComicShelf(entries, covers, browse, here, null, actions, Modifier.fillMaxSize(), wide = wide)
            book != null -> {
                // A comic on its own was opened from the shelf: back is the shelf again.
                val back = { if (open.isSeries) browse.comicBookId = null else closeSeries() }
                TabBackHandler { back() }
                detail(book, open.title) { back() }
            }
            else -> {
                TabBackHandler { closeSeries() }
                ComicSeriesPage(
                    open, covers, browse, koboConnected, here, actions, onBack = closeSeries,
                    mode = browse.seriesMode, onMode = { browse.seriesMode = it }, others = entries, infoAtTop = true,
                )
            }
        }
    }
}

/** The shelf of series: searched and sorted at the top (on a phone, the search is the library's own), series chosen by holding one. */
@Composable
private fun ComicShelf(
    entries: List<ComicEntry>,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    here: (LibraryBook) -> ReadHere?,
    highlighted: String?,
    actions: ComicActions,
    modifier: Modifier,
    /** Wide enough for a series to open beside the shelf: whether it does is a choice in the menu. */
    wide: Boolean = false,
) {
    val phone = phoneLayout()
    val shown = remember(entries, browse.comicQuery, browse.comicSort, here) { shownComicSeries(entries, browse.comicQuery, browse.comicSort, here) }
    val choosing = browse.comicSelecting == ComicSelecting.SERIES
    var confirmRemove by remember { mutableStateOf<List<String>?>(null) }
    var naming by remember { mutableStateOf<List<String>?>(null) }
    // The comics chosen, to put in a series there is (or a new one).
    var adding by remember { mutableStateOf<List<String>?>(null) }
    // Series chosen to remove: their comics stay, each on its own, unless they're to be removed too.
    var removingSeries by remember { mutableStateOf<List<ComicEntry>?>(null) }
    val remove = {
        val picked = chosenEntriesOf(shown, browse)
        if (picked.any { it.isSeries }) removingSeries = picked else confirmRemove = picked.flatMap { e -> e.issues.map { it.id } }
    }
    if (choosing) {
        TabBackHandler { browse.stopComicSelecting() }
        if (phone) HideBottomBar()
    }
    val chosenEntries = { shown.filter { browse.comicPicked.containsKey(it.key) } }
    // A series opens in place of the shelf (chosen so, or there's no room beside it): a comic on its own is opened to
    // read when it's tapped, and what's held has its details (or its series' page) from Info in the bar for it.
    val inPlace = !phone && (!wide || browse.seriesInPlace)
    val showInfo = { e: ComicEntry ->
        browse.stopComicSelecting()
        browse.comicKey = e.key
        browse.comicBookId = if (e.isSeries) null else e.issues.first().id
    }
    val chosen = { chosenEntries().flatMap { e -> e.issues.map { it.id } } }
    val done = { browse.stopComicSelecting() }
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            val chosenCount = browse.comicPicked.size
            val title = if (chosenCount == 0) tr("Tap series to select them") else if (chosenCount == 1) tr("1 series selected") else tr("{0} series selected", chosenCount)
            // On a short screen, the search slides away as the shelf is scrolled, once the bar and tabs have.
            dev.kolib.app.ui.components.HidingTop(top = {
            if (choosing && phone) {
                ComicChoosingHeader(title, onSelectAll = { shown.forEach { browse.comicPicked[it.key] = true } }, onDeselectAll = { browse.comicPicked.clear() },
                    anySelected = browse.comicPicked.isNotEmpty(), onCancel = done)
            } else if (!phone) {
                ComicSearchRow(browse, shown.size, entries.sumOf { it.issues.size }, wide)
            } else {
                ComicSortRow(browse, shown.size, entries.sumOf { it.issues.size })
            }
            }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The shelf, and while series are being chosen, the bar for them along the foot of the screen, its whole width.
            Column(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    if (browse.comicQuery.isBlank()) EmptyState(Icons.Outlined.AutoStories, tr("No comics"), tr("Comics you add show here, one entry for each series."))
                    else EmptyState(Icons.Outlined.Search, tr("No comics match"), tr("Nothing in the Comics tab has “{0}” in its name.", browse.comicQuery.trim()))
                    return@Column
                }
                val cellPadding = if (phone) 6.dp else 8.dp
                val perRow = chosenPerRow() ?: if (phone) 3 else null
                val minCell = shelfMinCell()
                // The series are covers whichever way the books show: as a list, the comfortable grid.
                ShelfGridBox(minCell = minCell, coverPadding = cellPadding, columns = perRow, mode = browse.mode.takeIf { browse.grid } ?: dev.kolib.app.ui.components.ShelfMode.COMFORTABLE) {
                    val bookcase = LocalBookcase.current
                    LazyVerticalGrid(
                        columns = shelfCells(perRow, minCell),
                        state = browse.comicGrid,
                        contentPadding = if (bookcase) PaddingValues(top = 8.dp, bottom = if (phone) 104.dp else 24.dp)
                        else PaddingValues(start = if (phone) 8.dp else 12.dp, end = if (phone) 8.dp else 12.dp, top = 8.dp, bottom = if (phone) 104.dp else 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (bookcase || phone) 0.dp else 4.dp),
                        verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("comic-shelf")
                            .then(if (bookcase) Modifier.background((dev.kolib.app.ui.theme.LocalKoLibStyle.current.shelf ?: ShelfColors).back) else Modifier),
                    ) {
                        itemsIndexed(shown, key = { _, e -> e.key }) { index, entry ->
                            ComicSeriesItem(
                                entry, covers, here, index, shown.size,
                                picked = if (choosing) browse.comicPicked.containsKey(entry.key) else null,
                                highlighted = entry.key == highlighted,
                                padding = cellPadding,
                                onTap = {
                                    if (choosing) {
                                        if (browse.comicPicked.remove(entry.key) == null) browse.comicPicked[entry.key] = true
                                    } else if (inPlace && !entry.isSeries) {
                                        actions.onRead(entry.issues.first().id)
                                    } else {
                                        browse.comicKey = entry.key
                                        browse.comicBookId = null
                                        if (browse.comicSelecting == ComicSelecting.ISSUES) browse.stopComicSelecting()
                                    }
                                },
                                onHold = {
                                    if (!choosing) {
                                        browse.stopComicSelecting()
                                        browse.comicSelecting = ComicSelecting.SERIES
                                    }
                                    browse.comicPicked[entry.key] = true
                                },
                            )
                        }
                    }
                }
            }
            }
            if (choosing && !phone) AtFoot {
                SelectionBar(
                    count = 0, title = title, anySelected = chosenCount > 0,
                    onSelectAll = { shown.forEach { browse.comicPicked[it.key] = true } }, onDeselectAll = { browse.comicPicked.clear() }, onCancel = done,
                    moreTag = "comics-more",
                    actions = comicShelfActions(chosenCount, onSend = { actions.onSend(chosen()); done() }, onRead = { actions.onMarkFinished(chosen()); done() },
                        onUnread = { actions.onMarkUnread(chosen()); done() }, onAddTo = { adding = chosen() }, onRemove = remove,
                        onInfo = if (inPlace && chosenCount == 1) ({ chosenEntries().firstOrNull()?.let(showInfo) }) else null),
                )
            }
        }
        if (choosing && phone) {
            val count = browse.comicPicked.size
            PhoneActionBar(
                comicShelfActions(count, onSend = { actions.onSend(chosen()); done() }, onRead = { actions.onMarkFinished(chosen()); done() },
                    onUnread = { actions.onMarkUnread(chosen()); done() }, onAddTo = { adding = chosen() }, onRemove = remove),
                Modifier.align(Alignment.BottomCenter),
                moreTag = "comics-more",
            )
        }
    }
    removingSeries?.let { picked ->
        RemoveSeriesDialog(
            series = picked.filter { it.isSeries },
            alone = picked.count { !it.isSeries },
            onKeepComics = {
                actions.onTakeOut(picked.filter { it.isSeries }.flatMap { e -> e.issues.map { it.id } })
                removingSeries = null
                done()
            },
            onRemoveComics = {
                actions.onDelete(picked.flatMap { e -> e.issues.map { it.id } })
                removingSeries = null
                done()
            },
            onDismiss = { removingSeries = null },
        )
    }
    adding?.let { ids ->
        val picked = remember(ids) { chosenEntries().map { it.key }.toSet() }
        AddToSeriesDialog(
            series = entries.filter { it.isSeries && it.key !in picked },
            covers = covers,
            onPick = { e -> actions.onGroup(ids, e.title); adding = null; done() },
            onNew = { adding = null; naming = ids },
            onDismiss = { adding = null },
        )
    }
    naming?.let { ids ->
        val suggestion = remember(ids) { chosenEntries().firstOrNull { it.isSeries }?.title ?: chosenEntries().firstOrNull()?.let { ComicSeries.parse(ComicSeries.nameOf(it.cover)).stem }.orEmpty() }
        ComicSeriesNameDialog(
            title = tr("Put them in one series"),
            initial = suggestion,
            onDone = { name -> actions.onGroup(ids, name); naming = null; done() },
            onDismiss = { naming = null },
        )
    }
    confirmRemove?.let { ids ->
        RemoveBooksDialog(ids, onRemove = { actions.onDelete(ids); confirmRemove = null; done() }, onDismiss = { confirmRemove = null })
    }
}

/** The entries chosen on the shelf, of those [shown]. */
private fun chosenEntriesOf(shown: List<ComicEntry>, browse: LibraryBrowseState) = shown.filter { browse.comicPicked.containsKey(it.key) }

/**
 * Removing series: the series go and their comics stay in the library, each on its own; or the comics are removed
 * from the library too. [alone] comics chosen on their own besides stay, unless the comics are removed.
 */
@Composable
internal fun RemoveSeriesDialog(series: List<ComicEntry>, alone: Int = 0, onKeepComics: () -> Unit, onRemoveComics: () -> Unit, onDismiss: () -> Unit) {
    val comics = series.sumOf { it.issues.size } + alone
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (series.size == 1) tr("Remove the series “{0}”?", series[0].title) else tr("Remove {0} series?", series.size)) },
        text = {
            Text(
                if (series.size == 1) tr("Its comics stay in the library, each on its own, unless you remove them too.")
                else tr("Their comics stay in the library, each on its own, unless you remove them too."),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onKeepComics, modifier = Modifier.testTag("series-remove-keep")) { Text(tr("Remove the series")) }
                TextButton(onClick = onRemoveComics, modifier = Modifier.testTag("series-remove-all")) {
                    Text(
                        when {
                            comics == 1 -> tr("Remove the series and its comic")
                            series.size == 1 -> tr("Remove the series and its {0} comics", comics)
                            else -> tr("Remove the series and their {0} comics", comics)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
            }
        },
    )
}

/** What can be done with the series and comics chosen on the shelf: the same in the bar along the foot of a phone or a wider screen. */
private fun comicShelfActions(
    count: Int,
    onSend: () -> Unit,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onAddTo: () -> Unit,
    onRemove: () -> Unit,
    /** The one chosen's details (or its series' page); null with more than one chosen, or where there's no need. */
    onInfo: (() -> Unit)? = null,
): List<BarAction> {
    val any = count > 0
    return listOfNotNull(
        BarAction(Icons.AutoMirrored.Filled.Send, tr("To Kobo"), onSend, enabled = any, main = true, tag = "comics-send", longLabel = tr("Send to Kobo")),
        BarAction(Icons.Outlined.DoneAll, dev.kolib.app.trc("mark", "Read"), onRead, enabled = any, tag = "comics-mark-read", longLabel = tr("Mark as read")),
        BarAction(Icons.Outlined.RemoveDone, dev.kolib.app.trc("mark", "Unread"), onUnread, enabled = any, tag = "comics-mark-unread", longLabel = tr("Mark as unread")),
        BarAction(Icons.AutoMirrored.Outlined.PlaylistAdd, tr("Add to series"), onAddTo, enabled = any, tag = "comics-add-to-series", canWait = true),
        onInfo?.let { BarAction(Icons.Outlined.Info, tr("Info"), it, tag = "comics-info", canWait = true) },
        BarAction(Icons.Outlined.Delete, tr("Remove"), onRemove, enabled = any, destructive = true, tag = "comics-remove"),
    )
}

/** What can be done with the comics chosen in a series: the same in the bar along the foot of a phone or a wider screen. */
private fun comicIssueActions(
    count: Int,
    inSeries: Boolean,
    onSend: () -> Unit,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onTakeOut: () -> Unit,
    onRemove: () -> Unit,
    /** The one chosen's details; null with more than one chosen, or where there's no need. */
    onInfo: (() -> Unit)? = null,
): List<BarAction> {
    val any = count > 0
    return listOfNotNull(
        BarAction(Icons.AutoMirrored.Filled.Send, tr("To Kobo"), onSend, enabled = any, main = true, tag = "comic-issues-send", longLabel = tr("Send to Kobo")),
        BarAction(Icons.Outlined.DoneAll, dev.kolib.app.trc("mark", "Read"), onRead, enabled = any, tag = "comics-mark-read", longLabel = tr("Mark as read")),
        BarAction(Icons.Outlined.RemoveDone, dev.kolib.app.trc("mark", "Unread"), onUnread, enabled = any, tag = "comic-issues-mark-unread", longLabel = tr("Mark as unread")),
        if (inSeries) BarAction(Icons.Outlined.LayersClear, tr("Take out"), onTakeOut, enabled = any, tag = "comic-issues-take-out", canWait = true, longLabel = tr("Take out of the series")) else null,
        onInfo?.let { BarAction(Icons.Outlined.Info, tr("Info"), it, tag = "comic-issue-info", canWait = true) },
        BarAction(Icons.Outlined.Delete, tr("Remove"), onRemove, enabled = any, destructive = true, tag = "comic-issues-remove"),
    )
}

/** On a phone, the top of the screen while choosing: what's chosen, Select all and Deselect all, and a way out. */
@Composable
private fun ComicChoosingHeader(title: String, onSelectAll: () -> Unit, onDeselectAll: () -> Unit, anySelected: Boolean, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, tr("Stop selecting"), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            SelectAllButtons(onSelectAll, onDeselectAll, anySelected = anySelected, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** On a tablet: the search, how many there are, and the order. */
@Composable
private fun ComicSearchRow(browse: LibraryBrowseState, series: Int, comics: Int, wide: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = browse.comicQuery,
            onValueChange = { browse.comicQuery = it },
            singleLine = true,
            placeholder = { Text(tr("Search the comics")) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (browse.comicQuery.isNotEmpty()) ({ IconButton(onClick = { browse.comicQuery = "" }) { Icon(Icons.Filled.Close, tr("Clear the search")) } }) else null,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("comic-search"),
        )
        Spacer(Modifier.width(8.dp))
        ComicSortButton(browse, series, comics, wide)
    }
}

/** On a phone (the library's search looks for comics here): how many there are, and the order. */
@Composable
private fun ComicSortRow(browse: LibraryBrowseState, series: Int, comics: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            countLine(series, comics),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ComicSortButton(browse, null, null)
    }
}

private fun countLine(series: Int, comics: Int) = (if (series == 1) tr("1 series") else tr("{0} series", series)) + " · " + comicsLabel(comics)

/**
 * The order of the series; on a wide screen, where a series opens (beside the shelf, or in its place); and a way to
 * hide the Comics tab.
 */
@Composable
private fun ComicSortButton(browse: LibraryBrowseState, series: Int?, comics: Int?, wide: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("comic-sort")) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (series != null && comics != null) countLine(series, comics) else browse.comicSort.label, maxLines = 1)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            ComicSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    leadingIcon = { RadioButton(selected = s == browse.comicSort, onClick = null) },
                    onClick = { browse.comicSort = s; open = false },
                )
            }
            if (wide) {
                HorizontalDivider()
                Text(tr("A series opens"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                for (inPlace in listOf(false, true)) {
                    DropdownMenuItem(
                        text = { Text(if (inPlace) tr("In place of the shelf") else tr("Beside the shelf")) },
                        leadingIcon = { RadioButton(selected = browse.seriesInPlace == inPlace, onClick = null) },
                        onClick = { browse.openSeriesInPlace(inPlace); open = false },
                        modifier = Modifier.testTag(if (inPlace) "series-in-place" else "series-beside"),
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(tr("Hide the Comics tab")) },
                leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                onClick = {
                    open = false
                    browse.comicsTab = false
                    android.widget.Toast.makeText(context, tr("Comics are with your books now. You can bring the tab back in Settings."), android.widget.Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.testTag("comics-hide-tab"),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicSeriesItem(
    entry: ComicEntry,
    covers: LibraryCovers,
    here: (LibraryBook) -> ReadHere?,
    index: Int,
    count: Int,
    picked: Boolean?,
    highlighted: Boolean,
    padding: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val unread = entry.unread(here).size
    Column(
        Modifier
            .shelfUnderCover(coverTop = padding, index = index, count = count)
            .clip(RoundedCornerShape(12.dp))
            .then(if (highlighted && picked == null) Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(padding)
            .testTag("comic-series"),
    ) {
        ComicSeriesCover(entry.cover, covers, unread = unread, onKobo = entry.issues.all { it.koboPath != null }, modifier = Modifier.gridCover(), picked = picked)
        ShelfSpace()
        UnderCover {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.isSeries) comicsLabel(entry.issues.size) else entry.cover.authorLine ?: comicsLabel(1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A series' page, as Mihon's: its cover and details, a button to carry on where it was left (the first comic not read
 * yet), and its comics in order, each showing how far it's been read. A comic tapped opens in the reader; held, it's
 * chosen, to mark as read, send, take out of the series or remove. [onBack] goes back to the shelf, where it isn't beside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComicSeriesPage(
    entry: ComicEntry,
    covers: LibraryCovers,
    browse: LibraryBrowseState,
    koboConnected: Boolean,
    here: (LibraryBook) -> ReadHere?,
    actions: ComicActions,
    onBack: (() -> Unit)?,
    /** How its comics show: as covers (each an entry of its own, with its words under it, on it, or none), or a list. */
    mode: dev.kolib.app.ui.components.ShelfMode = dev.kolib.app.ui.components.ShelfMode.LIST,
    /** Chooses how its comics show (and how many covers to a row), where the series has the screen's width. */
    onMode: ((dev.kolib.app.ui.components.ShelfMode) -> Unit)? = null,
    /** The library's other series and comics: this one's comics can be put in one of them. */
    others: List<ComicEntry> = emptyList(),
    /** A comic's details are had by holding it, then Info in the bar for it, rather than from a button on each. */
    infoAtTop: Boolean = false,
) {
    val phone = phoneLayout()
    val choosing = browse.comicSelecting == ComicSelecting.ISSUES
    var confirmRemove by remember { mutableStateOf<List<String>?>(null) }
    var naming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    if (choosing) {
        TabBackHandler { browse.stopComicSelecting() }
        if (phone) HideBottomBar()
    }
    val issues = if (browse.comicIssuesDescending) entry.issues.reversed() else entry.issues
    val chosen = { issues.filter { browse.comicPicked.containsKey(it.id) }.map { it.id } }
    val done = { browse.stopComicSelecting() }
    val next = entry.next(here)
    val unread = entry.unread(here).size
    val notOnKobo = entry.issues.filter { it.koboPath == null }
    Box(Modifier.fillMaxSize().testTag("comic-series-page")) {
        Column(Modifier.fillMaxSize()) {
            val chosenCount = browse.comicPicked.size
            val title = if (chosenCount == 0) tr("Tap comics to select them") else if (chosenCount == 1) tr("1 comic selected") else tr("{0} comics selected", chosenCount)
            if (choosing && phone) {
                Box(Modifier.windowInsetsPadding(TopInsets)) { ComicChoosingHeader(title, onSelectAll = { issues.forEach { browse.comicPicked[it.id] = true } }, onDeselectAll = { browse.comicPicked.clear() },
                    anySelected = browse.comicPicked.isNotEmpty(), onCancel = done) }
            } else if (phone || onBack != null) {
                // A bar with the way back, and the series' menu.
                Row(
                    Modifier.fillMaxWidth().then(if (phone) Modifier.windowInsetsPadding(TopInsets) else Modifier).padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back to the comics")) }
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    // How the comics show and how many covers to a row, chosen over them, as for the shelf.
                    if (onMode != null) dev.kolib.app.ui.components.DisplayButton(mode, onMode, tag = "comic-series-layout")
                    SeriesMenu(entry, menu, { menu = it }, actions, onRename = { naming = true }, onAddTo = { adding = true }, onRemove = { removing = true })
                }
            }
            // [inset]: the room the grid leaves at its sides already, so the head lines up as it does in the list.
            val head: @Composable (inset: Dp) -> Unit = { inset ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp - inset, end = 12.dp - inset, top = if (phone || onBack != null) 8.dp else 20.dp)) {
                        LibraryCover(entry.cover, covers, large = false, modifier = Modifier.width(if (phone) 108.dp else 132.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            if (!phone && onBack == null) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    SeriesMenu(entry, menu, { menu = it }, actions, onRename = { naming = true }, onAddTo = { adding = true }, onRemove = { removing = true })
                                }
                            } else {
                                Text(entry.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            entry.authors.takeIf { it.isNotEmpty() }?.let {
                                Text(it.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                comicsLabel(entry.issues.size) + when (unread) {
                                    0 -> tr(" · all read")
                                    entry.issues.size -> ""
                                    else -> tr(" · {0} not read yet", unread)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            if (next != null) {
                                Button(onClick = { actions.onRead(next.id) }, modifier = Modifier.testTag("comic-continue")) {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (!entry.started(here)) tr("Start reading") else if (entry.isSeries) tr("Carry on: {0}", issueName(entry, next)) else tr("Carry on"),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (koboConnected && notOnKobo.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(onClick = { actions.onSend(notOnKobo.map { it.id }) }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (notOnKobo.size == entry.issues.size) tr("Send all to Kobo") else tr("Send {0} to Kobo", notOnKobo.size), maxLines = 1)
                                }
                            }
                        }
                    }
                    entry.description?.let { html ->
                        val text = remember(html) { descriptionText(html) }
                        if (text.isNotBlank()) {
                            var more by remember(entry.key) { mutableStateOf(false) }
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (more) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 20.dp - inset, end = 20.dp - inset, top = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { more = !more }),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp - inset, end = 8.dp - inset, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(comicsLabel(entry.issues.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (entry.issues.size > 1) {
                            TextButton(onClick = { browse.comicIssuesDescending = !browse.comicIssuesDescending }) {
                                Icon(Icons.Outlined.SwapVert, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (browse.comicIssuesDescending) tr("Last to first") else tr("First to last"))
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp - inset), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
            val tap = { issue: LibraryBook ->
                if (choosing) {
                    if (browse.comicPicked.remove(issue.id) == null) browse.comicPicked[issue.id] = true
                } else actions.onRead(issue.id)
            }
            val hold = { issue: LibraryBook ->
                if (!choosing) {
                    browse.stopComicSelecting()
                    browse.comicSelecting = ComicSelecting.ISSUES
                }
                browse.comicPicked[issue.id] = true
            }
            // The comics, and while some are being chosen, the bar for them along the foot of the screen, its whole width.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mode != dev.kolib.app.ui.components.ShelfMode.LIST) {
                    // Each comic an entry of its own, as the series are on the shelf.
                    androidx.compose.runtime.CompositionLocalProvider(dev.kolib.app.ui.components.LocalShelfMode provides mode) {
                    LazyVerticalGrid(
                        columns = shelfCells(chosenPerRow(), shelfMinCell()),
                        state = browse.comicIssueGrid,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().testTag("comic-issue-grid"),
                    ) {
                        item(key = "head", span = { GridItemSpan(maxLineSpan) }) { head(8.dp) }
                        items(issues, key = { it.id }) { issue ->
                            ComicIssueTile(
                                issue, entry, here(issue), covers,
                                picked = if (choosing) browse.comicPicked.containsKey(issue.id) else null,
                                onTap = { tap(issue) },
                                onHold = { hold(issue) },
                                onDetails = if (infoAtTop) null else ({ browse.comicBookId = issue.id }),
                            )
                        }
                    }
                    }
                } else LazyColumn(Modifier.fillMaxSize(), state = browse.comicIssues, contentPadding = PaddingValues(bottom = if (phone) 104.dp else 24.dp)) {
                    item(key = "head") { head(0.dp) }
                    items(issues, key = { it.id }) { issue ->
                        ComicIssueRow(
                            issue, entry, here(issue),
                            picked = if (choosing) browse.comicPicked.containsKey(issue.id) else null,
                            onTap = { tap(issue) },
                            onHold = { hold(issue) },
                            onDetails = if (infoAtTop) null else ({ browse.comicBookId = issue.id }),
                        )
                    }
                }
            }
            if (choosing && !phone) AtFoot {
                SelectionBar(
                    count = 0, title = title, anySelected = browse.comicPicked.isNotEmpty(),
                    onSelectAll = { issues.forEach { browse.comicPicked[it.id] = true } }, onDeselectAll = { browse.comicPicked.clear() }, onCancel = done,
                    moreTag = "comic-issues-more",
                    actions = browse.comicPicked.size.let { count2 ->
                        comicIssueActions(
                            count2, entry.isSeries,
                            onSend = { actions.onSend(chosen()); done() }, onRead = { actions.onMarkFinished(chosen()); done() },
                            onUnread = { actions.onMarkUnread(chosen()); done() }, onTakeOut = { actions.onTakeOut(chosen()); done() },
                            onRemove = { confirmRemove = chosen() },
                            onInfo = if (infoAtTop && count2 == 1) ({ chosen().firstOrNull()?.let { id -> done(); browse.comicBookId = id } }) else null,
                        )
                    },
                )
            }
        }
        if (choosing && phone) {
            val count = browse.comicPicked.size
            PhoneActionBar(
                comicIssueActions(
                    count, entry.isSeries,
                    onSend = { actions.onSend(chosen()); done() }, onRead = { actions.onMarkFinished(chosen()); done() },
                    onUnread = { actions.onMarkUnread(chosen()); done() }, onTakeOut = { actions.onTakeOut(chosen()); done() },
                    onRemove = { confirmRemove = chosen() },
                ),
                Modifier.align(Alignment.BottomCenter),
                moreTag = "comic-issues-more",
            )
        }
    }
    if (removing) {
        RemoveSeriesDialog(
            series = listOf(entry),
            onKeepComics = { actions.onTakeOut(entry.issues.map { it.id }); removing = false },
            onRemoveComics = { actions.onDelete(entry.issues.map { it.id }); removing = false },
            onDismiss = { removing = false },
        )
    }
    if (adding) {
        AddToSeriesDialog(
            series = others.filter { it.isSeries && it.key != entry.key },
            covers = covers,
            onPick = { e -> actions.onGroup(entry.issues.map { it.id }, e.title); adding = false },
            onNew = { adding = false; naming = true },
            onDismiss = { adding = false },
        )
    }
    if (naming) {
        ComicSeriesNameDialog(
            title = if (entry.isSeries) tr("Rename the series") else tr("Make it a series"),
            initial = if (entry.isSeries) entry.title else ComicSeries.parse(ComicSeries.nameOf(entry.cover)).stem.ifEmpty { entry.title },
            onDone = { name -> actions.onGroup(entry.issues.map { it.id }, name); naming = false },
            onDismiss = { naming = false },
        )
    }
    confirmRemove?.let { ids ->
        RemoveBooksDialog(ids, onRemove = { actions.onDelete(ids); confirmRemove = null; done() }, onDismiss = { confirmRemove = null })
    }
}

/** The series' menu: mark them all read or unread, name the series, or put its comics in another. */
@Composable
private fun SeriesMenu(
    entry: ComicEntry,
    open: Boolean,
    setOpen: (Boolean) -> Unit,
    actions: ComicActions,
    onRename: () -> Unit,
    onAddTo: () -> Unit,
    onRemove: () -> Unit,
) {
    Box {
        IconButton(onClick = { setOpen(true) }, modifier = Modifier.testTag("comic-series-menu")) { Icon(Icons.Outlined.MoreVert, tr("More")) }
        DropdownMenu(open, onDismissRequest = { setOpen(false) }) {
            val ids = entry.issues.map { it.id }
            DropdownMenuItem(text = { Text(tr("Mark all as read")) }, leadingIcon = { Icon(Icons.Outlined.DoneAll, null) }, onClick = { setOpen(false); actions.onMarkFinished(ids) })
            DropdownMenuItem(text = { Text(tr("Mark all as unread")) }, leadingIcon = { Icon(Icons.Outlined.RemoveDone, null) }, onClick = { setOpen(false); actions.onMarkUnread(ids) })
            DropdownMenuItem(
                text = { Text(if (entry.isSeries) tr("Rename the series") else tr("Make it a series")) },
                leadingIcon = { Icon(Icons.Outlined.LibraryAdd, null) },
                onClick = { setOpen(false); onRename() },
            )
            DropdownMenuItem(
                text = { Text(if (entry.isSeries) tr("Add its comics to another series") else tr("Add to series")) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
                onClick = { setOpen(false); onAddTo() },
                modifier = Modifier.testTag("comic-series-add-to"),
            )
            if (entry.isSeries) {
                DropdownMenuItem(
                    text = { Text(tr("Remove the series")) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { setOpen(false); onRemove() },
                    modifier = Modifier.testTag("comic-series-remove"),
                )
            }
        }
    }
}

/** What a comic is called in its series' list: its number there ("#3") when its name is the series' with a number on. */
internal fun issueName(entry: ComicEntry, issue: LibraryBook): String {
    val n = issue.meta.seriesIndex ?: ComicSeries.parse(ComicSeries.nameOf(issue)).number
    val bare = ComicSeries.key(issue.title).removePrefix(ComicSeries.key(entry.title)).trim()
    val number = n?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
    // "Saga 001" in Saga is #1; "The Way Home" in it stays as it is.
    return if (number != null && entry.isSeries && (bare.isEmpty() || bare.trimStart('0').ifEmpty { "0" } == number || bare.all { it.isDigit() || it == ' ' })) "#$number" else issue.title
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicIssueRow(
    issue: LibraryBook,
    entry: ComicEntry,
    readHere: ReadHere?,
    picked: Boolean?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    /** Its details, from a button of its own; null where they're had another way (held, then Info at the top). */
    onDetails: (() -> Unit)?,
) {
    val read = ComicSeries.isRead(readHere)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (picked == true) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 52.dp)
            .testTag("comic-issue"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when {
                picked != null -> Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { if (picked) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                read -> Icon(Icons.Filled.Check, dev.kolib.app.trc("badge", "Read"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                readHere?.started == true -> Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
        Spacer(Modifier.width(12.dp))
        // Read ones are dimmed, as in Mihon.
        Column(Modifier.weight(1f).alpha(if (read && picked != true) 0.55f else 1f)) {
            Text(issueName(entry, issue), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val state = when {
                read -> dev.kolib.app.trc("badge", "Read")
                readHere?.started == true -> tr("{0}% read", readHere.percent)
                else -> tr("Not read yet")
            }
            Text(
                state + if (issue.koboPath != null) tr(" · on the Kobo") else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (readHere?.started == true && !read) {
                LinearProgressIndicator(
                    progress = { readHere.progress },
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.6f).height(3.dp),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
        if (picked == null && onDetails != null) IconButton(onClick = onDetails) { Icon(Icons.Outlined.Info, tr("Details")) }
    }
}

/** A comic in its series' grid: its cover (how far it's been read on it), its name, and its details to open. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicIssueTile(
    issue: LibraryBook,
    entry: ComicEntry,
    readHere: ReadHere?,
    covers: LibraryCovers,
    picked: Boolean?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    /** Its details, from a button of its own; null where they're had another way (held, then Info at the top). */
    onDetails: (() -> Unit)?,
) {
    val read = ComicSeries.isRead(readHere)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (picked == true) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(8.dp)
            .testTag("comic-issue"),
    ) {
        // Read ones are dimmed, as in the list.
        LibraryCover(
            issue, covers, large = false, onKobo = issue.koboPath != null, picked = picked, readHere = readHere,
            modifier = Modifier.gridCover().alpha(if (read && picked != true) 0.55f else 1f),
        )
        // Its number and how far it's been read, under it in the comfortable grid only.
        if (dev.kolib.app.ui.components.LocalShelfMode.current == dev.kolib.app.ui.components.ShelfMode.COMFORTABLE) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(issueName(entry, issue), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        read -> dev.kolib.app.trc("badge", "Read")
                        readHere?.started == true -> tr("{0}% read", readHere.percent)
                        else -> tr("Not read yet")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (picked == null && onDetails != null) {
                IconButton(onClick = onDetails, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Info, tr("Details"), Modifier.size(20.dp)) }
            }
        }
    }
}

/**
 * Choosing the series to put comics in: one there is (by name, A to Z, searched where there are many), or a new one,
 * named next.
 */
@Composable
internal fun AddToSeriesDialog(
    series: List<ComicEntry>,
    covers: LibraryCovers,
    onPick: (ComicEntry) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sorted = remember(series) { series.sortedWith { a, b -> ComicSeries.naturalCompare(a.title, b.title) } }
    val shown = remember(sorted, query) { query.trim().let { q -> if (q.isEmpty()) sorted else sorted.filter { it.title.contains(q, ignoreCase = true) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add to series")) },
        text = {
            Column {
                if (sorted.size > 6) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(tr("Search the series")) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onNew).padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("comics-new-series"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LibraryAdd, null) }
                    Spacer(Modifier.width(12.dp))
                    Text(tr("A new series…"), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
                if (sorted.isEmpty()) {
                    Text(
                        tr("There are no series yet."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.key }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(e) }.padding(vertical = 6.dp, horizontal = 4.dp)
                                .testTag("add-to-series"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ComicSeriesCover(e.cover, covers, unread = 0, onKobo = false, modifier = Modifier.width(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(comicsLabel(e.issues.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Naming the series comics go in. */
@Composable
internal fun ComicSeriesNameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(tr("They show as one series in the Comics tab, in order."), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(tr("Series")) },
                    modifier = Modifier.fillMaxWidth().testTag("comic-series-name"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("comic-series-name-done")) { Text(tr("Done")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
