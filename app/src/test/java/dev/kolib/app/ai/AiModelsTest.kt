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
        val (slow, mid, fast) = Triple(Device.Speed.SLOW, Device.Speed.MID, Device.Speed.FAST)
        // An old phone: the small one; with a recent processor, the next one up.
        assertEquals("qwen3.5-0.8b", AiModels.bestFor(sold(4), slow).id)
        assertEquals("qwen3.5-2b", AiModels.bestFor(sold(4), fast).id)
        // A mid-range phone of the last few years (a Galaxy A72), with 6 GB or 8.
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(6), mid).id)
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(8), mid).id)
        // A fast tablet with 8 GB (a Galaxy Tab S9), or a phone with 12: the best of the four-billion ones.
        assertEquals("gemma-4-e4b", AiModels.bestFor(sold(8), fast).id)
        assertEquals("gemma-4-e4b", AiModels.bestFor(sold(12), fast).id)
        // With 16 GB, the best there is.
        assertEquals("qwen3.5-9b", AiModels.bestFor(sold(16), fast).id)
        // Plenty of memory but an older processor: the bigger ones would crawl.
        assertEquals("gemma-4-e2b", AiModels.bestFor(sold(12), mid).id)
        // Too little memory for any: the smallest.
        assertEquals("qwen3.5-0.8b", AiModels.bestFor(1_000_000_000L, fast).id)
    }

    @Test
    fun eachDeviceIsOfferedTheThreeThatSuitItBest() {
        fun picks(gb: Int, speed: Device.Speed) = AiModels.picks(sold(gb), speed).map { it.id }
        // The Tab S9: the best it runs comfortably, and the two below.
        assertEquals(listOf("gemma-4-e4b", "qwen3.5-4b", "gemma-4-e2b"), picks(8, Device.Speed.FAST))
        // A phone with 12 GB: above those, the nine-billion one, a tight fit.
        assertEquals(listOf("qwen3.5-9b", "gemma-4-e4b", "qwen3.5-4b"), picks(12, Device.Speed.FAST))
        // An A72 with 6 GB: the one it runs well, the smaller one below, and above, the best that runs at all (slowly).
        assertEquals(listOf("gemma-4-e4b", "gemma-4-e2b", "qwen3.5-2b"), picks(6, Device.Speed.MID))
        // An old phone.
        assertEquals(listOf("gemma-4-e2b", "qwen3.5-2b", "qwen3.5-0.8b"), picks(4, Device.Speed.SLOW))
        // Always the one suggested.
        for (gb in listOf(2, 3, 4, 6, 8, 12, 16)) for (speed in Device.Speed.values()) {
            assertTrue("$gb GB, $speed", AiModels.bestFor(sold(gb), speed) in AiModels.picks(sold(gb), speed))
        }
        assertEquals(8, AiModels.soldGb((7.4 * gb).toLong()))
        assertEquals(12, AiModels.soldGb((11.2 * gb).toLong()))
        assertEquals(6, AiModels.soldGb((5.6 * gb).toLong()))
    }

    @Test
    fun theModelsAreOnesAnyoneMayDownloadAndUse() {
        assertEquals(6, AiModels.all.size)
        for (m in AiModels.all) {
            assertTrue(m.url.startsWith("https://huggingface.co/") && m.url.endsWith(".gguf"))
            assertEquals(64, m.sha256.length)
            assertEquals("Apache 2.0", m.licence)
            assertTrue(m.bytes > 100_000_000L)
            assertTrue(m.description.isNotBlank())
        }
        // The simplest first.
        assertEquals(AiModels.all.map { it.quality }.sorted(), AiModels.all.map { it.quality })
        assertEquals(AiModels.all.size, AiModels.all.map { it.id }.distinct().size)
        // The "E" models hold only part of their files in memory.
        assertTrue(AiModels.byId("gemma-4-e4b")!!.held < AiModels.byId("gemma-4-e4b")!!.bytes)
        assertEquals(AiModels.byId("qwen3.5-4b")!!.bytes, AiModels.byId("qwen3.5-4b")!!.held)
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
        val tabS9 = "processor\t: 0\nFeatures\t: fp asimd aes sha2 asimddp sve2 i8mm bf16\n"
        val a72 = "processor\t: 0\nFeatures\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp asimddp\n"
        val old = "processor\t: 0\nFeatures\t: fp asimd evtstrm aes pmull sha1 sha2 crc32\n"
        assertTrue(Device.fast(tabS9))
        assertFalse(Device.fast(a72))
        assertFalse(Device.fast(""))
        assertEquals(Device.Speed.FAST, Device.speed(tabS9, emptyList()))
        assertEquals(Device.Speed.MID, Device.speed(a72, listOf(2_300_000L, 1_800_000L)))
        assertEquals(Device.Speed.SLOW, Device.speed(old, listOf(1_800_000L, 1_400_000L)))
        // Without the features listed, quick cores still count.
        assertEquals(Device.Speed.MID, Device.speed("", listOf(2_400_000L)))
        // A computer's processor with AVX2.
        assertEquals(Device.Speed.MID, Device.speed("flags\t: fpu sse4_2 avx avx2 fma\n", emptyList()))
    }

    @Test
    fun aModelsThinkingOutLoudIsLeftOutOfItsAnswer() {
        assertEquals("The answer.", cleanAnswer("<think>Let me see… the house.</think>\nThe answer."))
        assertEquals("Start.", cleanAnswer("Start. <think>unfinished"))
        assertEquals("Plain.", cleanAnswer("  Plain.  "))
    }

    @Test
    fun aLookUpsAnswerIsReadInItsTwoParts() {
        val both = Prompts.LookedUp("verb, to think deeply", "The house loomed over the valley.")
        assertEquals(both, Prompts.readLookUp("Meaning: verb, to think deeply\nHere: The house loomed over the valley."))
        // The other way round, or with the old label for the meaning: the same.
        assertEquals(both, Prompts.readLookUp("Here: The house loomed over the valley.\nUsually: verb, to think deeply"))
        // Still being written: the meaning as far as it's got, with no half-written label after it, or nothing yet.
        assertEquals(Prompts.LookedUp("verb, to think", ""), Prompts.readLookUp("Meaning: verb, to think"))
        assertEquals(Prompts.LookedUp("verb, to think deeply", ""), Prompts.readLookUp("Meaning: verb, to think deeply\nHer"))
        assertEquals(Prompts.LookedUp("", ""), Prompts.readLookUp("Mean"))
        assertEquals(Prompts.LookedUp("verb, to think deeply", "The house"), Prompts.readLookUp("Meaning: verb, to think deeply\nHere: The house"))
        // A model that didn't keep to the form: its words as they are, as the meaning.
        assertEquals(Prompts.LookedUp("It means to loom.", ""), Prompts.readLookUp("It means to loom."))
        assertEquals("French", Prompts.languageName("fr"))
        assertEquals("Japanese", Prompts.languageName("ja-JP"))
        assertEquals("English", Prompts.languageName(null))
    }
}
