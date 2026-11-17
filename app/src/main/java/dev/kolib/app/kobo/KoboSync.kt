package dev.kolib.app.kobo

import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.LibraryBook
import java.text.Normalizer

/**
 * Works out what to change so the library and the Kobo agree:
 *
 *  - **Collections**, both ways. Each book remembers the collections both sides last agreed on, so a
 *    difference shows which side changed: added in the app → added on the Kobo, taken out on the Kobo →
 *    taken out in the app, and so on. A book never compared before keeps every collection either side has.
 *    Books only go into collections the Kobo has: KoLib can't make collections there, so one it doesn't
 *    have stays in the library ([Plan.missingOnKobo]) and the book goes in once it's made on the Kobo.
 *  - **Details** (title, author, series, description…), one way: for books the app sent and that haven't
 *    been edited since, the Kobo's record is made to match the book. The Kobo reads a book's details only
 *    when it first sees the file, so a book sent again would otherwise keep its old details there.
 *
 * Only books the library knows are on the Kobo are touched, and only sideloaded ones. A book just sent
 * that the Kobo hasn't taken in yet has no record there, but its collections can go in already, under
 * the ContentID it will have: the Kobo finds them when it takes the book in ([waitingChanges]).
 */
object KoboSync {

    data class BookChange(
        val bookId: String,
        val title: String,
        val contentId: String,
        val addOnKobo: List<String>,
        val removeOnKobo: List<String>,
        /** Columns of the Kobo's content table to set (null clears). */
        val details: Map<String, Any?>,
        /** The library's collections afterwards, when they change. */
        val libraryCollections: List<String>?,
        /** The collections both sides agree on afterwards. */
        val agreed: List<String>,
        /** Whether [agreed] differs from what the library had recorded. */
        val agreementChanged: Boolean,
    ) {
        val touchesKobo: Boolean get() = addOnKobo.isNotEmpty() || removeOnKobo.isNotEmpty() || details.isNotEmpty()
        val touchesLibrary: Boolean get() = libraryCollections != null || agreementChanged
    }

    data class Plan(
        val changes: List<BookChange>,
        /** Books the library thinks are on the Kobo that its database doesn't list (new, or deleted there). */
        val notListed: List<LibraryBook>,
        /** Collections books should go into that the Kobo doesn't have (yet), as the library spells them. */
        val missingOnKobo: List<String> = emptyList(),
    ) {
        val koboChanges: List<BookChange> get() = changes.filter { it.touchesKobo }
    }

    /** Names the Kobo keeps for itself; books can't be put in collections with these names. */
    private val reserved = setOf("wishlist", "shortlist")

    fun plan(books: List<LibraryBook>, snapshot: KoboSnapshot): Plan {
        val changes = mutableListOf<BookChange>()
        val notListed = mutableListOf<LibraryBook>()
        val exists = existsOn(snapshot)
        val missing = LinkedHashMap<String, String>()
        for (book in books) {
            val path = book.koboPath ?: continue
            val contentId = KoboPaths.contentId(path)
            val onKobo = snapshot.booksById[contentId]
            if (onKobo == null || !onKobo.isSideloaded) {
                if (onKobo == null) notListed += book
                continue
            }
            val merged = mergeCollections(book.collections, snapshot.collectionsByBook[contentId].orEmpty(), book.koboCollections, exists)
            merged.missingOnKobo.forEach { missing.putIfAbsent(key(it), it) }
            val details = if (book.sentAt != null && !book.changedSinceOnKobo) detailsFor(book.meta, onKobo) else emptyMap()
            val agreementChanged = book.koboCollections == null || keys(book.koboCollections) != keys(merged.agreed)
            val change = BookChange(
                bookId = book.id,
                title = book.title,
                contentId = contentId,
                addOnKobo = merged.addOnKobo,
                removeOnKobo = merged.removeOnKobo,
                details = details,
                libraryCollections = merged.library,
                agreed = merged.agreed,
                agreementChanged = agreementChanged,
            )
            if (change.touchesKobo || change.touchesLibrary) changes += change
        }
        return Plan(changes, notListed, missing.values.toList())
    }

