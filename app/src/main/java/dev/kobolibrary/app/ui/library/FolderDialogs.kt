package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.tr
import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.AppLanguage
import dev.kobolibrary.app.library.FolderScan
import dev.kobolibrary.app.ui.components.AlertDialog
import dev.kobolibrary.app.ui.components.Fmt

/** A folder chosen to add books from: being looked through, then how many books it has, to add them. */
@Composable
fun FolderScanDialog(scan: FolderScan, onAdd: () -> Unit, onChooseAnother: () -> Unit, onDismiss: () -> Unit) {
    val found = scan as? FolderScan.Found
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FolderOpen, null) },
        title = { Text(scan.folder) },
        text = {
            when (scan) {
                is FolderScan.Looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (scan.found == 0) tr("Looking for books…") else tr("Looking for books… {0} so far.", Fmt.plural(scan.found, "book")))
                }
                is FolderScan.Found -> Text(
                    when (scan.books.size) {
                        0 -> tr("No books found in this folder.")
                        1 -> tr("1 book found. It's skipped if it's already in your library.")
                        else -> tr("{0} books found. Ones already in your library are skipped.", scan.books.size)
                    },
                )
                is FolderScan.Failed -> Text(tr("Couldn't open this folder. Choose it again, or another one."))
                is FolderScan.Adding -> Unit
            }
        },
        confirmButton = {
            when {
                found != null && found.books.isNotEmpty() -> TextButton(onClick = onAdd) {
                    Text(if (found.books.size == 1) tr("Add 1 book") else tr("Add {0} books", found.books.size))
                }
                scan is FolderScan.Looking -> Unit
                else -> TextButton(onClick = onChooseAnother) { Text(tr("Choose another folder")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/**
 * The language KoLib is in: the device's, or another. KoLib restarts to change it, so it can't be changed
 * while it's [busy] sending or adding books, or saving to the Kobo.
 */
@Composable
fun LanguageDialog(onDismiss: () -> Unit, busy: Boolean = false) {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val device = remember { AppLanguage.deviceLanguage() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Language, null) },
        title = { Text(tr("Language")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                if (busy) {
                    Text(
                        tr("Changing the language restarts KoLib. Wait until it has finished sending or adding books."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                for (tag in AppLanguage.supported) {
                    val selected = tag == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(selected = selected, enabled = !busy, role = Role.RadioButton) {
                                onDismiss()
                                generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                                    .filterIsInstance<Activity>().firstOrNull()?.let { AppLanguage.choose(it, tag) }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = !busy)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(AppLanguage.nativeName(tag), style = MaterialTheme.typography.titleSmall)
                            val local = AppLanguage.localName(tag, context)
                            val note = listOfNotNull(
                                local.takeIf { it != AppLanguage.nativeName(tag) },
                                androidx.compose.ui.res.stringResource(dev.kobolibrary.app.R.string.intro_device_language).takeIf { tag == device },
                            ).joinToString(" · ")
                            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done")) } },
    )
}
