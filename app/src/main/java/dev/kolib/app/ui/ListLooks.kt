package dev.kolib.app.ui

import dev.kolib.app.ui.components.ShelfMode
import dev.kolib.app.ui.components.CoversPerRow
import android.content.Context
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibrarySort

/**
 * How the library's and the Kobo's book lists were last sorted (by what, which way) and shown (covers, and how, or a list),
 * and how many covers a shelf's row holds, kept for the next time KoLib is opened.
 */
object ListLooks {
    private fun prefs(context: Context) = context.getSharedPreferences("lists", Context.MODE_PRIVATE)

    /** Puts [library] (and [kobo]) back the way they were last left. */
    fun restore(context: Context, library: LibraryBrowseState? = null, kobo: BrowseState? = null, covers: CoversPerRow? = null) {
        val p = prefs(context)
        covers?.let { c ->
            c.portrait = p.getInt("covers_portrait", 0).coerceIn(0, CoversPerRow.MOST)
            c.landscape = p.getInt("covers_landscape", 0).coerceIn(0, CoversPerRow.MOST)
            c.showWhere = p.getBoolean("covers_where", true)
            c.showProgress = p.getBoolean("covers_progress", true)
            c.showContinue = p.getBoolean("continue_row", true)
        }
        library?.let { s ->
            LibrarySort.entries.firstOrNull { it.name == p.getString("library_sort", null) }?.let { order ->
                s.sort = order
                s.descending = p.getBoolean("library_descending", order.startsDescending)
            }
            s.mode = modeOf(p, "library")
            s.comicsTab = p.getBoolean("library_comics_tab", true)
            s.seriesInPlace = p.getBoolean("library_series_in_place", false)
            s.comicMode = ShelfMode.entries.firstOrNull { it.name == p.getString("library_comic_mode", null) } ?: ShelfMode.COMFORTABLE
            s.seriesMode = ShelfMode.entries.firstOrNull { it.name == p.getString("library_series_mode", null) }
                ?: if (p.getBoolean("library_series_grid", false)) ShelfMode.COMFORTABLE else ShelfMode.LIST
        }
        kobo?.let { s ->
            KoboSort.entries.firstOrNull { it.name == p.getString("kobo_sort", null) }?.let { order ->
                s.sort = order
                s.descending = p.getBoolean("kobo_descending", order.startsDescending)
            }
            s.mode = modeOf(p, "kobo")
            s.whereNoteSeen = p.getBoolean("kobo_where_seen", false)
        }
    }

    /** How [list]'s books were last shown; from before there was more than covers or a list, the covers are the comfortable grid. */
    private fun modeOf(p: android.content.SharedPreferences, list: String): ShelfMode =
        ShelfMode.entries.firstOrNull { it.name == p.getString("${list}_mode", null) }
            ?: if (p.getBoolean("${list}_grid", true)) ShelfMode.COMFORTABLE else ShelfMode.LIST

    /** What's kept, as it is now. */
    data class Now(
        val librarySort: LibrarySort, val libraryDescending: Boolean, val libraryMode: ShelfMode, val koboSort: KoboSort, val koboDescending: Boolean, val koboMode: ShelfMode,
        val comicsTab: Boolean = true, val seriesInPlace: Boolean = false, val seriesMode: ShelfMode = ShelfMode.LIST,
        val comicMode: ShelfMode = ShelfMode.COMFORTABLE,
        val coversPortrait: Int = 0, val coversLandscape: Int = 0,
        val coversWhere: Boolean = true, val coversProgress: Boolean = true, val continueRow: Boolean = true,
        val whereNoteSeen: Boolean = false,
    ) {
        constructor(library: LibraryBrowseState, kobo: BrowseState, covers: CoversPerRow? = null) : this(
            library.sort, library.descending, library.mode, kobo.sort, kobo.descending, kobo.mode,
            library.comicsTab, library.seriesInPlace, library.seriesMode, library.comicMode,
            covers?.portrait ?: 0, covers?.landscape ?: 0,
            covers?.showWhere ?: true, covers?.showProgress ?: true, covers?.showContinue ?: true,
            kobo.whereNoteSeen,
        )
    }

    fun save(context: Context, now: Now) {
        prefs(context).edit()
            .putString("library_sort", now.librarySort.name)
            .putBoolean("library_descending", now.libraryDescending)
            .putString("library_mode", now.libraryMode.name)
            .putString("kobo_sort", now.koboSort.name)
            .putBoolean("kobo_descending", now.koboDescending)
            .putString("kobo_mode", now.koboMode.name)
            .putBoolean("library_comics_tab", now.comicsTab)
            .putBoolean("library_series_in_place", now.seriesInPlace)
            .putString("library_series_mode", now.seriesMode.name)
            .putString("library_comic_mode", now.comicMode.name)
            .putInt("covers_portrait", now.coversPortrait)
            .putInt("covers_landscape", now.coversLandscape)
            .putBoolean("covers_where", now.coversWhere)
            .putBoolean("covers_progress", now.coversProgress)
            .putBoolean("continue_row", now.continueRow)
            .putBoolean("kobo_where_seen", now.whereNoteSeen)
            .apply()
    }
}
