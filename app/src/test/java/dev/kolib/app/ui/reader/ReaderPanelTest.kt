package dev.kolib.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Slider
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
 * The reader's side panels: a tap beside one closes it; a finger dragged beside it or on it slides it closed if thrown
 * or taken most of the way, and it springs back open otherwise (a slider in it keeps its own drags). Pulled out from the
 * edge, it follows the finger. In the contents panel, a swipe across its top part goes to the next tab.
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
    fun aDragOnThePanelItselfClosesIt() {
        open()
        compose.onNodeWithTag("inside").performTouchInput {
            down(Offset(250f, 350f))
            repeat(30) { moveBy(Offset(-200f / 30, 0f), 16) }
            up()
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aSliderInThePanelKeepsItsDrag() {
        var value by mutableStateOf(0.2f)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                ReaderPanel(true, wide = false, width = 300.dp, onDismiss = { dismissed++ }, fromLeft = true) {
                    Box(Modifier.fillMaxSize().testTag("inside")) {
                        Slider(value, { value = it }, Modifier.padding(top = 200.dp, start = 20.dp, end = 20.dp).testTag("slider"))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("slider").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(-6f, 0f), 16) }
            up()
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals("the panel stays", 0, dismissed)
        assertTrue("the slider moved: $value", value < 0.2f || value == 0f)
        assertEquals(0f, panelLeft(), 0.5f)
    }

    @Test
    fun pulledOutFromTheEdgeItFollowsTheFingerThenOpens() {
        var pulled by mutableStateOf<Float?>(0f)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                ReaderPanel(true, wide = false, width = 300.dp, onDismiss = { dismissed++ }, fromLeft = true, pulled = pulled) {
                    Box(Modifier.fillMaxSize().testTag("inside")) { Text("Contents") }
                }
            }
        }
        compose.waitForIdle()
        assertEquals("not out yet", -300f, panelLeft(), 0.5f)
        pulled = 0.3f
        compose.waitForIdle()
        assertEquals("a third of the way out, under the finger", -210f, panelLeft(), 0.5f)
        pulled = 0.8f
        compose.waitForIdle()
        assertEquals(-60f, panelLeft(), 0.5f)
        // Let go: the rest of the way out.
        pulled = null
        compose.waitForIdle()
        assertEquals(0f, panelLeft(), 0.5f)
    }

    /** Four tabs in a panel, as the contents panel has them. */
    private fun tabs(): PagerState {
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState { 4 }
            Box(Modifier.fillMaxSize()) {
                ReaderPanel(true, wide = false, width = 300.dp, onDismiss = { dismissed++ }, fromLeft = true) {
                    Column(Modifier.fillMaxSize().swipesTabs(pager).testTag("inside")) {
                        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = false) { page ->
                            LazyColumn(Modifier.fillMaxSize()) { items(40) { Text("Tab $page row $it") } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return pager
    }

    private fun swipeAcross(y: Float, dx: Float) {
        compose.onNodeWithTag("inside").performTouchInput {
            down(Offset(220f, y))
            repeat(20) { moveBy(Offset(dx / 20, 0f), 16) }
            up()
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun aSwipeAcrossTheTopOfTheContentsGoesToTheNextTab() {
        val pager = tabs()
        swipeAcross(100f, -200f)
        assertEquals("the next tab", 1, pager.currentPage)
        assertEquals("the panel stays", 0, dismissed)
        swipeAcross(100f, 200f)
        assertEquals("and back", 0, pager.currentPage)
        assertEquals(0, dismissed)
    }

    @Test
    fun aSwipeAcrossLowerDownClosesThePanelInstead() {
        val pager = tabs()
        swipeAcross(500f, -200f)
        assertEquals(1, dismissed)
        assertEquals("the tab stays", 0, pager.currentPage)
    }

    @Test
    fun theContentsTopHasTheBooksNameAndNoButtons() {
        compose.setContent { DrawerHeader("The Sea", subtitle = "40% read") }
        compose.onNodeWithText("The Sea").assertExists()
        // Leaving the book is the bar over the page's; a tap beside the drawer closes it, so it has no button to.
        compose.onNodeWithTag("contents-leave").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close the book").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
    }
}
