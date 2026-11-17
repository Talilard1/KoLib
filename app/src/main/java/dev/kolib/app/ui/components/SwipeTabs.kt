package dev.kolib.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Whether the tab something is in is the one on screen. While a swipe is under way the tab beside it is drawn too,
 * and Android may get it ready before then; neither should answer Back.
 */
val LocalTabShowing = compositionLocalOf { true }

/** Back for something inside a tab: only while that tab is on screen. */
@Composable
fun TabBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled = enabled && LocalTabShowing.current, onBack = onBack)

/**
 * The pages of a row of tabs, for swiping between them as well as tapping. [tab] is the tab showing; [onTab] is
 * called when a swipe or a tap lands on another. A tab changed from elsewhere (a search, a tag) is gone to at once.
 */
@Composable
fun rememberTabPager(tab: Int, count: Int, onTab: (Int) -> Unit): PagerState {
    val pager = rememberPagerState(initialPage = tab.coerceIn(0, count - 1)) { count }
    val latestOnTab by rememberUpdatedState(onTab)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { latestOnTab(it) } }
    LaunchedEffect(tab) {
        val wanted = tab.coerceIn(0, count - 1)
        if (wanted != pager.settledPage && !pager.isScrollInProgress) pager.scrollToPage(wanted)
    }
    return pager
}

/** Goes to tab [index], sliding across as a swipe would. */
@Composable
fun rememberTabTap(pager: PagerState): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    return { index -> scope.launch { pager.animateScrollToPage(index) } }
}

/** The tabs' pages, side by side: [swipe] off while something in them needs the sideways movement (choosing books). */
@Composable
fun TabPages(pager: PagerState, modifier: Modifier = Modifier, swipe: Boolean = true, page: @Composable (Int) -> Unit) {
    HorizontalPager(pager, modifier, userScrollEnabled = swipe, verticalAlignment = Alignment.Top, key = { it }) { index ->
        CompositionLocalProvider(LocalTabShowing provides (index == pager.settledPage)) { page(index) }
    }
}
