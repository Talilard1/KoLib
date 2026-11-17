package dev.kobolibrary.app.ui.cover

import android.graphics.Path
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Builds a figure out of closed shapes that all wind the same way, so they join wherever they overlap (and a figure
 * turned to face the other way joins with whatever else is in the same path). Places are given across the figure
 * (towards the way it faces) and up from its feet, both in its own heights.
 */
internal class Sil(private val into: Path, private val ox: Float, private val oy: Float, private val k: Float, private val dir: Float = 1f) {
    fun x(a: Float) = ox + dir * a * k
    fun y(b: Float) = oy - b * k

    private fun add(xs: FloatArray, ys: FloatArray, n: Int) {
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += xs[i] * ys[j] - xs[j] * ys[i]
        }
        if (area >= 0f) {
            into.moveTo(xs[0], ys[0])
            for (i in 1 until n) into.lineTo(xs[i], ys[i])
        } else {
            into.moveTo(xs[n - 1], ys[n - 1])
            for (i in n - 2 downTo 0) into.lineTo(xs[i], ys[i])
        }
        into.close()
    }

    /** A shape with straight sides through these places. */
    fun poly(vararg p: Float) {
        val n = p.size / 2
        add(FloatArray(n) { x(p[it * 2]) }, FloatArray(n) { y(p[it * 2 + 1]) }, n)
    }

    /** A shape with a smooth outline through these places; a place given twice running is a sharp corner. */
    fun round(vararg p: Float) {
        // The places, without the repeats, and which of them are corners.
        val pa = ArrayList<Float>()
        val pb = ArrayList<Float>()
        val corner = ArrayList<Boolean>()
        val count = p.size / 2
        for (i in 0 until count) {
            val a = p[i * 2]
            val b = p[i * 2 + 1]
            if (pa.isNotEmpty() && pa.last() == a && pb.last() == b) corner[corner.size - 1] = true
            else {
                pa.add(a); pb.add(b); corner.add(false)
            }
        }
        val n = pa.size
        val sub = 10
        val xs = FloatArray(n * sub)
        val ys = FloatArray(n * sub)
        fun at(i: Int) = ((i % n) + n) % n
        for (i in 0 until n) {
            // Past a corner the line doesn't look back, and before one it doesn't look on.
            val i0 = if (corner[at(i)]) i else i - 1
            val i3 = if (corner[at(i + 1)]) i + 1 else i + 2
            for (k in 0 until sub) {
                val f = k / sub.toFloat()
                fun sp(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (3 * b - a - 3 * c + d) * f * f * f)
                xs[i * sub + k] = x(sp(pa[at(i0)], pa[at(i)], pa[at(i + 1)], pa[at(i3)]))
                ys[i * sub + k] = y(sp(pb[at(i0)], pb[at(i)], pb[at(i + 1)], pb[at(i3)]))
            }
        }
        add(xs, ys, n * sub)
    }

    /** A round or an oval about a place, turned by [turn] degrees. */
    fun oval(a: Float, b: Float, ra: Float, rb: Float = ra, turn: Float = 0f) {
        val n = 28
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ct = cos(Math.toRadians(turn.toDouble())).toFloat()
        val st = sin(Math.toRadians(turn.toDouble())).toFloat()
        for (i in 0 until n) {
            val t = i * 6.2832f / n
            val ex = cos(t) * ra
            val ey = sin(t) * rb
            xs[i] = x(a + ex * ct - ey * st)
            ys[i] = y(b + ex * st + ey * ct)
        }
        add(xs, ys, n)
    }

    /** A limb (or a stick): a smooth line through these places, [w0] thick where it starts and [w1] where it ends, round at both. */
    fun limb(w0: Float, w1: Float, vararg p: Float) {
        val n = p.size / 2
        val sub = 8
        val total = (n - 1) * sub
        val cx = FloatArray(total + 1)
        val cy = FloatArray(total + 1)
        fun pa(i: Int) = p[i.coerceIn(0, n - 1) * 2]
        fun pb(i: Int) = p[i.coerceIn(0, n - 1) * 2 + 1]
        for (t in 0..total) {
            val at = t / sub.toFloat()
            val seg = min(n - 2, at.toInt())
            val f = at - seg
            fun sp(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (3 * b - a - 3 * c + d) * f * f * f)
            cx[t] = x(sp(pa(seg - 1), pa(seg), pa(seg + 1), pa(seg + 2)))
            cy[t] = y(sp(pb(seg - 1), pb(seg), pb(seg + 1), pb(seg + 2)))
        }
        val cap = 5
        val count = (total + 1) * 2 + cap * 2
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        var at = 0
        fun unit(t: Int): Pair<Float, Float> {
            val dx = cx[min(total, t + 1)] - cx[max(0, t - 1)]
            val dy = cy[min(total, t + 1)] - cy[max(0, t - 1)]
            val len = max(0.0001f, hypot(dx, dy))
            return dx / len to dy / len
        }
        fun wide(t: Int) = (w0 + (w1 - w0) * t / total) / 2 * k
        for (t in 0..total) {
            val (dx, dy) = unit(t)
            xs[at] = cx[t] - dy * wide(t)
            ys[at] = cy[t] + dx * wide(t)
            at++
        }
        run {
            val (dx, dy) = unit(total)
            for (c in 1..cap) {
                val th = c * 3.1416f / (cap + 1)
                xs[at] = cx[total] + (-dy * cos(th) + dx * sin(th)) * wide(total)
                ys[at] = cy[total] + (dx * cos(th) + dy * sin(th)) * wide(total)
                at++
            }
        }
        for (t in total downTo 0) {
            val (dx, dy) = unit(t)
            xs[at] = cx[t] + dy * wide(t)
            ys[at] = cy[t] - dx * wide(t)
            at++
        }
        run {
            val (dx, dy) = unit(0)
            for (c in 1..cap) {
                val th = c * 3.1416f / (cap + 1)
                xs[at] = cx[0] + (dy * cos(th) - dx * sin(th)) * wide(0)
                ys[at] = cy[0] + (-dx * cos(th) - dy * sin(th)) * wide(0)
                at++
            }
        }
        add(xs, ys, count)
    }
}

