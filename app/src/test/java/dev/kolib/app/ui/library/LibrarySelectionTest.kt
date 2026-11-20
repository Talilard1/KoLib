package dev.kolib.app.ui.library

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
        // The rest are a tap away, under More.
        compose.runOnIdle { browse.selecting = true; ids.forEach { browse.picked[it] = true } }
        compose.onNode(hasText("More") and hasClickAction()).performClick()
        compose.onNodeWithText("Save as files").assertExists()
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
        val (lib, covers) = library()
        val ids = lib.books.value.map { it.id }
        val browse = LibraryBrowseState().apply { selecting = true; ids.forEach { picked[it] = true } }
        show(browse, lib, covers)
        // Its title and its last button are on the same line.
        val title = compose.onNodeWithText("3 books selected").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithText("Send to Kobo").fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(title.center.y - send.center.y) < 12f) { "Title at ${title.center.y}, Send to Kobo at ${send.center.y}" }
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/library_choosing_one_line.png")
    }
}
