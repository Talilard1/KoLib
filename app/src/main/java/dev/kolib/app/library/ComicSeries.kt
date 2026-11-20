package dev.kolib.app.library

import dev.kolib.app.reader.ReadHere

/**
 * A comic series as Mihon shows one: an entry in the library with its comic files (issues, chapters or volumes) in
 * order under it. [key] is the series' name made comparable (see [ComicSeries.key]); a comic in no series is an entry
 * of its own, keyed by its id.
 */
class ComicEntry(val key: String, val title: String, val issues: List<LibraryBook>) {
    /** A series (in name at least), rather than a comic on its own. */
    val isSeries: Boolean get() = issues.size > 1 || issues.any { !it.meta.series.isNullOrBlank() }

    /** The comic whose cover stands for the series: its first. */
    val cover: LibraryBook get() = issues.first()

    val authors: List<String> get() = issues.flatMap { it.meta.authors }.distinctBy { it.lowercase() }

    /** What the series is about: the first issue's description that there is. */
    val description: String? get() = issues.firstNotNullOfOrNull { it.meta.description?.takeIf { d -> d.isNotBlank() } }

    /** The issues not yet read. */
    fun unread(here: (LibraryBook) -> ReadHere?): List<LibraryBook> = issues.filter { !ComicSeries.isRead(here(it)) }

    /** The issue to carry on with: the first not yet read, as Mihon's Resume has it; null once they've all been read. */
    fun next(here: (LibraryBook) -> ReadHere?): LibraryBook? = issues.firstOrNull { !ComicSeries.isRead(here(it)) }

    /** Whether any issue has been read (past its first page): to carry on, rather than start. */
    fun started(here: (LibraryBook) -> ReadHere?): Boolean = issues.any { here(it)?.let { h -> h.finished || h.progress > 0f } == true }

    /** When an issue was last read here; null if none has been. */
    fun lastRead(here: (LibraryBook) -> ReadHere?): Long? = issues.mapNotNull { here(it)?.at }.maxOrNull()

    /** The issue after [id] in the series, if there is one. */
    fun after(id: String): LibraryBook? = issues.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { issues.getOrNull(it + 1) }
}

object ComicSeries {
    /** What a comic's file name says: the series' name in front of its number, and its volume and number where it has them. */
    data class Name(val stem: String, val volume: Double?, val number: Double?)

    private val ending = Regex("""\.(cbz|cbr|cb7|cbt|zip|rar|7z|epub|kepub|pdf)$""", RegexOption.IGNORE_CASE)
    // (Every bracket and brace that's meant as itself escaped: Android's regex engine won't take a lone "}" as one.)
    private val brackets = Regex("""\([^)]*\)|\[[^\]]*\]|\{[^}]*\}""")
    private val volumeRe = Regex("""(?i)(?<![\p{L}\d])(?:volume|vol|tome|tomo|band|bd|v|t)\.?\s*(\d+(?:\.\d+)?)(?![\p{L}\d])""")
    private val numberRe = Regex(
        """(?i)(?<![\p{L}\d])(?:chapter|chap|ch|c|issue|episode|ep|part|pt|number|no|nr|#)\.?\s*(\d+(?:\.\d+)?)(?![\p{L}\d])""",
    )
    private val bareRe = Regex("""(?<![\p{L}\d.])(\d+(?:\.\d+)?)(?![\p{L}\d])""")

    /** Words that only number things: a name of nothing but these isn't a series' name. */
    private val numberWords = setOf(
        "chapter", "chap", "ch", "c", "issue", "episode", "ep", "part", "pt", "number", "no", "nr", "volume", "vol", "v",
        "tome", "tomo", "band", "bd", "t", "book", "comic", "comics", "manga", "page", "scan", "read",
    )

    /** What the comic's file name [fileName] (or its title) says; nothing, if it can't be read (never an error). */
    fun parse(fileName: String): Name = try {
        parseName(fileName)
    } catch (e: Exception) {
        Name("", null, null)
    }

    private fun parseName(fileName: String): Name {
        val clean = fileName.trim().replace(ending, "").replace(brackets, " ").replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        val volume = volumeRe.find(clean)
        val keyword = numberRe.find(clean)
        // Without a word for it, the number is the last one on its own (not the volume's).
        val number = keyword ?: bareRe.findAll(clean).lastOrNull { m -> volume == null || m.range.first !in volume.range }
        val cut = listOfNotNull(volume?.range?.first, number?.range?.first).minOrNull()
        val stem = if (cut == null) "" else clean.substring(0, cut).trim().trimEnd('-', '–', '—', ':', ';', ',', '.', '#', '~').trim()
        val stemKey = key(stem)
        return Name(
            stem = if (stemKey.isEmpty() || stemKey.split(' ').all { it in numberWords }) "" else stem,
            volume = volume?.groupValues?.get(1)?.toDoubleOrNull(),
            number = number?.groupValues?.get(1)?.toDoubleOrNull(),
        )
    }

    /** Folders whose names aren't a series' name: where comics are kept, rather than which comics they are. */
    private val folderWords = setOf(
        "download", "downloads", "documents", "comic", "comics", "manga", "mangas", "manhwa", "webtoon", "webtoons", "book",
        "books", "ebook", "ebooks", "library", "cbz", "cbr", "files", "storage", "emulated", "sdcard", "kolib", "mihon",
        "tachiyomi", "local", "new", "read", "unread",
    )

    /**
     * The series the folder at [path] ("Comics/Saga/Volume 1") names: the nearest folder in it with a name of its own,
     * not one that's only numbered ("Volume 1") or where comics are kept ("Comics", "Download"); null if there's none.
     */
    fun seriesFolder(path: String): String? = path.split('/').map { it.trim() }.filter { it.isNotEmpty() }.reversed().firstOrNull { name ->
        val k = key(name)
        k.isNotEmpty() && k !in folderWords && !k.split(' ').all { it in numberWords || it.all(Char::isDigit) }
    }

