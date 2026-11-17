package dev.kobolibrary.app

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kobolibrary.app.ui.AppRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kobolibrary.app.ui.library.DuplicatesDialog
import dev.kobolibrary.app.ui.library.ExportDialog
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.epub.BookAssembly
import dev.kobolibrary.app.ui.library.SplitBookDialog
import dev.kobolibrary.app.ui.library.MergeDialog
import dev.kobolibrary.app.ui.library.NewBookDialog
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
import dev.kobolibrary.app.kobo.KoboRepository
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.reader.ReadHere
import dev.kobolibrary.app.ui.AppFrame
import dev.kobolibrary.app.ui.Section
import dev.kobolibrary.app.ui.cover.CoverEditorScreen
import dev.kobolibrary.app.ui.cover.CoverRendering
import dev.kobolibrary.app.ui.edit.DescriptionEditor
import dev.kobolibrary.app.ui.edit.EditDetailsScreen
import dev.kobolibrary.app.ui.edit.EditSuggestions
import dev.kobolibrary.app.kobo.SendFormat
import dev.kobolibrary.app.ui.cover.CoverColors
import dev.kobolibrary.app.ui.cover.CoverDesign
import dev.kobolibrary.app.ui.cover.CoverGenerator
import dev.kobolibrary.app.ui.cover.CoverGeneratorScreen
import dev.kobolibrary.app.ui.cover.CoverStyle
import dev.kobolibrary.app.ui.cover.CoverText
import dev.kobolibrary.app.ui.library.JobDialog
import dev.kobolibrary.app.ui.library.SearchHelpDialog
import dev.kobolibrary.app.ui.library.SendDialog
import dev.kobolibrary.app.ui.library.TagPickerDialog
import dev.kobolibrary.app.ui.library.LibraryBrowseState
import dev.kobolibrary.app.ui.library.LibraryScreen
import dev.kobolibrary.app.ui.library.LibrarySort
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.KoboLibraryTheme
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
import java.util.UUID
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.ui.LookDialog
import dev.kobolibrary.app.ui.AboutScreen
import dev.kobolibrary.app.ui.LicenceText
import dev.kobolibrary.app.ui.Notices

private const val LANDSCAPE = "w1280dp-h800dp-land-mdpi"
private const val PORTRAIT = "w800dp-h1280dp-port-mdpi"
private const val LANDSCAPE_NIGHT = "w1280dp-h800dp-land-night-mdpi"
private const val PHONE = "w411dp-h891dp-port-mdpi"

