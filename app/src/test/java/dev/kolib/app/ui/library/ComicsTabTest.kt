package dev.kolib.app.ui.library

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.ui.theme.AppSurface
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The library's Comics tab: a series to an entry, its comics in order, carrying on, choosing, and series made by hand. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class ComicsTabTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val covers = LibraryCovers(Library(context, File(context.filesDir, "comics-tab-" + UUID.randomUUID()), null))

    private fun comic(file: String, series: String? = null, index: Double? = null, title: String = file.substringBeforeLast('.')) = LibraryBook(
        id = file, meta = BookMetadata(title = title, series = series, seriesIndex = index, authors = listOf("Brian K. Vaughan")),
        collections = emptyList(), fileSize = 1000, hasCover = false, coverVersion = 1, addedAt = 0, modifiedAt = 0,
        source = BookSource.FILE, originalName = file, koboPath = null, sourceFormat = "CBZ",
    )

    private val comics = listOf(
        comic("Saga 003.cbz", "Saga", 3.0), comic("Saga 001.cbz", "Saga", 1.0), comic("Saga 002.cbz", "Saga", 2.0),
        comic("Watchmen.cbz", title = "Watchmen"),
    )

    private val read = mutableListOf<String>()
    private val finished = mutableListOf<List<String>>()
    private val grouped = mutableListOf<Pair<List<String>, String>>()
    private val browse = LibraryBrowseState()

    private fun show(here: Map<String, ReadHere> = mapOf("Saga 001.cbz" to ReadHere(1f, 10))) {
        val actions = ComicActions(
            onRead = { read += it },
            onMarkFinished = { finished += it },
            onGroup = { ids, name -> grouped += ids to name },
        )
        compose.setContent {
            AppSurface { LibraryComicsTab(comics, covers, browse, koboConnected = false, here = { here[it.id] }, actions = actions) { _, _, _ -> } }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSeriesIsOneEntryWithItsComicsInOrder() {
        show()
        compose.onAllNodesWithTag("comic-series").assertCountEquals(2)
        // Saga's two not read yet show on its cover.
        compose.onNodeWithContentDescription("2 not read yet").assertExists()
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        assertEquals("s:saga", browse.comicKey)
        val issues = compose.onAllNodesWithTag("comic-issue").fetchSemanticsNodes()
        assertEquals(3, issues.size)
        compose.onNodeWithText("#1").assertExists()
        // On from the first not read.
        compose.onNodeWithText("Carry on: #2").assertExists()
        compose.onNodeWithTag("comic-continue").performClick()
        assertEquals(listOf("Saga 002.cbz"), read)
        // A comic tapped opens it.
        compose.onNodeWithText("#3").performClick()
        assertEquals(listOf("Saga 002.cbz", "Saga 003.cbz"), read)
    }

    @Test
    fun aSeriesNotStartedStartsAtTheFirst() {
        show(here = emptyMap())
        compose.onNodeWithText("Saga").performClick()
        compose.onNodeWithText("Start reading").performClick()
        assertEquals(listOf("Saga 001.cbz"), read)
    }

    @Test
    fun comicsHeldAreChosenToMarkAsRead() {
        show()
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("#2").performTouchInput { longClick() }
        compose.onNodeWithText("#3").performClick()
        compose.onNodeWithText("2 comics selected").assertExists()
        compose.onNodeWithTag("comics-mark-read").performClick()
        assertEquals(listOf(listOf("Saga 002.cbz", "Saga 003.cbz")), finished)
        // Choosing ends with it.
        compose.onAllNodesWithText("2 comics selected").assertCountEquals(0)
    }

    @Test
    fun seriesHeldArePutInOneSeries() {
        show()
        compose.onNodeWithText("Watchmen").performTouchInput { longClick() }
        compose.onNodeWithText("Saga").performClick()
        compose.onNodeWithText("2 series selected").assertExists()
        compose.onNodeWithTag("comics-group").performClick()
        // The series' name to start with: the one there is.
        compose.onNodeWithTag("comic-series-name-done").performClick()
        assertEquals(1, grouped.size)
        assertEquals("Saga", grouped[0].second)
        assertEquals(setOf("Saga 001.cbz", "Saga 002.cbz", "Saga 003.cbz", "Watchmen.cbz"), grouped[0].first.toSet())
    }

    @Test
    fun theSearchFindsSeriesByName() {
        show()
        compose.onNodeWithTag("comic-search").performTextInput("watch")
        compose.waitForIdle()
        compose.onAllNodesWithTag("comic-series").assertCountEquals(1)
        compose.onNodeWithText("Watchmen").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp-mdpi")
    fun onAPhoneASeriesOpensOnAPageOfItsOwn() {
        val book = comic("The Histories.epub", title = "The Histories").copy(sourceFormat = null)
        compose.setContent {
            AppSurface {
                LibraryScreen(
                    books = listOf(book) + comics, covers = covers, browse = browse, job = null, koboConnected = false,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                    onRead = { read += it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-comics").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-series-page").assertExists()
        compose.onAllNodesWithTag("tab-comics").assertCountEquals(0)
        compose.onNodeWithTag("comic-continue").performClick()
        assertEquals(listOf("Saga 001.cbz"), read)
        // A comic's details, and back to its series, then to the shelf.
        compose.onAllNodesWithContentDescription("Details")[1].performClick()
        compose.waitForIdle()
        assertEquals("Saga 002.cbz", browse.comicBookId)
        compose.onAllNodesWithTag("comic-series-page").assertCountEquals(0)
        browse.comicBookId = null
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back to the comics").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("comic-series").assertCountEquals(2)
    }

    @Test
    fun theLibraryHasAComicsTabOnlyWithComics() {
        val book = comic("The Histories.epub", title = "The Histories").copy(sourceFormat = null)
        var books by androidx.compose.runtime.mutableStateOf(listOf(book) + comics)
        compose.setContent {
            AppSurface {
                LibraryScreen(
                    books = books, covers = covers, browse = browse, job = null, koboConnected = false,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                )
            }
        }
        compose.waitForIdle()
        // Books only in the Books tab; a series to an entry in the Comics tab.
        compose.onNodeWithText("Books (1)").assertExists()
        compose.onNodeWithText("Comics (2)").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(2, browse.tab)
        compose.onAllNodesWithTag("comic-series").assertCountEquals(2)
        books = listOf(book)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onAllNodesWithTag("tab-comics").assertCountEquals(0)
        assertEquals(0, browse.tab)
    }
}
