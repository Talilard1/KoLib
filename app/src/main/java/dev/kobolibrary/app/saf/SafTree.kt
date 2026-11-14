package dev.kobolibrary.app.saf

import dev.kobolibrary.app.tr
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.kobolibrary.app.log.AppLog
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SyncFailedException

/** One file or folder inside a drive Android has given us access to. */
data class DocEntry(
    val docId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
}

/**
 * Thin wrapper over Android's Storage Access Framework for one granted folder tree
 * (here: the root of the Kobo's USB drive).
 *
 * Android's built-in storage provider names documents "VOLUME-ID:relative/path", which lets us
 * jump straight to a known path. Anything unexpected falls back to walking the folders by name.
 */
class SafTree(private val resolver: ContentResolver, val treeUri: Uri) {

    val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    /** True once a direct "VOLUME:path" lookup has worked; reported in the diagnostics. */
    @Volatile
    var directIdsWork: Boolean? = null
        private set

    fun uriOf(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    fun stat(docId: String): DocEntry? = try {
        resolver.query(uriOf(docId), PROJECTION, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.toEntry() else null
        }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    /** Throws if the drive has gone away, so callers can tell "missing" from "unplugged". */
    fun children(docId: String): List<DocEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val cursor = resolver.query(uri, PROJECTION, null, null, null)
            ?: throw IOException(tr("The drive didn't respond when listing {0}", docId))
        return cursor.use { c ->
            buildList { while (c.moveToNext()) add(c.toEntry()) }
        }
    }

    fun child(parentDocId: String, name: String): DocEntry? {
        guessChildId(parentDocId, name)?.let { guess ->
            val found = stat(guess)
            if (found != null) {
                directIdsWork = true
                return found
            }
        }
        val kids = children(parentDocId)
        return kids.firstOrNull { it.name == name }
            ?: kids.firstOrNull { it.name.equals(name, ignoreCase = true) } // FAT32 ignores case
    }

    /** Resolves a path such as ".kobo/KoboReader.sqlite" relative to the tree root. */
    fun resolve(path: String): DocEntry? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return stat(rootDocId)
        guessPathId(parts)?.let { guess ->
            val found = stat(guess)
            if (found != null) {
                directIdsWork = true
                return found
            }
        }
        var current = rootDocId
        var entry: DocEntry? = null
        for (part in parts) {
            entry = child(current, part) ?: return null
            current = entry.docId
        }
        return entry
    }

