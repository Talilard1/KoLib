package dev.kolib.app.ui.ai

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotMotionTest {
    private fun MascotMotion.run(seconds: Float) = repeat((seconds * 60).toInt()) { step(1 / 60f) }

    @Test
    fun itShowsWhatTheAiIsDoing() {
        val m = MascotMotion(seed = 1)
        m.run(0.5f)
        assertEquals(Act.IDLE, m.act)
        for ((mood, act) in listOf(
            BotMood.READING to Act.READING, BotMood.THINKING to Act.THINKING, BotMood.WRITING to Act.WRITING,
            BotMood.NOTING to Act.WRITING, BotMood.HAPPY to Act.HAPPY, BotMood.SAD to Act.SAD, BotMood.IDLE to Act.IDLE,
        )) {
            m.mood = mood
            m.run(0.1f)
            assertEquals(act, m.act)
        }
        // It eases into what it's doing: the book in its hands as it reads; its mouth still as it writes notes.
        m.mood = BotMood.READING
        m.run(1f)
        assertTrue(m.book > 0.9f)
        m.mood = BotMood.NOTING
        m.run(1f)
        assertTrue(m.note > 0.9f && m.book < 0.1f)
        assertTrue(m.talk < 0.01f)
        // Appearing, it says hello, then gets on with it.
        m.mood = BotMood.IDLE
        m.appear()
        m.run(0.1f)
        assertEquals(Act.HELLO, m.act)
        m.run(2f)
        assertEquals(Act.IDLE, m.act)
    }

    @Test
    fun whatsDoneToItComesFirst() {
        val m = MascotMotion(seed = 2)
        m.mood = BotMood.THINKING
        m.held = true
        m.run(0.1f)
        assertEquals(Act.HELD, m.act)
        m.scared = true
        m.run(0.1f)
        assertEquals(Act.SCARED, m.act)
        m.scared = false
        m.held = false
        m.velocity = Offset(3_000f, 0f)
        m.run(0.1f)
        assertEquals(Act.FLYING, m.act)
        assertTrue(m.lean > 0.5f)
        m.velocity = Offset.Zero
        // A gentle knock goes unnoticed; a hard one doesn't, for a moment.
        m.bump(400f)
        m.run(0.05f)
        assertEquals(Act.THINKING, m.act)
        m.bump(2_000f)
        m.run(0.05f)
        assertEquals(Act.BUMPED, m.act)
        m.run(0.5f)
        assertEquals(Act.THINKING, m.act)
        // Thrown hard, or bounced about: dizzy a while, then back to what it was doing.
        m.thrown(5_000f, 0)
        m.run(0.1f)
        assertEquals(Act.DIZZY, m.act)
        m.run(1.5f)
        assertEquals(Act.THINKING, m.act)
        m.thrown(2_000f, 1)
        m.run(0.1f)
        assertEquals(Act.THINKING, m.act)
        m.thrown(2_000f, 2)
        m.run(0.1f)
        assertEquals(Act.DIZZY, m.act)
    }

    @Test
    fun shakenAboutItGetsDizzy() {
        val m = MascotMotion(seed = 6)
        m.shakeReach = 20f
        m.held = true
        m.run(0.1f)
        assertEquals(Act.HELD, m.act)
        // Carried along, one way: not shaken.
        repeat(10) {
            m.dragged(Offset(5f, 0f))
            m.step(1 / 60f)
        }
        assertEquals(Act.HELD, m.act)
        // A little to and fro: not enough.
        repeat(4) { i ->
            repeat(3) {
                m.dragged(Offset(if (i % 2 == 0) 15f else -15f, 0f))
                m.step(1 / 60f)
            }
        }
        assertEquals(Act.HELD, m.act)
        // Back and forth, hard, again and again: dizzy, even as it's held.
        repeat(8) { i ->
            repeat(3) {
                m.dragged(Offset(if (i % 2 == 0) 15f else -15f, 0f))
                m.step(1 / 60f)
            }
        }
        assertEquals(Act.DIZZY, m.act)
        // Let go: dizzy a while yet, then itself again.
        m.held = false
        m.run(0.2f)
        assertEquals(Act.DIZZY, m.act)
        m.run(2f)
        assertEquals(Act.IDLE, m.act)
    }

    @Test
    fun leftAloneItNodsOffButAnIconDoesnt() {
        val m = MascotMotion(seed = 3)
        m.run(50f)
        assertEquals(Act.SLEEPING, m.act)
        assertTrue(m.sleepy > 0.9f)
        // Woken by anything: picked up.
        m.held = true
        m.run(0.1f)
        assertEquals(Act.HELD, m.act)
        val icon = MascotMotion(calm = true, seed = 3)
        icon.run(60f)
        assertEquals(Act.IDLE, icon.act)
        assertEquals(Fidget.NONE, icon.fidget)
    }

    @Test
    fun inTheSettingsEachShowsItselfOff() {
        val a = MascotMotion(seed = 4).apply { demo = true }
        val b = MascotMotion(seed = 5).apply { demo = true; demoShift = 2.6f }
        val seen = HashSet<Act>()
        repeat(8 * 156) {
            a.step(1 / 60f)
            b.step(1 / 60f)
            seen += a.act
        }
        assertEquals(setOf(Act.HELLO, Act.READING, Act.THINKING, Act.WRITING, Act.HAPPY, Act.IDLE, Act.SLEEPING, Act.DIZZY), seen)
        // One a step ahead of the next, so they're not all doing the same at once.
        assertNotEquals(a.act, b.act)
    }
}