/**
 * The people and animals that stand in the scenes and designs, each drawn as a whole figure in outline: someone with a
 * head, shoulders, arms and legs, dressed for their part, not a peg. Every one is added to a path to be filled, stands
 * with its feet at ([px], [py]) and is [tall] high.
 */
internal object Figures {
    /** A traveller in a hooded cloak that blows out behind, with a staff. */
    fun cloaked(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f, staff: Boolean = true) {
        val s = Sil(path, px, py, tall, facing)
        s.oval(-0.02f, 0.014f, 0.046f, 0.015f)
        s.oval(0.088f, 0.014f, 0.046f, 0.015f)
        s.round(
            -0.055f, 0.87f, -0.12f, 0.78f, -0.15f, 0.58f, -0.2f, 0.32f, -0.3f, 0.06f, -0.3f, 0.06f, -0.18f, 0.03f, -0.08f, 0.055f, 0.02f, 0.02f,
            0.115f, 0.05f, 0.125f, 0.3f, 0.1f, 0.58f, 0.09f, 0.78f, 0.05f, 0.87f,
        )
        s.round(-0.064f, 0.85f, -0.09f, 0.93f, -0.075f, 1.005f, -0.075f, 1.005f, 0f, 0.995f, 0.05f, 0.962f, 0.07f, 0.9f, 0.05f, 0.85f)
        if (staff) {
            s.limb(0.056f, 0.036f, 0.06f, 0.74f, 0.14f, 0.65f, 0.205f, 0.63f)
            s.limb(0.017f, 0.014f, 0.2f, 0f, 0.212f, 1.07f)
            s.oval(0.212f, 1.075f, 0.017f)
        } else {
            s.limb(0.056f, 0.036f, 0.06f, 0.74f, 0.1f, 0.6f, 0.09f, 0.48f)
        }
    }

    /** Someone in a long coat, hands in its pockets, seen square on: in a brimmed hat, or with a hood up. */
    fun inCoat(path: Path, px: Float, py: Float, tall: Float, hat: Boolean = true) {
        val s = Sil(path, px, py, tall)
        s.oval(-0.088f, 0.013f, 0.056f, 0.016f)
        s.oval(0.088f, 0.013f, 0.056f, 0.016f)
        s.limb(0.076f, 0.058f, -0.058f, 0.34f, -0.068f, 0.04f)
        s.limb(0.076f, 0.058f, 0.058f, 0.34f, 0.068f, 0.04f)
        // The coat: square shoulders, drawn in at the belt, to the knee.
        s.round(
            -0.05f, 0.865f, -0.13f, 0.84f, -0.155f, 0.78f, -0.125f, 0.6f, -0.152f, 0.3f, -0.152f, 0.3f, 0.152f, 0.3f, 0.152f, 0.3f,
            0.125f, 0.6f, 0.155f, 0.78f, 0.13f, 0.84f, 0.05f, 0.865f,
        )
        s.limb(0.064f, 0.05f, -0.14f, 0.8f, -0.19f, 0.66f, -0.14f, 0.53f)
        s.limb(0.064f, 0.05f, 0.14f, 0.8f, 0.19f, 0.66f, 0.14f, 0.53f)
        if (hat) {
            // The collar turned up, and the hat pulled down.
            s.poly(-0.078f, 0.85f, -0.05f, 0.9f, 0f, 0.845f, 0.05f, 0.9f, 0.078f, 0.85f, 0.04f, 0.8f, -0.04f, 0.8f)
            s.oval(0f, 0.9f, 0.048f, 0.055f)
            s.oval(0.004f, 0.93f, 0.11f, 0.015f, turn = -3f)
            s.round(-0.06f, 0.93f, -0.063f, 0.975f, -0.035f, 0.999f, 0f, 0.985f, 0.035f, 0.999f, 0.063f, 0.975f, 0.06f, 0.93f)
        } else {
            s.round(-0.07f, 0.84f, -0.085f, 0.92f, -0.05f, 0.985f, 0f, 1f, 0.05f, 0.985f, 0.085f, 0.92f, 0.07f, 0.84f, 0f, 0.82f)
        }
    }

