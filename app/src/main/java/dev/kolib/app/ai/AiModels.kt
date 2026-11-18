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
    /** How good its answers are, against the others KoLib offers: the higher, the better (and the slower). */
    val quality: Int = 0,
    private val about: () -> String,
) {
    val fileName: String get() = "$id.gguf"
    val description: String get() = about()

    /** How much of it is held in memory as it answers. */
    val held: Long get() = ModelSearch.held(bytes, name)
}

/**
 * The models KoLib offers, the simplest first: open models (Apache 2.0) in llama.cpp's GGUF format, downloaded
 * from Hugging Face when the user asks for one. Each is a "Q4" build, the usual trade of size for quality. Three
 * of them are offered on a device: the ones that suit it best ([picks]).
 */
object AiModels {
    val all: List<AiModel> = listOf(
        AiModel(
            id = "qwen3.5-0.8b", name = "Qwen3.5 0.8B", maker = "Alibaba",
            url = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
            bytes = 532_517_120L, sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
            licence = "Apache 2.0", quality = 10,
        ) { tr("Small and quick, with rougher answers. For older phones.") },
        AiModel(
            id = "qwen3.5-2b", name = "Qwen3.5 2B", maker = "Alibaba",
            url = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
            bytes = 1_280_835_840L, sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
            licence = "Apache 2.0", quality = 25,
        ) { tr("Quick, with simple but sound answers.") },
        AiModel(
            id = "gemma-4-e2b", name = "Gemma 4 E2B", maker = "Google",
            url = "https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_0.gguf",
            bytes = 2_841_481_184L, sha256 = "8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52",
            licence = "Apache 2.0", quality = 35,
        ) { tr("Good answers at a good speed. For most phones and tablets of the last few years.") },
        AiModel(
            id = "qwen3.5-4b", name = "Qwen3.5 4B", maker = "Alibaba",
            url = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            bytes = 2_740_937_888L, sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
            licence = "Apache 2.0", quality = 50,
        ) { tr("Thoughtful answers at a fair speed.") },
        AiModel(
            id = "gemma-4-e4b", name = "Gemma 4 E4B", maker = "Google",
            url = "https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_0.gguf",
            bytes = 4_590_807_392L, sha256 = "a555b900214b477d8880e7832e0b8925e139b0159640036b09fe472b6f2097f2",
            licence = "Apache 2.0", quality = 55,
        ) { tr("Very good answers, a little slower.") },
        AiModel(
            id = "qwen3.5-9b", name = "Qwen3.5 9B", maker = "Alibaba",
            url = "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf",
            bytes = 5_680_522_464L, sha256 = "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8",
            licence = "Apache 2.0", quality = 80,
        ) { tr("The best answers here, and the slowest. For fast devices with plenty of memory.") },
    )

    fun byId(id: String?): AiModel? = all.firstOrNull { it.id == id }

    /**
     * The most a model may hold in memory and still write at a reading pace (five or so words a second) on a
     * processor of [speed]: each word it writes means reading through all of it.
     */
    fun quickUpTo(speed: Device.Speed): Long = when (speed) {
        Device.Speed.FAST -> 7_000_000_000L
        Device.Speed.MID -> 2_400_000_000L
        Device.Speed.SLOW -> 1_200_000_000L
    }

    /** Whether [model] runs comfortably on a device with [memory] bytes of memory and a processor of [speed]. */
    fun comfortable(model: AiModel, memory: Long, speed: Device.Speed): Boolean =
        ModelSearch.fit(model.held, memory) == Fit.GOOD && model.held <= quickUpTo(speed)

    /** The model to suggest: the best one the device runs comfortably (or the smallest, where it runs none). */
    fun bestFor(memory: Long, speed: Device.Speed): AiModel =
        all.filter { comfortable(it, memory, speed) }.maxByOrNull { it.quality } ?: all.first()

    /**
     * The [count] models to offer on a device with [memory] bytes of memory and a processor of [speed], the best
     * first: the one suggested and the next best below it; and above it, where there's one that runs at all
     * (slowly, or with little memory to spare), the best of those. Where that leaves room, the others that run.
     */
    fun picks(memory: Long, speed: Device.Speed, count: Int = 3): List<AiModel> {
        val best = bestFor(memory, speed)
        val running = all.filter { ModelSearch.fit(it.held, memory) != Fit.TOO_BIG }
        val above = running.filter { it.quality > best.quality }.maxByOrNull { it.quality }
        val below = running.filter { it.quality < best.quality }.sortedByDescending { it.quality }
        val others = running.filter { it.quality > best.quality && it != above }.sortedByDescending { it.quality }
        return (listOfNotNull(above, best) + below + others).take(count).sortedByDescending { it.quality }
    }

    /** How much memory a device was sold with ("8 GB"), from the little less that it reports. */
    fun soldGb(memory: Long): Int = kotlin.math.ceil(memory / 1e9 / 0.97).toInt().coerceAtLeast(1)
}
