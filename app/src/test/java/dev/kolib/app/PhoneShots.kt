package dev.kolib.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.kobo.CoverSize
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboRepository
import dev.kolib.app.kobo.KoboSession
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.kobo.SendFormat
import dev.kolib.app.kobo.WriteCheckResult
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.ImportOutcome
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.ui.AppFrame
import dev.kolib.app.ui.BrowseState
import dev.kolib.app.ui.ConnectScreen
import dev.kolib.app.ui.DeviceScreen
import dev.kolib.app.ui.KoboExtras
import dev.kolib.app.ui.Section
import dev.kolib.app.ui.edit.EditDetailsScreen
import dev.kolib.app.ui.edit.EditSuggestions
import dev.kolib.app.ui.library.JobDialog
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibraryScreen
import dev.kolib.app.ui.library.LibrarySort
import dev.kolib.app.ui.library.SendDialog
import dev.kolib.app.ui.library.SplitBookDialog
import dev.kolib.app.ui.theme.AppSurface
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.ui.AboutScreen
import dev.kolib.app.ui.cover.CoverColors
import dev.kolib.app.ui.cover.CoverDesign
import dev.kolib.app.ui.cover.CoverEditorScreen
import dev.kolib.app.ui.cover.CoverGeneratorScreen
import dev.kolib.app.ui.cover.CoverRendering
import dev.kolib.app.ui.cover.CoverStyle
import dev.kolib.app.ui.cover.CoverText
import dev.kolib.app.ui.reader.Panel
import dev.kolib.app.ui.reader.ReaderScreen
import dev.kolib.app.ui.textedit.TextEditState
import dev.kolib.app.ui.textedit.TextEditorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import dev.kolib.app.ui.theme.ThemeChoice
import kotlinx.coroutines.runBlocking
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

/** A phone: 1080 x 2400 pixels at 420 dpi. */
private const val PHONE = "w411dp-h914dp-port-420dpi"

/** A narrow phone, or a large display size setting: 360 dp across. */
private const val SMALL = "w360dp-h780dp-port-xxhdpi"

private const val STORY = "The Adventures of Sherlock Holmes"

