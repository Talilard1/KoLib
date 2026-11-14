package dev.kobolibrary.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Finding the books in a folder, and in the folders inside it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookFoldersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun findsTheBooksInAFolderAndTheFoldersInIt() {
        val root = File(context.cacheDir, "folder-" + UUID.randomUUID())
        fun put(path: String, size: Int = 10) = File(root, path).apply { parentFile?.mkdirs(); writeBytes(ByteArray(size) { 65 }) }
        put("Calibre/Author/A Book (12)/A Book - Author.epub")
        put("Calibre/Author/A Book (12)/cover.jpg")
        put("Calibre/Author/A Book (12)/metadata.opf")
        put("Downloads/Scans/old manual.pdf")
        put("Downloads/story.txt")
        put("Downloads/README.txt")
        put("Downloads/page.html")
        put("Downloads/Kindle book.azw3")
        put("Downloads/empty.epub", size = 0)
        put(".trash/deleted.epub")
        val tree = FakeDocumentsProvider.install(root)
        var counted = 0
        val found = BookFolders.find(context.contentResolver, tree) { counted = it }
        assertEquals(
            listOf("A Book - Author.epub", "Kindle book.azw3", "story.txt", "old manual.pdf").sorted(),
            found.map { it.name }.sorted(),
        )
        assertEquals(4, counted)
        // Each can be opened.
        for (b in found) context.contentResolver.openInputStream(b.uri)!!.use { assertEquals(10, it.readBytes().size) }
    }

    @Test
    fun readMesAndWebPagesArentBooks() {
        assertTrue(BookFolders.looksLikeABook("The Hobbit.epub"))
        assertTrue(BookFolders.looksLikeABook("Notebook of Days.txt"))
        assertTrue(BookFolders.looksLikeABook("Readings in History.pdf"))
        assertFalse(BookFolders.looksLikeABook("README.txt"))
        assertFalse(BookFolders.looksLikeABook("read me.txt"))
        assertFalse(BookFolders.looksLikeABook("LICENSE.txt"))
        assertFalse(BookFolders.looksLikeABook("notes.txt"))
        assertFalse(BookFolders.looksLikeABook("index.html"))
        assertFalse(BookFolders.looksLikeABook("cover.jpg"))
    }
}
