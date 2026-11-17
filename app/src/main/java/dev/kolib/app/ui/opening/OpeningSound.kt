package dev.kolib.app.ui.opening

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import dev.kolib.app.R
import dev.kolib.app.log.AppLog

/**
 * The opening's sound, at the media volume. Anything else playing (music, an audiobook) is turned
 * down while it plays, and back up after. It looks after itself once started: it's let go when it
 * ends, after fading out, or when stopped.
 */
class OpeningSound private constructor(
    private val audio: AudioManager,
    private val player: MediaPlayer,
    private val focus: AudioFocusRequest,
) {
    private val main = Handler(Looper.getMainLooper())
    private var released = false
    private var fading = false
    private var started = false

    fun start() {
        if (released || started) return
        started = true
        audio.requestAudioFocus(focus)
        player.setOnCompletionListener { release() }
        runCatching { player.start() }.onFailure { release() }
    }

    /** Fades out over a moment, then stops (when the opening is skipped). */
    fun fadeOut(millis: Long = 280) {
        if (released || fading) return
        fading = true
        val steps = 14
        var step = 0
        main.post(object : Runnable {
            override fun run() {
                if (released) return
                step++
                val volume = (1f - step / steps.toFloat()).coerceAtLeast(0f)
                runCatching { player.setVolume(volume, volume) }
                if (step < steps) main.postDelayed(this, millis / steps) else release()
            }
        })
    }

    /**
     * How far into the sound is being heard, in seconds: 0 until it has really started, which can
     * take a phone a good moment after being asked.
     */
    fun heard(): Float =
        if (released || !started) 0f else runCatching { player.currentPosition / 1000f }.getOrDefault(0f)

    /** The opening has gone: the sound plays out its last moment, unless it never started. */
    fun letFinish() {
        if (!started) release()
    }

    fun stop() = release()

    private fun release() {
        if (released) return
        released = true
        runCatching { if (player.isPlaying) player.stop() }
        player.release()
        audio.abandonAudioFocusRequest(focus)
    }

    companion object {
        fun create(context: Context): OpeningSound? = runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(context, R.raw.kolib_opening, attributes, audio.generateAudioSessionId())
                ?: return@runCatching null
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            OpeningSound(audio, player, focus)
        }.onFailure { AppLog.w("Opening", "Couldn't play the opening sound: ${it.message}") }.getOrNull()
    }
}
