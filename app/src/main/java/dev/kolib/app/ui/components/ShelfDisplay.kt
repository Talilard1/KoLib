package dev.kolib.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.tr
import kotlin.math.roundToInt

/**
 * How a shelf shows its books, as Mihon's library does: covers with their title and author under them (comfortable),
 * covers with the title on them (compact), covers alone, or a list.
 */
enum class ShelfMode(val label: String) {
    COMPACT(tr("Compact grid")),
    COMFORTABLE(tr("Comfortable grid")),
    COVERS(tr("Cover-only grid")),
    LIST(tr("List")),
}

/** How the shelf being drawn shows its covers (see [ShelfGridBox]). */
val LocalShelfMode = staticCompositionLocalOf { ShelfMode.COMFORTABLE }

/** The words under a cover on a shelf (its title, author and the like): there in the comfortable grid only. */
@Composable
fun ColumnScope.UnderCover(content: @Composable ColumnScope.() -> Unit) {
    if (LocalShelfMode.current != ShelfMode.COMFORTABLE) return
    Spacer(Modifier.height(8.dp))
    content()
}

/**
 * What a shelf's one button offers besides how its books show, each on a tab of its sheet as Mihon's: the shelf's
 * filters ([filter]) and its order ([sort]). [filtered]: a filter's on, so the button's lit to say so.
 */
class ShelfExtras(
    val filter: (@Composable ColumnScope.() -> Unit)? = null,
    val sort: (@Composable ColumnScope.() -> Unit)? = null,
    val filtered: Boolean = false,
    /** Whether the covers' marks (where the book is, how far it's been read) can be turned off here. */
    val marks: Boolean = true,
    /** More of how the shelf shows, at the end of the Display tab. */
    val display: (@Composable ColumnScope.() -> Unit)? = null,
)

/**
 * The one button over a shelf, as Mihon's: it opens [ShelfDisplaySheet], with the shelf's filters, its order and how
 * its books show (and how many covers to a row). Lit while a filter's on.
 */
@Composable
fun DisplayButton(
    mode: ShelfMode,
    onMode: (ShelfMode) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "display-button",
    extras: ShelfExtras? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // The tab last looked at, for the next time it's opened.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    IconButton(onClick = { open = true }, modifier = modifier.testTag(tag)) {
        Icon(
            Icons.Filled.FilterList,
            if (extras?.filtered == true) tr("Filter, sort and display (filtered)") else if (extras != null) tr("Filter, sort and display") else tr("Display"),
            tint = if (extras?.filtered == true) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
    if (open) ShelfDisplaySheet(mode, onMode, extras = extras, tab = tab, onTab = { tab = it }) { open = false }
}

/**
 * How a shelf shows its books, chosen over the shelf itself, so each choice is seen as it's made: as covers (with their
 * words under them, on them, or none) or a list, and with covers, how many to a row with the screen held the way it is
 * (Auto: as many as there's room for). With [extras], its filters and its order too, each on a tab: Filter, Sort,
 * Display. A sheet from the foot of the screen, as Mihon's is.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelfDisplaySheet(
    mode: ShelfMode,
    onMode: (ShelfMode) -> Unit,
    extras: ShelfExtras? = null,
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val display: @Composable ColumnScope.() -> Unit = {
        DisplayChoices(mode, onMode, marks = extras?.marks ?: false)
        extras?.display?.invoke(this)
    }
    val parts = buildList {
        extras?.filter?.let { add(Triple("filter", tr("Filter"), it)) }
        extras?.sort?.let { add(Triple("sort", tr("Sort"), it)) }
        add(Triple("display", tr("Display"), display))
    }
    val shown = tab.coerceIn(0, parts.size - 1)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("display-sheet"),
    ) {
        if (parts.size > 1) {
            PrimaryTabRow(selectedTabIndex = shown, containerColor = Color.Transparent) {
                parts.forEachIndexed { i, (key, name, _) ->
                    Tab(selected = i == shown, onClick = { onTab(i) }, text = { Text(name) }, modifier = Modifier.testTag("display-tab-$key"))
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (parts.size > 1) 300.dp else 0.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = if (parts.size > 1) 16.dp else 0.dp, bottom = 24.dp)
        ) {
            parts[shown].third(this)
        }
    }
}

/** A heading in the shelf's sheet. */
@Composable
fun SheetHeading(text: String, top: Boolean = false) {
    if (!top) Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
}

/** The orders a shelf can be put in, as Mihon's: tapped again, the one it's in now turns the other way. */
@Composable
fun <T> SortChoices(
    orders: List<T>,
    current: T,
    descending: Boolean,
    label: (T) -> String,
    direction: (T, Boolean) -> String,
    /** Whether the order picked again turns the other way (with an arrow); otherwise it's ticked. */
    turns: Boolean = true,
    onPick: (T) -> Unit,
) {
    orders.forEach { order ->
        val on = order == current
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onPick(order) }
                .testTag("sort-choice"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (on) Icon(
                if (!turns) Icons.Filled.Check else if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                if (turns) tr("Reverse order") else null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            ) else Spacer(Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label(order), color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (on && turns) Text(direction(order, descending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Clear filters", at the foot of the Filter tab, while any are on. */
@Composable
fun ClearFiltersLine(on: Boolean, onClear: () -> Unit) {
    if (!on) return
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClear, modifier = Modifier.testTag("filters-clear")) { Text(tr("Clear filters")) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayChoices(mode: ShelfMode, onMode: (ShelfMode) -> Unit, marks: Boolean) {
    val perRow = LocalCoversPerRow.current
    val sideways = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Text(tr("Display mode"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ShelfMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onMode(m) },
                label = { Text(m.label) },
                modifier = Modifier.testTag("display-${m.name.lowercase()}"),
            )
        }
    }
    if (mode != ShelfMode.LIST) {
        val n = if (sideways) perRow.landscape else perRow.portrait
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Covers per row"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    if (n == 0) tr("Auto") else "$n",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("display-per-row-value"),
                )
            }
        }
        // For the way the screen is held: turned, the other way's number is set here.
        Slider(
            value = n.toFloat(),
            onValueChange = { v ->
                val k = v.roundToInt().coerceIn(0, CoversPerRow.MOST)
                if (sideways) perRow.landscape = k else perRow.portrait = k
            },
            valueRange = 0f..CoversPerRow.MOST.toFloat(),
            steps = CoversPerRow.MOST - 1,
            modifier = Modifier.fillMaxWidth().testTag("display-per-row"),
        )
    }
    if (marks) {
        SheetHeading(tr("Show"))
        SwitchRow(tr("Where each book is"), tr("Marks books that are on the Kobo, in the library, or changed since they were sent."), perRow.showWhere, "display-marks-where") { perRow.showWhere = it }
        SwitchRow(tr("Reading progress"), tr("Shows how far you've read, here and on the Kobo."), perRow.showProgress, "display-marks-progress") { perRow.showProgress = it }
        SwitchRow(tr("Continue reading"), tr("Shows what you're reading at the top of the shelf."), perRow.showContinue, "display-continue") { perRow.showContinue = it }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