/** The library, the details editor and the cover editor, at the Galaxy Tab S9's size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun inLanguage() = TestLanguage.apply()

    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, act: (() -> Unit)? = null, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        val path = "${TestLanguage.folder("build/outputs/roborazzi")}/$name.png"
        if (act == null) {
            compose.onRoot().captureRoboImage(path)
        } else {
            act()
            compose.mainClock.advanceTimeBy(1_000)
            // Whatever it opened, dialogs included.
            captureScreenRoboImage(path)
        }
    }

    /** The fake Kobo the library was filled from, for its reading progress. */
    private var koboBooks: Map<String, KoboBook> = emptyMap()

    /** A library filled from the fake Kobo, with fuller details on The Histories. */
    private fun library(): Pair<Library, LibraryCovers> = runBlocking {
        val drive = File(context.cacheDir, "shots-kobo").also { FakeKobo.create(it) }
        val session = KoboRepository(context).connect(FakeDocumentsProvider.install(drive)) {}
        koboBooks = session.snapshot.booksById
        val lib = Library(context, File(context.filesDir, "shots-" + UUID.randomUUID()), null)
        lib.load()
        lib.rememberKoboCollections(session.snapshot.collections.map { it.name })
        for (b in session.snapshot.books.filter { it.isSideloaded }) lib.importFromKobo(session, b)
        val h = lib.books.value.first { it.title == "The Histories" }
        lib.updateDetails(
            h.id,
            h.meta.copy(
                tags = listOf("Ancient Greece", "History"),
                publisher = "Penguin Classics",
                published = "2003-02-27",
                rating = 8,
                isbn = "9780140449082",
                description = "<p>Herodotus' account of the wars between Greece and Persia, and of the peoples and places " +
                    "around them, is the <b>first great work of history</b> in the West.</p><p>Translated by Aubrey de Sélincourt.</p>",
            ),
            h.collections,
        )
        val covers = LibraryCovers(lib)
        for (b in lib.books.value) {
            covers.load(b, false, 300)
            covers.load(b, true, 600)
        }
        lib to covers
    }

    private fun Library.titled(title: String): LibraryBook = books.value.first { it.title == title }

    private fun libraryScreen(
        name: String,
        empty: Boolean = false,
        importing: JobProgress? = null,
        theme: ThemeChoice = ThemeChoice.SYSTEM,
        continueFromKobo: Boolean = false,
        /** A phone or tablet is paired, so books can be sent to it. */
        linked: Boolean = false,
        act: (() -> Unit)? = null,
        configure: LibraryBrowseState.(Library) -> Unit = {},
    ) {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { configure(lib) }
        shot(name, theme, act) {
            AppFrame(Section.LIBRARY, {}, koboConnected = true, onNarrow = {}) {
                androidx.compose.runtime.CompositionLocalProvider(
                    dev.kobolibrary.app.ui.link.LocalLinkActions provides if (linked) dev.kobolibrary.app.ui.link.LinkActions {} else null,
                ) {
                LibraryScreen(
                    books = if (empty) emptyList() else lib.books.value,
                    covers = covers,
                    browse = browse,
                    job = importing,
                    koboConnected = !empty,
                    onAddFiles = {}, onAddFromKobo = {}, onShowJob = {}, onEditDetails = {}, onChangeCover = {}, onDelete = {}, onSend = {},
                    onKobo = if (empty) emptyMap() else koboBooks,
                    koboLive = true,
                    // Some books part-read in the app's reader.
                    readingHere = if (empty) emptyMap() else mapOf(
                        lib.titled("The Histories").id to ReadHere(0.42f, 1_794_400_000_000L),
                        lib.titled("Dracula").id to ReadHere(0.08f, 1_794_300_000_000L),
                        lib.titled("Meditations").id to ReadHere(1f, 1_794_200_000_000L, finished = true),
                    ),
                    onContinueFromKobo = if (continueFromKobo) ({ _, _ -> }) else null,
                )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun libraryGrid() = libraryScreen("18_library_grid_landscape") { selectedId = it.titled("The Histories").id }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun libraryListPortrait() = libraryScreen("19_library_list_portrait") {
        grid = false
        sort = LibrarySort.AUTHOR
    }

    @Test
    @Config(qualifiers = LANDSCAPE_NIGHT)
    fun libraryDark() = libraryScreen("20_library_dark") {
        collection = "Sherlock Holmes"
        selectedId = it.titled("The Sign of the Four").id
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun libraryEmpty() = libraryScreen("21_library_empty", empty = true)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun libraryCollections() = libraryScreen("21b_library_collections") {
        tab = 1
        collectionKey = "sherlock holmes"
    }

    /** A book tapped in a collection opens on the right; the collections and the collection's books stay where they are. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun libraryCollectionBook() = libraryScreen("21c_library_collection_book") {
        tab = 1
        collectionKey = "sherlock holmes"
        collectionBookId = it.titled("The Sign of the Four").id
    }

    /** Standing up: the collections stay down the left, the first open beside them until another is tapped. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun libraryCollectionsPortrait() = libraryScreen("21d_library_collections_portrait") { tab = 1 }

    /** And a book takes its collection's place there, with a way back to it. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun libraryCollectionBookPortrait() = libraryScreen("21e_library_collection_book_portrait") {
        tab = 1
        collectionKey = "sherlock holmes"
        collectionBookId = it.titled("The Sign of the Four").id
    }

    @Test
    @Config(qualifiers = PHONE)
    fun libraryCollectionsPhone() = libraryScreen("22b_library_collections_phone") { tab = 1 }

    @Test
    @Config(qualifiers = PHONE)
    fun libraryCollectionPhone() = libraryScreen("22c_library_collection_phone") {
        tab = 1
        collectionKey = "sherlock holmes"
    }

    @Test
    @Config(qualifiers = PHONE)
    fun libraryCollectionBookPhone() = libraryScreen("22d_library_collection_book_phone") {
        tab = 1
        collectionKey = "sherlock holmes"
        collectionBookId = it.titled("The Sign of the Four").id
    }

    @Test
    @Config(qualifiers = PHONE)
    fun libraryOnAPhone() = libraryScreen("22_library_phone")

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun importing() {
        val p = JobProgress(JobKind.ADD_FROM_KOBO, "Adding books from the Kobo", total = 16, done = 5, current = "The Republic", succeeded = 5)
        libraryScreen("23_library_importing", importing = p)
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun importSummary() {
        val p = JobProgress(
            JobKind.ADD_FILES, "Adding books", total = 7, done = 7, succeeded = 4, alreadyThere = 1, finished = true,
            failures = listOf(
                "notes.epub" to "The file isn't an EPUB (it has no META-INF/container.xml).",
                "broken.epub" to "Couldn't read the file: unexpected end of ZIP input stream",
            ),
        )
        shot("24_import_summary") { JobDialog(p, onHide = {}, onStop = {}, onDone = {}) }
    }

    private fun editScreen(name: String) {
        val (lib, covers) = library()
        val book = lib.titled("The Histories")
        val suggestions = EditSuggestions.from(lib.books.value, lib.allCollections(), lib.koboCollections.value)
        shot(name) { EditDetailsScreen(book, covers, suggestions, saving = false, onSave = { _, _ -> }, onClose = {}) }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun editDetails() = editScreen("25_edit_details_landscape")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun editDetailsPortrait() = editScreen("26_edit_details_portrait")

    @Test
    @Config(qualifiers = LANDSCAPE_NIGHT)
    fun editDetailsDark() = editScreen("27_edit_details_dark")

    @Test
    @Config(qualifiers = PHONE)
    fun editDetailsPhone() = editScreen("28_edit_details_phone")

    private fun coverScreen(name: String, asIs: Boolean = false) {
        val (lib, _) = library()
        val book = lib.titled("The Histories").copy(coverAsIs = asIs)
        val picture = runBlocking { lib.originalCover(book.id) }!!
            .let { CoverRendering.decode(it)!! }
            .let { CoverRendering.prepare(it, tr("The book's cover")) }
        shot(name) {
            CoverEditorScreen(book, loadOriginal = { null }, saving = false, onSave = {}, onClose = {}, initialPicture = picture)
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverEditor() = coverScreen("29_cover_editor_landscape")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun coverEditorPortrait() = coverScreen("30_cover_editor_portrait")

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverEditorAsItIs() = coverScreen("75_cover_editor_as_is", asIs = true)

    @Test
    @Config(qualifiers = PHONE)
    fun coverEditorAsItIsPhone() = coverScreen("76_cover_editor_as_is_phone", asIs = true)

    /** The whole app as it starts, with its real view models: the library first, then the Kobo section. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun appStartsOnTheLibrary() {
        val app = ApplicationProvider.getApplicationContext<KoboLibraryApp>()
        val vm = MainViewModel(app)
        val libraryVm = LibraryViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent { KoboLibraryTheme { AppRoot(vm, libraryVm) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(tr("Your library is empty")).assertExists()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/32_app_start.png")

        // Taps need the clock running; it is paused again for the picture (the Kobo screen has a spinner).
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Kobo").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(tr("Connect your Kobo")).assertExists()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/33_app_kobo_section.png")

        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Library").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add files").assertExists()
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun choosingBooksToSend() = libraryScreen("34_library_choosing") {
        sortBy(LibrarySort.PAGES)
        selecting = true
        listOf("The Histories", "Dracula", "The Republic").forEach { t -> picked[it.titled(t).id] = true }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun choosingBooksWithADevicePaired() = libraryScreen("34b_library_choosing_linked", linked = true) {
        selecting = true
        listOf("The Histories", "Dracula").forEach { t -> picked[it.titled(t).id] = true }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun aBookWithADevicePaired() = libraryScreen("34c_library_book_linked", linked = true) { selectedId = it.titled("The Histories").id }

    @Test
    @Config(qualifiers = PHONE)
    fun choosingBooksOnAPhoneWithADevicePaired() = libraryScreen("34d_phone_choosing_linked", linked = true) {
        selecting = true
        picked[it.titled("Dracula").id] = true
    }

    @Test
    @Config(qualifiers = PHONE)
    fun aBookOnAPhoneWithADevicePaired() = libraryScreen("34e_phone_book_linked", linked = true) { selectedId = it.titled("The Histories").id }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun searchingWithTags() = libraryScreen("35_library_search") {
        query = "tag:history and not tag:greece or series:holmes"
        sortBy(LibrarySort.SERIES)
        grid = false
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun newBookDialog() = shot("60_new_book_dialog") { NewBookDialog(onMake = { _, _ -> }, onDismiss = {}) }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun mergeDialog() {
        val (lib, covers) = library()
        // Picked out of order: they're put in series order.
        val books = listOf(lib.titled("The Hound of the Baskervilles"), lib.titled("A Study in Scarlet"), lib.titled("The Sign of the Four"))
        shot("61_merge_dialog") { MergeDialog(books, covers, onMerge = { _, _, _, _ -> }, onDismiss = {}) }
    }

    /** The Histories as a reader's edition has it: nine books, each with its chapters. */
    private fun historiesPages(): List<BookAssembly.Page> {
        val muses = listOf("Clio", "Euterpe", "Thalia", "Melpomene", "Terpsichore", "Erato", "Polymnia", "Urania", "Calliope")
        val pages = ArrayList<BookAssembly.Page>()
        fun add(title: String, words: Int, level: Int?) { pages += BookAssembly.Page(pages.size, "p${pages.size}", title, true, words, level, cover = title == "Cover") }
        add("Cover", 2, 0)
        add("Title Page", 24, 0)
        add("Introduction", 5200, 0)
        muses.forEachIndexed { i, m ->
            add("Book ${"I II III IV V VI VII VIII IX".split(' ')[i]}: $m", 12, 0)
            for (c in 1..4) add("Part $c", 4200 + 300 * ((i + c) % 5), 1)
        }
        add("Notes", 9000, 0)
        return pages
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun splitWhere() {
        val (lib, _) = library()
        shot("62_split_where") {
            SplitBookDialog(lib.titled("The Histories"), loadPages = { historiesPages() }, onSplit = { _, _, _, _, _ -> }, onDismiss = {}, initialPages = historiesPages())
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun splitNames() {
        val (lib, _) = library()
        shot("63_split_names") {
            SplitBookDialog(lib.titled("The Histories"), loadPages = { historiesPages() }, onSplit = { _, _, _, _, _ -> }, onDismiss = {}, initialPages = historiesPages(), initialStep = 1)
        }
    }

    /** Splitting with one cover design for every book: designs suggested for it, and each book's cover in its own colour. */
    private fun splitCovers(name: String?, eachColour: Boolean) {
        val (lib, _) = library()
        val pages = historiesPages()
        val design = CoverDesign(CoverStyle.FRAMED, CoverColors.themes[0], dev.kobolibrary.app.ui.cover.CoverGenre.HISTORICAL, 7)
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                SplitBookDialog(
                    lib.titled("The Histories"), loadPages = { pages }, onSplit = { _, _, _, _, _ -> }, onDismiss = {},
                    initialPages = pages, initialStep = 1, initialSameDesign = true, initialDesign = design, initialEachColour = eachColour,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(dev.kobolibrary.app.tr("How they'll look")).performScrollTo()
        // On to the first book's title, just below the covers, so they're all in sight.
        compose.onNodeWithText(dev.kobolibrary.app.tr("Book {0}", 1), useUnmergedTree = true).performScrollTo()
        // The small covers are drawn away from the screen: seven suggested (one chosen) and one for each of the nine books.
        compose.waitUntil(60_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("cover-thumb"), useUnmergedTree = true).fetchSemanticsNodes().size >= 12 }
        // Covers already shown stay while they're drawn in other colours: time for those.
        Thread.sleep(3_000)
        compose.waitForIdle()
        if (name != null) captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun splitSameDesign() = splitCovers("63b_split_same_design", eachColour = false)

    @Test
    @Config(qualifiers = PORTRAIT)
    fun splitEachColour() {
        splitCovers("63c_split_each_colour", eachColour = true)
        // "Other colours" deals every book's colour out afresh.
        compose.onNodeWithContentDescription(dev.kobolibrary.app.tr("Other colours")).performClick()
        compose.waitUntil(60_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("cover-thumb"), useUnmergedTree = true).fetchSemanticsNodes().size >= 12 }
        Thread.sleep(3_000)
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/63e_split_other_colours.png")
    }

    /**
     * The split's cover choices at a phone's width (as in the dialog there, 411dp less its margins). On its own: the test
     * runner never settles a dialog holding a text box at under 600dp, whatever's in it (Material's own dialog too).
     */
    @Test
    @Config(qualifiers = PHONE)
    fun splitCoversPhone() {
        val design = CoverDesign(CoverStyle.FRAMED, CoverColors.themes[0], dev.kobolibrary.app.ui.cover.CoverGenre.HISTORICAL, 7)
        val books = listOf("Book I: Clio", "Book II: Euterpe", "Book III: Thalia", "Book IV: Melpomene", "Book V: Terpsichore")
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(24.dp),
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.width(315.dp).padding(vertical = 16.dp)) {
                        dev.kobolibrary.app.ui.library.SplitCoverChooser(
                            thumbs = dev.kobolibrary.app.ui.library.rememberCoverThumbs(),
                            books = books.size,
                            textOf = { k -> dev.kobolibrary.app.ui.cover.CoverText(books[k], listOf("Herodotus"), "The Histories", k + 1.0) },
                            suggestions = dev.kobolibrary.app.ui.cover.CoverSet.suggestions(design.genre, 6, kotlin.random.Random(3)),
                            picked = design, onPick = {}, onOtherSuggestions = {}, onBrowse = {},
                            eachColour = true, onEachColour = {}, colourRound = 0, onOtherColours = {},
                        )
                    }
                }
            }
        }
        compose.waitUntil(60_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("cover-thumb"), useUnmergedTree = true).fetchSemanticsNodes().size >= 8 }
        Thread.sleep(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/63f_split_covers_phone.png")
    }

    /** All the designs, to choose one for every book: the cover maker, over the dialog, without pictures of one's own. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun splitAllDesigns() {
        splitCovers(null, eachColour = false)
        compose.onNodeWithText(dev.kobolibrary.app.tr("All designs…")).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(dev.kobolibrary.app.tr("Use this design")).assertExists()
        compose.onNodeWithText(dev.kobolibrary.app.tr("Photos")).assertDoesNotExist()
        Thread.sleep(4_000)
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/63d_split_all_designs.png")
    }

    /**
     * In a book's details, the title, author and series all work alike: a tap copies it, and holding it offers a search
     * for it in KoLib or on the internet.
     */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun detailsTitleAuthorAndSeries() {
        var browse: LibraryBrowseState? = null
        // The details are on the right, beside the list (which can show the same words on a book's card).
        fun biggest(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().maxBy { it.boundsInRoot.left }.boundsInRoot.center
        libraryScreen("65b_details_title_menu", act = {
            compose.mainClock.autoAdvance = true
            compose.onRoot().performTouchInput { longClick(biggest("The Sign of the Four")) }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
        }, configure = { lib ->
            selectedId = lib.titled("The Sign of the Four").id
            browse = this
        })
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(tr("Search the internet")).assertExists()
        compose.onNodeWithText(tr("Search in KoLib")).performClick()
        compose.waitForIdle()
        assertEquals(dev.kobolibrary.app.ui.components.titleSearch("The Sign of the Four"), browse!!.query)
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        fun copied() = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        // A tap copies each: the title, the author, and the series' name without its number.
        compose.onRoot().performTouchInput { click(biggest("The Sign of the Four")) }
        assertEquals("The Sign of the Four", copied())
        compose.onRoot().performTouchInput { click(biggest("Arthur Conan Doyle")) }
        assertEquals("Arthur Conan Doyle", copied())
        compose.onRoot().performTouchInput { click(biggest("Sherlock Holmes #2")) }
        assertEquals("Sherlock Holmes", copied())
        compose.onRoot().performTouchInput { longClick(biggest("Sherlock Holmes #2")) }
        compose.waitForIdle()
        compose.onNodeWithText(tr("Search in KoLib")).performClick()
        compose.waitForIdle()
        assertEquals(dev.kobolibrary.app.ui.components.seriesSearch("Sherlock Holmes"), browse!!.query)
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun addBooksMenu() {
        libraryScreen("65_library_selected", configure = { selectedId = it.titled("Dracula").id })
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(tr("Add books")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tr("Write a new book")).assertExists()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/66_add_books_menu.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun exportDialog() = shot("67_export_dialog") {
        ExportDialog(count = 1, title = "The Histories", initial = SendFormat.KEPUB, onSave = {}, onDismiss = {})
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun oneBookAlreadyThere() {
        val (lib, covers) = library()
        val h = lib.titled("The Histories")
        val held = listOf(HeldDuplicate("a", "The Histories - Herodotus.epub", File("a.epub"), 1_350_000, "The Histories", "Herodotus", h.id))
        val job = JobProgress(JobKind.ADD_FILES, "Adding books", total = 1, done = 1, alreadyThere = 1, finished = true)
        shot("70_already_there_one") { DuplicatesDialog(held, { id -> lib.book(id) }, covers, job, onDone = {}) }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun severalBooksAlreadyThere() {
        val (lib, covers) = library()
        val held = listOf("The Histories" to "Herodotus", "Dracula" to "Bram Stoker", "Moby-Dick" to "Herman Melville").mapIndexed { i, (t, a) ->
            HeldDuplicate("k$i", "$t - $a.epub", File("k$i.epub"), 900_000L + i * 260_000L, t, a, lib.titled(t).id)
        }
        val job = JobProgress(JobKind.ADD_FILES, "Adding books", total = 7, done = 7, succeeded = 4, alreadyThere = 3, finished = true)
        shot("71_already_there_several") { DuplicatesDialog(held, { id -> lib.book(id) }, covers, job, onDone = {}) }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun sendDialog() {
        val (lib, _) = library()
        // Moby-Dick's cover is set to "As it is".
        val books = listOf(lib.titled("The Histories"), lib.titled("Moby-Dick").copy(coverAsIs = true), lib.titled("Dracula"))
        shot("36_send_dialog") {
            SendDialog(books, connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB, onSend = { _, _, _ -> }, onConnect = {}, onDismiss = {})
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun sendSummary() {
        val p = JobProgress(
            JobKind.SEND, "Sending 12 books to the Kobo", total = 12, done = 12, succeeded = 11, alreadyThere = 3, finished = true,
            failures = listOf("Leviathan" to "Couldn't convert it to kepub (unexpected end of the file)."),
            conversionFailedIds = listOf("x"),
        )
        shot("37_send_summary") { JobDialog(p, onHide = {}, onStop = {}, onDone = {}, onHowToEject = {}, onSendAsEpub = {}) }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun searchHelp() = shot("38_search_help") { SearchHelpDialog(onPick = {}, onDismiss = {}) }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun tagPicker() = shot("39_tag_picker") {
        TagPickerDialog(
            tags = listOf("Adventure" to 4, "Ancient Greece" to 3, "Classics" to 12, "Gothic" to 2, "History" to 6, "Philosophy" to 5, "Rome" to 1),
            chosen = setOf("History", "Ancient Greece"),
            matchAll = true,
            onApply = { _, _ -> },
            onDismiss = {},
        )
    }

    private fun generatorScreen(name: String) {
        val text = CoverText("The Hound of the Baskervilles", listOf("Arthur Conan Doyle"), "Sherlock Holmes", 5.0)
        shot(name) {
            CoverGeneratorScreen(text, onUse = { _, _ -> }, onCancel = {}, initial = CoverDesign(CoverStyle.CLASSIC, CoverColors.themes[0]))
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverGenerator() = generatorScreen("40_cover_generator")

    @Test
    fun coverGeneratorGenres() {
        val text = CoverText("The Iron Crown", listOf("Mara Ellison"), "The Ninefold Gate", 6.0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                CoverGeneratorScreen(
                    text, onUse = { _, _ -> }, onCancel = {}, genre = dev.kobolibrary.app.ui.cover.CoverGenre.MAGIC_SCHOOL,
                    initial = CoverDesign(CoverStyle.FRAMED, CoverDesign.boardFor(6.0)!!, dev.kobolibrary.app.ui.cover.CoverGenre.MAGIC_SCHOOL, 7),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(dev.kobolibrary.app.ui.cover.CoverGenre.MAGIC_SCHOOL.label).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/40c_cover_generator_genres.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverGeneratorFramed() {
        val text = CoverText("The Iron Crown", listOf("Mara Ellison"), "The Ninefold Gate", 6.0)
        shot("40b_cover_generator_framed") {
            CoverGeneratorScreen(
                text, onUse = { _, _ -> }, onCancel = {}, genre = dev.kobolibrary.app.ui.cover.CoverGenre.FANTASY,
                initial = CoverDesign(CoverStyle.FRAMED, CoverDesign.boardFor(6.0)!!, dev.kobolibrary.app.ui.cover.CoverGenre.FANTASY, 7),
            )
        }
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun coverGeneratorPortrait() = generatorScreen("41_cover_generator_portrait")

    /** A prequel, book nought of its series, on the framed design: its number in words, in figures, or none. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    fun coverGeneratorSeriesNumber() {
        val text = CoverText("New Dawn", listOf("Mara Ellison"), "The Ninefold Gate", 0.0)
        shot("40e_cover_generator_number", act = {
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(tr("Number in the series")).performScrollTo()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
        }) {
            CoverGeneratorScreen(
                text, onUse = { _, _ -> }, onCancel = {}, genre = dev.kobolibrary.app.ui.cover.CoverGenre.FANTASY,
                initial = CoverDesign(CoverStyle.FRAMED, CoverColors.themes.first { it.name == tr("Spruce") }, dev.kobolibrary.app.ui.cover.CoverGenre.FANTASY, 7),
            )
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(tr("Book {0} of", "Zero")).assertExists()
        compose.onNodeWithText(tr("Book {0} of", "0")).assertExists()
        compose.onNodeWithText(tr("Book of")).assertExists()
    }

    /** The framed design's lines and ornaments in a colour of their own, and "A book of". */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverGeneratorLines() {
        val text = CoverText("Strike at the Northern Pass", listOf("Mara Ellison"), "The Ninefold Gate", 15.0)
        shot("40f_cover_generator_lines", act = {
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(tr("A book of")).performScrollTo().performClick()
            compose.onAllNodesWithContentDescription(tr("Silver"))[2].performScrollTo().performClick()
            compose.waitForIdle()
            Thread.sleep(2_000)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
        }) {
            CoverGeneratorScreen(
                text, onUse = { _, _ -> }, onCancel = {}, genre = dev.kobolibrary.app.ui.cover.CoverGenre.FANTASY,
                initial = CoverDesign(CoverStyle.FRAMED, CoverColors.themes.first { it.name == tr("Dusk") }, dev.kobolibrary.app.ui.cover.CoverGenre.FANTASY, 7),
            )
        }
    }

    /** The lettering in colours of its own: the title and author in gold, the series in white. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverGeneratorLettering() {
        val text = CoverText("The Hound of the Baskervilles", listOf("Arthur Conan Doyle"), "Sherlock Holmes", 5.0)
        shot("40d_cover_generator_lettering", act = {
            compose.mainClock.autoAdvance = true
            compose.onAllNodesWithContentDescription(tr("Gold"))[0].performScrollTo().performClick()
            compose.onAllNodesWithContentDescription(tr("White"))[1].performScrollTo().performClick()
            compose.waitForIdle()
            // The preview is drawn away from the main thread.
            Thread.sleep(2_000)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
        }) {
            CoverGeneratorScreen(text, onUse = { _, _ -> }, onCancel = {}, initial = CoverDesign(CoverStyle.CLASSIC, CoverColors.themes[0]))
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(tr("Lettering")).assertExists()
    }

    /** Every design in every theme and in two colours of its own, to look over. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun allGeneratedCovers() {
        val gen = CoverGenerator(context)
        val text = CoverText("The Count of Monte Cristo", listOf("Alexandre Dumas", "Robin Buss"), "Penguin Classics", 12.0)
        val dir = File("${TestLanguage.folder("build/outputs/roborazzi")}/covers").apply { mkdirs() }
        val custom = listOf(CoverColors.fromHue(350f, 0.8f).copy(name = "custom-dark"), CoverColors.fromHue(160f, 0.7f, dark = false).copy(name = "custom-light"))
        for (style in CoverStyle.entries) for (colors in CoverColors.themes + custom) {
            val bmp = gen.render(text, CoverDesign(style, colors), 536, 724)
            File(dir, "${style.name.lowercase()}_${colors.name.lowercase()}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        // And the words that are hard to fit.
        val hard = listOf(
            CoverText("Emma", listOf("Jane Austen"), null, null),
            CoverText("The Strange Case of Dr Jekyll and Mr Hyde and Other Tales of Terror", listOf("Robert Louis Stevenson"), "The Works of Robert Louis Stevenson, Swanston Edition", 5.0),
            CoverText("Incomprehensibilities", listOf("A. N. Other", "Someone Else", "A Third Person", "And A Fourth"), "Words", 1.5),
            CoverText("吾輩は猫である", listOf("夏目漱石"), "猫", 1.0),
            CoverText("Ἰλιάς", listOf("Ὅμηρος"), null, null),
            CoverText("Война и мир", listOf("Лев Николаевич Толстой"), "Русская классика", 3.0),
            CoverText("A".repeat(120), emptyList(), null, null),
            CoverText("", listOf("Unknown"), null, null),
        )
        hard.forEachIndexed { i, t ->
            for (style in CoverStyle.entries) {
                val bmp = gen.render(t, CoverDesign(style, CoverColors.themes[(i + style.ordinal) % CoverColors.themes.size]), 536, 724)
                File(dir, "hard${i}_${style.name.lowercase()}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun libraryInTheBookcaseLook() = libraryScreen("44_library_bookcase", theme = ThemeChoice.BOOKCASE) {
        selectedId = it.titled("The Sign of the Four").id
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun libraryInTheBookcaseLookPortrait() = libraryScreen("45_library_bookcase_portrait", theme = ThemeChoice.BOOKCASE)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun lookDialog() = shot("46_look_dialog") { LookDialog(current = ThemeChoice.BOOKCASE, onPick = {}, onDismiss = {}, opening = true, onOpening = {}, openingSound = true, onOpeningSound = {}, openingLight = false, onOpeningLight = {}) }

    @Test
    @Config(qualifiers = PHONE)
    fun aboutOnAPhone() = shot("72_about_phone") { AboutScreen(onBack = {}) }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun about() = shot("73_about") { AboutScreen(onBack = {}) }

    @Test
    @Config(qualifiers = PHONE)
    fun aboutLicence() = shot("74_about_licence") { LicenceText(Notices.all.first(), onBack = {}) }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun libraryInOledBlack() = libraryScreen("76_library_oled", theme = ThemeChoice.OLED) { selectedId = it.titled("Dracula").id }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun coverEditorWithTheOriginal() {
        val (lib, _) = library()
        val book = lib.titled("The Histories")
        val current = CoverRendering.prepare(android.graphics.Bitmap.createBitmap(1072, 1448, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF2C4A6E.toInt())
        }, tr("The book's cover"))
        val squareBytes = FakeKobo.drawCover(FakeKobo.books.first { it.title == "The Iliad" }, 900, 960)
        val first = CoverRendering.prepare(CoverRendering.decode(squareBytes)!!, "The original cover")
        compose.mainClock.autoAdvance = true
        compose.setContent {
            AppSurface {
                CoverEditorScreen(
                    book = book, loadOriginal = { null }, saving = false, onSave = {}, onClose = {},
                    initialPicture = current, onRestoreFirstCover = {}, initialFirstCover = first,
                )
            }
        }
        compose.onNodeWithText(tr("Original cover")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(tr("Restore original")).assertExists()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/47_cover_editor_original.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun descriptionEditor() = shot("31_description_editor") {
        DescriptionEditor("<p>Some <b>text</b>.</p>", "The Histories", onDone = {}, onCancel = {})
    }

    // ---- Continue from the Kobo: under how far the Kobo has got, with a check when the app is further on ----

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun continueFromTheKobo() = libraryScreen("kobo_place_button", continueFromKobo = true) { selectedId = it.titled("The Sign of the Four").id }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun continueFromTheKoboWhenTheAppIsFurtherOn() = libraryScreen(
        "kobo_place_ask", continueFromKobo = true,
        act = {
            compose.onNodeWithText(tr("Continue from the Kobo")).performClick()
            // The dialog has a window of its own, made once the main thread's queue has run.
            val asks = androidx.compose.ui.test.hasText(tr("Go back to the Kobo's place?"))
            var tries = 0
            while (compose.onAllNodes(asks).fetchSemanticsNodes().isEmpty() && tries++ < 40) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                compose.mainClock.advanceTimeBy(100)
            }
            compose.onNode(asks).assertExists()
        },
    ) { selectedId = it.titled("The Histories").id }

    @Test
    @Config(qualifiers = PHONE)
    fun continueFromTheKoboOnAPhone() = libraryScreen(
        "kobo_place_phone", continueFromKobo = true,
        act = { compose.onNodeWithText(tr("Continue from the Kobo")).performScrollTo() },
    ) { selectedId = it.titled("The Sign of the Four").id }
}
