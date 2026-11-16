package dev.kobolibrary.app.kobo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Ejects the Kobo the way a computer does, by sending it the eject commands over USB. Android keeps
 * unmounting drives to itself, and its Unmount doesn't tell the drive anything; told directly, the Kobo
 * leaves its "connected" screen and adds the new books, without the cable being pulled.
 *
 * To send them, KoLib takes the Kobo's USB connection over from Android (with permission), so Android
 * sees its drive go as if it had been unplugged. Everything KoLib writes is already on the Kobo by then.
 */
class UsbEject(private val context: Context) {

    sealed interface Result {
        /** The Kobo accepted the eject; [confirmed] if it then said it was empty. */
        data class Ejected(val confirmed: Boolean) : Result
        /** Told to eject, it stopped answering: probably gone off to add its books. */
        data object StoppedAnswering : Result
        data object NoKobo : Result
        data object NotAllowed : Result
        /** Android wouldn't hand the connection over. */
        data class CouldNotOpen(val why: String) : Result
        /** The Kobo turned the eject down, or the exchange broke down; Android has the drive back. */
        data class Refused(val why: String) : Result
    }

    private val usb: UsbManager? get() = context.getSystemService(UsbManager::class.java)

    /** The Kobo plugged in over USB, if Android lists one. */
    fun kobo(): UsbDevice? = try {
        usb?.deviceList?.values?.firstOrNull { it.vendorId == KoboModels.USB_VENDOR_ID }
    } catch (e: Exception) {
        AppLog.w("Eject", "Couldn't list USB devices: ${e.message}")
        null
    }

    /** Asks Android to let KoLib use the Kobo's USB connection; true once it may. Waits up to two minutes. */
    suspend fun permission(device: UsbDevice): Boolean {
        val manager = usb ?: return false
        if (manager.hasPermission(device)) return true
        val action = context.packageName + ".USB_PERMISSION"
        val answer = AtomicReference<Boolean?>(null)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == action) answer.set(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            // Android fills the answer in, so the intent must be changeable (and name this app, since Android 14).
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), flags)
            AppLog.i("Eject", "Asking Android to let KoLib use the Kobo's USB connection.")
            manager.requestPermission(device, pending)
            // The answer comes as a broadcast; Android is asked too, in case the broadcast goes astray.
            val granted = withTimeoutOrNull(120_000) {
                var g: Boolean? = null
                while (g == null) {
                    g = answer.get()?.let { it || manager.hasPermission(device) } ?: if (manager.hasPermission(device)) true else null
                    if (g == null) delay(250)
                }
                g
            } ?: false
            AppLog.i("Eject", if (granted) "Allowed." else "Not allowed.")
            return granted
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Sends the eject. Call once KoLib has finished with the Kobo, and [permission] has been given. */
    suspend fun eject(device: UsbDevice): Result = withContext(Dispatchers.IO) {
        val manager = usb ?: return@withContext Result.NoKobo
        AppLog.i(
            "Eject",
            "The Kobo is ${runCatching { device.productName }.getOrNull() ?: "unnamed"} (USB ${"%04x".format(device.vendorId)}:${"%04x".format(device.productId)}), " +
                "with ${device.interfaceCount} interface(s).",
        )
        val intf = (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE && it.interfaceProtocol == BULK_ONLY
        } ?: return@withContext Result.CouldNotOpen(tr("The Kobo didn't offer its storage over USB."))
        val endpoints = (0 until intf.endpointCount).map { intf.getEndpoint(it) }.filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val bulkIn = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        val bulkOut = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (bulkIn == null || bulkOut == null) return@withContext Result.CouldNotOpen(tr("The Kobo's storage had no way in or out."))

        val conn = manager.openDevice(device) ?: return@withContext Result.CouldNotOpen(tr("Android wouldn't open the Kobo's USB connection."))
        // Taking it from Android's own storage driver: from here, Android sees the drive as unplugged.
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            return@withContext Result.CouldNotOpen(tr("Android wouldn't hand the Kobo's storage over."))
        }
        AppLog.i("Eject", "KoLib has the Kobo's USB connection; Android has let go of its drive.")
        val maxLun = maxLun(conn, intf)
        val pipe = AndroidPipe(conn, intf, bulkIn, bulkOut)
        val report = ejectDrives(BulkOnly(pipe) { AppLog.i("Eject", it) }, maxLun)
        AppLog.i("Eject", "Ejected ${report.ejected} of ${report.drives} drive(s), ${report.confirmed} said they're empty.")

        val result = when {
            report.ejected > 0 && report.ejected == report.drives -> Result.Ejected(report.confirmed == report.drives)
            report.stoppedAnswering -> Result.StoppedAnswering
            report.refusal != null -> Result.Refused(tr("The Kobo didn't accept being ejected (sense {0}).", report.refusal.toString()))
            report.broken != null -> Result.Refused(tr("The exchange with the Kobo broke down: {0}.", report.broken))
            else -> Result.Refused(tr("The Kobo didn't accept being ejected."))
        }
        if (result is Result.Refused) {
            // Android's storage driver takes the Kobo back, so its drive comes back as it was.
            runCatching { conn.releaseInterface(intf) }
        }
        // Once ejected, the connection is left as it is: given back, Android would find an empty drive.
        conn.close()
        result
    }

    /** How many drives the Kobo has, less one (Bulk-Only's GET MAX LUN); 0 if it doesn't say. */
    private fun maxLun(conn: UsbDeviceConnection, intf: UsbInterface): Int {
        val b = ByteArray(1)
        val n = conn.controlTransfer(0xA1, 0xFE, 0, intf.id, b, 1, 1000)
        return (if (n == 1) b[0].toInt() and 0x0F else 0).also { AppLog.i("Eject", "The Kobo has ${it + 1} drive(s).") }
    }

    private class AndroidPipe(
        private val conn: UsbDeviceConnection,
        private val intf: UsbInterface,
        private val bulkIn: UsbEndpoint,
        private val bulkOut: UsbEndpoint,
    ) : BulkPipe {
        override fun send(data: ByteArray, timeoutMs: Int) = conn.bulkTransfer(bulkOut, data, data.size, timeoutMs)
        override fun receive(into: ByteArray, length: Int, timeoutMs: Int) = conn.bulkTransfer(bulkIn, into, length, timeoutMs)

        /** CLEAR_FEATURE(ENDPOINT_HALT) on the endpoint. */
        override fun clearStall(fromDrive: Boolean) =
            conn.controlTransfer(0x02, 0x01, 0, (if (fromDrive) bulkIn else bulkOut).address, null, 0, 1000) >= 0

        /** Bulk-Only Mass Storage Reset. */
        override fun reset() = conn.controlTransfer(0x21, 0xFF, 0, intf.id, null, 0, 1000) >= 0
    }

    private companion object {
        /** USB mass storage's Bulk-Only Transport. */
        const val BULK_ONLY = 0x50
    }
}
