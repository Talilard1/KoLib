package dev.kolib.app.link

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
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finding KoLib on the other devices on the same network (a Wi-Fi network, or one device's hotspot): every couple of
 * seconds each says who it is and where to reach it, to everyone on each of its local networks (a broadcast, and a
 * multicast to KoLib's group), and listens for the others doing the same. What's heard is only a hint where to look:
 * who's really there is proven when they connect. Some networks (a phone's hotspot, often) pass none of this on;
 * [LinkManager] looks for devices itself then.
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

    /** Says who this device is and where it can be reached, now, on each of its local networks. */
    fun announce() {
        val message = announcement()?.toString()?.toByteArray(Charsets.UTF_8) ?: return
        for (lan in lans()) {
            // From this network's own address, so it goes out on this network, not over mobile data.
            runCatching {
                DatagramSocket(InetSocketAddress(lan.address, 0)).use { s ->
                    s.broadcast = true
                    for (to in listOf(lan.broadcast, ALL)) runCatching { s.send(DatagramPacket(message, message.size, to, port)) }
                }
            }
            runCatching {
                MulticastSocket(InetSocketAddress(lan.address, 0)).use { s ->
                    s.networkInterface = lan.network
                    s.timeToLive = 1
                    s.send(DatagramPacket(message, message.size, GROUP, port))
                }
            }
        }
    }

    /** Says who this device is to the device at [address] alone (one that can't hear what's sent to everyone). */
    fun announceTo(address: String) {
        val message = announcement()?.toString()?.toByteArray(Charsets.UTF_8) ?: return
        runCatching { DatagramSocket().use { s -> s.send(DatagramPacket(message, message.size, InetAddress.getByName(address), port)) } }
    }

    private suspend fun listen() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val networks = lans()
            val s = runCatching {
                MulticastSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1_000
                    bind(InetSocketAddress(port))
                    for (lan in networks) runCatching { joinGroup(InetSocketAddress(GROUP, port), lan.network) }
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
                var checked = System.currentTimeMillis()
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        s.receive(packet)
                        parse(String(packet.data, 0, packet.length, Charsets.UTF_8), packet.address)?.let(onHeard)
                    } catch (e: SocketTimeoutException) {
                        // Nothing heard.
                    }
                    // A network joined or left (Wi-Fi, a hotspot turned on): listen again, joining the group on it.
                    if (System.currentTimeMillis() - checked > 5_000) {
                        checked = System.currentTimeMillis()
                        if (lans().map { it.address } != networks.map { it.address }) break
                    }
                }
            } catch (e: Exception) {
                // Closed, or the network went: start listening again (or stop, if asked to).
            } finally {
                runCatching { s.close() }
            }
        }
    }

    /** A local network this device is on: its address on it, the network's broadcast address, and the interface. */
    class Lan(val address: Inet4Address, val broadcast: InetAddress, val prefix: Int, val network: NetworkInterface)

    companion object {
        const val PORT = 47162
        /** KoLib's multicast group, for local networks only. */
        val GROUP: InetAddress = InetAddress.getByAddress(byteArrayOf(239.toByte(), 255.toByte(), 47, 162.toByte()))
        private val ALL: InetAddress = InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))

        /** Interfaces that are never a local network shared with the user's other devices: mobile data, VPNs and the like. */
        private val NOT_LOCAL = listOf("rmnet", "ccmni", "pdp", "v4-", "clat", "dummy", "tun", "ppp", "ipsec", "ifb", "lo", "seth", "usb_rmnet", "rev_rmnet")

        /** What a device says about itself: KoLib, its id and name, and the port it takes connections on. */
        fun announcement(id: String, name: String, port: Int): JSONObject =
            JSONObject().put("kolib", LinkHandshake.VERSION).put("id", id).put("name", name).put("port", port)

        fun parse(text: String, from: InetAddress): Heard? = runCatching {
            val o = JSONObject(text)
            if (!o.has("kolib")) return null
            val port = o.getInt("port").takeIf { it in 1..65535 } ?: return null
            Heard(o.getString("id").take(64), o.optString("name").take(80).ifBlank { "KoLib" }, from.hostAddress ?: return null, port)
        }.getOrNull()

        /** The local networks this device is on: Wi-Fi, its hotspot, a cable; not mobile data. */
        fun lans(): List<Lan> = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { ni ->
                runCatching { ni.isUp && !ni.isLoopback && !ni.isPointToPoint }.getOrDefault(false) &&
                    NOT_LOCAL.none { ni.name.lowercase().startsWith(it) }
            }.flatMap { ni ->
                ni.interfaceAddresses.mapNotNull { a ->
                    val address = a.address as? Inet4Address ?: return@mapNotNull null
                    if (address.isLoopbackAddress || address.isLinkLocalAddress) return@mapNotNull null
                    val prefix = a.networkPrefixLength.toInt().takeIf { it in 8..30 } ?: 24
                    Lan(address, a.broadcast ?: broadcastOf(address, prefix), prefix, ni)
                }
            }
        }.getOrDefault(emptyList())

        /** This device's own addresses on its local networks, to show (and to type in on another device). */
        fun addresses(): List<String> = lans().mapNotNull { it.address.hostAddress }.distinct()

        /** The broadcast address of the network [address] is on, [prefix] bits long. */
        fun broadcastOf(address: Inet4Address, prefix: Int): InetAddress {
            val ip = address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
            val b = ip or mask.inv()
            return InetAddress.getByAddress(byteArrayOf((b ushr 24).toByte(), (b ushr 16).toByte(), (b ushr 8).toByte(), b.toByte()))
        }

        /**
         * Every other address on this device's local networks (on a big network, the 254 around this device's own), to
         * look for KoLib at when nothing is heard.
         */
        fun neighbours(networks: List<Pair<Inet4Address, Int>> = lans().map { it.address to it.prefix }): List<String> {
            val own = networks.map { it.first.hostAddress }.toSet()
            return networks.flatMap { (address, length) ->
                val ip = address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
                val prefix = maxOf(length, 24)
                val mask = -1 shl (32 - prefix)
                val net = ip and mask
                val size = 1 shl (32 - prefix)
                (1 until size - 1).map { i ->
                    val a = net or i
                    "${(a ushr 24) and 0xFF}.${(a ushr 16) and 0xFF}.${(a ushr 8) and 0xFF}.${a and 0xFF}"
                }
            }.filter { it !in own }.distinct()
        }
    }
}
