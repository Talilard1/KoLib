package dev.kobolibrary.app.convert

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A PDF's objects: names, strings (as bytes), references, arrays, dictionaries and streams. */
internal class PdfName(val name: String) {
    override fun equals(other: Any?) = other is PdfName && other.name == name
    override fun hashCode() = name.hashCode()
    override fun toString() = "/$name"
}

internal class PdfString(val bytes: ByteArray) {
    /** As text: UTF-16 with a byte-order mark, UTF-8 with one, or PDF's own Latin encoding. */
    fun text(): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(CharArray(bytes.size) { i -> PdfEncodings.pdfDoc(bytes[i].toInt() and 0xFF) })
    }
}

internal data class PdfRef(val num: Int, val gen: Int)

internal class PdfStream(val dict: Map<String, Any?>, val raw: ByteArray, val ref: PdfRef?)

internal class PdfKeyword(val word: String) {
    override fun toString() = word
}

/** Reads PDF syntax from bytes. */
internal class PdfLexer(val b: ByteArray, var pos: Int = 0) {

    fun isWhite(c: Int) = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32
    fun isDelim(c: Int) = c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code || c == '['.code || c == ']'.code ||
        c == '{'.code || c == '}'.code || c == '/'.code || c == '%'.code

    fun skip() {
        while (pos < b.size) {
            val c = b[pos].toInt() and 0xFF
            if (isWhite(c)) pos++
            else if (c == '%'.code) {
                while (pos < b.size && b[pos].toInt() != 10 && b[pos].toInt() != 13) pos++
            } else break
        }
    }

    /** The next token: a number, keyword, name, string, or one of the brackets ("<<", "[" ...). Null at the end. */
    fun token(): Any? {
        skip()
        if (pos >= b.size) return null
        val c = b[pos].toInt() and 0xFF
        when (c) {
            '/'.code -> {
                pos++
                val sb = StringBuilder()
                while (pos < b.size) {
                    val d = b[pos].toInt() and 0xFF
                    if (isWhite(d) || isDelim(d)) break
                    if (d == '#'.code && pos + 2 < b.size) {
                        val hex = String(b, pos + 1, 2, Charsets.ISO_8859_1).toIntOrNull(16)
                        if (hex != null) {
                            sb.append(hex.toChar())
                            pos += 3
                            continue
                        }
                    }
                    sb.append(d.toChar())
                    pos++
                }
                return PdfName(sb.toString())
            }
            '('.code -> return PdfString(literal())
            '<'.code -> {
                if (pos + 1 < b.size && b[pos + 1] == '<'.code.toByte()) {
                    pos += 2
                    return DICT_START
                }
                pos++
                val out = ByteArrayOutputStream()
                var hi = -1
                while (pos < b.size && b[pos] != '>'.code.toByte()) {
                    val d = Character.digit((b[pos].toInt() and 0xFF).toChar(), 16)
                    pos++
                    if (d < 0) continue
                    if (hi < 0) hi = d else {
                        out.write(hi * 16 + d)
                        hi = -1
                    }
                }
                if (hi >= 0) out.write(hi * 16)
                pos++
                return PdfString(out.toByteArray())
            }
            '>'.code -> {
                pos += if (pos + 1 < b.size && b[pos + 1] == '>'.code.toByte()) 2 else 1
                return DICT_END
            }
            '['.code -> { pos++; return ARRAY_START }
            ']'.code -> { pos++; return ARRAY_END }
            '{'.code, '}'.code -> { pos++; return PdfKeyword(c.toChar().toString()) }
            ')'.code -> { pos++; return token() }
        }
        val start = pos
        while (pos < b.size) {
            val d = b[pos].toInt() and 0xFF
            if (isWhite(d) || isDelim(d)) break
            pos++
        }
        if (pos == start) {
            pos++
            return token()
        }
        val word = String(b, start, pos - start, Charsets.ISO_8859_1)
        val first = word[0]
        if (first.isDigit() || first == '-' || first == '+' || first == '.') {
            word.toLongOrNull()?.let { return it }
            word.toDoubleOrNull()?.let { return it }
            // Malformed numbers ("--5", "0.0.1"): as near as they can be read.
            Regex("""[-+]?\d*\.?\d+""").find(word)?.value?.toDoubleOrNull()?.let { return it }
            return 0L
        }
        return when (word) {
            "true" -> true
            "false" -> false
            "null" -> NULL
            else -> PdfKeyword(word)
        }
    }

