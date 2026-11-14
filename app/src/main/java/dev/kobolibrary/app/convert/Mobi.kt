package dev.kobolibrary.app.convert

import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.TocNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/**
 * Kindle books (MOBI, AZW, AZW3, PRC) as EPUBs: the older kind (MOBI 6, one long page of HTML split at its
 * page breaks) and the newer one (KF8, the pages of the EPUB it was made from), with their details,
 * cover, pictures, fonts, styles, contents and links. Books locked with DRM can't be opened.
 */
object MobiConverter {

    fun convert(bytes: ByteArray, fileName: String, target: File) {
        val book = MobiBook(bytes)
        book.toEpub(TextConverter.titleFromName(fileName)).write(target)
    }
}

/** The records of a Palm database (the container a Kindle book is). */
internal class Pdb(val data: ByteArray) {
    val name: String
    val type: String
    val offsets: IntArray

    init {
        if (data.size < 78) throw ConversionException("This file is too short to be a Kindle book.")
        name = String(data, 0, 32, Charsets.ISO_8859_1).substringBefore('\u0000')
        type = String(data, 60, 8, Charsets.ISO_8859_1)
        val n = u16(data, 76)
        if (n == 0 || 78 + n * 8 > data.size) throw ConversionException("This Kindle book is damaged.")
        offsets = IntArray(n) { i -> u32(data, 78 + i * 8).toInt() }
    }

    val count: Int get() = offsets.size

    fun record(i: Int): ByteArray {
        if (i < 0 || i >= offsets.size) return ByteArray(0)
        val start = offsets[i]
        val end = if (i + 1 < offsets.size) offsets[i + 1] else data.size
        if (start < 0 || end > data.size || end < start) return ByteArray(0)
        return data.copyOfRange(start, end)
    }

    companion object {
        fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
        fun u32(b: ByteArray, o: Int): Long = ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)
    }
}

/** A Kindle book's header (record 0 of its part) and what it says about the rest. */
internal class MobiHeader(val pdb: Pdb, val base: Int) {
    val r0 = pdb.record(base)
    val compression = Pdb.u16(r0, 0)
    val textLength = Pdb.u32(r0, 4).toInt()
    val textRecords = Pdb.u16(r0, 8)
    val encryption = Pdb.u16(r0, 12)
    val hasMobi = r0.size >= 20 && String(r0, 16, 4, Charsets.ISO_8859_1) == "MOBI"
    val headerLength = if (hasMobi) Pdb.u32(r0, 20).toInt() else 0
    private fun field(off: Int): Long = if (hasMobi && off + 4 <= 16 + headerLength && off + 4 <= r0.size) Pdb.u32(r0, off) else 0xFFFFFFFFL
    val codepage = field(28).toInt()
    val version = field(36).toInt()
    val firstNonBook = field(80)
    val firstImage = field(108).let { if (it == 0xFFFFFFFFL) -1 else (it + base).toInt() }
    val huffRecord = field(112).let { if (it == 0xFFFFFFFFL) -1 else (it + base).toInt() }
    val huffCount = field(116).toInt()
    val exthFlags = field(128)
    val fdst = if (headerLength >= 0xC4 - 16) field(0xC0).let { if (it == 0xFFFFFFFFL || it == 0L) -1 else (it + base).toInt() } else -1
    val extraFlags = if (headerLength >= 0xE4 - 16 && r0.size >= 0xF4) Pdb.u16(r0, 0xF2) else 0
    val ncx = field(0xF4).let { if (it == 0xFFFFFFFFL) -1 else (it + base).toInt() }
    val fragIndex = field(0xF8).let { if (it == 0xFFFFFFFFL) -1 else (it + base).toInt() }
    val skelIndex = field(0xFC).let { if (it == 0xFFFFFFFFL) -1 else (it + base).toInt() }
    val title: String = run {
        val off = field(84).toInt()
        val len = field(88).toInt()
        if (off in 0 until r0.size && len in 1..(r0.size - off)) decode(r0.copyOfRange(off, off + len)) else ""
    }

    fun decode(b: ByteArray): String = if (codepage == 1252) String(b, charset("windows-1252")) else String(b, Charsets.UTF_8)

    /** The EXTH records: what the book says about itself (author, publisher, cover...). */
    val exth: List<Pair<Int, ByteArray>> = run {
        if (exthFlags and 0x40L == 0L) return@run emptyList()
        val at = 16 + headerLength
        if (at + 12 > r0.size || String(r0, at, 4, Charsets.ISO_8859_1) != "EXTH") return@run emptyList()
        val count = Pdb.u32(r0, at + 8).toInt()
        val out = ArrayList<Pair<Int, ByteArray>>()
        var p = at + 12
        repeat(count) {
            if (p + 8 > r0.size) return@run out
            val t = Pdb.u32(r0, p).toInt()
            val l = Pdb.u32(r0, p + 4).toInt()
            if (l < 8 || p + l > r0.size) return@run out
            out += t to r0.copyOfRange(p + 8, p + l)
            p += l
        }
        out
    }

