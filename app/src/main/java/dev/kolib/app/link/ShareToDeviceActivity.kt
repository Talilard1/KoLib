package dev.kolib.app.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kolib.app.KoLibApp
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import dev.kolib.app.ui.theme.KoLibTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Send to device" in other apps' Share: text goes to a paired device's clipboard, files to its Downloads. Offered only
 * once a device is paired. It stays open while sending, so what was shared can still be read. Shared to one of the
 * devices at the top of Share (their shortcuts), it sends straight to that one.
 *
 * Opened from the link's notification or a device's shortcut ([ACTION_CHOOSE]), it asks what to send: books (chosen in
 * the library, in KoLib), files (chosen here) or what's on the clipboard. From the notification's Send clipboard
 * ([ACTION_CLIPBOARD]), it sends the clipboard straight away: to the one device paired, or the one chosen.
 */
class ShareToDeviceActivity : ComponentActivity() {

    companion object {
        /** What to send is chosen here (from the link's notification, or a device's shortcut). */
        const val ACTION_CHOOSE = "dev.kolib.app.link.CHOOSE_WHAT_TO_SEND"
        /** The id of the device to send to, when it's been chosen already. */
        const val EXTRA_DEVICE = "dev.kolib.app.link.DEVICE"
        /** For KoLib: choose books in the library to send to the device [EXTRA_DEVICE]. */
        const val ACTION_SEND_BOOKS = "dev.kolib.app.link.SEND_BOOKS"
        /** What's on the clipboard is sent (from the link's notification). */
        const val ACTION_CLIPBOARD = "dev.kolib.app.link.SEND_CLIPBOARD"

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

        /** Asks what to send, to a device chosen next (or [device]). */
        fun chooseIntent(context: Context, device: String? = null): Intent =
            Intent(context, ShareToDeviceActivity::class.java).setAction(ACTION_CHOOSE).putExtra(EXTRA_DEVICE, device)

        /** Sends what's on the clipboard, to the device paired (or one chosen next). */
        fun clipboardIntent(context: Context): Intent = Intent(context, ShareToDeviceActivity::class.java).setAction(ACTION_CLIPBOARD)
    }

    private val link get() = (application as KoLibApp).link

    /** What's to be sent: text, files, or books (chosen in the library). */
    private data class Shared(val text: String?, val uris: List<Uri>, val books: Boolean = false) {
        val nothing get() = text == null && uris.isEmpty() && !books
    }

