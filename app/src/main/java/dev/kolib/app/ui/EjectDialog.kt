package dev.kolib.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kolib.app.kobo.UnmountScreens
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import dev.kolib.app.ui.components.AlertDialog

/**
 * Ejecting the Kobo: only Android may unmount a drive, so this opens the one screen that does it, the
 * best this device has (the Kobo's own page in Settings, where there is one).
 */
@Composable
fun EjectDialog(
    onDismiss: () -> Unit,
    busy: Boolean = false,
    savingDatabase: Boolean = false,
    /** The folder KoLib was given on the Kobo, for finding its drive's own page. */
    treeUri: Uri? = null,
    /** The screen to open; looked up when not given. */
    screen: UnmountScreens.Screen? = null,
) {
    val context = LocalContext.current
    val target = screen ?: remember(treeUri) { UnmountScreens.best(context, treeUri) }
    val working = busy || savingDatabase
    val (label, how) = when (target?.kind) {
        UnmountScreens.Kind.SETTINGS -> tr("Unmount in Settings") to
            tr("Opens the Kobo's page in Android's settings. Tap Unmount there, then unplug it.")
        UnmountScreens.Kind.MY_FILES -> tr("Unmount in My Files") to
            tr("Tap the USB drive, then ⋮ (top right) → Unmount.")
        UnmountScreens.Kind.FILES -> tr("Eject in Files") to
            tr("Opens the Kobo in Android's Files app. Open its menu (☰) and tap ⏏ beside the USB drive.")
        UnmountScreens.Kind.STORAGE_SETTINGS, null -> tr("Open storage settings") to
            tr("Find the USB drive and tap Unmount.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Eject, null) },
        title = { Text(tr("Eject the Kobo")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) {
                    Text(
                        tr("Books are still being sent. Wait until sending finishes."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (savingDatabase) {
                    Text(
                        tr("Updating the Kobo. Wait a few seconds."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(how, style = MaterialTheme.typography.bodyMedium)
                Text(
                    tr("Unmount before unplugging, or Android may need a restart to connect the Kobo again."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    onDismiss()
                    val intent = target?.intent ?: Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                    AppLog.i("Eject", "Opening ${target?.kind ?: "storage settings"} (${target?.handledBy ?: "-"}).")
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        AppLog.w("Eject", "It wouldn't open: ${e.message}")
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (e: SecurityException) {
                        AppLog.w("Eject", "It wouldn't open: ${e.message}")
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
            ) { Text(label) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}
