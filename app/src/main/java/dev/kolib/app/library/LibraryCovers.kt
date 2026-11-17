package dev.kolib.app.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Loads library cover pictures (the small `cover.jpg` copies), keeping recent ones in memory. */
class LibraryCovers(private val library: Library) {

    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val gate = Semaphore(4)

    private fun key(book: LibraryBook, large: Boolean) = "${book.id}:${book.coverVersion}:${if (large) "L" else "S"}"

    fun cached(book: LibraryBook, large: Boolean): Bitmap? = memory.get(key(book, large))

    suspend fun load(book: LibraryBook, large: Boolean, targetWidthPx: Int): Bitmap? {
        if (!book.hasCover) return null
        val key = key(book, large)
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                memory.get(key)?.let { return@withPermit it }
                val file = library.coverFile(book.id)
                if (!file.exists()) return@withPermit null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    if (!large) inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(file.path, opts)?.also { memory.put(key, it) }
            }
        }
    }
}
