package dev.kobolibrary.app.epub

import dev.kobolibrary.app.epub.FindReplace.Flag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BookEditingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun garden(): BookEdit = BookEdit(TestBooks.garden(tmp.newFile("garden.epub")))
    private fun BookEdit.t(path: String) = text("OEBPS/$path")!!

    // ---- structure ----------------------------------------------------------------------------

    @Test
    fun readsChaptersAndContentsFromTheNcx() = garden().use { e ->
        val s = e.structure
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Chapter 2", "Part Two", "Chapter 3", "Notes"), s.chapters.map { it.title })
        assertEquals(listOf(true, true, true, true, true, false), s.chapters.map { it.linear })
        assertEquals(listOf("Part One", "Part Two", "Notes"), s.toc.map { it.title })
        assertEquals(listOf("Chapter 1: Rose", "Chapter 2"), s.toc[0].children.map { it.title })
        assertEquals("c2", s.toc[0].children[1].fragment)
        assertEquals("OEBPS/text/part1.xhtml", ChapterOps.startPath(s))
    }

    @Test
    fun readsContentsFromTheNavDocument() {
        BookEdit(TestBooks.threeParts(tmp.newFile("three.epub"))).use { e ->
            val s = e.structure
            assertEquals(listOf("One", "Middle"), s.toc.map { it.title })
            // A heading without a link of its own stands for its first entry.
            assertEquals("OEBPS/text/two.xhtml", s.toc[1].path)
            assertEquals(listOf("One", "Two", "Three"), s.chapters.map { it.title })
            assertEquals("OEBPS/text/one.xhtml", ChapterOps.startPath(s))
        }
    }

    // ---- strict check -------------------------------------------------------------------------

    @Test
    fun strictCheckFindsWhatReadersWouldRefuse() {
        assertNull(XmlCheck.check(TestBooks.CH1, htmlEntities = true))
        assertEquals("Found </p>, but expected </em>", XmlCheck.check("<html><body><p><em>hi</p></em></body></html>")?.message)
        assertEquals(1, XmlCheck.check("<a>\n<b x=1/></a>")?.line?.minus(1))
        assertNotNull(XmlCheck.check("<p>Fish & chips</p>"))
        assertNotNull(XmlCheck.check("<p>&nbsp;</p>"))
        assertNull(XmlCheck.check("<p>&nbsp;</p>", htmlEntities = true))
        assertNotNull(XmlCheck.check("<p a=\"1\" a=\"2\"/>"))
        assertNotNull(XmlCheck.check("<p></p><p></p>"))
        assertTrue(XmlCheck.hasXhtmlDoctype(TestBooks.CH1))
    }

    // ---- find & replace -----------------------------------------------------------------------

    @Test
    fun findsVisibleTextWithContext() = garden().use { e ->
        val found = FindReplace.search(e, e.structure, FindReplace.Options("garden"))
        // Tags, styles and file names aren't searched; "gar-den" isn't the word.
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Chapter 2"), found.map { it.chapter }.distinct())
        val first = found.first { it.chapter == "Chapter 1: Rose" }
        assertEquals("Rose walked into the ", first.before)
        assertTrue(first.after, first.after.startsWith(". She saw a rose"))
        // Entities are searched as the characters they stand for.
        val fish = FindReplace.search(e, e.structure, FindReplace.Options("Fish & chips")).single()
        assertEquals("Fish & chips", fish.text)
        assertEquals("Fish &amp; chips", e.t("text/ch1.xhtml").substring(fish.rawStart, fish.rawEnd))
    }

    @Test
    fun nameModeFlagsTheOrdinaryWord() = garden().use { e ->
        val found = FindReplace.search(e, e.structure, FindReplace.Options("Rose", nameMode = true), only = setOf("OEBPS/text/ch1.xhtml"))
        val flags = found.map { it.text to it.flag }
        assertEquals(
            listOf(
                "Rose" to Flag.UNSURE,   // starts a paragraph
                "rose" to Flag.NOT_NAME, // lower case
                "Rose" to Flag.NAME,     // "and Rose smiled"
                "Rose" to Flag.NOT_NAME, // the Rose Inn
                "Rose" to Flag.UNSURE,   // told her Rose
                "ROSE" to Flag.UNSURE,   // all capitals
                "Rose" to Flag.NAME,     // Rose’s supper
            ),
            flags,
        )
        assertEquals("after “the”", found[3].reason)
    }

    @Test
    fun replacingOneMovesTheRestAlong() = garden().use { e ->
        val opts = FindReplace.Options("Rose", nameMode = true)
        var matches = FindReplace.search(e, e.structure, opts, only = setOf("OEBPS/text/ch1.xhtml"))
        val target = matches[2]
        val (changes, skipped) = FindReplace.replace(e, listOf(target), "Lilibet", opts)
        assertEquals(0, skipped)
        e.apply("Replaced", changes)
        matches = FindReplace.shiftAfter(matches - target, target, FindReplace.replacementFor(target, "Lilibet", opts))
        // The rest are still found exactly where the list says, though the text has grown.
        for (m in matches) assertEquals(m.text, e.t("text/ch1.xhtml").substring(m.rawStart, m.rawEnd).replace("&#8217;", "’"))
        assertTrue(e.t("text/ch1.xhtml").contains("and Lilibet smiled"))
    }

    @Test
    fun replaceAllSkipsRemovedEntriesAndFollowsCase() = garden().use { e ->
        val opts = FindReplace.Options("rose", nameMode = true)
        val matches = FindReplace.search(e, e.structure, opts)
        val wanted = matches.filter { it.flag != Flag.NOT_NAME }
        val (changes, _) = FindReplace.replace(e, wanted, "lily", opts)
        e.apply("Replaced all", changes)
        val ch1 = e.t("text/ch1.xhtml")
        assertTrue(ch1.contains("Lily walked into the garden"))
        assertTrue(ch1.contains("a rose by the wall"))
        assertTrue(ch1.contains("the Rose Inn"))
        assertTrue(ch1.contains("The sign said LILY"))
        assertTrue(ch1.contains("Lily&#8217;s supper"))
        assertTrue(e.t("text/notes.xhtml").contains("A note about Lily."))
        assertNull(XmlCheck.check(ch1, htmlEntities = true))
    }

    @Test
    fun replacementsAreEscapedAndPatternsUseGroups() = garden().use { e ->
        val opts = FindReplace.Options("(\\w+) & (\\w+)", pattern = true, matchCase = true)
        val m = FindReplace.search(e, e.structure, opts)
        assertEquals(1, m.size)
        e.apply("x", FindReplace.replace(e, m, "$2 <and> $1", opts).first)
        assertTrue(e.t("text/ch1.xhtml").contains("chips &lt;and&gt; Fish"))
        val whole = FindReplace.search(e, e.structure, FindReplace.Options("the", wholeWords = true, matchCase = true))
        assertTrue(whole.all { it.text == "the" })
        assertEquals("LILY", FindReplace.followCase("ROSE", "lily"))
        assertEquals("Lily", FindReplace.followCase("Rose", "lily"))
        assertEquals("lily", FindReplace.followCase("rose", "lily"))
    }

    // ---- clean-ups ----------------------------------------------------------------------------

    @Test
    fun cleanUpsFixTheUsualThings() = garden().use { e ->
        val s = e.structure
        val blank = Cleanups.run(Cleanups.Kind.BLANK_LINES, e, s)
        assertEquals(2, blank.count) // three empty paragraphs in a row: the first stays
        e.apply("blank", blank.changes)
        assertEquals(1, Regex("<p>&#160;</p>|<p></p>|<p><br/></p>").findAll(e.t("text/ch1.xhtml")).count())

        val quotes = Cleanups.run(Cleanups.Kind.CURLY_QUOTES, e, e.structure)
        e.apply("quotes", quotes.changes)
        assertTrue(e.t("text/ch1.xhtml"), e.t("text/ch1.xhtml").contains("He said “it’s fine” and left"))

        val dashes = Cleanups.run(Cleanups.Kind.DASHES, e, e.structure)
        assertEquals(1, dashes.count)
        e.apply("dashes", dashes.changes)
        assertTrue(e.t("text/ch1.xhtml").contains("left — quickly"))

        val joins = Cleanups.run(Cleanups.Kind.JOIN_LINES, e, e.structure)
        e.apply("joins", joins.changes)
        val ch2 = e.t("text/ch2.xhtml")
        assertTrue(ch2, ch2.contains("<p>The rain fell on the roof all night, and the garden was soaked by morning.</p>"))
        // "well-known" is how the book spells it elsewhere? No: but "well" + "known" isn't a word, so the hyphen stays.
        assertTrue(ch2, ch2.contains("<p>A well-known story about the garden.</p>"))
        assertEquals(3, joins.count)

        val fonts = Cleanups.run(Cleanups.Kind.STRIP_FONTS, e, e.structure)
        assertEquals(1, fonts.count)
        e.apply("fonts", fonts.changes)
        assertFalse(e.has("OEBPS/fonts/fancy.ttf"))
        assertFalse(e.t("content.opf").contains("fancy.ttf"))
        assertFalse(e.t("styles/style.css").contains("@font-face"))
        assertTrue(e.t("styles/style.css").contains("text-indent"))
        BookCheck.run(e).let { assertTrue(it.toString(), it.isEmpty()) }
    }

    // ---- chapters -----------------------------------------------------------------------------

    @Test
    fun renamingChangesTheContentsAndHeading() = garden().use { e ->
        val r = ChapterOps.rename(e, e.structure, "OEBPS/text/ch2.xhtml", "The Rain", alsoHeading = true)
        e.apply(r.label, r.changes)
        assertEquals("The Rain", e.structure.chapters[2].title)
        assertTrue(e.t("toc.ncx").contains("<text>The Rain</text>"))
        assertTrue(e.t("text/ch2.xhtml").contains("<h2 id=\"c2\">The Rain</h2>"))
        assertEquals(listOf("Part One", "Part Two", "Notes"), e.structure.toc.map { it.title })
    }

    @Test
    fun reorderingMovesTheSpineAndContents() = garden().use { e ->
        val order = e.structure.chapters.map { it.path }.toMutableList()
        order.add(1, order.removeAt(4)) // chapter 3 to just after part one
        val r = ChapterOps.reorder(e, e.structure, order)
        e.apply(r.label, r.changes)
        assertEquals(listOf("Part One", "Chapter 3", "Chapter 1: Rose", "Chapter 2", "Part Two", "Notes"), e.structure.chapters.map { it.title })
        assertEquals(listOf("Chapter 3", "Chapter 1: Rose", "Chapter 2"), e.structure.toc[0].children.map { it.title })
        assertTrue(e.t("content.opf").contains("<itemref idref=\"notes\" linear=\"no\"/>"))
        assertNull(XmlCheck.check(e.t("toc.ncx")))
        assertNull(XmlCheck.check(e.t("content.opf")))
    }

    @Test
    fun mergingJoinsTheFilesAndFixesLinks() = garden().use { e ->
        val r = ChapterOps.mergeWithPrevious(e, e.structure, "OEBPS/text/ch2.xhtml")
        e.apply(r.label, r.changes)
        assertFalse(e.has("OEBPS/text/ch2.xhtml"))
        val ch1 = e.t("text/ch1.xhtml")
        assertTrue(ch1.contains("<div id=\"kl-joined-1\" class=\"chapter\">"))
        assertTrue(ch1.contains("Rose went home."))
        assertNull(XmlCheck.check(ch1, htmlEntities = true))
        // Links to the old file now go into the joined one.
        assertTrue(e.t("text/ch3.xhtml"), e.t("text/ch3.xhtml").contains("href=\"ch1.xhtml#kl-joined-1\""))
        assertEquals("OEBPS/text/ch1.xhtml", e.structure.toc[0].children[1].path)
        assertEquals("c2", e.structure.toc[0].children[1].fragment)
        assertEquals(5, e.structure.chapters.size)
        assertTrue(BookCheck.run(e).isEmpty())
    }

    @Test
    fun splittingMakesANewChapter() = garden().use { e ->
        val text = e.t("text/ch2.xhtml")
        val point = ChapterOps.splitPoints(text).first { it.tag == "h3" }
        val r = ChapterOps.split(e, e.structure, "OEBPS/text/ch2.xhtml", point.offset, null)
        e.apply(r.label, r.changes)
        val s = e.structure
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Chapter 2", "A Section", "Part Two", "Chapter 3", "Notes"), s.chapters.map { it.title })
        val newFile = r.focus!!
        assertEquals("OEBPS/text/ch2-2.xhtml", newFile)
        assertTrue(e.text(newFile)!!.contains("<h3 id=\"s2\">A Section</h3>"))
        assertFalse(e.t("text/ch2.xhtml").contains("A Section"))
        assertEquals(listOf("Chapter 1: Rose", "Chapter 2", "A Section"), s.toc[0].children.map { it.title })
        assertTrue(BookCheck.run(e).isEmpty())
    }

    @Test
    fun addingAChapterPutsItWhereItBelongs() = garden().use { e ->
        var r = ChapterOps.addChapter(e, e.structure, "OEBPS/text/ch1.xhtml", "Interlude")
        e.apply(r.label, r.changes)
        val added = r.focus!!
        assertEquals("OEBPS/text/chapter-7.xhtml", added)
        assertEquals(listOf("Part One", "Chapter 1: Rose", "Interlude", "Chapter 2", "Part Two", "Chapter 3", "Notes"), e.structure.chapters.map { it.title })
        assertEquals(listOf("Chapter 1: Rose", "Interlude", "Chapter 2"), e.structure.toc[0].children.map { it.title })
        // It looks like its neighbours: their style sheet and their kind of heading.
        val text = e.text(added)!!
        assertTrue(text, text.contains("href=\"../styles/style.css\"") && text.contains("<h2>Interlude</h2>"))
        assertTrue(BookCheck.run(e).isEmpty())

        // After a part's own page, it's the part's first chapter, with a chapter's heading.
        r = ChapterOps.addChapter(e, e.structure, "OEBPS/text/part2.xhtml", "Prologue")
        e.apply(r.label, r.changes)
        assertEquals(listOf("Part One", "Part Two", "Notes"), e.structure.toc.map { it.title })
        assertEquals(listOf("Prologue", "Chapter 3"), e.structure.toc[1].children.map { it.title })
        assertTrue(e.text(r.focus!!)!!.contains("<h2>Prologue</h2>"))

        // With nowhere given, after the last chapter read through: before the notes, which are read by link.
        r = ChapterOps.addChapter(e, e.structure, null, "Epilogue")
        e.apply(r.label, r.changes)
        assertEquals(listOf("Chapter 3", "Epilogue", "Notes"), e.structure.chapters.map { it.title }.takeLast(3))
        assertEquals(listOf("Prologue", "Chapter 3", "Epilogue"), e.structure.toc[1].children.map { it.title })
        assertTrue(BookCheck.run(e).isEmpty())
        // And it's one step to undo.
        e.undo()
        assertEquals(listOf("Chapter 3", "Notes"), e.structure.chapters.map { it.title }.takeLast(2))
    }

    @Test
    fun deletingRemovesLinksAndEntries() = garden().use { e ->
        val r = ChapterOps.delete(e, e.structure, "OEBPS/text/ch1.xhtml")
        e.apply(r.label, r.changes)
        assertFalse(e.has("OEBPS/text/ch1.xhtml"))
        assertTrue(e.t("text/ch3.xhtml").contains("Back in the first chapter and"))
        assertFalse(e.t("text/notes.xhtml").contains("ch1.xhtml"))
        assertEquals(listOf("Chapter 2"), e.structure.toc[0].children.map { it.title })
        assertTrue(BookCheck.run(e).isEmpty())
    }

    @Test
    fun hidingAndChoosingTheStart() = garden().use { e ->
        var r = ChapterOps.setHidden(e, e.structure, "OEBPS/text/part2.xhtml", true)
        e.apply(r.label, r.changes)
        assertFalse(e.structure.chapters[3].linear)
        // Its contents entry stays, so it can still be reached.
        assertEquals(listOf("Part One", "Part Two", "Notes"), e.structure.toc.map { it.title })
        r = ChapterOps.setHidden(e, e.structure, "OEBPS/text/part2.xhtml", false)
        e.apply(r.label, r.changes)
        assertTrue(e.structure.chapters[3].linear)
        assertTrue(e.t("content.opf").contains("<itemref idref=\"part2\"/>"))
        r = ChapterOps.setStart(e, e.structure, "OEBPS/text/ch1.xhtml")
        e.apply(r.label, r.changes)
        assertEquals("OEBPS/text/ch1.xhtml", ChapterOps.startPath(e.structure))

        BookEdit(TestBooks.threeParts(tmp.newFile("three.epub"))).use { n ->
            val s = ChapterOps.setStart(n, n.structure, "OEBPS/text/two.xhtml")
            n.apply(s.label, s.changes)
            assertTrue(n.text("OEBPS/nav.xhtml")!!.contains("epub:type=\"bodymatter\" href=\"text/two.xhtml\""))
        }
    }

    @Test
    fun contentsFromHeadings() = garden().use { e ->
        val r = ChapterOps.contentsFromHeadings(e, e.structure, deepest = 3)
        e.apply(r.label, r.changes)
        val titles = Contents.flatten(e.structure.toc).map { it.first.title to it.second }
        assertEquals("Part One" to 0, titles[0])
        assertTrue(titles.toString(), titles.contains("A Section" to 2))
    }

    // ---- the edit itself ------------------------------------------------------------------------

    @Test
    fun undoRedoAndTypingTogether() = garden().use { e ->
        val path = "OEBPS/text/ch3.xhtml"
        val original = e.text(path)!!
        assertFalse(e.dirty)
        e.apply("Typing", mapOf(path to original.replace("Back", "Back again").toByteArray()), coalesceKey = "type", now = 1000)
        e.apply("Typing", mapOf(path to original.replace("Back", "Back again, again").toByteArray()), coalesceKey = "type", now = 2000)
        assertEquals(1, e.changeCount)
        e.apply("Typing", mapOf(path to original.replace("Back", "Later").toByteArray()), coalesceKey = "type", now = 20_000)
        assertEquals(2, e.changeCount)
        assertTrue(e.dirty)
        e.undo()
        assertTrue(e.text(path)!!.contains("Back again, again"))
        e.undo()
        assertEquals(original, e.text(path))
        assertFalse(e.dirty)
        e.redo()
        assertTrue(e.text(path)!!.contains("Back again, again"))
        // Writing back the saved text is no change.
        assertFalse(e.apply("Same", mapOf(path to e.text(path)!!.toByteArray())))
    }

    @Test
    fun savingKeepsTheOriginalAndUndoStillWorks() {
        val file = TestBooks.garden(tmp.newFile("g.epub"))
        val original = File(tmp.root, "original.epub")
        BookEdit(file).use { e ->
            val r = ChapterOps.mergeWithPrevious(e, e.structure, "OEBPS/text/ch2.xhtml")
            e.apply(r.label, r.changes)
            val saved = e.save(original)
            assertTrue(saved is BookEdit.SaveResult.Saved)
            assertFalse(e.dirty)
            assertTrue(original.exists())
            EpubFile.open(file).use { assertFalse(it.has("OEBPS/text/ch2.xhtml")) }
            EpubFile.open(original).use { assertTrue(it.has("OEBPS/text/ch2.xhtml")) }
            // Undo after saving brings back the chapter, as an unsaved change.
            e.undo()
            assertTrue(e.dirty)
            assertTrue(e.has("OEBPS/text/ch2.xhtml"))
            assertEquals(TestBooks.CH2, e.text("OEBPS/text/ch2.xhtml"))
            e.redo()
            assertFalse(e.dirty)
            assertFalse(e.has("OEBPS/text/ch2.xhtml"))
        }
    }

    @Test
    fun aSaveThatWouldDamageTheBookIsRefused() {
        val file = TestBooks.garden(tmp.newFile("g.epub"))
        val before = file.readBytes()
        BookEdit(file).use { e ->
            val path = "OEBPS/text/ch3.xhtml"
            e.apply("Broke it", mapOf(path to e.text(path)!!.replace("</p>", "").toByteArray()))
            val result = e.save(File(tmp.root, "o.epub"))
            assertTrue(result is BookEdit.SaveResult.Refused)
            assertEquals(path, (result as BookEdit.SaveResult.Refused).problems.single().path)
            assertTrue(e.dirty)
        }
        assertTrue(before.contentEquals(file.readBytes()))
    }
}
