package dev.kolib.app.link

import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.LibraryBook
import org.json.JSONArray
import org.json.JSONObject

/** A library book's record as it goes to another device, and which of its files go with it. */
internal object LinkBooks {

    fun toJson(b: LibraryBook): JSONObject = JSONObject()
        .put("id", b.id)
        .put("meta", meta(b.meta))
        .put("collections", JSONArray(b.collections))
        .put("fileSize", b.fileSize)
        .put("hasCover", b.hasCover)
        .put("coverVersion", b.coverVersion)
        .put("addedAt", b.addedAt)
        .put("modifiedAt", b.modifiedAt)
        .put("source", b.source.name)
        .putOpt("originalName", b.originalName)
        .putOpt("koboPath", b.koboPath)
        .putOpt("sentAt", b.sentAt)
        .putOpt("words", b.words)
        .putOpt("koboCollections", b.koboCollections?.let { JSONArray(it) })
        .put("coverAsIs", b.coverAsIs)
        .putOpt("sourceFormat", b.sourceFormat)

    fun from(o: JSONObject): LibraryBook? = runCatching {
        val id = o.getString("id")
        // An id names the book's folder: only the kind the library makes.
        require(id.matches(Regex("[A-Za-z0-9-]{8,64}")))
        LibraryBook(
            id = id,
            meta = metaFrom(o.getJSONObject("meta")),
            collections = strings(o.optJSONArray("collections")),
            fileSize = o.optLong("fileSize"),
            hasCover = o.optBoolean("hasCover"),
            coverVersion = o.optLong("coverVersion", 1),
            addedAt = o.optLong("addedAt"),
            modifiedAt = o.optLong("modifiedAt"),
            source = BookSource.entries.firstOrNull { it.name == o.optString("source") } ?: BookSource.FILE,
            originalName = text(o, "originalName"),
            koboPath = text(o, "koboPath"),
            sentAt = o.optLong("sentAt").takeIf { o.has("sentAt") && !o.isNull("sentAt") },
            words = o.optInt("words").takeIf { o.has("words") && !o.isNull("words") },
            koboCollections = o.optJSONArray("koboCollections")?.let { strings(it) },
            coverAsIs = o.optBoolean("coverAsIs"),
            sourceFormat = text(o, "sourceFormat"),
        )
    }.getOrNull()

    private fun meta(m: BookMetadata): JSONObject = JSONObject()
        .put("title", m.title)
        .putOpt("titleSort", m.titleSort)
        .put("authors", JSONArray(m.authors))
        .put("authorSorts", JSONObject(m.authorSorts as Map<*, *>))
        .putOpt("series", m.series)
        .putOpt("seriesIndex", m.seriesIndex)
        .put("tags", JSONArray(m.tags))
        .putOpt("publisher", m.publisher)
        .putOpt("published", m.published)
        .putOpt("language", m.language)
        .putOpt("isbn", m.isbn)
        .putOpt("description", m.description)
        .putOpt("rating", m.rating)
        .putOpt("identifier", m.identifier)

    private fun metaFrom(o: JSONObject) = BookMetadata(
        title = o.optString("title"),
        titleSort = text(o, "titleSort"),
        authors = strings(o.optJSONArray("authors")),
        authorSorts = o.optJSONObject("authorSorts")?.let { s -> s.keys().asSequence().associateWith { s.optString(it) } }.orEmpty(),
        series = text(o, "series"),
        seriesIndex = o.optDouble("seriesIndex").takeIf { o.has("seriesIndex") && !it.isNaN() },
        tags = strings(o.optJSONArray("tags")),
        publisher = text(o, "publisher"),
        published = text(o, "published"),
        language = text(o, "language"),
        isbn = text(o, "isbn"),
        description = text(o, "description"),
        rating = o.optInt("rating").takeIf { o.has("rating") && !o.isNull("rating") },
        identifier = text(o, "identifier"),
    )

    private fun text(o: JSONObject, key: String): String? = if (o.has(key) && !o.isNull(key)) o.optString(key) else null

    private fun strings(a: JSONArray?): List<String> = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optString(it).takeIf { s -> s.isNotEmpty() } }

    /**
     * Whether a file in a book's folder goes with the book: the book and its cover, the file it was added as and the
     * ones kept to go back to, and how it's read (but not where it was left: that goes with the record, to put together
     * with the other device's), nor what this device works out for itself.
     */
    fun sendable(name: String): Boolean =
        name == "book.epub" || name == "cover.jpg" || name == "original.epub" ||
            name == "look.json" || name == "pictures.json" || name == "comic.json" ||
            (name.startsWith("source.") && name.length > 7) || (name.startsWith("original-cover.") && name.length > 15)
}
