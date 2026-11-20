package dev.kolib.app.ui.reader

import dev.kolib.app.ui.tour.tourPlace
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderPosition
import dev.kolib.app.reader.ReaderRotation
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.tr
import dev.kolib.app.ui.components.FilledTonalButton
import dev.kolib.app.ui.components.phoneLayout
import kotlin.math.roundToInt

/**
 * The book reader's top bar: the way back and what's being read (the book, and the chapter under it), then search, the
 * bookmark and the AI; on a phone the rest (the reading stats, reading as a PDF, editing the chapter) are in a menu, on
 * a wider screen buttons of their own. The contents and the settings are at the foot.
 */
@Composable
internal fun BookTopBar(
    controller: ReaderController,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit,
    onEdit: (() -> Unit)?,
    onPdf: (() -> Unit)? = null,
    onCompanion: (() -> Unit)? = null,
    aiPlace: dev.kolib.app.ui.ai.ChatPlace? = null,
) {
    // A phone's width hasn't room for every button: the ones used least go in a menu there.
    val compact = LocalConfiguration.current.screenWidthDp < 600
    var more by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("reader-top-bar")) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Close the book")) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(controller.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                controller.chapterTitle.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.tourPlace("reader-search-mark")) {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, tr("Search the book")) }
                val marked = controller.currentBookmark != null
                IconButton(onClick = { controller.toggleBookmark() }) {
                    Icon(
                        if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (marked) tr("Remove bookmark") else tr("Bookmark this page"),
                        tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // Out of sight while the AI is out as its bubble, which stands in for it.
            if (onCompanion != null && dev.kolib.app.ui.ai.aiButtonShown()) IconButton(onClick = onCompanion, modifier = Modifier.testTag("reader-companion")) {
                dev.kolib.app.ui.ai.AiButtonIcon(tr("Summary and questions"), aiPlace)
            }
            if (!compact) {
                IconButton(onClick = onStats) { Icon(Icons.Outlined.BarChart, tr("Reading stats")) }
                if (onPdf != null) IconButton(onClick = onPdf) { Icon(Icons.Outlined.PictureAsPdf, tr("Read as PDF")) }
                if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.EditNote, tr("Edit this chapter's text")) }
            } else Box {
                IconButton(onClick = { more = true }, modifier = Modifier.testTag("reader-more")) { Icon(Icons.Filled.MoreVert, tr("More")) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(tr("Reading stats")) }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = { more = false; onStats() })
                    if (onPdf != null) DropdownMenuItem(text = { Text(tr("Read as PDF")) }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { more = false; onPdf() })
                    if (onEdit != null) DropdownMenuItem(text = { Text(tr("Edit this chapter's text")) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { more = false; onEdit() })
                }
            }
        }
    }
}