    /** A man standing, his arms at his sides and his feet a little apart. */
    fun man(path: Path, px: Float, py: Float, tall: Float) {
        val s = Sil(path, px, py, tall)
        s.oval(-0.078f, 0.014f, 0.05f, 0.015f, turn = 8f)
        s.oval(0.078f, 0.014f, 0.05f, 0.015f, turn = -8f)
        s.limb(0.088f, 0.055f, -0.052f, 0.5f, -0.06f, 0.27f, -0.066f, 0.04f)
        s.limb(0.088f, 0.055f, 0.052f, 0.5f, 0.06f, 0.27f, 0.066f, 0.04f)
        s.round(
            -0.04f, 0.865f, -0.115f, 0.84f, -0.132f, 0.78f, -0.105f, 0.62f, -0.108f, 0.46f, -0.108f, 0.46f, 0.108f, 0.46f, 0.108f, 0.46f,
            0.105f, 0.62f, 0.132f, 0.78f, 0.115f, 0.84f, 0.04f, 0.865f,
        )
        s.limb(0.055f, 0.038f, -0.122f, 0.81f, -0.158f, 0.65f, -0.166f, 0.5f)
        s.limb(0.055f, 0.038f, 0.122f, 0.81f, 0.158f, 0.65f, 0.166f, 0.5f)
        s.oval(-0.167f, 0.47f, 0.022f, 0.028f)
        s.oval(0.167f, 0.47f, 0.022f, 0.028f)
        s.poly(-0.026f, 0.89f, 0.026f, 0.89f, 0.032f, 0.85f, -0.032f, 0.85f)
        s.oval(0f, 0.938f, 0.05f, 0.062f)
    }

    /** A caped hero, feet planted and fists on hips, the cape blowing out to one side ([capeTo] -1 left, 1 right). */
    fun hero(path: Path, px: Float, py: Float, tall: Float, capeTo: Float = -1f) {
        val s = Sil(path, px, py, tall, -capeTo)
        s.round(
            0.1f, 0.845f, 0.125f, 0.5f, 0.06f, 0.22f, -0.1f, 0.15f, -0.3f, 0.2f, -0.44f, 0.11f, -0.37f, 0.3f, -0.43f, 0.43f, -0.29f, 0.5f,
            -0.2f, 0.7f, -0.1f, 0.845f,
        )
        s.oval(-0.145f, 0.014f, 0.056f, 0.016f)
        s.oval(0.145f, 0.014f, 0.056f, 0.016f)
        s.limb(0.098f, 0.062f, -0.06f, 0.5f, -0.1f, 0.27f, -0.13f, 0.04f)
        s.limb(0.098f, 0.062f, 0.06f, 0.5f, 0.1f, 0.27f, 0.13f, 0.04f)
        s.round(
            -0.045f, 0.87f, -0.15f, 0.845f, -0.165f, 0.78f, -0.1f, 0.6f, -0.088f, 0.47f, -0.088f, 0.47f, 0.088f, 0.47f, 0.088f, 0.47f,
            0.1f, 0.6f, 0.165f, 0.78f, 0.15f, 0.845f, 0.045f, 0.87f,
        )
        s.limb(0.064f, 0.046f, -0.15f, 0.81f, -0.255f, 0.665f, -0.125f, 0.565f)
        s.limb(0.064f, 0.046f, 0.15f, 0.81f, 0.255f, 0.665f, 0.125f, 0.565f)
        s.poly(-0.028f, 0.89f, 0.028f, 0.89f, 0.036f, 0.85f, -0.036f, 0.85f)
        s.oval(0f, 0.938f, 0.05f, 0.06f)
    }

    /** A singer at the microphone, one fist in the air. */
    fun singer(path: Path, px: Float, py: Float, tall: Float) {
        val s = Sil(path, px, py, tall)
        s.oval(-0.14f, 0.014f, 0.05f, 0.015f)
        s.oval(0.105f, 0.014f, 0.05f, 0.015f)
        s.limb(0.086f, 0.056f, -0.05f, 0.5f, -0.09f, 0.27f, -0.125f, 0.04f)
        s.limb(0.086f, 0.056f, 0.05f, 0.5f, 0.07f, 0.27f, 0.09f, 0.04f)
        s.round(
            -0.04f, 0.865f, -0.11f, 0.84f, -0.122f, 0.78f, -0.088f, 0.62f, -0.092f, 0.46f, -0.092f, 0.46f, 0.092f, 0.46f, 0.092f, 0.46f,
            0.088f, 0.62f, 0.122f, 0.78f, 0.11f, 0.84f, 0.04f, 0.865f,
        )
        s.limb(0.054f, 0.036f, -0.11f, 0.81f, -0.2f, 0.92f, -0.24f, 1.06f)
        s.oval(-0.244f, 1.085f, 0.025f)
        s.limb(0.054f, 0.036f, 0.11f, 0.81f, 0.195f, 0.68f, 0.152f, 0.752f)
        s.poly(-0.026f, 0.89f, 0.026f, 0.89f, 0.032f, 0.85f, -0.032f, 0.85f)
        s.oval(-0.008f, 0.936f, 0.05f, 0.06f, turn = 8f)
        s.round(-0.072f, 0.9f, -0.088f, 0.97f, -0.03f, 1.022f, 0.04f, 1.012f, 0.078f, 0.95f, 0.06f, 0.885f, 0f, 0.93f)
    }

