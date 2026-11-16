package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Kobo's USB connection, as [BulkOnly] uses it: commands go out, data and replies come in. Android's
 * USB classes on the tablet (see [UsbEject]); a pretend Kobo in tests.
 */
internal interface BulkPipe {
    /** Sends [data]; the number of bytes sent, or a negative number if it failed. */
    fun send(data: ByteArray, timeoutMs: Int): Int

    /** Reads up to [length] bytes into [into]; the number read, or a negative number if it failed (a stall). */
    fun receive(into: ByteArray, length: Int, timeoutMs: Int): Int

    /** Clears a stall on the way in ([fromDrive]) or out. */
    fun clearStall(fromDrive: Boolean): Boolean

    /** Starts the drive's command handling afresh (the Bulk-Only reset), after a muddle. */
    fun reset(): Boolean
}

/** The SCSI commands a computer sends when a drive is ejected. */
internal object Scsi {
    const val TEST_UNIT_READY = 0x00
    const val REQUEST_SENSE = 0x03
    const val START_STOP_UNIT = 0x1B
    const val PREVENT_ALLOW_MEDIUM_REMOVAL = 0x1E
    const val SYNCHRONIZE_CACHE_10 = 0x35

    fun testUnitReady() = ByteArray(6)
    fun requestSense(length: Int = 18) = byteArrayOf(REQUEST_SENSE.toByte(), 0, 0, 0, length.toByte(), 0)

    /** Lets the medium be removed (computers forbid it while a drive is in use). */
    fun allowRemoval() = byteArrayOf(PREVENT_ALLOW_MEDIUM_REMOVAL.toByte(), 0, 0, 0, 0, 0)

    /** Writes out whatever the drive is holding back. */
    fun synchronizeCache() = ByteArray(10).also { it[0] = SYNCHRONIZE_CACHE_10.toByte() }

    /** Stop and unload: "eject". LoEj set, Start clear. */
    fun eject() = byteArrayOf(START_STOP_UNIT.toByte(), 0, 0, 0, 0x02, 0)
}

/** Why a drive turned a command down: its sense key, and the additional code and qualifier. */
internal data class Sense(val key: Int, val asc: Int, val ascq: Int) {
    /** Nothing in the drive: it has been ejected. */
    val mediumNotPresent: Boolean get() = key == 0x02 && asc == 0x3A
    /** Removal is still forbidden. */
    val removalPrevented: Boolean get() = key == 0x05 && asc == 0x53 && ascq == 0x02
    /** It doesn't do this command. */
    val notSupported: Boolean get() = key == 0x05 && (asc == 0x20 || asc == 0x24)

    override fun toString() = "%X/%02X/%02X".format(key, asc, ascq)
}

/**
 * Just enough of USB mass storage's Bulk-Only Transport to send a drive the commands a computer sends
 * when you eject it. Each command goes out in a 31-byte wrapper (CBW), and the drive answers with a
 * 13-byte status (CSW); a command that fails is asked about with REQUEST SENSE.
 */
internal class BulkOnly(private val pipe: BulkPipe, private val log: (String) -> Unit = {}) {

    sealed interface Reply {
        data object Passed : Reply
        data class Failed(val sense: Sense?) : Reply
        /** The exchange itself went wrong: the drive stopped answering, or answered nonsense. */
        data class Broken(val why: String) : Reply
    }

    private var tag = 0x4B4C0001

    /** Sends [cb] to drive [lun]; if it fails, asks the drive why. [name] is for the log. */
    fun run(lun: Int, cb: ByteArray, name: String, timeoutMs: Int = 5000): Reply {
        val reply = when (val r = transfer(lun, cb, 0, timeoutMs).first) {
            is Reply.Failed -> Reply.Failed(sense(lun))
            else -> r
        }
        log("$name, drive ${lun + 1}: " + when (reply) {
            Reply.Passed -> "done"
            is Reply.Failed -> "turned down" + (reply.sense?.let { " (sense $it)" } ?: "")
            is Reply.Broken -> reply.why
        })
        return reply
    }

    private fun sense(lun: Int): Sense? {
        val (r, data) = transfer(lun, Scsi.requestSense(), 18, 5000)
        if (r != Reply.Passed || data.size < 14) return null
        return Sense(data[2].toInt() and 0x0F, data[12].toInt() and 0xFF, data[13].toInt() and 0xFF)
    }

