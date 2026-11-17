package dev.kolib.app.epub

import dev.kolib.app.tr
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val DC_NS = "http://purl.org/dc/elements/1.1/"
const val OPF_NS = "http://www.idpf.org/2007/opf"

class EpubException(message: String) : Exception(message)

data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: Set<String>,
    val element: XElement,
) {
    val isImage: Boolean get() = mediaType.startsWith("image/")
    val isContent: Boolean get() = mediaType == "application/xhtml+xml" || mediaType == "text/html"
}

/**
 * An EPUB package document (the .opf file): reading its metadata and manifest, and writing new
 * metadata back with the smallest possible change to the original text.
 */
class OpfDocument(val source: String) {

    val doc: XDocument = try {
        Xml.parse(source)
    } catch (e: XmlException) {
        throw EpubException(tr("The book's package file is damaged ({0}).", e.message))
    }
    val pkg: XElement = doc.root.also {
        if (!it.localName.equals("package", ignoreCase = true)) throw EpubException(tr("The book's package file has no <package> element."))
    }
    val version: String = pkg.attributes["version"]?.trim().orEmpty().ifEmpty { "2.0" }
    val isEpub3: Boolean = version.startsWith("3")
    val metadataElement: XElement = pkg.elements().firstOrNull { it.localName.equals("metadata", true) }
        ?: throw EpubException(tr("The book's package file has no metadata section."))
    val manifestElement: XElement? = pkg.elements().firstOrNull { it.localName.equals("manifest", true) }
    val spineElement: XElement? = pkg.elements().firstOrNull { it.localName.equals("spine", true) }
    val guideElement: XElement? = pkg.elements().firstOrNull { it.localName.equals("guide", true) }

    /** Metadata children, including those inside the old OEB `dc-metadata`/`x-metadata` wrappers. */
    val metaChildren: List<XElement> = metadataElement.elements().flatMap { e ->
        if (e.localName.equals("dc-metadata", true) || e.localName.equals("x-metadata", true)) e.elements() else listOf(e)
    }

    val items: List<ManifestItem> = manifestElement?.elements()
        ?.filter { it.localName.equals("item", true) }
        ?.mapNotNull { e ->
            val id = e.attributes["id"] ?: return@mapNotNull null
            val href = e.attributes["href"] ?: return@mapNotNull null
            ManifestItem(
                id = id,
                href = href,
                mediaType = e.attributes["media-type"]?.trim()?.lowercase().orEmpty(),
                properties = e.attributes["properties"]?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
                element = e,
            )
        }.orEmpty()

    val uniqueIdentifierId: String? = pkg.attributes["unique-identifier"]

    fun isDc(e: XElement): Boolean = e.namespaceUri() == DC_NS || (e.prefix.equals("dc", ignoreCase = true) && e.namespaceUri() == null)
    private fun isMeta(e: XElement): Boolean = e.localName.equals("meta", true)
    private fun dc(local: String): List<XElement> = metaChildren.filter { isDc(it) && it.localName.equals(local, true) }

    /** EPUB 3 `<meta refines="#id" property="...">` entries, by target id. */
    private val refines: Map<String, List<XElement>> = metaChildren
        .filter { isMeta(it) && it.attributes["refines"]?.startsWith("#") == true }
        .groupBy { it.attributes["refines"]!!.removePrefix("#") }

    private fun refine(id: String?, property: String): String? =
        id?.let { refines[it] }?.firstOrNull { it.attributes["property"] == property }?.text()?.trim()

    /** `<meta name="x" content="y"/>` (EPUB 2 style) or `<meta property="x">y</meta>`. */
    private fun metaValue(name: String): String? {
        for (e in metaChildren) {
            if (!isMeta(e)) continue
            if (e.attributes["name"] == name) return e.attributes["content"]?.trim()
            if (e.attributes["property"] == name && e.attributes["refines"] == null) return e.text().trim()
        }
        return null
    }

    private fun opfAttr(e: XElement, local: String): String? =
        e.attr(local, OPF_NS) ?: e.attributes["opf:$local"]

    private fun creatorRole(e: XElement): String? =
        (opfAttr(e, "role") ?: refine(e.attributes["id"], "role"))?.trim()?.lowercase()?.ifEmpty { null }

