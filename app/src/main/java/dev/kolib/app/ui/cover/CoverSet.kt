package dev.kolib.app.ui.cover

import androidx.core.graphics.ColorUtils
import kotlin.random.Random

/**
 * Covers in one design for a set of books (the books a split makes): every one in [design], or with [eachColour] each
 * in a colour scheme of its own, from all the built-in ones. [round] 0 starts with the design's own; each round after
 * deals them all out afresh ("Other colours").
 */
data class CoverSet(val design: CoverDesign, val eachColour: Boolean = false, val round: Int = 0) {
    /** The design for book [k] of [n]. */
    fun designFor(k: Int, n: Int): CoverDesign = if (!eachColour || !design.style.themed) design else design.copy(colors = colours(n)[k])

    /**
     * [n] colour schemes for the books, drawn by chance from every built-in one, none much like one drawn before it
     * (as unlike as there are enough to be); round again when there are more books than schemes.
     */
    fun colours(n: Int): List<CoverColors> {
        val rnd = Random(design.seed * 31 + round * 7919 + 17)
        val out = mutableListOf<CoverColors>()
        if (round == 0) out += design.colors
        val left = CoverColors.themes.filter { c -> out.none { it.name == c.name } }.shuffled(rnd).toMutableList()
        var apart = APART
        while (out.size < n && left.isNotEmpty()) {
            val next = left.firstOrNull { c -> out.all { unlike(it, c) >= apart } }
            if (next == null) {
                apart = (apart - 4.0).coerceAtLeast(0.0)
                continue
            }
            out += next
            left -= next
        }
        val distinct = out.size
        while (out.size < n) out += out[out.size % distinct]
        return out
    }

    private fun unlike(a: CoverColors, b: CoverColors): Double {
        val la = DoubleArray(3)
        val lb = DoubleArray(3)
        ColorUtils.colorToLAB(a.ground, la)
        ColorUtils.colorToLAB(b.ground, lb)
        return ColorUtils.distanceEuclidean(la, lb)
    }

    companion object {
        /** How unlike (the distance between their boards' colours, in Lab) the books' colours are kept, while they can be. */
        private const val APART = 26.0

        /** [count] designs to suggest for a [genre]: different styles that suit it (others when it has too few), each in colours that suit both. */
        fun suggestions(genre: CoverGenre, count: Int, rnd: Random = Random.Default): List<CoverDesign> {
            val suited = CoverStyle.entries.filter { genre in it.suits }.shuffled(rnd).take(count)
            val others = CoverStyle.entries.filter { it !in suited }.shuffled(rnd).take(count - suited.size)
            return (suited + others).map { CoverDesign(it, CoverDesign.colorsFor(it, rnd, anyColour = false, genre = genre), genre, rnd.nextInt()) }
        }
    }
}
