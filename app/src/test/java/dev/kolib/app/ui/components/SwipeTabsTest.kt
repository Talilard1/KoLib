package dev.kolib.app.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tabs that can be swiped between: the tab follows the swipe, a tab set from elsewhere is gone to, and Back goes to the tab on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-mdpi")
class SwipeTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private var tab by mutableIntStateOf(0)
    private var swipe = true
    private val backs = IntArray(3)
    private lateinit var back: OnBackPressedDispatcher

    private fun show() {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            Column(Modifier.fillMaxSize()) {
                val pager = rememberTabPager(tab, 3) { tab = it }
                val goTo = rememberTabTap(pager)
                PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                    listOf("Books", "Collections", "Device").forEachIndexed { i, name ->
                        Tab(selected = pager.currentPage == i, onClick = { goTo(i) }, text = { Text(name) })
                    }
                }
                TabPages(pager, Modifier.fillMaxSize().testTag("pages"), swipe = swipe) { page ->
                    TabBackHandler { backs[page]++ }
                    Box(Modifier.fillMaxSize()) { Text("Page $page") }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pressBack() {
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun swipingMovesBetweenTheTabsAndTheTabFollows() {
        show()
        compose.onNodeWithTag("pages").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, tab)
        compose.onNodeWithText("Page 1").assertExists()
        compose.onNodeWithTag("pages").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(2, tab)
        // Past the last there's nothing more.
        compose.onNodeWithTag("pages").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(2, tab)
        compose.onNodeWithTag("pages").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(1, tab)
    }

    @Test
    fun tappingATabAndSettingOneFromElsewhereBothGoThere() {
        show()
        compose.onNodeWithText("Device").performClick()
        compose.waitForIdle()
        assertEquals(2, tab)
        compose.onNodeWithText("Page 2").assertExists()
        // As a search or a tag does: the tab is set, and the pages follow.
        tab = 0
        compose.waitForIdle()
        compose.onNodeWithText("Page 0").assertExists()
        compose.onNodeWithText("Page 2").assertDoesNotExist()
    }

    @Test
    fun backGoesToTheTabOnScreenOnly() {
        show()
        pressBack()
        assertEquals(listOf(1, 0, 0), backs.toList())
        compose.onNodeWithTag("pages").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        pressBack()
        assertEquals(listOf(1, 1, 0), backs.toList())
    }

    @Test
    fun noSwipingWhileItsOff() {
        swipe = false
        show()
        compose.onNodeWithTag("pages").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(0, tab)
        // The tabs still work.
        compose.onNodeWithText("Collections").performClick()
        compose.waitForIdle()
        assertEquals(1, tab)
    }
}
