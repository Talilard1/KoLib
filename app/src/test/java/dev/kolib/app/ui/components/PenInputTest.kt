package dev.kolib.app.ui.components

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A pen tap on a text box, where the tip slides a few pixels on the glass, as it does, must open
 * the keyboard while the tablet doesn't take handwriting in text boxes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PenInputTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Keyboard : SoftwareKeyboardController {
        var shown = 0
        override fun show() { shown++ }
        override fun hide() {}
    }

    private fun textBox(handwriting: Handwriting, keyboard: Keyboard) {
        compose.setContent {
            PenTapsOpenTheKeyboard(handwriting) {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    var text by remember { mutableStateOf("") }
                    OutlinedTextField(text, { text = it }, Modifier.testTag("box"))
                }
            }
        }
    }

    /** When the next tap starts: taps a second apart, so two of them never make a double tap. */
    private var nextTapAt = SystemClock.uptimeMillis()

    /** Touches the box with a pen and lifts it [slide] pixels to the right. */
    private fun penTap(slide: Float) {
        val box = compose.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot
        val x = box.center.x
        val y = box.center.y
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val down = nextTapAt
        nextTapAt += 1_000
        compose.runOnUiThread {
            root.dispatchTouchEvent(pen(down, down, MotionEvent.ACTION_DOWN, x, y))
            root.dispatchTouchEvent(pen(down, down + 8, MotionEvent.ACTION_MOVE, x + slide / 2, y))
            root.dispatchTouchEvent(pen(down, down + 16, MotionEvent.ACTION_MOVE, x + slide, y))
            root.dispatchTouchEvent(pen(down, down + 24, MotionEvent.ACTION_UP, x + slide, y))
        }
        compose.waitForIdle()
    }

    private fun pen(down: Long, time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            down, time, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
    }

    @Test
    fun penTapOnAFocusedBoxOpensTheKeyboard() {
        val keyboard = Keyboard()
        textBox({ false }, keyboard)
        penTap(slide = 0f)
        compose.onNodeWithTag("box").assertIsFocused()
        // The keyboard has been put away; the box still has the cursor. A pen that slides a
        // little, as it does, must still bring the keyboard back.
        val before = keyboard.shown
        penTap(slide = 5f)
        assertEquals(before + 1, keyboard.shown)
    }

    @Test
    fun penTapOnAnUnfocusedBoxFocusesIt() {
        textBox({ false }, Keyboard())
        penTap(slide = 5f)
        compose.onNodeWithTag("box").assertIsFocused()
    }

    @Test
    fun withHandwritingOnASlidingPenWritesInstead() {
        // What happened on a Samsung tablet with "S Pen to text" off: Compose takes the slide for
        // handwriting, and the keyboard doesn't come back.
        val keyboard = Keyboard()
        textBox({ true }, keyboard)
        penTap(slide = 0f)
        val before = keyboard.shown
        penTap(slide = 5f)
        assertEquals(before, keyboard.shown)
    }

    @Test
    fun dialogsGetTheSameTreatment() {
        var inWindow = 0f
        var inDialog = 0f
        val off = Handwriting { false }
        compose.setContent {
            PenTapsOpenTheKeyboard(off) {
                inWindow = LocalViewConfiguration.current.handwritingSlop
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    text = {
                        inDialog = LocalViewConfiguration.current.handwritingSlop
                        Text("Name")
                    },
                )
            }
        }
        compose.waitForIdle()
        assertEquals(Float.POSITIVE_INFINITY, inWindow)
        assertEquals(Float.POSITIVE_INFINITY, inDialog)
    }

    @Test
    fun materialsOwnDialogsDontGetIt() {
        // Why the app has its own AlertDialog: a dialog is a window with its own settings.
        var inDialog = 0f
        val off = Handwriting { false }
        compose.setContent {
            PenTapsOpenTheKeyboard(off) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    text = {
                        inDialog = LocalViewConfiguration.current.handwritingSlop
                        Text("Name")
                    },
                )
            }
        }
        compose.waitForIdle()
        assertTrue(inDialog > 0f && inDialog < 100f)
    }

    @Test
    fun onlyTheHandwritingThresholdChanges() {
        val window = object : ViewConfiguration {
            override val longPressTimeoutMillis = 400L
            override val doubleTapTimeoutMillis = 300L
            override val doubleTapMinTimeMillis = 40L
            override val touchSlop = 8f
            override val handwritingSlop = 2f
            override val minimumTouchTargetSize = DpSize(48.dp, 48.dp)
            override val maximumFlingVelocity = 8000f
        }
        var on = false
        val config = PenTapsAreTaps(window) { on }
        assertEquals(Float.POSITIVE_INFINITY, config.handwritingSlop)
        on = true
        assertEquals(2f, config.handwritingSlop)
        assertEquals(8f, config.touchSlop)
        assertEquals(400L, config.longPressTimeoutMillis)
        assertEquals(DpSize(48.dp, 48.dp), config.minimumTouchTargetSize)
        assertEquals(8000f, config.maximumFlingVelocity)
        assertTrue(config.minimumFlingVelocity == window.minimumFlingVelocity)
    }
}
