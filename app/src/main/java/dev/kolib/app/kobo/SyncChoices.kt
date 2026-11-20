package dev.kolib.app.kobo

import android.content.Context
import dev.kolib.app.tr

/** Which way where you're up to in a book goes when reading is synced with the Kobo. */
enum class PlaceWay(val label: () -> String, val explanation: () -> String) {
    LATEST({ tr("Whichever you read last") }, { tr("Each book opens where you last read it, in KoLib or on the Kobo.") }),
    FROM_KOBO({ tr("From the Kobo to KoLib") }, { tr("KoLib opens each book where you are on the Kobo.") }),
    TO_KOBO({ tr("From KoLib to the Kobo") }, { tr("The Kobo opens each book where you are in KoLib.") }),
    OFF({ tr("Don't sync") }, { tr("KoLib and the Kobo each keep their own place.") }),
}

/** Which way bookmarks, highlights and notes go when reading is synced with the Kobo. */
enum class MarksWay(val label: () -> String, val explanation: () -> String) {
    BOTH({ tr("Both ways") }, { tr("New ones are copied each way. One you delete goes from both.") }),
    FROM_KOBO({ tr("From the Kobo to KoLib") }, { tr("The Kobo's are copied to KoLib. One deleted on the Kobo goes from KoLib too. Nothing in KoLib changes the Kobo.") }),
    TO_KOBO({ tr("From KoLib to the Kobo") }, { tr("KoLib's are copied to the Kobo. One deleted in KoLib goes from the Kobo too. Nothing on the Kobo changes KoLib.") }),
    OFF({ tr("Don't sync") }, { tr("KoLib and the Kobo each keep their own.") }),
}

/** What syncing reading with the Kobo does, as chosen in its window: kept for next time, and for books synced once they're sent. */
data class SyncChoices(val place: PlaceWay = PlaceWay.LATEST, val marks: MarksWay = MarksWay.BOTH) {
    /** Both are off: there's nothing to sync. */
    val nothing: Boolean get() = place == PlaceWay.OFF && marks == MarksWay.OFF

    companion object {
        private const val PREFS = "reading_sync"

        fun load(context: Context): SyncChoices {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return SyncChoices(
                place = PlaceWay.entries.firstOrNull { it.name == p.getString("place", null) } ?: PlaceWay.LATEST,
                marks = MarksWay.entries.firstOrNull { it.name == p.getString("marks", null) } ?: MarksWay.BOTH,
            )
        }

        fun save(context: Context, choices: SyncChoices) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("place", choices.place.name)
                .putString("marks", choices.marks.name)
                .apply()
        }
    }
}
