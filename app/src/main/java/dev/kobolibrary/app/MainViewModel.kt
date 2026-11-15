@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package dev.kobolibrary.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kobolibrary.app.backup.DatabaseBackup
import dev.kobolibrary.app.kobo.AfterConnect
import dev.kobolibrary.app.kobo.KoboNotFoundException
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboRepository
import dev.kobolibrary.app.kobo.KoboSender
import dev.kobolibrary.app.kobo.KoboDatabaseChangedException
import dev.kobolibrary.app.kobo.KoboSyncRunner
import dev.kobolibrary.app.kobo.KoboTagReader
import dev.kobolibrary.app.kobo.KoboTagStore
import dev.kobolibrary.app.kobo.KoboView
import dev.kobolibrary.app.kobo.KoboSession
import dev.kobolibrary.app.kobo.VolumeCandidate
import dev.kobolibrary.app.kobo.WriteCheckResult
import dev.kobolibrary.app.log.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

sealed interface Connection {
    /** No Kobo drive is mounted. [usbKoboAttached] means a Kobo is plugged in but hasn't been told to connect. */
    data class Waiting(val usbKoboAttached: Boolean) : Connection

    /** A drive is mounted but we don't have permission for it yet. */
    data class NeedsAccess(val candidate: VolumeCandidate, val others: List<VolumeCandidate>) : Connection

    data class Working(val step: String) : Connection

    data class Connected(val session: KoboSession, val unplugged: Boolean = false) : Connection

    data class Failed(val message: String, val candidate: VolumeCandidate?) : Connection
}

