package dev.kolib.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookAiTest {
    private val book = BookAi.Book("The Keeper's Island", "A. Writer", "en")

    @Test
    fun aWordIsExplainedInItsSentence() {
        val engine = FakeEngine()
        val said = StringBuilder()
        val end = BookAi.explain(engine, " brooded ", "At the top, the old house brooded over the valley.", book, "French", {}) { said.append(it) }
        assertEquals(AiEnd.DONE, end)
        assertEquals(Prompts.LookedUp("noun, a thing", "it means what it says."), Prompts.readLookUp(said.toString()))
        val (system, user) = engine.asked.single()
        assertEquals("system", system.role)
        assertTrue(system.content.contains("Answer in French"))
        assertTrue(user.content.contains("“The Keeper's Island” by A. Writer"))
        assertTrue(user.content.contains("\"At the top, the old house brooded over the valley.\""))
        // Asked what it means first, then what it means in the sentence.
        assertTrue(user.content.contains("Define \"brooded\", then say what it means in this sentence."))
        assertTrue(user.content.indexOf(Prompts.MEANING) < user.content.indexOf(Prompts.HERE))
    }

    @Test
    fun aPassageIsPutInPlainWordsInstead() {
        val engine = FakeEngine { "It says the house looks gloomy." }
        val passage = "At the top, the old house brooded over the valley, its windows dark except for one."
        assertFalse(BookAi.isPhrase(passage))
        assertTrue(BookAi.isPhrase("old house"))
        BookAi.explain(engine, passage, "", book, "English", {}) {}
        val user = engine.asked.single().last()
        assertTrue(user.content.contains("plain words"))
        assertTrue(user.content.contains(passage))
    }

    private fun chapter(paragraphs: Int) = (1..paragraphs).map { n -> (1..40).joinToString(" ") { "p${n}w$it" } }

    @Test
    fun aShortChapterIsSummarisedInOneGo() {
        val engine = FakeEngine { "Mirren arrives.\n- She meets Tobias.\n- The lamp is dark." }
        val stages = ArrayList<Int>()
        val shown = StringBuilder()
        val (end, summary) = BookAi.summarise(engine, chapter(5), "The Dark Lamp", book, soFar = true, answerIn = "English", onStage = { stages += it.parts }) { shown.append(it) }
        assertEquals(AiEnd.DONE, end)
        assertEquals("Mirren arrives.\n- She meets Tobias.\n- The lamp is dark.", summary)
        assertEquals(summary, shown.toString())
        assertEquals(1, engine.asked.size)
        val asked = engine.asked.single().last().content
        assertTrue(asked.contains("the chapter “The Dark Lamp” of “The Keeper's Island”, up to where the reader has read"))
        assertTrue(asked.contains("p1w1") && asked.contains("p5w40"))
        assertTrue(stages.all { it == 1 })
    }

    @Test
    fun aLongChapterIsReadInPartsAndSummarisedFromNotes() {
        // A model that holds 1,400 tokens: the chapter (1,600 words) goes to it a part at a time, with room for its answer.
        var call = 0
        val engine = FakeEngine(contextTokens = 1400) { messages ->
            call++
            if (messages.last().content.contains("These are notes")) "All of it, in short." else "- note $call"
        }
        val stages = ArrayList<Pair<Int, Int>>()
        val shown = StringBuilder()
        val (end, summary) = BookAi.summarise(engine, chapter(40), "Long", book, soFar = false, answerIn = "English", onStage = { stages += it.part to it.parts }) { shown.append(it) }
        assertEquals(AiEnd.DONE, end)
        assertEquals("All of it, in short.", summary)
        // Only the summary is shown as it's written, not the notes.
        assertEquals("All of it, in short.", shown.toString())
        val parts = engine.asked.size - 1
        assertTrue(parts >= 2)
        // Every part was read, in order, and the summary written from the notes on them all.
        engine.asked.take(parts).forEachIndexed { i, m -> assertTrue(m.last().content.startsWith("This is part ${i + 1} of $parts")) }
        val last = engine.asked.last().last().content
        assertTrue((1..parts).all { last.contains("Part $it:\n- note $it") })
        assertFalse(last.contains("up to where the reader has read"))
        assertEquals((1..parts).map { it to parts } + (0 to parts), stages.distinct())
        // Nothing was too long for the model.
        assertTrue(engine.asked.all { m -> m.sumOf { engine.countTokens(it.content) } <= 1400 })
    }

    @Test
    fun stoppingASummaryPartWayLeavesNone() {
        val engine = FakeEngine(contextTokens = 1400) { "- a note of several words here" }
        engine.stopAfter = 2
        val (end, summary) = BookAi.summarise(engine, chapter(40), "Long", book, soFar = false, answerIn = "English", onStage = {}) {}
        assertEquals(AiEnd.CANCELLED, end)
        assertEquals("", summary)
        assertEquals(1, engine.asked.size)
        assertEquals(AiEnd.DONE to "", BookAi.summarise(FakeEngine(), emptyList(), "", book, false, "English", {}) {})
    }

    private val read = listOf(
        Triple(0, "The Ferry", listOf("The ferry left Harrow Point an hour late.", "Old Tobias Wren met her on the quay.", "“My brother Ansel looked at it straight,” he said.")),
        Triple(1, "The Dark Lamp", listOf("A little before midnight the wind dropped.", "Mirren heard the sea go quiet.", "She blew the whistle.")),
    )

    private fun at(read: List<Triple<Int, String, List<String>>> = this.read, screen: String? = null, before: List<Triple<Int, String, List<String>>>? = null,
                   notes: List<Pair<String, String>> = emptyList(), selected: String? = null) =
        BookAi.Place(read, "The Dark Lamp", 12, screen, before, notes, selected)

    /** A longer book, so that not all of it is told before the question: chapters of [lines] lines. */
    private fun longer(lines: Int = 60) = listOf(
        Triple(0, "The Ferry", listOf("The ferry left Harrow Point an hour late.", "“My brother Ansel looked at it straight,” he said.") + (1..lines).map { "Ferry line $it of the crossing, slow and grey and long." }),
        Triple(1, "The Dark Lamp", (1..lines).map { "Lamp line $it, in the dark of the tower." } + listOf("A little before midnight the wind dropped.", "She blew the whistle.")),
    )

    @Test
    fun aQuestionIsAnsweredFromWhatsBeenRead() {
        val engine = FakeEngine { "He is Tobias's brother." }
        val history = ArrayList<AiMessage>()
        val said = StringBuilder()
        val end = BookAi.answer(engine, history, "Who is Ansel?", book, at(longer()), "English", null, {}) { said.append(it) }
        assertEquals(AiEnd.DONE, end)
        assertEquals("He is Tobias's brother.", said.toString())
        val asked = engine.asked.single()
        val system = asked.first().content
        assertTrue(system.contains("12% of the way through, in “The Dark Lamp”"))
        assertTrue(system.contains("Never reveal anything that happens later"))
        // What the reader is on now is told first, before any question: it stays the same from one to the next.
        assertTrue(system.contains("The pages the reader is on now:\n") && system.contains("She blew the whistle."))
        assertTrue(system.endsWith("Answer in English."))
        val question = asked.last().content
        // With the question: the passage about him, under its chapter's name; then the question.
        assertTrue(question.contains("[The Ferry]\n"))
        assertTrue(question.contains("“My brother Ansel looked at it straight,” he said."))
        assertFalse(question.contains("She blew the whistle."))
        assertTrue(question.endsWith("Question: Who is Ansel?"))
        // The conversation remembers both.
        assertEquals(listOf("user", "assistant"), history.map { it.role })
        assertEquals("He is Tobias's brother.", history.last().content)
        // A follow-up comes after them, with the same first message: what was read for the first is still read.
        BookAi.answer(engine, history, "What did he do?", book, at(longer()), "English", null, {}) {}
        assertEquals(listOf("system", "user", "assistant", "user"), engine.asked.last().map { it.role })
        assertEquals(system, engine.asked.last().first().content)
        assertEquals(4, history.size)
    }

    @Test
    fun theScreenTheNotesAndTheWordsPickedOutAreTold() {
        val engine = FakeEngine { "Mirren is waiting for the ferry." }
        val screen = "A little before midnight the wind dropped.\nShe blew the whistle."
        val before = longer().let { it.take(1) + Triple(1, "The Dark Lamp", it[1].third.dropLast(2)) }
        val notes = listOf("The Ferry" to "Mirren crosses to the island and meets Tobias Wren, the keeper.")
        BookAi.answer(engine, ArrayList(), "Why is she here?", book, at(longer(), screen, before, notes, selected = "blew the whistle"), "English", null, {}) {}
        val (system, question) = engine.asked.single().let { it.first().content to it.last().content }
        // In order: the notes on the chapters before, what came just before the page, then the page itself.
        val n = system.indexOf("Your notes on the chapters before this one:\n[The Ferry] Mirren crosses to the island")
        val b = system.indexOf("What the reader read just before this page:\n")
        val p = system.indexOf("The page on the reader's screen now:\nA little before midnight the wind dropped.\nShe blew the whistle.")
        assertTrue("$n $b $p", n in 1 until b && b < p)
        assertTrue(system.substring(b, p).contains("Lamp line 60, in the dark of the tower."))
        assertFalse(system.substring(b, p).contains("She blew the whistle."))
        // The words picked out go with the question, not before it (they're for this question only).
        assertTrue(question.contains("The reader has picked out these words on the page:\n“blew the whistle”"))
        assertFalse(system.contains("picked out"))
    }

    @Test
    fun whatsReadAheadIsWhatTheQuestionIsAskedAfter() {
        val engine = FakeEngine { "An answer." }
        val history = arrayListOf(AiMessage.user("Question: Who is Tobias?"), AiMessage.assistant("The keeper."))
        val ahead = BookAi.ahead(engine, ArrayList(history), book, at(longer(), screen = "She blew the whistle."), "English")
        BookAi.answer(engine, ArrayList(history), "Who is Ansel?", book, at(longer(), screen = "She blew the whistle."), "English", null, {}) {}
        // Everything before the question, as it's asked: so reading it ahead leaves only the question to read.
        assertEquals(ahead, engine.asked.single().dropLast(1))
        assertEquals(3, ahead.size)
    }

    @Test
    fun aQuestionWithNothingToLookForGetsMoreOfWhatCameBefore() {
        val engine = FakeEngine { "The wind dropped and she blew the whistle." }
        BookAi.answer(engine, ArrayList(), "What has happened so far?", book, at(longer()), "English", null, {}) {}
        val (system, question) = engine.asked.single().let { it.first().content to it.last().content }
        assertTrue(system.contains("She blew the whistle."))
        // More of what came before the pages already told: not those again.
        assertTrue(question.contains("What you remember of the book so far:"))
        assertTrue(question.contains("Ferry line") || question.contains("Lamp line"))
        assertFalse(question.contains("She blew the whistle."))
    }

    @Test
    fun anOldConversationMakesRoomForANewQuestion() {
        val engine = FakeEngine(contextTokens = 900) { "An answer." }
        val history = ArrayList<AiMessage>()
        repeat(6) { n ->
            history += AiMessage.user((1..60).joinToString(" ") { "q${n}w$it" })
            history += AiMessage.assistant((1..40).joinToString(" ") { "a${n}w$it" })
        }
        val end = BookAi.answer(engine, history, "Who is Ansel?", book, at(), "English", null, {}) {}
        assertEquals(AiEnd.DONE, end)
        val asked = engine.asked.single()
        // The oldest turns went, a question and its answer together; the newest are still there, then this one.
        assertFalse(asked.any { it.content.contains("q0w1") })
        assertTrue(asked.any { it.content.contains("q5w1") })
        assertEquals("user", asked[1].role)
        assertTrue(history.size % 2 == 0)
        assertEquals("An answer.", history.last().content)
    }

    @Test
    fun aFailedAnswerIsntRemembered() {
        val engine = FakeEngine().apply { fail = "No connection" }
        val history = ArrayList<AiMessage>()
        assertEquals(AiEnd.FAILED, BookAi.answer(engine, history, "Who is Ansel?", book, at(), "English", null, {}) {})
        assertEquals("No connection", engine.lastError)
        assertTrue(history.isEmpty())
    }

    @Test
    fun theNotesKeptAreTheLatestWhereTheyDontAllFit() {
        val words = { s: String -> s.split(Regex("\\s+")).size }
        val notes = (1..20).map { "Chapter $it" to "Things happen in chapter $it, one after another." }
        val all = BookAi.notesText(notes, 1000, words)
        assertTrue(all.startsWith("[Chapter 1] Things happen in chapter 1") && all.endsWith("[Chapter 20] Things happen in chapter 20, one after another."))
        val some = BookAi.notesText(notes, 60, words)
        assertTrue(some.startsWith("(Notes on the first 15 chapters are left out.)\n[Chapter 16]"))
        assertTrue(some.endsWith("chapter 20, one after another."))
        assertEquals("", BookAi.notesText(emptyList(), 100, words))
    }

    @Test
    fun aChapterGetsANoteInOneGoOrFromItsParts() {
        val engine = FakeEngine { "Mirren reaches the island; Tobias meets her." }
        val (end, note) = BookAi.note(engine, chapter(5), "The Ferry", book)
        assertEquals(AiEnd.DONE to "Mirren reaches the island; Tobias meets her.", end to note)
        val asked = engine.asked.single()
        assertTrue(asked.first().content.contains("Write in English"))
        assertTrue(asked.last().content.contains("This is the chapter “The Ferry” of “The Keeper's Island”") && asked.last().content.contains("at most 90 words"))
        // A long one: notes on its parts first, then the note from them.
        var call = 0
        val small = FakeEngine(contextTokens = 1400) { m -> call++; if (m.last().content.contains("These are notes")) "The whole chapter, noted." else "- part $call" }
        val (end2, note2) = BookAi.note(small, chapter(40), "Long", book)
        assertEquals(AiEnd.DONE to "The whole chapter, noted.", end2 to note2)
        assertTrue(small.asked.size > 2)
        assertTrue(small.asked.dropLast(1).all { it.last().content.startsWith("This is part ") })
        // Stopped part-way: no note.
        val stopped = FakeEngine(contextTokens = 1400) { "- a note of several words here" }.apply { stopAfter = 1 }
        assertEquals(AiEnd.CANCELLED, BookAi.note(stopped, chapter(40), "Long", book).first)
    }
}
