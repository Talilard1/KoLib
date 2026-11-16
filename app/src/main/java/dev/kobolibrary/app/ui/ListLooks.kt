package dev.kobolibrary.app.ui

import android.content.Context
import dev.kobolibrary.app.ui.library.LibraryBrowseState
import dev.kobolibrary.app.ui.library.LibrarySort

/**
 * How the library's and the Kobo's book lists were last sorted (by what, which way) and shown (covers or a list),
 * kept for the next time KoLib is opened.
 */
object ListLooks {
    private fun prefs(context: Context) = context.getSharedPreferences("lists", Context.MODE_PRIVATE)

    /** Puts [library] (and [kobo]) back the way they were last left. */
    fun restore(context: Context, library: LibraryBrowseState? = null, kobo: BrowseState? = null) {
        val p = prefs(context)
        library?.let { s ->
            LibrarySort.entries.firstOrNull { it.name == p.getString("library_sort", null) }?.let { order ->
                s.sort = order
                s.descending = p.getBoolean("library_descending", order.startsDescending)
            }
            if (p.contains("library_grid")) s.grid = p.getBoolean("library_grid", true)
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
    data class Now(val librarySort: LibrarySort, val libraryDescending: Boolean, val libraryGrid: Boolean, val koboSort: KoboSort, val koboDescending: Boolean, val koboGrid: Boolean) {
        constructor(library: LibraryBrowseState, kobo: BrowseState) : this(library.sort, library.descending, library.grid, kobo.sort, kobo.descending, kobo.grid)
    }

    fun save(context: Context, now: Now) {
        prefs(context).edit()
            .putString("library_sort", now.librarySort.name)
            .putBoolean("library_descending", now.libraryDescending)
            .putBoolean("library_grid", now.libraryGrid)
            .putString("kobo_sort", now.koboSort.name)
            .putBoolean("kobo_descending", now.koboDescending)
            .putBoolean("kobo_grid", now.koboGrid)
            .apply()
    }
}
