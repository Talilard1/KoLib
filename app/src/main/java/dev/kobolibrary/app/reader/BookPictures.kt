package dev.kobolibrary.app.reader

/** The pictures in a book, for the list of them in the contents panel. */
object BookPictures {

    /** A picture: where it is in the book, the chapter it first comes in, and its size. */
    data class Found(val path: String, val chapter: Int, val width: Int, val height: Int)

    /**
     * Every picture in the chapters at [paths], each once, where it first comes, in reading order. [text] gives a
     * chapter's page, [sizeOf] a picture's size (null if it can't be read: left out). Spacers, rules and tiny
     * marks are left out too.
     */
    fun find(paths: List<String>, text: (String) -> String?, sizeOf: (String) -> Pair<Int, Int>?): List<Found> {
        val seen = HashSet<String>()
        val out = ArrayList<Found>()
        paths.forEachIndexed { i, path ->
            val page = text(path) ?: return@forEachIndexed
            for (p in PagePlacement.pictures(page, path)) {
                if (!seen.add(p)) continue
                val (w, h) = sizeOf(p) ?: continue
                if (tiny(w, h)) continue
                out += Found(p, i, w, h)
            }
        }
        return out
    }

    /** Big enough to be a picture in its own right (a map, a plate, a figure), not a chapter's little mark or a divider. */
    fun large(w: Int, h: Int): Boolean = maxOf(w, h) >= 300 && w.toLong() * h >= 60_000

    /** Too small to be a picture worth showing: a spacer, a rule, a dot. */
    fun tiny(w: Int, h: Int): Boolean = minOf(w, h) < 8 || (w < 48 && h < 48)
}
