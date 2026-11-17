package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr
import android.content.Context
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.EpubEditor
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.ui.cover.CoverRendering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.Normalizer

/** The two ways a book can go onto the Kobo. */
enum class SendFormat(val label: String, val extension: String, val explanation: String) {
    KEPUB(
        tr("Kepub"),
        ".kepub.epub",
        tr("Kobo's own format. Pages turn faster, and the Kobo shows reading stats."),
    ),
    EPUB(
        "EPUB",
        ".epub",
        tr("The standard format. The Kobo opens it in its older, slower reader."),
    );

    companion object {
        fun of(path: String): SendFormat = if (path.lowercase().endsWith(".kepub.epub")) KEPUB else EPUB
    }
}

/**
 * Where books go on the Kobo: a folder for each author (by sort name) with `Title - Authors.kepub.epub`
 * inside it, an arrangement people who have used Calibre will recognise.
 */
object KoboPaths {
    private val unsafe = Regex("""[\\/?%*:;|"'<>$!\u0000-\u001f]""")

    fun component(raw: String, max: Int): String {
        val cleaned = raw.replace(unsafe, "_").replace(Regex("\\s+"), " ").trim()
        return cleaned.take(max).trimEnd('.', ' ').trimStart('.').ifEmpty { "_" }
    }

    /** [number] above 1 is for a second book with the same title and authors: "Title - Author (2)". */
    fun forBook(meta: BookMetadata, format: SendFormat, number: Int = 1): String {
        val folder = component(meta.authorSort.ifBlank { tr("Unknown") }, 60)
        val authors = meta.authors.joinToString(" & ").ifBlank { tr("Unknown") }
        val name = component("${meta.title} - $authors", 110) + if (number > 1) " ($number)" else ""
        return "$folder/$name${format.extension}"
    }

    /**
     * A new place for a book that isn't where any other book is, in either format: [taken] says
     * whether a [stem] belongs to another book.
     */
    fun freeFor(meta: BookMetadata, format: SendFormat, taken: (String) -> Boolean): String {
        var n = 1
        while (true) {
            val path = forBook(meta, format, n)
            if (n >= 999 || !taken(stem(path))) return path
            n++
        }
    }

    private fun base(path: String): String = when {
        path.lowercase().endsWith(".kepub.epub") -> path.dropLast(".kepub.epub".length)
        else -> listOf(".epub", ".pdf", ".cbz", ".cbr").firstOrNull { path.lowercase().endsWith(it) }?.let { path.dropLast(it.length) } ?: path
    }

    /** The same path with the other format's ending. */
    fun withFormat(path: String, format: SendFormat): String = base(path) + format.extension

    /** The same path, for the book sent as a PDF. */
    fun asPdf(path: String): String = asFile(path, "pdf")

    /** The same path, for the book sent as the file it was added as, ending in [extension] ("pdf", "cbz"). */
    fun asFile(path: String, extension: String): String = base(path) + "." + extension.lowercase()

    /** What a book on the Kobo at [path] is, as people say it: "kepub", "EPUB", "PDF", "CBZ" or "CBR". */
    fun kindOf(path: String): String = when {
        path.lowercase().endsWith(".kepub.epub") -> "kepub"
        path.lowercase().endsWith(".pdf") -> "PDF"
        path.lowercase().endsWith(".cbz") -> "CBZ"
        path.lowercase().endsWith(".cbr") -> "CBR"
        else -> "EPUB"
    }

    /** The path without its format's ending, in lower case: the same for a book in either format. */
    fun stem(path: String): String = base(path).lowercase()

    fun contentId(path: String) = "file:///mnt/onboard/" + path.trimStart('/')
}

sealed interface SendOutcome {
    val name: String

