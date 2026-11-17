package dev.kobolibrary.app.link

import dev.kobolibrary.app.ui.link.QrCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** What a pairing QR code says, written and read back; and the code itself drawn and read. */
class LinkQrTest {
    private val offer = LinkQr(listOf("192.168.1.20", "10.0.0.7"), 47161, "3f2a-device id", LinkQr.keyPrint(ByteArray(91) { it.toByte() }), LinkQr.newToken(), "The tablet & co")

    @Test
    fun whatItSaysComesBackTheSame() {
        val text = offer.encode()
        assertEquals(offer, LinkQr.parse(text))
        assertEquals(offer, LinkQr.parse("  $text\n"))
        // A print of a key is short, and the same for the same key.
        assertEquals(22, offer.key.length)
        assertEquals(offer.key, LinkQr.keyPrint(ByteArray(91) { it.toByte() }))
    }

    @Test
    fun otherCodesAndBrokenOnesAreNotPairingCodes() {
        assertNull(LinkQr.parse("https://example.com/pair?a=1.2.3.4&p=1"))
        assertNull(LinkQr.parse(""))
        assertNull(LinkQr.parse("kolib://pair?"))
        assertNull(LinkQr.parse(offer.encode().replace("p=47161", "p=99999")))
        assertNull(LinkQr.parse(offer.encode().replace("a=192.168.1.20,10.0.0.7", "a=not an address")))
        assertNull(LinkQr.parse(offer.encode().replace("t=${offer.token}", "t=")))
        assertNull(LinkQr.parse(offer.encode() + "x".repeat(2000)))
    }

    @Test
    fun theCodeDrawnIsReadBack() {
        val text = offer.encode()
        val m = QrCodes.matrix(text)
        assertNotNull(m)
        // As a camera would see it: each square six dots across, dark on light.
        val scale = 6
        val w = m!!.width * scale
        val h = m.height * scale
        val picture = ByteArray(w * h) { i -> if (m.get((i % w) / scale, (i / w) / scale)) 20 else 235.toByte() }
        assertEquals(text, QrCodes.read(picture, w, h))
        assertNull(QrCodes.read(ByteArray(w * h) { 128.toByte() }, w, h))
    }
}
