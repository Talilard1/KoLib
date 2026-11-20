package dev.kolib.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt

/**
 * The book reader's top bar, laid out as the comic reader's: the way back and what's being read (the book, and the
 * chapter under it), then search, the bookmark and the AI; on a phone the rest (the reading stats, reading as a PDF,
 * editing the chapter) are in a menu, on a wider screen buttons of their own.
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
    Row(
        Modifier.fillMaxWidth().background(comicBarColour()).pointerInput(Unit) {}.statusBarsPadding().height(64.dp).padding(horizontal = 4.dp)
            .testTag("reader-top-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Close the book")) }
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
            Text(controller.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            controller.chapterTitle.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, tr("Search the book")) }
        val marked = controller.currentBookmark != null
        IconButton(onClick = { controller.toggleBookmark() }) {
            Icon(
                if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (marked) tr("Remove bookmark") else tr("Bookmark this page"),
                tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
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
            IconButton(onClick = { more = true }, modifier = Modifier.testTag("reader-more")) { Icon(Icons.Rounded.MoreVert, tr("More")) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text(tr("Reading stats")) }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = { more = false; onStats() })
                if (onPdf != null) DropdownMenuItem(text = { Text(tr("Read as PDF")) }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { more = false; onPdf() })
                if (onEdit != null) DropdownMenuItem(text = { Text(tr("Edit this chapter's text")) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { more = false; onEdit() })
            }
        }
    }
}

/**
 * The foot of the book reader, laid out as the comic reader's: the progress slider between the buttons for the
 * chapters before and after, with where it is and how far it goes either side of it, and under it the buttons for
 * the contents, autoscroll, rotation and the settings. Over the slider, what's left of the chapter and how much of
 * the book is read (while it's dragged, the page and chapter it would go to); over that, while autoscroll is on, its
 * speed, and after a move with the slider, the way back.
 */
@Composable
internal fun BookBottomBars(
    controller: ReaderController,
    onSettings: (ReaderSettings) -> Unit,
    onContents: () -> Unit,
    onLook: () -> Unit,
    onRotation: () -> Unit,
) {
    val bg = comicBarColour()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val side = if (wide) 24.dp else 8.dp
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        controller.barReturn?.let { back -> BarReturnButton(controller, back) }
        if (controller.autoscroll) AutoscrollSpeed(controller, onSettings, bg)
        // Over the slider and as wide as it, between the chapter buttons.
        Row(Modifier.fillMaxWidth().padding(horizontal = side + ChapterButton + 4.dp)) {
            SliderCaption(controller, dragging, value, bg, Modifier.weight(1f))
        }
        BookNavigator(
            controller, bg, dragging, value,
            onDrag = { dragging = true; value = it },
            onLetGo = {
                dragging = false
                controller.goToBookProgressFromBar(value)
            },
            modifier = Modifier.padding(horizontal = side),
        )
        Row(
            Modifier.fillMaxWidth().background(bg).pointerInput(Unit) {}.navigationBarsPadding().padding(horizontal = 8.dp).testTag("reader-bottom-bar"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onContents, modifier = Modifier.testTag("reader-contents")) { Icon(Icons.AutoMirrored.Rounded.List, tr("Contents and bookmarks")) }
            val on = controller.autoscroll
            IconButton(
                onClick = {
                    // Starting it puts the controls away, to read.
                    if (on) controller.stopAutoscroll() else {
                        controller.startAutoscroll()
                        controller.controlsVisible = false
                    }
                },
                modifier = Modifier.testTag("reader-autoscroll"),
            ) { Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (on) tr("Pause autoscroll") else tr("Autoscroll")) }
            IconButton(onClick = onRotation, modifier = Modifier.testTag("reader-rotation")) { Icon(controller.settings.rotation.icon(), tr("Rotation")) }
            IconButton(onClick = onLook, modifier = Modifier.testTag("reader-settings")) { Icon(Icons.Rounded.Settings, tr("Reading settings")) }
        }
    }
}

/** The chapter buttons' width. */
private val ChapterButton = 40.dp

