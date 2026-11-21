package dev.kolib.app.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.grid.GridCells
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kolib.app.tr
import android.graphics.Bitmap
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontStyle
import dev.kolib.app.ui.theme.Garamond
import dev.kolib.app.ui.theme.KoLibGold
import dev.kolib.app.ui.theme.LocalKoLibStyle
import androidx.compose.foundation.Image
import dev.kolib.app.reader.ReadHere
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
import dev.kolib.app.kobo.CoverSize
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboCoverLoader
import dev.kolib.app.kobo.ReadStatus
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.library.LibraryCovers
import kotlin.math.abs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import dev.kolib.app.ui.theme.ShelfColors
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import dev.kolib.app.ui.theme.LocalBookcase
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
    val shadow = LocalKoLibStyle.current.shadow
    BoxWithConstraints(
        modifier
            .aspectRatio(ratio)
            // A book's shadow, so the cover stands off the page as a book does.
            .shadow(if (large) 10.dp else 3.dp, shape, clip = false, ambientColor = shadow, spotColor = shadow)
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
        // On a compact shelf, the title is on the cover, along its foot (and the badges that would be there go to its top).
        val compact = !large && LocalShelfMode.current == ShelfMode.COMPACT
        if (compact) CompactCaption(title, small = maxWidth < 104.dp)
        // A small cover (as with many in a row) has room for its badges' dots, not their words; with the title on it, where
        // the badges share its top, a smaller one than that too.
        CompositionLocalProvider(LocalSmallCover provides (maxWidth < if (compact) 120.dp else 88.dp), LocalCompactCover provides compact) { overlay() }
    }
}

/** The cover's title along its foot, over a shade that darkens towards the bottom, as Mihon's compact grid has it. */
@Composable
private fun BoxScope.CompactCaption(title: String, small: Boolean) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Dark enough at the foot that a title printed on the cover doesn't show through this one.
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.92f))))
            .padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 6.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = if (small) 11.sp else 12.sp,
            lineHeight = if (small) 13.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The cover being drawn has its title along its foot: see [CoverImage]. */
private val LocalCompactCover = staticCompositionLocalOf { false }

/** The cover being drawn is small: see [CoverImage]. */
private val LocalSmallCover = staticCompositionLocalOf { false }

/**
 * How many covers a row of a shelf holds (in the library, the comics and on the Kobo), with the screen upright and
 * held sideways: 0 for as many as there's room for. Chosen in Settings, as in Mihon.
 */
class CoversPerRow(portrait: Int = 0, landscape: Int = 0) {
    var portrait by mutableIntStateOf(portrait)
    var landscape by mutableIntStateOf(landscape)
    /** The marks on covers for where a book is: on the Kobo, in the library, changed since sent. */
    var showWhere by mutableStateOf(true)
    /** The marks on covers for how far a book's been read, here and on the Kobo. */
    var showProgress by mutableStateOf(true)
    /** The row of books being read, at the top of the library's Books tab. */
    var showContinue by mutableStateOf(true)

    companion object {
        /** The most a row can be given. */
        const val MOST = 10
    }
}

val LocalCoversPerRow = compositionLocalOf { CoversPerRow() }

/** The covers in a shelf's row chosen in Settings for the way the screen is held; null for as many as fit. */
@Composable
fun chosenPerRow(): Int? {
    val chosen = LocalCoversPerRow.current
    val sideways = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    return (if (sideways) chosen.landscape else chosen.portrait).takeIf { it > 0 }
}

/**
 * The narrowest a cover on a shelf is, when as many as fit are shown: smaller on a phone, and on a phone held sideways
 * (so more than one shelf of them is in view).
 */
@Composable
fun shelfMinCell(): Dp = when {
    phoneLayout() -> 100.dp
    shortScreen() -> 112.dp
    else -> 132.dp
}

/** A shelf's columns: [count] of them, or as many at least [minCell] wide as there's room for. */
fun shelfCells(count: Int?, minCell: Dp): GridCells = if (count != null) GridCells.Fixed(count) else GridCells.Adaptive(minCell)

/**
 * The library's covers, for the books on the Kobo that are in the library too: [copyOf] finds a book's library copy.
 * Given to the Kobo's screens, so a book shows the cover it has in the library (sharper than the Kobo's own
 * thumbnail, and there before the Kobo has made one).
 */
class KoboLibraryCovers(val copyOf: (KoboBook) -> LibraryBook?, val covers: LibraryCovers)

val LocalKoboLibraryCovers = compositionLocalOf<KoboLibraryCovers?> { null }

