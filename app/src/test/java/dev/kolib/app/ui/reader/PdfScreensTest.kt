package dev.kolib.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which pages of a PDF go on each screen. */
class PdfScreensTest {

    private fun screens(count: Int, two: Boolean, wide: Set<Int> = emptySet()) =
        pdfScreens(count, two) { it in wide }.map { it.toList() }

    @Test
    fun oneAtATime() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), screens(3, two = false))
    }

    @Test
    fun twoSideBySideWithTheCoverAlone() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), screens(6, two = true))
        assertEquals(listOf(listOf(0), listOf(1, 2)), screens(3, two = true))
        assertEquals(listOf(listOf(0)), screens(1, two = true))
        assertEquals(emptyList<List<Int>>(), screens(0, two = true))
    }

    @Test
    fun aWidePageHasTheScreenToItself() {
        // Page 3 is a map across two pages.
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5), listOf(6)), screens(7, two = true, wide = setOf(3)))
        // A wide page next to a page that would have been its partner.
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)), screens(5, two = true, wide = setOf(2)))
    }
}
