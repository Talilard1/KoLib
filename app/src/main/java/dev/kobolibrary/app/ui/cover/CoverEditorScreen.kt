@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui.cover

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.GoldButton
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private class ShownPicture(val picture: CoverPicture) {
    val preview: ImageBitmap = picture.preview.asImageBitmap()
    val blurred: ImageBitmap = picture.blurred.asImageBitmap()
}

@Composable
fun CoverEditorScreen(
    book: LibraryBook,
    loadOriginal: suspend () -> ByteArray?,
    saving: Boolean,
    onSave: (SavedCover) -> Unit,
    onClose: () -> Unit,
    /** For previews and tests: start with this picture instead of reading the book. */
    initialPicture: CoverPicture? = null,
    /** The cover the book came with, if it has been changed since. */
    loadFirstCover: suspend () -> ByteArray? = { null },
    /** Puts the book's first cover back exactly as it was. */
    onRestoreFirstCover: (() -> Unit)? = null,
    /** For previews and tests: the first cover, instead of reading it. */
    initialFirstCover: CoverPicture? = null,
) {
    val coverText = remember(book.id, book.meta) {
        CoverText(book.meta.title, book.meta.authors, book.meta.series, book.meta.seriesIndex)
    }
    var generating by remember { mutableStateOf(false) }
    var findingOnline by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf(initialPicture?.let { ShownPicture(it) }) }
    var firstCover by remember { mutableStateOf(initialFirstCover?.let { ShownPicture(it) }) }
    var confirmRestore by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(original) }
    var loading by remember { mutableStateOf(initialPicture == null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // A cover left unchanged before opens that way again.
    val startCrop = remember(book.id) { CoverCrop(fit = if (book.coverAsIs) CoverFit.AS_IS else CoverFit.FILL) }
    var crop by remember { mutableStateOf(startCrop) }
    var greyscale by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(book.id) {
        if (initialPicture != null) return@LaunchedEffect
        val picture = withContext(Dispatchers.Default) {
            runCatching { loadOriginal()?.let { bytes -> CoverRendering.decode(bytes)?.let { CoverRendering.prepare(it, tr("The book's cover"), bytes) } } }
                .onFailure { AppLog.w("Cover", "Couldn't read the book's cover", it) }
                .getOrNull()
        }
        original = picture?.let { ShownPicture(it) }
        shown = original
        loading = false
    }
    LaunchedEffect(book.id, book.coverVersion) {
        if (initialFirstCover != null) return@LaunchedEffect
        val picture = withContext(Dispatchers.Default) {
            runCatching { loadFirstCover()?.let { bytes -> CoverRendering.decode(bytes)?.let { CoverRendering.prepare(it, tr("The original cover"), bytes) } } }
                .onFailure { AppLog.w("Cover", "Couldn't read the book's original cover", it) }
                .getOrNull()
        }
        firstCover = picture?.let { ShownPicture(it) }
    }

    fun open(uri: Uri?, label: String) {
        if (uri == null) return
        scope.launch {
            loading = true
            problem = null
            val picture = withContext(Dispatchers.Default) {
                runCatching {
                    // The file itself is kept (unless it's huge), so it can be saved unchanged.
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(256 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            total += n
                            if (total > MAX_KEPT_PICTURE) return@use null
                            buffer.write(chunk, 0, n)
                        }
                        buffer.toByteArray()
                    }
                    val bitmap = if (bytes != null) CoverRendering.decode(bytes) else CoverRendering.decode(context, uri)
                    bitmap?.let { CoverRendering.prepare(it, label, bytes) }
                }
                    .onFailure { AppLog.w("Cover", "Couldn't open the chosen picture", it) }
                    .getOrNull()
            }
            if (picture == null) {
                problem = tr("That picture couldn't be opened. Try a JPEG or PNG.")
            } else {
                shown = ShownPicture(picture)
                crop = CoverCrop(fit = crop.fit)
            }
            loading = false
        }
    }

    /** A picture found online, already fetched. */
    fun openBytes(bytes: ByteArray, label: String) {
        scope.launch {
            loading = true
            problem = null
            val picture = withContext(Dispatchers.Default) {
                runCatching { CoverRendering.decode(bytes)?.let { CoverRendering.prepare(it, label, bytes.takeIf { b -> b.size <= MAX_KEPT_PICTURE }) } }
                    .onFailure { AppLog.w("Cover", "Couldn't open the picture found online", it) }
                    .getOrNull()
            }
            if (picture == null) {
                problem = tr("That picture couldn't be opened. Try another.")
            } else {
                shown = ShownPicture(picture)
                crop = CoverCrop(fit = crop.fit)
            }
            loading = false
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { open(it, tr("Picture from Photos")) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { open(it, tr("Picture from Files")) }

    val changed = shown !== original || crop != startCrop
    val tryClose = { if (changed) confirmDiscard = true else onClose() }

    if (findingOnline) {
        CoverSearchScreen(
            title = dev.kobolibrary.app.reader.HeadTitles.book(book.meta.title, book.meta.seriesIndex),
            authors = book.meta.authors,
            isbn = book.meta.isbn,
            onPick = { bytes, label ->
                findingOnline = false
                openBytes(bytes, label)
            },
            onClose = { findingOnline = false },
        )
        return
    }
    if (generating) {
        CoverGeneratorScreen(
            text = coverText,
            onUse = { bitmap, design ->
                shown = ShownPicture(CoverRendering.prepare(bitmap, tr("Made here, in the {0} design", design.style.label)))
                crop = CoverCrop()
                problem = null
                generating = false
            },
            onCancel = { generating = false },
        )
        return
    }
    BackHandler { tryClose() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = tryClose) { Icon(Icons.Filled.Close, tr("Close")) } },
                title = {
                    Column {
                        Text(tr("Change cover"))
                        Text(book.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (saving || rendering) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                    GoldButton(
                        onClick = {
                            val pic = shown?.picture ?: return@GoldButton
                            val how = crop
                            rendering = true
                            scope.launch {
                                val cover = withContext(Dispatchers.Default) {
                                    if (how.fit == CoverFit.AS_IS) CoverRendering.asIs(pic) else CoverRendering.fitted(pic, how)
                                }
                                rendering = false
                                onSave(cover)
                            }
                        },
                        enabled = shown != null && !saving && !rendering,
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text(tr("Save cover")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val frame: @Composable (Modifier) -> Unit = { m ->
                CoverFrame(shown, crop, greyscale, loading, onCrop = { crop = it }, modifier = m)
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                CoverControls(
                    shown = shown?.picture,
                    hasOriginal = original != null,
                    showingOriginal = shown != null && shown === original,
                    hasFirst = firstCover != null,
                    showingFirst = shown != null && shown === firstCover,
                    onUseFirst = {
                        shown = firstCover
                        crop = CoverCrop(fit = crop.fit)
                        problem = null
                    },
                    onRestoreFirst = onRestoreFirstCover?.let { { confirmRestore = true } },
                    crop = crop,
                    greyscale = greyscale,
                    problem = problem,
                    onUseOriginal = {
                        shown = original
                        crop = CoverCrop(fit = crop.fit)
                    },
                    onPhotos = {
                        runCatching { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .onFailure { problem = tr("This device has no photo picker. Use Files instead.") }
                    },
                    onFiles = {
                        runCatching { filePicker.launch(arrayOf("image/*")) }
                            .onFailure { problem = tr("This device has no file picker.") }
                    },
                    onGenerate = { generating = true },
                    onFind = { findingOnline = true },
                    onCrop = { crop = it },
                    onGreyscale = { greyscale = it },
                    modifier = m,
                )
            }
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    frame(Modifier.weight(1f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(380.dp).fillMaxHeight()) {
                        controls(Modifier.fillMaxSize())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    frame(Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider()
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        controls(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (confirmRestore && onRestoreFirstCover != null) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(tr("Restore the original cover?")) },
            text = { Text(tr("The book will get back the cover it came with. You can change it again later.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    onRestoreFirstCover()
                }) { Text(tr("Restore")) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(tr("Cancel")) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("Discard the new cover?")) },
            text = { Text(tr("The book's cover won't change.")) },
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
private fun CoverFrame(
    shown: ShownPicture?,
    crop: CoverCrop,
    greyscale: Boolean,
    loading: Boolean,
    onCrop: (CoverCrop) -> Unit,
    modifier: Modifier,
) {
    val currentCrop by androidx.compose.runtime.rememberUpdatedState(crop)
    BoxWithConstraints(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        val boxW = min(maxWidth, maxHeight * CoverCrop.ASPECT)
        val boxH = boxW / CoverCrop.ASPECT
        Box(
            Modifier
                .size(boxW, boxH)
                .shadow(8.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .pointerInput(shown) {
                    val pic = shown?.picture ?: return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        onCrop(
                            currentCrop.transformed(
                                pan.x, pan.y, zoom, centroid.x, centroid.y,
                                size.width.toFloat(), size.height.toFloat(), pic.width, pic.height,
                            )
                        )
                    }
                }
                .pointerInput(shown) {
                    val pic = shown?.picture ?: return@pointerInput
                    detectTapGestures(onDoubleTap = { pos ->
                        val c = currentCrop
                        val target = if (c.zoom < 1.9f) 2f else 1f
                        onCrop(c.transformed(0f, 0f, target / c.zoom, pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), pic.width, pic.height))
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (shown != null) {
                val filter = remember(greyscale) { if (greyscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null }
                Canvas(Modifier.fillMaxSize()) {
                    if (crop.fit == CoverFit.FIT_BLUR) {
                        val bg = shown.blurred
                        val s = max(size.width / bg.width, size.height / bg.height)
                        val w = bg.width * s
                        val h = bg.height * s
                        drawImage(
                            bg,
                            dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            colorFilter = filter,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    val p = crop.place(size.width, size.height, shown.picture.width, shown.picture.height)
                    drawImage(
                        shown.preview,
                        dstOffset = IntOffset(p.left.roundToInt(), p.top.roundToInt()),
                        dstSize = IntSize(p.width.roundToInt(), p.height.roundToInt()),
                        colorFilter = filter,
                        filterQuality = FilterQuality.High,
                    )
                }
            } else if (loading) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Outlined.AutoStories, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(tr("No cover yet. Choose a picture."), color = Color(0xFF616161), textAlign = TextAlign.Center)
                }
            }
            if (loading && shown != null) CircularProgressIndicator()
        }
    }
}

@Composable
private fun CoverControls(
    shown: CoverPicture?,
    hasOriginal: Boolean,
    showingOriginal: Boolean,
    hasFirst: Boolean,
    showingFirst: Boolean,
    onUseFirst: () -> Unit,
    onRestoreFirst: (() -> Unit)?,
    crop: CoverCrop,
    greyscale: Boolean,
    problem: String?,
    onUseOriginal: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onGenerate: () -> Unit,
    onFind: () -> Unit,
    onCrop: (CoverCrop) -> Unit,
    onGreyscale: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("Picture"), style = MaterialTheme.typography.titleSmall)
        if (hasOriginal || hasFirst) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasOriginal) {
                    FilterChip(
                        selected = showingOriginal,
                        onClick = onUseOriginal,
                        label = { Text(if (hasFirst) tr("Current cover") else tr("The book's cover")) },
                    )
                }
                if (hasFirst) {
                    FilterChip(
                        selected = showingFirst,
                        onClick = onUseFirst,
                        leadingIcon = { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)) },
                        label = { Text(tr("Original cover")) },
                    )
                }
            }
        }
        if (showingFirst && onRestoreFirst != null) {
            Text(
                tr("This is the cover the book came with. You can fit it to the Kobo's screen like any other picture, or restore it unchanged."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRestoreFirst, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Restore it unchanged"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPhotos) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Photos"))
            }
            OutlinedButton(onClick = onFiles) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Files"))
            }
            OutlinedButton(onClick = onFind) {
                Icon(Icons.Outlined.TravelExplore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Find online"))
            }
            OutlinedButton(onClick = onGenerate) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Make a cover"))
            }
        }
        if (shown != null) {
            Text(
                "${shown.label} · ${shown.width} × ${shown.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shown.height < CoverCrop.OUT_HEIGHT * 0.6f && shown.width < CoverCrop.OUT_WIDTH * 0.6f) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tr("This picture is much smaller than the Kobo's screen, so it may look blurry."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(tr("Fit"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverFit.entries.forEach { f ->
                FilterChip(
                    selected = crop.fit == f,
                    onClick = { onCrop(CoverCrop(fit = f)) },
                    enabled = shown != null,
                    leadingIcon = if (crop.fit == f) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                    label = { Text(f.label, maxLines = 1) },
                )
            }
        }
        Text(crop.fit.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Zoom"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { onCrop(CoverCrop(fit = crop.fit)) }, enabled = shown != null && crop.fit != CoverFit.AS_IS && crop != CoverCrop(fit = crop.fit)) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Reset"))
            }
        }
        Slider(
            value = crop.zoom,
            onValueChange = { z ->
                shown?.let { onCrop(crop.zoomedTo(z, CoverCrop.OUT_WIDTH.toFloat(), CoverCrop.OUT_HEIGHT.toFloat(), it.width, it.height)) }
            },
            valueRange = CoverCrop.MIN_ZOOM..CoverCrop.MAX_ZOOM,
            enabled = shown != null && crop.fit != CoverFit.AS_IS,
        )
        Text(
            if (crop.fit == CoverFit.AS_IS) tr("Moving and zooming are off while the picture is left unchanged.")
            else tr("Drag the picture to move it, pinch to zoom, or double-tap to zoom in and out."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Black and white preview"), style = MaterialTheme.typography.titleSmall)
                Text(tr("About how it will look on the Kobo's screen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = greyscale, onCheckedChange = onGreyscale)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (crop.fit == CoverFit.AS_IS) {
                shown?.let { tr("The picture is saved in the book file at its own size ({0} × {1} pixels) and sent to the Kobo unchanged.", it.width, it.height) }
                    ?: tr("The picture is saved in the book file at its own size and sent to the Kobo unchanged.")
            } else {
                tr("The cover is saved inside the book file at {0} × {1} pixels, the size of the Kobo Clara's screen.", CoverCrop.OUT_WIDTH, CoverCrop.OUT_HEIGHT)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A picture bigger than this isn't kept as a file; "Leave unchanged" then keeps its pixels instead. */
private const val MAX_KEPT_PICTURE = 40L * 1024 * 1024
