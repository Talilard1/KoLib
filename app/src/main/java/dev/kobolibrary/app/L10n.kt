package dev.kobolibrary.app

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

/** [english] in the language KoLib is shown in, with [values] put in place of {0}, {1}... */
fun tr(english: String, vararg values: Any?): String {
    val text = L10n.lookUp(english)
    if (values.isEmpty() || text.indexOf('{') < 0) return text
    val out = StringBuilder(text.length + 16)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '{') {
            val close = text.indexOf('}', i + 1)
            val n = if (close > i + 1 && close - i <= 3) text.substring(i + 1, close).toIntOrNull() else null
            if (n != null && n in values.indices) {
                out.append(values[n].toString())
                i = close + 1
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}
