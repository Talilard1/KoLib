package dev.kolib.app.ui.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.link.Transfer
import dev.kolib.app.tr

/**
 * What's being sent to (or taken in from) the user's other devices, with how far each has got: the Devices
 * screen's progress bars, for the top of the library, where sending is started from.
 */
@Composable
fun TransferStrip(transfers: List<Transfer>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = transfers.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        // What was last shown stays while it shrinks away.
        var last by remember { mutableStateOf(transfers) }
        if (transfers.isNotEmpty()) last = transfers
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("transfer-strip")) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                last.forEachIndexed { i, t ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        (if (t.outgoing) tr("“{0}” to {1}", t.what, t.device) else tr("“{0}” from {1}", t.what, t.device)) +
                            (if (t.items > 1) " · " + tr("{0} of {1}", t.item, t.items) else ""),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { t.fraction }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text("${t.percent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