    private fun literal(): ByteArray {
        pos++
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < b.size) {
            val c = b[pos].toInt() and 0xFF
            pos++
            when (c) {
                '('.code -> {
                    depth++
                    out.write(c)
                }
                ')'.code -> {
                    depth--
                    if (depth == 0) return out.toByteArray()
                    out.write(c)
                }
                '\\'.code -> {
                    if (pos >= b.size) break
                    val e = b[pos].toInt() and 0xFF
                    pos++
                    when (e) {
                        'n'.code -> out.write(10)
                        'r'.code -> out.write(13)
                        't'.code -> out.write(9)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        13 -> if (pos < b.size && b[pos].toInt() == 10) pos++
                        10 -> Unit
                        in '0'.code..'7'.code -> {
                            var v = e - '0'.code
                            var k = 0
                            while (k < 2 && pos < b.size && (b[pos].toInt() and 0xFF) in '0'.code..'7'.code) {
                                v = v * 8 + (b[pos].toInt() - '0'.code)
                                pos++
                                k++
                            }
                            out.write(v and 0xFF)
                        }
                        else -> out.write(e)
                    }
                }
                else -> out.write(c)
            }
        }
        return out.toByteArray()
    }

    /** The next whole object (resolving "n g R" into a reference). */
    fun obj(): Any? {
        val t = token()
        return when {
            t === NULL -> null
            t === DICT_START -> dict()
            t === ARRAY_START -> {
                val list = ArrayList<Any?>()
                while (true) {
                    val save = pos
                    val n = token()
                    if (n === ARRAY_END || n == null) break
                    pos = save
                    list += obj()
                }
                list
            }
            t is Long -> {
                // "12 0 R": a reference.
                val save = pos
                val g = token()
                if (g is Long) {
                    val r = token()
                    if (r is PdfKeyword && r.word == "R") return PdfRef(t.toInt(), g.toInt())
                }
                pos = save
                t
            }
            else -> t
        }
    }

    fun dict(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        while (true) {
            val k = token()
            if (k === DICT_END || k == null) break
            if (k !is PdfName) continue
            val save = pos
            val v = token()
            if (v === DICT_END) break
            pos = save
            map[k.name] = obj()
        }
        return map
    }

    companion object {
        val DICT_START = Any()
        val DICT_END = Any()
        val ARRAY_START = Any()
        val ARRAY_END = Any()
        /** PDF's null (as a token, so it isn't taken for the end of the file). */
        val NULL = Any()
    }
}

/** A PDF file: its objects (through its cross-reference tables or, if those are broken, by searching for them), decrypted if need be. */
internal class PdfFile(val data: ByteArray) {

    private val offsets = HashMap<Int, Int>()
    private val inStream = HashMap<Int, Pair<Int, Int>>()
    private val cache = HashMap<Int, Any?>()
    val trailer = LinkedHashMap<String, Any?>()
    private var crypt: PdfCrypt? = null

    init {
        if (String(data, 0, minOf(data.size, 1024), Charsets.ISO_8859_1).indexOf("%PDF") < 0) throw ConversionException("This isn't a PDF file.")
        val ok = runCatching { readXref() }.getOrDefault(false)
        if (!ok || trailer["Root"] == null) scan()
        val enc = trailer["Encrypt"]
        if (enc != null) {
            val dict = resolve(enc) as? Map<*, *> ?: throw ConversionException("This PDF's lock can't be read.")
            @Suppress("UNCHECKED_CAST")
            crypt = PdfCrypt(dict as Map<String, Any?>, (trailer["ID"] as? List<*>)?.firstOrNull() as? PdfString, this)
            if (!crypt!!.opened) throw ConversionException("This PDF needs a password to open, so it can't be converted.")
        }
    }

