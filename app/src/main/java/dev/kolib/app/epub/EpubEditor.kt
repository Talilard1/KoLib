package dev.kolib.app.epub

import java.io.File

/** The changes the app makes to EPUB files. Each one rewrites the file safely (copy, check, swap). */
object EpubEditor {

    /** Writes new details into the book's package file. */
    fun writeMetadata(file: File, meta: BookMetadata) {
        val (opfPath, newOpf) = EpubFile.open(file).use { epub -> epub.opfPath to epub.opf.withMetadata(meta) }
        EpubWriter.rewriteInPlace(file, replace = mapOf(opfPath to newOpf.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Makes [image] the book's cover. An existing cover file of the same type is overwritten in place,
     * so cover pages inside the book show the new picture too; otherwise a new image is added and declared.
     */
    fun replaceCover(file: File, image: ByteArray, mediaType: String, width: Int, height: Int, namedOnly: Boolean = false) {
        val replace = LinkedHashMap<String, ByteArray>()
        val add = LinkedHashMap<String, ByteArray>()
        EpubFile.open(file).use { epub ->
            // [namedOnly]: a picture on the first page that the book doesn't name as its cover is left as it is.
            val existing = epub.findCover()?.takeIf { it.declared || !namedOnly }
            val sameType = existing != null && existing.mediaType == mediaType
            val coverPath: String
            if (existing != null && sameType) {
                coverPath = existing.path
                replace[coverPath] = image
                if (!existing.declared) {
                    replace[epub.opfPath] = epub.opf.withCover(EpubFile.relativeHref(epub.opfPath, coverPath), mediaType).toByteArray()
                }
            } else {
                val ext = if (mediaType == "image/png") "png" else "jpg"
                var candidate = (if (epub.opfDir.isEmpty()) "" else epub.opfDir + "/") + "cover-kl.$ext"
                var n = 2
                while (epub.has(candidate)) candidate = candidate.substringBeforeLast("cover-kl") + "cover-kl-${n++}.$ext"
                coverPath = candidate
                add[coverPath] = image
                replace[epub.opfPath] = epub.opf.withCover(EpubFile.relativeHref(epub.opfPath, coverPath), mediaType).toByteArray()
                // Point an existing cover page at the new picture.
                if (existing != null) {
                    coverPageFor(epub, existing.path)?.let { (page, html) ->
                        val oldRef = EpubFile.relativeHref(page, existing.path)
                        val newRef = EpubFile.relativeHref(page, coverPath)
                        replace[page] = html.replace(oldRef, newRef).toByteArray()
                    }
                }
            }
            // An SVG cover page sized for the old picture would squash the new one.
            coverPageFor(epub, existing?.path ?: coverPath)?.let { (page, html) ->
                val current = replace[page]?.let { EpubFile.decodeText(it) } ?: html
                val resized = resizeSvgCover(current, width, height)
                if (resized != current) replace[page] = resized.toByteArray()
            }
        }
        EpubWriter.rewriteInPlace(file, replace = replace, add = add)
    }

    /** The cover page (guide cover or first page) that shows [imagePath], with its text. */
    private fun coverPageFor(epub: EpubFile, imagePath: String): Pair<String, String>? {
        val pages = listOfNotNull(epub.opf.guideCoverHref()?.let { epub.resolve(it) }) + epub.opf.spineHrefs().take(1).map { epub.resolve(it) }
        for (page in pages.distinct()) {
            val html = epub.readText(page) ?: continue
            val ref = EpubFile.firstImageReference(html) ?: continue
            if (EpubFile.resolvePath(page.substringBeforeLast('/', ""), ref) == imagePath) return page to html
        }
        return null
    }

    private val viewBox = Regex("(viewBox\\s*=\\s*[\"'])\\s*0\\s+0\\s+\\d+(?:\\.\\d+)?\\s+\\d+(?:\\.\\d+)?\\s*([\"'])")
    private val imageSize = Regex("(<(?:\\w+:)?image\\b[^>]*?)\\bwidth\\s*=\\s*[\"']\\d+(?:\\.\\d+)?[\"']([^>]*?)\\bheight\\s*=\\s*[\"']\\d+(?:\\.\\d+)?[\"']")

    fun resizeSvgCover(html: String, width: Int, height: Int): String {
        if (!html.contains("<svg", ignoreCase = true) && !html.contains(":svg", ignoreCase = true)) return html
        return html
            .replace(viewBox) { m -> "${m.groupValues[1]}0 0 $width $height${m.groupValues[2]}" }
            .replace(imageSize) { m -> "${m.groupValues[1]}width=\"$width\"${m.groupValues[2]}height=\"$height\"" }
    }

    /** What [toKepub] did: how many text files it converted, and any it had to leave as they were. */
    data class KepubResult(val documents: Int, val converted: Int, val skipped: List<String>)

    /**
     * Writes a kepub copy of [source] to [target], adding Kobo's markup to every text file the way
     * kepubify does. Text files that aren't well-formed XHTML are copied unchanged (and listed).
     */
    fun toKepub(source: File, target: File): KepubResult {
        val (docs, opfPath, newOpf) = EpubFile.open(source).use { epub ->
            Triple(epub.contentDocuments().toSet(), epub.opfPath, KepubConverter.markCoverImage(epub.opf))
        }
        val skipped = mutableListOf<String>()
        var converted = 0
        EpubWriter.rewrite(
            source, target,
            replace = newOpf?.let { mapOf(opfPath to it.toByteArray(Charsets.UTF_8)) }.orEmpty(),
        ) { name, bytes ->
            if (name !in docs) return@rewrite null
            val raw = bytes()
            val utf16 = raw.size >= 2 && ((raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) || (raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte()))
            val text = EpubFile.decodeText(raw)
            try {
                var out = KepubConverter.convert(text)
                converted++
                // Written back as UTF-8, so a UTF-16 file's declaration has to say so.
                if (utf16) out = out.replaceFirst(Regex("""(<\?xml[^>]*encoding=["'])[Uu][Tt][Ff]-?16(["'])"""), "$1utf-8$2")
                if (out == text && !utf16) null else out.toByteArray(Charsets.UTF_8)
            } catch (e: XmlException) {
                skipped += name
                null
            }
        }
        EpubFile.open(target).close()
        return KepubResult(docs.size, converted, skipped)
    }

    /** Turns a kepub back into a plain EPUB. Returns true if anything was removed. */
    fun stripKepub(file: File): Boolean {
        val documents = EpubFile.open(file).use { epub ->
            val docs = epub.contentDocuments().toSet()
            val sample = docs.take(4).mapNotNull { epub.readText(it) }
            if (sample.none { KepubStripper.looksLikeKepub(it) } && docs.drop(4).none { d -> epub.readText(d)?.let { KepubStripper.looksLikeKepub(it) } == true }) {
                return false
            }
            docs
        }
        var changed = false
        EpubWriter.rewriteInPlace(file) { name, bytes ->
            if (name !in documents) return@rewriteInPlace null
            val text = EpubFile.decodeText(bytes())
            val stripped = KepubStripper.strip(text)
            if (stripped == text) null else {
                changed = true
                stripped.toByteArray(Charsets.UTF_8)
            }
        }
        return changed
    }
}
