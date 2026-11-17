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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<ChapterAction?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var showCleanups by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
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

    CompositionLocalProvider(LocalTourPlaces provides places.takeIf { touring }) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // On a phone the chapters are a screen of their own: while the page shows, the chapter's name in the top bar leads there.
        val chaptersApart = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
        val pageShowing = narrowShowsEditor && !state.find.open && !(state.copiedOpen && state.copied != null)
        EditorTopBar(state, onBack = close, onRead = { scope.launch { state.flush(); onRead(state.currentPath) } },
            onFind = { state.find.open = !state.find.open; if (state.find.open) state.copiedOpen = false },
            onCleanups = { showCleanups = true }, onRevert = { confirmRevert = true }, onTour = { touring = true },
            onChapters = if (chaptersApart && pageShowing) ({ narrowShowsEditor = false }) else null)
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val wide = maxWidth >= 1000.dp
            val medium = maxWidth >= 600.dp
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
            when {
                wide -> Row(Modifier.fillMaxSize()) {
                    chapters(Modifier.width(290.dp).fillMaxHeight().tourPlace(EditorPlace.CHAPTERS))
                    VerticalDivider()
                    editor(Modifier.weight(1f).fillMaxHeight())
                    if (state.find.open) {
                        VerticalDivider()
                        findPanel(Modifier.width(390.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                    } else if (copiedOpen) {
                        VerticalDivider()
                        copiedPanel(Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                    }
                }
                medium -> Row(Modifier.fillMaxSize()) {
                    if (state.find.open) findPanel(Modifier.width(340.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                    else if (!copiedOpen) chapters(Modifier.width(270.dp).fillMaxHeight().tourPlace(EditorPlace.CHAPTERS))
                    if (!copiedOpen) VerticalDivider()
                    editor(Modifier.weight(1f).fillMaxHeight())
                    if (copiedOpen) {
                        VerticalDivider()
                        copiedPanel(Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                    }
                }
                state.find.open -> findPanel(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
                copiedOpen -> copiedPanel(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
                narrowShowsEditor -> editor(Modifier.fillMaxSize())
                else -> chapters(Modifier.fillMaxSize())
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
        val toolsInMenu = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
        val edge = with(LocalDensity.current) { 10.dp.toPx() }
        val stops = remember(toolsInMenu, edge) { editorTourStops(places, toolbarScroll, toolsInMenu, edge) }
        Tour(stops, places, onDone = {
            touring = false
            onTourDone()
        })
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
    /** Opens the list of chapters, where it's a screen of its own (a phone) and the page is showing: the chapter's name is the way to it. */
    onChapters: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
        // The status bar is hidden while the editor is open, but a camera cutout, or the status bar
        // if it does show, would otherwise cover the buttons.
        val clear = WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(clear)) {
            // On a phone the less used buttons move into the menu.
            val roomy = maxWidth >= 600.dp
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
                            DropdownMenuItem(text = { Text(tr("Preview in reader")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }, onClick = { menu = false; onRead() })
                            HorizontalDivider()
                        }
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
                    onRelease = { state.releaseView() },
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
            val compact = maxWidth < 600.dp
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
                        var headings by remember { mutableStateOf(false) }
                        val block = when (t.block) { "h1" -> tr("Heading 1"); "h2" -> tr("Heading 2"); "h3" -> tr("Heading 3"); "h4", "h5", "h6" -> tr("Heading"); else -> tr("Paragraph") }
                        Box(Modifier.tourPlace(EditorPlace.HEADING)) {
                            if (compact) {
                                // Lit while the cursor is in a heading.
                                IconToggleButton(checked = page && t.block?.startsWith("h") == true, onCheckedChange = { headings = true }, enabled = page, colors = on) {
                                    Icon(Icons.Outlined.Title, block)
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
                                        trailingIcon = if ((t.block ?: "p") == tag) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                                        onClick = { headings = false; state.command("block", tag) },
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
                        IconButton(onClick = { state.command("scenebreak") }, enabled = page, modifier = Modifier.tourPlace(EditorPlace.SCENE_BREAK)) { Icon(Icons.Outlined.HorizontalRule, tr("Scene break")) }
                        val picture = if (t.image != null) tr("Replace picture") else tr("Picture")
                        if (compact) {
                            IconButton(onClick = onPicture, enabled = page, modifier = Modifier.tourPlace(EditorPlace.PICTURE)) { Icon(Icons.Outlined.Image, picture) }
                        } else {
                            TextButton(onClick = onPicture, enabled = page, modifier = Modifier.tourPlace(EditorPlace.PICTURE)) {
                                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(picture)
                            }
                        }
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
