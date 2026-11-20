package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What's shown round the page keeps clear of a camera in the screen: beside it where it can, below it otherwise. */
class CutoutsTest {
    // A phone held upright with a punch hole in the middle of its top, and with one in its right corner.
    private val middle = Cutouts(411f, 891f, top = Cutouts.Span(190f, 221f, 32f))
    private val corner = Cutouts(411f, 891f, top = Cutouts.Span(340f, 380f, 30f))
    private val phone = ReaderSettings.PHONE

    @Test
    fun aHeadingUnderTheCameraGoesBelowItAndTakesTheTextWithIt() {
        assertTrue(middle.topMiddle)
        assertFalse(corner.topMiddle)
        assertTrue(phone.stockArrangement)
        assertTrue(phone.cameraInTheWay(middle))
        assertEquals(34, phone.topRowAt(middle))
        // The same room as when all that was known of the camera was how deep it is.
        assertEquals(phone.marginRoom(cutoutTop = 32), phone.marginRoom(middle))
        assertTrue(phone.marginRoom(middle).top > phone.marginRoom(Cutouts.NONE).top + 20)
    }

    @Test
    fun withTheHeadingsAtTheFootAndTheTimeInTheCornersNothingHasToMove() {
        val placed = phone.aroundCamera()
        assertEquals(RunningHeads.BOTTOM, placed.runningHeads)
        assertEquals(MarginSpot.TOP_LEFT, placed.shownSpotOf(MarginItem.CLOCK))
        assertEquals(MarginSpot.TOP_RIGHT, placed.shownSpotOf(MarginItem.BATTERY))
        assertFalse(placed.stockArrangement)
        assertFalse(placed.cameraInTheWay(middle))
        // The time and the battery are where they'd be with no camera, either side of it, and so is the text.
        assertEquals(MarginRoom.ROW_TOP, placed.topRowAt(middle))
        assertEquals(ReaderSettings.SIDE, placed.topInset(MarginSpot.TOP_LEFT, middle))
        assertEquals(ReaderSettings.SIDE, placed.topInset(MarginSpot.TOP_RIGHT, middle))
        assertEquals(placed.marginRoom(Cutouts.NONE).top, placed.marginRoom(middle).top)
        // A camera deeper than that room still has the text start below it.
        val deep = middle.copy(top = Cutouts.Span(190f, 221f, 52f))
        assertEquals(58, placed.marginRoom(deep).top)
    }

    @Test
    fun whatsInACornerMovesInFromACameraThere() {
        val placed = phone.aroundCamera()
        assertFalse(placed.cameraInTheWay(corner))
        assertEquals(ReaderSettings.SIDE, placed.topInset(MarginSpot.TOP_LEFT, corner))
        // The battery ends a little before the camera rather than under it.
        assertEquals(411f - (340f - ReaderSettings.CLEAR), placed.topInset(MarginSpot.TOP_RIGHT, corner))
        // A title there hasn't the room to: the row goes below.
        val title = placed.copy(marginItems = placed.marginItems + (MarginItem.BATTERY to MarginSpot.OFF) + (MarginItem.BOOK to MarginSpot.TOP_RIGHT))
        assertTrue(title.spotWidth(MarginSpot.TOP_RIGHT, 411f) > 100f)
        assertEquals(title.topInset(MarginSpot.TOP_RIGHT, corner) == null, title.cameraInTheWay(corner))
    }

    @Test
    fun somethingPutInTheMiddleUnderTheCameraGoesBelowIt() {
        val s = phone.aroundCamera().let { it.copy(marginItems = it.marginItems + (MarginItem.CLOCK to MarginSpot.TOP_CENTER)) }
        assertNull(s.topInset(MarginSpot.TOP_CENTER, middle))
        assertTrue(s.cameraInTheWay(middle))
        assertEquals(34, s.topRowAt(middle))
        // Not under a camera in the corner, though.
        assertFalse(s.copy(marginItems = s.marginItems + (MarginItem.BATTERY to MarginSpot.OFF)).cameraInTheWay(corner))
    }

