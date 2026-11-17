package dev.kobolibrary.app.ui.cover

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.kobolibrary.app.ui.cover.Motif.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Small pictures for covers: a key, a moon, a teacup. Each is a plain shape that reads at any size, to be
 * stamped in foil in a pattern, scattered over a cover, or set alone as an emblem. All drawn here.
 */
internal enum class Motif {
    STAR, SPARK, MOON, HEART, LEAF, FLOWER, KEY, FEATHER, DAGGER, SWORD, CROWN, ANCHOR, SUN, EYE, BIRD, FISH, SKULL, BAT, SNOWFLAKE,
    TEACUP, CAT, MAGNIFIER, ENVELOPE, NOTE, PLANET, ROCKET, BOLT, FLAME, SHIELD, TREE, MUSHROOM, BOOK, HOURGLASS, GEAR, HORSESHOE,
    BUTTERFLY, PAW, CANDLE, BOTTLE, COMPASS, GEM, SPADE, SERPENT, TOWER, CLOUD, DROP, HAT, GLASSES, BALL, TROPHY, PUMPKIN, GHOST,
    HOLLY, BELL, GIFT, MASK, LANTERN, ARROW, HELMET, COLUMN, LAUREL, PYRAMID, CACTUS, ATOM, SAUCER, ROBOT, PENCIL, APPLE, PLANE,
    UMBRELLA, RING, FLEUR,
}

internal object CoverMotifs {
    /** A motif in a box from -1 to 1: what's filled, what's drawn as a line, and how thick the line is. */
    private class Shape(val fill: Path, val line: Path, val lineWidth: Float)

    private val shapes = ConcurrentHashMap<Motif, Shape>()

