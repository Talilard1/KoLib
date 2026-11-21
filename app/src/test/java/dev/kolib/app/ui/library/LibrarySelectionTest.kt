package dev.kolib.app.ui.library

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.TestLanguage
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.ui.theme.AppSurface
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Books chosen in the library: marked read or unread together from the bar along the foot, which stays on one line on a
 * tablet held sideways; and the side panel goes on showing the book that was open, as the Kobo's and the comics' do.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1200dp-h800dp-land-mdpi")
class LibrarySelectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val read = ArrayList<List<String>>()
    private val unread = ArrayList<List<String>>()

    private fun library(): Pair<Library, LibraryCovers> = runBlocking {
        val name = "selection-" + UUID.randomUUID()
        val lib = Library(context, File(context.filesDir, name), "$name.db").apply { load() }
        for ((t, a) in listOf("The Histories" to "Herodotus", "The Iliad" to "Homer", "The Odyssey" to "Homer")) {
            val f = FakeKobo.epub(File(context.cacheDir, "$name-$t.epub"), FakeKobo.Book(t, a, mime = "application/epub+zip"))
            lib.import("$t.epub", { f.inputStream() }, BookSource.FILE)
        }
        check(lib.books.value.size == 3) { "Three books, not ${lib.books.value.size}" }
        lib to LibraryCovers(lib)
    }

    private fun show(browse: LibraryBrowseState, lib: Library, covers: LibraryCovers) {
        compose.setContent {
            AppSurface {
                LibraryScreen(
                    books = lib.books.value, covers = covers, browse = browse, job = null, koboConnected = true,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                    onMarkFinished = { read += it }, onMarkUnread = { unread += it }, onSyncReading = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun chosenBooksAreMarkedReadOrUnreadTogether() {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selecting = true; ids.take(2).forEach { picked[it] = true } }
        show(browse, lib, covers)
        compose.onNodeWithTag("selection-mark-read").performClick()
        assertEquals(listOf(ids.take(2).toSet()), read.map { it.toSet() })
        assertFalse(browse.selecting)
        compose.runOnIdle { browse.selecting = true; ids.forEach { browse.picked[it] = true } }
        compose.onNodeWithTag("selection-mark-unread").performClick()
        assertEquals(listOf(ids.toSet()), unread.map { it.toSet() })
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun onAPhoneTooChosenBooksAreMarkedReadOrUnread() {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selecting = true; ids.take(2).forEach { picked[it] = true } }
        show(browse, lib, covers)
        compose.onNode(hasText("Read") and hasClickAction()).performClick()
        assertEquals(listOf(ids.take(2).toSet()), read.map { it.toSet() })
        assertFalse(browse.selecting)
        compose.runOnIdle { browse.selecting = true; ids.forEach { browse.picked[it] = true } }
        compose.onNode(hasText("Unread") and hasClickAction()).performClick()
        assertEquals(listOf(ids.toSet()), unread.map { it.toSet() })
        // As many as fit on a phone's bar, and the rest a tap away, under More.
        compose.runOnIdle { browse.selecting = true; ids.forEach { browse.picked[it] = true } }
        for (tag in listOf("selection-send", "selection-mark-read", "selection-mark-unread", "selection-sync-reading", "selection-save", "selection-remove")) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        }
        compose.onNode(hasText("More") and hasClickAction()).performClick()
        compose.onNodeWithText("Duplicate").assertExists()
        compose.onNodeWithText("Combine").assertExists()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/library_choosing_phone_more.png")
    }

    @Test
    fun theSidePanelKeepsTheBookWhileBooksAreChosen() {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selectedId = ids[0]; selecting = true; picked[ids[1]] = true }
        show(browse, lib, covers)
        // No panel in its place saying how many are chosen: the bar says that.
        compose.onAllNodesWithText("Tap books to select them, then use the buttons below the list.").assertCountEquals(0)
        compose.onAllNodesWithText("1 book selected").assertCountEquals(1)
        // The book still open beside the list: its details' buttons are there.
        compose.onNodeWithText("Edit details").assertExists()
    }

    @Test
    fun theBarIsOneLineAcrossATabletHeldSideways() {
        everyButtonOnOneLine("library_choosing_tablet.png")
    }

    @Test
    // A phone held sideways, less the app's rail of sections down the side.
    @Config(qualifiers = "w800dp-h411dp-land-xxhdpi")
    fun theBarIsOneLineOnAPhoneHeldSideways() {
        everyButtonOnOneLine("library_choosing_phone_sideways.png")
    }

    /**
     * With a Kobo plugged in (so Sync's there too), every button's on the bar, in one line, with its label under its
     * icon, and nothing's left for a More button.
     */
    private fun everyButtonOnOneLine(shot: String) {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selecting = true; ids.forEach { picked[it] = true } }
        show(browse, lib, covers)
        val bar = compose.onNodeWithTag("selection-bar").fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag("selection-more", useUnmergedTree = true).assertCountEquals(0)
        // All chosen: Deselect all in Select all's place, and Invert beside it.
        val tags = listOf(
            "deselect-all", "invert-selection", "selection-send", "selection-mark-read", "selection-mark-unread", "selection-sync-reading",
            "selection-save", "selection-duplicate", "selection-combine", "selection-remove",
        )
        for (tag in tags) {
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assert(b.left >= bar.left && b.right <= bar.right + 0.5f && b.top >= bar.top && b.bottom <= bar.bottom + 0.5f) { "$tag at $b, outside the bar at $bar" }
        }
        // Each button's label is under its icon.
        val read = compose.onNodeWithTag("selection-mark-read", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assert(read.height > bar.height * 0.7f) { "Read is laid out sideways: $read in $bar" }
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$shot")
    }

    @Test
    fun deselectAllLeavesNoneChosenButStillChoosing() {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selecting = true; picked[ids[0]] = true }
        show(browse, lib, covers)
        compose.onNodeWithTag("select-all").performClick()
        compose.runOnIdle { assertEquals(ids.toSet(), browse.picked.keys.toSet()) }
        compose.onNodeWithTag("deselect-all").performClick()
        compose.runOnIdle {
            assertEquals(emptySet<String>(), browse.picked.keys.toSet())
            assertTrue(browse.selecting)
        }
        // None chosen, it's Select all again.
        compose.onAllNodesWithTag("deselect-all").assertCountEquals(0)
        compose.onNodeWithTag("select-all").assertExists()
    }

    /** As in Mihon: what's chosen isn't, and what isn't is. */
    @Test
    fun invertSwapsWhatsChosen() {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selecting = true; picked[ids[0]] = true }
        show(browse, lib, covers)
        compose.onNodeWithTag("invert-selection").performClick()
        compose.runOnIdle { assertEquals(ids.drop(1).toSet(), browse.picked.keys.toSet()) }
        compose.onNodeWithTag("invert-selection").performClick()
        compose.runOnIdle { assertEquals(setOf(ids[0]), browse.picked.keys.toSet()) }
    }

    /**
     * The row of books being read: held there, a book's chosen, as on the shelf; then a tap chooses another, and they're
     * marked unread together.
     */
    @Test
    fun booksInTheContinueReadingRowAreChosenByHolding() {
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState()
        compose.setContent {
            AppSurface {
                LibraryScreen(
                    books = lib.books.value, covers = covers, browse = browse, job = null, koboConnected = true,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                    onMarkFinished = { read += it }, onMarkUnread = { unread += it }, onSyncReading = {},
                    onRead = {},
                    readingHere = mapOf(ids[0] to dev.kolib.app.reader.ReadHere(0.3f, 100L), ids[1] to dev.kolib.app.reader.ReadHere(0.6f, 200L)),
                )
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("continue-book").assertCountEquals(2)
        compose.onAllNodesWithTag("continue-book")[0].performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(browse.selecting)
        // Still there while choosing, a tap chooses another.
        compose.onAllNodesWithTag("continue-book")[1].performClick()
        compose.runOnIdle { assertEquals(setOf(ids[0], ids[1]), browse.picked.keys.toSet()) }
        compose.onNodeWithTag("selection-mark-unread").performClick()
        compose.runOnIdle { assertEquals(setOf(ids[0], ids[1]), unread.single().toSet()) }
    }

    /**
     * On a wide screen, a book's details open beside the shelf, narrowing it: the book tapped stays on the screen, and
     * closed, the shelf is back where it was.
     */
    @Test
    fun theShelfKeepsTheBookTappedOnScreenAndGoesBackWhenClosed() {
        val name = "keep-" + UUID.randomUUID()
        val lib = Library(context, File(context.filesDir, name), "$name.db").apply { runBlocking { load() } }
        runBlocking {
            for (n in 1..80) {
                val t = "Book %02d".format(n)
                val f = FakeKobo.epub(File(context.cacheDir, "$name-$n.epub"), FakeKobo.Book(t, "Author", mime = "application/epub+zip"))
                lib.import("$t.epub", { f.inputStream() }, BookSource.FILE)
            }
        }
        val browse = LibraryBrowseState()
        show(browse, lib, LibraryCovers(lib))
        compose.runOnIdle { runBlocking { browse.gridState.scrollToItem(30) } }
        compose.waitForIdle()
        val before = browse.gridState.firstVisibleItemIndex to browse.gridState.firstVisibleItemScrollOffset
        // The last book wholly on the screen, at the end of a row: furthest from where it'll be with fewer to a row.
        val info = browse.gridState.layoutInfo
        val last = info.visibleItemsInfo.last { it.offset.y + it.size.height <= info.viewportEndOffset }
        val id = last.key as String
        compose.runOnIdle { browse.selectedId = id }
        compose.waitForIdle()
        val after = browse.gridState.layoutInfo
        assertTrue(
            "the book tapped is on the screen",
            after.visibleItemsInfo.any { it.key == id && it.offset.y >= after.viewportStartOffset && it.offset.y + it.size.height <= after.viewportEndOffset },
        )
        compose.onNodeWithTag("side-pane-close").performClick()
        compose.waitForIdle()
        assertEquals(before, browse.gridState.firstVisibleItemIndex to browse.gridState.firstVisibleItemScrollOffset)
    }
}
