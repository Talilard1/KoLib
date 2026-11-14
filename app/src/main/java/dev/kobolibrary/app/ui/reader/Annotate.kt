package dev.kobolibrary.app.ui.reader

import dev.kobolibrary.app.tr
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import dev.kobolibrary.app.reader.Annotation
import dev.kobolibrary.app.reader.HighlightColor
import dev.kobolibrary.app.reader.HighlightStyle
import dev.kobolibrary.app.reader.LookUp
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReaderSettings
import dev.kobolibrary.app.ui.components.Fmt
import kotlin.math.roundToInt

/** Where the card for a tapped highlight goes: under it if there's room, otherwise over it. */
private class NearRect(private val rect: android.graphics.RectF, private val density: Float) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = (10 * density).roundToInt()
        val left = anchorBounds.left + (rect.centerX() * density).roundToInt() - popupContentSize.width / 2
        val x = left.coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
        val below = anchorBounds.top + (rect.bottom * density).roundToInt() + gap
        val above = anchorBounds.top + (rect.top * density).roundToInt() - popupContentSize.height - gap
        val y = if (below + popupContentSize.height <= windowSize.height - gap) below else above.coerceAtLeast(gap)
        return IntOffset(x, y)
    }
}

fun Color.Companion.of(c: HighlightColor) = Color(c.argb)

@Composable
private fun ColourDot(colour: HighlightColor, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.of(colour))
            .border(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** The card for a tapped highlight: its colour and style, its note, and what can be done with it. */
@Composable
fun AnnotationCard(controller: ReaderController, settings: ReaderSettings, onSettings: (ReaderSettings) -> Unit) {
    val open = controller.openAnnotation ?: return
    val a = controller.annotation(open.id) ?: run {
        controller.openAnnotation = null
        return
    }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current.density
    Popup(
        popupPositionProvider = remember(open, density) { NearRect(open.rect, density) },
        onDismissRequest = { controller.openAnnotation = null },
        properties = PopupProperties(focusable = true),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp, shadowElevation = 8.dp, modifier = Modifier.widthIn(max = 360.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HighlightColor.entries.forEach { c ->
                        ColourDot(c, c == a.color) {
                            controller.updateAnnotation(a.copy(color = c))
                            onSettings(settings.copy(highlightColor = c))
                        }
                    }
                    Spacer(Modifier.width(2.dp))
                    IconToggleButton(
                        checked = a.style == HighlightStyle.UNDERLINE,
                        onCheckedChange = { on ->
                            val st = if (on) HighlightStyle.UNDERLINE else HighlightStyle.HIGHLIGHT
                            controller.updateAnnotation(a.copy(style = st))
                            onSettings(settings.copy(highlightStyle = st))
                        },
                    ) { Icon(Icons.Outlined.FormatUnderlined, tr("Underline instead")) }
                }
                if (a.hasNote) {
                    Text(
                        a.note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.of(a.color).copy(alpha = 0.18f))
                            .clickable { controller.openAnnotation = null; controller.editingNote = a }
                            .padding(10.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardAction(Icons.Outlined.EditNote, if (a.hasNote) tr("Note") else tr("Add note")) {
                        controller.openAnnotation = null
                        controller.editingNote = a
                    }
                    CardAction(Icons.Outlined.ContentCopy, tr("Copy")) {
                        clipboard.setText(AnnotatedString(a.text.trim()))
                        controller.openAnnotation = null
                    }
                    CardAction(Icons.Outlined.IosShare, tr("Share")) {
                        shareText(context, quoteFor(a, controller), controller.bookTitle)
                        controller.openAnnotation = null
                    }
                    CardAction(Icons.AutoMirrored.Outlined.MenuBook, tr("Look up")) {
                        controller.openAnnotation = null
                        controller.lookUpText = a.text.trim()
                    }
                    CardAction(Icons.Outlined.Delete, tr("Remove")) { controller.removeAnnotation(a.id) }
                }
            }
        }
    }
}

@Composable
private fun CardAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun quoteFor(a: Annotation, controller: ReaderController): String = buildString {
    append(tr("“{0}”", a.text.replace(Regex("\\s+"), " ").trim()))
    append("\n— ").append(controller.bookTitle)
    controller.bookAuthor?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
    if (a.hasNote) append("\n\n").append(a.note.trim())
}

fun shareText(context: Context, text: String, subject: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, subject)
    context.startActivity(Intent.createChooser(send, null))
}

