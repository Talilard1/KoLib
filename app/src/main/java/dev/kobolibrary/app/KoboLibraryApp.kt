package dev.kobolibrary.app

import android.app.Application
import dev.kobolibrary.app.kobo.PendingKoboRecords
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.reader.ReaderSettingsStore
import dev.kobolibrary.app.reader.ReadingHereIndex
import dev.kobolibrary.app.ui.theme.ThemeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KoboLibraryApp : Application() {

    /** The books kept on the tablet; shared by the library and Kobo screens. */
    val library: Library by lazy { Library(this) }
    val libraryCovers: LibraryCovers by lazy { LibraryCovers(library) }

    /**
     * Asks for the Kobo's database to be brought in line with the library (collections, details) once
     * the current work is done: after sending, or after a book on the Kobo was edited. A request can
     * bring something to complete when that's done (or turns out not to be possible), for work that
     * isn't finished until then.
     */
    val koboSyncRequests = MutableSharedFlow<CompletableDeferred<Unit>?>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Changes to the Kobo's records of books just sent again, for the next write of its database. */
    val pendingKoboRecords = PendingKoboRecords()

    /** The colours the app is shown in. */
    val themeSettings: ThemeSettings by lazy { ThemeSettings(this) }

    /** How books look in the reader (the same for every book). */
    val readerSettings: ReaderSettingsStore by lazy { ReaderSettingsStore(this) }

    /** How far each library book has been read in the app's reader. */
    val readingHere: ReadingHereIndex by lazy { ReadingHereIndex() }

    /** The time spent reading each book in the app's readers, for the reading stats. */
    val readingLog: dev.kobolibrary.app.reader.ReadingLog by lazy { dev.kobolibrary.app.reader.ReadingLog(java.io.File(filesDir, "reading-log.json")).also { it.load() } }

    /** Makes scanned books into text, in the background. */
    val ocr: dev.kobolibrary.app.ocr.OcrManager by lazy { dev.kobolibrary.app.ocr.OcrManager(this) }

    /** Work that goes on whatever is on the screen: the link to the user's other devices. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** KoLib on the user's other devices (a phone and a tablet, say): pairing with them, and sending between them. */
    val link: dev.kobolibrary.app.link.LinkManager by lazy {
        val wifi = applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        var lock: android.net.wifi.WifiManager.MulticastLock? = null
        dev.kobolibrary.app.link.LinkManager(
            store = dev.kobolibrary.app.link.LinkStore(java.io.File(filesDir, "link")),
            host = dev.kobolibrary.app.link.AppLinkHost(this, library, readingHere),
            deviceName = ::deviceName,
            scope = appScope,
            // Some Wi-Fi hands an app what's sent to every device on the network only while it holds this.
            onRunning = { on ->
                if (on) {
                    lock = runCatching { wifi?.createMulticastLock("KoLib")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
                } else {
                    runCatching { lock?.release() }
                    lock = null
                }
            },
        ).also { m ->
            appScope.launch {
                // Kept running while anything is sent or taken in, whatever's on the screen.
                m.transfers.map { it.isNotEmpty() }.distinctUntilChanged().collect { busy -> if (busy) dev.kobolibrary.app.link.LinkService.start(this@KoboLibraryApp) }
            }
            appScope.launch {
                // KoLib is offered in other apps' Share only once there's a device to send to.
                m.paired.map { it.isNotEmpty() }.distinctUntilChanged().collect { any -> dev.kobolibrary.app.link.ShareToDeviceActivity.offer(this@KoboLibraryApp, any) }
            }
            appScope.launch {
                // Each paired device at the top of other apps' Share, and in KoLib's shortcuts; none once it's forgotten.
                m.paired.map { list -> list.map { it.id to it.name } }.distinctUntilChanged().collect {
                    dev.kobolibrary.app.link.DeviceShortcuts.update(this@KoboLibraryApp, m.paired.value)
                }
            }
        }
    }

    /** This device's name, as it's set in Android's settings ("Galaxy Tab S9"), for the user's other devices. */
    fun deviceName(): String =
        runCatching { android.provider.Settings.Global.getString(contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL ?: "KoLib"

    /** Sections copied out of books in the text editor, to put into others; kept until deleted. */
    val copiedSections: dev.kobolibrary.app.library.CopiedSections by lazy {
        dev.kobolibrary.app.library.CopiedSections(java.io.File(filesDir, "copied-sections"))
    }

    // Before Android 13, the language chosen in KoLib is applied here (and to each screen).
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // The small process that starts KoLib again after a change of language needs none of this.
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":restart")) return
        L10n.load(this)
        dev.kobolibrary.app.kobo.CoverScreen.load(this)
        AppLog.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.crash(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }
}
