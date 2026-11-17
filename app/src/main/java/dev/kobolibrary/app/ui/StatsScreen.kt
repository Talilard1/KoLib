package dev.kobolibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.kobo.KoboSnapshot
import dev.kobolibrary.app.kobo.ReadStatus
import dev.kobolibrary.app.library.LibraryBook
import dev.kobolibrary.app.reader.BookReading
import dev.kobolibrary.app.reader.ReadHere
import dev.kobolibrary.app.reader.ReadingLog
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.reader.DaysChart
import dev.kobolibrary.app.ui.reader.StatHeading
import dev.kobolibrary.app.ui.reader.StatTile
import dev.kobolibrary.app.ui.reader.timeSpan
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** A book in a list of the stats: its name, how far through it is, and what's known of the time on it. */
private class StatRow(val title: String, val progress: Float?, val detail: String)

/** The figures of all the reading: what's been read in KoLib's own readers, and what the Kobo says of the reading done on it. */
internal class ReadingTotals(books: List<LibraryBook>, here: Map<String, ReadHere>, log: Map<String, BookReading>, today: LocalDate = LocalDate.now()) {
    val total: BookReading = log.values.fold(BookReading()) { sum, b -> sum + b }
    val inLibrary: Int = books.size
    val finished: Int = books.count { here[it.id]?.let { h -> h.finished || h.progress >= 0.995f } == true }
    val finishedShare: Int = if (inLibrary == 0) 0 else (finished * 100f / inLibrary).roundToInt()
    val speed: Float = total.wordsPerMinute ?: ReadingLog.ORDINARY

    /** Seconds read over the last two weeks. */
    val lastTwoWeeks: Int = (0 until 14).sumOf { total.days[today.minusDays(it.toLong()).toString()] ?: 0 }

    /** The books being read, the last read first: each with how long it's been read for and how long is left. */
    val reading: List<Triple<LibraryBook, ReadHere, BookReading?>> = books
        .mapNotNull { b -> here[b.id]?.takeIf { it.started && it.progress < 0.995f }?.let { Triple(b, it, log[b.id]) } }
        .sortedByDescending { it.second.at }

    /** The books read for longest, with their names (as they were, for those no longer in the library). */
    val mostRead: List<Pair<String, BookReading>> = log.entries
        .filter { it.value.seconds >= 60 }
        .sortedByDescending { it.value.seconds }
        .map { (id, r) -> (books.firstOrNull { it.id == id }?.title ?: r.title.ifBlank { tr("Removed book") }) to r }

    /** How long is left of [book] at [progress], at the speed it (or everything) has been read. */
    fun secondsLeft(book: LibraryBook, progress: Float, read: BookReading?): Double? =
        book.words?.takeIf { it > 0 }?.let { it * (1f - progress) / (read?.wordsPerMinute ?: speed) * 60.0 }
}

