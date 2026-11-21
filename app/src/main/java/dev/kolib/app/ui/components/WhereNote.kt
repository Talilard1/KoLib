package dev.kolib.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.tr

/**
 * Opens a book from the Kobo here: its copy in the library, or (with the Kobo plugged in) it's added to the library
 * first. Null where nothing can be opened.
 */
val LocalReadHere = androidx.compose.runtime.staticCompositionLocalOf<((dev.kolib.app.kobo.KoboBook) -> Unit)?> { null }

/** "Phone" or "tablet", for saying where books are: this device's kind, as people call it. */
@Composable
fun thisDevice(): String = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) tr("this tablet") else tr("this phone")

/**
 * Over the Kobo's books until it's dismissed: this screen shows what's on the Kobo; the library is where books are
 * read, edited and sent, so a book is added to it to be worked with here. Said once, plainly, as a tip where it matters.
 */
@Composable
fun KoboWhereNote(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth().testTag("kobo-where-note"),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("These are the books on your Kobo. Your library is where you read, edit and send books. To add one from here, tap it, then Add to library."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(tr("Got it")) }
            }
        }
    }
}
