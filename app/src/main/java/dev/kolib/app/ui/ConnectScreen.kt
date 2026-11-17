package dev.kolib.app.ui

import androidx.compose.material.icons.outlined.CheckCircle
import dev.kolib.app.UsbKobo
import dev.kolib.app.tr
import dev.kolib.app.ui.components.GoldButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kolib.app.Connection
import dev.kolib.app.kobo.VolumeCandidate

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    connection: Connection,
    backupCount: Int,
    onChooseDrive: (VolumeCandidate?) -> Unit,
    onRetry: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenLog: () -> Unit,
    /** Opens the app's settings: its look, language and books folder. */
    onSettings: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(tr("Connect your Kobo"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                tr("KoLib reads your Kobo's books and collections over a USB-C cable."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Step(1, tr("Plug the Kobo into this device with a USB-C data cable."))
                    Step(2, tr("On the Kobo's screen, tap Connect."))
                    Step(3, tr("The first time only: allow access to the Kobo's drive below."))
                }
            }
            Spacer(Modifier.height(24.dp))

            StatusArea(connection, onChooseDrive, onRetry)

            Spacer(Modifier.height(28.dp))
            if (connection !is Connection.Working) {
                TextButton(onClick = { onChooseDrive(null) }) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Choose the Kobo's drive manually"))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                TextButton(onClick = onOpenBackups) {
                    Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (backupCount > 0) tr("Database backups ({0})", backupCount) else tr("Database backups"))
                }
                TextButton(onClick = onOpenLog) {
                    Icon(Icons.AutoMirrored.Outlined.Article, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Log"))
                }
                onSettings?.let { open ->
                    TextButton(onClick = open) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Settings"))
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusArea(
    connection: Connection,
    onChooseDrive: (VolumeCandidate?) -> Unit,
    onRetry: () -> Unit,
) {
    when (connection) {
        is Connection.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (connection.usb == UsbKobo.UNMOUNTED) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                usbKoboText(connection.usb) ?: tr("Waiting for the Kobo…"),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        is Connection.NeedsAccess -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tr("Found “{0}”.", connection.candidate.description),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            GoldButton(onClick = { onChooseDrive(connection.candidate) }) {
                Text(tr("Allow access to the Kobo"))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr("In Android's folder picker, tap “Use this folder”, then “Allow”."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        is Connection.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(14.dp))
            Text(connection.step, style = MaterialTheme.typography.titleMedium)
        }

        is Connection.Failed -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(connection.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) { Text(tr("Try again")) }
                    OutlinedButton(onClick = { onChooseDrive(connection.candidate) }) { Text(tr("Choose drive")) }
                }
            }
        }

        is Connection.Connected -> Unit
    }
}

/** What a Kobo plugged in over USB is doing, when there's something to say. */
fun usbKoboText(usb: UsbKobo): String? = when (usb) {
    UsbKobo.NONE -> null
    UsbKobo.OPENING -> tr("Opening the Kobo… Don't unplug it.")
    UsbKobo.SLOW -> tr("Still waiting for Android to open the Kobo. Check that the Kobo says it's connected.")
    UsbKobo.UNMOUNTED -> tr("The Kobo is unmounted. It's safe to unplug it now.")
}

/** How to connect, for when the saved copy of the Kobo is showing. */
@Composable
fun ConnectHelpDialog(connection: Connection, onChooseDrive: (VolumeCandidate?) -> Unit, onDismiss: () -> Unit) {
    dev.kolib.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Usb, null) },
        title = { Text(tr("Connect your Kobo")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Step(1, tr("Plug the Kobo into this device with a USB-C data cable."))
                Step(2, tr("On the Kobo's screen, tap Connect."))
                Step(3, tr("KoLib finds it and loads it again."))
                if (connection is Connection.Waiting) usbKoboText(connection.usb)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onChooseDrive(null)
            }) { Text(tr("Choose the drive manually")) }
        },
    )
}