    /** Whether the Kobo has a collection of that name (any case), not counting ones deleted there. */
    fun existsOn(snapshot: KoboSnapshot): (String) -> Boolean {
        val names = snapshot.collections.flatMap { listOfNotNull(it.name, it.internalName) }.map(::key).toSet()
        return { key(it) in names }
    }

    /**
     * For books sent to the Kobo that it hasn't taken in yet ([waiting]: their file is there, but its
     * database doesn't list them): their collections as the library has them. Nothing is recorded as
     * agreed until the Kobo lists the books, so if it didn't keep these, they're simply put in again.
     */
    fun waitingChanges(waiting: List<LibraryBook>, snapshot: KoboSnapshot): List<BookChange> {
        val exists = existsOn(snapshot)
        return waiting.mapNotNull { book -> waitingChange(book, snapshot, exists) }
    }

    private fun waitingChange(book: LibraryBook, snapshot: KoboSnapshot, exists: (String) -> Boolean): BookChange? {
        val contentId = KoboPaths.contentId(book.koboPath ?: return null)
        val wanted = book.collections.map { it.trim() }.filter { it.isNotEmpty() && key(it) !in reserved && exists(it) }.distinctBy(::key)
        val there = snapshot.collectionsByBook[contentId].orEmpty()
        val add = wanted.filter { w -> there.none { key(it) == key(w) } }
        val remove = there.filter { t -> wanted.none { key(it) == key(t) } }
        if (add.isEmpty() && remove.isEmpty()) return null
        return BookChange(
            bookId = book.id,
            title = book.title,
            contentId = contentId,
            addOnKobo = add,
            removeOnKobo = remove,
            details = emptyMap(),
            libraryCollections = null,
            agreed = book.koboCollections.orEmpty(),
            agreementChanged = false,
        )
    }

    /** Collections [books] are in (in the library) that the Kobo doesn't have, as the library spells them. */
    fun missingCollections(books: List<LibraryBook>, snapshot: KoboSnapshot): List<String> {
        val exists = existsOn(snapshot)
        return books.flatMap { it.collections }.map { it.trim() }
            .filter { it.isNotEmpty() && key(it) !in reserved && !exists(it) }
            .distinctBy(::key)
    }

    data class Merge(
        val addOnKobo: List<String>,
        val removeOnKobo: List<String>,
        val library: List<String>?,
        val agreed: List<String>,
        /** Collections the book should be put in that the Kobo doesn't have. */
        val missingOnKobo: List<String> = emptyList(),
    )

