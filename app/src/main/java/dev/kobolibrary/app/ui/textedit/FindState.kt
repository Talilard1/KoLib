package dev.kobolibrary.app.ui.textedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kobolibrary.app.epub.FindReplace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class EntryStatus { PENDING, REPLACED, REMOVED }

/** One place found, and what's become of it. */
data class FindEntry(val id: Int, val match: FindReplace.Match, val status: EntryStatus = EntryStatus.PENDING, val newText: String? = null)

/**
 * Find & replace across the book: the list of every place found (each with the text around it),
 * replacing them one by one from the top or all at once, and taking places off the list so
 * "Replace all" leaves them alone.
 */
class FindState(private val owner: TextEditState) {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    var replacement by mutableStateOf("")
    var matchCase by mutableStateOf(false)
    var wholeWords by mutableStateOf(false)
    var nameMode by mutableStateOf(false)
    var pattern by mutableStateOf(false)
    var thisChapterOnly by mutableStateOf(false)
    var entries by mutableStateOf<List<FindEntry>>(emptyList())
        private set
    var currentId by mutableStateOf<Int?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** What the last search was for (null before any). */
    var searchedFor by mutableStateOf<FindReplace.Options?>(null)
        private set
    /** Entries just taken off the list, so it can be undone. */
    var lastRemoved by mutableStateOf<List<Int>?>(null)
        private set

    private var searchedVersion = -1
    private var nextId = 0
    private var job: Job? = null
    private val scope: CoroutineScope get() = owner.scopeForFind

    val visible: List<FindEntry> get() = entries.filter { it.status != EntryStatus.REMOVED }
    val pendingCount: Int get() = entries.count { it.status == EntryStatus.PENDING }
    val replacedCount: Int get() = entries.count { it.status == EntryStatus.REPLACED }
    val removedCount: Int get() = entries.count { it.status == EntryStatus.REMOVED }
    val flaggedCount: Int get() = entries.count { it.status == EntryStatus.PENDING && it.match.flag == FindReplace.Flag.NOT_NAME }
    val stale: Boolean get() = searchedFor != null && searchedFor != options()

    fun options() = FindReplace.Options(query, matchCase, wholeWords, nameMode, pattern)

    fun search(keepRemoved: Boolean = false) {
        job?.cancel()
        job = scope.launch { searchNow(keepRemoved) }
    }

    private suspend fun searchNow(keepRemoved: Boolean) {
        owner.flush()
        val opts = options()
        if (opts.query.isEmpty()) {
            entries = emptyList()
            searchedFor = null
            owner.clearMatches()
            return
        }
        searching = true
        error = null
        val only = if (thisChapterOnly) setOfNotNull(owner.currentPath) else null
        val result = withContext(Dispatchers.Default) { runCatching { FindReplace.search(owner.edit, owner.structure, opts, only) } }
        searching = false
        result.exceptionOrNull()?.let { e ->
            error = (e as? FindReplace.BadPattern)?.message ?: e.message ?: "That search isn't valid."
            return
        }
        val matches = result.getOrThrow()
        val removed = if (keepRemoved) entries.filter { it.status == EntryStatus.REMOVED }.map { it.match.contextKey }.toSet() else emptySet()
        val wasCurrent = entries.firstOrNull { it.id == currentId }?.match?.contextKey
        entries = matches.map { m -> FindEntry(nextId++, m, if (m.contextKey in removed) EntryStatus.REMOVED else EntryStatus.PENDING) }
        searchedFor = opts
        searchedVersion = owner.edit.version
        currentId = wasCurrent?.let { k -> entries.firstOrNull { it.match.contextKey == k }?.id }
        lastRemoved = null
        if (currentId == null) owner.clearMatches()
    }

    /** The book changed by something other than this list (undo, a clean-up): look again. */
    fun bookChanged() {
        if (!open || searchedFor == null || owner.edit.version == searchedVersion) return
        search(keepRemoved = true)
    }

    private suspend fun ensureFresh() {
        owner.flush()
        if (owner.edit.version != searchedVersion || searchedFor != options()) searchNow(keepRemoved = searchedFor == options())
    }

    // ---- going through the list ---------------------------------------------------------------

    private fun occurrence(e: FindEntry): Int =
        entries.takeWhile { it.id != e.id }.count { it.match.path == e.match.path && it.match.text == e.match.text }

    private fun json(e: FindEntry) = JSONObject().apply {
        put("path", e.match.path)
        put("segment", e.match.segment)
        put("offset", e.match.start)
        put("length", e.match.end - e.match.start)
        put("text", e.match.text)
        put("occurrence", occurrence(e))
    }