    /**
     * [replaced]: a file was already at [path]; [movedFrom]: the book's old file (in the other format, or
     * one whose chapters changed), now deleted.
     */
    data class Sent(
        override val name: String,
        val path: String,
        val format: SendFormat,
        val replaced: Boolean,
        val movedFrom: String?,
        val textFilesLeftAsTheyWere: Int,
        /** The cover wasn't the screen's shape, so the copy sent has one fitted to it. */
        val coverReshaped: Boolean = false,
        /** The Kobo's library pictures of the cover were written by the app. */
        val coverPicturesWritten: Boolean = false,
        /**
         * The Kobo's record of the book is kept (reading progress, highlights, collections), once its
         * database is told the new file's size and cover pictures: see [PendingKoboRecords].
         */
        val keptRecord: KeptRecord? = null,
        /** The Kobo will take this file in as a new book, forgetting what it had recorded about the old one. */
        val koboStartsAfresh: Boolean = false,
        /** The book has a new cover, which the Kobo will show once it has been restarted. */
        val coverShowsAfterRestart: Boolean = false,
    ) : SendOutcome

    /**
     * The changes to the Kobo's record of a book sent again, so it keeps the record: the new file's size,
     * and the usual name for its cover pictures ([imageId], when the record had another).
     */
    data class KeptRecord(val contentId: String, val fileSize: Long, val imageId: String? = null) {
        val columns: Map<String, Any?> get() = buildMap {
            put("___FileSize", fileSize)
            imageId?.let { put("ImageId", it) }
        }
    }

    data class Failed(override val name: String, val reason: String, val conversionFailed: Boolean = false) : SendOutcome
}

/**
 * Copies books onto the Kobo, converting them to kepub on the way when asked.
 *
 * A book the Kobo already has is replaced in place. Left alone, the Kobo would notice that the file's
 * size changed and take it in as a new book, forgetting the reading progress, highlights and collections
 * it had. So when the new file has the same chapters and contents as the old one, the Kobo's record of
 * it is kept: its database is told the new size. When the chapters changed, the old record wouldn't fit
 * the new file, so the book goes to a new place as a new book and the old file is deleted.
 *
 * The Kobo keeps the covers it has shown in memory, by book, until it restarts. So a book with a new
 * cover that hasn't been started on the Kobo also goes as a new book, which shows the new cover at once;
 * one that has been started keeps its record (and the reader's place), and its new cover shows once the
 * Kobo has been restarted.
 */
class KoboSender(private val context: Context) {

    private val work get() = File(context.cacheDir, "send").apply { mkdirs() }

    /** Where a book will go: its current place on the Kobo (in the chosen format), or a new one. */
    fun targetPath(meta: BookMetadata, koboPath: String?, format: SendFormat): String =
        koboPath?.let { KoboPaths.withFormat(it, format) } ?: KoboPaths.forBook(meta, format)

