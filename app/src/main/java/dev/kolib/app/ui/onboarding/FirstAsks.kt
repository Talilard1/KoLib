package dev.kolib.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Button

/** What KoLib asks Android to allow, all at once when it's first opened. */
enum class Ask(val key: String) {
    NOTIFICATIONS("notifications"),
    CAMERA("camera"),
    BACKGROUND("background"),
    UPDATES("updates");

    /** Whether this phone asks for it at all: notifications are only asked for from Android 13. */
    fun applies(): Boolean = this != NOTIFICATIONS || Build.VERSION.SDK_INT >= 33

    fun allowed(context: Context): Boolean = when (this) {
        NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)
        CAMERA -> granted(context, Manifest.permission.CAMERA)
        BACKGROUND -> context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        UPDATES -> context.packageManager.canRequestPackageInstalls()
    }

    companion object {
        /** What's still to be allowed. */
        fun missing(context: Context): List<Ask> = entries.filter { it.applies() && !it.allowed(context) }

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * The first time KoLib's opened (and once after updating to this version): what it needs Android to allow, in one
 * place, so nothing stops partway later. Allow all asks for each in turn; Not now leaves them to be asked for when
 * they're needed. With everything allowed already, it isn't shown at all.
 */
@Composable
fun FirstAsks(onDone: () -> Unit) {
    val context = LocalContext.current
    val nothingToAsk = remember { Ask.missing(context).isEmpty() }
    if (nothingToAsk) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val asks = remember { Ask.entries.filter { it.applies() } }
    // Answered: a request came back (which moves Allow all on). Looked: back in KoLib, as from Settings.
    var answered by remember { mutableIntStateOf(0) }
    var looked by remember { mutableIntStateOf(0) }
    val allowed = remember(answered, looked) { asks.associateWith { it.allowed(context) } }
    var allowingAll by remember { mutableStateOf(false) }
    val tried = remember { mutableStateListOf<Ask>() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { answered++ }
    val setting = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { answered++ }
    val here = Uri.parse("package:" + context.packageName)
    // Asks Android for it; false if this phone has nowhere to ask.
    fun ask(a: Ask): Boolean = when (a) {
        Ask.NOTIFICATIONS -> runCatching { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }.isSuccess
        Ask.CAMERA -> runCatching { permission.launch(Manifest.permission.CAMERA) }.isSuccess
        Ask.BACKGROUND -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, here),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        ).any { runCatching { setting.launch(it) }.isSuccess }
        Ask.UPDATES -> runCatching { setting.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, here)) }.isSuccess
    }

    // Allow all: each still to be allowed, in turn, once; with everything allowed, on into KoLib.
    LaunchedEffect(allowingAll, answered) {
        if (!allowingAll) return@LaunchedEffect
        val missing = Ask.missing(context)
        val next = missing.firstOrNull { it !in tried }
        if (next == null) {
            allowingAll = false
            if (missing.isEmpty()) onDone()
            return@LaunchedEffect
        }
        tried += next
        if (!ask(next)) answered++
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val watch = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) looked++ }
        lifecycle.addObserver(watch)
        onDispose { lifecycle.removeObserver(watch) }
    }

    val allAllowed = allowed.values.all { it }
    Surface(Modifier.fillMaxSize().testTag("first-asks"), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                Text(tr("Before you start"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("KoLib asks for these now, so nothing stops it partway later. You can change them any time in Android's settings."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                for (a in asks) {
                    when (a) {
                        Ask.NOTIFICATIONS -> AskRow(a, Icons.Outlined.Notifications, tr("Notifications"), tr("See how sending, syncing and updates are going."), allowed.getValue(a)) { ask(a) }
                        Ask.CAMERA -> AskRow(a, Icons.Outlined.PhotoCamera, tr("Camera"), tr("Scan the code on another device to link it."), allowed.getValue(a)) { ask(a) }
                        Ask.BACKGROUND -> AskRow(a, Icons.Outlined.BatteryChargingFull, tr("Run in the background"), tr("So sending books and keeping devices in step aren't cut off."), allowed.getValue(a)) { ask(a) }
                        Ask.UPDATES -> AskRow(a, Icons.Outlined.SystemUpdate, tr("Install updates"), tr("So KoLib can update itself."), allowed.getValue(a)) { ask(a) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (allAllowed) {
                        Button(onClick = onDone, modifier = Modifier.testTag("first-asks-continue")) { Text(tr("Continue")) }
                    } else {
                        // Before Allow all's been through them, Not now; after, what's left is left.
                        TextButton(onClick = onDone, modifier = Modifier.testTag("first-asks-later")) {
                            Text(if (tried.isEmpty() || allowingAll) tr("Not now") else tr("Continue"))
                        }
                        Button(
                            onClick = { tried.clear(); allowingAll = true },
                            modifier = Modifier.testTag("first-asks-all"),
                        ) { Text(tr("Allow all")) }
                    }
                }
            }
        }
    }
}

/** One thing to allow: what it is, why, and Allow (or, allowed, a tick). */
@Composable
private fun AskRow(ask: Ask, icon: ImageVector, title: String, why: String, allowed: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("ask-${ask.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (allowed) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("ask-${ask.key}-allowed")) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(tr("Allowed"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            TextButton(onClick = onAllow, modifier = Modifier.testTag("ask-${ask.key}-allow")) { Text(tr("Allow")) }
        }
    }
}
