package dev.kolib.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.kolib.app.StorageUse
import dev.kolib.app.ui.theme.AppSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StorageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** What KoLib takes up in all, and a line for each kind of thing it keeps. */
    @Test
    fun itShowsWhatKoLibTakesUpAndByWhat() {
        val use = StorageUse(
            listOf(StorageUse.Kind.BOOKS to 3_000_000L, StorageUse.Kind.COMICS to 5_000_000L, StorageUse.Kind.APP to 2_000_000L),
            total = 100_000_000L, free = 40_000_000L,
        )
        compose.setContent { AppSurface { StorageScreen(onBack = {}, measure = { use }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("storage-books").assertExists()
        compose.onNodeWithTag("storage-comics").assertExists()
        compose.onNodeWithTag("storage-app").assertExists()
        compose.onNodeWithTag("storage-kolib").assertExists()
    }
}