data class UiState(
    val connection: Connection = Connection.Waiting(usbKoboAttached = false),
    val backups: List<DatabaseBackup> = emptyList(),
    /** The backup and health check that run after connecting: null while running. */
    val afterConnect: AfterConnect? = null,
    val afterConnectRunning: Boolean = false,
    val writeCheck: WriteCheckResult? = null,
    val writeCheckRunning: Boolean = false,
    val busyMessage: String? = null,
    val message: String? = null,
    /** What was on the Kobo the last time it was connected, for browsing while it's unplugged. */
    val lastSeen: KoboView? = null,
    /** Books deleted from the Kobo that it hasn't caught up with yet (it does once ejected). */
    val removedIds: Set<String> = emptySet(),
    /** The Kobo's database is being brought in line with the library. */
    val syncing: Boolean = false,
    /** Tags of the Kobo's books, read from their files (the Kobo's database has none), by path in lower case. */
    val koboTags: Map<String, KoboTagStore.Entry> = emptyMap(),
    /** While tags are being read from the Kobo's books: how many done, out of how many. */
    val readingTags: Pair<Int, Int>? = null,
) {
    /** What the Kobo screens show: the live Kobo if connected, otherwise the last-seen copy. */
    val koboView: KoboView?
        get() = when (val c = connection) {
            is Connection.Connected -> KoboView.of(c.session, c.session.snapshot.without(removedIds))
            else -> lastSeen?.let { v -> KoboView(v.info, v.snapshot.without(removedIds), v.covers, v.readAtMillis, v.databaseBytes, v.databaseHadWal, null) }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = KoboRepository(app)
    private val library = (app as KoboLibraryApp).library
    private val sender = KoboSender(app)
    private val syncRunner = KoboSyncRunner(repo, library, (app as KoboLibraryApp).pendingKoboRecords)
    private val syncLock = Mutex()
    /** Work waiting for the next sync to be done (see [KoboLibraryApp.koboSyncRequests]). */
    private val syncWaiters = mutableListOf<CompletableDeferred<Unit>>()
    private val removedPrefs = app.getSharedPreferences("kobo_removed", Context.MODE_PRIVATE)
    private var afterConnectJob: Job? = null
    private val _state = MutableStateFlow(UiState(backups = repo.backups.list()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var connectJob: Job? = null
    private var pollJob: Job? = null
    private var foreground = false

    /** Drive uuids we already tried to open automatically since they were mounted (avoids retry loops). */
    private val autoTried = mutableSetOf<String>()

    private val volumeCallback: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                AppLog.d("Drive", "Drive '${volume.getDescription(app)}' is now ${volume.state}.")
                refresh(auto = true)
            }
        }.also { cb ->
            app.getSystemService(StorageManager::class.java)
                .registerStorageVolumeCallback(ContextCompat.getMainExecutor(app), cb)
        }
    } else null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLog.d("USB", "USB event: ${intent.action?.substringAfterLast('.')}")
            refresh(auto = true)
        }
    }

    init {
        _state.update { it.copy(removedIds = removedPrefs.getStringSet("ids", emptySet()).orEmpty().toSet()) }
        viewModelScope.launch {
            val seen = withContext(Dispatchers.IO) { repo.loadLastSeen() }
            if (seen != null) _state.update { it.copy(lastSeen = seen) }
            val tags = withContext(Dispatchers.IO) { repo.bookTags.load() }
            _state.update { it.copy(koboTags = tags) }
        }
        viewModelScope.launch {
            // Several requests close together (a send finishing, a quick edit) make one sync.
            (app as KoboLibraryApp).koboSyncRequests
                .onEach { done -> if (done != null) synchronized(syncWaiters) { syncWaiters += done } }
                .debounce(500)
                .collect { syncWithKobo() }
        }
        ContextCompat.registerReceiver(
            app,
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh(auto = true)
    }

    fun onForeground() {
        foreground = true
        refresh(auto = true)
        startPolling()
    }

    fun onBackground() {
        foreground = false
        pollJob?.cancel()
    }

    /** While waiting for the Kobo, look every couple of seconds in case Android's events don't arrive. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && foreground) {
                delay(2500)
                val c = _state.value.connection
                if (c is Connection.Waiting || c is Connection.NeedsAccess || (c is Connection.Connected && c.unplugged)) {
                    refresh(auto = true)
                } else if (c is Connection.Connected) {
                    refresh(auto = false)
                }
            }
        }
    }

    /** Re-examines mounted drives; connects automatically to a Kobo we already have permission for. */
    fun refresh(auto: Boolean) {
        val volumes = runCatching { repo.removableVolumes() }.getOrElse {
            AppLog.e("Drive", "Could not list drives", it)
            emptyList()
        }
        val mountedUuids = volumes.mapNotNull { it.uuid }.toSet()
        autoTried.retainAll(mountedUuids) // unplugged drives may be retried after the next plug-in

        when (val c = _state.value.connection) {
            is Connection.Working -> return
            is Connection.Connected -> {
                val uuid = c.session.info.volumeUuid
                val stillThere = uuid == null || uuid in mountedUuids
                if (!stillThere && !c.unplugged) {
                    AppLog.i("Kobo", "The Kobo was unplugged or left USB mode.")
                    _state.update { it.copy(connection = c.copy(unplugged = true)) }
                } else if (stillThere && c.unplugged && auto) {
                    val candidate = volumes.firstOrNull { it.uuid == uuid && it.grantedTree != null }
                    if (candidate != null) connect(candidate.grantedTree!!, automatic = true)
                }
                return
            }
            else -> Unit
        }

        val withAccess = volumes.firstOrNull { it.grantedTree != null && (it.isKnownKobo || repo.knownKoboUuid == null) }
        if (withAccess != null && auto && withAccess.uuid !in autoTried) {
            withAccess.uuid?.let { autoTried += it }
            connect(withAccess.grantedTree!!, automatic = true)
            return
        }
        // An SD card in the tablet isn't a Kobo; don't offer it unless it's the only thing the user picks manually.
        val best = volumes.firstOrNull { !it.isLikelySdCard }
        val next: Connection = when {
            best != null -> {
                val current = _state.value.connection
                if (current is Connection.Failed && current.candidate?.uuid == best.uuid) current
                else Connection.NeedsAccess(best, volumes.drop(1))
            }
            else -> Connection.Waiting(usbKoboAttached = repo.attachedKoboUsbProductId() != null)
        }
        if (_state.value.connection != next) _state.update { it.copy(connection = next) }
    }

    fun openTreeIntent(candidate: VolumeCandidate?): Intent = repo.openTreeIntent(candidate)

    /** "Try again" after a failure: forget earlier automatic attempts and look afresh. */
    fun retry() {
        autoTried.clear()
        _state.update { it.copy(connection = Connection.Waiting(usbKoboAttached = false)) }
        refresh(auto = true)
    }

    fun onTreePicked(uri: Uri?) {
        if (uri == null) {
            AppLog.i("Kobo", "Drive picker closed without a choice.")
            return
        }
        try {
            repo.takePermission(uri)
        } catch (e: SecurityException) {
            AppLog.e("Kobo", "Android did not grant lasting access to $uri", e)
            fail(tr("Android didn't give permanent access to that drive. Try again, and tap “Use this folder”, then “Allow”."), null)
            return
        }
        connect(uri, automatic = false, justPicked = true)
    }

    fun reconnect() {
        val c = _state.value.connection
        val uri = (c as? Connection.Connected)?.session?.treeUri
        if (uri != null) connect(uri, automatic = false) else refresh(auto = true)
    }

    private fun connect(treeUri: Uri, automatic: Boolean, justPicked: Boolean = false) {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            val previous = _state.value.connection
            // Let an earlier backup finish before its files are replaced.
            afterConnectJob?.join()
            _state.update { it.copy(connection = Connection.Working(tr("Connecting…")), writeCheck = null) }
            try {
                val session = repo.connect(treeUri) { step ->
                    _state.update { s -> s.copy(connection = Connection.Working(step)) }
                }
                // Deleted books stay hidden until the Kobo has caught up and stopped listing them.
                val stillListed = _state.value.removedIds.filter { it in session.snapshot.booksById }.toSet()
                saveRemoved(stillListed)
                _state.update {
                    it.copy(
                        connection = Connection.Connected(session),
                        backups = repo.backups.list(),
                        afterConnect = null,
                        afterConnectRunning = true,
                        removedIds = stillListed,
                    )
                }
                runCatching { library.rememberKoboCollections(session.snapshot.collections.map { c -> c.name }) }
                    .onFailure { AppLog.w("Library", "Couldn't remember the Kobo's collections", it) }
                afterConnectJob = viewModelScope.launch {
                    var synced = false
                    try {
                        // A copy to browse while the Kobo is unplugged, with its covers.
                        withContext(Dispatchers.IO) { repo.saveLastSeen(session, session.snapshot.without(stillListed)) }
                        val result = repo.afterConnect(session)
                        _state.update {
                            it.copy(
                                afterConnect = result,
                                afterConnectRunning = false,
                                backups = repo.backups.list(),
                                message = when {
                                    result.backupError != null -> tr("The database backup failed: {0}", result.backupError)
                                    result.integrity != "ok" -> tr("The Kobo's database failed its health check. The log has details.")
                                    result.backup != null -> tr("Backed up your Kobo's database.")
                                    else -> it.message
                                },
                            )
                        }
                        keepCovers(session)
                        // Collections and details (only on a database that's backed up and healthy: the sync
                        // checks). Sends waiting for their collections to be saved are let go either way.
                        syncWithKobo()
                        synced = true
                        readBookTags(session)
                    } finally {
                        if (!synced) {
                            _state.update { it.copy(afterConnectRunning = false) }
                            releaseSyncWaiters()
                        }
                    }
                }
            } catch (e: KoboNotFoundException) {
                AppLog.w("Kobo", "Connect failed: ${e.message}")
                // A wrong folder was picked: don't keep a permission we have no use for.
                if (justPicked) repo.releasePermission(treeUri)
                fail(e.message ?: tr("That drive doesn't look like a Kobo."), null)
            } catch (e: FileNotFoundException) {
                AppLog.w("Kobo", "Connect failed: drive not reachable (${e.message})")
                if (automatic && previous is Connection.Connected) {
                    _state.update { it.copy(connection = previous.copy(unplugged = true)) }
                } else {
                    fail(tr("The Kobo was disconnected while it was being read. Plug it back in and tap Connect on the Kobo."), null)
                }
            } catch (e: SecurityException) {
                AppLog.e("Kobo", "Connect failed: permission", e)
                fail(tr("Android no longer allows access to this drive. Tap “Choose drive” to give access again."), null)
            } catch (e: Exception) {
                AppLog.e("Kobo", "Connect failed", e)
                fail(tr("Something went wrong reading the Kobo: {0}: {1}. The log has details.", e.javaClass.simpleName, e.message), null)
            }
        }
    }

    private var coversJob: Job? = null

    /** Copies every book's small cover to the tablet, so they show while the Kobo is unplugged. */
    private fun keepCovers(session: KoboSession) {
        coversJob?.cancel()
        coversJob = viewModelScope.launch {
            val ids = session.snapshot.books.map { it.imageId }
            val copied = session.covers.keepThumbnails(ids) {
                (_state.value.connection as? Connection.Connected)?.let { c -> c.session === session && !c.unplugged } == true
            }
            if (copied > 0) AppLog.i("Covers", "Kept $copied covers on this device for when the Kobo is unplugged.")
        }
    }

    private var tagsJob: Job? = null

    /**
     * Reads the tags of the Kobo's books that aren't in the library from their files, in the background.
     * Only books not read before (or changed since) are opened, so after the first time this is quick.
     */
    private fun readBookTags(session: KoboSession) {
        tagsJob?.cancel()
        tagsJob = viewModelScope.launch {
            val stillConnected = {
                (_state.value.connection as? Connection.Connected)?.let { c -> !c.unplugged && c.session.tree === session.tree } == true
            }
            val libraryPaths = library.books.value.mapNotNull { it.koboPath?.lowercase() }.toSet()
            val books = session.snapshot.books.filter { b -> b.relativePath?.lowercase() !in libraryPaths }
            try {
                val result = KoboTagReader.read(session, books, _state.value.koboTags) { done, total, found ->
                    if (!stillConnected()) throw kotlinx.coroutines.CancellationException(tr("The Kobo was disconnected"))
                    _state.update { it.copy(readingTags = (done to total).takeIf { done < total }, koboTags = HashMap(found)) }
                    if (done % 200 == 0) repo.bookTags.save(found)
                }
                // Books in the library use the library's tags; keep what's known about them anyway.
                val merged = _state.value.koboTags.filterKeys { it in libraryPaths } + result
                repo.bookTags.save(merged)
                _state.update { it.copy(koboTags = merged, readingTags = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                repo.bookTags.save(_state.value.koboTags)
                _state.update { it.copy(readingTags = null) }
                throw e
            } catch (e: Exception) {
                AppLog.w("Kobo", "Reading the tags of the Kobo's books stopped", e)
                _state.update { it.copy(readingTags = null) }
            }
        }
    }

    /**
     * Brings the Kobo's collections and book details in line with the library, and collection changes
     * made on the Kobo into the library. Writes the Kobo's database only when there's something to change,
     * and never one that wasn't backed up or failed its health check.
     */
    fun syncWithKobo() {
        val waiting = synchronized(syncWaiters) { syncWaiters.toList().also { syncWaiters.clear() } }
        val c = _state.value.connection as? Connection.Connected
        if (c == null || c.unplugged) {
            waiting.forEach { it.complete(Unit) }
            return
        }
        if (_state.value.afterConnectRunning) {
            // Nothing is saved until the database just read is backed up and checked. The sync after
            // that check takes these over, so books sent straight after plugging in aren't "Sent" until
            // their collections are saved.
            synchronized(syncWaiters) { syncWaiters.addAll(waiting) }
            return
        }
        viewModelScope.launch {
            // Whatever waits (a send saying "Sent") can go on once the Kobo has its changes.
            val release = { waiting.forEach { it.complete(Unit) } }
            try {
                sync(onKoboDone = release)
            } finally {
                release()
            }
        }
    }

    private fun releaseSyncWaiters() {
        synchronized(syncWaiters) { syncWaiters.toList().also { syncWaiters.clear() } }.forEach { it.complete(Unit) }
    }

    /** The collections the Kobo was last said not to have, and the connection that was in. */
    private var missingTold: Pair<Long, List<String>>? = null

    private suspend fun sync(onKoboDone: () -> Unit = {}) {
        syncLock.withLock {
            val conn = _state.value.connection as? Connection.Connected ?: return@withLock
            if (conn.unplugged) return@withLock
            val checked = _state.value.afterConnect
            if (checked == null || checked.integrity != "ok" || checked.backupError != null) return@withLock
            val session = conn.session
            _state.update { it.copy(syncing = true) }
            try {
                // "Don't eject" lasts only while the Kobo is being read or written; what's left is done on the tablet.
                val outcome = syncRunner.run(session) {
                    _state.update { it.copy(syncing = false) }
                    onKoboDone()
                }
                if (outcome.session !== session) {
                    _state.update { s ->
                        val now = s.connection as? Connection.Connected
                        if (now?.session === session) s.copy(connection = now.copy(session = outcome.session)) else s
                    }
                    withContext(Dispatchers.IO) {
                        repo.saveLastSeen(outcome.session, outcome.session.snapshot.without(_state.value.removedIds))
                    }
                    runCatching { library.rememberKoboCollections(outcome.session.snapshot.collections.map { it.name }) }
                }
                // Collections the Kobo doesn't have are told of once a connection, not after every change.
                val mention = outcome.missingCollections.isNotEmpty() && missingTold != session.connectedAtMillis to outcome.missingCollections
                if (outcome.missingCollections.isNotEmpty()) missingTold = session.connectedAtMillis to outcome.missingCollections
                outcome.describe(mentionMissing = mention)?.let { msg -> _state.update { it.copy(message = msg) } }
            } catch (e: KoboDatabaseChangedException) {
                AppLog.w("Sync", e.message ?: "The Kobo's database changed")
                _state.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                AppLog.e("Sync", "Updating the Kobo's database failed", e)
                val why = (e.message ?: e.javaClass.simpleName).trimEnd('.')
                _state.update { it.copy(message = tr("Couldn't update the Kobo's collections and details: {0}. No changes were made to the Kobo.", why)) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    private fun saveRemoved(ids: Set<String>) {
        removedPrefs.edit().putStringSet("ids", ids).apply()
    }

    /** Deletes sideloaded books from the Kobo (their files and cached covers). */
    fun deleteFromKobo(books: List<KoboBook>) {
        val c = _state.value.connection as? Connection.Connected ?: return
        if (c.unplugged) return
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = tr("Deleting from the Kobo…")) }
            val deleted = sender.delete(c.session, books)
            val ids = _state.value.removedIds + deleted.map { it.contentId }
            saveRemoved(ids)
            val failed = books.count { it.isSideloaded } - deleted.size
            _state.update {
                it.copy(
                    busyMessage = null,
                    removedIds = ids,
                    message = buildString {
                        append(if (deleted.size == 1) tr("Deleted “{0}” from the Kobo.", deleted[0].title) else tr("Deleted {0} books from the Kobo.", deleted.size))
                        if (failed > 0) append(tr(" {0} couldn't be deleted. The log has the details.", failed))
                        if (deleted.isNotEmpty()) append(tr(" Eject the Kobo so it can update its library."))
                    },
                )
            }
            library.forgetKoboPaths(deleted.mapNotNull { it.relativePath })
            withContext(Dispatchers.IO) { repo.saveLastSeen(c.session, c.session.snapshot.without(ids)) }
        }
    }

    private fun fail(message: String, candidate: VolumeCandidate?) {
        val best = candidate ?: runCatching { repo.removableVolumes().firstOrNull { !it.isLikelySdCard } }.getOrNull()
        _state.update { it.copy(connection = Connection.Failed(message, best)) }
    }

    fun forgetKobo() {
        repo.forgetKobo()
        autoTried.clear()
        _state.update { it.copy(connection = Connection.Waiting(false), writeCheck = null) }
        refresh(auto = false)
    }

    fun runWriteCheck() {
        val session = (_state.value.connection as? Connection.Connected)?.session ?: return
        if (_state.value.writeCheckRunning) return
        viewModelScope.launch {
            _state.update { it.copy(writeCheckRunning = true, writeCheck = null) }
            val result = repo.writeCheck(session)
            _state.update { it.copy(writeCheckRunning = false, writeCheck = result, message = result.message) }
        }
    }

    fun backupNow() {
        val session = (_state.value.connection as? Connection.Connected)?.session ?: return
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = tr("Backing up…")) }
            val msg = try {
                val b = repo.backupNow(session)
                if (b != null) tr("Backed up the Kobo's database.") else tr("Nothing has changed since the last backup.")
            } catch (e: Exception) {
                AppLog.e("Backup", "Backup failed", e)
                tr("Backup failed: {0}", e.message)
            }
            _state.update { it.copy(busyMessage = null, message = msg, backups = repo.backups.list()) }
        }
    }

    fun exportBackup(backup: DatabaseBackup) {
        viewModelScope.launch {
            val msg = try {
                val where = withContext(Dispatchers.IO) { repo.backups.exportToDownloads(backup) }
                AppLog.i("Backup", "Exported ${backup.file.name} to $where.")
                tr("Saved to {0}.", where)
            } catch (e: Exception) {
                AppLog.e("Backup", "Export failed", e)
                tr("Couldn't save to Downloads: {0}", e.message)
            }
            _state.update { it.copy(message = msg) }
        }
    }

    fun deleteBackup(backup: DatabaseBackup) {
        repo.backups.delete(backup)
        AppLog.i("Backup", "Deleted backup ${backup.file.name}.")
        _state.update { it.copy(backups = repo.backups.list(), message = tr("Backup deleted.")) }
    }

    fun refreshBackups() = _state.update { it.copy(backups = repo.backups.list()) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        val app = getApplication<Application>()
        runCatching { app.unregisterReceiver(usbReceiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (volumeCallback as? StorageManager.StorageVolumeCallback)?.let {
                runCatching { app.getSystemService(StorageManager::class.java).unregisterStorageVolumeCallback(it) }
            }
        }
        super.onCleared()
    }
}
