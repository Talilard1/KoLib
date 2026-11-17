package dev.kobolibrary.app.ui.theme

import dev.kobolibrary.app.tr
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
