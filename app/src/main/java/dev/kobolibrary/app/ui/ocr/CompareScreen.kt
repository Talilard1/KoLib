package dev.kobolibrary.app.ui.ocr

import dev.kobolibrary.app.tr
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.convert.AndroidPdfPages
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.ocr.OcrPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Words the recognition was less sure of than this (of 100) are marked. */
private const val UNSURE = 60

/** The scanned pages to compare the text with: how many, and each one drawn. */
interface ScanPages : java.io.Closeable {
    val count: Int

    /** Page [index] drawn to fit [maxW] × [maxH] pixels; null if it can't be. */
    fun draw(index: Int, maxW: Int, maxH: Int): android.graphics.Bitmap?
}

/** A PDF's pages, drawn by Android. */
class PdfScanPages(file: java.io.File) : ScanPages {
    private val pdf = AndroidPdfPages(file)
    override val count: Int get() = pdf.count
    override fun draw(index: Int, maxW: Int, maxH: Int): android.graphics.Bitmap? = runCatching {
        val (pw, ph) = pdf.size(index)
        val scale = minOf(maxW.toFloat() / pw, maxH.toFloat() / ph)
        pdf.bitmap(index, (pw * scale).roundToInt().coerceAtLeast(1), (ph * scale).roundToInt().coerceAtLeast(1))
    }.getOrNull()
    override fun close() = pdf.close()
}

