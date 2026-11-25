package dev.kolib.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings opened over a book whose reader has KoLib's own back swipe: that swipe goes back here too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class SettingsBackTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    private fun open(kolibBack: Boolean) {
        compose.setContent { AppSurface { LookDialog(current = ThemeChoice.SYSTEM, onPick = {}, onDismiss = { closed++ }, kolibBack = kolibBack) } }
        compose.waitForIdle()
    }

    /** In from the left edge, far enough (mdpi: a px is a dp). */
    private fun swipeIn() {
        compose.onNodeWithTag("settings-page").performTouchInput {
            down(Offset(2f, 400f))
            repeat(12) { moveBy(Offset(10f, 0f)) }
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun withKoLibsBackSwipeAnEdgeSwipeGoesBack() {
        open(kolibBack = true)
        swipeIn()
        assertEquals(1, closed)
    }

    @Test
    fun withoutItTheEdgesAreLeftToAndroid() {
        open(kolibBack = false)
        swipeIn()
        assertEquals(0, closed)
    }
}
