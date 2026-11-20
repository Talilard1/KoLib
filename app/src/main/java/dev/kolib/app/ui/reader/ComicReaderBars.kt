package dev.kolib.app.ui.reader

import dev.kolib.app.ui.tour.tourPlace
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kolib.app.comic.ComicMode
import dev.kolib.app.comic.ComicRotation
import dev.kolib.app.tr
import kotlin.math.roundToInt

private val BarsSlide = tween<IntOffset>(200)
private val BarsFade = tween<Float>(150)

/** The bars' colour: the theme's, a little see-through, as Mihon's are. */
@Composable
internal fun comicBarColour(): Color {
    val c = MaterialTheme.colorScheme.surfaceContainer
    return c.copy(alpha = if (c.luminance() < 0.5f) 0.9f else 0.95f)
}

/**
 * Where the reader is in the comic and how to move: what the page slider shows and moves, and the comics either
 * side in its series (null where there's none).
 */
internal class ComicNavigation(
    /** The page showing, from 0. */
    val page: Int,
    val count: Int,
    /** Read right to left: the slider runs that way too. */
    val rtl: Boolean,
    /** A page chosen on the slider (from 0), as it's dragged. */
    val onPage: (Int) -> Unit,
    val previous: (() -> Unit)?,
    val next: (() -> Unit)?,
)

/**
 * The comic reader's bars, laid out as Mihon's: along the top the way back and what's being read; at the foot the
 * page slider between the buttons for the comic before and after, and under it the buttons for the reading mode,
 * rotation, cropping and the settings. The slider can stand up the side instead.
 */
@Composable
internal fun ComicBars(
    visible: Boolean,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    nav: ComicNavigation,
    vertical: Boolean,
    verticalLeft: Boolean,
    /** How much of the height between the bars the upright slider takes (0.65 to 1). */
    verticalHeight: Float,
    modeIcon: ImageVector,
    rotationIcon: ImageVector,
    cropping: Boolean,
    onMode: () -> Unit,
    onRotation: () -> Unit,
    onCrop: () -> Unit,
    onSettings: () -> Unit,
) {
    val bg = comicBarColour()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(BarsSlide) { -it } + fadeIn(BarsFade),
            exit = slideOutVertically(BarsSlide) { -it } + fadeOut(BarsFade),
        ) {
            Row(
                Modifier.fillMaxWidth().background(bg).pointerInput(Unit) {}.statusBarsPadding().height(64.dp).padding(horizontal = 4.dp)
                    .testTag("comic-top-bar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Close the comic")) }
                Column(Modifier.weight(1f).padding(start = 4.dp, end = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (vertical) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = if (verticalLeft) Arrangement.Start else Arrangement.End) {
                AnimatedVisibility(
                    visible,
                    enter = slideInHorizontally(BarsSlide) { if (verticalLeft) -it else it } + fadeIn(BarsFade),
                    exit = slideOutHorizontally(BarsSlide) { if (verticalLeft) -it else it } + fadeOut(BarsFade),
                ) {
                    Box(Modifier.fillMaxHeight().padding(horizontal = 8.dp), contentAlignment = Alignment.BottomCenter) {
                        UprightNavigator(nav, bg, Modifier.fillMaxHeight(verticalHeight).padding(vertical = if (wide) 24.dp else 8.dp).tourPlace("comic-slider"))
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(BarsSlide) { it } + fadeIn(BarsFade),
            exit = slideOutVertically(BarsSlide) { it } + fadeOut(BarsFade),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vertical) LevelNavigator(nav, bg, Modifier.padding(horizontal = if (wide) 24.dp else 8.dp).tourPlace("comic-slider"))
                Row(
                    Modifier.fillMaxWidth().background(bg).pointerInput(Unit) {}.navigationBarsPadding().padding(horizontal = 8.dp)
                        .testTag("comic-bottom-bar"),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMode, modifier = Modifier.testTag("comic-mode-button").tourPlace("comic-mode")) { Icon(modeIcon, tr("Reading mode")) }
                    IconButton(onClick = onRotation, modifier = Modifier.testTag("comic-rotation-button").tourPlace("comic-rotation")) { Icon(rotationIcon, tr("Rotation")) }
                    IconButton(onClick = onCrop, modifier = Modifier.testTag("comic-crop-button").tourPlace("comic-crop")) { CropIcon(cropping) }
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("comic-settings-button").tourPlace("comic-settings")) { Icon(Icons.Rounded.Settings, tr("Settings")) }
                }
            }
        }
    }
}