    @Test
    fun scrollingTheBandAtTheTopHidesTheTextBehindTheCamera() {
        val scroll = phone.copy(flow = ReaderFlow.SCROLL)
        // The chapter's title in the middle of the top is under the camera: it goes below, in a deeper band.
        assertTrue(scroll.cameraInTheWay(middle))
        assertEquals(34 + MarginRoom.ROW_HEIGHT + 8, scroll.bandTop(middle))
        assertEquals(ReaderSettings.BAND, scroll.bandTop(Cutouts.NONE))
        // At the foot instead, with the time in the corners: the band is as it always was.
        val placed = scroll.aroundCamera()
        assertFalse(placed.cameraInTheWay(middle))
        assertEquals(ReaderSettings.BAND, placed.bandTop(middle))
        assertEquals(ReaderSettings.BAND, placed.bandBottom(middle))
        // With nothing shown at the top there's still a band, just deep enough to cover the camera.
        val bare = scroll.copy(runningHeads = RunningHeads.OFF, marginItems = MarginItem.entries.associateWith { MarginSpot.OFF })
        assertEquals(36, bare.bandTop(middle))
        assertEquals(0, bare.bandTop(Cutouts.NONE))
        // Unless the text's to have the whole screen: no band at all then.
        assertEquals(0, bare.copy(clearOfCamera = false).bandTop(middle))
        // With something shown at the top, its band stays, below the camera.
        assertEquals(34 + MarginRoom.ROW_HEIGHT + 8, scroll.copy(clearOfCamera = false).bandTop(middle))
    }

    @Test
    fun turningPagesTheTextCanHaveTheWholeScreen() {
        // One page at a time with nothing at the top: the text keeps out from under the camera...
        val one = phone.copy(runningHeads = RunningHeads.BOTTOM, marginItems = MarginItem.entries.associateWith { MarginSpot.OFF })
        assertTrue(one.marginRoom(middle).top > 0)
        // ...unless it's to have the whole screen.
        assertEquals(0, one.copy(clearOfCamera = false).marginRoom(middle).top)
        assertEquals(0, one.copy(clearOfCamera = false).pictureRoom(middle).top)
    }

    @Test
    fun aCameraOverTheGapBetweenTwoPagesIsInNobodysWay() {
        // A tablet held sideways with a notch in the middle of its top, two pages side by side.
        val tablet = ReaderSettings()
        val notch = Cutouts(1280f, 800f, top = Cutouts.Span(620f, 660f, 24f))
        assertEquals(2, tablet.pagesAcross(1280f, 800f))
        assertFalse(tablet.cameraInTheWay(notch))
        assertEquals(tablet.marginRoom(Cutouts.NONE), tablet.marginRoom(notch))
        // One page at a time, its heading would be under it.
        assertTrue(tablet.copy(spread = ReaderSpread.ONE).cameraInTheWay(notch))
    }

    @Test
    fun theTimeAndBatteryGoWhereTheySitBestWithTheHeadings() {
        val s = ReaderSettings()
        assertTrue(s.clockShown)
        assertFalse(s.withClock(false).clockShown)
        assertEquals(MarginSpot.BOTTOM_CENTER, s.withClock(false).withClock(true).spotOf(MarginItem.CLOCK))
        val foot = s.withHeads(RunningHeads.BOTTOM).withClock(true)
        assertEquals(MarginSpot.TOP_LEFT to MarginSpot.TOP_RIGHT, foot.spotOf(MarginItem.CLOCK) to foot.spotOf(MarginItem.BATTERY))
        assertEquals(foot, s.aroundCamera())
        // Back at the top, they're together at the foot again: as it started.
        assertEquals(s, foot.withHeads(RunningHeads.TOP).withClock(true))
    }

    @Test
    fun aCutoutAtTheFootIsKeptClearOfToo() {
        val under = Cutouts(411f, 891f, bottom = Cutouts.Span(190f, 221f, 32f))
        assertEquals(34, phone.bottomRowAt(under))
        assertTrue(phone.marginRoom(under).bottom >= 34 + MarginRoom.ROW_HEIGHT)
        assertEquals(Cutouts.NONE, Cutouts.top(0))
    }
}
