@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kobolibrary.app.ui

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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Eject
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Usb
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
    onAddToLibrary: (List<KoboBook>) -> Unit = {},
    onDeleteFromKobo: (List<KoboBook>) -> Unit = {},
    onHowToConnect: () -> Unit = {},
    /** Books are being sent, so ejecting now would cut one off. */
    sending: Boolean = false,
    initialCollectionKey: String? = null,
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
                stars = extras.stars(book),
                pages = extras.pages(book),
            )
            return
        }
    }
    // While choosing books on a phone, the bar for them takes the place of the Kobo's bar and tabs.
    val choosing = phone && tab == 0 && browse.selecting

    Scaffold(
        contentWindowInsets = if (choosing) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!choosing) TopAppBar(
                title = {
                    Column {
                        Text(view.info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                view.info.version?.firmware?.takeIf { !phone }?.let { "Firmware $it" },
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
                        TextButton(onClick = { showEject = true }) {
                            Icon(Icons.Outlined.Eject, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Eject")
                        }
                        IconButton(onClick = onReconnect) { Icon(Icons.Filled.Refresh, "Refresh") }
                    } else if (!view.live) {
                        TextButton(onClick = onHowToConnect) {
                            Icon(Icons.Outlined.Usb, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Connect")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // On a phone, the book list's own options are here too, so the screen has one menu.
                        if (phone && tab == 0) {
                            DropdownMenuItem(
                                text = { Text("Select books") },
                                leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                                onClick = { menuOpen = false; browse.selecting = true },
                            )
                            DropdownMenuItem(
                                text = { Text(if (browse.grid) "Show as list" else "Show as grid") },
                                leadingIcon = { Icon(if (browse.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, null) },
                                onClick = { menuOpen = false; browse.grid = !browse.grid },
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Back up database now") },
                            leadingIcon = { Icon(Icons.Outlined.Backup, null) },
                            enabled = live,
                            onClick = { menuOpen = false; onBackupNow() },
                        )
                        DropdownMenuItem(
                            text = { Text("Database backups") },
                            leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                            onClick = { menuOpen = false; onOpenBackups() },
                        )
                        DropdownMenuItem(
                            text = { Text("Check write access") },
                            leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                            enabled = live,
                            onClick = { menuOpen = false; tab = 2; onWriteCheck() },
                        )
                        DropdownMenuItem(
                            text = { Text("Log") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Article, null) },
                            onClick = { menuOpen = false; onOpenLog() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Forget this Kobo") },
                            leadingIcon = { Icon(Icons.Outlined.LinkOff, null) },
                            onClick = { menuOpen = false; confirmForget = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!choosing) {
                when {
                    !view.live -> OfflineBanner(view.readAtMillis, onHowToConnect)
                    unplugged -> UnpluggedBanner(view.readAtMillis)
                    state.syncing -> SyncingBanner()
                }
                // A phone has the counts in the bar above, so the tabs fit on one line.
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (phone) "Books" else "Books (${snapshot.books.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (phone) "Collections" else "Collections (${snapshot.collections.size})") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Device") })
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
            title = { Text("Forget this Kobo?") },
            text = {
                Text(
                    "The app will lose its permission to use the Kobo's drive. Nothing on the Kobo changes, and backups on this device aren't deleted. " +
                        "Next time, you'll be asked to allow access again."
                )
            },
            confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
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
                "Not connected. Showing your Kobo as it was on ${Fmt.dateTime(readAt)}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onHowToConnect) { Text("How to connect") }
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
                "Saving collections and book details to the Kobo… Wait until this finishes before ejecting.",
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
                "The Kobo is unplugged. Showing what was loaded at ${Fmt.time(readAt)}. To refresh, plug it in and tap Connect on the Kobo.",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
