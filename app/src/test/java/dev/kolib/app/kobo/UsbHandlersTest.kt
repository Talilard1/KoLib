package dev.kolib.app.kobo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

/** Telling which apps Android may open when a Kobo connects, and noticing one taking the screen from KoLib. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsbHandlersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun filters(xml: String): List<UsbFilter> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        return UsbHandlers.readFilters(parser)
    }

    @Test
    fun filtersAreReadAsAndroidReadsThem() {
        val f = filters(
            """<resources>
                 <usb-device vendor-id="8759" product-id="0x4235" />
                 <usb-device class="8" subclass="6" protocol="80" />
                 <usb-device />
                 <usb-device manufacturer-name="Kobo" product-name="eReader" vendor-id="oops" />
               </resources>""",
        )
        assertEquals(4, f.size)
        assertEquals(UsbFilter(vendorId = 0x2237, productId = 0x4235), f[0])
        assertEquals(UsbFilter(deviceClass = 8, subclass = 6, protocol = 0x50), f[1])
        assertEquals(UsbFilter(), f[2])
        // A number that isn't one is left out, as Android does.
        assertEquals(UsbFilter(manufacturer = "Kobo", product = "eReader"), f[3])
    }

    @Test
    fun aFileManagerThatTakesUsbDrivesTakesTheKobo() {
        val kobo = UsbHandlers.KOBO
        // Mass storage, by its interface (the Kobo says what it is there, not as a whole).
        assertTrue(UsbHandlers.matches(UsbFilter(deviceClass = 8), kobo))
        assertTrue(UsbHandlers.matches(UsbFilter(deviceClass = 8, subclass = 6, protocol = 0x50), kobo))
        // Every device.
        assertTrue(UsbHandlers.matches(UsbFilter(), kobo))
        // Kobo's own vendor ID.
        assertTrue(UsbHandlers.matches(UsbFilter(vendorId = 0x2237), kobo))
    }

    @Test
    fun appsForOtherDevicesDontTakeTheKobo() {
        val kobo = UsbHandlers.KOBO
        assertFalse(UsbHandlers.matches(UsbFilter(deviceClass = 3), kobo)) // keyboards and game pads
        assertFalse(UsbHandlers.matches(UsbFilter(deviceClass = 8, subclass = 2), kobo))
        assertFalse(UsbHandlers.matches(UsbFilter(vendorId = 0x1234), kobo))
        // One model of one maker's device: not a Kobo of unknown model.
        assertFalse(UsbHandlers.matches(UsbFilter(vendorId = 0x2237, productId = 0x4235), kobo))
        assertTrue(UsbHandlers.matches(UsbFilter(vendorId = 0x2237, productId = 0x4235), kobo.copy(productId = 0x4235)))
        // A maker's name that isn't the device's.
        assertFalse(UsbHandlers.matches(UsbFilter(manufacturer = "SanDisk"), kobo.copy(manufacturer = "Kobo")))
        assertTrue(UsbHandlers.matches(UsbFilter(manufacturer = "Kobo"), kobo.copy(manufacturer = "Kobo")))
    }

    @Test
    fun koLibIsOfferedForTheKoboOnlyWhenTurnedOn() {
        val pm = context.packageManager
        val alias = ComponentName(context.packageName, "dev.kolib.app.KoboConnected")
        // In the manifest, off to begin with, and for any device from Kobo.
        val info = pm.getActivityInfo(alias, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.GET_META_DATA)
        assertEquals("dev.kolib.app.MainActivity", info.targetActivity)
        val parser = info.loadXmlMetaData(pm, UsbManager.ACTION_USB_DEVICE_ATTACHED)!!
        val f = UsbHandlers.readFilters(parser)
        assertEquals(listOf(UsbFilter(vendorId = 0x2237)), f)
        assertTrue(UsbHandlers.matches(f.single(), UsbHandlers.KOBO))
        assertFalse(UsbHandlers.offeringKoLib(context))

        UsbHandlers.offerKoLib(context, true)
        assertTrue(UsbHandlers.offeringKoLib(context))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, pm.getComponentEnabledSetting(alias))
        UsbHandlers.offerKoLib(context, false)
        assertFalse(UsbHandlers.offeringKoLib(context))
    }

    @Test
    fun koLibItselfIsNeverCountedAsAnotherApp() {
        UsbHandlers.offerKoLib(context, true)
        val others = UsbHandlers.otherApps(context)
        assertTrue(others.none { it.packageName == context.packageName })
        UsbHandlers.offerKoLib(context, false)
        // Nothing else installed here takes USB devices.
        @Suppress("DEPRECATION")
        assertTrue(context.packageManager.queryIntentActivities(Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED), 0).none { it.activityInfo.packageName != context.packageName })
    }

    // ---- noticing another app taking the screen ------------------------------------------------------

    @Test
    fun goingOutOfSightJustAfterTheKoboConnectsUntouchedIsATakeover() {
        val t = UsbTakeover()
        t.attached(UsbHandlers.KOBO, at = 10_000)
        t.left(at = 10_700, lastTouch = 4_000)
        assertEquals(UsbHandlers.KOBO, t.back())
        // Said once.
        assertNull(t.back())
    }

    @Test
    fun aTapAfterTheKoboConnectedMeansKoLibOpenedSomethingItself() {
        val t = UsbTakeover()
        t.attached(UsbHandlers.KOBO, at = 10_000)
        t.left(at = 11_000, lastTouch = 10_400)
        assertNull(t.back())
    }

    @Test
    fun goingOutOfSightLongAfterIsTheUsersOwnDoing() {
        val t = UsbTakeover()
        t.attached(UsbHandlers.KOBO, at = 10_000)
        t.left(at = 20_000, lastTouch = 0)
        assertNull(t.back())
        // And with no Kobo plugged in, nothing at all.
        val none = UsbTakeover()
        none.left(at = 500, lastTouch = 0)
        assertNull(none.back())
    }

    @Test
    fun whenAndroidOpenedKoLibItselfForTheKoboNothingTookOver() {
        val t = UsbTakeover()
        t.attached(UsbHandlers.KOBO, at = 10_000)
        // Android's list of apps had the screen for a moment, and KoLib was chosen from it.
        t.left(at = 10_500, lastTouch = 4_000)
        t.forget()
        assertNull(t.back())
        // Nor is anything made of KoLib being left later, with the Kobo still plugged in.
        t.left(at = 12_000, lastTouch = 4_000)
        assertNull(t.back())
    }
}
