package dev.kolib.app.ui.textedit

import dev.kolib.app.ui.components.FittedText
import dev.kolib.app.tr
import dev.kolib.app.ui.components.GoldButton
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.kolib.app.ui.components.Fmt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Title
import dev.kolib.app.ui.components.AlertDialog
import dev.kolib.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.kolib.app.ui.components.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import dev.kolib.app.ui.tour.LocalTourPlaces
import dev.kolib.app.ui.tour.Tour
import dev.kolib.app.ui.tour.TourPlaces
import dev.kolib.app.ui.tour.tourPlace
import dev.kolib.app.epub.ChapterOps
import dev.kolib.app.epub.Cleanups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Subscript
import androidx.compose.material.icons.outlined.Superscript
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import dev.kolib.app.epub.EpubText

/**
 * Editing a book's text: its chapters down the side, the chapter as a page to type into (or its code),
 * find & replace, clean-ups, and Save, which checks the book before it replaces it.
 */
@Composable
fun TextEditorScreen(
    state: TextEditState,
    onClose: () -> Unit,
    /** Reads the book as it is now, unsaved changes included, from a chapter. */
    onRead: (String?) -> Unit,
    /** Shown instead of the page in screenshots, where a WebView can't draw. */
    pagePreview: (@Composable () -> Unit)? = null,
    /** The editor's tour starts as it opens: the first time it's opened. */
    tour: Boolean = false,
    /** The tour was gone through, or skipped. */
    onTourDone: () -> Unit = {},
    /** Turns every tour off, from the tour (null: the tour hasn't that choice). */
    onToursOff: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<ChapterAction?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var showCleanups by remember { mutableStateOf(false) }
    var assistant by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    var counting by remember { mutableStateOf(false) }
    var narrowShowsEditor by remember { mutableStateOf(state.currentPath != null) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f

    LaunchedEffect(state.message) {
        state.message?.let {
            state.message = null
            snackbar.showSnackbar(it)
        }
    }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) state.message = tr("That picture couldn't be opened.")
            else if (state.toolbar.image != null) state.replacePicture(bytes) else state.insertPicture(bytes)
        }
    }

    val close = { if (state.dirty) confirmClose = true else onClose() }
    BackHandler {
        when {
            state.find.open -> state.find.close()
            state.copiedOpen -> state.copiedOpen = false
            else -> close()
        }
    }
    val copiedItems by (state.copied?.items ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()

    // The tour: over the editor with the page showing and its panels closed, lighting each part where it is.
    var touring by rememberSaveable { mutableStateOf(tour) }
    val places = remember { TourPlaces() }
    val toolbarScroll = rememberScrollState()
    LaunchedEffect(touring) {
        if (touring) {
            if (state.find.open) state.find.close()
            state.copiedOpen = false
            narrowShowsEditor = true
        }
    }

    // How the editor is arranged on this screen, as it's laid out below: the chapters as a screen of their own
    // (a phone) rather than beside the page, and the top bar's less used buttons in its menu.
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    var chaptersApart by remember { mutableStateOf(screenWidth < 600) }
    var toolsInMenu by remember { mutableStateOf(screenWidth < 600) }

    CompositionLocalProvider(LocalTourPlaces provides places.takeIf { touring }) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // On a phone the chapters are a screen of their own: while the page shows, the chapter's name in the top bar leads there.
        val pageShowing = narrowShowsEditor && !state.find.open && !(state.copiedOpen && state.copied != null)
        EditorTopBar(state, onBack = close, onRead = { scope.launch { state.flush(); state.settleMoves(); onRead(state.currentPath) } },
            onFind = { state.find.open = !state.find.open; if (state.find.open) state.copiedOpen = false },
            onCleanups = { showCleanups = true }, onRevert = { confirmRevert = true }, onTour = { touring = true },
            onAssistant = { assistant = true },
            onChapters = if (chaptersApart && pageShowing) ({ narrowShowsEditor = false }) else null,
            onToolsInMenu = { toolsInMenu = it }, onWordCount = { counting = true })
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val wide = maxWidth >= 1000.dp
            val medium = maxWidth >= 600.dp
            // (What the top bar and the tour go by is what's laid out here, not the screen's size as Android gives it,
            // which can differ by the width of a navigation bar at the side.)
            SideEffect { chaptersApart = !medium }
            val chapters: @Composable (Modifier) -> Unit = { m ->
                ChaptersPane(state, onOpen = { state.open(it); narrowShowsEditor = true }, onAction = { if (it is ChapterAction.Duplicate) state.duplicate(it.chapter.path) else action = it }, modifier = m)
            }
            val findPanel: @Composable (Modifier) -> Unit = { m -> FindPanel(state.find, m, onClose = { state.find.close() }) }
            val copiedPanel: @Composable (Modifier) -> Unit = { m -> CopiedPanel(state, copiedItems, m, onClose = { state.copiedOpen = false }) }
            val editor: @Composable (Modifier) -> Unit = { m ->
                EditorPane(state, dark, pagePreview, onPicture = { pickPicture.launch("image/*") }, modifier = m,
                    copiedCount = copiedItems.size, toolbarScroll = toolbarScroll)
            }
            val copiedOpen = state.copiedOpen && state.copied != null && !state.find.open
            // The page is one and the same part of the screen however the rest is arranged around it, so turning
            // the device (which changes the arrangement) leaves it as it is, with its cursor and its place. Three
            // across where there's the width (the chapters, the page, a panel); two on a narrower tablet, where a
            // panel takes the chapters' place or their side; and one thing at a time on a phone.
            val pageShows = medium || (narrowShowsEditor && !state.find.open && !copiedOpen)
            val chaptersShow = when {
                wide -> true
                medium -> !state.find.open && !copiedOpen
                else -> !state.find.open && !copiedOpen && !narrowShowsEditor
            }
            val findInChaptersPlace = state.find.open && !wide
            val panel = MaterialTheme.colorScheme.surfaceContainerLow
            Row(Modifier.fillMaxSize()) {
                val all = Modifier.weight(1f).fillMaxHeight()
                if (chaptersShow) chapters(if (medium) Modifier.width(if (wide) 290.dp else 270.dp).fillMaxHeight().tourPlace(EditorPlace.CHAPTERS) else all)
                if (findInChaptersPlace) findPanel((if (medium) Modifier.width(340.dp).fillMaxHeight() else all).background(panel))
                if (copiedOpen && !medium) copiedPanel(all.background(panel))
                if (pageShows) {
                    if (chaptersShow || findInChaptersPlace) VerticalDivider()
                    editor(all)
                    if (wide && state.find.open) {
                        VerticalDivider()
                        findPanel(Modifier.width(390.dp).fillMaxHeight().background(panel))
                    } else if (copiedOpen) {
                        VerticalDivider()
                        copiedPanel(Modifier.width(if (wide) 360.dp else 320.dp).fillMaxHeight().background(panel))
                    }
                }
            }
            state.busy?.let { what ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inverseOnSurface)
                        Spacer(Modifier.width(10.dp))
                        Text(what, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
    if (touring) {
        val edge = with(LocalDensity.current) { 10.dp.toPx() }
        val stops = remember(toolsInMenu, edge) { editorTourStops(places, toolbarScroll, toolsInMenu, edge) }
        Tour(stops, places, onDone = {
            touring = false
            onTourDone()
        }, onTurnOff = onToursOff?.let { off -> { touring = false; off() } })
    }
    }
    }

    // ---- dialogs ---------------------------------------------------------------------------------

    when (val a = action) {
        is ChapterAction.Rename -> RenameDialog(a, state, onDone = { action = null })
        is ChapterAction.Split -> SplitDialog(a, state, onDone = { action = null })
        is ChapterAction.Add -> AddChapterDialog(a, state, onDone = { action = null })
        is ChapterAction.Duplicate -> action = null
        is ChapterAction.Merge -> AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(tr("Join “{0}” with the previous chapter?", a.chapter.title)) },
            text = { Text(tr("Its text will be added to the end of “{0}”, and links to it will point there. You can undo this.", state.structure.chapters.getOrNull(a.chapter.index - 1)?.title)) },
            confirmButton = { TextButton(onClick = { state.mergeWithPrevious(a.chapter.path); action = null }) { Text(tr("Join")) } },
            dismissButton = { TextButton(onClick = { action = null }) { Text(tr("Cancel")) } },
        )
        is ChapterAction.Delete -> AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(tr("Delete “{0}”?", a.chapter.title)) },
            text = { Text(tr("The chapter and its entry in the contents will be deleted, and links to it will become plain text. You can undo this until you close the editor.")) },
            confirmButton = { TextButton(onClick = { state.delete(a.chapter.path); action = null }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { action = null }) { Text(tr("Cancel")) } },
        )
        is ChapterAction.DeleteMany -> AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(tr("Delete {0}?", Fmt.plural(a.chapters.size, "chapter"))) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    a.chapters.forEach { c -> Text("• " + c.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("They and their entries in the contents will be deleted, and links to them will become plain text. You can undo this until you close the editor."))
                }
            },
            confirmButton = {
                TextButton(onClick = { state.deleteAll(a.chapters.map { it.path }); action = null }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { action = null }) { Text(tr("Cancel")) } },
        )
        null -> Unit
    }

    if (showCleanups) CleanupsDialog(state, onDismiss = { showCleanups = false })
    if (counting) WordCountDialog(state, onDismiss = { counting = false })
    if (assistant) EditorAssistant(state, onDismiss = { assistant = false })

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(tr("Save your changes?")) },
            text = { Text(tr("The changes to “{0}” haven't been saved yet.", state.title)) },
            confirmButton = { TextButton(onClick = { confirmClose = false; state.save { onClose() } }) { Text(tr("Save")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmClose = false; state.discardChanges(); onClose() }) { Text(tr("Don't save"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmClose = false }) { Text(tr("Keep editing")) }
                }
            },
        )
    }

    if (confirmRevert) {
        AlertDialog(
            onDismissRequest = { confirmRevert = false },
            icon = { Icon(Icons.Outlined.Restore, null) },
            title = { Text(tr("Restore the original text?")) },
            text = { Text(tr("All your text edits will be lost. The details and cover won't change.")) },
            confirmButton = { TextButton(onClick = { confirmRevert = false; state.revert {} }) { Text(tr("Restore original"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRevert = false }) { Text(tr("Cancel")) } },
        )
    }

    state.refused?.let { problems ->
        AlertDialog(
            onDismissRequest = { state.refused = null },
            title = { Text(tr("Not saved: these changes would break the book")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(tr("Problems found:"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    problems.take(12).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (problems.size > 12) Text(tr("…and {0} more.", problems.size - 12), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Fix them (the Code button can help) or undo the change. Nothing was saved."), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { state.refused = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { state.refused = null; state.undo() }) { Text(tr("Undo the last change")) } },
        )
    }

    state.checkResult?.let { problems ->
        AlertDialog(
            onDismissRequest = { state.checkResult = null },
            icon = { Icon(Icons.Outlined.FactCheck, null) },
            title = {
                Text(
                    when (problems.size) {
                        0 -> tr("No problems found")
                        1 -> tr("1 problem found")
                        else -> tr("{0} problems found", problems.size)
                    },
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (problems.isEmpty()) Text(tr("All chapters, files, the reading order and the contents are valid."))
                    else problems.take(20).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { state.checkResult = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun EditorTopBar(
    state: TextEditState, onBack: () -> Unit, onRead: () -> Unit, onFind: () -> Unit, onCleanups: () -> Unit, onRevert: () -> Unit, onTour: () -> Unit,
    onAssistant: () -> Unit = {},
    /** Opens the list of chapters, where it's a screen of its own (a phone) and the page is showing: the chapter's name is the way to it. */
    onChapters: (() -> Unit)? = null,
    /** Told whether the less used buttons are in the menu (there isn't the width for them in the bar). */
    onToolsInMenu: (Boolean) -> Unit = {},
    onWordCount: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
        // The status bar is hidden while the editor is open, but a camera cutout, or the status bar
        // if it does show, would otherwise cover the buttons.
        val clear = WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(clear)) {
            // On a phone the less used buttons move into the menu.
            val roomy = maxWidth >= 600.dp
            SideEffect { onToolsInMenu(!roomy) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the editor")) }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .then(if (onChapters != null) Modifier.tourPlace(EditorPlace.CHAPTER_NAME).clickable(onClickLabel = tr("Chapters"), onClick = onChapters) else Modifier)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (onChapters != null) {
                        // The chapter being edited, and the way to the others.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FittedText(
                                state.chapter?.title?.ifBlank { null } ?: tr("Chapters"), style = MaterialTheme.typography.titleMedium,
                                smallest = 0.8f, modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(22.dp))
                        }
                    } else {
                        FittedText(dev.kolib.app.trc("editor title", "Edit text"), style = MaterialTheme.typography.titleMedium, smallest = 0.7f)
                    }
                    Text(
                        state.title + if (state.dirty) tr(" · unsaved changes") else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { state.undo() }, enabled = state.canUndo, modifier = Modifier.tourPlace(EditorPlace.UNDO)) { Icon(Icons.AutoMirrored.Filled.Undo, state.undoLabel?.let { tr("Undo: {0}", it) } ?: tr("Undo")) }
                IconButton(onClick = { state.redo() }, enabled = state.canRedo, modifier = Modifier.tourPlace(EditorPlace.REDO)) { Icon(Icons.AutoMirrored.Filled.Redo, state.redoLabel?.let { tr("Redo: {0}", it) } ?: tr("Redo")) }
                if (roomy) {
                    IconButton(onClick = onFind, modifier = Modifier.tourPlace(EditorPlace.FIND)) { Icon(Icons.Filled.Search, tr("Find & replace"), tint = if (state.find.open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = onCleanups, modifier = Modifier.tourPlace(EditorPlace.CLEANUPS)) { Icon(Icons.Outlined.AutoFixHigh, tr("Clean-ups")) }
                    IconButton(onClick = onAssistant, modifier = Modifier.testTag("editor-ai")) { Icon(Icons.Outlined.AutoAwesome, tr("AI assistant")) }
                    IconButton(onClick = onRead, modifier = Modifier.tourPlace(EditorPlace.PREVIEW)) { Icon(Icons.AutoMirrored.Filled.MenuBook, tr("Preview in reader")) }
                    Spacer(Modifier.width(4.dp))
                }
                GoldButton(onClick = { state.save() }, enabled = state.dirty && state.busy == null, modifier = Modifier.tourPlace(EditorPlace.SAVE)) { Text(tr("Save")) }
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.tourPlace(EditorPlace.MORE)) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("More")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!roomy) {
                            DropdownMenuItem(text = { Text(tr("Find & replace")) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, onClick = { menu = false; onFind() })
                            DropdownMenuItem(text = { Text(tr("Clean-ups")) }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) }, onClick = { menu = false; onCleanups() })
                            DropdownMenuItem(text = { Text(tr("AI assistant")) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = { menu = false; onAssistant() })
                            DropdownMenuItem(text = { Text(tr("Preview in reader")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }, onClick = { menu = false; onRead() })
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(tr("Word count")) }, leadingIcon = { Icon(Icons.Outlined.Numbers, null) },
                            onClick = { menu = false; onWordCount() }, modifier = Modifier.testTag("editor-word-count"),
                        )
                        DropdownMenuItem(text = { Text(tr("Check for problems")) }, leadingIcon = { Icon(Icons.Outlined.FactCheck, null) },
                            onClick = { menu = false; state.checkBook() })
                        DropdownMenuItem(
                            text = { Text(tr("Restore original text…")) },
                            leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                            enabled = state.hasOriginal,
                            onClick = { menu = false; onRevert() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(tr("Editor tour")) }, leadingIcon = { Icon(Icons.Outlined.Lightbulb, null) }, onClick = { menu = false; onTour() })
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPane(
    state: TextEditState,
    dark: Boolean,
    pagePreview: (@Composable () -> Unit)?,
    onPicture: () -> Unit,
    modifier: Modifier,
    copiedCount: Int = 0,
    toolbarScroll: ScrollState = rememberScrollState(),
) {
    Column(modifier) {
        EditorToolbar(state, onPicture, copiedCount, toolbarScroll)
        HorizontalDivider()
        Box(Modifier.fillMaxSize().tourPlace(EditorPlace.PAGE)) {
            if (pagePreview != null) pagePreview() else {
                AndroidView(
                    factory = { ctx -> state.createView(ctx, dark) },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { state.releaseView(it) },
                )
            }
        }
    }
}

/**
 * The row of buttons over the page. Where there isn't the width for their names (a phone, or the editor's
 * side of a tablet held upright), Heading, Picture, Copied and Code are just their pictures, and on a phone
 * the buttons are a little smaller, so that they're all in view without scrolling the row along.
 */
@Composable
private fun EditorToolbar(state: TextEditState, onPicture: () -> Unit, copiedCount: Int = 0, scroll: ScrollState = rememberScrollState()) {
    val t = state.toolbar
    val page = !state.showCode
    val on = IconButtonDefaults.iconToggleButtonColors(
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // (With their names the buttons need some 800 across: less than that, the row would have to be scrolled along.)
            val compact = maxWidth < 800.dp
            val tight = maxWidth < 440.dp
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (tight) 40.dp else 48.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = if (tight) 4.dp else 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).tourPlace(EditorPlace.TOOLBAR).horizontalScroll(scroll),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (tight) 0.dp else 2.dp),
                    ) {
                        IconToggleButton(checked = t.bold && page, onCheckedChange = { state.command("bold") }, enabled = page, colors = on, modifier = Modifier.tourPlace(EditorPlace.BOLD)) { Icon(Icons.Outlined.FormatBold, tr("Bold")) }
                        IconToggleButton(checked = t.italic && page, onCheckedChange = { state.command("italic") }, enabled = page, colors = on, modifier = Modifier.tourPlace(EditorPlace.ITALIC)) { Icon(Icons.Outlined.FormatItalic, tr("Italic")) }
                        // Underlined, struck through, raised and lowered letters; and clearing all of it.
                        var styling by remember { mutableStateOf(false) }
                        Box(Modifier.tourPlace(EditorPlace.TEXT_STYLES)) {
                            IconToggleButton(
                                checked = page && (t.underline || t.strike || t.sup || t.sub), onCheckedChange = { styling = true }, enabled = page, colors = on,
                                modifier = Modifier.testTag("editor-styles"),
                            ) { Icon(Icons.Outlined.TextFormat, tr("More text styles")) }
                            DropdownMenu(expanded = styling, onDismissRequest = { styling = false }) {
                                listOf(
                                    Triple("underline", tr("Underline"), Icons.Outlined.FormatUnderlined) to t.underline,
                                    Triple("strike", tr("Strikethrough"), Icons.Outlined.FormatStrikethrough) to t.strike,
                                    Triple("sup", tr("Superscript"), Icons.Outlined.Superscript) to t.sup,
                                    Triple("sub", tr("Subscript"), Icons.Outlined.Subscript) to t.sub,
                                ).forEach { (style, lit) ->
                                    val (name, label, icon) = style
                                    DropdownMenuItem(
                                        text = { Text(label) }, leadingIcon = { Icon(icon, null) }, trailingIcon = ticked(lit),
                                        onClick = { styling = false; state.command(name) }, modifier = Modifier.testTag("editor-style-$name"),
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(tr("Clear formatting")) }, leadingIcon = { Icon(Icons.Outlined.FormatClear, null) },
                                    onClick = { styling = false; state.command("clear") }, modifier = Modifier.testTag("editor-style-clear"),
                                )
                            }
                        }
                        var headings by remember { mutableStateOf(false) }
                        val block = when {
                            t.list == "ul" -> tr("Bulleted list")
                            t.list == "ol" -> tr("Numbered list")
                            else -> when (t.block) { "h1" -> tr("Heading 1"); "h2" -> tr("Heading 2"); "h3" -> tr("Heading 3"); "h4", "h5", "h6" -> tr("Heading"); else -> tr("Paragraph") }
                        }
                        Box(Modifier.tourPlace(EditorPlace.HEADING)) {
                            if (compact) {
                                // Lit while the cursor is in a heading.
                                IconToggleButton(checked = page && (t.block?.startsWith("h") == true || t.list != null || t.quote), onCheckedChange = { headings = true }, enabled = page, colors = on) {
                                    Icon(
                                        when (t.list) { "ul" -> Icons.AutoMirrored.Outlined.FormatListBulleted; "ol" -> Icons.Outlined.FormatListNumbered; else -> Icons.Outlined.Title },
                                        block,
                                    )
                                }
                            } else {
                                TextButton(onClick = { headings = true }, enabled = page) {
                                    Icon(Icons.Outlined.Title, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(block)
                                }
                            }
                            DropdownMenu(expanded = headings, onDismissRequest = { headings = false }) {
                                listOf("p" to tr("Paragraph"), "h1" to tr("Heading 1 (part)"), "h2" to tr("Heading 2 (chapter)"), "h3" to tr("Heading 3 (section)")).forEach { (tag, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        trailingIcon = if (t.list == null && (t.block ?: "p") == tag) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                                        onClick = { headings = false; state.command("block", tag) },
                                    )
                                }
                                // Lists and quotes: what the paragraph (or every paragraph selected) is part of.
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(tr("Bulleted list")) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, null) }, trailingIcon = ticked(t.list == "ul"),
                                    onClick = { headings = false; state.command("list", "ul") }, modifier = Modifier.testTag("editor-list-ul"),
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Numbered list")) }, leadingIcon = { Icon(Icons.Outlined.FormatListNumbered, null) }, trailingIcon = ticked(t.list == "ol"),
                                    onClick = { headings = false; state.command("list", "ol") }, modifier = Modifier.testTag("editor-list-ol"),
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Quote")) }, leadingIcon = { Icon(Icons.Outlined.FormatQuote, null) }, trailingIcon = ticked(t.quote),
                                    onClick = { headings = false; state.command("quote") }, modifier = Modifier.testTag("editor-quote"),
                                )
                                if (t.list != null) {
                                    DropdownMenuItem(
                                        text = { Text(tr("Indent more")) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, null) }, enabled = t.canIndent,
                                        onClick = { headings = false; state.command("indent") }, modifier = Modifier.testTag("editor-indent"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Indent less")) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, null) },
                                        onClick = { headings = false; state.command("outdent") }, modifier = Modifier.testTag("editor-outdent"),
                                    )
                                }
                            }
                        }
                        // How the paragraph (or every paragraph selected) is lined up.
                        var aligning by remember { mutableStateOf(false) }
                        val alignments = listOf(
                            Triple("left", tr("Align left"), Icons.AutoMirrored.Outlined.FormatAlignLeft), Triple("center", tr("Align centre"), Icons.Outlined.FormatAlignCenter),
                            Triple("right", tr("Align right"), Icons.AutoMirrored.Outlined.FormatAlignRight), Triple("justify", tr("Justify"), Icons.Outlined.FormatAlignJustify),
                        )
                        Box(Modifier.tourPlace(EditorPlace.ALIGN)) {
                            IconButton(onClick = { aligning = true }, enabled = page) {
                                Icon((alignments.firstOrNull { it.first == t.align } ?: alignments[0]).third, tr("Alignment"))
                            }
                            DropdownMenu(expanded = aligning, onDismissRequest = { aligning = false }) {
                                alignments.forEach { (how, label, icon) ->
                                    DropdownMenuItem(
                                        text = { Text(label) }, leadingIcon = { Icon(icon, null) },
                                        trailingIcon = if (t.align == how) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                                        onClick = { aligning = false; state.command("align", how) },
                                    )
                                }
                            }
                        }
                        // Links, footnotes, tables, pictures, scene breaks and characters not on the keyboard, where the cursor is;
                        // in a table, its rows and columns.
                        var inserting by remember { mutableStateOf(false) }
                        var linking by remember { mutableStateOf(false) }
                        var newTable by remember { mutableStateOf(false) }
                        var noting by remember { mutableStateOf(false) }
                        var characters by remember { mutableStateOf(false) }
                        Box(Modifier.tourPlace(EditorPlace.INSERT)) {
                            if (compact) {
                                IconButton(onClick = { inserting = true }, enabled = page, modifier = Modifier.testTag("editor-insert")) { Icon(Icons.Outlined.AddBox, tr("Insert")) }
                            } else {
                                TextButton(onClick = { inserting = true }, enabled = page, modifier = Modifier.testTag("editor-insert")) {
                                    Icon(Icons.Outlined.AddBox, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Insert"))
                                }
                            }
                            DropdownMenu(expanded = inserting, onDismissRequest = { inserting = false }) {
                                if (t.table) {
                                    listOf(
                                        "rowAbove" to tr("Add row above"), "rowBelow" to tr("Add row below"),
                                        "colLeft" to tr("Add column to the left"), "colRight" to tr("Add column to the right"),
                                    ).forEach { (op, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) }, leadingIcon = { Icon(Icons.Outlined.TableChart, null) },
                                            onClick = { inserting = false; state.tableOp(op) }, modifier = Modifier.testTag("editor-table-$op"),
                                        )
                                    }
                                    listOf("delRow" to tr("Delete row"), "delCol" to tr("Delete column"), "delTable" to tr("Delete table")).forEach { (op, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { inserting = false; state.tableOp(op) }, modifier = Modifier.testTag("editor-table-$op"),
                                        )
                                    }
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text(if (t.link != null) tr("Edit link…") else tr("Link…")) }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
                                    onClick = { inserting = false; linking = true }, modifier = Modifier.testTag("editor-link"),
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Footnote…")) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.StickyNote2, null) },
                                    onClick = { inserting = false; noting = true }, modifier = Modifier.testTag("editor-footnote"),
                                )
                                if (!t.table) DropdownMenuItem(
                                    text = { Text(tr("Table…")) }, leadingIcon = { Icon(Icons.Outlined.TableChart, null) },
                                    onClick = { inserting = false; newTable = true }, modifier = Modifier.testTag("editor-table"),
                                )
                                DropdownMenuItem(
                                    text = { Text(if (t.image != null) tr("Replace picture…") else tr("Picture…")) }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                    onClick = { inserting = false; onPicture() }, modifier = Modifier.testTag("editor-picture"),
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Scene break")) }, leadingIcon = { Icon(Icons.Outlined.HorizontalRule, null) },
                                    onClick = { inserting = false; state.command("scenebreak") }, modifier = Modifier.testTag("editor-scene-break"),
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Special character…")) }, leadingIcon = { Icon(Icons.Outlined.EmojiSymbols, null) },
                                    onClick = { inserting = false; characters = true }, modifier = Modifier.testTag("editor-characters"),
                                )
                            }
                        }
                        if (linking) LinkDialog(state, onDismiss = { linking = false })
                        if (newTable) TableDialog(onAdd = { r, c, h -> newTable = false; state.insertTable(r, c, h) }, onDismiss = { newTable = false })
                        if (noting) FootnoteDialog(onAdd = { noting = false; state.footnote(it) }, onDismiss = { noting = false })
                        if (characters) CharactersDialog(onPick = { characters = false; state.insertCharacter(it) }, onDismiss = { characters = false })
                        IconButton(onClick = { state.splitAtCursor() }, enabled = page, modifier = Modifier.tourPlace(EditorPlace.SPLIT)) { Icon(Icons.Outlined.CallSplit, tr("Split the chapter here")) }
                        // Selecting without dragging, and the clipboard: a long chapter is hard to select by its handles.
                        var selecting by remember { mutableStateOf(false) }
                        val context = LocalContext.current
                        Box(Modifier.tourPlace(EditorPlace.SELECT)) {
                            IconButton(onClick = { selecting = true }, enabled = page) { Icon(Icons.Outlined.SelectAll, tr("Select and copy")) }
                            DropdownMenu(expanded = selecting, onDismissRequest = { selecting = false }) {
                                listOf("all" to tr("Select all"), "toStart" to tr("Select to start"), "toEnd" to tr("Select to end"), "paragraph" to tr("Select paragraph")).forEach { (what, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { selecting = false; state.select(what) })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(tr("Copy")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, enabled = t.selection,
                                    onClick = { selecting = false; state.copySelection(context) })
                                DropdownMenuItem(text = { Text(tr("Cut")) }, leadingIcon = { Icon(Icons.Outlined.ContentCut, null) }, enabled = t.selection,
                                    onClick = { selecting = false; state.copySelection(context, cut = true) })
                                DropdownMenuItem(text = { Text(tr("Paste")) }, leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                    onClick = { selecting = false; state.pasteClipboard(context) })
                            }
                        }
                    }
                    // Sections copied out of books (this one or others), to add here: they open in a panel on the right.
                    if (state.copied != null) {
                        val open = state.copiedOpen
                        val toggle = { state.copiedOpen = !open; if (!open) state.find.open = false }
                        if (compact) {
                            // Just the picture, with how many there are.
                            IconToggleButton(checked = open, onCheckedChange = { toggle() }, colors = on, modifier = Modifier.tourPlace(EditorPlace.COPIED)) {
                                BadgedBox(badge = { if (copiedCount > 0) Badge { Text("$copiedCount") } }) {
                                    Icon(Icons.Outlined.ContentPaste, tr("Copied sections"))
                                }
                            }
                        } else {
                            TextButton(
                                onClick = toggle,
                                modifier = Modifier.tourPlace(EditorPlace.COPIED),
                                colors = if (open) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.textButtonColors(),
                            ) {
                                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (copiedCount > 0) tr("Copied ({0})", copiedCount) else tr("Copied"), maxLines = 1)
                            }
                        }
                    }
                    VerticalDivider(Modifier.height(28.dp).padding(horizontal = if (tight) 3.dp else 6.dp))
                    if (compact) {
                        IconToggleButton(checked = state.showCode, onCheckedChange = { state.setCode(it) }, colors = on, modifier = Modifier.tourPlace(EditorPlace.CODE)) {
                            Icon(Icons.Outlined.Code, tr("Code"))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.tourPlace(EditorPlace.CODE).clickable { state.setCode(!state.showCode) }.padding(start = 4.dp, end = 2.dp)) {
                            Icon(Icons.Outlined.Code, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Code"), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = state.showCode, onCheckedChange = { state.setCode(it) })
                        }
                    }
                }
            }
        }
    }
}

/** A tick at the end of a menu item, when it's what the paragraph (or the words) are. */
private fun ticked(lit: Boolean): (@Composable () -> Unit)? = if (lit) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null

/**
 * A link on the words selected (or the link the cursor is in): to a web address or to a chapter of the book. The words
 * shown can be changed, or written, when nothing was selected; a link can be taken off.
 */
@Composable
private fun LinkDialog(state: TextEditState, onDismiss: () -> Unit) {
    val t = remember { state.toolbar }
    val chapters = remember(state.version) { state.structure.chapters.distinctBy { it.path } }
    val was = remember { t.link?.let { state.chapterOf(it) } }
    var toChapter by remember { mutableStateOf(was != null) }
    var chapter by remember { mutableStateOf(was) }
    var address by remember { mutableStateOf(if (t.link != null && was == null) t.link else "") }
    var words by remember { mutableStateOf(t.text) }
    val href = if (toChapter) chapter?.let { state.hrefTo(it) } else webHref(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t.link != null) tr("Edit link") else tr("Add a link")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    words, { words = it }, singleLine = true, label = { Text(tr("Words shown")) },
                    modifier = Modifier.fillMaxWidth().testTag("link-words"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !toChapter, onClick = { toChapter = false }, label = { Text(tr("Web address")) }, modifier = Modifier.testTag("link-to-web"))
                    FilterChip(selected = toChapter, onClick = { toChapter = true }, label = { Text(tr("A chapter")) }, modifier = Modifier.testTag("link-to-chapter"))
                }
                if (!toChapter) {
                    OutlinedTextField(
                        address, { address = it }, singleLine = true, label = { Text(tr("Web address")) }, placeholder = { Text("https://") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("link-address"),
                    )
                } else {
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        chapters.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { chapter = c }.padding(vertical = 2.dp).testTag("link-chapter"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = chapter?.path == c.path, onClick = { chapter = c })
                                Text(c.title.ifBlank { c.path.substringAfterLast('/') }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { href?.let { state.link(it, words) }; onDismiss() }, enabled = href != null,
                modifier = Modifier.testTag("link-done"),
            ) { Text(if (t.link != null) tr("Save") else tr("Add")) }
        },
        dismissButton = {
            Row {
                if (t.link != null) TextButton(onClick = { state.unlink(); onDismiss() }, modifier = Modifier.testTag("link-remove")) {
                    Text(tr("Remove link"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
            }
        },
    )
}

/** Where a web address typed for a link goes: one typed without its beginning (example.org) is a web page's. */
internal fun webHref(typed: String): String? {
    val a = typed.trim().takeIf { it.isNotEmpty() } ?: return null
    return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(a)) a else "https://$a"
}

/** A new table: how many rows and columns, and whether the first row is a header. */
@Composable
private fun TableDialog(onAdd: (rows: Int, cols: Int, header: Boolean) -> Unit, onDismiss: () -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    var header by remember { mutableStateOf(true) }
    @Composable
    fun Count(label: String, value: Int, range: IntRange, tag: String, onChange: (Int) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.testTag("$tag-less")) { Icon(Icons.Filled.Remove, tr("Fewer")) }
            Text("$value", Modifier.width(36.dp).testTag(tag), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.testTag("$tag-more")) { Icon(Icons.Filled.Add, tr("More")) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add a table")) },
        text = {
            Column {
                Count(tr("Rows"), rows, 1..100, "table-rows") { rows = it }
                Count(tr("Columns"), cols, 1..12, "table-cols") { cols = it }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { header = !header }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(header, { header = it })
                    Text(tr("First row is a header"))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(rows, cols, header) }, modifier = Modifier.testTag("table-add")) { Text(tr("Add")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** A footnote's words: its number goes where the cursor is, and the note at the end of the chapter. */
@Composable
private fun FootnoteDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add a footnote")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Its number goes where the cursor is, and the note at the end of the chapter."), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it }, minLines = 3, label = { Text(tr("Footnote")) }, modifier = Modifier.fillMaxWidth().testTag("footnote-text"))
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("footnote-add")) { Text(tr("Add")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Characters that aren't on most keyboards: dashes, quotes, marks and signs. */
private val specialCharacters = listOf(
    "—", "–", "…", "“", "”", "‘", "’", "«", "»", "„", "‹", "›", "•", "·", "§", "¶", "†", "‡", "※", "⁂",
    "©", "®", "™", "°", "×", "÷", "±", "≈", "≠", "½", "¼", "¾", "€", "£", "¥", "¢", "→", "←", "♥", "★", "✓", "\u00A0",
)

/** Picks a character to put in where the cursor is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharactersDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Special characters")) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                specialCharacters.forEach { c ->
                    val nbsp = c == "\u00A0"
                    Surface(
                        onClick = { onPick(c) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(if (nbsp) 96.dp else 44.dp, 44.dp).testTag("character")
                            .semantics { contentDescription = if (nbsp) tr("Non-breaking space") else c },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (nbsp) tr("Non-breaking space") else c, style = if (nbsp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

/** How many words there are in the chapter, the whole book, and the words selected. */
@Composable
private fun WordCountDialog(state: TextEditState, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf("") }
    val counts by produceState<Pair<Int, Int>?>(null) {
        state.selectedText { selected = it }
        state.flush()
        value = state.wordCounts()
    }
    fun words(n: Int) = if (n == 1) tr("1 word") else tr("{0} words", "%,d".format(n))
    @Composable
    fun Line(label: String, value: String?, tag: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (value == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Numbers, null) },
        title = { Text(tr("Word count")) },
        text = {
            Column {
                Line(tr("This chapter"), counts?.let { words(it.first) }, "words-chapter")
                Line(tr("The whole book"), counts?.let { words(it.second) }, "words-book")
                if (selected.isNotBlank()) Line(tr("Selected"), words(EpubText.countWords(selected)), "words-selected")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun RenameDialog(a: ChapterAction.Rename, state: TextEditState, onDone: () -> Unit) {
    var title by remember { mutableStateOf(a.chapter.title) }
    val headingText = remember(a.chapter.path) {
        state.edit.text(a.chapter.path)?.let { t ->
            runCatching {
                val doc = dev.kolib.app.epub.Xml.parse(t)
                dev.kolib.app.epub.TextMap.body(doc).descendants().firstOrNull { dev.kolib.app.epub.TextMap.isHeading(it) }?.text()?.trim()
            }.getOrNull()
        }
    }
    var alsoHeading by remember { mutableStateOf(headingText != null && headingText == a.chapter.title) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Rename chapter")) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(tr("Name in contents")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (headingText != null) {
                    Row(Modifier.fillMaxWidth().clickable { alsoHeading = !alsoHeading }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoHeading, onCheckedChange = { alsoHeading = it })
                        Text(tr("Also change its heading (currently “{0}”)", headingText), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { state.rename(a.chapter.path, title, alsoHeading); onDone() }, enabled = title.isNotBlank()) { Text(tr("Rename")) } },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("Cancel")) } },
    )
}

/**
 * "Chapter 7" after "Chapter 6" (or after "Chapter 6: The Storm"), "Part 3" after "Part 2";
 * otherwise "Chapter n" for the next number.
 */
internal fun nextChapterName(afterTitle: String?, chapters: Int): String {
    val t = afterTitle?.trim().orEmpty()
    Regex("""^(.*?\D)?(\d+)$""").find(t)?.let { m ->
        val n = m.groupValues[2].toIntOrNull()
        if (n != null && n < 100_000) return m.groupValues[1] + (n + 1)
    }
    Regex("""^(\p{L}+\s+)(\d+)\b""").find(t)?.let { m ->
        val n = m.groupValues[2].toIntOrNull()
        if (n != null && n < 100_000) return m.groupValues[1] + (n + 1)
    }
    return tr("Chapter {0}", chapters + 1)
}

@Composable
private fun AddChapterDialog(a: ChapterAction.Add, state: TextEditState, onDone: () -> Unit) {
    val chapters = state.structure.chapters
    val after = a.after?.let { p -> chapters.firstOrNull { it.path == p } } ?: chapters.lastOrNull { it.linear }
    var title by remember { mutableStateOf(nextChapterName(after?.title, chapters.count { it.linear })) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Add chapter")) },
        text = {
            Column {
                Text(
                    if (after != null) tr("Added after “{0}”. Its name is its heading and its entry in the contents.", after.title)
                    else tr("Its name is its heading and its entry in the contents."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr("Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { state.addChapter(after?.path, title); onDone() }, enabled = title.isNotBlank()) { Text(tr("Add")) } },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun SplitDialog(a: ChapterAction.Split, state: TextEditState, onDone: () -> Unit) {
    val points by produceState<List<ChapterOps.SplitPoint>?>(null, a.chapter.path) {
        value = withContext(Dispatchers.Default) { state.splitPoints(a.chapter.path) }
    }
    var chosen by remember { mutableStateOf<ChapterOps.SplitPoint?>(null) }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Split “{0}”", a.chapter.title)) },
        text = {
            Column {
                Text(tr("Tap the paragraph where the new chapter starts."), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val list = points
                if (list == null) CircularProgressIndicator(Modifier.padding(16.dp))
                else if (list.isEmpty()) Text(tr("This chapter can't be split."), style = MaterialTheme.typography.bodySmall)
                else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(list) { p ->
                        val on = p == chosen
                        Text(
                            (if (p.tag.startsWith("h")) tr("Heading: ") else "") + p.text.ifBlank { tr("(empty {0})", p.tag) },
                            style = if (p.tag.startsWith("h")) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (on) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    chosen = p
                                    if (p.tag.startsWith("h") && title.isBlank()) title = p.text
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(tr("Name of the new chapter (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let { state.split(a.chapter.path, it.offset, title.ifBlank { null }) }; onDone() }, enabled = chosen != null) { Text(tr("Split here")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun CleanupsDialog(state: TextEditState, onDismiss: () -> Unit) {
    var thisChapter by remember { mutableStateOf(false) }
    val only = if (thisChapter) setOfNotNull(state.currentPath) else null
    val counts by produceState<Map<Cleanups.Kind, Int>?>(null, thisChapter, state.version) {
        value = null
        value = state.cleanupCounts(only)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
        title = { Text(tr("Clean-ups")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !thisChapter, onClick = { thisChapter = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { FittedText(tr("Whole book"), style = MaterialTheme.typography.labelLarge, smallest = 0.7f) }
                    SegmentedButton(selected = thisChapter, onClick = { thisChapter = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { FittedText(tr("This chapter"), style = MaterialTheme.typography.labelLarge, smallest = 0.7f) }
                }
                Spacer(Modifier.height(8.dp))
                Cleanups.Kind.entries.forEach { k ->
                    if (thisChapter && k == Cleanups.Kind.STRIP_FONTS) return@forEach
                    val n = counts?.get(k)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(k.label, style = MaterialTheme.typography.titleSmall)
                            Text(k.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when (n) { null -> tr("Checking…"); 0 -> tr("Nothing to fix"); else -> tr("Found {0}", k.describe(n)) },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (n != null && n > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { state.cleanup(k, only) }, enabled = n != null && n > 0) { Text(tr("Fix")) }
                    }
                    HorizontalDivider()
                }
                Text(tr("You can undo each fix."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done")) } },
    )
}
