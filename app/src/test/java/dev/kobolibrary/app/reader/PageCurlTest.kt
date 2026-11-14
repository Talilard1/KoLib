package dev.kobolibrary.app.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageCurlTest {

    private fun near(a: Float, b: Float, tol: Float = 1.5f) = abs(a - b) <= tol

    @Test
    fun aFlatLeafStaysWhereItIs() {
        val c = PageCurl(0f, 1, 800f, 1200f)
        val pose = c.pose(800f)
        val (x, y) = c.place(pose, 300f, 500f)
        assertTrue(near(x, 300f) && near(y, 500f))
    }

    @Test
    fun aTurnedLeafLiesMirroredOverTheSpine() {
        // Right-hand page of a spread, spine down the middle.
        val c = PageCurl(640f, 1, 640f, 800f)
        val pose = c.pose(0f)
        val (x, y) = c.place(pose, 500f, 300f)
        assertTrue("landed at $x,$y", near(x, 140f) && near(y, 300f))
        // And a left-hand page turning the other way.
        val l = PageCurl(640f, -1, 640f, 800f)
        val (lx, ly) = l.place(l.pose(0f), 500f, 300f)
        assertTrue("landed at $lx,$ly", near(lx, 1140f) && near(ly, 300f))
    }

    @Test
    fun partWayTheHeldCornerFollowsTheFoldAndTheSpineSideStaysFlat() {
        val c = PageCurl(0f, 1, 800f, 1200f)
        c.anchorY = 1000f
        val pose = c.pose(400f) // fold half way across
        // The held point lands mirrored across the fold, so at the spine.
        val (x, y) = c.place(pose, 800f, 1000f)
        assertTrue("held point at $x,$y", near(x, pose.fu, 2f) && near(y, pose.fy, 2f))
        assertTrue(near(pose.fu, 0f, 1f))
        // Near the spine nothing moves.
        val (sx, sy) = c.place(pose, 100f, 600f)
        assertTrue(near(sx, 100f) && near(sy, 600f))
        assertTrue(pose.radius > 0f)
    }

    @Test
    fun theFoldLeansOnlyALittle() {
        val c = PageCurl(0f, 1, 800f, 1200f)
        c.anchorY = 600f
        val pose = c.pose(500f, bendY = 600f + 2000f)
        val lean = Math.toDegrees(kotlin.math.atan2((pose.fy - c.anchorY).toDouble(), (800f - pose.fu).toDouble()))
        assertTrue("leans $lean°", abs(lean) <= 16.5)
    }

    @Test
    fun theFoldNeverCrossesTheSpineSoTheLeafIsNeverTornOff() {
        // A right-hand page held low, high and in the middle, dragged far up or down at every point of a turn.
        for ((w, h) in listOf(640f to 800f, 800f to 1200f)) {
            val c = PageCurl(w, 1, w, h)
            for (anchor in listOf(0.1f, 0.5f, 0.9f)) {
                c.anchorY = anchor * h
                for (step in 0..40) {
                    val bend = w * step / 40f
                    for (dy in listOf(-2000f, -300f, 0f, 300f, 2000f)) {
                        val pose = c.pose(bend, c.anchorY + dy)
                        assertTrue("fold crosses the spine: bend $bend, held at $anchor, moved $dy (${c.foldClearance(pose)})", c.foldClearance(pose) >= -1f)
                    }
                }
            }
            // Turned right over, however the finger has moved, the leaf lands square on the page opposite.
            c.anchorY = 0.8f * h
            val over = c.pose(0f, c.anchorY - 500f)
            assertTrue(near(over.fy, c.anchorY) && near(over.fu, -w))
            val (x, y) = c.place(over, w, 0f)
            assertTrue("corner lands on the far corner: $x, $y", near(x, 0f, 2f) && near(y, 0f, 2f))
        }
    }

    // ---- pictures, to look at --------------------------------------------------------------------

    private val out = File("build/outputs/curl").apply { mkdirs() }

    private fun page(w: Int, h: Int, title: String, ink: Int, paper: Int, columns: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(paper)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 22f; typeface = Typeface.SERIF }
        val head = Paint(p).apply { textSize = 34f; isFakeBoldText = true }
        val words = "It was a bright cold day in April and the clocks were striking thirteen Winston Smith his chin nuzzled into his breast in an effort to escape the vile wind slipped quickly through the glass doors".split(' ')
        val colW = w / columns
        for (col in 0 until columns) {
            val x0 = col * colW + 56f
            var y = 90f
            c.drawText("$title ${col + 1}", x0, y, head)
            y += 52f
            var wi = col * 7
            while (y < h - 70) {
                val line = StringBuilder()
                while (p.measureText(line.toString() + words[wi % words.size]) < colW - 112) {
                    line.append(words[wi % words.size]).append(' ')
                    wi++
                }
                c.drawText(line.toString(), x0, y, p)
                y += 34f
            }
            c.drawText("Page ${col + 1}", x0 + colW / 2f - 90, h - 30f, Paint(p).apply { textSize = 16f; color = Color.GRAY })
        }
        return b
    }

    private fun frame(w: Int, h: Int, block: (Canvas) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        block(Canvas(b))
        return b
    }

    private fun save(b: Bitmap, name: String) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun picturesOfTurningPages() {
        val paper = Color.rgb(0xFB, 0xF8, 0xF1)
        val ink = Color.rgb(0x1F, 0x1C, 0x18)
        val plain = Paint(Paint.FILTER_BITMAP_FLAG)

        // Single page, portrait: this page curls away revealing the next.
        run {
            val w = 800; val h = 1200
            val a = page(w, h, "This page", ink, paper, 1)
            val next = page(w, h, "Next page", Color.rgb(0x20, 0x30, 0x60), paper, 1)
            for (p in listOf(0.15f, 0.4f, 0.65f, 0.9f)) {
                val c = PageCurl(0f, 1, w.toFloat(), h.toFloat(), 1f)
                c.anchorY = h * 0.85f
                val img = frame(w, h) { cv ->
                    cv.drawBitmap(next, 0f, 0f, plain) // the page underneath, live in the app
                    c.draw(cv, a, null, paper, c.pose(w * (1 - p), c.anchorY - h * 0.05f * kotlin.math.sin(Math.PI.toFloat() * p)))
                }
                save(img, "single_forward_${(p * 100).toInt()}")
            }
        }
        // Two pages, landscape: the right page turns onto the left; its back is the new left page.
        run {
            val w = 1280; val h = 800
            val a = page(w, h, "Spread A", ink, paper, 2)
            val b = page(w, h, "Spread B", Color.rgb(0x60, 0x20, 0x20), paper, 2)
            for (p in listOf(0.2f, 0.5f, 0.8f)) {
                val c = PageCurl(w / 2f, 1, w / 2f, h.toFloat(), 1f)
                c.anchorY = h * 0.8f
                val img = frame(w, h) { cv ->
                    cv.drawBitmap(b, 0f, 0f, plain)
                    val left = Rect(0, 0, w / 2, h)
                    cv.drawBitmap(a, left, RectF(left), plain)
                    c.draw(cv, a, b, paper, c.pose(c.width * (1 - p), c.anchorY))
                }
                save(img, "spread_forward_${(p * 100).toInt()}")
            }
            // Turning back: the left page lifts and goes over to the right.
            val c = PageCurl(w / 2f, -1, w / 2f, h.toFloat(), 1f)
            c.anchorY = h * 0.8f
            val img = frame(w, h) { cv ->
                cv.drawBitmap(b, 0f, 0f, plain)
                val right = Rect(w / 2, 0, w, h)
                cv.drawBitmap(a, right, RectF(right), plain)
                c.draw(cv, a, b, paper, c.pose(c.width * 0.45f, c.anchorY))
            }
            save(img, "spread_back_55")
        }
        // Dark page, finger dragging diagonally.
        run {
            val w = 800; val h = 1200
            val night = Color.rgb(0x1E, 0x1D, 0x1B)
            val a = page(w, h, "Night", Color.rgb(0xD8, 0xD2, 0xC8), night, 1)
            val next = page(w, h, "Night next", Color.rgb(0xA0, 0xB8, 0xE0), night, 1)
            val c = PageCurl(0f, 1, w.toFloat(), h.toFloat(), 1f)
            c.anchorY = h * 0.75f
            val img = frame(w, h) { cv ->
                cv.drawBitmap(next, 0f, 0f, plain)
                c.draw(cv, a, null, night, c.pose(w * 0.55f, c.anchorY - 160f))
            }
            save(img, "single_dark_lean")
        }
        assertEquals(true, File(out, "single_forward_40.png").exists())
    }
}
