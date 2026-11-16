@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui.cover

import dev.kobolibrary.app.kobo.CoverScreen
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.components.GoldButton
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Image
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import dev.kobolibrary.app.log.AppLog
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A picture of the reader's own for a cover: as chosen ([full], for the cover itself) and smaller ([small], to show). */
private class OwnPicture(val full: Bitmap) {
    val small: Bitmap = run {
        val longest = maxOf(full.width, full.height)
        if (longest <= 1200) full
        else Bitmap.createScaledBitmap(full, (full.width * 1200f / longest).roundToInt().coerceAtLeast(1), (full.height * 1200f / longest).roundToInt().coerceAtLeast(1), true)
    }
}

/** What to look for online for a cover's picture, to start with. */
private fun pictureQuery(genre: CoverGenre): String = when (genre) {
    CoverGenre.FANTASY -> tr("fantasy landscape painting")
    CoverGenre.MAGIC_SCHOOL -> tr("magical castle at night painting")
    CoverGenre.DARK_MAGIC -> tr("dark fantasy art")
    CoverGenre.WITCHES -> tr("witch cottage moonlight painting")
    CoverGenre.QUEST -> tr("epic fantasy journey painting")
    CoverGenre.STORM_EPIC -> tr("epic fantasy storm painting")
    CoverGenre.MYTHOLOGY -> tr("mythology painting")
    CoverGenre.FAIRY_TALE -> tr("fairy tale illustration")
    CoverGenre.ROMANTASY -> tr("romantic fantasy art")
    CoverGenre.URBAN_FANTASY -> tr("urban fantasy city at night")
    CoverGenre.WUXIA -> tr("chinese ink landscape painting")
    CoverGenre.SCIENCE_FICTION -> tr("science fiction space painting")
    CoverGenre.SPACE_OPERA -> tr("space battle concept art")
    CoverGenre.STARSHIP -> tr("starship concept art")
    CoverGenre.CYBERPUNK -> tr("cyberpunk city art")
    CoverGenre.DYSTOPIA -> tr("post-apocalyptic city art")
    CoverGenre.STEAMPUNK -> tr("steampunk airship city art")
    CoverGenre.SUPERHERO -> tr("superhero city at night art")
    CoverGenre.MYSTERY -> tr("foggy street at night")
    CoverGenre.DETECTIVE -> tr("victorian london fog painting")
    CoverGenre.COSY_MYSTERY -> tr("english village painting")
    CoverGenre.THRILLER -> tr("city at night")
    CoverGenre.SPY -> tr("spy thriller city at night")
    CoverGenre.HISTORICAL -> tr("historical painting")
    CoverGenre.WAR -> tr("war painting")
    CoverGenre.KNIGHTS -> tr("medieval battle painting")
    CoverGenre.REGENCY -> tr("regency era painting")
    CoverGenre.ANCIENT -> tr("ancient ruins painting")
    CoverGenre.ADVENTURE -> tr("sailing ship painting")
    CoverGenre.WESTERN -> tr("wild west landscape painting")
    CoverGenre.ROMANCE -> tr("romantic sunset painting")
    CoverGenre.MODERN_ROMANCE -> tr("romantic city lights")
    CoverGenre.DARK_ROMANCE -> tr("dark red rose")
    CoverGenre.FLUFF -> tr("cosy illustration")
    CoverGenre.ANGST -> tr("rainy night street")
    CoverGenre.HORROR -> tr("haunted house at night")
    CoverGenre.VAMPIRES -> tr("gothic castle red moon")
    CoverGenre.COSMIC_HORROR -> tr("sea monster tentacles art")
    CoverGenre.HALLOWEEN -> tr("halloween pumpkins at night")
    CoverGenre.LITERARY -> tr("landscape painting")
    CoverGenre.SLICE_OF_LIFE -> tr("cosy coffee shop illustration")
    CoverGenre.SCHOOL -> tr("university campus in autumn")
    CoverGenre.SPORTS -> tr("stadium lights at night")
    CoverGenre.MUSIC -> tr("concert stage lights")
    CoverGenre.CHRISTMAS -> tr("snowy village at christmas")
    CoverGenre.NON_FICTION -> tr("landscape photograph")
    CoverGenre.CHILDREN -> tr("storybook illustration")
}

