package dev.kolib.app.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.convert.BookFormats
import dev.kolib.app.convert.ComicConverter
import dev.kolib.app.convert.SourceFormat
import dev.kolib.app.epub.BookStructure
import dev.kolib.app.epub.EpubFile
import dev.kolib.app.epub.EpubFileFiles
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Comic book files: their pages found and read in order, their details, and the arithmetic of showing them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicTest {
    private val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "comic-" + UUID.randomUUID()).apply { mkdirs() }

    @Test
    fun aCbzsPagesAreItsPicturesInReadingOrder() {
        val file = TestComics.comic(File(dir, "lantern.cbz"))
        ComicArchives.open(file, libArchive = null).use { c ->
            assertTrue(c is ZipComic)
            // page2 before page10; nothing hidden, and nothing Apple's zip leaves behind.
            assertEquals((1..12).map { "pages/page$it.jpg" }, c.pages)
            assertArrayEquals(TestComics.page(4), c.bytes(4))
            assertNotNull(c.comicInfo)
        }
    }

    @Test
    fun whatKindOfComicAFileIsComesFromItsFirstBytes() {
        val zip = TestComics.comic(File(dir, "a.bin"), pages = 1)
        val rar4 = File(dir, "b.bin").apply { writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x00)) }
        val rar5 = File(dir, "c.bin").apply { writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)) }
        val sevenZip = File(dir, "d.bin").apply { writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4)) }
        assertEquals("CBZ", ComicArchives.kindOf(zip))
        assertEquals("CBR", ComicArchives.kindOf(rar4))
        assertEquals("CBR", ComicArchives.kindOf(rar5))
        assertEquals("CB7", ComicArchives.kindOf(sevenZip))
        assertEquals(SourceFormat.CBR, BookFormats.detect(rar5, "book"))
        assertEquals(SourceFormat.CB7, BookFormats.detect(sevenZip, "book"))
        assertEquals(SourceFormat.CBZ, BookFormats.detect(zip, "book"))
        for (n in listOf("a.cbz", "b.CBR", "c.cb7")) assertTrue(n, BookFormats.isBook(n))
        // A RAR needs libarchive, which tests don't have: it's said plainly, not a crash.
        try {
            ComicArchives.open(rar5, libArchive = null)
            fail("opened")
        } catch (e: IOException) {
        }
    }

    @Test
    fun namesFromOldZipProgramsAreRead() {
        // "é" in the old DOS character set, as zips without the UTF-8 mark have it.
        val file = File(dir, "dos.cbz")
        java.util.zip.ZipOutputStream(file.outputStream(), charset("CP437")).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("épisode 1.jpg"))
            out.write(TestComics.page(0))
            out.closeEntry()
        }
        ComicArchives.open(file, libArchive = null).use { assertEquals(listOf("épisode 1.jpg"), it.pages) }
    }

    @Test
    fun comicInfoGivesTheDetailsAndTheReadingDirection() {
        val info = ComicInfo.read(TestComics.INFO.toByteArray(), "harbour-lights-03.cbz")
        assertEquals("The Lantern Thief", info.meta.title)
        assertEquals(listOf("Ada Fenwick", "Tom Reyes"), info.meta.authors)
        assertEquals("Harbour Lights", info.meta.series)
        assertEquals(3.0, info.meta.seriesIndex!!, 0.0)
        assertEquals(listOf("Mystery", "Adventure"), info.meta.tags)
        assertTrue(info.rightToLeft)
        val none = ComicInfo.read(null, "Harbour Lights 03.cbz")
        assertEquals("Harbour Lights 03", none.meta.title)
        assertFalse(none.rightToLeft)
    }

    @Test
    fun aComicsStandInHasItsDetailsAndFirstPageForCover() {
        val file = TestComics.comic(File(dir, "lantern.cbz"))
        val target = File(dir, "stand-in.epub")
        ComicArchives.open(file, libArchive = null).use { ComicConverter.standIn(it, "lantern.cbz", target) }
        EpubFile.open(target).use { epub ->
            assertEquals("The Lantern Thief", epub.opf.readMetadata().title)
            val cover = epub.findCover()
            assertNotNull(cover)
            val bytes = epub.readBytes(cover!!.path)!!
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            assertEquals(600, o.outWidth)
            // Small: only the cover, not the comic.
            assertTrue(target.length() < file.length())
        }
    }

    @Test
    fun aWholeEpubOfAComicHasEveryPageInOrder() {
        val file = TestComics.comic(File(dir, "lantern.cbz"))
        val target = File(dir, "whole.epub")
        ComicArchives.open(file, libArchive = null).use { c -> ComicConverter.wholeEpub(c, ComicInfo.read(c.comicInfo, file.name).meta, target) }
        EpubFile.open(target).use { epub ->
            val files = EpubFileFiles(epub)
            val chapters = BookStructure(files).chapters
            assertEquals(12, chapters.size)
            val images = epub.entryNames().filter { it.startsWith("OEBPS/images/p") }.sorted()
            assertEquals(12, images.size)
            assertArrayEquals(TestComics.page(0), epub.readBytes(images[0]))
            assertArrayEquals(TestComics.page(11), epub.readBytes(images[11]))
        }
    }

    @Test
    fun aCb7GoesToTheKoboAsACbz() {
        val file = TestComics.comic(File(dir, "lantern.cbz"), pages = 3)
        val out = File(dir, "repacked.cbz")
        ComicArchives.open(file, libArchive = null).use { ComicRepack.toCbz(it, out) }
        ComicArchives.open(out, libArchive = null).use { c ->
            assertEquals(listOf("001.jpg", "002.jpg", "003.jpg"), c.pages)
            assertArrayEquals(TestComics.page(2), c.bytes(2))
            assertNotNull(c.comicInfo)
        }
    }

    @Test
    fun pagesAreReadOnlyAsBigAsTheyreShown() {
        assertEquals(1, ComicLayout.sampleFor(1000, 1500, 1000, 1500))
        assertEquals(2, ComicLayout.sampleFor(2000, 3000, 900, 1400))
        assertEquals(4, ComicLayout.sampleFor(8000, 12000, 1600, 2500))
        // Never smaller than asked for.
        assertEquals(1, ComicLayout.sampleFor(1000, 1500, 600, 900))
    }

    @Test
    fun wideSpreadsCanBeSplitIntoTwoPages() {
        val wide = setOf(2, 5)
        val none = ComicLayout.pieces(7, { it in wide }, split = false, rtl = false, invert = false)
        assertEquals(7, none.size)
        val ltr = ComicLayout.pieces(7, { it in wide }, split = true, rtl = false, invert = false)
        assertEquals(9, ltr.size)
        assertEquals(listOf(ComicLayout.Piece(2, ComicLayout.Part.LEFT), ComicLayout.Piece(2, ComicLayout.Part.RIGHT)), ltr.subList(2, 4))
        // Right to left, the right half comes first; swapped, the other way round.
        val rtl = ComicLayout.pieces(7, { it in wide }, split = true, rtl = true, invert = false)
        assertEquals(ComicLayout.Part.RIGHT, rtl[2].part)
        val swapped = ComicLayout.pieces(7, { it in wide }, split = true, rtl = true, invert = true)
        assertEquals(ComicLayout.Part.LEFT, swapped[2].part)
        assertEquals(Rect(0, 0, 500, 700), ComicLayout.regionOf(ComicLayout.Part.LEFT, Rect(0, 0, 1000, 700)))
        assertEquals(Rect(500, 0, 1000, 700), ComicLayout.regionOf(ComicLayout.Part.RIGHT, Rect(0, 0, 1000, 700)))
    }

    @Test
    fun eachWayOfFittingAPage() {
        // A 1000 × 1500 page on a 2000 × 1000 screen held sideways.
        fun fit(scale: ComicScale, zoomWide: Boolean = true) = ComicLayout.fit(1000f, 1500f, 2000f, 1000f, scale, zoomWide)
        assertEquals(666.67f to 1000f, fit(ComicScale.FIT_SCREEN).round())
        assertEquals(2000f to 1000f, fit(ComicScale.STRETCH))
        assertEquals(2000f to 3000f, fit(ComicScale.FIT_WIDTH))
        assertEquals(666.67f to 1000f, fit(ComicScale.FIT_HEIGHT).round())
        assertEquals(1000f to 1500f, fit(ComicScale.ORIGINAL))
        // Smart: the width for a page taller than wide.
        assertEquals(2000f to 3000f, fit(ComicScale.SMART))
        // A spread (2000 × 1500) on an upright 1000 × 2000 screen fills its height when zooming into wide pages.
        assertEquals(2666.67f to 2000f, ComicLayout.fit(2000f, 1500f, 1000f, 2000f, ComicScale.FIT_SCREEN, true).round())
        assertEquals(1000f to 750f, ComicLayout.fit(2000f, 1500f, 1000f, 2000f, ComicScale.FIT_SCREEN, false))
    }

    private fun Pair<Float, Float>.round() = (Math.round(first * 100) / 100f) to (Math.round(second * 100) / 100f)

    @Test
    fun aPageBiggerThanTheScreenStartsWhereItShould() {
        // 3000 wide on a 1000 wide screen: automatic is the left reading left to right, the right reading right to left.
        assertEquals(0f to 0f, ComicLayout.startOffset(3000f, 800f, 1000f, 1000f, ComicZoomStart.AUTO, rtl = false).let { it.first to 0f })
        assertEquals(-2000f, ComicLayout.startOffset(3000f, 800f, 1000f, 1000f, ComicZoomStart.AUTO, rtl = true).first)
        assertEquals(-1000f, ComicLayout.startOffset(3000f, 800f, 1000f, 1000f, ComicZoomStart.CENTER, rtl = false).first)
        assertEquals(-2000f, ComicLayout.startOffset(3000f, 800f, 1000f, 1000f, ComicZoomStart.RIGHT, rtl = false).first)
        // Smaller than the screen: in the middle; taller: at its top.
        assertEquals(100f to 100f, ComicLayout.startOffset(800f, 800f, 1000f, 1000f, ComicZoomStart.LEFT, rtl = false))
        assertEquals(0f, ComicLayout.startOffset(800f, 3000f, 1000f, 1000f, ComicZoomStart.LEFT, rtl = false).second)
    }

    @Test
    fun tapsTurnPagesAsMihonsTapZonesDo() {
        fun tap(x: Float, y: Float, taps: ComicTaps, mode: ComicMode = ComicMode.LEFT_TO_RIGHT, invert: ComicInvert = ComicInvert.NONE) =
            ComicLayout.tapAction(x, y, taps, mode, invert)
        // Right and left: the left side goes back, the right side on; reading right to left, the other way round.
        assertEquals(-1, tap(0.1f, 0.5f, ComicTaps.RIGHT_LEFT))
        assertEquals(1, tap(0.9f, 0.5f, ComicTaps.RIGHT_LEFT))
        assertEquals(0, tap(0.5f, 0.5f, ComicTaps.RIGHT_LEFT))
        assertEquals(1, tap(0.1f, 0.5f, ComicTaps.RIGHT_LEFT, ComicMode.RIGHT_TO_LEFT))
        assertEquals(-1, tap(0.9f, 0.5f, ComicTaps.RIGHT_LEFT, ComicMode.RIGHT_TO_LEFT))
        // L shaped: the top and the left go back, the bottom and the right go on.
        assertEquals(-1, tap(0.5f, 0.1f, ComicTaps.L_SHAPED))
        assertEquals(-1, tap(0.1f, 0.5f, ComicTaps.L_SHAPED))
        assertEquals(1, tap(0.5f, 0.9f, ComicTaps.L_SHAPED))
        assertEquals(1, tap(0.9f, 0.5f, ComicTaps.L_SHAPED))
        // Kindle-ish: the top is the menu, the left goes back, the rest on.
        assertEquals(0, tap(0.5f, 0.1f, ComicTaps.KINDLE))
        assertEquals(-1, tap(0.1f, 0.6f, ComicTaps.KINDLE))
        assertEquals(1, tap(0.6f, 0.6f, ComicTaps.KINDLE))
        // Edges: both sides go on, the bottom middle back.
        assertEquals(1, tap(0.1f, 0.5f, ComicTaps.EDGE))
        assertEquals(1, tap(0.9f, 0.5f, ComicTaps.EDGE))
        assertEquals(-1, tap(0.5f, 0.9f, ComicTaps.EDGE))
        // The usual: right and left for pages side to side, L shaped for top to bottom and the strip.
        assertEquals(-1, tap(0.1f, 0.5f, ComicTaps.DEFAULT))
        assertEquals(-1, tap(0.5f, 0.1f, ComicTaps.DEFAULT, ComicMode.STRIP))
        // Swapped, and off.
        assertEquals(1, tap(0.1f, 0.5f, ComicTaps.RIGHT_LEFT, invert = ComicInvert.HORIZONTAL))
        assertEquals(1, tap(0.5f, 0.1f, ComicTaps.L_SHAPED, invert = ComicInvert.VERTICAL))
        assertEquals(0, tap(0.1f, 0.5f, ComicTaps.OFF))
    }

    @Test
    fun plainBordersAreCroppedOff() {
        val bmp = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            drawRect(12f, 20f, 90f, 140f, Paint().apply { color = Color.rgb(30, 90, 200) })
        }
        assertEquals(Rect(12, 20, 90, 140), ComicLayout.borders(bmp) { x, y -> bmp.getPixel(x, y) })
        // A picture that runs to its edges keeps all of it.
        val full = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 50, 50)) }
        assertEquals(Rect(0, 0, 100, 150), ComicLayout.borders(full) { x, y -> full.getPixel(x, y) })
    }

    @Test
    fun settingsAreKeptAndReadBack() {
        val s = ComicSettings(
            mode = ComicMode.STRIP_GAPS, rotation = ComicRotation.LOCKED_LANDSCAPE, scale = ComicScale.SMART, splitWide = true,
            tapsPaged = ComicTaps.KINDLE, tapsStrip = ComicTaps.EDGE, invertTapsStrip = ComicInvert.BOTH, sidePadding = 13,
            background = ComicBackground.AUTO, alwaysTransition = false, flash = true, flashMillis = 300, flashEvery = 3,
            flashWith = ComicFlash.WHITE_BLACK, verticalIn = setOf(ComicMode.STRIP, ComicMode.VERTICAL), verticalLeft = true,
            verticalHeight = 80, customBrightness = true, brightness = -40, colourFilter = true, colour = 0x66FF8800,
            blend = ComicBlend.OVERLAY, greyscale = true, inverted = true, fullscreen = false, cutout = false,
            rotateWideStrip = true, invertRotate = true, splitWideStrip = true, invertSplitStrip = true,
        )
        assertEquals(s, ComicSettings.fromJson(s.toJson()))
        // Something unknown (a newer KoLib's) falls back to the usual.
        val o = s.toJson().put("mode", "SIDEWAYS").put("brightness", 400)
        assertEquals(ComicMode.LEFT_TO_RIGHT, ComicSettings.fromJson(o).mode)
        assertEquals(100, ComicSettings.fromJson(o).brightness)
        // Saved before taps were set for each way of reading: the one setting does for both.
        val old = JSONObject().put("taps", "KINDLE").put("invertTaps", "VERTICAL")
        assertEquals(ComicTaps.KINDLE, ComicSettings.fromJson(old).tapsPaged)
        assertEquals(ComicTaps.KINDLE, ComicSettings.fromJson(old).tapsStrip)
        assertEquals(ComicInvert.VERTICAL, ComicSettings.fromJson(old).invertTapsStrip)
        // And before comics had a rotation of their own: the book reader's.
        assertEquals(ComicRotation.PORTRAIT, ComicSettings.fromJson(old, firstRotation = ComicRotation.PORTRAIT).rotation)
        assertEquals(ComicRotation.LOCKED_LANDSCAPE, ComicSettings.fromJson(s.toJson(), firstRotation = ComicRotation.PORTRAIT).rotation)
    }

    @Test
    fun aSeriesKeepsItsOwnModeAndRotation() {
        val own = ComicOwnStore.inMemory()
        assertEquals(ComicOwn(), own.of("s:saga"))
        own.set("s:saga", ComicOwn(mode = ComicMode.RIGHT_TO_LEFT))
        own.set("s:saga", own.of("s:saga").copy(rotation = ComicRotation.LANDSCAPE))
        assertEquals(ComicOwn(ComicMode.RIGHT_TO_LEFT, ComicRotation.LANDSCAPE), own.of("s:saga"))
        // Back to the defaults: nothing kept.
        own.set("s:saga", ComicOwn())
        assertTrue(own.all.value.isEmpty())
        // A comic's mode from before modes were kept for each series is still read.
        java.io.File(dir, "comic.json").writeText("""{"mode":"RIGHT_TO_LEFT"}""")
        assertEquals(ComicMode.RIGHT_TO_LEFT, ComicSettingsStore.modeOf(dir))
    }
}
