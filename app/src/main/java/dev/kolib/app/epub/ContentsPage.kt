package dev.kolib.app.epub

/**
 * A book's own contents page: a page of links to its chapters, as opposed to the contents a reading app shows from
 * the book's list of them. Telling one apart, making one list another book's chapters in its own style, and taking
 * the links out of a page that go to pages a book doesn't have.
 */
object ContentsPage {

    /** A chapter for a contents page to list: its [title], where it is, and how deep in the contents (0 at the top). */
    data class Entry(val title: String, val path: String, val fragment: String?, val level: Int)

    /** The entries of a book's contents, [toc], in order, leaving out [except] (the contents page itself). */
    fun entriesOf(toc: List<TocNode>, except: String? = null): List<Entry> =
        Contents.flatten(toc).filter { (n, _) -> n.linked && n.path != except }.map { (n, level) -> Entry(n.title, n.path, n.fragment, level) }

    /** Each entry of [toc] by where it goes ("path" or "path#fragment"), with its depth: what [relist] goes by. */
    fun levelsOf(toc: List<TocNode>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((n, level) in Contents.flatten(toc)) {
            if (!n.linked) continue
            out.putIfAbsent(key(n.path, n.fragment), level)
            out.putIfAbsent(n.path, level)
        }
        return out
    }

    fun key(path: String, fragment: String?) = if (fragment == null) path else "$path#$fragment"

    private val external = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** A link in a page: the element, and the file (and place in it) it goes to. */
    private class Link(val a: XElement, val path: String, val fragment: String?)

