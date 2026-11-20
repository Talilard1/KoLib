package dev.kolib.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kolib.app.reader.ReaderStore
import dev.kolib.app.epub.BookMetadata
import dev.kolib.app.kobo.KoboBook
import dev.kolib.app.kobo.KoboPlaces
import dev.kolib.app.kobo.CoverFitting
import dev.kolib.app.kobo.KoboPaths
import dev.kolib.app.kobo.KoboSender
import dev.kolib.app.kobo.PendingKoboRecords
import dev.kolib.app.kobo.KoboSession
import dev.kolib.app.kobo.KoboSnapshot
import dev.kolib.app.kobo.SendFormat
import dev.kolib.app.kobo.SendOutcome
import dev.kolib.app.ui.cover.SavedCover
import dev.kolib.app.library.BookSource
import dev.kolib.app.library.ComicSeries
import dev.kolib.app.library.ImportOutcome
import dev.kolib.app.library.Library
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.log.AppLog
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.textedit.TextEditState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileNotFoundException

enum class JobKind { ADD_FILES, ADD_FROM_KOBO, SEND }

/** Progress and result of adding books to the library, or sending them to the Kobo. */
data class JobProgress(
    val kind: JobKind,
    val title: String,
    val total: Int,
    val done: Int = 0,
    val current: String? = null,
    /** How far through the current book (sending big files). */
    val currentFraction: Float = 0f,
    val succeeded: Int = 0,
    /** Adding: already in the library. Sending: replaced a copy already on the Kobo. */
    val alreadyThere: Int = 0,
    val failures: List<Pair<String, String>> = emptyList(),
    /** Sending: books that couldn't be made into kepubs, so they can be sent as EPUB instead. */
    val conversionFailedIds: List<String> = emptyList(),
    /** Sending: books with a new cover that the Kobo will show once it's restarted. */
    val coversAfterRestart: List<String> = emptyList(),
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val fromKobo: Boolean get() = kind == JobKind.ADD_FROM_KOBO
    val fraction: Float get() = if (total == 0) 0f else ((done + currentFraction.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
}

/** A file being added that the library already has, waiting to be told what to do with it. */
data class HeldDuplicate(
    val key: String,
    /** The file's name, as it was added. */
    val name: String,
    val file: java.io.File,
    val size: Long,
    /** The title and authors in the file. */
    val title: String,
    val authors: String,
    /** The library book it matches. */
    val existingId: String,
)

enum class DuplicateChoice(val label: String) {
    REPLACE(tr("Replace")),
    KEEP_BOTH(tr("Keep both")),
    SKIP(tr("Skip")),
}

/** One step's result, whatever the job. */
private sealed interface Step {
    data object Ok : Step
    data object Already : Step
    data class Failed(val name: String, val reason: String, val conversionFailedId: String? = null) : Step
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val koboApp = app as KoLibApp
    val library: Library = koboApp.library
    val covers = koboApp.libraryCovers
    val books: StateFlow<List<LibraryBook>> = library.books
    val koboCollections: StateFlow<List<String>> = library.koboCollections
    private val sender = KoboSender(app)
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val _job = MutableStateFlow<JobProgress?>(null)
    val job: StateFlow<JobProgress?> = _job.asStateFlow()
    private var runningJob: Job? = null

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** The format chosen last time a book was sent. */
    var sendFormat: SendFormat
        get() = runCatching { SendFormat.valueOf(prefs.getString("send_format", null) ?: "") }.getOrDefault(SendFormat.KEPUB)
        set(value) = prefs.edit().putString("send_format", value.name).apply()

    /** Covers always go as they are: the Kobo fits a cover to its screen itself, as its owner has it set. */
    var coverFitting: CoverFitting
        get() = CoverFitting.AS_IS
        @Suppress("UNUSED_PARAMETER") set(value) {}

    /** Whether books added as PDFs go to the Kobo as the PDF, as chosen last time. */
    var sendPdfs: Boolean
        get() = prefs.getBoolean("send_pdfs", false)
        set(value) = prefs.edit().putBoolean("send_pdfs", value).apply()

    /** Whether sending books syncs their reading with the Kobo too (place, bookmarks, highlights and notes), as chosen last time. */
    var syncReadingOnSend: Boolean
        get() = prefs.getBoolean("send_sync_reading", false)
        set(value) = prefs.edit().putBoolean("send_sync_reading", value).apply()

    /** Books waiting to be sent by the send that's running; more can join it while it runs. */
    private class SendRequest(val id: String, val session: KoboSession, val format: SendFormat, val covers: CoverFitting, val pdfs: Boolean)

    private val sendLock = Any()
    private val sendQueue = ArrayDeque<SendRequest>()
    private var sendRunning = false
    private var sendingId: String? = null

    init {
        viewModelScope.launch {
            if (!library.loaded) library.load()
            // How far each book has been read in the app's reader, for the covers.
            // (koboApp's, not the property below: init runs before that's set.)
            withContext(Dispatchers.IO) { koboApp.readingHere.load(library.books.value.map { it.id }) { library.bookDir(it) } }
            // Comics added before there were series go in them, by the names they share: once.
            // (A new name for it: 2.2 preview stopped before it could do it.)
            if (!prefs.getBoolean("comic_series_given_2", false)) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val given = ComicSeries.assignAll(library.books.value.filter { it.isComic })
                        if (given.isNotEmpty()) AppLog.i("Library", "Put ${given.size} comic(s) added before there were series in series.")
                        setComicSeries(given.keys) { given[it.id] }
                    }
                }.onFailure { AppLog.e("Library", "Putting comics in series failed", it) }
                prefs.edit().putBoolean("comic_series_given_2", true).apply()
            }
            // Page counts for books added before the app counted them.
            library.fillMissingWordCounts { isActive }
        }
    }

    fun consumeMessage() = _message.update { null }

    /** Books chosen with the file picker or shared from another app. */
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        startJob(JobKind.ADD_FILES, tr("Adding books"), uris.size) { report ->
            val comics = ArrayList<Pair<String, String?>>()
            for (uri in uris) {
                if (!isActive) break
                val name = displayName(uri) ?: uri.lastPathSegment ?: "book"
                report(name) { _ ->
                    val outcome = library.import(
                        name = name,
                        open = { resolver.openInputStream(uri) ?: throw FileNotFoundException(tr("Can't open {0}", name)) },
                        source = BookSource.FILE,
                        hold = true,
                    )
                    if (outcome is ImportOutcome.Added && outcome.book.isComic) comics += outcome.book.id to null
                    // Already there: kept aside, to ask whether it replaces that book or is added as well.
                    if (outcome is ImportOutcome.AlreadyThere) outcome.held?.let { file ->
                        _duplicates.update {
                            it + HeldDuplicate(
                                key = file.name,
                                name = name,
                                file = file,
                                size = file.length(),
                                title = outcome.meta?.title ?: outcome.existing.title,
                                authors = outcome.meta?.authors?.joinToString(" & ").orEmpty(),
                                existingId = outcome.existing.id,
                            )
                        }
                    }
                    outcome.toStep()
                }
            }
            seriesForAdded(comics)
        }
    }

    // ---- the sample books ------------------------------------------------------------------------

    private var addingSamples: Job? = null

    /**
     * Adds the books that come with KoLib, to try it with: three classics and a short comic, in a collection of their
     * own. With [onlyIfEmpty] (the first time KoLib starts) they're only added to a library with no books, once any
     * being added already are in. [onDone] is told how many were added. A sample the library has already isn't added again.
     */
    fun addSamples(onlyIfEmpty: Boolean = false, onDone: (Int) -> Unit = {}) {
        if (addingSamples?.isActive == true) return
        addingSamples = viewModelScope.launch {
            while (!library.loaded) kotlinx.coroutines.delay(100)
            // Books being added from the introduction come first.
            while (_job.value?.finished == false) kotlinx.coroutines.delay(250)
            if (onlyIfEmpty && library.books.value.isNotEmpty()) {
                onDone(0)
                return@launch
            }
            val assets = getApplication<Application>().assets
            val collection = tr("Sample books")
            val comics = ArrayList<Pair<String, String?>>()
            var added = 0
            for (name in SAMPLE_BOOKS) {
                val outcome = try {
                    library.import(name = name, open = { assets.open("samples/$name") }, source = BookSource.FILE, collections = listOf(collection))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("Library", "Couldn't add the sample $name", e)
                    null
                }
                when (outcome) {
                    is ImportOutcome.Added -> {
                        added++
                        if (outcome.book.isComic) comics += outcome.book.id to null
                    }
                    is ImportOutcome.Failed -> AppLog.w("Library", "Couldn't add the sample $name: ${outcome.reason}")
                    else -> {}
                }
            }
            runCatching { seriesForAdded(comics) }.onFailure { AppLog.e("Library", "Couldn't put the sample comic in its series", it) }
            AppLog.i("Library", "Added $added sample book(s).")
            if (!onlyIfEmpty) _message.value = when (added) {
                0 -> tr("The sample books are in your library already.")
                1 -> tr("Added 1 sample book.")
                else -> tr("Added {0} sample books. Remove them whenever you like.", added)
            }
            onDone(added)
        }
    }

    // ---- a folder of books -----------------------------------------------------------------------

    private val _folderScan = MutableStateFlow<dev.kolib.app.library.FolderScan?>(null)
    /** A folder of books being looked through, what's in it, and its books being added. */
    val folderScan: StateFlow<dev.kolib.app.library.FolderScan?> = _folderScan.asStateFlow()
    private var scanning: Job? = null

    /** The folder of books chosen last, kept so it can be looked through again. */
    var booksFolder: Uri?
        get() = prefs.getString("books_folder", null)?.let(Uri::parse)
        private set(value) = prefs.edit().putString("books_folder", value?.toString()).apply()

    /** Looks through [tree], a folder chosen in Android's folder picker, and the folders in it for books. */
    fun scanFolder(tree: Uri) {
        val resolver = getApplication<Application>().contentResolver
        // Kept, so the folder can be looked through again another time.
        runCatching { resolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { AppLog.w("Library", "Android didn't let KoLib keep the folder", it) }
        booksFolder = tree
        val name = dev.kolib.app.library.BookFolders.nameOf(resolver, tree)
        scanning?.cancel()
        _folderScan.value = dev.kolib.app.library.FolderScan.Looking(name, 0)
        scanning = viewModelScope.launch {
            val found = try {
                withContext(Dispatchers.IO) {
                    dev.kolib.app.library.BookFolders.find(resolver, tree) { n ->
                        // Told every few books, so the count can be seen going up.
                        if (n % 5 == 0) _folderScan.value = dev.kolib.app.library.FolderScan.Looking(name, n)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Library", "Couldn't look through the folder $name", e)
                _folderScan.value = dev.kolib.app.library.FolderScan.Failed(name)
                return@launch
            }
            AppLog.i("Library", "Found ${found.size} book file(s) in $name.")
            _folderScan.value = dev.kolib.app.library.FolderScan.Found(name, found)
        }
    }

    /** Adds the books found in the folder. Those the library has already are left as they are. */
    fun addFoundBooks() {
        val scan = _folderScan.value as? dev.kolib.app.library.FolderScan.Found ?: return
        if (scan.books.isEmpty()) return
        if (runningJob?.isActive == true) {
            _message.value = tr("Busy adding or sending books. Try again when it's done.")
            return
        }
        _folderScan.value = dev.kolib.app.library.FolderScan.Adding(scan.folder, scan.books.size)
        val resolver = getApplication<Application>().contentResolver
        startJob(JobKind.ADD_FILES, if (scan.books.size == 1) tr("Adding a book") else tr("Adding {0} books", scan.books.size), scan.books.size) { report ->
            val comics = ArrayList<Pair<String, String?>>()
            for (b in scan.books) {
                if (!isActive) break
                report(b.name) { _ ->
                    val outcome = library.import(
                        name = b.name,
                        open = { resolver.openInputStream(b.uri) ?: throw FileNotFoundException(tr("Can't open {0}", b.name)) },
                        source = BookSource.FILE,
                    )
                    if (outcome is ImportOutcome.Added && outcome.book.isComic) comics += outcome.book.id to b.folder
                    outcome.toStep()
                }
            }
            seriesForAdded(comics, rootFolder = scan.folder)
        }
    }

    // ---- comics in series ------------------------------------------------------------------------

    /**
     * Comics just added go in series, as Mihon has them (see ComicSeries.assign): [added] by id, with the folder each
     * was in inside [rootFolder], the folder chosen, if they came from one.
     */
    private suspend fun seriesForAdded(added: List<Pair<String, String?>>, rootFolder: String? = null) {
        if (added.isEmpty()) return
        withContext(Dispatchers.IO) {
            val comics = library.books.value.filter { it.isComic }
            val ids = added.map { it.first }.toSet()
            val folders = added.mapNotNull { (id, folder) -> folder?.let { id to it } }.toMap()
            val given = ComicSeries.assign(comics.filter { it.id in ids }, comics.filter { it.id !in ids }, folders, rootFolder)
            setComicSeries(given.keys) { given[it.id] }
        }
    }

    /** Puts the comics [ids] in one series called [name], each keeping its number in a series (or taking the one in its name). */
    fun groupComics(ids: List<String>, name: String) {
        val series = name.trim()
        if (series.isEmpty() || ids.isEmpty()) return
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) {
                setComicSeries(ids) { b -> ComicSeries.Given(series, b.meta.seriesIndex ?: ComicSeries.parse(ComicSeries.nameOf(b)).let { it.number ?: it.volume }) }
            }
            _message.value = if (done == 1) tr("1 comic is in “{0}” now.", series) else tr("{0} comics are in “{1}” now.", done, series)
        }
    }

    /** Takes the comics [ids] out of their series: each is on its own in the Comics tab. */
    fun takeComicsOutOfSeries(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) { setComicSeries(ids) { null } }
            _message.value = if (done == 1) tr("Took 1 comic out of its series.") else tr("Took {0} comics out of their series.", done)
        }
    }

    /** Gives each comic of [ids] the series [given] says (none, for null). How many have it now. */
    private suspend fun setComicSeries(ids: Collection<String>, given: (LibraryBook) -> ComicSeries.Given?): Int {
        var done = 0
        for (id in ids) {
            val b = library.book(id)?.takeIf { it.isComic } ?: continue
            val g = given(b)
            val meta = b.meta.copy(series = g?.series, seriesIndex = g?.index)
            if (meta != b.meta) {
                try {
                    // The comic goes to the Kobo as its own file, so this isn't a change to it there.
                    library.updateDetails(id, meta, b.collections, unchanged = true)
                } catch (e: Exception) {
                    AppLog.e("Library", "Couldn't change the series of “${b.title}”", e)
                    continue
                }
            }
            done++
        }
        return done
    }

    /** Done with the folder: what was found is let go. */
    fun forgetFolderScan() {
        scanning?.cancel()
        _folderScan.value = null
    }

    private val _duplicates = MutableStateFlow<List<HeldDuplicate>>(emptyList())
    /** Files added that the library already has, waiting for "Replace", "Keep both" or "Skip". */
    val duplicates: StateFlow<List<HeldDuplicate>> = _duplicates.asStateFlow()

    /**
     * Does what was chosen with each file in [duplicates] (by [HeldDuplicate.key]; Skip if not
     * chosen): it replaces the book it matched, is added as a book of its own, or is let go.
     */
    fun resolveDuplicates(choices: Map<String, DuplicateChoice>) {
        val held = _duplicates.value
        if (held.isEmpty()) return
        _making.value?.let { busy ->
            _message.value = tr("Wait for “{0}” to finish first.", busy)
            return
        }
        _duplicates.value = emptyList()
        if (_job.value?.finished == true) _job.value = null
        val chosen = held.map { it to (choices[it.key] ?: DuplicateChoice.SKIP) }
        if (chosen.all { it.second == DuplicateChoice.SKIP }) {
            held.forEach { library.discardHeld(it.file) }
            _message.value = if (held.size == 1) tr("Skipped “{0}”.", held[0].title) else tr("Skipped {0} books.", held.size)
            return
        }
        val doing = chosen.map { it.second }.filter { it != DuplicateChoice.SKIP }
        val replacing = doing.all { it == DuplicateChoice.REPLACE }
        making(
            when {
                replacing && doing.size == 1 -> tr("Replacing the book")
                replacing -> tr("Replacing the books")
                doing.size == 1 -> tr("Adding the book")
                else -> tr("Adding the books")
            },
        ) {
            var replaced = 0
            var added = 0
            val failed = ArrayList<String>()
            var last: LibraryBook? = null
            for ((d, choice) in chosen) {
                try {
                    when (choice) {
                        DuplicateChoice.REPLACE -> {
                            val existing = library.book(d.existingId)
                            when {
                                // Gone from the library meanwhile: then it's simply added.
                                existing == null -> last = library.addAnyway(d.file, d.name).also { added++ }
                                textEdit?.bookId == existing.id -> {
                                    failed += d.title
                                    AppLog.w("Library", "'${existing.title}' wasn't replaced: it's open in the text editor.")
                                }
                                else -> {
                                    last = library.replaceWith(existing.id, d.file, d.name)
                                    replaced++
                                }
                            }
                        }
                        DuplicateChoice.KEEP_BOTH -> {
                            last = library.addAnyway(d.file, d.name)
                            added++
                        }
                        DuplicateChoice.SKIP -> Unit
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("Library", "Couldn't ${choice.label.lowercase()} '${d.title}'", e)
                    failed += d.title
                } finally {
                    library.discardHeld(d.file)
                }
            }
            val only = last.takeIf { replaced + added == 1 && failed.isEmpty() }
            _message.value = when {
                only != null && replaced == 1 -> tr("Replaced “{0}” with the new file.", only.title)
                only != null -> tr("Added “{0}” as a separate book.", only.title)
                else -> listOfNotNull(
                    replaced.takeIf { it > 0 }?.let { if (it == 1) tr("Replaced 1 book with the new file.") else tr("Replaced {0} books with the new files.", it) },
                    added.takeIf { it > 0 }?.let { if (it == 1) tr("Added 1 book as a separate book.") else tr("Added {0} books as separate books.", it) },
                    failed.size.takeIf { it > 0 }?.let { tr("{0} failed. The log has the details.", it) },
                ).joinToString(" ")
            }
        }
    }

    /** Copies of [ids] in the library, each called "Title (copy)"; [onDone] gets their ids. */
    fun duplicate(ids: List<String>, onDone: (List<String>) -> Unit) = making(if (ids.size == 1) tr("Duplicating the book") else tr("Duplicating the books")) {
        val made = ArrayList<LibraryBook>()
        for ((i, id) in ids.withIndex()) {
            if (library.book(id) == null) continue
            if (ids.size > 1) _making.value = tr("Duplicating ({0} of {1})", i + 1, ids.size)
            made += library.duplicate(id)
        }
        if (made.isEmpty()) return@making
        _message.value = if (made.size == 1) tr("Duplicated as “{0}”.", made[0].title) else tr("Duplicated {0} books.", made.size)
        onDone(made.map { it.id })
    }

    /** Copies books from the connected Kobo, keeping the collections they're in. */
    fun importFromKobo(session: KoboSession, books: List<KoboBook>) {
        val wanted = books.filter { it.isSideloaded && it.relativePath != null }
        if (wanted.isEmpty()) {
            _message.value = tr("Kobo Store books can't be added, only ones you sideloaded.")
            return
        }
        val needed = wanted.sumOf { it.fileSize ?: 0L }
        if (needed > 0 && needed > library.freeSpaceBytes() - 200L * 1024 * 1024) {
            _message.value = tr("Not enough free space on this device for {0} books.", wanted.size)
            return
        }
        startJob(JobKind.ADD_FROM_KOBO, tr("Adding books from the Kobo"), wanted.size) { report ->
            for (book in wanted) {
                if (!isActive) break
                report(book.title) { _ -> library.importFromKobo(session, book).toStep() }
            }
        }
    }

    /**
     * Copies library books to the Kobo as [format]. Books already there are replaced in place
     * (in their new format if it changed). While books are being sent, more can be added: they join
     * the same send.
     */
    fun sendToKobo(session: KoboSession, ids: List<String>, format: SendFormat, covers: CoverFitting = coverFitting, pdfs: Boolean = sendPdfs) {
        sendFormat = format
        coverFitting = covers
        sendPdfs = pdfs
        val chosen = ids.mapNotNull { library.book(it) }
        if (chosen.isEmpty()) return
        val needed = chosen.sumOf { it.fileSize } + chosen.size * 64L * 1024
        val free = session.info.freeBytes
        if (free != null && needed > free - 20L * 1024 * 1024) {
            _message.value = tr("Not enough space on the Kobo: about {0} MB needed, {1} MB free.", needed / (1024 * 1024), free / (1024 * 1024))
            return
        }
        val requests = chosen.map { SendRequest(it.id, session, format, covers, pdfs) }
        synchronized(sendLock) {
            if (sendRunning) {
                val waiting = sendQueue.map { it.id }.toSet() + listOfNotNull(sendingId)
                val fresh = requests.filter { it.id !in waiting }
                sendQueue.addAll(fresh)
                if (fresh.isNotEmpty()) {
                    _job.update { p -> p?.copy(total = p.total + fresh.size, title = sendTitle(p.total + fresh.size)) }
                    AppLog.i("Library", "${fresh.size} more book(s) added to the send that's running.")
                }
                _message.value = when {
                    fresh.isEmpty() -> tr("Already being sent.")
                    fresh.size == 1 -> tr("Added to the send queue.")
                    else -> tr("{0} books added to the send queue.", fresh.size)
                }
                return
            }
        }
        val previous = runningJob
        if (previous?.isActive == true) {
            if (_job.value?.kind == JobKind.SEND) {
                // The last send is just finishing: start this one straight after it.
                viewModelScope.launch {
                    previous.join()
                    sendToKobo(session, ids, format, covers, pdfs)
                }
            } else {
                _message.value = tr("Still adding books. Try again when it's done.")
            }
            return
        }
        synchronized(sendLock) {
            sendQueue.clear()
            sendQueue.addAll(requests)
            sendRunning = true
        }
        startJob(JobKind.SEND, sendTitle(requests.size), requests.size) { report ->
            var savingAsked = false
            try {
                while (isActive) {
                    val next = synchronized(sendLock) {
                        sendQueue.removeFirstOrNull().also {
                            sendingId = it?.id
                            if (it == null) sendRunning = false
                        }
                    } ?: break
                    val title = library.book(next.id)?.title ?: tr("A book")
                    report(title) { fraction ->
                        // The book may have been edited since it was chosen.
                        val current = library.book(next.id) ?: return@report Step.Failed(title, tr("It's no longer in the library."))
                        val place = placeOnKobo(current, next.session.snapshot, next.format)
                        // A cover set to "Leave unchanged" is never fitted to the screen.
                        val covers = if (current.coverAsIs) CoverFitting.AS_IS else next.covers
                        // A PDF goes as it is when asked; scanned pages always do, having no text to convert, and so do comics.
                        val pdf = if (current.goesAsPdf(next.pdfs) || current.isComic) library.sourceFile(current.id) else null
                        val outcome = sender.send(
                            next.session, current.title, current.meta, place, library.epubFile(current.id), next.format, covers,
                            freshPlace = { format -> freshPlaceOnKobo(current, next.session.snapshot, format, besides = place) },
                            onProgress = fraction,
                            pdf = pdf,
                        )
                        when (outcome) {
                            is SendOutcome.Sent -> {
                                outcome.keptRecord?.let { kept ->
                                    koboApp.pendingKoboRecords.put(
                                        kept.contentId,
                                        PendingKoboRecords.Pending(current.id, next.session.connectedAtMillis, kept.columns),
                                    )
                                }
                                library.markSent(current.id, outcome.path, afresh = outcome.koboStartsAfresh)
                                if (outcome.coverShowsAfterRestart) {
                                    _job.update { it?.copy(coversAfterRestart = it.coversAfterRestart + current.title) }
                                }
                                if (outcome.replaced) Step.Already else Step.Ok
                            }
                            is SendOutcome.Failed -> Step.Failed(current.title, outcome.reason, current.id.takeIf { outcome.conversionFailed })
                        }
                    }
                }
                // Collections and details go into the Kobo's database once the books are there. Sending
                // isn't finished until that's saved: "Sent" says to eject the Kobo, and ejecting it while
                // the database is being written makes Android wait several seconds and then close the app.
                savingAsked = true
                val saved = CompletableDeferred<Unit>()
                koboApp.koboSyncRequests.tryEmit(saved)
                _job.update { it?.copy(current = tr("Updating collections and book details on the Kobo")) }
                if (withTimeoutOrNull(3 * 60_000L) { saved.await() } == null) {
                    AppLog.w("Library", "Saving to the Kobo's database is taking a long time; not waiting for it any more.")
                }
            } finally {
                synchronized(sendLock) {
                    sendRunning = false
                    sendingId = null
                    sendQueue.clear()
                }
                if (!savingAsked) koboApp.koboSyncRequests.tryEmit(null)
            }
        }
    }

    /**
     * A page for the Kobo's own web browser offering [ids], each made just as it would go over the cable: as [format],
     * with its cover fitted as [covers] says, books added as PDFs as their PDF when [pdfs], and comics as they are.
     * It runs while [KoboWifiServer.start]ed, for when the Kobo isn't plugged in.
     */
    fun koboOverWifi(ids: List<String>, format: SendFormat, covers: CoverFitting, pdfs: Boolean): dev.kolib.app.kobo.KoboWifiServer {
        sendFormat = format
        coverFitting = covers
        sendPdfs = pdfs
        // Books made ready go in a folder of their own, let go of when the page stops (and any left from before now).
        val dir = java.io.File(java.io.File(getApplication<Application>().cacheDir, "kobo-wifi").apply { deleteRecursively() }, "${System.nanoTime()}").apply { mkdirs() }
        val taken = HashSet<String>()
        val books = ids.mapNotNull { library.book(it) }.mapIndexed { i, b ->
            val kind = when {
                b.isComic -> if (b.sourceFormat == "CBR") "CBR" else "CBZ"
                b.goesAsPdf(pdfs) -> "PDF"
                format == SendFormat.KEPUB -> "kepub"
                else -> "EPUB"
            }
            val ending = dev.kolib.app.kobo.KoboWifiServer.endingFor(kind)
            var name = dev.kolib.app.kobo.KoboWifiServer.downloadName(b.meta, ending, b.id.take(8))
            // Two books of the same name: the Kobo would keep only one of them.
            var n = 2
            while (!taken.add(name.lowercase())) name = dev.kolib.app.kobo.KoboWifiServer.downloadName(b.meta, " (${n++})$ending", b.id.take(8))
            dev.kolib.app.kobo.WifiBook(i, b.id, b.title, b.authorLine, kind, name, hasCover = b.hasCover)
        }
        AppLog.i("WiFi", "Offering ${books.size} book(s) to the Kobo over Wi-Fi as ${format.label}.")
        return dev.kolib.app.kobo.KoboWifiServer(
            books = books,
            scope = viewModelScope,
            make = { w ->
                withContext(Dispatchers.IO) {
                    val b = library.book(w.id) ?: throw java.io.IOException(tr("It's no longer in the library."))
                    // A PDF goes as it is when asked; scanned pages always do, and so do comics.
                    val asIs = if (b.goesAsPdf(pdfs) || b.isComic) library.sourceFile(b.id) else null
                    // A cover set to "Leave unchanged" is never fitted to the screen.
                    val fitting = if (b.coverAsIs) CoverFitting.AS_IS else covers
                    sender.fileForDownload(b.title, library.epubFile(b.id), asIs, format, fitting, dir, b.id, w.name)
                }
            },
            cover = { w ->
                withContext(Dispatchers.IO) {
                    val file = library.coverFile(w.id).takeIf { it.isFile } ?: return@withContext null
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.path) ?: return@withContext null
                    dev.kolib.app.ui.cover.CoverRendering.toJpeg(dev.kolib.app.kobo.KoboCovers.boxed(bitmap, 160, 240), 80)
                }
            },
            onStopped = { dir.deleteRecursively() },
        )
    }

    private fun sendTitle(n: Int) = if (n == 1) tr("Sending to the Kobo") else tr("Sending {0} books to the Kobo", n)

    /** Places on the Kobo (as [KoboPaths.stem]s) that library books other than [book] are linked to. */
    private fun takenBesides(book: LibraryBook): Set<String> =
        library.books.value.filter { it.id != book.id }.mapNotNullTo(HashSet()) { it.koboPath?.let(KoboPaths::stem) }

    /**
     * Where [book] is on the Kobo: where it was sent, or a copy the Kobo has that is evidently this book
     * (one added from a file may be there already). A copy another library book is linked to doesn't
     * count: that's the other book.
     */
    fun pathOnKobo(book: LibraryBook, snapshot: KoboSnapshot?): String? {
        book.koboPath?.let { return it }
        snapshot ?: return null
        val taken = takenBesides(book)
        return KoboSender.findOnKobo(snapshot, book.meta) { KoboPaths.stem(it) in taken }?.relativePath
    }

    /** Where [book] goes when it's sent: where it is already, or a new place no other book has. */
    private fun placeOnKobo(book: LibraryBook, snapshot: KoboSnapshot, format: SendFormat): String =
        pathOnKobo(book, snapshot) ?: takenBesides(book).let { taken -> KoboPaths.freeFor(book.meta, format) { it in taken } }

    /**
     * A new place for [book], away from where it is now ([besides]) and from every other book, in the
     * library or on the Kobo: for a book that has to go to the Kobo as a new one.
     */
    private fun freshPlaceOnKobo(book: LibraryBook, snapshot: KoboSnapshot, format: SendFormat, besides: String): String {
        val taken = takenBesides(book) + snapshot.books.mapNotNull { b -> b.relativePath?.let(KoboPaths::stem) } + KoboPaths.stem(besides)
        return KoboPaths.freeFor(book.meta, format) { it in taken }
    }

    private fun ImportOutcome.toStep(): Step = when (this) {
        is ImportOutcome.Added -> Step.Ok
        is ImportOutcome.AlreadyThere -> Step.Already
        is ImportOutcome.Failed -> Step.Failed(name, reason)
    }

    private fun startJob(
        kind: JobKind,
        title: String,
        total: Int,
        work: suspend CoroutineScope.(report: suspend (String, suspend ((Float) -> Unit) -> Step) -> Unit) -> Unit,
    ) {
        if (runningJob?.isActive == true) {
            _message.value = tr("Another task is running. Try again when it's done.")
            return
        }
        _job.value = JobProgress(kind, title, total)
        runningJob = viewModelScope.launch {
            AppLog.i("Library", "$title: $total book(s).")
            work { name, step ->
                _job.update { it?.copy(current = name, currentFraction = 0f) }
                val result = try {
                    step { f -> _job.update { it?.copy(currentFraction = f) } }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("Library", "'$name' failed", e)
                    Step.Failed(name, e.message ?: e.javaClass.simpleName)
                }
                if (result is Step.Failed) AppLog.w("Library", "'${result.name}': ${result.reason}")
                _job.update { p ->
                    p?.copy(
                        done = p.done + 1,
                        currentFraction = 0f,
                        succeeded = p.succeeded + if (result is Step.Ok || (kind == JobKind.SEND && result is Step.Already)) 1 else 0,
                        alreadyThere = p.alreadyThere + if (result is Step.Already) 1 else 0,
                        failures = if (result is Step.Failed) p.failures + (result.name to result.reason) else p.failures,
                        conversionFailedIds = (result as? Step.Failed)?.conversionFailedId?.let { p.conversionFailedIds + it } ?: p.conversionFailedIds,
                    )
                }
            }
            _job.update { it?.copy(finished = true, current = null) }
            _job.value?.let { AppLog.i("Library", "$title finished: ${it.succeeded} done, ${it.alreadyThere} already there, ${it.failures.size} failed.") }
        }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause is kotlinx.coroutines.CancellationException) {
                    _job.update { it?.copy(finished = true, cancelled = true, current = null) }
                }
            }
        }
    }

    fun cancelJob() {
        runningJob?.cancel()
    }

    fun dismissJob() {
        if (_job.value?.finished == true || runningJob?.isActive != true) _job.value = null
    }

    fun saveDetails(id: String, meta: BookMetadata, collections: List<String>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            val ok = try {
                val saved = library.updateDetails(id, meta, collections)
                _message.value = tr("Saved.")
                // Collections of a book on the Kobo are updated there too (if it's connected).
                if (saved.koboPath != null) koboApp.koboSyncRequests.tryEmit(null)
                true
            } catch (e: Exception) {
                AppLog.e("Library", "Saving details failed", e)
                _message.value = tr("Couldn't save: {0}", e.message)
                false
            }
            _saving.value = false
            onDone(ok)
        }
    }

    fun saveCover(id: String, cover: SavedCover, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            val ok = try {
                library.updateCover(id, cover.bytes, cover.mediaType, cover.width, cover.height, cover.asIs)
                _message.value = if (cover.asIs) tr("Cover saved. It will be sent to the Kobo unchanged.") else tr("Cover saved.")
                true
            } catch (e: Exception) {
                AppLog.e("Library", "Saving the cover failed", e)
                _message.value = tr("Couldn't save the cover: {0}", e.message)
                false
            }
            _saving.value = false
            onDone(ok)
        }
    }

    /**
     * Marks books as finished: they show as finished instead of how far through, and open at the
     * beginning; reading one again shows how far through again.
     */
    fun markFinished(ids: Collection<String>) {
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) {
                ids.mapNotNull { id -> runCatching { id to ReaderStore.markFinished(library.bookDir(id)) }.getOrNull() }
            }
            done.forEach { (id, here) -> koboApp.readingHere.set(id, here) }
            _message.value = if (ids.size == 1) tr("Marked as finished.") else tr("Marked {0} books as finished.", ids.size)
        }
    }

    /**
     * Moves the app's reader to where the Kobo is in the book (as the Kobo's database had it when it was
     * last connected), then [onReady] opens the book there.
     */
    fun continueFromKobo(id: String, kobo: KoboBook, onReady: () -> Unit) {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { KoboPlaces.find(library.epubFile(id), kobo) }
                    .onFailure { AppLog.e("Kobo place", "Finding the Kobo's place in “${kobo.title}” failed", it) }
                    .getOrNull()
            }
            if (found == null) {
                _message.value = tr("Couldn't find the Kobo's position in this book.")
                return@launch
            }
            withContext(Dispatchers.IO) { ReaderStore.movePosition(library.bookDir(id), found.position) }
            koboApp.readingHere.update(id, found.position)
            AppLog.i("Kobo place", "The reader is now at ${found.position.path} (${found.position.anchor ?: "no exact spot"}), found by ${found.match}.")
            onReady()
        }
    }

    /** Marks books as not started: how far through goes, and they open at the beginning. Bookmarks and highlights stay. */
    fun markUnread(ids: Collection<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { id -> runCatching { ReaderStore.markUnread(library.bookDir(id)) } } }
            koboApp.readingHere.forget(ids)
            _message.value = if (ids.size == 1) tr("Marked as unread.") else tr("Marked {0} books as unread.", ids.size)
        }
    }

    /** Makes scanned books into text (it runs on its own, outside this screen). */
    val ocr: dev.kolib.app.ocr.OcrManager get() = koboApp.ocr

    /** A book made into text goes back to being read and sent as its scan. */
    fun goBackToScan(id: String) {
        viewModelScope.launch {
            try {
                val book = library.goBackToScan(id)
                _message.value = tr("“{0}” reverted to the scan.", book.title)
            } catch (e: Exception) {
                AppLog.e("Library", "Going back to the scan failed", e)
                _message.value = tr("Couldn't revert to the scan: {0}", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun delete(ids: Collection<String>) {
        // A book being made into text stops first.
        ids.forEach { ocr.stop(it) }
        viewModelScope.launch {
            library.delete(ids)
            koboApp.readingHere.forget(ids)
            _message.value = if (ids.size == 1) tr("Removed from the library.") else tr("Removed {0} books from the library.", ids.size)
        }
    }

    suspend fun originalCover(id: String): ByteArray? = library.originalCover(id)

    /** The cover the book came with, if it's been changed since. */
    suspend fun savedOriginalCover(id: String): ByteArray? = library.savedOriginalCover(id)

    fun restoreOriginalCover(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            val ok = try {
                library.restoreOriginalCover(id)
                _message.value = tr("Original cover restored.")
                true
            } catch (e: Exception) {
                AppLog.e("Library", "Putting back the original cover failed", e)
                _message.value = tr("Couldn't restore the original cover: {0}", e.message)
                false
            }
            _saving.value = false
            onDone(ok)
        }
    }

    // ---- making books: a new one, one from several, several from one ----------------------------------

    /**
     * A book without a cover of its own gets one drawn, as the cover editor's Generate would. A [split] book's is added
     * as a cover of its own, leaving any picture on its first page (a title page shared from the book it came from) be.
     */
    private suspend fun withCover(book: LibraryBook, split: Boolean = false, design: dev.kolib.app.ui.cover.CoverDesign? = null): LibraryBook {
        // In a [design] given, every book gets a new cover; one it had is kept, to be put back ("Restore original").
        if (book.hasCover && design == null) return book
        return try {
            val generator = dev.kolib.app.ui.cover.CoverGenerator(getApplication())
            val text = dev.kolib.app.ui.cover.CoverText(book.meta.title, book.meta.authors, book.meta.series, book.meta.seriesIndex)
            val bmp = withContext(Dispatchers.Default) { generator.render(text, design ?: dev.kolib.app.ui.cover.CoverDesign.random()) }
            library.updateCover(book.id, dev.kolib.app.ui.cover.CoverRendering.toJpeg(bmp), "image/jpeg", bmp.width, bmp.height, first = split && !book.hasCover)
        } catch (e: Exception) {
            AppLog.w("Library", "Couldn't draw a cover for '${book.title}'", e)
            book
        }
    }

    private val _making = MutableStateFlow<String?>(null)
    /** What's being made right now ("Combining the books"), shown while it's under way. */
    val making: StateFlow<String?> = _making

    private fun making(what: String, block: suspend () -> Unit) {
        // One at a time: marked as under way straight away, so a second tap can't start another.
        _making.value?.let { busy ->
            _message.value = tr("Wait for “{0}” to finish first.", busy)
            return
        }
        _making.value = what
        viewModelScope.launch {
            try {
                block()
            } catch (e: dev.kolib.app.epub.AssemblyException) {
                _message.value = e.message
            } catch (e: Exception) {
                AppLog.e("Library", "$what failed", e)
                _message.value = "$what failed: ${e.message}"
            } finally {
                _making.value = null
            }
        }
    }

    /** A new, empty book, opened in the editor once it's made ([onDone] gets its id). */
    fun createBook(title: String, author: String, onDone: (String) -> Unit) = making(tr("Creating the book")) {
        val meta = dev.kolib.app.epub.BookMetadata(
            title = title.trim().ifEmpty { tr("Untitled") },
            authors = author.split('&').map { it.trim() }.filter { it.isNotEmpty() },
            language = java.util.Locale.getDefault().toLanguageTag(),
        )
        val book = withCover(library.createBook(meta))
        _message.value = tr("Created “{0}”.", book.title)
        onDone(book.id)
    }

    /** [ids] put together in that order as one book; the books themselves stay as they are. */
    fun mergeBooks(ids: List<String>, title: String, author: String, coverFrom: Int?, onDone: (String) -> Unit) = making(tr("Combining the books")) {
        val books = ids.mapNotNull { library.book(it) }
        val first = books.firstOrNull() ?: return@making
        val publishers = books.mapNotNull { it.meta.publisher?.takeIf { p -> p.isNotBlank() } }.distinct()
        val meta = dev.kolib.app.epub.BookMetadata(
            title = title.trim().ifEmpty { first.title },
            authors = author.split('&').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { first.meta.authors },
            language = first.meta.language,
            publisher = publishers.singleOrNull(),
            tags = books.flatMap { it.meta.tags }.distinctBy { it.lowercase() },
            description = "<p>Contains:</p><ul>" + books.joinToString("") { "<li>" + android.text.TextUtils.htmlEncode(it.title) + "</li>" } + "</ul>",
        )
        val book = withCover(library.mergeBooks(ids, meta, coverFrom))
        _message.value = tr("Combined {0} books into “{1}”.", books.size, book.title)
        onDone(book.id)
    }

    suspend fun pagesOf(id: String) = library.pagesOf(id)

    /**
     * [id] cut into books at [cuts], titled [titles]; numbered as a series called [series] if it's
     * given. The book itself stays as it is. [shared]: pages before the first book and after the last (a title page, a
     * copyright notice, a contents page) that go into every one.
     */
    fun splitBook(
        id: String, cuts: List<Int>, titles: List<String>, series: String?, shared: dev.kolib.app.epub.BookAssembly.Shared? = null,
        /** Every book gets a cover in this design (null: a book without a cover gets one in a design picked at random). */
        covers: dev.kolib.app.ui.cover.CoverSet? = null,
        onDone: (List<String>) -> Unit,
    ) = making(tr("Splitting the book")) {
        val source = library.book(id) ?: return@making
        val metas = titles.mapIndexed { k, t ->
            source.meta.copy(
                title = t.trim().ifEmpty { tr("{0} (part {1})", source.title, k + 1) },
                titleSort = null,
                identifier = null,
                isbn = null,
                description = null,
                series = series?.takeIf { it.isNotBlank() } ?: source.meta.series,
                seriesIndex = if (!series.isNullOrBlank()) (k + 1).toDouble() else null,
            )
        }
        val split = library.splitBook(id, cuts, metas, shared)
        val made = split.mapIndexed { k, b -> withCover(b, split = true, design = covers?.designFor(k, split.size)) }
        _message.value = tr("Split “{0}” into {1} books.", source.title, made.size)
        onDone(made.map { it.id })
    }

    // ---- saving copies elsewhere ---------------------------------------------------------------------

    /** The name a book is saved under: "Title - Author.epub", ".kepub.epub", or (null) the ending of the file it was added as. */
    fun exportName(id: String, format: dev.kolib.app.kobo.SendFormat?): String {
        val ending = format?.extension ?: library.sourceFile(id)?.let { ".${it.extension}" } ?: ".epub"
        val b = library.book(id) ?: return "book$ending"
        return dev.kolib.app.kobo.KoboPaths.component("${b.title} - ${b.meta.authors.joinToString(" & ").ifBlank { tr("Unknown") }}", 110) + ending
    }

    /** What kind of file [book] was added as ("PDF"), when it wasn't an EPUB. */
    fun addedAsLabel(book: LibraryBook): String? = when (book.sourceFormat) {
        null -> null
        dev.kolib.app.convert.BookFormats.PDF_SCAN, dev.kolib.app.convert.BookFormats.PDF_ONLY,
        dev.kolib.app.convert.BookFormats.PDF_OCR -> "PDF"
        else -> dev.kolib.app.convert.SourceFormat.entries.firstOrNull { it.name == book.sourceFormat }?.label
    }

    /** The book as a file: itself, converted to kepub as it is for the Kobo, or (null) the file it was added as. */
    private fun writeCopy(id: String, format: dev.kolib.app.kobo.SendFormat?, out: java.io.OutputStream) {
        if (format == null) {
            (library.sourceFile(id) ?: library.epubFile(id)).inputStream().use { it.copyTo(out) }
            return
        }
        val book = library.book(id)
        val comic = book?.takeIf { it.isComic }?.let { library.sourceFile(id) }
        // A comic's EPUB is a stand-in: the whole of it is made from the comic file, a page at a time.
        val whole = comic?.let { file ->
            java.io.File(getApplication<Application>().cacheDir, "comic-${System.nanoTime()}.epub").also { epub ->
                dev.kolib.app.comic.ComicArchives.open(file).use { c -> dev.kolib.app.convert.ComicConverter.wholeEpub(c, book.meta, epub) }
            }
        }
        val source = whole ?: library.epubFile(id)
        val temp = java.io.File(getApplication<Application>().cacheDir, "copy-${System.nanoTime()}.kepub.epub")
        try {
            if (format == dev.kolib.app.kobo.SendFormat.EPUB) {
                source.inputStream().use { it.copyTo(out) }
                return
            }
            dev.kolib.app.epub.EpubEditor.toKepub(source, temp)
            temp.inputStream().use { it.copyTo(out) }
        } finally {
            temp.delete()
            whole?.delete()
        }
    }

    /** Saves [id] as [format] to [uri], a file chosen in Android's save picker. */
    fun exportBook(id: String, format: dev.kolib.app.kobo.SendFormat?, uri: Uri) = making(tr("Saving the file")) {
        val book = library.book(id) ?: return@making
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            (resolver.openOutputStream(uri, "wt") ?: throw java.io.IOException(tr("Can't save there."))).use { writeCopy(id, format, it) }
        }
        AppLog.i("Library", "Saved a copy of '${book.title}' as ${format?.label ?: "the file it was added as"}.")
        _message.value = when (format) {
            null -> tr("Saved “{0}”.", book.title)
            dev.kolib.app.kobo.SendFormat.KEPUB -> tr("Saved “{0}” as a kepub.", book.title)
            else -> tr("Saved “{0}” as an EPUB.", book.title)
        }
    }

    /** Saves [ids] as [format] into [tree], a folder chosen in Android's folder picker, each as "Title - Author". */
    fun exportBooks(ids: List<String>, format: dev.kolib.app.kobo.SendFormat?, tree: Uri) = making(tr("Saving the files")) {
        val resolver = getApplication<Application>().contentResolver
        val folder = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
        var saved = 0
        val failed = ArrayList<String>()
        for ((i, id) in ids.withIndex()) {
            val book = library.book(id) ?: continue
            _making.value = tr("Saving files ({0} of {1})", i + 1, ids.size)
            try {
                withContext(Dispatchers.IO) {
                    val name = exportName(id, format)
                    val mime = if (name.endsWith(".epub")) "application/epub+zip" else "application/octet-stream"
                    val file = android.provider.DocumentsContract.createDocument(resolver, folder, mime, name)
                        ?: throw java.io.IOException(tr("Couldn't create a file in that folder."))
                    (resolver.openOutputStream(file, "wt") ?: throw java.io.IOException(tr("Couldn't write the new file."))).use { writeCopy(id, format, it) }
                }
                saved++
            } catch (e: Exception) {
                AppLog.e("Library", "Couldn't save a copy of '${book.title}'", e)
                failed += book.title
            }
        }
        AppLog.i("Library", "Saved copies of $saved books as ${format?.label ?: "the files they were added as"}.")
        _message.value = (format?.let { tr("Saved {0} as {1}.", Fmt.plural(saved, "book"), it.label) } ?: tr("Saved {0}.", Fmt.plural(saved, "book"))) +
            if (failed.isNotEmpty()) tr(" {0} couldn't be saved. The log has the details.", failed.size) else ""
    }

    // ---- reading and editing the text --------------------------------------------------------------

    val readerSettings = koboApp.readerSettings

    /** How far each book has been read in the app's reader. */
    val readingHere = koboApp.readingHere

    /** The time spent reading each book in the app's readers. */
    val readingLog = koboApp.readingLog

    private var textEdit: TextEditState? = null

    /** The text editor for a book, kept while the editor is open (it holds the unsaved changes). */
    fun textEditor(bookId: String, path: String?): TextEditState? {
        textEdit?.let { if (it.bookId == bookId) return it else closeTextEditor() }
        val book = library.book(bookId) ?: return null
        return try {
            TextEditState(bookId, book.title, library, viewModelScope, getApplication<Application>().assets, path, koboApp.copiedSections).also { textEdit = it }
        } catch (e: Exception) {
            AppLog.e("Editor", "The book couldn't be opened for editing", e)
            _message.value = tr("This book can't be edited: {0}", e.message)
            null
        }
    }

    fun closeTextEditor() {
        textEdit?.close()
        textEdit = null
    }

    override fun onCleared() {
        closeTextEditor()
        super.onCleared()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

/** The sample books in the app's assets (samples/), in the order they're added: the last added shows first. */
private val SAMPLE_BOOKS = listOf("brown.epub", "persuasion.epub", "alice.epub", "lighthouse-cat.cbz")
