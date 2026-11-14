package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import dev.kobolibrary.app.backup.BackupManager
import dev.kobolibrary.app.backup.DatabaseBackup
import dev.kobolibrary.app.kobo.db.AndroidSqlSource
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.saf.DocEntry
import dev.kobolibrary.app.saf.SafTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.random.Random

/** A removable drive Android currently has mounted (an SD card or a USB device such as the Kobo). */
data class VolumeCandidate(
    val uuid: String?,
    val description: String,
    val volume: StorageVolume,
    /** We already hold permission for this drive's top folder. */
    val grantedTree: Uri?,
    /** This is the drive we connected to before. */
    val isKnownKobo: Boolean,
) {
    val looksLikeKobo: Boolean
        get() = isKnownKobo || description.contains("kobo", ignoreCase = true)

    /** Samsung and AOSP call memory cards "SD card"; USB drives are "USB …". */
    val isLikelySdCard: Boolean
        get() = !looksLikeKobo && description.contains("SD") && !description.contains("USB", ignoreCase = true)
}

data class KoboDeviceInfo(
    val modelName: String,
    val usbProductId: Int?,
    val version: KoboVersionInfo?,
    val volumeUuid: String?,
    val volumeDescription: String?,
    val totalBytes: Long?,
    val freeBytes: Long?,
    /** Whether the tablet lets apps read the Kobo drive as ordinary files (not needed, just useful to know). */
    val directFileAccess: Boolean,
) {
    val displayName: String get() = tr("Kobo {0}", modelName)
}

class KoboSession(
    val treeUri: Uri,
    val tree: SafTree,
    val info: KoboDeviceInfo,
    val snapshot: KoboSnapshot,
    val connectedAtMillis: Long,
    val databaseBytes: Long,
    val databaseHadWal: Boolean,
    val covers: KoboCoverLoader,
    /** Byte-for-byte copy of the Kobo's database, kept untouched for the backup. */
    internal val pristineDb: File,
    internal val pristineWal: File?,
    /** The copy the app opened to read books from. */
    internal val workDb: File,
) {
    /** The same connection after the app changed the Kobo's database: now [db], read as [snapshot]. */
    fun afterWrite(db: File, readCopy: File, snapshot: KoboSnapshot) = KoboSession(
        treeUri = treeUri,
        tree = tree,
        info = info,
        snapshot = snapshot,
        connectedAtMillis = connectedAtMillis,
        databaseBytes = db.length(),
        databaseHadWal = false,
        covers = covers,
        pristineDb = db,
        pristineWal = null,
        workDb = readCopy,
    )
}

/** What happened in the background after connecting. */
data class AfterConnect(val backup: DatabaseBackup?, val backupError: String?, val integrity: String)

class KoboNotFoundException(message: String) : Exception(message)

data class WriteCheckResult(val ok: Boolean, val message: String)

/**
 * Everything to do with finding the Kobo, getting Android's permission to use it,
 * and reading it. Nothing here changes anything on the Kobo except the explicit write check.
 */
class KoboRepository(private val context: Context) {

    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("kobo", Context.MODE_PRIVATE)
    private val storageManager = context.getSystemService(StorageManager::class.java)
    val backups = BackupManager(context)

    /** A copy of what was on the Kobo at the last connection, for browsing while it's unplugged. */
    val lastSeen = LastSeenStore(File(context.filesDir, "kobo"))

    /** Tags read from the Kobo's book files (its database doesn't keep them). */
    val bookTags = KoboTagStore(File(context.filesDir, "kobo/book-tags.json"))
    private val writer by lazy { KoboDatabaseWriter(File(context.cacheDir, "kobo-write"), backups) }

