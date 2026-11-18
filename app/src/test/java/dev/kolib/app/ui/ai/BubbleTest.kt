package dev.kolib.app.ui.ai

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BubbleTest {
    private val area = Rect(0f, 0f, 1000f, 2000f)

    /** Where a bubble thrown from [at] at [velocity] comes to rest, and how many sixtieths of a second it takes. */
    private fun rest(at: Offset, velocity: Offset): Pair<Offset, Int> {
        var p = at
        var v = velocity
        var steps = 0
        while (v.getDistance() > 40f && steps < 2_000) {
            val (np, nv) = glide(p, v, 1 / 60f, area)
            p = np
            v = nv
            steps++
        }
        return p to steps
    }

    @Test
    fun aThrownBubbleSlidesOnAndSlowsToAStop() {
        val (p, steps) = rest(Offset(200f, 1000f), Offset(1_500f, 0f))
        // It went on the way it was thrown, about as far as its speed over its slowing (1500 / 3.2), and stopped.
        assertEquals(1000f, p.y, 0.01f)
        assertTrue("it got to ${p.x}", p.x in 600f..700f)
        assertTrue("$steps steps", steps in 30..150)
    }

    @Test
    fun itBouncesOffTheEdgesAndStaysInside() {
        // Thrown hard at the right: it bounces back off the edge.
        val (p, v) = glide(Offset(990f, 100f), Offset(3_000f, 0f), 0.02f, area)
        assertTrue(p.x < 1000f)
        assertTrue(v.x < 0f)
        // However it's thrown, it ends up inside.
        val r = Random(7)
        repeat(200) {
            val (end, _) = rest(Offset(r.nextFloat() * 1000f, r.nextFloat() * 2000f), Offset(r.nextFloat() * 18_000f - 9_000f, r.nextFloat() * 18_000f - 9_000f))
            assertTrue("$end", end.x in 0f..1000f && end.y in 0f..2000f)
        }
    }

    @Test
    fun theWindowWarpsTowardsTheSideTheBubbleOrButtonIsOn() {
        // A phone's window across the foot of the screen.
        val window = Rect(12f, 690f, 604f, 1324f)
        // The bubble at the right edge, part way down the window: in from the side.
        assertEquals(2f, Warp.way(window, Offset(576f, 960f)))
        assertEquals(3f, Warp.way(window, Offset(40f, 960f)))
        // The AI button at the top: up into it.
        assertEquals(1f, Warp.way(window, Offset(446f, 40f)))
        // A window up top, and the bubble below it.
        assertEquals(0f, Warp.way(Rect(12f, 100f, 604f, 700f), Offset(300f, 1200f)))
    }
}
