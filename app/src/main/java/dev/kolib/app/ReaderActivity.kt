package dev.kolib.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.kolib.app.comic.ComicOwnStore
import dev.kolib.app.comic.ComicSettingsStore
import dev.kolib.app.kobo.KoboChapterSpan
import dev.kolib.app.library.ComicSeries
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.log.AppLog
import dev.kolib.app.reader.ReaderKeys
import dev.kolib.app.ui.library.libraryComics
import dev.kolib.app.ui.reader.ReaderHost
import dev.kolib.app.ui.reader.forComics
import dev.kolib.app.ui.reader.orientation
import dev.kolib.app.ui.theme.AppSurface
import kotlinx.coroutines.launch

/**
 * The readers (a book, a comic or a PDF), in an activity of their own, as Mihon's reader is. It's turned the way it's to
 * be read as it opens, and turned again when that's changed, made anew each time it turns. A Samsung tablet held in
 * landscape won't turn for the library's own screen when it asks while it's open, but does for Mihon's reader.
 */
class ReaderActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_BOOK = "dev.kolib.app.READ_BOOK"

        /** How the Kobo shares the book about to be opened out between its chapters, by its id: too much for an intent. */
        @Volatile
        private var koboSpans: Pair<String, List<KoboChapterSpan>>? = null

        /** Opens [bookId] to read. */
        fun open(context: Context, bookId: String, spans: List<KoboChapterSpan> = emptyList()) {
            koboSpans = bookId to spans
            context.startActivity(Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK, bookId))
        }

        /**
         * The way round [book]'s reader asks for, as Android has it: the comic reader's choice for its series (or its
         * own), or the book reader's.
         */
        fun orientationFor(context: Context, app: KoLibApp, book: LibraryBook): Int {
            if (!book.isComic || app.library.sourceFile(book.id) == null) return app.readerSettings.settings.value.rotation.orientation
            val usual = ComicSettingsStore.get(context, firstRotation = app.readerSettings.settings.value.rotation.forComics()).settings.value
            val series = ComicSeries.entryOf(book, libraryComics(app.library.books.value))?.takeIf { it.isSeries }
            val own = ComicOwnStore.get(context).all.value[series?.key ?: ("b:" + book.id)]
            return (own?.rotation ?: usual.rotation).orientation
        }
    }

    private val app get() = application as KoLibApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KoLib)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.getStringExtra(EXTRA_BOOK)
        if (id == null) {
            finish()
            return
        }
        val spans = koboSpans?.takeIf { it.first == id }?.second.orEmpty()
        if (app.library.loaded) start(id, spans, savedInstanceState)
        // Opened again after Android closed the app: the library's read first.
        else lifecycleScope.launch {
            app.library.load()
            start(id, spans, savedInstanceState)
        }
    }

    private fun start(id: String, spans: List<KoboChapterSpan>, saved: Bundle?) {
        val book = app.library.book(saved?.getString(KEY_SHOWING) ?: id) ?: app.library.book(id)
        if (book == null) {
            finish()
            return
        }
        // Turned the way it's to be read before it's shown, as Mihon's reader is.
        val wanted = orientationFor(this, app, book)
        if (requestedOrientation != wanted) requestedOrientation = wanted
        AppLog.i(
            "Rotation",
            "Reader opened: asks for ${orientationName(wanted)}, showing ${if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}.",
        )
        setContent {
            val theme by app.themeSettings.choice.collectAsStateWithLifecycle()
            AppSurface(theme) {
                ReaderHost(
                    startId = book.id,
                    koboSpans = spans,
                    onShowing = { showing = it },
                    onClose = { finish() },
                    onEditText = { bookId, path ->
                        // The text editor is the library's: it opens there, at this chapter.
                        app.openInLibrary.value = "text:$bookId|$path"
                        finish()
                    },
                )
            }
        }
    }

    /** The book or comic being read (reading on into the next comic changes it), kept for when it's made anew. */
    private var showing: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        showing?.let { outState.putString(KEY_SHOWING, it) }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) app.touched()
        return super.dispatchTouchEvent(ev)
    }

    /** The volume buttons turn pages, if they're set to. */
    @SuppressLint("RestrictedApi") // Calling the activity's own dispatchKeyEvent from its override is fine.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) app.touched()
        if (ReaderKeys.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

private const val KEY_SHOWING = "showing"

/** For the log: what the reader asked Android for. */
internal fun orientationName(o: Int): String = when (o) {
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "any way round"
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> "portrait"
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "landscape"
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "locked portrait"
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "locked landscape"
    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "reverse portrait"
    else -> "orientation $o"
}