    /**
     * Changes the Kobo's database (safely: see [KoboDatabaseWriter]). Returns the connection as it is
     * afterwards, or null if [edit] changed nothing.
     */
    suspend fun writeDatabase(session: KoboSession, edit: (KoboDbEditor) -> Int): KoboSession? = withContext(Dispatchers.IO) {
        val written = writer.write(session.tree, session.pristineDb, session.pristineWal, session.info.displayName, edit)
            ?: return@withContext null
        val read = File(written.database.parentFile, "read.sqlite")
        written.database.copyTo(read, overwrite = true)
        val snapshot = openLocal(read).use { db -> KoboDatabaseReader(AndroidSqlSource(db)).read() }
        listOf("-wal", "-shm", "-journal").forEach { File(read.path + it).delete() }
        session.afterWrite(written.database, read, snapshot)
    }

    /** What was on the Kobo the last time it was connected, with covers kept on the tablet. */
    fun loadLastSeen(): KoboView? = lastSeen.load()?.let { s ->
        KoboView(s.info, s.snapshot, KoboCoverLoader(null, lastSeen.coverDir), s.readAtMillis, s.databaseBytes, s.databaseHadWal, null)
    }

    fun saveLastSeen(session: KoboSession, snapshot: KoboSnapshot = session.snapshot) = lastSeen.save(
        LastSeenStore.LastSeen(session.info, snapshot, session.connectedAtMillis, session.databaseBytes, session.databaseHadWal)
    )

    var knownKoboUuid: String?
        get() = prefs.getString(KEY_UUID, null)
        private set(value) = prefs.edit().putString(KEY_UUID, value).apply()

    /** Mounted removable drives, best Kobo candidate first. */
    fun removableVolumes(): List<VolumeCandidate> {
        val granted = grantedTrees()
        val known = knownKoboUuid
        return storageManager.storageVolumes
            .filter { it.isRemovable && !it.isPrimary && it.state == Environment.MEDIA_MOUNTED }
            .map { v ->
                val uuid = v.uuid
                VolumeCandidate(
                    uuid = uuid,
                    description = v.getDescription(context) ?: tr("Removable drive"),
                    volume = v,
                    grantedTree = uuid?.let { id -> granted.firstOrNull { treeDocId(it) == "$id:" } },
                    isKnownKobo = uuid != null && uuid == known,
                )
            }
            .sortedWith(compareByDescending<VolumeCandidate> { it.isKnownKobo }.thenByDescending { it.looksLikeKobo })
    }

    /** USB product id of an attached Kobo, if Android lists one (no permission needed to look). */
    fun attachedKoboUsbProductId(): Int? = try {
        val usb = context.getSystemService(UsbManager::class.java)
        usb?.deviceList?.values?.firstOrNull { it.vendorId == KoboModels.USB_VENDOR_ID }?.productId
    } catch (e: Exception) {
        AppLog.w("Kobo", "Could not list USB devices: ${e.message}")
        null
    }

