package dev.kolib.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicPages
import dev.kolib.app.comic.ComicSettings
import dev.kolib.app.comic.TestComics
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.ComicSeries
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.reader.PdfPlace
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibraryScreen
import dev.kolib.app.ui.reader.Neighbour
import dev.kolib.app.ui.reader.OpenComic
import dev.kolib.app.ui.reader.ComicView
import dev.kolib.app.ui.reader.PageSettings
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pictures of the Comics tab, a series' page, the end of a comic, and the margins' settings, to look at. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicsShots {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A library of comics, in series as the app gives them when they're added, and a book. */
    private fun library(): Pair<Library, LibraryCovers> = runBlocking {
        val name = "comics-shots-" + UUID.randomUUID()
        val lib = Library(context, File(context.filesDir, name), "$name.db").apply { load() }
        val dir = File(context.cacheDir, name).apply { mkdirs() }
        var k = 0
        fun add(file: String, pages: Int = 3) = runBlocking {
            val f = TestComics.comic(File(dir, "c${k++}.cbz"), pages = pages, info = false)
            lib.import(file, { f.inputStream() }, BookSource.FILE)
        }
        for (n in 1..6) add("Saga ${"%03d".format(n)}.cbz")
        for (n in 1..2) add("Monstress ${"%03d".format(n)}.cbz")
        add("Watchmen.cbz")
        add("Akira v01.cbz")
        add("Akira v02.cbz")
        val given = ComicSeries.assignAll(lib.books.value.filter { it.isComic })
        for ((id, g) in given) {
            val b = lib.book(id)!!
            lib.updateDetails(id, b.meta.copy(series = g.series, seriesIndex = g.index, authors = listOf("Brian K. Vaughan")), b.collections, unchanged = true)
        }
        val covers = LibraryCovers(lib)
        for (b in lib.books.value) covers.load(b, false, 300)
        lib to covers
    }

    private fun here(lib: Library): Map<String, ReadHere> {
        val saga = lib.books.value.filter { it.meta.series == "Saga" }.sortedBy { it.meta.seriesIndex }
        return mapOf(saga[0].id to ReadHere(1f, 10), saga[1].id to ReadHere(1f, 20), saga[2].id to ReadHere(0.4f, 30))
    }

    private fun shot(name: String) {
        repeat(6) {
            Thread.sleep(200)
            compose.waitForIdle()
        }
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    private fun screen(lib: Library, covers: LibraryCovers, browse: LibraryBrowseState) {
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                LibraryScreen(
                    books = lib.books.value, covers = covers, browse = browse, job = null, koboConnected = true,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                    readingHere = here(lib),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletComicsWithASeriesOpen() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2; comicKey = "s:saga" })
        shot("comics_tablet_series")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletSeriesInPlaceAsCovers() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2; comicKey = "s:saga"; seriesInPlace = true; seriesGrid = true })
        shot("comics_tablet_inplace_grid")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletSeriesInPlaceAsAList() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2; comicKey = "s:saga"; seriesInPlace = true })
        shot("comics_tablet_inplace_list")
    }

    /** A comic held in its series: the bar for what's chosen is along the foot of the screen. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletSeriesInPlaceWithAComicChosen() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2; comicKey = "s:saga"; seriesInPlace = true })
        compose.waitForIdle()
        compose.onAllNodesWithTag("comic-issue")[1].performTouchInput { longClick() }
        shot("comics_tablet_inplace_chosen")
    }

    /** Series held on the shelf: the bar for them along the foot, the search still at the top. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletShelfWithASeriesChosen() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2 })
        compose.waitForIdle()
        compose.onNodeWithText("Saga").performTouchInput { longClick() }
        shot("comics_tablet_shelf_chosen")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletShelfInPlace() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2; seriesInPlace = true })
        shot("comics_tablet_inplace_shelf")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletAddToSeries() {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { tab = 2 }
        screen(lib, covers, browse)
        compose.waitForIdle()
        compose.onNodeWithText("Watchmen").performTouchInput { longClick() }
        compose.onNodeWithTag("comics-add-to-series").performClick()
        shot("comics_add_to_series")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletRemoveSeries() {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { tab = 2 }
        screen(lib, covers, browse)
        compose.waitForIdle()
        compose.onNodeWithText("Saga").performTouchInput { longClick() }
        compose.onNodeWithText("Remove").performClick()
        shot("comics_remove_series")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun settingsWithTheLibraryChoices() {
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                dev.kolib.app.ui.LookDialog(current = ThemeChoice.SYSTEM, onPick = {}, onDismiss = {}, comicsTab = true, seriesInPlace = true)
            }
        }
        shot("comics_settings_library")
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneComicsShelf() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2 })
        shot("comics_phone_shelf")
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneSeriesPage() {
        val (lib, covers) = library()
        screen(lib, covers, LibraryBrowseState().apply { tab = 2; comicKey = "s:saga" })
        shot("comics_phone_series")
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun theEndOfAComic() {
        val dir = File(context.cacheDir, "comic-end-shot").apply { deleteRecursively(); mkdirs() }
        val file = TestComics.comic(File(dir, "saga.cbz"), pages = 3)
        val book = dev.kolib.app.library.LibraryBook(
            id = "saga-1", meta = dev.kolib.app.epub.BookMetadata(title = "Saga 001", series = "Saga", seriesIndex = 1.0), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = BookSource.FILE, originalName = "Saga 001.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        ReaderStore.savePdfPlace(dir, PdfPlace(2, 3))
        val pages = ComicPages(ComicArchives.open(file, libArchive = null))
        val next = ComicPages(ComicArchives.open(file, libArchive = null))
        val first = OpenComic(book, pages, dir, name = "#1")
        val second = OpenComic(book.copy(id = "saga-2"), next, File(dir, "2"), name = "#2")
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                ComicView(
                    comic = first, s = ComicSettings(), mode = ComicSettings().mode, menu = false, setMenu = {}, onSettings = {}, onClose = {},
                    onCover = { _, _ -> }, readingHere = null, series = "Saga", after = Neighbour(second.id, second.name, second),
                )
            }
        }
        shot("comics_end_before")
        compose.onRoot().performTouchInput { click(Offset(width * 0.92f, height * 0.5f)) }
        compose.mainClock.advanceTimeBy(1_000)
        shot("comics_end")
        pages.close()
        next.close()
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun marginSettings() {
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                Column(Modifier.verticalScroll(rememberScrollState(1800)).padding(20.dp)) { PageSettings(ReaderSettings(), {}, camera = true) }
            }
        }
        shot("margins_settings")
    }
}
