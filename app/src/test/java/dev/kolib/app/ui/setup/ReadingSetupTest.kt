package dev.kolib.app.ui.setup

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.L10n
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.reader.PageTurn
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderFlow
import dev.kolib.app.reader.ReaderFont
import dev.kolib.app.reader.ReaderMargins
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ProgressLine
import dev.kolib.app.reader.MarginItem
import dev.kolib.app.reader.MarginSpot
import dev.kolib.app.reader.RunningHeads
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.reader.ReaderSpread
import dev.kolib.app.reader.ReaderTheme
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w411dp-h914dp-port-420dpi"
private const val TABLET = "w1280dp-h800dp-land-xhdpi"

/**
 * The reading setup: its steps change the reader's settings there and then on the example book, what was
 * chosen is given back when it's finished or skipped (not when it's backed out of), and the reader's menu
 * stays out of the way until the step that shows it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingSetupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun english() = L10n.use("en", emptyMap())

    @After
    fun forget() {
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Android's Back, as the screen under test is given it. */
    private var back: androidx.activity.OnBackPressedDispatcher? = null

    private fun controller(settings: ReaderSettings = ReaderSettings.PHONE): ReaderController {
        val files = ExampleBook.files()
        return ReaderController(files, BookStructure(files), ExampleBook.title, "en", null, settings, context.assets)
    }

    /** The setup over [c], from [at]; gives what it's closed with (a list, so "never closed" and "closed with nothing" differ). */
    private fun setup(c: ReaderController, at: SetupStep = SetupStep.COLOUR): MutableList<ReaderSettings?> {
        val closed = ArrayList<ReaderSettings?>()
        // The hints on the page move for as long as they show: time is moved on by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AppSurface(ThemeChoice.LIGHT) { ReadingSetupScreen(c, onClose = { closed += it }, pagePreview = {}, startAt = at) }
        }
        settle()
        c.showForPreview(0, 0, 3)
        settle()
        return closed
    }

    /** What was just done reaches the screen (the test's own clock is stopped: the screen hears of changes when it's idle), and it's given time to move. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        settle()
    }

    @Test
    fun eachStepChangesThePageAndTheLastGivesBackWhatWasChosen() {
        val c = controller()
        val closed = setup(c)
        compose.onNodeWithText("Page colour").assertIsDisplayed()
        tap("theme-NIGHT")
        assertEquals(ReaderTheme.NIGHT, c.settings.theme)
        tap("setup-next")

        compose.onNodeWithText("Font").assertIsDisplayed()
        tap("font-GARAMOND")
        assertEquals(ReaderFont.GARAMOND, c.settings.font)
        tap("setup-next")

        compose.onNodeWithText("Text size").assertIsDisplayed()
        compose.onNodeWithText("110%").assertIsDisplayed()
        tap("setup-next")

        compose.onNodeWithText("Spacing").assertIsDisplayed()
        tap("line-1.8")
        tap("margins-Wide")
        assertEquals(1.8f, c.settings.lineSpacing)
        assertEquals(ReaderMargins.WIDE, c.settings.margins)
        tap("setup-next")

        compose.onNodeWithText("Screen rotation").assertIsDisplayed()
        tap("rotation-Portrait")
        tap("spread-Always one")
        assertEquals(ReaderRotation.PORTRAIT, c.settings.rotation)
        assertEquals(ReaderSpread.ONE, c.settings.spread)
        tap("setup-next")

        compose.onNodeWithText("Turning pages").assertIsDisplayed()
        compose.onNodeWithText("Try it: tap the right side of the page, or swipe.").assertIsDisplayed()
        tap("turn-Fade")
        assertEquals(PageTurn.FADE, c.settings.pageTurn)
        // A page turned forward, and the hint moves on to turning back; then back, and it's done.
        c.showForPreview(0, 1, 3)
        settle()
        compose.onNodeWithText("Now tap the left side to go back.").assertIsDisplayed()
        c.showForPreview(0, 0, 3)
        settle()
        compose.onNodeWithText("Try the other page turns too.").assertIsDisplayed()
        tap("setup-next")

        // What shows round the page: the headings, the time and battery, and the progress bar.
        compose.onNodeWithText("Around the page").assertIsDisplayed()
        tap("heads-Bottom")
        assertEquals(RunningHeads.BOTTOM, c.settings.runningHeads)
        // With the headings at the foot, the time and the battery are in the top corners.
        assertEquals(MarginSpot.TOP_LEFT to MarginSpot.TOP_RIGHT, c.settings.spotOf(MarginItem.CLOCK) to c.settings.spotOf(MarginItem.BATTERY))
        tap("clock-Off")
        assertFalse(c.settings.clockShown)
        tap("heads-Top")
        assertFalse("Still off with the headings moved", c.settings.clockShown)
        tap("clock-On")
        assertEquals(MarginSpot.BOTTOM_CENTER, c.settings.spotOf(MarginItem.CLOCK))
        tap("heads-Bottom")
        tap("progress-Chapter")
        assertEquals(ProgressLine.CHAPTER, c.settings.progressLine)
        tap("setup-next")

        compose.onNodeWithText("The menu").assertIsDisplayed()
        tap("setup-next")
        assertTrue("Show me brings up the reader's controls", c.controlsVisible)
        compose.onNodeWithText("Everything you just chose, and more.").assertIsDisplayed()
        tap("setup-next")

        compose.onNodeWithText("You’re all set").assertIsDisplayed()
        assertFalse("The controls are put away again", c.controlsVisible)
        assertTrue(closed.isEmpty())
        tap("setup-next")
        val chosen = closed.single()!!
        assertEquals(ReaderTheme.NIGHT, chosen.theme)
        assertEquals(ReaderFont.GARAMOND, chosen.font)
        assertEquals(1.8f, chosen.lineSpacing)
        assertEquals(ReaderMargins.WIDE, chosen.margins)
        assertEquals(ReaderRotation.PORTRAIT, chosen.rotation)
        assertEquals(ReaderSpread.ONE, chosen.spread)
        assertEquals(PageTurn.FADE, chosen.pageTurn)
        assertEquals(ReaderSettings.PHONE.aroundCamera().runningHeads to ReaderSettings.PHONE.aroundCamera().marginItems, chosen.runningHeads to chosen.marginItems)
        assertEquals(ProgressLine.CHAPTER, chosen.progressLine)
        // What wasn't asked about is as it was.
        assertEquals(ReaderSettings.PHONE.copy(theme = chosen.theme, font = chosen.font, lineSpacing = chosen.lineSpacing, margins = chosen.margins,
            rotation = chosen.rotation, spread = chosen.spread, pageTurn = chosen.pageTurn, runningHeads = chosen.runningHeads,
            marginItems = chosen.marginItems, progressLine = chosen.progressLine), chosen)
    }

    @Test
    fun theMenuStaysAwayUntilItsStep() {
        val c = controller()
        setup(c)
        assertTrue("A tap in the middle of the page does nothing while choosing", c.menuLocked)
        repeat(SetupStep.MENU.ordinal) { tap("setup-next") }
        compose.onNodeWithText("The menu").assertIsDisplayed()
        assertFalse(c.menuLocked)
        c.controlsVisible = true
        settle()
        // Back a step: the controls go away with it.
        tap("guide-back")
        compose.onNodeWithText("Around the page").assertIsDisplayed()
        assertFalse(c.controlsVisible)
        assertTrue(c.menuLocked)
    }

    @Test
    fun skippingKeepsWhatWasChosenSoFar() {
        val c = controller()
        val closed = setup(c)
        tap("theme-SEPIA")
        tap("setup-next")
        tap("guide-skip")
        assertEquals(ReaderSettings.PHONE.copy(theme = ReaderTheme.SEPIA), closed.single())
    }

    @Test
    fun backingOutAtTheFirstStepChangesNothing() {
        val c = controller()
        val closed = setup(c)
        tap("theme-BLACK")
        // No back button on the card at the first step: Android's Back leaves the setup.
        assertTrue(compose.onAllNodesWithTag("guide-back", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.runOnUiThread { back!!.onBackPressed() }
        settle()
        assertEquals(1, closed.size)
        assertNull(closed.single())
    }

    @Test
    fun scrollingHasNoPageTurnsToChoose() {
        val c = controller(ReaderSettings.PHONE.copy(flow = ReaderFlow.SCROLL))
        setup(c, SetupStep.TURNING)
        compose.onNodeWithText("Try it: scroll the page up.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("turn-Slide", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        tap("flow-Pages")
        assertEquals(ReaderFlow.PAGES, c.settings.flow)
        compose.onNodeWithTag("turn-Slide", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun onATabletItStartsFromTheTabletsSettings() {
        val store = ReaderSettingsStore(context)
        assertEquals(ReaderSettings(), store.settings.value)
        val c = controller(store.settings.value)
        val closed = setup(c, SetupStep.SIZE)
        compose.onNodeWithText("145%").assertIsDisplayed()
        tap("guide-skip")
        assertEquals(ReaderSettings(), closed.single())
    }

    /** A tap as a pen or a finger really makes one: with a little movement between going down and coming up. */
    private fun tapAsAHandDoes(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(16)
            moveBy(Offset(1.5f, 1f))
            advanceEventTime(16)
            moveBy(Offset(0f, 0f))
            advanceEventTime(16)
            up()
        }
        settle()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun itsButtonsTakeTapsThatMoveALittleAsAPensDo() {
        val c = controller(ReaderSettings())
        val closed = setup(c)
        compose.onNodeWithText("Page colour").assertIsDisplayed()
        tapAsAHandDoes("theme-SEPIA")
        assertEquals(ReaderTheme.SEPIA, c.settings.theme)
        tapAsAHandDoes("setup-next")
        compose.onNodeWithText("Font").assertIsDisplayed()
        tapAsAHandDoes("font-GARAMOND")
        assertEquals(ReaderFont.GARAMOND, c.settings.font)
        tapAsAHandDoes("guide-back")
        compose.onNodeWithText("Page colour").assertIsDisplayed()
        tapAsAHandDoes("guide-skip")
        assertEquals(1, closed.size)
        assertEquals(ReaderFont.GARAMOND, closed.single()?.font)
    }
}
