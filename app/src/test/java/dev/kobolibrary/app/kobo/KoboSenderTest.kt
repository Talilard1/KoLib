package dev.kobolibrary.app.kobo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.TestEpubs
import dev.kobolibrary.app.fake.FakeDocumentsProvider
import dev.kobolibrary.app.fake.FakeKobo
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
import java.io.File
import java.util.UUID

/** Sending books to the (fake) Kobo and deleting them from it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KoboSenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "send-" + UUID.randomUUID()
    private val drive = File(context.cacheDir, "$name-kobo")
    private val sender = KoboSender(context)

    private fun connect(): KoboSession = runBlocking {
        FakeKobo.create(drive)
        KoboRepository(context).connect(FakeDocumentsProvider.install(drive)) {}
    }

    private fun histories(): Pair<File, BookMetadata> {
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/The Histories.epub"))
        return file to EpubFile.open(file).use { it.opf.readMetadata() }
    }

    private fun chapterOf(file: File): String = EpubFile.open(file).use { epub ->
        epub.readText(epub.contentDocuments().first { it.endsWith("part0000.html") })!!
    }

    private fun coverCacheFiles(imageId: String): List<File> =
        File(drive, ".kobo-images/" + KoboImages.folderFor(imageId)).listFiles().orEmpty().filter { it.name.startsWith("$imageId - ") }

    @Test
    fun aNewBookGoesWhereCalibrePutsItAsAKepub() = runBlocking {
        val session = connect()
        val (file, meta) = histories()
        val newBook = meta.copy(title = "Histories: Book One?")
        assertEquals("Herodotus/Histories_ Book One_ - Herodotus.kepub.epub", sender.targetPath(newBook, null, SendFormat.KEPUB))

        var lastProgress = 0f
        val outcome = sender.send(session, newBook.title, newBook, null, file, SendFormat.KEPUB) { lastProgress = it }
        assertTrue(outcome.toString(), outcome is SendOutcome.Sent)
        outcome as SendOutcome.Sent
        assertEquals("Herodotus/Histories_ Book One_ - Herodotus.kepub.epub", outcome.path)
        assertFalse(outcome.replaced)
        assertNull(outcome.movedFrom)
        assertEquals(0, outcome.textFilesLeftAsTheyWere)
        assertEquals(1f, lastProgress, 0.001f)

        val onKobo = File(drive, outcome.path)
        assertTrue(onKobo.exists())
        val chapter = chapterOf(onKobo)
        assertTrue(chapter, chapter.contains("""<span class="koboSpan" id="kobo.1.1">Herodotus of Halicarnassus...</span>"""))
        assertTrue(chapter.contains("""<div id="book-columns"><div id="book-inner">"""))
        EpubFile.open(onKobo).use { epub ->
            val opf = epub.readText(epub.opfPath)!!
            assertTrue("the cover is marked for the Kobo", Regex("""<item[^>]*id="cover"[^>]*properties="cover-image"""").containsMatchIn(opf))
            assertEquals("the details are untouched", meta.copy(), epub.opf.readMetadata())
        }
        // Nothing is left in the app's working folder.
        assertTrue(File(context.cacheDir, "send").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aBookAlreadyThereIsReplacedInPlaceAndItsOldCoversCleared() = runBlocking {
        val session = connect()
        val (file, meta) = histories()
        val there = session.snapshot.books.first { it.title == "The Histories" }
        assertEquals("Books/Herodotus/The Histories.kepub.epub", there.relativePath)
        assertEquals(2, coverCacheFiles(there.imageId).size)
        assertNotNull(session.covers.load(there.imageId, CoverSize.THUMBNAIL, 100))
        assertNotNull(session.covers.cached(there.imageId, CoverSize.THUMBNAIL))

        val outcome = sender.send(session, meta.title, meta, there.relativePath, file, SendFormat.KEPUB) as SendOutcome.Sent
        assertEquals(there.relativePath, outcome.path)
        assertTrue(outcome.replaced)
        assertNull(outcome.movedFrom)
        // A different file in its place, with nowhere new offered: the Kobo will take it in afresh.
        assertNull(outcome.keptRecord)
        assertTrue(outcome.koboStartsAfresh)
        assertTrue(chapterOf(File(drive, outcome.path)).contains("Herodotus of Halicarnassus"))
        // The Kobo makes new cover pictures from the new file; the app forgets its copies too.
        assertEquals(emptyList<File>(), coverCacheFiles(there.imageId))
        assertNull(session.covers.cached(there.imageId, CoverSize.THUMBNAIL))
        // Its neighbours are untouched.
        val odyssey = session.snapshot.books.first { it.title == "The Odyssey" }
        assertEquals(2, coverCacheFiles(odyssey.imageId).size)
    }

    // ---- sending a book the Kobo already has -------------------------------------------------------

    private fun reconnect(): KoboSession = runBlocking { KoboRepository(context).connect(FakeDocumentsProvider.install(drive)) {} }

    /** What the Kobo does with a new book once it's unplugged: it takes it into its database. [started]: read a little since. */
    private fun koboTakesIn(path: String, title: String, started: Boolean = true) {
        val id = KoboPaths.contentId(path)
        android.database.sqlite.SQLiteDatabase.openDatabase(
            File(drive, ".kobo/KoboReader.sqlite").path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE or android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { db ->
            db.execSQL(
                """INSERT INTO content (ContentID, ContentType, MimeType, ImageId, Title, ___UserID, ___FileSize, ___PercentRead, ReadStatus, Accessibility, IsDownloaded)
                   VALUES (?, '6', 'application/x-kobo-epub+zip', ?, ?, '', ?, ?, ?, -1, 'true')""",
                arrayOf<Any?>(id, KoboImages.imageIdFromContentId(id), title, File(drive, path).length(), if (started) 42 else 0, if (started) 1 else 0),
            )
        }
    }

    /** A book sent once and taken in by the Kobo; the session after that. */
    private fun sentAndTakenIn(file: File, meta: BookMetadata, started: Boolean = true): Pair<SendOutcome.Sent, KoboSession> = runBlocking {
        val first = sender.send(reconnect(), meta.title, meta, null, file, SendFormat.KEPUB, CoverFitting.BLUR) as SendOutcome.Sent
        assertNull("a new book has no record to keep", first.keptRecord)
        assertFalse(first.koboStartsAfresh)
        koboTakesIn(first.path, meta.title, started)
        first to reconnect()
    }

    @Test
    fun aBookBeingReadSentAgainKeepsTheKobosRecordAndItsNewCoverShowsAfterARestart() = runBlocking {
        connect()
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/again.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(1072, 1448)))
        val meta = EpubFile.open(file).use { it.opf.readMetadata() }.copy(title = "Histories Again")
        val (first, session) = sentAndTakenIn(file, meta, started = true)
        val record = session.snapshot.booksById[KoboPaths.contentId(first.path)]!!
        assertEquals(3, coverCacheFiles(record.imageId).size)

        // The same chapters, the same cover: the record is kept, nothing about the cover to wait for.
        val same = sender.send(session, meta.title, meta, first.path, file, SendFormat.KEPUB, CoverFitting.BLUR, freshPlace = { throw AssertionError("it keeps its place") }) as SendOutcome.Sent
        assertEquals(first.path, same.path)
        assertEquals(mapOf<String, Any?>("___FileSize" to File(drive, first.path).length()), same.keptRecord!!.columns)
        assertFalse(same.coverShowsAfterRestart)

        // A new cover, the same chapters, and the book is being read: its place is kept.
        dev.kobolibrary.app.epub.EpubEditor.replaceCover(file, realJpeg(1200, 1621), "image/jpeg", 1200, 1621)
        val again = sender.send(
            session, meta.title, meta, first.path, file, SendFormat.KEPUB, CoverFitting.BLUR,
            freshPlace = { throw AssertionError("it keeps its place") },
        ) as SendOutcome.Sent
        assertEquals(first.path, again.path)
        assertTrue(again.replaced)
        assertNull(again.movedFrom)
        assertFalse(again.koboStartsAfresh)
        assertTrue("the Kobo shows it once it has restarted", again.coverShowsAfterRestart)
        val kept = again.keptRecord!!
        assertEquals(record.contentId, kept.contentId)
        assertEquals(File(drive, first.path).length(), kept.fileSize)
        assertNull("the pictures keep their usual name", kept.imageId)
        assertEquals(3, coverCacheFiles(record.imageId).size)
        assertNotNull(session.covers.load(record.imageId, CoverSize.THUMBNAIL, 100))
    }

    @Test
    fun aBookNotYetStartedWithANewCoverGoesAsANewBookSoTheKoboShowsItAtOnce() = runBlocking {
        connect()
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/unread.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(1072, 1448)))
        val meta = EpubFile.open(file).use { it.opf.readMetadata() }.copy(title = "Not Started")
        val (first, session) = sentAndTakenIn(file, meta, started = false)
        val record = session.snapshot.booksById[KoboPaths.contentId(first.path)]!!

        dev.kobolibrary.app.epub.EpubEditor.replaceCover(file, realJpeg(1072, 1448, 0xFF1B2A49.toInt()), "image/jpeg", 1072, 1448)
        val again = sender.send(
            session, meta.title, meta, first.path, file, SendFormat.KEPUB, CoverFitting.BLUR,
            freshPlace = { f -> KoboPaths.freeFor(meta, f) { it == KoboPaths.stem(first.path) } },
        ) as SendOutcome.Sent
        assertEquals(first.path.replace(".kepub.epub", " (2).kepub.epub"), again.path)
        assertEquals(first.path, again.movedFrom)
        assertNull(again.keptRecord)
        assertFalse(again.coverShowsAfterRestart)
        assertFalse(File(drive, first.path).exists())
        assertEquals(emptyList<File>(), coverCacheFiles(record.imageId))
        assertEquals(3, coverCacheFiles(KoboImages.imageIdFromContentId(KoboPaths.contentId(again.path))).size)
    }

    @Test
    fun theKobosLibraryPicturesAreTheShapeOfABookCoverSoEveryBookIsTheSameHeight() = runBlocking {
        val session = connect()
        // A cover the shape of a book cover: its pictures are made from it, whole.
        // A blue band across its top, which cropping it to the screen's shape would cut off.
        val banded = android.graphics.Bitmap.createBitmap(1600, 2400, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF7A2E1E.toInt())
            android.graphics.Canvas(this).drawRect(0f, 0f, 1600f, 72f, android.graphics.Paint().apply { color = android.graphics.Color.BLUE })
        }
        val bandedJpeg = java.io.ByteArrayOutputStream().also { banded.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        val book = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/book.epub"), extra = mapOf("OEBPS/cover.jpeg" to bandedJpeg))
        val bookMeta = EpubFile.open(book).use { it.opf.readMetadata() }.copy(title = "Book Shaped")
        val a = sender.send(session, bookMeta.title, bookMeta, null, book, SendFormat.KEPUB, CoverFitting.FILL) as SendOutcome.Sent
        // A cover the shape of the screen, as the cover maker makes them.
        val screen = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/screen.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(1072, 1448)))
        val screenMeta = EpubFile.open(screen).use { it.opf.readMetadata() }.copy(title = "Screen Shaped")
        val b = sender.send(session, screenMeta.title, screenMeta, null, screen, SendFormat.KEPUB, CoverFitting.AS_IS) as SendOutcome.Sent
        assertTrue("a cover the screen's shape gets the app's pictures even when left unchanged", b.coverPicturesWritten)
        for (sent in listOf(a, b)) {
            val imageId = KoboImages.imageIdFromContentId(KoboPaths.contentId(sent.path))
            val dir = File(drive, ".kobo-images/" + KoboImages.folderFor(imageId))
            assertEquals(1072 to 1448, sizeOf(File(dir, "$imageId - N3_FULL.parsed").readBytes()))
            assertEquals(355 to 530, sizeOf(File(dir, "$imageId - N3_LIBRARY_FULL.parsed").readBytes()))
            assertEquals(149 to 223, sizeOf(File(dir, "$imageId - N3_LIBRARY_GRID.parsed").readBytes()))
        }
        // The book-shaped cover was cropped to fill the screen, but its library pictures show all of it.
        assertTrue(a.coverReshaped)
        val gridA = android.graphics.BitmapFactory.decodeFile(File(drive, ".kobo-images/" + KoboImages.folderFor(KoboImages.imageIdFromContentId(KoboPaths.contentId(a.path))) + "/" + KoboImages.imageIdFromContentId(KoboPaths.contentId(a.path)) + " - N3_LIBRARY_GRID.parsed").path)
        val top = gridA.getPixel(74, 2)
        assertTrue("the band at the top of the whole cover is there", android.graphics.Color.blue(top) > 180 && android.graphics.Color.red(top) < 80)
    }

    @Test
    fun aBookWhoseChaptersChangedGoesAsANewBookInANewPlace() = runBlocking {
        connect()
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/changing.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(1072, 1448)))
        val meta = EpubFile.open(file).use { it.opf.readMetadata() }.copy(title = "Changing Book")
        val (first, session) = sentAndTakenIn(file, meta)
        val record = session.snapshot.booksById[KoboPaths.contentId(first.path)]!!

        // Other chapters altogether.
        val rewritten = TestEpubs.epub3(File(context.cacheDir, "$name/rewritten.epub"))
        val again = sender.send(
            session, meta.title, meta, first.path, rewritten, SendFormat.KEPUB, CoverFitting.BLUR,
            freshPlace = { f -> KoboPaths.freeFor(meta, f) { it == KoboPaths.stem(first.path) } },
        ) as SendOutcome.Sent
        assertEquals(first.path.replace(".kepub.epub", " (2).kepub.epub"), again.path)
        assertEquals(first.path, again.movedFrom)
        assertFalse(again.replaced)
        assertNull(again.keptRecord)
        assertFalse("it's a new book in a new place", again.koboStartsAfresh)
        assertFalse(File(drive, first.path).exists())
        assertTrue(File(drive, again.path).exists())
        assertEquals(emptyList<File>(), coverCacheFiles(record.imageId))
    }

    @Test
    fun aCoverLeftAsItIsKeepsTheRecordAndTheKoboMakesItsOwnPictures() = runBlocking {
        connect()
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/asis.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(900, 1300)))
        val meta = EpubFile.open(file).use { it.opf.readMetadata() }.copy(title = "As It Is")
        val (first, session) = sentAndTakenIn(file, meta)
        val record = session.snapshot.booksById[KoboPaths.contentId(first.path)]!!
        val again = sender.send(session, meta.title, meta, first.path, file, SendFormat.KEPUB, CoverFitting.AS_IS, freshPlace = { "unused" }) as SendOutcome.Sent
        assertFalse(again.coverReshaped)
        assertFalse(again.coverPicturesWritten)
        assertNotNull("the book is being read, so its record is kept", again.keptRecord)
        assertTrue("the cover in the book changed from the fitted one to its own", again.coverShowsAfterRestart)
        // The old pictures go, and none are written: the Kobo makes them from the book's own cover.
        assertEquals(emptyList<File>(), coverCacheFiles(record.imageId))
        EpubFile.open(File(drive, again.path)).use { epub ->
            assertEquals(900 to 1300, sizeOf(epub.readBytes(epub.findCover()!!.path)!!))
        }
    }

    @Test
    fun changingFormatMovesTheBookAndDeletesTheOldFile() = runBlocking {
        val session = connect()
        val (file, meta) = histories()
        val kepubPath = "Books/Herodotus/The Histories.kepub.epub"

        val asEpub = sender.send(session, meta.title, meta, kepubPath, file, SendFormat.EPUB) as SendOutcome.Sent
        assertEquals("Books/Herodotus/The Histories.epub", asEpub.path)
        assertFalse(asEpub.replaced)
        assertEquals(kepubPath, asEpub.movedFrom)
        assertFalse(File(drive, kepubPath).exists())
        // Plain EPUB goes exactly as it is.
        assertArrayEquals(file.readBytes(), File(drive, asEpub.path).readBytes())

        // Dracula is a plain EPUB on the Kobo; as a kepub it takes the kepub name.
        val dracula = session.snapshot.books.first { it.title == "Dracula" }
        assertEquals("Books/Bram Stoker/Dracula.epub", dracula.relativePath)
        val draculaFile = File(drive, dracula.relativePath!!).copyTo(File(context.cacheDir, "$name/Dracula.epub"))
        val draculaMeta = EpubFile.open(draculaFile).use { it.opf.readMetadata() }
        val asKepub = sender.send(session, "Dracula", draculaMeta, dracula.relativePath, draculaFile, SendFormat.KEPUB) as SendOutcome.Sent
        assertEquals("Books/Bram Stoker/Dracula.kepub.epub", asKepub.path)
        assertEquals(dracula.relativePath, asKepub.movedFrom)
        assertFalse(File(drive, dracula.relativePath!!).exists())
        assertTrue(chapterOf2(File(drive, asKepub.path)).contains("koboSpan"))
        assertEquals(emptyList<File>(), coverCacheFiles(dracula.imageId))
    }

    private fun chapterOf2(file: File): String = EpubFile.open(file).use { epub -> epub.readText(epub.contentDocuments().first())!! }

    private fun realJpeg(w: Int, h: Int, ground: Int = 0xFF7A2E1E.toInt()): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(ground)
            drawCircle(w / 2f, h / 2f, w / 4f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        }
        return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun oddShapedCoversAreFittedToTheScreenAndTheKobosPicturesWritten() = runBlocking {
        val session = connect()
        // Nearly square, like Project Gutenberg's Iliad.
        val square = realJpeg(1000, 1100)
        val file = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/square.epub"), extra = mapOf("OEBPS/cover.jpeg" to square))
        val original = file.readBytes()
        val meta = EpubFile.open(file).use { it.opf.readMetadata() }.copy(title = "Square")
        val sent = sender.send(session, "Square", meta, null, file, SendFormat.KEPUB, CoverFitting.BLUR) as SendOutcome.Sent
        assertTrue(sent.coverReshaped)
        assertTrue(sent.coverPicturesWritten)

        EpubFile.open(File(drive, sent.path)).use { epub ->
            val cover = epub.findCover()!!
            assertEquals(1072 to 1448, sizeOf(epub.readBytes(cover.path)!!))
            val titlePage = epub.readText(epub.resolve("titlepage.xhtml"))!!
            assertTrue(titlePage, titlePage.contains("viewBox=\"0 0 1072 1448\""))
        }
        val imageId = KoboImages.imageIdFromContentId(KoboPaths.contentId(sent.path))
        val dir = File(drive, ".kobo-images/" + KoboImages.folderFor(imageId))
        for ((suffix, w, h) in KoboCovers.PICTURES) {
            assertEquals(suffix, w to h, sizeOf(File(dir, imageId + suffix).readBytes()))
        }
        // The app's view of the Kobo shows it straight away, and the library's copy is untouched.
        assertNotNull(session.covers.load(imageId, CoverSize.THUMBNAIL, 100))
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun koboShapedCoversAndCoversLeftAsTheyAreKeepTheBooksOwn() = runBlocking {
        val session = connect()
        val shaped = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/shaped.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(1200, 1621)))
        val meta = EpubFile.open(shaped).use { it.opf.readMetadata() }
        val a = sender.send(session, "Shaped", meta.copy(title = "Shaped"), null, shaped, SendFormat.EPUB, CoverFitting.BLUR) as SendOutcome.Sent
        assertFalse("already the screen's shape", a.coverReshaped)
        assertTrue(a.coverPicturesWritten)
        assertArrayEquals(shaped.readBytes(), File(drive, a.path).readBytes())

        val square = TestEpubs.calibreEpub2(File(context.cacheDir, "$name/sq.epub"), extra = mapOf("OEBPS/cover.jpeg" to realJpeg(900, 900)))
        val b = sender.send(session, "Square", meta.copy(title = "Square"), null, square, SendFormat.EPUB, CoverFitting.AS_IS) as SendOutcome.Sent
        assertFalse(b.coverReshaped)
        assertFalse(b.coverPicturesWritten)
        assertArrayEquals("plain EPUB with covers as they are: exactly the file", square.readBytes(), File(drive, b.path).readBytes())
    }

    @Test
    fun aBookThatCantBeConvertedFailsWithAReasonAndLeavesNothingBehind() = runBlocking {
        val session = connect()
        val broken = TestEpubs.build(File(context.cacheDir, "$name/broken.epub"), mapOf("notes.txt" to "not a book".toByteArray()))
        val meta = BookMetadata(title = "Broken", authors = listOf("Nobody"))
        val outcome = sender.send(session, "Broken", meta, null, broken, SendFormat.KEPUB)
        assertTrue(outcome.toString(), outcome is SendOutcome.Failed && outcome.conversionFailed)
        assertFalse(File(drive, "Nobody").exists())
        assertTrue(File(context.cacheDir, "send").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun textFilesThatArentXhtmlAreSentUnconverted() = runBlocking {
        val session = connect()
        val (file, meta) = histories()
        val messy = TestEpubs.calibreEpub2(
            File(context.cacheDir, "$name/messy.epub"),
            extra = mapOf("OEBPS/text/part0000.html" to "<html><body><p class=\"unclosed>Broken</p></body></html>".toByteArray()),
        )
        val outcome = sender.send(session, "Messy", meta.copy(title = "Messy"), null, messy, SendFormat.KEPUB) as SendOutcome.Sent
        assertEquals(1, outcome.textFilesLeftAsTheyWere)
        assertTrue(File(drive, outcome.path).exists())
        assertTrue(file.exists())
    }

    @Test
    fun deletesSideloadedBooksAndTheirCoversButNeverStoreBooks() = runBlocking {
        val session = connect()
        val histories = session.snapshot.books.first { it.title == "The Histories" }
        val warAndPeace = session.snapshot.allBooks.first { it.title == "War and Peace" }

        val deleted = sender.delete(session, listOf(histories, warAndPeace))
        assertEquals(listOf(histories), deleted)
        assertFalse(File(drive, histories.relativePath!!).exists())
        assertEquals(emptyList<File>(), coverCacheFiles(histories.imageId))
        assertTrue("store books keep their covers", coverCacheFiles(warAndPeace.imageId).isNotEmpty())

        // Already gone counts as deleted, so the app can stop showing it.
        assertEquals(listOf(histories), sender.delete(session, listOf(histories)))
        // The rest of the Kobo is untouched.
        assertTrue(File(drive, "Books/Homer/The Odyssey.kepub.epub").exists())
        assertTrue(File(drive, ".kobo/KoboReader.sqlite").exists())
    }

    @Test
    fun findsTheSameBookOnTheKoboByTitleAndAuthor() {
        val session = connect()
        val snapshot = session.snapshot
        fun find(title: String, vararg authors: String) = KoboSender.findOnKobo(snapshot, BookMetadata(title = title, authors = authors.toList()))?.relativePath

        assertEquals("Books/Herodotus/The Histories.kepub.epub", find("The Histories", "Herodotus"))
        assertEquals("Books/Herodotus/The Histories.kepub.epub", find("the histories ", "HERODOTUS"))
        assertEquals("Books/Niccolò Machiavelli/The Prince.kepub.epub", find("The Prince", "Niccolo Machiavelli"))
        assertNull("another author's book of the same name", find("The Histories", "Tacitus"))
        assertNull("not on the Kobo", find("The Aeneid", "Virgil"))
        assertNull("store books can't be replaced", find("War and Peace", "Leo Tolstoy"))
        assertNull(find("", "Herodotus"))
        // A copy another library book is linked to is that book's, not this one's (a second copy of a book).
        assertNull(KoboSender.findOnKobo(snapshot, BookMetadata(title = "The Histories", authors = listOf("Herodotus"))) { it == "Books/Herodotus/The Histories.kepub.epub" })
    }

    @Test
    fun aSecondCopyOfABookGetsAPlaceOfItsOwn() {
        val meta = BookMetadata(title = "Dune", authors = listOf("Frank Herbert"), authorSorts = mapOf("Frank Herbert" to "Herbert, Frank"))
        val first = KoboPaths.forBook(meta, SendFormat.KEPUB)
        assertEquals("Herbert, Frank/Dune - Frank Herbert.kepub.epub", first)
        // Nothing there yet: the usual place.
        assertEquals(first, KoboPaths.freeFor(meta, SendFormat.KEPUB) { false })
        // The first copy is there (in either format): the second is numbered, and so on.
        val taken = setOf(KoboPaths.stem("Herbert, Frank/Dune - Frank Herbert.epub"))
        assertEquals("Herbert, Frank/Dune - Frank Herbert (2).kepub.epub", KoboPaths.freeFor(meta, SendFormat.KEPUB) { it in taken })
        val two = taken + KoboPaths.stem("Herbert, Frank/Dune - Frank Herbert (2).kepub.epub")
        assertEquals("Herbert, Frank/Dune - Frank Herbert (3).epub", KoboPaths.freeFor(meta, SendFormat.EPUB) { it in two })
        assertEquals(KoboPaths.stem("a/B.kepub.epub"), KoboPaths.stem("A/b.EPUB"))
    }

    @Test
    fun pathsAreSafeForTheKobo() {
        assertEquals("_", KoboPaths.component("  ", 60))
        assertEquals("hack", KoboPaths.component(".hack", 60))
        assertEquals("What If_", KoboPaths.component("What If?", 60))
        assertEquals("a_b_c_d", KoboPaths.component("a/b\\c:d", 60))
        assertEquals("Ends here", KoboPaths.component("Ends here...", 60))
        assertEquals(110, KoboPaths.component("x".repeat(300), 110).length)

        val doyle = BookMetadata(title = "The Sign of the Four", authors = listOf("Arthur Conan Doyle"), authorSorts = mapOf("Arthur Conan Doyle" to "Doyle, Arthur Conan"))
        assertEquals("Doyle, Arthur Conan/The Sign of the Four - Arthur Conan Doyle.kepub.epub", KoboPaths.forBook(doyle, SendFormat.KEPUB))
        assertEquals("Unknown/Untitled - Unknown.epub", KoboPaths.forBook(BookMetadata(title = "Untitled"), SendFormat.EPUB))

        assertEquals("A/B.epub", KoboPaths.withFormat("A/B.kepub.epub", SendFormat.EPUB))
        assertEquals("A/B.kepub.epub", KoboPaths.withFormat("A/B.epub", SendFormat.KEPUB))
        assertEquals("A/B.kepub.epub", KoboPaths.withFormat("A/B.EPUB", SendFormat.KEPUB))
        assertEquals("A/B.kepub.epub", KoboPaths.withFormat("A/B.kepub.epub", SendFormat.KEPUB))
        assertEquals(SendFormat.KEPUB, SendFormat.of("x/Y.KEPUB.EPUB"))
        assertEquals(SendFormat.EPUB, SendFormat.of("x/Y.epub"))
        assertEquals("file:///mnt/onboard/A/B.epub", KoboPaths.contentId("/A/B.epub"))
    }
}
