package dev.kolib.app.reader

/** A picture's size in pixels, read from the start of its file (PNG, JPEG, GIF, WebP), without decoding it. */
object ImageSize {

    fun of(b: ByteArray): Pair<Int, Int>? = runCatching {
        when {
            b.size >= 24 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() ->
                int32be(b, 16) to int32be(b, 20)
            b.size >= 10 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() ->
                u16le(b, 6) to u16le(b, 8)
            b.size >= 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> jpeg(b)
            b.size >= 30 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> when (String(b, 12, 4, Charsets.ISO_8859_1)) {
                "VP8X" -> (1 + u24le(b, 24)) to (1 + u24le(b, 27))
                "VP8 " -> (u16le(b, 26) and 0x3FFF) to (u16le(b, 28) and 0x3FFF)
                "VP8L" -> {
                    val v = int32le(b, 21)
                    (1 + (v and 0x3FFF)) to (1 + ((v shr 14) and 0x3FFF))
                }
                else -> null
            }
            else -> null
        }
    }.getOrNull()?.takeIf { (w, h) -> w > 0 && h > 0 }

    /** A JPEG's size, from its frame header. */
    private fun jpeg(b: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 8 < b.size) {
            if (b[i] != 0xFF.toByte()) { i++; continue }
            val marker = b[i + 1].toInt() and 0xFF
            when {
                marker == 0xFF -> i++
                marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7 -> i += 2
                marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> return u16be(b, i + 7) to u16be(b, i + 5)
                else -> i += 2 + u16be(b, i + 2)
            }
        }
        return null
    }

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16be(b: ByteArray, i: Int) = (u(b, i) shl 8) or u(b, i + 1)
    private fun u16le(b: ByteArray, i: Int) = u(b, i) or (u(b, i + 1) shl 8)
    private fun u24le(b: ByteArray, i: Int) = u(b, i) or (u(b, i + 1) shl 8) or (u(b, i + 2) shl 16)
    private fun int32be(b: ByteArray, i: Int) = (u(b, i) shl 24) or (u(b, i + 1) shl 16) or (u(b, i + 2) shl 8) or u(b, i + 3)
    private fun int32le(b: ByteArray, i: Int) = u(b, i) or (u(b, i + 1) shl 8) or (u(b, i + 2) shl 16) or (u(b, i + 3) shl 24)
}
