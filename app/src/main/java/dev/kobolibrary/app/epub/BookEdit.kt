package dev.kobolibrary.app.epub

import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * A book being edited: its file as last saved, plus changes not saved yet, with undo and redo.
 *
 * Every change is a whole new copy of the files it touches (the others are shared), so undo is exact
 * and cheap. Saving writes a new file, checks it, and only then replaces the book; changes stay
 * undoable after saving.
 */
class BookEdit(val file: File) : BookFiles, Closeable {

    /** Changed files (null: deleted), in the order new files were added. */
    class State(val files: Map<String, ByteArray?>) {
        companion object {
            val EMPTY = State(emptyMap())
        }
    }

    class Step(
        val label: String,
        val state: State,
        /** Files this step changed, compared with the step before. */
        val touched: Set<String>,
        /** Steps with the same key, made close together (typing), become one undo. */
        val coalesceKey: String?,
        val madeAt: Long,
    )

    private var base: EpubFile = EpubFile.open(file)
    private val history = ArrayList<Step>().apply { add(Step("", State.EMPTY, emptySet(), null, 0)) }
    private var at = 0
    private var savedAt = 0

    /** Bumped on every change, undo or redo, so screens know to look again. */
    var version = 0
        private set

    override val opfPath: String get() = base.opfPath

    val state: State get() = history[at].state
    val canUndo: Boolean get() = at > 0
    val canRedo: Boolean get() = at < history.size - 1
    val undoLabel: String? get() = if (canUndo) history[at].label else null
    val redoLabel: String? get() = if (canRedo) history[at + 1].label else null
    /** There are changes that aren't in the saved file. */
    val dirty: Boolean get() = at != savedAt
    /** How many changes have been made (for messages). */
    val changeCount: Int get() = at

    override fun bytes(path: String): ByteArray? {
        val files = state.files
        return if (files.containsKey(path)) files[path] else base.readBytes(path)
    }

    override fun has(path: String): Boolean {
        val files = state.files
        return if (files.containsKey(path)) files[path] != null else base.has(path)
    }

    override fun names(): List<String> {
        val files = state.files
        val existing = base.entryNames()
        val baseSet = existing.toHashSet()
        return existing.filter { !files.containsKey(it) || files[it] != null } +
            files.filter { (k, v) -> v != null && k !in baseSet }.keys
    }

    private var cachedStructure: Pair<Int, BookStructure>? = null

    /** The reading order and contents as they are now (edits included). */
    val structure: BookStructure
        get() {
            cachedStructure?.let { (v, s) -> if (v == version) return s }
            return BookStructure(this).also { cachedStructure = version to it }
        }

    /**
     * Records a change: new contents for some files (null deletes one). Does nothing if nothing
     * actually changes. With [coalesceKey], a change made soon after another with the same key joins it.
     */
    fun apply(label: String, changes: Map<String, ByteArray?>, coalesceKey: String? = null, now: Long = System.currentTimeMillis()): Boolean {
        val real = changes.filter { (path, bytes) ->
            val current = bytes(path)
            if (bytes == null) current != null else current == null || !current.contentEquals(bytes)
        }
        if (real.isEmpty()) return false
        val next = LinkedHashMap(state.files)
        for ((path, bytes) in real) {
            // Back to exactly what's in the saved file: no longer a change.
            val inBase = base.readBytes(path)
            if ((bytes == null && inBase == null) || (bytes != null && inBase != null && inBase.contentEquals(bytes))) next.remove(path)
            else next[path] = bytes
        }
        // A new change throws away anything that was undone.
        while (history.size > at + 1) history.removeAt(history.size - 1)
        if (savedAt > at) savedAt = -1
        val top = history[at]
        if (coalesceKey != null && top.coalesceKey == coalesceKey && at > 0 && at != savedAt && now - top.madeAt < COALESCE_MS) {
            history[at] = Step(top.label, State(next), top.touched + real.keys, coalesceKey, now)
        } else {
            history += Step(label, State(next), real.keys, coalesceKey, now)
            at++
            trim()
        }
        version++
        return true
    }

    private fun trim() {
        while (history.size > MAX_STEPS) {
            history.removeAt(0)
            at--
            savedAt = if (savedAt > 0) savedAt - 1 else -1
        }
    }

    /** Undoes the last change; returns the files it touched. */
    fun undo(): Set<String> {
        if (!canUndo) return emptySet()
        val touched = history[at].touched
        at--
        version++
        return touched
    }

    fun redo(): Set<String> {
        if (!canRedo) return emptySet()
        at++
        version++
        return history[at].touched
    }

    /** Forgets every change not saved yet. */
    fun discardUnsaved() {
        if (savedAt >= 0) {
            at = savedAt
        } else {
            history.clear()
            history += Step("", State.EMPTY, emptySet(), null, 0)
            at = 0
            savedAt = 0
        }
        while (history.size > at + 1) history.removeAt(history.size - 1)
        version++
    }

