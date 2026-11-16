package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr

/** How a book got onto the Kobo, as its database records it. */
enum class BookKind {
    /** Copied onto the Kobo by USB (what this app will manage). */
    SIDELOADED,
    /** Bought or borrowed through Kobo. */
    PURCHASED,
    PREVIEW,
    RECOMMENDATION,
    OTHER,
}

enum class ReadStatus(val label: String) {
    UNREAD(tr("Unread")),
    READING(tr("Reading")),
    FINISHED(tr("Finished"));

    companion object {
        fun fromKobo(value: Long?): ReadStatus = when (value) {
            2L -> FINISHED
            1L, 3L -> READING
            else -> UNREAD
        }
    }
}

data class KoboBook(
    val contentId: String,
    val title: String,
    val author: String?,
    val subtitle: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val publisher: String?,
    val language: String?,
    val isbn: String?,
    val mimeType: String?,
    val format: String,
    val imageId: String,
    val kind: BookKind,
    val isDownloaded: Boolean,
    val isExpired: Boolean,
    val readStatus: ReadStatus,
    val percentRead: Int,
    val lastReadMillis: Long?,
    val addedMillis: Long?,
    val fileSize: Long?,
    /** Path on the Kobo's drive for sideloaded books, e.g. "Books/Author/Title.kepub.epub". */
    val relativePath: String?,
    /** DateCreated: for books, when they were published. */
    val published: String? = null,
    /** What the Kobo groups the series by: the series name for sideloaded books, an id for store books. */
    val seriesId: String? = null,
    /** Stars (1 to 5) given on the Kobo, usually when finishing the book. */
    val rating: Int? = null,
    /** The Kobo's own word count, when it has worked one out. */
    val words: Int? = null,
    /**
     * Where the Kobo is in the book (its ChapterIDBookmarked): the chapter file, and in a kepub the
     * sentence, e.g. "OEBPS/Text/chapter3.xhtml#kobo.12.1". See [KoboPlaces].
     */
    val chapterBookmark: String? = null,
    /** In an EPUB the Kobo reads with Adobe's renderer, the spot in the chapter, e.g. "point(/1/4/2/1:0)". */
    val adobeLocation: String? = null,
    /** How far through the chapter the Kobo is, 0 to 1, when its database says. */
    val chapterProgress: Double? = null,
    /**
     * How the Kobo shares the book out between its chapters, for its percentage read: where each starts and how
     * much of the book it is. Only for books it has a place in. See [KoboChapterSpan].
     */
    val chapterSpans: List<KoboChapterSpan> = emptyList(),
) {
    /** Books that are actually on the device and that you would see in its library. */
    val isOnDevice: Boolean
        get() = isDownloaded && !isExpired && (kind == BookKind.SIDELOADED || kind == BookKind.PURCHASED)

    val isSideloaded: Boolean get() = kind == BookKind.SIDELOADED

    /** "Mistborn #3", or just the series name if it has no number. */
    val seriesLabel: String?
        get() {
            val name = series?.takeIf { it.isNotBlank() } ?: return null
            val number = seriesNumber?.takeIf { it.isNotBlank() }?.let { formatSeriesNumber(it) }
            return if (number != null) "$name #$number" else name
        }

    companion object {
        private fun formatSeriesNumber(raw: String): String {
            val d = raw.toDoubleOrNull() ?: return raw
            return if (d == Math.floor(d)) d.toLong().toString() else d.toString()
        }
    }
}

/**
 * One of a book's chapters as the Kobo's database has it (a row of ContentType 9): its ContentID and title, which
 * between them name the chapter's file, and where it starts and how much of the book it is, in percent. The Kobo's
 * percentage read is the [offset] of the chapter it's in plus that share of its [size].
 */
data class KoboChapterSpan(val id: String, val title: String?, val offset: Double, val size: Double)

data class KoboCollection(
    /** The collection's name as shown on the Kobo. */
    val name: String,
    val internalName: String?,
    val id: String?,
    val type: String?,
    val isVisible: Boolean,
    val isSynced: Boolean,
    val createdMillis: Long?,
    val modifiedMillis: Long?,
    /** ContentIDs of every book the Kobo lists in this collection (including ones not on the device). */
    val memberIds: List<String>,
)

data class KoboSnapshot(
    val dbVersion: Int,
    /** Every book row in the database, including store books not downloaded, previews and so on. */
    val allBooks: List<KoboBook>,
    val collections: List<KoboCollection>,
    /** ContentID -> names of the collections it is in. */
    val collectionsByBook: Map<String, List<String>>,
    /** Things that looked odd while reading the database; shown in diagnostics and the log. */
    val warnings: List<String>,
) {
    val books: List<KoboBook> = allBooks.filter { it.isOnDevice }
    val booksById: Map<String, KoboBook> = allBooks.associateBy { it.contentId }

    val sideloadedCount: Int = books.count { it.kind == BookKind.SIDELOADED }
    val purchasedOnDeviceCount: Int = books.count { it.kind == BookKind.PURCHASED }
    val purchasedInCloudCount: Int = allBooks.count { it.kind == BookKind.PURCHASED && !it.isDownloaded && !it.isExpired }
    val previewCount: Int = allBooks.count { it.kind == BookKind.PREVIEW }

    /** On-device books in a collection: series in reading order first, then everything else by title. */
    fun booksIn(collection: KoboCollection): List<KoboBook> =
        collection.memberIds.mapNotNull { booksById[it] }.filter { it.isOnDevice }.sortedWith(collectionOrder)

    private companion object {
        val collectionOrder: Comparator<KoboBook> = compareBy<KoboBook> { it.series.isNullOrBlank() }
            .thenBy { it.series?.lowercase() ?: "" }
            .thenBy { it.seriesNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    }

    fun booksWithoutCollection(): List<KoboBook> =
        books.filter { collectionsByBook[it.contentId].isNullOrEmpty() }

    /** The same, without books that have been deleted from the Kobo since it was read. */
    fun without(removed: Set<String>): KoboSnapshot = if (removed.isEmpty()) this else KoboSnapshot(
        dbVersion = dbVersion,
        allBooks = allBooks.filter { it.contentId !in removed },
        collections = collections,
        collectionsByBook = collectionsByBook - removed,
        warnings = warnings,
    )
}
