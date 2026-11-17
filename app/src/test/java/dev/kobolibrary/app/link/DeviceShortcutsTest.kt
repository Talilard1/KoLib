package dev.kobolibrary.app.link

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each paired device offered at the top of other apps' Share (a sharing shortcut), and no longer once it's forgotten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tablet = PairedDevice("tab-1", "Galaxy Tab S9", "", 0L)
    private val phone = PairedDevice("phone-1", "Galaxy A72", "", 0L)

    private fun offered() = ShortcutManagerCompat.getDynamicShortcuts(context).filter { DeviceShortcuts.deviceOf(it.id) != null }

    @Test
    fun eachPairedDeviceIsOfferedUntilItsForgotten() {
        DeviceShortcuts.update(context, listOf(tablet, phone))
        val made = offered()
        assertEquals(setOf("tab-1", "phone-1"), made.mapNotNull { DeviceShortcuts.deviceOf(it.id) }.toSet())
        // In Share's top row (the share target's category), kept there (long-lived), by the device's name.
        assertTrue(made.all { DeviceShortcuts.CATEGORY in it.categories.orEmpty() })
        val tab = made.first { DeviceShortcuts.deviceOf(it.id) == "tab-1" }
        assertEquals("Galaxy Tab S9", tab.shortLabel.toString())
        // Opened from KoLib's icon, it asks what to send to that device.
        assertEquals(ShareToDeviceActivity.ACTION_CHOOSE, tab.intent.action)
        assertEquals("tab-1", tab.intent.getStringExtra(ShareToDeviceActivity.EXTRA_DEVICE))
        // A renamed device keeps its one shortcut, under its new name; a forgotten one loses it.
        DeviceShortcuts.update(context, listOf(tablet.copy(name = "Tab"), phone))
        assertEquals(2, offered().size)
        assertEquals("Tab", offered().first { DeviceShortcuts.deviceOf(it.id) == "tab-1" }.shortLabel.toString())
        // A send to one counts as a use of its shortcut, which stays the one.
        DeviceShortcuts.used(context, phone)
        assertEquals(2, offered().size)
        DeviceShortcuts.update(context, listOf(phone))
        assertEquals(listOf("phone-1"), offered().mapNotNull { DeviceShortcuts.deviceOf(it.id) })
        DeviceShortcuts.update(context, emptyList())
        assertTrue(offered().isEmpty())
    }

    @Test
    fun onlyItsOwnShortcutsAreDevices() {
        assertEquals("tab-1", DeviceShortcuts.deviceOf("device-tab-1"))
        assertNull(DeviceShortcuts.deviceOf("tab-1"))
        assertNull(DeviceShortcuts.deviceOf(null))
    }
}