    suspend fun send(
        session: KoboSession,
        title: String,
        meta: BookMetadata,
        koboPath: String?,
        file: File,
        format: SendFormat,
        covers: CoverFitting = CoverFitting.BLUR,
        /** A place no other book has, for a book that has to go to the Kobo as a new one. */
        freshPlace: ((SendFormat) -> String)? = null,
        /** A PDF or a comic (CBZ, CBR, CB7) to send as it is, in place of [file]. */
        pdf: File? = null,
        onProgress: (Float) -> Unit = {},
    ): SendOutcome = withContext(Dispatchers.IO) {
        if (pdf != null) return@withContext sendAsIs(session, title, meta, koboPath, pdf, onProgress)
        var target = targetPath(meta, koboPath, format)
        var oldPath = koboPath?.takeIf { !it.equals(target, ignoreCase = true) }
        val temps = mutableListOf<File>()
        try {
            val screen = session.info.screen ?: CoverScreen.current
            val made = try {
                make(title, file, format, covers, screen, temps)
            } catch (e: KepubFailed) {
                return@withContext SendOutcome.Failed(title, tr("Couldn't convert it to kepub ({0}).", e.cause?.message ?: e.cause?.javaClass?.simpleName), conversionFailed = true)
            }
            val shaped = made.shaped
            val reshaped = made.reshaped
            val leftAlone = made.leftAlone
            val upload = made.file

            // A book the Kobo already has: keep its record if the new file fits it, else send it afresh.
            val record = session.snapshot.booksById[KoboPaths.contentId(target)]?.takeIf { it.isSideloaded }
            var keep = false
            var newCover = false
            if (record != null && session.tree.resolve(target) != null) {
                val before = BookLayout.onKobo(session.tree, target)
                val after = BookLayout.of(upload)
                val fits = before != null && after != null && before.sameInsides(after)
                newCover = before == null || after == null || before.cover != after.cover
                val started = record.percentRead > 0 || record.readStatus != ReadStatus.UNREAD || record.lastReadMillis != null
                keep = fits && (!newCover || started)
                if (!keep && freshPlace != null) {
                    val fresh = freshPlace(format)
                    if (!fresh.equals(target, ignoreCase = true)) {
                        AppLog.i(
                            "Send",
                            "'$title': " + (if (fits) "it has a new cover and hasn't been started" else "its chapters or contents changed") +
                                ", so it goes to the Kobo as a new book, at $fresh.",
                        )
                        oldPath = target
                        target = fresh
                    }
                }
            }
            val sameRecord = record != null && target.equals(record.relativePath, ignoreCase = true)

            val total = upload.length().coerceAtLeast(1)
            val replaced = session.tree.writeFile(target, upload) { copied -> onProgress(copied.toFloat() / total) }
            val contentId = KoboPaths.contentId(target)
            // The pictures go under the name the Kobo gives them for this file; the old ones are cleared.
            val imageId = KoboImages.imageIdFromContentId(contentId)
            if (replaced || sameRecord) clearPictures(session, setOfNotNull(imageId, record?.imageId?.takeIf { sameRecord }))
            var picturesWritten = false
            if (shaped != null) {
                picturesWritten = try {
                    writeCoverPictures(session, imageId, shaped, screen)
                    true
                } catch (e: Exception) {
                    AppLog.w("Send", "Couldn't write the Kobo's cover pictures for '$title'; it will make its own", e)
                    false
                }
            }
            if (oldPath != null) {
                runCatching {
                    if (session.tree.deleteIfExists(oldPath)) {
                        val oldId = KoboPaths.contentId(oldPath)
                        clearPictures(session, setOfNotNull(KoboImages.imageIdFromContentId(oldId), session.snapshot.booksById[oldId]?.imageId))
                    }
                }.onFailure { AppLog.w("Send", "Couldn't delete the old copy at $oldPath", it) }
            }
            // A record that had its pictures under another name (as 0.8.4 and 0.8.5 gave them) gets the usual one back.
            val kept = if (keep) SendOutcome.KeptRecord(contentId, upload.length(), imageId.takeIf { it != record?.imageId }) else null
            AppLog.i(
                "Send",
                "Sent '$title' to $target as ${format.label}" + when {
                    kept != null && newCover -> " (replaced; the Kobo keeps the reader's place, and shows the new cover once it's restarted)."
                    kept != null -> " (replaced; the Kobo keeps its reading progress and collections)."
                    replaced -> " (replaced)."
                    else -> "."
                },
            )
            SendOutcome.Sent(
                title, target, format, replaced, oldPath, leftAlone, reshaped, picturesWritten,
                keptRecord = kept,
                koboStartsAfresh = sameRecord && !keep,
                coverShowsAfterRestart = keep && newCover,
            )
        } catch (e: IOException) {
            AppLog.e("Send", "Sending '$title' failed", e)
            SendOutcome.Failed(title, e.message ?: tr("Couldn't write to the Kobo."))
        } catch (e: SecurityException) {
            AppLog.e("Send", "Sending '$title' failed", e)
            SendOutcome.Failed(title, tr("Android didn't allow writing to the Kobo."))
        } finally {
            temps.forEach { it.delete() }
        }
    }

