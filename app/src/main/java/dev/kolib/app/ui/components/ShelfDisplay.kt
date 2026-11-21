package dev.kolib.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** The button over a shelf that opens [ShelfDisplaySheet]: how the shelf shows its books ([mode]), and how many to a row. */
@Composable
fun DisplayButton(mode: ShelfMode, onMode: (ShelfMode) -> Unit, modifier: Modifier = Modifier, tag: String = "display-button") {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.testTag(tag)) { Icon(Icons.Filled.FilterList, tr("Display")) }
    if (open) ShelfDisplaySheet(mode, onMode) { open = false }
}

/**
 * How a shelf shows its books, chosen over the shelf itself, so each choice is seen as it's made: as covers (with their
 * words under them, on them, or none) or a list, and with covers, how many to a row with the screen held the way it is
 * (Auto: as many as there's room for). A sheet from the foot of the screen, as Mihon's is.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelfDisplaySheet(mode: ShelfMode, onMode: (ShelfMode) -> Unit, onDismiss: () -> Unit) {
    val perRow = LocalCoversPerRow.current
    val sideways = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("display-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
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
        }
    }
}
