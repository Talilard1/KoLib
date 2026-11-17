package dev.kobolibrary.app.link

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.kobolibrary.app.R
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.tr

/**
 * A shortcut for each paired device: other apps' Share offers it in its top row (sharing shortcuts, as KDE Connect's
 * devices are), and holding KoLib's icon shows it. Shared to, it sends straight to that device; opened from the icon,
 * it asks what to send. Android puts the ones used most first: each send to a device is reported by putting its
 * shortcut out again (which counts as a use), so they're put out only when new, and changed in place when renamed.
 */
object DeviceShortcuts {
    /** The category the share target in res/xml/shortcuts.xml takes. */
    const val CATEGORY = "dev.kobolibrary.app.category.SEND_TO_DEVICE"
    private const val PREFIX = "device-"

    /** The device a shortcut ([Intent.EXTRA_SHORTCUT_ID]) is for; null if it isn't one of these. */
    fun deviceOf(shortcutId: String?): String? = shortcutId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    /** One shortcut for each of [devices], and none for a device no longer paired. */
    fun update(context: Context, devices: List<PairedDevice>) {
        runCatching {
            val wanted = devices.map { PREFIX + it.id }.toSet()
            val have = ShortcutManagerCompat.getDynamicShortcuts(context).associateBy { it.id }
            val known = have.keys.toList() +
                runCatching {
                    ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED).map { it.id }
                }.getOrDefault(emptyList())
            val stale = known.distinct().filter { it.startsWith(PREFIX) && it !in wanted }
            if (stale.isNotEmpty()) {
                // Gone from the launcher and from Share, where Android keeps long-lived ones it has shown.
                ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
                ShortcutManagerCompat.removeLongLivedShortcuts(context, stale)
            }
            devices.filter { PREFIX + it.id !in have }.forEach { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut(context, it)) }
            val renamed = devices.filter { d -> have[PREFIX + d.id]?.let { it.shortLabel.toString() != d.name } == true }
            if (renamed.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, renamed.map { shortcut(context, it) })
        }.onFailure { AppLog.w("Link", "Couldn't offer the paired devices in Share", it) }
    }

    /** Something was sent to [device]: its shortcut moves up. */
    fun used(context: Context, device: PairedDevice) {
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut(context, device)) }
    }

    private fun shortcut(context: Context, d: PairedDevice): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, PREFIX + d.id)
            .setShortLabel(d.name)
            .setLongLabel(tr("Send to {0}", d.name))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_device))
            .setIntent(
                Intent(context, ShareToDeviceActivity::class.java)
                    .setAction(ShareToDeviceActivity.ACTION_CHOOSE)
                    .putExtra(ShareToDeviceActivity.EXTRA_DEVICE, d.id)
            )
            .setLongLived(true)
            .setCategories(setOf(CATEGORY))
            .build()
}