    /** The book as it goes to the Kobo ([file], or a copy made of it), with how its cover went. */
    private class Made(val file: File, val shaped: ShapedCover?, val reshaped: Boolean, val leftAlone: Int)

    /** Making the kepub failed: [cause] says why. */
    private class KepubFailed(cause: Exception) : Exception(cause)

    /**
     * The book as it goes to the Kobo: its cover shaped for the Kobo's [screen] (when the book's own cover is
     * another shape, the copy sent gets the shaped one, so whatever the Kobo makes from the file looks right too),
     * then made a kepub when that's the [format]. Copies made go in [temps], to delete when done.
     */
    private fun make(title: String, file: File, format: SendFormat, covers: CoverFitting, screen: KoboScreen, temps: MutableList<File>): Made {
        val shaped = try {
            KoboCovers.shape(file, covers, screen)
        } catch (e: Exception) {
            AppLog.w("Send", "Couldn't shape the cover of '$title'; it goes as it is", e)
            null
        }
        var source = file
        var reshaped = false
        shaped?.jpegForBook?.let { jpeg ->
            val copy = File(work, "${System.nanoTime()}.epub").also { temps += it }
            try {
                file.copyTo(copy, overwrite = true)
                EpubEditor.replaceCover(copy, jpeg, "image/jpeg", screen.width, screen.height)
                source = copy
                reshaped = true
                AppLog.i("Send", "'$title': its ${shaped.sourceWidth} × ${shaped.sourceHeight} cover was fitted to the Kobo's screen (${covers.label}).")
            } catch (e: Exception) {
                AppLog.w("Send", "Couldn't put the fitted cover into '$title'; its own cover goes instead", e)
            }
        }
        var leftAlone = 0
        val upload = if (format == SendFormat.KEPUB) {
            val out = File(work, "${System.nanoTime()}.kepub.epub").also { temps += it }
            try {
                val result = EpubEditor.toKepub(source, out)
                leftAlone = result.skipped.size
                if (result.skipped.isNotEmpty()) {
                    AppLog.w("Send", "'$title': ${result.skipped.size} of ${result.documents} text files weren't valid XHTML and were sent unconverted.")
                }
            } catch (e: Exception) {
                AppLog.e("Send", "Kepub conversion of '$title' failed", e)
                throw KepubFailed(e)
            }
            out
        } else source
        return Made(upload, shaped, reshaped, leftAlone)
    }

    /**
     * [title] as a file for the Kobo to download over Wi-Fi ([dev.kobolibrary.app.kobo.KoboWifiServer]): made just as
     * it is to go over the cable, put in [dir]. A PDF or a comic ([asIs]) goes as it is (a CB7 as a CBZ).
     */
    fun fileForDownload(title: String, file: File, asIs: File?, format: SendFormat, covers: CoverFitting, dir: File, unique: String, name: String): WifiFile {
        dir.mkdirs()
        if (asIs != null) {
            val kind = asIs.name.substringAfterLast('.', "pdf").lowercase()
            val ending = when (kind) {
                "cbz", "cb7" -> "cbz"
                "cbr" -> "cbr"
                else -> "pdf"
            }
            val target = File(dir, "$unique.$ending")
            if (kind == "cb7") {
                try {
                    dev.kobolibrary.app.comic.ComicArchives.open(asIs).use { dev.kobolibrary.app.comic.ComicRepack.toCbz(it, target) }
                } catch (e: IOException) {
                    target.delete()
                    AppLog.e("Send", "Couldn't make a CBZ of '$title'", e)
                    throw IOException(tr("This comic couldn't be converted to CBZ for the Kobo."))
                }
            } else {
                asIs.copyTo(target, overwrite = true)
            }
            return WifiFile(target, name, KoboWifiServer.mimeFor(ending))
        }
        val temps = mutableListOf<File>()
        try {
            val made = try {
                make(title, file, format, covers, CoverScreen.current, temps)
            } catch (e: KepubFailed) {
                throw IOException(tr("Couldn't convert it to kepub ({0}).", e.cause?.message ?: e.cause?.javaClass?.simpleName))
            }
            val target = File(dir, unique + format.extension)
            if (made.file in temps) {
                if (!made.file.renameTo(target)) made.file.copyTo(target, overwrite = true)
            } else {
                made.file.copyTo(target, overwrite = true)
            }
            return WifiFile(target, name, KoboWifiServer.mimeFor("epub"))
        } finally {
            temps.forEach { it.delete() }
        }
    }

