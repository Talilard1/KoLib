package dev.kobolibrary.app.convert

import java.nio.charset.Charset

/** The characters PDF fonts' codes stand for: glyph names, and the standard encodings. */
internal object PdfEncodings {

    private val winAnsi: CharArray = String(ByteArray(256) { it.toByte() }, Charset.forName("windows-1252")).toCharArray().also { a ->
        // Codes Windows leaves out stand for these in PDF's WinAnsiEncoding.
        a[0x81] = '\u0000'; a[0x8D] = '\u0000'; a[0x8F] = '\u0000'; a[0x90] = '\u0000'; a[0x9D] = '\u0000'
    }

    private const val MAC_HIGH = "ÄÅÇÉÑÖÜáàâäãåçéèêëíìîïñóòôöõúùûü†°¢£§•¶ß®©™´¨≠ÆØ∞±≤≥¥µ∂∑∏π∫ªºΩæø¿¡¬√ƒ≈∆«»… ÀÃÕŒœ–—“”‘’÷◊ÿŸ⁄€‹›ﬁﬂ‡·‚„‰ÂÊÁËÈÍÎÏÌÓÔÒÚÛÙıˆ˜¯˘˙˚¸˝˛ˇ"

    /** Adobe's StandardEncoding where it isn't ASCII. */
    private val standardHigh = mapOf(
        0x27 to '’', 0x60 to '‘', 0xA1 to '¡', 0xA2 to '¢', 0xA3 to '£', 0xA4 to '⁄', 0xA5 to '¥', 0xA6 to 'ƒ', 0xA7 to '§', 0xA8 to '¤',
        0xA9 to '\'', 0xAA to '“', 0xAB to '«', 0xAC to '‹', 0xAD to '›', 0xAE to 'ﬁ', 0xAF to 'ﬂ', 0xB1 to '–', 0xB2 to '†', 0xB3 to '‡',
        0xB4 to '·', 0xB6 to '¶', 0xB7 to '•', 0xB8 to '‚', 0xB9 to '„', 0xBA to '”', 0xBB to '»', 0xBC to '…', 0xBD to '‰', 0xBF to '¿',
        0xC1 to '`', 0xC2 to '´', 0xC3 to 'ˆ', 0xC4 to '˜', 0xC5 to '¯', 0xC6 to '˘', 0xC7 to '˙', 0xC8 to '¨', 0xCA to '˚', 0xCB to '¸',
        0xCD to '˝', 0xCE to '˛', 0xCF to 'ˇ', 0xD0 to '—', 0xE1 to 'Æ', 0xE3 to 'ª', 0xE8 to 'Ł', 0xE9 to 'Ø', 0xEA to 'Œ', 0xEB to 'º',
        0xF1 to 'æ', 0xF5 to 'ı', 0xF8 to 'ł', 0xF9 to 'ø', 0xFA to 'œ', 0xFB to 'ß',
    )

    fun winAnsi(code: Int): Char = if (code in 0..255) winAnsi[code] else '\u0000'
    fun macRoman(code: Int): Char = if (code < 128) code.toChar() else MAC_HIGH.getOrElse(code - 128) { '\u0000' }
    fun standard(code: Int): Char = standardHigh[code] ?: if (code in 32..126) code.toChar() else '\u0000'

    /** PDFDocEncoding, for text strings without a byte-order mark. */
    fun pdfDoc(code: Int): Char = when (code) {
        in 0x18..0x1F -> "˘ˇˆ˙˝˛˚˜"[code - 0x18]
        in 0x80..0x9E -> "•†‡…—–ƒ⁄‹›−‰„“”‘’‚™ﬁﬂŁŒŠŸŽıłœšž"[code - 0x80]
        0xA0 -> '€'
        else -> code.toChar()
    }