/** Writing or changing the note on a highlight. */
@Composable
fun NoteDialog(controller: ReaderController) {
    val a = controller.editingNote ?: return
    var text by remember(a.id) { mutableStateOf(a.note) }
    AlertDialog(
        onDismissRequest = { controller.editingNote = null },
        title = { Text(if (a.hasNote) tr("Note") else tr("Add a note")) },
        text = {
            Column {
                Text(
                    tr("“{0}”", a.text.replace(Regex("\\s+"), " ").trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(tr("Your note")) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                controller.annotation(a.id)?.let { controller.updateAnnotation(it.copy(note = text.trim())) }
                controller.editingNote = null
            }) { Text(tr("Save")) }
        },
        dismissButton = {
            Row {
                if (a.hasNote) TextButton(onClick = {
                    controller.annotation(a.id)?.let { controller.updateAnnotation(it.copy(note = "")) }
                    controller.editingNote = null
                }) { Text(tr("Delete note")) }
                TextButton(onClick = { controller.editingNote = null }) { Text(tr("Cancel")) }
            }
        },
    )
}

/**
 * Looking up words: straight into the app chosen to look things up in, or, until one is chosen, a
 * sheet of the dictionary and translation apps installed, Wikipedia and a web search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookUpSheet(controller: ReaderController, settings: ReaderSettings, onSettings: (ReaderSettings) -> Unit, language: String?) {
    val text = controller.lookUpText ?: return
    val context = LocalContext.current
    val apps = remember { LookUp.apps(context) }
    val chosen = apps.firstOrNull { it.key == settings.dictionary }
    if (chosen != null) {
        androidx.compose.runtime.LaunchedEffect(text) {
            LookUp.open(context, chosen, text)
            controller.lookUpText = null
        }
        return
    }
    var always by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { controller.lookUpText = null }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                tr("Look up “{0}”", text.replace(Regex("\\s+"), " ").take(60)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (apps.isEmpty()) {
                Text(
                    tr("No dictionary or translation apps found. Install one that can look up selected text, such as ColorDict or Google Translate, and it will appear here."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            apps.forEach { app ->
                SheetRow(label = app.label, icon = { app.icon?.let { d -> Image(d.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(28.dp)) } }) {
                    if (always) onSettings(settings.copy(dictionary = app.key))
                    LookUp.open(context, app, text)
                    controller.lookUpText = null
                }
            }
            if (apps.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable { always = !always }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Text(tr("Remember my choice"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SheetRow(tr("Wikipedia"), icon = { Icon(Icons.Outlined.Language, null) }) {
                LookUp.wikipedia(context, text, language)
                controller.lookUpText = null
            }
            SheetRow(if (text.trim().contains(' ')) tr("Search the web") else tr("Define on the web"), icon = { Icon(Icons.Outlined.Search, null) }) {
                LookUp.webSearch(context, LookUp.defineQuery(text))
                controller.lookUpText = null
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Every highlight and note in the book, in reading order, for the contents panel. */
@Composable
fun HighlightsList(controller: ReaderController, onPicked: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf<Any?>(null) } // null: all; "notes"; or a HighlightColor
    val all = remember(controller.annotations) { Annotation.ordered(controller.annotations, controller::chapterOrder) }
    val shown = all.filter { a ->
        when (val f = filter) {
            null -> true
            "notes" -> a.hasNote
            is HighlightColor -> a.color == f
            else -> true
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (all.isEmpty()) {
            Text(
                tr("No highlights yet. To add one, press and hold a word, drag to select the text, then tap Highlight or Note."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(tr("All")) })
                FilterChip(selected = filter == "notes", onClick = { filter = if (filter == "notes") null else "notes" }, label = { Text(tr("Notes")) })
                HighlightColor.entries.filter { c -> all.any { it.color == c } }.forEach { c ->
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.of(c))
                            .border(if (filter == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { filter = if (filter == c) null else c },
                    )
                }
            }
            IconButton(onClick = {
                val text = Annotation.export(controller.bookTitle, controller.bookAuthor, shown, controller::chapterOrder, Fmt.date(System.currentTimeMillis()).orEmpty())
                shareText(context, text, tr("{0}: highlights and notes", controller.bookTitle))
            }) { Icon(Icons.Outlined.IosShare, tr("Share highlights and notes")) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(shown, key = { it.id }) { a ->
                AnnotationRow(
                    a,
                    onOpen = {
                        controller.goTo(a)
                        onPicked()
                    },
                    onNote = { controller.editingNote = a },
                    onCopy = { clipboard.setText(AnnotatedString(a.text.trim())) },
                    onShare = { shareText(context, quoteFor(a, controller), controller.bookTitle) },
                    onRemove = { controller.removeAnnotation(a.id) },
                )
            }
        }
    }
}

@Composable
private fun AnnotationRow(a: Annotation, onOpen: () -> Unit, onNote: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.of(a.color)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.text.replace(Regex("\\s+"), " ").trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (a.hasNote) {
                Row(Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Outlined.StickyNote2, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(a.note.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                listOf(a.chapterTitle.ifBlank { tr("Untitled chapter") }, "${(a.bookProgress * 100).roundToInt()}%", Fmt.date(a.modified).orEmpty()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("More")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (a.hasNote) tr("Edit note") else tr("Add note")) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { menu = false; onNote() })
                DropdownMenuItem(text = { Text(tr("Copy")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text(tr("Share")) }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text(tr("Remove")) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}
