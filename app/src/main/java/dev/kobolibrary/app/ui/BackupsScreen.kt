@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kobolibrary.app.ui

import dev.kobolibrary.app.tr
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.backup.DatabaseBackup
import dev.kobolibrary.app.backup.BackupManager
import dev.kobolibrary.app.ui.components.EmptyState
import dev.kobolibrary.app.ui.components.Fmt

@Composable
fun BackupsScreen(
    backups: List<DatabaseBackup>,
    onExport: (DatabaseBackup) -> Unit,
    onDelete: (DatabaseBackup) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<DatabaseBackup?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(tr("Database backups")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            )
        },
    ) { padding ->
        if (backups.isEmpty()) {
            EmptyState(
                Icons.Outlined.Restore,
                tr("No backups yet"),
                tr("A backup is made automatically when you connect your Kobo."),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    tr("Zipped copies of the Kobo's database: its library, collections and reading progress. One is made when you connect and something has changed. Only the newest {0} are stored. Tap the download button to copy one to Downloads/{1}.", BackupManager.MAX_BACKUPS, BackupManager.EXPORT_FOLDER),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            items(backups, key = { it.file.name }) { b ->
                ListItem(
                    headlineContent = { Text(Fmt.dateTime(b.createdMillis) ?: b.file.name) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                b.deviceLabel,
                                Fmt.bytes(context, b.sizeBytes),
                            ).joinToString(" · ")
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Storage, null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onExport(b) }) { Icon(Icons.Outlined.Download, tr("Save to Downloads")) }
                            IconButton(onClick = { toDelete = b }) { Icon(Icons.Outlined.Delete, tr("Delete backup")) }
                        }
                    },
                )
            }
        }
    }

    toDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Delete this backup?")) },
            text = { Text(tr("Deletes the backup from {0} from this device. The Kobo isn't affected.", Fmt.dateTime(b.createdMillis))) },
            confirmButton = { TextButton(onClick = { toDelete = null; onDelete(b) }) { Text(tr("Delete")) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel")) } },
        )
    }
}
