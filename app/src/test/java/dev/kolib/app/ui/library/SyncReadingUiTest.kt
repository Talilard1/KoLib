package dev.kolib.app.ui.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.kobo.BookKind
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.ReadStatus
import dev.kolib.app.kobo.SendFormat
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.ui.theme.AppSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Syncing reading with the Kobo from the library: a book's own button, the choice of all or some, and when sending. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class SyncReadingUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun onKobo(status: ReadStatus, percent: Int) = KoboBook(
        contentId = "x", title = "x", author = null, subtitle = null, series = null, seriesNumber = null, description = null,
        publisher = null, language = null, isbn = null, mimeType = null, format = "KEPUB", imageId = "x", kind = BookKind.SIDELOADED,
        isDownloaded = true, isExpired = false, readStatus = status, percentRead = percent, lastReadMillis = 2_000L, addedMillis = null,
        fileSize = null, relativePath = "x",
    )

    private fun book(title: String, format: String = "EPUB") = LibraryBook(
        id = title,
        meta = BookMetadata(title = title, authors = listOf("Herodotus")),
        collections = emptyList(), fileSize = 1000, hasCover = false, coverVersion = 1, addedAt = 0, modifiedAt = 0,
        source = BookSource.FILE, originalName = null, koboPath = null, sentAt = null, words = 1000,
    ).let { if (format == "EPUB") it else it.copy(sourceFormat = format) }

    @Test
    fun aBooksOwnButtonSyncsIt() {
        var synced = 0
        compose.setContent { AppSurface { ReadingLine(onKobo(ReadStatus.READING, 37), live = true, onSync = { synced++ }) } }
        compose.onNodeWithText("Sync with the Kobo").assertExists()
        compose.onNodeWithTag("sync-reading").performClick()
        assertEquals(1, synced)
    }

    @Test
    fun noButtonWithoutAWayToSync() {
        compose.setContent { AppSurface { ReadingLine(onKobo(ReadStatus.READING, 37), live = true) } }
        compose.onAllNodesWithTag("sync-reading").assertCountEquals(0)
    }

    @Test
    fun allOrSomeBooksAreSynced() {
        var all = 0
        var choose = 0
        var cancelled = 0
        compose.setContent { AppSurface { SyncReadingDialog(3, onAll = { all++ }, onChoose = { choose++ }, onDismiss = { cancelled++ }) } }
        compose.onNodeWithText("3 books are in your library and on the Kobo.", substring = true).assertExists()
        compose.onNodeWithTag("sync-reading-all").performClick()
        compose.onNodeWithText("Choose books").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(1, 1, 1), listOf(all, choose, cancelled))
    }

    @Test
    fun oneBookIsSaidSo() {
        compose.setContent { AppSurface { SyncReadingDialog(1, onAll = {}, onChoose = {}, onDismiss = {}) } }
        compose.onNodeWithText("1 book is in your library and on the Kobo.", substring = true).assertExists()
    }

    @Test
    fun sendingCanSyncTheirReadingToo() {
        var chosen: Boolean? = null
        var sent = 0
        compose.setContent {
            AppSurface {
                SendDialog(
                    listOf(book("The Histories")), connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB,
                    onSend = { _, _, _ -> sent++ }, onConnect = {}, onDismiss = {}, pathOnKobo = { null },
                    initialSyncReading = false, onSyncReading = { chosen = it },
                )
            }
        }
        val switch = compose.onNodeWithTag("send-sync-reading")
        switch.performScrollTo().assertIsOff()
        switch.performClick()
        switch.assertIsOn()
        compose.onNodeWithText("Send").performClick()
        assertEquals(true, chosen)
        assertEquals(1, sent)
    }

    @Test
    fun theChoiceIsRememberedAndCanBeTurnedOff() {
        var chosen: Boolean? = null
        compose.setContent {
            AppSurface {
                SendDialog(
                    listOf(book("The Histories")), connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB,
                    onSend = { _, _, _ -> }, onConnect = {}, onDismiss = {}, pathOnKobo = { null },
                    initialSyncReading = true, onSyncReading = { chosen = it },
                )
            }
        }
        val switch = compose.onNodeWithTag("send-sync-reading")
        switch.performScrollTo().assertIsOn()
        switch.performClick()
        compose.onNodeWithText("Send").performClick()
        assertEquals(false, chosen)
    }

    @Test
    fun comicsAloneHaveNoReadingToSync() {
        compose.setContent {
            AppSurface {
                SendDialog(
                    listOf(book("Akira", format = "CBZ")), connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB,
                    onSend = { _, _, _ -> }, onConnect = {}, onDismiss = {}, pathOnKobo = { null },
                    initialSyncReading = true, onSyncReading = {},
                )
            }
        }
        compose.onAllNodesWithTag("send-sync-reading").assertCountEquals(0)
    }
}
