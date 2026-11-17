package dev.kobolibrary.app.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
        CoverGenre.WUXIA to CoverText("The Jade Sword Sect", listOf("Wen Liang"), "Nine Heavens", 3.0),
        CoverGenre.SAMURAI to CoverText("Snow on the Tokaido", listOf("Keiko Arai"), null, null),
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

    /** A book for [genre]: one of its own if there is one, otherwise one of the samples. */
    private fun bookFor(genre: CoverGenre) = books[genre] ?: sample[genre.ordinal % sample.size]

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
            generator.render(bookFor(genre).takeIf { st != CoverStyle.FRAMED && st != CoverStyle.GILDED } ?: book, CoverDesign(st, colours, genre, 7), 268, 362)
        }
        sheet("designs", covers, 4)
    }

    /** The designs from [from] on, two of each (in the colours and for the genres they'd be given), four designs to a sheet. */
    @Test
    fun designsUpClose() {
        assumeTrue(out.isDirectory)
        val from = System.getenv("COVERS_FROM")?.toIntOrNull() ?: 25
        val only = System.getenv("COVERS_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val styles = CoverStyle.entries.filter { if (only != null) it.name in only else it.ordinal >= from }
        styles.chunked(4).forEachIndexed { n, four ->
            val covers = (0..1).flatMap { row ->
                four.map { st ->
                    val suited = st.suits.toList()
                    val genre = suited[(row * 3) % suited.size]
                    val rnd = kotlin.random.Random(st.ordinal * 31 + row * 7 + 1)
                    val colours = CoverDesign.colorsFor(st, rnd, anyColour = false, genre = genre)
                    generator.render(bookFor(genre), CoverDesign(st, colours, genre, 7 + row * 13), 402, 543)
                }
            }
            sheet("close_%02d_%s".format(n, four.first().name.lowercase()), covers, four.size)
        }
    }

    /** The designs named in COVERS_LARGE, each large and in three sets of colours, for looking closely. */
    @Test
    fun designsLarge() {
        assumeTrue(out.isDirectory)
        val only = System.getenv("COVERS_LARGE")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        for (st in CoverStyle.entries.filter { it.name in only }) {
            val suited = st.suits.toList()
            val covers = (0..2).map { k ->
                val genre = suited[(k * 2) % suited.size]
                val rnd = kotlin.random.Random(st.ordinal * 31 + k * 7 + 1)
                val colours = CoverDesign.colorsFor(st, rnd, anyColour = false, genre = genre)
                generator.render(bookFor(genre), CoverDesign(st, colours, genre, 7 + k * 13), 536, 724)
            }
            val pale = generator.render(bookFor(suited[0]), CoverDesign(st, theme("Sage"), suited[0], 20), 536, 724)
            sheet("large_" + st.name.lowercase(), covers + pale, 4)
        }
    }

    /** The scenes of the genres named in COVERS_SCENES: as a full painting (twice), framed, and in the wide-screen crop. */
    @Test
    fun scenesLarge() {
        assumeTrue(out.isDirectory)
        val only = System.getenv("COVERS_SCENES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        for (g in CoverGenre.entries.filter { it.name in only }) {
            val covers = (11..15).map { generator.render(bookFor(g), CoverDesign(CoverStyle.PAINTING, theme("Midnight"), g, it * 37), 402, 543) } +
                listOf(CoverStyle.FRAMED to 5, CoverStyle.FRAMED to 6, CoverStyle.SCROLL to 9, CoverStyle.WIDESCREEN to 7, CoverStyle.STAMP to 3).map { (st, seed) ->
                    generator.render(bookFor(g), CoverDesign(st, theme("Teal"), g, seed), 402, 543)
                }
            sheet("scene_" + g.name.lowercase(), covers, 5)
        }
    }

    /** Every figure the scenes and designs use, large and plain, to see how each is drawn. */
    @Test
    fun figures() {
        assumeTrue(out.isDirectory)
        if (System.getenv("COVERS_FIGURES") == null) return
        val cell = 300
        val cols = 8
        val draw = listOf<(Path, Float, Float, Float) -> Unit>(
            { p, x, y, t -> Figures.cloaked(p, x, y, t) },
            { p, x, y, t -> Figures.cloaked(p, x, y, t, facing = -1f, staff = false) },
            { p, x, y, t -> Figures.inCoat(p, x, y, t) },
            { p, x, y, t -> Figures.inCoat(p, x, y, t, hat = false) },
            { p, x, y, t -> Figures.man(p, x, y, t) },
            { p, x, y, t -> Figures.hero(p, x + t * 0.1f, y, t) },
            { p, x, y, t -> Figures.singer(p, x, y, t * 0.9f) },
            { p, x, y, t -> Figures.seated(p, x - t * 0.1f, y, t) },
            { p, x, y, t -> Figures.wizard(p, x - t * 0.1f, y, t) },
            { p, x, y, t -> Figures.couple(p, x, y, t) },
            { p, x, y, t -> Figures.couple(p, x, y, t, gowned = false) },
            { p, x, y, t -> Figures.periodCouple(p, x, y, t * 0.95f) },
            { p, x, y, t -> Figures.soldier(p, x, y, t * 0.9f) },
            { p, x, y, t -> Figures.detective(p, x, y, t) },
            { p, x, y, t -> Figures.onBroom(p, x, y - t * 0.4f, t * 0.7f) },
            { p, x, y, t -> Figures.cat(p, x + t * 0.1f, y, t * 0.7f) },
            { p, x, y, t -> Figures.wolf(p, x + t * 0.2f, y, t * 0.8f) },
            { p, x, y, t -> Figures.crane(p, x, y - t * 0.45f, t * 0.95f) },
            { p, x, y, t -> Painter(Canvas(), android.graphics.RectF(0f, 0f, 100f, 100f), kotlin.random.Random(1)).underUmbrella(p, x, y, t * 0.8f, two = true) },
            { p, x, y, t -> Painter(Canvas(), android.graphics.RectF(0f, 0f, 100f, 100f), kotlin.random.Random(1)).underUmbrella(p, x, y, t * 0.8f, two = false) },
        )
        val rows = (draw.size + 4 + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xE9, 0xE0, 0xCC))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x18, 0x1C, 0x22) }
        draw.forEachIndexed { i, d ->
            val path = Path()
            d(path, (i % cols + 0.5f) * cell, (i / cols + 0.94f) * cell, cell * 0.78f)
            c.drawPath(path, ink)
        }
        // The owl with its lines, and the cranes in their colours.
        var i = draw.size
        val painter = Painter(c, android.graphics.RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), kotlin.random.Random(3))
        val outline = Path()
        val lines = Path()
        Figures.owl(outline, (i % cols + 0.5f) * cell, (i / cols + 0.8f) * cell, cell * 0.66f, lines)
        c.drawPath(outline, ink)
        c.drawPath(lines, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x7A, 0x86, 0x96); style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND })
        i++
        for (wings in listOf(1f, -0.7f, 0.8f)) {
            painter.tancho((i % cols + 0.5f) * cell, (i / cols + 0.55f) * cell, cell * 0.82f, false, Color.WHITE, Color.rgb(0x18, 0x1C, 0x22), Color.rgb(0xC2, 0x36, 0x2B), wings = wings, line = Color.rgb(0x18, 0x1C, 0x22))
            i++
        }
        File(out, "figures.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Every genre's scene as a painting, large enough to see who and what is in it, eight to a sheet. */
    @Test
    fun scenesAll() {
        assumeTrue(out.isDirectory)
        if (System.getenv("COVERS_ALL_SCENES") == null) return
        CoverGenre.entries.chunked(8).forEachIndexed { n, eight ->
            sheet("all_%02d".format(n), eight.map { g -> generator.render(bookFor(g), CoverDesign(CoverStyle.PAINTING, theme("Midnight"), g, 23), 402, 543) }, 4)
        }
    }

    /** Every genre's scene as the wide-screen design crops it, to see that nothing is left hanging in the air. */
    @Test
    fun scenesInWideFrame() {
        assumeTrue(out.isDirectory)
        CoverGenre.entries.chunked(12).forEachIndexed { n, twelve ->
            sheet("wide_%02d".format(n), twelve.map { g -> generator.render(bookFor(g), CoverDesign(CoverStyle.WIDESCREEN, theme("Midnight"), g, 11), 268, 362) }, 6)
        }
    }

    @Test
    fun everyScene() {
        assumeTrue(out.isDirectory)
        sheet("scenes", CoverGenre.entries.map { g -> generator.render(bookFor(g), CoverDesign(CoverStyle.PAINTING, theme("Midnight"), g, 11), 268, 362) }, 4)
    }

    @Test
    fun framedInEveryGenre() {
        assumeTrue(out.isDirectory)
        sheet("framed", CoverGenre.entries.mapIndexed { i, g -> generator.render(bookFor(g), CoverDesign(CoverStyle.FRAMED, CoverDesign.boardFor(i + 1.0)!!, g, 5), 268, 362) }, 4)
    }

    @Test
    fun gildedInEveryGenre() {
        assumeTrue(out.isDirectory)
        val boards = listOf("Teal", "Midnight", "Claret", "Forest", "Plum", "Umber", "Charcoal", "Slate")
        sheet("gilded", CoverGenre.entries.mapIndexed { i, g -> generator.render(bookFor(g), CoverDesign(CoverStyle.GILDED, theme(boards[i % boards.size]), g, 3), 268, 362) }, 4)
    }

    private val sample = listOf(
        CoverText("The Iron Crown", listOf("Mara Ellison"), "The Ninefold Gate", 6.0),
        CoverText("A Death in Fog Lane", listOf("Agnes Brooke"), null, null),
        CoverText("Letters to Juniper", listOf("Clara Wells"), "Seaside Hearts", 1.0),
        CoverText("The House on Wren Hill", listOf("Evelyn Marsh"), null, null),
    )

    @Test
    fun scenesByGroup() {
        assumeTrue(out.isDirectory)
        for (group in CoverGenre.Group.entries) {
            val covers = group.genres.mapIndexed { i, g ->
                generator.render(sample[i % sample.size], CoverDesign(CoverStyle.PAINTING, theme("Midnight"), g, 11 + i), 268, 362)
            }
            sheet("scenes_${group.name.lowercase()}", covers, 4)
        }
    }

    @Test
    fun stampsByGroup() {
        assumeTrue(out.isDirectory)
        val boards = CoverColors.boards
        for (group in CoverGenre.Group.entries) {
            val covers = group.genres.mapIndexed { i, g ->
                generator.render(sample[i % sample.size], CoverDesign(CoverStyle.GILDED, boards[i % boards.size], g, 3 + i), 268, 362)
            }
            sheet("stamps_${group.name.lowercase()}", covers, 4)
        }
    }

    @Test
    fun newDesigns() {
        assumeTrue(out.isDirectory)
        val picks = listOf(
            Triple(CoverStyle.GRIMOIRE, CoverGenre.MAGIC_SCHOOL, CoverColors.CRIMSON), Triple(CoverStyle.GRIMOIRE, CoverGenre.DARK_MAGIC, CoverColors.EMERALD),
            Triple(CoverStyle.THORNS, CoverGenre.DARK_ROMANCE, CoverColors.OBSIDIAN), Triple(CoverStyle.THORNS, CoverGenre.ROMANTASY, CoverColors.PLUM),
            Triple(CoverStyle.NEON, CoverGenre.CYBERPUNK, CoverColors.SYNTH), Triple(CoverStyle.NEON, CoverGenre.SCIENCE_FICTION, CoverColors.MIDNIGHT),
            Triple(CoverStyle.PULP, CoverGenre.ADVENTURE, CoverColors.CRIMSON), Triple(CoverStyle.PULP, CoverGenre.SPACE_OPERA, CoverColors.SAPPHIRE),
            Triple(CoverStyle.POSTER, CoverGenre.WAR, CoverColors.UMBER), Triple(CoverStyle.POSTER, CoverGenre.SPORTS, CoverColors.SAPPHIRE),
            Triple(CoverStyle.SNAPSHOT, CoverGenre.FLUFF, CoverColors.LAVENDER), Triple(CoverStyle.SNAPSHOT, CoverGenre.SLICE_OF_LIFE, CoverColors.MINT),
        )
        sheet("new_designs", picks.mapIndexed { i, (st, g, colours) -> generator.render(sample[i % sample.size], CoverDesign(st, colours, g, 5 + i), 268, 362) }, 4)
    }

    @Test
    fun nightDesigns() {
        assumeTrue(out.isDirectory)
        val picks = listOf(
            Triple(CoverStyle.WAND, CoverGenre.MAGIC_SCHOOL, CoverColors.CRIMSON), Triple(CoverStyle.WAND, CoverGenre.FANTASY, CoverColors.MIDNIGHT),
            Triple(CoverStyle.WAND, CoverGenre.DARK_MAGIC, CoverColors.EMERALD), Triple(CoverStyle.WAND, CoverGenre.WITCHES, CoverColors.PARCHMENT),
            Triple(CoverStyle.OWL, CoverGenre.MAGIC_SCHOOL, CoverColors.MIDNIGHT), Triple(CoverStyle.OWL, CoverGenre.FANTASY, CoverColors.TEAL),
            Triple(CoverStyle.OWL, CoverGenre.CHILDREN, CoverColors.SAFFRON), Triple(CoverStyle.OWL, CoverGenre.LITERARY, CoverColors.INK),
            Triple(CoverStyle.RAVEN, CoverGenre.DARK_MAGIC, CoverColors.OBSIDIAN), Triple(CoverStyle.RAVEN, CoverGenre.VAMPIRES, CoverColors.CLARET),
            Triple(CoverStyle.RAVEN, CoverGenre.MYSTERY, CoverColors.EMERALD), Triple(CoverStyle.RAVEN, CoverGenre.DARK_ROMANCE, CoverColors.LAVENDER),
        )
        sheet("night_designs", picks.mapIndexed { i, (st, g, colours) -> generator.render(sample[i % sample.size], CoverDesign(st, colours, g, 21 + i), 268, 362) }, 4)
    }

    @Test
    fun grounded() {
        assumeTrue(out.isDirectory)
        val picks = listOf(
            CoverGenre.HORROR, CoverGenre.VAMPIRES, CoverGenre.HALLOWEEN, CoverGenre.DARK_MAGIC, CoverGenre.KNIGHTS, CoverGenre.CHRISTMAS,
            CoverGenre.WAR, CoverGenre.QUEST, CoverGenre.STORM_EPIC, CoverGenre.WITCHES, CoverGenre.CHILDREN, CoverGenre.MAGIC_SCHOOL,
            CoverGenre.FANTASY, CoverGenre.FANTASY, CoverGenre.ANCIENT, CoverGenre.HISTORICAL,
        )
        sheet("grounded", picks.mapIndexed { i, g -> generator.render(bookFor(g), CoverDesign(CoverStyle.PAINTING, theme("Midnight"), g, 40 + i * 7), 268, 362) }, 4)
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
        // The night designs and the lake under the academy, large enough to see the owl on its branch.
        val large = listOf(
            "raven_large" to CoverDesign(CoverStyle.RAVEN, theme("Obsidian"), CoverGenre.DARK_MAGIC, 21),
            "wand_large" to CoverDesign(CoverStyle.WAND, theme("Crimson"), CoverGenre.MAGIC_SCHOOL, 21),
            "academy_large" to CoverDesign(CoverStyle.PAINTING, theme("Midnight"), CoverGenre.MAGIC_SCHOOL, 51),
        )
        val pair = listOf(book, sample[3])
        for ((name, design) in large) {
            val covers = pair.map { generator.render(it, design.copy(seed = design.seed + pair.indexOf(it)), 536, 724) }
            sheet(name, covers, 2)
        }
    }
}
