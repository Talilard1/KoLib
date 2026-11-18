package dev.kolib.app.kobo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dev.kolib.app.log.AppLog
import org.xmlpull.v1.XmlPullParser

/** A `<usb-device>` line in an app's device filter: what Android matches a plugged-in USB device against. */
data class UsbFilter(
    val vendorId: Int = -1,
    val productId: Int = -1,
    val deviceClass: Int = -1,
    val subclass: Int = -1,
    val protocol: Int = -1,
    val manufacturer: String? = null,
    val product: String? = null,
    val serial: String? = null,
)

/** What a USB device says it is, as far as matching it to apps goes. */
data class UsbDescription(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val subclass: Int,
    val protocol: Int,
    /** Each of its interfaces' class, subclass and protocol. */
    val interfaces: List<Triple<Int, Int, Int>>,
    val manufacturer: String? = null,
    val product: String? = null,
)

/** An app Android may open when a Kobo is connected: its name, and its package (for its settings). */
data class UsbApp(val name: String, val packageName: String)

/**
 * The apps Android may open when a Kobo is connected. An app that declares it handles a USB device (a file
 * manager that takes USB drives, say) is opened when one is plugged in, and if it's the only such app, or was
 * chosen with "Always", it opens by itself and takes the screen from KoLib. KoLib can't stop that, but it can
 * be one of those apps, so it can be chosen instead, and it can say how to stop another app opening.
 */
object UsbHandlers {

    /** A Kobo once Connect is tapped on it: a USB drive (mass storage, SCSI, bulk-only) from Kobo. */
    val KOBO = UsbDescription(KoboModels.USB_VENDOR_ID, -1, 0, 0, 0, listOf(Triple(8, 6, 0x50)))

    fun describe(d: UsbDevice) = UsbDescription(
        d.vendorId, d.productId, d.deviceClass, d.deviceSubclass, d.deviceProtocol,
        (0 until d.interfaceCount).map { i -> d.getInterface(i).let { Triple(it.interfaceClass, it.interfaceSubclass, it.interfaceProtocol) } },
        runCatching { d.manufacturerName }.getOrNull(),
        runCatching { d.productName }.getOrNull(),
    )

    /** The `<usb-device>` filters in an app's device filter resource, read as Android reads them. */
    fun readFilters(parser: XmlPullParser): List<UsbFilter> {
        val out = ArrayList<UsbFilter>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "usb-device") out += readFilter(parser)
            event = parser.next()
        }
        return out
    }

    private fun readFilter(parser: XmlPullParser): UsbFilter {
        var f = UsbFilter()
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i) ?: continue
            f = when (name) {
                "manufacturer-name" -> f.copy(manufacturer = value)
                "product-name" -> f.copy(product = value)
                "serial-number" -> f.copy(serial = value)
                else -> {
                    // Numbers, written in decimal or as 0x… hex.
                    val n = (if (value.length > 2 && value[0] == '0' && (value[1] == 'x' || value[1] == 'X')) value.substring(2).toIntOrNull(16) else value.toIntOrNull())
                        ?: continue
                    when (name) {
                        "vendor-id" -> f.copy(vendorId = n)
                        "product-id" -> f.copy(productId = n)
                        "class" -> f.copy(deviceClass = n)
                        "subclass" -> f.copy(subclass = n)
                        "protocol" -> f.copy(protocol = n)
                        else -> f
                    }
                }
            }
        }
        return f
    }

    /**
     * Whether Android offers an app with filter [f] for device [d]: everything the filter names has to be
     * so, and its class (if it names one) has to be the device's or one of its interfaces'. A filter for one
     * serial number can't be checked without asking for the device, so it's taken to be this one.
     */
    fun matches(f: UsbFilter, d: UsbDescription): Boolean {
        if (f.vendorId != -1 && d.vendorId != f.vendorId) return false
        if (f.productId != -1 && d.productId != f.productId) return false
        if (f.manufacturer != null && f.manufacturer != d.manufacturer) return false
        if (f.product != null && f.product != d.product) return false
        if (classMatches(f, d.deviceClass, d.subclass, d.protocol)) return true
        return d.interfaces.any { (c, s, p) -> classMatches(f, c, s, p) }
    }

    private fun classMatches(f: UsbFilter, c: Int, s: Int, p: Int) =
        (f.deviceClass == -1 || f.deviceClass == c) && (f.subclass == -1 || f.subclass == s) && (f.protocol == -1 || f.protocol == p)

    /** The apps other than KoLib that Android may open when [device] is connected, by name. */
    fun otherApps(context: Context, device: UsbDescription = KOBO): List<UsbApp> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val handlers = runCatching { pm.queryIntentActivities(Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED), PackageManager.GET_META_DATA) }
            .getOrElse {
                AppLog.w("USB", "Couldn't list the apps that take USB devices: ${it.message}")
                emptyList()
            }
        return handlers
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .filter { info ->
                runCatching {
                    val parser = info.activityInfo.loadXmlMetaData(pm, UsbManager.ACTION_USB_DEVICE_ATTACHED) ?: return@runCatching false
                    try {
                        readFilters(parser).any { matches(it, device) }
                    } finally {
                        parser.close()
                    }
                }.getOrDefault(false)
            }
            .map { UsbApp(it.activityInfo.applicationInfo.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
    }

    private fun alias(context: Context) = ComponentName(context.packageName, "dev.kolib.app.KoboConnected")

    /**
     * Whether Android offers KoLib when a Kobo is connected. It does while another app could take the Kobo
     * ([on]), so KoLib can be chosen instead; otherwise not, or Android would ask about KoLib each time.
     */
    fun offerKoLib(context: Context, on: Boolean) {
        val pm = context.packageManager
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        runCatching {
            if (pm.getComponentEnabledSetting(alias(context)) == state) return
            pm.setComponentEnabledSetting(alias(context), state, PackageManager.DONT_KILL_APP)
            AppLog.i("USB", if (on) "Android offers KoLib when the Kobo connects, beside the other apps that take it." else "Android no longer offers KoLib when the Kobo connects.")
        }.onFailure { AppLog.w("USB", "Couldn't change whether Android offers KoLib for the Kobo: ${it.message}") }
    }

    fun offeringKoLib(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}

/**
 * Notices another app taking the screen from KoLib when a Kobo connects: KoLib went out of sight just
 * after the Kobo was plugged in, with nothing tapped in KoLib since (so it wasn't KoLib opening something).
 */
class UsbTakeover(
    /** How soon after: an app opened over KoLib can take a moment to start, more on a slow device. */
    private val withinMs: Long = 6_000,
) {
    private var attachedAt = 0L
    private var device: UsbDescription? = null
    private var pending: UsbDescription? = null

    /** A Kobo was plugged in at [at] (milliseconds since start-up). */
    fun attached(device: UsbDescription, at: Long) {
        this.device = device
        attachedAt = at
    }

    /** KoLib went out of sight at [at]; it was last touched at [lastTouch]. */
    fun left(at: Long, lastTouch: Long) {
        val d = device ?: return
        if (at - attachedAt in 0..withinMs && lastTouch < attachedAt) pending = d
    }

    /** KoLib is back: the Kobo another app may have opened for, if one did (once). */
    fun back(): UsbDescription? = pending.also { pending = null }

    /** It was KoLib that Android opened for the Kobo (chosen for it, perhaps just now): no other app took over. */
    fun forget() {
        pending = null
        device = null
    }
}
