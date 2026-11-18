package dev.kolib.app.ai

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class ServerEngineTest {
    private lateinit var server: ServerSocket
    private val bodies = ArrayList<JSONObject>()
    private val keys = ArrayList<String?>()
    private var answer: (JSONObject) -> Pair<Int, String> = { 200 to "" }

    private fun events(vararg pieces: String, finish: String = "stop") =
        pieces.joinToString("") { "data: " + JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", it)))) + "\n\n" } +
            "data: " + JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject()).put("finish_reason", finish))) + "\n\ndata: [DONE]\n\n"

    /** A little server: it reads one request at a time and answers as [answer] says. */
    @Before
    fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val input = s.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) {
                        val b = input.read()
                        if (b < 0) break
                        head.append(b.toChar())
                    }
                    val lines = head.toString().split("\r\n")
                    val path = lines.first().split(" ").getOrElse(1) { "" }
                    val headers = lines.drop(1).filter { it.contains(':') }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = ByteArray(length)
                    var got = 0
                    while (got < length) {
                        val n = input.read(body, got, length - got)
                        if (n < 0) break
                        got += n
                    }
                    val (code, text) = if (path.endsWith("/models")) 200 to """{"data":[{"id":"gemma3:4b"},{"id":"qwen3:8b"}]}""" else {
                        val json = JSONObject(String(body, Charsets.UTF_8))
                        bodies += json
                        keys += headers["authorization"]
                        answer(json)
                    }
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    s.getOutputStream().apply {
                        write("HTTP/1.1 $code X\r\nContent-Type: ${if (code == 200) "text/event-stream" else "application/json"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private val address get() = "http://127.0.0.1:${server.localPort}"

    @Test
    fun aServersAddressCanBeWrittenAnyOfTheUsualWays() {
        assertEquals("http://192.168.1.20:11434/v1/chat/completions", ServerEngine.chatUrl("192.168.1.20:11434"))
        assertEquals("http://192.168.1.20:11434/v1/chat/completions", ServerEngine.chatUrl(" http://192.168.1.20:11434/ "))
        assertEquals("https://api.example.com/v1/chat/completions", ServerEngine.chatUrl("https://api.example.com/v1"))
        assertEquals("https://host/api/v3/chat/completions", ServerEngine.chatUrl("https://host/api/v3"))
        assertEquals("https://host/v1/chat/completions", ServerEngine.chatUrl("https://host/v1/chat/completions"))
    }

    @Test
    fun anAnswerComesAsItsWritten() {
        answer = { 200 to events("The house ", "loomed ", "over the valley.") }
        val engine = ServerEngine(address, " secret ", "gemma3:4b")
        val pieces = ArrayList<String>()
        var read = 0f
        val end = engine.generate(listOf(AiMessage.system("Be brief."), AiMessage.user("What does brooded mean?")), 120, 0.2f, { read = it }) { pieces += it }
        assertEquals(AiEnd.DONE, end)
        assertEquals(listOf("The house ", "loomed ", "over the valley."), pieces)
        assertEquals(1f, read)
        val sent = bodies.single()
        assertEquals("gemma3:4b", sent.getString("model"))
        assertTrue(sent.getBoolean("stream"))
        assertEquals(120, sent.getInt("max_tokens"))
        assertEquals("system", sent.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("What does brooded mean?", sent.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals("Bearer secret", keys.single())
    }

    @Test
    fun anAnswerCutShortSaysSoAndThinkingIsLeftOut() {
        answer = { 200 to events("<think>hmm", " let me see</think>", "It means ", "to loom", finish = "length") }
        val engine = ServerEngine(address, "", "m")
        val said = StringBuilder()
        assertEquals(AiEnd.LENGTH, engine.generate(listOf(AiMessage.user("?")), 4, 0.2f, {}) { said.append(it) })
        assertEquals("It means to loom", said.toString())
        assertEquals(null, keys.single())
    }

    @Test
    fun aServersErrorIsPassedOn() {
        answer = { 404 to """{"error":{"message":"model 'nope' not found"}}""" }
        val engine = ServerEngine(address, "", "nope")
        assertEquals(AiEnd.FAILED, engine.generate(listOf(AiMessage.user("?")), 10, 0.2f, {}) {})
        assertEquals("model 'nope' not found", engine.lastError)
        // No server there at all.
        val nobody = ServerEngine("http://127.0.0.1:9", "", "m")
        assertEquals(AiEnd.FAILED, nobody.generate(listOf(AiMessage.user("?")), 10, 0.2f, {}) {})
        assertTrue(nobody.lastError!!.isNotBlank())
    }

    @Test
    fun aServerSaysWhichModelsItHas() {
        assertEquals(listOf("gemma3:4b", "qwen3:8b"), ServerEngine.models(address, ""))
    }
}
