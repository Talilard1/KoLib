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

    override fun onCreate(savedInstanceState: Bundle?) {
        // The starting theme's dark was only for while the app starts.
        setTheme(R.style.Theme_KoLib)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a restart Android hands back the same intent; its books were already added. So it does when the app
        // is opened again from the recent apps, after it was closed with Back.
        val fromRecents = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromRecents) handleIncoming(intent)
        val themes = app.themeSettings
        // The opening plays when the app is started from its icon: not after a restart, nor when it's sent a book.
        val playOpening = savedInstanceState == null && themes.opening.value &&
            (intent?.action == null || intent?.action == Intent.ACTION_MAIN)
        matchStartingScreen(themes.opening.value)
        // The Kobo is looked for, and the link to other devices kept up, while KoLib is in use: the library or a reader.
        lifecycleScope.launch { app.inUse.collect { inUse(it) } }
        setContent {
            val theme by themes.choice.collectAsStateWithLifecycle()
            val opening by themes.opening.collectAsStateWithLifecycle()
            val openingSound by themes.openingSound.collectAsStateWithLifecycle()
            val openingLight by themes.openingLight.collectAsStateWithLifecycle()
            val introSeen by themes.introSeen.collectAsStateWithLifecycle()
            val editorTourSeen by themes.editorTourSeen.collectAsStateWithLifecycle()
            val toursSeen by themes.toursSeen.collectAsStateWithLifecycle()
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
                            matchStartingScreen(on)
                        },
                        openingSound = openingSound,
                        onOpeningSound = themes::setOpeningSound,
                        openingLight = openingLight,
                        onOpeningLight = themes::setOpeningLight,
                        introSeen = introSeen,
                        onIntroSeen = themes::setIntroSeen,
                        editorTourSeen = editorTourSeen,
                        onEditorTourSeen = { themes.setEditorTourSeen(true) },
                        toursSeen = toursSeen,
                        onTourSeen = themes::setTourSeen,
                        onShowToursAgain = themes::showToursAgain,
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
            // KoLib on the user's other devices can find and reach this one while it's open.
            if (!underTest) {
                app.link.start()
                if (app.link.settings.value.stayReachable) dev.kolib.app.link.LinkService.start(this)
            }
        } else {
            vm.onLeft(app.lastTouch)
            vm.onBackground()
            if (!underTest) app.link.stop()
        }
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
