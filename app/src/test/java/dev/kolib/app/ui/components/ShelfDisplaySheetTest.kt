package dev.kolib.app.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sheet over a shelf, as Mihon's: its tabs swiped between, the look at the end of Display, dragged down to close. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ShelfDisplaySheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var tab = 0
    private var picked: ThemeChoice? = null
    private var closed = false

    private fun show() {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface {
                CompositionLocalProvider(LocalLook provides LookChoosing(ThemeChoice.SYSTEM) { picked = it }) {
                    ShelfDisplaySheet(
                        ShelfMode.COMFORTABLE, {},
                        extras = ShelfExtras(filter = { Text("What shows") }, sort = { Text("What order") }),
                        tab = 0, onTab = { tab = it }, onDismiss = { closed = true },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theTabsAreSwipedBetweenAndTheLookIsAtTheEndOfDisplay() {
        show()
        compose.onNodeWithText("What shows").assertExists()
        compose.onNodeWithTag("display-sheet").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("What order").assertExists()
        assertEquals(1, tab)
        compose.onNodeWithTag("display-tab-display").performClick()
        compose.waitForIdle()
        assertEquals(2, tab)
        compose.onNodeWithTag("look-DARK").performScrollTo().performClick()
        assertEquals(ThemeChoice.DARK, picked)
    }

    @Test
    fun draggedDownTheSheetCloses() {
        show()
        compose.onNodeWithTag("display-sheet").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertTrue(closed)
    }

    /** A short, slow pull down on what's in it closes it, as Mihon's does; a smaller one lets it come back up. */
    @Test
    fun aShortPullClosesItAndASmallerOneDoesNot() {
        show()
        val pull = { dp: Float ->
            compose.onNodeWithTag("display-sheet").performTouchInput {
                down(center)
                repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, dp.dp.toPx() / 20), delayMillis = 50) }
                up()
            }
            compose.waitForIdle()
        }
        pull(30f)
        assertTrue("a small pull doesn't close it", !closed)
        compose.onNodeWithText("What shows").assertExists()
        pull(90f)
        assertTrue("a short pull does", closed)
    }
}
