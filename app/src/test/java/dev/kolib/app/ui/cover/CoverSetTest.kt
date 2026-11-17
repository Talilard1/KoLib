package dev.kolib.app.ui.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Covers in one design for the books a split makes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverSetTest {
    private val design = CoverDesign(CoverStyle.FRAMED, CoverColors.MIDNIGHT, CoverGenre.HISTORICAL, 7)

    @Test
    fun everyBookGetsTheDesignAsItIs() {
        val set = CoverSet(design)
        assertTrue((0 until 9).all { set.designFor(it, 9) == design })
    }

    @Test
    fun eachInItsOwnColourStartsWithTheDesignsAndRepeatsNoneWhileThereAreEnough() {
        val set = CoverSet(design, eachColour = true)
        val colours = (0 until 9).map { set.designFor(it, 9) }
        assertEquals(CoverColors.MIDNIGHT, colours[0].colors)
        assertEquals(9, colours.map { it.colors.name }.distinct().size)
        // Only the colours change, and they're built-in ones.
        assertTrue(colours.all { it.copy(colors = design.colors) == design })
        assertTrue(colours.all { it.colors in CoverColors.themes })
        // The same each time: the covers shown before a split are the ones it makes.
        assertEquals(colours, (0 until 9).map { CoverSet(design, eachColour = true).designFor(it, 9) })
    }

    /** "Other colours": each round deals them out afresh, from every built-in scheme, light and dark. */
    @Test
    fun otherColoursComeFromAllTheSchemes() {
        val rounds = (0 until 120).map { CoverSet(design, eachColour = true, round = it).colours(9) }
        assertTrue(rounds.all { r -> r.size == 9 && r.map { it.name }.distinct().size == 9 })
        assertTrue(rounds[1] != rounds[0] && rounds[2] != rounds[1])
        assertTrue(rounds.drop(1).any { it[0] != CoverColors.MIDNIGHT })
        assertEquals(CoverColors.themes.map { it.name }.toSet(), rounds.flatten().map { it.name }.toSet())
        // Nine from 43: none much like another.
        val unlike = rounds.count { r -> r.indices.all { i -> r.indices.all { j -> i == j || lab(r[i], r[j]) >= 20 } } }
        assertTrue("only $unlike of ${rounds.size}", unlike >= rounds.size * 9 / 10)
    }

    private fun lab(a: CoverColors, b: CoverColors): Double {
        val la = DoubleArray(3)
        val lb = DoubleArray(3)
        androidx.core.graphics.ColorUtils.colorToLAB(a.ground, la)
        androidx.core.graphics.ColorUtils.colorToLAB(b.ground, lb)
        return androidx.core.graphics.ColorUtils.distanceEuclidean(la, lb)
    }

    @Test
    fun moreBooksThanColoursGoRoundAgain() {
        val colours = CoverSet(design, eachColour = true).colours(200)
        assertEquals(200, colours.size)
        assertEquals(CoverColors.themes.size, colours.map { it.name }.distinct().size)
        assertEquals(colours[0], colours[CoverColors.themes.size])
    }

    @Test
    fun aDesignInItsPicturesColoursKeepsThem() {
        val pictured = CoverStyle.entries.firstOrNull { !it.themed } ?: return
        val d = design.copy(style = pictured)
        assertTrue((0 until 4).all { CoverSet(d, eachColour = true).designFor(it, 4) == d })
    }

    @Test
    fun suggestionsAreDifferentDesignsThatSuitTheGenre() {
        for (genre in CoverGenre.entries) {
            val s = CoverSet.suggestions(genre, 6, Random(genre.ordinal))
            assertEquals(6, s.size)
            assertEquals(6, s.map { it.style }.distinct().size)
            val suited = CoverStyle.entries.count { genre in it.suits }
            assertEquals(minOf(6, suited), s.count { genre in it.style.suits })
            assertTrue(s.all { it.genre == genre })
        }
    }
}
