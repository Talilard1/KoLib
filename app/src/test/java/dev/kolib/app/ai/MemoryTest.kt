package dev.kolib.app.ai

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTest {
    private val gb = 1_000_000_000L

    @Test
    fun weightsAreCopiedOnlyWhereTheresRoomToSpare() {
        // An 8 GB tablet with 3 GB free: KoLib's E2B (1.1 GB copied) and a four-billion model's usual size (2.2 GB) are;
        // the bigger size of it that closed KoLib (2.9 GB) isn't.
        val tablet = copyRoom((7.4 * gb).toLong(), 3 * gb)
        assertTrue(1_070_000_000L <= tablet)
        assertTrue(2_171_000_000L <= tablet)
        assertTrue(2_928_000_000L > tablet)
        // A 12 GB phone with 5 GB free: that bigger size is.
        assertTrue(2_928_000_000L <= copyRoom((11.2 * gb).toLong(), 5 * gb))
        // With little free, less is; with next to nothing, nothing.
        assertTrue(2_171_000_000L > copyRoom((7.4 * gb).toLong(), 2 * gb))
        assertEquals(0L, copyRoom((7.4 * gb).toLong(), 400_000_000L))
        assertEquals(0L, copyRoom(0, 0))
    }

    @Test
    fun beingClosedWithAModelHeldIsToldOfWhenItWasForMemoryOrAFailure() {
        val seen = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        assertEquals(AiClosed.MEMORY, closedFor(true, ApplicationExitInfo.REASON_LOW_MEMORY, 0, seen))
        assertEquals(AiClosed.MEMORY, closedFor(true, ApplicationExitInfo.REASON_SIGNALED, 9, seen))
        assertEquals(AiClosed.CRASH, closedFor(true, ApplicationExitInfo.REASON_CRASH_NATIVE, 0, seen))
        // Working for the user out of sight (its notification showing) counts too.
        assertEquals(AiClosed.MEMORY, closedFor(true, ApplicationExitInfo.REASON_LOW_MEMORY, 0, RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE))
        // Not: with no model held; closed in the background, where apps are closed for memory all the time; closed by the user.
        assertNull(closedFor(false, ApplicationExitInfo.REASON_LOW_MEMORY, 0, seen))
        assertNull(closedFor(true, ApplicationExitInfo.REASON_LOW_MEMORY, 0, RunningAppProcessInfo.IMPORTANCE_CACHED))
        assertNull(closedFor(true, ApplicationExitInfo.REASON_USER_REQUESTED, 0, seen))
        assertNull(closedFor(true, ApplicationExitInfo.REASON_SIGNALED, 15, seen))
    }
}
