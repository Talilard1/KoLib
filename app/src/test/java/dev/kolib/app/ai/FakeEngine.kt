package dev.kolib.app.ai

/** A model for tests: it answers with what [reply] makes of the conversation, a word at a time, and remembers what it was asked. */
class FakeEngine(
    override val contextTokens: Int = 4096,
    private val reply: (List<AiMessage>) -> String = { "Here: it means what it says.\nUsually: noun, a thing" },
) : AiEngine {
    val asked = ArrayList<List<AiMessage>>()
    @Volatile var stopped = false
    @Volatile var closed = false
    /** Stops by itself after this many pieces of an answer, as if the reader had stopped it. */
    var stopAfter = Int.MAX_VALUE
    var fail: String? = null
    override var lastError: String? = null

    /** A token a word, near enough. */
    override fun countTokens(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

    override fun generate(messages: List<AiMessage>, maxTokens: Int, temperature: Float, onProgress: (Float) -> Unit, onText: (String) -> Unit): AiEnd {
        stopped = false
        asked += messages
        if (messages.sumOf { countTokens(it.content) } > contextTokens) return AiEnd.TOO_LONG
        fail?.let {
            lastError = it
            return AiEnd.FAILED
        }
        onProgress(0.5f)
        onProgress(1f)
        var said = 0
        for (piece in reply(messages).split(Regex("(?<= )"))) {
            if (stopped || said >= stopAfter) return AiEnd.CANCELLED
            if (said >= maxTokens) return AiEnd.LENGTH
            onText(piece)
            said++
        }
        return AiEnd.DONE
    }

    override fun stop() {
        stopped = true
    }

    override fun close() {
        closed = true
    }
}
