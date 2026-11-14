package dev.kobolibrary.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The words in other languages: putting values in, falling back to English, and the tables themselves. */
class L10nTest {

    @After
    fun backToEnglish() = L10n.use("en", emptyMap())

    @Test
    fun valuesGoWhereTheTranslationPutsThem() {
        L10n.use("ja", mapOf("{0} of {1}" to "{1}中 {0}", "Page {0}" to "{0} ページ"))
        assertEquals("30 冊中 10", tr("{0} of {1}", 10, "30 冊"))
        assertEquals("12 ページ", tr("Page {0}", 12))
    }

    @Test
    fun textWithNoTranslationStaysEnglish() {
        L10n.use("fr", mapOf("Library" to "Bibliothèque"))
        assertEquals("Bibliothèque", tr("Library"))
        assertEquals("Send 3 books to the Kobo", tr("Send {0} books to the Kobo", 3))
        assertEquals("Not translated", tr("Not translated"))
    }

    @Test
    fun aWordCanBeSaidDifferentlyWhereItsMeantDifferently() {
        L10n.use("fr", mapOf("Read" to "Lire", "badge|Read" to "Lu"))
        assertEquals("Lire", tr("Read"))
        assertEquals("Lu", trc("badge", "Read"))
        assertEquals("Read", trc("somewhere else", "Read"))
        L10n.use("en", emptyMap())
        assertEquals("Read", trc("badge", "Read"))
    }

    @Test
    fun bracesThatArentPlaceholdersAreLeftAlone() {
        assertEquals("a {x} {5} b", tr("a {x} {5} {0}", "b"))
        assertEquals("{0}", tr("{0}"))
    }

    // ---- the tables -------------------------------------------------------------------------------

    private val folder = File("src/main/assets/i18n")

    private fun tables(): Map<String, Map<String, String>> =
        AppLanguage.supported.filter { it != "en" }.associateWith { lang -> readTable(File(folder, "$lang.json")) }

    @Test
    fun everyLanguageHasAFullTable() {
        val all = tables()
        val most = all.values.maxOf { it.size }
        for ((lang, table) in all) {
            // Text that's the same in English isn't written down, so the tables differ a little in size.
            assertTrue("$lang has only ${table.size} of about $most", table.size > most * 0.9)
        }
    }

    @Test
    fun translationsKeepEveryPlaceholderAndLineBreak() {
        val placeholder = Regex("""\{\d}""")
        for ((lang, table) in tables()) {
            for ((english, words) in table) {
                val where = "$lang: \"$english\" → \"$words\""
                assertTrue(where, words.isNotBlank())
                assertEquals(where, placeholder.findAll(english).map { it.value }.toSortedSet(), placeholder.findAll(words).map { it.value }.toSortedSet())
                assertEquals(where, english.count { it == '\n' }, words.count { it == '\n' })
                assertEquals("$where: printf-style values", english.contains('%') && Regex("%[,.\\d]*[ds]").containsMatchIn(english), Regex("%[,.\\d]*[ds]").containsMatchIn(words))
            }
        }
    }

    @Test
    fun searchWordsStayAsTheyAreTyped() {
        // The search understands author:, tag: and so on only in English, so the help must show them that way.
        for ((lang, table) in tables()) {
            for ((english, words) in table) {
                if (!english.startsWith("Type words to search")) continue
                for (field in listOf("author:tolkien", "title", "collection", "publisher", "isbn")) {
                    assertTrue("$lang: $field in \"$words\"", words.contains(field))
                }
            }
        }
    }

    /** The tables are flat JSON objects of strings, which is all this reads. */
    private fun readTable(file: File): Map<String, String> {
        val s = file.readText()
        val out = LinkedHashMap<String, String>()
        var i = 0
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun string(): String {
            check(s[i] == '"') { "Expected a string at $i in ${file.name}" }
            i++
            val b = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val c = s[i]) {
                        'n' -> b.append('\n')
                        't' -> b.append('\t')
                        'r' -> b.append('\r')
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000c')
                        'u' -> { b.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> b.append(c)
                    }
                } else {
                    b.append(s[i])
                }
                i++
            }
            i++
            return b.toString()
        }
        skip(); check(s[i] == '{'); i++
        while (true) {
            skip()
            if (s[i] == '}') break
            val key = string()
            skip(); check(s[i] == ':'); i++; skip()
            out[key] = string()
            skip()
            if (s[i] == ',') i++
        }
        return out
    }
}
