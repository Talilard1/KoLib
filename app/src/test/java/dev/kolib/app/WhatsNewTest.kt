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
    private var opening by mutableStateOf(false)

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
                    newsDue = due, onNewsSeen = { told++; due = false }, openingPlaying = opening,
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

    /**
     * Once after each update; but 3.5.1's notes are 3.5's, so only someone coming from before 3.5 (who has no record of a
     * What's new by when it was installed) is shown them.
     */
    @Test
    fun itsDueOnceAfterEachUpdateBut351OnlyFromBefore35() {
        assertTrue(WhatsNew.due("3.5", seen = -1, installed = 5))
        assertTrue(WhatsNew.due("3.5", seen = 3, installed = 5))
        assertTrue(!WhatsNew.due("3.5", seen = 5, installed = 5))
        assertTrue("from before 3.5", WhatsNew.due("3.5.1", seen = -1, installed = 5))
        assertTrue("from 3.5", !WhatsNew.due("3.5.1", seen = 3, installed = 5))
        assertTrue(!WhatsNew.due("3.5.1 preview", seen = 3, installed = 5))
        assertTrue(WhatsNew.due("3.6", seen = 3, installed = 5))
    }

    /** The first thing it says is that the layout's been reworked. */
    @Test
    fun theReworkedLayoutComesFirst() {
        assertEquals(dev.kolib.app.tr("A simpler layout"), WhatsNew.notes("3.5")!!.first().title)
        // 3.5.1 has 3.5's notes: someone updating to it from an earlier KoLib hasn't seen them.
        assertEquals(WhatsNew.notes("3.5")!!.map { it.title }, WhatsNew.notes("3.5.1")!!.map { it.title })
    }

    /** While the opening animation plays, it waits (and so do the tours); once it's done, it shows. */
    @Test
    fun itWaitsForTheOpeningAnimation() {
        opening = true
        show()
        assertTrue(!showing("whats-new"))
        assertTrue(!showing("tour"))
        opening = false
        settle()
        assertTrue(showing("whats-new"))
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
        // The reader changed in this update: going through the reading setup again is suggested, before any tour.
        assertTrue(showing("reader-changed"))
        assertTrue("no tour under it", !showing("tour"))
        compose.onNodeWithTag("reader-changed-later").performClick()
        settle()
        assertTrue(!showing("reader-changed"))
        // The library's tour, now.
        assertTrue(showing("tour"))
    }

    /** After the update's What's new, the reading setup is a tap away. */
    @Test
    fun afterTheUpdateTheReadingSetupIsSuggested() {
        assertTrue(WhatsNew.suggestsReadingSetup("3.5") && WhatsNew.suggestsReadingSetup("3.5 preview"))
        assertTrue(WhatsNew.suggestsReadingSetup("3.5.1") && WhatsNew.suggestsReadingSetup("3.5.1 preview"))
        assertTrue(!WhatsNew.suggestsReadingSetup("3.6"))
        show()
        compose.onNodeWithTag("whats-new-done").performClick()
        settle()
        compose.onNodeWithTag("reader-changed-setup").performClick()
        settle()
        assertTrue(!showing("reader-changed"))
        assertTrue("the reading setup", showing("setup-card"))
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
        assertTrue("only after an update", !showing("reader-changed"))
        assertEquals("nothing to remember", 0, told)
    }
}
