@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kolib.app.ui

import dev.kolib.app.ui.components.shortScreen
import dev.kolib.app.ui.components.TabLabel
import dev.kolib.app.ui.components.ShortTopRow
import dev.kolib.app.ui.components.HidingTop
import androidx.compose.foundation.layout.RowScope
import dev.kolib.app.ui.tour.tourPlace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import dev.kolib.app.tr
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
import dev.kolib.app.ui.components.WithFoot
import dev.kolib.app.ui.components.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import dev.kolib.app.ui.components.TabPages
import dev.kolib.app.ui.components.rememberTabPager
import dev.kolib.app.ui.components.rememberTabTap
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
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboView
import dev.kolib.app.UiState
import dev.kolib.app.UsbKobo
import dev.kolib.app.ui.components.Fmt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Checklist
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.ui.text.style.TextOverflow
import dev.kolib.app.ui.components.phoneLayout
import androidx.compose.runtime.LaunchedEffect

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
    /** Shows another Kobo's saved copy; forgets one. For someone with more than one Kobo. */
    onShowKobo: (String) -> Unit = {},
    onForgetSavedKobo: (String) -> Unit = {},
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
    remember(initialCollectionKey) { initialCollectionKey?.let { browse.collectionKey = it }; 0 }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var showEject by remember { mutableStateOf(false) }
    val phone = phoneLayout()
    /** Shows the Kobo's books with [tag], or found by [search], in the Books tab (from a book's details). */
    val showTag = { tag: String ->
        browse.tags = setOf(tag)
        browse.selectedBookId = null
        browse.collectionBookId = null
        tab = 0
    }
    val showSearch = { search: String ->
        browse.query = search
        browse.collection = null
        browse.tags = emptySet()
        browse.selectedBookId = null
        browse.collectionBookId = null
        tab = 0
    }

    // On a phone, a book opens on a page of its own, in place of the Kobo's bar and tabs: from the Books tab, or from a
    // collection's books in the Collections tab, going back to where it came from.
    val inCollection = tab == 1
    val phoneBookId = when {
        !phone -> null
        tab == 0 && !browse.selecting -> browse.selectedBookId
        inCollection && browse.collectionKey != null -> browse.collectionBookId
        else -> null
    }
    if (phoneBookId != null) {
        snapshot.booksById[phoneBookId]?.let { book ->
            val back = { if (inCollection) browse.collectionBookId = null else browse.selectedBookId = null }
            BackHandler(onBack = back)
            PhoneKoboBookPage(
                book = book,
                collections = snapshot.collectionsByBook[book.contentId].orEmpty(),
                covers = view.covers,
                inLibrary = extras.inLibrary(book),
                live = live,
                onBack = back,
                onOpenCollection = { name ->
                    if (inCollection) {
                        browse.collectionKey = name
                        browse.collectionBookId = null
                    } else {
                        browse.collection = name
                        browse.selectedBookId = null
                    }
                },
                onAddToLibrary = if (live) ({ onAddToLibrary(listOf(book)) }) else null,
                onDeleteFromKobo = if (live && book.isSideloaded) ({
                    back()
                    onDeleteFromKobo(listOf(book))
                }) else null,
                tags = extras.tags(book),
                onTag = showTag,
                onSearch = showSearch,
                stars = extras.stars(book),
                pages = extras.pages(book),
            )
            return
        }
    }
    // While choosing books on a phone, the bar for them takes the place of the Kobo's bar and tabs.
    val choosing = phone && tab == 0 && browse.selecting
    // On a short screen (a phone held sideways), the name, the tabs and the buttons share a row, and slide away with
    // the search as the books are scrolled.
    val short = shortScreen()
    // Not plugged in: said under the Kobo's name, where a phone has room for it alone.
    val status = when {
        !view.live -> tr("Not connected · as of {0}", Fmt.date(view.readAtMillis) ?: "")
        unplugged -> tr("Unplugged · as of {0}", Fmt.time(view.readAtMillis) ?: "")
        else -> null
    }
    val title: @Composable () -> Unit = {
        Column {
            KoboName(
                view = view,
                known = state.knownKobos,
                // The Kobo plugged in is the one shown; another's saved copy can be looked at once it's unplugged.
                canSwitch = !view.live || unplugged,
                onShow = onShowKobo,
                onForget = onForgetSavedKobo,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status != null) {
                    Icon(Icons.Outlined.UsbOff, null, Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOfNotNull(
                        status,
                        view.info.version?.firmware?.takeIf { !phone && !short && status == null }?.let { tr("Firmware {0}", it) },
                        Fmt.plural(snapshot.books.size, "book").takeIf { !((phone || short) && status != null) },
                        Fmt.plural(snapshot.collections.size, "collection").takeIf { !((phone || short) && status != null) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("kobo-status"),
                )
            }
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        if (live) {
            TextButton(onClick = { if (onEject != null) onEject() else showEject = true }, modifier = Modifier.tourPlace("kobo-eject")) {
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
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.tourPlace("kobo-menu")) { Icon(Icons.Filled.MoreVert, tr("More")) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // On a phone, the book list's own options are here too, so the screen has one menu.
            if (phone && tab == 0) {
                DropdownMenuItem(
                    text = { Text(tr("Select books")) },
                    leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                    onClick = { menuOpen = false; browse.selecting = true },
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
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = if (choosing) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!choosing && !short) TopAppBar(
                title = title,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
            // Swipe left or right for the next tab, or tap one. Books being chosen are let go of on another tab.
            val pager = rememberTabPager(tab, 3) { t ->
                if (t != 0) browse.stopSelecting()
                tab = t
            }
            LaunchedEffect(browse.selecting) { if (browse.selecting) tab = 0 }
            val goTo = rememberTabTap(pager)
            // A phone has the counts in the bar above, so the tabs fit on one line.
            val tabs: @Composable () -> Unit = {
                PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent, modifier = Modifier.tourPlace("kobo-tabs")) {
                    Tab(selected = pager.currentPage == 0, onClick = { goTo(0) }, text = { TabLabel(if (phone) tr("Books") else tr("Books ({0})", snapshot.books.size)) })
                    Tab(selected = pager.currentPage == 1, onClick = { goTo(1) }, text = { TabLabel(if (phone) tr("Collections") else tr("Collections ({0})", snapshot.collections.size)) })
                    Tab(selected = pager.currentPage == 2, onClick = { goTo(2) }, text = { TabLabel(tr("Device")) })
                }
            }
        HidingTop(
            hides = short,
            top = {
                Column {
                    if (!choosing && short) ShortTopRow(title = title, tabs = tabs, actions = actions)
                    if (!choosing) {
                        val notOpen = !view.live || unplugged
                        when {
                            notOpen && (state.usbKobo == UsbKobo.OPENING || state.usbKobo == UsbKobo.SLOW) ->
                                OpeningBanner(slow = state.usbKobo == UsbKobo.SLOW)
                            notOpen && state.usbKobo == UsbKobo.UNMOUNTED -> SafeToUnplugBanner()
                            state.syncing -> SyncingBanner()
                        }
                    }
                    if (!choosing && !short) tabs()
                }
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // The bar for books being chosen goes along the foot, the screen's whole width, under a side panel too.
            WithFoot(Modifier.fillMaxSize()) { TabPages(pager, Modifier.fillMaxSize().tourPlace("kobo-shelf"), swipe = !browse.selecting) { page ->
                when (page) {
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
                        browse = browse,
                        extras = extras,
                        onAddToLibrary = onAddToLibrary,
                        onDeleteFromKobo = onDeleteFromKobo,
                        onTag = showTag,
                        onSearch = showSearch,
                    )
                    else -> DeviceTab(view, live, state, onWriteCheck, onBackupNow, onOpenBackups, onOpenLog)
                }
            } }
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

/**
 * The Kobo's name at the top of its window. With more than one Kobo known (someone with two, each with its
 * own books), it opens a list of them to choose which one's books and collections to look at.
 */
@Composable
private fun KoboName(
    view: KoboView,
    known: List<dev.kolib.app.kobo.KnownKobo>,
    canSwitch: Boolean,
    onShow: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    val shown = dev.kolib.app.kobo.LastSeenStore.keyOf(view.info)
    // Two of the same model are told apart by the end of their serial numbers.
    val twins = known.groupBy { it.name }.filterValues { it.size > 1 }.keys
    fun label(k: dev.kolib.app.kobo.KnownKobo) = if (k.name in twins && k.serialEnd.isNotEmpty()) "${k.name} (…${k.serialEnd})" else k.name
    val mine = known.firstOrNull { it.key == shown }
    val name = mine?.let { label(it) } ?: view.info.displayName
    if (known.size < 2 || !canSwitch) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.testTag("kobo-switch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, tr("Choose another Kobo"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            known.forEach { k ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(k))
                            Text(
                                listOfNotNull(Fmt.plural(k.books, "book"), Fmt.date(k.readAtMillis)?.let { tr("seen {0}", it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { if (k.key == shown) Icon(Icons.Filled.Check, tr("Shown now")) else Spacer(Modifier.size(24.dp)) },
                    trailingIcon = if (k.key == shown) null else {
                        { IconButton(onClick = { onForget(k.key) }) { Icon(Icons.Filled.Close, tr("Forget this Kobo")) } }
                    },
                    onClick = {
                        open = false
                        if (k.key != shown) onShow(k.key)
                    },
                    modifier = Modifier.testTag("kobo-${k.key}"),
                )
            }
        }
    }
}
