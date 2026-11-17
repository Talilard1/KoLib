package dev.kolib.app.kobo

import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A book offered to the Kobo over Wi-Fi: its place in the list, its library id, what to show, what it goes as
 * ("kepub", "EPUB", "PDF", "CBZ", "CBR"), and the name it downloads as (see [KoboWifiServer.downloadName]).
 */
data class WifiBook(val index: Int, val id: String, val title: String, val author: String?, val kind: String, val name: String, val hasCover: Boolean = true)

/** How a book offered to the Kobo over Wi-Fi is going. */
sealed interface WifiState {
    /** Not made ready yet. */
    data object Waiting : WifiState

    /** Being made ready (made a kepub, its cover fitted). */
    data object Making : WifiState

    data class Ready(val bytes: Long) : WifiState

    /** The Kobo is downloading it. */
    data class Sending(val done: Long, val total: Long) : WifiState

    /** The Kobo has downloaded it (and added it to its library). */
    data class Sent(val bytes: Long) : WifiState

    data class Failed(val reason: String) : WifiState
}

/** A book made ready for the Kobo: the file, the name it downloads as, and what kind of file it is. */
class WifiFile(val file: File, val name: String, val mime: String)

/**
 * Sends books to a Kobo through its own web browser, for when it isn't plugged in: a small web page on this
 * device, on the local network, lists the books, and each is a link the Kobo's browser downloads, and the Kobo
 * then adds to its library. That's all the Kobo lets a web page do: it can't put the books in collections, change
 * the ones it has, or say how far they've been read.
 *
 * The page is plain, for the Kobo's old browser and its e-ink screen. It runs only while it's wanted ([start],
 * [stop]), and offers only the books chosen.
 */
