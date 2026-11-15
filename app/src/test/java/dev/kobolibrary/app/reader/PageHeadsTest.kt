package dev.kobolibrary.app.reader

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.BookStructure
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubFileFiles
import dev.kobolibrary.app.epub.TestBooks
import dev.kobolibrary.app.epub.TestEpubs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.util.UUID

/**
 * The page headings, as a printed book has them: each page numbered on its own (two side by side are two
 * pages), the book's title without what's only there to sort it, and room kept for them over the text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageHeadsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aNumberOnlyThereToSortTheFilesIsLeftOffTheTitle() {
        val cases = mapOf(
            "01 The Eye of the World" to "The Eye of the World",
            "00 Prologue" to "Prologue",
            "06 - Lord of Chaos" to "Lord of Chaos",
            "6 - Lord of Chaos" to "Lord of Chaos",
            "6. Lord of Chaos" to "Lord of Chaos",
            "[06] Lord of Chaos" to "Lord of Chaos",
            "(6) Lord of Chaos" to "Lord of Chaos",
            "03_The Dragon Reborn" to "The Dragon Reborn",
            "01-The Hobbit" to "The Hobbit",
            "01: The Fellowship" to "The Fellowship",
            "Lord of Chaos (The Wheel of Time, Book 6)" to "Lord of Chaos",
            "06 Lord of Chaos (Wheel of Time #6)" to "Lord of Chaos",
            // Numbers that are the title, or part of it, stay.
            "1984" to "1984",
            "2001: A Space Odyssey" to "2001: A Space Odyssey",
            "12 Rules for Life" to "12 Rules for Life",
            "20,000 Leagues Under the Sea" to "20,000 Leagues Under the Sea",
            "11/22/63" to "11/22/63",
            "1Q84" to "1Q84",
            "1-800-Where-R-You" to "1-800-Where-R-You",
            "1 Corinthians" to "1 Corinthians",
            "Selected Poems (1950)" to "Selected Poems (1950)",
            // A range of numbers in front, for the books a collection holds.
            "0-14 The Wheel of Time" to "The Wheel of Time",
            "[1-3] The Hunger Games" to "The Hunger Games",
            "01 - 03 The Expanse" to "The Expanse",
            "The Hunger Games Trilogy (Books 1-3)" to "The Hunger Games Trilogy",
            "1914-1918 The Great War" to "1914-1918 The Great War",
            "9-11: Was There an Alternative?" to "9-11: Was There an Alternative?",
            "The Hobbit" to "The Hobbit",
        )
        for ((title, shown) in cases) assertEquals(title, shown, HeadTitles.book(title))
        // A number the same as the book's in its series is there to sort it.
        assertEquals("Lord of Chaos", HeadTitles.book("6 Lord of Chaos", seriesIndex = 6.0))
        assertEquals("12 Rules for Life", HeadTitles.book("12 Rules for Life", seriesIndex = 3.0))

        // The author's name in front or after, as file names have it, goes, along with a number in front of it.
        val fire = "Fire in the Minds of Men Origins of the Revolutionary Faith"
        val billington = listOf("James H. Billington")
        assertEquals(fire, HeadTitles.book("17 James H Billington - $fire", authors = billington))
        assertEquals(fire, HeadTitles.book("17 James H Billington - $fire"))
        assertEquals(fire, HeadTitles.book("17 James H Billington - $fire", authors = listOf("Unknown")))
        assertEquals(fire, HeadTitles.book("[17] Billington, James H. - $fire", authors = billington))
        assertEquals(fire, HeadTitles.book("17. J. H. Billington – $fire", authors = billington))
        assertEquals(fire, HeadTitles.book("James H Billington - $fire", authors = billington))
        assertEquals(fire, HeadTitles.book("$fire - James H. Billington", authors = billington))
        assertEquals("The Republic", HeadTitles.book("Plato - The Republic", authors = listOf("Plato")))
        assertEquals("Nausea", HeadTitles.book("03 Jean-Paul Sartre - Nausea"))
        assertEquals("The Hobbit", HeadTitles.book("The Hobbit - J.R.R. Tolkien", authors = listOf("J. R. R. Tolkien")))
        assertEquals("1984", HeadTitles.book("1984 - George Orwell", authors = listOf("George Orwell")))
        assertEquals("12 Rules for Life", HeadTitles.book("12 Jordan B Peterson - 12 Rules for Life", authors = listOf("Jordan B. Peterson")))
        assertEquals("Lord of Chaos", HeadTitles.book("6 - Lord of Chaos - Robert Jordan", authors = listOf("Robert Jordan")))
        // Not without a number, unless it's the book's author; nor what isn't a name, nor someone else's.
        assertEquals("James H Billington - $fire", HeadTitles.book("James H Billington - $fire"))
        assertEquals("Star Wars - Heir to the Empire", HeadTitles.book("Star Wars - Heir to the Empire", authors = listOf("Timothy Zahn")))
        assertEquals("12 Rules for Life - An Antidote to Chaos", HeadTitles.book("12 Rules for Life - An Antidote to Chaos", authors = listOf("Jordan B. Peterson")))
        assertEquals("17 James H Billington - $fire", HeadTitles.book("17 James H Billington - $fire", authors = listOf("Richard Pipes")))
        assertEquals("Lincoln - A Life", HeadTitles.book("Lincoln - A Life", authors = listOf("Abraham Lincoln")))
        assertEquals("Churchill - Walking with Destiny", HeadTitles.book("Churchill - Walking with Destiny", authors = listOf("Andrew Roberts")))
        assertEquals("1 Corinthians", HeadTitles.book("1 Corinthians", authors = listOf("Paul")))
        // A chapter's number in front of its title is left off the heading; one after it, or only a number, stays.
        val chapters = mapOf(
            "1 An Empty Road" to "An Empty Road",
            "12. The Battle" to "The Battle",
            "01 - The Dragon Reborn" to "The Dragon Reborn",
            "XII The Battle" to "The Battle",
            "XII: The Battle" to "The Battle",
            "IV. Escape" to "Escape",
            "I. The Beginning" to "The Beginning",
            "Chapter 3: The Road" to "The Road",
            "Chapter 3 - The Road" to "The Road",
            "CHAPTER 1. Loomings." to "Loomings.",
            "Chapter XII — The Battle" to "The Battle",
            "Chapter Twelve: The Battle" to "The Battle",
            "Chapter Twenty-One The Last" to "The Last",
            "12" to "Chapter 12",
            "XII" to "Chapter XII",
            "Chapter 12" to "Chapter 12",
            "Chapter One" to "Chapter One",
            "Interlude II" to "Interlude II",
            "The Road, Part 2" to "The Road, Part 2",
            // The part of the book it is goes too, as print has it over its pages; alone, it stays.
            "Prologue: The First Message" to "The First Message",
            "Epilogue — The Last Battle" to "The Last Battle",
            "Part Two: Winter" to "Winter",
            "Prologue" to "Prologue",
            "Book One" to "Book One",
            "Introduction to Greek" to "Introduction to Greek",
            "Book of Days" to "Book of Days",
            "I Am Legend" to "I Am Legend",
            "I, Robot" to "I, Robot",
            "One Night in Paris" to "One Night in Paris",
            "Mix Tape" to "Mix Tape",
            "MIX Tape" to "MIX Tape",
            "Civil War" to "Civil War",
            "1984" to "1984",
            "1945: The End" to "1945: The End",
            "1.5 Between" to "1.5 Between",
            "12th Night" to "12th Night",
        )
        for ((title, shown) in chapters) assertEquals(title, shown, HeadTitles.chapter(title))
        // All in capitals: all small capitals, so it isn't shouted.
        assertTrue(HeadTitles.smallCapsFeatures("LORD OF CHAOS").contains("c2sc"))
        assertFalse(HeadTitles.smallCapsFeatures("Lord of Chaos").contains("c2sc"))
        // As print sets a heading: capitals and small letters, so its first letters stand taller.
        assertEquals("Lord of Chaos", HeadTitles.headingCase("LORD OF CHAOS"))
        assertEquals("The Eye of the World", HeadTitles.headingCase("THE EYE OF THE WORLD"))
        assertEquals("Book II: Down the Rabbit-Hole", HeadTitles.headingCase("BOOK II: DOWN THE RABBIT-HOLE"))
        assertEquals("“The Shadow” Rising", HeadTitles.headingCase("“THE SHADOW” RISING"))
        assertEquals("Lord of Chaos", HeadTitles.headingCase("Lord of Chaos"))
        assertEquals("A", HeadTitles.headingCase("A"))
    }

    @Test
    fun theHeadingsTakeTheirEndOfThePageAndWhatTheyShow() {
        val before = ReaderSettings(runningHeads = RunningHeads.OFF, marginItems = MarginItem.DEFAULTS + mapOf(
            MarginItem.CHAPTER to MarginSpot.TOP_CENTER, MarginItem.PAGE to MarginSpot.BOTTOM_LEFT, MarginItem.CLOCK to MarginSpot.TOP_RIGHT,
        ))
        val on = before.withHeads(RunningHeads.TOP)
        assertEquals(RunningHeads.TOP, on.runningHeads)
        // What the headings show isn't shown twice, and nothing is left at the top for them to sit on.
        MarginItem.IN_HEADINGS.forEach { assertEquals(it.name, MarginSpot.OFF, on.spotOf(it)) }
        assertEquals(MarginSpot.BOTTOM_RIGHT, on.spotOf(MarginItem.CLOCK))
        assertTrue(MarginItem.entries.none { on.shownSpotOf(it).top })
        // At the bottom instead: the time and the rest go to the top.
        val below = on.withHeads(RunningHeads.BOTTOM)
        assertEquals(MarginSpot.TOP_RIGHT, below.spotOf(MarginItem.CLOCK))
        assertEquals(MarginSpot.TOP_CENTER, below.spotOf(MarginItem.BATTERY))
        // Off: the chapter's title and the page number come back where they were.
        val off = on.withHeads(RunningHeads.OFF)
        assertEquals(MarginSpot.TOP_CENTER, off.spotOf(MarginItem.CHAPTER))
        assertEquals(MarginSpot.BOTTOM_LEFT, off.spotOf(MarginItem.PAGE))
        // To start with, they're on, at the top, with nothing else at the top.
        val fresh = ReaderSettings()
        assertEquals(RunningHeads.TOP, fresh.runningHeads)
        assertTrue(MarginItem.entries.none { fresh.spotOf(it).top })
    }

    @Test
    fun roomIsKeptOverTheTextAndBelowACameraCutout() {
        // Headings as they were, small and close to the text.
        val s = ReaderSettings(headSize = HeadSize.SMALL, headGap = HeadGap.CLOSE)
        // Headings at the top; the time and the rest at the bottom, over the progress bar.
        assertEquals(MarginRoom(top = 12 + 18 + 10, bottom = 10 + 18 + 10), s.marginRoom())
        // As in print (to start with): about the text's size (23dp at 145%), with a line's space under them; at
        // the foot, room for a chapter's first page number, as big.
        val print = ReaderSettings()
        assertEquals(HeadSize.PRINT, print.headSize)
        assertEquals(HeadGap.LINE, print.headGap)
        assertEquals(21.3f, print.headFontSize, 0.1f)
        assertEquals(MarginRoom(top = 12 + 29 + 34, bottom = 10 + 29 + 10), print.marginRoom())
        val big = print.copy(headSize = HeadSize.LARGE, headGap = HeadGap.MORE)
        assertTrue(big.marginRoom().top > print.marginRoom().top + 15)
        // A line's space follows the line spacing chosen.
        assertEquals(Math.round(16 * 1.45f * 2f), print.copy(lineSpacing = 2f).headGapDp)
        // Under a camera's cutout 32dp deep, the top row goes below it.
        assertEquals(34 + 18 + 10, s.marginRoom(cutoutTop = 32).top)
        val js = JSONObject(s.toJs("en", cutoutTop = 32))
        assertEquals(62, js.getInt("minTop"))
        assertEquals(38, js.getInt("minBottom"))
        // Nothing at the bottom, no room kept there; scrolling keeps its own bands instead.
        val bare = s.copy(marginItems = MarginItem.entries.associateWith { MarginSpot.OFF }, progressLine = ProgressLine.OFF)
        assertEquals(0, bare.marginRoom().bottom)
        assertEquals(MarginRoom(), s.copy(flow = ReaderFlow.SCROLL).marginRoom(cutoutTop = 32))
    }

    @Test
    fun settingsFromBeforeTheHeadingsAreBroughtToThem() {
        val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("marginItems", "BOOK=OFF,CHAPTER=TOP_CENTER,PAGE=BOTTOM_LEFT,BOOK_PAGE=OFF,PAGES_LEFT=TOP_LEFT,TIME_LEFT=OFF,CLOCK=BOTTOM_CENTER,BATTERY=BOTTOM_CENTER,PERCENT=BOTTOM_RIGHT")
            .putString("theme", "NIGHT")
            .commit()
        val s = ReaderSettingsStore(context).settings.value
        assertEquals(RunningHeads.TOP, s.runningHeads)
        assertEquals(ReaderTheme.NIGHT, s.theme)
        assertEquals(MarginSpot.OFF, s.spotOf(MarginItem.CHAPTER))
        assertEquals(MarginSpot.OFF, s.spotOf(MarginItem.PAGE))
        assertEquals(MarginSpot.BOTTOM_LEFT, s.spotOf(MarginItem.PAGES_LEFT))
        assertEquals(MarginSpot.BOTTOM_CENTER, s.spotOf(MarginItem.CLOCK))
        assertEquals("TOP", prefs.getString("heads", null))
        // Chosen since: kept as chosen.
        ReaderSettingsStore(context).update { it.withHeads(RunningHeads.OFF) }
        assertEquals(RunningHeads.OFF, ReaderSettingsStore(context).settings.value.runningHeads)
        prefs.edit().clear().commit()
    }

    // ---- numbering each page, two side by side ----------------------------------------------------

    private class FakeCounter(private val pages: (String) -> Int, var layout: String) : ChapterCounter {
        override suspend fun count(path: String, start: String): ChapterCounter.Counted = ChapterCounter.Counted(1, layout, pages(path))
        override fun close() {}
    }

    private val layout = "1280|800|2|0|38|40/40|0|0|1|null|null|book|book|en"
    private val counts = mapOf("ch1.xhtml" to 7, "ch2.xhtml" to 12, "ch3.xhtml" to 9, "notes.xhtml" to 2)
    private fun pagesOf(path: String) = counts[path.substringAfterLast('/')] ?: 3

    private fun spread(page: Int, screens: Int, columns: Int) = JSONObject()
        .put("page", page).put("pages", screens).put("columns", columns).put("spread", true)
        .put("progress", if (screens > 1) page.toDouble() / (screens - 1) else 0.0)
        .put("anchor", "1/$page:0").put("snippet", "On screen $page").put("layout", layout)
        .put("frame", JSONObject().put("w", 1280).put("h", 800).put("perScreen", 2).put("padX", 38).put("col", 564).put("padTop", 40).put("padBottom", 40))
        .put("picture", false)
        .toString()

    private fun one(page: Int, pages: Int) = JSONObject()
        .put("page", page).put("pages", pages).put("columns", pages).put("spread", false)
        .put("progress", if (pages > 1) page.toDouble() / (pages - 1) else 0.0)
        .put("anchor", "1/$page:0").put("snippet", "On page $page").put("layout", layout.replace("|2|", "|1|"))
        .toString()

    private fun settle(done: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(600) {
            if (done()) return
            looper.idleFor(Duration.ofMillis(100))
            Thread.sleep(2)
        }
        fail("The reader never got there")
    }

    private fun ReaderController.index(name: String) = chapters.indexOfFirst { it.path.endsWith(name) }

    @Test
    fun twoPagesSideBySideAreNumberedEachOnTheirOwn() {
        val book = TestBooks.garden(File(context.cacheDir, "heads-${UUID.randomUUID()}.epub"))
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "01 The Garden", "en", null, ReaderSettings(), context.assets)
            val fake = FakeCounter(::pagesOf, layout)
            c.counter = fake
            settle { c.lengthsCounted }
            val ch1 = c.index("ch1.xhtml")
            c.open(ch1, JSONObject().put("type", "start"))
            // Chapter 1 is 7 pages: 4 screens, the last with text on its left-hand page only.
            c.readied(spread(page = 1, screens = 4, columns = 7))
            settle { c.bookPages != null }
            val before = c.bookPages!!.before(ch1)
            assertEquals(7, c.bookPages!!.counts[ch1])
            assertEquals(before + 3, c.bookPage)
            assertEquals(before + 4, c.bookPageLast)
            assertEquals("Pages ${before + 3}–${before + 4}", c.pagesLabel(c.bookPage!!, c.bookPageLast))
            c.shownPages.let { shown ->
                assertEquals(2, shown.size)
                assertEquals(ShownPage(0, before + 3, PageSide.LEFT, opening = false), shown[0])
                assertEquals(ShownPage(1, before + 4, PageSide.RIGHT, opening = false), shown[1])
            }
            // The last screen: one page, on the left.
            c.readied(spread(page = 3, screens = 4, columns = 7))
            assertEquals(before + 7, c.bookPage)
            assertEquals(before + 7, c.bookPageLast)
            assertEquals(listOf(ShownPage(0, before + 7, PageSide.LEFT, opening = false)), c.shownPages)
            // The first: the chapter's opening page, then the next.
            c.readied(spread(page = 0, screens = 4, columns = 7))
            assertEquals(listOf(true, false), c.shownPages.map { it.opening })
            // The headings' titles: the book's without its sorting number, and the chapter's from the contents,
            // without its number in front.
            assertEquals("The Garden", c.headBookTitle)
            assertEquals("Chapter 1: Rose", c.chapterTitle)
            assertEquals("Rose", c.headChapterTitle)
            assertTrue(c.headChapterTitle.isNotBlank())

            // The progress bar names a page, and goes to the screen it's on.
            for (f in listOf(0.3f, 0.62f)) {
                val named = c.bookPageAt(f)!!
                c.goToBookProgress(f)
                val target = JSONObject(c.startState).getJSONObject("target")
                if (target.getString("type") != "page") continue
                val landed = c.chapterIndex
                val total = pagesOf(c.chapters[landed].path)
                val screens = (total + 1) / 2
                c.readied(spread(target.getInt("value"), screens, total))
                assertTrue("bar at $f named $named, shows ${c.bookPage}–${c.bookPageLast}", named == c.bookPage || named == c.bookPageLast)
            }

            // One page at a time, it's a left-hand page when its number is even, as in print.
            fake.layout = layout.replace("|2|", "|1|")
            c.open(ch1, JSONObject().put("type", "start"))
            c.readied(one(page = 2, pages = 7))
            assertNull("counted again at the new layout", c.bookPages)
            settle { c.bookPages != null }
            val n = c.bookPage!!
            assertEquals(n, c.bookPageLast)
            assertEquals(listOf(ShownPage(0, n, if (n % 2 == 0) PageSide.LEFT else PageSide.RIGHT, opening = false)), c.shownPages)
            c.release()
        }
    }

    @Test
    fun aPageOnItsOwnIsTheRightHandOne() {
        val book = TestBooks.garden(File(context.cacheDir, "lone-${UUID.randomUUID()}.epub"))
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "The Garden", "en", null, ReaderSettings(), context.assets)
            c.counter = FakeCounter(::pagesOf, layout)
            settle { c.lengthsCounted }
            val notes = c.index("notes.xhtml")
            c.open(notes, JSONObject().put("type", "start"))
            // One page of text, put on the right with a blank page before it: its number, and no title over it.
            c.readied(JSONObject(spread(page = 0, screens = 1, columns = 1)).put("blank", 1).put("display", false).toString())
            settle { c.bookPages != null }
            val n = c.bookPages!!.before(notes) + 1
            assertEquals(listOf(ShownPage(1, n, PageSide.RIGHT, opening = true)), c.shownPages)
            assertEquals(n, c.bookPage)
            assertEquals(n, c.bookPageLast)
            // A dedication, a quote, a map: not even its number.
            c.reported(JSONObject(spread(page = 0, screens = 1, columns = 1)).put("blank", 1).put("display", true).toString())
            assertTrue(c.shownPages.isEmpty())
            assertEquals(n, c.bookPage)
            // One page at a time, there's no blank page.
            c.readied(JSONObject(one(page = 0, pages = 1)).put("blank", 0).toString())
            assertEquals(0, c.blankBefore)
            c.release()
        }
    }

    @Test
    fun shortPagesGoSideBySideAndCoversInTheMiddle() {
        val pages = linkedMapOf(
            "cover" to """<div><img src="../images/cover.jpg" alt="Cover"/></div>""",
            "title" to """<h1>THE WHEEL OF TIME</h1><p>ROBERT JORDAN</p><p>AND BRANDON SANDERSON</p>""",
            "notice" to """<p>The author and publisher have provided this e-book to you without DRM.</p><p>Copyright infringement is against the law.</p>""",
            "dedication" to """<p>For Harriet</p>""",
            "epigraph" to """<p>The Wheel weaves as the Wheel wills.</p><p>—from The Karaethon Cycle</p>""",
            "ch1" to "<h1>1 An Empty Road</h1>" + (1..60).joinToString("") { "<p>Paragraph $it of the first chapter, with enough words in it to read.</p>" },
            "alsoby" to """<p>Also by Robert Jordan: The Eye of the World, The Great Hunt.</p>""",
            "ch2" to "<h1>2 Strangers</h1>" + (1..60).joinToString("") { "<p>Paragraph $it of the second chapter, with enough words in it to read.</p>" },
        )
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:8</dc:identifier><dc:title>The Eye of the World</dc:title><dc:language>en</dc:language></metadata>
  <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="img" href="images/cover.jpg" media-type="image/jpeg"/>
${pages.keys.joinToString("\n") { """<item id="$it" href="text/$it.xhtml" media-type="application/xhtml+xml"/>""" }}</manifest>
  <spine>${pages.keys.joinToString("") { """<itemref idref="$it"/>""" }}</spine>
</package>"""
        val nav = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc"><ol><li><a href="text/dedication.xhtml">Dedication</a></li><li><a href="text/ch1.xhtml">1 An Empty Road</a></li><li><a href="text/ch2.xhtml">2 Strangers</a></li></ol></nav></body></html>"""
        val entries = linkedMapOf<String, ByteArray>(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
            "OEBPS/images/cover.jpg" to ByteArray(32),
        )
        for ((f, body) in pages) entries["OEBPS/text/$f.xhtml"] = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$f</title></head><body>$body</body></html>""".toByteArray()
        val book = TestEpubs.build(File(context.cacheDir, "short-${UUID.randomUUID()}.epub"), entries)
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val c = ReaderController(files, BookStructure(files), "The Eye of the World", "en", null, ReaderSettings(), context.assets)
            settle { c.lengthsCounted }
            val at = { name: String -> c.index("$name.xhtml") }
            assertEquals(
                listOf(PageKind.COVER, PageKind.SHORT, PageKind.SHORT, PageKind.DEDICATION, PageKind.QUOTE, PageKind.TEXT, PageKind.SHORT, PageKind.TEXT),
                pages.keys.map { c.pageKind(at(it)) },
            )
            // The title page and the notice side by side; the dedication and the quote. The cover alone, and a short page between chapters too.
            assertEquals(listOf("title", "dedication"), pages.keys.filter { c.isPairedLeft(at(it)) })
            val served = c.servedText(c.chapters[at("notice")].path)!!
            assertTrue(served.contains("id=\"kl-pair\"") && served.contains("THE WHEEL OF TIME") && served.contains("without DRM"))
            // Opening the left one opens the two; turning back from the right one goes past both.
            c.open(at("dedication"), JSONObject().put("type", "start"))
            assertEquals(at("epigraph"), c.chapterIndex)
            c.open(at("cover"), JSONObject().put("type", "start"))
            assertEquals("cover", JSONObject(c.startState).optString("place"))
            c.nextChapter()
            assertEquals(at("notice"), c.chapterIndex)
            assertFalse(JSONObject(c.startState).has("place"))
            c.nextChapter()
            assertEquals(at("epigraph"), c.chapterIndex)
            c.previousChapter()
            assertEquals(at("notice"), c.chapterIndex)
            // The contents' "Dedication" is the page on screen.
            c.open(at("epigraph"), JSONObject().put("type", "start"))
            assertTrue(c.contentsPaths.any { it.endsWith("dedication.xhtml") })
            c.release()
        }
    }

    @Test
    fun platesOneAfterAnotherGoSideBySideAndATurnedOneGoesAcross() {
        // A PNG's start: its signature and the header with its width and height (all that's read to tell its shape).
        fun png(w: Int, h: Int) = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13,
            'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0, 0, (w shr 8).toByte(), (w and 0xff).toByte(), 0, 0, (h shr 8).toByte(), (h and 0xff).toByte(), 8, 6, 0, 0, 0)
        val plates = listOf("cover", "p1", "p2", "wide", "p3", "side", "p4")
        val pages = linkedMapOf<String, String>()
        for (p in plates) pages[p] = """<div><img src="../images/$p.png" alt=""/></div>"""
        pages["ch1"] = "<h1>1 An Empty Road</h1>" + (1..60).joinToString("") { "<p>Paragraph $it of the first chapter, with enough words in it to read.</p>" }
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:10</dc:identifier><dc:title>Plates</dc:title><dc:language>en</dc:language></metadata>
  <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
${plates.joinToString("\n") { """<item id="i-$it" href="images/$it.png" media-type="image/png"/>""" }}
${pages.keys.joinToString("\n") { """<item id="$it" href="text/$it.xhtml" media-type="application/xhtml+xml"/>""" }}</manifest>
  <spine>${pages.keys.joinToString("") { """<itemref idref="$it"/>""" }}</spine>
</package>"""
        val nav = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc"><ol><li><a href="text/ch1.xhtml">1 An Empty Road</a></li></ol></nav></body></html>"""
        val entries = linkedMapOf<String, ByteArray>(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to nav.toByteArray(),
        )
        for (p in plates) entries["OEBPS/images/$p.png"] = if (p == "wide") png(1500, 1000) else png(1000, 1500)
        for ((f, body) in pages) entries["OEBPS/text/$f.xhtml"] = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$f</title></head><body>$body</body></html>""".toByteArray()
        val book = TestEpubs.build(File(context.cacheDir, "plates-${UUID.randomUUID()}.epub"), entries)
        EpubFile.open(book).use { epub ->
            val files = EpubFileFiles(epub)
            val dir = File(context.cacheDir, "plates-store-${UUID.randomUUID()}")
            val c = ReaderController(files, BookStructure(files), "Plates", "en", ReaderStore(dir), ReaderSettings(), context.assets)
            settle { c.lengthsCounted }
            val at = { name: String -> c.index("$name.xhtml") }
            assertEquals(
                listOf(PageKind.COVER, PageKind.PICTURE, PageKind.PICTURE, PageKind.WIDE_PICTURE, PageKind.PICTURE, PageKind.PICTURE, PageKind.PICTURE, PageKind.TEXT),
                pages.keys.map { c.pageKind(at(it)) },
            )
            // Plates one after another, two to a screen; the cover and the wide one alone.
            assertEquals(listOf("p1", "p3"), pages.keys.filter { c.isPairedLeft(at(it)) })
            val served = c.servedText(c.chapters[at("p2")].path)!!
            assertTrue(served.contains("id=\"kl-pair\"") && served.contains("p1.png") && served.contains("p2.png"))
            // The plate printed on its side, turned the right way up: it's wide now, across both pages, and p3 and p4 can't
            // be side by side any more (the turned one is between them); it's remembered with the book.
            c.open(at("side"), JSONObject().put("type", "start"))
            c.turnPicture("OEBPS/images/side.png", 1)
            assertEquals(PageKind.WIDE_PICTURE, c.pageKind(at("side")))
            assertEquals(listOf("p1"), pages.keys.filter { c.isPairedLeft(at(it)) })
            assertEquals(1, JSONObject(c.startState).getJSONObject("turns").optInt("OEBPS/images/side.png"))
            settle { File(dir, "pictures.json").exists() }
            assertEquals(mapOf("OEBPS/images/side.png" to 1), ReaderStore(dir).turns())
            // Turned back: as it was.
            c.turnPicture("OEBPS/images/side.png", -1)
            assertEquals(PageKind.PICTURE, c.pageKind(at("side")))
            assertEquals(listOf("p1", "p3"), pages.keys.filter { c.isPairedLeft(at(it)) })
            c.release()
        }
    }

    @Test
    fun aBookOfSeveralBooksHeadsEachWithItsOwnTitle() {
        fun page(title: String) = TestBooks.simplePage(title, "Words.").toByteArray()
        val files = linkedMapOf(
            "cover" to "Cover", "eye" to "The Eye of the World", "eye-pro" to "Prologue: Dragonmount", "eye-1" to "1 An Empty Road",
            "eye-2" to "2 Strangers", "hunt" to "The Great Hunt", "hunt-pro" to "Prologue: In the Shadow", "hunt-1" to "1 The Flame",
            "hunt-2" to "2 The Welcome", "about" to "About the Author",
        )
        val nav = files.entries.joinToString("\n") { (f, t) -> """<li><a href="text/$f.xhtml">$t</a></li>""" }
            .replace("""<li><a href="text/eye.xhtml">The Eye of the World</a></li>""", """<li><a href="text/eye.xhtml">The Eye of the World</a><ol>""")
            .replace("""<li><a href="text/hunt.xhtml">The Great Hunt</a></li>""", """</ol></li><li><a href="text/hunt.xhtml">The Great Hunt</a><ol>""")
            .replace("""<li><a href="text/about.xhtml">""", """</ol></li><li><a href="text/about.xhtml">""")
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:9</dc:identifier><dc:title>0-14 The Wheel of Time</dc:title><dc:language>en</dc:language></metadata>
  <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
${files.keys.joinToString("\n") { """<item id="$it" href="text/$it.xhtml" media-type="application/xhtml+xml"/>""" }}</manifest>
  <spine>${files.keys.joinToString("") { """<itemref idref="$it"/>""" }}</spine>
</package>"""
        val navDoc = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc"><ol>$nav</ol></nav></body></html>"""
        val entries = linkedMapOf<String, ByteArray>(
            "META-INF/container.xml" to TestEpubs.CONTAINER.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/nav.xhtml" to navDoc.toByteArray(),
        )
        for ((f, t) in files) entries["OEBPS/text/$f.xhtml"] = page(t)
        val book = TestEpubs.build(File(context.cacheDir, "omnibus-${UUID.randomUUID()}.epub"), entries)
        EpubFile.open(book).use { epub ->
            val bf = EpubFileFiles(epub)
            val structure = BookStructure(bf)
            for ((choice, inHunt) in listOf(CollectionTitle.BOOK to "The Great Hunt", CollectionTitle.COLLECTION to "The Wheel of Time")) {
                val c = ReaderController(bf, structure, "0-14 The Wheel of Time", "en", null, ReaderSettings(collectionTitle = choice), context.assets)
                assertEquals(listOf("The Eye of the World", "The Great Hunt"), c.innerBooks.map { it.title })
                c.open(c.index("hunt-1.xhtml"), JSONObject().put("type", "start"))
                assertEquals(inHunt, c.headBookTitle)
                c.open(c.index("eye-2.xhtml"), JSONObject().put("type", "start"))
                assertEquals(if (choice == CollectionTitle.BOOK) "The Eye of the World" else "The Wheel of Time", c.headBookTitle)
                // The collection's own pages, before and after its books: its title.
                c.open(c.index("cover.xhtml"), JSONObject().put("type", "start"))
                assertEquals("The Wheel of Time", c.headBookTitle)
                c.open(c.index("about.xhtml"), JSONObject().put("type", "start"))
                assertEquals("The Wheel of Time", c.headBookTitle)
                c.release()
            }
        }
    }
}
