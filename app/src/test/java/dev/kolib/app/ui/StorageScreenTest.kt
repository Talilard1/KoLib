package dev.kolib.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.kolib.app.StorageUse
import dev.kolib.app.ui.theme.AppSurface
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StorageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** What KoLib takes up in all, and a line for each kind of thing it keeps. */
    @Test
    fun itShowsWhatKoLibTakesUpAndByWhat() {
        val use = StorageUse(
            listOf(StorageUse.Kind.BOOKS to 3_000_000L, StorageUse.Kind.COMICS to 5_000_000L, StorageUse.Kind.APP to 2_000_000L),
            total = 100_000_000L, free = 40_000_000L,
        )
        compose.setContent { AppSurface { StorageScreen(onBack = {}, measure = { use }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("storage-books").assertExists()
        compose.onNodeWithTag("storage-comics").assertExists()
        compose.onNodeWithTag("storage-app").assertExists()
        compose.onNodeWithTag("storage-kolib").assertExists()
    }

    /**
     * Biggest first, each as a share of what KoLib uses; the device as a whole apart, under its own heading, with what
     * other apps take there, so it isn't taken for KoLib's.
     */
    @Test
    fun biggestFirstAndTheDeviceApart() {
        val use = StorageUse(
            listOf(StorageUse.Kind.BOOKS to 3_000_000L, StorageUse.Kind.COMICS to 5_000_000L, StorageUse.Kind.APP to 2_000_000L),
            total = 100_000_000L, free = 40_000_000L,
        )
        compose.setContent { AppSurface { StorageScreen(onBack = {}, measure = { use }) } }
        compose.waitForIdle()
        fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("comics first", top("storage-comics") < top("storage-books"))
        assertTrue(top("storage-books") < top("storage-app"))
        compose.onNodeWithText("50%", substring = true).assertExists()
        compose.onNodeWithText("30%", substring = true).assertExists()
        compose.onNodeWithText("20%", substring = true).assertExists()
        compose.onNodeWithText("Everything else on this device").assertDoesNotExist()
        assertTrue("the device after KoLib's own", top("storage-device") > top("storage-app"))
        compose.onNodeWithTag("storage-others", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Other apps and files", substring = true).assertExists()
    }

    /** The temporary files are cleared once it's been said what they are, and the figures worked out again. */
    @Test
    fun theTemporaryFilesAreClearedAfterSayingWhatTheyAre() {
        var measured = 0
        var cleared = 0
        val use = StorageUse(
            listOf(StorageUse.Kind.BOOKS to 3_000_000L, StorageUse.Kind.TEMP to 6_000_000L), total = 100_000_000L, free = 40_000_000L,
        )
        compose.setContent {
            AppSurface { StorageScreen(onBack = {}, measure = { measured++; use }, clear = { cleared++; 6_000_000L }) }
        }
        compose.waitForIdle()
        assertEquals(1, measured)
        compose.onNodeWithTag("storage-clear").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Clear temporary files?").assertExists()
        compose.onNodeWithText("None of your books, comics or settings are in them", substring = true).assertExists()
        assertEquals("nothing cleared before it's agreed to", 0, cleared)
        compose.onNodeWithTag("storage-clear-yes").performClick()
        compose.waitForIdle()
        assertEquals(1, cleared)
        assertEquals("worked out again", 2, measured)
        compose.onNodeWithText("Cleared", substring = true).assertExists()
    }

    /** While something's being sent, they can't be cleared. */
    @Test
    fun notWhileSending() {
        val use = StorageUse(listOf(StorageUse.Kind.TEMP to 6_000_000L), total = 100_000_000L, free = 40_000_000L)
        compose.setContent { AppSurface { StorageScreen(onBack = {}, measure = { use }, busy = true, clear = { error("not now") }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("storage-clear").assertIsNotEnabled()
    }

    /** Laid out with a device's figures, for a look (and to check nothing's cut off). */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun onATablet() {
        val use = StorageUse(
            listOf(
                StorageUse.Kind.BOOKS to 2_600_000_000L, StorageUse.Kind.COMICS to 884_000L, StorageUse.Kind.COVERS to 149_000_000L,
                StorageUse.Kind.AI to 3_900_000_000L, StorageUse.Kind.BACKUPS to 16_000_000L, StorageUse.Kind.APP to 60_000_000L,
                StorageUse.Kind.TEMP to 610_000_000L, StorageUse.Kind.OTHER to 180_000_000L,
            ),
            total = 113_000_000_000L, free = 15_000_000_000L,
        )
        compose.setContent { AppSurface(dev.kolib.app.ui.theme.ThemeChoice.BOOKCASE_DARK) { StorageScreen(onBack = {}, measure = { use }) } }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/storage_tablet.png")
    }
}