/** A book on the Kobo's cover: the library's, when the book is in the library and has one, or else the Kobo's own. */
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
    val large = size == CoverSize.FULL
    val fromLibrary = LocalKoboLibraryCovers.current?.let { lc -> lc.copyOf(book)?.takeIf { it.hasCover }?.let { it to lc.covers } }
    CoverImage(
        title = book.title,
        author = book.author,
        cacheKey = fromLibrary?.let { (copy, _) -> "library:${copy.id}:${copy.coverVersion}:${size.name}" } ?: (book.imageId + size.name),
        cached = { fromLibrary?.let { (copy, covers) -> covers.cached(copy, large) } ?: loader?.cached(book.imageId, size) },
        load = { w -> fromLibrary?.let { (copy, covers) -> covers.load(copy, large, w) } ?: loader?.load(book.imageId, size, w) },
        large = large,
        modifier = modifier,
    ) {
        val marks = LocalCoversPerRow.current
        if (showProgress && marks.showProgress) {
            when (book.readStatus) {
                ReadStatus.READING -> LinearProgressIndicator(
                    progress = { book.percentRead / 100f },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
                ReadStatus.FINISHED -> CornerBadge(dev.kolib.app.trc("badge", "Read"), BadgeDots.read, Alignment.TopEnd)
                ReadStatus.UNREAD -> Unit
            }
        }
        if (inLibrary && marks.showWhere) CornerBadge(tr("In library"), BadgeDots.gold, Alignment.BottomStart)
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
        val marks = LocalCoversPerRow.current
        if (onKobo && !large && marks.showWhere) {
            when {
                book.changedSinceOnKobo -> CornerBadge(tr("Changed"), BadgeDots.changed, Alignment.TopEnd)
                reading?.readStatus == ReadStatus.FINISHED -> CornerBadge(dev.kolib.app.trc("badge", "Read"), BadgeDots.read, Alignment.TopEnd)
                else -> CornerBadge(tr("On Kobo"), BadgeDots.gold, Alignment.TopEnd)
            }
        }
        val koboBar = reading?.readStatus == ReadStatus.READING && marks.showProgress
        if (koboBar) {
            LinearProgressIndicator(
                progress = { reading!!.percentRead / 100f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        if (!large && readHere != null && readHere.started && marks.showProgress) ReadHereBadge(readHere.percent, aboveBar = koboBar)
        if (!large && readHere != null && readHere.finished && marks.showProgress) ReadHereBadge(null, aboveBar = koboBar)
        if (picked != null) SelectionMark(picked)
    }
}

/**
 * A comic series' cover in the Comics tab: its first comic's, with how many of its comics haven't been read yet at the
 * top left (as Mihon shows it), and whether they're all on the Kobo.
 */
@Composable
fun ComicSeriesCover(
    book: LibraryBook,
    covers: LibraryCovers,
    unread: Int,
    onKobo: Boolean,
    modifier: Modifier = Modifier,
    picked: Boolean? = null,
) {
    CoverImage(
        title = book.title,
        author = book.authorLine,
        cacheKey = "${book.id}:${book.coverVersion}:false",
        cached = { covers.cached(book, false) },
        load = { w -> covers.load(book, false, w) },
        large = false,
        modifier = modifier,
    ) {
        if (onKobo && LocalCoversPerRow.current.showWhere) CornerBadge(tr("On Kobo"), BadgeDots.gold, Alignment.TopEnd)
        if (picked != null) SelectionMark(picked)
        else if (unread > 0) {
            val small = phoneLayout()
            Text(
                if (unread > 999) "999+" else "$unread",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = if (small) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (small) 4.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = if (small) 4.dp else 5.dp, vertical = 1.dp)
                    .semantics { contentDescription = if (unread == 1) tr("1 unread") else tr("{0} unread", unread) },
            )
        }
    }
}

/** How far the book has been read on this tablet, at the foot of its cover; or, with no [percent], that it's finished. */
@Composable
private fun BoxScope.ReadHereBadge(percent: Int?, aboveBar: Boolean) {
    // A phone's covers are smaller, so their badges are too.
    val small = phoneLayout()
    val style = LocalKoLibStyle.current
    // With the title along the cover's foot, at its top instead.
    val top = LocalCompactCover.current
    val edge = if (small) 4.dp else 5.dp
    Row(
        Modifier
            .align(if (top) Alignment.TopStart else Alignment.BottomStart)
            .padding(start = edge, top = if (top) edge else 0.dp, bottom = if (top) 0.dp else if (aboveBar) 9.dp else edge)
            .clip(RoundedCornerShape(50))
            .background(style.badge)
            .padding(start = if (small) 4.dp else 5.dp, end = if (small) 5.dp else 6.dp, top = 1.dp, bottom = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (percent == null) tr("Finished on this device") else tr("{0}% read on this device", percent) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (percent == null) Icons.Filled.Check else Icons.Outlined.TabletAndroid, null,
            tint = if (percent == null) BadgeDots.read else BadgeDots.gold,
            modifier = Modifier.size(if (small) 10.dp else 11.dp),
        )
        // On a small cover, finished is the tick alone.
        if (percent != null || !LocalSmallCover.current) {
            Spacer(Modifier.width(3.dp))
            Text(if (percent == null) tr("Finished") else "$percent%", color = style.onBadge, fontSize = if (small) 9.sp else 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The dots on the badges: gold for the Kobo, green for read, red for changed. Bright, for the dark film they're on. */
private object BadgeDots {
    val gold = KoLibGold.gold
    val read = Color(0xFFB5D49C)
    val changed = Color(0xFFF59C86)
}

@Composable
private fun BoxScope.CornerBadge(text: String, dot: Color, alignment: Alignment) {
    val small = phoneLayout()
    val dotOnly = LocalSmallCover.current
    val style = LocalKoLibStyle.current
    // With the title along the cover's foot, one at the foot goes to the top.
    val at = if (alignment == Alignment.BottomStart && LocalCompactCover.current) Alignment.TopStart else alignment
    Row(
        Modifier
            .align(at)
            .padding(if (small || dotOnly) 4.dp else 5.dp)
            .clip(RoundedCornerShape(50))
            .background(style.badge)
            .then(
                if (dotOnly) Modifier.padding(4.dp).semantics { contentDescription = text }
                else Modifier.padding(start = if (small) 4.dp else 5.dp, end = if (small) 5.dp else 6.dp, top = if (small) 0.5.dp else 1.dp, bottom = if (small) 0.5.dp else 1.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (small || dotOnly) 5.dp else 6.dp).clip(CircleShape).background(dot))
        if (!dotOnly) {
            Spacer(Modifier.width(if (small) 3.dp else 4.dp))
            Text(text, color = style.onBadge, fontSize = if (small) 9.sp else 10.sp, fontWeight = FontWeight.SemiBold)
        }
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
        if (selected) Icon(Icons.Filled.Check, contentDescription = tr("Selected"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
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
    val gilt = KoLibGold.light
    // Like a cloth binding, with its title stamped in gold inside a gilt frame.
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(if (large) 10.dp else 5.dp)
            .border(if (large) 1.dp else 0.75.dp, gilt.copy(alpha = 0.55f), RoundedCornerShape(if (large) 4.dp else 2.dp))
            .padding(if (large) 12.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        Text(
            title,
            color = Color(0xFFF8E9CC),
            fontFamily = Garamond,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            fontSize = if (large) 25.sp else 14.5.sp,
            lineHeight = if (large) 28.sp else 16.sp,
            maxLines = if (large) 6 else 5,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (author != null) {
            Text(
                author,
                color = gilt.copy(alpha = 0.9f),
                fontFamily = Garamond,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                fontSize = if (large) 16.sp else 11.sp,
                lineHeight = if (large) 19.sp else 12.5.sp,
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
fun ShelfGridBox(minCell: Dp, coverPadding: Dp, columns: Int? = null, mode: ShelfMode = ShelfMode.COMFORTABLE, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth
        val count = columns ?: with(density) { (width / minCell.roundToPx()).coerceAtLeast(1) }
        val cell = width / count
        // Many covers to a row: smaller words under them, so titles aren't cut off after a letter or two.
        CompositionLocalProvider(LocalShelfMode provides mode) { SmallerWords(with(density) { cell.toDp() } < SMALL_CELL) {
            if (!LocalBookcase.current) content()
            else {
                val metrics = with(density) { ShelfMetrics(((cell - 2 * coverPadding.roundToPx()) * 3 / 2).coerceAtLeast(1).toDp(), count, cell) }
                CompositionLocalProvider(LocalShelf provides metrics) { content() }
            }
        } }
    }
}

/** A shelf's cells narrower than this have smaller titles and authors under their covers. */
private val SMALL_CELL = 104.dp

/** With [smaller], the styles of the words under covers (title, author and the like) are made smaller. */
@Composable
private fun SmallerWords(smaller: Boolean, content: @Composable () -> Unit) {
    if (!smaller) return content()
    val type = MaterialTheme.typography
    fun TextStyle.shrunk() = copy(fontSize = fontSize * 0.8f, lineHeight = if (lineHeight.isSp) lineHeight * 0.8f else lineHeight)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = type.copy(titleSmall = type.titleSmall.shrunk(), bodySmall = type.bodySmall.shrunk(), labelSmall = type.labelSmall.shrunk()),
        content = content,
    )
}

/**
 * Draws the shelf a grid cell's cover stands on, [coverTop] below the cell's top. The last book in the
 * grid carries the shelf on to the right edge, so a row of one or two books still has a whole shelf.
 * Goes before any clip, so the shelf can run past the cell.
 */
@Composable
fun Modifier.shelfUnderCover(coverTop: Dp, index: Int, count: Int): Modifier {
    val shelf = LocalShelf.current ?: return this
    val wood = LocalKoLibStyle.current.shelf ?: ShelfColors
    val emptyCellsAfter = if (index == count - 1) (shelf.columns - 1 - index % shelf.columns).coerceAtLeast(0) else 0
    return drawWithContent {
        drawContent()
        val top = coverTop.toPx() + shelf.coverHeight.toPx()
        val width = size.width + emptyCellsAfter * shelf.cellWidthPx
        val plank = SHELF_HEIGHT.toPx() * 0.6f
        drawRect(
            Brush.verticalGradient(listOf(wood.plankTop, wood.plankBottom), startY = top, endY = top + plank),
            topLeft = Offset(0f, top),
            size = Size(width, plank),
        )
        drawRect(wood.plankEdge, topLeft = Offset(0f, top), size = Size(width, 1.5.dp.toPx()))
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
