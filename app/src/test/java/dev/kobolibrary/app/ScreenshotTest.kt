package dev.kobolibrary.app

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        // The same root as the app's own (theme and surface), so colours come out as they do on the tablet.
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
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
    ) {
        val (repo, s) = session()
        val browse = BrowseState().apply { deviceTab = tab }.apply(configure)
        val state = UiState(
            connection = Connection.Connected(s, unplugged),
            backups = repo.backups.list(),
            afterConnect = after,
            writeCheck = WriteCheckResult(true, tr("Writing works: a 64 KB test file was written, read back and deleted in {0} ms.", 41)),
            syncing = syncing,
        )
        shot(name, theme) {
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
                state = UiState(connection = Connection.Waiting(false), lastSeen = view),
                browse = browse,
                onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
            )
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun connectWaiting() = shot("11_connect_waiting") {
        ConnectScreen(Connection.Waiting(usbKoboAttached = true), 3, {}, {}, {}, {}, onSettings = {})
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

    // ---- Eject: the ways to do it side by side, and how ejecting over USB goes --------------------

    private val ejectScreens = listOf(
        UnmountScreens.Screen(UnmountScreens.Kind.SETTINGS, android.content.Intent(), "com.android.settings/Settings\$PublicVolumeSettingsActivity"),
        UnmountScreens.Screen(UnmountScreens.Kind.FILES, android.content.Intent(), "com.google.android.documentsui/FilesActivity"),
        UnmountScreens.Screen(UnmountScreens.Kind.MY_FILES, android.content.Intent(), "com.sec.android.app.myfiles/MainActivity"),
    )

    private fun ejectShot(name: String, usb: UsbEjectState? = null, busy: Boolean = false) {
        val (_, s) = session()
        val state = UiState(connection = Connection.Connected(s, false))
        shot(name) {
            DeviceScreen(
                view = KoboView.of(s), unplugged = false, state = state, browse = BrowseState(),
                onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
            )
            EjectDialog(onDismiss = {}, busy = busy, usb = usb, onUsbEject = {}, screens = ejectScreens)
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun ejectChoices() = ejectShot("eject_choices")

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    fun ejectChoicesPhone() = ejectShot("eject_choices_phone")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun ejectWhileSending() = ejectShot("eject_while_sending", busy = true)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun ejectingOverUsb() = ejectShot("eject_running", UsbEjectState(true, tr("Ejecting…")))

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun ejectedOverUsb() = ejectShot(
        "eject_done",
        UsbEjectState(false, tr("Ejected. The Kobo is adding your new books, and you can unplug it whenever you like."), ok = true),
    )

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun ejectTurnedDown() = ejectShot(
        "eject_refused",
        UsbEjectState(
            false,
            tr("The Kobo didn't accept being ejected (sense {0}).", "5/20/00") + " " + tr("Unplug it instead: everything KoLib changed is on it."),
            ok = false,
        ),
    )
}
