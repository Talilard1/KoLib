package dev.kolib.app.ai

import dev.kolib.app.tr
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The model files kept on the device: the ones KoLib downloaded, and the user's own. */
class ModelStore(val dir: File) {

    /** What a download answers with: its code (200 for the whole file, 206 for the rest of it), and the file's bytes. */
    class Answer(val code: Int, val body: InputStream, val close: () -> Unit = {})

    fun file(model: AiModel) = File(dir, model.fileName)

    private fun part(model: AiModel) = File(dir, model.fileName + ".part")

    fun installed(model: AiModel): Boolean = file(model).let { it.isFile && it.length() == model.bytes }

    /** How much of [model] is here already, from a download that stopped part-way. */
    fun partBytes(model: AiModel): Long = part(model).takeIf { it.isFile }?.length() ?: 0L

    /** What's been downloaded of models so far, that isn't whole yet. */
    fun parts(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf.part") }?.toList().orEmpty()

    /** Lets go of what's been downloaded of [model] so far. */
    fun dropPart(model: AiModel) {
        part(model).delete()
    }

    /** The user's own model files, by name. */
    fun own(): List<File> = (dir.listFiles { f -> f.isFile && f.name.startsWith(OWN) && f.name.endsWith(".gguf") } ?: emptyArray()).sortedBy { it.name.lowercase() }

    fun ownName(file: File): String = file.name.removePrefix(OWN).removeSuffix(".gguf")

    fun ownFile(name: String): File = File(dir, OWN + name + ".gguf")

    fun freeBytes(): Long = (dir.takeIf { it.exists() } ?: dir.parentFile ?: dir).usableSpace

    /** How much the storage the models are kept on holds in all. */
    fun totalBytes(): Long = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.totalSpace ?: 0L

    /** Whether [model] is one found or added by the user, kept with their own. */
    fun isOwn(model: AiModel): Boolean = model.id.startsWith(OWN)

    fun storedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun delete(model: AiModel) {
        file(model).delete()
        part(model).delete()
    }

    /**
     * Downloads [model], carrying on from where an earlier download stopped, and checks the file is the one it
     * should be. [open] fetches its address from a place in the file on; [progress] hears how many bytes are here.
     */
    fun download(
        model: AiModel,
        open: (String, Long) -> Answer = ::fetch,
        stillWanted: () -> Boolean = { true },
        progress: (Long) -> Unit = {},
    ) {
        if (installed(model)) return
        dir.mkdirs()
        val part = part(model)
        var have = part.takeIf { it.isFile }?.length() ?: 0L
        if (have > model.bytes) {
            part.delete()
            have = 0
        }
        if (freeBytes() < model.bytes - have + SPARE) throw IOException(tr("There isn't enough free storage for this model."))
        val digest = MessageDigest.getInstance("SHA-256")
        if (have < model.bytes) {
            val answer = open(model.url, have)
            try {
                // The whole file again, though only the rest was asked for: start over.
                if (answer.code == 200 && have > 0) have = 0
                else if (answer.code != 200 && answer.code != 206) throw IOException(tr("The download failed (error {0}).", answer.code))
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        if (!stillWanted()) throw StoppedException()
                        val n = answer.body.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        have += n
                        progress(have)
                        if (have > model.bytes) break
                    }
                }
            } finally {
                runCatching { answer.close() }
            }
        }
        if (have < model.bytes) throw IOException(tr("The download stopped part-way. Try again to carry on from there."))
        part.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                if (!stillWanted()) throw StoppedException()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val sum = digest.digest().joinToString("") { "%02x".format(it) }
        if (have != model.bytes || !sum.equals(model.sha256, ignoreCase = true)) {
            part.delete()
            throw IOException(tr("The download didn't come through whole. Try again."))
        }
        val file = file(model)
        file.delete()
        if (!part.renameTo(file)) throw IOException(tr("Couldn't save the model."))
    }

    /**
     * Keeps a copy of the user's own model, read from [input], under [name]. It has to be a GGUF file (what
     * llama.cpp runs).
     */
    fun addOwn(name: String, input: InputStream, stillWanted: () -> Boolean = { true }, progress: (Long) -> Unit = {}): File {
        dir.mkdirs()
        val file = ownFile(cleanName(name))
        val part = File(dir, file.name + ".part")
        try {
            part.outputStream().use { out ->
                val buffer = ByteArray(1 shl 16)
                var total = 0L
                var first = true
                while (true) {
                    if (!stillWanted()) throw StoppedException()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (first) {
                        if (n < 4 || String(buffer, 0, 4, Charsets.ISO_8859_1) != "GGUF") throw IOException(tr("That isn't a GGUF model file."))
                        first = false
                    }
                    out.write(buffer, 0, n)
                    total += n
                    progress(total)
                }
                if (first) throw IOException(tr("That isn't a GGUF model file."))
            }
            file.delete()
            if (!part.renameTo(file)) throw IOException(tr("Couldn't save the model."))
            return file
        } finally {
            part.delete()
        }
    }

    /** The download was stopped by the user: what's here is kept, to carry on from. */
    class StoppedException : IOException("stopped")

    companion object {
        private const val OWN = "own-"

        /** A model file's name as one of the user's own is kept under. */
        fun cleanName(name: String): String =
            name.substringAfterLast('/').removeSuffix(".gguf").replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim().take(80).ifEmpty { "model" }

        /** What a model found for the user is known by: downloaded, it's kept with their own, under its file's name. */
        fun ownId(fileName: String): String = OWN + cleanName(fileName)
        /** Storage left free after a download. */
        private const val SPARE = 200L * 1024 * 1024

        /** Over the network: follows where the file has moved to, asking for the same place in it each time. */
        fun fetch(url: String, from: Long): Answer {
            var at = url
            repeat(6) {
                val c = (URL(at).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "KoLib")
                    if (from > 0) setRequestProperty("Range", "bytes=$from-")
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: throw IOException("redirect")
                    c.disconnect()
                    at = URL(URL(at), next).toString()
                } else if (code == 200 || code == 206) {
                    return Answer(code, c.inputStream) { c.disconnect() }
                } else {
                    c.disconnect()
                    return Answer(code, ByteArray(0).inputStream())
                }
            }
            throw IOException("redirects")
        }
    }
}
