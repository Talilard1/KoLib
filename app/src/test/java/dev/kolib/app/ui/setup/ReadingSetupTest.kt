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
import dev.kolib.app.reader.TapZones
import dev.kolib.app.reader.LevelSide
import dev.kolib.app.reader.LevelStart
import androidx.compose.ui.test.onRoot
import dev.kolib.app.reader.TapWidth
import androidx.compose.ui.test.performScrollTo
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

        // How the reader is worked, a small card at a time, each tried on the page: where taps go (shown on the page
        // for a moment), how far in they reach, the volume keys, the swipes in from the sides and the brightness swipe.
        compose.onNodeWithText("Tapping the page").assertIsDisplayed()
        compose.onNodeWithTag("setup-tap-zones").assertExists()
        tap("taps-Both sides go forward")
        assertEquals(TapZones.FORWARD, c.settings.taps)
        tap("setup-next")

        compose.onNodeWithText("Tap zone size").assertIsDisplayed()
        tap("tapzone-Large")
        assertEquals(TapWidth.WIDE, c.settings.tapWidth)
        tap("setup-next")

        compose.onNodeWithText("Volume keys and screen").assertIsDisplayed()
        val volume = c.settings.volumeKeys
        tap("setup-volume-keys")
        assertEquals(!volume, c.settings.volumeKeys)
        tap("setup-next")

        // One switch: swipes in from the sides pull out the panels (going back with one open), or always go back.
        compose.onNodeWithText("Edge swipes").assertIsDisplayed()
        compose.onNodeWithText("Try it: swipe in from an edge for a panel, then in from an edge again to close it.").assertIsDisplayed()
        tap("setup-edge-back")
        assertTrue(c.settings.edgeBack)
        assertFalse(c.settings.edgePanels)
        compose.onNodeWithText("Try it: swipe in from either edge.").assertIsDisplayed()
        tap("setup-edge-back")
        assertFalse(c.settings.edgeBack)
        tap("setup-next")

        compose.onNodeWithText("Brightness swipe").assertIsDisplayed()
        compose.onNodeWithTag("setup-level-zones").assertExists()
        tap("levelside-Both")
        tap("levelstart-Bottom")
        assertEquals(LevelSide.BOTH to LevelStart.BOTTOM, c.settings.levelSide to c.settings.levelStart)
        tap("setup-next")

        // What shows round the page: the headings, the time and battery, and the progress bar.
        compose.onNodeWithText("Around the page").assertIsDisplayed()
        // A chapter's first page has no heading over it, so this step isn't shown on one: it turns to the next page.
        // (The page here is a stand-in that can't turn.) A book with a single page is given the headings on that page.
        assertFalse(c.headsOnOpenings)
        assertTrue(c.shownPages.single().opening)
        c.showForPreview(0, 0, 1)
        settle()
        assertTrue(c.headsOnOpenings)
        assertFalse(c.shownPages.single().opening)
        c.showForPreview(0, 0, 3)
        settle()
        assertFalse(c.headsOnOpenings)
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

        // How settings are kept: a book's own, and portrait's and landscape's.
        compose.onNodeWithText("Each book's own settings").assertIsDisplayed()
        assertFalse("The controls are put away again", c.controlsVisible)
        compose.onNodeWithText("Tap Set as default in Reading settings to use them for every book.").assertIsDisplayed()
        tap("setup-next")

        compose.onNodeWithText("You’re all set").assertIsDisplayed()
        compose.onNodeWithText("Swipe up or down either edge to change the brightness.").assertIsDisplayed()
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
        assertEquals(TapZones.FORWARD to TapWidth.WIDE, chosen.taps to chosen.tapWidth)
        assertEquals(LevelSide.BOTH to LevelStart.BOTTOM, chosen.levelSide to chosen.levelStart)
        // What wasn't asked about is as it was.
        assertEquals(ReaderSettings.PHONE.copy(theme = chosen.theme, font = chosen.font, lineSpacing = chosen.lineSpacing, margins = chosen.margins,
            rotation = chosen.rotation, spread = chosen.spread, pageTurn = chosen.pageTurn, runningHeads = chosen.runningHeads,
            marginItems = chosen.marginItems, progressLine = chosen.progressLine, taps = chosen.taps, tapWidth = chosen.tapWidth,
            volumeKeys = chosen.volumeKeys, levelSide = chosen.levelSide, levelStart = chosen.levelStart), chosen)
    }

    /** With taps turning no pages, how far in they reach isn't asked (there or back). */
    @Test
    fun withTapsOffHowFarTheyReachIsntAsked() {
        val c = controller()
        setup(c, SetupStep.TAPS)
        tap("taps-Off")
        assertEquals(TapZones.OFF, c.settings.taps)
        tap("setup-next")
        compose.onNodeWithText("Volume keys and screen").assertIsDisplayed()
        tap("guide-back")
        compose.onNodeWithText("Tapping the page").assertIsDisplayed()
    }

    /** With the top and the foot turning pages, the card moves to the middle, clear of both. */
    @Test
    fun withTheTopAndFootTurningPagesTheCardMovesToTheMiddle() {
        val c = controller()
        setup(c, SetupStep.TAPS)
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        tap("taps-Top goes back, bottom goes forward")
        settle()
        val card = compose.onNodeWithTag("setup-card").fetchSemanticsNode().boundsInRoot
        val zone = screen.height * c.settings.tapWidth.fraction
        assertTrue("clear of the top: $card", card.top > zone)
        assertTrue("clear of the foot: $card", card.bottom < screen.height - zone)
    }

    /** Scrolling, the setup shows the pull on to the next chapter (played over and over), with the hill to keep or not. */
    @Test
    fun scrollingTheSetupShowsTheChapterPullWithTheHillToKeepOrNot() {
        assertFalse(SetupStep.PULL in setupSteps(ReaderSettings.PHONE))
        val scrolling = ReaderSettings.PHONE.withFlow(dev.kolib.app.reader.ReaderFlow.SCROLL)
        assertTrue(SetupStep.PULL in setupSteps(scrolling))
        assertEquals(SetupStep.TURNING, setupSteps(scrolling).let { it[it.indexOf(SetupStep.PULL) - 1] })
        val c = controller(scrolling)
        setup(c, SetupStep.PULL)
        compose.onNodeWithText("Next chapter").assertIsDisplayed()
        compose.onNodeWithTag("setup-pull-demo").assertExists()
        assertTrue(c.settings.pullHill)
        tap("setup-pull-hill")
        assertFalse(c.settings.pullHill)
        // The demo: pulled to full, the hill taking the ring in (springing a little past), then let go and gone.
        assertEquals(0f, pullDemoAt(0f).first, 0.001f)
        assertEquals(1f to 0f, pullDemoAt(0.42f).let { it.first to it.second })
        assertTrue(pullDemoAt(0.6f).second > 0.9f)
        assertEquals(0f, pullDemoAt(0.9f).third, 0.001f)
    }

    /** KoLib's back swipe, tried on its step, says it worked: it doesn't go back a step, or out of the setup. */
    @Test
    fun kolibsBackSwipeTriedOnItsStepSaysItWorked() {
        val c = controller(ReaderSettings.PHONE.copy(edgeBack = true))
        val closed = setup(c, SetupStep.SWIPES)
        compose.onNodeWithText("Try it: swipe in from either edge.").assertIsDisplayed()
        compose.onRoot().performTouchInput {
            down(Offset(2f, height * 0.3f))
            repeat(16) { moveBy(Offset(width * 0.4f / 16, 0f), 16) }
            up()
        }
        settle()
        compose.onNodeWithText("That's it. In a book, it goes back, just like Back.").assertIsDisplayed()
        compose.onNodeWithText("Edge swipes").assertIsDisplayed()
        assertTrue(closed.isEmpty())
    }

    /** The brightness swipe's card keeps clear of where it's tried: off the sides it's on, and away from its start. */
    @Test
    fun theBrightnessCardKeepsClearOfTheSwipe() {
        val c = controller()
        setup(c, SetupStep.LEVEL)
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        fun card() = compose.onNodeWithTag("setup-card").fetchSemanticsNode().boundsInRoot
        val strip = screen.width * ReaderSettings.LEVEL_STRIP
        assertTrue("clear of the left strip: ${card()}", card().left > strip)
        tap("levelside-Right")
        settle()
        assertTrue("clear of the right strip: ${card()}", card().right < screen.width - strip)
        tap("levelside-Both")
        settle()
        assertTrue(card().left > strip && card().right < screen.width - strip)
        assertTrue("at the foot to start with", card().top > screen.height / 3)
        // Tried from the foot: the card goes to the top, clear of the bottom third.
        tap("levelstart-Bottom")
        settle()
        assertTrue("at the top: ${card()}", card().bottom <= screen.height * 2 / 3)
        // Off: nothing marked, and the card goes back to the foot.
        tap("levelside-Off")
        settle()
        compose.onNodeWithTag("setup-level-zones").assertDoesNotExist()
        assertTrue(card().bottom > screen.height * 2 / 3)
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
