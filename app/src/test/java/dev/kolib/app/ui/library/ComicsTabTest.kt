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
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val takenOut = mutableListOf<List<String>>()
    private val deleted = mutableListOf<List<String>>()
    private val browse = LibraryBrowseState()

    private fun show(here: Map<String, ReadHere> = mapOf("Saga 001.cbz" to ReadHere(1f, 10))) {
        val actions = ComicActions(
            onRead = { read += it },
            onMarkFinished = { finished += it },
            onGroup = { ids, name -> grouped += ids to name },
            onTakeOut = { takenOut += it },
            onDelete = { deleted += it },
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
        compose.onNodeWithTag("comics-add-to-series").performClick()
        // A new series: its name to start with, the one there is.
        compose.onNodeWithTag("comics-new-series").performClick()
        compose.onNodeWithTag("comic-series-name-done").performClick()
        assertEquals(1, grouped.size)
        assertEquals("Saga", grouped[0].second)
        assertEquals(setOf("Saga 001.cbz", "Saga 002.cbz", "Saga 003.cbz", "Watchmen.cbz"), grouped[0].first.toSet())
    }

    @Test
    fun comicsChosenAreAddedToASeriesThereIs() {
        show()
        compose.onNodeWithText("Watchmen").performTouchInput { longClick() }
        compose.onNodeWithText("1 series selected").assertExists()
        compose.onNodeWithTag("comics-add-to-series").performClick()
        // The series there are, to choose from (not the comics chosen).
        compose.onAllNodesWithTag("add-to-series").assertCountEquals(1)
        compose.onAllNodesWithTag("add-to-series")[0].performClick()
        assertEquals(listOf(listOf("Watchmen.cbz") to "Saga"), grouped)
        // Choosing ends with it.
        compose.onAllNodesWithText("1 series selected").assertCountEquals(0)
    }

    @Test
    fun aComicOnItsOwnIsAddedToASeriesFromItsMenu() {
        browse.comicKey = "b:Watchmen.cbz"
        show()
        compose.onNodeWithTag("comic-series-menu").performClick()
        compose.onNodeWithTag("comic-series-add-to").performClick()
        compose.onAllNodesWithTag("add-to-series")[0].performClick()
        assertEquals(listOf(listOf("Watchmen.cbz") to "Saga"), grouped)
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
    fun theLibraryAlwaysHasAComicsTab() {
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
        // The last comic gone, the tab's still there, saying how to fill it.
        books = listOf(book)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Comics (0)").assertExists()
        assertEquals(2, browse.tab)
        compose.onNodeWithText("No comics").assertExists()
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp-mdpi")
    fun onAPhoneTheComicsTabIsThereWithNoComics() {
        val book = comic("The Histories.epub", title = "The Histories").copy(sourceFormat = null)
        compose.setContent {
            AppSurface {
                LibraryScreen(
                    books = listOf(book), covers = covers, browse = browse, job = null, koboConnected = false,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-comics").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(2, browse.tab)
        compose.onNodeWithText("No comics").assertExists()
    }

    private lateinit var back: OnBackPressedDispatcher

    private fun pressBack() {
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
    }

    /** The tab, with a comic's details shown as a line saying whose they are. */
    private fun showWithDetails(actions: ComicActions = ComicActions(onRead = { read += it })) {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AppSurface { LibraryComicsTab(comics, covers, browse, koboConnected = false, here = { null }, actions = actions) { b, _, _ -> Text("Details of ${b.title}") } }
        }
        compose.waitForIdle()
    }

    @Test
    fun onAWideScreenBackFromAComicsDetailsIsItsSeries() {
        showWithDetails()
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Details")[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Details of Saga 002").assertExists()
        pressBack()
        compose.onAllNodesWithText("Details of Saga 002").assertCountEquals(0)
        compose.onNodeWithTag("comic-series-page").assertExists()
        assertEquals("s:saga", browse.comicKey)
        // Back again: the shelf on its own.
        pressBack()
        assertNull(browse.comicKey)
    }

    @Test
    fun aSeriesCanOpenInPlaceOfTheShelfAsAListOrAsCovers() {
        browse.seriesInPlace = true
        showWithDetails()
        // No side panel.
        compose.onAllNodesWithText("Tap a series").assertCountEquals(0)
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("comic-shelf").assertCountEquals(0)
        compose.onAllNodesWithTag("comic-issue").assertCountEquals(3)
        // As covers, each an entry of its own.
        compose.onNodeWithTag("comic-series-layout").performClick()
        compose.waitForIdle()
        assertTrue(browse.seriesGrid)
        compose.onNodeWithTag("comic-issue-grid").assertExists()
        compose.onAllNodesWithTag("comic-issue").assertCountEquals(3)
        // A comic's details: no button on each, but held, then Info in the bar; back to its series as it was, then back to
        // the shelf.
        compose.onAllNodesWithContentDescription("Details").assertCountEquals(0)
        compose.onAllNodesWithTag("comic-issue")[0].performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("comic-issue-info").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Details of Saga 001").assertExists()
        pressBack()
        compose.onNodeWithTag("comic-issue-grid").assertExists()
        pressBack()
        compose.onNodeWithTag("comic-shelf").assertExists()
        // And as a list again.
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comic-series-layout").performClick()
        compose.waitForIdle()
        assertFalse(browse.seriesGrid)
        compose.onAllNodesWithTag("comic-issue-grid").assertCountEquals(0)
    }

    @Test
    fun theComicsTabCanBeHiddenFromItsMenu() {
        val book = comic("The Histories.epub", title = "The Histories").copy(sourceFormat = null)
        compose.setContent {
            AppSurface {
                LibraryScreen(
                    books = listOf(book) + comics, covers = covers, browse = browse, job = null, koboConnected = false,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Comics (2)").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("comic-sort").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comics-hide-tab").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(browse.comicsTab)
        compose.onAllNodesWithTag("tab-comics").assertCountEquals(0)
        assertEquals(0, browse.tab)
        // The comics are with the books again.
        compose.onNodeWithText("Books (5)").assertExists()
    }

    @Test
    fun removingASeriesKeepsItsComicsUnlessTheyreRemovedToo() {
        show()
        compose.onNodeWithText("Saga").performTouchInput { longClick() }
        compose.onNodeWithText("1 series selected").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithTag("series-remove-keep").performClick()
        // Its comics stay, out of the series; none removed.
        assertEquals(listOf(setOf("Saga 001.cbz", "Saga 002.cbz", "Saga 003.cbz")), takenOut.map { it.toSet() })
        assertTrue(deleted.isEmpty())
        // Or with the comics.
        compose.onNodeWithText("Saga").performTouchInput { longClick() }
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove the series and its 3 comics").performClick()
        assertEquals(listOf(setOf("Saga 001.cbz", "Saga 002.cbz", "Saga 003.cbz")), deleted.map { it.toSet() })
    }

    @Test
    fun aSeriesIsRemovedFromItsMenuKeepingItsComics() {
        browse.comicKey = "s:saga"
        show()
        compose.onNodeWithTag("comic-series-menu").performClick()
        compose.onNodeWithTag("comic-series-remove").performClick()
        compose.onNodeWithTag("series-remove-keep").performClick()
        assertEquals(1, takenOut.size)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun aComicOnItsOwnIsRemovedAsBefore() {
        show()
        compose.onNodeWithText("Watchmen").performTouchInput { longClick() }
        compose.onNodeWithText("Remove").performClick()
        // The usual question for removing books: no series to keep.
        compose.onAllNodesWithTag("series-remove-keep").assertCountEquals(0)
        compose.onNodeWithText("Remove this book from the library?").assertExists()
    }

    @Test
    fun inPlaceOfTheShelfAComicIsOpenedByATapAndItsInfoIsAtTheTop() {
        browse.seriesInPlace = true
        showWithDetails()
        // A comic on its own opens to read; the shelf stays.
        compose.onNodeWithText("Watchmen").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Watchmen.cbz"), read)
        assertNull(browse.comicKey)
        // Held, its details are Info at the top.
        compose.onNodeWithText("Watchmen").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("comics-info").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Details of Watchmen").assertExists()
        // Back is the shelf.
        pressBack()
        compose.onNodeWithTag("comic-shelf").assertExists()
        assertNull(browse.comicKey)
        // With two chosen there's no Info.
        compose.onNodeWithText("Watchmen").performTouchInput { longClick() }
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("comics-info").assertCountEquals(0)
    }

    @Test
    fun switchedToInPlaceOfTheShelfTheShelfShows() {
        showWithDetails()
        // Open beside the shelf.
        compose.onNodeWithText("Saga").performClick()
        compose.waitForIdle()
        assertEquals("s:saga", browse.comicKey)
        compose.onNodeWithTag("comic-sort").performClick()
        compose.onNodeWithTag("series-in-place").performClick()
        compose.waitForIdle()
        assertTrue(browse.seriesInPlace)
        assertNull(browse.comicKey)
        compose.onNodeWithTag("comic-shelf").assertExists()
        compose.onAllNodesWithTag("comic-series-page").assertCountEquals(0)
    }
}
