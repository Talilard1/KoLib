package dev.kobolibrary.app.link

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import dev.kobolibrary.app.tr
import java.net.Socket
import java.security.KeyPair
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** This device, as it's known to the others: its id, its name, and its own keys. */
class LinkIdentity(val id: String, val name: String, internal val keys: KeyPair) {
    val publicKey: ByteArray get() = keys.public.encoded
}

/** Something went wrong in the link's conversation: a broken message, a check that failed, an answer of no. */
class LinkException(message: String) : IOException(message)

/**
 * What passes between two devices once they've agreed keys: each piece sealed with AES-GCM (which also shows it
 * wasn't changed on the way), numbered so none can be replayed, dropped or reordered unnoticed. A message too big
 * for one piece goes in several, each saying whether more follow.
 */
class SecureChannel internal constructor(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    sendKey: ByteArray,
    receiveKey: ByteArray,
) {
    private val sendKey = SecretKeySpec(sendKey, "AES")
    private val receiveKey = SecretKeySpec(receiveKey, "AES")
    private var sent = 0L
    private var received = 0L

    private fun nonce(n: Long) = ByteArray(12).also { b -> for (i in 0 until 8) b[11 - i] = (n ushr (8 * i)).toByte() }

    @Synchronized
    private fun sendFrame(plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(128, nonce(sent++)))
        val sealed = cipher.doFinal(plain)
        output.writeInt(sealed.size)
        output.write(sealed)
        output.flush()
    }

    private fun receiveFrame(): ByteArray {
        val size = input.readInt()
        if (size < 17 || size > MAX_FRAME + 17) throw LinkException(tr("A message from the other device was damaged."))
        val sealed = ByteArray(size)
        input.readFully(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, receiveKey, GCMParameterSpec(128, nonce(received++)))
        return try {
            cipher.doFinal(sealed)
        } catch (e: java.security.GeneralSecurityException) {
            throw LinkException(tr("A message failed its security check."))
        }
    }

    fun send(plain: ByteArray) {
        var from = 0
        do {
            val n = minOf(MAX_FRAME, plain.size - from)
            val last = from + n >= plain.size
            val frame = ByteArray(n + 1)
            frame[0] = if (last) 0 else 1
            System.arraycopy(plain, from, frame, 1, n)
            sendFrame(frame)
            from += n
        } while (!last)
    }

    /** The next message, put back together; one bigger than [limit] bytes isn't taken. */
    fun receive(limit: Int = MAX_MESSAGE): ByteArray {
        val first = receiveFrame()
        if (first[0] == 0.toByte()) return first.copyOfRange(1, first.size)
        val out = java.io.ByteArrayOutputStream()
        out.write(first, 1, first.size - 1)
        while (true) {
            val f = receiveFrame()
            out.write(f, 1, f.size - 1)
            if (out.size() > limit) throw LinkException(tr("A message from the other device was too big."))
            if (f[0] == 0.toByte()) return out.toByteArray()
        }
    }

    fun sendJson(o: JSONObject) = send(o.toString().toByteArray(Charsets.UTF_8))

    fun receiveJson(): JSONObject = try {
        JSONObject(String(receive(), Charsets.UTF_8))
    } catch (e: org.json.JSONException) {
        throw LinkException(tr("A message from the other device was damaged."))
    }

    /** Sends [size] bytes from [from], in pieces, telling [progress] how many so far. */
    fun sendStream(from: InputStream, size: Long, progress: (Long) -> Unit = {}) {
        val buffer = ByteArray(CHUNK)
        var done = 0L
        while (done < size) {
            val want = minOf(CHUNK.toLong(), size - done).toInt()
            var got = 0
            while (got < want) {
                val n = from.read(buffer, got, want - got)
                if (n < 0) throw LinkException(tr("The file changed while it was being sent."))
                got += n
            }
            send(if (got == CHUNK) buffer else buffer.copyOf(got))
            done += got
            progress(done)
        }
    }

    /** Takes [size] bytes sent by [sendStream] into [to], telling [progress] how many so far. */
    fun receiveStream(to: OutputStream, size: Long, progress: (Long) -> Unit = {}) {
        var done = 0L
        while (done < size) {
            val piece = receive()
            if (done + piece.size > size) throw LinkException(tr("A message from the other device was damaged."))
            to.write(piece)
            done += piece.size
            progress(done)
        }
    }

    companion object {
        /** The most one sealed piece holds. */
        const val MAX_FRAME = 256 * 1024
        /** The biggest message taken (a file goes as many messages, so this is only for lists and details). */
        const val MAX_MESSAGE = 64 * 1024 * 1024
        private const val CHUNK = 128 * 1024
    }
}

