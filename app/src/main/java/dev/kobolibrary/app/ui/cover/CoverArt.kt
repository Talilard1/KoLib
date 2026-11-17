package dev.kobolibrary.app.ui.cover

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The pictures the cover designs are painted with: a scene for each genre (a castle on a crag at dusk,
 * a ringed planet, a foggy street at night...), drawn from shapes in layers, and line drawings for the
 * gold-stamped designs. All of it is this app's own, drawn afresh for each book from a seed, so no two
 * books get quite the same picture.
 */

private fun rgb(hex: Long) = (0xFF000000 or hex).toInt()
private fun mix(a: Int, b: Int, f: Float) = ColorUtils.blendARGB(a, b, f.coerceIn(0f, 1f))
private fun alpha(c: Int, a: Int) = ColorUtils.setAlphaComponent(c, a.coerceIn(0, 255))

/** Draws into [r] on [c]: measurements are fractions of its width ([x]) and height ([y]). */
internal class Painter(val c: Canvas, val r: RectF, val rnd: Random) {
    val w = r.width()
    val h = r.height()
    /** A hundredth of the shorter side, for line widths and small things. */
    val u = min(w, h) / 100f

    fun x(f: Float) = r.left + f * w
    fun y(f: Float) = r.top + f * h

    fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.FILL }

    fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** The sky (or any backdrop): [colours] from top to bottom, at [stops] (fractions of the height). */
    fun sky(colours: IntArray, stops: FloatArray? = null) {
        val p = Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, colours, stops, Shader.TileMode.CLAMP) }
        c.drawRect(r, p)
    }

    /** Light spreading round a point, fading out by [radius]. */
    fun glow(cx: Float, cy: Float, radius: Float, colour: Int, strength: Int = 200) {
        if (radius <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radius, intArrayOf(alpha(colour, strength), alpha(colour, strength / 3), alpha(colour, 0)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, radius, p)
    }

    fun disc(cx: Float, cy: Float, radius: Float, colour: Int) = c.drawCircle(cx, cy, radius, fill(colour))

    /** Stars down to [lowest] of the height, a few of them bright. */
    fun stars(n: Int, lowest: Float, colour: Int = Color.WHITE) {
        for (i in 0 until n) {
            val sx = x(rnd.nextFloat())
            val sy = y(rnd.nextFloat() * lowest)
            val big = rnd.nextFloat()
            val radius = u * (0.12f + big * big * big * 0.55f)
            c.drawCircle(sx, sy, radius, fill(alpha(colour, (90 + 165 * rnd.nextFloat()).toInt())))
            if (big > 0.93f) {
                // A bright one twinkles.
                val s = stroke(alpha(colour, 150), u * 0.12f)
                c.drawLine(sx - radius * 4, sy, sx + radius * 4, sy, s)
                c.drawLine(sx, sy - radius * 4, sx, sy + radius * 4, s)
            }
        }
    }

    /**
     * A line of hills or mountains across the whole width: its points' heights from 0 to 1, made by
     * halving and nudging ([rough]: how much each finer step nudges, 0.3 smooth to 0.6 jagged).
     */
    fun heights(n: Int = 129, rough: Float = 0.5f): FloatArray {
        val p = FloatArray(n)
        p[0] = rnd.nextFloat()
        p[n - 1] = rnd.nextFloat()
        var step = n - 1
        var amp = 1f
        while (step > 1) {
            val half = step / 2
            var i = half
            while (i < n) {
                p[i] = (p[i - half] + p[i + half]) / 2 + (rnd.nextFloat() - 0.5f) * amp
                i += step
            }
            amp *= rough
            step = half
        }
        val lo = p.min()
        val hi = p.max()
        val span = (hi - lo).takeIf { it > 1e-4f } ?: 1f
        for (i in p.indices) p[i] = (p[i] - lo) / span
        return p
    }

    /** The shape under a ridge whose highest point is [top] and lowest [base] (fractions of the height), down to the bottom. */
    fun ridge(base: Float, top: Float, rough: Float = 0.5f, bottom: Float = 1.02f, peaks: FloatArray? = null): Path {
        val hs = peaks ?: heights(rough = rough)
        val path = Path()
        path.moveTo(r.left - 2, y(bottom))
        for (i in hs.indices) {
            val f = i / (hs.size - 1f)
            path.lineTo(x(f), y(base + (top - base) * hs[i]))
        }
        path.lineTo(r.right + 2, y(bottom))
        path.close()
        return path
    }

    /** Fills [path] shaded from [top] (at [from] of the height) to [bottom] (at [to]). */
    fun layer(path: Path, top: Int, bottom: Int, from: Float = 0f, to: Float = 1f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, y(from), 0f, y(to), top, bottom, Shader.TileMode.CLAMP) }
        c.drawPath(path, p)
    }

    /** A band of mist [thick] tall centred on [at]. */
    fun mist(at: Float, thick: Float, colour: Int, strength: Int = 150) {
        val p = Paint().apply {
            shader = LinearGradient(0f, y(at - thick / 2), 0f, y(at + thick / 2), intArrayOf(alpha(colour, 0), alpha(colour, strength), alpha(colour, 0)), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(r.left, y(at - thick / 2), r.right, y(at + thick / 2), p)
    }

    /** Soft clouds: overlapping ovals, gathered along [at]. */
    fun clouds(n: Int, at: Float, spread: Float, colour: Int, strength: Int = 60, puffy: Boolean = false) {
        repeat(n) {
            val cx = x(rnd.nextFloat())
            val cy = y(at + (rnd.nextFloat() - 0.5f) * spread)
            val cw = w * (0.18f + rnd.nextFloat() * 0.3f)
            val ch = if (puffy) cw * 0.42f else cw * 0.12f
            val p = fill(alpha(colour, strength))
            if (puffy) {
                // One shape, so where its parts overlap doesn't show darker.
                val cloud = Path()
                for (k in 0 until 5) {
                    val ox = (k - 2) * cw * 0.17f
                    val rad = ch * (0.55f + 0.45f * sin(PI.toFloat() * (k + 0.5f) / 5f))
                    cloud.addCircle(cx + ox, cy - rad * 0.3f, rad, Path.Direction.CW)
                }
                cloud.addRoundRect(cx - cw * 0.45f, cy - ch * 0.3f, cx + cw * 0.45f, cy + ch * 0.35f, ch * 0.3f, ch * 0.3f, Path.Direction.CW)
                c.drawPath(cloud, p)
                // A little shade on its underside.
                c.save()
                c.clipPath(cloud)
                c.drawRect(cx - cw, cy, cx + cw, cy + ch, fill(alpha(Color.BLACK, strength / 12)))
                c.restore()
            } else {
                c.drawOval(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2, p)
            }
        }
    }

    /** Birds far off: little curved V's. */
    fun birds(n: Int, cx: Float, cy: Float, spread: Float, colour: Int) {
        val p = stroke(colour, u * 0.35f)
        repeat(n) {
            val bx = x(cx + (rnd.nextFloat() - 0.5f) * spread)
            val by = y(cy + (rnd.nextFloat() - 0.5f) * spread * 0.6f)
            val s = u * (0.9f + rnd.nextFloat() * 1.4f)
            val path = Path().apply {
                moveTo(bx - s, by - s * 0.2f)
                quadTo(bx - s * 0.45f, by - s * 0.55f, bx, by)
                quadTo(bx + s * 0.45f, by - s * 0.55f, bx + s, by - s * 0.2f)
            }
            c.drawPath(path, p)
        }
    }

    /** A fine speckle over everything, as of paint on canvas. */
    fun grain(amount: Float = 1f) {
        val n = (w * h / 260f * amount).toInt().coerceAtMost(60000)
        val light = fill(alpha(Color.WHITE, 14))
        val dark = fill(alpha(Color.BLACK, 18))
        val s = max(1f, u * 0.18f)
        repeat(n) {
            val gx = x(rnd.nextFloat())
            val gy = y(rnd.nextFloat())
            c.drawRect(gx, gy, gx + s, gy + s, if (rnd.nextBoolean()) light else dark)
        }
    }

    /** Darker towards the edges. */
    fun vignette(strength: Int = 110) {
        val cx = r.centerX()
        val cy = r.centerY()
        val radius = max(w, h) * 0.75f
        val p = Paint().apply {
            shader = RadialGradient(cx, cy, radius, intArrayOf(alpha(Color.BLACK, 0), alpha(Color.BLACK, 0), alpha(Color.BLACK, strength)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(r, p)
    }

    // ---- things in the scenes ----------------------------------------------------------------------

    /** A pine, [tall] high, standing on ([px], [py]). */
    fun pine(path: Path, px: Float, py: Float, tall: Float) {
        val wide = tall * 0.42f
        path.addRect(px - tall * 0.03f, py - tall * 0.12f, px + tall * 0.03f, py, Path.Direction.CW)
        for (k in 0 until 3) {
            val bottom = py - tall * (0.1f + 0.27f * k)
            val half = wide / 2 * (1f - 0.24f * k)
            path.moveTo(px - half, bottom)
            path.lineTo(px, bottom - tall * 0.42f)
            path.lineTo(px + half, bottom)
            path.close()
        }
    }

    /** A castle on a crag: towers with pointed roofs, walls with battlements; [base] is where the crag starts. */
    fun castle(path: Path, cx: Float, base: Float, size: Float, windows: Path?, downTo: Float = base) {
        // The rock below the crag, down to the ground under it.
        if (downTo > base) {
            path.moveTo(cx - size * 1.0f, base - size * 0.01f)
            path.lineTo(cx - size * 1.25f, downTo)
            path.lineTo(cx + size * 1.3f, downTo)
            path.lineTo(cx + size * 1.05f, base - size * 0.01f)
            path.close()
        }
        // The crag, its sides broken into ledges.
        path.moveTo(cx - size * 1.0f, base)
        path.lineTo(cx - size * 0.82f, base - size * 0.12f)
        path.lineTo(cx - size * 0.76f, base - size * 0.1f)
        path.lineTo(cx - size * 0.66f, base - size * 0.3f)
        path.lineTo(cx - size * 0.6f, base - size * 0.29f)
        path.lineTo(cx - size * 0.52f, base - size * 0.47f)
        path.lineTo(cx + size * 0.42f, base - size * 0.5f)
        path.lineTo(cx + size * 0.5f, base - size * 0.38f)
        path.lineTo(cx + size * 0.58f, base - size * 0.39f)
        path.lineTo(cx + size * 0.7f, base - size * 0.2f)
        path.lineTo(cx + size * 0.8f, base - size * 0.18f)
        path.lineTo(cx + size * 1.05f, base)
        path.close()
        val ground = base - size * 0.46f
        fun tower(tx: Float, wide: Float, tall: Float, roof: Float) {
            path.addRect(tx - wide / 2, ground - tall, tx + wide / 2, ground + size * 0.02f, Path.Direction.CW)
            path.moveTo(tx - wide * 0.62f, ground - tall)
            path.lineTo(tx, ground - tall - roof)
            path.lineTo(tx + wide * 0.62f, ground - tall)
            path.close()
            // A pennant.
            path.addRect(tx - size * 0.005f, ground - tall - roof - size * 0.09f, tx + size * 0.005f, ground - tall - roof, Path.Direction.CW)
            path.moveTo(tx, ground - tall - roof - size * 0.09f)
            path.lineTo(tx + size * 0.07f, ground - tall - roof - size * 0.07f)
            path.lineTo(tx, ground - tall - roof - size * 0.05f)
            path.close()
            windows?.addRect(tx - wide * 0.09f, ground - tall * 0.72f, tx + wide * 0.09f, ground - tall * 0.6f, Path.Direction.CW)
        }
        // The walls between, with battlements.
        val wl = cx - size * 0.42f
        val wr = cx + size * 0.38f
        val wallTop = ground - size * 0.2f
        path.addRect(wl, wallTop, wr, ground + size * 0.02f, Path.Direction.CW)
        var bx = wl
        while (bx < wr - size * 0.02f) {
            path.addRect(bx, wallTop - size * 0.035f, min(wr, bx + size * 0.03f), wallTop, Path.Direction.CW)
            bx += size * 0.06f
        }
        tower(cx - size * 0.42f, size * 0.12f, size * 0.36f, size * 0.16f)
        tower(cx - size * 0.08f, size * 0.17f, size * 0.62f, size * 0.24f)
        tower(cx + size * 0.18f, size * 0.11f, size * 0.44f, size * 0.17f)
        tower(cx + size * 0.38f, size * 0.09f, size * 0.3f, size * 0.13f)
    }

    /** A cloaked figure with a staff, [tall] high, standing on ([px], [py]). */
    fun cloaked(path: Path, px: Float, py: Float, tall: Float) {
        val head = tall * 0.09f
        path.addCircle(px, py - tall + head, head, Path.Direction.CW)
        path.moveTo(px - head * 0.9f, py - tall + head * 1.6f)
        path.quadTo(px - head * 1.6f, py - tall * 0.5f, px - head * 2.6f, py)
        path.lineTo(px + head * 2.3f, py)
        path.quadTo(px + head * 1.4f, py - tall * 0.5f, px + head * 0.9f, py - tall + head * 1.6f)
        path.close()
        path.addRect(px + head * 2.4f, py - tall * 1.08f, px + head * 2.4f + tall * 0.018f, py, Path.Direction.CW)
    }

    /** A man in a long coat and a hat, [tall] high, standing on ([px], [py]). */
    fun inHat(path: Path, px: Float, py: Float, tall: Float) {
        val head = tall * 0.065f
        val neck = py - tall + head * 3.2f
        path.addCircle(px, py - tall + head * 2.1f, head, Path.Direction.CW)
        // Hat: brim and crown.
        path.addOval(px - head * 2.1f, py - tall + head * 1.0f, px + head * 2.1f, py - tall + head * 1.55f, Path.Direction.CW)
        path.addRoundRect(px - head * 1.15f, py - tall, px + head * 1.15f, py - tall + head * 1.3f, head * 0.4f, head * 0.4f, Path.Direction.CW)
        // Coat, wider at the hem, collar up.
        path.moveTo(px - head * 1.5f, neck - head * 0.6f)
        path.lineTo(px - head * 2.6f, neck + head * 0.8f)
        path.lineTo(px - head * 2.9f, py - tall * 0.22f)
        path.lineTo(px + head * 2.9f, py - tall * 0.22f)
        path.lineTo(px + head * 2.6f, neck + head * 0.8f)
        path.lineTo(px + head * 1.5f, neck - head * 0.6f)
        path.close()
        // Legs.
        path.addRect(px - head * 1.5f, py - tall * 0.23f, px - head * 0.35f, py, Path.Direction.CW)
        path.addRect(px + head * 0.35f, py - tall * 0.23f, px + head * 1.5f, py, Path.Direction.CW)
    }

    /** A tree without leaves, branching from ([bx], [by]) upward. */
    fun bareTree(bx: Float, by: Float, length: Float, colour: Int, twist: Float = 0.35f, depth: Int = 8, angle: Float = -90f) {
        fun branch(x0: Float, y0: Float, len: Float, ang: Float, d: Int, width: Float) {
            val rad = Math.toRadians(ang.toDouble())
            val x1 = x0 + (cos(rad) * len).toFloat()
            val y1 = y0 + (sin(rad) * len).toFloat()
            val p = stroke(colour, width)
            val mid = Path().apply {
                moveTo(x0, y0)
                val bend = (rnd.nextFloat() - 0.5f) * len * twist
                quadTo((x0 + x1) / 2 + bend, (y0 + y1) / 2, x1, y1)
            }
            c.drawPath(mid, p)
            if (d <= 0 || len < u * 0.6f) return
            val n = if (rnd.nextFloat() < 0.3f) 3 else 2
            for (k in 0 until n) {
                val spread = 18f + rnd.nextFloat() * 22f
                val a = ang + (k - (n - 1) / 2f) * spread * 1.4f + (rnd.nextFloat() - 0.5f) * 16f
                branch(x1, y1, len * (0.62f + rnd.nextFloat() * 0.16f), a, d - 1, width * 0.68f)
            }
        }
        branch(bx, by, length, angle, depth, length * 0.11f)
    }

    /** Buildings along [base] (a fraction of the height), up to [tallest]; lit windows in [lit] if given. */
    fun skyline(base: Float, tallest: Float, colour: Int, lit: Int?, dense: Float = 1f, spires: Boolean = false) {
        val p = fill(colour)
        var bx = r.left - u * 2
        while (bx < r.right) {
            val bw = w * (0.05f + rnd.nextFloat() * 0.09f) / dense
            val bh = h * (tallest * (0.25f + rnd.nextFloat() * 0.75f))
            val top = y(base) - bh
            c.drawRect(bx, top, bx + bw, r.bottom, p)
            when (rnd.nextInt(5)) {
                0 -> c.drawRect(bx + bw * 0.45f, top - bh * 0.12f, bx + bw * 0.55f, top, p)
                1 -> c.drawRect(bx + bw * 0.15f, top - bh * 0.05f, bx + bw * 0.85f, top, p)
                2 -> if (spires) c.drawPath(Path().apply { moveTo(bx, top); lineTo(bx + bw / 2, top - bh * 0.25f); lineTo(bx + bw, top); close() }, p)
            }
            if (lit != null) {
                val win = fill(lit)
                val cols = max(2, (bw / (u * 2.2f)).toInt())
                val rows = max(2, (bh / (u * 3f)).toInt())
                for (i in 0 until cols) for (j in 0 until rows) {
                    if (rnd.nextFloat() > 0.28f) continue
                    val wx = bx + bw * (i + 0.3f) / cols
                    val wy = top + bh * (j + 0.3f) / rows
                    c.drawRect(wx, wy, wx + bw * 0.4f / cols, wy + bh * 0.35f / rows, win)
                }
            }
            bx += bw + u * (if (rnd.nextFloat() < 0.3f) 0.8f else 0f)
        }
    }

    /** A planet, lit from [lightAngle], with rings if [rings]. */
    fun planet(cx: Float, cy: Float, radius: Float, lit: Int, dark: Int, rings: Int?, lightAngle: Float = -40f) {
        val la = Math.toRadians(lightAngle.toDouble())
        val ringTilt = -14f
        fun ring(front: Boolean) {
            if (rings == null) return
            c.save()
            c.rotate(ringTilt, cx, cy)
            // The near half of the rings passes in front of the planet, the far half behind it.
            if (front) c.clipRect(cx - radius * 3, cy, cx + radius * 3, cy + radius * 3) else c.clipRect(cx - radius * 3, cy - radius * 3, cx + radius * 3, cy)
            for (k in 0 until 4) {
                val rw = radius * (1.45f + k * 0.16f)
                val rh = rw * 0.22f
                c.drawOval(cx - rw, cy - rh, cx + rw, cy + rh, stroke(alpha(rings, 150 - k * 25), radius * (0.07f - k * 0.008f)))
            }
            c.restore()
        }
        glow(cx, cy, radius * 1.35f, lit, 70)
        ring(front = false)
        val lx = cx + (cos(la) * radius * 0.55).toFloat()
        val ly = cy + (sin(la) * radius * 0.55).toFloat()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(lx, ly, radius * 1.6f, intArrayOf(lit, mix(lit, dark, 0.55f), dark), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, radius, body)
        // Bands across it.
        c.save()
        val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        c.clipPath(clip)
        for (k in 0 until 6) {
            val by = cy - radius + radius * 2 * (k + 0.5f) / 6 + (rnd.nextFloat() - 0.5f) * radius * 0.1f
            c.drawRect(cx - radius, by, cx + radius, by + radius * (0.05f + rnd.nextFloat() * 0.08f), fill(alpha(dark, 40)))
        }
        c.restore()
        ring(front = true)
    }

    /** A sailing ship, [size] long, on the water at [waterY]. */
    fun ship(path: Path, cx: Float, waterY: Float, size: Float) {
        val hull = size
        path.moveTo(cx - hull * 0.5f, waterY - size * 0.12f)
        path.lineTo(cx + hull * 0.55f, waterY - size * 0.15f)
        path.quadTo(cx + hull * 0.42f, waterY + size * 0.03f, cx + hull * 0.3f, waterY + size * 0.04f)
        path.lineTo(cx - hull * 0.38f, waterY + size * 0.04f)
        path.quadTo(cx - hull * 0.48f, waterY - size * 0.02f, cx - hull * 0.5f, waterY - size * 0.12f)
        path.close()
        // Raised stern.
        path.addRect(cx - hull * 0.5f, waterY - size * 0.2f, cx - hull * 0.3f, waterY - size * 0.1f, Path.Direction.CW)
        val masts = floatArrayOf(-0.28f, 0.02f, 0.3f)
        val tall = floatArrayOf(0.62f, 0.78f, 0.6f)
        for (i in masts.indices) {
            val mx = cx + hull * masts[i]
            val top = waterY - size * 0.12f - size * tall[i]
            path.addRect(mx - size * 0.006f, top, mx + size * 0.006f, waterY - size * 0.12f, Path.Direction.CW)
            // Square sails, bellied.
            for (k in 0 until 3) {
                val sTop = top + size * tall[i] * (0.08f + 0.29f * k)
                val sBottom = sTop + size * tall[i] * 0.24f
                val half = size * (0.12f - 0.02f * k)
                path.moveTo(mx - half, sTop)
                path.lineTo(mx + half, sTop)
                path.quadTo(mx + half * 1.12f, (sTop + sBottom) / 2, mx + half * 0.95f, sBottom)
                path.lineTo(mx - half * 0.95f, sBottom)
                path.quadTo(mx - half * 0.8f, (sTop + sBottom) / 2, mx - half, sTop)
                path.close()
            }
            // A pennant at the top.
            path.moveTo(mx, top)
            path.lineTo(mx + size * 0.08f, top + size * 0.015f)
            path.lineTo(mx, top + size * 0.03f)
            path.close()
        }
        // The jib from the bowsprit.
        path.moveTo(cx + hull * 0.55f, waterY - size * 0.15f)
        path.lineTo(cx + hull * 0.72f, waterY - size * 0.2f)
        path.lineTo(cx + hull * 0.3f, waterY - size * 0.75f)
        path.close()
    }

    /** Rows of little wave crests below [from]. */
    fun waves(from: Float, colour: Int, rows: Int = 9) {
        for (j in 0 until rows) {
            val f = j / (rows - 1f)
            val wy = y(from + (1f - from) * f * f)
            val len = w * (0.03f + f * 0.07f)
            val p = stroke(alpha(colour, (60 + f * 100).toInt()), u * (0.2f + f * 0.5f))
            var wx = r.left + rnd.nextFloat() * len
            while (wx < r.right) {
                c.drawPath(Path().apply { moveTo(wx, wy); quadTo(wx + len / 2, wy - len * 0.18f, wx + len, wy) }, p)
                wx += len * (1.4f + rnd.nextFloat())
            }
        }
    }

    /** A flat-topped desert hill ending at [base]. */
    fun mesa(path: Path, cx: Float, base: Float, wide: Float, tall: Float) {
        path.moveTo(cx - wide / 2, base)
        path.lineTo(cx - wide * 0.34f, base - tall * 0.55f)
        path.lineTo(cx - wide * 0.3f, base - tall)
        path.lineTo(cx + wide * 0.28f, base - tall * 0.97f)
        path.lineTo(cx + wide * 0.33f, base - tall * 0.5f)
        path.lineTo(cx + wide / 2, base)
        path.close()
    }

    /** A tall cactus with two arms. */
    fun cactus(path: Path, px: Float, py: Float, tall: Float) {
        val t = tall * 0.09f
        path.addRoundRect(px - t, py - tall, px + t, py, t, t, Path.Direction.CW)
        path.addRoundRect(px - t * 4.2f, py - tall * 0.72f, px - t * 2.2f, py - tall * 0.32f, t, t, Path.Direction.CW)
        path.addRect(px - t * 4.2f, py - tall * 0.42f, px, py - tall * 0.32f, Path.Direction.CW)
        path.addRoundRect(px + t * 2.4f, py - tall * 0.82f, px + t * 4.4f, py - tall * 0.45f, t, t, Path.Direction.CW)
        path.addRect(px, py - tall * 0.55f, px + t * 4.4f, py - tall * 0.45f, Path.Direction.CW)
    }

    /** An old house on a hill: steep roofs, a tower and a chimney. Lit windows go in [windows]. */
    fun oldHouse(path: Path, cx: Float, base: Float, size: Float, windows: Path) {
        val l = cx - size * 0.42f
        val rr = cx + size * 0.3f
        val eaves = base - size * 0.42f
        path.addRect(l, eaves, rr, base, Path.Direction.CW)
        path.moveTo(l - size * 0.05f, eaves)
        path.lineTo((l + rr) / 2 - size * 0.05f, eaves - size * 0.36f)
        path.lineTo(rr + size * 0.05f, eaves)
        path.close()
        // The tower.
        val tl = rr - size * 0.02f
        val tr = rr + size * 0.2f
        path.addRect(tl, eaves - size * 0.25f, tr, base, Path.Direction.CW)
        path.moveTo(tl - size * 0.04f, eaves - size * 0.25f)
        path.lineTo((tl + tr) / 2, eaves - size * 0.7f)
        path.lineTo(tr + size * 0.04f, eaves - size * 0.25f)
        path.close()
        path.addRect(l + size * 0.08f, eaves - size * 0.36f, l + size * 0.14f, eaves - size * 0.1f, Path.Direction.CW)
        windows.addRect(l + size * 0.12f, eaves + size * 0.12f, l + size * 0.2f, eaves + size * 0.24f, Path.Direction.CW)
        windows.addRect((tl + tr) / 2 - size * 0.035f, eaves - size * 0.14f, (tl + tr) / 2 + size * 0.035f, eaves - size * 0.04f, Path.Direction.CW)
    }

    /** Bats: little jagged wings. */
    fun bats(n: Int, cx: Float, cy: Float, spread: Float, colour: Int) {
        val p = fill(colour)
        repeat(n) {
            val bx = x(cx + (rnd.nextFloat() - 0.5f) * spread)
            val by = y(cy + (rnd.nextFloat() - 0.5f) * spread * 0.5f)
            val s = u * (1.2f + rnd.nextFloat() * 1.6f)
            val path = Path().apply {
                moveTo(bx, by)
                quadTo(bx - s * 0.6f, by - s * 0.7f, bx - s * 1.5f, by - s * 0.25f)
                lineTo(bx - s * 1.1f, by + s * 0.05f)
                lineTo(bx - s * 0.75f, by - s * 0.05f)
                lineTo(bx - s * 0.4f, by + s * 0.25f)
                lineTo(bx, by + s * 0.1f)
                lineTo(bx + s * 0.4f, by + s * 0.25f)
                lineTo(bx + s * 0.75f, by - s * 0.05f)
                lineTo(bx + s * 1.1f, by + s * 0.05f)
                lineTo(bx + s * 1.5f, by - s * 0.25f)
                quadTo(bx + s * 0.6f, by - s * 0.7f, bx, by)
                close()
            }
            c.drawPath(path, p)
        }
    }

    /** A row of columns, some broken, with a beam across the whole ones. */
    fun ruins(path: Path, left: Float, right: Float, base: Float, tall: Float) {
        val n = 6
        val gap = (right - left) / n
        val cw = gap * 0.42f
        val heights = FloatArray(n) { if (it == 1 || it == 4 && rnd.nextBoolean()) tall * (0.35f + rnd.nextFloat() * 0.3f) else tall }
        for (i in 0 until n) {
            val cx = left + gap * (i + 0.5f)
            val top = base - heights[i]
            path.addRect(cx - cw / 2, top + tall * 0.05f, cx + cw / 2, base, Path.Direction.CW)
            if (heights[i] >= tall) path.addRect(cx - cw * 0.75f, top, cx + cw * 0.75f, top + tall * 0.05f, Path.Direction.CW)
            else {
                // A broken top.
                path.moveTo(cx - cw / 2, top + tall * 0.05f)
                path.lineTo(cx - cw * 0.1f, top - tall * 0.03f)
                path.lineTo(cx + cw / 2, top + tall * 0.08f)
                path.close()
            }
        }
        // The beam over the first three, and the step under them all.
        path.addRect(left + gap * 0.1f, base - tall - tall * 0.09f, left + gap * 3.4f, base - tall + tall * 0.005f, Path.Direction.CW)
        path.moveTo(left + gap * 0.1f, base - tall - tall * 0.09f)
        path.lineTo(left + gap * 1.75f, base - tall - tall * 0.25f)
        path.lineTo(left + gap * 3.4f, base - tall - tall * 0.09f)
        path.close()
        path.addRect(left - gap * 0.2f, base, right + gap * 0.2f, base + tall * 0.06f, Path.Direction.CW)
    }

    /** A tall, thin, dark cypress. */
    fun cypress(path: Path, px: Float, py: Float, tall: Float) {
        path.addOval(px - tall * 0.09f, py - tall, px + tall * 0.09f, py, Path.Direction.CW)
    }

    /** A hot-air balloon: striped envelope, ropes and a basket. */
    fun balloon(cx: Float, cy: Float, size: Float, a: Int, b: Int, basket: Int) {
        val env = Path().apply {
            moveTo(cx, cy + size * 0.62f)
            cubicTo(cx - size * 0.75f, cy + size * 0.2f, cx - size * 0.62f, cy - size * 0.62f, cx, cy - size * 0.62f)
            cubicTo(cx + size * 0.62f, cy - size * 0.62f, cx + size * 0.75f, cy + size * 0.2f, cx, cy + size * 0.62f)
            close()
        }
        c.drawPath(env, fill(a))
        c.save()
        c.clipPath(env)
        val stripe = size * 0.2f
        var sx = cx - size
        var k = 0
        while (sx < cx + size) {
            if (k % 2 == 1) c.drawRect(sx, cy - size, sx + stripe, cy + size, fill(b))
            sx += stripe
            k++
        }
        c.drawCircle(cx - size * 0.2f, cy - size * 0.25f, size * 0.55f, fill(alpha(Color.WHITE, 30)))
        c.restore()
        val ropes = stroke(basket, size * 0.015f)
        c.drawLine(cx - size * 0.12f, cy + size * 0.6f, cx - size * 0.1f, cy + size * 0.82f, ropes)
        c.drawLine(cx + size * 0.12f, cy + size * 0.6f, cx + size * 0.1f, cy + size * 0.82f, ropes)
        c.drawRoundRect(cx - size * 0.12f, cy + size * 0.82f, cx + size * 0.12f, cy + size * 0.98f, size * 0.03f, size * 0.03f, fill(basket))
    }

    /** A small house with a pointed roof, a door and windows. */
    fun cottage(cx: Float, base: Float, size: Float, wall: Int, roof: Int, door: Int) {
        c.drawRect(cx - size * 0.4f, base - size * 0.55f, cx + size * 0.4f, base, fill(wall))
        c.drawPath(Path().apply { moveTo(cx - size * 0.52f, base - size * 0.5f); lineTo(cx, base - size * 1.0f); lineTo(cx + size * 0.52f, base - size * 0.5f); close() }, fill(roof))
        c.drawRoundRect(cx - size * 0.09f, base - size * 0.32f, cx + size * 0.09f, base, size * 0.05f, size * 0.05f, fill(door))
        c.drawRect(cx - size * 0.32f, base - size * 0.42f, cx - size * 0.16f, base - size * 0.28f, fill(alpha(door, 200)))
        c.drawRect(cx + size * 0.16f, base - size * 0.42f, cx + size * 0.32f, base - size * 0.28f, fill(alpha(door, 200)))
    }

    /** A five-petalled flower. */
    fun blossom(cx: Float, cy: Float, size: Float, petal: Int, middle: Int, turn: Float = 0f) {
        for (k in 0 until 5) {
            val a = turn + k * 72f
            val rad = Math.toRadians(a.toDouble())
            val px = cx + (cos(rad) * size * 0.5).toFloat()
            val py = cy + (sin(rad) * size * 0.5).toFloat()
            c.drawCircle(px, py, size * 0.42f, fill(petal))
        }
        c.drawCircle(cx, cy, size * 0.22f, fill(middle))
    }

    /** A branch curving in from a corner, with twigs and blossoms along it. */
    /**
     * A limb tapering along the curve from ([x0], [y0]) to ([x3], [y3]) (with [x1], [y1] and [x2], [y2] pulling
     * it), [wide] across at the start and coming to a point.
     */
    fun taper(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, wide: Float, paint: Paint) {
        val n = 28
        val xs = FloatArray(2 * (n + 1))
        val ys = FloatArray(2 * (n + 1))
        for (i in 0..n) {
            val t = i / n.toFloat()
            val m = 1 - t
            val x = m * m * m * x0 + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t * t * t * x3
            val y = m * m * m * y0 + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t * t * t * y3
            val dx = 3 * m * m * (x1 - x0) + 6 * m * t * (x2 - x1) + 3 * t * t * (x3 - x2)
            val dy = 3 * m * m * (y1 - y0) + 6 * m * t * (y2 - y1) + 3 * t * t * (y3 - y2)
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
            val half = wide * (1 - t) / 2
            xs[i] = x - dy / len * half
            ys[i] = y + dx / len * half
            xs[2 * n + 1 - i] = x + dy / len * half
            ys[2 * n + 1 - i] = y - dx / len * half
        }
        val path = Path()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) path.lineTo(xs[i], ys[i])
        path.close()
        c.drawPath(path, paint)
    }

    /** A dragon in flight, its wings up, facing right (or left if [flip]), about [size] from wingtip to tail. */
    fun dragon(cx: Float, cy: Float, size: Float, flip: Boolean, colour: Int) {
        val d = if (flip) -1f else 1f
        fun px(f: Float) = cx + d * f * size
        fun py(f: Float) = cy + f * size
        val ink = fill(colour)
        // A wing: up from the shoulder to the wrist, out to its tip, then back to the body in scallops
        // between the long finger bones.
        fun wing(sx: Float, sy: Float, wx: Float, wy: Float, tips: List<Pair<Float, Float>>, bx: Float, by: Float, paint: Paint) {
            val path = Path()
            path.moveTo(px(sx), py(sy))
            path.quadTo(px((sx + wx) / 2 + 0.06f), py((sy + wy) / 2), px(wx), py(wy))
            var lastX = wx
            var lastY = wy
            val points = tips + (bx to by)
            for ((i, pt) in points.withIndex()) {
                val (tx, ty) = pt
                if (i == 0) {
                    path.lineTo(px(tx), py(ty))
                } else {
                    // The skin between two fingers sags back towards the wrist.
                    val mx = (lastX + tx) / 2 + (wx - (lastX + tx) / 2) * 0.28f
                    val my = (lastY + ty) / 2 + (wy - (lastY + ty) / 2) * 0.28f
                    path.quadTo(px(mx), py(my), px(tx), py(ty))
                }
                lastX = tx
                lastY = ty
            }
            path.close()
            c.drawPath(path, paint)
            // The finger bones.
            val bone = stroke(colour, size * 0.012f)
            for ((tx, ty) in tips.drop(1)) c.drawLine(px(wx), py(wy), px(tx), py(ty), bone)
        }
        // The far wing, behind and lower.
        wing(0.02f, -0.02f, 0.2f, -0.36f, listOf(-0.02f to -0.62f, -0.2f to -0.42f, -0.3f to -0.22f), -0.12f, 0.02f, fill(mix(colour, Color.WHITE, 0.12f)))
        // The tail, long and curling.
        taper(px(-0.24f), py(0.02f), px(-0.5f), py(0.1f), px(-0.62f), py(-0.12f), px(-0.84f), py(0.02f), size * 0.07f, ink)
        val tip = Path().apply {
            moveTo(px(-0.83f), py(0.02f))
            lineTo(px(-0.9f), py(-0.03f))
            lineTo(px(-0.92f), py(0.05f))
            close()
        }
        c.drawPath(tip, ink)
        // The body, the neck and the head.
        val body = Path().apply {
            moveTo(px(0.28f), py(-0.04f))
            cubicTo(px(0.14f), py(-0.11f), px(-0.16f), py(-0.07f), px(-0.3f), py(0.01f))
            cubicTo(px(-0.14f), py(0.09f), px(0.14f), py(0.09f), px(0.28f), py(0.03f))
            close()
        }
        c.drawPath(body, ink)
        taper(px(0.4f), py(-0.2f), px(0.36f), py(-0.1f), px(0.3f), py(-0.04f), px(0.12f), py(0.0f), size * 0.012f, ink)
        val neck = Path().apply {
            moveTo(px(0.2f), py(-0.06f))
            cubicTo(px(0.32f), py(-0.1f), px(0.38f), py(-0.2f), px(0.47f), py(-0.24f))
            lineTo(px(0.5f), py(-0.27f))
            lineTo(px(0.53f), py(-0.25f))
            lineTo(px(0.62f), py(-0.23f))
            lineTo(px(0.64f), py(-0.21f))
            lineTo(px(0.53f), py(-0.19f))
            cubicTo(px(0.44f), py(-0.15f), px(0.38f), py(-0.04f), px(0.26f), py(0.03f))
            close()
        }
        c.drawPath(neck, ink)
        // A horn swept back.
        taper(px(0.5f), py(-0.26f), px(0.47f), py(-0.31f), px(0.43f), py(-0.33f), px(0.39f), py(-0.34f), size * 0.02f, ink)
        // The legs, tucked under.
        taper(px(0.12f), py(0.04f), px(0.14f), py(0.1f), px(0.1f), py(0.13f), px(0.06f), py(0.15f), size * 0.05f, ink)
        taper(px(-0.14f), py(0.05f), px(-0.12f), py(0.12f), px(-0.18f), py(0.15f), px(-0.23f), py(0.16f), size * 0.06f, ink)
        // The near wing, raised.
        wing(0.1f, -0.06f, 0.3f, -0.48f, listOf(0.05f to -0.86f, -0.18f to -0.66f, -0.3f to -0.42f, -0.32f to -0.18f), -0.08f, -0.02f, ink)
    }

    fun blossomBranch(fromX: Float, fromY: Float, toX: Float, toY: Float, bark: Int, petal: Int, middle: Int, scale: Float = 1f) {
        val u = this.u * scale
        val ctrlX = (fromX + toX) / 2 + (toY - fromY) * 0.25f
        val ctrlY = (fromY + toY) / 2 - (toX - fromX) * 0.15f
        val steps = 24
        var px = fromX
        var py = fromY
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val nx = (1 - t) * (1 - t) * fromX + 2 * (1 - t) * t * ctrlX + t * t * toX
            val ny = (1 - t) * (1 - t) * fromY + 2 * (1 - t) * t * ctrlY + t * t * toY
            c.drawLine(px, py, nx, ny, stroke(bark, u * (2.4f * (1 - t) + 0.4f)))
            if (i % 4 == 0 && i < steps) {
                // A twig, and flowers on it.
                val side = if (i % 8 == 0) 1 else -1
                val tx = nx + side * u * (5f + rnd.nextFloat() * 4f)
                val ty = ny - u * (4f + rnd.nextFloat() * 5f)
                c.drawLine(nx, ny, tx, ty, stroke(bark, u * 0.7f))
                blossom(tx, ty, u * (2.4f + rnd.nextFloat() * 1.2f), petal, middle, rnd.nextFloat() * 72f)
                if (rnd.nextBoolean()) blossom((nx + tx) / 2 + u * 2, (ny + ty) / 2, u * 1.6f, petal, middle, rnd.nextFloat() * 72f)
            }
            px = nx
            py = ny
        }
        blossom(toX, toY, u * 3f, petal, middle)
    }
}

/** The scenes painted for each genre, for the designs with a picture. */
internal object CoverArt {

    /** Paints a scene for [genre] filling [r]; [seed] varies it. */
    fun scene(c: Canvas, r: RectF, genre: CoverGenre, seed: Int) {
        c.save()
        c.clipRect(r)
        val p = Painter(c, r, Random(seed))
        when (genre) {
            CoverGenre.FANTASY -> if (p.rnd.nextInt(5) < 2) dragonPeaks(p) else fantasy(p)
            CoverGenre.MAGIC_SCHOOL -> Scenes.magicSchool(p)
            CoverGenre.DARK_MAGIC -> Scenes.darkMagic(p)
            CoverGenre.WITCHES -> Scenes.witches(p)
            CoverGenre.QUEST -> Scenes.quest(p)
            CoverGenre.STORM_EPIC -> Scenes.stormEpic(p)
            CoverGenre.MYTHOLOGY -> Scenes.mythology(p)
            CoverGenre.FAIRY_TALE -> Scenes.fairyTale(p)
            CoverGenre.ROMANTASY -> Scenes.romantasy(p)
            CoverGenre.URBAN_FANTASY -> Scenes.urbanFantasy(p)
            CoverGenre.WUXIA -> p.wuxiaScene()
            CoverGenre.SAMURAI -> p.samuraiScene()
            CoverGenre.SCIENCE_FICTION -> space(p)
            CoverGenre.SPACE_OPERA -> Scenes.spaceOpera(p)
            CoverGenre.STARSHIP -> Scenes.starship(p)
            CoverGenre.CYBERPUNK -> Scenes.cyberpunk(p)
            CoverGenre.DYSTOPIA -> Scenes.dystopia(p)
            CoverGenre.STEAMPUNK -> Scenes.steampunk(p)
            CoverGenre.SUPERHERO -> Scenes.superhero(p)
            CoverGenre.MYSTERY -> foggyStreet(p)
            CoverGenre.DETECTIVE -> MoreScenes.detective(p)
            CoverGenre.COSY_MYSTERY -> MoreScenes.cosyMystery(p)
            CoverGenre.THRILLER -> cityAtDusk(p)
            CoverGenre.SPY -> MoreScenes.spy(p)
            CoverGenre.HISTORICAL -> if (p.rnd.nextBoolean()) ruins(p) else sailing(p, golden = true)
            CoverGenre.WAR -> MoreScenes.war(p)
            CoverGenre.KNIGHTS -> MoreScenes.knights(p)
            CoverGenre.REGENCY -> MoreScenes.regency(p)
            CoverGenre.ANCIENT -> MoreScenes.ancient(p)
            CoverGenre.ADVENTURE -> if (p.rnd.nextInt(3) < 2) sailing(p, golden = false) else MoreScenes.jungle(p)
            CoverGenre.WESTERN -> desert(p)
            CoverGenre.ROMANCE -> blossomSea(p)
            CoverGenre.MODERN_ROMANCE -> MoreScenes.modernRomance(p)
            CoverGenre.DARK_ROMANCE -> MoreScenes.darkRomance(p)
            CoverGenre.FLUFF -> MoreScenes.fluff(p)
            CoverGenre.ANGST -> MoreScenes.angst(p)
            CoverGenre.HORROR -> hauntedHill(p)
            CoverGenre.VAMPIRES -> MoreScenes.vampires(p)
            CoverGenre.COSMIC_HORROR -> MoreScenes.cosmicHorror(p)
            CoverGenre.HALLOWEEN -> MoreScenes.halloween(p)
            CoverGenre.LITERARY -> lonelyTree(p)
            CoverGenre.SLICE_OF_LIFE -> MoreScenes.cafe(p)
            CoverGenre.SCHOOL -> MoreScenes.school(p)
            CoverGenre.SPORTS -> MoreScenes.sports(p)
            CoverGenre.MUSIC -> MoreScenes.music(p)
            CoverGenre.CHRISTMAS -> MoreScenes.christmas(p)
            CoverGenre.NON_FICTION -> shapes(p)
            CoverGenre.CHILDREN -> hills(p)
        }
        p.grain(0.8f)
        c.restore()
    }

    private fun fantasy(p: Painter) {
        val variant = p.rnd.nextInt(3)
        val skies = listOf(
            intArrayOf(rgb(0x1B1E3D), rgb(0x5A3D6B), rgb(0xD9875A), rgb(0xF2C27A)),
            intArrayOf(rgb(0x0B1028), rgb(0x1E2B57), rgb(0x46628F), rgb(0x8EA6C4)),
            intArrayOf(rgb(0x2C3E66), rgb(0x7A6C93), rgb(0xE6A77D), rgb(0xF6D9A6)),
        )
        val sky = skies[variant]
        p.sky(sky, floatArrayOf(0f, 0.38f, 0.66f, 0.8f))
        val night = variant == 1
        if (night) p.stars(160, 0.6f)
        val sunX = p.x(0.2f + p.rnd.nextFloat() * 0.6f)
        val sunY = p.y(if (night) 0.2f else 0.55f)
        val light = if (night) rgb(0xE8EEF7) else rgb(0xFFE1A1)
        p.glow(sunX, sunY, p.w * 0.5f, light, if (night) 90 else 170)
        p.disc(sunX, sunY, p.u * (if (night) 6f else 7f), alpha(light, 235))
        p.clouds(5, 0.42f, 0.18f, sky[3], 45)
        val haze = sky[3]
        val far = mix(sky[1], haze, 0.35f)
        p.layer(p.ridge(0.62f, 0.38f, 0.58f), mix(far, haze, 0.35f), far, 0.38f, 0.75f)
        p.mist(0.62f, 0.12f, haze, 110)
        val mid = mix(sky[0], sky[1], 0.5f)
        val crag = p.ridge(0.74f, 0.55f, 0.5f)
        p.layer(crag, mix(mid, haze, 0.25f), mid, 0.55f, 0.85f)
        // The castle, on its crag, in the middle distance.
        val castle = Path()
        val windows = Path()
        val cx = p.x(0.25f + p.rnd.nextFloat() * 0.5f)
        p.castle(castle, cx, p.y(0.74f), p.w * 0.36f, windows)
        p.c.drawPath(castle, p.fill(mix(mid, sky[0], 0.35f)))
        p.c.drawPath(windows, p.fill(alpha(rgb(0xFFD38A), if (night) 230 else 150)))
        p.mist(0.76f, 0.1f, haze, 90)
        // The near hillside with pines, and someone looking on.
        val near = mix(sky[0], Color.BLACK, 0.45f)
        val hill = p.ridge(0.95f, 0.8f, 0.4f)
        p.layer(hill, near, mix(near, Color.BLACK, 0.3f), 0.8f, 1f)
        val pines = Path()
        val left = p.rnd.nextBoolean()
        for (k in 0 until 7) {
            val f = if (left) p.rnd.nextFloat() * 0.35f else 0.65f + p.rnd.nextFloat() * 0.35f
            p.pine(pines, p.x(f), p.groundAt(hill, p.x(f)) + p.u * (1f + p.rnd.nextFloat() * 4f), p.h * (0.12f + p.rnd.nextFloat() * 0.1f))
        }
        p.c.drawPath(pines, p.fill(near))
        val figure = Path()
        val fx = p.x(if (left) 0.72f else 0.28f)
        p.cloaked(figure, fx, p.groundAt(hill, fx) + p.u * 0.5f, p.h * 0.1f)
        p.c.drawPath(figure, p.fill(mix(near, Color.BLACK, 0.4f)))
        p.birds(5, if (left) 0.7f else 0.3f, 0.3f, 0.2f, alpha(near, 200))
    }

    /** A dragon over snowy peaks at sunset or under a cold sky, and someone on the rocks watching it. */
    private fun dragonPeaks(p: Painter) {
        val warm = p.rnd.nextBoolean()
        val sky = if (warm) intArrayOf(rgb(0x241A38), rgb(0x7E3A4E), rgb(0xD9774E), rgb(0xF4C27C))
        else intArrayOf(rgb(0x0E1A2E), rgb(0x2D4A6E), rgb(0x86A2BF), rgb(0xDCE5EC))
        p.sky(sky, floatArrayOf(0f, 0.42f, 0.7f, 0.86f))
        val sunX = p.x(0.25f + p.rnd.nextFloat() * 0.5f)
        p.glow(sunX, p.y(0.64f), p.w * 0.65f, sky[3], 170)
        p.clouds(6, 0.34f, 0.3f, sky[3], 38)
        val haze = sky[3]
        // Far peaks, high and hazy.
        val far = mix(sky[1], haze, 0.35f)
        p.layer(p.ridge(0.62f, 0.34f, 0.62f), mix(far, haze, 0.3f), far, 0.34f, 0.72f)
        p.mist(0.62f, 0.12f, haze, 120)
        val mid = mix(sky[0], sky[1], 0.45f)
        p.layer(p.ridge(0.8f, 0.54f, 0.6f), mix(mid, haze, 0.2f), mid, 0.54f, 0.92f)
        p.mist(0.8f, 0.08f, haze, 80)
        // The dragon, over the peaks, all of it in the picture.
        val flip = p.rnd.nextBoolean()
        val size = min(p.w * 0.44f, p.h * 0.5f)
        val d = if (flip) -1f else 1f
        p.dragon(p.x(0.5f) + d * 0.14f * size + (p.rnd.nextFloat() - 0.5f) * p.w * 0.08f, p.y(0.48f), size, flip, mix(sky[0], Color.BLACK, 0.3f))
        // The near rocks, and someone on them watching.
        val near = mix(sky[0], Color.BLACK, 0.5f)
        val rocks = p.heights(rough = 0.55f)
        p.layer(p.ridge(0.97f, 0.86f, peaks = rocks), near, mix(near, Color.BLACK, 0.3f), 0.86f, 1f)
        val at = if (flip) 0.2f else 0.8f
        val ground = 0.97f + (0.86f - 0.97f) * rocks[(at * (rocks.size - 1)).toInt()]
        val figure = Path()
        p.cloaked(figure, p.x(at), p.y(ground + 0.01f), p.h * 0.09f)
        p.c.drawPath(figure, p.fill(mix(near, Color.BLACK, 0.4f)))
    }

    private fun space(p: Painter) {
        val deep = listOf(rgb(0x05060F), rgb(0x0A0F24), rgb(0x10081E))[p.rnd.nextInt(3)]
        p.sky(intArrayOf(deep, mix(deep, rgb(0x1A2450), 0.6f), deep))
        val hues = listOf(rgb(0xB23A7A), rgb(0x2A7FB8), rgb(0x6A3FB5), rgb(0x2BA39A), rgb(0xD2643C))
        repeat(5) {
            p.glow(p.x(p.rnd.nextFloat()), p.y(p.rnd.nextFloat() * 0.7f), p.w * (0.25f + p.rnd.nextFloat() * 0.35f), hues.random(p.rnd), 55)
        }
        p.stars(320, 1f)
        // A big ringed planet, low on one side; a small moon on the other.
        val right = p.rnd.nextBoolean()
        val lit = listOf(rgb(0xE9C58E), rgb(0x9FD3E0), rgb(0xE4A07A), rgb(0xC7B6E8)).random(p.rnd)
        p.planet(p.x(if (right) 0.68f else 0.32f), p.y(0.66f), p.w * 0.27f, lit, mix(lit, deep, 0.85f), rings = mix(lit, Color.WHITE, 0.3f), lightAngle = if (right) -140f else -40f)
        p.planet(p.x(if (right) 0.18f else 0.82f), p.y(0.22f), p.w * 0.05f, rgb(0xD8D8E0), rgb(0x2A2A3A), rings = null, lightAngle = if (right) -140f else -40f)
        // A ship heading off, its engines glowing.
        val sx = p.x(if (right) 0.3f else 0.7f)
        val sy = p.y(0.35f)
        val s = p.u * 7f
        val dir = if (right) -1f else 1f
        val ship = Path().apply {
            moveTo(sx + dir * s * 1.4f, sy)
            lineTo(sx - dir * s * 0.8f, sy - s * 0.42f)
            lineTo(sx - dir * s * 0.55f, sy)
            lineTo(sx - dir * s * 0.8f, sy + s * 0.42f)
            close()
        }
        p.glow(sx - dir * s * 1.1f, sy, s * 1.6f, rgb(0x7FD8FF), 160)
        p.c.drawPath(ship, p.fill(rgb(0xC9D3E3)))
        p.c.drawLine(sx - dir * s * 0.7f, sy, sx - dir * s * 3.5f, sy, p.stroke(alpha(rgb(0x7FD8FF), 140), s * 0.18f))
    }

    private fun foggyStreet(p: Painter) {
        val sky = intArrayOf(rgb(0x0E1622), rgb(0x22344A), rgb(0x56677A))
        p.sky(sky, floatArrayOf(0f, 0.55f, 1f))
        val moonX = p.x(0.25f + p.rnd.nextFloat() * 0.5f)
        p.glow(moonX, p.y(0.18f), p.w * 0.3f, rgb(0xDDE6F0), 110)
        p.disc(moonX, p.y(0.18f), p.u * 5f, rgb(0xE6ECF3))
        p.clouds(6, 0.2f, 0.2f, rgb(0x56677A), 70)
        p.skyline(0.7f, 0.42f, rgb(0x2A3A4E), alpha(rgb(0xF2C46B), 90), dense = 1.4f)
        p.mist(0.66f, 0.16f, rgb(0x8796A6), 120)
        p.skyline(0.82f, 0.5f, rgb(0x121A25), alpha(rgb(0xF5C365), 200), dense = 1f, spires = true)
        p.mist(0.8f, 0.12f, rgb(0x8796A6), 90)
        // The wet street, and a lamp with someone under it.
        p.c.drawRect(p.r.left, p.y(0.86f), p.r.right, p.r.bottom, p.fill(rgb(0x0B1119)))
        val lampX = p.x(0.62f + p.rnd.nextFloat() * 0.2f)
        val lampTop = p.y(0.5f)
        val warm = rgb(0xFFD58A)
        val cone = Path().apply {
            moveTo(lampX - p.u * 2, lampTop + p.u * 3)
            lineTo(lampX - p.w * 0.2f, p.y(0.97f))
            lineTo(lampX + p.w * 0.2f, p.y(0.97f))
            lineTo(lampX + p.u * 2, lampTop + p.u * 3)
            close()
        }
        p.c.drawPath(cone, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, lampTop, 0f, p.y(0.97f), alpha(warm, 110), alpha(warm, 10), Shader.TileMode.CLAMP)
        })
        p.glow(lampX, lampTop + p.u * 2, p.w * 0.16f, warm, 200)
        p.c.drawRect(lampX - p.u * 0.6f, lampTop + p.u * 3, lampX + p.u * 0.6f, p.y(0.9f), p.fill(rgb(0x06090D)))
        p.c.drawRect(lampX - p.u * 2.2f, lampTop, lampX + p.u * 2.2f, lampTop + p.u * 3.5f, p.fill(rgb(0x06090D)))
        p.disc(lampX, lampTop + p.u * 2, p.u * 1.4f, rgb(0xFFF1CF))
        val man = Path()
        p.inHat(man, lampX - p.w * 0.12f, p.y(0.92f), p.h * 0.24f)
        p.c.drawPath(man, p.fill(rgb(0x05080C)))
        // His reflection on the wet stones.
        p.c.drawRect(lampX - p.w * 0.16f, p.y(0.92f), lampX - p.w * 0.08f, p.y(0.98f), p.fill(alpha(warm, 30)))
    }

    private fun cityAtDusk(p: Painter) {
        p.sky(intArrayOf(rgb(0x1A0E1E), rgb(0x6B1E24), rgb(0xD9552E), rgb(0xF3A04A)), floatArrayOf(0f, 0.4f, 0.75f, 1f))
        val sunX = p.x(0.3f + p.rnd.nextFloat() * 0.4f)
        p.glow(sunX, p.y(0.72f), p.w * 0.45f, rgb(0xFFC27A), 190)
        p.disc(sunX, p.y(0.72f), p.u * 9f, rgb(0xFFD9A0))
        // Searchlights.
        for (k in 0 until 2) {
            val bx = p.x(0.2f + 0.6f * k + (p.rnd.nextFloat() - 0.5f) * 0.1f)
            val ang = if (k == 0) 0.25f else -0.25f
            val beam = Path().apply {
                moveTo(bx, p.y(0.8f))
                lineTo(bx + p.w * (ang - 0.04f), p.r.top)
                lineTo(bx + p.w * (ang + 0.04f), p.r.top)
                close()
            }
            p.c.drawPath(beam, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, p.y(0.8f), 0f, p.r.top, alpha(Color.WHITE, 70), alpha(Color.WHITE, 0), Shader.TileMode.CLAMP)
            })
        }
        p.skyline(0.78f, 0.45f, rgb(0x3A1420), null, dense = 1.3f)
        p.skyline(0.92f, 0.62f, rgb(0x12070C), alpha(rgb(0xFFB25E), 170), dense = 0.9f, spires = true)
        // A helicopter.
        val hx = p.x(0.25f + p.rnd.nextFloat() * 0.5f)
        val hy = p.y(0.28f)
        val s = p.u * 5f
        val heli = p.fill(rgb(0x12070C))
        p.c.drawOval(hx - s, hy - s * 0.45f, hx + s * 0.6f, hy + s * 0.45f, heli)
        p.c.drawRect(hx + s * 0.4f, hy - s * 0.12f, hx + s * 2.2f, hy + s * 0.08f, heli)
        p.c.drawRect(hx - s * 1.8f, hy - s * 0.62f, hx + s * 1.6f, hy - s * 0.55f, heli)
        p.c.drawLine(hx - s * 0.6f, hy + s * 0.6f, hx + s * 0.4f, hy + s * 0.6f, p.stroke(rgb(0x12070C), s * 0.12f))
    }

    private fun hauntedHill(p: Painter) {
        p.sky(intArrayOf(rgb(0x0B1210), rgb(0x24342D), rgb(0x4C5C4E)), floatArrayOf(0f, 0.6f, 1f))
        val moonX = p.x(0.35f + p.rnd.nextFloat() * 0.3f)
        val moonY = p.y(0.3f)
        p.glow(moonX, moonY, p.w * 0.55f, rgb(0xE8F0D8), 150)
        p.disc(moonX, moonY, p.w * 0.16f, rgb(0xEEF2DD))
        // The moon's seas.
        for (k in 0 until 5) p.disc(moonX + (p.rnd.nextFloat() - 0.5f) * p.w * 0.2f, moonY + (p.rnd.nextFloat() - 0.5f) * p.w * 0.2f, p.w * (0.02f + p.rnd.nextFloat() * 0.03f), alpha(rgb(0xBFC8AE), 120))
        p.clouds(5, 0.38f, 0.2f, rgb(0x2C3A33), 120)
        val hill = Path().apply {
            moveTo(p.r.left - 2, p.r.bottom)
            lineTo(p.r.left - 2, p.y(0.82f))
            quadTo(p.x(0.45f), p.y(0.62f), p.r.right + 2, p.y(0.86f))
            lineTo(p.r.right + 2, p.r.bottom)
            close()
        }
        val dark = rgb(0x070A09)
        p.c.drawPath(hill, p.fill(dark))
        val house = Path()
        val windows = Path()
        p.oldHouse(house, p.x(0.47f), p.groundUnder(hill, p.x(0.47f) - p.w * 0.16f, p.x(0.47f) + p.w * 0.2f) + p.u * 0.6f, p.w * 0.36f, windows)
        p.c.drawPath(house, p.fill(dark))
        p.glow(p.x(0.4f), p.y(0.62f), p.w * 0.05f, rgb(0xF5C46A), 140)
        p.c.drawPath(windows, p.fill(rgb(0xF3C46B)))
        p.bareTree(p.x(0.12f), p.y(0.86f), p.h * 0.17f, dark, twist = 0.5f)
        p.bareTree(p.x(0.86f), p.y(0.88f), p.h * 0.14f, dark, twist = 0.5f)
        p.bats(6, 0.6f, 0.2f, 0.4f, dark)
        p.mist(0.9f, 0.14f, rgb(0x9DAE9E), 100)
    }

    private fun blossomSea(p: Painter) {
        p.sky(intArrayOf(rgb(0x6E5A8C), rgb(0xD891A6), rgb(0xF6C3A0), rgb(0xFBE3C3)), floatArrayOf(0f, 0.35f, 0.62f, 0.68f))
        val sunX = p.x(0.5f + (p.rnd.nextFloat() - 0.5f) * 0.3f)
        val horizon = 0.68f
        p.glow(sunX, p.y(horizon), p.w * 0.45f, rgb(0xFFF0C8), 200)
        p.disc(sunX, p.y(horizon - 0.02f), p.u * 8f, rgb(0xFFF3D6))
        p.clouds(6, 0.35f, 0.25f, rgb(0xFFE4E8), 60)
        // The sea, with the sun's path across it.
        p.c.drawRect(p.r.left, p.y(horizon), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(horizon), 0f, p.r.bottom, rgb(0xC98FA3), rgb(0x5D4E78), Shader.TileMode.CLAMP)
        })
        for (k in 0 until 12) {
            val f = k / 11f
            val yy = p.y(horizon + 0.01f + (1 - horizon) * f * 0.9f)
            val half = p.w * (0.02f + f * 0.12f) * (0.6f + p.rnd.nextFloat() * 0.6f)
            p.c.drawLine(sunX - half, yy, sunX + half, yy, p.stroke(alpha(rgb(0xFFF0D0), 170), p.u * (0.3f + f)))
        }
        p.waves(horizon + 0.04f, rgb(0xF6D2C6), rows = 7)
        // A blossoming branch across the top (lower on a tall picture, where the title goes over the top).
        val fromLeft = p.rnd.nextBoolean()
        val top = if (p.h > p.w * 1.15f) 0.4f else 0.14f
        p.blossomBranch(
            if (fromLeft) p.r.left - p.u * 2 else p.r.right + p.u * 2, p.y(top),
            p.x(if (fromLeft) 0.7f else 0.3f), p.y(top + 0.12f),
            rgb(0x3E2A33), rgb(0xF7C7D6), rgb(0xB84A6E), scale = 1.6f,
        )
        // Petals on the wind.
        repeat(18) { p.blossom(p.x(p.rnd.nextFloat()), p.y(0.25f + p.rnd.nextFloat() * 0.5f), p.u * 0.9f, alpha(rgb(0xFBD3DF), 200), alpha(rgb(0xFBD3DF), 200)) }
    }

    private fun ruins(p: Painter) {
        p.sky(intArrayOf(rgb(0x2E4A6B), rgb(0xA8869A), rgb(0xF1B77C), rgb(0xF8D9A0)), floatArrayOf(0f, 0.45f, 0.75f, 0.9f))
        val sunX = p.x(0.3f + p.rnd.nextFloat() * 0.4f)
        p.glow(sunX, p.y(0.72f), p.w * 0.5f, rgb(0xFFE2A8), 200)
        p.disc(sunX, p.y(0.72f), p.u * 6f, rgb(0xFFEBC0))
        p.clouds(5, 0.3f, 0.2f, rgb(0xF8D9C0), 55)
        p.layer(p.ridge(0.78f, 0.62f, 0.35f), rgb(0x9B7F86), rgb(0x7C6575), 0.6f, 0.85f)
        p.mist(0.76f, 0.1f, rgb(0xF8D9A0), 110)
        val ground = rgb(0x2B2026)
        val land = p.ridge(0.95f, 0.84f, 0.3f)
        p.layer(land, mix(ground, rgb(0x7C6575), 0.25f), ground, 0.84f, 1f)
        val ruin = Path()
        p.ruins(ruin, p.x(0.18f), p.x(0.82f), p.groundUnder(land, p.x(0.18f), p.x(0.82f)) + p.u * 0.5f, p.h * 0.36f)
        p.c.drawPath(ruin, p.fill(ground))
        val trees = Path()
        p.cypress(trees, p.x(0.08f), p.groundAt(land, p.x(0.08f)) + p.u, p.h * 0.35f)
        p.cypress(trees, p.x(0.92f), p.groundAt(land, p.x(0.92f)) + p.u, p.h * 0.3f)
        p.cypress(trees, p.x(0.97f), p.groundAt(land, p.x(0.97f)) + p.u, p.h * 0.24f)
        p.c.drawPath(trees, p.fill(ground))
        p.birds(4, 0.5f, 0.35f, 0.3f, alpha(ground, 180))
    }

    private fun sailing(p: Painter, golden: Boolean) {
        val sky = if (golden) intArrayOf(rgb(0x31476E), rgb(0xB78D8A), rgb(0xF3C27F), rgb(0xF9DDA6))
        else intArrayOf(rgb(0x1F3B57), rgb(0x45708F), rgb(0x9DBBCB), rgb(0xD8E3E3))
        val horizon = 0.62f
        p.sky(sky, floatArrayOf(0f, 0.38f, 0.58f, horizon))
        val sunX = p.x(0.25f + p.rnd.nextFloat() * 0.5f)
        if (golden) {
            p.glow(sunX, p.y(horizon), p.w * 0.5f, rgb(0xFFE0A0), 200)
            p.disc(sunX, p.y(horizon - 0.03f), p.u * 6f, rgb(0xFFEDC4))
        }
        p.clouds(7, 0.3f, 0.3f, if (golden) rgb(0xF6D7B6) else rgb(0xF0F4F6), if (golden) 60 else 215, puffy = !golden)
        // Islands on the horizon.
        p.layer(p.ridge(horizon, horizon - 0.06f, 0.45f, bottom = horizon + 0.01f), mix(sky[1], sky[3], 0.4f), mix(sky[1], sky[3], 0.2f), horizon - 0.06f, horizon)
        val sea = if (golden) intArrayOf(rgb(0x6C6A80), rgb(0x22314A)) else intArrayOf(rgb(0x3E6B86), rgb(0x0E2236))
        p.c.drawRect(p.r.left, p.y(horizon), p.r.right, p.r.bottom, Paint().apply {
            shader = LinearGradient(0f, p.y(horizon), 0f, p.r.bottom, sea[0], sea[1], Shader.TileMode.CLAMP)
        })
        p.waves(horizon + 0.02f, if (golden) rgb(0xF9DDA6) else rgb(0xD8E3E3))
        val ship = Path()
        val sx = p.x(0.35f + p.rnd.nextFloat() * 0.3f)
        p.ship(ship, sx, p.y(0.8f), p.w * 0.42f)
        val hull = if (golden) rgb(0x1C1820) else rgb(0x0C1620)
        p.c.drawPath(ship, p.fill(hull))
        // Its reflection, broken by the water.
        p.c.save()
        p.c.scale(1f, -0.35f, sx, p.y(0.8f))
        p.c.drawPath(ship, p.fill(alpha(hull, 60)))
        p.c.restore()
        p.birds(4, 0.7f, 0.25f, 0.25f, alpha(hull, 200))
    }

    private fun desert(p: Painter) {
        p.sky(intArrayOf(rgb(0x3B2A4A), rgb(0xB34B3A), rgb(0xEE8A3C), rgb(0xF7C66B)), floatArrayOf(0f, 0.4f, 0.65f, 0.8f))
        val sunX = p.x(0.3f + p.rnd.nextFloat() * 0.4f)
        p.glow(sunX, p.y(0.66f), p.w * 0.5f, rgb(0xFFD27A), 210)
        p.disc(sunX, p.y(0.66f), p.w * 0.13f, rgb(0xFFE3A3))
        p.c.drawRect(sunX - p.w * 0.14f, p.y(0.62f), sunX + p.w * 0.14f, p.y(0.625f), p.fill(alpha(rgb(0xEE8A3C), 120)))
        val far = rgb(0x9C4A3E)
        val mesas = Path()
        // (Down past the ground in front of them, so they stand on it however wide the picture is.)
        p.mesa(mesas, p.x(0.18f), p.y(0.95f), p.w * 0.4f, p.h * 0.33f)
        p.mesa(mesas, p.x(0.78f), p.y(0.95f), p.w * 0.46f, p.h * 0.37f)
        p.c.drawPath(mesas, p.fill(far))
        p.mist(0.77f, 0.08f, rgb(0xF7C66B), 100)
        val ground = rgb(0x2A1414)
        p.layer(p.ridge(0.92f, 0.82f, 0.3f), rgb(0x5C2A22), ground, 0.82f, 1f)
        val cacti = Path()
        p.cactus(cacti, p.x(0.15f), p.y(0.95f), p.h * 0.22f)
        p.cactus(cacti, p.x(0.86f), p.y(0.93f), p.h * 0.14f)
        p.c.drawPath(cacti, p.fill(ground))
        p.birds(3, 0.55f, 0.3f, 0.2f, alpha(ground, 220))
    }

    private fun lonelyTree(p: Painter) {
        val tones = listOf(
            intArrayOf(rgb(0x8EA3B0), rgb(0xC9D2D2), rgb(0xE9E6DA)),
            intArrayOf(rgb(0xB59E8C), rgb(0xE0CCB5), rgb(0xF2E8D8)),
            intArrayOf(rgb(0x7F9488), rgb(0xC4CFBF), rgb(0xEDEDE0)),
        )[p.rnd.nextInt(3)]
        p.sky(tones, floatArrayOf(0f, 0.55f, 1f))
        p.glow(p.x(0.7f), p.y(0.3f), p.w * 0.5f, Color.WHITE, 90)
        for (k in 0 until 4) {
            val f = k / 3f
            val shade = mix(tones[2], mix(tones[0], Color.BLACK, 0.45f), 0.25f + 0.55f * f)
            p.layer(p.ridge(0.66f + 0.1f * k, 0.56f + 0.1f * k, 0.32f), shade, mix(shade, Color.BLACK, 0.06f), 0.5f, 1f)
            p.mist(0.68f + 0.1f * k, 0.08f, tones[2], 120)
        }
        val ink = mix(tones[0], Color.BLACK, 0.7f)
        val tx = p.x(0.3f + p.rnd.nextFloat() * 0.4f)
        val ty = p.y(0.84f)
        p.bareTree(tx, ty, p.h * 0.14f, ink, twist = 0.25f, depth = 9)
        p.birds(3, 0.75f, 0.25f, 0.15f, alpha(ink, 200))
    }

    private fun shapes(p: Painter) {
        val tones = listOf(
            intArrayOf(rgb(0x1E3A5F), rgb(0xE8B04A), rgb(0xD9DED8), rgb(0xC0583F)),
            intArrayOf(rgb(0x23443A), rgb(0xE6C26E), rgb(0xE9E4D6), rgb(0x8E3B46)),
            intArrayOf(rgb(0x2B2B33), rgb(0x3FA7A0), rgb(0xEEE8DC), rgb(0xE07B39)),
        )[p.rnd.nextInt(3)]
        p.sky(intArrayOf(tones[0], mix(tones[0], Color.BLACK, 0.25f)))
        // Rings and arcs, as on a scientific instrument.
        val cx = p.x(0.5f)
        val cy = p.y(0.55f)
        val big = min(p.w, p.h) * 0.42f
        p.disc(cx, cy, big, alpha(tones[2], 30))
        for (k in 0 until 5) p.c.drawCircle(cx, cy, big * (0.35f + k * 0.16f), p.stroke(alpha(tones[2], 120), p.u * 0.35f))
        p.c.drawArc(RectF(cx - big, cy - big, cx + big, cy + big), -150f, 120f, false, p.stroke(tones[1], p.u * 2.2f))
        p.c.drawArc(RectF(cx - big * 0.67f, cy - big * 0.67f, cx + big * 0.67f, cy + big * 0.67f), 20f, 140f, false, p.stroke(tones[3], p.u * 2.2f))
        p.disc(cx + big * 0.35f, cy - big * 0.35f, big * 0.14f, tones[1])
        p.disc(cx - big * 0.5f, cy + big * 0.2f, big * 0.07f, tones[3])
        for (k in 0 until 24) {
            val a = k * 15.0
            val r1 = big * 1.05f
            val r2 = big * (if (k % 6 == 0) 1.18f else 1.1f)
            p.c.drawLine(cx + (cos(Math.toRadians(a)) * r1).toFloat(), cy + (sin(Math.toRadians(a)) * r1).toFloat(),
                cx + (cos(Math.toRadians(a)) * r2).toFloat(), cy + (sin(Math.toRadians(a)) * r2).toFloat(), p.stroke(alpha(tones[2], 150), p.u * 0.4f))
        }
    }

    private fun hills(p: Painter) {
        p.sky(intArrayOf(rgb(0x5FB3E8), rgb(0x9ED6F2), rgb(0xD8F0F8)), floatArrayOf(0f, 0.6f, 1f))
        val sx = p.x(if (p.rnd.nextBoolean()) 0.8f else 0.2f)
        val sy = p.y(0.17f)
        val sun = rgb(0xFFD23F)
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0)
            p.c.drawLine(sx + (cos(a) * p.u * 9).toFloat(), sy + (sin(a) * p.u * 9).toFloat(), sx + (cos(a) * p.u * 13).toFloat(), sy + (sin(a) * p.u * 13).toFloat(), p.stroke(sun, p.u * 1.2f))
        }
        p.disc(sx, sy, p.u * 7f, sun)
        p.clouds(4, 0.25f, 0.15f, Color.WHITE, 235, puffy = true)
        val greens = intArrayOf(rgb(0x8CCB5E), rgb(0x6DB84A), rgb(0x4E9E3A))
        for (k in 0 until 3) {
            val hill = Path().apply {
                val base = 0.62f + 0.12f * k
                moveTo(p.r.left - 2, p.r.bottom)
                lineTo(p.r.left - 2, p.y(base + 0.06f))
                val bump = p.rnd.nextFloat() * 0.4f + 0.3f
                quadTo(p.x(bump), p.y(base - 0.14f), p.x(bump + 0.35f), p.y(base))
                quadTo(p.x(bump + 0.6f), p.y(base + 0.08f), p.r.right + 2, p.y(base - 0.04f))
                lineTo(p.r.right + 2, p.r.bottom)
                close()
            }
            p.c.drawPath(hill, p.fill(greens[k]))
            if (k == 1) p.cottage(p.x(0.68f), p.groundUnder(hill, p.x(0.68f) - p.w * 0.05f, p.x(0.68f) + p.w * 0.05f) + p.u * 0.5f, p.w * 0.13f, rgb(0xFFF4DE), rgb(0xD9483B), rgb(0x6B4226))
        }
        p.balloon(p.x(0.3f), p.y(0.38f), p.w * 0.11f, rgb(0xE8483D), rgb(0xFFD23F), rgb(0x7A5230))
        repeat(28) {
            val fx = p.x(p.rnd.nextFloat())
            val fy = p.y(0.8f + p.rnd.nextFloat() * 0.18f)
            p.blossom(fx, fy, p.u * 0.9f, listOf(rgb(0xFFFFFF), rgb(0xFF8FB1), rgb(0xFFD23F)).random(p.rnd), rgb(0xF29F05))
        }
    }

    // ---- gold-stamped line drawings ------------------------------------------------------------------

    /**
     * A line drawing for [genre] in [r], drawn with [ink] (a paint with the foil's shader on it), as is
     * stamped in gold on a cloth-bound book.
     */
    fun stamp(c: Canvas, r: RectF, genre: CoverGenre, seed: Int, ink: Paint, weight: Float = 1f) {
        val p = Painter(c, r, Random(seed))
        val line = Paint(ink).apply { style = Paint.Style.STROKE; strokeWidth = p.u * 0.9f * weight; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val solid = Paint(ink).apply { style = Paint.Style.FILL }
        when (genre) {
            CoverGenre.FANTASY -> if (p.rnd.nextBoolean()) towerAndMoon(p, line, solid) else treeOfLife(p, line)
            CoverGenre.MAGIC_SCHOOL -> Stamps.owlOnBooks(p, line, solid)
            CoverGenre.DARK_MAGIC -> Stamps.ouroboros(p, line, solid)
            CoverGenre.WITCHES -> Stamps.cauldron(p, line, solid)
            CoverGenre.QUEST -> Stamps.swordInStone(p, line, solid)
            CoverGenre.STORM_EPIC -> Stamps.stormAndGem(p, line, solid)
            CoverGenre.MYTHOLOGY -> Stamps.trident(p, line, solid)
            CoverGenre.FAIRY_TALE -> Stamps.roseTower(p, line, solid)
            CoverGenre.ROMANTASY -> Stamps.thornCrown(p, line, solid)
            CoverGenre.URBAN_FANTASY -> Stamps.moonCity(p, line, solid)
            CoverGenre.WUXIA -> Stamps.craneAndPeaks(p, line, solid)
            CoverGenre.SAMURAI -> Stamps.gateAndSun(p, line, solid)
            CoverGenre.SCIENCE_FICTION -> orbits(p, line, solid)
            CoverGenre.SPACE_OPERA -> Stamps.fighterAndPlanet(p, line, solid)
            CoverGenre.STARSHIP -> Stamps.shipAndWarp(p, line, solid)
            CoverGenre.CYBERPUNK -> Stamps.circuitEye(p, line, solid)
            CoverGenre.DYSTOPIA -> Stamps.seedling(p, line, solid)
            CoverGenre.STEAMPUNK -> Stamps.gearsAndAirship(p, line, solid)
            CoverGenre.SUPERHERO -> Stamps.maskAndBurst(p, line, solid)
            CoverGenre.MYSTERY, CoverGenre.THRILLER -> keyAndVines(p, line, solid)
            CoverGenre.DETECTIVE -> Stamps.glassAndPipe(p, line, solid)
            CoverGenre.COSY_MYSTERY -> Stamps.teacupAndGlass(p, line, solid)
            CoverGenre.SPY -> Stamps.keyInSight(p, line, solid)
            CoverGenre.HISTORICAL -> laurel(p, line, solid)
            CoverGenre.WAR -> Stamps.poppyAndWire(p, line, solid)
            CoverGenre.KNIGHTS -> Stamps.shieldAndSwords(p, line, solid)
            CoverGenre.REGENCY -> Stamps.cameo(p, line, solid)
            CoverGenre.ANCIENT -> Stamps.pyramidAndSun(p, line, solid)
            CoverGenre.ADVENTURE -> compass(p, line, solid)
            CoverGenre.WESTERN -> sunOverMesas(p, line, solid)
            CoverGenre.ROMANCE -> roses(p, line, solid)
            CoverGenre.MODERN_ROMANCE -> Stamps.twoHearts(p, line, solid)
            CoverGenre.DARK_ROMANCE -> Stamps.roseAndDagger(p, line, solid)
            CoverGenre.FLUFF -> Stamps.cloudAndHeart(p, line, solid)
            CoverGenre.ANGST -> Stamps.brokenHeart(p, line, solid)
            CoverGenre.HORROR -> bareTreeAndMoon(p, line, solid)
            CoverGenre.VAMPIRES -> Stamps.batAndMoon(p, line, solid)
            CoverGenre.COSMIC_HORROR -> Stamps.eyeAndTentacles(p, line, solid)
            CoverGenre.HALLOWEEN -> Stamps.pumpkin(p, line, solid)
            CoverGenre.LITERARY -> quill(p, line)
            CoverGenre.SLICE_OF_LIFE -> Stamps.coffeeCup(p, line, solid)
            CoverGenre.SCHOOL -> Stamps.capAndBooks(p, line, solid)
            CoverGenre.SPORTS -> Stamps.trophy(p, line, solid)
            CoverGenre.MUSIC -> Stamps.microphone(p, line, solid)
            CoverGenre.CHRISTMAS -> Stamps.snowflakeWreath(p, line, solid)
            CoverGenre.NON_FICTION -> armillary(p, line, solid)
            CoverGenre.CHILDREN -> balloonAndStars(p, line, solid)
        }
    }

    /** A four-pointed sparkle. */
    private fun sparkle(p: Painter, cx: Float, cy: Float, size: Float, solid: Paint) {
        val path = Path().apply {
            moveTo(cx, cy - size)
            quadTo(cx, cy, cx + size, cy)
            quadTo(cx, cy, cx, cy + size)
            quadTo(cx, cy, cx - size, cy)
            quadTo(cx, cy, cx, cy - size)
            close()
        }
        p.c.drawPath(path, solid)
    }

    /** A curl of wind: a spiral winding out from ([cx], [cy]) into a long tail. */
    private fun curl(p: Painter, cx: Float, cy: Float, size: Float, line: Paint, flip: Boolean = false) {
        val path = Path()
        val dir = if (flip) -1f else 1f
        val steps = 60
        var lx = cx
        var ly = cy
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            // A turn and a quarter, so it ends at the bottom heading sideways, into the tail.
            val a = 2.5f * PI.toFloat() * t
            val rad = size * (0.1f + 0.9f * t)
            lx = cx + dir * cos(a) * rad
            ly = cy + sin(a) * rad * 0.8f
            if (i == 0) path.moveTo(lx, ly) else path.lineTo(lx, ly)
        }
        path.quadTo(lx - dir * size * 1.2f, ly, lx - dir * size * 2.6f, ly - size * 0.15f)
        p.c.drawPath(path, Paint(line).apply { strokeWidth = line.strokeWidth * 0.8f })
    }

    private fun towerAndMoon(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        // The crescent moon.
        val mx = p.x(0.68f)
        val my = p.y(0.2f)
        val mr = p.w * 0.12f
        val moon = Path().apply {
            addCircle(mx, my, mr, Path.Direction.CW)
            val cut = Path().apply { addCircle(mx + mr * 0.45f, my - mr * 0.15f, mr * 0.88f, Path.Direction.CW) }
            op(cut, Path.Op.DIFFERENCE)
        }
        p.c.drawPath(moon, solid)
        repeat(9) { sparkle(p, p.x(0.1f + p.rnd.nextFloat() * 0.8f), p.y(0.04f + p.rnd.nextFloat() * 0.4f), p.u * (1.2f + p.rnd.nextFloat() * 1.8f), solid) }
        // The tower on its rock.
        val base = p.y(0.92f)
        val top = p.y(0.38f)
        val tw = p.w * 0.13f
        val tower = Path().apply {
            moveTo(cx - tw * 0.6f, base - p.h * 0.08f)
            lineTo(cx - tw * 0.42f, top + p.h * 0.1f)
            lineTo(cx + tw * 0.42f, top + p.h * 0.1f)
            lineTo(cx + tw * 0.6f, base - p.h * 0.08f)
            // The roof.
            moveTo(cx - tw * 0.62f, top + p.h * 0.1f)
            lineTo(cx, top)
            lineTo(cx + tw * 0.62f, top + p.h * 0.1f)
            close()
        }
        p.c.drawPath(tower, line)
        // Windows and courses of stone.
        p.c.drawRoundRect(cx - tw * 0.08f, top + p.h * 0.16f, cx + tw * 0.08f, top + p.h * 0.23f, tw * 0.08f, tw * 0.08f, solid)
        p.c.drawRoundRect(cx - tw * 0.1f, top + p.h * 0.33f, cx + tw * 0.1f, top + p.h * 0.41f, tw * 0.1f, tw * 0.1f, line)
        for (k in 1..4) {
            val yy = top + p.h * (0.1f + 0.1f * k)
            p.c.drawLine(cx - tw * (0.43f + 0.03f * k), yy, cx + tw * (0.43f + 0.03f * k), yy, Paint(line).apply { strokeWidth = p.u * 0.4f })
        }
        val rock = Path().apply {
            moveTo(p.x(0.12f), base)
            lineTo(p.x(0.28f), base - p.h * 0.06f)
            lineTo(cx - tw * 0.7f, base - p.h * 0.09f)
            lineTo(cx + tw * 0.7f, base - p.h * 0.08f)
            lineTo(p.x(0.74f), base - p.h * 0.04f)
            lineTo(p.x(0.88f), base)
        }
        p.c.drawPath(rock, line)
        curl(p, p.x(0.22f), p.y(0.3f), p.w * 0.07f, line)
        curl(p, p.x(0.82f), p.y(0.5f), p.w * 0.06f, line, flip = true)
        curl(p, p.x(0.26f), p.y(0.62f), p.w * 0.05f, line)
    }

    private fun treeOfLife(p: Painter, line: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val rad = min(p.w, p.h) * 0.46f
        p.c.drawCircle(cx, cy, rad, line)
        p.c.drawCircle(cx, cy, rad * 0.94f, Paint(line).apply { strokeWidth = p.u * 0.35f })
        p.c.save()
        p.c.clipPath(Path().apply { addCircle(cx, cy, rad * 0.93f, Path.Direction.CW) })
        val ground = cy + rad * 0.2f
        fun grow(x0: Float, y0: Float, len: Float, ang: Float, d: Int, width: Float, down: Boolean) {
            val rr = Math.toRadians(ang.toDouble())
            val x1 = x0 + (cos(rr) * len).toFloat()
            val y1 = y0 + (sin(rr) * len).toFloat()
            p.c.drawLine(x0, y0, x1, y1, Paint(line).apply { strokeWidth = width })
            if (d == 0) {
                if (!down) p.c.drawCircle(x1, y1, p.u * 0.9f, Paint(line).apply { style = Paint.Style.FILL })
                return
            }
            val spread = if (down) 26f else 30f
            grow(x1, y1, len * 0.72f, ang - spread + (p.rnd.nextFloat() - 0.5f) * 10f, d - 1, width * 0.72f, down)
            grow(x1, y1, len * 0.72f, ang + spread + (p.rnd.nextFloat() - 0.5f) * 10f, d - 1, width * 0.72f, down)
        }
        grow(cx, ground, rad * 0.3f, -90f, 6, p.u * 2.4f, down = false)
        grow(cx, ground, rad * 0.12f, 90f, 5, p.u * 1.8f, down = true)
        p.c.drawLine(cx - rad, ground, cx + rad, ground, Paint(line).apply { strokeWidth = p.u * 0.5f })
        p.c.restore()
    }

    private fun orbits(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val rad = min(p.w, p.h) * 0.17f
        // Orbits first, tilted, then the planet and its rings over them.
        p.c.save()
        p.c.rotate(-18f, cx, cy)
        for (k in 0 until 3) {
            val ow = rad * (2.1f + k * 0.55f)
            val oh = ow * 0.38f
            p.c.drawOval(cx - ow, cy - oh, cx + ow, cy + oh, Paint(line).apply { strokeWidth = p.u * 0.35f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(p.u * 1.5f, p.u * 1.2f), 0f) })
            val a = p.rnd.nextFloat() * 2 * PI.toFloat()
            p.c.drawCircle(cx + cos(a) * ow, cy + sin(a) * oh, p.u * (1.3f + k * 0.5f), solid)
        }
        p.c.restore()
        p.c.drawCircle(cx, cy, rad, line)
        // Bands on the planet.
        p.c.save()
        p.c.clipPath(Path().apply { addCircle(cx, cy, rad, Path.Direction.CW) })
        for (k in -2..2) p.c.drawLine(cx - rad, cy + k * rad * 0.32f, cx + rad, cy + k * rad * 0.32f - rad * 0.12f, Paint(line).apply { strokeWidth = p.u * 0.35f })
        p.c.restore()
        p.c.save()
        p.c.rotate(-18f, cx, cy)
        p.c.drawOval(cx - rad * 1.75f, cy - rad * 0.38f, cx + rad * 1.75f, cy + rad * 0.38f, line)
        p.c.drawOval(cx - rad * 1.5f, cy - rad * 0.3f, cx + rad * 1.5f, cy + rad * 0.3f, Paint(line).apply { strokeWidth = p.u * 0.35f })
        p.c.restore()
        repeat(14) { sparkle(p, p.x(p.rnd.nextFloat()), p.y(p.rnd.nextFloat()), p.u * (0.8f + p.rnd.nextFloat() * 1.6f), solid) }
    }

    private fun keyAndVines(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        // A keyhole behind, as an arch.
        val kh = Path().apply {
            addCircle(cx, p.y(0.33f), p.w * 0.16f, Path.Direction.CW)
            moveTo(cx - p.w * 0.1f, p.y(0.42f))
            lineTo(cx - p.w * 0.18f, p.y(0.86f))
            lineTo(cx + p.w * 0.18f, p.y(0.86f))
            lineTo(cx + p.w * 0.1f, p.y(0.42f))
        }
        p.c.drawPath(kh, Paint(line).apply { strokeWidth = p.u * 0.4f })
        // The key, upright: an ornate bow, the shaft, the bit.
        val bowY = p.y(0.2f)
        val br = p.w * 0.07f
        p.c.drawCircle(cx, bowY, br, line)
        for (k in 0 until 3) {
            val a = Math.toRadians((k * 120 - 90).toDouble())
            p.c.drawCircle(cx + (cos(a) * br * 0.95f).toFloat(), bowY + (sin(a) * br * 0.95f).toFloat(), br * 0.45f, line)
        }
        p.c.drawCircle(cx, bowY, br * 0.3f, solid)
        p.c.drawLine(cx, bowY + br * 1.2f, cx, p.y(0.8f), Paint(line).apply { strokeWidth = p.u * 1.6f })
        p.c.drawRect(cx - p.w * 0.02f, p.y(0.3f), cx + p.w * 0.02f, p.y(0.32f), solid)
        val bit = Path().apply {
            moveTo(cx, p.y(0.68f))
            lineTo(cx + p.w * 0.09f, p.y(0.68f))
            lineTo(cx + p.w * 0.09f, p.y(0.72f))
            lineTo(cx + p.w * 0.05f, p.y(0.72f))
            lineTo(cx + p.w * 0.05f, p.y(0.75f))
            lineTo(cx + p.w * 0.09f, p.y(0.75f))
            lineTo(cx + p.w * 0.09f, p.y(0.8f))
            lineTo(cx, p.y(0.8f))
        }
        p.c.drawPath(bit, line)
        // Vines winding up either side.
        for (side in listOf(-1f, 1f)) {
            val vine = Path()
            val steps = 30
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val vy = p.y(0.92f - t * 0.82f)
                val vx = cx + side * (p.w * 0.3f + sin(t * 9f) * p.w * 0.05f)
                if (i == 0) vine.moveTo(vx, vy) else vine.lineTo(vx, vy)
                if (i % 5 == 2) {
                    val lx = vx + side * p.w * 0.05f
                    p.c.drawOval(min(vx, lx), vy - p.u * 1.2f, max(vx, lx), vy + p.u * 1.2f, line)
                }
            }
            p.c.drawPath(vine, line)
        }
    }

    private fun bareTreeAndMoon(p: Painter, line: Paint, solid: Paint) {
        val mx = p.x(0.66f)
        val my = p.y(0.24f)
        p.c.drawCircle(mx, my, p.w * 0.14f, line)
        p.c.drawCircle(mx, my, p.w * 0.12f, Paint(line).apply { strokeWidth = p.u * 0.35f })
        // A crooked tree in lines.
        val trunk = Paint(line)
        fun branch(x0: Float, y0: Float, len: Float, ang: Float, d: Int, width: Float) {
            val rr = Math.toRadians(ang.toDouble())
            val x1 = x0 + (cos(rr) * len).toFloat()
            val y1 = y0 + (sin(rr) * len).toFloat()
            p.c.drawPath(Path().apply { moveTo(x0, y0); quadTo((x0 + x1) / 2 + (p.rnd.nextFloat() - 0.5f) * len * 0.5f, (y0 + y1) / 2, x1, y1) }, Paint(trunk).apply { strokeWidth = width })
            if (d == 0) return
            branch(x1, y1, len * 0.7f, ang - 22f - p.rnd.nextFloat() * 18f, d - 1, width * 0.7f)
            branch(x1, y1, len * 0.7f, ang + 22f + p.rnd.nextFloat() * 18f, d - 1, width * 0.7f)
        }
        val bx = p.x(0.36f)
        branch(bx, p.y(0.92f), p.h * 0.22f, -95f, 6, p.u * 2.6f)
        p.c.drawLine(p.x(0.06f), p.y(0.92f), p.x(0.94f), p.y(0.92f), line)
        repeat(4) {
            val sx = p.x(0.55f + p.rnd.nextFloat() * 0.35f)
            val sy = p.y(0.45f + p.rnd.nextFloat() * 0.2f)
            val s = p.u * 2.4f
            p.c.drawPath(Path().apply {
                moveTo(sx - s * 1.5f, sy - s * 0.3f); quadTo(sx - s * 0.6f, sy - s * 0.9f, sx, sy)
                quadTo(sx + s * 0.6f, sy - s * 0.9f, sx + s * 1.5f, sy - s * 0.3f)
            }, line)
        }
    }

    private fun roses(p: Painter, line: Paint, solid: Paint) {
        fun rose(cx: Float, cy: Float, size: Float) {
            // Petals wrapping round from the middle.
            for (k in 0 until 4) {
                val rad = size * (0.25f + k * 0.22f)
                val start = k * 70f
                p.c.drawArc(RectF(cx - rad, cy - rad * 0.85f, cx + rad, cy + rad * 0.85f), start, 250f, false, line)
            }
            p.c.drawCircle(cx, cy, size * 0.08f, solid)
        }
        fun leaf(x0: Float, y0: Float, angle: Float, size: Float) {
            p.c.save()
            p.c.rotate(angle, x0, y0)
            val leaf = Path().apply {
                moveTo(x0, y0)
                quadTo(x0 + size * 0.5f, y0 - size * 0.32f, x0 + size, y0)
                quadTo(x0 + size * 0.5f, y0 + size * 0.32f, x0, y0)
            }
            p.c.drawPath(leaf, line)
            p.c.drawLine(x0, y0, x0 + size * 0.9f, y0, Paint(line).apply { strokeWidth = p.u * 0.35f })
            p.c.restore()
        }
        val size = p.w * 0.15f
        val a = floatArrayOf(p.x(0.36f), p.y(0.32f))
        val b = floatArrayOf(p.x(0.64f), p.y(0.36f))
        // Stems crossing under the flowers.
        p.c.drawPath(Path().apply { moveTo(a[0], a[1] + size); quadTo(p.x(0.45f), p.y(0.7f), p.x(0.62f), p.y(0.94f)) }, line)
        p.c.drawPath(Path().apply { moveTo(b[0], b[1] + size); quadTo(p.x(0.56f), p.y(0.7f), p.x(0.38f), p.y(0.94f)) }, line)
        leaf(p.x(0.43f), p.y(0.6f), 200f, size * 0.9f)
        leaf(p.x(0.57f), p.y(0.62f), -20f, size * 0.9f)
        leaf(p.x(0.47f), p.y(0.78f), 160f, size * 0.7f)
        leaf(p.x(0.53f), p.y(0.8f), 20f, size * 0.7f)
        rose(a[0], a[1], size)
        rose(b[0], b[1], size * 0.9f)
        // A small heart between them.
        val hx = p.x(0.5f)
        val hy = p.y(0.12f)
        val hs = p.u * 3f
        p.c.drawPath(Path().apply {
            moveTo(hx, hy + hs)
            cubicTo(hx - hs * 2f, hy - hs * 0.2f, hx - hs * 0.9f, hy - hs * 1.6f, hx, hy - hs * 0.4f)
            cubicTo(hx + hs * 0.9f, hy - hs * 1.6f, hx + hs * 2f, hy - hs * 0.2f, hx, hy + hs)
        }, solid)
    }

    private fun laurel(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val rad = min(p.w, p.h) * 0.4f
        for (side in listOf(-1f, 1f)) {
            val stem = Path()
            val n = 11
            for (i in 0..n) {
                val t = i / n.toFloat()
                val a = Math.toRadians((90 + side * (20 + t * 150)).toDouble())
                val px = cx + (cos(a) * rad).toFloat()
                val py = cy + (sin(a) * rad).toFloat()
                if (i == 0) stem.moveTo(px, py) else stem.lineTo(px, py)
                if (i in 1 until n) {
                    // A pair of leaves, pointing along the stem.
                    for (inOut in listOf(-1f, 1f)) {
                        p.c.save()
                        val along = Math.toDegrees(a).toFloat() + side * 90f
                        p.c.rotate(along + inOut * 35f * side, px, py)
                        val size = rad * 0.16f * (1f - t * 0.35f)
                        p.c.drawPath(Path().apply {
                            moveTo(px, py)
                            quadTo(px + size * 0.5f, py - size * 0.28f, px + size, py)
                            quadTo(px + size * 0.5f, py + size * 0.28f, px, py)
                        }, solid)
                        p.c.restore()
                    }
                }
            }
            p.c.drawPath(stem, line)
        }
        // A ribbon where the stems meet, and a star above.
        p.c.drawPath(Path().apply {
            moveTo(cx - rad * 0.2f, cy + rad * 1.02f); lineTo(cx, cy + rad * 0.9f); lineTo(cx + rad * 0.2f, cy + rad * 1.02f)
            moveTo(cx - rad * 0.12f, cy + rad * 0.92f); lineTo(cx - rad * 0.28f, cy + rad * 1.2f)
            moveTo(cx + rad * 0.12f, cy + rad * 0.92f); lineTo(cx + rad * 0.28f, cy + rad * 1.2f)
        }, line)
        val star = Path()
        for (k in 0 until 10) {
            val a = Math.toRadians((-90 + k * 36).toDouble())
            val rr = if (k % 2 == 0) rad * 0.2f else rad * 0.08f
            val px = cx + (cos(a) * rr).toFloat()
            val py = cy + (sin(a) * rr).toFloat()
            if (k == 0) star.moveTo(px, py) else star.lineTo(px, py)
        }
        star.close()
        p.c.drawPath(star, solid)
    }

    private fun compass(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.5f)
        val rad = min(p.w, p.h) * 0.42f
        p.c.drawCircle(cx, cy, rad, line)
        p.c.drawCircle(cx, cy, rad * 0.92f, Paint(line).apply { strokeWidth = p.u * 0.35f })
        for (k in 0 until 72) {
            val a = Math.toRadians(k * 5.0)
            val r2 = rad * (if (k % 9 == 0) 0.82f else 0.87f)
            p.c.drawLine(cx + (cos(a) * rad * 0.92f).toFloat(), cy + (sin(a) * rad * 0.92f).toFloat(), cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), Paint(line).apply { strokeWidth = p.u * 0.3f })
        }
        // The rose: long points to the four quarters, short ones between.
        for (k in 0 until 8) {
            val long = k % 2 == 0
            val a = Math.toRadians((k * 45 - 90).toDouble())
            val tip = rad * (if (long) 0.8f else 0.5f)
            val side = Math.toRadians((k * 45 - 90 + 90).toDouble())
            val half = rad * (if (long) 0.09f else 0.07f)
            val tx = cx + (cos(a) * tip).toFloat()
            val ty = cy + (sin(a) * tip).toFloat()
            val lx = cx + (cos(side) * half).toFloat()
            val ly = cy + (sin(side) * half).toFloat()
            val rx = cx - (cos(side) * half).toFloat()
            val ry = cy - (sin(side) * half).toFloat()
            p.c.drawPath(Path().apply { moveTo(tx, ty); lineTo(lx, ly); lineTo(cx, cy); close() }, solid)
            p.c.drawPath(Path().apply { moveTo(tx, ty); lineTo(rx, ry); lineTo(cx, cy); close() }, line)
        }
        p.c.drawCircle(cx, cy, rad * 0.06f, solid)
    }

    private fun sunOverMesas(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val horizon = p.y(0.62f)
        val sr = p.w * 0.17f
        p.c.drawArc(RectF(cx - sr, horizon - sr, cx + sr, horizon + sr), 180f, 180f, false, line)
        for (k in 0..12) {
            val a = Math.toRadians((180 + k * 15).toDouble())
            p.c.drawLine(cx + (cos(a) * sr * 1.2f).toFloat(), horizon + (sin(a) * sr * 1.2f).toFloat(), cx + (cos(a) * sr * (if (k % 2 == 0) 1.9f else 1.6f)).toFloat(), horizon + (sin(a) * sr * (if (k % 2 == 0) 1.9f else 1.6f)).toFloat(), line)
        }
        val mesas = Path()
        p.mesa(mesas, p.x(0.2f), horizon + p.h * 0.02f, p.w * 0.36f, p.h * 0.14f)
        p.mesa(mesas, p.x(0.8f), horizon + p.h * 0.02f, p.w * 0.36f, p.h * 0.18f)
        p.c.drawPath(mesas, line)
        p.c.drawLine(p.x(0.02f), horizon + p.h * 0.02f, p.x(0.98f), horizon + p.h * 0.02f, line)
        val cactus = Path()
        p.cactus(cactus, p.x(0.5f), p.y(0.95f), p.h * 0.24f)
        p.c.drawPath(cactus, line)
    }

    private fun quill(p: Painter, line: Paint) {
        val x0 = p.x(0.3f)
        val y0 = p.y(0.9f)
        val x1 = p.x(0.72f)
        val y1 = p.y(0.08f)
        val spine = Path().apply { moveTo(x0, y0); quadTo(p.x(0.42f), p.y(0.4f), x1, y1) }
        p.c.drawPath(spine, line)
        // The vanes: barbs either side of the spine.
        val pm = android.graphics.PathMeasure(spine, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val n = 34
        for (i in 4..n) {
            val t = i / n.toFloat()
            pm.getPosTan(pm.length * t, pos, tan)
            val nx = -tan[1]
            val ny = tan[0]
            val len = p.w * 0.13f * sin(PI.toFloat() * ((t - 0.1f) / 0.9f).coerceIn(0f, 1f))
            for (side in listOf(-1f, 1f)) {
                p.c.drawLine(pos[0], pos[1], pos[0] + side * nx * len - tan[0] * len * 0.35f, pos[1] + side * ny * len - tan[1] * len * 0.35f, Paint(line).apply { strokeWidth = p.u * 0.35f })
            }
        }
        // A flourish under the nib.
        p.c.drawPath(Path().apply {
            moveTo(p.x(0.12f), p.y(0.94f))
            cubicTo(p.x(0.3f), p.y(0.84f), p.x(0.5f), p.y(1.02f), p.x(0.68f), p.y(0.92f))
            cubicTo(p.x(0.78f), p.y(0.86f), p.x(0.88f), p.y(0.9f), p.x(0.9f), p.y(0.96f))
        }, line)
    }

    private fun armillary(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.45f)
        val rad = min(p.w, p.h) * 0.34f
        p.c.drawCircle(cx, cy, rad, line)
        for ((tilt, squash) in listOf(0f to 0.3f, 60f to 0.3f, -60f to 0.3f, 90f to 1f)) {
            p.c.save()
            p.c.rotate(tilt, cx, cy)
            p.c.drawOval(cx - rad, cy - rad * squash, cx + rad, cy + rad * squash, Paint(line).apply { strokeWidth = p.u * 0.5f })
            p.c.restore()
        }
        p.c.drawLine(cx - rad * 0.35f, cy + rad * 1.15f, cx + rad * 0.35f, cy - rad * 1.15f, line)
        p.c.drawCircle(cx, cy, rad * 0.12f, solid)
        // The stand.
        p.c.drawLine(cx, cy + rad, cx, p.y(0.9f), line)
        p.c.drawPath(Path().apply { moveTo(cx - rad * 0.5f, p.y(0.92f)); lineTo(cx, p.y(0.86f)); lineTo(cx + rad * 0.5f, p.y(0.92f)); close() }, line)
    }

    private fun balloonAndStars(p: Painter, line: Paint, solid: Paint) {
        val cx = p.x(0.5f)
        val cy = p.y(0.42f)
        val s = min(p.w, p.h) * 0.3f
        val env = Path().apply {
            moveTo(cx, cy + s * 0.62f)
            cubicTo(cx - s * 0.75f, cy + s * 0.2f, cx - s * 0.62f, cy - s * 0.62f, cx, cy - s * 0.62f)
            cubicTo(cx + s * 0.62f, cy - s * 0.62f, cx + s * 0.75f, cy + s * 0.2f, cx, cy + s * 0.62f)
        }
        p.c.drawPath(env, line)
        for (k in listOf(-0.32f, 0f, 0.32f)) {
            p.c.drawPath(Path().apply { moveTo(cx, cy - s * 0.62f); quadTo(cx + k * s * 1.4f, cy, cx, cy + s * 0.62f) }, Paint(line).apply { strokeWidth = p.u * 0.4f })
        }
        p.c.drawLine(cx - s * 0.1f, cy + s * 0.6f, cx - s * 0.1f, cy + s * 0.82f, line)
        p.c.drawLine(cx + s * 0.1f, cy + s * 0.6f, cx + s * 0.1f, cy + s * 0.82f, line)
        p.c.drawRoundRect(cx - s * 0.12f, cy + s * 0.82f, cx + s * 0.12f, cy + s * 0.98f, s * 0.03f, s * 0.03f, line)
        repeat(12) { sparkle(p, p.x(p.rnd.nextFloat()), p.y(p.rnd.nextFloat() * 0.9f), p.u * (1f + p.rnd.nextFloat() * 1.6f), solid) }
        // Clouds as scallops.
        for ((fx, fy) in listOf(0.18f to 0.78f, 0.8f to 0.72f)) {
            val path = Path()
            val bx = p.x(fx)
            val by = p.y(fy)
            val cw = p.w * 0.1f
            for (k in 0 until 3) path.addArc(RectF(bx - cw * 1.5f + k * cw, by - cw * 0.6f, bx - cw * 0.5f + k * cw, by + cw * 0.4f), 180f, 180f)
            path.moveTo(bx - cw * 1.6f, by)
            path.lineTo(bx + cw * 1.6f, by)
            p.c.drawPath(path, line)
        }
    }

    /** How far apart [a] and [b] are, from 0 (the same) to about 1. */
    fun distance(a: Int, b: Int): Float = (abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))) / 765f
}