    private fun readXref(): Boolean {
        val tail = String(data, maxOf(0, data.size - 2048), minOf(2048, data.size), Charsets.ISO_8859_1)
        val sx = tail.lastIndexOf("startxref")
        if (sx < 0) return false
        var at = Regex("""\d+""").find(tail, sx + 9)?.value?.toIntOrNull() ?: return false
        val seen = HashSet<Int>()
        var first = true
        while (at in 0 until data.size && seen.add(at)) {
            val lx = PdfLexer(data, at)
            val t = lx.token()
            val section: Map<String, Any?>
            if (t is PdfKeyword && t.word == "xref") {
                while (true) {
                    val save = lx.pos
                    val a = lx.token()
                    if (a !is Long) {
                        lx.pos = save
                        break
                    }
                    val n = lx.token() as? Long ?: break
                    for (k in 0 until n) {
                        val off = lx.token() as? Long ?: break
                        lx.token()
                        val kind = lx.token() as? PdfKeyword
                        val num = (a + k).toInt()
                        if (kind?.word == "n" && num !in offsets && num !in inStream) offsets[num] = off.toInt()
                    }
                }
                val tr = lx.token()
                if (!(tr is PdfKeyword && tr.word == "trailer")) return false
                section = lx.dict()
                (section["XRefStm"] as? Long)?.let { s -> runCatching { xrefStream(s.toInt()) } }
            } else {
                section = xrefStream(at) ?: return false
            }
            if (first) {
                trailer.putAll(section)
                first = false
            } else for ((k, v) in section) trailer.putIfAbsent(k, v)
            at = (section["Prev"] as? Long)?.toInt() ?: break
        }
        return offsets.isNotEmpty() || inStream.isNotEmpty()
    }