    fun exthText(t: Int): List<String> = exth.filter { it.first == t }.map { decode(it.second).trim() }.filter { it.isNotEmpty() }
    fun exthInt(t: Int): Int? = exth.firstOrNull { it.first == t && it.second.size == 4 }?.let { Pdb.u32(it.second, 0).toInt() }

    val isKf8: Boolean get() = version >= 8

    /** The book's text, every text record uncompressed and joined. */
    fun text(): ByteArray {
        if (encryption != 0) throw ConversionException("This book is locked with DRM, so it can't be opened.")
        val huff = if (compression == 0x4448) HuffCdic(pdb, huffRecord, huffCount) else null
        val out = ByteArrayOutputStream(textLength + 4096)
        for (i in 1..textRecords) {
            var rec = pdb.record(base + i)
            rec = rec.copyOf(rec.size - trailing(rec))
            when (compression) {
                1 -> out.write(rec)
                2 -> out.write(PalmDoc.decompress(rec))
                0x4448 -> out.write(huff!!.unpack(rec))
                else -> throw ConversionException("This Kindle book is compressed in a way that can't be read.")
            }
        }
        val all = out.toByteArray()
        return if (textLength in 1 until all.size) all.copyOf(textLength) else all
    }

    /** How many bytes at a text record's end aren't text (its trailing entries). */
    private fun trailing(rec: ByteArray): Int {
        var size = 0
        var flags = extraFlags shr 1
        while (flags != 0) {
            if (flags and 1 != 0) {
                var n = 0
                val end = rec.size - size
                for (k in maxOf(0, end - 4) until end) {
                    val v = rec[k].toInt() and 0xFF
                    if (v and 0x80 != 0) n = 0
                    n = (n shl 7) or (v and 0x7F)
                }
                size += n
            }
            flags = flags shr 1
        }
        if (extraFlags and 1 != 0 && rec.size - size - 1 >= 0) size += (rec[rec.size - size - 1].toInt() and 0x3) + 1
        return size.coerceIn(0, rec.size)
    }
}

/** PalmDOC's compression: a simple LZ77. */
internal object PalmDoc {
    fun decompress(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size * 2)
        val buf = ByteArray(src.size * 8 + 16)
        var o = 0
        fun put(b: Int) {
            buf[o++] = b.toByte()
        }
        var i = 0
        while (i < src.size) {
            val c = src[i++].toInt() and 0xFF
            when {
                c in 1..8 -> for (k in 0 until c) if (i < src.size) put(src[i++].toInt())
                c < 0x80 -> put(c)
                c >= 0xC0 -> {
                    put(' '.code)
                    put(c xor 0x80)
                }
                i < src.size -> {
                    val pair = (c shl 8) or (src[i++].toInt() and 0xFF)
                    val distance = (pair and 0x3FFF) shr 3
                    val length = (pair and 7) + 3
                    if (distance in 1..o) for (k in 0 until length) put(buf[o - distance].toInt())
                }
            }
        }
        out.write(buf, 0, o)
        return out.toByteArray()
    }
}

/** HUFF/CDIC compression, used by some Kindle books. */
internal class HuffCdic(pdb: Pdb, first: Int, count: Int) {
    private val dict1 = Array(256) { IntArray(3) }
    private val mincode = LongArray(33)
    private val maxcode = LongArray(33)
    private val dictionary = ArrayList<Pair<ByteArray, Boolean>?>()
    private val maxFor = LongArray(256)

    init {
        if (first < 0 || count < 2) throw ConversionException("This Kindle book's compression tables are missing.")
        val huff = pdb.record(first)
        if (String(huff, 0, 4, Charsets.ISO_8859_1) != "HUFF") throw ConversionException("This Kindle book's compression tables are damaged.")
        val off1 = Pdb.u32(huff, 8).toInt()
        val off2 = Pdb.u32(huff, 12).toInt()
        for (i in 0 until 256) {
            val v = Pdb.u32(huff, off1 + 4 * i)
            val codelen = (v and 0x1F).toInt()
            val term = (v and 0x80) != 0L
            var max = v shr 8
            max = ((max + 1) shl (32 - codelen)) - 1
            dict1[i] = intArrayOf(codelen, if (term) 1 else 0, 0)
            maxFor[i] = max
        }
        for (codelen in 1..32) {
            val mn = Pdb.u32(huff, off2 + (codelen - 1) * 8)
            val mx = Pdb.u32(huff, off2 + (codelen - 1) * 8 + 4)
            mincode[codelen] = mn shl (32 - codelen)
            maxcode[codelen] = ((mx + 1) shl (32 - codelen)) - 1
        }
        for (k in 1 until count) {
            val cdic = pdb.record(first + k)
            if (String(cdic, 0, 4, Charsets.ISO_8859_1) != "CDIC") continue
            val phrases = Pdb.u32(cdic, 8).toInt()
            val bits = Pdb.u32(cdic, 12).toInt()
            val n = minOf(1 shl bits, phrases - dictionary.size)
            for (j in 0 until n) {
                val off = Pdb.u16(cdic, 16 + 2 * j)
                val blen = Pdb.u16(cdic, 16 + off)
                val len = blen and 0x7FFF
                dictionary += cdic.copyOfRange(18 + off, minOf(cdic.size, 18 + off + len)) to (blen and 0x8000 != 0)
            }
        }
    }

