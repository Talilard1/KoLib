package dev.kolib.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbKoboTrackerTest {
    private val kobo = "/dev/bus/usb/001/002"
    private val again = "/dev/bus/usb/001/003"

    @Test
    fun pluggedInOpenedUnmountedUnplugged() {
        val t = UsbKoboTracker(patienceMs = 40_000)
        assertEquals(UsbKobo.NONE, t.update(null, driveOpen = false, now = 0))
        // Connect tapped: the Kobo appears over USB, its drive a few seconds later.
        assertEquals(UsbKobo.OPENING, t.update(kobo, driveOpen = false, now = 1_000))
        assertEquals(UsbKobo.OPENING, t.update(kobo, driveOpen = false, now = 9_000))
        assertEquals(UsbKobo.NONE, t.update(kobo, driveOpen = true, now = 9_500))
        assertEquals(UsbKobo.NONE, t.update(kobo, driveOpen = true, now = 60_000))
        // Unmounted in Android's settings: still plugged in.
        assertEquals(UsbKobo.UNMOUNTED, t.update(kobo, driveOpen = false, now = 70_000))
        assertEquals(UsbKobo.UNMOUNTED, t.update(kobo, driveOpen = false, now = 200_000))
        // Mounted again from Android's settings.
        assertEquals(UsbKobo.NONE, t.update(kobo, driveOpen = true, now = 210_000))
        // Unplugged.
        assertEquals(UsbKobo.NONE, t.update(null, driveOpen = false, now = 220_000))
    }

    @Test
    fun pluggedInAgainIsOpeningNotUnmounted() {
        val t = UsbKoboTracker()
        t.update(kobo, driveOpen = true, now = 0)
        assertEquals(UsbKobo.NONE, t.update(null, driveOpen = false, now = 5_000))
        // Plugged in again (a new USB device): its drive isn't open yet, which isn't an unmount.
        assertEquals(UsbKobo.OPENING, t.update(again, driveOpen = false, now = 30_000))
        assertEquals(UsbKobo.NONE, t.update(again, driveOpen = true, now = 36_000))
    }

    @Test
    fun replacedWithoutBeingSeenUnpluggedIsOpening() {
        // Unplugged and plugged in again between two looks: the device's name has changed.
        val t = UsbKoboTracker()
        t.update(kobo, driveOpen = true, now = 0)
        assertEquals(UsbKobo.OPENING, t.update(again, driveOpen = false, now = 2_000))
    }

    @Test
    fun androidTakingTooLongIsSlow() {
        val t = UsbKoboTracker(patienceMs = 40_000)
        assertEquals(UsbKobo.OPENING, t.update(kobo, driveOpen = false, now = 100_000))
        assertEquals(UsbKobo.OPENING, t.update(kobo, driveOpen = false, now = 139_999))
        assertEquals(UsbKobo.SLOW, t.update(kobo, driveOpen = false, now = 140_000))
        assertEquals(UsbKobo.NONE, t.update(kobo, driveOpen = true, now = 150_000))
        assertEquals(100_000L, t.since)
    }

    @Test
    fun alreadyOpenWhenKoLibStarts() {
        val t = UsbKoboTracker()
        assertEquals(UsbKobo.NONE, t.update(kobo, driveOpen = true, now = 0))
        assertEquals(UsbKobo.UNMOUNTED, t.update(kobo, driveOpen = false, now = 1_000))
    }
}
