package dev.kolib.app.kobo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoboFactsTest {

    @Test
    fun parsesVersionFile() {
        val v = KoboVersionInfo.parse("N418231234567,4.1.15,4.38.23171,4.1.15,4.1.15,00000000-0000-0000-0000-000000000386\n")!!
        assertEquals("N418231234567", v.serial)
        assertEquals("4.38.23171", v.firmware)
        assertEquals("00000000-0000-0000-0000-000000000386", v.modelId)
        assertEquals("N418…567", v.maskedSerial)
    }

    @Test
    fun rejectsGarbageVersionFile() {
        assertNull(KoboVersionInfo.parse(""))
        assertNull(KoboVersionInfo.parse("just-one-field"))
    }

    @Test
    fun namesModels() {
        assertEquals("Clara 2E", KoboModels.name(0x4235, null))
        assertEquals("Clara BW", KoboModels.name(0x4237, "00000000-0000-0000-0000-000000000391"))
        assertEquals("Clara Colour", KoboModels.name(null, "00000000-0000-0000-0000-000000000393"))
        assertEquals("Clara 2E", KoboModels.name(null, "00000000-0000-0000-0000-000000000386"))
        assertNull(KoboModels.name(null, "00000000-0000-0000-0000-000000000999"))
    }

    @Test
    fun namesTheLaterVersionsOfModelsByTheirOwnNumbers() {
        assertEquals("Touch", KoboModels.name(null, "00000000-0000-0000-0000-000000000320"))
        assertEquals("Aura Edition 2", KoboModels.name(null, "00000000-0000-0000-0000-000000000375"))
        assertEquals("Aura Edition 2", KoboModels.name(null, "00000000-0000-0000-0000-000000000379"))
        assertEquals("Aura H2O Edition 2", KoboModels.name(null, "00000000-0000-0000-0000-000000000378"))
        assertEquals("Forma", KoboModels.name(null, "00000000-0000-0000-0000-000000000380"))
        assertEquals("Aura One", KoboModels.name(null, "00000000-0000-0000-0000-000000000381"))
    }

    @Test
    fun everyModelHasItsScreen() {
        for (model in KoboModels.names) {
            val screen = KoboModels.screen(model)
            assertNotNull("$model has no screen size", screen)
            // Every Kobo's screen is close to three by four, held upright.
            assertTrue("$model: ${screen!!.width} × ${screen.height}", screen.aspect in 0.74f..0.76f)
        }
        assertEquals(KoboScreen(1072, 1448), KoboModels.screen(0x4235, null))                                  // Clara 2E
        assertEquals(KoboScreen(1072, 1448), KoboModels.screen(0x4237, "00000000-0000-0000-0000-000000000391")) // Clara BW
        assertEquals(KoboScreen(1264, 1680), KoboModels.screen(0x4237, "00000000-0000-0000-0000-000000000390")) // Libra Colour
        assertEquals(KoboScreen(1264, 1680), KoboModels.screen(0x4234, null))                                  // Libra 2
        assertEquals(KoboScreen(1440, 1920), KoboModels.screen(0x4231, null))                                  // Sage
        assertEquals(KoboScreen(1440, 1920), KoboModels.screen(0x4229, null))                                  // Forma
        assertEquals(KoboScreen(1404, 1872), KoboModels.screen(0x4236, null))                                  // Elipsa 2E
        assertEquals(KoboScreen(1404, 1872), KoboModels.screen(0x4225, null))                                  // Aura One
        assertEquals(KoboScreen(1080, 1440), KoboModels.screen(0x4193, null))                                  // Aura HD
        assertEquals(KoboScreen(1080, 1429), KoboModels.screen(0x4213, null))                                  // Aura H2O
        assertEquals(KoboScreen(758, 1014), KoboModels.screen(0x4203, null))                                   // Aura
        assertEquals(KoboScreen(758, 1024), KoboModels.screen(0x4230, null))                                   // Nia
        assertEquals(KoboScreen(600, 800), KoboModels.screen(0x4163, null))                                    // Touch
        // The three 2024 models share a USB id: without the version file there's no telling which it is.
        assertNull(KoboModels.screen(0x4237, null))
        assertNull(KoboModels.screen(null, "00000000-0000-0000-0000-000000000999"))
    }

    @Test
    fun aCoverMadeForOneKoboFillsEveryOtherKobosScreen() {
        val screens = KoboModels.names.mapNotNull { KoboModels.screen(it) }.toSet()
        for (madeFor in screens) for (sentTo in screens) {
            assertTrue(
                "${madeFor.width} × ${madeFor.height} on ${sentTo.width} × ${sentTo.height}",
                KoboCovers.isKoboShaped(madeFor.width, madeFor.height, sentTo),
            )
        }
        // An ordinary book cover (two by three) and a square one aren't the screen's shape.
        assertFalse(KoboCovers.isKoboShaped(1600, 2400, KoboScreen.CLARA))
        assertFalse(KoboCovers.isKoboShaped(1000, 1000, KoboScreen.CLARA))
    }

    /** Expected values come from Calibre's Python qhash(), so covers are looked up in the same folders. */
    @Test
    fun coverFoldersMatchCalibre() {
        val sideloaded = KoboImages.imageIdFromContentId("file:///mnt/onboard/Books/Brandon Sanderson/The Final Empire.kepub.epub")
        assertEquals("file____mnt_onboard_Books_Brandon_Sanderson_The_Final_Empire_kepub_epub", sideloaded)
        assertEquals(121531442, KoboImages.qhash(sideloaded))
        assertEquals("50/108", KoboImages.folderFor(sideloaded))

        assertEquals(101946994, KoboImages.qhash("e6f1b2c4-9d3a-4a8e-b0f1-1234567890ab"))
        assertEquals("114/150", KoboImages.folderFor("e6f1b2c4-9d3a-4a8e-b0f1-1234567890ab"))

        assertEquals(257769765, KoboImages.qhash("Ünïcødé ☃ title"))
        assertEquals("37/65", KoboImages.folderFor("Ünïcødé ☃ title"))
        assertEquals("0/0", KoboImages.folderFor(""))
    }

    @Test
    fun parsesKoboTimestamps() {
        val expected = 1705329000000L // 2024-01-15T14:30:00Z
        assertEquals(expected, KoboDates.parseMillis("2024-01-15T14:30:00Z"))
        assertEquals(expected, KoboDates.parseMillis("2024-01-15 14:30:00.000+00:00"))
        assertEquals(expected, KoboDates.parseMillis("2024-01-15T14:30:00"))
        assertEquals(expected, KoboDates.parseMillis("2024-01-15T14:30:00.000"))
        assertEquals(1705276800000L, KoboDates.parseMillis("2024-01-15"))
        assertNull(KoboDates.parseMillis("1970-01-01T00:00:00Z"))
        assertNull(KoboDates.parseMillis(""))
        assertNull(KoboDates.parseMillis("not a date"))
    }
}
