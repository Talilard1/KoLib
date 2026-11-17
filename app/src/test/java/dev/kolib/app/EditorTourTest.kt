package dev.kolib.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.CopiedSections
import dev.kolib.app.library.Library
import dev.kolib.app.ui.textedit.TextEditState
import dev.kolib.app.ui.textedit.TextEditorScreen
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

private const val PHONE = "w411dp-h914dp-port-420dpi"
private const val PHONE_ACROSS = "w914dp-h411dp-land-420dpi"
private const val TABLET = "w1280dp-h800dp-land-xhdpi"
private const val TABLET_UP = "w800dp-h1280dp-port-xhdpi"

/**
 * The text editor's tour: given as the editor opens, it goes through the editor's parts as they're laid out
 * on the screen it's on (a phone or a tablet, upright or on its side), and is done once. Pictures of each
 * stop go to /home/claude/setup/shots, where that folder is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorTourTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private val home = File("/home/claude/setup")
    private val out: File get() = File(home, TestLanguage.code?.let { "shots/lang_$it" } ?: "shots")

    @Before
    fun inLanguage() = TestLanguage.apply()

    private fun editorState(copied: CopiedSections? = null): TextEditState {
        val lib = Library(context, File(context.filesDir, "tourlib-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        return TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch1.xhtml", copied)
    }

    @Composable
    private fun FakeEditorPage() {
        Box(Modifier.fillMaxSize().background(Color(0xFFE7E3DA)), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).padding(top = 20.dp).background(Color(0xFFFFFDF8)).padding(horizontal = 40.dp, vertical = 40.dp)) {
                Text("Chapter 1", style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp))
                listOf(
                    "Rose walked into the garden. She saw a rose by the wall, and Rose smiled.",
                    "“Is that you?” called the man from the Rose Inn. He told her Rose was late.",
                    "The sign said ROSE in red letters.",
                ).forEach { Text(it, style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp), modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }

    /** Goes through the whole tour, taking a picture of each stop (where there's somewhere to put it); gives the stops' titles. */
    private fun tour(name: String, copied: Boolean = false, theme: ThemeChoice = ThemeChoice.SYSTEM): List<String> {
        val store = if (copied) CopiedSections(File(context.filesDir, "copied-" + UUID.randomUUID())) else null
        val state = editorState(store)
        var done = 0
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(theme) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }, tour = true, onTourDone = { done++ }) }
        }
        val titles = ArrayList<String>()
        var n = 1
        while (n <= 14) {
            compose.mainClock.advanceTimeBy(900)
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("tour-next").fetchSemanticsNodes().isEmpty()) break
            compose.onNodeWithTag("tour-card").assertIsDisplayed()
            titles += compose.onAllNodesWithTag("tour-title").fetchSemanticsNodes().last().config[SemanticsProperties.Text].joinToString("") { it.text }
            if (home.exists()) {
                out.mkdirs()
                captureScreenRoboImage(File(out, "editor_${name}_$n.png").path)
            }
            compose.onNodeWithTag("tour-next").performClick()
            n++
        }
        compose.waitForIdle()
        assertEquals("the tour ends once", 1, done)
        assertTrue("the tour is gone", compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
        return titles
    }

    @Test
    @Config(qualifiers = TABLET)
    fun onATabletItShowsTheChaptersBesideThePageAndTheToolsAsButtons() {
        val titles = tour("tablet")
        if (TestLanguage.code == null) {
            assertEquals(listOf("Edit the text", "Chapters", "Style", "Add and split", "Select and copy", "Code", "Undo and redo", "Find, clean up, preview", "Save", "More"), titles)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun onAPhoneItShowsTheChaptersNameAndTheToolsInTheMenu() {
        val titles = tour("phone")
        if (TestLanguage.code == null) {
            assertEquals(listOf("Edit the text", "Chapters", "Style", "Add and split", "Select and copy", "Code", "Undo and redo", "Save", "More"), titles)
            // What's in the menu on a phone is named at its stop.
        }
    }

    @Test
    @Config(qualifiers = PHONE_ACROSS)
    fun onAPhoneOnItsSide() {
        tour("phone_across")
    }

    @Test
    @Config(qualifiers = TABLET_UP)
    fun onATabletUpright() {
        val titles = tour("tablet_up", copied = true)
        // With sections copied out of books to add, they have a stop too.
        if (TestLanguage.code == null) assertTrue("Copied sections" in titles)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-night-xhdpi")
    fun inTheDark() {
        tour("tablet_dark", copied = true, theme = ThemeChoice.DARK)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun skippingEndsIt() {
        // (Looks for the screen's words in English.)
        org.junit.Assume.assumeTrue(TestLanguage.code == null)
        val state = editorState()
        var done = 0
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }, tour = true, onTourDone = { done++ }) } }
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        compose.onNodeWithTag("guide-skip").performClick()
        compose.waitForIdle()
        assertEquals(1, done)
        assertTrue(compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
        // The editor is there to use: on a phone, with the chapter's name in the top bar, which leads to the chapters.
        compose.onNodeWithText("Chapter 1: Rose").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit text").assertIsDisplayed()
        compose.onNodeWithText("Part Two").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun onAPhoneEveryButtonOverThePageIsInViewWithoutScrolling() {
        // (Looks for the screen's words in English.)
        org.junit.Assume.assumeTrue(TestLanguage.code == null)
        val state = editorState()
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        for (name in listOf("Bold", "Italic", "Paragraph", "Alignment", "Scene break", "Picture", "Split the chapter here", "Select and copy", "Code")) {
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
        }
        if (home.exists()) {
            out.mkdirs()
            captureScreenRoboImage(File(out, "editor_phone_toolbar.png").path)
        }
    }

    @Test
    @Config(qualifiers = TABLET_UP)
    fun onATabletUprightEveryButtonOverThePageIsInViewToo() {
        // (Looks for the screen's words in English.)
        org.junit.Assume.assumeTrue(TestLanguage.code == null)
        val state = editorState(CopiedSections(File(context.filesDir, "copied-" + UUID.randomUUID())))
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        for (name in listOf("Bold", "Italic", "Paragraph", "Alignment", "Scene break", "Picture", "Split the chapter here", "Select and copy", "Copied sections", "Code")) {
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
        }
        if (home.exists()) {
            out.mkdirs()
            captureScreenRoboImage(File(out, "editor_tablet_up_toolbar.png").path)
        }
    }

    @Test
    @Config(qualifiers = TABLET)
    fun withoutItTheEditorOpensAsItAlwaysHas() {
        val state = editorState()
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
    }
}
