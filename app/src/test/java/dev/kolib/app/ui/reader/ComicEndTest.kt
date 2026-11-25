package dev.kolib.app.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Past a comic's last page, as in Mihon: the page between it and the next in its series (or the end), and the next
 * read on into, with that page or without; and back again into the one before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicEndTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var closes = 0
    private val goneTo = ArrayList<String>()
    private var current by mutableStateOf("")
    private lateinit var comics: List<OpenComic>

    private fun comic(name: String, n: Int, pages: Int, at: Int?): OpenComic {
        val dir = File(context.cacheDir, "comic-end-$name-${System.nanoTime()}").apply { mkdirs() }
        val file = TestComics.comic(File(dir, "saga.cbz"), pages = pages, info = false)
        val book = LibraryBook(
            id = name, meta = BookMetadata(title = "Saga 00$n", series = "Saga", seriesIndex = n.toDouble()), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = BookSource.FILE, originalName = "Saga 00$n.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        if (at != null) ReaderStore.savePdfPlace(dir, PdfPlace(at, pages))
        return OpenComic(book, ComicPages(ComicArchives.open(file, libArchive = null)), dir, name = "#$n")
    }

    /**
     * The comics of a series, of [counts] pages each, as the reader has them: reading the one at [start], at its page
     * [at]; reading on into another makes it the one being read, as the reader does.
     */
    private fun open(counts: List<Int>, start: Int, at: Int, s: ComicSettings = ComicSettings(alwaysTransition = true), series: String? = "Saga") {
        comics = counts.mapIndexed { i, n -> comic("saga-${i + 1}", i + 1, n, if (i == start) at else null) }
        current = comics[start].id
        compose.setContent {
            var menu by remember { mutableStateOf(false) }
            var generation by remember { mutableIntStateOf(0) }
            var startPage by remember { mutableStateOf<Int?>(null) }
            val i = comics.indexOfFirst { it.id == current }
            AppSurface {
                ComicView(
                    comic = comics[i], s = s, mode = s.mode, menu = menu, setMenu = { menu = it }, onSettings = {}, onClose = { closes++ },
                    onCover = { _, _ -> }, readingHere = null, startPage = startPage, generation = generation, series = series,
                    before = comics.getOrNull(i - 1)?.let { Neighbour(it.id, it.name, it) },
                    after = comics.getOrNull(i + 1)?.let { Neighbour(it.id, it.name, it) },
                    onCurrent = { current = it },
                    onGoTo = { id ->
                        goneTo += id
                        startPage = 0
                        current = id
                        generation++
                    },
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

    /** A tap on the right of the page (on, page by page), or on the left (back). */
    private fun tap(x: Float = 380f, y: Float = 400f) {
        compose.onRoot().performTouchInput { click(Offset(x, y)) }
        settle()
    }

    /** The page between comics on the screen (not just laid out beside it, as the pager has the pages either side). */
    private fun betweenShowing(): Boolean = compose.onAllNodesWithTag("comic-between").fetchSemanticsNodes().any { n ->
        val b = n.boundsInRoot
        b.width > 1f && b.height > 1f
    }

    private fun finished(i: Int) = ReaderStore.readHereOf(comics[i].dir)?.finished == true

    @Test
    fun aComicOnItsOwnEndsWithAPageThatSaysSo() {
        open(listOf(3), start = 0, at = 2, series = null)
        assertFalse(betweenShowing())
        tap()
        assertTrue(betweenShowing())
        compose.onNodeWithText("That's the end of the comic.").assertExists()
        // It's been read.
        assertTrue(finished(0))
        compose.onNodeWithText("Close the comic").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun theLastInItsSeriesSaysSo() {
        open(listOf(3), start = 0, at = 2)
        tap()
        compose.onNodeWithText("That's the last comic in Saga.").assertExists()
        // Going on again goes nowhere.
        tap()
        assertTrue(betweenShowing())
        assertEquals("saga-1", current)
    }

    @Test
    fun thePageBetweenNamesTheNextAndGoingOnReadsIt() {
        open(listOf(3, 4), start = 0, at = 2)
        tap()
        assertTrue(betweenShowing())
        compose.onNodeWithText("Finished").assertExists()
        compose.onNodeWithText("#1").assertExists()
        compose.onNodeWithText("Next").assertExists()
        compose.onNodeWithText("#2").assertExists()
        assertTrue(finished(0))
        assertEquals("saga-1", current)
        // On again: the next comic's first page, as any page comes on.
        tap()
        assertEquals("saga-2", current)
        assertFalse(betweenShowing())
        // On its first page it isn't being read yet.
        assertNull(ReaderStore.readHereOf(comics[1].dir))
    }

    @Test
    fun swipedOnTheSameHappens() {
        open(listOf(3, 4), start = 0, at = 2)
        compose.onRoot().performTouchInput { swipeLeft(startX = 360f, endX = 40f, durationMillis = 300) }
        settle()
        assertTrue(betweenShowing())
        compose.onRoot().performTouchInput { swipeLeft(startX = 360f, endX = 40f, durationMillis = 300) }
        settle()
        assertEquals("saga-2", current)
    }

    @Test
    fun joinedTheNextComicFollowsStraightOn() {
        open(listOf(3, 4), start = 0, at = 2, s = ComicSettings(alwaysTransition = false))
        tap()
        assertFalse(betweenShowing())
        assertEquals("saga-2", current)
        assertTrue(finished(0))
        // And on through it, as one.
        tap()
        assertEquals(1, ReaderStore.pdfPlace(comics[1].dir)!!.page)
    }

    @Test
    fun backFromTheNextIsTheOneBeforesLastPage() {
        open(listOf(3, 4), start = 1, at = 0)
        tap(x = 20f)
        assertTrue(betweenShowing())
        compose.onNodeWithText("Previous").assertExists()
        compose.onNodeWithText("Current").assertExists()
        tap(x = 20f)
        assertEquals("saga-1", current)
        assertEquals(2, ReaderStore.pdfPlace(comics[0].dir)!!.page)
    }

    @Test
    fun inTheLongStripTheNextComicFollowsTheOneBefore() {
        open(listOf(2, 3), start = 0, at = 1, s = ComicSettings(mode = ComicMode.STRIP, alwaysTransition = true))
        var sawBetween = false
        for (i in 0 until 30) {
            if (current == "saga-2") break
            compose.onRoot().performTouchInput { swipeUp(startY = 600f, endY = 300f, durationMillis = 900) }
            settle()
            sawBetween = sawBetween || betweenShowing()
        }
        assertTrue(sawBetween)
        assertEquals("saga-2", current)
        assertTrue(finished(0))
    }

    @Test
    fun inTheLongStripJoinedThereIsNoPageBetween() {
        open(listOf(2, 3), start = 0, at = 1, s = ComicSettings(mode = ComicMode.STRIP, alwaysTransition = false))
        for (i in 0 until 30) {
            if (current == "saga-2") break
            assertFalse(betweenShowing())
            compose.onRoot().performTouchInput { swipeUp(startY = 600f, endY = 300f, durationMillis = 900) }
            settle()
        }
        assertEquals("saga-2", current)
    }

    @Test
    fun theButtonsBesideTheSliderOpenTheComicsEitherSide() {
        open(listOf(3, 4, 2), start = 1, at = 2)
        // The menu, from a tap in the middle.
        tap(x = 200f)
        compose.onNodeWithTag("comic-next-comic").performClick()
        settle()
        assertEquals(listOf("saga-3"), goneTo)
        assertEquals("saga-3", current)
        // On its first page it isn't being read yet.
        assertNull(ReaderStore.readHereOf(comics[2].dir))
        if (compose.onAllNodesWithTag("comic-previous-comic").fetchSemanticsNodes().isEmpty()) tap(x = 200f)
        compose.onNodeWithTag("comic-previous-comic").performClick()
        settle()
        assertEquals("saga-2", current)
    }
}
