package dev.kobolibrary.app.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverGeneratorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val generator = CoverGenerator(context)
    private val text = CoverText("The Hound of the Baskervilles", listOf("Arthur Conan Doyle"), "Sherlock Holmes", 5.0)
    private val w = 536
    private val h = 724

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    /** How many pixels differ between two covers in the rows [from, to). */
    private fun changed(a: Bitmap, b: Bitmap, from: Int, to: Int): Int {
        val pa = a.pixels()
        val pb = b.pixels()
        return (from * a.width until to * a.width).count { pa[it] != pb[it] }
    }

    private fun draw(t: CoverText, style: CoverStyle, colors: CoverColors = CoverColors.themes[0]) = generator.render(t, CoverDesign(style, colors), w, h)

    @Test
    fun everyDesignInEveryThemeIsAFullCoverInItsColours() {
        for (style in CoverStyle.entries) for (colors in CoverColors.themes) {
            val label = "${style.label} / ${colors.name}"
            val bmp = draw(text, style, colors)
            assertEquals(label, w, bmp.width)
            assertEquals(label, h, bmp.height)
            val px = bmp.pixels()
            assertTrue("$label is opaque", px.all { Color.alpha(it) == 255 })
            fun near(colour: Int) = px.count {
                Math.abs(Color.red(it) - Color.red(colour)) < 40 && Math.abs(Color.green(it) - Color.green(colour)) < 40 &&
                    Math.abs(Color.blue(it) - Color.blue(colour)) < 40
            }
            assertTrue("$label is mostly its ground (${near(colors.ground)})", near(colors.ground) > px.size / 4)
            assertTrue("$label shows its accent (${near(colors.accent)})", near(colors.accent) > px.size / 200)
        }
    }

    @Test
    fun theTitleSeriesAndAuthorAreAllOnTheCover() {
        for (style in CoverStyle.entries) {
            val whole = draw(text, style)
            val retitled = draw(text.copy(title = "A Study in Scarlet"), style)
            assertTrue("${style.label} title", changed(whole, retitled, 0, h * 2 / 3) > 400)

            val other = draw(text.copy(authors = listOf("Agatha Christie")), style)
            assertTrue("${style.label} author", changed(whole, other, h * 2 / 3, h) > 150)
            // (A mosaic's tiles change with the author as well, so it is left out of this one.)
            if (style != CoverStyle.MOSAIC) assertEquals("${style.label}: the author is at the foot", 0, changed(whole, other, 0, h / 2))

            val alone = draw(text.copy(series = null, seriesIndex = null), style)
            assertTrue("${style.label} series", changed(whole, alone, 0, h * 2 / 3) > 100)

            val unnamed = draw(text.copy(authors = listOf("Unknown")), style)
            val nobody = draw(text.copy(authors = emptyList()), style)
            assertEquals("${style.label}: Unknown isn't printed", 0, changed(unnamed, nobody, 0, h))
        }
    }

    @Test
    fun defaultsToTheKobosScreenSizeAndCopesWithAwkwardText() {
        val full = generator.render(text, CoverDesign(CoverStyle.CLASSIC, CoverColors.themes[0]))
        assertEquals(CoverCrop.OUT_WIDTH, full.width)
        assertEquals(CoverCrop.OUT_HEIGHT, full.height)

        val awkward = listOf(
            CoverText("", emptyList(), null, null),
            CoverText("A".repeat(400), List(6) { "Author Number $it" }, "S".repeat(200), 1.5),
            CoverText("Supercalifragilisticexpialidociousnessesqueness", listOf("X"), "", null),
            CoverText("吾輩は猫である", listOf("夏目漱石"), "猫", 1.0),
            CoverText("Ἰλιάς", listOf("Ὅμηρος"), null, null),
            CoverText("Война и мир", listOf("Лев Толстой"), null, null),
        )
        for (t in awkward) for (style in CoverStyle.entries) {
            val bmp = generator.render(t, CoverDesign(style, CoverColors.fromHue(200f, 1f)), 268, 362)
            assertEquals(268, bmp.width)
        }
    }

    @Test
    fun aLongTitleIsSetSmallerRatherThanCutShort() {
        // The last word of a long title still changes the picture, so it was drawn and not cut off.
        val long = "The Strange Case of Dr Jekyll and Mr Hyde and Other Tales of Terror"
        for (style in CoverStyle.entries.filter { it != CoverStyle.MOSAIC }) {
            val a = draw(text.copy(title = "$long Told"), style)
            val b = draw(text.copy(title = "$long Sung"), style)
            assertTrue("${style.label} keeps the whole title", changed(a, b, 0, h) > 20)
        }
    }

    @Test
    fun everyThemeCanBeRead() {
        fun check(c: CoverColors, label: String) {
            assertTrue("$label ink on ground", ColorUtils.calculateContrast(c.ink, c.ground) >= 4.5)
            assertTrue("$label ink on accent", ColorUtils.calculateContrast(c.accentInk, c.accent) >= 4.5)
            assertTrue("$label accent on ground", ColorUtils.calculateContrast(c.accent, c.ground) >= 3.0)
        }
        for (c in CoverColors.themes) check(c, c.name)
        for (hue in listOf(0f, 45f, 120f, 210f, 300f, 359f, 720f, -30f)) for (strength in listOf(0f, 0.5f, 1f)) for (dark in listOf(true, false)) {
            check(CoverColors.fromHue(hue, strength, dark), "hue $hue at $strength, dark $dark")
        }
        val board = CoverColors.fromHue(210f, 0.8f, dark = true)
        val page = CoverColors.fromHue(210f, 0.8f, dark = false)
        assertTrue(ColorUtils.calculateLuminance(board.ground) < 0.2 && ColorUtils.calculateLuminance(page.ground) > 0.6)
        val grey = CoverColors.fromHue(90f, 0f)
        assertEquals(Color.red(grey.ground), Color.green(grey.ground))
        assertEquals(CoverColors.themes.size, CoverColors.themes.map { it.name }.toSet().size)
    }

    @Test
    fun aMosaicBelongsToItsBook() {
        val one = draw(text, CoverStyle.MOSAIC)
        assertArrayEquals("the same book gets the same tiles", one.pixels(), draw(text, CoverStyle.MOSAIC).pixels())
        // The band of tiles lies between the title and the author, so a change there is a change of tiles.
        val another = draw(text.copy(title = "A Study in Scarlet"), CoverStyle.MOSAIC)
        assertTrue("another book gets other tiles", changed(one, another, h * 45 / 100, h * 8 / 10) > 5000)
    }

    @Test
    fun randomDesignsCoverEveryStyleAndTheme() {
        val rnd = Random(7)
        val designs = List(300) { CoverDesign.random(rnd) }
        assertEquals(CoverStyle.entries.toSet(), designs.map { it.style }.toSet())
        assertTrue(designs.any { it.colors.name == "Custom" })
        assertTrue(designs.map { it.colors.name }.toSet().containsAll(CoverColors.themes.map { it.name }))
        val builtIn = List(100) { CoverDesign.random(rnd, anyColour = false) }
        assertTrue(builtIn.all { it.colors in CoverColors.themes })
    }
}
