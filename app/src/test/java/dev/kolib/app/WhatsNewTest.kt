package dev.kolib.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.ui.AppRoot
import dev.kolib.app.ui.WhatsNew
import dev.kolib.app.ui.theme.KoLibTheme
import dev.kolib.app.ui.tour.Tours
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** What's new: shown once after an update, before any tour, and again from More. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhatsNewTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<KoLibApp>()
    private var due by mutableStateOf(true)
    private var seen by mutableStateOf(setOf(Tours.SAMPLES))
    private var told = 0

    @Before
    fun setUp() {
        TestLanguage.apply()
        runBlocking {
            if (!app.library.loaded) app.library.load()
            val f = TestBooks.garden(File(app.cacheDir, "g-${UUID.randomUUID()}.epub"))
            app.library.import("garden.epub", { f.inputStream() }, BookSource.FILE)
        }
    }

    private fun show() {
        val vm = MainViewModel(app)
        val libraryVm = LibraryViewModel(app)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            KoLibTheme {
                AppRoot(
                    vm, libraryVm, toursSeen = seen, onTourSeen = { seen = seen + it },
                    newsDue = due, onNewsSeen = { told++; due = false },
                )
            }
        }
        settle()
    }

    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
        }
    }

    private fun showing(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun thisVersionHasNotes() {
        val notes = WhatsNew.notes(BuildConfig.VERSION_NAME)
        assertTrue("notes for ${BuildConfig.VERSION_NAME}", !notes.isNullOrEmpty())
        assertEquals(null, WhatsNew.notes("0.1"))
    }

    @Test
    fun afterAnUpdateItShowsOnceAndTheToursWaitForIt() {
        show()
        assertTrue(showing("whats-new"))
        assertTrue("no tour under it", !showing("tour"))
        compose.onNodeWithTag("whats-new-done").performClick()
        settle()
        assertEquals(1, told)
        assertTrue(!showing("whats-new"))
        // The library's tour, now.
        assertTrue(showing("tour"))
    }

    @Test
    fun withoutAnUpdateItIsntShownButMoreHasIt() {
        assumeTrue(TestLanguage.code == null)
        due = false
        seen = setOf(Tours.SAMPLES, Tours.LIBRARY)
        show()
        assertTrue(!showing("whats-new"))
        compose.onNodeWithTag("nav-more").performClick()
        settle()
        compose.onNodeWithTag("more-whats-new").performClick()
        settle()
        assertTrue(showing("whats-new"))
        compose.onNodeWithText("What's new in KoLib ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithTag("whats-new-done").performClick()
        settle()
        assertTrue(!showing("whats-new"))
        assertEquals("nothing to remember", 0, told)
    }
}
