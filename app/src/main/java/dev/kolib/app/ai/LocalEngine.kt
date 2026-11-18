package dev.kolib.app.ai

import android.content.Context
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import java.io.File

/** A model in a file on this device, run by llama.cpp. */
class LocalEngine private constructor(private var handle: Long, override val contextTokens: Int) : AiEngine {

    /** How fast the last prompt was read, in tokens a second: what a prompt of a given length will take. */
    @Volatile
    var readSpeed: Float? = null
        private set

    override var lastError: String? = null
        private set

    override fun countTokens(text: String): Int =
        if (handle == 0L) Tokens.guess(text) else LlamaNative.countTokens(handle, text.toByteArray(Charsets.UTF_8))

    override fun generate(messages: List<AiMessage>, maxTokens: Int, temperature: Float, onProgress: (Float) -> Unit, onText: (String) -> Unit): AiEnd {
        val h = handle
        if (h == 0L) return AiEnd.FAILED
        lastError = null
        val started = System.nanoTime()
        var readIn = 0L
        val sink = object : LlamaNative.Sink {
            override fun progress(p: Float): Boolean {
                if (p >= 1f) readIn = System.nanoTime() - started
                onProgress(p)
                return true
            }

            override fun text(utf8: ByteArray): Boolean {
                onText(String(utf8, Charsets.UTF_8))
                return true
            }
        }
        val code = LlamaNative.generate(
            h, messages.map { it.role }.toTypedArray(), messages.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray(),
            maxTokens, temperature, sink,
        )
        // Only what it actually read counts (what it held already, it didn't), and only enough to tell: a little is
        // over before the clock has anything to measure.
        val read = LlamaNative.lastRead(h)
        if (readIn > 300_000_000L && read > 200) readSpeed = read / (readIn / 1e9f)
        return ended(h, code)
    }

    override fun prefill(messages: List<AiMessage>): AiEnd {
        val h = handle
        if (h == 0L || messages.isEmpty()) return AiEnd.FAILED
        lastError = null
        val started = System.nanoTime()
        val code = LlamaNative.prefill(h, messages.map { it.role }.toTypedArray(), messages.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray())
        val took = System.nanoTime() - started
        val read = LlamaNative.lastRead(h)
        if (code == 0 && took > 300_000_000L && read > 200) readSpeed = read / (took / 1e9f)
        return ended(h, code)
    }

    override fun yieldAhead() {
        val h = handle
        if (h != 0L) LlamaNative.yieldAhead(h)
    }

    private fun ended(h: Long, code: Int): AiEnd = when (code) {
        0 -> AiEnd.DONE
        1 -> AiEnd.LENGTH
        2 -> AiEnd.CANCELLED
        3 -> AiEnd.TOO_LONG
        else -> {
            lastError = String(LlamaNative.error(h), Charsets.UTF_8).ifBlank { null }
            AiEnd.FAILED
        }
    }

    override fun stop() {
        val h = handle
        if (h != 0L) LlamaNative.stop(h)
    }

    @Synchronized
    override fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) LlamaNative.unload(h)
    }

    companion object {
        /**
         * Opens the model in [file], holding up to [wantContext] tokens (or as many as the model was made for, if
         * that's fewer).
         */
        fun open(context: Context, file: File, wantContext: Int): LocalEngine {
            if (!LlamaNative.available(context)) throw AiException(tr("This device can't run AI models itself. You can still use a server of your own."))
            if (!file.isFile) throw AiException(tr("The model's file is missing. Download it again."))
            val copied = LlamaNative.copiedSize(file.absolutePath)
            val room = copyRoom(Device.memory(context), Device.available(context))
            val copy = copied in 0..room
            AppLog.i(
                "AI", "Opening ${file.name} (${file.length() / 1_000_000} MB): " +
                    (if (copy) "its weights copied into a faster order (${copied / 1_000_000} MB)"
                    else "its weights used from the file (${copied / 1_000_000} MB would be copied; room for ${room / 1_000_000} MB)"),
            )
            val handle = LlamaNative.load(file.absolutePath, wantContext, Device.threads(), copy)
            if (handle == 0L) {
                val why = String(LlamaNative.loadError(), Charsets.UTF_8)
                throw AiException(
                    if (why == "memory") tr("There isn't enough free memory for this model. Close other apps, or choose a smaller model.")
                    else tr("This model couldn't be opened. Its file may be damaged, or it's a kind KoLib can't run."),
                )
            }
            val held = String(LlamaNative.describe(handle), Charsets.UTF_8).substringAfterLast('\n').trim().toIntOrNull() ?: wantContext
            return LocalEngine(handle, held)
        }
    }
}

/**
 * How many bytes of a model's weights may be copied into an order that's faster to work through, on a device with
 * [total] bytes of memory of which [available] are free now. The copy is held in memory beside the file, so only as
 * much as leaves enough to spare. (On an 8 GB tablet, 2.2 GB copied ran well; 2.9 GB took over 6 GB at its peak,
 * enough for Android to close the app.)
 */
fun copyRoom(total: Long, available: Long): Long =
    if (total <= 0) 0 else minOf((total * 0.32).toLong(), available - 500_000_000L).coerceAtLeast(0)

/** Counting tokens without a model: close enough to keep a prompt inside what a model can hold. */
object Tokens {
    /** About how many tokens [text] is: a little over one for every four letters; one for each Chinese, Japanese or Korean character. */
    fun guess(text: String): Int {
        var wide = 0
        for (c in text) if (c.code >= 0x2E80) wide++
        return wide + ((text.length - wide) / 3.5f).toInt() + 1
    }
}