    private var shared by mutableStateOf(Shared(null, emptyList()))
    /** Opened to choose what to send, rather than with something shared. */
    private var choosing = false
    /** The device to send to, chosen already: a shortcut's. */
    private var chosen: String? = null
    /** Sending has begun. Kept if Android makes the screen again, so nothing is then sent a second time unasked. */
    private var begun = false
    /** Opened to send the clipboard: it's read as soon as this is in front (Android allows it no sooner). */
    private var forClipboard by mutableStateOf(false)
    /** Opened to send the clipboard, and there was nothing on it. */
    private var clipboardEmpty by mutableStateOf(false)

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) shared = Shared(null, uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        forClipboard = intent?.action == ACTION_CLIPBOARD && savedInstanceState == null
        choosing = intent?.action == ACTION_CHOOSE || intent?.action == ACTION_CLIPBOARD
        chosen = intent?.getStringExtra(EXTRA_DEVICE) ?: DeviceShortcuts.deviceOf(intent?.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))
        shared = savedInstanceState?.let { b ->
            Shared(
                b.getString("text"),
                BundleCompat.getParcelableArrayList(b, "uris", Uri::class.java).orEmpty(),
                b.getBoolean("books"),
            )
        } ?: if (choosing) Shared(null, emptyList()) else read(intent)
        begun = savedInstanceState?.getBoolean("begun") == true
        val theme = (application as KoLibApp).themeSettings
        setContent {
            val choice by theme.choice.collectAsStateWithLifecycle()
            KoLibTheme(choice) { ShareSheet() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && forClipboard) {
            forClipboard = false
            val text = clipboardText()
            if (text == null) clipboardEmpty = true else shared = Shared(text, emptyList())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("text", shared.text)
        outState.putParcelableArrayList("uris", ArrayList(shared.uris))
        outState.putBoolean("books", shared.books)
        outState.putBoolean("begun", begun)
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

    /** The clipboard's text; null when there's none. Read while this is in front, as Android allows. */
    private fun clipboardText(): String? = runCatching {
        getSystemService(android.content.ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    @Composable
    private fun ShareSheet() {
        val paired by link.paired.collectAsStateWithLifecycle()
        val nearby by link.nearby.collectAsStateWithLifecycle()
        val transfers by link.transfers.collectAsStateWithLifecycle()
        var sending by remember { mutableStateOf<String?>(null) }
        var said by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        // What came of the sending; what the link says about anything else (places kept in step meanwhile) isn't for here.
        LaunchedEffect(Unit) { link.messages.collect { if (sending != null) said = it } }
        LaunchedEffect(clipboardEmpty) { if (clipboardEmpty) said = tr("The clipboard is empty.") }
        val what = shared
        val nothing = what.nothing
        // The device chosen already, while it's still paired; with only one paired, when choosing what to send, that one.
        val target = chosen?.let { id -> paired.firstOrNull { it.id == id } } ?: if (choosing) paired.singleOrNull() else null

        fun send(d: PairedDevice) {
            begun = true
            DeviceShortcuts.used(this, d)
            if (what.books) {
                // Books are chosen in the library, in KoLib, which sends them.
                startActivity(
                    Intent(this, dev.kolib.app.MainActivity::class.java)
                        .setAction(ACTION_SEND_BOOKS)
                        .putExtra(EXTRA_DEVICE, d.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
                return
            }
            sending = d.name
            scope.launch {
                val job = try {
                    if (what.text != null) link.sendText(d, what.text)
                    else link.sendFiles(d, withContext(Dispatchers.IO) { what.uris.map { LinkFiles.fromUri(this@ShareToDeviceActivity, it) } })
                } catch (e: Exception) {
                    said = tr("Couldn't send to {0}: {1}", d.name, e.message ?: e.javaClass.simpleName)
                    return@launch
                }
                job.join()
            }
        }
        // Sent to the device chosen already as soon as there's something to send.
        LaunchedEffect(what, target) {
            if (target != null && !nothing && sending == null && said == null && !begun) send(target)
        }

        val busy = sending
        val t = transfers.firstOrNull()
        ShareSheetContent(
            title = target?.let { tr("Send to {0}", it.name) } ?: tr("Send to device"),
            about = when {
                forClipboard && nothing -> ""
                choosing && nothing -> tr("What would you like to send?")
                nothing -> tr("Nothing here can be sent.")
                what.books -> tr("Books from your library: choose them in KoLib.")
                what.text != null -> tr("Text to the other device's clipboard: “{0}”", what.text.take(80) + if (what.text.length > 80) "…" else "")
                what.uris.size == 1 -> tr("1 file to the other device's Downloads.")
                else -> tr("{0} files to the other device's Downloads.", what.uris.size)
            },
            body = when {
                said != null -> SheetBody.Said(said.orEmpty())
                busy != null -> SheetBody.Sending(busy, t?.takeIf { it.total > 0 || it.items > 1 }?.fraction)
                paired.isEmpty() -> SheetBody.NoDevices
                // Waiting to read the clipboard.
                forClipboard && nothing -> SheetBody.Empty
                choosing && nothing -> SheetBody.ChooseWhat
                // (Also after the screen was made again part-way: a tap sends again, nothing does by itself.)
                !nothing && (target == null || begun) -> SheetBody.ChooseDevice(paired, nearby.map { it.id }.toSet())
                else -> SheetBody.Empty
            },
            closeLabel = if (said != null || (nothing && !choosing) || paired.isEmpty()) tr("Close") else tr("Cancel"),
            canClose = sending == null || said != null,
            onBooks = { shared = Shared(null, emptyList(), books = true) },
            onFiles = { runCatching { pickFiles.launch(arrayOf("*/*")) }.onFailure { said = tr("This device has no file picker.") } },
            onClipboard = {
                val text = clipboardText()
                if (text == null) said = tr("The clipboard is empty.") else shared = Shared(text, emptyList())
            },
            onDevice = ::send,
            onClose = { finish() },
        )
    }
}

/** What the sheet shows under what's to be sent. */
internal sealed interface SheetBody {
    /** What came of it. */
    data class Said(val text: String) : SheetBody
    /** Under way, to the device named; how far ([fraction]), when that's known. */
    data class Sending(val to: String, val fraction: Float?) : SheetBody
    data object NoDevices : SheetBody
    /** Books, files or the clipboard. */
    data object ChooseWhat : SheetBody
    /** The paired [devices]; those [here] found on this network. */
    data class ChooseDevice(val devices: List<PairedDevice>, val here: Set<String>) : SheetBody
    data object Empty : SheetBody
}

/** "Send to device": a card over whatever's behind it (a tap beside it closes it, when it can be). */
@Composable
internal fun ShareSheetContent(
    title: String,
    about: String,
    body: SheetBody,
    closeLabel: String,
    canClose: Boolean,
    onBooks: () -> Unit = {},
    onFiles: () -> Unit = {},
    onClipboard: () -> Unit = {},
    onDevice: (PairedDevice) -> Unit = {},
    onClose: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null) { if (canClose) onClose() }
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
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                when (body) {
                    is SheetBody.Said -> Text(body.text, style = MaterialTheme.typography.bodyLarge)
                    is SheetBody.Sending -> {
                        Text(tr("Sending to {0}…", body.to), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        if (body.fraction != null) LinearProgressIndicator(progress = { body.fraction }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    SheetBody.NoDevices -> Text(tr("No paired devices. Pair one in KoLib, under Devices."), style = MaterialTheme.typography.bodyLarge)
                    SheetBody.ChooseWhat -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SendChoice(Icons.AutoMirrored.Outlined.SendToMobile, tr("Send books"), onBooks)
                        SendChoice(Icons.Outlined.UploadFile, tr("Send files"), onFiles)
                        SendChoice(Icons.Outlined.ContentPaste, tr("Send clipboard"), onClipboard)
                    }
                    is SheetBody.ChooseDevice -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        body.devices.forEach { d ->
                            val here = d.id in body.here
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onDevice(d) }
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
                    SheetBody.Empty -> Unit
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose, enabled = canClose) { Text(closeLabel) }
                }
            }
        }
    }
}

/** One thing that can be sent, when choosing what. */
@Composable
private fun SendChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
