package dev.kolib.app.reader

import dev.kolib.app.tr
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * The fonts the user has added to read in: font files (TrueType, OpenType, WOFF) kept in a folder of the app's,
 * grouped into families by the names inside them, so a family's bold and italic are used for a book's bold and
 * italic.
 */
class UserFonts(private val dir: File) {

    /** One file of a family: its regular, bold, italic... [weight] as style sheets count it (400 regular, 700 bold). */
    data class Face(val file: String, val weight: Int, val italic: Boolean)

    data class Family(val name: String, val faces: List<Face>)

    @Volatile
    private var cached: List<Family>? = null

    /** Counts up each time a font is added or removed, for whatever shows them. */
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The families added, by name. */
    fun families(): List<Family> = cached ?: synchronized(this) {
        cached ?: (dir.listFiles { f -> f.isFile && kindOf(f.name) != null } ?: emptyArray())
            .mapNotNull { f -> runCatching { describe(f) }.getOrNull()?.let { (family, face) -> family to face } }
            .groupBy({ it.first }, { it.second })
            .map { (name, faces) -> Family(name, faces.sortedBy { it.weight * 2 + (if (it.italic) 1 else 0) }) }
            .sortedBy { it.name.lowercase() }
            .also { cached = it }
    }

    fun has(family: String): Boolean = family.isNotEmpty() && families().any { it.name == family }

    /** What the page's style asks for to be set in [family]; null if there's no such family (any more). */
    fun cssFor(family: String): String? = if (has(family)) "'${cssName(family)}', serif" else null

    /** The families as a style sheet for the reader's pages; their files are fetched from under [base]. */
    fun css(base: String): String = buildString {
        for (family in families()) for (face in family.faces) {
            append("@font-face { font-family: '").append(cssName(family.name)).append("'; font-style: ").append(if (face.italic) "italic" else "normal")
            append("; font-weight: ").append(face.weight).append("; font-display: block; src: url(")
            append(base).append(BookServer.encode(face.file)).append("); }\n")
        }
    }

    fun bytes(file: String): ByteArray? = File(dir, file.substringAfterLast('/')).takeIf { it.isFile && it.parentFile == dir }?.readBytes()

    /** [family]'s regular file (or the nearest it has to one), for showing the family's name set in itself. */
    fun fileOf(family: String): File? = families().firstOrNull { it.name == family }?.faces
        ?.minByOrNull { kotlin.math.abs(it.weight - 400) + (if (it.italic) 1000 else 0) }?.let { File(dir, it.file) }

    /**
     * Keeps the font read from [input], a file called [name]. Returns its family's name. Throws if it isn't a
     * font file.
     */
    fun add(name: String, input: InputStream): String {
        val bytes = input.readBytes(MOST_BYTES + 1)
        if (bytes.size > MOST_BYTES) throw IOException(tr("That file is too big to be a font."))
        val kind = kindOf(bytes) ?: throw IOException(tr("“{0}” isn't a font file. Fonts end in .ttf, .otf, .ttc, .woff or .woff2.", name.substringAfterLast('/')))
        dir.mkdirs()
        val base = name.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim().take(80).ifEmpty { "font" }
        val file = File(dir, "$base.$kind")
        file.writeBytes(bytes)
        cached = null
        version.value++
        return describe(file).first
    }

    /** Removes [family] and its files. */
    fun remove(family: String) {
        families().firstOrNull { it.name == family }?.faces?.forEach { File(dir, it.file).delete() }
        cached = null
        version.value++
    }

    private fun describe(file: File): Pair<String, Face> {
        val bytes = file.readBytes()
        val named = runCatching { FontNames.read(bytes) }.getOrNull()
        val guess = FontNames.fromFileName(file.name)
        return (named?.family ?: guess.family) to Face(file.name, named?.weight ?: guess.weight, named?.italic ?: guess.italic)
    }

    private fun cssName(family: String) = "KLU " + family.filter { it !in "'\"\\;{}()" }

