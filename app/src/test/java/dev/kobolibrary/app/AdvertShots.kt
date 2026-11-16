package dev.kobolibrary.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kobolibrary.app.library.ImportOutcome
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.epub.BookAssembly
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboRepository
import dev.kobolibrary.app.kobo.SendFormat
import dev.kobolibrary.app.kobo.CoverSize
import dev.kobolibrary.app.kobo.KoboSession
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.kobo.WriteCheckResult
import dev.kobolibrary.app.reader.Annotation
import dev.kobolibrary.app.reader.HighlightColor
import dev.kobolibrary.app.ui.BrowseState
import dev.kobolibrary.app.ui.DeviceScreen
import dev.kobolibrary.app.ui.KoboExtras
import dev.kobolibrary.app.ui.LookDialog
import dev.kobolibrary.app.ui.cover.CoverColors
import dev.kobolibrary.app.ui.cover.CoverDesign
import dev.kobolibrary.app.ui.cover.CoverGeneratorScreen
import dev.kobolibrary.app.ui.cover.CoverStyle
import dev.kobolibrary.app.ui.cover.CoverText
import dev.kobolibrary.app.ui.library.JobDialog
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.reader.ReadHere
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReaderFlow
import dev.kobolibrary.app.reader.ReaderFont
import dev.kobolibrary.app.reader.ReaderSettings
import dev.kobolibrary.app.reader.ReaderTheme
import dev.kobolibrary.app.ui.AppFrame
import dev.kobolibrary.app.ui.Section
import dev.kobolibrary.app.ui.cover.CoverEditorScreen
import dev.kobolibrary.app.ui.cover.CoverRendering
import dev.kobolibrary.app.ui.edit.EditDetailsScreen
import dev.kobolibrary.app.ui.edit.EditSuggestions
import dev.kobolibrary.app.ui.library.LibraryBrowseState
import dev.kobolibrary.app.ui.library.LibraryScreen
import dev.kobolibrary.app.ui.library.LibrarySort
import dev.kobolibrary.app.ui.library.MergeDialog
import dev.kobolibrary.app.ui.library.SendDialog
import dev.kobolibrary.app.ui.library.SplitBookDialog
import dev.kobolibrary.app.ui.reader.Panel
import dev.kobolibrary.app.ui.reader.ReaderScreen
import dev.kobolibrary.app.ui.textedit.TextEditState
import dev.kobolibrary.app.ui.textedit.TextEditorScreen
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** The Galaxy Tab S9's screen, at its real pixel size. */
private const val TAB = "w1280dp-h800dp-land-xhdpi"

/** A phone with a 1080 x 2400 screen. */
private const val PHONE = "w360dp-h800dp-port-xxhdpi"

private const val STORY = "The Adventures of Sherlock Holmes"
private const val STORY_FILE = "acd_adventures"
private const val STORY_CHAPTER = "a-scandal-in-bohemia.xhtml"

