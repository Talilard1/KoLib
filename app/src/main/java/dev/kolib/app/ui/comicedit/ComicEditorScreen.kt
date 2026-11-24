package dev.kolib.app.ui.comicedit

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import dev.kolib.app.comic.ComicArchive
import dev.kolib.app.comic.ComicArchives
import dev.kolib.app.comic.ComicDraft
import dev.kolib.app.comic.ComicInfo
import dev.kolib.app.comic.ComicWriter
import dev.kolib.app.comic.Crop
import dev.kolib.app.comic.DraftPage
import dev.kolib.app.comic.PageSource
import dev.kolib.app.tr
import dev.kolib.app.ui.components.BarAction
import dev.kolib.app.ui.components.PhoneActionBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * What the comic editor works on: the pages it starts with and each page's picture (the comic's own file's, or
 * pictures added), and the comic's details as its file has them.
 */
class ComicPictures(
    val start: List<DraftPage>,
    private val inComic: ((Int) -> ByteArray)?,
    /** The comic's ComicInfo.xml, kept when its pages are saved. */
    val comicInfo: ByteArray? = null,
    /** It reads right to left (a two-page spread's right half comes first). */
    val rightToLeft: Boolean = false,
    private val closing: () -> Unit = {},
) : Closeable {
    private val lock = Any()

    fun bytes(source: PageSource): ByteArray = when (source) {
        // (Read one at a time: the comic's file may not be read from two places at once.)
        is PageSource.InComic -> synchronized(lock) { inComic!!.invoke(source.index) }
        is PageSource.Picture -> source.file.readBytes()
    }

    override fun close() = closing()

    companion object {
        fun of(archive: ComicArchive, name: String): ComicPictures = ComicPictures(
            start = archive.pages.indices.map { DraftPage(it.toLong() + 1, PageSource.InComic(it)) },
            inComic = archive::bytes,
            comicInfo = archive.comicInfo,
            rightToLeft = runCatching { ComicInfo.read(archive.comicInfo, name).rightToLeft }.getOrDefault(false),
            closing = archive::close,
        )

        fun of(files: List<File>): ComicPictures = ComicPictures(files.mapIndexed { i, f -> DraftPage(i.toLong() + 1, PageSource.Picture(f)) }, null)
    }
}

/**
 * What's to be saved: the comics the pages make (one, or the parts of a comic split), whether the comic they came from
 * is kept as it was, and for a new comic, its title, series and number.
 */
class ComicEdit(
    val parts: List<List<DraftPage>>,
    val keepOriginal: Boolean = true,
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
)

/** Where pictures picked for a comic are kept till it's made. */
const val PICTURES_FOLDER = "comic-pictures"

/** The pictures at [uris] kept as files (in [PICTURES_FOLDER]), in the order of their names (page2 before page10). */
fun copyPictures(context: Context, uris: List<Uri>): List<File> {
    val dir = File(context.cacheDir, PICTURES_FOLDER).apply { mkdirs() }
    val kept = uris.mapIndexedNotNull { i, uri ->
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "picture $i"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@mapIndexedNotNull null
        name to File(dir, "${System.nanoTime()}-$i.${ComicWriter.kindOf(bytes)}").apply { writeBytes(bytes) }
    }
    return kept.sortedWith { a, b -> ComicArchives.natural(a.first, b.first) }.map { it.second }
}

/** The pages' pictures, small, as they'll be (halved, turned, cropped): kept for a while, as they're slow to make. */
private class Thumbs(private val pictures: ComicPictures) {
    private val cache = android.util.LruCache<String, ImageBitmap>(200)

    private fun keyOf(p: DraftPage, height: Int) = "${p.source}|${p.half}|${p.turns}|${p.crop}|$height"

    fun cached(p: DraftPage, height: Int = SMALL): ImageBitmap? = cache.get(keyOf(p, height))

    suspend fun load(p: DraftPage, height: Int = SMALL): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(keyOf(p, height)) ?: runCatching {
            val bytes = pictures.bytes(p.source)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= height && bounds.outWidth / (sample * 2) >= height / 3) sample *= 2
            val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            picture?.let { ComicWriter.transform(it, p).asImageBitmap() }
        }.getOrNull()?.also { cache.put(keyOf(p, height), it) }
    }

    companion object {
        const val SMALL = 360
        const val LARGE = 1600
    }
}

