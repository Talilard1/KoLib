package dev.kolib.app.covers

import dev.kolib.app.BuildConfig
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.ImageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Where a cover found online comes from. */
enum class CoverSource(val label: String) {
    AMAZON("Amazon"),
    GOOGLE_BOOKS("Google Books"),
    OPEN_LIBRARY("Open Library"),
    GOOGLE_IMAGES("Google Images"),
}

/** A picture that might be the book's cover, at [url]. */
data class CoverCandidate(val source: CoverSource, val url: String)

/** A cover found online: where it's from, the picture itself, and its size in pixels. */
class FoundCover(val source: CoverSource, val url: String, val bytes: ByteArray, val width: Int, val height: Int)

/** Fetches what's at a web address, up to [maxBytes]; null if it can't. */
fun interface Fetcher {
    fun get(url: String, maxBytes: Int): ByteArray?
}

/** Over the internet, as KoLib, with time limits so a slow site doesn't hold up the rest. */
object HttpFetcher : Fetcher {
    private val agent = "KoLib/${BuildConfig.VERSION_NAME} (Android ebook app; cover search)"

    override fun get(url: String, maxBytes: Int): ByteArray? {
        var address = url
        repeat(4) {
            val c = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", agent)
                setRequestProperty("Accept", "*/*")
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    // Followed here, so a redirect from https to http (or back) is followed too.
                    address = URL(URL(address), c.getHeaderField("Location") ?: return null).toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                c.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > maxBytes) return null
                    }
                    return out.toByteArray()
                }
            } finally {
                c.disconnect()
            }
        }
        return null
    }
}

/**
 * Looks for a book's cover online: on Open Library and Google Books by its title and author, and on
 * Amazon by the ISBNs of its editions (its own, and those the other two list). Each picture is fetched
 * whole, so its real size is known.
 */
class CoverSearch(private val fetch: Fetcher = HttpFetcher) {

    /** Where covers for the book might be. [query] instead of title and author: searched as typed. */
    suspend fun candidates(title: String, authors: List<String>, isbn: String?, query: String? = null): List<CoverCandidate> = coroutineScope {
        val openLibrary = async(Dispatchers.IO) { runCatching { openLibrary(title, authors, query) }.getOrDefault(Found()) }
        val googleBooks = async(Dispatchers.IO) { runCatching { googleBooks(title, authors, query) }.getOrDefault(Found()) }
        val ol = openLibrary.await()
        val gb = googleBooks.await()
        val isbns = (listOfNotNull(isbn) + gb.isbns + ol.isbns).mapNotNull { isbn10(it) }.distinct().take(MAX_AMAZON)
        isbns.map { CoverCandidate(CoverSource.AMAZON, amazonCover(it)) } + gb.covers + ol.covers
    }

    /** Fetches [c] and measures it; null if it isn't a picture, or is too small to be a cover. */
    fun load(c: CoverCandidate): FoundCover? {
        val bytes = fetch.get(c.url, MAX_PICTURE) ?: return null
        val (w, h) = ImageSize.of(bytes) ?: return null
        // Amazon answers a missing cover with a dot; Google Books with a small grey card.
        if (w < 100 || h < 120) return null
        return FoundCover(c.source, c.url, bytes, w, h)
    }

    /** Every cover found, given to [found] as each comes in (on a background thread), without the same picture twice. */
    suspend fun search(title: String, authors: List<String>, isbn: String?, query: String? = null, found: suspend (FoundCover) -> Unit) = coroutineScope {
        val all = candidates(title, authors, isbn, query)
        val seen = HashSet<String>()
        val gate = Semaphore(6)
        all.map { c ->
            launch(Dispatchers.IO) {
                val cover = gate.withPermit { runCatching { load(c) }.getOrNull() } ?: return@launch
                val key = sha1(cover.bytes)
                val fresh = synchronized(seen) { seen.add(key) }
                if (fresh) found(cover)
            }
        }.forEach { it.join() }
    }

    internal class Found(val covers: List<CoverCandidate> = emptyList(), val isbns: List<String> = emptyList())

    private fun openLibrary(title: String, authors: List<String>, query: String?): Found {
        val url = if (query != null) "https://openlibrary.org/search.json?q=${enc(query)}&fields=key,title,cover_i,isbn&limit=12"
        else "https://openlibrary.org/search.json?title=${enc(title)}" +
            (authors.firstOrNull()?.let { "&author=${enc(it)}" } ?: "") + "&fields=key,title,cover_i,isbn&limit=12"
        val json = fetch.get(url, MAX_JSON)?.let { JSONObject(String(it, Charsets.UTF_8)) } ?: return Found()
        return parseOpenLibrary(json)
    }