    fun select(e: FindEntry, reload: Boolean = false) {
        currentId = e.id
        val all = JSONArray()
        entries.filter { it.status == EntryStatus.PENDING && it.match.path == e.match.path && it.id != e.id }.forEach { all.put(json(it)) }
        owner.showMatch(json(e), all, reload)
    }

    private fun nextPendingAfter(id: Int?): FindEntry? {
        val list = entries
        val start = id?.let { i -> list.indexOfFirst { it.id == i } } ?: -1
        return list.drop(start + 1).firstOrNull { it.status == EntryStatus.PENDING }
            ?: list.take(maxOf(0, start)).firstOrNull { it.status == EntryStatus.PENDING }
    }

    fun skip() {
        nextPendingAfter(currentId)?.let { select(it) }
    }

    /** Replaces the current place and moves on to the next. The first press just goes to the first one. */
    fun replaceCurrent() {
        job?.cancel()
        job = scope.launch {
            ensureFresh()
            val cur = entries.firstOrNull { it.id == currentId && it.status == EntryStatus.PENDING }
            if (cur == null) {
                nextPendingAfter(currentId)?.let { select(it) }
                return@launch
            }
            val opts = searchedFor ?: return@launch
            val (changes, skipped) = FindReplace.replace(owner.edit, listOf(cur.match), replacement, opts)
            if (skipped > 0 || changes.isEmpty()) {
                owner.message = "The text there has changed, so the search was run again."
                searchNow(keepRemoved = true)
                return@launch
            }
            val newText = FindReplace.replacementFor(cur.match, replacement, opts)
            if (owner.edit.apply("Replaced “${cur.match.text}” with “$newText”", changes)) owner.bump()
            val shifted = FindReplace.shiftAfter(entries.map { it.match }, cur.match, newText)
            entries = entries.mapIndexed { i, e -> if (e.id == cur.id) e.copy(status = EntryStatus.REPLACED, newText = newText) else e.copy(match = shifted[i]) }
            searchedVersion = owner.edit.version
            val next = nextPendingAfter(cur.id)
            if (next != null) select(next, reload = true) else {
                currentId = null
                owner.refreshPage(null)
                owner.message = "That was the last one."
            }
        }
    }

    /** Replaces every place still on the list, as one change. */
    fun replaceAll() {
        job?.cancel()
        job = scope.launch {
            ensureFresh()
            val todo = entries.filter { it.status == EntryStatus.PENDING }
            if (todo.isEmpty()) return@launch
            val opts = searchedFor ?: return@launch
            val (changes, skipped) = withContext(Dispatchers.Default) { FindReplace.replace(owner.edit, todo.map { it.match }, replacement, opts) }
            if (changes.isEmpty()) return@launch
            val done = todo.size - skipped
            if (owner.edit.apply("Replaced ${if (done == 1) "1 “${opts.query}”" else "$done × “${opts.query}”"} with “$replacement”", changes)) owner.bump()
            val ids = todo.map { it.id }.toSet()
            entries = entries.map { e -> if (e.id in ids) e.copy(status = EntryStatus.REPLACED, newText = FindReplace.replacementFor(e.match, replacement, opts)) else e }
            searchedVersion = -1 // positions moved; the next action looks again
            currentId = null
            owner.refreshPage(null)
            owner.message = "Replaced $done." + if (skipped > 0) " $skipped had changed and were skipped." else ""
        }
    }

    // ---- taking places off the list -----------------------------------------------------------

    fun remove(e: FindEntry) {
        entries = entries.map { if (it.id == e.id) it.copy(status = EntryStatus.REMOVED) else it }
        lastRemoved = listOf(e.id)
        if (currentId == e.id) currentId = null
    }

    fun removeFlagged() {
        val ids = entries.filter { it.status == EntryStatus.PENDING && it.match.flag == FindReplace.Flag.NOT_NAME }.map { it.id }
        if (ids.isEmpty()) return
        entries = entries.map { if (it.id in ids) it.copy(status = EntryStatus.REMOVED) else it }
        lastRemoved = ids
        if (currentId in ids) currentId = null
    }

    fun undoRemove() {
        val ids = lastRemoved?.toSet() ?: return
        entries = entries.map { if (it.id in ids && it.status == EntryStatus.REMOVED) it.copy(status = EntryStatus.PENDING) else it }
        lastRemoved = null
    }

    fun restoreAllRemoved() {
        entries = entries.map { if (it.status == EntryStatus.REMOVED) it.copy(status = EntryStatus.PENDING) else it }
        lastRemoved = null
    }

    fun close() {
        open = false
        owner.clearMatches()
    }

    /** Waits for a search or replace to finish (for tests). */
    internal fun awaitWork() = kotlinx.coroutines.runBlocking { job?.join() }
}