/** The crop icon, struck through while borders are left on. */
@Composable
private fun CropIcon(cropping: Boolean) {
    Box(Modifier.size(24.dp).semantics { contentDescription = if (cropping) tr("Crop borders: on") else tr("Crop borders: off") }) {
        Icon(Icons.Rounded.Crop, null)
        if (!cropping) {
            val ink = LocalContentColor.current
            val gap = comicBarColour().copy(alpha = 1f)
            Canvas(Modifier.matchParentSize()) {
                val a = Offset(size.width * 0.12f, size.height * 0.12f)
                val b = Offset(size.width * 0.88f, size.height * 0.88f)
                drawLine(gap, a, b, strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(ink, a, b, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** The page slider lying along the foot of the screen, between the buttons for the comics before and after. */
@Composable
private fun LevelNavigator(nav: ComicNavigation, bg: Color, modifier: Modifier = Modifier) {
    val buttons = IconButtonDefaults.filledIconButtonColors(containerColor = bg, disabledContainerColor = bg, contentColor = MaterialTheme.colorScheme.onSurface)
    // The way the pages go decides which side is which, whatever the language's direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { (if (nav.rtl) nav.next else nav.previous)?.invoke() },
                enabled = (if (nav.rtl) nav.next else nav.previous) != null,
                colors = buttons,
                modifier = Modifier.testTag(if (nav.rtl) "comic-next-comic" else "comic-previous-comic"),
            ) { Icon(Icons.Rounded.SkipPrevious, if (nav.rtl) tr("Next comic") else tr("Previous comic")) }
            if (nav.count > 1) {
                CompositionLocalProvider(LocalLayoutDirection provides if (nav.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Row(
                        Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(24.dp)).background(bg).pointerInput(Unit) {}
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // As wide as the last page's number, so the slider doesn't shift as the number grows.
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Text("${nav.page + 1}", style = MaterialTheme.typography.bodyMedium)
                            Text("${nav.count}", style = MaterialTheme.typography.bodyMedium, color = Color.Transparent)
                        }
                        PageSlider(nav, upright = false, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        Text("${nav.count}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            FilledIconButton(
                onClick = { (if (nav.rtl) nav.previous else nav.next)?.invoke() },
                enabled = (if (nav.rtl) nav.previous else nav.next) != null,
                colors = buttons,
                modifier = Modifier.testTag(if (nav.rtl) "comic-previous-comic" else "comic-next-comic"),
            ) { Icon(Icons.Rounded.SkipNext, if (nav.rtl) tr("Previous comic") else tr("Next comic")) }
        }
    }
}

/** The page slider standing up the side of the screen: the first page at the top. */
@Composable
private fun UprightNavigator(nav: ComicNavigation, bg: Color, modifier: Modifier = Modifier) {
    val buttons = IconButtonDefaults.filledIconButtonColors(containerColor = bg, disabledContainerColor = bg, contentColor = MaterialTheme.colorScheme.onSurface)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = { nav.previous?.invoke() }, enabled = nav.previous != null, colors = buttons, modifier = Modifier.testTag("comic-previous-comic")) {
            Icon(Icons.Rounded.SkipPrevious, tr("Previous comic"), Modifier.rotate(90f))
        }
        if (nav.count > 1) {
            Column(
                Modifier.weight(1f).padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(bg).pointerInput(Unit) {}
                    .padding(vertical = 16.dp).width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${nav.page + 1}", style = MaterialTheme.typography.bodyMedium)
                PageSlider(nav, upright = true, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                Text("${nav.count}", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        FilledIconButton(onClick = { nav.next?.invoke() }, enabled = nav.next != null, colors = buttons, modifier = Modifier.testTag("comic-next-comic")) {
            Icon(Icons.Rounded.SkipNext, tr("Next comic"), Modifier.rotate(90f))
        }
    }
}

/**
 * The page slider: a mark for every page, as Mihon's has (close together they run into a line), the page moving as
 * it's dragged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSlider(nav: ComicNavigation, upright: Boolean, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val count = nav.count
    val onPageNow by rememberUpdatedState(nav.onPage)
    var dragging by remember { mutableStateOf(false) }
    val state = remember(count) { SliderState(value = nav.page + 1f, steps = (count - 2).coerceAtLeast(0), valueRange = 1f..count.toFloat()) }
    state.onValueChange = { v ->
        val before = state.value.roundToInt()
        state.value = v
        dragging = true
        val p = v.roundToInt()
        if (p != before) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPageNow(p - 1)
        }
    }
    state.onValueChangeFinished = {
        dragging = false
        onPageNow(state.value.roundToInt() - 1)
    }
    // Following the page turned to, unless it's being dragged.
    val shown = nav.page + 1f
    SideEffect { if (!dragging && state.value != shown) state.value = shown }
    val tagged = modifier.testTag("comic-slider")
    Slider(state, if (upright) tagged.upright() else tagged)
}

/**
 * Lays out what it's on turned a quarter clockwise (its start at the top): as long as the height it's given, and as
 * thick as it is lying down.
 */
private fun Modifier.upright(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(constraints.minHeight, constraints.maxHeight, constraints.minWidth, constraints.maxWidth))
    layout(p.height, p.width) {
        p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}

// ---- the choices from the bottom bar -----------------------------------------------------------------

/**
 * The reading modes (or rotations) to choose from for the series, as buttons, as Mihon has them: one chosen and
 * applied, or the series put back to the default. With [applyOnTap], a button applies its choice when it's tapped,
 * with nothing else to press: no Apply, and no Revert to default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ComicChoiceSheet(
    title: String,
    options: List<T>,
    /** What the series has of its own; null if it's left to the default. */
    own: T?,
    icon: (T) -> ImageVector,
    label: (T) -> String,
    /** The one chosen, or null for the default again. */
    onApply: (T?) -> Unit,
    onDismiss: () -> Unit,
    tag: String,
    /** Said under the choices. */
    note: (@Composable () -> Unit)? = null,
    applyOnTap: Boolean = false,
    /** The choice shown as chosen when the sheet opens: what the series has of its own, unless told otherwise. */
    current: T? = own,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        var chosen by remember { mutableStateOf(current) }
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag(tag)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            options.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { o -> ChoiceButton(icon(o), label(o), o == chosen, { if (applyOnTap) onApply(o) else chosen = o }, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            note?.invoke()
            if (!applyOnTap) Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (own != null) OutlinedButton(onClick = { onApply(null) }) { Text(tr("Revert to default")) }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = { onApply(chosen) }) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Apply"))
                }
            }
        }
    }
}

/** One of the choices in a sheet from the bottom bar (the comic reader's, and the book reader's rotation). */
@Composable
internal fun ChoiceButton(icon: ImageVector, label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(48.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- icons ----------------------------------------------------------------------------------------------

/** Each reading mode's icon: a phone with the way the pages go drawn on it. */
internal fun ComicMode.icon(): ImageVector = when (this) {
    ComicMode.LEFT_TO_RIGHT -> ComicIcons.pagedLtr
    ComicMode.RIGHT_TO_LEFT -> ComicIcons.pagedRtl
    ComicMode.VERTICAL -> ComicIcons.pagedDown
    ComicMode.STRIP -> ComicIcons.strip
    ComicMode.STRIP_GAPS -> ComicIcons.stripGaps
}

/** The icon for a series left to the default reading mode. */
internal val defaultModeIcon: ImageVector get() = Icons.Rounded.AppSettingsAlt

internal fun ComicRotation?.icon(): ImageVector = when (this) {
    null, ComicRotation.FREE -> Icons.Rounded.ScreenRotation
    ComicRotation.PORTRAIT, ComicRotation.REVERSE_PORTRAIT -> Icons.Rounded.StayCurrentPortrait
    ComicRotation.LANDSCAPE -> Icons.Rounded.StayCurrentLandscape
    ComicRotation.LOCKED_PORTRAIT -> Icons.Rounded.ScreenLockPortrait
    ComicRotation.LOCKED_LANDSCAPE -> Icons.Rounded.ScreenLockLandscape
}

private object ComicIcons {
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit, phone: Boolean = true): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            if (phone) {
                path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
                    moveTo(8f, 2f); lineTo(16f, 2f); quadTo(18f, 2f, 18f, 4f); lineTo(18f, 20f); quadTo(18f, 22f, 16f, 22f)
                    lineTo(8f, 22f); quadTo(6f, 22f, 6f, 20f); lineTo(6f, 4f); quadTo(6f, 2f, 8f, 2f); close()
                }
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()

    val pagedLtr = icon("ComicPagedLtr", { moveTo(9f, 12f); lineTo(15f, 12f); moveTo(12.5f, 9.5f); lineTo(15f, 12f); lineTo(12.5f, 14.5f) })
    val pagedRtl = icon("ComicPagedRtl", { moveTo(15f, 12f); lineTo(9f, 12f); moveTo(11.5f, 9.5f); lineTo(9f, 12f); lineTo(11.5f, 14.5f) })
    val pagedDown = icon("ComicPagedDown", { moveTo(12f, 8f); lineTo(12f, 16f); moveTo(9.5f, 13.5f); lineTo(12f, 16f); lineTo(14.5f, 13.5f) })

    // The strip's sides running on past the screen, the way it scrolls between them.
    val strip = icon("ComicStrip", {
        moveTo(6f, 1f); lineTo(6f, 23f); moveTo(18f, 1f); lineTo(18f, 23f)
        moveTo(12f, 8f); lineTo(12f, 16f); moveTo(9.5f, 13.5f); lineTo(12f, 16f); lineTo(14.5f, 13.5f)
    }, phone = false)
    val stripGaps = icon("ComicStripGaps", {
        for (x in listOf(6f, 18f)) {
            moveTo(x, 1f); lineTo(x, 6f); moveTo(x, 9.5f); lineTo(x, 14.5f); moveTo(x, 18f); lineTo(x, 23f)
        }
        moveTo(12f, 8f); lineTo(12f, 16f); moveTo(9.5f, 13.5f); lineTo(12f, 16f); lineTo(14.5f, 13.5f)
    }, phone = false)
}
