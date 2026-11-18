package dev.kolib.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelsTest {
    private val gb = 1_000_000_000L
    /** What a device sold with so many GB reports: a little less. */
    private fun sold(n: Int) = (n * gb * 0.92).toLong()

    @Test
    fun eachDeviceIsSuggestedTheBestModelItRunsComfortably() {
        // An old phone: the small one, whatever its processor.
        assertEquals("qwen3.5-0.8b", AiModels.bestFor(sold(4), fast = false).id)
        assertEquals("qwen3.5-0.8b", AiModels.bestFor(sold(4), fast = true).id)
        // A mid-range phone of the last few years (a Galaxy A72).
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(6), fast = false).id)
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(8), fast = false).id)
        // A fast tablet with 8 GB (a Galaxy Tab S9): the biggest would be tight, so the one below it.
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(8), fast = true).id)
        // The same with 12 GB: the best there is.
        assertEquals("gemma-4-e4b", AiModels.bestFor(sold(12), fast = true).id)
        // Plenty of memory but a slow processor: the biggest would crawl.
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(12), fast = false).id)
    }

    @Test
    fun aModelFitsWhereTheresTheMemoryForIt() {
        val small = AiModels.all.first()
        val best = AiModels.all.last()
        assertTrue(AiModels.fits(small, sold(4)))
        assertFalse(AiModels.fits(best, sold(6)))
        assertTrue(AiModels.fits(best, sold(8)))
    }

    @Test
    fun theModelsAreOnesAnyoneMayDownloadAndUse() {
        assertEquals(3, AiModels.all.size)
        for (m in AiModels.all) {
            assertTrue(m.url.startsWith("https://huggingface.co/") && m.url.endsWith(".gguf"))
            assertEquals(64, m.sha256.length)
            assertEquals("Apache 2.0", m.licence)
            assertTrue(m.bytes > 100_000_000L)
            assertTrue(m.description.isNotBlank())
        }
        assertEquals(AiModels.all.map { it.bytes }.sorted(), AiModels.all.map { it.bytes })
        assertEquals(AiModels.all.size, AiModels.all.map { it.id }.distinct().size)
    }

    @Test
    fun aModelRunsOnTheFastCoresOnly() {
        // A Galaxy Tab S9's processor: one very fast core, four fast, three slow.
        assertEquals(4, Device.threads(listOf(3360000L, 2800000, 2800000, 2800000, 2800000, 2000000, 2000000, 2000000), 8))
        // A Galaxy A72's: two fast, six slow.
        assertEquals(2, Device.threads(listOf(2300000L, 2300000, 1800000, 1800000, 1800000, 1800000, 1800000, 1800000), 8))
        // Where the system doesn't say: all but two, four at most.
        assertEquals(4, Device.threads(emptyList(), 8))
        assertEquals(2, Device.threads(emptyList(), 2))
    }

    @Test
    fun aRecentProcessorIsToldFromAnOlderOne() {
        assertTrue(Device.fast("processor\t: 0\nFeatures\t: fp asimd aes sha2 asimddp sve2 i8mm bf16\n"))
        assertFalse(Device.fast("processor\t: 0\nFeatures\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp asimddp\n"))
        assertFalse(Device.fast(""))
    }

    @Test
    fun aModelsThinkingOutLoudIsLeftOutOfItsAnswer() {
        assertEquals("The answer.", cleanAnswer("<think>Let me see… the house.</think>\nThe answer."))
        assertEquals("Start.", cleanAnswer("Start. <think>unfinished"))
        assertEquals("Plain.", cleanAnswer("  Plain.  "))
    }

    @Test
    fun aLookUpsAnswerIsReadInItsTwoParts() {
        assertEquals("The house loomed over the valley." to "verb, to think deeply", Prompts.readLookUp("Here: The house loomed over the valley.\nUsually: verb, to think deeply"))
        // Still being written: what there is of it.
        assertEquals("The house" to "", Prompts.readLookUp("Here: The house"))
        // A model that didn't keep to the form: its words as they are.
        assertEquals("It means to loom." to "", Prompts.readLookUp("It means to loom."))
        assertEquals("French", Prompts.languageName("fr"))
        assertEquals("Japanese", Prompts.languageName("ja-JP"))
        assertEquals("English", Prompts.languageName(null))
    }
}
