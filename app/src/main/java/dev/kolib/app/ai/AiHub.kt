package dev.kolib.app.ai

import android.content.Context
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** How a download of a model is going. */
data class AiDownload(val bytes: Long, val total: Long, val error: String? = null, val checking: Boolean = false)

/**
 * The app's language model: which one the user chose, its files, and the one place it's asked anything, one
 * question at a time. A model on the device is opened when it's first needed and let go of after a few idle
 * minutes, since it takes a good share of the device's memory.
 */
class AiHub(
    private val context: Context,
    private val scope: CoroutineScope,
    canRunHere: () -> Boolean = { LlamaNative.available(context) },
    deviceMemory: () -> Long = { Device.memory(context) },
    deviceSpeed: () -> Device.Speed = { Device.speed() },
    /** Opens a model file to hold so many tokens; and the user's server. (Tests put their own in.) */
    private val openLocal: (File, Int) -> AiEngine = { file, tokens -> LocalEngine.open(context, file, tokens) },
    private val openServer: (AiSettings) -> AiEngine = { s -> ServerEngine(s.serverAddress, s.serverKey, s.serverModel) },
    /** Where a model's file is downloaded from (from so many bytes in). */
    private val fetch: (String, Long) -> ModelStore.Answer = { url, from -> ModelStore.fetch(url, from) },
) {

    val settingsStore = AiSettingsStore(context)
    val settings: StateFlow<AiSettings> get() = settingsStore.settings
    val models = ModelStore(File(context.filesDir, "models"))

    /** Whether this build, on this device, can run models itself (it's built for 64-bit devices). */
    val onDevice: Boolean by lazy(canRunHere)

    /** The device's memory and how quick its processor is, for suggesting a model. */
    val memory: Long by lazy(deviceMemory)
    val speed: Device.Speed by lazy(deviceSpeed)
    val suggested: AiModel get() = AiModels.bestFor(memory, speed)

    /** The models to offer on this device, the best first (see [AiModels.picks]). */
    val picks: List<AiModel> get() = AiModels.picks(memory, speed)

    // ---- downloads -----------------------------------------------------------------------------------

    private val downloadStates = MutableStateFlow<Map<String, AiDownload>>(emptyMap())
    /** The downloads going on, or stopped by an error, by model. */
    val downloads: StateFlow<Map<String, AiDownload>> = downloadStates
    private val downloadJobs = HashMap<String, Job>()

    /** Called when a download starts, so the app can keep running while it goes on. */
    var onDownloading: () -> Unit = {}

    /** Counts up when the model files change, so screens showing them look again. */
    val filesChanged = MutableStateFlow(0)

    @Synchronized
    fun download(model: AiModel) {
        val before = downloadJobs[model.id]
        if (before?.isActive == true) return
        if (models.isOwn(model)) found.value = found.value + (model.id to model)
        downloadStates.value = downloadStates.value + (model.id to AiDownload(models.partBytes(model), model.bytes))
        val job = scope.launch(Dispatchers.IO) {
            var last = 0L
            try {
                // One stopped a moment ago may still be finishing the piece it was reading: waited for, so that
                // two never write to the file at once.
                before?.join()
                // Its own stopping is what stops it (not what's in the list of downloads).
                models.download(model, open = fetch, stillWanted = { isActive }) { bytes ->
                    val now = System.currentTimeMillis()
                    if (isActive && (now - last > 300 || bytes >= model.bytes)) {
                        last = now
                        downloadStates.value = downloadStates.value + (model.id to AiDownload(bytes, model.bytes, checking = bytes >= model.bytes))
                    }
                }
                downloadStates.value = downloadStates.value - model.id
                found.value = found.value - model.id
                filesChanged.value++
                // The first model downloaded is the one used, with nothing else to do.
                if (!ready()) settingsStore.set(settings.value.copy(source = sourceOf(model)))
            } catch (e: ModelStore.StoppedException) {
                // Stopped: what came of it is let go too.
                models.dropPart(model)
                filesChanged.value++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("AI", "Downloading ${model.id} failed", e)
                val why = (e as? IOException)?.message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: tr("The download failed. Check the connection and try again.")
                downloadStates.value = downloadStates.value + (model.id to AiDownload(models.partBytes(model), model.bytes, error = why))
            }
        }
        downloadJobs[model.id] = job
        job.invokeOnCompletion { synchronized(this) { if (downloadJobs[model.id] === job) downloadJobs.remove(model.id) } }
        onDownloading()
    }

    /** Stops [model]'s download, and lets go of what it had downloaded (or what's left of one that failed). */
    @Synchronized
    fun stopDownload(model: AiModel) {
        val job = downloadJobs[model.id]
        job?.cancel()
        downloadStates.value = downloadStates.value - model.id
        found.value = found.value - model.id
        // Still running, it lets go of its part itself as it ends.
        if (job == null || job.isCompleted) models.dropPart(model)
    }

    /**
     * The models found for the user (on Hugging Face) that are being downloaded, or whose download stopped
     * with an error: by what they're known by.
     */
    val found = MutableStateFlow<Map<String, AiModel>>(emptyMap())

    /** What the settings call [model] when it's the one used. */
    fun sourceOf(model: AiModel): String = if (models.isOwn(model)) "own:" + models.ownName(models.file(model)) else "model:${model.id}"

    val downloading: Boolean get() = downloadStates.value.values.any { it.error == null }

    fun delete(model: AiModel) {
        stopDownload(model)
        if (settings.value.modelId == model.id) {
            release()
            settingsStore.set(settings.value.copy(source = ""))
        }
        models.delete(model)
        filesChanged.value++
    }

    fun deleteOwn(file: File) {
        if (settings.value.ownName == models.ownName(file)) {
            release()
            settingsStore.set(settings.value.copy(source = ""))
        }
        file.delete()
        filesChanged.value++
    }

    // ---- the model in use ----------------------------------------------------------------------------

    /** The file of the model chosen, if it's one on the device and it's here. */
    private fun modelFile(s: AiSettings): File? =
        s.modelId?.let { id -> AiModels.byId(id)?.takeIf { models.installed(it) }?.let { models.file(it) } }
            ?: s.ownName?.let { models.ownFile(it).takeIf { f -> f.isFile } }

    /** There's a model to ask: one chosen that's on the device (and can run here), or a server filled in. */
    fun ready(s: AiSettings = settings.value): Boolean =
        if (s.usesServer) s.serverAddress.isNotBlank() && s.serverModel.isNotBlank() else onDevice && modelFile(s) != null

    /** What's answering, by name, for the screen. */
    fun name(s: AiSettings = settings.value): String = when {
        s.usesServer -> s.serverModel.ifBlank { tr("Your server") }
        s.modelId != null -> AiModels.byId(s.modelId)?.name.orEmpty()
        s.ownName != null -> s.ownName.orEmpty()
        else -> ""
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "kolib-ai").apply { isDaemon = true } }.asCoroutineDispatcher()
    @Volatile
    private var engine: AiEngine? = null
    private var engineFor: AiSettings? = null
    private var uses = 0L

    private val state = MutableStateFlow(State.IDLE)
    /** What the model is doing now: nothing, being opened, or answering. */
    val busy: StateFlow<State> = state

    enum class State { IDLE, OPENING, ANSWERING }

    /** How many tokens the model on this device is opened to hold: more where there's the memory for it. */
    private fun contextFor(): Int = when {
        memory >= 7_000_000_000L -> 8192
        memory >= 5_000_000_000L -> 6144
        else -> 4096
    }

    /** On the worker thread: the engine for what's chosen now, opened if it isn't yet. */
    private fun open(): AiEngine {
        val s = settings.value
        engine?.let { if (engineFor == s.copy(dictionary = true)) return it }
        closeNow()
        if (!ready(s)) throw AiException(tr("Choose an AI model first."))
        state.value = State.OPENING
        val made = if (s.usesServer) openServer(s) else {
            onHolding(true)
            try { openLocal(modelFile(s)!!, contextFor()) } catch (e: Throwable) { onHolding(false); throw e }
        }
        engine = made
        engineFor = s.copy(dictionary = true)
        return made
    }

    /** What the model is doing for whom: answering what was asked, reading ahead of a question, or work in the background. */
    private enum class Kind { ASKED, AHEAD, BACKGROUND }

    private val lock = Any()
    private var running: Kind? = null
    /** Things asked that are waiting for the model. */
    private val waiting = AtomicInteger(0)
    /** When something was last asked or read ahead: work in the background waits for a quiet spell. */
    @Volatile
    private var lastAsked = 0L

    /**
     * Makes way for something asked: reading ahead stops at its next step (what it read stays read), and work in
     * the background is stopped, to be done again later. Reading ahead [forAhead] only stops work in the background.
     */
    private fun makeWay(forAhead: Boolean) {
        synchronized(lock) {
            when (running) {
                Kind.AHEAD -> engine?.yieldAhead()
                Kind.BACKGROUND -> engine?.stop()
                Kind.ASKED, null -> {}
            }
        }
    }

    private fun startOf(kind: Kind) = synchronized(lock) { running = kind }

    private fun endOf(mine: Long) {
        synchronized(lock) { running = null }
        state.value = State.IDLE
        settle(mine)
    }

    /**
     * Whether the model is kept open though nothing's been asked of it a while (the user wants it ready, and KoLib
     * is in sight).
     */
    var keepOpen: () -> Boolean = { false }

    /** Lets go of the model if nothing's asked of it for a while, unless it's kept open: after work, and as KoLib goes out of sight. */
    fun settle(mine: Long = uses) {
        scope.launch {
            delay(IDLE_MS)
            withContext(worker) { if (uses == mine && !keepOpen()) closeNow() }
        }
    }

    /** How much is taken up by downloads that were stopped or failed part-way, and aren't going on. */
    fun unfinishedBytes(): Long = synchronized(this) {
        models.parts().filter { f -> downloadJobs.keys.none { f.name == "$it.gguf.part" } }.sumOf { it.length() }
    }

    /** Lets go of those. */
    fun dropUnfinished() = synchronized(this) {
        models.parts().filter { f -> downloadJobs.keys.none { f.name == "$it.gguf.part" } }.forEach { it.delete() }
        downloadStates.value = downloadStates.value.filterValues { it.error == null }
        filesChanged.value++
    }

    /** Runs [work] on [e] where cancelling the coroutine stops the model writing. */
    private suspend fun <T> stoppable(e: AiEngine, work: (AiEngine) -> T): T = coroutineScope {
        val done = AtomicBoolean(false)
        val stopper = launch(Dispatchers.Default) {
            try { awaitCancellation() } finally { if (!done.get()) e.stop() }
        }
        try { work(e) } finally {
            done.set(true)
            stopper.cancel()
        }
    }

    /**
     * Does [work] with the model, off the main thread, after anything already asked; reading ahead and work in the
     * background make way for it. Cancelling the coroutine stops the model writing. Throws [AiException] if the
     * model can't be made ready.
     */
    suspend fun <T> use(work: (AiEngine) -> T): T {
        waiting.incrementAndGet()
        val counted = AtomicBoolean(true)
        lastAsked = System.currentTimeMillis()
        makeWay(forAhead = false)
        try {
            return withContext(worker) {
                if (counted.getAndSet(false)) waiting.decrementAndGet()
                val mine = ++uses
                try {
                    val e = open()
                    state.value = State.ANSWERING
                    startOf(Kind.ASKED)
                    stoppable(e, work)
                } finally {
                    lastAsked = System.currentTimeMillis()
                    endOf(mine)
                }
            }
        } finally {
            if (counted.getAndSet(false)) waiting.decrementAndGet()
        }
    }

    /**
     * Reads ahead (see [AiEngine.prefill]) what [build] makes for the model, when nothing is waiting for it, opening
     * it if it isn't yet: done while the reader is typing a question, so then only the question is left to read.
     * Anything asked meanwhile goes first; work in the background makes way for it, and so does reading ahead
     * asked for before.
     */
    fun warm(build: (AiEngine) -> List<AiMessage>) {
        if (!ready()) return
        lastAsked = System.currentTimeMillis()
        makeWay(forAhead = true)
        scope.launch(worker) {
            if (waiting.get() > 0) return@launch
            val mine = ++uses
            try {
                val e = open()
                if (waiting.get() > 0) return@launch
                val messages = build(e)
                if (messages.isEmpty() || waiting.get() > 0) return@launch
                startOf(Kind.AHEAD)
                e.prefill(messages)
            } catch (e: AiException) {
                // Nothing to read ahead with: the question will say why when it's asked.
            } finally {
                lastAsked = System.currentTimeMillis()
                endOf(mine)
            }
        }
    }

    /**
     * Does [work] with the model when nothing else wants it, for work that can be done again later (notes on a
     * book): anything asked, or read ahead, meanwhile stops it. Null, without doing it, when something is waiting.
     * Cancelling the coroutine stops it too. Throws [AiException] if the model can't be made ready.
     */
    suspend fun <T> background(work: (AiEngine) -> T): T? = withContext(worker) {
        if (waiting.get() > 0) return@withContext null
        val mine = ++uses
        try {
            val e = open()
            if (waiting.get() > 0) return@withContext null
            startOf(Kind.BACKGROUND)
            stoppable(e, work)
        } finally {
            endOf(mine)
        }
    }

    /** How long since anything was asked of the model or read ahead: what work in the background waits for. */
    fun quietFor(now: Long = System.currentTimeMillis()): Long = now - lastAsked

    /** Whether the model is doing anything, or something is waiting for it. */
    val working: Boolean get() = synchronized(lock) { running != null } || waiting.get() > 0

    private fun closeNow() {
        val was = engine ?: return
        was.close()
        engine = null
        engineFor = null
        onHolding(false)
    }

    /** Told as a model on the device is opened (true) and as it's let go (false): it's held in memory in between. */
    var onHolding: (Boolean) -> Unit = {}

    /** Lets go of the model now (the app is short of memory, or another model was chosen). */
    fun release() {
        scope.launch(worker) { closeNow() }
    }

    companion object {
        private const val IDLE_MS = 4 * 60_000L
    }
}
