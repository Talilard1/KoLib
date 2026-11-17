package dev.kolib.app.link

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.kolib.app.tr
import java.io.File
import java.io.IOException

/** Files chosen on this device, to send to another. */
internal object LinkFiles {
    /** [uri] as it's sent: its name, kind and size (copied here first if the app it's from doesn't say how big it is). */
    fun fromUri(context: Context, uri: Uri): OutgoingFile {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val fileName = LinkManager.safeName(name ?: uri.lastPathSegment ?: "file")
        val type = runCatching { resolver.getType(uri) }.getOrNull()
        if (size >= 0) return OutgoingFile(fileName, type, size) { resolver.openInputStream(uri) ?: throw IOException(tr("The file couldn't be read.")) }
        val copy = File(File(context.cacheDir, "share-out").apply { mkdirs() }, fileName)
        resolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } ?: throw IOException(tr("The file couldn't be read."))
        return OutgoingFile(fileName, type, copy.length()) { copy.inputStream() }
    }
}
