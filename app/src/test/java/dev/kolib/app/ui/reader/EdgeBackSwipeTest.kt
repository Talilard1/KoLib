package dev.kolib.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** KoLib's back swipe takes swipes in from the side edges, and leaves taps and everything else alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h700dp-mdpi")
class EdgeBackSwipeTest {

    @get:Rule
    val compose = createComposeRule()

    private var backs = 0
    private var taps = 0
    private val state = EdgeBackState()

    private fun setUp(enabled: Boolean = true) {
        compose.setContent {
            Box(Modifier.size(400.dp, 700.dp).testTag("root").edgeBackSwipe(state, enabled) { backs++ }) {
                Box(Modifier.fillMaxSize().clickable { taps++ })
            }
        }
    }

    /** A swipe from [x] going [dx] across, in steps, then let go (mdpi: a px is a dp). */
    private fun swipe(x: Float, dx: Float, dy: Float = 0f) {
        compose.onNodeWithTag("root").performTouchInput {
            down(Offset(x, 300f))
            val steps = 12
            repeat(steps) { moveBy(Offset(dx / steps, dy / steps)) }
            up()
        }
        compose.waitForIdle()
    }

    @Test fun farEnoughFromTheLeftGoesBack() {
        setUp()
        swipe(4f, 120f)
        assertEquals(1, backs)
        assertEquals(0, taps)
        assertFalse(state.dragging)
    }

    @Test fun farEnoughFromTheRightGoesBack() {
        setUp()
        swipe(396f, -120f)
        assertEquals(1, backs)
    }

    @Test fun notFarEnoughDoesNothing() {
        setUp()
        swipe(4f, 40f)
        assertEquals(0, backs)
    }

    @Test fun inAndBackOutAgainDoesNothing() {
        setUp()
        compose.onNodeWithTag("root").performTouchInput {
            down(Offset(4f, 300f))
            repeat(10) { moveBy(Offset(12f, 0f)) }
            repeat(10) { moveBy(Offset(-11f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(0, backs)
    }

    @Test fun aTapAtTheEdgeStillTaps() {
        setUp()
        compose.onNodeWithTag("root").performTouchInput { down(Offset(4f, 300f)); up() }
        compose.waitForIdle()
        assertEquals(1, taps)
        assertEquals(0, backs)
    }

    @Test fun upAndDownAtTheEdgeIsLeftAlone() {
        setUp()
        swipe(4f, 4f, dy = 200f)
        assertEquals(0, backs)
    }

    @Test fun awayFromTheEdgeIsLeftAlone() {
        setUp()
        swipe(100f, 150f)
        assertEquals(0, backs)
    }

    @Test fun offDoesNothing() {
        setUp(enabled = false)
        swipe(4f, 150f)
        assertEquals(0, backs)
    }
}
