package dev.kobolibrary.app.ui

/**
 * Choosing a run of books at once, as phones do: with one chosen, pressing and holding another chooses it and every
 * book between the two, either way round. The run always goes from the book chosen last (by a tap or a hold), so
 * holding one more carries on from the end of the run before.
 */
class PickRange {
    /** The book the next run starts from: the last one chosen. */
    var anchor: String? = null
        private set

    fun reset() {
        anchor = null
    }

    /** A tap: chooses [id], or unchooses it. A book chosen becomes where the next run starts. */
    fun toggle(picked: MutableMap<String, Boolean>, id: String) {
        if (picked.remove(id) == null) {
            picked[id] = true
            anchor = id
        } else if (anchor == id) {
            anchor = null
        }
    }

    /**
     * A hold on [id]: chooses it and, if a book was chosen before it, every book between the two in [shown] (the books
     * in the order they're shown). [id] is where the next run starts.
     */
    fun hold(picked: MutableMap<String, Boolean>, id: String, shown: List<String>) {
        val from = anchor?.let { shown.indexOf(it) } ?: -1
        val to = shown.indexOf(id)
        if (from >= 0 && to >= 0) {
            for (i in minOf(from, to)..maxOf(from, to)) picked[shown[i]] = true
        } else {
            picked[id] = true
        }
        anchor = id
    }
}
