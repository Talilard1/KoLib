package dev.kolib.app.reader

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import java.util.Locale

/** Looking words up: in the dictionary and translation apps installed, Wikipedia, or a web search. */
object LookUp {
    data class App(val label: String, val component: ComponentName, val icon: Drawable?) {
        val key: String get() = component.flattenToString()
    }

    private fun processText(text: String) = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

    /** The apps that take selected text (dictionaries, Translate...), this app left out. */
    fun apps(context: Context): List<App> {
        val pm = context.packageManager
        return pm.queryIntentActivities(processText("x"), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                App(ri.loadLabel(pm).toString(), ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), runCatching { ri.loadIcon(pm) }.getOrNull())
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun app(context: Context, key: String?): App? = key?.let { k -> apps(context).firstOrNull { it.key == k } }

    fun open(context: Context, app: App, text: String): Boolean = start(context, processText(text).setComponent(app.component))

    fun wikipedia(context: Context, text: String, language: String?): Boolean {
        val lang = language?.substringBefore('-')?.lowercase(Locale.ROOT)?.takeIf { it.length in 2..3 } ?: "en"
        val url = "https://$lang.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(text.trim())
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun webSearch(context: Context, text: String): Boolean =
        start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text.trim())) ||
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(text.trim()))))

    /** "define …" for a single word, the words themselves for a phrase. */
    fun defineQuery(text: String): String {
        val t = text.trim()
        return if (!t.contains(' ')) "define $t" else t
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
