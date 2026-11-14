package dev.kobolibrary.app.reader

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.TestBooks
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

    private fun withFrame(block: (ReaderFrame) -> Unit) {
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, ReaderSettings(), context.assets)
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