class KoboWifiServer(
    val books: List<WifiBook>,
    private val scope: CoroutineScope,
    /** Makes a book ready to download (the same file as goes over the cable). */
    private val make: suspend (WifiBook) -> WifiFile,
    /** A small picture of a book's cover for the page, as a JPEG; null if it has none. */
    private val cover: suspend (WifiBook) -> ByteArray?,
    /** The ports to try, in order; any free one after those. */
    private val ports: List<Int> = (8080..8089).toList(),
    /** Told once it's stopped (to delete the books made ready, say). */
    private val onStopped: () -> Unit = {},
) {
    private val _states = MutableStateFlow<Map<Int, WifiState>>(books.associate { it.index to WifiState.Waiting })
    val states: StateFlow<Map<Int, WifiState>> = _states.asStateFlow()

    private val _port = MutableStateFlow(0)
    /** The port the page is on while it runs (0 when it doesn't). */
    val port: StateFlow<Int> = _port.asStateFlow()

    private var server: ServerSocket? = null
    private var job: Job? = null
    private val made = HashMap<Int, Deferred<WifiFile>>()
    private val covers = ConcurrentHashMap<Int, ByteArray>()

    fun start() {
        if (job != null) return
        val s = ports.firstNotNullOfOrNull { p -> runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull() }
            ?: runCatching { ServerSocket(0) }.getOrNull()
            ?: return AppLog.w("WiFi", "Couldn't start the page for the Kobo")
        server = s
        _port.value = s.localPort
        AppLog.i("WiFi", "Offering ${books.size} book(s) to the Kobo on port ${s.localPort}.")
        job = scope.launch(Dispatchers.IO) {
            // Made ready one after another, so each downloads at once when it's tapped.
            launch {
                for (b in books) runCatching { fileFor(b) }
            }
            while (isActive) {
                val socket = try {
                    s.accept()
                } catch (e: SocketException) {
                    break
                } catch (e: Exception) {
                    continue
                }
                launch { handle(socket) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { server?.close() }
        server = null
        _port.value = 0
        synchronized(made) {
            made.values.forEach { it.cancel() }
            made.clear()
        }
        scope.launch(Dispatchers.IO) { runCatching(onStopped) }
    }

    private fun set(b: WifiBook, state: WifiState) {
        _states.update { it + (b.index to state) }
    }

    private suspend fun fileFor(b: WifiBook): WifiFile {
        val d = synchronized(made) {
            made.getOrPut(b.index) {
                scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    set(b, WifiState.Making)
                    try {
                        make(b).also { if (_states.value[b.index] !is WifiState.Sent) set(b, WifiState.Ready(it.file.length())) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w("WiFi", "Couldn't make '${b.title}' ready for the Kobo", e)
                        set(b, WifiState.Failed(e.message ?: e.javaClass.simpleName))
                        throw e
                    }
                }
            }
        }
        return d.await()
    }

    // ---- the web page ---------------------------------------------------------------------------------------

    private suspend fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 30_000
                val input = BufferedInputStream(s.getInputStream())
                val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
                val head = readHead(input) ?: return
                val parts = head.substringBefore("\r\n").split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty().substringBefore('?').substringBefore('#')
                val headOnly = method == "HEAD"
                if (method != "GET" && !headOnly) return respond(out, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray(), headOnly)
                val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
                when {
                    segments.isEmpty() || path == "/index.html" ->
                        respond(out, 200, "text/html; charset=utf-8", page().toByteArray(Charsets.UTF_8), headOnly)
                    segments[0] == "cover" -> {
                        val b = segments.getOrNull(1)?.substringBefore('.')?.toIntOrNull()?.let { i -> books.getOrNull(i) }
                        val jpeg = b?.let { covers[it.index] ?: runCatching { cover(it) }.getOrNull()?.also { c -> covers[it.index] = c } }
                        if (jpeg == null) respond(out, 404, "text/plain; charset=utf-8", "No cover".toByteArray(), headOnly)
                        else respond(out, 200, "image/jpeg", jpeg, headOnly, cache = true)
                    }
                    segments[0] == "book" -> {
                        val b = segments.getOrNull(1)?.toIntOrNull()?.let { i -> books.getOrNull(i) }
                            ?: return respond(out, 404, "text/html; charset=utf-8", message(tr("That book is no longer available.")).toByteArray(), headOnly)
                        val f = try {
                            fileFor(b)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            return respond(out, 500, "text/html; charset=utf-8", message(tr("Couldn't prepare “{0}”: {1}", b.title, e.message ?: "")).toByteArray(), headOnly)
                        }
                        sendFile(out, b, f, headOnly)
                    }
                    else -> respond(out, 404, "text/plain; charset=utf-8", "Not found".toByteArray(), headOnly)
                }
            } catch (e: IOException) {
                // The Kobo went away part way.
            }
        }
    }

    private fun sendFile(out: OutputStream, b: WifiBook, f: WifiFile, headOnly: Boolean) {
        val total = f.file.length()
        header(out, 200, f.mime, total, "Content-Disposition: attachment; filename=\"${f.name}\"\r\n")
        if (headOnly) return out.flush()
        set(b, WifiState.Sending(0, total))
        try {
            f.file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var told = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    if (done - told >= 256 * 1024) {
                        told = done
                        set(b, WifiState.Sending(done, total))
                    }
                }
            }
            out.flush()
            set(b, WifiState.Sent(total))
            AppLog.i("WiFi", "The Kobo downloaded '${b.title}'.")
        } catch (e: IOException) {
            // Stopped part way: it can be downloaded again.
            set(b, WifiState.Ready(total))
            throw e
        }
    }

    /** The page the Kobo's browser shows: the books, each a link to download it. */
    internal fun page(): String {
        val states = _states.value
        val items = if (books.isEmpty()) "<p class=\"lead\">${esc(tr("There are no books to send."))}</p>" else books.joinToString("\n") { b ->
            val state = states[b.index]
            val size = when (state) {
                is WifiState.Ready -> megabytes(state.bytes)
                is WifiState.Sent -> megabytes(state.bytes)
                is WifiState.Sending -> megabytes(state.total)
                else -> null
            }
            val line = listOfNotNull(b.kind, size).joinToString(" · ")
            val done = if (state is WifiState.Sent) "<span class=\"done\">${esc(tr("Downloaded"))}</span>" else ""
            val failed = (state as? WifiState.Failed)?.let { "<span class=\"meta\">${esc(tr("Failed: {0}", it.reason))}</span>" } ?: ""
            """<a class="book" href="${bookHref(b)}">""" + (if (b.hasCover) """<img src="/cover/${b.index}.jpg" width="80" height="120" alt="">""" else "") +
                """<span class="title">${esc(b.title)}</span>""" +
                (b.author?.let { """<span class="author">${esc(it)}</span>""" } ?: "") +
                """<span class="meta">${esc(line)}</span>$failed<span class="get">${esc(tr("Download"))}</span>$done</a>"""
        }
        return """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>KoLib</title>
<style>
body{font-family:Georgia,serif;margin:0;padding:18px 22px;background:#fff;color:#000}
h1{font-size:32px;margin:0 0 6px}
.lead{font-size:20px;margin:0 0 20px}
.book{display:block;border:2px solid #000;border-radius:12px;padding:14px;margin:0 0 16px;text-decoration:none;color:#000;overflow:hidden}
.book img{float:left;width:80px;height:120px;margin:0 16px 0 0;border:1px solid #000}
.title{display:block;font-size:24px;font-weight:bold;line-height:1.2}
.author{display:block;font-size:20px;margin-top:4px}
.meta{display:block;font-size:17px;margin-top:6px}
.get{display:inline-block;margin-top:10px;font-size:20px;font-weight:bold;border:2px solid #000;border-radius:10px;padding:6px 18px}
.done{display:inline-block;margin:10px 0 0 12px;font-size:18px;font-weight:bold}
.note{font-size:16px;margin-top:22px}
</style></head><body>
<h1>KoLib</h1>
<p class="lead">${esc(tr("Tap a book to download it. Your Kobo adds it to its library."))}</p>
$items
<p class="note">${esc(tr("If a download stops part way, close the Kobo's browser and open it again before the next one."))}</p>
</body></html>
"""
    }

    /** Where a book downloads from: a path ending in its file's name, as the Kobo's browser wants. */
    private fun bookHref(b: WifiBook): String = "/book/${b.index}/" + URLEncoder.encode(b.name, "UTF-8").replace("+", "%20")

    private fun message(text: String) = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>KoLib</title>
