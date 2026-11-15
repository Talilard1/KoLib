package dev.kobolibrary.app.ui.opening

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The threads of light swirl in as they always have, then run into the two points drawing the book's pages,
 * which meet at the foot of the spine and set off the flash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningThreadsTest {

    private fun path(d: String) = PathParser().parsePathString(d).toPath()
    private val left = MarkTrack(path("M32,17 C25,11.5 15,10.5 6,12.5 V50 C15,48 25,49 32,54.5"))
    private val right = MarkTrack(path("M32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5"))
    private val threads = OpeningThreads.make(Random(7), left, right)

    private fun pen(side: Int, t: Float) = (if (side == 0) left else right).at(OpeningThreads.pages(t))

    @Test
    fun theSwirlIsAsItAlwaysWasUntilEachThreadGoesOver() {
        assertEquals(13, threads.size)
        val center = Offset(300f, 400f)
        for (th in threads) {
            // The same way round as before: from far out in to circle the book, going round faster.
            val until = minOf(th.leaves, Opening.SWIRL_END)
            for (k in 0..10) {
                val t = Opening.SWIRL_START + th.delay + (until - Opening.SWIRL_START - th.delay) * k / 10f
                val v = th.along(t).coerceAtMost(1f)
                val r = 26f + (150f - 26f) * (1f - v).pow(1.5f)
                val phi = th.angle + (2.3f * PI.toFloat() * v + 1.6f * PI.toFloat() * v * v)
                val x = cos(phi) * r
                val y = sin(phi) * r * th.squash
                val was = Offset(center.x + x * cos(th.tilt) - y * sin(th.tilt), center.y + x * sin(th.tilt) + y * cos(th.tilt))
                val now = th.at(t, center, 150f, 26f) { s, time -> pen(s, time) }
                assertEquals("${th.angle} at $t", 0f, (now - was).getDistance(), 0.01f)
            }
        }
    }

    @Test
    fun eachRunsIntoAPagesPointAsItsCirclingBringsItPast() {
        val joins = threads.map { it.joined }
        assertTrue("from about when the pages start (${joins.min()})", joins.min() >= Opening.DRAW_START - 0.021f)
        assertTrue("all in well before the points meet (${joins.max()})", joins.max() <= OpeningThreads.ALL_IN + 0.001f)
        // Both pages' points are made of threads.
        assertTrue(threads.count { it.side == 0 } >= 4)
        assertTrue(threads.count { it.side == 1 } >= 4)
        val center = MarkPoints.MIDDLE
        for (th in threads) {
            // Its circling brings it close by the point, so it runs straight on in rather than across the book
            // (the first ones, as the pages start, while the threads still circle wide).
            val pass = (th.swirl(th.along(th.joined), center, 150f, 26f) - pen(th.side, th.joined)).getDistance()
            assertTrue("${th.angle}: passes $pass units away", pass < (if (th.joined < 2.1f) 22f else 16f))
            // And then it's the point itself.
            val at = th.at(th.joined, center, 150f, 26f) { s, time -> pen(s, time) }
            assertEquals(0f, (at - pen(th.side, th.joined)).getDistance(), 0.001f)
        }
        // The points grow brighter as the threads run in.
        for (side in 0..1) {
            assertEquals(0f, OpeningThreads.joinedInto(threads, side, Opening.DRAW_START - 0.2f), 1e-4f)
            assertEquals(1f, OpeningThreads.joinedInto(threads, side, OpeningThreads.BEAD_START), 1e-4f)
        }
    }

    @Test
    fun thePagesMeetAndRiseToTheMiddleForTheFlash() {
        assertEquals(0f, OpeningThreads.pages(Opening.DRAW_START), 1e-4f)
        assertEquals(1f, OpeningThreads.pages(Opening.DRAW_END), 1e-4f)
        assertNull(OpeningThreads.rising(OpeningThreads.BEAD_START - 0.01f))
        val (start, s0) = OpeningThreads.rising(OpeningThreads.BEAD_START)!!
        assertEquals(MarkPoints.SPINE_FOOT, start)
        assertEquals(0f, s0, 1e-4f)
        val (end, s1) = OpeningThreads.rising(Opening.FLASH)!!
        assertEquals(MarkPoints.MIDDLE.y, end.y, 1e-3f)
        assertEquals(1f, s1, 1e-4f)
        assertNull(OpeningThreads.rising(Opening.FLASH + 0.1f))
        assertTrue(OpeningThreads.BEAD_START < Opening.DRAW_END && OpeningThreads.BEAD_START > Opening.RIBBON_START)
    }

    @Test
    fun theRibbonsSparkIsLeftEarlyOnTheRightHandPage() {
        val seed = OpeningThreads.nearestAlong(path("M32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5"), MarkPoints.RIBBON_TOP)
        // Along the top of the right-hand page, before its corner.
        assertTrue("$seed", seed in 0.05f..0.25f)
    }
}
