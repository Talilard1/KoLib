package dev.kolib.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.AiMessage
import dev.kolib.app.ai.AiModels
import dev.kolib.app.ai.FakeEngine
import dev.kolib.app.dict.Wiktionary
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.fake.FakeDocumentsProvider
import dev.kolib.app.fake.FakeKobo
import dev.kolib.app.kobo.KnownKobo
import dev.kolib.app.kobo.KoboRepository
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.kobo.LastSeenStore
import dev.kolib.app.reader.ReaderController
import androidx.compose.foundation.layout.size
import dev.kolib.app.reader.ReaderFlow
import dev.kolib.app.reader.Cutouts
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.reader.ReaderTheme
import dev.kolib.app.ui.BrowseState
import dev.kolib.app.ui.DeviceScreen
import dev.kolib.app.ui.KoboExtras
import dev.kolib.app.ui.ai.AiSetupDialog
import dev.kolib.app.ui.reader.ReaderScreen
import dev.kolib.app.ui.theme.AppSurface
import dev.kolib.app.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

private const val TABLET = "w1280dp-h800dp-land-mdpi"
private const val PHONE = "w411dp-h891dp-port-xxhdpi"

/**
 * What 2.0 adds, as it looks and as it works with a model answering: looking a word up without leaving the
 * book, the chapter's summary and questions about the book, the AI settings, and choosing between two Kobos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiScreenshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app get() = context as KoLibApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val asked = ArrayList<List<AiMessage>>()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TestLanguage.apply()
        context.getSharedPreferences("ai", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "models").deleteRecursively()
        File(context.filesDir, "ai").deleteRecursively()
        Wiktionary.fetcher = { url ->
            when (url.substringAfterLast('/')) {
                "brooded" -> javaClass.getResourceAsStream("/wiktionary/brooded.json")!!.bufferedReader().readText()
                "brood" -> javaClass.getResourceAsStream("/wiktionary/brood.json")!!.bufferedReader().readText()
                else -> null
            }
        }
    }

    @After
    fun tearDown() {
        Wiktionary.fetcher = Wiktionary::get
        scope.cancel()
    }

    /** A hub whose model answers as a good one would, for the picture. */
    private fun hub(withModel: Boolean = true): AiHub {
        val hub = AiHub(
            context, scope, canRunHere = { true }, deviceMemory = { 7_400_000_000L }, deviceFast = { true },
            openLocal = { _, tokens ->
                FakeEngine(tokens) { messages ->
                    asked += messages
                    val last = messages.last().content
                    when {
                        last.startsWith("Instruction:") && last.contains("Teh gate") -> "Rosa came back to *the* garden at the end of winter.\n\nThe gate was rusted shut."
                        last.startsWith("Instruction:") -> "Rosa came back to *the* garden at the end of winter."
                        last.contains("Explain \"") -> "Here: The old house stood over the valley in a dark, heavy way, as if it were watching it.\nUsually: verb, to think about something sad or worrying for a long time"
                        last.contains("Summarise it") -> "Rosa comes back to the garden after the winter and finds it overgrown, with the gate rusted shut.\n\n" +
                            "- Rosa climbs the wall and finds the garden gone wild.\n- She uncovers the old sundial under the ivy.\n- A boy from the village offers to help her clear the paths.\n- They find the first green shoots by the pond."
                        else -> "She is the gardener's daughter, who has come back to look after the garden now that he is too old to. So far she has only met the boy from the village."
                    }
                }
            },
        )
        if (withModel) {
            // The model as if it were downloaded: a file of its size (with nothing in it), chosen.
            val model = AiModels.all[1]
            hub.models.dir.mkdirs()
            RandomAccessFile(hub.models.file(model), "rw").use { it.setLength(model.bytes) }
            hub.settingsStore.set(hub.settings.value.copy(source = "model:${model.id}"))
        }
        app.ai = hub
        return hub
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
    }

    /** A book of three chapters with enough in them to summarise and to ask about. */
    private fun garden(): File {
        val chapters = linkedMapOf(
            "ch1" to ("The Wall" to listOf(
                "Rosa came back to the garden at the end of winter, eleven years after her father had locked its gate for the last time.",
                "The gate had rusted shut. She climbed the wall where the old pear tree leaned against it, as she had when she was small.",
                "Inside, the garden had gone wild. Ivy covered the sundial and brambles had closed the paths her father used to rake every morning.",
                "“You'll want a hand with that,” said a boy from the top of the wall. His name was Tam, and he lived at the mill below the village.",
            )),
            "ch2" to ("The Sundial" to listOf(
                "They cleared the ivy from the sundial first. Under it the brass was green, but the words cut round its edge could still be read.",
                "Tam read them out slowly: the hours pass, the garden stays. Rosa had heard her father say it a hundred times.",
                "By the pond they found the first green shoots, pushing up through last year's leaves.",
                "“He planted those the spring before he stopped,” said Rosa. “He thought nobody would see them come up.”",
            )),
            "ch3" to ("The Letter" to listOf(
                "The letter was in the potting shed, in the tin where her father kept his seed packets.",
                "It was addressed to her, and it had been written the week he locked the gate.",
            )),
        )
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:20</dc:identifier><dc:title>The Garden</dc:title><dc:language>en</dc:language></metadata>
  <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
${chapters.keys.joinToString("\n") { """<item id="$it" href="text/$it.xhtml" media-type="application/xhtml+xml"/>""" }}</manifest>
  <spine>${chapters.keys.joinToString("") { """<itemref idref="$it"/>""" }}</spine>
</package>"""
        val nav = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc"><ol>${chapters.entries.joinToString("") { (f, c) -> """<li><a href="text/$f.xhtml">${c.first}</a></li>""" }}</ol></nav></body></html>"""
        val entries = linkedMapOf<String, ByteArray>(
            "META-INF/container.xml" to dev.kolib.app.epub.TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
        )
        for ((f, c) in chapters) entries["OEBPS/text/$f.xhtml"] = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${c.first}</title></head><body><h1>${c.first}</h1>${c.second.joinToString("") { "<p>$it</p>" }}</body></html>""".toByteArray()
        return dev.kolib.app.epub.TestEpubs.build(File(context.cacheDir, "garden-${UUID.randomUUID()}.epub"), entries)
    }

    private fun reader(columns: Int): ReaderController {
        val files = EpubFileFiles(EpubFile.open(garden()))
        val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, ReaderSettings(theme = ReaderTheme.PAPER), context.assets, bookAuthor = "A. Gardener")
        // At the end of the second chapter.
        c.showForPreview(1, 11, 12, columns)
        c.controlsVisible = true
        return c
    }

    @Composable
    private fun Page() {
        Box(Modifier.fillMaxSize().background(Color(0xFFFBF7EE)).padding(horizontal = 48.dp, vertical = 96.dp)) {
            Text(
                "At the top, the old house brooded over the valley, its windows dark except for one. A lamp burned in the turret, small and steady, like a lamp left on for someone expected home.",
                fontSize = 20.sp, lineHeight = 32.sp, color = Color(0xFF2B2620),
            )
        }
    }

    private fun show(c: ReaderController) {
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { Page() }) } }
    }

    /**
     * A phone with a camera in the middle of its top. [placed]: the headings at the foot and the time and battery
     * in the top corners, as the reader starts on such a phone; otherwise as it was, with the heading moved below.
     */
    private fun camera(name: String, placed: Boolean, scroll: Boolean = false) {
        val cut = Cutouts(top = Cutouts.Span(190f, 221f, 32f))
        val base = ReaderSettings.PHONE.copy(theme = ReaderTheme.PAPER).let { if (scroll) it.copy(flow = ReaderFlow.SCROLL) else it }
        val settings = if (placed) base.aroundCamera() else base
        val files = EpubFileFiles(EpubFile.open(garden()))
        val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, settings, context.assets, bookAuthor = "A. Gardener")
        c.showForPreview(1, 3, 12, 1)
        c.controlsVisible = false
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                Box {
                    ReaderScreen(c, onSettings = {}, onClose = {}, onEdit = {}, pagePreview = { Page() }, cutouts = cut)
                    // The camera.
                    Box(Modifier.padding(start = 193.dp, top = 5.dp).size(25.dp).background(Color.Black, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
        compose.waitForIdle()
        // The reader knows where the camera is on its screen, and what's shown keeps clear of it.
        assertEquals(411f, c.cutouts.width)
        assertEquals(placed, !c.settings.cameraInTheWay(c.cutouts))
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
        val clock = compose.onNodeWithText(time).fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        if (placed) {
            // Beside the camera, in the corner, at the top row's usual height.
            assertTrue(clock.right / density < 190f - 8f)
            assertTrue(clock.top / density < 20f)
        } else if (!scroll) {
            // The time is at the foot; the heading, which would be under the camera, is below it.
            assertTrue(clock.top / density > 800f)
        }
        shoot(name)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun aCameraInTheTopOfTheScreenHasTheTimeEitherSideOfIt() = camera("97_camera_placed", placed = true)

    @Test
    @Config(qualifiers = PHONE)
    fun aHeadingUnderTheCameraGoesBelowIt() = camera("97_camera_heading_below", placed = false)

    @Test
    @Config(qualifiers = PHONE)
    fun scrollingWithACameraInTheTopOfTheScreen() = camera("97_camera_scrolling", placed = true, scroll = true)

    @Test
    @Config(qualifiers = PHONE)
    fun scrollingWithTheChaptersTitleUnderTheCamera() = camera("97_camera_scrolling_below", placed = false, scroll = true)

    private val paragraph = "The road climbed out of the village in slow, patient bends. At the top, the old house brooded over the valley, its windows dark except for one."

    /** Where "brooded" is in the page these tests show, in dp. */
    private fun wordAt(columns: Int) = if (columns == 1) android.graphics.RectF(283f, 100f, 361f, 128f) else android.graphics.RectF(330f, 118f, 420f, 142f)

    private fun lookUp(name: String, columns: Int) {
        hub()
        val c = reader(columns)
        c.lookUpForPreview("brooded", paragraph, wordAt(columns))
        show(c)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("look-up-ai")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20_000) { app.ai.busy.value == AiHub.State.IDLE }
        compose.onNodeWithText("The old house stood over the valley in a dark, heavy way, as if it were watching it.").assertExists()
        // The card is a small one by the word: the dictionary's entry waits behind its button.
        compose.onNodeWithTag("look-up-dictionary").assertDoesNotExist()
        val screen = compose.onRoot().fetchSemanticsNode().size
        val small = compose.onNodeWithTag("look-up-card").fetchSemanticsNode().size
        assertTrue("the card is ${small.height} of ${screen.height} high", small.height < screen.height * 0.36f)
        assertTrue(small.width <= screen.width - 2 * 16 * context.resources.displayMetrics.density.toInt())
        // The model was shown the sentence the word is in, and told the book.
        val user = asked.single().last().content
        assertTrue(user.contains("\"At the top, the old house brooded over the valley, its windows dark except for one.\""))
        assertTrue(user.contains("“The Garden” by A. Gardener"))
        shoot(name)
        // Asked for, the entry opens out in the card, which grows to hold it.
        compose.onNodeWithTag("look-up-dictionary-button").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("look-up-dictionary")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("simple past and past participle of brood").assertExists()
        compose.waitForIdle()
        val big = compose.onNodeWithTag("look-up-card").fetchSemanticsNode().size
        assertTrue(big.height > small.height)
        assertTrue(big.height <= screen.height * 0.63f)
        shoot(name + "_dictionary")
    }

    /** The card is as big as what's in it, and follows the answer as it's written: it grows, it doesn't jump. */
    @Test
    @Config(qualifiers = PHONE)
    fun theLookUpCardGrowsAsTheAnswerIsWritten() {
        val gate = java.util.concurrent.Semaphore(0)
        val engine = object : dev.kolib.app.ai.AiEngine {
            override val contextTokens = 4096
            override val lastError: String? = null
            override fun countTokens(text: String) = text.split(' ').size
            override fun generate(messages: List<dev.kolib.app.ai.AiMessage>, maxTokens: Int, temperature: Float, onProgress: (Float) -> Unit, onText: (String) -> Unit): dev.kolib.app.ai.AiEnd {
                onText("Here: The old house")
                gate.acquire()
                onText(" stood over the valley in a dark, heavy way, as if it were watching everything that went on below it, by day and by night, and thinking it over at great length.\nUsually: verb, to think about something for a long time")
                return dev.kolib.app.ai.AiEnd.DONE
            }
            override fun stop() = gate.release()
            override fun close() {}
        }
        val hub = hub()
        app.ai = AiHub(context, scope, canRunHere = { true }, deviceMemory = { 7_400_000_000L }, deviceFast = { true }, openLocal = { _, _ -> engine }).also {
            it.settingsStore.set(hub.settings.value)
        }
        val c = reader(1)
        c.lookUpForPreview("brooded", paragraph, wordAt(1))
        show(c)
        // The first words are there, with more to come.
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("look-up-ai")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        fun height() = compose.onNodeWithTag("look-up-card").fetchSemanticsNode().size.height
        val before = height()
        compose.onNodeWithTag("look-up-time").assertExists()
        // The rest arrives. A frame at a time, the card is somewhere between the two sizes before it reaches the new one.
        compose.mainClock.autoAdvance = false
        gate.release()
        val until = System.currentTimeMillis() + 20_000
        while (app.ai.busy.value != AiHub.State.IDLE && System.currentTimeMillis() < until) Thread.sleep(20)
        val sizes = ArrayList<Int>()
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            sizes += height()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val after = height()
        assertTrue("it grew: $before to $after", after > before + 40)
        assertEquals(after, sizes.last())
        assertTrue("it passed through sizes between: $sizes", sizes.count { it > before + 2 && it < after - 2 } >= 3)
        assertEquals("it only grew", sizes.sorted(), sizes)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun lookingAWordUpInTheBook() = lookUp("90_look_up", 2)

    @Test
    @Config(qualifiers = PHONE)
    fun lookingAWordUpInTheBookOnAPhone() = lookUp("90_look_up_phone", 1)

    @Test
    @Config(qualifiers = PHONE)
    fun lookingAWordUpWithNoModelStillHasTheDictionary() {
        hub(withModel = false)
        val c = reader(1)
        c.lookUpForPreview("brooded", paragraph, wordAt(1))
        show(c)
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("look-up-dictionary")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("look-up-ai").assertDoesNotExist()
        assertTrue(asked.isEmpty())
        // The gist of the entry to start with: the rest is a tap away.
        compose.onNodeWithText("1. (transitive) To keep an egg warm to make it hatch.").assertExists()
        compose.onNodeWithText("1. The young of certain animals, especially a group of young birds or fowl hatched at one time by the same mother.").assertDoesNotExist()
        shoot("90_look_up_no_model_phone")
        compose.onNodeWithTag("look-up-more").performClick()
        compose.onNodeWithText("1. The young of certain animals, especially a group of young birds or fowl hatched at one time by the same mother.").assertExists()
    }

    private fun summary(name: String, columns: Int) {
        hub()
        val c = reader(columns)
        show(c)
        compose.onNodeWithTag("reader-companion").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("companion-summarise")).fetchSemanticsNodes().isNotEmpty() }
        // The window is as big as what's in it: small before there's a summary, bigger with one.
        compose.waitForIdle()
        val screenHeight = context.resources.displayMetrics.heightPixels
        val before = compose.onNodeWithTag("companion-window").fetchSemanticsNode().size.height
        assertTrue("the window is $before of $screenHeight high", before < screenHeight * 0.45f)
        compose.onNodeWithTag("companion-summarise").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("companion-summary")).fetchSemanticsNodes().isNotEmpty() && app.ai.busy.value == AiHub.State.IDLE }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("companion-window").fetchSemanticsNode().size.height > before)
        // It's kept: asked for again, the model isn't.
        assertEquals(1, asked.size)
        assertTrue(File(context.filesDir, "ai/summaries.json").readText().contains("Rosa comes back"))
        shoot(name)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun aChaptersSummary() = summary("91_summary", 2)

    @Test
    @Config(qualifiers = PHONE)
    fun aChaptersSummaryOnAPhone() = summary("91_summary_phone", 1)

    private fun ask(name: String, columns: Int) {
        hub()
        val c = reader(columns)
        show(c)
        compose.onNodeWithTag("reader-companion").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("companion-summarise")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr("Ask")).performClick()
        compose.onNodeWithTag("companion-question").performTextInput("Who is Rosa?")
        compose.onNodeWithTag("companion-send").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("companion-answer")).fetchSemanticsNodes().isNotEmpty() && app.ai.busy.value == AiHub.State.IDLE }
        val sent = asked.single()
        assertTrue(sent.first().content.contains("Never give away"))
        assertTrue(sent.last().content.endsWith("Question: Who is Rosa?"))
        // It was given what the book has said about her so far, and nothing from the chapter not yet read.
        assertTrue(sent.last().content.contains("Rosa came back to the garden at the end of winter"))
        assertTrue(sent.none { it.content.contains("potting shed") })
        shoot(name)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun askingAboutTheBook() = ask("92_ask", 2)

    @Test
    @Config(qualifiers = PHONE)
    fun askingAboutTheBookOnAPhone() = ask("92_ask_phone", 1)

    @Test
    @Config(qualifiers = PHONE)
    fun thePhonesBarKeepsItsLeastUsedButtonsInAMenu() {
        hub()
        show(reader(1))
        compose.onNodeWithTag("reader-more").performClick()
        compose.onNodeWithText(tr("Reading stats")).assertExists()
        compose.onNodeWithText(tr("Edit this chapter's text")).assertExists()
        shoot("93_reader_more_phone")
    }

    /**
     * (No waiting for the screen to settle here: a text field in a dialog takes the focus in these tests, and its
     * blinking cursor never settles.)
     */
    private fun setup(name: String) {
        val hub = hub()
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { AiSetupDialog(onDismiss = {}, hub = hub) } }
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/$name.png")
        assertEquals("gemma-4-e2b", hub.suggested.id)
        assertTrue(hub.ready())
    }

    @Test
    @Config(qualifiers = TABLET)
    fun theAiSettings() = setup("94_ai_setup")

    // No picture of this on a phone: there, Robolectric measures a dialog at two widths in turn (it gives every
    // window the whole screen), and with text boxes in it that never settles. A real phone measures it once.

    private val oneParagraph = dev.kolib.app.ui.textedit.TextEditState.Around(
        "Rosa cmae back to teh garden at the end of winter.", "<p>Rosa cmae back to <i>teh</i> garden at the end of winter.</p>", "",
    )

    private fun assistant(name: String?, auto: Boolean, given: dev.kolib.app.ui.textedit.TextEditState.Around = oneParagraph, asks: Boolean = !auto) {
        hub()
        dev.kolib.app.ui.textedit.EditorAssistantChoice.setAuto(context, auto)
        val lib = dev.kolib.app.library.Library(context, File(context.filesDir, "lib-${UUID.randomUUID()}"), null)
        runBlocking { lib.load() }
        val f = garden()
        runBlocking { lib.import("garden.epub", { f.inputStream() }, dev.kolib.app.library.BookSource.FILE) }
        val book = lib.books.value.first()
        val state = dev.kolib.app.ui.textedit.TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch1.xhtml")
        compose.setContent { AppSurface(ThemeChoice.SYSTEM) { dev.kolib.app.ui.textedit.EditorAssistant(state, onDismiss = {}, given = given) } }
        compose.onNodeWithTag("assistant-spelling").performClick()
        if (!asks) {
            // Let off asking: the change goes straight in, and says so.
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("assistant-done")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("assistant-apply").assertDoesNotExist()
        } else {
            // A change to the writer's own words is shown first, and waits to be accepted.
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("assistant-apply")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("assistant-done").assertDoesNotExist()
        }
        // The model was given the words with their italics marked, and told only to fix them.
        val sent = asked.single().last().content
        assertTrue(sent.startsWith("Instruction: Correct spelling mistakes"))
        assertTrue(sent.contains("Rosa cmae back to *teh* garden at the end of winter."))
        name?.let { shoot(it) }
    }

    /** Over several paragraphs it's given whole paragraphs to change, and says what it leaves alone. */
    @Test
    @Config(qualifiers = TABLET)
    fun theEditorsAssistantChangesWholeParagraphs() {
        val runs = listOf(listOf("Rosa cmae back to *teh* garden at the end of winter.", "Teh gate was rusted shut."))
        assistant("96_assistant_paragraphs", auto = false, given = dev.kolib.app.ui.textedit.TextEditState.Around(runs[0].joinToString("\n\n"), "", "", runs, skipped = 2))
        compose.onNodeWithText("Headings, and paragraphs with links, pictures or special styling, are left as they are.").assertExists()
        compose.onNodeWithTag("assistant-preview").assertTextEquals("Rosa came back to the garden at the end of winter.\n\nThe gate was rusted shut.")
        compose.onNodeWithTag("assistant-note").assertDoesNotExist()
        assertEquals("Instruction: Correct spelling mistakes, typos and punctuation. Change nothing else.\n\nText:\n" + runs[0].joinToString("\n\n"), asked.single().last().content)
    }

    /** Even let off asking, it asks before a change that would lose a link, a picture or styling. */
    @Test
    @Config(qualifiers = PHONE)
    fun theEditorsAssistantStillAsksWhenSomethingWouldBeLost() {
        assistant(null, auto = true, asks = true, given = dev.kolib.app.ui.textedit.TextEditState.Around(oneParagraph.text, oneParagraph.html, "", more = true))
        compose.onNodeWithTag("assistant-note").assertTextEquals("Links, pictures and special styling in it will be lost.")
    }

    /** And before one that comes back much shorter than what went in. */
    @Test
    @Config(qualifiers = PHONE)
    fun theEditorsAssistantStillAsksWhenMuchOfTheTextWouldGo() {
        val long = oneParagraph.text + " " + "It had been a long winter, and the lane was deep in last year's leaves. ".repeat(3).trim()
        assistant(null, auto = true, asks = true, given = dev.kolib.app.ui.textedit.TextEditState.Around(long, "<p>" + long.replace("teh", "<i>teh</i>") + "</p>", ""))
        compose.onNodeWithTag("assistant-note").assertTextEquals("This is much shorter than what you selected.")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun theEditorsAssistantAsksBeforeChangingText() = assistant("96_assistant", auto = false)

    @Test
    @Config(qualifiers = PHONE)
    fun theEditorsAssistantLetOffAsking() = assistant("96_assistant_auto_phone", auto = true)

    @Test
    @Config(qualifiers = TABLET)
    fun choosingBetweenTwoKobos() {
        val drive = File(context.cacheDir, "two-" + UUID.randomUUID()).also { FakeKobo.create(it) }
        val repo = KoboRepository(context)
        val session = runBlocking { repo.connect(FakeDocumentsProvider.install(drive)) {} }
        val view = KoboView.of(session).let { v -> KoboView(v.info, v.snapshot, v.covers, v.readAtMillis, v.databaseBytes, v.databaseHadWal, null) }
        val mine = LastSeenStore.keyOf(view.info)
        val known = listOf(
            KnownKobo(mine, view.info.displayName, "4821", 1_763_000_000_000L, view.snapshot.books.size, view.snapshot.collections.size),
            KnownKobo("N418C2222222", "Kobo Libra Colour", "2222", 1_761_000_000_000L, 212, 9),
        )
        val shown = ArrayList<String>()
        compose.setContent {
            AppSurface(ThemeChoice.SYSTEM) {
                DeviceScreen(
                    view = view, unplugged = false, state = UiState(lastSeen = view, knownKobos = known), browse = BrowseState(),
                    onReconnect = {}, onWriteCheck = {}, onBackupNow = {}, onForget = {}, onOpenBackups = {}, onOpenLog = {},
                    extras = KoboExtras(emptyList(), emptyMap()), onShowKobo = { shown += it },
                )
            }
        }
        compose.onNodeWithTag("kobo-switch").performClick()
        compose.onNodeWithText("Kobo Libra Colour").assertExists()
        shoot("95_two_kobos")
        compose.onNodeWithTag("kobo-N418C2222222").performClick()
        assertEquals(listOf("N418C2222222"), shown)
    }
}
