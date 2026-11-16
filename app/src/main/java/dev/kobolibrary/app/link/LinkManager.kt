package dev.kobolibrary.app.link

import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.tr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

/** What the app does with what another device sends, and finds to send it. */
interface LinkHost {
    /** Text sent from [from]: it goes on the clipboard. */
    fun receiveText(text: String, from: String)

    /** A file sent from [from], written by [write] where files are kept; gives where that is, to say. */
    suspend fun receiveFile(name: String, type: String?, size: Long, from: String, write: suspend (OutputStream) -> Unit): String

    /** A library book's record, reading data and files, to send; null if it's gone. */
    suspend fun outgoingBook(id: String): OutgoingBook?

    /**
     * Another device offers a book ([record], and [reading], where it was left and its bookmarks and highlights): true
     * to have its files sent (it's new here, or a newer version of one here); false if it's here already, when its
     * reading data is put together with this device's now.
     */
    suspend fun wantBook(record: JSONObject, reading: JSONObject): Boolean

    /** Takes in a book whose files came into [files]; its reading data is put together with this device's. */
    suspend fun receiveBook(record: JSONObject, reading: JSONObject, files: File): BookReceived

    /** Where every library book was left reading here (an empty place for one that wasn't). */
    suspend fun places(): List<BookPlace>

    /** Puts books (by this device's ids) at places put together with another device's. */
    suspend fun applyPlaces(places: List<BookPlace>)

    /** A folder for what's coming in, before it's taken in. */
    fun scratch(): File

    /** Room left for books, in bytes. */
    fun freeSpace(): Long
}

/** A book to send: what it's called, its library record, where it was left reading, and its files. */
class OutgoingBook(val title: String, val record: JSONObject, val reading: JSONObject, val files: List<File>)

/** How a book sent from another device was taken in. */
enum class BookReceived { ADDED, UPDATED, ALREADY_THERE, FAILED }

/** A file to send. */
class OutgoingFile(val name: String, val type: String?, val size: Long, val open: () -> InputStream)

/**
 * Where a book was left reading on a device (see [ReadingData.placeOf]), with its id there, and its title and first
 * author to find it by on another device that added it separately.
 */
data class BookPlace(val id: String, val title: String, val author: String?, val state: JSONObject) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).putOpt("author", author).put("state", state)

    companion object {
        fun from(o: JSONObject): BookPlace? = runCatching {
            BookPlace(o.getString("id"), o.optString("title"), o.optString("author").takeIf { it.isNotBlank() }, o.optJSONObject("state") ?: JSONObject())
        }.getOrNull()

        private fun key(p: BookPlace) = p.title.trim().lowercase().replace(Regex("\\s+"), " ") + "\u0000" + p.author.orEmpty().trim().lowercase()

        /**
         * Puts two devices' places together, book by book (the same book by id, as sent from one to the other, or else
         * by title and author when only one book on each side has them): which of [mine] change (as this device's
         * books), and which of [theirs] (as theirs).
         */
        fun sync(mine: List<BookPlace>, theirs: List<BookPlace>): Pair<List<BookPlace>, List<BookPlace>> {
            val byId = mine.associateBy { it.id }
            val myNames = mine.groupBy(::key).filterValues { it.size == 1 }.mapValues { it.value.single() }
            val theirNames = theirs.groupingBy(::key).eachCount()
            val pairs = ArrayList<Pair<BookPlace, BookPlace>>()
            val taken = HashSet<String>()
            val left = ArrayList<BookPlace>()
            for (t in theirs) {
                val m = byId[t.id]
                if (m != null && taken.add(m.id)) pairs += m to t else left += t
            }
            for (t in left) {
                val k = key(t)
                val m = myNames[k]?.takeIf { theirNames[k] == 1 } ?: continue
                if (taken.add(m.id)) pairs += m to t
            }
            val forMe = ArrayList<BookPlace>()
            val forThem = ArrayList<BookPlace>()
            for ((m, t) in pairs) {
                val merged = ReadingData.mergePlace(m.state, t.state)
                if (!ReadingData.samePlace(merged, m.state)) forMe += m.copy(state = merged)
                if (!ReadingData.samePlace(merged, t.state)) forThem += t.copy(state = merged)
            }
            return forMe to forThem
        }
    }
}

