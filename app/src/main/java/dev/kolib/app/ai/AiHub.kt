package dev.kolib.app.ai

import android.content.Context
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    deviceFast: () -> Boolean = { Device.fast() },
    /** Opens a model file to hold so many tokens; and the user's server. (Tests put their own in.) */
    private val openLocal: (File, Int) -> AiEngine = { file, tokens -> LocalEngine.open(context, file, tokens) },
    private val openServer: (AiSettings) -> AiEngine = { s -> ServerEngine(s.serverAddress, s.serverKey, s.serverModel) },
) {

    val settingsStore = AiSettingsStore(context)
    val settings: StateFlow<AiSettings> get() = settingsStore.settings
    val models = ModelStore(File(context.filesDir, "models"))

    /** Whether this build, on this device, can run models itself (it's built for 64-bit devices). */
    val onDevice: Boolean by lazy(canRunHere)

    /** The device's memory and whether its processor is a fast one, for suggesting a model. */
    val memory: Long by lazy(deviceMemory)
    val fast: Boolean by lazy(deviceFast)
    val suggested: AiModel get() = AiModels.bestFor(memory, fast)

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
        if (downloadJobs[model.id]?.isActive == true) return
        downloadStates.value = downloadStates.value + (model.id to AiDownload(models.partBytes(model), model.bytes))
        downloadJobs[model.id] = scope.launch(Dispatchers.IO) {
            var last = 0L
            try {
                models.download(model, stillWanted = { downloadJobs[model.id]?.isActive != false }) { bytes ->
                    val now = System.currentTimeMillis()
                    if (now - last > 300 || bytes >= model.bytes) {
                        last = now
                        downloadStates.value = downloadStates.value + (model.id to AiDownload(bytes, model.bytes, checking = bytes >= model.bytes))
                    }
                }
                downloadStates.value = downloadStates.value - model.id
                filesChanged.value++
                // The first model downloaded is the one used, with nothing else to do.
                if (!ready()) settingsStore.set(settings.value.copy(source = "model:${model.id}"))
            } catch (e: ModelStore.StoppedException) {
                downloadStates.value = downloadStates.value - model.id
                filesChanged.value++
            } catch (e: Exception) {
                AppLog.w("AI", "Downloading ${model.id} failed", e)
                val why = (e as? IOException)?.message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: tr("The download failed. Check the connection and try again.")
                downloadStates.value = downloadStates.value + (model.id to AiDownload(models.partBytes(model), model.bytes, error = why))
            }
        }
        onDownloading()
    }

    @Synchronized
    fun stopDownload(model: AiModel) {
        downloadJobs.remove(model.id)?.cancel()
        downloadStates.value = downloadStates.value - model.id
    }

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
        engine?.close()
        engine = null
        if (!ready(s)) throw AiException(tr("Choose an AI model first."))
        state.value = State.OPENING
        val made = if (s.usesServer) openServer(s) else openLocal(modelFile(s)!!, contextFor())
        engine = made
        engineFor = s.copy(dictionary = true)
        return made
    }

    /**
     * Does [work] with the model, off the main thread, after anything already asked. Cancelling the coroutine
     * stops the model writing. Throws [AiException] if the model can't be made ready.
     */
    suspend fun <T> use(work: (AiEngine) -> T): T = withContext(worker) {
        val mine = ++uses
        try {
            val e = open()
            state.value = State.ANSWERING
            coroutineScope {
                val done = AtomicBoolean(false)
                val stopper = launch(Dispatchers.Default) {
                    try { awaitCancellation() } finally { if (!done.get()) e.stop() }
                }
                try { work(e) } finally {
                    done.set(true)
                    stopper.cancel()
                }
            }
        } finally {
            state.value = State.IDLE
            scope.launch {
                delay(IDLE_MS)
                withContext(worker) { if (uses == mine) closeNow() }
            }
        }
    }

    private fun closeNow() {
        engine?.close()
        engine = null
        engineFor = null
    }

    /** Lets go of the model now (the app is short of memory, or another model was chosen). */
    fun release() {
        scope.launch(worker) { closeNow() }
    }

    companion object {
        private const val IDLE_MS = 4 * 60_000L
    }
}
