package dev.kolib.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import dev.kolib.app.reader.ReaderKeys
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import dev.kolib.app.log.AppLog
import dev.kolib.app.ui.AppRoot
import dev.kolib.app.ui.theme.AppSurface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.os.Build
import dev.kolib.app.ui.opening.OpeningAnimation
import dev.kolib.app.ui.opening.OpeningClock
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    /** The screen last written to the log, so a change (turned, folded, resized) is noted once. */
    private var screenLogged: String? = null

    private fun logScreen(why: String) {
        val c = resources.configuration
        val now = "${c.screenWidthDp}x${c.screenHeightDp} ${c.orientation} ${c.fontScale} ${resources.displayMetrics.densityDpi} ${c.locales[0]}"
        if (now == screenLogged) return
        screenLogged = now
        dev.kolib.app.log.AppLog.screen(this, why)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        logScreen("Screen changed")
    }

    /** When this KoLib was installed or last updated (the same version put on again counts as an update). */
    private fun installedAt(): Long = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
        info.lastUpdateTime
    }.getOrDefault(BuildConfig.VERSION_CODE.toLong())

    override fun onCreate(savedInstanceState: Bundle?) {
        // The starting theme's dark was only for while the app starts.
        setTheme(R.style.Theme_KoLib)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logScreen("Screen")
        // After a restart Android hands back the same intent; its books were already added. So it does when the app
        // is opened again from the recent apps, after it was closed with Back.
        val fromRecents = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromRecents) handleIncoming(intent)
        val themes = app.themeSettings
        // When this KoLib was put on the device (installed, or updated): the opening and What's new go by it, so they come
        // once after each update, even one of the same version.
        val installed = installedAt()
        // Someone who's used KoLib before gets the tours of the screens that have changed again (once, the first time
        // this KoLib starts).
        themes.renewToursAfterUpdate(updated = themes.introSeen.value)
        // The opening plays when the app is started from its icon (not after a restart, nor when it's sent a book): the
        // first time, and after each update, or each time if that's chosen.
        val playOpening = savedInstanceState == null && themes.openingDue(installed) &&
            (intent?.action == null || intent?.action == Intent.ACTION_MAIN)
        if (playOpening) themes.openingPlayed(installed)
        // The starting screen is for the next start: the opening's own, when it's to play each time.
        matchStartingScreen(themes.opening.value && themes.openingEveryTime.value)
        // The Kobo is looked for, and the link to other devices kept up, while KoLib is in use: the library or a reader.
        lifecycleScope.launch { app.inUse.collect { inUse(it) } }
        // Looking for devices the whole time KoLib is open, turned on or off while it is.
        lifecycleScope.launch { app.link.settings.collect { if (inUseNow && !underTest) holdLink(true, it.lookAlways) } }
        setContent {
            val theme by themes.choice.collectAsStateWithLifecycle()
            val opening by themes.opening.collectAsStateWithLifecycle()
            val openingSound by themes.openingSound.collectAsStateWithLifecycle()
            val openingLight by themes.openingLight.collectAsStateWithLifecycle()
            val openingEveryTime by themes.openingEveryTime.collectAsStateWithLifecycle()
            val introSeen by themes.introSeen.collectAsStateWithLifecycle()
            val asksSeen by themes.asksSeen.collectAsStateWithLifecycle()
            val newsSeen by themes.newsSeen.collectAsStateWithLifecycle()
            val editorTourSeen by themes.editorTourSeen.collectAsStateWithLifecycle()
            val toursSeen by themes.toursSeen.collectAsStateWithLifecycle()
            val toursOff by themes.toursOff.collectAsStateWithLifecycle()
            var showOpening by remember { mutableStateOf(playOpening) }
            val clock = remember { OpeningClock() }
            Box(Modifier.fillMaxSize()) {
                // Under the opening the app is laid out, ready, but not drawn until it starts to show through.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (showOpening && !clock.showingApp) 0f else 1f }) {
                AppSurface(theme) {
                    AppRoot(
                        vm, libraryVm, theme = theme, onTheme = themes::set,
                        opening = opening,
                        onOpening = { on ->
                            themes.setOpening(on)
                            matchStartingScreen(on && openingEveryTime)
                        },
                        openingEveryTime = openingEveryTime,
                        onOpeningEveryTime = { on ->
                            themes.setOpeningEveryTime(on)
                            matchStartingScreen(opening && on)
                        },
                        openingSound = openingSound,
                        onOpeningSound = themes::setOpeningSound,
                        openingLight = openingLight,
                        onOpeningLight = themes::setOpeningLight,
                        introSeen = introSeen,
                        // Someone new to KoLib has nothing to be told is new.
                        onIntroSeen = { seen -> themes.setIntroSeen(seen); if (seen) themes.setNewsSeen(installed) },
                        asksSeen = asksSeen,
                        onAsksSeen = { themes.setAsksSeen(true) },
                        newsDue = dev.kolib.app.ui.WhatsNew.due(BuildConfig.VERSION_NAME, newsSeen, installed),
                        openingPlaying = showOpening,
                        onNewsSeen = { themes.setNewsSeen(installed) },
                        editorTourSeen = editorTourSeen,
                        onEditorTourSeen = { themes.setEditorTourSeen(true) },
                        toursSeen = toursSeen,
                        onTourSeen = themes::setTourSeen,
                        onShowToursAgain = themes::showToursAgain,
                        toursOff = toursOff,
                        onToursOff = themes::setToursOff,
                    )
                }
                }
                if (showOpening) OpeningAnimation(sound = openingSound, light = openingLight, clock = clock, look = theme) { showOpening = false }
            }
        }
    }

    /**
     * What Android shows while the app starts, from the next start on (Android 13 and later): just the
     * dark the opening begins from, or, with the opening off, KoLib's mark on it.
     */
    private fun matchStartingScreen(opening: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                splashScreen.setSplashScreenTheme(if (opening) R.style.Theme_KoLib_Starting else R.style.Theme_KoLib_Starting_Mark)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /**
     * EPUBs shared from, or opened in, another app (Files, a browser's downloads, email); or, from "Send to device"
     * (the link's notification), a paired device to choose books in the library for.
     */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        // From the notification that the AI's answer is ready: its window opens.
        if (intent.action == dev.kolib.app.ai.AiAnswerService.ACTION_OPEN) {
            (application as KoLibApp).aiPanel.open()
            return
        }
        // Android opened KoLib because a Kobo was connected, and KoLib is the app chosen for it.
        if (intent.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            vm.openedForKobo()
            return
        }
        if (intent.action == dev.kolib.app.link.ShareToDeviceActivity.ACTION_SEND_BOOKS) {
            intent.getStringExtra(dev.kolib.app.link.ShareToDeviceActivity.EXTRA_DEVICE)?.let { vm.booksForDevice.value = it }
            return
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        AppLog.i("Library", "Received ${uris.size} file(s) from another app.")
        libraryVm.importUris(uris)
    }

    private val app get() = application as KoLibApp

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) app.touched()
        return super.dispatchTouchEvent(ev)
    }

    /** The volume buttons turn pages while a book is open. */
    @SuppressLint("RestrictedApi") // Calling the activity's own dispatchKeyEvent from its override is fine.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) app.touched()
        if (ReaderKeys.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    /** Whether what's done while KoLib is in use is going (see [inUse]). */
    private var inUseNow = false

    /**
     * KoLib came into use ([now]), or went out of sight: the library's screen or a reader is showing, or neither is (a
     * reader over the library's screen keeps it in use).
     */
    private fun inUse(now: Boolean) {
        if (now == inUseNow) return
        inUseNow = now
        if (now) {
            vm.onForeground()
            // KoLib on the user's other devices can reach this one while it's open, and find it too if it's to look
            // for them all the time (otherwise that's while the Devices screen is open).
            if (!underTest) {
                holdLink(true)
                if (app.link.settings.value.stayReachable) dev.kolib.app.link.LinkService.start(this)
            }
        } else {
            vm.onLeft(app.lastTouch)
            vm.onBackground()
            if (!underTest) holdLink(false)
        }
    }

    /**
     * How this screen is keeping the link to other devices going (see [inUse]): not at all (null), or taking
     * connections, and looking for the devices too (true) or not (false).
     */
    private var holdingLink: Boolean? = null

    private fun holdLink(hold: Boolean, look: Boolean = app.link.settings.value.lookAlways) {
        val now = if (hold) look else null
        if (now == holdingLink) return
        val before = holdingLink
        holdingLink = now
        // The new hold before the old one's let go, so connections aren't dropped in between.
        now?.let { app.link.start(look = it) }
        before?.let { app.link.stop(look = it) }
    }

    override fun onStart() {
        super.onStart()
        inUse(app.inUse.value)
    }

    override fun onStop() {
        super.onStop()
        inUse(app.inUse.value)
    }

    /** Robolectric, which runs the tests: no network there. */
    private val underTest get() = Build.FINGERPRINT == "robolectric"
}
