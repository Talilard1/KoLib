package dev.kobolibrary.app.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import dev.kobolibrary.app.convert.BookFormats
import dev.kobolibrary.app.saf.SafTree

/** A book file found in a folder, to add to the library. */
data class FoundBook(val uri: Uri, val name: String, val size: Long)

/** Looking through a folder for books to add: while it's looked through, what was found, and adding them. */
sealed interface FolderScan {
    /** The folder's name, as people know it: "Download/Books". */
    val folder: String

    data class Looking(override val folder: String, val found: Int) : FolderScan
    data class Found(override val folder: String, val books: List<FoundBook>) : FolderScan
    data class Adding(override val folder: String, val count: Int) : FolderScan
    data class Failed(override val folder: String) : FolderScan
}

/** Finding the books in a folder chosen in Android's folder picker. */
object BookFolders {

    /** [tree]'s name as people know it: the path on the device ("Download/Books"), or the folder's own name. */
    fun nameOf(resolver: ContentResolver, tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        // Android's own storage names folders "primary:Download/Books".
        val path = id.substringAfter(':', "").trim('/')
        if (path.isNotEmpty()) return path
        return runCatching { SafTree(resolver, tree).let { it.stat(it.rootDocId)?.name } }.getOrNull()
            ?: id.ifEmpty { tree.lastPathSegment.orEmpty() }
    }

    /**
     * A file in a folder of books that's a book: web pages and notes there are usually something else
     * (a Calibre library's, a download's), so only book files and plain text are taken, and not a
     * read-me or a licence.
     */
    fun looksLikeABook(name: String): Boolean {
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        if (!BookFormats.isBook(lower) || ext in setOf("html", "htm", "xhtml", "md", "markdown")) return false
        return !Regex("""^(read ?me|licen[cs]e|copying|changelog|changes|notes?)\b""").containsMatchIn(lower)
    }

    /**
     * Every book file in [tree] and the folders inside it, apart from hidden ones; [onCount] is told how
     * many have been found so far, as it goes.
     */
    fun find(resolver: ContentResolver, tree: Uri, onCount: (Int) -> Unit = {}): List<FoundBook> {
        val saf = SafTree(resolver, tree)
        val out = ArrayList<FoundBook>()
        val folders = ArrayDeque<Pair<String, Int>>()
        folders.add(saf.rootDocId to 0)
        var first = true
        while (folders.isNotEmpty()) {
            val (id, depth) = folders.removeFirst()
            // The chosen folder must be readable; one inside it that isn't is passed over.
            val inside = if (first) saf.children(id) else runCatching { saf.children(id) }.getOrDefault(emptyList())
            first = false
            for (e in inside.sortedBy { it.name.lowercase() }) {
                when {
                    e.name.startsWith(".") -> Unit
                    e.isDirectory -> if (depth < 12) folders.add(e.docId to depth + 1)
                    looksLikeABook(e.name) && e.size != 0L -> {
                        out += FoundBook(saf.uriOf(e.docId), e.name, e.size)
                        onCount(out.size)
                    }
                }
            }
        }
        return out
    }
}
