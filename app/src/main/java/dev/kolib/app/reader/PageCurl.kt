package dev.kolib.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Draws a leaf of paper turning over. The leaf bends round a cylinder whose axis sweeps across it;
 * past the bend it lies face down on the far side of the spine, as a page does in a book.
 *
 * Everything is worked out in the leaf's own coordinates: u runs from the spine (0) to the free edge
 * ([width]), y down the page. [dir] says which way u runs on screen: 1 for a leaf to the right of its
 * spine (turned leftwards, like a book's right-hand page), -1 for one to the left of it.
 */
class PageCurl(
    val spineX: Float,
    val dir: Int,
    val width: Float,
    val height: Float,
    /** Screen pixels per dp, for shadow sizes. */
    private val density: Float = 2f,
) {
    /** How the leaf lies: where the held point of its free edge has got to, and the bend's radius. */
    data class Pose(val fu: Float, val fy: Float, val radius: Float) {
        companion object {
            fun flat(width: Float, anchorY: Float) = Pose(width, anchorY, 0f)
        }
    }

    /** The point of the free edge being held (where a finger took it, or near the bottom corner). */
    var anchorY: Float = height * 0.8f

    /** The bend's largest radius, part way through a turn. */
    val maxRadius: Float = min(width, height) * 0.085f

    /**
     * The pose with the fold through ([bend], [bendY]): 0 is a leaf turned right over, [width] one lying
     * flat. The held point lands mirrored across the fold. The fold may lean, but never so far that it
     * would cross the spine within the page: the leaf is fixed there, and a fold across it would tear
     * it off. So it stands upright again as the leaf lands, and lands squarely on the page opposite.
     */
    fun pose(bend: Float, bendY: Float = anchorY): Pose {
        val b = bend.coerceIn(0f, width)
        val fu = 2 * b - width
        val du = width - fu
        val wanted = 2 * bendY - anchorY
        // Landing higher swings the fold's foot towards the spine (and lower, its head).
        val toEnd = if (wanted < anchorY) height - anchorY else anchorY
        val bent = radiusAt(du)
        val room = width - du / 2 - PI.toFloat() * bent / 2 - 1f
        val spineLimit = if (du < 0.5f || room <= 0f) 0f else -toEnd + sqrt(toEnd * toEnd + 2 * du * room)
        val lean = min(du * tan(MAX_LEAN), max(0f, spineLimit))
        val fy = wanted.coerceIn(anchorY - lean, anchorY + lean)
        val d = hypot(du, fy - anchorY)
        return Pose(fu, fy, radiusAt(d))
    }

    /** Flat at either end of a turn, most curled in the middle. */
    private fun radiusAt(d: Float): Float = maxRadius * ramp(d / (0.35f * width)) * ramp((2 * width - d) / (0.35f * width))

    /**
     * How far from the spine the fold lies at its nearest point within the page (negative: it crosses
     * the spine), for tests.
     */
    internal fun foldClearance(pose: Pose): Float {
        val du = width - pose.fu
        val e = anchorY - pose.fy
        if (du < 0.5f) return width
        val mu = width - du / 2
        val my = anchorY - e / 2
        val atTop = mu - (0f - my) * e / du
        val atBottom = mu - (height - my) * e / du
        return min(atTop, atBottom) - PI.toFloat() * pose.radius / 2
    }

    // ---- the mesh --------------------------------------------------------------------------------

    private val nu = 56
    private val ny = 36
    private val count = (nu + 1) * (ny + 1)
    private val baseU = FloatArray(count)
    private val baseY = FloatArray(count)
    private val frontVerts = FloatArray(count * 2)
    private val backVerts = FloatArray(count * 2)
    private val shadowVerts = FloatArray(count * 2)
    private val texFront = FloatArray(count * 2)
    private val texBack = FloatArray(count * 2)
    private val frontColors = IntArray(count)
    private val backColors = IntArray(count)
    private val washColors = IntArray(count)
    private val shadowColors = IntArray(count)
    private val indices: ShortArray

    init {
        var k = 0
        for (j in 0..ny) for (i in 0..nu) {
            val u = width * i / nu
            val y = height * j / ny
            baseU[k] = u
            baseY[k] = y
            texFront[2 * k] = spineX + dir * u
            texFront[2 * k + 1] = y
            // The back of the leaf shows what lies there once it's turned: mirrored across the spine.
            texBack[2 * k] = spineX - dir * u
            texBack[2 * k + 1] = y
            k++
        }
        val idx = ShortArray(nu * ny * 6)
        var n = 0
        for (j in 0 until ny) for (i in 0 until nu) {
            val a = j * (nu + 1) + i
            val b = a + 1
            val c = a + nu + 1
            val d = c + 1
            idx[n++] = a.toShort(); idx[n++] = b.toShort(); idx[n++] = c.toShort()
            idx[n++] = b.toShort(); idx[n++] = d.toShort(); idx[n++] = c.toShort()
        }
        indices = idx
    }

    /** The leaf's rectangle on screen. */
    val leafRect: RectF = if (dir > 0) RectF(spineX, 0f, spineX + width, height) else RectF(spineX - width, 0f, spineX, height)

    /** Where a leaf point (u, y) is on screen once posed; for tests and for placing shadows. */
    fun place(pose: Pose, u: Float, y: Float): Pair<Float, Float> {
        val g = geometry(pose) ?: return (spineX + dir * u) to y
        val out = FloatArray(2)
        g.map(u, y, Side.WHOLE, out)
        return out[0] to out[1]
    }

    private enum class Side { FRONT, BACK, WHOLE }

    /** The cylinder for a pose: its axis through (au, ay), running along m; n points to the free edge. */
    private inner class Geometry(val au: Float, val ay: Float, val nu_: Float, val ny_: Float, val r: Float) {
        val mu = -ny_
        val my = nu_

        /** Distance past the axis (d) for a leaf point; negative means still flat. */
        fun distance(u: Float, y: Float) = (u - au) * nu_ + (y - ay) * ny_

        fun map(u: Float, y: Float, side: Side, out: FloatArray, dx: Float = 0f, dy: Float = 0f) {
            val pu = u - au
            val py = y - ay
            val d = pu * nu_ + py * ny_
            val t = pu * mu + py * my
            val off = when {
                r < 0.5f -> when (side) {
                    Side.FRONT -> min(d, 0f)
                    Side.BACK -> if (d <= 0f) 0f else -d
                    Side.WHOLE -> if (d <= 0f) d else -d
                }
                else -> {
                    val theta = d / r
                    when (side) {
                        Side.FRONT -> when {
                            d <= 0f -> d
                            theta < PI / 2 -> r * sin(theta)
                            else -> r
                        }
                        Side.BACK -> when {
                            theta <= PI / 2 -> r
                            theta < PI -> r * sin(theta)
                            else -> -(d - PI.toFloat() * r)
                        }
                        Side.WHOLE -> when {
                            d <= 0f -> d
                            theta < PI -> r * sin(theta)
                            else -> -(d - PI.toFloat() * r)
                        }
                    }
                }
            }
            val lu = au + nu_ * off + mu * t + dx
            val ly = ay + ny_ * off + my * t + dy
            out[0] = spineX + dir * lu
            out[1] = ly
        }

        /** How much light each side catches at a point: the bend darkens it towards its crest. */
        fun frontShade(d: Float): Float = when {
            d <= 0f || r < 0.5f -> 1f
            else -> 1f - 0.42f * (1f - cos(min(d / r, (PI / 2).toFloat())))
        }

        /** [flat] is how much light the back catches where it lies flat on the page opposite. */
        fun backShade(d: Float, flat: Float): Float = when {
            r < 0.5f -> flat
            else -> {
                val theta = d / r
                if (theta >= PI) flat else 0.70f + (flat - 0.70f) * max(0f, -cos(max(theta, (PI / 2).toFloat())))
            }
        }
    }

    private fun geometry(pose: Pose): Geometry? {
        val du = width - pose.fu
        val dy = anchorY - pose.fy
        val d = hypot(du, dy)
        if (d < 0.5f) return null
        val n0 = du / d
        val n1 = dy / d
        val s = (d + PI.toFloat() * pose.radius) / 2
        return Geometry(width - n0 * s, anchorY - n1 * s, n0, n1, pose.radius)
    }

    // ---- drawing --------------------------------------------------------------------------------

    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    /** White, so vertex colours come through unchanged whichever way the canvas mixes them with it. */
    private val plainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frontShader: Pair<Bitmap, BitmapShader>? = null
    private var backShader: Pair<Bitmap, BitmapShader>? = null

    private fun shaderFor(cache: Pair<Bitmap, BitmapShader>?, bitmap: Bitmap): Pair<Bitmap, BitmapShader> =
        if (cache?.first === bitmap) cache else bitmap to BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    /**
     * Draws the leaf. [front] and [back] are pictures of the whole screen: the leaf's face is cut from
     * [front] where the leaf lies; its back from [back] where it will land, or, without one, it's
     * [front] seen through the paper ([paper] is the page colour).
     */
    fun draw(canvas: Canvas, front: Bitmap, back: Bitmap?, paper: Int, pose: Pose) {
        val g = geometry(pose)
        if (g == null) {
            // Lying flat.
            val src = android.graphics.Rect(leafRect.left.toInt(), 0, leafRect.right.toInt(), height.toInt())
            canvas.drawBitmap(front, src, leafRect, texturePaint)
            return
        }
        // How far over the leaf is (0 lying flat, 1 right over), and how much of the turn is left.
        val over = ((width - pose.fu) / (2 * width)).coerceIn(0f, 1f)
        val left = 1f - over
        // The back of the leaf lies a touch in shade while it's still turning, and in full light once it
        // has landed: right over, it's the very page that shows when the turn ends, in the same colours.
        val flatBack = 1f - 0.03f * ramp(left / 0.1f)
        var k = 0
        val out = FloatArray(2)
        var maxD = 0f
        for (j in 0..ny) for (i in 0..nu) {
            val u = baseU[k]
            val y = baseY[k]
            val d = g.distance(u, y)
            maxD = max(maxD, d)
            g.map(u, y, Side.FRONT, out); frontVerts[2 * k] = out[0]; frontVerts[2 * k + 1] = out[1]
            g.map(u, y, Side.BACK, out); backVerts[2 * k] = out[0]; backVerts[2 * k + 1] = out[1]
            val fs = g.frontShade(d)
            val bs = g.backShade(d, flatBack)
            frontColors[k] = gray(fs)
            backColors[k] = gray(bs)
            washColors[k] = shadeColor(paper, bs, 0.86f)
            k++
        }

        // The shadow the curl casts on what's underneath, beyond its crest.
        val crestU = g.au + g.nu_ * g.r
        val crestY = g.ay + g.ny_ * g.r
        val spread = 14f * density + g.r * 0.7f
        val strength = 0.30f * ramp(g.r / (0.35f * maxRadius)) * ramp(left / 0.15f)
        if (strength > 0.01f) {
            val (x0, y0) = toScreen(crestU, crestY)
            val (x1, y1) = toScreen(crestU + g.nu_ * spread, crestY + g.ny_ * spread)
            shadowPaint.shader = LinearGradient(x0, y0, x1, y1, Color.argb((255 * strength).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.save()
            canvas.clipRect(leafRect)
            canvas.drawRect(leafRect, shadowPaint)
            canvas.restore()
            shadowPaint.shader = null
        }

        // The face of the leaf, up to the crest of the bend.
        frontShader = shaderFor(frontShader, front)
        texturePaint.shader = frontShader!!.second
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 2, frontVerts, 0, texFront, 0, frontColors, 0, indices, 0, indices.size, texturePaint)

        // Once any of it is over, its back shows (all of it, lying on the page opposite, when it's right over).
        val hasBack = maxD > (PI / 2).toFloat() * g.r && over > 0.002f
        if (hasBack) {
            // A soft shadow under the turned-over part, cast on the page below (gone once it lies flat there).
            val lift = ramp(left / 0.1f)
            for ((step, alpha) in listOf(3f to 0.10f, 7f to 0.07f, 12f to 0.045f)) {
                val ox = -g.nu_ * step * density
                val oy = -g.ny_ * step * density
                for (q in 0 until count) {
                    shadowVerts[2 * q] = backVerts[2 * q] + dir * ox
                    shadowVerts[2 * q + 1] = backVerts[2 * q + 1] + oy
                    shadowColors[q] = Color.argb((255 * alpha * lift).toInt(), 0, 0, 0)
                }
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 2, shadowVerts, 0, null, 0, shadowColors, 0, indices, 0, indices.size, plainPaint)
            }
            // The back of the leaf.
            if (back != null) {
                backShader = shaderFor(backShader, back)
                texturePaint.shader = backShader!!.second
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 2, backVerts, 0, texBack, 0, backColors, 0, indices, 0, indices.size, texturePaint)
            } else {
                texturePaint.shader = frontShader!!.second
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 2, backVerts, 0, texFront, 0, backColors, 0, indices, 0, indices.size, texturePaint)
                // Paper, with the print showing faintly through.
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count * 2, backVerts, 0, null, 0, washColors, 0, indices, 0, indices.size, plainPaint)
            }
        }
        texturePaint.shader = null
    }

    private fun toScreen(u: Float, y: Float): Pair<Float, Float> = (spineX + dir * u) to y

    companion object {
        /** How far the fold may lean from upright, at most. */
        private val MAX_LEAN = (16.0 * PI / 180).toFloat()

        private fun ramp(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        private fun gray(f: Float): Int {
            val v = (255 * f.coerceIn(0f, 1f)).toInt()
            return Color.argb(255, v, v, v)
        }

        private fun shadeColor(c: Int, f: Float, alpha: Float): Int =
            Color.argb((255 * alpha).toInt(), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    }
}
