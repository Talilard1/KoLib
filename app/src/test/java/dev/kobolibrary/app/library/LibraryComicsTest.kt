package dev.kobolibrary.app.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.comic.TestComics
import dev.kobolibrary.app.epub.EpubFile
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Comics added to the library: kept as the files they came as, never held whole, with a stand-in EPUB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryComicsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "comics-" + UUID.randomUUID()
    private val root = File(context.filesDir, name)

    private fun library() = Library(context, root, "$name.db")

    @Test
    fun aComicIsKeptAsItCameWithAStandInForItsDetailsAndCover(): Unit = runBlocking {
        val file = TestComics.comic(File(context.cacheDir, "$name/lantern.cbz"))
        val lib = library().apply { load() }
        val added = lib.import("Harbour Lights 03.cbz", { file.inputStream() }, BookSource.FILE)
        assertTrue(added.toString(), added is ImportOutcome.Added)
        val book = (added as ImportOutcome.Added).book
        assertTrue(book.isComic)
        assertEquals("CBZ", book.sourceFormat)
        assertEquals("CBZ comic, kept as it is", book.addedAs)
        assertEquals("The Lantern Thief", book.title)
        assertEquals(listOf("Ada Fenwick", "Tom Reyes"), book.meta.authors)
        assertTrue(book.hasCover)
        val kept = lib.sourceFile(book.id)!!
        assertEquals("source.cbz", kept.name)
        assertEquals(file.length(), kept.length())
        // The stand-in is small: the comic isn't in it twice.
        assertTrue(lib.epubFile(book.id).length() < file.length() / 2)
        EpubFile.open(lib.epubFile(book.id)).use { assertEquals("The Lantern Thief", it.opf.readMetadata().title) }
        // Nothing left behind from bringing it in.
        assertTrue(File(root, "incoming").listFiles().orEmpty().isEmpty())
    }

    /**
     * A comic bigger than all the memory the tests have: added without ever being held whole. Only when asked
     * for (it writes a couple of gigabytes): touch /tmp/kolib-big-comic first.
     */
    @Test
    fun aComicOfGigabytesIsAddedWithoutRunningOutOfMemory(): Unit = runBlocking {
        assumeTrue(File("/tmp/kolib-big-comic").exists())
        val heap = Runtime.getRuntime().maxMemory()
        val file = File(context.cacheDir, "$name/big.cbz")
        file.parentFile?.mkdirs()
        val chunk = ByteArray(8 shl 20) { (it * 31 + 7).toByte() }
        val crc = CRC32().apply { update(chunk) }.value
        var pages = 0
        ZipOutputStream(file.outputStream().buffered(1 shl 20)).use { out ->
            fun put(n: String, bytes: ByteArray, sum: Long) {
                out.putNextEntry(ZipEntry(n).apply { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = sum })
                out.write(bytes)
                out.closeEntry()
            }
            val first = TestComics.page(0)
            put("page0001.jpg", first, CRC32().apply { update(first) }.value)
            while (file.length() < heap + (256L shl 20)) {
                pages++
                put("page%04d.jpg".format(pages + 1), chunk, crc)
                out.flush()
            }
        }
        val size = file.length()
        println("Comic of ${size shr 20} MB, ${pages + 1} pages; the tests have ${heap shr 20} MB of memory")
        val lib = library().apply { load() }
        val t0 = System.nanoTime()
        val added = lib.import("Big.cbz", { file.inputStream() }, BookSource.FILE)
        println("Added in ${(System.nanoTime() - t0) / 1_000_000} ms")
        assertTrue(added.toString(), added is ImportOutcome.Added)
        val book = (added as ImportOutcome.Added).book
        assertEquals(size, lib.sourceFile(book.id)!!.length())
        dev.kobolibrary.app.comic.ComicArchives.open(lib.sourceFile(book.id)!!, libArchive = null).use { c ->
            assertEquals(pages + 1, c.pages.size)
            // The last page, read straight from the far end of the file.
            assertEquals(chunk.size, c.bytes(c.pages.size - 1).size)
        }
        file.delete()
        lib.sourceFile(book.id)?.delete()
    }
}
