package dev.kolib.app.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicBlend
import dev.kolib.app.comic.ComicMode
import dev.kolib.app.comic.ComicOwn
import dev.kolib.app.comic.ComicPages
import dev.kolib.app.comic.ComicRotation
import dev.kolib.app.comic.ComicSettings
import dev.kolib.app.comic.TestComics
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.reader.PdfPlace
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.ui.theme.AppSurface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The comic reader's bars and settings, as Mihon's: the buttons along the foot (reading mode, rotation, cropping and
 * the settings, none at the top), the page slider with a mark for each page, the settings' three tabs, and the
 * custom filter on the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicReaderUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var s by mutableStateOf(ComicSettings())
    private var own by mutableStateOf(ComicOwn())
    private var menu by mutableStateOf(true)
    private var closes = 0
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var dir: File

    private var appSettingsOpened = 0

    private fun open(settings: ComicSettings = ComicSettings(), pages: Int = 8, shown: Boolean = true) {
        s = settings
        menu = shown
        dir = File(context.cacheDir, "comic-ui-${System.nanoTime()}").apply { mkdirs() }
        val file = TestComics.comic(File(dir, "lantern.cbz"), pages = pages, info = false)
        val book = LibraryBook(
            id = "lantern", meta = BookMetadata(title = "The Lantern Thief"), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = BookSource.FILE, originalName = "lantern.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        ReaderStore.savePdfPlace(dir, PdfPlace(0, pages))
        val comic = OpenComic(book, ComicPages(ComicArchives.open(file, libArchive = null)), dir)
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AppSurface {
                ComicView(
                    comic = comic, s = s, mode = own.mode ?: s.mode, menu = menu, setMenu = { menu = it },
                    onSettings = { change -> s = change(s) }, onClose = { closes++ }, onCover = { _, _ -> }, readingHere = null,
                    own = own, onOwn = { own = it }, onAppSettings = { appSettingsOpened++ },
                )
            }
        }
        settle()
    }

    /** The screen as it's drawn. */
    private fun shot(name: String): android.graphics.Bitmap {
        val path = "build/outputs/roborazzi/comic_ui_$name.png"
        compose.onRoot().captureRoboImage(path)
        return android.graphics.BitmapFactory.decodeFile(path)
    }

    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(80)
            compose.waitForIdle()
        }
    }

    @Test
    fun theButtonsAreAlongTheFootAndNoneAtTheTop() {
        open()
        compose.onNodeWithTag("comic-top-bar").assertExists()
        compose.onAllNodesWithContentDescription("Comic settings").assertCountEquals(0)
        for (tag in listOf("comic-mode-button", "comic-rotation-button", "comic-crop-button", "comic-settings-button")) {
            compose.onNodeWithTag(tag).assertExists()
        }
        // A comic on its own: no comics either side to go to.
        compose.onNodeWithTag("comic-next-comic").assertIsNotEnabled()
        compose.onNodeWithTag("comic-previous-comic").assertIsNotEnabled()
    }

    @Test
    fun theSliderHasAMarkForEveryPageAndMovesThePageAsItsDragged() {
        open(pages = 12)
        val info = compose.onNodeWithTag("comic-slider").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(1f, info.range.start)
        assertEquals(12f, info.range.endInclusive)
        // Between the ends, a step for each page.
        assertEquals(10, info.steps)
        compose.onNodeWithTag("comic-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        settle()
        assertEquals(6, ReaderStore.pdfPlace(dir)!!.page)
    }

    /** A tap on a reading mode is the one used for the series, there and then: no Apply, and no Revert to default. */
    @Test
    fun theReadingModeIsChosenForTheSeriesWithATap() {
        open()
        compose.onNodeWithTag("comic-mode-button").performClick()
        settle()
        compose.onNodeWithTag("comic-mode-sheet").assertExists()
        compose.onAllNodesWithText("Apply").assertCountEquals(0)
        compose.onAllNodesWithText("Revert to default").assertCountEquals(0)
        compose.onNodeWithText("Long strip").performClick()
        settle()
        assertEquals(ComicMode.STRIP, own.mode)
        compose.onAllNodesWithTag("comic-mode-sheet").assertCountEquals(0)
    }

    /** Changed from turning pages to a long strip, and back, it stays on the page reached, not the one it opened on. */
    @Test
    fun changingTheReadingModeKeepsThePageReached() {
        open(pages = 12)
        compose.onNodeWithTag("comic-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        settle()
        assertEquals(6, ReaderStore.pdfPlace(dir)!!.page)
        for (pick in listOf("Long strip", "Paged (right to left)")) {
            compose.onNodeWithTag("comic-mode-button").performClick()
            settle()
            compose.onNodeWithText(pick).performClick()
            settle()
            assertEquals("after $pick", 6, ReaderStore.pdfPlace(dir)!!.page)
        }
    }

    @Test
    fun theRotationIsChosenForTheSeriesWithATap() {
        open()
        compose.onNodeWithTag("comic-rotation-button").performClick()
        settle()
        // No Apply: the choice tapped is the one used, and the sheet goes.
        compose.onAllNodesWithText("Apply").assertCountEquals(0)
        compose.onNodeWithText("Locked landscape").performClick()
        settle()
        assertEquals(ComicRotation.LOCKED_LANDSCAPE, own.rotation)
        compose.onNodeWithTag("comic-rotation-sheet").assertDoesNotExist()
        // Opened again: just the choices, with no Revert to default either.
        compose.onNodeWithTag("comic-rotation-button").performClick()
        settle()
        compose.onAllNodesWithText("Revert to default").assertCountEquals(0)
        compose.onAllNodesWithText("Apply").assertCountEquals(0)
        compose.onNodeWithText("Free").performClick()
        settle()
        assertEquals(ComicRotation.FREE, own.rotation)
    }

    @Test
    fun theCropButtonCropsTheWayOfReadingShowing() {
        open()
        compose.onNodeWithTag("comic-crop-button").performClick()
        settle()
        assertTrue(s.cropPaged)
        assertFalse(s.cropStrip)
    }

    @Test
    fun theSettingsHaveMihonsThreeTabsAndTheFilterTabClearsTheBars() {
        open()
        compose.onNodeWithTag("comic-settings-button").performClick()
        settle()
        compose.onNodeWithTag("comic-settings").assertExists()
        compose.onNodeWithText("For this comic").assertExists()
        compose.onNodeWithText("Scale type").assertExists()
        compose.onNodeWithTag("comic-settings-tab-1").performClick()
        settle()
        compose.onNodeWithText("Always show the page between comics").assertExists()
        compose.onNodeWithTag("comic-settings-tab-2").performClick()
        settle()
        // The page can be seen as it's filtered.
        assertFalse(menu)
        compose.onNodeWithText("Greyscale").performClick()
        settle()
        assertTrue(s.greyscale)
        compose.onNodeWithText("Custom colour filter").performClick()
        settle()
        assertTrue(s.colourFilter)
        compose.onNodeWithTag("comic-filter-opacity").assertExists()
    }

    @Test
    fun theFilterChangesThePage() {
        // Red panels on white paper: greyscale makes the red grey.
        open(ComicSettings(greyscale = true), shown = false)
        val grey = shot("filter_grey")
        val x = grey.width / 2
        val y = grey.height / 2 - grey.height / 4
        val c = grey.getPixel(x, y)
        assertTrue("grey, not red", kotlin.math.abs(android.graphics.Color.red(c) - android.graphics.Color.green(c)) < 12)
    }

    @Test
    fun theColourFilterMultipliesWithThePage() {
        // White paper multiplied by full green is green; ink stays black.
        open(ComicSettings(colourFilter = true, colour = 0xFF00FF00.toInt(), blend = ComicBlend.MULTIPLY), shown = false)
        val shot = shot("filter_multiply")
        // The paper's margin, near the page's top left corner.
        var green = 0
        for (yy in 0 until shot.height step 7) for (xx in 0 until shot.width step 7) {
            val c = shot.getPixel(xx, yy)
            if (android.graphics.Color.green(c) > 200 && android.graphics.Color.red(c) < 60 && android.graphics.Color.blue(c) < 60) green++
        }
        assertTrue("paper turned green ($green)", green > 50)
    }

    @Test
    fun backClosesTheComicEvenWithTheBarsShowing() {
        open()
        assertTrue(menu)
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun theAppsOwnSettingsAreAtTheTopOfTheSettings() {
        open()
        compose.onNodeWithTag("comic-settings-button").performClick()
        settle()
        compose.onNodeWithTag("comic-app-settings").performClick()
        settle()
        assertEquals(1, appSettingsOpened)
    }
}