/**
 * Pictures of the app for its advert, at the Tab S9's 2560 x 1600: real public-domain books (Standard
 * Ebooks covers and covers made by the app's own designer, and pages laid out by the app's reader in
 * Chrome). One book, The Adventures of Sherlock Holmes, is followed through the app: it's added from a
 * badly made file, given its details and a new cover, cleaned up, read, and sent to the Kobo.
 * Only runs where those files are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvertShots {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private val root = File("/home/claude/advert")
    private val out = File(root, "app5")

    private fun B(
        title: String, author: String, cover: String, status: Int = 0, percent: Int = 0, lastRead: String? = null,
        collections: List<String> = emptyList(), series: String? = null, number: String? = null, tags: List<String> = emptyList(),
        description: String? = null,
    ) = Triple(FakeKobo.Book(title, author, series, number, status, percent, lastRead, collections, tags = tags, description = description), cover, title)

    /** What's on the Kobo. The story's book isn't: it's added to the library from a file. */
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

    /** Books whose covers were made with the app's cover designer; the rest keep their painted ones. */
    private val designed = setOf("hound", "artofwar", "carol", "meditations", "walden", "timemachine", "worlds")

    private fun art(file: String): ByteArray = File(root, if (file in designed) "designed/$file.jpg" else "covers/$file.jpg").readBytes()

    private var koboBooks: Map<String, KoboBook> = emptyMap()

    /**
     * A library filled from a Kobo holding the shelf above, plus the story's book added from its badly
     * made file, with the cover it came with or ([newCover]) the one designed for it.
     */
    private fun library(newCover: Boolean = false): Pair<Library, LibraryCovers> = runBlocking {
        assumeTrue(File(root, "covers/mobydick.jpg").exists() && File(root, "books/acd_messy.epub").exists())
        FakeKobo.bookList = shelf.map { it.first }
        FakeKobo.collectionList = shelf.flatMap { it.first.collections }.distinct().sorted()
        FakeKobo.coverArt = shelf.associate { (b, file, _) -> b.title to art(file) }
        try {
            val drive = File(context.cacheDir, "ad-kobo-" + UUID.randomUUID()).also { FakeKobo.create(it) }
            val session = KoboRepository(context).connect(FakeDocumentsProvider.install(drive)) {}
            koboBooks = session.snapshot.booksById
            val lib = Library(context, File(context.filesDir, "ad-" + UUID.randomUUID()), null)
            lib.load()
            lib.rememberKoboCollections(session.snapshot.collections.map { it.name })
            for (b in session.snapshot.books.filter { it.isSideloaded }) lib.importFromKobo(session, b)
            val file = File(root, "books/acd_messy.epub")
            val story = (lib.import("$STORY.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
            lib.updateDetails(
                story.id,
                story.meta.copy(series = "Sherlock Holmes", seriesIndex = 3.0, tags = listOf("Mystery", "Detective stories", "Short stories"), publisher = "Standard Ebooks", published = "1892-10-14"),
                listOf("Sherlock Holmes"),
            )
            if (newCover) lib.updateCover(story.id, File(root, "designed/sherlock_new.jpg").readBytes(), "image/jpeg", 1072, 1448)
            val covers = LibraryCovers(lib)
            for (b in lib.books.value) {
                covers.load(b, false, 600)
                covers.load(b, true, 1200)
            }
            lib to covers
        } finally {
            FakeKobo.bookList = null
            FakeKobo.collectionList = null
            FakeKobo.coverArt = emptyMap()
        }
    }

    private fun Library.titled(title: String): LibraryBook = books.value.first { it.title == title }

    private fun capture(name: String) {
        out.mkdirs()
        captureScreenRoboImage(File(out, "$name.png").path)
    }

    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.BOOKCASE, settle: Long = 1_000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(settle)
        capture(name)
    }

    @Composable
    private fun LibraryPage(lib: Library, covers: LibraryCovers, browse: LibraryBrowseState) {
        AppFrame(Section.LIBRARY, {}, koboConnected = true, onNarrow = {}) {
            LibraryScreen(
                books = lib.books.value,
                covers = covers,
                browse = browse,
                job = null,
                koboConnected = true,
                onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                onKobo = koboBooks,
                koboLive = true,
                readingHere = mapOf(
                    lib.titled("Moby Dick").id to ReadHere(0.36f, 1_794_400_000_000L),
                    lib.titled("Alice's Adventures in Wonderland").id to ReadHere(0.62f, 1_794_300_000_000L),
                    lib.titled("The Aeneid").id to ReadHere(0.18f, 1_794_200_000_000L),
                    lib.titled(STORY).id to ReadHere(0.02f, 1_794_100_000_000L),
                ),
            )
        }
    }

    // ---- the library ---------------------------------------------------------------------------

    private fun libraryIn(name: String, theme: ThemeChoice = ThemeChoice.BOOKCASE, newCover: Boolean = false, configure: LibraryBrowseState.(Library) -> Unit = {}) {
        val (lib, covers) = library(newCover)
        val browse = LibraryBrowseState().apply { configure(lib) }
        shot(name, theme) { LibraryPage(lib, covers, browse) }
    }

    @Test @Config(qualifiers = TAB) fun libMoby() = libraryIn("lib_moby") { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libMobyLight() = libraryIn("lib_moby_light", ThemeChoice.LIGHT) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libMobyDark() = libraryIn("lib_moby_dark", ThemeChoice.DARK) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libMobyOled() = libraryIn("lib_moby_oled", ThemeChoice.OLED) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libSearch1() = libraryIn("lib_q1") { query = "sh"; selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libSearch2() = libraryIn("lib_q2") { query = "sherl"; selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libSearch3() = libraryIn("lib_q3") { query = "sherlock"; selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libSearchStory() = libraryIn("lib_q3_story") { query = "sherlock"; selectedId = it.titled(STORY).id }
    @Test @Config(qualifiers = TAB) fun libSearchStoryNew() = libraryIn("lib_q3_story_new", newCover = true) { query = "sherlock"; selectedId = it.titled(STORY).id }
    @Test @Config(qualifiers = TAB) fun libStoryNew() = libraryIn("lib_story_new", newCover = true) { selectedId = it.titled(STORY).id }

    // ---- the Kobo -------------------------------------------------------------------------------

    /** A Kobo holding the shelf, connected. */
    private fun kobo(): Pair<KoboRepository, KoboSession> = runBlocking {
        assumeTrue(File(root, "covers/mobydick.jpg").exists())
        FakeKobo.bookList = shelf.map { it.first }
        FakeKobo.collectionList = shelf.flatMap { it.first.collections }.distinct().sorted()
        FakeKobo.coverArt = shelf.associate { (b, file, _) -> b.title to art(file) }
        try {
            val drive = File(context.cacheDir, "ad-kobo-" + UUID.randomUUID()).also { FakeKobo.create(it) }
            val repo = KoboRepository(context)
            val s = repo.connect(FakeDocumentsProvider.install(drive)) {}
            for (b in s.snapshot.books) for (px in listOf(300, 600, 1200)) {
                s.covers.load(b.imageId, CoverSize.THUMBNAIL, px)
                s.covers.load(b.imageId, CoverSize.FULL, px)
            }
            repo to s
        } finally {
            FakeKobo.bookList = null
            FakeKobo.collectionList = null
            FakeKobo.coverArt = emptyMap()
        }
    }

    private fun koboScreen(name: String, tab: Int = 0, collection: String? = null, configure: BrowseState.(KoboSession) -> Unit = {}) {
        val (repo, s) = kobo()
        val after = runBlocking { repo.afterConnect(s) }
        val browse = BrowseState().apply { deviceTab = tab }.apply { configure(s) }
        val state = UiState(
            connection = Connection.Connected(s, false),
            backups = repo.backups.list(),
            afterConnect = after,
            writeCheck = WriteCheckResult(true, "Writing works: a 64 KB test file was written, read back and deleted in 41 ms."),
        )
        shot(name) {
            AppFrame(Section.KOBO, {}, koboConnected = true, onNarrow = {}) {
                DeviceScreen(
                    view = KoboView.of(s), unplugged = false, state = state, browse = browse,
                    onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
                    extras = KoboExtras(), initialCollectionKey = collection,
                )
            }
        }
    }

    @Test @Config(qualifiers = TAB) fun koboBooks() = koboScreen("kobo_books") { s -> selectedBookId = s.snapshot.books.first { it.title == "The Odyssey" }.contentId }
    @Test @Config(qualifiers = TAB) fun koboCollections() = koboScreen("kobo_collections", tab = 1, collection = "Classics")

    // ---- details and the cover -------------------------------------------------------------------

    @Test
    @Config(qualifiers = TAB)
    fun details() {
        val (lib, covers) = library()
        val book = lib.titled(STORY)
        val suggestions = EditSuggestions.from(lib.books.value, lib.allCollections(), lib.koboCollections.value)
        shot("details") { EditDetailsScreen(book, covers, suggestions, saving = false, onSave = { _, _ -> }, onClose = {}) }
    }

    /** The cover editor on the cover the book came with, in each way of fitting it to the Kobo's screen. */
    @Test
    @Config(qualifiers = TAB)
    fun coverFits() {
        val (lib, _) = library()
        val book = lib.titled(STORY)
        val bytes = runBlocking { lib.originalCover(book.id) }!!
        val picture = CoverRendering.prepare(CoverRendering.decode(bytes)!!, "The book's cover", bytes)
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                CoverEditorScreen(book, loadOriginal = { null }, saving = false, onSave = {}, onClose = {}, initialPicture = picture)
            }
        }
        fun settle() = repeat(4) { compose.waitForIdle(); Thread.sleep(250) }
        settle(); capture("cover_fill")
        for ((label, name) in listOf("Blur the gaps" to "cover_blur", "White gaps" to "cover_white", "Leave unchanged" to "cover_asis")) {
            compose.onNodeWithText(label).performClick()
            settle(); capture(name)
        }
    }

    private val storyText = CoverText(STORY, listOf("Arthur Conan Doyle"), "Sherlock Holmes", 3.0)
    private val houndText = CoverText("The Hound of the Baskervilles", listOf("Arthur Conan Doyle"), "Sherlock Holmes", 5.0)

    private fun generator(name: String, style: CoverStyle, colors: CoverColors, text: CoverText = storyText) {
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.BOOKCASE) { CoverGeneratorScreen(text, onUse = { _, _ -> }, onCancel = {}, initial = CoverDesign(style, colors)) } }
        repeat(12) { compose.waitForIdle(); Thread.sleep(250) }
        capture(name)
    }

    private fun theme(name: String) = CoverColors.themes.first { it.name == name }

    @Test @Config(qualifiers = TAB) fun genLabel() = generator("gen_label", CoverStyle.LABEL, theme("Midnight"))
    @Test @Config(qualifiers = TAB) fun genSunrise() = generator("gen_sunrise", CoverStyle.SUNRISE, theme("Midnight"))
    @Test @Config(qualifiers = TAB) fun genSplit() = generator("gen_split", CoverStyle.SPLIT, theme("Midnight"))
    @Test @Config(qualifiers = TAB) fun genMosaic() = generator("gen_mosaic", CoverStyle.MOSAIC, theme("Midnight"))
    @Test @Config(qualifiers = TAB) fun genBold() = generator("gen_bold", CoverStyle.BOLD, theme("Midnight"))
    @Test @Config(qualifiers = TAB) fun genClassic() = generator("gen_classic", CoverStyle.CLASSIC, theme("Midnight"))
    @Test @Config(qualifiers = TAB) fun genClassicClaret() = generator("gen_classic_claret", CoverStyle.CLASSIC, theme("Claret"))
    @Test @Config(qualifiers = TAB) fun genClassicForest() = generator("gen_classic_forest", CoverStyle.CLASSIC, theme("Forest"))
    @Test @Config(qualifiers = TAB) fun genClassicParchment() = generator("gen_classic_parchment", CoverStyle.CLASSIC, theme("Parchment"))
    @Test @Config(qualifiers = TAB) fun genClassicSaffron() = generator("gen_classic_saffron", CoverStyle.CLASSIC, theme("Saffron"))

    // the same book the first advert made a cover for
    @Test @Config(qualifiers = TAB) fun houndGen1() = generator("hound_gen_1", CoverStyle.LABEL, theme("Parchment"), houndText)
    @Test @Config(qualifiers = TAB) fun houndGen2() = generator("hound_gen_2", CoverStyle.SUNRISE, theme("Forest"), houndText)
    @Test @Config(qualifiers = TAB) fun houndGen3() = generator("hound_gen_3", CoverStyle.MOSAIC, theme("Slate"), houndText)
    @Test @Config(qualifiers = TAB) fun houndGen4() = generator("hound_gen_4", CoverStyle.BOLD, theme("Claret"), houndText)
    @Test @Config(qualifiers = TAB) fun houndGen5() = generator("hound_gen_5", CoverStyle.CLASSIC, theme("Midnight"), houndText)

    // ---- editing the text ------------------------------------------------------------------------

    private fun pageImage(name: String, spare: String? = null) =
        BitmapFactory.decodeFile((File(root, "pages/$name.png").takeIf { it.exists() } ?: File(root, "pages/$spare.png")).path).asImageBitmap()

    /**
     * The badly made book through the editor: a scanning mistake replaced everywhere, then each clean-up,
     * then a chapter's menu. The chapters as the app left them are written to books/acd_cleaned, for the
     * page that shows behind the later pictures.
     */
    @Test
    @Config(qualifiers = TAB)
    fun editorStory() {
        val lib = Library(context, File(context.filesDir, "ad-ed-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val epub = File(root, "books/acd_messy.epub")
        assumeTrue(epub.exists())
        runBlocking { lib.import("$STORY.epub", { epub.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        val state = TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "epub/text/$STORY_CHAPTER")
        var page by mutableStateOf("ed_before")
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = {
                    val img = remember(page) { pageImage(page, "ed_before") }
                    Box(Modifier.fillMaxSize().background(Color(0xFFE7E3DA)), contentAlignment = Alignment.TopCenter) {
                        Image(img, null, Modifier.padding(top = 20.dp).width(640.dp), contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter)
                    }
                })
            }
        }
        compose.waitForIdle()
        capture("ed_main")

        // find & replace: "tbe" for "the", as a scanner reads it
        compose.runOnIdle {
            state.find.open = true
            state.find.query = "tbe"
            state.find.replacement = "the"
            state.find.wholeWords = true
        }
        compose.waitForIdle()
        capture("ed_find_typed")
        compose.runOnIdle { state.find.search() }
        compose.waitUntil(20_000) { state.find.entries.isNotEmpty() || state.find.error != null }
        check(state.find.error == null) { "search failed: ${state.find.error}" }
        compose.waitForIdle()
        capture("ed_find")
        compose.runOnIdle { state.find.replaceAll() }
        state.awaitWork()
        compose.waitUntil(20_000) { state.find.pendingCount == 0 }
        page = "ed_mid"
        compose.waitForIdle()
        capture("ed_find_done")
        compose.runOnIdle { state.find.close() }
        compose.waitForIdle()

        // clean-ups, one after another
        compose.onNodeWithContentDescription("Clean-ups").performClick()
        fun counted() {
            repeat(60) {
                compose.waitForIdle()
                if (compose.onAllNodesWithText("Checking…").fetchSemanticsNodes().isEmpty()) return
                Thread.sleep(250)
            }
        }
        counted()
        capture("ed_clean_0")
        val kinds = dev.kobolibrary.app.epub.Cleanups.Kind.entries
        for (k in kinds.indices) {
            compose.onAllNodesWithText("Fix")[k].performClick()
            state.awaitWork()
            if (k == kinds.lastIndex) page = "ed_after"
            counted()
            capture("ed_clean_${k + 1}")
        }
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        capture("ed_tidy")

        // the chapters as the app left them
        val cleaned = File(root, "books/acd_cleaned")
        if (cleaned.isDirectory) {
            for (c in state.structure.chapters) state.edit.text(c.path)?.let { File(cleaned, c.path).writeText(it) }
        }

        compose.onNodeWithContentDescription("Options for “A Scandal in Bohemia”").performClick()
        compose.waitForIdle()
        capture("ed_menu")
    }

    @Test
    @Config(qualifiers = TAB)
    fun split() {
        val (lib, covers) = library(newCover = true)
        val book = lib.titled(STORY)
        val pages = runBlocking { lib.pagesOf(book.id) }
        val browse = LibraryBrowseState().apply { query = "sherlock"; selectedId = book.id }
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                LibraryPage(lib, covers, browse)
                SplitBookDialog(book, loadPages = { pages }, onSplit = { _, _, _, _ -> }, onDismiss = {}, initialPages = pages)
            }
        }
        compose.waitForIdle()
        capture("split_0")
        for (title in listOf("The Redheaded League", "A Case of Identity", "The Boscombe Valley Mystery", "The Five Orange Pips", "The Man with the Twisted Lip", "The Adventure of the Blue Carbuncle", "The Adventure of the Speckled Band")) {
            compose.onNodeWithText(title).performClick()
            compose.waitForIdle()
        }
        capture("split")
    }

    @Test
    @Config(qualifiers = TAB)
    fun combine() {
        val (lib, covers) = library(newCover = true)
        val books = listOf(lib.titled(STORY), lib.titled("The Hound of the Baskervilles"))
        val browse = LibraryBrowseState().apply {
            query = "sherlock"
            selecting = true
            books.forEach { picked[it.id] = true }
        }
        shot("merge") {
            LibraryPage(lib, covers, browse)
            MergeDialog(books, covers, onMerge = { _, _, _, _ -> }, onDismiss = {})
        }
    }

    // ---- the reader ---------------------------------------------------------------------------

    private fun <T> withBook(file: String, block: (EpubFileFiles, BookStructure) -> T): T {
        val f = File(root, "books/$file.epub")
        assumeTrue(f.exists())
        return EpubFile.open(f).use { epub ->
            val files = EpubFileFiles(epub)
            block(files, BookStructure(files))
        }
    }

    /** The reader over a page laid out by the app's reader in Chrome. */
    private fun reader(
        name: String, settings: ReaderSettings, page: String, pageIndex: Int, pages: Int, controls: Boolean = false, panel: Panel = Panel.NONE,
        file: String = STORY_FILE, title: String = STORY, author: String = "Arthur Conan Doyle", chapter: String = STORY_CHAPTER,
        prepare: (ReaderController) -> Unit = {},
    ) = withBook(file) { files, structure ->
        val c = ReaderController(files, structure, title, "en", null, settings, context.assets, author)
        val at = c.chapters.indexOfFirst { it.path.endsWith(chapter) }
        c.showForPreview(at, pageIndex, pages)
        c.controlsVisible = controls
        prepare(c)
        val img = pageImage(page)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                ReaderScreen(
                    c, onSettings = {}, onClose = {}, onEdit = {},
                    pagePreview = { Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) },
                    initialPanel = panel,
                )
            }
        }
        // The reader starts opening the chapter (there's no WebView here to do it): shown as opened again,
        // so its "still opening" spinner doesn't come up.
        compose.mainClock.advanceTimeBy(50)
        c.showForPreview(at, pageIndex, pages)
        compose.mainClock.advanceTimeBy(900)
        capture(name)
    }

    /**
     * The reader's own parts (margins, progress bar) over a plain black or plain white page: from the
     * two, they can be lifted off with their transparency and put over a moving page.
     */
    private fun matte(name: String, settings: ReaderSettings, pageIndex: Int, pages: Int, white: Boolean) = withBook(STORY_FILE) { files, structure ->
        val c = ReaderController(files, structure, STORY, "en", null, settings, context.assets, "Arthur Conan Doyle")
        val at = c.chapters.indexOfFirst { it.path.endsWith(STORY_CHAPTER) }
        c.showForPreview(at, pageIndex, pages)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = {
                    Box(Modifier.fillMaxSize().background(if (white) Color.White else Color.Black))
                })
            }
        }
        compose.mainClock.advanceTimeBy(50)
        c.showForPreview(at, pageIndex, pages)
        compose.mainClock.advanceTimeBy(900)
        capture("${name}_${if (white) "white" else "black"}")
    }

    private fun paged(theme: ReaderTheme, font: ReaderFont = ReaderFont.GARAMOND, scale: Float = 1.1f) = ReaderSettings(theme = theme, font = font, fontScale = scale)
    private fun scrolling() = ReaderSettings(theme = ReaderTheme.BLACK, font = ReaderFont.GARAMOND, fontScale = 1.1f, flow = ReaderFlow.SCROLL)

    @Test @Config(qualifiers = TAB) fun rdPaper2() = reader("rd_paper_2", paged(ReaderTheme.PAPER), "sh_paper_2", 1, 13)
    @Test @Config(qualifiers = TAB) fun rdPaper3() = reader("rd_paper_3", paged(ReaderTheme.PAPER), "sh_paper_3", 2, 13)
    @Test @Config(qualifiers = TAB) fun rdPaper4() = reader("rd_paper_4", paged(ReaderTheme.PAPER), "sh_paper_4", 3, 13)
    @Test @Config(qualifiers = TAB) fun rdPaper5() = reader("rd_paper_5", paged(ReaderTheme.PAPER), "sh_paper_5", 4, 13)
    @Test @Config(qualifiers = TAB) fun rdPaper6() = reader("rd_paper_6", paged(ReaderTheme.PAPER), "sh_paper_6", 5, 13)
    @Test @Config(qualifiers = TAB) fun rdBlack6() = reader("rd_black_6", paged(ReaderTheme.BLACK), "sh_black_6", 5, 13)

    @Test
    @Config(qualifiers = TAB)
    fun rdNote() {
        val note = Annotation(
            path = "epub/text/$STORY_CHAPTER", start = "1/0:0", end = "1/0:40",
            text = "You see, but you do not observe. The distinction is clear.",
            color = HighlightColor.YELLOW, note = "Seeing is not observing.", chapterTitle = "A Scandal in Bohemia", bookProgress = 0.02f, created = 1_794_300_000_000L,
        )
        reader("rd_note", paged(ReaderTheme.PAPER), "sh_paper_2", 1, 13, prepare = { c ->
            c.showAnnotationsForPreview(listOf(note))
            c.openAnnotation = ReaderController.OpenAnnotation(note.id, android.graphics.RectF(1516f, 126f, 2293f, 172f))
        })
    }

    // The page colours and the typefaces, with the settings open on them.
    private fun themePanel(theme: ReaderTheme) = reader(
        "rd_theme_${theme.name.lowercase()}", paged(theme), "sh_${theme.name.lowercase()}_6", 5, 13, controls = true, panel = Panel.LOOK, prepare = { it.lookTab = 1 },
    )
    @Test @Config(qualifiers = TAB) fun rdThemePaper() = themePanel(ReaderTheme.PAPER)
    @Test @Config(qualifiers = TAB) fun rdThemeWhite() = themePanel(ReaderTheme.WHITE)
    @Test @Config(qualifiers = TAB) fun rdThemeSepia() = themePanel(ReaderTheme.SEPIA)
    @Test @Config(qualifiers = TAB) fun rdThemeSlate() = themePanel(ReaderTheme.SLATE)
    @Test @Config(qualifiers = TAB) fun rdThemeNight() = themePanel(ReaderTheme.NIGHT)
    @Test @Config(qualifiers = TAB) fun rdThemeBlack() = themePanel(ReaderTheme.BLACK)

    private fun fontPanel(font: ReaderFont, file: String, at: Int, pages: Int, scale: Float) = reader(
        "rd_font_$file", paged(ReaderTheme.PAPER, font, scale), if (file == "garamond") "sh_paper_6" else "sh_font_${file}_6", at, pages,
        controls = true, panel = Panel.LOOK, prepare = { it.lookTab = 0 },
    )
    @Test @Config(qualifiers = TAB) fun rdFontLiterata() = fontPanel(ReaderFont.LITERATA, "literata", 5, 14, 1.0f)
    @Test @Config(qualifiers = TAB) fun rdFontGaramond() = fontPanel(ReaderFont.GARAMOND, "garamond", 5, 13, 1.1f)
    @Test @Config(qualifiers = TAB) fun rdFontAtkinson() = fontPanel(ReaderFont.HYPERLEGIBLE, "atkinson", 4, 11, 1.0f)
    @Test @Config(qualifiers = TAB) fun rdFontDyslexic() = fontPanel(ReaderFont.DYSLEXIC, "dyslexic", 8, 21, 0.9f)

    // Pages or scrolling, and the page moving by itself.
    @Test @Config(qualifiers = TAB)
    fun rdLayoutPages() = reader("rd_layout_pages", paged(ReaderTheme.BLACK), "sh_black_6", 5, 13, controls = true, panel = Panel.LOOK, prepare = { it.lookTab = 2 })

    @Test @Config(qualifiers = TAB)
    fun rdLayoutScroll() = reader("rd_layout_scroll", scrolling(), "sh_black_scroll_view", 8, 20, controls = true, panel = Panel.LOOK, prepare = { it.lookTab = 2 })

    @Test @Config(qualifiers = TAB)
    fun rdScrollControls() = reader("rd_scroll_controls", scrolling(), "sh_black_scroll_view", 8, 20, controls = true)

    @Test @Config(qualifiers = TAB)
    fun rdAuto() = reader("rd_auto", scrolling(), "sh_black_scroll_view", 8, 20, controls = true, prepare = { it.startAutoscroll(); it.controlsVisible = true })

    @Test @Config(qualifiers = TAB) fun scrollMatteBlack() = matte("matte_scroll", scrolling(), 8, 20, false)
    @Test @Config(qualifiers = TAB) fun scrollMatteWhite() = matte("matte_scroll", scrolling(), 8, 20, true)

    // ---- sending ---------------------------------------------------------------------------------

    private fun overLibrary(name: String, over: @Composable (LibraryBook) -> Unit) {
        val (lib, covers) = library(newCover = true)
        val book = lib.titled(STORY)
        val browse = LibraryBrowseState().apply { selectedId = book.id }
        shot(name) {
            LibraryPage(lib, covers, browse)
            over(book)
        }
    }

    @Test @Config(qualifiers = TAB)
    fun sendDialog() = overLibrary("send_dialog") { book ->
        SendDialog(listOf(book), connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB, onSend = { _, _, _ -> }, onConnect = {}, onDismiss = {}, pathOnKobo = { null })
    }

    @Test @Config(qualifiers = TAB)
    fun sendProgress() = overLibrary("send_progress") {
        JobDialog(JobProgress(JobKind.SEND, "Sending to the Kobo", total = 1, done = 0, current = STORY, currentFraction = 0.6f, startedAt = 0), onHide = {}, onStop = {}, onDone = {}, onHowToEject = {})
    }

    @Test @Config(qualifiers = TAB)
    fun sendDone() = overLibrary("send_done") {
        JobDialog(JobProgress(JobKind.SEND, "Sending to the Kobo", total = 1, done = 1, succeeded = 1, finished = true, startedAt = 0), onHide = {}, onStop = {}, onDone = {}, onHowToEject = {})
    }

    // ---- on a phone ----------------------------------------------------------------------------

    // (a phone shows the library by what was read last; Moby Dick is the book being read on it)
    @Test @Config(qualifiers = PHONE) fun phLibrary() = libraryIn("ph_library", newCover = true) { sort = LibrarySort.READ_HERE; descending = true }
    @Test @Config(qualifiers = PHONE) fun phBook() = libraryIn("ph_book", newCover = true) { sort = LibrarySort.READ_HERE; descending = true; selectedId = it.titled("Moby Dick").id }

    private fun phoneReader(n: Int) = reader(
        "ph_reader_$n", ReaderSettings(theme = ReaderTheme.PAPER, font = ReaderFont.GARAMOND, fontScale = 1.0f), "ph_moby_$n", n - 1, 8,
        file = "herman-melville_moby-dick", title = "Moby Dick", author = "Herman Melville", chapter = "chapter-1.xhtml",
    )
    @Test @Config(qualifiers = PHONE) fun phReader1() = phoneReader(1)
    @Test @Config(qualifiers = PHONE) fun phReader2() = phoneReader(2)
}
