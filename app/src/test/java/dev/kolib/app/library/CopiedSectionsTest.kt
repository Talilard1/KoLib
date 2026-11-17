package dev.kolib.app.library

import dev.kolib.app.epub.ChapterOps
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Copied sections are kept in the app's files, through restarts, until they're deleted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CopiedSectionsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun keptUntilDeleted() {
        val dir = tmp.newFolder("copied")
        val page = ChapterOps.CopiedPage(
            title = "Title Page",
            path = "OEBPS/text/title.xhtml",
            text = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head><body><p>The Wheel of Time</p><img src=\"../images/tp.jpg\"/></body></html>",
            files = listOf(ChapterOps.CopiedFile("OEBPS/images/tp.jpg", "image/jpeg", byteArrayOf(1, 2, 3)), ChapterOps.CopiedFile("OEBPS/styles/s.css", "text/css", "p{}".toByteArray())),
            levels = mapOf("OEBPS/text/ch1.xhtml" to 1),
            isContents = false,
        )
        val store = CopiedSections(dir)
        val item = store.add(page, "The Wheel of Time")
        assertEquals("Title Page", item.title)
        assertEquals("The Wheel of Time", item.fromBook)
        assertEquals(4, item.words)
        assertEquals(1, item.pictures)

        // A new start of the app finds it, and it comes back whole.
        val again = CopiedSections(dir)
        assertEquals(listOf(item), again.items.value)
        val back = again.load(item.id)!!
        assertEquals(page.title, back.title)
        assertEquals(page.path, back.path)
        assertEquals(page.text, back.text)
        assertEquals(page.files.map { it.path to it.mediaType }, back.files.map { it.path to it.mediaType })
        assertArrayEquals(byteArrayOf(1, 2, 3), back.files[0].bytes)
        assertEquals(page.levels, back.levels)

        // Newest first.
        Thread.sleep(5)
        val second = again.add(page.let { ChapterOps.CopiedPage("Copyright", it.path, it.text, emptyList(), emptyMap(), false) }, "Another")
        assertEquals(listOf(second.id, item.id), again.items.value.map { it.id })

        again.delete(item.id)
        assertEquals(listOf(second.id), again.items.value.map { it.id })
        assertNull(again.load(item.id))
        assertTrue(CopiedSections(dir).items.value.none { it.id == item.id })
    }
}
