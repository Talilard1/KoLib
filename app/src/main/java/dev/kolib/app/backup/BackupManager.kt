package dev.kolib.app.backup

import dev.kolib.app.tr
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.kolib.app.log.AppLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DatabaseBackup(
    /** A .zip holding KoboReader.sqlite (and its -wal file if there was one). Older versions saved bare .sqlite files. */
    val file: File,
    val createdMillis: Long,
    val deviceLabel: String,
    val hasWal: Boolean,
) {
    val sizeBytes: Long get() = file.length() + (legacyWal?.length() ?: 0L)
    val isCompressed: Boolean get() = file.name.endsWith(".zip")
    internal val legacyWal: File? get() = if (isCompressed) null else File(file.path + "-wal").takeIf { it.exists() }
}

/**
 * Keeps compressed copies of the Kobo's KoboReader.sqlite on the tablet.
 * A new copy is made only when the database has changed since the last one.
 */
class BackupManager(private val context: Context) {

    val folder: File = File(context.filesDir, "backups").apply { mkdirs() }

    fun list(): List<DatabaseBackup> =
        (folder.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".zip") || it.name.endsWith(".sqlite")) }
            .map { f ->
                DatabaseBackup(
                    file = f,
                    createdMillis = f.lastModified(),
                    deviceLabel = sidecar(f, "label")?.readText()?.trim() ?: tr("Kobo"),
                    hasWal = sidecar(f, "wal") != null || File(f.path + "-wal").exists(),
                )
            }
            .sortedWith(compareByDescending<DatabaseBackup> { it.createdMillis }.thenByDescending { sameSecondIndex(it.file.name) })

    /** Returns the new backup, or null if the newest backup already has identical contents. */
    fun backupIfChanged(database: File, wal: File?, deviceLabel: String): DatabaseBackup? {
        val started = System.currentTimeMillis()
        val hash = hashOf(database, wal)
        val newest = list().firstOrNull()
        if (newest != null && sidecar(newest.file, "sha256")?.readText()?.trim() == hash) {
            AppLog.i("Backup", "Database unchanged since the backup of ${stamp(newest.createdMillis)}; no new copy needed.")
            return null
        }
        val base = "KoboReader-${fileSafe(deviceLabel)}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}"
        var target = File(folder, "$base.zip")
        var n = 2
        while (target.exists()) target = File(folder, "$base-${n++}.zip")
        val partial = File(folder, target.name + ".part")
        try {
            ZipOutputStream(partial.outputStream().buffered(256 * 1024)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED) // fast, and still about a third of the size
                for ((file, name) in listOfNotNull(database to "KoboReader.sqlite", wal?.let { it to "KoboReader.sqlite-wal" })) {
                    zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(zip, 256 * 1024) }
                    zip.closeEntry()
                }
            }
            if (!partial.renameTo(target)) throw IOException(tr("Couldn't finish the backup file"))
        } finally {
            partial.delete()
        }
        sidecarFile(target, "sha256").writeText(hash)
        sidecarFile(target, "label").writeText(deviceLabel)
        if (wal != null) sidecarFile(target, "wal").writeText("1")
        prune()
        AppLog.i(
            "Backup",
            "Saved database backup ${target.name}: ${database.length()} bytes compressed to ${target.length()} " +
                "in ${System.currentTimeMillis() - started} ms${if (wal != null) " (with WAL)" else ""}."
        )
        return list().firstOrNull { it.file == target }
    }

    /** Version 0.1 saved uncompressed copies; squeeze them into zips once. */
    fun compressOldBackups() {
        for (b in list().filter { !it.isCompressed }) {
            try {
                val target = File(folder, b.file.name.removeSuffix(".sqlite") + ".zip")
                val partial = File(folder, target.name + ".part")
                ZipOutputStream(partial.outputStream().buffered()).use { zip ->
                    zip.setLevel(Deflater.BEST_SPEED)
                    for ((file, name) in listOfNotNull(b.file to "KoboReader.sqlite", b.legacyWal?.let { it to "KoboReader.sqlite-wal" })) {
                        zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                partial.renameTo(target)
                target.setLastModified(b.createdMillis)
                for (ext in listOf("sha256", "label")) File(b.file.path + ".$ext").takeIf { it.exists() }?.renameTo(sidecarFile(target, ext))
                if (b.legacyWal != null) sidecarFile(target, "wal").writeText("1")
                b.legacyWal?.delete()
                b.file.delete()
                AppLog.i("Backup", "Compressed older backup ${b.file.name} to ${target.length()} bytes.")
            } catch (e: Exception) {
                AppLog.w("Backup", "Couldn't compress older backup ${b.file.name}", e)
            }
        }
    }

    fun delete(backup: DatabaseBackup) {
        backup.legacyWal?.delete()
        for (ext in listOf("sha256", "label", "wal")) {
            sidecarFile(backup.file, ext).delete()
            File(backup.file.path + ".$ext").delete()
        }
        backup.file.delete()
    }

    /** Copies a backup into Downloads/KoLib so it can be kept anywhere. Returns the visible folder name. */
    fun exportToDownloads(backup: DatabaseBackup): String {
        exportFile(backup.file, backup.file.name)
        backup.legacyWal?.let { exportFile(it, it.name) }
        return "Downloads/$EXPORT_FOLDER"
    }

    private fun exportFile(source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, if (displayName.endsWith(".zip")) "application/zip" else "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_FOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException(tr("Android couldn't create {0} in Downloads", displayName))
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IOException(tr("Could not write {0} to Downloads", displayName))
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun prune() {
        list().drop(MAX_BACKUPS).forEach { delete(it) }
    }

    /** Sidecar files sit next to the backup: "<name>.sha256", "<name>.label". */
    private fun sidecarFile(backup: File, ext: String) = File(backup.path + ".$ext")
    private fun sidecar(backup: File, ext: String) = sidecarFile(backup, ext).takeIf { it.exists() }

    private fun hashOf(database: File, wal: File?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        for (f in listOfNotNull(database, wal)) {
            f.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun stamp(millis: Long) = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    private fun fileSafe(s: String) = s.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "Kobo" }

    /** 1 for "…-HHmmss.zip", N for "…-HHmmss-N.zip" (a later backup in the same second). */
    private fun sameSecondIndex(name: String): Int =
        Regex("-\\d{8}-\\d{6}-(\\d+)\\.(zip|sqlite)$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    companion object {
        const val MAX_BACKUPS = 10
        const val EXPORT_FOLDER = "KoLib"
    }
}
