package dev.kobolibrary.app.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.tr
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

    /**
     * The designs drawn on darker shades of their ground (a night sky, deep space), or with a picture over
     * most of it, rather than on the ground itself.
     */
    private val darkened = setOf(
        CoverStyle.NOIR, CoverStyle.STARFIELD, CoverStyle.GOTHIC, CoverStyle.THORNS, CoverStyle.NEON, CoverStyle.WAND, CoverStyle.OWL, CoverStyle.RAVEN,
    )

    /** The designs on paper of their own, with the ground colour only in a band. */
    private val onPaper = setOf(CoverStyle.PULP)

    @Test
    fun everyDesignInEveryThemeIsAFullCoverInItsColours() {
        val wrong = mutableListOf<String>()
        for (style in CoverStyle.entries) for (colors in CoverColors.themes) {
            val label = "${style.label} / ${colors.name}"
            val bmp = draw(text, style, colors)
            assertEquals(label, w, bmp.width)
            assertEquals(label, h, bmp.height)
            val px = bmp.pixels()
            assertTrue("$label is opaque", px.all { Color.alpha(it) == 255 })
            // Those in a picture's own colours are left at that.
            if (!style.themed) continue
            fun near(colour: Int) = px.count {
                Math.abs(Color.red(it) - Color.red(colour)) < 40 && Math.abs(Color.green(it) - Color.green(colour)) < 40 &&
                    Math.abs(Color.blue(it) - Color.blue(colour)) < 40
            }
            if (style !in darkened && style !in onPaper && near(colors.ground) <= px.size / 4) wrong += "$label is mostly its ground (${near(colors.ground)})"
            // (Those on a night sky show it in smaller touches: a glow, a rim of light, a line of lettering.)
            if (near(colors.accent) <= px.size / (if (style in darkened) 600 else 200)) wrong += "$label shows its accent (${near(colors.accent)})"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun theDesignsWithoutTheirOwnColoursStillDiffer() {
        // A painting and a storybook keep their picture's colours whatever the theme, and differ by genre and seed.
        for (style in CoverStyle.entries.filter { !it.themed }) {
            val a = generator.render(text, CoverDesign(style, CoverColors.themes[0], CoverGenre.FANTASY, 1), 268, 362)
            val b = generator.render(text, CoverDesign(style, CoverColors.themes[3], CoverGenre.FANTASY, 1), 268, 362)
            assertArrayEquals(style.label, a.pixels(), b.pixels())
            val c = generator.render(text, CoverDesign(style, CoverColors.themes[0], CoverGenre.FANTASY, 2), 268, 362)
            assertTrue(style.label, changed(a, c, 0, 362) > 1000)
        }
    }

    @Test
    fun theTitleSeriesAndAuthorAreAllOnTheCover() {
        // The designs with the author's name at the head.
        val authorAtTop = setOf(CoverStyle.FRAMED, CoverStyle.NOIR, CoverStyle.POSTER)
        // The designs with the words in a block in the middle, the author's name last.
        val authorInMiddle = setOf(CoverStyle.DECO, CoverStyle.BLOSSOM)
        for (style in CoverStyle.entries) {
            val whole = draw(text, style)
            val retitled = draw(text.copy(title = "A Study in Scarlet"), style)
            assertTrue("${style.label} title", changed(whole, retitled, 0, h) > 400)

            val other = draw(text.copy(authors = listOf("Agatha Christie")), style)
            if (style in authorAtTop) {
                assertTrue("${style.label} author", changed(whole, other, 0, h / 3) > 150)
                assertEquals("${style.label}: the author is at the head", 0, changed(whole, other, h / 2, h))
            } else if (style in authorInMiddle) {
                assertTrue("${style.label} author", changed(whole, other, h / 3, h) > 150)
                assertEquals("${style.label}: the author is under the title", 0, changed(whole, other, 0, h / 4))
            } else {
                assertTrue("${style.label} author", changed(whole, other, h * 2 / 3, h) > 150)
                // (A mosaic's tiles change with the author as well, so it is left out of this one.)
                if (style != CoverStyle.MOSAIC) assertEquals("${style.label}: the author is at the foot", 0, changed(whole, other, 0, h / 2))
            }

            val alone = draw(text.copy(series = null, seriesIndex = null), style)
            assertTrue("${style.label} series", changed(whole, alone, 0, h) > 100)

            val unnamed = draw(text.copy(authors = listOf("Unknown")), style)
            val nobody = draw(text.copy(authors = emptyList()), style)
            assertEquals("${style.label}: Unknown isn't printed", 0, changed(unnamed, nobody, 0, h))
        }
    }

    @Test
    fun theLetteringAndTheSeriesTakeTheColoursChosenForThemInEveryDesign() {
        val green = Color.rgb(0x12, 0xE0, 0x5A)
        val magenta = Color.rgb(0xF0, 0x10, 0xE0)
        // (Near enough to count the series where a design softens it.)
        fun Bitmap.near(colour: Int) = pixels().count {
            Math.abs(Color.red(it) - Color.red(colour)) < 48 && Math.abs(Color.green(it) - Color.green(colour)) < 48 &&
                Math.abs(Color.blue(it) - Color.blue(colour)) < 48
        }
        val found = mutableListOf<String>()
        var wrong = false
        for (style in CoverStyle.entries) {
            val plain = generator.render(text, CoverDesign(style, CoverColors.themes[0]), w, h)
            val lettered = generator.render(text, CoverDesign(style, CoverColors.themes[0], lettering = green), w, h)
            val both = generator.render(text, CoverDesign(style, CoverColors.themes[0], lettering = green, seriesLettering = magenta), w, h)
            val words = lettered.near(green) - plain.near(green)
            val series = both.near(magenta) - lettered.near(magenta)
            // The series keeps the design's colour when only the title and author's is chosen, and the other way round.
            val seriesAlone = lettered.near(magenta) - plain.near(magenta)
            val wordsWithSeries = both.near(green) - lettered.near(green)
            found += "${style.label}: words $words, series $series ($seriesAlone, $wordsWithSeries)"
            if (words < 1500 || series < 30 || seriesAlone != 0 || Math.abs(wordsWithSeries) > 40) wrong = true
        }
        assertTrue(found.joinToString("\n"), !wrong)
    }

    @Test
    fun theSeriesNumberCanBeInWordsInFiguresOrLeftOff() {
        assertEquals(tr("Book {0} of", "Six"), SeriesNumber.bookOf(6.0, SeriesNumber.WORDS))
        assertEquals(tr("Book {0} of", "6"), SeriesNumber.bookOf(6.0, SeriesNumber.FIGURES))
        assertEquals(tr("Book of"), SeriesNumber.bookOf(6.0, SeriesNumber.NONE))
        // Past twenty, or not a whole book, it's in figures whichever is asked for.
        assertEquals(SeriesNumber.written(21.0, SeriesNumber.FIGURES), SeriesNumber.written(21.0, SeriesNumber.WORDS))
        assertEquals(SeriesNumber.written(2.5, SeriesNumber.FIGURES), SeriesNumber.written(2.5, SeriesNumber.WORDS))
        fun draw(style: CoverStyle, how: SeriesNumber?) = generator.render(text, CoverDesign(style, CoverColors.themes[0], seriesNumber = how), w, h)
        // The framed design: "Book Five of" as it was, and the other two each different.
        val framed = SeriesNumber.entries.map { draw(CoverStyle.FRAMED, it) }
        assertArrayEquals(draw(CoverStyle.FRAMED, null).pixels(), framed[0].pixels())
        assertTrue(changed(framed[0], framed[1], 0, h) > 50 && changed(framed[0], framed[2], 0, h) > 50 && changed(framed[1], framed[2], 0, h) > 50)
        // Another: "Sherlock Holmes · Book 5" as it was, in words, or the series' name alone.
        val classic = SeriesNumber.entries.map { draw(CoverStyle.CLASSIC, it) }
        assertArrayEquals(draw(CoverStyle.CLASSIC, null).pixels(), classic[1].pixels())
        assertTrue(changed(classic[0], classic[1], 0, h) > 50 && changed(classic[1], classic[2], 0, h) > 50)
    }

    @Test
    fun aPaleNameOnAStorybookGoesOnADarkPill() {
        val plain = generator.render(text, CoverDesign(CoverStyle.STORYBOOK, CoverColors.themes[0]), w, h)
        val pale = generator.render(text, CoverDesign(CoverStyle.STORYBOOK, CoverColors.themes[0], lettering = Color.WHITE), w, h)
        // The pill at the foot, round the author's name: white with navy words, navy under white ones.
        fun Bitmap.at(x: Int, y: Int) = getPixel(x, y)
        val y = h - (0.07f * w).toInt() - 6
        val x = w / 2 - (0.2f * w).toInt()
        assertTrue("white pill", ColorUtils.calculateLuminance(plain.at(x, y)) > 0.7)
        assertTrue("navy pill", ColorUtils.calculateLuminance(pale.at(x, y)) < 0.15)
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
    fun theFramedDesignSaysWhichBookOfTheSeriesItIs() {
        val sixth = draw(text.copy(seriesIndex = 6.0), CoverStyle.FRAMED)
        val seventh = draw(text.copy(seriesIndex = 7.0), CoverStyle.FRAMED)
        // "Book Six of" sits under the title, near the foot.
        assertTrue(changed(sixth, seventh, h * 3 / 4, h) > 100)
        assertEquals(0, changed(sixth, seventh, 0, h / 2))
        // The books of a series get the boards in turn.
        assertTrue(CoverDesign.boardFor(1.0) != CoverDesign.boardFor(2.0))
        assertEquals(CoverDesign.boardFor(1.0), CoverDesign.boardFor((CoverColors.boards.size + 1).toDouble()))
        assertTrue(CoverColors.boards.all { it in CoverColors.dark })
        assertEquals(null, CoverDesign.boardFor(null))
    }

    @Test
    fun aPictureOfYourOwnGoesInTheFrame() {
        val pic = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(255, 0, 255)) }
        val design = CoverDesign(CoverStyle.FRAMED, CoverColors.themes[0], CoverGenre.FANTASY, 3)
        val bmp = generator.render(text, design, w, h, pic)
        val frame = generator.pictureFrame(CoverStyle.FRAMED, w.toFloat(), h.toFloat())!!
        val px = bmp.pixels()
        fun magenta(x: Int, y: Int) = px[y * w + x] == Color.rgb(255, 0, 255)
        assertTrue(magenta(frame.centerX().toInt(), frame.centerY().toInt()))
        assertTrue(magenta(frame.left.toInt() + 3, frame.top.toInt() + 3))
        assertTrue(!magenta(frame.left.toInt() - 6, frame.centerY().toInt()) && !magenta(w / 2, frame.top.toInt() - 6))
        // Designs without a frame for it leave it out.
        assertEquals(null, generator.pictureFrame(CoverStyle.CLASSIC, w.toFloat(), h.toFloat()))
        assertArrayEquals(draw(text, CoverStyle.CLASSIC).pixels(), generator.render(text, CoverDesign(CoverStyle.CLASSIC, CoverColors.themes[0]), w, h, pic).pixels())
    }

    @Test
    fun designsForAGenreMostlySuitIt() {
        val rnd = Random(3)
        for (genre in CoverGenre.entries) {
            val suited = CoverStyle.entries.filter { genre in it.suits }
            assertTrue("$genre has designs", suited.isNotEmpty())
            assertEquals(suited, CoverStyle.forGenre(genre).take(suited.size))
            val picked = List(100) { CoverDesign.random(rnd, genre = genre) }
            assertTrue(picked.all { it.genre == genre })
            assertTrue("$genre", picked.count { it.style in suited } > 60)
        }
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
