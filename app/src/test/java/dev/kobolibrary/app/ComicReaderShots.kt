package dev.kobolibrary.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.comic.ComicArchives
import dev.kobolibrary.app.comic.ComicMode
import dev.kobolibrary.app.comic.ComicPages
import dev.kobolibrary.app.comic.ComicSettings
import dev.kobolibrary.app.comic.TestComics
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.PdfPlace
import dev.kobolibrary.app.reader.ReaderStore
import dev.kobolibrary.app.ui.reader.ComicView
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The comic reader, with a made-up comic: pages one at a time, the long strip, the menu and the settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicReaderShots {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, s: ComicSettings, menu: Boolean = false, settings: Boolean = false, mode: ComicMode = s.mode) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "comic-shot-$name").apply { deleteRecursively(); mkdirs() }
        val file = TestComics.comic(File(dir, "lantern.cbz"), pages = 8)
        val book = LibraryBook(
            id = "comic", meta = BookMetadata(title = "The Lantern Thief", authors = listOf("Ada Fenwick")), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = dev.kobolibrary.app.library.BookSource.FILE, originalName = "lantern.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        ReaderStore.savePdfPlace(dir, PdfPlace(2, 8))
        val pages = ComicPages(ComicArchives.open(file, libArchive = null))
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                ComicView(
                    book = book, pages = pages, dir = dir, s = s, mode = mode, menu = menu, setMenu = {},
                    onMode = {}, onSettings = {}, onClose = {}, onCover = {}, readingHere = null, onAppSettings = {}, settingsOpen = settings,
                )
            }
        }
        repeat(10) {
            Thread.sleep(400)
            compose.waitForIdle()
        }
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
        pages.close()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletPaged() = shoot("comic_tablet_paged", ComicSettings())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletPagedMenu() = shoot("comic_tablet_menu", ComicSettings(mode = ComicMode.RIGHT_TO_LEFT), menu = true)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLtrMenu() = shoot("comic_tablet_ltr_menu", ComicSettings(), menu = true)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phonePaged() = shoot("comic_phone_paged", ComicSettings(cropPaged = true))

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneStrip() = shoot("comic_phone_strip", ComicSettings(mode = ComicMode.STRIP_GAPS, sidePadding = 5))

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneSettings() = shoot("comic_phone_settings", ComicSettings(), settings = true)
}
