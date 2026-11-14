package dev.kobolibrary.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.SaveAlt
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.epub.AssemblyException
import dev.kobolibrary.app.epub.BookAssembly
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.ui.components.LibraryCover

/** Sensible first answers for the merge dialog, kept apart so they can be tested. */
internal object MakeBooks {

    /** The books in reading order: by number when they're all numbered in one series, else as picked. */
    fun mergeOrder(books: List<LibraryBook>): List<LibraryBook> {
        val series = books.map { it.meta.series?.trim()?.lowercase()?.ifEmpty { null } }.distinct()
        return if (series.size == 1 && series[0] != null && books.all { it.meta.seriesIndex != null }) books.sortedBy { it.meta.seriesIndex } else books
    }

    private fun number(d: Double): String = if (d == Math.floor(d) && d < 1e9) d.toLong().toString() else d.toString()

    /** "The Expanse: Books 1–3", "Dracula & Carmilla", or the author's omnibus. */
    fun mergedTitle(books: List<LibraryBook>): String {
        if (books.isEmpty()) return ""
        // One series for them all, spelled as most of them spell it (with capitals, if it's a tie).
        val spellings = books.map { it.meta.series?.trim().orEmpty() }
        val series = spellings.takeIf { all -> all.map { it.lowercase() }.distinct().size == 1 }
            ?.groupingBy { it }?.eachCount()?.entries
            ?.maxWithOrNull(compareBy<Map.Entry<String, Int>>({ it.value }, { e -> e.key.count { it.isUpperCase() } }))?.key
        if (!series.isNullOrEmpty()) {
            val numbers = books.mapNotNull { it.meta.seriesIndex }.sorted()
            if (numbers.size == books.size) {
                val whole = numbers.all { it == Math.floor(it) }
                val run = whole && numbers.zipWithNext().all { (a, b) -> b - a == 1.0 }
                return when {
                    run -> "$series: Books ${number(numbers.first())}–${number(numbers.last())}"
                    numbers.size == 2 -> "$series: Books ${number(numbers[0])} & ${number(numbers[1])}"
                    else -> "$series: Books " + numbers.dropLast(1).joinToString(", ") { number(it) } + " & " + number(numbers.last())
                }
            }
            return "$series Omnibus"
        }
        return when (books.size) {
            2 -> "${books[0].title} & ${books[1].title}"
            3 -> "${books[0].title}, ${books[1].title} & ${books[2].title}"
            else -> {
                val authors = books.map { b -> b.meta.authors.map { it.lowercase() }.toSet() }.distinct()
                val author = books[0].meta.authors.singleOrNull()?.takeIf { authors.size == 1 }
                if (author != null) "$author Omnibus" else "${books[0].title} and Others"
            }
        }
    }

    /** Everyone who wrote them, in order: "A & B", or "Various" for a crowd. */
    fun mergedAuthor(books: List<LibraryBook>): String {
        val all = books.flatMap { it.meta.authors }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        return if (all.size > 4) "Various" else all.joinToString(" & ")
    }
}

