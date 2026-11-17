package dev.kolib.app.log

import android.content.Context
import android.os.Build
import android.util.Log
import dev.kolib.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's own log. Kept in memory for the Log screen and appended to a file so that
 * entries (including crashes) survive a restart and can be shared for debugging.
 */
object AppLog {
    enum class Level(val letter: Char) { DEBUG('D'), INFO('I'), WARN('W'), ERROR('E') }

    data class Entry(val id: Long, val time: Long, val level: Level, val tag: String, val message: String) {
        fun format(): String = "${stamp(time)} ${level.letter}/$tag: $message"
    }

    private const val MAX_ENTRIES = 3000
    private const val MAX_FILE_BYTES = 1_000_000L
    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private var nextId = 0L
    private var logFile: File? = null
    private var previousLogFile: File? = null

    private val _changes = MutableStateFlow(0L)
    /** Bumps whenever an entry is added or the log is cleared, so the Log screen can refresh. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private val formatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private fun stamp(time: Long): String = formatter.get()!!.format(Date(time))

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, "app.log")
        val old = File(dir, "app.log.1")
        if (file.length() > MAX_FILE_BYTES) {
            old.delete()
            file.renameTo(old)
        }
        synchronized(lock) {
            logFile = file
            previousLogFile = old
        }
        i(
            "App",
            "Session start: KoLib ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, t: Throwable? = null) = log(Level.WARN, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(Level.ERROR, tag, message, t)

    fun log(level: Level, tag: String, message: String, t: Throwable?) {
        val full = if (t != null) "$message\n${stackTrace(t)}" else message
        when (level) {
            Level.DEBUG -> Log.d("KoLib/$tag", full)
            Level.INFO -> Log.i("KoLib/$tag", full)
            Level.WARN -> Log.w("KoLib/$tag", full)
            Level.ERROR -> Log.e("KoLib/$tag", full)
        }
        synchronized(lock) {
            val entry = Entry(nextId++, System.currentTimeMillis(), level, tag, full)
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            try {
                logFile?.appendText(entry.format() + "\n")
            } catch (_: Exception) {
                // Logging must never crash the app.
            }
            _changes.value = entry.id + 1
        }
    }

    /** Called from the uncaught-exception handler, so it must work without anything else. */
    fun crash(thread: Thread, t: Throwable) {
        log(Level.ERROR, "Crash", "Uncaught exception on thread '${thread.name}'", t)
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            try {
                logFile?.writeText("")
                previousLogFile?.delete()
            } catch (_: Exception) {
            }
            _changes.value = _changes.value + 1
        }
    }

    /** Writes a shareable copy of the log (previous and current sessions) and returns it. */
    fun exportFile(context: Context): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "kolib-log.txt")
        val header = buildString {
            appendLine("KoLib ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Exported ${stamp(System.currentTimeMillis())}")
            appendLine("────────────────────────────────────────")
        }
        synchronized(lock) {
            out.writeText(header)
            previousLogFile?.takeIf { it.exists() }?.let { out.appendText(it.readText()) }
            logFile?.takeIf { it.exists() }?.let { out.appendText(it.readText()) }
        }
        return out
    }

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }
}
