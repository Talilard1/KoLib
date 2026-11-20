package dev.kolib.app.ui.theme

import dev.kolib.app.tr
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The looks the app offers: its own, and Clean in other colours, a light and a dark in each palette. */
enum class ThemeChoice(
    val label: String,
    val description: String,
    /** The palette of a Clean look in other colours. */
    val palette: Palette? = null,
    /** Which of a palette's two it is. */
    val paletteDark: Boolean = false,
) {
    SYSTEM(tr("System default"), tr("Light or dark, matching your device's setting")),
    LIGHT(tr("Light"), tr("Warm paper, ink and gold")),
    DARK(tr("Dark"), tr("Gold on dark brown, like the opening. Easy on the eyes at night")),
    OLED(tr("OLED black"), tr("Gold on pure black, for OLED screens. Saves battery")),
    BOOKCASE(tr("Bookcase"), tr("Dark wood and brass, with your books on shelves")),
    BOOKCASE_DARK(tr("Bookcase, dark"), tr("Shelves in the Dark look's colours")),
    CLEAN_LIGHT(tr("Clean light"), tr("White and soft grey, with blue. Plain and bright")),
    CLEAN_DARK(tr("Clean dark"), tr("Black and graphite, with blue. Plain and quiet")),
    MANUSCRIPT(tr("Manuscript"), tr("Parchment, oxblood and gold leaf, on a patterned page")),
    ROMANCE(dev.kolib.app.trc("look", "Romance"), tr("Blush, rose and wine, with flowing titles")),
    CINEMA(tr("Cinema"), tr("Black and red, like a screen as the lights go down")),
    TERMINAL(tr("Terminal"), tr("Green type on black, like an old computer screen")),
    STARSHIP(tr("Starship"), tr("A bridge at night, deep space through the glass: ice blue, amber and teal")),
    HOLOGRAM(tr("Hologram"), tr("A projection in a dark room: pale blue light, scan lines, a beam from below")),
    RELIC(tr("Relic"), tr("Gunmetal and ember orange, like an old machine")),
    FLIGHT_DECK(tr("Flight deck"), tr("Charcoal panels with signal red, cyan and amber lights")),

    DYNAMIC_LIGHT(Palette.DYNAMIC, false), DYNAMIC_DARK(Palette.DYNAMIC, true),
    CATPPUCCIN_LIGHT(Palette.CATPPUCCIN, false), CATPPUCCIN_DARK(Palette.CATPPUCCIN, true),
    GREEN_APPLE_LIGHT(Palette.GREEN_APPLE, false), GREEN_APPLE_DARK(Palette.GREEN_APPLE, true),
    LAVENDER_LIGHT(Palette.LAVENDER, false), LAVENDER_DARK(Palette.LAVENDER, true),
    MIDNIGHT_DUSK_LIGHT(Palette.MIDNIGHT_DUSK, false), MIDNIGHT_DUSK_DARK(Palette.MIDNIGHT_DUSK, true),
    NORD_LIGHT(Palette.NORD, false), NORD_DARK(Palette.NORD, true),
    STRAWBERRY_LIGHT(Palette.STRAWBERRY, false), STRAWBERRY_DARK(Palette.STRAWBERRY, true),
    TAKO_LIGHT(Palette.TAKO, false), TAKO_DARK(Palette.TAKO, true),
    TEAL_TURQUOISE_LIGHT(Palette.TEAL_TURQUOISE, false), TEAL_TURQUOISE_DARK(Palette.TEAL_TURQUOISE, true),
    TIDAL_WAVE_LIGHT(Palette.TIDAL_WAVE, false), TIDAL_WAVE_DARK(Palette.TIDAL_WAVE, true),
    YIN_YANG_LIGHT(Palette.YIN_YANG, false), YIN_YANG_DARK(Palette.YIN_YANG, true),
    YOTSUBA_LIGHT(Palette.YOTSUBA, false), YOTSUBA_DARK(Palette.YOTSUBA, true),
    TOKYO_NIGHT_LIGHT(Palette.TOKYO_NIGHT, false), TOKYO_NIGHT_DARK(Palette.TOKYO_NIGHT, true),
    MONOCHROME_LIGHT(Palette.MONOCHROME, false), MONOCHROME_DARK(Palette.MONOCHROME, true),
    ;

    constructor(palette: Palette, dark: Boolean) : this(
        if (dark) tr("{0}, dark", palette.label) else tr("{0}, light", palette.label),
        if (dark) tr("Plain and quiet, on black, in the colours of {0}", palette.label) else tr("Plain and bright, in the colours of {0}", palette.label),
        palette,
        dark,
    )

    companion object {
        /** The Clean look in [palette]'s colours, light or [dark]. */
        fun of(palette: Palette, dark: Boolean): ThemeChoice = entries.first { it.palette == palette && it.paletteDark == dark }
    }
}

