package dev.kolib.app.comic

import android.graphics.BitmapFactory
import dev.kolib.app.epub.BookMetadata
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** A comic's pages, as edited, written out as a CBZ; and its details as ComicInfo.xml. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicWriterTest {
    private val dir: File = Files.createTempDirectory("comic-writer").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun size(bytes: ByteArray) = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).let { it.width to it.height }

    @Test
    fun pagesAreWrittenInTheirNewOrderAsTheyAreUnlessChanged() {
        val source = TestComics.comic(File(dir, "in.cbz"), pages = 4)
        ComicArchives.open(source, libArchive = null).use { a ->
            val pages = listOf(
                DraftPage(1, PageSource.InComic(2)),
                DraftPage(2, PageSource.InComic(0)),
                DraftPage(3, PageSource.InComic(1), turns = 1),
                DraftPage(4, PageSource.InComic(3), half = Half.RIGHT),
            )
            val out = File(dir, "out.cbz")
            ComicWriter.write(pages, { s -> a.bytes((s as PageSource.InComic).index) }, a.comicInfo, out)
            ComicArchives.open(out, libArchive = null).use { b ->
                assertEquals(4, b.pages.size)
                // As they were: the very same pictures.
                assertArrayEquals(a.bytes(2), b.bytes(0))
                assertArrayEquals(a.bytes(0), b.bytes(1))
                // Turned a quarter: on its side; a spread's right half: half as wide.
                val (w, h) = size(a.bytes(1))
                assertEquals(h to w, size(b.bytes(2)))
                val (w3, h3) = size(a.bytes(3))
                assertEquals(w3 / 2 to h3, size(b.bytes(3)))
                assertNotNull(b.comicInfo)
            }
        }
    }

    @Test
    fun aCroppedPageIsTheCropOfIt() {
        val picture = TestComics.page(0)
        val (w, h) = size(picture)
        val out = ComicWriter.render(picture, DraftPage(1, PageSource.InComic(0), crop = Crop(0.25f, 0f, 0.75f, 0.5f)))
        assertEquals(w / 2 to h / 2, size(out))
    }

    @Test
    fun aComicsDetailsAreWrittenAsComicInfo() {
        val meta = BookMetadata(title = "Saga (part 2)", series = "Saga", seriesIndex = 2.0, authors = listOf("Brian K. Vaughan"), description = "<p>Two &amp; more</p>")
        val read = ComicInfo.read(ComicWriter.info(meta, rightToLeft = true), "x.cbz")
        assertEquals("Saga (part 2)", read.meta.title)
        assertEquals("Saga", read.meta.series)
        assertEquals(2.0, read.meta.seriesIndex!!, 0.0)
        assertEquals(listOf("Brian K. Vaughan"), read.meta.authors)
        assertTrue(read.rightToLeft)
    }
}
