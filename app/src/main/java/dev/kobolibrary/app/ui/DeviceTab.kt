package dev.kobolibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.UiState
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.components.InfoRow
import dev.kobolibrary.app.ui.components.SectionCard

@Composable
fun DeviceTab(
    view: KoboView,
    live: Boolean,
    state: UiState,
    onWriteCheck: () -> Unit,
    onBackupNow: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenLog: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= TwoPaneWidth
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (twoColumns) {
                Row(
                    Modifier
                        .widthIn(max = 1200.dp)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DeviceCard(view)
                        LibraryCard(view)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DatabaseCard(view, live, state, onBackupNow, onOpenBackups)
                        WriteCard(live, state, onWriteCheck)
                        DiagnosticsCard(view, onOpenLog)
                    }
                }
            } else {
                Column(
                    Modifier
                        .widthIn(max = 700.dp)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DeviceCard(view)
                    LibraryCard(view)
                    DatabaseCard(view, live, state, onBackupNow, onOpenBackups)
                    WriteCard(live, state, onWriteCheck)
                    DiagnosticsCard(view, onOpenLog)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(view: KoboView) {
    val context = LocalContext.current
    val info = view.info
    SectionCard(info.displayName, icon = Icons.Outlined.TabletAndroid) {
        InfoRow("Firmware", info.version?.firmware)
        InfoRow("Serial number", info.version?.serial, selectable = true)
        InfoRow("Model ID", info.version?.modelId?.takeLast(3))
        InfoRow("USB ID", info.usbProductId?.let { "0x%04X".format(it) })
        InfoRow("Database version", view.snapshot.dbVersion.takeIf { it > 0 }?.toString())
        InfoRow("Drive", listOfNotNull(info.volumeDescription, info.volumeUuid).joinToString(" · ").ifEmpty { null })
        val space = if (info.freeBytes != null && info.totalBytes != null) {
            "${Fmt.bytes(context, info.freeBytes)} free of ${Fmt.bytes(context, info.totalBytes)}"
        } else {
            "Not reported by Android"
        }
        InfoRow(if (view.live) "Storage" else "Storage (when last connected)", space)
        InfoRow(if (view.live) "Connected" else "Last connected", if (view.live) Fmt.time(view.readAtMillis) else Fmt.dateTime(view.readAtMillis))
    }
}

@Composable
private fun LibraryCard(view: KoboView) {
    val s = view.snapshot
    SectionCard("On this Kobo", icon = Icons.Outlined.AutoStories) {
        InfoRow("Books", "${s.books.size}")
        InfoRow("Sideloaded", "${s.sideloadedCount}")
        InfoRow("Kobo Store", "${s.purchasedOnDeviceCount}")
        if (s.purchasedInCloudCount > 0) InfoRow("Kobo Store, not downloaded", "${s.purchasedInCloudCount}")
        if (s.previewCount > 0) InfoRow("Previews", "${s.previewCount}")
        InfoRow("Collections", "${s.collections.size}")
        InfoRow("Not in any collection", "${s.booksWithoutCollection().size}")
    }
}

@Composable
private fun DatabaseCard(view: KoboView, live: Boolean, state: UiState, onBackupNow: () -> Unit, onOpenBackups: () -> Unit) {
    val context = LocalContext.current
    SectionCard("Database and backups", icon = Icons.Outlined.Storage) {
        InfoRow("Size", Fmt.bytes(context, view.databaseBytes))
        val after = state.afterConnect.takeIf { live }
        if (live) InfoRow(
            "Health check",
            when {
                state.afterConnectRunning -> "Checking…"
                after == null -> "Not run"
                after.integrity == "ok" -> "Passed"
                else -> after.integrity
            },
        )
        InfoRow("Write-ahead log", if (view.databaseHadWal) "Present" else "None")
        val latest = state.backups.firstOrNull()
        InfoRow(
            "Latest backup",
            when {
                live && state.afterConnectRunning -> "Backing up…"
                after?.backupError != null -> "Failed: ${after.backupError}"
                after?.backup != null -> "Made when you connected (${Fmt.time(after.backup.createdMillis)}), ${Fmt.bytes(context, after.backup.sizeBytes)} zipped"
                latest != null -> "${Fmt.dateTime(latest.createdMillis)} (no changes since)"
                else -> "None yet"
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Whenever you connect and the Kobo's database has changed, a zipped copy is saved on this device. " +
                "The newest ${dev.kobolibrary.app.backup.BackupManager.MAX_BACKUPS} are kept.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackupNow, enabled = live && state.busyMessage == null) { Text("Back up now") }
            OutlinedButton(onClick = onOpenBackups) {
                Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Backups (${state.backups.size})")
            }
            if (state.busyMessage != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun WriteCard(live: Boolean, state: UiState, onWriteCheck: () -> Unit) {
    SectionCard("Write access check", icon = Icons.Outlined.EditNote) {
        Text(
            "Sending books needs write access to the Kobo. " +
                "This check writes a small hidden test file to the Kobo, reads it back and deletes it. Nothing else changes.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onWriteCheck, enabled = live && !state.writeCheckRunning) { Text("Check write access") }
            if (state.writeCheckRunning) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        state.writeCheck?.let { result ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (result.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    null,
                    tint = if (result.ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Text(result.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(view: KoboView, onOpenLog: () -> Unit) {
    SectionCard("Diagnostics", icon = Icons.Outlined.Troubleshoot) {
        view.session?.let { InfoRow("Quick file lookups", if (it.tree.directIdsWork == true) "Working" else "Not used") }
        InfoRow("Direct file access", if (view.info.directFileAccess) "Available" else "Not available (normal)")
        InfoRow("Covers loaded", "${view.covers.loadedCount}")
        val warnings = view.snapshot.warnings
        if (warnings.isEmpty()) {
            InfoRow("Database notes", "Nothing unusual")
        } else {
            Spacer(Modifier.height(6.dp))
            Text("Database notes", style = MaterialTheme.typography.labelLarge)
            warnings.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenLog) {
            Icon(Icons.AutoMirrored.Outlined.Article, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open log")
        }
    }
}
