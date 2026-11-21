package dev.kolib.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.tr

/**
 * A book's details beside the shelf on a wide screen, opened by tapping the book: with a ✕ that closes them and gives
 * the shelf the whole width back (as Back does).
 */
@Composable
fun SidePane(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("side-pane-close")) { Icon(Icons.Filled.Close, tr("Close")) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
