package dev.kobolibrary.app

import android.app.Application
import dev.kobolibrary.app.kobo.PendingKoboRecords
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.library.LibraryCovers
import dev.kobolibrary.app.log.AppLog
import dev.kobolibrary.app.reader.ReaderSettingsStore
import dev.kobolibrary.app.reader.ReadingHereIndex
import dev.kobolibrary.app.ui.theme.ThemeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class KoboLibraryApp : Application() {

    /** The books kept on the tablet; shared by the library and Kobo screens. */
    val library: Library by lazy { Library(this) }
    val libraryCovers: LibraryCovers by lazy { LibraryCovers(library) }

    /**
     * Asks for the Kobo's database to be brought in line with the library (collections, details) once
     * the current work is done: after sending, or after a book on the Kobo was edited. A request can
     * bring something to complete when that's done (or turns out not to be possible), for work that
     * isn't finished until then.
     */
    val koboSyncRequests = MutableSharedFlow<CompletableDeferred<Unit>?>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Changes to the Kobo's records of books just sent again, for the next write of its database. */
    val pendingKoboRecords = PendingKoboRecords()

    /** The colours the app is shown in. */
    val themeSettings: ThemeSettings by lazy { ThemeSettings(this) }

    /** How books look in the reader (the same for every book). */
    val readerSettings: ReaderSettingsStore by lazy { ReaderSettingsStore(this) }

    /** How far each library book has been read in the app's reader. */
    val readingHere: ReadingHereIndex by lazy { ReadingHereIndex() }

    /** Makes scanned books into text, in the background. */
    val ocr: dev.kobolibrary.app.ocr.OcrManager by lazy { dev.kobolibrary.app.ocr.OcrManager(this) }

    /** Sections copied out of books in the text editor, to put into others; kept until deleted. */
    val copiedSections: dev.kobolibrary.app.library.CopiedSections by lazy {
        dev.kobolibrary.app.library.CopiedSections(java.io.File(filesDir, "copied-sections"))
    }

    // Before Android 13, the language chosen in KoLib is applied here (and to each screen).
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // The small process that starts KoLib again after a change of language needs none of this.
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":restart")) return
        L10n.load(this)
        dev.kobolibrary.app.kobo.CoverScreen.load(this)
        AppLog.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.crash(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }
}
