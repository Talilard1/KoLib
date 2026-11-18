package dev.kolib.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAiTest {
    @Test
    fun theEditorsTextGoesToTheModelWithItsItalicsAndBoldMarked() {
        val html = "<p>She <i>never</i> looked back, not <b>once</b>.</p><h2>The Gate</h2><p>Caf&#233; &amp; bar<br/>open late.</p>"
        assertEquals("She *never* looked back, not **once**.\n\nThe Gate\n\nCafé & bar\nopen late.", EditorAi.marked(html))
        assertEquals("Just words.", EditorAi.marked("Just words."))
    }

    @Test
    fun theModelsTextComesBackAsParagraphsWithThem() {
        val text = "She *never* looked back, not **once**.\n\nA < B & 3 * 4 = 12."
        assertEquals("<p>She <i>never</i> looked back, not <b>once</b>.</p><p>A &lt; B &amp; 3 * 4 = 12.</p>", EditorAi.html(text))
        assertEquals("She never looked back, not once.\n\nA < B & 3 * 4 = 12.", EditorAi.plain(text))
        // There and back again.
        val html = "<p>One <i>two</i> three.</p><p><b>Four</b> five.</p>"
        assertEquals(html, EditorAi.html(EditorAi.marked(html)))
        // Thinking out loud isn't text.
        assertEquals("<p>Done.</p>", EditorAi.html("<think>hmm</think>Done."))
    }

    @Test
    fun aChangeIsMadeAPartAtATime() {
        val engine = FakeEngine(contextTokens = 1500) { messages -> messages.last().content.substringAfter("Text:\n").replace("teh", "the") }
        val text = (1..30).joinToString("\n\n") { n -> (1..40).joinToString(" ") { if (it == 7) "teh" else "w${n}x$it" } }
        val parts = ArrayList<Pair<Int, Int>>()
        val shown = StringBuilder()
        val (end, out) = EditorAi.change(engine, text, " Fix the typos. ", { a, b -> parts += a to b }) { shown.append(it) }
        assertEquals(AiEnd.DONE, end)
        // Every paragraph came back, in order, changed as asked and otherwise as it was.
        assertEquals(text.replace("teh", "the"), out)
        assertEquals(out, shown.toString())
        assertTrue(engine.asked.size > 1)
        assertEquals((1..engine.asked.size).map { it to engine.asked.size }, parts)
        val first = engine.asked.first()
        assertTrue(first.first().content.contains("Reply with the text after carrying out the instruction, and nothing else"))
        assertTrue(first.last().content.startsWith("Instruction: Fix the typos.\n\nText:\nw1x1 "))
        assertTrue(engine.asked.all { m -> m.sumOf { engine.countTokens(it.content) } < 1500 })
    }

    @Test
    fun wholeParagraphsAreChangedAGroupAtATimeAndEachGroupKeepsItsPlace() {
        // The model fixes a typo, joins the two halves of a broken sentence, and says nothing at all for one group.
        val engine = FakeEngine(contextTokens = 1500) { messages ->
            val text = messages.last().content.substringAfter("Text:\n")
            when {
                text.contains("SILENT") -> ""
                else -> text.replace("teh", "the").replace("broken in\n\nthe middle", "broken in the middle")
            }
        }
        val long = (1..12).map { n -> (1..40).joinToString(" ") { if (it == 7) "teh" else "w${n}x$it" } }
        val runs = listOf(long, listOf("A line broken in", "the middle of it.", "And *one* more."), listOf("SILENT paragraph."))
        val parts = ArrayList<Pair<Int, Int>>()
        val shown = StringBuilder()
        val (end, edits) = EditorAi.changeRuns(engine, runs, "Tidy it.", { a, b -> parts += a to b }) { shown.append(it) }
        assertEquals(AiEnd.DONE, end)
        // The long run went in several groups, each saying where in the run it starts and how many paragraphs it stands for.
        val first = edits.filter { it.run == 0 }
        assertTrue(first.size > 1)
        assertEquals(0, first.first().from)
        assertEquals(long.size, first.sumOf { it.count })
        assertEquals((0 until first.size).map { i -> first.take(i).sumOf { it.count } }, first.map { it.from })
        assertEquals(long.map { it.replace("teh", "the") }, first.flatMap { it.paragraphs })
        // Three paragraphs went and two came back: the edit says so, and the neighbours in other groups don't move.
        val second = edits.single { it.run == 1 }
        assertEquals(listOf(0, 3), listOf(second.from, second.count))
        assertEquals(listOf("A line broken in the middle of it.", "And *one* more."), second.paragraphs)
        // Nothing came back for the last group, so it's left as it was.
        assertTrue(edits.none { it.run == 2 })
        assertEquals((1..engine.asked.size).map { it to engine.asked.size }, parts)
        assertTrue(engine.asked.all { m -> m.sumOf { engine.countTokens(it.content) } < 1500 })
    }

    @Test
    fun aParagraphsWordsComeBackAsMarkupForInsideIt() {
        assertEquals("She <i>never</i> looked back, not <b>once</b>.", EditorAi.inner(" She *never* looked back, not **once**. "))
        assertEquals("A &lt; B &amp; 3 * 4 = 12.<br/>Next line.", EditorAi.inner("A < B & 3 * 4 = 12.\nNext line."))
    }

    @Test
    fun paragraphsGoInGroupsThatFit() {
        val words = { s: String -> s.split(" ").size }
        val groups = EditorAi.groups(listOf("a b c", "d e f g", "h", "i j k l m n o p q r", "s"), 10, words)
        assertEquals(listOf(0 to listOf("a b c"), 1 to listOf("d e f g", "h"), 3 to listOf("i j k l m n o p q r"), 4 to listOf("s")), groups)
        assertEquals(emptyList<Pair<Int, List<String>>>(), EditorAi.groups(emptyList(), 10, words))
    }

    @Test
    fun aChangeToParagraphsThatsStoppedGivesNothingBack() {
        val engine = FakeEngine { "one two three four" }.apply { stopAfter = 2 }
        assertEquals(AiEnd.CANCELLED, EditorAi.changeRuns(engine, listOf(listOf("Some text here.")), "Fix it.", { _, _ -> }) {}.first)
    }

    @Test
    fun aChangeThatsStoppedGivesNothingBack() {
        val engine = FakeEngine { "one two three four" }.apply { stopAfter = 2 }
        assertEquals(AiEnd.CANCELLED to "", EditorAi.change(engine, "Some text here.", "Fix it.", { _, _ -> }) {})
    }

    @Test
    fun writingCarriesOnFromWhatsThere() {
        val engine = FakeEngine { "The gate was open.\n\nShe went in." }
        val (end, out) = EditorAi.carryOn(engine, "Rosa walked up the lane. ".repeat(400), "She finds the gate open") {}
        assertEquals(AiEnd.DONE, end)
        assertEquals("The gate was open.\n\nShe went in.", out)
        val (system, user) = engine.asked.single()
        assertTrue(system.content.contains("What the writer wants next: She finds the gate open"))
        // The end of what's written is what it carries on from, not the whole book.
        assertTrue(user.content.length <= 4000 && user.content.endsWith("Rosa walked up the lane."))
        // An empty page is a start.
        EditorAi.carryOn(engine, "", "") {}
        assertTrue(engine.asked.last().last().content.contains("start the story"))
    }
}
