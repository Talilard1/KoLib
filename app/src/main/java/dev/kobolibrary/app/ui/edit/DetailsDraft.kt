package dev.kobolibrary.app.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.HtmlText
import dev.kobolibrary.app.epub.Isbn
import dev.kobolibrary.app.epub.Names
import dev.kobolibrary.app.epub.OpfDocument
import java.time.LocalDate
import java.time.YearMonth

/** The details being edited, as typed. Nothing reaches the book until it's saved. */
class DetailsDraft(private val original: BookMetadata, originalCollections: List<String>) {

    var title by mutableStateOf(original.title)

    /** How the title sorts when it's been set by hand; null means it follows the title. */
    var titleSort by mutableStateOf(
        original.titleSort?.takeIf { it.isNotBlank() && it != Names.titleSort(original.title) }
    )

    val authors = mutableStateListOf<String>().apply { addAll(original.authors) }

    /** Sort names the book has for particular authors ("Tolkien, J. R. R."). */
    val authorSorts = mutableStateMapOf<String, String>().apply { putAll(original.authorSorts) }

    var series by mutableStateOf(original.series.orEmpty())
    var seriesIndex by mutableStateOf(original.seriesIndex?.let { Names.formatIndex(it) }.orEmpty())
    val collections = mutableStateListOf<String>().apply { addAll(originalCollections) }
    val tags = mutableStateListOf<String>().apply { addAll(original.tags) }
    var publisher by mutableStateOf(original.publisher.orEmpty())
    var published by mutableStateOf(original.published.orEmpty())
    var language by mutableStateOf(original.language.orEmpty())
    var isbn by mutableStateOf(original.isbn.orEmpty())

    /** HTML. */
    var description by mutableStateOf(original.description)

    /** 0–10, two per star; 0 is no rating. */
    var rating by mutableIntStateOf(original.rating ?: 0)

    private val baseline: BookMetadata = toMetadata()
    private val baselineCollections: List<String> = collections.toList()
    private val originalIsbn = original.isbn.orEmpty()

    fun toMetadata(): BookMetadata {
        val authorList = authors.map { Names.normalizeSpace(it) }.filter { it.isNotEmpty() }
        val seriesName = Names.normalizeSpace(series)
        return original.copy(
            title = Names.normalizeSpace(title),
            titleSort = titleSort?.let { Names.normalizeSpace(it) }?.takeIf { it.isNotEmpty() },
            authors = authorList,
            authorSorts = authorSorts.filterKeys { it in authorList },
            series = seriesName.ifEmpty { null },
            seriesIndex = if (seriesName.isEmpty()) null else parseSeriesIndex(seriesIndex),
            tags = tags.map { Names.normalizeSpace(it) }.filter { it.isNotEmpty() },
            publisher = Names.normalizeSpace(publisher).ifEmpty { null },
            published = published.trim().ifEmpty { null }?.let { OpfDocument.normalizeDate(it) ?: it },
            language = language.trim().ifEmpty { null },
            isbn = isbn.trim().ifEmpty { null }?.let { Isbn.normalize(it).ifEmpty { null } },
            description = description?.takeIf { !HtmlText.isBlank(it) },
            rating = rating.takeIf { it > 0 },
        )
    }

    fun collectionList(): List<String> = collections.map { Names.normalizeSpace(it) }.filter { it.isNotEmpty() }

    val isDirty: Boolean get() = toMetadata() != baseline || collectionList() != baselineCollections

    // ---- checks -----------------------------------------------------------------------------

    val titleProblem: String? get() = if (title.isBlank()) "Enter a title." else null

    val seriesIndexProblem: String?
        get() = if (series.isNotBlank() && seriesIndex.isNotBlank() && parseSeriesIndex(seriesIndex) == null) {
            "Use a number, like 3 or 2.5."
        } else null

    val publishedProblem: String?
        get() = if (published.isNotBlank() && parseDate(published) == null) "Use a year (2005) or a date (2005-07-17)." else null

    /** An ISBN that was already wrong isn't held against an edit; a newly typed one must add up. */
    val isbnProblem: String?
        get() {
            val typed = isbn.trim()
            if (typed.isEmpty() || Isbn.isValid(typed)) return null
            if (Isbn.normalize(typed) == Isbn.normalize(originalIsbn)) return null
            val digits = Isbn.normalize(typed)
            return if (digits.length != 10 && digits.length != 13) "An ISBN has 10 or 13 digits. This one has ${digits.length}."
            else "This ISBN isn't valid. Check for a typo."
        }

    val isbnWarning: String?
        get() = if (isbn.isNotBlank() && !Isbn.isValid(isbn) && isbnProblem == null) "This ISBN came with the book, but it isn't valid." else null

    val problems: List<String> get() = listOfNotNull(titleProblem, seriesIndexProblem, publishedProblem, isbnProblem)

    fun sortNameFor(author: String): String = authorSorts[author] ?: Names.authorSort(author)

    /** Renames an author in place, keeping their position and any sort name set by hand. */
    fun renameAuthor(index: Int, name: String, sortName: String?) {
        val old = authors.getOrNull(index) ?: return
        val clean = Names.normalizeSpace(name)
        if (clean.isEmpty()) return
        authorSorts.remove(old)
        authors[index] = clean
        val sort = sortName?.let { Names.normalizeSpace(it) }
        if (!sort.isNullOrEmpty()) authorSorts[clean] = sort
    }

    fun moveAuthorToFront(index: Int) {
        if (index <= 0 || index >= authors.size) return
        val a = authors.removeAt(index)
        authors.add(0, a)
    }

    companion object {
        fun parseSeriesIndex(s: String): Double? =
            s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && !it.isNaN() && !it.isInfinite() }

        /** A year, year-month or full date, checked properly ("2005-02-30" is no good). */
        fun parseDate(s: String): String? {
            val m = Regex("""^(\d{4})(?:-(\d{1,2})(?:-(\d{1,2}))?)?$""").matchEntire(s.trim()) ?: return null
            val year = m.groupValues[1].toInt()
            if (year < 100) return null
            val month = m.groupValues[2].toIntOrNull()
            val day = m.groupValues[3].toIntOrNull()
            return runCatching {
                when {
                    month == null -> "%04d".format(year)
                    day == null -> YearMonth.of(year, month).toString()
                    else -> LocalDate.of(year, month, day).toString()
                }
            }.getOrNull()
        }
    }
}
