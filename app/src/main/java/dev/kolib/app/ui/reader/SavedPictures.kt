package dev.kolib.app.ui.reader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A book's pictures saved to the device (in Pictures, in a KoLib folder) or shared with another app. */
internal object SavedPictures {

    /** What kind of picture [bytes] are, from how they start, with the file ending for it; null if not one a gallery shows. */
    fun kindOf(bytes: ByteArray): Pair<String, String>? {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        fun text(from: Int, s: String) = s.indices.all { at(from + it) == s[it].code }
        return when {
            at(0) == 0xFF && at(1) == 0xD8 -> "image/jpeg" to "jpg"
            at(0) == 0x89 && text(1, "PNG") -> "image/png" to "png"
            text(0, "GIF8") -> "image/gif" to "gif"
            text(0, "RIFF") && text(8, "WEBP") -> "image/webp" to "webp"
            text(0, "BM") -> "image/bmp" to "bmp"
            else -> null
        }
    }

    /** "Book title - picture", with nothing in it a file name can't have. */
    fun nameFor(bookTitle: String, path: String, ending: String): String {
        fun clean(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ").replace(Regex("""\s+"""), " ").trim()
        val picture = path.substringAfterLast('/').substringBeforeLast('.')
        val name = listOf(clean(bookTitle.take(60)), clean(picture)).filter { it.isNotEmpty() }.joinToString(" - ")
        return "${name.ifEmpty { "Picture" }}.$ending"
    }

    /** Saves the picture at [path] ([bytes], as it's shown) to Pictures/KoLib, and says how that went. */
    suspend fun save(context: Context, bytes: ByteArray, path: String, bookTitle: String) {
        val kind = kindOf(bytes)
        val ok = kind != null && withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, nameFor(bookTitle, path, kind.second))
                    put(MediaStore.Images.Media.MIME_TYPE, kind.first)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KoLib")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no place for it")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("not written")
            }.onFailure { AppLog.w("Reader", "Couldn't save a picture from the book", it) }.isSuccess
        }
        Toast.makeText(context, if (ok) tr("Saved to Pictures/KoLib.") else tr("The picture couldn't be saved."), Toast.LENGTH_SHORT).show()
    }

    /** Offers the picture at [path] to another app. */
    suspend fun share(context: Context, bytes: ByteArray, path: String, bookTitle: String) {
        val kind = kindOf(bytes) ?: run {
            Toast.makeText(context, tr("The picture couldn't be shared."), Toast.LENGTH_SHORT).show()
            return
        }
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            File(dir, nameFor(bookTitle, path, kind.second)).also { it.writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType(kind.first).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, tr("Share"))) }
    }
}