/** A connection between two devices after their handshake: who's at the other end, and how to talk to them. */
class LinkSession internal constructor(
    val peerId: String,
    val peerName: String,
    /** The other device's own public key (X.509), which it proved it holds. */
    val peerKey: ByteArray,
    /** The six figures both devices show when pairing: the same on both if nothing came between them. */
    val code: String,
    /** The other device asked to pair. */
    val pairing: Boolean,
    val channel: SecureChannel,
    private val socket: Socket,
) : Closeable {
    /** How long to wait for the other device before giving up. */
    fun waitAtMost(millis: Int) {
        socket.soTimeout = millis
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

/**
 * How two devices start a connection, in the station-to-station way: each says who it is and gives a fresh key for
 * this connection; both work out the same secret from those (ECDH) and make the connection's keys from it and from
 * everything said so far; then each signs all that with its own long-kept key, inside the encrypted channel, to prove
 * it's the device it says. A device in the middle can't make both sides agree: it would have to sign as one of them.
 * When they don't know each other yet, a six-figure code from the keys, shown on both screens, shows that it's
 * really them.
 */
object LinkHandshake {
    const val VERSION = 1
    private const val MAX_HELLO = 16 * 1024
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun client(socket: Socket, me: LinkIdentity, pairing: Boolean): LinkSession {
        val (input, output) = streams(socket)
        val mine = LinkCrypto.newKeys()
        val hello = hello(me, mine, pairing).toByteArray(Charsets.UTF_8)
        writePlain(output, hello)
        val theirs = readPlain(input)
        val peer = parseHello(theirs)
        val keys = keys(mine, peer, hello, theirs)
        val channel = SecureChannel(input, output, sendKey = keys.toServer, receiveKey = keys.toClient)
        // The server proves itself first; then this side does.
        val proof = channel.receiveJson()
        checkSigned(peer, proof, "server", keys.transcript)
        channel.sendJson(JSONObject().put("sig", b64.encodeToString(LinkCrypto.sign(me.keys.private, sigData("client", keys.transcript)))))
        return LinkSession(peer.id, peer.name, peer.key, LinkCrypto.code(keys.codeKey), pairing, channel, socket)
    }

    fun server(socket: Socket, me: LinkIdentity): LinkSession {
        val (input, output) = streams(socket)
        val theirs = readPlain(input)
        val peer = parseHello(theirs)
        val mine = LinkCrypto.newKeys()
        val hello = hello(me, mine, pairing = false).toByteArray(Charsets.UTF_8)
        writePlain(output, hello)
        val keys = keys(mine, peer, theirs, hello)
        val channel = SecureChannel(input, output, sendKey = keys.toClient, receiveKey = keys.toServer)
        channel.sendJson(JSONObject().put("sig", b64.encodeToString(LinkCrypto.sign(me.keys.private, sigData("server", keys.transcript)))))
        val proof = channel.receiveJson()
        checkSigned(peer, proof, "client", keys.transcript)
        return LinkSession(peer.id, peer.name, peer.key, LinkCrypto.code(keys.codeKey), peer.pairing, channel, socket)
    }

    private class Hello(val id: String, val name: String, val key: ByteArray, val ephemeral: ByteArray, val pairing: Boolean)

    private class Keys(val toServer: ByteArray, val toClient: ByteArray, val codeKey: ByteArray, val transcript: ByteArray)

    private fun streams(socket: Socket) =
        DataInputStream(BufferedInputStream(socket.getInputStream())) to DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    private fun hello(me: LinkIdentity, ephemeral: KeyPair, pairing: Boolean) = JSONObject()
        .put("v", VERSION)
        .put("app", "KoLib")
        .put("id", me.id)
        .put("name", me.name)
        .put("key", b64.encodeToString(me.publicKey))
        .put("eph", b64.encodeToString(ephemeral.public.encoded))
        .put("nonce", b64.encodeToString(LinkCrypto.randomBytes(32)))
        .put("pair", pairing)
        .toString()

    private fun parseHello(bytes: ByteArray): Hello = try {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        if (o.optString("app") != "KoLib") throw LinkException(tr("That isn't KoLib."))
        if (o.optInt("v") != VERSION) throw LinkException(tr("The other device has a different version of KoLib. Update both to the same one."))
        val key = unb64.decode(o.getString("key"))
        val eph = unb64.decode(o.getString("eph"))
        // Both must be keys on the right curve (decoding checks the point is on it).
        LinkCrypto.publicKey(key)
        LinkCrypto.publicKey(eph)
        Hello(o.getString("id").take(64), o.optString("name").take(80).ifBlank { "KoLib" }, key, eph, o.optBoolean("pair"))
    } catch (e: LinkException) {
        throw e
    } catch (e: Exception) {
        throw LinkException(tr("The connection to the other device couldn't be set up."))
    }

    private fun keys(mine: KeyPair, peer: Hello, clientHello: ByteArray, serverHello: ByteArray): Keys {
        val secret = LinkCrypto.agree(mine.private, LinkCrypto.publicKey(peer.ephemeral))
        val transcript = LinkCrypto.sha256(clientHello, serverHello)
        val okm = LinkCrypto.hkdf(secret, transcript, "KoLib link 1".toByteArray(), 96)
        return Keys(okm.copyOfRange(0, 32), okm.copyOfRange(32, 64), okm.copyOfRange(64, 96), transcript)
    }

    private fun sigData(side: String, transcript: ByteArray) = "KoLib $side".toByteArray() + transcript

    private fun checkSigned(peer: Hello, proof: JSONObject, side: String, transcript: ByteArray) {
        val sig = runCatching { unb64.decode(proof.getString("sig")) }.getOrNull()
        if (sig == null || !LinkCrypto.verify(LinkCrypto.publicKey(peer.key), sigData(side, transcript), sig)) {
            throw LinkException(tr("The other device couldn't be verified."))
        }
    }

    private fun writePlain(output: DataOutputStream, bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readPlain(input: DataInputStream): ByteArray {
        val size = try {
            input.readInt()
        } catch (e: EOFException) {
            throw LinkException(tr("The other device disconnected."))
        }
        if (size <= 0 || size > MAX_HELLO) throw LinkException(tr("The connection to the other device couldn't be set up."))
        return ByteArray(size).also { input.readFully(it) }
    }
}