/**
 * The foot of the book reader, kept short: autoscroll's speed while it's running, the progress bar between the buttons
 * for the chapters before and after, where the reader is in the book, and along the bottom the buttons for the contents,
 * autoscroll, rotation and the settings (and after a move with the progress bar, the way back among them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookBottomBars(
    controller: ReaderController,
    onSettings: (ReaderSettings) -> Unit,
    onContents: () -> Unit,
    onLook: () -> Unit,
    onRotation: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0f) }
    val progress = if (dragging) value else controller.bookProgress
    val book = controller.bookPages
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("reader-bottom-bar")) {
        Column(Modifier.navigationBarsPadding().padding(start = 8.dp, end = 8.dp, top = 2.dp)) {
            // Autoscroll's speed, while it's running (it's in the settings too).
            if (controller.autoscroll) AutoscrollSpeedRow(controller, onSettings)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.tourPlace("reader-slider")) {
                IconButton(onClick = { controller.previousChapter() }, modifier = Modifier.testTag("reader-previous-chapter")) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, tr("Previous chapter"))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Where chapters start.
                    val marks = controller.chapterMarks
                    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 10.dp)) {
                        for (m in marks) {
                            val x = size.width * m
                            drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                    // The place the way back goes to: a line across the bar, like the bar's own handle.
                    val backAt = controller.barReturn?.bookProgress
                    val backMark = MaterialTheme.colorScheme.onSurfaceVariant
                    Slider(
                        value = progress,
                        onValueChange = { dragging = true; value = it },
                        onValueChangeFinished = {
                            dragging = false
                            controller.goToBookProgressFromBar(value)
                        },
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                modifier = if (backAt == null) Modifier else Modifier.drawWithContent {
                                    drawContent()
                                    val x = size.width * backAt.coerceIn(0f, 1f)
                                    val half = 13.dp.toPx()
                                    drawLine(backMark, Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("reader-slider"),
                    )
                }
                IconButton(onClick = { controller.nextChapter() }, modifier = Modifier.testTag("reader-next-chapter")) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, tr("Next chapter"))
                }
            }
            // Pages are counted through the whole book, at the text size and page size being read at. Its numbers are
            // all one width, so they don't shift about as they change.
            val footStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            val footColor = MaterialTheme.colorScheme.onSurfaceVariant
            val dragAt = if (dragging) controller.bookPageAt(value) else null
            if (dragAt != null && book != null) {
                // While dragging: the page number held still, just left of the middle, and the chapter it's in just
                // right of it, however long the chapter's name.
                val total = book.total.toString()
                val shown = dragAt.toString().padStart(total.length, '\u2007')
                Row(Modifier.fillMaxWidth().testTag("reader-caption"), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Page {0} of {1}", shown, total), style = footStyle, color = footColor, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("  ·  ", style = footStyle, color = footColor, maxLines = 1)
                    Text(controller.chapterTitleAt(value), style = footStyle, color = footColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            } else {
                val details = if (dragging) tr("Go to {0}%", (value * 100).roundToInt()) else listOfNotNull(
                    controller.chapterTitle.ifBlank { null },
                    controller.bookPage?.let { tr("{0} of {1}", controller.pagesLabel(it, controller.bookPageLast), book?.total) }
                        ?: if (controller.chapterPages > 1) tr("{0} of {1} in chapter", controller.pagesLabel(controller.firstShown + 1, controller.lastShown + 1), controller.chapterPages) else null,
                    // The book's page numbers show once every chapter is counted, so they never change.
                    if (controller.countingPages && book == null) tr("Counting pages…") else null,
                    controller.minutesLeftInChapter.takeIf { it > 0 }?.let { tr("{0} min left in chapter", it) },
                    tr("{0}% of book read", (controller.bookProgress * 100).roundToInt()),
                ).joinToString(" · ")
                Text(
                    details, style = footStyle, color = footColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("reader-caption"),
                )
            }
            // The buttons, and after a move with the progress bar, the way back among them.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onContents, modifier = Modifier.testTag("reader-contents").tourPlace("reader-contents")) { Icon(Icons.AutoMirrored.Filled.List, tr("Contents and bookmarks")) }
                val on = controller.autoscroll
                IconButton(
                    onClick = {
                        // Starting it puts the controls away, to read.
                        if (on) controller.stopAutoscroll() else {
                            controller.startAutoscroll()
                            controller.controlsVisible = false
                        }
                    },
                    modifier = Modifier.testTag("reader-autoscroll").tourPlace("reader-autoscroll"),
                ) { Icon(if (on) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (on) tr("Pause autoscroll") else tr("Autoscroll")) }
                controller.barReturn?.let { back -> BarReturnButton(controller, back, compact = phoneLayout()) }
                IconButton(onClick = onRotation, modifier = Modifier.testTag("reader-rotation").tourPlace("reader-rotation")) { Icon(controller.settings.rotation.icon(), tr("Rotation")) }
                IconButton(onClick = onLook, modifier = Modifier.testTag("reader-settings").tourPlace("reader-settings")) { Icon(Icons.Outlined.Settings, tr("Reading settings")) }
            }
        }
    }
}

/**
 * Back to [back], where the reader was before moving with the progress bar (and then back again). On a phone, where
 * the row is narrow, just its icon.
 */