    /** Someone reading on a high stool, turned to the table beside them. */
    fun seated(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
        val s = Sil(path, px, py, tall, facing)
        // The stool.
        s.poly(-0.11f, 0.4f, 0.1f, 0.4f, 0.1f, 0.372f, -0.11f, 0.372f)
        s.limb(0.016f, 0.016f, -0.085f, 0.38f, -0.125f, 0f)
        s.limb(0.016f, 0.016f, 0.075f, 0.38f, 0.115f, 0f)
        s.limb(0.012f, 0.012f, -0.105f, 0.16f, 0.098f, 0.16f)
        // Legs: thighs along the seat, shins down to the rail.
        s.limb(0.088f, 0.07f, -0.02f, 0.44f, 0.2f, 0.43f)
        s.limb(0.07f, 0.05f, 0.2f, 0.43f, 0.175f, 0.2f)
        s.oval(0.2f, 0.17f, 0.05f, 0.016f, turn = -12f)
        // The body, leaning to the book.
        s.round(-0.085f, 0.4f, -0.1f, 0.6f, -0.07f, 0.78f, 0f, 0.81f, 0.07f, 0.77f, 0.085f, 0.6f, 0.075f, 0.4f)
        s.limb(0.052f, 0.036f, 0.03f, 0.75f, 0.1f, 0.62f, 0.215f, 0.655f)
        s.poly(0.19f, 0.665f, 0.3f, 0.715f, 0.312f, 0.69f, 0.2f, 0.635f)
        s.poly(-0.012f, 0.84f, 0.04f, 0.84f, 0.045f, 0.79f, -0.02f, 0.79f)
        s.oval(0.02f, 0.878f, 0.05f, 0.058f, turn = -10f)
        s.oval(-0.04f, 0.925f, 0.032f)
    }

    /** A wizard in robes and a pointed hat, a wand raised. Gives where the wand's tip is. */
    fun wizard(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f): Pair<Float, Float> {
        val s = Sil(path, px, py, tall, facing)
        s.round(
            -0.06f, 0.72f, -0.12f, 0.62f, -0.15f, 0.35f, -0.25f, 0.02f, -0.25f, 0.02f, -0.1f, 0f, 0.1f, 0f, 0.21f, 0.02f, 0.21f, 0.02f,
            0.125f, 0.35f, 0.1f, 0.6f, 0.06f, 0.72f,
        )
        // The raised arm in its hanging sleeve, the hand and the wand.
        s.round(0.035f, 0.715f, 0.17f, 0.79f, 0.3f, 0.852f, 0.312f, 0.8f, 0.23f, 0.7f, 0.2f, 0.585f, 0.14f, 0.66f, 0.08f, 0.6f)
        s.oval(0.318f, 0.83f, 0.022f)
        s.limb(0.013f, 0.005f, 0.312f, 0.824f, 0.47f, 0.972f)
        // The other hand on a staff.
        s.limb(0.05f, 0.036f, -0.05f, 0.68f, -0.13f, 0.56f, -0.185f, 0.56f)
        s.limb(0.016f, 0.014f, -0.19f, 0f, -0.185f, 0.9f)
        // Head, beard and hat.
        s.oval(0.008f, 0.762f, 0.05f, 0.052f)
        s.round(-0.025f, 0.745f, 0.062f, 0.75f, 0.058f, 0.68f, 0.04f, 0.6f, 0.01f, 0.66f)
        s.oval(0.004f, 0.802f, 0.112f, 0.018f, turn = -4f)
        s.round(-0.066f, 0.8f, -0.045f, 0.9f, -0.04f, 0.975f, -0.085f, 1f, -0.075f, 1.004f, -0.01f, 0.955f, 0.03f, 0.88f, 0.066f, 0.8f)
        return s.x(0.47f) to s.y(0.972f)
    }

    /** Two close together, her head on his shoulder: she in a gown to the ground ([gowned]) or a dress to the knee. */
    fun couple(path: Path, px: Float, py: Float, tall: Float, gowned: Boolean = true) {
        val s = Sil(path, px, py, tall)
        s.oval(-0.152f, 0.014f, 0.05f, 0.015f)
        s.oval(-0.044f, 0.014f, 0.05f, 0.015f)
        s.limb(0.08f, 0.055f, -0.135f, 0.5f, -0.14f, 0.27f, -0.146f, 0.04f)
        s.limb(0.08f, 0.055f, -0.06f, 0.5f, -0.055f, 0.27f, -0.05f, 0.04f)
        s.round(
            -0.13f, 0.865f, -0.19f, 0.835f, -0.2f, 0.76f, -0.175f, 0.6f, -0.18f, 0.46f, -0.18f, 0.46f, -0.02f, 0.46f, -0.02f, 0.46f,
            -0.025f, 0.6f, -0.002f, 0.76f, -0.02f, 0.835f, -0.07f, 0.865f,
        )
        s.limb(0.055f, 0.04f, -0.185f, 0.8f, -0.218f, 0.65f, -0.2f, 0.5f)
        s.poly(-0.122f, 0.89f, -0.075f, 0.89f, -0.07f, 0.85f, -0.127f, 0.85f)
        s.oval(-0.092f, 0.936f, 0.048f, 0.058f, turn = -8f)
        if (gowned) {
            s.round(0.03f, 0.8f, 0f, 0.62f, -0.02f, 0.3f, -0.072f, 0.02f, -0.072f, 0.02f, 0.245f, 0.02f, 0.245f, 0.02f, 0.19f, 0.36f, 0.14f, 0.62f, 0.13f, 0.8f)
        } else {
            s.oval(0.04f, 0.013f, 0.042f, 0.014f)
            s.oval(0.118f, 0.013f, 0.042f, 0.014f)
            s.limb(0.056f, 0.04f, 0.05f, 0.36f, 0.05f, 0.04f)
            s.limb(0.056f, 0.04f, 0.122f, 0.36f, 0.126f, 0.04f)
            s.round(0.03f, 0.8f, 0f, 0.62f, -0.012f, 0.35f, -0.012f, 0.35f, 0.172f, 0.35f, 0.172f, 0.35f, 0.14f, 0.62f, 0.13f, 0.8f)
        }
        // His arm round her, and hers on his chest.
        s.limb(0.055f, 0.04f, -0.02f, 0.8f, 0.06f, 0.7f, 0.135f, 0.64f)
        s.limb(0.045f, 0.034f, 0.05f, 0.76f, -0.02f, 0.72f, -0.08f, 0.76f)
        s.oval(0.056f, 0.862f, 0.044f, 0.052f, turn = 10f)
        s.round(0.05f, 0.917f, 0.105f, 0.902f, 0.13f, 0.84f, 0.142f, 0.7f, 0.1f, 0.74f, 0.09f, 0.82f)
    }

