package dev.kolib.app.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** What this device is like, for choosing a model it can run well. */
object Device {
    /** The device's memory, in bytes. */
    fun memory(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.totalMem
    }

    /** How much of the device's memory is free now, or could be freed without closing anything, in bytes. */
    fun available(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.availMem
    }

    /** Whether the device is charging (plugged in, and taking it). */
    fun charging(context: Context): Boolean =
        runCatching { context.getSystemService(android.os.BatteryManager::class.java)?.isCharging == true }.getOrDefault(false)

    /** Each processor core's top speed in kHz, as the system reports it; empty where it doesn't. */
    fun coreSpeeds(root: File = File("/sys/devices/system/cpu")): List<Long> =
        (root.listFiles { f -> f.name.matches(Regex("cpu\\d+")) } ?: emptyArray())
            .mapNotNull { runCatching { File(it, "cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull() }

    /**
     * How many threads to run a model on: the fast cores, between two and four. The slow cores of a phone's
     * processor would hold the fast ones back, and more than four add heat faster than speed.
     */
    fun threads(speeds: List<Long> = coreSpeeds(), cores: Int = Runtime.getRuntime().availableProcessors()): Int {
        if (speeds.isEmpty()) return (cores - 2).coerceIn(2, 4)
        val top = speeds.max()
        return speeds.count { it >= top * 0.8 }.coerceIn(2, 4)
    }

    /**
     * A recent, fast processor (2022's phones and tablets on): its cores can do the sums models are made of in
     * bigger steps (the "i8mm" or "sve2" features). Older ones run the same models several times slower.
     */
    fun fast(cpuinfo: String = cpuinfo()): Boolean {
        val words = features(cpuinfo)
        return "i8mm" in words || "sve2" in words || "avx512f" in words || "avx_vnni" in words
    }

    /** How quickly a processor runs a model, for picking the models that answer at a reading pace on it. */
    enum class Speed { SLOW, MID, FAST }

    /**
     * The processor's [Speed]: [fast]; a few years old, with the dot-product sums ("asimddp", "avx2") or quick
     * cores; or older and slower than that.
     */
    fun speed(cpuinfo: String = cpuinfo(), speeds: List<Long> = coreSpeeds()): Speed {
        val words = features(cpuinfo)
        return when {
            fast(cpuinfo) -> Speed.FAST
            "asimddp" in words || "avx2" in words || (speeds.maxOrNull() ?: 0L) >= 2_200_000L -> Speed.MID
            else -> Speed.SLOW
        }
    }

    private fun cpuinfo(): String = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")

    /** What the processor can do, as /proc/cpuinfo lists it ("Features" on ARM, "flags" on x86). */
    private fun features(cpuinfo: String): Set<String> =
        cpuinfo.lineSequence().filter { it.startsWith("Features") || it.startsWith("flags") }
            .joinToString(" ").split(Regex("\\s+")).toSet()
}
