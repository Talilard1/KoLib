package dev.kobolibrary.app.kobo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
