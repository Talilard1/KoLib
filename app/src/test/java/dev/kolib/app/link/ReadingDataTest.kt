package dev.kolib.app.link

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.kolib.app.reader.ReaderStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Places in books, bookmarks and highlights from two devices, put together. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingDataTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val base = File(context.cacheDir, "reading-" + UUID.randomUUID())

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun dir(name: String) = File(base, name).apply { mkdirs() }

    private fun position(at: Long, path: String = "c1.xhtml", book: Double = 0.5) =
        JSONObject().put("path", path).put("anchor", "p:3").put("progress", 0.25).put("book", book).put("at", at)

    private fun note(id: String, modified: Long, text: String) =
        JSONObject().put("id", id).put("path", "c1.xhtml").put("start", "a").put("end", "b").put("text", text).put("modified", modified)

    private fun mark(path: String, anchor: String) = JSONObject().put("path", path).put("anchor", anchor).put("progress", 0.1).put("at", 1L)

    @Test
    fun theLaterOfEachPartIsTakenWhicheverSideItsOn() {
        val early = JSONObject().put("text", position(1000))
        val late = JSONObject().put("text", position(2000, "c4.xhtml"))
        assertEquals(2000L, ReadingData.textAt(ReadingData.mergePlace(early, late)))
        assertEquals(2000L, ReadingData.textAt(ReadingData.mergePlace(late, early)))

        // Finished later than the other's place: finished. Earlier: the place.
        val finished = JSONObject().put("finished", 1500L)
        assertTrue(ReadingData.mergePlace(early, finished).has("finished"))
        assertFalse(ReadingData.mergePlace(late, finished).has("finished"))
        assertEquals("c4.xhtml", ReadingData.mergePlace(finished, late).getJSONObject("text").getString("path"))

        // The page in the PDF is a part of its own.
        val pdf = JSONObject().put("page", 40).put("pages", 300).put("at", 500L).put("asText", false)
        val withPdf = JSONObject().put("text", position(1000)).put("pdf", pdf)
        val merged = ReadingData.mergePlace(withPdf, late)
        assertEquals(2000L, ReadingData.textAt(merged))
        assertEquals(40, merged.getJSONObject("pdf").getInt("page"))
        assertEquals(2000L, ReadingData.latest(merged))

        // Nothing on either side: nothing.
        assertEquals(0, ReadingData.mergePlace(JSONObject(), JSONObject()).length())
        // The same time on both, different places: both sides still come to the same one.
        val a = JSONObject().put("text", position(3000, "a.xhtml"))
        val b = JSONObject().put("text", position(3000, "b.xhtml"))
        assertTrue(ReadingData.samePlace(ReadingData.mergePlace(a, b), ReadingData.mergePlace(b, a)))
    }

    @Test
    fun aPlaceIsPutWithoutLosingBookmarksOrHighlights() {
        val d = dir("book")
        File(d, "reader.json").writeText(
            JSONObject().put("position", position(1000)).put("bookmarks", JSONArray().put(mark("c1.xhtml", "x")))
                .put("annotations", JSONArray().put(note("n1", 10, "Herodotus"))).toString()
        )
        ReadingData.applyPlace(d, JSONObject().put("finished", 4000L).put("pdf", JSONObject().put("page", 7).put("pages", 10).put("at", 3000L)))
        val o = JSONObject(File(d, "reader.json").readText())
        assertTrue(o.getBoolean("finished"))
        assertEquals(4000L, o.getLong("finishedAt"))
        assertFalse(o.has("position"))
        assertEquals(1, o.getJSONArray("bookmarks").length())
        assertEquals(1, o.getJSONArray("annotations").length())
        assertEquals(7, ReaderStore.pdfPlace(d)!!.page)
        // How far through, for the library: finished, as that's the later.
        assertTrue(ReaderStore.readHereOf(d)!!.finished)

        ReadingData.applyPlace(d, JSONObject().put("text", position(5000, "c9.xhtml", book = 0.9)))
        val after = ReaderStore.readHereOf(d)!!
        assertFalse(after.finished)
        assertEquals(0.9f, after.progress, 0.001f)
    }

    @Test
    fun bookmarksAndHighlightsFromBothAreKept() {
        val d = dir("book")
        File(d, "reader.json").writeText(
            JSONObject().put("position", position(1000))
                .put("bookmarks", JSONArray().put(mark("c1.xhtml", "x")).put(mark("c2.xhtml", "y")))
                .put("annotations", JSONArray().put(note("n1", 10, "mine")).put(note("n2", 50, "changed here last"))).toString()
        )
        val theirs = JSONObject().put(
            "reader",
            JSONObject().put("position", position(2000, "c3.xhtml"))
                .put("bookmarks", JSONArray().put(mark("c2.xhtml", "y")).put(mark("c5.xhtml", "z")))
                .put("annotations", JSONArray().put(note("n2", 20, "older")).put(note("n3", 30, "theirs"))),
        ).put("pdf", JSONObject().put("page", 3).put("pages", 9).put("at", 100L))
        ReadingData.mergeInto(d, theirs)
        val o = JSONObject(File(d, "reader.json").readText())
        assertEquals("c3.xhtml", o.getJSONObject("position").getString("path"))
        val marks = o.getJSONArray("bookmarks").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("path") } }
        assertEquals(listOf("c1.xhtml", "c2.xhtml", "c5.xhtml"), marks)
        val notes = o.getJSONArray("annotations").let { a -> (0 until a.length()).associate { a.getJSONObject(it).getString("id") to a.getJSONObject(it).getString("text") } }
        assertEquals(mapOf("n1" to "mine", "n2" to "changed here last", "n3" to "theirs"), notes)
        assertEquals(3, ReaderStore.pdfPlace(d)!!.page)
    }

    @Test
    fun bookmarksHighlightsAndDeletionsGoWithPlacesBetweenDevices() {
        val mine = dir("mine")
        val theirs = dir("theirs")
        val kept = mark("c2.xhtml", "y")
        val gone = mark("c1.xhtml", "x")
        val goneId = dev.kolib.app.reader.ReaderPosition.markIdOf("c1.xhtml", "x")
        // This device deleted n1 and a bookmark; the other added n3 since they last met.
        File(mine, "reader.json").writeText(
            JSONObject().put("position", position(1000)).put("bookmarks", JSONArray().put(kept))
                .put("annotations", JSONArray().put(note("n2", 20, "both")))
                .put("deleted", JSONObject().put("n1", 900L).put(goneId, 950L)).toString()
        )
        File(theirs, "reader.json").writeText(
            JSONObject().put("position", position(1200, "c3.xhtml")).put("bookmarks", JSONArray().put(gone).put(kept))
                .put("annotations", JSONArray().put(note("n1", 10, "old")).put(note("n2", 20, "both")).put(note("n3", 30, "new")))
                .put("kobo", JSONObject().put("n3", JSONObject().put("k", JSONObject().put("added", "a").put("at", 5L)))).toString()
        )
        assertFalse(ReadingData.samePlace(ReadingData.placeOf(mine), ReadingData.placeOf(theirs)))
        // As the sync of places does it: put together once, and that given to both.
        val merged = ReadingData.mergePlace(ReadingData.placeOf(mine), ReadingData.placeOf(theirs))
        ReadingData.applyPlace(mine, merged)
        ReadingData.applyPlace(theirs, merged)
        for (d in listOf(mine, theirs)) {
            val o = JSONObject(File(d, "reader.json").readText())
            assertEquals("c3.xhtml", o.getJSONObject("position").getString("path"))
            val notes = o.getJSONArray("annotations").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }
            assertEquals(setOf("n2", "n3"), notes)
            val marks = o.getJSONArray("bookmarks").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("path") } }
            assertEquals(listOf("c2.xhtml"), marks)
            assertTrue(o.getJSONObject("deleted").has("n1"))
            assertTrue(o.getJSONObject("deleted").has(goneId))
            assertTrue(o.getJSONObject("kobo").has("n3"))
        }
        // Both the same now: the next sync has nothing to do.
        assertTrue(ReadingData.samePlace(ReadingData.placeOf(mine), ReadingData.placeOf(theirs)))
    }

    @Test
    fun aBookNeverOpenedTakesTheOthersReadingAsItIs() {
        val d = dir("new")
        ReadingData.mergeInto(d, JSONObject().put("reader", JSONObject().put("finished", true).put("finishedAt", 900L).put("annotations", JSONArray().put(note("n1", 1, "a")))))
        val here = ReaderStore.readHereOf(d)!!
        assertTrue(here.finished)
        assertEquals(900L, here.at)
        // Nothing at all to put together: nothing written.
        val empty = dir("empty")
        ReadingData.mergeInto(empty, JSONObject())
        assertFalse(File(empty, "reader.json").exists())
        assertNull(ReaderStore.readHereOf(empty))
    }

    @Test
    fun placesSavedBeforeTheyHadTimesUseTheFilesTime() {
        val d = dir("old")
        val f = File(d, "reader.json")
        f.writeText(JSONObject().put("position", JSONObject().put("path", "c1.xhtml").put("progress", 0.5)).toString())
        f.setLastModified(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, ReadingData.textAt(ReadingData.placeOf(d)))
    }

    @Test
    fun booksAreMatchedByIdOrElseByTitleAndAuthor() {
        fun at(t: Long) = JSONObject().put("text", position(t))
        val mine = listOf(
            BookPlace("m1", "Dune", "Frank Herbert", at(100)),
            BookPlace("m2", "Emma", "Jane Austen", JSONObject()),
            // Two of the same on this side: neither is taken for the other's by name.
            BookPlace("m3", "Poems", "Anon", JSONObject()),
            BookPlace("m4", "Poems", "Anon", JSONObject()),
        )
        val theirs = listOf(
            BookPlace("m1", "Dune (retitled)", "Frank Herbert", at(50)),
            BookPlace("t2", "  emma ", "jane austen", at(70)),
            BookPlace("t3", "Poems", "Anon", at(80)),
        )
        val (forMe, forThem) = BookPlace.sync(mine, theirs)
        assertEquals(listOf("m2"), forMe.map { it.id })
        assertEquals(70L, ReadingData.textAt(forMe[0].state))
        assertEquals(listOf("m1"), forThem.map { it.id })
        assertEquals(100L, ReadingData.textAt(forThem[0].state))
    }
}