/** The chosen look, and how the opening animation plays (at all, with its sound, lighter), remembered between visits. */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _choice = MutableStateFlow(
        runCatching { ThemeChoice.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ThemeChoice.SYSTEM)
    )
    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    private val _opening = MutableStateFlow(prefs.getBoolean(OPENING, true))
    /** The opening animation plays when the app starts. */
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    private val _openingLight = MutableStateFlow(
        if (prefs.contains(OPENING_LIGHT)) {
            prefs.getBoolean(OPENING_LIGHT, false)
        } else {
            // The first time the app starts: the lighter opening on a phone, the full one on a tablet.
            (context.resources.configuration.smallestScreenWidthDp < 600).also { phone ->
                prefs.edit().putBoolean(OPENING_LIGHT, phone).apply()
            }
        }
    )
    /** The lighter opening, which does far less work each frame: for phones and slower devices. */
    val openingLight: StateFlow<Boolean> = _openingLight.asStateFlow()

    private val _openingSound = MutableStateFlow(prefs.getBoolean(OPENING_SOUND, true))
    /** The opening animation plays its sound. */
    val openingSound: StateFlow<Boolean> = _openingSound.asStateFlow()

    private val _introSeen = MutableStateFlow(prefs.getBoolean(INTRO_SEEN, false))
    /** The introduction has been gone through (or skipped): it's shown the first time KoLib starts. */
    val introSeen: StateFlow<Boolean> = _introSeen.asStateFlow()

    fun setIntroSeen(seen: Boolean) {
        prefs.edit().putBoolean(INTRO_SEEN, seen).apply()
        _introSeen.value = seen
    }

    private val _editorTourSeen = MutableStateFlow(prefs.getBoolean(EDITOR_TOUR_SEEN, false))
    /** The text editor's tour has been gone through (or skipped): it's given the first time the editor is opened. */
    val editorTourSeen: StateFlow<Boolean> = _editorTourSeen.asStateFlow()

    fun setEditorTourSeen(seen: Boolean) {
        prefs.edit().putBoolean(EDITOR_TOUR_SEEN, seen).apply()
        _editorTourSeen.value = seen
    }

    fun set(choice: ThemeChoice) {
        prefs.edit().putString(KEY, choice.name).apply()
        _choice.value = choice
    }

    fun setOpening(on: Boolean) {
        prefs.edit().putBoolean(OPENING, on).apply()
        _opening.value = on
    }

    fun setOpeningLight(on: Boolean) {
        prefs.edit().putBoolean(OPENING_LIGHT, on).apply()
        _openingLight.value = on
    }

    fun setOpeningSound(on: Boolean) {
        prefs.edit().putBoolean(OPENING_SOUND, on).apply()
        _openingSound.value = on
    }

    private companion object {
        const val KEY = "theme"
        const val OPENING = "opening"
        const val OPENING_SOUND = "openingSound"
        const val OPENING_LIGHT = "openingLight"
        const val INTRO_SEEN = "introSeen"
        const val EDITOR_TOUR_SEEN = "editorTourSeen"
    }
}