    sealed interface SaveResult {
        data object NothingToSave : SaveResult
        data class Saved(val problemsBefore: Int) : SaveResult
        /** The new file would have problems the book didn't have; it wasn't saved. */
        data class Refused(val problems: List<BookCheck.Problem>) : SaveResult
    }

    /**
     * Writes the changes into the book. The first time, the book as it was is kept in [original].
     * The new file is checked, and the book is only replaced if the changes didn't damage it.
     */
    fun save(original: File?): SaveResult {
        if (!dirty) return SaveResult.NothingToSave
        val before = BookCheck.run(EpubFileFiles(base))
        val pending = state.files
        val existing = base.entryNames().toHashSet()
        val replace = pending.filter { (k, v) -> v != null && k in existing }.mapValues { it.value!! }
        val add = LinkedHashMap<String, ByteArray>().apply { pending.forEach { (k, v) -> if (v != null && k !in existing) put(k, v) } }
        val remove = pending.filter { it.value == null }.keys

        val tmp = File(file.parentFile, file.name + ".saving")
        tmp.delete()
        try {
            EpubWriter.rewrite(file, tmp, replace = replace, add = add, remove = remove)
            val after = EpubFile.open(tmp).use { BookCheck.run(EpubFileFiles(it)) }
            val known = before.map { it.key }.toSet()
            val fresh = after.filter { it.key !in known }
            if (fresh.isNotEmpty()) return SaveResult.Refused(fresh)

            if (original != null && !original.exists()) {
                val part = File(original.path + ".part")
                file.copyTo(part, overwrite = true)
                if (!part.renameTo(original)) throw IOException("Couldn't save a copy of the original book")
            }

            // Every version in the undo history, written relative to the new file.
            val paths = history.flatMap { it.state.files.keys }.toSet()
            val oldBase = paths.associateWith { base.readBytes(it) }
            fun valueIn(s: State, path: String): ByteArray? = if (s.files.containsKey(path)) s.files[path] else oldBase[path]
            val savedState = state

            base.close()
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("Couldn't replace ${file.name}")
            }
            base = EpubFile.open(file)

            for (i in history.indices) {
                val s = history[i].state
                val rebased = LinkedHashMap<String, ByteArray?>()
                for (p in paths) {
                    val mine = valueIn(s, p)
                    val now = valueIn(savedState, p)
                    val same = (mine == null && now == null) || (mine != null && now != null && (mine === now || mine.contentEquals(now)))
                    if (!same) rebased[p] = mine
                }
                history[i] = Step(history[i].label, State(rebased), history[i].touched, null, history[i].madeAt)
            }
            savedAt = at
            version++
            return SaveResult.Saved(before.size)
        } finally {
            tmp.delete()
        }
    }

    /** Opens the book again after it was replaced outside this edit (reverted, for instance). */
    fun reload() {
        base.close()
        base = EpubFile.open(file)
        history.clear()
        history += Step("", State.EMPTY, emptySet(), null, 0)
        at = 0
        savedAt = 0
        version++
    }

    override fun close() = base.close()

    companion object {
        const val MAX_STEPS = 200
        const val COALESCE_MS = 4_000L
    }
}

/** Checks a book the way a strict reader would, so changes that damage it are caught before saving. */
object BookCheck {

    data class Problem(val path: String, val message: String, val line: Int? = null) {
        /** Problems are compared without line numbers, which move when text is edited. */
        val key: String get() = "$path\u0000$message"
        override fun toString() = if (line != null) "$path, line $line: $message" else "$path: $message"
    }

    fun run(files: BookFiles): List<Problem> {
        val out = ArrayList<Problem>()
        val structure = try {
            BookStructure(files)
        } catch (e: Exception) {
            return listOf(Problem(files.opfPath, e.message ?: "The book's package file (OPF) can't be read"))
        }
        val opf = structure.opf
        XmlCheck.check(structure.opfText)?.let { out += Problem(files.opfPath, it.message, it.line) }
        if (opf.spineRefs().isEmpty()) out += Problem(files.opfPath, "The reading order is empty")
        val names = files.names().toHashSet()
        for (item in opf.items) {
            val path = structure.pathOf(item)
            if (path !in names) {
                out += Problem(path, "The book lists this file, but it's missing")
                continue
            }
            val checkXml = item.mediaType == "application/xhtml+xml" || item.mediaType == "application/x-dtbncx+xml" ||
                item.mediaType == "image/svg+xml"
            if (checkXml) {
                val text = files.text(path) ?: continue
                XmlCheck.check(text, htmlEntities = XmlCheck.hasXhtmlDoctype(text))?.let { out += Problem(path, it.message, it.line) }
            }
        }
        for (ref in opf.spineRefs()) {
            if (opf.item(ref.idref) == null) out += Problem(files.opfPath, "The reading order includes “${ref.idref}”, which isn't in the book")
        }
        return out
    }
}
