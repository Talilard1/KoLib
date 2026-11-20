package dev.kolib.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reader's side panels: a tap beside one closes it; a finger dragged beside it slides it closed if thrown or taken
 * most of the way, and it springs back open otherwise. The contents panel's top has a button to leave the book, at the left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h700dp-mdpi")
class ReaderPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private var dismissed = 0

    /** The contents panel's place: 300 wide from the left of a 400 by 700 screen (mdpi: a px is a dp). */
    private fun open() {
        var visible by mutableStateOf(true)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                ReaderPanel(visible, wide = false, width = 300.dp, onDismiss = { dismissed++; visible = false }, fromLeft = true) {
                    Box(Modifier.fillMaxSize().testTag("inside")) { Text("Contents") }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun panelLeft() = compose.onNodeWithTag("inside").fetchSemanticsNode().boundsInRoot.left

    /** A finger put down beside the panel and moved [dx] across in [steps], [ms] apart. */
    private fun drag(dx: Float, steps: Int = 12, ms: Long = 16) {
        compose.onNodeWithTag("reader-panel-shade").performTouchInput {
            down(Offset(380f, 350f))
            repeat(steps) { moveBy(Offset(dx / steps, 0f), ms) }
        }
        compose.waitForIdle()
    }

    private fun letGo() {
        compose.onNodeWithTag("reader-panel-shade").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun aTapBesideItClosesIt() {
        open()
        assertEquals(0f, panelLeft(), 0.5f)
        compose.onNodeWithTag("reader-panel-shade").performTouchInput { click(Offset(360f, 300f)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aShortDragFollowsTheFingerThenSpringsBack() {
        open()
        drag(-60f)
        // It follows the finger...
        assertTrue("at ${panelLeft()}", panelLeft() < -40f)
        letGo()
        // ...and, let go a little way along without a flick, springs back open.
        assertEquals(0, dismissed)
        assertEquals(0f, panelLeft(), 0.5f)
    }

    @Test
    fun draggedMostOfTheWayItCloses() {
        open()
        drag(-200f, steps = 30)
        letGo()
        assertEquals(1, dismissed)
    }

    @Test
    fun aFlickClosesIt() {
        open()
        drag(-60f, steps = 6, ms = 8)
        letGo()
        assertEquals(1, dismissed)
    }

    @Test
    fun draggedTheOtherWayItStaysOpen() {
        open()
        drag(60f)
        assertEquals(0f, panelLeft(), 0.5f)
        letGo()
        assertEquals(0, dismissed)
    }

    @Test
    fun theContentsTopHasAButtonToLeaveTheBookAtTheLeft() {
        var left = 0
        var closed = 0
        compose.setContent { DrawerHeader("The Sea", subtitle = "40% read", onClose = { closed++ }, onLeave = { left++ }) }
        val back = compose.onNodeWithTag("contents-leave").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("The Sea").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
        // At the left, level with the book's name.
        assertTrue("back at $back", back.left < 12f && back.top < title.bottom && back.bottom > title.top)
        assertTrue(back.right <= title.left && title.right <= close.left)
        compose.onNodeWithTag("contents-leave").performClick()
        assertEquals(1, left)
        assertEquals(0, closed)
    }
}
