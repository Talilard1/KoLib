package dev.kolib.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

/** Chats kept on the device, and questions about the library. */
@RunWith(RobolectricTestRunner::class)
class ChatsTest {
    @Test
    fun chatsAreKeptUntilTheyreDeleted() {
        val dir = Files.createTempDirectory("chats").toFile()
        val store = ChatStore(dir)
        assertTrue(store.list().isEmpty())
        // Nothing answered yet: nothing to keep.
        store.save("a", emptyList())
        assertTrue(store.list().isEmpty())
        val first = listOf(SavedTurn("Who is Rosa?", "The gardener's daughter.", "The Garden"), SavedTurn("What should I read next?", "Dracula.", ""))
        store.save("a", first, now = 1_000)
        store.save("b", listOf(SavedTurn("A very long question ".repeat(8), "Yes.", "")), now = 2_000)
        val list = store.list()
        // The latest first, each called by its first question.
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals("Who is Rosa?", list[1].title)
        assertEquals(2, list[1].questions)
        assertTrue(list[0].title.length <= 70 && list[0].title.endsWith("…"))
        assertEquals(first, store.load("a"))
        // Added to, it moves up.
        store.save("a", first + SavedTurn("And then?", "They cleared the paths.", "The Garden"), now = 3_000)
        assertEquals(listOf("a", "b"), store.list().map { it.id })
        store.delete("a")
        assertEquals(listOf("b"), store.list().map { it.id })
        assertNull(store.load("a"))
        // Kept on the device: there for another run of the app.
        assertEquals(1, ChatStore(dir).list().size)
        dir.deleteRecursively()
    }

    private val shelf = listOf(
        LibraryAi.Shelved("Dracula", "Bram Stoker", null, listOf("Horror", "Classics"), "finished"),
        LibraryAi.Shelved("The Eye of the World", "Robert Jordan", "The Wheel of Time #1", listOf("Fantasy"), "42% read"),
        LibraryAi.Shelved("The Great Hunt", "Robert Jordan", "The Wheel of Time #2", listOf("Fantasy"), null),
        LibraryAi.Shelved("Meditations", "Marcus Aurelius", null, emptyList(), "not started"),
    )

    @Test
    fun aQuestionAboutTheLibraryComesWithTheListOfItsBooks() {
        val engine = FakeEngine { "You have two Wheel of Time books." }
        val history = ArrayList<AiMessage>()
        val end = LibraryAi.answer(engine, history, "Which Wheel of Time books do I have?", shelf, "English") {}
        assertEquals(AiEnd.DONE, end)
        val (system, user) = engine.asked.single()
        assertTrue(system.content.startsWith(Prompts.CHAT_DEFAULT))
        assertTrue(system.content.contains("their library, which has 4 books") && system.content.endsWith("Answer in English."))
        assertTrue(user.content.contains("- The Eye of the World — Robert Jordan (The Wheel of Time #1) [Fantasy] · 42% read"))
        assertTrue(user.content.contains("- Dracula — Bram Stoker [Horror, Classics] · finished"))
        assertTrue(user.content.endsWith("Question: Which Wheel of Time books do I have?"))
        // The books the question names come first.
        assertTrue(user.content.indexOf("The Eye of the World") < user.content.indexOf("Dracula"))
        // The conversation remembers the question as it was asked, without the list.
        assertEquals(listOf("Question: Which Wheel of Time books do I have?", "You have two Wheel of Time books."), history.map { it.content })
    }

    @Test
    fun aBigLibraryIsToldOfAsFarAsTheresRoomStartingWithWhatTheQuestionNames() {
        val many = (1..400).map { LibraryAi.Shelved("Book number $it", "Writer $it", null, emptyList(), null) } + shelf
        val words = { s: String -> s.split(Regex("\\s+")).size }
        val (list, left) = LibraryAi.shelf(many, "Have I got Dracula?", 200, words)
        assertTrue(list.startsWith("- Dracula — Bram Stoker"))
        assertTrue(left > 300)
        assertEquals(many.size, list.trim().lines().size + left)
    }

    @Test
    fun theUsersOwnInstructionsTakeThePlaceOfTheUsualOnes() {
        val engine = FakeEngine { "Aye." }
        LibraryAi.answer(engine, ArrayList(), "Anything good?", shelf, "English", instructions = "  Talk like a pirate.  ") {}
        val system = engine.asked.single().first().content
        assertTrue(system.startsWith("Talk like a pirate.\n\nThe reader is in their library"))
        assertFalse(system.contains("reading companion"))
        // In a book, with its reading turned off: told the book, shown none of it.
        LibraryAi.plain(engine, ArrayList(), "Who wrote it?", "Dracula", "Bram Stoker", "English") {}
        val plain = engine.asked.last()
        assertTrue(plain.first().content.contains("“Dracula” by Bram Stoker") && plain.first().content.contains("You have not been shown any of it this time"))
        assertEquals("Question: Who wrote it?", plain.last().content)
    }

    @Test
    fun whatsRoundTheBookIsntTakenForTheBook() {
        for (name in listOf("Introduction", "FOREWORD", "Copyright", "Title Page", "Contents", "Dedication", "About the Author", "Also by George R. R. Martin", "Acknowledgments", "A Note on Chronology", "Maps")) {
            assertTrue(name, BookText.frontMatter(name))
        }
        for (name in listOf("Prologue", "Bran", "Chapter 1", "An Introduction to Trouble", "The Map Room", "")) assertFalse(name, BookText.frontMatter(name))
        // A general question, a page into the prologue: it's told the pages the reader is on, not the introduction before them.
        val read = listOf(
            Triple(0, "Introduction", listOf("This edition celebrates the author's style and the brutal world he made.")),
            Triple(1, "Prologue", listOf("“We should start back,” Gared urged as the woods began to grow dark around them.")),
        )
        val engine = FakeEngine { "Three rangers are tracking wildlings north of the Wall." }
        val book = BookAi.Book("A Game of Thrones", "George R. R. Martin", "en", about = "Winter is coming. " + "The first book of an epic of rival houses. ".repeat(3))
        BookAi.answer(engine, ArrayList(), "what is this book about", book, read, "Prologue", 1, "English", null, {}) {}
        val (system, user) = engine.asked.single()
        assertTrue(user.content.contains("“We should start back,”"))
        assertFalse(user.content.contains("celebrates the author's style"))
        // And what the book says it's about, with leave to say what kind of book it is.
        assertTrue(system.content.contains("Its description: Winter is coming."))
        assertTrue(system.content.contains("never mention notes, passages or text you were given"))
        // In the introduction itself, that's what the reader is on.
        BookAi.answer(engine, ArrayList(), "what is this about", book, read.take(1), "Introduction", 0, "English", null, {}) {}
        assertTrue(engine.asked.last().last().content.contains("celebrates the author's style"))
    }
}