/**
 * Every main screen at a phone's size, with real public-domain covers, to look over the phone layout.
 * Pictures go to /home/claude/phone/<variant>, where the variant is read from variant.txt there.
 * Only runs where those files are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneShots {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private val art = File("/home/claude/advert")
    private val home = File("/home/claude/phone")

    /** The language to take the pictures in (fr, de, ja...), from language.txt there; English if there's none. */
    private val language: String? = File(home, "language.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    private val out: File get() = language?.let { File(home, "lang_$it") }
        ?: File(home, File(home, "variant.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null } ?: "after")

    @Before
    fun inLanguage() {
        val lang = language ?: return
        val table = HashMap<String, String>()
        File("src/main/assets/i18n/$lang.json").reader(Charsets.UTF_8).use { input ->
            android.util.JsonReader(input).use { r ->
                r.beginObject()
                while (r.hasNext()) table[r.nextName()] = r.nextString()
                r.endObject()
            }
        }
        L10n.use(lang, table)
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(lang))
    }

    private fun B(
        title: String, author: String, cover: String, status: Int = 0, percent: Int = 0, lastRead: String? = null,
        collections: List<String> = emptyList(), series: String? = null, number: String? = null, tags: List<String> = emptyList(),
        description: String? = null,
    ) = Triple(FakeKobo.Book(title, author, series, number, status, percent, lastRead, collections, tags = tags, description = description), cover, title)

    private val shelf = listOf(
        B("Moby Dick", "Herman Melville", "mobydick", 1, 34, "2026-11-12T21:40:00Z", listOf("Sea Stories", "Classics"), tags = listOf("Adventure", "The Sea"),
            description = "<p>Ishmael signs on to the whaler <i>Pequod</i>, and Captain Ahab's hunt for the white whale begins.</p>"),
        B("Pride and Prejudice", "Jane Austen", "pride", 2, 100, "2026-08-20T20:00:00Z", listOf("Austen", "Classics"), tags = listOf("Romance", "Classics")),
        B("Emma", "Jane Austen", "emma", collections = listOf("Austen")),
        B("Persuasion", "Jane Austen", "persuasion", collections = listOf("Austen", "To Read")),
        B("The Iliad", "Homer", "iliad", 2, 100, "2026-05-02T19:00:00Z", listOf("Epics", "Classics"), tags = listOf("Poetry", "Ancient Greece")),
        B("The Odyssey", "Homer", "odyssey", 1, 58, "2026-11-08T20:10:00Z", listOf("Epics", "Classics"), tags = listOf("Poetry", "Ancient Greece")),
        B("The Aeneid", "Virgil", "aeneid", collections = listOf("Epics")),
        B("The Divine Comedy", "Dante Alighieri", "divinecomedy", collections = listOf("Epics", "To Read")),
        B("Frankenstein", "Mary Shelley", "frankenstein", 2, 100, "2025-10-31T23:00:00Z", listOf("Gothic")),
        B("Dracula", "Bram Stoker", "dracula", 1, 12, "2026-10-29T22:15:00Z", listOf("Gothic")),
        B("The Picture of Dorian Gray", "Oscar Wilde", "doriangray", collections = listOf("Gothic")),
        B("Wuthering Heights", "Emily Brontë", "wuthering", collections = listOf("Gothic", "Classics")),
        B("Jane Eyre", "Charlotte Brontë", "janeeyre", 1, 71, "2026-11-10T21:00:00Z", listOf("Classics")),
        B("The Hound of the Baskervilles", "Arthur Conan Doyle", "hound", 2, 100, "2026-03-11T22:30:00Z", listOf("Sherlock Holmes"), "Sherlock Holmes", "5", tags = listOf("Mystery")),
        B("The Count of Monte Cristo", "Alexandre Dumas", "montecristo", 1, 81, "2026-10-20T18:00:00Z"),
        B("Treasure Island", "Robert Louis Stevenson", "treasure", collections = listOf("Sea Stories")),
        B("The Wind in the Willows", "Kenneth Grahame", "willows", 2, 100, "2026-07-14T20:00:00Z", listOf("Cosy")),
        B("Anne of Green Gables", "L. M. Montgomery", "anne", collections = listOf("Cosy")),
        B("Little Women", "Louisa May Alcott", "littlewomen", collections = listOf("Cosy")),
        B("The Secret Garden", "Frances Hodgson Burnett", "secretgarden", collections = listOf("Cosy")),
        B("Walden", "Henry David Thoreau", "walden", collections = listOf("Philosophy")),
        B("Meditations", "Marcus Aurelius", "meditations", 2, 100, "2026-09-02T21:14:00Z", listOf("Philosophy")),
        B("The Art of War", "Sun Tzu", "artofwar", collections = listOf("Philosophy")),
        B("The Time Machine", "H. G. Wells", "timemachine", collections = listOf("Science Fiction")),
        B("The War of the Worlds", "H. G. Wells", "worlds", collections = listOf("Science Fiction")),
        B("Great Expectations", "Charles Dickens", "greatexpectations"),
        B("A Christmas Carol", "Charles Dickens", "carol", collections = listOf("Cosy")),
        B("A Tale of Two Cities", "Charles Dickens", "twocities"),
        B("Les Misérables", "Victor Hugo", "miserables", collections = listOf("To Read")),
        B("War and Peace", "Leo Tolstoy", "warandpeace", collections = listOf("To Read")),
        B("The Brothers Karamazov", "Fyodor Dostoevsky", "karamazov"),
        B("Don Quixote", "Miguel de Cervantes Saavedra", "quixote"),
        B("Middlemarch", "George Eliot", "middlemarch"),
        B("Alice's Adventures in Wonderland", "Lewis Carroll", "alice", collections = listOf("Cosy")),
        B("The Great Gatsby", "F. Scott Fitzgerald", "gatsby"),
    )

    private val designed = setOf("hound", "artofwar", "carol", "meditations", "walden", "timemachine", "worlds")

    private fun cover(file: String): ByteArray = File(art, if (file in designed) "designed/$file.jpg" else "covers/$file.jpg").readBytes()

    private var koboBooks: Map<String, KoboBook> = emptyMap()

    private fun <T> withShelf(block: () -> T): T {
        assumeTrue(File(art, "covers/mobydick.jpg").exists() && File(art, "books/acd_messy.epub").exists())
        FakeKobo.bookList = shelf.map { it.first }
        FakeKobo.collectionList = shelf.flatMap { it.first.collections }.distinct().sorted()
        FakeKobo.coverArt = shelf.associate { (b, file, _) -> b.title to cover(file) }
        try {
            return block()
        } finally {
            FakeKobo.bookList = null
            FakeKobo.collectionList = null
            FakeKobo.coverArt = emptyMap()
        }
    }

    /** The library filled from the Kobo, plus the Sherlock Holmes stories added from a file, with a designed cover. */
    private fun library(): Pair<Library, LibraryCovers> = runBlocking {
        withShelf {
            runBlocking {
                val drive = File(context.cacheDir, "ph-kobo-" + UUID.randomUUID()).also { FakeKobo.create(it) }
                val session = KoboRepository(context).connect(FakeDocumentsProvider.install(drive)) {}
                koboBooks = session.snapshot.booksById
                val lib = Library(context, File(context.filesDir, "ph-" + UUID.randomUUID()), null)
                lib.load()
                lib.rememberKoboCollections(session.snapshot.collections.map { it.name })
                for (b in session.snapshot.books.filter { it.isSideloaded }) lib.importFromKobo(session, b)
                val file = File(art, "books/acd_messy.epub")
                val story = (lib.import("$STORY.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
                lib.updateDetails(
                    story.id,
                    story.meta.copy(series = "Sherlock Holmes", seriesIndex = 3.0, tags = listOf("Mystery", "Detective stories", "Short stories"), publisher = "Standard Ebooks", published = "1892-10-14"),
                    listOf("Sherlock Holmes"),
                )
                lib.updateCover(story.id, File(art, "designed/sherlock_new.jpg").readBytes(), "image/jpeg", 1072, 1448)
                val covers = LibraryCovers(lib)
                for (b in lib.books.value) {
                    covers.load(b, false, 400)
                    covers.load(b, true, 900)
                }
                lib to covers
            }
        }
    }

    private fun Library.titled(title: String): LibraryBook = books.value.first { it.title == title }

    private fun capture(name: String) {
        out.mkdirs()
        captureScreenRoboImage(File(out, "$name.png").path)
    }

    private fun shot(name: String, theme: ThemeChoice, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        capture(name)
    }

    @Composable
    private fun LibraryPage(lib: Library, covers: LibraryCovers, browse: LibraryBrowseState, books: List<LibraryBook> = lib.books.value) {
        AppFrame(Section.LIBRARY, {}, koboConnected = true, onNarrow = {}, onLook = {}) {
            LibraryScreen(
                books = books,
                covers = covers,
                browse = browse,
                job = null,
                koboConnected = true,
                onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                onKobo = koboBooks,
                koboLive = true,
                onTheme = {},
                readingHere = if (books.isEmpty()) emptyMap() else mapOf(
                    lib.titled("Moby Dick").id to ReadHere(0.36f, 1_794_400_000_000L),
                    lib.titled("Alice's Adventures in Wonderland").id to ReadHere(0.62f, 1_794_300_000_000L),
                    lib.titled("The Aeneid").id to ReadHere(1f, 1_794_200_000_000L, finished = true),
                ),
            )
        }
    }

    private fun libraryIn(name: String, theme: ThemeChoice = ThemeChoice.BOOKCASE, configure: LibraryBrowseState.(Library) -> Unit = {}) {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { configure(lib) }
        shot(name, theme) { LibraryPage(lib, covers, browse) }
    }

    // ---- the library ----------------------------------------------------------------------------

    @Test @Config(qualifiers = PHONE) fun libraryGrid() = libraryIn("p01_library")
    @Test @Config(qualifiers = PHONE) fun libraryLight() = libraryIn("p02_library_light", ThemeChoice.LIGHT)
    @Test @Config(qualifiers = PHONE) fun libraryList() = libraryIn("p03_library_list") { grid = false }
    @Test @Config(qualifiers = PHONE) fun libraryFiltered() = libraryIn("p04_library_filtered") { collection = "Classics"; sortBy(LibrarySort.LAST_READ) }
    @Test @Config(qualifiers = PHONE) fun book() = libraryIn("p05_book") { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = PHONE) fun bookLight() = libraryIn("p05_book_light", ThemeChoice.LIGHT) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = PHONE) fun libraryDarkLook() = libraryIn("p02_library_dark", ThemeChoice.DARK)
    @Test @Config(qualifiers = PHONE) fun libraryOledLook() = libraryIn("p02_library_oled", ThemeChoice.OLED)
    @Test @Config(qualifiers = PHONE) fun bookDarkLook() = libraryIn("p05_book_dark", ThemeChoice.DARK) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = PHONE) fun bookOledLook() = libraryIn("p05_book_oled", ThemeChoice.OLED) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = PHONE) fun bookLongTitle() = libraryIn("p06_book_story") { selectedId = it.titled(STORY).id }
    @Test @Config(qualifiers = PHONE) fun selecting() = libraryIn("p07_selecting") {
        selecting = true
        listOf("Moby Dick", "Emma", "Dracula").forEach { t -> picked[it.titled(t).id] = true }
    }
    @Test @Config(qualifiers = PHONE) fun search() = libraryIn("p08_library_search") { query = "austen" }

    @Test
    @Config(qualifiers = PHONE)
    fun emptyLibrary() {
        val (lib, covers) = library()
        shot("p09_library_empty", ThemeChoice.BOOKCASE) { LibraryPage(lib, covers, LibraryBrowseState(), books = emptyList()) }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun addBooks() {
        val (lib, covers) = library()
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.BOOKCASE) { LibraryPage(lib, covers, LibraryBrowseState()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(tr("Add books")).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        capture("p10_add_books")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun sortMenu() {
        val (lib, covers) = library()
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.BOOKCASE) { LibraryPage(lib, covers, LibraryBrowseState()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(tr("Sort")).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        capture("p11_sort")
    }

    // ---- dialogs over the library ----------------------------------------------------------------

    private fun overBook(name: String, over: @Composable (Library, LibraryCovers, LibraryBook) -> Unit) {
        val (lib, covers) = library()
        val book = lib.titled(STORY)
        val browse = LibraryBrowseState().apply { selectedId = book.id }
        shot(name, ThemeChoice.BOOKCASE) {
            LibraryPage(lib, covers, browse)
            over(lib, covers, book)
        }
    }

    @Test @Config(qualifiers = PHONE)
    fun send() = overBook("p20_send") { _, _, book ->
        SendDialog(listOf(book), connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB, onSend = { _, _, _ -> }, onConnect = {}, onDismiss = {}, pathOnKobo = { null })
    }

    @Test @Config(qualifiers = SMALL)
    fun sendSmall() = overBook("s20_send") { _, _, book ->
        SendDialog(listOf(book), connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB, onSend = { _, _, _ -> }, onConnect = {}, onDismiss = {}, pathOnKobo = { null })
    }

    @Test @Config(qualifiers = PHONE)
    fun sendProgress() = overBook("p21_send_progress") { _, _, _ ->
        JobDialog(JobProgress(JobKind.SEND, "Sending to the Kobo", total = 3, done = 1, current = STORY, currentFraction = 0.6f, startedAt = 0), onHide = {}, onStop = {}, onDone = {}, onHowToEject = {})
    }


    @Test
    @Config(qualifiers = PHONE)
    fun split() {
        val (lib, covers) = library()
        val book = lib.titled(STORY)
        val pages = runBlocking { lib.pagesOf(book.id) }
        shot("p23_split", ThemeChoice.BOOKCASE) {
            LibraryPage(lib, covers, LibraryBrowseState().apply { selectedId = book.id })
            SplitBookDialog(book, loadPages = { pages }, onSplit = { _, _, _, _, _ -> }, onDismiss = {}, initialPages = pages)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun details() {
        val (lib, covers) = library()
        val book = lib.titled(STORY)
        val suggestions = EditSuggestions.from(lib.books.value, lib.allCollections(), lib.koboCollections.value)
        shot("p24_details", ThemeChoice.BOOKCASE) { EditDetailsScreen(book, covers, suggestions, saving = false, onSave = { _, _ -> }, onClose = {}) }
    }

    // ---- the Kobo ---------------------------------------------------------------------------------

    private fun kobo(): Pair<KoboRepository, KoboSession> = withShelf {
        runBlocking {
            val drive = File(context.cacheDir, "ph-kobo-" + UUID.randomUUID()).also { FakeKobo.create(it) }
            val repo = KoboRepository(context)
            val s = repo.connect(FakeDocumentsProvider.install(drive)) {}
            for (b in s.snapshot.books) for (px in listOf(400, 900)) {
                s.covers.load(b.imageId, CoverSize.THUMBNAIL, px)
                s.covers.load(b.imageId, CoverSize.FULL, px)
            }
            repo to s
        }
    }

    private fun koboScreen(name: String, tab: Int = 0, collection: String? = null, theme: ThemeChoice = ThemeChoice.BOOKCASE, configure: BrowseState.(KoboSession) -> Unit = {}) {
        val (repo, s) = kobo()
        val after = runBlocking { repo.afterConnect(s) }
        val browse = BrowseState().apply { deviceTab = tab }.apply { configure(s) }
        val state = UiState(
            connection = Connection.Connected(s, false),
            backups = repo.backups.list(),
            afterConnect = after,
            writeCheck = WriteCheckResult(true, "Writing works: a 64 KB test file was written, read back and deleted in 41 ms."),
        )
        shot(name, theme) {
            AppFrame(Section.KOBO, {}, koboConnected = true, onNarrow = {}, onLook = {}) {
                DeviceScreen(
                    view = KoboView.of(s), unplugged = false, state = state, browse = browse,
                    onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
                    extras = KoboExtras(), initialCollectionKey = collection,
                )
            }
        }
    }

    @Test @Config(qualifiers = PHONE) fun koboBooks() = koboScreen("p30_kobo_books")
    @Test @Config(qualifiers = PHONE) fun koboBooksLight() = koboScreen("p30_kobo_books_light", theme = ThemeChoice.LIGHT)
    @Test @Config(qualifiers = PHONE) fun koboBook() = koboScreen("p31_kobo_book") { s -> selectedBookId = s.snapshot.books.first { it.title == "The Odyssey" }.contentId }
    @Test @Config(qualifiers = PHONE) fun koboCollections() = koboScreen("p32_kobo_collections", tab = 1)
    @Test @Config(qualifiers = PHONE) fun koboCollection() = koboScreen("p33_kobo_collection", tab = 1, collection = "Classics")
    @Test @Config(qualifiers = PHONE) fun koboDevice() = koboScreen("p34_kobo_device", tab = 2)
    @Test @Config(qualifiers = PHONE) fun koboSelecting() = koboScreen("p35_kobo_selecting") { s ->
        selecting = true
        s.snapshot.books.filter { it.title in setOf("Emma", "Dracula") }.forEach { picked[it.contentId] = true }
    }
    @Test @Config(qualifiers = SMALL) fun koboBooksSmall() = koboScreen("s30_kobo_books")

    @Test
    @Config(qualifiers = PHONE)
    fun connect() = shot("p36_connect", ThemeChoice.BOOKCASE) {
        AppFrame(Section.KOBO, {}, koboConnected = false, onNarrow = {}, onLook = {}) {
            ConnectScreen(Connection.Waiting(), backupCount = 3, onChooseDrive = {}, onRetry = {}, onOpenBackups = {}, onOpenLog = {}, onSettings = {})
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun libraryMenu() {
        val (lib, covers) = library()
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.BOOKCASE) { LibraryPage(lib, covers, LibraryBrowseState()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(tr("More options")).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        capture("p12_menu")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun koboMenu() {
        val (repo, s) = kobo()
        val state = UiState(connection = Connection.Connected(s, false), backups = repo.backups.list())
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                AppFrame(Section.KOBO, {}, koboConnected = true, onNarrow = {}, onLook = {}) {
                    DeviceScreen(
                        view = KoboView.of(s), unplugged = false, state = state, browse = BrowseState(),
                        onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(tr("More")).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        capture("p37_kobo_menu")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun coverEditor() {
        val (lib, _) = library()
        val book = lib.titled("Moby Dick")
        val bytes = runBlocking { lib.originalCover(book.id) }!!
        val picture = CoverRendering.prepare(CoverRendering.decode(bytes)!!, tr("The book's cover"), bytes)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                CoverEditorScreen(book, loadOriginal = { null }, saving = false, onSave = {}, onClose = {}, initialPicture = picture)
            }
        }
        repeat(4) { compose.waitForIdle(); Thread.sleep(250) }
        capture("p25_cover_editor")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun coverGenerator() {
        val text = CoverText(STORY, listOf("Arthur Conan Doyle"), "Sherlock Holmes", 3.0)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                CoverGeneratorScreen(text, onUse = { _, _ -> }, onCancel = {}, initial = CoverDesign(CoverStyle.KEYHOLE, CoverColors.themes.first { it.name == tr("Teal") }, dev.kolib.app.ui.cover.CoverGenre.DETECTIVE))
            }
        }
        repeat(12) { compose.waitForIdle(); Thread.sleep(250) }
        capture("p26_cover_generator")
    }

    @Test @Config(qualifiers = PHONE) fun about() = shot("p27_about", ThemeChoice.BOOKCASE) { AboutScreen(onBack = {}) }

    /** The launcher icon as Android draws it, and its one-colour layer for themed icons. */
    @Test
    @Config(qualifiers = PHONE)
    fun appIcon() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as android.graphics.drawable.AdaptiveIconDrawable
        val size = 432
        fun save(name: String, draw: (android.graphics.Canvas) -> Unit) {
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            draw(android.graphics.Canvas(bmp))
            out.mkdirs()
            File(out, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        save("p00_icon") { c -> icon.setBounds(0, 0, size, size); icon.draw(c) }
        save("p00_icon_layers") { c ->
            icon.background.setBounds(0, 0, size, size); icon.background.draw(c)
            icon.foreground.setBounds(0, 0, size, size); icon.foreground.draw(c)
        }
        save("p00_icon_mono") { c ->
            c.drawColor(0xFF2B3A55.toInt())
            icon.monochrome!!.apply { setTint(0xFFD3E4FF.toInt()); setBounds(0, 0, size, size); draw(c) }
        }
    }

    // ---- the reader and the text editor ----------------------------------------------------------------

    /** Waits for the book's chapters to be measured, so the reader can number pages through the whole book. */
    private fun counted(c: ReaderController) {
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(1200) {
            if (c.lengthsCounted) return
            looper.idle()
            Thread.sleep(25)
        }
    }

    /**
     * What the page is told as Moby Dick's first chapter opens with the reader as it comes: for laying the
     * page out in Chrome at the phone's size (render_now.py there), which [reader] then shows.
     */
    @Test
    @Config(qualifiers = PHONE)
    fun readerStart() {
        val f = File(art, "books/herman-melville_moby-dick.epub")
        assumeTrue(f.exists() && home.exists())
        EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "Moby Dick", "en", null, ReaderSettings(), context.assets, "Herman Melville")
            counted(c)
            val at = c.chapters.indexOfFirst { it.path.endsWith("chapter-1.xhtml") }
            File(home, "start").mkdirs()
            File(home, "start/moby.json").writeText(c.startForPreview(at))
        }
    }

    private fun reader(name: String, controls: Boolean, panel: Panel = Panel.NONE, lookTab: Int = 0) {
        val f = File(art, "books/herman-melville_moby-dick.epub")
        val pageNow = File(home, "pages/moby_now_1.png")
        assumeTrue(f.exists() && pageNow.exists())
        EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            val structure = BookStructure(files)
            // The reader as it comes, over the chapter's first page as the app's own reader lays it out.
            val settings = ReaderSettings()
            val c = ReaderController(files, structure, "Moby Dick", "en", null, settings, context.assets, "Herman Melville")
            val at = c.chapters.indexOfFirst { it.path.endsWith("chapter-1.xhtml") }
            counted(c)
            val pages = File(home, "pages/moby_now_pages.txt").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 8
            c.showForPreview(at, 0, pages)
            c.controlsVisible = controls
            c.lookTab = lookTab
            val img = BitmapFactory.decodeFile(pageNow.path).asImageBitmap()
            compose.mainClock.autoAdvance = false
            compose.setContent {
                AppSurface(ThemeChoice.BOOKCASE) {
                    ReaderScreen(
                        c, onSettings = {}, onClose = {}, onEdit = {},
                        pagePreview = { Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) },
                        initialPanel = panel,
                        onAppSettings = {},
                    )
                }
            }
            compose.mainClock.advanceTimeBy(50)
            c.showForPreview(at, 0, pages)
            compose.mainClock.advanceTimeBy(900)
            capture(name)
        }
    }

    @Test @Config(qualifiers = PHONE) fun readerControls() = reader("p40_reader_controls", controls = true)

    // ---- for the front page: the comic reader, and the AI's summary of the chapter ---------------------------------

    /** The sample comic, with the comic reader's bars showing. */
    @Test @Config(qualifiers = PHONE)
    fun comicReader() {
        assumeTrue(home.exists())
        val comic = openSampleComic(context)
        val settings = dev.kolib.app.comic.ComicSettings()
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                dev.kolib.app.ui.reader.ComicView(
                    comic = comic, s = settings, mode = settings.mode, menu = true, setMenu = {}, onSettings = {}, onClose = {},
                    onCover = { _, _ -> }, readingHere = null, startPage = 1,
                )
            }
        }
        repeat(40) { compose.waitForIdle(); Thread.sleep(100) }
        capture("p50_comic_reader")
        comic.pages.close()
    }

    private val aiScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /** The first chapter of Moby Dick summarised in the AI's window. */
    @Test @Config(qualifiers = PHONE)
    fun aiSummary() {
        val f = File(art, "books/herman-melville_moby-dick.epub")
        val pageNow = File(home, "pages/moby_now_1.png")
        assumeTrue(f.exists() && pageNow.exists())
        answeringModel(context, aiScope) { m -> if (m.last().content.contains("Summarise it")) MOBY_SUMMARY else "" }
        EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "Moby Dick", "en", null, ReaderSettings(), context.assets, "Herman Melville")
            val at = c.chapters.indexOfFirst { it.path.endsWith("chapter-1.xhtml") }
            counted(c)
            val pages = File(home, "pages/moby_now_pages.txt").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 8
            c.showForPreview(at, 0, pages)
            c.controlsVisible = true
            val img = BitmapFactory.decodeFile(pageNow.path).asImageBitmap()
            compose.mainClock.autoAdvance = true
            compose.setContent {
                AppSurface(ThemeChoice.BOOKCASE) {
                    ReaderScreen(
                        c, onSettings = {}, onClose = {}, onEdit = {},
                        pagePreview = { Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) },
                        onAppSettings = {},
                    )
                }
            }
            compose.waitForIdle()
            c.showForPreview(at, 0, pages)
            compose.waitForIdle()
            compose.summariseTheChapter(context, whole = true)
            capture("p60_ai_summary")
        }
    }

    @Test @Config(qualifiers = PHONE) fun readerLook() = reader("p41_reader_look", controls = true, panel = Panel.LOOK, lookTab = 0)
    @Test @Config(qualifiers = PHONE) fun readerLookPage() = reader("p42_reader_look_page", controls = true, panel = Panel.LOOK, lookTab = 1)
    @Test @Config(qualifiers = PHONE) fun readerLookControls() = reader("p47_reader_look_controls", controls = true, panel = Panel.LOOK, lookTab = 3)
    @Test @Config(qualifiers = PHONE) fun readerContents() = reader("p43_reader_contents", controls = true, panel = Panel.CONTENTS)

    private fun editor(name: String, chapter: String?, configure: (TextEditState) -> Unit = {}) {
        val lib = Library(context, File(context.filesDir, "ph-ed-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val epub = File(art, "books/acd_messy.epub")
        assumeTrue(epub.exists())
        runBlocking { lib.import("$STORY.epub", { epub.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        val state = TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, chapter)
        configure(state)
        val page = File(art, "pages/ed_before.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path).asImageBitmap() }
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE7E3DA)), contentAlignment = Alignment.TopCenter) {
                        if (page != null) Image(page, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter)
                    }
                })
            }
        }
        compose.waitForIdle()
        capture(name)
    }

    @Test @Config(qualifiers = PHONE) fun editorPage() = editor("p44_editor", "epub/text/a-scandal-in-bohemia.xhtml")
    @Test @Config(qualifiers = PHONE) fun editorChapters() {
        editor("p45_editor_chapters_tmp", "epub/text/a-scandal-in-bohemia.xhtml")
        compose.onNodeWithText("A Scandal in Bohemia").performClick()
        compose.waitForIdle()
        capture("p45_editor_chapters")
        File(out, "p45_editor_chapters_tmp.png").delete()
    }
    @Test @Config(qualifiers = PHONE) fun editorFind() = editor("p46_editor_find", "epub/text/a-scandal-in-bohemia.xhtml") { st ->
        st.find.open = true
        st.find.query = "tbe"
        st.find.replacement = "the"
    }

    // ---- a narrow phone ------------------------------------------------------------------------------

    @Test @Config(qualifiers = SMALL) fun readerLookSmall() = reader("s41_reader_look", controls = true, panel = Panel.LOOK, lookTab = 0)
    @Test @Config(qualifiers = SMALL) fun readerControlsSmall() = reader("s40_reader_controls", controls = true)
    @Test @Config(qualifiers = SMALL) fun librarySmall() = libraryIn("s01_library")
    @Test @Config(qualifiers = SMALL) fun bookSmall() = libraryIn("s05_book") { selectedId = it.titled(STORY).id }
    @Test @Config(qualifiers = SMALL) fun selectingSmall() = libraryIn("s07_selecting") {
        selecting = true
        listOf("Moby Dick", "Emma").forEach { t -> picked[it.titled(t).id] = true }
    }
}
