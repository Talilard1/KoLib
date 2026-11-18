package dev.kolib.app.ui

import dev.kolib.app.Connection
import dev.kolib.app.UsbKobo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the top of the library says about the Kobo, from plugging it in to unplugging it. */
class KoboNoticeTest {

    private fun notice(
        open: Boolean = false,
        connecting: Boolean = false,
        usb: UsbKobo = UsbKobo.NONE,
        syncing: Boolean = false,
        checking: Boolean = false,
        justUnplugged: Boolean = false,
    ) = libraryNotice(open, connecting, usb, syncing, checking, justUnplugged)

    @Test
    fun pluggingInSaysNotToUnplugIt() {
        assertEquals(KoboNotice.OPENING, notice(usb = UsbKobo.OPENING))
        assertEquals(KoboNotice.SLOW, notice(usb = UsbKobo.SLOW))
        assertEquals(KoboNotice.OPENING, notice(connecting = true))
        assertEquals(KoboNotice.OPENING, libraryNotice(Connection.Working("Reading the Kobo…"), UsbKobo.NONE, false, false, false))
        assertEquals(KoboNotice.SLOW, libraryNotice(Connection.Waiting(UsbKobo.SLOW), UsbKobo.SLOW, false, false, false))
    }

    @Test
    fun whileItsOpenOnlySavingIsMentioned() {
        assertNull(notice(open = true))
        assertEquals(KoboNotice.CHECKING, notice(open = true, checking = true))
        assertEquals(KoboNotice.UPDATING, notice(open = true, syncing = true, checking = true))
    }

    @Test
    fun ejectedItsSafeToUnplugThenUnplugged() {
        // Unmounted in Android's settings, still plugged in.
        assertEquals(KoboNotice.SAFE_TO_UNPLUG, notice(usb = UsbKobo.UNMOUNTED))
        // Unplugged: said for a while, then nothing (and never "Not connected").
        assertTrue(koboGone(driveGone = true, usb = UsbKobo.NONE))
        assertEquals(KoboNotice.UNPLUGGED, notice(justUnplugged = true))
        assertNull(notice())
        // Not while it's still plugged in, nor if it was never open here.
        assertFalse(koboGone(driveGone = true, usb = UsbKobo.UNMOUNTED))
        assertFalse(koboGone(Connection.Waiting(), UsbKobo.NONE))
        assertNull(libraryNotice(Connection.Waiting(), UsbKobo.NONE, false, false, false))
    }

    @Test
    fun pluggedInAgainItsOpeningOnceMore() {
        assertEquals(KoboNotice.OPENING, notice(usb = UsbKobo.OPENING, justUnplugged = true))
    }
}
