package dev.kobolibrary.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.comic.ComicArchives
import dev.kobolibrary.app.comic.ComicMode
import dev.kobolibrary.app.comic.ComicPages
import dev.kobolibrary.app.comic.ComicSettings
import dev.kobolibrary.app.comic.ComicTaps
import dev.kobolibrary.app.comic.TestComics
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.PdfPlace
import dev.kobolibrary.app.reader.ReaderStore
import dev.kobolibrary.app.ui.reader.ComicView
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every way of zooming a comic, page by page and in the long strip: double tap, pinch (in the middle and off
 * it), moving about when zoomed, and double-tap-and-drag. Each leaves what's under the fingers under them, as
 * Mihon does, and fills the screen with the page made bigger.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicZoomTest {

    @get:Rule
    val compose = createComposeRule()

    private var menuShown = false

    private fun open(s: ComicSettings, name: String, mode: ComicMode = s.mode) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "comic-zoom-$name").apply { deleteRecursively(); mkdirs() }
        val file = TestComics.comic(File(dir, "lantern.cbz"), pages = 8)
        val book = LibraryBook(
            id = "comic", meta = BookMetadata(title = "The Lantern Thief"), collections = emptyList(),
            fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = dev.kobolibrary.app.library.BookSource.FILE, originalName = "lantern.cbz", koboPath = null, sourceFormat = "CBZ",
        )
        ReaderStore.savePdfPlace(dir, PdfPlace(2, 8))
        val pages = ComicPages(ComicArchives.open(file, libArchive = null))
        compose.setContent {
            var menu by remember { mutableStateOf(false) }
            menuShown = menu
            AppSurface(ThemeChoice.BOOKCASE) {
                ComicView(
                    book = book, pages = pages, dir = dir, s = s, mode = mode, menu = menu, setMenu = { menu = it },
                    onMode = {}, onSettings = {}, onClose = {}, onCover = {}, readingHere = null,
                )
            }
        }
        settle()
    }

    /** Lets what's moving finish, and a single tap's wait for a second one run out. */
    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(150)
            compose.waitForIdle()
        }
    }

    private fun shot(name: String): Bitmap {
        File("/home/claude/heads/shots").mkdirs()
        val path = "/home/claude/heads/shots/zoom_$name.png"
        compose.onRoot().captureRoboImage(path)
        return BitmapFactory.decodeFile(path)
    }

    /**
     * Page 3 of the test comic: 600 × 900, white paper with a 30-pixel margin, panels in green: one across the top
     * (y 30 to 299), two side by side in the middle row (y 315 to 584; x 30 to 292 and 308 to 570, a white gap
     * between them), one across the bottom. Fitted to the screen it's [k] times as big, its corner at ([x], [y]).
     */
    private data class Placed(val x: Float, val y: Float, val k: Float)

    private fun placed(w: Int, h: Int): Placed {
        val k = minOf(w / 600f, h / 900f)
        return Placed((w - Math.round(600 * k)) / 2f, (h - Math.round(900 * k)) / 2f, k)
    }

    private fun isGreen(c: Int) = android.graphics.Color.green(c) > 120 && android.graphics.Color.red(c) < 120 && android.graphics.Color.blue(c) < 120
    private fun isWhite(c: Int) = android.graphics.Color.red(c) > 220 && android.graphics.Color.green(c) > 220 && android.graphics.Color.blue(c) > 220
    private fun isBlack(c: Int) = android.graphics.Color.red(c) < 40 && android.graphics.Color.green(c) < 40 && android.graphics.Color.blue(c) < 40

    @Test
    fun doubleTapZoomsInTwiceAsBigAndBackOut() {
        open(ComicSettings(), "dt")
        val before = shot("paged_before")
        val w = before.width
        val h = before.height
        // Twice on the white gap between the middle row's panels (the page's middle).
        compose.onRoot().performTouchInput { doubleClick(Offset(w / 2f, h / 2f)) }
        settle()
        val after = shot("paged_doubletap")
        // Twice the size: the gap (16 page pixels wide) still in the middle, the panels either side of it, and
        // the page reaching past the screen's sides (no margin at the left edge).
        val p = placed(w, h)
        val gapHalf = 8 * p.k * 2
        assertTrue("gap in the middle", isWhite(after.getPixel(w / 2, h / 2)))
        assertTrue("left panel", isGreen(after.getPixel((w / 2 - gapHalf - 40).toInt(), h / 2)))
        assertTrue("right panel", isGreen(after.getPixel((w / 2 + gapHalf + 40).toInt(), h / 2)))
        assertTrue("fills the width", isGreen(after.getPixel(5, h / 2)))
        // And not squeezed into a box the size of the screen at its top left: the bottom right is page too.
        assertTrue("bottom right is the page", !isBlack(after.getPixel(w - 5, h - 5)))
        compose.onRoot().performTouchInput { doubleClick(Offset(w / 2f, h / 2f)) }
        settle()
        val back = shot("paged_doubletap_back")
        for ((x, y) in listOf(5 to h / 2, w / 2 to 20, w / 2 to (p.y + 60).toInt())) assertEquals("($x, $y)", before.getPixel(x, y), back.getPixel(x, y))
    }

    @Test
    fun doubleTapBringsTheSpotTappedToTheMiddle() {
        open(ComicSettings(), "dt_spot")
        val before = shot("paged_spot_before")
        val w = before.width
        val h = before.height
        val p = placed(w, h)
        // On the left panel of the middle row, at the page's (219, 450).
        val tx = p.x + 219 * p.k
        val ty = p.y + 450 * p.k
        compose.onRoot().performTouchInput { doubleClick(Offset(tx, ty)) }
        settle()
        val after = shot("paged_spot")
        // That spot is in the middle now, and the gap (81 page pixels to its right) twice as far off as it was.
        assertTrue("the spot in the middle", isGreen(after.getPixel(w / 2, h / 2)))
        val gapX = (w / 2f + 81 * p.k * 2).toInt()
        assertTrue("the gap to its right", isWhite(after.getPixel(gapX, h / 2)))
    }

    @Test
    fun pinchingZoomsAboutTheFingers() {
        open(ComicSettings(), "pinch")
        val before = shot("paged_pinch_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        val p = placed(w.toInt(), h.toInt())
        // About a point in the top panel, near its left: the page's (120, 150). The fingers spread four times as far.
        val fx = p.x + 120 * p.k
        val fy = p.y + 150 * p.k
        compose.onRoot().performTouchInput {
            pinch(Offset(fx - 40, fy), Offset(fx - 160, fy), Offset(fx + 40, fy), Offset(fx + 160, fy), durationMillis = 400)
        }
        settle()
        val after = shot("paged_pinch")
        // That point is still between the fingers, and the panel's left edge (page x 30), 90 page pixels to its
        // left, is about four times as far: off the screen.
        assertTrue("panel still under the fingers", isGreen(after.getPixel(fx.toInt(), fy.toInt())))
        assertTrue("panel to the screen's edge", isGreen(after.getPixel(3, fy.toInt())))
        assertTrue("bottom right is the page", !isBlack(after.getPixel(w.toInt() - 5, h.toInt() - 5)))
    }

    @Test
    fun zoomedInAFingerMovesThePageAboutWithoutTurningIt() {
        open(ComicSettings(), "pan")
        val before = shot("paged_pan_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        compose.onRoot().performTouchInput { doubleClick(Offset(w / 2, h / 2)) }
        settle()
        // Half the screen to the right: the page's left side comes up to the screen's.
        compose.onRoot().performTouchInput { swipe(Offset(w * 0.1f, h * 0.5f), Offset(w * 0.6f, h * 0.5f), durationMillis = 400) }
        settle()
        val after = shot("paged_pan")
        assertTrue("left margin shows", isWhite(after.getPixel(3, (h / 2).toInt())))
        assertTrue("left panel next to it", isGreen(after.getPixel(200, (h / 2).toInt())))
        compose.onNodeWithText("3 / 8").assertExists()
    }

    @Test
    fun zoomedInAndPulledOnPastTheEdgeTheNextPageComesOn() {
        open(ComicSettings(), "pull")
        val before = shot("paged_pull_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        compose.onRoot().performTouchInput { doubleClick(Offset(w / 2, h / 2)) }
        settle()
        // Right, past the page's left edge by a third of the screen: the page before (reading left to right).
        compose.onRoot().performTouchInput { swipe(Offset(w * 0.05f, h * 0.5f), Offset(w * 0.95f, h * 0.5f), durationMillis = 500) }
        settle()
        shot("paged_pull")
        compose.onNodeWithText("2 / 8").assertExists()
    }

    @Test
    fun doubleTapAndDragZooms() {
        open(ComicSettings(), "quick")
        val before = shot("paged_quick_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        compose.onRoot().performTouchInput {
            down(Offset(w / 2, h / 2))
            up()
            advanceEventTime(80)
            down(Offset(w / 2, h / 2))
            repeat(12) { i -> moveTo(Offset(w / 2, h / 2 + (i + 1) * 25f)) }
            up()
        }
        settle()
        val after = shot("paged_quick")
        // Down by 300 pixels: nearly twice as big, about the middle.
        assertTrue("zoomed: no margin at the left edge", isGreen(after.getPixel(5, (h / 2).toInt())))
        assertTrue("the gap still in the middle", isWhite(after.getPixel((w / 2).toInt(), (h / 2).toInt())))
        // And back up the same way: smaller again.
        compose.onRoot().performTouchInput {
            down(Offset(w / 2, h / 2))
            up()
            advanceEventTime(80)
            down(Offset(w / 2, h / 2))
            repeat(12) { i -> moveTo(Offset(w / 2, h / 2 - (i + 1) * 25f)) }
            up()
        }
        settle()
        val back = shot("paged_quick_back")
        assertTrue("whole page again: margin at the left edge", isWhite(back.getPixel(5, (h / 2).toInt())))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun onATabletTheZoomedPageStaysInTheMiddle() {
        open(ComicSettings(), "tablet")
        val before = shot("tablet_before")
        val w = before.width
        val h = before.height
        val p = placed(w, h)
        compose.onRoot().performTouchInput { doubleClick(Offset(w / 2f, h / 2f)) }
        settle()
        val after = shot("tablet_doubletap")
        // Twice the size it's still narrower than the screen: in the middle, the background either side.
        val left = (w - Math.round(600 * p.k) * 2) / 2
        assertTrue("background to its left", isBlack(after.getPixel(left - 10, h / 2)))
        assertTrue("its margin", isWhite(after.getPixel(left + 10, h / 2)))
        assertTrue("its left panel", isGreen(after.getPixel(left + (40 * p.k * 2).toInt(), h / 2)))
        assertTrue("the gap in the middle", isWhite(after.getPixel(w / 2, h / 2)))
        assertTrue("background to its right", isBlack(after.getPixel(w - left + 10, h / 2)))
    }

    @Test
    fun rightToLeftZoomsTheSame() {
        open(ComicSettings(mode = ComicMode.RIGHT_TO_LEFT), "rtl")
        val before = shot("rtl_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        compose.onRoot().performTouchInput { doubleClick(Offset(w / 2, h / 2)) }
        settle()
        val after = shot("rtl_doubletap")
        assertTrue("gap in the middle", isWhite(after.getPixel((w / 2).toInt(), (h / 2).toInt())))
        assertTrue("fills the width", isGreen(after.getPixel(5, (h / 2).toInt())))
        // Pulled on past its right edge, reading right to left: the page before.
        compose.onRoot().performTouchInput { swipe(Offset(w * 0.95f, h * 0.5f), Offset(w * 0.05f, h * 0.5f), durationMillis = 500) }
        settle()
        compose.onNodeWithText("2 / 8").assertExists()
    }

    @Test
    fun theStripDoubleTappedAtTheSideZoomsRatherThanScrolling() {
        open(ComicSettings(mode = ComicMode.STRIP), "strip")
        val before = shot("strip_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        val tx = (w * 0.25f).toInt()
        val ty = (h * 0.5f).toInt()
        compose.onRoot().performTouchInput { doubleClick(Offset(tx.toFloat(), ty.toFloat())) }
        settle()
        val after = shot("strip_doubletap")
        // Still the same page, twice the size about the spot tapped: that spot the same, the left margin gone.
        compose.onNodeWithText("3 / 8").assertExists()
        assertEquals("the spot tapped", isGreen(before.getPixel(tx, ty)), isGreen(after.getPixel(tx, ty)))
        assertTrue("margin before", isWhite(before.getPixel(3, ty)))
        assertTrue("no margin after", !isWhite(after.getPixel(3, ty)))
        compose.onRoot().performTouchInput { doubleClick(Offset(tx.toFloat(), ty.toFloat())) }
        settle()
        val back = shot("strip_doubletap_back")
        assertTrue("margin again", isWhite(back.getPixel(3, ty)))
    }

    @Test
    fun theStripPinchesAboutTheFingers() {
        open(ComicSettings(mode = ComicMode.STRIP), "strip_pinch")
        val before = shot("strip_pinch_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        val fx = w * 0.3f
        val fy = h * 0.3f
        compose.onRoot().performTouchInput {
            pinch(Offset(fx - 30, fy), Offset(fx - 90, fy), Offset(fx + 30, fy), Offset(fx + 90, fy), durationMillis = 400)
        }
        settle()
        val after = shot("strip_pinch")
        compose.onNodeWithText("3 / 8").assertExists()
        assertEquals("the spot between the fingers", isGreen(before.getPixel(fx.toInt(), fy.toInt())), isGreen(after.getPixel(fx.toInt(), fy.toInt())))
        assertTrue("no margin after", !isWhite(after.getPixel(3, fy.toInt())))
    }

    @Test
    fun theStripZoomsWithOneFingerToo() {
        open(ComicSettings(mode = ComicMode.STRIP), "strip_quick")
        val before = shot("strip_quick_before")
        val w = before.width.toFloat()
        val h = before.height.toFloat()
        val y = (h * 0.4f).toInt()
        compose.onRoot().performTouchInput {
            down(Offset(w / 2, y.toFloat()))
            up()
            advanceEventTime(80)
            down(Offset(w / 2, y.toFloat()))
            repeat(12) { i -> moveTo(Offset(w / 2, y + (i + 1) * 25f)) }
            up()
        }
        settle()
        val after = shot("strip_quick")
        compose.onNodeWithText("3 / 8").assertExists()
        assertTrue("margin before", isWhite(before.getPixel(3, y)))
        assertTrue("zoomed: no margin after", !isWhite(after.getPixel(3, y)))
    }

    @Test
    fun withTapZonesOffATapShowsAndHidesTheMenu() {
        open(ComicSettings(taps = ComicTaps.OFF), "menu")
        val bmp = shot("menu_before")
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        compose.onRoot().performTouchInput { click(Offset(w * 0.1f, h * 0.5f)) }
        settle()
        assertTrue("shown", menuShown)
        compose.onRoot().performTouchInput { click(Offset(w * 0.5f, h * 0.6f)) }
        settle()
        assertTrue("hidden again", !menuShown)
        compose.onRoot().performTouchInput { click(Offset(w * 0.9f, h * 0.4f)) }
        settle()
        assertTrue("shown again", menuShown)
        compose.onRoot().performTouchInput { click(Offset(w * 0.9f, h * 0.5f)) }
        settle()
        assertTrue("and hidden", !menuShown)
        compose.onNodeWithText("3 / 8").assertExists()
    }

    @Test
    fun inTheStripWithTapZonesOffATapShowsAndHidesTheMenu() {
        open(ComicSettings(mode = ComicMode.STRIP, taps = ComicTaps.OFF), "menu_strip")
        val bmp = shot("menu_strip_before")
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        compose.onRoot().performTouchInput { click(Offset(w * 0.1f, h * 0.5f)) }
        settle()
        assertTrue("shown", menuShown)
        compose.onRoot().performTouchInput { click(Offset(w * 0.5f, h * 0.6f)) }
        settle()
        assertTrue("hidden again", !menuShown)
    }

    @Test
    fun withTapZonesOnTurningThePageHidesTheMenu() {
        open(ComicSettings(), "menu2")
        val bmp = shot("menu2_before")
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        compose.onRoot().performTouchInput { click(Offset(w * 0.5f, h * 0.5f)) }
        settle()
        assertTrue("shown", menuShown)
        // As in Mihon: a tap on the right turns the page, and the menu goes.
        compose.onRoot().performTouchInput { click(Offset(w * 0.9f, h * 0.5f)) }
        settle()
        assertTrue("hidden", !menuShown)
        compose.onNodeWithText("4 / 8").assertExists()
        // The middle shows it again, and hides it.
        compose.onRoot().performTouchInput { click(Offset(w * 0.5f, h * 0.5f)) }
        settle()
        assertTrue("shown again", menuShown)
        compose.onRoot().performTouchInput { click(Offset(w * 0.5f, h * 0.5f)) }
        settle()
        assertTrue("hidden again", !menuShown)
    }
}
