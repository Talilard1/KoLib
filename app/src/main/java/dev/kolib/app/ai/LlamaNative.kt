package dev.kolib.app.ai

import android.content.Context
import dev.kolib.app.log.AppLog

/**
 * llama.cpp, built into the app for 64-bit devices (app/src/main/cpp). Text crosses as UTF-8 bytes, so any
 * character survives the trip.
 */
internal object LlamaNative {
    /** Hears a reply as it's made; either returns false to stop it. Called by name from the native side. */
    interface Sink {
        fun progress(p: Float): Boolean
        fun text(utf8: ByteArray): Boolean
    }

    private var state = 0

    /** Whether this build of the app, on this device, can run models: the libraries are there and load. */
    @Synchronized
    fun available(context: Context): Boolean {
        if (state == 0) {
            state = try {
                System.loadLibrary("kolib-llm")
                init(context.applicationInfo.nativeLibraryDir)
                1
            } catch (e: Throwable) {
                AppLog.w("AI", "No on-device models in this build or on this device: ${e.message}")
                -1
            }
        }
        return state == 1
    }

    external fun init(dir: String)
    external fun load(path: String, nCtx: Int, nThreads: Int): Long
    external fun loadError(): ByteArray
    external fun unload(handle: Long)
    external fun stop(handle: Long)
    external fun countTokens(handle: Long, text: ByteArray): Int
    external fun describe(handle: Long): ByteArray
    external fun error(handle: Long): ByteArray
    external fun generate(handle: Long, roles: Array<String>, contents: Array<ByteArray>, maxTokens: Int, temperature: Float, sink: Sink): Int
}
