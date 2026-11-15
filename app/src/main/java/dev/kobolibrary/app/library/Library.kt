package dev.kobolibrary.app.library

import dev.kobolibrary.app.tr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.kobolibrary.app.convert.AndroidPdfPages
import dev.kobolibrary.app.convert.BookFormats
import dev.kobolibrary.app.convert.ConversionException
import dev.kobolibrary.app.convert.SourceFormat
import dev.kobolibrary.app.epub.BookMetadata
import dev.kobolibrary.app.epub.BookAssembly
import dev.kobolibrary.app.epub.EpubEditor
import dev.kobolibrary.app.epub.EpubException
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.EpubText
import dev.kobolibrary.app.epub.Names
import dev.kobolibrary.app.kobo.KoboBook
import dev.kobolibrary.app.kobo.KoboSession
import dev.kobolibrary.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * The books kept on the tablet: one folder per book (`book.epub` and a `cover.jpg` picture),
 * listed in a small database. Every change to a book is written into its EPUB as well.
 */
class Library(context: Context, val root: File = File(context.filesDir, "library"), dbName: String? = "library.db") {

    private val db = LibraryDb(context, dbName)
    private val incoming = File(root, ".incoming")
    /** Files being added that the library already has, kept until it's decided what to do with them. */
    private val held = File(root, ".held")
    private val lock = Mutex()

    private val _books = MutableStateFlow<List<LibraryBook>>(emptyList())
    val books: StateFlow<List<LibraryBook>> = _books.asStateFlow()

    private val _koboCollections = MutableStateFlow<List<String>>(emptyList())
    /** Collection names last seen on the Kobo, so they can be offered even when it isn't plugged in. */
    val koboCollections: StateFlow<List<String>> = _koboCollections.asStateFlow()