    /** The links in [body] (of a page at [pagePath]) to other files of the book, in order. */
    private fun links(body: XElement, pagePath: String): List<Link> {
        val dir = pagePath.substringBeforeLast('/', "")
        return body.descendants().filter { it.localName.equals("a", true) }.mapNotNull { a ->
            val href = a.attributes["href"]?.trim() ?: return@mapNotNull null
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("//") || external.containsMatchIn(href)) return@mapNotNull null
            val path = EpubFile.resolvePath(dir, href)
            if (path.isEmpty() || path == pagePath) null else Link(a, path, href.substringAfter('#', "").ifEmpty { null })
        }.toList()
    }

    /** Links one after another to the same page are one entry ("Chapter 1: The Hook" as two links): the first of each. */
    private fun entryLinks(links: List<Link>): List<Link> =
        links.filterIndexed { i, l -> i == 0 || links[i - 1].path != l.path || (l.fragment != null && l.fragment != links[i - 1].fragment) }

    private val named = Regex("""(?:^|[^a-z])(?:contents|toc)(?:[^a-z]|$)""")

    /**
     * [xhtml] (a page at [pagePath], called [title] in its book's contents) is a contents page: links to three or more
     * of the book's other pages, and either named or marked as contents, or mostly those links.
     */
    fun isContents(xhtml: String, pagePath: String, title: String): Boolean {
        val doc = runCatching { Xml.parse(xhtml) }.getOrNull() ?: return false
        val body = TextMap.body(doc)
        val links = links(body, pagePath)
        if (links.map { key(it.path, it.fragment) }.distinct().size < 3) return false
        val marks = buildString {
            append(pagePath.substringAfterLast('/').lowercase()).append(' ').append(title.lowercase())
            for (e in sequenceOf(body) + body.descendants().take(30)) {
                for (k in listOf("epub:type", "class", "id", "role")) e.attributes[k]?.let { append(' ').append(it.lowercase()) }
            }
        }
        if (named.containsMatchIn(marks)) return true
        val all = body.text().count { !it.isWhitespace() }
        val linked = links.sumOf { l -> l.a.text().count { !it.isWhitespace() } }
        return all > 0 && linked * 2 >= all
    }

    /** An element's tag to write again: its name and attributes (less an id, which mustn't be repeated, and a link's address). */
    private class Tag(val name: String, val attrs: Map<String, String>) {
        fun start(extra: Map<String, String> = emptyMap()) = Xml.startTag(name, attrs + extra, false)
        fun end() = "</$name>"

        companion object {
            fun of(e: XElement) = Tag(e.name, LinkedHashMap(e.attributes).apply { remove("id"); remove("xml:id"); remove("href") })
        }
    }

    /** An entry written as [chain] says (its tags, down to the link): the title, linking to it from [pagePath]. */
    private fun entry(chain: List<Tag>, e: Entry, pagePath: String): String = buildString {
        for (t in chain.dropLast(1)) append(t.start())
        append(chain.last().start(mapOf("href" to Contents.href(pagePath, TocNode(e.title, e.path, e.fragment)))))
        append(Xml.escapeText(e.title))
        for (t in chain.asReversed()) append(t.end())
    }

    /** Of [templates] (by depth), the one for an entry at [level]: that depth, else the nearest shallower, else the nearest deeper. */
    private fun <T> nearest(templates: Map<Int, T>, level: Int): T? =
        templates[level] ?: templates.keys.filter { it < level }.maxOrNull()?.let { templates[it] }
            ?: templates.keys.filter { it > level }.minOrNull()?.let { templates[it] }

    /**
     * [xhtml], a contents page at [pagePath], listing [entries] instead of its own, in its own style: each entry made
     * like the ones it had at that depth, by [levels] (each of its entries' depth in its own book's contents, keyed
     * as [key] makes them), where those are known. Everything else on the page stays as it was. Null if it hasn't
     * entries enough to go by.
     */
    fun relist(xhtml: String, pagePath: String, entries: List<Entry>, levels: Map<String, Int> = emptyMap()): String? {
        if (entries.isEmpty()) return null
        val doc = runCatching { Xml.parse(xhtml) }.getOrNull() ?: return null
        val body = TextMap.body(doc)
        if (body === doc.root) return null
        val links = entryLinks(links(body, pagePath))
        if (links.size < 2) return null
        val top = entries.minOf { it.level }
        val wanted = entries.map { it.copy(level = it.level - top) }
        return asList(xhtml, body, links, pagePath, wanted) ?: asBlocks(xhtml, body, links, pagePath, wanted, levels)
    }

    private fun isList(e: XElement) = e.localName.lowercase().let { it == "ol" || it == "ul" }
    private fun isItem(e: XElement) = e.localName.equals("li", true)
    private fun ancestors(e: XElement, below: XElement): Sequence<XElement> = generateSequence(e.parent) { it.parent }.takeWhile { it !== below }

    /** The page's entries as a list (items in an ol or ul, deeper ones in lists of their own): made again from [entries]. */
    private fun asList(text: String, body: XElement, links: List<Link>, pagePath: String, entries: List<Entry>): String? {
        // The outermost list that holds nearly all the entries.
        val outer = links.map { l -> ancestors(l.a, body).lastOrNull { isList(it) } }
        val root = outer.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value * 5 >= links.size * 4 }?.key ?: return null
        // At each depth: how an item is written, how its link is (the tags down to it), and the list deeper items go in.
        val itemTags = HashMap<Int, Tag>()
        val chains = HashMap<Int, List<Tag>>()
        val sublists = HashMap<Int, Tag>()
        for (li in root.descendants().filter { isItem(it) }) {
            val depth = ancestors(li, root).count { isItem(it) }
            itemTags.putIfAbsent(depth, Tag.of(li))
            li.elements().firstOrNull { isList(it) }?.let { sublists.putIfAbsent(depth, Tag.of(it)) }
            if (depth in chains) continue
            // Its own link: in it, but not in a list inside it.
            val own = links.firstOrNull { l -> generateSequence(l.a.parent) { it.parent }.takeWhile { it !== li }.none { isList(it) } &&
                generateSequence(l.a.parent) { it.parent }.any { it === li } }
            if (own != null) chains[depth] = (listOf(own.a) + ancestors(own.a, li)).asReversed().map { Tag.of(it) }
        }
        if (chains.isEmpty()) return null
        val rootTag = Tag.of(root)
        val tree = Contents.nest(entries.map { TocNode(it.title, it.path, it.fragment) to it.level })
        val baseIndent = indentBefore(text, root.start)
        fun items(nodes: List<TocNode>, depth: Int, indent: String): String = buildString {
            for (n in nodes) {
                val li = nearest(itemTags, depth) ?: Tag("li", emptyMap())
                val chain = nearest(chains, depth)!!
                append(indent).append(li.start()).append(entry(chain, Entry(n.title, n.path, n.fragment, depth), pagePath))
                if (n.children.isNotEmpty()) {
                    val sub = nearest(sublists, depth) ?: rootTag
                    append('\n').append(indent).append("  ").append(sub.start()).append('\n')
                    append(items(n.children, depth + 1, "$indent    "))
                    append(indent).append("  ").append(sub.end()).append('\n').append(indent)
                }
                append(li.end()).append('\n')
            }
        }
        return text.substring(0, root.startTagEnd) + "\n" + items(tree, 0, "$baseIndent  ") + baseIndent + text.substring(root.endTagStart)
    }

    /**
     * The page's entries one after another (paragraphs, say, styled by depth): made again from [entries], each like the
     * ones the page had at its depth. They go where the page's own went, from the first to the last.
     */
    private fun asBlocks(text: String, body: XElement, links: List<Link>, pagePath: String, entries: List<Entry>, levels: Map<String, Int>): String? {
        // How many entries each element holds; an entry is the most around its link that holds no other.
        val count = HashMap<XElement, Int>()
        for (l in links) (sequenceOf(l.a) + ancestors(l.a, body)).forEach { count[it] = (count[it] ?: 0) + 1 }
        val blocks = links.map { l ->
            var b = l.a
            while (true) {
                val p = b.parent ?: break
                if (p === body || (count[p] ?: 0) > 1) break
                b = p
            }
            b
        }
        // The tags from each entry down to its link, and so its kind ("p.toc-chapter > a").
        val chains = links.indices.map { i -> (listOf(links[i].a) + ancestors(links[i].a, blocks[i].parent ?: body)).asReversed().map { Tag.of(it) } }
        val kinds = LinkedHashMap<String, MutableList<Int>>()
        chains.forEachIndexed { i, c -> kinds.getOrPut(c.joinToString(">") { t -> t.name + "." + (t.attrs["class"] ?: "") }) { ArrayList() } += i }
        // Each kind at the depth most of its entries were at in their book's contents.
        val known = HashMap<Int, List<Tag>>()
        for ((_, members) in kinds) {
            val depths = members.mapNotNull { i -> levels[key(links[i].path, links[i].fragment)] ?: levels[links[i].path] }
            val depth = depths.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: continue
            known.putIfAbsent(depth, chains[members.first()])
        }
        val templates: Map<Int, List<Tag>> = known.takeIf { it.isNotEmpty() } ?: run {
            // Not known: the commonest kind is the deepest (chapters), the rarer ones above it (parts).
            val byUse = kinds.values.sortedByDescending { it.size }
            byUse.withIndex().associate { (k, members) -> (byUse.size - 1 - k) to chains[members.first()] }
        }
        // Where they go: the entries' common ancestor, from its child holding the first to the one holding the last.
        var container: XElement = blocks.first().parent ?: return null
        while (!blocks.all { b -> generateSequence(b) { it.parent }.any { it === container } }) container = container.parent ?: return null
        fun topOf(e: XElement): XElement = generateSequence(e) { it.parent }.first { it.parent === container }
        val first = topOf(blocks.first())
        val last = topOf(blocks.last())
        if (last.end < first.start) return null
        // What goes between two entries: what the page had between its own (a line break, say), else a new line.
        val between = blocks.zipWithNext().filter { (a, b) -> a.parent === b.parent && a.end <= b.start }.map { (a, b) -> text.substring(a.end, b.start) }
        val indent = indentBefore(text, first.start)
        val sep = between.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?.takeIf { s -> runCatching { Xml.parse("<x>$s</x>").root.text().isBlank() }.getOrDefault(false) || s.trim().length <= 3 }
            ?: "\n$indent"
        val made = entries.joinToString(sep) { e -> entry(nearest(templates, e.level)!!, e, pagePath) }
        return text.substring(0, first.start) + made + text.substring(last.end)
    }

    /** The spaces at the start of the line [at] is on, up to it (if there's only space before it on the line). */
    private fun indentBefore(text: String, at: Int): String {
        var s = at
        while (s > 0 && (text[s - 1] == ' ' || text[s - 1] == '\t')) s--
        return if (s == 0 || text[s - 1] == '\n') text.substring(s, at) else ""
    }

    /**
     * [xhtml] (a page at [pagePath]) without links to pages [has] says aren't in the book: each becomes a span around
     * its words, so they stay and the book has no broken links. Links out of the book, and within the page, stay.
     */
    fun unlinkMissing(xhtml: String, pagePath: String, has: (String) -> Boolean): String {
        val doc = runCatching { Xml.parse(xhtml) }.getOrNull() ?: return xhtml
        val edits = TextEdits(xhtml)
        var changed = false
        for (l in links(TextMap.body(doc), pagePath)) {
            if (has(l.path)) continue
            val a = l.a
            val attrs = LinkedHashMap(a.attributes).apply { remove("href"); remove("target"); remove("rel"); remove("epub:type") }
            val span = if (a.prefix != null) "${a.prefix}:span" else "span"
            if (a.selfClosing) {
                edits.replace(a.start, a.end, Xml.startTag(span, attrs, true))
            } else {
                edits.replace(a.start, a.startTagEnd, Xml.startTag(span, attrs, false))
                edits.replace(a.endTagStart, a.end, "</$span>")
            }
            changed = true
        }
        return if (changed) edits.apply() else xhtml
    }
}