    /** A lady and a gentleman of two hundred years ago, arm in arm: a bonnet, a shawl and a high-waisted gown; a tall hat and a tailcoat. */
    fun periodCouple(path: Path, px: Float, py: Float, tall: Float) {
        val s = Sil(path, px, py, tall)
        val m = 0.13f
        s.oval(m - 0.052f, 0.014f, 0.045f, 0.015f)
        s.oval(m + 0.058f, 0.014f, 0.045f, 0.015f)
        s.limb(0.07f, 0.05f, m - 0.04f, 0.5f, m - 0.045f, 0.04f)
        s.limb(0.07f, 0.05f, m + 0.04f, 0.5f, m + 0.05f, 0.04f)
        s.round(
            m - 0.04f, 0.845f, m - 0.11f, 0.82f, m - 0.125f, 0.76f, m - 0.095f, 0.6f, m - 0.11f, 0.355f, m - 0.11f, 0.355f, m - 0.03f, 0.5f,
            m + 0.03f, 0.5f, m + 0.11f, 0.355f, m + 0.11f, 0.355f, m + 0.095f, 0.6f, m + 0.125f, 0.76f, m + 0.11f, 0.82f, m + 0.04f, 0.845f,
        )
        s.limb(0.05f, 0.036f, m + 0.115f, 0.79f, m + 0.152f, 0.64f, m + 0.142f, 0.5f)
        s.limb(0.05f, 0.036f, m - 0.115f, 0.79f, m - 0.16f, 0.68f, m - 0.11f, 0.62f)
        s.poly(m - 0.024f, 0.87f, m + 0.024f, 0.87f, m + 0.03f, 0.83f, m - 0.03f, 0.83f)
        s.oval(m, 0.9f, 0.045f, 0.052f)
        s.poly(m - 0.046f, 0.935f, m + 0.046f, 0.935f, m + 0.052f, 1.02f, m - 0.052f, 1.02f)
        s.oval(m, 0.936f, 0.08f, 0.012f)
        val w = -0.12f
        s.round(w - 0.035f, 0.78f, w - 0.07f, 0.7f, w - 0.1f, 0.4f, w - 0.142f, 0.02f, w - 0.142f, 0.02f, w + 0.142f, 0.02f, w + 0.142f, 0.02f, w + 0.1f, 0.4f, w + 0.07f, 0.7f, w + 0.035f, 0.78f)
        s.round(w - 0.03f, 0.8f, w - 0.095f, 0.76f, w - 0.11f, 0.64f, w - 0.06f, 0.6f, w + 0.06f, 0.6f, w + 0.11f, 0.64f, w + 0.095f, 0.76f, w + 0.03f, 0.8f)
        s.limb(0.042f, 0.032f, w + 0.09f, 0.74f, w + 0.14f, 0.65f, w + 0.2f, 0.63f)
        s.oval(w, 0.846f, 0.042f, 0.05f)
        s.round(w - 0.06f, 0.83f, w - 0.072f, 0.9f, w - 0.02f, 0.927f, w + 0.04f, 0.916f, w + 0.066f, 0.87f, w + 0.05f, 0.82f, w, 0.875f)
    }

