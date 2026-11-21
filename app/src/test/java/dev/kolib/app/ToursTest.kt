package dev.kolib.app

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kolib.app.comic.TestComics
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.ui.AppRoot
import dev.kolib.app.ui.reader.ComicBookReader
import dev.kolib.app.ui.reader.ReaderScreen
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.KoLibTheme
import dev.kolib.app.ui.tour.Tours
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

private const val PHONE = "w411dp-h914dp-port-420dpi"
private const val TABLET = "w1280dp-h800dp-land-xhdpi"
/** A phone held sideways: the top of each screen in one row, which slides away as the books are scrolled. */
private const val SIDEWAYS = "w914dp-h387dp-land-420dpi"

/**
 * The tours of the app's screens, each given the first time its screen opens and then not again, and the sample books
 * the app comes with. Pictures of each stop go to /tmp/tour_shots, where that folder is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToursTest {

    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<KoLibApp>()
    private var seen by mutableStateOf(setOf(Tours.SAMPLES))
    private var off by mutableStateOf(false)
    private val shots = File("/tmp/tour_shots")

    private fun withABook() = runBlocking {
        if (!app.library.loaded) app.library.load()
        val f = TestBooks.garden(File(app.cacheDir, "g-${UUID.randomUUID()}.epub"))
        app.library.import("garden.epub", { f.inputStream() }, BookSource.FILE)
    }

    private fun showApp() {
        val vm = MainViewModel(app)
        val libraryVm = LibraryViewModel(app)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            KoLibTheme {
                AppRoot(
                    vm, libraryVm, toursSeen = seen, onTourSeen = { seen = seen + it }, onShowToursAgain = { seen = setOf(Tours.SAMPLES); off = false },
                    toursOff = off, onToursOff = { off = it },
                )
            }
        }
    }

    /** Goes through the tour showing, to its end; gives its stops' titles. */
    private fun goThrough(name: String): List<String> {
        val titles = ArrayList<String>()
        var n = 1
        while (n <= 14) {
            compose.mainClock.advanceTimeBy(900)
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("tour-next").fetchSemanticsNodes().isEmpty()) break
            compose.onNodeWithTag("tour-card").assertIsDisplayed()
            titles += compose.onAllNodesWithTag("tour-title").fetchSemanticsNodes().last().config[SemanticsProperties.Text].joinToString("") { it.text }
            if (shots.exists()) captureScreenRoboImage(File(shots, "${name}_$n.png").path)
            compose.onNodeWithTag("tour-next").performClick()
            n++
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertTrue("the tour is gone", compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
        return titles
    }

    @Test
    @Config(qualifiers = PHONE)
    fun onAPhoneTheLibrarysTourGoesThroughItsPartsOnce() {
        withABook()
        showApp()
        assertEquals(
            listOf("Your library", "Books, collections and comics", "Find a book", "Filter, sort and display", "Select and sync", "Add books", "Your Kobo", "Your other devices", "More"),
            goThrough("library_phone"),
        )
        assertTrue(Tours.LIBRARY in seen)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertTrue("given once", compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = TABLET)
    fun onATabletTheLibrarysTourGoesThroughItsParts() {
        withABook()
        showApp()
        assertEquals(
            listOf("Your library", "Books, collections and comics", "Find a book", "Filter, sort and display", "Add books", "Your Kobo", "Your other devices", "More"),
            goThrough("library_tablet"),
        )
        assertTrue(Tours.LIBRARY in seen)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun theLibrarysTourWaitsForTheSampleBooks() {
        seen = emptySet()
        withABook()
        showApp()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        // The library has a book already, so no samples are added; once that's settled, the tour starts.
        repeat(200) {
            if (Tours.SAMPLES in seen) return@repeat
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue(Tours.SAMPLES in seen)
        assertEquals(1, app.library.books.value.size)
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        compose.onNodeWithTag("tour-card").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun everyTourCanTurnThemAllOffAndSettingsTurnsThemOnAgain() {
        withABook()
        showApp()
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        compose.onNodeWithTag("tour-card").assertIsDisplayed()
        compose.onNodeWithTag("tour-off").performClick()
        compose.waitForIdle()
        assertTrue("tours are off", off)
        assertTrue("the tour is gone", compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
        // Not given when they're off, the library's included, though it was never finished.
        assertTrue(Tours.LIBRARY !in seen)
        compose.onNodeWithText("Devices").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
        // On again in Settings (in More): the library's tour is given once it shows again.
        compose.onNodeWithTag("nav-more").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-settings").performClick()
        compose.waitForIdle()
        assertTrue("no Settings tour while they're off", compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("settings-tours-on").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("tours are on", !off)
        // Back from the Settings page, and to the library.
        compose.onNodeWithTag("settings-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Library").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        compose.onNodeWithTag("tour-card").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun theKoboAndDevicesSectionsHaveToursOfTheirOwn() {
        seen = setOf(Tours.SAMPLES, Tours.LIBRARY)
        withABook()
        showApp()
        compose.onNodeWithText("Kobo").performClick()
        assertEquals(listOf("Connect your Kobo", "Backups and the log"), goThrough("kobo_connect"))
        compose.onNodeWithText("Devices").performClick()
        assertEquals(listOf("This device", "Paired", "Nearby", "Your Kobo, over Wi-Fi", "Settings"), goThrough("devices"))
        assertTrue(seen.containsAll(listOf(Tours.KOBO_CONNECT, Tours.DEVICES)))
        // Back in the library, whose tour was given: no tour.
        compose.onNodeWithText("Library").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("tour").fetchSemanticsNodes().isEmpty())
    }

    /** On a phone held sideways, every tour finds its parts in the one-row top, which stays put while it's given. */
    @Test
    @Config(qualifiers = SIDEWAYS)
    fun onAPhoneHeldSidewaysEveryTourFindsItsParts() {
        withABook()
        showApp()
        assertEquals(
            listOf("Your library", "Books, collections and comics", "Find a book", "Filter, sort and display", "Add books", "Your Kobo", "Your other devices", "More"),
            goThrough("library_sideways"),
        )
        compose.onNodeWithText("Kobo").performClick()
        assertEquals(listOf("Connect your Kobo", "Backups and the log"), goThrough("kobo_connect_sideways"))
        compose.onNodeWithText("Devices").performClick()
        assertEquals(listOf("This device", "Paired", "Nearby", "Your Kobo, over Wi-Fi", "Settings"), goThrough("devices_sideways"))
        // Settings is in More.
        compose.onNodeWithTag("nav-more").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-settings").performClick()
        assertEquals(listOf("Language", "Books folder", "Reading setup", "AI and dictionary", "Comics", "Look", "Tours"), goThrough("settings_sideways"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun onAPhoneTheDevicesTourScrollsToTheSettingsAtTheFoot() {
        seen = setOf(Tours.SAMPLES, Tours.LIBRARY)
        withABook()
        showApp()
        compose.onNodeWithText("Devices").performClick()
        assertEquals(listOf("This device", "Paired", "Nearby", "Your Kobo, over Wi-Fi", "Settings"), goThrough("devices_phone"))
    }

    @Test
    @Config(qualifiers = TABLET)
    fun settingsHaveATourAndCanGiveEveryTourAgain() {
        seen = setOf(Tours.SAMPLES, Tours.LIBRARY)
        withABook()
        showApp()
        compose.onNodeWithTag("nav-more").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-settings").performClick()
        assertEquals(listOf("Language", "Books folder", "Reading setup", "AI and dictionary", "Comics", "Look", "Tours"), goThrough("settings"))
        assertTrue(Tours.SETTINGS in seen)
        // Every tour again: Settings close, and the library's tour starts again at once.
        compose.onNodeWithTag("settings-tours").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(setOf(Tours.SAMPLES), seen)
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        compose.onNodeWithTag("tour-card").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun theBookReadersTourShowsTheBarsAndIsDoneOnce() {
        val f = TestBooks.garden(File(app.cacheDir, "garden-${UUID.randomUUID()}.epub"))
        val files = EpubFileFiles(EpubFile.open(f))
        val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, ReaderSettings(), app.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = false
        var done = 0
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface {
                ReaderScreen(
                    c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { Box(Modifier.fillMaxSize().background(Color(0xFFFBF7EF))) },
                    tour = true, onTourDone = { done++ },
                )
            }
        }
        assertEquals(
            listOf(
                "Turn the page", "Highlights and notes", "Search and bookmark", "Move through the book", "Contents", "Autoscroll",
                "Rotation", "Reading settings",
            ),
            goThrough("book_reader"),
        )
        assertEquals(1, done)
        // The bars it showed are there to use.
        assertTrue(c.controlsVisible)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun theComicReadersTourShowsTheBarsAndIsDoneOnce() {
        val file = TestComics.comic(File(app.cacheDir, "lantern-${UUID.randomUUID()}.cbz"), pages = 6, info = false)
        runBlocking {
            if (!app.library.loaded) app.library.load()
            app.library.import("lantern.cbz", { file.inputStream() }, BookSource.FILE)
        }
        val book = app.library.books.value.single()
        var done = 0
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface {
                ComicBookReader(book, app.library, app.readerSettings, onClose = {}, tour = true, onTourDone = { done++ })
            }
        }
        repeat(40) {
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(50)
            compose.waitForIdle()
        }
        assertEquals(
            listOf("Turn the page", "Hold a page", "Move through the comic", "Reading mode", "Rotation", "Crop borders", "Settings"),
            goThrough("comic_reader"),
        )
        assertEquals(1, done)
        compose.onNodeWithTag("comic-bottom-bar").assertIsDisplayed()
    }

    // ---- the sample books ------------------------------------------------------------------------

    private fun addSamples(vm: LibraryViewModel, onlyIfEmpty: Boolean): Int {
        var added = -1
        vm.addSamples(onlyIfEmpty) { added = it }
        // Up to a minute: four books and their covers, slower on a busy machine.
        repeat(3_000) {
            if (added >= 0) return added
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        error("The samples took too long")
    }

    @Test
    fun theSampleBooksAreAddedToAnEmptyLibraryAndNeverTwice() {
        val vm = LibraryViewModel(app)
        assertEquals(4, addSamples(vm, onlyIfEmpty = true))
        val books = app.library.books.value
        assertEquals(
            setOf("Alice's Adventures in Wonderland", "Persuasion", "The Wisdom of Father Brown", "The Lighthouse Cat"),
            books.map { it.title }.toSet(),
        )
        assertTrue("in a collection of their own", books.all { "Sample books" in it.collections })
        assertTrue("with covers", books.all { it.hasCover })
        val comic = books.single { it.isComic }
        assertEquals("The Lighthouse Cat", comic.meta.series)
        assertEquals(listOf("Lewis Carroll"), books.single { it.title.startsWith("Alice") }.meta.authors)
        // Asked for again, from the empty library's button: none is added twice.
        assertEquals(0, addSamples(vm, onlyIfEmpty = false))
        assertEquals(4, app.library.books.value.size)
    }

    /** With books in the library already, the samples are in the Add books menu (the sheet on a phone). */
    private fun samplesFromTheAddMenu() {
        withABook()
        var asked = 0
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface {
                dev.kolib.app.ui.library.LibraryScreen(
                    books = app.library.books.value, covers = dev.kolib.app.library.LibraryCovers(app.library),
                    browse = dev.kolib.app.ui.library.LibraryBrowseState(), job = null, koboConnected = false,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                    onAddSamples = { asked++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Add books", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sample books").performClick()
        compose.waitForIdle()
        assertEquals(1, asked)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun theSampleBooksAreInTheAddBooksMenu() = samplesFromTheAddMenu()

    @Test
    @Config(qualifiers = PHONE)
    fun theSampleBooksAreInThePhonesAddBooksSheet() = samplesFromTheAddMenu()

    @Test
    fun theFirstTimeTheyAreNotAddedToALibraryWithBooks() {
        withABook()
        val vm = LibraryViewModel(app)
        assertEquals(0, addSamples(vm, onlyIfEmpty = true))
        assertEquals(1, app.library.books.value.size)
    }
}
