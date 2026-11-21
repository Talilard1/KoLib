package dev.kolib.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The top of a page on a short screen (a phone held sideways): its name, its tabs and its buttons on one row, where
 * a taller screen has a bar with the tabs under it.
 */
@Composable
fun ShortTopRow(
    title: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(max = 240.dp)) { title() }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { tabs() }
        actions()
    }
}

/** [ShortTopRow] with the page's name in words. */
@Composable
fun ShortTopRow(title: String, tabs: @Composable () -> Unit, actions: @Composable RowScope.() -> Unit, modifier: Modifier = Modifier) =
    ShortTopRow(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        tabs = tabs, actions = actions, modifier = modifier,
    )
