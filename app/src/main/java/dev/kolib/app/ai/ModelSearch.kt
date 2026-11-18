package dev.kolib.app.ai

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A model found on Hugging Face: [repo] ("maker/name"), how often it's been downloaded, its licence as its
 * page gives it, and whether it has to be asked for there first ([gated]: those can't be downloaded here).
 */
data class FoundModel(val repo: String, val downloads: Long, val licence: String?, val gated: Boolean) {
    val maker: String get() = repo.substringBefore('/')
    val name: String get() = repo.substringAfter('/')
}

/** One of a found model's files: the model at one size ([quant], as its name says: "Q4_K_M"), to download. */
data class FoundFile(val repo: String, val path: String, val bytes: Long, val sha256: String, val quant: String) {
    val fileName: String get() = path.substringAfterLast('/')
}

/** How a model file's size sits with the device's memory. */
enum class Fit { GOOD, TIGHT, TOO_BIG }

/**
 * Finding models on Hugging Face: any chat model there in a GGUF file can be downloaded, whoever made it.
 * What's found is the makers' own, not looked over by KoLib.
 */
object ModelSearch {
    private const val HUB = "https://huggingface.co"
    private const val FIELDS = "expand[]=downloads&expand[]=gated&expand[]=tags&expand[]=pipeline_tag"

    /** What a model's page says it's for, when that's something other than answering in words. */
    private val notChat = setOf(
        "text-to-image", "image-to-image", "text-to-video", "image-to-video", "text-to-speech", "text-to-audio", "automatic-speech-recognition",
        "feature-extraction", "sentence-similarity", "text-ranking", "image-classification", "object-detection", "audio-classification",
        "image-feature-extraction", "fill-mask", "token-classification", "translation", "unconditional-image-generation", "image-to-3d", "text-to-3d",
    )

    /**
     * A file this big runs comfortably in [memory] bytes of memory, runs with little to spare, or doesn't
     * fit: the whole file is held in memory, beside Android and whatever else is open.
     */
    fun fit(bytes: Long, memory: Long): Fit = when {
        bytes <= memory * 0.52 -> Fit.GOOD
        bytes <= memory * 0.65 -> Fit.TIGHT
        else -> Fit.TOO_BIG
    }

    /**
     * The biggest model worth looking for on a device with [memory] bytes of memory, as Hugging Face counts
     * them ("6400M": millions of parameters). At the usual size of file (four bits and a bit each), that
     * many still run comfortably.
     */
    fun mostParameters(memory: Long): String = "${(memory * 0.52 / 0.6 / 1_000_000).toLong().coerceAtLeast(300)}M"

    /**
     * The address that lists chat models in GGUF files, the most downloaded first: those whose name or maker
     * has [query] in it (all of them, with none), no bigger than [most] parameters when that's given.
     */
    fun searchUrl(query: String, most: String? = null, limit: Int = 40): String = buildString {
        append(HUB).append("/api/models?filter=gguf")
        val q = query.trim()
        if (q.isNotEmpty()) append("&search=").append(URLEncoder.encode(q, "UTF-8")) else append("&filter=conversational")
        if (most != null) append("&num_parameters=min:0,max:").append(most)
        append("&sort=downloads&direction=-1&limit=").append(limit).append('&').append(FIELDS)
    }

    /** What a search answered, without what plainly isn't a chat model (pictures, speech, embeddings). */
    fun parseSearch(json: String): List<FoundModel> {
        val all = JSONArray(json)
        return (0 until all.length()).mapNotNull { i ->
            val o = all.optJSONObject(i) ?: return@mapNotNull null
            val repo = o.optString("id").takeIf { it.contains('/') } ?: return@mapNotNull null
            if (o.optString("pipeline_tag") in notChat) return@mapNotNull null
            val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) } }.orEmpty()
            if (tags.any { it in notChat || it == "diffusers" }) return@mapNotNull null
            val gated = o.opt("gated").let { it != null && it != false && it.toString() != "false" && it.toString() != "null" }
            FoundModel(repo, o.optLong("downloads"), tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:"), gated)
        }
    }

    fun filesUrl(repo: String): String = "$HUB/api/models/$repo/tree/main?recursive=true"

    private val quantRe = Regex("(?i)(?:^|[-._])((?:UD-)?(?:IQ\\d|Q\\d)[A-Z0-9_]*|BF16|F16|F32)(?=\\.gguf$|[-._])")
    private val splitRe = Regex("(?i)-\\d{5}-of-\\d{5}\\.gguf$")

    /**
     * A model's files that can be run as they are: its GGUF files, smallest first, without the parts that go
     * with a model rather than being one (for pictures, for faster guessing), and without files split in pieces.
     */
    fun parseFiles(repo: String, json: String): List<FoundFile> {
        val all = JSONArray(json)
        return (0 until all.length()).mapNotNull { i ->
            val o = all.optJSONObject(i) ?: return@mapNotNull null
            val path = o.optString("path")
            val name = path.substringAfterLast('/')
            if (o.optString("type") != "file" || !name.endsWith(".gguf", ignoreCase = true)) return@mapNotNull null
            val low = name.lowercase()
            if (low.startsWith("mmproj") || low.contains("-mmproj") || low.startsWith("mtp-") || low.contains("imatrix") || splitRe.containsMatchIn(name)) return@mapNotNull null
            val lfs = o.optJSONObject("lfs") ?: return@mapNotNull null
            val bytes = lfs.optLong("size", o.optLong("size"))
            val sum = lfs.optString("oid").takeIf { it.length == 64 } ?: return@mapNotNull null
            if (bytes < 1_000_000) return@mapNotNull null
            FoundFile(repo, path, bytes, sum, quantRe.find(name)?.groupValues?.get(1)?.uppercase().orEmpty())
        }.sortedBy { it.bytes }
    }

    /**
     * The file of [files] to offer first on a device with [memory] bytes of memory: of those that run
     * comfortably, the usual size (four bits and a bit a number: smaller ones answer noticeably worse) if
     * it's there, else the biggest; with none that do, the smallest there is.
     */
    fun best(files: List<FoundFile>, memory: Long): FoundFile? {
        val good = files.filter { fit(it.bytes, memory) == Fit.GOOD }
        if (good.isEmpty()) return files.minByOrNull { it.bytes }
        return good.lastOrNull { it.quant == "Q4_K_M" }
            ?: good.lastOrNull { it.quant.startsWith("Q4") || it.quant.endsWith("Q4_K_XL") || it.quant.startsWith("IQ4") }
            ?: good.last()
    }

    fun downloadUrl(file: FoundFile): String =
        "$HUB/${file.repo}/resolve/main/" + file.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    /** [file] as a model to download and keep with the user's own, under its file's name. */
    fun asModel(file: FoundFile, licence: String?): AiModel = AiModel(
        id = ModelStore.ownId(file.fileName),
        name = file.fileName.removeSuffix(".gguf"),
        maker = file.repo.substringBefore('/'),
        url = downloadUrl(file),
        bytes = file.bytes,
        sha256 = file.sha256,
        licence = licence.orEmpty(),
        memoryGb = 0,
        wantsFast = false,
        about = { "" },
    )

    /** How the search reaches Hugging Face (tests put their own in). */
    var fetcher: (String) -> String = ::get

    fun search(query: String, most: String? = null): List<FoundModel> = parseSearch(fetcher(searchUrl(query, most)))

    fun files(repo: String): List<FoundFile> = parseFiles(repo, fetcher(filesUrl(repo)))

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "KoLib")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
