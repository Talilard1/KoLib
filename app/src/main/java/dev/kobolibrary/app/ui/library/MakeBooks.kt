package dev.kobolibrary.app.ui.library

import dev.kobolibrary.app.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import dev.kobolibrary.app.ui.theme.appBackdrop

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
                    run -> tr("{0}: Books {1}–{2}", series, number(numbers.first()), number(numbers.last()))
                    numbers.size == 2 -> tr("{0}: Books {1} & {2}", series, number(numbers[0]), number(numbers[1]))
                    else -> tr("{0}: Books {1} & {2}", series, numbers.dropLast(1).joinToString(", ") { number(it) }, number(numbers.last()))
                }
            }
            return tr("{0} Omnibus", series)
        }
        return when (books.size) {
            2 -> "${books[0].title} & ${books[1].title}"
            3 -> "${books[0].title}, ${books[1].title} & ${books[2].title}"
            else -> {
                val authors = books.map { b -> b.meta.authors.map { it.lowercase() }.toSet() }.distinct()
                val author = books[0].meta.authors.singleOrNull()?.takeIf { authors.size == 1 }
                if (author != null) tr("{0} Omnibus", author) else tr("{0} and Others", books[0].title)
            }
        }
    }

    /** Everyone who wrote them, in order: "A & B", or "Various" for a crowd. */
    fun mergedAuthor(books: List<LibraryBook>): String {
        val all = books.flatMap { it.meta.authors }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        return if (all.size > 4) tr("Various") else all.joinToString(" & ")
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
        title = { Text(tr("Write a new book")) },
        text = {
            Column {
                Text(
                    tr("Opens in the text editor with one empty chapter and a simple cover. You can change both later."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr("Title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(tr("Author")) },
                    supportingText = { Text(tr("For more than one author, put & between the names")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onMake(title, author) }, enabled = title.isNotBlank()) { Text(tr("Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
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
        title = { Text(tr("Combine {0} books", books.size)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr("Order"), style = MaterialTheme.typography.titleSmall)
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
                        IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, tr("Move “{0}” up", b.title)) }
                        IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex) { Icon(Icons.Outlined.ArrowDownward, tr("Move “{0}” down", b.title)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleTyped = true },
                    label = { Text(tr("Title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it; authorTyped = true },
                    label = { Text(tr("Author")) },
                    supportingText = { Text(tr("For more than one author, put & between the names")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(tr("Cover"), style = MaterialTheme.typography.titleSmall)
                CoverChoice(selected = coverOf == null, text = tr("Make a new cover with the title"), onClick = { coverOf = null })
                order.filter { it.hasCover }.forEach { b ->
                    CoverChoice(selected = coverOf == b.id, text = tr("Use the cover of “{0}”", b.title), onClick = { coverOf = b.id })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("One book with all their chapters in this order, and a contents list by book. It's added to all their collections. The originals aren't changed."),
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
            ) { Text(tr("Combine")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
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
/**
 * Choosing which pages go into every book a split makes: the first book's opening pages (a title page, a copyright
 * notice, a contents page) and the last book's closing ones (about the author), with those [chosen] ticked.
 */
@Composable
private fun SharedPagesDialog(
    pages: List<BookAssembly.Page>,
    candidates: BookAssembly.Shared,
    chosen: BookAssembly.Shared,
    onDone: (front: Set<Int>, back: Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val front = remember { mutableStateMapOf<Int, Boolean>().apply { chosen.front.forEach { put(it, true) } } }
    val back = remember { mutableStateMapOf<Int, Boolean>().apply { chosen.back.forEach { put(it, true) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Pages for every book")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                for ((heading, indexes, ticked) in listOf(
                    Triple(tr("From the start of the first book"), candidates.front, front),
                    Triple(tr("From the end of the last book"), candidates.back, back),
                )) {
                    if (indexes.isEmpty()) continue
                    item(key = heading) {
                        Text(heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    }
                    items(indexes, key = { "$heading-$it" }) { i ->
                        val p = pages[i]
                        val on = ticked.containsKey(i)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = on, role = Role.Checkbox, onValueChange = { v -> if (v) ticked[i] = true else ticked.remove(i) })
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        if (p.cover) tr("Cover") else null,
                                        if (p.words == 1) tr("1 word") else tr("{0} words", "%,d".format(p.words)),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(front.keys.toSet(), back.keys.toSet()) }) { Text(tr("Done")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
fun SplitBookDialog(
    book: LibraryBook,
    loadPages: suspend () -> List<BookAssembly.Page>,
    /** [covers]: every book gets a cover in one design (null: those without one each get a design picked at random). */
    onSplit: (cuts: List<Int>, titles: List<String>, series: String?, shared: BookAssembly.Shared?, covers: dev.kobolibrary.app.ui.cover.CoverSet?) -> Unit,
    onDismiss: () -> Unit,
    /** For previews: the pages, already loaded. */
    initialPages: List<BookAssembly.Page>? = null,
    initialStep: Int = 0,
    /** For previews: one design for every book, and which (null: the app picks). */
    initialSameDesign: Boolean = false,
    initialDesign: dev.kobolibrary.app.ui.cover.CoverDesign? = null,
    initialEachColour: Boolean = false,
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
    /** Pages go into every book: null until changed, which means yes if any were found. */
    var share by remember { mutableStateOf<Boolean?>(null) }
    /** The pages chosen for every book, by the page they are; null: the ones found. */
    var chosenFront by remember { mutableStateOf<Set<Int>?>(null) }
    var chosenBack by remember { mutableStateOf<Set<Int>?>(null) }
    var choosing by remember { mutableStateOf(false) }
    // Covers: one design for every book (picked: which; null: the app picks one when splitting), or each its own.
    val genre = remember(book.id) { dev.kobolibrary.app.ui.cover.CoverGenre.guess(book.meta.tags, book.meta.series, book.meta.title, book.meta.description) }
    var sameDesign by remember { mutableStateOf(initialSameDesign) }
    var picked by remember { mutableStateOf(initialDesign) }
    var eachColour by remember { mutableStateOf(initialEachColour) }
    var colourRound by remember { mutableIntStateOf(0) }
    var suggestionRound by remember { mutableIntStateOf(0) }
    val suggestions = remember(genre, suggestionRound) {
        dev.kobolibrary.app.ui.cover.CoverSet.suggestions(genre, 6, kotlin.random.Random(book.id.hashCode() * 31 + suggestionRound))
    }
    var browsing by remember { mutableStateOf(false) }
    val thumbs = rememberCoverThumbs()
    LaunchedEffect(book.id) {
        if (pages != null) return@LaunchedEffect
        try {
            val p = loadPages()
            if (p.size < 2) problem = tr("“{0}” has only one chapter, so it can't be split into books. First split the chapter in the text editor.", book.title)
            BookAssembly.suggestedCuts(p).forEach { cuts[it] = true }
            suggested = cuts.size
            pages = p
        } catch (e: AssemblyException) {
            problem = e.message
        } catch (e: Exception) {
            problem = tr("The book couldn't be read: {0}", e.message ?: e.javaClass.simpleName)
        }
    }
    val list = pages
    val sortedCuts = cuts.keys.sorted()
    val starts = listOf(0) + sortedCuts
    val defaults = remember(list, sortedCuts) { list?.let { BookAssembly.partTitles(it, sortedCuts, book.title) }.orEmpty() }
    fun titleOf(k: Int) = typed[starts[k]] ?: defaults.getOrElse(k) { tr("{0} (part {1})", book.title, k + 1) }
    // The pages before the first book and after the last, which can go into every one: those found, or chosen.
    val found = remember(list, sortedCuts) { list?.let { BookAssembly.sharedPages(it, sortedCuts) } ?: BookAssembly.Shared(emptyList(), emptyList()) }
    val candidates = remember(list, sortedCuts) { list?.let { BookAssembly.sharedCandidates(it, sortedCuts) } ?: BookAssembly.Shared(emptyList(), emptyList()) }
    val shared = BookAssembly.Shared(
        (chosenFront?.toList() ?: found.front).filter { it < (starts.getOrNull(1) ?: 0) }.sorted(),
        (chosenBack?.toList() ?: found.back).filter { it > starts.last() }.sorted(),
    )
    val sharing = (share ?: !found.isEmpty) && !shared.isEmpty
    val seriesName = series.trim().takeIf { numbered && it.isNotEmpty() }
    // What book k's cover says, as the split will write it.
    fun coverText(k: Int) = dev.kobolibrary.app.ui.cover.CoverText(
        titleOf(k).trim().ifEmpty { tr("{0} (part {1})", book.title, k + 1) },
        book.meta.authors,
        seriesName ?: book.meta.series,
        if (seriesName != null) (k + 1).toDouble() else null,
    )

    if (browsing) {
        // All the designs, to choose one for every book: the cover maker, without pictures of one's own.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { browsing = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().appBackdrop()) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    dev.kobolibrary.app.ui.cover.CoverGeneratorScreen(
                        text = coverText(0),
                        onUse = { _, design ->
                            picked = design
                            browsing = false
                        },
                        onCancel = { browsing = false },
                        genre = genre,
                        initial = remember { picked ?: suggestions.first() },
                        ownPictures = false,
                        useLabel = tr("Use this design"),
                    )
                }
            }
        }
        return
    }
    if (choosing && list != null) {
        SharedPagesDialog(
            pages = list,
            candidates = candidates,
            chosen = shared,
            onDone = { f, b ->
                chosenFront = f
                chosenBack = b
                share = f.isNotEmpty() || b.isNotEmpty()
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) },
        title = { Text(if (step == 0) tr("Split “{0}”", book.title) else tr("Name the {0} books", starts.size)) },
        text = {
            when {
                problem != null -> Text(problem!!, style = MaterialTheme.typography.bodyMedium)
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                step == 0 -> Column {
                    Text(
                        tr("Tick the chapters where each new book starts.") +
                            if (suggested > 0) tr(" Chapters that look like the start of a book are already ticked.") else "",
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
                                            if (p.words == 1) tr("1 word") else tr("{0} words", "%,d".format(p.words)),
                                            if (!p.linear) tr("only opened from links") else null,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (startsBook) {
                                    Text(
                                        tr("Book {0}", starts.indexOf(p.index) + 1),
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
                        if (sortedCuts.isEmpty()) tr("Nothing ticked yet. The first book always starts at the beginning.") else tr("This will make {0} books.", starts.size),
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
                        Text(tr("Number them as a series"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = numbered, onCheckedChange = null)
                    }
                    if (numbered) {
                        OutlinedTextField(
                            value = series,
                            onValueChange = { series = it },
                            label = { Text(tr("Series")) },
                            supportingText = { Text(tr("They'll be numbered 1, 2, 3… in this order")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = sharing, role = Role.Switch, onValueChange = { on ->
                                share = on
                                // Nothing found to give them: choose which.
                                if (on && shared.isEmpty) choosing = true
                            })
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Add front and back pages to every book"), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (shared.isEmpty) tr("Pages such as a title page or copyright notice. None were found: choose them.")
                                else listOfNotNull(
                                    shared.front.takeIf { it.isNotEmpty() }?.let { f -> tr("Before the first book: {0}.", f.joinToString(", ") { list[it].title }) },
                                    shared.back.takeIf { it.isNotEmpty() }?.let { b -> tr("After the last: {0}.", b.joinToString(", ") { list[it].title }) },
                                    tr("If one is a contents page, it lists each book's own chapters."),
                                ).joinToString(" "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = sharing, onCheckedChange = null)
                    }
                    TextButton(onClick = { choosing = true }, enabled = !(candidates.front.isEmpty() && candidates.back.isEmpty())) {
                        Text(tr("Choose pages…"))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = sameDesign, role = Role.Switch, onValueChange = { sameDesign = it })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Same cover design for every book"), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (sameDesign) tr("Every book gets a new cover in it, the first one too.")
                                else tr("Off: a book without a cover gets one in a design picked at random."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = sameDesign, onCheckedChange = null)
                    }
                    if (sameDesign) {
                        SplitCoverChooser(
                            thumbs = thumbs,
                            books = starts.size,
                            textOf = ::coverText,
                            suggestions = suggestions,
                            picked = picked,
                            onPick = { picked = it },
                            onOtherSuggestions = { suggestionRound++ },
                            onBrowse = { browsing = true },
                            eachColour = eachColour,
                            onEachColour = { eachColour = it },
                            colourRound = colourRound,
                            onOtherColours = { colourRound++ },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    starts.forEachIndexed { k, start ->
                        val end = starts.getOrNull(k + 1) ?: list.size
                        val words = list.subList(start, end).sumOf { it.words }
                        OutlinedTextField(
                            value = titleOf(k),
                            onValueChange = { typed[start] = it },
                            label = { Text(tr("Book {0}", k + 1)) },
                            supportingText = { Text(tr("{0} words, starting at “{1}”", "%,d".format(words), list[start].title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (sameDesign) tr("Each book gets its chapters with their pictures, styles, fonts and notes. The first book's old cover can be put back with Change cover, then Restore original. “{0}” isn't changed.", book.title)
                            else tr("Each book gets its chapters with their pictures, styles, fonts and notes. The first gets the original cover; the others use their own cover page or get a new cover. “{0}” isn't changed.", book.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                problem != null || list == null -> TextButton(onClick = onDismiss) { Text(tr("Close")) }
                step == 0 -> TextButton(onClick = { step = 1 }, enabled = sortedCuts.isNotEmpty()) { Text(tr("Next")) }
                else -> TextButton(
                    onClick = {
                        val covers = if (!sameDesign) null else dev.kobolibrary.app.ui.cover.CoverSet(
                            picked ?: dev.kobolibrary.app.ui.cover.CoverDesign.random(anyColour = false, genre = genre),
                            eachColour,
                            colourRound,
                        )
                        onSplit(sortedCuts, starts.indices.map { titleOf(it) }, seriesName, shared.takeIf { sharing }, covers)
                    },
                    enabled = starts.indices.all { titleOf(it).isNotBlank() },
                ) { Text(tr("Split")) }
            }
        },
        dismissButton = {
            when {
                problem != null || list == null -> Unit
                step == 0 -> TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
                else -> TextButton(onClick = { step = 0 }) { Text(tr("Back")) }
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
        title = { Text(if (count == 1 && title != null) tr("Save “{0}” as a file", title) else tr("Save {0} books as files", count)) },
        text = {
            Column {
                CoverChoice(
                    selected = format == dev.kobolibrary.app.kobo.SendFormat.EPUB,
                    text = tr("EPUB: works with any reading app or e-reader"),
                    onClick = { format = dev.kobolibrary.app.kobo.SendFormat.EPUB },
                )
                CoverChoice(
                    selected = format == dev.kobolibrary.app.kobo.SendFormat.KEPUB,
                    text = tr("Kepub: Kobo's own format"),
                    onClick = { format = dev.kobolibrary.app.kobo.SendFormat.KEPUB },
                )
                if (addedAsOther > 0) {
                    CoverChoice(
                        selected = format == null,
                        text = when {
                            count == 1 && addedAsLabel != null -> tr("{0}: the original file", addedAsLabel)
                            addedAsOther == count -> tr("Original files: the PDF, MOBI or other file each was added as")
                            else -> tr("Original files: PDFs, MOBIs and so on in their own format, the rest as EPUBs")
                        },
                        onClick = { format = null },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (count == 1) tr("Next, you'll choose where to save it.") else tr("Next, you'll choose a folder. Each book is saved there as “Title - Author”."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(format) }) { Text(tr("Choose location")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