    /** Glyph names to characters: Adobe's list, for the names fonts use. */
    private val glyphs: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (c in 'A'..'Z') m[c.toString()] = c.toString()
        for (c in 'a'..'z') m[c.toString()] = c.toString()
        listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine").forEachIndexed { i, n ->
            m[n] = ('0' + i).toString()
            m["$n.oldstyle"] = ('0' + i).toString()
            m["${n}oldstyle"] = ('0' + i).toString()
            m["$n.lf"] = ('0' + i).toString()
            m["${n}superior"] = "⁰¹²³⁴⁵⁶⁷⁸⁹"[i].toString()
            m["${n}inferior"] = "₀₁₂₃₄₅₆₇₈₉"[i].toString()
        }
        val pairs = """space 20 exclam 21 quotedbl 22 numbersign 23 dollar 24 percent 25 ampersand 26 quotesingle 27 parenleft 28 parenright 29
asterisk 2A plus 2B comma 2C hyphen 2D period 2E slash 2F colon 3A semicolon 3B less 3C equal 3D greater 3E question 3F at 40
bracketleft 5B backslash 5C bracketright 5D asciicircum 5E underscore 5F grave 60 braceleft 7B bar 7C braceright 7D asciitilde 7E
nbspace A0 exclamdown A1 cent A2 sterling A3 currency A4 yen A5 brokenbar A6 section A7 dieresis A8 copyright A9 ordfeminine AA
guillemotleft AB logicalnot AC sfthyphen AD registered AE macron AF degree B0 plusminus B1 twosuperior B2 threesuperior B3 acute B4
mu B5 paragraph B6 periodcentered B7 cedilla B8 onesuperior B9 ordmasculine BA guillemotright BB onequarter BC onehalf BD
threequarters BE questiondown BF Agrave C0 Aacute C1 Acircumflex C2 Atilde C3 Adieresis C4 Aring C5 AE C6 Ccedilla C7 Egrave C8
Eacute C9 Ecircumflex CA Edieresis CB Igrave CC Iacute CD Icircumflex CE Idieresis CF Eth D0 Ntilde D1 Ograve D2 Oacute D3
Ocircumflex D4 Otilde D5 Odieresis D6 multiply D7 Oslash D8 Ugrave D9 Uacute DA Ucircumflex DB Udieresis DC Yacute DD Thorn DE
germandbls DF agrave E0 aacute E1 acircumflex E2 atilde E3 adieresis E4 aring E5 ae E6 ccedilla E7 egrave E8 eacute E9
ecircumflex EA edieresis EB igrave EC iacute ED icircumflex EE idieresis EF eth F0 ntilde F1 ograve F2 oacute F3 ocircumflex F4
otilde F5 odieresis F6 divide F7 oslash F8 ugrave F9 uacute FA ucircumflex FB udieresis FC yacute FD thorn FE ydieresis FF
Amacron 100 amacron 101 Abreve 102 abreve 103 Aogonek 104 aogonek 105 Cacute 106 cacute 107 Ccaron 10C ccaron 10D Dcaron 10E
dcaron 10F Dcroat 110 dcroat 111 Emacron 112 emacron 113 Edotaccent 116 edotaccent 117 Eogonek 118 eogonek 119 Ecaron 11A
ecaron 11B Gbreve 11E gbreve 11F Idotaccent 130 dotlessi 131 IJ 132 ij 133 Lacute 139 lacute 13A Lcaron 13D lcaron 13E Lslash 141
lslash 142 Nacute 143 nacute 144 Ncaron 147 ncaron 148 Omacron 14C omacron 14D Ohungarumlaut 150 ohungarumlaut 151 OE 152 oe 153
Racute 154 racute 155 Rcaron 158 rcaron 159 Sacute 15A sacute 15B Scedilla 15E scedilla 15F Scaron 160 scaron 161 Tcaron 164
tcaron 165 Umacron 16A umacron 16B Uring 16E uring 16F Uhungarumlaut 170 uhungarumlaut 171 Uogonek 172 uogonek 173 Ydieresis 178
Zacute 179 zacute 17A Zdotaccent 17B zdotaccent 17C Zcaron 17D zcaron 17E florin 192 circumflex 2C6 caron 2C7 breve 2D8
dotaccent 2D9 ring 2DA ogonek 2DB tilde 2DC hungarumlaut 2DD endash 2013 emdash 2014 quoteleft 2018 quoteright 2019
quotesinglbase 201A quotedblleft 201C quotedblright 201D quotedblbase 201E dagger 2020 daggerdbl 2021 bullet 2022 ellipsis 2026
perthousand 2030 guilsinglleft 2039 guilsinglright 203A fraction 2044 Euro 20AC trademark 2122 minus 2212 fi FB01 fl FB02
ff FB00 ffi FB03 ffl FB04 st FB06 Omega 3A9 pi 3C0 mu1 B5 Delta 2206 summation 2211 product 220F radical 221A infinity 221E
integral 222B approxequal 2248 notequal 2260 lessequal 2264 greaterequal 2265 lozenge 25CA arrowright 2192 arrowleft 2190
arrowup 2191 arrowdown 2193 checkmark 2713 bullet1 2022 hyphen1 2010 figuredash 2012 quotereversed 201B underscoredbl 2017
twodotenleader 2025 onedotenleader 2024 periodcentered1 B7 afii00208 2015 nonbreakingspace A0 uni00A0 A0 space1 20"""
        val tokens = pairs.split(Regex("\\s+"))
        var i = 0
        while (i + 1 < tokens.size) {
            m[tokens[i]] = String(Character.toChars(tokens[i + 1].toInt(16)))
            i += 2
        }
        m
    }

    /** The character(s) a glyph named [name] stands for; null if the name says nothing. */
    fun glyph(name: String): String? {
        glyphs[name]?.let { return it }
        val base = name.substringBefore('.').substringBefore('_')
        glyphs[base]?.let { return it }
        if (base.startsWith("uni") && base.length >= 7) {
            val hex = base.substring(3)
            if (hex.length % 4 == 0 && hex.all { it.isLetterOrDigit() }) return runCatching {
                buildString { hex.chunked(4).forEach { appendCodePoint(it.toInt(16)) } }
            }.getOrNull()
        }
        if (base.startsWith("u") && base.length in 5..7) base.substring(1).toIntOrNull(16)?.let { if (Character.isValidCodePoint(it)) return String(Character.toChars(it)) }
        // Small capitals ("Asmall") and the like: their letter.
        if (base.endsWith("small") && base.length > 5) glyph(base.dropLast(5))?.let { return it.lowercase() }
        return null
    }
}

