package dev.kolib.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kolib.app.tr
import dev.kolib.app.ui.components.GoldButton
import dev.kolib.app.update.Updates

/** What's new in a version of KoLib: shown once after it's installed over an older one, and from More. */
object WhatsNew {
    class Note(val icon: ImageVector, val title: String, val text: String)

    /** The notes for KoLib [version] (its name, as "3.5"); null for a version that has none. */
    fun notes(version: String): List<Note>? = when (version.substringBefore('-').substringBefore(' ').trim()) {
        "3.5" -> listOf(
            Note(
                Icons.Outlined.Dashboard, tr("A simpler layout"),
                tr("One button to filter, sort and choose how books show, a More tab, the reading settings you change most up front, and a layout for phones held sideways."),
            ),
            Note(
                Icons.Outlined.PhotoLibrary, tr("Comic editor"),
                tr("Put a comic's pages in order, turn and crop them, split two-page spreads, or split it into several comics. You can make a comic from pictures too."),
            ),
            Note(Icons.Outlined.EditNote, tr("More in the text editor"), tr("Links, tables, lists, quotes and footnotes, underline and more, special characters and a word count.")),
            Note(
                Icons.AutoMirrored.Outlined.MenuBook, tr("The book reader"),
                tr("Pages and scrolling keep their own settings. Choose where the brightness swipe works, swipe the side panels in and out with your finger, and pull up at a chapter's end for the next."),
            ),
            Note(
                Icons.Outlined.AutoStories, tr("Comics"),
                tr("A series opens beside the shelf. Send comics to another device, duplicate them or save them as files from the bar. The reader's menu stays up when you swipe to turn a page."),
            ),
            Note(Icons.Outlined.Devices, tr("Your devices"), tr("The notification says which devices you're connected to, and their battery. So does the Devices screen.")),
            Note(Icons.Outlined.Storage, tr("Storage"), tr("See how much space KoLib takes up, and by what, under More.")),
            Note(Icons.Outlined.SystemUpdate, tr("Updates and help"), tr("Check for updates under More, and report a problem on GitHub from the log.")),
            Note(Icons.Outlined.VerifiedUser, tr("Permissions in one place"), tr("What KoLib needs Android to allow is asked for on one screen.")),
            Note(Icons.Outlined.Lightbulb, tr("New tours"), tr("Short tours show you a book's options, the Comics tab and a series. Any of them can turn tours off.")),
        )
        else -> null
    }
}

/** The window of what's new in KoLib [version]: its [notes], and the way to the full notes on GitHub. */
@Composable
fun WhatsNewWindow(version: String, notes: List<WhatsNew.Note>, onDone: () -> Unit) {
    val links = LocalUriHandler.current
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.92f).heightIn(max = 720.dp).testTag("whats-new"),
        ) {
            Column {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                    Icon(Icons.Outlined.NewReleases, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text(tr("What's new in KoLib {0}", version), style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    notes.forEach { n ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Icon(n.icon, null, Modifier.padding(top = 2.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(n.title, style = MaterialTheme.typography.titleSmall)
                                Text(n.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { runCatching { links.openUri(Updates.RELEASES) } }, modifier = Modifier.testTag("whats-new-notes")) {
                        Text(tr("Full notes on GitHub"))
                    }
                    GoldButton(onClick = onDone, modifier = Modifier.testTag("whats-new-done")) { Text(tr("Got it")) }
                }
            }
        }
    }
}
