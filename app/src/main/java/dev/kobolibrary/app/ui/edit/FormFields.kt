@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.ui.languageName
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Best matches first: names starting with the text, then names containing it. */
/** Every name shows when nothing is typed yet, so all of the Kobo's collections can be scrolled through. */
internal fun matchSuggestions(text: String, suggestions: List<String>, exclude: Collection<String>, limit: Int = 400): List<String> {
    val t = text.trim().lowercase()
    val excluded = exclude.map { it.lowercase() }.toSet()
    val pool = suggestions.filter { it.lowercase() !in excluded }
    if (t.isEmpty()) return pool.take(limit)
    val starts = pool.filter { it.lowercase().startsWith(t) }
    val words = pool.filter { s -> s !in starts && s.lowercase().split(' ', '-', '(').any { it.startsWith(t) } }
    val contains = pool.filter { s -> s !in starts && s !in words && s.lowercase().contains(t) }
    return (starts + words + contains).take(limit)
}

private fun highlighted(text: String, query: String) = buildAnnotatedString {
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (at < 0) {
        append(text)
    } else {
        append(text.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(at, at + q.length)) }
        append(text.substring(at + q.length))
    }
}

/**
 * A list of names shown as chips, with a box to add more that suggests names already in use
 * (collections on the Kobo, tags in the library, and so on).
 */
@Composable
fun ChipsField(
    values: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    suggestions: List<String>,
    addLabel: String,
    newItemLabel: (String) -> String,
    chipIcon: ImageVector?,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    commitOnComma: Boolean = true,
    onChipClick: ((Int) -> Unit)? = null,
    chipBadge: (Int) -> String? = { null },
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(text, suggestions, values.toList()) { matchSuggestions(text, suggestions, values) }
    val trimmed = text.trim()
    val exact = trimmed.isNotEmpty() && (values + suggestions).any { it.equals(trimmed, ignoreCase = true) }
    val canCreate = trimmed.isNotEmpty() && !exact

    fun commit(raw: String) {
        val v = raw.trim()
        if (v.isEmpty()) return
        if (values.none { it.equals(v, ignoreCase = true) }) {
            onAdd(suggestions.firstOrNull { it.equals(v, ignoreCase = true) } ?: v)
        }
        text = ""
    }

    Column(modifier) {
        if (values.isEmpty()) {
            if (emptyText != null) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                values.forEachIndexed { i, v ->
                    InputChip(
                        selected = false,
                        onClick = { onChipClick?.invoke(i) },
                        label = {
                            val badge = chipBadge(i)
                            Text(if (badge != null) "$v  ·  $badge" else v)
                        },
                        leadingIcon = chipIcon?.let { icon -> { Icon(icon, null, Modifier.size(InputChipDefaults.IconSize)) } },
                        trailingIcon = {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemove(i) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, "Remove $v", Modifier.size(16.dp)) }
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        ExposedDropdownMenuBox(expanded = expanded && (matches.isNotEmpty() || canCreate), onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (commitOnComma && new.contains(',')) {
                        val parts = new.split(',')
                        parts.dropLast(1).forEach { commit(it) }
                        text = parts.last().trimStart()
                    } else {
                        text = new
                    }
                    expanded = true
                },
                placeholder = { Text(addLabel) },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Words),
                keyboardActions = KeyboardActions(onDone = { commit(text) }),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded && (matches.isNotEmpty() || canCreate), onDismissRequest = { expanded = false }) {
                if (canCreate) {
                    DropdownMenuItem(
                        text = { Text(newItemLabel(trimmed)) },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { commit(trimmed) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                    if (matches.isNotEmpty()) HorizontalDivider()
                }
                matches.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(highlighted(m, text)) },
                        onClick = { commit(m) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

/** A text box that offers values already used in the library (series names, publishers). */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        matchSuggestions(value, suggestions, emptyList(), limit = 30).filterNot { it.equals(value.trim(), ignoreCase = true) }
    }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            trailingIcon = {
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, "Clear $label") }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { m ->
                DropdownMenuItem(
                    text = { Text(highlighted(m, value)) },
                    onClick = {
                        onValueChange(m)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** The book's number in its series, with buttons to step it up and down. */
@Composable
fun SeriesNumberField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, error: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = value,
            onValueChange = { s -> onValueChange(s.filter { it.isDigit() || it == '.' || it == ',' }) },
            label = { Text("Number") },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.width(120.dp),
        )
        Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
            Row {
                IconButton(
                    onClick = {
                        val n = DetailsDraft.parseSeriesIndex(value) ?: 1.0
                        onValueChange(dev.kobolibrary.app.epub.Names.formatIndex(maxOf(0.0, Math.ceil(n) - 1)))
                    },
                    enabled = enabled,
                ) { Icon(Icons.Filled.Remove, "One less") }
                IconButton(
                    onClick = {
                        val n = DetailsDraft.parseSeriesIndex(value)
                        onValueChange(dev.kobolibrary.app.epub.Names.formatIndex(if (n == null) 1.0 else Math.floor(n) + 1))
                    },
                    enabled = enabled,
                ) { Icon(Icons.Filled.Add, "One more") }
            }
        }
    }
}

/** Five stars; tapping the current rating again clears it. Values are 0–10, two per star. */
@Composable
fun RatingField(rating: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..5) {
            val icon = when {
                rating >= star * 2 -> Icons.Filled.Star
                rating == star * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            IconButton(onClick = { onChange(if (rating == star * 2) 0 else star * 2) }) {
                Icon(
                    icon,
                    contentDescription = if (star == 1) "1 star" else "$star stars",
                    tint = if (rating >= star * 2 - 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                rating <= 0 -> "Not rated"
                rating % 2 == 0 -> "${rating / 2} of 5"
                else -> "${rating / 2}½ of 5"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rating > 0) {
            TextButton(onClick = { onChange(0) }) { Text("Clear") }
        }
    }
}

private val commonLanguages = listOf(
    "en", "fr", "de", "es", "it", "pt", "nl", "sv", "da", "nb", "fi", "is", "pl", "cs", "hu", "ro", "ru", "uk",
    "el", "tr", "ja", "zh", "ko", "vi", "id", "hi", "ar", "he", "la", "eo",
)

/** The book's language, picked by name. */
@Composable
fun LanguageField(code: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(code) {
        val base = commonLanguages.map { it to languageName(it) }.sortedBy { it.second.lowercase() }
        if (code.isBlank() || commonLanguages.any { it.equals(code, true) }) base
        else listOf(code to languageName(code)) + base
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (code.isBlank()) "Not set" else languageName(code),
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (c, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = { Text(c, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        onChange(c)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Publication date: type a year or a date, or pick one from a calendar. */
@Composable
fun PublishedField(value: String, onChange: (String) -> Unit, error: String?, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    val readable = remember(value) {
        DetailsDraft.parseDate(value)?.let { iso ->
            when (iso.length) {
                10 -> LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                else -> null
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '-' }.take(10)) },
        label = { Text("Published") },
        placeholder = { Text("2005 or 2005-07-17") },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: readable)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Filled.CalendarMonth, "Pick a date") } },
        modifier = modifier,
    )
    if (picking) {
        val iso = DetailsDraft.parseDate(value)
        val initial = when (iso?.length) {
            10 -> LocalDate.parse(iso)
            7 -> LocalDate.parse("$iso-01")
            4 -> LocalDate.of(iso.toInt(), 1, 1)
            else -> null
        }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            yearRange = 1000..(Year.now().value + 5),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                        picking = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