/** Something being sent or received now, for its progress bar. */
data class Transfer(val key: Long, val device: String, val what: String, val outgoing: Boolean, val done: Long, val total: Long)

/**
 * Pairing with [name], as it stands: [incoming] when the other device asked. [code] is shown on both, once they're
 * connected; [confirmed] once this side has said yes; [failed] says why it didn't work.
 */
data class Pairing(val name: String, val code: String?, val incoming: Boolean, val confirmed: Boolean = false, val failed: String? = null)

/**
 * KoLib on one device talking to KoLib on the others: finding them on the network, pairing, sending books (with their
 * details, covers, collections, places, bookmarks and highlights), files and text between them, and keeping places in
 * books in step. Every connection is encrypted, and a device that isn't paired can do nothing but ask to pair.
 */
class LinkManager(
    private val store: LinkStore,
    private val host: LinkHost,
    /** This device's name, as the system has it now. */
    private val deviceName: () -> String,
    private val scope: CoroutineScope,
    /** The port connections come in on: the usual one, or (tests) 0 for any free one. */
    private val port: Int = PORT,
    /** Whether to find devices on the network by itself (tests connect by address instead). */
    private val discover: Boolean = true,
    private val discoveryPort: Int = LinkDiscovery.PORT,
    /** Told when it starts and stops listening (to let the Wi-Fi hand it what's sent to everyone meanwhile). */
    private val onRunning: (Boolean) -> Unit = {},
) {
    val identity: LinkIdentity get() = store.identity(deviceName())
    val paired: StateFlow<List<PairedDevice>> get() = store.devices
    val settings: StateFlow<LinkSettings> get() = store.settings

    /** A device heard on the network lately, or reached by its address. */
    data class Nearby(val id: String, val name: String, val address: String, val port: Int, val at: Long)

    private val _nearby = MutableStateFlow<List<Nearby>>(emptyList())
    /** KoLib on the other devices on this network, heard in the last few seconds (paired or not). */
    val nearby: StateFlow<List<Nearby>> = _nearby.asStateFlow()

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private val _pairing = MutableStateFlow<Pairing?>(null)
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** What to tell the person: what came in, what went, what didn't work. */
    val messages: SharedFlow<String> = _messages

    private val _port = MutableStateFlow(0)
    /** The port this device takes connections on, while it does (0 when it doesn't). */
    val listeningPort: StateFlow<Int> = _port.asStateFlow()

    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var expiry: Job? = null
    private var users = 0
    private val discovery = LinkDiscovery(
        announcement = { if (_port.value > 0) identity.let { LinkDiscovery.announcement(it.id, it.name, _port.value) } else null },
        onHeard = ::heard,
        port = discoveryPort,
    )
    private val transferKeys = AtomicLong()
    @Volatile private var answer: CompletableDeferred<Boolean>? = null

    // ---- running ------------------------------------------------------------------------------------------

    /** Takes connections and listens for other devices while anything (a screen, the service) wants it to; [stop] when done. */
    @Synchronized
    fun start() {
        users++
        if (users > 1) return
        startServer()
        if (discover) {
            onRunning(true)
            discovery.start(scope)
        }
        expiry = scope.launch {
            while (isActive) {
                delay(2_000)
                val now = System.currentTimeMillis()
                _nearby.update { list -> list.filter { it.at >= now - FORGET_AFTER } }
            }
        }
    }

    @Synchronized
    fun stop() {
        if (users == 0) return
        users--
        if (users > 0) return
        if (discover) {
            discovery.stop()
            onRunning(false)
        }
        expiry?.cancel()
        serverJob?.cancel()
        runCatching { server?.close() }
        server = null
        _port.value = 0
        _nearby.value = emptyList()
    }

    private fun startServer() {
        val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
            ?: runCatching { ServerSocket(0) }.getOrNull()
            ?: return AppLog.w("Link", "Couldn't take connections")
        server = s
        _port.value = s.localPort
        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = try {
                    s.accept()
                } catch (e: SocketException) {
                    break
                } catch (e: Exception) {
                    continue
                }
                // Not stopped with the server: what's being taken in carries on (the service keeps KoLib running for it).
                scope.launch(Dispatchers.IO) { serve(socket) }
            }
        }
    }

    /** Whether a device has been heard on the network lately (or reached by its address). */
    fun isHere(id: String): Boolean = _nearby.value.any { it.id == id }

    private fun heard(h: LinkDiscovery.Heard) {
        val me = identity.id
        if (h.id == me) return
        val now = System.currentTimeMillis()
        val wasHere = _nearby.value.any { it.id == h.id }
        _nearby.update { list ->
            val old = list.firstOrNull { it.id == h.id }
            list.filter { it.id != h.id } + Nearby(h.id, h.name, h.address, h.port, maxOf(now, old?.at ?: 0))
        }
        val paired = store.device(h.id) ?: return
        store.seen(h.id, h.name, h.address, h.port, now)
        // A paired device just come along: bring places in books into step with it (one of the two does).
        if (!wasHere && me < h.id && store.settings.value.keepPlacesInStep) {
            scope.launch(Dispatchers.IO) {
                try {
                    syncPlaces(paired, quiet = true)
                } catch (e: Exception) {
                    AppLog.w("Link", "Couldn't bring places into step with ${paired.name}", e)
                }
            }
        }
    }

    // ---- connecting ---------------------------------------------------------------------------------------

    /** What's asked of another device, with where this one takes connections, so it can reach back. */
    private fun request(op: String) = JSONObject().put("op", op).put("port", _port.value)

    private fun open(address: String, port: Int, pairing: Boolean): LinkSession {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), 5_000)
            socket.soTimeout = 15_000
            return LinkHandshake.client(socket, identity, pairing)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    /** A connection to the paired [device], where it was last found; checked that it's really that one. */
    private fun connect(device: PairedDevice): LinkSession {
        val near = _nearby.value.firstOrNull { it.id == device.id }
        val address = near?.address ?: device.address ?: throw LinkException(tr("{0} isn't on this network. Open KoLib on it.", device.name))
        val port = near?.port ?: device.port ?: PORT
        val session = try {
            open(address, port, pairing = false)
        } catch (e: LinkException) {
            throw e
        } catch (e: Exception) {
            throw LinkException(tr("{0} couldn't be reached. Open KoLib on it, on the same network as this one.", device.name))
        }
        if (session.peerId != device.id || !LinkCrypto.same(session.peerKey, device.keyBytes())) {
            session.close()
            throw LinkException(tr("The device found there isn't {0}.", device.name))
        }
        session.waitAtMost(60_000)
        return session
    }

    /**
     * Looks for KoLib at [address] ("192.168.1.20", or with its port: "192.168.1.20:47161"), for a network where
     * devices can't hear each other; null if it isn't there.
     */
    suspend fun reach(address: String): Nearby? = withContext(Dispatchers.IO) {
        val text = address.trim()
        val host = text.substringBefore(':').trim()
        val port = text.substringAfter(':', "").trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: PORT
        if (host.isEmpty()) return@withContext null
        val session = runCatching { open(host, port, pairing = false) }.getOrNull() ?: return@withContext null
        session.use {
            runCatching { it.channel.sendJson(request("hello")) }
            if (it.peerId == identity.id) return@withContext null
            // Not heard on the network, so kept for a while longer than one that is.
            val n = Nearby(it.peerId, it.peerName, host, port, System.currentTimeMillis() + MANUAL_GRACE)
            _nearby.update { list -> list.filter { x -> x.id != n.id } + n }
            store.device(n.id)?.let { d -> if (LinkCrypto.same(d.keyBytes(), it.peerKey)) store.seen(d.id, n.name, host, port) }
            n
        }
    }

    // ---- pairing ------------------------------------------------------------------------------------------

    /** Asks [target] to pair: both show a code; once both say yes (having seen the codes are the same), they're paired. */
    fun pair(target: Nearby) {
        if (_pairing.value != null) return
        _pairing.value = Pairing(target.name, null, incoming = false)
        val mine = CompletableDeferred<Boolean>()
        answer = mine
        scope.launch(Dispatchers.IO) {
            var session: LinkSession? = null
            try {
                val s = open(target.address, target.port, pairing = true).also { session = it }
                // Put off while connecting.
                if (_pairing.value == null) return@launch
                s.waitAtMost((PAIRING_WAIT + 15_000).toInt())
                s.channel.sendJson(request("pair"))
                _pairing.value = Pairing(s.peerName, s.code, incoming = false)
                when (agree(s, mine) { _pairing.value = Pairing(s.peerName, s.code, incoming = false, confirmed = true) }) {
                    Agreed.BOTH -> {
                        store.add(PairedDevice(s.peerId, s.peerName, Base64.getEncoder().encodeToString(s.peerKey), System.currentTimeMillis(), target.address, target.port, System.currentTimeMillis()))
                        _pairing.value = null
                        _messages.tryEmit(tr("Paired with {0}.", s.peerName))
                    }
                    Agreed.NOT_ME -> _pairing.value = null
                    Agreed.NOT_THEM -> _pairing.value = Pairing(s.peerName, s.code, incoming = false, failed = tr("{0} didn't pair.", s.peerName))
                    Agreed.TOO_LONG -> _pairing.value = Pairing(s.peerName, s.code, incoming = false, failed = tr("Nobody said yes in time. Try again."))
                }
            } catch (e: Exception) {
                AppLog.w("Link", "Pairing with ${target.name} didn't work", e)
                if (_pairing.value != null) {
                    _pairing.value = Pairing(target.name, null, incoming = false, failed = (e as? LinkException)?.message ?: tr("{0} couldn't be reached.", target.name))
                }
            } finally {
                session?.close()
                if (answer === mine) answer = null
            }
        }
    }

    /** This side's yes or no to pairing; or putting away what's shown once it's over. */
    fun answer(yes: Boolean) {
        val pending = answer
        if (pending != null && !pending.isCompleted) {
            pending.complete(yes)
            if (!yes) _pairing.value = null
        } else if (_pairing.value?.failed != null || pending == null) {
            _pairing.value = null
        }
    }

    fun forget(id: String) {
        store.remove(id)
    }

    fun changeSettings(change: (LinkSettings) -> LinkSettings) = store.changeSettings(change)

    private enum class Agreed { BOTH, NOT_ME, NOT_THEM, TOO_LONG }

    /**
     * Each side says yes or no, and tells the other: paired only if both say yes in time. Each side's answer may come
     * first. Closes the connection when done.
     */
    private suspend fun agree(s: LinkSession, mine: CompletableDeferred<Boolean>, onConfirmed: () -> Unit): Agreed = coroutineScope {
        val theirs = async(Dispatchers.IO) { runCatching { s.channel.receiveJson().optBoolean("confirm") }.getOrDefault(false) }
        fun tell(yes: Boolean) = runCatching { s.channel.sendJson(JSONObject().put("confirm", yes)) }
        try {
            val deadline = System.currentTimeMillis() + PAIRING_WAIT
            val mineFirst = withTimeoutOrNull(PAIRING_WAIT) {
                select<Boolean> {
                    mine.onAwait { true }
                    theirs.onAwait { false }
                }
            }
            when (mineFirst) {
                null -> {
                    tell(false)
                    Agreed.TOO_LONG
                }
                true -> {
                    val yes = mine.await()
                    tell(yes)
                    if (!yes) Agreed.NOT_ME else {
                        onConfirmed()
                        when (withTimeoutOrNull(deadline - System.currentTimeMillis()) { theirs.await() }) {
                            true -> Agreed.BOTH
                            false -> Agreed.NOT_THEM
                            null -> Agreed.TOO_LONG
                        }
                    }
                }
                false -> if (!theirs.await()) Agreed.NOT_THEM else {
                    val yes = withTimeoutOrNull(deadline - System.currentTimeMillis()) { mine.await() }
                    tell(yes == true)
                    when (yes) {
                        true -> Agreed.BOTH
                        false -> Agreed.NOT_ME
                        null -> Agreed.TOO_LONG
                    }
                }
            }
        } finally {
            theirs.cancel()
            // Lets go of the read still waiting, if there is one, so this can end.
            s.close()
        }
    }

    private suspend fun servePairing(s: LinkSession, from: String?, theirPort: Int?) {
        if (_pairing.value != null) {
            // Already pairing with someone.
            runCatching { s.channel.sendJson(JSONObject().put("confirm", false)) }
            return
        }
        s.waitAtMost((PAIRING_WAIT + 15_000).toInt())
        val mine = CompletableDeferred<Boolean>()
        answer = mine
        _pairing.value = Pairing(s.peerName, s.code, incoming = true)
        try {
            when (agree(s, mine) { _pairing.value = Pairing(s.peerName, s.code, incoming = true, confirmed = true) }) {
                Agreed.BOTH -> {
                    store.add(PairedDevice(s.peerId, s.peerName, Base64.getEncoder().encodeToString(s.peerKey), System.currentTimeMillis(), from, theirPort, System.currentTimeMillis()))
                    _pairing.value = null
                    _messages.tryEmit(tr("Paired with {0}.", s.peerName))
                }
                Agreed.NOT_ME -> _pairing.value = null
                Agreed.NOT_THEM -> _pairing.value = Pairing(s.peerName, s.code, incoming = true, failed = tr("{0} didn't pair.", s.peerName))
                Agreed.TOO_LONG -> _pairing.value = Pairing(s.peerName, s.code, incoming = true, failed = tr("Nobody said yes in time. Try again."))
            }
        } finally {
            if (answer === mine) answer = null
        }
    }

    // ---- what's served -----------------------------------------------------------------------------------

    private suspend fun serve(socket: Socket) {
        val session = try {
            socket.soTimeout = 15_000
            LinkHandshake.server(socket, identity)
        } catch (e: Exception) {
            runCatching { socket.close() }
            return
        }
        session.use { s ->
            try {
                s.waitAtMost(60_000)
                val request = s.channel.receiveJson()
                val op = request.optString("op")
                val known = store.device(s.peerId)?.takeIf { LinkCrypto.same(it.keyBytes(), s.peerKey) }
                // Where the other device is, and takes connections: it's here, and can be reached there.
                val from = socket.inetAddress?.hostAddress
                val theirPort = request.optInt("port").takeIf { it in 1..65535 }
                if (from != null && theirPort != null && s.peerId != identity.id) {
                    val now = System.currentTimeMillis()
                    _nearby.update { list ->
                        val old = list.firstOrNull { it.id == s.peerId }
                        list.filter { it.id != s.peerId } + Nearby(s.peerId, s.peerName, from, theirPort, maxOf(now + CONNECTED_GRACE, old?.at ?: 0))
                    }
                }
                when {
                    op == "hello" -> Unit
                    op == "pair" -> servePairing(s, from, theirPort)
                    known == null -> s.channel.sendJson(JSONObject().put("ok", false).put("error", "not paired"))
                    else -> {
                        if (from != null) store.seen(known.id, s.peerName, from, theirPort ?: known.port ?: PORT)
                        when (op) {
                            "text" -> {
                                host.receiveText(request.getString("text"), s.peerName)
                                s.channel.sendJson(JSONObject().put("ok", true))
                                _messages.tryEmit(tr("Text from {0} is on the clipboard.", s.peerName))
                            }
                            "files" -> serveFiles(s, request)
                            "books" -> serveBooks(s, request)
                            "places" -> servePlaces(s, request)
                            else -> s.channel.sendJson(JSONObject().put("ok", false).put("error", "unknown"))
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.w("Link", "A connection from ${s.peerName} ended early", e)
            }
        }
    }

    private suspend fun serveFiles(s: LinkSession, request: JSONObject) {
        val list = request.getJSONArray("files")
        var where = ""
        for (i in 0 until list.length()) {
            val f = list.getJSONObject(i)
            val name = safeName(f.getString("name"))
            val size = f.getLong("size")
            val key = begin(s.peerName, name, outgoing = false, total = size)
            try {
                where = host.receiveFile(name, f.optString("type").takeIf { it.isNotBlank() }, size, s.peerName) { out ->
                    s.channel.receiveStream(out, size) { done -> progress(key, done) }
                }
            } finally {
                end(key)
            }
        }
        s.channel.sendJson(JSONObject().put("ok", true))
        _messages.tryEmit(
            if (list.length() == 1) tr("“{0}” came from {1}. It's in {2}.", safeName(list.getJSONObject(0).getString("name")), s.peerName, where)
            else tr("{0} files came from {1}. They're in {2}.", list.length(), s.peerName, where),
        )
    }

    private suspend fun serveBooks(s: LinkSession, request: JSONObject) {
        val count = request.getInt("count")
        val counts = IntArray(BookReceived.entries.size)
        for (n in 0 until count) {
            val head = s.channel.receiveJson()
            val record = head.getJSONObject("book")
            val reading = head.optJSONObject("reading") ?: JSONObject()
            val files = head.getJSONArray("files")
            val total = (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") }
            val want = try {
                host.wantBook(record, reading)
            } catch (e: Exception) {
                AppLog.w("Link", "Couldn't take '${record.optJSONObject("meta")?.optString("title")}' from ${s.peerName}", e)
                s.channel.sendJson(JSONObject().put("want", false).put("result", BookReceived.FAILED.name).put("error", e.message ?: ""))
                counts[BookReceived.FAILED.ordinal]++
                continue
            }
            if (want && host.freeSpace() < total + SPACE_TO_SPARE) {
                s.channel.sendJson(JSONObject().put("want", false).put("result", BookReceived.FAILED.name).put("error", "space"))
                counts[BookReceived.FAILED.ordinal]++
                continue
            }
            if (!want) {
                s.channel.sendJson(JSONObject().put("want", false).put("result", BookReceived.ALREADY_THERE.name))
                counts[BookReceived.ALREADY_THERE.ordinal]++
                continue
            }
            s.channel.sendJson(JSONObject().put("want", true))
            val dir = File(host.scratch(), "in-" + System.nanoTime()).apply { mkdirs() }
            val title = record.optJSONObject("meta")?.optString("title").orEmpty()
            val key = begin(s.peerName, title, outgoing = false, total = total)
            try {
                var before = 0L
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val size = f.getLong("size")
                    File(dir, safeName(f.getString("name"))).outputStream().buffered().use { out ->
                        s.channel.receiveStream(out, size) { done -> progress(key, before + done) }
                    }
                    before += size
                }
                val result = try {
                    host.receiveBook(record, reading, dir)
                } catch (e: Exception) {
                    AppLog.w("Link", "Couldn't take in '$title' from ${s.peerName}", e)
                    BookReceived.FAILED
                }
                counts[result.ordinal]++
                s.channel.sendJson(JSONObject().put("result", result.name))
            } finally {
                end(key)
                dir.deleteRecursively()
            }
        }
        _messages.tryEmit(booksMessage(s.peerName, counts, incoming = true))
    }

    private suspend fun servePlaces(s: LinkSession, request: JSONObject) {
        val theirs = request.getJSONArray("places").let { a -> (0 until a.length()).mapNotNull { BookPlace.from(a.getJSONObject(it)) } }
        val (forMe, forThem) = BookPlace.sync(host.places(), theirs)
        if (forMe.isNotEmpty()) host.applyPlaces(forMe)
        s.channel.sendJson(JSONObject().put("ok", true).put("places", JSONArray().apply { forThem.forEach { put(it.toJson()) } }))
        if (forMe.isNotEmpty()) _messages.tryEmit(tr("Places in books are in step with {0}.", s.peerName))
    }

    // ---- sending ------------------------------------------------------------------------------------------

    /** Puts [text] on [device]'s clipboard. */
    fun sendText(device: PairedDevice, text: String): Job = launchSend(device) {
        connect(device).use { s ->
            s.channel.sendJson(request("text").put("text", text))
            check(s.channel.receiveJson())
            _messages.tryEmit(tr("Sent to {0}'s clipboard.", device.name))
        }
    }

    /** Sends [files] to [device], which keeps them in its Downloads. */
    fun sendFiles(device: PairedDevice, files: List<OutgoingFile>): Job = launchSend(device) {
        if (files.isEmpty()) return@launchSend
        connect(device).use { s ->
            s.channel.sendJson(request("files").put("files", JSONArray().apply {
                files.forEach { put(JSONObject().put("name", it.name).putOpt("type", it.type).put("size", it.size)) }
            }))
            for (f in files) {
                val key = begin(device.name, f.name, outgoing = true, total = f.size)
                try {
                    f.open().use { input -> s.channel.sendStream(input, f.size) { done -> progress(key, done) } }
                } finally {
                    end(key)
                }
            }
            check(s.channel.receiveJson())
            _messages.tryEmit(if (files.size == 1) tr("Sent “{0}” to {1}.", files[0].name, device.name) else tr("Sent {0} files to {1}.", files.size, device.name))
        }
    }

    /**
     * Sends the library books [ids] to [device], with their details, covers and collections, where they were left
     * reading, and their bookmarks and highlights. A book it has already only has its reading put together.
     */
    fun sendBooks(device: PairedDevice, ids: List<String>): Job = launchSend(device) {
        val books = ids.mapNotNull { host.outgoingBook(it) }
        if (books.isEmpty()) return@launchSend
        connect(device).use { s ->
            s.channel.sendJson(request("books").put("count", books.size))
            val counts = IntArray(BookReceived.entries.size)
            var noSpace = false
            for (b in books) {
                val sizes = b.files.map { it.length() }
                s.channel.sendJson(
                    JSONObject().put("book", b.record).put("reading", b.reading).put("files", JSONArray().apply {
                        b.files.forEachIndexed { i, f -> put(JSONObject().put("name", f.name).put("size", sizes[i])) }
                    })
                )
                val reply = s.channel.receiveJson()
                if (!reply.optBoolean("want")) {
                    val r = BookReceived.entries.firstOrNull { it.name == reply.optString("result") } ?: BookReceived.FAILED
                    if (reply.optString("error") == "space") noSpace = true
                    counts[r.ordinal]++
                    continue
                }
                val key = begin(device.name, b.title, outgoing = true, total = sizes.sum())
                try {
                    var before = 0L
                    b.files.forEachIndexed { i, f ->
                        f.inputStream().buffered().use { input -> s.channel.sendStream(input, sizes[i]) { done -> progress(key, before + done) } }
                        before += sizes[i]
                    }
                } finally {
                    end(key)
                }
                val r = BookReceived.entries.firstOrNull { it.name == s.channel.receiveJson().optString("result") } ?: BookReceived.FAILED
                counts[r.ordinal]++
            }
            _messages.tryEmit(booksMessage(device.name, counts, incoming = false) + if (noSpace) " " + tr("{0} hasn't enough space left.", device.name) else "")
        }
    }

    /** Brings places in books into step with [device]: each book goes to the later of the places it was left at. */
    fun syncPlaces(device: PairedDevice): Job = launchSend(device) { syncPlaces(device, quiet = false) }

    private suspend fun syncPlaces(device: PairedDevice, quiet: Boolean) {
        connect(device).use { s ->
            s.channel.sendJson(request("places").put("places", JSONArray().apply { host.places().forEach { put(it.toJson()) } }))
            val reply = check(s.channel.receiveJson())
            val back = reply.getJSONArray("places").let { a -> (0 until a.length()).mapNotNull { BookPlace.from(a.getJSONObject(it)) } }
            if (back.isNotEmpty()) host.applyPlaces(back)
            if (!quiet || back.isNotEmpty()) _messages.tryEmit(tr("Places in books are in step with {0}.", device.name))
        }
    }

    private fun launchSend(device: PairedDevice, work: suspend () -> Unit): Job = scope.launch(Dispatchers.IO) {
        try {
            work()
        } catch (e: Exception) {
            AppLog.w("Link", "Sending to ${device.name} didn't work", e)
            _messages.tryEmit((e as? LinkException)?.message ?: tr("That didn't reach {0}: {1}", device.name, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun check(reply: JSONObject): JSONObject {
        if (!reply.optBoolean("ok")) throw LinkException(
            when (reply.optString("error")) {
                "not paired" -> tr("The other device doesn't know this one any more. Pair them again.")
                else -> tr("The other device said no.")
            }
        )
        return reply
    }

    private fun booksMessage(device: String, counts: IntArray, incoming: Boolean): String {
        fun n(r: BookReceived) = counts[r.ordinal]
        val parts = listOfNotNull(
            n(BookReceived.ADDED).takeIf { it > 0 }?.let { if (it == 1) tr("1 book added") else tr("{0} books added", it) },
            n(BookReceived.UPDATED).takeIf { it > 0 }?.let { if (it == 1) tr("1 book updated") else tr("{0} books updated", it) },
            n(BookReceived.ALREADY_THERE).takeIf { it > 0 }?.let { if (it == 1) tr("1 was there already") else tr("{0} were there already", it) },
            n(BookReceived.FAILED).takeIf { it > 0 }?.let { if (it == 1) tr("1 couldn't be taken in") else tr("{0} couldn't be taken in", it) },
        ).joinToString(", ")
        return if (incoming) tr("From {0}: {1}.", device, parts) else tr("To {0}: {1}.", device, parts)
    }

    // ---- progress -----------------------------------------------------------------------------------------

    private fun begin(device: String, what: String, outgoing: Boolean, total: Long): Long {
        val key = transferKeys.incrementAndGet()
        _transfers.update { it + Transfer(key, device, what, outgoing, 0, total) }
        return key
    }

    private fun progress(key: Long, done: Long) {
        _transfers.update { list -> list.map { if (it.key == key) it.copy(done = done) else it } }
    }

    private fun end(key: Long) {
        _transfers.update { list -> list.filter { it.key != key } }
    }

    companion object {
        const val PORT = 47161
        /** How long a device not heard from stays in the list. */
        private const val FORGET_AFTER = 9_000L
        /** A device reached by its address stays this much longer, not being heard. */
        private const val MANUAL_GRACE = 5 * 60_000L
        /** A device that connected stays this much longer, not being heard. */
        private const val CONNECTED_GRACE = 60_000L
        /** How long pairing waits for both to say yes. */
        private const val PAIRING_WAIT = 120_000L
        /** Room kept free when taking in books. */
        private const val SPACE_TO_SPARE = 64L * 1024 * 1024

        /** A file's name with nothing in it that could reach out of the folder it goes into. */
        fun safeName(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001F:*?\"<>|]"), "_").trim().trimStart('.').ifBlank { "file" }.take(150)
    }
}
