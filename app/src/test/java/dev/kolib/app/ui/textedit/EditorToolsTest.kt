package dev.kolib.app.ui.textedit

import android.content.Context
import android.os.Looper
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.TestLanguage
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.ui.theme.AppSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/**
 * The text editor's tools beyond bold and italic: more text styles, lists and quotes, links, tables, footnotes, special
 * characters and the word count; what each tells the page to do. (The page's own side is checked in Chromium, by
 * tools/editor-tests.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: TextEditState
    private lateinit var view: WebView

    @Before
    fun setUp() {
        TestLanguage.apply()
        // (Looks for the screen's words in English.)
        assumeTrue(TestLanguage.code == null)
        val lib = Library(context, File(context.filesDir, "toolslib-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        state = TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch1.xhtml", null)
        view = state.createView(context, dark = false)
        compose.setContent { AppSurface { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { Box(Modifier.fillMaxSize()) }) } }
        compose.waitForIdle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** The page says where the cursor is, as it does when it moves. */
    private fun page(json: String) {
        val bridge = shadowOf(view).getJavascriptInterface("KLE")!!
        bridge.javaClass.getMethod("state", String::class.java).invoke(bridge, json)
        idle()
    }

    private fun sent(): String = shadowOf(view).lastEvaluatedJavascript.orEmpty()

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        idle()
    }

    @Test
    fun moreTextStylesAreUnderAa() {
        tap("editor-styles")
        tap("editor-style-strike")
        assertEquals("KLED.cmd(\"strike\", null)", sent())
        tap("editor-styles")
        tap("editor-style-clear")
        assertEquals("KLED.cmd(\"clear\", null)", sent())
    }

    @Test
    fun listsAndQuotesAreWithTheParagraphStyles() {
        compose.onNodeWithContentDescription("Paragraph").performClick()
        idle()
        tap("editor-list-ol")
        assertEquals("KLED.cmd(\"list\", \"ol\")", sent())
        // In a list, the button says so, and the list's levels are there too.
        page("""{"block":"li","list":"ol","indent":true}""")
        compose.onNodeWithContentDescription("Numbered list").performClick()
        idle()
        tap("editor-indent")
        assertEquals("KLED.cmd(\"indent\", null)", sent())
    }

    @Test
    fun aTableIsAddedWithTheRowsAndColumnsChosen() {
        tap("editor-insert")
        tap("editor-table")
        tap("table-rows-more")
        tap("table-cols-less")
        tap("table-add")
        assertTrue(sent(), sent().startsWith("KLED.cmd(\"table\", "))
        assertTrue(sent(), sent().contains("\\\"rows\\\":4") && sent().contains("\\\"cols\\\":2") && sent().contains("\\\"header\\\":true"))
    }

    @Test
    fun inATableItsRowsAndColumnsAreInTheInsertMenu() {
        page("""{"block":"td","table":true}""")
        tap("editor-insert")
        assertTrue(compose.onAllNodesWithTag("editor-table").fetchSemanticsNodes().isEmpty())
        tap("editor-table-rowBelow")
        assertEquals("KLED.cmd(\"tableOp\", \"rowBelow\")", sent())
        tap("editor-insert")
        tap("editor-table-delTable")
        assertEquals("KLED.cmd(\"tableOp\", \"delTable\")", sent())
    }

    // (A window with a box to type in never settles under Robolectric, so the link's and the footnote's windows aren't
    // opened here: what they give the page is.)

    @Test
    fun theInsertMenuHasLinksAndFootnotes() {
        tap("editor-insert")
        compose.onNodeWithText("Link…").assertExists()
        compose.onNodeWithText("Footnote…").assertExists()
        compose.onNodeWithText("Table…").assertExists()
        compose.onNodeWithText("Picture…").assertExists()
        compose.onNodeWithText("Scene break").assertExists()
        // The cursor in a link: it's to be edited.
        page("""{"block":"p","link":"https://example.org/","text":"a site"}""")
        compose.onNodeWithText("Edit link…").assertExists()
    }

    @Test
    fun aWebAddressTypedWithoutItsBeginningIsAWebPages() {
        assertEquals("https://example.org/roses", webHref(" example.org/roses "))
        assertEquals("http://example.org/", webHref("http://example.org/"))
        assertEquals("mailto:someone@example.org", webHref("mailto:someone@example.org"))
        assertEquals(null, webHref("  "))
    }

    @Test
    fun aLinkToAChapterGoesFromThisChapterToThatOne() {
        val other = state.structure.chapters.first { it.path != state.currentPath }
        val href = state.hrefTo(other)
        assertEquals(other.path.substringAfterLast('/'), href)
        assertEquals(other.path, state.chapterOf(href)?.path)
        assertEquals("#", state.hrefTo(state.structure.chapters.first { it.path == state.currentPath }))
        assertEquals(null, state.chapterOf("https://example.org/"))
        state.link(href, "next")
        idle()
        assertEquals("KLED.cmd(\"link\", \"{\\\"href\\\":\\\"$href\\\",\\\"text\\\":\\\"next\\\"}\")", sent())
    }

    @Test
    fun linksAreTakenOffAndFootnotesAddedInThePage() {
        state.unlink()
        idle()
        assertEquals("KLED.cmd(\"unlink\", null)", sent())
        state.footnote("See the map.")
        idle()
        assertEquals("KLED.cmd(\"footnote\", \"See the map.\")", sent())
    }

    @Test
    fun aSpecialCharacterGoesInWhereTheCursorIs() {
        tap("editor-insert")
        tap("editor-characters")
        compose.onAllNodesWithTag("character")[0].performClick()
        idle()
        assertEquals("KLED.cmd(\"char\", \"—\")", sent())
    }

    @Test
    fun theWordCountIsForTheChapterAndTheWholeBook() {
        compose.onNodeWithContentDescription("More").performClick()
        idle()
        tap("editor-word-count")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("words-book").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("words-chapter").assertExists()
    }
}