/** A font as far as getting text out goes: what each code stands for, and how wide it is. */
internal class PdfFont(private val pdf: PdfFile, private val d: Map<String, Any?>) {
    private val subtype = (d["Subtype"] as? PdfName)?.name ?: "Type1"
    val composite = subtype == "Type0"
    private val toUnicode = HashMap<Int, String>()
    /** Codespace ranges of a composite font's encoding: how many bytes each code has. */
    private val codeLengths = ArrayList<Triple<Int, Long, Long>>()
    private val simpleMap = arrayOfNulls<String>(256)
    private val widths = HashMap<Int, Double>()
    private var defaultWidth = 1000.0
    val bold: Boolean
    val name: String = (d["BaseFont"] as? PdfName)?.name.orEmpty()

    init {
        val descriptor = pdf.dict(d["FontDescriptor"]) ?: pdf.dict((pdf.list(d["DescendantFonts"])?.firstOrNull()).let { pdf.dict(it)?.get("FontDescriptor") })
        val flags = (pdf.number(descriptor?.get("Flags")) ?: 0.0).toInt()
        bold = name.contains("Bold", true) || name.contains("Black", true) || name.contains("Heavy", true) || flags and (1 shl 18) != 0 ||
            (pdf.number(descriptor?.get("FontWeight")) ?: 400.0) >= 600
        pdf.stream(d["ToUnicode"])?.let { parseCMap(it, toUnicode) }
        if (composite) {
            val enc = pdf.resolve(d["Encoding"])
            when (enc) {
                is PdfStream -> pdf.stream(enc)?.let { parseCodespace(String(it, Charsets.ISO_8859_1)) }
                else -> Unit
            }
            if (codeLengths.isEmpty()) codeLengths += Triple(2, 0L, 0xFFFFL)
            val desc = pdf.dict(pdf.list(d["DescendantFonts"])?.firstOrNull())
            defaultWidth = pdf.number(desc?.get("DW")) ?: 1000.0
            pdf.list(desc?.get("W"))?.let { w ->
                var i = 0
                while (i < w.size) {
                    val first = (pdf.number(w[i]) ?: break).toInt()
                    val next = pdf.resolve(w.getOrNull(i + 1))
                    if (next is List<*>) {
                        next.forEachIndexed { k, v -> pdf.number(v)?.let { widths[first + k] = it } }
                        i += 2
                    } else {
                        val last = (pdf.number(next) ?: break).toInt()
                        val width = pdf.number(w.getOrNull(i + 2)) ?: break
                        for (c in first..minOf(last, first + 65535)) widths[c] = width
                        i += 3
                    }
                }
            }
        } else {
            val enc = pdf.resolve(d["Encoding"])
            val baseName = when (enc) {
                is PdfName -> enc.name
                is Map<*, *> -> (pdf.resolve(enc["BaseEncoding"]) as? PdfName)?.name
                else -> null
            }
            val symbolic = name.contains("Symbol") || name.contains("Dingbat")
            for (c in 0 until 256) {
                val ch = when (baseName) {
                    "WinAnsiEncoding" -> PdfEncodings.winAnsi(c)
                    "MacRomanEncoding" -> PdfEncodings.macRoman(c)
                    "StandardEncoding" -> PdfEncodings.standard(c)
                    else -> if (subtype == "TrueType" || symbolic) PdfEncodings.winAnsi(c) else PdfEncodings.standard(c)
                }
                if (ch != '\u0000') simpleMap[c] = ch.toString()
            }
            (enc as? Map<*, *>)?.let { e ->
                pdf.list(e["Differences"])?.let { diffs ->
                    var code = 0
                    for (x in diffs) {
                        when (val v = pdf.resolve(x)) {
                            is Long -> code = v.toInt()
                            is PdfName -> {
                                if (code in 0..255) simpleMap[code] = PdfEncodings.glyph(v.name) ?: simpleMap[code]
                                code++
                            }
                        }
                    }
                }
            }
            val first = (pdf.number(d["FirstChar"]) ?: 0.0).toInt()
            pdf.list(d["Widths"])?.forEachIndexed { k, v -> pdf.number(v)?.let { widths[first + k] = it } }
            defaultWidth = pdf.number(descriptor?.get("MissingWidth"))?.takeIf { it > 0 } ?: if (widths.isEmpty()) 500.0 else widths.values.average()
            if (subtype == "Type3") {
                // Widths in glyph space: scaled by the font's matrix.
                val fm = pdf.list(d["FontMatrix"])?.mapNotNull { pdf.number(it) }
                val scale = (fm?.firstOrNull() ?: 0.001) * 1000
                for (k in widths.keys.toList()) widths[k] = widths[k]!! * scale
                defaultWidth *= scale
            }
        }
    }