    /**
     * Sends a PDF or a comic (CBZ, CBR) as it is; a CB7, which the Kobo doesn't read, goes as a CBZ. The Kobo
     * makes its own cover pictures for these, from the first page. Sent again unchanged, the Kobo keeps its
     * record of it; changed, it takes it in as a new book.
     */
    private fun sendAsIs(session: KoboSession, title: String, meta: BookMetadata, koboPath: String?, original: File, onProgress: (Float) -> Unit): SendOutcome {
        val kind = original.name.substringAfterLast('.', "pdf").lowercase()
        var pdf = original
        var made: File? = null
        if (kind == "cb7") {
            made = File(work, "${System.nanoTime()}.cbz")
            try {
                dev.kobolibrary.app.comic.ComicArchives.open(original).use { dev.kobolibrary.app.comic.ComicRepack.toCbz(it, made) }
            } catch (e: IOException) {
                made.delete()
                AppLog.e("Send", "Couldn't make a CBZ of '$title'", e)
                return SendOutcome.Failed(title, tr("This comic couldn't be converted to CBZ for the Kobo."))
            }
            pdf = made
        }
        val extension = when (kind) {
            "cbz", "cb7" -> "cbz"
            "cbr" -> "cbr"
            else -> "pdf"
        }
        val target = KoboPaths.asFile(koboPath ?: KoboPaths.forBook(meta, SendFormat.EPUB), extension)
        val oldPath = koboPath?.takeIf { !it.equals(target, ignoreCase = true) }
        return try {
            val contentId = KoboPaths.contentId(target)
            val record = session.snapshot.booksById[contentId]?.takeIf { it.isSideloaded }
            val before = session.tree.resolve(target)?.size
            val unchanged = record != null && before == pdf.length()
            val total = pdf.length().coerceAtLeast(1)
            val replaced = session.tree.writeFile(target, pdf) { copied -> onProgress(copied.toFloat() / total) }
            if (replaced && !unchanged) clearPictures(session, setOfNotNull(KoboImages.imageIdFromContentId(contentId), record?.imageId))
            if (oldPath != null) {
                runCatching {
                    if (session.tree.deleteIfExists(oldPath)) {
                        val oldId = KoboPaths.contentId(oldPath)
                        clearPictures(session, setOfNotNull(KoboImages.imageIdFromContentId(oldId), session.snapshot.booksById[oldId]?.imageId))
                    }
                }.onFailure { AppLog.w("Send", "Couldn't delete the old copy at $oldPath", it) }
            }
            AppLog.i("Send", "Sent '$title' to $target as a ${extension.uppercase()}${if (replaced) " (replaced)" else ""}.")
            SendOutcome.Sent(
                title, target, SendFormat.EPUB, replaced, oldPath, 0,
                keptRecord = if (unchanged) SendOutcome.KeptRecord(contentId, pdf.length()) else null,
                koboStartsAfresh = record != null && !unchanged,
            )
        } catch (e: IOException) {
            AppLog.e("Send", "Sending '$title' failed", e)
            SendOutcome.Failed(title, e.message ?: tr("Couldn't write to the Kobo."))
        } catch (e: SecurityException) {
            AppLog.e("Send", "Sending '$title' failed", e)
            SendOutcome.Failed(title, tr("Android didn't allow writing to the Kobo."))
        } finally {
            made?.delete()
        }
    }

