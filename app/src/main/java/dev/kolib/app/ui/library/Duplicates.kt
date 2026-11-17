package dev.kolib.app.ui.library

import dev.kolib.app.ui.components.FittedText
import dev.kolib.app.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import dev.kolib.app.ui.components.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.kolib.app.DuplicateChoice
import dev.kolib.app.HeldDuplicate
import dev.kolib.app.JobProgress
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.LibraryCover

/**
 * Asks what to do with files just added that the library already has: put each in place of the
 * book it matches, add it as a book of its own, or leave it out. [job] is the adding that found them,
 * for a line on how the rest went.
 */
@Composable
fun DuplicatesDialog(
    held: List<HeldDuplicate>,
    existing: (String) -> LibraryBook?,
    covers: LibraryCovers,
    job: JobProgress?,
    onDone: (Map<String, DuplicateChoice>) -> Unit,
) {
    val choices = remember(held) { mutableStateMapOf<String, DuplicateChoice>() }
    val choiceOf = { d: HeldDuplicate -> choices[d.key] ?: DuplicateChoice.SKIP }
    val skipAll = { onDone(emptyMap()) }
    val single = held.singleOrNull()
    AlertDialog(
        // Back leaves them all out, as before; a tap beside the dialog does nothing, so it isn't lost by accident.
        onDismissRequest = skipAll,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.ContentCopy, null) },
        title = { Text(if (single != null) tr("Already in the library") else tr("{0} books already in the library", held.size)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                job?.takeIf { it.finished }?.let { p ->
                    val others = listOfNotNull(
                        p.succeeded.takeIf { it > 0 }?.let { if (it == 1) tr("1 other book was added.") else tr("{0} other books were added.", it) },
                        p.failures.size.takeIf { it > 0 }?.let { tr("{0} couldn't be added. The log has the details.", it) },
                    )
                    if (others.isNotEmpty()) {
                        Text(others.joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (single != null) {
                    DuplicateRow(single, existing(single.existingId), covers, choice = null, onChoose = null)
                    Spacer(Modifier.height(14.dp))
                    Explanation()
                } else {
                    val all = held.map(choiceOf).distinct().singleOrNull()
                    Text(tr("Choose for all"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    ChoiceRow(all) { c -> held.forEach { choices[it.key] = c } }
                    Spacer(Modifier.height(8.dp))
                    Explanation()
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(held, key = { it.key }) { d ->
                            DuplicateRow(d, existing(d.existingId), covers, choiceOf(d)) { choices[d.key] = it }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (single != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = skipAll) { Text(tr("Skip")) }
                    TextButton(onClick = { onDone(mapOf(single.key to DuplicateChoice.KEEP_BOTH)) }) { Text(tr("Keep both")) }
                    TextButton(onClick = { onDone(mapOf(single.key to DuplicateChoice.REPLACE)) }) { Text(tr("Replace")) }
                }
            } else {
                TextButton(onClick = { onDone(held.associate { it.key to choiceOf(it) }) }) { Text(tr("Done")) }
            }
        },
    )
}

@Composable
private fun Explanation() {
    Text(
        tr("Replace: use the new file for this book. Its collections won't change.\nKeep both: add the new file as a separate book.\nSkip: don't add the new file."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChoiceRow(selected: DuplicateChoice?, onChoose: (DuplicateChoice) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        DuplicateChoice.entries.forEachIndexed { i, c ->
            SegmentedButton(
                selected = selected == c,
                onClick = { onChoose(c) },
                shape = SegmentedButtonDefaults.itemShape(i, DuplicateChoice.entries.size),
            ) { FittedText(c.label, style = MaterialTheme.typography.labelLarge, smallest = 0.7f) }
        }
    }
}

/** The file being added and the book it matches, with what to do with it when there are several. */
@Composable
private fun DuplicateRow(
    d: HeldDuplicate,
    book: LibraryBook?,
    covers: LibraryCovers,
    choice: DuplicateChoice?,
    onChoose: ((DuplicateChoice) -> Unit)?,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        if (book != null) {
            LibraryCover(book, covers, large = false, modifier = Modifier.width(if (onChoose == null) 64.dp else 48.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "“${d.title}”" + if (d.authors.isNotBlank()) tr(" by {0}", d.authors) else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                d.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val newSize = Fmt.bytes(context, d.size)
            Text(
                if (book == null) tr("New file: {0}\nThe matching book has since been removed from your library.", newSize)
                else buildString {
                    append(tr("New file: {0}\nIn your library: {1}", newSize, Fmt.bytes(context, book.fileSize)))
                    Fmt.date(book.addedAt)?.let { append(tr(", added {0}", it)) }
                    if (book.koboPath != null) append(tr(", on the Kobo"))
                    if (book.collections.isNotEmpty()) append(tr(", in {0}", Fmt.plural(book.collections.size, "collection")))
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (onChoose != null) {
                Spacer(Modifier.height(8.dp))
                ChoiceRow(choice, onChoose)
            }
        }
    }
}