private enum class Asking { CLOSE, SPLIT, NEW, MOVE }

/**
 * The comic editor: a comic's pages (or pictures picked for a new one) to put in order, turn, crop, split in two (a
 * two-page spread), copy, make the cover or take out; more pictures added; and the comic split into comics where
 * new ones start. Saved, [onSave] is given what to save (and [ComicPictures], to read the pages from: it's closed
 * by whoever saves them); closed without saving, [onClose].
 */
@Composable
fun ComicEditorScreen(
    title: String,
    open: suspend () -> ComicPictures,
    isNew: Boolean = false,
    /** Opened to split the comic: says how. */
    splitting: Boolean = false,
    onSave: (ComicPictures, ComicEdit) -> Unit,
    onClose: () -> Unit,
) {
    val opened by produceState<Result<ComicPictures>?>(null) { value = runCatching { withContext(Dispatchers.IO) { open() } } }
    val pictures = opened?.getOrNull()
    var handedOver by remember { mutableStateOf(false) }
    DisposableEffect(pictures) { onDispose { if (!handedOver) pictures?.close() } }
    Surface(Modifier.fillMaxSize().testTag("comic-editor"), color = MaterialTheme.colorScheme.background) {
        when {
            opened == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            pictures == null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                BackHandler(onBack = onClose)
                Text(tr("The comic couldn't be opened."), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text(tr("Close")) }
            }
            else -> ComicEditor(pictures, title, isNew, splitting, onSave = { edit -> handedOver = true; onSave(pictures, edit) }, onClose = onClose)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComicEditor(
    pictures: ComicPictures,
    title: String,
    isNew: Boolean,
    splitting: Boolean,
    onSave: (ComicEdit) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val draft = remember(pictures) { ComicDraft(pictures.start) }
    val thumbs = remember(pictures) { Thumbs(pictures) }
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    var cropping by remember { mutableStateOf<DraftPage?>(null) }
    var asking by remember { mutableStateOf<Asking?>(null) }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val changed = draft.canUndo || isNew
    fun toggle(key: Long) {
        picked = if (key in picked) picked - key else picked + key
    }
    val close: () -> Unit = { if (changed && draft.pages.isNotEmpty()) asking = Asking.CLOSE else onClose() }
    BackHandler { if (picked.isNotEmpty()) picked = emptySet() else close() }

    val addPictures = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val files = withContext(Dispatchers.IO) { copyPictures(context, uris) }
            // After the last page chosen, or at the end.
            val after = draft.pages.indexOfLast { it.key in picked }
            draft.insert(if (after < 0) draft.pages.size else after + 1, files)
        }
    }
    val save: () -> Unit = {
        val parts = draft.parts()
        when {
            isNew -> asking = Asking.NEW
            parts.size > 1 -> asking = Asking.SPLIT
            else -> onSave(ComicEdit(parts))
        }
    }

    // A page held is lifted and follows the finger; let go over another page, it's put there.
    var lifted by remember { mutableStateOf<Long?>(null) }
    var liftedBy by remember { mutableStateOf(Offset.Zero) }
    var liftedFrom by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = close, modifier = Modifier.testTag("comic-edit-close")) { Icon(Icons.Filled.Close, tr("Close")) } },
                title = { Text(if (isNew) tr("New comic") else title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { draft.undo() }, enabled = draft.canUndo, modifier = Modifier.testTag("comic-edit-undo")) { Icon(Icons.AutoMirrored.Filled.Undo, tr("Undo")) }
                    IconButton(onClick = { draft.redo() }, enabled = draft.canRedo, modifier = Modifier.testTag("comic-edit-redo")) { Icon(Icons.AutoMirrored.Filled.Redo, tr("Redo")) }
                    IconButton(onClick = { addPictures.launch("image/*") }, modifier = Modifier.testTag("comic-edit-add")) { Icon(Icons.Outlined.AddPhotoAlternate, tr("Add pictures")) }
                    TextButton(onClick = save, enabled = draft.pages.isNotEmpty() && changed, modifier = Modifier.testTag("comic-edit-save")) { Text(tr("Save")) }
                },
            )
        },
        bottomBar = {
            if (picked.isNotEmpty()) {
                val one = draft.pages.singleOrNull { it.key in picked }?.takeIf { picked.size == 1 }
                val at = draft.pages.indexOfFirst { it.key in picked }
                PhoneActionBar(
                    listOf(
                        BarAction(Icons.Filled.RotateLeft, tr("Left"), { draft.turn(picked, -1) }, tag = "comic-edit-turn-left", longLabel = tr("Turn left")),
                        BarAction(Icons.Filled.RotateRight, tr("Right"), { draft.turn(picked, 1) }, tag = "comic-edit-turn-right", longLabel = tr("Turn right")),
                        BarAction(Icons.Outlined.Crop, tr("Crop"), { cropping = one }, enabled = one != null, tag = "comic-edit-crop"),
                        BarAction(Icons.Outlined.VerticalSplit, tr("Halves"), { draft.splitSpreads(picked, pictures.rightToLeft); picked = emptySet() }, tag = "comic-edit-halves", canWait = true, longLabel = tr("Split a two-page spread")),
                        BarAction(Icons.Outlined.SwapVert, tr("Move"), { asking = Asking.MOVE }, tag = "comic-edit-move", canWait = true, longLabel = tr("Move to page…")),
                        BarAction(Icons.Outlined.Image, tr("Cover"), { one?.let { draft.toFront(it.key) } }, enabled = one != null && at > 0, tag = "comic-edit-cover", canWait = true, longLabel = tr("Make it the cover")),
                        BarAction(Icons.AutoMirrored.Outlined.CallSplit, tr("New comic"), { one?.let { draft.toggleStart(it.key) } }, enabled = one != null && at > 0, tag = "comic-edit-new-comic", canWait = true, longLabel = tr("A new comic starts here")),
                        BarAction(Icons.Outlined.ContentCopy, tr("Duplicate"), { draft.duplicate(picked) }, tag = "comic-edit-duplicate", canWait = true),
                        BarAction(Icons.Outlined.Delete, tr("Delete"), { draft.delete(picked); picked = emptySet() }, destructive = true, tag = "comic-edit-delete"),
                    ),
                    Modifier.navigationBarsPadding(),
                    moreTag = "comic-edit-more",
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (picked.isEmpty()) {
                    Text(
                        if (splitting) tr("Tap the page a new comic starts on, then New comic in the bar.")
                        else tr("Tap pages to choose them. Hold one and drag it to move it."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Text(
                        if (picked.size == 1) tr("1 page chosen") else tr("{0} pages chosen", picked.size),
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { picked = draft.pages.map { it.key }.toSet() }) { Text(tr("Select all")) }
                    TextButton(onClick = { picked = emptySet() }) { Text(tr("Clear")) }
                }
            }
            if (draft.pages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("No pages. Add pictures to make a comic."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                state = grid,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxSize().testTag("comic-edit-pages"),
            ) {
                itemsIndexed(draft.pages, key = { _, p -> p.key }) { i, page ->
                    PageCell(
                        page, i, chosen = page.key in picked, thumbs = thumbs,
                        lift = if (lifted == page.key) liftedBy else null,
                        onTap = { toggle(page.key) },
                        modifier = Modifier.pointerInput(page.key) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Let go (a tap) or moved (scrolling) before it's held long enough: not lifted.
                                val notHeld = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    var result: Boolean? = null
                                    while (result == null) {
                                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                        if (c == null || !c.pressed) {
                                            toggle(page.key)
                                            result = true
                                        } else if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) result = true
                                    }
                                    result
                                }
                                if (notHeld != null) return@awaitEachGesture
                                val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == page.key }
                                lifted = page.key
                                liftedBy = Offset.Zero
                                liftedFrom = info?.let { Offset(it.offset.x + it.size.width / 2f, it.offset.y + it.size.height / 2f) } ?: Offset.Zero
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    liftedBy += c.positionChange()
                                    c.consume()
                                }
                                // Held without moving: chosen; moved over another page: put there.
                                val from = draft.pages.indexOfFirst { it.key == page.key }
                                val over = (liftedFrom + liftedBy).let { at ->
                                    grid.layoutInfo.visibleItemsInfo.firstOrNull {
                                        at.x >= it.offset.x && at.x < it.offset.x + it.size.width && at.y >= it.offset.y && at.y < it.offset.y + it.size.height
                                    }
                                }
                                if (liftedBy.getDistance() < viewConfiguration.touchSlop) toggle(page.key)
                                else if (over != null && over.index != from) draft.move(from, over.index)
                                lifted = null
                                liftedBy = Offset.Zero
                            }
                        },
                    )
                }
            }
        }
    }

    cropping?.let { page ->
        CropDialog(page, thumbs, onDone = { crop -> draft.crop(page.key, crop); cropping = null }, onDismiss = { cropping = null })
    }
    when (asking) {
        Asking.MOVE -> MoveDialog(draft.pages.size, onMove = { to -> draft.moveTo(picked, to); asking = null }, onDismiss = { asking = null })
        Asking.CLOSE -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(tr("Save your changes?")) },
            confirmButton = {
                Row {
                    TextButton(onClick = { asking = null; onClose() }) { Text(tr("Don't save")) }
                    TextButton(onClick = { asking = null; save() }) { Text(tr("Save")) }
                }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(tr("Keep editing")) } },
        )
        Asking.SPLIT -> SplitDialog(draft.parts().size, onSplit = { keep -> asking = null; onSave(ComicEdit(draft.parts(), keepOriginal = keep)) }, onDismiss = { asking = null })
        Asking.NEW -> NewComicDialog(
            parts = draft.parts().size,
            onMake = { t, series, number -> asking = null; onSave(ComicEdit(draft.parts(), title = t, series = series, number = number)) },
            onDismiss = { asking = null },
        )
        null -> Unit
    }
}

