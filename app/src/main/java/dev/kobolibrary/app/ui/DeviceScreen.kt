@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kobolibrary.app.ui

import dev.kobolibrary.app.tr
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Eject
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.UsbOff
import dev.kobolibrary.app.ui.components.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.UiState
import dev.kobolibrary.app.UsbKobo
import dev.kobolibrary.app.ui.components.Fmt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Checklist
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.ui.text.style.TextOverflow
import dev.kobolibrary.app.ui.components.phoneLayout

@Composable
fun DeviceScreen(
    view: KoboView,
    unplugged: Boolean,
    state: UiState,
    browse: BrowseState,
    onReconnect: () -> Unit,
    onWriteCheck: () -> Unit,
    onBackupNow: () -> Unit,
    onForget: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenLog: () -> Unit,
    extras: KoboExtras = KoboExtras(),
    /** Opens the app's settings: its look, language and books folder. */
    onSettings: (() -> Unit)? = null,
    onAddToLibrary: (List<KoboBook>) -> Unit = {},
    onDeleteFromKobo: (List<KoboBook>) -> Unit = {},
    onHowToConnect: () -> Unit = {},
    /** Books are being sent, so ejecting now would cut one off. */
    sending: Boolean = false,
    initialCollectionKey: String? = null,
    /** Opens the app's Eject dialog (otherwise this screen shows its own). */
    onEject: (() -> Unit)? = null,
) {
    val snapshot = view.snapshot
    val live = view.live && !unplugged
    var tab by browse::deviceTab
    var collectionKey by rememberSaveable { mutableStateOf(initialCollectionKey) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var showEject by remember { mutableStateOf(false) }
    val phone = phoneLayout()
    /** A book opened from a collection's books goes back there, on a phone. */
    var fromCollection by rememberSaveable { mutableStateOf(false) }

    // On a phone, a book opens on a page of its own, in place of the Kobo's bar and tabs.
    if (phone && tab == 0 && !browse.selecting) {
        browse.selectedBookId?.let { snapshot.booksById[it] }?.let { book ->
            val back = {
                browse.selectedBookId = null
                if (fromCollection) tab = 1
                fromCollection = false
            }
            BackHandler(onBack = back)
            PhoneKoboBookPage(
                book = book,
                collections = snapshot.collectionsByBook[book.contentId].orEmpty(),
                covers = view.covers,
                inLibrary = extras.inLibrary(book),
                live = live,
                onBack = back,
                onOpenCollection = { name ->
                    browse.collection = name
                    browse.selectedBookId = null
                    fromCollection = false
                },
                onAddToLibrary = if (live) ({ onAddToLibrary(listOf(book)) }) else null,
                onDeleteFromKobo = if (live && book.isSideloaded) ({
                    browse.selectedBookId = null
                    onDeleteFromKobo(listOf(book))
                }) else null,
                tags = extras.tags(book),
                onTag = { tag ->
                    browse.tags = setOf(tag)
                    browse.selectedBookId = null
                    fromCollection = false
                },
                onSearch = { search ->
                    browse.query = search
                    browse.collection = null
                    browse.tags = emptySet()
                    browse.selectedBookId = null
                    fromCollection = false
                },
                stars = extras.stars(book),
                pages = extras.pages(book),
            )
            return
        }
    }
    // While choosing books on a phone, the bar for them takes the place of the Kobo's bar and tabs.
    val choosing = phone && tab == 0 && browse.selecting

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = if (choosing) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!choosing) TopAppBar(
                title = {
                    Column {
                        Text(view.info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                view.info.version?.firmware?.takeIf { !phone }?.let { tr("Firmware {0}", it) },
                                Fmt.plural(snapshot.books.size, "book"),
                                Fmt.plural(snapshot.collections.size, "collection"),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (live) {
                        TextButton(onClick = { if (onEject != null) onEject() else showEject = true }) {
                            Icon(Icons.Outlined.Eject, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Eject"))
                        }
                        IconButton(onClick = onReconnect) { Icon(Icons.Filled.Refresh, tr("Refresh")) }
                    } else if (!view.live) {
                        TextButton(onClick = onHowToConnect) {
                            Icon(Icons.Outlined.Usb, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Connect"))
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, tr("More")) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // On a phone, the book list's own options are here too, so the screen has one menu.
                        if (phone && tab == 0) {
                            DropdownMenuItem(
                                text = { Text(tr("Select books")) },
                                leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                                onClick = { menuOpen = false; browse.selecting = true },
                            )
                            DropdownMenuItem(
                                text = { Text(if (browse.grid) tr("Show as list") else tr("Show as grid")) },
                                leadingIcon = { Icon(if (browse.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, null) },
                                onClick = { menuOpen = false; browse.grid = !browse.grid },
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(tr("Back up database now")) },
                            leadingIcon = { Icon(Icons.Outlined.Backup, null) },
                            enabled = live,
                            onClick = { menuOpen = false; onBackupNow() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Database backups")) },
                            leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                            onClick = { menuOpen = false; onOpenBackups() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Check write access")) },
                            leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                            enabled = live,
                            onClick = { menuOpen = false; tab = 2; onWriteCheck() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Log")) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Article, null) },
                            onClick = { menuOpen = false; onOpenLog() },
                        )
                        onSettings?.let { open ->
                            DropdownMenuItem(
                                text = { Text(tr("Settings")) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menuOpen = false; open() },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(tr("Forget this Kobo")) },
                            leadingIcon = { Icon(Icons.Outlined.LinkOff, null) },
                            onClick = { menuOpen = false; confirmForget = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!choosing) {
                val notOpen = !view.live || unplugged
                when {
                    notOpen && (state.usbKobo == UsbKobo.OPENING || state.usbKobo == UsbKobo.SLOW) ->
                        OpeningBanner(slow = state.usbKobo == UsbKobo.SLOW)
                    notOpen && state.usbKobo == UsbKobo.UNMOUNTED -> SafeToUnplugBanner()
                    !view.live -> OfflineBanner(view.readAtMillis, onHowToConnect)
                    unplugged -> UnpluggedBanner(view.readAtMillis)
                    state.syncing -> SyncingBanner()
                }
                // A phone has the counts in the bar above, so the tabs fit on one line.
                PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (phone) tr("Books") else tr("Books ({0})", snapshot.books.size)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (phone) tr("Collections") else tr("Collections ({0})", snapshot.collections.size)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(tr("Device")) })
                }
            }
            when (tab) {
                0 -> BooksTab(
                    view,
                    live,
                    browse,
                    extras = extras,
                    onAddToLibrary = onAddToLibrary,
                    onDeleteFromKobo = onDeleteFromKobo,
                    readingTags = state.readingTags,
                )
                1 -> CollectionsTab(
                    view,
                    live,
                    selectedKey = collectionKey,
                    libraryPaths = extras.libraryPaths,
                    onSelect = { collectionKey = it },
                    onOpenBook = { id ->
                        browse.selectedBookId = id
                        tab = 0
                        fromCollection = phone
                    },
                    onAddToLibrary = onAddToLibrary,
                )
                else -> DeviceTab(view, live, state, onWriteCheck, onBackupNow, onOpenBackups, onOpenLog)
            }
        }
    }

    if (showEject) EjectDialog(onDismiss = { showEject = false }, busy = sending)

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(tr("Forget this Kobo?")) },
            text = {
                Text(
                    tr("KoLib loses access to the Kobo's drive. Nothing on the Kobo or in your backups changes. You'll be asked for access again next time.")
                )
            },
            confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text(tr("Forget")) } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun OfflineBanner(readAt: Long, onHowToConnect: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.UsbOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                tr("Not connected. Showing your Kobo as of {0}.", Fmt.dateTime(readAt)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onHowToConnect) { Text(tr("How to connect")) }
        }
    }
}

@Composable
private fun SyncingBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                tr("Updating the Kobo… Don't eject it yet."),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A Kobo has been plugged in (Connect tapped on it), and Android is checking and opening its drive. This takes a
 * few seconds, and without this the app would look as if nothing were happening.
 */
@Composable
private fun OpeningBanner(slow: Boolean) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                usbKoboText(if (slow) UsbKobo.SLOW else UsbKobo.OPENING).orEmpty(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The Kobo has been unmounted and is still plugged in: it can be unplugged. */
@Composable
private fun SafeToUnplugBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                usbKoboText(UsbKobo.UNMOUNTED).orEmpty(),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UnpluggedBanner(readAt: Long) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.UsbOff, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                tr("Unplugged. Showing data from {0}. To refresh, plug it in and tap Connect on the Kobo.", Fmt.time(readAt)),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
