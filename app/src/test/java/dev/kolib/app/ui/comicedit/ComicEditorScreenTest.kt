package dev.kolib.app.ui.comicedit

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
        compose.onNodeWithTag("comic-edit-starts").assertExists()
        compose.onNodeWithTag("comic-edit-save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-split-go").performClick()
        compose.waitForIdle()
        assertEquals(listOf(3, 2), saved!!.parts.map { it.size })
        assertTrue(saved!!.keepOriginal)
    }
}
