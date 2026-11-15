package dev.kobolibrary.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReaderSettings
import dev.kobolibrary.app.reader.RunningHeads
import dev.kobolibrary.app.ui.reader.Panel
import dev.kobolibrary.app.ui.reader.ReaderScreen
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The page headings over pages of Moby Dick laid out by the app's reader in Chrome (/home/claude/heads/render.py),
 * as they'll look on the tablet and the phone. Only runs where those pages are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeadingShots {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = File("/home/claude/heads")
    private val out = File(root, "shots")

    private fun counted(c: ReaderController) {
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(1200) {
            if (c.lengthsCounted) return
            looper.idle()
            Thread.sleep(25)
        }
    }

    private fun shot(
        name: String, page: String, pageIndex: Int, pages: Int, columns: Int, twoUp: Boolean, settings: ReaderSettings = ReaderSettings(),
        panel: Panel = Panel.NONE, prepare: (ReaderController) -> Unit = {}, file: String = "chapter-1.xhtml", blank: Int = 0,
        display: Boolean = false,
    ) {
        val epubFile = File("/home/claude/advert/books/herman-melville_moby-dick.epub")
        val img = File(root, "pages/$page.png")
        assumeTrue(epubFile.exists() && img.exists())
        EpubFile.open(epubFile).use { epub ->
            val files = EpubFileFiles(epub)
            // A title as a file-sorted library might have it: the headings leave its number off.
            val c = ReaderController(files, BookStructure(files), "01 Moby Dick", "en", null, settings, context.assets, "Herman Melville")
            val at = c.chapters.indexOfFirst { it.path.endsWith(file) }
            counted(c)
            c.showForPreview(at, pageIndex, pages, columns, twoUp, blank, display)
            prepare(c)
            val bitmap = BitmapFactory.decodeFile(img.path).asImageBitmap()
            out.mkdirs()
            compose.mainClock.autoAdvance = false
            compose.setContent {
                AppSurface(ThemeChoice.BOOKCASE) {
                    ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, initialPanel = panel,
                        pagePreview = { Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) })
                }
            }
            compose.mainClock.advanceTimeBy(50)
            c.showForPreview(at, pageIndex, pages, columns, twoUp, blank, display)
            compose.mainClock.advanceTimeBy(900)
            captureScreenRoboImage(File(out, "$name.png").path)
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletSpread() = shot("tab_spread", "tab_1", 1, 6, 11, true, blank = 1)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletOpening() = shot("tab_opening", "tab_0", 0, 6, 11, true, blank = 1)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletAtTheBottom() = shot("tab_bottom", "tabb_1", 1, 6, 10, true, ReaderSettings().withHeads(RunningHeads.BOTTOM), blank = 1)

    /** The headings as they were: smaller than the text, close over it. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletSmallHeadings() = shot(
        "tab_small", "tabs_1", 1, 6, 10, true, ReaderSettings(headSize = dev.kobolibrary.app.reader.HeadSize.SMALL, headGap = dev.kobolibrary.app.reader.HeadGap.CLOSE), blank = 1,
    )

    /** Bigger headings, with more room under them. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLargeHeadings() = shot(
        "tab_large", "tabl_1", 1, 6, 11, true, ReaderSettings(headSize = dev.kobolibrary.app.reader.HeadSize.LARGE, headGap = dev.kobolibrary.app.reader.HeadGap.MORE), blank = 1,
    )

    /** The headings' titles in the middle of their pages, rather than toward the spine as print often has them. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletCentred() = shot("tab_centred", "tab_1", 1, 6, 11, true, ReaderSettings(headTitlePlace = dev.kobolibrary.app.reader.HeadTitlePlace.CENTRED), blank = 1)

    /** A page on its own (the dedication): the right-hand one, with no heading over it, not even its number. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletPageOnItsOwn() = shot("tab_lone", "tabd_0", 0, 1, 1, true, file = "dedication.xhtml", blank = 1, display = true)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneLeftHand() = shot("phone_2", "phone_2", 2, 14, 14, false)

    /** A chapter's first page: no heading over it, its number at the foot. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneOpening() = shot("phone_0", "phone_0", 0, 14, 14, false)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun phoneRightHand() = shot("phone_3", "phone_3", 3, 14, 14, false)

    /** The page's settings, tall enough to show the headings' choice and the margins under it. */
    @Test
    @Config(qualifiers = "w411dp-h1800dp-port-xxhdpi")
    fun phoneSettings() = shot("phone_settings", "phone_3", 3, 14, 14, false, panel = Panel.LOOK, prepare = { it.lookTab = dev.kobolibrary.app.ui.reader.LookTab.PAGE.ordinal })

    /** The layout settings, with the choices for chapters' first pages. */
    @Test
    @Config(qualifiers = "w411dp-h1800dp-port-xxhdpi")
    fun phoneLayoutSettings() = shot(
        "phone_layout", "phone_3", 3, 14, 14, false, panel = Panel.LOOK, prepare = { it.lookTab = dev.kobolibrary.app.ui.reader.LookTab.LAYOUT.ordinal },
    )

    @Test
    @Config(qualifiers = "w411dp-h1800dp-port-xxhdpi")
    fun phoneSettingsOff() = shot(
        "phone_settings_off", "phone_3", 3, 14, 14, false, settings = ReaderSettings().withHeads(RunningHeads.OFF), panel = Panel.LOOK,
        prepare = { it.lookTab = dev.kobolibrary.app.ui.reader.LookTab.PAGE.ordinal },
    )
}