    companion object {
        private const val MOST_BYTES = 40 * 1024 * 1024

        /** The app's fonts, for the reader's pages (which have no other way to them). */
        @Volatile
        var shared: UserFonts? = null

        private fun kindOf(name: String): String? = name.substringAfterLast('.', "").lowercase().takeIf { it in setOf("ttf", "otf", "ttc", "woff", "woff2") }

        /** What kind of font file [bytes] start like, as a file ending; null if none. */
        fun kindOf(bytes: ByteArray): String? {
            if (bytes.size < 12) return null
            val tag = String(bytes, 0, 4, Charsets.ISO_8859_1)
            return when {
                tag == "OTTO" -> "otf"
                tag == "ttcf" -> "ttc"
                tag == "wOFF" -> "woff"
                tag == "wOF2" -> "woff2"
                tag == "true" || (bytes[0].toInt() == 0 && bytes[1].toInt() == 1 && bytes[2].toInt() == 0 && bytes[3].toInt() == 0) -> "ttf"
                else -> null
            }
        }

        private fun InputStream.readBytes(most: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1 shl 16)
            while (out.size() < most) {
                val n = read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}

/** A font's family and style, read from inside a TrueType or OpenType file, or guessed from a file's name. */
object FontNames {
    /** [weight] as style sheets count it: 400 for regular, 700 for bold. */
    class Names(val family: String, val weight: Int, val italic: Boolean)

    private fun u16(b: ByteBuffer, at: Int) = b.getShort(at).toInt() and 0xFFFF

    /** From the file's own tables; null for a file that isn't TrueType or OpenType (WOFF packs them away) or has no name. */
    fun read(bytes: ByteArray): Names? {
        if (bytes.size < 12) return null
        val b = ByteBuffer.wrap(bytes)
        var start = 0
        val tag = String(bytes, 0, 4, Charsets.ISO_8859_1)
        // A collection: its first font.
        if (tag == "ttcf") start = b.getInt(12)
        else if (tag == "wOFF" || tag == "wOF2") return null
        if (start < 0 || start + 12 > bytes.size) return null
        val tables = u16(b, start + 4)
        var name = -1
        var os2 = -1
        var head = -1
        for (i in 0 until tables) {
            val at = start + 12 + i * 16
            if (at + 16 > bytes.size) return null
            val offset = b.getInt(at + 8)
            when (String(bytes, at, 4, Charsets.ISO_8859_1)) {
                "name" -> name = offset
                "OS/2" -> os2 = offset
                "head" -> head = offset
            }
        }
        if (name < 0 || name + 6 > bytes.size) return null
        val count = u16(b, name + 2)
        val strings = name + u16(b, name + 4)
        // Each name the best it's given in: English (Windows), then any other Windows or Unicode one, then the old Mac's.
        val found = HashMap<Int, Pair<Int, String>>()
        for (i in 0 until count) {
            val r = name + 6 + i * 12
            if (r + 12 > bytes.size) break
            val platform = u16(b, r)
            val language = u16(b, r + 4)
            val id = u16(b, r + 6)
            val length = u16(b, r + 8)
            val offset = strings + u16(b, r + 10)
            if (id !in WANTED || offset < 0 || offset + length > bytes.size) continue
            val (rank, text) = when (platform) {
                3 -> (if (language == 0x409) 3 else 2) to String(bytes, offset, length, Charsets.UTF_16BE)
                0 -> 1 to String(bytes, offset, length, Charsets.UTF_16BE)
                1 -> 0 to String(bytes, offset, length, Charsets.ISO_8859_1)
                else -> continue
            }
            if (text.isBlank()) continue
            val had = found[id]
            if (had == null || rank > had.first) found[id] = rank to text
        }
        val family = (found[16] ?: found[1])?.second?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val style = (found[17] ?: found[2])?.second.orEmpty().lowercase()
        var italic = style.contains("italic") || style.contains("oblique")
        var weight = if (style.contains("bold") && !style.contains("semi") && !style.contains("demi")) 700 else 400
        if (os2 >= 0 && os2 + 64 <= bytes.size) {
            val selection = u16(b, os2 + 62)
            italic = italic || (selection and 1) != 0
            u16(b, os2 + 4).takeIf { it in 1..1000 }?.let { weight = it }
        } else if (head >= 0 && head + 46 <= bytes.size) {
            val mac = u16(b, head + 44)
            if ((mac and 1) != 0) weight = 700
            italic = italic || (mac and 2) != 0
        }
        return Names(family, weight, italic)
    }

    private val WANTED = setOf(1, 2, 16, 17)
    private val styleWords = Regex("(?i)[-_ ]?(regular|bolditalic|boldoblique|bold|italic|oblique|medium|book|roman|normal)$")

    /** From what the file is called: "Bookerly-BoldItalic.ttf" is Bookerly's bold italic. */
    fun fromFileName(name: String): Names {
        val base = name.substringBeforeLast('.')
        val lower = base.lowercase()
        val bold = lower.contains("bold") && !lower.contains("semibold")
        val italic = lower.contains("italic") || lower.contains("oblique")
        var family = base
        repeat(2) { family = family.replace(styleWords, "") }
        return Names(family.replace('_', ' ').trim().ifEmpty { base }, if (bold) 700 else 400, italic)
    }
}
