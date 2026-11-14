package dev.kobolibrary.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import dev.kobolibrary.app.reader.ReaderKeys
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.ui.AppRoot
import dev.kobolibrary.app.ui.theme.AppSurface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.os.Build
import dev.kobolibrary.app.ui.opening.OpeningAnimation
import dev.kobolibrary.app.ui.opening.OpeningClock
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The starting theme's dark was only for while the app starts.
        setTheme(R.style.Theme_KoboLibrary)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a restart Android hands back the same intent; its books were already added.
        if (savedInstanceState == null) handleIncoming(intent)
        val themes = (application as KoboLibraryApp).themeSettings
        // The opening plays when the app is started from its icon: not after a restart, nor when it's sent a book.
        val playOpening = savedInstanceState == null && themes.opening.value &&
            (intent?.action == null || intent?.action == Intent.ACTION_MAIN)
        matchStartingScreen(themes.opening.value)
        setContent {
            val theme by themes.choice.collectAsStateWithLifecycle()
            val opening by themes.opening.collectAsStateWithLifecycle()
            val openingSound by themes.openingSound.collectAsStateWithLifecycle()
            val openingLight by themes.openingLight.collectAsStateWithLifecycle()
            val introSeen by themes.introSeen.collectAsStateWithLifecycle()
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
                    )
                }
                }
                if (showOpening) OpeningAnimation(sound = openingSound, light = openingLight, clock = clock) { showOpening = false }
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
                splashScreen.setSplashScreenTheme(if (opening) R.style.Theme_KoboLibrary_Starting else R.style.Theme_KoboLibrary_Starting_Mark)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** EPUBs shared from, or opened in, another app (Files, a browser's downloads, email). */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
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

    /** The volume buttons turn pages while a book is open. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ReaderKeys.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}
