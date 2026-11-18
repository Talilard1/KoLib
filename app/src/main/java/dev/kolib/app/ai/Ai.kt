package dev.kolib.app.ai

/** One turn of a conversation with a language model. */
data class AiMessage(val role: String, val content: String) {
    companion object {
        fun system(text: String) = AiMessage("system", text)
        fun user(text: String) = AiMessage("user", text)
        fun assistant(text: String) = AiMessage("assistant", text)
    }
}

/** How a reply ended. */
enum class AiEnd {
    /** The model finished what it had to say. */
    DONE,
    /** It reached the most it was asked to write. */
    LENGTH,
    /** Stopped by the reader. */
    CANCELLED,
    /** What was asked is more than the model can hold. */
    TOO_LONG,
    /** Something went wrong; the engine's [AiEngine.lastError] may say what. */
    FAILED,
}

/** A language model that answers conversations: one on this device, or one on a server of the user's. */
interface AiEngine {
    /** How many tokens (pieces of words) the model can hold at once: the prompt and its answer together. */
    val contextTokens: Int

    /** How many tokens [text] is for this model (a close guess where the engine can't count). */
    fun countTokens(text: String): Int

    /**
     * Answers [messages], at most [maxTokens] long. Blocks until it's done: call it off the main thread.
     * [onProgress] hears how much of the prompt has been read (0 to 1), [onText] gets the answer as it's written.
     */
    fun generate(messages: List<AiMessage>, maxTokens: Int, temperature: Float, onProgress: (Float) -> Unit, onText: (String) -> Unit): AiEnd

    /** Stops the answer being written, from any thread. */
    fun stop()

    /** Lets go of the model. */
    fun close()

    /** What went wrong the last time, if anything. */
    val lastError: String?
}

/** Why a model couldn't be made ready. */
class AiException(message: String) : Exception(message)

/** Text a model wrote, without any thinking out loud it left in (some models do, between marks like these). */
fun cleanAnswer(text: String): String {
    var t = text
    for ((open, close) in listOf("<think>" to "</think>", "<|channel>thought" to "<channel|>", "<thinking>" to "</thinking>")) {
        while (true) {
            val a = t.indexOf(open)
            if (a < 0) break
            val b = t.indexOf(close, a + open.length)
            t = if (b < 0) t.substring(0, a) else t.substring(0, a) + t.substring(b + close.length)
        }
    }
    return t.trim()
}
