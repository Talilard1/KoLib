package dev.kobolibrary.app.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.reader.ReaderStore
import dev.kobolibrary.app.reader.ReadingHereIndex
import dev.kobolibrary.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** What KoLib does with what's sent from another device, and finds to send: the library, the clipboard, Downloads. */
class AppLinkHost(
    private val context: Context,
    private val library: Library,
    private val readingHere: ReadingHereIndex,
) : LinkHost {

    override fun receiveText(text: String, from: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(tr("From {0}", from), text))
            }
        }
    }

    override suspend fun receiveFile(name: String, type: String?, size: Long, from: String, write: suspend (OutputStream) -> Unit): String {
        val resolver = context.contentResolver
        val mime = type?.takeIf { it.contains('/') }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = withContext(Dispatchers.IO) { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }
            ?: throw IOException(tr("Android couldn't create {0} in Downloads", name))
        try {
            val out = withContext(Dispatchers.IO) { resolver.openOutputStream(uri) } ?: throw IOException(tr("Could not write {0} to Downloads", name))
            out.use { write(it) }
            withContext(Dispatchers.IO) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return tr("Downloads, in the {0} folder", FOLDER)
    }

    /** KoLib can be started by another device's link (Share, or staying reachable) before its library is read. */
    private suspend fun ready() {
        if (!library.loaded) library.load()
    }

    override suspend fun outgoingBook(id: String): OutgoingBook? = withContext(Dispatchers.IO) {
        ready()
        val book = library.book(id) ?: return@withContext null
        val dir = library.bookDir(id)
        val files = dir.listFiles()?.filter { it.isFile && LinkBooks.sendable(it.name) }?.sortedBy { if (it.name == "book.epub") 0 else 1 }.orEmpty()
        if (files.none { it.name == "book.epub" }) return@withContext null
        OutgoingBook(book.title, LinkBooks.toJson(book), ReadingData.readingOf(dir), files)
    }

    override suspend fun wantBook(record: JSONObject, reading: JSONObject): Boolean = withContext(Dispatchers.IO) {
        ready()
        val incoming = LinkBooks.from(record) ?: throw IOException(tr("The book's details couldn't be read."))
        val here = library.sameBookAs(incoming) ?: return@withContext true
        if (here.id == incoming.id && incoming.modifiedAt > here.modifiedAt) return@withContext true
        // The same version (or this one's newer): the files stay, but the collections it was sent in are taken,
        // as they're changed without changing the book.
        val collections = incoming.collections.distinctBy { it.lowercase() }
        if (here.id == incoming.id && incoming.modifiedAt == here.modifiedAt && collections.map { it.lowercase() }.toSet() != here.collections.map { it.lowercase() }.toSet()) {
            library.updateDetails(here.id, here.meta, collections)
        }
        mergeReading(here.id, reading)
        false
    }

    override suspend fun receiveBook(record: JSONObject, reading: JSONObject, files: File): BookReceived {
        val incoming = LinkBooks.from(record) ?: throw IOException(tr("The book's details couldn't be read."))
        ready()
        val (book, added) = library.takeFromDevice(incoming, files)
        withContext(Dispatchers.IO) { mergeReading(book.id, reading) }
        return if (added) BookReceived.ADDED else BookReceived.UPDATED
    }

    private fun mergeReading(id: String, reading: JSONObject) {
        val dir = library.bookDir(id)
        ReadingData.mergeInto(dir, reading)
        refresh(id, dir)
    }

    private fun refresh(id: String, dir: File) {
        val here = ReaderStore.readHereOf(dir)
        if (here != null) readingHere.set(id, here) else readingHere.forget(listOf(id))
    }

    override suspend fun places(): List<BookPlace> = withContext(Dispatchers.IO) {
        ready()
        library.books.value.map { b -> BookPlace(b.id, b.title, b.meta.authors.firstOrNull(), ReadingData.placeOf(library.bookDir(b.id))) }
    }

    override suspend fun applyPlaces(places: List<BookPlace>) = withContext(Dispatchers.IO) {
        ready()
        for (p in places) {
            if (library.book(p.id) == null) continue
            val dir = library.bookDir(p.id)
            ReadingData.applyPlace(dir, p.state)
            refresh(p.id, dir)
        }
    }

    override fun scratch(): File = File(context.cacheDir, "link").apply { mkdirs() }

    override fun freeSpace(): Long = library.freeSpaceBytes()

    companion object {
        const val FOLDER = "KoLib"
    }
}
