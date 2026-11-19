package dev.kolib.app.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Chat instructions kept under a name, to switch to (in the chat, or the AI settings). */
data class PromptPreset(val name: String, val text: String)

/** Which model answers, and the user's own server if they have one. */
data class AiSettings(
    /** "" for none; "model:<id>" for one of KoLib's; "own:<name>" for a file of the user's; "server". */
    val source: String = "",
    val serverAddress: String = "",
    val serverKey: String = "",
    val serverModel: String = "",
    /** The dictionary (Wiktionary) is asked when a word is looked up. */
    val dictionary: Boolean = true,
    /**
     * The instructions the chat has now, for the character it is ([mascot]); empty for the character's own (see
     * Prompts.asCharacter).
     */
    val chatPrompt: String = "",
    /** Notes on books (see BookNotes) are only written while the device is charging. */
    val notesOnCharge: Boolean = false,
    /** Who the AI is on the screen: see Mascot (and the user's own characters). */
    val mascot: String = "robot",
    /** Names the user has given characters, by their ids. */
    val mascotNames: Map<String, String> = emptyMap(),
    /**
     * The instructions each of the other characters had when it was last the AI ([chatPrompt]'s, for it), by their ids:
     * switched back to, it has them again.
     */
    val mascotPrompts: Map<String, String> = emptyMap(),
    /** The AI button at the top shows the character's face; otherwise (as it starts) the plain AI icon. */
    val mascotButton: Boolean = false,
    /** The model is opened as KoLib opens, and reads ahead what's showing, so the first answer comes quicker. */
    val preload: Boolean = false,
    /** Instructions kept under names, to switch between. */
    val presets: List<PromptPreset> = emptyList(),
) {
    /** The preset in use: the one whose instructions the chat has now, if it has any of them. */
    val preset: PromptPreset? get() = chatPrompt.takeIf { it.isNotEmpty() }?.let { now -> presets.firstOrNull { it.text == now } }

    /** With [text] kept as the preset called [name] (in place of one already called that), and in use. */
    fun withPreset(name: String, text: String): AiSettings {
        val kept = PromptPreset(name.trim(), text)
        val i = presets.indexOfFirst { it.name.equals(kept.name, ignoreCase = true) }
        return copy(presets = if (i >= 0) presets.toMutableList().also { it[i] = kept } else presets + kept, chatPrompt = text)
    }

    /** Without the preset called [name]. */
    fun withoutPreset(name: String): AiSettings = copy(presets = presets.filterNot { it.name == name })

    /** With [preset]'s instructions in use; null for the character's own. */
    fun using(preset: PromptPreset?): AiSettings = copy(chatPrompt = preset?.text.orEmpty())

    /** The character [id] is called: what the user called it, or [default]. */
    fun nameOf(id: String, default: String): String = mascotNames[id]?.trim()?.takeIf { it.isNotEmpty() } ?: default

    /** With the character [id] called [name] (blank: its own name again). */
    fun naming(id: String, name: String): AiSettings =
        copy(mascotNames = if (name.isBlank()) mascotNames - id else mascotNames + (id to name.trim().take(30)))

    /** The instructions the character [id] has: [chatPrompt] for the one that's the AI now. Empty: its own. */
    fun instructionsOf(id: String): String = if (id == mascot) chatPrompt else mascotPrompts[id].orEmpty()

    /** With the character [id] as the AI, and the instructions it had last time; this one's kept for it. */
    fun switchedTo(id: String): AiSettings = if (id == mascot) this else copy(
        mascotPrompts = mascotPrompts + (mascot to chatPrompt) - id,
        chatPrompt = mascotPrompts[id].orEmpty(),
        mascot = id,
    )

    /** Of the characters [ids], those using [preset] now. */
    fun usersOf(preset: PromptPreset, ids: List<String>): List<String> = ids.filter { instructionsOf(it) == preset.text }

    /** Without the character [id] (deleted): what it had is let go of. */
    fun without(id: String): AiSettings = copy(mascotNames = mascotNames - id, mascotPrompts = mascotPrompts - id)

    val modelId: String? get() = source.removePrefix("model:").takeIf { source.startsWith("model:") }
    val ownName: String? get() = source.removePrefix("own:").takeIf { source.startsWith("own:") }
    val usesServer: Boolean get() = source == "server"
}

class AiSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AiSettings> = state

    private fun read() = AiSettings(
        source = prefs.getString("source", "").orEmpty(),
        serverAddress = prefs.getString("server_address", "").orEmpty(),
        serverKey = prefs.getString("server_key", "").orEmpty(),
        serverModel = prefs.getString("server_model", "").orEmpty(),
        dictionary = prefs.getBoolean("dictionary", true),
        chatPrompt = prefs.getString("chat_prompt", "").orEmpty(),
        notesOnCharge = prefs.getBoolean("notes_on_charge", false),
        mascot = prefs.getString("mascot", "robot").orEmpty(),
        mascotButton = prefs.getBoolean("mascot_button", false),
        preload = prefs.getBoolean("preload", false),
        mascotNames = readMap(prefs.getString("mascot_names", null)),
        mascotPrompts = readMap(prefs.getString("mascot_prompts", null)),
        presets = runCatching {
            val all = org.json.JSONArray(prefs.getString("prompt_presets", "[]"))
            (0 until all.length()).map { i -> all.getJSONObject(i).let { PromptPreset(it.getString("name"), it.getString("text")) } }
        }.getOrDefault(emptyList()),
    )

    fun set(s: AiSettings) {
        prefs.edit()
            .putString("source", s.source)
            .putString("server_address", s.serverAddress)
            .putString("server_key", s.serverKey)
            .putString("server_model", s.serverModel)
            .putBoolean("dictionary", s.dictionary)
            .putString("chat_prompt", s.chatPrompt)
            .putBoolean("notes_on_charge", s.notesOnCharge)
            .putString("mascot", s.mascot)
            .putBoolean("mascot_button", s.mascotButton)
            .putBoolean("preload", s.preload)
            .putString("prompt_presets", org.json.JSONArray().apply { s.presets.forEach { put(org.json.JSONObject().put("name", it.name).put("text", it.text)) } }.toString())
            .putString("mascot_names", org.json.JSONObject(s.mascotNames).toString())
            .putString("mascot_prompts", org.json.JSONObject(s.mascotPrompts).toString())
            .apply()
        state.value = s
    }
}

private fun readMap(json: String?): Map<String, String> = runCatching {
    val o = org.json.JSONObject(json ?: "{}")
    o.keys().asSequence().associateWith { o.getString(it) }
}.getOrDefault(emptyMap())
