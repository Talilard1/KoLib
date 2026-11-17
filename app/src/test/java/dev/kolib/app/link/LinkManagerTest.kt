package dev.kolib.app.link

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Collections
import java.util.UUID

/** Two devices' links talking over the loopback: pairing, and sending text, files, books and places. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val base = File(context.cacheDir, "link-test-" + UUID.randomUUID())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** A device's side of things, kept in memory. */
    private class FakeHost(val dir: File) : LinkHost {
        val texts = Collections.synchronizedList(ArrayList<String>())
        val files = Collections.synchronizedMap(LinkedHashMap<String, ByteArray>())
        /** Books this device has: their records, and where they were left. */
        val books = Collections.synchronizedMap(LinkedHashMap<String, JSONObject>())
        val bookFiles = Collections.synchronizedMap(LinkedHashMap<String, Map<String, ByteArray>>())
        var placeList = listOf<BookPlace>()
        val applied = Collections.synchronizedList(ArrayList<BookPlace>())
        val merged = Collections.synchronizedList(ArrayList<JSONObject>())

        override fun receiveText(text: String, from: String) {
            texts += text
        }

        override suspend fun receiveFile(name: String, type: String?, size: Long, from: String, write: suspend (OutputStream) -> Unit): String {
            val out = ByteArrayOutputStream()
            write(out)
            files[name] = out.toByteArray()
            return "Downloads"
        }

        override suspend fun outgoingBook(id: String): OutgoingBook? {
            val record = books[id] ?: return null
            val folder = File(dir, "out-$id").apply { mkdirs() }
            val list = bookFiles[id].orEmpty().map { (name, bytes) -> File(folder, name).apply { writeBytes(bytes) } }
            return OutgoingBook(record.getJSONObject("meta").getString("title"), record, JSONObject().put("reader", JSONObject().put("from", id)), list)
        }

        override suspend fun wantBook(record: JSONObject, reading: JSONObject): Boolean {
            val id = record.getString("id")
            if (books.containsKey(id)) {
                merged += reading
                return false
            }
            return true
        }

        override suspend fun receiveBook(record: JSONObject, reading: JSONObject, files: File): BookReceived {
            val id = record.getString("id")
            books[id] = record
            bookFiles[id] = files.listFiles().orEmpty().associate { it.name to it.readBytes() }
            merged += reading
            return BookReceived.ADDED
        }

        override suspend fun places(): List<BookPlace> = placeList

        override suspend fun applyPlaces(places: List<BookPlace>) {
            applied += places
        }

        override fun scratch(): File = File(dir, "scratch").apply { mkdirs() }

        override fun freeSpace(): Long = Long.MAX_VALUE
    }

    private class Device(val name: String, val host: FakeHost, val link: LinkManager) {
        val messages = Collections.synchronizedList(ArrayList<String>())
    }

    private val devices = ArrayList<Device>()

    private fun device(name: String): Device {
        val dir = File(base, name).apply { mkdirs() }
        val host = FakeHost(dir)
        val link = LinkManager(LinkStore(File(dir, "link")), host, { name }, scope, port = 0, discover = false)
        return Device(name, host, link).also { d ->
            devices += d
            link.start()
            scope.launch { link.messages.collect { d.messages += it } }
        }
    }

    @After
    fun tearDown() {
        devices.forEach { it.link.stop() }
        scope.cancel()
        base.deleteRecursively()
    }

    private fun waitFor(what: String, seconds: Int = 15, done: () -> Boolean) {
        val until = System.currentTimeMillis() + seconds * 1000L
        while (!done()) {
            if (System.currentTimeMillis() > until) throw AssertionError("Waited too long for $what")
            Thread.sleep(20)
        }
    }

    private fun address(d: Device) = "127.0.0.1:${d.link.listeningPort.value}"

    /** Pairs [a] with [b], both saying yes. */
    private fun pair(a: Device, b: Device) {
        val near = runBlocking { a.link.reach(address(b)) }
        assertNotNull(near)
        a.link.pair(near!!)
        waitFor("both codes") { a.link.pairing.value?.code != null && b.link.pairing.value?.code != null }
        val asking = a.link.pairing.value!!
        val asked = b.link.pairing.value!!
        assertEquals(asking.code, asked.code)
        assertTrue(asked.incoming)
        assertEquals(a.name, asked.name)
        a.link.answer(true)
        b.link.answer(true)
        waitFor("pairing") { a.link.paired.value.any { it.id == b.link.identity.id } && b.link.paired.value.any { it.id == a.link.identity.id } }
        waitFor("dialogs closing") { a.link.pairing.value == null && b.link.pairing.value == null }
    }

    private fun Device.record(of: Device) = link.paired.value.first { it.id == of.link.identity.id }

    @Test
    fun pairedDevicesSendTextAndFiles() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        pair(phone, tablet)
        waitFor("the message") { phone.messages.any { "Paired with Tablet" in it } }

        runBlocking { phone.link.sendText(phone.record(tablet), "A line to paste").join() }
        assertEquals(listOf("A line to paste"), tablet.host.texts)

        val notes = "Chapter notes".toByteArray()
        val big = ByteArray(600_000) { (it % 13).toByte() }
        runBlocking {
            tablet.link.sendFiles(
                tablet.record(phone),
                listOf(OutgoingFile("notes.txt", "text/plain", notes.size.toLong()) { notes.inputStream() }, OutgoingFile("../map.png", null, big.size.toLong()) { big.inputStream() }),
            ).join()
        }
        assertArrayEquals(notes, phone.host.files["notes.txt"])
        // A name can't reach out of the folder files go into.
        assertArrayEquals(big, phone.host.files["map.png"])
        waitFor("the message") { phone.messages.any { "Received 2 files from Tablet" in it } }
        waitFor("the message") { tablet.messages.any { "Sent 2 files to Phone" in it } }
        assertTrue(tablet.link.transfers.value.isEmpty())
    }

    @Test
    fun aQrCodePairsTwoDevicesWithNothingToConfirm() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        val code = tablet.link.qrOffer(listOf("127.0.0.1"))
        assertNotNull(code)
        // The same code for as long as it's shown.
        assertEquals(code, tablet.link.qrOffer(listOf("127.0.0.1")))
        assertTrue(phone.link.pairFromQr(code!!))
        waitFor("pairing") { phone.link.paired.value.any { it.id == tablet.link.identity.id } && tablet.link.paired.value.any { it.id == phone.link.identity.id } }
        waitFor("dialogs closing") { phone.link.pairing.value == null && tablet.link.pairing.value == null }
        waitFor("the message") { phone.messages.any { "Paired with Tablet" in it } && tablet.messages.any { "Paired with Phone" in it } }
        runBlocking { phone.link.sendText(phone.record(tablet), "Sent after a scan").join() }
        assertEquals(listOf("Sent after a scan"), tablet.host.texts)

        // The code was good the once: another device that reads it later isn't paired.
        val other = device("Other")
        assertTrue(other.link.pairFromQr(code))
        waitFor("the refusal") { other.link.pairing.value?.failed != null }
        assertTrue(tablet.link.paired.value.none { it.id == other.link.identity.id })
        assertTrue(other.link.paired.value.isEmpty())
        other.link.answer(false)
        assertNull(other.link.pairing.value)

        // Not one of KoLib's codes.
        assertFalse(phone.link.pairFromQr("https://example.com/"))
        // With nowhere to be reached, there's no code to show.
        assertNull(tablet.link.qrOffer(emptyList()))
    }

    @Test
    fun aQrCodeOnlyPairsWithTheDeviceItCameFrom() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        val impostor = device("Impostor")
        val real = LinkQr.parse(tablet.link.qrOffer(listOf("127.0.0.1"))!!)!!
        // The tablet's code, but pointing at another device: its key isn't the tablet's.
        val forged = real.copy(port = impostor.link.listeningPort.value).encode()
        assertTrue(phone.link.pairFromQr(forged))
        waitFor("the refusal") { phone.link.pairing.value?.failed != null }
        assertTrue(phone.link.pairing.value!!.failed!!.contains("isn't the device"))
        assertTrue(phone.link.paired.value.isEmpty())
        assertTrue(impostor.link.paired.value.isEmpty())
        assertNull(impostor.link.pairing.value)
    }

    @Test
    fun devicesThatCantHearEachOtherAreFoundByLookingAround() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        // Nothing on the network but closed ports, and the tablet.
        val found = runBlocking { phone.link.lookAround(listOf("127.0.0.1:1", "127.0.0.1:${tablet.link.listeningPort.value}")) }
        assertEquals(1, found)
        assertEquals(listOf("Tablet"), phone.link.nearby.value.map { it.name })
        // The tablet learnt of the phone from its looking, and where to reach it.
        waitFor("the tablet to know the phone") { tablet.link.nearby.value.any { it.name == "Phone" } }
        assertEquals(phone.link.listeningPort.value, tablet.link.nearby.value.single().port)
        assertTrue(!phone.link.looking.value)
    }

    @Test
    fun theAddressesToLookAtAreTheOthersOnEachNetwork() {
        fun ip(s: String) = java.net.InetAddress.getByName(s) as java.net.Inet4Address
        val around = LinkDiscovery.neighbours(listOf(ip("192.168.67.79") to 24))
        assertEquals(253, around.size)
        assertTrue("192.168.67.1" in around && "192.168.67.254" in around && "192.168.67.79" !in around && "192.168.67.255" !in around)
        // A big network: only the 254 around this device.
        assertEquals(253, LinkDiscovery.neighbours(listOf(ip("10.1.2.3") to 16)).size)
        assertEquals("192.168.67.255", LinkDiscovery.broadcastOf(ip("192.168.67.79"), 24).hostAddress)
        assertEquals("10.1.255.255", LinkDiscovery.broadcastOf(ip("10.1.2.3"), 16).hostAddress)
    }

    @Test
    fun aBatchIsOneSteadyTransfer() {
        val t = Transfer(1, "Tablet", "Dune", outgoing = true, done = 50, total = 100, item = 3, items = 4)
        assertEquals((2 + 0.5f) / 4, t.fraction, 0.0001f)
        assertEquals(62, t.percent)
    }

    @Test
    fun saidNoOnEitherSideIsntPaired() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        val near = runBlocking { phone.link.reach(address(tablet)) }!!
        phone.link.pair(near)
        waitFor("both codes") { phone.link.pairing.value?.code != null && tablet.link.pairing.value?.code != null }
        phone.link.answer(true)
        tablet.link.answer(false)
        waitFor("the phone to hear no") { phone.link.pairing.value?.failed != null }
        assertEquals("Tablet didn't pair.", phone.link.pairing.value!!.failed)
        assertNull(tablet.link.pairing.value)
        assertTrue(phone.link.paired.value.isEmpty())
        assertTrue(tablet.link.paired.value.isEmpty())
        phone.link.answer(false)
        assertNull(phone.link.pairing.value)
    }

    @Test
    fun aDeviceThatIsntPairedCanOnlyAskToPair() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        pair(phone, tablet)
        // A stranger knows where the tablet is and what its key is, but the tablet doesn't know the stranger.
        val dir = File(base, "Stranger").apply { mkdirs() }
        val store = LinkStore(File(dir, "link")).apply { add(phone.record(tablet)) }
        val stranger = LinkManager(store, FakeHost(dir), { "Stranger" }, scope, port = 0, discover = false)
        stranger.start()
        val said = Collections.synchronizedList(ArrayList<String>())
        scope.launch { stranger.messages.collect { said += it } }
        runBlocking { stranger.sendText(phone.record(tablet), "Let me in").join() }
        waitFor("the answer") { said.isNotEmpty() }
        assertEquals("The other device no longer has this one paired. Pair them again.", said[0])
        stranger.stop()
        assertTrue(tablet.host.texts.isEmpty())

        // And a device at the address that isn't the one paired isn't sent to.
        val wrong = phone.record(tablet).copy(key = java.util.Base64.getEncoder().encodeToString(LinkCrypto.newKeys().public.encoded))
        runBlocking { phone.link.sendText(wrong, "Hello?").join() }
        waitFor("the answer") { phone.messages.any { "isn't Tablet" in it } }
        assertTrue(tablet.host.texts.isEmpty())
    }

    @Test
    fun booksGoOnceAndPlacesComeIntoStep() {
        val tablet = device("Tablet")
        val phone = device("Phone")
        pair(phone, tablet)
        val id = UUID.randomUUID().toString()
        tablet.host.books[id] = JSONObject().put("id", id).put("meta", JSONObject().put("title", "The Histories"))
        tablet.host.bookFiles[id] = mapOf("book.epub" to ByteArray(400_000) { 3 }, "cover.jpg" to byteArrayOf(1, 2, 3))

        runBlocking { tablet.link.sendBooks(tablet.record(phone), listOf(id)).join() }
        assertEquals(setOf(id), phone.host.books.keys)
        assertArrayEquals(ByteArray(400_000) { 3 }, phone.host.bookFiles[id]!!["book.epub"])
        assertArrayEquals(byteArrayOf(1, 2, 3), phone.host.bookFiles[id]!!["cover.jpg"])
        assertEquals(id, phone.host.merged.single().getJSONObject("reader").getString("from"))
        waitFor("the message") { tablet.messages.any { "To Phone: 1 book added." in it } }
        waitFor("the message") { phone.messages.any { "From Tablet: 1 book added." in it } }

        // Again: it's there, so only its reading is put together, and no files go.
        runBlocking { tablet.link.sendBooks(tablet.record(phone), listOf(id)).join() }
        assertEquals(2, phone.host.merged.size)
        waitFor("the message") { tablet.messages.any { "To Phone: 1 already there." in it } }

        // Places: the phone read further, later; the tablet finished another book.
        val other = UUID.randomUUID().toString()
        fun text(at: Long, book: Double) = JSONObject().put("text", JSONObject().put("path", "c1.xhtml").put("progress", 0.5).put("book", book).put("at", at))
        tablet.host.placeList = listOf(BookPlace(id, "The Histories", "Herodotus", text(1000, 0.2)), BookPlace(other, "Odyssey", "Homer", JSONObject().put("finished", 5000L)))
        phone.host.placeList = listOf(BookPlace(id, "The Histories", "Herodotus", text(2000, 0.4)), BookPlace("phone-odyssey", "Odyssey", "Homer", JSONObject()))
        runBlocking { tablet.link.syncPlaces(tablet.record(phone)).join() }
        // The tablet takes the phone's later place in the first; the phone takes the tablet's finishing the second (found by title).
        assertEquals(listOf(id), tablet.host.applied.map { it.id })
        assertEquals(2000L, tablet.host.applied.single().state.getJSONObject("text").getLong("at"))
        assertEquals(listOf("phone-odyssey"), phone.host.applied.map { it.id })
        assertEquals(5000L, phone.host.applied.single().state.getLong("finished"))
    }
}
