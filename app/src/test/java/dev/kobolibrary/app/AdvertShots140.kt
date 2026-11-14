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

/**
 * Pictures of the app for its 1:40 advert: real public-domain books (Standard Ebooks covers, and pages laid
 * out by the app's own reader in Chrome), at the Tab S9's 2560 x 1600. Only runs where those files are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvertShots140 {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    private val root = File("/home/claude/advert")
    private val out = File(root, "app140")

    private fun B(
        title: String, author: String, cover: String, status: Int = 0, percent: Int = 0, lastRead: String? = null,
        collections: List<String> = emptyList(), series: String? = null, number: String? = null, tags: List<String> = emptyList(),
        description: String? = null,
    ) = Triple(FakeKobo.Book(title, author, series, number, status, percent, lastRead, collections, tags = tags, description = description), cover, title)

    private val shelf = listOf(
        B("Moby Dick", "Herman Melville", "mobydick", 1, 34, "2026-11-12T21:40:00Z", listOf("Sea Stories", "Classics"), tags = listOf("Adventure", "The Sea"),
            description = "<p>Ishmael signs on to the whaler <i>Pequod</i>, and Captain Ahab's hunt for the white whale begins.</p>"),
        B("Pride and Prejudice", "Jane Austen", "pride", 2, 100, "2026-08-20T20:00:00Z", listOf("Austen", "Classics"), tags = listOf("Romance", "Classics"),
            description = "<p>Elizabeth Bennet meets the proud Mr. Darcy, and first impressions prove <b>hard to shake</b>.</p>"),
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
        B("The Adventures of Sherlock Holmes", "Arthur Conan Doyle", "sherlock", 2, 100, "2026-03-11T22:30:00Z", listOf("Sherlock Holmes"), "Sherlock Holmes", "3"),
        B("The Hound of the Baskervilles", "Arthur Conan Doyle", "hound", collections = listOf("Sherlock Holmes", "To Read"), series = "Sherlock Holmes", number = "5"),
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

    private var koboBooks: Map<String, KoboBook> = emptyMap()

    /** A library filled from a Kobo holding the shelf above, with its real covers. */
    private fun library(): Pair<Library, LibraryCovers> = runBlocking {
        assumeTrue(File(root, "covers/mobydick.jpg").exists())
        FakeKobo.bookList = shelf.map { it.first }
        FakeKobo.collectionList = shelf.flatMap { it.first.collections }.distinct().sorted()
        FakeKobo.coverArt = shelf.associate { (b, file, _) -> b.title to File(root, "covers/$file.jpg").readBytes() }
        try {
            val drive = File(context.cacheDir, "ad-kobo-" + UUID.randomUUID()).also { FakeKobo.create(it) }
            val session = KoboRepository(context).connect(FakeDocumentsProvider.install(drive)) {}
            koboBooks = session.snapshot.booksById
            val lib = Library(context, File(context.filesDir, "ad-" + UUID.randomUUID()), null)
            lib.load()
            lib.rememberKoboCollections(session.snapshot.collections.map { it.name })
            for (b in session.snapshot.books.filter { it.isSideloaded }) lib.importFromKobo(session, b)
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

    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.BOOKCASE, settle: Long = 1_000, content: @Composable () -> Unit) {
        out.mkdirs()
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(settle)
        captureScreenRoboImage(File(out, "$name.png").path)
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
                ),
            )
        }
    }

    @Test
    @Config(qualifiers = TAB)
    fun libraryShelves() {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { selectedId = lib.titled("Moby Dick").id }
        shot("ad_library") { LibraryPage(lib, covers, browse) }
    }

    @Test
    @Config(qualifiers = TAB)
    fun sending() {
        val (lib, covers) = library()
        val picked = listOf("Pride and Prejudice", "Emma", "Persuasion").map { lib.titled(it) }
        val browse = LibraryBrowseState().apply {
            selecting = true
            picked.forEach { this.picked[it.id] = true }
        }
        shot("ad_send") {
            LibraryPage(lib, covers, browse)
            SendDialog(picked, connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB, onSend = { _, _ -> }, onConnect = {}, onDismiss = {}, pathOnKobo = { null })
        }
    }

    @Test
    @Config(qualifiers = TAB)
    fun details() {
        val (lib, covers) = library()
        val book = lib.titled("Pride and Prejudice")
        runBlocking { lib.updateDetails(book.id, book.meta.copy(publisher = "Standard Ebooks", published = "1813-01-28", rating = 10), book.collections) }
        val again = lib.titled("Pride and Prejudice")
        val suggestions = EditSuggestions.from(lib.books.value, lib.allCollections(), lib.koboCollections.value)
        shot("ad_details") { EditDetailsScreen(again, covers, suggestions, saving = false, onSave = { _, _ -> }, onClose = {}) }
    }

    @Test
    @Config(qualifiers = TAB)
    fun cover() {
        val (lib, _) = library()
        val book = lib.titled("The Wind in the Willows")
        val picture = runBlocking { lib.originalCover(book.id) }!!
            .let { CoverRendering.decode(it)!! }
            .let { CoverRendering.prepare(it, "The book's cover") }
        shot("ad_cover") {
            CoverEditorScreen(book, loadOriginal = { null }, saving = false, onSave = {}, onClose = {}, initialPicture = picture)
        }
    }

    @Test
    @Config(qualifiers = TAB)
    fun combine() {
        val (lib, covers) = library()
        val books = listOf(lib.titled("The Iliad"), lib.titled("The Odyssey"))
        shot("ad_merge") { MergeDialog(books, covers, onMerge = { _, _, _, _ -> }, onDismiss = {}) }
    }

    @Test
    @Config(qualifiers = TAB)
    fun split() {
        val (lib, _) = library()
        val pages = ArrayList<BookAssembly.Page>()
        fun add(title: String, words: Int, level: Int?) { pages += BookAssembly.Page(pages.size, "p${pages.size}", title, true, words, level) }
        add("Cover", 2, 0)
        add("Titlepage", 30, 0)
        listOf("Fantine", "Cosette", "Marius", "Saint-Denis", "Jean Valjean").forEachIndexed { v, name ->
            add("Volume ${v + 1}: $name", 14, 0)
            val books = listOf(4, 3, 5, 4, 3)[v]
            for (k in 1..books) add("Book ${"I II III IV V".split(' ')[k - 1]}", 9000 + 700 * ((v + k) % 6), 1)
        }
        add("Endnotes", 6000, 0)
        shot("ad_split") {
            SplitBookDialog(lib.titled("Les Misérables"), loadPages = { pages }, onSplit = { _, _, _ -> }, onDismiss = {}, initialPages = pages)
        }
    }

    @Test
    @Config(qualifiers = TAB)
    fun editor() {
        val lib = Library(context, File(context.filesDir, "ad-ed-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val epub = File(root, "books/herman-melville_moby-dick.epub")
        assumeTrue(epub.exists())
        runBlocking { lib.import("Moby Dick.epub", { epub.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        val state = TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "epub/text/chapter-1.xhtml")
        val page = BitmapFactory.decodeFile(File(root, "pages/editor_moby.png").path).asImageBitmap()
        out.mkdirs()
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface(ThemeChoice.BOOKCASE) {
                TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE7E3DA)), contentAlignment = Alignment.TopCenter) {
                        Image(page, null, Modifier.padding(top = 20.dp).width(640.dp), contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter)
                    }
                })
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(File(out, "ad_editor.png").path)
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

    private fun pageImage(name: String) = BitmapFactory.decodeFile(File(root, "pages/$name.png").path).asImageBitmap()

    /** Waits for the book's chapters to be measured, so the reader can number pages through the whole book. */
    private fun counted(c: ReaderController) {
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(1200) {
            if (c.lengthsCounted) return
            looper.idle()
            Thread.sleep(25)
        }
    }

    /** The reader over a page laid out by the app's reader in Chrome. */
    private fun reader(
        name: String, book: String, title: String, author: String, chapterFile: String, settings: ReaderSettings,
        page: String, pageIndex: Int, pages: Int, controls: Boolean = false, panel: Panel = Panel.NONE,
        prepare: (ReaderController) -> Unit = {},
    ) = withBook(book) { files, structure ->
        val c = ReaderController(files, structure, title, "en", null, settings, context.assets, author)
        val at = c.chapters.indexOfFirst { it.path.endsWith(chapterFile) }
        counted(c)
        c.showForPreview(at, pageIndex, pages)
        c.controlsVisible = controls
        prepare(c)
        val img = pageImage(page)
        out.mkdirs()
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
        captureScreenRoboImage(File(out, "$name.png").path)
    }

    /**
     * The reader's own parts (margins, progress bar) over a plain black or plain white page: from the
     * two, they can be lifted off with their transparency and put over a moving page.
     */
    private fun matte(name: String, book: String, title: String, author: String, chapterFile: String, settings: ReaderSettings, pageIndex: Int, pages: Int, white: Boolean) =
        withBook(book) { files, structure ->
            val c = ReaderController(files, structure, title, "en", null, settings, context.assets, author)
            val at = c.chapters.indexOfFirst { it.path.endsWith(chapterFile) }
            counted(c)
            c.showForPreview(at, pageIndex, pages)
            out.mkdirs()
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
            captureScreenRoboImage(File(out, "${name}_${pageIndex + 1}_${if (white) "white" else "black"}.png").path)
        }

    /** As the advert's reader pages were shot: the chapter's title and page number in the margins, from before page headings. */
    private fun mobySettings(theme: ReaderTheme) =
        ReaderSettings(theme = theme, font = ReaderFont.GARAMOND, fontScale = 1.1f).withHeads(dev.kobolibrary.app.reader.RunningHeads.OFF)

    private fun moby(name: String, theme: ReaderTheme, page: String, n: Int, controls: Boolean = false, panel: Panel = Panel.NONE) =
        reader(name, "herman-melville_moby-dick", "Moby Dick", "Herman Melville", "chapter-1.xhtml", mobySettings(theme), page, n - 1, 3, controls, panel)

    @Test @Config(qualifiers = TAB) fun readerMobyPaper1() = moby("ad_reader_moby_paper_1", ReaderTheme.PAPER, "moby_paper_1", 1)
    @Test @Config(qualifiers = TAB) fun readerMobyPaper2() = moby("ad_reader_moby_paper_2", ReaderTheme.PAPER, "moby_paper_2", 2)
    @Test @Config(qualifiers = TAB) fun readerMobyBlack2() = moby("ad_reader_moby_black_2", ReaderTheme.BLACK, "moby_black_2", 2)




    @Test
    @Config(qualifiers = TAB)
    fun readerPride() = reader(
        "ad_reader_pride", "jane-austen_pride-and-prejudice", "Pride and Prejudice", "Jane Austen", "chapter-1.xhtml",
        ReaderSettings(theme = ReaderTheme.PAPER, font = ReaderFont.GARAMOND, fontScale = 1.1f), "pride_paper_1", 0, 2,
    )

    private fun iliadSettings() = ReaderSettings(theme = ReaderTheme.NIGHT, font = ReaderFont.GARAMOND, fontScale = 1.1f, flow = ReaderFlow.SCROLL)

    private fun iliadMatte(white: Boolean) =
        matte("ad_matte_iliad", "homer_the-iliad_william-cullen-bryant", "The Iliad", "Homer", "book-1.xhtml", iliadSettings(), 0, 9, white)

    @Test @Config(qualifiers = TAB) fun iliadMatteBlack() = iliadMatte(false)
    @Test @Config(qualifiers = TAB) fun iliadMatteWhite() = iliadMatte(true)

    @Test
    @Config(qualifiers = TAB)
    fun readerIliadAutoscroll() = reader(
        "ad_reader_iliad_auto", "homer_the-iliad_william-cullen-bryant", "The Iliad", "Homer", "book-1.xhtml",
        iliadSettings(), "iliad_night_scroll_view", 0, 9, controls = true, prepare = { it.startAutoscroll(); it.controlsVisible = true },
    )

    // ---- the second advert ---------------------------------------------------------------------

    private fun libraryIn(name: String, theme: ThemeChoice, configure: LibraryBrowseState.(Library) -> Unit = {}) {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { configure(lib) }
        shot(name, theme) { LibraryPage(lib, covers, browse) }
    }

    @Test @Config(qualifiers = TAB) fun libLightSel() = libraryIn("ad_lib_light_sel", ThemeChoice.LIGHT) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libDarkSel() = libraryIn("ad_lib_dark_sel", ThemeChoice.DARK) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libOledSel() = libraryIn("ad_lib_oled_sel", ThemeChoice.OLED) { selectedId = it.titled("Moby Dick").id }
    @Test @Config(qualifiers = TAB) fun libBookcaseWall() = libraryIn("ad_lib_bookcase", ThemeChoice.BOOKCASE)
    @Test @Config(qualifiers = TAB) fun libSearch1() = libraryIn("ad_lib_search_1", ThemeChoice.BOOKCASE) { query = "au" }
    @Test @Config(qualifiers = TAB) fun libSearch2() = libraryIn("ad_lib_search_2", ThemeChoice.BOOKCASE) { query = "aust" }
    @Test @Config(qualifiers = TAB) fun libSearch3() = libraryIn("ad_lib_search_3", ThemeChoice.BOOKCASE) { query = "austen" }


    /** A Kobo holding the shelf, connected. */
    private fun kobo(): Pair<KoboRepository, KoboSession> = runBlocking {
        assumeTrue(File(root, "covers/mobydick.jpg").exists())
        FakeKobo.bookList = shelf.map { it.first }
        FakeKobo.collectionList = shelf.flatMap { it.first.collections }.distinct().sorted()
        FakeKobo.coverArt = shelf.associate { (b, file, _) -> b.title to File(root, "covers/$file.jpg").readBytes() }
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

    @Test @Config(qualifiers = TAB) fun koboBooks() = koboScreen("ad_kobo_books") { s -> selectedBookId = s.snapshot.books.first { it.title == "The Odyssey" }.contentId }
    @Test @Config(qualifiers = TAB) fun koboCollections() = koboScreen("ad_kobo_collections", tab = 1, collection = "Classics")

    private fun sendJob(name: String, p: JobProgress) {
        val (lib, covers) = library()
        val picked = listOf("Pride and Prejudice", "Emma", "Persuasion").map { lib.titled(it) }
        val browse = LibraryBrowseState().apply {
            selecting = true
            picked.forEach { this.picked[it.id] = true }
        }
        shot(name) {
            LibraryPage(lib, covers, browse)
            JobDialog(p, onHide = {}, onStop = {}, onDone = {}, onHowToEject = {})
        }
    }

    @Test @Config(qualifiers = TAB)
    fun sendProgress() = sendJob("ad_send_progress", JobProgress(JobKind.SEND, "Sending 3 books to the Kobo", total = 3, done = 1, current = "Emma", currentFraction = 0.55f, succeeded = 1, startedAt = 0))

    @Test @Config(qualifiers = TAB)
    fun sendDone() = sendJob("ad_send_done", JobProgress(JobKind.SEND, "Sending 3 books to the Kobo", total = 3, done = 3, succeeded = 3, finished = true, startedAt = 0))

    private val houndText = CoverText("The Hound of the Baskervilles", listOf("Arthur Conan Doyle"), "Sherlock Holmes", 5.0)

    private fun generator(name: String, style: CoverStyle, colors: CoverColors) {
        out.mkdirs()
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.BOOKCASE) { CoverGeneratorScreen(houndText, onUse = { _, _ -> }, onCancel = {}, initial = CoverDesign(style, colors)) } }
        repeat(28) { compose.waitForIdle(); Thread.sleep(250) }
        captureScreenRoboImage(File(out, "$name.png").path)
    }

    private fun theme(name: String) = CoverColors.themes.first { it.name == name }

    @Test @Config(qualifiers = TAB) fun newGen1() = generator("ad_newgen_1", CoverStyle.LABEL, theme("Parchment"))
    @Test @Config(qualifiers = TAB) fun newGen2() = generator("ad_newgen_2", CoverStyle.SUNRISE, theme("Forest"))
    @Test @Config(qualifiers = TAB) fun newGen3() = generator("ad_newgen_3", CoverStyle.MOSAIC, theme("Slate"))
    @Test @Config(qualifiers = TAB) fun newGen4() = generator("ad_newgen_4", CoverStyle.BOLD, theme("Claret"))
    @Test @Config(qualifiers = TAB) fun newGen5() = generator("ad_newgen_5", CoverStyle.CLASSIC, theme("Midnight"))

    private fun mobyWith(name: String, settings: ReaderSettings, page: String, n: Int, pages: Int, chapter: String = "chapter-1.xhtml", controls: Boolean = false, panel: Panel = Panel.NONE, lookTab: Int = 0) =
        reader(name, "herman-melville_moby-dick", "Moby Dick", "Herman Melville", chapter, settings, page, n - 1, pages, controls, panel, prepare = { it.lookTab = lookTab })

    @Test @Config(qualifiers = TAB) fun rdMoby3() = mobyWith("ad_rd_moby_paper_3", mobySettings(ReaderTheme.PAPER), "moby_paper_3", 3, 3)
    @Test @Config(qualifiers = TAB) fun rdMoby21() = mobyWith("ad_rd_moby2_paper_1", mobySettings(ReaderTheme.PAPER), "moby2_paper_1", 1, 2, "chapter-2.xhtml")
    @Test @Config(qualifiers = TAB) fun rdMoby22() = mobyWith("ad_rd_moby2_paper_2", mobySettings(ReaderTheme.PAPER), "moby2_paper_2", 2, 2, "chapter-2.xhtml")

    // The page colours, with the settings open on them.
    private fun themePanel(theme: ReaderTheme) = mobyWith("ad_rd_panel_theme_${theme.name.lowercase()}", mobySettings(theme), "moby_${theme.name.lowercase()}_2", 2, 3, controls = true, panel = Panel.LOOK, lookTab = 1)
    @Test @Config(qualifiers = TAB) fun rdPanelPaper() = themePanel(ReaderTheme.PAPER)
    @Test @Config(qualifiers = TAB) fun rdPanelWhite() = themePanel(ReaderTheme.WHITE)
    @Test @Config(qualifiers = TAB) fun rdPanelSepia() = themePanel(ReaderTheme.SEPIA)
    @Test @Config(qualifiers = TAB) fun rdPanelSlate() = themePanel(ReaderTheme.SLATE)
    @Test @Config(qualifiers = TAB) fun rdPanelNight() = themePanel(ReaderTheme.NIGHT)
    @Test @Config(qualifiers = TAB) fun rdPanelBlack() = themePanel(ReaderTheme.BLACK)

    // The typefaces, with the settings open on them, and without.
    private fun fontShot(font: ReaderFont, file: String, pages: Int, scale: Float, panel: Boolean) = mobyWith(
        "ad_rd_${if (panel) "panel_" else ""}font_$file", ReaderSettings(theme = ReaderTheme.PAPER, font = font, fontScale = scale),
        if (file == "garamond") "moby_paper_2" else "moby_font_${file}_2", 2, pages, controls = panel, panel = if (panel) Panel.LOOK else Panel.NONE, lookTab = 0,
    )
    @Test @Config(qualifiers = TAB) fun rdPanelLiterata() = fontShot(ReaderFont.LITERATA, "literata", 4, 1.0f, true)
    @Test @Config(qualifiers = TAB) fun rdPanelGaramond() = fontShot(ReaderFont.GARAMOND, "garamond", 3, 1.1f, true)
    @Test @Config(qualifiers = TAB) fun rdPanelAtkinson() = fontShot(ReaderFont.HYPERLEGIBLE, "atkinson", 3, 1.0f, true)
    @Test @Config(qualifiers = TAB) fun rdPanelDyslexic() = fontShot(ReaderFont.DYSLEXIC, "dyslexic", 5, 0.9f, true)



    @Test
    @Config(qualifiers = TAB)
    fun rdPrideNote() {
        val note = Annotation(
            path = "epub/text/chapter-1.xhtml", start = "1/0:0", end = "1/0:40",
            text = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
            color = HighlightColor.YELLOW, note = "Still the best first line in all of fiction.", chapterTitle = "I", bookProgress = 0.01f, created = 1_794_300_000_000L,
        )
        reader(
            "ad_rd_pride_note", "jane-austen_pride-and-prejudice", "Pride and Prejudice", "Jane Austen", "chapter-1.xhtml",
            ReaderSettings(theme = ReaderTheme.PAPER, font = ReaderFont.GARAMOND, fontScale = 1.1f), "pride_paper_1", 0, 2,
            prepare = { c ->
                c.showAnnotationsForPreview(listOf(note))
                c.openAnnotation = ReaderController.OpenAnnotation(note.id, android.graphics.RectF(84f, 466f, 1196f, 559f))
            },
        )
    }

    // ---- on a phone ----------------------------------------------------------------------------

    // (a phone shows the library by what was read last; Moby Dick is the book being read on it, and the one tapped)
    @Test @Config(qualifiers = PHONE) fun phLibrary() = libraryIn("ph_library", ThemeChoice.BOOKCASE) { sort = LibrarySort.READ_HERE; descending = true }
    @Test @Config(qualifiers = PHONE) fun phBook() = libraryIn("ph_book", ThemeChoice.BOOKCASE) { sort = LibrarySort.READ_HERE; descending = true; selectedId = it.titled("Moby Dick").id }


    private fun phoneReader(n: Int) = mobyWith("ph_reader_$n", ReaderSettings(theme = ReaderTheme.PAPER, font = ReaderFont.GARAMOND, fontScale = 1.0f), "ph_moby_$n", n, 8)
    @Test @Config(qualifiers = PHONE) fun phReader1() = phoneReader(1)
    @Test @Config(qualifiers = PHONE) fun phReader2() = phoneReader(2)
}
