package dev.kolib.app.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicMode
import dev.kolib.app.comic.ComicPages
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Past a comic's last page, as in Mihon: it's finished, and the next in its series is offered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicEndTest {

    @get:Rule
    val compose = createComposeRule()

    private var nexts = 0
    private var closes = 0
    private lateinit var dir: File

    /** A comic of three pages, open at its last. */
    private fun open(name: String, mode: ComicMode = ComicMode.LEFT_TO_RIGHT, next: Boolean = true) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dir = File(context.cacheDir, "comic-end-$name").apply { deleteRecursively(); mkdirs() }
        val file = TestComics.comic(File(dir, "saga.cbz"), pages = 3)
        val book = LibraryBook(
            id = "saga-1", meta = BookMetadata(title = "Saga 001", series = "Saga", seriesIndex = 1.0), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = BookSource.FILE, originalName = "Saga 001.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        ReaderStore.savePdfPlace(dir, PdfPlace(2, 3))
        val pages = ComicPages(ComicArchives.open(file, libArchive = null))
        val ending = ComicEnding("#1", series = "Saga", next = if (next) "#2" else null, onNext = if (next) ({ nexts++ }) else null)
        compose.setContent {
            var menu by remember { mutableStateOf(false) }
            AppSurface {
                ComicView(
                    book = book, pages = pages, dir = dir, s = ComicSettings(mode = mode), mode = mode, menu = menu, setMenu = { menu = it },
                    ending = ending, onMode = {}, onSettings = {}, onClose = { closes++ }, onCover = {}, readingHere = null,
                )
            }
        }
        settle()
    }

    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(100)
            compose.waitForIdle()
        }
    }

    /** A tap on the right of the page (on, page by page), or at its foot (on, in the strip). */
    private fun tapOn(x: Float = 380f, y: Float = 400f) {
        compose.onRoot().performTouchInput { click(Offset(x, y)) }
        settle()
    }

    private fun endShowing() = compose.onAllNodesWithTag("comic-end").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun onFromTheLastPageTheNextIsOfferedThenOpened() {
        open("paged")
        assertTrue(!endShowing())
        tapOn()
        assertTrue(endShowing())
        compose.onNodeWithText("Finished").assertExists()
        compose.onNodeWithText("#2").assertExists()
        // It's been read.
        assertTrue(ReaderStore.readHereOf(dir)!!.finished)
        compose.onNodeWithTag("comic-next").performClick()
        assertEquals(1, nexts)
    }

    @Test
    fun swipedOnFromTheLastPageTheEndShowsAndSwipedOnAgainTheNextOpens() {
        open("swipe")
        compose.onRoot().performTouchInput { swipeLeft(startX = 380f, endX = 40f, durationMillis = 300) }
        settle()
        assertTrue(endShowing())
        assertEquals(0, nexts)
        compose.onNodeWithTag("comic-end").performTouchInput { swipeLeft(startX = 380f, endX = 40f, durationMillis = 300) }
        settle()
        assertEquals(1, nexts)
    }

    @Test
    fun backFromTheEndIsTheLastPageAgain() {
        open("back")
        tapOn()
        compose.onNodeWithText("Back to the last page").performClick()
        settle()
        assertTrue(!endShowing())
        assertEquals(0, nexts)
    }

    @Test
    fun theLastInItsSeriesSaysSo() {
        open("last", next = false)
        tapOn()
        compose.onNodeWithText("That's the last comic in Saga.").assertExists()
        // Going on again does nothing more.
        compose.onNodeWithText("Close the comic").performClick()
        assertEquals(1, closes)
        assertEquals(0, nexts)
    }

    @Test
    fun inTheLongStripTheEndComesAfterTheLastPage() {
        open("strip", mode = ComicMode.STRIP)
        // To the very end of the strip, then on again.
        for (i in 0 until 8) {
            if (endShowing()) break
            compose.onRoot().performTouchInput { swipeUp(startY = 700f, endY = 100f, durationMillis = 200) }
            settle()
        }
        if (!endShowing()) tapOn(200f, 760f)
        assertEquals(0, nexts)
        assertTrue(endShowing())
        compose.onNodeWithTag("comic-next").performClick()
        assertEquals(1, nexts)
    }
}
