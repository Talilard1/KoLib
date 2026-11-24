package dev.kolib.app.ui.textedit

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.tr
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChaptersPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun open(book: (File) -> File): TextEditState {
        val lib = Library(context, File(context.filesDir, "cp-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = book(File(context.cacheDir, "b-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("book.epub", { f.inputStream() }, BookSource.FILE) }
        val b = lib.books.value.first()
        return TextEditState(b.id, b.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, null)
    }

    private val actions = mutableListOf<ChapterAction>()

    private fun show(state: TextEditState, height: Int = 600) {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                Box(Modifier.size(320.dp, height.dp)) {
                    ChaptersPane(state, onOpen = {}, onAction = { actions += it }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun titles(state: TextEditState) = state.structure.chapters.map { it.title }

    /** Waits for the book's change to land and the list to be redrawn. */
    private fun settle(state: TextEditState) {
        compose.waitForIdle()
        state.awaitWork()
        compose.waitForIdle()
    }

    /** Drags the chapter titled [title] by its handle [by] pixels, a little at a time, holds it there [holdMs], and lets go. */
    private fun drag(title: String, by: Float, steps: Int = 24, holdMs: Long = 0) {
        // The handle itself, not the row it's part of.
        val row = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val handles = compose.onAllNodesWithContentDescription(tr("Drag to reorder"), useUnmergedTree = true)
        val i = handles.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.center.y in row.top - 20f..row.bottom + 20f }
        check(i >= 0) { "no handle beside $title" }
        handles[i].performTouchInput {
            down(center)
            repeat(steps) { moveBy(Offset(0f, by / steps)) }
            if (holdMs > 0) {
                // Held still past the edge while the list runs.
                var left = holdMs
                while (left > 0) {
                    advanceEventTime(50)
                    moveBy(Offset(0f, if (by > 0) 0.5f else -0.5f))
                    left -= 50
                }
            }
            up()
        }
    }

    @Test
    fun chosenChaptersMoveTogether() {
        val order = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(listOf("b", "a", "d", "c", "e", "f"), ChapterMoves.step(order, setOf("b", "d"), -1))
        assertEquals(listOf("a", "c", "b", "d", "f", "e"), ChapterMoves.step(order, setOf("b", "e"), 1))
        // A block moves as one; one already at the end stays.
        assertEquals(listOf("b", "c", "a", "d", "e", "f"), ChapterMoves.step(order, setOf("b", "c"), -1))
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), ChapterMoves.step(order, setOf("a", "b"), -1))
        assertEquals(listOf("a", "b", "c", "e", "d", "f"), ChapterMoves.step(order, setOf("d", "f"), 1))
        // Put somewhere together, in the order they were in.
        assertEquals(listOf("b", "e", "a", "c", "d", "f"), ChapterMoves.after(order, setOf("e", "b"), null))
        assertEquals(listOf("a", "c", "d", "b", "e", "f"), ChapterMoves.after(order, setOf("b"), "d"))
        assertEquals(listOf("c", "d", "f", "a", "b", "e"), ChapterMoves.after(order, setOf("a", "b", "e"), "f"))
    }

    @Test
    fun aSecondDragMovesOnFromWhereTheFirstLeftThings() {
        val state = open { TestBooks.garden(it) }
        show(state)
        drag("Notes", by = -600f)
        settle(state)
        assertEquals(listOf("Notes", "Part One", "Chapter 1: Rose", "Chapter 2", "Part Two", "Chapter 3"), titles(state))
        // The row being dragged now was on screen before the first move.
        drag("Chapter 3", by = -600f)
        settle(state)
        assertEquals(listOf("Chapter 3", "Notes", "Part One", "Chapter 1: Rose", "Chapter 2", "Part Two"), titles(state))
    }

    @Test
    fun chaptersStillMoveAfterOneIsDeleted() {
        val state = open { TestBooks.garden(it) }
        show(state)
        state.delete("OEBPS/text/ch2.xhtml")
        settle(state)
        drag("Notes", by = -600f)
        settle(state)
        assertEquals(listOf("Notes", "Part One", "Chapter 1: Rose", "Part Two", "Chapter 3"), titles(state))
    }

    @Test
    fun draggedPastTheBottomTheListRunsAndKeepsHoldOfIt() {
        val state = open { TestBooks.long(it, 40) }
        show(state, height = 400)
        drag("Chapter 2", by = 520f, holdMs = 2_000)
        settle(state)
        val at = titles(state).indexOf("Chapter 2")
        assertTrue("Chapter 2 went down a long way (to ${at + 1})", at > 15)
        // Only it moved: the rest are in their order.
        assertEquals((1..40).map { "Chapter $it" }.minus("Chapter 2"), titles(state).minus("Chapter 2"))
    }

    @Test
    fun draggedPastTheTopTheListRunsBack() {
        val state = open { TestBooks.long(it, 40) }
        show(state, height = 400)
        compose.onNode(hasScrollAction()).performScrollToIndex(39)
        compose.waitForIdle()
        drag("Chapter 38", by = -520f, holdMs = 2_000)
        settle(state)
        val at = titles(state).indexOf("Chapter 38")
        assertTrue("Chapter 38 went up a long way (to ${at + 1})", at < 22)
        assertEquals((1..40).map { "Chapter $it" }.minus("Chapter 38"), titles(state).minus("Chapter 38"))
    }

    @Test
    fun aChapterHeldIsChosenBeforeItsLetGoAndOnlyItsHandleMovesIt() {
        val state = open { TestBooks.garden(it) }
        show(state)
        val before = titles(state)
        // Held, and not yet let go: chosen already.
        compose.onNodeWithText("Chapter 1: Rose").performTouchInput {
            down(center)
            advanceEventTime(900)
            moveBy(Offset(0f, 1f))
        }
        compose.waitForIdle()
        compose.onNodeWithText(tr("1 chapter selected")).assertExists()
        compose.onNodeWithText("Chapter 1: Rose").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText(tr("1 chapter selected")).assertExists()
        compose.onNodeWithContentDescription(tr("Stop selecting")).performClick()
        compose.waitForIdle()
        // Dragged by the row (not its handle), held first or not, nothing moves.
        compose.onNodeWithText("Chapter 2").performTouchInput {
            down(center)
            advanceEventTime(900)
            repeat(20) { moveBy(Offset(0f, -12f)) }
            up()
        }
        compose.onNodeWithText("Chapter 2").performTouchInput { swipeUp() }
        settle(state)
        assertEquals(before, titles(state))
    }

    /** Held on its handle or on its menu button, a chapter isn't chosen: those are for moving it and for its menu. */
    @Test
    fun aHoldOnTheHandleOrTheMenuButtonDoesntChooseIt() {
        val state = open { TestBooks.garden(it) }
        show(state)
        val before = titles(state)
        val handle = compose.onAllNodesWithContentDescription(tr("Drag to reorder"), useUnmergedTree = true)[1]
        handle.performTouchInput {
            down(center)
            advanceEventTime(900)
            moveBy(Offset(0f, 1f))
        }
        compose.waitForIdle()
        compose.onNodeWithText(tr("1 chapter selected")).assertDoesNotExist()
        handle.performTouchInput { up() }
        settle(state)
        compose.onNodeWithText(tr("1 chapter selected")).assertDoesNotExist()
        assertEquals(before, titles(state))

        val menu = compose.onNodeWithContentDescription(tr("Options for “{0}”", "Chapter 2"), useUnmergedTree = true)
        menu.performTouchInput {
            down(center)
            advanceEventTime(900)
            moveBy(Offset(0f, 1f))
        }
        compose.waitForIdle()
        compose.onNodeWithText(tr("1 chapter selected")).assertDoesNotExist()
        menu.performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText(tr("1 chapter selected")).assertDoesNotExist()
    }

    @Test
    fun holdingChoosesAndChosenChaptersMoveOrGoTogether() {
        val state = open { TestBooks.garden(it) }
        show(state)
        compose.onNodeWithText("Chapter 1: Rose").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(tr("1 chapter selected")).assertExists()
        // Holding another chooses everything between.
        compose.onNodeWithText("Part Two").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(tr("{0} chapters selected", 3)).assertExists()
        // A tap lets one go, and takes it again.
        compose.onNodeWithText("Chapter 2").performClick()
        compose.onNodeWithText(tr("{0} chapters selected", 2)).assertExists()
        compose.onNodeWithText("Chapter 2").performClick()
        compose.onNodeWithText(tr("{0} chapters selected", 3)).assertExists()

        compose.onNodeWithText(tr("Move to…")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tr("The start of the book")).performClick()
        settle(state)
        assertEquals(listOf("Chapter 1: Rose", "Chapter 2", "Part Two", "Part One", "Chapter 3", "Notes"), titles(state))
        assertEquals(tr("Moved {0} chapters", 3), state.edit.undoLabel)

        // Still chosen, they go down a place together.
        compose.onNodeWithContentDescription(tr("Move down")).performClick()
        settle(state)
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Chapter 2", "Part Two", "Chapter 3", "Notes"), titles(state))

        compose.onNodeWithText(tr("Delete…")).performClick()
        val delete = actions.last() as ChapterAction.DeleteMany
        assertEquals(listOf("Chapter 1: Rose", "Chapter 2", "Part Two"), delete.chapters.map { it.title })
        state.deleteAll(delete.chapters.map { it.path })
        settle(state)
        assertEquals(listOf("Part One", "Chapter 3", "Notes"), titles(state))
        // With what was chosen gone, choosing ends.
        compose.onNodeWithText(tr("Chapters")).assertExists()
    }

    @Test
    fun makeACoverPagePutsOneAtTheFrontAndOpensIt() {
        val state = open(TestBooks::garden)
        show(state)
        val before = titles(state)
        compose.onNodeWithContentDescription(tr("Table of contents options")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tr("Make a cover page")).performClick()
        settle(state)
        assertEquals("message: ${state.message}", before.size + 1, titles(state).size)
        assertEquals(state.structure.chapters.first().path, state.coverPath)
        // It says so, since on a phone the page itself isn't in view from the list of chapters.
        assertEquals(tr("Cover page added at the front."), state.message)
        // It's the page that's open, and the book has changed.
        assertEquals(state.coverPath, state.currentPath)
        assertTrue(state.dirty)
        // And it's in view at the top of the list, not added out of sight above it.
        compose.onNodeWithText("Cover", useUnmergedTree = true).assertIsDisplayed()
        // There's one now, so another can't be made.
        compose.onNodeWithContentDescription(tr("Table of contents options")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tr("Make a cover page")).assertIsNotEnabled()
    }

    @Test
    fun inALongBookTheNewCoverPageIsShownNotAddedOutOfSightAboveTheList() {
        val state = open { TestBooks.long(it, 40) }
        show(state)
        compose.onNodeWithContentDescription(tr("Table of contents options")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tr("Make a cover page")).performClick()
        settle(state)
        assertEquals(41, titles(state).size)
        compose.onNodeWithText("Cover", useUnmergedTree = true).assertIsDisplayed()
    }
}