    /** Draws [m] about ([cx], [cy]), [r] from its middle to its edge, in [paint]'s colour (or shader), turned by [turn] degrees. */
    fun draw(c: Canvas, m: Motif, cx: Float, cy: Float, r: Float, paint: Paint, turn: Float = 0f) {
        val sh = shapes.getOrPut(m) { build(m) }
        val matrix = Matrix().apply {
            setScale(r, r)
            postRotate(turn)
            postTranslate(cx, cy)
        }
        if (!sh.fill.isEmpty) {
            val f = Path()
            sh.fill.transform(matrix, f)
            c.drawPath(f, Paint(paint).apply { style = Paint.Style.FILL })
        }
        if (!sh.line.isEmpty) {
            val l = Path()
            sh.line.transform(matrix, l)
            c.drawPath(l, Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = r * sh.lineWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
    }

    /** The whole shape of [m] about ([cx], [cy]) as one outline (its lines thickened into shapes), to clip to or to work on. */
    fun outline(m: Motif, cx: Float, cy: Float, r: Float, turn: Float = 0f): Path {
        val sh = shapes.getOrPut(m) { build(m) }
        val matrix = Matrix().apply {
            setScale(r, r)
            postRotate(turn)
            postTranslate(cx, cy)
        }
        val out = Path()
        sh.fill.transform(matrix, out)
        if (!sh.line.isEmpty) {
            val l = Path()
            sh.line.transform(matrix, l)
            val thick = Path()
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = r * sh.lineWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }.getFillPath(l, thick)
            out.op(thick, Path.Op.UNION)
        }
        return out
    }

    /** The motifs that go with a genre, the most telling first. */
    fun forGenre(genre: CoverGenre): List<Motif> = when (genre) {
        CoverGenre.FANTASY -> listOf(SWORD, CROWN, SHIELD, TOWER, STAR, TREE)
        CoverGenre.MAGIC_SCHOOL -> listOf(BOOK, KEY, FEATHER, CANDLE, BOTTLE, STAR)
        CoverGenre.DARK_MAGIC -> listOf(SKULL, SERPENT, CANDLE, EYE, MOON, DAGGER)
        CoverGenre.WITCHES -> listOf(MOON, BOTTLE, CAT, MUSHROOM, STAR, CANDLE)
        CoverGenre.QUEST -> listOf(COMPASS, SWORD, KEY, TREE, ARROW, SHIELD)
        CoverGenre.STORM_EPIC -> listOf(BOLT, CLOUD, SWORD, DROP, CROWN, SHIELD)
        CoverGenre.MYTHOLOGY -> listOf(LAUREL, COLUMN, BOLT, SUN, SERPENT, EYE)
        CoverGenre.FAIRY_TALE -> listOf(CROWN, KEY, MUSHROOM, BUTTERFLY, FLOWER, STAR)
        CoverGenre.ROMANTASY -> listOf(CROWN, DAGGER, MOON, FLOWER, SERPENT, STAR)
        CoverGenre.URBAN_FANTASY -> listOf(MOON, KEY, EYE, BOLT, CAT, DAGGER)
        CoverGenre.WUXIA -> listOf(SWORD, FLOWER, CLOUD, MOON, LANTERN, LEAF)
        CoverGenre.SCIENCE_FICTION -> listOf(PLANET, ROCKET, ATOM, STAR, ROBOT, SAUCER)
        CoverGenre.SPACE_OPERA -> listOf(PLANET, ROCKET, STAR, SPARK, SAUCER, MOON)
        CoverGenre.STARSHIP -> listOf(ROCKET, PLANET, STAR, COMPASS, ATOM, SPARK)
        CoverGenre.CYBERPUNK -> listOf(EYE, BOLT, ROBOT, GEAR, ATOM, KEY)
        CoverGenre.DYSTOPIA -> listOf(EYE, GEAR, FLAME, TOWER, BOLT, KEY)
        CoverGenre.STEAMPUNK -> listOf(GEAR, KEY, HOURGLASS, COMPASS, HAT, GLASSES)
        CoverGenre.SUPERHERO -> listOf(BOLT, STAR, SHIELD, MASK, FLAME, ROCKET)
        CoverGenre.MYSTERY -> listOf(KEY, MAGNIFIER, EYE, HOURGLASS, CANDLE, FEATHER)
        CoverGenre.DETECTIVE -> listOf(MAGNIFIER, HAT, KEY, GLASSES, PAW, ENVELOPE)
        CoverGenre.COSY_MYSTERY -> listOf(TEACUP, CAT, MAGNIFIER, KEY, BOOK, PAW)
        CoverGenre.THRILLER -> listOf(EYE, DAGGER, KEY, HOURGLASS, BOLT, DROP)
        CoverGenre.SPY -> listOf(EYE, KEY, MASK, GLASSES, HAT, SPADE)
        CoverGenre.HISTORICAL -> listOf(CROWN, FEATHER, KEY, COLUMN, FLEUR, ANCHOR)
        CoverGenre.WAR -> listOf(STAR, SHIELD, SWORD, FLAME, ANCHOR, ARROW)
        CoverGenre.KNIGHTS -> listOf(SHIELD, SWORD, HELMET, FLEUR, CROWN, TOWER)
        CoverGenre.REGENCY -> listOf(FEATHER, FLOWER, RING, ENVELOPE, BUTTERFLY, TEACUP)
        CoverGenre.ANCIENT -> listOf(COLUMN, LAUREL, SUN, PYRAMID, EYE, SERPENT)
        CoverGenre.ADVENTURE -> listOf(COMPASS, ANCHOR, ARROW, SUN, FISH, TREE)
        CoverGenre.WESTERN -> listOf(HORSESHOE, CACTUS, STAR, HAT, SUN, ARROW)
        CoverGenre.ROMANCE -> listOf(HEART, FLOWER, ENVELOPE, RING, BUTTERFLY, FEATHER)
        CoverGenre.MODERN_ROMANCE -> listOf(HEART, TEACUP, PLANE, UMBRELLA, STAR, NOTE)
        CoverGenre.DARK_ROMANCE -> listOf(DAGGER, FLOWER, SERPENT, HEART, MASK, MOON)
        CoverGenre.FLUFF -> listOf(HEART, STAR, CLOUD, CAT, FLOWER, PAW)
        CoverGenre.ANGST -> listOf(DROP, CLOUD, MOON, HEART, UMBRELLA, BIRD)
        CoverGenre.HORROR -> listOf(SKULL, BAT, EYE, CANDLE, DROP, MOON)
        CoverGenre.VAMPIRES -> listOf(BAT, DROP, MOON, FLOWER, DAGGER, SKULL)
        CoverGenre.COSMIC_HORROR -> listOf(EYE, STAR, SERPENT, MOON, PLANET, SKULL)
        CoverGenre.HALLOWEEN -> listOf(PUMPKIN, GHOST, BAT, MOON, CANDLE, CAT)
        CoverGenre.LITERARY -> listOf(LEAF, BIRD, FEATHER, BOOK, KEY, FLOWER)
        CoverGenre.SLICE_OF_LIFE -> listOf(TEACUP, LEAF, CLOUD, UMBRELLA, CAT, SUN)
        CoverGenre.SCHOOL -> listOf(PENCIL, APPLE, BOOK, STAR, PLANE, GLASSES)
        CoverGenre.SPORTS -> listOf(BALL, TROPHY, STAR, BOLT, SHIELD, FLAME)
        CoverGenre.MUSIC -> listOf(NOTE, STAR, HEART, BOLT, SPARK, MOON)
        CoverGenre.CHRISTMAS -> listOf(SNOWFLAKE, HOLLY, BELL, GIFT, STAR, TREE)
        CoverGenre.NON_FICTION -> listOf(BOOK, ATOM, COMPASS, LEAF, GEAR, EYE)
        CoverGenre.CHILDREN -> listOf(STAR, CLOUD, SUN, CAT, ROCKET, BUTTERFLY)
    }

    /** A star of [points] about ([cx], [cy]): [outer] to its tips, [inner] to the notches between. */
    fun star(path: Path, cx: Float, cy: Float, outer: Float, inner: Float, points: Int, turn: Float = -90f) {
        for (k in 0 until points * 2) {
            val a = Math.toRadians((turn + k * 180.0 / points))
            val r = if (k % 2 == 0) outer else inner
            val x = cx + (cos(a) * r).toFloat()
            val y = cy + (sin(a) * r).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** A leaf (a lens) from ([x], [y]), [length] long, pointing at [angle] degrees. */
    fun leaf(path: Path, x: Float, y: Float, length: Float, angle: Float, fat: Float = 0.3f) {
        val a = Math.toRadians(angle.toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val tx = x + dx * length
        val ty = y + dy * length
        val mx = x + dx * length / 2
        val my = y + dy * length / 2
        val w = length * fat
        path.moveTo(x, y)
        path.quadTo(mx - dy * w, my + dx * w, tx, ty)
        path.quadTo(mx + dy * w, my - dx * w, x, y)
        path.close()
    }

    /** A scroll: a line from ([x], [y]) curling in on itself, [size] across, turning to the left or ([clockwise]) the right. */
    fun scroll(path: Path, x: Float, y: Float, size: Float, angle: Float, clockwise: Boolean, turns: Float = 1.6f) {
        val steps = 28
        val sign = if (clockwise) 1 else -1
        var px = x
        var py = y
        var a = Math.toRadians(angle.toDouble())
        path.moveTo(px, py)
        for (i in 1..steps) {
            val f = i / steps.toFloat()
            // Shorter steps and tighter turns as it winds in.
            val step = size * 0.16f * (1f - f * 0.82f)
            a += sign * turns * 2 * Math.PI / steps * (0.45 + f * 1.3)
            px += (cos(a) * step).toFloat()
            py += (sin(a) * step).toFloat()
            path.lineTo(px, py)
        }
    }

    /** A wreath of leaves round ([cx], [cy]), open at the top by [gap] degrees. */
    fun wreath(path: Path, cx: Float, cy: Float, r: Float, leaves: Int = 22, gap: Float = 36f, leafLength: Float = r * 0.3f) {
        for (side in intArrayOf(-1, 1)) for (k in 0 until leaves) {
            val along = k / (leaves - 1f)
            // From the foot up each side.
            val deg = 90f + side * (8f + along * (172f - gap / 2 - 8f))
            val a = Math.toRadians(deg.toDouble())
            val x = cx + (cos(a) * r).toFloat()
            val y = cy + (sin(a) * r).toFloat()
            val tangent = deg + side * 90f
            leaf(path, x, y, leafLength * (1f - along * 0.25f), tangent - side * 32f, 0.34f)
            leaf(path, x, y, leafLength * 0.85f * (1f - along * 0.25f), tangent + side * 38f, 0.34f)
        }
    }

    private fun build(m: Motif): Shape {
        val f = Path()
        val l = Path()
        var lw = 0.13f
        fun circle(x: Float, y: Float, r: Float) = f.addCircle(x, y, r, Path.Direction.CW)
        fun oval(left: Float, top: Float, right: Float, bottom: Float) = f.addOval(RectF(left, top, right, bottom), Path.Direction.CW)
        fun rect(left: Float, top: Float, right: Float, bottom: Float) = f.addRect(left, top, right, bottom, Path.Direction.CW)
        fun round(left: Float, top: Float, right: Float, bottom: Float, r: Float) = f.addRoundRect(RectF(left, top, right, bottom), r, r, Path.Direction.CW)
        fun poly(vararg p: Float) {
            f.moveTo(p[0], p[1])
            for (i in 2 until p.size step 2) f.lineTo(p[i], p[i + 1])
            f.close()
        }
        fun seg(x0: Float, y0: Float, x1: Float, y1: Float) {
            l.moveTo(x0, y0)
            l.lineTo(x1, y1)
        }
        /** Takes [shape] out of what's filled. */
        fun minus(shape: Path) {
            f.op(shape, Path.Op.DIFFERENCE)
        }
        fun hole(x: Float, y: Float, r: Float) = minus(Path().apply { addCircle(x, y, r, Path.Direction.CW) })
        fun holeRect(left: Float, top: Float, right: Float, bottom: Float) = minus(Path().apply { addRect(left, top, right, bottom, Path.Direction.CW) })
        /** Cuts a line [width] wide out of what's filled (a vein, a seam). */
        fun cut(width: Float, draw: Path.() -> Unit) {
            val src = Path().apply(draw)
            val dst = Path()
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
            }.getFillPath(src, dst)
            minus(dst)
        }
        /** Adds what's filled so far again, mirrored left to right. */
        fun mirror() {
            val other = Path()
            f.transform(Matrix().apply { setScale(-1f, 1f) }, other)
            f.op(other, Path.Op.UNION)
        }
        fun turned(deg: Float, of: Path) = Path().also { of.transform(Matrix().apply { setRotate(deg) }, it) }

        when (m) {
            Motif.STAR -> star(f, 0f, 0.05f, 1f, 0.42f, 5)
            Motif.SPARK -> star(f, 0f, 0f, 1f, 0.24f, 4)
            Motif.MOON -> {
                circle(0f, 0f, 0.95f)
                hole(0.42f, -0.16f, 0.8f)
            }
            Motif.HEART -> {
                f.moveTo(0f, 0.92f)
                f.cubicTo(-1.35f, -0.05f, -0.75f, -1.05f, 0f, -0.4f)
                f.cubicTo(0.75f, -1.05f, 1.35f, -0.05f, 0f, 0.92f)
                f.close()
            }
            Motif.LEAF -> {
                leaf(f, 0f, 0.72f, 1.7f, -90f, 0.42f)
                cut(0.07f) { moveTo(0f, 0.6f); lineTo(0f, -0.55f) }
                seg(0f, 0.7f, 0f, 1f)
                lw = 0.1f
            }
            Motif.FLOWER -> {
                for (k in 0 until 5) {
                    val a = Math.toRadians(k * 72.0 - 90.0)
                    circle((cos(a) * 0.54).toFloat(), (sin(a) * 0.54).toFloat(), 0.42f)
                }
                hole(0f, 0f, 0.2f)
            }
            Motif.KEY -> {
                circle(0f, -0.55f, 0.42f)
                hole(0f, -0.55f, 0.2f)
                rect(-0.09f, -0.2f, 0.09f, 1f)
                rect(0.09f, 0.6f, 0.42f, 0.74f)
                rect(0.09f, 0.84f, 0.32f, 0.98f)
            }
            Motif.FEATHER -> {
                f.moveTo(0.18f, -1f)
                f.cubicTo(0.85f, -0.45f, 0.55f, 0.35f, -0.12f, 0.6f)
                f.cubicTo(-0.55f, 0.15f, -0.4f, -0.6f, 0.18f, -1f)
                f.close()
                cut(0.06f) { moveTo(0.16f, -0.8f); quadTo(0.05f, 0f, -0.14f, 0.55f) }
                cut(0.05f) { moveTo(0.42f, 0.02f); lineTo(0.14f, 0.2f) }
                l.moveTo(-0.12f, 0.55f)
                l.quadTo(-0.25f, 0.8f, -0.42f, 1f)
                lw = 0.08f
            }
            Motif.DAGGER -> {
                poly(0f, 1f, -0.17f, -0.2f, 0.17f, -0.2f)
                round(-0.5f, -0.33f, 0.5f, -0.2f, 0.06f)
                rect(-0.08f, -0.8f, 0.08f, -0.33f)
                circle(0f, -0.86f, 0.14f)
            }
            Motif.SWORD -> {
                poly(0f, 1f, -0.1f, 0.8f, -0.1f, -0.42f, 0.1f, -0.42f, 0.1f, 0.8f)
                round(-0.42f, -0.52f, 0.42f, -0.42f, 0.05f)
                rect(-0.055f, -0.86f, 0.055f, -0.52f)
                circle(0f, -0.9f, 0.1f)
            }
            Motif.CROWN -> {
                poly(-0.9f, 0.5f, -0.9f, -0.35f, -0.45f, 0.08f, 0f, -0.6f, 0.45f, 0.08f, 0.9f, -0.35f, 0.9f, 0.5f)
                circle(-0.9f, -0.47f, 0.12f)
                circle(0f, -0.74f, 0.14f)
                circle(0.9f, -0.47f, 0.12f)
                round(-0.9f, 0.62f, 0.9f, 0.8f, 0.05f)
            }
            Motif.ANCHOR -> {
                lw = 0.15f
                l.addCircle(0f, -0.72f, 0.2f, Path.Direction.CW)
                seg(0f, -0.52f, 0f, 0.88f)
                seg(-0.38f, -0.25f, 0.38f, -0.25f)
                l.addArc(RectF(-0.78f, -0.4f, 0.78f, 0.88f), 15f, 150f)
                poly(-0.98f, 0.32f, -0.6f, 0.32f, -0.86f, 0.62f)
                poly(0.98f, 0.32f, 0.6f, 0.32f, 0.86f, 0.62f)
            }
            Motif.SUN -> {
                circle(0f, 0f, 0.42f)
                lw = 0.11f
                for (k in 0 until 12) {
                    val a = Math.toRadians(k * 30.0)
                    val far = if (k % 2 == 0) 0.98f else 0.8f
                    seg((cos(a) * 0.6).toFloat(), (sin(a) * 0.6).toFloat(), (cos(a) * far).toFloat(), (sin(a) * far).toFloat())
                }
            }
            Motif.EYE -> {
                lw = 0.11f
                l.moveTo(-1f, 0f)
                l.quadTo(0f, -0.9f, 1f, 0f)
                l.quadTo(0f, 0.9f, -1f, 0f)
                l.close()
                circle(0f, 0f, 0.36f)
                hole(0.1f, -0.1f, 0.11f)
            }
            Motif.BIRD -> {
                lw = 0.14f
                l.moveTo(-1f, -0.05f)
                l.quadTo(-0.5f, -0.6f, 0f, 0.15f)
                l.quadTo(0.5f, -0.6f, 1f, -0.05f)
            }
            Motif.FISH -> {
                f.moveTo(-0.95f, 0f)
                f.quadTo(-0.2f, -0.75f, 0.5f, 0f)
                f.quadTo(-0.2f, 0.75f, -0.95f, 0f)
                f.close()
                poly(0.38f, 0f, 0.98f, -0.48f, 0.98f, 0.48f)
                hole(-0.5f, -0.08f, 0.08f)
            }
            Motif.SKULL -> {
                circle(0f, -0.2f, 0.72f)
                round(-0.42f, 0.2f, 0.42f, 0.88f, 0.1f)
                hole(-0.29f, -0.18f, 0.2f)
                hole(0.29f, -0.18f, 0.2f)
                minus(Path().apply { moveTo(0f, 0.08f); lineTo(-0.1f, 0.32f); lineTo(0.1f, 0.32f); close() })
                holeRect(-0.16f, 0.6f, -0.11f, 0.9f)
                holeRect(0.11f, 0.6f, 0.16f, 0.9f)
                holeRect(-0.025f, 0.6f, 0.025f, 0.9f)
            }
            Motif.BAT -> {
                f.moveTo(0f, -0.2f)
                f.lineTo(0.13f, -0.5f)
                f.lineTo(0.22f, -0.2f)
                f.quadTo(0.6f, -0.62f, 1f, -0.3f)
                f.quadTo(0.76f, -0.08f, 0.82f, 0.28f)
                f.quadTo(0.6f, 0.04f, 0.46f, 0.28f)
                f.quadTo(0.3f, 0.06f, 0.16f, 0.32f)
                f.lineTo(0f, 0.58f)
                f.close()
                mirror()
            }
            Motif.SNOWFLAKE -> {
                lw = 0.1f
                for (k in 0 until 6) {
                    val deg = k * 60.0
                    val a = Math.toRadians(deg)
                    val cx = cos(a).toFloat()
                    val sy = sin(a).toFloat()
                    seg(0f, 0f, cx * 0.95f, sy * 0.95f)
                    for (side in intArrayOf(-1, 1)) for (at in floatArrayOf(0.45f, 0.7f)) {
                        val b = Math.toRadians(deg + side * 50.0)
                        seg(cx * at, sy * at, cx * at + (cos(b) * 0.24).toFloat(), sy * at + (sin(b) * 0.24).toFloat())
                    }
                }
            }
            Motif.TEACUP -> {
                f.moveTo(-0.72f, -0.22f)
                f.lineTo(0.5f, -0.22f)
                f.cubicTo(0.5f, 0.35f, 0.2f, 0.56f, -0.11f, 0.56f)
                f.cubicTo(-0.42f, 0.56f, -0.72f, 0.35f, -0.72f, -0.22f)
                f.close()
                round(-0.92f, 0.68f, 0.7f, 0.8f, 0.06f)
                lw = 0.11f
                l.addArc(RectF(0.32f, -0.14f, 0.9f, 0.34f), -75f, 165f)
                l.moveTo(-0.32f, -0.42f)
                l.quadTo(-0.48f, -0.62f, -0.32f, -0.8f)
                l.moveTo(0.06f, -0.42f)
                l.quadTo(-0.1f, -0.62f, 0.06f, -0.8f)
            }
            Motif.CAT -> {
                oval(-0.52f, -0.1f, 0.4f, 0.95f)
                circle(-0.06f, -0.42f, 0.36f)
                poly(-0.4f, -0.52f, -0.36f, -0.97f, -0.1f, -0.72f)
                poly(0.28f, -0.52f, 0.24f, -0.97f, -0.02f, -0.72f)
                lw = 0.15f
                l.moveTo(0.3f, 0.82f)
                l.cubicTo(0.92f, 0.82f, 0.98f, 0.2f, 0.66f, 0.04f)
            }
            Motif.MAGNIFIER -> {
                lw = 0.16f
                l.addCircle(-0.2f, -0.2f, 0.55f, Path.Direction.CW)
                seg(0.24f, 0.24f, 0.9f, 0.9f)
            }
            Motif.ENVELOPE -> {
                lw = 0.1f
                l.addRect(-0.92f, -0.58f, 0.92f, 0.58f, Path.Direction.CW)
                l.moveTo(-0.92f, -0.58f)
                l.lineTo(0f, 0.12f)
                l.lineTo(0.92f, -0.58f)
            }
            Motif.NOTE -> {
                f.addPath(turned(-18f, Path().apply { addOval(RectF(-0.28f, -0.2f, 0.28f, 0.2f), Path.Direction.CW) }), -0.38f, 0.62f)
                f.addPath(turned(-18f, Path().apply { addOval(RectF(-0.28f, -0.2f, 0.28f, 0.2f), Path.Direction.CW) }), 0.52f, 0.44f)
                rect(-0.2f, -0.62f, -0.1f, 0.62f)
                rect(0.7f, -0.8f, 0.8f, 0.44f)
                poly(-0.2f, -0.72f, 0.8f, -0.92f, 0.8f, -0.6f, -0.2f, -0.4f)
            }
            Motif.PLANET -> {
                circle(0f, 0f, 0.55f)
                lw = 0.09f
                l.addPath(turned(-20f, Path().apply { addOval(RectF(-1f, -0.26f, 1f, 0.26f), Path.Direction.CW) }))
            }
            Motif.ROCKET -> {
                f.moveTo(0f, -1f)
                f.quadTo(0.52f, -0.4f, 0.36f, 0.45f)
                f.lineTo(-0.36f, 0.45f)
                f.quadTo(-0.52f, -0.4f, 0f, -1f)
                f.close()
                poly(-0.36f, 0.05f, -0.78f, 0.72f, -0.36f, 0.45f)
                poly(0.36f, 0.05f, 0.78f, 0.72f, 0.36f, 0.45f)
                hole(0f, -0.3f, 0.16f)
                poly(-0.18f, 0.56f, 0f, 1f, 0.18f, 0.56f)
            }
            Motif.BOLT -> poly(0.25f, -1f, -0.52f, 0.1f, -0.06f, 0.1f, -0.3f, 1f, 0.56f, -0.26f, 0.1f, -0.26f)
            Motif.FLAME -> {
                f.moveTo(0.05f, -1f)
                f.cubicTo(0.2f, -0.5f, 0.78f, -0.2f, 0.62f, 0.4f)
                f.cubicTo(0.5f, 0.86f, 0.1f, 1f, 0f, 1f)
                f.cubicTo(-0.1f, 1f, -0.52f, 0.86f, -0.62f, 0.4f)
                f.cubicTo(-0.72f, 0f, -0.3f, -0.2f, -0.26f, -0.5f)
                f.cubicTo(-0.1f, -0.35f, 0.02f, -0.6f, 0.05f, -1f)
                f.close()
                minus(Path().apply {
                    moveTo(0f, 0.2f)
                    cubicTo(0.3f, 0.5f, 0.2f, 0.85f, 0f, 0.85f)
                    cubicTo(-0.2f, 0.85f, -0.3f, 0.5f, 0f, 0.2f)
                    close()
                })
            }
            Motif.SHIELD -> {
                f.moveTo(-0.76f, -0.88f)
                f.lineTo(0.76f, -0.88f)
                f.lineTo(0.76f, 0f)
                f.cubicTo(0.76f, 0.5f, 0.36f, 0.86f, 0f, 1f)
                f.cubicTo(-0.36f, 0.86f, -0.76f, 0.5f, -0.76f, 0f)
                f.close()
                cut(0.07f) { moveTo(0f, -0.7f); lineTo(0f, 0.8f) }
                cut(0.07f) { moveTo(-0.6f, -0.2f); lineTo(0.6f, -0.2f) }
            }
            Motif.TREE -> {
                poly(0f, -1f, -0.5f, -0.3f, 0.5f, -0.3f)
                poly(0f, -0.62f, -0.66f, 0.2f, 0.66f, 0.2f)
                poly(0f, -0.18f, -0.82f, 0.7f, 0.82f, 0.7f)
                rect(-0.1f, 0.7f, 0.1f, 1f)
            }
            Motif.MUSHROOM -> {
                f.moveTo(-0.92f, 0f)
                f.cubicTo(-0.92f, -1.15f, 0.92f, -1.15f, 0.92f, 0f)
                f.close()
                round(-0.26f, 0f, 0.26f, 0.92f, 0.16f)
                hole(-0.36f, -0.42f, 0.15f)
                hole(0.3f, -0.5f, 0.12f)
                hole(0.04f, -0.2f, 0.08f)
            }
            Motif.BOOK -> {
                f.moveTo(0.04f, -0.45f)
                f.quadTo(0.5f, -0.78f, 0.96f, -0.5f)
                f.lineTo(0.96f, 0.6f)
                f.quadTo(0.5f, 0.34f, 0.04f, 0.66f)
                f.close()
                mirror()
            }
            Motif.HOURGLASS -> {
                round(-0.62f, -1f, 0.62f, -0.84f, 0.05f)
                round(-0.62f, 0.84f, 0.62f, 1f, 0.05f)
                poly(-0.5f, -0.84f, 0.5f, -0.84f, 0.07f, 0f, 0.5f, 0.84f, -0.5f, 0.84f, -0.07f, 0f)
                minus(Path().apply { moveTo(-0.32f, -0.7f); lineTo(0.32f, -0.7f); lineTo(0f, -0.2f); close() })
            }
            Motif.GEAR -> {
                val tooth = Path().apply { addRect(-0.17f, -1f, 0.17f, -0.6f, Path.Direction.CW) }
                for (k in 0 until 8) f.op(turned(k * 45f, tooth), Path.Op.UNION)
                f.op(Path().apply { addCircle(0f, 0f, 0.74f, Path.Direction.CW) }, Path.Op.UNION)
                hole(0f, 0f, 0.3f)
            }
            Motif.HORSESHOE -> {
                lw = 0.26f
                l.addArc(RectF(-0.66f, -0.86f, 0.66f, 0.46f), 115f, 310f)
                l.moveTo(-0.28f, 0.4f)
                l.lineTo(-0.3f, 0.82f)
                l.moveTo(0.28f, 0.4f)
                l.lineTo(0.3f, 0.82f)
            }
            Motif.BUTTERFLY -> {
                f.addPath(turned(-20f, Path().apply { addOval(RectF(0.06f, -0.9f, 0.9f, 0f), Path.Direction.CW) }))
                oval(0.08f, -0.02f, 0.68f, 0.72f)
                mirror()
                oval(-0.08f, -0.6f, 0.08f, 0.62f)
                lw = 0.05f
                l.moveTo(0f, -0.55f)
                l.quadTo(-0.12f, -0.85f, -0.3f, -0.95f)
                l.moveTo(0f, -0.55f)
                l.quadTo(0.12f, -0.85f, 0.3f, -0.95f)
            }
            Motif.PAW -> {
                oval(-0.46f, -0.05f, 0.46f, 0.78f)
                circle(-0.62f, -0.24f, 0.2f)
                circle(-0.23f, -0.6f, 0.21f)
                circle(0.23f, -0.6f, 0.21f)
                circle(0.62f, -0.24f, 0.2f)
            }
            Motif.CANDLE -> {
                rect(-0.22f, -0.22f, 0.22f, 0.8f)
                round(-0.56f, 0.8f, 0.56f, 0.96f, 0.06f)
                f.moveTo(0f, -1f)
                f.quadTo(0.32f, -0.55f, 0f, -0.36f)
                f.quadTo(-0.32f, -0.55f, 0f, -1f)
                f.close()
            }
            Motif.BOTTLE -> {
                circle(0f, 0.36f, 0.6f)
                rect(-0.16f, -0.6f, 0.16f, -0.1f)
                round(-0.27f, -0.72f, 0.27f, -0.58f, 0.04f)
                rect(-0.13f, -0.94f, 0.13f, -0.72f)
                hole(0.2f, 0.2f, 0.1f)
                hole(-0.16f, 0.5f, 0.07f)
            }
            Motif.COMPASS -> {
                lw = 0.08f
                l.addCircle(0f, 0f, 0.93f, Path.Direction.CW)
                poly(0f, -0.82f, 0.16f, -0.16f, 0.82f, 0f, 0.16f, 0.16f, 0f, 0.82f, -0.16f, 0.16f, -0.82f, 0f, -0.16f, -0.16f)
                hole(0f, 0f, 0.08f)
            }
            Motif.GEM -> {
                poly(-0.92f, -0.25f, -0.5f, -0.75f, 0.5f, -0.75f, 0.92f, -0.25f, 0f, 0.92f)
                cut(0.06f) { moveTo(-0.92f, -0.25f); lineTo(0.92f, -0.25f) }
                cut(0.06f) { moveTo(-0.25f, -0.75f); lineTo(-0.4f, -0.25f); lineTo(0f, 0.92f); lineTo(0.4f, -0.25f); lineTo(0.25f, -0.75f) }
            }
            Motif.SPADE -> {
                f.moveTo(0f, -0.96f)
                f.cubicTo(0.3f, -0.5f, 0.96f, -0.2f, 0.96f, 0.25f)
                f.cubicTo(0.96f, 0.76f, 0.3f, 0.8f, 0.08f, 0.4f)
                f.lineTo(0.3f, 0.96f)
                f.lineTo(-0.3f, 0.96f)
                f.lineTo(-0.08f, 0.4f)
                f.cubicTo(-0.3f, 0.8f, -0.96f, 0.76f, -0.96f, 0.25f)
                f.cubicTo(-0.96f, -0.2f, -0.3f, -0.5f, 0f, -0.96f)
                f.close()
            }
            Motif.SERPENT -> {
                lw = 0.19f
                l.moveTo(0.2f, -0.74f)
                l.cubicTo(-0.95f, -0.8f, -0.95f, -0.02f, 0f, 0f)
                l.cubicTo(0.95f, 0.02f, 0.95f, 0.8f, 0f, 0.8f)
                l.lineTo(-0.55f, 0.72f)
                f.addPath(turned(8f, Path().apply { addOval(RectF(0.1f, -0.94f, 0.66f, -0.58f), Path.Direction.CW) }))
            }
            Motif.TOWER -> {
                rect(-0.45f, -0.5f, 0.45f, 0.6f)
                round(-0.66f, 0.6f, 0.66f, 0.96f, 0.05f)
                rect(-0.62f, -0.62f, 0.62f, -0.45f)
                rect(-0.62f, -0.96f, -0.32f, -0.6f)
                rect(-0.15f, -0.96f, 0.15f, -0.6f)
                rect(0.32f, -0.96f, 0.62f, -0.6f)
                minus(Path().apply { addRoundRect(RectF(-0.13f, 0.05f, 0.13f, 0.6f), 0.13f, 0.13f, Path.Direction.CW) })
            }
            Motif.CLOUD -> {
                circle(-0.46f, 0.16f, 0.4f)
                circle(0f, -0.14f, 0.5f)
                circle(0.5f, 0.16f, 0.38f)
                rect(-0.46f, 0.16f, 0.5f, 0.56f)
            }
            Motif.DROP -> {
                f.moveTo(0f, -1f)
                f.cubicTo(0.2f, -0.4f, 0.7f, 0f, 0.7f, 0.35f)
                f.cubicTo(0.7f, 0.76f, 0.36f, 1f, 0f, 1f)
                f.cubicTo(-0.36f, 1f, -0.7f, 0.76f, -0.7f, 0.35f)
                f.cubicTo(-0.7f, 0f, -0.2f, -0.4f, 0f, -1f)
                f.close()
            }
            Motif.HAT -> {
                f.moveTo(-0.52f, 0.25f)
                f.lineTo(-0.44f, -0.55f)
                f.quadTo(0f, -0.86f, 0.44f, -0.55f)
                f.lineTo(0.52f, 0.25f)
                f.close()
                holeRect(-0.6f, -0.02f, 0.6f, 0.1f)
                oval(-1f, 0.12f, 1f, 0.52f)
            }
            Motif.GLASSES -> {
                lw = 0.12f
                l.addCircle(-0.5f, 0.05f, 0.38f, Path.Direction.CW)
                l.addCircle(0.5f, 0.05f, 0.38f, Path.Direction.CW)
                l.moveTo(-0.12f, 0f)
                l.quadTo(0f, -0.16f, 0.12f, 0f)
                seg(-0.88f, 0f, -1f, -0.24f)
                seg(0.88f, 0f, 1f, -0.24f)
            }
            Motif.BALL -> {
                circle(0f, 0f, 0.92f)
                cut(0.07f) { moveTo(-0.92f, 0f); quadTo(0f, -0.5f, 0.92f, 0f) }
                cut(0.07f) { moveTo(-0.66f, 0.62f); quadTo(0f, 0.12f, 0.66f, 0.62f) }
                cut(0.07f) { moveTo(0f, -0.92f); lineTo(0f, 0.92f) }
            }
            Motif.TROPHY -> {
                f.moveTo(-0.5f, -0.92f)
                f.lineTo(0.5f, -0.92f)
                f.cubicTo(0.5f, -0.1f, 0.2f, 0.1f, 0.1f, 0.16f)
                f.lineTo(0.1f, 0.56f)
                f.lineTo(0.46f, 0.66f)
                f.lineTo(0.46f, 0.92f)
                f.lineTo(-0.46f, 0.92f)
                f.lineTo(-0.46f, 0.66f)
                f.lineTo(-0.1f, 0.56f)
                f.lineTo(-0.1f, 0.16f)
                f.cubicTo(-0.2f, 0.1f, -0.5f, -0.1f, -0.5f, -0.92f)
                f.close()
                lw = 0.1f
                l.addArc(RectF(-0.92f, -0.8f, -0.3f, -0.2f), 90f, 180f)
                l.addArc(RectF(0.3f, -0.8f, 0.92f, -0.2f), -90f, 180f)
            }
            Motif.PUMPKIN -> {
                oval(-0.92f, -0.45f, 0.92f, 0.92f)
                poly(-0.1f, -0.42f, -0.06f, -0.92f, 0.22f, -0.86f, 0.12f, -0.42f)
                minus(Path().apply { moveTo(-0.5f, 0.05f); lineTo(-0.2f, 0.05f); lineTo(-0.35f, -0.22f); close() })
                minus(Path().apply { moveTo(0.5f, 0.05f); lineTo(0.2f, 0.05f); lineTo(0.35f, -0.22f); close() })
                minus(Path().apply {
                    moveTo(-0.5f, 0.35f); lineTo(-0.25f, 0.45f); lineTo(0f, 0.35f); lineTo(0.25f, 0.45f); lineTo(0.5f, 0.35f)
                    lineTo(0.3f, 0.65f); lineTo(0f, 0.55f); lineTo(-0.3f, 0.65f); close()
                })
            }
            Motif.GHOST -> {
                f.moveTo(-0.62f, 0.92f)
                f.lineTo(-0.62f, -0.3f)
                f.cubicTo(-0.62f, -1.18f, 0.62f, -1.18f, 0.62f, -0.3f)
                f.lineTo(0.62f, 0.92f)
                f.lineTo(0.37f, 0.66f)
                f.lineTo(0.12f, 0.92f)
                f.lineTo(-0.12f, 0.66f)
                f.lineTo(-0.37f, 0.92f)
                f.close()
                hole(-0.23f, -0.3f, 0.12f)
                hole(0.23f, -0.3f, 0.12f)
            }
            Motif.HOLLY -> {
                fun spiky(deg: Float) {
                    val leafPath = Path().apply {
                        moveTo(0f, 0f)
                        quadTo(0.1f, -0.28f, 0.3f, -0.12f); quadTo(0.4f, -0.36f, 0.6f, -0.14f); quadTo(0.76f, -0.3f, 1f, 0f)
                        quadTo(0.76f, 0.3f, 0.6f, 0.14f); quadTo(0.4f, 0.36f, 0.3f, 0.12f); quadTo(0.1f, 0.28f, 0f, 0f)
                        close()
                    }
                    f.addPath(turned(deg, leafPath), 0f, 0.1f)
                }
                spiky(-150f)
                spiky(-30f)
                spiky(90f)
                circle(0f, -0.12f, 0.16f)
                circle(-0.2f, 0.2f, 0.16f)
                circle(0.2f, 0.2f, 0.16f)
            }
            Motif.BELL -> {
                f.moveTo(-0.78f, 0.56f)
                f.cubicTo(-0.46f, 0.3f, -0.56f, -0.82f, 0f, -0.82f)
                f.cubicTo(0.56f, -0.82f, 0.46f, 0.3f, 0.78f, 0.56f)
                f.close()
                circle(0f, 0.76f, 0.16f)
                circle(0f, -0.88f, 0.1f)
            }
            Motif.GIFT -> {
                rect(-0.76f, -0.2f, 0.76f, 0.92f)
                rect(-0.88f, -0.52f, 0.88f, -0.26f)
                holeRect(-0.11f, -0.52f, 0.11f, 0.92f)
                lw = 0.1f
                l.addOval(RectF(-0.52f, -0.96f, -0.02f, -0.56f), Path.Direction.CW)
                l.addOval(RectF(0.02f, -0.96f, 0.52f, -0.56f), Path.Direction.CW)
            }
            Motif.MASK -> {
                f.moveTo(-0.96f, -0.3f)
                f.quadTo(-0.5f, -0.56f, 0f, -0.25f)
                f.quadTo(0.5f, -0.56f, 0.96f, -0.3f)
                f.quadTo(1f, 0.3f, 0.46f, 0.42f)
                f.quadTo(0.16f, 0.42f, 0f, 0.1f)
                f.quadTo(-0.16f, 0.42f, -0.46f, 0.42f)
                f.quadTo(-1f, 0.3f, -0.96f, -0.3f)
                f.close()
                minus(Path().apply { addOval(RectF(-0.72f, -0.16f, -0.26f, 0.16f), Path.Direction.CW) })
                minus(Path().apply { addOval(RectF(0.26f, -0.16f, 0.72f, 0.16f), Path.Direction.CW) })
            }
            Motif.LANTERN -> {
                rect(-0.42f, -0.45f, 0.42f, 0.62f)
                round(-0.52f, 0.62f, 0.52f, 0.78f, 0.05f)
                poly(-0.56f, -0.45f, 0f, -0.84f, 0.56f, -0.45f)
                minus(Path().apply { addRoundRect(RectF(-0.26f, -0.3f, 0.26f, 0.46f), 0.08f, 0.08f, Path.Direction.CW) })
                f.moveTo(0f, -0.14f)
                f.quadTo(0.17f, 0.14f, 0f, 0.3f)
                f.quadTo(-0.17f, 0.14f, 0f, -0.14f)
                f.close()
                lw = 0.07f
                l.addCircle(0f, -0.92f, 0.08f, Path.Direction.CW)
            }
            Motif.ARROW -> {
                lw = 0.1f
                seg(-0.72f, 0.72f, 0.7f, -0.7f)
                poly(0.96f, -0.96f, 0.4f, -0.82f, 0.82f, -0.4f)
                poly(-0.6f, 0.6f, -0.96f, 0.56f, -0.82f, 0.82f)
                poly(-0.6f, 0.6f, -0.56f, 0.96f, -0.82f, 0.82f)
            }
            Motif.HELMET -> {
                f.moveTo(-0.62f, 0.9f)
                f.lineTo(-0.62f, -0.2f)
                f.cubicTo(-0.62f, -1.1f, 0.62f, -1.1f, 0.62f, -0.2f)
                f.lineTo(0.62f, 0.9f)
                f.lineTo(0f, 0.72f)
                f.close()
                holeRect(-0.46f, -0.24f, 0.46f, -0.1f)
                holeRect(-0.05f, -0.1f, 0.05f, 0.5f)
                hole(-0.3f, 0.2f, 0.05f)
                hole(0.3f, 0.2f, 0.05f)
                hole(-0.3f, 0.42f, 0.05f)
                hole(0.3f, 0.42f, 0.05f)
            }
            Motif.COLUMN -> {
                rect(-0.72f, -1f, 0.72f, -0.84f)
                rect(-0.56f, -0.84f, 0.56f, -0.7f)
                rect(-0.42f, -0.7f, 0.42f, 0.7f)
                rect(-0.56f, 0.7f, 0.56f, 0.84f)
                rect(-0.72f, 0.84f, 0.72f, 1f)
                for (x in floatArrayOf(-0.2f, 0f, 0.2f)) cut(0.06f) { moveTo(x, -0.6f); lineTo(x, 0.6f) }
            }
            Motif.LAUREL -> {
                lw = 0.06f
                l.addArc(RectF(-0.8f, -0.8f, 0.8f, 0.8f), 60f, 240f)
                for (k in 0 until 8) {
                    val deg = 75f + k * 28f
                    val a = Math.toRadians(deg.toDouble())
                    val x = (cos(a) * 0.8).toFloat()
                    val y = (sin(a) * 0.8).toFloat()
                    leaf(f, x, y, 0.36f, deg + 90f - 38f, 0.36f)
                    leaf(f, x, y, 0.3f, deg + 90f + 42f, 0.36f)
                }
            }
            Motif.PYRAMID -> {
                poly(0f, -0.82f, 0.96f, 0.7f, -0.96f, 0.7f)
                cut(0.06f) { moveTo(0f, -0.82f); lineTo(0.3f, 0.7f) }
            }
            Motif.CACTUS -> {
                round(-0.19f, -0.92f, 0.19f, 0.92f, 0.19f)
                round(-0.72f, -0.32f, -0.42f, 0.3f, 0.15f)
                rect(-0.6f, 0.06f, -0.1f, 0.3f)
                round(0.42f, -0.58f, 0.72f, 0.05f, 0.15f)
                rect(0.1f, -0.2f, 0.6f, 0.05f)
            }
            Motif.ATOM -> {
                lw = 0.08f
                val ring = Path().apply { addOval(RectF(-1f, -0.38f, 1f, 0.38f), Path.Direction.CW) }
                for (k in 0 until 3) l.addPath(turned(k * 60f, ring))
                circle(0f, 0f, 0.16f)
            }
            Motif.SAUCER -> {
                f.moveTo(-0.42f, -0.1f)
                f.cubicTo(-0.42f, -0.78f, 0.42f, -0.78f, 0.42f, -0.1f)
                f.close()
                oval(-1f, -0.2f, 1f, 0.26f)
                lw = 0.07f
                seg(-0.3f, 0.4f, -0.52f, 0.92f)
                seg(0f, 0.4f, 0f, 0.92f)
                seg(0.3f, 0.4f, 0.52f, 0.92f)
            }
            Motif.ROBOT -> {
                round(-0.7f, -0.5f, 0.7f, 0.72f, 0.16f)
                hole(-0.3f, -0.05f, 0.17f)
                hole(0.3f, -0.05f, 0.17f)
                holeRect(-0.3f, 0.36f, 0.3f, 0.46f)
                rect(-0.86f, -0.1f, -0.7f, 0.3f)
                rect(0.7f, -0.1f, 0.86f, 0.3f)
                rect(-0.04f, -0.84f, 0.04f, -0.5f)
                circle(0f, -0.9f, 0.1f)
            }
            Motif.PENCIL -> {
                val pencil = Path().apply {
                    addRect(-0.15f, -0.62f, 0.15f, 0.56f, Path.Direction.CW)
                    moveTo(-0.15f, 0.6f); lineTo(0.15f, 0.6f); lineTo(0f, 1f); close()
                    addRect(-0.15f, -0.96f, 0.15f, -0.7f, Path.Direction.CW)
                }
                f.addPath(turned(40f, pencil))
            }
            Motif.APPLE -> {
                f.moveTo(0f, -0.45f)
                f.cubicTo(0.5f, -0.8f, 1f, -0.3f, 0.86f, 0.25f)
                f.cubicTo(0.76f, 0.76f, 0.36f, 1f, 0f, 0.86f)
                f.cubicTo(-0.36f, 1f, -0.76f, 0.76f, -0.86f, 0.25f)
                f.cubicTo(-1f, -0.3f, -0.5f, -0.8f, 0f, -0.45f)
                f.close()
                leaf(f, 0.08f, -0.62f, 0.5f, -30f, 0.4f)
                lw = 0.08f
                l.moveTo(0f, -0.45f)
                l.quadTo(-0.02f, -0.72f, -0.14f, -0.9f)
            }
            Motif.PLANE -> {
                poly(-0.96f, -0.1f, 0.96f, -0.82f, 0.2f, 0.86f, -0.06f, 0.26f)
                cut(0.06f) { moveTo(0.96f, -0.82f); lineTo(-0.06f, 0.26f) }
            }
            Motif.UMBRELLA -> {
                f.moveTo(-0.96f, 0f)
                f.cubicTo(-0.96f, -1.12f, 0.96f, -1.12f, 0.96f, 0f)
                f.quadTo(0.72f, -0.26f, 0.48f, 0f)
                f.quadTo(0.24f, -0.26f, 0f, 0f)
                f.quadTo(-0.24f, -0.26f, -0.48f, 0f)
                f.quadTo(-0.72f, -0.26f, -0.96f, 0f)
                f.close()
                lw = 0.09f
                seg(0f, -0.1f, 0f, 0.7f)
                l.addArc(RectF(-0.3f, 0.55f, 0f, 0.85f), 0f, 180f)
            }
            Motif.RING -> {
                lw = 0.14f
                l.addCircle(0f, 0.28f, 0.6f, Path.Direction.CW)
                poly(-0.3f, -0.56f, 0f, -0.98f, 0.3f, -0.56f, 0f, -0.28f)
            }
            Motif.FLEUR -> {
                f.moveTo(0.1f, 0.36f)
                f.cubicTo(0.3f, -0.1f, 0.5f, -0.56f, 0.86f, -0.45f)
                f.cubicTo(1.06f, -0.35f, 0.96f, 0.06f, 0.7f, 0.06f)
                f.cubicTo(0.76f, -0.15f, 0.6f, -0.2f, 0.5f, -0.05f)
                f.cubicTo(0.4f, 0.1f, 0.36f, 0.25f, 0.32f, 0.36f)
                f.close()
                mirror()
                f.moveTo(0f, -1f)
                f.cubicTo(0.36f, -0.5f, 0.3f, 0f, 0.09f, 0.36f)
                f.lineTo(-0.09f, 0.36f)
                f.cubicTo(-0.3f, 0f, -0.36f, -0.5f, 0f, -1f)
                f.close()
                round(-0.42f, 0.36f, 0.42f, 0.52f, 0.05f)
                poly(-0.26f, 0.52f, 0.26f, 0.52f, 0f, 0.98f)
            }
        }
        return Shape(f, l, lw)
    }

    // ---- noise, for marbled paper, contour lines and worn edges ---------------------------------------------

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    /** Smooth noise from 0 to 1 at ([x], [y]). */
    fun noise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = hash(x0, y0, seed)
        val b = hash(x0 + 1, y0, seed)
        val c = hash(x0, y0 + 1, seed)
        val d = hash(x0 + 1, y0 + 1, seed)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }

    /** Noise in [octaves] layers, each finer and fainter, from 0 to 1. */
    fun cloudy(x: Float, y: Float, seed: Int, octaves: Int = 4): Float {
        var sum = 0f
        var amp = 0.5f
        var f = 1f
        var total = 0f
        for (o in 0 until octaves) {
            sum += noise(x * f, y * f, seed + o * 31) * amp
            total += amp
            amp *= 0.5f
            f *= 2f
        }
        return sum / total
    }
}
