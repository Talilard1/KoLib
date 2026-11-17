package dev.kolib.app

import android.content.Context
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * KoLib's words in the language it's shown in. The app is written in English; each other language
 * has a table (assets/i18n/<language>.json) from the English to its own words. Text with a {0}, {1}...
 * in it has the values put in where they go, which can be in another order in another language.
 *
 * The table is read when KoLib starts. Choosing another language starts KoLib again (see
 * [AppLanguage.choose]), so everything is in the new language, even what was made only once.
 */
object L10n {
    @Volatile
    private var words: Map<String, String> = emptyMap()

    /** The language the words are in. */
    @Volatile
    var language: String = "en"
        private set

    /** Reads the table for the language KoLib is shown in (none for English). */
    fun load(context: Context) {
        val lang = AppLanguage.current(context)
        language = lang
        words = if (lang == "en") emptyMap() else runCatching { read(context, lang) }.getOrDefault(emptyMap())
    }

    /** For tests: the words of a language, as they'd be read from a table. */
    internal fun use(lang: String, table: Map<String, String>) {
        language = lang
        words = table
    }

    private fun read(context: Context, lang: String): Map<String, String> {
        val out = HashMap<String, String>(2048)
        context.assets.open("i18n/$lang.json").use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) out[r.nextName()] = r.nextString()
                r.endObject()
            }
        }
        return out
    }

    internal fun lookUp(english: String): String = words[english] ?: english

    /**
     * Which of a word's forms goes with [value], a count, in the language KoLib is shown in: Russian has
     * three (1 книга, 2 книги, 5 книг; 21 книга), French and Portuguese two (0 and 1 are one), English and
     * most others two (1, and the rest), Japanese one. [forms] are the language's, in that order.
     */
    internal fun formFor(value: Any?, forms: List<String>): String {
        if (forms.isEmpty()) return ""
        val n: Long? = when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Number -> value.toDouble().takeIf { it % 1.0 == 0.0 }?.toLong()
            else -> value?.toString()?.filter { !it.isWhitespace() && it != ',' && it != '\u202F' && it != '\u00A0' }?.toLongOrNull()
        }?.let { kotlin.math.abs(it) }
        val i = when (language) {
            "ru", "uk", "be" -> when {
                // A fraction takes the second form: 1,5 книги.
                n == null -> 1
                n % 10 == 1L && n % 100 != 11L -> 0
                n % 10 in 2L..4L && n % 100 !in 12L..14L -> 1
                else -> 2
            }
            "ja", "zh", "ko" -> 0
            "fr", "pt-BR", "pt" -> if (n != null && n <= 1) 0 else 1
            else -> if (n == 1L) 0 else 1
        }
        return forms[i.coerceAtMost(forms.size - 1)]
    }

    /**
     * A label ("Title", "Last read") as it's written in the middle of a sentence ("sorted by title"):
     * in small letters, except in German, where nouns keep their capital.
     */
    fun midSentence(label: String): String =
        if (language == "de") label else label.lowercase(java.util.Locale.forLanguageTag(language))
}

/** [english] in the language KoLib is shown in. */
fun tr(english: String): String = L10n.lookUp(english)

/**
 * [english] as it's meant in [context], for a word that's said differently in other languages depending
 * on what it's for ("Read" on a button, and on a badge on a book that's been read). Its key is "context|english".
 */
fun trc(context: String, english: String): String {
    val key = "$context|$english"
    val found = L10n.lookUp(key)
    return if (found === key || found == key) english else found
}

/**
 * [english] in the language KoLib is shown in, with [values] put in place of {0}, {1}... A translation can
 * also say {0:книга|книги|книг}: the form of the word that goes with the count in {0} (see [L10n.formFor]).
 */
fun tr(english: String, vararg values: Any?): String {
    val text = L10n.lookUp(english)
    if (values.isEmpty() || text.indexOf('{') < 0) return text
    val out = StringBuilder(text.length + 16)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '{') {
            val close = text.indexOf('}', i + 1)
            if (close > i + 1) {
                val inside = text.substring(i + 1, close)
                val colon = inside.indexOf(':')
                val number = if (colon < 0) inside else inside.substring(0, colon)
                val n = if (number.length in 1..2) number.toIntOrNull() else null
                if (n != null && n in values.indices) {
                    out.append(if (colon < 0) values[n].toString() else L10n.formFor(values[n], inside.substring(colon + 1).split('|')))
                    i = close + 1
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}
