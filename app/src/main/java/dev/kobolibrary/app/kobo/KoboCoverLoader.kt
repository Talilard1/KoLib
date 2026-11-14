package dev.kobolibrary.app.kobo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.saf.DocEntry
import dev.kobolibrary.app.saf.SafTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

enum class CoverSize { THUMBNAIL, FULL }

/**
 * Loads the cover images the Kobo has already made for its own library screens
 * (`.kobo-images/<a>/<b>/<ImageId> - N3_*.parsed`). Nothing is written to the Kobo.
 *
 * Each picture read is also kept in [diskDir] on the tablet, so the Kobo's books still show their
 * covers when it isn't plugged in. With no [tree] (the Kobo isn't connected) only that copy is used.
 */
class KoboCoverLoader(private val tree: SafTree?, private val diskDir: File?) {

    private val memory = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val missing = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val folders = ConcurrentHashMap<String, List<DocEntry>>()
    private val gate = Semaphore(3)

    @Volatile
    private var failureLogged = false

    @Volatile
    var loadedCount = 0
        private set

    fun cached(imageId: String, size: CoverSize): Bitmap? = memory.get(key(imageId, size))

    suspend fun load(imageId: String, size: CoverSize, targetWidthPx: Int): Bitmap? {
        val key = key(imageId, size)
        memory.get(key)?.let { return it }
        if (key in missing) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                memory.get(key)?.let { return@withPermit it }
                val bytes = readBytes(imageId, size) ?: return@withPermit null
                val bitmap = decode(bytes, targetWidthPx)
                if (bitmap == null) {
                    missing += key
                } else {
                    memory.put(key, bitmap)
                    loadedCount++
                }
                bitmap
            }
        }
    }

    /** The picture's bytes: from the copy on the tablet if there is one, otherwise from the Kobo (keeping a copy). */
    private fun readBytes(imageId: String, size: CoverSize): ByteArray? {
        val key = key(imageId, size)
        val local = diskFile(imageId, size)
        if (local != null && local.length() > 0) {
            runCatching { return local.readBytes() }
        }
        val t = tree ?: return null
        return try {
            val entry = findCoverFile(t, imageId, size)
            if (entry == null) {
                missing += key
                null
            } else {
                t.openInput(entry.docId).use { it.readBytes() }.also { bytes -> keep(local, bytes) }
            }
        } catch (e: Exception) {
            // Most likely the Kobo was unplugged. Log once, don't remember as missing.
            if (!failureLogged) {
                failureLogged = true
                AppLog.w("Covers", "Could not read a cover from the Kobo: ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    private fun keep(file: File?, bytes: ByteArray) {
        file ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".part")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    /**
     * Copies the small cover of every listed book to the tablet in the background (skipping ones
     * already there), so they all show when the Kobo isn't plugged in.
     */
    suspend fun keepThumbnails(imageIds: List<String>, stillWanted: () -> Boolean): Int = withContext(Dispatchers.IO) {
        val t = tree ?: return@withContext 0
        var copied = 0
        for (id in imageIds) {
            if (!stillWanted()) break
            val local = diskFile(id, CoverSize.THUMBNAIL) ?: break
            if (local.exists()) continue
            val entry = runCatching { findCoverFile(t, id, CoverSize.THUMBNAIL) }.getOrNull() ?: continue
            val bytes = runCatching { t.openInput(entry.docId).use { it.readBytes() } }.getOrNull() ?: break
            keep(local, bytes)
            copied++
        }
        copied
    }

    /** Forgets the pictures of a book whose cover the Kobo will make again (after it was replaced). */
    fun forget(imageId: String) {
        for (size in CoverSize.entries) {
            memory.remove(key(imageId, size))
            missing.remove(key(imageId, size))
            diskFile(imageId, size)?.delete()
        }
        folders.remove(KoboImages.folderFor(imageId))
    }

    private fun diskFile(imageId: String, size: CoverSize): File? =
        diskDir?.let { File(it, "${size.name.lowercase()}/${hashName(imageId)}.jpg") }

    private fun findCoverFile(tree: SafTree, imageId: String, size: CoverSize): DocEntry? {
        val folder = KoboImages.folderFor(imageId)
        val files = folders.getOrPut(folder) {
            val dir = tree.resolve("${KoboImages.IMAGES_DIR}/$folder")
            if (dir == null || !dir.isDirectory) emptyList() else tree.children(dir.docId)
        }
        val suffixes = if (size == CoverSize.THUMBNAIL) KoboImages.THUMBNAIL_SUFFIXES else KoboImages.FULL_SUFFIXES
        for (suffix in suffixes) {
            val name = imageId + suffix
            files.firstOrNull { it.name == name }?.let { return it }
        }
        return null
    }

    private fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun key(imageId: String, size: CoverSize) = "${size.name}:$imageId"

    private companion object {
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        fun hashName(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
