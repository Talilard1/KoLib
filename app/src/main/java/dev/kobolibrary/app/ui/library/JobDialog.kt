package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.tr
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.JobKind
import dev.kobolibrary.app.JobProgress
import dev.kobolibrary.app.ui.components.Fmt

/** Shows a running job (which can be hidden or stopped) and then what came of it. */
@Composable
fun JobDialog(
    p: JobProgress,
    onHide: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
    /** After adding from the Kobo: go and look at the books. */
    onOpenLibrary: (() -> Unit)? = null,
    /** After sending: how to eject. */
    onHowToEject: (() -> Unit)? = null,
    /** After sending: try the books that couldn't be made into kepubs again, as plain EPUB. */
    onSendAsEpub: ((List<String>) -> Unit)? = null,
) {
    val sending = p.kind == JobKind.SEND
    AlertDialog(
        onDismissRequest = { if (p.finished) onDone() else onHide() },
        title = {
            Text(
                when {
                    !p.finished -> p.title
                    p.cancelled -> tr("Stopped")
                    sending && p.failures.isEmpty() -> tr("Sent")
                    else -> tr("Done")
                }
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (!p.finished) {
                    LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(tr("{0} of {1}", p.done, p.total), style = MaterialTheme.typography.labelLarge)
                    p.current?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (sending) tr("Keep the Kobo plugged in until sending finishes. You can hide this window in the meantime.")
                        else tr("You can hide this window and keep using the app. Progress is shown at the top of the library."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val parts = buildList {
                        if (sending) {
                            add(if (p.succeeded == 1) tr("1 book sent.") else tr("{0} books sent.", p.succeeded))
                            if (p.alreadyThere > 0) add(if (p.alreadyThere == 1) tr("1 of them was already on the Kobo and was updated.") else tr("{0} of them were already on the Kobo and were updated.", p.alreadyThere))
                            if (p.failures.isNotEmpty()) add(tr("{0} couldn't be sent.", p.failures.size))
                        } else {
                            add(if (p.succeeded == 1) tr("1 book added.") else tr("{0} books added.", p.succeeded))
                            if (p.alreadyThere > 0) add(if (p.alreadyThere == 1) tr("1 was already in the library.") else tr("{0} were already in the library.", p.alreadyThere))
                            if (p.failures.isNotEmpty()) add(tr("{0} couldn't be added.", p.failures.size))
                        }
                        if (p.cancelled) (p.total - p.done).let { add(if (it == 1) tr("1 was skipped because you stopped.") else tr("{0} were skipped because you stopped.", it)) }
                    }
                    Text(parts.joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                    if (sending && p.succeeded > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr("Now eject the Kobo. Once it's unplugged, it adds the new books to its library. This takes a moment."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (p.coversAfterRestart.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (p.coversAfterRestart.size == 1) {
                                    tr("To see the new cover of “{0}”, restart the Kobo after ejecting it. It keeps showing a book's old cover until it restarts.", p.coversAfterRestart[0])
                                } else {
                                    tr("To see the new covers of {0} books, restart the Kobo after ejecting it. It keeps showing a book's old cover until it restarts.", p.coversAfterRestart.size)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (onHowToEject != null) {
                            TextButton(onClick = onHowToEject, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                                Text(tr("How to eject"))
                            }
                        }
                    }
                    if (p.failures.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(p.failures) { (name, reason) ->
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (sending && p.conversionFailedIds.isNotEmpty() && onSendAsEpub != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr("Books that couldn't be converted to kepub can be sent as EPUB instead."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (p.finished) TextButton(onClick = onDone) { Text("OK") }
            else TextButton(onClick = onHide) { Text(tr("Hide")) }
        },
        dismissButton = {
            when {
                !p.finished -> TextButton(onClick = onStop) { Text(tr("Stop")) }
                sending && p.conversionFailedIds.isNotEmpty() && onSendAsEpub != null ->
                    TextButton(onClick = { onSendAsEpub(p.conversionFailedIds) }) { Text(tr("Send them as EPUB")) }
                onOpenLibrary != null && p.succeeded > 0 -> TextButton(onClick = onOpenLibrary) { Text(tr("Open library")) }
            }
        },
    )
}
