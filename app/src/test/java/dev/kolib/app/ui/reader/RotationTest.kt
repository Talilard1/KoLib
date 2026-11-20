package dev.kolib.app.ui.reader

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.comic.TestComics
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.ui.theme.AppSurface
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The readers' rotation choices turn the screen: the comic reader's sheet, and the book reader's button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class RotationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun settle() {
        repeat(8) {
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(60)
            compose.waitForIdle()
        }
    }

    @Test
    fun theComicReadersRotationTurnsTheScreen() {
        val name = "rotation-" + UUID.randomUUID()
        val lib = Library(context, File(context.filesDir, name), "$name.db")
        val file = TestComics.comic(File(context.cacheDir, "$name.cbz"), pages = 4, info = false)
        runBlocking {
            lib.load()
            lib.import("Lantern.cbz", { file.inputStream() }, BookSource.FILE)
        }
        val book = lib.books.value.first()
        compose.setContent {
            AppSurface { ComicBookReader(book = book, library = lib, readerSettings = ReaderSettingsStore(context), onClose = {}) }
        }
        // Opened in the background; then the bars, with a tap in the middle.
        for (i in 0 until 20) {
            settle()
            compose.onRoot().performTouchInput { click(center) }
            settle()
            if (runCatching { compose.onNodeWithTag("comic-rotation-button").assertExists() }.isSuccess) break
        }
        compose.onNodeWithTag("comic-rotation-button").performClick()
        settle()
        compose.onNodeWithText("Landscape").performClick()
        compose.onNodeWithText("Apply").performClick()
        settle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
    }

    @Test
    fun theBookReadersRotationButtonTurnsTheScreen() {
        val f = TestBooks.garden(File(context.cacheDir, "garden-${UUID.randomUUID()}.epub"))
        val files = EpubFileFiles(EpubFile.open(f))
        val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, ReaderSettings(), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        compose.setContent {
            AppSurface {
                ReaderScreen(c, onSettings = { c.applySettings(it) }, onClose = {}, pagePreview = { Box(Modifier.fillMaxSize()) })
            }
        }
        settle()
        compose.onNodeWithTag("reader-rotation").performClick()
        settle()
        compose.onNodeWithTag("reader-rotation-sheet").assertExists()
        compose.onNodeWithTag("reader-rotation-LANDSCAPE").performClick()
        settle()
        assertEquals(ReaderRotation.LANDSCAPE, c.settings.rotation)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        // And back to following the device.
        compose.onNodeWithTag("reader-rotation").performClick()
        settle()
        compose.onNodeWithTag("reader-rotation-AUTO").performClick()
        settle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
    }
}
