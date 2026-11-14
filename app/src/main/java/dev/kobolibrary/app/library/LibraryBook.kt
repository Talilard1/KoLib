package dev.kobolibrary.app.library

import dev.kobolibrary.app.epub.BookMetadata

enum class BookSource { FILE, KOBO }

/** A book kept on the tablet. Its details mirror what is written inside its EPUB. */
data class LibraryBook(
    val id: String,
    val meta: BookMetadata,
    /** Kobo collections this book belongs in (kept by the app; the EPUB has no place for them). */
    val collections: List<String>,
    val fileSize: Long,
    val hasCover: Boolean,
    /** Bumped whenever the cover changes, so cached pictures refresh. */
    val coverVersion: Long,
    val addedAt: Long,
    val modifiedAt: Long,
    val source: BookSource,
    val originalName: String?,
    /** Where this book lives on the Kobo ("Books/Author/Title.kepub.epub"), once it's been there. */
    val koboPath: String?,
    /** When the app last copied it to the Kobo. */
    val sentAt: Long? = null,
    /** Words in the book's text, for its page count. */
    val words: Int? = null,
    /**
     * The collections the library and the Kobo last agreed this book is in, so a later difference
     * shows which side changed. Null until they've been compared.
     */
    val koboCollections: List<String>? = null,
    /**
     * The cover is to be left unchanged: chosen with "Leave unchanged" in Change cover. It isn't fitted
     * to the Kobo's screen when the book is sent; the Kobo shows it the way it shows any cover.
     */
    val coverAsIs: Boolean = false,
) {
    val title: String get() = meta.title
    val authorLine: String? get() = meta.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /** Pages at about 250 words each, the usual for a paperback. */
    val pages: Int? get() = words?.let { if (it <= 0) null else maxOf(1, (it + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE) }

    /** On the Kobo, but edited here since it was copied there. */
    val changedSinceOnKobo: Boolean get() = koboPath != null && modifiedAt > (sentAt ?: addedAt) + 1000

    companion object {
        const val WORDS_PER_PAGE = 250
    }
}

sealed interface ImportOutcome {
    val name: String

    data class Added(override val name: String, val book: LibraryBook) : ImportOutcome
    /**
     * The library has [existing] already. When it was asked for, the file is kept aside as [held], so
     * it can still replace that book or be added as well; whoever asked deletes it when done.
     */
    data class AlreadyThere(
        override val name: String,
        val existing: LibraryBook,
        val held: java.io.File? = null,
        /** The details in the file being added. */
        val meta: dev.kobolibrary.app.epub.BookMetadata? = null,
    ) : ImportOutcome
    data class Failed(override val name: String, val reason: String) : ImportOutcome
}

/** Details the Kobo knows, used only where the book's own file says nothing. */
data class KoboFallback(
    val title: String?,
    val authors: List<String>,
    val series: String?,
    val seriesIndex: Double?,
)
