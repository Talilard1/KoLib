package dev.kobolibrary.app.kobo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BulkOnlyTest {

    @Test
    fun aCommandGoesOutInItsWrapper() {
        val cbw = BulkOnly.cbw(tag = 0x11223344, lun = 1, cb = Scsi.eject(), dataIn = 0)
        assertEquals(31, cbw.size)
        val b = ByteBuffer.wrap(cbw).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x43425355, b.getInt(0)) // "USBC"
        assertEquals(0x11223344, b.getInt(4))
        assertEquals(0, b.getInt(8))
        assertEquals(0, cbw[12].toInt()) // no data, so no direction
        assertEquals(1, cbw[13].toInt())
        assertEquals(6, cbw[14].toInt())
        assertArrayEquals(byteArrayOf(0x1B, 0, 0, 0, 0x02, 0), cbw.copyOfRange(15, 21)) // START STOP UNIT, LoEj
        // A command that reads data back says so, and how much.
        val sense = BulkOnly.cbw(7, 0, Scsi.requestSense(), 18)
        assertEquals(0x80, sense[12].toInt() and 0xFF)
        assertEquals(18, ByteBuffer.wrap(sense).order(ByteOrder.LITTLE_ENDIAN).getInt(8))
    }

    @Test
    fun ejectsTheWayAComputerDoes() {
        // As Android leaves it: removal forbidden, which a computer allows before ejecting.
        val kobo = PretendKobo(prevented = true)
        val log = mutableListOf<String>()
        val report = ejectDrives(BulkOnly(kobo) { log += it }, maxLun = 0)
        assertEquals(listOf("ready?", "allow", "sync", "eject", "ready?", "sense"), kobo.commands)
        assertEquals(1, report.ejected)
        assertEquals(1, report.confirmed)
        assertFalse(kobo.hasMedium[0])
        assertTrue(log.toString(), log.any { it.startsWith("Eject, drive 1: done") })
    }

    @Test
    fun aKoboStillForbiddingRemovalIsAllowedAgainAndAskedOnceMore() {
        val kobo = PretendKobo(prevented = true, ignoresFirstAllow = true)
        val report = ejectDrives(BulkOnly(kobo), maxLun = 0)
        assertEquals(listOf("ready?", "allow", "sync", "eject", "sense", "allow", "eject", "ready?", "sense"), kobo.commands)
        assertEquals(1, report.ejected)
        assertNull(report.refusal)
    }

    @Test
    fun aKoboThatWontEjectSaysWhy() {
        val kobo = PretendKobo(ejects = false)
        val report = ejectDrives(BulkOnly(kobo), maxLun = 0)
        assertEquals(0, report.ejected)
        assertEquals(Sense(0x05, 0x20, 0x00), report.refusal)
        assertTrue(kobo.hasMedium[0])
    }

    @Test
    fun aKoboThatGoesQuietOnceToldIsReportedAsSuch() {
        val kobo = PretendKobo(goesQuiet = true)
        val report = ejectDrives(BulkOnly(kobo), maxLun = 0)
        assertTrue(report.stoppedAnswering)
        assertEquals(0, report.ejected)
    }

    @Test
    fun bothDrivesOfAKoboWithACardAreEjected() {
        val kobo = PretendKobo(drives = 2)
        val report = ejectDrives(BulkOnly(kobo), maxLun = 1)
        assertEquals(2, report.ejected)
        assertEquals(2, report.confirmed)
        assertFalse(kobo.hasMedium[0] || kobo.hasMedium[1])
    }

    @Test
    fun aStallBeforeTheStatusIsClearedAndTheStatusReadAgain() {
        val kobo = PretendKobo(stallsBeforeStatus = 1)
        val (reply, _) = BulkOnly(kobo).transfer(0, Scsi.testUnitReady(), 0, 1000)
        assertEquals(BulkOnly.Reply.Passed, reply)
        assertEquals(1, kobo.stallsCleared)
    }

    /** A Kobo's USB storage, answering as Linux's mass storage gadget does. */
    private class PretendKobo(
        drives: Int = 1,
        var prevented: Boolean = false,
        val ignoresFirstAllow: Boolean = false,
        val ejects: Boolean = true,
        val goesQuiet: Boolean = false,
        var stallsBeforeStatus: Int = 0,
    ) : BulkPipe {
        val commands = mutableListOf<String>()
        val hasMedium = BooleanArray(drives) { true }
        var stallsCleared = 0
        private var status: ByteArray? = null
        private var data: ByteArray? = null
        private var sense = Triple(0, 0, 0)
        private var allows = 0
        private var gone = false

        override fun send(data: ByteArray, timeoutMs: Int): Int {
            if (gone) return -1
            val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            check(b.getInt(0) == 0x43425355)
            val tag = b.getInt(4)
            val lun = data[13].toInt()
            val cb = data.copyOfRange(15, 31)
            var failed = false
            when (cb[0].toInt() and 0xFF) {
                Scsi.TEST_UNIT_READY -> {
                    commands += "ready?"
                    if (!hasMedium[lun]) { failed = true; sense = Triple(0x02, 0x3A, 0x00) }
                }
                Scsi.REQUEST_SENSE -> {
                    commands += "sense"
                    this.data = ByteArray(18).also {
                        it[0] = 0x70
                        it[2] = sense.first.toByte()
                        it[7] = 10
                        it[12] = sense.second.toByte()
                        it[13] = sense.third.toByte()
                    }
                    sense = Triple(0, 0, 0)
                }
                Scsi.PREVENT_ALLOW_MEDIUM_REMOVAL -> {
                    commands += "allow"
                    allows++
                    if (!(ignoresFirstAllow && allows == 1)) prevented = cb[4].toInt() and 1 == 1
                }
                Scsi.SYNCHRONIZE_CACHE_10 -> commands += "sync"
                Scsi.START_STOP_UNIT -> {
                    commands += "eject"
                    when {
                        !ejects -> { failed = true; sense = Triple(0x05, 0x20, 0x00) }
                        prevented -> { failed = true; sense = Triple(0x05, 0x53, 0x02) }
                        goesQuiet -> gone = true
                        else -> hasMedium[lun] = false
                    }
                }
            }
            if (!gone) status = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x53425355).putInt(tag).putInt(0).put(if (failed) 1 else 0).array()
            return data.size
        }

        override fun receive(into: ByteArray, length: Int, timeoutMs: Int): Int {
            data?.let { d ->
                data = null
                val n = minOf(length, d.size)
                d.copyInto(into, 0, 0, n)
                return n
            }
            if (gone) return -1
            if (stallsBeforeStatus > 0) {
                stallsBeforeStatus--
                return -1
            }
            val s = status ?: return -1
            status = null
            s.copyInto(into)
            return s.size
        }

        override fun clearStall(fromDrive: Boolean): Boolean {
            stallsCleared++
            return true
        }

        override fun reset() = true
    }
}
