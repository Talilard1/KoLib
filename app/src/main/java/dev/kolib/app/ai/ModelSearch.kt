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
     * A model that holds [bytes] in memory as it answers (see [held]) runs comfortably in [memory] bytes of memory,
     * runs with little to spare, or doesn't fit, beside Android and whatever else is open.
     */
    fun fit(bytes: Long, memory: Long): Fit = when {
        bytes <= memory * 0.42 -> Fit.GOOD
        bytes <= memory * 0.6 -> Fit.TIGHT
        else -> Fit.TOO_BIG
    }

    /**
     * How much of a model file of [bytes] is held in memory as it answers: all of it, but for the "E" models
     * ([name] like "gemma-4-E2B…"), a third or more of whose file is a table only read a row at a time.
     */
    fun held(bytes: Long, name: String): Long {
        val e = sizeRe.find(name.substringAfter('/'))?.groupValues?.get(2).orEmpty()
        return if (e.isNotEmpty()) (bytes * 0.65).toLong() else bytes
    }

    fun held(file: FoundFile): Long = held(file.bytes, file.repo)

    // How big a model is, as its name says: "Qwen3.5-9B" (9 billion numbers), "gemma-4-E4B" (4 billion in use at a
    // time, about twice that in all), "LFM2.5-350M", "Mixtral-8x7B". The first such size in the name is the model's
    // own ("…-35B-A3B": 35 billion, of which 3 are in use at a time; "Qwen3.5-4B-Qwen3.8-27B-Draft": 4).
    private val sizeRe = Regex("(?i)(?:^|[-_ .])(?:(\\d+)x)?(E)?(\\d+(?:\\.\\d+)?)([BM])(?=$|[-_ .])")

    /** How many billion numbers a model has, as its [repo]'s name gives it; null when the name doesn't say. */
    fun billionsByName(repo: String): Double? {
        val m = sizeRe.find(repo.substringAfter('/')) ?: return null
        val (times, effective, n, unit) = m.destructured
        var b = n.toDouble() * (if (unit.equals("M", ignoreCase = true)) 0.001 else 1.0)
        if (times.isNotEmpty()) b *= times.toDouble()
        if (effective.isNotEmpty()) b *= 2
        return b
    }

    /**
     * Whether a model might run on a device with [memory] bytes of memory, going by its name: a file of it
     * that still answers well (a little under four and a half bits a number) would at least fit. Names that
     * don't say are given the benefit of the doubt: their files are looked at.
     */
    fun mayFit(repo: String, memory: Long): Boolean {
        val b = billionsByName(repo) ?: return true
        return fit(held((b * 0.55e9).toLong(), repo), memory) != Fit.TOO_BIG
    }

    /**
     * The address that lists chat models in GGUF files, the most downloaded first: those whose name or maker
     * has [query] in it (all of them, with none).
     */
    fun searchUrl(query: String, limit: Int = 40): String = buildString {
        append(HUB).append("/api/models?filter=gguf")
        val q = query.trim()
        if (q.isNotEmpty()) append("&search=").append(URLEncoder.encode(q, "UTF-8")) else append("&filter=conversational")
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
            if (low.contains("mmproj") || low.startsWith("mtp-") || low.contains("imatrix") || splitRe.containsMatchIn(name)) return@mapNotNull null
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
        val good = files.filter { fit(held(it), memory) == Fit.GOOD }
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
        about = { "" },
    )

    /** How the search reaches Hugging Face (tests put their own in). */
    var fetcher: (String) -> String = ::get

    /**
     * The models found for [query]. With the device's [memory] given, only those that might run on it: more are
     * asked for, as many of the most downloaded are too big. (Hugging Face's own count of a model's numbers
     * can't be asked for here: it isn't kept for GGUF files, so asking for it leaves out nearly all of them.)
     */
    fun search(query: String, memory: Long? = null): List<FoundModel> {
        if (memory == null) return parseSearch(fetcher(searchUrl(query)))
        return parseSearch(fetcher(searchUrl(query, limit = 150))).filter { mayFit(it.repo, memory) }.take(40)
    }

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
