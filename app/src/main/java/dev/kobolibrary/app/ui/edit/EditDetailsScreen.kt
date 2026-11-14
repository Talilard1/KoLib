@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui.edit

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.GoldButton
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.Names
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.ui.components.LibraryCover
import dev.kobolibrary.app.ui.components.SectionCard
import dev.kobolibrary.app.ui.descriptionText

/** Names already used in the library, offered while typing. */
data class EditSuggestions(
    val authors: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    /** Every collection the app knows of: on the Kobo and in the library. */
    val collections: List<String> = emptyList(),
    /** Collections that exist on the Kobo (lower case), so new ones can be pointed out. */
    val onKobo: Set<String> = emptySet(),
) {
    companion object {
        fun from(books: List<LibraryBook>, collections: List<String>, koboCollections: List<String>): EditSuggestions {
            fun sorted(values: Sequence<String>) = values.map { it.trim() }.filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER).toList()
            return EditSuggestions(
                authors = sorted(books.asSequence().flatMap { it.meta.authors }),
                series = sorted(books.asSequence().mapNotNull { it.meta.series }),
                tags = sorted(books.asSequence().flatMap { it.meta.tags }),
                publishers = sorted(books.asSequence().mapNotNull { it.meta.publisher }),
                collections = collections,
                onKobo = koboCollections.map { it.lowercase() }.toSet(),
            )
        }
    }
}

