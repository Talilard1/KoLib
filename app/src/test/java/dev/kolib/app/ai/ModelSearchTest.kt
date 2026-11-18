package dev.kolib.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Finding models on Hugging Face: what a search and a model's list of files answer, as that site answers them. */
@RunWith(RobolectricTestRunner::class)
class ModelSearchTest {
    private val gb = 1_000_000_000L
    private val eight = (7.4 * gb).toLong()

    private val found = """[
      {"_id":"1","id":"unsloth/Qwen3.5-4B-GGUF","downloads":1633531,"gated":false,"pipeline_tag":"image-text-to-text","tags":["gguf","qwen","license:apache-2.0","conversational"]},
      {"_id":"2","id":"handy-computer/Qwen3-ASR-1.7B-gguf","downloads":2268924,"gated":false,"pipeline_tag":"automatic-speech-recognition","tags":["gguf","license:apache-2.0"]},
      {"_id":"3","id":"chatrlabs/Some-Image-Model","downloads":224306,"gated":false,"tags":["gguf","text-to-image","diffusers"]},
      {"_id":"4","id":"someone/Asked-For-GGUF","downloads":90,"gated":"auto","pipeline_tag":"text-generation","tags":["gguf","license:other"]},
      {"_id":"5","id":"HauhauCS/Qwen3.5-4B-Uncensored-HauhauCS-Aggressive","downloads":353187,"gated":false,"tags":["gguf","uncensored"]}
    ]"""

    private val files = """[
      {"type":"directory","path":"MTP","size":0},
      {"type":"file","path":".gitattributes","size":1968},
      {"type":"file","path":"Model-4B-BF16.gguf","size":135,"lfs":{"oid":"${"a".repeat(64)}","size":8424393056}},
      {"type":"file","path":"Model-4B-Q4_K_M.gguf","size":135,"lfs":{"oid":"${"b".repeat(64)}","size":2707513696}},
      {"type":"file","path":"Model-4B-Q6_K.gguf","size":135,"lfs":{"oid":"${"c".repeat(64)}","size":3464055136}},
      {"type":"file","path":"Model-4B-Q8_0.gguf","size":135,"lfs":{"oid":"${"d".repeat(64)}","size":4482402656}},
      {"type":"file","path":"Model-4B-UD-Q2_K_XL.gguf","size":135,"lfs":{"oid":"${"e".repeat(64)}","size":1600000000}},
      {"type":"file","path":"mmproj-Model-4B-BF16.gguf","size":135,"lfs":{"oid":"${"f".repeat(64)}","size":675568768}},
      {"type":"file","path":"Model-4B.mmproj-Q8_0.gguf","size":135,"lfs":{"oid":"${"9".repeat(64)}","size":620000000}},
      {"type":"file","path":"mtp-model-4b.gguf","size":135,"lfs":{"oid":"${"1".repeat(64)}","size":465109248}},
      {"type":"file","path":"imatrix_unsloth.gguf_file","size":7480640},
      {"type":"file","path":"big/Model-4B-Q4_K_M-00001-of-00002.gguf","size":135,"lfs":{"oid":"${"2".repeat(64)}","size":2000000000}},
      {"type":"file","path":"README.md","size":2921}
    ]"""

    @Test
    fun aSearchListsChatModelsAndLeavesOutWhatIsnt() {
        val models = ModelSearch.parseSearch(found)
        // The speech model and the picture model aren't chat models.
        assertEquals(listOf("unsloth/Qwen3.5-4B-GGUF", "someone/Asked-For-GGUF", "HauhauCS/Qwen3.5-4B-Uncensored-HauhauCS-Aggressive"), models.map { it.repo })
        val first = models.first()
        assertEquals("unsloth" to "Qwen3.5-4B-GGUF", first.maker to first.name)
        assertEquals(1633531L, first.downloads)
        assertEquals("apache-2.0", first.licence)
        assertFalse(first.gated)
        // One that has to be asked for is listed, and marked.
        assertTrue(models[1].gated)
        assertNull(models[2].licence)
    }

    @Test
    fun aSearchIsForChatModelsInGgufFilesTheMostDownloadedFirst() {
        val all = ModelSearch.searchUrl("")
        assertTrue(all.startsWith("https://huggingface.co/api/models?filter=gguf&filter=conversational&sort=downloads&direction=-1&limit=40"))
        val some = ModelSearch.searchUrl(" dark romance ", limit = 150)
        assertTrue(some.contains("&search=dark+romance&") && some.contains("&limit=150&"))
        assertFalse(some.contains("conversational"))
        // Hugging Face's count of a model's numbers isn't asked for: it isn't kept for GGUF files.
        assertFalse(some.contains("num_parameters"))
    }

