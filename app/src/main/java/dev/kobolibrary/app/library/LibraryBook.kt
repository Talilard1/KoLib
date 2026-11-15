package dev.kobolibrary.app.library

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.convert.BookFormats
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
    /**
     * The kind of file the book was added as, when it wasn't an EPUB ("PDF", "MOBI"...): it was made into
     * one, and the file it came as is kept with it. A PDF reads and can be sent as it is; "SCAN" is a PDF of
     * scanned pages, which only reads and goes to the Kobo as the PDF.
     */
    val sourceFormat: String? = null,
) {
    /** Added as a PDF, which is kept to read and send as it is. */
    val isPdf: Boolean get() = sourceFormat == "PDF" || isScan

    /**
     * A PDF that's only read and sent as the PDF: scanned pages, which have no text, or one too big to
     * make into an EPUB on the device. Its EPUB is a stand-in.
     */
    val isScan: Boolean get() = sourceFormat == BookFormats.PDF_SCAN || sourceFormat == BookFormats.PDF_ONLY

    /**
     * A comic book (added as a CBZ, CBR or CB7): kept as that file, read in the comic reader, and sent to the
     * Kobo as it is. Its EPUB is a stand-in, with its details and cover.
     */
    val isComic: Boolean get() = sourceFormat == "CBZ" || sourceFormat == "CBR" || sourceFormat == "CB7"

    /** Goes to the Kobo as its PDF: when [pdfs] are sent as PDFs, and always for scanned pages. */
    fun goesAsPdf(pdfs: Boolean): Boolean = isScan || (pdfs && isPdf)

    /** What kind of file the book was added as, for its details: "PDF file, made into an EPUB". */
    val addedAs: String? get() = when (sourceFormat) {
        null -> null
        BookFormats.PDF_SCAN -> tr("PDF of scanned pages or pictures")
        BookFormats.PDF_ONLY -> tr("PDF, too big to make into an EPUB")
        "CBZ", "CBR", "CB7" -> tr("{0} comic, kept as it is", sourceFormat)
        else -> dev.kobolibrary.app.convert.SourceFormat.entries.firstOrNull { it.name == sourceFormat }?.let { tr("{0} file, made into an EPUB", it.label) }
    }
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
