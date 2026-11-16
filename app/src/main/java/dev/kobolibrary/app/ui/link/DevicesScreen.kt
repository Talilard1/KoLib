package dev.kobolibrary.app.ui.link

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.link.LinkDiscovery
import dev.kobolibrary.app.link.LinkManager
import dev.kobolibrary.app.link.LinkSettings
import dev.kobolibrary.app.link.PairedDevice
import dev.kobolibrary.app.link.Pairing
import dev.kobolibrary.app.link.Transfer
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.GoldButton
import dev.kobolibrary.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the Devices screen shows. */
data class DevicesView(
    /** This device's name. */
    val me: String,
    /** Its addresses on the networks it's on (none: it isn't on one). */
    val addresses: List<String>,
    /** The port it takes connections on (0: it doesn't, now). */
    val port: Int,
    val paired: List<PairedDevice>,
    /** Devices heard lately, paired or not. */
    val nearby: List<LinkManager.Nearby>,
    val transfers: List<Transfer>,
    val settings: LinkSettings,
    /** Looking for KoLib at every address on the network now. */
    val looking: Boolean = false,
)

/** What can be done from the Devices screen. */
class DevicesActions(
    val pair: (LinkManager.Nearby) -> Unit,
    /** Looks for KoLib at an address typed in; the device found, or null. */
    val reach: suspend (String) -> LinkManager.Nearby?,
    val sendBooks: (PairedDevice) -> Unit,
    val sendFiles: (PairedDevice) -> Unit,
    val sendClipboard: (PairedDevice) -> Unit,
    val syncPlaces: (PairedDevice) -> Unit,
    val forget: (PairedDevice) -> Unit,
    val keepPlacesInStep: (Boolean) -> Unit,
    val stayReachable: (Boolean) -> Unit,
    /** Chooses books in the library to send to the Kobo over Wi-Fi. */
    val koboOverWifi: () -> Unit = {},
    /** Looks for KoLib at every address on the network (a hotspot may pass on no device's announcements). */
    val lookAgain: () -> Unit = {},
)

