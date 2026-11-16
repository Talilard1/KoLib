package dev.kobolibrary.app.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finding KoLib on the other devices on the same network (a Wi-Fi network, or one device's hotspot): every couple of
 * seconds each says who it is and where to reach it, to everyone on the network (a UDP broadcast), and listens for the
 * others doing the same. What's heard is only a hint where to look: who's really there is proven when they connect.
 */
class LinkDiscovery(
    private val announcement: () -> JSONObject?,
    private val onHeard: (Heard) -> Unit,
    private val port: Int = PORT,
) {
    /** A device heard on the network. */
    data class Heard(val id: String, val name: String, val address: String, val port: Int)

    private var listening: Job? = null
    private var announcing: Job? = null
    @Volatile private var socket: DatagramSocket? = null

    fun start(scope: CoroutineScope) {
        if (listening?.isActive == true) return
        listening = scope.launch(Dispatchers.IO) { listen() }
        announcing = scope.launch(Dispatchers.IO) {
            while (isActive) {
                announce()
                delay(2_000)
            }
        }
    }

    fun stop() {
        listening?.cancel()
        announcing?.cancel()
        listening = null
        announcing = null
        runCatching { socket?.close() }
        socket = null
    }

    /** Says who this device is and where it can be reached, now. */
    fun announce() {
        val message = announcement()?.toString()?.toByteArray(Charsets.UTF_8) ?: return
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (to in broadcastAddresses() + InetAddress.getByName("255.255.255.255")) {
                    runCatching { s.send(DatagramPacket(message, message.size, to, port)) }
                }
            }
        }
    }

    private suspend fun listen() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val s = runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1_000
                    bind(InetSocketAddress(port))
                }
            }.getOrNull()
            if (s == null) {
                // The port is busy for now: try again shortly.
                delay(3_000)
                continue
            }
            socket = s
            val buffer = ByteArray(2048)
            try {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        s.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    parse(String(packet.data, 0, packet.length, Charsets.UTF_8), packet.address)?.let(onHeard)
                }
            } catch (e: Exception) {
                // Closed, or the network went: start listening again (or stop, if asked to).
            } finally {
                runCatching { s.close() }
            }
        }
    }

    companion object {
        const val PORT = 47162

        /** What a device says about itself: KoLib, its id and name, and the port it takes connections on. */
        fun announcement(id: String, name: String, port: Int): JSONObject =
            JSONObject().put("kolib", LinkHandshake.VERSION).put("id", id).put("name", name).put("port", port)

        fun parse(text: String, from: InetAddress): Heard? = runCatching {
            val o = JSONObject(text)
            if (!o.has("kolib")) return null
            val port = o.getInt("port").takeIf { it in 1..65535 } ?: return null
            Heard(o.getString("id").take(64), o.optString("name").take(80).ifBlank { "KoLib" }, from.hostAddress ?: return null, port)
        }.getOrNull()

        /** This device's own addresses on its networks (Wi-Fi, hotspot), to show and to know its own announcements by. */
        fun addresses(): List<String> = interfaces().flatMap { it.interfaceAddresses }.mapNotNull { a ->
            (a.address as? Inet4Address)?.takeIf { !it.isLoopbackAddress && !it.isLinkLocalAddress }?.hostAddress
        }.distinct()

        private fun broadcastAddresses(): List<InetAddress> = interfaces().flatMap { it.interfaceAddresses }.mapNotNull { a ->
            a.broadcast?.takeIf { a.address is Inet4Address && !a.address.isLoopbackAddress }
        }.distinct()

        private fun interfaces(): List<NetworkInterface> = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        }.getOrDefault(emptyList())
    }
}
