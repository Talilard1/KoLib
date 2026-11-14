package dev.kobolibrary.app.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.TestEpubs
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
import dev.kobolibrary.app.kobo.KoboRepository
import dev.kobolibrary.app.ui.cover.CoverCrop
import dev.kobolibrary.app.ui.cover.CoverFit
import dev.kobolibrary.app.ui.cover.CoverRendering
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** The library end to end: adding files and Kobo books, editing details, and replacing covers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "test-" + UUID.randomUUID()
    private val root = File(context.filesDir, name)

    private fun library() = Library(context, root, "$name.db")

    private fun realJpeg(w: Int, h: Int, color: Int = 0xFF2C4A6E.toInt()): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(color)
            drawCircle(w / 2f, h / 2f, w / 4f, Paint().apply { this.color = Color.WHITE })
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun histories(): File = TestEpubs.calibreEpub2(
        File(context.cacheDir, "$name/The Histories.epub"),
        extra = mapOf("OEBPS/cover.jpeg" to realJpeg(600, 900)),
    )

    @Test
    fun addsAFileAndKeepsEditsInsideIt() = runBlocking {
        val lib = library().apply { load() }
        val file = histories()
        val added = lib.import("The Histories.epub", { file.inputStream() }, BookSource.FILE)
        assertTrue(added.toString(), added is ImportOutcome.Added)
        val book = (added as ImportOutcome.Added).book
        assertEquals("The Histories", book.title)
        assertEquals(listOf("Herodotus"), book.meta.authors)
        assertEquals("Penguin Classics #3", book.meta.seriesLabel)
        assertEquals(listOf("History", "Classics"), book.meta.tags)
        assertTrue(book.hasCover)
        assertEquals(600, BitmapFactory.decodeFile(lib.coverFile(book.id).path).width)

        // The same book again is recognised.
        assertTrue(lib.import("copy.epub", { file.inputStream() }, BookSource.FILE) is ImportOutcome.AlreadyThere)
        // Something that isn't an EPUB is turned away with a reason.
        val junk = lib.import("notes.epub", { "hello".byteInputStream() }, BookSource.FILE)
        assertTrue(junk is ImportOutcome.Failed)

        val edited = lib.updateDetails(
            book.id,
            book.meta.copy(title = "Histories", authors = listOf("Herodotus", "Tom Holland"), tags = listOf("History"), series = null, seriesIndex = null),
            listOf("Classics", "To Read", "classics"),
        )
        assertEquals("Histories", edited.title)
        assertEquals(listOf("Classics", "To Read"), edited.collections)
        assertTrue(edited.modifiedAt >= book.modifiedAt)

        // The changes are in the file itself, not just the app's list.
        val inFile = EpubFile.open(lib.epubFile(book.id)).use { it.opf.readMetadata() }
        assertEquals("Histories", inFile.title)
        assertEquals(listOf("Herodotus", "Tom Holland"), inFile.authors)
        assertEquals(listOf("History"), inFile.tags)
        assertNull(inFile.series)

        // And they survive the app restarting.
        val again = library().apply { load() }
        val reloaded = again.book(book.id)!!
        assertEquals("Histories", reloaded.title)
        assertEquals(listOf("Classics", "To Read"), reloaded.collections)
        assertEquals(BookSource.FILE, reloaded.source)

        again.delete(listOf(book.id))
        assertFalse(again.bookDir(book.id).exists())
        assertTrue(again.books.value.isEmpty())
    }

    @Test
    fun booksCanBeMadePutTogetherAndSplit() = runBlocking {
        val lib = library().apply { load() }
        // A new book to write, in the collections asked for.
        val fresh = lib.createBook(dev.kobolibrary.app.epub.BookMetadata(title = "My Story", authors = listOf("Sam Writer"), language = "en"), listOf("Writing"))
        assertEquals("My Story", fresh.title)
        assertEquals(listOf("Writing"), fresh.collections)
        // Another with the same title is a book of its own, not "already there".
        val second = lib.createBook(dev.kobolibrary.app.epub.BookMetadata(title = "My Story"))
        assertTrue(second.id != fresh.id)

        val hist = (lib.import("The Histories.epub", { histories().inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        lib.updateDetails(hist.id, hist.meta, listOf("Classics"))
        val gardenFile = dev.kobolibrary.app.epub.TestBooks.garden(File(context.cacheDir, "$name/garden.epub"))
        val garden = (lib.import("garden.epub", { gardenFile.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        lib.updateDetails(garden.id, garden.meta, listOf("Gothic"))

        // Put together: one book in both books' collections, with the first one's cover. Both stay.
        val merged = lib.mergeBooks(listOf(hist.id, garden.id), dev.kobolibrary.app.epub.BookMetadata(title = "Two in One"), coverFrom = 0)
        assertEquals("Two in One", merged.title)
        assertEquals(listOf("Classics", "Gothic"), merged.collections)
        assertTrue(merged.hasCover)
        assertEquals(5, lib.books.value.size)

        // And split back into two, where the second book starts.
        val pages = lib.pagesOf(merged.id)
        val cut = pages.indexOfFirst { it.path.startsWith("OEBPS/book-2/") }
        assertTrue(cut > 0)
        val parts = lib.splitBook(merged.id, listOf(cut), listOf(dev.kobolibrary.app.epub.BookMetadata(title = "First"), dev.kobolibrary.app.epub.BookMetadata(title = "Second")))
        assertEquals(listOf("First", "Second"), parts.map { it.title })
        assertTrue(parts.all { it.collections == listOf("Classics", "Gothic") })
        assertTrue(parts[0].hasCover)
        assertEquals(7, lib.books.value.size)
        // Nothing is left lying about in the folder books are made in.
        assertTrue(File(root, ".incoming").listFiles().orEmpty().isEmpty())
        // A cut past the end makes nothing, and says why.
        try {
            lib.splitBook(hist.id, listOf(99), listOf(dev.kobolibrary.app.epub.BookMetadata(title = "x"), dev.kobolibrary.app.epub.BookMetadata(title = "y")))
            throw AssertionError("A cut past the end shouldn't make anything")
        } catch (e: dev.kobolibrary.app.epub.AssemblyException) {
            assertEquals(7, lib.books.value.size)
        }
    }

    @Test
    fun aBookAlreadyThereCanBeReplacedOrAddedAsWell() = runBlocking {
        val lib = library().apply { load() }
        val book = (lib.import("The Histories.epub", { histories().inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        lib.updateDetails(book.id, book.meta, listOf("Classics"))
        lib.markSent(book.id, "Herodotus/The Histories - Herodotus.kepub.epub")
        lib.updateCover(book.id, realJpeg(1072, 1448, Color.RED), "image/jpeg", 1072, 1448)
        lib.originalTextFile(book.id).writeText("the text as it was before it was edited")
        val before = lib.book(book.id)!!

        // A newer file of the same book: more text and another cover.
        val newer = TestEpubs.calibreEpub2(
            File(context.cacheDir, "$name/newer.epub"),
            extra = mapOf(
                "OEBPS/cover.jpeg" to realJpeg(600, 900, Color.BLUE),
                "OEBPS/text/part0000.html" to ("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>" +
                    "Herodotus of Halicarnassus here presents his research. ".repeat(40) + "</p></body></html>").toByteArray(),
            ),
        )
        // Not asked to keep it aside: turned away as before, with nothing kept.
        val plain = lib.import("newer.epub", { newer.inputStream() }, BookSource.FILE)
        assertTrue(plain is ImportOutcome.AlreadyThere && plain.held == null)
        // Asked to: kept aside, with what's in it, to replace the book or be added as well.
        val held = lib.import("newer.epub", { newer.inputStream() }, BookSource.FILE, hold = true) as ImportOutcome.AlreadyThere
        assertEquals(book.id, held.existing.id)
        assertTrue(held.held!!.exists())
        assertEquals("The Histories", held.meta!!.title)
        assertEquals(1, lib.books.value.size)

        // Replace: the new file goes in the book's place...
        val replaced = lib.replaceWith(book.id, held.held!!, "newer.epub")
        assertEquals(book.id, replaced.id)
        assertEquals(1, lib.books.value.size)
        EpubFile.open(lib.epubFile(book.id)).use { e ->
            assertTrue(e.readText(e.contentDocuments().first { it.endsWith("part0000.html") })!!.contains("presents his research"))
        }
        assertTrue(replaced.words!! > before.words!!)
        assertTrue(replaced.coverVersion > before.coverVersion)
        val thumb = BitmapFactory.decodeFile(lib.coverFile(book.id).path)
        assertTrue("the new file's cover", Color.blue(thumb.getPixel(4, 4)) > 180 && Color.red(thumb.getPixel(4, 4)) < 80)
        assertEquals("newer.epub", replaced.originalName)
        // ...keeping its collections and its place on the Kobo, so it can be updated there.
        assertEquals(listOf("Classics"), replaced.collections)
        assertEquals("Herodotus/The Histories - Herodotus.kepub.epub", replaced.koboPath)
        assertTrue(replaced.modifiedAt >= before.modifiedAt)
        // What was kept of the old file, to put back, belonged to it and has gone.
        assertFalse(lib.hasOriginalText(book.id))
        assertNull(lib.savedOriginalCover(book.id))

        // Keep both: the same file added as a book of its own, as it is.
        val again = lib.import("newer.epub", { newer.inputStream() }, BookSource.FILE, hold = true) as ImportOutcome.AlreadyThere
        val second = lib.addAnyway(again.held!!, "newer.epub")
        assertTrue(second.id != book.id)
        assertEquals("The Histories", second.title)
        assertTrue(second.collections.isEmpty())
        assertNull(second.koboPath)
        assertEquals(2, lib.books.value.size)

        // Files nobody decided about are cleared away the next time the library opens.
        val third = lib.import("newer.epub", { newer.inputStream() }, BookSource.FILE, hold = true) as ImportOutcome.AlreadyThere
        assertTrue(third.held!!.exists())
        library().load()
        assertFalse(third.held!!.exists())
    }

    @Test
    fun aBookCanBeDuplicated() = runBlocking {
        val lib = library().apply { load() }
        val book = (lib.import("The Histories.epub", { histories().inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        lib.updateDetails(book.id, book.meta, listOf("Classics"))
        lib.markSent(book.id, "Herodotus/The Histories - Herodotus.kepub.epub")

        val copy = lib.duplicate(book.id)
        assertTrue(copy.id != book.id)
        assertEquals("The Histories (copy)", copy.title)
        assertEquals(listOf("Herodotus"), copy.meta.authors)
        assertEquals("Penguin Classics #3", copy.meta.seriesLabel)
        assertEquals(listOf("Classics"), copy.collections)
        assertNull("not on the Kobo", copy.koboPath)
        assertTrue(copy.hasCover)
        EpubFile.open(lib.epubFile(copy.id)).use { assertEquals("The Histories (copy)", it.opf.readMetadata().title) }
        // The book itself is as it was.
        assertEquals("The Histories", lib.book(book.id)!!.title)
        EpubFile.open(lib.epubFile(book.id)).use { assertEquals("The Histories", it.opf.readMetadata().title) }

        // Another copy, or a copy of the copy, is numbered.
        assertEquals("The Histories (copy 2)", lib.duplicate(book.id).title)
        assertEquals("The Histories (copy 3)", lib.duplicate(copy.id).title)
        assertEquals(4, lib.books.value.size)
        assertTrue(File(root, ".incoming").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aCopyIsSavedAsEpubOrKepub() {
        val app = ApplicationProvider.getApplicationContext<dev.kobolibrary.app.KoboLibraryApp>()
        val vm = dev.kobolibrary.app.LibraryViewModel(app)
        // The view model's own start (loading the library) first, so it can't overtake what follows.
        val started = System.currentTimeMillis() + 10_000
        while ((!vm.library.loaded || vm.library.books.value.isNotEmpty()) && System.currentTimeMillis() < started) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        repeat(10) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        val file = histories()
        val book = runBlocking { (vm.library.import("The Histories.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book }
        assertEquals("The Histories - Herodotus.kepub.epub", vm.exportName(book.id, dev.kobolibrary.app.kobo.SendFormat.KEPUB))
        val epub = File(context.cacheDir, "$name-copy.epub")
        val kepub = File(context.cacheDir, "$name-copy.kepub.epub")
        fun finish() {
            val deadline = System.currentTimeMillis() + 15_000
            while (vm.making.value != null && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20)
            }
        }
        vm.exportBook(book.id, dev.kobolibrary.app.kobo.SendFormat.EPUB, android.net.Uri.fromFile(epub))
        finish()
        vm.exportBook(book.id, dev.kobolibrary.app.kobo.SendFormat.KEPUB, android.net.Uri.fromFile(kepub))
        finish()
        assertEquals("Saved “The Histories” as a kepub.", vm.message.value)
        // The EPUB is the library's file as it is; the kepub has Kobo's sentence marks.
        assertArrayEquals(vm.library.epubFile(book.id).readBytes(), epub.readBytes())
        EpubFile.open(kepub).use { k -> assertTrue(k.contentDocuments().any { k.readText(it)!!.contains("koboSpan") }) }
    }

    @Test
    fun filesAddedThatAreThereAlreadyWaitToBeToldWhatToDo() {
        val app = ApplicationProvider.getApplicationContext<dev.kobolibrary.app.KoboLibraryApp>()
        val vm = dev.kobolibrary.app.LibraryViewModel(app)
        fun idleUntil(what: String, done: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 15_000
            while (!done() && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(what, done())
        }
        idleUntil("the library loads") { vm.library.loaded }
        repeat(10) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        val file = histories()
        val uri = android.net.Uri.fromFile(file)

        vm.importUris(listOf(uri))
        idleUntil("added") { vm.job.value?.finished == true }
        assertEquals(1, vm.job.value!!.succeeded)
        assertTrue(vm.duplicates.value.isEmpty())
        vm.dismissJob()
        val first = vm.library.books.value.single()

        // The same file twice more: both are kept aside, and the job says they were already there.
        vm.importUris(listOf(uri, uri))
        idleUntil("added again") { vm.job.value?.finished == true }
        assertEquals(2, vm.job.value!!.alreadyThere)
        val held = vm.duplicates.value
        assertEquals(2, held.size)
        assertTrue(held.all { it.existingId == first.id && it.file.exists() && it.title == "The Histories" })

        // One replaces the book, the other is added as a book of its own.
        vm.resolveDuplicates(mapOf(held[0].key to dev.kobolibrary.app.DuplicateChoice.REPLACE, held[1].key to dev.kobolibrary.app.DuplicateChoice.KEEP_BOTH))
        assertTrue("the question goes straight away", vm.duplicates.value.isEmpty())
        assertNull("and so does the finished job it came from", vm.job.value)
        idleUntil("done") { vm.making.value == null && vm.library.books.value.size == 2 }
        assertEquals("Replaced 1 book with the new file. Added 1 book again as a separate book.", vm.message.value)
        assertTrue(held.none { it.file.exists() })
        assertTrue(vm.library.book(first.id)!!.modifiedAt >= first.modifiedAt)

        // Skipped: nothing changes, and the files kept aside go.
        vm.consumeMessage()
        vm.importUris(listOf(uri))
        idleUntil("added once more") { vm.job.value?.finished == true }
        val skipped = vm.duplicates.value.single()
        vm.resolveDuplicates(emptyMap())
        assertEquals("Skipped “The Histories”.", vm.message.value)
        assertFalse(skipped.file.exists())
        assertEquals(2, vm.library.books.value.size)
    }

    @Test
    fun addsKoboBooksAsPlainEpubsWithTheirCollections() = runBlocking {
        val drive = File(context.cacheDir, "$name-kobo").also { FakeKobo.create(it) }
        val repo = KoboRepository(context)
        val session = repo.connect(FakeDocumentsProvider.install(drive)) {}
        val lib = library().apply { load() }

        val outcomes = session.snapshot.books.filter { it.isSideloaded }.map { lib.importFromKobo(session, it) }
        assertTrue(outcomes.filterNot { it is ImportOutcome.Added }.toString(), outcomes.all { it is ImportOutcome.Added })
        assertEquals(16, lib.books.value.size)

        val histories = lib.books.value.first { it.title == "The Histories" }
        assertEquals(BookSource.KOBO, histories.source)
        assertEquals("Books/Herodotus/The Histories.kepub.epub", histories.koboPath)
        assertEquals(listOf("Classics", "History", "To Read"), histories.collections)
        assertTrue(histories.hasCover)
        EpubFile.open(lib.epubFile(histories.id)).use { epub ->
            val chapter = epub.readText(epub.contentDocuments().first())!!
            assertFalse("Kobo spans removed", chapter.contains("koboSpan"))
            assertFalse("Kobo wrappers removed", chapter.contains("book-columns"))
            assertTrue(chapter.contains("It begins here. And so it goes."))
        }

        // The file had no author; the Kobo's record fills the gap.
        assertEquals(listOf("Herman Melville"), lib.books.value.first { it.title == "Moby-Dick" }.meta.authors)
        assertFalse(lib.books.value.first { it.title == "Leviathan" }.hasCover)
        assertEquals("Sherlock Holmes #2", lib.books.value.first { it.title == "The Sign of the Four" }.meta.seriesLabel)

        // Adding again changes nothing; store books can't be copied.
        val again = lib.importFromKobo(session, session.snapshot.books.first { it.title == "The Histories" })
        assertTrue(again is ImportOutcome.AlreadyThere)
        val store = lib.importFromKobo(session, session.snapshot.books.first { it.title == "War and Peace" })
        assertTrue(store is ImportOutcome.Failed)
        assertEquals(16, lib.books.value.size)
    }

    @Test
    fun theFirstCoverIsKeptAndCanBePutBack() = runBlocking {
        val lib = library().apply { load() }
        val file = histories()
        val book = (lib.import("h.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        assertNull("nothing kept until the cover changes", lib.savedOriginalCover(book.id))
        val first = lib.originalCover(book.id)!!
        lib.updateCover(book.id, realJpeg(1072, 1448, Color.RED), "image/jpeg", 1072, 1448)
        assertArrayEquals(first, lib.savedOriginalCover(book.id))
        lib.updateCover(book.id, realJpeg(1072, 1448, Color.GREEN), "image/jpeg", 1072, 1448)
        assertArrayEquals("still the very first one", first, lib.savedOriginalCover(book.id))

        val restored = lib.restoreOriginalCover(book.id)
        assertArrayEquals("back exactly as it was", first, lib.originalCover(book.id))
        assertEquals(4L, restored.coverVersion)
        EpubFile.open(lib.epubFile(book.id)).use { epub ->
            val titlePage = epub.readText(epub.resolve("titlepage.xhtml"))!!
            assertTrue(titlePage, titlePage.contains("viewBox=\"0 0 600 900\""))
        }
        assertEquals(600, BitmapFactory.decodeFile(lib.coverFile(book.id).path).width)
    }

    @Test
    fun changingOnlyCollectionsLeavesTheBookAlone() = runBlocking {
        val lib = library().apply { load() }
        val file = histories()
        val book = (lib.import("h.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        val bytes = lib.epubFile(book.id).readBytes()
        Thread.sleep(5)
        val saved = lib.updateDetails(book.id, book.meta, listOf("History", "To Read"))
        assertEquals(listOf("History", "To Read"), saved.collections)
        assertEquals("not an edit of the book", book.modifiedAt, saved.modifiedAt)
        assertArrayEquals(bytes, lib.epubFile(book.id).readBytes())

        // Sent somewhere new, it's a new book to the Kobo: what was agreed about its collections no longer holds.
        lib.applyKoboAgreement(listOf(Triple(book.id, null, listOf("History"))))
        assertEquals(listOf("History"), lib.book(book.id)!!.koboCollections)
        lib.markSent(book.id, "Herodotus/The Histories - Herodotus.kepub.epub")
        assertNull(lib.book(book.id)!!.koboCollections)
        lib.applyKoboAgreement(listOf(Triple(book.id, listOf("History", "To Read", "Greek"), listOf("History", "To Read", "Greek"))))
        lib.markSent(book.id, "Herodotus/The Histories - Herodotus.kepub.epub")
        assertEquals("the same place keeps it", listOf("History", "To Read", "Greek"), lib.book(book.id)!!.koboCollections)
        assertEquals(listOf("History", "To Read", "Greek"), library().apply { load() }.book(book.id)!!.collections)
    }

    @Test
    fun aNewCoverGoesIntoTheBook() = runBlocking {
        val lib = library().apply { load() }
        val file = histories()
        val book = (lib.import("h.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book

        // Decode the book's own cover the way the cover editor does, and make a fitted cover from it.
        val original = lib.originalCover(book.id)!!
        val decoded = CoverRendering.decode(original)
        assertNotNull(decoded)
        assertEquals(600, decoded!!.width)
        val picture = CoverRendering.prepare(decoded, "The book's cover")
        val rendered = CoverRendering.render(picture, CoverCrop(fit = CoverFit.FIT_BLUR))
        assertEquals(CoverCrop.OUT_WIDTH, rendered.width)
        assertEquals(CoverCrop.OUT_HEIGHT, rendered.height)
        // Blurred edges, not white, above and below the picture.
        val edge = rendered.getPixel(CoverCrop.OUT_WIDTH / 2, 3)
        assertTrue("edge is tinted by the blur", Color.red(edge) < 240 || Color.blue(edge) < 240)
        val jpeg = CoverRendering.toJpeg(rendered)

        val updated = lib.updateCover(book.id, jpeg, "image/jpeg", CoverCrop.OUT_WIDTH, CoverCrop.OUT_HEIGHT)
        assertEquals(2L, updated.coverVersion)
        assertArrayEquals(jpeg, lib.originalCover(book.id))
        EpubFile.open(lib.epubFile(book.id)).use { epub ->
            val cover = epub.findCover()!!
            assertTrue(cover.declared)
            assertArrayEquals(jpeg, epub.readBytes(cover.path))
            val titlePage = epub.readText(epub.resolve("titlepage.xhtml"))!!
            assertTrue(titlePage, titlePage.contains("viewBox=\"0 0 ${CoverCrop.OUT_WIDTH} ${CoverCrop.OUT_HEIGHT}\""))
        }
        assertEquals(600, BitmapFactory.decodeFile(lib.coverFile(book.id).path).width)
    }

    @Test
    fun aCoverKeptAsItIsIsRememberedAndGoesWithCopies() = runBlocking {
        val lib = library().apply { load() }
        val file = histories()
        val book = (lib.import("h.epub", { file.inputStream() }, BookSource.FILE) as ImportOutcome.Added).book
        assertFalse(book.coverAsIs)
        val bytes = lib.epubFile(book.id).readBytes()
        val own = lib.originalCover(book.id)!!

        // "As it is" with the book's own cover: nothing in the book changes, only how its cover is treated.
        Thread.sleep(5)
        val kept = lib.updateCover(book.id, own, "image/jpeg", 600, 900, asIs = true)
        assertTrue(kept.coverAsIs)
        assertArrayEquals(bytes, lib.epubFile(book.id).readBytes())
        assertNull("nothing was replaced, so there's nothing to put back", lib.savedOriginalCover(book.id))
        assertTrue("a copy on the Kobo had its cover fitted, so it shows as changed", kept.modifiedAt > book.modifiedAt)
        assertTrue("remembered", library().apply { load() }.book(book.id)!!.coverAsIs)

        assertTrue("a copy of the book has the same cover, treated the same way", lib.duplicate(book.id).coverAsIs)

        // A picture fitted to the screen ends it.
        assertFalse(lib.updateCover(book.id, realJpeg(1072, 1448, Color.RED), "image/jpeg", 1072, 1448).coverAsIs)

        // A PNG kept as it is goes into the book as that very PNG.
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        assertTrue(lib.updateCover(book.id, png, "image/png", 500, 800, asIs = true).coverAsIs)
        EpubFile.open(lib.epubFile(book.id)).use { epub ->
            val cover = epub.findCover()!!
            assertEquals("image/png", cover.mediaType)
            assertArrayEquals(png, epub.readBytes(cover.path))
        }
        // "Restore it unchanged" keeps the first cover unchanged on the Kobo too.
        lib.updateCover(book.id, realJpeg(1072, 1448, Color.GREEN), "image/jpeg", 1072, 1448)
        assertTrue(lib.restoreOriginalCover(book.id).coverAsIs)
        assertArrayEquals(own, lib.originalCover(book.id))
    }

    @Test
    fun aLibraryFromTheLastVersionOpensWithItsBooks() {
        // The books table as 0.8.3 left it.
        val dbName = "$name-v3.db"
        val path = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(
                """CREATE TABLE books (id TEXT PRIMARY KEY, title TEXT NOT NULL, title_sort TEXT, authors TEXT NOT NULL, author_sorts TEXT,
                   series TEXT, series_index REAL, tags TEXT NOT NULL, collections TEXT NOT NULL, publisher TEXT, published TEXT, language TEXT,
                   isbn TEXT, description TEXT, rating INTEGER, identifier TEXT, file_size INTEGER NOT NULL, has_cover INTEGER NOT NULL,
                   cover_version INTEGER NOT NULL, added_at INTEGER NOT NULL, modified_at INTEGER NOT NULL, source TEXT NOT NULL,
                   original_name TEXT, kobo_path TEXT, sent_at INTEGER, words INTEGER, kobo_collections TEXT)"""
            )
            db.execSQL("CREATE INDEX books_kobo_path ON books(kobo_path)")
            db.execSQL("CREATE TABLE kobo_collections (name TEXT PRIMARY KEY COLLATE NOCASE)")
            db.execSQL(
                """INSERT INTO books (id, title, authors, tags, collections, file_size, has_cover, cover_version, added_at, modified_at, source, kobo_collections)
                   VALUES ('b1', 'Old Book', '["A. Author"]', '[]', '["Classics"]', 10, 0, 1, 1, 1, 'FILE', '["Classics"]')"""
            )
            db.version = 3
        }
        val db = LibraryDb(context, dbName)
        val old = db.all().single()
        assertEquals("Old Book", old.title)
        assertEquals(listOf("Classics"), old.collections)
        assertEquals(listOf("Classics"), old.koboCollections)
        assertFalse(old.coverAsIs)
        db.upsert(old.copy(coverAsIs = true))
        assertTrue(db.all().single().coverAsIs)
        db.close()
    }
}
