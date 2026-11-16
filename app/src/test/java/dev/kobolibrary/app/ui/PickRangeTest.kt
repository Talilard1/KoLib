package dev.kobolibrary.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Choosing a run of books by holding a second one, as phones do. */
class PickRangeTest {
    private val shown = (1..20).map { "b$it" }
    private fun books(vararg n: Int) = n.map { "b$it" }.toSet()
    private fun run(from: Int, to: Int) = (from..to).map { "b$it" }.toSet()

    @Test
    fun holdingAnotherChoosesTheBooksBetweenAndCarriesOnFromIt() {
        val picked = LinkedHashMap<String, Boolean>()
        val r = PickRange()
        r.hold(picked, "b1", shown)
        assertEquals(books(1), picked.keys)
        r.hold(picked, "b9", shown)
        assertEquals(run(1, 9), picked.keys)
        // From the one held last: 9 to 15.
        r.hold(picked, "b15", shown)
        assertEquals(run(1, 15), picked.keys)
    }

    @Test
    fun itWorksBackwardsToo() {
        val picked = LinkedHashMap<String, Boolean>()
        val r = PickRange()
        r.hold(picked, "b9", shown)
        r.hold(picked, "b3", shown)
        assertEquals(run(3, 9), picked.keys)
    }

    @Test
    fun aBookTappedIsWhereTheNextRunStarts() {
        val picked = LinkedHashMap<String, Boolean>()
        val r = PickRange()
        r.hold(picked, "b20", shown)
        r.toggle(picked, "b5")
        r.hold(picked, "b8", shown)
        assertEquals(books(20) + run(5, 8), picked.keys)
        // Unchoosing it leaves nowhere to start from: the next hold chooses just the one held.
        r.toggle(picked, "b8")
        r.hold(picked, "b12", shown)
        assertEquals(books(20, 5, 6, 7, 12), picked.keys)
        // A book no longer shown (filtered out) isn't a start either.
        r.hold(picked, "b14", shown.filter { it != "b12" })
        assertEquals(books(20, 5, 6, 7, 12, 14), picked.keys)
    }
}
