package dev.kobolibrary.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eject
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Android only lets the system unmount drives, so this explains the steps and opens the right place.
 */
@Composable
fun EjectDialog(onDismiss: () -> Unit, busy: Boolean = false, savingDatabase: Boolean = false) {
    val context = LocalContext.current
    val myFiles = remember { context.packageManager.getLaunchIntentForPackage("com.sec.android.app.myfiles") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Eject, null) },
        title = { Text("Eject the Kobo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) {
                    Text(
                        "Books are still being sent. Wait until sending finishes, or the book being copied will be incomplete.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (savingDatabase) {
                    Text(
                        "The app is saving collections and book details to the Kobo. Wait a few seconds until it's done.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("Apps can't eject drives on Android, so use Android's Unmount option:")
                if (myFiles != null) {
                    Text("1. Open My Files.")
                    Text("2. Tap the USB drive, then ⋮ (top right) → Unmount.")
                } else {
                    Text("1. Open Storage settings.")
                    Text("2. Find the USB drive and tap Unmount.")
                }
                Text("3. Once it says the drive is unmounted, unplug the cable.")
                Text(
                    "You can also swipe down from the top of the screen. The USB drive notification often has an Unmount button.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                try {
                    context.startActivity(myFiles ?: Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }) { Text(if (myFiles != null) "Open My Files" else "Open storage settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
