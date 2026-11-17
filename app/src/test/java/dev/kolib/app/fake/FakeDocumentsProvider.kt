package dev.kolib.app.fake

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

/**
 * Stands in for Android's storage provider: exposes a folder on disk as if it were the Kobo's USB drive,
 * using the same document URIs and "VOLUME-ID:relative/path" document ids as the real one.
 *
 * It is a plain ContentProvider rather than a DocumentsProvider because Robolectric calls the old
 * query() signature, which DocumentsProvider refuses. The URIs the app sends are exactly the same.
 */
class FakeDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "dev.kolib.test.documents"
        const val VOLUME = "1A2B-3C4D"

        // Values of DocumentsContract's hidden constants, used by createDocument()/deleteDocument().
        private const val METHOD_CREATE_DOCUMENT = "android:createDocument"
        private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
        private const val METHOD_RENAME_DOCUMENT = "android:renameDocument"
        private const val EXTRA_URI = "uri"

        private val DEFAULT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )

        @Volatile
        lateinit var root: File

        /** Registers the provider for [rootDir] and returns the tree URI the folder picker would have returned. */
        fun install(rootDir: File): Uri {
            root = rootDir
            Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(AUTHORITY)
            return DocumentsContract.buildTreeDocumentUri(AUTHORITY, "$VOLUME:")
        }
    }

    override fun onCreate(): Boolean = true

    private fun fileFor(docId: String): File {
        if (!docId.startsWith("$VOLUME:")) throw FileNotFoundException("Unknown document $docId")
        val rel = docId.removePrefix("$VOLUME:")
        return if (rel.isEmpty()) root else File(root, rel)
    }

    private fun docIdFor(file: File): String = "$VOLUME:" + file.relativeTo(root).path.replace(File.separatorChar, '/')

    /** Same check the real provider makes: the document must sit inside the granted tree. */
    private fun checkInTree(uri: Uri, docId: String) {
        if (!DocumentsContract.isTreeUri(uri)) return
        val parent = fileFor(DocumentsContract.getTreeDocumentId(uri)).canonicalPath
        val child = fileFor(docId).canonicalPath
        if (child != parent && !child.startsWith(parent + File.separator)) throw SecurityException("$docId is outside the tree")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val docId = DocumentsContract.getDocumentId(uri)
        checkInTree(uri, docId)
        val file = fileFor(docId)
        val children = uri.lastPathSegment == "children"
        // Nothing to list: no cursor is made, so none is left open.
        if (children && !file.isDirectory) return null
        if (!children && !file.exists()) return null // the real provider returns null for a missing document
        val cursor = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        if (children) file.listFiles()?.sortedBy { it.name }?.forEach { addRow(cursor, it) } else addRow(cursor, file)
        return cursor
    }

    override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor? =
        query(uri, projection, null, null, null)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val docId = DocumentsContract.getDocumentId(uri)
        checkInTree(uri, docId)
        val f = fileFor(docId)
        if (!f.exists() && !mode.contains('w')) throw FileNotFoundException("Missing file for $docId")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode))
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        @Suppress("DEPRECATION")
        val uri = extras?.getParcelable<Uri>(EXTRA_URI) ?: throw IllegalArgumentException("No document uri")
        val docId = DocumentsContract.getDocumentId(uri)
        checkInTree(uri, docId)
        return when (method) {
            METHOD_CREATE_DOCUMENT -> {
                val mime = extras.getString(Document.COLUMN_MIME_TYPE)
                val name = extras.getString(Document.COLUMN_DISPLAY_NAME) ?: throw IllegalArgumentException("No name")
                val f = File(fileFor(docId), name)
                if (mime == Document.MIME_TYPE_DIR) f.mkdirs() else f.createNewFile()
                Bundle().apply {
                    putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(uri, docIdFor(f)))
                }
            }
            METHOD_RENAME_DOCUMENT -> {
                val name = extras.getString(Document.COLUMN_DISPLAY_NAME) ?: throw IllegalArgumentException("No name")
                val f = fileFor(docId)
                if (!f.exists()) throw FileNotFoundException("Missing file for $docId")
                // Like Android's own provider: a name that's taken gets a number added.
                var target = File(f.parentFile, name)
                var n = 1
                while (target.exists()) target = File(f.parentFile, "${name.substringBeforeLast('.')} (${n++})" + (if ('.' in name) "." + name.substringAfterLast('.') else ""))
                if (!f.renameTo(target)) throw IllegalStateException("Could not rename $docId")
                Bundle().apply { putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(uri, docIdFor(target))) }
            }
            METHOD_DELETE_DOCUMENT -> {
                val f = fileFor(docId)
                if (!f.exists() || !f.deleteRecursively()) throw FileNotFoundException("Could not delete $docId")
                Bundle()
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun addRow(c: MatrixCursor, f: File) {
        val row = c.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, docIdFor(f))
        row.add(Document.COLUMN_DISPLAY_NAME, if (f == root) "KOBOeReader" else f.name)
        row.add(Document.COLUMN_MIME_TYPE, if (f.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream")
        row.add(Document.COLUMN_SIZE, if (f.isDirectory) null else f.length())
        row.add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
        row.add(
            Document.COLUMN_FLAGS,
            Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_WRITE or (if (f.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else 0),
        )
    }
}
