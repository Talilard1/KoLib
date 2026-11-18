package dev.kolib.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class UserFontsTest {
    @get:Rule
    val folder = TemporaryFolder()

    /**
     * The least a TrueType file needs for its names to be read: a name table (family, style) and an OS/2 table
     * (weight, italic).
     */
    private fun font(family: String, style: String, weight: Int, italic: Boolean, typographicFamily: String? = null): ByteArray {
        val names = listOfNotNull(1 to family, 2 to style, typographicFamily?.let { 16 to it }).map { (id, text) -> id to text.toByteArray(Charsets.UTF_16BE) }
        val name = ByteArrayOutputStream().also { out ->
            val d = DataOutputStream(out)
            d.writeShort(0); d.writeShort(names.size); d.writeShort(6 + names.size * 12)
            var at = 0
            for ((id, bytes) in names) {
                d.writeShort(3); d.writeShort(1); d.writeShort(0x409); d.writeShort(id); d.writeShort(bytes.size); d.writeShort(at)
                at += bytes.size
            }
            for ((_, bytes) in names) d.write(bytes)
        }.toByteArray()
        val os2 = ByteArray(96).also {
            it[4] = (weight shr 8).toByte(); it[5] = weight.toByte()
            it[63] = (if (italic) 1 else 0).toByte()
        }
        val out = ByteArrayOutputStream()
        val d = DataOutputStream(out)
        d.writeInt(0x00010000); d.writeShort(2); d.writeShort(0); d.writeShort(0); d.writeShort(0)
        var offset = 12 + 2 * 16
        for ((tag, table) in listOf("OS/2" to os2, "name" to name)) {
            d.write(tag.toByteArray(Charsets.ISO_8859_1)); d.writeInt(0); d.writeInt(offset); d.writeInt(table.size)
            offset += table.size
        }
        d.write(os2); d.write(name)
        return out.toByteArray()
    }

    @Test
    fun aFontsFamilyAndStyleAreReadFromInsideIt() {
        val bold = FontNames.read(font("Bookerly", "Bold Italic", 700, italic = true))!!
        assertEquals("Bookerly", bold.family)
        assertEquals(700, bold.weight)
        assertTrue(bold.italic)
        // A family's lighter and heavier cuts name the family they belong to: that's the one they're filed under.
        val light = FontNames.read(font("Bookerly Light", "Regular", 300, italic = false, typographicFamily = "Bookerly"))!!
        assertEquals("Bookerly", light.family)
        assertEquals(300, light.weight)
        assertFalse(light.italic)
        // Not a font, or one whose tables are packed away: nothing to read.
        assertNull(FontNames.read("wOF2".toByteArray() + ByteArray(40)))
        assertNull(FontNames.read(ByteArray(4)))
    }

    @Test
    fun aRealFontIsReadToo() {
        val file = listOf("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf").map { File(it) }.firstOrNull { it.isFile }
        org.junit.Assume.assumeNotNull(file)
        val names = FontNames.read(file!!.readBytes())!!
        assertTrue(names.family.startsWith("DejaVu"))
        assertEquals(700, names.weight)
        assertFalse(names.italic)
    }

    @Test
    fun whereAFileSaysNothingItsNameDoes() {
        val n = FontNames.fromFileName("Bookerly-BoldItalic.woff2")
        assertEquals("Bookerly", n.family)
        assertEquals(700, n.weight)
        assertTrue(n.italic)
        assertEquals("Atkinson Hyperlegible", FontNames.fromFileName("Atkinson_Hyperlegible-Regular.ttf").family)
        assertEquals(400, FontNames.fromFileName("Literata.otf").weight)
    }

    @Test
    fun fontsAreAddedGroupedIntoFamiliesAndRemoved() {
        val fonts = UserFonts(File(folder.root, "fonts"))
        assertEquals(emptyList<UserFonts.Family>(), fonts.families())
        assertEquals("Bookerly", fonts.add("Bookerly-Regular.ttf", font("Bookerly", "Regular", 400, false).inputStream()))
        assertEquals("Bookerly", fonts.add("Bookerly-Bold.ttf", font("Bookerly", "Bold", 700, false).inputStream()))
        assertEquals("Bookerly", fonts.add("Bookerly-Italic.ttf", font("Bookerly", "Italic", 400, true).inputStream()))
        assertEquals("Charis SIL", fonts.add("content://whatever/CharisSIL.ttf", font("Charis SIL", "Regular", 400, false).inputStream()))
        assertEquals(4, fonts.version.value)
        val families = fonts.families()
        assertEquals(listOf("Bookerly", "Charis SIL"), families.map { it.name })
        assertEquals(listOf(400 to false, 400 to true, 700 to false), families[0].faces.map { it.weight to it.italic })
        assertTrue(fonts.has("Bookerly"))
        assertFalse(fonts.has("Garamond"))
        assertFalse(fonts.has(""))
        // What the reader's pages are given: each file under its family's name, with its weight and slant.
        val css = fonts.css("https://kolib.invalid/app/userfont/")
        assertEquals(4, Regex("@font-face").findAll(css).count())
        assertTrue(css.contains("font-family: 'KLU Bookerly'; font-style: italic; font-weight: 400;"))
        assertTrue(css.contains("src: url(https://kolib.invalid/app/userfont/Bookerly-Bold.ttf)"))
        assertEquals("'KLU Bookerly', serif", fonts.cssFor("Bookerly"))
        assertNull(fonts.cssFor("Garamond"))
        assertNotNull(fonts.bytes("Bookerly-Bold.ttf"))
        assertNull(fonts.bytes("../secrets.txt"))
        assertEquals("Bookerly-Regular.ttf", fonts.fileOf("Bookerly")!!.name)
        // A new store over the same folder finds them again.
        assertEquals(listOf("Bookerly", "Charis SIL"), UserFonts(File(folder.root, "fonts")).families().map { it.name })
        fonts.remove("Bookerly")
        assertEquals(listOf("Charis SIL"), fonts.families().map { it.name })
        assertEquals(1, File(folder.root, "fonts").listFiles()!!.size)
    }

    @Test
    fun whatIsntAFontIsntAdded() {
        val fonts = UserFonts(File(folder.root, "fonts"))
        val e = assertThrows(IOException::class.java) { fonts.add("notes.txt", "Just some words in a text file.".byteInputStream()) }
        assertTrue(e.message!!.contains("notes.txt"))
        assertEquals(emptyList<UserFonts.Family>(), fonts.families())
        assertEquals("woff2", UserFonts.kindOf("wOF2".toByteArray() + ByteArray(20)))
        assertEquals("otf", UserFonts.kindOf("OTTO".toByteArray() + ByteArray(20)))
        assertNull(UserFonts.kindOf("PK".toByteArray() + ByteArray(20)))
    }

    @Test
    fun aBookSetInAFontThatsGoneFallsBackToTheBuiltInOne() {
        val fonts = UserFonts(File(folder.root, "fonts"))
        UserFonts.shared = fonts
        try {
            fonts.add("Bookerly-Regular.ttf", font("Bookerly", "Regular", 400, false).inputStream())
            val settings = ReaderSettings(font = ReaderFont.LITERATA, userFont = "Bookerly")
            assertEquals("'KLU Bookerly', serif", org.json.JSONObject(settings.toJs("en", 0)).getString("font"))
            fonts.remove("Bookerly")
            assertEquals(ReaderFont.LITERATA.css, org.json.JSONObject(settings.toJs("en", 0)).getString("font"))
            // The font is part of a book's own look, and comes back from it.
            val kept = ReaderSettings.withLook(ReaderSettings(), settings.lookJson())
            assertEquals("Bookerly", kept.userFont)
            assertEquals(ReaderFont.LITERATA, kept.font)
        } finally {
            UserFonts.shared = null
        }
    }
}
