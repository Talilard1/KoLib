package dev.kolib.app.covers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Finding covers online, with the internet's answers made up here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverSearchTest {

    /** A PNG header saying it's [w] by [h]: all the search reads to know a picture's size. */
    private fun png(w: Int, h: Int, tag: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13))
        out.write("IHDR".toByteArray())
        for (v in listOf(w, h)) out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
        out.write(byteArrayOf(8, 6, 0, 0, 0, tag.toByte()))
        return out.toByteArray()
    }

    private val openLibrary = """{"numFound":2,"docs":[
        {"key":"/works/OL1W","title":"Lord of Chaos","cover_i":182398,"isbn":["9780312854287","1857232623"]},
        {"key":"/works/OL2W","title":"Lord of Chaos (Part 1/2)","cover_i":12305905,"isbn":["2266142119"]}]}"""
    private val googleBooks = """{"kind":"books#volumes","totalItems":2,"items":[
        {"id":"abcDEF12345","volumeInfo":{"title":"Lord of Chaos","authors":["Robert Jordan"],
          "industryIdentifiers":[{"type":"ISBN_13","identifier":"9781250251978"},{"type":"ISBN_10","identifier":"1250251974"}],
          "imageLinks":{"thumbnail":"http://books.google.com/books/content?id=abcDEF12345&printsec=frontcover&img=1&zoom=1"}}},
        {"id":"noCover0001","volumeInfo":{"title":"Lord of Chaos","industryIdentifiers":[{"type":"OTHER","identifier":"UOM:39015"}]}}]}"""

    private val web = mapOf(
        "openlibrary.org/search.json" to openLibrary.toByteArray(),
        "googleapis.com/books" to googleBooks.toByteArray(),
        // Amazon: two editions with covers (one the same picture as Open Library's), the rest a dot.
        "images/P/0312854285.01._SCRM_.jpg" to png(1400, 2135, 1),
        "images/P/1250251974.01._SCRM_.jpg" to png(1000, 1600, 2),
        "covers.openlibrary.org/b/id/182398-L.jpg" to png(1400, 2135, 1),
        "covers.openlibrary.org/b/id/12305905-L.jpg" to png(312, 475, 3),
        "books.google.com/books/content?id=abcDEF12345" to png(800, 1237, 4),
    )
    private val asked = mutableListOf<String>()
    private val fetcher = Fetcher { url, _ ->
        synchronized(asked) { asked += url }
        web.entries.firstOrNull { url.contains(it.key) }?.value ?: if (url.contains("media-amazon")) png(1, 1) else null
    }

    @Test
    fun coversFromAmazonGoogleBooksAndOpenLibrary() = runBlocking {
        val search = CoverSearch(fetcher)
        val candidates = search.candidates("Lord of Chaos", listOf("Robert Jordan"), isbn = "978-0-312-85428-7")
        // The book's own ISBN first, as Amazon knows it; then the editions the other sites list; then their covers.
        assertEquals(CoverCandidate(CoverSource.AMAZON, CoverSearch.amazonCover("0312854285")), candidates.first())
        assertTrue(candidates.any { it.url == CoverSearch.amazonCover("1250251974") })
        assertTrue(candidates.any { it.url == CoverSearch.googleCover("abcDEF12345") })
        assertTrue(candidates.any { it.url == CoverSearch.openLibraryCover(182398) })
        assertTrue("searched by title and author", asked.any { it.contains("title=Lord+of+Chaos") && it.contains("author=Robert+Jordan") })

        val found = mutableListOf<FoundCover>()
        search.search("Lord of Chaos", listOf("Robert Jordan"), "9780312854287") { found += it }
        // Each picture once, with its size; Amazon's missing covers (a dot) left out.
        assertEquals(setOf(1400 to 2135, 1000 to 1600, 800 to 1237, 312 to 475), found.map { it.width to it.height }.toSet())
        assertEquals(4, found.size)
        assertTrue(found.none { it.width < 100 })
    }

    @Test
    fun searchedAsTyped() = runBlocking {
        asked.clear()
        CoverSearch(fetcher).candidates("Lord of Chaos", listOf("Robert Jordan"), null, query = "wheel of time lord of chaos")
        assertTrue(asked.any { it.contains("search.json?q=wheel+of+time+lord+of+chaos") })
        assertTrue(asked.any { it.contains("volumes?q=wheel+of+time+lord+of+chaos") })
    }

    @Test
    fun isbnsAsAmazonKnowsThem() {
        assertEquals("0312854285", CoverSearch.isbn10("978-0-312-85428-7"))
        assertEquals("185723300X", CoverSearch.isbn10("9781857233001"))
        assertEquals("185723300X", CoverSearch.isbn10("185723300x"))
        assertEquals("B00ABCDEFG", CoverSearch.isbn10("B00ABCDEFG"))
        assertNull(CoverSearch.isbn10("9791234567896"))
        assertNull(CoverSearch.isbn10("UOM:39015"))
    }

    @Test
    fun aPictureInAWebPagesAddress() {
        val bytes = png(300, 450)
        val data = "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        assertTrue(bytes.contentEquals(CoverSearch.picture(data, fetcher)))
        assertNull(CoverSearch.picture("javascript:void(0)", fetcher))
    }
}
