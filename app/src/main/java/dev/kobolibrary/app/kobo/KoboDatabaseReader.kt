package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.kobo.db.SqlRow
import dev.kobolibrary.app.kobo.db.SqlSource
import java.util.TreeSet

class KoboDatabaseException(message: String) : Exception(message)

/**
 * Reads books and collections out of a copy of the Kobo's KoboReader.sqlite.
 *
 * Read-only. Column lists differ between firmware versions, so every query is built from the
 * columns that actually exist. A book is a row of the content table with ContentType 6 and no parent
 * (BookID); its Accessibility value tells bought books, previews and recommendations apart. Those are
 * the Kobo's own conventions (see NOTICE.md for where they were learned).
 */
class KoboDatabaseReader(private val db: SqlSource) {

    fun read(): KoboSnapshot {
        val warnings = mutableListOf<String>()
        val tables = tables()
        if ("content" !in tables) {
            throw KoboDatabaseException(tr("The Kobo database has no 'content' table, so it can't be read."))
        }

        val dbVersion = readDbVersion(warnings)
        val ratings = if ("ratings" in tables) readRatings(warnings) else emptyMap()
        val books = readBooks(warnings).map { b -> ratings[b.contentId]?.let { b.copy(rating = it) } ?: b }
        val (collections, byBook) = readCollections(tables, books.associateBy { it.contentId }, warnings)

        return KoboSnapshot(
            dbVersion = dbVersion,
            allBooks = books,
            collections = collections,
            collectionsByBook = byBook,
            warnings = warnings,
        )
    }

    fun tables(): Set<String> = ciSet(
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").mapNotNull { it.string("name") }
    )

    fun columns(table: String): Set<String> = ciSet(
        db.query("PRAGMA table_info(\"$table\")").mapNotNull { it.string("name") }
    )

    private fun readDbVersion(warnings: MutableList<String>): Int = try {
        db.query("SELECT version FROM DbVersion").firstOrNull()?.long("version")?.toInt() ?: 0
    } catch (e: Exception) {
        warnings += tr("Could not read the database version ({0}).", e.message)
        0
    }

    /** Stars given on the Kobo, by ContentID. */
    private fun readRatings(warnings: MutableList<String>): Map<String, Int> = try {
        val cols = columns("ratings")
        if ("ContentID" !in cols || "Rating" !in cols) emptyMap()
        else db.query("SELECT ContentID, Rating FROM ratings").mapNotNull { r ->
            val id = r.string("ContentID") ?: return@mapNotNull null
            val stars = r.long("Rating")?.toInt()?.takeIf { it in 1..5 } ?: return@mapNotNull null
            id to stars
        }.toMap()
    } catch (e: Exception) {
        warnings += tr("Could not read the Kobo's ratings ({0}).", e.message)
        emptyMap()
    }