@Composable
fun EditDetailsScreen(
    book: LibraryBook,
    covers: LibraryCovers,
    suggestions: EditSuggestions,
    saving: Boolean,
    onSave: (BookMetadata, List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val draft = remember(book.id) { DetailsDraft(book.meta, book.collections) }
    val scroll = rememberScrollState()
    var editingDescription by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var editAuthor by remember { mutableStateOf<Int?>(null) }

    val tryClose = { if (draft.isDirty) confirmDiscard = true else onClose() }

    if (editingDescription) {
        DescriptionEditor(
            initialHtml = draft.description.orEmpty(),
            bookTitle = draft.title.ifBlank { book.title },
            onDone = { html ->
                if (html != null) draft.description = html
                editingDescription = false
            },
            onCancel = { editingDescription = false },
        )
        return
    }

    BackHandler { tryClose() }
    val problems = draft.problems
    val canSave = draft.isDirty && problems.isEmpty() && !saving

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = tryClose) { Icon(Icons.Filled.Close, tr("Close")) } },
                title = {
                    Column {
                        Text(tr("Edit details"))
                        Text(
                            book.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                    GoldButton(
                        onClick = { onSave(draft.toMetadata(), draft.collectionList()) },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text(tr("Save")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            val wide = maxWidth >= 840.dp
            val roomy = maxWidth >= 600.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth()
                        .padding(horizontal = if (roomy) 24.dp else 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (wide) {
                        Column(Modifier.width(180.dp)) {
                            LibraryCover(book, covers, large = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Text(
                                tr("To change the cover, tap Change cover on the book's page."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (problems.isNotEmpty() && draft.isDirty) ProblemBanner(problems)
                        TitleAndAuthors(draft, suggestions, onEditAuthor = { editAuthor = it })
                        SeriesCard(draft, suggestions, roomy)
                        CollectionsCard(draft, suggestions, onKobo = book.koboPath != null)
                        TagsCard(draft, suggestions)
                        MoreDetailsCard(draft, suggestions, roomy)
                        DescriptionCard(draft.description, onEdit = { editingDescription = true })
                        book.meta.identifier?.let {
                            Text(
                                tr("Book identifier: {0}", it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editAuthor?.let { index ->
        val name = draft.authors.getOrNull(index)
        if (name == null) {
            editAuthor = null
        } else {
            AuthorDialog(
                name = name,
                sortName = draft.sortNameFor(name),
                isFirst = index == 0,
                onSave = { newName, sort ->
                    draft.renameAuthor(index, newName, sort)
                    editAuthor = null
                },
                onMakeFirst = {
                    draft.moveAuthorToFront(index)
                    editAuthor = null
                },
                onRemove = {
                    draft.authors.removeAt(index)
                    editAuthor = null
                },
                onDismiss = { editAuthor = null },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("Discard your changes?")) },
            text = { Text(tr("The book's details won't change.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(tr("Discard")) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("Keep editing")) } },
        )
    }
}

@Composable
private fun ProblemBanner(problems: List<String>) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tr("Fix these problems to save:"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun TitleAndAuthors(draft: DetailsDraft, suggestions: EditSuggestions, onEditAuthor: (Int) -> Unit) {
    SectionCard(tr("Title and authors")) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft.title = it },
            label = { Text(tr("Title")) },
            singleLine = true,
            isError = draft.titleProblem != null,
            supportingText = draft.titleProblem?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        val manualSort = draft.titleSort
        if (manualSort == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(tr("Sorted as “{0}”", Names.titleSort(draft.title)), Modifier.weight(1f, fill = false))
                TextButton(onClick = { draft.titleSort = Names.titleSort(draft.title) }) { Text(tr("Change")) }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualSort,
                    onValueChange = { draft.titleSort = it },
                    label = { Text(tr("Sort as")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { draft.titleSort = null }, modifier = Modifier.padding(start = 8.dp)) { Text(tr("Reset")) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(tr("Authors"), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        ChipsField(
            values = draft.authors,
            onAdd = { draft.authors.add(it) },
            onRemove = { draft.authors.removeAt(it) },
            suggestions = suggestions.authors,
            addLabel = tr("Add an author"),
            newItemLabel = { tr("Add “{0}”", it) },
            chipIcon = Icons.Outlined.Person,
            emptyText = tr("No author. The Kobo will show “Unknown author”."),
            commitOnComma = false,
            onChipClick = onEditAuthor,
        )
        if (draft.authors.isNotEmpty()) {
            Caption(tr("Tap an author to edit their name, change how it's sorted, or make them the first author."), Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SeriesCard(draft: DetailsDraft, suggestions: EditSuggestions, roomy: Boolean) {
    SectionCard(tr("Series")) {
        val name: @Composable (Modifier) -> Unit = { m ->
            SuggestField(
                value = draft.series,
                onValueChange = { draft.series = it },
                label = tr("Series"),
                suggestions = suggestions.series,
                modifier = m,
            )
        }
        val number: @Composable () -> Unit = {
            SeriesNumberField(
                value = draft.seriesIndex,
                onValueChange = { draft.seriesIndex = it },
                enabled = draft.series.isNotBlank(),
                error = draft.seriesIndexProblem,
            )
        }
        if (roomy) {
            Row(verticalAlignment = Alignment.Top) {
                name(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                number()
            }
        } else {
            name(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            number()
        }
        if (draft.series.isBlank()) Caption(tr("Not in a series."), Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CollectionsCard(draft: DetailsDraft, suggestions: EditSuggestions, onKobo: Boolean) {
    SectionCard(tr("Kobo collections"), icon = Icons.Outlined.CollectionsBookmark) {
        ChipsField(
            values = draft.collections,
            onAdd = { draft.collections.add(it) },
            onRemove = { draft.collections.removeAt(it) },
            suggestions = suggestions.collections,
            addLabel = tr("Add to a collection"),
            newItemLabel = { tr("New collection “{0}”", it) },
            chipIcon = null,
            emptyText = tr("Not in any collection yet."),
            chipBadge = { i -> if (draft.collections[i].lowercase() !in suggestions.onKobo && suggestions.onKobo.isNotEmpty()) tr("not on the Kobo") else null },
        )
        Caption(
            if (onKobo) tr("The book's collections on your Kobo. Changes are made on the Kobo when you send the book.")
            else tr("When you send the book to your Kobo, it's added to these collections. KoLib can't make new collections on the Kobo, so make any that are missing there first."),
            Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TagsCard(draft: DetailsDraft, suggestions: EditSuggestions) {
    SectionCard(tr("Tags"), icon = Icons.Outlined.LocalOffer) {
        ChipsField(
            values = draft.tags,
            onAdd = { draft.tags.add(it) },
            onRemove = { draft.tags.removeAt(it) },
            suggestions = suggestions.tags,
            addLabel = tr("Add a tag"),
            newItemLabel = { tr("New tag “{0}”", it) },
            chipIcon = null,
            emptyText = tr("No tags."),
        )
        Caption("Tags are saved inside the book file, where Calibre and other apps can see them. The Kobo doesn't use tags; it uses collections.", Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MoreDetailsCard(draft: DetailsDraft, suggestions: EditSuggestions, roomy: Boolean) {
    SectionCard(tr("More details")) {
        val publisher: @Composable (Modifier) -> Unit = { m ->
            SuggestField(draft.publisher, { draft.publisher = it }, tr("Publisher"), suggestions.publishers, m)
        }
        val published: @Composable (Modifier) -> Unit = { m ->
            PublishedField(draft.published, { draft.published = it }, draft.publishedProblem, m)
        }
        val language: @Composable (Modifier) -> Unit = { m -> LanguageField(draft.language, { draft.language = it }, m) }
        val isbn: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(
                value = draft.isbn,
                onValueChange = { s -> draft.isbn = s.filter { it.isDigit() || it in "Xx- " }.take(20) },
                label = { Text("ISBN") },
                singleLine = true,
                isError = draft.isbnProblem != null,
                supportingText = (draft.isbnProblem ?: draft.isbnWarning)?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = m,
            )
        }
        if (roomy) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                publisher(Modifier.weight(1f))
                published(Modifier.width(230.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                language(Modifier.weight(1f))
                isbn(Modifier.weight(1f))
            }
        } else {
            publisher(Modifier.fillMaxWidth())
            published(Modifier.fillMaxWidth())
            language(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            isbn(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("Your rating"), style = MaterialTheme.typography.titleSmall)
        RatingField(draft.rating) { draft.rating = it }
    }
}

@Composable
private fun DescriptionCard(html: String?, onEdit: () -> Unit) {
    SectionCard(tr("Description")) {
        val text = remember(html) { html?.let { descriptionText(it) } }
        if (text == null || text.isBlank()) {
            Caption(tr("No description yet."))
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (text == null || text.isBlank()) tr("Write a description") else tr("Edit description"))
        }
    }
}

@Composable
private fun AuthorDialog(
    name: String,
    sortName: String,
    isFirst: Boolean,
    onSave: (String, String) -> Unit,
    onMakeFirst: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf(name) }
    var sort by remember { mutableStateOf(sortName) }
    var sortFollowsName by remember { mutableStateOf(sortName == Names.authorSort(name)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Author")) },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        if (sortFollowsName) sort = Names.authorSort(it)
                    },
                    label = { Text(tr("Name")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sort,
                    onValueChange = {
                        sort = it
                        sortFollowsName = false
                    },
                    label = { Text(tr("Sort as")) },
                    supportingText = { Text(tr("Used when books are sorted by author, like “Doyle, Arthur Conan”.")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isFirst) TextButton(onClick = onMakeFirst) { Text(tr("Make first author")) }
                    TextButton(onClick = onRemove) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(newName, sort) }, enabled = newName.isNotBlank()) { Text(tr("Done")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