    @Volatile
    var loaded = false
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            root.mkdirs()
            incoming.deleteRecursively() // leftovers from an interrupted import
            held.deleteRecursively() // and duplicates nobody decided about
            val all = db.all()
            // A book whose file has gone missing can't be used; leave it out (and say so).
            val (present, missing) = all.partition { epubFile(it.id).exists() }
            if (missing.isNotEmpty()) AppLog.w("Library", "${missing.size} library entries had no file and were hidden.")
            _books.value = present
            _koboCollections.value = db.koboCollections()
            loaded = true
            AppLog.i("Library", "Loaded ${present.size} books.")
        }
    }

    fun bookDir(id: String) = File(root, id)
    fun epubFile(id: String) = File(bookDir(id), "book.epub")
    fun coverFile(id: String) = File(bookDir(id), "cover.jpg")

    /** The file a book was added as, when that wasn't an EPUB (a PDF, a MOBI...): kept with it, as it was. */
    fun sourceFile(id: String): File? = bookDir(id).listFiles()?.firstOrNull { it.name.startsWith("$SOURCE.") }
    fun book(id: String): LibraryBook? = _books.value.firstOrNull { it.id == id }
    fun byKoboPath(path: String): LibraryBook? = _books.value.firstOrNull { it.koboPath.equals(path, ignoreCase = true) }

    /** Every collection name the app knows: from the Kobo and from books in the library. */
    fun allCollections(): List<String> =
        (_koboCollections.value + _books.value.flatMap { it.collections }).distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    suspend fun rememberKoboCollections(names: List<String>) = withContext(Dispatchers.IO) {
        db.replaceKoboCollections(names)
        _koboCollections.value = names.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Adds one book. The file is copied in, checked, turned back into a plain EPUB if it's a kepub,
     * and skipped if the library already has it (or, with [hold], kept aside so it can replace that
     * book or be added as well: see [ImportOutcome.AlreadyThere.held]).
     */
    suspend fun import(
        name: String,
        open: () -> InputStream,
        source: BookSource,
        koboPath: String? = null,
        collections: List<String> = emptyList(),
        fallback: KoboFallback? = null,
        /** A book made here on purpose (new, merged, split, a copy): never mistaken for one already there. */
        made: Boolean = false,
        hold: Boolean = false,
        /** The file this EPUB was made from (see [sourceFile]) and what kind it is, to keep with it. */
        attach: Pair<File, String>? = null,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        if (koboPath != null) byKoboPath(koboPath)?.let { return@withContext ImportOutcome.AlreadyThere(name, it) }
        incoming.mkdirs()
        val temp = File(incoming, UUID.randomUUID().toString() + ".epub")
        val raw = File(incoming, UUID.randomUUID().toString() + ".in")
        // Added as another kind of file: the file itself, kept with the book, and what kind it is.
        var origin: Pair<File, String>? = attach
        try {
            try {
                open().use { input -> raw.outputStream().use { input.copyTo(it, 256 * 1024) } }
            } catch (e: IOException) {
                // A big comic can fill the device.
                if (incoming.usableSpace < 64L * 1024 * 1024) return@withContext ImportOutcome.Failed(name, tr("There isn't enough space on this device for it."))
                return@withContext ImportOutcome.Failed(name, tr("Couldn't read the file: {0}", e.message))
            }
            if (raw.length() == 0L) return@withContext ImportOutcome.Failed(name, tr("The file is empty."))
            val format = if (attach != null) SourceFormat.EPUB else runCatching { BookFormats.detect(raw, name) }.getOrNull()
                ?: return@withContext ImportOutcome.Failed(name, tr("KoLib can't open this kind of file."))
            if (format == SourceFormat.EPUB) {
                if (!raw.renameTo(temp)) raw.copyTo(temp, overwrite = true)
            } else {
                // Made into an EPUB, so it reads, edits and goes to the Kobo like any other book.
                val made0 = System.currentTimeMillis()
                val result = try {
                    val pages = if (format == SourceFormat.PDF) runCatching { AndroidPdfPages(raw) }.getOrNull() else null
                    try {
                        BookFormats.toEpub(format, raw, name, temp, pages)
                    } finally {
                        pages?.close()
                    }
                } catch (e: ConversionException) {
                    return@withContext ImportOutcome.Failed(name, e.message ?: tr("It couldn't be made into a book."))
                } catch (e: OutOfMemoryError) {
                    return@withContext ImportOutcome.Failed(name, tr("It's too big to convert on this device."))
                } catch (e: Exception) {
                    AppLog.e("Library", "Converting '$name' failed", e)
                    return@withContext ImportOutcome.Failed(name, tr("It couldn't be made into a book ({0}).", e.message ?: e.javaClass.simpleName))
                }
                origin = raw to (result.kind ?: format.name)
                AppLog.i("Library", "Made '$name' (${format.label}) into an EPUB in ${System.currentTimeMillis() - made0} ms.")
            }

            try {
                EpubFile.open(temp).close()
            } catch (e: EpubException) {
                return@withContext ImportOutcome.Failed(name, e.message ?: tr("This isn't an EPUB file."))
            }
            val stripped = try {
                EpubEditor.stripKepub(temp)
            } catch (e: Exception) {
                AppLog.w("Library", "Couldn't turn '$name' back into a plain EPUB; keeping it as it is", e)
                false
            }

            var coverBytes: ByteArray? = null
            var words: Int? = null
            val fromFile = try {
                EpubFile.open(temp).use { epub ->
                    coverBytes = epub.findCover()?.let { epub.readBytes(it.path) }
                    words = runCatching { EpubText.countWords(epub) }.getOrNull()
                    epub.opf.readMetadata()
                }
            } catch (e: Exception) {
                return@withContext ImportOutcome.Failed(name, tr("The book's details couldn't be read: {0}", e.message))
            }
            val meta = withFallbacks(fromFile, fallback, name)

            lock.withLock {
                if (!made) findDuplicate(meta, koboPath)?.let { existing ->
                    val kept = if (hold) keepAside(temp) else null
                    // The file it was made from goes aside with it.
                    if (kept != null) origin?.let { (file, kind) -> runCatching { file.copyTo(File(held, "${kept.name}.$SOURCE.$kind.${extensionOf(name, kind)}"), overwrite = true) } }
                    return@withContext ImportOutcome.AlreadyThere(name, existing, kept, meta)
                }
                val id = UUID.randomUUID().toString()
                val dir = bookDir(id).apply { mkdirs() }
                val target = File(dir, "book.epub")
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                val hasCover = coverBytes?.let { writeThumbnail(it, coverFile(id)) } ?: false
                origin?.let { (file, kind) ->
                    // The file brought in for this is moved into place, not copied again (a comic can be gigabytes).
                    val kept = File(dir, "$SOURCE.${extensionOf(name, kind)}")
                    if (file != raw || !file.renameTo(kept)) file.copyTo(kept, overwrite = true)
                }
                val now = System.currentTimeMillis()
                val book = LibraryBook(
                    id = id,
                    meta = meta,
                    collections = collections.distinctBy { it.lowercase() },
                    fileSize = target.length(),
                    hasCover = hasCover,
                    coverVersion = 1,
                    addedAt = now,
                    modifiedAt = now,
                    source = source,
                    originalName = name,
                    koboPath = koboPath,
                    words = words,
                    // Taken from the Kobo, so both sides agree on them to start with.
                    koboCollections = if (koboPath != null) collections.distinctBy { it.lowercase() } else null,
                    sourceFormat = origin?.second,
                )
                db.upsert(book)
                _books.update { it + book }
                AppLog.i("Library", "Added '${meta.title}'${if (stripped) " (converted from kepub)" else ""}${if (!hasCover) " (no cover)" else ""}.")
                ImportOutcome.Added(name, book)
            }
        } finally {
            temp.delete()
            raw.delete()
        }
    }

    /** A file's own ending ("azw3", "pdf"), or the usual one for [kind]. */
    private fun extensionOf(name: String, kind: String): String {
        val ext = name.lowercase().substringAfterLast('.', "").filter { it.isLetterOrDigit() }.take(8)
        return ext.ifEmpty { if (kind == BookFormats.PDF_SCAN || kind == BookFormats.PDF_ONLY) "pdf" else SourceFormat.entries.firstOrNull { it.name == kind }?.extension ?: "bin" }
    }

    /** The file a held book was made from, kept aside with it, and what kind it is. */
    fun heldSource(file: File): Pair<File, String>? =
        file.parentFile?.listFiles()?.firstOrNull { it.name.startsWith("${file.name}.$SOURCE.") }?.let { f ->
            f to f.name.removePrefix("${file.name}.$SOURCE.").substringBefore('.')
        }

    /** Lets go of a book that was held, and the file it was made from. */
    fun discardHeld(file: File) {
        heldSource(file)?.first?.delete()
        file.delete()
    }

    /**
     * Copies one book from the connected Kobo, with the collections it's in there. What the Kobo knows
     * about the book (title, author, series) only fills gaps in the file's own details.
     */
    // ---- books made here ----------------------------------------------------------------------------

    /** Takes in a book the app has just written to [file], and returns it. */
    private suspend fun takeMade(file: File, collections: List<String>): LibraryBook {
        try {
            val name = withContext(Dispatchers.IO) { runCatching { EpubFile.open(file).use { it.opf.readMetadata().title } }.getOrNull() }
                ?.ifBlank { null }?.let { "$it.epub" } ?: "book.epub"
            return when (val r = import(name, { file.inputStream() }, BookSource.FILE, collections = collections, made = true)) {
                is ImportOutcome.Added -> r.book
                is ImportOutcome.AlreadyThere -> r.existing
                is ImportOutcome.Failed -> throw IOException(r.reason)
            }
        } finally {
            file.delete()
        }
    }

    private fun scratch(name: String): File {
        incoming.mkdirs()
        return File(incoming, "${UUID.randomUUID()}-$name.epub")
    }

    /** A new, empty book with these details and a first chapter, to write in the editor. */
    suspend fun createBook(meta: BookMetadata, collections: List<String> = emptyList()): LibraryBook {
        val file = scratch("new")
        withContext(Dispatchers.IO) { BookAssembly.create(file, meta) }
        return takeMade(file, collections).also { AppLog.i("Library", "Made a new book, '${it.title}'.") }
    }

    /** One book made of [ids], in that order, with the cover of the book at [coverFrom]. The books themselves stay as they are. */
    suspend fun mergeBooks(ids: List<String>, meta: BookMetadata, coverFrom: Int?, titles: List<String>? = null): LibraryBook {
        val books = ids.map { book(it) ?: throw IOException(tr("One of the selected books is no longer in the library.")) }
        val file = scratch("merged")
        withContext(Dispatchers.IO) { BookAssembly.merge(file, books.map { epubFile(it.id) }, meta, coverFrom, titles) }
        val collections = books.flatMap { it.collections }.distinctBy { it.lowercase() }
        return takeMade(file, collections).also { AppLog.i("Library", "Put ${books.size} books together as '${it.title}'.") }
    }

    /** The pages of a book, for choosing where to split it. */
    suspend fun pagesOf(id: String): List<BookAssembly.Page> = withContext(Dispatchers.IO) { BookAssembly.pages(epubFile(id)) }

    /** Books cut from [id] at [cuts] (page indexes where each new one starts), one for each of [metas]. The book itself stays as it is. */
    suspend fun splitBook(id: String, cuts: List<Int>, metas: List<BookMetadata>): List<LibraryBook> {
        val source = book(id) ?: throw IOException(tr("That book is no longer in the library."))
        val files = metas.indices.map { scratch("part-${it + 1}") }
        try {
            withContext(Dispatchers.IO) { BookAssembly.split(epubFile(id), cuts, metas, files) }
            return files.map { takeMade(it, source.collections) }.also { AppLog.i("Library", "Split '${source.title}' into ${it.size} books.") }
        } finally {
            files.forEach { it.delete() }
        }
    }

    /** A copy of [id] in the library, called "Title (copy)", in the same collections. The book itself stays as it is. */
    suspend fun duplicate(id: String): LibraryBook {
        val source = book(id) ?: throw IOException(tr("That book is no longer in the library."))
        val file = scratch("copy")
        val title = copyTitle(source.meta.title)
        withContext(Dispatchers.IO) {
            epubFile(id).copyTo(file, overwrite = true)
            EpubEditor.writeMetadata(file, source.meta.copy(title = title, titleSort = source.meta.titleSort?.let { "$it (copy)" }))
        }
        var copy = takeMade(file, source.collections)
        sourceFile(id)?.let { f ->
            withContext(Dispatchers.IO) { f.copyTo(File(bookDir(copy.id), f.name), overwrite = true) }
            copy = updateBook(copy.copy(sourceFormat = source.sourceFormat))
        }
        AppLog.i("Library", "Made a copy of '${source.title}', '${copy.title}'.")
        // The copy's cover is the same picture, to be treated the same way.
        return if (source.coverAsIs) setCoverAsIs(copy.id, true) ?: copy else copy
    }

    /** Saves [b] as it now is. */
    private suspend fun updateBook(b: LibraryBook): LibraryBook = withContext(Dispatchers.IO) {
        db.upsert(b)
        _books.update { list -> list.map { if (it.id == b.id) b else it } }
        b
    }

    /** "Title (copy)", or "Title (copy 2)" and so on when the library has that already. */
    internal fun copyTitle(title: String): String {
        val base = title.replace(Regex("""\s*\(copy(?: \d+)?\)\s*$"""), "").ifBlank { title }
        val taken = _books.value.map { Names.normalizeSpace(it.meta.title).lowercase() }.toSet()
        var n = 1
        while (true) {
            val candidate = if (n == 1) tr("{0} (copy)", base) else tr("{0} (copy {1})", base, n)
            if (Names.normalizeSpace(candidate).lowercase() !in taken) return candidate
            n++
        }
    }

    /** Moves a file being added out of the way, until it's decided whether it replaces a book or is added as well. */
    private fun keepAside(temp: File): File? = runCatching {
        held.mkdirs()
        val target = File(held, UUID.randomUUID().toString() + ".epub")
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target
    }.onFailure { AppLog.w("Library", "Couldn't keep a file being added aside", it) }.getOrNull()

    /**
     * Puts [file] (a book being added: [ImportOutcome.AlreadyThere.held]) in place of [id]'s file. The
     * new file's details, cover and text are taken; the book keeps its collections, its place on the
     * Kobo (so it shows as changed since it was sent) and where it was left in the reader. What was kept
     * of the old file, to put back its first cover or text, goes.
     */
    suspend fun replaceWith(id: String, file: File, name: String): LibraryBook = withContext(Dispatchers.IO) {
        lock.withLock {
            book(id) ?: throw IOException(tr("That book is no longer in the library."))
            try {
                EpubFile.open(file).close()
            } catch (e: EpubException) {
                throw IOException(e.message ?: tr("The new file isn't an EPUB."))
            }
            val target = epubFile(id)
            val tmp = File(target.path + ".replacing")
            try {
                file.copyTo(tmp, overwrite = true)
                if (!tmp.renameTo(target)) {
                    target.delete()
                    if (!tmp.renameTo(target)) throw IOException(tr("Couldn't replace the file."))
                }
            } finally {
                tmp.delete()
            }
            originalTextFile(id).delete()
            bookDir(id).listFiles()?.filter { it.name.startsWith(ORIGINAL_COVER) }?.forEach { it.delete() }
            // The file it was made from now: the new one's, if it had one.
            sourceFile(id)?.delete()
            val newSource = heldSource(file)
            newSource?.let { (f, kind) -> f.copyTo(File(bookDir(id), "$SOURCE.${extensionOf(name, kind)}"), overwrite = true) }
            refreshFromFile(id, "Replaced the file of") { it.copy(originalName = name, source = BookSource.FILE, coverAsIs = false, sourceFormat = newSource?.second) }
        }
    }

    /** Adds [file] (a book being added: [ImportOutcome.AlreadyThere.held]) as a book of its own, beside the one it matched. */
    suspend fun addAnyway(file: File, name: String): LibraryBook =
        when (val r = import(name, { file.inputStream() }, BookSource.FILE, made = true, attach = heldSource(file))) {
            is ImportOutcome.Added -> r.book.also { AppLog.i("Library", "Added '${it.title}' again, as a book of its own.") }
            is ImportOutcome.AlreadyThere -> r.existing
            is ImportOutcome.Failed -> throw IOException(r.reason)
        }

    suspend fun importFromKobo(session: KoboSession, book: KoboBook): ImportOutcome {
        val path = book.relativePath ?: return ImportOutcome.Failed(book.title, tr("The Kobo's database doesn't list where this book's file is."))
        if (!book.isSideloaded) return ImportOutcome.Failed(book.title, tr("Kobo Store books can't be copied from the Kobo."))
        byKoboPath(path)?.let { return ImportOutcome.AlreadyThere(book.title, it) }
        val entry = withContext(Dispatchers.IO) { session.tree.resolve(path) }
            ?: return ImportOutcome.Failed(book.title, tr("The file isn't on the Kobo any more ({0}).", path))
        return import(
            name = path.substringAfterLast('/'),
            open = { session.tree.openInput(entry.docId) },
            source = BookSource.KOBO,
            koboPath = path,
            collections = session.snapshot.collectionsByBook[book.contentId].orEmpty(),
            fallback = KoboFallback(
                title = book.title,
                authors = book.author?.split(Regex("\\s*(?:,|&)\\s*"))?.filter { it.isNotBlank() }.orEmpty(),
                series = book.series,
                seriesIndex = book.seriesNumber?.toDoubleOrNull(),
            ),
        )
    }

    private fun withFallbacks(meta: BookMetadata, fallback: KoboFallback?, fileName: String): BookMetadata {
        var m = meta
        if (m.title.isBlank()) {
            m = m.copy(title = fallback?.title?.takeIf { it.isNotBlank() } ?: stripExtension(fileName))
        }
        if (m.authors.isEmpty() && fallback != null && fallback.authors.isNotEmpty()) m = m.copy(authors = fallback.authors)
        if (m.series.isNullOrBlank() && fallback != null && !fallback.series.isNullOrBlank()) {
            m = m.copy(series = fallback.series, seriesIndex = fallback.seriesIndex)
        }
        return m
    }

    private fun findDuplicate(meta: BookMetadata, koboPath: String?): LibraryBook? {
        val books = _books.value
        if (koboPath != null) books.firstOrNull { it.koboPath.equals(koboPath, true) }?.let { return it }
        val title = Names.normalizeSpace(meta.title).lowercase()
        val author = meta.authors.firstOrNull()?.lowercase()
        return books.firstOrNull { b ->
            val sameTitle = Names.normalizeSpace(b.meta.title).lowercase() == title
            val sameId = meta.identifier != null && meta.identifier.length >= 8 && b.meta.identifier == meta.identifier
            (sameId && sameTitle) ||
                (sameTitle && b.meta.authors.firstOrNull()?.lowercase() == author && b.meta.seriesIndex == meta.seriesIndex && b.meta.series.equals(meta.series, true))
        }
    }

    /** Saves new details into the EPUB and the library. */
    suspend fun updateDetails(id: String, meta: BookMetadata, collections: List<String>): LibraryBook = withContext(Dispatchers.IO) {
        lock.withLock {
            val book = book(id) ?: throw IOException(tr("That book is no longer in the library."))
            val cleanCollections = collections.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            if (meta == book.meta) {
                // Only the collections changed. They aren't kept in the book file, so the file (and
                // the copy on the Kobo) stays as it is; the Kobo's collections are updated separately.
                val updated = book.copy(collections = cleanCollections)
                db.upsert(updated)
                _books.update { list -> list.map { if (it.id == id) updated else it } }
                AppLog.i("Library", "Saved the collections of '${updated.title}'.")
                return@withContext updated
            }
            val file = epubFile(id)
            EpubEditor.writeMetadata(file, meta)
            // Read back what's now in the file, so the library always matches it.
            val saved = EpubFile.open(file).use { it.opf.readMetadata() }
            val updated = book.copy(
                meta = saved,
                collections = cleanCollections,
                fileSize = file.length(),
                modifiedAt = System.currentTimeMillis(),
            )
            db.upsert(updated)
            _books.update { list -> list.map { if (it.id == id) updated else it } }
            AppLog.i("Library", "Saved details of '${updated.title}'.")
            updated
        }
    }

    /**
     * Makes [image] the book's cover, in the EPUB and the library. [asIs]: the picture is to be left
     * unchanged, never fitted to the Kobo's screen. A picture that is the book's cover already
     * isn't written again; only how it's to be treated changes.
     */
    suspend fun updateCover(id: String, image: ByteArray, mediaType: String, width: Int, height: Int, asIs: Boolean = false): LibraryBook {
        val current = withContext(Dispatchers.IO) { runCatching { originalCover(id) }.getOrNull() }
        if (current != null && current.contentEquals(image)) {
            return setCoverAsIs(id, asIs, edit = true) ?: throw IOException(tr("That book is no longer in the library."))
        }
        return replaceCoverWith(id, image, mediaType, width, height, if (asIs) "Saved an unchanged cover for" else "Saved a new cover for", asIs)
    }

    /**
     * Marks [id]'s cover to be left unchanged, or not. With [edit], it counts as a change to the book: a
     * copy on the Kobo was sent with its cover treated the other way, so it shows as changed since.
     */
    suspend fun setCoverAsIs(id: String, asIs: Boolean, edit: Boolean = false): LibraryBook? = withContext(Dispatchers.IO) {
        lock.withLock {
            val book = book(id) ?: return@withLock null
            if (book.coverAsIs == asIs) return@withLock book
            val updated = book.copy(coverAsIs = asIs, modifiedAt = if (edit) System.currentTimeMillis() else book.modifiedAt)
            db.upsert(updated)
            _books.update { list -> list.map { if (it.id == id) updated else it } }
            AppLog.i("Library", if (asIs) "The cover of '${book.title}' will be sent unchanged." else "The cover of '${book.title}' will be fitted to the Kobo's screen.")
            updated
        }
    }

    /** Puts the book's first cover back, exactly as it was. */
    suspend fun restoreOriginalCover(id: String): LibraryBook {
        val (bytes, type) = withContext(Dispatchers.IO) { readSavedOriginal(id) } ?: throw IOException(tr("This book's original cover wasn't saved, so it can't be restored."))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // "Restore it unchanged": it stays unchanged on the Kobo too.
        return replaceCoverWith(id, bytes, type, bounds.outWidth.coerceAtLeast(1), bounds.outHeight.coerceAtLeast(1), "Restored the original cover of", asIs = true)
    }

    /** The cover the book came with, if it has been changed since (kept the first time it was). */
    suspend fun savedOriginalCover(id: String): ByteArray? = withContext(Dispatchers.IO) { readSavedOriginal(id)?.first }

    private fun originalCoverFile(id: String): File? =
        bookDir(id).listFiles()?.firstOrNull { it.name.startsWith(ORIGINAL_COVER) && it.length() > 0 }

    private fun readSavedOriginal(id: String): Pair<ByteArray, String>? {
        val f = originalCoverFile(id) ?: return null
        return f.readBytes() to EpubFile.mediaTypeFor(f.name)
    }

    /** Keeps the book's current cover, the first time it's about to be replaced. */
    private fun keepOriginalCover(id: String) {
        if (originalCoverFile(id) != null) return
        runCatching {
            EpubFile.open(epubFile(id)).use { epub ->
                val cover = epub.findCover() ?: return
                val bytes = epub.readBytes(cover.path) ?: return
                val ext = cover.path.substringAfterLast('.', "jpg").lowercase().takeIf { it.length in 2..4 } ?: "jpg"
                File(bookDir(id), "$ORIGINAL_COVER.$ext").writeBytes(bytes)
            }
        }.onFailure { AppLog.w("Library", "Couldn't keep the original cover of $id", it) }
    }

    private suspend fun replaceCoverWith(id: String, image: ByteArray, mediaType: String, width: Int, height: Int, what: String, asIs: Boolean): LibraryBook = withContext(Dispatchers.IO) {
        lock.withLock {
            val book = book(id) ?: throw IOException(tr("That book is no longer in the library."))
            val file = epubFile(id)
            keepOriginalCover(id)
            EpubEditor.replaceCover(file, image, mediaType, width, height)
            val hasCover = writeThumbnail(image, coverFile(id))
            val updated = book.copy(
                hasCover = hasCover,
                coverVersion = book.coverVersion + 1,
                fileSize = file.length(),
                modifiedAt = System.currentTimeMillis(),
                coverAsIs = asIs,
            )
            db.upsert(updated)
            _books.update { list -> list.map { if (it.id == id) updated else it } }
            AppLog.i("Library", "$what '${updated.title}'.")
            updated
        }
    }

    /** The book as it was before its text was first edited (kept for "Revert to original"). */
    fun originalTextFile(id: String) = File(bookDir(id), ORIGINAL_TEXT)

    fun hasOriginalText(id: String): Boolean = originalTextFile(id).exists()

    /** After the book's text was edited and saved: its details, cover picture, length and size are read again. */
    suspend fun afterTextEdit(id: String): LibraryBook = withContext(Dispatchers.IO) {
        lock.withLock { refreshFromFile(id, "Saved the text of") }
    }

    private fun refreshFromFile(id: String, what: String, also: (LibraryBook) -> LibraryBook = { it }): LibraryBook {
        val book = book(id) ?: throw IOException(tr("That book is no longer in the library."))
        val file = epubFile(id)
        var cover: ByteArray? = null
        var words: Int? = null
        val meta = EpubFile.open(file).use { epub ->
            cover = runCatching { epub.findCover()?.let { epub.readBytes(it.path) } }.getOrNull()
            words = runCatching { EpubText.countWords(epub) }.getOrNull()
            epub.opf.readMetadata()
        }
        val hasCover = cover?.let { writeThumbnail(it, coverFile(id)) } ?: false
        val updated = also(
            book.copy(
                meta = meta,
                fileSize = file.length(),
                words = words ?: book.words,
                hasCover = hasCover,
                coverVersion = book.coverVersion + 1,
                modifiedAt = System.currentTimeMillis(),
            )
        )
        db.upsert(updated)
        _books.update { list -> list.map { if (it.id == id) updated else it } }
        AppLog.i("Library", "$what '${updated.title}'.")
        return updated
    }

    /**
     * Puts the book's text and chapters back as they were before they were first edited. Its details and
     * cover stay as they are now.
     */
    suspend fun revertText(id: String): LibraryBook = withContext(Dispatchers.IO) {
        lock.withLock {
            val book = book(id) ?: throw IOException(tr("That book is no longer in the library."))
            val original = originalTextFile(id)
            if (!original.exists()) throw IOException(tr("This book's text hasn't been changed."))
            val file = epubFile(id)
            val coverNow = runCatching {
                EpubFile.open(file).use { e -> e.findCover()?.let { c -> e.readBytes(c.path)?.let { it to c.mediaType } } }
            }.getOrNull()
            val tmp = File(file.path + ".reverting")
            try {
                original.copyTo(tmp, overwrite = true)
                EpubEditor.writeMetadata(tmp, book.meta)
                val coverThen = runCatching { EpubFile.open(tmp).use { e -> e.findCover()?.let { c -> e.readBytes(c.path) } } }.getOrNull()
                if (coverNow != null && (coverThen == null || !coverThen.contentEquals(coverNow.first))) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(coverNow.first, 0, coverNow.first.size, bounds)
                    EpubEditor.replaceCover(tmp, coverNow.first, coverNow.second, bounds.outWidth.coerceAtLeast(1), bounds.outHeight.coerceAtLeast(1))
                }
                EpubFile.open(tmp).close()
                if (!tmp.renameTo(file)) {
                    file.delete()
                    if (!tmp.renameTo(file)) throw IOException(tr("Couldn't restore the original"))
                }
            } finally {
                tmp.delete()
            }
            original.delete()
            refreshFromFile(id, "Restored the original text of")
        }
    }

    /** The cover picture inside the book itself, full size, for re-cropping. */
    suspend fun originalCover(id: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { EpubFile.open(epubFile(id)).use { epub -> epub.findCover()?.let { epub.readBytes(it.path) } } }.getOrNull()
    }

    suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            db.delete(ids)
            ids.forEach { bookDir(it).deleteRecursively() }
            _books.update { list -> list.filter { it.id !in ids } }
            AppLog.i("Library", "Removed ${ids.size} book(s) from the library.")
        }
    }

    /**
     * Records that [id] is now on the Kobo at [path]. [afresh]: the Kobo will take the file in as a new
     * book, forgetting the collections it had it in.
     */
    suspend fun markSent(id: String, path: String, afresh: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock {
            val book = book(id) ?: return@withLock
            // In a new place (another format, or new chapters) it's a new book to the Kobo, in no collections there yet.
            val moved = !path.equals(book.koboPath, ignoreCase = true)
            val updated = book.copy(
                koboPath = path,
                sentAt = System.currentTimeMillis(),
                koboCollections = if (moved || afresh) null else book.koboCollections,
            )
            db.upsert(updated)
            _books.update { list -> list.map { if (it.id == id) updated else it } }
        }
    }

    /**
     * The Kobo took these books in as new ones, so what it and the library last agreed about their
     * collections no longer holds: next time they're compared afresh, and nothing is taken out of either.
     */
    suspend fun forgetKoboAgreement(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        lock.withLock {
            val changed = _books.value.filter { it.id in ids && it.koboCollections != null }.map { it.copy(koboCollections = null) }
            changed.forEach { db.upsert(it) }
            val byId = changed.associateBy { it.id }
            _books.update { list -> list.map { byId[it.id] ?: it } }
        }
    }

    /** Books deleted from the Kobo: forget where they were. */
    suspend fun forgetKoboPaths(paths: Collection<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val lower = paths.map { it.lowercase() }.toSet()
        lock.withLock {
            val changed = _books.value.filter { it.koboPath?.lowercase() in lower }.map { it.copy(koboPath = null, sentAt = null, koboCollections = null) }
            changed.forEach { db.upsert(it) }
            val byId = changed.associateBy { it.id }
            _books.update { list -> list.map { byId[it.id] ?: it } }
        }
    }

    /**
     * Records the result of comparing collections with the Kobo: each book's new collections (null to
     * keep them) and what both sides now agree on. Not an edit of the book, so it isn't marked changed.
     */
    suspend fun applyKoboAgreement(changes: List<Triple<String, List<String>?, List<String>>>) = withContext(Dispatchers.IO) {
        if (changes.isEmpty()) return@withContext
        lock.withLock {
            val byId = HashMap<String, LibraryBook>()
            for ((id, collections, agreed) in changes) {
                val book = byId[id] ?: book(id) ?: continue
                val updated = book.copy(collections = collections ?: book.collections, koboCollections = agreed)
                db.upsert(updated)
                byId[id] = updated
            }
            _books.update { list -> list.map { byId[it.id] ?: it } }
        }
    }

    /** Counts the words of books added before page counts existed. Runs in the background. */
    suspend fun fillMissingWordCounts(stillWanted: () -> Boolean = { true }) = withContext(Dispatchers.IO) {
        for (b in _books.value.filter { it.words == null }) {
            if (!stillWanted()) break
            val words = runCatching { EpubFile.open(epubFile(b.id)).use { EpubText.countWords(it) } }.getOrNull() ?: continue
            lock.withLock {
                val current = book(b.id) ?: return@withLock
                val updated = current.copy(words = words)
                db.upsert(updated)
                _books.update { list -> list.map { if (it.id == b.id) updated else it } }
            }
        }
    }

    fun freeSpaceBytes(): Long = root.usableSpace

    companion object {
        private const val ORIGINAL_COVER = "original-cover"
        private const val ORIGINAL_TEXT = "original.epub"
        /** The start of the name of the file a book was made from: "source.pdf". */
        private const val SOURCE = "source"
        private const val THUMB_WIDTH = 600

        fun stripExtension(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".kepub.epub") -> name.dropLast(".kepub.epub".length)
                lower.contains('.') -> name.substringBeforeLast('.')
                else -> name
            }
        }

        /** Saves a smaller copy of the cover for the library screens. Returns false if it can't be decoded. */
        fun writeThumbnail(image: ByteArray, target: File): Boolean {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                target.delete()
                return false
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return false.also { target.delete() }
            val scaled = if (decoded.width > THUMB_WIDTH) {
                Bitmap.createScaledBitmap(decoded, THUMB_WIDTH, (decoded.height * THUMB_WIDTH.toFloat() / decoded.width).toInt().coerceAtLeast(1), true)
            } else decoded
            target.parentFile?.mkdirs()
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            return true
        }
    }
}
