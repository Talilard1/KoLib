package dev.kolib.app.ui

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.KoLibApp
import dev.kolib.app.StorageUse
import dev.kolib.app.tr
import dev.kolib.app.ui.ai.StorageBar
import dev.kolib.app.ui.ai.StorageShare
import dev.kolib.app.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage, from More: what KoLib takes up, by what, biggest first, in a bar and a list; then, apart (so it isn't taken
 * for KoLib's), the device as a whole: KoLib, other apps and files, and what's free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    measure: (Context) -> StorageUse = ::measureHere,
    /** Something's being sent or saved just now: the temporary files aren't cleared under it. */
    busy: Boolean = false,
    /** Clears the temporary files, giving how much space that freed (see [StorageUse.clearTemporary]). */
    clear: (Context) -> Long = { StorageUse.clearTemporary(it.cacheDir) },
) {
    val context = LocalContext.current
    // Worked out as the page opens, and again once the temporary files are cleared.
    var use by remember { mutableStateOf<StorageUse?>(null) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) { use = withContext(Dispatchers.IO) { measure(context) } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(tr("Storage")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).testTag("storage-screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp)) {
                val u = use
                if (u == null) {
                    Text(tr("Working it out…"), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                } else {
                    StorageDetails(u, busy = busy, onClear = { asking = true })
                }
            }
        }
    }
    // What they are, before they're cleared.
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(tr("Clear temporary files?")) },
            text = {
                Text(
                    tr("These are files KoLib makes as it works: pages and pictures got ready for reading, copies of books made while sending them, and pages the reader keeps to show them faster. None of your books, comics or settings are in them, and KoLib makes them again when it needs them, so a book may open a little more slowly the first time after."),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { clear(context) }
                            round++
                            snackbar.showSnackbar(
                                if (freed > 0) tr("Cleared {0}.", Fmt.bytes(context, freed).orEmpty())
                                else tr("There was nothing to clear just now."),
                            )
                        }
                    },
                    modifier = Modifier.testTag("storage-clear-yes"),
                ) { Text(tr("Clear")) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("Cancel")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageDetails(u: StorageUse, busy: Boolean = false, onClear: () -> Unit = {}) {
    val context = LocalContext.current
    fun bytes(b: Long) = Fmt.bytes(context, b).orEmpty()
    // What KoLib keeps, biggest first, in the bar and the list alike, each as a share of all KoLib uses.
    val parts = u.parts.sortedByDescending { it.second }
    fun percent(b: Long): String =
        if (u.kolib <= 0) "" else (b * 100.0 / u.kolib).let { if (it < 0.1) "<0.1%" else if (it < 10) "%.1f%%".format(it) else "%.0f%%".format(it) }
    Text(tr("KoLib uses {0}", bytes(u.kolib)), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("storage-kolib"))
    if (u.kolib > 0) PartsBar(parts.map { (kind, b) -> colourOf(kind) to b }, Modifier.padding(top = 16.dp, bottom = 12.dp).testTag("storage-parts-bar"))
    parts.forEach { (kind, b) ->
        Line(colourOf(kind), nameOf(kind), tr("{0} · {1}", bytes(b), percent(b)), Modifier.testTag("storage-${kind.name.lowercase()}"), about = aboutOf(kind))
        // The temporary files can be cleared from here (not while something's being sent or saved).
        if (kind == StorageUse.Kind.TEMP) {
            TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.padding(start = 12.dp).testTag("storage-clear")) {
                Text(tr("Clear temporary files"))
            }
            if (busy) {
                Text(
                    tr("They can be cleared once KoLib's finished sending."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                )
            }
        }
    }

    // The device as a whole, apart from KoLib's own: KoLib among everything else on it, and what's free.
    if (u.total > 0) {
        HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 16.dp))
        Text(tr("This device"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("storage-device"))
        Text(
            tr("{0} free of {1}", bytes(u.free), bytes(u.total)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
        )
        val kolibColour = MaterialTheme.colorScheme.primary
        val others = (u.total - u.free - u.kolib).coerceAtLeast(0L)
        StorageBar(listOf(StorageShare("KoLib", u.kolib, kolibColour)), u.total, u.free, Modifier.padding(top = 12.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Key(kolibColour, tr("{0} · {1}", "KoLib", bytes(u.kolib)))
            Key(MaterialTheme.colorScheme.outline, tr("{0} · {1}", tr("Other apps and files"), bytes(others)), Modifier.testTag("storage-others"))
            Key(MaterialTheme.colorScheme.surfaceContainerHighest, tr("{0} · {1}", tr("Free"), bytes(u.free)), outlined = true)
        }
    }
}

/** What KoLib keeps, as one bar the width of it all: a piece for each, in order, none too thin to see. */
@Composable
private fun PartsBar(parts: List<Pair<Color, Long>>, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(track)) {
        val sum = parts.sumOf { it.second }.toFloat()
        if (sum <= 0f || parts.isEmpty()) return@Canvas
        val least = 5.dp.toPx()
        val gap = 1.5.dp.toPx()
        val room = size.width - gap * (parts.size - 1)
        // The thinnest are made wide enough to see; the rest share what's left, in proportion.
        val thin = parts.map { room * it.second / sum < least }
        val restBytes = parts.filterIndexed { i, _ -> !thin[i] }.sumOf { it.second }.toFloat()
        val restRoom = room - least * thin.count { it }
        var x = 0f
        parts.forEachIndexed { i, (colour, b) ->
            val w = if (thin[i] || restBytes <= 0f) least else restRoom * b / restBytes
            drawRect(colour, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w + gap
        }
    }
}

@Composable
private fun Line(colour: Color, name: String, size: String, modifier: Modifier = Modifier, about: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colour))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (about != null) Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(size, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A colour in the device's bar, and what it stands for. */
@Composable
private fun Key(colour: Color, text: String, modifier: Modifier = Modifier, outlined: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colour)
                .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A line under a part's name, for those whose name doesn't say it all. */
private fun aboutOf(kind: StorageUse.Kind): String? = when (kind) {
    StorageUse.Kind.TEMP -> tr("Made while reading and sending. Android clears them when space runs low.")
    StorageUse.Kind.OTHER -> tr("Your library's records, settings, the log and the like")
    else -> null
}

private fun nameOf(kind: StorageUse.Kind): String = when (kind) {
    StorageUse.Kind.BOOKS -> tr("Books")
    StorageUse.Kind.COMICS -> tr("Comics")
    StorageUse.Kind.COVERS -> tr("Covers")
    StorageUse.Kind.TEXT -> tr("Text recognition")
    StorageUse.Kind.AI -> tr("AI models and chats")
    StorageUse.Kind.FONTS -> tr("Fonts")
    StorageUse.Kind.BACKUPS -> tr("Database backups")
    StorageUse.Kind.UPDATES -> tr("Downloaded updates")
    StorageUse.Kind.APP -> tr("The app itself")
    StorageUse.Kind.TEMP -> tr("Temporary files")
    StorageUse.Kind.OTHER -> tr("Records and settings")
}

private fun colourOf(kind: StorageUse.Kind): Color = Color(
    when (kind) {
        StorageUse.Kind.BOOKS -> 0xFF4E8FD9
        StorageUse.Kind.COMICS -> 0xFFE0903A
        StorageUse.Kind.COVERS -> 0xFF57A773
        StorageUse.Kind.TEXT -> 0xFFB86BC9
        StorageUse.Kind.AI -> 0xFFD9605A
        StorageUse.Kind.FONTS -> 0xFF3FA7A3
        StorageUse.Kind.BACKUPS -> 0xFFC9A227
        StorageUse.Kind.UPDATES -> 0xFF7C83DB
        StorageUse.Kind.APP -> 0xFF8E8E93
        StorageUse.Kind.TEMP -> 0xFF6F9FA8
        StorageUse.Kind.OTHER -> 0xFFB0A28A
    },
)

/** This device's figures: KoLib's folders, its library, the app's own size (as Android has it), and the storage. */
private fun measureHere(context: Context): StorageUse {
    val app = context.applicationContext as KoLibApp
    val comics = app.library.books.value.associate { it.id to it.isComic }
    val appBytes = runCatching {
        context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle()).appBytes
    }.getOrDefault(0L)
    val files = context.filesDir
    return StorageUse.measure(
        data = context.dataDir, files = files, cache = context.cacheDir, library = app.library.root, comic = { comics[it] },
        app = appBytes, total = files.totalSpace, free = files.usableSpace,
    )
}
