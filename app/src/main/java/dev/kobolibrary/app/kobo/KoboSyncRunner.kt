package dev.kobolibrary.app.kobo

import dev.kobolibrary.app.tr
import dev.kobolibrary.app.library.Library
import dev.kobolibrary.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carries out a [KoboSync] plan: the Kobo's database first, then the library once that has worked.
 * The same write makes the [pending] changes to the records of books just sent again.
 */
class KoboSyncRunner(
    private val repo: KoboRepository,
    private val library: Library,
    private val pending: PendingKoboRecords = PendingKoboRecords(),
) {

    data class Outcome(
        /** The connection afterwards (a fresh read of the Kobo's database if it changed). */
        val session: KoboSession,
        val collectionsOnKobo: Int,
        val detailsOnKobo: Int,
        val collectionsInLibrary: Int,
        val noLongerOnKobo: Int,
        /** Books sent again whose record the Kobo keeps (reading progress, highlights, collections). */
        val recordsKept: Int = 0,
        /** Collections books should be in that the Kobo doesn't have, so they weren't put in them. */
        val missingCollections: List<String> = emptyList(),
    ) {
        val anything: Boolean get() = collectionsOnKobo + detailsOnKobo + collectionsInLibrary + noLongerOnKobo + recordsKept > 0

        /** What was done, to show; [mentionMissing] says whether to tell of collections the Kobo doesn't have. */
        fun describe(mentionMissing: Boolean = true): String? {
            val parts = buildList {
                if (collectionsOnKobo > 0) add(tr("Put {0} in their collections on the Kobo.", plural(collectionsOnKobo)))
                if (detailsOnKobo > 0) add(tr("Updated the details of {0} on the Kobo.", plural(detailsOnKobo)))
                if (collectionsInLibrary > 0) add(tr("Copied collection changes from the Kobo to {0} in your library.", plural(collectionsInLibrary)))
                if (noLongerOnKobo > 0) add(tr("Marked {0} as deleted from the Kobo.", plural(noLongerOnKobo)))
                when (if (mentionMissing) missingCollections.size else 0) {
                    0 -> Unit
                    1 -> add(tr("The Kobo has no collection called “{0}”, so books weren't put in it. Make it on the Kobo, then connect again.", missingCollections[0]))
                    else -> add(tr("The Kobo doesn't have these collections, so books weren't put in them: {0}. Make them on the Kobo, then connect again.", missingCollections.joinToString(", ")))
                }
            }
            if (parts.isEmpty()) return null
            return parts.joinToString(" ")
        }

        private fun plural(n: Int) = if (n == 1) tr("1 book") else tr("{0} books", n)
    }

    suspend fun run(session: KoboSession): Outcome = withContext(Dispatchers.IO) {
        // Record changes from an earlier connection weren't made before the Kobo was unplugged, so it
        // took those books in as new ones, without their collections: compare those afresh.
        val stale = pending.takeStale(session.connectedAtMillis)
        if (stale.isNotEmpty()) {
            AppLog.i("Sync", "${stale.size} book(s) sent last time were taken in by the Kobo as new books; their collections are compared afresh.")
            library.forgetKoboAgreement(stale.map { it.bookId })
        }
        // Only books the Kobo still has a record of can keep it.
        val records = pending.forConnection(session.connectedAtMillis).filterKeys { it in session.snapshot.booksById }
        pending.done(pending.forConnection(session.connectedAtMillis).keys - records.keys)

        val plan = KoboSync.plan(library.books.value, session.snapshot)
        // Books whose file has gone from the Kobo were deleted there; ones still there are waiting to be added.
        val (gone, waiting) = plan.notListed.partition { b ->
            runCatching { session.tree.resolve(b.koboPath!!) == null }.getOrDefault(false)
        }
        if (gone.isNotEmpty()) {
            AppLog.i("Sync", "${gone.size} book(s) are no longer on the Kobo: ${gone.joinToString { "'${it.title}'" }}.")
            library.forgetKoboPaths(gone.mapNotNull { it.koboPath })
        }

        val forKobo = plan.koboChanges + KoboSync.waitingChanges(waiting, session.snapshot)
        val missing = (plan.missingOnKobo + KoboSync.missingCollections(waiting, session.snapshot)).distinctBy { it.lowercase() }
        if (missing.isNotEmpty()) AppLog.i("Sync", "Not on the Kobo, so no books were put in them: ${missing.joinToString { "'$it'" }}.")
        var after = session
        if (records.isNotEmpty()) {
            AppLog.i("Sync", "Keeping the Kobo's record of ${records.size} book(s) sent again: their new size and cover pictures.")
        }
        if (forKobo.isNotEmpty() || records.isNotEmpty()) {
            for (c in forKobo) {
                AppLog.i(
                    "Sync",
                    "'${c.title}': " + listOfNotNull(
                        c.addOnKobo.takeIf { it.isNotEmpty() }?.let { "add to ${it.joinToString()}" },
                        c.removeOnKobo.takeIf { it.isNotEmpty() }?.let { "take out of ${it.joinToString()}" },
                        c.details.keys.takeIf { it.isNotEmpty() }?.let { "set ${it.joinToString()}" },
                    ).joinToString("; ") + ".",
                )
            }
            after = repo.writeDatabase(session) { editor ->
                var n = 0
                for (c in forKobo) {
                    if (editor.hasCollections) {
                        c.addOnKobo.forEach { if (editor.addToCollection(it, c.contentId)) n++ }
                        c.removeOnKobo.forEach { if (editor.removeFromCollection(it, c.contentId)) n++ }
                    }
                    if (c.details.isNotEmpty() && editor.updateBook(c.contentId, c.details)) n++
                }
                for ((contentId, p) in records) if (editor.updateBook(contentId, p.columns)) n++
                n
            } ?: session
            pending.done(records.keys)
        }
        // Only now that the Kobo has them does the library record what both sides agree on.
        val forLibrary = plan.changes.filter { it.touchesLibrary }
        library.applyKoboAgreement(forLibrary.map { Triple(it.bookId, it.libraryCollections, it.agreed) })
        Outcome(
            session = after,
            collectionsOnKobo = forKobo.count { it.addOnKobo.isNotEmpty() || it.removeOnKobo.isNotEmpty() },
            detailsOnKobo = forKobo.count { it.details.isNotEmpty() },
            collectionsInLibrary = plan.changes.count { it.libraryCollections != null },
            noLongerOnKobo = gone.size,
            recordsKept = records.size,
            missingCollections = missing,
        )
    }
}