    private fun parseCodespace(s: String) {
        Regex("""begincodespacerange(.*?)endcodespacerange""", RegexOption.DOT_MATCHES_ALL).findAll(s).forEach { block ->
            Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""").findAll(block.groupValues[1]).forEach { m ->
                codeLengths += Triple(m.groupValues[1].length / 2, m.groupValues[1].toLong(16), m.groupValues[2].toLong(16))
            }
        }
    }

    private fun parseCMap(bytes: ByteArray, into: HashMap<Int, String>) {
        val s = String(bytes, Charsets.ISO_8859_1)
        if (composite) parseCodespace(s)
        fun hexText(h: String): String {
            val b = ByteArray(h.length / 2) { k -> h.substring(2 * k, 2 * k + 2).toInt(16).toByte() }
            return String(b, Charsets.UTF_16BE)
        }
        Regex("""beginbfchar(.*?)endbfchar""", RegexOption.DOT_MATCHES_ALL).findAll(s).forEach { block ->
            Regex("""<([0-9A-Fa-f]+)>\s*(?:<([0-9A-Fa-f]*)>|/(\S+))""").findAll(block.groupValues[1]).forEach { m ->
                val code = m.groupValues[1].toLongOrNull(16)?.toInt() ?: return@forEach
                into[code] = if (m.groupValues[3].isNotEmpty()) PdfEncodings.glyph(m.groupValues[3]) ?: "" else hexText(m.groupValues[2])
            }
        }
        Regex("""beginbfrange(.*?)endbfrange""", RegexOption.DOT_MATCHES_ALL).findAll(s).forEach { block ->
            Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*(<[0-9A-Fa-f]*>|\[[^\]]*\])""").findAll(block.groupValues[1]).forEach { m ->
                val lo = m.groupValues[1].toLongOrNull(16)?.toInt() ?: return@forEach
                val hi = m.groupValues[2].toLongOrNull(16)?.toInt() ?: return@forEach
                if (hi - lo > 65535 || hi < lo) return@forEach
                val dst = m.groupValues[3]
                if (dst.startsWith("[")) {
                    Regex("""<([0-9A-Fa-f]*)>""").findAll(dst).forEachIndexed { k, d -> if (lo + k <= hi) into[lo + k] = hexText(d.groupValues[1]) }
                } else {
                    val h = dst.trim('<', '>')
                    if (h.isEmpty()) return@forEach
                    val base = hexText(h)
                    for (c in lo..hi) {
                        val off = c - lo
                        into[c] = if (base.isEmpty()) "" else base.dropLast(1) + (base.last() + off)
                    }
                }
            }
        }
    }

    /** The codes in a string shown with this font. */
    fun codes(bytes: ByteArray): List<Int> {
        if (!composite) return bytes.map { it.toInt() and 0xFF }
        val out = ArrayList<Int>()
        var i = 0
        while (i < bytes.size) {
            var matched = false
            for (len in 1..4) {
                if (i + len > bytes.size) break
                var v = 0L
                for (k in 0 until len) v = (v shl 8) or (bytes[i + k].toLong() and 0xFF)
                if (codeLengths.any { it.first == len && v in it.second..it.third }) {
                    out += v.toInt()
                    i += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                val len = codeLengths.firstOrNull()?.first ?: 2
                var v = 0
                for (k in 0 until minOf(len, bytes.size - i)) v = (v shl 8) or (bytes[i + k].toInt() and 0xFF)
                out += v
                i += len
            }
        }
        return out
    }

    fun text(code: Int): String = toUnicode[code] ?: if (!composite) simpleMap.getOrNull(code) ?: "" else if (code in 0x20..0xFFFF && toUnicode.isEmpty()) code.toChar().toString() else ""

    fun width(code: Int): Double = (widths[code] ?: defaultWidth) / 1000.0
}