/**
 * A page of a scanned book as it was scanned (left of the line) and as its words were recognised (right of
 * it), drawn where they were found on the page, so the two line up. The line can be dragged (or tapped to)
 * anywhere across the page; pinch to zoom in on small print. Words the recognition wasn't sure of are in orange.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    book: LibraryBook,
    library: Library,
    onClose: () -> Unit,
    onGoBack: (() -> Unit)?,
    open: (java.io.File) -> ScanPages? = { f -> runCatching { PdfScanPages(f) }.getOrNull() },
) {
    BackHandler(onBack = onClose)
    val store = remember(book.id) { library.ocrStore(book.id) }
    val pdf = remember(book.id) { library.sourceFile(book.id) }
    val pages = remember(pdf) { pdf?.let(open) }
    DisposableEffect(pages) { onDispose { pages?.close() } }
    val count = pages?.count ?: 0
    // Start at the first page with some words on it (the cover and blank pages first are no help).
    var index by rememberSaveable(book.id) { mutableIntStateOf(-1) }
    val start by produceState(-1, book.id) {
        value = withContext(Dispatchers.IO) { (0 until count).firstOrNull { i -> (store.page(i)?.letters ?: 0) > 200 } ?: 0 }
    }
    val shown = if (index >= 0) index else start
    var split by remember { mutableFloatStateOf(0.5f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Compare with the pages")) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
                actions = {
                    if (count > 0 && shown >= 0) {
                        IconButton(onClick = { index = (shown - 1).coerceAtLeast(0) }, enabled = shown > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Previous page"))
                        }
                        Text(tr("Page {0} of {1}", shown + 1, count), style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { index = (shown + 1).coerceAtMost(count - 1) }, enabled = shown < count - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Next page"))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (count > 1 && shown >= 0) {
                        Slider(
                            value = shown.toFloat(),
                            onValueChange = { index = it.roundToInt().coerceIn(0, count - 1) },
                            valueRange = 0f..(count - 1).toFloat(),
                        )
                    }
                    Text(
                        tr("Scan on the left, recognised text on the right. Uncertain words are orange. Drag the line or tap to move it; pinch to zoom."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                        if (onGoBack != null) OutlinedButton(onClick = onGoBack) { Text(tr("Revert to scan"), color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onClose) { Text(tr("Done")) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim)) {
            when {
                pages == null -> Text(tr("The original PDF is missing."), Modifier.align(Alignment.Center).padding(24.dp))
                shown < 0 -> Unit
                else -> ComparePage(pages, shown, store.let { s -> { i: Int -> s.page(i) } }, split) { split = it }
            }
        }
    }
}

@Composable
private fun ComparePage(pages: ScanPages, index: Int, read: (Int) -> OcrPage?, split: Float, onSplit: (Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val ocr by produceState<OcrPage?>(null, index) { value = withContext(Dispatchers.IO) { read(index) } }
        // The scan, drawn sharp enough to zoom in on a little.
        val image by produceState<ImageBitmap?>(null, index, boxW, boxH) {
            value = withContext(Dispatchers.IO) {
                pages.draw(index, minOf(boxW * 2f, 3000f).roundToInt(), minOf(boxH * 2f, 3000f).roundToInt())?.asImageBitmap()
            }
        }
        var zoom by remember(index) { mutableFloatStateOf(1f) }
        var pan by remember(index) { mutableStateOf(Offset.Zero) }
        val pic = image
        val pageW = (ocr?.width?.takeIf { it > 0 } ?: pic?.width ?: 1).toFloat()
        val pageH = (ocr?.height?.takeIf { it > 0 } ?: pic?.height ?: 1).toFloat()
        val fit = minOf(boxW / pageW, boxH / pageH) * 0.96f
        val scale = fit * zoom
        val left = (boxW - pageW * scale) / 2 + pan.x
        val top = (boxH - pageH * scale) / 2 + pan.y
        val lineX = boxW * split
        val splitNow by rememberUpdatedState(split)
        val fitNow by rememberUpdatedState(fit)
        val pageNow by rememberUpdatedState(pageW to pageH)
        val orange = Color(0xFFE07A10)
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SERIF } }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(index, boxW, boxH) {
                    detectTransformGestures { centroid, move, change, _ ->
                        val (pageW, pageH) = pageNow
                        val fit = fitNow
                        val z = (zoom * change).coerceIn(1f, 6f)
                        // Zoom about the fingers, and keep some of the page on screen.
                        val k = z / zoom
                        val c = centroid - Offset(boxW / 2, boxH / 2)
                        pan = Offset((pan.x - c.x) * k + c.x + move.x, (pan.y - c.y) * k + c.y + move.y)
                        val maxX = maxOf(0f, (pageW * fit * z - boxW) / 2 + boxW * 0.3f)
                        val maxY = maxOf(0f, (pageH * fit * z - boxH) / 2 + boxH * 0.3f)
                        pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
                        zoom = z
                        if (z == 1f) pan = Offset.Zero
                    }
                }
                .pointerInput(boxW) { detectTapGestures { onSplit((it.x / boxW).coerceIn(0f, 1f)) } },
        ) {
            val pageSize = Size(pageW * scale, pageH * scale)
            // The scan, left of the line.
            clipRect(right = lineX) {
                if (pic != null) {
                    drawImage(pic, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(pageSize.width.roundToInt(), pageSize.height.roundToInt()))
                } else {
                    drawRect(Color.White, Offset(left, top), pageSize)
                }
            }
            // The words as recognised, right of it, where they were on the page.
            clipRect(left = lineX) {
                drawRect(Color.White, Offset(left, top), pageSize)
                val page = ocr
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    if (page == null || (page.blocks.isEmpty() && !page.blank)) {
                        paint.color = Color(0xFF8A8580).toArgb()
                        paint.textScaleX = 1f
                        paint.textSize = 16.dp.toPx()
                        val note = if (page == null) tr("This page hasn't been converted yet.") else tr("No words were found on this page.")
                        c.drawText(note, left + pageSize.width * 0.55f, top + pageSize.height / 2, paint)
                        return@drawIntoCanvas
                    }
                    for (line in page.lines) {
                        val size = line.size * scale * 0.72f
                        val baseline = top + (line.box.bottom - line.size * 0.2f) * scale
                        for (w in line.words) {
                            val x = left + w.box.left * scale
                            val width = w.box.width * scale
                            if (x > boxW || x + width < lineX) continue
                            val unsure = w.confidence < UNSURE
                            if (unsure) {
                                paint.color = orange.copy(alpha = 0.16f).toArgb()
                                c.drawRect(x, top + w.box.top * scale, x + width, top + w.box.bottom * scale, paint)
                            }
                            paint.color = if (unsure) orange.toArgb() else Color(0xFF1F1C18).toArgb()
                            paint.textSize = size.coerceAtLeast(1f)
                            paint.textScaleX = 1f
                            val natural = paint.measureText(w.text)
                            paint.textScaleX = if (natural > 0) (width / natural).coerceIn(0.55f, 1.6f) else 1f
                            c.drawText(w.text, x, baseline, paint)
                        }
                    }
                }
            }
            // The line between them.
            drawLine(Color(0xFFC9A961), Offset(lineX, 0f), Offset(lineX, size.height), strokeWidth = 3.dp.toPx())
        }
        // The handle on the line, to drag it.
        val handle = 44.dp
        Box(
            Modifier
                .offset { IntOffset((lineX - with(density) { handle.toPx() } / 2).roundToInt(), 0) }
                .width(handle)
                .fillMaxHeight()
                .pointerInput(boxW) {
                    detectHorizontalDragGestures { change, amount ->
                        change.consume()
                        onSplit(((boxW * splitNow + amount) / boxW).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = Color(0xFFC9A961), shadowElevation = 3.dp, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SwapHoriz, null, Modifier.size(22.dp), tint = Color(0xFF1F1C18))
                }
            }
        }
    }
}

