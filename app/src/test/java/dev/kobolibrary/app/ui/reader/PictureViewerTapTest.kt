package dev.kobolibrary.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In the picture viewer, a tap beside the picture closes it, as the close button does; one on the picture doesn't. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h700dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class PictureViewerTapTest {

    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    /** A wide picture (400 by 200, fitted to the 400 by 700 screen: rows 250 to 450). */
    private fun show(picture: Result<ImageBitmap>?) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("viewer")) {
                ZoomablePicture(picture, "p", onZoomed = {}, onTapOutside = { closed++ })
            }
        }
    }

    private fun tapAt(x: Float, y: Float) {
        compose.onNodeWithTag("viewer").performTouchInput { click(Offset(x, y)) }
        // A single tap is known only once a second one hasn't come.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun aTapBesideThePictureClosesIt() {
        show(Result.success(ImageBitmap(400, 200)))
        tapAt(200f, 350f)
        assertEquals(0, closed)
        tapAt(20f, 260f)
        assertEquals(0, closed)
        tapAt(200f, 100f)
        assertEquals(1, closed)
        tapAt(200f, 600f)
        assertEquals(2, closed)
    }

    @Test
    fun aDoubleTapZoomsAndDoesntClose() {
        show(Result.success(ImageBitmap(400, 200)))
        compose.onNodeWithTag("viewer").performTouchInput { doubleClick(Offset(200f, 100f)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(0, closed)
        // Zoomed in 2.5 times about the middle, the picture now reaches rows 100 to 600: a tap there is on it.
        tapAt(200f, 150f)
        assertEquals(0, closed)
        tapAt(200f, 30f)
        assertEquals(1, closed)
    }

    @Test
    fun onePictureThatCantBeShownClosesOnAnyTap() {
        show(Result.failure(IllegalStateException("broken")))
        tapAt(200f, 350f)
        assertEquals(1, closed)
    }

    @Test
    fun whileItsReadTapsDoNothing() {
        show(null)
        tapAt(200f, 100f)
        assertEquals(0, closed)
    }
}
