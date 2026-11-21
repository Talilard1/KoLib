package dev.kolib.app.ui.update

import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Button
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.FloatingWindow
import dev.kolib.app.ui.components.grows
import dev.kolib.app.update.Release
import dev.kolib.app.update.Updater
import dev.kolib.app.update.Updates

/**
 * What the updater has to show: a newer version with what's new in it and the choice to update, its
 * download as it comes, the install, or what went wrong; and, when the user asked, that there's nothing
 * newer. Nothing while there's nothing to say.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateWindow(updater: Updater) {
    val state by updater.state.collectAsState()
    val context = LocalContext.current
    val s = state
    if (s == Updater.State.Idle) return
    fun page(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    FloatingWindow(onDismiss = { updater.dismiss() }, Modifier.testTag("update-window"), maxWidth = 520.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    when (s) {
                        is Updater.State.Available -> tr("KoLib {0} is available", s.release.version)
                        is Updater.State.Downloading -> tr("Downloading KoLib {0}…", s.release.version)
                        is Updater.State.Installing -> tr("Installing KoLib {0}…", s.release.version)
                        is Updater.State.Failed -> tr("KoLib {0} is available", s.release.version)
                        else -> tr("Updates")
                    },
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            when (s) {
                Updater.State.Idle -> {}
                Updater.State.Checking -> {
                    Text(tr("Checking for updates…"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 14.dp))
                }
                Updater.State.UpToDate -> {
                    Text(tr("You have the newest version, {0}.", updater.installed), style = MaterialTheme.typography.bodyMedium)
                    Buttons { TextButton(onClick = { updater.dismiss() }) { Text(tr("Close")) } }
                }
                Updater.State.Unreachable -> {
                    Text(tr("Couldn't check for updates. Check the internet connection."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Buttons {
                        TextButton(onClick = { updater.dismiss() }) { Text(tr("Close")) }
                        TextButton(onClick = { updater.lookNow() }) { Text(tr("Try again")) }
                    }
                }
                is Updater.State.Available -> {
                    if (s.stopped) {
                        Text(tr("The update wasn't installed."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        AutoBlockerNote()
                    } else {
                        Text(tr("You have {0}.", updater.installed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Notes(s.release)
                    if (s.release.apkUrl == null) {
                        Text(tr("There's no download for this device in that version yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    Buttons {
                        TextButton(onClick = { updater.skip(s.release) }) { Text(tr("Skip this version")) }
                        TextButton(onClick = { updater.dismiss() }, modifier = Modifier.testTag("update-later")) { Text(tr("Not now")) }
                        if (s.release.apkUrl != null) Button(onClick = { updater.update(s.release) }, modifier = Modifier.testTag("update-go")) {
                            // Downloaded already: it's only installed.
                            val downloaded = remember(s) { updater.downloaded(s.release) }
                            Text(
                                when {
                                    downloaded -> tr("Install")
                                    s.release.apkBytes > 0 -> tr("Update ({0})", Fmt.bytes(context, s.release.apkBytes).orEmpty())
                                    else -> tr("Update")
                                }
                            )
                        }
                        else Button(onClick = { page(s.release.page) }) { Text(tr("Open the download page")) }
                    }
                }
                is Updater.State.Downloading -> {
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp).testTag("update-progress"),
                    )
                    Text(
                        tr("{0} of {1}", Fmt.bytes(context, s.bytes).orEmpty(), Fmt.bytes(context, s.total).orEmpty()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Buttons { TextButton(onClick = { updater.stop() }) { Text(tr("Cancel")) } }
                }
                is Updater.State.Installing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp))
                    Text(
                        tr("Android may ask you to confirm. The first time, it also asks you to let KoLib install updates. KoLib closes while it's updated: open it again afterwards."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is Updater.State.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    if (s.installing) AutoBlockerNote()
                    Buttons {
                        TextButton(onClick = { updater.dismiss() }) { Text(tr("Close")) }
                        TextButton(onClick = { page(s.release.page) }) { Text(tr("Open the download page")) }
                        Button(onClick = { updater.update(s.release) }) { Text(tr("Try again")) }
                    }
                }
            }
        }
    }
}

/**
 * On a Samsung, why an update may not have installed: Auto Blocker lets only apps from the Galaxy Store and Play Store
 * be installed, updates to KoLib included. It's only said: KoLib doesn't open the phone's security settings or tell
 * anyone to turn a protection off.
 */
@Composable
private fun AutoBlockerNote(samsung: Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
    if (!samsung) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("update-auto-blocker"),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(tr("Samsung's Auto Blocker may have stopped it."), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("It only lets apps from the Galaxy Store and Play Store be installed."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** What's new in a release, as its notes on GitHub say, in a box that scrolls when they're long. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Notes(release: Release) {
    val notes = remember(release) { Updates.withoutTitle(Updates.plainNotes(release.notes)) }
    if (notes.isBlank()) return
    Text(tr("What's new"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
        Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.grows().verticalScroll(rememberScrollState()).padding(14.dp).testTag("update-notes"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.Center) { content() }
}
