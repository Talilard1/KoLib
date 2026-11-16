package dev.kobolibrary.app

/** A Kobo plugged in over USB, as far as Android shows it. */
enum class UsbKobo {
    /** None is plugged in, or its drive is open. */
    NONE,

    /** It has just been plugged in (it appears once Connect is tapped on it): Android is checking and opening its drive. */
    OPENING,

    /** It was plugged in a while ago, and Android still hasn't opened its drive. */
    SLOW,

    /** Its drive was open and has been unmounted, and it's still plugged in: it's safe to unplug. */
    UNMOUNTED,
}

/**
 * Follows a Kobo plugged in over USB. Android lists it as soon as Connect is tapped on it, and opens its drive a
 * few seconds later, once it has checked it: until then it's [UsbKobo.OPENING], so KoLib can say so and the Kobo
 * isn't unplugged in the belief that nothing is happening. A drive that goes while the same Kobo stays plugged in
 * was unmounted: [UsbKobo.UNMOUNTED], safe to unplug.
 */
class UsbKoboTracker(
    /** How long Android usually takes at most to check and open a Kobo's drive once it's plugged in. */
    val patienceMs: Long = 40_000,
) {
    /** Android's name for the Kobo's USB device: a new one each time it's plugged in. */
    var name: String? = null
        private set

    /** When it was plugged in (or first seen), in milliseconds. */
    var since = 0L
        private set

    /** Its drive has been open since it was plugged in. */
    private var opened = false

    /** What the Kobo is doing, given the USB device Android lists for it (if any) and whether its drive is open. */
    fun update(name: String?, driveOpen: Boolean, now: Long): UsbKobo {
        if (name != this.name) {
            this.name = name
            since = now
            opened = false
        }
        return when {
            name == null -> UsbKobo.NONE
            driveOpen -> UsbKobo.NONE.also { opened = true }
            opened -> UsbKobo.UNMOUNTED
            now - since < patienceMs -> UsbKobo.OPENING
            else -> UsbKobo.SLOW
        }
    }
}