    private fun isAuthor(e: XElement): Boolean = creatorRole(e).let { it == null || it == "aut" }

    private fun mainTitle(): XElement? {
        val titles = dc("title")
        if (titles.size > 1 && isEpub3) {
            titles.firstOrNull { refine(it.attributes["id"], "title-type") == "main" }?.let { return it }
        }
        return titles.firstOrNull()
    }

    private data class SeriesInfo(val name: String, val index: Double?, val element: XElement)

    private fun epub3Series(): SeriesInfo? {
        for (e in metaChildren) {
            if (!isMeta(e) || e.attributes["property"] != "belongs-to-collection") continue
            val id = e.attributes["id"]
            val type = refine(id, "collection-type")
            if (type != null && type != "series") continue
            val name = Names.normalizeSpace(e.text())
            if (name.isEmpty()) continue
            return SeriesInfo(name, refine(id, "group-position")?.toDoubleOrNull(), e)
        }
        return null
    }

    private fun isIsbnIdentifier(e: XElement): Boolean {
        val scheme = opfAttr(e, "scheme")
        if (scheme.equals("isbn", ignoreCase = true)) return true
        val text = e.text().trim().lowercase()
        if (text.startsWith("urn:isbn:") || text.startsWith("isbn:")) return true
        val type = refine(e.attributes["id"], "identifier-type")
        return type == "15" || type == "02" || type.equals("isbn", true)
    }

