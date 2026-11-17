package dev.kobolibrary.app.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.KoboLibraryApp
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.theme.KoboLibraryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Send to device" in other apps' Share: text goes to a paired device's clipboard, files to its Downloads. Offered only
 * once a device is paired. It stays open while sending, so what was shared can still be read.
 */
class ShareToDeviceActivity : ComponentActivity() {

    companion object {
        /** Offers "Send to device" in other apps' Share, or stops offering it. */
        fun offer(context: Context, on: Boolean) {
            runCatching {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context, ShareToDeviceActivity::class.java),
                    if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }.onFailure { AppLog.w("Link", "Couldn't change whether Share offers KoLib", it) }
        }
    }

    private val link get() = (application as KoboLibraryApp).link

    /** What was shared: text, or files. */
    private class Shared(val text: String?, val uris: List<Uri>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = read(intent)
        val theme = (application as KoboLibraryApp).themeSettings
        setContent {
            val choice by theme.choice.collectAsStateWithLifecycle()
            KoboLibraryTheme(choice) { ShareSheet(shared) }
        }
    }

    override fun onStart() {
        super.onStart()
        link.start()
    }

    override fun onStop() {
        link.stop()
        super.onStop()
    }

    private fun read(intent: Intent?): Shared {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        val text = if (uris.isEmpty()) intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } else null
        return Shared(text, uris)
    }

    @Composable
    private fun ShareSheet(shared: Shared) {
        val paired by link.paired.collectAsStateWithLifecycle()
        val nearby by link.nearby.collectAsStateWithLifecycle()
        val transfers by link.transfers.collectAsStateWithLifecycle()
        var sending by remember { mutableStateOf<String?>(null) }
        var said by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { link.messages.collect { said = it } }
        val nothing = shared.text == null && shared.uris.isEmpty()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { if (sending == null || said != null) finish() }
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(tr("Send to device"), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            nothing -> tr("Nothing here can be sent.")
                            shared.text != null -> tr("Text to the other device's clipboard: “{0}”", shared.text.take(80) + if (shared.text.length > 80) "…" else "")
                            shared.uris.size == 1 -> tr("1 file to the other device's Downloads.")
                            else -> tr("{0} files to the other device's Downloads.", shared.uris.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    val busy = sending
                    when {
                        said != null -> Text(said.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        busy != null -> {
                            val t = transfers.firstOrNull()
                            Text(tr("Sending to {0}…", busy), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(10.dp))
                            if (t != null && (t.total > 0 || t.items > 1)) LinearProgressIndicator(progress = { t.fraction }, Modifier.fillMaxWidth())
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        paired.isEmpty() -> Text(
                            tr("No paired devices. Pair one in KoLib, under Devices."),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        !nothing -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            paired.forEach { d ->
                                val here = nearby.any { it.id == d.id }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sending = d.name
                                            scope.launch {
                                                val job = try {
                                                    if (shared.text != null) link.sendText(d, shared.text)
                                                    else link.sendFiles(d, withContext(Dispatchers.IO) { shared.uris.map { LinkFiles.fromUri(this@ShareToDeviceActivity, it) } })
                                                } catch (e: Exception) {
                                                    said = tr("Couldn't send to {0}: {1}", d.name, e.message ?: e.javaClass.simpleName)
                                                    return@launch
                                                }
                                                job.join()
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Devices, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (here) tr("Here") else tr("Not found on this network yet"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { finish() }, enabled = sending == null || said != null) {
                            Text(if (said != null || nothing || paired.isEmpty()) tr("Close") else tr("Cancel"))
                        }
                    }
                }
            }
        }
    }
}
