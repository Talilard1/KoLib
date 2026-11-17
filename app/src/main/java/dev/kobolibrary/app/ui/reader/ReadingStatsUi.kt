package dev.kobolibrary.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kobolibrary.app.KoboLibraryApp
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.BookReading
import dev.kobolibrary.app.reader.ReaderController
import dev.kobolibrary.app.reader.ReadingLog
import dev.kobolibrary.app.reader.ReadingSession
import dev.kobolibrary.app.tr
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** How long, in the shortest words that say it: "45 min" under an hour, "2.5 h" over. */
internal fun timeSpan(seconds: Double): String {
    val minutes = seconds / 60.0
    return when {
        minutes < 1.0 -> tr("{0} min", if (seconds <= 0.0) "0" else "<1")
        minutes < 60.0 -> tr("{0} min", minutes.roundToInt().toString())
        else -> tr("{0} h", String.format(Locale.getDefault(), "%.1f", minutes / 60.0))
    }
}

/** The app's log of reading, for a reader to count into; null where there's no app (a preview, some tests). */
@Composable
internal fun rememberReadingSession(book: LibraryBook): ReadingSession? {
    val app = LocalContext.current.applicationContext as? KoboLibraryApp ?: return null
    return remember(book.id) { ReadingSession(app.readingLog, book.id, book.title) }
}

/**
 * Counts the time a book is on screen as reading ([ReadingSession]): from when it's shown until it's left. A reader
 * that goes by whole pages gives the [page] it's on, and each one turned to is counted.
 */
@Composable
internal fun CountReading(session: ReadingSession?, page: Int? = null) {
    if (session == null) return
    val owner = LocalLifecycleOwner.current
    DisposableEffect(session, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.resume()
                Lifecycle.Event.ON_PAUSE -> session.pause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.resume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            session.pause()
        }
    }
    if (page != null) {
        var last by remember(session) { mutableIntStateOf(page) }
        LaunchedEffect(page) {
            if (page != last) {
                // A page or two on (or back) is a page read; further is a jump.
                session.moved(pages = if (kotlin.math.abs(page - last) <= 2) 1 else 0)
                last = page
            }
        }
    }
}

/** A figure with what it's of under it, as on the Kobo's own reading stats. */
@Composable
internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A heading over a group of figures. */
@Composable
internal fun StatHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 4.dp),
    )
}

/** The reading of the last [days] days as bars, a day each, today last: the taller, the longer was read. */
@Composable
internal fun DaysChart(perDay: Map<String, Int>, days: Int = 14, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    val seconds = (days - 1 downTo 0).map { perDay[today.minusDays(it.toLong()).toString()] ?: 0 }
    val most = (seconds.maxOrNull() ?: 0).coerceAtLeast(60)
    val bar = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        val gap = size.width / days * 0.28f
        val wide = size.width / days - gap
        seconds.forEachIndexed { i, s ->
            val x = i * (wide + gap) + gap / 2
            val tall = if (s <= 0) 0f else (size.height * s / most).coerceAtLeast(3.dp.toPx())
            drawRoundRect(rest, Offset(x, 0f), Size(wide, size.height), CornerRadius(wide / 4))
            if (tall > 0f) drawRoundRect(bar, Offset(x, size.height - tall), Size(wide, tall), CornerRadius(wide / 4))
        }
    }
}

/**
 * The reading stats of the book that's open, in the reader's side panel: how much is left of the chapter, of the next
 * and of the book, at the speed it's being read; and how long it's been read for so far.
 */
@Composable
internal fun ReadingStatsPanel(controller: ReaderController, onClose: () -> Unit, header: @Composable (String, String?, () -> Unit) -> Unit) {
    val session = controller.session
    val all by (session?.log?.books ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, BookReading>()) }).collectAsStateWithLifecycle()
    val read: BookReading? = session?.let { all[it.bookId] }
    val speed = controller.wordsPerMinute
    Column(Modifier.verticalScroll(rememberScrollState())) {
        header(tr("Reading stats"), controller.bookTitle, onClose)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val inChapter = controller.wordsLeftInChapter
            val inBook = controller.wordsLeftInBook
            val next = controller.wordsInNextChapter
            StatHeading(tr("This chapter"))
            controller.chapterTitle.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(tr("{0}%", (controller.chapterProgress * 100).roundToInt()), tr("Read"), Modifier.weight(1f).fillMaxHeight())
                StatTile(inChapter?.let { timeSpan(it / speed * 60.0) } ?: "…", tr("Left to read"), Modifier.weight(1f).fillMaxHeight())
            }
            if (next != null && next > 0) {
                StatHeading(tr("Next chapter"))
                controller.nextChapterTitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatTile(timeSpan(next / speed * 60.0), tr("To read it"), Modifier.fillMaxWidth())
            }
            StatHeading(tr("This book"))
            LinearProgressIndicator(
                progress = { controller.bookProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(tr("{0}%", (controller.bookProgress * 100).roundToInt()), tr("Read"), Modifier.weight(1f).fillMaxHeight())
                StatTile(inBook?.let { timeSpan(it / speed * 60.0) } ?: "…", tr("Left to read"), Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(timeSpan((read?.seconds ?: 0L).toDouble()), tr("Spent reading it"), Modifier.weight(1f).fillMaxHeight())
                StatTile(read?.minutesPerSession?.let { tr("{0} min", it.toString()) } ?: "–", tr("A sitting, on average"), Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(read?.pagesPerMinute?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–", tr("Pages a minute"), Modifier.weight(1f).fillMaxHeight())
                StatTile(speed.roundToInt().toString(), tr("Words a minute"), Modifier.weight(1f).fillMaxHeight())
            }
            if (read?.wordsPerMinute == null) {
                Text(
                    tr("The time left is worked out from how fast you read. Until you've read a few pages of this book, it goes by your speed in other books, or an ordinary one."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (read != null && read.days.isNotEmpty()) {
                StatHeading(tr("The last two weeks"))
                DaysChart(read.days)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A sample of [ReadingLog] for pictures of the screens in tests. */
internal fun sampleReadingLog(file: java.io.File, ids: List<String>, today: LocalDate = LocalDate.now()): ReadingLog {
    val log = ReadingLog(file) { it.run() }
    val zone = java.time.ZoneId.systemDefault()
    ids.forEachIndexed { n, id ->
        for (back in 0 until 12) {
            if ((back + n) % 3 == 2) continue
            val at = today.minusDays(back.toLong()).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
            val seconds = 900 + ((back * 7 + n * 5) % 9) * 420
            log.record(id, "", seconds, seconds / 28, seconds * 4, session = true, now = at)
        }
    }
    return log
}
