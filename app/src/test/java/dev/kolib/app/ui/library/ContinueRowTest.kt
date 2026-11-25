package dev.kolib.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The row of what's being read comes in once what's being read is known, after the shelf's first shown (as just after
 * an update): a shelf at its top stays at its very top, the row showing; one scrolled down stays where it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-mdpi")
class ContinueRowTest {
    @get:Rule
    val compose = createComposeRule()

    private var shown by mutableStateOf(false)

    private fun shelf(grid: LazyGridState) {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                ContinueRowKeepsTop(shown, grid, LazyListState())
                LazyVerticalGrid(GridCells.Fixed(3), state = grid, modifier = Modifier.fillMaxSize()) {
                    if (shown) item(key = "continue", span = { GridItemSpan(maxLineSpan) }) { Text("Continue reading", Modifier.height(160.dp).testTag("continue")) }
                    items((1..90).toList(), key = { it }) { Text("Book $it", Modifier.height(140.dp)) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aShelfAtItsTopStaysAtItsVeryTopWhenTheRowComesIn() {
        val grid = LazyGridState()
        shelf(grid)
        shown = true
        compose.waitForIdle()
        assertEquals(0, grid.firstVisibleItemIndex)
        assertEquals(0, grid.firstVisibleItemScrollOffset)
        compose.onNodeWithTag("continue").assertIsDisplayed()
    }

    @Test
    fun aShelfScrolledDownStaysWhereItIs() {
        val grid = LazyGridState()
        shelf(grid)
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        val at = grid.firstVisibleItemIndex
        assertTrue(at > 3)
        shown = true
        compose.waitForIdle()
        assertTrue("still down the shelf: ${grid.firstVisibleItemIndex}", grid.firstVisibleItemIndex >= at)
    }
}
