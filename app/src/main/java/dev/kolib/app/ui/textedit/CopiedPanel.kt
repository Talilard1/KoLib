package dev.kolib.app.ui.textedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import dev.kolib.app.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.library.CopiedSections
import dev.kolib.app.tr
import dev.kolib.app.ui.components.AlertDialog
import java.text.DateFormat
import java.util.Date

/**
 * The sections copied out of books (a chapter, a title page, a contents page…), newest first: each can be put into the
 * book being edited, after the chapter open now, or deleted. They're kept, whatever book is open, until deleted.
 */
@Composable
fun CopiedPanel(state: TextEditState, items: List<CopiedSections.Item>, modifier: Modifier = Modifier, onClose: () -> Unit) {
    var pasting by remember { mutableStateOf<CopiedSections.Item?>(null) }
    var deleting by remember { mutableStateOf<CopiedSections.Item?>(null) }
    val here = state.chapter?.title
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Copied sections"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close copied sections")) }
        }
        Text(
            if (items.isEmpty()) tr("Nothing copied yet. In the chapter list, tap ⋮ beside a section, then Copy. Copied sections can be added to any book.")
            else if (here != null) tr("Added after “{0}”, with its pictures and styles.", here)
            else tr("Added with its pictures and styles."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                CopiedCard(
                    item,
                    canAdd = state.busy == null,
                    onAdd = { if (item.isContents) pasting = item else state.pasteSection(item.id, relist = false) },
                    onDelete = { deleting = item },
                )
            }
        }
    }

    pasting?.let { item ->
        AlertDialog(
            onDismissRequest = { pasting = null },
            icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, null) },
            title = { Text(tr("List this book's chapters?")) },
            text = {
                Text(
                    tr("“{0}” is a contents page from “{1}”. It can list this book's chapters instead, in the same style, or be added unchanged.", item.title, item.fromBook),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { pasting = null; state.pasteSection(item.id, relist = true) }) { Text(tr("List this book's chapters")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pasting = null; state.pasteSection(item.id, relist = false) }) { Text(tr("Add unchanged")) }
                    TextButton(onClick = { pasting = null }) { Text(tr("Cancel")) }
                }
            },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(tr("Delete “{0}” from copied sections?", item.title)) },
            text = { Text(tr("Books it was added to aren't affected."), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { deleting = null; state.deleteCopied(item.id) }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun CopiedCard(item: CopiedSections.Item, canAdd: Boolean, onAdd: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.title.ifBlank { tr("Untitled section") }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tr("From “{0}”", item.fromBook),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            if (item.isContents) tr("Contents page") else null,
                            when (item.words) { 0 -> null; 1 -> tr("1 word"); else -> tr("{0} words", "%,d".format(item.words)) },
                            when (item.pictures) { 0 -> null; 1 -> tr("1 picture"); else -> tr("{0} pictures", item.pictures) },
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.copiedAt)),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, tr("Delete “{0}” from copied sections", item.title)) }
            }
            Box(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                FilledTonalButton(onClick = onAdd, enabled = canAdd) {
                    Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Add to this book"))
                }
            }
        }
    }
}
