package dev.kolib.app

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The app starts from its icon, with and without the opening, and gets through it to the library. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartTest {

    private fun start(opening: Boolean) {
        val app = ApplicationProvider.getApplicationContext<KoLibApp>()
        app.themeSettings.setOpening(opening)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val controller = Robolectric.buildActivity(MainActivity::class.java, launch).setup()
        val activity = controller.get()
        // Through the whole opening and out the other side.
        repeat(75) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        }
        assertTrue(activity.window.decorView.isAttachedToWindow)
        assertEquals(R.style.Theme_KoLib, shadowOf(activity).callGetThemeResId())
        controller.pause().stop().destroy()
    }

    @Test fun withTheOpening() = start(opening = true)

    @Test fun withoutTheOpening() = start(opening = false)
}
