package dev.kobolibrary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.kobo.KoboWifiServer
import dev.kobolibrary.app.kobo.WifiBook
import dev.kobolibrary.app.kobo.WifiState
import dev.kobolibrary.app.link.LinkDiscovery
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.Fmt
import dev.kobolibrary.app.ui.components.GoldButton
import dev.kobolibrary.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sending books to the Kobo over Wi-Fi, through its own web browser: where to go on the Kobo, and how each book is
 * getting on. The page for the Kobo runs while this is open (and the screen stays on).
 */
@Composable
fun KoboWifiScreen(server: KoboWifiServer, onClose: () -> Unit) {
    DisposableEffect(server) {
        server.start()
        onDispose { server.stop() }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var addresses by remember { mutableStateOf(emptyList<String>()) }
    // The networks this device is on can change while it's open.
    LaunchedEffect(Unit) {
        while (true) {
            addresses = withContext(Dispatchers.IO) { LinkDiscovery.addresses() }
            delay(3_000)
        }
    }
    val port by server.port.collectAsStateWithLifecycle()
    val states by server.states.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    KoboWifiContent(server.books, states, addresses, port, onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoboWifiContent(books: List<WifiBook>, states: Map<Int, WifiState>, addresses: List<String>, port: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(tr("Send to the Kobo over Wi-Fi")) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val sent = books.count { states[it.index] is WifiState.Sent }
                if (books.isNotEmpty() && sent == books.size) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (sent == 1) tr("The book is on your Kobo. You can close this now.") else tr("All the books are on your Kobo. You can close this now."),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                SectionCard(tr("On your Kobo"), icon = Icons.Outlined.Wifi) {
                    Step(1, tr("Connect it to the same Wi-Fi network as this device, or to this device's hotspot."))
                    Spacer(Modifier.height(10.dp))
                    Step(2, tr("Open its web browser: More, then Beta features, then Web browser."))
                    Spacer(Modifier.height(10.dp))
                    Step(3, tr("Type this address at the top, then tap each book to download it:"))
                    Spacer(Modifier.height(12.dp))
                    if (addresses.isEmpty() || port == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WifiOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (port == 0 && addresses.isNotEmpty()) tr("The page for the Kobo couldn't be started.")
                                else tr("This device isn't on a network. Join a Wi-Fi network, or turn on its hotspot."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        SelectionContainer {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                addresses.forEach { a ->
                                    Text(
                                        "$a:$port",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (addresses.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("This device is on more than one network: use the address of the one the Kobo is on."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                SectionCard(if (books.size == 1) tr("1 book") else tr("{0} books", books.size)) {
                    books.forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        BookLine(b, states[b.index] ?: WifiState.Waiting, context)
                    }
                }

                Note(
                    Icons.Outlined.WarningAmber,
                    tr("Over Wi-Fi the Kobo adds the books itself, so KoLib can't put them in collections, update books that are already on it, or bring back how far you've read. Plug the Kobo in for those."),
                    warning = true,
                )
                Note(Icons.Outlined.Info, tr("Keep KoLib open on this screen until the books are on the Kobo. If a download stops part way, close the Kobo's browser and open it again before the next one."))
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    GoldButton(onClick = onClose) { Text(tr("Done")) }
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun BookLine(b: WifiBook, state: WifiState, context: android.content.Context) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(b.author, b.kind).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            when (state) {
                is WifiState.Sent -> Icon(Icons.Outlined.CheckCircle, tr("Downloaded"), tint = MaterialTheme.colorScheme.primary)
                is WifiState.Making -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> {}
            }
        }
        Spacer(Modifier.height(4.dp))
        val (line, error) = when (state) {
            WifiState.Waiting -> tr("Waiting to be made ready") to false
            WifiState.Making -> tr("Getting it ready…") to false
            is WifiState.Ready -> tr("Ready to download · {0}", Fmt.bytes(context, state.bytes)) to false
            is WifiState.Sending -> tr("Downloading · {0}%", if (state.total > 0) (state.done * 100 / state.total).toInt() else 0) to false
            is WifiState.Sent -> tr("On the Kobo") to false
            is WifiState.Failed -> tr("It couldn't be made ready: {0}", state.reason) to true
        }
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                error -> MaterialTheme.colorScheme.error
                state is WifiState.Sent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (state is WifiState.Sending) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warning: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