/** A piece of text on a page: where it starts, how wide it is, and its size. */
internal class Glyphs(val text: String, val x: Double, val y: Double, val width: Double, val size: Double, val bold: Boolean)

/** Runs a page's content and gathers its text, with where each piece is. */
internal class PdfTextReader(private val pdf: PdfFile) {

    private class GState(var ctm: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)) {
        var font: PdfFont? = null
        var size = 12.0
        var charSpace = 0.0
        var wordSpace = 0.0
        var scale = 1.0
        var leading = 0.0
        var rise = 0.0
        fun copy() = GState(ctm.copyOf()).also {
            it.font = font; it.size = size; it.charSpace = charSpace; it.wordSpace = wordSpace; it.scale = scale; it.leading = leading; it.rise = rise
        }
    }

    private val fonts = HashMap<Any, PdfFont>()
    var images = 0
        private set

    /** The text on [page], piece by piece. */
    fun read(page: Map<String, Any?>): List<Glyphs> {
        images = 0
        val out = ArrayList<Glyphs>()
        val contents = when (val c = pdf.resolve(page["Contents"])) {
            is PdfStream -> pdf.stream(c) ?: ByteArray(0)
            is List<*> -> c.mapNotNull { pdf.stream(it) }.fold(ByteArray(0)) { acc, b -> acc + b + byteArrayOf(10) }
            else -> ByteArray(0)
        }
        run(contents, pdf.dict(page["Resources"]) ?: emptyMap(), GState(), out, 0)
        return out
    }

    private fun mul(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[0] * b[0] + a[1] * b[2], a[0] * b[1] + a[1] * b[3],
        a[2] * b[0] + a[3] * b[2], a[2] * b[1] + a[3] * b[3],
        a[4] * b[0] + a[5] * b[2] + b[4], a[4] * b[1] + a[5] * b[3] + b[5],
    )

    private fun run(content: ByteArray, resources: Map<String, Any?>, start: GState, out: MutableList<Glyphs>, depth: Int) {
        if (depth > 12) return
        val lx = PdfLexer(content)
        val args = ArrayList<Any?>()
        var g = start
        val stack = ArrayList<GState>()
        var tm = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        var tlm = tm.copyOf()
        val fontDicts = pdf.dict(resources["Font"]) ?: emptyMap()
        val xobjects = pdf.dict(resources["XObject"]) ?: emptyMap()

        fun show(bytes: ByteArray) {
            val font = g.font ?: return
            val sb = StringBuilder()
            var x0 = Double.NaN
            var y0 = 0.0
            var size = 0.0
            var w = 0.0
            for (code in font.codes(bytes)) {
                val trm = mul(doubleArrayOf(g.size * g.scale, 0.0, 0.0, g.size, 0.0, g.rise), mul(tm, g.ctm))
                val gx = trm[4]
                val gy = trm[5]
                val s = Math.hypot(trm[2], trm[3])
                val adv = font.width(code) * g.size + g.charSpace + if (code == 32 && !font.composite) g.wordSpace else 0.0
                val advUser = adv * g.scale * Math.hypot(tm[0] * g.ctm[0] + tm[1] * g.ctm[2], tm[0] * g.ctm[1] + tm[1] * g.ctm[3])
                if (x0.isNaN()) {
                    x0 = gx
                    y0 = gy
                    size = s
                }
                sb.append(font.text(code))
                w = (gx - x0) + advUser
                tm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, adv * g.scale, 0.0), tm)
            }
            if (sb.isNotEmpty() && !x0.isNaN()) out += Glyphs(sb.toString(), x0, y0, w, size, font.bold)
        }

        while (true) {
            val save = lx.pos
            val t = lx.token() ?: break
            if (t !is PdfKeyword) {
                lx.pos = save
                args += lx.obj()
                if (args.size > 64) args.clear()
                continue
            }
            fun num(i: Int) = (args.getOrNull(i) as? Number)?.toDouble() ?: 0.0
            when (t.word) {
                "q" -> stack += g.copy()
                "Q" -> if (stack.isNotEmpty()) g = stack.removeAt(stack.size - 1)
                "cm" -> if (args.size >= 6) g.ctm = mul(doubleArrayOf(num(0), num(1), num(2), num(3), num(4), num(5)), g.ctm)
                "BT" -> {
                    tm = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
                    tlm = tm.copyOf()
                }
                "Tf" -> {
                    val name = (args.getOrNull(0) as? PdfName)?.name
                    g.size = num(1)
                    val ref = name?.let { fontDicts[it] }
                    g.font = ref?.let { r -> fonts.getOrPut(r) { PdfFont(pdf, pdf.dict(r) ?: emptyMap()) } }
                }
                "Tc" -> g.charSpace = num(0)
                "Tw" -> g.wordSpace = num(0)
                "Tz" -> g.scale = num(0) / 100.0
                "TL" -> g.leading = num(0)
                "Ts" -> g.rise = num(0)
                "Td" -> {
                    tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, num(0), num(1)), tlm)
                    tm = tlm.copyOf()
                }
                "TD" -> {
                    g.leading = -num(1)
                    tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, num(0), num(1)), tlm)
                    tm = tlm.copyOf()
                }
                "Tm" -> if (args.size >= 6) {
                    tlm = doubleArrayOf(num(0), num(1), num(2), num(3), num(4), num(5))
                    tm = tlm.copyOf()
                }
                "T*" -> {
                    tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, -g.leading), tlm)
                    tm = tlm.copyOf()
                }
                "Tj" -> (args.lastOrNull() as? PdfString)?.let { show(it.bytes) }
                "'" -> {
                    tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, -g.leading), tlm)
                    tm = tlm.copyOf()
                    (args.lastOrNull() as? PdfString)?.let { show(it.bytes) }
                }
                "\"" -> {
                    g.wordSpace = num(0)
                    g.charSpace = num(1)
                    tlm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, -g.leading), tlm)
                    tm = tlm.copyOf()
                    (args.lastOrNull() as? PdfString)?.let { show(it.bytes) }
                }
                "TJ" -> (args.lastOrNull() as? List<*>)?.forEach { item ->
                    when (item) {
                        is PdfString -> show(item.bytes)
                        is Number -> tm = mul(doubleArrayOf(1.0, 0.0, 0.0, 1.0, -item.toDouble() / 1000.0 * g.size * g.scale, 0.0), tm)
                    }
                }
                "Do" -> {
                    val name = (args.lastOrNull() as? PdfName)?.name
                    val xo = name?.let { pdf.resolve(xobjects[it]) } as? PdfStream
                    when ((xo?.dict?.get("Subtype") as? PdfName)?.name) {
                        "Form" -> {
                            val body = pdf.stream(xo) ?: ByteArray(0)
                            val m = pdf.list(xo.dict["Matrix"])?.mapNotNull { pdf.number(it) }
                            val inner = g.copy()
                            if (m != null && m.size == 6) inner.ctm = mul(m.toDoubleArray(), g.ctm)
                            run(body, pdf.dict(xo.dict["Resources"]) ?: resources, inner, out, depth + 1)
                        }
                        "Image" -> images++
                    }
                }
                "BI" -> {
                    // An inline picture: skip its data (to "EI" on its own).
                    images++
                    val idAt = indexOfWord(content, "ID", lx.pos)
                    if (idAt >= 0) {
                        val ei = indexOfWord(content, "EI", idAt + 3)
                        lx.pos = if (ei >= 0) ei + 2 else content.size
                    }
                }
            }
            args.clear()
        }
    }

    private fun indexOfWord(b: ByteArray, w: String, from: Int): Int {
        var i = from
        while (i + w.length <= b.size) {
            if (b[i] == w[0].code.toByte() && b[i + 1] == w[1].code.toByte()) {
                val before = if (i == 0) 32 else b[i - 1].toInt() and 0xFF
                val after = if (i + 2 >= b.size) 32 else b[i + 2].toInt() and 0xFF
                if ((before == 32 || before == 10 || before == 13 || before == 9) && (after == 32 || after == 10 || after == 13 || after == 9 || i + 2 >= b.size)) return i
            }
            i++
        }
        return -1
    }
}
