package dev.kolib.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.tr
import dev.kolib.app.ui.components.KoLibMark
import dev.kolib.app.ui.components.TopInsets

/**
 * The More tab, as Mihon's: everything that isn't the library, the Kobo or other devices, in one place that's the same
 * on every screen (Settings, reading stats, the tours, the log, the Kobo's database backups, and about KoLib).
 */
@Composable
fun MoreScreen(
    version: String,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onLog: () -> Unit,
    onBackups: () -> Unit,
    onAbout: () -> Unit,
    onUpdates: () -> Unit = {},
    onStorage: () -> Unit = {},
    /** What's new in this version; null where it has nothing to say. */
    onWhatsNew: (() -> Unit)? = null,
    /** The AI's settings: the model that explains words, summarises and answers questions, and the dictionary. */
    onAi: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(TopInsets)
            .verticalScroll(rememberScrollState())
            .testTag("more-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        KoLibMark(Modifier.size(56.dp), glow = true)
        Spacer(Modifier.height(10.dp))
        Text("KoLib", style = MaterialTheme.typography.headlineSmall)
        Text(version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            HorizontalDivider()
            MoreItem(Icons.Outlined.Settings, tr("Settings"), tr("Look, reading, library and updates"), "more-settings", onSettings)
            MoreItem(Icons.Outlined.BarChart, tr("Reading stats"), tr("Time read, here and on the Kobo"), "more-stats", onStats)
            onAi?.let { MoreItem(Icons.Outlined.AutoAwesome, tr("AI and dictionary"), tr("Meanings of words, chapter summaries and questions about a book"), "more-ai", it) }
            HorizontalDivider()
            MoreItem(Icons.Outlined.Storage, tr("Storage"), tr("How much space KoLib takes up, and by what"), "more-storage", onStorage)
            MoreItem(Icons.Outlined.Restore, tr("Database backups"), tr("Copies of the Kobo's library, saved before each change"), "more-backups", onBackups)
            MoreItem(Icons.AutoMirrored.Outlined.ReceiptLong, tr("Log"), tr("A record of what KoLib did, to send when something goes wrong"), "more-log", onLog)
            HorizontalDivider()
            onWhatsNew?.let { MoreItem(Icons.Outlined.NewReleases, tr("What's new"), tr("What changed in this version of KoLib"), "more-whats-new", it) }
            MoreItem(Icons.Outlined.SystemUpdate, tr("Check for updates"), tr("See if there's a newer KoLib on GitHub"), "more-updates", onUpdates)
            MoreItem(Icons.Outlined.Info, tr("About KoLib"), tr("Version, licences and how to get help"), "more-about", onAbout)
        }
        ReleasesButton(Modifier.padding(top = 12.dp).testTag("more-releases"))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** KoLib on GitHub: its newest release, or ([home]) the front page of its repository. */
@Composable
internal fun ReleasesButton(modifier: Modifier = Modifier, home: Boolean = false) {
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    val url = if (home) dev.kolib.app.update.Updates.HOME else dev.kolib.app.update.Updates.PAGE
    androidx.compose.material3.IconButton(onClick = { runCatching { links.openUri(url) } }, modifier = modifier) {
        Icon(dev.kolib.app.ui.components.SourceIcons.Release, if (home) tr("KoLib on GitHub") else tr("Newest release on GitHub"), tint = MaterialTheme.colorScheme.primary)
    }
}
