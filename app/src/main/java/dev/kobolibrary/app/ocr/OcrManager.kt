package dev.kobolibrary.app.ocr

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.KoboLibraryApp
import dev.kobolibrary.app.convert.PdfConverter
import dev.kobolibrary.app.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Where a book's text recognition is. */
sealed class OcrStatus {
    /** Waiting for the books before it. */
    data object Waiting : OcrStatus()

    /** Downloading what the recognition needs for its languages: [done] of [total] bytes. */
    data class Downloading(val done: Long, val total: Long) : OcrStatus()

    /** Looking at a page or two, to see how big to draw the pages. */
    data object Preparing : OcrStatus()

    /** Reading the pages: [done] of [total]; [doneAtStart] were done when this run began, at [startedAt]. */
    data class Reading(val done: Int, val total: Int, val startedAt: Long, val doneAtStart: Int) : OcrStatus() {
        /** About how long is left, once there's enough to go by. */
        fun millisLeft(now: Long = System.currentTimeMillis()): Long? {
            val n = done - doneAtStart
            if (n < 3) return null
            return (now - startedAt) / n * (total - done)
        }
    }

    /** Putting the book together from its pages. */
    data object Making : OcrStatus()
}

/** How a book's recognition ended, to tell. */
sealed class OcrNews(val bookId: String, val title: String) {
    class Done(bookId: String, title: String) : OcrNews(bookId, title)
    class Failed(bookId: String, title: String, val reason: String) : OcrNews(bookId, title)
}

/**
 * Recognises the text in scanned books, one book at a time, while [OcrService] keeps the app running.
 * What's been read of a book is kept page by page (see [OcrStore]), so a stopped book carries on where it
 * was. A finished book's text replaces its stand-in in the library.
 */
class OcrManager(private val app: KoboLibraryApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow<Map<String, OcrStatus>>(emptyMap())
    val status: StateFlow<Map<String, OcrStatus>> = _status.asStateFlow()

    private val _news = MutableSharedFlow<OcrNews>(extraBufferCapacity = 16)
    val news: SharedFlow<OcrNews> = _news.asSharedFlow()

    private class Request(val id: String, val languages: List<String>)

    private val queue = ArrayDeque<Request>()
    private var worker: Job? = null
    private var current: Pair<String, Job>? = null

    val busy: Boolean get() = _status.value.isNotEmpty()

    /** Readers at once: half the processor's cores, up to four (each holds a page and its own copy of the language data). */
    private fun workers(): Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

    /** Queues [ids] to be read in [languages]. */
    fun start(ids: List<String>, languages: List<String>) {
        if (languages.isEmpty()) return
        synchronized(queue) {
            for (id in ids) {
                if (id in _status.value) continue
                queue += Request(id, languages)
                _status.update { it + (id to OcrStatus.Waiting) }
            }
            if (worker?.isActive != true) worker = scope.launch { drain() }
        }
        OcrService.start(app)
    }

    /** Stops reading [id] (what's been read is kept, to carry on from). */
    fun stop(id: String) {
        synchronized(queue) {
            queue.removeAll { it.id == id }
            val running = current
            if (running?.first == id) running.second.cancel() else _status.update { it - id }
        }
    }

    fun stopAll() {
        synchronized(queue) {
            for (r in queue) _status.update { it - r.id }
            queue.clear()
            current?.second?.cancel()
        }
    }

    /** How far an unfinished book got: pages read, of how many; null if it hasn't been started (or is done). */
    fun partly(id: String): Pair<Int, Int>? {
        val store = app.library.ocrStore(id)
        if (store.applied != null) return null
        val s = store.settings ?: return null
        val done = store.done(s.pages)
        return if (done in 1 until s.pages) done to s.pages else null
    }

    private suspend fun drain() {
        while (true) {
            val r = synchronized(queue) { queue.removeFirstOrNull() } ?: break
            val job = scope.launch { process(r) }
            synchronized(queue) { current = r.id to job }
            job.join()
            synchronized(queue) { current = null }
            _status.update { it - r.id }
        }
    }

    private fun set(id: String, s: OcrStatus) = _status.update { it + (id to s) }

    private suspend fun process(r: Request) {
        val library = app.library
        val book = library.book(r.id) ?: return
        try {
            val pdf = library.sourceFile(r.id) ?: throw IOException(tr("The original PDF is missing, so its pages can't be read."))
            val filesDir = app.filesDir
            val missing = OcrLanguages.missing(filesDir, r.languages)
            if (missing.isNotEmpty()) {
                set(r.id, OcrStatus.Downloading(0, missing.sumOf { it.bytes }))
                withContext(Dispatchers.IO) {
                    val running = currentCoroutineContext()
                    OcrLanguages.download(filesDir, r.languages, stillWanted = { running.isActive }) { done, total ->
                        set(r.id, OcrStatus.Downloading(done, total))
                    }
                }
            }
            val dataRoot = OcrLanguages.root(filesDir)
            AndroidOcrPages(pdf).use { pages ->
                val store = library.ocrStore(r.id)
                val count = pages.count
                if (count == 0) throw IOException(tr("This PDF has no pages."))
                val engines = { TesseractEngine(dataRoot, r.languages) }
                val run = OcrRun(pages, store, engines, workers())
                val earlier = store.settings?.takeIf { it.languages == r.languages && it.pages == count }
                val dpi = earlier?.dpi ?: run {
                    set(r.id, OcrStatus.Preparing)
                    withContext(Dispatchers.Default) { engines().use { e -> synchronized(pages) { run.chooseDpi(e) } } }
                }
                store.begin(OcrStore.Settings(r.languages, dpi, count))
                val started = System.currentTimeMillis()
                val atStart = store.done(count)
                AppLog.i("OCR", "Reading '${book.title}' (${r.languages.joinToString("+")}, $dpi dpi, ${workers()} at once): $atStart of $count pages done before.")
                run.run(dpi) { done, total -> set(r.id, OcrStatus.Reading(done, total, started, atStart)) }

                set(r.id, OcrStatus.Making)
                val made = File(app.cacheDir, "ocr-${r.id}.epub")
                try {
                    withContext(Dispatchers.IO) {
                        val text = (0 until count).map { i -> OcrBook.PageText.of(store.page(i) ?: OcrPage(0, 0, dpi, emptyList())) }
                        // The bookmarks, from a PDF small enough to read whole.
                        val contents = if (pdf.length() <= 64L shl 20) PdfConverter.contents(pdf) else emptyList()
                        OcrBook.write(library.book(r.id)?.meta ?: book.meta, text, contents, pages, made)
                    }
                    library.applyRecognisedText(r.id, made, r.languages)
                } finally {
                    made.delete()
                }
                AppLog.i("OCR", "Made '${book.title}' into text in ${(System.currentTimeMillis() - started) / 1000} s.")
            }
            _news.emit(OcrNews.Done(r.id, book.title))
        } catch (e: CancellationException) {
            AppLog.i("OCR", "Stopped reading '${book.title}'.")
            throw e
        } catch (e: Throwable) {
            AppLog.e("OCR", "Making '${book.title}' into text failed", e)
            _news.emit(OcrNews.Failed(r.id, book.title, (e.message ?: e.javaClass.simpleName).trimEnd('.')))
        }
    }
}