    private fun readBooks(warnings: MutableList<String>): List<KoboBook> {
        val available = columns("content")
        val required = listOf("ContentID", "ContentType")
        val missing = required.filter { it !in available }
        if (missing.isNotEmpty()) {
            throw KoboDatabaseException(tr("The Kobo database is missing column(s) {0}.", missing.joinToString()))
        }
        val selected = BOOK_COLUMNS.filter { it in available }
        val where = buildString {
            append("WHERE ContentType = 6")
            if ("BookID" in available) append(" AND BookID IS NULL")
        }
        val sql = "SELECT ${selected.joinToString { "\"$it\"" }} FROM content $where"
        val rows = db.query(sql)
        val books = ArrayList<KoboBook>(rows.size)
        var skipped = 0
        for (row in rows) {
            val book = parseBook(row)
            if (book == null) skipped++ else books += book
        }
        if (skipped > 0) warnings += tr("{0} book row(s) had no ContentID and were skipped.", skipped)
        return books
    }

    private fun parseBook(row: SqlRow): KoboBook? {
        val contentId = row.string("ContentID")?.takeIf { it.isNotBlank() } ?: return null
        val isFile = contentId.startsWith("file://")
        val relativePath = if (isFile) relativePathOf(contentId) else null

        val accessibility = row.long("Accessibility")?.toInt()
        val userId = row.string("___UserID")
        val kind = when {
            isFile -> BookKind.SIDELOADED
            accessibility == null -> BookKind.PURCHASED
            accessibility in PURCHASED_ACCESSIBILITY -> BookKind.PURCHASED
            accessibility == 6 && !userId.isNullOrEmpty() -> BookKind.PREVIEW
            userId.isNullOrEmpty() && accessibility in RECOMMENDATION_ACCESSIBILITY -> BookKind.RECOMMENDATION
            else -> BookKind.OTHER
        }

        val mime = row.string("MimeType")
        val fileName = relativePath?.substringAfterLast('/')
        val title = row.string("Title")?.takeIf { it.isNotBlank() }
            ?: row.string("BookTitle")?.takeIf { it.isNotBlank() }
            ?: fileName?.let { stripBookExtension(it) }
            ?: "Untitled"

        return KoboBook(
            contentId = contentId,
            title = title.trim(),
            author = row.string("Attribution")?.trim()?.takeIf { it.isNotEmpty() },
            subtitle = row.string("Subtitle")?.trim()?.takeIf { it.isNotEmpty() },
            series = row.string("Series")?.trim()?.takeIf { it.isNotEmpty() },
            seriesNumber = (row.string("SeriesNumber") ?: row.double("SeriesNumberFloat")?.toString())
                ?.trim()?.takeIf { it.isNotEmpty() },
            description = row.string("Description")?.trim()?.takeIf { it.isNotEmpty() },
            publisher = row.string("Publisher")?.trim()?.takeIf { it.isNotEmpty() },
            language = row.string("Language")?.trim()?.takeIf { it.isNotEmpty() },
            isbn = row.string("ISBN")?.trim()?.takeIf { it.isNotEmpty() },
            mimeType = mime,
            format = formatOf(mime, relativePath),
            imageId = row.string("ImageId")?.takeIf { it.isNotBlank() } ?: KoboImages.imageIdFromContentId(contentId),
            kind = kind,
            isDownloaded = row.bool("IsDownloaded") ?: true,
            isExpired = row.long("___ExpirationStatus") == 3L,
            readStatus = ReadStatus.fromKobo(row.long("ReadStatus")),
            percentRead = (row.long("___PercentRead") ?: 0L).toInt().coerceIn(0, 100),
            lastReadMillis = KoboDates.parseMillis(row.string("DateLastRead")),
            addedMillis = KoboDates.parseMillis(row.string("DateAdded")),
            fileSize = row.long("___FileSize")?.takeIf { it > 0 },
            published = row.string("DateCreated")?.trim()?.takeIf { it.isNotEmpty() },
            seriesId = row.string("SeriesID")?.trim()?.takeIf { it.isNotEmpty() },
            words = row.long("WordCount")?.toInt()?.takeIf { it > 0 },
            relativePath = relativePath,
            chapterBookmark = row.string("ChapterIDBookmarked")?.trim()?.takeIf { it.isNotEmpty() },
            adobeLocation = row.string("adobe_location")?.trim()?.takeIf { it.isNotEmpty() },
            chapterProgress = row.double("CurrentChapterProgress")?.takeIf { it in 0.0..1.0 },
        )
    }

    private fun readCollections(
        tables: Set<String>,
        booksById: Map<String, KoboBook>,
        warnings: MutableList<String>,
    ): Pair<List<KoboCollection>, Map<String, List<String>>> {
        if ("Shelf" !in tables) {
            warnings += tr("This Kobo's database has no collections table.")
            return emptyList<KoboCollection>() to emptyMap()
        }

        val shelfCols = columns("Shelf")
        val shelfSelected = SHELF_COLUMNS.filter { it in shelfCols }
        if ("Name" !in shelfCols && "InternalName" !in shelfCols) {
            warnings += tr("The collections table has no name column.")
            return emptyList<KoboCollection>() to emptyMap()
        }

        data class ShelfRow(
            val name: String, val internalName: String?, val id: String?, val type: String?,
            val visible: Boolean, val synced: Boolean, val created: Long?, val modified: Long?,
        )

        val shelves = db.query("SELECT ${shelfSelected.joinToString { "\"$it\"" }} FROM Shelf")
            .filter { it.bool("_IsDeleted") != true }
            .mapNotNull { r ->
                val name = r.string("Name")?.takeIf { it.isNotBlank() }
                    ?: r.string("InternalName")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                ShelfRow(
                    name = name,
                    internalName = r.string("InternalName"),
                    id = r.string("Id"),
                    type = r.string("Type"),
                    visible = r.bool("_IsVisible") ?: true,
                    synced = r.bool("_IsSynced") ?: false,
                    created = KoboDates.parseMillis(r.string("CreationDate")),
                    modified = KoboDates.parseMillis(r.string("LastModified")),
                )
            }

        // ShelfContent.ShelfName normally equals Shelf.Name; fall back to InternalName, then any case.
        val byName = HashMap<String, ShelfRow>()
        val byInternal = HashMap<String, ShelfRow>()
        val byLower = HashMap<String, ShelfRow>()
        for (s in shelves) {
            byName.putIfAbsent(s.name, s)
            s.internalName?.let { byInternal.putIfAbsent(it, s) }
            byLower.putIfAbsent(s.name.lowercase(), s)
        }

        val members = LinkedHashMap<String, MutableList<String>>() // shelf name -> content ids
        val byBook = HashMap<String, MutableList<String>>()
        var unknownShelfEntries = 0
        var missingBookEntries = 0

        if ("ShelfContent" in tables) {
            val scCols = columns("ShelfContent")
            val scSelected = SHELF_CONTENT_COLUMNS.filter { it in scCols }
            if ("ShelfName" in scCols && "ContentId" in scCols) {
                val orderBy = if ("DateModified" in scCols) " ORDER BY DateModified" else ""
                val rows = db.query("SELECT ${scSelected.joinToString { "\"$it\"" }} FROM ShelfContent$orderBy")
                for (r in rows) {
                    if (r.bool("_IsDeleted") == true) continue
                    val shelfName = r.string("ShelfName") ?: continue
                    val contentId = r.string("ContentId") ?: continue
                    val shelf = byName[shelfName] ?: byInternal[shelfName] ?: byLower[shelfName.lowercase()]
                    if (shelf == null) {
                        unknownShelfEntries++
                        continue
                    }
                    val list = members.getOrPut(shelf.name) { mutableListOf() }
                    if (contentId in list) continue
                    list += contentId
                    if (contentId !in booksById) missingBookEntries++
                    byBook.getOrPut(contentId) { mutableListOf() } += shelf.name
                }
            } else {
                warnings += tr("The collection membership table has unexpected columns.")
            }
        } else {
            warnings += tr("This Kobo's database has no collection membership table.")
        }

        if (unknownShelfEntries > 0) {
            warnings += if (unknownShelfEntries == 1) tr("1 collection entry points to a deleted or missing collection (the Kobo ignores these).")
            else tr("{0} collection entries point to a deleted or missing collection (the Kobo ignores these).", unknownShelfEntries)
        }
        if (missingBookEntries > 0) {
            warnings += if (missingBookEntries == 1) {
                tr("1 collection entry points to a book that isn't in the database yet. Books you've just sent are added when you eject the Kobo.")
            } else {
                tr("{0} collection entries point to a book that isn't in the database yet. Books you've just sent are added when you eject the Kobo.", missingBookEntries)
            }
        }

        val collections = shelves
            .distinctBy { it.name }
            .map { s ->
                KoboCollection(
                    name = s.name,
                    internalName = s.internalName,
                    id = s.id,
                    type = s.type,
                    isVisible = s.visible,
                    isSynced = s.synced,
                    createdMillis = s.created,
                    modifiedMillis = s.modified,
                    memberIds = members[s.name].orEmpty(),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val sortedByBook = byBook.mapValues { (_, names) -> names.sortedWith(String.CASE_INSENSITIVE_ORDER) }
        return collections to sortedByBook
    }

    companion object {
        /** Every column we would like; only those present in this firmware's database are queried. */
        val BOOK_COLUMNS = listOf(
            "ContentID", "ContentType", "MimeType", "BookID", "BookTitle", "Title", "Attribution",
            "Subtitle", "Description", "Publisher", "Language", "ISBN", "ImageId", "ReadStatus",
            "___PercentRead", "DateLastRead", "DateAdded", "Series", "SeriesNumber", "SeriesNumberFloat",
            "___FileSize", "___ExpirationStatus", "Accessibility", "IsDownloaded", "___UserID",
            "DateCreated", "SeriesID", "WordCount",
            // Where the Kobo is in the book.
            "ChapterIDBookmarked", "adobe_location", "CurrentChapterProgress",
        )
        val SHELF_COLUMNS = listOf(
            "Id", "Name", "InternalName", "Type", "CreationDate", "LastModified",
            "_IsDeleted", "_IsVisible", "_IsSynced",
        )
        val SHELF_CONTENT_COLUMNS = listOf("ShelfName", "ContentId", "DateModified", "_IsDeleted")

        private val PURCHASED_ACCESSIBILITY = setOf(1, 2, 8, 9)
        private val RECOMMENDATION_ACCESSIBILITY = setOf(-1, 4, 6)

        private const val ONBOARD_PREFIX = "file:///mnt/onboard/"

        fun relativePathOf(contentId: String): String? = when {
            contentId.startsWith(ONBOARD_PREFIX) -> contentId.removePrefix(ONBOARD_PREFIX)
            contentId.startsWith("file://") -> contentId.removePrefix("file://")
            else -> null
        }

        fun stripBookExtension(fileName: String): String {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".kepub.epub") -> fileName.dropLast(".kepub.epub".length)
                lower.contains('.') -> fileName.substringBeforeLast('.')
                else -> fileName
            }
        }

        fun formatOf(mime: String?, path: String?): String {
            val p = path?.lowercase().orEmpty()
            val m = mime?.lowercase().orEmpty()
            return when {
                m == "application/x-kobo-epub+zip" || p.endsWith(".kepub.epub") || p.endsWith(".kepub") -> "KEPUB"
                m == "application/epub+zip" || p.endsWith(".epub") -> "EPUB"
                m == "application/pdf" || p.endsWith(".pdf") -> "PDF"
                m.contains("cbz") || p.endsWith(".cbz") -> "CBZ"
                m.contains("cbr") || p.endsWith(".cbr") -> "CBR"
                m == "application/x-kobo-mp3z" -> "Audiobook"
                m.contains("mobipocket") || p.endsWith(".mobi") -> "MOBI"
                m.startsWith("text/plain") || p.endsWith(".txt") -> "TXT"
                m.startsWith("text/html") || p.endsWith(".html") || p.endsWith(".htm") -> "HTML"
                m.contains("rtf") || p.endsWith(".rtf") -> "RTF"
                m.isNotEmpty() -> m.substringAfterLast('/').uppercase()
                else -> "Unknown"
            }
        }

        private fun ciSet(items: List<String>): Set<String> =
            TreeSet(String.CASE_INSENSITIVE_ORDER).apply { addAll(items) }
    }
}
