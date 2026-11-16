package dev.kobolibrary.app.link

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** How two devices start a connection, and what passes between them after. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkHandshakeTest {

    private val pool = Executors.newCachedThreadPool()

    private fun identity(id: String, name: String, keys: KeyPair = LinkCrypto.newKeys()) = LinkIdentity(id, name, keys)

    /** Connects [client] to [server] over the loopback, each side's handshake on its own thread. */
    private fun connect(client: LinkIdentity, server: LinkIdentity, pairing: Boolean = false): Pair<Future<LinkSession>, Future<LinkSession>> {
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val served = pool.submit<LinkSession> { listener.use { LinkHandshake.server(it.accept().apply { soTimeout = 5000 }, server) } }
        val asked = pool.submit<LinkSession> {
            LinkHandshake.client(Socket(InetAddress.getLoopbackAddress(), listener.localPort).apply { soTimeout = 5000 }, client, pairing)
        }
        return asked to served
    }

    @Test
    fun bothSidesKnowEachOtherAndShowTheSameCode() {
        val tablet = identity("tablet-0001", "Galaxy Tab S9")
        val phone = identity("phone-00001", "Galaxy A72")
        val (a, b) = connect(phone, tablet, pairing = true)
        val p = a.get(10, TimeUnit.SECONDS)
        val t = b.get(10, TimeUnit.SECONDS)
        assertEquals("tablet-0001", p.peerId)
        assertEquals("Galaxy Tab S9", p.peerName)
        assertArrayEquals(tablet.publicKey, p.peerKey)
        assertEquals("phone-00001", t.peerId)
        assertArrayEquals(phone.publicKey, t.peerKey)
        assertTrue(t.pairing)
        assertEquals(6, p.code.length)
        assertEquals(p.code, t.code)

        // Messages go both ways, a big one in pieces.
        val big = ByteArray(700_000) { (it % 251).toByte() }
        val sent = pool.submit {
            p.channel.sendJson(JSONObject().put("op", "hello"))
            p.channel.send(big)
        }
        assertEquals("hello", t.channel.receiveJson().getString("op"))
        assertArrayEquals(big, t.channel.receive())
        sent.get(5, TimeUnit.SECONDS)
        t.channel.sendJson(JSONObject().put("ok", true))
        assertTrue(p.channel.receiveJson().getBoolean("ok"))

        // A file goes in pieces, and comes out whole.
        val file = ByteArray(300_001) { (it * 7).toByte() }
        val streamed = pool.submit { p.channel.sendStream(ByteArrayInputStream(file), file.size.toLong()) }
        val out = ByteArrayOutputStream()
        var last = 0L
        t.channel.receiveStream(out, file.size.toLong()) { last = it }
        streamed.get(5, TimeUnit.SECONDS)
        assertArrayEquals(file, out.toByteArray())
        assertEquals(file.size.toLong(), last)
        p.close()
        t.close()
    }

    @Test
    fun eachConnectionHasItsOwnCode() {
        val tablet = identity("tablet-0001", "Tablet")
        val phone = identity("phone-00001", "Phone")
        val codes = (1..2).map {
            val (a, b) = connect(phone, tablet)
            val s = a.get(10, TimeUnit.SECONDS)
            b.get(10, TimeUnit.SECONDS).close()
            s.close()
            s.code
        }
        // Fresh keys each time: the same code twice would be a one in a million chance.
        assertNotEquals(codes[0], codes[1])
    }

    @Test
    fun aDeviceThatCantProveItsKeyIsTurnedAway() {
        val real = LinkCrypto.newKeys()
        val other = LinkCrypto.newKeys()
        // Says it has the real key, but can only sign with another.
        val pretender = identity("tablet-0001", "Tablet", KeyPair(real.public, other.private))
        val (a, b) = connect(identity("phone-00001", "Phone"), pretender)
        try {
            a.get(10, TimeUnit.SECONDS)
            fail("The pretender was let in")
        } catch (e: java.util.concurrent.ExecutionException) {
            assertTrue(e.cause.toString(), e.cause is LinkException)
        }
        runCatching { b.get(10, TimeUnit.SECONDS) }
    }

    @Test
    fun aChangedOrReplayedMessageIsNoticed() {
        val key1 = LinkCrypto.randomBytes(32)
        val key2 = LinkCrypto.randomBytes(32)
        val wire = ByteArrayOutputStream()
        val sender = SecureChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(wire), key1, key2)
        sender.sendJson(JSONObject().put("text", "hello"))
        sender.sendJson(JSONObject().put("text", "again"))
        val bytes = wire.toByteArray()

        fun receiver(b: ByteArray) = SecureChannel(DataInputStream(ByteArrayInputStream(b)), DataOutputStream(ByteArrayOutputStream()), key2, key1)
        // As sent, both come through in order.
        receiver(bytes).let {
            assertEquals("hello", it.receiveJson().getString("text"))
            assertEquals("again", it.receiveJson().getString("text"))
        }
        // One bit changed on the way.
        val changed = bytes.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        try {
            receiver(changed).receiveJson()
            fail("A changed message got through")
        } catch (e: LinkException) {
        }
        // The second message on its own, sent again in place of the first.
        val first = 4 + DataInputStream(ByteArrayInputStream(bytes)).readInt()
        try {
            receiver(bytes.copyOfRange(first, bytes.size)).receiveJson()
            fail("A message out of order got through")
        } catch (e: LinkException) {
        }
    }

    @Test
    fun hkdfMatchesItsStandard() {
        // RFC 5869, test case 1.
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = LinkCrypto.hkdf(ikm, salt, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun keysAgreeAndSignaturesCheck() {
        val a = LinkCrypto.newKeys()
        val b = LinkCrypto.newKeys()
        assertArrayEquals(LinkCrypto.agree(a.private, b.public), LinkCrypto.agree(b.private, a.public))
        val sig = LinkCrypto.sign(a.private, "data".toByteArray())
        assertTrue(LinkCrypto.verify(a.public, "data".toByteArray(), sig))
        assertFalse(LinkCrypto.verify(a.public, "date".toByteArray(), sig))
        assertFalse(LinkCrypto.verify(b.public, "data".toByteArray(), sig))
        // Keys saved and read back are the same keys.
        assertArrayEquals(a.public.encoded, LinkCrypto.publicKey(a.public.encoded).encoded)
        assertArrayEquals(a.private.encoded, LinkCrypto.privateKey(a.private.encoded).encoded)
    }
}