    @Test
    fun aModelsSizeIsReadFromItsName() {
        val sizes = mapOf(
            "unsloth/Qwen3.5-9B-GGUF" to 9.0,
            "unsloth/Qwen3.5-0.8B-GGUF" to 0.8,
            "HauhauCS/Gemma4-12B-QAT-Uncensored-HauhauCS-Balanced" to 12.0,
            "unsloth/gemma-4-E4B-it-GGUF" to 8.0,
            "HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive" to 4.0,
            "HauhauCS/Qwen3.6-35B-A3B-Uncensored-HauhauCS-Aggressive" to 35.0,
            "AtomicChat/LFM2.5-24B-A2B-Uncensored-GGUF" to 24.0,
            "LiquidAI/LFM2.5-350M-GGUF" to 0.35,
            "TheBloke/Mixtral-8x7B-Instruct-GGUF" to 56.0,
            "mradermacher/smut_llama_8b_smut_2k_romance_1k_merged-GGUF" to 8.0,
            "Novaciano/SEX_ROLEPLAY-3.2-1B" to 1.0,
            "bartowski/Qwen3.5-4B-Qwen3.8-27B-Draft-GGUF" to 4.0,
            "mradermacher/Ornith-1.5-9B-uncensored-GGUF" to 9.0,
        )
        for ((repo, b) in sizes) assertEquals(repo, b, ModelSearch.billionsByName(repo)!!, 0.001)
        assertNull(ModelSearch.billionsByName("unsloth/Qwen3.8-Flash-Next-GGUF"))
        assertNull(ModelSearch.billionsByName("L4ndbo/L4DK-UNCENSORED-Q4_0-GGUF"))
        // On 8 GB: up to the four-billion and E4B models (whose smaller files fit); not nine billion and up.
        assertTrue(ModelSearch.mayFit("unsloth/Qwen3.5-4B-GGUF", eight))
        assertTrue(ModelSearch.mayFit("unsloth/gemma-4-E4B-it-GGUF", eight))
        assertFalse(ModelSearch.mayFit("unsloth/Qwen3.5-9B-GGUF", eight))
        assertFalse(ModelSearch.mayFit("HauhauCS/Qwen3.6-35B-A3B-Uncensored-HauhauCS-Aggressive", eight))
        // On 12 GB: nine billion too, and twelve (a tight fit); not twenty-four.
        val twelve = (11.2 * gb).toLong()
        assertTrue(ModelSearch.mayFit("unsloth/Qwen3.5-9B-GGUF", twelve))
        assertTrue(ModelSearch.mayFit("HauhauCS/Gemma4-12B-QAT-Uncensored-HauhauCS-Balanced", twelve))
        assertFalse(ModelSearch.mayFit("AtomicChat/LFM2.5-24B-A2B-Uncensored-GGUF", twelve))
        // A name that doesn't say: its files are looked at.
        assertTrue(ModelSearch.mayFit("unsloth/Qwen3.8-Flash-Next-GGUF", eight))
    }

    @Test
    fun aModelsFilesAreItsSizesWithoutThePartsThatArentModels() {
        val list = ModelSearch.parseFiles("maker/Model-4B-GGUF", files)
        // Smallest first; no picture part, no guessing part, no pieces of a split file, nothing that isn't a GGUF file.
        assertEquals(listOf("UD-Q2_K_XL", "Q4_K_M", "Q6_K", "Q8_0", "BF16"), list.map { it.quant })
        val q4 = list[1]
        assertEquals(2707513696L, q4.bytes)
        assertEquals("b".repeat(64), q4.sha256)
        assertEquals("https://huggingface.co/maker/Model-4B-GGUF/resolve/main/Model-4B-Q4_K_M.gguf", ModelSearch.downloadUrl(q4))
        assertEquals("https://huggingface.co/m/r/resolve/main/sub%20dir/A%20B.gguf", ModelSearch.downloadUrl(FoundFile("m/r", "sub dir/A B.gguf", 1, "x", "")))
    }

