package dev.kobolibrary.app.ui.opening

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Points of the book mark, in its own 64 x 64 space. */
internal object MarkPoints {
    /** The middle of the mark: what's centred on screen, and where the flash goes off. */
    val MIDDLE = Offset(32f, 32.85f)
    /** The top of the spine, where both pages start being drawn from. */
    val SPINE_TOP = Offset(32f, 17f)
    /** The foot of the spine, where the two pages' lines meet. */
    val SPINE_FOOT = Offset(32f, 54.5f)
    /** Where the ribbon hangs from the top of the right-hand page. */
    val RIBBON_TOP = Offset(44f, 12f)

    /** [p] on screen, with the mark's middle at [center] and [unit] pixels to one of its units. */
    fun onScreen(p: Offset, center: Offset, unit: Float) = Offset(center.x + (p.x - MIDDLE.x) * unit, center.y + (p.y - MIDDLE.y) * unit)
}

/** A line of the mark measured once, so where along it something is can be found quickly while drawing. */
internal class MarkTrack(path: Path, private val samples: Int = 160) {
    private val xs = FloatArray(samples + 1)
    private val ys = FloatArray(samples + 1)

    init {
        val measure = PathMeasure()
        measure.setPath(path, false)
        val length = measure.length
        for (k in 0..samples) {
            val p = measure.getPosition(length * k / samples)
            xs[k] = p.x
            ys[k] = p.y
        }
    }

    /** The point [f] of the way along (0 to 1), in the mark's space. */
    fun at(f: Float): Offset {
        val x = f.coerceIn(0f, 1f) * samples
        val i = min(x.toInt(), samples - 1)
        val w = x - i
        return Offset(xs[i] + (xs[i + 1] - xs[i]) * w, ys[i] + (ys[i + 1] - ys[i]) * w)
    }
}

/**
 * A thread of light swirling in out of the dark, as it always has: it sets off [delay] after the swirl starts,
 * from [angle] round and beyond the screen's edge, and turns in to circle the book. Then, as its circling brings
 * it past one of the two points drawing the book's pages ([side]: 0 the left-hand page's, 1 the right's), it runs
 * into it, taking [reach] seconds to and arriving at [joined]: the threads together make the lines that draw the book.
 */
internal class OpeningThread(
    val angle: Float, val delay: Float, val squash: Float, val tilt: Float,
    val width: Float, val length: Float, val bright: Float,
    val joined: Float, val side: Int, reach: Float = 0.1f,
) {
    private val setOff = Opening.SWIRL_START + delay
    /** Seconds from setting off to the end of the swirl. */
    private val span = Opening.SWIRL_END - Opening.SWIRL_START - delay
    val leaves = joined - reach
    /** How long its tail is, in seconds of its way. */
    val tailTime = length * span

    /** How far round its swirl at [t]: 0 setting off, 1 at the swirl's end (after which it goes on round). */
    fun along(t: Float): Float = (t - setOff) / span

    /** Where on its swirl it is [v] of the way round [center]: from [from] pixels out, in to circle at [ring]. */
    fun swirl(v: Float, center: Offset, from: Float, ring: Float): Offset {
        val w = v.coerceAtLeast(0f)
        val r = if (w >= 1f) ring else ring + (from - ring) * (1f - w).pow(1.5f)
        val phi = angle + (2.3f * PI.toFloat() * w + 1.6f * PI.toFloat() * w * w)
        val x = cos(phi) * r
        val y = sin(phi) * r * squash
        val ct = cos(tilt)
        val st = sin(tilt)
        return Offset(center.x + x * ct - y * st, center.y + x * st + y * ct)
    }

    /** How far it's gone over into its line at [t]: 0 still swirling, 1 run into it. */
    fun joining(t: Float): Float = OpeningThreads.step(leaves, joined, t)

    /** Where it is at [t]: on its swirl, then going over into its line, which [pen] says is being drawn where at a time. */
    fun at(t: Float, center: Offset, from: Float, ring: Float, pen: (Int, Float) -> Offset): Offset {
        val s = swirl(along(t), center, from, ring)
        val k = joining(t)
        if (k <= 0f) return s
        val p = pen(side, t)
        return if (k >= 1f) p else Offset(s.x + (p.x - s.x) * k, s.y + (p.y - s.y) * k)
    }
}

internal object OpeningThreads {
    /** About when the first thread runs in, as the pages start being drawn; and the last, as the threads used to fade. */
    const val FIRST_JOIN = Opening.DRAW_START + 0.03f
    const val LAST_JOIN = 2.36f
    /** All in by here, so the book's clear of their tails before its two points meet. */
    const val ALL_IN = 2.45f
    /** How long a thread's tail still shows along its line after it's run in. */
    const val AFTER_JOIN = 0.3f
    /** The ring the threads circle the book on, in the mark's units from its middle. */
    const val RING = 26f

    /** How far out the threads set off, in the mark's units, about (it depends a little on the screen's shape). */
    private const val FROM = 150f

