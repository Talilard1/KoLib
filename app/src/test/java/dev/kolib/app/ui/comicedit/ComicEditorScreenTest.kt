package dev.kolib.app.ui.comicedit

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTouchInput
import dev.kolib.app.comic.TestComics
import dev.kolib.app.ui.theme.AppSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** The comic editor: pages chosen and changed, and what's saved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dir: File = Files.createTempDirectory("comic-editor").toFile()
    private var saved: ComicEdit? = null

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun open(pages: Int = 5) {
        val files = (0 until pages).map { i -> File(dir, "p$i.jpg").apply { writeBytes(TestComics.page(i)) } }
        compose.setContent {
            AppSurface { ComicEditorScreen("The Lantern Thief", open = { ComicPictures.of(files) }, onSave = { _, e -> saved = e }, onClose = {}) }
        }
        compose.waitForIdle()
    }

    /** An action in the bar along the foot, or under its More. */
    private fun bar(tag: String) {
        if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("comic-edit-more").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test
    fun aPageChosenIsTakenOutAndTheRestSaved() {
        open(5)
        compose.onAllNodesWithTag("comic-edit-page")[1].performClick()
        compose.waitForIdle()
        bar("comic-edit-delete")
        compose.onAllNodesWithTag("comic-edit-page").assertCountEquals(4)
        compose.onNodeWithTag("comic-edit-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf(4), saved!!.parts.map { it.size })
    }

    @Test
    fun theComicIsSplitWhereANewOneStarts() {
        open(5)
        compose.onAllNodesWithTag("comic-edit-page")[3].performClick()
        compose.waitForIdle()
        bar("comic-edit-new-comic")
        compose.onNodeWithTag("comic-edit-starts", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("comic-edit-save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-split-go").performClick()
        compose.waitForIdle()
        assertEquals(listOf(3, 2), saved!!.parts.map { it.size })
        assertTrue(saved!!.keepOriginal)
    }

    private fun order(edit: ComicEdit) = edit.parts.flatten().map { (it.source as dev.kolib.app.comic.PageSource.Picture).file.name }

    @Test
    fun aPagesOwnMenuDeletesItOrShowsItOnItsOwn() {
        open(5)
        compose.onAllNodesWithTag("comic-page-menu")[2].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-page-delete").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("comic-edit-page").assertCountEquals(4)
        compose.onAllNodesWithTag("comic-page-menu")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-page-view").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-viewer").assertExists()
        compose.onNodeWithTag("comic-viewer-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-viewer").assertDoesNotExist()
        compose.onNodeWithTag("comic-edit-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("p0.jpg", "p1.jpg", "p3.jpg", "p4.jpg"), order(saved!!))
    }

    @Test
    fun aPageHeldAndDraggedGoesWhereItsPutDownAndTheMoveIsUndoneAsOne() {
        open(6)
        val grid = compose.onNodeWithTag("comic-edit-pages")
        val origin = grid.fetchSemanticsNode().boundsInRoot.topLeft
        val pages = compose.onAllNodesWithTag("comic-edit-page")
        val from = pages[0].fetchSemanticsNode().boundsInRoot.center - origin
        val to = pages[4].fetchSemanticsNode().boundsInRoot.center - origin
        grid.performTouchInput {
            down(from)
            // Held: picked up.
            advanceEventTime(900)
            moveTo(from + androidx.compose.ui.geometry.Offset(2f, 2f))
            for (k in 1..12) moveTo(from + (to - from) * (k / 12f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("comic-edit-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("p1.jpg", "p2.jpg", "p3.jpg", "p4.jpg", "p0.jpg", "p5.jpg"), order(saved!!))
        compose.onNodeWithTag("comic-edit-undo").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-edit-undo").assertIsNotEnabled()
    }

    @Test
    fun aSwipeThatIsntHeldScrollsThePages() {
        open(40)
        val grid = compose.onNodeWithTag("comic-edit-pages")
        val first = compose.onAllNodesWithTag("comic-edit-page")[0].fetchSemanticsNode().boundsInRoot.center - grid.fetchSemanticsNode().boundsInRoot.topLeft
        val before = compose.onAllNodesWithTag("comic-edit-page")[0].fetchSemanticsNode().boundsInRoot.top
        grid.performTouchInput { swipeUp(startY = first.y + 600f, endY = first.y, durationMillis = 200) }
        compose.waitForIdle()
        // Scrolled, not moved: nothing to undo, and the first pages have gone up.
        compose.onNodeWithTag("comic-edit-undo").assertIsNotEnabled()
        val firstNow = compose.onAllNodesWithText("1").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
        assertTrue("scrolled: $before -> $firstNow", firstNow == null || firstNow < before - 100f)
    }
}
