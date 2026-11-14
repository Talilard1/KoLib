package dev.kobolibrary.app.epub

import dev.kobolibrary.app.tr
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Writes a modified copy of an EPUB. The `mimetype` entry goes first and uncompressed, as the EPUB
 * rules require; every other entry keeps its place. Images and other already-compressed media are
 * stored rather than compressed again, which keeps big comics quick to save.
 */
object EpubWriter {

    private const val MAX_STORED_IN_MEMORY = 48L * 1024 * 1024
    private val storedExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "mp3", "m4a", "m4b", "mp4", "ogg", "zip", "woff", "woff2")

    /**
     * @param replace new contents for existing entries (by zip path)
     * @param add new entries (added at the end, unless they already exist, in which case they replace)
     * @param transform optional per-entry rewrite applied to entries not in [replace]; return null to keep as is
     */
    fun rewrite(
        source: File,
        target: File,
        replace: Map<String, ByteArray> = emptyMap(),
        add: Map<String, ByteArray> = emptyMap(),
        remove: Set<String> = emptySet(),
        transform: ((name: String, bytes: () -> ByteArray) -> ByteArray?)? = null,
    ) {
        val all = replace + add
        ZipFile(source).use { zip ->
            ZipOutputStream(target.outputStream().buffered(256 * 1024)).use { out ->
                out.setLevel(Deflater.DEFAULT_COMPRESSION)
                val written = HashSet<String>()

                val mimetype = all["mimetype"]
                    ?: zip.getEntry("mimetype")?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
                    ?: "application/epub+zip".toByteArray()
                // No timestamp extras on mimetype: strict readers want it bare.
                writeStored(out, "mimetype", mimetype, null)
                written += "mimetype"

                for (entry in zip.entries()) {
                    val name = entry.name
                    if (name in written || name in remove) continue
                    written += name
                    if (entry.isDirectory) {
                        out.putNextEntry(ZipEntry(name).apply { time = entry.time })
                        out.closeEntry()
                        continue
                    }
                    val replacement = all[name]
                        ?: transform?.invoke(name) { zip.getInputStream(entry).use { it.readBytes() } }
                    if (replacement != null) {
                        write(out, name, replacement, entry.time)
                    } else {
                        copy(out, name, entry, zip)
                    }
                }
                for ((name, bytes) in add) {
                    if (name in written) continue
                    written += name
                    write(out, name, bytes, null)
                }
            }
        }
    }

    private fun shouldStore(name: String) = name.substringAfterLast('.').lowercase() in storedExtensions

    private fun write(out: ZipOutputStream, name: String, bytes: ByteArray, time: Long?) {
        if (shouldStore(name)) {
            writeStored(out, name, bytes, time)
        } else {
            out.putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.DEFLATED
                if (time != null && time > 0) this.time = time
            })
            out.write(bytes)
            out.closeEntry()
        }
    }

    private fun writeStored(out: ZipOutputStream, name: String, bytes: ByteArray, time: Long?) {
        val crc = CRC32().apply { update(bytes) }
        out.putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
            if (time != null && time > 0) this.time = time
        })
        out.write(bytes)
        out.closeEntry()
    }

    private fun copy(out: ZipOutputStream, name: String, entry: ZipEntry, zip: ZipFile) {
        if (shouldStore(name) && entry.size in 0..MAX_STORED_IN_MEMORY) {
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            writeStored(out, name, bytes, entry.time)
            return
        }
        out.putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            if (entry.time > 0) time = entry.time
        })
        zip.getInputStream(entry).use { input: InputStream -> input.copyTo(out, 128 * 1024) }
        out.closeEntry()
    }

    /** Writes a new EPUB from scratch: `mimetype` first and bare, then [entries] in order. */
    fun create(target: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(target.outputStream().buffered(256 * 1024)).use { out ->
            out.setLevel(Deflater.DEFAULT_COMPRESSION)
            writeStored(out, "mimetype", "application/epub+zip".toByteArray(), null)
            val written = hashSetOf("mimetype")
            for ((name, bytes) in entries) {
                if (!written.add(name)) continue
                write(out, name, bytes, null)
            }
        }
    }

    /** Replaces [file] with a rewritten copy, safely: the original is only replaced once the copy is complete. */
    fun rewriteInPlace(
        file: File,
        replace: Map<String, ByteArray> = emptyMap(),
        add: Map<String, ByteArray> = emptyMap(),
        remove: Set<String> = emptySet(),
        transform: ((name: String, bytes: () -> ByteArray) -> ByteArray?)? = null,
    ) {
        val tmp = File(file.parentFile, file.name + ".saving")
        tmp.delete()
        try {
            rewrite(file, tmp, replace, add, remove, transform)
            // Make sure the result opens before it replaces the original.
            EpubFile.open(tmp).close()
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw java.io.IOException(tr("Couldn't replace {0}", file.name))
            }
        } finally {
            tmp.delete()
        }
    }
}
