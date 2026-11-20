package dev.kolib.app.comic

import android.content.Context
import android.content.pm.ActivityInfo
import dev.kolib.app.tr
import dev.kolib.app.trc
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** How a comic's pages follow each other, as in Mihon. */
enum class ComicMode(val label: () -> String, val paged: Boolean, val horizontal: Boolean) {
    LEFT_TO_RIGHT({ tr("Paged (left to right)") }, true, true),
    RIGHT_TO_LEFT({ tr("Paged (right to left)") }, true, true),
    VERTICAL({ tr("Paged (vertical)") }, true, false),
    STRIP({ tr("Long strip") }, false, false),
    STRIP_GAPS({ tr("Long strip with gaps") }, false, false),
}

/** Which way round the screen is while a comic's read, as in Mihon. */
enum class ComicRotation(val label: () -> String, val orientation: Int) {
    FREE({ tr("Free") }, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT({ tr("Portrait") }, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    LANDSCAPE({ tr("Landscape") }, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    LOCKED_PORTRAIT({ tr("Locked portrait") }, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LOCKED_LANDSCAPE({ tr("Locked landscape") }, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE_PORTRAIT({ tr("Reverse portrait") }, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
}

/** How a page fits the screen in the paged modes. */
enum class ComicScale(val label: () -> String) {
    FIT_SCREEN({ tr("Fit screen") }),
    STRETCH({ tr("Stretch") }),
    FIT_WIDTH({ tr("Fit width") }),
    FIT_HEIGHT({ tr("Fit height") }),
    ORIGINAL({ tr("Original size") }),
    SMART({ tr("Smart fit") }),
}

/** Where a page bigger than the screen is shown first. */
enum class ComicZoomStart(val label: () -> String) {
    AUTO({ tr("Automatic") }),
    LEFT({ tr("Left") }),
    RIGHT({ tr("Right") }),
    CENTER({ tr("Middle") }),
}

/** Which parts of the screen turn the page when tapped (the rest shows the menu). */
enum class ComicTaps(val label: () -> String) {
    DEFAULT({ tr("Default") }),
    L_SHAPED({ tr("L shaped") }),
    KINDLE({ tr("Kindle-ish") }),
    EDGE({ tr("Edges") }),
    RIGHT_LEFT({ tr("Right and left") }),
    OFF({ tr("Off") }),
}

enum class ComicInvert(val label: () -> String) {
    NONE({ tr("No") }),
    HORIZONTAL({ tr("Left and right") }),
    VERTICAL({ tr("Top and bottom") }),
    BOTH({ tr("Both") }),
}

enum class ComicBackground(val label: () -> String) {
    BLACK({ tr("Black") }),
    GRAY({ tr("Grey") }),
    WHITE({ tr("White") }),
    AUTO({ tr("Automatic") }),
}

/** How the custom colour filter mixes with the page, as Mihon's blend modes. */
enum class ComicBlend(val label: () -> String) {
    DEFAULT({ tr("Default") }),
    MULTIPLY({ tr("Multiply") }),
    SCREEN({ trc("blend", "Screen") }),
    OVERLAY({ tr("Overlay") }),
    LIGHTEN({ tr("Dodge / Lighten") }),
    DARKEN({ tr("Burn / Darken") }),
}

/** What the screen flashes with as the page changes (to clear an e-ink screen's ghosting). */
enum class ComicFlash(val label: () -> String) {
    BLACK({ tr("Black") }),
    WHITE({ tr("White") }),
    WHITE_BLACK({ tr("White, then black") }),
}

/** The comic reader's settings: as Mihon's reader has them. */
data class ComicSettings(
    /** For comics that don't say (a manga's ComicInfo says right to left), and haven't been set on their own. */
    val mode: ComicMode = ComicMode.LEFT_TO_RIGHT,
    /** For comics whose series hasn't a rotation of its own. */
    val rotation: ComicRotation = ComicRotation.FREE,
    val scale: ComicScale = ComicScale.FIT_SCREEN,
    val zoomStart: ComicZoomStart = ComicZoomStart.AUTO,
    val cropPaged: Boolean = false,
    val cropStrip: Boolean = false,
    /** A page wider than it's tall (a spread) shown as two pages, one after the other. */
    val splitWide: Boolean = false,
    val invertSplit: Boolean = false,
    /** A wide page turned on its side to fill a screen held upright. */
    val rotateWide: Boolean = false,
    /** Turned the other way (anticlockwise). */
    val invertRotate: Boolean = false,
    val splitWideStrip: Boolean = false,
    val invertSplitStrip: Boolean = false,
    val rotateWideStrip: Boolean = false,
    val invertRotateStrip: Boolean = false,
    /** A wide page on a screen held upright is shown filling the screen's height, to be moved along. */
    val zoomWide: Boolean = true,
    /** Tapping or pressing on through a page bigger than the screen moves along it before turning. */
    val panWide: Boolean = true,
    val tapsPaged: ComicTaps = ComicTaps.DEFAULT,
    val invertTapsPaged: ComicInvert = ComicInvert.NONE,
    val tapsStrip: ComicTaps = ComicTaps.DEFAULT,
    val invertTapsStrip: ComicInvert = ComicInvert.NONE,
    val showTapZones: Boolean = false,
    val background: ComicBackground = ComicBackground.BLACK,
    val animate: Boolean = true,
    val pageNumber: Boolean = true,
    /** The long strip's space either side, in percent of the screen's width. */
    val sidePadding: Int = 0,
    val doubleTapZoom: Boolean = true,
    val volumeKeys: Boolean = false,
    val invertVolume: Boolean = false,
    val longTap: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** The status and navigation bars hidden while reading. */
    val fullscreen: Boolean = true,
    /** Pages drawn round the camera too, where the screen has a cutout for it. */
    val cutout: Boolean = true,
    /** The page between one comic and the next in its series always shown; otherwise they're joined, as one. */
    val alwaysTransition: Boolean = true,
    val flash: Boolean = false,
    val flashMillis: Int = 100,
    /** Every this many pages. */
    val flashEvery: Int = 1,
    val flashWith: ComicFlash = ComicFlash.BLACK,
    /** The reading modes the page slider stands up the side of the screen in. */
    val verticalIn: Set<ComicMode> = emptySet(),
    val verticalLeft: Boolean = false,
    /** How much of the screen's height the upright slider takes, in percent. */
    val verticalHeight: Int = 65,
    val customBrightness: Boolean = false,
    /** -75 to 100: above 0, the screen's brightness in percent; below, as dim as it goes and darker still. 0 is the tablet's own. */
    val brightness: Int = 0,
    val colourFilter: Boolean = false,
    /** The filter's colour, alpha included, as an ARGB number. */
    val colour: Int = 0,
    val blend: ComicBlend = ComicBlend.DEFAULT,
    val greyscale: Boolean = false,
    val inverted: Boolean = false,
) {
    fun taps(mode: ComicMode) = if (mode.paged) tapsPaged else tapsStrip
    fun invertTaps(mode: ComicMode) = if (mode.paged) invertTapsPaged else invertTapsStrip
    fun cropping(mode: ComicMode) = if (mode.paged) cropPaged else cropStrip

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode.name).put("rotation", rotation.name).put("scale", scale.name).put("zoomStart", zoomStart.name)
        .put("cropPaged", cropPaged).put("cropStrip", cropStrip).put("splitWide", splitWide).put("invertSplit", invertSplit)
        .put("rotateWide", rotateWide).put("invertRotate", invertRotate)
        .put("splitWideStrip", splitWideStrip).put("invertSplitStrip", invertSplitStrip)
        .put("rotateWideStrip", rotateWideStrip).put("invertRotateStrip", invertRotateStrip)
        .put("zoomWide", zoomWide).put("panWide", panWide)
        .put("tapsPaged", tapsPaged.name).put("invertTapsPaged", invertTapsPaged.name)
        .put("tapsStrip", tapsStrip.name).put("invertTapsStrip", invertTapsStrip.name)
        .put("showTapZones", showTapZones).put("background", background.name)
        .put("animate", animate).put("pageNumber", pageNumber).put("sidePadding", sidePadding).put("doubleTapZoom", doubleTapZoom)
        .put("volumeKeys", volumeKeys).put("invertVolume", invertVolume).put("longTap", longTap).put("keepScreenOn", keepScreenOn)
        .put("fullscreen", fullscreen).put("cutout", cutout).put("alwaysTransition", alwaysTransition)
        .put("flash", flash).put("flashMillis", flashMillis).put("flashEvery", flashEvery).put("flashWith", flashWith.name)
        .put("verticalIn", JSONArray(verticalIn.map { it.name })).put("verticalLeft", verticalLeft).put("verticalHeight", verticalHeight)
        .put("customBrightness", customBrightness).put("brightness", brightness)
        .put("colourFilter", colourFilter).put("colour", colour).put("blend", blend.name)
        .put("greyscale", greyscale).put("inverted", inverted)

    companion object {
        const val BRIGHTNESS_MIN = -75
        const val BRIGHTNESS_MAX = 100
        const val SIDE_PADDING_MAX = 25

        private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, d: E): E =
            optString(key).let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: d

        /** Read from what was saved; [firstRotation] for settings saved before there was a rotation of their own. */
        fun fromJson(o: JSONObject, firstRotation: ComicRotation = ComicRotation.FREE): ComicSettings {
            val d = ComicSettings()
            // Before taps were set for each way of reading, one setting did for both.
            val taps = o.enumOr("taps", d.tapsPaged)
            val invertTaps = o.enumOr("invertTaps", d.invertTapsPaged)
            val vertical = o.optJSONArray("verticalIn")?.let { a ->
                (0 until a.length()).mapNotNull { i -> ComicMode.entries.firstOrNull { it.name == a.optString(i) } }.toSet()
            } ?: d.verticalIn
            return ComicSettings(
                mode = o.enumOr("mode", d.mode), rotation = o.enumOr("rotation", firstRotation),
                scale = o.enumOr("scale", d.scale), zoomStart = o.enumOr("zoomStart", d.zoomStart),
                cropPaged = o.optBoolean("cropPaged", d.cropPaged), cropStrip = o.optBoolean("cropStrip", d.cropStrip),
                splitWide = o.optBoolean("splitWide", d.splitWide), invertSplit = o.optBoolean("invertSplit", d.invertSplit),
                rotateWide = o.optBoolean("rotateWide", d.rotateWide), invertRotate = o.optBoolean("invertRotate", d.invertRotate),
                splitWideStrip = o.optBoolean("splitWideStrip", d.splitWideStrip), invertSplitStrip = o.optBoolean("invertSplitStrip", d.invertSplitStrip),
                rotateWideStrip = o.optBoolean("rotateWideStrip", d.rotateWideStrip), invertRotateStrip = o.optBoolean("invertRotateStrip", d.invertRotateStrip),
                zoomWide = o.optBoolean("zoomWide", d.zoomWide), panWide = o.optBoolean("panWide", d.panWide),
                tapsPaged = o.enumOr("tapsPaged", taps), invertTapsPaged = o.enumOr("invertTapsPaged", invertTaps),
                tapsStrip = o.enumOr("tapsStrip", taps), invertTapsStrip = o.enumOr("invertTapsStrip", invertTaps),
                showTapZones = o.optBoolean("showTapZones", d.showTapZones), background = o.enumOr("background", d.background),
                animate = o.optBoolean("animate", d.animate), pageNumber = o.optBoolean("pageNumber", d.pageNumber),
                sidePadding = o.optInt("sidePadding", d.sidePadding).coerceIn(0, SIDE_PADDING_MAX),
                doubleTapZoom = o.optBoolean("doubleTapZoom", d.doubleTapZoom), volumeKeys = o.optBoolean("volumeKeys", d.volumeKeys),
                invertVolume = o.optBoolean("invertVolume", d.invertVolume), longTap = o.optBoolean("longTap", d.longTap),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                fullscreen = o.optBoolean("fullscreen", d.fullscreen), cutout = o.optBoolean("cutout", d.cutout),
                alwaysTransition = o.optBoolean("alwaysTransition", d.alwaysTransition),
                flash = o.optBoolean("flash", d.flash), flashMillis = o.optInt("flashMillis", d.flashMillis).coerceIn(100, 1500),
                flashEvery = o.optInt("flashEvery", d.flashEvery).coerceIn(1, 10), flashWith = o.enumOr("flashWith", d.flashWith),
                verticalIn = vertical, verticalLeft = o.optBoolean("verticalLeft", d.verticalLeft),
                verticalHeight = o.optInt("verticalHeight", d.verticalHeight).coerceIn(65, 100),
                customBrightness = o.optBoolean("customBrightness", d.customBrightness),
                brightness = o.optInt("brightness", d.brightness).coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX),
                colourFilter = o.optBoolean("colourFilter", d.colourFilter), colour = o.optInt("colour", d.colour),
                blend = o.enumOr("blend", d.blend), greyscale = o.optBoolean("greyscale", d.greyscale),
                inverted = o.optBoolean("inverted", d.inverted),
            )
        }
    }
}

/** The comic reader's settings, kept in a file of the app's own; one for the whole app. */
class ComicSettingsStore private constructor(private val file: File, firstRotation: ComicRotation) {
    private val _settings = MutableStateFlow(
        runCatching { ComicSettings.fromJson(JSONObject(file.readText()), firstRotation) }.getOrDefault(ComicSettings(rotation = firstRotation)),
    )
    val settings: StateFlow<ComicSettings> = _settings.asStateFlow()
    private val writing = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Changes the settings; they're written to the file a moment later, once for a run of changes (a slider dragged). */
    fun update(change: (ComicSettings) -> ComicSettings) {
        _settings.update(change)
        if (writing.compareAndSet(false, true)) {
            writer.execute {
                writing.set(false)
                runCatching {
                    val tmp = File(file.parentFile, file.name + ".part")
                    tmp.writeText(_settings.value.toJson().toString())
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var one: ComicSettingsStore? = null

        private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "comic-settings").apply { isDaemon = true } }

        /**
         * The settings; [firstRotation] is the rotation comics had before they had their own (the book reader's), for
         * settings saved before then.
         */
        fun get(context: Context, firstRotation: ComicRotation = ComicRotation.FREE): ComicSettingsStore = one ?: synchronized(this) {
            one ?: ComicSettingsStore(File(context.filesDir, "comic-reader.json"), firstRotation).also { one = it }
        }

        /** The reading mode chosen for the comic kept in [bookDir] before modes were kept for each series. */
        fun modeOf(bookDir: File): ComicMode? = runCatching {
            JSONObject(File(bookDir, "comic.json").readText()).optString("mode").let { n -> ComicMode.entries.firstOrNull { it.name == n } }
        }.getOrNull()

        /** Lets go of the reading mode chosen for one comic before modes were kept for each series. */
        fun forgetMode(bookDir: File) {
            runCatching { File(bookDir, "comic.json").delete() }
        }
    }
}

/** A series' own reading mode and rotation (or a comic's, in none), where they've been chosen rather than left to the defaults. */
data class ComicOwn(val mode: ComicMode? = null, val rotation: ComicRotation? = null)

/**
 * What's been chosen for each series (and each comic in none) "for this series", as Mihon keeps them: by the key of
 * its entry in the library ([dev.kolib.app.library.ComicEntry.key]).
 */
class ComicOwnStore private constructor(private val file: File?) {
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, ComicOwn>> = _all.asStateFlow()

    private fun load(): Map<String, ComicOwn> = runCatching {
        val o = JSONObject(file!!.readText())
        o.keys().asSequence().associateWith { k ->
            val e = o.getJSONObject(k)
            ComicOwn(
                mode = ComicMode.entries.firstOrNull { it.name == e.optString("mode") },
                rotation = ComicRotation.entries.firstOrNull { it.name == e.optString("rotation") },
            )
        }
    }.getOrDefault(emptyMap())

    fun of(key: String): ComicOwn = _all.value[key] ?: ComicOwn()

    fun set(key: String, own: ComicOwn) {
        _all.update { if (own == ComicOwn()) it - key else it + (key to own) }
        val f = file ?: return
        runCatching {
            val o = JSONObject()
            for ((k, v) in _all.value) {
                o.put(k, JSONObject().apply { v.mode?.let { put("mode", it.name) }; v.rotation?.let { put("rotation", it.name) } })
            }
            val tmp = File(f.parentFile, f.name + ".part")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    companion object {
        @Volatile
        private var one: ComicOwnStore? = null

        fun get(context: Context): ComicOwnStore = one ?: synchronized(this) {
            one ?: ComicOwnStore(File(context.filesDir, "comic-series.json")).also { one = it }
        }

        /** Kept only while the app's open: for tests. */
        fun inMemory(): ComicOwnStore = ComicOwnStore(null)
    }
}
