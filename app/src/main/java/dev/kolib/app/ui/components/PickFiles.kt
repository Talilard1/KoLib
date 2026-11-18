package dev.kolib.app.ui.components

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import dev.kolib.app.tr

/**
 * Choosing files with the user's own file manager, not only Android's: the first time, Android lists the apps
 * that can pick a file (the file managers installed, and its own); the one chosen is remembered and opened
 * straight away from then on. It can be changed in the app's settings.
 *
 * (A whole folder can only be handed over by Android's own picker: no other app is able to.)
 */
object FileManagers {
    private const val PREFS = "pickers"
    private const val KEY = "files"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ask(types: Array<String>) = Intent(Intent.ACTION_GET_CONTENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .putExtra(Intent.EXTRA_MIME_TYPES, types)
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

    /** The app chosen to pick files with, if one has been and it's still installed. */
    fun chosen(context: Context): ComponentName? {
        val name = prefs(context).getString(KEY, null)?.let { ComponentName.unflattenFromString(it) } ?: return null
        val there = runCatching { context.packageManager.resolveActivity(ask(arrayOf("*/*")).setComponent(name), 0) }.getOrNull() != null
        if (!there) forget(context)
        return name.takeIf { there }
    }

    /** That app's name, for the settings. */
    fun chosenName(context: Context): String? = chosen(context)?.let { c ->
        runCatching { context.packageManager.getActivityInfo(c, 0).loadLabel(context.packageManager).toString() }.getOrNull()
    }

    fun remember(context: Context, app: ComponentName) = prefs(context).edit().putString(KEY, app.flattenToString()).apply()

    /** Asks which app again, the next time. */
    fun forget(context: Context) = prefs(context).edit().remove(KEY).apply()

    /** What starts the picking: the app chosen, or Android's list of apps to choose one from. */
    fun intent(context: Context, types: Array<String>): Intent {
        val ask = ask(types)
        chosen(context)?.let { return ask.setComponent(it) }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val told = PendingIntent.getBroadcast(context, 0, Intent(context, FileManagerChosen::class.java), flags)
        return Intent.createChooser(ask, tr("Choose files with"), told.intentSender)
    }

    /** The files picked, from what the app picked with answered. */
    fun picked(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData
        val many = if (clip != null) (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } else emptyList()
        return many.ifEmpty { listOfNotNull(intent.data) }
    }
}

/** Android tells this which app the user chose from its list, to open that one straight away the next time. */
class FileManagerChosen : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app: ComponentName? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        if (app != null) FileManagers.remember(context, app)
    }
}

/** Picks files (of the given kinds, where the app picked with can tell) with the user's file manager. */
class PickFiles : ActivityResultContract<Array<String>, List<Uri>>() {
    override fun createIntent(context: Context, input: Array<String>): Intent = FileManagers.intent(context, input)
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> = FileManagers.picked(resultCode, intent)
}