    /**
     * A three-way merge of one book's collections: [library] and [kobo] now, and [base], what they last
     * agreed on (null if never compared, when nothing is taken out on either side). Names match whatever
     * their case. A collection the Kobo doesn't have ([exists] says) stays in the library but isn't put on
     * the Kobo or counted as agreed, so it goes onto the Kobo the first time it's there.
     */
    fun mergeCollections(library: List<String>, kobo: List<String>, base: List<String>?, exists: (String) -> Boolean = { true }): Merge {
        val lib = library.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::key)
        val onKobo = kobo.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::key)
        val baseKeys = base?.map(::key)?.toSet() ?: emptySet()
        val libKeys = lib.map(::key).toSet()
        val koboKeys = onKobo.map(::key).toSet()
        val removed = if (base == null) emptySet() else (baseKeys - libKeys) + (baseKeys - koboKeys)
        val resultKeys = (libKeys + koboKeys) - removed
        // Names as the library spells them, then the Kobo, in the library's order with new ones after.
        val spelled = LinkedHashMap<String, String>()
        (lib + onKobo + base.orEmpty()).forEach { spelled.putIfAbsent(key(it), it.trim()) }
        val result = spelled.filterKeys { it in resultKeys }.values.toList()
        val wanted = result.filter { key(it) !in koboKeys && key(it) !in reserved }
        val addOnKobo = wanted.filter(exists)
        val removeOnKobo = onKobo.filter { key(it) !in resultKeys }
        val newLibrary = result.takeIf { r -> r.map(::key) != lib.map(::key) }
        // A name the Kobo keeps for itself, or a collection it doesn't have, stays in the library but
        // isn't on the Kobo afterwards, so it isn't agreed: next time it's still to be put there.
        val agreed = result.filter { key(it) in koboKeys || (key(it) !in reserved && exists(it)) }
        return Merge(addOnKobo, removeOnKobo, newLibrary, agreed, missingOnKobo = wanted.filterNot(exists))
    }

    /** The Kobo's record of a book changed to match [meta]: only what differs, column by column. */
    fun detailsFor(meta: BookMetadata, kobo: KoboBook): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val title = meta.title.trim()
        if (title.isNotEmpty() && title != kobo.title) out["Title"] = title
        val authors = meta.authors.map { it.trim() }.filter { it.isNotEmpty() }
        if (authors.isNotEmpty() && authors.map(::matchKey).toSet() != splitAuthors(kobo.author).map(::matchKey).toSet()) {
            out["Attribution"] = authors.joinToString(", ")
        }
        if (differs(meta.description, kobo.description)) out["Description"] = meta.description?.trim()?.takeIf { it.isNotEmpty() }
        if (differs(meta.publisher, kobo.publisher)) out["Publisher"] = meta.publisher?.trim()?.takeIf { it.isNotEmpty() }
        meta.language?.trim()?.takeIf { it.isNotEmpty() }?.let { lang ->
            if (!lang.equals(kobo.language, ignoreCase = true)) out["Language"] = lang
        }
        if (isbnDigits(meta.isbn) != isbnDigits(kobo.isbn)) out["ISBN"] = meta.isbn?.trim()?.takeIf { it.isNotEmpty() }
        val series = meta.series?.trim()?.takeIf { it.isNotEmpty() }
        val number = meta.seriesIndex.takeIf { series != null }
        val koboNumber = kobo.seriesNumber?.toDoubleOrNull()
        val seriesDiffers = series != kobo.series || number != koboNumber || (series != null && kobo.seriesId != series)
        if (seriesDiffers) {
            out["Series"] = series
            out["SeriesNumber"] = number?.let(::formatIndex)
            out["SeriesNumberFloat"] = number
            // Sideloaded books are grouped into series by name (as the Kobo itself does for them).
            out["SeriesID"] = series
        }
        isoDate(meta.published)?.let { date ->
            if (kobo.published?.take(10) != date.take(10)) out["DateCreated"] = date
        }
        return out
    }

    private fun key(s: String) = s.trim().lowercase()

    private fun keys(list: List<String>?): Set<String> = list.orEmpty().map(::key).filter { it.isNotEmpty() }.toSet()

    private fun differs(a: String?, b: String?) = a?.trim().orEmpty() != b?.trim().orEmpty()

    private fun isbnDigits(s: String?) = s?.filter { it.isDigit() || it == 'X' || it == 'x' }?.uppercase().orEmpty()

    private fun splitAuthors(s: String?): List<String> =
        s?.split(Regex("\\s*(?:,|&|;|\\band\\b)\\s*"))?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun matchKey(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }

    /** 2 → "2", 1.5 → "1.5", as the Kobo shows series numbers. */
    fun formatIndex(d: Double): String = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    /** "2003", "2003-02" or "2003-02-27" → "2003-02-27T00:00:00Z"; anything else → null. */
    fun isoDate(published: String?): String? {
        val m = Regex("""^(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?""").find(published?.trim().orEmpty()) ?: return null
        val (y, mo, d) = m.destructured
        return "$y-${mo.ifEmpty { "01" }}-${d.ifEmpty { "01" }}T00:00:00Z"
    }
}
