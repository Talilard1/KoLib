package dev.kolib.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.UsbOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.kolib.app.Connection
import dev.kolib.app.UsbKobo
import dev.kolib.app.tr

/** What the top of the library says about the Kobo while it's opened, saved to, ejected or unplugged. */
enum class KoboNotice { OPENING, SLOW, CHECKING, UPDATING, SAFE_TO_UNPLUG, UNPLUGGED }

/** How long the library says the Kobo is unplugged, once it has been. */
const val UNPLUGGED_NOTICE_MS = 10_000L

/**
 * The Kobo window's notices, for the top of the library: all but "Not connected", since the library shows
 * nothing of the Kobo's that could be out of date, and "unplugged" just for a while ([justUnplugged]).
 */
fun libraryNotice(connection: Connection, usb: UsbKobo, syncing: Boolean, checking: Boolean, justUnplugged: Boolean): KoboNotice? =
    libraryNotice(
        open = connection is Connection.Connected && !connection.unplugged,
        connecting = connection is Connection.Working,
        usb = usb, syncing = syncing, checking = checking, justUnplugged = justUnplugged,
    )

/** [open]: a Kobo is open here; [connecting]: one is being read. */
fun libraryNotice(open: Boolean, connecting: Boolean, usb: UsbKobo, syncing: Boolean, checking: Boolean, justUnplugged: Boolean): KoboNotice? =
    when {
        !open && (connecting || usb == UsbKobo.OPENING) -> KoboNotice.OPENING
        !open && usb == UsbKobo.SLOW -> KoboNotice.SLOW
        !open && usb == UsbKobo.UNMOUNTED -> KoboNotice.SAFE_TO_UNPLUG
        open && syncing -> KoboNotice.UPDATING
        open && checking -> KoboNotice.CHECKING
        !open && justUnplugged -> KoboNotice.UNPLUGGED
        else -> null
    }

/**
 * The Kobo had been opened here and is now unplugged altogether (not just unmounted): the moment the
 * library starts saying so.
 */
fun koboGone(connection: Connection, usb: UsbKobo): Boolean =
    koboGone(driveGone = connection is Connection.Connected && connection.unplugged, usb = usb)

/** [driveGone]: the drive of a Kobo that was open here has gone; [usb]: what the Kobo is doing on USB. */
fun koboGone(driveGone: Boolean, usb: UsbKobo): Boolean = driveGone && usb == UsbKobo.NONE

/** The library's notice about the Kobo, sliding open and shut as it comes and goes. */
@Composable
internal fun KoboNoticeBar(notice: KoboNotice?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = notice,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) using SizeTransform(clip = true) },
        modifier = modifier.fillMaxWidth(),
        label = "Kobo notice",
    ) { n ->
        when (n) {
            null -> Spacer(Modifier.fillMaxWidth())
            KoboNotice.OPENING -> OpeningBanner(slow = false)
            KoboNotice.SLOW -> OpeningBanner(slow = true)
            KoboNotice.CHECKING -> SyncingBanner(tr("Checking the Kobo… Don't eject it yet."))
            KoboNotice.UPDATING -> SyncingBanner()
            KoboNotice.SAFE_TO_UNPLUG -> SafeToUnplugBanner()
            KoboNotice.UNPLUGGED -> NoticeBanner(
                Icons.Outlined.UsbOff, tr("The Kobo is unplugged."),
                MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * A Kobo has been plugged in (Connect tapped on it), and Android is checking and opening its drive. This takes a
 * few seconds, and without this the app would look as if nothing were happening.
 */
@Composable
internal fun OpeningBanner(slow: Boolean) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().testTag("kobo-notice")) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                usbKoboText(if (slow) UsbKobo.SLOW else UsbKobo.OPENING).orEmpty(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The Kobo has been unmounted and is still plugged in: it can be unplugged. */
@Composable
internal fun SafeToUnplugBanner() = NoticeBanner(
    Icons.Outlined.CheckCircle, usbKoboText(UsbKobo.UNMOUNTED).orEmpty(),
    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
)

/** Something is being saved to the Kobo, so it mustn't be ejected yet. */
@Composable
internal fun SyncingBanner(text: String = tr("Updating the Kobo… Don't eject it yet.")) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().testTag("kobo-notice")) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoticeBanner(icon: ImageVector, text: String, background: Color, content: Color) {
    Surface(color = background, modifier = Modifier.fillMaxWidth().testTag("kobo-notice")) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = content)
            Spacer(Modifier.width(12.dp))
            Text(text, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
