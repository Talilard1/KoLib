package dev.kolib.app.ui

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
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

/** Storage, from More: how much of the device KoLib takes up, and by what, in one bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(onBack: () -> Unit, measure: (Context) -> StorageUse = ::measureHere) {
    val context = LocalContext.current
    val use by produceState<StorageUse?>(null) { value = withContext(Dispatchers.IO) { measure(context) } }
    Scaffold(
        containerColor = Color.Transparent,
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
                    StorageDetails(u)
                }
            }
        }
    }
}

@Composable
private fun StorageDetails(u: StorageUse) {
    val context = LocalContext.current
    fun bytes(b: Long) = Fmt.bytes(context, b).orEmpty()
    fun percent(b: Long): String =
        if (u.total <= 0) "" else (b * 100.0 / u.total).let { if (it < 0.1) "<0.1%" else if (it < 10) "%.1f%%".format(it) else "%.0f%%".format(it) }
    val shares = u.parts.map { (kind, b) -> StorageShare(nameOf(kind), b, colourOf(kind)) }
    Text(tr("KoLib uses {0}", bytes(u.kolib)), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("storage-kolib"))
    Text(
        tr("{0} free of {1}", bytes(u.free), bytes(u.total)),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
    )
    if (u.total > 0) StorageBar(shares, u.total, u.free, Modifier.padding(top = 16.dp, bottom = 12.dp))
    shares.forEachIndexed { i, s -> Line(s.colour, s.name, tr("{0} · {1}", bytes(s.bytes), percent(s.bytes)), Modifier.testTag("storage-${u.parts[i].first.name.lowercase()}")) }
    val others = (u.total - u.free - u.kolib).coerceAtLeast(0L)
    Line(MaterialTheme.colorScheme.outline, tr("Everything else on this device"), tr("{0} · {1}", bytes(others), percent(others)))
    Line(MaterialTheme.colorScheme.surfaceContainerHighest, tr("Free"), tr("{0} · {1}", bytes(u.free), percent(u.free)))
}

@Composable
private fun Line(colour: Color, name: String, size: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colour))
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(size, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
    StorageUse.Kind.OTHER -> tr("Other")
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
