package dev.kolib.app

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
    fun aCountTakesTheRightFormOfTheWordInRussian() {
        L10n.use("ru", mapOf("{0} books" to "{0} {0:книга|книги|книг}", "{0} books sent." to "{0} {0:книга отправлена|книги отправлены|книг отправлено}."))
        assertEquals("21 книга", tr("{0} books", 21))
        assertEquals("2 книги", tr("{0} books", 2))
        assertEquals("24 книги", tr("{0} books", 24L))
        assertEquals("5 книг", tr("{0} books", 5))
        assertEquals("11 книг", tr("{0} books", 11))
        assertEquals("14 книг", tr("{0} books", 14))
        assertEquals("112 книг", tr("{0} books", 112))
        assertEquals("0 книг", tr("{0} books", 0))
        // A number already written out, with its thousands spaced.
        assertEquals("1 234 книги", tr("{0} books", "1 234"))
        assertEquals("3 книги отправлены.", tr("{0} books sent.", 3))
        assertEquals("101 книга отправлена.", tr("{0} books sent.", 101))
    }

    /** How many things a clean-up changed ("5 blank lines") is said with the count's own form too, never left as written in the table. */
    @Test
    fun cleanUpCountsTakeTheRightFormOfTheWord() {
        L10n.use("ru", mapOf("{0} blank lines" to "{0} {0:пустая строка|пустые строки|пустых строк}", "{0} fonts" to "{0} {0:шрифт|шрифта|шрифтов}"))
        val blank = dev.kolib.app.epub.Cleanups.Kind.BLANK_LINES
        assertEquals("3 пустые строки", blank.describe(3))
        assertEquals("5 пустых строк", blank.describe(5))
        assertEquals("21 пустая строка", blank.describe(21))
        assertEquals("12 шрифтов", dev.kolib.app.epub.Cleanups.Kind.STRIP_FONTS.describe(12))
        for (kind in dev.kolib.app.epub.Cleanups.Kind.entries) for (n in listOf(2, 5, 21, 1234)) {
            assertEquals(-1, kind.describe(n).indexOf('{'))
        }
        L10n.use("en", emptyMap())
        assertEquals("5 blank lines", blank.describe(5))
    }

    @Test
    fun otherLanguagesHaveTwoForms() {
        L10n.use("fr", mapOf("{0} pages" to "{0} {0:page|pages}"))
        assertEquals("0 page", tr("{0} pages", 0))
        assertEquals("2 pages", tr("{0} pages", 2))
        L10n.use("de", mapOf("{0} pages" to "{0} {0:Seite|Seiten}"))
        assertEquals("1 Seite", tr("{0} pages", 1))
        assertEquals("0 Seiten", tr("{0} pages", 0))
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
