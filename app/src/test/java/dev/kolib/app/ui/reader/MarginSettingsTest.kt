package dev.kolib.app.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kolib.app.reader.MarginItem
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.RunningHeads
import dev.kolib.app.ui.theme.AppSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reading settings' margins (what the page headings show is theirs while they're on, and each item's style), the
 * omnibus title's choice, and full screen and the camera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2000dp-mdpi")
class MarginSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(ReaderSettings())
    private var camera by mutableStateOf(false)

    private fun show() {
        compose.setContent {
            AppSurface { Column(Modifier.verticalScroll(rememberScrollState())) { LayoutSettings(settings, { settings = it }) } }
        }
        compose.waitForIdle()
    }

    private fun showPage() {
        compose.setContent {
            AppSurface { Column(Modifier.verticalScroll(rememberScrollState())) { PageSettings(settings, { settings = it }, camera) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun withTheHeadingsOnTheirTitlesAndPageNumbersCantGoInTheMargins() {
        show()
        // The book's and chapter's titles and the page numbers.
        compose.onAllNodesWithText("In the headings").assertCountEquals(4)
        compose.onAllNodesWithText("In the headings")[0].assertIsNotEnabled()
        // The rest can.
        compose.onNodeWithText("Pages left in chapter").performScrollTo()
        compose.onAllNodesWithText("Off")[0].assertIsEnabled()
        // Headings off: all of them can.
        settings = settings.withHeads(RunningHeads.OFF)
        compose.waitForIdle()
        compose.onAllNodesWithText("In the headings").assertCountEquals(0)
    }

    @Test
    fun theTimeAndBatteryStartPlainAndEachItemCanHaveEitherStyle() {
        show()
        assertEquals(setOf(MarginItem.CLOCK, MarginItem.BATTERY), settings.plainMargins)
        val clockButton = hasAnyAncestor(hasTestTag("margin-CLOCK")) and hasClickAction()
        compose.onNode(hasAnyAncestor(hasTestTag("margin-CLOCK")) and hasText("plain", substring = true)).performScrollTo().assertExists()
        // In the headings' style.
        compose.onNode(clockButton).performScrollTo().performClick()
        compose.onNodeWithText("Like the page headings").performClick()
        compose.waitForIdle()
        assertEquals(setOf(MarginItem.BATTERY), settings.plainMargins)
        // And plain again.
        compose.onNode(clockButton).performClick()
        compose.onNodeWithText("Plain").performClick()
        compose.waitForIdle()
        assertEquals(setOf(MarginItem.CLOCK, MarginItem.BATTERY), settings.plainMargins)
    }

    @Test
    fun theOmnibusTitleShowsOnlyWhenTheBookTitleDoes() {
        show()
        // The headings show the book's title over left-hand pages.
        compose.onAllNodesWithText("Omnibus title").assertCountEquals(1)
        // Off, with the book's title nowhere else: nothing to choose.
        settings = settings.withHeads(RunningHeads.OFF)
        compose.waitForIdle()
        compose.onAllNodesWithText("Omnibus title").assertCountEquals(0)
        // The book's title in a margin: there's a choice again.
        settings = settings.copy(marginItems = settings.marginItems + (MarginItem.BOOK to dev.kolib.app.reader.MarginSpot.TOP_CENTER))
        compose.waitForIdle()
        compose.onAllNodesWithText("Omnibus title").assertCountEquals(1)
    }

    @Test
    fun fullScreenAndWhereThePageGoesBesideTheCamera() {
        showPage()
        // Full screen: a switch, there on any screen.
        compose.onNodeWithText("Full screen").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(settings.fullscreen)
        compose.onNodeWithText("Full screen").performClick()
        compose.waitForIdle()
        assertTrue(settings.fullscreen)
        // The camera's choice: only where there's a camera in the screen.
        compose.onAllNodesWithText("The page and the camera").assertCountEquals(0)
        camera = true
        compose.waitForIdle()
        compose.onNodeWithText("Below it").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(dev.kolib.app.reader.CameraRoom.BELOW, settings.cameraRoom)
        assertFalse(settings.roundCamera)
        compose.onNodeWithText("Under it").performClick()
        compose.waitForIdle()
        assertTrue(settings.roundCamera)
        assertFalse(settings.clearOfCamera)
        compose.onNodeWithText("Round it").performClick()
        compose.waitForIdle()
        assertTrue(settings.roundCamera)
        assertTrue(settings.clearOfCamera)
        // Not full screen, the status bar's over the camera: nothing to choose.
        settings = settings.copy(fullscreen = false)
        compose.waitForIdle()
        compose.onAllNodesWithText("The page and the camera").assertCountEquals(0)
    }
}