    fun unpack(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        unpackInto(data, out, 0)
        return out.toByteArray()
    }

    private fun unpackInto(src: ByteArray, out: ByteArrayOutputStream, depth: Int) {
        if (depth > 32) throw ConversionException("This Kindle book's text is damaged.")
        val data = src.copyOf(src.size + 8)
        var bitsLeft = src.size.toLong() * 8
        var pos = 0
        fun q(p: Int): Long {
            var v = 0L
            for (k in 0 until 8) v = (v shl 8) or (data[p + k].toLong() and 0xFF)
            return v
        }
        var x = q(pos)
        var n = 32
        while (true) {
            if (n <= 0) {
                pos += 4
                x = q(pos)
                n += 32
            }
            val code = (x ushr n) and 0xFFFFFFFFL
            val d = dict1[(code ushr 24).toInt()]
            var codelen = d[0]
            var max = maxFor[(code ushr 24).toInt()]
            if (d[1] == 0) {
                while (codelen < 32 && code < mincode[codelen]) codelen++
                max = maxcode[codelen]
            }
            n -= codelen
            bitsLeft -= codelen
            if (bitsLeft < 0) break
            val r = ((max - code) ushr (32 - codelen)).toInt()
            val entry = dictionary.getOrNull(r) ?: break
            if (!entry.second) {
                val sub = ByteArrayOutputStream()
                dictionary[r] = null
                unpackInto(entry.first, sub, depth + 1)
                val done = sub.toByteArray()
                dictionary[r] = done to true
                out.write(done)
            } else out.write(entry.first)
        }
    }
}

/** A Kindle book's index (its contents, or KF8's page pieces): entries with their names and values. */
internal class MobiIndex(private val pdb: Pdb, private val first: Int) {

    class Entry(val name: String, val tags: Map<Int, List<Int>>)

    val entries = ArrayList<Entry>()
    /** Strings the entries point to (titles in the contents), by their offset. */
    val strings = HashMap<Int, String>()

    init {
        val header = pdb.record(first)
        if (header.size < 0xC0 || String(header, 0, 4, Charsets.ISO_8859_1) != "INDX") throw ConversionException("An index in this Kindle book is damaged.")
        val hdrLen = Pdb.u32(header, 4).toInt()
        val count = Pdb.u32(header, 24).toInt()
        val code = Pdb.u32(header, 28).toInt()
        val cncxCount = Pdb.u32(header, 52).toInt()
        // The tag table: which values each entry has.
        val tagx = hdrLen
        val tags = ArrayList<IntArray>()
        var controlBytes = 0
        if (tagx + 12 <= header.size && String(header, tagx, 4, Charsets.ISO_8859_1) == "TAGX") {
            val firstEntry = Pdb.u32(header, tagx + 4).toInt()
            controlBytes = Pdb.u32(header, tagx + 8).toInt()
            var p = tagx + 12
            while (p + 4 <= tagx + firstEntry && p + 4 <= header.size) {
                tags += intArrayOf(header[p].toInt() and 0xFF, header[p + 1].toInt() and 0xFF, header[p + 2].toInt() and 0xFF, header[p + 3].toInt() and 0xFF)
                p += 4
            }
        }
        // Strings: the records after the index's own.
        var base = 0
        for (k in 0 until cncxCount) {
            val rec = pdb.record(first + count + 1 + k)
            var p = 0
            while (p < rec.size) {
                if (rec[p].toInt() == 0) break
                val start = p
                val (used, len) = varint(rec, p)
                p += used
                if (p + len > rec.size) break
                strings[base + start] = String(rec, p, len, Charsets.UTF_8)
                p += len
            }
            base += 0x10000
        }
        for (r in 1..count) {
            val rec = pdb.record(first + r)
            if (rec.size < 0xC0 || String(rec, 0, 4, Charsets.ISO_8859_1) != "INDX") continue
            val idxt = Pdb.u32(rec, 20).toInt()
            val n = Pdb.u32(rec, 24).toInt()
            val ordt2 = ordt(rec, code)
            val positions = IntArray(n + 1) { j -> if (j < n) Pdb.u16(rec, idxt + 4 + 2 * j) else idxt }
            for (j in 0 until n) {
                val start = positions[j]
                val end = positions[j + 1]
                if (start >= rec.size) continue
                val len = rec[start].toInt() and 0xFF
                val raw = rec.copyOfRange(start + 1, minOf(rec.size, start + 1 + len))
                val name = if (ordt2 != null) raw.joinToString("") { b -> (ordt2.getOrNull(b.toInt() and 0xFF) ?: (b.toInt() and 0xFF)).toChar().toString() } else String(raw, Charsets.ISO_8859_1)
                entries += Entry(name, tagMap(rec, start + 1 + len, end, controlBytes, tags))
            }
        }
    }

