package dev.kolib.app.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which model answers, and the user's own server if they have one. */
data class AiSettings(
    /** "" for none; "model:<id>" for one of KoLib's; "own:<name>" for a file of the user's; "server". */
    val source: String = "",
    val serverAddress: String = "",
    val serverKey: String = "",
    val serverModel: String = "",
    /** The dictionary (Wiktionary) is asked when a word is looked up. */
    val dictionary: Boolean = true,
    /** The user's own instructions for the chat, in place of the usual ones ([Prompts.CHAT_DEFAULT]); empty for those. */
    val chatPrompt: String = "",
) {
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
    )

    fun set(s: AiSettings) {
        prefs.edit()
            .putString("source", s.source)
            .putString("server_address", s.serverAddress)
            .putString("server_key", s.serverKey)
            .putString("server_model", s.serverModel)
            .putBoolean("dictionary", s.dictionary)
            .putString("chat_prompt", s.chatPrompt)
            .apply()
        state.value = s
    }
}
