package dev.kolib.app.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a swipe up or down sets the brightness: the sides chosen, and the part of them it starts in. */
class LevelSwipeTest {
    private val w = 400f
    private val h = 900f

    private fun ReaderSettings.at(x: Float, y: Float) = startsLevelSwipe(x, y, w, h)

    @Test
    fun theLeftAnywhereToStartWith() {
        val s = ReaderSettings()
        assertTrue(s.at(5f, 10f))
        assertTrue(s.at(5f, 450f))
        assertTrue(s.at(5f, 899f))
        assertFalse("not the right", s.at(395f, 450f))
        assertFalse("not in from the strip", s.at(40f, 450f))
    }

    @Test
    fun eitherSideOrBothOrNeither() {
        assertTrue(ReaderSettings(levelSide = LevelSide.RIGHT).at(395f, 450f))
        assertFalse(ReaderSettings(levelSide = LevelSide.RIGHT).at(5f, 450f))
        assertTrue(ReaderSettings(levelSide = LevelSide.BOTH).at(5f, 450f))
        assertTrue(ReaderSettings(levelSide = LevelSide.BOTH).at(395f, 450f))
        assertFalse(ReaderSettings(levelSide = LevelSide.BOTH).at(200f, 450f))
        assertFalse(ReaderSettings(levelSide = LevelSide.OFF).at(5f, 450f))
        assertFalse(ReaderSettings(levelSide = LevelSide.OFF).at(395f, 450f))
    }

    @Test
    fun theTopMiddleOrBottomThird() {
        val top = ReaderSettings(levelStart = LevelStart.TOP)
        assertTrue(top.at(5f, 100f))
        assertFalse(top.at(5f, 450f))
        assertFalse(top.at(5f, 800f))
        val middle = ReaderSettings(levelStart = LevelStart.MIDDLE)
        assertFalse(middle.at(5f, 100f))
        assertTrue(middle.at(5f, 450f))
        assertFalse(middle.at(5f, 800f))
        val bottom = ReaderSettings(levelStart = LevelStart.BOTTOM)
        assertFalse(bottom.at(5f, 100f))
        assertFalse(bottom.at(5f, 450f))
        assertTrue(bottom.at(5f, 800f))
        assertTrue("the very foot", bottom.at(5f, 900f))
    }

    @Test
    fun theyDontChangeThePage() {
        val s = ReaderSettings()
        assertTrue(s.pageOnly() == s.copy(levelSide = LevelSide.BOTH, levelStart = LevelStart.TOP).pageOnly())
    }
}
