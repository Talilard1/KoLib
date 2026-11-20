package dev.kolib.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicMode
import dev.kolib.app.comic.ComicOwn
import dev.kolib.app.comic.ComicPages
import dev.kolib.app.comic.ComicRotation
import dev.kolib.app.comic.ComicSettings
import dev.kolib.app.comic.TestComics
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.reader.PdfPlace
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.ui.reader.ComicView
import dev.kolib.app.ui.reader.Neighbour
import dev.kolib.app.ui.reader.OpenComic
import dev.kolib.app.ui.reader.SETTINGS_TAB_FILTER
import dev.kolib.app.ui.reader.SETTINGS_TAB_GENERAL
import dev.kolib.app.ui.reader.SETTINGS_TAB_MODE
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The comic reader's bars, sheets and the page between comics, as pictures, to compare with Mihon's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicBarsShots {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun comic(name: String, title: String, pages: Int, at: Int, many: Boolean = false): OpenComic {
        val dir = File(context.cacheDir, "comic-bars-$name").apply { deleteRecursively(); mkdirs() }
        val file = if (many) {
            val p = TestComics.page(2, 300, 450)
            TestComics.zip(File(dir, "$name.cbz"), (1..pages).map { "p${"%04d".format(it)}.jpg" to p })
        } else TestComics.comic(File(dir, "$name.cbz"), pages = pages)
        val book = LibraryBook(
            id = name, meta = BookMetadata(title = title, series = "Harbour Lights"), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = BookSource.FILE, originalName = "$name.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        ReaderStore.savePdfPlace(dir, PdfPlace(at, pages))
        return OpenComic(book, ComicPages(ComicArchives.open(file, libArchive = null)), dir, name = title.substringAfterLast(' ').let { "#${it.trimStart('0')}" })
    }

    private fun settle() {
        repeat(8) {
            Thread.sleep(300)
            compose.waitForIdle()
        }
    }

    private fun show(
        s: ComicSettings = ComicSettings(),
        menu: Boolean = true,
        pages: Int = 24,
        at: Int = 0,
        many: Boolean = false,
        next: Boolean = true,
        own: ComicOwn = ComicOwn(),
        settingsTab: Int? = null,
    ) {
        val a = comic("issue1", "Harbour Lights 001", pages, at, many)
        val b = comic("issue2", "Harbour Lights 002", 8, 0)
        compose.setContent {
            var shown by remember { mutableStateOf(menu) }
            AppSurface(ThemeChoice.BOOKCASE) {
                ComicView(
                    comic = a, s = s, mode = own.mode ?: s.mode, menu = shown, setMenu = { shown = it }, onSettings = {}, onClose = {},
                    onCover = { _, _ -> }, readingHere = null, series = "Harbour Lights",
                    after = if (next) Neighbour(b.id, b.name, b) else null, own = own,
                    settingsOpen = settingsTab != null, settingsTab = settingsTab ?: SETTINGS_TAB_MODE,
                )
            }
        }
        settle()
    }

    private fun shot(name: String) = captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun tabletMenu() {
        show(pages = 126)
        shot("comicbars_tablet_menu")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLandscapeManyPages() {
        show(pages = 338, many = true)
        shot("comicbars_tablet_338")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLandscapeVeryManyPages() {
        show(pages = 838, many = true)
        shot("comicbars_tablet_838")
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneMenuRightToLeft() {
        show(ComicSettings(mode = ComicMode.RIGHT_TO_LEFT), pages = 40, at = 9)
        shot("comicbars_phone_rtl")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun modeSheet() {
        show()
        compose.onNodeWithTag("comic-mode-button").performClick()
        settle()
        shot("comicbars_mode_sheet")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun rotationSheet() {
        show(own = ComicOwn(rotation = ComicRotation.PORTRAIT))
        compose.onNodeWithTag("comic-rotation-button").performClick()
        settle()
        shot("comicbars_rotation_sheet")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun settingsReadingMode() {
        show(settingsTab = SETTINGS_TAB_MODE)
        shot("comicbars_settings_mode")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun settingsGeneral() {
        show(ComicSettings(flash = true), settingsTab = SETTINGS_TAB_GENERAL)
        shot("comicbars_settings_general")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun settingsFilter() {
        show(ComicSettings(colourFilter = true, colour = 0x6966FFFF.toInt(), customBrightness = true, brightness = -20), settingsTab = SETTINGS_TAB_FILTER)
        shot("comicbars_settings_filter")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun uprightSlider() {
        show(ComicSettings(verticalIn = setOf(ComicMode.LEFT_TO_RIGHT)), pages = 60, at = 20)
        shot("comicbars_upright")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun pageBetweenComics() {
        show(menu = false, pages = 6, at = 5)
        // On past the last page: the page between this comic and the next.
        compose.onRoot().performTouchInput { swipeLeft(startX = 700f, endX = 100f, durationMillis = 300) }
        settle()
        shot("comicbars_between")
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun greyscaleAndInverted() {
        show(ComicSettings(greyscale = true, inverted = true), menu = false)
        shot("comicbars_filtered")
    }
}
