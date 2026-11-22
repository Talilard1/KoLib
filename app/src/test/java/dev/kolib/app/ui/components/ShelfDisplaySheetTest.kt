package dev.kolib.app.ui.components

import androidx.compose.ui.geometry.Offset
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
import org.junit.Assert.assertFalse
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

    /** From [from] of the way down the sheet, it's pulled slowly by [slow] of its height, then quickly by [quick]. */
    private fun drag(from: Float, slow: Float, quick: Float = 0f) {
        val sheet = compose.onNodeWithTag("display-sheet")
        val h = sheet.fetchSemanticsNode().size.height.toFloat()
        sheet.performTouchInput {
            down(Offset(centerX, h * from))
            repeat(20) { moveBy(Offset(0f, h * slow / 20), delayMillis = 50) }
            if (quick != 0f) repeat(5) { moveBy(Offset(0f, h * quick / 5), delayMillis = 10) }
            up()
        }
        compose.waitForIdle()
    }

    private fun top() = compose.onNodeWithText("What shows").fetchSemanticsNode().boundsInRoot.top

    /**
     * Held by its tabs or by what's in it: pulled a little way down, it comes back up; pulled well down but let go
     * moving up, it comes back up; let go well down, it closes.
     */
    @Test
    fun itClosesWhenLetGoWellDownButNotALittleOrMovingUp() {
        show()
        val open = top()
        for (from in listOf(0.1f, 0.75f)) {
            drag(from, 0.25f)
            assertFalse("a little way down, held at $from, it closed", closed)
            assertEquals("a little way down, held at $from, it came back up", open, top(), 1f)
            drag(from, 0.75f, -0.15f)
            assertFalse("let go moving up, held at $from, it closed", closed)
            assertEquals("let go moving up, held at $from, it came back up", open, top(), 1f)
        }
        drag(0.75f, 0.6f)
        assertTrue("let go well down, it closes", closed)
    }

    /** On a big tablet (800 high) the sheet's in the middle of the screen, and it's dragged down to close just the same. */
    @Test
    @Config(qualifiers = "sw800dp-w1280dp-h800dp-land")
    fun onABigTabletItsInTheMiddleAndDraggedDownToCloseToo() {
        show()
        assertTrue("it's in the middle", compose.onNodeWithTag("display-sheet").fetchSemanticsNode().boundsInRoot.bottom < 650f)
        val open = top()
        drag(0.75f, 0.25f)
        assertFalse("a little way down, it closed", closed)
        assertEquals("a little way down, it came back", open, top(), 1f)
        drag(0.75f, 0.75f, -0.15f)
        assertFalse("let go moving up, it closed", closed)
        assertEquals("let go moving up, it came back", open, top(), 1f)
        drag(0.75f, 0.6f)
        assertTrue("let go well down, it closes", closed)
    }
}