    private fun googleBooks(title: String, authors: List<String>, query: String?): Found {
        val q = query ?: ("intitle:" + title + (authors.firstOrNull()?.let { " inauthor:$it" } ?: ""))
        val url = "https://www.googleapis.com/books/v1/volumes?q=${enc(q)}&maxResults=20&printType=books"
        val json = fetch.get(url, MAX_JSON)?.let { JSONObject(String(it, Charsets.UTF_8)) } ?: return Found()
        return parseGoogleBooks(json)
    }

    companion object {
        const val MAX_AMAZON = 16
        private const val MAX_JSON = 2 * 1024 * 1024
        private const val MAX_PICTURE = 12 * 1024 * 1024

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** Amazon's full-size picture for the edition with ISBN-10 (or ASIN) [id]. */
        fun amazonCover(id: String) = "https://m.media-amazon.com/images/P/$id.01._SCRM_.jpg"

        /** Google Books' large cover for the book with [id]. */
        fun googleCover(id: String) = "https://books.google.com/books/content?id=$id&printsec=frontcover&img=1&zoom=0&source=gbs_api"

        /** Open Library's large cover with [id]. */
        fun openLibraryCover(id: Long) = "https://covers.openlibrary.org/b/id/$id-L.jpg"

        /** An ISBN-10 (or Amazon's own ID) for [raw]: as it is, or from an ISBN-13 starting 978. Null if it's neither. */
        fun isbn10(raw: String): String? {
            val s = raw.uppercase().filter { it.isLetterOrDigit() }
            if (s.length == 10 && s.startsWith("B0")) return s
            if (s.length == 10 && s.take(9).all { it.isDigit() } && (s[9].isDigit() || s[9] == 'X')) return s
            if (s.length == 13 && s.startsWith("978") && s.all { it.isDigit() }) {
                val core = s.substring(3, 12)
                val sum = core.mapIndexed { i, ch -> (10 - i) * (ch - '0') }.sum()
                val check = (11 - sum % 11) % 11
                return core + if (check == 10) "X" else check.toString()
            }
            return null
        }

        internal fun parseOpenLibrary(json: JSONObject): Found {
            val docs = json.optJSONArray("docs") ?: return Found()
            val covers = ArrayList<CoverCandidate>()
            val isbns = ArrayList<String>()
            for (i in 0 until docs.length()) {
                val d = docs.optJSONObject(i) ?: continue
                val id = d.optLong("cover_i", 0L)
                if (id > 0) covers += CoverCandidate(CoverSource.OPEN_LIBRARY, openLibraryCover(id))
                // The editions of the best matches, for their covers on Amazon.
                if (i < 3) d.optJSONArray("isbn")?.let { a -> for (k in 0 until minOf(a.length(), 8)) isbns += a.optString(k) }
            }
            return Found(covers, isbns)
        }

        internal fun parseGoogleBooks(json: JSONObject): Found {
            val items = json.optJSONArray("items") ?: return Found()
            val covers = ArrayList<CoverCandidate>()
            val isbns = ArrayList<String>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val info = item.optJSONObject("volumeInfo") ?: continue
                val id = item.optString("id").takeIf { it.isNotEmpty() } ?: continue
                if (info.optJSONObject("imageLinks") != null) covers += CoverCandidate(CoverSource.GOOGLE_BOOKS, googleCover(id))
                info.optJSONArray("industryIdentifiers")?.let { a ->
                    for (k in 0 until a.length()) a.optJSONObject(k)?.optString("identifier")?.let { isbns += it }
                }
            }
            return Found(covers, isbns.take(12))
        }

        private fun sha1(b: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

        /** A picture at [url] (from a web page): fetched, or decoded if it's in the address itself. */
        fun picture(url: String, fetch: Fetcher = HttpFetcher): ByteArray? {
            if (url.startsWith("data:")) {
                val comma = url.indexOf(',')
                if (comma < 0 || !url.substring(0, comma).endsWith(";base64")) return null
                return runCatching { android.util.Base64.decode(url.substring(comma + 1), android.util.Base64.DEFAULT) }.getOrNull()
            }
            if (!url.startsWith("http")) return null
            return runCatching { fetch.get(url, MAX_PICTURE) }.onFailure { AppLog.w("Covers", "Couldn't fetch a picture", it) }.getOrNull()
        }
    }
}