/** A page in the grid: its picture as it'll be, its number, and whether it's chosen or starts a new comic. */
@Composable
private fun PageCell(page: DraftPage, index: Int, chosen: Boolean, thumbs: Thumbs, lift: Offset?, onTap: () -> Unit, modifier: Modifier) {
    val picture by produceState(thumbs.cached(page), page.source, page.half, page.turns, page.crop) { value = thumbs.load(page) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .zIndex(if (lift != null) 1f else 0f)
            .graphicsLayer {
                if (lift != null) {
                    translationX = lift.x
                    translationY = lift.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                    shadowElevation = 12.dp.toPx()
                }
            }
            .semantics { onClick(label = null) { onTap(); true } }
            .padding(4.dp)
            .testTag("comic-edit-page"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            picture?.let { Image(it, null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit) }
                ?: CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (chosen) Icon(
                Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
            )
            if (page.startsComic) Text(
                tr("New comic"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp).testTag("comic-edit-starts"),
            )
        }
        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Which page the pages chosen go to. */
@Composable
private fun MoveDialog(count: Int, onMove: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val n = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Move to page")) },
        text = {
            OutlinedTextField(
                text, { text = it.filter(Char::isDigit).take(5) }, singleLine = true,
                label = { Text(tr("Page (1 to {0})", count)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag("comic-edit-move-to"),
            )
        },
        confirmButton = { TextButton(onClick = { n?.let { onMove(it - 1) } }, enabled = n != null && n in 1..count) { Text(tr("Move")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Splitting the comic into [parts] comics: and whether the comic stays as it was too. */
@Composable
private fun SplitDialog(parts: Int, onSplit: (keep: Boolean) -> Unit, onDismiss: () -> Unit) {
    var keep by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Split into {0} comics?", parts)) },
        text = {
            Column {
                Text(tr("Each part becomes a comic of its own, in one series."))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keep, { keep = it }, modifier = Modifier.testTag("comic-split-keep"))
                    Text(tr("Keep the comic as it was too"))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSplit(keep) }, modifier = Modifier.testTag("comic-split-go")) { Text(tr("Split")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** A new comic's title (and series and number, for one comic). */
@Composable
private fun NewComicDialog(parts: Int, onMake: (String, String?, String?) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parts == 1) tr("New comic") else tr("{0} new comics", parts)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(tr("Title")) }, modifier = Modifier.testTag("comic-new-title"))
                OutlinedTextField(series, { series = it }, singleLine = true, label = { Text(tr("Series")) })
                if (parts == 1) OutlinedTextField(
                    number, { number = it.take(8) }, singleLine = true, label = { Text(tr("Number")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMake(title.trim(), series.trim().ifEmpty { null }, number.trim().ifEmpty { null }) },
                enabled = title.isNotBlank(), modifier = Modifier.testTag("comic-new-make"),
            ) { Text(tr("Make")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** A page's picture to crop: its edges (or the part kept) dragged; Reset keeps it whole. */
@Composable
private fun CropDialog(page: DraftPage, thumbs: Thumbs, onDone: (Crop?) -> Unit, onDismiss: () -> Unit) {
    val whole = remember(page) { page.copy(crop = null) }
    val picture by produceState<ImageBitmap?>(null, whole) { value = thumbs.load(whole, Thumbs.LARGE) }
    var box by remember { mutableStateOf(page.crop ?: Crop.WHOLE) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("comic-crop"), color = Color.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { box = Crop.WHOLE }) { Text(tr("Reset"), color = Color.White) }
                    TextButton(onClick = { onDone(box.takeIf { !it.whole }) }, modifier = Modifier.testTag("comic-crop-done")) { Text(tr("Done"), color = Color.White) }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    val pic = picture
                    if (pic == null) {
                        CircularProgressIndicator()
                    } else {
                        val aspect = pic.width.toFloat() / pic.height
                        val w = min(maxWidth.value, maxHeight.value * aspect)
                        Box(
                            Modifier.size(w.dp, (w / aspect).dp).pointerInput(Unit) {
                                val grab = 26.dp.toPx()
                                var moving = 0
                                detectDragGestures(
                                    onDragStart = { at ->
                                        val b = box
                                        val l = b.left * size.width
                                        val t = b.top * size.height
                                        val r = b.right * size.width
                                        val btm = b.bottom * size.height
                                        // Which edges go with the finger: those it's on (a corner is two); inside, the whole part kept.
                                        moving = 0
                                        if (abs(at.x - l) < grab && at.y in t - grab..btm + grab) moving = moving or LEFT
                                        if (abs(at.x - r) < grab && at.y in t - grab..btm + grab) moving = moving or RIGHT
                                        if (abs(at.y - t) < grab && at.x in l - grab..r + grab) moving = moving or TOP
                                        if (abs(at.y - btm) < grab && at.x in l - grab..r + grab) moving = moving or BOTTOM
                                        if (moving == 0 && at.x in l..r && at.y in t..btm) moving = ALL
                                    },
                                    onDrag = { change, by ->
                                        change.consume()
                                        box = moved(box, moving, by.x / size.width, by.y / size.height)
                                    },
                                )
                            },
                        ) {
                            Image(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            Canvas(Modifier.fillMaxSize()) {
                                val l = box.left * size.width
                                val t = box.top * size.height
                                val r = box.right * size.width
                                val b = box.bottom * size.height
                                val shade = Color.Black.copy(alpha = 0.6f)
                                drawRect(shade, Offset.Zero, Size(size.width, t))
                                drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                                drawRect(shade, Offset(0f, t), Size(l, b - t))
                                drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                                val k = 10.dp.toPx()
                                for (x in listOf(l, r)) for (y in listOf(t, b)) drawRect(Color.White, Offset(x - k / 2, y - k / 2), Size(k, k))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val LEFT = 1
private const val TOP = 2
private const val RIGHT = 4
private const val BOTTOM = 8
private const val ALL = 15

/** [c] with its edges [moving] moved by [dx] and [dy] (fractions of the picture), kept on it and never too small. */
internal fun moved(c: Crop, moving: Int, dx: Float, dy: Float, least: Float = 0.08f): Crop {
    if (moving == ALL) {
        val w = c.right - c.left
        val h = c.bottom - c.top
        val l = (c.left + dx).coerceIn(0f, 1f - w)
        val t = (c.top + dy).coerceIn(0f, 1f - h)
        return Crop(l, t, l + w, t + h)
    }
    var l = c.left
    var t = c.top
    var r = c.right
    var b = c.bottom
    if (moving and LEFT != 0) l = (l + dx).coerceIn(0f, r - least)
    if (moving and RIGHT != 0) r = (r + dx).coerceIn(l + least, 1f)
    if (moving and TOP != 0) t = (t + dy).coerceIn(0f, b - least)
    if (moving and BOTTOM != 0) b = (b + dy).coerceIn(t + least, 1f)
    return Crop(l, t, r, b)
}