    private fun ordt(rec: ByteArray, code: Int): IntArray? {
        if (rec.size < 0xB8) return null
        val ocnt = Pdb.u32(rec, 0xA4).toInt()
        val oentries = Pdb.u32(rec, 0xA8).toInt()
        val op2 = Pdb.u32(rec, 0xB0).toInt()
        if (code != 0xFDEA && ocnt == 0 && oentries == 0) return null
        if (op2 + 4 + oentries * 2 > rec.size || String(rec, op2, 4, Charsets.ISO_8859_1) != "ORDT") return null
        return IntArray(oentries) { k -> Pdb.u16(rec, op2 + 4 + 2 * k) }
    }

    private fun tagMap(rec: ByteArray, startPos: Int, endPos: Int, controlBytes: Int, table: List<IntArray>): Map<Int, List<Int>> {
        class T(val tag: Int, val count: Int?, val bytes: Int?, val per: Int)
        val found = ArrayList<T>()
        var cbIndex = 0
        var dataStart = startPos + controlBytes
        for (t in table) {
            val (tag, per, mask0, end) = listOf(t[0], t[1], t[2], t[3])
            if (end == 1) {
                cbIndex++
                continue
            }
            if (startPos + cbIndex >= rec.size) break
            val cbyte = rec[startPos + cbIndex].toInt() and 0xFF
            var value = cbyte and mask0
            if (value == 0) continue
            if (value == mask0) {
                if (Integer.bitCount(mask0) > 1) {
                    val (used, v) = varint(rec, dataStart)
                    dataStart += used
                    found += T(tag, null, v, per)
                } else found += T(tag, 1, null, per)
            } else {
                var mask = mask0
                while (mask and 1 == 0) {
                    mask = mask shr 1
                    value = value shr 1
                }
                found += T(tag, value, null, per)
            }
        }
        val out = HashMap<Int, List<Int>>()
        for (t in found) {
            val values = ArrayList<Int>()
            if (t.count != null) {
                repeat(t.count * t.per) {
                    if (dataStart >= endPos || dataStart >= rec.size) return@repeat
                    val (used, v) = varint(rec, dataStart)
                    dataStart += used
                    values += v
                }
            } else {
                var consumed = 0
                while (consumed < (t.bytes ?: 0) && dataStart < rec.size) {
                    val (used, v) = varint(rec, dataStart)
                    dataStart += used
                    consumed += used
                    values += v
                }
            }
            out[t.tag] = values
        }
        return out
    }

    companion object {
        /** A number written in 7-bit pieces, the last with its top bit set. */
        fun varint(b: ByteArray, at: Int): Pair<Int, Int> {
            var value = 0
            var used = 0
            while (at + used < b.size) {
                val v = b[at + used].toInt() and 0xFF
                used++
                value = (value shl 7) or (v and 0x7F)
                if (v and 0x80 != 0) break
            }
            return used to value
        }
    }
}

private class Skel(val name: String, val fragments: Int, val start: Int, val length: Int)
private class Frag(val insert: Int, val selector: String, val file: Int, val start: Int, val length: Int)

/** A Kindle book read whole: its details and how to turn it into an EPUB. */
internal class MobiBook(bytes: ByteArray) {
    private val pdb = Pdb(bytes)
    private val first = MobiHeader(pdb, 0)
    /** The KF8 part of a book that has both kinds in it, if it has. */
    private val kf8: MobiHeader? = run {
        if (first.isKf8) return@run first
        val boundary = first.exthInt(121) ?: return@run null
        runCatching { MobiHeader(pdb, boundary + 1).takeIf { it.hasMobi && it.isKf8 } }.getOrNull()
    }

    init {
        if (!pdb.type.startsWith("BOOKMOBI") && !pdb.type.startsWith("TEXtREAd")) throw ConversionException("This isn't a Kindle book.")
        if (first.encryption != 0) throw ConversionException("This book is locked with DRM, so it can't be opened.")
    }

