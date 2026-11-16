package dev.kobolibrary.app.kobo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings

/**
 * Android's own places for unmounting the Kobo's drive, as far as this phone or tablet has them. Only
 * Android may unmount a drive, so KoLib can only take you to the right screen.
 */
object UnmountScreens {
    /**
     * Opens Settings' page for one drive, where its Unmount button is. It isn't in Android's public API,
     * but Android's own Files app uses it, and Android's Settings app answers it.
     */
    const val ACTION_ROOT_SETTINGS = "android.provider.action.DOCUMENT_ROOT_SETTINGS"
    const val MY_FILES = "com.sec.android.app.myfiles"

    enum class Kind {
        /** The Kobo's own page in Settings. */
        SETTINGS,
        /** Android's Files app, at the Kobo (its menu has an eject button beside each USB drive). */
        FILES,
        /** Samsung's My Files. */
        MY_FILES,
        /** Storage settings, to find the drive in. */
        STORAGE_SETTINGS,
    }

    data class Screen(val kind: Kind, val intent: Intent, val handledBy: String)

    /** The drive's root in Android's file system, from the folder KoLib was given ("…/tree/1A2B-3C4D%3A"). */
    fun rootUri(treeUri: Uri): Uri? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val root = docId.substringBefore(':').takeIf { it.isNotEmpty() } ?: return null
        return DocumentsContract.buildRootUri(treeUri.authority ?: return null, root)
    }

    fun settingsIntent(root: Uri): Intent =
        Intent(ACTION_ROOT_SETTINGS).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM)

    fun filesIntent(root: Uri): Intent =
        Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_DEFAULT).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM)

    /**
     * The screens this device has for the drive KoLib was given ([treeUri]), best first: the Kobo's own
     * page in Settings; failing that My Files (on a Samsung), Android's Files app, then storage settings.
     */
    fun available(context: Context, treeUri: Uri?): List<Screen> {
        val pm = context.packageManager
        val root = treeUri?.let { rootUri(it) }
        val out = mutableListOf<Screen>()
        fun add(kind: Kind, intent: Intent?) {
            intent ?: return
            val by = handlers(pm, intent)
            if (by.isNotEmpty()) out += Screen(kind, intent, by.joinToString())
        }
        if (root != null) add(Kind.SETTINGS, settingsIntent(root))
        add(Kind.MY_FILES, pm.getLaunchIntentForPackage(MY_FILES))
        if (root != null) add(Kind.FILES, filesIntent(root))
        add(Kind.STORAGE_SETTINGS, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        return out
    }

    /** The one screen to send you to: the best this device has. */
    fun best(context: Context, treeUri: Uri?): Screen? = available(context, treeUri).firstOrNull()

    @Suppress("DEPRECATION")
    private fun handlers(pm: PackageManager, intent: Intent): List<String> = try {
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName + "/" + it.activityInfo.name.substringAfterLast('.') }
    } catch (e: Exception) {
        emptyList()
    }
}
