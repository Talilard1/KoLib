package dev.kobolibrary.app

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
import dev.kobolibrary.app.kobo.AfterConnect
import dev.kobolibrary.app.kobo.CoverSize
import dev.kobolibrary.app.kobo.KoboRepository
import dev.kobolibrary.app.kobo.KoboSession
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.kobo.WriteCheckResult
import dev.kobolibrary.app.ui.BackupsScreen
import dev.kobolibrary.app.ui.BrowseState
import dev.kobolibrary.app.ui.KoboExtras
import dev.kobolibrary.app.ui.ConnectScreen
import dev.kobolibrary.app.ui.DeviceScreen
import dev.kobolibrary.app.ui.EjectDialog
import dev.kobolibrary.app.kobo.UnmountScreens
import dev.kobolibrary.app.ui.LogScreen
import dev.kobolibrary.app.ui.NO_COLLECTION_KEY
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val LANDSCAPE = "w1280dp-h800dp-land-mdpi"
private const val PORTRAIT = "w800dp-h1280dp-port-mdpi"
private const val LANDSCAPE_NIGHT = "w1280dp-h800dp-land-night-mdpi"
private const val PORTRAIT_NIGHT = "w800dp-h1280dp-port-night-mdpi"

/** Renders every screen at the Galaxy Tab S9's size so the layout can be checked by eye. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var after: AfterConnect? = null

    private fun session(): Pair<KoboRepository, KoboSession> = runBlocking {
        val root = File(context.cacheDir, "fake-kobo").also { FakeKobo.create(it) }
        val repo = KoboRepository(context)
        val s = repo.connect(FakeDocumentsProvider.install(root)) {}
        after = repo.afterConnect(s)
        // Load covers up front so the screenshot doesn't race the background loading.
        for (b in s.snapshot.books) {
            s.covers.load(b.imageId, CoverSize.THUMBNAIL, 300)
            s.covers.load(b.imageId, CoverSize.FULL, 600)
        }
        repo to s
    }

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun inLanguage() = TestLanguage.apply()

    /**
     * The clock is paused so endless animations (progress spinners) can't keep the UI from settling;
     * it is then moved on a second so everything has laid out.
     */
    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, act: (() -> Unit)? = null, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        // The same root as the app's own (theme and surface), so colours come out as they do on the tablet.
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        if (act != null) {
            act()
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    private fun libraryCopies(vararg paths: String, tags: List<String> = emptyList()) = paths.map { p ->
        dev.kobolibrary.app.library.LibraryBook(
            id = p, meta = dev.kobolibrary.app.epub.BookMetadata(title = p.substringAfterLast('/'), tags = tags), collections = emptyList(),
            fileSize = 0, hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
            source = dev.kobolibrary.app.library.BookSource.KOBO, originalName = null, koboPath = p,
        )
    }

    private fun deviceScreen(
        name: String,
        tab: Int = 0,
        collection: String? = null,
        configure: BrowseState.() -> Unit = {},
        unplugged: Boolean = false,
        extras: KoboExtras = KoboExtras(),
        theme: ThemeChoice = ThemeChoice.SYSTEM,
        syncing: Boolean = false,
        usbKobo: UsbKobo = UsbKobo.NONE,
        act: (() -> Unit)? = null,
    ) {
        val (repo, s) = session()
        val browse = BrowseState().apply { deviceTab = tab }.apply(configure)
        val state = UiState(
            connection = Connection.Connected(s, unplugged),
            backups = repo.backups.list(),
            afterConnect = after,
            writeCheck = WriteCheckResult(true, tr("Writing works: a 64 KB test file was written, read back and deleted in {0} ms.", 41)),
            syncing = syncing,
            usbKobo = usbKobo,
        )
        shot(name, theme, act) {
            DeviceScreen(
                view = KoboView.of(s),
                unplugged = unplugged,
                state = state,
                browse = browse,
                onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
                extras = extras,
                initialCollectionKey = collection,
            )
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun booksGridWithDetails() = deviceScreen("01_books_grid_landscape", configure = {
        selectedBookId = FakeKobo.books.first { it.title == "The Histories" }.contentId
    })

    @Test
    @Config(qualifiers = PORTRAIT)
    fun booksGridPortrait() = deviceScreen("02_books_grid_portrait")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun bookDetailPortrait() = deviceScreen("03_book_detail_portrait", configure = {
        selectedBookId = FakeKobo.books.first { it.title == "Meditations" }.contentId
    })

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun booksListSortedByAuthor() = deviceScreen("04_books_list_landscape", configure = {
        grid = false
        sort = dev.kobolibrary.app.ui.KoboSort.AUTHOR
        selectedBookId = FakeKobo.books.first { it.title == "The Sign of the Four" }.contentId
    })

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun collections() = deviceScreen("05_collections_landscape", tab = 1, collection = "Sherlock Holmes")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun collectionsPortrait() = deviceScreen("06_collections_portrait", tab = 1)

    /** The Kobo's tabs can be swiped between: Books, then Collections, then Device, and back. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun swipedBetweenTabs() {
        var browse: BrowseState? = null
        deviceScreen("05c_swiped_to_device", configure = { browse = this }, act = {
            repeat(2) {
                compose.onRoot().performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.2f) }
                compose.mainClock.advanceTimeBy(1_500)
            }
        })
        assertEquals(2, browse!!.deviceTab)
        compose.onRoot().performTouchInput { swipeRight(startX = width * 0.2f, endX = width * 0.85f) }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(1, browse!!.deviceTab)
    }

    /** A book tapped in a collection opens on the right; the collections and the collection's books stay where they are. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun collectionBookOpen() = deviceScreen("05b_collection_book_landscape", tab = 1, collection = "Sherlock Holmes", configure = {
        collectionBookId = FakeKobo.books.first { it.title == "The Sign of the Four" }.contentId
    })

    /** The same, standing up: the collections stay down the left, and the book takes its collection's place, with a way back to it. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun collectionBookOpenPortrait() = deviceScreen("06b_collection_book_portrait", tab = 1, collection = "Sherlock Holmes", configure = {
        collectionBookId = FakeKobo.books.first { it.title == "The Sign of the Four" }.contentId
    })

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun notInACollection() = deviceScreen("07_no_collection_landscape", tab = 1, collection = NO_COLLECTION_KEY)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun deviceTab() = deviceScreen("08_device_landscape", tab = 2)

    @Test
    @Config(qualifiers = LANDSCAPE_NIGHT)
    fun booksDark() = deviceScreen("09_books_dark_landscape", configure = {
        selectedBookId = FakeKobo.books.first { it.title == "The Sign of the Four" }.contentId
    })

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun unplugged() = deviceScreen("10_unplugged_landscape", unplugged = true, configure = {
        query = "homer"
    })

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun choosingBooksForTheLibrary() = deviceScreen(
        "17_kobo_choosing_books",
        extras = KoboExtras(libraryCopies("Books/Homer/The Odyssey.kepub.epub", "Books/Marcus Aurelius/Meditations.kepub.epub")),
        configure = {
            selecting = true
            source = dev.kobolibrary.app.ui.SourceFilter.SIDELOADED
            listOf("The Histories", "The Iliad", "Dracula").forEach { t -> picked[FakeKobo.books.first { it.title == t }.contentId] = true }
        },
    )

    /** After a restart with the Kobo unplugged: what was on it last time, with covers kept on the tablet. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun lastSeenWhileUnplugged() {
        val (repo, s) = session()
        // The covers were copied to the tablet while it was connected.
        runBlocking { s.covers.keepThumbnails(s.snapshot.books.map { it.imageId }) { true } }
        repo.saveLastSeen(s)
        val view = repo.loadLastSeen()!!
        runBlocking { for (b in view.snapshot.books) view.covers.load(b.imageId, CoverSize.THUMBNAIL, 300) }
        val browse = BrowseState().apply { selectedBookId = FakeKobo.books.first { it.title == "The Histories" }.contentId }
        shot("18_kobo_last_seen") {
            DeviceScreen(
                view = view,
                unplugged = false,
                state = UiState(connection = Connection.Waiting(), lastSeen = view),
                browse = browse,
                onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
            )
        }
    }

    private fun statsBooks(): Pair<List<dev.kobolibrary.app.library.LibraryBook>, Map<String, dev.kobolibrary.app.reader.ReadHere>> {
        val titles = listOf("The Histories" to 189_000, "War and Peace" to 587_000, "The Republic" to 118_000, "Meditations" to 60_000, "The Odyssey" to 130_000)
        val books = titles.map { (t, words) ->
            dev.kobolibrary.app.library.LibraryBook(
                id = t, meta = dev.kobolibrary.app.epub.BookMetadata(title = t), collections = emptyList(), fileSize = 1, hasCover = false, coverVersion = 0,
                addedAt = 0, modifiedAt = 0, source = dev.kobolibrary.app.library.BookSource.FILE, originalName = null, koboPath = null, words = words,
            )
        }
        val at = java.time.LocalDate.of(2026, 11, 17).atTime(20, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val here = mapOf(
            "The Histories" to dev.kobolibrary.app.reader.ReadHere(0.38f, at),
            "War and Peace" to dev.kobolibrary.app.reader.ReadHere(0.12f, at - 86_400_000),
            "Meditations" to dev.kobolibrary.app.reader.ReadHere(1f, at - 5 * 86_400_000L, finished = true),
        )
        return books to here
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun readingStats() {
        val (_, s) = session()
        val (books, here) = statsBooks()
        val today = java.time.LocalDate.of(2026, 11, 17)
        val log = dev.kobolibrary.app.ui.reader.sampleReadingLog(File(context.cacheDir, "stats-log.json"), listOf("The Histories", "War and Peace", "Meditations"), today)
        shot("84_reading_stats") {
            dev.kobolibrary.app.ui.ReadingStatsScreen(books, here, log.books.value, kobo = s.snapshot, koboLive = false, onBack = {}, today = today)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun readingStatsPhoneWithNothingRead() {
        val (books, _) = statsBooks()
        shot("85_reading_stats_phone_empty") {
            dev.kobolibrary.app.ui.ReadingStatsScreen(books, emptyMap(), emptyMap(), kobo = null, koboLive = false, onBack = {})
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun connectWaiting() = shot("11_connect_waiting") {
        ConnectScreen(Connection.Waiting(UsbKobo.OPENING), 3, {}, {}, {}, {}, onSettings = {})
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun connectWorking() = shot("12_connect_working_portrait") {
        ConnectScreen(Connection.Working("Copying the Kobo's database…"), 0, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun connectFailed() = shot("13_connect_failed") {
        ConnectScreen(
            Connection.Failed(
                "That drive has no .kobo folder, so it doesn't look like a Kobo. Choose the top level of the drive called KOBOeReader.",
                null,
            ),
            1, {}, {}, {}, {},
        )
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun backups() {
        val (repo, _) = session()
        shot("14_backups") { BackupsScreen(repo.backups.list(), {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun log() {
        session()
        shot("15_log") { LogScreen(onBack = {}, onMessage = {}) }
    }

    /** What was unreadable in 0.3.0: the progress text while connecting, in dark mode. */
    @Test
    @Config(qualifiers = PORTRAIT_NIGHT)
    fun connectDark() {
        shot("16_connect_dark_portrait") {
            ConnectScreen(Connection.Working("Copying the Kobo's database… 42%"), 0, {}, {}, {}, {})
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun koboInTheBookcaseLook() = deviceScreen("42_kobo_bookcase", theme = ThemeChoice.BOOKCASE, configure = {
        selectedBookId = FakeKobo.books.first { it.title == "The Histories" }.contentId
    })

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun savingToTheKobosDatabase() = deviceScreen("43_kobo_saving", syncing = true)

    // ---- Eject: the one screen that unmounts the Kobo ----------------------------------------------

    private val settingsPage = UnmountScreens.Screen(
        UnmountScreens.Kind.SETTINGS, android.content.Intent(), "com.android.settings/Settings\$PublicVolumeSettingsActivity",
    )

    private fun ejectShot(name: String, busy: Boolean = false) {
        val (_, s) = session()
        val state = UiState(connection = Connection.Connected(s, false))
        shot(name) {
            DeviceScreen(
                view = KoboView.of(s), unplugged = false, state = state, browse = BrowseState(),
                onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
            )
            EjectDialog(onDismiss = {}, busy = busy, screen = settingsPage)
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun ejectDialog() = ejectShot("eject_dialog")

    /** Unmounted in Android's settings, and still plugged in. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun safeToUnplug() = deviceScreen("eject_safe_to_unplug", unplugged = true, usbKobo = UsbKobo.UNMOUNTED)

    /** Plugged in again and Connect tapped: Android is opening the Kobo's drive. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun koboOpening() = deviceScreen("kobo_opening", unplugged = true, usbKobo = UsbKobo.OPENING)

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    fun ejectDialogPhone() = ejectShot("eject_dialog_phone")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun ejectWhileSending() = ejectShot("eject_while_sending", busy = true)
}
