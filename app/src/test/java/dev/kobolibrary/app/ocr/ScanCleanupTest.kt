package dev.kobolibrary.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page behind showing through a scan is taken out before it's read; the print stays, darker. */
class ScanCleanupTest {

    private fun page(w: Int, h: Int, paint: (x: Int, y: Int) -> Int) = ByteArray(w * h) { paint(it % w, it / w).toByte() }
    private fun at(b: ByteArray, w: Int, x: Int, y: Int) = b[y * w + x].toInt() and 0xff

    @Test
    fun theFaintWordsBehindGoAndThePrintStays() {
        val w = 200
        val h = 100
        // Paper at 222; print (ten rows of it) at 30; the page behind showing through (other rows) at 165.
        val g = page(w, h) { x, y ->
            when {
                y in 10..19 && x in 20..180 && x % 3 != 0 -> 30
                y in 40..49 && x in 20..180 && x % 2 == 0 -> 165
                else -> 222
            }
        }
        val c = ScanCleanup.clean(g, w, h)
        assertEquals(0, at(c, w, 22, 12))
        assertEquals(255, at(c, w, 22, 42))
        assertEquals(255, at(c, w, 5, 5))
    }

    @Test
    fun pagesOfPicturesAndBlankOnesAreLeftAsTheyAre() {
        // Half the page dark: a photograph.
        val photo = page(100, 100) { x, _ -> if (x < 50) 40 else 200 }
        assertSame(photo, ScanCleanup.clean(photo, 100, 100))
        // Nothing but paper.
        val blank = page(100, 100) { _, _ -> 230 }
        assertSame(blank, ScanCleanup.clean(blank, 100, 100))
        // Faint all over (a pale scan with no strong print): nothing to tell print from paper by.
        val pale = page(100, 100) { x, _ -> 180 + x % 30 }
        assertSame(pale, ScanCleanup.clean(pale, 100, 100))
        assertTrue(ScanCleanup.clean(ByteArray(0), 0, 0).isEmpty())
    }
}