    fun metadata(fallbackTitle: String): BookMetadata {
        val h = first
        val title = h.exthText(503).firstOrNull() ?: h.title.ifBlank { pdb.name.replace('_', ' ') }.ifBlank { fallbackTitle }
        return BookMetadata(
            title = title,
            authors = h.exthText(100).flatMap { a -> a.split(';', '&').map { it.trim() } }.filter { it.isNotEmpty() }.distinct(),
            publisher = h.exthText(101).firstOrNull(),
            description = h.exthText(103).firstOrNull()?.let { d -> if (d.contains('<')) d else "<p>${HtmlSoup.escapeText(d)}</p>" },
            isbn = h.exthText(104).firstOrNull(),
            tags = h.exthText(105).flatMap { it.split(';') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            published = h.exthText(106).firstOrNull()?.take(10),
            language = h.exthText(524).firstOrNull(),
        )
    }

    /** Resource [n] (from 0), counted from the book's first picture. */
    private fun resource(h: MobiHeader, n: Int): ByteArray? = if (h.firstImage < 0) null else pdb.record(h.firstImage + n).takeIf { it.isNotEmpty() }

    private fun isPicture(b: ByteArray): Boolean = b.size > 8 && (
        (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) || (b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) ||
            (b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte()) || (b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte()) ||
            (b[0] == 'R'.code.toByte() && b[8] == 'W'.code.toByte())
        )

    fun toEpub(fallbackTitle: String): EpubOut {
        val out = EpubOut(metadata(fallbackTitle))
        val h = kf8 ?: first
        // The cover, as the book names it.
        (first.exthInt(201) ?: h.exthInt(201))?.let { off -> resource(h, off)?.takeIf { isPicture(it) }?.let { out.image("cover.jpg", it, asCover = true) } }
        if (kf8 != null) {
            try {
                Kf8(pdb, kf8, out).write()
                return out
            } catch (e: ConversionException) {
                if (kf8 === first) throw e
            } catch (e: Exception) {
                if (kf8 === first) throw ConversionException("This Kindle book couldn't be read: ${e.message}")
            }
        }
        Mobi6(first, out).write()
        return out
    }

    /** The older kind: one long page of HTML, with page breaks, links by byte position and pictures by number. */
    private inner class Mobi6(val h: MobiHeader, val out: EpubOut) {
        fun write() {
            val raw = h.text()
            // Where links and the contents point, by byte: an anchor goes in at each.
            val toc = runCatching { if (h.ncx >= 0) MobiIndex(pdb, h.ncx) else null }.getOrNull()
            val positions = sortedSetOf<Int>()
            val text0 = String(raw, Charsets.ISO_8859_1)
            Regex("""filepos\s*=\s*['"]?0*(\d+)""", RegexOption.IGNORE_CASE).findAll(text0).forEach { m -> m.groupValues[1].toIntOrNull()?.let { positions += it } }
            toc?.entries?.forEach { e -> e.tags[1]?.firstOrNull()?.let { positions += it } }
            val withAnchors = ByteArrayOutputStream(raw.size + positions.size * 24)
            var at = 0
            for (p in positions) {
                if (p <= 0 || p > raw.size) continue
                withAnchors.write(raw, at, p - at)
                withAnchors.write("<a id=\"filepos$p\"></a>".toByteArray())
                at = p
            }
            withAnchors.write(raw, at, raw.size - at)
            var html = h.decode(withAnchors.toByteArray())
            html = html.replace(Regex("""<a([^>]*?)\sfilepos\s*=\s*['"]?0*(\d+)['"]?""", RegexOption.IGNORE_CASE)) { m -> "<a${m.groupValues[1]} href=\"#filepos${m.groupValues[2]}\"" }
            // Pictures by their number.
            val pictures = HashMap<Int, String>()
            html = html.replace(Regex("""<img([^>]*?)\s(?:hi|lo)?recindex\s*=\s*['"]?0*(\d+)['"]?""", RegexOption.IGNORE_CASE)) { m ->
                val n = m.groupValues[2].toInt()
                val src = pictures.getOrPut(n) { resource(h, n - 1)?.takeIf { isPicture(it) }?.let { out.image("image%05d.jpg".format(n), it) } ?: "" }
                "<img${m.groupValues[1]} src=\"$src\""
            }
            val body = Regex("""<body[^>]*>""", RegexOption.IGNORE_CASE).find(html)?.let { html.substring(it.range.last + 1) } ?: html
            val pieces = body.substringBefore("</body>").split(Regex("""<mbp:pagebreak\s*/?>|</mbp:pagebreak>""", RegexOption.IGNORE_CASE))
            val docs = pieces.map { HtmlSoup.parse(it) }.map { d -> d.children.filterIsInstance<HtmlSoup.Element>().firstOrNull { it.name == "body" } ?: d }
                .filter { b -> b.text().isNotBlank() || b.elements().any { it.name == "img" } }
            if (docs.isEmpty()) throw ConversionException("There's no text in this Kindle book.")
            val paths = docs.indices.map { out.nextPagePath("part%04d.xhtml".format(it + 1)).substringAfterLast('/') }
            val idFile = HashMap<String, String>()
            docs.forEachIndexed { i, b -> b.elements().forEach { e -> e.attrs["id"]?.let { idFile[it] = paths[i] } } }
            if (!out.hasCover) pictures.values.firstOrNull { it.isNotEmpty() }?.let { if (docs.first().elements().any { e -> e.name == "img" } && docs.first().text().isBlank()) out.useAsCover(it) }
            docs.forEachIndexed { i, b ->
                tidy(b, i, paths, idFile)
                val title = b.elements().firstOrNull { it.name.length == 2 && it.name[0] == 'h' && it.name[1].isDigit() }?.text()?.trim()?.replace(Regex("\\s+"), " ")?.take(120)
                out.page(title?.ifEmpty { null } ?: if (i == 0) out.meta.title else null, HtmlSoup.inner(b), name = paths[i])
            }
            // The book's own contents, if it has them.
            toc?.let { idx ->
                val nodes = ArrayList<Pair<Int, TocNode>>()
                for (e in idx.entries) {
                    val pos = e.tags[1]?.firstOrNull() ?: continue
                    val label = e.tags[3]?.firstOrNull()?.let { idx.strings[it] }?.trim() ?: continue
                    val file = idFile["filepos$pos"] ?: continue
                    nodes += (e.tags[4]?.firstOrNull() ?: 0) to TocNode(label, "OEBPS/text/$file", "filepos$pos")
                }
                if (nodes.isNotEmpty()) out.toc = nest(nodes).toMutableList()
            }
            out.coverPage()
        }

        /** Kindle-only HTML as plain XHTML: links between pages, widths and heights as styles, no Kindle tags. */
        private fun tidy(e: HtmlSoup.Element, page: Int, paths: List<String>, idFile: Map<String, String>) {
            val it = e.children.listIterator()
            while (it.hasNext()) {
                val c = it.next() as? HtmlSoup.Element ?: continue
                if (c.name.startsWith("mbp:") || c.name == "guide" || c.name == "reference" || c.name == "script") {
                    it.remove()
                    c.children.forEach { k -> it.add(k) }
                    continue
                }
                val style = StringBuilder(c.attrs["style"].orEmpty())
                c.attrs.remove("width")?.let { w -> if (c.name == "p" || c.name == "div") style.append("text-indent:").append(cssLength(w)).append(';') else c.attrs["width"] = w }
                c.attrs.remove("height")?.let { hh -> if (c.name == "p" || c.name == "div") style.append("margin-top:").append(cssLength(hh)).append(';') else c.attrs["height"] = hh }
                c.attrs.remove("align")?.let { a -> if (c.name != "img") style.append("text-align:").append(a.lowercase()).append(';') }
                if (style.isNotEmpty()) c.attrs["style"] = style.toString()
                when (c.name) {
                    "font" -> {
                        val size = c.attrs["size"]
                        c.name = "span"
                        c.attrs.clear()
                        size?.toIntOrNull()?.let { s -> c.attrs["style"] = "font-size:" + listOf("0.6em", "0.75em", "0.9em", "1em", "1.2em", "1.5em", "2em").getOrElse(s - 1) { "1em" } }
                    }
                    "center" -> {
                        c.name = "div"
                        c.attrs["style"] = "text-align:center"
                    }
                    "a" -> c.attrs["href"]?.let { href ->
                        if (href.startsWith("#")) {
                            val f = idFile[href.substring(1)]
                            if (f == null) c.attrs.remove("href") else if (f != paths[page]) c.attrs["href"] = f + href
                        }
                    }
                    "img" -> if (c.attrs["src"].isNullOrEmpty()) {
                        it.remove()
                        continue
                    }
                }
                tidy(c, page, paths, idFile)
            }
        }
    }

    private fun cssLength(v: String): String = v.trim().let { if (it.matches(Regex("""-?\d+(\.\d+)?"""))) "${it}px" else it }

    /** Contents with a depth each, nested. */
    private fun nest(nodes: List<Pair<Int, TocNode>>): List<TocNode> {
        class T(val node: TocNode, val kids: MutableList<T> = ArrayList())
        val top = ArrayList<T>()
        val stack = ArrayList<Pair<Int, T>>()
        for ((depth, n) in nodes) {
            val t = T(n)
            while (stack.isNotEmpty() && stack.last().first >= depth) stack.removeAt(stack.size - 1)
            (stack.lastOrNull()?.second?.kids ?: top) += t
            stack += depth to t
        }
        fun build(t: T): TocNode = t.node.copy(children = t.kids.map { build(it) })
        return top.map { build(it) }
    }

    /** The newer kind (KF8, "AZW3"): the pages of the EPUB it was made from, put back together. */
    private inner class Kf8(val pdb: Pdb, val h: MobiHeader, val out: EpubOut) {
        private lateinit var raw: ByteArray
        private val flows = ArrayList<IntRange>()
        private val parts = ArrayList<String>()
        private val partStart = ArrayList<Int>()
        private val partEnd = ArrayList<Int>()
        private val frags = ArrayList<Frag>()
        private val resources = HashMap<Int, String>()
        private val flowFiles = HashMap<Int, String>()

        fun write() {
            raw = h.text()
            if (h.fdst >= 0) {
                val f = pdb.record(h.fdst)
                if (f.size >= 12 && String(f, 0, 4, Charsets.ISO_8859_1) == "FDST") {
                    val n = Pdb.u32(f, 8).toInt()
                    for (k in 0 until n) {
                        val s = Pdb.u32(f, 12 + 8 * k).toInt()
                        val e = Pdb.u32(f, 16 + 8 * k).toInt()
                        flows += s until minOf(e, raw.size)
                    }
                }
            }
            if (flows.isEmpty()) flows += 0 until raw.size
            if (h.skelIndex < 0 || h.fragIndex < 0) throw ConversionException("This Kindle book's page index is missing.")
            val skelIdx = MobiIndex(pdb, h.skelIndex)
            val fragIdx = MobiIndex(pdb, h.fragIndex)
            for (e in fragIdx.entries) {
                val geometry = e.tags[6] ?: continue
                frags += Frag(e.name.toIntOrNull() ?: continue, e.tags[2]?.firstOrNull()?.let { fragIdx.strings[it] }.orEmpty(), e.tags[3]?.firstOrNull() ?: 0, geometry[0], geometry.getOrElse(1) { 0 })
            }
            val text = raw.copyOfRange(flows[0].first, flows[0].last + 1)
            var fragPtr = 0
            for (e in skelIdx.entries) {
                val geometry = e.tags[6] ?: continue
                val skel = Skel(e.name, e.tags[1]?.firstOrNull() ?: 0, geometry[0], geometry.getOrElse(1) { 0 })
                var file = String(text, skel.start, minOf(skel.length, text.size - skel.start), Charsets.ISO_8859_1)
                var basePtr = skel.start + skel.length
                for (k in 0 until skel.fragments) {
                    val fr = frags.getOrNull(fragPtr++) ?: break
                    val slice = String(text, basePtr, minOf(fr.length, text.size - basePtr).coerceAtLeast(0), Charsets.ISO_8859_1)
                    var insert = (fr.insert - skel.start).coerceIn(0, file.length)
                    // Some books' insert points land inside a tag: move to the tag's end.
                    val lt = file.lastIndexOf('<', insert - 1)
                    val gt = file.lastIndexOf('>', insert - 1)
                    if (lt > gt) file.indexOf('>', insert).takeIf { it >= 0 }?.let { insert = it + 1 }
                    file = file.substring(0, insert) + slice + file.substring(insert)
                    basePtr += fr.length
                }
                partStart += skel.start
                partEnd += basePtr
                parts += file
            }
            if (parts.isEmpty()) throw ConversionException("There's no text in this Kindle book.")
            val names = parts.indices.map { "part%04d.xhtml".format(it) }
            // Links into the book: to the page and the element nearest before the place.
            val wantedAids = HashMap<Int, HashSet<String>>()
            fun target(fid: Int, off: Int): Pair<Int, String?>? {
                val fr = frags.getOrNull(fid) ?: return null
                val pos = fr.insert + off
                var p = partStart.indices.firstOrNull { pos >= partStart[it] && pos < partEnd[it] }
                if (p == null) p = fr.file.coerceIn(0, parts.size - 1)
                val part = parts[p]
                var npos = (pos - partStart[p]).coerceIn(0, part.length)
                // Inside a tag (or at its start): from the end of it.
                val gt = part.indexOf('>', npos)
                val lt = part.indexOf('<', npos)
                if (gt >= 0 && (lt == npos || lt < 0 || gt < lt)) npos = gt + 1
                val before = part.substring(maxOf(0, npos - 40_000), npos)
                for (m in Regex("""<([A-Za-z][^\s>/]*)([^>]*)>""").findAll(before).toList().asReversed()) {
                    val attrs = m.groupValues[2]
                    if (m.groupValues[1].equals("body", true)) return p to null
                    Regex("""\sid\s*=\s*["']([^"']+)["']""").find(attrs)?.let { return p to it.groupValues[1] }
                    Regex("""\said\s*=\s*["']([^"']+)["']""").find(attrs)?.let { a ->
                        wantedAids.getOrPut(p) { HashSet() } += a.groupValues[1]
                        return p to "aid-" + a.groupValues[1]
                    }
                }
                return p to null
            }
            val posLink = Regex("""kindle:pos:fid:([0-9A-Va-v]{4}):off:([0-9A-Va-v]{10})""")
            val fixed = parts.mapIndexed { i, part ->
                var s = h.decode(part.toByteArray(Charsets.ISO_8859_1))
                s = posLink.replace(s) { m ->
                    val t = target(base32(m.groupValues[1]), base32(m.groupValues[2])) ?: return@replace "#"
                    val file = names[t.first]
                    (if (t.first == i) "" else file) + (t.second?.let { "#$it" } ?: if (t.first == i) "#" else "")
                }
                s = rewriteResources(s, fromText = true)
                s
            }
            fixed.forEachIndexed { i, s ->
                val doc = HtmlSoup.parse(s)
                val head = doc.children.filterIsInstance<HtmlSoup.Element>().firstOrNull { it.name == "head" }
                val body = doc.children.filterIsInstance<HtmlSoup.Element>().firstOrNull { it.name == "body" } ?: doc
                val aids = wantedAids[i].orEmpty()
                for (e in body.elements().toList() + body) {
                    e.attrs.remove("aid")?.let { a -> if (a in aids && "id" !in e.attrs) e.attrs["id"] = "aid-$a" }
                }
                // The page's own style sheets (now in the book), and styles written in its head.
                val sheets = head?.elements()?.filter { it.name == "link" && it.attrs["href"]?.endsWith(".css") == true }?.mapNotNull { it.attrs["href"] }?.toList().orEmpty()
                val inline = head?.elements()?.filter { it.name == "style" }?.joinToString("\n") { it.text() }.orEmpty()
                val headExtra = if (inline.isNotBlank()) "  <style type=\"text/css\">${HtmlSoup.escapeText(rewriteResources(inline, fromText = false))}</style>\n" else ""
                val title = body.elements().firstOrNull { it.name.length == 2 && it.name[0] == 'h' && it.name[1].isDigit() }?.text()?.trim()?.replace(Regex("\\s+"), " ")?.take(120)
                val bodyClass = body.attrs["class"]
                out.page(title?.ifEmpty { null }, HtmlSoup.inner(body), name = names[i], extraStyles = sheets, bodyClass = bodyClass, head = headExtra, ownStyles = true)
            }
            // The contents.
            if (h.ncx >= 0) runCatching {
                val idx = MobiIndex(pdb, h.ncx)
                val nodes = ArrayList<Pair<Int, TocNode>>()
                for (e in idx.entries) {
                    val label = e.tags[3]?.firstOrNull()?.let { idx.strings[it] }?.trim() ?: continue
                    val fidOff = e.tags[6] ?: continue
                    val t = target(fidOff[0], fidOff.getOrElse(1) { 0 }) ?: continue
                    nodes += (e.tags[4]?.firstOrNull() ?: 0) to TocNode(label, "OEBPS/text/" + names[t.first], t.second)
                }
                if (nodes.isNotEmpty()) out.toc = nest(nodes).toMutableList()
            }
            if (out.toc.isEmpty()) out.toc = out.toc
            if (!fixed.first().contains("<img") || fixed.first().replace(Regex("<[^>]+>"), "").isNotBlank()) out.coverPage()
        }

        /** kindle:embed and kindle:flow addresses as files in the book. */
        private fun rewriteResources(s: String, fromText: Boolean): String {
            var t = s.replace(Regex("""kindle:embed:([0-9A-Va-v]{4})(\?mime=[^"')\s]*)?""")) { m ->
                val n = base32(m.groupValues[1]) - 1
                resources.getOrPut(n) {
                    val bytes = resource(h, n) ?: return@getOrPut ""
                    when {
                        isPicture(bytes) -> out.image("image%05d.jpg".format(n + 1), bytes)
                        bytes.size > 24 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "FONT" -> font(bytes)?.let { f -> out.font("font%05d.${fontExt(f)}".format(n + 1), f) } ?: ""
                        String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1).contains("<svg") -> out.image("image%05d.svg".format(n + 1), bytes)
                        else -> ""
                    }
                }.let { p -> if (fromText) p else p.replace("../", "../") }
            }
            t = t.replace(Regex("""kindle:flow:([0-9A-Va-v]{4})\?mime=([^"')\s]*)""")) { m ->
                val n = base32(m.groupValues[1])
                flowFiles.getOrPut(n) {
                    val range = flows.getOrNull(n) ?: return@getOrPut ""
                    val content = h.decode(raw.copyOfRange(range.first, range.last + 1))
                    when {
                        m.groupValues[2].contains("css") -> out.style("flow%04d.css".format(n), rewriteResources(content, fromText = false))
                        m.groupValues[2].contains("svg") -> out.image("flow%04d.svg".format(n), rewriteResources(content, fromText = false).toByteArray())
                        else -> ""
                    }
                }
            }
            return t
        }

        /** A KF8 font record: un-hidden and uncompressed. */
        private fun font(rec: ByteArray): ByteArray? = runCatching {
            val size = Pdb.u32(rec, 4).toInt()
            val flags = Pdb.u32(rec, 8).toInt()
            val dataStart = Pdb.u32(rec, 12).toInt()
            val keyLen = Pdb.u32(rec, 16).toInt()
            val keyStart = Pdb.u32(rec, 20).toInt()
            val data = rec.copyOfRange(dataStart, rec.size)
            if (flags and 2 != 0 && keyLen > 0) {
                val key = rec.copyOfRange(keyStart, keyStart + keyLen)
                for (k in 0 until minOf(1040, data.size)) data[k] = (data[k].toInt() xor key[k % keyLen].toInt()).toByte()
            }
            if (flags and 1 != 0) {
                val inf = Inflater()
                inf.setInput(data)
                val outBuf = ByteArray(size)
                val n = inf.inflate(outBuf)
                inf.end()
                outBuf.copyOf(n)
            } else data
        }.getOrNull()

        private fun fontExt(f: ByteArray): String = when {
            f.size > 4 && String(f, 0, 4, Charsets.ISO_8859_1) == "OTTO" -> "otf"
            f.size > 4 && String(f, 0, 4, Charsets.ISO_8859_1) == "wOFF" -> "woff"
            else -> "ttf"
        }
    }

    companion object {
        /** Kindle's base-32 numbers: 0-9 then A-V. */
        fun base32(s: String): Int = s.fold(0) { acc, ch ->
            acc * 32 + when (ch) {
                in '0'..'9' -> ch - '0'
                in 'A'..'V' -> ch - 'A' + 10
                in 'a'..'v' -> ch - 'a' + 10
                else -> 0
            }
        }
    }
}
