package dev.kobolibrary.app.ui.textedit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.TestBooks
import dev.kobolibrary.app.library.BookSource
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.reader.BookServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * The editor's page is laid out to the width of the screen it's on (a phone, the editor's side of a tablet
 * held upright), so all of it is in view across, and two fingers zoom in on it and back out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorPageFitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val chapter = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title><meta name="viewport" content="width=600, height=800"/></head><body><p>Text</p></body></html>"""

    @Test
    fun thePageIsToldToFitTheScreensWidth() {
        val (_, page) = BookServer.prepare(chapter, TextEditState.EDITOR_HEAD)
        val ours = """<meta data-kl="" name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5"/>"""
        assertTrue(page.contains(ours))
        // After anything the book says of its own page's width, so the editor's is the one that counts; and it can be
        // zoomed (nothing says it can't).
        assertTrue(page.indexOf(ours) > page.indexOf("width=600"))
        assertFalse(TextEditState.EDITOR_HEAD.contains("user-scalable=no"))
        // The book's own line is still there, to be kept when the chapter's saved; the editor's is marked as its own, to be left out.
        assertTrue(page.contains("""<meta name="viewport" content="width=600, height=800"/>"""))
    }

    @Test
    fun thePageCanBeZoomedWithTwoFingers() {
        val lib = Library(context, File(context.filesDir, "fitlib-" + UUID.randomUUID()), null)
        runBlocking { lib.load() }
        val f = TestBooks.garden(File(context.cacheDir, "g-${UUID.randomUUID()}.epub"))
        runBlocking { lib.import("garden.epub", { f.inputStream() }, BookSource.FILE) }
        val book = lib.books.value.first()
        val state = TextEditState(book.id, book.title, lib, CoroutineScope(Dispatchers.Unconfined), context.assets, "OEBPS/text/ch1.xhtml")
        val view = state.createView(context, dark = false)
        assertTrue(view.settings.supportZoom())
        assertTrue(view.settings.builtInZoomControls)
        assertFalse("No zoom buttons over the page", view.settings.displayZoomControls)
        assertTrue("The page's own word on its width is taken", view.settings.useWideViewPort)
        assertEquals("Its text is the size the page says, whatever the device's text size", 100, view.settings.textZoom)
        state.releaseView()
    }

    @Test
    fun theCodeCanBeZoomedToo() {
        val html = context.assets.open("editor/code.html").use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue(html.contains("width=device-width"))
        assertFalse(html.contains("user-scalable=no"))
        assertFalse(html.contains("maximum-scale=1,"))
    }
}
