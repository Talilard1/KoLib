package dev.kolib.app.ai

import dev.kolib.app.tr

/** A model KoLib can download and run on the device. */
data class AiModel(
    val id: String,
    val name: String,
    val maker: String,
    /** Where its file is downloaded from. */
    val url: String,
    val bytes: Long,
    val sha256: String,
    val licence: String,
    /** The least memory a device needs to run it comfortably, in GB as devices are sold (4, 6, 8...). */
    val memoryGb: Int,
    /** It wants a recent, fast processor to answer in good time. */
    val wantsFast: Boolean,
    private val about: () -> String,
) {
    val fileName: String get() = "$id.gguf"
    val description: String get() = about()
}

/**
 * The models KoLib offers, smallest first: open models (Apache 2.0) in llama.cpp's GGUF format, downloaded
 * from Hugging Face when the user asks for one. Each is a "Q4" build, the usual trade of size for quality.
 */
object AiModels {
    val all: List<AiModel> = listOf(
        AiModel(
            id = "qwen3.5-0.8b", name = "Qwen3.5 0.8B", maker = "Alibaba",
            url = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
            bytes = 532_517_120L, sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
            licence = "Apache 2.0", memoryGb = 4, wantsFast = false,
        ) { tr("Small and quick, with rougher answers. For older phones.") },
        AiModel(
            id = "gemma-4-e2b", name = "Gemma 4 E2B", maker = "Google",
            url = "https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_0.gguf",
            bytes = 2_841_481_184L, sha256 = "8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52",
            licence = "Apache 2.0", memoryGb = 6, wantsFast = false,
        ) { tr("Good answers at a good speed. For most phones and tablets of the last few years.") },
        AiModel(
            id = "gemma-4-e4b", name = "Gemma 4 E4B", maker = "Google",
            url = "https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_0.gguf",
            bytes = 4_590_807_392L, sha256 = "a555b900214b477d8880e7832e0b8925e139b0159640036b09fe472b6f2097f2",
            licence = "Apache 2.0", memoryGb = 8, wantsFast = true,
        ) { tr("The best answers here, and the slowest. For fast devices with plenty of memory: 12 GB is comfortable, 8 GB can be tight.") },
    )

    fun byId(id: String?): AiModel? = all.firstOrNull { it.id == id }

    private const val GB = 1_000_000_000L

    /** A device sold with [gb] GB of memory reports a little less than that. */
    private fun has(memory: Long, gb: Int) = memory >= gb * GB * 0.86

    /** Whether a device with [memory] bytes of memory can be expected to run [model] at all. */
    fun fits(model: AiModel, memory: Long): Boolean = has(memory, model.memoryGb)

    /**
     * The model to suggest on a device with [memory] bytes of memory and a [fast] processor or not: the best
     * one it runs comfortably. The biggest only where there's memory to spare (12 GB) and the speed for it;
     * it's still offered on a device with 8.
     */
    fun bestFor(memory: Long, fast: Boolean): AiModel = when {
        fast && has(memory, 12) -> all[2]
        has(memory, 6) -> all[1]
        else -> all[0]
    }
}