/** The reading stats: the reading done in KoLib, and under it the Kobo's own figures, from its database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(
    books: List<LibraryBook>,
    readingHere: Map<String, ReadHere>,
    log: Map<String, BookReading>,
    /** What's on the Kobo, as it is now or as it was last seen; null if there's never been one. */
    kobo: KoboSnapshot?,
    /** The Kobo is connected now. */
    koboLive: Boolean,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val totals = remember(books, readingHere, log, today) { ReadingTotals(books, readingHere, log, today) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(tr("Reading stats")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatHeading(tr("Read in KoLib"))
                    val t = totals.total
                    if (t.seconds < 60) {
                        Text(
                            tr("Nothing read yet. Reading time is tracked from when you open a book in KoLib."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(timeSpan(t.seconds.toDouble()), tr("Spent reading"), Modifier.weight(1f).fillMaxHeight())
                        StatTile(totals.finished.toString(), tr("Books finished"), Modifier.weight(1f).fillMaxHeight())
                        StatTile(tr("{0}%", totals.finishedShare), tr("Of your library finished"), Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(t.minutesPerSession?.let { tr("{0} min", it.toString()) } ?: "–", tr("Average session"), Modifier.weight(1f).fillMaxHeight())
                        StatTile(t.pagesPerMinute?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–", tr("Pages per minute"), Modifier.weight(1f).fillMaxHeight())
                        StatTile(t.wordsPerMinute?.roundToInt()?.toString() ?: "–", tr("Words per minute"), Modifier.weight(1f).fillMaxHeight())
                    }
                    if (t.days.isNotEmpty()) {
                        StatHeading(tr("Last 14 days"))
                        DaysChart(t.days, today = today)
                        Text(tr("{0} read in the last 14 days.", timeSpan(totals.lastTwoWeeks.toDouble())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (totals.reading.isNotEmpty()) {
                        StatHeading(tr("Reading now"))
                        for ((book, here, read) in totals.reading.take(4)) {
                            val left = totals.secondsLeft(book, here.progress, read)
                            BookStat(StatRow(
                                book.title, here.progress,
                                listOfNotNull(
                                    tr("{0}% read", here.percent),
                                    read?.takeIf { it.seconds >= 60 }?.let { tr("{0} spent reading", timeSpan(it.seconds.toDouble())) },
                                    left?.let { tr("about {0} left", timeSpan(it)) },
                                ).joinToString(" · "),
                            ))
                        }
                    }
                    if (totals.mostRead.isNotEmpty()) {
                        StatHeading(tr("Most read"))
                        for ((title, read) in totals.mostRead.take(5)) {
                            BookStat(StatRow(
                                title, null,
                                listOfNotNull(
                                    timeSpan(read.seconds.toDouble()),
                                    read.sessions.takeIf { it > 0 }?.let { if (it == 1) tr("1 session") else tr("{0} sessions", it) },
                                ).joinToString(" · "),
                            ))
                        }
                    }
                }
            }
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatHeading(tr("Read on your Kobo"))
                    if (kobo == null) {
                        Text(tr("Connect your Kobo to see its reading stats."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val onIt = kobo.books
                        val done = onIt.count { it.readStatus == ReadStatus.FINISHED }
                        val seconds = onIt.sumOf { (it.secondsRead ?: 0).toLong() }
                        if (!koboLive) Text(tr("From when the Kobo was last connected."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(if (seconds > 0) timeSpan(seconds.toDouble()) else "–", tr("Spent reading"), Modifier.weight(1f).fillMaxHeight())
                            StatTile(done.toString(), tr("Books finished"), Modifier.weight(1f).fillMaxHeight())
                            StatTile(tr("{0}%", if (onIt.isEmpty()) 0 else (done * 100f / onIt.size).roundToInt()), tr("Of its books finished"), Modifier.weight(1f).fillMaxHeight())
                        }
                        if (seconds == 0L) {
                            Text(tr("This Kobo's database has no reading times."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val reading = onIt.filter { it.readStatus == ReadStatus.READING }.sortedByDescending { it.lastReadMillis ?: 0L }
                        if (reading.isNotEmpty()) {
                            StatHeading(tr("Reading now"))
                            for (b in reading.take(5)) {
                                BookStat(StatRow(
                                    b.title, b.percentRead / 100f,
                                    listOfNotNull(
                                        tr("{0}% read", b.percentRead),
                                        b.secondsRead?.let { tr("{0} spent reading", timeSpan(it.toDouble())) },
                                        b.secondsLeft?.let { tr("about {0} left", timeSpan(it.toDouble())) },
                                    ).joinToString(" · "),
                                ))
                            }
                        }
                        val longest = onIt.filter { (it.secondsRead ?: 0) >= 60 }.sortedByDescending { it.secondsRead ?: 0 }
                        if (longest.isNotEmpty()) {
                            StatHeading(tr("Most read"))
                            for (b in longest.take(5)) BookStat(StatRow(b.title, null, timeSpan((b.secondsRead ?: 0).toDouble())))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookStat(row: StatRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        row.progress?.let {
            LinearProgressIndicator(
                progress = { it.coerceIn(0f, 1f) },
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