@Composable
private fun BarReturnButton(controller: ReaderController, back: ReaderPosition, compact: Boolean, modifier: Modifier = Modifier) {
    val label = controller.barReturnBookPage?.let { tr("Back to page {0}", it) } ?: tr("Back to {0}%", (back.bookProgress * 100).roundToInt())
    if (compact) {
        FilledTonalIconButton(onClick = { controller.swapWithBarReturn() }, modifier = modifier.testTag("reader-bar-return")) {
            Icon(Icons.AutoMirrored.Filled.Undo, label, Modifier.size(20.dp))
        }
    } else {
        FilledTonalButton(
            onClick = { controller.swapWithBarReturn() },
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            modifier = modifier.testTag("reader-bar-return"),
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Autoscroll's speed, slower and faster, in the middle. */
@Composable
private fun AutoscrollSpeedRow(controller: ReaderController, onSettings: (ReaderSettings) -> Unit) {
    val s = controller.settings
    // A phone hasn't room for the speed on one line: the number goes over its unit, without the label.
    val phone = phoneLayout()
    Row(
        Modifier.fillMaxWidth().testTag("reader-autoscroll-speed"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!phone) Text(tr("Speed"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(
            onClick = { onSettings(s.copy(autoWpm = (s.autoWpm - 10).coerceAtLeast(ReaderSettings.MIN_WPM))) },
            enabled = s.autoWpm > ReaderSettings.MIN_WPM,
        ) { Icon(Icons.Outlined.Remove, tr("Slower")) }
        if (phone) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.autoWpm}", style = MaterialTheme.typography.titleSmall)
                Text(tr("words per minute"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(tr("{0} words per minute", s.autoWpm), style = MaterialTheme.typography.labelLarge)
        }
        IconButton(
            onClick = { onSettings(s.copy(autoWpm = (s.autoWpm + 10).coerceAtMost(ReaderSettings.MAX_WPM))) },
            enabled = s.autoWpm < ReaderSettings.MAX_WPM,
        ) { Icon(Icons.Outlined.Add, tr("Faster")) }
    }
}

/** What Android is asked for, for each way of holding the screen. */
internal val ReaderRotation.orientation: Int get() = when (this) {
    ReaderRotation.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ReaderRotation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    ReaderRotation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

/**
 * While a reader is open, the screen faces as [wanted] (as Android is asked for it), and when it closes, as it did
 * before (on the reader's own screen, which closes with it, there's nothing to put back, nor as it's made anew when
 * it turns). Android is asked only when the way changes: asked over and over in a moment, some tablets stop listening.
 */
@Composable
internal fun ScreenFacing(wanted: Int) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity ?: return
    androidx.compose.runtime.DisposableEffect(activity) {
        val before = activity.requestedOrientation
        onDispose {
            if (activity is dev.kolib.app.ReaderActivity || activity.isFinishing || activity.isChangingConfigurations) return@onDispose
            if (activity.requestedOrientation != before) activity.requestedOrientation = before
        }
    }
    androidx.compose.runtime.SideEffect {
        if (activity.requestedOrientation != wanted) {
            dev.kolib.app.log.AppLog.i("Rotation", "Reader asks for ${dev.kolib.app.orientationName(wanted)} (was ${dev.kolib.app.orientationName(activity.requestedOrientation)}).")
            activity.requestedOrientation = wanted
        }
    }
}

/**
 * After a way of holding the screen is chosen ([wanted], as Android is asked for it): says so if it hasn't turned
 * that way in a moment, as a tablet with Android 16 does unless the app's aspect ratio is App default in its settings.
 */
internal suspend fun sayIfNotTurned(context: android.content.Context, wanted: Int) {
    kotlinx.coroutines.delay(1_800)
    val portrait = wanted in setOf(
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
    )
    val landscape = wanted in setOf(
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
    )
    val now = context.resources.configuration.orientation
    val turned = !((portrait && now != android.content.res.Configuration.ORIENTATION_PORTRAIT) || (landscape && now != android.content.res.Configuration.ORIENTATION_LANDSCAPE))
    dev.kolib.app.log.AppLog.i(
        "Rotation",
        "Asked for ${dev.kolib.app.orientationName(wanted)}: the screen is ${if (now == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"} " +
            "(display turned ${(context.getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation ?: 0) * 90}°).",
    )
    RotationRefused.set(context, !turned)
    if (!turned) android.widget.Toast.makeText(context, tr("Android didn't turn the screen. Rotation has a tip for this."), android.widget.Toast.LENGTH_LONG).show()
}

/** Each way of holding the screen's icon. */
internal fun ReaderRotation.icon(): ImageVector = when (this) {
    ReaderRotation.AUTO -> Icons.Rounded.ScreenRotation
    ReaderRotation.PORTRAIT -> Icons.Rounded.ScreenLockPortrait
    ReaderRotation.LANDSCAPE -> Icons.Rounded.ScreenLockLandscape
}

/** Which way the screen faces while reading, chosen from the rotation button: it changes at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookRotationSheet(current: ReaderRotation, onPick: (ReaderRotation) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("reader-rotation-sheet")) {
            Text(tr("Rotation"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderRotation.entries.forEach { r ->
                    ChoiceButton(r.icon(), r.label, r == current, { onPick(r) }, Modifier.weight(1f).testTag("reader-rotation-${r.name}"))
                }
            }
            Text(
                current.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            RotationTip(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        }
    }
}

/**
 * Whether Android didn't turn the screen the last time a reader asked it to. On a tablet with Android 16 it turns only
 * for an app whose aspect ratio is App default in Android's settings, as it does for Mihon's reader (see [RotationTip]).
 */
internal object RotationRefused {
    private fun prefs(context: android.content.Context) = context.getSharedPreferences("reader", android.content.Context.MODE_PRIVATE)
    fun get(context: android.content.Context): Boolean = prefs(context).getBoolean("rotationRefused", false)
    fun set(context: android.content.Context, refused: Boolean) {
        if (get(context) != refused) prefs(context).edit().putBoolean("rotationRefused", refused).apply()
    }
}

/** Why the screen didn't turn, and what lets it: with the rotation choices, once Android hasn't turned it. */
@Composable
internal fun RotationTip(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!remember { RotationRefused.get(context) }) return
    Text(
        tr("Android didn't turn the screen last time. On a tablet with Android 16, it does only once KoLib's aspect ratio is set to App default in Android's settings (search them for “aspect ratio”)."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = modifier.testTag("rotation-tip"),
    )
}