    private fun xrefStream(at: Int): Map<String, Any?>? {
        val lx = PdfLexer(data, at)
        lx.token(); lx.token()
        val kw = lx.token() as? PdfKeyword
        if (kw?.word != "obj") return null
        val d = lx.obj() as? Map<*, *> ?: return null
        @Suppress("UNCHECKED_CAST")
        val dict = d as Map<String, Any?>
        if ((dict["Type"] as? PdfName)?.name != "XRef") return null
        val raw = streamBytes(lx, dict) ?: return null
        val bytes = PdfFilters.decode(raw, dict, this) ?: return null
        val w = (dict["W"] as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: return null
        val size = (dict["Size"] as? Long)?.toInt() ?: 0
        val index = (dict["Index"] as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: listOf(0, size)
        val row = w.sum()
        var p = 0
        fun field(width: Int, default: Int): Int {
            if (width == 0) return default
            var v = 0
            for (k in 0 until width) v = (v shl 8) or (bytes.getOrElse(p++) { 0 }.toInt() and 0xFF)
            return v
        }
        var k = 0
        while (k + 1 < index.size) {
            val start = index[k]
            val count = index[k + 1]
            for (j in 0 until count) {
                if (p + row > bytes.size) break
                val type = field(w[0], 1)
                val f2 = field(w[1], 0)
                val f3 = field(w.getOrElse(2) { 0 }, 0)
                val num = start + j
                if (num in offsets || num in inStream) continue
                when (type) {
                    1 -> offsets[num] = f2
                    2 -> inStream[num] = f2 to f3
                }
            }
            k += 2
        }
        return dict
    }

    /** With no usable tables: every "n g obj" in the file, and the last trailer (or the catalogue). */
    private fun scan() {
        val text = String(data, Charsets.ISO_8859_1)
        Regex("""(?<![0-9])(\d{1,7})\s+(\d{1,5})\s+obj\b""").findAll(text).forEach { m ->
            offsets[m.groupValues[1].toInt()] = m.range.first
        }
        val t = text.lastIndexOf("trailer")
        if (t >= 0) runCatching {
            val lx = PdfLexer(data, t + 7)
            trailer.putAll(lx.obj() as Map<String, Any?>)
        }
        if (trailer["Root"] == null) {
            for (num in offsets.keys) {
                val o = runCatching { get(num) }.getOrNull()
                if (o is Map<*, *> && (o["Type"] as? PdfName)?.name == "Catalog") {
                    trailer["Root"] = PdfRef(num, 0)
                    break
                }
            }
        }
        if (trailer["Root"] == null) throw ConversionException("This PDF is damaged.")
    }

    fun streamBytes(lx: PdfLexer, dict: Map<String, Any?>): ByteArray? {
        val kw = lx.token() as? PdfKeyword ?: return null
        if (kw.word != "stream") return null
        var p = lx.pos
        if (p < data.size && data[p].toInt() == 13) p++
        if (p < data.size && data[p].toInt() == 10) p++
        val len = (resolve(dict["Length"]) as? Long)?.toInt()
        if (len != null && len >= 0 && p + len <= data.size) {
            // Trust the length if "endstream" follows it.
            val after = String(data, p + len, minOf(20, data.size - p - len), Charsets.ISO_8859_1)
            if (after.trimStart().startsWith("endstream")) return data.copyOfRange(p, p + len)
        }
        val end = indexOf("endstream".toByteArray(), p)
        if (end < 0) return null
        var e = end
        if (e > p && data[e - 1].toInt() == 10) e--
        if (e > p && data[e - 1].toInt() == 13) e--
        return data.copyOfRange(p, e)
    }

    private fun indexOf(needle: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - needle.size) {
            for (j in needle.indices) if (data[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Object [num], read and (if need be) decrypted. */
    fun get(num: Int): Any? {
        if (cache.containsKey(num)) return cache[num]
        cache[num] = null
        val v = inStream[num]?.let { (stm, idx) -> fromObjectStream(stm, idx, num) } ?: offsets[num]?.let { at ->
            val lx = PdfLexer(data, at)
            lx.token(); lx.token()
            val kw = lx.token()
            if (!(kw is PdfKeyword && kw.word == "obj")) return@let null
            val o = lx.obj()
            if (o is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val dict = o as Map<String, Any?>
                val save = lx.pos
                val raw = streamBytes(lx, dict)
                if (raw != null) {
                    val ref = PdfRef(num, 0)
                    val plain = crypt?.let { c -> if ((dict["Type"] as? PdfName)?.name == "XRef") raw else c.stream(raw, ref, dict) } ?: raw
                    return@let PdfStream(crypt?.decryptStrings(dict, ref) as? Map<String, Any?> ?: dict, plain, ref)
                }
                lx.pos = save
            }
            crypt?.decryptStrings(o, PdfRef(num, 0)) ?: o
        }
        cache[num] = v
        return v
    }

    private fun fromObjectStream(stm: Int, index: Int, num: Int): Any? {
        val s = get(stm) as? PdfStream ?: return null
        val bytes = PdfFilters.decode(s.raw, s.dict, this) ?: return null
        val n = (s.dict["N"] as? Long)?.toInt() ?: return null
        val first = (s.dict["First"] as? Long)?.toInt() ?: return null
        val lx = PdfLexer(bytes)
        var target = -1
        for (k in 0 until n) {
            val objNum = (lx.token() as? Long)?.toInt() ?: break
            val off = (lx.token() as? Long)?.toInt() ?: break
            if (objNum == num || k == index && target < 0) target = off
        }
        if (target < 0) return null
        return PdfLexer(bytes, first + target).obj()
    }

    fun resolve(o: Any?): Any? {
        var v = o
        var guard = 0
        while (v is PdfRef && guard++ < 32) v = get(v.num)
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun dict(o: Any?): Map<String, Any?>? = when (val r = resolve(o)) {
        is Map<*, *> -> r as Map<String, Any?>
        is PdfStream -> r.dict
        else -> null
    }

    fun list(o: Any?): List<Any?>? = resolve(o) as? List<Any?>

    fun stream(o: Any?): ByteArray? = (resolve(o) as? PdfStream)?.let { PdfFilters.decode(it.raw, it.dict, this) }

    fun number(o: Any?): Double? = when (val r = resolve(o)) {
        is Long -> r.toDouble()
        is Double -> r
        else -> null
    }

    val catalog: Map<String, Any?> get() = dict(trailer["Root"]) ?: emptyMap()

    /** The pages, in order, each with what it inherits (resources, size). */
    val pages: List<Map<String, Any?>> by lazy {
        val out = ArrayList<Map<String, Any?>>()
        val seen = HashSet<Any?>()
        fun walk(node: Any?, inherited: Map<String, Any?>, depth: Int) {
            if (depth > 64 || !seen.add(node as? PdfRef ?: node)) return
            val d = dict(node) ?: return
            val here = LinkedHashMap(inherited)
            for (k in listOf("Resources", "MediaBox", "CropBox", "Rotate")) d[k]?.let { here[k] = it }
            val kids = list(d["Kids"])
            if ((d["Type"] as? PdfName)?.name == "Pages" || kids != null) {
                kids?.forEach { walk(it, here, depth + 1) }
            } else {
                val page = LinkedHashMap(d)
                for ((k, v) in here) page.putIfAbsent(k, v)
                page["#ref"] = node
                out += page
            }
        }
        walk(catalog["Pages"], emptyMap(), 0)
        out
    }

    /** The document's own details: from its information dictionary, or its XMP metadata. */
    fun info(): Map<String, String> {
        val out = HashMap<String, String>()
        dict(trailer["Info"])?.forEach { (k, v) -> (resolve(v) as? PdfString)?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { out[k] = it } }
        runCatching {
            val xmp = stream(catalog["Metadata"])?.let { String(it, Charsets.UTF_8) } ?: return@runCatching
            fun tag(name: String): String? = Regex("""<dc:$name>\s*(?:<rdf:(?:Alt|Seq|Bag)>\s*)?(?:<rdf:li[^>]*>)?([^<]+)""").find(xmp)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            tag("title")?.let { out.putIfAbsent("Title", HtmlSoup.decode(it)) }
            tag("creator")?.let { out.putIfAbsent("Author", HtmlSoup.decode(it)) }
            tag("description")?.let { out.putIfAbsent("Subject", HtmlSoup.decode(it)) }
            tag("language")?.let { out.putIfAbsent("Lang", it) }
        }
        (resolve(catalog["Lang"]) as? PdfString)?.text()?.let { out.putIfAbsent("Lang", it) }
        return out
    }

    /** The outline (bookmarks): titles with the page each points to, and how deep. */
    fun outline(): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        val pageIndex = HashMap<Any?, Int>()
        pages.forEachIndexed { i, p -> pageIndex[p["#ref"]] = i }
        val names = HashMap<String, Any?>()
        // Named destinations, old style and new.
        dict(catalog["Dests"])?.forEach { (k, v) -> names[k] = v }
        fun nameTree(node: Any?, depth: Int) {
            if (depth > 20) return
            val d = dict(node) ?: return
            list(d["Names"])?.chunked(2)?.forEach { pair -> ((resolve(pair[0]) as? PdfString)?.text())?.let { names[it] = pair.getOrNull(1) } }
            list(d["Kids"])?.forEach { nameTree(it, depth + 1) }
        }
        nameTree(dict(catalog["Names"])?.get("Dests"), 0)
        fun pageOf(dest: Any?): Int? {
            var d = resolve(dest)
            if (d is PdfString) d = resolve(names[d.text()])
            if (d is PdfName) d = resolve(names[d.name])
            if (d is Map<*, *>) d = resolve(d["D"])
            val first = (d as? List<*>)?.firstOrNull() ?: return null
            return pageIndex[first] ?: (first as? Long)?.toInt()
        }
        val seen = HashSet<Any?>()
        fun walk(node: Any?, depth: Int) {
            var cur = node
            var guard = 0
            while (cur != null && guard++ < 5000 && seen.add(cur)) {
                val d = dict(cur) ?: break
                val title = (resolve(d["Title"]) as? PdfString)?.text()?.trim().orEmpty()
                val page = pageOf(d["Dest"]) ?: dict(d["A"])?.let { a -> pageOf(a["D"]) }
                if (title.isNotEmpty() && page != null) out += Triple(title, page, depth)
                d["First"]?.let { walk(it, depth + 1) }
                cur = d["Next"]
            }
        }
        dict(catalog["Outlines"])?.get("First")?.let { walk(it, 0) }
        return out
    }
}

/** Undoing the "standard" lock PDFs have, when it has no user password (only limits on printing and copying). */
internal class PdfCrypt(private val d: Map<String, Any?>, id: PdfString?, private val pdf: PdfFile) {
    private val v = (d["V"] as? Long)?.toInt() ?: 0
    private val r = (d["R"] as? Long)?.toInt() ?: 2
    private val lengthBytes = ((d["Length"] as? Long)?.toInt() ?: 40) / 8
    private val o = (pdf.resolve(d["O"]) as? PdfString)?.bytes ?: ByteArray(32)
    private val u = (pdf.resolve(d["U"]) as? PdfString)?.bytes ?: ByteArray(32)
    private val p = (d["P"] as? Long)?.toInt() ?: 0
    private val idBytes = id?.bytes ?: ByteArray(0)
    private val encryptMetadata = d["EncryptMetadata"] != false
    private var aes = false
    private var key: ByteArray? = null

    /** The padding the standard lock's keys start from. */
    private val pad = byteArrayOf(
        0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
        0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(), 0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A,
    )

    val opened: Boolean

    init {
        if ((d["Filter"] as? PdfName)?.name != "Standard") throw ConversionException("This PDF is locked in a way that can't be opened.")
        if (v >= 4) {
            val cf = pdf.dict(d["CF"])
            val stmF = (d["StmF"] as? PdfName)?.name ?: "Identity"
            val method = (pdf.dict(cf?.get(stmF))?.get("CFM") as? PdfName)?.name
            aes = method == "AESV2" || method == "AESV3"
        }
        key = if (r >= 5) key256() else key128()
        opened = key != null
    }


    /** The key for the older locks, with an empty password; checked against the file's U entry. */
    private fun key128(): ByteArray? {
        val n = if (r == 2) 5 else lengthBytes.coerceIn(5, 16)
        val md = MessageDigest.getInstance("MD5")
        md.update(pad)
        md.update(o.copyOf(32))
        md.update(byteArrayOf(p.toByte(), (p shr 8).toByte(), (p shr 16).toByte(), (p shr 24).toByte()))
        md.update(idBytes)
        if (r >= 4 && !encryptMetadata) md.update(byteArrayOf(-1, -1, -1, -1))
        var k = md.digest()
        if (r >= 3) repeat(50) { k = MessageDigest.getInstance("MD5").digest(k.copyOf(n)) }
        k = k.copyOf(n)
        // Check: the U entry, worked out from this key, matches.
        val check = if (r == 2) rc4(k, pad) else {
            val m = MessageDigest.getInstance("MD5")
            m.update(pad)
            m.update(idBytes)
            var x = rc4(k, m.digest())
            for (i in 1..19) x = rc4(ByteArray(k.size) { j -> (k[j].toInt() xor i).toByte() }, x)
            x
        }
        val cmp = if (r == 2) 32 else 16
        return if (u.size >= cmp && check.copyOf(cmp).contentEquals(u.copyOf(cmp))) k else k.takeIf { u.isEmpty() }
    }

    /** The key for AES-256 locks (revisions 5 and 6), with an empty password. */
    private fun key256(): ByteArray? {
        val ue = (pdf.resolve(d["UE"]) as? PdfString)?.bytes ?: return null
        if (u.size < 48) return null
        val validationSalt = u.copyOfRange(32, 40)
        val keySalt = u.copyOfRange(40, 48)
        val check = if (r == 5) sha256(validationSalt) else hash2b(ByteArray(0), validationSalt, ByteArray(0))
        if (!check.contentEquals(u.copyOf(32))) return null
        val inter = if (r == 5) sha256(keySalt) else hash2b(ByteArray(0), keySalt, ByteArray(0))
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(inter, "AES"), IvParameterSpec(ByteArray(16)))
        aes = true
        return c.doFinal(ue.copyOf(32))
    }

    private fun sha256(salt: ByteArray) = MessageDigest.getInstance("SHA-256").digest(salt)

    /** ISO 32000-2's hash for revision 6. */
    private fun hash2b(password: ByteArray, salt: ByteArray, udata: ByteArray): ByteArray {
        var k = MessageDigest.getInstance("SHA-256").digest(password + salt + udata)
        var round = 0
        while (true) {
            val k1 = ByteArray(0).let { var acc = it; repeat(64) { acc += password + k + udata }; acc }
            val c = Cipher.getInstance("AES/CBC/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.copyOf(16), "AES"), IvParameterSpec(k.copyOfRange(16, 32)))
            val e = c.doFinal(k1)
            val mod = e.copyOf(16).fold(0) { acc, b -> (acc + (b.toInt() and 0xFF)) % 3 }
            k = MessageDigest.getInstance(when (mod) { 0 -> "SHA-256"; 1 -> "SHA-384"; else -> "SHA-512" }).digest(e)
            round++
            if (round >= 64 && (e.last().toInt() and 0xFF) <= round - 32) break
        }
        return k.copyOf(32)
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        var i = 0
        j = 0
        return ByteArray(data.size) { k ->
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
            (data[k].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
        }
    }

    private fun objectKey(ref: PdfRef): ByteArray {
        val k = key!!
        if (r >= 5) return k
        val md = MessageDigest.getInstance("MD5")
        md.update(k)
        md.update(byteArrayOf(ref.num.toByte(), (ref.num shr 8).toByte(), (ref.num shr 16).toByte(), ref.gen.toByte(), (ref.gen shr 8).toByte()))
        if (aes) md.update("sAlT".toByteArray())
        return md.digest().copyOf(minOf(k.size + 5, 16))
    }

    fun decrypt(data: ByteArray, ref: PdfRef): ByteArray {
        val k = objectKey(ref)
        if (!aes) return rc4(k, data)
        if (data.size < 32) return ByteArray(0)
        return runCatching {
            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(data.copyOf(16)))
            c.doFinal(data, 16, data.size - 16)
        }.getOrElse { data }
    }

    fun stream(raw: ByteArray, ref: PdfRef, dict: Map<String, Any?>): ByteArray {
        if ((dict["Type"] as? PdfName)?.name == "Metadata" && !encryptMetadata) return raw
        return decrypt(raw, ref)
    }

    /** Strings in [o], decrypted. */
    fun decryptStrings(o: Any?, ref: PdfRef): Any? = when (o) {
        is PdfString -> PdfString(decrypt(o.bytes, ref))
        is List<*> -> o.map { decryptStrings(it, ref) }
        is Map<*, *> -> o.entries.associate { (k, v) -> k as String to decryptStrings(v, ref) }
        else -> o
    }
}

/** Undoing a stream's compression and encodings. */
internal object PdfFilters {
    fun decode(raw: ByteArray, dict: Map<String, Any?>, pdf: PdfFile?): ByteArray? {
        val f = pdf?.resolve(dict["Filter"]) ?: dict["Filter"]
        val filters = when (f) {
            is PdfName -> listOf(f.name)
            is List<*> -> f.mapNotNull { (pdf?.resolve(it) ?: it as? PdfName)?.let { n -> (n as? PdfName)?.name } }
            else -> emptyList()
        }
        val parmsRaw = pdf?.resolve(dict["DecodeParms"]) ?: dict["DecodeParms"]
        var data = raw
        filters.forEachIndexed { i, name ->
            @Suppress("UNCHECKED_CAST")
            val parms = (if (parmsRaw is List<*>) pdf?.resolve(parmsRaw.getOrNull(i)) ?: parmsRaw.getOrNull(i) else parmsRaw) as? Map<String, Any?>
            data = when (name) {
                "FlateDecode", "Fl" -> predict(inflate(data), parms)
                "LZWDecode", "LZW" -> predict(lzw(data, (parms?.get("EarlyChange") as? Long ?: 1L).toInt()), parms)
                "ASCIIHexDecode", "AHx" -> hex(data)
                "ASCII85Decode", "A85" -> a85(data)
                "RunLengthDecode", "RL" -> runLength(data)
                else -> return null
            }
        }
        return data
    }

    fun inflate(b: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(b)
        val out = ByteArrayOutputStream(b.size * 3)
        val buf = ByteArray(64 * 1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break
                }
                out.write(buf, 0, n)
            }
        } catch (e: Exception) {
            // Damaged at the end: what came out so far.
        } finally {
            inf.end()
        }
        if (out.size() == 0 && b.isNotEmpty()) {
            // Raw deflate with no zlib header.
            val raw = Inflater(true)
            raw.setInput(b)
            try {
                while (!raw.finished()) {
                    val n = raw.inflate(buf)
                    if (n == 0) break
                    out.write(buf, 0, n)
                }
            } catch (_: Exception) {
            } finally {
                raw.end()
            }
        }
        return out.toByteArray()
    }

    private fun predict(b: ByteArray, parms: Map<String, Any?>?): ByteArray {
        val predictor = (parms?.get("Predictor") as? Long)?.toInt() ?: 1
        if (predictor < 10) return b
        val colors = (parms?.get("Colors") as? Long)?.toInt() ?: 1
        val bpc = (parms?.get("BitsPerComponent") as? Long)?.toInt() ?: 8
        val columns = (parms?.get("Columns") as? Long)?.toInt() ?: 1
        val bpp = maxOf(1, colors * bpc / 8)
        val rowLen = (colors * bpc * columns + 7) / 8
        val out = ByteArrayOutputStream(b.size)
        var prev = ByteArray(rowLen)
        var p = 0
        while (p < b.size) {
            val type = b[p++].toInt() and 0xFF
            val row = ByteArray(rowLen)
            for (i in 0 until rowLen) row[i] = if (p + i < b.size) b[p + i] else 0
            p += rowLen
            for (i in 0 until rowLen) {
                val left = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
                val up = prev[i].toInt() and 0xFF
                val upLeft = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                val x = row[i].toInt() and 0xFF
                row[i] = when (type) {
                    1 -> x + left
                    2 -> x + up
                    3 -> x + (left + up) / 2
                    4 -> {
                        val pa = Math.abs(up - upLeft)
                        val pb = Math.abs(left - upLeft)
                        val pc = Math.abs(left + up - 2 * upLeft)
                        x + if (pa <= pb && pa <= pc) left else if (pb <= pc) up else upLeft
                    }
                    else -> x
                }.toByte()
            }
            out.write(row)
            prev = row
        }
        return out.toByteArray()
    }

    private fun hex(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var hi = -1
        for (x in b) {
            if (x == '>'.code.toByte()) break
            val d = Character.digit((x.toInt() and 0xFF).toChar(), 16)
            if (d < 0) continue
            if (hi < 0) hi = d else {
                out.write(hi * 16 + d)
                hi = -1
            }
        }
        if (hi >= 0) out.write(hi * 16)
        return out.toByteArray()
    }

    private fun a85(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val tuple = IntArray(5)
        var n = 0
        var i = 0
        val start = String(b, 0, minOf(2, b.size), Charsets.ISO_8859_1)
        if (start == "<~") i = 2
        while (i < b.size) {
            val c = b[i++].toInt() and 0xFF
            if (c == '~'.code) break
            if (c == 'z'.code && n == 0) {
                repeat(4) { out.write(0) }
                continue
            }
            if (c < '!'.code || c > 'u'.code) continue
            tuple[n++] = c - '!'.code
            if (n == 5) {
                var v = 0L
                for (k in 0 until 5) v = v * 85 + tuple[k]
                for (k in 3 downTo 0) out.write(((v shr (8 * k)) and 0xFF).toInt())
                n = 0
            }
        }
        if (n > 1) {
            for (k in n until 5) tuple[k] = 84
            var v = 0L
            for (k in 0 until 5) v = v * 85 + tuple[k]
            for (k in 3 downTo 5 - n) out.write(((v shr (8 * k)) and 0xFF).toInt())
        }
        return out.toByteArray()
    }

    private fun runLength(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < b.size) {
            val len = b[i++].toInt() and 0xFF
            when {
                len == 128 -> break
                len < 128 -> { out.write(b, i, minOf(len + 1, b.size - i)); i += len + 1 }
                else -> { if (i < b.size) repeat(257 - len) { out.write(b[i].toInt()) }; i++ }
            }
        }
        return out.toByteArray()
    }

    private fun lzw(b: ByteArray, early: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val dict = ArrayList<ByteArray>(4096)
        fun reset() {
            dict.clear()
            for (i in 0 until 256) dict += byteArrayOf(i.toByte())
            dict += ByteArray(0)
            dict += ByteArray(0)
        }
        reset()
        var bits = 9
        var buf = 0L
        var have = 0
        var prev: ByteArray? = null
        var i = 0
        while (true) {
            while (have < bits && i < b.size) {
                buf = (buf shl 8) or (b[i++].toLong() and 0xFF)
                have += 8
            }
            if (have < bits) break
            val code = ((buf shr (have - bits)) and ((1L shl bits) - 1)).toInt()
            have -= bits
            when {
                code == 256 -> { reset(); bits = 9; prev = null; continue }
                code == 257 -> break
            }
            val entry = when {
                code < dict.size -> dict[code]
                prev != null -> prev + prev[0]
                else -> break
            }
            out.write(entry)
            if (prev != null && dict.size < 4096) dict += prev + entry[0]
            prev = entry
            if (dict.size + early >= (1 shl bits) && bits < 12) bits++
        }
        return out.toByteArray()
    }
}
