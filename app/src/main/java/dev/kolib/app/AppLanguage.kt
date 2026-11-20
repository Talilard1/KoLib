package dev.kolib.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The languages KoLib is in, and which one it's shown in: the device's own (when KoLib has it), or one
 * chosen in KoLib. From Android 13 the choice is Android's own per-app language, which also shows in
 * the device's settings; before that, KoLib keeps it and applies it itself.
 */
object AppLanguage {
    /** KoLib's languages, as Android names them. English is the one it falls back to. */
    val supported = listOf("en", "fr", "de", "es", "it", "nl", "pt-BR", "ro", "ru", "ja", "ko", "zh-Hans")

    private const val PREFS = "settings"
    private const val KEY = "language"

    /** The one of KoLib's languages nearest to [locale], if any: Portuguese is Brazil's and Chinese is simplified, whatever the country. */
    fun match(locale: Locale): String? {
        val lang = locale.language.lowercase()
        return supported.firstOrNull { Locale.forLanguageTag(it).language == lang }
    }

    /** The device's language, as KoLib has it: the first of the device's languages KoLib is in, or English. */
    fun deviceLanguage(): String {
        val system = Resources.getSystem().configuration.locales
        for (i in 0 until system.size()) match(system[i])?.let { return it }
        return "en"
    }

    /** The language chosen in KoLib, or null when it follows the device. */
    fun chosen(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 33) {
            val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
            return if (list.isEmpty) null else match(list[0])
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }

    /** The language KoLib is shown in now. */
    fun current(context: Context): String = chosen(context) ?: deviceLanguage()

    /**
     * Shows KoLib in [tag] from now on; null (or the device's own language) follows the device. KoLib
     * starts again, so that everything is in the new language.
     */
    fun choose(activity: Activity, tag: String?) {
        val keep = tag?.takeIf { it != deviceLanguage() }
        if (keep == chosen(activity) && (keep ?: deviceLanguage()) == L10n.language) return
        // Kept by KoLib before Android 13; Android keeps it from 13 on (and shows it in its own settings).
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, keep).commit()
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (keep == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(keep)
        }
        RestartActivity.restart(activity)
    }

    /** Before Android 13: [base] in the language chosen in KoLib, for an activity or the app to use. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** [tag]'s name in its own language: "Deutsch", "日本語". */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** [tag]'s name in the language of [context]: "German". */
    fun localName(tag: String, context: Context): String {
        val here = context.resources.configuration.locales[0]
        return Locale.forLanguageTag(tag).getDisplayName(here).replaceFirstChar { it.titlecase(here) }
    }
}
