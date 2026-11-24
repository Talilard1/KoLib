package dev.kolib.app.comic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Where a page's picture comes from: the comic's own file (its page [InComic.index]), or a picture added to it. */
sealed interface PageSource {
    data class InComic(val index: Int) : PageSource
    data class Picture(val file: File) : PageSource
}

/** The part of a picture kept, as fractions of it (once it's halved and turned): left, top, right and bottom. */
data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** Nothing cut off. */
    val whole: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** The same part of the picture once it's turned a quarter, clockwise ([by] 1) or the other way ([by] -1). */
    fun turned(by: Int): Crop = when (((by % 4) + 4) % 4) {
        1 -> Crop(1f - bottom, left, 1f - top, right)
        2 -> Crop(1f - right, 1f - bottom, 1f - left, 1f - top)
        3 -> Crop(top, 1f - right, bottom, 1f - left)
        else -> this
    }

    companion object {
        val WHOLE = Crop(0f, 0f, 1f, 1f)
    }
}

/** A two-page spread's left or right half. */
enum class Half { LEFT, RIGHT }

/**
 * A page of a comic being edited: its picture, and what's done to it (halved, then turned a [turns] of quarters
 * clockwise, then cropped). A new comic starts at it when the comic's split there ([startsComic]).
 */
data class DraftPage(
    val key: Long,
    val source: PageSource,
    val half: Half? = null,
    val turns: Int = 0,
    val crop: Crop? = null,
    val startsComic: Boolean = false,
) {
    /** Its picture is used just as it is. */
    val asIs: Boolean get() = half == null && turns % 4 == 0 && (crop == null || crop.whole)
}

/** A comic's pages, being edited: each change can be undone, and redone. */
class ComicDraft(start: List<DraftPage>) {
    private var nextKey = (start.maxOfOrNull { it.key } ?: 0L) + 1
    var pages by mutableStateOf(start)
        private set
    private val undos = ArrayDeque<List<DraftPage>>()
    private val redos = ArrayDeque<List<DraftPage>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun change(to: List<DraftPage>) {
        if (to == pages) return
        undos.addLast(pages)
        if (undos.size > 300) undos.removeFirst()
        redos.clear()
        pages = to
        sync()
    }

    private fun sync() {
        canUndo = undos.isNotEmpty()
        canRedo = redos.isNotEmpty()
    }

    fun undo() {
        val before = undos.removeLastOrNull() ?: return
        redos.addLast(pages)
        pages = before
        sync()
    }

    fun redo() {
        val after = redos.removeLastOrNull() ?: return
        undos.addLast(pages)
        pages = after
        sync()
    }

    private fun copyOf(p: DraftPage) = p.copy(key = nextKey++, startsComic = false)

    /** The page at [from] moved so it's at [to]. */
    fun move(from: Int, to: Int) {
        if (from !in pages.indices || to !in pages.indices || from == to) return
        val list = pages.toMutableList()
        list.add(to, list.removeAt(from))
        change(list)
    }

    /** The pages [keys], in their order, moved so the first of them is at [to] (counted among the rest). */
    fun moveTo(keys: Set<Long>, to: Int) {
        val moving = pages.filter { it.key in keys }
        if (moving.isEmpty()) return
        val rest = pages.filter { it.key !in keys }
        val at = to.coerceIn(0, rest.size)
        change(rest.take(at) + moving + rest.drop(at))
    }

    fun delete(keys: Set<Long>) = change(pages.filter { it.key !in keys })

    /** Each of [keys] turned a quarter clockwise ([by] 1) or the other way ([by] -1), its crop with it. */
    fun turn(keys: Set<Long>, by: Int) = change(pages.map { p -> if (p.key in keys) p.copy(turns = ((p.turns + by) % 4 + 4) % 4, crop = p.crop?.turned(by)) else p })

    fun crop(key: Long, crop: Crop?) = change(pages.map { p -> if (p.key == key) p.copy(crop = crop?.takeIf { !it.whole }) else p })

    /**
     * Each of [keys] that's a two-page spread made two pages, its halves in reading order (right first, read right to
     * left). It's split as it is in the comic: not turned or cropped. A page that's a half already stays as it is.
     */
    fun splitSpreads(keys: Set<Long>, rightToLeft: Boolean) = change(
        pages.flatMap { p ->
            if (p.key !in keys || p.half != null) listOf(p)
            else {
                val first = if (rightToLeft) Half.RIGHT else Half.LEFT
                val second = if (rightToLeft) Half.LEFT else Half.RIGHT
                listOf(p.copy(half = first, turns = 0, crop = null), p.copy(key = nextKey++, half = second, turns = 0, crop = null, startsComic = false))
            }
        },
    )

    /** A copy of each of [keys], just after it. */
    fun duplicate(keys: Set<Long>) = change(pages.flatMap { p -> if (p.key in keys) listOf(p, copyOf(p)) else listOf(p) })

    /** The page [key] made the first, the comic's cover. */
    fun toFront(key: Long) {
        val i = pages.indexOfFirst { it.key == key }
        if (i > 0) change(listOf(pages[i].copy(startsComic = false)) + pages.filterIndexed { k, _ -> k != i })
    }

    /** Pictures [files] put in at [at] (or at the end), in their order. */
    fun insert(at: Int, files: List<File>) {
        if (files.isEmpty()) return
        val i = at.coerceIn(0, pages.size)
        change(pages.take(i) + files.map { DraftPage(nextKey++, PageSource.Picture(it)) } + pages.drop(i))
    }

    /** A new comic starts at page [key], or no longer does. The first page always starts the first. */
    fun toggleStart(key: Long) {
        if (pages.firstOrNull()?.key == key) return
        change(pages.map { p -> if (p.key == key) p.copy(startsComic = !p.startsComic) else p })
    }

    /** The comics the pages make: one, or one for each part where a new one starts. */
    fun parts(): List<List<DraftPage>> {
        val out = ArrayList<MutableList<DraftPage>>()
        pages.forEachIndexed { i, p -> if (i == 0 || p.startsComic) out += mutableListOf(p) else out.last() += p }
        return out
    }
}