<style>body{font-family:Georgia,serif;margin:0;padding:18px 22px;font-size:20px}a{color:#000;font-weight:bold}</style></head>
<body><p>${esc(text)}</p><p><a href="/">${esc(tr("Back to the books"))}</a></p></body></html>
"""

    private fun megabytes(bytes: Long): String =
        if (bytes < 1_048_576) tr("{0} kB", (bytes + 1023) / 1024) else tr("{0} MB", String.format(Locale.getDefault(), "%.1f", bytes / 1_048_576.0))

    private fun respond(out: OutputStream, status: Int, type: String, body: ByteArray, headOnly: Boolean, cache: Boolean = false) {
        header(out, status, type, body.size.toLong(), if (cache) "Cache-Control: max-age=3600\r\n" else "Cache-Control: no-store\r\n")
        if (!headOnly) out.write(body)
        out.flush()
    }

    private fun header(out: OutputStream, status: Int, type: String, length: Long, extra: String = "") {
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Internal Server Error"
        }
        out.write(
            ("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: $length\r\n${extra}Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
        )
    }

    /** The request's lines, up to the blank line after them; null if it doesn't come. */
    private fun readHead(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var last4 = 0
        while (bytes.size() < 16 * 1024) {
            val c = input.read()
            if (c < 0) return null
            bytes.write(c)
            last4 = (last4 shl 8) or c
            if (last4 == 0x0D0A0D0A) return bytes.toString("ISO-8859-1")
        }
        return null
    }

    companion object {
        /**
         * The name a book downloads as: "Title - Author" and the file's ending, in plain letters (the Kobo's browser
         * is happiest with those), or "Book" and [unique] when nothing of the title is left that way.
         */
        fun downloadName(meta: BookMetadata, ending: String, unique: String): String {
            val raw = listOf(meta.title, meta.authors.joinToString(" & ")).filter { it.isNotBlank() }.joinToString(" - ")
            val plain = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^A-Za-z0-9 ._()&,'-]"), " ").replace(Regex("\\s+"), " ").trim().take(100).trim().trimEnd('.')
            return (if (plain.count { it.isLetterOrDigit() } < 2) "Book $unique" else plain) + ending
        }

        fun endingFor(kind: String): String = when (kind) {
            "kepub" -> ".kepub.epub"
            "PDF" -> ".pdf"
            "CBZ" -> ".cbz"
            "CBR" -> ".cbr"
            else -> ".epub"
        }

        fun mimeFor(ending: String): String = when (ending.lowercase().trimStart('.')) {
            "pdf" -> "application/pdf"
            "cbz" -> "application/vnd.comicbook+zip"
            "cbr" -> "application/vnd.comicbook-rar"
            else -> "application/epub+zip"
        }

        private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