/** The Devices section: this device, the ones paired with it, the ones nearby to pair with, and how the link behaves. */
@Composable
fun DevicesScreen(link: LinkManager, actions: DevicesActions) {
    val paired by link.paired.collectAsStateWithLifecycle()
    val nearby by link.nearby.collectAsStateWithLifecycle()
    val live by link.transfers.collectAsStateWithLifecycle()
    var transfers by remember { mutableStateOf(live) }
    LaunchedEffect(live) {
        if (live.isEmpty()) delay(1_500)
        transfers = live
    }
    val settings by link.settings.collectAsStateWithLifecycle()
    val port by link.listeningPort.collectAsStateWithLifecycle()
    val looking by link.looking.collectAsStateWithLifecycle()
    // Nothing heard after a moment (a phone's hotspot often passes on no announcements): look at every address.
    LaunchedEffect(Unit) {
        delay(3_000)
        val pairedIds = link.paired.value.map { it.id }.toSet()
        val strangers = link.nearby.value.count { it.id !in pairedIds }
        if (strangers == 0 || pairedIds.any { !link.isHere(it) }) link.lookAround()
    }
    var addresses by remember { mutableStateOf(emptyList<String>()) }
    // The networks this device is on can change while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            addresses = withContext(Dispatchers.IO) { LinkDiscovery.addresses() }
            delay(3_000)
        }
    }
    DevicesContent(DevicesView(link.identity.name, addresses, port, paired, nearby, transfers, settings, looking), actions)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesContent(view: DevicesView, actions: DevicesActions) {
    var connecting by remember { mutableStateOf(false) }
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(76.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Devices, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(38.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(tr("Your devices"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Send books, files and text between KoLib on your phone and tablet, and keep your place in books the same on both."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // This device.
            SectionCard(tr("This device"), icon = if (tablet) Icons.Outlined.TabletAndroid else Icons.Outlined.PhoneAndroid) {
                Text(view.me, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                if (view.addresses.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WifiOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("Not on a network. Join a Wi-Fi network, or the other device's hotspot."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    val withPort = if (view.port > 0 && view.port != LinkManager.PORT) view.addresses.map { "$it:${view.port}" } else view.addresses
                    Text(
                        tr("On the network at {0}", withPort.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = view.transfers.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                // What was last shown stays while it shrinks away.
                var last by remember { mutableStateOf(view.transfers) }
                if (view.transfers.isNotEmpty()) last = view.transfers
                Transfers(last)
            }

            // Paired devices.
            SectionCard(tr("Paired"), icon = Icons.Outlined.Shield) {
                if (view.paired.isEmpty()) {
                    Text(
                        tr("None yet. Open KoLib on your other device, on the same Wi-Fi network as this one (or one's hotspot), then pair them below."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                view.paired.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PairedRow(d, here = view.nearby.any { it.id == d.id }, actions)
                }
            }

            // Devices to pair with.
            SectionCard(tr("Nearby"), icon = Icons.Outlined.Devices) {
                val unpaired = view.nearby.filter { n -> view.paired.none { it.id == n.id } }
                if (unpaired.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (view.addresses.isNotEmpty() && view.port > 0) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (view.addresses.isEmpty()) tr("Join a network to find your other devices.")
                            else tr("Looking for KoLib on other devices on this network…"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                unpaired.forEach { n ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.name, style = MaterialTheme.typography.titleMedium)
                            Text(n.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GoldButton(onClick = { actions.pair(n) }) { Text(tr("Pair")) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = actions.lookAgain,
                        enabled = !view.looking && view.addresses.isNotEmpty(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    ) { Text(if (view.looking) tr("Looking at every address…") else tr("Look again")) }
                    TextButton(onClick = { connecting = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                        Text(tr("Not found? Connect by address"))
                    }
                }
            }

            // The Kobo, with no cable: through its own web browser.
            SectionCard(tr("Your Kobo, over Wi-Fi"), icon = Icons.Outlined.Wifi) {
                Text(
                    tr("Send books to your Kobo without the cable, through its web browser: choose them in your library, tap Send to Kobo, then Over Wi-Fi."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("The Kobo adds them itself, so they can't go into collections that way, books already on it aren't updated, and how far you've read doesn't come back. Plug it in for those."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LinkButton(Icons.AutoMirrored.Outlined.SendToMobile, tr("Choose books")) { actions.koboOverWifi() }
            }

            SectionCard(tr("Settings")) {
                SwitchLine(
                    tr("Keep places in books in step"),
                    tr("When a paired device is found, each book goes to the later of the places it was left at on the two."),
                    view.settings.keepPlacesInStep, actions.keepPlacesInStep,
                )
                Spacer(Modifier.height(10.dp))
                SwitchLine(
                    tr("Stay reachable when KoLib is closed"),
                    tr("Paired devices can send to this one while KoLib isn't open. A notification shows while it is."),
                    view.settings.stayReachable, actions.stayReachable,
                )
            }

            Text(
                tr("What's sent between your devices is encrypted, and only paired devices can send. It goes over your own network, not the internet."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
    if (connecting) ConnectByAddressDialog(actions.reach, onDismiss = { connecting = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PairedRow(d: PairedDevice, here: Boolean, actions: DevicesActions) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(8.dp),
                    ) {}
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (here) tr("Here") else tr("Not found on this network"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("More")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Forget this device")) },
                        leadingIcon = { Icon(Icons.Outlined.LinkOff, null) },
                        onClick = {
                            menu = false
                            actions.forget(d)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkButton(Icons.AutoMirrored.Outlined.SendToMobile, tr("Send books")) { actions.sendBooks(d) }
            LinkButton(Icons.Outlined.UploadFile, tr("Send files")) { actions.sendFiles(d) }
            LinkButton(Icons.Outlined.ContentPaste, tr("Send clipboard")) { actions.sendClipboard(d) }
            LinkButton(Icons.Outlined.Sync, tr("Bring places into step")) { actions.syncPlaces(d) }
        }
    }
}

@Composable
private fun LinkButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SwitchLine(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun Transfers(transfers: List<Transfer>) {
    SectionCard(tr("Sending and receiving"), icon = Icons.Outlined.Sync) {
        transfers.forEach { t ->
            Text(
                if (t.outgoing) tr("“{0}” to {1}", t.what, t.device) else tr("“{0}” from {1}", t.what, t.device),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (t.items > 1) Text(
                tr("{0} of {1}", t.item, t.items),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { t.fraction }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("${t.percent}%", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Typing in where KoLib is on the other device, for a network where devices can't hear each other. */
@Composable
private fun ConnectByAddressDialog(reach: suspend (String) -> LinkManager.Nearby?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var trying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val go = {
        if (text.isNotBlank() && !trying) {
            trying = true
            failed = false
            scope.launch {
                val found = reach(text)
                trying = false
                if (found != null) onDismiss() else failed = true
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Connect by address")) },
        text = {
            Column {
                Text(
                    tr("On the other device, the Devices section shows where it is on the network. Type that in here."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        failed = false
                    },
                    singleLine = true,
                    placeholder = { Text("192.168.1.20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = failed,
                    supportingText = if (failed) ({ Text(tr("No KoLib found there. Check that it's open on the other device.")) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = go, enabled = text.isNotBlank() && !trying) {
                if (trying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(tr("Connect"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Pairing, over everything: the code both devices show, and whether to pair. */
@Composable
fun PairingDialog(p: Pairing, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (p.code == null || p.failed != null) onAnswer(false) },
        title = {
            Text(
                when {
                    p.failed != null -> tr("Not paired")
                    p.code == null -> tr("Connecting to {0}…", p.name)
                    p.confirmed -> tr("Waiting for {0}…", p.name)
                    p.incoming -> tr("{0} wants to pair", p.name)
                    else -> tr("Pair with {0}?", p.name)
                }
            )
        },
        text = {
            Column {
                when {
                    p.failed != null -> Text(p.failed, style = MaterialTheme.typography.bodyLarge)
                    p.code == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Keep KoLib open on {0}.", p.name), style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> {
                        Text(
                            if (p.confirmed) tr("Say yes on {0} too, if it shows this code.", p.name)
                            else tr("Check that {0} shows the same code. If it doesn't, don't pair.", p.name),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            p.code.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                p.failed != null -> TextButton(onClick = { onAnswer(false) }) { Text(tr("OK")) }
                p.code != null && !p.confirmed -> TextButton(onClick = { onAnswer(true) }) { Text(tr("Pair")) }
                else -> {}
            }
        },
        dismissButton = {
            if (p.failed == null) TextButton(onClick = { onAnswer(false) }) {
                Text(if (p.incoming && p.code != null && !p.confirmed) tr("Don't pair") else tr("Cancel"))
            }
        },
    )
}

/** Which paired device to send to, when there's more than one. */
@Composable
fun ChooseDeviceDialog(devices: List<PairedDevice>, here: Set<String>, what: String, onPick: (PairedDevice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Send to which device?")) },
        text = {
            Column {
                Text(what, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                devices.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Devices, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (d.id in here) tr("Here") else tr("Not found on this network"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d.id in here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** What the library can do with the user's other devices; null when none is paired. */
class LinkActions(val sendBooks: (List<String>) -> Unit)

val LocalLinkActions = androidx.compose.runtime.staticCompositionLocalOf<LinkActions?> { null }
