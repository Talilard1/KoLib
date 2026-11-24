package dev.kolib.app.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A comic's pages being edited: put in order, turned, cropped, split, copied; each change undone and redone. */
class ComicDraftTest {
    private fun draft(n: Int = 5) = ComicDraft((0 until n).map { DraftPage(it + 1L, PageSource.InComic(it)) })

    private fun ComicDraft.order() = pages.map { (it.source as PageSource.InComic).index }

    private fun near(a: Crop?, b: Crop) {
        a!!
        for ((x, y) in listOf(a.left to b.left, a.top to b.top, a.right to b.right, a.bottom to b.bottom)) assertEquals(y, x, 0.0001f)
    }

    @Test
    fun pagesAreMovedAndEachMoveIsUndoneAndRedone() {
        val d = draft()
        d.move(0, 3)
        assertEquals(listOf(1, 2, 3, 0, 4), d.order())
        // Pages 2 and 5 (keys), in their order, to the front.
        d.moveTo(setOf(5L, 2L), 0)
        assertEquals(listOf(1, 4, 2, 3, 0), d.order())
        d.undo()
        assertEquals(listOf(1, 2, 3, 0, 4), d.order())
        d.undo()
        assertEquals(listOf(0, 1, 2, 3, 4), d.order())
        assertFalse(d.canUndo)
        d.redo()
        assertEquals(listOf(1, 2, 3, 0, 4), d.order())
        assertTrue(d.canRedo)
    }

    @Test
    fun aPageTurnedTakesItsCropRoundWithIt() {
        val d = draft(1)
        d.crop(1, Crop(0.1f, 0.2f, 0.6f, 0.9f))
        d.turn(setOf(1L), 1)
        assertEquals(1, d.pages[0].turns)
        near(d.pages[0].crop, Crop(0.1f, 0.1f, 0.8f, 0.6f))
        d.turn(setOf(1L), -1)
        assertEquals(0, d.pages[0].turns)
        near(d.pages[0].crop, Crop(0.1f, 0.2f, 0.6f, 0.9f))
        // A crop of the whole picture is no crop.
        d.crop(1, Crop.WHOLE)
        assertEquals(null, d.pages[0].crop)
        assertTrue(d.pages[0].asIs)
    }

    @Test
    fun aSpreadBecomesTwoPagesInTheOrderTheyreRead() {
        val d = draft(2)
        d.splitSpreads(setOf(1L), rightToLeft = false)
        assertEquals(listOf(Half.LEFT, Half.RIGHT, null), d.pages.map { it.half })
        assertEquals(3, d.pages.map { it.key }.toSet().size)
        val manga = draft(1)
        manga.splitSpreads(setOf(1L), rightToLeft = true)
        assertEquals(listOf(Half.RIGHT, Half.LEFT), manga.pages.map { it.half })
        // A half isn't split again.
        manga.splitSpreads(manga.pages.map { it.key }.toSet(), rightToLeft = true)
        assertEquals(2, manga.pages.size)
    }

    @Test
    fun theComicIsSplitWhereNewOnesStart() {
        val d = draft(5)
        d.toggleStart(3)
        assertEquals(listOf(2, 3), d.parts().map { it.size })
        // The first page always starts the first comic.
        d.toggleStart(1)
        assertEquals(listOf(2, 3), d.parts().map { it.size })
        d.toggleStart(3)
        assertEquals(listOf(5), d.parts().map { it.size })
    }

    @Test
    fun pagesAreCopiedMadeTheCoverAddedAndTakenOut() {
        val d = draft(3)
        d.duplicate(setOf(2L))
        assertEquals(listOf(0, 1, 1, 2), d.order())
        assertNotEquals(d.pages[1].key, d.pages[2].key)
        d.toFront(3L)
        assertEquals(listOf(2, 0, 1, 1), d.order())
        d.insert(1, listOf(File("a.jpg"), File("b.jpg")))
        assertEquals(PageSource.Picture(File("a.jpg")), d.pages[1].source)
        assertEquals(6, d.pages.size)
        d.delete(setOf(d.pages[1].key, d.pages[2].key))
        assertEquals(listOf(2, 0, 1, 1), d.order())
    }
}