/** The genres to choose from, in their groups. */
@Composable
private fun GenrePicker(current: CoverGenre, onPick: (CoverGenre) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Genre"), style = MaterialTheme.typography.titleLarge)
                CoverGenre.Group.entries.forEach { group ->
                    Text(group.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.genres.forEach { g ->
                            FilterChip(
                                selected = g == current,
                                onClick = { onPick(g) },
                                label = { Text(g.label) },
                                leadingIcon = if (g == current) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A design for the book: for its [genre], and for a book in a series, the series' colour for the framed design. */
private fun designFor(text: CoverText, genre: CoverGenre, rnd: kotlin.random.Random = kotlin.random.Random.Default, anyColour: Boolean = true): CoverDesign {
    val d = CoverDesign.random(rnd, anyColour, genre)
    val board = CoverDesign.boardFor(text.seriesIndex)
    return if (d.style == CoverStyle.FRAMED && board != null) d.copy(colors = board) else d
}

/**
 * Makes a cover for a book: pick one of the designs (those that suit the book's [genre] first) and a
 * colour theme (a built-in one or any colour, dark or light), or let it pick by chance. The designs with
 * a picture paint one for the genre, which can be painted again, or swapped for a picture from the
 * device or found online and moved and zoomed in its frame.
 */
@Composable
fun CoverGeneratorScreen(
    text: CoverText,
    onUse: (Bitmap, CoverDesign) -> Unit,
    onCancel: () -> Unit,
    genre: CoverGenre = CoverGenre.LITERARY,
    initial: CoverDesign = remember { designFor(text, genre, anyColour = false) },
    /** The book's cover as it is now, to use as the picture on a design that has one. */
    currentCover: Bitmap? = null,
) {
    val context = LocalContext.current
    val generator = remember { CoverGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    // One preview drawn at a time, so a quick run of changes doesn't pile up.
    val drawing = remember { Dispatchers.Default.limitedParallelism(1) }
    var kind by remember { mutableStateOf(initial.genre) }
    var style by remember { mutableStateOf(initial.style) }
    var seed by remember { mutableIntStateOf(initial.seed) }
    var themeIndex by remember { mutableStateOf(CoverColors.themes.indexOfFirst { it.name == initial.colors.name }.takeIf { it >= 0 }) }
    var hue by remember { mutableStateOf(210f) }
    var strength by remember { mutableStateOf(0.7f) }
    var dark by remember { mutableStateOf(true) }
    val colors = themeIndex?.let { CoverColors.themes[it] } ?: CoverColors.fromHue(hue, strength, dark)
    // The title and author, and the series, in colours of their own, whatever the design; null: as the design has them.
    var lettering by remember { mutableStateOf<Int?>(null) }
    var seriesLettering by remember { mutableStateOf<Int?>(null) }
    // How its number in the series is written; null: as the design has it.
    var seriesNumber by remember { mutableStateOf<SeriesNumber?>(null) }
    // Its lines and ornaments in a colour of their own; null: as the design has them.
    var decoration by remember { mutableStateOf<Int?>(null) }
    val design = CoverDesign(style, colors, kind, seed, lettering, seriesLettering, seriesNumber, decoration)
    var own by remember { mutableStateOf<OwnPicture?>(null) }
    var crop by remember { mutableStateOf(CoverCrop()) }
    // While the picture is being moved, the preview is drawn smaller, to keep up.
    var moving by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var thumbs by remember { mutableStateOf<Map<CoverStyle, ImageBitmap>>(emptyMap()) }
    var making by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var findingPicture by remember { mutableStateOf(false) }
    val shownPicture = own?.takeIf { style.picture }

    fun take(load: () -> Bitmap?) {
        opening = true
        problem = null
        scope.launch {
            val picture = withContext(Dispatchers.Default) {
                runCatching { load()?.let { OwnPicture(it) } }
                    .onFailure { AppLog.w("Cover", "Couldn't open the picture for a cover", it) }
                    .getOrNull()
            }
            opening = false
            if (picture == null) {
                problem = tr("That picture couldn't be opened. Try a JPEG or PNG.")
            } else {
                own = picture
                crop = CoverCrop()
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) take { CoverRendering.decode(context, uri) } }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) take { CoverRendering.decode(context, uri) } }

    /** Moves and zooms the picture after a drag or pinch on a preview [w] × [h]: [pan] and [centroid] are in its pixels. */
    fun move(pan: Offset, zoom: Float, centroid: Offset, w: Float, h: Float) {
        val pic = own?.small ?: return
        val frame = generator.pictureFrame(style, w, h) ?: return
        crop = crop.transformed(pan.x, pan.y, zoom, centroid.x - frame.left, centroid.y - frame.top, frame.width(), frame.height(), pic.width, pic.height)
    }

    if (findingPicture) {
        PictureSearchScreen(
            firstQuery = remember(kind) { pictureQuery(kind) },
            onPick = { bytes ->
                findingPicture = false
                take { CoverRendering.decode(bytes) }
            },
            onClose = { findingPicture = false },
        )
        return
    }

    BackHandler { onCancel() }

    val coverAspect = CoverCrop.ASPECT
    LaunchedEffect(design, shownPicture, crop, moving, coverAspect) {
        // Half the Clara's width (a quarter while the picture is being moved), at the cover's own shape.
        val w = if (moving) 268 else 536
        val h = (w / coverAspect).roundToInt()
        preview = withContext(drawing) { generator.render(text, design, w, h, shownPicture?.small, crop).asImageBitmap() }
    }
    LaunchedEffect(colors, kind, seed, own, coverAspect, lettering, seriesLettering, seriesNumber, decoration) {
        thumbs = withContext(Dispatchers.Default) {
            CoverStyle.entries.associateWith {
                generator.render(text, CoverDesign(it, colors, kind, seed, lettering, seriesLettering, seriesNumber, decoration), 134, (134 / coverAspect).roundToInt(), own?.small?.takeIf { _ -> it.picture }, crop).asImageBitmap()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, tr("Cancel")) } },
                title = { Text(tr("Make a cover")) },
                actions = {
                    if (making) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                    GoldButton(
                        onClick = {
                            making = true
                            val picture = shownPicture?.full
                            val placed = crop
                            scope.launch {
                                val full = withContext(Dispatchers.Default) { generator.render(text, design, picture = picture, crop = placed) }
                                making = false
                                onUse(full, design)
                            }
                        },
                        enabled = !making,
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text(tr("Use this cover")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val previewPane: @Composable (Modifier) -> Unit = { m ->
              Column(m.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val w = min(maxWidth, maxHeight * CoverCrop.ASPECT)
                    val canMove = shownPicture != null
                    Box(
                        Modifier
                            .width(w)
                            .aspectRatio(CoverCrop.ASPECT)
                            .shadow(8.dp, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .pointerInput(canMove, style) {
                                if (!canMove) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    try {
                                        do {
                                            val event = awaitPointerEvent()
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            if (zoom != 1f || pan != Offset.Zero) {
                                                moving = true
                                                move(pan, zoom, event.calculateCentroid(useCurrent = true), size.width.toFloat(), size.height.toFloat())
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    } finally {
                                        moving = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        preview?.let { Image(it, contentDescription = tr("The cover"), modifier = Modifier.fillMaxSize()) } ?: CircularProgressIndicator()
                        if (opening) CircularProgressIndicator()
                    }
                }
                // Under the cover rather than among the designs, so picking a design never moves the others.
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    if (shownPicture != null) {
                        Text(
                            tr("Drag the picture to move it, and pinch or use the slider to zoom."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Zoom"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                            Slider(
                                value = crop.zoom,
                                onValueChange = { z ->
                                    val pic = shownPicture.small
                                    val frame = generator.pictureFrame(style, CoverCrop.OUT_WIDTH.toFloat(), CoverCrop.OUT_HEIGHT.toFloat())
                                    if (frame != null) {
                                        moving = true
                                        crop = crop.zoomedTo(z, frame.width(), frame.height(), pic.width, pic.height)
                                    }
                                },
                                onValueChangeFinished = { moving = false },
                                valueRange = CoverCrop.MIN_ZOOM..CoverCrop.MAX_ZOOM,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else if (own != null) {
                        Text(
                            tr("This design has no room for your picture. Pick one with a picture (framed picture, painting, pulp magazine or snapshot) to use it."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
              }
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = {
                        val d = designFor(text, kind)
                        style = d.style
                        seed = d.seed
                        val idx = CoverColors.themes.indexOfFirst { it == d.colors }
                        if (idx >= 0) {
                            themeIndex = idx
                        } else {
                            themeIndex = null
                            hue = kotlin.random.Random.nextFloat() * 360f
                            strength = 0.45f + kotlin.random.Random.nextFloat() * 0.55f
                            dark = kotlin.random.Random.nextBoolean()
                        }
                    }) {
                        Icon(Icons.Filled.Casino, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Surprise me"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Genre"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 16.dp))
                        var choosing by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { choosing = true }) {
                            Text(kind.label)
                            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(20.dp))
                        }
                        if (choosing) GenrePicker(kind, onPick = { kind = it; choosing = false }, onDismiss = { choosing = false })
                    }
                    HorizontalDivider()
                    Text(tr("Picture"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                seed = kotlin.random.Random.nextInt()
                                own = null
                            },
                            enabled = style.picture || style in CoverStyle.drawn,
                        ) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("New picture"))
                        }
                        if (currentCover != null) {
                            OutlinedButton(onClick = { take { currentCover } }, enabled = style.picture) {
                                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("The book's cover"))
                            }
                        }
                        OutlinedButton(
                            onClick = { runCatching { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                            enabled = style.picture,
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Photos"))
                        }
                        OutlinedButton(onClick = { runCatching { filePicker.launch(arrayOf("image/*")) } }, enabled = style.picture) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Files"))
                        }
                        OutlinedButton(onClick = { findingPicture = true }, enabled = style.picture) {
                            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Find online"))
                        }
                    }
                    HorizontalDivider()
                    val (suited, others) = CoverStyle.forGenre(kind).partition { kind in it.suits }
                    val styleTile: @Composable (CoverStyle) -> Unit = { s ->
                        val on = s == style
                        Column(
                            Modifier
                                .width(78.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    // The framed design takes the series' colour, as the books of a series often have.
                                    if (s == CoverStyle.FRAMED && s != style) CoverDesign.boardFor(text.seriesIndex)?.let { b ->
                                        CoverColors.themes.indexOf(b).takeIf { it >= 0 }?.let { themeIndex = it }
                                    }
                                    style = s
                                }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(CoverCrop.ASPECT)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(
                                        if (on) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        RoundedCornerShape(4.dp),
                                    ),
                            ) {
                                thumbs[s]?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                            }
                            Text(
                                s.label,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Text(tr("Designs for this genre"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suited.forEach { styleTile(it) }
                    }
                    if (others.isNotEmpty()) {
                        Text(tr("Other designs"), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            others.forEach { styleTile(it) }
                        }
                    }
                    HorizontalDivider()
                    Text(tr("Colour"), style = MaterialTheme.typography.titleSmall)
                    if (!style.themed) {
                        Text(
                            tr("This design takes its colours from its picture."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoverColors.themes.forEachIndexed { i, theme ->
                                Swatch(theme, selected = themeIndex == i, label = theme.name) { themeIndex = i }
                            }
                            Swatch(CoverColors.fromHue(hue, strength, dark), selected = themeIndex == null, label = tr("Your colour")) { themeIndex = null }
                        }
                        if (themeIndex == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = dark, onClick = { dark = true }, label = { Text(tr("Dark cover")) })
                                FilterChip(selected = !dark, onClick = { dark = false }, label = { Text(tr("Light cover")) })
                            }
                            Text(tr("Colour"), style = MaterialTheme.typography.labelLarge)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Brush.horizontalGradient((0..12).map { Color.hsv(it * 30f % 360f, 0.55f, 0.85f) }))
                            )
                            Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                            Text(tr("Strength"), style = MaterialTheme.typography.labelLarge)
                            Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.1f..1f)
                        }
                    }
                    // The words' colours, apart from the cover's, in any design.
                    HorizontalDivider()
                    Text(tr("Lettering"), style = MaterialTheme.typography.titleSmall)
                    // Its number in the series: in words, in figures, or none ("Book of", on the framed design).
                    val index = text.seriesIndex
                    if (!text.series.isNullOrBlank() && index != null) {
                        val framed = style == CoverStyle.FRAMED
                        val shown = seriesNumber ?: if (framed) SeriesNumber.WORDS else SeriesNumber.FIGURES
                        Text(tr("Number in the series"), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val choices = listOfNotNull(
                                SeriesNumber.WORDS.takeIf { SeriesNumber.inWords(index) != null },
                                SeriesNumber.FIGURES,
                                SeriesNumber.NONE,
                                // "A book of" is the framed design's; elsewhere it's the series' name alone, as with no number.
                                SeriesNumber.A_BOOK.takeIf { framed },
                            )
                            for (how in choices) {
                                FilterChip(
                                    selected = shown == how || (how == SeriesNumber.FIGURES && shown == SeriesNumber.WORDS && SeriesNumber.inWords(index) == null) ||
                                        (how == SeriesNumber.NONE && shown == SeriesNumber.A_BOOK && !framed),
                                    onClick = { seriesNumber = how },
                                    label = {
                                        Text(
                                            if (framed) SeriesNumber.bookOf(index, how)
                                            else SeriesNumber.written(index, how)?.let { tr("Book {0}", it) } ?: tr("No number"),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    TextColourChooser(tr("Title and author"), lettering) { lettering = it }
                    if (!text.series.isNullOrBlank()) TextColourChooser(tr("Series"), seriesLettering) { seriesLettering = it }
                    if (style in CoverStyle.decorated) TextColourChooser(tr("Lines and ornaments"), decoration) { decoration = it }
                    Text(
                        if (CoverScreen.fromKobo) tr("The cover shows the book's title, series and authors. It's made at {0} × {1} pixels, the size of your Kobo's screen.", CoverCrop.OUT_WIDTH, CoverCrop.OUT_HEIGHT)
                        else tr("The cover shows the book's title, series and authors. It's made at 1072 × 1448 pixels, the size of the Kobo Clara's screen."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    previewPane(Modifier.weight(1f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(400.dp).fillMaxHeight()) {
                        controls(Modifier.fillMaxSize())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    previewPane(Modifier.weight(1f).fillMaxWidth())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        controls(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Colours for a cover's lettering, from pale (for dark covers) to dark (for pale ones). */
private val letteringColours: List<Pair<String, Int>> = listOf(
    tr("Cream") to 0xFFF4EBD6.toInt(),
    tr("White") to 0xFFFFFFFF.toInt(),
    tr("Pale gold") to 0xFFEBD9A6.toInt(),
    tr("Gold") to 0xFFE0B458.toInt(),
    tr("Copper") to 0xFFD49A5C.toInt(),
    tr("Silver") to 0xFFC8CED6.toInt(),
    tr("Rose") to 0xFFE7A4AE.toInt(),
    tr("Scarlet") to 0xFFC8323C.toInt(),
    tr("Forest green") to 0xFF1F4035.toInt(),
    tr("Navy") to 0xFF1B2A49.toInt(),
    tr("Brown") to 0xFF3E2B20.toInt(),
    tr("Black") to 0xFF1C1C1C.toInt(),
)

/**
 * A choice of colour for some of a cover's words, apart from the cover's colours: as the design has them,
 * one of [letteringColours], or any colour, by hue and lightness.
 */
@Composable
private fun TextColourChooser(label: String, chosen: Int?, onChoose: (Int?) -> Unit) {
    var own by remember { mutableStateOf(false) }
    var hue by remember { mutableStateOf(40f) }
    var light by remember { mutableStateOf(0.7f) }
    fun ownColour() = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hue, 0.65f, light))
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WordSwatch(0, tr("As the design has it"), selected = chosen == null && !own, auto = true) { own = false; onChoose(null) }
        for ((name, colour) in letteringColours) WordSwatch(colour, name, selected = chosen == colour && !own) { own = false; onChoose(colour) }
        WordSwatch(ownColour(), tr("Your colour"), selected = own, rainbow = true) { own = true; onChoose(ownColour()) }
    }
    if (own) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient((0..12).map { Color.hsv(it * 30f % 360f, 0.55f, 0.85f) }))
        )
        Slider(value = hue, onValueChange = { hue = it; onChoose(ownColour()) }, valueRange = 0f..360f)
        Text(tr("Lightness"), style = MaterialTheme.typography.labelMedium)
        Slider(value = light, onValueChange = { light = it; onChoose(ownColour()) }, valueRange = 0.08f..0.95f)
    }
}

/**
 * A round swatch of a colour for words, named [name] for those who can't see it; [auto] (as the design has it)
 * is an "A" instead, [rainbow] (a colour of one's own) every hue.
 */
@Composable
private fun WordSwatch(colour: Int, name: String, selected: Boolean, auto: Boolean = false, rainbow: Boolean = false, onClick: () -> Unit) {
    val mark = when {
        auto -> MaterialTheme.colorScheme.onSurface
        androidx.core.graphics.ColorUtils.calculateLuminance(colour) > 0.45 -> Color(0xFF1C1C1C)
        else -> Color.White
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(
                when {
                    rainbow -> Modifier.background(Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.6f, 0.9f) }))
                    auto -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    else -> Modifier.background(Color(colour))
                }
            )
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Filled.Check, null, tint = if (rainbow) Color.White else mark, modifier = Modifier.size(16.dp))
            auto -> Text("A", style = MaterialTheme.typography.labelMedium, color = mark)
        }
    }
}

@Composable
private fun Swatch(colors: CoverColors, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                // The board's colour, with a wedge of the accent at the foot.
                .background(Brush.verticalGradient(0f to Color(colors.ground), 0.64f to Color(colors.ground), 0.64f to Color(colors.accent), 1f to Color(colors.accent)))
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color(colors.ink), modifier = Modifier.size(18.dp).offset(y = (-5).dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
