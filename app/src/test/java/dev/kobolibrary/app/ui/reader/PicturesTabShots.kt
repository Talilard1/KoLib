package dev.kobolibrary.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.TestLanguage
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.TestEpubs
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReaderSettings
import dev.kobolibrary.app.reader.ReaderTheme
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.util.UUID

private const val LANDSCAPE = "w1280dp-h800dp-land-mdpi"
private const val PHONE = "w411dp-h891dp-port-mdpi"

/** The contents panel's list of the book's pictures, and a picture opened from it, in a book made up of drawn pictures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PicturesTabShots {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun inLanguage() = TestLanguage.apply()

    // ---- a made-up book with pictures ---------------------------------------------------------------

    private fun png(w: Int, h: Int, jpeg: Boolean = false, draw: Canvas.(Paint) -> Unit): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).draw(Paint(Paint.ANTI_ALIAS_FLAG))
        return ByteArrayOutputStream().also { bmp.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, it) }.toByteArray()
    }

    private fun mark(colour: Int, sides: Int) = png(120, 120) { p ->
        p.color = colour
        val path = Path()
        for (k in 0 until sides) {
            val a = Math.PI * 2 * k / sides - Math.PI / 2
            val x = 60 + 48 * Math.cos(a).toFloat()
            val y = 60 + 48 * Math.sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, p)
    }

    private fun page(title: String, body: String) = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:xlink="http://www.w3.org/1999/xlink"><head><title>$title</title></head><body>$body</body></html>"""

    private val words = (1..80).joinToString(" ") { "word" }

    private fun chapter(title: String, mark: String, extra: String = "") =
        page(title, """<h2><img src="../images/$mark" alt=""/><br/>$title</h2><p>$words</p>$extra<p>$words</p>""")

    private val chapters = listOf(
        "prologue" to "Prologue",
        "ch1" to "1 The Road North",
        "ch2" to "2 The River",
        "ch3" to "3 The Hill Fort",
        "ch4" to "4 Night Watch",
        "ch5" to "5 Homecoming",
    )

    private fun book(): File {
        val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">mapped-lands</dc:identifier><dc:title>The Mapped Lands</dc:title><dc:language>en</dc:language><meta name="cover" content="cover-image"/></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="cover" href="text/cover.xhtml" media-type="application/xhtml+xml"/>
    <item id="map" href="text/map.xhtml" media-type="application/xhtml+xml"/>
${chapters.joinToString("\n") { (id, _) -> """    <item id="$id" href="text/$id.xhtml" media-type="application/xhtml+xml"/>""" }}
    <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
  </manifest>
  <spine>
    <itemref idref="cover"/><itemref idref="map"/>
${chapters.joinToString("\n") { (id, _) -> """    <itemref idref="$id"/>""" }}
  </spine>
</package>"""
        val nav = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body>
<nav epub:type="toc" id="toc"><ol>
${chapters.joinToString("\n") { (id, t) -> """<li><a href="text/$id.xhtml">$t</a></li>""" }}
</ol></nav></body></html>"""
        val sky = 0xFF2E4A6B.toInt()
        val entries = linkedMapOf(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
            "OEBPS/text/cover.xhtml" to page("Cover", """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 900"><image width="600" height="900" xlink:href="../images/cover.jpg"/></svg>""").toByteArray(),
            "OEBPS/text/map.xhtml" to page("Map", """<div><img src="../images/map.png" alt="The lands"/></div>""").toByteArray(),
            "OEBPS/text/prologue.xhtml" to chapter("Prologue", "mark0.png", """<p><img src="../images/spacer.png" alt=""/></p>""").toByteArray(),
            "OEBPS/text/ch1.xhtml" to chapter("1 The Road North", "mark1.png", """<div><img src="../images/plate.jpg" alt=""/></div>""").toByteArray(),
            "OEBPS/text/ch2.xhtml" to chapter("2 The River", "mark2.png").toByteArray(),
            "OEBPS/text/ch3.xhtml" to chapter("3 The Hill Fort", "mark3.png", """<div><img src="../images/wide.jpg" alt=""/></div>""").toByteArray(),
            "OEBPS/text/ch4.xhtml" to chapter("4 Night Watch", "mark4.png").toByteArray(),
            // The first chapter's mark again: listed once, where it first came.
            "OEBPS/text/ch5.xhtml" to chapter("5 Homecoming", "mark1.png").toByteArray(),
            "OEBPS/images/cover.jpg" to png(600, 900, jpeg = true) { p ->
                drawColor(sky)
                p.color = 0xFFE9C46A.toInt(); drawCircle(300f, 330f, 120f, p)
                p.color = 0xFF1B2838.toInt(); drawPath(Path().apply { moveTo(0f, 900f); lineTo(0f, 640f); lineTo(220f, 480f); lineTo(380f, 600f); lineTo(600f, 430f); lineTo(600f, 900f); close() }, p)
            },
            "OEBPS/images/map.png" to png(1400, 1000) { p ->
                drawColor(0xFFEFE2C0.toInt())
                p.color = 0xFF9DB7C9.toInt(); drawRect(0f, 0f, 1400f, 220f, p)
                p.color = 0xFF6B7F4A.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 10f
                drawPath(Path().apply { moveTo(80f, 900f); cubicTo(400f, 700f, 700f, 900f, 1300f, 300f) }, p)
                p.style = Paint.Style.FILL; p.color = 0xFF7A3B2E.toInt()
                for ((x, y) in listOf(300f to 760f, 760f to 640f, 1150f to 420f)) drawCircle(x, y, 22f, p)
            },
            "OEBPS/images/plate.jpg" to png(800, 1100, jpeg = true) { p ->
                drawColor(0xFFCFE0E8.toInt())
                p.color = 0xFF4F6D3A.toInt(); drawPath(Path().apply { moveTo(0f, 1100f); lineTo(0f, 700f); quadTo(400f, 560f, 800f, 760f); lineTo(800f, 1100f); close() }, p)
                p.color = 0xFF5B4636.toInt(); drawRect(360f, 520f, 440f, 760f, p)
            },
            "OEBPS/images/wide.jpg" to png(1600, 700, jpeg = true) { p ->
                drawColor(0xFFF2D7B6.toInt())
                p.color = 0xFF8C5A3C.toInt(); drawPath(Path().apply { moveTo(0f, 700f); lineTo(0f, 420f); lineTo(500f, 260f); lineTo(900f, 380f); lineTo(1300f, 200f); lineTo(1600f, 360f); lineTo(1600f, 700f); close() }, p)
            },
            "OEBPS/images/spacer.png" to png(1, 1) { drawColor(0) },
        )
        listOf(0xFF7A3B2E, 0xFF2E4A6B, 0xFF4F6D3A, 0xFF8C5A3C, 0xFF5B4636).forEachIndexed { i, c ->
            entries["OEBPS/images/mark$i.png"] = mark(c.toInt(), 3 + i)
        }
        return TestEpubs.build(File(context.cacheDir, "pictures-${UUID.randomUUID()}.epub"), entries)
    }

    private fun settle(done: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(400) {
            if (done()) return
            looper.idleFor(Duration.ofMillis(50))
            Thread.sleep(5)
        }
        fail("Never got there")
    }

    private fun controller(): ReaderController {
        val files = EpubFileFiles(EpubFile.open(book()))
        val c = ReaderController(files, BookStructure(files), "The Mapped Lands", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets)
        c.showForPreview(3, 2, 6)
        c.controlsVisible = true
        c.findPictures()
        settle { c.pictures != null }
        // Their small pictures made beforehand, so the shot has them.
        c.pictures!!.forEach { c.thumbnail(it.path, 360) }
        return c
    }

    private fun shot(name: String, c: ReaderController, panel: Panel = Panel.CONTENTS) {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { Box(Modifier.fillMaxSize().background(Color(0xFFF4EEDF))) }, initialPanel = panel)
            }
        }
        repeat(6) { compose.waitForIdle(); Thread.sleep(40) }
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun everyPictureInOrderWithItsChapter() {
        val c = controller()
        val list = c.pictures!!
        assertEquals(
            listOf("cover.jpg", "map.png", "mark0.png", "mark1.png", "plate.jpg", "mark2.png", "mark3.png", "wide.jpg", "mark4.png"),
            list.map { it.path.substringAfterLast('/') },
        )
        // The front of the book isn't a chapter of the contents; the plate is in the first chapter.
        assertEquals("", c.chapterNameOf(list[1].chapter))
        assertEquals("1 The Road North", c.chapterNameOf(list.first { it.path.endsWith("plate.jpg") }.chapter))
        // Not a book of several.
        assertEquals(null, c.innerBookOf(list[4].chapter))
        c.contentsTab = 3
        shot("59b_reader_pictures", c)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun picturesOnAPhone() {
        val c = controller()
        c.contentsTab = 3
        shot("59c_reader_pictures_phone", c)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun largeOnes() {
        val c = controller()
        c.contentsTab = 3
        c.largePicturesOnly = true
        shot("59d_reader_pictures_large", c)
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun aPictureFromTheList() {
        val c = controller()
        c.contentsTab = 3
        val all = c.pictures!!
        c.browsing = ReaderController.Browse(all, all.indexOfFirst { it.path.endsWith("map.png") })
        shot("59e_reader_picture_viewer", c)
        assertEquals(1, c.browsedTo)
    }

    /** From the list, a swipe goes to the next picture or the one before; "Go to its page" goes to the one shown. */
    @Test
    @Config(qualifiers = PHONE)
    fun swipingThroughThePictures() {
        val c = controller()
        c.contentsTab = 3
        val all = c.pictures!!
        c.browsing = ReaderController.Browse(all, 1)
        shot("59f_reader_picture_swipe_tmp", c)
        assertEquals(1, c.browsedTo)
        fun swipe(left: Boolean) {
            compose.onRoot().performTouchInput { if (left) swipeLeft() else swipeRight() }
            repeat(6) { compose.waitForIdle(); Thread.sleep(30) }
        }
        swipe(left = true)
        assertEquals("the next", 2, c.browsedTo)
        swipe(left = true)
        assertEquals(3, c.browsedTo)
        swipe(left = false)
        assertEquals("the one before", 2, c.browsedTo)
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/59f_reader_picture_swipe.png")
        File("${TestLanguage.folder("build/outputs/roborazzi")}/59f_reader_picture_swipe_tmp.png").delete()
        // The first picture has none before it.
        c.browsing = ReaderController.Browse(all, 0)
        repeat(4) { compose.waitForIdle(); Thread.sleep(30) }
        swipe(left = false)
        assertEquals(0, c.browsedTo)
        // Going to the page of the one shown closes the pictures.
        swipe(left = true)
        compose.onNodeWithText("Go to its page").performClick()
        compose.waitForIdle()
        assertEquals(null, c.browsing)
        assertEquals(all[1].chapter, c.chapterIndex)
    }
}
