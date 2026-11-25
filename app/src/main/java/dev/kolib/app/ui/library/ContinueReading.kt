package dev.kolib.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.tr
import dev.kolib.app.ui.components.LibraryCover

/** How much of a wide screen the place beside a shelf takes, for a book's details or a series. */
internal const val SIDE_SHARE = 0.41f

/** The books started but not finished in KoLib's reader, the last read first. */
internal fun beingRead(books: List<LibraryBook>, here: (LibraryBook) -> ReadHere?, most: Int = 12): List<LibraryBook> =
    books.mapNotNull { b -> here(b)?.takeIf { it.started && it.percent < 100 }?.let { b to it.at } }
        .sortedByDescending { it.second }
        .take(most)
        .map { it.first }

/**
 * The books being read, along the top of the Books tab (as Apple Books opens on Continue, and Mihon's covers have a
 * button to carry on): tapped, one opens in the reader where it was left; held, it's chosen, as on the shelf, so
 * several can be marked unread (or the like) together. While choosing, a tap chooses one, or lets it go: [picked]
 * says which are.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContinueReadingRow(
    books: List<LibraryBook>,
    covers: LibraryCovers,
    here: (LibraryBook) -> ReadHere?,
    onTap: (LibraryBook) -> Unit,
    onHold: (LibraryBook) -> Unit,
    picked: ((LibraryBook) -> Boolean)? = null,
    sidePadding: Dp = 16.dp,
    coverWidth: Dp = 84.dp,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("continue-reading")) {
        Text(
            tr("Continue reading"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = sidePadding, top = 8.dp, bottom = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = sidePadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books, key = { it.id }) { book ->
                Column(
                    Modifier
                        .width(coverWidth)
                        .combinedClickable(onClick = { onTap(book) }, onLongClick = { onHold(book) })
                        .testTag("continue-book"),
                ) {
                    LibraryCover(book, covers, large = false, picked = picked?.invoke(book), readHere = here(book), modifier = Modifier.width(coverWidth))
                    Spacer(Modifier.height(6.dp))
                    Text(book.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    here(book)?.let { r ->
                        Text(tr("{0}% read", r.percent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * The row of what's being read comes in at the top of a shelf once what's being read is known, often just after the
 * shelf first shows (as when KoLib's just been updated): a shelf still at its top is kept at its very top, the row
 * showing, rather than staying on its first books with the row above them, out of sight. One scrolled down stays put.
 */
@androidx.compose.runtime.Composable
internal fun ContinueRowKeepsTop(shown: Boolean, grid: androidx.compose.foundation.lazy.grid.LazyGridState, list: androidx.compose.foundation.lazy.LazyListState) {
    androidx.compose.runtime.LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        if (grid.firstVisibleItemIndex <= 1 && grid.firstVisibleItemScrollOffset == 0) grid.scrollToItem(0)
        if (list.firstVisibleItemIndex <= 1 && list.firstVisibleItemScrollOffset == 0) list.scrollToItem(0)
    }
}