    /**
     * The threads, drawn from [r] as they always have been (so what's drawn from it after stays the same), each
     * running into whichever page's point it passes closest to (on [left] or [right]) about its turn to.
     */
    fun make(r: Random, left: MarkTrack, right: MarkTrack): List<OpeningThread> {
        val made = List(13) { i ->
            floatArrayOf(
                (i * 2 * PI / 13).toFloat() + r.nextFloat() * 0.4f,
                r.nextFloat() * 0.4f,
                0.7f + r.nextFloat() * 0.3f,
                r.nextFloat() * PI.toFloat(),
                2.6f + r.nextFloat() * 2.0f,
                0.16f + r.nextFloat() * 0.14f,
                0.65f + r.nextFloat() * 0.35f,
            )
        }
        // They run in one after another, in the order they set off: each at the moment about its turn when its
        // circling brings it nearest one of the two points, so it runs straight on into it.
        val order = made.indices.sortedBy { made[it][1] }
        // For each thread and each point: when it passes nearest, and how near.
        val whenNear = Array(made.size) { FloatArray(2) }
        val howNear = Array(made.size) { FloatArray(2) }
        made.forEachIndexed { i, f ->
            val aim = FIRST_JOIN + (LAST_JOIN - FIRST_JOIN) * order.indexOf(i) / (made.size - 1f)
            val probe = OpeningThread(f[0], f[1], f[2], f[3], f[4], f[5], f[6], aim, 0)
            for (s in 0..1) {
                val track = if (s == 0) left else right
                whenNear[i][s] = aim
                howNear[i][s] = Float.MAX_VALUE
                var t = max(aim - 0.2f, Opening.DRAW_START - 0.02f)
                val last = min(aim + 0.2f, ALL_IN)
                while (t <= last) {
                    val d = (probe.swirl(probe.along(t), MarkPoints.MIDDLE, FROM, RING) - track.at(pages(t))).getDistance()
                    if (d < howNear[i][s]) {
                        howNear[i][s] = d
                        whenNear[i][s] = t
                    }
                    t += 0.005f
                }
            }
        }
        // Each to the point it passes nearer; then about as many to each, where one passes near both.
        val side = IntArray(made.size) { i -> if (howNear[i][0] <= howNear[i][1]) 0 else 1 }
        while (true) {
            val more = if (side.count { it == 0 } > side.count { it == 1 }) 0 else 1
            if (side.count { it == more } - side.count { it != more } <= 1) break
            val swap = side.indices.filter { side[it] == more }.minByOrNull { howNear[it][1 - more] - howNear[it][more] } ?: break
            if (howNear[swap][1 - more] - howNear[swap][more] > 10f) break
            side[swap] = 1 - more
        }
        return made.mapIndexed { i, f ->
            val s = side[i]
            // In a moment when it passes right by; a little longer when it has further to go.
            OpeningThread(f[0], f[1], f[2], f[3], f[4], f[5], f[6], whenNear[i][s], s, reach = (0.08f + howNear[i][s] * 0.006f).coerceAtMost(0.24f))
        }
    }

    /** How far the pages' lines are drawn at [t] (0 to 1), from the top of the spine round to its foot. */
    fun pages(t: Float): Float = easeInOut(((t - Opening.DRAW_START) / (Opening.DRAW_END - Opening.DRAW_START)).coerceIn(0f, 1f))

    /** How much of the threads going to [side]'s line have run into it by [t] (0 to 1): it grows brighter with them. */
    fun joinedInto(threads: List<OpeningThread>, side: Int, t: Float): Float {
        var all = 0
        var into = 0f
        for (th in threads) {
            if (th.side != side) continue
            all++
            into += step(th.joined - 0.12f, th.joined, t)
        }
        return if (all == 0) 1f else into / all
    }

    /**
     * Where the pages' lines, met at the foot of the spine, go next: up the middle of the book to the flash, from
     * [BEAD_START] to the flash. Null before and after; else its place in the mark's space, and how far (0 to 1).
     */
    fun rising(t: Float): Pair<Offset, Float>? {
        if (t < BEAD_START || t > Opening.FLASH + 0.05f) return null
        val s = ((t - BEAD_START) / (Opening.FLASH - BEAD_START)).coerceIn(0f, 1f)
        val k = s * s
        val foot = MarkPoints.SPINE_FOOT
        val middle = MarkPoints.MIDDLE
        return Offset(foot.x, foot.y + (middle.y - foot.y) * k) to s
    }

    /** When the two pages' lines have all but met at the foot of the spine and go on as one. */
    const val BEAD_START = Opening.DRAW_END - 0.06f

    /** How far along [path] (0 to 1) it comes nearest to [p]. */
    fun nearestAlong(path: Path, p: Offset): Float {
        val measure = PathMeasure()
        measure.setPath(path, false)
        val length = measure.length
        var best = 0f
        var bestDistance = Float.MAX_VALUE
        for (k in 0..400) {
            val q = measure.getPosition(length * k / 400f)
            val d = (q - p).getDistanceSquared()
            if (d < bestDistance) {
                bestDistance = d
                best = k / 400f
            }
        }
        return best
    }

    fun step(a: Float, b: Float, x: Float): Float {
        val v = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
}
