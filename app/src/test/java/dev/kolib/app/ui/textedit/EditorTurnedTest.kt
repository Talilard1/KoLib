package dev.kolib.app.ui.textedit

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
 * Turning the device rearranges the editor (the chapters beside the page or not, a panel at its side or in the
 * chapters' place). The page itself stays: the same view, still there, with what was being typed in it.
 * It used to be made again, and the new view was then taken down in the old one's place, leaving a blank screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h1280dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorTurnedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private fun editorState(): TextEditState {
        val lib = Library(context, File(context.filesDir, "turnlib-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        return TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch1.xhtml")
    }

    private var size by mutableStateOf(1280.dp to 800.dp)

    private fun show(state: TextEditState, w: Dp, h: Dp) {
        size = w to h
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                Box(Modifier.size(size.first, size.second)) { TextEditorScreen(state, onClose = {}, onRead = {}) }
            }
        }
        compose.waitForIdle()
    }

    private fun turnTo(w: Dp, h: Dp) {
        size = w to h
        compose.waitForIdle()
    }

    private fun live(view: WebView?) = view != null && !shadowOf(view).wasDestroyCalled()

    @Test
    fun onATabletThePageIsStillThereAfterTurningAndTurningBack() {
        val state = editorState()
        show(state, 1280.dp, 800.dp)
        val page = state.pageView
        assertNotNull(page)
        // Upright: the chapters and the page, two across.
        turnTo(800.dp, 1280.dp)
        assertSame("the same page, not one made again", page, state.pageView)
        assertTrue("and not taken down", live(state.pageView))
        // On its side again: three across when a panel's open.
        turnTo(1280.dp, 800.dp)
        assertSame(page, state.pageView)
        assertTrue(live(state.pageView))
    }

    @Test
    fun withFindOpenTooWhichMovesFromTheSideToTheChaptersPlace() {
        val state = editorState()
        show(state, 1280.dp, 800.dp)
        val page = state.pageView
        state.find.open = true
        compose.waitForIdle()
        turnTo(800.dp, 1280.dp)
        assertSame(page, state.pageView)
        assertTrue(live(state.pageView))
        turnTo(1280.dp, 800.dp)
        state.find.close()
        compose.waitForIdle()
        assertSame(page, state.pageView)
        assertTrue(live(state.pageView))
    }

    @Test
    fun onAPhoneTurnedOnItsSideThePageStaysAsTheChaptersComeUpBesideIt() {
        val state = editorState()
        show(state, 411.dp, 914.dp)
        val page = state.pageView
        assertNotNull("a phone opens on the page when a chapter's given", page)
        turnTo(914.dp, 411.dp)
        assertSame(page, state.pageView)
        assertTrue(live(state.pageView))
        turnTo(411.dp, 914.dp)
        assertSame(page, state.pageView)
        assertTrue(live(state.pageView))
    }

    @Test
    fun aNewPageMadeBeforeTheOldOneIsLetGoOfIsTheOneThatStays() {
        val state = editorState()
        val old = state.createView(context, dark = false)
        // The screen is rearranged: the new view is made first, and only then is the old one released.
        val new = state.createView(context, dark = false)
        state.releaseView(old)
        assertTrue(shadowOf(old).wasDestroyCalled())
        assertSame(new, state.pageView)
        assertFalse(shadowOf(new).wasDestroyCalled())
        state.releaseView(new)
        assertNull(state.pageView)
    }

    /**
     * Why letting go of "the page's view" used to take down the wrong one: when a view moves from one arrangement
     * of a screen to another, the new one is made first, and the old one is released after.
     */
    @Test
    fun whenAnArrangementChangesTheNewViewIsMadeBeforeTheOldOneIsReleased() {
        val log = ArrayList<String>()
        var across by mutableStateOf(true)
        compose.setContent {
            if (across) {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.ui.viewinterop.AndroidView(factory = { log += "made 1"; android.view.View(it) }, onRelease = { log += "released 1" })
                }
            } else {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.ui.viewinterop.AndroidView(factory = { log += "made 2"; android.view.View(it) }, onRelease = { log += "released 2" })
                }
            }
        }
        compose.waitForIdle()
        across = false
        compose.waitForIdle()
        org.junit.Assert.assertEquals(listOf("made 1", "made 2", "released 1"), log)
    }

    /** How far down the page is told to start, as the page itself asks when it's loaded. */
    private fun startScroll(view: WebView): Int {
        val bridge = shadowOf(view).getJavascriptInterface("KLE")
        return org.json.JSONObject(bridge.javaClass.getMethod("start").invoke(bridge) as String).optInt("scrollY", -1)
    }

    /**
     * On a phone the chapters are a screen of their own, with no page behind it. Another chapter chosen there opens
     * at its top, not as far down as the last one was left; the same one opens where it was left.
     */
    @Test
    fun onAPhoneAnotherChapterOpensAtItsTopAndTheSameOneWhereItWasLeft() {
        val state = editorState()
        val first = state.createView(context, dark = false)
        // Scrolled some way down chapter 1, as the page reports it.
        val bridge = shadowOf(first).getJavascriptInterface("KLE")
        bridge.javaClass.getMethod("state", String::class.java).invoke(bridge, """{"scrollY": 740}""")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        // To the list of chapters: the page's view goes.
        state.releaseView(first)
        // The same chapter again: where it was.
        state.open("OEBPS/text/ch1.xhtml")
        state.awaitWork()
        val again = state.createView(context, dark = false)
        assertTrue("where it was left: ${startScroll(again)}", startScroll(again) == 740)
        state.releaseView(again)
        // Another chapter: from its top.
        state.open("OEBPS/text/ch2.xhtml")
        state.awaitWork()
        val other = state.createView(context, dark = false)
        assertTrue("from the top: ${startScroll(other)}", startScroll(other) == 0)
        state.releaseView(other)
    }
}