/** A new, empty book: its title and author, then it opens in the text editor. */
@Composable
fun NewBookDialog(onMake: (title: String, author: String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null) },
        title = { Text("Write a new book") },
        text = {
            Column {
                Text(
                    "The book opens in the text editor with one empty chapter. You can add more chapters from the chapter list. A simple cover is made for it, and you can change it later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    supportingText = { Text("For more than one author, put & between the names") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onMake(title, author) }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Several books put together as one: their order, the new book's title and author, and its cover.
 * [onMerge] gets the ids in order and the index of the book whose cover to use (null to draw one).
 */
@Composable
fun MergeDialog(
    books: List<LibraryBook>,
    covers: LibraryCovers,
    onMerge: (ids: List<String>, title: String, author: String, coverFrom: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val order = remember { MakeBooks.mergeOrder(books).toMutableStateList() }
    var title by remember { mutableStateOf(MakeBooks.mergedTitle(order)) }
    var titleTyped by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf(MakeBooks.mergedAuthor(order)) }
    var authorTyped by remember { mutableStateOf(false) }
    /** The book whose cover to use; null draws a new one with the new title. */
    var coverOf by remember { mutableStateOf<String?>(null) }
    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order.add(to, order.removeAt(from))
        if (!titleTyped) title = MakeBooks.mergedTitle(order)
        if (!authorTyped) author = MakeBooks.mergedAuthor(order)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Merge, null) },
        title = { Text("Combine ${books.size} books") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Order", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                order.forEachIndexed { i, b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                        LibraryCover(b, covers, large = false, modifier = Modifier.width(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(b.authorLine, b.meta.seriesLabel).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, "Move “${b.title}” up") }
                        IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move “${b.title}” down") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleTyped = true },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it; authorTyped = true },
                    label = { Text("Author") },
                    supportingText = { Text("For more than one author, put & between the names") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Cover", style = MaterialTheme.typography.titleSmall)
                CoverChoice(selected = coverOf == null, text = "Make a new cover with the title", onClick = { coverOf = null })
                order.filter { it.hasCover }.forEach { b ->
                    CoverChoice(selected = coverOf == b.id, text = "Use the cover of “${b.title}”", onClick = { coverOf = b.id })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "The new book contains all their chapters, in this order. Its contents list each book with its chapters under it. " +
                            "It's added to all of their collections. The original books aren't changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMerge(order.map { it.id }, title, author, coverOf?.let { id -> order.indexOfFirst { it.id == id }.takeIf { it >= 0 } }) },
                enabled = title.isNotBlank(),
            ) { Text("Combine") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CoverChoice(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One book cut into several: first where each new book starts (the chapters that look like the
 * start of a book are ticked already), then their titles and whether they're numbered as a series.
 */
@Composable
fun SplitBookDialog(
    book: LibraryBook,
    loadPages: suspend () -> List<BookAssembly.Page>,
    onSplit: (cuts: List<Int>, titles: List<String>, series: String?) -> Unit,
    onDismiss: () -> Unit,
    /** For previews: the pages, already loaded. */
    initialPages: List<BookAssembly.Page>? = null,
    initialStep: Int = 0,
) {
    var pages by remember { mutableStateOf(initialPages) }
    var problem by remember { mutableStateOf<String?>(null) }
    val cuts = remember { mutableStateMapOf<Int, Boolean>().apply { initialPages?.let { p -> BookAssembly.suggestedCuts(p).forEach { put(it, true) } } } }
    var suggested by remember { mutableIntStateOf(cuts.size) }
    var step by remember { mutableIntStateOf(initialStep) }
    /** Titles typed, by the page each book starts at (so changing other cuts keeps them). */
    val typed = remember { mutableStateMapOf<Int, String>() }
    var numbered by remember { mutableStateOf(true) }
    var series by remember { mutableStateOf(book.title) }
    LaunchedEffect(book.id) {
        if (pages != null) return@LaunchedEffect
        try {
            val p = loadPages()
            if (p.size < 2) problem = "“${book.title}” has only one chapter, so it can't be split into books. First split the chapter in the text editor."
            BookAssembly.suggestedCuts(p).forEach { cuts[it] = true }
            suggested = cuts.size
            pages = p
        } catch (e: AssemblyException) {
            problem = e.message
        } catch (e: Exception) {
            problem = "The book couldn't be read: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    val list = pages
    val sortedCuts = cuts.keys.sorted()
    val starts = listOf(0) + sortedCuts
    val defaults = remember(list, sortedCuts) { list?.let { BookAssembly.partTitles(it, sortedCuts, book.title) }.orEmpty() }
    fun titleOf(k: Int) = typed[starts[k]] ?: defaults.getOrElse(k) { "${book.title} (part ${k + 1})" }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) },
        title = { Text(if (step == 0) "Split “${book.title}”" else "Name the ${starts.size} books") },
        text = {
            when {
                problem != null -> Text(problem!!, style = MaterialTheme.typography.bodyMedium)
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                step == 0 -> Column {
                    Text(
                        "Tick the chapters where each new book starts." +
                            if (suggested > 0) " Chapters that look like the start of a book are already ticked." else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(list, key = { it.index }) { p ->
                            val startsBook = p.index == 0 || cuts.containsKey(p.index)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = startsBook,
                                        enabled = p.index > 0,
                                        role = Role.Checkbox,
                                        onValueChange = { on -> if (on) cuts[p.index] = true else cuts.remove(p.index) },
                                    )
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = startsBook, onCheckedChange = null, enabled = p.index > 0)
                                Spacer(Modifier.width(4.dp + 14.dp * ((p.level ?: 1).coerceIn(0, 3))))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.title,
                                        style = if (p.level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                        fontStyle = if (!p.linear) FontStyle.Italic else null,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(
                                            if (p.words == 1) "1 word" else "%,d words".format(p.words),
                                            if (!p.linear) "only opened from links" else null,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (startsBook) {
                                    Text(
                                        "Book ${starts.indexOf(p.index) + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (sortedCuts.isEmpty()) "Nothing ticked yet. The first book always starts at the beginning." else "This will make ${starts.size} books.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = numbered, role = Role.Switch, onValueChange = { numbered = it })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Number them as a series", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = numbered, onCheckedChange = null)
                    }
                    if (numbered) {
                        OutlinedTextField(
                            value = series,
                            onValueChange = { series = it },
                            label = { Text("Series") },
                            supportingText = { Text("They'll be numbered 1, 2, 3… in this order") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    starts.forEachIndexed { k, start ->
                        val end = starts.getOrNull(k + 1) ?: list.size
                        val words = list.subList(start, end).sumOf { it.words }
                        OutlinedTextField(
                            value = titleOf(k),
                            onValueChange = { typed[start] = it },
                            label = { Text("Book ${k + 1}") },
                            supportingText = { Text("%,d words, starting at “%s”".format(words, list[start].title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Each new book gets its chapters, with the pictures, styles and fonts they use, and any notes pages they link to. " +
                                "The first book gets the original cover. The others use the first picture in their opening pages, or get a new cover with their title. " +
                                "“${book.title}” itself isn't changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                problem != null || list == null -> TextButton(onClick = onDismiss) { Text("Close") }
                step == 0 -> TextButton(onClick = { step = 1 }, enabled = sortedCuts.isNotEmpty()) { Text("Next") }
                else -> TextButton(
                    onClick = { onSplit(sortedCuts, starts.indices.map { titleOf(it) }, series.trim().takeIf { numbered && it.isNotEmpty() }) },
                    enabled = starts.indices.all { titleOf(it).isNotBlank() },
                ) { Text("Split") }
            }
        },
        dismissButton = {
            when {
                problem != null || list == null -> Unit
                step == 0 -> TextButton(onClick = onDismiss) { Text("Cancel") }
                else -> TextButton(onClick = { step = 0 }) { Text("Back") }
            }
        },
    )
}

/**
 * Before saving copies of books as files: plain EPUB, kepub, or the file a book was added as (null). One
 * book goes to a file chosen next in Android's save picker; several go into a folder chosen in its folder picker.
 */
@Composable
fun ExportDialog(
    count: Int,
    title: String?,
    initial: dev.kobolibrary.app.kobo.SendFormat,
    /** How many of the books were added as another kind of file, and for one book, what kind ("PDF"). */
    addedAsOther: Int = 0,
    addedAsLabel: String? = null,
    onSave: (dev.kobolibrary.app.kobo.SendFormat?) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by remember { mutableStateOf<dev.kobolibrary.app.kobo.SendFormat?>(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SaveAlt, null) },
        title = { Text(if (count == 1 && title != null) "Save “$title” as a file" else "Save ${count} books as files") },
        text = {
            Column {
                CoverChoice(
                    selected = format == dev.kobolibrary.app.kobo.SendFormat.EPUB,
                    text = "EPUB: works with any reading app or e-reader",
                    onClick = { format = dev.kobolibrary.app.kobo.SendFormat.EPUB },
                )
                CoverChoice(
                    selected = format == dev.kobolibrary.app.kobo.SendFormat.KEPUB,
                    text = "Kepub: Kobo's own format, the same as when sending to the Kobo",
                    onClick = { format = dev.kobolibrary.app.kobo.SendFormat.KEPUB },
                )
                if (addedAsOther > 0) {
                    CoverChoice(
                        selected = format == null,
                        text = when {
                            count == 1 && addedAsLabel != null -> "$addedAsLabel: the file it was added as"
                            addedAsOther == count -> "As they were added: the PDF, MOBI or other file each one came as"
                            else -> "As they were added: books that came as PDFs, MOBIs and so on are saved as those, the rest as EPUBs"
                        },
                        onClick = { format = null },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (count == 1) "Next, you'll choose where to save it." else "Next, you'll choose a folder. Each book is saved there as “Title - Author”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(format) }) { Text("Choose location") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