    /** A series' name, compared as the same whatever its case, spacing and punctuation. */
    fun key(name: String): String = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** The name a comic was added as, or failing that its title. */
    fun nameOf(book: LibraryBook): String = book.originalName?.takeIf { it.isNotBlank() } ?: book.title

    /** An issue's been read: marked finished, or read to its last page. */
    fun isRead(here: ReadHere?): Boolean = here != null && (here.finished || here.progress >= 1f)

    /** [issues] in the series' order: by volume, then number (its own, or the one in its name), then name. */
    fun ordered(issues: List<LibraryBook>): List<LibraryBook> {
        val names = issues.associate { it.id to parse(nameOf(it)) }
        return issues.sortedWith(
            compareBy<LibraryBook> { names.getValue(it.id).volume ?: 0.0 }
                .thenBy { it.meta.seriesIndex ?: names.getValue(it.id).number ?: Double.MAX_VALUE }
                .then { a, b -> naturalCompare(a.title, b.title) }
                .thenBy { it.addedAt },
        )
    }

    /** The library's comics as entries: each series with its issues in order, and each comic in none on its own; A to Z. */
    fun entries(comics: List<LibraryBook>): List<ComicEntry> {
        val groups = LinkedHashMap<String, MutableList<LibraryBook>>()
        for (b in comics) {
            val series = b.meta.series?.trim().orEmpty()
            val k = if (series.isNotEmpty() && key(series).isNotEmpty()) "s:" + key(series) else "b:" + b.id
            groups.getOrPut(k) { ArrayList() } += b
        }
        return groups.map { (k, list) ->
            val issues = ordered(list)
            // The series' name as most of its issues spell it.
            val title = if (k.startsWith("s:")) {
                issues.mapNotNull { it.meta.series?.trim()?.ifEmpty { null } }.groupingBy { it }.eachCount().maxBy { it.value }.key
            } else issues.first().title
            ComicEntry(k, title, issues)
        }.sortedWith { a, b -> naturalCompare(a.title, b.title) }
    }

    /** The entry [book] is in, of [comics]. */
    fun entryOf(book: LibraryBook, comics: List<LibraryBook>): ComicEntry? = entries(comics).firstOrNull { e -> e.issues.any { it.id == book.id } }

    /** A series and number to give a comic. */
    data class Given(val series: String, val index: Double?)

    /**
     * Series for comics with none of their own, as they're added: [added], with the folder each was in (inside the
     * folder chosen, if they were added from one) in [folders]; [others] are the library's other comics. Each goes in the
     * series named by its folder (see [seriesFolder]); or failing that the one its file name starts with, where another
     * comic's name starts the same way (one of [others] with no series of its own too gets it then) or a series of that
     * name is there already. Comics with names that are only numbers ("Chapter 12") straight inside [rootFolder], the
     * folder chosen ("Download/Blue Period"), go in the series it names.
     */
    fun assign(added: List<LibraryBook>, others: List<LibraryBook>, folders: Map<String, String> = emptyMap(), rootFolder: String? = null): Map<String, Given> {
        val out = LinkedHashMap<String, Given>()
        val loose = added.filter { it.meta.series.isNullOrBlank() }
        val names = (loose + others).associate { it.id to parse(nameOf(it)) }
        // Series there are already, by key, as they're written.
        val existing = HashMap<String, String>()
        for (b in others + added) b.meta.series?.trim()?.takeIf { it.isNotEmpty() && key(it).isNotEmpty() }?.let { existing.putIfAbsent(key(it), it) }
        fun indexOf(n: Name) = n.number ?: n.volume
        val byStem = HashMap<String, MutableList<LibraryBook>>()
        for (b in loose) {
            val n = names.getValue(b.id)
            val folder = folders[b.id]?.let { seriesFolder(it) }
            val root = rootFolder?.let { seriesFolder(it) }
            when {
                folder != null -> out[b.id] = Given(existing[key(folder)] ?: folder, indexOf(n))
                n.stem.isNotEmpty() -> byStem.getOrPut(key(n.stem)) { ArrayList() } += b
                root != null && (n.number != null || n.volume != null) -> out[b.id] = Given(existing[key(root)] ?: root, indexOf(n))
            }
        }
        val othersLoose = others.filter { it.meta.series.isNullOrBlank() }
        for ((k, list) in byStem) {
            val named = existing[k]
            val alike = othersLoose.filter { o -> names[o.id]?.stem?.let { key(it) } == k }
            if (named == null && list.size + alike.size < 2) continue
            val series = named ?: names.getValue(list.first().id).stem
            for (b in list + alike) out[b.id] = Given(series, indexOf(names.getValue(b.id)))
        }
        return out
    }

    /** Series for the library's comics with none, by the names they share: done once, for comics added before there were series. */
    fun assignAll(comics: List<LibraryBook>): Map<String, Given> = assign(comics.filter { it.meta.series.isNullOrBlank() }, comics.filter { !it.meta.series.isNullOrBlank() })

    /** Compares names with the numbers in them as numbers: "Issue 2" before "Issue 10". */
    fun naturalCompare(a: String, b: String): Int {
        val ca = chunks(a)
        val cb = chunks(b)
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val nx = x.toBigDecimalOrNull()
            val ny = y.toBigDecimalOrNull()
            val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
            if (c != 0) return c
        }
        return ca.size.compareTo(cb.size)
    }

    private fun chunks(s: String): List<String> = Regex("""\d+|\D+""").findAll(s.trim()).map { it.value }.toList()
}
