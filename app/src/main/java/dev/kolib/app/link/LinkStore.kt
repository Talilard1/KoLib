package dev.kolib.app.link

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPair
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A device this one is paired with: its id, name, and the key it proved it holds when they paired. */
data class PairedDevice(
    val id: String,
    val name: String,
    /** Its own public key (X.509, base 64): a device saying it's this one must prove it holds the matching key. */
    val key: String,
    val pairedAt: Long,
    /** Where it was last found on the network, to try first. */
    val address: String? = null,
    val port: Int? = null,
    val seenAt: Long? = null,
) {
    fun keyBytes(): ByteArray = Base64.getDecoder().decode(key)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("key", key).put("pairedAt", pairedAt)
        .putOpt("address", address).putOpt("port", port).putOpt("seenAt", seenAt)

    companion object {
        fun from(o: JSONObject): PairedDevice? = runCatching {
            PairedDevice(
                o.getString("id"), o.optString("name").ifBlank { "KoLib" }, o.getString("key"), o.optLong("pairedAt"),
                o.optString("address").takeIf { it.isNotBlank() }, o.optInt("port").takeIf { it > 0 }, o.optLong("seenAt").takeIf { it > 0 },
            )
        }.getOrNull()
    }
}

/** How the links behave. */
data class LinkSettings(
    /** Places in books are brought into step with a paired device whenever it's found. */
    val keepPlacesInStep: Boolean = true,
    /** KoLib stays reachable from paired devices when it isn't open (with a notification saying so). */
    val stayReachable: Boolean = false,
    /**
     * KoLib looks for other devices (and says where it is) the whole time it's open. Off, as it starts: only while the
     * Devices screen is open (or while it stays reachable, above); paired devices can still reach it while it's open.
     */
    val lookAlways: Boolean = false,
)

/**
 * What a device keeps for its links: its own id and keys (made the first time), and the devices it's paired with.
 * Kept in the app's own private folder.
 */
class LinkStore(private val dir: File) {
    private val identityFile get() = File(dir, "identity.json")
    private val devicesFile get() = File(dir, "devices.json")
    private val settingsFile get() = File(dir, "settings.json")

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LinkSettings> = _settings.asStateFlow()

    @Synchronized
    fun changeSettings(change: (LinkSettings) -> LinkSettings) {
        val s = change(_settings.value)
        _settings.value = s
        dir.mkdirs()
        write(settingsFile, JSONObject().put("keepPlacesInStep", s.keepPlacesInStep).put("stayReachable", s.stayReachable).put("lookAlways", s.lookAlways).toString())
    }

    private fun readSettings(): LinkSettings = runCatching {
        val o = JSONObject(settingsFile.readText())
        LinkSettings(o.optBoolean("keepPlacesInStep", true), o.optBoolean("stayReachable", false), o.optBoolean("lookAlways", false))
    }.getOrDefault(LinkSettings())

    private val _devices = MutableStateFlow(readDevices())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private var keys: Pair<String, KeyPair>? = null

    /** This device as the others know it, named [name] (its name in the system's settings, which can change). */
    @Synchronized
    fun identity(name: String): LinkIdentity {
        val (id, pair) = keys ?: loadOrMake().also { keys = it }
        return LinkIdentity(id, name, pair)
    }

    private fun loadOrMake(): Pair<String, KeyPair> {
        runCatching {
            val o = JSONObject(identityFile.readText())
            val pub = LinkCrypto.publicKey(Base64.getDecoder().decode(o.getString("public")))
            val priv = LinkCrypto.privateKey(Base64.getDecoder().decode(o.getString("private")))
            return o.getString("id") to KeyPair(pub, priv)
        }
        val id = UUID.randomUUID().toString()
        val pair = LinkCrypto.newKeys()
        dir.mkdirs()
        write(
            identityFile,
            JSONObject().put("id", id)
                .put("public", Base64.getEncoder().encodeToString(pair.public.encoded))
                .put("private", Base64.getEncoder().encodeToString(pair.private.encoded))
                .toString(),
        )
        return id to pair
    }

    fun device(id: String): PairedDevice? = _devices.value.firstOrNull { it.id == id }

    /** Pairs with [device] (or updates it, if paired already). */
    fun add(device: PairedDevice) = change { list -> list.filter { it.id != device.id } + device }

    fun remove(id: String) = change { list -> list.filter { it.id != id } }

    /** Notes where a paired device was found, and its name now. */
    fun seen(id: String, name: String, address: String, port: Int, now: Long = System.currentTimeMillis()) {
        val d = device(id) ?: return
        // Saved at most once a minute for the same place, the device being seen every few seconds.
        if (d.address == address && d.port == port && d.name == name && (d.seenAt ?: 0) > now - 60_000) return
        change { list -> list.map { if (it.id == id) it.copy(name = name, address = address, port = port, seenAt = now) else it } }
    }

    @Synchronized
    private fun change(f: (List<PairedDevice>) -> List<PairedDevice>) {
        _devices.update(f)
        dir.mkdirs()
        write(devicesFile, JSONArray().apply { _devices.value.forEach { put(it.toJson()) } }.toString())
    }

    private fun readDevices(): List<PairedDevice> = runCatching {
        val a = JSONArray(devicesFile.readText())
        (0 until a.length()).mapNotNull { PairedDevice.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun write(file: File, text: String) {
        val tmp = File(file.path + ".part")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
