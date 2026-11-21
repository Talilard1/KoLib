package dev.kolib.app.ui

import dev.kolib.app.ui.components.CoversPerRow
import android.content.Context
import dev.kolib.app.ui.library.LibraryBrowseState
import dev.kolib.app.ui.library.LibrarySort

/**
 * How the library's and the Kobo's book lists were last sorted (by what, which way) and shown (covers or a list),
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
        }
        library?.let { s ->
            LibrarySort.entries.firstOrNull { it.name == p.getString("library_sort", null) }?.let { order ->
                s.sort = order
                s.descending = p.getBoolean("library_descending", order.startsDescending)
            }
            if (p.contains("library_grid")) s.grid = p.getBoolean("library_grid", true)
            s.comicsTab = p.getBoolean("library_comics_tab", true)
            s.seriesInPlace = p.getBoolean("library_series_in_place", false)
            s.seriesGrid = p.getBoolean("library_series_grid", false)
        }
        kobo?.let { s ->
            KoboSort.entries.firstOrNull { it.name == p.getString("kobo_sort", null) }?.let { order ->
                s.sort = order
                s.descending = p.getBoolean("kobo_descending", order.startsDescending)
            }
            if (p.contains("kobo_grid")) s.grid = p.getBoolean("kobo_grid", true)
        }
    }

    /** What's kept, as it is now. */
    data class Now(
        val librarySort: LibrarySort, val libraryDescending: Boolean, val libraryGrid: Boolean, val koboSort: KoboSort, val koboDescending: Boolean, val koboGrid: Boolean,
        val comicsTab: Boolean = true, val seriesInPlace: Boolean = false, val seriesGrid: Boolean = false,
        val coversPortrait: Int = 0, val coversLandscape: Int = 0,
    ) {
        constructor(library: LibraryBrowseState, kobo: BrowseState, covers: CoversPerRow? = null) : this(
            library.sort, library.descending, library.grid, kobo.sort, kobo.descending, kobo.grid,
            library.comicsTab, library.seriesInPlace, library.seriesGrid,
            covers?.portrait ?: 0, covers?.landscape ?: 0,
        )
    }

    fun save(context: Context, now: Now) {
        prefs(context).edit()
            .putString("library_sort", now.librarySort.name)
            .putBoolean("library_descending", now.libraryDescending)
            .putBoolean("library_grid", now.libraryGrid)
            .putString("kobo_sort", now.koboSort.name)
            .putBoolean("kobo_descending", now.koboDescending)
            .putBoolean("kobo_grid", now.koboGrid)
            .putBoolean("library_comics_tab", now.comicsTab)
            .putBoolean("library_series_in_place", now.seriesInPlace)
            .putBoolean("library_series_grid", now.seriesGrid)
            .putInt("covers_portrait", now.coversPortrait)
            .putInt("covers_landscape", now.coversLandscape)
            .apply()
    }
}
