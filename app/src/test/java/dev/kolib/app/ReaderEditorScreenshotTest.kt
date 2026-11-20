package dev.kolib.app

import android.content.Context
import org.junit.Assert.assertEquals
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kolib.app.reader.HighlightStyle
import dev.kolib.app.reader.ReaderFlow
import dev.kolib.app.reader.HighlightColor
import dev.kolib.app.reader.Annotation
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.ui.test.performScrollTo
import dev.kolib.app.reader.MarginSpot
import dev.kolib.app.reader.MarginItem
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.kobo.KoboRepository
import dev.kolib.app.kobo.KoboTagReader
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.kobo.WriteCheckResult
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.Library
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderPosition
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderTheme
import dev.kolib.app.ui.BrowseState
import dev.kolib.app.ui.DeviceScreen
import dev.kolib.app.ui.KoboExtras
import dev.kolib.app.ui.KoboSort
import dev.kolib.app.ui.reader.Panel
import dev.kolib.app.ui.reader.ReaderScreen
import dev.kolib.app.ui.textedit.TextEditState
import dev.kolib.app.ui.textedit.TextEditorScreen
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

private const val LANDSCAPE = "w1280dp-h800dp-land-mdpi"
private const val PORTRAIT = "w800dp-h1280dp-port-mdpi"
private const val LANDSCAPE_NIGHT = "w1280dp-h800dp-land-night-mdpi"
private const val PHONE = "w411dp-h891dp-port-mdpi"