    /** A soldier marching: a steel helmet, a pack, and a rifle at the slope. */
    fun soldier(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
        val s = Sil(path, px, py, tall, facing)
        // The rifle over the shoulder, behind all.
        s.limb(0.03f, 0.011f, 0.115f, 0.6f, -0.02f, 0.86f, -0.145f, 1.09f)
        // Legs in stride, and boots.
        s.limb(0.088f, 0.056f, -0.02f, 0.52f, -0.08f, 0.28f, -0.17f, 0.075f)
        s.round(-0.215f, 0.095f, -0.15f, 0.118f, -0.105f, 0.012f, -0.165f, 0f)
        s.limb(0.088f, 0.056f, 0.02f, 0.52f, 0.1f, 0.29f, 0.118f, 0.05f)
        s.round(0.085f, 0.062f, 0.15f, 0.05f, 0.205f, 0.03f, 0.208f, 0f, 0.085f, 0f)
        // Pack, body and the arm swung forward.
        s.round(-0.145f, 0.835f, -0.06f, 0.845f, -0.06f, 0.62f, -0.145f, 0.6f)
        s.round(-0.07f, 0.85f, -0.088f, 0.7f, -0.07f, 0.5f, -0.07f, 0.5f, 0.063f, 0.5f, 0.063f, 0.5f, 0.078f, 0.7f, 0.06f, 0.85f, 0f, 0.872f)
        s.limb(0.052f, 0.04f, 0.03f, 0.8f, 0.09f, 0.66f, 0.125f, 0.6f)
        // Head and helmet.
        s.oval(0.006f, 0.905f, 0.045f, 0.05f)
        s.round(-0.062f, 0.93f, -0.05f, 0.976f, 0f, 0.996f, 0.05f, 0.976f, 0.062f, 0.93f)
        s.oval(0.006f, 0.932f, 0.088f, 0.012f, turn = -4f)
    }

    /** A detective of the gaslit sort: a cap with a peak before and behind, a short cape over a long coat, and a pipe. */
    fun detective(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
        val s = Sil(path, px, py, tall, facing)
        s.oval(-0.075f, 0.013f, 0.052f, 0.015f)
        s.oval(0.085f, 0.013f, 0.052f, 0.015f)
        s.limb(0.072f, 0.056f, -0.06f, 0.22f, -0.066f, 0.04f)
        s.limb(0.072f, 0.056f, 0.06f, 0.22f, 0.066f, 0.04f)
        s.round(-0.115f, 0.58f, -0.15f, 0.19f, -0.15f, 0.19f, 0.15f, 0.19f, 0.15f, 0.19f, 0.115f, 0.58f)
        s.round(-0.05f, 0.865f, -0.16f, 0.77f, -0.205f, 0.57f, -0.2f, 0.53f, 0f, 0.5f, 0.2f, 0.53f, 0.205f, 0.57f, 0.16f, 0.77f, 0.05f, 0.865f)
        // The head, its nose and chin, the cap and the pipe.
        s.oval(0f, 0.9f, 0.05f, 0.055f)
        s.poly(0.035f, 0.915f, 0.068f, 0.89f, 0.045f, 0.875f)
        s.round(-0.062f, 0.918f, -0.046f, 0.965f, 0f, 0.982f, 0.046f, 0.965f, 0.062f, 0.918f)
        s.poly(0.04f, 0.932f, 0.118f, 0.916f, 0.118f, 0.905f, 0.04f, 0.914f)
        s.poly(-0.04f, 0.932f, -0.112f, 0.912f, -0.112f, 0.901f, -0.04f, 0.914f)
        s.oval(0f, 0.985f, 0.013f)
        s.limb(0.011f, 0.011f, 0.042f, 0.868f, 0.138f, 0.85f)
        s.poly(0.128f, 0.87f, 0.156f, 0.87f, 0.15f, 0.836f, 0.134f, 0.836f)
    }

    /** A witch astride her broom, her cloak streaming behind; [size] is the broom's length, about. */
    fun onBroom(path: Path, cx: Float, cy: Float, size: Float, facing: Float = 1f) {
        val s = Sil(path, cx, cy, size, facing)
        s.limb(0.022f, 0.016f, -0.46f, 0.01f, 0.55f, -0.04f)
        s.round(-0.44f, 0.035f, -0.72f, 0.1f, -0.76f, 0.02f, -0.7f, -0.085f, -0.44f, -0.015f)
        s.round(-0.07f, 0.34f, -0.3f, 0.44f, -0.52f, 0.4f, -0.36f, 0.3f, -0.42f, 0.2f, -0.2f, 0.2f, -0.06f, 0.1f)
        s.limb(0.06f, 0.04f, 0.07f, 0.06f, 0.16f, -0.05f, 0.2f, -0.16f)
        s.round(0.17f, -0.14f, 0.27f, -0.2f, 0.27f, -0.23f, 0.16f, -0.21f)
        s.round(-0.06f, 0.01f, -0.11f, 0.3f, -0.03f, 0.43f, 0.06f, 0.32f, 0.13f, 0.01f)
        s.limb(0.05f, 0.034f, 0.03f, 0.33f, 0.14f, 0.2f, 0.24f, 0.03f)
        s.oval(0.03f, 0.5f, 0.07f)
        s.round(0.07f, 0.49f, 0.115f, 0.47f, 0.085f, 0.45f)
        s.oval(0.03f, 0.565f, 0.165f, 0.024f, turn = -6f)
        s.round(-0.055f, 0.57f, -0.03f, 0.74f, -0.13f, 0.9f, -0.12f, 0.905f, 0.055f, 0.76f, 0.115f, 0.565f)
    }