    fun readMetadata(): BookMetadata {
        val titleEl = mainTitle()
        val title = titleEl?.text()?.let { Names.normalizeSpace(it) }.orEmpty()
        val titleSort = metaValue("calibre:title_sort")?.takeIf { it.isNotBlank() }
            ?: refine(titleEl?.attributes?.get("id"), "file-as")

        val authors = mutableListOf<String>()
        val sorts = LinkedHashMap<String, String>()
        for (c in dc("creator").filter { isAuthor(it) }) {
            val name = Names.normalizeSpace(c.text())
            if (name.isEmpty() || authors.any { it.equals(name, true) }) continue
            authors += name
            (opfAttr(c, "file-as") ?: refine(c.attributes["id"], "file-as"))?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { sorts[name] = it }
        }

        val calibreSeries = metaValue("calibre:series")?.let { Names.normalizeSpace(it) }?.takeIf { it.isNotEmpty() }
        val e3 = if (calibreSeries == null) epub3Series() else null
        val series = calibreSeries ?: e3?.name
        val seriesIndex = if (calibreSeries != null) metaValue("calibre:series_index")?.toDoubleOrNull() else e3?.index

        val tags = dc("subject").map { Names.normalizeSpace(it.text()) }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        val isbn = dc("identifier").firstOrNull { isIsbnIdentifier(it) }?.text()?.let { Isbn.normalize(it) }
            ?.takeIf { it.length == 10 || it.length == 13 }

        return BookMetadata(
            title = title,
            titleSort = titleSort,
            authors = authors,
            authorSorts = sorts,
            series = series,
            seriesIndex = if (series != null) seriesIndex else null,
            tags = tags,
            publisher = dc("publisher").firstOrNull()?.text()?.let { Names.normalizeSpace(it) }?.ifEmpty { null },
            published = publicationDateElement()?.text()?.let { normalizeDate(it) },
            language = dc("language").firstOrNull()?.text()?.trim()?.ifEmpty { null },
            isbn = isbn,
            description = dc("description").firstOrNull()?.text()?.trim()?.ifEmpty { null },
            rating = metaValue("calibre:rating")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 10),
            identifier = uniqueIdentifierId?.let { id -> dc("identifier").firstOrNull { it.attributes["id"] == id } }
                ?.text()?.trim(),
        )
    }

    private fun publicationDateElement(): XElement? {
        val dates = dc("date")
        if (isEpub3) return dates.firstOrNull()
        return dates.firstOrNull { opfAttr(it, "event").let { ev -> ev == null || ev == "publication" || ev == "original-publication" } }
    }

    // ---- covers and structure -------------------------------------------------------------------

    fun item(id: String): ManifestItem? = items.firstOrNull { it.id == id }

    /** The manifest item declared as the cover image, if any. */
    fun declaredCoverItem(): ManifestItem? {
        items.firstOrNull { "cover-image" in it.properties && it.isImage }?.let { return it }
        metaValue("cover")?.let { ref ->
            (item(ref) ?: items.firstOrNull { it.href == ref })?.takeIf { it.isImage }?.let { return it }
        }
        return null
    }

    /** A likely cover image when none is declared: an image whose id or file name says "cover". */
    fun guessedCoverItem(): ManifestItem? =
        items.firstOrNull { it.isImage && (it.id.contains("cover", true) || it.href.substringAfterLast('/').contains("cover", true)) }

    fun guideCoverHref(): String? = guideElement?.elements()
        ?.firstOrNull { it.localName.equals("reference", true) && it.attributes["type"].equals("cover", true) }
        ?.attributes?.get("href")?.substringBefore('#')

    /** One entry of the reading order. */
    data class SpineRef(val idref: String, val linear: Boolean, val element: XElement)

    fun spineRefs(): List<SpineRef> = spineElement?.elements()
        ?.filter { it.localName.equals("itemref", true) }
        ?.mapNotNull { ref ->
            val idref = ref.attributes["idref"] ?: return@mapNotNull null
            SpineRef(idref, !ref.attributes["linear"].equals("no", ignoreCase = true), ref)
        }
        .orEmpty()

    /** The EPUB 2 table of contents (NCX), named by the spine's toc attribute (or found by type). */
    fun ncxItem(): ManifestItem? =
        spineElement?.attributes?.get("toc")?.let { item(it) } ?: items.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }

    /** The EPUB 3 navigation document. */
    fun navItem(): ManifestItem? = items.firstOrNull { "nav" in it.properties }

    fun spineHrefs(): List<String> = spineElement?.elements()
        ?.filter { it.localName.equals("itemref", true) }
        ?.mapNotNull { ref -> ref.attributes["idref"]?.let { item(it)?.href } }
        .orEmpty()

    // ---- writing ------------------------------------------------------------------------------

    /**
     * Returns the package document with [meta] written in. Elements the app manages are rewritten;
     * everything else is kept exactly as it was.
     */
    fun withMetadata(meta: BookMetadata, now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): String {
        require(meta.title.isNotBlank()) { "A book must have a title" }
        val edits = TextEdits(source)
        val removed = HashSet<XElement>()
        fun remove(e: XElement) {
            if (removed.add(e)) edits.removeWithLeadingSpace(e)
        }
        fun removeRefinesOf(id: String?, properties: Set<String>? = null) {
            if (id == null) return
            refines[id]?.filter { properties == null || it.attributes["property"] in properties }?.forEach { remove(it) }
        }

        val ids = doc.root.descendants().mapNotNull { it.attributes["id"] }.toMutableSet()
        doc.root.attributes["id"]?.let { ids += it }
        fun newId(base: String): String {
            var candidate = base
            var n = 2
            while (candidate in ids) candidate = "$base-${n++}"
            ids += candidate
            return candidate
        }

        // Prefixes: use whatever the document already binds, declaring them on <metadata> if needed.
        val extraNs = LinkedHashMap<String, String>()
        val dcPrefix = metadataElement.prefixFor(DC_NS)?.takeIf { it.isNotEmpty() }
            ?: "dc".also { extraNs["xmlns:dc"] = DC_NS }
        val opfPrefix = metadataElement.prefixFor(OPF_NS)?.takeIf { it.isNotEmpty() }
        val opfAttrPrefix = opfPrefix ?: if (!isEpub3) "opf".also { extraNs["xmlns:opf"] = OPF_NS } else "opf"
        val metaName = metadataElement.prefix?.let { "$it:meta" } ?: "meta"

        val lines = mutableListOf<String>()
        fun dcEl(local: String, text: String, attrs: Map<String, String> = emptyMap()) {
            lines += buildString {
                append("<").append(dcPrefix).append(':').append(local)
                for ((k, v) in attrs) append(' ').append(k).append("=\"").append(Xml.escapeAttr(v)).append('"')
                append('>').append(Xml.escapeText(text)).append("</").append(dcPrefix).append(':').append(local).append('>')
            }
        }
        fun oldMeta(name: String, content: String) {
            lines += "<$metaName name=\"${Xml.escapeAttr(name)}\" content=\"${Xml.escapeAttr(content)}\"/>"
        }
        fun refineMeta(id: String, property: String, value: String, scheme: String? = null) {
            val schemeAttr = scheme?.let { " scheme=\"$it\"" }.orEmpty()
            lines += "<$metaName refines=\"#$id\" property=\"$property\"$schemeAttr>${Xml.escapeText(value)}</$metaName>"
        }

        // Title: changed in place, because readers (the Kobo included) take the first title they find.
        val titleEl = mainTitle()
        var titleId = titleEl?.attributes?.get("id")
        if (titleEl != null) {
            if (isEpub3 && titleId == null) {
                titleId = newId("title")
                val attrs = LinkedHashMap(titleEl.attributes).apply { this["id"] = titleId }
                edits.replace(titleEl.start, titleEl.startTagEnd, Xml.startTag(titleEl.name, attrs, false))
            }
            if (titleEl.selfClosing) {
                edits.replace(titleEl.start, titleEl.end,
                    Xml.startTag(titleEl.name, LinkedHashMap(titleEl.attributes).apply { titleId?.let { this["id"] = it } }, false) +
                        Xml.escapeText(meta.title.trim()) + "</${titleEl.name}>")
            } else {
                edits.replace(titleEl.startTagEnd, titleEl.endTagStart, Xml.escapeText(meta.title.trim()))
            }
            removeRefinesOf(titleId, setOf("file-as"))
        } else {
            if (isEpub3) titleId = newId("title")
            dcEl("title", meta.title.trim(), titleId?.let { mapOf("id" to it) } ?: emptyMap())
        }
        val newTitleLines = lines.size
        metaChildren.filter { isMeta(it) && (it.attributes["name"] == "calibre:title_sort" || (it.attributes["property"] == "calibre:title_sort")) }
            .forEach { remove(it) }
        val titleSort = meta.effectiveTitleSort
        if (isEpub3 && titleId != null) refineMeta(titleId, "file-as", titleSort)
        oldMeta("calibre:title_sort", titleSort)

        // Authors: replace author creators where the first one was (so authors still come first),
        // keeping illustrators, editors and so on.
        val oldAuthors = dc("creator").filter { isAuthor(it) }
        for (c in oldAuthors) {
            remove(c)
            removeRefinesOf(c.attributes["id"])
        }
        val authorLines = mutableListOf<String>()
        val beforeAuthors = lines.size
        meta.authors.map { Names.normalizeSpace(it) }.filter { it.isNotEmpty() }.forEach { name ->
            val sort = meta.sortNameFor(name)
            if (isEpub3) {
                val id = newId("creator")
                dcEl("creator", name, mapOf("id" to id))
                refineMeta(id, "role", "aut", "marc:relators")
                refineMeta(id, "file-as", sort)
            } else {
                dcEl("creator", name, linkedMapOf("$opfAttrPrefix:role" to "aut", "$opfAttrPrefix:file-as" to sort))
            }
        }
        authorLines += lines.subList(beforeAuthors, lines.size)
        repeat(authorLines.size) { lines.removeAt(lines.size - 1) }
        val authorAnchor = oldAuthors.firstOrNull()?.let { edits.leadingSpaceStart(it) } ?: titleEl?.end

        // Series: Calibre's metas (read by Calibre and Kobo) plus the EPUB 3 form.
        metaChildren.filter {
            isMeta(it) && (it.attributes["name"] in setOf("calibre:series", "calibre:series_index") ||
                it.attributes["property"] in setOf("calibre:series", "calibre:series_index"))
        }.forEach { remove(it) }
        for (e in metaChildren.filter { isMeta(it) && it.attributes["property"] == "belongs-to-collection" }) {
            val type = refine(e.attributes["id"], "collection-type")
            if (type == null || type == "series") {
                remove(e)
                removeRefinesOf(e.attributes["id"])
            }
        }
        meta.series?.let { Names.normalizeSpace(it) }?.takeIf { it.isNotEmpty() }?.let { series ->
            val index = meta.seriesIndex
            if (isEpub3) {
                val id = newId("series")
                lines += "<$metaName property=\"belongs-to-collection\" id=\"$id\">${Xml.escapeText(series)}</$metaName>"
                refineMeta(id, "collection-type", "series")
                if (index != null) refineMeta(id, "group-position", Names.formatIndex(index))
            }
            oldMeta("calibre:series", series)
            oldMeta("calibre:series_index", Names.formatIndex(index ?: 1.0))
        }

        // Tags.
        dc("subject").forEach { remove(it) }
        meta.tags.map { Names.normalizeSpace(it) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .forEach { dcEl("subject", it) }

        // Publisher.
        dc("publisher").forEach { remove(it) }
        meta.publisher?.trim()?.takeIf { it.isNotEmpty() }?.let { dcEl("publisher", it) }

        // Publication date (other EPUB 2 dates such as "modification" stay).
        publicationDateElement()?.let { remove(it) }
        if (isEpub3) dc("date").forEach { remove(it) }
        meta.published?.let { normalizeDate(it) }?.let { dcEl("date", it) }

        // Language: replace the first; an EPUB must keep at least one.
        val language = meta.language?.trim()?.takeIf { it.isNotEmpty() }
        if (language != null) {
            dc("language").firstOrNull()?.let { remove(it) }
            dcEl("language", language)
        }

        // ISBN. The book's own unique identifier is changed in place, never removed.
        val isbn = meta.isbn?.let { Isbn.normalize(it) }?.takeIf { it.isNotEmpty() }
        val isbnEls = dc("identifier").filter { isIsbnIdentifier(it) }
        val uniqueIsbn = isbnEls.firstOrNull { it.attributes["id"] != null && it.attributes["id"] == uniqueIdentifierId }
        isbnEls.filter { it !== uniqueIsbn }.forEach { remove(it); removeRefinesOf(it.attributes["id"]) }
        if (uniqueIsbn != null) {
            if (isbn != null) {
                val value = if (uniqueIsbn.text().trim().lowercase().startsWith("urn:isbn:")) "urn:isbn:$isbn" else isbn
                edits.replace(uniqueIsbn.startTagEnd, uniqueIsbn.endTagStart, Xml.escapeText(value))
            }
        } else if (isbn != null) {
            if (isEpub3) dcEl("identifier", "urn:isbn:$isbn", mapOf("id" to newId("isbn")))
            else dcEl("identifier", isbn, mapOf("$opfAttrPrefix:scheme" to "ISBN"))
        }

        // Description.
        dc("description").forEach { remove(it) }
        meta.description?.trim()?.takeIf { it.isNotEmpty() }?.let { dcEl("description", it) }

        // Rating.
        metaChildren.filter { isMeta(it) && (it.attributes["name"] == "calibre:rating" || it.attributes["property"] == "calibre:rating") }
            .forEach { remove(it) }
        meta.rating?.takeIf { it > 0 }?.let { oldMeta("calibre:rating", it.coerceIn(0, 10).toString()) }

        // EPUB 3 requires a last-modified stamp.
        if (isEpub3) {
            metaChildren.filter { isMeta(it) && it.attributes["property"] == "dcterms:modified" && it.attributes["refines"] == null }
                .forEach { remove(it) }
            lines += "<$metaName property=\"dcterms:modified\">${now.withNano(0).format(DateTimeFormatter.ISO_INSTANT)}</$metaName>"
        }

        // Insert after the last element we keep, with the document's indentation.
        val kept = metadataElement.children.filterIsInstance<XElement>().filter { it !in removed }
        val anchor = kept.lastOrNull()?.end ?: metadataElement.startTagEnd
        val indent = indentOf(metadataElement) ?: "    "
        if (authorLines.isNotEmpty()) {
            if (authorAnchor != null) edits.insert(authorAnchor, authorLines.joinToString("") { "\n" + indent + it })
            else lines.addAll(newTitleLines, authorLines) // right after a newly added title
        }
        edits.insert(anchor, lines.joinToString("") { "\n" + indent + it })

        if (extraNs.isNotEmpty()) {
            val attrs = LinkedHashMap(metadataElement.attributes).apply { putAll(extraNs) }
            edits.replace(metadataElement.start, metadataElement.startTagEnd, Xml.startTag(metadataElement.name, attrs, metadataElement.selfClosing))
        }
        return edits.apply()
    }

    fun indentOf(parent: XElement): String? {
        val first = parent.children.filterIsInstance<XElement>().firstOrNull() ?: return null
        var s = first.start
        while (s > 0 && (source[s - 1] == ' ' || source[s - 1] == '\t')) s--
        return source.substring(s, first.start).takeIf { s > 0 && source[s - 1] == '\n' }
    }

    /**
     * Declares [path] (relative to the OPF) as the cover image. Adds a manifest item if needed and
     * points the EPUB 2 `cover` meta and the EPUB 3 `cover-image` property at it.
     */
    fun withCover(href: String, mediaType: String): String {
        val edits = TextEdits(source)
        val existing = items.firstOrNull { it.href == href }
        val ids = doc.root.descendants().mapNotNull { it.attributes["id"] }.toMutableSet()
        val itemId = existing?.id ?: run {
            var c = "cover-image"
            var n = 2
            while (c in ids) c = "cover-image-${n++}"
            c
        }
        // Only one item may carry cover-image.
        for (it in items) {
            if (it === existing || "cover-image" !in it.properties) continue
            val attrs = LinkedHashMap(it.element.attributes)
            val props = it.properties - "cover-image"
            if (props.isEmpty()) attrs.remove("properties") else attrs["properties"] = props.joinToString(" ")
            edits.replace(it.element.start, it.element.startTagEnd, Xml.startTag(it.element.name, attrs, it.element.selfClosing))
        }
        val manifest = manifestElement ?: throw EpubException(tr("The book has no manifest."))
        if (existing != null) {
            val attrs = LinkedHashMap(existing.element.attributes)
            attrs["media-type"] = mediaType
            if (isEpub3) attrs["properties"] = (existing.properties + "cover-image").joinToString(" ")
            edits.replace(existing.element.start, existing.element.startTagEnd, Xml.startTag(existing.element.name, attrs, existing.element.selfClosing))
        } else {
            val itemName = manifest.prefix?.let { "$it:item" } ?: "item"
            val props = if (isEpub3) " properties=\"cover-image\"" else ""
            val anchor = manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd
            val indent = indentOf(manifest) ?: "    "
            edits.insert(anchor, "\n$indent<$itemName id=\"$itemId\" href=\"${Xml.escapeAttr(href)}\" media-type=\"$mediaType\"$props/>")
        }
        // The EPUB 2 cover meta (Kobo and many readers look for it in EPUB 3 books too).
        val metaName = metadataElement.prefix?.let { "$it:meta" } ?: "meta"
        val coverMeta = metaChildren.firstOrNull { isMeta(it) && it.attributes["name"] == "cover" }
        if (coverMeta != null) {
            val attrs = LinkedHashMap(coverMeta.attributes).apply { this["content"] = itemId }
            edits.replace(coverMeta.start, coverMeta.startTagEnd, Xml.startTag(coverMeta.name, attrs, coverMeta.selfClosing))
        } else {
            val anchor = metadataElement.elements().lastOrNull()?.end ?: metadataElement.startTagEnd
            val indent = indentOf(metadataElement) ?: "    "
            edits.insert(anchor, "\n$indent<$metaName name=\"cover\" content=\"$itemId\"/>")
        }
        return edits.apply()
    }

    companion object {
        private val datePattern = Regex("^(\\d{4})(?:-(\\d{1,2}))?(?:-(\\d{1,2}))?")

        /** "2005-07-17T14:00:00+00:00" → "2005-07-17"; Calibre's "0101-01-01" (no date) → null. */
        fun normalizeDate(raw: String): String? {
            val m = datePattern.find(raw.trim()) ?: return null
            val year = m.groupValues[1].toInt()
            if (year <= 101) return null
            val month = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 }
            val day = m.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 }
            return buildString {
                append("%04d".format(year))
                if (month != null) {
                    append("-%02d".format(month))
                    if (day != null) append("-%02d".format(day))
                }
            }
        }
    }
}
