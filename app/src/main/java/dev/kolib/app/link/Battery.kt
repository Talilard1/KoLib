package dev.kolib.app.link

import android.content.Context
import android.os.BatteryManager

/** How charged this device is, in percent: told to paired devices, which show it. Null where it can't be told. */
fun batteryPercent(context: Context): Int? =
    runCatching { context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
        .getOrNull()?.takeIf { it in 0..100 }
