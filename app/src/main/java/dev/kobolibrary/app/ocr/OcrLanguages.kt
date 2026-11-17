package dev.kobolibrary.app.ocr

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.log.AppLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The languages text can be recognised in: those KoLib itself is in, and Ancient and Modern Greek and Latin
 * for older books. What the recognition needs for each ("traineddata", Tesseract's quicker kind) is
 * downloaded the first time it's used, from the Tesseract project, and checked against the copy these
 * sizes and checksums came from.
 */
object OcrLanguages {

    class Language(
        /** Tesseract's name for it. */
        val code: String,
        val name: String,
        /** The book languages (ISO codes) that call for it. */
        val bookCodes: Set<String>,
        val bytes: Long,
        val sha256: String,
    )

    /** The version of the data, so every download is the same file. */
    private const val SOURCE = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/"

    val all: List<Language> = listOf(
        Language("eng", tr("English"), setOf("en", "eng"), 4113088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
        Language("fra", tr("French"), setOf("fr", "fre", "fra"), 1130365, "ced037562e8c80c13122dece28dd477d399af80911a28791a66a63ac1e3445ca"),
        Language("deu", tr("German"), setOf("de", "ger", "deu"), 1525436, "19d219bbb6672c869d20a9636c6816a81eb9a71796cb93ebe0cb1530e2cdb22d"),
        Language("spa", tr("Spanish"), setOf("es", "spa"), 2294433, "6f2e04d02774a18f01bed44b1111f2cd7f3ba7ac9dc4373cd3f898a40ea6b464"),
        Language("ita", tr("Italian"), setOf("it", "ita"), 2701314, "b8f89e1e785118dac4d51ae042c029a64edb5c3ee42ef73027a6d412748d8827"),
        Language("nld", tr("Dutch"), setOf("nl", "dut", "nld"), 6050296, "ced0e5e046a84c908a6aa7accbef9a232c4a5d9a8276691b81c6ee64d02963f6"),
        Language("por", tr("Portuguese"), setOf("pt", "por"), 1982756, "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"),
        Language("rus", tr("Russian"), setOf("ru", "rus"), 3861738, "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225"),
        Language("jpn", tr("Japanese"), setOf("ja", "jpn"), 2471260, "1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d"),
        Language("jpn_vert", tr("Japanese, written downwards"), emptySet(), 3037480, "bf1e2640954691797e2dc14f38533e601b59ee37958698ae0f0b81dc6f09c71b"),
        Language("grc", tr("Ancient Greek"), setOf("grc"), 2246328, "57b735557eeb67503803cf9f99786dd0b62417931352601952a0b1a23b6c4759"),
        Language("ell", tr("Modern Greek"), setOf("el", "ell", "gre"), 1419514, "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"),
        Language("lat", tr("Latin"), setOf("la", "lat"), 3187463, "3859d8ba60404f4b79830622625bbc76fb4ee2808eac1ad360ffa77f0a533328"),
    )

    fun byCode(code: String): Language? = all.firstOrNull { it.code == code }

    /** The languages to start with for a book in [bookLanguage]: its own, and English. */
    fun suggested(bookLanguage: String?): List<String> {
        val lang = bookLanguage?.lowercase()?.substringBefore('-')
        val own = all.firstOrNull { lang != null && lang in it.bookCodes }?.code
        return listOfNotNull(own, "eng").distinct()
    }

    /** Where the data is kept, and where Tesseract is told to look ("…/tessdata" inside it). */
    fun root(filesDir: File) = File(filesDir, "ocr")
    private fun folder(filesDir: File) = File(root(filesDir), "tessdata")
    private fun file(filesDir: File, code: String) = File(folder(filesDir), "$code.traineddata")

    fun installed(filesDir: File, code: String): Boolean {
        val l = byCode(code) ?: return false
        return file(filesDir, code).length() == l.bytes
    }

    /** What still has to be downloaded for [codes]. */
    fun missing(filesDir: File, codes: List<String>): List<Language> = codes.mapNotNull { byCode(it) }.filter { !installed(filesDir, it.code) }

    /**
     * Downloads what's missing for [codes]; [progress] is told how much of it has come so far. Throws if
     * a download fails or doesn't match its checksum (nothing half-downloaded is kept).
     */
    fun download(filesDir: File, codes: List<String>, stillWanted: () -> Boolean = { true }, progress: (Long, Long) -> Unit = { _, _ -> }) {
        val needed = missing(filesDir, codes)
        if (needed.isEmpty()) return
        val total = needed.sumOf { it.bytes }
        var before = 0L
        folder(filesDir).mkdirs()
        for (l in needed) {
            val target = file(filesDir, l.code)
            val part = File(target.path + ".part")
            try {
                val c = URL(SOURCE + l.code + ".traineddata").openConnection() as HttpURLConnection
                c.connectTimeout = 20_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "KoLib")
                try {
                    if (c.responseCode != 200) throw IOException(tr("The download of {0} failed ({1}).", l.name, c.responseCode))
                    val md = MessageDigest.getInstance("SHA-256")
                    c.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var got = 0L
                            while (true) {
                                if (!stillWanted()) throw IOException(tr("Stopped."))
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                md.update(buffer, 0, n)
                                got += n
                                progress(before + got, total)
                            }
                        }
                    }
                    val sum = md.digest().joinToString("") { "%02x".format(it) }
                    if (part.length() != l.bytes || sum != l.sha256) {
                        throw IOException(tr("The download of {0} was damaged, so it wasn't used.", l.name))
                    }
                } finally {
                    c.disconnect()
                }
                if (!part.renameTo(target)) throw IOException(tr("Couldn't save the download of {0}.", l.name))
                AppLog.i("OCR", "Downloaded the recognition data for ${l.code} (${l.bytes} bytes).")
            } finally {
                part.delete()
            }
            before += l.bytes
        }
    }

    /** Recognition data no longer wanted can go: everything but [keep]. Returns the bytes freed. */
    fun removeAllBut(filesDir: File, keep: Set<String>): Long {
        var freed = 0L
        folder(filesDir).listFiles()?.filter { it.name.endsWith(".traineddata") && it.name.removeSuffix(".traineddata") !in keep }?.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }

    fun storedBytes(filesDir: File): Long = folder(filesDir).listFiles()?.sumOf { it.length() } ?: 0L
}
