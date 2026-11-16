package dev.kobolibrary.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Eject
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.UsbEjectState
import dev.kobolibrary.app.kobo.UnmountScreens
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.AlertDialog

/**
 * The ways to eject the Kobo, side by side: KoLib telling the Kobo itself over USB ([onUsbEject], when
 * Android lists the Kobo there), and Android's own Unmount, in whichever of its screens this device has.
 * Only Android may unmount a drive, so for those KoLib opens the right screen.
 */
@Composable
fun EjectDialog(
    onDismiss: () -> Unit,
    busy: Boolean = false,
    savingDatabase: Boolean = false,
    /** The folder KoLib was given on the Kobo, for finding its drive's own screens. */
    treeUri: Uri? = null,
    usb: UsbEjectState? = null,
    onUsbEject: (() -> Unit)? = null,
    /** Android's screens for unmounting it; looked up when not given. */
    screens: List<UnmountScreens.Screen>? = null,
) {
    val context = LocalContext.current
    val found = screens ?: remember(treeUri) { UnmountScreens.available(context, treeUri) }
    val working = busy || savingDatabase
    val running = usb?.running == true
    val open = { s: UnmountScreens.Screen ->
        AppLog.i("Eject", "Opening ${s.kind} (${s.handledBy}).")
        onDismiss()
        try {
            context.startActivity(s.intent)
        } catch (e: ActivityNotFoundException) {
            AppLog.w("Eject", "It wouldn't open: ${e.message}")
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: SecurityException) {
            AppLog.w("Eject", "It wouldn't open: ${e.message}")
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        icon = { Icon(Icons.Outlined.Eject, null) },
        title = { Text(tr("Eject the Kobo")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    usb != null -> UsbStatus(usb)
                    busy -> Text(
                        tr("Books are still being sent. Wait until sending finishes, or the book being copied will be incomplete."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    savingDatabase -> Text(
                        tr("The app is updating collections and book details on the Kobo. Wait a few seconds until it's done."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Everything KoLib changed is already on the Kobo, so you can also just unplug it."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // The ways to eject it; after one has worked, there's nothing more to do.
                if (usb == null || usb.ok == false) {
                    val enabled = !working && !running
                    if (onUsbEject != null) {
                        Choice(
                            Icons.Outlined.Eject, tr("Eject from KoLib"),
                            tr("The Kobo adds your new books straight away, without being unplugged."),
                            tag = tr("Trial"), enabled = enabled, onClick = onUsbEject,
                        )
                    }
                    for (s in found) when (s.kind) {
                        UnmountScreens.Kind.SETTINGS -> Choice(
                            Icons.Outlined.Settings, tr("Unmount in Settings"),
                            tr("Opens the Kobo's page in Android's settings. Tap Unmount there, then unplug it."),
                            enabled = enabled,
                        ) { open(s) }
                        UnmountScreens.Kind.FILES -> Choice(
                            Icons.Outlined.Folder, tr("Eject in Files"),
                            tr("Opens the Kobo in Android's Files app. Open its menu (☰) and tap ⏏ beside the USB drive."),
                            enabled = enabled,
                        ) { open(s) }
                        UnmountScreens.Kind.MY_FILES -> Choice(
                            Icons.Outlined.FolderOpen, tr("Unmount in My Files"),
                            tr("Tap the USB drive, then ⋮ (top right) → Unmount."),
                            enabled = enabled,
                        ) { open(s) }
                        UnmountScreens.Kind.STORAGE_SETTINGS -> Choice(
                            Icons.Outlined.Storage, tr("Open storage settings"),
                            tr("Find the USB drive and tap Unmount."),
                            enabled = enabled,
                        ) { open(s) }
                    }
                    Text(
                        tr("You can also swipe down from the top of the screen. The USB drive notification often has an Unmount button."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !running) { Text(tr("Close")) } },
    )
}

/** What the eject over USB is doing, or how it went. */
@Composable
private fun UsbStatus(usb: UsbEjectState) {
    Row(verticalAlignment = Alignment.Top) {
        if (usb.running) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                when (usb.ok) {
                    true -> Icons.Outlined.CheckCircle
                    false -> Icons.Outlined.WarningAmber
                    null -> Icons.Outlined.HelpOutline
                },
                null,
                Modifier.size(18.dp).padding(top = 1.dp),
                tint = if (usb.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(usb.text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One way to eject: what it's called, and in a line what happens. */
@Composable
private fun Choice(icon: ImageVector, title: String, detail: String, tag: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    // A card of its own on the dialog's colour, so each reads as something to tap.
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (tag != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
