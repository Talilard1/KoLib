package dev.kobolibrary.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Collections tabs' layout: with room, the collections stay where they are whatever is tapped, and so do the
 * open collection's books when a book of it is opened beside them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class CollectionsLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private var collection by mutableStateOf<String?>(null)
    private var book by mutableStateOf<String?>(null)
    private val rows = listOf(CollectionRow("a", "Classics", 2), CollectionRow("b", "Gothic", 1), CollectionRow(NO_COLLECTION_KEY, "Not in any collection", 0))

    private fun show(width: Dp) {
        compose.setContent {
            Box(Modifier.width(width)) {
                CollectionsLayout(
                    hasCollection = collection != null,
                    hasBook = book != null,
                    onOpenFirst = { collection = rows.first().key },
                    onCloseCollection = { collection = null; book = null },
                    onCloseBook = { book = null },
                    list = { modifier, panes ->
                        CollectionList(
                            rows, "3 collections", collection,
                            onSelect = { key ->
                                if (key != collection || panes != CollectionPanes.THREE) book = null
                                collection = key
                            },
                            modifier = modifier.testTag("list"),
                            opens = panes == CollectionPanes.ONE,
                        )
                    },
                    books = { onBack ->
                        Box(Modifier.fillMaxSize().testTag("books")) {
                            Text("Books of $collection", Modifier.testTag("open-book").then(Modifier))
                            if (onBack != null) Text("back to collections")
                        }
                    },
                    detail = { onBack -> Box(Modifier.fillMaxSize().testTag("detail")) { Text(if (onBack != null) "Detail with back" else "Detail") } },
                    nothingChosen = { Text("Tap a collection") },
                    noBookChosen = { Text("Tap a book", Modifier.testTag("no-book")) },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun left(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().left
    private fun width(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { it.right - it.left }

    @Test
    fun wideNothingMovesWhenACollectionOrABookIsTapped() {
        show(1200.dp)
        // The first collection is open beside the list from the start, and the details' place is there, empty.
        compose.onNodeWithText("Books of a").assertExists()
        compose.onNodeWithTag("no-book").assertExists()
        val list = left("list") to width("list")
        val books = left("books") to width("books")
        val details = left("no-book")

        compose.onNodeWithText("Gothic").performClick()
        compose.onNodeWithText("Books of b").assertExists()
        compose.onNodeWithText("Gothic").assertIsSelected()
        assertEquals(list, left("list") to width("list"))
        assertEquals(books, left("books") to width("books"))

        // A book opens on the right; the list and the books are where they were.
        book = "x"
        compose.waitForIdle()
        compose.onNodeWithText("Detail").assertExists()
        compose.onNodeWithTag("list").assertExists()
        assertEquals(list, left("list") to width("list"))
        assertEquals(books, left("books") to width("books"))
        assert(left("detail") <= details) { "the details start where their empty place was" }

        // The open collection tapped again changes nothing; another puts the book away.
        compose.onNodeWithText("Gothic").performClick()
        assertEquals("x", book)
        compose.onNodeWithText("Classics").performClick()
        assertNull(book)
        compose.onNodeWithText("Books of a").assertExists()
        assertEquals(list, left("list") to width("list"))
    }

    /** A tablet on its side with Android's display size set larger (about 1060dp beside the app's bar) still has all three. */
    @Test
    fun aTabletOnItsSideWithLargerDisplaySizeStillHasAllThree() {
        show(1058.dp)
        book = "x"
        compose.waitForIdle()
        compose.onNodeWithTag("list").assertExists()
        compose.onNodeWithTag("books").assertExists()
        compose.onNodeWithText("Detail").assertExists()
        assert(width("books") >= 300.dp) { "room for two books to a row" }
    }

    @Test
    fun mediumTheListStaysAndABookTakesItsCollectionsPlace() {
        show(720.dp)
        compose.onNodeWithText("Books of a").assertExists()
        val list = left("list") to width("list")
        val books = left("books") to width("books")
        book = "x"
        compose.waitForIdle()
        compose.onNodeWithText("Detail with back").assertExists()
        compose.onNodeWithTag("books").assertDoesNotExist()
        assertEquals(list, left("list") to width("list"))
        assertEquals(books, left("detail") to width("detail"))
        // The open collection tapped in the list goes back to its books.
        compose.onNodeWithText("Classics").performClick()
        assertNull(book)
        compose.onNodeWithText("Books of a").assertExists()
        assertEquals(list, left("list") to width("list"))
    }

    @Test
    fun narrowOneAtATime() {
        show(411.dp)
        // Nothing opens by itself: the list has the screen.
        assertNull(collection)
        compose.onNodeWithTag("books").assertDoesNotExist()
        compose.onNodeWithText("Gothic").performClick()
        compose.onNodeWithText("Books of b").assertExists()
        compose.onNodeWithText("back to collections").assertExists()
        compose.onNodeWithTag("list").assertDoesNotExist()
        book = "x"
        compose.waitForIdle()
        compose.onNodeWithText("Detail with back").assertExists()
    }

    @Test
    fun aCollectionsBooksStayWhereTheyWereScrolledToUntilAnotherIsOpened() {
        val scroll = CollectionScroll()
        val a = scroll.grid("a")
        assertSame(a, scroll.grid("a"))
        assertNotSame(a, scroll.grid("b"))
        assertNotSame(a, scroll.grid("a"))
        assertSame(scroll.list, scroll.list)
    }
}
