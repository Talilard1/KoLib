package dev.kobolibrary.app.epub

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
        val chapter = structure.chapterAt(path) ?: throw OpError("That chapter is no longer in the book.")
        val clean = title.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw OpError("Enter a name for the chapter.")
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
        return Result("Renamed “${chapter.title}” to “$clean”", changes, path)
    }

    // ---- reorder -----------------------------------------------------------------------------

    /** Puts the chapters in [newOrder] (paths); the contents follow. */
    fun reorder(files: BookFiles, structure: BookStructure, newOrder: List<String>, label: String = "Moved a chapter"): Result {
        val opf = structure.opf
        val spine = opf.spineElement ?: throw OpError("The book has no reading order.")
        val refs = opf.spineRefs()
        val pathOfRef = refs.associateWith { r -> opf.item(r.idref)?.let { structure.pathOf(it) } }
        val content = refs.filter { r -> opf.item(r.idref)?.isContent == true }
        if (newOrder.toSet() != content.mapNotNull { pathOfRef[it] }.toSet()) throw OpError("The chapters changed while they were being moved.")
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
        val chapter = structure.chapterAt(path) ?: throw OpError("That chapter is no longer in the book.")
        val ref = spineRef(structure.opf, chapter.id) ?: throw OpError("That chapter isn't in the reading order.")
        val attrs = LinkedHashMap(ref.element.attributes)
        if (hidden) attrs["linear"] = "no" else attrs.remove("linear")
        val edits = TextEdits(structure.opfText)
        edits.replace(ref.element.start, ref.element.startTagEnd, Xml.startTag(ref.element.name, attrs, ref.element.selfClosing))
        return Result(if (hidden) "Hid “${chapter.title}”" else "Showed “${chapter.title}” again", mapOf(structure.opfPath to bytes(edits.apply())), path)
    }

    // ---- start -------------------------------------------------------------------------------

    /** Where the book opens: the guide's "text" entry and the navigation document's "bodymatter" landmark. */
    fun startPath(structure: BookStructure): String? {
        structure.opf.guideElement?.elements()?.firstOrNull {
            it.localName.equals("reference", true) && (it.attributes["type"].equals("text", true) || it.attributes["type"].equals("start", true))
        }?.attributes?.get("href")?.let { return EpubFile.resolvePath(structure.opfDir, it) }
        val nav = structure.opf.navItem() ?: return null
        val navPath = structure.pathOf(nav)
        val text = structure.source.text(navPath) ?: return null
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return null
        val link = doc.root.descendants().firstOrNull { a ->
            a.localName.equals("a", true) && a.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("bodymatter") }
        } ?: return null
        return link.attributes["href"]?.let { EpubFile.resolvePath(navPath.substringBeforeLast('/', ""), it) }
    }

    fun setStart(files: BookFiles, structure: BookStructure, path: String): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError("That chapter is no longer in the book.")
        val opf = structure.opf
        val text = structure.opfText
        val edits = TextEdits(text)
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, path))
        val guide = opf.guideElement
        val existing = guide?.elements()?.firstOrNull {
            it.localName.equals("reference", true) && (it.attributes["type"].equals("text", true) || it.attributes["type"].equals("start", true))
        }
        val refName = guide?.prefix?.let { "$it:reference" } ?: (opf.pkg.prefix?.let { "$it:reference" } ?: "reference")
        val newRef = "<$refName type=\"text\" title=\"Start\" href=\"${Xml.escapeAttr(href)}\"/>"
        when {
            existing != null -> edits.replace(existing.start, existing.end, newRef)
            guide != null && !guide.selfClosing -> edits.insert(guide.endTagStart, "  $newRef\n" + indentBefore(text, guide))
            guide != null -> edits.replace(guide.start, guide.end, "<${guide.name}>\n${indentBefore(text, guide)}  $newRef\n${indentBefore(text, guide)}</${guide.name}>")
            else -> {
                val spine = opf.spineElement ?: throw OpError("The book has no reading order.")
                val ind = indentBefore(text, spine)
                val guideName = opf.pkg.prefix?.let { "$it:guide" } ?: "guide"
                edits.insert(spine.end, "\n$ind<$guideName>\n$ind  $newRef\n$ind</$guideName>")
            }
        }
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(edits.apply())
        // The EPUB 3 landmark, if the book has landmarks.
        opf.navItem()?.let { nav ->
            val navPath = structure.pathOf(nav)
            val navText = files.text(navPath) ?: return@let
            val doc = runCatching { Xml.parse(navText) }.getOrNull() ?: return@let
            val landmarks = doc.root.descendants().firstOrNull { e ->
                e.localName.equals("nav", true) && e.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("landmarks") }
            } ?: return@let
            val navHref = Contents.encodeHref(EpubFile.relativeHref(navPath, path))
            val link = landmarks.descendants().firstOrNull { a ->
                a.localName.equals("a", true) && a.attributes.entries.any { (k, v) -> k.substringAfter(':') == "type" && v.split(' ').contains("bodymatter") }
            }
            val ne = TextEdits(navText)
            if (link != null) {
                val attrs = LinkedHashMap(link.attributes).apply { this["href"] = navHref }
                ne.replace(link.start, link.startTagEnd, Xml.startTag(link.name, attrs, link.selfClosing))
            } else {
                val ol = landmarks.elements().firstOrNull { it.localName.equals("ol", true) } ?: return@let
                val typeAttr = landmarks.attributes.keys.first { it.substringAfter(':') == "type" }
                ne.insert(ol.startTagEnd, "\n${indentBefore(navText, ol)}  <li><a $typeAttr=\"bodymatter\" href=\"${Xml.escapeAttr(navHref)}\">Start</a></li>")
            }
            changes[navPath] = bytes(ne.apply())
        }
        return Result("The book now opens at “${chapter.title}”", changes, path)
    }

    // ---- delete ------------------------------------------------------------------------------

    fun delete(files: BookFiles, structure: BookStructure, path: String): Result {
        val chapter = structure.chapterAt(path) ?: throw OpError("That chapter is no longer in the book.")
        if (structure.chapters.size <= 1) throw OpError("A book must have at least one chapter.")
        val opf = structure.opf
        val item = opf.item(chapter.id) ?: throw OpError("That chapter isn't in the book's list of files.")
        val text = structure.opfText
        val edits = TextEdits(text)
        edits.removeWithLeadingSpace(item.element)
        opf.spineRefs().filter { it.idref == chapter.id }.forEach { edits.removeWithLeadingSpace(it.element) }
        opf.guideElement?.elements()?.filter { r ->
            r.localName.equals("reference", true) && r.attributes["href"]?.let { EpubFile.resolvePath(structure.opfDir, it) } == path
        }?.forEach { edits.removeWithLeadingSpace(it) }
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[structure.opfPath] = bytes(edits.apply())
        changes[path] = null
        // Links to it from elsewhere become plain text.
        for (other in structure.chapters) {
            if (other.path == path) continue
            val t = files.text(other.path) ?: continue
            val fixed = unwrapLinksTo(t, other.path, path)
            if (fixed != t) changes[other.path] = bytes(fixed)
        }
        // Its contents entries go; entries under them move up.
        val flat = Contents.flatten(structure.toc)
        val kept = ArrayList<Pair<TocNode, Int>>()
        var removedLevel = -1
        for ((node, level) in flat) {
            if (removedLevel >= 0 && level <= removedLevel) removedLevel = -1
            if (node.path == path) {
                if (removedLevel < 0) removedLevel = level
                continue
            }
            kept += node to if (removedLevel >= 0) level - 1 else level
        }
        changes.putAll(Contents.write(files, structure, Contents.nest(kept), changes))
        // The navigation document may itself point at it (landmarks).
        opf.navItem()?.let { nav ->
            val navPath = structure.pathOf(nav)
            val navText = changes[navPath]?.let { EpubFile.decodeText(it) } ?: files.text(navPath) ?: return@let
            val fixed = removeLandmarksTo(navText, navPath, path)
            if (fixed != navText) changes[navPath] = bytes(fixed)
        }
        val next = structure.chapters.getOrNull(chapter.index + 1) ?: structure.chapters.getOrNull(chapter.index - 1)
        return Result("Deleted “${chapter.title}”", changes, next?.path)
    }

    private fun unwrapLinksTo(text: String, docPath: String, target: String): String {
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return text
        val dir = docPath.substringBeforeLast('/', "")
        val edits = TextEdits(text)
        var any = false
        for (a in TextMap.body(doc).descendants()) {
            if (!a.localName.equals("a", true)) continue
            val href = a.attributes["href"] ?: continue
            if (href.startsWith("#") || href.contains("://")) continue
            if (EpubFile.resolvePath(dir, href) != target) continue
            if (a.selfClosing) edits.replace(a.start, a.end, "")
            else {
                edits.replace(a.start, a.startTagEnd, "")
                edits.replace(a.endTagStart, a.end, "")
            }
            any = true
        }
        return if (any) edits.apply() else text
    }

    private fun removeLandmarksTo(text: String, navPath: String, target: String): String {
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
                if (EpubFile.resolvePath(dir, href) == target) {
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
        val chapter = structure.chapterAt(path) ?: throw OpError("That chapter is no longer in the book.")
        val previous = structure.chapters.getOrNull(chapter.index - 1) ?: throw OpError("It's the first chapter, so there's no previous chapter to join it with.")
        val a = files.text(previous.path) ?: throw OpError("The previous chapter can't be read.")
        val b = files.text(path) ?: throw OpError("The chapter can't be read.")
        val docA = Xml.parse(a)
        val docB = Xml.parse(b)
        val bodyA = TextMap.body(docA)
        val bodyB = TextMap.body(docB)
        if (bodyA === docA.root || bodyB === docB.root) throw OpError("One of these chapters has no text section (body), so they can't be joined.")

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
        return Result("Joined “${chapter.title}” with “${previous.title}”", changes, previous.path)
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
        val chapter = structure.chapterAt(path) ?: throw OpError("That chapter is no longer in the book.")
        val text = files.text(path) ?: throw OpError("The chapter can't be read.")
        val doc = Xml.parse(text)
        val body = TextMap.body(doc)
        val at = body.descendants().firstOrNull { it.start == offset } ?: throw OpError("That spot is no longer in the chapter.")
        val ancestors = generateSequence(at.parent) { it.parent }.takeWhile { it !== body }.toList().reversed()
        if (at.parent == null || generateSequence(at.parent) { it.parent }.none { it === body }) throw OpError("A chapter can only be split within its text.")
        val firstText = text.substring(body.startTagEnd, at.start)
        if (EpubText.plainText(firstText).isBlank() && !firstText.contains("<img", true)) throw OpError("That's already the start of the chapter.")

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
        val item = opf.item(chapter.id) ?: throw OpError("That chapter isn't in the book's list of files.")
        val ref = spineRef(opf, chapter.id) ?: throw OpError("That chapter isn't in the reading order.")
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
            ?: "${chapter.title} (continued)"
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
        return Result("Split “${chapter.title}”", changes, newPath)
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
        val name = title.trim().ifEmpty { throw OpError("Enter a name for the chapter.") }
        val opf = structure.opf
        val manifest = opf.manifestElement ?: throw OpError("The book has no list of files.")
        val spineEl = opf.spineElement ?: throw OpError("The book has no reading order.")
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
        return Result("Added “$name”", changes, newPath)
    }

    // ---- pictures ----------------------------------------------------------------------------

    /** Where a new picture goes: the folder most of the book's pictures are in, with a free name. */
    fun newPicturePath(files: BookFiles, structure: BookStructure, extension: String): String {
        val folders = structure.opf.items.filter { it.isImage }.map { structure.pathOf(it).substringBeforeLast('/', "") }
        val dir = folders.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: (if (structure.opfDir.isEmpty()) "images" else "${structure.opfDir}/images")
        var n = 1
        fun candidate() = (if (dir.isEmpty()) "" else "$dir/") + "picture-$n.$extension"
        while (files.has(candidate())) n++
        return candidate()
    }

    /** Adds a file to the book and lists it in the package file. */
    fun addFile(files: BookFiles, structure: BookStructure, path: String, data: ByteArray, mediaType: String): Result {
        val opf = structure.opf
        val manifest = opf.manifestElement ?: throw OpError("The book has no list of files.")
        val id = uniqueId(opf, "kl-" + path.substringAfterLast('/').substringBeforeLast('.'))
        val href = Contents.encodeHref(EpubFile.relativeHref(structure.opfPath, path))
        val itemName = manifest.prefix?.let { "$it:item" } ?: "item"
        val edits = TextEdits(structure.opfText)
        val anchor = manifest.elements().lastOrNull()?.end ?: manifest.startTagEnd
        edits.insert(anchor, "\n${opf.indentOf(manifest) ?: "    "}<$itemName id=\"$id\" href=\"${Xml.escapeAttr(href)}\" media-type=\"$mediaType\"/>")
        return Result("Added a picture", mapOf(structure.opfPath to bytes(edits.apply()), path to data))
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
        if (flat.isEmpty()) throw OpError("None of the chapters has a heading, so the contents can't be rebuilt.")
        val minLevel = flat.minOf { it.second }
        changes.putAll(Contents.write(files, structure, Contents.nest(flat.map { it.first to it.second - minLevel }), changes))
        return Result("Rebuilt the contents from the headings", changes)
    }
}
