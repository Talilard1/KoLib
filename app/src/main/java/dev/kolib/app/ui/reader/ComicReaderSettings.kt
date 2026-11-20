package dev.kolib.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.comic.ComicBackground
import dev.kolib.app.comic.ComicBlend
import dev.kolib.app.comic.ComicFlash
import dev.kolib.app.comic.ComicInvert
import dev.kolib.app.comic.ComicMode
import dev.kolib.app.comic.ComicOwn
import dev.kolib.app.comic.ComicRotation
import dev.kolib.app.comic.ComicScale
import dev.kolib.app.comic.ComicSettings
import dev.kolib.app.comic.ComicTaps
import dev.kolib.app.comic.ComicZoomStart
import dev.kolib.app.tr
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The settings sheet's tabs, as Mihon's. */
internal const val SETTINGS_TAB_MODE = 0
internal const val SETTINGS_TAB_GENERAL = 1
internal const val SETTINGS_TAB_FILTER = 2

/**
 * The comic reader's settings, in Mihon's three tabs: how this series reads ("Reading mode"), how the reader looks and
 * behaves ("General") and the screen's brightness and colours ("Custom filter"). On that last tab the page isn't
 * dimmed and the bars are out of the way, so the filter can be seen as it's set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComicSettingsSheet(
    s: ComicSettings,
    /** The reading mode the comic's read in: the paged or long strip settings are shown for it. */
    mode: ComicMode,
    own: ComicOwn,
    inSeries: Boolean,
    hasCutout: Boolean,
    onOwn: (ComicOwn) -> Unit,
    onSettings: ((ComicSettings) -> ComicSettings) -> Unit,
    onAppSettings: (() -> Unit)?,
    /** The tab showing. */
    onTab: (Int) -> Unit,
    onDismiss: () -> Unit,
    startTab: Int = SETTINGS_TAB_MODE,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pager = rememberPagerState(startTab) { 3 }
    val onTabNow by rememberUpdatedState(onTab)
    LaunchedEffect(pager) { androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { onTabNow(it) } }
    val filtering = pager.currentPage == SETTINGS_TAB_FILTER
    val scope = rememberCoroutineScope()
    val most = LocalConfiguration.current.screenHeightDp.dp * 0.75f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        scrimColor = if (filtering) Color.Transparent else BottomSheetDefaults.ScrimColor,
        modifier = Modifier.testTag("comic-settings"),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = most)) {
            // The app's own settings, a step away, as the book reader has them: its theme, language and books folder.
            if (onAppSettings != null) AppSettingsLink(onAppSettings, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("comic-app-settings"))
            val tabs = listOf(tr("Reading mode"), tr("General"), tr("Custom filter"))
            PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        text = { Text(t, maxLines = 1) },
                        modifier = Modifier.testTag("comic-settings-tab-$i"),
                    )
                }
            }
            HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { p ->
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    when (p) {
                        SETTINGS_TAB_MODE -> ReadingModePage(s, mode, own, inSeries, onOwn, onSettings)
                        SETTINGS_TAB_GENERAL -> GeneralPage(s, hasCutout, onSettings)
                        else -> FilterPage(s, onSettings)
                    }
                    Spacer(Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ReadingModePage(
    s: ComicSettings,
    mode: ComicMode,
    own: ComicOwn,
    inSeries: Boolean,
    onOwn: (ComicOwn) -> Unit,
    onSettings: ((ComicSettings) -> ComicSettings) -> Unit,
) {
    Heading(if (inSeries) tr("For this series") else tr("For this comic"))
    Chips(tr("Reading mode"), listOf<ComicMode?>(null) + ComicMode.entries, own.mode, { it?.label?.invoke() ?: tr("Default") }) { onOwn(own.copy(mode = it)) }
    Chips(tr("Rotation"), listOf<ComicRotation?>(null) + ComicRotation.entries, own.rotation, { it?.label?.invoke() ?: tr("Default") }) {
        onOwn(own.copy(rotation = it))
    }
    if (mode.paged) {
        Heading(tr("Paged"))
        TapZones(s.tapsPaged, s.invertTapsPaged, { v -> onSettings { it.copy(tapsPaged = v) } }, { v -> onSettings { it.copy(invertTapsPaged = v) } })
        Chips(tr("Scale type"), ComicScale.entries, s.scale, { it.label() }) { v -> onSettings { it.copy(scale = v) } }
        Chips(tr("Zoom start position"), ComicZoomStart.entries, s.zoomStart, { it.label() }) { v -> onSettings { it.copy(zoomStart = v) } }
        Check(tr("Crop borders"), s.cropPaged) { v -> onSettings { it.copy(cropPaged = v) } }
        Check(tr("Automatically zoom into wide pages"), s.zoomWide) { v -> onSettings { it.copy(zoomWide = v) } }
        Check(tr("Pan wide pages"), s.panWide) { v -> onSettings { it.copy(panWide = v) } }
        Check(tr("Split wide pages"), s.splitWide) { v -> onSettings { it.copy(splitWide = v) } }
        if (s.splitWide) Check(tr("Invert split pages"), s.invertSplit) { v -> onSettings { it.copy(invertSplit = v) } }
        Check(tr("Rotate wide pages to fit"), s.rotateWide) { v -> onSettings { it.copy(rotateWide = v) } }
        if (s.rotateWide) Check(tr("Invert rotation"), s.invertRotate) { v -> onSettings { it.copy(invertRotate = v) } }
    } else {
        Heading(tr("Long strip"))
        TapZones(s.tapsStrip, s.invertTapsStrip, { v -> onSettings { it.copy(tapsStrip = v) } }, { v -> onSettings { it.copy(invertTapsStrip = v) } })
        Slide(tr("Side padding"), "${s.sidePadding}%", s.sidePadding, 0..ComicSettings.SIDE_PADDING_MAX) { v -> onSettings { it.copy(sidePadding = v) } }
        Check(tr("Crop borders"), s.cropStrip) { v -> onSettings { it.copy(cropStrip = v) } }
        Check(tr("Split wide pages"), s.splitWideStrip) { v -> onSettings { it.copy(splitWideStrip = v) } }
        if (s.splitWideStrip) Check(tr("Invert split pages"), s.invertSplitStrip) { v -> onSettings { it.copy(invertSplitStrip = v) } }
        Check(tr("Rotate wide pages to fit"), s.rotateWideStrip) { v -> onSettings { it.copy(rotateWideStrip = v) } }
        if (s.rotateWideStrip) Check(tr("Invert rotation"), s.invertRotateStrip) { v -> onSettings { it.copy(invertRotateStrip = v) } }
        Check(tr("Double-tap to zoom"), s.doubleTapZoom) { v -> onSettings { it.copy(doubleTapZoom = v) } }
    }
}

@Composable
private fun TapZones(taps: ComicTaps, invert: ComicInvert, onTaps: (ComicTaps) -> Unit, onInvert: (ComicInvert) -> Unit) {
    Chips(tr("Tap zones"), ComicTaps.entries, taps, { it.label() }, onTaps)
    if (taps != ComicTaps.OFF) Chips(tr("Invert tap zones"), ComicInvert.entries, invert, { it.label() }, onInvert)
}

@Composable
private fun ColumnScope.GeneralPage(
    s: ComicSettings,
    hasCutout: Boolean,
    onSettings: ((ComicSettings) -> ComicSettings) -> Unit,
) {
    Chips(tr("Background colour"), ComicBackground.entries, s.background, { it.label() }) { v -> onSettings { it.copy(background = v) } }
    Check(tr("Show page number"), s.pageNumber) { v -> onSettings { it.copy(pageNumber = v) } }
    ManyChips(tr("Show the page slider at the side in these modes"), ComicMode.entries, s.verticalIn, { it.label() }) { v -> onSettings { it.copy(verticalIn = v) } }
    if (s.verticalIn.isNotEmpty()) {
        Check(tr("On the left"), s.verticalLeft) { v -> onSettings { it.copy(verticalLeft = v) } }
        Slide(tr("Side slider height"), "${s.verticalHeight}%", s.verticalHeight, 65..100, steps = 6) { v -> onSettings { it.copy(verticalHeight = v) } }
    }
    Check(tr("Fullscreen"), s.fullscreen) { v -> onSettings { it.copy(fullscreen = v) } }
    if (hasCutout && s.fullscreen) Check(tr("Show pages round the camera"), s.cutout) { v -> onSettings { it.copy(cutout = v) } }
    Check(tr("Keep screen on"), s.keepScreenOn) { v -> onSettings { it.copy(keepScreenOn = v) } }
    Check(tr("Show actions on long tap"), s.longTap) { v -> onSettings { it.copy(longTap = v) } }
    Check(
        tr("Always show the page between comics"), s.alwaysTransition,
        note = tr("When this is off, the next comic in the series starts straight after the last page."),
    ) { v -> onSettings { it.copy(alwaysTransition = v) } }
    Check(tr("Animate page turns"), s.animate) { v -> onSettings { it.copy(animate = v) } }
    Check(tr("Flash on page change"), s.flash, note = tr("Clears ghosting on e-ink screens.")) { v -> onSettings { it.copy(flash = v) } }
    if (s.flash) {
        Slide(tr("Flash length"), tr("{0} ms", s.flashMillis), s.flashMillis / 100, 1..15) { v -> onSettings { it.copy(flashMillis = v * 100) } }
        Slide(tr("Flash after every"), if (s.flashEvery == 1) tr("1 page") else tr("{0} pages", s.flashEvery), s.flashEvery, 1..10, steps = 8) { v ->
            onSettings { it.copy(flashEvery = v) }
        }
        Chips(tr("Flash colour"), ComicFlash.entries, s.flashWith, { it.label() }) { v -> onSettings { it.copy(flashWith = v) } }
    }
    Heading(tr("Taps and keys"))
    Check(tr("Show tap zones on opening"), s.showTapZones) { v -> onSettings { it.copy(showTapZones = v) } }
    Check(tr("Volume keys turn pages"), s.volumeKeys) { v -> onSettings { it.copy(volumeKeys = v) } }
    if (s.volumeKeys) Check(tr("Invert volume keys"), s.invertVolume) { v -> onSettings { it.copy(invertVolume = v) } }
    Heading(tr("For all comics"))
    Chips(tr("Default reading mode"), ComicMode.entries, s.mode, { it.label() }) { v -> onSettings { it.copy(mode = v) } }
    Chips(tr("Default rotation"), ComicRotation.entries, s.rotation, { it.label() }) { v -> onSettings { it.copy(rotation = v) } }
}

@Composable
private fun ColumnScope.FilterPage(s: ComicSettings, onSettings: ((ComicSettings) -> ComicSettings) -> Unit) {
    Check(tr("Custom brightness"), s.customBrightness) { v -> onSettings { it.copy(customBrightness = v) } }
    if (s.customBrightness) {
        Slide(tr("Brightness"), "${s.brightness}", s.brightness, ComicSettings.BRIGHTNESS_MIN..ComicSettings.BRIGHTNESS_MAX) { v ->
            onSettings { it.copy(brightness = v) }
        }
    }
    Check(tr("Custom colour filter"), s.colourFilter) { v -> onSettings { it.copy(colourFilter = v) } }
    if (s.colourFilter) {
        val c = s.colour
        Slide(tr("Red"), "${(c shr 16) and 0xFF}", (c shr 16) and 0xFF, 0..255, tag = "comic-filter-red") { v -> onSettings { it.copy(colour = withChannel(it.colour, 16, v)) } }
        Slide(tr("Green"), "${(c shr 8) and 0xFF}", (c shr 8) and 0xFF, 0..255) { v -> onSettings { it.copy(colour = withChannel(it.colour, 8, v)) } }
        Slide(tr("Blue"), "${c and 0xFF}", c and 0xFF, 0..255) { v -> onSettings { it.copy(colour = withChannel(it.colour, 0, v)) } }
        Slide(tr("Opacity"), "${(c ushr 24) and 0xFF}", (c ushr 24) and 0xFF, 0..255, tag = "comic-filter-opacity") { v ->
            onSettings { it.copy(colour = withChannel(it.colour, 24, v)) }
        }
        Chips(tr("Blend mode"), ComicBlend.entries, s.blend, { it.label() }) { v -> onSettings { it.copy(blend = v) } }
    }
    Check(tr("Greyscale"), s.greyscale) { v -> onSettings { it.copy(greyscale = v) } }
    Check(tr("Inverted"), s.inverted) { v -> onSettings { it.copy(inverted = v) } }
}

/** [colour] with one of its channels (at [shift]: 24 alpha, 16 red, 8 green, 0 blue) set to [value]. */
internal fun withChannel(colour: Int, shift: Int, value: Int): Int = (colour and (0xFF shl shift).inv()) or ((value and 0xFF) shl shift)

// ---- the rows, laid out as Mihon's -----------------------------------------------------------------

private val Side = 24.dp

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side).padding(top = 14.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(label: String, options: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Side, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 2.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(name(o)) }) }
        }
    }
}

/** Chips any number of which can be chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ManyChips(label: String, options: List<T>, selected: Set<T>, name: (T) -> String, onPick: (Set<T>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Side, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 2.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o in selected, onClick = { onPick(if (o in selected) selected - o else selected + o) }, label = { Text(name(o)) }) }
        }
    }
}

@Composable
private fun Check(label: String, on: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = Side - 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** A setting set with a slider: its name and value above it. */
@Composable
private fun Slide(label: String, value: String, at: Int, range: IntRange, steps: Int = 0, tag: String? = null, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Side, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { Text(value, style = MaterialTheme.typography.labelLarge) }
            }
        }
        Slider(
            value = at.toFloat(),
            onValueChange = { v -> v.roundToInt().coerceIn(range).let { if (it != at) onChange(it) } },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }
}
