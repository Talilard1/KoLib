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

    /** What was read ahead, each time. */
    val ahead = ArrayList<List<AiMessage>>()
    /** Reading ahead takes this long (ms), in steps a tenth as long, giving way when [yieldAhead] is called. */
    var aheadTakes = 0L
    @Volatile var yielded = false

    override fun prefill(messages: List<AiMessage>): AiEnd {
        yielded = false
        ahead += messages
        var waited = 0L
        while (waited < aheadTakes) {
            if (yielded) return AiEnd.CANCELLED
            Thread.sleep(aheadTakes / 10)
            waited += aheadTakes / 10
        }
        return AiEnd.DONE
    }

    override fun yieldAhead() {
        yielded = true
    }

    override fun close() {
        closed = true
    }
}
