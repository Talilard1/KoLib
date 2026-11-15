package dev.kobolibrary.app.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Sheets of covers in every design and genre, for looking over (written to /home/claude/covers when it's there). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverDesignShots {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val generator = CoverGenerator(context)
    private val out = File("/home/claude/covers")

    private val books = mapOf(
        CoverGenre.FANTASY to CoverText("The Iron Crown", listOf("Mara Ellison"), "The Ninefold Gate", 6.0),
        CoverGenre.SCIENCE_FICTION to CoverText("Beyond the Pale Star", listOf("J. T. Okafor"), "The Long Dark", 2.0),
        CoverGenre.MYSTERY to CoverText("A Death in Fog Lane", listOf("Agnes Brooke"), null, null),
        CoverGenre.THRILLER to CoverText("The Last Courier", listOf("Daniel Vance"), null, null),
        CoverGenre.HORROR to CoverText("The House on Wren Hill", listOf("Evelyn Marsh"), null, null),
        CoverGenre.ROMANCE to CoverText("Letters to Juniper", listOf("Clara Wells"), "Seaside Hearts", 1.0),
        CoverGenre.HISTORICAL to CoverText("The Emperor's Tutor", listOf("Marcus Hale"), null, null),
        CoverGenre.ADVENTURE to CoverText("Salt and Canvas", listOf("Tom Reyes"), null, null),
        CoverGenre.WESTERN to CoverText("Dust Over Red Mesa", listOf("Hank Doyle"), null, null),
        CoverGenre.LITERARY to CoverText("The Quiet Year", listOf("Ruth Ainsley"), null, null),
        CoverGenre.NON_FICTION to CoverText("A Short History of Rivers", listOf("Dr Ian Sowerby"), null, null),
        CoverGenre.CHILDREN to CoverText("Pip and the Paper Moon", listOf("Lily Hart"), null, null),
    )

    private fun sheet(name: String, covers: List<Bitmap>, cols: Int) {
        val w = covers[0].width
        val h = covers[0].height
        val rows = (covers.size + cols - 1) / cols
        val g = 8
        val bmp = Bitmap.createBitmap(cols * (w + g) + g, rows * (h + g) + g, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0x80, 0x80, 0x80))
        covers.forEachIndexed { i, b -> c.drawBitmap(b, (g + (i % cols) * (w + g)).toFloat(), (g + (i / cols) * (h + g)).toFloat(), Paint()) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun theme(name: String) = CoverColors.themes.first { it.name == name }

    @Test
    fun everyDesign() {
        assumeTrue(out.isDirectory)
        val book = books.getValue(CoverGenre.FANTASY)
        val covers = CoverStyle.entries.map { st ->
            val colours = when (st) {
                CoverStyle.BLOSSOM -> theme("Blush")
                CoverStyle.ESSAY -> theme("Linen")
                else -> CoverDesign.boardFor(6.0)!!
            }
            val genre = st.suits.firstOrNull() ?: CoverGenre.FANTASY
            generator.render(books.getValue(genre).takeIf { st != CoverStyle.FRAMED && st != CoverStyle.GILDED } ?: book, CoverDesign(st, colours, genre, 7), 268, 362)
        }
        sheet("designs", covers, 4)
    }

    @Test
    fun everyScene() {
        assumeTrue(out.isDirectory)
        sheet("scenes", CoverGenre.entries.map { g -> generator.render(books.getValue(g), CoverDesign(CoverStyle.PAINTING, theme("Midnight"), g, 11), 268, 362) }, 4)
    }

    @Test
    fun framedInEveryGenre() {
        assumeTrue(out.isDirectory)
        sheet("framed", CoverGenre.entries.mapIndexed { i, g -> generator.render(books.getValue(g), CoverDesign(CoverStyle.FRAMED, CoverDesign.boardFor(i + 1.0)!!, g, 5), 268, 362) }, 4)
    }

    @Test
    fun gildedInEveryGenre() {
        assumeTrue(out.isDirectory)
        val boards = listOf("Teal", "Midnight", "Claret", "Forest", "Plum", "Umber", "Charcoal", "Slate")
        sheet("gilded", CoverGenre.entries.mapIndexed { i, g -> generator.render(books.getValue(g), CoverDesign(CoverStyle.GILDED, theme(boards[i % boards.size]), g, 3), 268, 362) }, 4)
    }

    @Test
    fun fantasySeeds() {
        assumeTrue(out.isDirectory)
        val book = books.getValue(CoverGenre.FANTASY)
        val covers = (1..8).map { k ->
            val style = if (k % 2 == 0) CoverStyle.PAINTING else CoverStyle.FRAMED
            generator.render(book, CoverDesign(style, CoverDesign.boardFor(k.toDouble())!!, CoverGenre.FANTASY, k * 101), 268, 362)
        }
        sheet("fantasy", covers, 4)
    }

    @Test
    fun ownPicture() {
        assumeTrue(out.isDirectory)
        // A test card: rings round a cross, so where it's placed and how big is plain to see.
        val pic = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(pic)
        c.drawColor(Color.rgb(0x30, 0x60, 0x90))
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12f; color = Color.WHITE }
        for (k in 1..6) c.drawCircle(450f, 300f, k * 45f, ring)
        c.drawLine(0f, 300f, 900f, 300f, ring)
        c.drawLine(450f, 0f, 450f, 600f, ring)
        val book = books.getValue(CoverGenre.FANTASY)
        val design = CoverDesign(CoverStyle.FRAMED, CoverDesign.boardFor(3.0)!!, CoverGenre.FANTASY, 1)
        val covers = listOf(
            generator.render(book, design, 268, 362, pic, CoverCrop()),
            generator.render(book, design, 268, 362, pic, CoverCrop(zoom = 2f, centerX = 0.3f, centerY = 0.3f)),
            generator.render(book, design.copy(style = CoverStyle.PAINTING), 268, 362, pic, CoverCrop()),
            generator.render(book, design.copy(style = CoverStyle.PAINTING), 268, 362, pic, CoverCrop(zoom = 1.5f, centerX = 0.7f)),
        )
        sheet("own", covers, 4)
    }

    @Test
    fun oneLarge() {
        assumeTrue(out.isDirectory)
        val book = books.getValue(CoverGenre.FANTASY)
        File(out, "framed_large.png").outputStream().use { generator.render(book, CoverDesign(CoverStyle.FRAMED, CoverDesign.boardFor(6.0)!!, CoverGenre.FANTASY, 7)).compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(out, "gilded_large.png").outputStream().use { generator.render(book, CoverDesign(CoverStyle.GILDED, theme("Teal"), CoverGenre.FANTASY, 3)).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
