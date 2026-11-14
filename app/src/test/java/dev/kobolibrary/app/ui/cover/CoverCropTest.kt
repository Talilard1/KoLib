package dev.kobolibrary.app.ui.cover

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverCropTest {

    private val fw = CoverCrop.OUT_WIDTH.toFloat()
    private val fh = CoverCrop.OUT_HEIGHT.toFloat()

    @Test
    fun fillCentresAWidePictureAndCoversTheFrame() {
        val p = CoverCrop().place(fw, fh, 2000, 1000)
        assertEquals(2896f, p.width, 0.5f)
        assertEquals(1448f, p.height, 0.5f)
        assertEquals(-912f, p.left, 0.5f)
        assertEquals(0f, p.top, 0.5f)
    }

    @Test
    fun draggingStopsAtTheEdges() {
        val moved = CoverCrop().transformed(5000f, 300f, 1f, 0f, 0f, fw, fh, 2000, 1000)
        val p = moved.place(fw, fh, 2000, 1000)
        assertEquals(0f, p.left, 0.5f) // left edge of the picture at the left of the frame, no gap
        assertEquals(0f, p.top, 0.5f) // no room to move vertically
        val other = CoverCrop().transformed(-5000f, 0f, 1f, 0f, 0f, fw, fh, 2000, 1000).place(fw, fh, 2000, 1000)
        assertEquals(fw - 2896f, other.left, 0.5f)
    }

    @Test
    fun pinchingKeepsThePointUnderTheFingers() {
        val before = CoverCrop().place(fw, fh, 2000, 1000)
        val pivotX = 100f
        val pivotY = 200f
        val fx = (pivotX - before.left) / before.width
        val fy = (pivotY - before.top) / before.height
        val zoomed = CoverCrop().transformed(0f, 0f, 2f, pivotX, pivotY, fw, fh, 2000, 1000)
        assertEquals(2f, zoomed.zoom, 0.0001f)
        val after = zoomed.place(fw, fh, 2000, 1000)
        assertEquals(fx, (pivotX - after.left) / after.width, 0.0005f)
        assertEquals(fy, (pivotY - after.top) / after.height, 0.0005f)
    }

    @Test
    fun theSameCropGivesTheSameCoverAtAnySize() {
        val crop = CoverCrop().transformed(-150f, 40f, 2.5f, 300f, 500f, fw, fh, 1600, 2400)
        val big = crop.place(fw, fh, 1600, 2400)
        val small = crop.place(fw / 2, fh / 2, 1600, 2400)
        assertEquals(big.left / 2, small.left, 0.01f)
        assertEquals(big.top / 2, small.top, 0.01f)
        assertEquals(big.width / 2, small.width, 0.01f)
    }

    @Test
    fun fitShowsTheWholePictureAndCanSlideWithinTheFrame() {
        val crop = CoverCrop(fit = CoverFit.FIT_BLUR)
        val p = crop.place(fw, fh, 1000, 3000)
        assertEquals(1448f, p.height, 0.5f)
        assertEquals((fw - p.width) / 2, p.left, 0.5f)
        val moved = crop.transformed(100f, 0f, 1f, 0f, 0f, fw, fh, 1000, 3000).place(fw, fh, 1000, 3000)
        assertEquals(p.left + 100f, moved.left, 0.5f)
        val tooFar = crop.transformed(5000f, 0f, 1f, 0f, 0f, fw, fh, 1000, 3000).place(fw, fh, 1000, 3000)
        assertEquals(fw - p.width, tooFar.left, 0.5f) // right edge of the picture at the right of the frame
    }

    @Test
    fun zoomStaysWithinLimits() {
        assertEquals(CoverCrop.MAX_ZOOM, CoverCrop().transformed(0f, 0f, 50f, 0f, 0f, fw, fh, 800, 1200).zoom, 0.0001f)
        assertEquals(CoverCrop.MIN_ZOOM, CoverCrop(zoom = 2f).transformed(0f, 0f, 0.01f, 0f, 0f, fw, fh, 800, 1200).zoom, 0.0001f)
        assertEquals(3f, CoverCrop().zoomedTo(3f, fw, fh, 800, 1200).zoom, 0.0001f)
    }

    @Test
    fun aPictureAsItIsIsShownWholeAndCantBeMovedOrZoomed() {
        val crop = CoverCrop(fit = CoverFit.AS_IS)
        // A square picture in the Kobo's frame: as wide as the frame, in the middle.
        val p = crop.place(1072f, 1448f, 900, 900)
        assertEquals(0f, p.left, 0.01f)
        assertEquals(1072f, p.width, 0.01f)
        assertEquals((1448f - 1072f) / 2, p.top, 0.01f)
        assertEquals(crop, crop.transformed(40f, 10f, 2f, 300f, 300f, 1072f, 1448f, 900, 900))
        assertEquals(crop, crop.zoomedTo(3f, 1072f, 1448f, 900, 900))
    }
}