    /** A cat sitting, its tail curled round. */
    fun cat(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
        val s = Sil(path, px, py, tall, facing)
        s.limb(0.07f, 0.03f, -0.18f, 0.05f, -0.42f, 0.03f, -0.52f, 0.2f, -0.42f, 0.36f)
        s.round(-0.24f, 0f, -0.31f, 0.2f, -0.24f, 0.48f, -0.08f, 0.68f, 0.06f, 0.62f, 0.12f, 0.32f, 0.15f, 0f)
        s.limb(0.085f, 0.07f, 0.075f, 0.42f, 0.1f, 0.03f)
        s.oval(0.125f, 0.018f, 0.062f, 0.02f)
        s.oval(0.02f, 0.795f, 0.152f, 0.13f)
        s.poly(-0.115f, 0.85f, -0.1f, 1f, 0f, 0.905f)
        s.poly(0.04f, 0.905f, 0.14f, 1f, 0.155f, 0.85f)
    }

    /** A wolf standing to howl, its muzzle to the sky; [tall] to the tip of its nose. */
    fun wolf(path: Path, px: Float, py: Float, tall: Float, facing: Float = 1f) {
        val s = Sil(path, px, py, tall, facing)
        // The tail, thick and hanging; the far legs; then the body over them, deep in the chest.
        s.limb(0.15f, 0.04f, -0.46f, 0.52f, -0.62f, 0.36f, -0.66f, 0.14f)
        s.limb(0.085f, 0.055f, 0.02f, 0.4f, -0.01f, 0.2f, 0.02f, 0.03f)
        s.limb(0.13f, 0.07f, -0.31f, 0.44f, -0.37f, 0.26f)
        s.limb(0.065f, 0.05f, -0.37f, 0.26f, -0.29f, 0.16f, -0.31f, 0.03f)
        s.round(-0.5f, 0.44f, -0.52f, 0.6f, -0.3f, 0.66f, 0f, 0.66f, 0.18f, 0.58f, 0.17f, 0.36f, -0.08f, 0.29f, -0.38f, 0.33f)
        // The near legs, and the shag under its belly.
        s.limb(0.095f, 0.06f, 0.11f, 0.4f, 0.125f, 0.2f, 0.11f, 0.03f)
        s.oval(0.13f, 0.017f, 0.05f, 0.017f)
        s.oval(0.04f, 0.017f, 0.05f, 0.017f)
        s.limb(0.16f, 0.08f, -0.4f, 0.44f, -0.46f, 0.26f)
        s.limb(0.07f, 0.052f, -0.46f, 0.26f, -0.38f, 0.16f, -0.4f, 0.03f)
        s.oval(-0.38f, 0.017f, 0.05f, 0.017f)
        s.oval(-0.29f, 0.017f, 0.05f, 0.017f)
        s.poly(-0.3f, 0.33f, -0.24f, 0.25f, -0.18f, 0.31f, -0.11f, 0.24f, -0.04f, 0.3f)
        // The neck stretched up, shaggy behind and before, and the head thrown back: muzzle, open jaw and ears.
        s.limb(0.25f, 0.14f, 0.08f, 0.54f, 0.15f, 0.7f, 0.19f, 0.83f)
        s.poly(0.07f, 0.76f, -0.04f, 0.78f, 0.05f, 0.67f)
        s.poly(0.03f, 0.68f, -0.09f, 0.67f, 0.01f, 0.57f)
        s.poly(0.24f, 0.66f, 0.3f, 0.58f, 0.2f, 0.56f)
        s.limb(0.13f, 0.045f, 0.19f, 0.82f, 0.25f, 0.92f, 0.3f, 1f)
        s.limb(0.045f, 0.02f, 0.25f, 0.82f, 0.345f, 0.925f)
        s.poly(0.15f, 0.86f, 0.07f, 0.97f, 0.19f, 0.915f)
        s.poly(0.12f, 0.84f, 0.03f, 0.9f, 0.14f, 0.89f)
    }

