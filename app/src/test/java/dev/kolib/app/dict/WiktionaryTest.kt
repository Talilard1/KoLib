package dev.kolib.app.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WiktionaryTest {
    private fun page(name: String) = javaClass.getResourceAsStream("/wiktionary/$name.json")!!.bufferedReader().readText()

    @Test
    fun whatToLookAWordUpAs() {
        assertEquals(listOf("brooded"), Wiktionary.candidates(" brooded, "))
        assertEquals(listOf("Gared", "gared"), Wiktionary.candidates("“Gared”"))
        assertEquals(listOf("Night’s", "night’s", "Night", "night"), Wiktionary.candidates("Night’s"))
        assertEquals(listOf("sooner or later"), Wiktionary.candidates("sooner or later"))
        // A sentence isn't a word to look up.
        assertEquals(emptyList<String>(), Wiktionary.candidates("He had seen the lordlings come and go."))
        assertEquals(emptyList<String>(), Wiktionary.candidates(" … "))
        assertEquals("https://en.wiktionary.org/api/rest_v1/page/definition/sooner_or_later", Wiktionary.url("sooner or later"))
        assertEquals("https://en.wiktionary.org/api/rest_v1/page/definition/caf%C3%A9", Wiktionary.url("café"))
    }

    @Test
    fun anEntryIsReadAsPlainWords() {
        val r = Wiktionary.parse(page("brood"), "brood", "en")!!
        assertEquals("English", r.language)
        assertEquals(listOf("Noun", "Verb", "Adjective"), r.entries.map { it.partOfSpeech })
        assertNull(r.formOf)
        // Labels keep their brackets; links and markup are gone; an empty meaning is passed over.
        assertEquals("(uncountable) The young of any egg-laying creature, especially if produced at the same time.", r.entries[0].senses[1])
        assertEquals(3, r.entries[0].senses.size)
        // Four meanings at most to a part of speech.
        assertEquals(4, r.entries[1].senses.size)
        assertEquals("(intransitive) To dwell upon moodily and at length, mainly alone.", r.entries[1].senses[1])
        assertTrue(r.entries[1].senses[2].contains("\"over\" or \"on\""))
        assertFalse(r.entries.any { e -> e.senses.any { it.contains('<') } })
    }

    /** Where a link meets a semicolon, a colon or the like, no space is left before it ("a pile; a post", not "a pile ; a post"). */
    @Test
    fun noSpaceIsLeftBeforePunctuation() {
        val r = Wiktionary.parse(page("spile"), "spile", "en")!!
        assertEquals("A pile; a post or girder.", r.entries[1].senses[0])
        assertEquals("(obsolete or dialectal, Cumbria, Lancashire) A splinter.", r.entries[0].senses[0])
        assertEquals("plural of spile", Wiktionary.parse(page("spiles"), "spiles", "en")!!.form)
    }

    @Test
    fun theBooksLanguageIsPreferredThenEnglish() {
        assertEquals("Dutch", Wiktionary.parse(page("brood"), "brood", "nl-NL")!!.language)
        assertEquals("English", Wiktionary.parse(page("brood"), "brood", "fr")!!.language)
        assertEquals("English", Wiktionary.parse(page("brood"), "brood", null)!!.language)
        assertNull(Wiktionary.parse("not json", "brood", "en"))
        assertNull(Wiktionary.parse("{}", "brood", "en"))
    }

    @Test
    fun aWordThatsAFormOfAnotherIsLookedUpUnderIt() {
        val asked = ArrayList<String>()
        val r = Wiktionary.lookUp("brooded", "en") { url ->
            asked += url.substringAfterLast('/')
            when (asked.last()) {
                "brooded" -> page("brooded")
                "brood" -> page("brood")
                else -> null
            }
        }!!
        assertEquals(listOf("brooded", "brood"), asked)
        assertEquals("brooded", r.word)
        assertEquals("brood", r.formOf)
        assertEquals("simple past and past participle of brood", r.form)
        // Brood's meanings, the verb's first (brooded is a form of the verb), and then brooded's own (having broods).
        assertEquals(listOf("Verb", "Noun", "Adjective", "Adjective"), r.entries.map { it.partOfSpeech })
        assertEquals(listOf("Having broods"), r.entries.last().senses)
    }

    @Test
    fun aWordAtTheStartOfASentenceIsFoundInSmallLetters() {
        val asked = ArrayList<String>()
        val r = Wiktionary.lookUp("Brood", "en") { url ->
            asked += url.substringAfterLast('/')
            if (asked.last() == "brood") page("brood") else null
        }!!
        assertEquals(listOf("Brood", "brood"), asked)
        assertEquals("brood", r.word)
        assertNull(r.formOf)
        assertNull(Wiktionary.lookUp("zzyzx", "en") { null })
        assertNull(Wiktionary.lookUp("A whole sentence is not looked up at all.", "en") { throw AssertionError("asked") })
    }
}
