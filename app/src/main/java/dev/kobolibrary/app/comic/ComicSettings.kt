package dev.kobolibrary.app.comic

import android.content.Context
import dev.kobolibrary.app.tr
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** How a comic's pages follow each other, as in Mihon. */
enum class ComicMode(val label: () -> String, val paged: Boolean, val horizontal: Boolean) {
    LEFT_TO_RIGHT({ tr("Left to right") }, true, true),
    RIGHT_TO_LEFT({ tr("Right to left (manga)") }, true, true),
    VERTICAL({ tr("Top to bottom") }, true, false),
    STRIP({ tr("Long strip") }, false, false),
    STRIP_GAPS({ tr("Long strip with gaps") }, false, false),
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
    DEFAULT({ tr("Default for the reading mode") }),
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
    AUTO({ tr("Match the page") }),
}

/** The comic reader's settings: as Mihon's reader has them. */
data class ComicSettings(
    /** For comics that don't say (a manga's ComicInfo says right to left), and haven't been set on their own. */
    val mode: ComicMode = ComicMode.LEFT_TO_RIGHT,
    val scale: ComicScale = ComicScale.FIT_SCREEN,
    val zoomStart: ComicZoomStart = ComicZoomStart.AUTO,
    val cropPaged: Boolean = false,
    val cropStrip: Boolean = false,
    /** A page wider than it's tall (a spread) shown as two pages, one after the other. */
    val splitWide: Boolean = false,
    val invertSplit: Boolean = false,
    /** A wide page turned on its side to fill a screen held upright. */
    val rotateWide: Boolean = false,
    /** A wide page on a screen held upright is shown filling the screen's height, to be moved along. */
    val zoomWide: Boolean = true,
    /** Tapping or pressing on through a page bigger than the screen moves along it before turning. */
    val panWide: Boolean = true,
    val taps: ComicTaps = ComicTaps.DEFAULT,
    val invertTaps: ComicInvert = ComicInvert.NONE,
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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode.name).put("scale", scale.name).put("zoomStart", zoomStart.name)
        .put("cropPaged", cropPaged).put("cropStrip", cropStrip).put("splitWide", splitWide).put("invertSplit", invertSplit)
        .put("rotateWide", rotateWide).put("zoomWide", zoomWide).put("panWide", panWide).put("taps", taps.name)
        .put("invertTaps", invertTaps.name).put("showTapZones", showTapZones).put("background", background.name)
        .put("animate", animate).put("pageNumber", pageNumber).put("sidePadding", sidePadding).put("doubleTapZoom", doubleTapZoom)
        .put("volumeKeys", volumeKeys).put("invertVolume", invertVolume).put("longTap", longTap).put("keepScreenOn", keepScreenOn)

    companion object {
        val SIDE_PADDINGS = listOf(0, 5, 10, 15, 20, 25)

        private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, d: E): E =
            optString(key).let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: d

        fun fromJson(o: JSONObject): ComicSettings {
            val d = ComicSettings()
            return ComicSettings(
                mode = o.enumOr("mode", d.mode), scale = o.enumOr("scale", d.scale), zoomStart = o.enumOr("zoomStart", d.zoomStart),
                cropPaged = o.optBoolean("cropPaged", d.cropPaged), cropStrip = o.optBoolean("cropStrip", d.cropStrip),
                splitWide = o.optBoolean("splitWide", d.splitWide), invertSplit = o.optBoolean("invertSplit", d.invertSplit),
                rotateWide = o.optBoolean("rotateWide", d.rotateWide), zoomWide = o.optBoolean("zoomWide", d.zoomWide),
                panWide = o.optBoolean("panWide", d.panWide), taps = o.enumOr("taps", d.taps), invertTaps = o.enumOr("invertTaps", d.invertTaps),
                showTapZones = o.optBoolean("showTapZones", d.showTapZones), background = o.enumOr("background", d.background),
                animate = o.optBoolean("animate", d.animate), pageNumber = o.optBoolean("pageNumber", d.pageNumber),
                sidePadding = o.optInt("sidePadding", d.sidePadding).let { p -> SIDE_PADDINGS.minByOrNull { kotlin.math.abs(it - p) } ?: 0 },
                doubleTapZoom = o.optBoolean("doubleTapZoom", d.doubleTapZoom), volumeKeys = o.optBoolean("volumeKeys", d.volumeKeys),
                invertVolume = o.optBoolean("invertVolume", d.invertVolume), longTap = o.optBoolean("longTap", d.longTap),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
            )
        }
    }
}

/** The comic reader's settings, kept in a file of the app's own; one for the whole app. */
class ComicSettingsStore private constructor(private val file: File) {
    private val _settings = MutableStateFlow(runCatching { ComicSettings.fromJson(JSONObject(file.readText())) }.getOrDefault(ComicSettings()))
    val settings: StateFlow<ComicSettings> = _settings.asStateFlow()

    fun update(change: (ComicSettings) -> ComicSettings) {
        _settings.update(change)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".part")
            tmp.writeText(_settings.value.toJson().toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    companion object {
        @Volatile
        private var one: ComicSettingsStore? = null

        fun get(context: Context): ComicSettingsStore = one ?: synchronized(this) {
            one ?: ComicSettingsStore(File(context.filesDir, "comic-reader.json")).also { one = it }
        }

        /** The reading mode chosen for the comic kept in [bookDir], if one was. */
        fun modeOf(bookDir: File): ComicMode? = runCatching {
            JSONObject(File(bookDir, "comic.json").readText()).optString("mode").let { n -> ComicMode.entries.firstOrNull { it.name == n } }
        }.getOrNull()

        fun saveMode(bookDir: File, mode: ComicMode?) {
            runCatching {
                val f = File(bookDir, "comic.json")
                if (mode == null) f.delete() else f.writeText(JSONObject().put("mode", mode.name).toString())
            }
        }
    }
}
