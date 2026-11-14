package dev.kobolibrary.app.reader

import dev.kobolibrary.app.epub.Chapter
import dev.kobolibrary.app.epub.TocNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding the books inside a book that holds several, from its contents; and not finding them in one that doesn't. */
class OmnibusTest {

    /** Chapters named by file, in reading order. */
    private fun chapters(vararg files: String) = files.mapIndexed { i, f -> Chapter(i, "id$i", "text/$f.xhtml", true, f, false) }
    private fun entry(title: String, file: String, vararg under: TocNode) = TocNode(title, "text/$file.xhtml", null, under.toList())

    @Test
    fun eachBookWithItsChaptersUnderIt() {
        val ch = chapters("cover", "contents", "eye-title", "eye-map", "eye-pro", "eye-1", "eye-2", "eye-3",
            "hunt-title", "hunt-pro", "hunt-1", "hunt-2", "lord-title", "lord-pro", "lord-1", "about")
        val toc = listOf(
            entry("Cover", "cover"),
            entry("Contents", "contents"),
            entry("Book One: The Eye of the World", "eye-title",
                entry("Map", "eye-map"), entry("Prologue: Dragonmount", "eye-pro"), entry("1 An Empty Road", "eye-1"),
                entry("2 Strangers", "eye-2"), entry("3 The Peddler", "eye-3")),
            entry("The Great Hunt by Robert Jordan", "hunt-title",
                entry("Prologue: In the Shadow", "hunt-pro"), entry("Chapter 1: The Flame of Tar Valon", "hunt-1"), entry("Chapter 2", "hunt-2")),
            entry("06 Lord of Chaos (The Wheel of Time, Book 6)", "lord-title",
                entry("Prologue: The First Message", "lord-pro"), entry("1 Lion on the Hill", "lord-1"), entry("2 More", "lord-1")),
            entry("About the Author", "about"),
        )
        val books = Omnibus.books(toc, ch, "The Wheel of Time")
        assertEquals(
            listOf(InnerBook("The Eye of the World", 2, 7), InnerBook("The Great Hunt", 8, 11), InnerBook("Lord of Chaos", 12, 14)),
            books,
        )
        // Under one entry with the collection's own title.
        val wrapped = listOf(entry("Cover", "cover"), TocNode("The Wheel of Time", "text/contents.xhtml", null, toc.drop(1)))
        assertEquals(books, Omnibus.books(wrapped, ch, "The Wheel of Time"))
    }

    @Test
    fun oneListWithEachBooksTitleBeforeItsPrologue() {
        val names = listOf("cover", "copyright", "eye", "eye-maps", "eye-pro") + (1..6).map { "eye-$it" } + "eye-gloss" +
            listOf("hunt", "hunt-pro") + (1..6).map { "hunt-$it" } + listOf("about")
        val ch = chapters(*names.toTypedArray())
        val toc = listOf(
            entry("Cover", "cover"), entry("Copyright", "copyright"),
            entry("The Eye of the World", "eye"), entry("Maps", "eye-maps"), entry("Prologue: Dragonmount", "eye-pro"),
        ) + (1..6).map { entry("Chapter $it", "eye-$it") } + listOf(entry("Glossary", "eye-gloss"),
            entry("The Great Hunt", "hunt"), entry("Prologue: In the Shadow", "hunt-pro"),
        ) + (1..6).map { entry("Chapter $it", "hunt-$it") } + listOf(entry("About the Author", "about"))
        val books = Omnibus.books(toc, ch, "0-14 The Wheel of Time")
        assertEquals(listOf("The Eye of the World", "The Great Hunt"), books.map { it.title })
        assertEquals(names.indexOf("eye") to names.indexOf("eye-gloss"), books[0].first to books[0].last)
        assertEquals(names.indexOf("hunt") to names.indexOf("about") - 1, books[1].first to books[1].last)
    }

    @Test
    fun aSingleBookIsNotOne() {
        // In parts, each starting its chapters over.
        val ch = chapters("title", "p1", "c1", "c2", "c3", "p2", "c4", "c5", "c6")
        val parts = listOf(
            entry("Title Page", "title"),
            entry("Part One: The Shadow", "p1", entry("Chapter 1", "c1"), entry("Chapter 2", "c2"), entry("Chapter 3", "c3")),
            entry("Part Two: The Light", "p2", entry("Chapter 1", "c4"), entry("Chapter 2", "c5"), entry("Chapter 3", "c6")),
        )
        assertTrue(Omnibus.books(parts, ch, "The Shadow and the Light").isEmpty())
        // The same, as one list.
        assertTrue(Omnibus.books(parts.flatMap { n -> n.flatten().map { it.first.copy(children = emptyList()) } }, ch, "A Novel").isEmpty())
        // A novel with a sample of the next book at the end.
        val names = listOf("cover", "pro") + (1..8).map { "c$it" } + listOf("sample", "s1", "s2")
        val toc = listOf(entry("Cover", "cover"), entry("Prologue", "pro")) + (1..8).map { entry("Chapter $it", "c$it") } +
            listOf(entry("Excerpt: The Next Book", "sample"), entry("Chapter 1", "s1"), entry("Chapter 2", "s2"))
        assertTrue(Omnibus.books(toc, chapters(*names.toTypedArray()), "The Novel").isEmpty())
        // Groups that aren't books, in a book that doesn't say it holds several.
        val seasons = listOf(
            entry("Winter", "p1", entry("The Snow", "c1"), entry("The Ice", "c2"), entry("The Thaw", "c3")),
            entry("Spring", "p2", entry("The Rain", "c4"), entry("The Buds", "c5"), entry("The Light", "c6")),
        )
        assertTrue(Omnibus.books(seasons, ch, "A Year").isEmpty())
        // The Bible's books are named, but it's not a list of books with their own prologues.
        val bible = listOf("Genesis", "Exodus", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "Psalms", "Matthew", "1 Corinthians")
        assertTrue(Omnibus.books(bible.mapIndexed { i, t -> entry(t, "b$i") }, chapters(*bible.indices.map { "b$it" }.toTypedArray()), "The Holy Bible").isEmpty())
    }

    @Test
    fun aBooksNameFromItsEntry() {
        assertEquals("The Eye of the World", Omnibus.bookName("Book One: The Eye of the World"))
        assertEquals("The Eye of the World", Omnibus.bookName("Book 1 - The Eye of the World"))
        assertEquals("The Great Hunt", Omnibus.bookName("The Great Hunt by Robert Jordan"))
        assertEquals("Lord of Chaos", Omnibus.bookName("06 Lord of Chaos (The Wheel of Time, Book 6)"))
        assertEquals(null, Omnibus.bookName("Book One"))
        assertEquals(null, Omnibus.bookName("Part Two: The Light"))
        assertEquals(null, Omnibus.bookName("Copyright"))
    }
}
