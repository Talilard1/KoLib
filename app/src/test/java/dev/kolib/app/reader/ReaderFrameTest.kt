package dev.kolib.app.reader

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.TestBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Swipes in from the side edges of the page open the side panels, and Android's back swipe is kept off those edges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderFrameTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun withFrame(settings: ReaderSettings = ReaderSettings(), block: (ReaderFrame) -> Unit) {
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, settings, context.assets)
            val frame = ReaderFrame(context, c)
            frame.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, 1280, 800)
            try {
                block(frame)
            } finally {
                frame.release()
                c.release()
            }
        }
    }

    /** A touch from (x0, y0) to (x1, y1), in steps, then lifted. */
    private fun ReaderFrame.drag(x0: Float, y0: Float, x1: Float, y1: Float) {
        val t = SystemClock.uptimeMillis()
        dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y0, 0))
        for (i in 1..8) {
            val k = i / 8f
            dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, 0))
        }
        dispatchTouchEvent(MotionEvent.obtain(t, t + 9 * 16L, MotionEvent.ACTION_UP, x1, y1, 0))
    }

    @Test
    fun aSwipeInFromEitherEdgeOpensThePanelOnThatSide() = withFrame { frame ->
        val sides = mutableListOf<Int>()
        frame.onSideSwipe = { sides += it }
        var levels = 0
        frame.onLevel = { levels++ }
        frame.sideSwipes = true

        // Android's back swipe is kept off both edges, all the way down.
        val rects = frame.systemGestureExclusionRects
        assertEquals(2, rects.size)
        assertEquals(0, rects[0].left)
        assertEquals(800, rects[0].height())
        assertEquals(1280, rects[1].right)

        frame.drag(4f, 400f, 120f, 405f)
        assertEquals("from the left edge: the contents", listOf(-1), sides)
        frame.drag(1276f, 300f, 1150f, 298f)
        assertEquals("from the right edge: the reading settings", listOf(-1, 1), sides)

        // Up or down the left edge is the brightness, not a panel.
        frame.drag(6f, 200f, 8f, 500f)
        assertEquals(listOf(-1, 1), sides)
        assertTrue("the brightness changed", levels > 0)

        // A drag that starts away from the edge is a page turn, and a tap at the edge isn't a swipe.
        frame.drag(200f, 400f, 600f, 400f)
        frame.drag(1275f, 400f, 1275f, 400f)
        assertEquals(listOf(-1, 1), sides)
    }

    /** Pulled in from the edge, the panel is told where the finger is as it goes, and how fast it was let go. */
    @Test
    fun thePanelFollowsTheFingerAndHearsHowItWasLetGo() = withFrame { frame ->
        val pulls = mutableListOf<Float>()
        val letGo = mutableListOf<Float>()
        frame.onSideSwipe = {}
        frame.onSidePull = { pulls += it }
        frame.onSideLetGo = { letGo += it }
        frame.sideSwipes = true
        frame.drag(4f, 400f, 300f, 405f)
        assertTrue("told as it went: $pulls", pulls.size >= 4)
        assertEquals("in step with the finger", pulls.sorted(), pulls)
        assertEquals(300f, pulls.last(), 1f)
        assertEquals("let go once, going in", 1, letGo.size)
        assertTrue("going in: ${letGo.single()}", letGo.single() > 0f)

        // From the right: how far in from that edge.
        pulls.clear()
        frame.drag(1276f, 300f, 1080f, 298f)
        assertEquals(200f, pulls.last(), 1f)
    }

    /** The brightness is set from the side chosen, starting in the part of it chosen; elsewhere, nothing. */
    @Test
    fun theBrightnessSwipeIsOnTheSideAndPartChosen() = withFrame(ReaderSettings(levelSide = LevelSide.RIGHT, levelStart = LevelStart.BOTTOM)) { frame ->
        var levels = 0
        frame.onLevel = { levels++ }
        frame.drag(6f, 600f, 8f, 780f)
        assertEquals("not on the left", 0, levels)
        frame.drag(1275f, 100f, 1274f, 300f)
        assertEquals("not from the top of the right", 0, levels)
        frame.drag(1275f, 600f, 1274f, 780f)
        assertTrue("from the foot of the right", levels > 0)
    }

    @Test
    fun withTheBrightnessSwipeOffTheSidesDontSetIt() = withFrame(ReaderSettings(levelSide = LevelSide.OFF)) { frame ->
        var levels = 0
        frame.onLevel = { levels++ }
        frame.drag(6f, 200f, 8f, 500f)
        frame.drag(1275f, 200f, 1274f, 500f)
        assertEquals(0, levels)
    }

    /** Scrolling, at the foot of a chapter: the frame's, with what it was told of each pull. */
    private fun ReaderFrame.pulls(atFoot: Boolean = true, atTop: Boolean = false): Pair<MutableList<Pair<Int, Float>>, MutableList<Pair<Int, Boolean>>> {
        val pulls = mutableListOf<Pair<Int, Float>>()
        val letGo = mutableListOf<Pair<Int, Boolean>>()
        canScroll = { d -> if (d > 0) !atFoot else !atTop }
        hasChapter = { true }
        onChapterPull = { d, f -> pulls += d to f }
        onChapterLetGo = { d, go -> letGo += d to go }
        return pulls to letGo
    }

    private val scrolled = ReaderSettings(flow = ReaderFlow.SCROLL)

    /** Pulled up far enough at the foot of a scrolled chapter, the ring fills; let go full, it's on to the next. */
    @Test
    fun pulledOnFarEnoughTheRingFillsAndItsOnToTheNext() = withFrame(scrolled) { frame ->
        val (pulls, letGo) = frame.pulls()
        frame.drag(640f, 700f, 640f, 200f)
        assertTrue("told as it went: $pulls", pulls.size >= 4 && pulls.all { it.first == 1 })
        assertEquals(1f, pulls.last().second)
        assertEquals(listOf(1 to true), letGo)
    }

    /** It takes some pulling before it starts to fill, and a short pull goes nowhere. */
    @Test
    fun aShortPullDoesntGoOn() = withFrame(scrolled) { frame ->
        val (pulls, letGo) = frame.pulls()
        frame.drag(640f, 700f, 640f, 670f)
        assertTrue("not filling yet: $pulls", pulls.all { it.second == 0f })
        assertEquals(listOf(1 to false), letGo.ifEmpty { listOf(1 to false) })
    }

    /** Let off part way, it empties; the touch stays the frame's (the page doesn't scroll back under it). */
    @Test
    fun letOffItEmpties() = withFrame(scrolled) { frame ->
        val (pulls, letGo) = frame.pulls()
        val t = SystemClock.uptimeMillis()
        frame.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 640f, 700f, 0))
        for (i in 1..10) frame.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, 640f, 700f - i * 30f, 0))
        val most = pulls.last().second
        for (i in 1..10) frame.dispatchTouchEvent(MotionEvent.obtain(t, t + 200 + i * 16L, MotionEvent.ACTION_MOVE, 640f, 400f + i * 30f, 0))
        assertTrue("emptied: $most then ${pulls.last().second}", most > 0.5f && pulls.last().second == 0f)
        frame.dispatchTouchEvent(MotionEvent.obtain(t, t + 400, MotionEvent.ACTION_UP, 640f, 700f, 0))
        assertEquals(listOf(1 to false), letGo)
    }

    /**
     * A scroll that only gets to the foot in this touch doesn't go on into a pull (reading on to the last lines doesn't
     * open the next chapter by chance); a fresh touch there does.
     */
    @Test
    fun aScrollThatGetsToTheFootDoesntGoOnIntoAPull() = withFrame(scrolled) { frame ->
        val (pulls, letGo) = frame.pulls()
        var atFoot = false
        frame.canScroll = { d -> if (d > 0) !atFoot else true }
        val t = SystemClock.uptimeMillis()
        frame.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 640f, 750f, 0))
        for (i in 1..16) {
            if (i == 4) atFoot = true
            frame.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, 640f, 750f - i * 40f, 0))
        }
        frame.dispatchTouchEvent(MotionEvent.obtain(t, t + 300, MotionEvent.ACTION_UP, 640f, 110f, 0))
        assertTrue("no pull in that touch: $pulls $letGo", pulls.isEmpty() && letGo.isEmpty())
        frame.drag(640f, 700f, 640f, 200f)
        assertEquals(listOf(1 to true), letGo)
    }

    /** At the top, pulled down: back into the chapter before. */
    @Test
    fun atTheTopItsBackToTheOneBefore() = withFrame(scrolled) { frame ->
        val (pulls, letGo) = frame.pulls(atFoot = false, atTop = true)
        frame.drag(640f, 100f, 640f, 600f)
        assertTrue(pulls.all { it.first == -1 })
        assertEquals(listOf(-1 to true), letGo)
    }

    /** Part way through the chapter, or turning pages, there's no pull: the page has the touch. */
    @Test
    fun midChapterOrOnPagesThereIsNoPull() {
        withFrame(scrolled) { frame ->
            val (pulls, letGo) = frame.pulls(atFoot = false)
            frame.drag(640f, 700f, 640f, 200f)
            assertTrue(pulls.isEmpty() && letGo.isEmpty())
        }
        withFrame { frame ->
            val (pulls, _) = frame.pulls()
            frame.drag(640f, 700f, 640f, 200f)
            assertTrue(pulls.isEmpty())
        }
    }

    @Test
    fun withSomethingOverThePageTheEdgesAreLeftToAndroid() = withFrame { frame ->
        val sides = mutableListOf<Int>()
        frame.onSideSwipe = { sides += it }
        frame.sideSwipes = true
        frame.sideSwipes = false
        assertTrue(frame.systemGestureExclusionRects.isEmpty())
        frame.drag(4f, 400f, 120f, 405f)
        assertTrue(sides.isEmpty())
    }
}
