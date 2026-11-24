@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kolib.app.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.SaveAlt
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import dev.kolib.app.tr
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import android.content.ClipData
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.ui.platform.LocalUriHandler
import dev.kolib.app.update.Updates
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
import dev.kolib.app.ui.components.AlertDialog
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
import dev.kolib.app.log.AppLog
import dev.kolib.app.ui.components.EmptyState

@Composable
fun LogScreen(onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val changes by AppLog.changes.collectAsStateWithLifecycle()
    val entries = remember(changes) { AppLog.snapshot() }
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    // Saved as a file wherever the user chooses on the device.
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) saveLog(context, uri, onMessage)
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(tr("Log")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
                actions = {
                    IconButton(onClick = { save.launch("KoLib log ${java.time.LocalDate.now()}.txt") }) { Icon(Icons.Outlined.SaveAlt, tr("Save log file")) }
                    IconButton(onClick = { shareLog(context, onMessage) }) { Icon(Icons.Outlined.Share, tr("Share log file")) }
                    IconButton(onClick = { copyLog(context, onMessage) }) { Icon(Icons.Outlined.ContentCopy, tr("Copy log")) }
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, tr("Clear log")) }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Outlined.Article, tr("The log is empty"), tr("KoLib's activity is recorded here."), Modifier.padding(padding))
        } else Column(Modifier.padding(padding).fillMaxSize()) {
            ReportLine()
            SelectionContainer(Modifier.weight(1f)) {
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
            title = { Text(tr("Clear the log?")) },
            text = { Text(tr("This deletes the whole log, including earlier sessions.")) },
            confirmButton = { TextButton(onClick = { confirmClear = false; AppLog.clear() }) { Text(tr("Clear")) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel")) } },
        )
    }
}

/**
 * Something gone wrong: where to report it, on KoLib's GitHub, with the log (from Share or Save) and screenshots. KoLib
 * doesn't send the log anywhere itself.
 */
@Composable
private fun ReportLine() {
    val links = LocalUriHandler.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp).testTag("log-report"),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Something wrong? Report it on GitHub, with the log and screenshots if you can."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            IconButton(onClick = { runCatching { links.openUri(Updates.ISSUES) } }, modifier = Modifier.testTag("log-issues")) {
                Icon(Icons.Outlined.BugReport, tr("Report a problem on GitHub"), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The whole log, saved where the user chose ([uri]). */
private fun saveLog(context: Context, uri: Uri, onMessage: (String) -> Unit) {
    try {
        val text = AppLog.exportFile(context).readBytes()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException(tr("The file couldn't be written."))
        out.use { it.write(text) }
        onMessage(tr("Log saved."))
    } catch (e: Exception) {
        AppLog.e("Log", "Could not save the log", e)
        onMessage(tr("Couldn't save the log: {0}", e.message))
    }
}

private fun shareLog(context: Context, onMessage: (String) -> Unit) {
    try {
        val file = java.io.File(AppLog.exportFile(context).parentFile, "kolib-log.txt").apply { writeText(latestLog(context)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "KoLib log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, tr("Share log")))
    } catch (e: Exception) {
        AppLog.e("Log", "Could not share the log", e)
        onMessage(tr("Couldn't share the log: {0}", e.message))
    }
}

/**
 * The latest of the log under its heading (the app's version and the device), from the start of a line: no more than a
 * message can take, so pasted or attached, the newest isn't what's cut off.
 */
private fun latestLog(context: Context): String {
    val all = AppLog.exportFile(context).readText()
    if (all.length <= LATEST_CHARS) return all
    val heading = all.lineSequence().take(4).joinToString("\n")
    val tail = all.takeLast(LATEST_CHARS)
    return heading + "\n…\n" + tail.substring((tail.indexOf('\n') + 1).coerceAtMost(tail.length))
}

private const val LATEST_CHARS = 80_000

private fun copyLog(context: Context, onMessage: (String) -> Unit) {
    val text = latestLog(context)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(tr("KoLib log"), text))
    onMessage(tr("Log copied."))
}