    /**
     * An owl on its perch, facing us: its outline goes in [path], and the lines of its face and feathers in [details] if given.
     * Gives where its eyes are (the first, the second), and how big.
     */
    fun owl(path: Path, px: Float, py: Float, tall: Float, details: Path? = null): FloatArray {
        val s = Sil(path, px, py, tall)
        // The tail behind the perch, the body, the ear tufts and the feet.
        s.round(-0.075f, 0.05f, -0.082f, -0.14f, -0.082f, -0.14f, 0.082f, -0.14f, 0.082f, -0.14f, 0.075f, 0.05f)
        s.round(
            -0.1f, 0.03f, -0.25f, 0.12f, -0.318f, 0.36f, -0.3f, 0.58f, -0.316f, 0.74f, -0.27f, 0.865f, -0.15f, 0.918f, 0f, 0.895f,
            0.15f, 0.918f, 0.27f, 0.865f, 0.316f, 0.74f, 0.3f, 0.58f, 0.318f, 0.36f, 0.25f, 0.12f, 0.1f, 0.03f,
        )
        s.poly(-0.292f, 0.83f, -0.33f, 1.01f, -0.16f, 0.915f)
        s.poly(0.292f, 0.83f, 0.33f, 1.01f, 0.16f, 0.915f)
        s.oval(-0.08f, 0.018f, 0.05f, 0.042f)
        s.oval(0.08f, 0.018f, 0.05f, 0.042f)
        fun ax(f: Float) = px + f * tall
        fun ay(f: Float) = py - f * tall
        details?.apply {
            val rim = tall * 0.128f
            for (side in listOf(-1f, 1f)) {
                val ex = ax(side * 0.11f)
                val ey = ay(0.72f)
                // The rim of its face round each eye, and the fine feathers that run out to it.
                if (side < 0f) addArc(ex - rim, ey - rim, ex + rim, ey + rim, 300f, -250f) else addArc(ex - rim, ey - rim, ex + rim, ey + rim, 240f, 250f)
                for (k in 0 until 12) {
                    val ang = Math.toRadians((k * 30f + 15f).toDouble())
                    // (None across the beak's side of the eye.)
                    if (cos(ang) * -side > 0.75) continue
                    moveTo(ex + (cos(ang) * rim * 0.72f).toFloat(), ey + (sin(ang) * rim * 0.72f).toFloat())
                    lineTo(ex + (cos(ang) * rim * 0.92f).toFloat(), ey + (sin(ang) * rim * 0.92f).toFloat())
                }
                // Its brow, running up into the tuft.
                moveTo(ax(0f), ay(0.71f))
                quadTo(ax(side * 0.09f), ay(0.885f), ax(side * 0.27f), ay(0.9f))
                // The edge of its folded wing, the long feathers within it, and the small ones at its shoulder.
                moveTo(ax(side * 0.275f), ay(0.57f))
                cubicTo(ax(side * 0.19f), ay(0.42f), ax(side * 0.22f), ay(0.2f), ax(side * 0.12f), ay(0.07f))
                for (k in 1..3) {
                    moveTo(ax(side * (0.275f + k * 0.012f)), ay(0.5f - k * 0.045f))
                    cubicTo(ax(side * (0.2f + k * 0.022f)), ay(0.38f - k * 0.04f), ax(side * (0.225f + k * 0.014f)), ay(0.22f - k * 0.02f), ax(side * (0.14f + k * 0.03f)), ay(0.1f))
                }
                for (row in 0..1) for (k in 0..1) {
                    val fx = side * (0.235f + k * 0.045f - row * 0.01f)
                    val fy = 0.6f - row * 0.05f + k * 0.012f
                    moveTo(ax(fx - 0.02f), ay(fy))
                    quadTo(ax(fx), ay(fy - 0.035f), ax(fx + 0.02f), ay(fy))
                }
                // Its toes over the perch.
                for (k in -1..1) {
                    moveTo(ax(side * 0.08f + k * 0.026f), ay(0.05f))
                    lineTo(ax(side * 0.08f + k * 0.03f), ay(-0.012f))
                }
            }
            // Its beak.
            moveTo(ax(-0.03f), ay(0.69f)); lineTo(ax(0f), ay(0.59f)); lineTo(ax(0.03f), ay(0.69f))
            moveTo(ax(0f), ay(0.69f)); lineTo(ax(0f), ay(0.62f))
            // The feathers of its breast, row under row.
            for (row in 0 until 6) {
                val fy = 0.535f - row * 0.078f
                val reach = 0.165f - row * 0.012f
                val n = if (row % 2 == 0) 5 else 4
                for (k in 0 until n) {
                    val fx = -reach + 2 * reach * (k / (n - 1f))
                    moveTo(ax(fx - 0.03f), ay(fy))
                    quadTo(ax(fx), ay(fy - 0.046f), ax(fx + 0.03f), ay(fy))
                }
            }
            // The feathers of its tail.
            for (k in -1..1) { moveTo(ax(k * 0.04f), ay(-0.02f)); lineTo(ax(k * 0.045f), ay(-0.125f)) }
        }
        return floatArrayOf(ax(-0.11f), ay(0.72f), ax(0.11f), ay(0.72f), tall * 0.062f)
    }

    /** A crane in flight, its neck out before it, legs trailing and wings raised; [size] from beak to feet. */
    fun crane(path: Path, cx: Float, cy: Float, size: Float, facing: Float = 1f) {
        val s = Sil(path, cx, cy, size, facing)
        s.limb(0.022f, 0.008f, -0.1f, -0.012f, -0.3f, -0.05f, -0.5f, -0.075f)
        s.poly(-0.12f, 0.035f, -0.26f, -0.015f, -0.12f, -0.04f)
        s.oval(0f, 0f, 0.16f, 0.052f)
        s.limb(0.046f, 0.02f, 0.11f, 0.005f, 0.26f, 0.05f, 0.38f, 0.062f, 0.46f, 0.076f)
        s.oval(0.465f, 0.078f, 0.022f)
        s.poly(0.478f, 0.092f, 0.6f, 0.07f, 0.478f, 0.062f)
        // The near wing swept back, and the far one reaching forward, each with its long feathers spread.
        s.round(0.07f, 0.02f, 0.06f, 0.2f, -0.05f, 0.4f, -0.17f, 0.56f, -0.24f, 0.4f, -0.2f, 0.18f, -0.13f, 0.02f)
        for (k in 0 until 5) s.limb(0.05f, 0.012f, -0.1f - k * 0.02f, 0.4f - k * 0.03f, -0.2f - k * 0.033f, 0.66f - k * 0.075f)
        s.round(0.1f, 0.02f, 0.18f, 0.2f, 0.22f, 0.38f, 0.2f, 0.54f, 0.12f, 0.4f, 0.05f, 0.2f, -0.02f, 0.02f)
        for (k in 0 until 4) s.limb(0.045f, 0.012f, 0.19f + k * 0.012f, 0.4f - k * 0.02f, 0.22f + k * 0.03f, 0.63f - k * 0.07f)
    }
}
