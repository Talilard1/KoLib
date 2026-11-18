package dev.kolib.app

import android.app.Application
import dev.kolib.app.kobo.PendingKoboRecords
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryCovers
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.ReaderSettingsStore
import dev.kolib.app.reader.ReadingHereIndex
import dev.kolib.app.ui.theme.ThemeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KoLibApp : Application() {

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
    val readingLog: dev.kolib.app.reader.ReadingLog by lazy { dev.kolib.app.reader.ReadingLog(java.io.File(filesDir, "reading-log.json")).also { it.load() } }

    /** Makes scanned books into text, in the background. */
    val ocr: dev.kolib.app.ocr.OcrManager by lazy { dev.kolib.app.ocr.OcrManager(this) }

    private var aiHub: dev.kolib.app.ai.AiHub? = null

    /** The language model that explains words and answers questions about books, and its files. */
    var ai: dev.kolib.app.ai.AiHub
        @Synchronized get() = aiHub ?: dev.kolib.app.ai.AiHub(this, appScope).also { hub ->
            hub.onDownloading = { dev.kolib.app.ai.AiDownloadService.start(this) }
            // Noted before the model's opened, so that if KoLib is closed while it's held, the next start knows.
            hub.onHolding = { on ->
                val edit = getSharedPreferences("ai", MODE_PRIVATE).edit().putBoolean(MODEL_HELD, on)
                if (on) edit.commit() else edit.apply()
            }
            aiHub = hub
        }
        @Synchronized set(value) { aiHub = value }

    /** Looking for a newer KoLib on GitHub, and installing it. */
    val updater: dev.kolib.app.update.Updater by lazy { dev.kolib.app.update.Updater(this, appScope) }

    /** The chat with the model: the same conversation wherever it's opened from, and the ones kept before it. */
    val chat: dev.kolib.app.ui.ai.ChatSession by lazy {
        dev.kolib.app.ui.ai.ChatSession(
            dev.kolib.app.ai.ChatStore(java.io.File(filesDir, "ai/chats")), prefs = getSharedPreferences("ai", MODE_PRIVATE),
            onStarted = ::aiStarted, onFinished = ::aiFinished,
        )
    }

    /** The AI's window over the app's screens: open, minimised to a bubble, or closed. */
    val aiPanel: dev.kolib.app.ui.ai.AiPanel by lazy { dev.kolib.app.ui.ai.AiPanel(getSharedPreferences("ai", MODE_PRIVATE)) }

    /** KoLib was closed last time while it held an AI model, and why, until the AI window has said so. */
    val aiClosed = kotlinx.coroutines.flow.MutableStateFlow<dev.kolib.app.ai.AiClosed?>(null)

    /**
     * Notes in the app's log why KoLib was closed last time; and if it was closed while it held an AI model, in sight
     * or working for the user, because Android ran short of memory or the model's code failed, the AI window says so.
     */
    private fun lastExit() {
        val prefs = getSharedPreferences("ai", MODE_PRIVATE)
        val held = prefs.getBoolean(MODEL_HELD, false)
        if (held) prefs.edit().putBoolean(MODEL_HELD, false).apply()
        if (android.os.Build.VERSION.SDK_INT < 30) return
        val exit = runCatching {
            getSystemService(android.app.ActivityManager::class.java)?.getHistoricalProcessExitReasons(null, 0, 5)
                ?.firstOrNull { it.processName == packageName }
        }.getOrNull() ?: return
        if (exit.timestamp <= prefs.getLong(EXIT_SEEN, 0L)) return
        prefs.edit().putLong(EXIT_SEEN, exit.timestamp).apply()
        val at = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(exit.timestamp))
        AppLog.i(
            "Exit",
            "Last closed $at: ${dev.kolib.app.ai.exitReason(exit.reason)} (status ${exit.status}, importance ${exit.importance}, " +
                "${exit.pss / 1024} MB in use, ${exit.rss / 1024} MB resident)" + (if (held) ", with an AI model open" else "") +
                (exit.description?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
        )
        aiClosed.value = dev.kolib.app.ai.closedFor(held, exit.reason, exit.status, exit.importance)
    }

    /** How many things the AI has been asked that it's still working on (questions, summaries). */
    val aiWorking = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The AI has started on something asked of it: the app keeps running for it while it's out of sight. */
    fun aiStarted() {
        aiWorking.value = aiWorking.value + 1
        dev.kolib.app.ai.AiAnswerService.start(this)
    }

    /**
     * The AI is done with something asked of it ([what]: what it was, as the user would say it), with [result] (its
     * answer), or none (it was stopped, or failed). Out of sight, the window's bubble or button says it's done; out
     * of the app altogether, a notification does.
     */
    fun aiFinished(what: String, result: String?) {
        aiWorking.value = (aiWorking.value - 1).coerceAtLeast(0)
        if (result.isNullOrBlank()) return
        aiPanel.finished(result)
        if (!inFront) dev.kolib.app.ai.AiAnswerService.answered(this, what, result)
    }

    private var started = 0

    /** Whether one of the app's screens is showing. */
    @Volatile
    var inFront = false
        private set

    /** Chapter summaries the model has written, kept so they're there at once the next time. */
    val summaries: dev.kolib.app.ai.SummaryStore by lazy { dev.kolib.app.ai.SummaryStore(java.io.File(filesDir, "ai/summaries.json")) }

    /** The fonts the user has added for reading in. */
    val fonts: dev.kolib.app.reader.UserFonts by lazy {
        dev.kolib.app.reader.UserFonts(java.io.File(filesDir, "fonts")).also { dev.kolib.app.reader.UserFonts.shared = it }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // A model holds a good share of the device's memory: let go of it when Android runs short.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL && level < android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) aiHub?.release()
    }

    /** Work that goes on whatever is on the screen: the link to the user's other devices. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** KoLib on the user's other devices (a phone and a tablet, say): pairing with them, and sending between them. */
    val link: dev.kolib.app.link.LinkManager by lazy {
        val wifi = applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        var lock: android.net.wifi.WifiManager.MulticastLock? = null
        dev.kolib.app.link.LinkManager(
            store = dev.kolib.app.link.LinkStore(java.io.File(filesDir, "link")),
            host = dev.kolib.app.link.AppLinkHost(this, library, readingHere),
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
                m.transfers.map { it.isNotEmpty() }.distinctUntilChanged().collect { busy -> if (busy) dev.kolib.app.link.LinkService.start(this@KoLibApp) }
            }
            appScope.launch {
                // KoLib is offered in other apps' Share only once there's a device to send to.
                m.paired.map { it.isNotEmpty() }.distinctUntilChanged().collect { any -> dev.kolib.app.link.ShareToDeviceActivity.offer(this@KoLibApp, any) }
            }
            appScope.launch {
                // Each paired device at the top of other apps' Share, and in KoLib's shortcuts; none once it's forgotten.
                m.paired.map { list -> list.map { it.id to it.name } }.distinctUntilChanged().collect {
                    dev.kolib.app.link.DeviceShortcuts.update(this@KoLibApp, m.paired.value)
                }
            }
        }
    }

    /** This device's name, as it's set in Android's settings ("Galaxy Tab S9"), for the user's other devices. */
    fun deviceName(): String =
        runCatching { android.provider.Settings.Global.getString(contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL ?: "KoLib"

    /** Sections copied out of books in the text editor, to put into others; kept until deleted. */
    val copiedSections: dev.kolib.app.library.CopiedSections by lazy {
        dev.kolib.app.library.CopiedSections(java.io.File(filesDir, "copied-sections"))
    }

    // Before Android 13, the language chosen in KoLib is applied here (and to each screen).
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                started++
                inFront = true
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0)
                inFront = started > 0
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        // The small process that starts KoLib again after a change of language needs none of this.
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":restart")) return
        L10n.load(this)
        dev.kolib.app.kobo.CoverScreen.load(this)
        AppLog.init(this)
        lastExit()
        fonts
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.crash(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        /** Whether an AI model is held in memory (noted as it's opened, cleared as it's let go). */
        const val MODEL_HELD = "model_held"
        /** When the last closing that's been noted in the log was. */
        const val EXIT_SEEN = "exit_seen"
    }
}
