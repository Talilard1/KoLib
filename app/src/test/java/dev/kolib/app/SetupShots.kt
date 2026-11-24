package dev.kolib.app

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
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.reader.PageTurn
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderFlow
import dev.kolib.app.reader.ReaderFont
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderTheme
import dev.kolib.app.ui.setup.ExampleBook
import dev.kolib.app.ui.setup.ReadingSetupScreen
import dev.kolib.app.ui.setup.SetupStep
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PHONE = "w411dp-h914dp-port-420dpi"
private const val TABLET = "w1280dp-h800dp-land-xhdpi"
private const val TABLET_UP = "w800dp-h1280dp-port-xhdpi"
private const val PHONE_ACROSS = "w914dp-h411dp-land-420dpi"
/** A smaller phone on its side: not wide enough for two pages. */
private const val SMALL_ACROSS = "w780dp-h360dp-land-xhdpi"

/**
 * The reading setup's steps on a phone and a tablet, over the example book's page as the app's own reader
 * lays it out: [starts] writes the book and what the page is told for each look to /home/claude/setup,
 * render_setup.py there lays them out in Chrome, and the other tests show the setup over those pages (or over
 * a blank page, where they aren't). Pictures go to /home/claude/setup/shots. Only runs where that folder is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupShots {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private val home = File("/home/claude/setup")
    private val lang: String get() = TestLanguage.code ?: "en"
    private val out: File get() = File(home, TestLanguage.code?.let { "shots/lang_$it" } ?: "shots")

    @Before
    fun inLanguage() = TestLanguage.apply()

    /** A look to show the setup with: the reader's settings, and the screen they're on. */
    private class Look(val name: String, val settings: ReaderSettings, val width: Int, val height: Int, val scale: Double)

    private val phone = ReaderSettings.PHONE
    private val tablet = ReaderSettings()
    private val looks = listOf(
        Look("phone", phone, 411, 914, 2.625),
        Look("phone_sepia", phone.copy(theme = ReaderTheme.SEPIA), 411, 914, 2.625),
        Look("phone_night", phone.copy(theme = ReaderTheme.NIGHT, font = ReaderFont.HYPERLEGIBLE), 411, 914, 2.625),
        Look("phone_big", phone.copy(fontScale = 1.45f, font = ReaderFont.GARAMOND), 411, 914, 2.625),
        Look("phone_scroll", phone.copy(flow = ReaderFlow.SCROLL), 411, 914, 2.625),
        Look("phone_across", phone, 914, 411, 2.625),
        Look("small_across", phone, 780, 360, 2.0),
        Look("tablet", tablet, 1280, 800, 2.0),
        Look("tablet_slate", tablet.copy(theme = ReaderTheme.SLATE), 1280, 800, 2.0),
        Look("tablet_up", tablet, 800, 1280, 2.0),
    )

    private fun controller(settings: ReaderSettings): ReaderController {
        val files = ExampleBook.files()
        return ReaderController(files, BookStructure(files), ExampleBook.title, L10n.language, null, settings, context.assets)
    }

    /** The example book's files, and what the page is told as it opens with each look. */
    @Test
    @Config(qualifiers = PHONE)
    fun starts() {
        assumeTrue(home.exists())
        val files = ExampleBook.files()
        for (name in files.names()) File(home, "book/$lang/$name").apply { parentFile.mkdirs() }.writeBytes(files.bytes(name)!!)
        val list = JSONArray()
        for (look in looks) {
            val c = controller(look.settings)
            File(home, "start/$lang").mkdirs()
            File(home, "start/$lang/${look.name}.json").writeText(c.startForPreview(0))
            list.put(JSONObject().put("name", look.name).put("width", look.width).put("height", look.height).put("scale", look.scale))
        }
        File(home, "looks.json").writeText(list.toString(1))
    }

    private fun shot(name: String, look: String, step: SetupStep, page: Int = 0, then: (ReaderController) -> Unit = {}) {
        assumeTrue(home.exists())
        val shown = looks.first { it.name == look }
        val settings = shown.settings
        val c = controller(settings)
        val picture = File(home, "pages/$lang/${look}_$page.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path).asImageBitmap() }
        val laid = File(home, "pages/$lang/$look.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        val pages = laid?.optInt("pages", 4) ?: 4
        val columns = laid?.optInt("columns", pages) ?: pages
        // Two pages side by side on a wide screen, the chapter opening on the right-hand one.
        val twoUp = !settings.scrolling && shown.width > shown.height && shown.width >= 880
        val blank = if (twoUp) 1 else 0
        c.showForPreview(0, 0, pages, columns, twoUp, blank)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                ReadingSetupScreen(
                    c, onClose = {}, startAt = step,
                    pagePreview = { if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) },
                )
            }
        }
        compose.mainClock.advanceTimeBy(700)
        // (The reader opens the chapter as it starts, and waits for a page that isn't there.)
        c.showForPreview(0, 0, pages, columns, twoUp, blank)
        compose.mainClock.advanceTimeBy(100)
        if (page > 0) c.showForPreview(0, page, pages, columns, twoUp, blank)
        then(c)
        compose.mainClock.advanceTimeBy(1_400)
        out.mkdirs()
        captureScreenRoboImage(File(out, "$name.png").path)
    }

    @Test @Config(qualifiers = PHONE) fun phone1Colour() = shot("phone_1_colour", "phone", SetupStep.COLOUR)
    @Test @Config(qualifiers = PHONE) fun phone2Font() = shot("phone_2_font", "phone", SetupStep.FONT)
    @Test @Config(qualifiers = PHONE) fun phone3Size() = shot("phone_3_size", "phone", SetupStep.SIZE)
    @Test @Config(qualifiers = PHONE) fun phone4Spacing() = shot("phone_4_spacing", "phone", SetupStep.SPACING)
    @Test @Config(qualifiers = PHONE) fun phone4Rotation() = shot("phone_4b_rotation", "phone", SetupStep.ROTATION)
    @Test @Config(qualifiers = PHONE) fun phone5Turning() = shot("phone_5_turning", "phone", SetupStep.TURNING)
    @Test @Config(qualifiers = PHONE) fun phone5Turned() = shot("phone_5_turned", "phone", SetupStep.TURNING, page = 1)
    @Test @Config(qualifiers = PHONE) fun phone5Scrolling() = shot("phone_5_scrolling", "phone_scroll", SetupStep.TURNING)
    @Test @Config(qualifiers = PHONE) fun phone5Edges() = shot("phone_5b_edges", "phone", SetupStep.EDGES)
    @Test @Config(qualifiers = PHONE) fun phone5Controls() = shot("phone_5c_controls", "phone", SetupStep.CONTROLS)
    @Test @Config(qualifiers = TABLET) fun tablet5Controls() = shot("tablet_5c_controls", "tablet", SetupStep.CONTROLS)
    @Test @Config(qualifiers = PHONE) fun phone6Menu() = shot("phone_6_menu", "phone", SetupStep.MENU)
    @Test @Config(qualifiers = PHONE) fun phone6MenuOpen() = shot("phone_6_menu_open", "phone", SetupStep.MENU) { it.controlsVisible = true }
    @Test @Config(qualifiers = PHONE) fun phone7Done() = shot("phone_7_done", "phone", SetupStep.DONE)
    @Test @Config(qualifiers = PHONE) fun phoneSepia() = shot("phone_sepia_colour", "phone_sepia", SetupStep.COLOUR)
    @Test @Config(qualifiers = PHONE) fun phoneNight() = shot("phone_night_font", "phone_night", SetupStep.FONT)
    @Test @Config(qualifiers = PHONE) fun phoneNightSpacing() = shot("phone_night_spacing", "phone_night", SetupStep.SPACING)
    @Test @Config(qualifiers = PHONE) fun phoneNightMenu() = shot("phone_night_menu_open", "phone_night", SetupStep.MENU) { it.controlsVisible = true }
    @Test @Config(qualifiers = PHONE) fun phoneBig() = shot("phone_big_size", "phone_big", SetupStep.SIZE)
    @Test @Config(qualifiers = PHONE_ACROSS) fun phoneAcross() = shot("phone_across_spacing", "phone_across", SetupStep.SPACING)
    @Test @Config(qualifiers = PHONE_ACROSS) fun phoneAcrossRotation() = shot("phone_across_rotation", "phone_across", SetupStep.ROTATION)
    @Test @Config(qualifiers = PHONE_ACROSS) fun phoneAcrossTurned() = shot("phone_across_turned", "phone_across", SetupStep.TURNING, page = 1)
    @Test @Config(qualifiers = PHONE_ACROSS) fun phoneAcrossMenu() = shot("phone_across_menu_open", "phone_across", SetupStep.MENU) { it.controlsVisible = true }
    @Test @Config(qualifiers = SMALL_ACROSS) fun smallAcrossFont() = shot("small_across_font", "small_across", SetupStep.FONT)
    @Test @Config(qualifiers = SMALL_ACROSS) fun smallAcrossTurned() = shot("small_across_turned", "small_across", SetupStep.TURNING, page = 1)
    @Test @Config(qualifiers = SMALL_ACROSS) fun smallAcrossMenu() = shot("small_across_menu", "small_across", SetupStep.MENU)

    @Test @Config(qualifiers = TABLET) fun tablet1Colour() = shot("tablet_1_colour", "tablet", SetupStep.COLOUR)
    @Test @Config(qualifiers = TABLET) fun tablet2Font() = shot("tablet_2_font", "tablet", SetupStep.FONT)
    @Test @Config(qualifiers = TABLET) fun tablet3Size() = shot("tablet_3_size", "tablet", SetupStep.SIZE)
    @Test @Config(qualifiers = TABLET) fun tablet4Spacing() = shot("tablet_4_spacing", "tablet", SetupStep.SPACING)
    @Test @Config(qualifiers = TABLET) fun tablet4Rotation() = shot("tablet_4b_rotation", "tablet", SetupStep.ROTATION)
    @Test @Config(qualifiers = TABLET) fun tablet5Turning() = shot("tablet_5_turning", "tablet", SetupStep.TURNING)
    @Test @Config(qualifiers = TABLET) fun tablet5Turned() = shot("tablet_5_turned", "tablet", SetupStep.TURNING, page = 1)
    @Test @Config(qualifiers = TABLET) fun tablet5Edges() = shot("tablet_5b_edges", "tablet", SetupStep.EDGES)
    @Test @Config(qualifiers = TABLET) fun tablet6MenuOpen() = shot("tablet_6_menu_open", "tablet", SetupStep.MENU) { it.controlsVisible = true }
    @Test @Config(qualifiers = TABLET) fun tablet7Done() = shot("tablet_7_done", "tablet", SetupStep.DONE)
    @Test @Config(qualifiers = TABLET) fun tabletSlate() = shot("tablet_slate_font", "tablet_slate", SetupStep.FONT)
    @Test @Config(qualifiers = TABLET_UP) fun tabletUpSize() = shot("tablet_up_size", "tablet_up", SetupStep.SIZE)
    @Test @Config(qualifiers = TABLET_UP) fun tabletUpRotation() = shot("tablet_up_rotation", "tablet_up", SetupStep.ROTATION)
    @Test @Config(qualifiers = TABLET_UP) fun tabletUpMenu() = shot("tablet_up_menu_open", "tablet_up", SetupStep.MENU) { it.controlsVisible = true }
}
