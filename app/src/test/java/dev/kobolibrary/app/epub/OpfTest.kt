package dev.kobolibrary.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OpfTest {

    private val now = ZonedDateTime.of(2026, 11, 13, 4, 5, 6, 0, ZoneOffset.UTC)

    @Test
    fun readsCalibreEpub2() {
        val m = OpfDocument(TestEpubs.CALIBRE_EPUB2_OPF).readMetadata()
        assertEquals("The Histories", m.title)
        assertEquals("Histories, The", m.titleSort)
        assertEquals(listOf("Herodotus"), m.authors) // the translator is not an author
        assertEquals("Penguin Classics", m.series)
        assertEquals(3.0, m.seriesIndex!!, 0.0)
        assertEquals(listOf("History", "Classics"), m.tags)
        assertEquals("Penguin Classics", m.publisher)
        assertEquals("2003-02-27", m.published)
        assertEquals("en", m.language)
        assertEquals("9780140449082", m.isbn)
        assertEquals("<p>The first great work of <b>history</b>.</p>", m.description)
        assertEquals(8, m.rating)
        assertEquals("0d8f2b6a-1c2d-4e5f-8a9b-0c1d2e3f4a5b", m.identifier)
    }

    @Test
    fun readsEpub3Refines() {
        val m = OpfDocument(TestEpubs.EPUB3_OPF).readMetadata()
        assertEquals("Meditations", m.title)
        assertEquals(listOf("Marcus Aurelius"), m.authors)
        assertEquals("Aurelius, Marcus", m.authorSorts["Marcus Aurelius"])
        assertEquals("Modern Library", m.series)
        assertEquals(12.0, m.seriesIndex!!, 0.0)
        assertEquals("2002-05-07", m.published)
        assertNull(m.isbn)
    }

    @Test
    fun writesEpub2AndReadsBack() {
        val opf = OpfDocument(TestEpubs.CALIBRE_EPUB2_OPF)
        val edited = opf.readMetadata().copy(
            title = "Histories & Inquiries",
            titleSort = null,
            authors = listOf("Herodotus", "Tom Holland"),
            series = "Penguin Black Classics",
            seriesIndex = 4.5,
            tags = listOf("Ancient History", "Greece"),
            publisher = "Penguin",
            published = "2013-10-01",
            isbn = "978-0-14-310754-0",
            description = "<p>Holland's <i>new</i> translation.</p>",
            rating = 10,
        )
        val out = opf.withMetadata(edited, now)
        val back = OpfDocument(out).readMetadata()
        assertEquals("Histories & Inquiries", back.title)
        assertEquals("Histories & Inquiries", Names.titleSort(back.title)) // no leading article
        assertEquals(listOf("Herodotus", "Tom Holland"), back.authors)
        assertEquals("Holland, Tom", back.authorSorts["Tom Holland"])
        assertEquals("Herodotus", back.authorSorts["Herodotus"]) // kept the book's own sort name
        assertEquals("Penguin Black Classics", back.series)
        assertEquals(4.5, back.seriesIndex!!, 0.0)
        assertEquals(listOf("Ancient History", "Greece"), back.tags)
        assertEquals("Penguin", back.publisher)
        assertEquals("2013-10-01", back.published)
        assertEquals("9780143107540", back.isbn)
        assertEquals("<p>Holland's <i>new</i> translation.</p>", back.description)
        assertEquals(10, back.rating)

        // Everything the app doesn't manage is still there, untouched.
        assertTrue(out.contains("<dc:creator opf:role=\"trl\" opf:file-as=\"Rawlinson, George\">George Rawlinson</dc:creator>"))
        assertTrue(out.contains("<dc:identifier opf:scheme=\"uuid\" id=\"uuid_id\">0d8f2b6a-1c2d-4e5f-8a9b-0c1d2e3f4a5b</dc:identifier>"))
        assertTrue(out.contains("<meta name=\"calibre:timestamp\" content=\"2023-01-01T00:00:00+00:00\"/>"))
        assertTrue(out.contains("<!-- a comment the app must keep -->"))
        assertTrue(out.contains("<meta name=\"cover\" content=\"cover\"/>"))
        assertTrue(out.endsWith("</package>\n"))
        // Old values are gone, not duplicated.
        assertFalse(out.contains("Penguin Classics\""))
        assertEquals(1, Regex("<dc:title>").findAll(out).count())
        assertEquals(1, Regex("calibre:series\"").findAll(out).count())
        // The title stayed first and the authors stayed before the translator.
        assertTrue(out.indexOf("<dc:title>") < out.indexOf("<dc:creator"))
        assertTrue(out.indexOf(">Herodotus</dc:creator>") < out.indexOf("George Rawlinson"))
        // The result is still well-formed for our parser.
        assertEquals("package", Xml.parse(out).root.localName)
    }

    @Test
    fun writesEpub3KeepingSubtitleIllustratorAndIdentifier() {
        val opf = OpfDocument(TestEpubs.EPUB3_OPF)
        val out = opf.withMetadata(
            opf.readMetadata().copy(title = "Meditations (Hays)", authors = listOf("Marcus Aurelius", "Gregory Hays"), series = null, tags = listOf("Stoicism"), isbn = "0812968255"),
            now,
        )
        val back = OpfDocument(out).readMetadata()
        assertEquals("Meditations (Hays)", back.title)
        assertEquals(listOf("Marcus Aurelius", "Gregory Hays"), back.authors)
        assertEquals("Aurelius, Marcus", back.authorSorts["Marcus Aurelius"])
        assertNull(back.series)
        assertEquals(listOf("Stoicism"), back.tags)
        assertEquals("0812968255", back.isbn)
        // The main title was edited in place; the subtitle and its refines survive.
        assertTrue(out.contains("<dc:title id=\"t1\">Meditations (Hays)</dc:title>"))
        assertTrue(out.contains("<dc:title id=\"t2\">A New Translation</dc:title>"))
        assertTrue(out.indexOf("id=\"t1\"") < out.indexOf("id=\"t2\""))
        assertTrue(out.contains("<meta refines=\"#t2\" property=\"title-type\">subtitle</meta>"))
        // The illustrator stays; the series and its refines are gone.
        assertTrue(out.contains("<dc:creator id=\"ill\">Some Illustrator</dc:creator>"))
        assertFalse(out.contains("Modern Library"))
        assertFalse(out.contains("#s1"))
        // Unique identifier untouched; modified stamp updated once.
        assertTrue(out.contains("<dc:identifier id=\"pub-id\">urn:uuid:12345678-1234-1234-1234-123456789abc</dc:identifier>"))
        assertEquals(1, Regex("dcterms:modified").findAll(out).count())
        assertTrue(out.contains(">2026-11-13T04:05:06Z<"))
        // Ids are unique.
        val ids = Regex("id=\"([^\"]+)\"").findAll(out).map { it.groupValues[1] }.toList()
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("    ", Regex("\n( +)<dc:creator id=\"creator").find(out)?.groupValues?.get(1)?.take(4))
    }

    @Test
    fun declaresMissingNamespacesAndHandlesEmptyMetadata() {
        val bare = """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata><dc:identifier xmlns:dc="http://purl.org/dc/elements/1.1/" id="id">x</dc:identifier></metadata><manifest/><spine/></package>"""
        val out = OpfDocument(bare).withMetadata(BookMetadata(title = "New", authors = listOf("Ann Author")), now)
        assertTrue(out.contains("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">"))
        val back = OpfDocument(out).readMetadata()
        assertEquals("New", back.title)
        assertEquals(listOf("Ann Author"), back.authors)
        assertEquals("Author, Ann", back.authorSorts["Ann Author"])
    }

    @Test
    fun normalizesDates() {
        assertEquals("2005-07-17", OpfDocument.normalizeDate("2005-07-17T14:00:00+00:00"))
        assertEquals("1998", OpfDocument.normalizeDate("1998"))
        assertEquals("1998-03", OpfDocument.normalizeDate("1998-3"))
        assertNull(OpfDocument.normalizeDate("0101-01-01T00:00:00+00:00"))
        assertNull(OpfDocument.normalizeDate("unknown"))
    }

    @Test
    fun sortNamesAndIsbns() {
        assertEquals("Doyle, Arthur Conan", Names.authorSort("Arthur Conan Doyle"))
        assertEquals("King, Martin Luther, Jr.", Names.authorSort("Martin Luther King Jr."))
        assertEquals("Tolkien, J. R. R.", Names.authorSort("J. R. R. Tolkien"))
        assertEquals("Herodotus", Names.authorSort("Herodotus"))
        assertEquals("Unknown", Names.authorSort("Unknown"))
        assertEquals("Odyssey, The", Names.titleSort("The Odyssey"))
        assertEquals("Game of Thrones, A", Names.titleSort("A Game of Thrones"))
        assertEquals("Theory of Everything", Names.titleSort("Theory of Everything"))
        assertTrue(Isbn.isValid("978-0-14-044908-2"))
        assertTrue(Isbn.isValid("0812968255"))
        assertTrue(Isbn.isValid("080442957X"))
        assertFalse(Isbn.isValid("9780140449083"))
        assertEquals("9780140449082", Isbn.normalize("urn:isbn:978-0-14-044908-2"))
    }
}
