package dev.kolib.app

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.AiMessage
import dev.kolib.app.ai.AiModels
import dev.kolib.app.ai.Device
import dev.kolib.app.ai.FakeEngine
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicPages
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.ui.reader.OpenComic
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** The sample comic that comes with KoLib, The Lighthouse Cat, opened for the reader. Close its pages when done. */
internal fun openSampleComic(context: Context): OpenComic {
    val dir = File(context.cacheDir, "sample-comic-" + UUID.randomUUID()).apply { mkdirs() }
    val file = File(dir, "lighthouse-cat.cbz")
    context.assets.open("samples/lighthouse-cat.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
    val book = LibraryBook(
        id = "lighthouse-cat", meta = BookMetadata(title = "The Lighthouse Cat"), collections = emptyList(),
        fileSize = file.length(), hasCover = false, coverVersion = 0, addedAt = 0, modifiedAt = 0,
        source = BookSource.FILE, originalName = "lighthouse-cat.cbz", koboPath = null, sourceFormat = "CBZ",
    )
    return OpenComic(book, ComicPages(ComicArchives.open(file, libArchive = null)), File(dir, "place"))
}

/**
 * For the pictures on the front page: the AI with a model as if downloaded, which gives the answers in [answer]
 * (a summary, an answer about the book, about the library) as a good model would.
 */
internal fun answeringModel(context: Context, scope: CoroutineScope, answer: (List<AiMessage>) -> String): AiHub {
    // The characters stand still, at the moment the picture is taken.
    dev.kolib.app.ui.ai.mascotsMove = false
    context.getSharedPreferences("ai", Context.MODE_PRIVATE).edit().clear().commit()
    File(context.filesDir, "models").deleteRecursively()
    File(context.filesDir, "ai").deleteRecursively()
    val hub = AiHub(
        context, scope, canRunHere = { true }, deviceMemory = { 7_400_000_000L }, deviceSpeed = { Device.Speed.FAST },
        openLocal = { _, tokens -> FakeEngine(tokens, answer) },
    )
    val model = AiModels.byId("gemma-4-e2b")!!
    hub.models.dir.mkdirs()
    RandomAccessFile(hub.models.file(model), "rw").use { it.setLength(model.bytes) }
    hub.settingsStore.set(hub.settings.value.copy(source = "model:${model.id}"))
    (context as KoLibApp).ai = hub
    return hub
}

/** Waits a moment at a time, letting the screen catch up, until [done]. */
internal fun ComposeContentTestRule.settleUntil(done: () -> Boolean) {
    repeat(400) {
        waitForIdle()
        if (done()) return
        Thread.sleep(50)
    }
    error("never settled")
}

/** The reader's AI window opened, and the chapter summarised in it: what's been read of it, or [whole] of it. */
internal fun ComposeContentTestRule.summariseTheChapter(context: Context, whole: Boolean = false) {
    val app = context as KoLibApp
    onNodeWithTag("reader-companion").performClick()
    settleUntil { onAllNodes(hasTestTag("companion-summarise")).fetchSemanticsNodes().isNotEmpty() }
    if (whole) {
        onNodeWithText(tr("The whole chapter")).performClick()
        waitForIdle()
    }
    onNodeWithTag("companion-summarise").performClick()
    settleUntil { onAllNodes(hasTestTag("companion-summary")).fetchSemanticsNodes().isNotEmpty() && app.ai.busy.value == AiHub.State.IDLE }
    repeat(10) { waitForIdle(); Thread.sleep(50) }
}

/** What the AI says when asked to summarise what's been read of Pride and Prejudice: the first two pages. */
internal const val PRIDE_SUMMARY = "Mrs Bennet has heard that Netherfield Park, the big house nearby, has been let to a rich young man, " +
    "Mr Bingley. She wants her husband to call on him at once, so that their daughters can meet him.\n\n" +
    "- Mr Bingley is single, with four or five thousand a year.\n" +
    "- Mrs Bennet is sure he'll fall for one of the girls.\n" +
    "- Mr Bennet teases her, and says she and the girls could go without him.\n" +
    "- He offers to put in a good word for Lizzy, which annoys his wife."

/** The words on the screen at the first two pages of Pride and Prejudice: its first line, and its last. */
internal const val PRIDE_SCREEN = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\n" +
    "\u201CI desire you will do no such thing. Lizzy is not a bit better than the others; and I am sure she is not half so handsome as Jane, " +
    "nor half so good humoured as Lydia. But you are always giving her the preference.\u201D"

/** What the AI says when asked to summarise the first chapter of Moby Dick. */
internal const val MOBY_SUMMARY = "Ishmael explains that whenever he feels low, he goes to sea. It's his way of shaking off the gloom, " +
    "and this time he's set on a whaling voyage.\n\n" +
    "- He says everyone is drawn to water, from the crowds on Manhattan's waterfront to anyone dreaming by a stream.\n" +
    "- He never goes as a passenger or an officer, only as a plain sailor, because sailors are paid.\n" +
    "- This time he picks a whaler, drawn above all by the great whale itself."
