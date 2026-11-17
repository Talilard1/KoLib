package dev.kolib.app.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Page turns run against a stand-in screen with its own clock: frames come every 16 ms, copies of
 * the screen and "drawn" notices arrive a frame later, and each page is a plain colour (two colours
 * side by side in two-page view), so what's on screen can be checked at any moment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTurnerTest {

    private fun colourOf(chapter: Int, page: Int) = Color.rgb((40 + chapter * 70) % 256, (30 + page * 45) % 256, 200 - page * 20)

    private inner class Book(var spreadView: Boolean = false) : TurnPages {
        var chapter = 0
        val chapters = 3
        override var page = 0
        var pagesIn = 5
        var mode = PageTurn.CURL
        var turnsAsked = 0
        var chapterOpens = 0
        lateinit var turner: PageTurner
        lateinit var screen: Screen
        /** Seen by the page script: the next chapter opens after this many frames, or never. */
        var chapterLoadFrames: Int? = 3

        override val pageTurn get() = mode
        override val spread get() = spreadView
        override var selecting = false
        override var blocked = false
        override val pages get() = pagesIn
        override val paper = Color.rgb(0xFB, 0xF8, 0xF1)
        override fun canMove(dir: Int) = chapter + dir in 0 until chapters
        override fun turnPageNow(dir: Int, done: (Boolean) -> Unit) {
            turnsAsked++
            val ok = page + dir in 0 until pagesIn
            screen.later(1) {
                if (ok) page += dir
                done(ok)
            }
        }
        override fun openChapter(dir: Int) {
            chapterOpens++
            val frames = chapterLoadFrames ?: return
            screen.later(frames) {
                chapter += dir
                page = if (dir > 0) 0 else pagesIn - 1
                turner.chapterReady()
            }
        }
        override fun askForTurn(dir: Int) {
            if (page + dir in 0 until pagesIn) turner.turn(dir, 0.8f) else turner.edge(dir)
        }

        /** What the page shows, without anything drawn over it. */
        fun paint(c: Canvas, w: Int, h: Int) {
            val p = Paint()
            if (spreadView) {
                p.color = colourOf(chapter, page * 2); c.drawRect(0f, 0f, w / 2f, h.toFloat(), p)
                p.color = colourOf(chapter, page * 2 + 1); c.drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), p)
            } else {
                p.color = colourOf(chapter, page); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
        }
    }

    private inner class Screen(override val width: Int, override val height: Int, val book: Book) : TurnHost {
        override val density = 1f
        override val touchSlop = 8
        override val surfaceReady = true
        var now = 1_000_000_000L
        var copies = 0
        var copyFails = false
        var frames = 0
        var given = 0
        /** What the surface shows now: null when it's clear. */
        var surface: Bitmap? = null
        private val tasks = mutableListOf<Pair<Int, () -> Unit>>()
        private var frameCallback: ((Long) -> Unit)? = null
        private val delayed = mutableListOf<Pair<Long, () -> Unit>>()

        fun later(frames: Int, then: () -> Unit) {
            tasks += (this.frames + frames) to then
        }

        override fun copyScreen(done: (Bitmap?) -> Unit) {
            copies++
            if (copyFails) {
                later(1) { done(null) }
                return
            }
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            book.paint(Canvas(b), width, height)
            later(1) { done(b) }
        }

        override fun draw(block: (Canvas) -> Unit): Boolean {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            block(Canvas(b))
            surface = b
            return true
        }

        override fun clear() {
            surface = null
        }

        override fun whenDrawn(then: () -> Unit) = later(2, then)
        override fun afterFrames(n: Int, then: () -> Unit) = later(n, then)
        override fun postFrame(callback: (Long) -> Unit) {
            frameCallback = callback
        }
        override fun cancelFrames() {
            frameCallback = null
        }
        override fun postDelayed(ms: Long, then: () -> Unit) {
            delayed += (now + ms * 1_000_000) to then
        }
        override fun nanoTime() = now
        override fun giveBack(b: Bitmap) {
            given++
        }

        fun frame() {
            frames++
            now += 16_000_000
            val due = tasks.filter { it.first <= frames }
            tasks.removeAll(due)
            due.forEach { it.second() }
            frameCallback?.let { cb ->
                frameCallback = null
                cb(now)
            }
            val ripe = delayed.filter { it.first <= now }
            delayed.removeAll(ripe)
            ripe.forEach { it.second() }
        }

        fun run(ms: Int) = repeat(ms / 16) { frame() }

        /** What's on screen: the page, with the surface over it. */
        fun shown(): Bitmap {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            book.paint(c, width, height)
            surface?.let { c.drawBitmap(it, 0f, 0f, null) }
            return b
        }
    }

    private fun setUp(w: Int = 800, h: Int = 1200, spread: Boolean = false): Triple<Book, Screen, PageTurner> {
        val book = Book(spread)
        val screen = Screen(w, h, book)
        val turner = PageTurner(screen, book)
        book.turner = turner
        book.screen = screen
        return Triple(book, screen, turner)
    }

    private fun near(c: Int, expected: Int, tol: Int = 12) =
        Math.abs(Color.red(c) - Color.red(expected)) <= tol && Math.abs(Color.green(c) - Color.green(expected)) <= tol && Math.abs(Color.blue(c) - Color.blue(expected)) <= tol

    private val out = File("build/outputs/curl").apply { mkdirs() }
    private fun save(b: Bitmap, name: String) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun aTappedCurlTurnsThePageAndClearsUp() {
        val (book, screen, turner) = setUp()
        assertTrue(turner.turn(1, 0.8f))
        // Nothing changes on screen while the copy is made and put up.
        screen.run(32)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 0)))
        screen.run(16 * 12)
        // Part way: the old page still covers the left, the new one shows on the right.
        val mid = screen.shown()
        save(mid, "turner_single_mid")
        assertEquals(1, book.page)
        assertTrue(turner.showing)
        assertTrue(near(mid.getPixel(40, 300), colourOf(0, 0)))
        // The bottom corner lifts first: the new page shows there, clear of the curl's shadow.
        assertTrue("new page at the corner: ${Integer.toHexString(mid.getPixel(795, 1150))}", near(mid.getPixel(795, 1150), colourOf(0, 1)))
        screen.run(600)
        assertFalse(turner.showing)
        assertTrue(screen.surface == null)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 1)))
        assertEquals(1, screen.copies)
        assertEquals(1, screen.given)
    }

    @Test
    fun turningBackTheEarlierPageComesBackOverThisOne() {
        val (book, screen, turner) = setUp()
        book.page = 2
        turner.turn(-1, 0.8f)
        screen.run(16 * 3)
        // Until the earlier page has been copied, this page is held on screen.
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 2)))
        screen.run(16 * 14)
        val mid = screen.shown()
        save(mid, "turner_back_mid")
        assertEquals(1, book.page)
        assertEquals(2, screen.copies)
        // The earlier page is rolling back in from the left, its paper back showing first; the right is
        // still this page.
        assertFalse(near(mid.getPixel(20, 600), colourOf(0, 2), 30))
        assertTrue(near(mid.getPixel(790, 300), colourOf(0, 2)))
        // Past half way its face shows, from the left.
        screen.run(16 * 12)
        val later = screen.shown()
        save(later, "turner_back_later")
        assertTrue("face from the left: ${Integer.toHexString(later.getPixel(10, 600))}", near(later.getPixel(10, 600), colourOf(0, 1), 30))
        screen.run(700)
        assertFalse(turner.showing)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 1)))
    }

    @Test
    fun inTwoPageViewTheRightPageTurnsOntoTheLeft() {
        val (book, screen, turner) = setUp(1280, 800, spread = true)
        turner.turn(1, 0.8f)
        screen.run(16 * 16)
        val mid = screen.shown()
        save(mid, "turner_spread_mid")
        // The left page is still the old left page; the new right page shows at the far right.
        assertTrue(near(mid.getPixel(30, 400), colourOf(0, 0)))
        assertTrue(near(mid.getPixel(1270, 100), colourOf(0, 3), 40))
        assertEquals(2, screen.copies)
        screen.run(700)
        assertFalse(turner.showing)
        assertTrue(near(screen.shown().getPixel(30, 400), colourOf(0, 2)))
    }

    @Test
    fun pastTheLastPageTheNextChapterOpensUnderTheCurl() {
        val (book, screen, turner) = setUp()
        book.page = 4
        assertTrue(turner.edge(1))
        screen.run(16 * 3)
        assertEquals(1, book.chapterOpens)
        // While it opens, the last page is held on screen.
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 4)))
        screen.run(1000)
        assertFalse(turner.showing)
        assertEquals(1, book.chapter)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(1, 0)))
    }

    @Test
    fun aChapterThatNeverOpensDoesNotLeaveTheCopyUp() {
        val (book, screen, turner) = setUp()
        book.page = 4
        book.chapterLoadFrames = null
        turner.edge(1)
        screen.run(1000)
        assertTrue(turner.showing)
        screen.run(6000)
        assertFalse(turner.showing)
        assertTrue(screen.surface == null)
    }

    private fun ev(action: Int, x: Float, y: Float, at: Long): MotionEvent = MotionEvent.obtain(1000, at, action, x, y, 0)

    @Test
    fun aDragTurnsThePageWithTheFinger() {
        val (book, screen, turner) = setUp()
        var t = SystemClock.uptimeMillis()
        assertFalse(turner.intercept(ev(MotionEvent.ACTION_DOWN, 760f, 900f, t)))
        assertFalse(turner.intercept(ev(MotionEvent.ACTION_MOVE, 755f, 900f, t + 16)))
        assertTrue(turner.intercept(ev(MotionEvent.ACTION_MOVE, 730f, 902f, t + 32)))
        screen.run(16 * 4)
        for (x in listOf(650f, 560f, 470f, 400f)) {
            t += 16
            turner.touch(ev(MotionEvent.ACTION_MOVE, x, 905f, t))
            screen.frame()
        }
        val held = turner.progress
        // 360 of the 440 a full turn takes (a little over half the screen).
        assertTrue("follows the finger: $held", held in 0.78f..0.86f)
        save(screen.shown(), "turner_drag")
        screen.run(16 * 4)
        assertTrue(turner.showing)
        turner.touch(ev(MotionEvent.ACTION_UP, 400f, 905f, t + 400))
        screen.run(800)
        assertFalse(turner.showing)
        assertEquals(1, book.page)
    }

    @Test
    fun draggedAtAChaptersEndTheNextChapterShowsUnderTheLeaf() {
        val (book, screen, turner) = setUp()
        book.page = 4
        var t = SystemClock.uptimeMillis()
        turner.intercept(ev(MotionEvent.ACTION_DOWN, 780f, 900f, t))
        assertTrue(turner.intercept(ev(MotionEvent.ACTION_MOVE, 750f, 900f, t + 16)))
        screen.run(16 * 3)
        // The next chapter opens straight away, under the copy of this page.
        assertEquals(1, book.chapterOpens)
        for (x in listOf(700f, 600f, 500f)) {
            t += 16
            turner.touch(ev(MotionEvent.ACTION_MOVE, x, 905f, t))
            screen.run(16 * 3)
        }
        assertTrue(turner.showing)
        val held = screen.shown()
        save(held, "turner_edge_drag_held")
        // Where the leaf has lifted (the bottom right), the next chapter's first page shows, not blank paper.
        assertTrue("next chapter under the leaf: ${Integer.toHexString(held.getPixel(790, 1190))}", near(held.getPixel(790, 1190), colourOf(1, 0)))
        turner.touch(ev(MotionEvent.ACTION_UP, 500f, 905f, t + 400))
        screen.run(1000)
        assertFalse(turner.showing)
        assertEquals(1, book.chapter)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(1, 0)))
    }

    @Test
    fun anEdgeDragLetGoEarlyGoesBackToWhereItWas() {
        val (book, screen, turner) = setUp()
        book.page = 4
        val t = SystemClock.uptimeMillis()
        turner.intercept(ev(MotionEvent.ACTION_DOWN, 780f, 900f, t))
        assertTrue(turner.intercept(ev(MotionEvent.ACTION_MOVE, 750f, 900f, t + 16)))
        screen.run(16 * 8)
        turner.touch(ev(MotionEvent.ACTION_MOVE, 720f, 900f, t + 400))
        screen.run(16 * 3)
        turner.touch(ev(MotionEvent.ACTION_UP, 720f, 900f, t + 800))
        // While the chapter it started in opens again, this page stays on screen.
        screen.run(16 * 4)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 4)))
        screen.run(1000)
        assertFalse(turner.showing)
        assertEquals(2, book.chapterOpens)
        assertEquals(0, book.chapter)
        assertEquals(4, book.page)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 4)))
    }

    @Test
    fun heldRightOverInTwoPageViewTheWholeNewSpreadShows() {
        val (book, screen, turner) = setUp(1280, 800, spread = true)
        var t = SystemClock.uptimeMillis()
        turner.intercept(ev(MotionEvent.ACTION_DOWN, 1270f, 600f, t))
        assertTrue(turner.intercept(ev(MotionEvent.ACTION_MOVE, 1240f, 600f, t + 16)))
        screen.run(16 * 8)
        // Right across, and well up the screen: the leaf still lands square on the left page.
        for (x in listOf(1000f, 700f, 400f, 100f, 0f)) {
            t += 16
            turner.touch(ev(MotionEvent.ACTION_MOVE, x, 250f, t))
            screen.run(16 * 2)
        }
        assertTrue(turner.showing)
        assertEquals(1f, turner.progress, 0.001f)
        val held = screen.shown()
        save(held, "turner_spread_held_over")
        for ((x, y) in listOf(8 to 8, 8 to 792, 620 to 8, 620 to 792, 320 to 400)) {
            assertTrue("new left page at $x,$y: ${Integer.toHexString(held.getPixel(x, y))}", near(held.getPixel(x, y), colourOf(0, 2)))
        }
        assertTrue(near(held.getPixel(1000, 400), colourOf(0, 3)))
        turner.touch(ev(MotionEvent.ACTION_UP, 0f, 250f, t + 400))
        screen.run(800)
        assertFalse(turner.showing)
        assertEquals(1, book.page)
    }

    @Test
    fun aDragLetGoEarlyPutsThePageBack() {
        val (book, screen, turner) = setUp()
        val t = SystemClock.uptimeMillis()
        turner.intercept(ev(MotionEvent.ACTION_DOWN, 760f, 900f, t))
        assertTrue(turner.intercept(ev(MotionEvent.ACTION_MOVE, 730f, 900f, t + 16)))
        screen.run(16 * 6)
        turner.touch(ev(MotionEvent.ACTION_MOVE, 700f, 900f, t + 400))
        screen.run(16 * 3)
        turner.touch(ev(MotionEvent.ACTION_UP, 700f, 900f, t + 800))
        screen.run(1000)
        assertFalse(turner.showing)
        assertEquals(0, book.page)
        assertTrue(near(screen.shown().getPixel(400, 600), colourOf(0, 0)))
    }

    @Test
    fun tapsInQuickSuccessionAllTurn() {
        val (book, screen, turner) = setUp()
        turner.turn(1, 0.8f)
        screen.run(100)
        turner.turn(1, 0.8f)
        screen.run(40)
        turner.turn(1, 0.8f)
        screen.run(3000)
        assertEquals(3, book.page)
        assertFalse(turner.showing)
    }

    @Test
    fun withoutACopyOfTheScreenThePageJustTurns() {
        val (book, screen, turner) = setUp()
        screen.copyFails = true
        turner.turn(1, 0.8f)
        screen.run(200)
        assertEquals(1, book.page)
        assertFalse(turner.showing)
        assertTrue(screen.surface == null)
    }

    @Test
    fun aSweepUncoversTheNewPageFromTheTopCorner() {
        val (book, screen, turner) = setUp()
        book.mode = PageTurn.SWEEP
        assertTrue(turner.turn(1, 0.5f))
        // Until the new page is underneath, the old one is shown whole.
        screen.run(16 * 2)
        assertTrue(near(screen.shown().getPixel(790, 10), colourOf(0, 0)))
        // (About 0.7 seconds in all, slower than a curl.)
        screen.run(16 * 18)
        val mid = screen.shown()
        save(mid, "turner_sweep_mid")
        assertTrue(turner.showing)
        // Part way: the new page at the top right, where the sweep began; the old one still at the bottom left.
        assertTrue("new page top right: ${Integer.toHexString(mid.getPixel(790, 10))}", near(mid.getPixel(790, 10), colourOf(0, 1)))
        assertTrue("old page bottom left: ${Integer.toHexString(mid.getPixel(10, 1190))}", near(mid.getPixel(10, 1190), colourOf(0, 0)))
        screen.run(600)
        assertFalse(turner.showing)
        assertEquals(1, book.page)
        assertTrue(near(screen.shown().getPixel(10, 1190), colourOf(0, 1)))
        // Going back, it sweeps from the top left.
        assertTrue(turner.turn(-1, 0.5f))
        screen.run(16 * 20)
        val back = screen.shown()
        save(back, "turner_sweep_back")
        assertTrue(near(back.getPixel(10, 10), colourOf(0, 0)) && near(back.getPixel(790, 1190), colourOf(0, 1)))
        screen.run(600)
        assertEquals(0, book.page)
    }

    @Test
    fun aSweepFollowsTheFingerToo() {
        val (book, screen, turner) = setUp()
        book.mode = PageTurn.SWEEP
        var t = SystemClock.uptimeMillis()
        turner.intercept(ev(MotionEvent.ACTION_DOWN, 760f, 600f, t))
        assertTrue(turner.intercept(ev(MotionEvent.ACTION_MOVE, 730f, 600f, t + 16)))
        screen.run(16 * 8)
        for (x in listOf(700f, 600f, 500f)) {
            t += 16
            turner.touch(ev(MotionEvent.ACTION_MOVE, x, 600f, t))
            screen.run(16 * 2)
        }
        val held = screen.shown()
        save(held, "turner_sweep_drag")
        assertEquals(260f / 440f, turner.progress, 0.02f)
        // Held there: the new page uncovered at the top right, the old one still at the bottom left.
        assertTrue(near(held.getPixel(790, 10), colourOf(0, 1)) && near(held.getPixel(10, 1190), colourOf(0, 0)))
        // It doesn't run on by itself while it's held.
        screen.run(16 * 10)
        assertEquals(260f / 440f, turner.progress, 0.02f)
        turner.touch(ev(MotionEvent.ACTION_UP, 500f, 600f, t + 500))
        screen.run(900)
        assertFalse(turner.showing)
        assertEquals(1, book.page)
    }

    @Test
    fun fadeFadesTheOldPageOut() {
        val (book, screen, turner) = setUp()
        book.mode = PageTurn.FADE
        turner.turn(1, 0.5f)
        screen.run(16 * 10)
        val c = screen.shown().getPixel(400, 600)
        assertTrue("half way between: ${Integer.toHexString(c)}", !near(c, colourOf(0, 0), 6) && !near(c, colourOf(0, 1), 6))
        screen.run(400)
        assertFalse(turner.showing)
        assertEquals(1, book.page)
    }

    @Test
    fun slideAndNoneAreLeftToThePage() {
        val (book, _, turner) = setUp()
        book.mode = PageTurn.SLIDE
        assertFalse(turner.turn(1, 0.5f))
        book.mode = PageTurn.INSTANT
        assertFalse(turner.turn(1, 0.5f))
        book.mode = PageTurn.CURL
        book.blocked = true
        assertFalse(turner.turn(1, 0.5f))
    }

    @Test
    fun withTextSelectedADragIsLeftToThePage() {
        val (book, _, turner) = setUp()
        book.selecting = true
        val t = SystemClock.uptimeMillis()
        turner.intercept(ev(MotionEvent.ACTION_DOWN, 760f, 900f, t))
        assertFalse(turner.intercept(ev(MotionEvent.ACTION_MOVE, 600f, 900f, t + 16)))
    }
}
