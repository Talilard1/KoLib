package dev.kolib.app.link

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * What a device's pairing QR code says: where it is ([addresses], [port]), which device it is (its [id] and a print of
 * its [key]), and a [token] good for this one showing of the code. A device that reads it knows who it's connecting
 * to (the key it meets must match the print) and can show it read the code (by sending the token back), so neither
 * side has a code to compare or a question to answer.
 */
data class LinkQr(val addresses: List<String>, val port: Int, val id: String, val key: String, val token: String, val name: String) {
    fun encode(): String = PREFIX + listOf(
        "a=" + addresses.joinToString(","), "p=$port", "i=" + enc(id), "k=$key", "t=$token", "n=" + enc(name),
    ).joinToString("&")

    companion object {
        const val PREFIX = "kolib://pair?"
        private val address = Regex("[0-9A-Fa-f:.%]{2,64}")
        private val word = Regex("[A-Za-z0-9_-]{8,64}")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        /** A short print of a device's public key: enough to know the key met is the one the code was made with. */
        fun keyPrint(publicKey: ByteArray): String = b64(LinkCrypto.sha256(publicKey).copyOf(16))

        fun newToken(): String = b64(LinkCrypto.randomBytes(12))

        /** What a QR code's [text] says, or null if it isn't one of KoLib's (or is broken). */
        fun parse(text: String): LinkQr? {
            val t = text.trim()
            if (!t.startsWith(PREFIX, ignoreCase = true) || t.length > 1024) return null
            val fields = HashMap<String, String>()
            for (part in t.substring(PREFIX.length).split('&')) {
                val at = part.indexOf('=')
                if (at <= 0) continue
                fields.putIfAbsent(part.substring(0, at), part.substring(at + 1))
            }
            return try {
                val addresses = fields["a"].orEmpty().split(',').map { it.trim() }.filter { address.matches(it) }.take(6)
                val port = fields["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                val id = URLDecoder.decode(fields["i"].orEmpty(), "UTF-8").takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
                val key = fields["k"]?.takeIf { word.matches(it) } ?: return null
                val token = fields["t"]?.takeIf { word.matches(it) } ?: return null
                val name = URLDecoder.decode(fields["n"].orEmpty(), "UTF-8").trim().take(80).ifEmpty { "KoLib" }
                if (addresses.isEmpty()) null else LinkQr(addresses, port, id, key, token, name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
