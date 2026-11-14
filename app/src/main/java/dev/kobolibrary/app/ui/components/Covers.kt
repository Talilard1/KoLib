package dev.kobolibrary.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import dev.kobolibrary.app.reader.ReadHere
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kobolibrary.app.kobo.CoverSize
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboCoverLoader
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.library.LibraryCovers
import kotlin.math.abs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import dev.kobolibrary.app.ui.theme.ShelfColors
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import dev.kobolibrary.app.ui.theme.LocalBookcase
import androidx.compose.ui.draw.drawWithContent

/** Book covers are about 2:3, as are the Kobo's own library thumbnails (149 × 223). */
const val COVER_ASPECT = 2f / 3f

/**
 * A cover picture with a generated stand-in while it loads or when there is none.
 * Grid covers share one 2:3 shape; a [large] cover keeps the picture's own proportions.
 */
@Composable
fun CoverImage(
    title: String,
    author: String?,
    cacheKey: Any,
    cached: () -> Bitmap?,
    load: suspend (widthPx: Int) -> Bitmap?,
    large: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(if (large) 10.dp else 6.dp)
    var bitmap by remember(cacheKey) { mutableStateOf(cached()) }
    val bmp = bitmap
    val ratio = if (large && bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else COVER_ASPECT
    BoxWithConstraints(
        modifier
            .aspectRatio(ratio)
            .clip(shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(64)
        LaunchedEffect(cacheKey) {
            if (bitmap == null) bitmap = load(widthPx)
        }
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GeneratedCover(title, author, large)
        }
        overlay()
    }
}

/** A cover from the Kobo's own picture cache. */
@Composable
fun BookCover(
    book: KoboBook,
    loader: KoboCoverLoader?,
    size: CoverSize,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    selected: Boolean? = null,
    inLibrary: Boolean = false,
) {
    CoverImage(
        title = book.title,
        author = book.author,
        cacheKey = book.imageId + size.name,
        cached = { loader?.cached(book.imageId, size) },
        load = { w -> loader?.load(book.imageId, size, w) },
        large = size == CoverSize.FULL,
        modifier = modifier,
    ) {
        if (showProgress) {
            when (book.readStatus) {
                ReadStatus.READING -> LinearProgressIndicator(
                    progress = { book.percentRead / 100f },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
                ReadStatus.FINISHED -> CornerBadge("Read", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, Alignment.TopEnd)
                ReadStatus.UNREAD -> Unit
            }
        }
        if (inLibrary) CornerBadge("In library", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, Alignment.BottomStart)
        if (selected != null) SelectionMark(selected)
    }
}

/** A cover from the library's own copy of the book. */
@Composable
fun LibraryCover(
    book: LibraryBook,
    covers: LibraryCovers,
    large: Boolean,
    modifier: Modifier = Modifier,
    onKobo: Boolean = false,
    picked: Boolean? = null,
    /** How far it's been read on the Kobo, if it's there. */
    reading: KoboBook? = null,
    /** How far it's been read on this tablet, in the app's reader. */
    readHere: ReadHere? = null,
) {
    CoverImage(
        title = book.title,
        author = book.authorLine,
        cacheKey = "${book.id}:${book.coverVersion}:$large",
        cached = { covers.cached(book, large) },
        load = { w -> covers.load(book, large, w) },
        large = large,
        modifier = modifier,
    ) {
        if (onKobo && !large) {
            when {
                book.changedSinceOnKobo -> CornerBadge("Changed", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, Alignment.TopEnd)
                reading?.readStatus == ReadStatus.FINISHED -> CornerBadge("Read", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, Alignment.TopEnd)
                else -> CornerBadge("On Kobo", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, Alignment.TopEnd)
            }
        }
        val koboBar = reading?.readStatus == ReadStatus.READING
        if (koboBar) {
            LinearProgressIndicator(
                progress = { reading!!.percentRead / 100f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        if (!large && readHere != null && readHere.started) ReadHereBadge(readHere.percent, aboveBar = koboBar)
        if (!large && readHere != null && readHere.finished) ReadHereBadge(null, aboveBar = koboBar)
        if (picked != null) SelectionMark(picked)
    }
}

/** How far the book has been read on this tablet, at the foot of its cover; or, with no [percent], that it's finished. */
@Composable
private fun BoxScope.ReadHereBadge(percent: Int?, aboveBar: Boolean) {
    // A phone's covers are smaller, so their badges are too.
    val small = phoneLayout()
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = if (small) 4.dp else 5.dp, bottom = if (aboveBar) 9.dp else if (small) 4.dp else 5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(start = if (small) 4.dp else 5.dp, end = if (small) 5.dp else 6.dp, top = 1.dp, bottom = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (percent == null) "Finished on this device" else "$percent% read on this device" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (percent == null) Icons.Filled.Check else Icons.Outlined.TabletAndroid, null, tint = Color.White, modifier = Modifier.size(if (small) 10.dp else 11.dp))
        Spacer(Modifier.width(3.dp))
        Text(if (percent == null) "Finished" else "$percent%", color = Color.White, fontSize = if (small) 9.sp else 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BoxScope.CornerBadge(text: String, background: Color, content: Color, alignment: Alignment) {
    val small = phoneLayout()
    Box(
        Modifier
            .align(alignment)
            .padding(if (small) 4.dp else 5.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = if (small) 5.dp else 6.dp, vertical = if (small) 0.5.dp else 1.dp)
    ) {
        Text(text, color = content, fontSize = if (small) 9.sp else 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BoxScope.SelectionMark(selected: Boolean) {
    if (selected) {
        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
    }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
    }
}

private val coverPalettes = listOf(
    Color(0xFF2F5D8A) to Color(0xFF1B3A5A),
    Color(0xFF8A5A2B) to Color(0xFF5A3814),
    Color(0xFF4E6543) to Color(0xFF2E4026),
    Color(0xFF6B4E8A) to Color(0xFF412E5A),
    Color(0xFF8A2F45) to Color(0xFF5A1B2A),
    Color(0xFF3F6E6B) to Color(0xFF234543),
    Color(0xFF5C5C5C) to Color(0xFF353535),
)

/** Stand-in when there is no cover picture. */
@Composable
fun GeneratedCover(title: String, author: String?, large: Boolean) {
    val (top, bottom) = coverPalettes[abs(title.hashCode()) % coverPalettes.size]
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(if (large) 20.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            fontSize = if (large) 22.sp else 13.sp,
            lineHeight = if (large) 26.sp else 15.sp,
            maxLines = if (large) 6 else 5,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (author != null) {
            Text(
                author,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                fontSize = if (large) 15.sp else 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How the Bookcase look's shelves fall in a grid: every cover's height, the number of columns, and a cell's width. */
data class ShelfMetrics(val coverHeight: Dp, val columns: Int, val cellWidthPx: Int)

/** In the Bookcase look, how the grid's shelves fall (null otherwise). */
val LocalShelf = compositionLocalOf<ShelfMetrics?> { null }

/** Height of a shelf plank and its shadow. */
val SHELF_HEIGHT = 15.dp

/**
 * A grid of covers that, in the Bookcase look, stand on shelves running edge to edge. The grid shares
 * out leftover pixels, so some cells are a pixel wider than others; every cover gets the height of the
 * narrowest cell's, which keeps the shelves straight.
 */
@Composable
fun ShelfGridBox(minCell: Dp, coverPadding: Dp, columns: Int? = null, content: @Composable () -> Unit) {
    if (!LocalBookcase.current) {
        content()
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val metrics = with(density) {
            val width = constraints.maxWidth
            val count = columns ?: (width / minCell.roundToPx()).coerceAtLeast(1)
            val cell = width / count
            ShelfMetrics(((cell - 2 * coverPadding.roundToPx()) * 3 / 2).coerceAtLeast(1).toDp(), count, cell)
        }
        CompositionLocalProvider(LocalShelf provides metrics) { content() }
    }
}

/**
 * Draws the shelf a grid cell's cover stands on, [coverTop] below the cell's top. The last book in the
 * grid carries the shelf on to the right edge, so a row of one or two books still has a whole shelf.
 * Goes before any clip, so the shelf can run past the cell.
 */
@Composable
fun Modifier.shelfUnderCover(coverTop: Dp, index: Int, count: Int): Modifier {
    val shelf = LocalShelf.current ?: return this
    val emptyCellsAfter = if (index == count - 1) (shelf.columns - 1 - index % shelf.columns).coerceAtLeast(0) else 0
    return drawWithContent {
        drawContent()
        val top = coverTop.toPx() + shelf.coverHeight.toPx()
        val width = size.width + emptyCellsAfter * shelf.cellWidthPx
        val plank = SHELF_HEIGHT.toPx() * 0.6f
        drawRect(
            Brush.verticalGradient(listOf(ShelfColors.plankTop, ShelfColors.plankBottom), startY = top, endY = top + plank),
            topLeft = Offset(0f, top),
            size = Size(width, plank),
        )
        drawRect(ShelfColors.plankEdge, topLeft = Offset(0f, top), size = Size(width, 1.5.dp.toPx()))
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), startY = top + plank, endY = top + SHELF_HEIGHT.toPx()),
            topLeft = Offset(0f, top + plank),
            size = Size(width, SHELF_HEIGHT.toPx() - plank),
        )
    }
}

/** Room for the shelf under a grid cover, in the Bookcase look. */
@Composable
fun ShelfSpace() {
    if (LocalShelf.current != null) Spacer(Modifier.height(SHELF_HEIGHT))
}

/** The grid cover's size: its cell's width, and in the Bookcase look the shared shelf height. */
@Composable
fun Modifier.gridCover(): Modifier = fillMaxWidth().then(LocalShelf.current?.let { Modifier.height(it.coverHeight) } ?: Modifier)
