package dev.kobolibrary.app.kobo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Contents of the Kobo's `.kobo/version` file: "serial,?,firmware,?,?,model-id". */
data class KoboVersionInfo(
    val serial: String,
    val firmware: String,
    val modelId: String,
) {
    /** Enough to recognise the device without putting the full serial number in logs. */
    val maskedSerial: String
        get() = if (serial.length > 7) serial.take(4) + "…" + serial.takeLast(3) else serial

    companion object {
        fun parse(text: String): KoboVersionInfo? {
            val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val parts = line.split(',').map { it.trim() }
            if (parts.size < 3) return null
            return KoboVersionInfo(serial = parts[0], firmware = parts[2], modelId = parts.last())
        }
    }
}

/** Kobo's USB product ids and model numbers, and the models they belong to. */
object KoboModels {
    const val USB_VENDOR_ID = 0x2237

    private val byUsbProductId = mapOf(
        0x4163 to "Touch",
        0x4173 to "Glo",
        0x4183 to "Mini",
        0x4193 to "Aura HD",
        0x4203 to "Aura",
        0x4213 to "Aura H2O",
        0x4223 to "Glo HD",
        0x4224 to "Touch 2.0",
        0x4225 to "Aura One",
        0x4226 to "Aura Edition 2",
        0x4227 to "Aura H2O Edition 2",
        0x4228 to "Clara HD",
        0x4229 to "Forma",
        0x4230 to "Nia",
        0x4231 to "Sage",
        0x4232 to "Libra H2O",
        0x4233 to "Elipsa",
        0x4234 to "Libra 2",
        0x4235 to "Clara 2E",
        0x4236 to "Elipsa 2E",
        0x4238 to "Clara Colour",
        0x4239 to "Clara BW",
    )

    /** 0x4237 is shared by the 2024 models; the version file tells them apart. */
    private const val SHARED_2024_PRODUCT_ID = 0x4237

    /** Last digits of the model id in `.kobo/version`: Kobo's own model numbers. */
    private val byModelIdSuffix = mapOf(
        "310" to "Touch", "330" to "Glo", "340" to "Mini", "350" to "Aura HD", "360" to "Aura",
        "370" to "Aura H2O", "371" to "Glo HD", "372" to "Touch 2.0", "373" to "Aura One",
        "374" to "Aura H2O Edition 2", "376" to "Clara HD", "377" to "Forma", "382" to "Nia",
        "383" to "Sage", "384" to "Libra H2O", "386" to "Clara 2E", "387" to "Elipsa", "388" to "Libra 2",
        "389" to "Elipsa 2E", "390" to "Libra Colour", "391" to "Clara BW", "393" to "Clara Colour",
    )

    fun name(usbProductId: Int?, modelId: String?): String? {
        val suffixName = modelId?.takeLast(3)?.let { byModelIdSuffix[it] }
        if (usbProductId == SHARED_2024_PRODUCT_ID) return suffixName ?: "Libra Colour / Clara Colour / Clara BW"
        usbProductId?.let { byUsbProductId[it] }?.let { return it }
        return suffixName
    }
}

/**
 * Where the Kobo keeps its own cover thumbnails. Its software (built on Qt) files each book's pictures
 * in a folder named from a hash of the book's image id, so finding them means doing the same sum.
 */
object KoboImages {
    const val IMAGES_DIR = ".kobo-images"

    /** The string hash of Qt 4, which the Kobo uses to spread cover files over folders. */
    fun qhash(input: String): Int {
        var h = 0L
        for (b in input.toByteArray(Charsets.UTF_8)) {
            h = (h shl 4) + (b.toInt() and 0xFF)
            h = h xor ((h and 0xF0000000L) shr 23)
            h = h and 0x0FFFFFFFL
        }
        return h.toInt()
    }

    fun folderFor(imageId: String): String {
        val h = qhash(imageId)
        return "${h and 0xFF}/${(h and 0xFF00) shr 8}"
    }

    fun imageIdFromContentId(contentId: String): String =
        contentId.replace('/', '_').replace(' ', '_').replace(':', '_').replace('.', '_')

    /** Small to large; the grid uses the first that exists. */
    val THUMBNAIL_SUFFIXES = listOf(
        " - N3_LIBRARY_GRID.parsed",
        " - N3_LIBRARY_FULL.parsed",
        " - AndroidBookLoadTablet_Aspect.parsed",
        " - N3_FULL.parsed",
        " - N3_LIBRARY_LIST.parsed",
    )

    /** Large to small; the detail view uses the first that exists. */
    val FULL_SUFFIXES = listOf(
        " - N3_FULL.parsed",
        " - N3_LIBRARY_FULL.parsed",
        " - AndroidBookLoadTablet_Aspect.parsed",
        " - N3_LIBRARY_GRID.parsed",
    )
}

/** Kobo timestamps come in several shapes depending on firmware and on who wrote them. */
object KoboDates {
    fun parseMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(' ', 'T')
        val millis = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        // The Kobo uses 1970-01-01 as "never".
        return millis?.takeIf { it > 86_400_000L }
    }
}