    /**
     * Writes the pictures the Kobo shows for a book under [imageId]: the full-screen one from the cover as
     * fitted to the screen, and the home screen's and library's from the book's own cover, at the shape of
     * an ordinary book cover so they line up with the Kobo's other books.
     */
    private fun writeCoverPictures(session: KoboSession, imageId: String, cover: ShapedCover, screen: KoboScreen) {
        val folder = "${KoboImages.IMAGES_DIR}/${KoboImages.folderFor(imageId)}"
        val tmp = File(work, "${System.nanoTime()}.jpg")
        val thumbnailSource = KoboCovers.thumbnailSource(cover)
        try {
            for (p in KoboCovers.pictures(screen)) {
                val picture = if (p.fullScreen) KoboCovers.scaled(cover.bitmap, p.width, p.height) else KoboCovers.boxed(thumbnailSource, p.width, p.height)
                tmp.writeBytes(CoverRendering.toJpeg(picture, 90))
                session.tree.writeFile("$folder/$imageId${p.suffix}", tmp)
            }
        } finally {
            tmp.delete()
            session.covers.forget(imageId)
        }
    }

    /** Deletes sideloaded books from the Kobo, with their cached covers. Returns the ones deleted. */
    suspend fun delete(session: KoboSession, books: List<KoboBook>): List<KoboBook> = withContext(Dispatchers.IO) {
        books.filter { it.isSideloaded && it.relativePath != null }.filter { book ->
            val path = book.relativePath!!
            try {
                val gone = session.tree.deleteIfExists(path) || session.tree.resolve(path) == null
                if (gone) {
                    clearPictures(session, setOf(KoboImages.imageIdFromContentId(KoboPaths.contentId(path)), book.imageId))
                    AppLog.i("Kobo", "Deleted '${book.title}' ($path) from the Kobo.")
                }
                gone
            } catch (e: Exception) {
                AppLog.e("Kobo", "Couldn't delete '${book.title}' from the Kobo", e)
                false
            }
        }
    }

    companion object {
        /**
         * A book the Kobo already has that is evidently this one (the same title and authors), for
         * library books whose place on the Kobo isn't known. Sending replaces it instead of adding a
         * second copy. Books another library book is already linked to ([taken], by
         * path) are passed over, so a second copy of a book doesn't replace the first on the Kobo.
         */
        fun findOnKobo(snapshot: KoboSnapshot, meta: BookMetadata, taken: (String) -> Boolean = { false }): KoboBook? {
            val title = matchKey(meta.title)
            if (title.isEmpty()) return null
            val authors = meta.authors.map(::matchKey).filter { it.isNotEmpty() }
            return snapshot.books.firstOrNull { b ->
                b.isSideloaded && b.relativePath != null && !taken(b.relativePath) && matchKey(b.title) == title && run {
                    val onKobo = matchKey(b.author.orEmpty())
                    if (authors.isEmpty()) onKobo.isEmpty() else authors.all { onKobo.contains(it) }
                }
            }
        }

        /** Lower case, accents and punctuation gone: "Brontë, Charlotte" and "Charlotte Bronte" share their words. */
        private fun matchKey(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
    }

    /** Removes the cover pictures kept under [imageIds], so the Kobo makes new ones (and the app forgets its copies). */
    private fun clearPictures(session: KoboSession, imageIds: Set<String>) {
        for (imageId in imageIds) {
            session.covers.forget(imageId)
            runCatching {
                val dir = session.tree.resolve("${KoboImages.IMAGES_DIR}/${KoboImages.folderFor(imageId)}") ?: return@runCatching
                session.tree.children(dir.docId)
                    .filter { it.name.startsWith("$imageId - ") && it.name.endsWith(".parsed") }
                    .forEach { session.tree.delete(it.docId) }
            }.onFailure { AppLog.w("Send", "Couldn't clear the Kobo's old cover pictures $imageId", it) }
        }
    }
}