/** Back to [back], where the reader was before moving with the progress slider (and then back again). */
@Composable
private fun BarReturnButton(controller: ReaderController, back: ReaderPosition) {
    val label = controller.barReturnBookPage?.let { tr("Back to page {0}", it) } ?: tr("Back to {0}%", (back.bookProgress * 100).roundToInt())
    FilledTonalButton(
        onClick = { controller.swapWithBarReturn() },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        modifier = Modifier.testTag("reader-bar-return"),
    ) {
        Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** While autoscroll is on (held while the controls show): its speed, slower and faster. */
@Composable
private fun AutoscrollSpeed(controller: ReaderController, onSettings: (ReaderSettings) -> Unit, bg: Color) {
    val s = controller.settings
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(bg).pointerInput(Unit) {}.padding(horizontal = 4.dp).testTag("reader-autoscroll-speed"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onSettings(s.copy(autoWpm = (s.autoWpm - 10).coerceAtLeast(ReaderSettings.MIN_WPM))) },
            enabled = s.autoWpm > ReaderSettings.MIN_WPM,
        ) { Icon(Icons.Rounded.Remove, tr("Slower")) }
        Text(tr("{0} words per minute", s.autoWpm), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(
            onClick = { onSettings(s.copy(autoWpm = (s.autoWpm + 10).coerceAtMost(ReaderSettings.MAX_WPM))) },
            enabled = s.autoWpm < ReaderSettings.MAX_WPM,
        ) { Icon(Icons.Rounded.Add, tr("Faster")) }
    }
}

/**
 * What's left of the chapter and how much of the book is read (with the chapter's pages while the book's are still
 * being counted); while the slider is dragged, the page it would go to, held still just left of the middle, and the
 * chapter that's in just right of it.
 */
@Composable
private fun SliderCaption(controller: ReaderController, dragging: Boolean, value: Float, bg: Color, modifier: Modifier) {
    val book = controller.bookPages
    // Its numbers all one width, so they don't shift about as they change.
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val dragAt = if (dragging) controller.bookPageAt(value) else null
    val box = modifier.clip(RoundedCornerShape(16.dp)).background(bg).pointerInput(Unit) {}.padding(horizontal = 14.dp, vertical = 5.dp).testTag("reader-caption")
    if (dragAt != null && book != null) {
        val total = book.total.toString()
        val shown = dragAt.toString().padStart(total.length, ' ')
        Row(box, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Page {0} of {1}", shown, total), style = style, color = color, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("  ·  ", style = style, color = color, maxLines = 1)
            Text(controller.chapterTitleAt(value), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        return
    }
    val words = if (dragging) {
        listOf(tr("Go to {0}%", (value * 100).roundToInt()), controller.chapterTitleAt(value)).filter { it.isNotBlank() }.joinToString("  ·  ")
    } else listOfNotNull(
        // Until the book's pages are counted, the chapter's: the slider's numbers are the book's share read till then.
        if (book == null && controller.chapterPages > 1) {
            tr("{0} of {1} in chapter", controller.pagesLabel(controller.firstShown + 1, controller.lastShown + 1), controller.chapterPages)
        } else null,
        if (controller.countingPages && book == null) tr("Counting pages…") else null,
        controller.minutesLeftInChapter.takeIf { it > 0 }?.let { tr("{0} min left in chapter", it) },
        if (book != null) tr("{0}% of book read", (controller.bookProgress * 100).roundToInt()) else null,
    ).joinToString(" · ")
    if (words.isEmpty()) return
    Box(box, contentAlignment = Alignment.Center) {
        Text(words, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * The progress slider through the book, between the buttons for the chapters before and after: a mark where each
 * chapter starts, and one where the way back goes to. Either side of it, the page showing and how many there are in
 * the book (once they're counted; till then, how much of the book is read).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookNavigator(
    controller: ReaderController,
    bg: Color,
    dragging: Boolean,
    value: Float,
    onDrag: (Float) -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttons = IconButtonDefaults.filledIconButtonColors(containerColor = bg, disabledContainerColor = bg, contentColor = MaterialTheme.colorScheme.onSurface)
    val book = controller.bookPages
    val progress = if (dragging) value else controller.bookProgress
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = { controller.previousChapter() }, colors = buttons,
            modifier = Modifier.size(ChapterButton).testTag("reader-previous-chapter"),
        ) { Icon(Icons.Rounded.SkipPrevious, tr("Previous chapter")) }
        Row(
            Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(24.dp)).background(bg).pointerInput(Unit) {}.padding(horizontal = 16.dp)
                .testTag("reader-navigator"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val numbers = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
            // The page showing (or two side by side), or as it's dragged the one it would go to; as wide as the most it
            // could be, so the slider doesn't shift as the number grows.
            val here = when {
                book == null -> "${((if (dragging) value else controller.bookProgress) * 100).roundToInt()}%"
                dragging -> controller.bookPageAt(value)?.toString().orEmpty()
                else -> controller.bookPage?.let { n -> controller.bookPageLast?.takeIf { it > n }?.let { "$n–$it" } ?: "$n" }.orEmpty()
            }
            val widest = when {
                book == null -> "100%"
                controller.perScreen > 1 -> "${book.total}–${book.total}"
                else -> "${book.total}"
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                Text(here, style = numbers)
                Text(widest, style = numbers, color = Color.Transparent)
            }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                // Where chapters start.
                val marks = controller.chapterMarks
                val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                Canvas(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 10.dp)) {
                    for (m in marks) {
                        val x = size.width * m
                        drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                    }
                }
                // The place the way back goes to: a line across the slider, like its own handle.
                val backAt = controller.barReturn?.bookProgress
                val backMark = MaterialTheme.colorScheme.onSurfaceVariant
                Slider(
                    value = progress,
                    onValueChange = onDrag,
                    onValueChangeFinished = onLetGo,
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
            if (book != null) Text("${book.total}", style = numbers)
        }
        FilledIconButton(
            onClick = { controller.nextChapter() }, colors = buttons,
            modifier = Modifier.size(ChapterButton).testTag("reader-next-chapter"),
        ) { Icon(Icons.Rounded.SkipNext, tr("Next chapter")) }
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
 * before. Android is asked only when the way changes: asked over and over in a moment, some tablets stop listening.
 */
@Composable
internal fun ScreenFacing(wanted: Int) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity ?: return
    androidx.compose.runtime.DisposableEffect(activity) {
        val before = activity.requestedOrientation
        onDispose { if (activity.requestedOrientation != before) activity.requestedOrientation = before }
    }
    androidx.compose.runtime.SideEffect { if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted }
}

/**
 * After a way of holding the screen is chosen ([wanted], as Android is asked for it): says so if it hasn't turned
 * that way in a moment, as the device's own settings can keep an app as it is.
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
    if ((portrait && now != android.content.res.Configuration.ORIENTATION_PORTRAIT) || (landscape && now != android.content.res.Configuration.ORIENTATION_LANDSCAPE)) {
        android.widget.Toast.makeText(context, tr("The screen didn't turn. Your device's settings may be keeping it this way round."), android.widget.Toast.LENGTH_LONG).show()
    }
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
        }
    }
}
