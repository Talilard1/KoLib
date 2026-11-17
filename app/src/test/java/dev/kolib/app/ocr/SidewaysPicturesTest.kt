package dev.kolib.app.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a picture printed on its side from how its words read each way; and turning a grey picture. */
class SidewaysPicturesTest {

    @Test
    fun theWayItReadsBest() {
        // Read on its side as it is, words turned clockwise: turn it clockwise.
        assertEquals(1, SidewaysPictures.decide(upright = 0, clockwise = 7, anticlockwise = 0))
        assertEquals(3, SidewaysPictures.decide(upright = 1, clockwise = 0, anticlockwise = 5))
        // Reads as it is: leave it.
        assertEquals(0, SidewaysPictures.decide(upright = 7, clockwise = 0, anticlockwise = 1))
        // Not enough to tell either way (a picture with no words, a word or two by chance).
        assertNull(SidewaysPictures.decide(upright = 0, clockwise = 2, anticlockwise = 0))
        assertNull(SidewaysPictures.decide(upright = 0, clockwise = 0, anticlockwise = 0))
        // Some words each way: only a turn that reads plainly better.
        assertEquals(0, SidewaysPictures.decide(upright = 4, clockwise = 6, anticlockwise = 0))
        assertEquals(1, SidewaysPictures.decide(upright = 2, clockwise = 9, anticlockwise = 0))
    }

    @Test
    fun whatCountsAsAWordReadSurely() {
        assertTrue(SidewaysPictures.sure("“Good", 91))
        assertTrue(SidewaysPictures.sure("morning.”", 88))
        assertFalse(SidewaysPictures.sure("morning", 60))
        assertFalse(SidewaysPictures.sure("~~", 95))
        assertFalse(SidewaysPictures.sure("a1;7", 95))
        assertFalse(SidewaysPictures.sure("of", 96))
    }

    @Test
    fun aQuarterTurnEachWay() {
        // 3 wide, 2 tall:  1 2 3
        //                  4 5 6
        val p = GrayPicture(byteArrayOf(1, 2, 3, 4, 5, 6), 3, 2)
        val cw = p.turned(1)
        assertEquals(2, cw.width)
        assertEquals(3, cw.height)
        // Clockwise: 4 1 / 5 2 / 6 3.
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), cw.pixels)
        // Anticlockwise: 3 6 / 2 5 / 1 4.
        assertArrayEquals(byteArrayOf(3, 6, 2, 5, 1, 4), p.turned(3).pixels)
        // And back.
        assertArrayEquals(p.pixels, cw.turned(3).pixels)
        assertTrue(p.turned(2) === p)
    }
}
