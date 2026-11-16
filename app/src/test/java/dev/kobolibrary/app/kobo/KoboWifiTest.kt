package dev.kobolibrary.app.kobo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.TestEpubs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile

/** Books offered to the Kobo's web browser over Wi-Fi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KoboWifiTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val base = File(context.cacheDir, "wifi-" + UUID.randomUUID()).apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: KoboWifiServer? = null

    @After
    fun tearDown() {
        server?.stop()
        scope.cancel()
        base.deleteRecursively()
        CoverScreen.forget(context)
    }

    private class Got(val status: Int, val type: String?, val disposition: String?, val length: Long, val body: ByteArray)

    private fun get(path: String, method: String = "GET"): Got {
        val c = URL("http://127.0.0.1:${server!!.port.value}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 2.0; en-us;) AppleWebKit/538.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/538.1 (Kobo Touch 0386/4.38.23171)")
        c.connectTimeout = 5_000
        c.readTimeout = 10_000
        val status = c.responseCode
        val stream = if (status < 400) c.inputStream else c.errorStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        return Got(status, c.getHeaderField("Content-Type"), c.getHeaderField("Content-Disposition"), c.getHeaderFieldLong("Content-Length", -1), body)
    }

    private fun waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 15_000
        while (!done()) {
            if (System.currentTimeMillis() > until) throw AssertionError("Waited too long for $what")
            Thread.sleep(20)
        }
    }

    @Test
    fun theKobosBrowserGetsAPageOfBooksAndDownloadsThem() {
        val bytes = ByteArray(300_000) { (it % 7).toByte() }
        val book = File(base, "ready.kepub.epub").apply { writeBytes(bytes) }
        val books = listOf(
            WifiBook(0, "id-histories", "The Histories", "Herodotus", "kepub", "The Histories - Herodotus.kepub.epub"),
            WifiBook(1, "id-broken", "Broken <Book>", null, "EPUB", "Broken Book.epub"),
        )
        server = KoboWifiServer(
            books, scope,
            make = { b -> if (b.index == 0) WifiFile(book, b.name, KoboWifiServer.mimeFor("epub")) else throw java.io.IOException("not an EPUB") },
            cover = { b -> if (b.index == 0) byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2) else null },
            ports = emptyList(),
        ).apply { start() }
        waitFor("the page to start") { server!!.port.value > 0 }

        val page = get("/")
        assertEquals(200, page.status)
        assertTrue(page.type!!.startsWith("text/html"))
        val html = String(page.body, Charsets.UTF_8)
        assertTrue(html, "The Histories" in html && "Herodotus" in html)
        // Names are made safe for the page.
        assertTrue(html, "Broken &lt;Book&gt;" in html && "<Book>" !in html)
        // The path ends with the file's name, as the Kobo's browser wants.
        assertTrue(html, "href=\"/book/0/The%20Histories%20-%20Herodotus.kepub.epub\"" in html)

        // Asking only about it doesn't count as downloading it.
        val head = get("/book/0/The%20Histories%20-%20Herodotus.kepub.epub", "HEAD")
        assertEquals(200, head.status)
        assertEquals(bytes.size.toLong(), head.length)
        waitFor("it to be ready") { server!!.states.value[0] is WifiState.Ready }

        val got = get("/book/0/The%20Histories%20-%20Herodotus.kepub.epub")
        assertEquals(200, got.status)
        assertEquals("application/epub+zip", got.type)
        assertEquals("attachment; filename=\"The Histories - Herodotus.kepub.epub\"", got.disposition)
        assertArrayEquals(bytes, got.body)
        waitFor("it to count as sent") { server!!.states.value[0] is WifiState.Sent }

        // A book that can't be made ready says why, on the Kobo's screen and here.
        val broken = get("/book/1/Broken%20Book.epub")
        assertEquals(500, broken.status)
        assertTrue(String(broken.body, Charsets.UTF_8).contains("not an EPUB"))
        assertTrue(server!!.states.value[1] is WifiState.Failed)

        assertEquals(200, get("/cover/0.jpg").status)
        assertEquals(404, get("/cover/1.jpg").status)
        assertEquals(404, get("/book/7/x.epub").status)
        assertEquals(404, get("/nothing").status)
        assertEquals(405, get("/", "POST").status)

        // Stopped, it's no longer there.
        val port = server!!.port.value
        server!!.stop()
        assertEquals(0, server!!.port.value)
        // Nothing listens there any more: the port can be taken again. (Connecting to see would be no proof: a
        // connection to a port just let go of can join up with itself.)
        val free = runCatching { java.net.ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(port)) }.close() }.isSuccess
        assertTrue("The page is still there after it was stopped", free)
    }

    @Test
    fun downloadNamesAreInPlainLetters() {
        fun name(title: String, vararg authors: String, ending: String = ".kepub.epub") =
            KoboWifiServer.downloadName(BookMetadata(title = title, authors = authors.toList()), ending, "abcd1234")
        assertEquals("Les Miserables - Victor Hugo.kepub.epub", name("Les Misérables", "Victor Hugo"))
        assertEquals("Book abcd1234.kepub.epub", name("吾輩は猫である", "夏目漱石"))
        assertEquals("Dune.pdf", name("Dune", ending = ".pdf"))
        assertEquals("Prince Caspian - C. S. Lewis & Pauline Baynes.epub", name("Prince Caspian", "C. S. Lewis", "Pauline Baynes", ending = ".epub"))
        assertEquals(".kepub.epub", KoboWifiServer.endingFor("kepub"))
        assertEquals("application/pdf", KoboWifiServer.mimeFor(".pdf"))
    }

    @Test
    fun aBookIsMadeJustAsItGoesOverTheCable() {
        val epub = TestEpubs.calibreEpub2(File(base, "The Histories.epub"), extra = mapOf("OEBPS/cover.jpeg" to squareJpeg()))
        val made = KoboSender(context).fileForDownload("The Histories", epub, null, SendFormat.KEPUB, CoverFitting.BLUR, File(base, "out"), "id1", "The Histories - Herodotus.kepub.epub")
        assertEquals("The Histories - Herodotus.kepub.epub", made.name)
        assertTrue(made.file.name.endsWith(".kepub.epub"))
        // A kepub: Kobo's spans are in the text.
        val text = ZipFile(made.file).use { z -> z.entries().toList().filter { it.name.endsWith(".html") || it.name.endsWith(".xhtml") }.joinToString { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) } }
        assertTrue("koboSpan" in text)
        // The square cover was fitted to the Kobo's screen.
        val cover = EpubFile.open(made.file).use { e -> e.findCover()?.let { e.readBytes(it.path) } }!!
        val decoded = android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size)
        assertEquals(CoverScreen.current.width, decoded.width)
        assertEquals(CoverScreen.current.height, decoded.height)

        // A PDF goes as it is.
        val pdf = File(base, "scan.pdf").apply { writeBytes("%PDF-1.4 test".toByteArray()) }
        val asIs = KoboSender(context).fileForDownload("Scan", epub, pdf, SendFormat.KEPUB, CoverFitting.BLUR, File(base, "out"), "id2", "Scan.pdf")
        assertEquals("application/pdf", asIs.mime)
        assertArrayEquals(pdf.readBytes(), asIs.file.readBytes())
    }

    private fun squareJpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(0xFF2C4A6E.toInt())
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