    fun openTreeIntent(candidate: VolumeCandidate?): Intent =
        candidate?.volume?.createOpenDocumentTreeIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    fun takePermission(treeUri: Uri) {
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Gives back access to a drive that turned out not to be the Kobo. */
    fun releasePermission(treeUri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun forgetKobo() {
        val known = knownKoboUuid
        for (uri in grantedTrees()) {
            if (known == null || treeDocId(uri) == "$known:") {
                runCatching {
                    resolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
        }
        prefs.edit().clear().apply()
        AppLog.i("Kobo", "Forgot the Kobo and released its drive permission.")
    }

    /**
     * Reads the Kobo: version file, database (copied to the tablet), books and collections.
     * The backup and health check run afterwards in [afterConnect], so the books appear sooner.
     */
    suspend fun connect(treeUri: Uri, progress: (String) -> Unit): KoboSession = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val tree = SafTree(resolver, treeUri)
        AppLog.i("Kobo", "Connecting via drive root '${tree.rootDocId}'.")

        progress(tr("Checking the drive…"))
        if (tree.stat(tree.rootDocId) == null) {
            throw KoboNotFoundException(tr("The Kobo's drive isn't available. Plug the Kobo in and tap Connect on its screen."))
        }
        val koboDir = tree.child(tree.rootDocId, ".kobo")
            ?: throw KoboNotFoundException(
                tr("That drive has no .kobo folder, so it doesn't look like a Kobo. Choose the top level of the drive called KOBOeReader.")
            )

        progress(tr("Loading device details…"))
        val version = tree.child(koboDir.docId, "version")?.let { entry ->
            runCatching { tree.openInput(entry.docId).use { it.readBytes().toString(Charsets.UTF_8) } }
                .onFailure { AppLog.w("Kobo", "Could not read .kobo/version: ${it.message}") }
                .getOrNull()
                ?.let { KoboVersionInfo.parse(it) }
        }
        val usbProductId = attachedKoboUsbProductId()
        val volume = removableVolumes().firstOrNull { it.uuid != null && tree.rootDocId == "${it.uuid}:" }
        val (total, free) = spaceOf(volume?.volume)
        val direct = directAccess(volume?.volume)
        val model = KoboModels.name(usbProductId, version?.modelId) ?: "eReader"
        val info = KoboDeviceInfo(
            modelName = model,
            usbProductId = usbProductId,
            version = version,
            volumeUuid = volume?.uuid ?: tree.rootDocId.substringBefore(':').takeIf { tree.rootDocId.endsWith(":") },
            volumeDescription = volume?.description,
            totalBytes = total,
            freeBytes = free,
            directFileAccess = direct,
        )
        AppLog.i(
            "Kobo",
            "Device: ${info.displayName}; firmware ${version?.firmware ?: "?"}; model id ${version?.modelId ?: "?"}; " +
                "serial ${version?.maskedSerial ?: "?"}; USB product ${usbProductId?.let { "0x%04x".format(it) } ?: "not listed"}; " +
                "drive '${volume?.description ?: "?"}' (${info.volumeUuid ?: "?"}); " +
                "space ${free?.let { "$it free of $total" } ?: "unknown"}; direct file access ${if (direct) "yes" else "no"}."
        )

        val dbEntry = tree.child(koboDir.docId, "KoboReader.sqlite")
            ?: recoverInterruptedSwap(tree, koboDir.docId)
            ?: throw KoboNotFoundException(tr("The Kobo's database file (KoboReader.sqlite) is missing. Has the Kobo finished setting up?"))
        // Each connection gets its own folder, so a backup still running from an earlier one is never disturbed.
        val base = File(context.cacheDir, "kobo").apply { mkdirs() }
        base.listFiles()?.filter { it.lastModified() < started - 60_000 }?.forEach { it.deleteRecursively() }
        val work = File(base, started.toString()).apply { mkdirs() }
        val pristine = File(work, "KoboReader.sqlite")
        val pristineWal = File(work, "KoboReader.sqlite-wal")
        val copyStart = System.currentTimeMillis()
        val totalBytes = dbEntry.size.takeIf { it > 0 }
        tree.openInput(dbEntry.docId).use { input ->
            pristine.outputStream().use { out ->
                val buffer = ByteArray(512 * 1024)
                var copied = 0L
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (totalBytes != null) {
                        val percent = (copied * 100 / totalBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            progress(tr("Copying the Kobo's database… {0}%", percent))
                        }
                    }
                }
            }
        }
        val walEntry = tree.child(koboDir.docId, "KoboReader.sqlite-wal")?.takeIf { it.size > 0 }
        walEntry?.let { entry -> tree.openInput(entry.docId).use { input -> pristineWal.outputStream().use { input.copyTo(it) } } }
        AppLog.i(
            "Kobo",
            "Copied database: ${pristine.length()} bytes in ${System.currentTimeMillis() - copyStart} ms" +
                (if (walEntry != null) ", plus a ${pristineWal.length()}-byte WAL file" else ", no WAL file") +
                ". Direct path lookups ${if (tree.directIdsWork == true) "work" else "not used"}."
        )

        progress(tr("Loading books and collections…"))
        // Read from a second copy: opening a database can change its file, and the backup must be exact.
        val workDb = File(work, "read.sqlite")
        pristine.copyTo(workDb, overwrite = true)
        if (walEntry != null) pristineWal.copyTo(File(work, "read.sqlite-wal"), overwrite = true)
        val snapshot = openLocal(workDb).use { db -> KoboDatabaseReader(AndroidSqlSource(db)).read() }
        AppLog.i(
            "Kobo",
            "Database version ${snapshot.dbVersion}. " +
                "${snapshot.books.size} books on device (${snapshot.sideloadedCount} sideloaded, ${snapshot.purchasedOnDeviceCount} Kobo store), " +
                "${snapshot.purchasedInCloudCount} store books not downloaded, ${snapshot.previewCount} previews, " +
                "${snapshot.collections.size} collections. Read in ${System.currentTimeMillis() - started} ms."
        )
        snapshot.warnings.forEach { AppLog.w("Kobo", it) }

        knownKoboUuid = info.volumeUuid ?: knownKoboUuid
        prefs.edit().putString(KEY_TREE, treeUri.toString()).putString(KEY_NAME, info.displayName).apply()

        KoboSession(
            treeUri = treeUri,
            tree = tree,
            info = info,
            snapshot = snapshot,
            connectedAtMillis = System.currentTimeMillis(),
            databaseBytes = pristine.length(),
            databaseHadWal = walEntry != null,
            covers = KoboCoverLoader(tree, lastSeen.coverDir),
            pristineDb = pristine,
            pristineWal = pristineWal.takeIf { walEntry != null },
            workDb = workDb,
        )
    }

    /**
     * If the app was putting a changed database in place when the Kobo was unplugged, the database may
     * be under its temporary name: the new one (already checked, so complete) or the old one set aside.
     * Puts it back under its real name.
     */
    private fun recoverInterruptedSwap(tree: SafTree, koboDirId: String): DocEntry? {
        for (name in listOf("KoboReader.sqlite.kl-new", "KoboReader.sqlite.kl-old")) {
            val entry = tree.child(koboDirId, name)?.takeIf { it.size > 0 } ?: continue
            val id = tree.rename(entry.docId, "KoboReader.sqlite") ?: continue
            AppLog.w("Kobo", "The Kobo's database was found as $name after an interrupted change, and was put back.")
            return tree.stat(id)
        }
        return null
    }

    /** Backs up the copied database (if it changed) and checks it for damage. Runs after [connect]. */
    suspend fun afterConnect(session: KoboSession): AfterConnect = withContext(Dispatchers.IO) {
        backups.compressOldBackups()
        var error: String? = null
        val backup = try {
            backups.backupIfChanged(session.pristineDb, session.pristineWal, session.info.displayName)
        } catch (e: Exception) {
            AppLog.e("Backup", "Backup failed", e)
            error = e.message ?: e.javaClass.simpleName
            null
        }
        val integrity = runCatching {
            SQLiteDatabase.openDatabase(session.workDb.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.rawQuery("PRAGMA quick_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "no result" }
            }
        }.getOrElse { tr("check failed: {0}", it.message) }
        AppLog.i("Kobo", "Database health check: $integrity.")
        AfterConnect(backup, error, integrity)
    }

    /** Makes a fresh copy of the database and backs it up if it changed. */
    suspend fun backupNow(session: KoboSession): DatabaseBackup? = withContext(Dispatchers.IO) {
        val tree = session.tree
        val koboDir = tree.child(tree.rootDocId, ".kobo") ?: throw KoboNotFoundException(tr("The Kobo is no longer connected."))
        val dbEntry = tree.child(koboDir.docId, "KoboReader.sqlite") ?: throw KoboNotFoundException(tr("The Kobo's database is missing."))
        val work = File(context.cacheDir, "kobo-backup").apply { mkdirs() }
        val db = File(work, "KoboReader.sqlite")
        val wal = File(work, "KoboReader.sqlite-wal").apply { delete() }
        tree.openInput(dbEntry.docId).use { input -> db.outputStream().use { input.copyTo(it, 256 * 1024) } }
        val walEntry = tree.child(koboDir.docId, "KoboReader.sqlite-wal")?.takeIf { it.size > 0 }
        walEntry?.let { e -> tree.openInput(e.docId).use { input -> wal.outputStream().use { input.copyTo(it) } } }
        backups.backupIfChanged(db, wal.takeIf { walEntry != null }, session.info.displayName)
    }

    /**
     * Proves the app can write to the Kobo, which later features need: creates a small hidden file in the
     * drive's top folder, reads it back, and deletes it. The Kobo ignores hidden files.
     */
    suspend fun writeCheck(session: KoboSession): WriteCheckResult = withContext(Dispatchers.IO) {
        val tree = session.tree
        val name = ".kobolibrary-write-check.tmp"
        val started = System.currentTimeMillis()
        try {
            tree.child(tree.rootDocId, name)?.let { tree.delete(it.docId) }
            val id = tree.createFile(tree.rootDocId, "application/octet-stream", name)
                ?: return@withContext WriteCheckResult(false, tr("Android didn't allow creating a file on the Kobo."))
            val data = Random.nextBytes(64 * 1024)
            tree.openOutput(id, "w").use { it.write(data) }
            val back = tree.openInput(id).use { it.readBytes() }
            val deleted = tree.delete(id)
            val ms = System.currentTimeMillis() - started
            val created = tree.stat(id)?.name
            when {
                !back.contentEquals(data) ->
                    WriteCheckResult(false, tr("A test file was written, but it read back differently. Writing to the Kobo isn't reliable."))
                !deleted ->
                    WriteCheckResult(false, tr("A test file was written and read back, but couldn't be deleted. It's hidden and harmless: {0}.", created ?: name))
                else ->
                    WriteCheckResult(true, tr("Writing works: a 64 KB test file was written, read back and deleted in {0} ms.", ms))
            }.also { AppLog.i("WriteCheck", it.message) }
        } catch (e: FileNotFoundException) {
            AppLog.e("WriteCheck", "Write check failed", e)
            WriteCheckResult(false, tr("The Kobo isn't reachable any more. Is it still connected?"))
        } catch (e: SecurityException) {
            AppLog.e("WriteCheck", "Write check failed", e)
            WriteCheckResult(false, tr("Android didn't give this app write access to the Kobo. Try “Forget this Kobo” and connect again."))
        } catch (e: Exception) {
            AppLog.e("WriteCheck", "Write check failed", e)
            WriteCheckResult(false, tr("Write check failed: {0}: {1}", e.javaClass.simpleName, e.message))
        }
    }

    private fun openLocal(file: File): SQLiteDatabase =
        // Read-write on our private copy so SQLite can apply a WAL file if there was one.
        // NO_LOCALIZED_COLLATORS stops Android adding its own table to the copy.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)

    private fun grantedTrees(): List<Uri> =
        resolver.persistedUriPermissions.filter { it.isReadPermission && it.isWritePermission }.map { it.uri }

    private fun treeDocId(uri: Uri): String? = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

    private fun spaceOf(volume: StorageVolume?): Pair<Long?, Long?> {
        if (volume == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null to null
        return try {
            val dir = volume.directory ?: return null to null
            val stat = StatFs(dir.path)
            stat.totalBytes to stat.availableBytes
        } catch (e: Exception) {
            null to null
        }
    }

    private fun directAccess(volume: StorageVolume?): Boolean {
        if (volume == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val dir = volume.directory ?: return false
            File(dir, ".kobo/version").canRead()
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val KEY_UUID = "kobo_volume_uuid"
        const val KEY_TREE = "kobo_tree_uri"
        const val KEY_NAME = "kobo_name"
    }
}
