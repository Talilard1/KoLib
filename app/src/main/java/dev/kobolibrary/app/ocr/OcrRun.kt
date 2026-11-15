package dev.kobolibrary.app.ocr

import dev.kobolibrary.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Reads the text in pictures of pages. Each is used by one thread at a time. */
interface OcrEngine : Closeable {
    /** The page's text as hOCR: [gray] is the picture, a byte a pixel, [width] × [height], drawn at [dpi]. */
    fun read(gray: ByteArray, width: Int, height: Int, dpi: Int): String

    /** Asks a read that's under way to give up as soon as it can. */
    fun stop()
}

/** A page drawn in shades of grey (a byte a pixel), at [dpi]; [blank] if there's next to nothing on it. */
class GrayPage(val pixels: ByteArray, val width: Int, val height: Int, val dpi: Int, val blank: Boolean)

/** Draws a book's pages for reading. */
interface OcrPageSource {
    val count: Int

    /** Page [index] in grey at [dpi] (less if that would make it too big); null if it can't be drawn. */
    fun gray(index: Int, dpi: Int): GrayPage?
}

/**
 * Reads every page of [source] that [store] doesn't have yet, [workers] at a time (each with its own
 * engine from [engines]), saving each page as it's done, so stopping loses at most the pages under way.
 */
class OcrRun(
    private val source: OcrPageSource,
    private val store: OcrStore,
    private val engines: () -> OcrEngine,
    private val workers: Int,
) {
    /** The usual size of a line of text the recognition reads best; small print is drawn bigger to make it so. */
    private val wantedLineSize = 40

    /**
     * The resolution to draw pages at: 300 dpi, or more for small print (judged from a page or two of the
     * book), up to 600.
     */
    fun chooseDpi(engine: OcrEngine): Int {
        val n = source.count
        for (i in listOf(n / 2, n / 3, 2 * n / 3, n / 4, 0).distinct().filter { it in 0 until n }) {
            val g = runCatching { source.gray(i, 300) }.getOrNull() ?: continue
            if (g.blank) continue
            val page = Hocr.parse(engine.read(g.pixels, g.width, g.height, g.dpi), g.dpi)
            val sizes = page.lines.filter { it.words.size >= 3 }.map { it.size }.sorted()
            if (sizes.size < 5) continue
            val median = sizes[sizes.size / 2] * 300.0 / g.dpi
            val dpi = if (median >= wantedLineSize * 0.85) 300 else (300 * wantedLineSize / median).roundToInt().coerceIn(300, 600)
            AppLog.i("OCR", "Lines on page ${i + 1} are about ${median.roundToInt()} pixels at 300 dpi; reading pages at $dpi dpi.")
            return dpi
        }
        return 300
    }

    /** Reads what's left; [progress] is told how many pages of all of them are done. Failed pages are left out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run(dpi: Int, progress: (done: Int, total: Int) -> Unit) {
        val total = source.count
        val todo = (0 until total).filter { !store.has(it) }
        val done = AtomicInteger(total - todo.size)
        progress(done.get(), total)
        if (todo.isEmpty()) return
        val next = AtomicInteger(0)
        val open = CopyOnWriteArrayList<OcrEngine>()
        val threads = Dispatchers.Default.limitedParallelism(workers.coerceAtLeast(1))
        coroutineScope {
            // Stopping stops the pages being read too. (Not on the readers' threads, which may all be busy.)
            val watcher = launch(Dispatchers.Default) {
                try {
                    awaitCancellation()
                } finally {
                    open.forEach { runCatching { it.stop() } }
                }
            }
            val readers = (0 until workers.coerceIn(1, todo.size)).map {
                    launch(threads) {
                        val engine = engines()
                        open += engine
                        try {
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val k = next.getAndIncrement()
                                if (k >= todo.size) break
                                val index = todo[k]
                                try {
                                    val g = synchronized(source) { source.gray(index, dpi) }
                                    if (g == null) {
                                        AppLog.w("OCR", "Page ${index + 1} couldn't be drawn; it stays a picture.")
                                    } else {
                                        val page = if (g.blank) OcrPage.blank(g.width, g.height, g.dpi)
                                        else Hocr.parse(engine.read(g.pixels, g.width, g.height, g.dpi), g.dpi)
                                        currentCoroutineContext().ensureActive()
                                        store.save(index, page)
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLog.w("OCR", "Page ${index + 1} couldn't be read; it stays a picture.", e)
                                }
                                progress(done.incrementAndGet(), total)
                            }
                        } finally {
                            open -= engine
                            runCatching { engine.close() }
                        }
                    }
            }
            readers.joinAll()
            watcher.cancel()
        }
    }
}