    /** One command, with [dataIn] bytes to read back, and the drive's status. */
    fun transfer(lun: Int, cb: ByteArray, dataIn: Int, timeoutMs: Int): Pair<Reply, ByteArray> {
        val t = tag++
        if (pipe.send(cbw(t, lun, cb, dataIn), timeoutMs) != CBW_LENGTH) {
            recover()
            return Reply.Broken(tr("the command didn't go through")) to EMPTY
        }
        var data = EMPTY
        if (dataIn > 0) {
            val buf = ByteArray(dataIn)
            val n = pipe.receive(buf, dataIn, timeoutMs)
            if (n >= 0) data = buf.copyOf(n) else pipe.clearStall(fromDrive = true)
        }
        val csw = ByteArray(CSW_LENGTH)
        var n = pipe.receive(csw, CSW_LENGTH, timeoutMs)
        if (n < 0) {
            // A stall where the status should be: clear it and read the status again, as the standard says.
            pipe.clearStall(fromDrive = true)
            n = pipe.receive(csw, CSW_LENGTH, timeoutMs)
        }
        return when (statusOf(csw, n, t)) {
            0 -> Reply.Passed
            1 -> Reply.Failed(null)
            null -> {
                recover()
                if (n < 0) Reply.Broken(tr("the Kobo stopped answering")) else Reply.Broken(tr("the Kobo's answer made no sense"))
            }
            else -> {
                recover()
                Reply.Broken(tr("the Kobo lost track of the command"))
            }
        } to data
    }

    private fun recover() {
        pipe.reset()
        pipe.clearStall(fromDrive = true)
        pipe.clearStall(fromDrive = false)
    }

    companion object {
        const val CBW_LENGTH = 31
        const val CSW_LENGTH = 13
        private const val CBW_SIGNATURE = 0x43425355 // "USBC"
        private const val CSW_SIGNATURE = 0x53425355 // "USBS"
        private val EMPTY = ByteArray(0)

        fun cbw(tag: Int, lun: Int, cb: ByteArray, dataIn: Int): ByteArray =
            ByteBuffer.allocate(CBW_LENGTH).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(CBW_SIGNATURE)
                putInt(tag)
                putInt(dataIn)
                put(if (dataIn > 0) 0x80.toByte() else 0)
                put((lun and 0x0F).toByte())
                put(cb.size.toByte())
                put(cb.copyOf(16))
            }.array()

        /** The status in a reply to command [tag]: 0 passed, 1 failed, 2 phase error; null if it isn't one. */
        fun statusOf(csw: ByteArray, n: Int, tag: Int): Int? {
            if (n != CSW_LENGTH) return null
            val b = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
            if (b.getInt(0) != CSW_SIGNATURE || b.getInt(4) != tag) return null
            return csw[12].toInt() and 0xFF
        }
    }
}

/** How telling the Kobo it's been ejected went. */
internal data class EjectReport(
    /** How many drives the Kobo has (one; two for an older Kobo with a memory card). */
    val drives: Int,
    /** How many accepted being ejected. */
    val ejected: Int,
    /** How many then said they were empty, which shows the eject took. */
    val confirmed: Int,
    /** The Kobo stopped answering once told to eject, so whether it took can't be told. */
    val stoppedAnswering: Boolean = false,
    /** Why the last drive that turned it down did. */
    val refusal: Sense? = null,
    /** The exchange broke down before the eject was sent. */
    val broken: String? = null,
)

/**
 * Ejects each of the Kobo's drives the way a computer does: is it ready; allow removal; write out its
 * cache; unload. A drive that still forbids removal is allowed again and asked once more.
 */
internal fun ejectDrives(bulk: BulkOnly, maxLun: Int): EjectReport {
    var ejected = 0
    var confirmed = 0
    var refusal: Sense? = null
    for (lun in 0..maxLun.coerceIn(0, 15)) {
        val ready = bulk.run(lun, Scsi.testUnitReady(), "Ready?")
        if (ready is BulkOnly.Reply.Broken) return EjectReport(maxLun + 1, ejected, confirmed, broken = ready.why)
        if (ready is BulkOnly.Reply.Failed && ready.sense?.mediumNotPresent == true) {
            // Nothing in this drive (an empty card slot, or already ejected).
            ejected++
            confirmed++
            continue
        }
        bulk.run(lun, Scsi.allowRemoval(), "Allow removal")
        bulk.run(lun, Scsi.synchronizeCache(), "Write out its cache", timeoutMs = 30_000)
        var r = bulk.run(lun, Scsi.eject(), "Eject")
        if (r is BulkOnly.Reply.Failed && r.sense?.removalPrevented == true) {
            bulk.run(lun, Scsi.allowRemoval(), "Allow removal again")
            r = bulk.run(lun, Scsi.eject(), "Eject again")
        }
        when (r) {
            BulkOnly.Reply.Passed -> {
                ejected++
                val after = bulk.run(lun, Scsi.testUnitReady(), "Ready after ejecting?")
                if (after is BulkOnly.Reply.Failed && after.sense?.mediumNotPresent == true) confirmed++
            }
            is BulkOnly.Reply.Failed -> refusal = r.sense
            // Sent, but no answer: the Kobo may have gone straight off to add its books.
            is BulkOnly.Reply.Broken -> return EjectReport(maxLun + 1, ejected, confirmed, stoppedAnswering = true, refusal = refusal)
        }
    }
    return EjectReport(maxLun + 1, ejected, confirmed, refusal = refusal)
}
