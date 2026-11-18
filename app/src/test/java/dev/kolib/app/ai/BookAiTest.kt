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
        assertEquals("it means what it says." to "noun, a thing", Prompts.readLookUp(said.toString()))
        val (system, user) = engine.asked.single()
        assertEquals("system", system.role)
        assertTrue(system.content.contains("Answer in French"))
        assertTrue(user.content.contains("“The Keeper's Island” by A. Writer"))
        assertTrue(user.content.contains("\"At the top, the old house brooded over the valley.\""))
        assertTrue(user.content.contains("Explain \"brooded\""))
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

    @Test
    fun aQuestionIsAnsweredFromWhatsBeenRead() {
        val engine = FakeEngine { "He is Tobias's brother." }
        val history = ArrayList<AiMessage>()
        val said = StringBuilder()
        val end = BookAi.answer(engine, history, "Who is Ansel?", book, read, "The Dark Lamp", 12, "English", null, {}) { said.append(it) }
        assertEquals(AiEnd.DONE, end)
        assertEquals("He is Tobias's brother.", said.toString())
        val asked = engine.asked.single()
        assertTrue(asked.first().content.contains("12% of the way through, in “The Dark Lamp”"))
        assertTrue(asked.first().content.contains("Never reveal anything that happens later"))
        val question = asked.last().content
        // The passage about him, under its chapter's name; the latest lines read; then the question.
        assertTrue(question.contains("[The Ferry]\n"))
        assertTrue(question.contains("“My brother Ansel looked at it straight,” he said."))
        assertTrue(question.contains("The pages the reader is on now:\n") && question.contains("She blew the whistle."))
        assertTrue(question.endsWith("Question: Who is Ansel?"))
        // The conversation remembers both.
        assertEquals(listOf("user", "assistant"), history.map { it.role })
        assertEquals("He is Tobias's brother.", history.last().content)
        // A follow-up comes after them.
        BookAi.answer(engine, history, "What did he do?", book, read, "The Dark Lamp", 12, "English", null, {}) {}
        assertEquals(listOf("system", "user", "assistant", "user"), engine.asked.last().map { it.role })
        assertEquals(4, history.size)
    }

    @Test
    fun aQuestionWithNothingToLookForGetsTheLatestPages() {
        val engine = FakeEngine { "The wind dropped and she blew the whistle." }
        BookAi.answer(engine, ArrayList(), "What has happened so far?", book, read, "The Dark Lamp", 12, "English", null, {}) {}
        val question = engine.asked.single().last().content
        assertFalse(question.contains("What you remember of the book so far"))
        assertTrue(question.contains("Old Tobias Wren met her on the quay.") && question.contains("She blew the whistle."))
    }

    @Test
    fun anOldConversationMakesRoomForANewQuestion() {
        val engine = FakeEngine(contextTokens = 900) { "An answer." }
        val history = ArrayList<AiMessage>()
        repeat(6) { n ->
            history += AiMessage.user((1..60).joinToString(" ") { "q${n}w$it" })
            history += AiMessage.assistant((1..40).joinToString(" ") { "a${n}w$it" })
        }
        val end = BookAi.answer(engine, history, "Who is Ansel?", book, read, "The Dark Lamp", 12, "English", null, {}) {}
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
        assertEquals(AiEnd.FAILED, BookAi.answer(engine, history, "Who is Ansel?", book, read, "The Dark Lamp", 12, "English", null, {}) {})
        assertEquals("No connection", engine.lastError)
        assertTrue(history.isEmpty())
    }
}
