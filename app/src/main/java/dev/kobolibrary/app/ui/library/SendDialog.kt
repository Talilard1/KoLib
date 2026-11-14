package dev.kobolibrary.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.kobolibrary.app.ui.components.phoneLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.kobo.CoverFitting
import dev.kobolibrary.app.kobo.SendFormat
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.ui.components.Fmt

/**
 * Before sending: which format, and what will happen to books the Kobo already has.
 * [freeBytes] is the Kobo's free space when it was read (null if unknown).
 */
@Composable
fun SendDialog(
    books: List<LibraryBook>,
    connected: Boolean,
    freeBytes: Long?,
    initialFormat: SendFormat,
    initialCovers: CoverFitting = CoverFitting.BLUR,
    onSend: (SendFormat, CoverFitting) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
    /** Where each book already is on the Kobo, if it is. */
    pathOnKobo: (LibraryBook) -> String? = { it.koboPath },
) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(initialFormat) }
    var covers by remember { mutableStateOf(initialCovers) }
    val onKobo = remember(books) { books.mapNotNull { pathOnKobo(it) } }
    val switching = onKobo.count { SendFormat.of(it) != format }
    val replacing = onKobo.size - switching
    val size = books.sumOf { it.fileSize }
    // A phone's dialog is too narrow for the choices side by side, so they go one under another.
    val phone = phoneLayout()
    val formatLabel = { f: SendFormat -> if (f == SendFormat.KEPUB) "Kepub (recommended)" else "EPUB" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
        title = { Text(if (books.size == 1) "Send “${books[0].title}” to the Kobo" else "Send ${books.size} books to the Kobo") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("File format", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (phone) {
                    RadioChoices(SendFormat.entries, format, formatLabel) { format = it }
                } else {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SendFormat.entries.forEachIndexed { i, f ->
                            SegmentedButton(
                                selected = format == f,
                                onClick = { format = f },
                                shape = SegmentedButtonDefaults.itemShape(i, SendFormat.entries.size),
                            ) { Text(formatLabel(f)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(format.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Covers that don't fit the Kobo's screen", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (phone) {
                    RadioChoices(CoverFitting.entries, covers, { it.label }) { covers = it }
                } else {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CoverFitting.entries.forEachIndexed { i, c ->
                            SegmentedButton(
                                selected = covers == c,
                                onClick = { covers = c },
                                shape = SegmentedButtonDefaults.itemShape(i, CoverFitting.entries.size),
                            ) { Text(c.label, maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(covers.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val asIs = books.count { it.coverAsIs }
                if (asIs > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            books.size == 1 -> "This book's cover will be sent unchanged."
                            asIs == 1 -> "1 book's cover will be sent unchanged."
                            else -> "$asIs books' covers will be sent unchanged."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (replacing > 0) {
                    Note(
                        Icons.Outlined.Info,
                        when {
                            books.size == 1 -> "This book is already on the Kobo. It will be updated, and you won't lose your place. " +
                                "If its chapters changed, the Kobo will treat it as a new book."
                            replacing == 1 -> "1 book is already on the Kobo. It will be updated, and you won't lose your place. " +
                                "If its chapters changed, the Kobo will treat it as a new book."
                            else -> "$replacing books are already on the Kobo. They will be updated, and you won't lose your place. " +
                                "If their chapters changed, the Kobo will treat them as new books."
                        },
                    )
                }
                if (switching > 0) {
                    Note(
                        Icons.Outlined.WarningAmber,
                        when {
                            books.size == 1 -> "This book is on the Kobo as ${if (format == SendFormat.KEPUB) "EPUB" else "kepub"}, so it will change format. " +
                                "The Kobo will treat it as a new book, so your reading progress in it will start over."
                            switching == 1 -> "1 book will change format. The Kobo will treat it as a new book, " +
                                "so your reading progress in it will start over."
                            else -> "$switching books will change format. The Kobo will treat them as new books, " +
                                "so your reading progress in them will start over."
                        },
                        warning = true,
                    )
                }
                Note(
                    Icons.Outlined.Info,
                    "Total size: ${Fmt.bytes(context, size)}." + (freeBytes?.let { " Free space on the Kobo: ${Fmt.bytes(context, it)}." } ?: ""),
                )
                if (!connected) Note(Icons.Outlined.WarningAmber, "The Kobo isn't connected. Plug it in and tap Connect on its screen.", warning = true)
            }
        },
        confirmButton = {
            if (connected) TextButton(onClick = { onSend(format, covers) }) { Text("Send") }
            else TextButton(onClick = onConnect) { Text("Go to Kobo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Choices one under another, each with a round button, for a dialog that's too narrow to put them side by side. */
@Composable
internal fun <T> RadioChoices(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { o ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = o == selected, role = Role.RadioButton, onClick = { onPick(o) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = o == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(o), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth().then(Modifier), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(8.dp))
}
