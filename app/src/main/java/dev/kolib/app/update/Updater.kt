package dev.kolib.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import dev.kolib.app.KoLibApp
import dev.kolib.app.ai.ModelStore
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Keeping KoLib up to date from its releases on GitHub: looks for a newer version (when the app starts, once
 * a day, unless that's turned off; or when asked), and with the user's say-so downloads it, checks it's a
 * later KoLib signed as this one is, and hands it to Android to install over this one.
 */
class Updater(
    private val context: Context,
    private val scope: CoroutineScope,
    /** The version that's installed. */
    val installed: String = versionOf(context),
    private val abis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val fetch: (String) -> String = Updates::get,
    private val open: (String, Long) -> ModelStore.Answer = ModelStore.Companion::fetch,
    /** Says what's wrong with a downloaded file, or null when it's a later KoLib from the same maker. */
    private val verify: (File) -> String? = { verifyApk(context, it) },
    private val install: (File) -> Unit = { installApk(context, it) },
    /** Whether looking by itself is allowed here at all (not where tests run). */
    private val canLookAlone: Boolean = Build.FINGERPRINT != "robolectric",
) {
    sealed interface State {
        data object Idle : State
        /** Looking, because the user asked. */
        data object Checking : State
        /** Asked, and there's nothing newer. */
        data object UpToDate : State
        /** Asked, and GitHub couldn't be reached. */
        data object Unreachable : State
        /** [stopped]: it was downloaded and handed to Android, and not installed (Android was told no, or stopped it). */
        data class Available(val release: Release, val stopped: Boolean = false) : State
        data class Downloading(val release: Release, val bytes: Long, val total: Long) : State
        /** Downloaded and checked; Android has it, and may be asking the user. */
        data class Installing(val release: Release) : State
        /** [installing]: it was downloaded, and Android couldn't install it. */
        data class Failed(val release: Release, val message: String, val installing: Boolean = false) : State
    }

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var job: Job? = null

    /** Looking for a newer version by itself when the app starts. */
    var auto: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) = prefs.edit().putBoolean("auto", value).apply()

    private val dir get() = File(context.cacheDir, "updates")

    /** When the app starts: looks if it's allowed to and hasn't for most of a day. A newer version is offered; anything else is kept quiet. */
    fun lookIfDue(now: Long = System.currentTimeMillis()) {
        // Nothing left over from an update that's been made, or a download cut short. A newer version downloaded and
        // not installed yet is kept, so it isn't downloaded again.
        if (_state.value == State.Idle) runCatching {
            dir.listFiles()?.forEach { f ->
                val version = f.name.takeIf { it.startsWith("KoLib-") && it.endsWith(".apk") }?.removePrefix("KoLib-")?.removeSuffix(".apk")
                if (version == null || !Updates.isNewer(version, installed)) f.delete()
            }
        }
        if (!canLookAlone || !auto || _state.value != State.Idle) return
        if (now - prefs.getLong("lookedAt", 0L) < DAY * 3 / 4) return
        look(asked = false, now = now)
    }

    /** The user asked: looks now, and says what it finds either way. */
    fun lookNow() = look(asked = true, now = System.currentTimeMillis())

    private fun look(asked: Boolean, now: Long) {
        if (job?.isActive == true) return
        if (asked) _state.value = State.Checking
        job = scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { Updates.parse(fetch(Updates.LATEST), abis) } }
            val release = found.getOrNull()
            if (found.isSuccess) prefs.edit().putLong("lookedAt", now).apply()
            _state.value = when {
                found.isFailure -> {
                    AppLog.w("Update", "Couldn't look for a newer version", found.exceptionOrNull())
                    if (asked) State.Unreachable else State.Idle
                }
                release == null || !Updates.isNewer(release.version, installed) -> if (asked) State.UpToDate else State.Idle
                // One the user said to pass over is only offered when they ask.
                !asked && prefs.getString("skipped", null) == release.version -> State.Idle
                else -> State.Available(release)
            }
        }
    }

    /** Not now: it's offered again the next time it's looked for. */
    fun dismiss() {
        val s = _state.value
        if (s is State.Downloading || s is State.Installing) return
        _state.value = State.Idle
    }

    /** Not this version at all: it isn't offered again, until asked for. */
    fun skip(release: Release) {
        prefs.edit().putString("skipped", release.version).apply()
        _state.value = State.Idle
    }

    /** Where [release] is kept once it's downloaded. */
    private fun fileOf(release: Release) = File(dir, "KoLib-${release.version.replace(Regex("[^0-9A-Za-z.]"), "_")}.apk")

    /** [release] has been downloaded already (all of it), so updating needn't download it again. */
    fun downloaded(release: Release): Boolean = fileOf(release).let { it.isFile && (release.apkBytes <= 0 || it.length() == release.apkBytes) }

    /**
     * Downloads [release], checks it, and hands it to Android to install. One downloaded already (an install that was
     * stopped, tried again) isn't downloaded again.
     */
    fun update(release: Release) {
        val url = release.apkUrl ?: return
        if (job?.isActive == true) return
        val reuse = downloaded(release)
        _state.value = if (reuse) State.Installing(release) else State.Downloading(release, 0, release.apkBytes)
        job = scope.launch {
            var installing = false
            try {
                val file = withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    val out = fileOf(release)
                    if (reuse && verify(out) == null) return@withContext out
                    dir.listFiles()?.forEach { it.delete() }
                    // Written under another name until it's all there, so a download cut short is never taken for one that isn't.
                    val part = File(dir, out.name + ".part")
                    _state.value = State.Downloading(release, 0, release.apkBytes)
                    val answer = open(url, 0)
                    try {
                        if (answer.code != 200) throw IOException(tr("The download failed (error {0}).", answer.code))
                        part.outputStream().use { to ->
                            val buffer = ByteArray(1 shl 16)
                            var have = 0L
                            var told = 0L
                            while (true) {
                                if (!isActive) throw ModelStore.StoppedException()
                                val n = answer.body.read(buffer)
                                if (n < 0) break
                                to.write(buffer, 0, n)
                                have += n
                                val now = System.currentTimeMillis()
                                if (now - told > 120) {
                                    told = now
                                    _state.value = State.Downloading(release, have, maxOf(release.apkBytes, have))
                                }
                            }
                            if (release.apkBytes > 0 && have != release.apkBytes) throw IOException(tr("The download didn't come through whole. Try again."))
                        }
                    } finally {
                        runCatching { answer.close() }
                    }
                    if (!part.renameTo(out)) throw IOException(tr("The download failed. Check the connection and try again."))
                    verify(out)?.let { problem ->
                        out.delete()
                        throw IOException(problem)
                    }
                    out
                }
                _state.value = State.Installing(release)
                installing = true
                withContext(Dispatchers.IO) { install(file) }
            } catch (e: ModelStore.StoppedException) {
                _state.value = State.Available(release)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = State.Available(release)
                throw e
            } catch (e: Exception) {
                AppLog.w("Update", "The update couldn't be downloaded or installed", e)
                _state.value = if (installing) {
                    State.Failed(release, tr("The update couldn't be installed: {0}", e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName), installing = true)
                } else {
                    val why = (e as? IOException)?.message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: tr("The download failed. Check the connection and try again.")
                    State.Failed(release, why)
                }
            }
        }
    }

    /** Stops a download that's under way. */
    fun stop() {
        if (_state.value is State.Downloading) job?.cancel()
    }

    /** What Android said of the install: the user turned it down, or it couldn't be done. (When it's done, this app is gone.) */
    fun installResult(status: Int, message: String?) {
        val release = (_state.value as? State.Installing)?.release ?: return
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> State.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> State.Available(release, stopped = true)
            else -> State.Failed(release, tr("The update couldn't be installed: {0}", message?.takeIf { it.isNotBlank() } ?: status.toString()), installing = true)
        }
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        const val ACTION_STATUS = "dev.kolib.app.update.STATUS"

        fun versionOf(context: Context): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

        private fun signers(info: PackageInfo?): Set<String>? =
            info?.signingInfo?.apkContentsSigners?.map { java.util.Base64.getEncoder().encodeToString(it.toByteArray()) }?.toSet()?.takeIf { it.isNotEmpty() }

        /**
         * What's wrong with a downloaded app file, or null when it's fit to install: it has to be KoLib, a
         * later build than this one, and signed with the key this one is. (Android checks the key again
         * itself when it installs.)
         */
        fun verifyApk(context: Context, file: File): String? {
            val pm = context.packageManager
            val theirs = runCatching { pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
                ?: return tr("That download isn't a KoLib update, so it wasn't installed.")
            val mine = runCatching { pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            if (theirs.packageName != context.packageName) return tr("That download isn't a KoLib update, so it wasn't installed.")
            if (mine != null && theirs.longVersionCode <= mine.longVersionCode) return tr("That download isn't newer than the KoLib you have.")
            val a = signers(theirs)
            val b = signers(mine)
            if (a != null && b != null && a != b) return tr("That download isn't a KoLib update, so it wasn't installed.")
            return null
        }

        /** Hands an app file to Android to install over this app. Android asks the user first, where it has to. */
        fun installApk(context: Context, file: File) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                // An app updating itself needn't ask each time, where Android allows that; where it doesn't, it asks.
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("KoLib.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 1 shl 16) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallReceiver::class.java).setAction(ACTION_STATUS).setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        }
    }

    /** Hears from Android how an install is going: shows its question to the user when it has one, and passes on how it ended. */
    class InstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                @Suppress("DEPRECATION")
                val ask = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                runCatching { context.startActivity(ask?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { AppLog.w("Update", "Android's question about the update couldn't be shown", it) }
                return
            }
            (context.applicationContext as? KoLibApp)?.updater?.installResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