    @Test
    fun aFileSuitsADeviceByItsSizeAgainstTheMemory() {
        // On 8 GB: the usual size of a four-billion model is comfortable, a bigger one (3.5 GB) a tight fit, 4.5 GB too big.
        assertEquals(Fit.GOOD, ModelSearch.fit(2_707_513_696, eight))
        assertEquals(Fit.TIGHT, ModelSearch.fit(3_464_055_136, eight))
        assertEquals(Fit.TOO_BIG, ModelSearch.fit(4_482_402_656, eight))
        assertEquals(Fit.TOO_BIG, ModelSearch.fit(8_424_393_056, eight))
        // The "E" models hold only part of their files in memory: KoLib's own E2B is comfortable on 6 GB, E4B on 8 and 12.
        val e2b = AiModels.all[1]
        val e4b = AiModels.all[2]
        assertEquals(Fit.GOOD, ModelSearch.fit(ModelSearch.held(e2b.bytes, "ggml-org/gemma-4-E2B-it-GGUF"), (5.5 * gb).toLong()))
        assertEquals(Fit.GOOD, ModelSearch.fit(ModelSearch.held(e4b.bytes, "ggml-org/gemma-4-E4B-it-GGUF"), eight))
        assertEquals(Fit.GOOD, ModelSearch.fit(ModelSearch.held(e4b.bytes, "ggml-org/gemma-4-E4B-it-GGUF"), (11.2 * gb).toLong()))
        // Held in memory: all of a model's file, but for those, part of it (going by the name of the file or the model).
        assertTrue(ModelSearch.held(e4b.bytes, "gemma-4-E4B-it-Q4_0") < e4b.bytes * 0.7)
        assertEquals(3_464_055_136L, ModelSearch.held(3_464_055_136, "Qwen3.5-4B-Uncensored-HauhauCS-Aggressive-Q6_K"))
        // The one offered first: the usual size where it's comfortable; on a small device, the biggest that is; else the smallest there is.
        val list = ModelSearch.parseFiles("maker/Model-4B-GGUF", files)
        assertEquals("Q4_K_M", ModelSearch.best(list, eight)!!.quant)
        assertEquals("UD-Q2_K_XL", ModelSearch.best(list, 4 * gb)!!.quant)
        assertEquals("UD-Q2_K_XL", ModelSearch.best(list, 1 * gb)!!.quant)
        assertNull(ModelSearch.best(emptyList(), eight))
    }

    @Test
    fun aFoundFileIsDownloadedAsOneOfTheUsersOwn() {
        val file = ModelSearch.parseFiles("maker/Model-4B-GGUF", files)[1]
        val model = ModelSearch.asModel(file, "apache-2.0")
        assertEquals("own-Model-4B-Q4_K_M", model.id)
        assertEquals("own-Model-4B-Q4_K_M.gguf", model.fileName)
        assertEquals("Model-4B-Q4_K_M", model.name)
        assertEquals("maker", model.maker)
        assertEquals(file.bytes, model.bytes)
        assertEquals(file.sha256, model.sha256)
        // Kept where the user's own models are, so it's listed with them and chosen like them.
        val store = ModelStore(java.nio.file.Files.createTempDirectory("models").toFile())
        assertTrue(store.isOwn(model))
        assertEquals(store.ownFile("Model-4B-Q4_K_M"), store.file(model))
        assertEquals("Model-4B-Q4_K_M", store.ownName(store.file(model)))
        assertFalse(store.isOwn(AiModels.all[0]))
    }

    @Test
    fun theSearchAsksHuggingFace() {
        val asked = ArrayList<String>()
        val before = ModelSearch.fetcher
        try {
            ModelSearch.fetcher = { url -> asked += url; if (url.contains("/tree/")) files else found }
            assertEquals(3, ModelSearch.search("qwen").size)
            assertEquals(5, ModelSearch.files("maker/Model-4B-GGUF").size)
            assertTrue(asked[0].contains("search=qwen") && asked[0].contains("limit=40"))
            // For what suits a device, more are asked for, and those too big by their names are left out.
            val big = found.replace("unsloth/Qwen3.5-4B-GGUF", "unsloth/Qwen3.5-27B-GGUF")
            ModelSearch.fetcher = { url -> asked += url; big }
            assertEquals(listOf("someone/Asked-For-GGUF", "HauhauCS/Qwen3.5-4B-Uncensored-HauhauCS-Aggressive"), ModelSearch.search("qwen", eight).map { it.repo })
            assertTrue(asked.last().contains("limit=150"))
            assertEquals("https://huggingface.co/api/models/maker/Model-4B-GGUF/tree/main?recursive=true", asked[1])
        } finally {
            ModelSearch.fetcher = before
        }
    }
}
