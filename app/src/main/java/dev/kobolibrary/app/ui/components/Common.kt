package dev.kobolibrary.app.ui.components

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import dev.kobolibrary.app.ui.theme.LocalKoLibStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, LocalKoLibStyle.current.hairline),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?, selectable: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
        )
        if (selectable) {
            SelectionContainer(Modifier.weight(1f)) { Text(value, style = MaterialTheme.typography.bodyMedium) }
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

object Fmt {
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

    fun date(millis: Long?): String? = millis?.let { dateFormatter.format(Instant.ofEpochMilli(it)) }
    fun dateTime(millis: Long?): String? = millis?.let { dateTimeFormatter.format(Instant.ofEpochMilli(it)) }
    fun time(millis: Long?): String? = millis?.let { timeFormatter.format(Instant.ofEpochMilli(it)) }
    fun bytes(context: Context, bytes: Long?): String? = bytes?.let { Formatter.formatShortFileSize(context, it) }

    /** "1 book", "3 books": [one] and [many] are the English words; the phrase is in KoLib's language. */
    fun plural(n: Int, one: String, many: String = one + "s") = dev.kobolibrary.app.tr(if (n == 1) "{0} $one" else "{0} $many", n)

    /** A publication date as stored ("2003", "2003-02" or "2003-02-27"), written out for reading. */
    fun publication(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            when (iso.length) {
                10 -> java.time.LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                7 -> java.time.YearMonth.parse(iso).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                else -> iso
            }
        }.getOrDefault(iso)
    }
}
