package dev.kolib.app.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.tr

/**
 * A book's details beside the shelf on a wide screen, opened by tapping the book: with a ✕ that closes them and gives
 * the shelf the whole width back (as Back does).
 */
@Composable
fun SidePane(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("side-pane-close")) { Icon(Icons.Filled.Close, tr("Close")) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** A shelf's scrolling, as covers or as a list, for [KeepsItsPlace]. */
internal class ShelfScroll(
    val first: () -> Int,
    val offset: () -> Int,
    val interactions: androidx.compose.foundation.interaction.InteractionSource,
    /** The item at this place is wholly on the screen. */
    val inView: (Int) -> Boolean,
    val scrollTo: suspend (Int, Int) -> Unit,
    /** The item at this place to about a third of the way down the screen. */
    val bringIntoView: suspend (Int) -> Unit,
)

internal fun androidx.compose.foundation.lazy.grid.LazyGridState.shelfScroll() = ShelfScroll(
    first = { firstVisibleItemIndex }, offset = { firstVisibleItemScrollOffset }, interactions = interactionSource,
    inView = { i ->
        val info = layoutInfo
        info.visibleItemsInfo.any { it.index == i && it.offset.y >= info.viewportStartOffset && it.offset.y + it.size.height <= info.viewportEndOffset }
    },
    scrollTo = { i, o -> scrollToItem(i, o) },
    bringIntoView = { i ->
        scrollToItem(i)
        scrollBy(-(layoutInfo.viewportSize.height / 3f))
    },
)

internal fun androidx.compose.foundation.lazy.LazyListState.shelfScroll() = ShelfScroll(
    first = { firstVisibleItemIndex }, offset = { firstVisibleItemScrollOffset }, interactions = interactionSource,
    inView = { i ->
        val info = layoutInfo
        info.visibleItemsInfo.any { it.index == i && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
    },
    scrollTo = { i, o -> scrollToItem(i, o) },
    bringIntoView = { i ->
        scrollToItem(i)
        scrollBy(-(layoutInfo.viewportSize.height / 3f))
    },
)

/**
 * A shelf narrowed by what opens beside it (a book's details, a series) keeps the item tapped on the screen; widened
 * again when that closes, it's back where it was before, unless it was scrolled in between. [open] is what's open (null
 * for nothing), and [indexOf] its place among the shelf's items.
 */
@androidx.compose.runtime.Composable
internal fun KeepsItsPlace(scroll: ShelfScroll, open: Any?, indexOf: (Any) -> Int?) {
    val isOpen = open != null
    val before = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null) }
    val scrolled = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val latestOpen = androidx.compose.runtime.rememberUpdatedState(open)
    androidx.compose.runtime.LaunchedEffect(isOpen) {
        if (!isOpen) {
            // Closed: back where it was, if it wasn't scrolled while open; then where it is is kept note of, till it opens.
            before.value?.let { (i, o) -> if (!scrolled.value) scroll.scrollTo(i, o) }
            androidx.compose.runtime.snapshotFlow { scroll.first() to scroll.offset() }.collect { before.value = it }
        } else {
            scrolled.value = false
            kotlinx.coroutines.delay(1)  // the shelf narrowed first
            latestOpen.value?.let(indexOf)?.let { i -> if (!scroll.inView(i)) scroll.bringIntoView(i) }
            scroll.interactions.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) scrolled.value = true }
        }
    }
}
