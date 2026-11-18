package dev.kolib.app.ai

import android.content.Context
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
        val promptTokens = messages.sumOf { countTokens(it.content) + 8 }
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
        // Only a prompt long enough to tell: a short one is over before the clock has anything to measure.
        if (readIn > 300_000_000L && promptTokens > 200) readSpeed = promptTokens / (readIn / 1e9f)
        return when (code) {
            0 -> AiEnd.DONE
            1 -> AiEnd.LENGTH
            2 -> AiEnd.CANCELLED
            3 -> AiEnd.TOO_LONG
            else -> {
                lastError = String(LlamaNative.error(h), Charsets.UTF_8).ifBlank { null }
                AiEnd.FAILED
            }
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
            val handle = LlamaNative.load(file.absolutePath, wantContext, Device.threads())
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

/** Counting tokens without a model: close enough to keep a prompt inside what a model can hold. */
object Tokens {
    /** About how many tokens [text] is: a little over one for every four letters; one for each Chinese, Japanese or Korean character. */
    fun guess(text: String): Int {
        var wide = 0
        for (c in text) if (c.code >= 0x2E80) wide++
        return wide + ((text.length - wide) / 3.5f).toInt() + 1
    }
}
