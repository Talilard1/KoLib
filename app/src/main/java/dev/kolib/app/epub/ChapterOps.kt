package dev.kolib.app.epub

import dev.kolib.app.tr

/**
 * Changes to a book's chapters: renaming, reordering, merging, splitting, deleting, hiding and
 * choosing where it starts. The package file, the table of contents and links between chapters are
 * kept in step. Each returns the files to change, for the edit to record as one undoable step.
 */
object ChapterOps {

    data class Result(val label: String, val changes: Map<String, ByteArray?>, val focus: String? = null)

    class OpError(message: String) : Exception(message)

    // ---- package file helpers ----------------------------------------------------------------

    private fun spineRef(opf: OpfDocument, id: String) = opf.spineRefs().firstOrNull { it.idref == id }

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)

    private fun uniqueId(opf: OpfDocument, base: String): String {
        val ids = opf.doc.root.descendants().mapNotNull { it.attributes["id"] }.toSet()
        var c = base.replace(Regex("[^A-Za-z0-9_.-]"), "_").let { if (it.firstOrNull()?.isLetter() == true) it else "id_$it" }
        var n = 2
        val start = c
        while (c in ids) c = "$start-${n++}"
        return c
    }

    // ---- contents helpers ---------------------------------------------------------------------

    /** The contents with entries for [path] given a new title (or one added where it belongs). */
    private fun tocWithTitle(structure: BookStructure, path: String, title: String): List<TocNode> {
        val flat = Contents.flatten(structure.toc).toMutableList()
        val idx = flat.indexOfFirst { it.first.path == path && it.first.fragment == null && it.first.linked }.takeIf { it >= 0 }
            ?: flat.indexOfFirst { it.first.path == path && it.first.linked }
        if (idx >= 0) {
            flat[idx] = flat[idx].first.copy(title = title) to flat[idx].second
            return Contents.nest(flat)
        }
        return Contents.nest(insertInOrder(structure, flat, TocNode(title, path, null)))
    }

    /** Puts a new top-level entry where its chapter falls in the reading order. */
    private fun insertInOrder(structure: BookStructure, flat: MutableList<Pair<TocNode, Int>>, node: TocNode, order: List<String> = structure.chapters.map { it.path }): MutableList<Pair<TocNode, Int>> {
        val at = order.indexOf(node.path)
        val pos = flat.indexOfFirst { (n, _) -> order.indexOf(n.path).let { it > at || it < 0 } }.let { if (it < 0) flat.size else it }
        // Beside its neighbours: under the same heading as the entry before it, if the next one is too.
        val before = if (pos > 0) flat[pos - 1].second else 0
        val after = if (pos < flat.size) flat[pos].second else 0
        val level = minOf(before, after).let { if (pos > 0 && flat[pos - 1].second < after) after else it }
        flat.add(pos, node to level)
        return flat
    }

    /** Contents entries sorted into the reading order [order], keeping their levels. */
    private fun tocInOrder(toc: List<TocNode>, order: List<String>): List<TocNode> {
        val flat = Contents.flatten(toc)
        val sorted = flat.withIndex().sortedWith(compareBy({ order.indexOf(it.value.first.path).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.index }))
        return Contents.nest(sorted.map { it.value })
    }

    // ---- links between files ------------------------------------------------------------------

    private val linkAttr = Regex("""(\s(?:href|src|xlink:href)\s*=\s*)(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)

    /**
     * Rewrites links in [text] (a file at [docPath]): [map] gets each link's target file and fragment and
     * returns the new target, or null to leave it.
     */
    fun rewriteLinks(
        text: String,
        docPath: String,
        /** Where the text will live: new links are written relative to it. */
        writeFrom: String = docPath,
        map: (path: String, fragment: String?) -> Pair<String, String?>?,
    ): String {
        val dir = docPath.substringBeforeLast('/', "")
        return linkAttr.replace(text) { m ->
            val raw = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val value = Xml.unescape(raw)
            if (value.isEmpty() || value.contains(':') && value.substringBefore(':').all { it.isLetter() } || value.startsWith("//")) return@replace m.value
            val target = if (value.startsWith("#")) docPath else EpubFile.resolvePath(dir, value)
            val fragment = value.substringAfter('#', "").ifEmpty { null }
            val (newPath, newFragment) = map(target, fragment) ?: return@replace m.value
            val href = (if (newPath == writeFrom && newFragment != null) "" else Contents.encodeHref(EpubFile.relativeHref(writeFrom, newPath))) +
                (newFragment?.let { "#$it" } ?: "")
            m.groupValues[1] + "\"" + Xml.escapeAttr(href) + "\""
        }
    }

    /** [rewriteLinks] over every text file in the book (but not the package file or NCX). */
    private fun rewriteAll(files: BookFiles, structure: BookStructure, skip: Set<String>, changes: MutableMap<String, ByteArray?>, map: (String, String?) -> Pair<String, String?>?) {
        for (item in structure.opf.items) {
            if (!item.isContent) continue
            val path = structure.pathOf(item)
            if (path in skip) continue
            val text = changes[path]?.let { EpubFile.decodeText(it) } ?: files.text(path) ?: continue
            val fixed = rewriteLinks(text, path, map = map)
            if (fixed != text) changes[path] = bytes(fixed)
        }
    }

    /** The ids in a piece of XHTML. */
    private val idAttr = Regex("""\s(?:id|xml:id)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private fun idsIn(text: String): Set<String> = idAttr.findAll(text).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toSet()

    // ---- rename ------------------------------------------------------------------------------

    /** Gives a chapter a new title in the contents (and, if wanted, in its first heading). */
    fun rename(files: BookFiles, structure: BookStructure, path: String, title: String, alsoHeading: Boolean): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val clean = title.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw OpError(tr("Enter a name for the chapter."))
        val changes = LinkedHashMap<String, ByteArray?>()
        changes.putAll(Contents.write(files, structure, tocWithTitle(structure, path, clean), changes))
        if (alsoHeading) {
            val text = files.text(path)
            if (text != null) {
                val doc = Xml.parse(text)
                val heading = TextMap.body(doc).descendants().firstOrNull { TextMap.isHeading(it) }
                if (heading != null && !heading.selfClosing) {
                    val edits = TextEdits(text)
                    edits.replace(heading.startTagEnd, heading.endTagStart, Xml.escapeText(clean))
                    changes[path] = bytes(edits.apply())
                }
            }
        }
        return Result(tr("Renamed “{0}” to “{1}”", chapter.title, clean), changes, path)
    }

    // ---- reorder -----------------------------------------------------------------------------

    /** Puts the chapters in [newOrder] (paths); the contents follow. */
    fun reorder(files: BookFiles, structure: BookStructure, newOrder: List<String>, label: String = tr("Moved a chapter")): Result {
        val opf = structure.opf
        val spine = opf.spineElement ?: throw OpError(tr("The book has no reading order."))
        val refs = opf.spineRefs()
        val pathOfRef = refs.associateWith { r -> opf.item(r.idref)?.let { structure.pathOf(it) } }
        val content = refs.filter { r -> opf.item(r.idref)?.isContent == true }
        if (newOrder.toSet() != content.mapNotNull { pathOfRef[it] }.toSet()) throw OpError(tr("The chapters changed while they were being moved."))
        // Other things in the reading order (pictures, say) stay after the chapter they followed.
        val followers = LinkedHashMap<SpineKey, MutableList<OpfDocument.SpineRef>>()
        var last: OpfDocument.SpineRef? = null
        val leading = ArrayList<OpfDocument.SpineRef>()
        for (r in refs) {
            if (r in content) last = r
            else if (last == null) leading += r
            else followers.getOrPut(SpineKey(last)) { mutableListOf() } += r
        }
        val byPath = content.associateBy { pathOfRef[it]!! }
        val ordered = leading + newOrder.flatMap { p -> listOf(byPath[p]!!) + followers[SpineKey(byPath[p]!!)].orEmpty() }
        val text = structure.opfText
        val indent = opf.indentOf(spine) ?: "    "
        val inner = ordered.joinToString("") { "\n" + indent + text.substring(it.element.start, it.element.end) } +
            "\n" + (indentBefore(text, spine))
        val edits = TextEdits(text)
        edits.replace(spine.startTagEnd, spine.endTagStart, inner)
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(edits.apply())
        changes.putAll(Contents.write(files, structure, tocInOrder(structure.toc, newOrder), changes))
        return Result(label, changes)
    }

    private data class SpineKey(val start: Int) {
        constructor(r: OpfDocument.SpineRef) : this(r.element.start)
    }

    private fun indentBefore(text: String, e: XElement): String {
        var s = e.start
        while (s > 0 && (text[s - 1] == ' ' || text[s - 1] == '\t')) s--
        return if (s > 0 && text[s - 1] == '\n') text.substring(s, e.start) else ""
    }

    // ---- hide / show -------------------------------------------------------------------------

    /**
     * Marks a chapter as outside the reading order (readers skip it when turning pages straight through),
     * or puts it back. Its contents entry stays, so it can still be reached from there.
     */
    fun setHidden(files: BookFiles, structure: BookStructure, path: String, hidden: Boolean): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val ref = spineRef(structure.opf, chapter.id) ?: throw OpError(tr("That chapter isn't in the reading order."))
        val attrs = LinkedHashMap(ref.element.attributes)
        if (hidden) attrs["linear"] = "no" else attrs.remove("linear")
        val edits = TextEdits(structure.opfText)
        edits.replace(ref.element.start, ref.element.startTagEnd, Xml.startTag(ref.element.name, attrs, ref.element.selfClosing))
        return Result(if (hidden) tr("Hid “{0}”", chapter.title) else tr("Showed “{0}” again", chapter.title), mapOf(structure.opfPath to bytes(edits.apply())), path)
    }

    // ---- guide and landmarks -----------------------------------------------------------------

    /** The page the guide's reference of one of [types] points at. */
    private fun guidePath(structure: BookStructure, types: Set<String>): String? =
        structure.opf.guideElement?.elements()?.firstOrNull { e -> e.localName.equals("reference", true) && types.any { e.attributes["type"].equals(it, true) } }
            ?.attributes?.get("href")?.substringBefore('#')?.let { EpubFile.resolvePath(structure.opfDir, it) }

    /** The page the navigation document's landmark of [type] points at. */
    private fun landmarkPath(structure: BookStructure, type: String): String? {
        val nav = structure.opf.navItem() ?: return null
        val navPath = structure.pathOf(nav)
        val text = structure.source.text(navPath) ?: return null
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return null
        val link = doc.root.descendants().firstOrNull { a ->
            a.localName.equals("a", true) && a.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains(type) }
        } ?: return null
        return link.attributes["href"]?.substringBefore('#')?.let { EpubFile.resolvePath(navPath.substringBeforeLast('/', ""), it) }
    }

    /** The package file [text] with its guide's reference of one of [types] pointing at [href] as a [type] (added if there's none). */
    private fun withGuideRef(text: String, types: Set<String>, type: String, title: String, href: String): String {
        val opf = OpfDocument(text)
        val edits = TextEdits(text)
        val guide = opf.guideElement
        val existing = guide?.elements()?.firstOrNull { e -> e.localName.equals("reference", true) && types.any { e.attributes["type"].equals(it, true) } }
        val refName = guide?.prefix?.let { "$it:reference" } ?: (opf.pkg.prefix?.let { "$it:reference" } ?: "reference")
        val newRef = "<$refName type=\"$type\" title=\"${Xml.escapeAttr(title)}\" href=\"${Xml.escapeAttr(href)}\"/>"
        when {
            existing != null -> edits.replace(existing.start, existing.end, newRef)
            guide != null && !guide.selfClosing -> edits.insert(guide.endTagStart, "  $newRef\n" + indentBefore(text, guide))
            guide != null -> edits.replace(guide.start, guide.end, "<${guide.name}>\n${indentBefore(text, guide)}  $newRef\n${indentBefore(text, guide)}</${guide.name}>")
            else -> {
                val spine = opf.spineElement ?: throw OpError(tr("The book has no reading order."))
                val ind = indentBefore(text, spine)
                val guideName = opf.pkg.prefix?.let { "$it:guide" } ?: "guide"
                edits.insert(spine.end, "\n$ind<$guideName>\n$ind  $newRef\n$ind</$guideName>")
            }
        }
        return edits.apply()
    }

    /**
     * The navigation document with its landmark of [type] pointing at [target] (added at the top of the
     * landmarks if there's none), for a book with landmarks; null for one without.
     */
    private fun withLandmark(files: BookFiles, structure: BookStructure, type: String, title: String, target: String): Pair<String, ByteArray>? {
        val nav = structure.opf.navItem() ?: return null
        val navPath = structure.pathOf(nav)
        val navText = files.text(navPath) ?: return null
        val doc = runCatching { Xml.parse(navText) }.getOrNull() ?: return null
        val landmarks = doc.root.descendants().firstOrNull { e ->
            e.localName.equals("nav", true) && e.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("landmarks") }
        } ?: return null
        val navHref = Contents.encodeHref(EpubFile.relativeHref(navPath, target))
        val link = landmarks.descendants().firstOrNull { a ->
            a.localName.equals("a", true) && a.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains(type) }
        }
        val ne = TextEdits(navText)
        if (link != null) {
            val attrs = LinkedHashMap(link.attributes).apply { this["href"] = navHref }
            ne.replace(link.start, link.startTagEnd, Xml.startTag(link.name, attrs, link.selfClosing))
        } else {
            val ol = landmarks.elements().firstOrNull { it.localName.equals("ol", true) } ?: return null
            val typeAttr = landmarks.attributes.keys.first { it.substringAfter(':') == "type" }
            ne.insert(ol.startTagEnd, "\n${indentBefore(navText, ol)}  <li><a $typeAttr=\"$type\" href=\"${Xml.escapeAttr(navHref)}\">${Xml.escapeText(title)}</a></li>")
        }
        return navPath to bytes(ne.apply())
    }

    // ---- start -------------------------------------------------------------------------------

    /** Where the book opens: the guide's "text" entry and the navigation document's "bodymatter" landmark. */
    fun startPath(structure: BookStructure): String? = guidePath(structure, setOf("text", "start")) ?: landmarkPath(structure, "bodymatter")

    fun setStart(files: BookFiles, structure: BookStructure, path: String): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, path))
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(withGuideRef(structure.opfText, setOf("text", "start"), "text", "Start", href))
        // The EPUB 3 landmark, if the book has landmarks.
        withLandmark(files, structure, "bodymatter", "Start", path)?.let { (p, b) -> changes[p] = b }
        return Result(tr("The book now opens at “{0}”", chapter.title), changes, path)
    }

    // ---- cover page --------------------------------------------------------------------------

    /** A picture to put in the book: its bytes, type, and size in pixels. */
    class NewPicture(val data: ByteArray, val mediaType: String, val width: Int, val height: Int)

    /** The book's cover page: the page its guide or landmarks call the cover. */
    fun coverPagePath(structure: BookStructure): String? =
        listOfNotNull(guidePath(structure, setOf("cover")), landmarkPath(structure, "cover")).firstOrNull { structure.chapterAt(it) != null }

    /** The book's cover picture: the one it declares, or failing that one named as the cover. */
    fun coverImagePath(files: BookFiles, structure: BookStructure): String? =
        (structure.opf.declaredCoverItem() ?: structure.opf.guessedCoverItem())?.let { structure.pathOf(it) }?.takeIf { files.has(it) }

    /** Stops if the book names a cover page already. */
    fun checkNoCoverPage(files: BookFiles, structure: BookStructure) {
        coverPagePath(structure)?.let { p ->
            throw OpError(tr("The book already has a cover page, “{0}”.", structure.chapterAt(p)?.title ?: p))
        }
    }

    /**
     * A first page that is the book's cover page already, though the book doesn't name it as one: little more
     * than a picture, and that picture is the book's cover (or the book names no cover picture, so this one is
     * what it shows as its cover). A first page with some other picture (a title page, as a book split from an
     * omnibus often opens on) isn't one.
     */
    fun unnamedCoverPage(files: BookFiles, structure: BookStructure): String? {
        if (coverPagePath(structure) != null) return null
        val first = structure.chapters.firstOrNull() ?: return null
        val html = files.text(first.path) ?: return null
        val ref = EpubFile.firstImageReference(html) ?: return null
        if (EpubText.plainText(html).trim().length >= 40) return null
        val cover = coverImagePath(files, structure) ?: return first.path
        return first.path.takeIf { EpubFile.resolvePath(first.path.substringBeforeLast('/', ""), ref) == cover }
    }

    /**
     * Makes the chapter at [path] the book's cover page: named so in the guide (and the landmarks, in a
     * book that has them) and moved to the front.
     */
    fun setCoverPage(files: BookFiles, structure: BookStructure, path: String): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, path))
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(withGuideRef(structure.opfText, setOf("cover"), "cover", "Cover", href))
        var overlay: BookFiles = OverlayFiles(files, changes.toMap())
        var now = BookStructure(overlay)
        if (now.chapters.firstOrNull()?.path != path) {
            changes.putAll(reorder(overlay, now, listOf(path) + now.chapters.map { it.path }.filter { it != path }).changes)
            overlay = OverlayFiles(files, changes.toMap())
            now = BookStructure(overlay)
        }
        withLandmark(overlay, now, "cover", "Cover", path)?.let { (p, b) -> changes[p] = b }
        return Result(tr("“{0}” is now the cover page", chapter.title), changes, path)
    }

    /**
     * Adds a cover page at the front of the book: the whole page a picture of the cover. With [picture],
     * that picture is added and made the book's cover; without, the book's own cover picture is used.
     */
    fun addCoverPage(files: BookFiles, structure: BookStructure, picture: NewPicture?): Result {
        checkNoCoverPage(files, structure)
        // The first page is the cover page in all but name: it's named, and no second one is made.
        if (picture == null) unnamedCoverPage(files, structure)?.let { return setCoverPage(files, structure, it) }
        val changes = LinkedHashMap<String, ByteArray?>()
        var opfText = structure.opfText
        val imagePath: String
        val size: Pair<Int, Int>
        if (picture != null) {
            imagePath = newPicturePath(files, structure, if (picture.mediaType == "image/png") "png" else "jpg", name = "cover")
            changes[imagePath] = picture.data
            opfText = OpfDocument(opfText).withCover(EpubFile.relativeHref(structure.opfPath, imagePath), picture.mediaType)
            size = picture.width to picture.height
        } else {
            imagePath = coverImagePath(files, structure) ?: throw OpError(tr("The book has no cover picture."))
            size = files.bytes(imagePath)?.let { dev.kolib.app.reader.ImageSize.of(it) } ?: throw OpError(tr("The book's cover picture couldn't be read."))
        }

        // The page, in the folder the book's first page is in.
        val dir = structure.chapters.firstOrNull()?.path?.substringBeforeLast('/', "")
            ?: (if (structure.opfDir.isEmpty()) "text" else "${structure.opfDir}/text")
        var n = 1
        fun candidate() = (if (dir.isEmpty()) "" else "$dir/") + (if (n == 1) "cover.xhtml" else "cover-$n.xhtml")
        while (files.has(candidate())) n++
        val pagePath = candidate()
        val opf = OpfDocument(opfText)
        val lang = opf.readMetadata().language?.takeIf { it.isNotBlank() } ?: "en"
        changes[pagePath] = bytes(coverPage(lang, Contents.encodeHref(EpubFile.relativeHref(pagePath, imagePath)), size.first, size.second, opf.isEpub3))

        // Listed in the package file, first in the reading order, and named the cover in the guide.
        val manifest = opf.manifestElement ?: throw OpError(tr("The book has no list of files."))
        val spine = opf.spineElement ?: throw OpError(tr("The book has no reading order."))
        val id = uniqueId(opf, "cover-page")
        val pageHref = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, pagePath))
        val oe = TextEdits(opfText)
        val itemName = manifest.prefix?.let { "$it:item" } ?: "item"
        val svg = if (opf.isEpub3) " properties=\"svg\"" else ""
        oe.insert(
            manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd,
            "\n${opf.indentOf(manifest) ?: "    "}<$itemName id=\"$id\" href=\"${Xml.escapeAttr(pageHref)}\" media-type=\"application/xhtml+xml\"$svg/>",
        )
        val firstRef = spine.elements().firstOrNull()
        val refName = firstRef?.name ?: spine.prefix?.let { "$it:itemref" } ?: "itemref"
        val indent = opf.indentOf(spine) ?: "    "
        if (firstRef != null) oe.insert(firstRef.start, "<$refName idref=\"$id\"/>\n$indent")
        else oe.insert(spine.startTagEnd, "\n$indent<$refName idref=\"$id\"/>")
        changes[structure.opfPath] = bytes(withGuideRef(oe.apply(), setOf("cover"), "cover", "Cover", pageHref))

        val overlay = OverlayFiles(files, changes.toMap())
        withLandmark(overlay, BookStructure(overlay), "cover", "Cover", pagePath)?.let { (p, b) -> changes[p] = b }
        return Result(if (picture != null) tr("Made a cover and a cover page") else tr("Made a cover page"), changes, pagePath)
    }

    /** A page that is the whole of a picture [w] × [h] (at [href]), kept in proportion on any screen. */
    private fun coverPage(lang: String, href: String, w: Int, h: Int, epub3: Boolean): String {
        val l = Xml.escapeAttr(lang)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append(if (epub3) "<!DOCTYPE html>\n" else "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
            append("<html xmlns=\"http://www.w3.org/1999/xhtml\"")
            if (epub3) append(" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"$l\" lang=\"$l\"") else append(" xml:lang=\"$l\"")
            append(">\n<head>\n  <title>Cover</title>\n  <style type=\"text/css\">\n")
            append("    @page { margin: 0; padding: 0; }\n")
            append("    html, body { margin: 0; padding: 0; height: 100%; text-align: center; }\n")
            append("    div.cover { height: 100%; margin: 0; padding: 0; }\n")
            append("  </style>\n</head>\n")
            append(if (epub3) "<body epub:type=\"cover\">\n" else "<body>\n")
            append("  <div class=\"cover\">\n")
            append("    <svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"100%\" height=\"100%\" viewBox=\"0 0 $w $h\" preserveAspectRatio=\"xMidYMid meet\">\n")
            append("      <image width=\"$w\" height=\"$h\" xlink:href=\"${Xml.escapeAttr(href)}\"/>\n")
            append("    </svg>\n  </div>\n</body>\n</html>\n")
        }
    }

    // ---- delete ------------------------------------------------------------------------------

    fun delete(files: BookFiles, structure: BookStructure, path: String): Result = delete(files, structure, listOf(path))

    /** Deletes the chapters at [paths] together, as one change. */
    fun delete(files: BookFiles, structure: BookStructure, paths: Collection<String>): Result {
        val gone = paths.toSet()
        val chapters = gone.map { structure.chapterAt(it) ?: throw OpError(tr("That chapter is no longer in the book.")) }.sortedBy { it.index }
        if (chapters.isEmpty()) throw OpError(tr("That chapter is no longer in the book."))
        if (structure.chapters.size <= gone.size) throw OpError(tr("A book must have at least one chapter."))
        val opf = structure.opf
        val text = structure.opfText
        val edits = TextEdits(text)
        for (chapter in chapters) {
            val item = opf.item(chapter.id) ?: throw OpError(tr("That chapter isn't in the book's list of files."))
            edits.removeWithLeadingSpace(item.element)
            opf.spineRefs().filter { it.idref == chapter.id }.forEach { edits.removeWithLeadingSpace(it.element) }
        }
        opf.guideElement?.elements()?.filter { r ->
            r.localName.equals("reference", true) && r.attributes["href"]?.let { EpubFile.resolvePath(structure.opfDir, it) } in gone
        }?.forEach { edits.removeWithLeadingSpace(it) }
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(edits.apply())
        for (p in gone) changes[p] = null
        // Links to them from elsewhere become plain text.
        for (other in structure.chapters) {
            if (other.path in gone) continue
            val t = files.text(other.path) ?: continue
            val fixed = unwrapLinksTo(t, other.path, gone)
            if (fixed != t) changes[other.path] = bytes(fixed)
        }
        // Their contents entries go; entries under them move up, a level for each that went.
        val kept = ArrayList<Pair<TocNode, Int>>()
        val removedLevels = ArrayList<Int>()
        for ((node, level) in Contents.flatten(structure.toc)) {
            while (removedLevels.isNotEmpty() && level <= removedLevels.last()) removedLevels.removeAt(removedLevels.size - 1)
            if (node.path in gone) {
                removedLevels += level
                continue
            }
            kept += node to level - removedLevels.size
        }
        changes.putAll(Contents.write(files, structure, Contents.nest(kept), changes))
        // The navigation document may itself point at them (landmarks).
        opf.navItem()?.let { nav ->
            val navPath = structure.pathOf(nav)
            val navText = changes[navPath]?.let { EpubFile.decodeText(it) } ?: files.text(navPath) ?: return@let
            val fixed = removeLandmarksTo(navText, navPath, gone)
            if (fixed != navText) changes[navPath] = bytes(fixed)
        }
        // The chapter after the last one deleted opens, or else the one before the first.
        val next = structure.chapters.drop(chapters.last().index + 1).firstOrNull { it.path !in gone }
            ?: structure.chapters.take(chapters.first().index).lastOrNull { it.path !in gone }
        val label = if (chapters.size == 1) tr("Deleted “{0}”", chapters[0].title) else tr("Deleted {0} chapters", chapters.size)
        return Result(label, changes, next?.path)
    }

    private fun unwrapLinksTo(text: String, docPath: String, targets: Set<String>): String {
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return text
        val dir = docPath.substringBeforeLast('/', "")
        val edits = TextEdits(text)
        var any = false
        for (a in TextMap.body(doc).descendants()) {
            if (!a.localName.equals("a", true)) continue
            val href = a.attributes["href"] ?: continue
            if (href.startsWith("#") || href.contains("://")) continue
            if (EpubFile.resolvePath(dir, href) !in targets) continue
            if (a.selfClosing) edits.replace(a.start, a.end, "")
            else {
                edits.replace(a.start, a.startTagEnd, "")
                edits.replace(a.endTagStart, a.end, "")
            }
            any = true
        }
        return if (any) edits.apply() else text
    }

    private fun removeLandmarksTo(text: String, navPath: String, targets: Set<String>): String {
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return text
        val dir = navPath.substringBeforeLast('/', "")
        val edits = TextEdits(text)
        var any = false
        for (nav in doc.root.descendants().filter { it.localName.equals("nav", true) }) {
            val isToc = nav.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("toc") }
            if (isToc) continue
            for (li in nav.descendants().filter { it.localName.equals("li", true) }) {
                val a = li.elements().firstOrNull { it.localName.equals("a", true) } ?: continue
                val href = a.attributes["href"] ?: continue
                if (EpubFile.resolvePath(dir, href) in targets) {
                    edits.removeWithLeadingSpace(li)
                    any = true
                }
            }
        }
        return if (any) edits.apply() else text
    }

    // ---- merge -------------------------------------------------------------------------------

    /** Joins a chapter onto the end of the one before it. */
    fun mergeWithPrevious(files: BookFiles, structure: BookStructure, path: String): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val previous = structure.chapters.getOrNull(chapter.index - 1) ?: throw OpError(tr("It's the first chapter, so there's no previous chapter to join it with."))
        val a = files.text(previous.path) ?: throw OpError(tr("The previous chapter can't be read."))
        val b = files.text(path) ?: throw OpError(tr("The chapter can't be read."))
        val docA = Xml.parse(a)
        val docB = Xml.parse(b)
        val bodyA = TextMap.body(docA)
        val bodyB = TextMap.body(docB)
        if (bodyA === docA.root || bodyB === docB.root) throw OpError(tr("One of these chapters has no text section (body), so they can't be joined."))

        // Ids from the second chapter that are already used in the first get new names.
        val idsA = idsIn(a)
        val renamed = HashMap<String, String>()
        var innerB = b.substring(bodyB.startTagEnd, bodyB.endTagStart)
        for (id in idsIn(innerB)) {
            if (id in idsA) {
                var n = 2
                while ("$id-$n" in idsA || "$id-$n" in renamed.values) n++
                renamed[id] = "$id-$n"
            }
        }
        if (renamed.isNotEmpty()) {
            innerB = idAttr.replace(innerB) { m ->
                val id = m.groupValues[1].ifEmpty { m.groupValues[2] }
                renamed[id]?.let { m.value.replace(id, it) } ?: m.value
            }
        }
        // Links inside the second chapter, from its folder to the first's.
        val dirA = previous.path.substringBeforeLast('/', "")
        val dirB = path.substringBeforeLast('/', "")
        innerB = rewriteLinks(innerB, path, writeFrom = previous.path) { target, fragment ->
            when {
                target == path -> previous.path to (fragment?.let { renamed[it] ?: it })
                dirA != dirB -> target to fragment
                else -> null
            }
        }
        // The second chapter's body becomes a section, which keeps its class (and so its look).
        val wrapperId = bodyB.attributes["id"]?.let { renamed[it] ?: it }?.takeIf { it !in idsA } ?: run {
            var n = 1
            while ("kl-joined-$n" in idsA) n++
            "kl-joined-$n"
        }
        val wrapperAttrs = LinkedHashMap<String, String>().apply {
            put("id", wrapperId)
            bodyB.attributes["class"]?.let { put("class", it) }
            bodyB.attributes["style"]?.let { put("style", it) }
        }
        val divName = bodyA.prefix?.let { "$it:div" } ?: "div"
        val joined = "\n" + Xml.startTag(divName, wrapperAttrs, false) + innerB + "</$divName>\n"
        val edits = TextEdits(a)
        edits.insert(bodyA.endTagStart, joined)
        // Style sheets the second chapter used.
        val headA = docA.root.elements().firstOrNull { it.localName.equals("head", true) }
        val headB = docB.root.elements().firstOrNull { it.localName.equals("head", true) }
        if (headA != null && headB != null && !headA.selfClosing) {
            val haveA = headA.elements().filter { it.localName.equals("link", true) }
                .mapNotNull { it.attributes["href"]?.let { h -> EpubFile.resolvePath(dirA, h) } }.toSet()
            val extra = StringBuilder()
            for (e in headB.elements()) {
                when {
                    e.localName.equals("link", true) -> {
                        val target = e.attributes["href"]?.let { EpubFile.resolvePath(dirB, it) } ?: continue
                        if (target in haveA) continue
                        val attrs = LinkedHashMap(e.attributes).apply { this["href"] = Contents.encodeHref(EpubFile.relativeHref(previous.path, target)) }
                        extra.append("\n").append(Xml.startTag(e.name, attrs, true))
                    }
                    e.localName.equals("style", true) -> extra.append("\n").append(b.substring(e.start, e.end))
                }
            }
            if (extra.isNotEmpty()) edits.insert(headA.endTagStart, extra.toString() + "\n")
        }
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[previous.path] = bytes(edits.apply())
        changes[path] = null

        // Package file: the second chapter goes.
        val opf = structure.opf
        val oe = TextEdits(structure.opfText)
        opf.item(chapter.id)?.let { oe.removeWithLeadingSpace(it.element) }
        opf.spineRefs().filter { it.idref == chapter.id }.forEach { oe.removeWithLeadingSpace(it.element) }
        opf.guideElement?.elements()?.filter { r ->
            r.localName.equals("reference", true) && r.attributes["href"]?.let { EpubFile.resolvePath(structure.opfDir, it) } == path
        }?.forEach { oe.removeWithLeadingSpace(it) }
        changes[structure.opfPath] = bytes(oe.apply())

        // Links from anywhere else, and the contents, now point into the first chapter.
        val retarget: (String, String?) -> Pair<String, String?>? = { target, fragment ->
            if (target == path) previous.path to (fragment?.let { renamed[it] ?: it } ?: wrapperId) else null
        }
        rewriteAll(files, structure, setOf(path), changes, retarget)
        val toc = Contents.nest(Contents.flatten(structure.toc).map { (n, l) ->
            if (n.path == path) n.copy(path = previous.path, fragment = n.fragment?.let { renamed[it] ?: it } ?: wrapperId) to l else n to l
        })
        changes.putAll(Contents.write(files, structure, toc, changes))
        opf.navItem()?.let { nav ->
            val navPath = structure.pathOf(nav)
            val navText = changes[navPath]?.let { EpubFile.decodeText(it) } ?: files.text(navPath) ?: return@let
            val fixed = rewriteLinks(navText, navPath, map = retarget)
            if (fixed != navText) changes[navPath] = bytes(fixed)
        }
        return Result(tr("Joined “{0}” with “{1}”", chapter.title, previous.title), changes, previous.path)
    }

    // ---- split -------------------------------------------------------------------------------

    /** Places a chapter can be split: its paragraphs and headings, with a little of their text. */
    data class SplitPoint(val offset: Int, val tag: String, val text: String)

    fun splitPoints(text: String): List<SplitPoint> {
        val doc = Xml.parse(text)
        val body = TextMap.body(doc)
        val out = ArrayList<SplitPoint>()
        fun walk(e: XElement) {
            for (c in e.elements()) {
                val name = c.localName.lowercase()
                val leafBlock = name in setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "hr", "img", "figure", "table", "ul", "ol", "pre") ||
                    (name == "div" && c.elements().none { TextMap.isBlock(it) && !it.localName.equals("br", true) })
                if (leafBlock) out += SplitPoint(c.start, name, EpubText.plainText(text.substring(c.start, c.end)).replace(Regex("\\s+"), " ").trim().take(140))
                else if (name in setOf("div", "section", "article", "main", "header", "footer", "aside")) walk(c)
            }
        }
        walk(body)
        // A chapter can't be split before its first paragraph.
        return out.drop(1)
    }

    /**
     * Splits a chapter so a new one starts at the element that begins at [offset]. Elements around it
     * are closed in the first part and opened again in the second.
     */
    fun split(files: BookFiles, structure: BookStructure, path: String, offset: Int, newTitle: String?): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val text = files.text(path) ?: throw OpError(tr("The chapter can't be read."))
        val doc = Xml.parse(text)
        val body = TextMap.body(doc)
        val at = body.descendants().firstOrNull { it.start == offset } ?: throw OpError(tr("That spot is no longer in the chapter."))
        val ancestors = generateSequence(at.parent) { it.parent }.takeWhile { it !== body }.toList().reversed()
        if (at.parent == null || generateSequence(at.parent) { it.parent }.none { it === body }) throw OpError(tr("A chapter can only be split within its text."))
        val firstText = text.substring(body.startTagEnd, at.start)
        if (EpubText.plainText(firstText).isBlank() && !firstText.contains("<img", true)) throw OpError(tr("That's already the start of the chapter."))

        val close = ancestors.reversed().joinToString("") { "</${it.name}>" }
        val reopen = ancestors.joinToString("") { e -> Xml.startTag(e.name, LinkedHashMap(e.attributes).apply { remove("id") }, false) }
        val first = text.substring(0, at.start).trimEnd() + "\n" + close + "\n" + text.substring(body.endTagStart)
        val second = text.substring(0, body.startTagEnd) + "\n" + reopen + text.substring(at.start)

        // The new file, next to the first.
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "xhtml")
        var n = 2
        fun candidate() = (if (dir.isEmpty()) "" else "$dir/") + "$base-$n.$ext"
        while (files.has(candidate())) n++
        val newPath = candidate()

        val opf = structure.opf
        val item = opf.item(chapter.id) ?: throw OpError(tr("That chapter isn't in the book's list of files."))
        val ref = spineRef(opf, chapter.id) ?: throw OpError(tr("That chapter isn't in the reading order."))
        val newId = uniqueId(opf, "${chapter.id}-$n")
        val oe = TextEdits(structure.opfText)
        val itemName = item.element.name
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, newPath))
        oe.insert(item.element.end, "\n${opf.indentOf(opf.manifestElement!!) ?: "    "}<$itemName id=\"$newId\" href=\"${Xml.escapeAttr(href)}\" media-type=\"${item.mediaType}\"/>")
        val refAttrs = LinkedHashMap<String, String>().apply {
            put("idref", newId)
            if (!ref.linear) put("linear", "no")
        }
        oe.insert(ref.element.end, "\n${opf.indentOf(opf.spineElement!!) ?: "    "}" + Xml.startTag(ref.element.name, refAttrs, true))

        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(oe.apply())
        changes[path] = bytes(first)
        changes[newPath] = bytes(second)

        // Links to anything that's now in the second part follow it there.
        val moved = idsIn(text.substring(at.start, body.endTagStart))
        val retarget: (String, String?) -> Pair<String, String?>? = { target, fragment ->
            if (target == path && fragment != null && fragment in moved) newPath to fragment else null
        }
        rewriteAll(files, structure, emptySet(), changes, retarget)
        // Links inside the new part were written for the old file: "#note" may now be in either part.
        changes[newPath]?.let { bytesNew ->
            val t = EpubFile.decodeText(bytesNew)
            val fixed = rewriteLinks(t, path, writeFrom = newPath) { target, fragment ->
                if (target == path) (if (fragment != null && fragment in moved) newPath else path) to fragment else null
            }
            if (fixed != t) changes[newPath] = bytes(fixed)
        }

        // Contents: entries now in the second part move; the new chapter gets an entry of its own.
        val title = newTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: TextMap.body(Xml.parse(second)).descendants().firstOrNull { TextMap.isHeading(it) }?.text()?.let { BookStructure.clean(it) }?.takeIf { it.isNotEmpty() }
            ?: tr("{0} (continued)", chapter.title)
        val flat = Contents.flatten(structure.toc).map { (node, level) ->
            if (node.path == path && node.fragment != null && node.fragment in moved) node.copy(path = newPath) to level else node to level
        }.toMutableList()
        val startIds = idsIn(text.substring(at.start, minOf(body.endTagStart, at.start + 600)))
        if (flat.none { it.first.path == newPath && (it.first.fragment == null || it.first.fragment in startIds) }) {
            val order = structure.chapters.map { it.path }.toMutableList().apply { add(indexOf(path) + 1, newPath) }
            val lastOfFirst = flat.indexOfLast { it.first.path == path }
            if (lastOfFirst >= 0) {
                val level = flat.filter { it.first.path == path }.minOf { it.second }
                flat.add(lastOfFirst + 1, TocNode(title, newPath, null) to level)
            } else {
                insertInOrder(structure, flat, TocNode(title, newPath, null), order)
            }
        }
        changes.putAll(Contents.write(files, structure, Contents.nest(flat), changes))
        return Result(tr("Split “{0}”", chapter.title), changes, newPath)
    }

    // ---- new chapters --------------------------------------------------------------------------

    private val stylesheetLink = Regex("""<link\b[^>]*\brel\s*=\s*["']?stylesheet[^>]*>""", RegexOption.IGNORE_CASE)
    private val hrefIn = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val bodyStart = Regex("""<body\b""", RegexOption.IGNORE_CASE)
    private val headingTag = Regex("""<h([1-3])\b""", RegexOption.IGNORE_CASE)

    /**
     * A new, empty chapter called [title], after [afterPath] (or after the last chapter read through).
     * It goes in the contents after that chapter's entries, beside the entry that follows if that's a
     * chapter under the same heading (so a chapter added after a part's own page is the part's first
     * chapter), and it looks like its neighbour at that level: the same style sheets and kind of heading.
     */
    fun addChapter(files: BookFiles, structure: BookStructure, afterPath: String?, title: String): Result {
        val name = title.trim().ifEmpty { throw OpError(tr("Enter a name for the chapter.")) }
        val opf = structure.opf
        val manifest = opf.manifestElement ?: throw OpError(tr("The book has no list of files."))
        val spineEl = opf.spineElement ?: throw OpError(tr("The book has no reading order."))
        val after = afterPath?.let { structure.chapterAt(it) } ?: structure.chapters.lastOrNull { it.linear } ?: structure.chapters.lastOrNull()
        val dir = after?.path?.substringBeforeLast('/', "") ?: (if (structure.opfDir.isEmpty()) "text" else "${structure.opfDir}/text")
        var n = structure.chapters.size + 1
        fun candidate() = (if (dir.isEmpty()) "" else "$dir/") + "chapter-$n.xhtml"
        while (files.has(candidate())) n++
        val newPath = candidate()

        // Where it goes in the contents, and which chapter it's a sibling of.
        val node = TocNode(name, newPath, null)
        val flat = Contents.flatten(structure.toc).toMutableList()
        val last = after?.let { a -> flat.indexOfLast { it.first.path == a.path } } ?: -1
        var model = after?.path
        if (last >= 0) {
            val own = flat.filter { it.first.path == after!!.path }.minOf { it.second }
            val next = flat.getOrNull(last + 1)
            val level = if (next != null && next.second > own) { model = next.first.path; next.second } else own
            flat.add(last + 1, node to level)
        } else {
            val order = structure.chapters.map { it.path }.toMutableList()
            order.add(after?.let { order.indexOf(it.path) + 1 } ?: order.size, newPath)
            insertInOrder(structure, flat, node, order)
        }

        // Its style sheets and heading, from that sibling.
        val like = model?.let { files.text(it) }.orEmpty()
        val bodyAt = bodyStart.find(like)?.range?.first ?: like.length
        val modelDir = model?.substringBeforeLast('/', "").orEmpty()
        val styles = stylesheetLink.findAll(like.substring(0, bodyAt))
            .mapNotNull { m -> hrefIn.find(m.value)?.let { Xml.unescape(it.groupValues[1].ifEmpty { it.groupValues[2] }) } }
            .filter { !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it) }
            .map { EpubFile.relativeHref(newPath, EpubFile.resolvePath(modelDir, it)) }
            .toList()
        val heading = "h" + (headingTag.find(like, bodyAt)?.groupValues?.get(1) ?: "2")
        val lang = opf.readMetadata().language?.takeIf { it.isNotBlank() } ?: "en"
        val page = BookAssembly.chapterPage(name, lang, styles = styles, heading = heading)

        val id = uniqueId(opf, "chapter-$n")
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, newPath))
        val oe = TextEdits(structure.opfText)
        val afterItem = after?.let { opf.item(it.id) }
        val itemName = afterItem?.element?.name ?: manifest.prefix?.let { "$it:item" } ?: "item"
        oe.insert(
            afterItem?.element?.end ?: manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd,
            "\n${opf.indentOf(manifest) ?: "    "}<$itemName id=\"$id\" href=\"${Xml.escapeAttr(href)}\" media-type=\"application/xhtml+xml\"/>",
        )
        val afterRef = after?.let { spineRef(opf, it.id) }
        val refName = afterRef?.element?.name ?: spineEl.prefix?.let { "$it:itemref" } ?: "itemref"
        oe.insert(
            afterRef?.element?.end ?: spineEl.elements().lastOrNull()?.end ?: spineEl.startTagEnd,
            "\n${opf.indentOf(spineEl) ?: "    "}<$refName idref=\"$id\"/>",
        )
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(oe.apply())
        changes[newPath] = bytes(page)
        changes.putAll(Contents.write(files, structure, Contents.nest(flat), changes))
        return Result(tr("Added “{0}”", name), changes, newPath)
    }

    // ---- copying a page into another book ---------------------------------------------------------

    /** A file a copied page needs (a picture, a style sheet, a font), by its path in the book it came from. */
    class CopiedFile(val path: String, val mediaType: String, val bytes: ByteArray)

    /**
     * A page copied out of a book with all it needs to go into another: its [text], the [files] it uses (pictures,
     * style sheets, and the fonts and pictures those use), and, for a contents page, how deep each entry was in its
     * book's contents ([levels], by [ContentsPage.key]), so it can be made to list another book's chapters alike.
     */
    class CopiedPage(
        val title: String,
        /** Where it was in its book. */
        val path: String,
        val text: String,
        val files: List<CopiedFile>,
        val levels: Map<String, Int>,
        val isContents: Boolean,
    )

    private val usedAttr = Regex("""\s(?:src|href|xlink:href|poster|data)\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val cssUrl = Regex("""url\(\s*(['"]?)([^'")]+?)\1\s*\)|@import\s+(['"])([^'"]+)\3""", RegexOption.IGNORE_CASE)
    private val externalRef = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private fun isPageType(type: String) = type == "application/xhtml+xml" || type == "text/html" || type == "application/x-dtbncx+xml"

    /** The files [text] (at [path]) uses or links to, as paths in the book; [css] for a style sheet. */
    private fun referencesIn(text: String, path: String, css: Boolean): List<String> {
        val dir = path.substringBeforeLast('/', "")
        val raw = ArrayList<String>()
        if (!css) usedAttr.findAll(text).forEach { raw += it.groupValues[2] }
        cssUrl.findAll(text).forEach { m -> raw += m.groupValues[2].ifEmpty { m.groupValues[4] } }
        return raw.asSequence().map { Xml.unescape(it.trim()) }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") && !externalRef.containsMatchIn(it) }
            .map { EpubFile.resolvePath(dir, it) }.filter { it != path }.distinct().toList()
    }

    /** The page at [path] copied, with what it needs, to put into another book with [paste]. */
    fun copyPage(files: BookFiles, structure: BookStructure, path: String): CopiedPage {
        val chapter = structure.chapterAt(path) ?: throw OpError(tr("That chapter is no longer in the book."))
        val text = files.text(path) ?: throw OpError(tr("That chapter couldn't be read."))
        val types = structure.opf.items.associate { structure.pathOf(it) to it.mediaType }
        val needed = LinkedHashMap<String, String>()
        val queue = ArrayDeque(referencesIn(text, path, css = false))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val type = types[p] ?: continue
            if (p in needed || isPageType(type)) continue
            needed[p] = type
            if (type == "text/css") files.text(p)?.let { css -> queue += referencesIn(css, p, css = true) }
        }
        val copied = needed.mapNotNull { (p, type) -> files.bytes(p)?.let { CopiedFile(p, type, it) } }
        val contents = ContentsPage.isContents(text, path, chapter.title)
        return CopiedPage(chapter.title, path, text, copied, if (contents) ContentsPage.levelsOf(structure.toc) else emptyMap(), contents)
    }

    /** The folder a file of [mediaType] goes in: where most of the book's files of its kind are, else one for them. */
    private fun folderFor(structure: BookStructure, mediaType: String): String {
        fun kind(t: String) = when {
            t.startsWith("image/") -> "images"
            t == "text/css" -> "styles"
            t.startsWith("font/") || t.contains("font") || t.contains("opentype") -> "fonts"
            else -> "misc"
        }
        val wanted = kind(mediaType)
        return structure.opf.items.filter { kind(it.mediaType) == wanted }.map { structure.pathOf(it).substringBeforeLast('/', "") }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: listOf(structure.opfDir, wanted).filter { it.isNotEmpty() }.joinToString("/")
    }

    /** [name] in [folder], or "name-2" and so on, not taken in the book nor in [taken]. */
    private fun freePath(files: BookFiles, folder: String, name: String, taken: Set<String>): String {
        val base = name.substringBeforeLast('.').ifEmpty { "file" }
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        fun candidate() = (if (folder.isEmpty()) "" else "$folder/") + (if (n == 1) "$base$ext" else "$base-$n$ext")
        while (files.has(candidate()) || candidate() in taken) n++
        return candidate()
    }

    /** [css] (at [from]) with its url()s and @imports written for where it now is, [to], with [moved] files' new paths. */
    private fun rewriteCss(css: String, from: String, to: String, moved: Map<String, String>): String {
        val dir = from.substringBeforeLast('/', "")
        return cssUrl.replace(css) { m ->
            val raw = m.groupValues[2].ifEmpty { m.groupValues[4] }
            val value = raw.trim()
            if (value.isEmpty() || value.startsWith("#") || value.startsWith("//") || externalRef.containsMatchIn(value)) return@replace m.value
            val target = EpubFile.resolvePath(dir, value)
            val href = Contents.encodeHref(EpubFile.relativeHref(to, moved[target] ?: target))
            m.value.replace(raw, href)
        }
    }

    /**
     * [page], copied from another book, put into this one after [afterPath] (or after the last chapter read through):
     * with the pictures, style sheets and fonts it uses (one the book has already, the same, is used as it is), its
     * links written for where it is now, and its own entry in the contents. Links to pages this book hasn't got are
     * taken out (their words stay). A contents page can instead be made to list this book's chapters, in its own style
     * ([relist]).
     */
    fun paste(files: BookFiles, structure: BookStructure, afterPath: String?, page: CopiedPage, relist: Boolean = false): Result {
        val opf = structure.opf
        val manifest = opf.manifestElement ?: throw OpError(tr("The book has no list of files."))
        val spineEl = opf.spineElement ?: throw OpError(tr("The book has no reading order."))
        val after = afterPath?.let { structure.chapterAt(it) } ?: structure.chapters.lastOrNull { it.linear } ?: structure.chapters.lastOrNull()
        val dir = after?.path?.substringBeforeLast('/', "") ?: (if (structure.opfDir.isEmpty()) "text" else "${structure.opfDir}/text")
        val taken = HashSet<String>()
        val newPath = freePath(files, dir, page.path.substringAfterLast('/').ifEmpty { "page.xhtml" }, taken).also { taken += it }

        // Where its files go: one the book has already, the same, is used as it is.
        val moved = HashMap<String, String>()
        moved[page.path] = newPath
        val added = ArrayList<CopiedFile>()
        for (f in page.files) {
            val folder = folderFor(structure, f.mediaType)
            val name = f.path.substringAfterLast('/')
            val same = listOf(f.path, (if (folder.isEmpty()) "" else "$folder/") + name)
                .firstOrNull { p -> files.bytes(p)?.contentEquals(f.bytes) == true }
            if (same != null) {
                moved[f.path] = same
                continue
            }
            val target = freePath(files, folder, name, taken)
            taken += target
            moved[f.path] = target
            added += f
        }
        val changes = LinkedHashMap<String, ByteArray?>()
        for (f in added) {
            val target = moved.getValue(f.path)
            changes[target] = if (f.mediaType == "text/css") bytes(rewriteCss(EpubFile.decodeText(f.bytes), f.path, target, moved)) else f.bytes
        }

        // The page, its links written for where it is now (links to other pages still go where they went).
        var text = rewriteLinks(page.text, page.path, writeFrom = newPath) { p, fragment -> (moved[p] ?: p) to fragment }
        text = rewriteCss(text, page.path, newPath, moved)

        // Its entry in the contents: after the chapter it follows, beside it.
        val node = TocNode(page.title.ifBlank { newPath.substringAfterLast('/').substringBeforeLast('.') }, newPath, null)
        val flat = Contents.flatten(structure.toc).toMutableList()
        val last = after?.let { a -> flat.indexOfLast { it.first.path == a.path } } ?: -1
        if (last >= 0) {
            flat.add(last + 1, node to flat.filter { it.first.path == after!!.path }.minOf { it.second })
        } else {
            val order = structure.chapters.map { it.path }.toMutableList()
            order.add(after?.let { order.indexOf(it.path) + 1 } ?: order.size, newPath)
            insertInOrder(structure, flat, node, order)
        }
        val toc = Contents.nest(flat)
        if (relist) text = ContentsPage.relist(text, newPath, ContentsPage.entriesOf(toc, except = newPath), page.levels) ?: text
        val inBook = files.names().toSet()
        text = ContentsPage.unlinkMissing(text, newPath) { p -> p in inBook || p in changes || p == newPath }
        changes[newPath] = bytes(text)

        // The package file: the page and its files listed, and the page in the reading order after [after].
        val oe = TextEdits(structure.opfText)
        val indent = opf.indentOf(manifest) ?: "    "
        val itemName = manifest.prefix?.let { "$it:item" } ?: "item"
        val afterItem = after?.let { opf.item(it.id) }
        val pageId = uniqueId(opf, newPath.substringAfterLast('/').substringBeforeLast('.'))
        val props = if (opf.isEpub3 && Regex("""<(?:\w+:)?svg\b""").containsMatchIn(text)) " properties=\"svg\"" else ""
        val listed = StringBuilder()
        listed.append("\n$indent<$itemName id=\"$pageId\" href=\"${Xml.escapeAttr(Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, newPath)))}\" media-type=\"application/xhtml+xml\"$props/>")
        val ids = HashSet<String>().apply { add(pageId) }
        for (f in added) {
            var id = uniqueId(opf, "kl-" + moved.getValue(f.path).substringAfterLast('/').substringBeforeLast('.'))
            var n = 2
            val start = id
            while (id in ids) id = "$start-${n++}"
            ids += id
            listed.append("\n$indent<$itemName id=\"$id\" href=\"${Xml.escapeAttr(Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, moved.getValue(f.path))))}\" media-type=\"${Xml.escapeAttr(f.mediaType)}\"/>")
        }
        oe.insert(afterItem?.element?.end ?: manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd, listed.toString())
        val afterRef = after?.let { spineRef(opf, it.id) }
        val refName = afterRef?.element?.name ?: spineEl.prefix?.let { "$it:itemref" } ?: "itemref"
        oe.insert(
            afterRef?.element?.end ?: spineEl.elements().lastOrNull()?.end ?: spineEl.startTagEnd,
            "\n${opf.indentOf(spineEl) ?: "    "}<$refName idref=\"$pageId\"/>",
        )
        changes[structure.opfPath] = bytes(oe.apply())
        changes.putAll(Contents.write(files, structure, toc, changes))
        return Result(tr("Added “{0}”", node.title), changes, newPath)
    }

    /**
     * A copy of the chapter at [path], straight after it: the same words, pictures and styles, as a page of its own,
     * called the same in the contents with "(copy)" after.
     */
    fun duplicate(files: BookFiles, structure: BookStructure, path: String): Result {
        val page = copyPage(files, structure, path)
        val named = CopiedPage(tr("{0} (copy)", page.title.ifBlank { path.substringAfterLast('/').substringBeforeLast('.') }), page.path, page.text, page.files, page.levels, page.isContents)
        val r = paste(files, structure, path, named)
        return r.copy(label = tr("Duplicated “{0}”", page.title.ifBlank { path.substringAfterLast('/') }))
    }

    // ---- pictures ----------------------------------------------------------------------------

    /** Where a new picture goes: the folder most of the book's pictures are in, with a free name. */
    fun newPicturePath(files: BookFiles, structure: BookStructure, extension: String, name: String? = null): String {
        val folders = structure.opf.items.filter { it.isImage }.map { structure.pathOf(it).substringBeforeLast('/', "") }
        val dir = folders.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: (if (structure.opfDir.isEmpty()) "images" else "${structure.opfDir}/images")
        var n = 1
        fun candidate() = (if (dir.isEmpty()) "" else "$dir/") + when {
            name == null -> "picture-$n.$extension"
            n == 1 -> "$name.$extension"
            else -> "$name-$n.$extension"
        }
        while (files.has(candidate())) n++
        return candidate()
    }

    /** Adds a file to the book and lists it in the package file. */
    fun addFile(files: BookFiles, structure: BookStructure, path: String, data: ByteArray, mediaType: String): Result {
        val opf = structure.opf
        val manifest = opf.manifestElement ?: throw OpError(tr("The book has no list of files."))
        val id = uniqueId(opf, "kl-" + path.substringAfterLast('/').substringBeforeLast('.'))
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, path))
        val itemName = manifest.prefix?.let { "$it:item" } ?: "item"
        val edits = TextEdits(structure.opfText)
        val anchor = manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd
        edits.insert(anchor, "\n${opf.indentOf(manifest) ?: "    "}<$itemName id=\"$id\" href=\"${Xml.escapeAttr(href)}\" media-type=\"$mediaType\"/>")
        return Result(tr("Added a picture"), mapOf(structure.opfPath to bytes(edits.apply()), path to data))
    }

    // ---- contents from headings --------------------------------------------------------------

    /** Makes a new table of contents from the chapters' headings (giving headings ids where needed). */
    fun contentsFromHeadings(files: BookFiles, structure: BookStructure, deepest: Int = 2): Result {
        val changes = LinkedHashMap<String, ByteArray?>()
        val flat = ArrayList<Pair<TocNode, Int>>()
        val levels = (1..deepest).toSet()
        for (chapter in structure.chapters) {
            if (!chapter.linear) continue
            val text = files.text(chapter.path) ?: continue
            val doc = runCatching { Xml.parse(text) }.getOrNull() ?: continue
            val headings = TextMap.body(doc).descendants().filter { h ->
                TextMap.isHeading(h) && h.localName.last().digitToInt() in levels && BookStructure.clean(EpubText.plainText(text.substring(h.start, h.end))).isNotEmpty()
            }.toList()
            if (headings.isEmpty()) continue
            val edits = TextEdits(text)
            val ids = idsIn(text).toMutableSet()
            for ((i, h) in headings.withIndex()) {
                val title = BookStructure.clean(EpubText.plainText(text.substring(h.start, h.end))).take(200)
                val level = h.localName.last().digitToInt() - 1
                var id = h.attributes["id"]
                if (id == null && i > 0) {
                    var n = 1
                    while ("kl-h-$n" in ids) n++
                    id = "kl-h-$n"
                    ids += id
                    val attrs = LinkedHashMap(h.attributes).apply { this["id"] = id }
                    edits.replace(h.start, h.startTagEnd, Xml.startTag(h.name, attrs, h.selfClosing))
                }
                // The first heading stands for the chapter itself.
                flat += TocNode(title, chapter.path, if (i == 0) null else id) to level
            }
            val fixed = edits.apply()
            if (fixed != text) changes[chapter.path] = bytes(fixed)
        }
        if (flat.isEmpty()) throw OpError(tr("None of the chapters has a heading, so the contents can't be rebuilt."))
        val minLevel = flat.minOf { it.second }
        changes.putAll(Contents.write(files, structure, Contents.nest(flat.map { it.first to it.second - minLevel }), changes))
        return Result(tr("Rebuilt the contents from the headings"), changes)
    }
}
