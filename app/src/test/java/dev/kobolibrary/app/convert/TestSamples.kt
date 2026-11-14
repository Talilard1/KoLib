package dev.kobolibrary.app.convert

import java.io.File

/** Small books in other formats kept with the tests (test resources, "formats/"). */
object TestSamples {
    /** The sample called [name], as a file; null if there's no such sample. */
    fun file(name: String): File? {
        val url = TestSamples::class.java.getResource("/formats/$name") ?: return null
        return if (url.protocol == "file") File(url.toURI()) else {
            val out = File.createTempFile("sample-", "-$name")
            out.deleteOnExit()
            url.openStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            out
        }
    }

    fun bytes(name: String): ByteArray? = file(name)?.readBytes()
}