    /** Opens the file at [path] for reading anywhere in it (null if it isn't there). */
    fun openFileDescriptor(path: String): ParcelFileDescriptor? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        guessPathId(parts)?.let { guess ->
            try {
                resolver.openFileDescriptor(uriOf(guess), "r")?.let { return it }
            } catch (_: FileNotFoundException) {
            } catch (_: IllegalArgumentException) {
            } catch (_: SecurityException) {
            }
        }
        val entry = resolve(path) ?: return null
        return resolver.openFileDescriptor(uriOf(entry.docId), "r")
    }

    fun openInput(docId: String): InputStream =
        resolver.openInputStream(uriOf(docId)) ?: throw IOException(tr("Could not open {0} for reading", docId))

    fun openOutput(docId: String, mode: String = "wt"): OutputStream =
        resolver.openOutputStream(uriOf(docId), mode) ?: throw IOException(tr("Could not open {0} for writing", docId))

    /** Creates a file and returns its document id (the provider may adjust the name if it already exists). */
    fun createFile(parentDocId: String, mimeType: String, name: String): String? =
        DocumentsContract.createDocument(resolver, uriOf(parentDocId), mimeType, name)
            ?.let { DocumentsContract.getDocumentId(it) }

    fun delete(docId: String): Boolean = DocumentsContract.deleteDocument(resolver, uriOf(docId))

    /**
     * Renames a file in place and returns its new document id, or null if the drive won't rename it
     * or gives it a different name (it does that when [newName] is taken).
     */
    fun rename(docId: String, newName: String): String? {
        val renamed = try {
            DocumentsContract.renameDocument(resolver, uriOf(docId), newName)
        } catch (e: UnsupportedOperationException) {
            return null
        } ?: return null
        val newId = DocumentsContract.getDocumentId(renamed)
        val name = stat(newId)?.name
        return newId.takeIf { name == newName }
    }

    /** The folder at [path] (like "Herodotus/Notes"), made if it isn't there. Returns its document id. */
    fun ensureFolder(path: String): String {
        var parent = rootDocId
        for (name in path.split('/').filter { it.isNotEmpty() }) {
            val existing = child(parent, name)
            parent = when {
                existing == null -> createFile(parent, Document.MIME_TYPE_DIR, name)
                    ?: throw IOException(tr("Couldn't create the folder {0} on the drive", name))
                existing.isDirectory -> existing.docId
                else -> throw IOException(tr("Can't create the folder {0}, because a file with that name exists", name))
            }
        }
        return parent
    }

    /**
     * Writes [source] to [path], replacing any file already there. The size is checked afterwards.
     * Returns true if a file was replaced.
     */
    fun writeFile(path: String, source: File, onProgress: (copied: Long) -> Unit = {}): Boolean {
        val folder = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val parent = if (folder.isEmpty()) rootDocId else ensureFolder(folder)
        val existing = child(parent, name)?.takeIf { !it.isDirectory }
        val id = existing?.docId
            ?: createFile(parent, "application/octet-stream", name)
            ?: throw IOException(tr("Android couldn't create {0} on the drive", name))
        // Some drives quietly rename a new file; the Kobo would then know it by the wrong name.
        if (existing == null) {
            val made = stat(id)?.name
            if (made != null && made != name) {
                delete(id)
                throw IOException(tr("The drive renamed {0} to {1}", name, made))
            }
        }
        try {
            val pfd = resolver.openFileDescriptor(uriOf(id), "wt") ?: throw IOException(tr("Could not open {0} for writing", name))
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        onProgress(copied)
                    }
                }
                onTheDrive(out.fd, name)
            }
        } catch (e: IOException) {
            // A half-written book is worse than none.
            if (existing == null) runCatching { delete(id) }
            throw e
        }
        val size = stat(id)?.size
        if (size != null && size > 0 && size != source.length()) {
            throw IOException(tr("{0} was written, but its size on the drive is wrong ({1} instead of {2} bytes)", name, size, source.length()))
        }
        return existing != null
    }

    /**
     * Waits until what was written to [fd] is on the drive itself, not just in Android's memory.
     * Otherwise Android writes it out when the drive is ejected, which makes ejecting slow, and
     * unplugging before it's done loses it.
     */
    private fun onTheDrive(fd: FileDescriptor, name: String) {
        try {
            fd.sync()
        } catch (e: SyncFailedException) {
            AppLog.w("Drive", "Couldn't make sure $name is on the drive yet: ${e.message}")
        }
    }

    /** Deletes the file at [path] if it's there. Returns true if something was deleted. */
    fun deleteIfExists(path: String): Boolean {
        val entry = resolve(path) ?: return false
        return delete(entry.docId)
    }

    private fun guessChildId(parentDocId: String, name: String): String? {
        if (!parentDocId.contains(':')) return null
        return if (parentDocId.endsWith(":")) parentDocId + name else "$parentDocId/$name"
    }

    private fun guessPathId(parts: List<String>): String? = guessChildId(rootDocId, parts.joinToString("/"))

    private fun Cursor.toEntry(): DocEntry {
        fun str(col: String) = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
        fun lng(col: String) = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) 0L else getLong(it) } ?: 0L
        return DocEntry(
            docId = str(Document.COLUMN_DOCUMENT_ID) ?: "",
            name = str(Document.COLUMN_DISPLAY_NAME) ?: "",
            mimeType = str(Document.COLUMN_MIME_TYPE) ?: "application/octet-stream",
            size = lng(Document.COLUMN_SIZE),
            lastModified = lng(Document.COLUMN_LAST_MODIFIED),
        )
    }

    companion object {
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        val ContentResolver.treeDocIdOrNull: (Uri) -> String?
            get() = { uri -> runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() }
    }
}
