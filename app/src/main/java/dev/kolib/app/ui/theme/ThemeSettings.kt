package dev.kolib.app.ui.theme

import dev.kolib.app.tr
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The looks the app offers. */
enum class ThemeChoice(val label: String, val description: String) {
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
    STARSHIP(tr("Starship"), tr("A ship's hull at night: steel-blue panels, with ice blue, amber and teal lights")),
    HOLOGRAM(tr("Hologram"), tr("Pale blue light in the air, drawn as a fine mesh")),
    RELIC(tr("Relic"), tr("Gunmetal and ember orange, like an old machine")),
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

    private val _toursSeen = MutableStateFlow(prefs.getStringSet(TOURS_SEEN, emptySet()).orEmpty().toSet())
    /** The tours given already (see dev.kolib.app.ui.tour.Tours): each is given the first time its screen is opened. */
    val toursSeen: StateFlow<Set<String>> = _toursSeen.asStateFlow()

    fun setTourSeen(name: String) {
        val now = _toursSeen.value + name
        prefs.edit().putStringSet(TOURS_SEEN, now).apply()
        _toursSeen.value = now
    }

    /**
     * Every tour is given again, the editor's too, the next time its screen is opened, with tours turned on. The
     * sample books aren't added again.
     */
    fun showToursAgain() {
        val now = _toursSeen.value.filter { it == dev.kolib.app.ui.tour.Tours.SAMPLES }.toSet()
        prefs.edit().putStringSet(TOURS_SEEN, now).putBoolean(EDITOR_TOUR_SEEN, false).putBoolean(TOURS_OFF, false).apply()
        _toursSeen.value = now
        _editorTourSeen.value = false
        _toursOff.value = false
    }

    private val _toursOff = MutableStateFlow(prefs.getBoolean(TOURS_OFF, false))
    /** No tours are given: turned off from one of them, or in Settings. */
    val toursOff: StateFlow<Boolean> = _toursOff.asStateFlow()

    fun setToursOff(off: Boolean) {
        prefs.edit().putBoolean(TOURS_OFF, off).apply()
        _toursOff.value = off
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
        const val TOURS_SEEN = "toursSeen"
        const val TOURS_OFF = "toursOff"
    }
}
