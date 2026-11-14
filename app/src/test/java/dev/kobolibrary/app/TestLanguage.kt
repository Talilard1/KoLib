package dev.kobolibrary.app

import java.io.File
import java.util.Locale

/**
 * For looking over the screens in another language: the language named in /home/claude/phone/language.txt
 * (fr, de, ja...), or none. Screenshot tests call [apply] before each test and keep their pictures apart.
 */
object TestLanguage {
    val code: String? get() = File("/home/claude/phone/language.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /** Uses the language's table from the app's assets, as the app would. */
    fun apply() {
        val lang = code ?: return
        val table = HashMap<String, String>()
        File("src/main/assets/i18n/$lang.json").reader(Charsets.UTF_8).use { input ->
            android.util.JsonReader(input).use { r ->
                r.beginObject()
                while (r.hasNext()) table[r.nextName()] = r.nextString()
                r.endObject()
            }
        }
        L10n.use(lang, table)
        Locale.setDefault(Locale.forLanguageTag(lang))
    }

    /** [folder], or a folder for the language inside it. */
    fun folder(folder: String): String = code?.let { "$folder/lang_$it" } ?: folder
}
