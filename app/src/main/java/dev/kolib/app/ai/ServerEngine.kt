package dev.kolib.app.ai

import dev.kolib.app.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A model on a server of the user's own, spoken to the way most of them understand (the "OpenAI-compatible"
 * chat API): Ollama, LM Studio or llama.cpp on a computer at home, or a service the user has a key for.
 */
class ServerEngine(private val address: String, private val key: String, private val model: String, override val contextTokens: Int = 8192) : AiEngine {

    @Volatile
    private var connection: HttpURLConnection? = null

    @Volatile
    private var stopped = false

    override var lastError: String? = null
        private set

    override fun countTokens(text: String): Int = Tokens.guess(text)

    override fun generate(messages: List<AiMessage>, maxTokens: Int, temperature: Float, onProgress: (Float) -> Unit, onText: (String) -> Unit): AiEnd {
        stopped = false
        lastError = null
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray(messages.map { JSONObject().put("role", it.role).put("content", it.content) }))
            .put("max_tokens", maxTokens)
            .put("temperature", temperature.toDouble())
            .put("stream", true)
        var c: HttpURLConnection? = null
        try {
            c = (URL(chatUrl(address)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer ${key.trim()}")
            }
            connection = c
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val said = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                lastError = errorIn(said) ?: tr("The server answered with error {0}.", code)
                return AiEnd.FAILED
            }
            onProgress(1f)
            var end = AiEnd.DONE
            var thinking = false
            c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (stopped) return AiEnd.CANCELLED
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") break
                    val choice = runCatching { JSONObject(data).optJSONArray("choices")?.optJSONObject(0) }.getOrNull() ?: continue
                    val piece = choice.optJSONObject("delta")?.takeIf { !it.isNull("content") }?.optString("content").orEmpty()
                    if (piece.isNotEmpty()) {
                        // Thinking out loud some models send along with the answer: not the answer.
                        if (piece.contains("<think>")) thinking = true
                        if (!thinking) onText(piece)
                        if (piece.contains("</think>")) thinking = false
                    }
                    if (choice.optString("finish_reason") == "length") end = AiEnd.LENGTH
                }
            }
            return end
        } catch (e: IOException) {
            if (stopped) return AiEnd.CANCELLED
            lastError = tr("Couldn't reach the server: {0}", e.message ?: e.javaClass.simpleName)
            return AiEnd.FAILED
        } catch (e: Exception) {
            lastError = e.message
            return AiEnd.FAILED
        } finally {
            connection = null
            runCatching { c?.disconnect() }
        }
    }

    override fun stop() {
        stopped = true
        val c = connection
        if (c != null) Thread { runCatching { c.disconnect() } }.start()
    }

    override fun close() = stop()

    companion object {
        /** Where chats are sent on a server at [address]: "http://192.168.1.20:11434" and "https://host/v1" both work. */
        fun chatUrl(address: String): String {
            var a = address.trim().trimEnd('/')
            if (!a.contains("://")) a = "http://$a"
            if (a.endsWith("/chat/completions")) return a
            if (!Regex("/v\\d+$").containsMatchIn(a)) a += "/v1"
            return "$a/chat/completions"
        }

        /** The models a server at [address] offers, by name; empty if it doesn't say. */
        fun models(address: String, key: String): List<String> {
            val url = chatUrl(address).removeSuffix("/chat/completions") + "/models"
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer ${key.trim()}")
            }
            try {
                if (c.responseCode !in 200..299) throw IOException(tr("The server answered with error {0}.", c.responseCode))
                val data = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("data") ?: return emptyList()
                return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            } finally {
                c.disconnect()
            }
        }

        private fun errorIn(body: String): String? = runCatching {
            val o = JSONObject(body)
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: o.optString("error").takeIf { it.isNotBlank() && !it.startsWith("{") }
        }.getOrNull()
    }
}
