package dev.kobolibrary.app

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
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
        configure: LibraryBrowseState.(Library) -> Unit = {},
    ) {
        val (lib, covers) = library()
        val browse = LibraryBrowseState().apply { configure(lib) }
        shot(name, theme) {
            AppFrame(Section.LIBRARY, {}, koboConnected = true, onNarrow = {}) {
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
                )
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
            .let { CoverRendering.prepare(it, "The book's cover") }
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
        compose.onNodeWithText("Your library is empty").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/32_app_start.png")

        // Taps need the clock running; it is paused again for the picture (the Kobo screen has a spinner).
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Kobo").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Connect your Kobo").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/33_app_kobo_section.png")

        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Library").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add EPUB files").assertExists()
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
        fun add(title: String, words: Int, level: Int?) { pages += BookAssembly.Page(pages.size, "p${pages.size}", title, true, words, level) }
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
            SplitBookDialog(lib.titled("The Histories"), loadPages = { historiesPages() }, onSplit = { _, _, _ -> }, onDismiss = {}, initialPages = historiesPages())
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun splitNames() {
        val (lib, _) = library()
        shot("63_split_names") {
            SplitBookDialog(lib.titled("The Histories"), loadPages = { historiesPages() }, onSplit = { _, _, _ -> }, onDismiss = {}, initialPages = historiesPages(), initialStep = 1)
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun addBooksMenu() {
        libraryScreen("65_library_selected", configure = { selectedId = it.titled("Dracula").id })
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Add books").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Write a new book").assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/66_add_books_menu.png")
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
            SendDialog(books, connected = true, freeBytes = 9_800_000_000, initialFormat = SendFormat.KEPUB, onSend = { _, _ -> }, onConnect = {}, onDismiss = {})
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
    @Config(qualifiers = PORTRAIT)
    fun coverGeneratorPortrait() = generatorScreen("41_cover_generator_portrait")

    /** Every design in every theme and in two colours of its own, to look over. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun allGeneratedCovers() {
        val gen = CoverGenerator(context)
        val text = CoverText("The Count of Monte Cristo", listOf("Alexandre Dumas", "Robin Buss"), "Penguin Classics", 12.0)
        val dir = File("build/outputs/roborazzi/covers").apply { mkdirs() }
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
        }, "The book's cover")
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
        compose.onNodeWithText("Original cover").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Restore it unchanged").assertExists()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/47_cover_editor_original.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun descriptionEditor() = shot("31_description_editor") {
        DescriptionEditor("<p>Some <b>text</b>.</p>", "The Histories", onDone = {}, onCancel = {})
    }
}
