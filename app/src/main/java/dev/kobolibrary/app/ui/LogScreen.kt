@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kobolibrary.app.ui

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Share
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.ui.components.EmptyState

@Composable
fun LogScreen(onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val changes by AppLog.changes.collectAsStateWithLifecycle()
    val entries = remember(changes) { AppLog.snapshot() }
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Log") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { shareLog(context, onMessage) }) { Icon(Icons.Outlined.Share, "Share log file") }
                    IconButton(onClick = { copyLog(context, onMessage) }) { Icon(Icons.Outlined.ContentCopy, "Copy log") }
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear log") }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Outlined.Article, "The log is empty", "The app's activity will be recorded here.", Modifier.padding(padding))
        } else {
            SelectionContainer(Modifier.padding(padding)) {
                LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { e ->
                        Text(
                            e.format(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = when (e.level) {
                                AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
                                AppLog.Level.WARN -> MaterialTheme.colorScheme.secondary
                                AppLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                                AppLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the log?") },
            text = { Text("This deletes the whole log, including earlier sessions.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; AppLog.clear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun shareLog(context: Context, onMessage: (String) -> Unit) {
    try {
        val file = AppLog.exportFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "KoLib log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share log"))
    } catch (e: Exception) {
        AppLog.e("Log", "Could not share the log", e)
        onMessage("Couldn't share the log: ${e.message}")
    }
}

private fun copyLog(context: Context, onMessage: (String) -> Unit) {
    val text = AppLog.exportFile(context).readText().takeLast(200_000)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("KoLib log", text))
    onMessage("Log copied.")
}
