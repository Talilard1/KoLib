package dev.kolib.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.kobo.UsbApp
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import dev.kolib.app.ui.components.AlertDialog

/**
 * Another app (a file manager that takes USB drives) may have opened over KoLib as the Kobo connected.
 * KoLib can't stop that itself: the app has to stop being the one Android opens for the Kobo, and then
 * KoLib can be chosen instead. [apps]: the apps that could have opened.
 */
@Composable
fun KoboTakeoverDialog(apps: List<UsbApp>, onClose: (dontShowAgain: Boolean) -> Unit) {
    val context = LocalContext.current
    var dontShow by remember { mutableStateOf(false) }
    val one = apps.singleOrNull()
    AlertDialog(
        onDismissRequest = { onClose(dontShow) },
        icon = { Icon(Icons.Outlined.Usb, null) },
        title = { Text(if (one != null) tr("Stop {0} opening", one.name) else tr("Stop other apps opening")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (one != null) tr("If {0} opens when you connect the Kobo, open its settings, tap Set as default (or Open by default), then Clear defaults.", one.name)
                    else tr("If one of these apps opens when you connect the Kobo, open its settings, tap Set as default (or Open by default), then Clear defaults."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (one == null) {
                    Column {
                        apps.forEach { app ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(app.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                TextButton(onClick = { openAppSettings(context, app) }, modifier = Modifier.testTag("settings-${app.packageName}")) { Text(tr("Settings")) }
                            }
                        }
                    }
                }
                Text(tr("Next time you connect the Kobo, choose KoLib and tap Always."), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = dontShow, role = Role.Checkbox, onValueChange = { dontShow = it })
                        .padding(vertical = 2.dp)
                        .testTag("takeover-mute"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontShow, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Don't show this again"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (one != null) {
                TextButton(onClick = {
                    onClose(dontShow)
                    openAppSettings(context, one)
                }, modifier = Modifier.testTag("takeover-settings")) { Text(tr("Open settings")) }
            } else {
                TextButton(onClick = { onClose(dontShow) }) { Text(tr("Close")) }
            }
        },
        dismissButton = if (one != null) ({ TextButton(onClick = { onClose(dontShow) }) { Text(tr("Close")) } }) else null,
    )
}

/** Android's page for [app], where what it opens by default can be cleared. */
private fun openAppSettings(context: Context, app: UsbApp) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        AppLog.w("USB", "Couldn't open ${app.name}'s settings: ${e.message}")
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
    }
}
