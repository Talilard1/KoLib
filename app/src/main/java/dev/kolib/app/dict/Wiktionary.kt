package dev.kolib.app.dict

import dev.kolib.app.epub.EpubText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** A word's meanings under one part of speech ("Verb", "Noun"). */
data class DictEntry(val partOfSpeech: String, val senses: List<String>)

/**
 * What the dictionary says about [word]. [formOf]: the word it was looked up under when [word] is only a form
 * of it ("brooded" → "brood"), and [form] says which form ("simple past and past participle of brood").
 */
data class DictResult(val word: String, val language: String, val entries: List<DictEntry>, val formOf: String? = null, val form: String? = null)

/**
 * Wiktionary, the free dictionary (en.wiktionary.org): words of every language, explained in English. Asked
 * over the internet when a word is looked up; nothing but the word is sent.
 */
object Wiktionary {
    const val AGENT = "KoLib (https://github.com/Talilard1/KoLib; e-book reader, dictionary look-up)"
    /** How an address's text is fetched (tests put their own pages here). */
    @Volatile
    var fetcher: (String) -> String? = ::get

    private const val MOST_SENSES = 4
    private const val MOST_ENTRIES = 4

    fun url(word: String): String = "https://en.wiktionary.org/api/rest_v1/page/definition/" + URLEncoder.encode(word.replace(' ', '_'), "UTF-8").replace("+", "%20")

    /** The page a reader can open for [word]. */
    fun page(word: String): String = "https://en.wiktionary.org/wiki/" + URLEncoder.encode(word.replace(' ', '_'), "UTF-8").replace("+", "%20")

    /**
     * What to look [text] up as, most likely first: as it's written without the punctuation around it, then in
     * small letters (a word that starts a sentence), then without a possessive's ending.
     */
    fun candidates(text: String): List<String> {
        val t = text.trim().replace(Regex("\\s+"), " ").trim { !it.isLetterOrDigit() }
        if (t.isEmpty() || t.length > 60 || t.count { it == ' ' } > 3) return emptyList()
        val out = LinkedHashSet<String>()
        out += t
        out += t.lowercase()
        val plain = t.replace('’', '\'')
        if (plain.endsWith("'s") || plain.endsWith("s'")) {
            val stem = plain.removeSuffix("'s").removeSuffix("'")
            out += stem
            out += stem.lowercase()
        }
        return out.toList()
    }

    private val formLink = Regex("""form-of-definition-link.*?href="/wiki/([^"#?]+)""")

    private fun plain(html: String): String = EpubText.plainText(html).replace(Regex("\\s+"), " ").replace(" ,", ",").replace(" .", ".").replace("( ", "(").replace(" )", ")").trim()

    /**
     * Reads Wiktionary's answer for [word]: the entries for [language] (a code such as "en" or "fr") if it has
     * them, or else English's, or else the first language's. Null if there's nothing to show.
     */
    fun parse(json: String, word: String, language: String?): DictResult? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val want = language?.substringBefore('-')?.lowercase()
        val key = listOfNotNull(want, "en").firstOrNull { o.has(it) } ?: o.keys().asSequence().firstOrNull { o.optJSONArray(it) != null } ?: return null
        val list = o.optJSONArray(key) ?: return null
        val entries = ArrayList<DictEntry>()
        var languageName = ""
        var lemma: String? = null
        var form: String? = null
        var onlyForms = true
        for (i in 0 until list.length()) {
            val e = list.optJSONObject(i) ?: continue
            languageName = languageName.ifEmpty { e.optString("language") }
            val defs = e.optJSONArray("definitions") ?: continue
            val senses = ArrayList<String>()
            for (j in 0 until defs.length()) {
                val html = defs.optJSONObject(j)?.optString("definition").orEmpty()
                val text = plain(html)
                if (text.isEmpty()) continue
                val link = formLink.find(html)?.groupValues?.get(1)
                if (link != null) {
                    if (lemma == null) {
                        lemma = runCatching { URLDecoder.decode(link, "UTF-8") }.getOrDefault(link).replace('_', ' ')
                        form = text
                    }
                } else onlyForms = false
                if (senses.size < MOST_SENSES) senses += text
            }
            if (senses.isNotEmpty() && entries.size < MOST_ENTRIES) entries += DictEntry(e.optString("partOfSpeech"), senses)
        }
        if (entries.isEmpty()) return null
        return DictResult(word, languageName, entries, formOf = lemma?.takeIf { onlyForms || it != word }, form = form)
    }

    /**
     * Looks [text] up. A word that's only a form of another ("brooded") is looked up under that word too, whose
     * meanings are the ones wanted. [fetch] gets an address's text, or null if there's no such page.
     */
    fun lookUp(text: String, language: String?, fetch: (String) -> String? = fetcher): DictResult? {
        for (word in candidates(text)) {
            val found = fetch(url(word))?.let { parse(it, word, language) } ?: continue
            val lemma = found.formOf
            if (lemma != null && lemma != word) {
                val base = fetch(url(lemma))?.let { parse(it, lemma, language) }
                if (base != null) {
                    // The word's own meanings too, if it has any besides being a form ("brooded": having broods).
                    val own = found.entries.map { e -> e.copy(senses = e.senses.filter { it != found.form }) }.filter { it.senses.isNotEmpty() }
                    // The kind of word it's a form of first ("brooded" is a verb's form: the verb, then the noun).
                    val kind = found.entries.firstOrNull { e -> found.form in e.senses }?.partOfSpeech
                    return DictResult(word, base.language, base.entries.sortedByDescending { it.partOfSpeech == kind } + own.take(1), formOf = lemma, form = found.form)
                }
            }
            return found.copy(formOf = null)
        }
        return null
    }

    /** The dictionary is busy, or can't be reached: not the same as having no such word. */
    class Unreachable(message: String) : java.io.IOException(message)

    fun get(address: String): String? {
        val c = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            return when (val code = c.responseCode) {
                in 200..299 -> c.inputStream.bufferedReader(Charsets.UTF_8).readText()
                404 -> null
                else -> throw Unreachable("HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }
}
