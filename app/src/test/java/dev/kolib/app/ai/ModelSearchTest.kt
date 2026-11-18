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
        val some = ModelSearch.searchUrl(" dark romance ", most = "6400M")
        assertTrue(some.contains("&search=dark+romance&") && some.contains("&num_parameters=min:0,max:6400M&"))
        assertFalse(some.contains("conversational"))
        // What's worth looking for on a device: with 8 GB, models of about six billion numbers; with 12, nine.
        assertEquals("6413M", ModelSearch.mostParameters(eight))
        assertEquals("9706M", ModelSearch.mostParameters((11.2 * gb).toLong()))
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
        // On 8 GB: the usual size of a four-billion model is comfortable, a bigger one is a tight fit, the biggest don't fit.
        assertEquals(Fit.GOOD, ModelSearch.fit(2_707_513_696, eight))
        assertEquals(Fit.GOOD, ModelSearch.fit(3_464_055_136, eight))
        assertEquals(Fit.TIGHT, ModelSearch.fit(4_482_402_656, eight))
        assertEquals(Fit.TOO_BIG, ModelSearch.fit(8_424_393_056, eight))
        // KoLib's own suggestions sit the same way: E2B is comfortable on 6 GB and 8, E4B a tight fit on 8 and comfortable on 12.
        val e2b = AiModels.all[1].bytes
        val e4b = AiModels.all[2].bytes
        assertEquals(Fit.GOOD, ModelSearch.fit(e2b, (5.5 * gb).toLong()))
        assertEquals(Fit.TIGHT, ModelSearch.fit(e4b, eight))
        assertEquals(Fit.GOOD, ModelSearch.fit(e4b, (11.2 * gb).toLong()))
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
            assertEquals(3, ModelSearch.search("qwen", "6413M").size)
            assertEquals(5, ModelSearch.files("maker/Model-4B-GGUF").size)
            assertTrue(asked[0].contains("search=qwen") && asked[0].contains("max:6413M"))
            assertEquals("https://huggingface.co/api/models/maker/Model-4B-GGUF/tree/main?recursive=true", asked[1])
        } finally {
            ModelSearch.fetcher = before
        }
    }
}
