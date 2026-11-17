package dev.kolib.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The library's and the Kobo's lists, sorted and shown as they were left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListLooksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun listsComeBackSortedAsTheyWereLeft() {
        // Nothing kept yet: the usual order.
        val fresh = LibraryBrowseState().also { ListLooks.restore(context, library = it) }
        assertEquals(LibrarySort.TITLE, fresh.sort)

        val library = LibraryBrowseState().apply {
            sortBy(LibrarySort.SERIES)
            sortBy(LibrarySort.SERIES)
            grid = false
        }
        val kobo = BrowseState().apply { sortBy(KoboSort.ADDED) }
        ListLooks.save(context, ListLooks.Now(library, kobo))

        val libraryAgain = LibraryBrowseState().also { ListLooks.restore(context, library = it) }
        assertEquals(LibrarySort.SERIES, libraryAgain.sort)
        assertTrue(libraryAgain.descending)
        assertFalse(libraryAgain.grid)
        val koboAgain = BrowseState().also { ListLooks.restore(context, kobo = it) }
        assertEquals(KoboSort.ADDED, koboAgain.sort)
        // Date added starts newest first.
        assertTrue(koboAgain.descending)
        assertTrue(koboAgain.grid)
    }
}