/** The reader, the text editor and the Kobo tab's new filters, at the Galaxy Tab S9's size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderEditorScreenshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun inLanguage() = TestLanguage.apply()

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        capture(name)
    }

    // ---- the Kobo tab ----------------------------------------------------------------------------

    private fun koboTab(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, configure: BrowseState.() -> Unit) {
        val drive = File(context.cacheDir, "p5-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val repo = KoboRepository(context)
        val session = runBlocking { repo.connect(FakeDocumentsProvider.install(drive)) {} }
        val tags = runBlocking { KoboTagReader.read(session, session.snapshot.books, emptyMap()) { _, _, _ -> } }
        val browse = BrowseState().apply(configure)
        shot(name, theme) {
            DeviceScreen(
                view = KoboView.of(session),
                unplugged = false,
                state = UiState(
                    connection = Connection.Connected(session),
                    writeCheck = WriteCheckResult(true, "ok"),
                    koboTags = tags,
                    readingTags = 412 to 1302,
                ),
                browse = browse,
                onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
                extras = KoboExtras(emptyList(), tags),
            )
        }
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun koboTabWithTagsAndSorts() = koboTab("48_kobo_tags_and_sorts") {
        tags = setOf("Ancient Greece")
        sortBy(KoboSort.RATING)
        selectedBookId = FakeKobo.books.first { it.title == "The Odyssey" }.contentId
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun koboTabByProgress() = koboTab("49_kobo_progress_portrait", ThemeChoice.BOOKCASE) {
        sortBy(KoboSort.PROGRESS)
        collection = "Classics"
    }

    // ---- the reader --------------------------------------------------------------------------------

    private fun garden(): Pair<EpubFileFiles, BookStructure> {
        val f = TestBooks.garden(File(context.cacheDir, "garden-${UUID.randomUUID()}.epub"))
        val files = EpubFileFiles(EpubFile.open(f))
        return files to BookStructure(files)
    }

    /** The page as the WebView would draw it, roughly, for the picture. */
    @Composable
    private fun FakePage(theme: ReaderTheme, columns: Int) {
        val ink = Color(theme.fg)
        val para = "It was a bright cold day in April, and the clocks were striking thirteen. The hallway smelt of boiled cabbage and old rag mats. " +
            "At one end of it a coloured poster, too large for indoor display, had been tacked to the wall."
        Row(Modifier.fillMaxSize().background(Color(theme.bg)).padding(horizontal = 40.dp, vertical = 44.dp), horizontalArrangement = Arrangement.spacedBy(80.dp)) {
            repeat(columns) { c ->
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (c == 0) Text("Chapter 1: Rose", style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = ink), modifier = Modifier.padding(bottom = 24.dp))
                    repeat(if (c == 0) 4 else 5) {
                        Text(para, style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp, color = ink, textAlign = TextAlign.Justify, textIndent = TextIndent(firstLine = 24.sp)))
                    }
                }
            }
        }
    }

    /** A chapter being scrolled, roughly: one column of text, running on past the top and bottom of the screen. */
    @Composable
    private fun FakeScrolledPage(theme: ReaderTheme) {
        val ink = Color(theme.fg)
        val para = "It was a bright cold day in April, and the clocks were striking thirteen. The hallway smelt of boiled cabbage and old rag mats. " +
            "At one end of it a coloured poster, too large for indoor display, had been tacked to the wall. It depicted simply an enormous face, more than a metre wide."
        Box(Modifier.fillMaxSize().background(Color(theme.bg)), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).padding(horizontal = 48.dp).offset(y = (-12).dp)) {
                repeat(9) {
                    Text(para, style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp, color = ink, textAlign = TextAlign.Justify, textIndent = TextIndent(firstLine = 24.sp)))
                }
            }
        }
    }

    private fun reader(
        name: String, theme: ReaderTheme, panel: Panel = Panel.NONE, controls: Boolean = true, columns: Int = 2,
        settings: ReaderSettings = ReaderSettings(theme = theme), prepare: (ReaderController) -> Unit = {},
        autobar: Float? = null,
    ) {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, settings, context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = controls
        c.toggleBookmark() // nothing to bookmark yet: no position
        prepare(c)
        shot(name) {
            ReaderScreen(
                c, onSettings = {}, onClose = {}, onEdit = {},
                pagePreview = {
                    Box(Modifier.fillMaxSize()) {
                        if (settings.scrolling) FakeScrolledPage(theme) else FakePage(theme, columns)
                        // Autoscroll's bar on pages, which the page itself draws (reader.css): at the very foot, 2dp thick, in the bar colour.
                        if (autobar != null) Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth(autobar).height(2.dp).background(Color(settings.barColor))
                        )
                    }
                },
                initialPanel = panel,
            )
        }
    }

    /** The foot of the page while autoscrolling on pages: page info, the progress bar, and autoscroll's bar under it. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerFootBarOn() = reader("78_reader_foot_bar_on", ReaderTheme.PAPER, controls = false, autobar = 0.6f)

    /** The progress bar off: the page info drops into its place. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerFootBarOff() = reader(
        "78_reader_foot_bar_off", ReaderTheme.PAPER, controls = false,
        settings = ReaderSettings(theme = ReaderTheme.PAPER, progressLine = dev.kolib.app.reader.ProgressLine.OFF), autobar = 0.6f,
    )

    /** Dimmed bars on a black page, for an OLED screen. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerFootDimmed() = reader(
        "78_reader_foot_dimmed", ReaderTheme.BLACK, controls = false,
        settings = ReaderSettings(theme = ReaderTheme.BLACK, barShade = dev.kolib.app.reader.BarShade.DIM), autobar = 0.6f,
    )

    /** The same at full strength, to compare. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerFootFull() = reader(
        "78_reader_foot_full", ReaderTheme.BLACK, controls = false,
        settings = ReaderSettings(theme = ReaderTheme.BLACK), autobar = 0.6f,
    )

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerScrolling() = reader("72_reader_scrolling", ReaderTheme.PAPER, controls = false, settings = ReaderSettings(theme = ReaderTheme.PAPER, flow = ReaderFlow.SCROLL))

    /** After moving with the progress bar: the way back to where the reader was, with the controls. */
    private val backThere = dev.kolib.app.reader.ReaderPosition(
        path = "chapter1.xhtml", anchor = null, progress = 0.4f, bookProgress = 0.18f, snippet = "", chapterTitle = "The Walled Garden",
    )

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerBarReturn() = reader("79_reader_bar_return", ReaderTheme.PAPER, prepare = { it.showBarReturnForPreview(backThere) })

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun readerBarReturnPhone() = reader("79_reader_bar_return_phone", ReaderTheme.NIGHT, columns = 1, prepare = { it.showBarReturnForPreview(backThere) })

    /** The reading stats of the book that's open: time left in the chapter, the next and the book, and how long it's been read for. */
    private fun readerStats(name: String, theme: ReaderTheme, columns: Int) {
        val (files, structure) = garden()
        val log = dev.kolib.app.ui.reader.sampleReadingLog(File(context.cacheDir, "stats-${UUID.randomUUID()}.json"), listOf("garden"))
        val session = dev.kolib.app.reader.ReadingSession(log, "garden", "The Garden")
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = theme), context.assets, session = session)
        c.showForPreview(1, 3, 12)
        c.wordsForPreview(IntArray(structure.chapters.size) { 5200 + it * 1900 })
        c.controlsVisible = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(theme, columns) }, initialPanel = Panel.STATS) }
        }
        capture(name)
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerStatsPanel() = readerStats("86_reader_stats", ReaderTheme.SEPIA, 2)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun readerStatsPanelPhone() = readerStats("87_reader_stats_phone", ReaderTheme.NIGHT, 1)

    /** Dragging the progress bar: the page number held still just left of the middle, the chapter beside it. */
    private fun readerDragging(name: String, theme: ReaderTheme, columns: Int, to: Float) {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = theme), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(theme, columns) }) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * 0.1f, centerY))
            moveTo(androidx.compose.ui.geometry.Offset(width * to, centerY))
        }
        capture(name)
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerBarDragging() = readerDragging("79_reader_bar_dragging", ReaderTheme.PAPER, 2, 0.55f)

    @Test
    @Config(qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun readerBarDraggingPhone() = readerDragging("79_reader_bar_dragging_phone", ReaderTheme.NIGHT, 1, 0.8f)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerAutoscrollControls() = reader(
        "73_reader_autoscroll_controls", ReaderTheme.SEPIA, controls = true,
        settings = ReaderSettings(theme = ReaderTheme.SEPIA, flow = ReaderFlow.SCROLL, autoWpm = 260),
        prepare = { it.startAutoscroll(); it.controlsVisible = true },
    )

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerScrollSettings() {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER, flow = ReaderFlow.SCROLL), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        c.lookTab = dev.kolib.app.ui.reader.LookTab.AUTOSCROLL.ordinal
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakeScrolledPage(ReaderTheme.PAPER) }, initialPanel = Panel.LOOK) } }
        compose.waitForIdle()
        compose.onNodeWithText(tr("When you touch the page")).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/74_reader_scroll_settings.png")
    }

    private val gardenNotes = listOf(
        Annotation(path = "OEBPS/text/ch1.xhtml", start = "1/0:0", end = "1/0:40", text = "Rose walked into the garden. She saw a rose by the wall, and Rose smiled.",
            color = HighlightColor.YELLOW, note = "Rose the name against rose the flower: the whole chapter turns on it.", chapterTitle = "Chapter 1: Rose", bookProgress = 0.08f, created = 1_794_300_000_000L),
        Annotation(path = "OEBPS/text/ch1.xhtml", start = "3/0:0", end = "3/0:20", text = "The sign said ROSE in red letters.",
            color = HighlightColor.PINK, style = HighlightStyle.UNDERLINE, chapterTitle = "Chapter 1: Rose", bookProgress = 0.12f, created = 1_794_310_000_000L),
        Annotation(path = "OEBPS/text/ch2.xhtml", start = "2/0:0", end = "2/0:10", text = "The gate was shut, but the latch was loose.",
            color = HighlightColor.BLUE, chapterTitle = "Chapter 2", bookProgress = 0.31f, created = 1_794_320_000_000L),
    )

    private fun readerWithNotes(name: String, panel: Panel, open: Boolean) {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets, bookAuthor = "Ada Lane")
        c.showForPreview(1, 3, 12)
        c.controlsVisible = panel != Panel.NONE
        c.showAnnotationsForPreview(gardenNotes)
        c.contentsTab = 2
        if (open) c.openAnnotation = ReaderController.OpenAnnotation(gardenNotes[0].id, android.graphics.RectF(120f, 160f, 560f, 210f))
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(ReaderTheme.PAPER, 2) }, initialPanel = panel) } }
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerHighlightCard() = readerWithNotes("58_reader_highlight_card", Panel.NONE, open = true)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerHighlightsList() = readerWithNotes("59_reader_highlights", Panel.CONTENTS, open = false)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerControls() = reader("50_reader_controls", ReaderTheme.PAPER)

    /** The margins arranged differently: the book's title top left, chapter top right, pages and time left at the bottom. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerMarginsMoved() = reader(
        "68_reader_margins", ReaderTheme.SEPIA, controls = false,
        settings = ReaderSettings(
            theme = ReaderTheme.SEPIA,
            marginItems = MarginItem.DEFAULTS + mapOf(
                MarginItem.BOOK to MarginSpot.TOP_LEFT,
                MarginItem.CHAPTER to MarginSpot.TOP_RIGHT,
                MarginItem.PAGE to MarginSpot.OFF,
                MarginItem.PAGES_LEFT to MarginSpot.BOTTOM_LEFT,
                MarginItem.CLOCK to MarginSpot.TOP_CENTER,
            ),
        ),
    )

    /** Each tab of the reading settings, top to bottom of what shows first. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerSettingsTabs() {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(ReaderTheme.PAPER, 2) }, initialPanel = Panel.LOOK) } }
        dev.kolib.app.ui.reader.LookTab.entries.forEachIndexed { i, tab ->
            c.lookTab = i
            compose.waitForIdle()
            captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/77_reader_settings_${i + 1}_${tab.name.lowercase()}.png")
        }
        compose.onNodeWithText(tr("Controls")).performClick()
        compose.waitForIdle()
        assertEquals(dev.kolib.app.ui.reader.LookTab.CONTROLS.ordinal, c.lookTab)
    }

    /** A swipe across either side panel goes to its next tab, as a tap on the tab does. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerSettingsTabsSwipe() {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(ReaderTheme.PAPER, 2) }, initialPanel = Panel.LOOK) } }
        compose.waitForIdle()
        assertEquals(0, c.lookTab)
        // The settings come in from the right: swipe from right to left across them.
        compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(1220f, 450f), androidx.compose.ui.geometry.Offset(900f, 450f), 250) }
        compose.waitForIdle()
        assertEquals("on to Page", 1, c.lookTab)
        compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(1220f, 450f), androidx.compose.ui.geometry.Offset(900f, 450f), 250) }
        compose.waitForIdle()
        assertEquals("and Layout", 2, c.lookTab)
        compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(900f, 450f), androidx.compose.ui.geometry.Offset(1220f, 450f), 250) }
        compose.waitForIdle()
        assertEquals("and back", 1, c.lookTab)
        compose.onNodeWithText(tr("Page colour")).assertExists()
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerContentsTabsSwipe() {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(ReaderTheme.PAPER, 2) }, initialPanel = Panel.CONTENTS) } }
        compose.waitForIdle()
        assertEquals(0, c.contentsTab)
        // The contents come in from the left.
        compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(380f, 450f), androidx.compose.ui.geometry.Offset(60f, 450f), 250) }
        compose.waitForIdle()
        assertEquals("on to the bookmarks", 1, c.contentsTab)
        compose.onNodeWithText(tr("No bookmarks yet. Tap the middle of a page, then the bookmark icon."), substring = true).assertExists()
        // A tap on a tab still works.
        compose.onNodeWithText(tr("Contents")).performClick()
        compose.waitForIdle()
        assertEquals(0, c.contentsTab)
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerMarginSettings() {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = true
        c.lookTab = dev.kolib.app.ui.reader.LookTab.LAYOUT.ordinal
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { FakePage(ReaderTheme.PAPER, 2) }, initialPanel = Panel.LOOK) } }
        compose.waitForIdle()
        compose.onNodeWithText(tr("Dimmed")).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/69_reader_margin_settings.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerReading() = reader("51_reader_reading", ReaderTheme.SEPIA, controls = false)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun readerLook() = reader("52_reader_look", ReaderTheme.NIGHT, Panel.LOOK)

    /** A picture held down on the page: open it, share it or save it. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun readerHeldPicture() {
        val (files, structure) = garden()
        val c = ReaderController(files, structure, "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets)
        c.showForPreview(1, 3, 12)
        c.controlsVisible = false
        c.heldImage = dev.kolib.app.reader.BookServer.BOOK + "OEBPS/images/rose.jpg"
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, pagePreview = { FakePage(ReaderTheme.PAPER, 1) }) } }
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/53b_reader_held_picture.png")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(tr("Save to Pictures")).assertExists()
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun readerContents() = reader("53_reader_contents_portrait", ReaderTheme.PAPER, Panel.CONTENTS, columns = 1)

    // ---- the editor --------------------------------------------------------------------------------

    private fun editorState(copied: dev.kolib.app.library.CopiedSections? = null): TextEditState {
        val lib = Library(context, File(context.filesDir, "p5lib-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        return TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch1.xhtml", copied)
    }

    @Composable
    private fun FakeEditorPage() {
        Box(Modifier.fillMaxSize().background(Color(0xFFE7E3DA)), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).padding(top = 20.dp).background(Color(0xFFFFFDF8)).padding(horizontal = 56.dp, vertical = 48.dp)) {
                Text("Chapter 1", style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp))
                listOf(
                    "Rose walked into the garden. She saw a rose by the wall, and Rose smiled.",
                    "“Is that you?” called the man from the Rose Inn. He told her Rose was late.",
                    "The sign said ROSE in red letters.",
                ).forEach { Text(it, style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 26.sp), modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }

    private fun editor(name: String, find: Boolean, theme: ThemeChoice = ThemeChoice.SYSTEM) {
        val state = editorState()
        if (find) {
            state.find.open = true
            state.find.query = "Rose"
            state.find.replacement = "Lily"
            state.find.nameMode = true
        }
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(theme) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        if (find) {
            compose.runOnIdle { state.find.search() }
            compose.waitUntil(10_000) { state.find.entries.isNotEmpty() || state.find.error != null }
            check(state.find.error == null) { "search failed: ${state.find.error}" }
            compose.runOnIdle { state.find.remove(state.find.entries[1]) }
            compose.runOnIdle { state.find.select(state.find.entries.first { it.match.flag == dev.kolib.app.epub.FindReplace.Flag.NAME }) }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun editorWithFind() = editor("54_editor_find", find = true)

    @Test
    @Config(qualifiers = PORTRAIT)
    fun editorPortrait() = editor("55_editor_portrait", find = false)

    /** Sections copied out of books, in the panel on the right, to add to the book open. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun editorCopiedSections() {
        val store = dev.kolib.app.library.CopiedSections(File(context.filesDir, "copied-" + UUID.randomUUID()))
        val state = editorState(store)
        val page = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head><body><p>x</p></body></html>"""
        store.add(dev.kolib.app.epub.ChapterOps.CopiedPage("Copyright Notice", "OEBPS/c.xhtml", page.replace("x", (1..105).joinToString(" ") { "word" }),
            emptyList(), emptyMap(), false), "The Wheel of Time")
        Thread.sleep(3)
        store.add(dev.kolib.app.epub.ChapterOps.CopiedPage("Table of Contents", "OEBPS/toc.xhtml", page.replace("x", (1..2905).joinToString(" ") { "word" }),
            emptyList(), emptyMap(), true), "The Wheel of Time")
        Thread.sleep(3)
        store.add(dev.kolib.app.epub.ChapterOps.CopiedPage("Title Page", "OEBPS/tp.xhtml", page.replace("<p>x</p>", "<img src=\"tp.jpg\"/>"),
            listOf(dev.kolib.app.epub.ChapterOps.CopiedFile("OEBPS/tp.jpg", "image/jpeg", byteArrayOf(1))), emptyMap(), false), "The Wheel of Time")
        state.copiedOpen = true
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/58_editor_copied.png")
    }

    /** Upright, find & replace takes the chapters' place beside the page. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun editorPortraitWithFind() = editor("55b_editor_portrait_find", find = true)

    /** On a phone it has the screen to itself. */
    @Test
    @Config(qualifiers = PHONE)
    fun editorPhoneWithFind() = editor("57b_editor_phone_find", find = true)

    /** A phone on its side: the chapters beside the page, as on a tablet held upright. */
    @Test
    @Config(qualifiers = "w891dp-h411dp-land-mdpi")
    fun editorPhoneOnItsSide() = editor("57c_editor_phone_across", find = false)

    /** Upright, the copied sections are at the page's other side, in place of the chapters. */
    @Test
    @Config(qualifiers = PORTRAIT)
    fun editorPortraitCopiedSections() {
        val store = dev.kolib.app.library.CopiedSections(File(context.filesDir, "copied-" + UUID.randomUUID()))
        val state = editorState(store)
        val page = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title></head><body><p>x</p></body></html>"""
        store.add(dev.kolib.app.epub.ChapterOps.CopiedPage("Copyright Notice", "OEBPS/c.xhtml", page.replace("x", (1..105).joinToString(" ") { "word" }),
            emptyList(), emptyMap(), false), "The Wheel of Time")
        state.copiedOpen = true
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/58b_editor_portrait_copied.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE_NIGHT)
    fun editorDark() = editor("56_editor_dark", find = true, theme = ThemeChoice.DARK)

    /** The editor in some of the other looks: their corners, lettering and colours reach its panels and buttons too. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun editorInTheManuscriptLook() = editor("56b_editor_manuscript", find = true, theme = ThemeChoice.MANUSCRIPT)

    @Test
    @Config(qualifiers = LANDSCAPE_NIGHT)
    fun editorInTheHologramLook() = editor("56c_editor_hologram", find = true, theme = ThemeChoice.HOLOGRAM)

    @Test
    @Config(qualifiers = LANDSCAPE_NIGHT)
    fun editorInTheTerminalLook() = editor("56d_editor_terminal", find = true, theme = ThemeChoice.TERMINAL)

    @Test
    @Config(qualifiers = PHONE)
    fun editorPhone() = editor("57_editor_phone", find = false)

    /** Chapters chosen together, to move or delete. */
    private fun editorChoosing(name: String) {
        val state = editorState()
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Chapter 1: Rose").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Part Two").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(tr("{0} chapters selected", 3)).assertExists()
        compose.onRoot().captureRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun editorChoosingChapters() = editorChoosing("59_editor_choosing")

    @Test
    @Config(qualifiers = PORTRAIT)
    fun editorChoosingChaptersPortrait() = editorChoosing("59b_editor_choosing_portrait")

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun editorAddingAChapter() {
        val state = editorState()
        compose.mainClock.autoAdvance = true
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { TextEditorScreen(state, onClose = {}, onRead = {}, pagePreview = { FakeEditorPage() }) } }
        compose.waitForIdle()
        compose.onNodeWithText(tr("Add")).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/64_editor_add_chapter.png")
    }
}
